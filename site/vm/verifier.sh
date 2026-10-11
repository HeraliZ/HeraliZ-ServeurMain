#!/bin/bash
# Vérification de la VM du site (lancé par .github/workflows/site.yml, choix « verifier ») : lecture seule, rien n'est
# installé ni modifié. Sortie lue par Claude et par le propriétaire dans l'onglet Actions.
. /etc/os-release 2> /dev/null
echo "Système : ${PRETTY_NAME:-inconnu} (noyau $(uname -r), $(uname -m))"
echo "Processeurs : $(nproc) ; mémoire : $(free -h | awk '/^Mem/ {print $2 " (libre " $7 ")"}') ; disque / : $(df -h / | awk 'NR == 2 {print $2 " (libre " $4 ")"}')"
echo "Connecté en : $(id -un)"
deja=""
for p in nginx apache2 mariadb-server mysql-server postgresql certbot docker.io; do
  dpkg -s "$p" > /dev/null 2>&1 && deja="$deja $p"
done
echo "Déjà installé :${deja:- rien de tout ça (nginx, apache2, mariadb, mysql, postgresql, certbot, docker)}"
command -v php > /dev/null && echo "PHP présent : $(php -r 'echo PHP_VERSION;')"
echo "PHP proposé par le système : $(apt-cache policy php8.4-fpm php8.3-fpm php8.2-fpm 2> /dev/null | awk '/^php/ {p = $1} /Candidate/ && $2 != "(none)" {printf "%s %s ; ", p, $2}')"
echo "Ports en écoute : $( (ss -ltn 2> /dev/null || netstat -ltn 2> /dev/null) | awk 'NR > 1 {n = split($4, a, ":"); print a[n]}' | sort -n | uniq | tr '\n' ' ')"
if command -v ufw > /dev/null; then echo "Pare-feu ufw : $(ufw status 2> /dev/null | head -n 1)"; else echo "Pare-feu ufw : absent"; fi
code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' https://packages.sury.org/ 2> /dev/null)
if [ "${code:-000}" = 000 ]; then echo "Sortie vers internet : bloquée"; else echo "Sortie vers internet : OK"; fi
# le site et le launcher
if [ -f /var/www/azuriom/artisan ]; then
  v=$(grep -oE "VERSION = '[^']+'" /var/www/azuriom/app/Azuriom.php 2> /dev/null | head -n 1 | cut -d"'" -f2)
  echo "Azuriom : installé${v:+ (version $v)}"
elif [ -f /var/www/azuriom/index.php ]; then
  echo "Azuriom : installeur en place, installation à terminer dans le navigateur (https://heraliz.fr)"
  # ce que l'installeur vérifie avant son bouton « Télécharger » (même requête que sa page)
  etat=$(curl -s -m 20 --resolve heraliz.fr:443:127.0.0.1 -H 'X-Requested-With: XMLHttpRequest' https://heraliz.fr/ 2> /dev/null)
  python3 - "$etat" <<'PY'
import json, sys
try:
    d = json.loads(sys.argv[1])
except ValueError:
    sys.exit("Installeur d'Azuriom : pas de réponse lisible")
manque = [k for k, v in d.get("requirements", {}).items() if not v]
print("Installeur d'Azuriom : " + ("tout est prêt pour « Télécharger »" if d.get("compatible") else "il manque : " + ", ".join(manque))
      + (" ; Azuriom déjà téléchargé" if d.get("downloaded") else "") + (" ; déjà extrait" if d.get("extracted") else ""))
PY
else
  echo "Azuriom : absent"
fi
# API de connexion du launcher (AzAuth) : 422 = activée (requête vide refusée), 400 = pas activée dans l'administration
if [ -f /var/www/azuriom/artisan ]; then
  code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 -X POST \
    -H 'Accept: application/json' -H 'Content-Type: application/json' -d '{}' https://heraliz.fr/api/auth/authenticate)
  case "$code" in
    422) echo "Connexion des comptes au launcher (API d'Azuriom) : activée";;
    400) echo "Connexion des comptes au launcher (API d'Azuriom) : PAS activée (administration du site → Paramètres → Authentification)";;
    *) echo "Connexion des comptes au launcher (API d'Azuriom) : réponse inattendue (code ${code:-aucun})";;
  esac
fi
if [ -f /var/www/launcher/distribution.json ]; then
  echo "Pack du launcher : $(find /var/www/launcher -type f | wc -l) fichiers, $(du -sh /var/www/launcher | cut -f1), distribution du $(date -r /var/www/launcher/distribution.json '+%d/%m %H:%M')"
else
  echo "Pack du launcher : pas encore déposé"
fi
if [ -f /var/www/telecharger/HeraliZ-Launcher-Setup.exe ]; then
  echo "Installateur du launcher : $(du -h /var/www/telecharger/HeraliZ-Launcher-Setup.exe | cut -f1), du $(date -r /var/www/telecharger/HeraliZ-Launcher-Setup.exe '+%d/%m %H:%M')"
else
  echo "Installateur du launcher : pas encore en ligne"
fi
