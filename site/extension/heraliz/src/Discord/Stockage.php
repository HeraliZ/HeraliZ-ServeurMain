<?php

namespace Azuriom\Plugin\Heraliz\Discord;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Schema;

/**
 * Tables du Discord obligatoire, créées au premier usage (comme le journal des PB : l'extension n'a pas de migrations
 * lancées par Azuriom). Le repère en cache évite de regarder la base à chaque requête ; theme.sh vide le cache à chaque
 * envoi, les tables sont donc revues après chaque mise à jour.
 *  - heraliz_discord_comptes : le Discord lié à chaque compte du site, son état (membre, banni) et la décision du staff ;
 *  - heraliz_ips_comptes : adresses IP vues pour chaque compte (une IPv6 compte pour son bloc /64, comme Adresse) ;
 *  - heraliz_ips_bannies : adresses bloquées (compte lié à un Discord banni) : ni inscription ni connexion ni jeu ;
 *  - heraliz_commandes_serveur : commandes à jouer par la console du serveur de jeu (ban PC de Spartan), gardées ici
 *    même si AzLink ne répond pas, renvoyées ensuite par la tâche planifiée.
 */
final class Stockage
{
    public const COMPTES = 'heraliz_discord_comptes';
    public const IPS = 'heraliz_ips_comptes';
    public const IPS_BANNIES = 'heraliz_ips_bannies';
    public const COMMANDES = 'heraliz_commandes_serveur';

    private const REPERE = 'heraliz-discord-tables-v1';

    private static bool $pret = false;

    public static function tables(): void
    {
        if (self::$pret || Cache::has(self::REPERE)) {
            self::$pret = true;

            return;
        }

        if (! Schema::hasTable(self::COMPTES)) {
            Schema::create(self::COMPTES, function (Blueprint $t) {
                $t->id();
                $t->unsignedBigInteger('user_id')->unique();
                $t->string('discord_id', 25)->unique();
                $t->string('discord_pseudo', 64)->nullable();
                $t->timestamp('discord_cree_le')->nullable();
                $t->enum('statut', ['en_attente', 'accepte', 'refuse'])->default('en_attente');
                $t->string('decide_par', 25)->nullable();
                $t->timestamp('decide_le')->nullable();
                $t->string('message_id', 25)->nullable();
                $t->timestamp('lie_le')->nullable();
                $t->timestamp('verifie_le')->nullable();
                $t->boolean('membre')->default(false);
                $t->boolean('banni')->default(false);
            });
        }
        if (! Schema::hasTable(self::IPS)) {
            Schema::create(self::IPS, function (Blueprint $t) {
                $t->id();
                $t->unsignedBigInteger('user_id')->index();
                $t->string('ip', 64)->index();
                $t->timestamp('vu_le')->nullable();
                $t->unique(['user_id', 'ip']);
            });
        }
        if (! Schema::hasTable(self::IPS_BANNIES)) {
            Schema::create(self::IPS_BANNIES, function (Blueprint $t) {
                $t->id();
                $t->string('ip', 64)->unique();
                // compte dont le Discord banni a bloqué cette adresse (levée si Discord lève le ban)
                $t->unsignedBigInteger('user_id')->nullable()->index();
                $t->string('raison', 190)->nullable();
                $t->timestamp('cree_le')->nullable();
            });
        }
        if (! Schema::hasTable(self::COMMANDES)) {
            Schema::create(self::COMMANDES, function (Blueprint $t) {
                $t->id();
                $t->unsignedBigInteger('user_id')->nullable()->index();
                $t->string('commande', 255);
                $t->timestamp('cree_le')->nullable();
                $t->timestamp('envoyee_le')->nullable()->index();
                $t->unsignedInteger('essais')->default(0);
                $t->string('erreur', 255)->nullable();
            });
        }

        Cache::forever(self::REPERE, true);
        self::$pret = true;
    }
}
