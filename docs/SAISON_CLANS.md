# Saisons des clans — règles

Décisions du propriétaire (22/10) et propositions encore à valider (marquées **[à valider]** : codées avec la valeur
proposée, à changer si besoin). **Codé le 22/10, rien testé en jeu** : voir §13 (ce qui marche, où, et ce qui reste). Tous les chiffres sont des réglages du serveur, à ajuster pendant la bêta
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
- Protection des nouvelles bases : **aucune** (décision du 31/10, CRac 1.29.0-beta). Une base posée juste avant ou
  pendant une Purge se raide comme les autres ; plus de protection de 4 jours (coupée le 27/10) ni de 48 h avant la Purge
  (`protection_nouvelle_base_heures` = 0 dans `purge.json` ; un nombre d'heures la remettrait).

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
| Boss tué | 60 | apparu sur la carte ou dans un event (jamais invoqué par le staff) ; partagé selon les dégâts faits ; par clan et par jour : 1er kill de ce boss 100 %, 2e 20 %, ensuite 0 (25/10 : avant, 150 à chaque réapparition, toutes les heures) |
| Salle à carte verte / bleue / rouge | 5 / 15 / 40 | une fois par recharge de la salle |
| Capture d'un drapeau (zone libre) | 3 × valeur de la ville (1 à 7) × difficulté des vagues (0,8 / 1 / 1,25) | 25/10 ; même zone pour le même clan : une fois par 12 h ; 40 points de capture par clan et par jour au plus (avec les prises ci-dessous) ; section `capture` de `saison.json` ; 31/10 : clan de niveau 5 au moins, avec l'amélioration Territoires, 2 zones au plus (§6 bis) |
| Conteneur de loot T1 → T6 | 0,05 / 0,1 / 0,2 / 0,4 / 0,8 / 1,5 | première ouverture après qu'il a été rempli |
| Coffre d'événement T7 | 5 | première ouverture |
| Vente aux PNJ | 1 point pour 1 000 hérals | seulement les objets trouvés, jamais ceux achetés à un PNJ |
| Arme fabriquée | Commun 1, Peu commun 2, Rare 4, Épique 8 | **[à valider]** quand le craft sera refait |
| Quête de Varga finie | 10 | une fois chacune ; 02/11 (choix du propriétaire) : comptée dans les petites actions plafonnées par joueur et par jour (`plafonnees` de `saison.json`) |
| Quête de clan finie | 5 | limite par jour ; « Jouer N min » : seulement les minutes sur la carte, en survie, où le joueur a marché au moins 10 m ou agi (dégâts, cache fouillée) ; « Parcourir N km » : seulement à pied (ni véhicule, ni train, ni nage) (25/10) |

**Plafond par joueur et par jour** pour les petites actions (zombies, conteneurs, ventes, fabrication et, depuis le
02/11, quêtes de Varga) : 50 points.
Salles à carte et quêtes de clan ne sont pas plafonnées ; les boss ont leur rendement décroissant par clan et par jour
(`boss_coefficients` de `saison.json`).

### PVP

| Action | Points |
|---|---|
| Kill | +1 |
| Mort tuée par un joueur | −1 |
| Prendre un drapeau à un clan qui le tenait depuis 60 min au moins | 6 × valeur de la ville × difficulté des vagues (25/10) ; les deux mêmes clans sur la même zone : une fois par 48 h (contre les échanges entre clans complices) ; plafond commun de 40 points de capture par clan et par jour |

- **Plafond des kills** (02/11, choix du propriétaire) : 40 points de kills gagnés par clan et par jour au plus, comme
  les captures (`pvp.plafond_jour_clan` de `saison.json`, 0 : sans plafond) ; les points perdus par le clan de la victime
  ne sont pas plafonnés.
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
- **Chaque niveau est plus dur que le précédent** (décision). ~~Premiers niveaux en quelques heures, tout prendre en 2 à
  3 semaines~~ : remplacé le 31/10 (demande du propriétaire : « beaucoup trop facile, dès le début ») par la courbe
  ci-dessous (§6 bis). Les chiffres d'XP restent un réglage (`niveaux.json`), réglé pendant la bêta.

| Branche | Paliers | Points | Hérals |
|---|---|---|---|
| ~~Membres : 4 → 8 (paliers aux niveaux 10, 20, 30, 40)~~ : retirée de l'arbre le 31/10, les places viennent du niveau du clan (§6 bis) | – | – | – |
| Taille de la base : rayon 20 → 23 → 26 → 29 blocs | 1 + 2 + 3 | 6 | 30 000 |
| TP vers la base (/home et réapparition à la base, délai partagé) : 8 → 7 → 6 → 5 min (25/10) | 1 + 2 + 3 | 6 | 30 000 |
| Revenu des territoires : +10 / +20 / +30 % | 1 + 2 + 3 | 6 | 30 000 |
| Ravitaillement plus rapide : −15 / −30 / −45 % | 1 + 2 + 3 | 6 | 30 000 |
| Loot dans ses territoires : +2 / +4 % | 1 + 2 | 3 | 15 000 |
| Territoires tenus en même temps : 0 → 1 → 2 (31/10 ; paliers réservés aux niveaux 5 et 10 du clan ; avant : 1 → 2 → 3) | 1 + 2 | 3 | 15 000 |
| **Total** (sans Membres, depuis le 31/10) | | **30** | **150 000** |

- **La déco reste automatique** avec le niveau (décision) : fonds de bannière, tag en couleur, formes rares, tag doré
  (niveaux à recaler sur le nouveau maximum de 45).
- Tout ce qui touche au jeu (territoires, revenu, ravitaillement, loot) **n'est plus automatique** : seulement
  par l'arbre. Exception du 31/10 (décision du propriétaire) : les **membres** viennent du niveau seul (§6 bis). Aujourd'hui, un clan gagne 1 membre par niveau sans rien payer (jusqu'à 20) ; ce sera 5 à 10.
- La zone de construction (aujourd'hui agrandie avec du métal renforcé) passe dans l'arbre.
- À surveiller : 225 000 hérals en ~17 jours = plus de 13 000 par jour pour le clan ; 3 territoires en rapportent
  environ 7 200. Le reste vient de la revente, des quêtes et des events.

- **25/10 (demande du propriétaire)** : un clan commence à **4 membres** et monte à **8 au plus** par 4 améliorations ;
  chacune demande un niveau du clan (10, 20, 30, 40) et beaucoup de hérals (455 000 en tout, trois fois le reste de
  l'arbre) : 8 membres est un objectif de fin de saison (31/10 : remplacé, places données par le niveau, §6 bis). Les
  clans déjà plus nombreux gardent leurs membres (aucun n'est
  exclu), mais n'en invitent plus tant qu'ils sont au-dessus de leur maximum. **Aucune alliance** entre clans : il n'en
  existe aucune dans le code (le mot « alliés » de la carte désigne les membres de son propre clan) ; bases, coffres,
  pings, voix des matchs, captures et anti-ESP de Spartan ne voient que son propre clan.
- Niveau maximum toujours 45 (5 points de plus que l'arbre complet avant le 31/10 ; 15 de plus depuis le retrait de la
  branche Membres, l'arbre complet coûtant 30 points).

### D'où vient l'XP de clan

Territoires tenus (déjà en place), kills, boss, zombies, ravitaillement, quêtes de clan (déjà en place), events gagnés,
et **dons d'objets au clan** (décision) : un objet Légendaire ou Unique donné au clan disparaît et rapporte de l'XP, au
lieu d'être vendu.

### 6 bis. Niveaux plus durs, plafonds par jour, captures au niveau 5 (31/10, demande du propriétaire)

**Sources d'XP relevées dans le code** (gains par action : `heralisz_clans-common.toml` [xp] et `xp_heure_valeur` ;
quêtes : `quetes.json`). Les points de saison, les événements de la carte et ClashWar ne donnent **aucune** XP de clan.

| Source | XP par action | Plafond par clan et par jour (31/10) |
|---|---|---|
| Quête de clan du jour (3 tirées par jour) | 200 à 600 (moyenne ~330) | aucun (déjà limitées par le tirage) |
| Quête de clan de la semaine (2 tirées par semaine) | 1 200 à 2 500 | aucun |
| Capture d'une zone | 150 | 450 (3 captures) |
| Zone tenue | 10 par heure et par point de valeur de la zone (2 à 5 sur la carte) | 600 |
| Signal de ravitaillement | 100 | 300 |
| Joueur tué (anti-boost des clans) | 5 | 150 |
| Zombie tué | 1 | 400 (avant : aucun plafond) |
| Boss tué (boss mis de côté) | 50 | 150 |
| Don d'un objet (`/clan don`) | Épique 150, Légendaire 1 000, Unique 2 500 | 3 000 en entier, puis 25 % |
| Staff (`/clan admin xp`) | – | jamais plafonné |

Plafonds et dons : `serveur/config/heralisz_clans/niveaux.json` (`plafonds_jour`, `dons`), relus par
`/clan admin recharger`. Jour = heure de Paris ; compteurs gardés dans le clan (un redémarrage ne les remet pas à zéro) ;
le clan est prévenu dans le chat quand une source atteint son plafond ; l'onglet Niveau (K) montre au survol de chaque
ligne le plafond et ce que le clan a déjà reçu aujourd'hui (valeurs envoyées par le serveur ; avant, l'onglet lisait les
réglages du PC du joueur).

**Ce que gagne un clan par jour** (estimation avec ces plafonds) :
- petit clan normal (3 joueurs, 2 à 3 h chacun) : ~1 500 XP avant le niveau 5 (quêtes ~1 100, zombies ~300, un peu de
  PVP et de dons), ~2 100 ensuite (une ou deux captures, une zone tenue, un ravitaillement) ;
- gros clan très actif (8 joueurs, 5 h chacun) : ~2 900 avant le niveau 5, ~4 600 ensuite (tous les plafonds atteints).

**Courbe** (`xp_suivant` de `niveaux.json`, `facteur_xp` = 1) : `3 600 × (1 + 0,065 × (n − 1))` arrondi à 100, soit
3 600 XP pour le niveau 2, puis 3 800, 4 100, 4 300, 4 500… jusqu'à 13 700 pour le niveau 45 ; ~380 000 XP en tout
(avant : 20 × n^1,5 × 3, soit **60 XP** pour le niveau 2 et ~317 000 en tout : facile au début, dur à la fin).

| Niveau atteint | XP totale | Petit clan normal | Gros clan très actif |
|---|---|---|---|
| 2 | 3 600 | ~3 jours | ~2 jours |
| 5 (captures) | 15 800 | ~11 jours | ~6 jours |
| 10 | 40 800 | ~23 jours | ~12 jours |
| 20 | 108 300 | ~55 jours | ~26 jours |
| 45 | 379 800 | ~6 mois | ~3 mois |

En une saison d'un mois : niveau ~12 pour un petit clan normal, ~22 pour le meilleur clan. ~~Conséquence à valider :
les paliers Membres (niveaux 10, 20, 30, 40) deviennent bien plus lointains~~ : réglé le 31/10 (décision du propriétaire,
Clans 3.10.1) : **4 membres au départ, 5 au niveau 2, 6 au niveau 5, 7 au niveau 10, 8 au niveau 15** du clan (8 reste
le maximum). La place est **donnée toute seule** en atteignant le niveau : rien à acheter, ni points ni hérals.
L'amélioration « Membres » est retirée de l'arbre (6 branches, 30 points pour tout prendre).
- Réglage : section `membres` de `niveaux.json` (`depart`, `niveaux_paliers`, 4 niveaux au plus), relue par
  `/clan admin recharger`, ajoutée au fichier du serveur s'il ne l'a pas, envoyée aux joueurs avec l'état du clan
  (canal des clans version 8).
- Montée de niveau 2, 5, 10, 15 : message au clan « Niveau N : votre clan peut maintenant avoir X membres. » ; l'onglet
  Niveau annonce « X membres au plus » dans « Prochain niveau » ; « clan plein » donne le niveau de la place suivante.
- Clans qui avaient acheté des paliers Membres (25/10 au 31/10), une seule fois au démarrage (`MigrationMembres`) :
  points de compétence rendus (la branche ne compte plus), hérals rendus à la banque du clan, palier par palier : le
  prix vraiment payé s'il est encore dans le journal de la banque (30 dernières lignes), sinon le prix de base du palier
  sans facteur (25 000 / 60 000 / 120 000 / 250 000, le moins qu'il ait pu coûter) : jamais plus que ce qui a été payé,
  parfois moins si l'achat est trop ancien pour le journal (achat à ×2 après le 28/10). Ligne « remboursement
  (Membres) » dans la banque, ligne au journal du clan, trace dans le journal du serveur.
- Un clan déjà au-dessus du maximum de son niveau garde tous ses membres et n'invite plus personne tant qu'il est
  au-dessus (règle du 25/10, inchangée).

**Clans d'avant le 31/10** : leur XP totale (comptée sur l'ancienne courbe) est gardée et leur niveau recalculé une fois
au démarrage sur la nouvelle (ancien niveau 10 → 2, 15 → 5, 20 → 9, 30 → 20, 45 → 40) ; **toutes les améliorations
déjà achetées restent** (et leurs effets) : un clan qui a dépensé plus de points que son nouveau niveau n'en donne n'a
simplement plus de point libre tant que ses niveaux suivants n'ont pas comblé l'écart. Ses membres sont prévenus à leur
première connexion, une ligne est ajoutée au journal du clan, l'ancien niveau est gardé dans `clans.json`
(`niveauAvantCourbe`) et dans le journal du serveur (`MigrationNiveaux`).

**Dons (`/clan don`)** : seulement les objets de valeur à revendre (tag `heraliz_composants:vente`), Épiques et plus ;
environ 3 à 5 XP par héral de leur prix chez Mila (Épique 50, Légendaire 200, Unique 500). Rare et moins : refusés
(4 % des zombies lâchent un objet Rare : ce serait une source sans fin). Contre l'XP créée à l'infini :
- aucun marchand ne vend ces objets, aucune recette ne les fabrique, le recycleur n'en rend que des Communs et Peu communs
  (vérifié le 31/10) ; seules autres sources : loot, butin des zombies, largages, et le lingot d'or d'une récompense de
  quête du jour de Varga (5 %, une fois par joueur et par jour) ;
- refusé en créatif et en spectateur (copie au bouton du milieu) ;
- les objets Légendaires et Uniques (non empilables) sont numérotés par l'anti-duplication de Spartan
  (`trackedItems` de `spartan-common.toml`) ; un objet dont le numéro a déjà été donné est refusé (copie), le staff le
  voit dans le journal ; le fichier des clans est écrit tout de suite après chaque don ;
- au-delà du plafond du jour (3 000 XP), le joueur doit confirmer (`/clan don confirmer`, 30 s) : il ne perd pas un
  objet rare sans le savoir.
Les objets Uniques à revendre (`heraliz_composants:crane_cristal`, `sceptre`, `calice`) tombent depuis le 31/10
**seulement dans les coffres T7** (coffres d'événement : hélicoptère abattu, points T7 du staff…) : 2 pour 100 coffres
T7, soit environ **1 coffre T7 sur 50** (`UNIQUES_T7` de `outils/loot_armes.py` ; même chance qu'une arme Unique dans
un T7). Jamais dans les conteneurs T1 à T6, les zombies, le largage public ni les caisses ; jamais vendus, fabriqués ni
recyclés ; suivis par Spartan.

**Captures** (`Territoires.peutPrendre`, vérifié à chaque cycle de capture) : un clan peut prendre une zone seulement à
partir du **niveau 5** et avec l'amélioration Territoires (palier 1 : 1 zone, niveau 5 ; palier 2 : 2 zones, niveau 10),
et **jamais plus de 2 zones en même temps**. Un clan qui ne peut pas prendre de zone ne capture pas et ne **conteste pas
non plus** (il ne bloque pas les autres clans au drapeau) ; ses membres au drapeau voient « Capture verrouillée » ou
« Limite de territoires » au HUD et la raison dans le chat (une fois toutes les 5 min). `/territoire capturer` du staff
refuse une 3e zone. Les quêtes de territoire (capturer, tenir, ravitailler) ne sont plus tirées pour un clan qui ne peut
pas tenir de zone. Zones déjà tenues par un clan au-dessus de la nouvelle limite : il les garde jusqu'à la fin de leur
tenue (2 h au plus, `tenue_min`), puis elles redeviennent libres ; il ne peut pas en prendre d'autre entre-temps.

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
| Acheter chez un PNJ, revendre chez un autre | aucun point pour un objet qu'un marchand vend |
| Fabriquer, défaire, refabriquer ; poser une arme au sol et la ramasser table ouverte | arme ramassée exclue ; plafond par jour |
| Farmer les zombies des vagues de capture ou du mode Zombies | ne comptent pas |
| Boss invoqué | /summon, œuf : aucun point ; points partagés selon les dégâts |
| AFK, macros, petites actions répétées | plafond par joueur et par jour ; quêtes de clan de temps et de distance comptées seulement en jeu actif sur la carte, à pied |
| Farmer les boss à chaque réapparition | par clan et par boss : 100 %, puis 20 %, puis 0 dans la journée |
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
  **Validé le 22/10.** Le cristal tourne sans jamais traverser le socle, les bras ou l'anneau : le script mesure le
  volume qu'il balaie en tournant et refuse tout modèle où une pièce y entre (marge 0,5 px, `verifier_rotation`).
  Rendus : `python modules/bases/outils/coeur_tc_rendus.py --apercu DOSSIER`.
- **Armoire** : style « survie » **validé** le 22/10 (mêmes rendus).
- **Armoire (TC) refaite** : l'actuelle est jugée très moche. Style apocalypse zombie, avec un **petit écran rouillé qui
  bugue** où s'affichent ce que la base demande (entretien) et le temps restant avant qu'elle disparaisse.

## 12. Questions encore ouvertes

1. Prix et craft du cœur (après le modèle).
2. Points de l'oil rig.
3. ~~Protection des nouvelles bases~~ : aucune (décidé le 31/10).
4. Les montants exacts du barème (à régler pendant la bêta).

---

## 13. Ce qui est codé (22/10) — rien testé en jeu

**HeraliZ Clans 3.6.0** (`modules/clans`, `fr.heralisz.clans.saison`) :
- barème : `serveur/config/heralisz_clans/saison.json` (fichier du dépôt qui fait foi, relu par `/saison recharger`) ;
  état hors du monde : `heralisz_clans/saison_etat.json`, journal de chaque point `heralisz_clans/saison_journal.log`,
  archives `heralisz_clans/saisons/saison_N.json` (classement et membres de chaque clan) ;
- `/saison`, `/saison classement [general|pve|pvp|events]` ; staff (permission 4) : `/saison commencer <jours>`,
  `/saison terminer confirmer`, `/saison points <tag> <pve|pvp|events> <±n> <motif>`, `/saison journal <tag>` ;
- PVP (kill/mort hors match, hors spawn et monde du tutoriel ; même IP ; victime sans arme ni armure d'au moins Peu
  commun, objets du kit exclus ; même victime ×1, ×0,5, 0 sur 6 h ; jamais sous 0) ; zombies (sauf vagues de capture) ;
  boss (pas ceux invoqués, partagés selon les dégâts) ; quêtes de clan ; plafond 50 points par joueur et par jour pour
  les petites actions ; verrou des 7 derniers jours (ni création ni entrée dans un clan) ;
- arbre d'améliorations (onglet NIVEAU du menu K, `/clan ameliorer <branche>`) et niveaux jusqu'à 45
  (`serveur/config/heralisz_clans/niveaux.json`) ; `/clan don` (objet de récupération Légendaire ou Unique en main → XP ;
  31/10 : Épique aussi, barème et plafonds dans `niveaux.json`, §6 bis) ;
- `fr.heralisz.clans.saison.SaisonApi` : API par réflexion pour les autres mods (ne jamais la renommer).

**CRac 1.19.0-beta** :
- **Purge** : `serveur/config/crac/purge.json` (créneaux, heure de Paris), `/purge`, staff `/purge forcer <min>`,
  `/purge arreter`, `/purge recharger` ; hors Purge, explosifs sans effet sur les bases des clans ; base dont l'armoire a
  été vue moins de 48 h avant le début d'une Purge : pas raidable pendant celle-ci (casser et reposer son armoire ne
  remet pas ce compteur à zéro ; **retiré le 31/10, CRac 1.29.0-beta** : 0 h par défaut) ;
- **prise du cœur** (clic droit, 90 s, 3 blocs, au moins un membre du clan attaquant vivant et pas à terre, barre
  visible des deux clans, pas à travers un mur) ; **`/coeur deplacer`** (chef ou droit de gérer la base, à côté du
  cœur, 50 000 hérals de la banque, 10 min pour le reposer, sinon tous les points perdus ; l'objet ne se jette pas) ;
  cœur posé seulement par le chef ou un membre qui gère la base ; points et rangs affichés sur le cœur (toutes les 5 s) ;
  clan dissous : cœur retiré ;
- arbre : rayon de la base 20/23/26/29 (claim de niveau 4, `radiusL4`), plus d'agrandissement au métal renforcé ;
  délai du `/home` 10/8/6/4 min ;
- `fr.heraliz.crac.api.CoeurApi.clanAUnCoeur(UUID)` (lu par Clans ; cœur dans la zone de son armoire).

**Autres mods** (serveur seulement) : Loot 0.9.12 (conteneurs T1 à T7 et salles à carte : une fois par remplissage, au
premier qui ouvre), Quêtes 1.9.5 (quête principale, ventes d'objets qu'aucun marchand ne vend, armes fabriquées),
Matchs 0.14.1 (ClashWar : podium et participation, ×2, au moins 4 clans différents).

**Reste à faire** : le wipe global de saison (double panneau) et la liste des objets à vie hors du monde (§8) ; le
contrôle du même PC par Spartan pour les kills ; prix et craft du cœur (aujourd'hui donné par le staff) ; oil rig et
autres events ; couper le « Bonus de loot » de la boutique pendant la saison (§10) ; staff hors des clans classés ;
afficher le classement sur un mur du spawn.
