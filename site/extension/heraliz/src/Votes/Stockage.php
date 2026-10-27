<?php

namespace Azuriom\Plugin\Heraliz\Votes;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Schema;

/**
 * Tables des votes protégés, créées au premier usage (comme celles du Discord obligatoire : l'extension n'a pas de
 * migrations lancées par Azuriom). theme.sh vide le cache à chaque envoi : les tables sont revues après chaque mise à jour.
 *  - heraliz_votes : votes récompensés et votes refusés (connexion déjà récompensée pour un autre compte), avec l'adresse
 *    IP (une IPv6 compte pour son bloc /64, comme Adresse) : une récompense par connexion et par site sur la durée du vote,
 *    et l'alerte au staff quand une même connexion vote pour plusieurs comptes ;
 *  - heraliz_temps_jeu : temps passé sur le serveur de jeu par compte, compté sur les relevés d'AzLink (TempsDeJeu).
 */
final class Stockage
{
    public const VOTES = 'heraliz_votes';
    public const JEU = 'heraliz_temps_jeu';

    private const REPERE = 'heraliz-votes-tables-v1';

    private static bool $pret = false;

    public static function tables(): void
    {
        if (self::$pret || Cache::has(self::REPERE)) {
            self::$pret = true;

            return;
        }

        if (! Schema::hasTable(self::VOTES)) {
            Schema::create(self::VOTES, function (Blueprint $t) {
                $t->id();
                $t->unsignedBigInteger('user_id')->index();
                $t->unsignedBigInteger('site_id');
                $t->string('ip', 64);
                $t->boolean('recompense')->default(false);
                $t->timestamp('cree_le')->nullable()->index();
                $t->index(['ip', 'site_id']);
            });
        }
        if (! Schema::hasTable(self::JEU)) {
            Schema::create(self::JEU, function (Blueprint $t) {
                $t->unsignedBigInteger('user_id')->primary();
                $t->unsignedInteger('secondes')->default(0);
                $t->timestamp('premier_le')->nullable();
                $t->timestamp('vu_le')->nullable();
            });
        }

        Cache::forever(self::REPERE, true);
        self::$pret = true;
    }
}
