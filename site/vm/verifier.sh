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
installe=non
if [ -f /var/www/azuriom/artisan ]; then
  v=$(grep -oE "VERSION = '[^']+'" /var/www/azuriom/app/Azuriom.php 2> /dev/null | head -n 1 | cut -d"'" -f2)
  # Azuriom est installé quand son .env porte une clé (APP_KEY), écrite par la dernière étape dans le navigateur
  if [ -f /var/www/azuriom/.env ] && [ ! -r /var/www/azuriom/.env ]; then
    installe=inconnu
    echo "Azuriom${v:+ $v} : présent (son .env n'est lisible qu'en root : état exact inconnu)"
  elif grep -qE '^APP_KEY=base64:.+' /var/www/azuriom/.env 2> /dev/null; then
    installe=oui
    echo "Azuriom : installé${v:+ (version $v)}"
  elif [ -f /var/www/azuriom/.env ]; then
    echo "Azuriom${v:+ $v} : téléchargé, base de données réglée ; à finir dans le navigateur (jeu, compte administrateur)"
  else
    echo "Azuriom${v:+ $v} : téléchargé ; à finir dans le navigateur (base de données, jeu, compte administrateur)"
  fi
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
if [ "$installe" != non ]; then
  code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 -X POST \
    -H 'Accept: application/json' -H 'Content-Type: application/json' -d '{}' https://heraliz.fr/api/auth/authenticate)
  case "$code" in
    422) echo "Connexion des comptes au launcher (API d'Azuriom) : activée";;
    400) echo "Connexion des comptes au launcher (API d'Azuriom) : PAS activée (administration du site → Paramètres → Authentification)";;
    200) echo "Connexion des comptes au launcher (API d'Azuriom) : pas encore (l'installation d'Azuriom n'est pas finie)";;
    *) echo "Connexion des comptes au launcher (API d'Azuriom) : réponse inattendue (code ${code:-aucun})";;
  esac
fi
# thème et extensions du site (choix « theme » du workflow)
if [ "$installe" != non ]; then
  if curl -s -m 20 --resolve heraliz.fr:443:127.0.0.1 https://heraliz.fr/ 2> /dev/null | grep -q 'themes/heraliz/css/heraliz.css'; then
    echo "Thème du site : HeraliZ"
  else
    echo "Thème du site : celui d'origine d'Azuriom (lancer « theme »)"
  fi
  ext=""
  for e in heraliz shop vote; do [ -f "/var/www/azuriom/plugins/$e/plugin.json" ] && ext="$ext $e"; done
  echo "Extensions téléchargées :${ext:- aucune}"
  # extension HeraliZ : création de compte et double authentification depuis le launcher
  case "$(curl -s -m 15 --resolve heraliz.fr:443:127.0.0.1 https://heraliz.fr/api/heraliz/etat 2> /dev/null)" in
    *'"inscription":true'*) echo "Création de compte depuis le launcher : ouverte";;
    *'"inscription":false'*) echo "Création de compte depuis le launcher : fermée (inscriptions ou API d'authentification coupées dans l'administration)";;
    *) echo "Création de compte depuis le launcher : extension HeraliZ absente (lancer « theme »)";;
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
if [ -f /var/www/telecharger/latest.yml ]; then
  echo "Mise à jour automatique du launcher : version $(awk '/^version:/ {print $2}' /var/www/telecharger/latest.yml) annoncée"
else
  echo "Mise à jour automatique du launcher : pas encore annoncée (latest.yml absent)"
fi

# sécurité : ce qui est visible depuis internet
echo "== Sécurité"
ouverts=$(ss -ltnH 2> /dev/null | awk '{print $4}' | grep -vE '^(127\.[0-9.]+|\[::1\]|::1)(%[[:alnum:]]+)?:' | awk -F: '{print $NF}' | sort -n | uniq | tr '\n' ' ')
echo "Ports ouverts sur le réseau : ${ouverts:-aucun}"
if ss -ltnH 2> /dev/null | awk '{print $4}' | grep -E ':3306$' | grep -vqE '^(127\.[0-9.]+|\[::1\]|::1)(%[[:alnum:]]+)?:'; then
  echo "::warning::Base de données (port 3306) : ouverte sur le réseau"
else
  echo "Base de données (comptes, mails, mots de passe chiffrés) : accessible seulement depuis la VM elle-même"
fi
# fichiers internes d'Azuriom : jamais servis (la racine du site est son dossier public)
servis=""
total=0
for chemin in /.env /artisan /composer.json /storage/logs/laravel.log /vendor/autoload.php /config/database.php /plugins/heraliz/plugin.json /database/database.sqlite; do
  total=$((total + 1))
  code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' --resolve heraliz.fr:443:127.0.0.1 "https://heraliz.fr$chemin" || true)
  [ "$code" = 200 ] && servis="$servis $chemin"
done
if [ -n "$servis" ]; then
  echo "::warning::Fichiers internes du site visibles depuis internet :$servis"
else
  echo "Fichiers internes du site (.env, journaux, réglages, extensions) : tous refusés ($total essayés)"
fi
if [ "$(id -u)" = 0 ]; then
  echo "SSH : mot de passe $(sshd -T 2> /dev/null | awk '$1 == "passwordauthentication" {print ($2 == "yes" ? "accepté" : "refusé")}'), connexion root $(sshd -T 2> /dev/null | awk '$1 == "permitrootlogin" {print $2}')"
fi
if command -v fail2ban-client > /dev/null; then
  echo "fail2ban : actif ($(fail2ban-client status 2> /dev/null | awk -F: '/Jail list/ {gsub(/^[ \t]+/, "", $2); print $2}'))"
else
  echo "fail2ban : absent (les essais de mot de passe SSH ne sont pas bloqués : lancer « securite »)"
fi
