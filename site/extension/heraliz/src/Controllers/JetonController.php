<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Plugin\Heraliz\Controllers\Concerns\CompteApi;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Jeton de lancement de Spartan (anticheat du serveur) signé par le site : le launcher le demande à chaque partie,
 * pour le compte connecté (jamais pour un pseudo envoyé par le launcher), et le serveur de jeu le vérifie avec la même
 * clé (mise en ligne du serveur). La clé est créée sur la VM par site/vm/theme.sh : elle n'est ni dans le dépôt ni dans
 * le launcher, aucun autre launcher ne peut fabriquer de jeton. Format : LaunchToken.java du mod Spartan.
 */
class JetonController extends Controller
{
    use CompteApi;

    // une soirée de jeu : le serveur ne vérifie le jeton qu'à la connexion
    private const DUREE_SECONDES = 6 * 3600;

    public function creer(Request $request)
    {
        // empreinte du dossier mods du jeu, calculée par le launcher ; le serveur la compare à celle du jeu lancé
        $this->validate($request, ['empreinte' => ['required', 'string', 'regex:/^[0-9a-f]{64}$/']]);

        $user = $this->compte($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }
        // Discord lié, membre, pas banni et accepté par le staff (Discord\Comptes)
        if (($refus = $this->discord($user, $request)) !== null) {
            return $refus;
        }

        $cle = self::cle();
        if ($cle === null) {
            return response()->json([
                'status' => 'error',
                'reason' => 'jeton_indisponible',
                'message' => 'Launch tokens are not configured',
            ], 503);
        }

        $expire = time() + self::DUREE_SECONDES;
        $payload = implode('|', [
            'v1', self::base64Url($user->name), (string) $expire, $request->input('empreinte'), bin2hex(random_bytes(12)),
        ]);

        return response()->json([
            'jeton' => self::base64Url($payload).'.'.self::base64Url(hash_hmac('sha256', $payload, $cle, true)),
            'expire' => $expire,
        ]);
    }

    /**
     * La clé des jetons (storage/app/heraliz/jeton-jeu.cle, 32 caractères au moins), ou null si elle manque.
     */
    public static function cle(): ?string
    {
        $fichier = storage_path('app/heraliz/jeton-jeu.cle');
        $cle = is_readable($fichier) ? trim((string) file_get_contents($fichier)) : '';

        return strlen($cle) >= 32 ? $cle : null;
    }

    private static function base64Url(string $valeur): string
    {
        return rtrim(strtr(base64_encode($valeur), '+/', '-_'), '=');
    }
}
