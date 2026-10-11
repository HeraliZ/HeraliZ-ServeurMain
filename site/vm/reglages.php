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
