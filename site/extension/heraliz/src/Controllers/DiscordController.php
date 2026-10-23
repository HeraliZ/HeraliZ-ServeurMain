<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Controllers\Concerns\CompteApi;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Azuriom\Plugin\Heraliz\Discord\DiscordApi;
use Azuriom\Plugin\Heraliz\Discord\Jeton;
use Azuriom\Plugin\Heraliz\Discord\Reglages;
use Azuriom\Plugin\Heraliz\Discord\Stockage;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * Discord obligatoire (bêta), voir Discord\Comptes :
 *  - pages du site : /heraliz/discord (état du compte connecté), /heraliz/discord/lier (vers la connexion Discord, avec
 *    le jeton donné au launcher ou le compte connecté au site), /heraliz/discord/retour (retour de Discord) ;
 *  - API du launcher : POST /api/heraliz/compte/etat ;
 *  - boutons Accepter / Refuser du salon privé du staff : POST /api/heraliz/discord/interactions (appelé par Discord,
 *    signé Ed25519 avec la clé publique de l'application).
 */
class DiscordController extends Controller
{
    use CompteApi;

    /** État du compte, pour le launcher (même connexion que le pack et les jetons : access_token). */
    public function etatCompte(Request $request)
    {
        if (! setting('auth_api', false)) {
            return response()->json(['status' => 'error', 'message' => 'Auth API is not enabled'], 400);
        }
        $this->validate($request, ['access_token' => ['required', 'string']]);
        $user = User::firstWhere('access_token', $request->input('access_token'));
        if ($user === null) {
            return response()->json(['status' => 'error', 'reason' => 'invalid_token', 'message' => 'Invalid token'], 401);
        }

        $statut = $user->isBanned() ? Comptes::BANNI : Comptes::statut($user, Adresse::de($request));
        $ligne = Reglages::actif() ? Comptes::ligne($user->id) : null;

        return response()->json([
            'statut' => $statut,
            'message' => Comptes::message($statut),
            'discord' => $ligne === null ? null : ['pseudo' => $ligne->discord_pseudo],
            'lien' => Reglages::actif() && $statut !== Comptes::BANNI
                ? route('heraliz.discord.lier', ['jeton' => Jeton::creer('lien', $user->id, Jeton::LIEN_SECONDES)]) : null,
            'invitation' => Reglages::invitation(),
            'validation_manuelle' => Reglages::actif() && Reglages::validationManuelle(),
        ], 200, ['Cache-Control' => 'no-store']);
    }

    /** Page « Mon Discord » du site (menu du compte). */
    public function index(Request $request)
    {
        $user = $request->user();

        return $this->page($user, Reglages::actif() ? Comptes::statut($user, Adresse::de($request)) : 'desactive', $this->lienDe($user));
    }

    /** Vers la connexion Discord, pour le compte du jeton (launcher) ou celui connecté au site. */
    public function lier(Request $request)
    {
        $user = $this->compteDuLien($request);
        if ($user === null) {
            return Auth::check() || $request->filled('jeton')
                ? $this->page(null, 'expire')
                : redirect()->guest(route('login'));
        }
        if (! Reglages::actif()) {
            return $this->page($user, 'desactive');
        }
        if ($user->isBanned()) {
            return $this->page($user, Comptes::BANNI);
        }

        [, $nonce] = Jeton::lire($etat = Jeton::creer('etat', $user->id, Jeton::ETAT_SECONDES), 'etat');
        $request->session()->put('heraliz_discord_etat', $nonce);
        // le jeton du launcher, pour les boutons « Vérifier » de la page de retour
        $request->session()->put('heraliz_discord_lien', $request->query('jeton'));

        return redirect()->away(DiscordApi::autorisation($etat));
    }

    /** Revérifie tout de suite (membre, ban) le Discord déjà lié, puis montre l'état : bouton « J'ai rejoint ». */
    public function verifier(Request $request)
    {
        $user = $this->compteDuLien($request);
        if ($user === null) {
            return Auth::check() || $request->filled('jeton') ? $this->page(null, 'expire') : redirect()->guest(route('login'));
        }
        if (! Reglages::actif()) {
            return $this->page($user, 'desactive');
        }
        Stockage::tables();
        $ligne = Comptes::ligne($user->id);
        // au plus une vérification forcée toutes les 10 s par compte (le bot a ses limites chez Discord)
        if ($ligne !== null && Cache::add('heraliz-discord-verif:'.$user->id, true, 10)) {
            Comptes::verifier($user, $ligne, true);
        }
        $lien = $request->filled('jeton') ? (string) $request->query('jeton') : null;

        return $this->page($user, $user->isBanned() ? Comptes::BANNI : Comptes::statut($user, Adresse::de($request)), $this->lienDe($user, $lien));
    }

    /** Retour de Discord : code échangé, Discord lu, lié, vérifié ; page du résultat. */
    public function retour(Request $request)
    {
        $lu = Jeton::lire($request->query('state'), 'etat');
        $nonce = $request->session()->pull('heraliz_discord_etat');
        $lien = $request->session()->get('heraliz_discord_lien');
        if ($lu === null || ! is_string($nonce) || ! hash_equals($nonce, $lu[1]) || ! Cache::add('heraliz-discord-etat:'.$lu[1], true, Jeton::ETAT_SECONDES)) {
            return $this->page(null, 'expire');
        }
        $user = User::find($lu[0]);
        if ($user === null || ! Reglages::actif()) {
            return $this->page($user, $user === null ? 'expire' : 'desactive');
        }
        if ($request->filled('error') || ! is_string($request->query('code'))) {
            return $this->page($user, 'annule', $this->lienDe($user, $lien));
        }

        $discord = DiscordApi::utilisateur($request->query('code'));
        if ($discord === null) {
            return $this->page($user, 'erreur', $this->lienDe($user, $lien));
        }

        $statut = Comptes::lier($user, $discord);
        Comptes::noterIp($user->id, Adresse::de($request));

        return $this->page($user, $statut, $this->lienDe($user, $lien));
    }

    /**
     * Boutons du salon privé du staff. Discord exige une réponse en moins de 3 s et refuse l'adresse si une requête mal
     * signée est acceptée : tout ce qui n'est pas signé par l'application est refusé (401).
     */
    public function interactions(Request $request)
    {
        $corps = $request->getContent();
        $signature = (string) $request->header('X-Signature-Ed25519');
        $horodatage = (string) $request->header('X-Signature-Timestamp');
        $cle = Reglages::get('public_key');

        if (! function_exists('sodium_crypto_sign_verify_detached') || strlen($cle) !== 64 || ! ctype_xdigit($cle)
            || strlen($signature) !== 128 || ! ctype_xdigit($signature) || ! ctype_digit($horodatage)
            || abs(time() - (int) $horodatage) > 300
            || ! sodium_crypto_sign_verify_detached(hex2bin($signature), $horodatage.$corps, hex2bin($cle))) {
            return response()->json(['erreur' => 'signature'], 401);
        }

        $donnees = json_decode($corps, true);
        if (! is_array($donnees)) {
            return response()->json(['erreur' => 'requete'], 400);
        }
        // PING de Discord (vérification de l'adresse dans le portail des développeurs)
        if (($donnees['type'] ?? null) === 1) {
            return response()->json(['type' => 1]);
        }
        if (($donnees['type'] ?? null) !== 3 || ! preg_match('/^hz_(ok|non):(\d+)$/', (string) ($donnees['data']['custom_id'] ?? ''), $m)) {
            return $this->ephemere('Action inconnue.');
        }

        $roles = $donnees['member']['roles'] ?? [];
        $staff = (string) ($donnees['member']['user']['id'] ?? '');
        if (($donnees['guild_id'] ?? null) !== Reglages::get('guild_id') || ! is_array($roles)
            || array_intersect(array_map('strval', $roles), Reglages::rolesStaff()) === [] || $staff === '') {
            return $this->ephemere('Réservé au staff.');
        }

        $userId = (int) $m[2];
        $accepte = $m[1] === 'ok';
        if (! Comptes::decider($userId, $accepte, $staff)) {
            return $this->ephemere('Ce compte n’a plus de Discord lié.');
        }
        Log::info('HeraliZ Discord : compte '.$userId.' '.($accepte ? 'accepté' : 'refusé').' par '.$staff);

        // la demande, avec la décision et les boutons grisés
        $embed = $donnees['message']['embeds'][0] ?? ['title' => 'Compte à valider (bêta)'];
        $embed['color'] = $accepte ? 0x2E9E4F : 0x6B6B6B;
        $embed['fields'] = array_values(array_filter($embed['fields'] ?? [], fn ($f) => ($f['name'] ?? '') !== 'Décision'));
        $embed['fields'][] = ['name' => 'Décision', 'value' => ($accepte ? '✅ Accepté' : '❌ Refusé').' par <@'.$staff.'> <t:'.time().':R>', 'inline' => false];

        return response()->json([
            'type' => 7,
            'data' => ['embeds' => [$embed], 'components' => Comptes::boutons($userId, true), 'allowed_mentions' => ['parse' => []]],
        ]);
    }

    private function ephemere(string $texte): JsonResponse
    {
        return response()->json(['type' => 4, 'data' => ['content' => $texte, 'flags' => 64]]);
    }

    private function compteDuLien(Request $request): ?User
    {
        if ($request->filled('jeton')) {
            $lu = Jeton::lire((string) $request->query('jeton'), 'lien');

            return $lu === null ? null : User::find($lu[0]);
        }

        return $request->user();
    }

    /** Adresse du bouton « Lier / Vérifier » : le jeton du launcher s'il y en a un, sinon la page du compte connecté. */
    private function lienDe(?User $user, ?string $jeton = null): ?string
    {
        if ($user === null) {
            return null;
        }
        if (is_string($jeton) && Jeton::lire($jeton, 'lien') !== null) {
            return route('heraliz.discord.lier', ['jeton' => $jeton]);
        }

        return route('heraliz.discord.lier', ['jeton' => Jeton::creer('lien', $user->id, Jeton::LIEN_SECONDES)]);
    }

    private function page(?User $user, string $cas, ?string $lien = null)
    {
        $invitation = Reglages::invitation();
        $ligne = null;
        if ($user !== null && Reglages::actif()) {
            Stockage::tables();
            $ligne = Comptes::ligne($user->id);
        }
        $verifier = $lien === null ? null : str_replace('/discord/lier?', '/discord/verifier?', $lien);

        [$titre, $texte, $boutons] = match ($cas) {
            Comptes::OK => ['Discord lié', 'Tout est bon : tu peux retourner au launcher et jouer.', []],
            Comptes::A_LIER => ['Lie ton Discord', 'Pendant la bêta, chaque compte HeraliZ doit être lié à un compte Discord membre du serveur Discord HeraliZ.',
                [['Lier mon Discord', $lien, 'btn-primary']]],
            Comptes::PAS_MEMBRE => ['Rejoins notre Discord', 'Ton Discord est lié, mais il n’est pas membre du serveur Discord HeraliZ. Rejoins-le avec ce même compte Discord, puis clique sur « J’ai rejoint ».',
                [['Rejoindre le Discord', $invitation, 'btn-primary'], ['J’ai rejoint', $verifier, 'btn-outline-light']]],
            Comptes::EN_ATTENTE => ['En attente du staff', 'Discord lié. Pendant la bêta, le staff valide chaque compte à la main : reviens dans le launcher un peu plus tard.',
                [['Vérifier', $verifier, 'btn-outline-light'], ['Ouvrir le Discord', $invitation, 'btn-outline-light']]],
            Comptes::REFUSE => ['Compte refusé', 'Ton compte n’a pas été accepté pour la bêta. Pour en parler, ouvre un ticket sur le Discord.',
                [['Ouvrir le Discord', $invitation, 'btn-outline-light']]],
            Comptes::BANNI => ['Accès refusé', 'Ce Discord est banni du serveur Discord HeraliZ : ce compte ne peut pas jouer.', []],
            Comptes::INDISPONIBLE => ['Discord injoignable', 'La vérification auprès de Discord a échoué. Réessaie dans une minute.',
                [['Réessayer', $verifier, 'btn-primary']]],
            'erreur' => ['Discord injoignable', 'La connexion à Discord a échoué. Réessaie dans une minute.',
                [['Réessayer', $lien, 'btn-primary']]],
            'deja_lie' => ['Discord déjà utilisé', 'Ce compte Discord est déjà lié à un autre compte HeraliZ. Un Discord ne sert qu’à un seul compte.',
                [['Lier un autre Discord', $lien, 'btn-outline-light']]],
            'annule' => ['Liaison annulée', 'Tu as annulé la connexion à Discord. Sans Discord lié, pas de partie pendant la bêta.',
                [['Recommencer', $lien, 'btn-primary']]],
            'desactive' => ['Discord', 'Lier son Discord n’est pas demandé pour le moment.', []],
            default => ['Lien périmé', 'Ce lien n’est plus valable. Rouvre-le depuis le launcher, ou depuis ton compte sur le site.', []],
        };
        if ($ligne !== null && $cas === Comptes::OK) {
            $boutons[] = ['Lier un autre Discord', $lien, 'btn-outline-light'];
        }

        return response()->view('heraliz::discord', [
            'titre' => $titre,
            'texte' => $texte,
            'boutons' => array_values(array_filter($boutons, fn ($b) => is_string($b[1]) && $b[1] !== '')),
            'pseudo' => $user?->name,
            'discord' => $ligne?->discord_pseudo,
        ], $cas === 'expire' ? 410 : 200)->header('Cache-Control', 'no-store');
    }
}
