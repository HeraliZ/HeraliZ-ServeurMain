#!/bin/bash
# Export des comptes du site pour une recherche du staff (lancé par .github/workflows/site.yml, choix
# « chercher_comptes ») : LECTURE SEULE, rien n'est modifié. Sert à retrouver le compte de jeu d'un tricheur connu
# seulement par un nom Discord avant un ban.
# Le dépôt est public, donc les journaux de GitHub aussi : AUCUN nom n'est envoyé à GitHub ni écrit dans le journal.
# Ce script écrit sur sa sortie un JSON (comptes, Discord liés, comptes vus sur les mêmes adresses IP) que la tâche
# GitHub chiffre aussitôt avec la clé publique donnée au lancement (seul celui qui a la clé privée le lit) ; la
# comparaison avec les noms recherchés se fait hors de GitHub. Ni adresse IP ni e-mail dans l'export.
set -uo pipefail
DOSSIER=/var/www/azuriom
if [ ! -f "$DOSSIER/artisan" ]; then echo "Azuriom introuvable" >&2; exit 1; fi
TRAVAIL=$(mktemp -d)
chmod 755 "$TRAVAIL"
cat > "$TRAVAIL/exporter.php" <<'PHP'
<?php
use Azuriom\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

[, $dossier] = $argv;
require $dossier.'/vendor/autoload.php';
$app = require $dossier.'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

$tDiscord = 'heraliz_discord_comptes';
$tIps = 'heraliz_ips_comptes';
$avecDiscord = Schema::hasTable($tDiscord);
$avecIps = Schema::hasTable($tIps);

$discord = $avecDiscord ? DB::table($tDiscord)->get()->keyBy('user_id') : collect();
$nbIps = $avecIps ? DB::table($tIps)->select('user_id', DB::raw('COUNT(*) as n'), DB::raw('MAX(vu_le) as vu'))
    ->groupBy('user_id')->get()->keyBy('user_id') : collect();

$comptes = [];
foreach (User::query()->get() as $u) {
    $d = $discord->get($u->id);
    $i = $nbIps->get($u->id);
    $comptes[] = [
        'id' => $u->id,
        'pseudo' => $u->name,
        'inscrit' => substr((string) $u->created_at, 0, 10),
        'connexion' => $i && $i->vu ? substr((string) $i->vu, 0, 10) : substr((string) $u->last_login_at, 0, 10),
        'banni_site' => $u->isBanned(),
        'ips' => $i ? (int) $i->n : 0,
        'discord' => $d ? [
            'pseudo' => $d->discord_pseudo ?? null,
            'statut' => $d->statut ?? null,
            'membre' => (bool) ($d->membre ?? false),
            'banni' => (bool) ($d->banni ?? false),
        ] : null,
    ];
}

// paires de comptes vus sur au moins une même adresse IP (les adresses ne sortent jamais)
$paires = [];
if ($avecIps) {
    $paires = DB::table($tIps.' as a')->join($tIps.' as b', function ($j) {
        $j->on('a.ip', '=', 'b.ip')->on('a.user_id', '<', 'b.user_id');
    })->select('a.user_id as a', 'b.user_id as b', DB::raw('COUNT(DISTINCT a.ip) as communes'))
        ->groupBy('a.user_id', 'b.user_id')->get()->map(fn ($p) => [(int) $p->a, (int) $p->b, (int) $p->communes])->all();
}

echo json_encode(['comptes' => $comptes, 'memes_ips' => $paires, 'tables' => ['discord' => $avecDiscord, 'ips' => $avecIps]],
    JSON_UNESCAPED_UNICODE);
PHP
chmod 644 "$TRAVAIL/exporter.php"
(cd "$DOSSIER" && runuser -u www-data -- php -d display_errors=stderr "$TRAVAIL/exporter.php" "$DOSSIER")
r=$?
rm -rf "$TRAVAIL"
exit $r
