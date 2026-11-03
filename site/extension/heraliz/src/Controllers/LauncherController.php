<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Plugin\Heraliz\Alerte;
use Azuriom\Plugin\Heraliz\Controllers\Concerns\CompteApi;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * Lancement du jeu bloqué (ou fichiers remis en état) par le contrôle d'intégrité du launcher (03/11, demande du
 * propriétaire : « si le launcher empêche le lancement, ça ajoute l'info sur Discord »). Le launcher signale, pour le
 * compte connecté (jamais un pseudo envoyé par le launcher), une raison d'une liste fermée et un court détail ; le site
 * l'écrit dans son journal et le poste dans le salon privé du staff (Alerte::staff).
 *
 * Rien de personnel : ni adresse IP, ni chemin du PC, ni liste des programmes (seulement les noms d'outils interdits
 * trouvés, ou un nombre de fichiers). Anti-spam : une alerte par compte et par raison toutes les 10 minutes ; au-delà de
 * 5 alertes d'une même raison en 10 minutes (pack abîmé chez tout le monde, par exemple), une seule alerte de synthèse,
 * le reste seulement dans le journal. Un launcher modifié peut ne rien envoyer : c'est une information, pas une preuve.
 */
class LauncherController extends Controller
{
    use CompteApi;

    /** Raisons acceptées et leur texte pour le staff. */
    private const RAISONS = [
        'outil_interdit' => 'programme d\'injection ou d\'édition de la mémoire ouvert (lancement refusé)',
        'fichier_modifie' => 'fichier du jeu différent du pack signé (retiré, lancement refusé)',
        'java_modifie' => 'Java du jeu modifié depuis son installation (remplacé par un Java propre)',
        'java_modifie_encore' => 'Java du jeu modifié aussitôt réinstallé (lancement refusé)',
        'jars_ecartes' => 'jar(s) en trop dans le dossier mods du jeu (mis de côté)',
        'pack_refuse' => 'pack de jeu refusé (signature invalide ou trop ancienne)',
    ];

    public function refus(Request $request)
    {
        $this->validate($request, [
            'raison' => ['required', 'string', 'in:'.implode(',', array_keys(self::RAISONS))],
            'detail' => ['nullable', 'string', 'max:300'],
            'version' => ['nullable', 'string', 'max:20'],
        ]);

        $user = $this->compte($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        $raison = (string) $request->input('raison');
        $detail = self::texte($request->input('detail'), 200);
        $version = self::texte($request->input('version'), 20);
        $texte = 'Launcher : '.$user->name.' — '.self::RAISONS[$raison]
            .($detail !== '' ? ' : '.$detail : '')
            .($version !== '' ? ' (launcher '.$version.')' : '');

        // synthèse au-delà de 5 alertes d'une même raison en 10 minutes
        $fenetre = 'heraliz-launcher-refus:'.$raison.':'.intdiv(time(), 600);
        Cache::add($fenetre, 0, 600);
        $n = (int) Cache::increment($fenetre);
        if ($n > 5) {
            Log::warning('HeraliZ alerte : '.$texte);
            if ($n === 6) {
                Alerte::staff('Launcher : plus de 5 comptes en 10 minutes pour « '.self::RAISONS[$raison]
                    .' » (pack ou mise en ligne en cause ?) ; la suite est seulement dans le journal du site.',
                    'launcher-refus-synthese:'.$raison, 600);
            }

            return response()->noContent();
        }

        Alerte::staff($texte, 'launcher-refus:'.$user->id.':'.$raison, 600);

        return response()->noContent();
    }

    /** Texte sur une ligne, sans caractères de contrôle ni mise en forme Discord, coupé. */
    private static function texte(mixed $valeur, int $max): string
    {
        $t = is_scalar($valeur) ? (string) $valeur : '';
        $t = preg_replace('/[\x00-\x1f\x7f`*_~|<>@#\\\\]/u', ' ', $t) ?? '';

        return mb_substr(trim(preg_replace('/\s+/u', ' ', $t) ?? ''), 0, $max);
    }
}
