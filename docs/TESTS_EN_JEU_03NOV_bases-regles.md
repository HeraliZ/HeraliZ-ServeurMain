# Essais en jeu — chantier « bases-regles » (retours du propriétaire du 02/11 au soir)

Bases (CRac) **1.31.0-beta**, puis **1.31.1-beta** (suite du 03/11 : home seulement sur une fondation). Puis **1.33.0-beta**
(nouveau retour du 03/11 : carrés tournés contre les triangles, section en fin de fiche). Rien n'a été testé en jeu ni fabriqué : Gradle est bloqué sur cette machine
(maven.minecraftforge.net inaccessible). Tous les fichiers Java touchés passent la lecture de javac (syntaxe seulement,
sans Minecraft) ; la pente de l'usure (`PenteUsure`, Java pur) a été compilée et essayée hors du jeu (13 h pour une pièce
intacte, 7 h jusqu'à 30 %). La compilation complète et `verif_split` se feront au contrôle de branche de GitHub.
`python outils/verifier_langues.py` : 0 erreur, 0 avertissement (avec le nouveau contrôle des clés citées dans le code).

**Joueurs ET serveur doivent avoir les nouveaux jars** (textes, interface de construction). Le canal `crac:main` ne change
pas (protocole 31). Fichiers de réglages livrés : `crac-server.toml` (section `[decay]`), `crac-bases.toml` (commentaires),
`heraliz_noyau-server.toml` (`sethome` permise sur la carte).

## Ce qui change

| Point | Quoi | Pour le joueur |
|---|---|---|
| 1 | Plus de home créé tout seul à la pose de l'armoire ; `/sethome [nom]` pose le home de la base | message à la pose de l'armoire ; `/home` et la réapparition à la base vont au home posé |
| 2 | Barricade en bois : 18 planches, 18 clous, 3 cordes (x 3) | plus chère à la Table d'armurier |
| 3 | Démolition au marteau refusée si elle coupe une partie de la base de l'armoire | on démolit depuis les bouts |
| 4, 5 | Usure matériau par matériau, 10 %/h puis 5 %/h sous 30 %, dès que le stock est vide | une base sans entretien tombe en 13 h environ |
| 6 | Refus de construction dits une seule fois, dans l'interface de construction ; bon message pour une partie coupée de sa base | plus de texte en double au-dessus de la barre d'objets |
| 7 | Plus de clé de CRac affichée brute (« crac.build.need… ») ; contrôle automatique | messages lisibles |

### Home de la base (point 1)
- Nom de la commande à un seul endroit : `Homes.COMMANDE_POSER` (`modules/bases/src/serveur/.../bases/Homes.java`) ; les
  textes le reçoivent en `%s`. Changer le nom = cette ligne + `commandes_permises` de `heraliz_noyau-server.toml`.
- Règles : seulement dans la zone de l'armoire de la base de son clan (carte), droit de clan « Gérer la base » (le chef
  l'a ; le staff aussi), **debout sur une fondation de la base** (1.31.1, choix du propriétaire : jamais sur le sol, un
  plancher, un toit ou un autre bloc ; revu au départ de /home et de la réapparition : fondation démolie = refus), aucun bloc ni liquide dans le corps, jamais au-dessus du vide ; hors combat,
  à terre, match et tutoriel. Un seul home par base : le nouveau remplace l'ancien. Sans nom : le nom du clan. Les autres
  membres connectés sont prévenus. Le home est gardé avec le claim (`crac_claims.dat`) : armoire cassée ou déplacée au
  marteau = plus de home (il faut le reposer).
- Bases posées AVANT cette version : elles gardent leur home créé tout seul (devant l'armoire, comme avant) tant que
  personne ne fait `/sethome`. Bases posées APRÈS : pas de home tant qu'on ne l'a pas posé ; `/home` et l'écran de mort
  le disent.
- `/home` vers un home bouché (coffre posé dessus, mur) ou plus sûr : refusé, jamais une autre place (qui pourrait être
  dehors).

### Usure (points 4 et 5) — réglages de la section `[decay]` de `crac-server.toml` (relus quand le fichier change)
- `delayHours = 0.0` : délai avant l'usure d'un matériau sans entretien (choix du propriétaire : aucun). Avec un délai, il
  se compte quand le serveur tourne et que la base est chargée, comme l'entretien, et revient heure par heure une fois
  l'entretien payé (ajouter 1 planche par jour ne suffit pas à l'éviter).
- `highPercentPerHour = 10.0`, `thresholdPercent = 30.0`, `lowPercentPerHour = 5.0` : la pente.
- Brindilles : pas d'entretien, donc pas d'usure tant que la base a son armoire (comme avant).
- Pièces coupées de l'armoire, base dont l'armoire a disparu, objets posés d'une base qui s'use : même pente, sans délai.
  Fondations qu'aucun clan n'a jamais tenues (posées dans la nature) : usure rapide d'avant (`hpPerMinute`, 10 PV/min).
- Comme avant : une base dont les tronçons ne sont pas chargés (personne autour) ne paie pas d'entretien et ne s'use pas ;
  une pièce posée ou améliorée depuis moins de 30 min ne s'use pas (`graceMinutes`).

## Essais à faire (serveur d'essai, puis Ptero)

1. **Pose d'une armoire** (nouvelle base, sur la carte) : après « Claim créé… », un message doré dit de taper
   `/sethome <nom>`. `/home` du spawn : « Ta base n'a pas encore de home… ». Écran de mort : carte « À la base » fermée,
   « Ta base n'a pas encore de home. »
2. **/sethome** dans la base, debout sur une fondation : « Home « … » posé ici » ; un autre membre connecté reçoit le
   message gris. Du spawn, `/home` (attendre les 3 s) : arrivée exactement à cet endroit, regard dans le même sens,
   « Bienvenue chez toi : te voici au home « … » de ta base. » Mourir et choisir « À la base » : même endroit.
3. **/sethome refusé** (1.31.1) : debout sur le sol de la zone, sur un plancher, un toit, un escalier, une caisse ou un
   bloc posé : « Le home se pose seulement debout sur une fondation de ta base… » ; sur une fondation d'un bout de base
   coupé de l'armoire : refusé aussi. Ailleurs : hors de la zone de l'armoire ; sur la carte hors de toute base ; en sautant / en l'air ; dans
   l'eau ; sous un plafond trop bas ; par un membre sans le droit « Gérer la base » ; en combat ; pendant le tutoriel.
   Chaque fois un message rouge clair, rien n'est changé.
4. **Home bouché** : poser une caisse à l'endroit du home, `/home` : « Le home « … » de ta base est bouché… », pas de
   téléportation, délai pas compté. Retirer la caisse : `/home` marche. Démolir la fondation sous le home : `/home` refusé
   (« … ou sa fondation a disparu »).
5. **Base d'avant** (posée avant la mise à jour) : `/home` va devant l'armoire comme avant ; après un `/sethome`, va au
   nouveau home.
6. **/sethome sur la carte** : la commande n'est pas refusée par le noyau (« seulement au spawn »).
7. **Barricade** : à la Table d'armurier, onglet Divers : 18 planches, 18 clous, 3 cordes.
8. **Démolition** (dans les minutes qui suivent la pose) : ligne de 3 fondations depuis celle de l'armoire : démolir
   celle du milieu = refusé, message au-dessus du viseur (« Démolition impossible : une partie de ta base serait coupée de
   l'armoire… ») ; démolir celle du bout = permis, puis celle du milieu = permis. Murs posés sur la fondation du bout :
   elle se démolit (ils tombent avec). Fondation sous l'armoire avec d'autres fondations : refusé (message de l'armoire).
   Deux groupes de fondations reliés par un plancher sur des murs : démolir la fondation du milieu = permis (le plancher
   les relie encore). Créatif : tout permis.
9. **Partie coupée** : faire sauter au C4 la fondation du milieu (Purge) : la partie coupée garde ses PV puis s'use
   (10 %/h, `/cracbuild` ou marteau pour voir les PV) ; viser cette partie avec le plan : « Cette partie n'est plus
   reliée au bâtiment de ton armoire… » (plus « Dans le claim d'un autre groupe »). Une nouvelle fondation hors de la
   base dans le claim : toujours « Impossible de commencer un nouveau bâtiment dans un claim. Agrandis plutôt ta base. »
10. **Usure par matériau** : base en bois + pierre, armoire avec des planches mais sans pierres taillées : message rouge
    « Ta base s'use… » ; les pièces en pierre perdent environ 10 % par heure (marteau : PV), les pièces en bois non, et
    se réparent si abîmées ; remettre des pierres taillées : « L'entretien de ta base est de nouveau payé… », l'usure
    s'arrête. Écran de l'armoire et écran 3D : « DÉGRADATION EN COURS » / « DÉPOP IMMINENT » tant qu'une ressource manque.
11. **Durée** (long, à laisser tourner avec un joueur près de la base) : un mur en bois intact sans entretien tombe vers
    7 h à 30 % de ses PV, détruit vers 13 h. Les coffres de la base s'usent à la même pente.
12. **Messages de construction** : plan en main, viser un endroit refusé et cliquer : le refus n'apparaît que dans la
    carte sous le viseur, plus au-dessus de la barre d'objets. Refus que seul le serveur voit (ex. pièce trop instable,
    claims pas encore reçus) : dans la carte, 2 s. Marteau : refus d'amélioration (matériaux) et de démolition au-dessus du viseur, sur plusieurs
    lignes s'il est long. Caisse en main : refus sous le viseur. Aucun autre message de la barre d'action ne disparaît
    (ex. sans outil en main).
13. **Messages bruts** : plan en main sans brindilles : « Pas assez de matériaux, il faut 16x Brindilles » dans la carte
    (plus « crac.build.need… ») ; jeu en anglais, marteau sur une pièce déjà en métal : « Already Sheet Metal. ».

## Pas fait, ou pour un autre chantier
- **Textes du tutoriel** (chantier « bases-objets ») qui disent encore que le home est créé tout seul devant l'armoire :
  `heraliz_quetes.tuto.etape.10`, `.radio.10`, `.aide.10`, `.radio.fin3` (module pnj, écrits par
  `modules/pnj/outils/langues.py`) et `crac.tuto.etape6` (CRac, tutoriel sans Varga). À réécrire avec `/sethome <nom>`.
- `heraliz_grades.perm.homes.aide` (éditeur des grades, boutique) : « Plus utilisé : /home va devant l'armoire… ».
- `NoyauConfig.COMMANDES_CARTE_DEFAUT` (noyau) ne contient pas `sethome` : seul le fichier livré
  `heraliz_noyau-server.toml` l'a (c'est lui qui compte sur le Ptero).
- `python outils/commandes.py` ne voit pas `/sethome` (nom dans une constante) : `docs/COMMANDES.md` est à jour à la main.
- Un joueur ne voit pas à l'avance, dans la roue du marteau, qu'une démolition couperait sa base : il l'apprend au clic.
- `modules/bases/outils/apercu_mort.py` ne se lance plus (polices déplacées dans le noyau le 02/11) : écran de mort non
  revu en image ; les nouveaux textes sont plus courts ou de même longueur que les anciens.

## Carrés contre les côtés en biais des triangles (nouveau retour du 03/11, Bases 1.33.0-beta)

**Ce qui change** : la règle du 31/10 « une pièce carrée se pose toujours droite » est retirée (serveur et fantôme du
plan, texte `crac.rule.square_off_grid` retiré). Comme dans Rust, un carré (fondation, plancher, toit, escalier, rampe)
se pose contre chacun des 3 côtés d'une fondation triangle, tourné comme ce côté (30° ou 60°). Les caisses, coffres et
objets posés (blocs de Minecraft, toujours droits) tiennent sur une case dont le sol couvre la moitié au moins avec son
centre (avant : la case entière) : un carré tourné porte 8 caisses au lieu de 2, une fondation triangle 4 au lieu de 1.
L'armoire demande toujours une case entièrement couverte.

**Pourquoi le carré tourné « posait problème »** (relu dans le code, rien à réécrire) :
- sa collision, sa visée (marteau, plan) et son fantôme sont déjà ceux d'un vrai carré tourné : polygone exact pour le
  dessin et le fantôme, collision tramée au pixel par bloc (`ShapeRaster`, gardée en cache par orientation dans
  `PieceShapes` : rien par tick). Avec F3+B, des marches d'un pixel le long des bords en biais : Minecraft ne connaît que
  des boîtes droites ;
- murs, fenêtres, portes, planchers et toits posés dessus suivent déjà son orientation (même grille à 30° que les côtés
  des triangles) ; connexité au bâtiment de l'armoire, PV, usure et démolition ne dépendent pas de l'orientation ;
- le vrai blocage était la règle des objets posés : seules les cases entièrement couvertes portaient une caisse (2 sur
  un carré tourné), d'où « les caisses de travers sur 2 cases ». C'est cette règle qui change.

**Rendus avant / après** (à valider ; dessinés hors du jeu avec la géométrie de CRac) :
`/tmp/claude-0/-home-user-HeraliZ-ServeurMain/17fb6a25-fa62-5289-ae73-5657d6f9f60f/scratchpad/rendus_03nov/bases-regles/`
`1_avant_plan.png`, `2_apres_plan.png` (vue de dessus : carré contre chaque côté, collision au pixel, cases qui portent une
caisse), `3_avant_fantome.png`, `4_apres_fantome.png` (fantôme de pose et carte du verdict), `5_petite_base.png`.

**Essais à faire**
14. Fondation triangle : poser une fondation carrée contre chacun des 3 côtés : fantôme bleu, « Prêt à construire »,
    pose acceptée ; les deux carrés en biais sont tournés comme leur côté. Même chose avec un plancher posé sur des murs
    en biais.
15. Marcher, sauter et courir sur et autour d'un carré tourné : aucun accroc (F3+B : petites marches d'un pixel le long
    des bords en biais, normal). Viser au marteau : la carte de la pièce s'affiche partout sur le carré.
16. Murs, fenêtre, porte sur les bords d'un carré tourné : ils suivent le bord ; plancher et toit dessus.
17. Caisses sur un carré tourné : 8 cases acceptent une caisse ; sur une case coupée à moins de la moitié : « Moins de la
    moitié de cette case est sur une fondation ou un plancher (bord en biais) : pose-le plus vers l'intérieur. » Une
    caisse de bord dépasse un peu du carré. Fondation triangle seule : 4 caisses. Coffres par rareté : mêmes règles.
18. Démolir ou faire sauter le carré tourné : les caisses dessus tombent avec lui (contenu au sol) ; démolition depuis les
    bouts (point 3) respectée ; un carré tourné coupé de l'armoire s'use (points 4 et 5).
19. Bases existantes : rien ne bouge ; une caisse déjà posée reste.

**Limites connues** : les caisses, coffres, tables et l'armoire sont des blocs de Minecraft : ils restent droits sur un
carré tourné (ils ne peuvent pas tourner de 30° ; il faudrait des modèles et des collisions à part, que le chantier des
coffres pourrait étudier). Une caisse de bord dépasse un peu du sol. Une caisse ne se pose pas dans la rangée d'un mur
en biais (seulement d'un mur droit, comme avant).

