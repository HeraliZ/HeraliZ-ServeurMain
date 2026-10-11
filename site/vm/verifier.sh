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
