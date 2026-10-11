<?php

namespace Azuriom\Plugin\Heraliz\Middleware;

use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Closure;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\Response;

/**
 * Connexion aux comptes, par le launcher (/api/auth/authenticate) comme par le site (/user/login, puis /user/2fa) :
 * - 10 erreurs (mot de passe ou code de double authentification) depuis une même adresse IP, puis 10 minutes
 *   d'attente ; une réussite n'efface rien (sinon un compte valide permettrait d'essayer sans fin ceux des autres) ;
 * - 10 codes de double authentification faux pour un même compte, puis 15 minutes d'attente, quelle que soit
 *   l'adresse IP (il faut déjà le bon mot de passe pour en arriver là : personne ne peut bloquer le compte d'un autre) ;
 * - les espaces sont retirés des codes : Azuriom 1.2.13 accepte « ABCD EFGH… » sans consommer le code de secours, qui
 *   resterait alors valable.
 * Azuriom seul permet 60 essais par minute sur l'API, et 5 par minute pour chaque compte sur le site.
 */
class LimiteConnexion
{
    private const ERREURS_MAX = 10;

    private const ATTENTE_SECONDES = 600;

    private const CODES_FAUX_MAX = 10;

    private const ATTENTE_CODES_SECONDES = 900;

    public function handle(Request $request, Closure $next): Response
    {
        $api = $request->is('api/auth/authenticate');
        $site = $request->is('user/login') || $request->is('user/2fa');

        if (! $request->isMethod('post') || (! $api && ! $site)) {
            return $next($request);
        }

        if (is_string($request->input('code'))) {
            $request->merge(['code' => preg_replace('/\s+/u', '', $request->input('code'))]);
        }

        $cle = 'heraliz-connexion:'.Adresse::de($request);
        $compte = $this->compteDuCode($request);

        if (RateLimiter::tooManyAttempts($cle, self::ERREURS_MAX)
            || ($compte !== null && RateLimiter::tooManyAttempts($this->cleCompte($compte), self::CODES_FAUX_MAX))) {
            return $this->refus($request, $api);
        }

        $reponse = $next($request);

        if ($api) {
            $raison = $reponse instanceof JsonResponse ? ($reponse->getData(true)['reason'] ?? null) : null;
            $erreur = in_array($raison, ['invalid_credentials', 'invalid_2fa'], true);
            $codeFaux = $raison === 'invalid_2fa';
            $compte ??= $codeFaux ? Auth::getLastAttempted()?->getAuthIdentifier() : null;
        } else {
            // le site répond à une erreur en revenant au formulaire avec des messages, posés par CETTE requête
            $erreur = ! Auth::check() && in_array('errors', $request->session()->get('_flash.new', []), true);
            $codeFaux = $erreur && $request->is('user/2fa');
        }

        if ($erreur) {
            RateLimiter::hit($cle, self::ATTENTE_SECONDES);
        }
        if ($codeFaux && $compte !== null) {
            RateLimiter::hit($this->cleCompte($compte), self::ATTENTE_CODES_SECONDES);
        }

        return $reponse;
    }

    /**
     * Compte dont on vérifie un code de double authentification : celui en attente de code sur le site, ou celui que
     * nomme une connexion du launcher qui envoie un code. Null sinon.
     */
    private function compteDuCode(Request $request): mixed
    {
        if ($request->is('user/2fa')) {
            return $request->session()->get('login.2fa.id');
        }

        $identifiant = $request->input('email');
        if (! $request->is('api/auth/authenticate') || ! is_string($identifiant) || empty($request->input('code'))) {
            return null;
        }

        // comme Azuriom\Http\Controllers\Api\AuthController::credentials : un « @ » = e-mail, sinon pseudo
        return User::where(Str::contains($identifiant, '@') ? 'email' : 'name', $identifiant)->value('id');
    }

    private function cleCompte(mixed $compte): string
    {
        return 'heraliz-a2f-compte:'.$compte;
    }

    private function refus(Request $request, bool $api): Response
    {
        $message = 'Trop d’essais ratés : réessaie dans quelques minutes.';

        if ($api) {
            return response()->json([
                'status' => 'error',
                'reason' => 'too_many_attempts',
                'message' => 'Too many attempts, retry later',
            ], 429);
        }

        return back()
            ->withErrors([$request->is('user/2fa') ? 'code' : 'email' => $message])
            ->withInput($request->only('email', 'remember'));
    }
}
