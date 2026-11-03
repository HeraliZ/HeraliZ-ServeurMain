# Essais en jeu du 03/11 : chantier « armes et objets » (rien testé par Claude)

Retours du propriétaire après son premier passage en jeu (02/11 au soir), points 1 à 8 de ce chantier.

Jars touchés (joueurs ET serveur, même fabrication des deux côtés) : HeraliZ Tactique 2.46.0 (`armes`, suite du 03/11 et coups mieux timés compris), Loot 0.14.8,
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

## 1. Étoiles des couteaux (point 1, proposition A validée) — à refaire
- Infobulle d'un skin de couteau TaCZ (Karambit, M9…) puis d'un couteau à vie (Credo, Push…) : sous le nom et la rareté,
  4 étoiles nettes de la taille d'une lettre : les allumées en or (avec un liseré sombre), les autres en contour gris,
  comme sur les rendus. Plus d'étoiles en gros points.
- Essayer 1, 2, 3 et 4 étoiles, aux échelles d'interface 2, 3 et 4 : étoiles lisses, ni floues ni pixelisées, alignées
  sur le texte, rien ne déborde du cadre ; « Exclusivité HeraliZ » toujours dessous.
- Les autres infobulles (soins, grenades, armes à feu) ne changent pas.

## 2. Couteaux : +10 % de vitesse en main (point 2)
- Avec un couteau TaCZ (Karambit, M9, papillon, couteau CT ou T) en main : on court environ 10 % plus vite qu'à mains nues
  (avant : 5 %). En main secondaire ou rangé : aucun bonus.
- Pareil avec un couteau à vie (Credo, Skeleton, Push, Stiletto, Dague TAC, Miséricorde…).
- La batte de baseball aussi : +10 % (choix du propriétaire du 03/11 ; avant : +5 %).
- Couteau de cuisine ou couteau à éviscérer de GeckoLib (staff, `/give`) : +10 % aussi.
- Courir longtemps couteau en main sur la carte : aucune alerte de vitesse de Spartan.

## 3. Camo sur un sac : réservé aux op (point 3, revu le 03/11) — à refaire
- Compte **sans op**, en survie : camo + sac vide dans la grille 2 x 2 de l'inventaire (ou un établi) : **aucun
  résultat** dans la case de résultat. Camo au curseur et clic droit sur un sac : rien ne se passe (échange ou rien,
  comme avant le 03/11) ; plus de ligne d'aide dans l'infobulle des sacs.
- Compte **op** (niveau 2 ou plus), en survie : même grille : le sac camouflé apparaît et se prend ; le camo se voit sur
  le dos et en pastille sur l'icône du sac. Un skin à vie (Séraphin, Chaînes de l'Enfer) n'est jamais accepté.
- Retirer l'op au joueur pendant que la grille est remplie, puis bouger un objet : le résultat disparaît.

## 4. Infobulles des camos (point 4)
- Un camo (ex. Galaxie) : 3 lignes seulement : nom en couleur, rareté, « Clic droit : ajoute à la collection à vie ».
  Plus de description ni de lignes « Ou : menu… », « Sac : … », « définitif ». Même chose pour Séraphin et Chaînes de
  l'Enfer.
- Un sticker : nom (en couleur de sa rareté : papier gris, holo bleu, métallisé violet, or doré), rareté, « Posé depuis le
  menu de modification d'armes ». Le module StatTrak : nom, « Épique », même ligne.
- Vérifier en anglais et en russe que rien ne dépasse.

## 5. Kit de survie militaire et Attelle et bandage (point 5)
- Le kit Épique « Kit chirurgical de campagne Surv12 » s'appelle « Kit de survie militaire » (en : Military Survival
  Kit, ru : Военный набор выживания) ; il soigne comme avant (confirmé par le
  propriétaire le 03/11 : c'est bien le Surv12).
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
- la batte aussi : 1 bloc au plus (choix du propriétaire du 03/11 ; avant : 3) ;
- exécution / coup de grâce sur un joueur à terre, couteau en main : marche comme avant ;
- créatif : pas de limite (staff).
- Journal de Spartan : aucune alerte « Reach » nouvelle.

## 7. Deagle .12g et Thunderbird Short supprimés (points 7 et 8)
- Plus dans les conteneurs T1 à T7, le coffre d'armes de l'hélicoptère, le largage, ni à la Table d'armurier (onglet
  Pistolets : 16 armes au lieu de 18) ; plus dans le menu créatif de TaCZ ni dans sa recherche.
- Un exemplaire déjà possédé reste une arme normale (icône, tir, dégâts, revente à Mila, recyclage).
- Sur le Ptero après la mise en ligne : les fichiers `heraliz_crafts/.../armes/maxstuff_deagle_12g.json`,
  `maxstuff_thunderbird_short.json` et `soins/heralisz_heal_attelle_bandage.json` ont disparu de `/tacz`.

## 8. Coups des couteaux et de la batte mieux timés (retour du 03/11) — à tester
Sur un zombie puis un joueur ami collé à soi, d'abord avec un petit ping, puis depuis un PC avec un ping plus fort (ou
un serveur chargé) :
- **Couteau TaCZ** (Karambit, M9…), clic gauche puis clic droit : le bruit d'impact « chair » part au moment où la lame
  passe devant soi, plus avec un temps de retard ; la cible rougit au même moment chez un ami qui regarde. Avec un fort
  ping, la cible rougit un peu après le bruit (c'est le trajet du réseau), mais jamais avant que la lame passe.
- **Batte**, clic gauche plusieurs fois de suite : les deux gestes qui alternent frappent chacun quand la batte passe
  (avant : un sur deux tapait en retard) ; clic droit : coup au moment du choc. Bruit d'impact entendu par le frappeur
  (avant : il n'entendait rien).
- **Couteau à vie** (Credo, Push…) : bruit d'impact entendu par le frappeur au moment du coup.
- Rien ne change pour la vitesse : toujours 1,2 s entre deux coups, toujours 1 bloc de portée ; cliquer très vite ne
  donne pas plus de coups. Frapper dans le vide : aucun bruit d'impact.
