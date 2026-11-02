<?php

namespace Azuriom\Plugin\Heraliz;

use Illuminate\Support\Facades\Http;
use Throwable;

/**
 * Anti-robot Cloudflare Turnstile, celui réglé dans l'administration du site (Paramètres → Sécurité, captcha
 * « Turnstile ») pour l'inscription sur le site. Sert aussi au bouton « Voter » (Votes\Clic) et à la création de compte
 * depuis le launcher (InscriptionController : le launcher ouvre la page /heraliz/robot, RobotController).
 */
final class Turnstile
{
    /** Clé publique Turnstile si l'anti-robot de Cloudflare est réglé dans l'administration (null sinon). */
    public static function cle(): ?string
    {
        $cle = trim((string) setting('captcha.site_key'));

        return setting('captcha.type') === 'turnstile' && $cle !== '' && trim((string) setting('captcha.secret_key')) !== ''
            ? $cle : null;
    }

    /**
     * Réponse de Turnstile vérifiée auprès de Cloudflare pour cette adresse IP. Faux si elle manque, est mal formée,
     * refusée ou si Cloudflare ne répond pas (rien ne passe dans le doute).
     */
    public static function valide(mixed $reponse, string $ip): bool
    {
        if (! is_string($reponse) || $reponse === '' || strlen($reponse) > 4096) {
            return false;
        }

        try {
            $r = Http::asForm()->timeout(5)->post('https://challenges.cloudflare.com/turnstile/v0/siteverify', [
                'secret' => (string) setting('captcha.secret_key'),
                'response' => $reponse,
                'remoteip' => $ip,
            ]);
        } catch (Throwable) {
            return false;
        }

        return $r->successful() && $r->json('success') === true;
    }
}
