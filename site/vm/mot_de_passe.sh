#!/bin/bash
# Nouveau mot de passe SSH de la VM du site, lancé en root par .github/workflows/site.yml (choix « mot_de_passe »).
# UTILISATEUR et NOUVEAU_MDP sont écrits en tête de ce script par le workflow (secret SITE_SSH_MDP_NOUVEAU, déjà
# contrôlé : 24 caractères au moins, sans espace ni deux-points) ; le script vit dans /tmp, lisible par root seul, et
# il est effacé juste après. Le mot de passe n'apparaît jamais dans une ligne de commande ni dans le journal.
set -euo pipefail

if [ -z "${UTILISATEUR:-}" ] || [ -z "${NOUVEAU_MDP:-}" ]; then
  echo "Utilisateur ou nouveau mot de passe absent : rien n'est changé." >&2
  exit 1
fi
if ! id "$UTILISATEUR" > /dev/null 2>&1; then
  echo "Utilisateur $UTILISATEUR inconnu sur la VM : rien n'est changé." >&2
  exit 1
fi

printf '%s:%s\n' "$UTILISATEUR" "$NOUVEAU_MDP" | chpasswd
unset NOUVEAU_MDP
echo "== Mot de passe SSH de $UTILISATEUR changé"
