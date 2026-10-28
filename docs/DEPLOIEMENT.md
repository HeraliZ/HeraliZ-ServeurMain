# Mettre le nouveau serveur en ligne (Ptero) — liste à suivre

Écrit le 25/10 après l'audit, à partir de ce qui est réellement sur le Ptero aujourd'hui (lu en lecture seule).
Il n'y a pas encore de mise en ligne automatique pour ce projet (étape 7) : tout se fait à la main, dans cet ordre.

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
- `tacz/` : envoyer `serveur/tacz/`, puis le workflow « Gunpacks du serveur » (ou à la main) pour retirer du Ptero les
  gunpacks qui ne sont plus dans le dépôt (sinon carrés roses chez les joueurs).
  **26/10 : dossiers renommés.** Supprimer du Ptero les dossiers entiers `arkaniaz_pack_0_converted` à
  `arkaniaz_pack_5_converted`, `arkaniaz_custom_converted` et `Cislumen_Armory` (fin de `serveur/tacz_retires.txt`) :
  sinon chaque objet existe deux fois. Chez les joueurs, le launcher les retire lui-même (depuis la 1.15.3, tout fichier
  du dossier `tacz` ou `modstore` qui n'est plus dans le pack, `retirerAnciennesVersions`).
- `plugins/` : les jars de `livraison/plugins/`.
- `server.properties` : celui du dépôt dit maintenant `level-name=dayz_map` (comme le Ptero) et `sync-chunk-writes=false`.
  Sur le Ptero, changer au moins `sync-chunk-writes=false` (gel de 6 s à une sauvegarde pendant l'ouverture du 24/10 ;
  fait le 27/10).
  Changer aussi la description du serveur (`motd`) : celle du Ptero est vulgaire et s'affiche dans la liste des serveurs
  de tous les joueurs.
- `bukkit.yml` : envoyer celui du dépôt (monstres naturels 70 au lieu de 250, chauves-souris coupées : le peuplement
  des zombies est fait par HeraliZ Utils).
- **Java** (à demander à l'hébergeur, la commande est verrouillée) : réglages Aikar du §12 de l'ancien CLAUDE.md, avec
  `-Xms` égal à `-Xmx` (12 à 16 Go), au lieu de `-Xmx32G` seul. Le 27/10, l'hébergeur a mis `-Xms32G -Xmx32G` sans les
  réglages Aikar : à 32 Go pile, Java coupe les « Compressed Oops » (`gc.log` : « Compressed Oops: Disabled »), chaque objet
  prend plus de place et les pauses du ramasse-miettes durent 85 à 165 ms serveur vide, alors que le serveur n'utilise
  qu'environ 4 Go (4,5 Go au test de 90 bots). Demander 16 Go (31 Go au plus) avec `-XX:MaxTenuringThreshold=1` et la
  suite des réglages Aikar.
- Vérifier sur le Ptero `/forceload query` dans chaque monde (tronçons forcés par les scripts de pose, chargés à vie) et
  `/forceload remove all` s'il en reste.

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
