<?php

namespace Azuriom\Plugin\Heraliz\Votes;

use Azuriom\Models\User;
use Azuriom\Plugin\Vote\Models\Site;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Throwable;

/**
 * Passage par la page de vote, pour un site de vote dont le site ne peut pas vérifier les votes (Top-Serveurs sans son
 * jeton) : sans preuve du site de vote, la récompense ne va qu'à celui qui a cliqué « Voter » sur NOTRE page, en
 * humain (Cloudflare Turnstile, réglé dans l'administration → Paramètres → Sécurité, le même que pour l'inscription),
 * depuis la même connexion, et pas avant DELAI_MIN secondes (le temps de voter pour de vrai sur le site de vote).
 * Un programme qui appelait directement l'adresse de la récompense n'obtient plus rien.
 */
final class Clic
{
    /** Secondes entre le clic et la récompense : le temps minimal d'un vrai vote sur le site de vote. */
    public const DELAI_MIN = 20;

    /** Un clic vaut pour ce délai (la page redemande la récompense toutes les 5 s tant que le vote n'est pas fini). */
    private const DUREE = 1800;

    public static function noter(User $user, Site $site, string $ip): void
    {
        Cache::put(self::cle($user, $site), ['t' => time(), 'ip' => $ip], self::DUREE);
    }

    /** Secondes depuis le clic de ce compte sur ce site depuis cette connexion (null : pas de clic). */
    public static function depuis(User $user, Site $site, string $ip): ?int
    {
        $clic = Cache::get(self::cle($user, $site));
        if (! is_array($clic) || ($clic['ip'] ?? null) !== $ip) {
            return null;
        }

        return max(0, time() - (int) $clic['t']);
    }

    public static function oublier(User $user, Site $site): void
    {
        Cache::forget(self::cle($user, $site));
    }

    /** Clé publique Turnstile si l'anti-robot de Cloudflare est réglé dans l'administration (null sinon). */
    public static function turnstile(): ?string
    {
        $cle = trim((string) setting('captcha.site_key'));

        return setting('captcha.type') === 'turnstile' && $cle !== '' && trim((string) setting('captcha.secret_key')) !== ''
            ? $cle : null;
    }

    /** Réponse de Turnstile envoyée avec le clic vérifiée auprès de Cloudflare (vrai si Turnstile n'est pas réglé). */
    public static function humain(Request $request): bool
    {
        if (self::turnstile() === null) {
            return true;
        }
        $reponse = $request->input('cf-turnstile-response');
        if (! is_string($reponse) || $reponse === '' || strlen($reponse) > 4096) {
            return false;
        }

        try {
            $r = Http::asForm()->timeout(4)->post('https://challenges.cloudflare.com/turnstile/v0/siteverify', [
                'secret' => (string) setting('captcha.secret_key'),
                'response' => $reponse,
                'remoteip' => (string) $request->ip(),
            ]);
        } catch (Throwable) {
            return false;
        }

        return $r->successful() && $r->json('success') === true;
    }

    private static function cle(User $user, Site $site): string
    {
        return 'heraliz-vote-clic:'.$site->id.':'.$user->id;
    }
}
