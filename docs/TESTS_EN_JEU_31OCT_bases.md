# À tester en jeu — bases (CRac 1.29.0-beta, Quêtes 1.15.1, 31/10)

**Rien de tout cela n'a été testé en jeu ni compilé ici** (Gradle ne joint pas le dépôt de Forge depuis la machine de
travail) : la fabrication complète est faite par le contrôle de branche de GitHub. Vérifié hors du jeu : syntaxe Java
des fichiers changés (`javac`, phase de lecture), `verifier_langues` (0 erreur), calcul des places refait à part (voir
« Calculs » plus bas). Un essai automatique en jeu est écrit (`IntegrityGameTests.aCrateTakesTheInnerHalfOfAStraightWall`,
lancé avec les autres GameTests de CRac) mais n'a pas été lancé.

**Canal CRac version 29** (nouvel état de la caisse en métal) : joueurs et serveur doivent avoir les jars de la **même**
fabrication de CRac. Quêtes : nouveau jar client (un texte de Varga).

## 1. Purge : plus de protection des nouvelles bases
- [ ] Poser une armoire (base neuve) pendant une Purge (`/purge forcer 30`), puis lancer un C4 sur sa base : le C4 fait
      ses dégâts (avant : « Cette base est trop récente »).
- [ ] Même chose avec une base posée quelques heures avant la Purge.
- [ ] Prise de cœur sur une base neuve pendant la Purge : elle démarre.
- [ ] Hors Purge, rien ne change : les explosifs ne font toujours rien aux bases des clans.
- [ ] `purge.json` du Ptero : `"protection_nouvelle_base_heures": 0`. Un nombre d'heures remet l'ancienne règle
      (`/purge recharger`).
- [ ] `/protection donner <joueur>` (staff) marche toujours (protection donnée à la main).

## 2. Messages avec une commande qui n'existe plus
- [ ] Poser une armoire sans être dans un clan : « Tu n'as pas de clan. Crée-le d'abord avec /clan creer (ou demande à un
      chef de clan de t'inviter). » (anglais et russe aussi).
- [ ] Fin du tutoriel des bases : Varga ne dit plus « /home poser », mais « ton home est devant son armoire : du spawn,
      /home t'y ramènera ».

## 3. Caisses en métal dans la rangée des murs (le plus important)
Avant : une caisse ne se posait que dans une case libre ; les murs prennent toute la rangée de cases du bord (4 px
centrés sur le bord, donc la case de chaque côté). Une caisse visée contre un mur était **dessinée** dans la case du mur,
mais **son bloc** gardait la case libre devant : case vide à l'œil mais prise, « bloc vide entre deux caisses », une
seule caisse au milieu d'une fondation fermée. Maintenant la caisse prend la case du mur elle-même : le mur lui laisse sa
moitié intérieure (comme pour une caisse posée avant le mur), et la reprend quand la caisse part.

Places pour des caisses en métal (calcul refait hors du jeu, mêmes nombres que le mod) :

| Configuration | Avant | Après |
|---|---|---|
| fondation seule, sans mur | 9 | 9 |
| fondation fermée par 4 murs | 1 | 5 |
| fondation, 3 murs (un côté ouvert) | 2 | 7 |
| deux fondations côte à côte, une pièce (6 murs) | 4 | 14 |
| deux fondations, deux pièces (mur entre les deux) | 2 | 9 |
| étage (plancher sur les murs) fermé par 4 murs | 1 | 5 |
| fondation triangle fermée | 0 | 1 |

Pourquoi pas 9 sur une fondation fermée : 3 blocs = 48 px, les murs en prennent 2 px de chaque côté, il reste 44 px. La
caisse fait 16 × 13 px : contre un mur elle tient seulement « face au mur » ou « dos au mur » (ses 13 px vers le mur),
jamais dans un angle (ses 16 px toucheraient le second mur). D'où le milieu + le milieu de chaque mur = 5.

À vérifier :
- [ ] Fondation fermée par 4 murs : poser 5 caisses (milieu, puis le pied de chaque mur, en regardant le mur). Aucune
      case « libre mais prise » ; le fantôme bleu est à l'endroit de la caisse posée.
- [ ] Viser le **sol au pied d'un mur**, la **face du mur** (à hauteur de caisse) ou le **bord de la case du milieu**
      contre le mur : la caisse apparaît contre ce mur, dans la rangée du mur, la case du milieu reste libre.
- [ ] Caisse tournée de côté avec R (ses 16 px vers le mur) ou visée dans un angle : comme avant (case libre voisine,
      poussée contre le mur).
- [ ] La caisse ne rentre pas dans le mur (jeu de 0,25 px, comme avant) ; le mur derrière elle reste plein à l'œil (sa
      moitié est dessinée par la case voisine).
- [ ] Ramasser la caisse (accroupi + clic droit, ou marteau), la casser, la faire exploser : le mur reprend sa case (pas
      de trou dans le mur).
- [ ] Casser le mur puis le reposer avec la caisse en place : il se pose (il laisse la case à la caisse).
- [ ] Mur entre deux pièces : une caisse d'un côté, puis viser le même mur de l'autre côté : la seconde ne prend pas la
      case du mur (le mur garde toujours une moitié) ; elle se pose comme avant.
- [ ] Porte, double porte : jamais de caisse dans leur rangée (battant).
- [ ] Étage (plancher posé sur les murs) : mêmes essais. Plancher posé sur des demi-murs (dessus à mi-bloc) : comme avant.
- [ ] Limite de caisses par base (14) toujours comptée ; coffre ouvert par un clic sur la caisse dans la rangée du mur.
- [ ] Raid : la caisse dans la rangée du mur prend ses dégâts normalement ; le mur aussi (C4 sur le mur).
- [ ] Tutoriel des bases : la caisse posée sur la case verte est acceptée (elle peut maintenant se mettre dans la case du
      mur à côté de l'armoire).
- [ ] Rien d'anormal avec les caisses déjà posées avant la mise à jour (elles gardent leur place).

## 4. Caisse posée derrière le joueur ou impossible à poser (survie)
- [ ] En survie, viser un point à plus de 4,5 blocs : plus de fantôme (avant : fantôme jusqu'à 5 blocs, mais le clic de
      Minecraft s'arrête à 4,5 : rien ne se posait, ou ailleurs).
- [ ] Coffre de rareté (malle, casier, caisse d'armes, coffre-fort) et armoire visés au pied d'un mur : ils vont dans la
      case libre **devant** la case visée (avant : la case libre la plus proche des yeux du joueur, souvent celle où il se
      tient : refusé, ou à côté / en arrière de lui). Ils ne vont pas dans la rangée du mur (trop profonds : 14 à 14,75 px
      pour 13,75 de place).

## 5. Fondations triangles (question au propriétaire, rien de changé)
Le code traite les trois côtés de la même façon (`Topology.across`). La différence vient de la forme : le triangle est
équilatéral (angles de 60°), donc un seul de ses côtés peut suivre la grille des blocs. Un carré collé à ce côté est droit ;
collé à un des deux autres, il est forcément tourné de 30° (ou 60°) : c'est la géométrie de Rust. Un tel carré n'a que 2
cases entièrement couvertes (5 avec le triangle) au lieu de 9, et les caisses (blocs de Minecraft, toujours droits) y sont
« en biais ». Aucun calcul de placement n'est faux ; aucune modification n'a été faite (voir les questions du compte rendu).

## Calculs (hors du jeu)
Script de reproduction (mêmes règles que le mod : cases touchées à plus de 0,05 px², dessus couvert à 100 %, murs de
4 px, caisse 16 × 13 px, jeu de 0,25 px) : tableau ci-dessus et angles des carrés contre un triangle (0°, 30°, 60°).
