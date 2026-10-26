# À tester en jeu — construction des bases (CRac 1.24.0-beta, 26/10)

Vérifié hors du jeu : le mod compile, `verifier.py bases` passe (séparation client / serveur, langues), banc de calcul
(56 vérifications) sur les classes du mod : soutiens, placement, collisions, raccords de rampes, rendus, portes.

**Vu en jeu par Claude le 26/10** (serveur d'essai `C:\hzt`, profil CurseForge « HeraliZ Decors », client automatique
`outils/client_essai.py` ; parcours construit par `/cracbuild essai <pos>`, scripts `outils/capture/scripts/essai_bases*.json`,
positions du joueur dans `capture/journal.txt`) :
- `/cracbuild essai` : 44 pièces posées, 0 refusée (dont les murs autour d'une armoire posée d'abord dans un coin) ;
- demi-mur nu (1,25 bloc) : saut ordinaire, le joueur tient dessus ; sous un plafond : saut accroupi dessus, passage de
  l'autre côté, debout ensuite ;
- plancher triangle à mi-hauteur sur un demi-mur : on saute dessus (1,5 bloc) ; avec le plan, il se pose devant ;
- rampe : on la gravit ; on passe dessous (sous la partie haute des deux rampes raccordées) ;
- rampe triangle : on la gravit ; on passe de la rampe carrée raccordée à la rampe triangle sans buter ;
- escalier en L : depuis une fondation voisine, marches, palier, haut ; on tient debout sous la seconde volée ;
- armoire retirée : le serveur remet le mur dans ses deux cases ;
- captures : fenêtre large, meurtrière (on voit le ciel au travers, porte fermée et ouverte), portes ouvertes des trois
  paliers sans défaut visible sur image fixe (le clignotement se juge en bougeant : à revoir par vous), rampe posée par
  le plan sur le haut des murs.
- Défaut d'affichage déjà existant (pas lié à ces changements) : une armoire retirée par `/setblock` reste dessinée chez
  le joueur jusqu'à ce qu'il s'éloigne et revienne (vu aussi sur une armoire seule, sans mur). À vérifier avec une armoire
  cassée normalement.

Ce qu'un joueur doit encore confirmer : la sensation (saut, glissade), le clignotement des portes en bougeant, l'aspect
général. Canal réseau CRac version 25 : joueurs et serveur doivent avoir les jars de la **même** fabrication.

Serveur d'essai seulement : `openOnFirstJoin = false` dans `dayz_map/serverconfig/caracrea-server.toml` de `C:\hzt`
(le créateur de personnage bloquait le joueur d'essai) ; le parcours est construit en l'air (x 395-485, y 199-215,
z 394-410) sur la copie de test de la carte.

## 1. Demi-mur
- [ ] Un demi-mur nu fait maintenant 1,25 bloc : on saute dessus normalement. Dès qu'un plancher, une rampe ou un mur
      est posé dessus, il remonte à 1,5 bloc (sans jour sous ce qui repose dessus).
- [ ] Sous un plafond (plancher du niveau du dessus) : accroupi, sauter sur le demi-mur et passer de l'autre côté.
- [ ] Plancher posé sur un demi-mur, les 3 autres côtés fermés par des murs entiers : casser le demi-mur → le plancher
      reste. Sans les murs autour : il tombe (normal).
- [ ] Montée : demi-mur, plancher triangle posé dessus, puis le plancher du niveau suivant : on monte en sautant.

## 2. Plancher triangle sur un demi-mur
- [ ] Debout à côté du demi-mur, viser son dessus avec le plancher triangle : il apparaît **devant** (de l'autre côté
      du demi-mur), plus sur soi (« Quelqu'un gêne le passage »). Essayer les 4 côtés d'une fondation.
- [ ] Viser le haut d'un mur entier depuis le dessous (pour un plafond) : le plancher reste du côté du joueur.

## 3. Fenêtre
- [ ] Nouvelle fenêtre plus large (1,75 × 1 bloc), sur tous les paliers ; barreaux et vitre sur toute la largeur.
- [ ] Les fenêtres déjà posées prennent la nouvelle forme (même pièce) : vérifier qu'aucune ne gêne.

## 4. Escalier en L
- [ ] Escalier ouvert : planches de marche, palier sur un poteau ; on passe sous la seconde volée.
- [ ] On monte et on descend sans sauter.

## 5. Rampes
- [ ] Rampe ouverte : on passe sous sa partie haute ; on marche dessus normalement (montée pleine et demie, touche R).
- [ ] Deux rampes côte à côte, même sens, même palier : plus de rainure ni de rebord entre elles. Palier différent,
      sens opposé ou mur entre les deux : chacune garde ses rebords.
- [ ] Améliorer une des deux au marteau : elles se séparent ; améliorer l'autre au même palier : elles se raccordent.
- [ ] Carré entouré de murs, sans plancher : viser le haut d'un mur avec la rampe → elle se pose sur les murs.
- [ ] **Rampe triangle** (nouvelle, dans la roue du plan) : sur une fondation ou un plancher triangle, ou sur le haut des
      murs d'un triangle ; on monte du coin bas vers le côté opposé ; R change le coin bas et la demi-montée ; plancher
      du niveau suivant posé contre sa sortie ; amélioration au marteau, réparation, destruction.
- [ ] Rampe triangle contre une rampe carrée (côté commun, même coin bas) : raccord sans marche.

## 6. Armoire (Claim Block)
- [ ] Poser l'armoire **en premier** dans un coin de la fondation, puis les murs autour : les murs se posent.
- [ ] Le mur ne traverse pas l'armoire (il garde sa moitié extérieure dans cette case).
- [ ] Retirer l'armoire : le mur se rebouche dans la case (plus de trou). Même chose avec la caisse en métal.
- [ ] Un coffre ou un autre bloc à cet endroit refuse toujours le mur (normal).

## 7. Portes
- [ ] Porte simple et double, ouvertes, tous paliers : plus de clignotement noir contre le chambranle, sous tous les
      angles ; ouvrir / fermer plusieurs fois.
- [ ] Porte **blindée** (palier blindé) : on voit à travers la meurtrière, des deux côtés, porte fermée et ouverte. La
      porte arrête toujours les balles (la fente n'est que pour les yeux).

## 8. Toits retirés
- [ ] Plus de toit ni de toit triangle dans la roue du plan ni dans `/base info`.
- [ ] Les toits déjà posés restent, s'améliorent et se cassent comme avant (aucune erreur au chargement du monde).
