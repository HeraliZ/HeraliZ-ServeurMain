#!/bin/bash
# Nouvelle clé des Points Boutique (audit du 02/11, point 15), lancé en root par .github/workflows/site.yml (choix
# « nouvelle_cle_pb ») : à faire si la clé a pu fuiter (accès au Ptero donné puis retiré, sauvegarde partagée, alerte
# « nouvelle adresse IP » du salon privé…). La clé actuelle devient l'ancienne (storage/app/heraliz/pb-ancienne.cle),
# encore acceptée 7 jours au plus par le site (PbController) : le serveur de jeu continue de vendre en attendant de
# recevoir la nouvelle. Dès que le serveur de jeu se sert de la nouvelle clé, le site oublie l'ancienne.
# Ensuite : « Serveur HeraliZ » → Run workflow avec « redemarrer » (la mise en ligne écrit la nouvelle clé sur le Ptero,
# le redémarrage la fait lire au serveur de jeu). Rien d'autre n'est touché (comptes, soldes, journal des PB).
set -euo pipefail
DOSSIER=/var/www/azuriom/storage/app/heraliz
PB="$DOSSIER/pb.cle"
ANCIENNE="$DOSSIER/pb-ancienne.cle"
if [ ! -s "$PB" ]; then
  echo "::error::Pas encore de clé des Points Boutique sur le site : lancer « theme » (elle y est créée), rien à changer."
  exit 1
fi
# copie (date du jour : le délai de 7 jours part de maintenant), puis nouvelle clé posée d'un coup
install -m 600 -o www-data -g www-data "$PB" "$ANCIENNE"
(umask 077 && head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$PB.nouvelle")
chown www-data:www-data "$PB.nouvelle"
chmod 600 "$PB.nouvelle"
mv -f "$PB.nouvelle" "$PB"
if [ "$(wc -c < "$PB")" -lt 40 ] || cmp -s "$PB" "$ANCIENNE"; then
  # impossible en pratique : on remet l'ancienne plutôt que de laisser le site sans clé valable
  cp -f "$ANCIENNE" "$PB"
  echo "::error::Nouvelle clé des Points Boutique ratée : l'ancienne est gardée."
  exit 1
fi
echo "Clé des Points Boutique changée : l'ancienne reste acceptée 7 jours au plus, jusqu'à ce que le serveur de jeu ait la nouvelle."
