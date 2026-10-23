# Boutique premium (`/boutique`) : maquettes validées

**Validées par le propriétaire le 23/10, codées dans HeraliZ Grades 2.6.0** (`client/EcranBoutique`). Décisions : panneau
de droite gardé, pack ×5 de la clé Camo ajouté (1 190 PB), BETA en `#A970FF`, pas de carte « perso », avantages officiels
(1 home pour tous, zone VIP du casino, avant-première des cartes Zombies ; BETA = Roi de la Zone à vie + 10 clés des emotes
+ une emote ou un finisher sur mesure). Les images ci-dessous gardent les textes d'avant ces décisions ; les aperçus à
jour se refont avec `python mods/heraliz-grades/outils/apercu_boutique.py DOSSIER --langue toutes`. Écran de 640 × 360 unités
d'interface, montré à l'échelle 3 (1920 × 1080). Style du créateur de personnage (`/perso`) : voile sombre sur le monde,
panneaux de verre, bord d'un pixel, filet clair en haut, rouge du logo pour ce qui est choisi, police DM Sans. Chaque
texte a été mesuré avec la vraie police : aucun ne dépasse son cadre. Aucune image n'est agrandie (pas de pixels).

Toujours visible en haut à droite : **le solde de PB** (gros chiffre doré), avec « + Recharger » (ouvre la boutique du
site). En haut à gauche, 4 onglets.

| Fichier | Onglet | Contenu |
|---|---|---|
| `1_grades.png` | Grades | Les 3 grades en grandes cartes côte à côte (personnage, nom dans sa couleur, rang en losanges, prix « à partir de »). Clic sur une carte = choisie (contour de sa couleur, « CHOISI »). À droite : ses avantages, puis **1 mois / 3 mois** avec le prix (3 mois : −20 %, prix par mois), le solde avant / après, bouton « Acheter ». |
| `2_offre_beta.png` | Offre bêta test | Onglet à part (pastille violette). Grand personnage BETA « Édition limitée », avantages du Roi de la Zone à vie, comparaison avec les prix du Roi de la Zone, prix 8 990 PB. Montré dans l'état « pas assez de PB » : bouton éteint, « Il te manque … », lien pour recharger. |
| `3_cles.png` | Clés | Trois parties : **Gemme, Camo, Emotes**. Chaque clé est le **vrai modèle 3D** du jeu, avec ses packs ×1 / ×5 (économie affichée) et un bouton « Acheter ». |
| `4_sur_mesure.png` | Sur mesure | Les images du site (camo à vie, finisher, emote), « Sur devis », et un grand bandeau « Faire une demande » qui ouvre le Discord (`discord.gg/heraliz`) après la confirmation de Minecraft. |

Exemple choisi pour les maquettes : solde de 4 120 PB, Traqueur 3 mois choisi.

## Images utilisées

- Personnages des grades : `site/theme/heraliz/assets/img/grades/` (rescape, traqueur, roi_de_la_zone, beta) ; les mêmes
  que sur le site.
- PB : `site/theme/heraliz/assets/img/pb/petit.webp` pour le solde ; dans les prix, une petite pièce dessinée (l'image du
  site devient une tache à cette taille).
- Sur mesure : `site/theme/heraliz/assets/img/avantages/` (skin, finisher, emote) et les textes du site (bloc
  `lots-pb.blade.php`, page `sur-mesure`).
- Clés : vrai modèle (`KeySmith` + modèle de chaque caisse : Gemme, Graffiti pour les camos, Spectacle pour les emotes)
  dessiné hors du jeu avec le moteur de `outils/apercu_caisse.py`. En jeu, la clé serait dessinée par le jeu lui-même
  (même rendu que dans l'inventaire), pas une image.
- Polices : DM Sans du créateur (`mods/caracrea/.../font/`).

## Ce qui manque ou reste à décider

- **Pack ×5 de la clé Camo** : il n'existe pas dans `boutique.json` ; ajouté sur la maquette au prix de la clé des
  emotes ×5 (1 190 PB) pour que les trois parties se ressemblent. À confirmer (ou à retirer).
- **Couleur du BETA** : `boutique.json` dit `#8E5CFF`, le site `#A970FF` (plus lisible sur fond sombre, gardée ici).
- **« Perso »** dans la demande : le site n'a que camo, finisher et emote. Ajouter un 4e (skin de personnage) demande
  une image.
- Les avantages écrits sur les cartes viennent des réglages actuels (homes, coffre VIP 3 / 6 / 9 cases, kits 48 h,
  `/nick`, couleur du chat, téléportations −10 / −20 / −30 %, zones VIP) : les grades se règlent en jeu, le texte devra
  suivre l'éditeur des grades.
- Prix : ceux de `boutique.json` (provisoires).
