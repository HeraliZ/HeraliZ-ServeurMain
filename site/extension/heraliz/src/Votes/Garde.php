<?php

namespace Azuriom\Plugin\Heraliz\Votes;

use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Azuriom\Plugin\Heraliz\Discord\DiscordApi;
use Azuriom\Plugin\Heraliz\Discord\Reglages;
use Azuriom\Plugin\Vote\Models\Site;
use Azuriom\Plugin\Vote\Verification\VoteChecker;
use Carbon\Carbon;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Règles des votes contre les bots et les fermes de comptes (27/10, demande du propriétaire). Chaque vote donne une clé,
 * des hérals et des PB : un vote ne rapporte que
 *  - à un compte connecté au site (réglage « vote.auth-required » de l'extension Vote, remis par reglages.php) ;
 *  - dont le Discord est lié, membre et validé comme pour jouer (Discord\Comptes), et qui a déjà joué (TempsDeJeu) ;
 *  - si le site de vote a vu le vote quand sa vérification est branchée (jeton Top-Serveurs : VerificationStricte) ;
 *    sinon, seulement après un clic sur « Voter » de notre page, anti-robot passé, il y a au moins 20 s (Clic) ;
 *  - une fois par connexion internet et par site sur la durée du vote, tous comptes confondus.
 * Une même connexion qui vote pour plusieurs comptes en 24 h est signalée au staff (salon privé des comptes).
 * Appliqué par Middleware\VotesProteges sur la route du vote, et montré à l'avance sur la page de vote (thème) : un joueur
 * ne gâche pas son vote sur Top-Serveurs pour rien.
 */
final class Garde
{
    /** Comptes différents vus sur une même connexion en 24 h à partir desquels le staff est prévenu. */
    public const SEUIL_ALERTE = 3;

    public const MESSAGE_SANS_CLIC = 'Pour être récompensé, clique sur « Voter » sur la page de vote du site HeraliZ, puis vote.';

    /**
     * Ce qui empêche ce compte de voter (null : rien), avec de quoi le régler : ['message' => …, 'lien' => …|null,
     * 'bouton' => …|null].
     */
    public static function compte(User $user, Request $request): ?array
    {
        $statut = Comptes::statut($user, Adresse::de($request));
        if ($statut !== Comptes::OK) {
            return match ($statut) {
                Comptes::A_LIER => ['message' => 'Lie ton compte Discord pour voter, comme pour jouer.',
                    'lien' => route('heraliz.discord.index'), 'bouton' => 'Lier mon Discord'],
                Comptes::PAS_MEMBRE => ['message' => 'Rejoins le Discord HeraliZ avec le compte Discord lié pour voter.',
                    'lien' => Reglages::invitation(), 'bouton' => 'Rejoindre le Discord'],
                Comptes::EN_ATTENTE => ['message' => 'Ton compte attend la validation du staff : tu pourras voter dès qu’il sera accepté.',
                    'lien' => null, 'bouton' => null],
                default => ['message' => Comptes::message($statut), 'lien' => null, 'bouton' => null],
            };
        }

        if (! TempsDeJeu::suffisant($user)) {
            $minutes = TempsDeJeu::minutes($user);

            return ['message' => 'Joue au moins '.TempsDeJeu::MINUTES_MIN.' minutes sur HeraliZ avant de voter (pour l’instant : '
                .$minutes.' min). Les votes récompensent les joueurs, pas les comptes vides.', 'lien' => null, 'bouton' => null];
        }

        return null;
    }

    /** Ce qui empêche ce vote sur ce site en particulier (null : rien) : connexion déjà récompensée pour un autre compte. */
    public static function pourSite(Site $site, User $user, Request $request): ?string
    {
        $jusqua = self::ipPrise($site, Adresse::de($request), $user->id);

        return $jusqua === null ? null : self::messageIp($jusqua);
    }

    /**
     * La vérification du site de vote est branchée : case « vérification » cochée, site connu de l'extension Vote, et
     * jeton présent quand le site en demande un (Top-Serveurs). Sinon, le passage par notre page est exigé (Clic).
     */
    public static function verifiable(Site $site): bool
    {
        if (! $site->has_verification) {
            return false;
        }
        if (VerificationStricte::geree($site)) {
            return trim((string) $site->verification_key) !== '';
        }

        try {
            $host = Site::hostFromUrl((string) $site->url);
            $verif = $host === null ? null : app(VoteChecker::class)->getVerificationForSite($host);
            if ($verif === null) {
                return false;
            }
            if ($verif->requireVerificationKey()) {
                return trim((string) $site->verification_key) !== '';
            }
            // identifiant du serveur lu dans l'adresse de vote (sinon l'extension Vote accepterait tout)
            $lire = $verif->verificationTypeKey();

            return $lire instanceof Closure && $lire((string) $site->url) !== null;
        } catch (Throwable) {
            return false;
        }
    }

    /** Durée d'un vote sur ce site, en minutes (une fois par jour pour un site à heure fixe). */
    public static function fenetre(Site $site): int
    {
        return $site->vote_reset_at !== null ? 1440 : max(1, (int) $site->vote_delay);
    }

    /** Jusqu'à quand cette connexion est prise par le vote récompensé d'un AUTRE compte sur ce site (null : libre). */
    public static function ipPrise(Site $site, string $ip, int $userId): ?Carbon
    {
        Stockage::tables();
        $fenetre = self::fenetre($site);
        $dernier = DB::table(Stockage::VOTES)
            ->where('ip', $ip)->where('site_id', $site->id)->where('recompense', true)
            ->where('user_id', '!=', $userId)
            ->where('cree_le', '>=', now()->subMinutes($fenetre))
            ->max('cree_le');

        return $dernier === null ? null : Carbon::parse($dernier)->addMinutes($fenetre);
    }

    public static function messageIp(Carbon $jusqua): string
    {
        return 'Un vote récompensé par connexion internet et par site : un autre compte a déjà voté depuis ta connexion. '
            .'Prochain vote possible '.$jusqua->locale('fr')->diffForHumans(['parts' => 2, 'join' => true]).'.';
    }

    /** Note un vote récompensé, ou un vote refusé parce que la connexion était déjà prise (au plus un toutes les 10 min). */
    public static function noter(int $userId, Site $site, string $ip, bool $recompense): void
    {
        try {
            Stockage::tables();
            if (! $recompense && ! Cache::add('heraliz-vote-refus:'.$site->id.':'.$userId.':'.$ip, true, 600)) {
                return;
            }
            DB::table(Stockage::VOTES)->insert([
                'user_id' => $userId, 'site_id' => $site->id, 'ip' => $ip, 'recompense' => $recompense, 'cree_le' => now(),
            ]);
            self::surveiller($ip);
        } catch (Throwable $e) {
            Log::warning('HeraliZ votes : vote non noté ('.class_basename($e).')');
        }
    }

    /** Une même connexion a voté (ou essayé) pour SEUIL_ALERTE comptes ou plus en 24 h : le staff est prévenu. */
    private static function surveiller(string $ip): void
    {
        $lignes = DB::table(Stockage::VOTES)->where('ip', $ip)->where('cree_le', '>=', now()->subDay())->get(['user_id', 'recompense']);
        $comptes = $lignes->pluck('user_id')->unique()->values();
        // une alerte par nombre de comptes atteint : 3, puis 4, 5… (pas une à chaque vote)
        if ($comptes->count() < self::SEUIL_ALERTE || ! Cache::add('heraliz-vote-alerte:'.$ip.':'.$comptes->count(), true, 86400)) {
            return;
        }

        $noms = User::whereIn('id', $comptes->take(10))->pluck('name')->map(fn ($n) => '`'.str_replace('`', 'ˋ', (string) $n).'`')->all();
        $recompenses = $lignes->where('recompense', true)->count();
        $texte = '🗳️ Votes : une même connexion internet (`'.self::lisible($ip).'`) a voté pour **'.$comptes->count().' comptes** en 24 h : '
            .implode(', ', $noms).($comptes->count() > 10 ? '…' : '').'. Votes récompensés : '.$recompenses
            .', refusés (connexion déjà récompensée) : '.($lignes->count() - $recompenses)
            .'. Possible ferme de comptes : à vérifier (même famille, même box ?).';

        Log::notice('HeraliZ votes : connexion '.$ip.' vue sur '.$comptes->count().' comptes en 24 h');
        self::prevenirStaff($texte);
    }

    /** Bloc IPv6 noté par Adresse (« 20010db800010002::/64 ») écrit comme d'habitude (« 2001:db8:1:2::/64 »). */
    private static function lisible(string $ip): string
    {
        if (preg_match('/^([0-9a-f]{16})::\/64$/', $ip, $m) === 1) {
            $texte = inet_ntop(hex2bin($m[1]).str_repeat("\0", 8));

            return $texte === false ? $ip : $texte.'/64';
        }

        return $ip;
    }

    /** Message dans le salon privé du staff (celui de la validation des comptes), si le bot Discord est réglé. */
    public static function prevenirStaff(string $texte): void
    {
        $salon = Reglages::get('salon_comptes');
        if ($salon === '' || Reglages::get('bot_token') === '') {
            return;
        }
        rescue(fn () => DiscordApi::message($salon, ['content' => mb_substr($texte, 0, 1900)]), null, false);
    }
}
