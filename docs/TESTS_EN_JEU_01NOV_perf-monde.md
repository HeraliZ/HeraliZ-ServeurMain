# Essais en jeu du 01/11 — chantier « perf-monde » (zombies, borne de départ, zones dangereuses)

Demande du propriétaire : « pathfinding des zombies ultra bien opti pour 0 lag », puis le reste du module `monde`
(et `survie`) en pensant « 150 joueurs, des centaines de zombies ». Monde 1.0.7, serveur seulement : les joueurs n'ont
rien à changer (leur jar `monde` est refait mais se comporte pareil).

**Rien de ceci n'a été essayé en jeu ni compilé avec Gradle** (ForgeGradle injoignable ici) : le code neuf a été compilé
avec javac contre des signatures de Minecraft écrites à la main ; la vraie fabrication se fait par le contrôle de branche
de GitHub.

## Ce qui était déjà en place (vérifié dans le code)

- Objectifs inutiles retirés à l'arrivée de chaque zombie HeraliZ (village la nuit, œufs de tortue, villageois, golems,
  tortues) : `IaZombies`.
- Taille des recherches de chemin selon la distance au joueur le plus proche (1 ; 0,5 ; 0,25 ; 0,125), budget de
  40 recherches par tick, chemin partagé par la horde, poursuite en ligne droite de près : `IaZombies`, `CheminsZombies`.
- « Zombies plus légers » (26/10) : poussée des voisins un tick sur 4, déplacement au repos un tick sur 10, pas de mort
  par entassement, peuplement étalé (2 apparitions par tick, chaque joueur à son tick) : `ZombieSurvie`, `Peuplement`.

**Pas en place** : le zombie « version serveur », sa navigation qui recalcule moins souvent et le mixin de Zombie
Awareness du 24/10 n'existaient que sur une ancienne branche jamais fusionnée (`607caf1b`). Refaits ici (plus complets).

**Trouvé en passant** : le réglage `ia_rythme` (« objectifs revus tous les N ticks ») n'a aucun effet : Minecraft 1.20.1
revoit les objectifs de toutes les créatures un tick sur deux et ne lit plus ce rythme. Gardé (rien ne casse), documenté.

## Ce qui change (estimations pour 150 joueurs, ~400 zombies dont ~200 actifs, processeur du Ptero)

| Endroit | Avant | Après | Pour le joueur |
|---|---|---|---|
| Recherche de cible de toutes les créatures (`zombies/cibles/CiblesPorteeMixin`, mixin de Minecraft) | chaque recherche juge les 150 joueurs (équipe, visibilité avec un événement Forge créé à chaque fois) avant la distance : ~20 à 40 µs ; ~20-30 recherches par tick → **~0,5 à 1 ms/tick** | joueur hors de portée écarté d'abord par la distance : **~0,05 à 0,15 ms/tick** | rien (même cible choisie) |
| Calcul des chemins des zombies (`CheminsZombies`) | budget de 40 calculs par tick, sans limite de temps : une horde sous un joueur hors d'atteinte = jusqu'à **20-40 ms** dans un tick | en plus **4 ms par tick au plus** (`chemins_budget_ms`), temps mesuré | rien d'habitude ; sous très forte charge, un zombie garde son chemin un tick de plus (avant : il **s'arrêtait net** jusqu'à 1 s) |
| Chemin vers une cible hors d'atteinte ou loin (`NavigationZombie`, `ZombieSurvieServeur`) | joueur sur un toit, derrière une clôture : chaque zombie refait un calcul complet (560 cases, ~0,5-2 ms) toutes les 4-10 ticks → **1 à 3 ms/tick** pour 14 zombies | même cible, même place, chemin incomplet (zombie en route ou déjà au pied du mur) : 1 calcul / 20 ticks (**÷3 à ÷5**) ; 16-24 blocs : 12 ticks au moins (Minecraft 9-15) ; > 24 blocs : 20 (Minecraft 9-20) | de près (< 16 blocs) : rien. De loin, le zombie suit l'endroit où était le joueur jusqu'à 1 s plus tôt |
| Bruits (tirs, explosions, sang) (`StimuliZombies`) | jusqu'à 16 calculs dans le tick du tir, chacun parcourant toutes ses cases quand le bruit est plus loin que la portée de suivi (journal du Ptero : **49 bruits de plus de 10 ms**) | 4 zombies lancés par tick au plus, recherche 4 fois plus petite, point visé à la portée de suivi (~33 blocs) dans la direction du bruit : **~0,1-0,5 ms/tick** pendant 4 ticks | les zombies partent en 0,2 s au plus au lieu du même tick ; un zombie à 60 blocs avance d'environ 33 blocs (Minecraft ne l'emmenait pas plus loin) |
| Zombie Awareness, recherche des sons et odeurs (`SensAwareness`, mixin, jar intact) | chaque zombie inoccupé, toutes les 15 ticks, liste **toutes** les entités sur 129 blocs puis jette la liste 9 fois sur 10 : ~10-40 µs → **~0,1 à 0,4 ms/tick** | tirage d'abord, puis seulement ses entités de sens : **~0,01 ms/tick** | rien (même résultat) |
| Niveaux d'IA (`IaZombies`) | 100 zombies × 150 joueurs testés toutes les 5 ticks (~150 µs) | positions relevées une fois par passage (~30 µs) | rien |
| Gestes des zombies (`AttaquesZombiesServeur`) | une entrée de table créée et cherchée à chaque tick pour chaque zombie | seulement pendant un geste, et pour le Crawler (bond) | rien |
| Zones dangereuses (`decors/serveur/ZonesServeur`) | chaque seconde, chaque joueur mesuré contre chaque côté de chaque zone (~0,3-0,5 ms dans un tick) | boîte de chaque zone d'abord (~15 µs) | rien |
| Dates des fichiers relus (`serveur/DatesFichiers`) | borne de départ : **jusqu'à 128 coups d'œil au disque par tick** et par joueur qui part (`zones.json`) ; peuplement, tenues, crash de l'hélicoptère : toutes les 10 à 30 s. Un seul coup d'œil a déjà gelé un tick 117 ms (disque occupé, 28/10) | relevées sur un fil à part toutes les 5 s : **0** sur le fil du serveur | un changement de `zones.json`, `heraliz_zones.json` ou `zones_vip.json` est vu en 5 à 15 s (avant : 0 à 10 s) |
| Borne de départ, point d'arrivée tiré (`spawn/DepartServeur`, `Prechargement`) | chaque point essayé faisait **lire son tronçon sur le disque par le fil du serveur** (~10 à 50 ms, au moins une fois par départ) ; fichier de régions vérifié sur le disque à chaque essai | tronçon demandé à Minecraft en tâche de fond, point contrôlé quand il est prêt (2 s au plus, puis comme avant) ; régions présentes gardées en mémoire | rarement, « Recherche d'un point d'arrivée sûr… » une seconde de plus à la fin du compte à rebours |

Réglages ajoutés (`heraliz_zombies-server.toml`, valeurs mises toutes seules au démarrage) : `chemins_budget_ms` (4.0),
`chemin_pres` (16), `attente_moyen` (12), `attente_loin` (20), `attente_bloque` (20), `awareness_rapide` (true) dans
`[zombies]` ; `par_tick` (4) et `recherche` (0.25) dans `[stimuli]`. `ia_allegee = false` remet la navigation de
Minecraft ; `awareness_rapide = false` la recherche de Zombie Awareness.

Mixins (jars des autres auteurs intacts) : `heralisz_utils_cibles.mixins.json` (Minecraft, `TargetingConditions.test`,
config non obligatoire : si un autre mod touche aux mêmes appels, Minecraft reste tel quel) ; `ZombieAwarenessSensMixin`
dans `heralisz_utils_serveur.mixins.json` (Zombie Awareness, non obligatoire).

## À vérifier en jeu (serveur redémarré avec les nouveaux jars)

1. **Démarrage** : aucune erreur de mixin dans le journal pour `heralisz_utils_cibles.mixins.json` ni
   `ZombieAwarenessSensMixin`. À la première recherche d'un zombie : ligne
   `[zombies] Zombie Awareness : recherche rapide des sons et odeurs branchée` (jamais « coupée »).
2. **Zombies de près** : ils repèrent un joueur visible à ~35 blocs, le poursuivent, contournent les obstacles, frappent
   comme avant (coup esquivable, 3 attaquants au plus). Les créatures regardent toujours les joueurs proches.
3. **Joueur hors d'atteinte** (sur un toit, derrière une clôture, dans une base fermée) : les zombies se massent dessous
   sans se figer. `/zombies stats` : la ligne « Chemins gardés » augmente ; « pire tick » reste sous 4 ms.
4. **Tirs en ville** : les zombies sans cible arrivent (une fraction de seconde de décalage au plus). Journal : plus de
   lignes `[zombies] bruit ... en N ms` au-dessus de 10 ms. Avec silencieux : seulement les zombies proches.
5. **Sang et Zombie Awareness** : un joueur blessé attire encore les zombies voisins ; la nuit, les zombies vont encore
   vers les sources de lumière et les odeurs.
6. **Vagues de capture** (Crawler, Blister, Brute) : bond du Crawler et gestes des autres comme avant.
7. **Zones gaz / radiation / froid** : en entrant, le voile et les effets arrivent comme avant ; avec la protection,
   rien ; la protection s'use toujours.
8. **Borne de départ** (avec un compte normal, plusieurs fois) : départ à la fin du compte à rebours, arrivée sur un sol
   sûr, jamais dans du terrain neuf ; si le message « Recherche d'un point d'arrivée sûr… » apparaît, le départ suit en
   1 à 2 s. `/depart` du staff : inchangé. Pas d'alerte `[Diag] Blocage` au départ.
9. **Fichiers relus** : modifier `config/heralisz_clans/zones.json` (une ville) ou `heraliz_grades/zones_vip.json` : pris
   en compte en 15 s au plus (tenues des zombies, peuplement, points de départ, crash de l'hélicoptère).
10. **Mesure** : `/diag profil 300` un soir chargé, avant et après : la part de `TargetingConditions.test`,
    `PathFinder.findPath` et `ZAUtil.getSenseNearEntity` doit baisser nettement.

## Réglages proposés (pas changés)

- `spigot.yml`, `entity-activation-range.monsters: 24` : au-delà de 24 blocs de tout joueur, un monstre sans cible ne
  fait plus qu'un tick sur 20 (probable aussi pour les entités de mods sous Mohist, à confirmer avec `/diag profil`). C'est le plus
  gros allègement des zombies : le garder. Conséquence : un tir attire jusqu'à 80 blocs, mais au-delà de ~24 blocs les
  zombies n'avancent presque pas (voir question 2).
- `config/zombieawareness/General.toml`, `tickRateAILoop = 5` → `10` : le travail de Zombie Awareness par zombie divisé
  par deux ; les zombies réagiraient aux odeurs et lumières en 0,5 s au lieu de 0,25 s (à peine visible).
- Gardes déjà notées ailleurs : `/gamerule doMobSpawning false` sur la carte ; spark retiré le 01/11 (`/diag profil`).

## Questions pour le propriétaire (réponses du 01/11)

1. Zombies qui **ramassent les objets au sol** : **coupé** (Monde 1.0.8, `ramassage = false` dans `[zombies]` de
   `heraliz_zombies-server.toml`, tous les monstres, aussi ceux relus du disque). À vérifier : jeter une arme près d'un
   zombie, il ne la prend plus ; un zombie qui en portait déjà une la garde et la lâche à sa mort.
2. Portée d'activation des zombies : **24 blocs gardés** (le plus léger).
3. Anneau de particules de la borne : **un tick sur deux** (choix logique, presque invisible) : 4 paquets par tick en
   moyenne au lieu de 8 pour chaque joueur à 32 blocs. À vérifier : l'anneau a le même air pendant le compte à rebours.

## Pas fait (à savoir)

- Portée de suivi des zombies toujours celle de Minecraft (35 blocs, jusqu'à 87 avec le bonus de difficulté) : la borner
  changerait la poursuite ; à décider.
- Rayon de vue des zombies (un rayon par tick pour chaque zombie qui poursuit, fait par Minecraft) : pas touché.
- Zombie Awareness : ses sons (chaque son du serveur passe par lui, ~0,05 ms/tick) et sa recherche de cible toutes les
  2 s : pas touchés (petits).
- Extraction : forme de la zone recalculée chaque seconde pour chaque zone ouverte (~25 µs/tick) : pas touché.
- Usure (`ReglesServeur`) : un coup d'œil au disque toutes les 2 s, laissé.
