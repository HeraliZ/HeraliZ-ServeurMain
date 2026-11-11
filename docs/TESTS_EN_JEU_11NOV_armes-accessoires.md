# Essais en jeu du 11/11 : chantier « armes-accessoires » (rien testé par Claude)

Retours du propriétaire du 11/11 après son passage en jeu. Jars touchés : HeraliZ Tactique 2.55.0 (`armes`, jar des
JOUEURS : nouveau mixin et nouvelle table, le serveur n'est pas concerné). Gunpacks (`serveur/tacz`) changés : nom des
armes, icônes des chargeurs. Mise en ligne complète (le dossier tacz arrive chez les joueurs par le launcher), puis
redémarrage. Rien n'a été compilé ici (Gradle bloqué) : le contrôle de branche de GitHub le fera. Les deux classes Java
nouvelles ont seulement été vérifiées avec javac contre des copies vides des classes de Minecraft.

## 1. Noms des armes sans « -(SGS Beta/1.0.1) »
Le gunpack des armes Nitrogen n'a que l'anglais : le français et le russe reprennent l'anglais. Rien d'autre n'écrit ces
noms (marchands, quêtes, loot et recycleur passent par l'identifiant de l'arme).

| Avant | Maintenant |
|---|---|
| colt-(SGS Beta/1.0.1) | Colt |
| PP19-(SGS Beta/1.0.1) | PP19 |
| AN94-(SGS Beta/1.0.1) | AN94 |
| M200-(SGS Beta/1.0.1) | M200 |
| PKP-(SGS Beta/1.0.1) (aussi dans le loot, pas cité) | PKP |

À vérifier, en français, en anglais et en russe :
- le nom de ces 5 armes dans l'inventaire, l'infobulle, la Table d'armurier, chez Jack et au recycleur ;
- aucun « (SGS … ) » nulle part.

## 2a. Icônes « carte » des chargeurs à la couleur de leur rareté
Cause : la carte (fond qui se fond vers le bas dans une couleur, liseré) suivait les niveaux de TaCZ (gris, bleu,
violet, jaune), pas nos raretés, et cachait la couleur de rareté de la case. Maintenant la carte est de la couleur de
la rareté de l'accessoire (celle de l'étoile de l'infobulle et du trait du bas de la case).

| Accessoire | Rareté | Carte avant | Maintenant |
|---|---|---|---|
| Chargeur tambour 12 (wmag_12g, fusil à pompe) | Peu commun | violet | vert |
| Chargeurs étendus I (balles lourdes, légères, de précision) | Peu commun | gris | vert |
| Chargeurs Glock et RPK à fenêtre | Peu commun | violet | vert |
| Chargeur SMG à fenêtre, Speed mag | Peu commun | gris, jaune | vert |
| Chargeurs 5,56 et 7,62 à fenêtre | Rare | gris | bleu |
| Munitions FMJ et incendiaires | Épique | jaune | violet |
| Crosses d'usine légère et tactique | Épique | blanc | violet |
| Crosse d'usine lourde | Légendaire | blanc | or |

Les 8 autres cartes (chargeurs II et III, munitions HE et HP) gardent leur teinte, au ton exact du jeu.

À vérifier :
- le chargeur tambour du fusil à pompe est vert, comme son étoile « Peu commun » ; un chargeur II est bleu, un III violet ;
- le dessin (chargeur, « III+ », « FAC. ») est le même qu'avant.
- une rareté changée en jeu par `/rarete` ne change pas la carte : la reporter dans `serveur/config/heraliz_loot/raretes.json`
  et relancer `python outils/icones_accessoires.py`.

## 3. Silencieux entiers à l'écran en main
Cause : TaCZ tient un accessoire comme un objet plat de Minecraft, par le point où il se fixe sur l'arme (l'arrière d'un
silencieux). Le silencieux partait vers la droite et la moitié de sa longueur ou plus sortait de l'écran (les trois
quarts pour le Vulture .50). Maintenant, un accessoire qui sort par le côté de l'écran est tenu par son milieu et réduit
juste assez pour tenir entier (calcul : main droite, champ de vision de la main 70°, écran 16:9). 41 accessoires :
les silencieux (25 accessoires de bouche qui étouffent le tir ; le KX30, court, tenait déjà), les 5 bipieds, les 2
baïonnettes, 4 freins de bouche, 2 poignées
longues (CQR G2, tronçonneuse) et 3 crosses (CQR, CQR tan, SCW). Exemples de taille gardée : silencieux Ursus 56 %,
Phantom S1 48 %, Mirage 69 %, Vulture .50 28 %. Les autres accessoires ne bougent pas.

À vérifier :
- tenir chaque silencieux en main droite puis en main gauche : il est entier à l'écran, en bas à droite (à gauche pour la
  main gauche), pas trop petit ;
- sur l'arme, au sol, dans un cadre et vu par un autre joueur : comme avant ;
- une poignée ou un viseur ordinaires : comme avant ;
- à essayer aussi sur un écran 4:3 ou très large (le calcul est fait pour du 16:9) ; le réglage du champ de vision ne
  change pas la main (Minecraft la dessine toujours à 70°).

## 4. Bipied X48 visible en main
Cause : son modèle est dessiné 8 à 12 px devant le point où il se fixe : tenu en main, il tombait tout entier hors de
l'écran (le modèle et la texture sont bons). Corrigé par la même règle que le point 3 : il est tenu par son milieu,
réduit à 33 %. Les 4 autres bipieds, en partie hors de l'écran, sont entiers aussi.

À vérifier :
- le X48 est visible en main (droite et gauche) ; sur une arme, il est au même endroit qu'avant.

## 2b (À VALIDER : seulement après l'intégration du commit « À VALIDER »)
Chargeurs en vrai modèle 3D, pris dans le modèle d'une arme qui les porte : en main, au sol et dans un cadre ; icône
d'inventaire 3D, fond transparent (la couleur de rareté de la case se voit derrière). Crosses d'usine : icône 3D seulement
(en main, l'icône reste posée à plat, sans la carte). Munitions spéciales : carte inchangée.

À vérifier, si c'est intégré :
- les 16 chargeurs en main : modèle 3D entier à l'écran (plus de grand carré plat), au sol et dans un cadre aussi ;
- sur l'arme, rien ne change (chargeur de l'arme, comme avant) ;
- dans l'inventaire, les coffres et la barre d'objets : icône 3D, couleur de rareté visible derrière ;
- images par seconde avec un chargeur RPK à fenêtre en main (le plus lourd : environ 1 000 cubes, comme une arme).
