<?php

namespace Azuriom\Plugin\Heraliz\Providers;

use Azuriom\Extensions\Plugin\BasePluginServiceProvider;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;

class HeralizServiceProvider extends BasePluginServiceProvider
{
    public function boot(): void
    {
        // Création de compte depuis le launcher : 30 essais par heure et par adresse IP (les comptes créés sont limités à
        // part, dans InscriptionController : une faute de frappe ne compte pas)
        RateLimiter::for('heraliz-inscription', fn (Request $request) => Limit::perHour(30)->by($request->ip()));

        // Double authentification : 10 essais par minute et par adresse IP
        RateLimiter::for('heraliz-a2f', fn (Request $request) => Limit::perMinute(10)->by($request->ip()));
    }
}
