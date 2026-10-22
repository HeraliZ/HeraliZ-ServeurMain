<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\User;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
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
 */
class PbController extends Controller
{
    private const TABLE = 'heraliz_pb_journal';
    private const FENETRE_MS = 120000;
    private const TAXE_POURCENT = 10;

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

            return DB::transaction(function () use ($d, $ref, $montant) {
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
                self::noter($ref, 'transfert', $de->name, $vers->name, $montant, $taxe, $d['motif'] ?? 'trade');

                return response()->json([
                    'ok' => true, 'ref' => $ref, 'recu' => $montant - $taxe, 'taxe' => $taxe,
                    'solde_de' => self::entier($de->money), 'solde_vers' => self::entier($vers->money),
                ]);
            });
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

        return DB::transaction(function () use ($d, $ref, $montant, $type) {
            if (($deja = self::deja($ref)) !== null) {
                return $deja;
            }
            $user = self::compte($d['joueur'] ?? null, true);
            if ($user === null) {
                return self::erreur('compte_inconnu', 404);
            }
            $solde = self::entier($user->money);
            if ($type === 'debit' && $solde < $montant) {
                return self::erreur('solde_insuffisant', 409, ['solde' => $solde]);
            }
            $user->money = $type === 'debit' ? $solde - $montant : $solde + $montant;
            $user->save();
            self::noter($ref, $type, $type === 'debit' ? $user->name : null, $type === 'credit' ? $user->name : null, $montant, 0, $d['motif'] ?? '');

            return response()->json(['ok' => true, 'ref' => $ref, 'solde' => self::entier($user->money)]);
        });
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
        $attendu = hash_hmac('sha256', $ts."\n".$request->method()."\n/".ltrim($request->path(), '/')."\n".$corps, $cle);
        if (! hash_equals($attendu, $signature)) {
            return self::erreur('signature', 401);
        }
        // une même signature ne sert qu'une fois (les opérations portent aussi leur ref)
        if (! Cache::add('heraliz-pb-sig:'.$signature, 1, now()->addMilliseconds(self::FENETRE_MS * 2))) {
            return self::erreur('rejeu', 401);
        }
        self::table();
        $d = json_decode($corps === '' ? '{}' : $corps, true);

        return $operation(is_array($d) ? $d : []);
    }

    private static function table(): void
    {
        if (Schema::hasTable(self::TABLE)) {
            return;
        }
        Schema::create(self::TABLE, function (Blueprint $t) {
            $t->id();
            $t->string('ref', 80)->unique();
            $t->string('type', 16)->index();
            $t->string('de', 32)->nullable();
            $t->string('vers', 32)->nullable();
            $t->unsignedBigInteger('montant');
            $t->unsignedBigInteger('taxe')->default(0);
            $t->string('motif', 120)->nullable();
            $t->timestamp('created_at')->useCurrent();
        });
    }

    private static function deja(string $ref): ?JsonResponse
    {
        $ligne = DB::table(self::TABLE)->where('ref', $ref)->lockForUpdate()->first();

        return $ligne === null ? null : response()->json([
            'ok' => true, 'ref' => $ref, 'deja' => true, 'recu' => (int) $ligne->montant - (int) $ligne->taxe, 'taxe' => (int) $ligne->taxe,
        ]);
    }

    private static function noter(string $ref, string $type, ?string $de, ?string $vers, int $montant, int $taxe, $motif): void
    {
        DB::table(self::TABLE)->insert([
            'ref' => $ref, 'type' => $type, 'de' => $de, 'vers' => $vers, 'montant' => $montant, 'taxe' => $taxe,
            'motif' => mb_substr(is_string($motif) ? $motif : '', 0, 120), 'created_at' => now(),
        ]);
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

    private static function entier($valeur): int
    {
        return (int) floor((float) $valeur + 1e-6);
    }

    private static function erreur(string $raison, int $code, array $plus = []): JsonResponse
    {
        return response()->json(['ok' => false, 'reason' => $raison] + $plus, $code);
    }

    /** La clé du serveur de jeu (storage/app/heraliz/pb.cle, 40 caractères au moins), ou null si elle manque. */
    public static function cle(): ?string
    {
        $fichier = storage_path('app/heraliz/pb.cle');
        $cle = is_readable($fichier) ? trim((string) file_get_contents($fichier)) : '';

        return strlen($cle) >= 40 ? $cle : null;
    }
}
