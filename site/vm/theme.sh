#!/bin/bash
# Mise à jour du site HeraliZ sur sa VM, lancée en root par .github/workflows/site.yml (choix « theme », et tout seul à
# chaque envoi sur main qui touche le thème ou l'extension) : thème HeraliZ (site/theme/heraliz), extension HeraliZ
# (site/extension/heraliz : création de compte et double authentification depuis le launcher), extensions Boutique et
# Votes d'Azuriom, puis site/vm/reglages.php (thème actif ; réglages de départ une seule fois : l'administration fait
# foi). Variables écrites en tête par la mise en ligne : ARCHIVE (thème, extension, reglages.php et images des premières
# actualités ; effacée à la fin), DISCORD_* (secrets GitHub du Discord obligatoire, vides s'ils manquent) et
# TOPSERVEURS_TOKEN (jeton du serveur Top-Serveurs : vérification des votes ; vide s'il manque).
set -euo pipefail
: "${ARCHIVE:?}"
DOSSIER=/var/www/azuriom
# Pack du launcher réservé aux comptes HeraliZ (liens signés par le site) : « non » le rouvre à tous. Verrou posé
# seulement si le launcher publié (heraliz.fr/telecharger/latest.yml) est au moins PACK_LAUNCHER_MIN, la première version
# qui demande le pack au site : les plus anciens se mettent à jour seuls au démarrage, avant de télécharger le pack.
PACK_RESERVE=oui
PACK_LAUNCHER_MIN=1.7.0
# Téléchargement du launcher (heraliz.fr/telecharger/) : « ferme » le refuse à tous (403) et cache les boutons du site
# (« Bientôt disponible ») ; « ouvert » le rend. Fermé, les launchers déjà installés ne se mettent plus à jour seuls.
TELECHARGEMENT=ouvert
# Discord obligatoire pour jouer (bêta) : chaque compte lie son Discord et doit être membre du serveur Discord, pas banni.
# « manuelle » : en plus, le staff accepte chaque compte (boutons du salon privé) ; « auto » : Discord lié et membre
# suffisent. Sans les secrets DISCORD_*, rien de tout ça n'est exigé.
VALIDATION=manuelle
TRAVAIL=$(mktemp -d /tmp/heraliz_site.XXXXXX)
trap 'rm -rf "$TRAVAIL" "$ARCHIVE"' EXIT

if [ ! -f "$DOSSIER/artisan" ] || ! grep -qE '^APP_KEY=base64:.+' "$DOSSIER/.env" 2> /dev/null; then
  echo "::error::Azuriom n'est pas encore installé sur la VM : lancer « installer », puis finir l'installation dans le navigateur."
  exit 1
fi
tar --no-same-owner -xzf "$ARCHIVE" -C "$TRAVAIL"
chmod -R a+rX "$TRAVAIL"   # lisible par le site (www-data) : reglages.php et les images
# commandes d'Azuriom avec l'utilisateur du site, pour que ses fichiers (cache, extensions) restent à lui
en_site() { (cd "$DOSSIER" && runuser -u www-data -- "$@"); }

echo "== Thème HeraliZ"
THEME="$DOSSIER/resources/themes/heraliz"
install -d -o www-data -g www-data "$THEME"
# config.json est réécrit par l'administration (Thèmes → Configurer) : jamais écrasé
rsync -a --delete --exclude=/config.json "$TRAVAIL/theme/heraliz/" "$THEME/"
[ -f "$THEME/config.json" ] || cp "$TRAVAIL/theme/heraliz/config.json" "$THEME/config.json"
chown -R www-data:www-data "$THEME"
echo "Thème déposé : $(find "$THEME" -type f | wc -l) fichiers"

echo "== Extension HeraliZ (comptes du launcher)"
EXTENSION="$DOSSIER/plugins/heraliz"
install -d -o www-data -g www-data "$EXTENSION"
rsync -a --delete "$TRAVAIL/extension/heraliz/" "$EXTENSION/"
chown -R www-data:www-data "$EXTENSION"
en_site php artisan plugin:enable heraliz
# clé des jetons de lancement du jeu (Spartan), créée une seule fois sur la VM : jamais dans le dépôt ni le launcher ;
# la mise en ligne du serveur de jeu vient la lire ici (serveur.yml)
CLE="$DOSSIER/storage/app/heraliz/jeton-jeu.cle"
if [ ! -s "$CLE" ]; then
  install -d -o www-data -g www-data -m 700 "$(dirname "$CLE")"
  (umask 077 && head -c 48 /dev/urandom | base64 | tr -d '\n=' > "$CLE")
  chown www-data:www-data "$CLE"
  echo "Clé des jetons de lancement : créée"
fi
# clé du pack du launcher (liens signés pour les comptes connectés : PackController de l'extension), créée une seule
# fois sur la VM ; nginx la reçoit dans /etc/nginx/heraliz-pack.conf (plus bas)
PACK_CLE="$DOSSIER/storage/app/heraliz/pack.cle"
if [ ! -s "$PACK_CLE" ]; then
  (umask 077 && head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$PACK_CLE")
  chown www-data:www-data "$PACK_CLE"
  echo "Clé du pack du launcher : créée"
fi
# clé des Points Boutique du serveur de jeu (/boutique, /trade : PbController), créée une seule fois ; la mise en ligne
# du serveur vient la lire ici (serveur.yml) et l'écrit dans config/heraliz_grades/boutique_site.json
PB_CLE="$DOSSIER/storage/app/heraliz/pb.cle"
if [ ! -s "$PB_CLE" ]; then
  (umask 077 && head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$PB_CLE")
  chown www-data:www-data "$PB_CLE"
  echo "Clé des Points Boutique : créée"
fi
# Discord obligatoire : réglages lus par l'extension (Discord\Reglages), réécrits à chaque envoi depuis les secrets
# GitHub, jamais dans le dépôt ; sans application, bot ou serveur Discord, fichier retiré : Discord pas exigé
DISCORD_FICHIER="$DOSSIER/storage/app/heraliz/discord.json"
if [ -n "${DISCORD_CLIENT_ID:-}" ] && [ -n "${DISCORD_CLIENT_SECRET:-}" ] && [ -n "${DISCORD_BOT_TOKEN:-}" ] && [ -n "${DISCORD_GUILD_ID:-}" ]; then
  # secrets passés à php par l'environnement (export dans ce sous-shell), jamais en arguments d'une commande : `ps`
  # montre les arguments (et `env VAR=…` en est un) à tous les comptes de la VM
  (umask 077
    export DISCORD_CLIENT_ID DISCORD_CLIENT_SECRET DISCORD_BOT_TOKEN DISCORD_GUILD_ID
    export DISCORD_PUBLIC_KEY="${DISCORD_PUBLIC_KEY:-}" DISCORD_SALON_COMPTES="${DISCORD_SALON_COMPTES:-}" \
      DISCORD_ROLES_STAFF="${DISCORD_ROLES_STAFF:-}" DISCORD_INVITATION="${DISCORD_INVITATION:-}"
    php -r '$c = [];
      foreach (["client_id", "client_secret", "bot_token", "public_key", "guild_id", "salon_comptes", "roles_staff", "invitation"] as $k) {
        $c[$k] = trim((string) getenv("DISCORD_".strtoupper($k)));
      }
      file_put_contents($argv[1], json_encode($c, JSON_UNESCAPED_SLASHES));' "$DISCORD_FICHIER")
  chown www-data:www-data "$DISCORD_FICHIER"
  chmod 600 "$DISCORD_FICHIER"
  DISCORD_ACTIF=oui
  echo "Discord obligatoire : actif (validation $VALIDATION)"
  if [ "$VALIDATION" = manuelle ] && { [ -z "${DISCORD_PUBLIC_KEY:-}" ] || [ -z "${DISCORD_SALON_COMPTES:-}" ] || [ -z "${DISCORD_ROLES_STAFF:-}" ]; }; then
    echo "::warning::Validation à la main demandée, mais DISCORD_PUBLIC_KEY, DISCORD_SALON_COMPTES ou DISCORD_ROLES_STAFF manque : validation automatique (Discord lié et membre suffisent) tant qu'ils ne sont pas réglés."
  fi
else
  rm -f "$DISCORD_FICHIER"
  DISCORD_ACTIF=non
  echo "::warning::Discord obligatoire : INACTIF (secrets DISCORD_CLIENT_ID, DISCORD_CLIENT_SECRET, DISCORD_BOT_TOKEN ou DISCORD_GUILD_ID absents) : n'importe quel compte peut jouer."
fi
# storage (journaux, sessions, clés) lisible par le site seulement, remis à chaque envoi (« installer » relancé ouvre le
# dossier du site en lecture à tous les comptes de la VM)
chown -R www-data:www-data "$DOSSIER/storage"
chmod -R o-rwx "$DOSSIER/storage"
chmod 700 "$(dirname "$CLE")"
chmod 600 "$CLE" "$PACK_CLE" "$PB_CLE"

echo "== Pack du launcher"
# Les fichiers du pack (https://heraliz.fr/launcher/) ne s'ouvrent qu'avec un lien signé par le site pour un compte
# connecté (6 h) : sans compte, plus rien ne se télécharge. Même calcul que PackController (module secure_link de nginx).
SITE_NGINX=/etc/nginx/sites-available/heraliz
PACK_NGINX=/etc/nginx/heraliz-pack.conf
pack_ouvert() {
  printf '# Pack du launcher ouvert à tous (%s), écrit par site/vm/theme.sh.\n' "$1" > "$PACK_NGINX"
  chmod 600 "$PACK_NGINX"
}
pack_signe() {   # <chemin> : lien signé pour 10 minutes, comme le site le fait pour les comptes connectés
  local e=$(( $(date +%s) + 600 ))
  printf '%s?e=%s&s=%s' "$1" "$e" "$(printf '%s' "$e$1 $(cat "$PACK_CLE")" | openssl md5 -binary | base64 | tr '+/' '-_' | tr -d '=')"
}
pack_code() { curl -s -o /dev/null -m 20 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 "https://heraliz.fr$1" || true; }
nginx_modules=$(nginx -V 2>&1 || true)
publie=$(awk '/^version:/ {print $2}' /var/www/telecharger/latest.yml 2> /dev/null | tr -d "'\"\r" || true)
if [ "$PACK_RESERVE" != oui ]; then
  raison="PACK_RESERVE=non dans site/vm/theme.sh"
elif ! grep -q http_secure_link_module <<< "$nginx_modules"; then
  raison="nginx n'a pas le module secure_link"
elif [ -z "$publie" ] || [ "$(printf '%s\n' "$PACK_LAUNCHER_MIN" "$publie" | sort -V | head -n 1)" != "$PACK_LAUNCHER_MIN" ]; then
  raison="launcher publié : ${publie:-aucun} ; publier la $PACK_LAUNCHER_MIN ou plus récente (« Launcher HeraliZ », case « publier »), puis relancer « theme »"
else
  raison=""
fi
if [ ! -f "$SITE_NGINX" ]; then
  echo "::warning::Pack du launcher : réglages nginx du site absents (lancer « installer »)."
else
  cp "$SITE_NGINX" "$TRAVAIL/heraliz-nginx.avant"
  if [ -f "$PACK_NGINX" ]; then cp "$PACK_NGINX" "$TRAVAIL/heraliz-pack.avant"; else rm -f "$TRAVAIL/heraliz-pack.avant"; fi
  if [ -z "$raison" ]; then
    (umask 077 && cat > "$PACK_NGINX" <<CONF
# Pack du launcher réservé aux comptes HeraliZ, écrit par site/vm/theme.sh : modifier le dépôt, pas ce fichier.
# Lien signé par le site pour un compte connecté (PackController de l'extension HeraliZ) : sans signature valable,
# refusé (403) ; signature périmée, refusé (410).
secure_link \$arg_s,\$arg_e;
secure_link_md5 "\$secure_link_expires\$uri $(cat "$PACK_CLE")";
if (\$secure_link = "") { return 403; }
if (\$secure_link = "0") { return 410; }
CONF
    )
  else
    pack_ouvert "$raison"
  fi
  chmod 600 "$PACK_NGINX"
  # site installé avant le verrou : ses deux accès au pack (/launcher/) lisent ce réglage
  if ! grep -q "include /etc/nginx/heraliz-pack.conf" "$SITE_NGINX"; then
    sed -i -E 's#^([[:space:]]*)alias /var/www/launcher/(distribution\.json)?;#&\n\1include /etc/nginx/heraliz-pack.conf;#' "$SITE_NGINX"
  fi
  if nginx -t > /dev/null 2>&1; then
    systemctl reload nginx
  else
    cp "$TRAVAIL/heraliz-nginx.avant" "$SITE_NGINX"
    if [ -f "$TRAVAIL/heraliz-pack.avant" ]; then cp "$TRAVAIL/heraliz-pack.avant" "$PACK_NGINX"; else pack_ouvert "réglage refusé par nginx"; fi
    nginx -t > /dev/null 2>&1 && systemctl reload nginx
    raison="nginx a refusé le verrou : réglages remis comme avant"
    echo "::warning::Pack du launcher : $raison."
  fi
  rm -f "$TRAVAIL/heraliz-nginx.avant" "$TRAVAIL/heraliz-pack.avant"
  sleep 2
  # vu comme un visiteur : un fichier inconnu du pack, sans signature (403 = verrouillé, 404 = ouvert), et la
  # distribution avec un lien signé comme le site le fait (200, ou 404 si le pack n'a pas encore été déposé)
  libre=$(pack_code /launcher/verification-du-verrou.jar)
  signe=$(pack_code "$(pack_signe /launcher/distribution.json)")
  if [ -z "$raison" ]; then
    if [ "$libre" = 403 ] && { [ "$signe" = 200 ] || [ "$signe" = 404 ]; }; then
      echo "Pack du launcher : réservé aux comptes HeraliZ (sans lien signé par le site : refusé)"
    else
      # un lien signé refusé bloquerait tous les joueurs : pack rouvert
      pack_ouvert "verrou rouvert : sans signature $libre, lien signé $signe"
      nginx -t > /dev/null 2>&1 && systemctl reload nginx
      echo "::warning::Pack du launcher : verrou mal en place (sans signature : ${libre:-aucune réponse}, lien signé : ${signe:-aucune réponse}) : pack rouvert à tous."
    fi
  elif [ "$libre" = 403 ]; then
    echo "Pack du launcher : toujours réservé aux comptes HeraliZ ($raison)"
  else
    echo "Pack du launcher : ouvert à tous ($raison) ; fichier inconnu sans signature : réponse ${libre:-aucune}"
  fi
fi

echo "== Extensions Boutique et Votes"
for ext in shop vote; do
  if [ ! -f "$DOSSIER/plugins/$ext/plugin.json" ]; then
    en_site php artisan plugin:download "$ext"
  fi
  en_site php artisan plugin:enable "$ext"
done

echo "== Téléchargement du launcher"
TELE_NGINX=/etc/nginx/heraliz-telecharger.conf
SITE_NGINX_T=/etc/nginx/sites-available/heraliz
cp "$SITE_NGINX_T" "$TRAVAIL/heraliz-nginx-t.avant"
if [ "$TELECHARGEMENT" = ferme ]; then echo 'return 403;' > "$TELE_NGINX"; else : > "$TELE_NGINX"; fi
if ! grep -q "include /etc/nginx/heraliz-telecharger.conf" "$SITE_NGINX_T"; then
  sed -i -E 's#^([[:space:]]*)alias /var/www/telecharger/;#&\n\1include /etc/nginx/heraliz-telecharger.conf;#' "$SITE_NGINX_T"
fi
if nginx -t > /dev/null 2>&1; then
  systemctl reload nginx
  echo "Téléchargement du launcher : $TELECHARGEMENT"
else
  cp "$TRAVAIL/heraliz-nginx-t.avant" "$SITE_NGINX_T"; : > "$TELE_NGINX"
  nginx -t > /dev/null 2>&1 && systemctl reload nginx
  echo "::warning::Téléchargement du launcher : nginx a refusé le réglage, laissé ouvert."
  TELECHARGEMENT=ouvert
fi
rm -f "$TRAVAIL/heraliz-nginx-t.avant"

echo "== Réglages"
# jeton du serveur Top-Serveurs (vérification des votes) : secret GitHub passé à php par l'environnement (export dans ce
# sous-shell), jamais en argument d'une commande ; reglages.php l'écrit dans le site de vote Top-Serveurs
(export HERALIZ_TOPSERVEURS_TOKEN="${TOPSERVEURS_TOKEN:-}"
  en_site env HERALIZ_TELECHARGEMENT="$TELECHARGEMENT" HERALIZ_VALIDATION="$VALIDATION" php "$TRAVAIL/vm/reglages.php" "$DOSSIER" "$TRAVAIL/contenu")
en_site php artisan view:clear > /dev/null
en_site php artisan cache:clear > /dev/null

# le site répond avec le thème (requêtes locales, comme un visiteur) et ses pages principales s'ouvrent
code=$(curl -s -o "$TRAVAIL/accueil.html" -m 30 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 https://heraliz.fr/ || true)
if [ "$code" = 200 ] && grep -q 'themes/heraliz/css/heraliz.css' "$TRAVAIL/accueil.html"; then
  echo "Accueil de https://heraliz.fr : en ligne avec le thème HeraliZ"
else
  echo "::warning::Accueil de https://heraliz.fr : réponse ${code:-aucune}, thème HeraliZ non vu dans la page"
fi
# fichiers appelés par l'accueil (styles, scripts, polices, images du thème et des actualités)
total=0
absents=0
while read -r chemin; do
  total=$((total + 1))
  code=$(curl -s -o /dev/null -m 20 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 "https://heraliz.fr$chemin" || true)
  if [ "$code" != 200 ]; then
    absents=$((absents + 1))
    echo "::warning::Fichier du site absent : $chemin (réponse ${code:-aucune})"
  fi
done < <(grep -oE '(href|src)="https?://[^/"]+/(assets|storage)/[^"]+"' "$TRAVAIL/accueil.html" | cut -d'"' -f2 | sed -E 's#^https?://[^/]+##' | sort -u)
echo "Fichiers de l'accueil (styles, scripts, polices, images) : $((total - absents)) sur $total s'ouvrent"
pages=""
for chemin in /shop /vote /reglement /news /user/login /user/register; do
  code=$(curl -s -o /dev/null -m 30 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 "https://heraliz.fr$chemin" || true)
  pages="$pages $chemin ${code:-000} ;"
  [ "$code" = 200 ] || echo "::warning::Page https://heraliz.fr$chemin : réponse ${code:-aucune}"
  if [ "$code" = 500 ]; then
    # cause de l'erreur, sans données des joueurs : la dernière ligne d'erreur du journal du site, coupée à 400 caractères
    derniere=$(grep -h '\.ERROR:' "$DOSSIER"/storage/logs/laravel*.log 2>/dev/null | tail -n 1 | cut -c1-400 || true)
    [ -n "$derniere" ] && echo "Erreur du site : $derniere"
  fi
done
echo "Pages (200 = s'ouvre) :${pages% ;}"
# API des comptes du launcher (extension HeraliZ) : ce que le launcher lit avant d'afficher son formulaire
etat=$(curl -s -m 30 --resolve heraliz.fr:443:127.0.0.1 https://heraliz.fr/api/heraliz/etat || true)
case "$etat" in
  *'"jeton":true'*) ;;
  *) echo "::warning::Jetons de lancement du jeu (Spartan) : clé absente sur le site" ;;
esac
case "$etat" in
  *'"connexion":true'*'"inscription":true'*) echo "Comptes du launcher : connexion et création de compte ouvertes" ;;
  *'"connexion":true'*) echo "::warning::Comptes du launcher : connexion ouverte, création de compte fermée (administration → Paramètres → Authentification → inscriptions)" ;;
  *'"connexion":'*) echo "::warning::Comptes du launcher : API de connexion désactivée (administration → Paramètres → Authentification)" ;;
  *) echo "::warning::Comptes du launcher : https://heraliz.fr/api/heraliz/etat ne répond pas (extension HeraliZ)" ;;
esac
# Discord obligatoire : l'adresse des boutons du staff répond (Discord l'exige : requête non signée refusée, 401)
if [ "$DISCORD_ACTIF" = oui ]; then
  code=$(curl -s -o /dev/null -m 20 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 -X POST -H 'Content-Type: application/json' \
    -d '{"type":1}' https://heraliz.fr/api/heraliz/discord/interactions || true)
  if [ "$code" = 401 ]; then
    echo "Boutons du staff (https://heraliz.fr/api/heraliz/discord/interactions) : en place (requête non signée refusée)"
  else
    echo "::warning::Boutons du staff : https://heraliz.fr/api/heraliz/discord/interactions répond ${code:-rien} à une requête non signée (401 attendu)"
  fi
fi
