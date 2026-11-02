#!/bin/bash
# Machine distante vérifiée par son empreinte (clé d'hôte SSH) avant d'y envoyer un mot de passe : VM du site (SSH) et
# SFTP du Ptero (audit du 02/11, point 41). Avant, la mise en ligne acceptait n'importe quelle machine qui répondait à
# l'adresse (« accept-new » sur une machine de GitHub toujours neuve) : un nom ou un réseau détourné recevait le mot de
# passe.
#
#   bash outils/hote_connu.sh <hôte> <port> <fichier known_hosts à écrire> [<nom de la variable GitHub>] <<< "$EMPREINTE"
#
# L'empreinte (variable ou secret GitHub, par exemple SITE_SSH_EMPREINTE, PTERO_SFTP_EMPREINTE) arrive sur l'entrée :
# une ou plusieurs lignes « type clé » (« ssh-ed25519 AAAA… »), avec ou sans le nom de la machine devant (sortie de
# ssh-keyscan acceptée telle quelle). Rend 0 et écrit le fichier si au moins une clé est lue : l'appelant passe alors
# StrictHostKeyChecking=yes avec ce fichier (une autre machine est refusée) ; pour lftp, le lanceur <fichier>.ssh fait
# la même chose (set sftp:connect-program <fichier>.ssh). Sinon rend 1 (comportement d'avant) et, si
# le nom de la variable est donné, écrit dans le résumé de la tâche les clés que GitHub voit, prêtes à recopier. Les
# clés d'hôte sont publiques : rien de secret n'est affiché (ni le nom de la machine).
set -u
hote="$1"
port="${2:-22}"
fichier="$3"
variable="${4:-}"
nom="$hote"
[ "$port" = 22 ] || nom="[$hote]:$port"
( umask 077 && : > "$fichier" )
tr -d '\r' | awk -v n="$nom" '{
  for (i = 1; i < NF; i++) if ($i ~ /^(ssh-(ed25519|rsa)|ecdsa-sha2-nistp(256|384|521)|sk-(ssh-ed25519|ecdsa-sha2-nistp256)@openssh\.com)$/ && $(i + 1) ~ /^[A-Za-z0-9+\/]+=*$/) {
    print n, $i, $(i + 1); break
  }
}' > "$fichier"
if [ -s "$fichier" ]; then
  # lanceur de ssh sans espace dans son chemin, pour « set sftp:connect-program <fichier>.ssh » de lftp : écrit entre
  # guillemets dans un « lftp -e » de plusieurs lignes, la commande « ssh -o … » était prise pour un seul nom de
  # programme (« sh: 1: ssh -a -x …: not found ») et la mise en ligne du 02/11 n'a rien pu envoyer
  printf '#!/bin/sh\nexec ssh -a -x -o StrictHostKeyChecking=yes -o UserKnownHostsFile="%s" "$@"\n' "$fichier" > "$fichier.ssh"
  chmod 700 "$fichier.ssh"
  exit 0
fi
if [ -n "$variable" ] && [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  vu=$(ssh-keyscan -T 15 -p "$port" -t ed25519,ecdsa,rsa "$hote" 2> /dev/null | awk 'NF >= 3 && $1 !~ /^#/ {print $2, $3}')
  {
    echo "### Machine distante non vérifiée : \`$variable\` absent"
    echo "- la connexion marche comme avant, mais une machine qui se ferait passer pour celle-ci recevrait le mot de passe"
    echo "- à faire une fois : Settings → Secrets and variables → Actions → onglet **Variables** → *New repository variable* → nom \`$variable\`, valeur : les lignes ci-dessous (clés publiques de la machine vues par GitHub ; rien de secret)"
    if [ -n "$vu" ]; then
      echo '```'
      printf '%s\n' "$vu"
      echo '```'
    else
      echo "- (la machine n'a pas répondu à la lecture de ses clés)"
    fi
  } >> "$GITHUB_STEP_SUMMARY"
fi
exit 1
