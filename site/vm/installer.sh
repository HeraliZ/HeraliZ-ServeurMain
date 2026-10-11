#!/bin/bash
# Installation du site HeraliZ (Azuriom) sur la VM, lancée en root par .github/workflows/site.yml (choix « installer ») :
# nginx, PHP, MariaDB (base « azuriom »), installeur officiel d'Azuriom, HTTPS (Let's Encrypt), tâches planifiées, et le
# dossier du pack du launcher (https://heraliz.fr/launcher/, rempli par la mise en ligne du serveur) et celui de
# l'installateur du launcher (https://heraliz.fr/telecharger/, rempli par le workflow « Launcher HeraliZ »).
# Peut être relancée sans danger : rien de déjà installé n'est écrasé (le site en service n'est jamais retéléchargé).
# Variables écrites en tête par la mise en ligne : BDD_MDP, DOMAINES (ceux qui mènent à la VM), HTTPS (oui / non),
# URL_INSTALLEUR.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
: "${BDD_MDP:?}" "${DOMAINES:?}" "${HTTPS:?}" "${URL_INSTALLEUR:?}"
DOSSIER=/var/www/azuriom
. /etc/os-release
case "${ID:-}" in
  debian|ubuntu) ;;
  *) echo "Système non prévu : ${PRETTY_NAME:-inconnu} (Debian ou Ubuntu attendus)."; exit 1;;
esac
# service_ <demarrer|restart|reload> <service> : systemd s'il est là, sinon la commande « service »
service_() {
  if command -v systemctl > /dev/null && [ -d /run/systemd/system ]; then
    if [ "$1" = demarrer ]; then systemctl enable --now "$2"; else systemctl "$1" "$2"; fi
  else
    if [ "$1" = demarrer ]; then service "$2" start; else service "$2" "$1"; fi
  fi
}

echo "== Paquets"
apt-get update -q
apt-get install -y -q ca-certificates curl unzip cron nginx mariadb-server certbot python3-certbot-nginx lsb-release gnupg \
  rsync

# PHP 8.4 de préférence (recommandé par Azuriom ; 8.3 ou 8.2 sinon) ; dépôt Sury (Debian) ou ondrej (Ubuntu) si le système
# n'en propose aucun
choisir_php() {
  local v
  for v in 8.4 8.3 8.2; do
    # (sortie gardée avant le grep : « grep -q » ferme le tube et ferait échouer la commande avec pipefail)
    if grep -q 'Candidate: [0-9]' <<< "$(apt-cache policy "php$v-fpm" 2> /dev/null)"; then echo "$v"; return 0; fi
  done
  return 1
}
if ! V=$(choisir_php); then
  if [ "$ID" = ubuntu ]; then
    apt-get install -y -q software-properties-common
    add-apt-repository -y ppa:ondrej/php
  else
    curl -fsSLo /tmp/debsuryorg-archive-keyring.deb https://packages.sury.org/debsuryorg-archive-keyring.deb
    dpkg -i /tmp/debsuryorg-archive-keyring.deb
    echo "deb [signed-by=/usr/share/keyrings/deb.sury.org-php.gpg] https://packages.sury.org/php/ $VERSION_CODENAME main" > /etc/apt/sources.list.d/php.list
  fi
  apt-get update -q
  V=$(choisir_php)
fi
apt-get install -y -q "php$V-fpm" "php$V-cli" "php$V-mysql" "php$V-bcmath" "php$V-mbstring" "php$V-xml" "php$V-curl" \
  "php$V-zip" "php$V-gd" "php$V-intl"
cat > "/etc/php/$V/fpm/conf.d/99-heraliz.ini" <<INI
; Site HeraliZ (Azuriom) : images de la boutique et des actualités, extensions
upload_max_filesize = 64M
post_max_size = 64M
memory_limit = 256M
expose_php = Off
INI
service_ demarrer "php$V-fpm"
service_ restart "php$V-fpm"
echo "PHP $V prêt."

echo "== Base de données"
service_ demarrer mariadb
MDP_SQL=$(printf '%s' "$BDD_MDP" | sed -e 's/\\/\\\\/g' -e "s/'/''/g")
mysql -u root <<SQL
CREATE DATABASE IF NOT EXISTS azuriom CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'azuriom'@'localhost' IDENTIFIED BY '$MDP_SQL';
ALTER USER 'azuriom'@'localhost' IDENTIFIED BY '$MDP_SQL';
GRANT ALL PRIVILEGES ON azuriom.* TO 'azuriom'@'localhost';
CREATE USER IF NOT EXISTS 'azuriom'@'127.0.0.1' IDENTIFIED BY '$MDP_SQL';
ALTER USER 'azuriom'@'127.0.0.1' IDENTIFIED BY '$MDP_SQL';
GRANT ALL PRIVILEGES ON azuriom.* TO 'azuriom'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
echo "Base « azuriom » et utilisateur « azuriom » prêts."

echo "== Azuriom"
if [ -f "$DOSSIER/.env" ]; then
  echo "Azuriom est déjà installé dans $DOSSIER : rien n'est retéléchargé."
elif [ -n "$(ls -A "$DOSSIER" 2> /dev/null)" ]; then
  echo "L'installeur est déjà dans $DOSSIER : rien n'est retéléchargé."
else
  install -d -o www-data -g www-data "$DOSSIER"
  curl -fsSL -o /tmp/AzuriomInstaller.zip "$URL_INSTALLEUR"
  unzip -o -q /tmp/AzuriomInstaller.zip -d "$DOSSIER"
  rm -f /tmp/AzuriomInstaller.zip
  echo "Installeur d'Azuriom déposé dans $DOSSIER."
fi
chown -R www-data:www-data "$DOSSIER"
chmod -R u+rwX,g+rX,o+rX "$DOSSIER"
# le .env d'Azuriom (mot de passe de la base, clé du site) : lisible par le site seulement
if [ -f "$DOSSIER/.env" ]; then chmod 640 "$DOSSIER/.env"; fi
# racine du site : le dossier public d'Azuriom dès qu'il existe (sinon l'installeur, à la racine du dossier)
RACINE="$DOSSIER"
[ -f "$DOSSIER/public/index.php" ] && RACINE="$DOSSIER/public"
echo "Racine du site : $RACINE"
# pack et installateur du launcher : déposés par rsync avec l'utilisateur SSH (root ou celui qui a lancé sudo)
install -d -m 755 -o "${SUDO_USER:-root}" /var/www/launcher /var/www/telecharger
chown -R "${SUDO_USER:-root}" /var/www/launcher /var/www/telecharger

echo "== nginx"
ECOUTE6=""
DEFAUT6=""
if [ -f /proc/net/if_inet6 ]; then   # IPv6 seulement si la machine l'a (sinon nginx refuse de démarrer)
  ECOUTE6="listen [::]:80;"
  DEFAUT6="listen [::]:80 default_server; listen [::]:443 ssl default_server;"
fi
# le site ne répond pas à l'IP de la VM ni à un nom inconnu (recommandation d'Azuriom)
cat > /etc/nginx/sites-available/heraliz-defaut <<NGINX
# Écrit par site/vm/installer.sh : requêtes qui ne visent pas heraliz.fr refusées.
server {
    listen 80 default_server;
    listen 443 ssl default_server;
    $DEFAUT6
    server_name _;
    ssl_reject_handshake on;
    return 444;
}
NGINX
ln -sf /etc/nginx/sites-available/heraliz-defaut /etc/nginx/sites-enabled/heraliz-defaut
cat > /etc/nginx/sites-available/heraliz <<NGINX
# Site HeraliZ (Azuriom), écrit par site/vm/installer.sh : modifier le dépôt, pas ce fichier.
server {
    listen 80;
    $ECOUTE6
    server_name $DOMAINES;
    root $RACINE;
    index index.php;
    charset utf-8;
    client_max_body_size 64M;
    server_tokens off;

    add_header X-Frame-Options "SAMEORIGIN";
    add_header X-Content-Type-Options "nosniff";
    add_header Referrer-Policy "strict-origin-when-cross-origin";

    location / {
        try_files \$uri \$uri/ /index.php?\$query_string;
    }

    location = /favicon.ico { access_log off; log_not_found off; }
    location = /robots.txt  { access_log off; log_not_found off; }

    error_page 404 /index.php;

    location ~ \.php\$ {
        fastcgi_pass unix:/run/php/php$V-fpm.sock;
        fastcgi_param SCRIPT_FILENAME \$realpath_root\$fastcgi_script_name;
        include fastcgi_params;
    }

    location ~ /\.(?!well-known).* {
        deny all;
    }

    # Pack du launcher (distribution Helios et fichiers du jeu), déposé par la mise en ligne du serveur
    location = /launcher/distribution.json {
        alias /var/www/launcher/distribution.json;
        add_header Cache-Control "no-cache";
        gzip on;
        gzip_types application/json;
    }
    location ^~ /launcher/ {
        alias /var/www/launcher/;
        autoindex off;
    }

    # Installateur du launcher (HeraliZ-Launcher-Setup.exe), déposé par le workflow « Launcher HeraliZ »
    location ^~ /telecharger/ {
        alias /var/www/telecharger/;
        autoindex off;
        add_header Cache-Control "no-cache";
    }
}
NGINX
ln -sf /etc/nginx/sites-available/heraliz /etc/nginx/sites-enabled/heraliz
rm -f /etc/nginx/sites-enabled/default
nginx -t
service_ demarrer nginx
service_ reload nginx
if command -v ufw > /dev/null && grep -q 'Status: active' <<< "$(ufw status 2> /dev/null)"; then
  ufw allow 'Nginx Full' > /dev/null
  echo "Pare-feu ufw : ports 80 et 443 ouverts."
fi

echo "== HTTPS"
if [ "$HTTPS" = oui ]; then
  args=()
  for d in $DOMAINES; do args+=(-d "$d"); done
  if certbot --nginx --non-interactive --agree-tos --register-unsafely-without-email --redirect --keep-until-expiring "${args[@]}"; then
    echo "Certificat HTTPS en place pour : $DOMAINES (renouvelé tout seul par certbot). Site : https://${DOMAINES%% *}"
  else
    echo "::warning::Certificat HTTPS refusé (port 80 fermé chez l'hébergeur ?) : le site marche en http://${DOMAINES%% *} ; relancer « installer » plus tard."
  fi
else
  echo "HTTPS non demandé (le domaine ne mène pas encore à la VM) : relancer « installer » une fois le DNS à jour."
fi

echo "== Tâches planifiées d'Azuriom"
cat > /etc/cron.d/azuriom <<CRON
# Site HeraliZ (Azuriom) : tâches planifiées (votes, nettoyages...), écrit par site/vm/installer.sh
* * * * * www-data [ -f $DOSSIER/artisan ] && cd $DOSSIER && php artisan schedule:run > /dev/null 2>&1
CRON
chmod 644 /etc/cron.d/azuriom
service_ demarrer cron

echo "== Fini"
echo "Base de données pour l'installeur : hôte 127.0.0.1, port 3306, base azuriom, utilisateur azuriom (mot de passe : le secret SITE_BDD_MDP)."
