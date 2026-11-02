<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Plugin\Heraliz\Turnstile;

/**
 * Anti-robot de la création de compte depuis le launcher (audit du 02/11, point 41). Le launcher ouvre /heraliz/robot
 * dans une petite fenêtre isolée (launcher/app/assets/js/robot.js) : l'anti-robot Turnstile du site s'y affiche ; une
 * fois passé, la page part vers /heraliz/robot/fait#<réponse>, que le launcher intercepte sans la charger et joint à la
 * création du compte (InscriptionController, réponse vérifiée auprès de Cloudflare). Le fragment (#…) n'est jamais
 * envoyé au site. Rien n'est gardé, aucun compte n'est lu ici.
 */
class RobotController extends Controller
{
    public function page()
    {
        return response()->view('heraliz::robot', ['cle' => Turnstile::cle()])
            ->header('Cache-Control', 'no-store');
    }

    /** Fin de la vérification : le launcher ferme la fenêtre avant ; une page ouverte autrement dit quoi faire. */
    public function fait()
    {
        return response()->view('heraliz::robot', ['cle' => null, 'fini' => true])
            ->header('Cache-Control', 'no-store');
    }
}
