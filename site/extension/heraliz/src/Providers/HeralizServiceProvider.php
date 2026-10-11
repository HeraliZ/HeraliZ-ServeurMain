<?php

namespace Azuriom\Plugin\Heraliz\Providers;

use Azuriom\Extensions\Plugin\BasePluginServiceProvider;
use Azuriom\Plugin\Heraliz\Adresse;
use Azuriom\Plugin\Heraliz\Middleware\LimiteConnexion;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
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

        // Jeton de lancement du jeu (Spartan) : 30 par minute et par adresse IP (un par partie lancée)
        RateLimiter::for('heraliz-jeton', fn (Request $request) => Limit::perMinute(30)->by(Adresse::de($request)));

        // Pack de jeu signé pour le compte connecté (PackController) : 30 par minute et par adresse IP (un par partie)
        RateLimiter::for('heraliz-pack', fn (Request $request) => Limit::perMinute(30)->by(Adresse::de($request)));

        // Connexion aux comptes, par le launcher (API d'Azuriom) comme par le site : 10 erreurs par adresse IP, puis
        // 10 minutes d'attente ; 10 codes de double authentification faux par compte, puis 15 minutes (LimiteConnexion)
        $this->router->pushMiddlewareToGroup('api', LimiteConnexion::class);
        $this->router->pushMiddlewareToGroup('web', LimiteConnexion::class);
    }
}
