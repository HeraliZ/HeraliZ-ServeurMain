<?php

namespace Azuriom\Plugin\Heraliz;

use Azuriom\Plugin\Heraliz\Discord\DiscordApi;
use Azuriom\Plugin\Heraliz\Discord\Reglages;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * Alertes de sécurité pour le staff (audit du 02/11, point 15) : écrites dans le journal du site
 * (storage/logs/laravel-*.log) et envoyées dans le salon privé du staff sur Discord (celui de la validation des
 * comptes, si le bot Discord est réglé). L'envoi part après la réponse : la requête n'attend jamais Discord. Une même
 * alerte (même clé) n'est envoyée qu'une fois par délai : pas de salon inondé.
 */
final class Alerte
{
    public static function staff(string $texte, ?string $cle = null, int $delaiSecondes = 600): void
    {
        if ($cle !== null && ! Cache::add('heraliz-alerte:'.$cle, true, $delaiSecondes)) {
            return;
        }
        Log::warning('HeraliZ alerte : '.$texte);

        $salon = Reglages::get('salon_comptes');
        if ($salon === '' || Reglages::get('bot_token') === '') {
            return;
        }
        $envoyer = fn () => rescue(fn () => DiscordApi::message($salon, ['content' => mb_substr('🚨 '.$texte, 0, 1900)]), null, false);
        if (app()->runningInConsole()) {
            $envoyer();
        } else {
            app()->terminating($envoyer);
        }
    }
}
