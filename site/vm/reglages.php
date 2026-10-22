<?php

/*
 * Réglages du site HeraliZ (Azuriom), lancés par site/vm/theme.sh avec l'utilisateur du site (www-data) :
 *   php reglages.php <dossier d'Azuriom> <dossier du contenu envoyé>
 * À chaque envoi : le thème « heraliz » devient le thème du site.
 * Une seule fois (repère « heraliz.api_launcher ») : l'API de connexion des comptes est activée, pour le launcher
 * (connexion, création de compte et double authentification avec le compte du site).
 * Une seule fois (repère « heraliz.reglages » dans les réglages) : nom et description du site, crédits, boutique en
 * crédits et en euros, menu, page Règlement, grades de la saison, trois premières actualités, serveur de jeu à
 * surveiller. Chaque élément n'est créé que s'il manque : rien de ce que l'administration a déjà changé n'est écrasé.
 */

use Azuriom\Extensions\Theme\ThemeManager;
use Azuriom\Models\NavbarElement;
use Azuriom\Models\Page;
use Azuriom\Models\Post;
use Azuriom\Models\Role;
use Azuriom\Models\Server;
use Azuriom\Models\Setting;
use Azuriom\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Http\File;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

if ($argc < 3) {
    fwrite(STDERR, "usage : php reglages.php <dossier d'Azuriom> <dossier du contenu>\n");
    exit(2);
}

[, $dossier, $contenu] = $argv;
chdir($dossier);
require $dossier.'/vendor/autoload.php';
$app = require $dossier.'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

/** Caisse dont les clés sont vendues sur la boutique (« clés cosmétiques »), identifiant de HeraliZ Caisses. */
const CAISSE_COSMETIQUE = 'spectacle';

require __DIR__.'/pages_legales.php';

function dire(string $message): void
{
    fwrite(STDOUT, $message.PHP_EOL);
}

// le thème du site (et le lien public de ses fichiers, assets/themes/heraliz)
app(ThemeManager::class)->changeTheme('heraliz');
dire('Thème du site : HeraliZ');

// le launcher se connecte avec les comptes du site : API d'authentification d'Azuriom activée une seule fois, ensuite
// l'administration fait foi (Paramètres → Authentification → API d'authentification)
if (setting('heraliz.api_launcher') === null) {
    Setting::updateSettings(['auth_api' => true, 'heraliz.api_launcher' => now()->toDateString()]);
    dire('Connexion des comptes depuis le launcher : API d’authentification activée');
}

if (setting('heraliz.reglages') !== null) {
    dire('Réglages de départ : déjà faits le '.setting('heraliz.reglages').' (rien n’est changé, l’administration fait foi)');
} else {
    reglagesDeDepart($contenu);
}

// téléchargement du launcher ouvert ou fermé (TELECHARGEMENT de theme.sh), lu par le thème
if (in_array(getenv('HERALIZ_TELECHARGEMENT'), ['ouvert', 'ferme'], true)) {
    Setting::updateSettings('heraliz.telechargement', getenv('HERALIZ_TELECHARGEMENT'));
}

// récompense des votes : une seule fois (repère « heraliz.votes »), ensuite l'administration fait foi
if (setting('heraliz.votes') === null) {
    recompenseVote();
}

// boutique de la saison 1 et pages légales : une seule fois (repère « heraliz.boutique »), chaque élément seulement s'il
// manque ; ensuite l'administration fait foi
if (setting('heraliz.boutique') === null) {
    boutiqueDeDepart();
}

// état du site, lu par le propriétaire dans le résumé de l'envoi
$jeu = (string) config('azuriom.game');
dire('Jeu choisi à l’installation : '.$jeu.match ($jeu) {
    'mc-offline' => ' (Minecraft sans compte Microsoft : bon pour le serveur en mode cracké)',
    'mc-online' => ' (Minecraft avec compte Microsoft obligatoire : les joueurs en version crackée ne pourront pas créer de compte)',
    default => '',
});
dire('Inscriptions sur le site : '.(setting('register', true) ? 'ouvertes' : 'fermées (administration → Paramètres)'));
dire('Comptes du site dans le launcher : '.(setting('auth_api', false) ? 'connexion ouverte' : 'connexion fermée (administration → Paramètres → Authentification → API d’authentification)')
    .(plugins()->isEnabled('heraliz') ? ', création de compte et double authentification par l’extension HeraliZ' : ', extension HeraliZ absente (pas de création de compte depuis le launcher)'));
dire('Extensions actives : '.(implode(', ', array_keys(plugins()->plugins())) ?: 'aucune'));
dire('Boutique : '.(plugins()->isEnabled('shop') ? \Azuriom\Plugin\Shop\Models\Package::count().' article(s)' : 'extension absente'));
dire('Votes : '.(plugins()->isEnabled('vote') ? \Azuriom\Plugin\Vote\Models\Site::count().' site(s) de vote' : 'extension absente'));

function reglagesDeDepart(string $contenu): void
{
    // site : nom, description, nom de la monnaie du site (crédits ; les hérals sont la monnaie du jeu)
    $reglages = [];
    if (in_array(setting('name'), [null, '', 'Azuriom'], true)) {
        $reglages['name'] = 'HeraliZ';
    }
    if (blank(setting('description'))) {
        $reglages['description'] = 'HeraliZ, serveur Minecraft de survie : loot, zombies, clans, bases et raids.';
    }
    if (in_array(setting('money'), [null, 'points', 'point|points'], true)) {
        $reglages['money'] = 'crédit|crédits';
    }
    // boutique : articles payés en crédits du site, crédits achetés en euros (maquettes validées)
    if (setting('shop.use_site_money') === null) {
        $reglages['shop.use_site_money'] = true;
    }
    if (setting('currency') === null) {
        $reglages['currency'] = 'EUR';
    }
    if ($reglages !== []) {
        Setting::updateSettings($reglages);
        dire('Réglages du site : '.implode(', ', array_keys($reglages)));
    }

    // page Règlement (le même que dans le launcher)
    $reglement = Page::where('slug', 'reglement')->first();
    if ($reglement === null) {
        $reglement = Page::create([
            'title' => 'Règlement',
            'slug' => 'reglement',
            'description' => 'Une zone. Des règles. Les contrôles et les sanctions sont faits par le serveur et son équipe.',
            'content' => '<ol class="liste-regles">'
                .'<li><span class="num">01</span><div><h3>Un joueur, un compte.</h3><p>Les doubles comptes sont interdits et entraînent un bannissement immédiat. Changer de pseudo ne permet pas de contourner une sanction.</p></div></li>'
                .'<li><span class="num">02</span><div><h3>Respecte ton identité.</h3><p>Garde le même pseudo. N’utilise jamais le pseudo d’un autre joueur ou d’un membre du staff.</p></div></li>'
                .'<li><span class="num">03</span><div><h3>Joue loyalement.</h3><p>La triche, l’exploitation de bugs et le harcèlement n’ont pas leur place sur HeraliZ. Signale les problèmes à l’équipe.</p></div></li>'
                .'</ol>',
            'is_enabled' => true,
        ]);
        dire('Page Règlement créée');
    }

    // menu : Accueil, Boutique, Votes, Règlement (seulement si le menu est encore celui d'origine : un seul « Accueil »)
    $elements = NavbarElement::all();
    if ($elements->count() <= 1 && $elements->every(fn (NavbarElement $e) => $e->type === 'home')) {
        if ($elements->isEmpty()) {
            NavbarElement::create(['name' => 'Accueil', 'type' => 'home', 'value' => '#', 'position' => 1]);
        } else {
            $elements->first()->update(['name' => 'Accueil', 'icon' => null, 'position' => 1]);
        }
        $position = 2;
        foreach ([['Boutique', 'shop'], ['Votes', 'vote']] as [$nom, $extension]) {
            if (plugins()->isEnabled($extension)) {
                NavbarElement::create(['name' => $nom, 'type' => 'plugin', 'value' => $extension.'.home', 'position' => $position++]);
            }
        }
        NavbarElement::create(['name' => 'Règlement', 'type' => 'page', 'value' => 'reglement', 'position' => $position]);
        dire('Menu : Accueil, Boutique, Votes, Règlement');
    }

    // grades de la saison 1 : nom en couleur, sans icône (choix du propriétaire), au-dessus des membres, sous l'administration
    $pouvoirAdmin = (int) Role::where('is_admin', true)->max('power');
    $grades = [['Rescapé', '#E39B5E'], ['Traqueur', '#8FD3FF'], ['Roi de la Zone', '#FFC23A']];
    foreach ($grades as $i => [$nom, $couleur]) {
        if (! Role::where('name', $nom)->exists()) {
            Role::create(['name' => $nom, 'color' => $couleur, 'power' => max(1, min($pouvoirAdmin - 1, $i + 1)), 'is_admin' => false]);
            dire('Grade créé : '.$nom);
        }
    }

    // trois premières actualités (celles du launcher), seulement si le site n'en a aucune
    $auteur = User::whereHas('role', fn ($q) => $q->where('is_admin', true))->orderBy('id')->first() ?? User::orderBy('id')->first();
    if ($auteur !== null && ! Post::exists()) {
        $actus = [
            ['caisses-hd', 'Caisses HD et roue de butin', 'Ouvre-les en jeu avec tes clés et regarde la roue tourner.', '2026-10-05 18:00',
                '<p>Les caisses du serveur passent en haute définition : chaque caisse a son modèle, et l’ouverture se fait en jeu avec sa clé.</p><p>Une roue de butin défile et s’arrête sur ta récompense, sous les yeux des joueurs autour.</p>'],
            ['crac-plan-marteau', 'Plan et marteau repensés', 'Roues plus claires, coûts lisibles, réparation en un geste.', '2026-10-08 18:00',
                '<p>Construire sa base devient plus simple : les roues du plan et du marteau ont été refaites, avec le coût de chaque pièce bien visible.</p><p>Réparer, améliorer ou retirer une pièce se fait en un geste.</p>'],
            ['createur-personnage', 'Nouveau créateur de personnage', 'Visage, cicatrices, tenues : crée ton survivant avant d’entrer dans la zone.', '2026-10-10 18:00',
                '<p>Avant ta première sortie, crée ton survivant : visage, coupe, cicatrices et tenue.</p><p>Ton personnage est le même pour tous les joueurs qui te croisent.</p>'],
        ];
        foreach ($actus as [$image, $titre, $resume, $date, $texte]) {
            $post = new Post([
                'title' => $titre,
                'description' => $resume,
                'slug' => Str::slug($titre),
                'content' => $texte,
                'published_at' => Carbon::parse($date, config('app.timezone')),
            ]);
            $post->author_id = $auteur->id;
            $fichier = $contenu.'/actus/'.$image.'.jpg';
            if (is_file($fichier)) {
                $post->image = basename(Storage::disk('public')->putFileAs('posts', new File($fichier), $image.'.jpg'));
            }
            $post->save();
        }
        dire('Actualités : 3 premières actualités publiées (modifiables dans l’administration → Actualités)');
    }

    // serveur de jeu surveillé par le site (état et joueurs en ligne en haut des pages) : play.heraliz.fr, par simple ping
    if (! Server::exists()) {
        $serveur = Server::create([
            'name' => 'HeraliZ',
            'address' => 'play.heraliz.fr',
            'port' => null,
            'type' => 'mc-ping',
            'home_display' => true,
        ]);
        Setting::updateSettings('servers.default', $serveur->id);
        dire('Serveur de jeu ajouté au site : play.heraliz.fr (lecture de l’état seulement)');
    }

    Setting::updateSettings('heraliz.reglages', now()->toDateString());
    dire('Réglages de départ : faits');
}


/** Garde seulement les colonnes qui existent (les versions de la Boutique d'Azuriom n'ont pas toutes les mêmes). */
function colonnes(string $table, array $valeurs): array
{
    return array_filter($valeurs, fn ($cle) => Schema::hasColumn($table, $cle), ARRAY_FILTER_USE_KEY);
}

function boutiqueDeDepart(): void
{
    // serveur de jeu relié par AzLink (commandes des achats jouées par la console du serveur)
    $serveur = Server::find(setting('servers.default')) ?? Server::first();
    if ($serveur !== null && $serveur->type !== 'mc-azlink') {
        $serveur->type = 'mc-azlink';
        if (blank($serveur->token)) {
            $serveur->token = Str::random(32);
        }
        $serveur->save();
        dire('Serveur de jeu relié par AzLink : taper dans la console du Ptero la commande « azlink setup » donnée par l’administration → Serveurs');
    }

    foreach (pagesLegales() as $slug => [$titre, $resume, $texte]) {
        if (! Page::where('slug', $slug)->exists()) {
            Page::create(['title' => $titre, 'slug' => $slug, 'description' => $resume, 'content' => $texte, 'is_enabled' => true]);
            dire('Page créée : '.$titre.' (à relire et compléter : les passages entre crochets)');
        }
    }

    if (! plugins()->isEnabled('shop')) {
        dire('Boutique : extension absente, articles non créés');
        return;
    }
    $Categorie = \Azuriom\Plugin\Shop\Models\Category::class;
    $Article = \Azuriom\Plugin\Shop\Models\Package::class;
    if ($Article::exists()) {
        dire('Boutique : des articles existent déjà, rien n’est ajouté');
    } else {
        $sid = $serveur?->id;
        $cmd = fn (string $c) => [['command' => $c, 'trigger' => 'purchase', 'require_online' => true, 'server' => $sid]];
        $contenu = '<h3>Ce que contient ce grade</h3><ul><li>[À compléter dans l’administration → Boutique → Articles]</li></ul>';
        $temps = '<p><strong>Sans renouvellement automatique</strong> : à la fin de la durée, le grade est retiré. Aucun prélèvement : pour le garder, il faut le racheter. Monter d’un grade se paie au prix plein du nouveau grade.</p>'
            .'<p>Contenu numérique livré immédiatement en jeu : <strong>aucun remboursement</strong> (voir les Conditions de vente). Être connecté au serveur au moment de la livraison.</p>';
        $categories = [
            ['Grades', 'grades', '<p>Soutiens HeraliZ et profite d’avantages pratiques et visuels, jamais d’avantage en combat. Emote, skin ou finisher rien qu’à toi ou à ton clan : voir la page « Créations sur mesure ».</p>', [
                ['Rescapé · 1 mois', 9.90, 'rescape', 30], ['Rescapé · 3 mois', 24.00, 'rescape', 90],
                ['Traqueur · 1 mois', 14.90, 'traqueur', 30], ['Traqueur · 3 mois', 35.90, 'traqueur', 90],
                ['Roi de la Zone · 1 mois', 19.90, 'roi_de_la_zone', 30], ['Roi de la Zone · 3 mois', 47.90, 'roi_de_la_zone', 90],
            ]],
            ['Soutien Bêta', 'beta', '<p>Pour ceux qui soutiennent HeraliZ dès la bêta et l’aident à s’améliorer.</p>', [
                ['Grade BETA · à vie', 89.90, 'roi_de_la_zone', 0],
            ]],
            ['Clés cosmétiques', 'cles', '<p>Clés de caisses cosmétiques, à ouvrir en jeu. Plus le lot est grand, moins la clé coûte cher.</p>', [
                ['1 clé', 0.55, 1], ['10 clés', 5.20, 10], ['20 clés', 9.90, 20], ['50 clés', 23.90, 50], ['100 clés', 44.90, 100],
            ]],
        ];
        foreach ($categories as $ic => [$nom, $slug, $desc, $articles]) {
            $c = $Categorie::create(colonnes('shop_categories', ['name' => $nom, 'slug' => $slug, 'description' => $desc, 'position' => $ic + 1, 'is_enabled' => true]));
            foreach ($articles as $ia => $a) {
                if ($slug === 'cles') {
                    [$titre, $prix, $n] = $a;
                    $desc2 = '<p>'.$n.' clé(s) de caisse cosmétique, livrée(s) dans ton inventaire en jeu.</p>';
                    $commande = 'heraliz givekey {player} '.CAISSE_COSMETIQUE.' '.$n;
                    $jours = null;
                    $court = $n.' clé(s) cosmétique(s), livrée(s) en jeu.';
                } else {
                    [$titre, $prix, $grade, $jours] = $a;
                    $desc2 = $jours === 0
                        ? '<p>Les mêmes avantages que Roi de la Zone, <strong>à vie</strong>, plus une emote et un skin d’arme personnalisés à vie : fais ta demande au staff sur Discord avec un exemple (image ou vidéo) du rendu voulu, dans les limites de ce qui est possible sur Minecraft et de ce que nos développeurs peuvent faire.</p>'.$contenu
                            .'<p>Contenu numérique livré immédiatement en jeu : <strong>aucun remboursement</strong> (voir les Conditions de vente).</p>'
                        : $contenu.$temps;
                    $court = $jours === 0 ? 'Avantages Roi de la Zone à vie, emote et skin d’arme personnalisés.' : 'Grade pour '.$jours.' jours, sans renouvellement automatique.';
                    $commande = 'grade acheter {player} '.$grade.' '.($jours === 0 ? 'vie' : $jours);
                }
                $Article::create(colonnes('shop_packages', [
                    'category_id' => $c->id, 'name' => $titre, 'short_description' => $court,
                    'description' => $desc2, 'price' => $prix, 'position' => $ia + 1, 'commands' => $cmd($commande),
                    'need_online' => true, 'user_limit' => $jours === 0 ? 1 : null, 'is_enabled' => true,
                ]));
            }
            dire('Boutique : catégorie « '.$nom.' » ('.count($articles).' articles)');
        }
    }

    // crédits du site achetés en euros : 1 crédit = 1 € (moyens de paiement à cocher dans Boutique → Offres)
    $Offre = \Azuriom\Plugin\Shop\Models\Offer::class;
    if (class_exists($Offre) && ! $Offre::exists()) {
        foreach ([5, 10, 20, 50, 100] as $e) {
            $Offre::create(colonnes('shop_offers', ['name' => $e.' crédits', 'price' => $e, 'money' => $e, 'is_enabled' => true]));
        }
        dire('Boutique : offres de crédits 5, 10, 20, 50, 100 € (1 crédit = 1 €)');
    }

    Setting::updateSettings('heraliz.boutique', now()->toDateString());
    dire('Boutique et pages légales : faites');
}

/** Chaque vote : 1 clé de la caisse « vote » et 10 hérals en jeu (joués par AzLink, joueur connecté). */
function recompenseVote(): void
{
    if (! plugins()->isEnabled('vote')) {
        dire('Votes : extension absente, récompense non créée');
        return;
    }
    $Recompense = \Azuriom\Plugin\Vote\Models\Reward::class;
    if ($Recompense::exists()) {
        dire('Votes : une récompense existe déjà, rien n’est ajouté');
    } else {
        $serveur = Server::find(setting('servers.default')) ?? Server::first();
        $commandes = ['heraliz givekey {player} vote 1', 'eco donner {player} 10'];
        $r = $Recompense::create(colonnes('vote_rewards', [
            'name' => '1 clé vote + 10 hérals', 'chances' => 100, 'money' => 0,
            'commands' => $commandes, 'need_online' => true, 'is_enabled' => true,
        ]));
        if ($serveur !== null && method_exists($r, 'servers')) {
            $r->servers()->sync([$serveur->id]);
        }
        dire('Votes : récompense « 1 clé vote + 10 hérals » créée (sites de vote à ajouter dans l’administration → Votes → Sites)');
    }
    Setting::updateSettings('heraliz.votes', now()->toDateString());
}
