# Méthode : fusionner des mods et séparer le client du serveur

Méthode suivie par tous nos mods coupés en « affichage » (client) et « logique » (serveur). Premier groupe traité : **les
boss** (`mods/heraliz-boss`) ; les exemples de code viennent de là (Ymirak, le mammouth : `fr.heralisz.mammoth`). Les
règles générales du dépôt (Git, fabrication, livraison) sont dans `CLAUDE.md` à la racine.

---

## 0. En bref

1. **Un jar, plusieurs mods.** Chaque mod d'origine garde son modId, son espace de noms et sa classe `@Mod`. Le
   `mods.toml` du jar a un bloc `[[mods]]` par mod, plus un mod « porteur » (ex. `heraliz_boss`) pour le code partagé.
   Aucun identifiant ne change : mondes, cartes, tables de loot, configs, gunpacks et autres mods restent valides.
2. **Trois ensembles de sources** dans un seul projet Gradle : `src/commun` (le `main` de Gradle), `src/client`,
   `src/serveur`. Client et serveur voient commun ; commun ne voit ni l'un ni l'autre ; client et serveur ne se voient pas.
   C'est le compilateur qui l'impose.
3. **Le pont** (`fr.heraliz.<groupe>.pont`) : le code commun déclare des usines et des crochets neutres ; la partie
   serveur (`PartieServeur`) et la partie client (`PartieClient`) les branchent au chargement, si elles sont dans le jar.
4. **Une classe qui a de la logique est coupée en deux** : la classe commune (état synchronisé, ce que lit le rendu) et
   sa sous-classe `XServeur` (IA, attaques, règles, sauvegarde). L'usine crée `XServeur` côté serveur.
   Outil : `outils/scinder_classe.py`.
5. **Trois jars réobfusqués** : `-client` (commun + client + `assets/`), `-serveur` (commun + serveur + `data/`),
   `-complet` (tout, pour le solo et le profil de test). Même `mods.toml`, mêmes canaux réseau.
6. **Contrôles obligatoires** sur chaque jar : `verif_reobf.py`, `check_geckolib_api.py`, `verif_split.py`.

---

## 1. Ce que FML 47 permet (vérifié dans le code de Forge 47.3)

Sources lues : `fmlloader-1.20.1-47.3.0-sources.jar`, `javafmllanguage-…-sources.jar`, `fmlcore-…-sources.jar` (cache
Gradle `~/.gradle/caches/modules-2/files-2.1/net.minecraftforge/`).

| Point | Code FML | Conséquence |
|---|---|---|
| Plusieurs `[[mods]]` dans un fichier | `ModFileInfo` : `config.getConfigList("mods")` -> une `ModInfo` par entrée | OK. |
| Une classe `@Mod` par modId | `FMLJavaModLanguageProvider.getFileVisitor` : carte modId -> classe `@Mod` du fichier | OK, plusieurs `@Mod` par jar. |
| Correspondance stricte | `ModLoader.buildMods` : un conteneur par `@Mod` trouvé ; si le nombre diffère du `mods.toml` : erreur `fml.modloading.missingclasses` ; un `@Mod` sans `[[mods]]` : `missingmetadata` | Chaque `[[mods]]` doit avoir SA classe `@Mod`, et inversement. `verif_split` le vérifie. |
| Bus du mod, configs | `FMLModContainer` : un bus d'événements et un contexte par conteneur ; le constructeur `@Mod` tourne avec SON conteneur actif | `FMLJavaModLoadingContext.get().getModEventBus()` et `ModLoadingContext.registerConfig` restent par modId : `config/heralisz_mammoth-server.toml` ne change pas de nom. |
| `@Mod.EventBusSubscriber` | `AutomaticEventSubscriber.inject` est appelé POUR CHAQUE mod du fichier ; sans `modid`, l'annotation prend le modId du conteneur en cours | **Piège n°1** : sans `modid`, la classe est inscrite une fois par mod du jar (6 fois pour les boss). Toujours écrire `modid = ...`. `verif_split` le vérifie. |
| Réseau | `NetworkRegistry.newSimpleChannel(ResourceLocation, …)` | Les canaux gardent leur nom (`heralisz_mammoth:vfx`) et leur version. |
| Ressources | un pack de ressources par FICHIER de mod | Tous les `assets/<ns>/` et `data/<ns>/` du jar sont lus, chaque espace de noms à sa place. |
| Mixins | attribut de manifeste `MixinConfigs` lu par Mixin, liste séparée par des virgules | Plusieurs configs dans un jar : OK. |
| `ModList.isLoaded("heralisz_n07")` | liste des `ModInfo` | Les autres mods qui testent un modId continuent de marcher. |
| Nom de module Java | premier modId du fichier | Mettre le mod porteur en premier (`heraliz_boss`). Un paquet Java ne doit exister que dans UN jar : retirer les anciens jars ! |

Conclusion : la fusion « un jar, plusieurs mods, identifiants inchangés » est prise en charge proprement par FML 47.
Aucun remappage d'identifiant n'est nécessaire (ni permis sans l'accord du propriétaire).

---

## 2. Organisation d'un projet fusionné

```
mods/<projet>/                      ex. mods/heraliz-boss
  build.gradle  settings.gradle  gradle.properties  gradlew(.bat)  gradle/wrapper/
  libs/geckolib222-4.4.7-hzfusion.jar  (dépendances en blank:<nom>:<version unique>, voir §9)
  src/commun/java/        fr/heraliz/<groupe>/        <- mod porteur : @Mod, pont/, onglet/
                          fr/heralisz/<mod>/...       <- classes communes de chaque mod
  src/commun/resources/   META-INF/mods.toml, pack.mcmeta
  src/client/java/        fr/heraliz/<groupe>/client/ <- PartieClient du porteur, panneau de l'onglet, kit/ (Ui, SonsUi)
                          fr/heralisz/<mod>/client/   <- rendus, HUD, écrans, effets, PartieClient
                          fr/heralisz/<mod>/mixin/    <- mixins client
  src/client/resources/   assets/<ns>/..., <mod>.mixins.json
  src/serveur/java/       fr/heralisz/<mod>/serveur/PartieServeur.java
                          fr/heralisz/<mod>/<paquet>/XServeur.java   <- MÊME paquet que la classe commune X
  src/serveur/resources/  data/<ns>/...
```

Règles de nommage (les outils s'en servent) :
- la sous-classe serveur de `X` s'appelle **`XServeur`** et vit dans le **même paquet** que `X` (dans `src/serveur`) ;
- les points d'entrée s'appellent **`<paquet du mod>.serveur.PartieServeur`** et **`<paquet du mod>.client.PartieClient`** ;
- le mod porteur s'appelle `heraliz_<groupe>` (paquet `fr.heraliz.<groupe>`), ses classes partagées sont dans
  `fr.heraliz.<groupe>.pont` et `fr.heraliz.<groupe>.onglet`.

Même paquet dans deux ensembles de sources : javac l'accepte (chemin de classes), et dans le jar tout est dans le même
module. C'est voulu : la sous-classe serveur accède aux membres « package-private » de la classe commune.

---

## 3. Démarrer un projet (nouveau groupe)

1. `python outils/fusion_nouveau.py <dossier> <groupe> "<Nom affiché>"` — ex.
   `python outils/fusion_nouveau.py heraliz-tactique tactique "HeraliZ Tactique"`. Copie `mods/_gabarit`, renomme le
   paquet (`fr.heraliz.tactique`), le mod porteur (`heraliz_tactique`) et le jar.
2. Pour chaque mod à fusionner : `python outils/fusion_copier.py <dossier des sources reçues> mods/<dossier>`. Premier tri automatique :
   paquets `client` et mixins client -> `src/client`, `assets/` -> client, `data/` -> serveur, le reste -> commun. Il
   liste les classes communes qui citent encore du code client (à brancher, §6).
3. `mods.toml` : ajouter un bloc `[[mods]]` par mod (modId d'origine, `version="${mod_version}"`,
   `displayName="HeraliZ <Groupe> · <Nom>"`) et ses dépendances ; chaque mod dépend du porteur (`versionRange="[${mod_version}]"`).
   Recopier les dépendances d'origine (geckolib, curios…) bloc par bloc.
4. Dans chaque classe `@Mod` d'origine :
   - retirer son `DeferredRegister<CreativeModeTab>` et son onglet ; à la place, ajouter un groupe à l'onglet du porteur
     (§11) ;
   - à la fin du constructeur, brancher les parties (le nom de classe est une chaîne : voir §4) :
     ```java
     Parties.serveur(MODID, "fr.heralisz.mammoth.serveur.PartieServeur", bus);
     Parties.client(MODID, "fr.heralisz.mammoth.client.PartieClient", bus);
     ```
5. Vérifier tous les `@Mod.EventBusSubscriber` : `modid` obligatoire.
6. Brancher les crochets client (§6) jusqu'à ce que `compileJava compileClientJava` passe. À ce stade, la fusion
   marche déjà (toute la logique est encore commune) : c'est une bonne étape pour un premier jeu de jars.
7. Puis découper, classe par classe (§5), en recompilant souvent.

---

## 4. Le pont (`fr.heraliz.<groupe>.pont`)

Copié tel quel du gabarit ; ne pas le modifier dans un projet sans raison.

| Classe | Rôle |
|---|---|
| `Partie` | interface `init(IEventBus busMod)` des points d'entrée `PartieServeur` / `PartieClient`. |
| `Parties` | `Parties.serveur(modId, "nom.de.Classe", bus)` charge la partie serveur si sa classe est dans le jar ; `Parties.client(...)` pareil, seulement sur un client physique. Une ligne de journal par mod (« partie serveur chargée / absente »). `serveurPresent(modId)` pour savoir si la logique est là. |
| `UsineEntite<T>` | usine d'`EntityType` : version serveur dans un monde serveur, version commune dans le monde client. |
| `UsineBlocEntite<T>` | usine de `BlockEntityType` : version serveur dès que la partie serveur est dans le jar. |
| `Usine<A, T>` | usine générique (blocs, objets…) : version serveur dès que la partie serveur est dans le jar. |
| `Crochet<T>` | point d'accroche « appel simple » (paquets reçus, tickers client) : neutre tant que rien n'est branché. |

Pourquoi des chaînes de caractères pour les parties : le code commun ne doit pas pouvoir citer une classe serveur ou
client (le compilateur l'interdit). `Class.forName` trouve la classe si elle est dans le jar, sinon le mod reste neutre.

Quand : `Partie.init` est appelée PENDANT le constructeur du `@Mod`, avant les événements d'enregistrement. C'est le seul
bon moment pour brancher les usines (les `DeferredRegister` appellent leurs fournisseurs plus tard).

Exemple complet (Ymirak) :

```java
// src/commun/.../HeraliszMammoth.java
public static final UsineEntite<Mammoth> MAMMOTH_USINE = new UsineEntite<>(Mammoth::new);
public static final RegistryObject<EntityType<Mammoth>> MAMMOTH = ENTITIES.register("mammoth",
   () -> EntityType.Builder.of(MAMMOTH_USINE, MobCategory.MONSTER).sized(6.0F, 7.0F)...build("mammoth"));

// src/serveur/.../mammoth/serveur/PartieServeur.java
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      HeraliszMammoth.MAMMOTH_USINE.serveur(MammothServeur::new);
      HeraliszMammoth.DRAUGR_USINE.serveur(DraugrServeur::new);
      HeraliszMammoth.GUARDIAN_SPEAR_USINE.serveur(GuardianSpearServeur::new);
      ModBlocks.ALTAR_USINE.serveur(HornAltarBlockEntityServeur::new);
   }
}
```

Ce que donne chaque jar :
- `-serveur` sur le serveur : `PartieServeur` chargée, les boss ont leur IA ; pas de `PartieClient` (rien à faire).
- `-client` chez un joueur en multijoueur : `PartieClient` chargée ; les entités du monde client sont les classes
  communes (affichage) ; la logique tourne sur le serveur.
- `-client` en solo : pas de logique (boss immobiles, autels inertes). C'est voulu : le solo se joue avec `-complet`.
- `-complet` : les deux parties ; le client affiche quand même les classes communes (même rendu que les joueurs).

---

## 5. Découper une classe

### 5.0 Que mettre où (règle de tri)

**Commun** — ce que les deux côtés doivent connaître ou que le rendu lit :
- les enregistrements (DeferredRegister, `RegistryObject`, usines), les attributs (`createAttributes`), les configs
  (`ForgeConfigSpec`, enregistrées dans le `@Mod`) ;
- dans une entité : TOUS les `EntityDataAccessor` et `defineSynchedData`, les lectures de l'état synchronisé (`phase()`,
  `action()`…), les constantes et `enum`, le constructeur, les drapeaux de comportement lus des deux côtés
  (`isPushable`, `isInvulnerableTo`, `canBeAffected`, `removeWhenFarAway`…), les contrôleurs GeckoLib
  (`registerControllers`, `getAnimatableInstanceCache`), ce que lisent le HUD et les cinématiques (plans de caméra,
  couleurs, titres) et les calculs d'affichage (`sink(t)`, `headGone(partial)`…) ;
- les paquets réseau (classes, encodage, décodage) et les fonctions d'envoi (`Vfx.circle(...)`) ;
- les blocs et objets « simples » (forme, état, infobulle, `animateTick`), les entités de bloc réduites aux données
  synchronisées ;
- les interfaces lues par d'autres mods (ex. `fr.heralisz.n07.api.BossInfo`, lue par heralisz_matchs côté client).

**Serveur** — ce qui ne tourne que côté serveur et qui « décide » :
- IA et buts (`registerGoals`, `customServerAiStep`), attaques, phases, dégâts (`hurt`, calculs de PV virtuels), barre de
  boss (`ServerBossEvent`), butin, apparitions, rituels, remise en état des salles ;
- sauvegarde NBT des entités (`addAdditionalSaveData` / `readAdditionalSaveData`) ;
- sons joués par l'entité (`getAmbientSound`, `playHurtSound`, `playStepSound`…) : dans Minecraft ils ne sont joués QUE
  par le serveur (côté client, `playSound` sans joueur ne fait rien) ;
- commandes, écouteurs d'événements serveur (gardes d'apparition…), gestionnaires (salles, combats), API serveur pour
  les autres mods (ex. `N07Api`).

**Client** — l'affichage : rendus, modèles, écrans, HUD, sons d'ambiance et musiques, effets, cinématiques, mixins
client, gestionnaires de paquets reçus par le client.

Cas limite : une entité purement visuelle dont la seule logique serveur est de disparaître (ex. `SpectralHand` : elle se
supprime quand son maître meurt) peut rester entièrement commune. Tout ce qui touche au jeu (dégâts, IA, monde, butin,
apparitions, règles) va côté serveur.

### 5.1 Entité (exemple : `Mammoth` -> `Mammoth` + `MammothServeur`)

1. Regarder les membres : `python outils/scinder_classe.py src/commun/java/.../Mammoth.java --liste` (indices `S` = mots
   serveur, `C` = mots client).
2. Choisir : `--serveur` = membres déplacés ; `--deux` = méthodes qui existent des deux côtés (la classe commune garde la
   branche client / neutre, la version serveur reprend la méthode d'origine TELLE QUELLE). Essai d'abord :
   ```
   python outils/scinder_classe.py <Mammoth.java> --serveur bossBar,threat,...,customServerAiStep,hurt,... --deux tickDeath --essai
   ```
   puis sans `--essai`. L'outil crée `src/serveur/java/.../MammothServeur.java`, retire `private` des membres communs
   cités par le serveur, et signale ce que le commun cite encore.
3. Réduire à la main les méthodes `--deux` côté commun. Ex. `tickDeath` d'Ymirak : la version d'origine fait la
   cinématique de mort côté serveur et fait avancer le temps de mort côté client. Commun :
   ```java
   @Override
   protected void tickDeath() {
      if (!this.level().isClientSide) {   // sans partie serveur dans le jar : mort vanilla
         super.tickDeath();
         return;
      }
      if (this.entityData.get(DEATH_WALK)) {
         this.deathTime = 0;
         return;
      }
      ++this.deathTime;
   }
   ```
   `MammothServeur.tickDeath` = la méthode d'origine, intacte (ses branches `isClientSide` y sont mortes, sans danger).
4. `python outils/scinder_classe.py <Mammoth.java> --imports` pour retirer les imports devenus inutiles.
5. Enregistrement par usine (§4) et branchement dans `PartieServeur`.
6. Compiler (`compileJava compileClientJava compileServeurJava`) et corriger le code serveur qui appelle des méthodes
   déplacées : il doit manipuler le TYPE SERVEUR (§5.7).

Règles :
- **Tous les `EntityDataAccessor` dans la classe commune.** Un `defineId` dans `XServeur` décalerait les numéros : le
  client ne comprendrait plus les données (plantage).
- `registerGoals` est appelé par le constructeur de `Mob`, des deux côtés, avant les initialisations de champs : on le met
  dans `XServeur` (le client n'exécute jamais les buts). Même comportement qu'avant pour les champs.
- Entités qui en créent d'autres : toujours `TYPE.get().create(level)`, jamais `new X(...)` (on aurait la version neutre).
  Côté serveur, `new XServeur(...)` (constructeur d'origine avec lanceur, cible…) est permis : c'est bien la version serveur.
  `verif_split` signale tout `new X` d'une classe qui a une version serveur.

### 5.2 Entité de bloc

**a) Logique propre (ticker, état serveur) : sous-classe + `UsineBlocEntite`.** Exemple : l'autel du Cor
`HornAltarBlockEntity` (783 lignes) -> commun 150 lignes (état synchronisé `state/stateTime/nextReady`, `nearest()` lu
par l'ambiance client, animations) + `HornAltarBlockEntityServeur` (rituel, Gardiens, combat, remise en état, NBT).

- Le bloc ne fait plus `new` : `newBlockEntity(pos, state) { return ModBlocks.ALTAR_BE.get().create(pos, state); }`.
- Le ticker serveur reste une méthode statique commune qui appelle une méthode neutre redéfinie côté serveur :
  ```java
  public static void serverTick(Level lvl, BlockPos pos, BlockState st, HornAltarBlockEntity be) {
     be.tick((ServerLevel) lvl);
  }
  void tick(ServerLevel level) {}                 // commun : neutre ; HornAltarBlockEntityServeur la redéfinit
  public void use(ServerPlayer p) {}              // clic droit (appelé par HornAltarBlock.use) : idem
  ```
- `load` est appelé côté client avec l'étiquette de synchronisation : le commun lit ce que `getUpdateTag` envoie ; la
  version serveur lit la sauvegarde complète (et appelle `super.load`). `saveAdditional` et `getUpdateTag` : serveur.
- Attention : une entité de bloc est créée sans connaître son monde. Avec `-complet`, les entités de bloc du CLIENT sont
  aussi des versions serveur ; leur code doit rester sûr côté client, comme dans le mod d'origine (le ticker serveur
  n'est donné qu'au monde serveur, les méthodes serveur reçoivent un `ServerLevel`).

**b) Juste une action serveur sur des données communes : fonction serveur à part.** Exemple : le mur de glace
`IceGateBlockEntity` garde ses données (largeur, hauteur, synchronisées pour le rendu) ; l'action « fermer / ouvrir » est
devenue `IceGates.setClosed(be, level, fermé)` dans `src/serveur`. Pas d'usine, pas de sous-classe.

### 5.3 Blocs et objets

La plupart restent communs : leur `use()` délègue à une méthode neutre de leur entité de bloc (§5.2), et leurs
`animateTick` / infobulles sont de l'affichage. Si un bloc ou un objet porte lui-même de la logique (dégâts, monde,
règles), le couper comme une entité et l'enregistrer par `Usine` :
```java
public static final Usine<Void, Block> TRAP_USINE = Usine.simple(TrapBlock::new);    // commun
BLOCKS.register("trap", TRAP_USINE::creer);
ModBlocks.TRAP_USINE.serveur(a -> new TrapBlockServeur());                           // PartieServeur.init
```

### 5.4 Projectiles et petites entités

Le mouvement reste commun (fluidité côté client), les coups et l'atterrissage passent côté serveur. Exemple
`GuardianSpear` :
```java
// commun
@Override
public void tick() {
   super.tick();          // Projectile.tick
   vol();
}
protected void vol() {    // part commune : un pas de vol en cloche
   Vec3 v = this.getDeltaMovement();
   this.setPos(this.position().add(v));
   this.setDeltaMovement(v.scale(0.99).add(0, -0.035, 0));
}
// serveur (GuardianSpearServeur) : le code d'origine qui suivait super.tick(), tel quel (collision, dégâts, plantage)
@Override
protected void vol() { ... }
```
Ne PAS redéfinir `tick()` côté serveur en recopiant l'original : son `super.tick()` appellerait la version commune, qui
bouge déjà la lance, et la lance bougerait deux fois par tick (piège n°15).

### 5.5 Classe mère abstraite qui porte de la logique

Ex. `HeraliszBoss<A>` (N-07, Peste) : classe mère générique avec l'IA commune à tous les boss, une seule sous-classe
concrète (`SujetN07`, `PlagueBearer`). Java n'a qu'un héritage : on ne peut pas avoir `HeraliszBossServeur` ET
`SujetN07Serveur` dans la chaîne. Méthode :
1. couper d'abord la classe concrète : `SujetN07` -> `SujetN07` + `SujetN07Serveur` ;
2. puis la mère, en envoyant ses membres serveur DANS la sous-classe serveur de la classe concrète :
   ```
   python outils/scinder_classe.py <HeraliszBoss.java> --serveur ... --deux ... --vers <SujetN07Serveur.java> --type A=Action
   ```
   (`--type` remplace le paramètre de type `A` par le type concret dans le texte déplacé).
3. les méthodes abstraites serveur de la mère (`runAction(ServerLevel…)`, `choose`, `deathTick`…) deviennent de simples
   méthodes de `SujetN07Serveur` (retirer `abstract` de la mère et la déclaration, garder les implémentations).
4. un champ serveur initialisé par le constructeur de la mère (ex. `bossBar` avec sa couleur) : l'initialiser dans
   `XServeur` (initialiseur de champ ou constructeur).

### 5.6 Classes 100 % serveur

Commandes (`MammothCommand`), gardes d'événements (`SpawnGuard`), gestionnaires, calculs de dégâts (`WeakPoint`) : on
déplace simplement le fichier de `src/commun/java` vers `src/serveur/java` (même chemin). Leurs
`@Mod.EventBusSubscriber(modid = ...)` les inscrivent tout seuls quand ils sont dans le jar.

### 5.7 Code serveur qui manipule des objets communs

Le code serveur reçoit souvent le type commun (`level.getEntity(...)`, `getBlockEntity(...)`, `create(level)`). Il doit
passer au type serveur pour appeler la logique :
```java
if (!(HeraliszMammoth.MAMMOTH.get().create(level) instanceof MammothServeur e)) return;   // créé par l'usine
private MammothServeur findBoss(ServerLevel level) {
   return level.getEntity(boss) instanceof MammothServeur m && m.isAlive() ? m : null;
}
public HornAltarBlockEntityServeur altar() { ... instanceof HornAltarBlockEntityServeur a ? a : null; }
public static HornAltarBlockEntityServeur nearestServeur(Level l, BlockPos p, double r) {
   return nearest(l, p, r) instanceof HornAltarBlockEntityServeur a ? a : null;
}
```
Les méthodes statiques déplacées s'appellent par la classe serveur (`DraugrServeur.of(...)` ; l'outil liste les appels).
Un `level.getEntitiesOfClass(Draugr.class, …, d -> d.master …)` qui lit un champ serveur devient
`getEntitiesOfClass(DraugrServeur.class, …)`.

---

## 6. Réseau et crochets

Les classes de paquets sont communes. Leur gestionnaire ne cite jamais de code client : il appelle un `Crochet` que la
partie client branche. **`DistExecutor` ne doit plus apparaître dans le commun** (il cite une classe client).

```java
// commun (vfx/Vfx.java)
public static final Crochet<Packet> RECEPTION_CLIENT = new Crochet<>();
void handle(Supplier<NetworkEvent.Context> ctx) {
   ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(this));
   ctx.get().setPacketHandled(true);
}
// client (mammoth/client/PartieClient.java)
Vfx.RECEPTION_CLIENT.brancher(VfxManager::receive);
```
Paquet reçu par le serveur (client -> serveur) : même principe avec un crochet branché par `PartieServeur`, ou un
gestionnaire qui appelle une méthode neutre d'un objet commun redéfinie côté serveur. Les paquets eux-mêmes (numéros,
classes, encodage) restent identiques des deux côtés : on ne touche ni à l'ordre d'enregistrement ni à la version du
canal.

Tickers client d'entités de bloc (`DecorBlockEntity::clientTick`, N-07) : même chose, `TICK_CLIENT` est un `Crochet`
branché par `PartieClient`.

---

## 7. Côté client

- Les paquets `client/` d'origine passent tels quels dans `src/client`. Leurs `@Mod.EventBusSubscriber(modid = ...,
  value = Dist.CLIENT)` les inscrivent tout seuls (jamais sur un serveur dédié, même avec `-complet`).
- `PartieClient` ne fait que brancher les crochets du commun (paquets, tickers).
- Bancs d'essai de développement (`dev/Harness`, actifs seulement avec `-Dheralisz.harness`) : côté client.
- **Mixins** : configs dans `src/client/resources/*.mixins.json`. Une seule table de correspondance pour tout le jar :
  `mixin { add sourceSets.client, "${jar_name}.refmap.json" }` dans `build.gradle`, et `"refmap": "<jar_name>.refmap.json"`
  dans CHAQUE config. Le manifeste `MixinConfigs` des jars `-client` et `-complet` les liste toutes (fait par
  `build.gradle`), le jar `-serveur` n'en a aucune.
- Mixin commun (rare) : mettre la config dans `src/commun/resources`, la classe dans `src/commun/java`, et ajouter
  `mixin { add sourceSets.main, ... }` ; à faire seulement si nécessaire.

---

## 8. Construire et contrôler

Construction (TOUJOURS par lancer.py ; 2 Gradle au plus sur la machine ; jamais `gradlew --stop`, jamais
`JAVA_TOOL_OPTIONS`) :
```
PYTHONIOENCODING=utf-8 python outils/lancer.py build mods/<projet> compileJava compileClientJava compileServeurJava   # rapide
PYTHONIOENCODING=utf-8 python outils/lancer.py build mods/<projet> build livrer                                       # les 3 jars -> dist/fusion/
```
Le journal complet est dans `mods/<projet>/run/lancer_<tâches>.log`. Chaque jar doit avoir sa tâche
`> Task :reobfJar`, `:reobfClientJar`, `:reobfServeurJar`, puis `BUILD SUCCESSFUL`.

Contrôles (sur CHAQUE jar livré) :
```
python outils/verif_reobf.py --reference "livraison/mods serveur/"*.jar --verifier dist/fusion/<jar>
python outils/check_geckolib_api.py dist/fusion/<jar> serveur/mods-tiers/geckolib222-forge-1.20.1-4.4.7.jar
python outils/verif_split.py dist/fusion/<nom>-<v>-client.jar dist/fusion/<nom>-<v>-serveur.jar dist/fusion/<nom>-<v>-complet.jar --mods "livraison/mods client" --projet mods/<projet>
```
- `verif_reobf` : restes légitimes = constantes d'enum, méthodes ajoutées par Forge, Netty, blaze3d, brigadier.
- `check_geckolib_api` : « 0 absentes ». Aucun jar ne doit embarquer GeckoLib (pas de `META-INF/jarjar`).
- `verif_split` (voir sa docstring) : références introuvables du bon côté (client SRG + Forge 47.4.0 du lanceur pour
  `-client`/`-complet`, serveur SRG + Forge 47.3.12 du serveur de test pour `-serveur`, membres `@OnlyIn(CLIENT)`
  compris), classes client dans `-serveur`, `data/` dans `-client`, `assets/` et mixins dans `-serveur`, `[[mods]]` <->
  `@Mod`, `@EventBusSubscriber` sans `modid`, mixins (refmap, `@Shadow` réobfusqués), `new X` d'une classe qui a une
  version serveur, et contenu exact attendu (`--projet`). Les lignes « partie présente / absente » disent quelles parties
  chaque jar charge. Objectif : `0 erreur(s)`.

Aucun test en jeu par Claude (le propriétaire teste lui-même). Essai de démarrage du serveur seulement si la
machine a au moins 8 Go libres et qu'aucun Minecraft ne tourne, sur une COPIE locale du serveur, avec
`server-ip=127.0.0.1`, puis arrêt propre (`stop`).

---

## 9. Pièges (à lire avant de commencer)

1. **`@Mod.EventBusSubscriber` sans `modid`** : inscrit une fois par mod du jar (écouteurs appelés N fois).
2. **Un `[[mods]]` sans classe `@Mod`** (ou l'inverse) : le jar entier refuse de charger.
3. **Anciens jars encore installés** : même paquet Java dans deux jars = plantage au démarrage, et modIds en double.
   Retirer les jars d'origine des livrables quand on installe le jar fusionné.
4. **`EntityDataAccessor` hors de la classe commune** : désynchronisation client/serveur.
5. **`new X(...)` d'une classe coupée** : version neutre (sans IA) côté serveur. Passer par le type / l'usine.
6. **Usine branchée trop tard** : les usines se branchent dans `Partie.init` (constructeur du `@Mod`), pas dans
   `FMLCommonSetupEvent` (les registres sont déjà remplis).
7. **`DistExecutor` / `Minecraft.getInstance()` dans le commun** : interdit (le compilateur le refuse : la classe client
   n'est pas visible). Passer par un `Crochet`.
8. **`load()` d'une entité de bloc** : appelé AUSSI côté client avec l'étiquette de synchronisation ; le commun doit lire
   ce que `getUpdateTag` envoie.
9. **Entités de bloc et `-complet`** : côté client, ce sont aussi des versions serveur (pas de monde à la création) :
   leur code serveur doit vérifier `isClientSide` comme avant.
10. **Cache de déobfuscation partagé** (`blank:<nom>:<version>`) : une version de dépendance déjà utilisée par un autre
    projet avec un autre contenu donne un jar faux. Suffixe propre au projet dans `libs/` (ex. `geckolib222-4.4.7-hzfusion.jar`).
11. **GeckoLib embarqué** : jamais de `jarJar` ; GeckoLib = `compileOnly fg.deobf("blank:geckolib222:4.4.7-hzfusion")`.
12. **Écrire des fichiers Java avec Python sous Windows** : `write_text(..., newline="\n")`, sinon fins de ligne CRLF.
13. **Mod qui lit une API d'un autre mod par réflexion côté client** (ex. heralisz_matchs lit `N07Api.VERSION` dans son
    setup commun) : si la classe est serveur, le client du joueur journalise un avertissement sans conséquence ; si la
    classe est lue pour l'affichage (ex. `BossInfo`, `api.client.CinematiqueN07`), elle doit rester commune / client.
14. **Noms de classes et signatures d'API publiques** (ex. `fr.heralisz.n07.api.*`, utilisée par heralisz_matchs) :
    ne jamais les renommer ni changer leurs signatures.
15. **`super.X()` dans une méthode `--deux`** : dans `XServeur`, `super` désigne la version COMMUNE de X, plus la classe
    vanilla. Si la version commune fait déjà une partie du travail (mouvement, compteur), il est fait deux fois côté
    serveur. Mettre la part commune dans une méthode à part (`vol()`, §5.4) et redéfinir celle-ci côté serveur.
    `scinder_classe.py` le signale. Sans danger seulement si la version commune ne fait rien de plus que le vanilla, ou si
    l'opération est idempotente (relire les mêmes valeurs dans `load`, ajouter à un ensemble dans `onLoad`).

---

## 10. Installer

- Serveur (Ptero) : le jar `-serveur` dans `mods/` (sans jamais laisser l'ancien jar du même mod à côté).
- Joueurs : le jar `-client`.
- Solo : le jar `-complet`.
- `python outils/construire.py` fabrique tout et range les jars dans `livraison/mods serveur` et `livraison/mods client`.
- Les configs (`serveur/config/`, `serveur/defaultconfigs/<modid>-server.toml`) restent valables : modIds inchangés.

## 11. L'onglet créatif commun et son panneau

Un seul onglet par jar fusionné, enregistré par le mod porteur. Chaque mod y ajoute un groupe (son contenu d'avant) :
```java
// commun, dans le constructeur @Mod du boss
HeralizBoss.ONGLET.ajouter("mammoth", "onglet.heraliz_boss.mammoth", () -> new ItemStack(WAR_HORN.get()), out -> {
   out.accept(WAR_HORN.get());
   ...
});
```
- `OngletGroupe` (commun, `fr.heraliz.<groupe>.onglet`) : groupes, construction de l'onglet, groupe de chaque objet.
- `PanneauOnglet` (client, `fr.heraliz.<groupe>.client.onglet`) : panneau vertical en verre à gauche de l'écran créatif,
  visible quand l'onglet est ouvert ; « Tous » puis une entrée par groupe (icône + nom en DM Sans) ; un clic filtre la
  liste ; icônes seules si l'écran est étroit ; un clic sur le panneau ne jette jamais l'objet tenu. Installé par la
  `PartieClient` du porteur :
  ```java
  PanneauOnglet.installer(HeralizBoss.TAB, HeralizBoss.ONGLET, "onglet.heraliz_boss.titre", "onglet.heraliz_boss.tous");
  ```
- Style des écrans de clan : kit `client/kit` (`Ui`, `SonsUi`, polices `assets/<porteur>/font`), copié par
  `outils/copier_kit_ui.py`.
- Noms des groupes : clés `onglet.<porteur>.<id>` dans `assets/<porteur>/lang/{fr_fr,en_us}.json`.
- Tactique : groupes Mêlée / Soins / Grenades / Camos / Décorations (stickers, pendentifs), même composant, déclarés
  dans le porteur pour un ordre fixe (§16, piège n°24).

---

## 12. Liste de contrôle par mod

- [ ] Pour un mod qu'on reçoit (jar + sources) : sources rangées par `fusion_copier.py`, puis découpées.
- [ ] `[[mods]]` + classe `@Mod` ; onglet remplacé par un groupe ; `Parties.serveur/client` à la fin du constructeur.
- [ ] Tous les `@EventBusSubscriber` ont `modid`.
- [ ] Aucun `DistExecutor` ni classe client dans `src/commun` (le compilateur le garantit).
- [ ] Chaque classe à logique coupée (`XServeur`), usines branchées dans `PartieServeur`, aucun `new X` d'une classe coupée.
- [ ] Commandes, gardes, gestionnaires, API serveur dans `src/serveur`.
- [ ] Mixins client : refmap commune, configs dans `src/client/resources`.
- [ ] `build livrer` : 3 tâches reobf + BUILD SUCCESSFUL.
- [ ] `verif_reobf`, `check_geckolib_api`, `verif_split` : propres sur les 3 jars.

---

## 13. Annexe : les boss (heraliz-boss 2.0.0)

| Mod | Commun (dans les 3 jars) | Client (`-client`, `-complet`) | Serveur (`-serveur`, `-complet`) |
|---|---|---|---|
| heraliz_boss | `@Mod`, pont, `OngletGroupe` | panneau de l'onglet, kit d'interface, polices | — |
| heralisz_mammoth | registres, config, `Mammoth`/`Draugr`/`GuardianSpear` (état, animations, cinématiques), `SpectralHand`, blocs, `HornAltarBlockEntity` (état), paquets `Vfx` | rendus GeckoLib, HUD, givre d'écran, VFX, musique, ambiance, cinématiques, mixins caméra/souris | `MammothServeur` (IA, 20 attaques, phases, mort, butin), `DraugrServeur`, `GuardianSpearServeur`, `HornAltarBlockEntityServeur` (rituel, Gardiens, combat, remise en état), `IceGates`, `WeakPoint`, `/mammouth`, `SpawnGuard` |
| heralisz_mere | `RuinMother`, `Spiderling`, `WebGlob` (état, animations, mouvement du globe, données de la cinématique de mort), `NestHallBE` (état), blocs (`use` -> `offerEgg` neutre), paquets `Vfx` | rendus, HUD, cocons, toiles, VFX, musique, ambiance, cinématiques, mixins | `RuinMotherServeur` (IA, 17 actions, éclosion, phases, mort scénarisée, butin), `SpiderlingServeur` (marionnette, scripts), `WebGlobServeur` (impacts), `NestHallBEServeur` (rituel, combat, remise en état), `/mere`, `SpawnGuard`, `WeakPoint` |
| heralisz_n07 | `HeraliszBoss` (état + HUD), `SujetN07`, `N07Shell`/`N07Boulder`/`N07Isotope`/`BossRemains` (mouvement, effets client), blocs et décors (données), `ShrineBlockEntity`/`PropBlockEntity`/`ContainmentVatBlockEntity` (état), `ScenePackets`, `api/BossInfo`, `api/DevControl`, `api/scene/Scene`, `SkipMode`, paquets `Vfx` | rendus, HUD, scènes (`ScenePlayer`), rituels, décors animés, VFX, musique, `api/client/CinematiqueN07`, mixins | `SujetN07Serveur` (IA de `HeraliszBoss` + attaques), projectiles et dépouille `…Serveur`, terminal/cuve/décors `…Serveur`, blocs à logique (rigoles, boue, téléporteur 935, décors) par `Usine`, `ArenaGates`, API `N07Api`/`Encounter`/`EncounterSpec`/`HallLayout`/`PlacedHall`/`ZombiesBalance`/`DamageRemap`/`N07Events`/`SceneHandle`/`SceneResult`, `encounter/*`, `hall/*`, `SceneRunner`, `GiantScenes`, `SkipVote`, `CinematicGuard`, commandes `/n07`, `/boss`, `ServerTest` |
| heralisz_plague | `HeraliszBoss` (état + HUD), `PlagueBearer`, `PlagueGlob` (vol), `PlagueSwarm` (données), `StationHallBE` (état), blocs, paquets `vfx`/`fvfx` | rendus, HUD, VFX, particules, musique, ambiance, cinématiques, mixins | `PlagueBearerServeur` (IA de `HeraliszBoss` + 17 actions, gaz, infectés, mort, butin), `PlagueGlobServeur`, `PlagueSwarmServeur` (traque, piqûres), `StationHallBEServeur`, `/peste`, `WeakPoint`, `Ground`, `Lob` |
| heralisz_sylvorn | `Sylvorn` (état, calculs de mort lus par le rendu), `SylvornLegs`, `ThrownLog`/`SeedPod`/`ThornSpear`/`SapNode` (mouvement, cycle de vie), `TreeHallBE` (état), blocs (`use` -> `offerSeed` neutre), `Ground`, paquets `vfx`/`fvfx` | rendus, branches, racines, HUD, VFX, musique, ambiance, cinématiques, mixins | `SylvornServeur` (IA, 20 attaques, phases, mort, butin), `SylvornLegsServeur`, projectiles `…Serveur` (impacts), `SapNodeServeur`, `TreeHallBEServeur`, `/sylvorn`, `WeakPoint` |

---

## 14. Travailler à plusieurs : les chantiers

Un projet fusionné ne compile que si TOUS ses mods compilent : deux agents qui découpent deux mods en même temps dans le
même projet se bloqueraient. Chaque agent travaille donc dans un « chantier » :
```
python outils/fusion_chantier.py creer mods/heraliz-boss heralisz_mere        # -> mods/_chantier/mere
PYTHONIOENCODING=utf-8 python outils/lancer.py build mods/_chantier/mere compileJava compileClientJava compileServeurJava
python outils/fusion_chantier.py rapatrier mods/heraliz-boss heralisz_mere    # quand le chantier est propre
```
- Le chantier = le code partagé (`fr/heraliz/*`) + ce seul mod, son bloc `[[mods]]` et celui du porteur. On n'y modifie
  pas le code partagé (si besoin, le dire au pilote) ; `rapatrier` le signale.
- `rapatrier` remet le code du mod dans le projet ; les anciennes copies vont dans `_retires/fusion_chantier/`.
- Ne pas lancer la tâche `livrer` depuis un chantier (le chemin `../../dist/fusion` n'y est pas le bon).
- `lancer.py` accepte n'importe quel dossier de projet, et limite toujours à 2 Gradle.
- `verif_reobf.py` relit tous les jars de référence avec javap : compter ~5 minutes.

---

## 15. Retours du groupe ClansEtBases (heraliz-clansetbases 2.0.0)

Économie, clans, bases et tags (`mods/heraliz-clansetbases`, porteur `heraliz_clansetbases`). Pièges nouveaux :

16. **Ressource `assets/` lue par `getResourceAsStream` côté serveur** : sans elle, le serveur prend une liste de secours
    différente du client. Cas vus : `assets/heralisz_tags/tags.json` (il fixe la liste des objets `tag_*` enregistrés :
    sans lui, registres différents des deux côtés), `assets/heralisz_clans/banniere/formes.json` (bannières), et
    `banniere/modeles.json`, lu côté serveur par un AUTRE mod (heralisz_matchs, banc ClashWar). Les mettre dans
    `src/commun/resources` au MÊME chemin ; `verif_split --projet` accepte ces fichiers dans `-serveur` (info « assets/
    communs »). Chercher `getResourceAsStream("/assets` dans le code commun et serveur, et dans les jars des dépendants.
17. **API statique appelée des deux côtés** (`EconomieApi.format` sur le client, comptes sur le serveur ; `ClansApi`
    chargée par du code commun d'autres mods) : la classe reste commune, mêmes noms et signatures ; ses méthodes serveur
    passent par une interface package-private `Moteur` et un champ `moteur`, branchés par `XApiServeur.installer()`
    (même paquet, `src/serveur`, corps d'origine tels quels). Sans partie serveur, rendre EXACTEMENT ce que l'original
    rendait hors serveur (vide / faux / 0 pour les clans ; même IllegalStateException pour l'économie). Une API que seul
    le serveur appelle (`BasesApi`, `PillageHooks`) passe entière dans `src/serveur`, comme `N07Api`.
18. **Beaucoup de paquets** : un crochet par sens et par canal, `RECEPTION_CLIENT` (`Crochet<Object>`) et
    `RECEPTION_SERVEUR` (`Crochet<Recu>`, `record Recu(ServerPlayer joueur, Object paquet)`), aiguillage `instanceof` dans
    `PartieClient` / `PartieServeur` vers les traitements d'origine. Numéros, ordre et version du canal inchangés ;
    `consumerMainThread` garde le fil.
19. **Constante d'une classe serveur lue par une classe commune** (`AvionEntity` ← `Ravitaillement.VITESSE`) : le
    compilateur refuse. La déclarer dans la classe commune (même valeur) et la reprendre côté serveur
    (`VITESSE = AvionEntity.VITESSE`).
20. **Ordre des écouteurs** : quand un module branché plus loin dans le constructeur ajoutait ses écouteurs (bases :
    `PillageModule.init`), `PartieServeur` les inscrit dans l'ordre d'origine (store, règles, commandes… puis pillage,
    blindage, C4). Les `@Mod.EventBusSubscriber` de `src/serveur` sont injectés après le constructeur, comme avant.
21. **Dépendants** : `python outils/verif_dependants.py <-client> <-serveur> <-complet> --paquets ... --mods ...` vérifie que
    les jars installés trouvent chaque classe / méthode du bon côté. Les seuls manques côté client venaient de bancs
    d'essai de développement (crac `ClanBench`, ping `BancLiens` : `ClanStore`, `ClanService`, `LienJoueur`), lancés sur
    le serveur intégré : ils demandent le jar `-complet`. Même chose pour un banc client qui appelle une classe serveur
    (`ClansHarness` → `TestPerms`) : appel par son nom (réflexion).
22. **Onglet** : un mod sans onglet propre (clans, tags) reçoit quand même son groupe ; un mod sans aucun objet
    (économie) n'en a pas. L'ancien onglet `heralisz_bases:blindage` disparaît (contenu repris dans le groupe Bases).

Répartition : commun = registres, configs, modèles et vues (`Banniere`, `Clan`, `ClanSnapshot`, `Base`, `Plan`...),
paquets, catalogue des tags, blocs/objets/entités (`DrapeauBlock`, `CaisseRavitaillementBlock`, `AvionEntity`,
`CaisseParachuteEntity` réduits à l'état et à l'affichage), API (`EconomieApi`, `ClansApi`, `ClanInfo`, événements) ;
serveur = comptes et Vault, stores et sauvegardes, règles et commandes des clans, territoires, largages
(`CaisseParachuteEntityServeur`, `CaisseRavitaillementBlockServeur`), bunkers, pillage, blindage, tags posés ;
client = écrans (kit), HUD, bannières, drapeaux, sprays, touches, bancs d'essai.

---

## 16. Retours du groupe Tactique (heraliz-tactique 2.0.0)

Moteur `heralisz_tactical` (fork GPL-3.0 de LR Tactical), contenus `heralisz_melee` / `heralisz_heal` /
`heralisz_grenade`, camos `heralisz_camo` (`mods/heraliz-tactique`, porteur `heraliz_tactique`). Pièges nouveaux :

23. **Mod de données sans code** (lowcodefml : melee, heal, grenade) : dans un jar javafml, chaque `[[mods]]` doit avoir
    sa classe `@Mod` -> classe vide (`fr.heralisz.melee.HeraliszMelee`) ; `pack/data` -> `src/serveur/resources`,
    `pack/assets` -> `src/client/resources` (copie à la main : `fusion_copier` attend `src/main`).
24. **Ordre des groupes de l'onglet** : FML construit les mods EN PARALLÈLE (`ModList.futureVisitor`), l'ordre des
    `ONGLET.ajouter` faits dans chaque `@Mod` change d'un lancement à l'autre. Pour un ordre fixe : déclarer les groupes
    dans un bloc `static` du porteur, le contenu restant fourni par chaque mod (`iconeOnglet()` / `remplirOnglet(out)`,
    appelés par lambdas). Appliqué aux boss en 2.0.1 (Ymirak, Mère des Ruines, Sujet N-07, Décors du spawn, Peste, Sylvorn).
25. **Mixins des deux côtés** (enclume, bouclier, objet consommé : `mixin.common`) : config dans `src/commun/resources`
    avec SON paquet (un sous-paquet par config), refmap à part (`add sourceSets.main, "<jar>-commun.refmap.json"`,
    `annotationProcessor` mixin + MixinExtras pour `main`), listée dans le `MixinConfigs` des trois jars ; `-serveur`
    n'exclut que la refmap client. `verif_split` l'accepte (config sans section `client`, info « mixins communs »).
26. **Dépendances embarquées et facultatives** : LuaJ, SimpleBedrockModel et MixinExtras viennent de TaCZ (Jar-in-Jar) :
    `verif_split` lit maintenant les `META-INF/jarjar/*.jar` des dépendances. Les mods facultatifs d'origine
    (playeranimator, cloth_config, curios, backpacked) sont déclarés `mandatory=false` dans le `mods.toml` pour être
    vérifiés. `@Shadow` d'un mixin qui vise un autre mod (playeranimator) garde son nom : plus d'erreur SRG pour lui.
27. **Usine d'entité et `EntityType` statique** (`TYPE = Builder.of(...).build(...)` en champ de la classe) : lire
    `X.USINE` dans `PartieServeur.init` initialiserait la classe et construirait l'`EntityType` pendant le chargement des
    mods. Ranger les usines dans la classe de registre (`ModEntities.GRENADE_USINE`).
28. **Classe mère abstraite à plusieurs sous-classes concrètes** (lancers : `ThrowableItemEntity` -> grenade -> C4,
    fumigène, flash, molotov) : §5.5 ne suffit pas. Méthodes neutres dans le commun (`impact`, `exploser`,
    `reactionEnChaine`, `etourdir`, `liberer`, `verifierSupport`…) appelées là où l'original testait `!isClientSide` ;
    chaque `XServeur` les redéfinit en appelant des fonctions statiques serveur partagées (`LancersServeur.impact`,
    `GrenadeEntityServeur.explosion` pour la grenade ET le C4). Aucun `super.X()` côté serveur (piège n°15 évité). Un
    `return` au milieu de l'original (explosion annulée : grenade gardée) devient une méthode neutre qui rend un booléen.
29. **Entité créée par un constructeur « lanceur »** (`new GrenadeEntity(lanceur, level, vie)`) : la remplacer par
    `TYPE.create(level)` + `lancePar(lanceur, vie)` (mêmes trois affectations) pour passer par l'usine.
30. **API à moitié client** (`HeraliszTacticalAPI.get*Display`, `@OnlyIn(CLIENT)`, à côté des index lus par réflexion par
    heralisz_matchs) : les méthodes client passent dans une classe client (`api.client.HeraliszTacticalClientAPI`, mêmes
    noms) ; l'API commune garde ses méthodes. `verif_dependants` ne voit pas la réflexion (`Class.forName("fr.heralisz…")`,
    `getMethod("…")`) : chercher ces chaînes dans `src/*/src` et vérifier les membres dans les trois jars.
31. **Règle appliquée des deux côtés pour la prédiction du client** (`CriticalHitEvent` : `Player.attack` tourne aussi sur
    le client pour les particules de coup critique ; effets qui « tiquent » des deux côtés) : l'écouteur reste commun.
    Rendus d'objets (`initializeClient`) : `Crochet<Consumer<IClientItemExtensions>>` branché par `PartieClient` (Forge
    l'appelle à la création de l'objet, après les constructeurs `@Mod` : à temps).

Répartition : commun = registres, configs, onglet, objets réduits (utilisation, prédiction, infobulles), lancers réduits
(vol, rebonds, collage suivi, état synchronisé, sauvegarde de « Item » lue par CRac et Bases), capacités (recharges,
combat prédit), index et cache réseau, paquets, mixins communs, coups critiques (dos) ; serveur = coups de mêlée,
soins / effets / consommation, lancers (`onThrow`), explosions (`CustomExplosion`), C4 (collage, décrochage,
détonateur), flash, molotov / gaz et nuages au sol, brûlure, bouclier, chargement des index et envoi aux joueurs, camos
et décorations appliqués, compteur de kills, reprise des objets retirés, `data/` ; client = rendus 1re / 3e personne,
animations (machines à états Lua, player-animator), HUD, touches, sons, particules, shaders et effets des camos,
écrans de modification, mixins client, `assets/` (dont les packs de contenu).
