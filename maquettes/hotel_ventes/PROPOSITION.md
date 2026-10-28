# Hôtel des ventes — proposition (maquette du 28/10, à valider)

Images : `1_acheter.png`, `2_vendre.png`, `3_mes_ventes.png` (source : les `.html` du même dossier).
Style des écrans actuels (comme « Quêtes du jour ») ; vraies icônes du jeu ; noms dans la couleur de leur rareté.

## Comment ça marche
- Ouvert au spawn seulement (PNJ « commissaire-priseur » à poser, et/ou commande `/hdv` au spawn), jamais en combat, en
  match, à terre ni sur la carte.
- **Acheter** : recherche, catégories, monnaie (hérals / PB), tri ; à droite le détail de l'offre (usure, accessoires),
  le prix, les 10 % de frais **payés par l'acheteur** pour les PB (100 PB affichés → 110 PB payés, le vendeur reçoit
  100), le solde après l'achat ; achat en deux clics (comme la boutique).
- **Vendre** : on clique un objet de son inventaire, on choisit hérals ou PB, le prix et la durée. Une arme est
  déchargée à la mise en vente (les balles sont rendues). Interdits : objets à vie, prêtés par un match, de quête, à camo
  à vie (même règle que les échanges des marchands et `/trade`).
- **Mes ventes** : ventes en cours (retirer), vendues (hérals à récupérer ; PB versés directement sur le compte du site),
  expirées (objet à récupérer). Récupération seulement au spawn, dans des cases libres.

## Sécurité (contre la duplication et l'argent perdu)
Même méthode que `/trade` (Grades 2.3.x), qui a déjà été durcie :
1. Mise en vente : l'objet est retiré de l'inventaire et mis en dépôt par le serveur (fichier hors du monde), puis le
   joueur est sauvegardé. Il n'existe plus qu'à un seul endroit.
2. Achat : l'offre est réservée (personne d'autre ne peut l'acheter), puis le paiement est fait en premier :
   hérals retirés à l'acheteur, ou PB débités par le site avec une référence unique jouée une seule fois.
   Paiement refusé = offre libérée, rien ne bouge. Site muet = l'achat attend la réponse (même après un redémarrage).
3. Paiement accepté : l'objet passe dans « à récupérer » de l'acheteur (ou directement dans son inventaire s'il a la
   place), l'argent dans « à récupérer » du vendeur ; tout écrit en une fois sur le disque.
4. Chaque opération est notée dans un journal (qui, quoi, combien, référence).

## Points à trancher
1. **Où l'ouvrir** : un PNJ au spawn, la commande `/hdv` au spawn, ou les deux ?
2. **Ventes en même temps** : 5 par joueur (proposé), plus avec un grade boutique ?
3. **Durée** : 12 h / 24 h / 48 h (proposé) ?
4. **Ventes en hérals** : sans frais (proposé), ou une petite taxe qui brûle des hérals (bon pour l'économie) ?
5. **Prix minimum / maximum** : pour éviter de faire passer des PB d'un compte à l'autre avec un objet sans valeur
   (un ami « vend » un bandage 5 000 PB). Proposé : prix plafonné selon la rareté de l'objet pour les ventes en PB.
