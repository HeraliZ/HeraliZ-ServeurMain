<?php

namespace Azuriom\Plugin\Heraliz;

use Illuminate\Http\Request;

/**
 * Adresse IP utilisée par toutes les limites de l'extension (connexion, création de compte, double authentification,
 * jetons de jeu). Une adresse IPv6 compte pour tout son bloc /64 : un seul abonné en reçoit des milliards, il pourrait
 * sinon en changer à chaque essai.
 */
final class Adresse
{
    public static function de(Request $request): string
    {
        $ip = (string) $request->ip();

        if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) {
            $octets = inet_pton($ip);

            // adresse IPv4 écrite en IPv6 (::ffff:1.2.3.4) : c'est l'adresse IPv4 qui compte
            if (str_starts_with($octets, str_repeat("\0", 10)."\xff\xff")) {
                return inet_ntop(substr($octets, 12));
            }

            return bin2hex(substr($octets, 0, 8)).'::/64';
        }

        return $ip;
    }
}
