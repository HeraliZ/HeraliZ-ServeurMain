<?php

namespace Azuriom\Plugin\Heraliz\Discord;

use Azuriom\Models\Server;
use Azuriom\Models\User;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Schema;
use Throwable;

/**
 * Discord obligatoire pour jouer (bêta, choix du propriétaire) : chaque compte du site, nouveau ou ancien, doit avoir
 * lié son Discord, être membre du serveur Discord HeraliZ, ne pas y être banni et, tant que la validation est à la main,
 * avoir été accepté par le staff (boutons du salon privé), avant d'avoir le pack de jeu (PackController) ou un jeton de
 * lancement (JetonController). Un Discord banni bloque le compte du site, ses adresses IP et son PC (ban de Spartan joué
 * par la console du serveur de jeu, par AzLink).
 *
 * Membre et ban sont revérifiés auprès de Discord au plus toutes les 5 minutes par compte (toutes les 20 s tant qu'il
 * n'est pas membre, pour qu'il puisse jouer dès qu'il a rejoint), et par la tâche planifiée pour ceux qui ne demandent
 * rien : un ban sur Discord compte en 5 à 10 minutes. Discord injoignable : le dernier état connu est gardé (une panne de
 * Discord ne bloque personne), mais un compte jamais vérifié ne passe pas.
 */
final class Comptes
{
    public const OK = 'ok';
    public const A_LIER = 'a_lier';
    public const PAS_MEMBRE = 'pas_membre';
    public const EN_ATTENTE = 'en_attente';
    public const REFUSE = 'refuse';
    public const BANNI = 'banni';
    public const INDISPONIBLE = 'indisponible';

    private const DELAI_MEMBRE = 300;
    private const DELAI_AUTRE = 20;
    private const DELAI_PANNE = 60;

    /** Ce qui manque au compte pour jouer (OK si rien). L'adresse IP donnée est aussi notée et contrôlée. */
    public static function statut(User $user, ?string $ip = null): string
    {
        Stockage::tables();
        if ($ip !== null) {
            self::noterIp($user->id, $ip);
        }
        if (! Reglages::actif()) {
            return self::OK;
        }
        if ($ip !== null && self::ipBannie($ip)) {
            return self::BANNI;
        }

        $ligne = self::ligne($user->id);
        if ($ligne === null) {
            return self::A_LIER;
        }
        $ligne = self::verifier($user, $ligne);

        if ($ligne->banni) {
            return self::BANNI;
        }
        if ($ligne->verifie_le === null) {
            return self::INDISPONIBLE;
        }
        if (! $ligne->membre) {
            return self::PAS_MEMBRE;
        }
        if ($ligne->statut === 'refuse') {
            return self::REFUSE;
        }
        if (Reglages::validationManuelle() && $ligne->statut !== 'accepte' && ! self::staffDuSite($user)) {
            self::demander($user, $ligne);

            return self::EN_ATTENTE;
        }

        return self::OK;
    }

    /** Message court pour le joueur, selon le statut. */
    public static function message(string $statut): string
    {
        return match ($statut) {
            self::A_LIER => 'Lie ton compte Discord pour jouer pendant la bêta.',
            self::PAS_MEMBRE => 'Rejoins le Discord HeraliZ avec le compte Discord lié, puis réessaie.',
            self::EN_ATTENTE => 'Ton compte attend la validation du staff : réessaie un peu plus tard.',
            self::REFUSE => 'Ton compte n’a pas été accepté pour la bêta. Pour en parler, ouvre un ticket sur le Discord.',
            self::BANNI => 'Ce compte ne peut pas jouer sur HeraliZ.',
            self::INDISPONIBLE => 'Vérification de ton Discord impossible pour le moment : réessaie dans une minute.',
            default => 'Tout est bon.',
        };
    }

    public static function ligne(int $userId): ?object
    {
        return DB::table(Stockage::COMPTES)->where('user_id', $userId)->first();
    }

    /**
     * Lie un Discord (lu par OAuth2) au compte et le vérifie tout de suite. Rend le statut, ou « deja_lie » si ce Discord
     * est déjà lié à un autre compte.
     */
    public static function lier(User $user, array $discord): string
    {
        Stockage::tables();
        $id = (string) $discord['id'];
        $pseudo = (string) ($discord['username'] ?? '');
        if (($discord['discriminator'] ?? '0') !== '0' && ($discord['discriminator'] ?? '') !== '') {
            $pseudo .= '#'.$discord['discriminator'];
        }
        $pseudo = mb_substr($pseudo, 0, 64);

        if (DB::table(Stockage::COMPTES)->where('discord_id', $id)->where('user_id', '!=', $user->id)->exists()) {
            return 'deja_lie';
        }

        $ligne = self::ligne($user->id);
        if ($ligne !== null && $ligne->discord_id !== $id && ($ligne->banni || $ligne->statut === 'refuse')) {
            // un compte bloqué ou refusé ne change pas de Discord pour repartir de zéro
            return $ligne->banni ? self::BANNI : self::REFUSE;
        }

        $valeurs = [
            'discord_id' => $id,
            'discord_pseudo' => $pseudo,
            'discord_cree_le' => date('Y-m-d H:i:s', DiscordApi::creeLe($id)),
        ];
        if ($ligne === null) {
            DB::table(Stockage::COMPTES)->insert($valeurs + [
                'user_id' => $user->id, 'statut' => 'en_attente', 'lie_le' => now(), 'membre' => false, 'banni' => false,
            ]);
        } else {
            if ($ligne->discord_id !== $id) {
                // autre Discord : nouvelle demande au staff si elle est encore à faire, nouvelle vérification
                $valeurs += ['message_id' => null, 'verifie_le' => null, 'membre' => false, 'lie_le' => now()];
                if (Reglages::validationManuelle()) {
                    // le staff a validé l'ancien Discord, pas celui-ci : nouvelle validation
                    $valeurs['statut'] = 'en_attente';
                }
            }
            DB::table(Stockage::COMPTES)->where('id', $ligne->id)->update($valeurs);
        }

        self::verifier($user, self::ligne($user->id), true);

        return self::statut($user);
    }

    /**
     * Revérifie membre et ban auprès de Discord si la dernière vérification est assez vieille (ou si forcé). Un ban
     * trouvé bloque compte, adresses IP et PC ; un ban levé sur Discord rend les adresses IP (le compte du site et le
     * ban PC restent au staff : administration du site et /unbanpc).
     */
    public static function verifier(User $user, object $ligne, bool $forcer = false): object
    {
        $age = $ligne->verifie_le === null ? PHP_INT_MAX : time() - strtotime((string) $ligne->verifie_le);
        $delai = $ligne->membre && ! $ligne->banni ? self::DELAI_MEMBRE : self::DELAI_AUTRE;
        if (! $forcer && $age < $delai) {
            return $ligne;
        }
        if (! $forcer && Cache::has('heraliz-discord-panne:'.$user->id)) {
            return $ligne;
        }

        $membre = DiscordApi::membre($ligne->discord_id);
        // un membre n'est pas banni (Discord retire un banni du serveur) : un seul appel dans le cas courant
        $banni = $membre === true ? false : ($membre === false ? DiscordApi::banni($ligne->discord_id) : null);
        if ($membre === null || $banni === null) {
            Cache::put('heraliz-discord-panne:'.$user->id, true, self::DELAI_PANNE);

            return $ligne;
        }

        $etaitBanni = (bool) $ligne->banni;
        DB::table(Stockage::COMPTES)->where('id', $ligne->id)->update([
            'membre' => $membre, 'banni' => $banni, 'verifie_le' => now(),
        ]);
        $ligne = self::ligne($user->id);

        if ($banni && ! $etaitBanni) {
            self::propagerBan($user, $ligne);
        } elseif (! $banni && $etaitBanni) {
            $rendues = DB::table(Stockage::IPS_BANNIES)->where('user_id', $user->id)->delete();
            Log::info('HeraliZ Discord : ban Discord levé pour le compte '.$user->id.', '.$rendues.' adresse(s) IP rendue(s)');
        }

        return $ligne;
    }

    /** Note l'adresse IP (déjà ramenée au bloc /64 en IPv6 par Adresse) vue pour un compte, au plus toutes les 10 min. */
    public static function noterIp(int $userId, string $ip): void
    {
        if ($ip === '' || ! Cache::add('heraliz-ip:'.$userId.':'.$ip, true, 600)) {
            return;
        }
        try {
            Stockage::tables();
            DB::table(Stockage::IPS)->upsert([['user_id' => $userId, 'ip' => $ip, 'vu_le' => now()]], ['user_id', 'ip'], ['vu_le']);
        } catch (Throwable $e) {
            Log::warning('HeraliZ : adresse IP non notée ('.class_basename($e).')');
        }
    }

    public static function ipBannie(string $ip): bool
    {
        try {
            Stockage::tables();

            return DB::table(Stockage::IPS_BANNIES)->where('ip', $ip)->exists();
        } catch (Throwable) {
            return false;
        }
    }

    /** Compte de l'équipe du site (accès à l'administration) : pas de validation à la main (Discord lié et membre exigés). */
    public static function staffDuSite(User $user): bool
    {
        return method_exists($user, 'hasAdminAccess') && $user->hasAdminAccess();
    }

    /** Décision du staff depuis les boutons du salon privé. */
    public static function decider(int $userId, bool $accepte, string $parDiscordId): bool
    {
        Stockage::tables();

        return DB::table(Stockage::COMPTES)->where('user_id', $userId)->update([
            'statut' => $accepte ? 'accepte' : 'refuse', 'decide_par' => $parDiscordId, 'decide_le' => now(),
        ]) > 0;
    }

    /**
     * Tâche planifiée (toutes les 5 minutes) : revérifie les comptes les moins récemment vus (un ban Discord compte même
     * pour qui ne lance pas le jeu), publie les demandes de validation restées en attente et renvoie les commandes du
     * serveur de jeu pas encore parties.
     */
    public static function balayer(): void
    {
        if (! Reglages::actif()) {
            return;
        }
        Stockage::tables();

        $lignes = DB::table(Stockage::COMPTES)
            ->where(fn ($q) => $q->whereNull('verifie_le')->orWhere('verifie_le', '<', now()->subSeconds(self::DELAI_MEMBRE)))
            ->orderByRaw('verifie_le is not null, verifie_le')
            ->limit(20)
            ->get();
        foreach ($lignes as $ligne) {
            $user = User::find($ligne->user_id);
            if ($user === null) {
                continue;
            }
            $ligne = self::verifier($user, $ligne, true);
            if (! $ligne->banni && $ligne->membre && $ligne->statut === 'en_attente' && Reglages::validationManuelle()
                && ! self::staffDuSite($user)) {
                self::demander($user, $ligne);
            }
            usleep(200000);
        }

        self::envoyerCommandes();
    }

    /** Publie la demande de validation dans le salon privé du staff (une seule fois par Discord lié). */
    private static function demander(User $user, object $ligne): void
    {
        if ($ligne->message_id !== null || ! Cache::add('heraliz-discord-demande:'.$user->id, true, 300)) {
            return;
        }

        $ips = DB::table(Stockage::IPS)->where('user_id', $user->id)->pluck('ip');
        $autres = $ips->isEmpty() ? collect() : DB::table(Stockage::IPS)->whereIn('ip', $ips)->where('user_id', '!=', $user->id)
            ->distinct()->pluck('user_id');
        $noms = $autres->isEmpty() ? [] : User::whereIn('id', $autres->take(5))->pluck('name')->all();
        $creeDiscord = DiscordApi::creeLe($ligne->discord_id);
        $jours = intdiv(max(0, time() - $creeDiscord), 86400);

        $champs = [
            ['name' => 'Compte du site', 'value' => self::code($user->name), 'inline' => true],
            ['name' => 'E-mail', 'value' => self::code((string) $user->email), 'inline' => true],
            ['name' => 'Discord', 'value' => '<@'.$ligne->discord_id.'> '.self::code((string) $ligne->discord_pseudo)."\n".self::code($ligne->discord_id), 'inline' => false],
            ['name' => 'Compte Discord créé', 'value' => '<t:'.$creeDiscord.':D> ('.$jours.' jour'.($jours > 1 ? 's' : '').')'.($jours < 30 ? ' ⚠️ récent' : ''), 'inline' => true],
            ['name' => 'Compte du site créé', 'value' => $user->created_at ? '<t:'.$user->created_at->getTimestamp().':D>' : 'inconnu', 'inline' => true],
            ['name' => 'Autres comptes vus sur ses adresses IP', 'value' => $autres->count() === 0 ? 'aucun'
                : $autres->count().' : '.implode(', ', array_map(fn ($n) => self::code($n), $noms)).($autres->count() > 5 ? '…' : ''), 'inline' => false],
        ];

        $id = DiscordApi::message(Reglages::get('salon_comptes'), [
            'embeds' => [[
                'title' => 'Compte à valider (bêta)',
                'color' => 0xC62B1F,
                'fields' => $champs,
                'footer' => ['text' => 'Compte n° '.$user->id],
                'timestamp' => now()->toIso8601String(),
            ]],
            'components' => self::boutons($user->id, false),
        ]);
        if ($id !== null) {
            DB::table(Stockage::COMPTES)->where('id', $ligne->id)->update(['message_id' => $id]);
        }
    }

    /** Les boutons Accepter / Refuser d'une demande (grisés une fois la décision prise). */
    public static function boutons(int $userId, bool $grises): array
    {
        return [[
            'type' => 1,
            'components' => [
                ['type' => 2, 'style' => 3, 'label' => 'Accepter', 'custom_id' => 'hz_ok:'.$userId, 'disabled' => $grises],
                ['type' => 2, 'style' => 4, 'label' => 'Refuser', 'custom_id' => 'hz_non:'.$userId, 'disabled' => $grises],
            ],
        ]];
    }

    /**
     * Discord banni : compte du site bloqué (et déconnecté du launcher), toutes ses adresses IP bloquées, ban PC de
     * Spartan demandé au serveur de jeu, le staff prévenu dans le salon privé.
     */
    private static function propagerBan(User $user, object $ligne): void
    {
        $raison = 'Discord banni ('.$ligne->discord_id.'), compte '.$user->name;
        $ips = DB::table(Stockage::IPS)->where('user_id', $user->id)->pluck('ip');
        foreach ($ips as $ip) {
            DB::table(Stockage::IPS_BANNIES)->insertOrIgnore(['ip' => $ip, 'user_id' => $user->id, 'raison' => mb_substr($raison, 0, 190), 'cree_le' => now()]);
        }

        self::bannirSurLeSite($user, $raison);

        // ban compte + IP + PC de Spartan (/banpc = /sban <joueur> <durée> [raison]) ; le joueur doit s'être connecté au
        // moins une fois au serveur de jeu, sinon Spartan ne le connaît pas (rien d'autre à bannir)
        // pseudo de jeu seulement (3 à 16 lettres, chiffres ou _) : un nom changé dans l'administration ne glisse rien
        // d'autre dans la commande jouée par la console du serveur
        if (preg_match('/^[A-Za-z0-9_]{3,16}$/', (string) $user->name) === 1) {
            $commande = 'banpc '.$user->name.' def Ban Discord (compte du site lié à un Discord banni)';
            DB::table(Stockage::COMMANDES)->insert(['user_id' => $user->id, 'commande' => $commande, 'cree_le' => now()]);
            self::envoyerCommandes();
        }

        $salon = Reglages::get('salon_comptes');
        if ($salon !== '') {
            DiscordApi::message($salon, ['content' => '⛔ Compte du site '.self::code($user->name).' bloqué : son Discord <@'.$ligne->discord_id
                .'> est banni du serveur Discord. '.$ips->count().' adresse(s) IP bloquée(s), ban PC demandé au serveur de jeu.']);
        }
        Log::notice('HeraliZ Discord : compte '.$user->id.' bloqué (Discord banni), '.$ips->count().' adresse(s) IP');
    }

    private static function bannirSurLeSite(User $user, string $raison): void
    {
        $user->forceFill(['access_token' => null]);
        if (Schema::hasColumn('users', 'is_banned')) {
            $user->forceFill(['is_banned' => true]);
        }
        $user->save();

        // trace dans les bans d'Azuriom (administration → Bans) si sa table a la forme attendue ; sans elle, le compte
        // reste bloqué par is_banned et par l'extension
        rescue(function () use ($user, $raison) {
            if (! Schema::hasTable('bans')) {
                return;
            }
            $ligne = ['user_id' => $user->id, 'reason' => mb_substr($raison, 0, 190), 'created_at' => now(), 'updated_at' => now()];
            if (Schema::hasColumn('bans', 'author_id')) {
                // pas d'auteur du staff (ban décidé sur Discord) : le compte lui-même
                $ligne['author_id'] = $user->id;
            }
            DB::table('bans')->insert(array_filter($ligne, fn ($c) => Schema::hasColumn('bans', $c), ARRAY_FILTER_USE_KEY));
        }, null, false);
    }

    /**
     * Commandes en attente pour la console du serveur de jeu, envoyées par AzLink (serveur par défaut du site). Gardées
     * dans heraliz_commandes_serveur : si AzLink n'est pas branché, elles y restent (à jouer à la main) et sont renvoyées
     * par la tâche planifiée (10 essais au plus).
     */
    public static function envoyerCommandes(): void
    {
        $attente = DB::table(Stockage::COMMANDES)->whereNull('envoyee_le')->where('essais', '<', 10)->orderBy('id')->limit(20)->get();
        if ($attente->isEmpty()) {
            return;
        }
        $serveur = Server::find(setting('servers.default')) ?? Server::first();

        foreach ($attente as $c) {
            $erreur = null;
            try {
                $user = $c->user_id ? User::find($c->user_id) : null;
                if ($serveur === null || ! method_exists($serveur, 'bridge') || $user === null) {
                    $erreur = 'aucun serveur de jeu relié (AzLink) ou compte disparu';
                } else {
                    $serveur->bridge()->sendCommands([$c->commande], $user, false);
                }
            } catch (Throwable $e) {
                $erreur = class_basename($e).' : '.mb_substr($e->getMessage(), 0, 200);
            }
            DB::table(Stockage::COMMANDES)->where('id', $c->id)->update([
                'essais' => $c->essais + 1,
                'envoyee_le' => $erreur === null ? now() : null,
                'erreur' => $erreur,
            ]);
        }
    }

    private static function code(string $texte): string
    {
        return '`'.str_replace('`', 'ˋ', $texte).'`';
    }
}
