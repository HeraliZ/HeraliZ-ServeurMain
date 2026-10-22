# Saisons des clans — règles (brouillon à valider)

Document de conception : **rien n'est codé**. Il rassemble les décisions du propriétaire (22/10) et les propositions
encore à valider (marquées **[à valider]**). Tous les chiffres sont des réglages du serveur, à ajuster pendant la bêta
sans refabriquer les mods.

---

## 1. L'idée

Une **saison dure 1 mois**. Les clans gagnent des **points de saison** dans trois classements (PVE, PVP, events). Les
points sont gardés dans le **cœur de clan**, posé dans la base : pendant **la Purge** (les seuls moments où les bases
peuvent être raidées), un clan ennemi qui tient le cœur vole une partie des points. À la fin du mois, les trois premiers
clans du classement général gagnent un prix.

Prix de la bêta :

| Place | Prix |
|---|---|
| 1er | 75 € de PB + une danse / emote perso pour tous les membres du clan |
| 2e | 50 € de PB + un skin au hasard (pas à vie) pour tous les membres du clan |
| 3e | 30 € de PB |

Membres récompensés : ceux du clan à la clôture de la saison (les clans sont figés les 7 derniers jours, §7).
**V1 : seulement des PB, pas d'argent réel** (décision du 22/10). Argent réel : saisons suivantes.

**Deux choses différentes, à ne pas confondre :**
- les **points de saison** : le classement, le cash prize ; gardés dans le cœur ; volables pendant la Purge ;
- l'**XP de clan** : le niveau du clan et son arbre d'améliorations (§6) ; jamais volée.
Beaucoup d'actions donnent les deux.

---

## 2. Le cœur de clan

- **Objet à part de l'armoire** (l'armoire de CRac reste le claim de la base). Un clan sans cœur posé **ne gagne aucun
  point de saison** et n'apparaît pas au classement : un clan qui ne veut pas jouer le classement ne pose pas de cœur,
  et n'intéresse pas les raideurs.
- **Cher**, pour qu'il faille d'abord farmer avant de pouvoir marquer des points. Décision : on fait d'abord le **bloc**
  (modèle très propre, c'est l'objet principal des clans : rendus à valider), le prix et le craft viendront ensuite.
- **Un seul par clan**, posé seulement dans la zone de la base du clan (claim de son armoire), par le chef ou un membre
  qui a le droit de gérer la base.
- **Hors Purge** : indestructible, personne ne peut y toucher.
- **Déplacer le cœur** : possible hors Purge, contre **50 000 hérals** (décision). Le chef le reprend en objet et a
  **10 minutes pour le reposer ; sinon le clan perd 100 % de ses points** (décision). Aucun point gagné tant qu'il n'est
  pas posé. Interdit pendant la Purge et dans l'heure qui la précède **[à valider]**. L'objet ne se jette pas, ne se
  range nulle part et revient au chef s'il meurt **[à valider]**.
- Clan dissous : le cœur disparaît avec tous ses points.

### Voler les points pendant la Purge

- Un membre d'un clan ennemi **tient le cœur 90 secondes** (entre 1 et 2 minutes, décision) : il reste à 3 blocs au plus,
  vivant, pas à terre. Tué, à terre ou éloigné : la prise repart de zéro. Une barre est visible par les deux clans.
- Réussite : le clan raidé **perd 15 %** de ses points, le clan attaquant en **gagne 10 %** ; les 5 % restants
  disparaissent (décision). Les 15 % sont pris dans chacun des trois classements (PVE, PVP, events).
- **Une seule prise par clan raidé et par Purge** (décision).
- L'attaquant doit avoir son propre cœur posé, sinon il ne gagne rien (le raidé perd quand même ses 15 %)
  **[à valider]**.
- Défendre ne rapporte pas de points (décision) : les défenseurs gardent le loot et les explosifs des attaquants.

---

## 3. La Purge

- **Les bases ne sont raidables que pendant la Purge.** Hors Purge, les explosifs ne font rien aux constructions de CRac
  et le cœur est intouchable. (Aujourd'hui, une base se raide à tout moment, sauf ses 4 premiers jours.)
- Horaires réglables, exemple du propriétaire : vendredi 18 h – 21 h, samedi 15 h – 22 h, dimanche 15 h – 22 h.
- Annonces 1 h et 10 min avant, au début et à la fin ; petit indicateur « PURGE » à l'écran pendant qu'elle dure.
- À la fin de la Purge : les explosifs ne font plus rien ; **une prise de cœur déjà commencée va jusqu'au bout**
  (décision), tant que les attaquants la tiennent.
- Protection des nouvelles bases **[à valider]** : une base dont l'armoire a été posée moins de 48 h avant le début d'une
  Purge n'est pas raidable pendant cette Purge (remplace la protection de 4 jours).

---

## 4. Le barème des points de saison

Les chiffres sont des **points de départ** (le propriétaire a donné le boss à 150 et le zombie à 0,01). Le principe :
ce qui est dur rapporte beaucoup, ce qui est facile presque rien.

### PVE

| Action | Points | Conditions |
|---|---|---|
| Zombie tué | 0,01 | pas en match Zombies, pas dans les vagues de capture d'un drapeau |
| Crawler / Blister | 0,02 | mêmes conditions |
| Brute | 0,5 | mêmes conditions |
| Boss tué | 150 | apparu sur la carte ou dans un event (jamais invoqué par le staff) ; partagé selon les dégâts faits |
| Salle à carte verte / bleue / rouge | 5 / 15 / 40 | une fois par recharge de la salle |
| Conteneur de loot T1 → T6 | 0,05 / 0,1 / 0,2 / 0,4 / 0,8 / 1,5 | première ouverture après qu'il a été rempli |
| Coffre d'événement T7 | 5 | première ouverture |
| Vente aux PNJ | 1 point pour 1 000 hérals | seulement les objets trouvés, jamais ceux achetés à un PNJ |
| Arme fabriquée | Commun 1, Peu commun 2, Rare 4, Épique 8 | **[à valider]** quand le craft sera refait |
| Quête de Varga finie | 5 à 20 selon le chapitre | une fois chacune |
| Quête de clan finie | selon la quête | limite par jour |

**Plafond par joueur et par jour** pour les petites actions (zombies, conteneurs, ventes) : 50 points **[à valider]**.
Boss, salles à carte et quêtes ne sont pas plafonnés.

### PVP

| Action | Points |
|---|---|
| Kill | +1 |
| Mort tuée par un joueur | −1 |

- Le total PVP d'un clan ne descend **jamais sous 0** (1 kill et 100 morts = 0) (décision).
- **Même victime** : 2e kill ×0,5, 3e kill et suivants 0 (décision) ; remise à zéro au bout de 6 h **[à valider]**.
- **La victime doit être une menace** (décision) : une arme ou une pièce d'armure sur elle. Proposition : à partir de
  Peu commun, et jamais les objets du `/kit` (sinon un ami donne un pistolet à un 2e compte puis le tue).
- **Rien** si le tueur et la victime ont le même PC ou la même IP (Spartan connaît le PC de chaque joueur), sont dans le
  même clan, ou si le kill a lieu en duel, en ClashWar ou en Zombies (ça compte dans les events).
- Un joueur à terre puis achevé = un seul kill.
- Une mort ne fait perdre des points que si le kill en aurait rapporté (pas de perte pour une mort contre un zombie,
  `/suicide`, ou un ami au même PC).

### Events

Même grille de points pour tous les events, pour qu'un nouvel event ne déséquilibre rien :

| Place | Points |
|---|---|
| 1er | 100 |
| 2e | 60 |
| 3e | 40 |
| 4e | 20 |
| Participation (avoir vraiment joué) | 5 |

Chaque event mesure quelque chose pour donner les places : KOTH = temps passé dans la zone ; ClashWar = les 4 places de son tableau (points ×2, c'est le gros event
**[à valider]**). Idées d'events : KOTH, oil rig, largage disputé, boss du monde, attaque du train.

**Oil rig** (décision, façon Rust) : une plateforme avec une **salle à carte rouge** et un **coffre T7**, gardée par des
bots qui arrivent par vagues. Points PVE pour le clan qui ouvre le T7 **[montant à valider]**. Les bots sont un
nouveau système à faire (PNJ armés).

---

## 5. Les classements

- **Trois classements** : PVE, PVP, events. Le **classement général** additionne les places dans les trois : la plus
  petite somme gagne (1er partout = 3). Égalité : le meilleur total de points départage.
- Un clan sans aucun point dans une catégorie est dernier de cette catégorie.
- **Toujours affiché** (décision) : mur au spawn (même système que le classement du ClashWar), commande `/saison
  classement`, et onglet dans le menu des clans.
- Un clan sans cœur posé n'est pas classé.

---

## 6. Niveaux de clan et arbre d'améliorations

- Le clan gagne de l'XP et monte de niveau ; **chaque niveau donne 1 point de compétence**.
- Une amélioration coûte **des points + des hérals** ; les points ne sont **jamais remboursés** (décision).
- **Palier n d'une branche = n points et n × 5 000 hérals** (décision).
- **Niveau maximum = le niveau qui permet de tout prendre** : **45**.
- **Chaque niveau est plus dur que le précédent** (décision) : premiers niveaux en quelques heures, derniers en environ
  une journée. Un clan nombreux et très actif doit pouvoir tout prendre en **2 à 3 semaines** (décision). Les chiffres
  d'XP restent un réglage (`niveaux.json`), réglé pendant la bêta sur ce que gagnent les meilleurs clans.

| Branche | Paliers | Points | Hérals |
|---|---|---|---|
| Membres : 5 → 6 → 7 → 8 → 9 → 10 | 1 + 2 + 3 + 4 + 5 | 15 | 75 000 |
| Taille de la base : rayon 20 → 23 → 26 → 29 blocs | 1 + 2 + 3 | 6 | 30 000 |
| TP vers la base : 10 → 8 → 6 → 4 min | 1 + 2 + 3 | 6 | 30 000 |
| Revenu des territoires : +10 / +20 / +30 % | 1 + 2 + 3 | 6 | 30 000 |
| Ravitaillement plus rapide : −15 / −30 / −45 % | 1 + 2 + 3 | 6 | 30 000 |
| Loot dans ses territoires : +2 / +4 % | 1 + 2 | 3 | 15 000 |
| Territoires tenus en même temps : 1 → 2 → 3 | 1 + 2 | 3 | 15 000 |
| **Total** | | **45** | **225 000** |

- **La déco reste automatique** avec le niveau (décision) : fonds de bannière, tag en couleur, formes rares, tag doré
  (niveaux à recaler sur le nouveau maximum de 45).
- Tout ce qui touche au jeu (membres, territoires, revenu, ravitaillement, loot) **n'est plus automatique** : seulement
  par l'arbre. Aujourd'hui, un clan gagne 1 membre par niveau sans rien payer (jusqu'à 20) ; ce sera 5 à 10.
- La zone de construction (aujourd'hui agrandie avec du métal renforcé) passe dans l'arbre.
- À surveiller : 225 000 hérals en ~17 jours = plus de 13 000 par jour pour le clan ; 3 territoires en rapportent
  environ 7 200. Le reste vient de la revente, des quêtes et des events.

### D'où vient l'XP de clan

Territoires tenus (déjà en place), kills, boss, zombies, ravitaillement, quêtes de clan (déjà en place), events gagnés,
et **dons d'objets au clan** (décision) : un objet Légendaire ou Unique donné au clan disparaît et rapporte de l'XP, au
lieu d'être vendu.

---

## 7. Les clans pendant la saison

- **Les 7 derniers jours, les clans sont figés** (décision) : on ne rejoint plus un clan ; qui quitte le sien ne peut
  plus en rejoindre un autre.
- Le reste de la saison : 24 h d'attente après avoir quitté un clan avant d'en rejoindre un autre **[à valider]**.
- Les points sont ceux du clan, pas des joueurs : un joueur qui part ne les emporte pas.

---

## 8. Fin et remise à zéro de la saison

1. **Clôture** (`/saison fin`) : le classement est figé et archivé (saison N, avec les membres de chaque clan) : preuve
   pour les prix, historique.
2. **Wipe de saison** (`/saison wipe`), pour **tous** les joueurs, connectés ou non :
   - un panneau liste tout ce qui sera effacé, Oui / Non (comme pour jeter un couteau) ; Non = rien ne change ;
   - puis un 2e panneau « Êtes-vous sûr ? » ;
   - seulement le staff op connecté sur AuthMe, ou la console.

| Effacé | Gardé |
|---|---|
| clans, niveaux, arbres, points de saison | comptes AuthMe et du site |
| hérals | grades de la boutique |
| inventaires, coffres de l'Ender, coffres personnels | collection de camos à vie |
| bases, cœurs, homes | **objets à vie** (couteaux, skins à vie) : rendus à la 1re connexion |
| quêtes, territoires | emotes perso et autres récompenses permanentes |
| apparence du personnage (refait au créateur) | |

Le `/wipe <joueur>` actuel ne vise qu'un joueur connecté et ne touche ni aux hérals, ni au clan, ni aux coffres
personnels : il reste pour les cas isolés.

**À faire avant la 1re saison** : les objets à vie sont gardés aujourd'hui dans les données du joueur, **dans le
monde**. Il faut une liste hors du monde (qui possède quel couteau ou skin à vie, comme la collection de camos) pour les
rendre à chaque nouvelle saison, même avec une nouvelle carte.

---

## 9. Contre la triche et les abus

| Faille | Parade |
|---|---|
| Se faire tuer par un 2e compte | rien pour même PC / même IP ; victime armée ou en armure (hors kit) |
| Tuer le même joueur en boucle | ×1, ×0,5, puis 0 |
| Deux clans complices qui se raident pour se passer des points | 15 % perdus, 10 % gagnés ; une prise par Purge |
| Acheter chez un PNJ, revendre chez un autre | objets achetés marqués, aucun point à la revente |
| Fabriquer, défaire, refabriquer | points du craft une seule fois ou selon les matériaux |
| Farmer les zombies des vagues de capture ou du mode Zombies | ne comptent pas |
| Boss invoqué | seulement les boss de la carte ou des events |
| AFK, macros, petites actions répétées | plafond par joueur et par jour |
| Rejoindre le 1er clan juste avant la fin | clans figés les 7 derniers jours |
| Dissoudre et refaire un clan | nouveau cœur à payer, points perdus |
| Un joueur avec 2 comptes dans 2 clans | repéré par le PC (Spartan) |
| Le staff qui joue avec ses droits | staff hors des clans classés, ou sans ses droits |
| Contestation des résultats | **chaque point noté** dans un journal (qui, quand, pourquoi, combien) ; `/saison journal <clan>` |

Règle du concours : un clan pris à tricher est disqualifié et perd tout.

---

## 10. Argent réel : à régler avant la saison

- **Rien de payant ne doit aider à gagner des points** : le « Bonus de loot » de la boutique est coupé ou ne compte pas
  pendant la saison ; les hérals ne sont **jamais** vendus contre de l'argent réel.
- Un **règlement écrit du concours** (qui peut gagner, âge, versement des prix).
- Serveur en version crackée + prix en argent : à faire vérifier avant de passer à l'argent réel.

---

## 11. Modèles à faire (rendus d'abord)

- **Cœur de clan** : bloc « ultra clean », l'objet principal des clans. Style **futuriste** (choix du 22/10 ; refusés :
  noir et or, radio, étendard, coffre de récup). Proposition en cours : cristal d'énergie qui flotte entre trois bras,
  socle rond à filets lumineux, **points du clan sur le socle**, **hologramme au-dessus qui fait défiler les rangs**
  (PVE, PVP, events, général). Doit rester très léger en jeu (modèle simple, hologramme et rotation dessinés à part).
  Rendus : `python mods/crac/outils/coeur_tc_rendus.py --apercu DOSSIER`.
- **Armoire** : style « survie » **validé** le 22/10 (mêmes rendus).
- **Armoire (TC) refaite** : l'actuelle est jugée très moche. Style apocalypse zombie, avec un **petit écran rouillé qui
  bugue** où s'affichent ce que la base demande (entretien) et le temps restant avant qu'elle disparaisse.

## 12. Questions encore ouvertes

1. Prix et craft du cœur (après le modèle).
2. Points de l'oil rig.
3. Protection des nouvelles bases : 48 h avant une Purge, ou autre chose ?
4. Les montants exacts du barème (à régler pendant la bêta).
