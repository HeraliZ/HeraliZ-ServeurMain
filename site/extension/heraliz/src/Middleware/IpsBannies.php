<?php

namespace Azuriom\Plugin\Heraliz\Middleware;

use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Closure;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Adresses IP des comptes (Discord obligatoire, voir Discord\Comptes) :
 * - une adresse bloquée (compte lié à un Discord banni) ne peut ni créer de compte (site et launcher) ni se connecter
 *   (site et launcher), avec un message neutre ;
 * - l'adresse d'une connexion réussie du launcher (/api/auth/authenticate, /api/auth/verify) est notée pour le compte
 *   (celles du site le sont par l'évènement Login, HeralizServiceProvider).
 */
class IpsBannies
{
    private const MESSAGE = 'Impossible de continuer depuis cette connexion. Pour toute question, ouvre un ticket sur le Discord HeraliZ.';

    public function handle(Request $request, Closure $next): Response
    {
        $connexionApi = $request->is('api/auth/authenticate');
        $verification = $request->is('api/auth/verify');
        $protegee = $request->isMethod('post') && ($connexionApi || $request->is('api/heraliz/inscription')
            || $request->is('user/login') || $request->is('user/register'));

        if ($protegee && Comptes::ipBannie(Adresse::de($request))) {
            if ($request->is('api/*')) {
                return response()->json(['status' => 'error', 'reason' => 'refused', 'message' => self::MESSAGE], 403);
            }

            return back()->withErrors(['email' => self::MESSAGE])->withInput($request->only('email', 'name'));
        }

        $reponse = $next($request);

        if (($connexionApi || $verification) && $reponse instanceof JsonResponse && $reponse->getStatusCode() === 200) {
            $donnees = $reponse->getData(true);
            $jeton = $donnees['access_token'] ?? $donnees['data']['access_token'] ?? $request->input('access_token');
            $id = is_string($jeton) && $jeton !== '' ? User::where('access_token', $jeton)->value('id') : null;
            if ($id !== null) {
                Comptes::noterIp((int) $id, Adresse::de($request));
            }
        }

        return $reponse;
    }
}
