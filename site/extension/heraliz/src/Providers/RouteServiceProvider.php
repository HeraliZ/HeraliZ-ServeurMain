<?php

namespace Azuriom\Plugin\Heraliz\Providers;

use Azuriom\Extensions\Plugin\BaseRouteServiceProvider;
use Illuminate\Support\Facades\Route;

class RouteServiceProvider extends BaseRouteServiceProvider
{
    /**
     * Routes de l'extension : l'API du launcher (/api/heraliz/...) et les pages du lien Discord (/heraliz/discord/...).
     */
    public function loadRoutes(): void
    {
        Route::middleware('web')
            ->prefix($this->plugin->id)
            ->name($this->plugin->id.'.')
            ->group(plugin_path($this->plugin->id.'/routes/web.php'));

        Route::middleware('api')
            ->prefix('api/'.$this->plugin->id)
            ->name($this->plugin->id.'.api.')
            ->group(plugin_path($this->plugin->id.'/routes/api.php'));
    }
}
