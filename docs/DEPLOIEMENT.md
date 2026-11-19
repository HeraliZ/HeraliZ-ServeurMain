# Mettre le nouveau serveur en ligne (Ptero) — liste à suivre

Écrit le 25/10 après l'audit, à partir de ce qui est réellement sur le Ptero aujourd'hui (lu en lecture seule).
Il n'y a pas encore de mise en ligne automatique pour ce projet (étape 7) : tout se fait à la main, dans cet ordre.
**Depuis le 26/10, la mise en ligne se fait par GitHub** (`docs/MISE_EN_LIGNE_GITHUB.md`) : cette liste reste pour une
installation à la main et pour ce que GitHub ne fait pas (sections 6 à 9, ajoutées le 19/11 après l'audit des réglages).

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
