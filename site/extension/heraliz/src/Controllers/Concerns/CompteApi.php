<?php

namespace Azuriom\Plugin\Heraliz\Controllers\Concerns;

use Azuriom\Models\User;
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
}
