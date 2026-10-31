# Essais en jeu du 31/10, chantier « crafts » (rien testé par Claude)

Versions : CRac 1.29.0-beta (module `bases`), Loot 0.14.1 (module `loot`). Les autres changements sont des données
(gunpack `serveur/tacz/heraliz_crafts`, `serveur/config/...`, `serveur/defaultconfigs/crac-placable-hp.txt`) : il suffit
de les mettre sur le serveur et de redémarrer. Rien n'a été compilé ici (Gradle n'a pas accès à Forge) : la
fabrication sera faite par le contrôle de branche de GitHub.

## 1. Munitions avec un bocal de poudre de niveau plus haut
Recettes en plus de la normale (onglet Munitions de la Table d'armurier). `python outils/crafts.py --bareme` les liste.

| Munition (bocal normal) | Normale | Bocal II | Bocal III | Bocal IV |
|---|---|---|---|---|
| 9 mm, .45 (I) | 2 bocaux I + 4 ferraille -> 15 | 1 + 3 ferraille -> 20 | 1 + 8 -> 45 | 1 + 10 -> 60 (pile) |
| 12 g (I) | 2 bocaux I + 6 -> 15 | 1 + 5 -> 20 | 1 + 8 -> 30 | 1 + 9 -> 36 (pile) |
| 5,56, 7,62, .50 AE (II) | 2 bocaux II + 8 -> 15 | — | 1 + 7 -> 20 | 1 + 15 -> 45 |
| .308, .338 (II) | 3 bocaux II + 12 -> 15 | — | 1 + 5 -> 10 | 1 + 15 -> 30 (pile du .338) |

À vérifier :
- les cartes d'une même munition sont côte à côte, la normale d'abord, avec leur quantité (×15, ×20, ×45, ×60) ;
- en choisissant une carte, la colonne de droite montre bien le bon bocal (I, II, III ou IV) ;
- « Avec mes composants » coché, avec seulement 1 bocal IV et 10 ferraille : seules les recettes « bocal IV » restent ;
- la fabrication donne bien le nombre annoncé (60 balles de 9 mm en une pile) ;
- recycleur : 60 balles de 9 mm faites avec un bocal IV ne rendent que des bocaux I et de la ferraille I (règle de la
  recette normale), jamais un bocal IV.

## 2. Lait concentré et vaseline
Recette : 12 tissus (niveau I) chacun, au lieu de 1.

À vérifier : la Table d'armurier demande 12 tissus pour chacun ; Jacqueline les vend toujours 30 hérals.

## 3. Table d'armurier à poser dans sa base
Fabriquée à la Table d'armurier, onglet Divers : 16 métal renforcé, 8 fil à souder, 12 équerres en acier, 4 kits de
pièces IV, 2 canons IV, 2 ressorts IV. Nom en couleur Légendaire.

À vérifier :
- pose dans sa base (droit de construire) : les deux cases doivent être dans la base et sur un sol ; hors d'une base :
  « Tu ne peux poser des blocs que dans ton claim. » ; dans la base d'un autre clan : refusée ;
- clic droit : même écran que la table du spawn (mêmes onglets, mêmes recettes, recettes ajoutées au Registre de
  l'armurier comprises) ; fabriquer une arme et des munitions marche ;
- la table reste après un redémarrage du serveur ;
- reprise (touche des objets posés de CRac, ou marteau) par un membre qui a le droit de casser : une seule table rendue,
  les deux moitiés partent ; refusée pendant un raid et pour un membre sans le droit ;
- raid (Purge) : C4 / charges l'abîment (600 PV, comme le coffre-fort : environ 2 C4) ; détruite, elle disparaît en
  entier, sans objet rendu ; un pillard ne peut pas la ramasser ;
- les tables du spawn ne se ramassent pas et ne bougent pas ;
- entretien de la base non payé : elle s'abîme comme les autres objets posés.

## 4. Vector Red Moon et Vector Enigma hors du loot
`arkaniaz_custom:vectorhy` et `arkaniaz_custom:vector_firmax` ne sortent plus des conteneurs T1 à T7 (ni du coffre
d'armes de l'hélicoptère). Elles existent toujours (`/give`, boutique).

À vérifier : `/recharger` (ou redémarrage), puis quelques coffres T7 : jamais ces deux armes.

## Aussi
Le fichier du recycleur (`serveur/config/heraliz_quetes/recyclage.json`) n'avait pas été relancé depuis le 28/10 : il
rendait des bocaux IV pour les balles .308 / .338 (la recette demande des bocaux II). Il est à jour.
