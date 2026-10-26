# Audit complet du serveur HeraliZ — 26/10

Audit en **lecture seule** : aucun fichier du projet n'a été changé (sauf ce rapport), rien n'a été compilé, **rien n'a été
testé en jeu**. Les chemins sont donnés à partir de la racine du projet (dossier `HeraliZ`).

Comment lire les mentions :
- **VÉRIFIÉ** : un deuxième relecteur a relu le code et confirmé le problème (et parfois corrigé sa gravité).
- **NON VÉRIFIÉ** : vu par un seul relecteur. Probable, mais à confirmer avant de corriger.
- Les numéros `A123` / `T-xxx` renvoient aux données complètes de l'audit (fichier `fusion.json` du dossier de travail de
  l'audit, hors projet).

---

**Corrigé le 26/10 (soir), rien testé en jeu** : A42 (sacs à dos), A68 et A69 (salles à carte et portes), A78
(`/inspecter`), A130 et A131 (groupes CRac : tag jamais vide, sauvegarde protégée, 8 membres), A324 (Spartan refuse
les connexions si le secret du launcher manque ; mise en ligne par GitHub, qui écrit les secrets), A313
(`zones.json` jamais écrasé par la mise en ligne). **A245 / A144 (ClashWar) gardé tel quel : choix du propriétaire**
(un clan qui prend plusieurs places est simplement le meilleur).

## 1. Résumé en 10 lignes

1. Le serveur est **globalement solide** : paquets des joueurs revérifiés partout, écritures de fichiers sûres, aucune
   boucle « acheter → transformer → revendre » qui rapporte (0 sur 159 articles, 0 sur 307 recettes).
2. Après fusion des doublons : **332 problèmes** ; **6 critiques et 24 hauts, tous vérifiés** ; 145 moyens, 157 bas.
3. Urgent n° 1 : la mise en ligne à la main **efface les secrets de Spartan** (A324) : le jeton du launcher n'est plus
   vérifié, tout le monde entre, plus d'alertes Discord.
4. Urgent n° 2 : **duplication par les sacs à dos** (A42) : la sauvegarde réécrit un sac qu'on n'a plus sur le dos.
5. Urgent n° 3 : **salles à carte ouvertes sans carte** (A68) en se collant à la porte, et portes qui restent ouvertes
   pour toujours (A69).
6. Urgent n° 4 : un clan qui reprend un ancien tag reçoit **un groupe CRac sans tag : la sauvegarde plante** (A130).
7. Urgent n° 5 : **un seul clan touche les primes du ClashWar** (7 000 à 8 750 hérals par soirée) en inscrivant 2 équipes
   à lui (A245). Juste derrière : `/inspecter` qui peut écraser l'inventaire d'un joueur avec une vieille copie (A78).
8. Saison : les drapeaux posés en jeu sont **effacés à chaque mise en ligne** (A313), les quêtes « Fouiller N caches » ne
   peuvent jamais avancer (A116), les points PvP n'ont **aucun plafond** (A314).
9. Perfs : pas de bug « lag majeur » prouvé, mais les **outils de mesure mentent** (/diag et /diag client) : à corriger
   avant tout banc. Côté joueurs, les conteneurs de loot HD et les grosses textures sont les vrais poids.
10. Anticheat : bon sur le mouvement et l'anti-ESP des joueurs ; **rien sur les tirs TaCZ** (aimbot), fake lag de 1 s
    possible, et la plupart des contrôles du client se contournent. Pas de pilote noyau : priorité au serveur.

---

## 2. Tableau de bord

**332 problèmes après fusion** (369 constats bruts, 37 doublons regroupés). Aucun des 46 critiques/hauts revérifiés
(39 de la première passe, 7 des nouveaux thèmes) n'a été écarté ; 20 ont été ramenés de « haut » à « moyen ».

| Gravité | Vérifiés | Non vérifiés | Total |
|---|---|---|---|
| Critique | 6 | 0 | 6 |
| Haute | 24 | 0 | 24 |
| Moyenne | 30 | 115 | 145 |
| Basse | 2 | 155 | 157 |
| **Total** | **62** | **270** | **332** |

Par catégorie (chaque case : vérifiés / non vérifiés) :

| Catégorie | Critique | Haute | Moyenne | Basse | Total |
|---|---|---|---|---|---|
| Bugs | 2 / 0 | 9 / 0 | 3 / 17 | 1 / 16 | 48 |
| Performances serveur | – | – | 3 / 19 | 1 / 28 | 51 |
| Propreté du code | – | – | 0 / 4 | 0 / 42 | 46 |
| Anticheat | 1 / 0 | 5 / 0 | 4 / 10 | 0 / 8 | 28 |
| Données et fichiers | 1 / 0 | 3 / 0 | 5 / 9 | 0 / 8 | 26 |
| Performances joueurs | – | – | 5 / 8 | 0 / 10 | 23 |
| Économie | 1 / 0 | 1 / 0 | 3 / 9 | 0 / 5 | 19 |
| Équilibrage et loot | – | 2 / 0 | 1 / 9 | 0 / 6 | 18 |
| Commandes | – | – | 1 / 8 | 0 / 9 | 18 |
| Interfaces | – | – | 2 / 5 | 0 / 11 | 18 |
| Sécurité | – | 1 / 0 | 1 / 6 | 0 / 8 | 16 |
| Conflits entre mods | – | 2 / 0 | 2 / 5 | 0 / 1 | 10 |
| Duplication / perte | 1 / 0 | 1 / 0 | 0 / 5 | 0 / 2 | 9 |
| Architecture | – | – | 0 / 1 | 0 / 1 | 2 |

Ce que l'audit a couvert : les 19 modules livrés relus un par un, puis 12 thèmes transversaux (duplication, économie,
équilibre du loot, commandes, logique de jeu, interfaces, perfs serveur, perfs joueurs, conflits entre mods, cohérence
des données, code et conception de l'anticheat). Non audités : `events` (boss) et `capture` (pas livrés).

---

## 3. Problèmes critiques et hauts (tous VÉRIFIÉS dans le code)

### 3.1 Duplication, perte d'objets et argent gratuit

**A42 — CRITIQUE — Sac à dos : duplication ou perte de tout le contenu**
- Où : `modules\sacs\src\commun\java\com\mrcrayfish\backpacked\mixin\common\PlayerMixin.java` l.101-106.
- Quoi : le joueur garde en mémoire le dernier sac ouvert. À chaque sauvegarde (déconnexion, sauvegarde auto, mort sur
  l'écran de mort), ce contenu est réécrit dans le sac, même si le sac a été donné à quelqu'un entre-temps (touches 1-9,
  cadavre).
- Pourquoi grave : duplication répétable d'un sac plein (munitions, composants : Spartan ne les voit pas), ou objets
  effacés chez le nouveau porteur.
- Correction : ne réécrire que si le sac est encore sur le dos (`getBackpackStack(player) == inv.getBackpackStack()`),
  sinon oublier sans écrire. Quelques lignes. Plan d'essai à 2 comptes dans le rapport du relecteur.

**A78 — CRITIQUE — `/inspecter` : une vieille copie écrase l'inventaire d'un joueur**
- Où : `modules\monde\src\serveur\java\fr\heralisz\utils\inspecter\Cible.java` l.80, `Inspecter.connexion`.
- Quoi : ouvrir la vue « sac » ou « base » d'un joueur déconnecté qui n'en a pas laisse une copie en mémoire. Plus tard,
  la même copie est reprise, et fermer la fenêtre (même en lecture seule) réécrit le fichier du joueur avec son ancien
  inventaire.
- Pourquoi grave : objets perdus ou en double, déclenché par un simple usage du staff.
- Correction : à la connexion, invalider toute copie hors ligne ; jeter une copie qu'aucun menu n'utilise ; ne
  sauvegarder que si quelque chose a changé, jamais depuis une vue en lecture seule.

**A245 (+ A144) — CRITIQUE — ClashWar : un clan seul touche toutes les primes**
- Où : `modules\clashwar\src\serveur\java\fr\heraliz\clashwar\tournoi\Recompenses.java` l.37-68,
  `Tournoi.java` l.413 et 470, `serveur\heraliz_clashwar\clashwar.json` (`min_equipes` 2).
- Quoi : un clan peut inscrire plusieurs équipes d'un joueur ; le départ compte les équipes, pas les clans. Seuls les
  points de saison exigent 4 clans.
- Pourquoi grave : 7 000 hérals (2 équipes) à 8 750 (4 équipes) + clés d'event + titre, deux soirs par semaine, sans
  adversaire. Un vérificateur l'a classé « haut » (il faut que personne d'autre ne s'inscrive), l'autre « critique » :
  gardé critique (argent créé à partir de rien).
- Correction : aucune prime, clé ni place au classement sous un minimum de clans différents (réglage à choisir : 2, 3
  ou 4) ; une seule place payée par clan ; annuler le tournoi s'il n'y a qu'un clan.

**A240 (+ A16, A163) — HAUT — Objets à vie perdus à la déconnexion ou au redémarrage**
- Où : `modules\armes\src\serveur\java\fr\heraliz\tactique\serveur\ObjetsAVieServeur.java` l.85 et 269 ;
  `modules\pnj\...\MenuRecycleurServeur.java` l.492.
- Quoi : à la déconnexion, Minecraft sauvegarde le joueur PUIS ferme son écran. Un couteau ou un skin à vie tenu au
  curseur, ou posé dans une grille d'artisanat, le recycleur ou l'atelier, n'est gardé qu'en mémoire (ou rangé dans un
  joueur déjà sauvegardé). Au redémarrage (y compris `/redemarrage`), il disparaît.
- Pourquoi grave : perte d'objets payés.
- Correction : dans `deconnexion()`, ramener curseur, grille 2×2 et cases temporaires dans l'inventaire AVANT la
  sauvegarde ; un objet jeté par un joueur déjà déconnecté va dans un petit fichier hors du monde, relu à la connexion.

**A52 (+ A241) — HAUT — Objets déposés dans un conteneur de loot détruits**
- Où : `modules\loot\src\serveur\java\fr\heraliz\loot\RotationLoot.java` l.961 (et la caisse de ravitaillement du clan).
- Quoi : les conteneurs T1-T6 acceptent les dépôts ; la règle des restes les vide 5 min plus tard (15 pour la caisse),
  objets à vie compris. N'importe qui peut aussi prendre un objet à vie posé là.
- Correction : conteneurs « prise seulement » (refuser tout dépôt, Maj+clic et touches 1-9 compris).

**A158 — HAUT — Le `/wipe` garde la grille d'artisanat**
- Où : `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Commandes.java` l.394-441.
- Quoi : la grille 2×2 et l'écran ouvert ne sont pas vidés ; à l'expulsion, leur contenu tombe au sol et survit au wipe
  (jusqu'à 8 cases). Les joueurs hors ligne jouent aussi 2 s avec leur ancien inventaire à leur reconnexion.
- Correction : vider ces cases (en gardant les objets à vie), `closeContainer()` avant l'expulsion ; remise à zéro au tick
  suivant la connexion au lieu de 40 ticks.

**A187 (+ A189) — HAUT — Casino : gros gains jetés au sol**
- Où : `modules\boutique\src\serveur\java\fr\heraliz\casino\economy\ChipsServeur.java` l.105-116.
- Quoi : la plus grosse capsule vaut 10 ; un inventaire vide tient au plus 23 040 capsules. Une roulette gagnée à la
  mise max donne 360 000 (563 piles, dont plus de 500 au sol). `/casino jetons donner` accepte 1 milliard (1,5 million
  d'objets en un tick : gel du serveur).
- Correction : ranger dans l'inventaire, le reste va « en attente à la caisse » (jamais au sol) ; plafonner la commande à
  100 000 et la noter dans le journal du staff.

**A190 — HAUT — Caisse du casino : capsules prises pour rien**
- Où : `modules\boutique\src\serveur\java\fr\heraliz\casino\block\entity\CashierBlockEntityServeur.java` l.113-141 ;
  article `serveur\config\heralizcasino\casino.json` l.253.
- Quoi : « Émote au hasard » prend les capsules même quand le joueur a déjà toutes les émotes (jusqu'à 32 000 d'un coup).
- Correction : compter ce que la commande a vraiment donné et rembourser le reste (pour les émotes, passer par
  `EmotesApi.debloquerAuHasard`).

**A198 — HAUT — Caisses (boutique) : récompense au sol sans propriétaire**
- Où : `modules\boutique\src\serveur\java\fr\heraliz\crates\loot\RewardHelper.java` l.64, `CaseOpening`.
- Quoi : inventaire plein = l'objet gagné tombe au sol, ramassable par n'importe qui (couteau d'une clé Gemme achetée).
  Joueur mort pendant la roue ou déconnecté : objet perdu.
- Correction : ce qui ne rentre pas reste dans `PendingRewards` avec un message ; ne rien donner à un joueur mort ou
  pendant la déconnexion (remise à la réapparition ou à la connexion).

**A13 — HAUT — Camos à vie : un fichier abîmé efface les collections de tout le monde**
- Où : `modules\armes\src\serveur\java\fr\heralisz\camo\serveur\CollectionCamos.java` l.91.
- Quoi : si `collections.json` est illisible, le serveur part d'une liste vide puis l'écrit au premier camo ajouté.
- Correction : refuser toute sauvegarde tant que le fichier n'a pas été lu ; copie de secours datée, jamais écrasée.

**A119 — HAUT — `clans.json` lu en partie : coffres des clans perdus**
- Où : `modules\clans\src\serveur\java\fr\heralisz\clans\server\CoffreClan.java` l.366, `ClanStore.charger` l.276-330.
- Quoi : un clan sauté à la lecture disparaît de `coffres.dat` à la sauvegarde suivante, sans copie de secours.
- Correction : ne jamais retirer le coffre d'un clan « inconnu » (la dissolution s'en charge déjà) ; ne rien réécrire
  tant que la lecture est incomplète ; copie `coffres.dat.bak`.

**A30 — HAUT — Armes de mêlée « à vie » qui s'usent et disparaissent**
- Où : `modules\armes\src\serveur\java\fr\heralisz\tactical\item\MeleeItemServeur.java` l.67 ;
  `serveur\config\heralisz_tactical-common.toml` l.8.
- Quoi : chaque coup retire 1 point ; une batte de la caisse Gemme casse en 240 coups (≈ 120 zombies), sans réparation
  possible.
- Correction : pas d'usure pour un objet à vie (`ObjetsAVie.estAVie`), et `meleeItemConsumeDurability = false`.

### 3.2 Bugs : des règles du jeu qui ne marchent pas

**A68 — CRITIQUE — Salle à carte ouverte sans carte**
- Où : `modules\bunker\src\serveur\java\fr\heralisz\bunker\serveur\salle\SallesMonde.java` l.216-219.
- Quoi : la zone « encadrement de porte » est testée même porte fermée, des deux côtés : se coller contre la porte depuis
  le couloir l'ouvre en 1 s. Rester sur le seuil fait aussi réapparaître le loot sans fin.
- Pourquoi grave : salles, cartes d'accès et fragments ne servent plus à rien.
- Correction : ne compter l'encadrement que si la porte est déjà tenue ouverte, et compter le joueur sur le seuil comme
  occupant (la recharge du loot attend qu'il parte).

**A69 — HAUT — Porte à carte qui reste ouverte pour toujours**
- Où : `modules\bunker\src\commun\java\com\hbm_m\block\entity\doors\DoorBlockEntity.java` l.244-261 et 386-395 (code
  HBM, licence GPL : retouche minimale).
- Quoi : si le signal retombe pendant que la porte s'ouvre (plus de 25 ticks pour la plupart), elle finit de s'ouvrir et
  ne relit plus jamais le signal. Arrive en jeu normal (ressortir pendant la fermeture).
- Correction : à la fin de l'ouverture, refermer si le signal est retombé. À tester aussi sur une porte à levier.

**A130 — CRITIQUE — Groupe CRac sans tag : la sauvegarde plante**
- Où : `modules\bases\src\serveur\java\fr\heraliz\crac\heraliz\BasesClans.java` l.430-434 ; `Group.save` ;
  `data\SafeSavedData.java` l.162.
- Quoi : si le tag du clan est déjà pris (par exemple par la cabane d'essai d'un joueur en tutoriel), le groupe est créé
  avec un tag vide (`null`). La sauvegarde de tous les groupes plante ensuite (le serveur peut planter), et plus aucun
  clan ne peut poser d'armoire.
- Correction : ne jamais donner un tag `null` (tag libre dérivé, sinon vide), protéger `Group` et `GroupData`, et mettre
  la sauvegarde sous `try/catch`.

**A131 — HAUT — Le 8e membre d'un clan n'a pas de base**
- Où : `BasesClans.java` l.475 ; `modules\bases\src\commun\java\fr\heraliz\crac\Config.java` l.207 ;
  `serveur\defaultconfigs\crac-server.toml` l.106.
- Quoi : un clan monte à 8 membres, un groupe CRac est limité à 7. Le 8e est retiré de son ancien groupe et jamais
  ajouté : ni `/home`, ni réapparition, ni alertes de raid.
- Correction : `maxGroupSize = 8` (ou pas de limite pour les groupes de clan) et noter l'échec dans le journal.

**A143 — HAUT — Après le premier ClashWar, plus aucun largage ni hélicoptère**
- Où : `modules\evenements\src\serveur\java\fr\heraliz\evenements\serveur\Outils.java` l.165-178.
- Quoi : le tournoi terminé reste en mémoire ; le Directeur croit qu'un ClashWar est en cours jusqu'au redémarrage
  (jusqu'à ~7 largages et 4 hélicos perdus par jour).
- Correction : une méthode `Tournois.enCours()` (tournoi actif, pas une simulation) lue à la place de `courant()`.
  À faire avant la prochaine soirée ClashWar.

**A116 (+ A312) — HAUT — Quêtes de clan « Fouiller N caches » impossibles**
- Où : `modules\clans\src\serveur\java\fr\heralisz\clans\server\ClanEvents.java` l.70 et 425-450.
- Quoi : le code compte les blocs `cfm:loot_*`, qui n'existent plus ; les conteneurs sont `heraliz_loot:*`. Environ
  25 % de chances par jour qu'un clan tire une quête impossible.
- Correction : écouter `EvenementsLoot.ecouterFouille` (comme les quêtes du jour), une seule fois au démarrage.

**A313 — HAUT — La mise en ligne efface les drapeaux des territoires**
- Où : `serveur\config\heralisz_clans\zones.json` ; `Territoires.java` l.707-781.
- Quoi : les drapeaux posés en jeu ne sont gardés que dans ce fichier ; la copie du dépôt n'en a aucun. Chaque envoi de
  `serveur\config\` remet 13 zones sans drapeau : plus aucune capture.
- Correction immédiate : recopier le `zones.json` du Ptero dans le projet AVANT le prochain envoi. Ensuite : un fichier
  `heralisz_clans/zones_jeu.json` hors de `config/`, appliqué par-dessus (comme `marchands_jeu.json`).

**A43 — HAUT — Armure : un tir à la tête « contamine » les autres coups du même tick**
- Où : `modules\equipement\src\serveur\java\fr\heraliz\armory\compat\TaczCompat.java` l.37-39.
- Quoi : après un plomb dans la tête, tous les autres plombs du même tir (et les balles des autres tireurs ce tick-là)
  sont comptés « tête » : le gilet est ignoré.
- Correction : chaque balle qui ne touche pas la tête efface la note ; consommer la note au coup suivant.

### 3.3 Performances serveur

Aucun problème critique ou haut **prouvé** : les deux constats « hauts » de départ (budget des chemins des zombies,
Zombie Awareness) ont été ramenés à **moyen** par la vérification, faute de mesure. Le détail, les chiffres et le plan
de mesure sont au §5.

### 3.4 Performances joueurs

Aucun problème critique ou haut : les 5 constats vérifiés (conteneurs HD, atlas, textures, /diag client) sont **moyens**.
Voir le §5.

### 3.5 Conflits entre mods

**T-conf-1 — HAUT — geckolib222 refait les dégâts des tirs à la tête**
- Où : `serveur\mods-tiers\geckolib222-forge-1.20.1-4.4.7.jar`, classe cachée d'ArkaniaZ `CuriosEventHandler` ;
  notre réglage dans `modules\armes\src\serveur\java\fr\heraliz\tactique\serveur\DegatsArmes.java` l.123.
- Quoi : cet écouteur tourne sur le serveur et remultiplie chaque tir à la tête sur un joueur par « multiplicateur ÷ 2 ».
  Exemple : fusil 5,56 Rare prévu 8,84 à la tête, réel 6,6 ou 7,5 selon l'ordre de chargement ; au fusil à pompe, la
  tête fait moins que le corps. Il écrit aussi dans la console et pose le saignement (à garder).
- Correction : un mixin qui neutralise seulement ses `setAmount` (et ses messages console), jar intact ; garder la chance
  de saignement. Puis tir d'essai tête / corps.

(Le blocage des largages par le ClashWar, A143, est aussi un conflit entre deux modules : voir §3.2.)

### 3.6 Économie et saison

**A314 — HAUT — Points PvP de saison sans plafond**
- Où : `modules\clans\src\serveur\java\fr\heralisz\clans\saison\Saison.java` l.308-342 ; `Bareme.java`.
- Quoi : seuls freins : même clan, même IP, victime « équipée », 6 h par couple. Une victime sans clan ou dont le clan n'a
  pas de cœur ne perd rien. 5 tueurs et 10 complices = environ 300 points par jour ; une capture est plafonnée à 40.
- Correction : plafond par clan et par jour (ex. 30) et par victime (ex. 3), coefficient réduit si la victime n'a pas de
  clan ou de cœur, comparer aussi le PC (Spartan).

(L'argent gratuit du ClashWar, A245, est au §3.1.)

### 3.7 Loot

**A255 — HAUT — Un zombie de village lâche des composants Épiques par 1 à 7**
- Où : `outils\loot_zombies.py` l.70-72 et 160-171 ; tables écrites `serveur\config\heraliz_loot\coffres\zombies.json`
  (`[1, 7]`) et `zombies_militaire.json` (`[1, 6]`).
- Quoi : l'outil agrandit la quantité de TOUS les composants, niveaux II et III compris. Règle du propriétaire : un
  Épique d'un coup, 2 à 5 « exceptionnel ».
- Correction : ne pas agrandir `_ii` et `_iii` (II : 1-2, III : 1), puis relancer l'outil et `--simuler`.

### 3.8 Interfaces

Pas de problème critique ou haut : les chevauchements de HUD (coin haut-droit A296, « Raid block » sur la barre du cœur
A297) sont vérifiés mais classés **moyens** (gênants quelques secondes, rien n'est perdu). Voir §4.

### 3.9 Commandes et droits

**A279 — HAUT — Commandes des plugins (dont `/logout` d'AuthMe) hors du filtre combat / carte**
- Où : `modules\noyau\src\serveur\java\fr\heraliz\noyau\serveur\combat\CombatService.java` l.94 et 274-316.
- Quoi : seules les commandes Forge passent par le filtre. Sous Mohist, une commande de plugin ne lance pas
  `CommandEvent`. Un joueur en combat ou à terre tape `/logout` : AuthMe le fige et le protège 185 s, son tag expire, il
  revient avec son équipement.
- Vérification : un premier relecteur l'avait écarté, le second l'a confirmé dans le code. **À tester en jeu (5 min)** ;
  si confirmé, à passer en critique.
- Correction tout de suite : retirer `authme.player.logout` au groupe `default` dans LuckPerms. Ensuite : écouter aussi
  l'événement Bukkit `PlayerCommandPreprocessEvent` (par réflexion) avec les mêmes refus.

**A188 — HAUT — Tablette du Directeur (op 2) = commandes de la console**
- Où : `modules\boutique\src\serveur\java\fr\heraliz\casino\net\ReceptionServeur.java` l.33 ;
  `CasinoConfigServeur.applySection` ; `CashierBlockEntityServeur.java` l.113-133.
- Quoi : un op 2 (sans AuthMe) peut réécrire la section « caisse », dont les commandes jouées par la console à chaque
  achat (`op {player}`, `grade donner…`, `stop`).
- Correction : la Tablette ne doit jamais modifier `encaissements` ni `economie` (fichier du projet seulement) ; sinon op 4
  + AuthMe et liste blanche des commandes.

### 3.10 Anticheat

**A324 — CRITIQUE — La mise en ligne à la main efface les secrets de Spartan**
- Où : `docs\DEPLOIEMENT.md` l.36 (« envoyer serveur/config/ par-dessus ») ; `serveur\config\spartan-common.toml`
  l.181, 189 et 385 (marques `__SPARTAN_…__`) ; `modules\anticheat\src\serveur\java\com\Spartan\integrity\LauncherGate.java`
  l.104-113 ; `PorteJoueurs.jetonExige()` l.67.
- Quoi : les marques sont lues comme « pas de secret ». Alors le jeton du launcher n'est plus vérifié (tout le monde
  entre, avec n'importe quel lanceur), `tokenRequired = true` ne sert plus à rien, et les webhooks Discord sont vides.
  Rien ne prévient, sauf une ligne dans le journal.
- Correction : (1) DEPLOIEMENT.md : ne jamais écraser ce fichier sur le Ptero ; (2) un petit outil qui écrit une copie
  avec les vrais secrets lus dans un fichier local hors projet, et refuse s'il reste une marque ; (3) dans Spartan,
  **refuser les connexions** si `tokenRequired` est vrai et le secret vide (fermé par défaut).

**A326 — HAUT — Aucune détection sur les tirs TaCZ**
- Où : `modules\anticheat\src\serveur\java\com\Spartan\SpartanEvents.java` l.74 (contrôles de combat = corps à corps
  seulement).
- Quoi : aimbot, triggerbot, no-recoil : rien n'analyse les rotations au moment du tir. C'est la triche la plus rentable
  d'un serveur PvP aux armes à feu.
- Correction : nouveau contrôle serveur (rotations avant le tir, « snaps », part de tirs à la tête selon la distance,
  recul, tirs sur une cible que l'anti-ESP cachait). D'abord collecter sans alerte 1 à 2 semaines.

**A327 (+ A18) — HAUT — Fake lag : la cible est reculée jusqu'à 1 s**
- Où : `serveur\config\tacz-common.toml` l.50-54 (`ServerHitboxLatencyMaxSaveMs = 1000`) ;
  `modules\armes\src\commun\java\fr\heraliz\tactique\mixin\commun\TaczHitboxCorpsMixin.java` l.102 et 158.
- Quoi : le ping utilisé vient des réponses « keepalive », que le client peut retarder. Le tricheur touche des joueurs
  là où ils étaient 1 s plus tôt, déjà à couvert. Les marges de Spartan grandissent aussi avec ce faux ping.
- Correction : 350 ms max (7 ticks) ; Spartan compare ce ping à son propre ping par tick (`Transactions`) et alerte
  « FakeLag » si l'écart dépasse 150 ms pendant 10 s.

**A330 — HAUT — DLL de triche ignorées sur un simple nom**
- Où : `modules\anticheat\src\client\java\com\Spartan\scan\collect\InjectionCollector.java` l.45-48 et ~255 ;
  `NativeCollector.java` l.49-60.
- Quoi : un crochet d'affichage vers une DLL dont le nom contient « steam », « discord », « radeon »… est ignoré, et
  toute DLL rangée dans le Java du launcher (dossier où le joueur peut écrire) aussi.
- Correction : tout envoyer (chemin, empreinte, signature) et décider côté serveur ; comparer le Java du launcher à une
  liste officielle.

**A31 — HAUT — Soin lent fini en 3,5 s et recharge contournée (jeu modifié)**
- Où : `modules\armes\src\commun\java\fr\heralisz\tactical\item\ConsumableItem.java` l.90 (et `ThrowableItem`).
- Quoi : tous les soins sont le même objet ; Forge ne compare que le type pendant l'utilisation. On commence un bandage
  et on met un CMS dans la main : soin complet 3,7 fois plus vite, recharge contournée.
- Correction : redéfinir `canContinueUsing` pour comparer aussi l'identifiant du soin ou de la grenade.

---

## 4. Problèmes moyens et bas

Légende : **(V)** vérifié, **(NV)** non vérifié. La plupart des moyens et des bas n'ont **pas** été revérifiés : à
confirmer avant de corriger. Le détail (description, effet, correction proposée) est dans `fusion.json`.

### 4.1 Moyens (145)

**Duplication et perte d'objets** (5)

- (NV) A132 — Kit du tutoriel gardé : planches qui passent par l'armoire, armoire et caisse ramassées pendant le tour de fin — `modules\bases\src\serveur\java\fr\heraliz\crac\bases\Tutoriel.java` l.232
- (NV) A147 — Objet d'événement recréé après un plantage (contourne la protection « déjà vendu ») — `modules\evenements\src\serveur\java\fr\heraliz\evenements\serveur\ObjetsServeur.java` l.151
- (NV) A151 — Clés du ClashWar données avant d'être retirées de la liste sur le disque — `modules\clashwar\src\serveur\java\fr\heraliz\clashwar\tournoi\Recompenses.java` l.135
- (NV) A172 — Bons : un bon peut être rendu alors que le site va quand même verser les PB — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\BonServeur.java` l.528
- (NV) A194 — Gains en attente et état des machines enregistrés à part du joueur (fenêtre de double paiement si plantage) — `modules\boutique\src\serveur\java\fr\heraliz\casino\event\CommonEventsServeur.java` l.179

**Économie** (12)

- (V) A117 — Quêtes « Déposer N hérals » : récompense gratuite (dépôt puis retrait) (+ doublons A246, A315) — `modules\clans\src\serveur\java\fr\heralisz\clans\server\Quetes.java` l.368
- (V) A14 — Une recette changée en jeu (Registre de l'armurier) n'est jamais reportée dans le recycleur (+ doublon T-arch-3) — `modules\armes\src\serveur\java\fr\heraliz\tactique\armurier\ArmurierServeur.java` l.271
- (V) A254 — Les zombies ordinaires rapportent bien plus que « la moitié des conteneurs » : la référence de calcul est fausse (+ doublon A56) — `outils\loot_zombies.py` l.44
- (NV) A100 — Mila rachète une arme ruinée (0 %) ou une armure usée au même prix qu'une neuve (+ doublon A250) — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Marchands.java` l.242
- (NV) A109 — Les exécutions des armes (karambit, M9, batte…) ne vérifient jamais si le joueur les possède — `modules\survie\src\serveur\java\fr\heraliz\reanimation\execution\Executions.java` l.96
- (NV) A161 — Une progression illisible permet de refaire (et se faire payer) toutes les quêtes de Varga — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Donnees.java` l.208
- (NV) A165 — Échanges : une arme TaCZ usée est acceptée comme une neuve — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Echanges.java` l.273
- (NV) A173 — Boutique : une erreur 5xx du site est prise pour un refus, l'achat est oublié — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\BoutiqueServeur.java` l.241
- (NV) A191 — Passage capsules → hérals (et points de saison) via la caisse puis Mila (+ doublon A244) — `serveur\config\heralizcasino\casino.json`
- (NV) A200 — En créatif, les caisses s'ouvrent gratuitement avec de vraies récompenses (clés Gemme, grades, emotes) — `modules\boutique\src\serveur\java\fr\heraliz\crates\opening\CaseOpening.java` l.113
- (NV) A284 — Commandes op 2 qui créent de la valeur (hérals, clés payantes, capsules) sans plafond ni journal du staff — `modules\clans\src\serveur\java\fr\heralisz\economie\server\EconomieCommands.java` l.96
- (NV) A55 — /rarete en jeu change le prix de rachat sans contrôle de l'économie — `modules\loot\src\serveur\java\fr\heraliz\loot\RaretesServeur.java` l.153

**Équilibrage et loot** (10)

- (V) A45 — Sacs : on peut porter autant de sacs pleins qu'on veut dans l'inventaire (capacité multipliée, données très lourdes) — `serveur\config\backpacked.server.toml` l.67
- (NV) A121 — Points PVP de capture farmables entre deux clans complices — `modules\clans\src\serveur\java\fr\heralisz\clans\saison\Saison.java` l.271
- (NV) A248 — Quêtes du jour « livrer » : récompense bien plus forte que l'effort (3 composants III → 408 hérals en moyenne) — `outils\quetes_journalieres.py` l.76
- (NV) A256 — Fragments rouges et bleus : un zombie de village en donne bien plus que la fouille des zones militaires — `modules\loot\src\serveur\java\fr\heraliz\loot\ButinZombies.java` l.241
- (NV) A257 — 8 armes Légendaires presque sans munitions : .50 BMG, 40 mm et roquettes RPG introuvables — `outils\loot_armes.py` l.163
- (NV) A258 — Sacs à dos : la part « 62 % Commun » ne s'applique pas, les sacs trouvés sont bien meilleurs que prévu — `outils\loot_armes.py` l.74
- (NV) A259 — Le levier FACTEUR_TOTAL ne peut plus remplir davantage de T5 et T6 à l'aérodrome — `outils\loot_armes.py` l.557
- (NV) A316 — Quêtes de Varga : 10 points de saison chacune, sans plafond, multipliés par le nombre de membres — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Moteur.java` l.237
- (NV) A317 — Une mort fait perdre des points même quand le tueur n'en gagne aucun (règle de SAISON_CLANS non appliquée) — `modules\clans\src\serveur\java\fr\heralisz\clans\saison\Saison.java` l.334
- (NV) A319 — Salles à carte : une carte rouge vaut jusqu'à 240 points de saison, sans plafond ni baisse au 2e passage — `modules\loot\src\serveur\java\fr\heraliz\loot\RotationLoot.java` l.939

**Bugs** (20)

- (V) A19 — Registre de l'armurier : l'objet choisi garde ses munitions, son usure et ses marques dans la recette — `modules\armes\src\serveur\java\fr\heraliz\tactique\armurier\ArmurierServeur.java` l.339
- (V) A225 — Discord : une seule limite de 20 messages par minute pour TOUT ; les alertes graves, les captures et les annonces de bans sont jetées en silence — `modules\anticheat\src\serveur\java\com\Spartan\alert\DiscordWebhook.java` l.36
- (V) T-perf-4 — Rapport /diag client : « 1 % le plus lent » et durée des images mal mesurés — `modules\noyau\src\client\java\fr\heraliz\noyau\client\diag\DiagClient.java` l.147
- (NV) A107 — Un joueur à terre (jeu modifié) peut cacher des objets à la fouille dans sa grille 2×2 ou sous sa souris — `modules\survie\src\serveur\java\fr\heraliz\reanimation\core\DownedManagerServeur.java` l.268
- (NV) A133 — L'armoire de la cabane d'essai lance le compteur des 48 h de protection de la vraie base — `modules\bases\src\serveur\java\fr\heraliz\crac\heraliz\Purge.java` l.217
- (NV) A146 — L'arène n'est pas nettoyée entre deux manches (fumigènes, feu, grenades) — `modules\clashwar\src\serveur\java\fr\heraliz\clashwar\tournoi\MatchCw.java` l.299
- (NV) A148 — Largage : le suivi s'arrête avant que la caisse disparaisse (points perdus) — `modules\evenements\src\serveur\java\fr\heraliz\evenements\serveur\Largage.java` l.159
- (NV) A159 — Le Registre du marchand transforme le recycleur en vendeur vide — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\EditionMarchands.java` l.127
- (NV) A176 — Menu /kit : au-delà de 16 kits, les derniers (Roi de la Zone, staff) disparaissent sans message — `modules\boutique\src\commun\java\fr\heraliz\grades\reseau\ReseauGrades.java` l.306
- (NV) A199 — Objets gagnés perdus après un plantage (clé déjà prise, joueur pas sauvegardé) (+ doublon A242) — `modules\boutique\src\serveur\java\fr\heraliz\crates\opening\CaseOpening.java` l.229
- (NV) A203 — Compte de clés « fermé » jusqu'au redémarrage, même après réparation du fichier — `modules\boutique\src\serveur\java\fr\heraliz\crates\cles\ComptesCles.java` l.112
- (NV) A224 — Anti-ESP : une porte ouverte ou un mur cassé est « oublié » pour les paires qui ne sont pas recalculées ce tick-là (joueur invisible jusqu'à 1 s) — `modules\anticheat\src\serveur\java\com\Spartan\esp\VisibilityEngine.java` l.295
- (NV) A226 — Sans config/spartan/officiel.json, chaque joueur honnête lancé par le launcher est signalé « Triche probable » — `modules\anticheat\src\serveur\java\com\Spartan\scan\ScanAnalyzer.java` l.236
- (NV) A228 — Couper puis rallumer Spartan à chaud (enabled) : file des paquets pleine, puis fausse alerte « inondation de paquets » pour tous les joueurs — `modules\anticheat\src\serveur\java\com\Spartan\check\movement\PacketObserver.java` l.89
- (NV) A53 — Fouille des gros véhicules T1 qui se referme toute seule — `modules\loot\src\serveur\java\fr\heraliz\loot\RotationLoot.java` l.1116
- (NV) A79 — Réserves des zombies : elles se rechargent même quand les zombies sont vivants — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\ReservesZombies.java` l.67
- (NV) A92 — Véhicules posables au spawn et dans le monde du tutoriel ; liste des mondes interdits périmée — `modules\monde\src\serveur\java\fr\heraliz\vehicules\Regles.java` l.83
- (NV) A93 — Extraction : un nom de zone de plus de 64 caractères casse l'envoi de la liste aux joueurs — `modules\monde\src\serveur\java\com\heraliz\extraction\serveur\ExtractionCommand.java` l.129
- (NV) A98 — Exposition au gaz et à la radiation gardée hors de la carte : poison et flétrissement au retour — `modules\monde\src\serveur\java\fr\heraliz\decors\serveur\ZonesServeur.java` l.128
- (NV) A99 — Atelier de réparation : l'arme posée tombe au sol si le joueur se déconnecte ou plante (+ doublon A243) — `modules\monde\src\serveur\java\fr\heraliz\usure\MenuReparationServeur.java` l.211

**Données et fichiers** (14)

- (V) A118 — saison_etat.json illisible : nouvelle saison lancée seule, points perdus et fichier écrasé — `modules\clans\src\serveur\java\fr\heralisz\clans\saison\SaisonDonnees.java` l.97
- (V) A2 — Écran Informations (règles, commandes) jamais mis à jour chez les joueurs existants — `modules\interface\src\main\java\fr\heraliz\interfaces\client\Informations.java` l.61
- (V) A44 — Emotes débloquées : un fichier illisible est écrasé au déblocage suivant (perte des emotes achetées) — `modules\emotes\src\serveur\java\fr\heraliz\emote\serveur\Deblocages.java` l.133
- (V) A54 — Rareté écrite sur les objets des T7 : pas d'empilement et prix figé — `modules\loot\src\serveur\java\fr\heraliz\loot\TablesCoffres.java` l.336
- (V) T-arch-1 — verifier.py ne contrôle pas la chaîne des générateurs (recette changée sans relancer le recycleur : rien ne le voit) — `outils\verifier.py` l.179
- (NV) A150 — ClashWar : fichiers d'état et de classement illisibles écrasés (clés et classement perdus) — `modules\clashwar\src\commun\java\fr\heraliz\clashwar\tournoi\CwEtat.java` l.73
- (NV) A160 — Un fichier de PNJ illisible efface les quêtes en cours de tous les joueurs — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Moteur.java` l.324
- (NV) A175 — definitions.json illisible : les grades créés en jeu sont effacés de grades.json à la première écriture — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\RegistreGrades.java` l.139
- (NV) A192 — Réglages faits à la Tablette écrasés à chaque mise en ligne — `modules\boutique\src\serveur\java\fr\heraliz\casino\config\CasinoConfigServeur.java` l.112
- (NV) A202 — Butin de secours codé en dur avec des objets de Minecraft (totems, élytres, épée en netherite) qui peut revenir tout seul — `modules\boutique\src\serveur\java\fr\heraliz\crates\loot\LootManager.java` l.112
- (NV) A209 — Sourdines vocales perdues si leur fichier est illisible — `modules\voix\src\serveur\java\com\caracrea\server\VoiceMutes.java` l.61
- (NV) A247 — Ravitaillement des clans : 11 entrées (30 % du poids) visent des objets qui n'existent pas — `serveur\config\heralisz_clans\ravitaillement.json` l.14
- (NV) T-arch-2 — Le contrôle des caisses laisse passer une arme ou un accessoire retiré des gunpacks — `outils\caisses.py` l.350
- (NV) T-arch-4 — Tables de loot : l'outil lit et réécrit ses propres fichiers, et les notes citent un outil disparu — `modules\loot\src\serveur\resources\data\heraliz_loot\coffres\t1.json` l.2

**Sécurité** (7)

- (V) A34 — Un paquet de mêlée trafiqué remplit le journal du serveur sans limite — `modules\armes\src\commun\java\fr\heralisz\tactical\network\message\CMeleeAttackRequest.java` l.46
- (NV) A134 — Un membre avec « Casser » mais sans « Gérer la base » peut ramasser l'armoire et retirer la base — `modules\bases\src\serveur\java\fr\heraliz\crac\network\C2SPickupTraitement.java` l.144
- (NV) A152 — Banc : nettoyage final trop large, et largages des bots qui laissent du vrai butin — `modules\banc\src\serveur\java\fr\heraliz\banc\Scenario.java` l.228
- (NV) A174 — Pas de limite de cadence pour /boutique, les achats et /kit vers le site et HeraliBot — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\BoutiqueServeur.java` l.187
- (NV) A177 — Panel du staff : le mode spectateur laisse revenir en survie à l'endroit atteint (dans une base, par exemple) — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\PanelStaff.java` l.407
- (NV) A204 — Clés et butin modifiables avec le seul niveau op 2, sans contrôle AuthMe — `modules\boutique\src\serveur\java\fr\heraliz\crates\command\HeralizCommand.java` l.73
- (NV) A212 — Le mute du panel du staff (LiteBans) ne coupe pas la voix — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\PanelStaff.java` l.345

**Anticheat** (14)

- (V) A32 — Le serveur accepte des cibles de mêlée presque tout autour du joueur (demi-cône de 100°) : marge pour une « kill aura » — `modules\armes\src\serveur\java\fr\heralisz\tactical\item\MeleeItemServeur.java` l.90
- (V) A325 — Le jeton du site prouve seulement un compte, pas le launcher : contournement complet du launcher — `site\extension\heraliz\src\Controllers\JetonController.php` l.26
- (V) A328 — Le défi d'intégrité se calcule sans faire tourner le vrai code, et un mauvais numéro de version coupe tous les défis — `modules\anticheat\src\serveur\java\com\Spartan\integrity\ChallengeManager.java` l.89
- (V) A329 — Les signatures cherchées (classes, programmes, DLL, Mixins connus) sont envoyées au client à chaque contrôle — `modules\anticheat\src\serveur\java\com\Spartan\scan\ScanManager.java` l.227
- (NV) A123 — XP de clan et quêtes « Tuer N joueurs » sans les protections de la saison (+ doublon A318) — `modules\clans\src\serveur\java\fr\heralisz\clans\server\ClanEvents.java` l.244
- (NV) A211 — Position exacte des membres d'un groupe vocal envoyée partout sur la carte — `modules\voix\src\serveur\java\com\caracrea\server\VoiceServer.java` l.699
- (NV) A227 — « TricheMenu » : Maj droite utilisée pour le Maj+clic dans l'inventaire ou un coffre = alerte + capture d'écran d'un joueur honnête — `modules\anticheat\src\client\java\com\Spartan\Client\TouchesSuspectes.java` l.99
- (NV) A230 — Joueur qui redevient « non vérifié » en cours de partie (/logout AuthMe, AuthMe injoignable un instant) : réponses jetées, puis fausses alertes ; AuthMe en panne = exceptions à chaque tick pour chaque joueur — `modules\anticheat\src\serveur\java\com\Spartan\integrity\PorteJoueurs.java` l.281
- (NV) A331 — Speedhack d'environ +30 % jamais signalé (marge par tick + tolérance), et pas de renvoi en arrière — `modules\anticheat\src\serveur\java\com\Spartan\check\movement\SpeedCheck.java` l.48
- (NV) A332 — Anti-ESP limité aux joueurs : un joueur caché reste repérable par son véhicule ; aucun anti-xray — `modules\anticheat\src\serveur\java\com\Spartan\mixin\ServerPlayerTrackingMixin.java` l.19
- (NV) A333 — Ban PC basé sur des identifiants envoyés par le client et faciles à changer — `modules\anticheat\src\client\java\com\Spartan\Utils\ComponentInfo.java` l.16
- (NV) A334 — Capture d'écran prévisible (paquet explicite + 0,5 s de délai) et image non liée à la demande — `modules\anticheat\src\serveur\java\com\Spartan\capture\CaptureManager.java` l.151
- (NV) A335 — Launcher : archive app.asar non scellée et Gardien coupé — `launcher\electron-builder.yml` l.14
- (NV) A90 — Train : on peut sauter 2 voitures d'un coup avec un client modifié — `modules\monde\src\serveur\java\fr\heralisz\utils\train\VoyageursServeur.java` l.74

**Commandes** (9)

- (V) A278 — /cracbuild : outil de débogage livré, un op 2 peut effacer, blinder ou ouvrir toutes les bases autour de lui (+ doublon A135) — `modules\bases\src\serveur\java\fr\heraliz\crac\build\world\BuildDebugCommand.java` l.46
- (NV) A101 — Réponses de /zones et /brume invisibles pour le staff (sendCommandFeedback=false) (+ doublon A281) — `modules\monde\src\serveur\java\fr\heraliz\decors\serveur\MeteoServeur.java` l.161
- (NV) A122 — /territoire capturer (niveau 2) donne de vrais points de saison (+ doublon A283) — `modules\clans\src\serveur\java\fr\heralisz\clans\territory\Territoires.java` l.818
- (NV) A162 — /quete donner + /quete finir (op 2) créent des hérals sans limite — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Commandes.java` l.347
- (NV) A280 — Quêtes du jour : livraison et récompense possibles sur la carte, en combat, à terre et en match — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Journalieres.java` l.248
- (NV) A282 — Commandes du staff destructrices sans confirmation (sélecteurs @a acceptés, blocs de la carte écrasés) — `modules\bases\src\serveur\java\fr\heraliz\crac\bases\EffacerBase.java` l.47
- (NV) A286 — /suicide sur la carte : retour gratuit au spawn qui contourne « on ne quitte la carte que par l'extraction » — `modules\monde\src\serveur\java\fr\heralisz\utils\serveur\Suicide.java` l.35
- (NV) A287 — /clan don marche sur la carte : la valeur du loot Légendaire est mise à l'abri sans extraction — `modules\clans\src\serveur\java\fr\heralisz\clans\saison\SaisonCommandes.java` l.151
- (NV) A88 — /zombies banc : jusqu'à 3 000 zombies gardés à jamais, dès la permission 2 (+ doublon A288) — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\CommandeZombies.java` l.343

**Conflits entre mods ou systèmes** (7)

- (V) A0 — Panel du staff : actions refusées sur la carte pour un staff qui n'est pas op (+ doublons A285, T-conf-3) — `modules\noyau\src\serveur\java\fr\heraliz\noyau\serveur\combat\CombatService.java` l.311
- (V) A33 — Le feu des molotovs n'a pas d'auteur : pas de tag de combat, pas de kill crédité, zones sans PvP ignorées — `modules\armes\src\serveur\java\fr\heralisz\tactical\entity\sp\SpEffectCloudEntityServeur.java` l.35
- (NV) A108 — Le « coup d'essai » des exécutions passe dans l'anticheat Spartan : refus à tort et fausses alertes KillAura — `modules\survie\src\serveur\java\fr\heraliz\reanimation\execution\Executions.java` l.465
- (NV) A57 — Nourriture achetée hors loot : 2 chances sur 3 d'être pourrie (ex. MRE du casino) — `modules\loot\src\serveur\java\fr\heraliz\loot\EtatNourriture.java` l.45
- (NV) A59 — Une seule erreur de lecture des claims de CRac coupe les exceptions « base » jusqu'au redémarrage — `modules\loot\src\serveur\java\fr\heraliz\loot\ConteneursVanilla.java` l.176
- (NV) A70 — Barricade : fabricable, posable partout (spawn compris), sans limite, jamais retirée — `modules\bunker\src\commun\java\fr\heralisz\bunker\barricade\BarricadeRegistry.java` l.365
- (NV) A91 — Gaz des frappes : les masques à gaz de l'Armurerie ne protègent qu'en partie — `modules\monde\src\serveur\java\fr\heraliz\airstrike\entity\EffectCloudEntityServeur.java` l.106

**Performances serveur** (22)

- (V) A1 — /diag ne voit pas le travail fait entre deux ticks : TPS trop optimiste, lags invisibles (+ doublon A270) — `modules\noyau\src\serveur\java\fr\heraliz\noyau\serveur\diag\Diag.java` l.160
- (V) A264 — Budget des chemins des zombies compté en nombre, pas en temps : jusqu'à 40 recherches A* par tick — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\CheminsZombies.java` l.51
- (V) A265 — Zombie Awareness avec des réglages lourds, qui refait en plus le travail de nos StimuliZombies (+ doublon T-conf-2) — `serveur\config\zombieawareness\General.toml` l.30
- (NV) A120 — Chaque opération de banque réécrit 3 gros fichiers sur le fil du serveur (spammable) — `modules\clans\src\serveur\java\fr\heralisz\clans\server\ClanService.java` l.885
- (NV) A145 — Début et fin du tournoi : tous les coffres écrits sur le disque dans le même tick — `modules\clashwar\src\serveur\java\fr\heraliz\clashwar\tournoi\Tournoi.java` l.456
- (NV) A149 — Tirage du point de largage : chargement de tronçons sur le fil serveur — `modules\evenements\src\serveur\java\fr\heraliz\evenements\serveur\Largage.java` l.237
- (NV) A164 — « Va voir le Commandant Varga » reste affiché à vie pour les joueurs sans base, avec un parcours de toutes les entités du monde — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Accueil.java` l.159
- (NV) A178 — Recharges des kits et du /tpa : fichiers jamais nettoyés, réécrits en entier sur le fil du serveur (2 fois par /tpa) — `modules\boutique\src\serveur\java\fr\heraliz\grades\serveur\Recharges.java` l.528
- (NV) A210 — Un joueur peut faire renvoyer la liste vocale à tout le monde à chaque tick — `modules\voix\src\serveur\java\com\caracrea\server\VoiceServer.java` l.548
- (NV) A214 — Port UDP de la voix absent du guide de mise en ligne, repli coûteux — `modules\voix\src\serveur\java\com\caracrea\server\VoiceServer.java` l.723
- (NV) A229 — Mouvement : une vingtaine de recherches dans le monde (blocs et entités) à chaque position reçue, même quand tout est normal — `modules\anticheat\src\serveur\java\com\Spartan\check\movement\MovementEngine.java` l.633
- (NV) A231 — Anti-duplication : toutes les 20 s, des milliers d'objets sont reconstruits (ItemStack.of) dans le même tick pour lire le contenu des sacs (+ doublon A267) — `modules\anticheat\src\serveur\java\com\Spartan\check\item\AntiDupe.java` l.137
- (NV) A266 — Une dizaine de travaux « une fois par seconde » tombent sur le même tick (pic toutes les 20 ticks) — `modules\monde\src\serveur\java\fr\heraliz\decors\serveur\ZonesServeur.java` l.126
- (NV) A268 — Quêtes : toutes les 30 s, les fichiers JSON des joueurs modifiés sont écrits sur le fil du serveur — `modules\pnj\src\serveur\java\fr\heraliz\quetes\serveur\Donnees.java` l.175
- (NV) A269 — Rotation du loot : à chaque sauvegarde, tout l'état des conteneurs actifs est sérialisé et compressé sur le fil serveur — `modules\loot\src\serveur\java\fr\heraliz\loot\RotationLoot.java` l.210
- (NV) A272 — Rien n'empêche la règle doMobSpawning de repasser à true sur la carte (coût doublé, jusqu'à 1 500 monstres) — `modules\monde\src\serveur\java\fr\heralisz\utils\cycle\CycleServeur.java` l.150
- (NV) A80 — Les zombies du peuplement sont gardés sur le disque quand le tronçon se décharge — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\Peuplement.java` l.266
- (NV) A81 — Peuplement : toutes les apparitions de tous les joueurs se calculent dans le même tick — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\Peuplement.java` l.138
- (NV) A82 — Borne de départ : jusqu'à 128 lectures du disque par tick et par joueur — `modules\monde\src\serveur\java\fr\heralisz\utils\spawn\DepartServeur.java` l.411
- (NV) A83 — Borne de départ et /depart : tronçons de la carte chargés en bloquant le serveur — `modules\monde\src\serveur\java\fr\heralisz\utils\spawn\DepartServeur.java` l.451
- (NV) A84 — Coffres personnels : chaque fermeture resauvegarde les coffres de TOUS les joueurs sur le fil serveur — `modules\monde\src\serveur\java\fr\heralisz\utils\coffres\CoffresPersonnels.java` l.476
- (NV) A85 — Bruit des tirs : chemins calculés pour des zombies trop loin pour bouger — `modules\monde\src\serveur\java\fr\heralisz\utils\zombies\StimuliZombies.java` l.103

**Performances joueurs** (13)

- (V) A15 — Stickers et StatTrak recalculés en entier à chaque image pour chaque arme visible — `modules\armes\src\client\java\fr\heralisz\camo\decor\client\DecorRender.java` l.262
- (V) A20 — Textures d'armes très lourdes en mémoire graphique (une texture de 4096 × 4096 pour tous les M9) — `serveur\tacz\heraliz_5_couteaux_cs2\assets\arkaniaz\textures\gun\uv\m9_rusted.png`
- (V) T-perf-1 — Conteneurs de loot HD : 945 faces chacun, jusqu'à 3,8 millions de faces chargées à Valmont (+ doublon T-perf-2) — `modules\loot\src\client\java\fr\heraliz\loot\client\ModeleConteneur.java` l.126
- (V) T-perf-3 — Grande texture des blocs en 8192×8192 : icônes trop grandes (512 px) qui la remplissent — `modules\…\textures\item\keycard_fragment_blue_1.png`
- (V) T-perf-5 — Casino, caisses et armures : environ 445 Mo de textures gardées en mémoire graphique — `modules\…\textures\machine\souffle_loup_roue.png`
- (NV) A60 — Modèles HD des conteneurs : ombrage ambiant de Minecraft calculé en plus de celui déjà cuit — `modules\loot\src\client\java\fr\heraliz\loot\client\ModeleConteneur.java` l.158
- (NV) A71 — Fuite de mémoire côté joueur : chaque joueur qui passe une carte reste en mémoire à vie — `modules\bunker\src\client\java\fr\heralisz\bunker\keycard\client\KeycardSwipes.java` l.51
- (NV) A72 — Délais d'animation et rafraîchissements des portes (code HBM) jamais traités : file qui grossit, vieilles portes gardées — `modules\bunker\src\client\java\com\hbm_m\client\overlay\DoorAnimationDelayHelper.java` l.20
- (NV) T-perf-10 — MAtmos : 42 minutes d'ambiances décodées en mémoire (aucune en « stream ») — `serveur\mods-tiers\MAtmos-7.1-forge-1.20.1.jar`
- (NV) T-perf-6 — Conteneurs : 5 copies de chaque modèle en mémoire Java (environ 245 Mo au pire) — `modules\loot\src\client\java\fr\heraliz\loot\client\ClientLoot.java` l.52
- (NV) T-perf-7 — Modèles de conteneurs fabriqués en plein jeu (environ 4,3 s de calcul), sous verrou — `modules\loot\src\client\java\fr\heraliz\loot\client\ModeleConteneur.java` l.68
- (NV) T-perf-8 — 75 shaders de camo identiques (192 Ko chacun) : environ 5 s de compilation à chaque rechargement — `modules\armes\outils\generer_camos.py` l.32
- (NV) T-perf-9 — 35 armes TaCZ sans modèle allégé (LOD), 3 avec un LOD identique au modèle complet — `serveur\tacz\heraliz_2_armes_nitrogen\assets\arkaniaz\display\guns\mod2_display.json`

**Interfaces** (7)

- (V) A296 — Coin haut-droit : 6 affichages et les icônes d'effets se posent au même endroit sans se pousser — `modules\clans\src\client\java\fr\heralisz\economie\client\SoldeHud.java` l.47
- (V) A297 — La carte « Raid block » recouvre la barre de prise du cœur de clan — `modules\bases\src\client\java\fr\heraliz\crac\client\RaidBlockOverlay.java` l.79
- (NV) A201 — Chances affichées fausses quand des emotes sont déjà débloquées (caisse des emotes vendue, caisse Événement) — `modules\boutique\src\serveur\java\fr\heraliz\crates\opening\CaseOpening.java` l.87
- (NV) A213 — Liste du groupe vocal dessinée par-dessus la Purge et le panneau de zone (+ doublon A298) — `modules\voix\src\client\java\com\caracrea\client\voice\VoiceHud.java` l.189
- (NV) A299 — « EN COMBAT » se pose sur la carte du plan de construction — `modules\noyau\src\client\java\fr\heraliz\noyau\client\HudCombat.java` l.50
- (NV) A300 — Réglages du casino perdus par Échap ou en changeant de machine — `modules\boutique\src\client\java\fr\heraliz\casino\client\screen\AdminScreen.java` l.221
- (NV) A301 — Registre du marchand : prix tapés perdus sur Échap ou après une autre modification — `modules\pnj\src\client\java\fr\heraliz\quetes\client\EcranEditionMarchand.java` l.61

**Architecture** (1)

- (NV) A302 — Kit d'interface : 20 copies divergentes, la référence du noyau est en retard (+ doublons A10, A26, T-arch-6, T-arch-10) — `modules\noyau\src\client\java\fr\heraliz\noyau\client\kit\Ui.java` l.1

**Propreté du code** (4)

- (NV) A193 — Jackpot progressif affiché mais jamais alimenté ni gagnable — `modules\boutique\src\serveur\java\fr\heraliz\casino\data\Jackpot.java` l.86
- (NV) A271 — Le banc /bots scenario ne mesure ni les zombies ni des joueurs éparpillés sur la carte — `modules\bases\src\serveur\java\fr\heraliz\crac\bases\NatureBases.java` l.96
- (NV) A58 — Table de loot mal écrite : elle disparaît sans bruit, et un conteneur vide rapporte quand même des points — `modules\loot\src\serveur\java\fr\heraliz\loot\TablesCoffres.java` l.150
- (NV) T-arch-5 — caisses.py : la table CAISSES est écrite puis écrasée aussitôt ; le guide dit de changer les caisses à la main — `outils\caisses.py` l.481

### 4.2 Bas (157)

**Duplication et perte d'objets** (2)

- (NV) A167 — Achat chez un marchand non sauvegardé tout de suite (vente et troc le sont)
- (NV) A86 — Coffres personnels : courte fenêtre de copie ou de perte si le serveur plante juste après une fermeture

**Économie** (5)

- (NV) A124 — Signal de ravitaillement : délai rendu au redémarrage alors que l'XP et la quête sont déjà données
- (NV) A23 — 250 anciennes recettes de table (fer, cuivre, plans…) toujours chargées sur le serveur
- (NV) A251 — Un échange (troc) créé en jeu bloque la revente et le recyclage de TOUS les objets du même type
- (NV) A253 — Hérals perdus sans message quand le compte est au plafond (10 millions)
- (NV) A322 — Cœur de clan à 1 000 hérals (et 3 % dans la caisse Vote), alors que la règle le veut « cher »

**Équilibrage et loot** (6)

- (NV) A139 — C4 et charge explosive consommées sans prévenir quand elles visent une base hors Purge
- (NV) A260 — Matériaux Épiques de construction en T6 et T7 : toujours 2 ou plus d'un coup
- (NV) A320 — Quête du jour « Tuer 3 joueurs armés » : le Glock gratuit du kit de départ suffit
- (NV) A321 — Largage public ouvert par un membre du staff en créatif : les points et l'objet d'événement comptent quand même
- (NV) A323 — Faim deux fois plus lente : la vie rendue par chaque point de nourriture double aussi
- (NV) A39 — Grenade flash : aveugle jusqu'à 15 s et assourdit tout le monde à 32 blocs, même derrière les murs

**Bugs** (17)

- (V) A35 — Pendant un /reload, toutes les données de mêlée, de soins et de grenades sont vides côté serveur
- (NV) A103 — Zones dangereuses : l'exposition repart de zéro en se reconnectant
- (NV) A140 — Entretien : l'armoire se dit « non payée » juste après avoir payé avec sa dernière planche
- (NV) A153 — Arrêt du serveur pendant le feu de l'épave : boîte noire et coffre perdus
- (NV) A156 — Podium du ClashWar gardé en mémoire seulement : blocs laissés dans la salle après un plantage
- (NV) A168 — Quêtes du jour : gain perdu sans message au-delà de 7, et gains gardés après un /wipe
- (NV) A169 — Étape « fabriquer » de Varga : une arme ramassée table ouverte compte comme fabriquée
- (NV) A182 — Statut Soutien (tag Discord) connu seulement après /kit, oublié à la déconnexion
- (NV) A186 — /trade : un objet peut sortir de l'échange tout seul si un autre mod change son NBT
- (NV) A21 — La recette « camo sur sac » accepte les skins à vie et les camos du Pack-a-Punch
- (NV) A238 — IPv6 : l'adresse du joueur lue par Minecraft est tronquée (« [2001 »), donc jamais enregistrée ni bannie, contrairement à ce que dit le code
- (NV) A249 — Quête du jour « recycler 8 objets » remplie par un seul paquet de 15 balles
- (NV) A261 — Colt 933 trouvé en mode coup par coup avec 1 balle (fiche d'arme mal lue)
- (NV) A41 — Une seule fiche illisible reçue du serveur efface toutes les armes de mêlée, soins ou grenades chez le joueur
- (NV) A65 — Coffre T7 : l'écran s'ouvre à 8 blocs mais se ferme au-delà de 6
- (NV) A76 — Carte en main masquée pendant le passage : peut rester invisible si un autre mod annule le rendu du joueur
- (NV) A94 — Le train traverse les véhicules et ne heurte jamais leurs passagers

**Données et fichiers** (8)

- (NV) A142 — Vieilles recettes de Minecraft de CRac toujours livrées (armoire, C4, charge, caisse, marteau, clou…)
- (NV) A181 — Warps et zones VIP : « posé / créée » annoncé même quand rien n'est enregistré
- (NV) A218 — Sourdines vocales rangées dans le dossier du monde
- (NV) A235 — Preuves : 500 fichiers au plus, communs à toutes les preuves ; une rafale d'alertes de mouvement efface les preuves de duplication et de contrôle d'intégrité
- (NV) A74 — Réglages des cartes (« common ») non envoyés aux joueurs : barre d'usure et infobulle fausses si le serveur change les valeurs
- (NV) A96 — Frappe en vol perdue avec sa cartouche si le serveur redémarre
- (NV) T-arch-7 — Deux copies des tables de loot ; munitions et nourriture écrites par aucun outil
- (NV) T-conf-5 — Listes Curios « face » et « ear_protection » qui citent des objets inexistants

**Sécurité** (8)

- (NV) A110 — Paquet « Exécuter » sans limite : chaque essai refusé refait un faux coup et écrit une ligne dans le journal
- (NV) A185 — Éditeur des grades : le contenu des kits vient tel quel du joueur, pas de son inventaire
- (NV) A215 — Le chat vocal marche avant le /login d'AuthMe
- (NV) A217 — Deux limites de débit séparées : 120 trames/s possibles par joueur
- (NV) A219 — Clé de la voix envoyée en clair sur la connexion du jeu
- (NV) A27 — Paquet d'attaque de mêlée : liste de cibles lue sans limite avant d'être vérifiée
- (NV) A47 — Enchantements des sacs toujours actifs : « Pillard » range le butin sans les règles du sac (sac dans un sac possible)
- (NV) A75 — Clic droit sur un morceau de porte orphelin = morceau supprimé, même en survie

**Anticheat** (8)

- (NV) A102 — Pings : les messages de refus révèlent si un joueur caché est à portée (contourne en partie l'anti-ESP)
- (NV) A106 — Fouille possible pendant la scène d'exécution avec un client modifié
- (NV) A111 — Durées « maintenir la touche » (exécuter 0,5 s, abandonner 2,5 s) vérifiées seulement par le jeu du joueur
- (NV) A180 — /trade accepté en créatif, contrairement à ce que dit la documentation
- (NV) A216 — La liste vocale et les apparences révèlent les staffs en vanish
- (NV) A22 — Jambe cassée : la réduction du saut n'est faite que chez le joueur, contrairement à la doc
- (NV) A336 — Anti-duplication aveugle aux copies chez un joueur déconnecté ou dans un coffre
- (NV) A50 — Emotes : la roue d'Emotecraft démarre sur ², une touche réservée à l'anticheat

**Commandes** (9)

- (NV) A115 — Débloquer un finisher payant demande seulement le niveau de permission 2
- (NV) A154 — /clashwar est refusé aux joueurs sur la carte alors que les annonces le proposent
- (NV) A179 — /pb retirer à la console : un refus du site s'affiche comme une réussite
- (NV) A184 — Panel du staff : une sanction est notée comme réussie dès que LiteBans existe
- (NV) A28 — /camos ajouter et /camos recharge ouverts dès la permission 2, sans contrôle AuthMe
- (NV) A289 — /spawn ne marche jamais pour un joueur, alors que la doc et le message de /rtp disent le contraire
- (NV) A292 — Doublons de commandes (/caracrea vs /perso et /voix, /clan admin argent vs /eco)
- (NV) A293 — /tpa : aucune limite d'envoi de demandes (spam du joueur visé)
- (NV) A295 — Staff caché (vanish) repérable par certaines commandes de joueur

**Conflits entre mods ou systèmes** (1)

- (NV) T-conf-6 — Regard libre (Alt) : T ouvre les réglages de TaCZ au lieu du chat

**Performances serveur** (29)

- (V) A17 — Recherche des cibles des balles élargie pour toutes les entités, zombies compris
- (NV) A104 — Zones dangereuses : expression rationnelle recompilée et réflexion Curios à chaque contrôle
- (NV) A114 — Petites pertes de performance : « est à terre ? » calcule tout l'état, et le mur est vérifié deux fois pendant une fouille
- (NV) A128 — /saison journal lit tout le journal de la saison sur le fil serveur
- (NV) A136 — /base info relit tous les fichiers de PNJ sur le disque à chaque appel, et ignore les prix changés en jeu
- (NV) A137 — Monde du tutoriel : /rtp et l'envoi au tutoriel peuvent créer jusqu'à 6 tronçons de terrain d'un coup ; /home charge 4 tronçons
- (NV) A138 — Purge.enCours() recalcule tout le calendrier (objets créés) à chaque bloc ou pièce touché par une explosion
- (NV) A171 — wipe.dat relu et décompressé sur le disque à chaque connexion
- (NV) A183 — Petites tables en mémoire jamais vidées (DERNIER_PB, LienDiscord.DERNIER, TpaServeur.RECHARGES, ZonesVip.COINS)
- (NV) A196 — Zones du casino : liste des joueurs recopiée à chaque tick
- (NV) A197 — Statistiques : joueurs jamais purgés, tri complet toutes les 2 s par tableau
- (NV) A205 — Conversion des clés de la planque : écriture du coffre en arrière-plan et sauvegarde de toutes les données du monde
- (NV) A206 — Comptes de clés jamais retirés de la mémoire
- (NV) A232 — Contrôle d'intégrité : décompression, analyse et JSON de preuve « joli » du rapport complet sur le fil du serveur
- (NV) A274 — Attaques des zombies : une lecture de WeakHashMap par zombie et par tick, et un état créé pour chaque zombie même sans attaque
- (NV) A275 — Fichiers de réglages vérifiés sur le disque depuis le fil serveur toutes les 2 à 10 s
- (NV) A276 — Zones dangereuses : Curios lu par réflexion sans cache, pour chaque joueur dans une zone, chaque seconde
- (NV) A277 — Plafond des monstres : toutes les entités de la carte parcourues chaque seconde et deux nouvelles tables créées à chaque fois
- (NV) A36 — Grenade dont l'explosion est annulée : nouvel essai d'explosion à chaque tick, sans fin
- (NV) A37 — Chaque explosion de grenade calcule les 1 352 rayons de destruction de blocs même quand elle ne casse rien
- (NV) A46 — Backpacked : systèmes coupés pour les joueurs mais qui tournent encore (défis, marchand ambulant, mixin sur toutes les entités)
- (NV) A48 — Emotes : tout le fichier des déblocages est réécrit sur le disque, pendant le tick, à chaque déblocage
- (NV) A49 — Armure : chaque coup reçu par n'importe quelle créature fait le calcul complet de protection
- (NV) A6 — MAtmos : deux procédures encore inutiles à chaque tick de chaque joueur côté serveur
- (NV) A61 — Commande /heralizloot reinitialiser jusqu'à 17 millions de blocs lus d'un coup
- (NV) A62 — Forme d'un bloc « partie » recréée à chaque lecture
- (NV) A64 — Mémoire du butin des zombies jamais élaguée en mémoire et écrite sur le fil serveur
- (NV) A67 — Tirage : somme des poids et lecture du NBT refaites à chaque objet tiré
- (NV) A9 — Petite allocation à chaque tick serveur pour lire le temps du ramasse-miettes

**Performances joueurs** (10)

- (NV) A125 — Panneau de zone : textes traduits et reconstruits à chaque image (+ doublon T-perf-12)
- (NV) A223 — Course au démarrage du transport UDP du joueur
- (NV) A233 — Jeu du joueur : lecture de l'identifiant de la carte mère (OSHI, requête WMI) sur le fil du jeu à la connexion, sans protection contre une erreur
- (NV) A234 — Jeu du joueur : la réponse au défi d'intégrité (réflexion sur toutes les classes + SHA-256 sur environ 290 Ko) est calculée sur le fil du jeu
- (NV) A307 — Liste des joueurs (Tab) entièrement recalculée à chaque image
- (NV) A308 — Allocations à chaque image : chat, Purge, groupe vocal
- (NV) A38 — Particules d'explosion forcées envoyées à tous les joueurs de la dimension, jusqu'à 512 blocs
- (NV) A63 — Fond de rareté des cases : 3 dessins séparés par case et par image
- (NV) A8 — Barre DayZ : chaînes relues et objets créés à chaque image
- (NV) T-conf-4 — La mesure /diag client des entités de bloc peut être faussée par EntityCulling

**Interfaces** (11)

- (NV) A11 — Indicateur « EN COMBAT » gardé après une déconnexion
- (NV) A3 — Menu Échap : bouton Boutique « Bientôt » et désactivé pour tout le monde
- (NV) A303 — Inscription ClashWar et console admin de CRac : brouillon perdu sur Échap
- (NV) A304 — Voile des zones gaz, radiation et froid dessiné par-dessus tous les HUD
- (NV) A305 — Munitions : pas de contrôle de place sur un écran étroit (barre DayZ, faim)
- (NV) A306 — Petit chevauchement de la notification d'extraction avec le panneau de ville pendant une capture (+ doublon T-conf-7)
- (NV) A309 — Annonce de cartouche des jumelles dessinée sous l'armure et l'air
- (NV) A311 — Textes encore agrandis par pose().scale et police de base (chantier déjà connu)
- (NV) A4 — Message de refus sur la carte faux : « seul /tpa marche ici »
- (NV) A66 — Couleurs des paliers différentes entre l'onglet créatif et le badge de fouille
- (NV) A7 — Reconnexion automatique : cache toute autre raison de refus pendant 10 minutes

**Architecture** (1)

- (NV) T-arch-11 — Tests de CRac (GameTests) livrés dans le jar serveur

**Propreté du code** (42)

- (NV) A105 — Convertisseur des anciens corps « corpse » : code probablement mort, avec un risque théorique de double
- (NV) A112 — Code d'essai mort encore livré : bot de réanimation et son œuf dans l'onglet créatif, exécutions « variantes » jamais lancées
- (NV) A113 — Réponses de commandes et messages du serveur sans texte français de repli
- (NV) A12 — Code mort de l'ancien PlayerRevive et contrôle des bots par simple étiquette
- (NV) A126 — Valeurs par défaut des vagues d'assaut : zombies de Minecraft, retirés de la carte
- (NV) A127 — Quêtes de clan tirées à l'heure du système, saison à l'heure de Paris
- (NV) A129 — Code mort : solde interne et ancien mode /cc (+ doublon T-conf-9)
- (NV) A141 — Messages faux sur /spawn et /home (règles qui ont changé)
- (NV) A155 — Code mort du banc d'essai ClashWar (commandes jamais branchées)
- (NV) A157 — Textes du staff écrits en dur (français seulement)
- (NV) A166 — Une seule erreur dans un appel par réflexion coupe la fonction jusqu'au redémarrage (raretés, à terre, armures...)
- (NV) A170 — Échanges : le contrôle des boucles par identifiant seul bloque des échanges légitimes
- (NV) A195 — Code d'essai mort : bot des duels et testPlay
- (NV) A207 — Messages du serveur sans texte de secours (console et joueurs sans le jar à jour voient la clé)
- (NV) A208 — Code mort de l'ancienne ouverture dans le monde et API jamais appelées
- (NV) A220 — Code mort : enregistreur, banc d'essai, écho
- (NV) A221 — Messages serveur sans texte de secours (console)
- (NV) A222 — Petites listes jamais vidées (serveur et client)
- (NV) A236 — Fils Discord : deux listes en mémoire jamais vidées (un élément par joueur ayant eu une alerte depuis le démarrage)
- (NV) A237 — Code mort : AliasSanctions n'enregistre plus rien, mais garde tout le code de transmission vers LiteBans
- (NV) A239 — Mouvement : MovementEngine.tick n'a pas le filet de sécurité des détections (une erreur remonte dans le tick du joueur)
- (NV) A24 — Les grenades peuvent casser des blocs : seule la donnée de chaque grenade l'empêche
- (NV) A25 — Code mort : photos 3D des icônes d'armes (577 lignes) toujours chargées
- (NV) A252 — Règle morte « heralisz_heal:* » au recycleur et documentation fausse des hérals « rien »
- (NV) A262 — Composants des coffres T7 marqués d'une rareté : ils ne s'empilent pas avec les mêmes composants
- (NV) A263 — Les simulateurs ne tirent pas comme le serveur (munitions plafonnées à 15 au lieu de 40, quantités simplifiées)
- (NV) A29 — Les dégâts des armes (armes.json) ne se rechargent pas avec /recharger
- (NV) A290 — Listes de commandes avec des entrées mortes, et message de refus trompeur sur la carte
- (NV) A291 — Inventaire et doc des commandes périmés (une dizaine de racines absentes, sous-commandes disparues) (+ doublon T-arch-8)
- (NV) A294 — Réponses du staff écrites en dur en français (Component.literal)
- (NV) A310 — KnifeHud est devenu inutile
- (NV) A40 — Code mort et petites incohérences du fork LR (détonateur, recettes vanilla, réglages mal nommés)
- (NV) A5 — Moniteur de fuites : aucune liste de mod n'est surveillée (+ doublon A273)
- (NV) A51 — Armure : le texte du réglage dit « plafond 75 % » alors que le plafond réel est 60 %
- (NV) A73 — Cinématique de carte retirée mais son code, le mixin de caméra et ses données réseau restent (+ doublon T-conf-8)
- (NV) A77 — Textes dépassés ou en dur : description du mod, libellé des zones de loot des salles
- (NV) A87 — Partage de chemin entre zombies : position de départ et cible mal mises à jour
- (NV) A89 — Vent de la faille et tempête de sable : probablement du code mort de l'ancienne carte
- (NV) A95 — Abri contre les frappes : le code exige « dans une base ET sous un toit », la doc dit « ou »
- (NV) A97 — Petits défauts : ensemble DESCENDUS jamais vidé à la déconnexion, messages serveur sans texte de secours, code mort de la caisse « bloc »
- (NV) T-arch-9 — Sorties des outils non stables (ordre des clés, fins de ligne CRLF/LF)
- (NV) T-perf-11 — outils/mipmaps.py : point de départ faux sous Forge, sources de cfm non vues

---

## 5. Performances

### 5.1 Les plus gros consommateurs côté serveur (50 joueurs, processeur Xeon E5-2690 v2 du Ptero)

Estimations tirées du code et des mesures du 24-25/10 (prod : ~40 ms de médiane par tick à 55 joueurs ; banc local
~2 fois plus rapide : 32,9 ms de moyenne à 30 bots). **À confirmer avec spark.**

| # | Poste | Ordre de grandeur | Levier |
|---|---|---|---|
| 1 | IA de Minecraft des zombies actifs (à moins de 24 blocs d'un joueur) | 200-400 zombies × 0,04-0,1 ms ≈ 8 à 40 ms | `max_total` 400, garder `doMobSpawning=false` (A272) |
| 2 | Recherches de chemin des zombies (budget compté en nombre, pas en temps) | 0 à 24 ms en pic | budget en temps de 3 ms (A264, V) |
| 3 | Zombie Awareness (mod tiers, en doublon avec nos stimuli) | plusieurs ms probables | retirer ou couper ses sens (A265, V) |
| 4 | Suivi des entités par Minecraft | 5 000 entités × 50 joueurs = 250 000 tests par tick | moins d'objets au sol et d'entités |
| 5 | Spartan (rejeu des déplacements, ~20 recherches dans le monde par position) + anti-ESP | 1 à 3 ms | n'interroger le monde que si utile (A229) |
| 6 | Chargement de tronçons | pics | purger les anciens `forceload` ; borne de départ (A82, A83) |
| 7 | Sauvegarde auto (toutes les 5 min) | gel de 300 ms et plus | rotation du loot en fond (A269), quêtes en fond (A268) |
| 8 | Une dizaine de travaux « une fois par seconde » sur le même tick | pic toutes les 20 ticks | décaler les phases (A266) |
| 9 | Pics ponctuels : relevé anti-duplication (20 s), quêtes (30 s), banque des clans | 5 à 50 ms | A267, A268, A120 |
| 10 | Ramasse-miettes | pauses de 32 à 104 ms | `-Xms` = `-Xmx` chez l'hébergeur |

Notre propre code respecte déjà presque partout ses règles (sortie immédiate quand il n'y a rien à faire, rien de lourd
à chaque tick, écritures en fond pour CRac et les coffres). Le vrai coût, ce sont **les zombies** (IA, chemins, mod tiers)
et **les pics alignés**.

### 5.2 Les plus gros consommateurs côté joueurs

| Élément | Mesure | Effet |
|---|---|---|
| Conteneurs de loot HD (T-perf-1, V) | 945 faces par conteneur (meuble cfm : 87) ; 3,84 millions de faces au pire endroit de Valmont (vue 8) | ≈ 307 Mo de mémoire graphique, tronçons longs à refaire |
| 10 modèles les plus posés (T-perf-2, V) | 45 % de toutes les faces de loot (poubelle en tôle, meuble à chaussures en tête) | là où alléger d'abord |
| Grande texture des blocs (T-perf-3, V) | 8192×8192 (journal du jeu) ≈ 341 Mo ; icônes de 512 px pour des objets vus en 16-32 px | risque de passer en 16384 au prochain ajout (non confirmé) |
| Casino, caisses, armures (T-perf-5, V) | ≈ 194 + 125 + 125 Mo si tout est vu, jamais libérés | saccades sur cartes de 4 Go |
| Textures des armes (A20, V) | ≈ 486 Mo si tout est chargé ; une texture de 4096×4096 (≈ 89 Mo) pour les M9 | |
| Stickers et StatTrak (A15, V) | recalculés en entier à chaque image pour chaque arme visible | images par seconde en combat |
| Modèles des conteneurs (T-perf-6, T-perf-7) | 5 copies en mémoire Java (≈ 245 Mo), fabriqués en plein jeu (≈ 4,3 s) | à-coups en entrant en ville |
| Shaders de camo (T-perf-8) | 75 copies de 3 940 lignes : ≈ 5 s de compilation à chaque rechargement | changement de profil graphique |
| MAtmos (T-perf-10) | 42 min d'ambiances décodées en mémoire (≈ 233 Mo) | |
| 35 armes TaCZ sans modèle allégé (T-perf-9) | 643 cubes en moyenne dessinés pour chaque joueur visible | combats à plusieurs |

### 5.3 Les idées du propriétaire, chiffrées

| Idée | Réponse | Chiffres |
|---|---|---|
| **Props de la carte moins détaillés** | **Oui, c'est le gain le plus rentable côté joueurs.** Un vrai changement « selon la distance » est impossible pour des blocs cuits dans le terrain ; il faut une **version allégée** (d'abord dans le profil Performance, puis pour tous si les rendus sont validés). | Mesuré sur les vrais générateurs : biseaux de moins de 0,2 px remplacés par une boîte : **−29 %** ; + faces de moins de 0,05 px² retirées : **−43 %** (Valmont 3,84 M → 2,2 M de faces) ; seuil 0,1 px² : **−52 %**. Commencer par les 10 modèles les plus posés. Essai gratuit d'abord : couper l'ombrage ambiant ajouté par Minecraft sur ces modèles (A60, à mesurer). |
| **Hitbox simplifiées** | **Non pour les props et les décors** : les formes sont déjà calculées une fois et gardées (souvent une seule boîte ; décors : médiane 5 boîtes). La hitbox des joueurs en 6 morceaux est légère. | Le vrai gain est ailleurs : la recherche des cibles des balles a été élargie à **toutes** les entités, zombies compris (A17, V) : la réserver aux joueurs. Seuls les très gros véhicules (jusqu'à 121 boîtes, char T-34) pourraient valoir une forme simplifiée, si une mesure le montre. |
| **Textures plus légères** | **Oui, par endroits.** | Casino, caisses, armures (côté ÷ 2) : **≈ −330 Mo** de mémoire graphique au pire. Armes : 5 textures de 2048 px et plus → 1024 : **≈ −150 Mo**. Icônes de 512 px → 128 px : −6,2 Mpix dans la grande texture ; sacs 512 → 256 : −6,6 Mpix. Les pages des conteneurs (9 × 1024²) sont à garder. Toujours par les scripts qui écrivent ces textures, avec rendus validés. |

### 5.4 Plan de benchmark avant / après

**Étape 0 : réparer les instruments de mesure** (sinon les chiffres sont faux) :
- `/diag` : compter aussi le travail fait entre deux ticks (paquets, commandes, tronçons) — A1 (V).
- `/diag client` : mesurer l'intervalle entre deux images, pas seulement le dessin (aujourd'hui « 1 % le plus lent :
  234 FPS » pour une moyenne de 59 FPS) — T-perf-4 (V). Ajouter la mémoire de géométrie et la taille de l'atlas.
- Ajouter un chronomètre par système (`/diag perf`) autour des 20 plus gros ticks de nos mods, un compteur de temps des
  chemins des zombies, la durée de sauvegarde par fichier, et un script `compare_charge.py` qui compare deux rapports.

**Règles communes** : même machine (idéalement un serveur de test au même processeur que le Ptero), copie figée de
`dayz_map`, mêmes jars, Mohist et plugins ; 5 min de chauffe ; `/forceload query` vide ; `doMobSpawning` à false ;
10 min par scénario, 3 passages, médiane. **Un changement est gardé** si les 95e ET 99e centiles des ms par tick baissent
de plus de 10 %, sans hausse de la moyenne.

**Scénarios serveur** :
- S0 à vide (`/diag start 120`, `spark profiler`) : base ≈ 1,5 ms.
- S1 combat et bases : `/bots scenario 50 10 96` dans `crac:bases` (ne dit rien des zombies).
- S2 zombies : copie de la carte, Valmont, `/zombies banc 400 64` + quelques comptes qui bougent ; comparer budget des
  chemins 40 contre 3 ms, puis Zombie Awareness actuel contre réglé ou retiré.
- S3 sauvegarde : `/save-all` 3 fois pendant S1 ou S2 ; durée du tick de sauvegarde.
- S4 vrais joueurs (soirée) : `/diag start 900`, `spark profiler`, nombre de joueurs noté.

**Scénarios joueurs** (`/diagclient 30`, 3 fois, PC de test et un petit PC, profils Équilibré puis Performance) :
A centre de Valmont (pire endroit), B rue de Tcherno, C campagne (repère), D spawn puis casino et caisses (mémoire
graphique avant / après), E combat avec 10-20 bots en ghillie. A/B avec un jar de maillages allégés. Seuils : plus de
10 % d'images par seconde gagnées à Valmont, ou plus de 150 Mo de mémoire graphique en moins.

---

## 6. Économie et loot : résultats des calculs

Calculs faits en lecture seule sur les fichiers du projet, avec les règles du code (rachat au plus 50 % du prix de vente,
refus « trop rentable » du recycleur, objets à vie jamais rachetés). Rien testé en jeu.

### 6.1 Boucles d'argent
- **Aucune boucle qui rapporte** par les marchands : 0 sur 159 articles (meilleur cas : brindilles, −1 héral), 0 sur 307
  recettes, monter un composant de niveau perd toujours (10 × 8 = 80 pour un II revendu 20), le casino ne reprend aucun
  objet contre des capsules.
- **Sources d'argent gratuit encore ouvertes** :

| Source | Montant | Gravité |
|---|---|---|
| ClashWar joué entre ses propres équipes (A245) | 7 000 à 8 750 par soirée, ≈ 14 000-17 500 par semaine + clés | critique |
| Quête de clan « Déposer N hérals » (A117) | +300 par jour, +2 000 par semaine (+ XP, points) | moyen |
| Caisse du casino puis Mila (A191) | ≈ 0,40 héral par capsule (planches) | moyen |
| Mila rachète au prix plein les armes ruinées (A100) | prix d'une arme neuve | moyen |
| Quêtes du jour « livrer » (A248) | rapportent 2,2 à 3,7 fois ce qu'elles coûtent | moyen |
| Quête « recycler 8 objets » (A249) | remplie par un seul paquet de 15 balles | bas |

- **Grosses sources voulues, à garder en tête pour les prix** : territoires 7 200 à 10 080 hérals par jour en banque
  (même sans personne en ligne) ; ravitaillement ≈ 745 hérals de butin par caisse, toutes les 90 min par zone (jusqu'à
  ≈ 35 000 par jour avec 3 zones) — mais 30 % de son poids vise des objets qui n'existent pas (A247) ; quêtes de Varga
  ≈ 27 300 hérals nets par compte (sûr tant que la validation Discord reste manuelle).
- Casino, part des mises rendue : roulette 94,7 %, crash 93 %, démineur ≤ 95 %, course 85 %, blackjack ≈ 98 %, Plinko
  ≈ 87,8 %. Espérance par clé : event ≈ 720 hérals, quête 93, vote 21. Kits gratuits : négligeables (≤ 11 hérals par jour).

### 6.2 Loot

| Palier | Valeur d'un conteneur rempli | Dont revendable à Mila | Armes pour 100 | Balles dans une arme trouvée |
|---|---|---|---|---|
| T1 | 21,0 | 6,7 | 7,7 | 1,1 |
| T2 | 31,9 | 10,1 | 12,4 | 1,2 |
| T3 | 49,2 | 19,6 | 18,6 | 1,3 |
| T4 | 81,7 | 36,6 | 33,9 | 1,4 |
| T5 | 151,0 | 75,0 | 53,4 | 1,5 |
| T6 | 276,5 | 148,1 | 88,3 | 1,7 |
| T7 | 658,4 | 471,9 | 145,0 | 2,0 |

- **Progression correcte** (×1,5 à ×2,4 par palier), armes à 10 % de leur chargeur, toutes les raretés renseignées.
- **Carte (copie v3)** : sur 6 619 conteneurs remplis à la fois, 79 % sont des T1 ; seulement 17 T5 et 8 T6. Un conteneur
  rempli à l'aérodrome ne vaut que 2 fois plus qu'à Valmont (47 contre 22,7 hérals).
- **Zombies trop rentables (A254, V)** : l'outil les compare à un conteneur « T2,5 » (40 hérals) alors que la vraie moyenne
  est ≈ 25. Rapport réel zombies / fouille ≈ 0,77 à 0,85 au lieu de 0,5 voulu (au couteau, sans munitions, encore plus).
  Correction : référence 1,35 et zombies ≈ 18 hérals brut au lieu de 22,5.
- **Composants Épiques des zombies (A255, haut)** : voir §3.7.
- **Fragments rouges** : un zombie en donne environ 20 fois plus vite que la fouille de l'aérodrome (A256).
- **8 armes Légendaires presque sans munitions** (.50 BMG, 40 mm, roquettes) : introuvables (A257).
- **Sacs** : la part « 62 % Commun » ne s'applique pas (A258). Le levier `FACTEUR_TOTAL` ne remplit plus de T5/T6 à
  l'aérodrome (A259).

### 6.3 Points de saison par jour et par clan

| Source | Points | Plafond |
|---|---|---|
| Petites actions (zombies, conteneurs, ventes, crafts) | jusqu'à 50 par joueur | 50 par joueur et par jour |
| Captures de drapeaux | 3 × valeur × difficulté | 40 par clan et par jour |
| Largage public / objets d'événement | 8 / 2 à 12 | 24 / 70 par jour |
| **Kills PvP** | +1 par couple, ×0,5 ensuite | **aucun** (A314, haut) |
| **Salles à carte** | 5 / 15 / 40 par recharge | **aucun** : 240 points par carte rouge (A319) |
| **Quêtes de Varga** | 10 chacune | **aucun** : ≈ 380 par joueur, 3 040 pour un clan de 8 (A316) |
| ClashWar | 200 / 120 / 80 / 40 (×2) | 2 soirs par semaine |

Les sources plafonnées pèsent peu à côté des trois non plafonnées : c'est là que se jouera le classement.

---

## 7. Cohérence des données

### 7.1 Qui écrit quoi (graphe des générateurs)

| Outil | Lit | Écrit |
|---|---|---|
| `outils/equilibrage_armes.py` | gunpacks | `armes.json`, `raretes.json` (armes, accessoires, munitions) |
| `modules/equipement/outils/equilibrage_armures.py` | ses tableaux | catalogues Java des armures, `raretes.json` (armurerie) |
| `modules/equipement/outils/build.py` | `composants.py` | `ComponentDefs.java`, `raretes.json` (composants) |
| `outils/loot_armes.py` | raretés, armes, composants, gunpacks, sacs, **ses propres tables** | tables T1-T7 et soins, `ConfigLoot.java`, `heraliz_loot-server.toml` |
| `outils/loot_zombies.py` | raretés, prix des marchands et des crafts | tables des zombies |
| `outils/crafts.py` | raretés, gunpacks | gunpack `heraliz_crafts` |
| `outils/recyclage.py` | recettes `heraliz_crafts` | `recyclage.json` |
| `outils/marchands.py` | raretés, **tables de loot**, **recyclage.json** | prix des PNJ, tags de rachat |
| `liste_emotes.py` → `butin_spectacle.py` | émotes | `spectacle.json` |
| `outils/caisses.py` | raretés, composants, couteaux, **spectacle.json, t6.json** | caisses, largage |
| `outils/quetes_journalieres.py` | valeurs des zombies, raretés | `journalieres.json` |

**Ordre à respecter** : equilibrage_armes → equilibrage_armures → build.py → loot_armes → loot_zombies → crafts →
recyclage → marchands → liste_emotes → butin_spectacle → caisses → quetes_journalieres. Quatre outils écrivent dans
`raretes.json`, chacun sur sa partie (pas de chevauchement vérifié).

**État le 26/10 : tout est à jour.** La chaîne relancée dans l'ordre sur une copie ne change rien ; 8 225 références
d'objets citées, 0 inconnue ; 132 noms de classes lus par réflexion, tous présents du bon côté.

**Le problème : rien ne le garantit pour la suite (T-arch-1, V).** Exemple du propriétaire rejoué sur une copie : une
recette de munition changée dans `crafts.py` sans relancer `recyclage.py` laisse le recycleur sur l'ancienne recette, et
`verifier.py` dit « 0 problème ». Les recettes changées en jeu au Registre de l'armurier ne sont pas vues non plus (A14, V).

### 7.2 Contrôles automatiques à ajouter à `verifier.py`
1. Appeler `recyclage.py --verifier` et `marchands.py --controle` (existent déjà, jamais appelés). **Le plus urgent.**
2. Contrôle « chaîne » (≈ 30 s) : recopier les données dans un dossier temporaire, relancer les outils dans l'ordre,
   comparer au projet et dire quel outil relancer. Un `outils/generer_tout.py` dans le même ordre.
3. Références : les armes, accessoires et munitions TaCZ cités (dans le nbt) existent dans les gunpacks (caisses, loot,
   marchands, recycleur, quêtes, casino, kits) — T-arch-2.
4. Les deux copies des tables de loot (jar et `serveur\config`) identiques — T-arch-7.
5. Noms de classes lus par réflexion présents ; copies du kit d'interface et des classes « pont » identiques à la
   référence (T-arch-6, A302).
6. Boucles d'argent : aucune revente ou recyclage ne dépasse le coût de fabrication.
7. `commandes.py --verifier` (inventaire des commandes, T-arch-8) ; texture d'icône de plus de 128 px (T-perf-3).
8. Écrire toujours en UTF-8 et fins de ligne LF, sans réécrire si rien ne change (T-arch-9).

À mettre à jour aussi dans `docs/GUIDE.md` §4 : « changer une recette → crafts.py, puis recyclage.py, puis marchands.py ».

---

## 8. Anticheat

### 8.1 Fonctionnement actuel
- **Launcher** : options Java en liste blanche, `-XX:+DisableAttachMechanism`, jeton du site (6 h), Gardien (programmes
  et jars surveillés) — **coupé côté serveur** (`[gardien] enabled = false`).
- **Jeu du joueur** : défi tournant (empreinte des classes de Spartan), contrôle d'intégrité (jars, Mixins, DLL, threads,
  programmes), touches ² et Maj droite, captures d'écran. Tout cela n'est qu'un **indice** : un jeu modifié peut mentir.
- **Serveur (la partie solide)** : porte des joueurs (rien avant le jeton et le `/login`), mouvement (Fly, Speed, Timer,
  NoFall… physique recalculée), corps à corps (Reach, KillAura, AutoClicker), anti-ESP des joueurs, anti-duplication,
  bans compte + IP + PC, alertes Discord. Réglage actuel : alerte seulement, pas de renvoi en arrière.

### 8.2 Faiblesses (ce qu'un tricheur qui a lu le code peut faire)
- Mise en ligne qui efface les secrets (A324, critique) ; jeton qui prouve un compte, pas le launcher (A325, V) : avec
  son mot de passe, on obtient un jeton valide et on lance le jeu depuis n'importe quel lanceur.
- Aucune analyse des tirs (A326) ; fake lag de 1 s (A327).
- Speedhack d'environ +30 % sous les seuils, +50 % près des décors (A331).
- Défi d'intégrité calculable sans faire tourner le vrai code (A328, V, moyen) ; signatures recherchées envoyées en clair
  au client (A329, V, moyen) ; DLL ignorées sur leur nom (A330, haut).
- Anti-ESP limité aux joueurs (véhicules, cadavres, blocs visibles) ; **aucun anti-xray** des bases et du loot (A332).
- Ban PC basé sur des identifiants faciles à changer (A333) ; capture d'écran prévisible et non liée à la demande (A334) ;
  archive du launcher non scellée (A335).
- Discord : une seule limite de 20 messages par minute pour tout, les alertes graves et les annonces de ban peuvent se
  perdre (A225, V, moyen).

### 8.3 Améliorations classées (gain / effort)
1. **Secrets hors du projet + refus si absents** — énorme / petit.
2. **Fake lag** : 350 ms max + contrôle « FakeLag » — grand / petit.
3. **Marges de vitesse** : `speedMarginPerTick` 0,06 → 0,02, marge seulement là où il faut ; renvoi en arrière pour les
   déplacements impossibles — grand / petit.
4. **Statistiques de tir TaCZ** (collecte d'abord, alertes ensuite) — très grand / moyen à gros.
5. **Empreintes salées au lieu des signatures en clair** ; DLL jugées par chemin et signature côté serveur — grand / moyen.
6. **Jar joueur de Spartan différent à chaque fabrication** (graine secrète + brouillage) — moyen / moyen.
7. **Anti-ESP élargi aux véhicules**, puis **anti-xray des blocs sensibles des bases** — moyen à grand / moyen à gros.
8. Registre anti-duplication persistant (joueurs déconnectés et coffres) ; captures discrètes avec filigrane ; launcher
   scellé et signé, Gardien rallumé après publication du launcher 1.14.1.

### 8.4 Avis sur la proposition « anticheat Rust / kernel / clé qui tourne »
- **Pilote noyau : non.** Certificat EV + signature de Microsoft, expertise noyau (une erreur = écran bleu chez les
  joueurs), impossible sur Mac, très intrusif au regard du RGPD (joueurs souvent mineurs ; avis d'un juriste conseillé).
  Et le gain est faible : les triches Minecraft sont surtout du **code Java** qu'un pilote ne voit pas mieux.
- **Module serveur externe en Rust : non.** Les détections ont besoin du monde, des collisions et de TaCZ : leur place est
  dans le serveur Java, comme aujourd'hui ; l'anti-ESP tourne déjà en parallèle.
- **« Clé qui change avant qu'on la casse » : utile mais pas magique.** Une clé utilisée par le client est dans le client.
  La rotation casse les triches toutes faites et oblige à tout refaire à chaque version (point 6 ci-dessus), mais ne rend
  pas inviolable. Ce qui est vraiment sûr, c'est ce que le serveur **ne donne pas** (anti-ESP, anti-xray) et ce qu'il
  **mesure** (mouvement, tirs).
- **Réaliste plus tard** : un petit module natif **en mode utilisateur** dans le launcher (Windows d'abord, signé) qui
  surveille le processus du jeu, les accès à sa mémoire, les DLL signées et les débogueurs.

### 8.5 Plan d'action anticheat
1. Tout de suite : A324 (DEPLOIEMENT.md + refus si secret absent).
2. Petits réglages : fake lag 350 ms, marges de vitesse, renvoi en arrière des déplacements impossibles, archive du
   launcher scellée puis Gardien rallumé.
3. Contrôle FakeLag. 4. Statistiques de tir (collecte 1-2 semaines, puis seuils, puis alertes). 5. Empreintes salées et
   DLL jugées par le serveur. 6. Défi lié à chaque fabrication. 7. Anti-ESP élargi, puis anti-xray. 8. Registre
   anti-duplication. 9. Captures améliorées. 10. Plus tard : module natif en mode utilisateur. 11. Ne pas faire : pilote
   noyau.

---

## 9. Plan d'action global

Chaque lot : corriger dans les sources, `construire.py` + `verifier.py`, puis **essai en jeu par les propriétaires**.

**Lot 0 — avant la prochaine mise en ligne (petits, urgents)**
1. A324 secrets de Spartan (doc + refus des connexions si secret absent).
2. A313 recopier le `zones.json` du Ptero dans le projet.
3. A143 ClashWar qui bloque les largages (une ligne).
4. A130 tag de groupe CRac jamais vide + sauvegarde protégée ; A131 limite à 8.
5. A68 et A69 portes des salles à carte.
6. A42 sacs à dos.
7. A279 : retirer `authme.player.logout` du groupe `default` (LuckPerms), puis tester `/logout` en combat.
8. A188 Tablette du Directeur (ne plus toucher aux commandes de la caisse).

**Lot 1 — pertes et copies d'objets** : A78 /inspecter, A240 objets à vie, A52 dépôt dans les conteneurs, A158 /wipe,
A187 + A189 capsules au sol, A190 caisse du casino, A198 récompenses des caisses, A13 camos, A119 + A118 fichiers des
clans et de la saison, A30 mêlée qui s'use, A99 atelier, A199 sauvegarde après les remises, A44 et A209 fichiers des
émotes et des sourdines.

**Lot 2 — économie et saison** (décisions du propriétaire sur les chiffres) : A245 ClashWar, A117 quête de dépôt, A314
plafond PvP, A116 quêtes de caches, A121-A123 captures et kills des quêtes, A316 Varga, A319 salles, A191 capsules →
hérals, A100 Mila et l'usure.

**Lot 3 — loot** : A255 composants Épiques, A254 référence des zombies, A256 fragments, A257 munitions des armes
Légendaires, A258-A259 ; relancer la chaîne des outils dans l'ordre (§7.1).

**Lot 4 — combat** : T-conf-1 geckolib222, A43 armure, A31 soins, A32 angle de la mêlée, A33 feu des molotovs.

**Lot 5 — anticheat** : plan du §8.5.

**Lot 6 — mesure puis perfs serveur** : d'abord les outils (A1, T-perf-4, chronos), puis A264 chemins, A265 Zombie
Awareness, A266 phases, A267 anti-duplication, A268 quêtes, A269 rotation du loot, A120 banque, A82-A84 borne et coffres.

**Lot 7 — perfs joueurs** (rendus à valider) : textures (T-perf-5, A20, T-perf-3), conteneurs allégés (T-perf-1/2, A60),
stickers (A15), shaders de camo (T-perf-8), MAtmos en « stream » (T-perf-10).

**Lot 8 — interfaces** : une pile commune par coin d'écran dans le noyau (A296, A297, A213, A299), puis les éditeurs qui
perdent leurs réglages sur Échap (A300, A301).

**Lot 9 — outils et propreté** : contrôles du §7.2, kit d'interface, code mort, textes en dur.

**En parallèle** : les lots qui touchent des modules différents peuvent avancer en même temps (bunker, sacs, bases,
boutique, anticheat, armes, loot). Attention aux modules partagés : **clans** (lots 0, 1, 2), **boutique** (lots 0, 1),
**armes** (lots 1, 4) et **outils/loot** (lots 3, 9) — un seul chantier à la fois sur chacun. Les lots 6 et 7 commencent
par la mesure et peuvent tourner pendant les autres.

---

## 10. Limites de l'audit

- **Rien n'a été testé en jeu ni compilé.** Tout vient de la lecture du code, des réglages et de calculs ; les chiffres de
  performance sont des estimations à confirmer avec spark et `/diag` (une fois réparés).
- **270 problèmes sur 332 ne sont pas vérifiés** (presque tous les moyens et les bas) : à relire avant correction.
- Les calculs de loot et de saison reposent sur la **copie v3 de la carte**, pas sur `dayz_map` en construction : à
  refaire quand la carte sera finie. Les prix changés en jeu (Registre, Tablette) ne sont pas dans le projet et n'ont pas
  été vus.
- Le code des autres auteurs (TaCZ, Curios, cfm, ywzj, Zombie Awareness, geckolib222) n'a été lu que là où nos mods s'y
  branchent. Les modules `events` (boss) et `capture` n'ont pas été audités (non livrés).
- L'audit a été fait en plusieurs sessions (coupures de la limite d'utilisation) ; les scripts d'analyse sont dans les
  dossiers de travail de ces sessions, hors projet.

**Écartés ou corrigés par la vérification** (aucun des 46 critiques/hauts revérifiés n'était faux) :
- « Commandes des plugins hors du filtre » : écarté par un premier relecteur, puis **confirmé** dans le code par le
  second (A279) : gardé haut, à tester en jeu.
- A328, point 2 (« un mauvais numéro de version coupe tous les défis ») : **réfuté** — c'est un choix voulu qui évite
  d'accuser à tort des joueurs pas à jour ; le staff est déjà alerté.
- A189 : « aucun journal » est faux — la commande est bien notée dans la console ; seul manque un journal du staff à part.
- A119 : la libération des territoires des clans non lus n'a pas été confirmée (hypothèse).
- A264 : les « 0,2 à 0,6 ms par recherche de chemin » sont une estimation, pas une mesure.
- T-perf-3 : le remplissage « 97 % » de la grande texture et le passage à 16384 au prochain ajout ne sont pas confirmés.
- Gravités baissées par la vérification : A117, A118, A189, A225, A246, A254, A264, A265, A278, A296, A297, A325, A328,
  A329, et 5 constats des perfs joueurs (T-perf-1 à 5) et 1 des données (T-arch-1) : tous de haut à moyen. Aucune
  gravité n'a été montée ; A68, A78, A130, A245 et A324 restent critiques.
