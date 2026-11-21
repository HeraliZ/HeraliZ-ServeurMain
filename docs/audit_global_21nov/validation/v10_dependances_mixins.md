# Validation de l'audit du 21/11 — zone v10 : liens entre les mods et mixins au démarrage

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider »), sur ce qui
relie les mods entre eux après les changements du jour (dépendances déclarées, réseau, appels par leur nom, clés de
données partagées), et diagnostic des mixins au démarrage du serveur. Compte rendu d'origine : `../a10_dependances.md`.
Modules relus : tous ceux qui sont livrés (tous sauf `events`, `banc`, `capture`, `_gabarit`), branche
`carte-ile-reglages` (production `main` = `6638bb00a`, puis les corrections des autres relectures au fil de la soirée).

**Rien n'a été testé en jeu.** Aucun code de module n'a été changé ici : seulement l'outil de démarrage d'essai
(`outils/demarrage_essai.py`) et ce compte rendu. Contrôles faits : `outils/verif_reflexion.py` et `outils/verif_mixins.py`
(avec le jar de Mohist 916 de la Release « base-serveur », empreinte vérifiée), `outils/verif_synchro.py`, relecture des
`mods.toml`, des canaux et des paquets, et démarrages d'essai sur GitHub (normal et « mixins stricts »).

## En bref (pour les propriétaires)

- **Les liens entre les mods sont bons** après les changements du jour : aucune dépendance manquante, aucune boucle, réseau
  cohérent. Quatre canaux ont changé de numéro de version (skins d'armes, liste Tab, tags de clan, voix) : un jeu de
  joueur plus ancien est refusé à la connexion, le launcher met les jars à jour, comme d'habitude.
- **Le démarrage « mixins stricts » va enfin au bout.** Il s'arrêtait chaque fois sur un mod d'un autre auteur parce que le
  nom d'une option était mal écrit. Corrigé dans l'outil d'essai (jamais sur le serveur en ligne) : en un seul démarrage,
  les 111 mixins de nos mods côté serveur (plus les 4 que l'anticheat ajoute) ont tous été essayés, chaque classe visée
  chargée exprès, même celles que le jeu ne charge que plus tard.
- **Résultat : une seule injection de nos mods ne trouve pas sa cible, et c'est voulu** (une 2e façon d'écrire un
  allègement du noyau, prévue pour une variante de Bukkit ; celle que Mohist utilise marche). Rien de cassé en ligne.
- Une petite chose à ranger, sans effet aujourd'hui : plusieurs mods annoncent une version minimale du noyau trop basse.
- Pas vérifié : les mixins côté joueur (il faut un vrai jeu avec écran), et rien en jeu.

---

## Problèmes trouvés

### 1. Outil d'essai : le démarrage « mixins stricts » ne pouvait pas aller au bout — important pour le diagnostic (aucun effet en jeu ; corrigé, outil seulement)

- **Cause** : l'essai du 21/11 (commit `84bad746`, démarrage d'essai n° 22) passait `-Dmixin.ignoreRequired=true`. Ce n'est
  pas une option de Mixin 0.8.5 (celui que Mohist 916 charge) : son option s'appelle **`mixin.env.ignoreRequired`**
  (`MixinEnvironment$Option.IGNORE_REQUIRED`, rangée sous `ENVIRONMENT` = « env » ; `MixinConfig.onLoad` :
  `required = requiredValue && !IGNORE_REQUIRED`, lu dans le jar de Mixin du jar de Mohist). L'option écrite ne faisait donc
  rien : la première injection ratée d'une configuration « required » arrêtait le serveur (essais n° 20, 22 et 24, chaque
  fois sur ModernFix). En plus, un démarrage ne charge qu'une partie des classes visées : les injections des classes
  chargées seulement en jeu n'étaient jamais essayées.
- **Fait** (`outils/demarrage_essai.py`, mode `--mixins-stricts` seulement, serveur d'essai seulement) :
  - l. 89-90 `OPTIONS_STRICTES` : `-Dmixin.debug.countInjections=true` (toute injection qui trouve moins que prévu est une
    erreur, même « require 0 »), `-Dmixin.env.ignoreRequired=true` (chaque erreur est notée et le démarrage continue),
    `-Dmodernfix.auditMixinsAtStart=true` (ModernFix 5.27.85 appelle `MixinEnvironment.audit()` pendant la préparation
    des mods : `ModernFixForge.commonSetup` → `enqueueWork(ModernFix::runAuditIfRequested)` ; chaque classe visée par un
    mixin et pas encore chargée l'est, donc chaque injection est essayée) ;
  - l. 93 `MODERNFIX_COUPES` : `perf.reduce_blockstate_cache_rebuilds` (déjà coupé) et **`bugfix.forge_vehicle_packets`**,
    ajoutées à la copie d'essai de `config/modernfix-mixins.properties` (l. 166-172) ; jamais `serveur/config` ;
  - l. 256-… `configs_mixins()` et `rapport_strict()` : chaque injection ratée (les nôtres d'abord : jars qui ne viennent
    pas de `serveur/mods-tiers`), les classes visées pas transformées (« Could not force-load »), les cibles absentes, et
    ceux de nos mixins jamais essayés (lignes « Mixing X from config into cible » de `logs/debug.log`, niveau DEBUG) ;
  - aide en tête du script : options, liste des injections d'autres auteurs coupées dans l'essai, résultat attendu.
- Rien ne change pour le démarrage d'essai normal ni pour la mise en ligne : démarrages normaux n° 27 et n° 29 réussis avec
  ce script.

### 2. Version minimale du noyau trop basse dans 8 mods — mineur (sans effet aujourd'hui)

Depuis le 19/11 (noyau **1.1.5**, commit `69e054f61` puis `5c0755672`), `fr.heraliz.noyau.api.Troncons` (tronçons prêts) et
`fr.heraliz.noyau.api.EcrituresFond` (écritures en fond) sont appelés directement par d'autres mods, dont plusieurs depuis
aujourd'hui. Leur `mods.toml` accepte pourtant un noyau 1.0.x :

| Mod (jar) | Appels | `mods.toml` à changer |
|---|---|---|
| heraliz_tactique (armes) | `VitresServeur` (Troncons), `CollectionCamos` (voie « camos », 21/11) | `modules/armes/src/commun/resources/META-INF/mods.toml` l. 34 `[1.0.6,)` |
| heralizcasino (boutique) | `casino/data/EcrituresFond` (voie « casino ») | `modules/boutique/…/mods.toml` l. 92 `[1.0.0,)` |
| heralisz_clans (clans) | `server/EcrituresFond`, `territory/Territoires` | `modules/clans/…/mods.toml` l. 91 `[1.0.19,)` |
| heraliz_clashwar | `moteur/arenes/ArenesService` | `modules/clashwar/…/mods.toml` l. 45 `[1.0.0,)` |
| heraliz_evenements | `serveur/EcrituresFond` | `modules/evenements/…/mods.toml` l. 29 `[1.1.0,)` |
| heraliz_loot | `PointsT7`, `ConteneursEvenement`, `RotationLoot`, `EcrituresFond` | `modules/loot/…/mods.toml` l. 33 `[1.0.19,)` |
| jar du monde (usure, extraction, ping) | frappes, hélicoptère, ping, décors, train, coffres personnels | `modules/monde/…/mods.toml` l. 175 `[1.0.0,)`, l. 295 `[1.0.16,)` ; ping l. 79-80 `mandatory=false` alors qu'il appelle `Troncons` sans protection (`ping/server/PingService.java` l. 220, `Vue.java` l. 31, `Contexte.java` l. 35) |
| heraliz_quetes (pnj) | `serveur/EcrituresFond` (voie « quetes ») | `modules/pnj/…/mods.toml` l. 30 `[1.0.19,)` |

- **Effet** : aucun tant que tous les jars partent ensemble (mise en ligne par GitHub côté serveur, launcher côté joueur :
  ces dépendances sont « BOTH », vérifiées des deux côtés). Un vieux jar du noyau laissé seul donnerait un plantage au
  premier appel au lieu d'un message clair au démarrage (les appels sont tous côté serveur). CRac est déjà juste
  (`[1.1.5,)`, `modules/bases/…/mods.toml` l. 40).
- **Correction proposée** (même famille que a4 n° 5) : `versionRange="[1.1.5,)"` sur ces lignes (et `mandatory=true` pour
  ping l. 79), avec la prochaine version de chaque module ; pas de mise en ligne exprès pour ça.

### 3. L'injection « voulue » qui rate en mode strict — mineur, facultatif

- `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/allege/ReglagesGenerationMixin.java` l. 32-36 : sa 2e redirection
  ne trouve jamais son appel sur Mohist 916 (voir ci-dessous) ; c'est voulu (« chacune facultative », l. 19-20).
- **Proposé (facultatif, aucun effet en ligne)** : ajouter `expect = 0` à ce `@Redirect` (l. 32) pour qu'un diagnostic
  strict n'affiche plus rien d'attendu. Sinon, rien à faire : le résultat attendu est écrit dans l'aide de
  `outils/demarrage_essai.py`.

---

## Injections de nos mixins qui ratent au démarrage

Démarrages d'essai « mixins stricts » (GitHub, « Run workflow » avec la case `mixins_stricts`, branche
`carte-ile-reglages`) :

| Essai | Commit | Résultat |
|---|---|---|
| n° 20, 22, 24 (avant cette relecture) | `f324509e`, `84bad746`, `f545589a` | arrêtés net sur ModernFix (option mal nommée, voir n° 1) |
| **n° 28** | `f02b5859` | « Done » atteint ; liste complète (ci-dessous) |
| **n° 30** | `7fc16a9a` | même résultat avec les versions corrigées par les autres relectures (noyau 1.1.12, CRac 1.38.11-beta, monde 1.3.12, loot 0.20.9, Spartan 2.15.6) ; l'audit de ModernFix a chargé 80 classes visées |

**Nos mixins côté serveur** : 19 configurations, 111 mixins listés pour un serveur, **111 essayés**, plus les 4 que le
plugin de Spartan ajoute (`TaczCadenceMixin`, `TaczReculMixin`, `TaczSoundMixin`, `TaczTirMixin`) ; 0 jamais essayé ;
0 cible absente dans nos configs ; 0 classe chargée trop tôt pour nos mixins. Par configuration (essayés / listés) :
backpacked.common 10/10, backpacked.forge 1/1, crac 3/3, crac_serveur 2/2, heralisz_tactical 4/4, heraliz_tactique
11/11, heralisz_utils_chemins 1/1, heralisz_utils_cibles 1/1, heralisz_utils_serveur 7/7, heraliz_armory 5/5,
heraliz_armory_serveur 3/3, heraliz_emote-commun 1/1, heraliz_loot_serveur 3/3, heraliz_noyau_allege 42/42,
heraliz_noyau_profil 3/3, heraliz_noyau_serveur 4/4, heraliz_noyau_serveur_forge 6/6, reanimation 1/1, spartan 3/3 (+4).

**Une seule injection ratée, et elle est voulue :**

| Config, mixin, injection | Cible | Pourquoi elle rate | Effet en ligne |
|---|---|---|---|
| `heraliz_noyau_allege.mixins.json` : `ReglagesGenerationMixin`, `@Redirect heraliz$encoderUneFoisDimensions` (l. 32-36) | `PrimaryLevelData.setTagData`, appel `WorldGenSettings.encode(DynamicOps, WorldOptions, WorldDimensions)` | Mohist 916 garde l'appel de Minecraft, `WorldGenSettings.encode(DynamicOps, WorldOptions, RegistryAccess)` : c'est la 1re redirection du même mixin (`heraliz$encoderUneFois`, l. 25-30) qui le prend. La 2e forme existe pour une variante de Bukkit qui passerait les dimensions (« Deux formes… chacune facultative », l. 19-20 ; config `defaultRequire: 0`). Mixin vérifie les injections dans l'ordre : la 1re est passée, sinon c'est elle qui aurait été signalée. | **Aucun** : en ligne (sans le mode strict), la 2e redirection ne fait rien sans rien dire et la 1re garde les réglages de génération en mémoire (`[perf] registres_en_memoire`, level.dat). Déjà notée « attendue » à la section 44 de la fiche des essais. |

Signalé en plus par l'audit de ModernFix : « Could not force-load `net.minecraft.world.level.storage.PrimaryLevelData` …
already classloaded ». **C'est la suite de la ligne ci-dessus, pas un 2e problème** : en mode strict, le mixin raté est
retiré tout entier ; sa configuration n'a alors plus d'autre mixin « terminé » sur cette classe (`MixinConfig.postApply`
n'est appelé que pour les mixins qui vont au bout) ; l'audit redemande la classe, déjà chargée. En ligne, le mixin
s'applique (1re redirection), la classe est traitée.

**Mods des autres auteurs** : 0 injection ratée avec les deux options de ModernFix coupées dans l'essai. Ces deux-là
ratent toujours sur Mohist, y compris en ligne où elles ratent sans rien dire (le serveur démarre : elles ne sont pas
obligatoires) : `perf.reduce_blockstate_cache_rebuilds` (optimisation qui ne s'applique pas) et
`bugfix.forge_vehicle_packets` (redirection de `movePlayerUsingPositionRider` dans
`ServerGamePacketListenerImpl.handleMoveVehicle`, méthode réécrite par Mohist pour Bukkit : la correction de ModernFix ne
s'applique pas, le serveur se comporte comme Mohist sans ModernFix). Rien à changer en ligne. Cibles absentes, déjà vues
au démarrage normal et sans effet : `geckolib.mixins.json:ApplyEntityFunctionsMixin` (classe du joueur) et
`mixins.ywzj_vehicle.json:create.ContraptionColliderMixin` (mod Create absent).

---

## Vérifié sans problème

- **Appels par leur nom** (`outils/verif_reflexion.py --forge <Mohist 916>`, sur la branche fusionnée) : 808 appels,
  321 chaînes qui nomment une de nos classes, 3 563 liens directs vers un autre module ; **613 vérifiés exacts,
  0 erreur** ; 33 appels protégés vers des mods facultatifs absents ; 131 avertissements = non vérifiables ici (noms SRG de
  Minecraft qu'aucun jar ne cite, classe connue seulement en jeu). Appels ajoutés depuis le 20/11 au soir : 6 lignes
  (`clans/…/server/PontVanish.java` l. 29-31, `voix/…/server/HeralizVoiceCompat.java` l. 124-126) vers
  `fr.heraliz.grades.serveur.GradesApi.cache(UUID)` et `voitLesCaches(ServerPlayer)` : publiques, statiques, rendent un
  `boolean`, côté serveur des deux côtés ; une erreur de l'autre mod donne « pas caché » (jamais de plantage).
- **Mixins** (`outils/verif_mixins.py --forge`) : 36 configurations, 197 mixins, 420 injections ou membres ; 209 cibles
  d'autres auteurs exactes ; 1 erreur connue (`heraliz_interface_net.mixins.json` hors du manifeste : décision du
  propriétaire, pas un problème) ; 14 avertissements connus (11 configs `defaultRequire 0`, 2 `@Overwrite` de TaCZ côté
  joueur, `JoueursProchesMixin` en `remap = false` : appliqué et vérifié par l'essai strict).
- **`mods.toml`** : les 3 commits qui les changent depuis le 20/11 au soir (`264894b1a` version de heraliz_grades,
  `a47ec9766` CRac → noyau des deux côtés `[1.1.5,)`, `f324509e4` noyau des deux côtés pour clans, extraction, armory, loot ;
  Événements → noyau ; Grades → Économie et Clans côté serveur ; sous-mods du monde `[2.26,)`), puis tous les `mods.toml`
  relus d'un bloc : 203 dépendances de nos 39 mods et des 28 mods des autres auteurs (jars imbriqués compris). Chaque
  dépendance obligatoire présente du bon côté, versions dans les bornes (TaCZ `1.1.8-hotfix` ≥ `1.1.8` pour Forge ;
  Emotecraft dans le jar des emotes), **aucune boucle d'ordre** (serveur et joueur). Les dépendances `side="SERVER"` de
  heraliz_grades ne sont pas vérifiées chez le joueur ; aucun mod ne dépend du jar `interface` (joueur seulement) :
  un jeu de joueur se charge toujours.
- **Réseau** : 40 canaux (39 `SimpleChannel` + celui de Framework pour les sacs), 183 enregistrements de paquets, tous dans
  `src/commun`, tous avec un sens (15 le passent par une variable : `crac/build/net/BuildPackets.java`,
  `Spartan/Networks/ModMessages.java`). Lectures de taille dans les 194 fichiers communs qui décodent des paquets : aucune
  sans plafond (`readVarIntArray()` de `CMeleeAttackRequest` et `readLongArray()` du train sont bornés par la taille du
  paquet, `readNbt` à 2 Mo). Canaux changés
  depuis le 20/11 au soir, numéro de version monté à chaque fois, nouveaux paquets en fin de liste :
  `heralisz_camo:main` 3 → 4 (`DessinsPacket` 4, `DessinsRefairePacket` 5, `DessinsBilanPacket` 6),
  `heraliz_grades:main` 7 → 8 (`DeltaTab` 8, `DemandeTab` 9), `heralisz_clans:main` 14 → 15 (`TagsDeltaS2C`,
  `TagsDemandeC2S`), `caracrea:main` 6 → 7 (`VoiceRosterDelta` 12, `VoiceRosterRequest` 13). Aucun autre canal n'a changé
  le contenu de ses paquets. Nouveaux paquets joueur → serveur : vides ou 4 nombres. Nouveaux décodages serveur → joueur
  bornés (`Math.min` à 500 / 1 024 / 4 096 lignes, `readUtf(64)`, `readByteArray(900 000)`). `DessinsPacket` vérifié chez
  le joueur (`camo/client/DessinsRecus.java` l. 64-100 : 1 à 32 morceaux, numéro valide, total ≤ 32 × 900 000 et égal à la
  somme reçue ; `DessinsCopie.deballer` l. 67-90 : 4 096 fichiers et 32 Mo au plus, taille contrôlée avant chaque lecture) ;
  morceau de 900 000 octets sous la limite de 1 Mio d'un paquet du serveur. Les trois copies de `Synchro` sont identiques
  et l'essai hors du jeu passe (`outils/verif_synchro.py` : 16 000 ticks, avec 0, 2 et 20 % de pertes, 0 erreur).
- **Clés de données partagées** (11 relues) : même nom et même type partout — `heralisz:combat_jusqua` (long, écrit par le
  noyau), `heralisz:en_match` (booléen, écrit par ClashWar via `Matchs.CLE_EN_MATCH`), `HeraliszMatch` (étiquette des objets
  prêtés, `Matchs.NBT_PRET`), `HeralizClashWarDepot`, `HzUsureEtat` (entier), `HeraliszCamoProprio` (UUID),
  `HeralizCouleurLibre` (booléen), `HeralizRarete` (texte), `spartan_uid` (long), `heraliz_cinematique_finie` / `_vue`
  (booléens).
- **Écritures en fond du noyau** : 10 voies aux noms tous différents (casino, clans, coffres, crac, donnees, evenements,
  level-dat, loot, quetes, camos) ; chaque module n'attend que la sienne ; tout est attendu à l'arrêt. **Tronçons prêts**
  (`Troncons`) : 52 appels hors du noyau, tous dans du code serveur (un seul dans `src/commun` : `EchelleBlock.getTicker`,
  serveur seulement) et sur le fil du serveur (paquets traités par `consumerMainThread`).
- **Démarrages d'essai normaux** avec l'outil changé : n° 27 (`f02b5859`) et n° 29 (`7fc16a9a`) réussis. « Contrôle de
  branche » vert sur `7fc16a9a` (images, fabrication, contrôles), qui contient les deux changements de l'outil.

## Non vérifié (et pourquoi)

- **Mixins côté joueur** : 79 mixins des configurations « client » (armes, interface, carte, noyau, sacs, voix… ; plus les
  3 des lettres nettes, jamais chargés par décision du propriétaire) et les 36
  mixins communs tels qu'ils s'appliquent sur le Minecraft du joueur (Forge sans les changements de Mohist) : un serveur ne
  les charge pas. Pour les vérifier : sur un PC, lancer le jeu d'essai (`outils/client_essai.py`) avec la variable
  d'environnement `JDK_JAVA_OPTIONS` = `-Dmixin.debug.countInjections=true -Dmixin.env.ignoreRequired=true
  -Dmodernfix.auditMixinsAtStart=true` (ModernFix est aussi dans le jeu des joueurs), puis chercher « Mixin apply
  failed » et « Could not force-load » dans `logs/latest.log`. Impossible ici (pas d'écran ni de jeu).
- **Le comportement des injections** : l'essai strict dit que chaque injection trouve sa place, pas que le code fait ce
  qu'il faut en jeu (essais en jeu du propriétaire).
- **Noms SRG lus par réflexion** que seul Minecraft confirmerait (avertissements de `verif_reflexion.py`) : les dépôts de
  Mojang et de Forge ne sont pas joignables d'ici.
- **Journal complet des essais** (artefact `demarrage-journal`) : le stockage des artefacts de GitHub est refusé depuis
  cette machine ; seul le journal de la tâche a été lu (il contient la liste complète du mode strict).
- Rien n'a été testé en jeu.
