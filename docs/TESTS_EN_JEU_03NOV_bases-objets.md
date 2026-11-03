# Essais en jeu — chantier « bases-objets » (retours du propriétaire du 03/11)

Bases (CRac) 1.32.0-beta (suite du 03/11 : coffres simples, cœur A, sortie validée), Clans 3.11.2, Quêtes 1.17.2. Rien de tout cela n'a été testé en jeu ni compilé ici (Forge
injoignable depuis la machine de travail : syntaxe seulement vérifiée ; la compilation complète est celle du contrôle de
branche de GitHub). Jars joueurs ET serveur de la même fabrication.

## 1. Coffre-fort sans scintillement (même dessin)

- [ ] Poser un coffre-fort, le regarder de près puis en reculant (2 à 30 blocs), en tournant autour : le cadran doré, le
      moyeu et la couronne du volant, le dessus et le dessous des deux charnières ne scintillent plus.
- [ ] L'ouvrir, le fermer plusieurs fois : le volant tourne, la porte s'ouvre ; porte ouverte, pas de scintillement sur
      le dos de la porte ni sur les charnières.
- [ ] Même contrôle (plus petit) : poignées sur les côtés de la caisse d'armes et de la malle de chantier ; dos de la
      porte du casier ouvert (charnières).
- [ ] Le dessin n'a pas changé (comparer avec un coffre posé avant la mise à jour) ; coffres déjà posés intacts.

## 2. Coffres simples (validés par le propriétaire le 03/11, installés)

- [ ] Chaque coffre (malle, casier, caisse d'armes, coffre-fort, caisse en métal) : plus aucun texte dessus ; ouverture
      animée (couvercle, porte ; coffre-fort : la poignée fait un quart de tour puis la porte s'ouvre) ; aucun scintillement.
- [ ] Coffres déjà posés sur le serveur : ils prennent le nouveau dessin, gardent leur contenu.
- [ ] Contre un mur et dans un angle (rangée des murs) : ils se posent comme avant ; rien ne dépasse dans le mur.

## 3. Cœur de clan

- [ ] Nouveau modèle (option A validée) : socle bas graphite, filet et cristal cyan fixes qui éclairent (même la nuit),
      points écrits sur la plaque devant ; rien ne tourne, plus d'hologramme ; les cœurs déjà posés prennent ce dessin.
- [ ] On ne passe pas à travers le socle ni la colonne ; viser et cliquer le cœur marche partout sur le modèle.
- [ ] En main et dans l'inventaire : l'objet du cœur s'affiche bien (taille, sens).
- [ ] Membre du clan, clic droit sur le cœur de son clan : l'écran du clan s'ouvre sur l'onglet Saison ; la ligne
      « Cœur du clan : N points · général #N » s'affiche aussi au-dessus de la barre.
- [ ] Joueur d'un autre clan : hors Purge, message « Le cœur ne se prend que pendant la Purge » ; pendant la Purge, la
      prise du cœur part comme avant.
- [ ] Le cœur reste incassable en survie ; /coeur deplacer marche comme avant.

## 4. Table d'armurier

- [ ] Table en main, viser le sol libre dans sa base : le fantôme montre la table exactement comme elle sera posée
      (même sens : tête à droite du joueur, devant tourné vers lui ; les deux cases encadrées) ; clic : elle est posée là.
      Essayer les 4 directions.
- [ ] Case de droite occupée : fantôme rouge (cadre), rien n'est posé.
- [ ] Viser la face d'un mur droit (bois, pierre, métal…) de sa fondation, face au mur : le fantôme montre la table
      collée au mur (dans la rangée du mur) ; clic : elle est posée collée, sans le bloc d'espace d'avant. Le mur paraît
      toujours plein (dehors aussi : pas de trou).
- [ ] Même chose en visant le sol au pied du mur (case du mur) ; dans un angle (table contre le mur, son côté contre
      l'autre mur) ; contre un mur intérieur (entre deux fondations), des deux côtés : la deuxième est refusée si la
      première prend déjà la case de l'autre côté.
- [ ] Refus (pose habituelle à la place) : mur avec porte, mur de biais, plancher à mi-bloc, objet poussé contre ce mur
      (armoire, caisse), case devant occupée, base d'un autre clan.
- [ ] Une seule table par base (2e refusée, message), même contre un mur.
- [ ] Reprendre la table (touche, marteau), ou la faire sauter pendant un raid : le mur reprend sa case (plus de trou).
- [ ] Se placer dans la case devant le mur et poser : refusé proprement (rien de pris au mur).
- [ ] Redémarrer le serveur : la table contre le mur est toujours là, le mur aussi.

## 5. Inventaire mis de côté pendant le tutoriel des bases

Préparer un joueur avec : inventaire plein (36 cases), armure, objet en main gauche, un sac porté (Curios) avec des objets
dedans, un objet à vie (couteau), un objet tenu à la souris en lançant le tutoriel si possible.

- [ ] Créer un clan (ou parler à Varga « Faire ma base ») : message « Ton inventaire est mis de côté… », inventaire,
      armure, main gauche et Curios vides, puis seulement le kit du tutoriel. Fichier
      `crac/tutoriel_inventaires/<uuid>.<n°>.depot.dat` présent sur le serveur.
- [ ] Finir le tutoriel : retour au spawn, kit repris, tout l'inventaire rendu À SA PLACE (même case, armure, main
      gauche, sac porté avec son contenu, case choisie) ; message « Ton inventaire t'a été rendu » ; fichier effacé.
- [ ] Ce qui avait été rangé dans la caisse de la cabane (hors kit) revient aussi ; si ça ne rentre pas : message
      « N objet(s) n'ont pas tenu… », fichier `.attente.dat` ; libérer une case : l'objet arrive dans la seconde. Rien au sol.
- [ ] Se déconnecter en plein tutoriel, se reconnecter : toujours dans le tutoriel, toujours le kit seul ; finir : tout
      est rendu une seule fois.
- [ ] Arrêter le serveur (ou le tuer) en plein tutoriel, redémarrer, se reconnecter : idem.
- [ ] Se déconnecter pendant le tour de caméra de la fin : à la connexion, inventaire rendu une seule fois.
- [ ] Mourir pendant le tutoriel (/kill par le staff) : rien de son inventaire ne tombe (il est de côté) ; il est renvoyé
      à sa cabane ; à la fin tout est rendu.
- [ ] /spawn, /home, /rtp, /tpa pendant le tutoriel : toujours refusés.
- [ ] Staff : /tp du joueur vers la carte pendant le tutoriel (décision du 03/11) : le tutoriel compte comme TERMINÉ
      (message de fin, Varga enchaîne comme après une cabane finie), kit repris, inventaire rendu, cabane d'essai effacée ;
      il ne le refait pas (TutorielApi : état 3).
- [ ] Même chose pour un joueur qui a reçu le kit au spawn et que le staff emmène ailleurs avant l'envoi.
- [ ] Monde du tutoriel absent ou tutoriel coupé (réglage) avec un joueur encore au kit ou en cours : validé dans la
      seconde, inventaire rendu, jamais bloqué.
- [ ] Staff : /tuto finir, /tuto rejouer, /tuto lancer (tutoriel en cours) : inventaire rendu à chaque fois, jamais deux
      fois ; /tuto lancer remet de côté au kit suivant.
- [ ] /wipe <joueur> pendant le tutoriel : le dépôt est effacé, seuls les objets à vie sont rendus (comme le reste du wipe).
- [ ] Jamais de double : après une fin, se déconnecter / reconnecter : rien n'est rendu une deuxième fois.
- [ ] Un joueur dont le tutoriel a commencé avant la mise à jour : à la fin, kit repris, il garde ce qu'il avait (rien de
      mis de côté, rien de rendu en double).
- [ ] Journal du serveur : lignes « [CRac] tutoriel : inventaire de X mis de côté / rendu » ; aucun « illisible ».

## Textes du tutoriel (home par /sethome, demande du pilote)

- [ ] Tutoriel avec Varga, dernière étape et fin : Varga explique de poser son home dans la vraie base avec
      `/sethome <nom>`, puis `/home` du spawn (fr, en, ru). Rien à taper dans le tutoriel.

## Suite : éclairage des coffres qui « clignote » (Bases 1.33.2-beta)

Cause : chaque coffre par rareté FERMÉ dessinait à chaque image son couvercle (malle, caisse d'armes) ou sa porte
(casier, coffre-fort, avec la poignée) une deuxième fois, par-dessus celui du modèle du bloc, avec un autre calcul de
lumière : les deux copies se départageaient au hasard (calcul de « fermé depuis un instant » qui débordait). Corrigé :
la pièce n'est dessinée à part qu'ouvert et pendant 4 ticks après une fermeture.

- [ ] Malle, casier, caisse d'armes, coffre-fort posés et fermés : de près, de loin, de jour, de nuit, près d'une torche,
      en tournant autour : plus aucun clignotement ni éclairage qui saute ; même éclairage que les blocs voisins.
- [ ] Ouvrir / fermer : l'animation marche ; à la fermeture, au plus un très bref instant (0,2 s) de double dessin.
- [ ] Caisse en métal (pas concernée par ce défaut, elle n'a pas de pièce dessinée à part) : rien ne clignote non plus.
- [ ] Coffres posés avant la mise à jour : mêmes contrôles.

## Échelle de raid (Bases 1.34.1-beta, validée par le propriétaire le 03/11, avec ses changements)

Objet et bloc `crac:echelle` (identifiant figé). **Une échelle = la hauteur d'un mur** (un étage de base, 3 blocs), posée
d'un coup ; piles de 9 ; une par recette à la Table d'armurier (onglet Divers : 9 planches, 9 clous, 2 cordes, le prix des
3 petites échelles d'avant). Modèle des bases (montants en planches, colliers d'acier) ; rendus :
`5_echelle_v2/echelle.png`. Aucune limite, aucune disparition avec le temps.

- [ ] Fabrication : onglet Divers, 1 échelle par recette ; Peu commune ; jamais dans le loot.
- [ ] Pose contre la FACE d'un mur droit (bois, pierre, métal, blindé), en visant n'importe où sur le mur : l'échelle va
      du pied au sommet de ce mur, collée à lui ; le mur paraît toujours plein des deux côtés. Dehors ; dedans seulement
      sans plancher au-dessus. Pareil dans la base d'un AUTRE clan (pas besoin d'être membre), pendant et hors Purge.
- [ ] Deux étages : viser l'échelle posée, la 2e se met au-dessus (même sens) contre le mur de l'étage suivant ; il en
      faut 2 pour 2 murs.
- [ ] Haut de l'échelle : on passe sur le toit / à l'étage en montant tout en haut (sans échelle de plus). Point à
      surveiller : un toit qui déborde au-dessus de l'échelle pourrait gêner le passage.
- [ ] Mur posé sur un demi-mur (à mi-hauteur) : l'échelle dépasse d'une demi-case en haut ; dire si ça va.
- [ ] Refus (« L'échelle se pose contre… ») : terrain hors base, flanc d'une fondation (plus prévu : l'échelle fait un
      mur de haut), mur en biais (pas possible proprement), mur avec porte, une des 3 cases déjà prise (coffre, autre
      échelle) ou tenue par un plancher, sol, plafond.
- [ ] Quelqu'un collé au mur à cet endroit (soi-même compris) : « Quelqu'un est dans la place de l'échelle… », rien n'est
      posé.
- [ ] Base d'un autre clan encore protégée (nouvelle base) : refusé ; sa propre base sous raid block : refusé.
- [ ] Grimper : comme une échelle de Minecraft (montée, descente, accroupi pour rester accroché).
- [ ] Sur l'échelle (accroché, pas au sol) : les armes ne tirent pas (pas d'animation de tir, pas de balle, ligne
      « Impossible de tirer sur une échelle. ») ; au pied de l'échelle, au sol : on tire. Sur l'échelle, permis : plan de
      construction, poser une autre échelle, se soigner, manger, coup de crosse.
- [ ] Anticheat : aucun signalement en grimpant, ni en tirant à vide sur l'échelle.
- [ ] Casse à la main : environ 10 coups de poing (moins avec une arme de mêlée forte) sur n'importe quelle partie,
      quel que soit le clan ; aux balles : selon l'arme ; l'échelle part toute ; rien n'est lâché ; le mur se rebouche.
- [ ] C4 ou charge explosive à côté (moins de 2,5 blocs du point d'explosion, sans mur entre les deux) : l'échelle part
      d'un coup ; une échelle de l'autre côté du mur ne bouge pas. Grenades, TNT et autres explosions : rien.
- [ ] Mur détruit (raid) : l'échelle posée contre lui tombe (toute), sans rien lâcher.
- [ ] Créatif : un clic la casse (toute).
- [ ] Recycleur : une échelle rend sa recette (9 planches, 9 clous, 2 cordes, avec la part du recycleur).
- [ ] Redémarrage : les échelles et leurs PV restent.
