<?php

namespace Azuriom\Plugin\Heraliz\Votes;

use Azuriom\Plugin\Vote\Models\Site;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Vérification des votes auprès du site de vote, SANS rien laisser passer en cas de doute. L'extension Vote d'Azuriom
 * pose la même question à la même API, mais accepte le vote quand l'API ne répond pas ou que le jeton manque (« fail
 * open ») : un bot n'aurait qu'à tomber pendant une panne. Ici : vote trouvé = récompense, tout le reste (pas encore
 * trouvé, jeton refusé, panne) = on attend (la page de vote redemande toutes les 5 s).
 *
 * Top-Serveurs ne connaît que l'adresse IP qui a voté : le vote doit venir de la même connexion que la page du site (ou
 * d'une adresse liée par ipv6-adapter.com, comme le fait l'extension Vote, pour les connexions à la fois IPv4 et IPv6).
 */
final class VerificationStricte
{
    /** Sites vérifiés ici (jeton du serveur dans « clé de vérification » du site de vote, administration → Votes → Sites). */
    private const API = [
        'top-serveurs.net' => 'https://api.top-serveurs.net/v1/votes/check-ip?server_token={cle}&ip={ip}',
        'top-games.net' => 'https://api.top-games.net/v1/votes/check-ip?server_token={cle}&ip={ip}',
    ];

    public static function geree(Site $site): bool
    {
        return isset(self::API[Garde::hote((string) $site->url) ?? '']);
    }

    /** Le site de vote a vu un vote récent depuis cette adresse IP (adresse brute de la requête, pas le bloc /64). */
    public static function aVote(Site $site, string $ip): bool
    {
        $api = self::API[Garde::hote((string) $site->url) ?? ''] ?? null;
        $cle = trim((string) $site->verification_key);
        if ($api === null || $cle === '' || $ip === '') {
            return false;
        }

        foreach (self::adresses($ip) as $adresse) {
            try {
                $reponse = Http::acceptJson()->timeout(5)
                    ->get(str_replace(['{cle}', '{ip}'], [rawurlencode($cle), rawurlencode($adresse)], $api));
            } catch (Throwable) {
                return false;
            }
            if ((int) $reponse->json('code') === 200) {
                return true;
            }
            if ($reponse->json('error') === 'ServerNotFound') {
                self::jetonRefuse($site);

                return false;
            }
        }

        return false;
    }

    /** L'adresse de la requête et celles qu'ipv6-adapter.com lui a liées (même réglage que l'extension Vote). */
    private static function adresses(string $ip): array
    {
        if (! setting('vote.ipv4-v6-compatibility', true)) {
            return [$ip];
        }

        $cle = 'heraliz-vote-ipv6:'.$ip;
        $liees = Cache::get($cle);
        if (! is_array($liees)) {
            try {
                $ips = Http::timeout(3)->get('https://ipv6-adapter.com/api/v1/fetch?ip='.rawurlencode($ip))->json('ips');
            } catch (Throwable) {
                $ips = null;
            }
            $liees = is_array($ips) ? array_values(array_filter($ips, fn ($v) => is_string($v) && filter_var($v, FILTER_VALIDATE_IP))) : [];
            // service muet : pas redemandé avant une minute (la page redemande le vote toutes les 5 s)
            Cache::put($cle, $liees, is_array($ips) ? 15 : 60);
        }

        return array_values(array_unique(array_merge([$ip], array_slice($liees, 0, 4))));
    }

    /** Jeton refusé par Top-Serveurs (« aucun serveur associé ») : aucun vote ne passe ; le staff est prévenu une fois par heure. */
    private static function jetonRefuse(Site $site): void
    {
        if (! Cache::add('heraliz-vote-jeton:'.$site->id, true, 3600)) {
            return;
        }
        Log::warning('HeraliZ votes : jeton refusé par '.$site->name.' : aucun vote de ce site n’est récompensé');
        Garde::prevenirStaff('⚠️ Votes : '.$site->name.' refuse le jeton du serveur (« aucun serveur associé à ce jeton »). '
            .'Plus aucun vote de ce site n’est récompensé : vérifier le secret GitHub TOPSERVEURS_TOKEN (ou la clé de '
            .'vérification dans l’administration → Votes → Sites).');
    }
}
