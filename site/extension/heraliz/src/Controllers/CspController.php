<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * Rapports de la politique de contenu (CSP) du site, en mode rapport seulement (en-tête posé par nginx :
 * site/vm/installer.sh, audit du 02/11, point 41) : le navigateur d'un visiteur signale ce que la politique bloquerait.
 * Chaque écart (directive et site bloqué) est noté une fois par jour dans le journal du site (storage/logs), sans
 * adresse IP ni compte : quand le journal n'en montre plus, la politique peut passer en vrai blocage.
 */
class CspController extends Controller
{
    public function rapport(Request $request)
    {
        $corps = json_decode(substr((string) $request->getContent(), 0, 20000), true);
        $rapports = is_array($corps) && isset($corps['csp-report']) ? [$corps['csp-report']] : (is_array($corps) ? $corps : []);
        foreach (array_slice($rapports, 0, 10) as $r) {
            $r = is_array($r) && isset($r['body']) && is_array($r['body']) ? $r['body'] : $r;
            if (! is_array($r)) {
                continue;
            }
            $directive = self::texte($r['effective-directive'] ?? $r['effectiveDirective'] ?? $r['violated-directive'] ?? '?');
            $bloque = self::origine($r['blocked-uri'] ?? $r['blockedURL'] ?? '?');
            $page = self::texte(parse_url((string) ($r['document-uri'] ?? $r['documentURL'] ?? ''), PHP_URL_PATH) ?: '?');
            if (Cache::add('heraliz-csp:'.sha1($directive.'|'.$bloque), true, 86400)) {
                Log::notice('HeraliZ CSP (rapport seulement) : '.$directive.' bloquerait « '.$bloque.' » (page '.$page.')');
            }
        }

        return response()->noContent();
    }

    /** Le site bloqué seulement (« https://exemple.fr »), ou le mot-clé du navigateur (« inline », « eval »…). */
    private static function origine(mixed $adresse): string
    {
        $adresse = self::texte($adresse);
        $hote = parse_url($adresse, PHP_URL_HOST);

        return is_string($hote) ? (parse_url($adresse, PHP_URL_SCHEME) ?: 'https').'://'.$hote : $adresse;
    }

    private static function texte(mixed $valeur): string
    {
        return mb_substr(preg_replace('/[^\w:\/.\- ]/u', '', is_scalar($valeur) ? (string) $valeur : '?') ?? '?', 0, 120);
    }
}
