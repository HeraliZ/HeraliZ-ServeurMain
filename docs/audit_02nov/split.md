# Audit « split » (02/11) : découpage client / serveur et mods livrés aux joueurs

Audit en **lecture seule** sur `main` (65e64fad). Rien n'a été modifié, fabriqué ni **testé en jeu**.
Le dossier `livraison/` présent sur la machine date du 23/10 et ne contient que les mods des autres auteurs (aucun de nos
jars) : la liste de ce que reçoivent les joueurs est donc déduite des sources, de `gradle/module-coupe.gradle`,
`outils/construire.py` et `outils/pack_launcher.py`. Les tailles de nos jars sont des **estimations** (pas de fabrication).
Les audits précédents (`docs/AUDIT_COMPLET_26OCT.md` surtout) ont été relus : les points déjà connus et toujours ouverts
sont seulement rappelés par leur numéro (A3xx).

---

## Ce qui est déjà bien (vérifié)

- La coupe est faite sérieusement dans tous nos modules : commandes, sauvegardes, IA, butin, prix des marchands, quêtes,
  recyclage, tables de loot, casino (le serveur tire les résultats : ex. `HorseRaceBlockEntityServeur.java:44-48`, le
  gagnant est tiré au départ de la course, la « graine » envoyée ne sert qu'à l'animation) sont dans `src/serveur` ou
  `data/`. Aucun dossier `data/` dans les ressources « client », aucun `assets/` dans les ressources « serveur ».
- **Spartan** : toutes les détections (mouvement, combat, anti-ESP, anti-duplication), leurs seuils et la config sont
  côté serveur seulement. Sa config est enregistrée par la partie serveur (`modules/anticheat/src/serveur/java/com/Spartan/serveur/PartieServeur.java:45`) :
  elle n'est ni dans le jar joueur, ni envoyée, ni écrite chez le joueur. **C'est le modèle à suivre** pour les points 3 et 4.
- Boss (`events`), `capture` et `banc` ne partent jamais chez les joueurs (`outils/construire.py:137-141`, `181-193`).
  Le launcher efface les anciennes versions de mods et de gunpacks (`launcher/app/assets/js/effacement.js:119`).
- Aucun secret, mot de passe, clé ni adresse dans le code des joueurs (seulement `heraliz.fr` et le Discord). Les secrets
  injectés par la mise en ligne vont dans des fichiers jamais envoyés (`spartan-common.toml`, `lien.json`…).
- **Aucun mod en double** : ModernFix + FerriteCore + MemoryLeakFix, Embeddium + EntityCulling sont complémentaires.
  Les mods de zombies (Mutants & Zombies, Zombie Awareness, CoroUtil) sont encore utilisés par notre code (`monde`).

---

## Points, du plus grave au moins grave

### 1. Gardien du launcher : c'est le jeu du joueur qui dit « tout va bien » — **important**
- **Où** : `modules/anticheat/src/client/java/com/Spartan/Client/Gardien.java:88-138` (vérification de la signature du
  launcher faite dans le jeu), `:138` (texte envoyé) ; `modules/anticheat/src/serveur/java/com/Spartan/serveur/GardienServeur.java:82-128`
  (le serveur ne reçoit que ce texte).
- **Ce qui se passe** : le launcher écrit un rapport signé (programmes de triche vus, jars changés). Mais c'est le jeu du
  joueur qui contrôle la signature, puis envoie au serveur une simple phrase du type `ok;lecture=ok;outils=;jars=0`. Le
  serveur ne voit jamais le rapport signé.
- **Ce qu'un tricheur obtient** : le format exact de la phrase « propre » est lisible dans le jar joueur. Un jeu modifié
  l'envoie toutes les 15 s : plus aucune alerte « Gardien » ni « Gardien muet », même avec Cheat Engine ouvert.
  (Non signalé dans l'audit du 26/10, qui parlait du Gardien coupé, rallumé depuis.)
- **Proposition** : faire suivre au serveur la ligne signée elle-même ; la clé du Gardien serait liée au jeton de
  lancement délivré par le site (que le serveur sait vérifier). Limite honnête : tout ce qui tourne sur le PC du joueur
  reste falsifiable par un tricheur déterminé ; on relève seulement la marche. Effort : **moyen** (launcher + site + Spartan).

### 2. Ce que la partie « joueur » de Spartan apprend à un tricheur — **important** (en partie déjà connu)
- **Où** : `modules/anticheat/src/client/java/com/Spartan/scan/collect/InjectionCollector.java:45-48` et `:255` ;
  `Client/TouchesSuspectes.java:42`, `:118` ; `Client/ClientEvents.java:37`, `:146` ;
  `src/commun/java/com/Spartan/Networks/ScanRequestPacket.java:105-107`.
- **Ce qui se passe** : les détections serveur sont bien cachées, mais les contrôles faits chez le joueur sont tous
  lisibles dans son jar, avec leurs règles :
  - un crochet d'affichage est ignoré si le nom de sa DLL contient `discord`, `steam`, `obs-`, `rtss`… et aussi
    **`renderdoc`** (un outil de capture graphique) : il suffit de nommer sa DLL « steam_… » (A330, toujours ouvert) ;
  - les noms de classes, programmes et DLL recherchés arrivent en clair à chaque contrôle (A329, toujours ouvert) ;
  - seules les touches ² et Maj droite sont surveillées : changer la touche du menu de triche suffit ;
  - le jeton du launcher est lu par le jeu et l'empreinte des mods est calculée par le jeu lui-même, puis envoyée telle
    quelle (format lisible dans `src/commun/java/com/Spartan/integrity/LaunchToken.java`) ;
  - le défi d'intégrité compare les fichiers et la **liste** des méthodes, pas leur contenu en mémoire : un tricheur qui
    réécrit l'intérieur d'une méthode, ou qui modifie les paquets à la sortie du jeu, n'est pas vu (suite de A328).
- **Proposition** : (a) enlever la liste d'« overlays » du jar joueur et tout envoyer au serveur, qui décide (A330) ;
  (b) envoyer des empreintes salées au lieu des noms (A329) ; (c) retirer `renderdoc` de la liste tout de suite (petit) ;
  (d) le brouillage du point 5 rend la lecture plus pénible. Effort : **moyen à gros** pour (a)-(b), **petit** pour (c).

### 3. Les réglages « serveur » partent chez chaque joueur à la connexion — **important**
- **Où** : Forge envoie à chaque joueur, à la connexion, le contenu complet de **tous** les fichiers de réglages de type
  SERVER (le code de CRac le dit lui-même : `modules/bases/src/commun/java/fr/heraliz/crac/admin/AdminConfig.java:9-11`,
  `modules/bases/src/serveur/java/fr/heraliz/crac/network/ConfigResync.java:4-6`). C'est vrai même quand le réglage est
  déclaré seulement côté serveur (ex. `modules/loot/src/serveur/java/fr/heraliz/loot/serveur/PartieServeur.java:18`).
- **Ce qu'un curieux obtient** (avec un petit mod qui note ce qu'il reçoit, sans rien décompiler), par exemple :
  - `serveur/defaultconfigs/heraliz_loot-server.toml:7-12` : 20 % des conteneurs actifs, réapparition entre 4 et 6 min ;
  - `heraliz_zombies-server.toml:27`, `:51`, `:92` : plafond de 1 500 zombies, sommeil au-delà de 64 blocs, vie de chaque sorte ;
  - `heraliz_noyau-server.toml:13`, `:36` : commandes permises en combat, noms des commandes admin de rechargement
    (`spartan officiel`, `heralizloot tables`…) ;
  - plus `heraliz_distances`, `heraliz_cycle`, `heraliz_spawn`, `heraliz_decors`, `reanimation-server.toml`.
- Ces 8 fichiers ne sont lus par **aucun** code du joueur (vérifié : `NoyauConfig`, `ConfigLoot`, `ZombiesConfig`,
  `DistanceVue`, `CycleConfig`, `DepartConfig`, `ConfigZones`, `ReanimationConfig` ne sont cités côté joueur que par leur
  enregistrement). Les autres (`crac-*`, `heralisz_tactical-server`, `airstrike`, `heraliz_corps`, `heraliz_ping`,
  `caracrea`, `heraliz_vehicules`) sont lus par le jeu du joueur (aperçus, prédiction) : ils doivent rester envoyés.
- **Proposition** : le plus simple, un petit mixin serveur (noyau) qui retire ces 8 fichiers de la liste envoyée par
  Forge ; ou, plus propre, faire comme Spartan (réglage déclaré côté serveur seulement, fichier dans `serveur/config`),
  ce qui oblige à déplacer les fichiers du monde et la mise en ligne. Rappel : ne jamais mettre d'adresse interne dans
  `caracrea-server.toml` (`adresseEcoute`) : il est envoyé à tous. Effort : **petit** (mixin) ou **moyen**.

### 4. Sept fichiers de réglages du serveur s'écrivent sur le PC de chaque joueur (dont l'anti-boost) — **important**
- **Où** : réglages de type COMMON déclarés dans le code partagé, donc créés aussi chez le joueur dans son dossier
  `config/` : `clans/.../HeraliszClans.java:32` (`heralisz_clans-common.toml`), `clans/.../economie/HeraliszEconomie.java:26`,
  `bunker/.../HeraliszBunker.java:52` (cartes d'accès : utilisations, recharges 30/120/240 min), `monde/.../extraction/HeralizExtraction.java:26`,
  `equipement/.../HeralizArmory.java:52`, `bases/.../Crac.java:47` (`crac-admin.toml`, mot de passe de console vide),
  `armes/.../HeraliszTactical.java:30`.
- **Ce qu'un curieux obtient sans rien décompiler** : un fichier texte en clair, commenté en français, par exemple
  `clans/src/commun/java/fr/heralisz/clans/ClansConfig.java:98-102` : « Anti-boost : minutes avant qu'un kill du même
  tueur sur la même victime compte à nouveau » = 10, « un joueur tué moins de 60 s après sa réapparition ne compte pas ».
  C'est la recette exacte pour farmer les stats, l'XP et les quêtes entre amis. (Valeurs par défaut, en pratique les mêmes
  que sur le serveur.)
- **Proposition** : déclarer ces réglages depuis la partie serveur de chaque module (comme Spartan) ; les deux valeurs que
  des écrans lisent (point 8) passent par un paquet. Effort : **moyen**.

### 5. Nos jars se décompilent presque comme le code source — **important**
- **Où** : `gradle/module-coupe.gradle:85-88` (aucun réglage de débogage : Gradle garde par défaut numéros de ligne et noms
  des variables locales) ; `outils/brouiller.py:3-5`, `:45-50` et `outils/brouillage/LISEZMOI.md:25` : l'outil de
  brouillage vise encore le module `airstrike`, qui n'existe plus (fusionné dans `monde`) ; il n'est branché ni dans
  `construire.py` ni dans les workflows.
- **Ce qu'un curieux obtient** : un décompilateur rend nos classes avec leurs vrais noms de classes, méthodes, champs,
  variables et textes ; seuls les commentaires manquent. La mention « LISEZ-MOI » n'est qu'un avertissement légal.
- **Proposition** : (a) tout de suite, compiler sans les noms de variables (garder les numéros de ligne pour les rapports
  de plantage) : une ligne dans `module-coupe.gradle`, effort **petit** ; (b) remettre `brouiller.py` à jour et le
  brancher dans la fabrication (pour Spartan : brouiller avant la copie `client.bin`), effort **moyen**, essai en jeu
  complet obligatoire. Le vrai rempart reste de garder la logique côté serveur.
- **Décision** : « On active le brouillage des jars (avec un essai en jeu complet avant la mise en ligne) ? »

### 6. Le jar de TaCZ livré contient un 2e pack d'armes de 48 Mo jamais utilisé — **important** (poids)
- **Où** : `serveur/mods-tiers/tacz-1.20.1-1.1.8-hotfix.jar` (55 Mo), dossier `assets/tacz/custom/tacz_default_gun`
  (48,5 Mo compressés, 3 322 fichiers). TaCZ ne s'en sert que pour le recopier dans le dossier `tacz`
  (`GunMod.registerDefaultExtraGunPack` → `registerExportResource`), et cette copie est coupée par
  `serveur/tacz/tacz-pre.toml:5` (`DefaultPackDebug = true`, vérifié dans `GunPackLoader`). Notre version de ce pack,
  triée, est déjà livrée à part (`serveur/tacz/heraliz_1_base_tacz`). Le fichier `serveur/tacz/.export-state.json` (qui
  cite encore MaxStuff et endlessammo) ne sert donc à rien non plus.
- **Ce que voit un joueur** : 48 Mo de plus à chaque installation et à chaque mise à jour de TaCZ (environ 10 % du
  téléchargement total, voir le tableau plus bas).
- **Proposition** : un jar TaCZ allégé (même code, licence GPL et crédits gardés, comme `cfm-…PATCH` et `ywzj…-fuse`
  déjà modifiés), puis essai en jeu. Effort : **petit**.
- **Décision** : « On allège le jar de TaCZ (code d'un autre auteur, licence gardée) ? »

### 7. Réglages du casino envoyés à tous les joueurs avec les commandes console — **mineur**
- **Où** : `modules/boutique/src/serveur/java/fr/heraliz/casino/event/CommonEventsServeur.java:59`, `:66` envoient tout
  `casino.json` (`CasinoConfig.json()`), y compris les commandes jouées par la console
  (`modules/boutique/src/commun/java/fr/heraliz/casino/config/CasinoConfig.java:194-195`, `:333-337` ;
  `serveur/config/heralizcasino/casino.json:236-254` : `grade acheter {player} rescape {amount}`, `emote debloquer …`).
- **Ce qu'un curieux obtient** : la syntaxe de nos commandes admin de grades et d'émotes.
- **Proposition** : vider les champs `commande` dans la copie envoyée aux joueurs (le jeu du joueur n'en a pas besoin).
  Effort : **petit**.

### 8. Deux écrans lisent un réglage dans le fichier du joueur au lieu du serveur — **mineur** (bug en attente)
- **Où** : `modules/clans/src/client/java/fr/heralisz/clans/client/gui/ClanScreen.java:329-335` et
  `ClanCreateScreen.java:216-222` (prix de création d'un clan, `cout_creation`) ;
  `modules/equipement/src/client/java/fr/heraliz/armory/client/DivingClient.java:83` (recharge de la bouteille).
- **Ce qui se passe** : ces valeurs viennent du fichier COMMON écrit chez le joueur (point 4), pas du serveur.
  Aujourd'hui les deux disent pareil (1 750 hérals ; 5 s), mais le jour où le prix change dans `serveur/config`, l'écran
  affichera toujours l'ancien prix.
- **Proposition** : faire envoyer ces deux valeurs par le serveur (avec le point 4). Effort : **petit**.

### 9. Outils du staff dans le jar de tous les joueurs, dont une console admin morte — **mineur**
- **Où** : console admin de CRac, `modules/bases/src/client/java/fr/heraliz/crac/client/admin/` (4 fichiers, 1 246 lignes),
  désactivée sur le serveur (`serveur/config/crac-admin.toml`, mot de passe vide ; `AdminManager.java:407`) mais sa
  touche « Console admin » apparaît dans les Commandes de chaque joueur (`AdminKeyHandler.java:17`). Autres écrans du
  staff livrés à tous : panel (`EcranStaff.java`, 952 lignes, 311 textes `heraliz_grades.staff.*`), éditeur des grades
  (804), tablette du casino (`AdminScreen.java`, 1 084), éditeur des caisses (571), règles du staff
  (`grades/staff/ActionStaff.java`, `CommandesStaff.java` : rangs par action, commandes interdites).
- **Ce qu'un curieux obtient** : l'organisation du staff et la liste de ses actions. Pas de faille : le serveur revérifie
  les droits (contrôlé sur le casino, `casino/net/ReceptionServeur.java:34`, `:74`, et sur la console CRac).
- **Proposition** : supprimer la console CRac (client et serveur) ; garder le reste (le séparer coûterait cher, et Spartan
  exige le même jeu de jars pour tous). Effort : **petit**.
- **Décision** : « La console admin de CRac (jamais activée) : on la supprime ? »

### 10. ClashWar : moteur et règles du tournoi dans le jar des joueurs — **mineur**
- **Où** : `modules/clashwar/src/commun/java/fr/heraliz/clashwar/tournoi/CwConfig.java` (fichiers du serveur `:96`,
  primes par défaut `:61`), `Tableau.java`, `CwEtat.java`, `Format.java`, et le paquet `moteur/api` (`MatchApi`,
  `Instance`, `Arenes`, `Coffre`, `Carte`, `Equipement`, `Kits`…), environ 1 800 lignes que le jeu du joueur n'utilise
  jamais (vérifié : aucun fichier client ne les cite).
- **Proposition** : les passer dans `src/serveur` (seuls les paquets réseau et ce que lit le HUD restent partagés).
  Effort : **moyen**.

### 11. Ressources mortes ou en double que les joueurs téléchargent — **mineur**
- **cfm (meubles, version modifiée ArkaniaZ)** : `assets/minecraft/optifine/random/entity` (6,3 Mo, 3 573 textures
  « OptiFine » de zombies et joueurs) : OptiFine n'est pas utilisé et nos zombies ont leur propre rendu
  (`monde/.../zombies/client/ZombiesSurvieClient.java`, 23 textures). Morts.
- **8 caisses vidées** (`outils/caisses.py:110` : 4 caisses de boss, stickers, chantier, médicale, largage) : leurs
  textures (≈ 8,3 Mo) et leurs scènes 3D (`boutique/src/client/java/fr/heraliz/crates/client/render/scene/crates/`,
  environ 5 000 lignes) sont toujours livrées. Les blocs eux-mêmes doivent rester enregistrés (identifiants figés).
- **Gunpacks** : 233 fichiers en double exact (10,7 Mo), ex. `heraliz_6_armes_maxstuff/.../mp5k.gltf` = `heraliz_1_base_tacz/.../hk_mp5a5.gltf`.
  Packs d'autres auteurs : à ne toucher qu'en dernier.
- **Polices** : DM Sans et Onest copiées dans 13 de nos modules (86 copies en trop, 3 Mo), chacune chargée à part par le jeu.
  Les fichiers de police des mods pourraient pointer vers une seule copie (noyau).
- **Restes d'essais** : outil de tournage vidéo `equipement/src/client/java/fr/heraliz/armory/client/devtools/CinemaClient.java`
  (inactif sans réglage Java, mais livré) ; bot d'essai de la réanimation et son œuf
  (`survie/src/commun/java/fr/heraliz/reanimation/registry/ModItems.java:19`, entité `reanimation:bot`) alors que
  `/reanimation bot` a été retirée ; copies inutilisées des classes « pont » (`Usine`, `UsineEntite`…) dans 14 modules.
- **Proposition** : retirer d'abord les textures OptiFine de cfm et les restes d'essais (petit), puis alléger les
  caisses vidées si elles ne reviennent pas (moyen). Les doublons des gunpacks : plus tard.
- **Décision** : « Les 8 caisses vidées reviendront-elles ? Sinon on allège leurs modèles (les blocs restent). »

### 12. Réglages morts — **mineur**
- **18 réglages « joueur »** dans `serveur/config/` (`tacz-client.toml`, `heraliz_usure-client.toml`,
  `reanimation-client.toml`, `Matmos Config.toml`, `kiwi-client.yaml`…, ajoutés le 20/10) : copiés sur le Ptero à chaque
  mise en ligne, jamais lus par un serveur, et jamais donnés aux joueurs (le launcher n'envoie que les mods et `tacz`).
  Si ces réglages (viseur, HUD…) étaient voulus pour les joueurs, ils ne leur arrivent pas.
- **5 réglages des boss** (`serveur/defaultconfigs/heralisz_mammoth|mere|n07|plague|sylvorn-server.toml`) recopiés dans
  le monde à chaque mise en ligne (`.github/workflows/serveur.yml:479-482`) alors que les boss ne sont pas installés.
- **Actions des boss** encore dans le panel du staff (`boutique/src/commun/java/fr/heraliz/grades/staff/ActionStaff.java:48-49`,
  déjà noté comme « répondent impossible »).
- `serveur/config/spartan-common.toml` : `flyHoverItems` cite des jetpacks de Mekanism (mod absent).
- **Proposition** : supprimer (ou, pour les 18 réglages joueur, les donner par le launcher). Effort : **petit**.
- **Décision** : « Les réglages joueur de `serveur/config` : on les donne aux joueurs, ou on les supprime ? »

---

## Tableau 1 — par module : ce qui part chez le joueur et pourrait rester au serveur

| Module | Peut passer côté serveur | Doit rester partagé (ne pas toucher) |
|---|---|---|
| noyau | déclaration de `heraliz_noyau-server.toml` et son envoi (pt 3) | API lues par réflexion (`CombatTag`, `Matchs`, `ATerre`, `EtatClient`, `Portes`) |
| armes | `heralisz_tactical-common.toml` écrit chez le joueur (pt 4) | `ServerConfig` (lu par 12 classes pour la prédiction), dégâts envoyés exprès pour les infobulles |
| equipement | `heraliz_armory-common.toml` (pt 4), outil de tournage (pt 11) | catalogues d'armures (infobulles) |
| sacs | — (code de MrCrayfish, LGPL) | tout |
| loot | envoi de `heraliz_loot-server.toml` (pt 3) | formes des conteneurs, raretés (affichage) |
| bunker | `heralisz_bunker-common.toml` (cartes : utilisations, recharges) (pt 4) | code HBM (GPL) |
| monde | `heraliz_extraction-common.toml` (pt 4) ; envoi de zombies / distances / cycle / spawn / décors (pt 3) | trajectoires des frappes, train, règles d'usure (aperçu de réparation) |
| survie | envoi de `reanimation-server.toml` (pt 3) ; bot d'essai (pt 11) | état « à terre » appliqué des deux côtés (voulu) |
| clans | `heralisz_clans-common.toml` (anti-boost) et `heralisz_economie-common.toml` (pt 4) | `ClansApi`, `EconomieApi` (lues par d'autres mods) |
| bases | console admin (pt 9), `crac-admin.toml` chez le joueur (pt 4) | `crac-server/building/bases` (aperçus de construction) |
| events | non livré | — |
| clashwar | `CwConfig`, `Tableau`, `CwEtat`, `moteur/api` (pt 10) | paquets, HUD |
| evenements | — | — |
| pnj | — (quêtes, prix, recyclage déjà côté serveur) | — |
| boutique | commandes console dans les réglages du casino (pt 7) ; caisses vidées (pt 11) | moteurs des machines (animations ; le serveur tire les résultats), règles de l'hôtel des ventes (affichage des taxes) |
| emotes, voix, interface | — | — |
| anticheat | décision du Gardien (pt 1), liste des overlays et signatures (pt 2) | défi d'intégrité, format des rapports |

## Tableau 2 — ce que télécharge un joueur (Mo)

Mods des autres auteurs (taille exacte du fichier) :

| Mod | Taille | Remarque |
|---|---|---|
| tacz 1.1.8-hotfix | 54,8 | dont 48,5 inutiles (pt 6) |
| cfm 7.0.0-pre36 PATCH (meubles ArkaniaZ) | 33,0 | dont 6,3 inutiles (pt 11) |
| MAtmos 7.1 | 21,1 | ambiances (déjà connu : T-perf-10) ; coupé côté serveur par le noyau |
| geckolib222 4.4.7 | 15,2 | version ArkaniaZ (effets, sons) |
| ywzj_vehicle 0.5.10-fuse | 10,2 | |
| Kiwi | 1,6 | demandé par PassableFoliage |
| entityculling (joueurs seulement) | 1,4 | |
| embeddium (joueurs seulement) | 1,3 | |
| cloth-config | 1,1 | seulement des écrans de réglages ; inutile sur le serveur (à vérifier avant de l'en retirer) |
| modernfix | 1,0 | |
| curios, taczaddon, framework, zombieawareness, MutantsZombies, memoryleakfix, player-animation-lib, TCTcore, ferritecore, coroutil, PassableFoliage, packetfixer | 0,02 à 0,4 chacun (2,3 au total) | |
| **Total** | **≈ 143** | |

Nos jars joueurs (**estimation** : ressources compressées + code) : boutique ≈ 52, loot ≈ 21, equipement ≈ 18,
monde ≈ 17, armes ≈ 16, interface ≈ 8, bunker ≈ 7, bases ≈ 6, survie ≈ 5, sacs ≈ 3, emotes ≈ 2, clans ≈ 2, clashwar,
pnj, voix ≈ 1 chacun, noyau, evenements, spartan < 0,5. **Total ≈ 160.**

Dossier `tacz` (gunpacks, sans leurs `data/`) : **≈ 192**, surtout `heraliz_2_armes_nitrogen` (≈ 74) et
`heraliz_1_base_tacz` (≈ 67).

**Total ≈ 495 Mo** (sans Minecraft et Forge). Gains possibles repérés : ≈ 48 (TaCZ) + 6 (cfm) + 8 (caisses) + 11
(doublons) + 3 (polices) ≈ 76 Mo.

---

## Ce qui n'a pas été fait ni testé

- Rien testé en jeu, rien fabriqué : tailles de nos jars estimées.
- L'envoi des fichiers SERVER par Forge (pt 3) est confirmé par le code de CRac, pas par une capture réseau.
- Pt 6 : que TaCZ ne lise jamais son pack intégré est déduit de son code (`DefaultPackDebug`), à confirmer par un essai.
- Les textures du pack imposé (`interface`, 1 286 textures de blocs) et les modèles « anciens » des portes HBM n'ont pas
  été jugés (usage à vérifier en jeu).

## Résumé

1. La coupe client / serveur est bien faite dans le code : règles, prix, loot et détections de Spartan restent au serveur.
2. Les fuites viennent surtout des **réglages** : 8 fichiers serveur envoyés à chaque connexion et 7 fichiers écrits en clair chez chaque joueur, dont la règle anti-boost (pts 3-4).
3. Anticheat : le **Gardien** se laisse tromper par une simple phrase envoyée par le jeu, et la partie joueur de Spartan montre ce qu'elle ignore (pts 1-2).
4. Nos jars se décompilent presque comme le code source ; l'outil de brouillage est cassé et jamais utilisé (pt 5).
5. Aucun mod en double ni reste des boss chez les joueurs, mais environ 76 Mo inutiles dans le téléchargement, dont 48 Mo dans TaCZ (pts 6, 11).
