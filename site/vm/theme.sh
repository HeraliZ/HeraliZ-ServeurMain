#!/bin/bash
# Mise à jour du site HeraliZ sur sa VM, lancée en root par .github/workflows/site.yml (choix « theme », et tout seul à
# chaque envoi sur main qui touche le thème) : thème HeraliZ (site/theme/heraliz), extensions Boutique et Votes
# d'Azuriom, puis site/vm/reglages.php (thème actif ; réglages de départ une seule fois : l'administration fait foi).
# Variable écrite en tête par la mise en ligne : ARCHIVE (thème, reglages.php et images des premières actualités ;
# effacée à la fin).
set -euo pipefail
: "${ARCHIVE:?}"
DOSSIER=/var/www/azuriom
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

echo "== Extensions Boutique et Votes"
for ext in shop vote; do
  if [ ! -f "$DOSSIER/plugins/$ext/plugin.json" ]; then
    en_site php artisan plugin:download "$ext"
  fi
  en_site php artisan plugin:enable "$ext"
done

echo "== Réglages"
en_site php "$TRAVAIL/vm/reglages.php" "$DOSSIER" "$TRAVAIL/contenu"
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
done
echo "Pages (200 = s'ouvre) :${pages% ;}"
