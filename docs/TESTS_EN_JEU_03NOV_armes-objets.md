# Essais en jeu du 03/11 : chantier « armes et objets » (rien testé par Claude)

Retours du propriétaire après son premier passage en jeu (02/11 au soir), points 1 à 8 de ce chantier.

Jars touchés (joueurs ET serveur, même fabrication des deux côtés) : HeraliZ Tactique 2.44.0 (`armes`), Loot 0.14.8,
Monde 1.0.12. Données changées (mise en ligne complète puis redémarrage) : gunpacks `heraliz_5_couteaux_cs2` (fiche des
couteaux TaCZ) et `heraliz_crafts` (3 recettes retirées, listées dans `serveur/tacz_retires.txt`), tables de loot T1 à T7,
`serveur/config/heraliz_quetes/pnj/jacqueline.json`, `serveur/config/heralizcrates/quete.json`,
`serveur/defaultconfigs/heralisz_tactical-server.toml` (nouveau réglage `[melee] portee_couteaux = 1.0`, ajouté tout seul
au fichier du serveur par Forge au démarrage).

**Rien n'a été compilé avec Gradle** (le dépôt de Forge est bloqué ici) : seulement une vérification de nos fichiers Java
avec `javac` sans Minecraft (syntaxe et appels à nos propres classes : aucune erreur) ; les noms des méthodes de Minecraft
et de Forge utilisés (clic sur une case, événement d'attaque, attributs) n'ont pas pu être vérifiés. Le contrôle de branche
de GitHub fera la vraie fabrication : à regarder en premier. Outils relancés : `skins_couteaux.py`, `crafts.py`,
`marchands.py`, `caisses.py` (`--verifier` : 0 problème), `loot_armes.py`, `loot_zombies.py`, `quetes_journalieres.py` ;
`verifier_langues.py armes` : 0 erreur. `verifier.py` n'a pas tourné (il lit les jars fabriqués).

## 1. Étoiles des couteaux (point 1) — rien ne change en jeu
Rendus à valider, rien n'est branché. Après le choix, refaire cette fiche.

## 2. Couteaux : +10 % de vitesse en main (point 2)
- Avec un couteau TaCZ (Karambit, M9, papillon, couteau CT ou T) en main : on court environ 10 % plus vite qu'à mains nues
  (avant : 5 %). En main secondaire ou rangé : aucun bonus.
- Pareil avec un couteau à vie (Credo, Skeleton, Push, Stiletto, Dague TAC, Miséricorde…).
- La batte de baseball garde +5 % (elle n'est pas un couteau : à confirmer par le propriétaire).
- Couteau de cuisine ou couteau à éviscérer de GeckoLib (staff, `/give`) : +10 % aussi.
- Courir longtemps couteau en main sur la carte : aucune alerte de vitesse de Spartan.

## 3. Skin d'un sac par un joueur normal (point 3)
Avec un compte **sans op**, en survie :
- Porter un sac qui contient des objets, ouvrir l'inventaire (touche de l'inventaire : écran de Curios), prendre un objet
  camo au curseur (clic gauche), **clic droit sur le sac porté** (case du dos) : message « <camo> posé sur le sac », un
  objet camo en moins, le sac garde tout son contenu. Le camo se voit sur le dos (soi-même en 3e personne, et un autre
  joueur), pastille du camo en bas à droite de l'icône du sac.
- Pareil avec un sac rangé dans l'inventaire, et avec un sac dans un coffre.
- Refus avec un message, rien de consommé : sac qui a déjà un camo ; skin à vie (Séraphin, Chaînes de l'Enfer).
- Clic gauche avec le camo sur un sac : comportement normal (rien de posé).
- Infobulle d'un sac sans camo : dernière ligne « Clic droit dessus avec un camo : le pose sur le sac ».
- L'ancienne manière (camo + sac vide dans la grille 2 x 2) marche toujours, mais refuse maintenant les skins à vie.

## 4. Infobulles des camos (point 4)
- Un camo (ex. Galaxie) : 3 lignes seulement : nom en couleur, rareté, « Clic droit : ajoute à la collection à vie ».
  Plus de description ni de lignes « Ou : menu… », « Sac : … », « définitif ». Même chose pour Séraphin et Chaînes de
  l'Enfer.
- Un sticker : nom (en couleur de sa rareté : papier gris, holo bleu, métallisé violet, or doré), rareté, « Posé depuis le
  menu de modification d'armes ». Le module StatTrak : nom, « Épique », même ligne.
- Vérifier en anglais et en russe que rien ne dépasse.

## 5. Kit de survie militaire et Attelle et bandage (point 5)
- Le kit Épique « Kit chirurgical de campagne Surv12 » s'appelle « Kit de survie militaire » (en : Military Survival
  Kit, ru : Военный набор выживания) ; il soigne comme avant. **À confirmer : c'est bien celui-là (et pas le Kit
  chirurgical CMS, l'autre soin Épique) ?**
- « Attelle et bandage » : plus à la Table d'armurier (onglet Soins), plus chez Jacqueline, plus dans le menu créatif
  (onglet HeraliZ · Tactique, recherche). Un exemplaire déjà possédé (`/give` du staff avant la mise à jour) marche
  toujours (jambe cassée + saignement), se recycle et compte pour la quête « Rapporter 3 soins » de Varga.
- Bandage et Attelle séparés : toujours à la Table d'armurier, chez Jacqueline (30 et 80 hérals) et dans le loot.
- Ouvrir quelques caisses Quête et largages : jamais d'Attelle et bandage.

## 6. Portée des couteaux : 1 bloc (point 6)
Sur un zombie ou un joueur ami, avec un couteau TaCZ puis un couteau à vie, clic gauche et clic droit :
- collé à la cible (1 bloc ou moins entre les yeux et le corps) : le coup touche ;
- à 2 blocs : le coup ne touche plus (avant : jusqu'à 3) ;
- cible qui court en s'éloignant : un coup donné à la limite peut encore toucher (marge de 0,4 bloc du serveur pour le
  décalage du réseau, comme avant) ;
- la batte touche toujours à 3 blocs ;
- exécution / coup de grâce sur un joueur à terre, couteau en main : marche comme avant ;
- créatif : pas de limite (staff).
- Journal de Spartan : aucune alerte « Reach » nouvelle.

## 7. Deagle .12g et Thunderbird Short supprimés (points 7 et 8)
- Plus dans les conteneurs T1 à T7, le coffre d'armes de l'hélicoptère, le largage, ni à la Table d'armurier (onglet
  Pistolets : 16 armes au lieu de 18) ; plus dans le menu créatif de TaCZ ni dans sa recherche.
- Un exemplaire déjà possédé reste une arme normale (icône, tir, dégâts, revente à Mila, recyclage).
- Sur le Ptero après la mise en ligne : les fichiers `heraliz_crafts/.../armes/maxstuff_deagle_12g.json`,
  `maxstuff_thunderbird_short.json` et `soins/heralisz_heal_attelle_bandage.json` ont disparu de `/tacz`.
