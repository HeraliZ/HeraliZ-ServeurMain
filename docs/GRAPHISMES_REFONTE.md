# Graphismes : herbe légère, nettoyage des textures, profils graphiques (25/10)

Chantier « rendu côté joueur » de l'audit `docs/AUDIT_VISUEL_ZOMBIES_25OCT.md` (§2.2, §4, §5) : un aspect plus Day-Z
**sans perdre d'images par seconde**. Tout est dans la copie de travail `HeraliZ-chantier` (rien n'est appliqué au projet,
rien n'est fabriqué, **rien n'a été essayé en jeu**). Pas de shaders : Spartan les interdit, on n'y touche pas.

Repères : **[vérifié]** lu dans le code ou les fichiers ; **[calcul]** calculé hors du jeu ; **[non testé]** à voir en jeu.

---

## 1. Ce qui change, en bref

| Sujet | Avant | Après |
|---|---|---|
| Herbe (une touffe) | 32,8 faces en moyenne, déborde de 6 pixels chez les voisins | **12 faces**, reste dans son bloc |
| Haute herbe (deux blocs) | 85 faces (36 en bas, 49 en haut en moyenne) | **27 faces** (12 en bas, 15 en haut) |
| Fougère, grande fougère | modèles de Minecraft (4 et 8 faces) | inchangées (pas le même problème) |
| Herbe au loin | découpe sans mipmaps : scintille | `cutout_mipped` : versions réduites au loin, calme |
| Textures inutiles du pack « textures » | 252 images jamais affichées | à supprimer (liste `a_supprimer.txt`, 263 fichiers) |
| Neige qui tombe (`environment/snow.png`) | 1024 × 4096 (4,2 millions de pixels) | 128 × 512 dans **notre** pack (66 000 pixels) |
| Nuages | coupés chez tout le monde (réglages HeraliZ v1) | **rallumés** (v3) : nos nuages doux, un seul carré |
| Qualité graphique | aucun choix | **Performance / Équilibré / Qualité** dans le menu Échap |
| Netteté des blocs en vue rasante | aucune | filtrage anisotrope ×4 (Équilibré) ou ×8 (Qualité) |

## 2. Herbe légère (notre pack `heraliz_terrain`)

**Pourquoi** [vérifié] : les états de bloc `grass` et `tall_grass` du pack « herbe » (d'un autre auteur, crédité dans
son `pack.mcmeta`, crédit gardé) utilisent le modèle `os_tinted_cross` : 18 plans = 36 faces, dont 12 plans posés à x = −1 / 17 et penchés
vers l'extérieur (après rotation, ils vont jusqu'à **−5,9 et 21,9 pixels**, chez les voisins). La 3e haute herbe (fleurs)
en a 88. Minecraft : 4 faces (herbe), 8 (haute herbe).

**Ce qui est fait** : nos propres états de bloc et modèles dans le pack `heraliz_terrain` du module monde (tout en haut
des packs : il passe devant le pack « herbe », dont **aucun fichier n'est touché**). Les textures sont celles du pack
« herbe », citées par leur nom (`minecraft:block/grass`, `grass2`, `grass3`, `grass4`, `tall_grass_top…`), jamais
copiées. Tout est **écrit** par `python modules/monde/outils/herbe_legere.py` (jamais à la main ; `--verifier` contrôle,
`--apercu F.png` fait l'image avant / après).

| Forme | Plans | Faces | Poids | Description |
|---|---|---|---|---|
| touffe | 6 | 12 | 2 | la croix en diagonale + 4 plans qui partent du milieu et s'ouvrent à 22,5° vers les bords |
| clairsemée | 4 | 8 | 1 | la croix + 2 plans penchés d'un seul côté (la rotation de l'état de bloc la tourne) |
| dense | 8 | 16 | 1 | la touffe + 2 plans bas (13 pixels) pour le dessous |
| haute (bas et haut) | 6 | 12 | — | la croix + 4 plans droits en carré à 3 pixels des bords : les tiges des deux moitiés se suivent |
| haute fleurie (haut) | 12 | 24 | — | la même + les fleurs jaunes du pack « herbe » (sans teinte) |

- Herbe : 16 tirages au hasard (4 textures × touffe à 0° et 180°, clairsemée et dense avec une rotation différente par
  texture). **12,0 faces en moyenne** (avant 32,8 : **−63 %**) [calcul, même calcul que `FaceBakery` de Minecraft].
- Haute herbe : bas tiré à 0° ou 180°, haut parmi les 4 textures (rotations 0 / 90 / 180 / 270, la géométrie ne change
  pas : les tiges restent alignées). **27 faces par plante** (avant 85 : **−68 %**).
- Bornes : toutes les faces dans x, z = 0,8 … 15,2 et y = 0 … 16 (même hauteur que l'herbe d'avant, 16 pixels). La
  prairie paraît un peu moins « tapis » (les touffes ne se chevauchent plus) : voir `maquettes/graphismes/herbe_avant_apres.png`
  (rendu logiciel approché, vue d'un joueur debout ; **à valider par le propriétaire avant d'appliquer**, règle 6).
- **`cutout_mipped`** (clé Forge `render_type`, héritée par les modèles enfants) [vérifié dans Forge 47 et Embeddium
  0.3.31] : l'herbe passe de la découpe sans mipmaps (`cutout`) à la découpe avec mipmaps, comme les feuilles. Au loin,
  plus de scintillement des brins fins. Coût nul avec Embeddium (cutout et cutout_mipped sont dans la même passe, seul
  le biais de mipmap change : 0 au lieu de −4). Les textures d'herbe sont tout ou rien (alpha 0 ou 255) : le seuil
  0,1 → 0,5 ne change rien de près ; au loin, Minecraft calcule les petites versions en gardant l'opacité (moyenne
  « gamma ») : les brins s'épaississent un peu au lieu de clignoter. Risque : herbe un peu plus « pleine » au loin.
- Ordre des packs : `PackImpose` remonte maintenant le pack terrain juste au-dessus de nos packs « textures » et « herbe »
  s'il se trouvait en dessous (aujourd'hui il est déjà au-dessus : sécurité seulement).
- Fichiers du pack « herbe » devenus inactifs (masqués par les nôtres, **gardés** : autre auteur, retour en arrière
  facile) : ses états de bloc `grass`, `tall_grass` et les modèles `grass*`, `os_tinted_cross`, `tall_grass_*`.

## 3. Nettoyage sûr des textures

Outil : `python outils/textures_inutiles.py` (nouveau, **lecture seule** ; `--liste F` écrit la liste avec la preuve,
`--detail` dit pourquoi chaque image est gardée). Il lit [vérifié] : les 40 jars de `livraison/mods client` (jars imbriqués
compris), les ressources de Minecraft 1.20.1 (lanceur CurseForge), les classes de Minecraft et de Forge 47.4.0 en noms
officiels (cache de Gradle), les 3 packs imposés, les sources de tous les modules et les gunpacks de `serveur/tacz` :
31 895 fichiers texte, 24 453 classes, 131 286 chemins cités (deux passes, avant et après la refabrication de la livraison de 17 h 38 : même résultat).

Une image du pack « textures » n'est retenue que si elle est :

- **remplacée** (6 images) : un pack posé au-dessus (herbe, heraliz_terrain) donne la même image, Minecraft ne lit jamais
  celle du dessous ni son `.mcmeta` (`FallbackResourceManager` : le dessus gagne, les métadonnées ne sont cherchées qu'au
  niveau du pack gagnant et au-dessus) ;
- **orpheline** (246 images) : nom que ni Minecraft 1.20.1, ni Forge, ni aucun mod livré ne fournit (anciens noms 1.12 :
  `tallgrass`, `double_plant_*`, `sapling_*`, `wheat_stage_N`, `leaves_*_opaque`, `grass_top`…), cité nulle part : ni
  modèle, état de bloc, atlas, particule, police, texte, ni chaîne du code (même en morceau, « `block/destroy_stage_` » +
  numéro), et pas le nom d'un bloc ou d'un objet. Seule exception : un nom cité uniquement par les tables de conversion
  des vieux mondes (`net/minecraft/util/datafix`, noms de **blocs** 1.12) ne compte pas.

Prudence : **53 images** de l'ancienne liste de l'audit (306) sont gardées parce que leur nom apparaît ailleurs comme
simple mot (exemples : `bench`, `sofa`… dans des modèles de cfm qui citent `blocks/…` à l'ancienne ; `glass_blue` dans
les étiquettes de Forge). Rien de certain : on n'y touche pas.

| Liste `a_supprimer.txt` | Fichiers | Pixels | Grande texture des blocs |
|---|---|---|---|
| remplacées (pack textures) | 6 images | 0,36 M | 0 (jamais chargées) |
| orphelines (pack textures) | 246 images + 5 animations | 1,64 M | **−1,38 M** |
| pack herbe : `tall_grass_bottom2..4` | 3 modèles + 3 images | 768 | −768 (les images de `textures/block` sont toujours cousues) |
| **total** | **263 fichiers, 530 ko** | **≈ 2,0 M** | **≈ −1,38 M pixels ≈ −5,5 Mo de mémoire graphique (≈ −7,4 Mo avec les mipmaps)** |

La grande texture des blocs fait ≈ 52,7 M pixels (audit) : −2,6 %. Elle reste une puissance de 2 : le gain réel dépend
de la place qu'occupe le rangement (Minecraft peut garder la même taille) [calcul].

`snow.png` (neige qui tombe, `textures/environment`, hors grande texture) : 1024 × 4096 → **128 × 512** dans
`heraliz_terrain` (Pillow, LANCZOS, alpha prémultiplié, même cadrage, restes de filtre < 6/255 effacés), 168 ko → 6,6 ko,
≈ 16 Mo → 0,26 Mo en mémoire quand il neige. Le fichier de l'autre pack n'est pas touché (masqué par le nôtre ; ne pas
l'ajouter à la liste). Aperçu : `maquettes/graphismes/neige_avant_apres.png`.

Non fait (pas certain ou pas demandé) : les 160 doublons exacts (il faudrait changer des modèles d'autres auteurs) ;
`fern.png` du pack textures (32 px en couleurs, teinté par-dessus par Minecraft : un peu sombre).

## 4. Réglages graphiques

### 4.1 Réglages HeraliZ version 3 (`ReglagesDefaut`, une seule fois par PC)

- **Nuages rallumés** (s'ils étaient coupés) [vérifié dans Minecraft 1.20.1 / Forge 47 / Embeddium 0.3.31] :
  `LevelRenderer.renderLevel` n'appelle `renderClouds` que si `Options.getCloudsType() != OFF` ; `renderClouds` appelle
  d'abord `level.effects().renderClouds(...)` (notre `CielHeraliz` → `Ciel.nuages`, un seul carré) et s'arrête si elle
  répond vrai ; Embeddium ne s'accroche qu'après (sur `Float.isNaN`), donc seulement si nos nuages ne se sont pas dessinés.
- **Hors de la carte** (spawn, monde du tutoriel des bases, arènes du ClashWar : mêmes effets « overworld ») : `CielHeraliz`
  ne dessine **aucun** nuage (sinon ceux de Minecraft y seraient revenus ; le spawn est au-dessus de leur couche, à 192).
- Le reste des versions 1 et 2 est gardé (synchro verticale, images illimitées, ombres des entités, Fabuleux → Détaillés…).

### 4.2 « Qualité graphique » (menu Échap, sous « Sprint en un appui »)

Même dessin que l'interrupteur du sprint (tuile, contour au survol, nom du cran en toutes lettres) avec 3 barres de
niveau ; un clic = profil suivant ; explication au survol ; textes en français, anglais, russe. Profil gardé dans
`config/heraliz_interface-client.toml` (`[graphismes] profil`, « equilibre » par défaut).

| Réglage | Performance | **Équilibré (défaut)** | Qualité |
|---|---|---|---|
| Graphismes | Rapides | Détaillés | Détaillés |
| Distance de vue | 6 | 8 | 8 (plafond du serveur) |
| Particules | Minimal | Réduit | Tout |
| Mipmaps | 2 | 4 | 4 |
| Nuages | non | oui | oui |
| Mélange des biomes | 0 | 1 | 2 |
| Filtrage anisotrope des blocs | non | ×4 | ×8 |
| Brouillard | jamais touché | jamais touché | jamais touché |

- Les réglages sont ceux de Minecraft (Options > Graphismes, `options.txt`) : le joueur peut encore les changer à la main ;
  un clic sur le choix les remet au profil.
- Rapides / Détaillés : les tronçons sont reconstruits une fois (comme l'écran Vidéo).
- **Mipmaps en dernier et une seule fois** : comme l'écran Vidéo de Minecraft, elles sont posées **en refermant le menu
  Échap** (rechargement des textures, quelques secondes) : plusieurs clics de suite ne rechargent qu'une fois.
- Nuages : nos nuages doux sont les mêmes en Équilibré et en Qualité ; « Rapides » / « Détaillés » ne sert qu'au secours
  (nuages de Minecraft si notre shader est en panne).

### 4.3 Filtrage anisotrope de la grande texture des blocs

`Qualite.anisotrope()` : après **chaque** rechargement des ressources (écouteur de `InterfaceClient`, qui passe après
celui des modèles de Minecraft : la grande texture est déjà refaite) et à chaque changement de profil ; aucun (×1) en
Performance, ×4 en Équilibré, ×8 en Qualité, borné au maximum de la carte graphique, seulement si elle le gère
(`GL_EXT_texture_filter_anisotropic`, `GL_ARB_texture_filter_anisotropic` ou OpenGL 4.6), comme `HDTexture` de HeraliZ
Loot. Le réglage reste sur la texture quand Minecraft change son filtre (couches avec ou sans mipmaps).

## 5. Fichiers du chantier

| Fichier | État |
|---|---|
| `modules/interface/src/main/java/fr/heraliz/interfaces/client/Qualite.java` | nouveau (profils, mipmaps différées, anisotrope) |
| `modules/interface/src/main/java/fr/heraliz/interfaces/kit/Choix.java` | nouveau (réglage à crans, dessin de `Bascule`) |
| `.../interfaces/client/EcranPause.java` | pris : ligne « Qualité graphique », infobulle, mipmaps à la fermeture |
| `.../interfaces/client/ReglagesDefaut.java` | pris : version 3 (nuages) |
| `.../interfaces/client/InterfaceClient.java` | pris : anisotrope après chaque rechargement |
| `.../interfaces/client/PackImpose.java` | pris : pack terrain toujours au-dessus de nos packs |
| `.../interfaces/InterfaceConfig.java` | pris : `[graphismes] profil` |
| `modules/interface/src/main/resources/assets/heraliz_interface/lang/{fr_fr,en_us,ru_ru}.json` | pris : 8 clés `pause.graphismes*` |
| `modules/monde/src/client/java/fr/heralisz/utils/ambiance/CielHeraliz.java` | pris : aucun nuage hors de la carte |
| `modules/monde/outils/herbe_legere.py` | nouveau (générateur + contrôle + aperçu) |
| `modules/monde/src/client/resources/resourcepacks/heraliz_terrain/assets/minecraft/blockstates/{grass,tall_grass}.json` | nouveaux (écrits par le générateur) |
| `.../heraliz_terrain/assets/heralisz_utils/models/block/herbe/*.json` (22) | nouveaux (écrits par le générateur) |
| `.../heraliz_terrain/assets/minecraft/textures/environment/snow.png` | nouveau (128 × 512) |
| `outils/textures_inutiles.py` | nouveau (recherche des textures inutiles, lecture seule) |
| `maquettes/graphismes/herbe_avant_apres.png`, `neige_avant_apres.png` | nouveaux (aperçus à valider) |
| `a_supprimer.txt` (racine du chantier) | 263 chemins, chacun avec sa preuve |

Pour appliquer (propriétaire ou autre session, après validation de l'aperçu de l'herbe) : `chantier.py appliquer`,
supprimer les fichiers de `a_supprimer.txt`, puis `python outils/construire.py interface monde` et
`python outils/verifier.py interface monde` (0 problème) ; les joueurs doivent avoir les nouveaux jars `heraliz_interface`
et `heraliz_monde` (client).

## 6. Ce qui reste à faire

**Terrain uniforme en 32 px** (à dessiner nous-mêmes, **images à valider** avant tout code, règle 6). Textures du terrain
naturel encore en 16 px (taille réelle, pack qui gagne : terrain > herbe > textures > Minecraft) [vérifié] :

- sols : `dirt_path_top`, `dirt_path_side` (chemins), `mud`, `packed_mud`, `muddy_mangrove_roots_top` (boue),
  `moss_block` (mousse), `rooted_dirt`, `podzol_side` (le dessus est en 32), `farmland`, `farmland_moist`, `red_sand`,
  `grass_block_snow`, `powder_snow`, `packed_ice`, `mycelium_top` ;
- roches : `sandstone` et `red_sandstone` (côtés ; dessus et dessous en 32), `mossy_cobblestone`, `bricks`, `tuff`,
  `calcite`, `deepslate`, `deepslate_top`, `dripstone_block` ;
- plantes : `grass`, `tall_grass_top`, `tall_grass_bottom` (pack herbe d'un autre auteur : à refaire à nous),
  `large_fern_top`, `large_fern_bottom`, `dead_bush`, `dandelion`, `poppy`, `oxeye_daisy`, `cornflower`, `azure_bluet`,
  `sweet_berry_bush_stage3`, `lily_pad`, `sugar_cane`, `seagrass`, `kelp`, `glow_lichen`, `hanging_roots` ;
- arbres : `dark_oak_leaves`, `azalea_leaves` (les autres feuilles sont en 32) ;
- eau : `water_still`, `water_overlay` (Minecraft ; `water_flow` en 32) — eau plus terne à prévoir.

Priorité (vus partout sur la carte) : herbe et hautes herbes, chemins, boue, mousse, grès, briques, feuilles de chêne
noir, eau. Ordre de grandeur : +0,65 M pixels pour tout passer en 32 px (audit, §4) [calcul].

Ensuite (audit §4, §5) : textures HD des conteneurs de loot en 512 en Performance ; réglages d'Embeddium et
d'EntityCulling livrés par le launcher ; nuances des verts (colormaps) ; profils à mesurer sur un petit PC.

## 7. Ce qui n'est PAS testé en jeu

**Rien n'a été fabriqué ni essayé en jeu** (consigne : ni Gradle, ni jeu). À vérifier :

- l'herbe : rendu réel avec Embeddium (le modèle et `render_type` sont lus comme prévu par Forge et Embeddium d'après
  leur code, pas vu à l'écran), hauteur et densité, raccord des deux moitiés des hautes herbes, aspect au loin avec les
  mipmaps (herbe plus « pleine » ?), images par seconde dans une prairie (F3, `/diag client`) avant / après ;
- les nuages : visibles sur la carte avec Embeddium, absents au spawn, au tutoriel et dans les arènes ;
- la qualité graphique : clic, nom du cran qui tient dans la tuile en français, anglais et russe (mesuré hors du jeu :
  158 / 151 / 146 points pour 170 de place), infobulle, un seul rechargement des textures en refermant le menu, distance
  de vue et particules appliquées, tronçons reconstruits sans saccade gênante ;
- le filtrage anisotrope : netteté en vue rasante, **risque de fins traits de couleur au bord des blocs vus de très loin**
  (la grande texture colle les images les unes aux autres : l'anisotrope peut lire un peu chez la voisine) ; si c'est
  visible, passer Équilibré à ×2 ou le couper (une ligne dans `Qualite.Profil`) ; cartes graphiques sans l'extension :
  rien ne se passe (une ligne dans le journal) ;
- la neige plus légère (flocons plus petits et plus nets ?) ;
- la suppression des 263 fichiers : certaine d'après l'outil, mais aucune partie n'a été chargée sans eux ; regarder
  le journal du jeu (« missing texture ») après la première fabrication ;
- la fabrication : le Java (écrit sur le modèle de `Bascule`, `Sprint`, `HDTexture`) a seulement été **compilé à part
  avec javac** (sans Gradle), contre Minecraft et Forge 47.4.0 en noms officiels : module interface entier avec les
  fichiers du chantier, et `CielHeraliz` contre le jar client du module monde : **0 erreur**. Ni Gradle, ni
  réobfuscation, ni `verifier.py` (dont `verifier_langues.py`) n'ont tourné.

## Mise à jour du 25/10 (soir) : écran Graphismes et Performance pour tous (demande du propriétaire)

- **Écran Graphismes** (client/EcranGraphismes, au style des clans) : en haut les trois profils en cartes
  (Performance « Conseillé », Équilibré, Qualité) ; un clic pose tous les réglages du profil d'un coup ; dessous, les
  réglages en cours lus dans les options (noms et valeurs traduits par Minecraft) ; « Réglages détaillés » ouvre l'écran
  Vidéo d'origine (Embeddium, sinon Minecraft). Il remplace l'écran Vidéo quand on vient des Options
  (InterfaceClient.video) et s'ouvre aussi par la tuile « Qualité graphique » du menu Échap (qui ne fait plus défiler
  les profils). En panne : écran d'origine pour le reste de la session.
- **Profils** (Qualite.Profil) : en plus, ombres des entités (Qualité seulement), lumière douce (coupée en
  Performance), distance des entités (75 % en Performance) ; tous coupent la synchronisation verticale.
- **Par défaut pour tout le monde** (ReglagesDefaut version 4, une fois par installation, même chez ceux qui avaient
  choisi un autre profil) : Performance, synchronisation verticale coupée, images par seconde illimitées ; profil par
  défaut de la config : performance.
- À savoir : en Performance (graphismes « Rapides »), les feuilles sont pleines : on ne voit pas à travers. Un joueur en
  Équilibré ou Qualité voit à travers les feuillages.
- Gradle : module interface compilé (0 erreur), langues 0 erreur. **Rien vu en jeu** : placement des cartes et des
  textes à vérifier à l'œil.
- Retrait des 263 textures inutiles : **pas fait** (après l'ouverture).
