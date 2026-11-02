<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Alerte;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Schema;

/**
 * Points Boutique (PB) lus et changés par le serveur de jeu (/boutique, /trade, commandes de la console). Les PB sont
 * le solde du compte du site (users.money) : un seul porte-monnaie pour le site, le launcher et le jeu.
 *
 * Réservé au serveur de jeu : chaque requête est signée (HMAC-SHA256 de « horodatage \n méthode \n chemin \n corps »,
 * en-têtes x-hz-horodatage et x-hz-signature, même calcul que le lien Discord du mod heraliz-grades) avec la clé
 * storage/app/heraliz/pb.cle (créée par site/vm/theme.sh, lue par la mise en ligne du serveur : jamais dans le dépôt).
 * Toute opération porte une référence unique (ref) : rejouée, elle rend le même résultat sans rien refaire ; le serveur
 * de jeu peut donc la renvoyer sans risque après une coupure ou un plantage, et demander ce qu'elle est devenue (statut).
 * Un débit peut dire quelle part de son montant est brûlée (« taxe », hôtel des ventes) : seulement notée pour les stats.
 *
 * Clé volée (audit du 02/11, point 15 : elle est en clair sur le Ptero) : ce qu'elle permet est borné et visible.
 *  - Création de PB = un crédit que ne couvre aucun débit (console « pb donner », bons). Un remboursement d'achat
 *    (« <achat>-r »), d'un échange annulé (« <échange>-ra ») ou le prix d'une vente de l'hôtel des ventes
 *    (« <achat>-v ») est couvert par l'opération d'origine, déjà notée au journal, et jamais au-delà de son montant.
 *    Une création est plafonnée (CREATION_MAX par opération, CREATION_JOUR_MAX sur 24 h glissantes, au-delà : refusée)
 *    et notée comme telle au journal (colonne « creation »).
 *  - Le staff est prévenu (Alerte : journal du site et salon privé Discord) : création à partir d'ALERTE_CREATION PB,
 *    toute opération à partir d'ALERTE_OPERATION PB, plafond atteint, clé utilisée depuis une nouvelle adresse IP.
 *  - Clé changeable à tout moment (« Site HeraliZ » → nouvelle_cle_pb, site/vm/nouvelle_cle_pb.sh) : l'ancienne clé
 *    reste acceptée ANCIENNE_CLE_JOURS jours au plus, le temps que le serveur de jeu reçoive la nouvelle (mise en ligne
 *    et redémarrage), et elle est oubliée dès que la nouvelle sert.
 */
class PbController extends Controller
{
    private const TABLE = 'heraliz_pb_journal';
    private const TABLE_ADRESSES = 'heraliz_pb_adresses';
    private const REPERE = 'heraliz-pb-tables-v2';
    private const FENETRE_MS = 120000;
    private const TAXE_POURCENT = 10;

    /** Création de PB (crédit sans débit qui le couvre) : au plus ce montant par opération… */
    private const CREATION_MAX = 20000;

    /** … et ce total sur 24 h glissantes (lots vendus sur le site : 11 500 PB au plus ; votes : 2 PB). */
    private const CREATION_JOUR_MAX = 50000;

    private const ALERTE_CREATION = 2000;

    private const ALERTE_OPERATION = 10000;

    private const ANCIENNE_CLE_JOURS = 7;

    private static bool $pret = false;

    public function solde(Request $request)
    {
        return $this->signe($request, function (array $d) {
            $user = self::compte($d['joueur'] ?? null);

            return $user === null ? self::erreur('compte_inconnu', 404) : response()->json(['solde' => self::entier($user->money)]);
        });
    }

    public function debiter(Request $request)
    {
        return $this->signe($request, fn (array $d) => $this->mouvement($d, 'debit'));
    }

    public function crediter(Request $request)
    {
        return $this->signe($request, fn (array $d) => $this->mouvement($d, 'credit'));
    }

    public function transfert(Request $request)
    {
        return $this->signe($request, function (array $d) {
            $ref = self::ref($d);
            $montant = self::montant($d);
            if ($ref === null || $montant === null) {
                return self::erreur('requete_invalide', 422);
            }

            $reponse = DB::transaction(function () use ($d, $ref, $montant) {
                if (($deja = self::deja($ref)) !== null) {
                    return $deja;
                }
                $de = self::compte($d['de'] ?? null, true);
                $vers = self::compte($d['vers'] ?? null, true);
                if ($de === null || $vers === null) {
                    return self::erreur('compte_inconnu', 404);
                }
                if ($de->id === $vers->id) {
                    return self::erreur('requete_invalide', 422);
                }
                if (self::entier($de->money) < $montant) {
                    return self::erreur('solde_insuffisant', 409, ['solde' => self::entier($de->money)]);
                }
                $taxe = intdiv($montant * self::TAXE_POURCENT, 100);
                $de->money = self::entier($de->money) - $montant;
                $vers->money = self::entier($vers->money) + $montant - $taxe;
                $de->save();
                $vers->save();
                self::noter($ref, 'transfert', $de->name, $vers->name, $montant, $taxe, $d['motif'] ?? 'trade', false);

                return response()->json([
                    'ok' => true, 'ref' => $ref, 'recu' => $montant - $taxe, 'taxe' => $taxe,
                    'solde_de' => self::entier($de->money), 'solde_vers' => self::entier($vers->money),
                ]);
            });
            if ($montant >= self::ALERTE_OPERATION && self::fait($reponse)) {
                Alerte::staff('Points Boutique : échange de **'.$montant.' PB** de '.self::code($d['de'] ?? '?').' à '
                    .self::code($d['vers'] ?? '?').' ('.self::code($ref).').', 'pb-op:'.$ref, 86400);
            }

            return $reponse;
        });
    }

    public function statut(Request $request)
    {
        return $this->signe($request, function (array $d) {
            $ref = self::ref($d);
            if ($ref === null) {
                return self::erreur('requete_invalide', 422);
            }
            $ligne = DB::table(self::TABLE)->where('ref', $ref)->first();

            return response()->json(['fait' => $ligne !== null, 'type' => $ligne->type ?? null]);
        });
    }

    public function stats(Request $request)
    {
        return $this->signe($request, function () {
            $t = DB::table(self::TABLE);

            return response()->json([
                'circulation' => self::entier(User::query()->sum('money')),
                'echanges' => (clone $t)->where('type', 'transfert')->count(),
                'echanges_pb' => self::entier((clone $t)->where('type', 'transfert')->sum('montant')),
                'brules' => self::entier((clone $t)->sum('taxe')),
                'depenses' => self::entier((clone $t)->where('type', 'debit')->sum('montant')),
                'credits' => self::entier((clone $t)->where('type', 'credit')->sum('montant')),
                'creations_24h' => self::entier((clone $t)->where('creation', true)->where('created_at', '>=', now()->subDay())->sum('montant')),
            ]);
        });
    }

    private function mouvement(array $d, string $type): JsonResponse
    {
        $ref = self::ref($d);
        $montant = self::montant($d);
        if ($ref === null || $montant === null) {
            return self::erreur('requete_invalide', 422);
        }

        $creation = false;
        $reponse = DB::transaction(function () use ($d, $ref, $montant, $type, &$creation) {
            if (($deja = self::deja($ref)) !== null) {
                return $deja;
            }
            $user = self::compte($d['joueur'] ?? null, true);
            if ($user === null) {
                return self::erreur('compte_inconnu', 404);
            }
            if ($type === 'credit' && ! self::couvert($ref, $user->name, $montant)) {
                $creation = true;
                if (($refus = self::plafond($ref, $user->name, $montant)) !== null) {
                    return $refus;
                }
            }
            $solde = self::entier($user->money);
            if ($type === 'debit' && $solde < $montant) {
                return self::erreur('solde_insuffisant', 409, ['solde' => $solde]);
            }
            $user->money = $type === 'debit' ? $solde - $montant : $solde + $montant;
            $user->save();
            self::noter($ref, $type, $type === 'debit' ? $user->name : null, $type === 'credit' ? $user->name : null, $montant,
                $type === 'debit' ? self::partBrulee($d, $montant) : 0, $d['motif'] ?? '', $creation);

            return response()->json(['ok' => true, 'ref' => $ref, 'solde' => self::entier($user->money)]);
        });

        if (self::fait($reponse) && ! ($reponse->getData(true)['deja'] ?? false)) {
            $qui = self::code($d['joueur'] ?? '?');
            $motif = is_string($d['motif'] ?? null) && $d['motif'] !== '' ? ' (motif '.self::code(mb_substr($d['motif'], 0, 60)).')' : '';
            if ($creation) {
                Log::info('HeraliZ PB : création de '.$montant.' PB pour '.($d['joueur'] ?? '?').' ('.$ref.')');
                if ($montant >= self::ALERTE_CREATION) {
                    Alerte::staff('Points Boutique : **'.$montant.' PB créés** pour '.$qui.$motif.', sans achat ni vente derrière ('
                        .self::code($ref).').', 'pb-op:'.$ref, 86400);
                }
            } elseif ($montant >= self::ALERTE_OPERATION) {
                Alerte::staff('Points Boutique : '.($type === 'debit' ? 'débit' : 'crédit').' de **'.$montant.' PB** pour '.$qui
                    .$motif.' ('.self::code($ref).').', 'pb-op:'.$ref, 86400);
            }
        }

        return $reponse;
    }

    /**
     * Crédit couvert par une opération déjà au journal : remboursement d'un achat (« <achat>-r ») ou d'un échange annulé
     * (« <échange>-ra ») au compte qui avait payé, prix d'une vente de l'hôtel des ventes (« <achat>-v ») au vendeur ;
     * jamais plus, en tout, que le montant de l'opération d'origine.
     */
    private static function couvert(string $ref, string $compte, int $montant): bool
    {
        if (preg_match('/^(.+)-(r|ra|v)$/', $ref, $m) !== 1) {
            return false;
        }
        $origine = DB::table(self::TABLE)->where('ref', $m[1])->lockForUpdate()->first();
        if ($origine === null || ! in_array($origine->type, ['debit', 'transfert'], true)) {
            return false;
        }
        if ($m[2] !== 'v' && strcasecmp((string) $origine->de, $compte) !== 0) {
            return false;
        }
        $deja = (int) DB::table(self::TABLE)->whereIn('ref', [$m[1].'-r', $m[1].'-ra', $m[1].'-v'])->sum('montant');

        return $deja + $montant <= (int) $origine->montant;
    }

    /** Création de PB au-delà des plafonds : la réponse de refus (le staff est prévenu), sinon null. */
    private static function plafond(string $ref, string $compte, int $montant): ?JsonResponse
    {
        if ($montant > self::CREATION_MAX) {
            Alerte::staff('Points Boutique : création de **'.$montant.' PB** pour '.self::code($compte).' REFUSÉE (plus de '
                .self::CREATION_MAX.' PB d’un coup, '.self::code($ref).'). Si ce n’est pas le staff : la clé des PB a peut-être '
                .'fuité, lancer « Site HeraliZ » → nouvelle_cle_pb.', 'pb-plafond:'.$ref, 86400);

            return self::erreur('plafond', 409, ['max' => self::CREATION_MAX]);
        }
        $jour = (int) DB::table(self::TABLE)->where('creation', true)->where('created_at', '>=', now()->subDay())->sum('montant');
        if ($jour + $montant > self::CREATION_JOUR_MAX) {
            Alerte::staff('Points Boutique : création de '.$montant.' PB pour '.self::code($compte).' REFUSÉE : déjà '.$jour
                .' PB créés en 24 h (plafond '.self::CREATION_JOUR_MAX.'). Si ce n’est pas le staff : lancer « Site HeraliZ » → '
                .'nouvelle_cle_pb.', 'pb-plafond-jour', 3600);

            return self::erreur('plafond', 409, ['max_jour' => self::CREATION_JOUR_MAX, 'jour' => $jour]);
        }

        return null;
    }

    /** Vérifie la signature, l'heure et le rejeu, puis lance l'opération (corps JSON décodé). */
    private function signe(Request $request, callable $operation): JsonResponse
    {
        $cle = self::cle();
        if ($cle === null) {
            return self::erreur('pb_indisponible', 503);
        }
        $ts = (string) $request->header('x-hz-horodatage', '');
        $signature = strtolower((string) $request->header('x-hz-signature', ''));
        $corps = (string) $request->getContent();
        if (! ctype_digit($ts) || abs((int) (microtime(true) * 1000) - (int) $ts) > self::FENETRE_MS) {
            return self::erreur('signature', 401);
        }
        $message = $ts."\n".$request->method()."\n/".ltrim($request->path(), '/')."\n".$corps;
        $ancienne = self::ancienneCle();
        $parNouvelle = hash_equals(hash_hmac('sha256', $message, $cle), $signature);
        if (! $parNouvelle && ($ancienne === null || ! hash_equals(hash_hmac('sha256', $message, $ancienne), $signature))) {
            return self::erreur('signature', 401);
        }
        // une même signature ne sert qu'une fois (les opérations portent aussi leur ref)
        if (! Cache::add('heraliz-pb-sig:'.$signature, 1, now()->addMilliseconds(self::FENETRE_MS * 2))) {
            return self::erreur('rejeu', 401);
        }
        // clé changée : le serveur de jeu se sert de la nouvelle, l'ancienne ne sert plus à rien
        if ($parNouvelle && $ancienne !== null) {
            @unlink(storage_path('app/heraliz/pb-ancienne.cle'));
            Log::notice('HeraliZ PB : le serveur de jeu utilise la nouvelle clé, l’ancienne est oubliée');
        }
        self::tables();
        self::noterAdresse($request);
        $d = json_decode($corps === '' ? '{}' : $corps, true);

        return $operation(is_array($d) ? $d : []);
    }

    /**
     * Adresses IP qui se servent de la clé (normalement : celle du serveur de jeu). Une nouvelle adresse prévient le
     * staff : si le serveur de jeu n'a pas changé de machine, la clé a fuité. Au plus une écriture par adresse toutes
     * les 10 minutes.
     */
    private static function noterAdresse(Request $request): void
    {
        $ip = (string) $request->ip();
        if ($ip === '' || ! Cache::add('heraliz-pb-ip:'.$ip, true, 600)) {
            return;
        }
        rescue(function () use ($ip) {
            $maj = DB::table(self::TABLE_ADRESSES)->where('ip', $ip)->update(['derniere_le' => now(), 'appels' => DB::raw('appels + 1')]);
            if ($maj > 0) {
                return;
            }
            DB::table(self::TABLE_ADRESSES)->insertOrIgnore(['ip' => $ip, 'premiere_le' => now(), 'derniere_le' => now(), 'appels' => 1]);
            $autres = DB::table(self::TABLE_ADRESSES)->where('ip', '!=', $ip)->count();
            $jeu = self::adresseDuJeu($ip) ? 'c’est bien une adresse de play.heraliz.fr' : 'ce n’est PAS une adresse de play.heraliz.fr';
            Alerte::staff('Points Boutique : la clé du serveur de jeu est utilisée depuis '.($autres === 0 ? 'une première' : 'une NOUVELLE')
                .' adresse IP, `'.$ip.'` ('.$jeu.'). Si le serveur de jeu n’a pas changé de machine, la clé a peut-être fuité : '
                .'lancer « Site HeraliZ » → nouvelle_cle_pb, puis « Serveur HeraliZ » avec « redemarrer ».', 'pb-ip:'.$ip, 86400);
        }, null, false);
    }

    /** L'adresse est-elle une de celles de play.heraliz.fr (ou de la machine vers laquelle son SRV renvoie) ? */
    private static function adresseDuJeu(string $ip): bool
    {
        $adresses = Cache::remember('heraliz-pb-adresses-jeu', 3600, function () {
            $noms = ['play.heraliz.fr'];
            foreach (@dns_get_record('_minecraft._tcp.play.heraliz.fr', DNS_SRV) ?: [] as $srv) {
                if (is_string($srv['target'] ?? null) && $srv['target'] !== '') {
                    $noms[] = $srv['target'];
                }
            }
            $ips = [];
            foreach ($noms as $nom) {
                foreach (@dns_get_record($nom, DNS_A | DNS_AAAA) ?: [] as $r) {
                    $ips[] = $r['ip'] ?? $r['ipv6'] ?? null;
                }
            }

            return array_values(array_filter($ips));
        });

        return in_array($ip, $adresses, true);
    }

    private static function tables(): void
    {
        if (self::$pret || Cache::has(self::REPERE)) {
            self::$pret = true;

            return;
        }
        if (! Schema::hasTable(self::TABLE)) {
            Schema::create(self::TABLE, function (Blueprint $t) {
                $t->id();
                $t->string('ref', 80)->unique();
                $t->string('type', 16)->index();
                $t->string('de', 32)->nullable();
                $t->string('vers', 32)->nullable();
                $t->unsignedBigInteger('montant');
                $t->unsignedBigInteger('taxe')->default(0);
                $t->string('motif', 120)->nullable();
                $t->boolean('creation')->default(false);
                $t->timestamp('created_at')->useCurrent();
            });
        } elseif (! Schema::hasColumn(self::TABLE, 'creation')) {
            // journal d'avant le 02/11 : ses crédits ne sont pas comptés comme créations (plafond du jour vide)
            Schema::table(self::TABLE, function (Blueprint $t) {
                $t->boolean('creation')->default(false);
            });
        }
        if (! Schema::hasTable(self::TABLE_ADRESSES)) {
            Schema::create(self::TABLE_ADRESSES, function (Blueprint $t) {
                $t->id();
                $t->string('ip', 64)->unique();
                $t->timestamp('premiere_le')->nullable();
                $t->timestamp('derniere_le')->nullable();
                $t->unsignedBigInteger('appels')->default(0);
            });
        }
        Cache::forever(self::REPERE, true);
        self::$pret = true;
    }

    private static function deja(string $ref): ?JsonResponse
    {
        $ligne = DB::table(self::TABLE)->where('ref', $ref)->lockForUpdate()->first();

        return $ligne === null ? null : response()->json([
            'ok' => true, 'ref' => $ref, 'deja' => true, 'recu' => (int) $ligne->montant - (int) $ligne->taxe, 'taxe' => (int) $ligne->taxe,
        ]);
    }

    private static function noter(string $ref, string $type, ?string $de, ?string $vers, int $montant, int $taxe, $motif, bool $creation): void
    {
        DB::table(self::TABLE)->insert([
            'ref' => $ref, 'type' => $type, 'de' => $de, 'vers' => $vers, 'montant' => $montant, 'taxe' => $taxe,
            'motif' => mb_substr(is_string($motif) ? $motif : '', 0, 120), 'creation' => $creation, 'created_at' => now(),
        ]);
    }

    private static function fait(JsonResponse $reponse): bool
    {
        return $reponse->getStatusCode() === 200 && ($reponse->getData(true)['ok'] ?? false) === true;
    }

    private static function compte($pseudo, bool $verrou = false): ?User
    {
        if (! is_string($pseudo) || ! preg_match('/^\w{3,16}$/', $pseudo)) {
            return null;
        }
        $q = User::query()->where('name', $pseudo);

        return ($verrou ? $q->lockForUpdate() : $q)->first();
    }

    private static function ref(array $d): ?string
    {
        $ref = $d['ref'] ?? null;

        return is_string($ref) && preg_match('/^[A-Za-z0-9_:.-]{8,80}$/', $ref) ? $ref : null;
    }

    private static function montant(array $d): ?int
    {
        $m = $d['montant'] ?? null;

        return is_int($m) && $m > 0 && $m <= 100000000 ? $m : null;
    }

    /**
     * Part d'un débit qui n'est versée à personne : la taxe des achats en PB de l'hôtel des ventes (le serveur de jeu
     * débite l'acheteur du prix + 10 % et ne verse que le prix au vendeur). Notée dans la colonne taxe pour que « PB
     * détruits par la taxe » (stats) la compte, comme la taxe de /trade ; elle ne change rien au solde. Absente ou
     * invalide : 0 (le débit passe quand même).
     */
    private static function partBrulee(array $d, int $montant): int
    {
        $t = $d['taxe'] ?? 0;

        return is_int($t) && $t > 0 && $t < $montant ? $t : 0;
    }

    private static function entier($valeur): int
    {
        return (int) floor((float) $valeur + 1e-6);
    }

    private static function code(mixed $texte): string
    {
        return '`'.str_replace('`', 'ˋ', mb_substr(is_scalar($texte) ? (string) $texte : '?', 0, 80)).'`';
    }

    private static function erreur(string $raison, int $code, array $plus = []): JsonResponse
    {
        return response()->json(['ok' => false, 'reason' => $raison] + $plus, $code);
    }

    /** La clé du serveur de jeu (storage/app/heraliz/pb.cle, 40 caractères au moins), ou null si elle manque. */
    public static function cle(): ?string
    {
        return self::lireCle(storage_path('app/heraliz/pb.cle'));
    }

    /**
     * L'ancienne clé, après un changement de clé (site/vm/nouvelle_cle_pb.sh), tant que le serveur de jeu ne s'est pas
     * servi de la nouvelle et au plus ANCIENNE_CLE_JOURS jours ; null sinon.
     */
    private static function ancienneCle(): ?string
    {
        $fichier = storage_path('app/heraliz/pb-ancienne.cle');
        $date = @filemtime($fichier);
        if ($date === false || time() - $date > self::ANCIENNE_CLE_JOURS * 86400) {
            return null;
        }

        return self::lireCle($fichier);
    }

    private static function lireCle(string $fichier): ?string
    {
        $cle = is_readable($fichier) ? trim((string) file_get_contents($fichier)) : '';

        return strlen($cle) >= 40 ? $cle : null;
    }
}
