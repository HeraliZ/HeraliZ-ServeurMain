# À tester en jeu — bases-2 (CRac 1.29.4-beta, Quêtes 1.15.2, ClashWar 1.0.1, Boutique 1.3.2, Monde 1.0.5, 31/10)

**Rien de tout cela n'a été testé en jeu ni compilé ici** (Gradle ne joint pas le dépôt de Forge depuis la machine de
travail) : la fabrication complète est faite par le contrôle de branche de GitHub. Vérifié hors du jeu : lecture `javac`
des fichiers Java changés, `verifier_langues` (0 erreur), calcul des places refait à part, rendus de contrôle des coffres
amincis et d'une fondation fermée avec ses caisses (géométrie des murs reprise de `PieceMeshes`, pas le vrai rendu du jeu).
Le GameTest `IntegrityGameTests.aCrateTakesTheInnerHalfOfAStraightWall` est mis à jour (angles) mais n'a pas été lancé.

**Canal CRac 30** (nouvel état des coffres par rareté) : joueurs et serveur doivent avoir les jars de la **même** fabrication.

## 1. 9 caisses en métal dans une fondation fermée (le plus important)
Places (calcul hors du jeu, mêmes règles que le mod) :

| Configuration | Avant (1.29.0) | Maintenant |
|---|---|---|
| fondation seule, sans mur | 9 | 9 |
| fondation fermée par 4 murs | 5 | **9** |
| fondation, 3 murs (un côté ouvert) | 7 | 9 |
| deux fondations côte à côte, une pièce (6 murs) | 14 | 18 (limite de caisses par base : 14) |
| deux fondations, deux pièces (mur entre les deux) | 9 | 15 (9 d'un côté, 6 de l'autre : le mur du milieu garde toujours une moitié) |
| étage (plancher sur les murs) fermé par 4 murs | 5 | 9 |
| fondation triangle fermée | 1 | 1 (inchangé, voir 5) |

- [ ] Fondation fermée : poser une caisse dans chaque angle (viser le sol de l'angle, ou la face d'un mur dans l'angle) :
      elle se colle au mur qu'elle a dans le dos ou devant elle, et rentre un peu dans l'autre mur. Puis le milieu de
      chaque mur et le centre : 9 caisses.
- [ ] **Dehors**, tourner autour de la pièce : aucune caisse ne dépasse des murs (ni poignée, ni couvercle), même de près.
- [ ] Dedans, regarder les angles de près : pas de faces qui clignotent (scintillement) entre la caisse et le mur, ni
      au-dessus de la caisse ; le mur paraît plein derrière et à côté.
- [ ] Les 4 sens (R) marchent dans un angle ; au milieu d'un mur, une caisse de côté (ses 16 px vers le mur) se pose
      comme avant (case libre voisine).
- [ ] Ramasser / casser / faire exploser une caisse d'angle : les deux murs reprennent la case (pas de trou).
- [ ] Mur avec porte ou double porte dans l'angle : jamais de caisse dans cet angle (battant).
- [ ] Deux pièces : un angle contre le mur du milieu pris d'un côté, l'angle d'en face (autre pièce) refuse la rangée du
      mur (le mur garde sa moitié) ; la caisse se pose alors comme avant.
- [ ] Murs de chaque matériau (brindilles, bois, pierre, métal, blindé) : rien ne traverse, rien ne clignote.
- [ ] Caisse posée au milieu d'un mur seule (angles vides) : le mur derrière elle n'est plus dessiné trois fois (avant,
      les faces pouvaient scintiller) : il est toujours plein, sans trou, éclairé comme le reste du mur.

## 2. Coffres par rareté dans la rangée des murs
Modèles amincis à 13,5 px de profondeur (avant 14 à 14,75) : même dessin, voir les rendus avant / après.
- [ ] Malle de chantier et caisse d'armes (2 de large) : posées dos au mur, leurs deux cases dans la rangée du mur (l'une
      peut être l'angle). Viser le sol au pied du mur ou la face du mur ; le fantôme bleu montre les deux moitiés au bon
      endroit.
- [ ] Casier et coffre-fort (2 de haut) : posés au pied d'un mur plein ou d'un demi-mur, dans la rangée du mur.
- [ ] Ouvrir / fermer chaque coffre dans la rangée : le couvercle ou la porte s'ouvre au bon endroit (pas décalé du
      coffre) ; le contenu est gardé.
- [ ] Dehors : rien ne dépasse ; dedans : rien ne clignote.
- [ ] Ramasser, casser, faire exploser un coffre dans la rangée : ses deux cases rendues au mur, pas de trou.
- [ ] Coffres déjà posés avant la mise à jour : ils restent à leur place (un peu moins profonds à l'œil).
- [ ] Limite de coffres par base et la table d'armurier (une par base) : inchangées.

## 3. Textes
- [ ] Tutoriel des bases, étape « ton home » : « /home t'y ramène (une fois toutes les 8 min, moins avec l'arbre du clan
      ou un grade) » (anglais et russe aussi).
- [ ] Casino, Tablette du Directeur, page Probabilités : en bas, « Toutes les machines : /casino rtp » ; `/casino rtp`
      pendant le calcul : renvoie à la page Probabilités (plus de /casino roll).
- [ ] Aucun texte ne cite plus /home poser, /clashwar banc, /casino roll.

## 4. Fondations triangles
Rien de changé (comme dans Rust : un carré posé contre un côté en biais est tourné). Phrase ajoutée dans `docs/GUIDE.md`
(section 18).
