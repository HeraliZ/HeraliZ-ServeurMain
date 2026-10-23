<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Plugin\Heraliz\Controllers\Concerns\CompteApi;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use JsonException;

/**
 * Pack de jeu du launcher réservé aux comptes HeraliZ. Le launcher demande ici, avec le jeton du compte connecté, la
 * distribution (liste des mods et fichiers du jeu, déposée sur la VM par la mise en ligne du serveur) : chaque lien vers
 * le pack (/launcher/...) y porte une signature valable 6 heures. Pack verrouillé (site/vm/theme.sh), nginx refuse tout
 * fichier du pack sans signature valable : sans compte, plus rien ne se télécharge, et un lien partagé ne sert que
 * quelques heures. Même calcul que le module secure_link de nginx : MD5 de « <expiration><chemin> <clé> », en base64
 * pour adresse. La clé est créée sur la VM par theme.sh : ni dans le dépôt, ni dans le launcher.
 */
class PackController extends Controller
{
    use CompteApi;

    /** Dossier du pack sur la VM (site/vm/installer.sh), rempli par la mise en ligne du serveur (serveur.yml). */
    private const DISTRIBUTION = '/var/www/launcher/distribution.json';

    /** Début du chemin des fichiers du pack dans les adresses (location /launcher/ de nginx). */
    private const PREFIXE = '/launcher/';

    // une soirée de jeu, même sur une connexion lente (nginx ne regarde la signature qu'au début de chaque fichier)
    private const DUREE_SECONDES = 6 * 3600;

    public function distribution(Request $request)
    {
        $user = $this->compte($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }
        // Discord lié, membre, pas banni et accepté par le staff (Discord\Comptes)
        if (($refus = $this->discord($user, $request)) !== null) {
            return $refus;
        }

        try {
            $distribution = json_decode((string) @file_get_contents(self::DISTRIBUTION), false, 512, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            $distribution = null;
        }
        if (! is_object($distribution) || ! is_array($distribution->servers ?? null)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'pack_absent',
                'message' => 'The game pack is not available',
            ], 503);
        }

        $cle = self::cle();
        if ($cle !== null) {
            $expire = time() + self::DUREE_SECONDES;
            foreach ($distribution->servers as $serveur) {
                if (is_object($serveur) && is_array($serveur->modules ?? null)) {
                    self::signerModules($serveur->modules, $cle, $expire);
                }
            }
        }

        // objets JSON gardés tels quels (un « {} » reste un objet) : le launcher reçoit la distribution d'origine
        $options = JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRESERVE_ZERO_FRACTION | JSON_THROW_ON_ERROR;

        return response(json_encode($distribution, $options), 200, [
            'Content-Type' => 'application/json',
            'Cache-Control' => 'no-store',
        ]);
    }

    /**
     * Le lien signé d'un fichier du pack, ou le lien tel quel s'il ne mène pas au pack (Forge, Mojang...).
     */
    public static function signer(string $url, string $cle, int $expire): string
    {
        $chemin = parse_url($url, PHP_URL_PATH);
        if (! is_string($chemin) || ! str_starts_with($chemin, self::PREFIXE) || parse_url($url, PHP_URL_QUERY) !== null
            || parse_url($url, PHP_URL_FRAGMENT) !== null) {
            return $url;
        }

        // nginx signe le chemin décodé ($uri : « %20 » devient une espace, « %2B » un « + »)
        $empreinte = md5($expire.rawurldecode($chemin).' '.$cle, true);

        return $url.'?e='.$expire.'&s='.rtrim(strtr(base64_encode($empreinte), '+/', '-_'), '=');
    }

    /**
     * La clé du pack (storage/app/heraliz/pack.cle, 32 caractères au moins), ou null si elle manque : liens non signés.
     */
    public static function cle(): ?string
    {
        $fichier = storage_path('app/heraliz/pack.cle');
        $cle = is_readable($fichier) ? trim((string) file_get_contents($fichier)) : '';

        return strlen($cle) >= 32 ? $cle : null;
    }

    private static function signerModules(array $modules, string $cle, int $expire): void
    {
        foreach ($modules as $module) {
            if (! is_object($module)) {
                continue;
            }
            if (is_object($module->artifact ?? null) && is_string($module->artifact->url ?? null)) {
                $module->artifact->url = self::signer($module->artifact->url, $cle, $expire);
            }
            if (is_array($module->subModules ?? null)) {
                self::signerModules($module->subModules, $cle, $expire);
            }
        }
    }
}
