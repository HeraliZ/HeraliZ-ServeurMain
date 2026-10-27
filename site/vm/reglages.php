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

// Discord obligatoire (bêta) : validation des comptes à la main par le staff ou automatique (VALIDATION de theme.sh),
// lue par l'extension HeraliZ (Discord\Reglages)
if (in_array(getenv('HERALIZ_VALIDATION'), ['manuelle', 'auto'], true)) {
    Setting::updateSettings('heraliz.validation_manuelle', getenv('HERALIZ_VALIDATION') === 'manuelle' ? 'oui' : 'non');
    dire('Validation des comptes (Discord obligatoire) : '.(getenv('HERALIZ_VALIDATION') === 'manuelle' ? 'à la main par le staff' : 'automatique'));
}

// Discord obligatoire : le bot voit-il le serveur Discord et ses bans ? (sans « Bannir des membres », un Discord banni
// passerait inaperçu ; mauvais serveur : tout le monde serait « pas membre »)
if (class_exists(\Azuriom\Plugin\Heraliz\Discord\Reglages::class) && \Azuriom\Plugin\Heraliz\Discord\Reglages::actif()) {
    $probleme = rescue(fn () => \Azuriom\Plugin\Heraliz\Discord\DiscordApi::controleBot(), 'contrôle impossible', false);
    dire($probleme === null ? 'Bot Discord : serveur et bans lisibles' : '::warning::Bot Discord : '.$probleme);
}

// récompense des votes : une seule fois (repère « heraliz.votes »), ensuite l'administration fait foi
if (setting('heraliz.votes') === null) {
    recompenseVote();
}

// 2 PB par vote (23/10, demande du propriétaire) : une seule fois (repère « heraliz.vote_pb »), chaque récompense de vote
// qui donne moins de 2 PB passe à 2 PB (crédités sur le compte du site) ; ensuite l'administration fait foi
if (setting('heraliz.vote_pb') === null) {
    pbParVote();
}

// boutique de la saison 1 et pages légales : une seule fois (repère « heraliz.boutique »), chaque élément seulement s'il
// manque ; ensuite l'administration fait foi
if (setting('heraliz.boutique') === null) {
    boutiqueDeDepart();
}

// site v4 (maquette validée le 22/10) : page Launcher, règlement en 10 règles, menu, sites de vote ; une seule fois
// (repère « heraliz.site_v4 »), ensuite l'administration fait foi
if (setting('heraliz.site_v4') === null) {
    siteV4();
}

// votes protégés contre les bots (27/10, demande du propriétaire) : à chaque envoi, le dépôt fait foi
if (plugins()->isEnabled('vote')) {
    votesProteges();
}

// pages légales : créées si elles manquent, remplacées tant qu'elles n'ont pas été modifiées dans l'administration
majPagesLegales();
// Serveurs-Minecraft.org : un vote toutes les 24 h (1 440 min), remis à chaque envoi
if (plugins()->isEnabled('vote')) {
    foreach (\Azuriom\Plugin\Vote\Models\Site::where('url', 'like', '%serveurs-minecraft.org%')->get() as $site) {
        if ((int) $site->vote_delay !== 1440) {
            $site->update(['vote_delay' => 1440]);
            dire('Votes : '.$site->name.' toutes les 24 h');
        }
    }
}

// boutique en Points Boutique (22/10) : le site ne vend plus que des PB (100 PB = 1 €, payés en euros), tout le reste
// s'achète en jeu (/boutique). Une seule fois (repère « heraliz.boutique_pb ») : anciens articles, catégories et offres
// de crédits retirés (aucun achat fait), lots de PB créés ; mis en vente seulement quand PB_EN_VENTE est vrai (la
// commande « pb donner » doit exister sur le serveur), à chaque envoi tant que l'administration ne les a pas touchés.
const PB_EN_VENTE = false;
const LOTS_PB = [[5, 500], [10, 1000], [20, 2150], [50, 5550], [100, 11500]];
if (plugins()->isEnabled('shop')) {
    boutiquePB();
    pbMonnaieDuSite();
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

    Setting::updateSettings('heraliz.boutique', now()->toDateString());
    dire('Boutique et pages légales : faites');
}

/** Chaque vote : 1 clé de la caisse « vote » et 10 hérals en jeu (joués par AzLink, joueur connecté). */
function boutiquePB(): void
{
    $Categorie = \Azuriom\Plugin\Shop\Models\Category::class;
    $Article = \Azuriom\Plugin\Shop\Models\Package::class;
    if (setting('heraliz.boutique_pb') === null) {
        foreach ($Article::all() as $a) {
            $a->delete();
        }
        foreach ($Categorie::all() as $c) {
            $c->delete();
        }
        $Offre = \Azuriom\Plugin\Shop\Models\Offer::class;
        if (class_exists($Offre)) {
            foreach ($Offre::all() as $o) {
                $o->delete();
            }
        }
        // payé directement en euros : plus de crédits du site
        Setting::updateSettings('shop.use_site_money', false);
        $serveur = Server::find(setting('servers.default')) ?? Server::first();
        $c = $Categorie::create(colonnes('shop_categories', ['name' => 'Points Boutique', 'slug' => 'points-boutique',
            'description' => '<p>Les <strong>Points Boutique (PB)</strong> se dépensent en jeu : tape <strong>/boutique</strong> pour acheter grades, clés et cosmétiques. Ils s’échangent aussi entre joueurs au spawn (/trade, taxe de 10 %). Aucun avantage en combat, aucun abonnement.</p>',
            'position' => 1, 'is_enabled' => true]));
        foreach (LOTS_PB as $i => [$euros, $pb]) {
            $bonus = $pb - $euros * 100;
            $Article::create(colonnes('shop_packages', [
                'category_id' => $c->id, 'name' => number_format($pb, 0, ',', ' ').' PB',
                'short_description' => $bonus > 0 ? '+'.number_format($bonus, 0, ',', ' ').' PB offerts' : 'Livrés en jeu tout de suite.',
                'description' => '<p>'.number_format($pb, 0, ',', ' ').' Points Boutique crédités en jeu sur ton compte (sois connecté au serveur). À dépenser dans <strong>/boutique</strong>.</p><p>Contenu numérique livré immédiatement : <strong>aucun remboursement</strong> (voir les Conditions de vente).</p>',
                'price' => $euros, 'position' => $i + 1,
                'commands' => [['command' => 'pb donner {player} '.$pb.' achat', 'trigger' => 'purchase', 'require_online' => true, 'server' => $serveur?->id]],
                'need_online' => true, 'is_enabled' => PB_EN_VENTE,
            ]));
        }
        Setting::updateSettings('heraliz.boutique_pb', now()->toDateString());
        dire('Boutique : passée aux Points Boutique ('.count(LOTS_PB).' lots, payés en euros ; anciens articles et crédits retirés)');
    }
    if (PB_EN_VENTE) {
        $Article::where('is_enabled', false)->where('name', 'like', '% PB')->update(['is_enabled' => true]);
    }
}

function siteV4(): void
{
    $reglement = '<p><strong>Échelle des sanctions :</strong> avertissement, mute, ban temporaire, ban définitif. Triche et doubles comptes : ban définitif direct. Aucun remboursement en cas de sanction.</p><h2>1. Comptes</h2><p>Un compte = une personne. Prêt, partage, vente ou achat de compte interdits. Double compte pour contourner une sanction : ban de tous les comptes.</p><p><em>Sanction : Ban définitif</em></p><h2>2. Triche</h2><p>Client modifié, x-ray, aimbot, macros, autoclick, logiciels tiers, duplication, exploitation de bug : interdit. Un bug se signale au staff sur Discord, il ne s’exploite pas.</p><p><em>Sanction : Ban compte + IP + PC</em></p><h2>3. Respect</h2><p>Pas d’insultes graves, de harcèlement, de propos racistes, sexistes, homophobes ou à caractère sexuel, ni de menaces hors jeu (doxx, DDoS).</p><p><em>Sanction : Mute puis ban</em></p><h2>4. Chat et vocal</h2><p>Pas de spam, de flood, de majuscules en boucle ni de publicité pour d’autres serveurs. En vocal : pas de musique ni de bruits pour gêner.</p><p><em>Sanction : Mute</em></p><h2>5. PvP et raids</h2><p>Le PvP, le pillage et les raids font partie du jeu. Interdit : tuer en boucle un joueur qui réapparaît, bloquer le spawn, piéger les zones de départ.</p><p><em>Sanction : Avertissement puis ban temporaire</em></p><h2>6. Bases et constructions</h2><p>Pas de constructions offensantes, de bases qui bloquent une route, une extraction ou un lieu de loot, ni de lag volontaire (machines, entités en masse).</p><p><em>Sanction : Base effacée</em></p><h2>7. Clans et alliances</h2><p>Un clan peut s’allier, trahir et piller : c’est le jeu. Une arnaque prouvée hors des règles du /trade est sanctionnée.</p><p><em>Sanction : Selon le cas</em></p><h2>8. Échanges et argent réel</h2><p>Échanges uniquement par /trade au spawn. Vendre ou acheter objets, PB, hérals ou comptes contre de l’argent réel : interdit.</p><p><em>Sanction : Ban et retrait</em></p><h2>9. Staff</h2><p>Les décisions du staff s’appliquent. Une contestation se fait calmement par ticket sur Discord, pas dans le chat. Se faire passer pour le staff : ban.</p><p><em>Sanction : —</em></p><h2>10. Anticheat</h2><p>Le jeu se lance uniquement depuis le launcher HeraliZ, qui contrôle les fichiers du jeu et peut prendre des captures du jeu avec ton accord. Le contourner = triche.</p><p><em>Sanction : Ban compte + IP + PC</em></p><p>Dernière mise à jour : 22 octobre 2026. Une question ? Discord, salon #support.</p>';
    $page = Page::where('slug', 'reglement')->first();
    if ($page === null) {
        Page::create(['title' => 'Règlement', 'slug' => 'reglement', 'description' => 'En jouant sur HeraliZ, tu acceptes ces règles.', 'content' => $reglement, 'is_enabled' => true]);
    } else {
        $page->update(['description' => 'En jouant sur HeraliZ, tu acceptes ces règles.', 'content' => $reglement]);
    }
    dire('Règlement : 10 règles (site v4)');

    if (! Page::where('slug', 'launcher')->exists()) {
        Page::create(['title' => 'Launcher', 'slug' => 'launcher', 'description' => 'Télécharger le launcher HeraliZ.', 'content' => '<p>Télécharger le launcher HeraliZ.</p>', 'is_enabled' => true]);
        dire('Page créée : Launcher');
    }
    if (! NavbarElement::where('type', 'page')->where('value', 'launcher')->exists()) {
        NavbarElement::where('position', '>=', 2)->increment('position');
        NavbarElement::create(['name' => 'Launcher', 'type' => 'page', 'value' => 'launcher', 'position' => 2]);
        dire('Menu : Launcher ajouté après Accueil');
    }
    NavbarElement::where('type', 'plugin')->where('value', 'shop.home')->update(['name' => 'Points Boutique']);

    // votes : Serveur-Minecraft.com retiré (sa vérification ne marche pas), Top-Serveurs ajouté avec la même récompense
    if (plugins()->isEnabled('vote')) {
        $Site = \Azuriom\Plugin\Vote\Models\Site::class;
        foreach ($Site::all() as $site) {
            if (str_contains((string) $site->url, 'serveur-minecraft.com')) {
                $site->update(['is_enabled' => false]);
                dire('Votes : '.$site->name.' désactivé');
            }
        }
        if (! $Site::where('url', 'like', '%top-serveurs.net%')->exists()) {
            $nouveau = $Site::create(colonnes('vote_sites', ['name' => 'Top-Serveurs', 'url' => 'https://top-serveurs.net/minecraft/vote/heraliz', 'vote_delay' => 120, 'has_verification' => true, 'is_enabled' => true]));
            $Recompense = \Azuriom\Plugin\Vote\Models\Reward::class;
            if (method_exists($nouveau, 'rewards')) {
                $nouveau->rewards()->sync($Recompense::pluck('id')->all());
            }
            dire('Votes : Top-Serveurs ajouté (toutes les 2 h ; jeton du serveur : secret GitHub TOPSERVEURS_TOKEN)');
        }
    }

    Setting::updateSettings('heraliz.site_v4', now()->toDateString());
}

// PB = monnaie du compte du site (22/10, demande du propriétaire) : un seul porte-monnaie pour le site, le launcher et le
// jeu (/boutique et /trade le liront par l'extension HeraliZ). Une seule fois (repère « heraliz.pb_monnaie ») : monnaie
// « PB », boutique payée en monnaie du site, anciens lots (articles) retirés, offres de recharge créées. À chaque envoi :
// chaque offre est proposée par chaque moyen de paiement actif (ajoutés plus tard dans l'administration).
function pbMonnaieDuSite(): void
{
    $Offre = \Azuriom\Plugin\Shop\Models\Offer::class;
    if (! class_exists($Offre)) {
        return;
    }
    if (setting('heraliz.pb_monnaie') === null) {
        Setting::updateSettings(['money' => 'PB|PB', 'shop.use_site_money' => true]);
        $Article = \Azuriom\Plugin\Shop\Models\Package::class;
        $Categorie = \Azuriom\Plugin\Shop\Models\Category::class;
        foreach ($Article::where('name', 'like', '% PB')->get() as $a) {
            $a->delete();
        }
        foreach ($Categorie::where('slug', 'points-boutique')->get() as $c) {
            if ($c->packages()->count() === 0) {
                $c->delete();
            }
        }
        foreach ($Offre::all() as $o) {
            $o->delete();
        }
        foreach (LOTS_PB as [$euros, $pb]) {
            $Offre::create(colonnes('shop_offers', ['name' => number_format($pb, 0, ',', ' ').' PB', 'price' => $euros, 'money' => $pb, 'is_enabled' => true]));
        }
        Setting::updateSettings('heraliz.pb_monnaie', now()->toDateString());
        dire('Points Boutique : monnaie du compte du site (offres de recharge 5 à 100 €)');
    }
    $Moyen = \Azuriom\Plugin\Shop\Models\Gateway::class;
    $ids = $Offre::pluck('id')->all();
    $moyens = 0;
    foreach ($Moyen::all() as $g) {
        if (method_exists($g, 'offers')) {
            $g->offers()->syncWithoutDetaching($ids);
            $moyens += $g->is_enabled ? 1 : 0;
        }
    }
    dire('Points Boutique : '.count($ids).' recharge(s), '.($moyens ? $moyens.' moyen(s) de paiement actif(s)' : 'AUCUN moyen de paiement actif (administration → Boutique → Paiements)'));
}

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
            'name' => '1 clé vote + 10 hérals + 2 PB', 'chances' => 100, 'money' => 2,
            'commands' => $commandes, 'need_online' => true, 'is_enabled' => true,
        ]));
        if ($serveur !== null && method_exists($r, 'servers')) {
            $r->servers()->sync([$serveur->id]);
        }
        dire('Votes : récompense « 1 clé vote + 10 hérals + 2 PB » créée (sites de vote à ajouter dans l’administration → Votes → Sites)');
    }
    Setting::updateSettings('heraliz.votes', now()->toDateString());
}

/**
 * Votes protégés (règles dans Votes\Garde de l'extension HeraliZ) : vote réservé aux comptes connectés (plus de vote par
 * simple pseudo) ; vérification de chaque site de vote cochée quand l'extension Vote sait la faire ; jeton du serveur
 * Top-Serveurs repris du secret GitHub TOPSERVEURS_TOKEN (passé par theme.sh dans l'environnement, jamais dans le dépôt ;
 * sans le secret, la clé déjà réglée dans l'administration est gardée). Un site sans vérification possible ne récompense
 * qu'après le clic « Voter » de notre page, anti-robot passé (Votes\Clic de l'extension HeraliZ).
 */
function votesProteges(): void
{
    if (! setting('vote.auth-required', false)) {
        Setting::updateSettings('vote.auth-required', true);
    }
    dire('Votes : réservés aux comptes du site connectés');

    $Site = \Azuriom\Plugin\Vote\Models\Site::class;
    $verifications = app(\Azuriom\Plugin\Vote\Verification\VoteChecker::class);
    $jeton = trim((string) getenv('HERALIZ_TOPSERVEURS_TOKEN'));
    $Garde = \Azuriom\Plugin\Heraliz\Votes\Garde::class;
    foreach ($Site::all() as $site) {
        // nom du site de vote sans « www. » (Site::hostFromUrl n'existe que depuis l'extension Vote 1.2.12)
        $hote = parse_url((string) $site->url, PHP_URL_HOST);
        $hote = is_string($hote) && $hote !== '' ? preg_replace('/^www\./', '', strtolower($hote)) : null;
        $maj = [];
        if ($hote === 'top-serveurs.net' && $jeton !== '' && $site->verification_key !== $jeton) {
            $maj['verification_key'] = $jeton;
        }
        if (! $site->has_verification && $hote !== null && $verifications->hasVerificationForSite($hote)) {
            $maj['has_verification'] = true;
        }
        if ($maj !== []) {
            $site->update($maj);
        }
        if (! $site->is_enabled) {
            continue;
        }
        $Stricte = \Azuriom\Plugin\Heraliz\Votes\VerificationStricte::class;
        $valide = class_exists($Stricte) && $Stricte::geree($site) && trim((string) $site->verification_key) !== ''
            ? $Stricte::jetonValide($site) : true;
        if ($valide === false) {
            dire('::warning::Votes : '.$site->name.' REFUSE la clé de vérification enregistrée (« aucun serveur associé ») : '
                .'ses votes passent à la protection par la page (clic « Voter » + anti-robot) ; corriger ou vider la clé dans '
                .'l’administration → Votes → Sites → '.$site->name);
        } elseif (class_exists($Garde) && $Garde::verifiable($site->fresh())) {
            dire('Votes : '.$site->name.' : chaque vote vérifié auprès du site de vote'
                .($valide === null ? ' (site de vote muet pendant le test de la clé : clé pas vérifiée)' : ''));
        } else {
            dire('Votes : '.$site->name.' : vérification impossible'.($hote === 'top-serveurs.net' ? ' (pas de jeton Top-Serveurs)' : '')
                .' : récompense seulement après le clic « Voter » de la page du site, anti-robot passé, au moins 20 s avant');
        }
    }

    // anti-robot du bouton « Voter » : le Turnstile de Cloudflare réglé dans l'administration (Paramètres → Sécurité,
    // captcha), le même qui protège l'inscription sur le site
    $turnstile = class_exists(\Azuriom\Plugin\Heraliz\Votes\Clic::class) && \Azuriom\Plugin\Heraliz\Votes\Clic::turnstile() !== null;
    dire($turnstile
        ? 'Votes : anti-robot Turnstile actif sur le bouton « Voter »'
        : '::warning::Votes : anti-robot Turnstile pas réglé (administration → Paramètres → Sécurité → captcha « Turnstile ») : un programme connecté à un compte validé peut encore cliquer « Voter » à la place du joueur');
}

function pbParVote(): void
{
    if (! plugins()->isEnabled('vote')) {
        return;
    }
    $Recompense = \Azuriom\Plugin\Vote\Models\Reward::class;
    $n = 0;
    foreach ($Recompense::all() as $r) {
        if ((float) $r->money < 2) {
            $nom = str_contains((string) $r->name, 'PB') ? $r->name : $r->name.' + 2 PB';
            $r->update(['money' => 2, 'name' => $nom]);
            $n++;
        }
    }
    dire('Votes : '.$n.' récompense(s) passée(s) à 2 PB par vote');
    Setting::updateSettings('heraliz.vote_pb', now()->toDateString());
}
