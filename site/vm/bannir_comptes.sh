#!/bin/bash
# Ban définitif décidé par le staff (lancé par .github/workflows/site.yml, choix « bannir_comptes ») pour les comptes du
# site dont les numéros sont dans COMPTES (écrite en tête par la mise en ligne, depuis l'entrée de la tâche GitHub :
# jamais dans le dépôt). Pour chaque compte :
#  1. son Discord lié est banni du serveur Discord par le bot (permission « Bannir des membres ») ;
#  2. le site le voit aussitôt (Comptes::verifier forcé) et fait le reste comme pour tout Discord banni : compte du site
#     banni (le launcher efface le jeu de ce PC), adresses IP récentes bloquées, « banpc <pseudo> def » envoyé au
#     serveur de jeu par AzLink (compte + IP + PC dans Spartan), staff prévenu dans le salon privé ;
#  3. ban du staff : TOUTES les adresses IP notées pour ce compte sont bloquées (pas seulement celles des dernières
#     48 h), sauf une adresse où un autre compte non banni a aussi été vu (box familiale, 4G : jamais de victime).
# Le journal de GitHub est public : aucun pseudo, nom Discord, e-mail ni adresse IP n'y est écrit (numéros et
# comptes seulement). Levée : administration du site (Bans), Discord (bans du serveur) et /unbanpc en jeu.
set -uo pipefail
DOSSIER=/var/www/azuriom
if [ ! -f "$DOSSIER/artisan" ]; then echo "Azuriom introuvable"; exit 1; fi
if ! [[ "${COMPTES:-}" =~ ^[0-9]+(,[0-9]+)*$ ]]; then echo "COMPTES : numéros de comptes séparés par des virgules"; exit 1; fi
TRAVAIL=$(mktemp -d)
chmod 755 "$TRAVAIL"
cat > "$TRAVAIL/bannir.php" <<'PHP'
<?php
use Azuriom\Models\User;
use Azuriom\Plugin\Heraliz\Discord\Comptes;
use Azuriom\Plugin\Heraliz\Discord\Reglages;
use Azuriom\Plugin\Heraliz\Discord\Stockage;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;

[, $dossier, $liste] = $argv;
require $dossier.'/vendor/autoload.php';
$app = require $dossier.'/bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

if (! class_exists(Comptes::class)) {
    fwrite(STDERR, "Extension HeraliZ introuvable : rien n'est fait.\n");
    exit(1);
}
$ids = array_values(array_unique(array_map('intval', explode(',', $liste))));
$echecs = 0;
foreach ($ids as $id) {
    echo "\n### compte n°$id\n";
    $user = User::find($id);
    if ($user === null) {
        echo "- compte introuvable\n";
        $echecs++;
        continue;
    }
    $ligne = Comptes::ligne($id);
    if ($ligne === null) {
        echo "- aucun Discord lié : ban Discord impossible (bannir à la main : administration du site, puis /banpc en jeu)\n";
        $echecs++;
        continue;
    }

    // 1. ban Discord par le bot
    $r = rescue(fn () => Http::withHeaders([
        'Authorization' => 'Bot '.Reglages::get('bot_token'),
        'X-Audit-Log-Reason' => rawurlencode('Triche : ban définitif du staff HeraliZ (site, IP, PC)'),
    ])->timeout(15)->acceptJson()->put('https://discord.com/api/v10/guilds/'.Reglages::get('guild_id').'/bans/'.$ligne->discord_id,
        ['delete_message_seconds' => 0]), null, false);
    $code = $r?->status();
    echo '- Discord : '.(in_array($code, [200, 204], true) ? 'banni du serveur Discord' : 'ÉCHEC du ban (code '.($code ?? 'aucun').')')."\n";

    // 2. le site constate le ban et le propage (site, IP récentes, banpc, salon du staff)
    $ligne = Comptes::verifier($user, $ligne, true);
    $user->refresh();
    echo '- état Discord vu par le site : '.($ligne->banni ? 'banni' : 'PAS banni')."\n";
    echo '- compte du site : '.($user->isBanned() ? 'banni (le launcher efface le jeu)' : 'PAS banni')."\n";

    // 3. toutes les adresses du compte, sauf celles partagées avec un compte non banni
    $bloquees = 0;
    $partagees = 0;
    if ($ligne->banni) {
        foreach (DB::table(Stockage::IPS)->where('user_id', $id)->pluck('ip') as $ip) {
            $autres = DB::table(Stockage::IPS)->where('ip', $ip)->where('user_id', '!=', $id)->pluck('user_id')
                ->contains(fn ($autre) => ($u = User::find($autre)) !== null && ! $u->isBanned());
            if ($autres) {
                $partagees++;
                continue;
            }
            $bloquees += DB::table(Stockage::IPS_BANNIES)->insertOrIgnore([
                'ip' => $ip, 'user_id' => $id, 'raison' => 'Ban du staff (triche), compte n°'.$id, 'cree_le' => now(),
            ]);
        }
    }
    $total = DB::table(Stockage::IPS_BANNIES)->where('user_id', $id)->count();
    echo "- adresses IP bloquées pour ce compte : $total (dont $bloquees ajoutée(s) maintenant ; $partagees laissée(s) car partagée(s) avec un autre joueur)\n";
    // le message automatique du ban Discord (juste avant) ne compte que les adresses des dernières 48 h : on donne au
    // staff le vrai total après le ban du staff
    $salon = Reglages::get('salon_comptes');
    if ($salon !== '' && $ligne->banni) {
        rescue(fn () => \Azuriom\Plugin\Heraliz\Discord\DiscordApi::message($salon, ['content' => '🔨 Ban du staff, compte du site `'
            .str_replace('`', 'ˋ', $user->name).'` : '.$total.' adresse(s) IP bloquée(s) au total (toutes celles du compte'
            .($partagees > 0 ? ', sauf '.$partagees.' partagée(s) avec un autre joueur' : '').'), ban compte + IP + PC envoyé au serveur de jeu.']), null, false);
    }

    // ban en jeu (compte + IP + PC) : commande envoyée au serveur de jeu par AzLink
    Comptes::envoyerCommandes();
    $c = DB::table(Stockage::COMMANDES)->where('user_id', $id)->where('commande', 'like', 'banpc %')->orderByDesc('id')->first();
    echo '- ban en jeu (banpc def) : '.($c === null ? 'AUCUNE commande (pseudo de jeu invalide ?)'
        : ($c->envoyee_le ? 'envoyé au serveur de jeu' : 'en attente (AzLink : '.($c->erreur ?? 'pas encore envoyé').')'))."\n";

    if (! $ligne->banni || ! $user->isBanned() || $c === null) {
        $echecs++;
    }
}
echo "\n".($echecs === 0 ? 'Tout est banni.' : "$echecs compte(s) à reprendre (voir ci-dessus).")."\n";
exit($echecs === 0 ? 0 : 2);
PHP
chmod 644 "$TRAVAIL/bannir.php"
(cd "$DOSSIER" && runuser -u www-data -- php -d display_errors=stderr "$TRAVAIL/bannir.php" "$DOSSIER" "$COMPTES")
r=$?
rm -rf "$TRAVAIL"
exit $r
