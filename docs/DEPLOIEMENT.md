# Mettre le nouveau serveur en ligne (Ptero) — liste à suivre

Écrit le 25/10 après l'audit, à partir de ce qui est réellement sur le Ptero aujourd'hui (lu en lecture seule).
Il n'y a pas encore de mise en ligne automatique pour ce projet (étape 7) : tout se fait à la main, dans cet ordre.
**Depuis le 26/10, la mise en ligne se fait par GitHub** (`docs/MISE_EN_LIGNE_GITHUB.md`) : cette liste reste pour une
installation à la main et pour ce que GitHub ne fait pas (sections 6 à 9, ajoutées le 19/11 après l'audit des réglages ;
section 10, 20/11 : changer de machine).

## 0. Avant tout

- **Couper la mise en ligne automatique de l'ancien dépôt GitHub** (workflow « Serveur HeraliZ » de
  `HeraliZ-ServeurMain`) : à chaque envoi sur sa branche `main`, il remplace le dossier `mods/` du Ptero par les
  ANCIENS jars. Tant qu'il est actif, un envoi de l'autre personne sur l'ancien dépôt efface ce déploiement.
- **Ne jamais lancer de script de pose (props, `forceload`, `setblock`, WorldEdit) pendant que des joueurs jouent** :
  le 24/10, pendant l'ouverture (jusqu'à 58 joueurs), 2 291 `forceload` et 1 426 `setblock` ont été joués par la console.
- Sauvegarde complète du serveur depuis le panel (onglet Backups), serveur arrêté.

## 1. Dossier `mods/` du Ptero : le vider, puis le remplir

Les mods ont été regroupés : les anciens jars contiennent les MÊMES identifiants que les nouveaux. Si un ancien jar
reste, Forge refuse de démarrer (« mod en double »). Plusieurs nouveaux jars ont aussi **le même nom de fichier** que les
anciens avec un contenu différent : un outil qui ne copie « que ce qui a changé » d'après le nom peut les sauter.

0. Juste avant l'envoi, sur le PC : `python outils/verifier.py` = 0 problème, **après** la dernière fabrication et sans
   fabrication en cours (fichier `construire.verrou` absent). Il contrôle aussi que chaque jar de `livraison/` est bien
   le jar fabriqué (25/10 : un jar copié pendant une fabrication, pas encore réobfusqué, faisait planter le serveur au
   démarrage).
1. Supprimer **tout** le contenu de `mods/` sur le Ptero.
2. Y envoyer **tout** `livraison/mods serveur/` (37 jars), rien d'autre.

Anciens jars qui doivent avoir disparu (vérifier) : `airstrike-*`, `heralisz_matchs-*`, `heralisz_utils-*`,
`heraliz_armory-*`, `heraliz_composants-*`, `heraliz_decors-*`, `heraliz_extraction-*`, `heraliz_grades-*`,
`heraliz_wonder-*`, `heralizcasino-*`, `heralizcrates-*`, et **`heraliz_boss-*`** : les boss sont mis de côté pour
l'ouverture (25/10, choix du propriétaire ; `MODULES_DE_COTE` de `outils/construire.py`), ni sur le serveur ni chez les
joueurs.

## 2. Réglages

- `config/` : envoyer `serveur/config/` par-dessus (sans rien supprimer d'autre), puis supprimer ce que listent
  `serveur/config_retires.txt` et `serveur/tacz_retires.txt`.
- `defaultconfigs/` : envoyer `serveur/defaultconfigs/`, **et** le recopier dans `dayz_map/serverconfig/` (sinon le monde
  garde les anciens réglages). Fichiers des mods retirés à supprimer de `dayz_map/serverconfig/` :
  `heraliz_wonder-server.toml`, `heralisz_matchs-moteur.toml` (gardé sans effet sinon).
  Le gunpack `tacz/heraliz_wonder` (armes du mode Zombies : Blundergat, Ray Gun, Thundergun…) était resté sur le Ptero :
  listé dans `serveur/tacz_retires.txt` le 27/10, la mise en ligne l'efface (redémarrer ensuite).
- `tacz/` : envoyer `serveur/tacz/`, puis le workflow « Gunpacks du serveur » (ou à la main) pour retirer du Ptero les
  gunpacks qui ne sont plus dans le dépôt (sinon carrés roses chez les joueurs).
  **26/10 : dossiers renommés.** Supprimer du Ptero les dossiers entiers `arkaniaz_pack_0_converted` à
  `arkaniaz_pack_5_converted`, `arkaniaz_custom_converted` et `Cislumen_Armory` (fin de `serveur/tacz_retires.txt`) :
  sinon chaque objet existe deux fois. Chez les joueurs, le launcher les retire lui-même (depuis la 1.15.3, tout fichier
  du dossier `tacz` ou `modstore` qui n'est plus dans le pack, `retirerAnciennesVersions`).
- `plugins/` : les jars de `livraison/plugins/`.
- `server.properties` : celui du dépôt dit maintenant `level-name=dayz_map` (comme le Ptero) et `sync-chunk-writes=false`.
  Sur le Ptero, changer au moins `sync-chunk-writes=false` (gel de 6 s à une sauvegarde pendant l'ouverture du 24/10 ;
  fait le 27/10). **19/11 : y mettre aussi `rate-limit=1000`** (section 9) : la mise en ligne n'envoie jamais ce fichier.
  Changer aussi la description du serveur (`motd`) : celle du Ptero est vulgaire et s'affiche dans la liste des serveurs
  de tous les joueurs.
- `bukkit.yml` et `spigot.yml` : **envoyés à chaque mise en ligne depuis le 19/11** (section 6 ; avant : seulement avec
  « Installation complète »). Les monstres naturels et les chauves-souris de `bukkit.yml` ne comptent pas (aucune
  apparition naturelle : le peuplement des zombies est fait par HeraliZ Utils).
- **Java** (à demander à l'hébergeur, la commande est verrouillée) : ligne complète et mémoire du conteneur en section 8.
  Le 27/10, l'hébergeur avait mis `-Xms32G -Xmx32G` sans les réglages Aikar : à 32 Go pile, Java coupe les « Compressed
  Oops » (`gc.log` : « Compressed Oops: Disabled »), chaque objet prend plus de place et les pauses du ramasse-miettes
  durent 85 à 165 ms serveur vide, alors que le serveur n'utilise qu'environ 4 Go (4,5 Go au test de 90 bots).
- Tronçons forcés par les scripts de pose (chargés à vie) : depuis le 02/11, le noyau les retire tout seul au démarrage
  dans chaque monde (`[monde] retirer_troncons_forces = true` de `heraliz_noyau-server.toml` ; la commande `/forceload`
  est retirée). Le journal du démarrage dit combien et où, et 30 s après, la ligne des tickets de chaque monde doit dire
  `forced x0`.

## 3. Joueurs

- Cette fabrication change plusieurs canaux réseau (caisses version 3, emotes version 2, grades…) : **tous les joueurs
  doivent avoir les nouveaux jars**, sinon ils ne peuvent pas se connecter.
- Sur la carte, garder `/gamerule doMobSpawning false` (mesuré : les monstres de Minecraft doublent le coût par tick ;
  les zombies viennent du peuplement de HeraliZ Utils) et `/gamerule keepInventory false`.

- Le pack du launcher doit être refait avec `livraison/mods client/` de **la même fabrication** que les jars serveur
  (canaux réseau : un joueur avec d'anciens jars ne peut pas se connecter, ou se connecte avec des écrans cassés).
- `livraison/spartan/officiel.json` → `config/spartan/officiel.json` du Ptero (sinon Spartan signale tous les nouveaux
  jars comme inconnus).

## 3 bis. Nouveautés du 25/10 (soir) à ne pas oublier

- Nouveau jar **`heraliz_evenements-…-serveur.jar`** (et `-client` chez les joueurs) : il fait partie de `livraison/`.
- `config/heraliz_evenements/` (envoyé avec `serveur/config/`) : `planning.json` (un largage **toutes les 3 h 30**, le
  premier 30 min après le démarrage), `largage.json` (point **tiré au hasard** sur la carte, annonce avec les coordonnées
  6 min avant, rappel 3 min avant : **plus besoin de poser des lieux**), `objets.json` (prix et chances des objets
  d'événement). Essai : `/event lancer largage` (sans points) ou `/event lancer largage avec_points` ; `/event` donne
  l'heure du prochain. Journal : `heraliz_evenements/journal.log`.
- **Objets d'événement à revendre** (dossier classé, mallette scellée, disque dur militaire) : toujours un dans la caisse
  du largage, rachetés par Mila (hérals + points de saison au clan, rien sans cœur). Au premier démarrage, le serveur crée
  `heraliz_evenements/objets.cle` (clé de signature) à sa racine : **ne jamais la supprimer ni la mettre dans le dépôt**
  (sinon les objets déjà trouvés ne se revendent plus). Essai du staff : `/event objet <type>` (hérals, aucun point).
- **Jars des joueurs à jour obligatoires** : les objets d'événement sont de nouveaux objets et l'écran des marchands a
  changé (canal des quêtes version 8) : un joueur avec l'ancien `heraliz_evenements` ou `heraliz_quetes` ne peut pas se
  connecter.
- **Saison** : rien à lancer. Au premier démarrage sans saison, la saison 1 commence toute seule. Pour repartir de 0 au
  moment de l'ouverture : `/saison reset` puis `/saison reset confirmer`. Date de fin : `/saison fin jj/mm/aaaa hh:mm`.
- `serveur/defaultconfigs/heraliz_noyau-server.toml` : `/event` permis sur la carte et en combat ; à recopier dans
  `dayz_map/serverconfig/` comme les autres.
- **Cycle jour / nuit** (HeraliZ Utils, `serveur/defaultconfigs/heraliz_cycle-server.toml`, à recopier dans
  `dayz_map/serverconfig/`) : 20 min de jour, 4 min de nuit. Le mod remet tout seul `doDaylightCycle` à false et fait
  avancer l'heure lui-même ; ne pas utiliser `/time set` (le cycle se recale), mais `/cycle info|jour|nuit|heure
  <hh:mm>|pause|reprendre` (boutons Jour / Nuit du panel du staff aussi).
- **Zombies** (`serveur/defaultconfigs/heraliz_zombies-server.toml`, nouveau dans le dépôt, à recopier dans
  `dayz_map/serverconfig/`) : peuplement par secteurs au hasard (jamais à la vue d'un joueur, moins près des extractions
  ouvertes), 400 zombies au plus (`max_total`, à remonter après mesure), bruit des tirs et des explosions, tenue « police ». Staff :
  `/zombies stats`, `/zombies zone ajouter <nom> <police|militaire|danger|village|calme> <rayon>` (zones gardées dans
  `heraliz_zombies/zones.json` à la racine du serveur). Banc mesuré : `docs/AUDIT_VISUEL_ZOMBIES_25OCT.md`.

## 4. Premier démarrage

- Démarrer, attendre « Done », lire le journal : aucune ligne `ERROR` de nos mods, aucune `Exception`.
- Se connecter avec un compte du staff, vérifier : `/diag`, `/kit`, `/home`, panel du staff (P), chat (T).
- Faire la liste `docs/TESTS_EN_JEU_25OCT.md`, puis un test à quelques joueurs avant toute ouverture publique.

## 5. Choix du 25/10 : on garde tout ce que les joueurs ont eu pendant la bêta (pas de `/wipe tout`)

Le nouveau serveur démarre sur le monde actuel `dayz_map` et sur les dossiers de la racine du Ptero (`heralisz_clans/`,
`heralisz_economie/`, `heraliz_grades/`, `heraliz_quetes/`, `heraliz_crates/`, `heralisz_camo/`, `heraliz_emote/`…) :
**ne jamais les supprimer ni les remplacer** pendant la mise en ligne.
- Gardé : inventaires, Curios, sacs, planque et coffre VIP, clans, banques, coffres de clan, hérals, saison, bases, homes,
  quêtes, grades, camos à vie, emotes.
- Converti tout seul à la connexion : clés physiques → clés virtuelles (« Mes clés » de `/boutique`), anciens pansements →
  Bandage.
- Disparaît : objets des mods retirés (Wonder Weapons, objets des modes Zombies et duels) ; points de réapparition posés sur
  un lit ; un clan de plus de 4 membres garde ses membres mais ne peut plus inviter.
- Bons gagnés dans une caisse avant la correction : ils portent tous le numéro du modèle ; le premier utilisé marche, les
  autres sont refusés (sans être supprimés) : le staff peut en refaire un avec `/bon editeur`, le bon en main.

## 6. Mise en ligne par GitHub : démarrage d'essai et fichiers de la racine (19/11)

- **Démarrage d'essai avant chaque envoi au Ptero** : après la fabrication, GitHub démarre le serveur fabriqué sur une de
  ses machines (nos jars, les plugins et les réglages du dépôt, le même Mohist, un monde plat neuf, aucun secret ;
  `.github/workflows/demarrage.yml`, `outils/demarrage_essai.py`). S'il n'arrive pas à « Done », ou si son journal
  montre un mixin qui ne s'applique pas ou un plantage, **rien n'est envoyé au Ptero** : le serveur en ligne garde la
  version d'avant (le 18/11, deux démarrages ratés avaient laissé le serveur à terre). La raison est dans le résumé de la
  tâche (« Démarrage d'essai ») ; 3 à 4 minutes de plus par mise en ligne.
  - En urgence seulement : « Run workflow » avec la case `sans_demarrage_essai`.
  - Pour essayer une branche avant de la passer sur `main` : onglet Actions → « Démarrage d'essai » → « Run workflow »
    (choisir la branche).
  - Ce qu'il ne voit pas : un problème qui n'apparaît qu'avec des joueurs connectés, sur la vraie carte ou avec AzLink
    (pas dans l'essai).
- **`bukkit.yml` et `spigot.yml` partent à chaque mise en ligne** (aucun secret dedans) et comptent au redémarrage
  suivant. `server.properties` n'est **jamais** envoyé par la mise en ligne normale (adresse et port propres au Ptero) :
  ses changements se font à la main dans le panel (« Installation complète » l'envoie aussi, avec Mohist).
- Réglages de ces deux fichiers depuis le 19/11 :
  - `bukkit.yml` : sauvegarde automatique toutes les **10 min** au lieu de 5 (`ticks-per.autosave` 6000 → 12000 : deux
    fois moins de pics ; au pire 10 min de jeu perdues après un plantage ; un joueur est toujours sauvegardé à sa
    déconnexion et nos mods ont leurs propres sauvegardes) ; « Can't keep up » de nouveau écrits dans le journal
    (`warn-on-overload` → `true`) ;
  - `spigot.yml` : cache des pseudos limité à **1 000** (`user-cache-size` 100 000 → 1 000 : `usercache.json`, réécrit à
    chaque connexion, reste petit) ; les spectateurs ne réveillent plus les créatures autour d'eux (`ignore-spectators`
    → `true`). `save-user-cache-on-stop-only` reste à `false` : la recherche d'un joueur déconnecté pour l'accès à une
    base et le contrôle des surnoms (`/avantages nick`) lisent ce fichier, un nouveau joueur doit y être tout de suite.

## 7. Grosses opérations WorldEdit (staff)

WorldEdit fait tout son travail sur le fil du serveur : pendant une opération, le jeu est figé pour tout le monde. Le
19/11, deux `//replace` sur une grande zone ont figé le serveur 78 s et 63 s ; 76 alertes de lenteur sur 193 venaient de
WorldEdit.

Limites depuis le 19/11 (`serveur/plugins/WorldEdit/config.yml`, envoyé à chaque mise en ligne, relu au redémarrage ou
par `/we reload`) :
- une opération s'arrête à **250 000 blocs changés** (« Max blocks changed in an operation reached ») ; ce qui est déjà
  fait reste annulable par `//undo` ;
- pour une grosse opération voulue : `//limit 2000000` au plus, puis `//limit 250000` après ;
- rayon des commandes à rayon (`//sphere`, `//cyl`, `//drain`, `//replacenear`…) : 64 au plus ;
- 6 retours en arrière (`//undo`) par personne au lieu de 15 (chaque opération gardée tient tous ses blocs en mémoire).

Comment faire une grosse opération :
1. **Quand personne ne joue** : aujourd'hui la liste blanche le permet ; après l'ouverture, la nuit ou pendant une
   maintenance (liste blanche remise le temps du chantier).
2. **En morceaux** : `//size` avant chaque `//set`, `//replace` ou `//paste` (il donne le nombre de blocs de la
   sélection) ; au-delà de 250 000, couper la sélection en plusieurs parties. **Jamais de `//replace` ni de `//set` sur une
   zone plus grande que celle chargée autour de soi** (environ 120 blocs, la distance de vue) : WorldEdit lit chaque bloc
   et charge chaque tronçon de la sélection sur le fil du serveur, même ceux qu'il ne change pas. C'est ce qui a figé le
   serveur 78 s, et la limite ne l'empêche pas : elle ne compte que les blocs changés, pas ceux qui sont lus.
3. **Avant un gros `//paste`** : `//perf neighbors off` et `//perf update off` (les blocs collés ne préviennent plus
   leurs voisins un par un : beaucoup moins de travail) ; `//paste -a` pour ne pas coller l'air. Après :
   `//perf neighbors on` et `//perf update on`, puis vérifier clôtures, vitres et murs (refaire à la main un raccord qui
   manque).
4. **Pas de `//regen`** (il crée un monde temporaire, `worldeditregentempworld`).
5. **Déplacements** : voler jusqu'au chantier plutôt qu'un `/tp` très loin (un `/tp` vers un endroit pas encore chargé lit
   des centaines de tronçons sur le fil du serveur : jusqu'à 1 à 2,5 s de gel).
6. Une opération arrêtée par la limite : `//undo`, puis recommencer en plus petit.

## 8. Java, mémoire et processeur (à transmettre à l'hébergeur)

La ligne de lancement est verrouillée dans le panel : c'est l'hébergeur qui la règle (audit du 19/11, F4 et F5 ; mêmes
réglages que le banc de charge, `outils/banc_charge.py`).

**Mémoire du conteneur : 20 Go au moins, pour 16 Go donnés à Java.** Java prend 3 à 4 Go de plus que ce qu'on lui donne
(fils, réseau, code chargé) : 16 Go pour Java dans un conteneur de 16 Go, et le système tue le serveur. Le serveur utilise
en vrai 1 à 4 Go (4,5 Go à 90 bots) : 16 Go laissent une grande marge.

Ligne de lancement demandée (Java 17 ; `-Xms` égal à `-Xmx` ; journal du ramasse-miettes léger, 5 fichiers de 10 Mo au
plus) :

```
java -Xms16G -Xmx16G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
     -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
     -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M
     -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
     -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90
     -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem
     -XX:MaxTenuringThreshold=1 -Dfile.encoding=UTF-8
     -Xlog:gc:file=logs/gc.log:time,uptime:filecount=5,filesize=10M
     -jar mohist-1.20.1-916-server.jar nogui
```

(sur une seule ligne dans le panel ; le nom du jar est celui de `serveur/mohist.sha256`).

Si le conteneur n'a que **16 Go** : `-Xms12G -Xmx12G` et ces valeurs à la place (celles du banc) :
`-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20
-XX:InitiatingHeapOccupancyPercent=15`, le reste identique.

À ne pas faire : donner à Java toute la mémoire du conteneur ; 32 Go pile (plus de « Compressed Oops », voir section 2) ;
ZGC avec Java 17 ; un `-Xms` plus petit que `-Xmx`. `-XX:+AlwaysPreTouch` réserve toute la mémoire au démarrage (10 à
20 s de plus) : le conteneur doit vraiment l'avoir.

Vérifier après le changement :
- `/diag` : mémoire maximale 16 384 Mo (ou 12 288) ;
- `logs/gc.log` existe et grandit ; les pauses « Pause Young » restent vers 20 à 50 ms (80 à 200 ms avant) ;
- après une mise à jour de Mohist ou une « Installation complète », le premier démarrage peut tourner sans ces réglages
  (Mohist installe Forge puis se relance tout seul) : redémarrer une seconde fois, puis revérifier `/diag`.

**Lu sur le Ptero le 20/11** (`/diag`, « Lenteurs du serveur ») : la ligne ci-dessus est en place (16 Go fixes, réglages
Aikar, 16 cœurs vus, machine de 64 Go, « Compressed Oops » actifs), avec deux différences : le journal du ramasse-miettes
est à la racine du serveur (`gc.log`, plus détaillé : `-Xlog:gc*`, 5 fichiers de 20 Mo, lu par « Lenteurs du serveur »),
et **`-Dfile.encoding=UTF-8` manque** (Java 17 prend alors l'encodage du système, qui n'est pas toujours UTF-8 dans un
conteneur ; `/diag etat` dira lequel après la mise en ligne du Noyau 1.1.8 : s'il n'est pas UTF-8, l'ajouter).
Mesuré : 29 pauses en 3 h 30 serveur presque vide, 25 à 50 ms d'habitude, 125 ms au pire hors démarrage ; aucune pause
complète.

**Processeur** : le jeu (le « tick ») tourne sur **un seul cœur** : c'est la vitesse d'un cœur qui compte, pas leur
nombre. Celui du Ptero (Xeon E5-2690 v2, 2013) est environ 2 fois plus lent par cœur qu'un PC récent ; à 100 bots, sur les
machines de GitHub (plus rapides), la marge sous 50 ms par tick est déjà mince. Pour 150 joueurs, demander une machine
récente à haute fréquence (2022 ou plus, environ 5 GHz en pointe : Ryzen 7000/9000, EPYC 4004, Xeon récents), des cœurs
réservés (pas partagés) et un disque NVMe.

**Mohist** : le build 916 relève toute la pile d'appels à chaque permission refusée (35 à 60 ms par mort, `/spawn`,
extraction ou changement de monde). Les builds après le 20/07/2025 ne le font plus. Mise à jour à faire **après un banc
de charge et un essai** (nouveau jar dans la Release « base-serveur » et `serveur/mohist.sha256`, puis « Installation
complète » et deux redémarrages). En attendant, LuckPerms ne renvoie plus toute la liste des commandes à chaque
changement de droits (`update-client-command-list: false`, 19/11).

## 9. Limites des paquets et des connexions (19/11)

**PacketFixer** (`serveur/config/packetfixer.properties`, envoyé à chaque mise en ligne, compte au redémarrage) : avant,
aucune limite (`allSizesUnlimited=true`) : un seul joueur, avant même de taper son mot de passe, pouvait envoyer un
paquet de centaines de Mo, ou un petit paquet compressé qui en annonce 2 Go une fois décompressé, et faire tomber le
serveur.

| Réglage | Avant | Maintenant | Pourquoi cette taille |
|---|---|---|---|
| `decoderSize` | sans limite | 32 Mo | un paquet une fois décompressé, reçu ou envoyé. Le plus gros connu : les armes TaCZ envoyées à chaque connexion (0,9 Mo de textes pour nos 13 gunpacks) ; Minecraft seul s'arrête à 8 Mo |
| `packetSize` | sans limite | 16 Mo | données d'un paquet de mod, dans les deux sens ; ce que les joueurs envoient : 30 Ko au plus (morceaux de l'anticheat ; voix : 512 octets) |
| `nbtMaxSize` | sans limite | 16 Mo | NBT d'un objet reçu d'un joueur ; un sac plein d'armes équipées : quelques centaines de Ko |
| `stringSize`, `varInt21` | sans limite | gardés | TaCZ envoie ses textes sans taille fixée ; le paquet entier reste borné par `decoderSize` |
| `varInt`, `varLong` | sans limite | 5 et 10 | valeurs de Minecraft |

Si un joueur est un jour exclu avec « Badly compressed packet », « Payload may not be larger » ou « Packet too big » :
doubler la valeur en cause, redémarrer, et le signaler. Ce que le réglage ne couvre pas : PacketFixer accepte aussi des
trames réseau de n'importe quelle longueur (sans réglage) ; une limite dans le noyau serait la vraie protection (à
demander au développement).

**`rate-limit=1000`** (`server.properties`, **à mettre à la main dans le panel**, la mise en ligne n'envoie pas ce
fichier ; avant : 0, aucune limite) : un joueur qui envoie plus de 1 000 paquets par seconde en moyenne est exclu. Un
joueur normal en envoie 20 à 100 (déplacements, tirs, voix).

**AuthMe** : les pseudos `hzbot001` à `hzbot200` (bots du banc de charge) entraient sans mot de passe. Retirés du serveur
en jeu le 19/11 (compte au redémarrage qui suit la mise en ligne) ; le banc ne les met que dans son propre serveur d'essai
(`outils/serveur_local.py --banc`) et `outils/verifier.py` refuse la mise en ligne s'ils reviennent dans
`serveur/plugins/AuthMe/config.yml`. Ne jamais ajouter `-Dheraliz.essai.sansAuthme=true` à la ligne de lancement du Ptero
(réservé aux serveurs d'essai sur 127.0.0.1).

À décider par le propriétaire (dans `server.properties`, à la main) :
- `enable-query=false` (aujourd'hui `true` : n'importe qui peut lire la liste des plugins, leurs versions et les
  joueurs) : à garder seulement si un site de classement s'en sert ;
- `player-idle-timeout` (aujourd'hui 0) : 20 ou 30 minutes libèrent la place et les tronçons d'un joueur parti de son
  écran.

## 10. Nouvelle machine : liste à suivre (20/11)

Pour passer le serveur sur une machine plus puissante. Une seule chose à la fois : pas de mise à jour de Mohist ni de
gros changement de mods le même jour (si quelque chose ne va pas, on saura que c'est la machine).

**Mesuré le 20/11** (serveur en ligne, « Lenteurs du serveur » ; banc de charge sur les machines de GitHub) : Java 17,
16 Go donnés à Java, réglages de la section 8 en place, 16 cœurs vus, machine de 62,8 Go. Le serveur garde **1 à 3 Go** de
mémoire vivante à vide, **3,5 Go au plus avec 100 bots** (environ 23 Mo par joueur, surtout les tronçons autour d'eux) : à
150 joueurs, environ 5 Go. Réseau du banc : **19,5 Mbit/s en sortie pour 100 bots** (24 Ko/s par joueur, 24 000 paquets
par seconde, compressés au cinquième).

### Ce qu'il faut demander à l'hébergeur

| Quoi | Minimum | Pourquoi |
|---|---|---|
| Processeur | récent, haute fréquence (environ 5 GHz en pointe : Ryzen 7000/9000, EPYC 4004/4005, Xeon récents), 8 cœurs réservés ou plus | le jeu tourne sur **un seul cœur** : c'est sa vitesse qui compte (section 8) ; les autres cœurs servent au réseau, à la compression, au ramasse-miettes, à l'anticheat, à la voix |
| Mémoire du conteneur | **24 Go** (20 Go au strict minimum) | 16 Go pour Java + 3 à 4 Go que Java prend en plus + marge. Ce qui reste sur la machine sert au système à garder les fichiers du monde en mémoire (tronçons relus plus vite) : utile, mais à ne pas donner à Java |
| Disque | **NVMe local** (pas un disque réseau), 50 Go libres de plus que la taille actuelle du serveur (le 20/11 : monde 1,2 Go, dont 600 Mo de mondes qui ne sont plus chargés ; plus de 8 Go de vieilles copies à la racine, voir « Copier le serveur » ; le détail : « Lenteurs du serveur », partie « Mémoire et disque ») | chaque opération d'argent (banque du clan, hôtel des ventes) est écrite sur le disque AVANT d'être faite, sur le fil du jeu (protection contre les copies) ; chaque téléportation et chaque réapparition lisent des centaines de tronçons |
| Réseau | **200 Mbit/s garantis en sortie** (1 Gbit/s idéal), protection anti-DDoS qui laisse passer l'UDP 24454 | prévu 35 à 50 Mbit/s en moyenne à 150 joueurs, le double dans les pics (arrivées en groupe, redémarrage, largage), plus la voix (5 à 15 Mbit/s) |
| Ports | TCP **25565** (le jeu) et UDP **24454** (la voix de proximité) | dans Pterodactyl : une deuxième « allocation » 24454 sur la même adresse que le jeu. Rien d'autre (RCON est coupé ; le « query » UDP 25565 seulement si un site de classement le demande : le launcher et le site ne s'en servent pas) |
| Base MySQL | une base pour LiteBans | les sanctions (bans, mutes) y sont gardées, pas dans le dossier du serveur |

### Ligne Java (Java 17, comme aujourd'hui)

Pour un conteneur de 24 Go (ou plus) : la ligne de la section 8, avec le journal du ramasse-miettes à la racine comme
aujourd'hui (l'outil « Lenteurs du serveur » le lit là) et **`-Dfile.encoding=UTF-8`**, absent du Ptero actuel (sans
lui, Java 17 prend l'encodage du système) :

```
java -Xms16G -Xmx16G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
     -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
     -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M
     -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
     -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90
     -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem
     -XX:MaxTenuringThreshold=1 -Dfile.encoding=UTF-8
     -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20M
     -jar mohist-1.20.1-916-server.jar nogui
```

- Machine plus grosse (64 Go ou plus) : **garder 16 Go** pour Java. Plus ne sert à rien (le serveur en utilise 5) et
  32 Go pile est pire (section 2). La mémoire en plus sert au système (fichiers du monde).
- Conteneur de 16 Go seulement : `-Xms12G -Xmx12G` et les valeurs « 16 Go » de la section 8.
- Rester sur Java 17 et G1 (Mohist 1.20.1, les plugins et nos mixins sont essayés avec lui).

### Copier le serveur

1. Prévenir les joueurs, puis arrêter le serveur proprement (`/stop` ou `redemarrage`, attendre la fin de la
   sauvegarde). Faire une sauvegarde complète depuis le panel (onglet Backups) de l'ancienne machine.
2. Copier **tout le dossier du serveur** vers la nouvelle machine (sauvegarde du panel restaurée, ou SFTP), au moins :
   le monde `dayz_map` (avec `dimensions/` : spawn, arènes, tutoriel), les dossiers de nos mods à la racine
   (`heralisz_clans`, `heralisz_economie`, `heraliz_quetes`, `heraliz_grades`, `heraliz_crates`, `heralisz_camo`,
   `heraliz_emote`, `heraliz_evenements` et surtout sa clé `objets.cle`, `heraliz_zombies`…), `config/` et
   `defaultconfigs/`, `plugins/` avec leurs données (AuthMe : les mots de passe des joueurs ; LuckPerms : les grades ;
   WorldGuard : les régions ; AzLink : le lien avec le site), `usercache.json`, `ops.json`, `whitelist.json`,
   `banned-players.json`, `banned-ips.json`, `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`,
   `mohist-config/`, `eula.txt`, le jar de Mohist et `libraries/`. Ne PAS copier `.bash_history` (à la racine du Ptero
   depuis 2025 : un historique de commandes, qui peut contenir des mots de passe) : le supprimer ; les vieux guides
   `.md` de la racine ne servent à rien. Mondes qui ne sont plus chargés (`dayz_map/dimensions/crac/bases` et ses deux
   copies `bases_ancien_…`, `heralisz_matchs`, `heralisz_bases` : environ 600 Mo) : à vous de choisir de les copier ou de
   les garder seulement dans la sauvegarde (rien ne change en jeu). Même choix pour les vieilles copies de la racine,
   que le serveur ne lit pas : `dayz_map_ancien_2026-10-11_00h46` (5,9 Go, le monde du 11/10), `dayz_map_echec_…`
   (250 Mo), `HeraliZ_ancien_…`, `tacz_backup`, `limitless_vehicle_backup`, `dynmap` (données d'un plugin de
   carte qui n'est plus là, très nombreux fichiers), et un dossier de 1,3 Go qui ne vient pas du serveur (son nom n'est pas écrit ici, dépôt public).
3. **LiteBans** : exporter la base MySQL de l'ancienne machine (`s1_LiteBans`) et l'importer dans la nouvelle
   (l'hébergeur sait le faire). Sinon l'historique des sanctions est perdu. Si son adresse n'est plus `127.0.0.1:3306`,
   la changer dans `serveur/plugins/LiteBans/config.yml` (demander au développement).
4. `server.properties` de la nouvelle machine : `server-ip` et `server-port` de la nouvelle allocation ; en profiter pour
   `rate-limit=1000` (section 9, toujours à 0 sur le Ptero le 20/11).
5. **Adresse `play.heraliz.fr`** : la veille, baisser la durée de cache (TTL) de l'enregistrement DNS à 5 minutes ; le
   jour J, faire pointer l'enregistrement A (et SRV s'il y en a un) vers la nouvelle adresse. Le launcher et les jeux des
   joueurs n'ont rien à changer.

### Secrets de GitHub à changer (Settings → Environments, section 7 de `docs/MISE_EN_LIGNE_GITHUB.md`)

- **`production`** : `PTERO_SFTP_HOST`, `PTERO_SFTP_PORT`, `PTERO_SFTP_USER`, `PTERO_SFTP_PASSWORD`, `PTERO_URL` (si le
  panel change), `PTERO_SERVER_ID`, `PTERO_API_KEY` (nouvelle clé de l'API client du panel), `LITEBANS_DB_USER` et
  `LITEBANS_DB_PASSWORD` (la nouvelle base).
- **`lecture`** : `PTERO_SFTP_HOST`, `PTERO_SFTP_PORT`, et `PTERO_SFTP_USER` / `PTERO_SFTP_PASSWORD` d'un nouveau
  sous-utilisateur **en lecture seule** sur le nouveau panel (« file.sftp », « file.read », « file.read-content »).
- **Variable `PTERO_SFTP_EMPREINTE`** (onglet Variables) : la clé de la nouvelle machine. La première mise en ligne
  s'arrête sur une erreur de clé d'hôte et son résumé donne les lignes à recopier (section 8 de
  `docs/MISE_EN_LIGNE_GITHUB.md`).
- Rien à changer pour le site, le launcher, Discord, Spartan, HeraliBot, TopServeurs.

### Premières vérifications

1. Journal du démarrage : « Done », aucune ligne `ERROR` de nos mods, et « [armurerie] contenu des sacs : envoyé
   seulement au porteur (actif) ».
2. `/diag etat` : « tas maximum 16384 Mo, réservé au départ 16384 Mo », le **nouveau processeur** (« processeur : … »),
   « encodage par défaut UTF-8 », « Disque du serveur : … Go libres ».
3. Voix : deux joueurs se parlent, puis `/voix stats` : les sessions « en UDP » = le nombre de joueurs connectés. Si
   c'est 0, le port UDP 24454 est fermé (la voix passe alors par la connexion du jeu : elle marche, mais saccade quand le
   jeu rame).
4. Un ancien joueur se connecte avec son mot de passe (AuthMe) ; `/lp user <pseudo du staff> info` (grades) ; `/rg list`
   dans chaque monde (WorldGuard) ; `/history <pseudo>` (LiteBans) ; un clan, sa banque, une quête, une base : tout est
   là.
5. GitHub : une mise en ligne « Serveur HeraliZ » verte ; « Lenteurs du serveur » lancé depuis `main` sans la case
   profil : partie « Mémoire et disque du serveur » : pauses « Pause Young » de 20 à 50 ms, aucune au-dessus de 200 ms,
   pas de « Pause Full ».
6. Première soirée avec des joueurs : « Lenteurs du serveur » avec la case profil (2 minutes) ; la partie « Paquets » du
   profil donne ce qui part vers les joueurs, par sorte (à comparer au banc).
