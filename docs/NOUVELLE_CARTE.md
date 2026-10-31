# Nouvelle carte de loot : préparer le remplacement en gardant le spawn (31/10)

Étude seulement : **rien n'a été changé dans le monde, ni sur le Ptero, ni dans les workflows**. Ce document dit ce qui
sera remplacé, ce qu'il faut garder et comment, ce qu'il faudra refaire en jeu et ce qu'il faudra vérifier. Les
chemins sont ceux du code (relus le 31/10) ; les fichiers du Ptero n'ont pas été lus.

## 1. En bref

- Tous nos mondes sont **dans un seul dossier**, `dayz_map/` (réglage `level-name` de `server.properties`) :
  - la carte de loot est le monde principal (`minecraft:overworld`) : `dayz_map/region`, `entities`, `poi` ;
  - le spawn est un monde à part, `heralisz:spawn`, dans `dayz_map/dimensions/heralisz/spawn/` ;
  - le tutoriel des bases (`crac:tuto`), l'ancien monde des bases (`crac:bases`) et les arènes du ClashWar sont aussi
    sous `dayz_map/dimensions/`.
- Remplacer la carte de loot = remplacer **seulement** `region/`, `entities/` et `poi/` à la racine de `dayz_map/`, et
  trier `dayz_map/data/`. On **garde** `level.dat`, `dimensions/`, `playerdata/` et le reste.
- **Les bases des clans sont sur la carte de loot** depuis CRac 1.16.0 (21/10) : changer de carte efface toutes les
  bases construites. À faire en même temps qu'un wipe (`/wipe tout`, avant le changement), sinon les données des bases
  pointent vers des constructions qui n'existent plus.
- Ne **jamais** reprendre le `level.dat` de la nouvelle carte : il remettrait l'heure du monde à zéro (recharges des
  jumelles, fins des vagues d'extraction, délais gardés en heure du monde : tout serait faux), changerait la graine du
  monde (le monde `crac:bases` se générerait autrement) et la table des identifiants de Forge.
- Il n'existe aujourd'hui **aucun workflow** qui change de carte (l'ancienne case « deposer_carte » a été retirée le
  26/10 ; elle remplaçait tout le dossier du monde, spawn compris). Un workflow est proposé au §8, **à valider avant
  d'être écrit**.

## 2. Où vit chaque monde

| Monde | Identifiant | Dossier (dans `dayz_map/`) | Avec la nouvelle carte |
|---|---|---|---|
| Carte de loot (et bases des clans) | `minecraft:overworld` | `region/`, `entities/`, `poi/` (racine) | **remplacé** |
| Spawn (grotte du terminus, PNJ, borne de départ) | `heralisz:spawn` | `dimensions/heralisz/spawn/` | **gardé tel quel** |
| Tutoriel des bases (parcelles) | `crac:tuto` | `dimensions/crac/tuto/` | gardé (ou vidé : il se refait seul) |
| Ancien monde des bases (bancs de charge) | `crac:bases` | `dimensions/crac/bases/` | gardé (inutile en jeu) |
| Arènes du ClashWar | `heraliz_clashwar:arenes` | `dimensions/heraliz_clashwar/arenes/` | **gardé** |

Chaque monde à part garde ses propres données dans son dossier (`dimensions/.../data/`) : elles suivent le monde.
Mais beaucoup de données de tout le serveur sont rangées dans `dayz_map/data/` (« données du monde principal ») : c'est
là qu'il faut trier (§4).

Mohist (Bukkit) range aussi `uid.dat` à la racine du monde : le garder. Les zones WorldGuard de la carte sont dans
`plugins/WorldGuard/worlds/dayz_map/` : elles gardent les mêmes coordonnées sur la nouvelle carte, à relire.

## 3. Ce qui est remplacé

- `dayz_map/region/`, `dayz_map/entities/`, `dayz_map/poi/` : les blocs, les entités (cadavres, caisses, terminaux,
  décors en entités) et les points d'intérêt de la carte de loot, pris dans la nouvelle carte.
- Si la nouvelle carte a un pack de données de loot (`datapacks/heraliz_carte/data/heraliz_loot/zones/`, zones de loot
  par lieu lues par HeraliZ Loot) : il remplace celui de l'ancienne carte dans `dayz_map/datapacks/`.
- Rien d'autre ne vient de la nouvelle carte : son `level.dat`, son `playerdata/`, son `data/` et ses `dimensions/`
  sont laissés de côté.

Les blocs posés sur la nouvelle carte doivent être les nôtres ou ceux de mods du serveur, avec les **mêmes
identifiants** (`heraliz_decors:…`, `heralisz_utils:…`, `heraliz_loot:…`, `cfm:…`) : un bloc d'un mod absent devient de
l'air. La carte doit donc être construite avec les mods du serveur (règle 7 : sur le serveur, par exemple dans une copie
de travail).

## 4. Les données de `dayz_map/data/` : garder ou repartir de zéro

### Garder (pas liées aux lieux de la carte)

| Fichier | Contenu |
|---|---|
| `heraliz_cinematique.dat` | **point de réveil du spawn** (cinématique, `/spawn` par défaut, retour des extractions) : indispensable |
| `heraliz_coffres_perso.dat` | planques et coffres VIP des joueurs |
| `heraliz_cycle.dat` | jour et nuit |
| `heraliz_brume.json` | réglage de la brume |
| `heraliz_clashwar_arenes.dat` | registre des arènes (leur monde est gardé) |
| `heralizcasino_*.dat`, `heralizcrates_pending.dat` | casino (jackpot, paiements et récompenses en attente, statistiques) |
| `heralizcasino_zone.dat`, `heralizcasino_zone_vip.dat` | salles du casino : à garder si le casino est au spawn ; s'il est sur la carte, à refaire |
| `crac_groups.dat`, `crac_purge.dat` | groupes de CRac, état de la Purge |
| fichiers de Minecraft (`idcounts`, `scoreboard`, `random_sequences`, `command_storage_*`, `raids`…) | garder |

### Repartir de zéro (liées à l'ancienne carte)

| Fichier | Contenu | À refaire |
|---|---|---|
| `heraliz_extraction_zones.dat` | zones d'extraction et vagues | `/extraction create` (§6) |
| `heraliz_carte_lieux.dat` | lieux nommés de la carte M et nom de la carte | `/carte lieu ajouter`, `/carte nom` |
| `heraliz_depart.dat` | points et zones d'arrivée de la borne de départ | `/depart point …`, `/depart zone …` |
| `heralisz_bunker_salles.dat` | salles à carte (boîtes, portes) | `/salle …` |
| `heraliz_loot_rotation.dat` | conteneurs actifs de la rotation du loot | rien : refait seul à partir des conteneurs de la nouvelle carte |
| `heraliz_loot_points_t7.dat` | points d'apparition des coffres T7 | à reposer |
| `heraliz_vitres.dat` | vitres cassées | rien |
| `heraliz_corps.dat` | index des cadavres | rien |
| `heralisz_train.dat` | état du train | voir §5 (voie du train) |
| `heralisz_clans_territoires.dat` | état des territoires (propriétaire, captures) | rien (suit `zones.json`, §5) |
| `heraliz_zones.json` | zones à effets (gaz, radiation, froid) | à reposer sur la nouvelle carte |
| `chunks.dat` (s'il existe) | tronçons forcés de la carte | `/forceload query` après coup |
| `crac_claims`, `crac_buildings`, `crac_placable_positions`, `crac_placable_hp`, `crac_raidblocks`, `crac_placed_terrain`, `crac_carrieres` (`.dat`) | bases construites sur la carte | parties avec la carte |
| `crac_bases_clans`, `crac_coeurs_clans`, `crac_coeurs_deplaces`, `crac_acces_bases` (`.dat`) | base et cœur de chaque clan, accès à l'armoire | `/wipe tout` les vide (à faire avant) |

### À trier (le fichier mélange spawn et carte)

| Fichier | Pourquoi | Comment |
|---|---|---|
| `heraliz_quetes_points.dat` | points de quête : chacun a son monde | garder le fichier ; retirer en jeu les points de la carte (`/quete point …`) et les reposer |
| `crac_bases.dat` | par joueur : étape du tutoriel, recharge de TP, homes, dernier `/home` et `/rtp` | `/wipe tout` le remet à zéro ; sans wipe, les homes posés sur l'ancienne carte pointent dans le vide de la nouvelle |

Conseil : ne rien supprimer. Déplacer les fichiers « à repartir de zéro » dans un dossier `data_ancienne_carte/` à côté
de `data/` : on peut revenir en arrière.

## 5. Hors du monde : gardé tout seul, ou réglages à refaire

Gardés sans rien faire (hors de `dayz_map/`) : clans, hérals, saison, grades, quêtes des joueurs, comptes de capsules,
AuthMe, bans, `/spawn definir` (`crac/spawn.json`, il désigne le monde du spawn), journal et état des événements.

À refaire pour la nouvelle carte :

| Réglage | Fichier | Comment |
|---|---|---|
| Territoires des clans (drapeaux, villes) | `config/heralisz_clans/zones.json` | la mise en ligne ne l'écrase **jamais** s'il est déjà sur le Ptero : le remplacer à la main (ou le supprimer pour que l'envoi suivant pose celui du dépôt) |
| Lieux des événements (largage, hélicoptère…) | `heraliz_evenements/lieux.json` (racine du serveur) | `/event point …` ; jamais écrasé par la mise en ligne |
| Zone du largage public | `config/heraliz_evenements/largage.json` (`zone`, `marge_bordure`) | à relire selon la taille de la nouvelle carte |
| Zones VIP | `heraliz_grades/zones_vip.json` (racine du serveur) | à relire si elles sont sur la carte |
| Masque des zones de construction des bases | `config/crac/zones_construction.json` + `zones_construction_masque.png` | `python outils/zones_construction.py` sur la nouvelle carte (zones de loot, zones à effets, voie du train), rendu à valider |
| Fond de la carte M | `modules/monde/src/client/resources/assets/heraliz_carte/carte/` | `python outils/carte_monde.py <copie du monde> --installer`, puis fabriquer : **nouveau jar joueur** |
| Voie du train | `assets/heralisz_utils/train/reseau_v2.json` (dans le mod) | nouvelle voie, ou train désactivé si la carte n'en a pas |
| Bordure de la carte | `level.dat` (gardé) | `/worldborder center …` et `/worldborder set …` à la taille de la nouvelle carte |
| Zones WorldGuard de la carte | `plugins/WorldGuard/worlds/dayz_map/` | à relire |

## 6. À refaire en jeu (staff)

1. Bordure : `/worldborder`.
2. Zones à effets (gaz, radiation, froid) : `/zones …` (HeraliZ Décors).
3. Points d'arrivée de la borne : `/depart point …`, `/depart zone …` ; `/depart info` doit dire « prêt ».
4. Zones d'extraction : `/extraction create <nom> [rayon]`, à ciel ouvert. Il en faut **au moins 12** (deux vagues de 6
   sans reprendre une zone), et plutôt **25 à 30** bien réparties : simulé sur 30 zones de l'ancienne carte, 800 blocs
   d'écart et 2 par quart sont tenus à chaque vague. Aujourd'hui : **45 zones** au dernier relevé (17/10, dont 2 en
   double au même endroit) ; le nombre réel dépend des zones posées depuis (`/extraction list`).
5. Lieux de la carte M : `/carte lieu ajouter …`, `/carte nom …`.
6. Salles à carte du bunker : `/salle …` (boîte, couleur, portes).
7. Points des coffres T7, points de quête de la carte (`/quete point …`), lieux des événements (`/event point …`).
8. Territoires des clans : `zones.json` (drapeaux, rayons) puis vérifier `/territoire info`.
9. Conteneurs de loot : posés avec la carte, ils entrent tout seuls dans la rotation ; vérifier `/heralizloot zones`
   (zones de loot du pack de données de la nouvelle carte).
10. Masque de construction, fond de la carte M, voie du train : voir §5.

## 7. Vérifications après le changement

- Un nouveau joueur : cinématique, réveil dans la grotte du spawn, PNJ et borne de départ en place.
- Borne de départ : arrivée sur la nouvelle carte, à ciel ouvert, jamais dans un mur ni dans l'eau.
- `/spawn` depuis la carte (refusé en combat) et `/spawn` du staff : mènent à la grotte.
- Extraction : une zone ouverte, la tenir, retour **au spawn** (et pas au point de départ de la nouvelle carte : c'est le
  signe que `heraliz_cinematique.dat` a été perdu).
- Mort : écran « Au camp » / « À la base » ; réapparition au spawn.
- `/home` : sans base, message clair ou arrivée au hasard sur la nouvelle carte ; après la pose d'une armoire, `/home` y
  mène. Homes posés avant : effacés par le wipe (sinon, à tester un par un).
- Un joueur déconnecté **sur l'ancienne carte** revient aux mêmes coordonnées sur la nouvelle (dans le sol, dans le
  vide, hors de la bordure) : le wipe le renvoie-t-il au spawn ? À vérifier ; sinon prévoir une remise au spawn à la
  première connexion (petit code à écrire, à valider).
- ClashWar : `/clashwar` (arènes en place), tutoriel des bases (`crac:tuto`).
- Carte M : nouveau fond, lieux, extractions ouvertes ; menu Échap : extractions ouvertes.
- Largage public et frappe aérienne : le point tiré tombe dans la nouvelle bordure.
- `/forceload query` dans chaque monde.

## 8. Mise en ligne : ce qui existe et ce qu'il faudrait (proposition, rien n'est écrit)

Aujourd'hui (`.github/workflows/serveur.yml`, `docs/MISE_EN_LIGNE_GITHUB.md`) : la mise en ligne n'envoie jamais le
monde. Seule case qui touche le monde : « reinitialiser_bases » (met `dimensions/crac/bases` de côté, serveur arrêté).
L'ancienne case « deposer_carte » (retirée) remplaçait **tout** `dayz_map/`, monde du spawn compris : à ne pas
reprendre telle quelle.

Proposition, **à valider par le propriétaire avant toute écriture** : une case « remplacer_carte_loot », sur le modèle
de « reinitialiser_bases » :

1. La nouvelle carte (seulement `region/`, `entities/`, `poi/`, et son pack de données de loot s'il y en a un) est
   prise dans une Release GitHub (archive), pas dans Git (trop lourde).
2. Envoi **à côté** pendant que le serveur tourne : `dayz_map/carte_nouvelle_<date>/`, contrôle fichier par fichier ;
   un échec ne touche à rien.
3. Joueurs prévenus, arrêt propre du serveur (API du Ptero), comme « reinitialiser_bases ».
4. Bascule : `region`, `entities`, `poi` renommés `*_ancienne_<date>` (jamais supprimés) ; les fichiers de `data/`
   « à repartir de zéro » (§4) déplacés dans `data_ancienne_carte_<date>/` ; la nouvelle carte prend la place.
   **Jamais** touchés : `level.dat`, `uid.dat`, `dimensions/`, `playerdata/`, `serverconfig/`, les autres fichiers de
   `data/`. En cas d'échec, les anciens noms sont remis.
5. Redémarrage, résumé dans l'onglet Actions (ce qui a été mis de côté, ce qu'il reste à faire en jeu, §6).

À adapter aussi : la mise en ligne n'écrase jamais `config/heralisz_clans/zones.json` ; une option pour le remplacer une
fois (nouvelle carte) serait utile. L'ancien workflow « Extractions de la carte » (lecture seule, rapport avec
`outils/extractions_carte.py`) pourrait revenir pour contrôler les nouvelles zones.

## 9. Questions pour le propriétaire

1. La nouvelle carte arrive-t-elle avec un wipe (`/wipe tout`) ? Sans wipe, les bases des clans sont perdues mais les
   clans gardent leurs données de base (cœurs, homes) qui ne correspondent plus à rien.
2. D'où vient la nouvelle carte : construite sur le Ptero (dans quel dossier ?) ou reçue toute faite (avec quels mods) ?
3. Le casino et les zones VIP sont-ils au spawn ou sur la carte ?
4. La nouvelle carte a-t-elle une voie de train ?
5. On écrit le workflow « remplacer_carte_loot » du §8 ?
