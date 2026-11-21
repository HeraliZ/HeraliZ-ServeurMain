# Audit global du 21/11 — a10 : les liens entre les mods

Relecture SEULE (aucun code de module changé) de tout ce qui relie nos mods entre eux et aux mods des autres auteurs :
appels « par leur nom » (réflexion), mixins, dépendances déclarées, canaux réseau, clés de données partagées, ordre de
chargement. Modules relus : tous ceux qui sont livrés (tous sauf `events`, `banc`, `capture`, `_gabarit`), branche
`carte-ile-reglages` du 21/11 (après la fusion des neuf autres relectures).
**Rien n'a été testé en jeu**, rien n'a été fabriqué ici (les dépôts de Minecraft et de Forge ne sont pas joignables
depuis cette machine) : tout est lu dans les sources, dans les jars des autres auteurs du dépôt et dans le jar de Mohist
1.20.1-916 de la Release « base-serveur ».

## En bref (pour les propriétaires)

- **Un vrai problème** : les « lettres nettes » de l'interface (03/11) ne sont **jamais chargées** chez les joueurs. Le
  jeu marche, mais les petits textes de nos écrans gardent l'ancien rendu. Correction d'une ligne (n° 1 ci-dessous).
- **Quelques petites choses** sans effet aujourd'hui (dépendances mal annoncées, un réglage de ModernFix à ne pas
  allumer, des numéros de version à changer ensemble).
- **Tout le reste est bon** : les 808 appels par leur nom, les 3 552 liens directs entre modules, les 197 mixins, les 44
  canaux réseau et les 17 clés de données partagées ont été vérifiés un par un, sans autre erreur.
- Deux outils ajoutés pour refaire ce contrôle en 20 secondes : `outils/verif_reflexion.py` et `outils/verif_mixins.py`.

## Problèmes trouvés

### 1. Interface : la config des « lettres nettes » n'est jamais chargée — important

- `modules/interface/build.gradle` l. 56 écrit le manifeste du jar avec `'MixinConfigs' : 'heraliz_interface.mixins.json'`
  seulement. La 2e config, `heraliz_interface_net.mixins.json` (`modules/interface/src/main/resources/`, déclarée l. 30
  par `config "heraliz_interface_net.mixins.json"`), n'y est pas : MixinGradle n'ajoute ses configs au manifeste que
  s'il n'a pas déjà d'attribut `MixinConfigs` (code de `MixinExtension.groovy`, tâche `AddMixinsToJarTask`). Forge ne
  lit que le manifeste : ses 3 mixins (`TexteDensiteMixin`, `FontSetAccesseur`, `TrueTypeAccesseur`, paquet
  `fr.heraliz.interfaces.nettete.mixin`) ne s'appliquent jamais, donc `client/PolicesDensite` (lettres retracées à la
  finesse de l'écran, gras et ombre d'un point : `docs/TESTS_EN_JEU_03NOV_ui-net.md` 1a et 1c) ne sert jamais. Aucun
  message dans le journal (rien n'est même tenté). `outils/polices_nettes.py` l. 8-10 et `PolicesNettes.java` l. 33 la
  disent pourtant active depuis le 03/11.
- **Correction** : `modules/interface/build.gradle` l. 56 →
  `'MixinConfigs'          : 'heraliz_interface.mixins.json,heraliz_interface_net.mixins.json'` ;
  `modules/interface/gradle.properties` `mod_version=2.0.17` → `2.0.18`. Après fabrication :
  `unzip -p "livraison/mods client/heraliz_interface-2.0.18.jar" META-INF/MANIFEST.MF` doit citer les deux configs.
  Spartan n'est pas gêné (`outils/construire.py` `_mixins` met déjà cette config dans `officiel.json`, elle est dans le jar).
- **À tester en jeu** (ce rendu n'a peut-être jamais tourné chez un joueur) : sections 1a et 1c de
  `docs/TESTS_EN_JEU_03NOV_ui-net.md` (petits textes nets aux échelles 2, 3, 4, Auto ; russe ; gras et ombre du chat ;
  images par seconde dans un écran plein de texte). En cas de souci : `-Dheraliz.lettresNettes=false` coupe le rendu.

### 2. ModernFix : même appel redirigé que notre noyau, si une option est allumée — mineur (rien aujourd'hui)

- `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/allege/SpawnSansTicketMixin.java` l. 21 (`@Redirect` de
  `ServerChunkCache.addRegionTicket` dans `ServerLevel.setDefaultSpawnPos`) et le mixin
  `org.embeddedt.modernfix.common.mixin.perf.remove_spawn_chunks.ServerLevelMixin` de ModernFix redirigent le même appel.
  Deux `@Redirect` du même appel : un des deux est ignoré, et la config `heraliz_noyau_allege` (defaultRequire 0) ne dit
  rien. Aujourd'hui l'option est coupée (`serveur/config/modernfix-mixins.properties` l. 113, valeur par défaut `false`) :
  aucun effet.
- **Correction proposée** : figer le choix en ajoutant à la fin de `serveur/config/modernfix-mixins.properties` la ligne
  `mixin.perf.remove_spawn_chunks=false` (ne jamais l'allumer : le noyau fait déjà ce travail, `MondesSansSpawn`).

### 3. Le noyau annoncé « côté joueur » seulement alors que le serveur s'en sert — mineur

Le code serveur de ces mods appelle le noyau (tag de combat, matchs, écritures en fond…), mais leur `mods.toml` ne le
demande que chez le joueur (`side="CLIENT"`) : sans noyau sur le serveur, l'erreur serait un plantage en jeu au lieu
d'un message clair au démarrage (aucun effet aujourd'hui : le noyau est toujours là).
- `modules/clans/src/commun/resources/META-INF/mods.toml` l. 92 (heralisz_clans),
  `modules/monde/src/commun/resources/META-INF/mods.toml` l. 296 (heraliz_extraction),
  `modules/equipement/src/commun/resources/META-INF/mods.toml` l. 60 (heraliz_armory),
  `modules/loot/src/commun/resources/META-INF/mods.toml` l. 34 (déjà noté par a6 n° 4). Bases : déjà corrigé (`side="BOTH"`).
- **Correction** : `side="CLIENT"` → `side="BOTH"` sur ces quatre lignes (et la version du mod si on le fait).

### 4. Liens directs vers un autre mod sans dépendance déclarée — mineur

Classes citées directement (import, sans réflexion) d'un mod qui n'est pas déclaré obligatoire : sans lui, plantage au
premier appel. Tous ces mods sont toujours livrés ensemble : aucun effet aujourd'hui.
- `heraliz_evenements` → noyau : `modules/evenements/src/serveur/java/fr/heraliz/evenements/serveur/EcrituresFond.java`
  l. 28 (`EcrituresFond.voie`, dès le chargement de la classe) ; son `mods.toml` ne cite que `airstrike`.
  **Correction** : ajouter dans `modules/evenements/src/commun/resources/META-INF/mods.toml` un bloc
  `[[dependencies.heraliz_evenements]] modId="heraliz_noyau" mandatory=true versionRange="[1.0.0,)" ordering="NONE" side="BOTH"`.
- `heraliz_grades` → économie et clans : `HdvServeur.java` l. 12-13, `CommandeArgent.java` l. 5, `PanelEconomie.java`
  l. 10-11 (`modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/`) ; même constat que a1 n° 6.
- Liens vers des mods facultatifs passés par une classe « compat » chargée seulement si le mod est là (`ClansCompat`,
  `CuriosCompat`, `CaraCreaPeaux`, `CaraCreaPnj`…) : corrects, rien à faire.

### 5. Versions exactes dans le jar du monde — mineur

`modules/monde/src/commun/resources/META-INF/mods.toml` l. 55, 93, 149, 188, 220 : `versionRange="[2.26.1]"` (ping, corps,
usure, véhicules, carte) exige EXACTEMENT la version de heralisz_utils (l. 10). Changer l'une sans les autres empêche
tout le jar de se charger. **Correction proposée** : `versionRange="[2.26,)"`, ou bien changer les six ensemble à chaque
nouvelle version.

### 6. Injections qui peuvent rater sans rien dire — à décider

11 configs ont `defaultRequire = 0` (crac, crac_client, crac_serveur, heraliz_armory_serveur, heraliz_loot_serveur,
heralisz_utils_cibles, heralisz_utils_serveur, heraliz_noyau_allege — 42 mixins —, heraliz_noyau_profil,
heraliz_noyau_serveur_forge, heraliz_noyau_matmos), et 187 injections ont `require = 0`. C'est voulu (le jeu démarre même
si un mod d'un autre auteur change), mais une injection qui ne trouve plus sa cible ne laisse aucune trace : le
démarrage d'essai sur GitHub ne voit que les injections obligatoires. **Proposition** : sur le serveur d'essai seulement,
un démarrage avec `-Dmixin.debug.countInjections=true` arrête Mixin sur la première injection qui ne trouve rien (à
relancer jusqu'à ce qu'il passe, en notant les injections facultatives voulues) ; ou `outils/verif_mixins.py` sur un PC
qui a Minecraft en noms SRG (`outils/chemins.py`).

### 7. Petites remarques (rien à faire)

- `modules/bunker/src/commun/java/com/hbm_m/lib/RefStrings.java` l. 8-9 : deux constantes d'origine de HBM nomment des
  classes qui n'existent pas (`ClientProxy`, `ServerProxy`) ; rien ne les lit : sans effet.
- Deux `@Overwrite` de TaCZ (`modules/armes/src/client/java/fr/heralisz/camo/mixin/BedrockCubeBoxRenduMixin.java` l. 24,
  `BedrockCubePerFaceRenduMixin.java` l. 31) remplacent toute la méthode `compile` : aucun autre mod du pack ne la vise
  (657 injections des autres mods comparées) ; à revoir à chaque nouvelle version de TaCZ.
- Bibliothèques embarquées en double : SimpleBedrockModel 2.2.2 (dans TaCZ) et 2.5.1 (dans les véhicules) ; Forge
  garde la 2.5.1 pour les deux (aucun de nos fichiers ne s'en sert directement). MixinExtras : 0.3.2 à 0.4.1 selon les
  mods, Forge garde la 0.4.1 (armes l'utilise en 0.3.6 : compatible). Player Animator : dans `serveur/mods-tiers` et
  dans le jar des emotes, même version.

## Vérifié sans problème

**Appels par leur nom** (`outils/verif_reflexion.py`) : 808 appels relevés (`Class.forName`, `getMethod`,
`getDeclaredField`, `MethodHandles.findStatic`…, `ObfuscationReflectionHelper`, petites fonctions d'aide), 321 chaînes
qui nomment une de nos classes (dont tous les `Parties.serveur / client`), 1 message IMC.
- 613 vérifiés exacts : classe, nom, types exacts des paramètres, `public`/`static` comme utilisé, type rendu, bon côté :
  365 vers nos classes, 104 vers Forge et Bukkit du serveur (jar de Mohist 916), 86 TaCZ, 13 Curios, 8 AuthMe,
  6 Zombie Awareness, 2 GeckoLib, 2 taczaddon, 2 Embeddium, 1 Emotecraft, 12 noms SRG vus dans d'autres jars, 6 Java.
- Membres ajoutés par Bukkit / Spigot lus par le noyau, confirmés dans Mohist 916 (sources publiques du build, commit
  `e49a904e`) : `Level.spigotConfig`, `Level.keepSpawnInMemory`, `Entity.activatedTick`, `Entity.visibleByDefault`,
  `Entity.getBukkitEntity()`, `ChunkMap.anyPlayerCloseEnoughForSpawning(ChunkPos, boolean)`, et `KeyMapping.keyModifierDefault`
  (ajouté par Forge, interface).
- 33 appels vers des mods facultatifs absents du pack (Iris, OptiFine, Indium, Fabric API, voicechat…), tous protégés.
- Vérifiés à la main parce que la classe n'est connue qu'en jeu : profil du joueur (`interface` → enregistrement
  `Extraction` de l'extraction, `TerritoireVue.nom` des clans), Curios lu par réflexion (`clans/saison/Ponts.java`,
  `decors/serveur/ZonesServeur.java` : classes publiques `CurioInventoryWrapper`, `CombinedInvWrapper`), champs de TaCZ lus
  par Spartan (`shooter`, `data`, `baseTimestamp`), tenue des zombies (`ZombieSurvie.tenue()`).
- IMC `ally_predicate` (train du monde → Spartan) : envoyé et reçu côté serveur, type vérifié à la réception.
- Aucun appel du mauvais côté, vers un module jamais livré, vers un membre non public par `getMethod`, ni converti dans
  le mauvais type.

**Liens directs entre modules** : 3 552 imports et noms qualifiés vers un autre module ou un mod d'un seul côté. Aucun
`src/serveur` qui cite une classe de joueur d'un autre module (ni le module interface), aucun `src/client` qui cite une
classe serveur, aucun `src/commun` qui cite l'un des deux, aucun mod des joueurs seulement (Embeddium, EntityCulling,
Cloth Config) cité côté serveur, aucun plugin cité côté joueur, rien vers events / banc / capture. (Ce contrôle est celui
de `verif_split.py` sur les jars, qui ne tourne pas sur GitHub.)

**Mixins** (`outils/verif_mixins.py`) : 36 configs livrées, 197 classes mixin (dont 4 ajoutées par le plugin de
Spartan quand TaCZ est là), 420 injections et membres lus.
- Chaque config : dans le manifeste de son jar (sauf n° 1), table de correspondance (refmap) produite par la fabrication
  pour le même ensemble de sources, classes présentes dans le même ensemble, aucune section `client` dans une config du
  serveur ; table écrite à la main de Spartan : 3 noms SRG (`m_7260_` confirmé ailleurs ; `m_6459_`, `m_289599_` dans une
  config obligatoire de classes chargées au démarrage : vus par le démarrage d'essai).
- 209 cibles vérifiées dans les vrais jars des autres auteurs (TaCZ, Curios, GeckoLib, véhicules, Forge) : classe,
  méthode et descripteur, et chaque appel / champ visé par `@At(INVOKE / FIELD)` retrouvé dans le code de la méthode
  (avec le nom SRG que ces jars utilisent pour Minecraft) ; noms de Mojang + SRG écrits ensemble avec `remap = false`
  (équipement, camos) : l'un des deux existe à chaque fois.
- Cibles de Minecraft côté serveur des modules pas comparés le 21/11 (section 44 : noyau, monde, loot, réanimation,
  bunker l'étaient) : armes, bases, équipement, sacs, anticheat, comparées aux correctifs de Mohist 916 de leurs 20
  classes : aucune méthode visée changée par Mohist, sauf `FireBlock.tryCatchFire` (signature Forge avec `Direction`,
  visée par son nom seul : bon) et `Block.dropResources` (Backpacked vise la forme à 7 paramètres de Forge : bonne).
- Conflits : 657 injections des 17 mods des autres auteurs qui ont des mixins, comparées aux nôtres : 71 méthodes visées par les
  deux, toujours par des injections compatibles (aucun `@Redirect` du même appel, aucun `@Overwrite`) sauf le cas
  ModernFix coupé (n° 2) ; aucune paire de nos mixins qui redirige le même appel.

**Réseau** : 44 canaux, noms tous différents, tous créés dans `src/commun`, même version des deux côtés (4 acceptent un
joueur sans le mod : emotes, cycle, carte, ping) ; 180 lignes d'enregistrement de paquets, toutes avec un sens (joueur → serveur ou
serveur → joueur), toutes appelées depuis le code commun (constructeur ou `FMLCommonSetupEvent`), donc mêmes numéros des
deux côtés, et avant le verrou des canaux de Forge ; 63 classes de paquets joueur → serveur : aucun décodage qui réserve
une taille lue dans le paquet sans plafond.

**Clés de données partagées** : 17 clés lues ou écrites par plusieurs modules, même nom et même type partout :
`heralisz:combat_jusqua` (long), `heralisz:en_match` (booléen), `HeraliszMatch` (texte),
`PlayerPersisted.HeralizBonusLoot.<source>.{Pct (entier), Fin (long)}` (clans, monde, boutique → loot),
`HeralizClashWarDepot` (long), `heraliz_cinematique_finie` / `_vue` (booléens), `spartan_uid` (long), `HzUsureEtat`
(entier), `HeraliszCamoProprio` (UUID), `HeralizCouleurLibre` (booléen), `HeralizRarete` (texte), `caracrea` (compound),
étiquettes `heraliz_reste_allonge` et `heraliz_bot`.

**Chargement** : 39 classes `@Mod` (construites en parallèle par Forge) : leurs constructeurs ne touchent que leur mod
porteur (onglet commun protégé, `synchronized`) ; 233 `@Mod.EventBusSubscriber`, tous avec un `modid` de leur jar et le
bon côté ; aucun `RegistryObject.get()` ni réglage lu dans un initialiseur statique ; dépendances de compilation
(`compileOnly project(...)`) : seulement des modules livrés des deux côtés.

## Non vérifié (et pourquoi)

- **Les classes de Minecraft elles-mêmes** : ni Minecraft ni Forge ne sont dans le dépôt, leurs dépôts sont refusés par
  cette machine, et la fabrication n'y est pas possible. Donc 225 cibles de mixins sur Minecraft (103 dans le noyau
  serveur, 29 sacs, 23 interface, 14 armes client, 10 armes commun, 8 monde serveur…) et 30 noms SRG lus par réflexion
  ne sont confirmés que s'ils apparaissent dans un autre jar (26 et 12) ; les cibles du noyau, du monde, du loot, de la
  réanimation et du bunker l'ont été le 21/11 contre Mohist 916 (section 44 de la fiche des essais) ; les mixins des
  joueurs (interface, armes client, sacs client, voix, boutique…) n'ont été comparés qu'aux correctifs de Forge, pas au
  Minecraft client.
- **Les jars fabriqués** (manifestes, tables de correspondance réellement produites) : déduits des `build.gradle`.
- **77 appels par réflexion sur une classe connue seulement en jeu** (`x.getClass()`, plugins Bukkit, Iris) : ceux qui
  relient nos modules ont été vus à la main (ci-dessus), pas ceux vers Bukkit et Iris.
- **Injections `require = 0`** : si une cible change, rien ne le dit (n° 6).

## Les outils ajoutés (relecture seule, branchés nulle part)

- `outils/verif_reflexion.py` (lit les sources avec `outils/sources_java.py`) : appels par leur nom, chaînes de classes,
  liens directs entre modules selon le côté. Sans Gradle ni Minecraft, environ 20 s ; `--forge <jar de Mohist>` ajoute
  Forge et Bukkit du serveur ; `--tout` liste tout ; code 1 au moindre problème. Essayé avec des erreurs posées exprès
  (mauvais types, classe absente, méthode absente, mauvais type rendu, mauvais côté, import serveur depuis le joueur) :
  toutes trouvées. Sur la branche du 21/11 : 0 erreur.
- `outils/verif_mixins.py` : configs, manifestes, tables, cibles des autres auteurs, conflits (ModernFix lu dans ses
  réglages). Sur la branche du 21/11 : 1 erreur (n° 1), 0 après la correction.
- Assez fiables pour GitHub (aucune fausse erreur aujourd'hui) : par exemple dans « Contrôle de branche », après le
  téléchargement de Mohist du démarrage d'essai, `python outils/verif_reflexion.py --forge _banc/mohist-*.jar` et
  `python outils/verif_mixins.py --forge _banc/mohist-*.jar`. À décider par le coordinateur.
