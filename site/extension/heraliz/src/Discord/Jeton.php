<?php

namespace Azuriom\Plugin\Heraliz\Discord;

/**
 * Jetons signés du lien Discord (HMAC-SHA256 avec une clé tirée de APP_KEY du site : rien de plus à garder) :
 *  - « lien » : adresse donnée au launcher pour UN compte, valable 5 minutes et à usage unique (consommée quand le
 *    navigateur part vers Discord : un lien qui traîne dans un historique ou une capture d'écran ne sert plus) ; le
 *    launcher l'ouvre dans le navigateur, où le joueur n'est pas forcément connecté au site ;
 *  - « etat » : paramètre state envoyé à Discord, valable 10 minutes, à usage unique et lié au navigateur (session).
 */
final class Jeton
{
    public const LIEN_SECONDES = 300;
    public const ETAT_SECONDES = 600;

    public static function creer(string $type, int $userId, int $duree): string
    {
        $donnees = implode('|', [$type, $userId, time() + $duree, bin2hex(random_bytes(12))]);

        return self::b64($donnees).'.'.self::b64(hash_hmac('sha256', $donnees, self::cle(), true));
    }

    /**
     * [compte, nonce] si le jeton est bon, du bon type et pas périmé ; null sinon.
     *
     * @return array{0:int,1:string}|null
     */
    public static function lire(?string $jeton, string $type): ?array
    {
        if (! is_string($jeton) || substr_count($jeton, '.') !== 1 || strlen($jeton) > 300) {
            return null;
        }
        [$donnees, $signature] = explode('.', $jeton);
        $donnees = self::deb64($donnees);
        if ($donnees === null || ! hash_equals(hash_hmac('sha256', $donnees, self::cle(), true), (string) self::deb64($signature))) {
            return null;
        }
        $parts = explode('|', $donnees);
        if (count($parts) !== 4 || $parts[0] !== $type || ! ctype_digit($parts[1]) || ! ctype_digit($parts[2]) || (int) $parts[2] < time()) {
            return null;
        }

        return [(int) $parts[1], $parts[3]];
    }

    /** Consomme un jeton « lien » (son nonce) : true la première fois seulement. */
    public static function utiliser(string $nonce): bool
    {
        return \Illuminate\Support\Facades\Cache::add('heraliz-discord-lien-utilise:'.$nonce, true, self::LIEN_SECONDES + 60);
    }

    /** Le jeton « lien » (son nonce) a-t-il déjà servi à partir vers Discord ? */
    public static function utilise(string $nonce): bool
    {
        return \Illuminate\Support\Facades\Cache::has('heraliz-discord-lien-utilise:'.$nonce);
    }

    private static function cle(): string
    {
        return hash_hmac('sha256', 'heraliz-discord-lien', (string) config('app.key'), true);
    }

    private static function b64(string $v): string
    {
        return rtrim(strtr(base64_encode($v), '+/', '-_'), '=');
    }

    private static function deb64(string $v): ?string
    {
        $r = base64_decode(strtr($v, '-_', '+/'), true);

        return $r === false ? null : $r;
    }
}
