# Essais en jeu du 03/11 : chantier « monde-zones » (retours du propriétaire après son premier passage en jeu)

Rien de tout cela n'a été testé en jeu ni compilé ici : Gradle ne trouve pas Forge sur la machine de travail
(maven.minecraftforge.net bloqué). Seule la syntaxe Java a été contrôlée (javac sans Minecraft, erreurs des classes de
Minecraft et de Forge absentes écartées) ; les signatures de Minecraft et de Forge utilisées ont été relues dans leurs
sources 1.20.1 (Zombie, Mob, Entity, PlayLevelSoundEvent, patchs de Mohist pour l'envoi des sons). La vraie compilation
sera faite par le contrôle de branche de GitHub. `outils/verifier_langues.py` : 0 erreur (aucun texte nouveau).
`outils/verifier.py` ne peut pas tourner ici sans les jars fabriqués.

Jars à mettre à jour ensemble (serveur ET joueurs) : **Monde 1.0.13**, **Noyau 1.0.16**, **Bases (CRac) 1.31.1-beta**,
**Clans 3.12.2**. Réglage du dépôt changé : `serveur/defaultconfigs/heraliz_noyau-server.toml` (section `[pas]`).
Nouveau réglage chez le joueur : `config/heraliz_noyau-client.toml`, section `[zones]`, `a_travers = 10`.

**Suite du 03/11 (réponses du propriétaire) : à refaire en entier.** Nouveau dessin des zones branché (sections 1 et
1 bis), pas des joueurs moins forts que la première version, pas des zombies revenus au volume normal (section 3).
Rendus du dessin branché : `rendus_03nov/monde-zones/5_melange_vue_d_ensemble.png` et `6_melange_a_hauteur_des_yeux.png`.

## 1 bis. Nouveau dessin des zones (mélange choisi le 03/11, branché)

Sol teinté léger, au bord une bande nette, un dégradé au sol vers l'intérieur et un mur bas très léger (moins d'un
bloc). Zones interdites : sol rouge sur les cases interdites. Bases : bord seulement (vert : la sienne, rouge : les
autres). Extraction : sol léger sur les cases où l'on s'extrait, couleur rouge → orange → vert.

- [ ] Plan en main près d'une zone interdite, d'une base, et dans une zone d'extraction : le dessin ressemble aux rendus
      5 et 6 ; rien de haut ni d'opaque à l'intérieur d'une zone (on voit bien un joueur qui se bat dedans).
- [ ] À moins de 10 blocs du bord : le bord se voit en transparence derrière un arbre, une caisse, un mur, une colline.
      Plus loin : on ne le voit plus à travers (seulement là où il est visible). À 50 blocs derrière un bâtiment : rien.
- [ ] `config/heraliz_noyau-client.toml`, `a_travers = 0` : plus jamais à travers ; `20` : de plus loin.
- [ ] Extraction : la couleur suit l'avancement (rouge, orange, vert), la zone apparaît et disparaît en fondu ; une zone
      fixe d'admin fermée ne montre rien. Le PC caché derrière un mur : la zone reste dessinée.
- [ ] Bases : sa propre base verte, celle d'un autre rouge ; pas de teinte sur les toits ni les sols de la base. Après
      avoir posé ou cassé des blocs au bord, le bord suit en 10 s au plus.
- [ ] Zones interdites : en marchant plan en main, la zone suit sans trou (refaite tous les 16 blocs) ; la case visée,
      si elle est interdite, est plus rouge.
- [ ] Images par seconde, plan en main au milieu d'une zone interdite et d'une forêt, puis dans une extraction : pas de
      baisse par rapport à avant (F3 ou `/diag client`) ; pas d'à-coup en marchant (refait tous les 16 blocs).
- [ ] Ranger le plan 30 s puis le reprendre : la zone revient tout de suite (refaite une fois).
- [ ] Avec les shaders / Embeddium des joueurs : pas de zone noire, pas de clignotement.
- [ ] Drapeaux de territoire (Clans 3.12.2, choix du propriétaire) : le cercle de capture a le même dessin (sol léger,
      bord net, mur bas) à la place du mur lumineux. Couleur : blanc libre, vert tenu par son clan, rouge par un autre,
      orange pendant une capture ou une contestation ; elle change aussitôt avec l'état. Visible jusqu'à 48 blocs du
      cercle, à travers le décor seulement de près ; mât caché derrière un mur : le cercle reste dessiné.
- [ ] Changer le rayon de capture (`heralisz_clans-common.toml`) : le cercle suit ; poser ou casser des blocs au bord :
      le cercle suit en 10 s au plus.

## 1. Zones plan en main et cercle d'extraction posés sur le vrai sol (point 1, défauts corrigés)

Même sol réel pour le nouveau dessin (section 1 bis).

- [ ] Plan de construction en main, près d'une base dont le bord passe sous des arbres : le bord reste
      au ras du sol sous les arbres (avant : ils montaient sur les feuilles et redescendaient). Pareil sur un bord qui
      passe sur une caisse, un conteneur de loot, un lampadaire, une barrière : le bord reste au sol.
- [ ] Zones interdites (près d'une ville, des rails, du nord enneigé), plan en main : la teinte rouge des cases et leur
      bord sont au sol, jamais en l'air sur la cime des arbres ou sur les props.
- [ ] Bord sur un chemin de terre, une dalle, de l'eau, de la glace, de la neige : le bord est posé dessus (pas dessous).
- [ ] Bord le long d'un mur de bâtiment ou sur un toit : il reste sur le toit ou le haut du mur (c'est du terrain).
- [ ] Images par seconde avec le plan en main au milieu d'une forêt (F3 ou `/diag client`) : pas de baisse par rapport
      au 02/11 (la hauteur du sol est gardée 10 ticks, jamais recalculée à chaque image).
- [ ] Fusée d'extraction posée au milieu de caisses, de barils, d'un lampadaire ou près d'un arbre : le cercle reste rond,
      sans encoche vers le centre devant chaque objet ; le bord ne saute plus d'un bloc sur une caisse.
- [ ] Fusée posée près d'un mur de bâtiment : le cercle s'arrête toujours au mur dans cette direction.
- [ ] Rien ne change pour l'extraction elle-même : on s'extrait aux mêmes endroits qu'avant (dans le cercle, pas sous un
      toit, pas sur une caisse hors d'atteinte), même chrono.
- [ ] Zone fixe d'admin (`/extraction create`) ouverte : même cercle propre.
- [ ] Drapeau de territoire dont le cercle de capture passe sous des arbres ou sur des props : le mur du cercle reste au
      sol (avant : il montait sur les feuilles et les caisses).

## 2. Aucun zombie équipé (point 2)

- [ ] Parcourir une ville peuplée une bonne demi-heure (des centaines de zombies) : aucun zombie avec une épée, une pelle,
      une armure ou une citrouille.
- [ ] `/summon heralisz_utils:zombie_survie` une vingtaine de fois : aucun équipé, aucun poulet chevauché.
- [ ] Vagues d'un drapeau (capture d'une zone) : zombies sans équipement.
- [ ] Un zombie déjà sur la carte avant la mise à jour et qui avait une pelle ou une épée : au retour dans son coin (tronçon
      rechargé), il ne l'a plus.
- [ ] Les tenues ne changent pas : ville (5 tenues), hazmat, arctique, militaire, prisonnier, police (ce sont des textures).
- [ ] Le butin des zombies tombe comme avant (rien de plus, rien de moins).

## 3. Bruits de pas des autres joueurs et des zombies (point 3, valeurs du 03/11)

Réglages : section `[pas]` de `heraliz_noyau-server.toml` (relue seule quand le fichier change) : `portee_joueurs = 24`,
`volume_joueurs = 1.3`, `douceur = 1.2`, `volume_monstres = 1.0`, `actif = true` (false : comme Minecraft).
Volume d'un pas d'un autre joueur selon la distance (Minecraft entre parenthèses) : 4 blocs ≈ 1,5 fois (0,11), 8 blocs
≈ 1,9 fois (0,075), 16 blocs ≈ autant que Minecraft à 8, plus rien à 24 (Minecraft : plus rien à 16).

- [ ] À deux joueurs : l'un marche, court, saute ; l'autre écoute à 4, 8, 16, 20 et 26 blocs. Les pas s'entendent un peu
      mieux de près, encore un peu à 16-20 blocs, plus du tout au-delà de 24. Trop ou pas assez : ajuster
      `volume_joueurs` (1,3) et `portee_joueurs` (24).
- [ ] La direction est juste (casque) : on sait de quel côté vient le joueur, de près comme de loin, même en tournant la
      tête pendant qu'il marche.
- [ ] Ses propres pas ne changent pas (même volume qu'avant, jamais en double, pas d'écho).
- [ ] Un joueur accroupi qui marche reste silencieux, comme avant.
- [ ] Staff en vanish (panneau du staff) qui marche à côté d'un joueur : le joueur n'entend rien.
- [ ] Pas sur différents sols (herbe, pierre, bois, gravier, métal, neige, laine) : chaque son est le bon.
- [ ] Zombies : leurs pas au même volume qu'avant le 03/11.
- [ ] Changer `portee_joueurs`, `volume_joueurs` ou `douceur` dans le fichier du serveur : pris en compte sans redémarrer.
- [ ] Lenteurs : `/diag profil` pendant un combat à plusieurs : rien de nouveau dans le haut du profil.
