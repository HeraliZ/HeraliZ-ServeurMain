<?php

namespace Azuriom\Plugin\Heraliz\Middleware;

use Closure;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Symfony\Component\HttpFoundation\Response;

/**
 * Connexion des comptes par l'API d'Azuriom (/api/auth/authenticate, utilisée par le launcher) : après 10 erreurs
 * (mot de passe ou code de double authentification) depuis une même adresse IP, 10 minutes d'attente. Azuriom n'y met
 * que la limite générale de son API (60 requêtes par minute), trop large contre qui essaie des mots de passe.
 */
class LimiteConnexion
{
    private const ERREURS_MAX = 10;

    private const ATTENTE_SECONDES = 600;

    public function handle(Request $request, Closure $next): Response
    {
        if (! $request->isMethod('post') || ! $request->is('api/auth/authenticate')) {
            return $next($request);
        }

        $cle = 'heraliz-connexion:'.$request->ip();

        if (RateLimiter::tooManyAttempts($cle, self::ERREURS_MAX)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'too_many_attempts',
                'message' => 'Too many attempts, retry in '.RateLimiter::availableIn($cle).' seconds',
            ], 429);
        }

        $reponse = $next($request);

        // une réussite n'efface rien : sinon, un compte valide permettrait d'essayer sans fin les mots de passe des autres
        $raison = $reponse instanceof JsonResponse ? ($reponse->getData(true)['reason'] ?? null) : null;
        if (in_array($raison, ['invalid_credentials', 'invalid_2fa'], true)) {
            RateLimiter::hit($cle, self::ATTENTE_SECONDES);
        }

        return $reponse;
    }
}
