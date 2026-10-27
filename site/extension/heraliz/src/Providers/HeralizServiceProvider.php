<?php

namespace Azuriom\Plugin\Heraliz\Providers;

use Azuriom\Extensions\Plugin\BasePluginServiceProvider;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Azuriom\Plugin\Heraliz\Middleware\IpsBannies;
use Azuriom\Plugin\Heraliz\Middleware\LimiteConnexion;
use Azuriom\Plugin\Heraliz\Middleware\SuiviAzLink;
use Azuriom\Plugin\Heraliz\Middleware\VotesProteges;
use Illuminate\Auth\Events\Login;
use Illuminate\Auth\Events\Registered;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Console\Scheduling\Schedule;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\URL;

class HeralizServiceProvider extends BasePluginServiceProvider
{
    public function register(): void
    {
        parent::register();

        // Aucun intermédiaire devant le site (nginx répond lui-même) : aucun X-Forwarded-For n'est cru. Sans cette ligne,
        // Laravel croit celui de n'importe qui dès que le nom demandé finit par .on-forge.com ou .on-vapor.com : un
        // pirate pouvait prendre l'adresse IP de son choix à chaque essai et échapper à toutes les limites. Site placé un
        // jour derrière Cloudflare : mettre ici les adresses de Cloudflare.
        config(['trustedproxy.proxies' => []]);

        // Appels venant d'une page d'un autre site : seulement les données publiques (actualités, serveurs, flux). Les
        // comptes (connexion, création, double authentification, jetons) ne répondent plus aux pages d'autres sites, qui
        // pourraient sinon faire vérifier en masse, par les navigateurs de leurs visiteurs, quels e-mails ont un compte.
        // Le launcher n'est pas une page web : il n'est pas concerné.
        config(['cors.paths' => ['api/posts', 'api/posts/*', 'api/servers', 'api/rss', 'api/atom']]);
    }

    public function boot(): void
    {
        // Liens écrits par le site (e-mails de vérification, de mot de passe oublié…) : toujours vers son adresse
        // officielle, jamais vers le nom envoyé par le visiteur (un pirate pourrait sinon faire partir un vrai e-mail
        // HeraliZ dont le lien mène chez lui). Seulement si l'adresse officielle est bien en https.
        $adresse = rtrim((string) config('app.url'), '/');
        if (preg_match('#^https://[a-z0-9.-]+(:\d+)?$#i', $adresse)) {
            URL::forceRootUrl($adresse);
            URL::forceScheme('https');
        }

        // Création de compte depuis le launcher : 30 essais par heure et par adresse IP (les comptes créés sont limités à
        // part, dans InscriptionController : une faute de frappe ne compte pas)
        RateLimiter::for('heraliz-inscription', fn (Request $request) => Limit::perHour(30)->by(Adresse::de($request)));

        // Double authentification : 10 essais par minute et par adresse IP
        RateLimiter::for('heraliz-a2f', fn (Request $request) => Limit::perMinute(10)->by(Adresse::de($request)));

        // Jeton de lancement du jeu (Spartan) : 120 par minute et par adresse IP (un par partie lancée ; plusieurs
        // joueurs derrière une même box, un même réseau 4G)
        RateLimiter::for('heraliz-jeton', fn (Request $request) => Limit::perMinute(120)->by(Adresse::de($request)));

        // Pack de jeu signé pour le compte connecté (PackController) : 120 par minute et par adresse IP (un par partie)
        RateLimiter::for('heraliz-pack', fn (Request $request) => Limit::perMinute(120)->by(Adresse::de($request)));

        // Points Boutique du serveur de jeu (PbController, requêtes signées) : 600 par minute
        RateLimiter::for('heraliz-pb', fn (Request $request) => Limit::perMinute(600)->by(Adresse::de($request)));

        // Connexion aux comptes, par le launcher (API d'Azuriom) comme par le site : 10 erreurs par adresse IP, puis
        // 10 minutes d'attente ; 10 codes de double authentification faux par compte, puis 15 minutes (LimiteConnexion)
        $this->router->pushMiddlewareToGroup('api', LimiteConnexion::class);
        $this->router->pushMiddlewareToGroup('web', LimiteConnexion::class);

        // Discord obligatoire (bêta, Discord\Comptes) : adresses bloquées refusées à l'inscription et à la connexion,
        // adresses des connexions du launcher notées pour chaque compte
        $this->router->pushMiddlewareToGroup('api', IpsBannies::class);
        $this->router->pushMiddlewareToGroup('web', IpsBannies::class);

        // votes protégés contre les bots et les fermes de comptes (Votes\Garde : compte validé qui a joué, vote vu par
        // Top-Serveurs, une récompense par connexion) ; temps de jeu compté sur les relevés d'AzLink (Votes\TempsDeJeu)
        $this->router->pushMiddlewareToGroup('web', VotesProteges::class);
        $this->router->pushMiddlewareToGroup('api', SuiviAzLink::class);
        // clics sur « Voter » (Votes\Clic) : 20 par minute et par compte
        RateLimiter::for('heraliz-vote-clic', fn (Request $request) => Limit::perMinute(20)->by('u'.($request->user()?->id ?? Adresse::de($request))));

        // adresses des connexions et inscriptions du site (et des inscriptions du launcher, qui envoient Registered)
        Event::listen(Login::class, fn (Login $e) => rescue(fn () => Comptes::noterIp((int) $e->user->getAuthIdentifier(), Adresse::de(request())), null, false));
        Event::listen(Registered::class, fn (Registered $e) => rescue(fn () => Comptes::noterIp((int) $e->user->getAuthIdentifier(), Adresse::de(request())), null, false));

        // état du compte pour le launcher (POST /api/heraliz/compte/etat, panneau Discord toutes les 15 s) : 240 par
        // minute et par adresse IP (adresses partagées : box familiale, LAN, 4G)
        RateLimiter::for('heraliz-compte', fn (Request $request) => Limit::perMinute(240)->by(Adresse::de($request)));

        // page du lien Discord (heraliz::discord) et entrée « Mon Discord » du menu du compte
        $this->loadViewsFrom(plugin_path('heraliz/resources/views'), 'heraliz');
        if (method_exists($this, 'registerUserNavigation')) {
            $this->registerUserNavigation();
        }

        // toutes les 5 minutes (tâches planifiées d'Azuriom, /etc/cron.d/azuriom) : bans Discord, demandes au staff,
        // commandes du serveur de jeu (Discord\Comptes::balayer)
        $this->app->booted(function () {
            $this->app->make(Schedule::class)
                ->call(fn () => Comptes::balayer())
                ->everyFiveMinutes()
                ->name('heraliz-discord')
                ->withoutOverlapping(10);
        });
    }

    /**
     * Entrée du menu du compte sur le site (lue par le thème : plugins()->getUserNavItems()).
     */
    protected function userNavigation(): array
    {
        return [
            'heraliz-discord' => [
                'route' => 'heraliz.discord.index',
                'name' => 'Mon Discord',
                'icon' => 'bi bi-discord',
            ],
        ];
    }
}
