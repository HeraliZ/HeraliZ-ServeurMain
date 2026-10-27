<?php

namespace Azuriom\Plugin\Heraliz\Controllers;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Votes\Clic;
use Azuriom\Plugin\Heraliz\Votes\Garde;
use Azuriom\Plugin\Vote\Models\Site;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Clic sur « Voter » de la page de vote (POST /heraliz/vote/{site}/clic, envoyé par la page avant d'ouvrir le site de
 * vote) : noté pour ce compte et cette connexion si le joueur peut voter et a passé l'anti-robot (Votes\Clic). La page ne
 * commence à demander la récompense qu'après la réponse.
 */
class ClicVoteController extends Controller
{
    public function clic(Request $request, string $site): JsonResponse
    {
        $site = class_exists(Site::class) && ctype_digit($site) ? Site::find((int) $site) : null;
        if ($site === null || ! $site->is_enabled) {
            return response()->json(['message' => 'Site de vote inconnu.'], 404);
        }

        $user = $request->user();
        if (($empechement = Garde::compte($user, $request)) !== null) {
            return response()->json(['message' => $empechement['message']], 403);
        }
        $ip = Adresse::de($request);
        if (($jusqua = Garde::ipPrise($site, $ip, $user->id)) !== null) {
            return response()->json(['message' => Garde::messageIp($jusqua)], 429);
        }
        if (! Clic::humain($request)) {
            return response()->json(['message' => 'Vérification anti-robot ratée : recharge la page et réessaie.'], 423);
        }

        Clic::noter($user, $site, $ip);

        return response()->json(['status' => 'ok']);
    }
}
