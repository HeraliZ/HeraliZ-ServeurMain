<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Http\Resources\AuthenticatedUser;
use Azuriom\Models\ActionLog;
use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Discord\Reglages;
use Azuriom\Plugin\Heraliz\Turnstile;
use Azuriom\Rules\GameAuth;
use Azuriom\Rules\Username;
use Illuminate\Auth\Events\Registered;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password;

/**
 * Création d'un compte HeraliZ depuis le launcher : les mêmes règles que la page d'inscription du site (pseudo, e-mail,
 * mot de passe confirmé, conditions, anti-robot Turnstile quand il est réglé dans l'administration), puis le jeton de
 * l'API d'authentification d'Azuriom, comme une connexion. Le compte est un compte du site : il sert aussi sur
 * heraliz.fr (boutique, votes).
 *
 * Anti-robot (audit du 02/11, point 41) : le launcher 1.16.0 et plus ouvre la page /heraliz/robot (RobotController) et
 * joint la réponse de Turnstile (« robot »), vérifiée ici auprès de Cloudflare. Plus de plafond pour tout le site (60
 * comptes par heure en tout) : quelques connexions suffisaient à bloquer toutes les inscriptions par le launcher.
 */
class InscriptionController extends Controller
{
    // comptes créés par le launcher : 3 par heure et par adresse IP
    private const PAR_IP = 3;

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
            // pack de jeu donné aux comptes connectés, avec des liens signés (PackController)
            'pack' => PackController::cle() !== null,
            // pack signé avec ses empreintes SHA-256 (signature v2) : exigé par le launcher 1.16.0 et plus ; lu par la
            // publication du launcher avant de le mettre en ligne (.github/workflows/launcher.yml)
            'pack_v2' => PackController::signatureV2() !== null,
            // anti-robot demandé avant la création d'un compte (Turnstile de l'administration) : le launcher ouvre
            // /heraliz/robot (RobotController) et joint la réponse ; null : pas d'anti-robot réglé
            'robot' => Turnstile::cle() !== null ? 'turnstile' : null,
            // Discord obligatoire pour jouer (bêta) : le launcher appelle /api/heraliz/compte/etat après la connexion
            'discord' => Reglages::actif(),
            'validation_manuelle' => Reglages::actif() && Reglages::validationManuelle(),
            // les conditions du site : leur adresse, ou la page d'inscription du site qui les affiche (si elle est ouverte)
            'conditions' => $conditions === null ? null : (preg_match('/^https?:\/\//i', $conditions)
                ? $conditions : (Route::has('register') ? route('register') : null)),
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

        // anti-robot réglé sur le site : la réponse de Turnstile est exigée (launcher d'avant la 1.16.0 : il ne sait pas
        // la donner, le joueur crée son compte sur le site, qui a le même anti-robot)
        if (Turnstile::cle() !== null) {
            $robot = $request->input('robot');
            if (! is_string($robot) || $robot === '') {
                return response()->json([
                    'status' => 'error',
                    'reason' => 'refused',
                    'message' => 'Mets ton launcher à jour (heraliz.fr/telecharger), ou crée ton compte sur heraliz.fr puis connecte-toi ici.',
                ], 403);
            }
            if (! Turnstile::valide($robot, (string) $request->ip())) {
                return response()->json([
                    'status' => 'error',
                    'reason' => 'refused',
                    'message' => 'Vérification anti-robot ratée ou trop ancienne : clique de nouveau sur « Créer mon compte ».',
                ], 403);
            }
        }

        $cleIp = 'heraliz-inscription:'.Adresse::de($request);
        // contrôle du plafond, création et comptage d'un seul tenant (verrou par connexion) : des demandes envoyées en
        // même temps ne passent pas toutes avant que la première ait été comptée ; une autre connexion n'attend jamais
        $verrou = Cache::lock('heraliz-inscription-verrou:'.Adresse::de($request), 10);
        if (! $verrou->block(5)) {
            return response()->json(['status' => 'error', 'reason' => 'too_many_attempts', 'message' => 'Réessaie dans un instant.'], 429);
        }
        try {
            if (RateLimiter::tooManyAttempts($cleIp, self::PAR_IP)) {
                return response()->json([
                    'status' => 'error',
                    'reason' => 'too_many_accounts',
                    'message' => 'Trop de comptes créés depuis cette connexion : réessaie plus tard.',
                ], 429);
            }

            // comme Azuriom\Http\Controllers\Auth\RegisterController::create : mot de passe haché ici (Hash::make), que
            // le modèle User le fasse lui-même ou non (un mot de passe déjà haché n'est pas haché deux fois)
            $user = User::forceCreate([
                'name' => $donnees['name'],
                'email' => $donnees['email'],
                'password' => Hash::make($donnees['password']),
                'game_id' => game()->getUserUniqueId($donnees['name']),
                'last_login_ip' => $request->ip(),
                'last_login_at' => now(),
            ]);

            RateLimiter::hit($cleIp, 3600);
        } finally {
            $verrou->release();
        }

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
