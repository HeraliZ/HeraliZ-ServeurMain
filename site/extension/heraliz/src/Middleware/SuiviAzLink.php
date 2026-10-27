<?php

namespace Azuriom\Plugin\Heraliz\Middleware;

use Azuriom\Plugin\Heraliz\Votes\TempsDeJeu;
use Closure;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Relevé d'AzLink (POST /api/azlink, environ une fois par minute, authentifié par la clé du serveur de jeu) : le site y
 * répond avec les comptes reconnus parmi les joueurs en ligne ; leur temps de jeu est compté (Votes\TempsDeJeu).
 */
class SuiviAzLink
{
    public function handle(Request $request, Closure $next): Response
    {
        $reponse = $next($request);

        if ($request->isMethod('post') && $request->is('api/azlink') && $reponse instanceof JsonResponse
            && $reponse->getStatusCode() === 200) {
            rescue(function () use ($reponse) {
                $comptes = $reponse->getData(true)['users'] ?? [];
                TempsDeJeu::noter(is_array($comptes) ? array_column($comptes, 'id') : []);
            }, null, false);
        }

        return $reponse;
    }
}
