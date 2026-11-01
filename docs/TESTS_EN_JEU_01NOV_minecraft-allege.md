# Essais en jeu du 01/11 — chantier « Minecraft allégé »

Demande : le banc de charge du 01/11 (80 bots dans un vrai Mohist, run 41327773381) montre que nos mods pèsent peu
(TaCZ 2,7 %, Tactique 0,9 %, Spartan 0,6 %…) : le gros du tick vient de **Minecraft lui-même**, qui passe mal à 150 joueurs.
Ici : des mixins **serveur** du noyau (jar de Minecraft, de Forge et de Mohist intacts), chacun avec le **même résultat
que Minecraft**, chacun coupé par un réglage de `[perf]` dans `heraliz_noyau-server.toml`. Plus, à la demande du
propriétaire, les **tics aléatoires coupés** (réglage `[monde] tics_aleatoires`).

Version : **Noyau 1.0.11** (rien d'autre). Côté serveur seulement ; le numéro change, les joueurs reçoivent le nouveau jar
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
