<?php

namespace Azuriom\Plugin\Heraliz\Votes;

use Azuriom\Models\User;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;

/**
 * Temps passé sur le serveur de jeu par compte du site. AzLink envoie au site, environ une fois par minute, la liste des
 * joueurs en ligne (POST /api/azlink) ; le site y répond avec les comptes reconnus (Middleware\SuiviAzLink). Le temps
 * écoulé depuis le relevé précédent est ajouté à chacun d'eux. Sert au vote : rien pour un compte qui n'a jamais joué.
 */
final class TempsDeJeu
{
    /** Temps de jeu minimal pour qu'un vote soit récompensé. */
    public const MINUTES_MIN = 10;

    /** Écart compté au plus entre deux relevés (serveur redémarré, AzLink coupé un moment). */
    private const ECART_MAX = 300;

    private const DERNIER_RELEVE = 'heraliz-azlink-releve';

    /** Relevé d'AzLink : comptes du site actuellement en jeu. */
    public static function noter(array $ids): void
    {
        $maintenant = time();
        $dernier = Cache::get(self::DERNIER_RELEVE);
        Cache::put(self::DERNIER_RELEVE, $maintenant, 3600);

        $ids = array_values(array_unique(array_filter(array_map('intval', $ids))));
        if ($ids === []) {
            return;
        }
        Stockage::tables();

        // premier relevé connu (cache vidé par une mise à jour du site) : une minute
        $ecart = $dernier === null ? 60 : max(0, min(self::ECART_MAX, $maintenant - (int) $dernier));
        DB::table(Stockage::JEU)->insertOrIgnore(array_map(
            fn (int $id) => ['user_id' => $id, 'secondes' => 0, 'premier_le' => now(), 'vu_le' => now()], $ids));
        DB::table(Stockage::JEU)->whereIn('user_id', $ids)
            ->update(['secondes' => DB::raw('secondes + '.$ecart), 'vu_le' => now()]);
    }

    public static function minutes(User $user): int
    {
        Stockage::tables();

        return intdiv((int) DB::table(Stockage::JEU)->where('user_id', $user->id)->value('secondes'), 60);
    }

    /**
     * Le compte a assez joué pour voter. Tant qu'AzLink n'a jamais rien relevé (serveur de jeu pas encore relié au site),
     * la règle ne s'applique pas : sinon plus personne ne pourrait voter.
     */
    public static function suffisant(User $user): bool
    {
        Stockage::tables();

        return ! DB::table(Stockage::JEU)->exists() || self::minutes($user) >= self::MINUTES_MIN;
    }
}
