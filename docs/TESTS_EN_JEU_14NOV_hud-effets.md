# Essais en jeu — chantier « hud-effets » (retours du propriétaire du 14/11)

Demande : « Créer un meilleur visuel de l'HUD inventaire et de l'affichage des effets ». Choix du propriétaire sur les
rendus : **inventaire D1 « Verre »** (avec ses 4 changements), **effets V2** à l'écran de jeu, **3 nouvelles icônes**,
puis **sac à dos, fouille et coffres** dans le style D1 (sans le nombre de cases occupées).

Versions : Noyau **1.0.19**, Équipement **1.3.0**, Interface **2.0.7**, Loot **0.18.0**, Bases **1.37.1-beta**,
Clans **3.15.1**, Quêtes **1.21.2** (montées une seule fois sur la branche). Joueurs et serveur : nouveaux jars, livrés
ensemble (Équipement, Loot, Bases, Clans et Quêtes demandent le noyau 1.0.19).

Rien n'a été essayé en jeu par Claude, ni fabriqué avec Minecraft (Minecraft et Forge ne se téléchargent pas sur la
machine de travail) : la compilation complète se fera au contrôle de branche de GitHub. Fait hors du jeu : compilation
du nouveau code contre des remplaçants des classes de Minecraft, Forge, Curios et Backpacked (javac : aucune erreur,
mais ces remplaçants reprennent ce que je sais de leurs méthodes : seule la vraie compilation le confirme) ;
`python outils/verifier_langues.py` : 0 erreur. `python outils/verifier.py` n'a pas pu tourner (il lit les jars fabriqués).

## Ce qui change

| Quoi | Pour le joueur |
|---|---|
| Inventaire (touche E) | Un panneau de verre sur le monde flouté (comme les écrans du clan), HUD caché derrière. En haut « INVENTAIRE » et la fine bande des effets ; à gauche le personnage entre ses 11 cases d'équipement (casque, masque, gilet, plastron, jambières, bottes ; sac, corps, mains, brassard, genoux) ; à droite « Sur toi » (27 cases), « Accès rapide » (la barre) et la carte du sac à dos porté (**Ouvrir**, ou la touche du sac, B). Plus d'aides, plus d'artisanat 2 × 2, plus de main gauche. On peut toujours marcher inventaire ouvert |
| Effets dans l'inventaire | Icônes de la taille d'un cœur dans un anneau fin : rouge plein + point rouge = à soigner, ambre = zone, vert qui se vide = bonus, rouge qui se vide = malus ; **bonus de loot** = icône dorée tout à gauche. Au survol : nom, « à soigner » / « effet de zone » / temps qui reste (« Encore 1 h 52 ») |
| Effets à l'écran de jeu (V2) | Les cadres de Minecraft en haut à droite disparaissent ; à leur place la même rangée d'icônes dans leur anneau (pas le bonus de loot). Rien sans effet. La pastille des hérals et les notifications de quêtes se posent dessous quand il y a des effets |
| Icônes | Intoxication alimentaire = conserve avariée, infection bactérienne = germe, infection parasitaire = ver (avant : l'os de la jambe cassée) |
| Infobulles (dans ces écrans) | Les mêmes lignes qu'avant (badge de rareté sous le nom), mais fond sombre et cadre de la couleur de la rareté |
| Sac à dos (touche B) | Même panneau : nom du sac et sa rareté en haut, son contenu, puis « Sur toi » + « Accès rapide ». B le ferme toujours |
| Fouille (conteneurs T1 à T6, salles à carte) et coffres de 9 colonnes au plus (coffres des bases, planque, coffre VIP, coffre du clan, inspection) | Même panneau, badge du palier après le nom pour le loot. Les coffres T7 (révélation des raretés) ne changent pas |
| Grands coffres des bases (caisse d'armes 11 × 6, coffre-fort 13 × 6) | Même panneau ; il s'affiche un cran plus petit (échelle 3 en 1080p) pour tenir à l'écran |

Côté serveur, rien ne change : mêmes menus, mêmes cases aux mêmes numéros, mêmes règles (anti-duplication comprise,
y compris celle du sac du 14/11). Seules les places des cases changent chez le joueur. Créatif et spectateur gardent
l'écran de Curios (avec l'artisanat et la main gauche, pour le staff).

## Où sont rangées les pièces communes (pour la suite)

Dans le kit du noyau, `modules/noyau/src/client/java/fr/heraliz/noyau/client/kit/` :

| Pièce | Rôle |
|---|---|
| `EcranVerre` | marque d'un écran à cases en verre : le noyau colore alors les infobulles du jeu (`InfobulleVerre`), atténue les icônes des cases vides (`mixin/CaseVideMixin`) et retire le carré blanc du survol (`mixin/SurvolCaseMixin`) |
| `CasesEcran` | place des cases chez le joueur (`mixin/CaseAcces` : Slot.x / Slot.y) et cases cachées |
| `CaseVerre` | dessin d'une case (20 ou 22), survol, numéros de la barre, couleur de rareté (HeraliZ Loot) |
| `InventaireVerre` | « Sur toi » + « Accès rapide » : places des cases du joueur dans n'importe quel menu, étiquettes |
| `InfobulleVerre` (+ `Bulle`) | couleurs des infobulles du jeu ; infobulle du clan gardée en mémoire pour nos textes |
| `FondVerre` | fond plein écran (monde flouté de HeraliZ Interface, sinon dégradé du clan) |
| `EcranConteneurVerre` | écran de conteneur complet (sac, fouille, coffres en héritent : quelques lignes chacun) |

Effets : `modules/noyau/src/client/java/fr/heraliz/noyau/client/effets/` (`EffetsJoueur` : liste et catégories,
`IconeEffet` : une icône dans son anneau, `EffetsHud` : la rangée V2). Écrans : `EcranInventaire`, `EcranSac` (+
`SacVerre`, `SacClient`) dans `modules/equipement/src/client/java/fr/heraliz/armory/client/curios/`, `EcranConteneur`
(Loot), `CoffreScreen` (Bases).

## Essais à faire en jeu

### Inventaire D1
1. Survie, touche E : le panneau de verre s'ouvre (pas l'écran de Curios), monde flouté derrière, HUD et main cachés.
   Comparer au rendu final validé le 14/11.
2. Les 11 cases d'équipement sont à leur place ; poser / enlever chaque pièce (casque, masque, gilet, plastron,
   jambières, bottes, sac, corps, gants, brassard, genouillères) ; une pièce refusée dans une mauvaise case.
3. Tous les gestes, sur l'équipement, « Sur toi » et la barre : clic, clic droit, Maj + clic (dans les deux sens, et
   une armure vers sa case), glisser à gauche et à droite, double clic (rassembler), touches 1 à 9 sur une case, touche
   F (ne fait rien), jeter un objet tenu en cliquant hors du panneau, touche de lâcher sur une case.
4. Toute la case réagit au survol (bords compris) ; pas de carré blanc sur l'objet, la case s'éclaire.
5. Boîte de munitions de TaCZ : Maj + clic droit dessus range les munitions (comme avant).
6. Plus d'artisanat ni de main gauche visibles ; inventaire plein : un objet resté en main gauche n'est pas montré
   (le serveur le range dès qu'une case se libère, règle du 31/10).
7. Carte du sac : nom, rareté, bouton « Ouvrir » et la touche (B) ; clic sur Ouvrir et touche B dans l'inventaire :
   l'écran du sac s'ouvre. Sans sac : « Aucun sac à dos », pas de bouton. Avec un objet tenu au curseur : rien.
8. Bande d'effets : saignement / jambe cassée (rouge + point), zone irradiée (ambre), un soin pris (vert qui se vide),
   bonus de loot (doré, tout à gauche) ; survol de chacune : bulle sous la bande, dans le panneau ; temps qui baisse.
9. Infobulles : fond sombre, cadre de la couleur de la rareté, badge sous le nom ; une arme TaCZ, une armure, un
   composant, un objet sans rareté.
10. Marcher, courir, sauter inventaire ouvert ; changer la taille de la fenêtre ; échelles d'interface 2, 3, auto ;
    écran 1366 × 768 si possible : le panneau tient toujours.
11. Créatif : l'écran de Curios (avec son bouton et l'artisanat) ; spectateur : rien ne change.
12. Changer de langue (anglais, russe) : textes traduits, bulle des effets comprise.

### Effets à l'écran de jeu (V2)
1. Sans effet : rien en haut à droite. Avec des effets : la rangée, de droite à gauche (à soigner, zone, malus, bonus
   du plus court au plus long) ; plus aucun cadre de Minecraft.
2. Saignement : anneau rouge et point rouge qui respire ; zone irradiée : ambre ; bonus : l'anneau vert se vide.
3. Bonus de loot actif : jamais dans la rangée (seulement dans l'inventaire).
4. Recevoir des hérals (pastille du solde) et une notification de quête avec des effets : elles se posent sous la
   rangée ; sans effet, elles restent tout en haut.
5. F1 : rien ; à terre (Réanimation) : comme les autres HUD.

### Icônes
1. Intoxication alimentaire, infection bactérienne, infection parasitaire : conserve avariée, germe, ver, à l'écran
   de jeu et dans l'inventaire (pack imposé chargé).

### Sac à dos, fouille et coffres
1. Sac (touche B, puis bouton Ouvrir de l'inventaire) : panneau de verre, nom et rareté, contenu ; B le ferme ;
   bruit d'ouverture ; tous les gestes de l'essai 3 entre le sac et l'inventaire ; refus d'un sac dans un sac.
2. Anti-duplication du sac (fiche `TESTS_EN_JEU_14NOV_sacs-coffres.md`) : refaire ses essais les plus courts avec le
   nouvel écran (rien ne change côté serveur, mais à vérifier).
3. Fouille : un conteneur de chaque palier T1 à T6 (badge du palier), une salle à carte, un coffre T7 (inchangé) ;
   marcher pendant la fouille.
4. Coffres : caisse en métal, malle de chantier, casier métallique (9 colonnes : écran de la fouille), caisse d'armes
   renforcée et coffre-fort (grand coffre), planque, coffre VIP, coffre du clan : ouverture, tous les gestes, deux
   joueurs sur le même coffre.
5. Grands coffres en 1080p : tout le panneau à l'écran, cases lisibles.

## À savoir
- Si un écran en verre plante au dessin, le jeu ne plante pas : l'inventaire reprend l'écran de Curios, le sac
  l'écran du mod, la fouille l'écran de coffre de Minecraft, pour la session ; un grand coffre se ferme. Une ligne
  est écrite dans le journal du jeu (`[armurerie]`, `[loot]` ou `[noyau] écran en verre en panne`).
- Deux mixins du noyau (icônes des cases vides atténuées, survol sans carré blanc) sont facultatifs : s'ils ne
  s'appliquent pas, l'icône reste pleine ou le carré blanc revient, sans autre effet.
