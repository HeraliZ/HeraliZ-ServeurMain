<?php

namespace Azuriom\Plugin\Heraliz\Middleware;

use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Votes\Garde;
use Azuriom\Plugin\Heraliz\Votes\VerificationStricte;
use Azuriom\Plugin\Vote\Models\Site;
use Azuriom\Plugin\Vote\Models\Vote;
use Closure;
use Illuminate\Contracts\Cache\LockTimeoutException;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Symfony\Component\HttpFoundation\Response;

/**
 * Garde de la récompense des votes (POST /vote/site/{site}/done de l'extension Vote, demandé toutes les 5 s par la page
 * de vote après le clic sur « Voter ») : règles de Votes\Garde, puis vote vu par le site de vote (Votes\VerificationStricte)
 * avant de laisser l'extension Vote donner la récompense. Les réponses gardent la forme de l'extension Vote : « pending »
 * pour attendre le vote, un message d'erreur sinon (affiché tel quel par la page).
 *
 * Une connexion ne prend la récompense qu'une fois : vérification et récompense se font sous un verrou par site et par
 * connexion (sans lui, plusieurs comptes envoyés au même instant depuis la même connexion auraient tous été récompensés
 * pour un seul vote : le verrou de l'extension Vote est par compte).
 */
class VotesProteges
{
    public function handle(Request $request, Closure $next): Response
    {
        if (! $request->isMethod('post') || ! $request->is('vote/site/*/done') || ! class_exists(Site::class)) {
            return $next($request);
        }

        $site = $request->route('site');
        if (! $site instanceof Site) {
            $site = is_numeric($site) ? Site::find((int) $site) : null;
        }
        if ($site === null || ! $site->is_enabled) {
            return $next($request);
        }

        $user = $request->user();
        if ($user === null) {
            return self::refus('Connecte-toi à ton compte HeraliZ pour voter.', 401);
        }

        // vote déjà fait il y a moins de 2 h (ce compte ou cette connexion) : l'extension Vote répond le temps restant
        if ($site->getNextVoteTime($user, $request->ip()) !== null) {
            return $next($request);
        }

        if (($empechement = Garde::compte($user, $request)) !== null) {
            return self::refus($empechement['message'], 403);
        }
        if (! Garde::verifiable($site)) {
            return self::refus(Garde::MESSAGE_PAUSE, 403);
        }

        $ip = Adresse::de($request);
        if (($jusqua = Garde::ipPrise($site, $ip, $user->id)) !== null) {
            Garde::noter($user->id, $site, $ip, false);

            return self::refus(Garde::messageIp($jusqua), 429);
        }

        // le site de vote n'a pas (encore) vu de vote depuis cette connexion : la page redemande dans 5 s
        if (VerificationStricte::geree($site) && ! VerificationStricte::aVote($site, (string) $request->ip())) {
            return response()->json(['status' => 'pending']);
        }

        try {
            return Cache::lock('heraliz-vote:'.$site->id.':'.$ip, 30)->block(10, function () use ($request, $next, $site, $user, $ip) {
                // revu sous le verrou : un autre compte de la même connexion vient peut-être d'être récompensé
                if (($jusqua = Garde::ipPrise($site, $ip, $user->id)) !== null) {
                    Garde::noter($user->id, $site, $ip, false);

                    return self::refus(Garde::messageIp($jusqua), 429);
                }

                $avant = (int) Vote::where('user_id', $user->id)->where('site_id', $site->id)->max('id');
                $reponse = $next($request);
                if (Vote::where('user_id', $user->id)->where('site_id', $site->id)->where('id', '>', $avant)->exists()) {
                    Garde::noter($user->id, $site, $ip, true);
                }

                return $reponse;
            });
        } catch (LockTimeoutException) {
            return response()->json(['status' => 'pending']);
        }
    }

    private static function refus(string $message, int $code): Response
    {
        return response()->json(['message' => $message], $code);
    }
}
