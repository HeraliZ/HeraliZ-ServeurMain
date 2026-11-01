# Essais en jeu du 01/11 — chantier « Minecraft allégé »

Demande : le banc de charge du 01/11 (80 bots dans un vrai Mohist, run 41327773381) montre que nos mods pèsent peu
(TaCZ 2,7 %, Tactique 0,9 %, Spartan 0,6 %…) : le gros du tick vient de **Minecraft lui-même**, qui passe mal à 150 joueurs.
Ici : des mixins **serveur** du noyau (jar de Minecraft, de Forge et de Mohist intacts), chacun avec le **même résultat
que Minecraft**, chacun coupé par un réglage de `[perf]` dans `heraliz_noyau-server.toml`. Plus, à la demande du
propriétaire, les **tics aléatoires coupés** (réglage `[monde] tics_aleatoires`).

Version : **Noyau 1.0.11** (rien d'autre). Suite : **étape 2, Noyau 1.0.12**, en fin de fiche. Côté serveur seulement ; le numéro change, les joueurs reçoivent le nouveau jar
par le launcher comme à chaque mise en ligne.

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle** (dépôt de Forge bloqué ici). Seules les classes changées
ont été compilées (javac, Java 17) contre des « bouchons » écrits à la main (signatures de Minecraft et de Mohist) ; les
mixins n'ont jamais été appliqués pour de vrai (la table de correspondance `heraliz_noyau-serveur.refmap.json` est faite par
la fabrication de GitHub). Essais hors du jeu de la logique : voir chaque point.

Lecture du code de Mohist : le jar de la Release « base-serveur » ne contient pas les classes de Minecraft modifiées, seulement
des différences binaires (`data/server.lzma`) appliquées au premier démarrage sur le jar de Mojang (téléchargement bloqué
ici). Lu à la place : les sources des patchs de Mohist 1.20.1 (dépôt public MohistMC/Mohist, branche 1.20.1, peut-être un peu
plus récente que la 916) et le code de Minecraft 1.20.1 décompilé. Les numéros de ligne du profil du banc collent (exemple :
`tickChunks` 371 / 385 / 392 = Minecraft 358 / 372 / 379 + 13 lignes ajoutées par Mohist).

## Tableau (coûts estimés à 150 joueurs, vue 6, simulation 4, ~4 000 entités suivies)

| Point | Endroit (Minecraft/Mohist → notre fichier) | Avant | Après | Pour le joueur |
|---|---|---|---|---|
| 1. Joueur proche d'un tronçon | `ChunkMap.anyPlayerCloseEnoughForSpawning` (m_183879_, l. 1017-1025), appelé 2 fois par tronçon actif et par tick par `ServerChunkCache.tickChunks` (l. 385) → `allege/JoueursProchesMixin.java:36-42`, `perf/JoueursProches.java:72` | tous les joueurs du monde parcourus pour chaque tronçon (+ 2 écritures atomiques de Mohist par joueur) : banc 80 joueurs ~8 % du tick (~6 ms) ; essai hors du jeu 150 joueurs en 30 groupes, 12 000 tronçons : **~36 ms par tick** | grille de cases de 128 blocs refaite une fois par tick : **~2 à 3 ms** | rien (mêmes apparitions de créatures, mêmes tics). Essai : 2,4 millions de cas, 0 différence (bords à 128 blocs, spectateurs, portée réduite `mob-spawn-range` comprise) |
| 2. Portée de suivi | `ChunkMap$TrackedEntity.getEffectiveRange` (m_140496_, l. 1477), via `ChunkMap.move` (l. 1111) à chaque paquet de mouvement → `allege/PorteeSuiviMixin.java:15`, `perf/PorteeSuivi.java:62` | ~600 000 paires (entité, joueur) jugées par tick, chacune avec un flux Java neuf (~10 objets) : **~15 à 18 ms par tick** + beaucoup de déchets mémoire | sans passager : liste vide partagée, aucun objet : **~9 à 12 ms** | rien (même portée ; avec un passager ou une classe d'entité qui redéfinit ces méthodes : comme Minecraft) |
| 4. Entités d'un tronçon envoyé | `ChunkMap.playerLoadedChunk` (m_183760_, **l. 1330** : boucle sur toutes les entités du monde pour chaque tronçon) → `allege/EntitesTronconMixin.java:33-50`, `allege/EntiteTronconMixin.java:18`, `perf/EntitesParTroncon.java:69` | ~170 tronçons × 4 000 entités par arrivée : **2 à 4 ms par arrivée** ; ~0,4 ms par tick pour les joueurs qui changent de tronçon en marchant | index fait en un parcours : **~0,1 ms par arrivée** | rien (mêmes entités, même ordre). Essai : 3,7 millions de tronçons envoyés au milieu d'entités qui bougent, apparaissent, disparaissent : 0 différence |
| 4a. Paquet du tronçon | `ChunkMap.playerLoadedChunk` (**l. 1322** : `new ClientboundLevelChunkWithLightPacket`) → `allege/PaquetsTronconsMixin.java:41-59` + `PaquetsChangementsMixin`, `PaquetsBlocsMixin`, `PaquetsEntitesBlocMixin`, `perf/PaquetsTroncons.java:44` | un paquet par joueur et par tronçon (~50 à 100 µs) : 6 joueurs extraits ensemble = ~1 000 paquets, **60 à 100 ms dans un tick** | ~170 paquets : **10 à 20 ms** ; un joueur seul : pareil qu'avant | rien (mêmes octets : le paquet est oublié dès qu'un bloc, une entité de bloc, la lumière ou les biomes du tronçon changent) |
| 4b. Envoi étalé (1.20.2 / Paper) | — | — | **pas fait** (voir plus bas) | — |
| 3. Zone de départ des mondes | Mohist `MinecraftServer.prepareLevels` (`keepSpawnInMemory`), `ServerLevel.setDefaultSpawnPos` → `perf/MondesSansSpawn.java:94`, `allege/SpawnSansTicketMixin.java:21` | tutoriel, ancien monde des bases, arènes : 441 tronçons chargés (voire générés) au démarrage puis libérés ; `/setworldspawn` remettait 2 209 tronçons gardés | jamais préparée, jamais remise | rien (démarrage un peu plus court) |
| 5. Parcours de tous les tronçons | `ServerChunkCache.tickChunks` (l. 371, `Iterators$1.hasNext` 3,9 %) | 25 000 à 50 000 tronçons parcourus par tick : ~0,5 à 1,5 ms | **pas fait** (analyse plus bas) | — |
| 6. Tics aléatoires | `ServerLevel.tickChunk` (l. 626-632, `FluidState.isRandomlyTicking` 11 % + `PalettedContainer.get` ~3 %) → `perf/TicsAleatoires.java:79` | ~14 % du tick du banc (~11 ms à 80 joueurs, ~15 à 20 ms à 150) | gamerule `randomTickSpeed` = 0 : **~1 %** (orage, neige et glace de la pluie restent) | **visible, voulu** : la carte est figée (voir plus bas) |

Ordre de grandeur total à 150 joueurs : **~30 à 50 ms par tick en moins** (surtout points 1, 2 et 6), et des pics en moins à
chaque arrivée (points 4).

`/diag etat` (et chaque rapport du diagnostic) a une ligne « Minecraft allégé (depuis le démarrage) : … » : un compteur par
optimisation. Un compteur qui reste à 0 après une partie avec du monde = mixin pas appliqué (chercher
`heraliz_noyau_allege` dans le journal du démarrage).

## Point 2 : ChunkMap.move, ce qui est sûr et ce qui ne l'est pas

À chaque paquet de mouvement d'un joueur (~20 par seconde), Minecraft juge **toutes** les entités suivies du monde pour ce
joueur (`ChunkMap.move` → `TrackedEntity.updatePlayer`) : c'est le plus gros coût restant à 150 joueurs (~10 ms par tick
après le point 2).
- **Fait (sûr)** : la portée de suivi sans flux Java.
- **Pas sûr, pas fait** : « ne rien faire si le joueur n'a pas changé de tronçon ni assez bougé ». Une entité qui bouge
  dans sa section (16 blocs) n'est rejugée QUE par ce passage : un zombie qui s'approche d'un joueur immobile apparaîtrait
  jusqu'à 16 blocs (ou 1 s) plus tard. Pas le même résultat.
- **Possible, gros chantier** : un index par tronçon pour ne juger que les entités à moins de 96 blocs (vue 6) plus celles
  que le joueur voit déjà (il faut tenir pour chaque joueur la liste des entités qu'il voit). Mêmes entités vues aux mêmes
  moments, seul l'ordre des paquets d'apparition entre deux entités différentes change (Minecraft les envoie déjà dans un
  ordre quelconque). Gain estimé : ~600 000 paires par tick → ~30 000 à 60 000 (~10 ms → ~1 à 2 ms). Proposé comme chantier à
  part, avec le banc avant et après.

## Point 4b : envoi étalé, pas fait

Minecraft 1.20.1 envoie tous les tronçons d'une arrivée dans le même tick. Les étaler (N par tick, les plus proches
d'abord) lisserait les pics, mais je ne peux pas prouver que rien ne casse sans essai : ordre des blocs changés envoyés
avant le tronçon (perdus chez le joueur, donc le paquet doit être fait au moment de l'envoi), tronçon oublié avant d'avoir
été envoyé (événement `ChunkWatchEvent` de Forge et `PlayerChunkLoadEvent` de Mohist au mauvais moment), écran de chargement
du joueur, joueur qui marche vers un tronçon pas encore reçu, anti-triche (mouvement contre un sol absent). Aucun de nos mods
n'écoute `ChunkWatchEvent` (vérifié). Avec les points 4 et 4a, une arrivée coûte surtout ses ~170 paquets (10 à 20 ms) :
à refaire plus tard, coupé par défaut, avec un essai en jeu.

## Point 5 : parcours de tous les tronçons chargés (analyse seulement)

`tickChunks` parcourt chaque tick tous les tronçons gardés (y compris ceux à moitié chargés autour des joueurs : ~1 500 par
joueur isolé en vue 6) pour trouver les ~1/8 actifs, crée un petit objet par tronçon actif et les mélange. Coût estimé :
**0,5 à 1,5 ms par tick** à 150 joueurs. Remède (comme Paper) : tenir à jour la liste des tronçons actifs à chaque changement
d'état. Gain ~1 ms, risque moyen (états des tronçons en tâche de fond). **Ne vaut pas le coup maintenant** : à revoir après une
mesure du banc avec les autres points.

## Point 3 : zone de départ et tickets, monde par monde

Un ticket de rayon r charge en entier (2r+1)² tronçons, et Minecraft en garde ~12 de plus tout autour à moitié chargés :
**(2·(r+12)+1)²** tronçons comptés (mesuré : rayon 11 = 2 209, rayon 2 = 841).

| Monde | Zone de départ (2 209) | Pourquoi |
|---|---|---|
| `minecraft:overworld` (carte) | **gardée** | une réapparition sans lit place d'abord le joueur au point de départ de la carte (avec un test de collision) avant que notre code l'envoie au spawn : sans la zone, chaque mort chargerait ce tronçon sur le fil du serveur. Les nouveaux joueurs aussi |
| `heralisz:spawn` | **gardée** (choix du propriétaire) | points d'arrivée déjà gardés en 5 × 5 (réveil, /spawn) ; sans la zone, le reste du spawn se chargerait quand quelqu'un y va (≤ 1 s visible quand il est vide). Question plus bas |
| `crac:tuto`, `crac:bases`, `heraliz_clashwar:arenes` | **libérée** (depuis le 27/10) et maintenant **jamais préparée** | chacun charge ses parcelles / arènes avec ses propres tickets |

Chiffres du Ptero (01/11 11:34, 0 joueur, version du 28/10) : spawn 2 209 = exactement la zone de départ. Carte 4 357 =
2 209 + **2 148 en plus**. Nos tickets permanents : seulement les points d'arrivée gardés (`Prechargement.garder`, rayon 2,
841 chacun), **ajoutés le 01/11, donc absents de la version du 28/10**. Tous les autres sont temporaires et ne tournent pas
à 0 joueur : départ de l'hélicoptère (rayon 3, ≤ 60 s), hélicoptère en vol (3), épave en feu (2), frappe et largage (4 et 5,
150 s), objet d'événement (1, ≤ 60 s), `/inspecter` (1 par tronçon de la base, 5 min), arrivées (2, 30 s), drone de
ywzj_vehicle (pendant le pilotage). Les 2 148 viennent donc d'ailleurs : très probablement des `/forceload` restés de la
construction de la carte (rechargés à chaque démarrage, rayon 2 chacun), ou une sauvegarde de tronçons en retard. **Aucun de
nos tickets ne reste pour rien** ; le rayon 2 des points d'arrivée est le plus petit qui évite le gel à l'arrivée (5 × 5
autour du joueur). Pour trancher : 30 s après le démarrage, le journal écrit maintenant les tickets de chaque monde
(`[noyau] tickets de minecraft:overworld (… tronçons chargés) : forced x… (rayon 2) player x… start x1 (rayon 11) …`), et
`/diag etat` aussi.

## Tics aléatoires coupés (choix du propriétaire du 01/11)

Réglage `[monde] tics_aleatoires = 0` de `heraliz_noyau-server.toml` : gamerule `randomTickSpeed` de **chaque** monde, par
l'API du jeu comme `/gamerule` (rien d'écrit à la main dans le monde), au chargement de chaque monde (mondes créés plus tard
compris), après le démarrage et quand le fichier change. Sous Mohist, le spawn, le tutoriel, les bases et les arènes
**partagent les règles de la carte** (même objet) ; un monde créé par un plugin a les siennes : chacun est réglé.
Journal au démarrage : `[noyau] tics aléatoires (démarrage) : randomTickSpeed = 0 : minecraft:overworld 0, heralisz:spawn 0
(règles de la carte), …`.

Ce que ça change : l'herbe ne pousse plus sur la terre et ne meurt plus sous un bloc, les feuilles ne tombent plus, neige et
glace ne fondent plus, cultures, pousses d'arbre, cannes, cactus, bambous, lianes, algues ne poussent plus, la terre labourée
ne sèche plus, la lave n'allume plus de feu autour d'elle, le cuivre ne vert-de-grise plus. **Ne change pas** : le feu qui se
propage (`doFireTick`), l'eau et la lave qui coulent, les portes, les machines, les mods ; et quand il pleut près d'un
joueur, Minecraft gèle encore l'eau et pose de la neige **dans les biomes froids** (ce n'est pas un tic aléatoire).

Remettre : `tics_aleatoires = 3` (valeur de Minecraft) ou une autre valeur, pris au plus tard au prochain redémarrage
(et tout de suite si le fichier est relu en marche). `-1` = le noyau ne touche plus à la règle : la dernière valeur
enregistrée dans le monde reste (donc 0 si elle y a déjà été mise) ; `/gamerule randomTickSpeed 3` la change alors à la main.

## À vérifier en jeu

1. **Démarrage** : pas d'erreur de mixin avec `heraliz_noyau_allege` ; lignes `[noyau] zone de départ de crac:tuto jamais
   préparée` (et bases, arènes) : Mohist écrit encore « Preparing start region for dimension … » mais plus de
   « Preparing spawn area : x % » pour ces mondes ; ligne des tics aléatoires (tous les mondes à 0) ; 30 s après, les
   lignes des tickets de chaque monde (**me les envoyer**, surtout celle de la carte).
2. **`/diag etat` après une soirée** : la ligne « Minecraft allégé » a tous ses compteurs au-dessus de 0 (le compteur
   « paquets de tronçon redonnés » ne monte qu'avec des joueurs téléportés ensemble).
3. **Créatures** : les zombies et les animaux apparaissent comme avant près des joueurs (ni plus, ni moins, ni plus loin).
4. **Distances de vue des entités** : joueurs, zombies, véhicules (avec et sans passager), objets au sol apparaissent et
   disparaissent aux mêmes distances qu'avant.
5. **Arrivées** (connexion, mort, extraction, `/spawn`, `/home`, tutoriel) : les entités autour (zombies, joueurs,
   véhicules, coffres de largage, animaux en laisse, passagers) sont là tout de suite ; extraction d'un groupe de 4 à 6 : le
   terrain arrive normalement chez tout le monde ; une porte ouverte ou un bloc cassé au moment de l'arrivée est vu comme il
   est ; les conteneurs de loot ont la bonne apparence.
6. **Anti-ESP de Spartan** : un joueur caché derrière un mur reste caché, réapparaît quand il est visible (même portée).
7. **Carte figée** : herbe, feuilles, neige, glace ne bougent plus (attendre quelques minutes au même endroit).
8. **Banc de charge** : relancer le banc et comparer le profil : les lignes `ChunkMap:1025`, `ChunkMap:1330`,
   `ChunkMap:1322`, `ChunkMap$TrackedEntity:1477`, `FluidState:84` doivent baisser nettement.

Pour couper une optimisation (si un des points 3 à 6 montre un souci) : passer son réglage de `[perf]` à `false` dans
`heraliz_noyau-server.toml` (relu tout seul en marche, sauf `spawn_jamais_prepare`, pris au prochain démarrage).

## Questions pour le propriétaire

1. **Monde du spawn** : libérer aussi sa zone de départ (2 209 tronçons gardés même vide) ? Les points d'arrivée restent
   prêts ; le reste du spawn se chargerait en moins d'une seconde quand quelqu'un arrive dans un spawn vide.
2. **Carte, 2 148 tronçons en plus à 0 joueur** : si la ligne des tickets dit `forced`, ce sont des `/forceload` de la
   construction : les retirer (`/forceload remove all` sur la carte) ?
3. **Neige** : y a-t-il des biomes froids sur la carte ? Avec les tics à 0, la pluie y pose encore neige et glace.
4. **Mouvements des joueurs** (point 2, ~10 ms par tick restants à 150 joueurs) : lancer le gros chantier « index par
   tronçon » avec banc avant et après ?
5. **Envoi étalé des tronçons** (point 4b) : le garder pour plus tard, coupé par défaut et essayé en jeu ?

---

# Étape 2 (Noyau 1.0.12) — suivi des entités, tickets, tronçons qui tournent, menus

Demande : le banc réaliste du 01/11 (100 bots, vue 8, simulation 4 comme le vrai serveur) donne **39,8 ms par tick** (95 % à
49,5) ; à **150 bots : 135 ms par tick, 7 TPS**. Le plus gros : `ChunkMap.move` (paquets de mouvement des joueurs) **14,6 ms à
100 joueurs, 77,4 ms à 150** (joueurs × entités, dont **2 300 à 3 150 balles de TaCZ**). Mêmes règles qu'à l'étape 1 : mixins
facultatifs (`heraliz_noyau_allege.mixins.json`, `require 0`), un réglage `[perf]` par optimisation, jamais utilisée tant que
ses crochets de sécurité n'ont pas été vus en marche, même résultat que Minecraft (sauf mention).

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle** (dépôt de Forge bloqué ici). Les classes changées ont été
compilées (javac, Java 17) contre des « bouchons » (signatures de Minecraft, Forge, Mixin) ; la logique des points 1 et 3 a
été essayée hors du jeu (plus bas). Les mixins n'ont jamais été appliqués pour de vrai.

Lu pour vérifier : Minecraft 1.20.1 décompilé, patchs de Mohist 1.20.1 (ChunkMap, ChunkHolder, ServerChunkCache,
DistanceManager, AbstractContainerMenu, ServerEntity), `CraftPlayer`, `CraftEntity`, `TrackingRange` et `ActivationRange` de
Mohist (les numéros de ligne du profil collent : `CraftPlayer.java:1575` = `canSee`, `CraftEntity.java:853` =
`isVisibleByDefault`), Mixin 0.8.5, les mixins de ModernFix qui touchent les mêmes classes, l'anti-ESP de Spartan
(`com/Spartan/esp`), le vanish du staff (`VanishServeur`, `PontBukkit`).

## Tableau (150 joueurs, vue 8 ; « avant » = banc à 150 bots du 01/11)

| Point | Endroit (Minecraft/Mohist → notre fichier) | Avant | Après (estimé) | Pour le joueur |
|---|---|---|---|---|
| 1. Suivi des entités à chaque paquet de mouvement | `ChunkMap.move` (l. 1111, boucle sur toutes les entités) → `TrackedEntity.updatePlayer` (l. 1448-1463) → `allege/SuiviCarteMixin.java:54`, `perf/SuiviParTroncon.java:463` (`bouger`), `:499` (`candidats`), `:675` (`juger`) | **77,4 ms** (~4 000 entités × 150 paquets = ~600 000 paires) | **~3 à 5 ms** (~140 entités jugées par paquet au lieu de ~3 650) | rien : mêmes entités vues aux mêmes moments, mêmes paquets ; seul l'ordre des paquets entre deux entités différentes change (Minecraft les envoie déjà dans un ordre quelconque) |
| 1 a. Test de Bukkit (`canSee`, vanish) | patch de Mohist l. 1455 → `perf/VoirBukkit.java:113` | `isVisibleByDefault` 18,5 ms + `getUniqueId` 2,1 ms | compris dans le point 1 (champ de l'entité lu directement) | rien (même réponse ; vrai test pour un joueur à qui un staff est caché, et une fois sur 4 096 pour comparer) |
| 1 b. `seenBy.remove` des entités lointaines | `Collections$SetFromMap.remove` (l. 1463) | 13 ms | ~0 (entités lointaines plus jugées ; les proches lues dans le miroir) | rien |
| 1 c. Entité ou joueur qui change de section | `ChunkMap.tick` (l. 1268/1274 : `updatePlayers` sur tous les joueurs, puis toutes les entités pour chaque joueur qui a changé de section) → `allege/SuiviMixin.java:29`, `allege/SuiviCarteMixin.java:83`, `perf/SuiviParTroncon.java:593`, `:643` | ~2,1 ms | ~0,2 ms | rien |
| 2. Ticket `UNKNOWN` de `getChunk` | `ServerChunkCache.getChunk` → `getChunkFutureMainThread` (l. 248 : ticket d'un tick même pour un tronçon complet) → `allege/TronconSansTicketMixin.java:35`, `perf/TronconsSansTicket.java:32` | 735 + 230 + 19 tickets à un instant (carte, tutoriel, spawn), leur part de `purgeStaleTickets` (1,55 ms en tout) et de `runAllUpdates` (1,26 ms) : **~0,5 à 1 ms** | ~0 | presque rien : un tronçon que plus RIEN ne tient (aucun joueur, aucun autre ticket) redescend 1 à 2 ticks (0,1 s) plus tôt |
| 3. Parcours de tous les tronçons | `ServerChunkCache.tickChunks` (l. 371, `Iterators$1.hasNext` 4,34 ms) → `allege/ActifsCacheMixin.java:21`, `allege/ActifsTronconMixin.java:20`, `perf/TronconsActifs.java:68`, `:87` | **~5 ms** (~29 700 tronçons parcourus par tick) | **~0,4 ms** (seulement ceux qui peuvent tourner, ~1/8) | rien : mêmes tronçons, que Minecraft mélange ensuite au hasard comme avant |
| 4. Menus : double comparaison | `AbstractContainerMenu.broadcastChanges` (l. 236/238 : `triggerSlotListeners` puis `synchronizeSlotToRemote`, `CompoundTag.equals`) → `allege/MenuSyncMixin.java:32`, `:47` | 1,9 ms | ~1 ms | rien |
| 4 bis. Reste du tick des joueurs (`doTick`) | `Player.tick` : collisions 5,1 ms, eau 1,7 ms, TaCZ 1,5 ms, écouteurs 2,7 ms | 14 ms | **pas fait** (rien d'exact de sûr ; plus bas) | — |
| 5. Monde du tutoriel | `crac:tuto` : 4,23 ms pour 13 à 14 joueurs, ~11 700 tronçons | — | **fait (Monde 1.0.9, choix du propriétaire : vue 4)** : ~−50 % de tronçons, ~1,5 à 2 ms | visible : dans le tutoriel seulement, on voit 64 blocs autour de soi au lieu de 128 |

Ordre de grandeur : **~80 ms par tick en moins à 150 joueurs** (surtout le point 1), soit, depuis les 135 ms du banc,
**~55 ms**. Ce n'est pas encore « nettement sous 50 » : voir « Ce qui reste » et les propositions (balles figées, portée des
balles, vue du tutoriel), qui en retireraient encore ~5 à 10.

`/diag etat` a une 2e ligne « Minecraft allégé, étape 2 : … » : mouvements faits par l'index (et entités jugées par paquet),
listes de joueurs, changements de section, contrôles, index refaits, `canSee` lus sur l'entité, tronçons sans ticket, ticks
par liste des tronçons qui tournent, comparaisons de menu évitées. Un compteur à 0 après une soirée avec du monde = mixin pas
appliqué (chercher `heraliz_noyau_allege` dans le journal) ; « COUPÉ » = une sécurité a coupé l'optimisation (le journal dit
pourquoi).

## Point 1 : comment c'est exact

Pour une paire (entité, joueur), Minecraft ne fait **rien** si l'entité est à plus de `vue × 16` blocs (sa portée ne dépasse
jamais ça) et que le joueur ne la voit pas déjà : elle reste non vue, aucun paquet. Il suffit donc de juger :
- les entités des cases de 32 blocs à portée du joueur (index « case → entités » tenu à jour : entité suivie ou plus,
  changement de tronçon) ;
- celles qu'il voit déjà, où qu'elles soient (téléportation de l'un ou de l'autre) : chaque liste `seenBy` de Minecraft est
  remplacée, à la création de l'entrée, par la même liste (même ordre, mêmes réponses) qui note chaque ajout et chaque retrait
  dans un « miroir » posé sur la connexion du joueur — quel que soit le code qui la change.

Chaque paire retenue est jugée avec la décision de Mohist refaite à l'identique : même distance, même portée
(`getEffectiveRange`, passagers compris, calculée seulement si l'entité n'est pas déjà trop loin), `broadcastToPlayer` (où se
branche l'anti-ESP de Spartan) puis le test `canSee` de Bukkit dans le même ordre, mêmes ajouts et retraits, mêmes paquets
(`addPairing` / `removePairing`, donc mêmes événements `StartTracking` / `StopTracking` de Forge).

Ce qui est gardé :
- **vanish du staff** (`/staff vanish`, `hidePlayer` de Bukkit) : Bukkit retire le joueur de la liste (noté dans le miroir) et
  `canSee` dit non tant qu'il est caché (le vrai test est posé pour tout joueur à qui une entité est cachée) ;
- **anti-ESP de Spartan** : sa décision passe par `broadcastToPlayer`, appelé pour les mêmes paires qu'avant (seules les
  paires hors de portée, pour lesquelles Minecraft ne l'appelait déjà pas, sont sautées) ; ses mises à jour directes
  (`TrackerAccess.refresh` → `updatePlayer`) passent par la liste, donc par le miroir ;
- **véhicules avec passagers** (portée du plus loin des passagers) ; **spectateurs** (`broadcastToPlayer` de Minecraft) ;
- un appel imbriqué (un écouteur appelé pendant un jugement) est fait par Minecraft.

Sécurités : rien n'est utilisé tant que les 4 crochets (listes, entité suivie, entité plus suivie, changement de tronçon)
n'ont pas été vus en marche, ni si le nombre d'entrées de l'index n'est pas celui de Minecraft (index refait à la fin du
tick), ni pour un joueur dont la connexion n'a pas de miroir. Une paire sur 8 192 est rejugée par Minecraft lui-même (rien ne
doit changer) ; toutes les 60 s, l'index et les miroirs sont comparés en entier à Minecraft. Un écart, ou une liste changée
depuis un autre fil que celui du serveur : coupé jusqu'au redémarrage, ligne `[noyau] suivi des entités par tronçon coupé…`.

Essai hors du jeu : deux mondes identiques, l'un avec les boucles de Minecraft, l'autre avec celles-ci (joueurs qui bougent,
tournent la tête, se téléportent, se connectent, partent, changent de section ; entités lentes et rapides, téléportées, qui
apparaissent, disparaissent, prennent un passager ; vanish ; décisions de `broadcastToPlayer` qui changent ; vue de 2 à 10) :
**69 064 états comparés, 470 746 paquets de mouvement faits par l'index, 0 différence** (qui voit quoi, paquets envoyés). Avec
une erreur volontaire, les différences sont vues et la sécurité coupe tout. Ordre de grandeur hors du jeu (150 joueurs en 25
groupes, 2 500 balles dont 2 200 figées autour de 10 groupes en combat, 1 000 créatures) : ~3 650 → ~140 entités jugées par
paquet, 15 ms → 1,4 ms pour les 150 paquets d'un tick (objets plus légers que dans le jeu).

La piste « presque exacte » (rien si le joueur n'a pas bougé d'un demi-bloc) n'a pas été prise : un joueur qui marche bouge de
0,2 à 0,3 bloc par tick, la passe complète aurait encore eu lieu tous les 2 à 3 ticks (~30 ms à 150 joueurs), et elle changeait
le moment où une entité apparaît.

## Point 2 : ticket UNKNOWN

À chaque `getChunk(x, z, FULL, true)` sur le fil du serveur qui rate le petit cache de 4 tronçons (chaque `getBlockState`
d'une créature, d'une balle, d'un mod…), Minecraft pose un ticket `UNKNOWN` (niveau 33, un tick) avant de rendre le tronçon,
même déjà complet ; le gestionnaire des distances l'ajoute, recalcule les niveaux, puis le retire au tick suivant. Paper rend
directement un tronçon complet. Ici, seulement si Minecraft lui-même trouverait le tronçon présent (même test `chunkAbsent`,
celui de Mohist) et que son avenir FULL est déjà terminé : **le même objet**, rangé dans le même petit cache, après le
court-circuit « en cours de chargement » de Forge (ModernFix ajoute le même au même endroit : sans conflit). Tout le reste
(tronçon absent, en cours, en échec, autre statut, autre fil) : Minecraft.

Sûr sous Forge et Mohist : le tronçon rendu reste en mémoire tant que le code qui l'a demandé s'en sert (Minecraft ne
décharge qu'à la fin de son tick des tronçons) ; un tronçon près d'un joueur est tenu par le ticket du joueur. **Seule
différence** : un tronçon que plus rien ne tient (le dernier joueur vient de partir) redescend 1 à 2 ticks plus tôt.

## Point 3 : tronçons qui tournent

Chaque carte tient la liste de ses supports de tronçon dont le niveau permet de tourner, mise à jour à la fin de
`ChunkHolder.updateFutures` (seul endroit où Minecraft crée ou retire leur avenir « qui tourne », avec le même test). La boucle
de `tickChunks` parcourt cette liste (seulement ses supports qui sont aussi ceux des tronçons visibles de Minecraft) et garde
son test `getTickingChunk()`. Comparée en entier à Minecraft toutes les 60 s (refaite si écart, coupée après 5 écarts).
Essai hors du jeu : 20 000 ticks (niveaux qui changent, avenirs prêts en retard, supports qui partent de la carte avec du
retard et n'y sont visibles qu'au tick suivant) : 0 différence ; avec une erreur volontaire, vue et coupée.

## Point 4 : tick des joueurs (`doTick`, 14 ms à 150)

Fait : dans `broadcastChanges` (menu ouvert de chaque joueur, à chaque tick), Minecraft comparait chaque case deux fois à ses
copies, chaque fois en entier (NBT des armes de TaCZ, sacs, armures, capacités de Forge). Quand les deux copies sont le même
objet et que la 1re a dit « pareil », la 2e (la même question) est sautée.

Pas fait (rien d'exact de sûr sans essai) :
- **collisions** (`Player.aiStep` → `Entity.move` → `BlockCollisions`, 5,1 ms) : les bots du banc sont déplacés par le serveur
  lui-même ; un vrai joueur est déplacé par son jeu (le serveur ne fait qu'un petit pas de gravité) : à mesurer avec de vrais
  joueurs avant de chercher plus ;
- eau et lave (`updateInWaterStateAndDoFluidPushing`, 1,7 ms), tick de TaCZ (1,5 ms), écouteurs des mods (Curios, TaCZ, LR
  Tactical, nos mods : ~2,7 ms) : à revoir mod par mod (chantier à part).

## Point 5 : monde du tutoriel (proposé, pas fait)

Chaque joueur du tutoriel est seul sur sa parcelle de 64 × 64 blocs (parcelles à 256 blocs l'une de l'autre, monde plat et
vide autour) et tient ~850 tronçons (banc : 11 700 pour 13 à 14 joueurs, 4,23 ms par tick).

| vue du tutoriel | tronçons par joueur (mesuré pour 8, estimé ensuite) | gain pour 15 joueurs du tutoriel |
|---|---|---|
| 8 (aujourd'hui) | ~850 | — |
| 6 | ~625 (−26 %) | ~1 ms par tick |
| 5 | ~530 (−38 %) | ~1,5 ms |
| 4 | ~440 (−48 %) | ~2 ms, et ~5 000 tronçons de moins en mémoire |

Visible : depuis la parcelle, le monde plat autour n'est plus dessiné au-delà de 64 blocs (vue 4) ; la parcelle elle-même
(64 × 64) reste entière à l'écran depuis n'importe quel point avec une vue de 5 ; à 4, son coin opposé (à ~90 blocs) peut être
coupé. **Comment** : Mohist a une vue par monde (`world-settings` de `spigot.yml`), mais notre `heraliz_distances-server.toml`
(module `monde`, `DistanceVue`) impose la même vue à tous les mondes au démarrage (`PlayerList.setViewDistance`) : il faudrait
un réglage à nous (« vue par monde », appliquée après), et un essai en jeu (le jeu du joueur garde sa vue de 8, il reçoit
simplement moins de tronçons dans ce monde).

## Balles de TaCZ (proposé, pas fait)

- **Portée de suivi** : TaCZ déclare 80 blocs (`clientTrackingRange(5)`), mais Mohist la remplace par la portée « other » de
  `spigot.yml` (les balles ne sont ni des objets ni de l'expérience, pas « misc ») : **64 blocs**. La baisser seulement pour
  `tacz:bullet` (mixin, jar de TaCZ intact ; changer « other » toucherait aussi véhicules, caisses de largage, grenades,
  corps…) : à 32, un joueur ne verrait plus les traçantes (et leurs impacts) que des balles passées à moins de 32 blocs de lui
  (aujourd'hui 64) ; le bruit des tirs ne dépend pas de la balle. Gain après le point 1 : moins de paires « à portée » jugées
  en entier et moins de paquets d'apparition (réseau) : ~1 ms par tick à 150 joueurs.
- **Balles figées** (constaté au banc à 150 : 2 300 à 3 150 balles présentes, **40 seulement tournent par tick**) : une balle
  qui sort de la zone simulée (simulation 4) s'arrête net côté serveur, sans vieillir, et reste suivie tant que le tronçon est
  chargé (le groupe qui a tiré est à côté). Le jeu du tireur l'a déjà fait voler et disparaître de lui-même. Aujourd'hui elle
  coûte à chaque paquet de mouvement des joueurs proches, et deux bizarreries : un joueur qui arrive près d'elle voit une
  traçante partir de nulle part ; un joueur qui entre dans la zone simulée la « réveille » et elle peut le toucher, des
  minutes après le tir. **Proposition** : retirer une balle de TaCZ dès qu'elle cesse de tourner (mixin). Gain : ~60 % des
  entités du serveur en combat, et chaque coût par entité (suivi, `ChunkMap.tick`, mémoire) d'autant.

## Ce qui reste (après l'étape 2, banc à 150)

`doTick` 14 ms (voir point 4), entités ~9 ms (balles 1,9 ; **orbes d'expérience 1,8** : 123 orbes qui tournent, 14 µs
chacune ; joueurs 1,9 ; zombies 1,1), `ServerEntity.sendChanges` 2,9 ms (paquets de mouvement des entités, nécessaires),
TaCZ 4,6 ms, Spartan 2,8 ms, joueurs proches des tronçons (étape 1) 2,7 ms, `LevelTicks.sortContainersToTick` 1,9 ms,
sauvegarde des tronçons (`ChunkMap.save`, 1,9 + 1,4 ms à 100 joueurs : parcours des tronçons visibles pour en sauver 20 par
tick). Et le banc lui-même (cerveau des bots, réseau simulé : `Reseau.recevoir` 2 ms).

## À vérifier en jeu (étape 2)

1. **Démarrage** : pas d'erreur de mixin avec `heraliz_noyau_allege` (surtout `SuiviMixin`, `SuiviCarteMixin`,
   `SuiviEntiteMixin`, `VusConnexionMixin`, `TronconSansTicketMixin`, `ActifsCacheMixin`, `MenuSyncMixin`).
2. **`/diag etat` après une soirée** : ligne « étape 2 » : mouvements par l'index au-dessus de 0 avec ~100 à 200 entités
   jugées par mouvement, pas de « COUPÉ » ; tronçons sans ticket, ticks par liste, comparaisons évitées au-dessus de 0 ; dans
   le journal, aucune ligne `suivi des entités par tronçon coupé`, `écart(s) avec Minecraft`, `tronçons qui tournent : liste en
   écart`.
3. **Ce qu'on voit** : joueurs, zombies, animaux, véhicules (avec et sans passager), objets au sol, balles et traçantes
   apparaissent et disparaissent aux mêmes distances qu'avant ; après une téléportation (`/spawn`, `/home`, extraction,
   tutoriel, mort), plus rien de l'endroit quitté ne reste affiché (aucun joueur ou zombie « fantôme » immobile).
4. **Vanish** : `/staff vanish` cache toujours le staff (entité et tab) aux joueurs, le staff se voit entre lui ; en sortant du
   vanish, il réapparaît tout de suite pour les joueurs proches.
5. **Anti-ESP de Spartan** : un joueur derrière un mur reste caché, réapparaît dès qu'il est visible.
6. **Inventaires** : armes de TaCZ (munitions, accessoires, camos), sacs, armures, Curios : tout changement dans l'inventaire
   se voit tout de suite chez le joueur (déplacer, recharger, tirer, ramasser) ; l'écran d'un coffre ou d'un sac aussi.
7. **Créatures et pousse** : les zombies et les animaux apparaissent comme avant ; les blocs qui « tournent » (feu, eau) aussi.
8. **Banc de charge** à 100 et 150 bots : `ChunkMap.move`, `CraftEntity.isVisibleByDefault`, `Collections$SetFromMap.remove`,
   `Iterators$1.hasNext`, `purgeStaleTickets` doivent baisser nettement ; me renvoyer les deux résumés.

Pour couper une optimisation : son réglage de `[perf]` à `false` dans `heraliz_noyau-server.toml` (relu en marche) :
`suivi_par_troncon`, `suivi_voir_rapide`, `troncons_sans_ticket`, `troncons_actifs_liste`, `menus_une_comparaison`.

## Questions pour le propriétaire (étape 2)

1. **Balles figées de TaCZ** : les retirer dès qu'elles cessent de tourner (plus de traçante « fantôme » ni de balle qui touche
   des minutes après) ? C'est le plus gros gain restant en combat.
2. **Portée des balles** : la baisser de 64 à 32 ou 48 blocs (traçantes vues de moins loin) ?
3. **Tutoriel** : vue de 5 (la parcelle reste entière à l'écran) ou de 4 dans ce monde seulement ?

### Vue du tutoriel (01/11, choix du propriétaire : 4)

Monde 1.0.9 : `vues_par_monde = ["crac:tuto=4"]` dans `heraliz_distances-server.toml`, posée après la vue générale (8)
au démarrage et à l'arrivée d'un monde chargé plus tard. À vérifier en jeu : ligne `[utils] distance de vue de crac:tuto
: 4 tronçons` au démarrage ; dans le tutoriel, la parcelle entière reste visible, le terrain s'arrête à ~64 blocs ;
revenu sur la carte ou au spawn, la vue est de nouveau 128 blocs. Liste vide : la vue générale partout.
