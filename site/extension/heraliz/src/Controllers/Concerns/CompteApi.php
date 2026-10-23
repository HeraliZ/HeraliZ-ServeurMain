<?php

namespace Azuriom\Plugin\Heraliz\Controllers\Concerns;

use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Le compte connecté au launcher : celui du jeton de l'API d'authentification d'Azuriom (access_token).
 */
trait CompteApi
{
    /**
     * Le compte du jeton, ou la réponse d'erreur à rendre (API fermée, jeton inconnu, compte banni).
     */
    protected function compte(Request $request): User|JsonResponse
    {
        if (! setting('auth_api', false)) {
            return response()->json(['status' => 'error', 'message' => 'Auth API is not enabled'], 400);
        }

        $this->validate($request, ['access_token' => ['required', 'string']]);
        $user = User::firstWhere('access_token', $request->input('access_token'));

        if ($user === null) {
            return response()->json(['status' => 'error', 'reason' => 'invalid_token', 'message' => 'Invalid token'], 401);
        }

        if ($user->isBanned()) {
            return response()->json(['status' => 'error', 'reason' => 'user_banned', 'message' => 'User banned'], 403);
        }

        return $user;
    }

    /**
     * Discord obligatoire (bêta) : null si le compte peut avoir le pack et les jetons de jeu, sinon la réponse à rendre
     * (403, ou 503 si Discord n'a encore jamais pu être interrogé pour ce compte). Note aussi l'adresse IP du compte.
     * Le launcher lit « statut » (a_lier, pas_membre, en_attente, refuse, banni, indisponible) et appelle
     * /api/heraliz/compte/etat pour le lien et l'invitation.
     */
    protected function discord(User $user, Request $request): ?JsonResponse
    {
        $statut = Comptes::statut($user, Adresse::de($request));
        if ($statut === Comptes::OK) {
            return null;
        }

        return response()->json([
            'status' => 'error',
            'reason' => 'discord',
            'erreur' => 'discord',
            'statut' => $statut,
            'message' => Comptes::message($statut),
        ], $statut === Comptes::INDISPONIBLE ? 503 : 403);
    }
}
