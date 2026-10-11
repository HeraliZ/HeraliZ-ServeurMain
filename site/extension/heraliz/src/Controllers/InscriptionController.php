<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Http\Resources\AuthenticatedUser;
use Azuriom\Models\ActionLog;
use Azuriom\Models\User;
use Azuriom\Rules\GameAuth;
use Azuriom\Rules\Username;
use Illuminate\Auth\Events\Registered;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password;

/**
 * Création d'un compte HeraliZ depuis le launcher : les mêmes règles que la page d'inscription du site (pseudo, e-mail,
 * mot de passe confirmé, conditions), puis le jeton de l'API d'authentification d'Azuriom, comme une connexion. Le compte
 * est un compte du site : il sert aussi sur heraliz.fr (boutique, votes).
 */
class InscriptionController extends Controller
{
    // comptes créés par le launcher : 3 par heure et par adresse IP, 60 par heure en tout
    private const PAR_IP = 3;

    private const EN_TOUT = 60;

    /**
     * Ce que le launcher doit savoir avant d'afficher le formulaire.
     */
    public function etat()
    {
        $conditions = setting('conditions');

        return response()->json([
            'connexion' => (bool) setting('auth_api', false),
            'inscription' => (bool) setting('register', true) && setting('auth_api', false),
            'mot_de_passe_min' => app()->isProduction() ? 12 : 8,
            // jetons de lancement du jeu signés par le site (Spartan)
            'jeton' => JetonController::cle() !== null,
            // les conditions du site : leur adresse, ou la page d'inscription du site qui les affiche
            'conditions' => $conditions === null ? null : (preg_match('/^https?:\/\//i', $conditions) ? $conditions : route('register')),
        ]);
    }

    public function inscrire(Request $request)
    {
        if (! setting('auth_api', false) || ! setting('register', true)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'register_disabled',
                'message' => 'La création de compte est fermée pour le moment.',
            ], 403);
        }

        $donnees = $this->validate($request, [
            // pseudo : règles du site, et utilisable en jeu (Minecraft : 3 à 16 lettres, chiffres ou _)
            'name' => ['required', 'string', 'regex:/^[A-Za-z0-9_]{3,16}$/', 'unique:users', new Username(), new GameAuth()],
            'email' => ['required', 'string', 'email', 'max:50', 'unique:users'],
            'password' => ['required', 'confirmed', Password::default()],
            'conditions' => [setting('conditions') !== null ? 'accepted' : 'nullable'],
        ], [
            'name.regex' => 'Le pseudo doit faire 3 à 16 caractères : lettres, chiffres ou _.',
            'name.unique' => 'Ce pseudo est déjà pris.',
            'email.unique' => 'Cet e-mail a déjà un compte : connecte-toi.',
            'password.confirmed' => 'Les deux mots de passe ne sont pas identiques.',
            'conditions.accepted' => 'Tu dois accepter les conditions du site.',
        ], [
            'name' => 'pseudo',
            'email' => 'e-mail',
            'password' => 'mot de passe',
        ]);

        $cleIp = 'heraliz-inscription:'.$request->ip();
        if (RateLimiter::tooManyAttempts($cleIp, self::PAR_IP) || RateLimiter::tooManyAttempts('heraliz-inscription', self::EN_TOUT)) {
            return response()->json([
                'status' => 'error',
                'reason' => 'too_many_accounts',
                'message' => 'Trop de comptes créés depuis cette connexion : réessaie plus tard.',
            ], 429);
        }

        // comme Azuriom\Http\Controllers\Auth\RegisterController::create
        $user = User::forceCreate([
            'name' => $donnees['name'],
            'email' => $donnees['email'],
            'password' => $donnees['password'],
            'game_id' => game()->getUserUniqueId($donnees['name']),
            'last_login_ip' => $request->ip(),
            'last_login_at' => now(),
        ]);

        RateLimiter::hit($cleIp, 3600);
        RateLimiter::hit('heraliz-inscription', 3600);

        // e-mail de vérification si le site en envoie ; une panne d'envoi ne doit pas faire échouer l'inscription
        rescue(fn () => event(new Registered($user)));

        // le jeton de l'API d'authentification, comme Azuriom\Http\Controllers\Api\AuthController::authenticate
        $user->forceFill(['access_token' => Str::random(128)])->save();

        ActionLog::create([
            'user_id' => $user->id,
            'action' => 'users.auth.api.login',
            'target_id' => null,
            'data' => ['ip' => $request->ip(), '2fa' => 'off', 'launcher' => 'inscription'],
        ]);

        return (new AuthenticatedUser($user))->response()->setStatusCode(201);
    }
}
