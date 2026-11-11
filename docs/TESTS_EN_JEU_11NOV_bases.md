# Essais en jeu — chantier « bases » (retours du propriétaire du 11/11)

Bases (CRac) **1.37.0-beta**. Rien n'a été essayé en jeu par Claude, ni fabriqué ici : Gradle est bloqué sur la machine de
travail (maven.minecraftforge.net injoignable). Chaque fichier Java touché passe la lecture de javac (syntaxe) ; la règle
de la zone (`Claim.tientPiece`) a été compilée et essayée hors du jeu avec la vraie géométrie de CRac (`PieceKey`,
`PieceSolids`) : 30 cas, tous bons. La compilation complète (et `verif_split`) se fera au contrôle de branche de GitHub.
`python outils/verifier_langues.py` : 0 erreur, 0 avertissement.

**Joueurs ET serveur doivent avoir les nouveaux jars** : canal `crac:main` en protocole **32** (un ancien jar joueur est
refusé à la connexion). Fichier de réglages livré : `crac-server.toml` (nouvelle clé `placables.forbiddenBlocks`).

## Ce qui change

| Point | Quoi | Pour le joueur |
|---|---|---|
| 1 | Coffres et rangements de Minecraft (coffre, coffre piégé, tonneau, boîtes de Shulker, entonnoir, distributeur, dropper) : plus posés nulle part, créatif excepté | « Les coffres et rangements de Minecraft ne se posent plus : utilise les coffres de base (caisse en métal, malle, casier…). » ; ceux déjà posés restent |
| 2 | Plus de touche R (ni Maj + R) pour tourner les objets de base ; plus de ligne « R : tourner l'objet » | caisse, coffres et armoire posés face au joueur, selon le sens où il regarde |
| 3 | Objets de base seulement sur une fondation ou un plancher de la base de son clan (ou sur un objet qui y repose) | « Cet objet se pose seulement sur une fondation ou un plancher de la base de ton clan. » dans la nature, chez un autre clan, sans clan, sur le terrain de sa zone |
| 4 | Mur, demi-mur, porte, fenêtre posés pile sur la ligne verte de la zone : acceptés | on construit jusqu'au bord dessiné de la zone |

### Point 1 — réglage
- Liste dans `serveur/defaultconfigs/crac-server.toml`, section `[placables]`, clé `forbiddenBlocks` (identifiants de blocs
  ou `#étiquettes`). Relue quand le fichier change (et renvoyée aux joueurs) : pas de redémarrage.
- Les coffres de nos mods (caisse en métal, malle de chantier, casier métallique, caisse d'armes renforcée, coffre-fort)
  ne sont pas dans la liste.
- Un coffre de Minecraft déjà posé reste un objet posé comme avant : PV, raids, ramassage (accroupi + clic droit). Une
  fois ramassé, il ne se repose plus.

### Point 4 — vraie cause
La zone dessinée (ligne verte, plan en main) et la zone vérifiée étaient déjà les mêmes (blocs à 20 / 23 / 26 / 29 au plus
de l'armoire). Mais une pièce n'était acceptée que si **tous les blocs qu'elle touche** étaient dans la zone. Un mur fait
4 px d'épaisseur centrés sur son bord (une porte ouverte va jusqu'à 16 px de lui) : posé sur le dernier bord, pile sur la
ligne, sa moitié extérieure prenait la rangée de blocs juste derrière la ligne → « Hors de la zone du Claim Block ».
Maintenant, un mur est jugé sur le bord où il se pose (ses deux bouts dans le carré dessiné, ligne comprise) ; les autres
pièces gardent la règle des blocs ; la hauteur ne change pas.

## Essais à faire (serveur d'essai, puis Ptero)

**Point 1 — coffres de Minecraft**
1. Coffre de Minecraft en main, dans sa base, en visant une fondation : fantôme rouge et la phrase sous le viseur ; clic :
   rien n'est posé. Même chose avec un coffre piégé, un tonneau, une boîte de Shulker, un entonnoir, un distributeur,
   un dropper.
2. Dans la nature : pas de fantôme (comme avant hors d'une base) ; au clic, rien n'est posé et la phrase s'affiche sous
   le viseur.
3. Caisse en métal, malle, casier, caisse d'armes, coffre-fort : se posent comme avant dans sa base.
4. Coffre de Minecraft posé AVANT la mise à jour dans une base : toujours là, s'ouvre, se ramasse ; une charge le casse
   comme avant pendant la Purge.
5. Staff en créatif : un coffre de Minecraft se pose partout (décor de la carte).
6. Retirer `"minecraft:barrel"` de `forbiddenBlocks` dans le `crac-server.toml` du monde : quelques secondes après, le
   tonneau se repose dans la base (sans redémarrer). Remettre la liste.

**Point 2 — plus de touche R pour les objets**
7. Caisse en métal, coffre ou armoire en main : plus de ligne « R : tourner l'objet » sous le viseur ; R et Maj + R ne
   tournent plus le fantôme.
8. Poser en regardant vers le nord, le sud, l'est, l'ouest : la face avant de l'objet est tournée vers le joueur, comme le
   fantôme juste avant le clic. Contre un mur, dans un angle, près d'une fondation triangle : pareil (sens du regard).
9. Plan de construction en main : R tourne toujours la pièce (porte : sens d'ouverture et gonds ; escalier…), Maj + R en
   arrière. Arme en main, appuyer plusieurs fois sur R (recharge), puis prendre le plan : la pièce n'a pas tourné.

**Point 3 — objets de base seulement dans sa base**
10. Dans la nature : caisse en métal, coffre, table d'armurier (ou four, établi) : au clic, refus avec la phrase sous le
    viseur, rien n'est posé.
11. Dans la base d'un autre clan : fantôme rouge et la même phrase ; rien n'est posé.
12. Sans clan, partout : refusé de même.
13. Dans sa base, sur le terrain de la zone (herbe entre les fondations et la ligne verte) : fantôme rouge et la même
    phrase.
14. Dans sa base, comme avant : sur une fondation, un plancher d'étage, dans la rangée d'un mur, contre un mur (table
    d'armurier), descendu sur un plancher à mi-bloc (coffres, caisse).
15. Caisse sur une caisse posée sur une fondation : permise. Coffre de 2 de large à cheval sur le bord d'une fondation (une
    case au-dessus du terrain) : « Cet objet dépasserait du bord du sol… ».
16. Cœur de clan : sur une fondation de sa base, posé ; sur le terrain de sa zone, refusé avec la phrase (nouveau) ; hors
    de sa base, son message à lui (« Le cœur de clan se pose dans la base de ton clan… »).
17. Armoire (Claim Block) : se pose comme avant sur une fondation d'un bâtiment sans armoire. Échelle de raid : se pose
    comme avant contre sa base et celle des autres.
18. Membre du clan sans le droit « Construire » : refus « Ce claim n'est pas à ton groupe. », comme avant.
19. Objets posés avant la mise à jour dans la nature ou sur le terrain d'une base : ils restent (rien ne tombe).
20. Tutoriel des bases : la caisse sur la case verte se pose comme avant.

**Point 4 — jusqu'au bord de la zone**
21. Plan en main, base dont la ligne verte passe pile sur le bord d'une fondation (cas de la capture du propriétaire) : mur,
    demi-mur, fenêtre, porte (s'ouvrant dedans, puis dehors) sur ce bord : fantôme bleu, « Prêt à construire », pose
    acceptée. Sur chacun des 4 côtés et dans un coin.
22. Toujours refusé (« Hors de la zone du Claim Block ») : une fondation qui passerait la ligne ; un mur posé une fondation
    au-delà de la ligne.
23. Ligne verte à 1 ou 2 blocs du bord de la dernière fondation : rien ne change (les murs y passaient déjà ; une fondation,
    qui fait 3 blocs, passerait la ligne).
24. Base finie par des murs sur la ligne : casser l'armoire puis la reposer au même endroit (ou la déplacer au marteau dans
    les 15 min) : acceptée au même palier, sans « Ce bâtiment est trop grand… » ni palier plus cher demandé.
25. Purge : une charge sur la face extérieure d'un mur sur la ligne l'abîme comme ailleurs ; hors Purge, rien (protégé
    comme le reste de la base).
26. Échelle de raid contre la face extérieure d'un mur ou d'une fondation sur la ligne : mêmes règles qu'ailleurs sur la
    base (dans sa propre base en raid block : refusée).

## Limites connues, pas fait
- Hors d'une base, pas de fantôme pour un objet de base (comme avant) : le refus se lit au clic. Comme pour les autres
  refus de pose, le jeu du joueur peut montrer l'objet un court instant avant que le serveur le retire.
- La touche R garde son rôle avec le plan de construction (tourner une pièce) : seuls les objets posés ne tournent plus.
- Les objets de Minecraft de la liste des placables (four, établi, enclume, feu de camp…) suivent la règle du point 3 : plus
  posés dans la nature.
