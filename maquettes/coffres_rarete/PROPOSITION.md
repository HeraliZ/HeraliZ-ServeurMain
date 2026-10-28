# Coffres de base par rareté : proposition (27/10), VALIDÉE

Rendus faits par `python modules/bases/outils/coffres_modeles.py --apercu maquettes/coffres_rarete` (modèles de bloc
du jeu, texture à 64 points par bloc comme l'armoire). Validés par le propriétaire le 27/10 : modèles, tableau, limites
et recettes ; la caisse commune garde sa couleur verte. Installés par `--installer` (CRac 1.25.0-beta).

## En bref

- Cinq coffres, de Commune (la caisse en métal actuelle) à Légendaire.
- Plus le coffre est rare, plus il range de cases pour la même place dans la base.
- Une limite par base et par rareté, réglable.
- Fabriqués à la Table d'armurier, dans un nouvel onglet « Rangement ».
- Fermés, ils ne coûtent rien à l'affichage (modèle « cuit », comme les décors) ; pendant l'ouverture, seule la pièce
  qui bouge est dessinée.

## Tailles, capacités et limites

| Rareté | Coffre | Place | Cases | Cases par bloc | Face à 2 caisses communes (36) | Limite par base | Points de vie (raid) |
|---|---|---|---|---|---|---|---|
| Commune (gris) | Caisse en métal (actuelle) | 1 bloc (basse) | 18 (9 × 2) | 18 | — | 14 | 100 |
| Peu commune (vert) | Malle de chantier | 2 blocs : 2 de large × 1 de haut | 45 (9 × 5) | 22,5 | + 25 % | 12 | 200 |
| Rare (bleu) | Casier métallique | 2 blocs : 1 de large × 2 de haut | 54 (9 × 6) | 27 | + 50 % | 10 | 300 |
| Épique (violet) | Caisse d'armes renforcée | 2 blocs : 2 de large × 1 de haut | 66 (11 × 6) | 33 | + 83 % | 8 | 450 |
| Légendaire (or) | Coffre-fort | 2 blocs : 1 de large × 2 de haut | 78 (13 × 6) | 39 | + 117 % | 5 | 600 |

Base au maximum : 2 250 cases sur 84 blocs de place. Chaque palier range 18 à 25 % de plus par bloc que le précédent.
Les coffres de 11 et 13 colonnes ont un écran de coffre plus large (même style), qui tient en 1920 × 1080 comme en
1366 × 768 (`ecrans_grands_coffres.png`).

### Pourquoi les petits coffres restent utiles

- La caisse commune tient dans un seul bloc : coins, sous un escalier, entre deux murs ; on en empile deux.
- Les coffres bas (malle, caisse d'armes) laissent leur dessus libre : on pose une caisse dessus.
- Les limites obligent à mélanger les raretés pour remplir une base.
- Les petits coûtent peu ; les grands demandent les matériaux rares des bases.
- Raids : une charge explosive fait 130 points aux objets voisins : une caisse commune saute, une malle tient ; un coffre
  détruit laisse tomber son contenu (comme aujourd'hui). Les objets de valeur vont dans les coffres solides.

## Recettes (Table d'armurier, onglet « Rangement »)

Règle déjà en place : le niveau des composants suit la rareté de l'objet (I → Commun, II → Peu commun, III → Rare,
IV → Épique). Le Légendaire est une exception, comme les jumelles : composants IV et matériaux légendaires des bases.

| Coffre | Recette |
|---|---|
| Caisse en métal | 6 ferraille + 4 clous (toujours 150 hérals chez le Bâtisseur) |
| Malle de chantier | 8 planches + 8 clous + 2 cordes + 3 ferraille II |
| Casier métallique | 6 fragments de métal + 2 équerres d'acier + 3 ferraille III + 1 kit de pièces III |
| Caisse d'armes renforcée | 8 fragments de métal + 4 équerres d'acier + 2 ferraille IV + 2 caoutchouc IV + 1 circuit imprimé IV |
| Coffre-fort | 6 métal renforcé + 3 bobines de fil à souder + 4 ferraille IV + 2 kits de pièces IV + 1 circuit imprimé IV |

## Ouverture animée

- Malle : le couvercle bascule vers l'arrière (0,4 s).
- Casier : la porte pivote (0,4 s).
- Caisse d'armes : les fermoirs sautent (petit à-coup), puis le couvercle s'ouvre sur sa mousse (0,5 s).
- Coffre-fort : le volant tourne, puis la porte blindée s'ouvre lentement (0,9 s).
- Sons du jeu (aucun nouveau fichier). La caisse commune s'ouvre aujourd'hui d'un coup : elle peut avoir la même
  animation (au choix).

## Règles des bases (déjà en place, valent aussi pour les nouveaux coffres)

- Adossés aux murs comme la caisse en métal ; un coffre de 2 blocs est poussé en entier.
- Ouverts seulement par le clan (droit « Ouvrir les coffres ») ; base protégée : seulement par son groupe ; les pillards
  entrés pendant un raid peuvent ouvrir (comme aujourd'hui, comme Rust).
- Raids seulement pendant la Purge ; seules la C4 et la charge explosive les abîment.
- `/base effacer` et `/wipe` : effacés avec leur contenu, objets à vie rendus (comme aujourd'hui).
- Limite : pose refusée au-delà, avec un message (« Ta base a déjà 5 coffres-forts : limite 5. ») ; ceux déjà posés
  comptent ; réglable dans `serveur/config/crac/coffres.json`.
- Caisse commune à 2 lignes : les caisses déjà posées gardent tout. Les objets des cases 19 à 27 sont mis de côté et
  reviennent dès qu'il y a de la place ; jamais perdus (ils tombent si la caisse est cassée).

## Performances

- Éléments : malle 39, casier 20, caisse d'armes 42, coffre-fort 52 (l'armoire en a une quarantaine).
- Textures à 64 points par bloc (comme l'armoire) : 1024 px pour les coffres de 2 de large, 512 px pour les autres.
- Fermé : rien n'est calculé à chaque image. Ouvert : seule la pièce qui bouge.

## Images

- `gamme.png` : les cinq coffres côte à côte, à l'échelle, contre un mur.
- `malle_chantier.png`, `casier_metallique.png`, `caisse_armes.png`, `coffre_fort.png` : vues fermé, ouvert, et étapes
  de l'ouverture (les animations en GIF et le PDF montrés au propriétaire se refont avec `--apercu`, pas gardés ici).
- `ecrans_grands_coffres.png` : écrans à 11 et 13 colonnes.
