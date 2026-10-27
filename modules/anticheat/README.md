# Spartan AntiCheat — Forge 1.20.1

Anti-cheat pour serveurs Minecraft **Forge 1.20.1** (`modId = spartan`).
Anticheat de HeraliZ.

> **Philosophie :** la vraie protection est **côté serveur**. Un tricheur peut
> cacher son mod, il ne peut pas cacher ce qu'il *fait* : voler, aller trop vite,
> frapper trop loin ou hors de son champ de vision, cliquer comme une machine,
> casser 10 blocs d'un coup. Le mod côté joueur n'est qu'une barrière en plus.

## Les murailles, de l'extérieur vers l'intérieur

| # | Muraille | Ce qu'elle arrête |
|---|---|---|
| 1 | **Launcher officiel** : dossier `mods` vérifié + jeton signé ([docs/LAUNCHER.md](docs/LAUNCHER.md)) | Mods de triche ajoutés, lancement hors du launcher, mod ajouté après le lancement |
| 2 | **Build identique** au serveur, à la connexion | Mod Spartan modifié ou dépassé |
| 3 | **Défi tournant** à intervalle aléatoire : chaque défi est différent | Mod Spartan modifié, bloqué ou désactivé en cours de partie |
| 4 | **Contrôle d'intégrité silencieux** (à la connexion puis au hasard) : ce que le jeu a VRAIMENT chargé, + handshake obligatoire | JAR de cheat (même renommé), mods chargés en douce, Mixins qui modifient le rendu / le mouvement, agents Java, injection en cours de partie, code injecté, DLL, logiciels de triche |
| 5 | **Détections serveur** : Fly, Speed, Reach, KillAura, AutoClicker, Nuker, FastBreak, effets illégaux | Toute triche qui se *voit* dans les actions, injectée ou non |
| 6 | **Correction instantanée** : renvoi au sol, coup annulé, bloc non cassé, effet retiré | Le tricheur ne profite pas de sa triche, même une seconde |
| 7 | **Ressources imposées** : packs de texture et shaders custom bloqués | Textures/shaders qui donnent un avantage (voir à travers les blocs, etc.) |
| 8 | **JVM durcie** par le launcher (`-XX:+DisableAttachMechanism`) | Injecteurs qui s'attachent au jeu lancé |
| 9 | **Anti-ESP serveur** : un joueur caché derrière un mur n'est plus envoyé au client | ESP, wallhack, radar, pseudos à travers les murs — même avec un client injecté indétectable |
| 10 | **Bans** compte + IP + PC, comptes secondaires repérés | Le retour du banni |

Chaque muraille franchie en laisse d'autres derrière ; et quand une détection
tombe, l'avantage est annulé sur-le-champ.

**Jeu identique au jeu officiel, sinon refusé (2.9.0, 27/10, `integrity.strictOfficial = true`)** : règle d'accès,
comme le jeton du launcher. Seuls les écarts CERTAINS refusent la connexion (`integrity/ConformiteStricte`) : jar en
plus, modifié ou manquant (comparés à `officiel.json`, le modpack d'avant la dernière mise en ligne restant accepté),
config Mixin inconnue ou déguisée, mod chargé par une autre voie que le launcher, agent Java / débogueur /
`-Xbootclasspath` / options `--fml.*` inconnues, jeu sans `DisableAttachMechanism`, mod interdit, classe d'un cheat
connu, Spartan modifié en mémoire, Spartan d'une autre version, défi faux (ou 3 sans réponse), infos du jeu jamais
envoyées, aucun contrôle d'intégrité réussi depuis la connexion, dossier mods ni vide ni officiel. Le joueur lit « relance
le jeu depuis le launcher » ; le staff reçoit la raison (fil du joueur et « Informations du staff »). Les indices devinés
(overlays, threads, DLL, programmes ouverts, code d'origine inconnue) restent des alertes. Refus à tort : passer
`strictOfficial = false` (relu sans redémarrer). `officiel.json` est relu tout seul quand la mise en ligne le change.
Tests : `ConformiteStricteTest`. Essayé le 27/10 sur un serveur de test avec un vrai jeu : jeu officiel accepté (défi
réussi, contrôle propre), jeu avec un jar en plus refusé en 3 s.

**Alertes Discord (2.9.0)** : file d'envoi de 300 messages (sanctions et bans d'abord), 1 message par seconde et par
webhook, attente demandée par Discord (HTTP 429) respectée ; une erreur de Discord ne coupe plus les fils par joueur ;
`/spartan discordtest` (op 3 ou console) envoie un message d'essai sur chaque webhook et donne la réponse de Discord.

**Jeu validé à chaque connexion (2.9.3, 27/10, demande du propriétaire, section `[validation]`)** : une fois le joueur
vérifié (jeton + `/login`), quand le jeton du launcher est accepté (dossier mods officiel), le défi d'intégrité réussi
(Spartan de la même version, aucune réponse fausse) et le premier contrôle d'intégrité terminé sans rien de suspect et
sans aucun écart au jeu officiel : « ✅ Jeu validé » dans le fil Discord du joueur (temps du contrôle, launcher, jeu,
Gardien) et une ligne dans la console et `alerts.log`. Rien dans les `timeoutSeconds` (60 s) après le `/login`, ou
déconnecté sans validation après 30 s : « ⚠️ Validation jamais reçue » avec ce qui manque, dans le fil du joueur, dans
« Informations du staff » et au staff en jeu. Un message par connexion ; bots ignorés ; joueur refusé par le mode strict :
seulement l'alerte du refus. `/spartan status <joueur>` donne l'état (`integrity/ValidationJeu`).

**Fausses alertes corrigées (2.9.3)** : une bibliothèque d'un Java normal rangée dans le Java du jeu (`%JAVA%` et ses
sous-dossiers : javajpeg pour la capture d'écran, awt, polices, lcms…) chargée en cours de partie n'est plus « injection
probable » (une autre DLL de ce dossier reste orange) ; un module venu d'un jar officiel n'est plus « module de code
inconnu » : reconnu par l'empreinte de son jar (le launcher range les mods sous le nom de leur mods.toml, ex.
`heraliz_noyau-1.0.2.jar`, jamais celui d'`officiel.json`), conteneur compris pour un jar imbriqué
(« conteneur.jar/imbriqué.jar »), modpack précédent compris.

**Détection silencieuse (par défaut) :** tout ce qui vient du jeu du joueur
(murailles 1 à 4, 7, 8) ne fait JAMAIS d'expulsion ni de ban automatique, et rien
n'est affiché au joueur. Le staff reçoit sur Discord le pseudo, les indices et le
rapport complet en pièce jointe, surveille le joueur en spectateur, puis décide
(`/sban`). Le tricheur ne sait pas qu'il est repéré, ni par quoi.

---

## 1. Installation (serveur + joueurs)

Spartan est **coupé en deux** (méthode HeraliZ, `docs/METHODE_CLIENT_SERVEUR.md` du dépôt) :

| Jar | Pour qui | Contenu |
|---|---|---|
| `spartan-<version>-client.jar` | les joueurs | infos de connexion, réponses aux défis, contrôle d'intégrité. **Aucune** détection serveur, aucun seuil, aucune signature : rien à étudier pour contourner |
| `spartan-<version>-serveur.jar` | le Ptero | toutes les détections, l'anti-ESP, les bans, la config, les Mixins serveur, et une **copie du jar joueur** (`META-INF/spartan/client.bin`) sur laquelle le serveur calcule les réponses attendues aux défis |
| `spartan-<version>-complet.jar` | le solo | tout |

1. Fabriquer : `python outils/construire.py spartan` (dans le dépôt), puis `python outils/verifier.py spartan`.
2. La mise en ligne GitHub envoie le jar `-serveur` sur le Ptero, la config `serveur/config/spartan-common.toml` et la
   liste officielle des mods `config/spartan/officiel.json` (voir section 5).
3. Les joueurs reçoivent le jar `-client` **de la même fabrication** (paquet « mods-client » de GitHub).

⚠️ Le jar joueur et le jar serveur doivent venir de la **même fabrication** : le serveur compare chaque réponse au
défi avec sa copie du jar joueur. Un joueur avec un autre jar joueur (pas à jour, ou modifié) est signalé au staff.

## 2. Configuration — `config/spartan-common.toml`

Ce fichier n'est **jamais envoyé aux joueurs** (type `COMMON` de Forge). Il peut
donc contenir l'URL du webhook Discord en sécurité. Forge le recharge
automatiquement quand on l'enregistre depuis un éditeur ; certains outils qui
remplacent le fichier au lieu de le modifier (ex. `sed -i`) ne sont pas
détectés : en cas de doute, **redémarrer le serveur**.

```toml
[general]
    enabled = true
    alertOnly = true              # ← AUCUNE sanction auto. Garder true au début !
    violationLevelForAction = 10  # VL à partir duquel on expulse (si alertOnly=false)
    violationDecaySeconds = 30    # le VL baisse de 1 toutes les 30 s sans infraction

[alerts]
    inGameStaff = true            # alerte les op niveau 2+ connectés
    logFile = true                # config/spartan/alerts.log (rotation à 10 Mo)
    cooldownSeconds = 5           # 1 alerte max / 5 s par joueur et par détection
    discordEnabled = false
    discordWebhookUrl = ""

[evidence]                       # boîte noire
    enabled = true
    ticks = 200                   # 10 s d'historique
    maxFiles = 500                # les plus anciennes preuves sont supprimées

[checks]
    fly = true
    flyHoverItems = ["mekanism:jetpack", ...]  # ← jetpacks : identifiants EXACTS (vol sur place, montée)
    flyExemptKeywords = ["parachute", "glider"]  # ← mots-clés : ralentissent seulement la chute
    flyMinFallPerSecond = 0.4     # un parachute descend toujours : il ne déclenche pas
    flyGearMinFallPerSecond = 0.05  # descente minimale avec parachute / planeur
    vertical = true               # Step / HighJump / AirJump (désactiver si mod de double saut / parkour)
    noFall = true
    timer = true
    badPackets = true
    speed = true
    speedMaxPerTick = 0.45
    reach = true
    reachDistance = 3.3           # + marge automatique selon le ping
    killaura = true
    killauraMaxAngle = 60.0
    autoclicker = true
    autoclickerMaxCps = 20
    nuker = true
    nukerMaxPerTick = 3
    fastbreak = true
    fastbreakRatio = 0.85         # vanilla refuse déjà en dessous de 0.7
    illegalEffects = true
    effectMaxLevel = 5
    effectMaxMinutes = 120
    ignoredEffects = ["minecraft:bad_omen", ...]  # ← effets permanents d'armures de mods

[integrity]
    silentClientDetections = true # ← jamais d'expulsion pour ce qui vient du jeu du joueur : alerte staff
    handshake = true
    handshakeTimeoutSeconds = 90  # un client à 100 mods peut rester figé après la connexion
    clientIntegrity = true
    allowedJavaAgents = ["authlib-injector"]   # agents légitimes (launchers crack)
    buildCheck = true             # même build que le serveur, sinon « relancez le launcher »
    challenge = true              # défi tournant
    challengeMinSeconds = 60      # intervalle aléatoire entre deux défis
    challengeMaxSeconds = 240
    challengeTimeoutSeconds = 20
    requireHardenedJvm = false    # true = exige le lancement via le launcher (anti-injection)

[resources]                       # imposer l'apparence du serveur
    blockCustomResourcePacks = true   # + require-resource-pack=true dans server.properties
    blockShaders = true               # idéal : ne pas inclure de mod de shaders
    allowedShaderpacks = []

[launcher]                        # voir docs/LAUNCHER.md
    launcherSecret = ""           # secret partagé avec le launcher (vide = désactivé)
    tokenRequired = false         # true = expulse ceux qui ne passent pas par le launcher
    allowedModsHashes = []        # empreintes des modpacks officiels (vide = non vérifié)

[mitigation]                      # correction instantanée (indépendante de alertOnly)
    movementSetback = true        # Fly / Speed : renvoi à la dernière position au sol
    cancelCombat = true           # Reach / KillAura / AutoClicker : coup annulé
    combatLockSeconds = 3         # puis plus aucun coup pendant 3 s
    cancelBlocks = true           # Nuker / FastBreak : bloc non cassé
    blockLockSeconds = 3          # puis plus aucune casse pendant 3 s
    removeIllegalEffects = true   # effet illégal retiré

[bans]
    ipBanHours = 72                # durée max du ban d'IP (0 = jamais bannir l'IP)
    alertSameIp = true             # alerte si un joueur arrive avec l'IP d'un banni
    playerDataRetentionDays = 180  # RGPD : purge des données des non-bannis

[modscan]
    blacklistedMods = ["wurst", "meteor", "doomsday", "vape", ...]

[scan]                            # contrôle d'intégrité du client (voir section 5)
    enabled = true
    firstScanMinSeconds = 10      # 1er contrôle 10 à 30 s après la connexion
    firstScanMaxSeconds = 30
    intervalMinSeconds = 180      # puis toutes les 3 à 8 min, au hasard
    intervalMaxSeconds = 480
    responseTimeoutSeconds = 90   # 2 absences de réponse de suite = alerte
    renderSamples = 40            # échantillons du thread de rendu (code injecté)
    mixinWatchClasses = ["net.minecraft.client.renderer.LevelRenderer", ...]  # classes sensibles
    classProbes = ["baritone.api.BaritoneAPI", ...]   # classes de cheats connus (jamais dans le jar)
    processPatterns = ["cheatengine", "processhacker", ...]
    nativePatterns = ["inject", "cheat", "hack", ...]
```

### Brancher les alertes sur un salon Discord privé
1. Créer un salon **privé**, visible des seuls admins.
2. Paramètres du salon → *Intégrations* → *Webhooks* → *Nouveau webhook* → *Copier l'URL*.
3. Dans `spartan-common.toml` : `discordEnabled = true`, coller l'URL dans `discordWebhookUrl`.
4. Enregistrer le fichier.

⚠️ L'URL d'un webhook permet à quiconque la possède de poster dans le salon :
ne jamais la publier (ni sur Discord, ni dans un dépôt Git).

### Régler les seuils (méthode conseillée)
1. Laisser `alertOnly = true` une à deux semaines.
2. Pour chaque alerte, ouvrir la preuve (`config/spartan/evidence/…json`) et
   vérifier : vraie triche ou faux positif ?
3. Faux positif récurrent → ajuster le seuil ou ajouter l'item dans `flyHoverItems`.
4. Quand les alertes sont fiables, passer `alertOnly = false`.

## 3. Commandes

| Commande | Niveau op | Effet |
|---|---|---|
| `/sban <joueur> <durée> [raison]` | 3 | Bannit le **compte + ses IP + ses PC**. Durée : `30m`, `12h`, `7d`, `2w` ou `def`. Marche aussi si le joueur est hors ligne (il doit s'être connecté au moins une fois). |
| `/sunban <joueur>` | 3 | Lève **tous** ses bans actifs (compte, IP, PC). Marche hors ligne. |
| `/unbanplayer <joueur>` | 3 | Alias de `/sunban` (l'ancienne commande, réparée). |
| `/banpc …`, `/unbanpc <joueur>` | 3 | Mêmes commandes que `/sban` et `/sunban`, avec les noms alignés sur ceux de LiteBans. |
| `/untempban <joueur> [raison]` | LiteBans | Transmis à `/unban` de LiteBans (qui lève aussi les bans temporaires), avec les droits de celui qui la tape. |
| `/sbaninfo <joueur>` | 2 | Nombre d'IP/PC connus, **comptes liés** (même IP ou même PC), historique des bans. |
| `/spartan checks` | 2 | Liste des détections actives. |
| `/spartan vl <joueur>` | 2 | Niveaux de violation actuels du joueur. |
| `/spartan status <joueur>` | 2 | Intégrité (build, défis, jeton du launcher) et suivi du mouvement (positions analysées, téléportations confirmées). |
| `/spartan setback <joueur>` | 2 | Renvoie le joueur à sa dernière position sûre au sol. |
| `/spartan scan <joueur>` | 2 | Contrôle d'intégrité immédiat, invisible pour le joueur ; le résultat arrive dans le chat (et sur Discord si suspect). |
| `/spartan report <joueur>` | 2 | Indices du dernier contrôle d'intégrité et nom du fichier de preuve. |
| `/spartan suspects` | 2 | Joueurs en ligne dont le dernier contrôle est suspect. |
| `/spartan reference learn <joueur>` | 3 | Apprend le **client de référence** depuis le jeu de ce joueur (modpack officiel PROPRE uniquement). |
| `/spartan reference add <joueur>` | 3 | Complète la référence avec un 2e PC propre (autre version de Java, autre Windows…). |
| `/spartan reference info` / `clear` | 2 / 3 | Résumé / effacement de la référence. |
| `/spartan selfscan` | 3 | Contrôle du serveur lui-même (diagnostic : voir ce que le scanner relève). |
| `/spartan esp [joueur]` | 2 | Anti-ESP : coût et activité, ou ce que ce joueur reçoit (voir section 6). |
| `/spartan esp bench [n] [rayon]` | 3 | Coût de l'anti-ESP sur votre carte avec *n* joueurs fictifs. |

### Comment marche le ban
- **Compte** : ajouté aux bans de Minecraft → refusé **dès l'écran de
  connexion**, même hors ligne, avec expiration automatique.
- **IP** : bannie aussi, mais **72 h maximum** par défaut (`ipBanHours`), car une
  IP est souvent partagée (4G, box familiale, lycée).
- **PC** : identifiants matériels bannis. Un autre compte qui arrive depuis ce PC
  est expulsé, et le staff reçoit une alerte **« compte secondaire de X »**.
- Un joueur qui arrive avec **l'IP** d'un banni n'est pas expulsé (IP partagée
  possible) : le staff reçoit une alerte « à surveiller ».
- Chaque ban et chaque déban est notifié au staff, dans le log et sur Discord.
- Les bans de l'ancien Spartan 1.0 (`config/bancomposant/`) sont **importés
  automatiquement** au premier démarrage.

### Fichiers de données (`config/spartan/`)
| Fichier | Contenu |
|---|---|
| `bans.json` | Historique complet des bans (qui, quand, pourquoi, levé par qui). |
| `players.json` | Pseudo, IP et PC vus par joueur. **Purgé après 180 jours** d'inactivité pour les joueurs non bannis (RGPD, `playerDataRetentionDays`). |
| `alerts.log` | Toutes les alertes (rotation à 10 Mo). |
| `evidence/` | Boîtes noires et rapports de contrôle d'intégrité (les plus anciens supprimés au-delà de `maxFiles`). |
| `reference.json` | Client de référence (ce que contient un jeu propre du modpack officiel). |

## 4. Détections

### Côté serveur (infalsifiables : basées sur ce que le joueur fait)

Le mouvement est analysé **paquet par paquet**, tel que le client l'envoie
réellement (observateur réseau placé avant le traitement vanilla), sur les
positions qu'il revendique — jamais sur la simulation physique interne du
serveur. Les téléportations sont suivies jusqu'à leur confirmation par le
client ; seule une confirmation d'une téléportation réellement envoyée
repositionne l'analyse, sur la destination choisie par le serveur. Un client qui
rame n'envoie rien : il n'est jamais pris pour un tricheur.

| Détection | Ce qu'elle repère | Comment |
|---|---|---|
| **Fly** | Vol, planer, hovering, Jesus | En l'air, le joueur suit la **physique de vanilla tick par tick** (trajectoire maximale recalculée depuis le dernier appui : saut maximal, Saut amélioré, gravité des mods). Descendre moins vite que la gravité dépasse cette trajectoire en moins d'une seconde ; un bloc fantôme corrigé avec le ping reste sous la tolérance. Immobilité parfaite en l'air 3 s alors que le client envoie toujours des paquets. **Parachute / planeur** (mots-clés) : seulement une descente minimale. Exemptions : sol réel, vol autorisé, élytre, riptide, montures et véhicules, lévitation, chute lente, fluides, échelles, toiles / miel / neige poudreuse, plateformes (bateau, Create, shulker), pistons, jetpacks (identifiants exacts). |
| **Speed** | Vitesse anormale, bunny-hop | **Enveloppe physique de vanilla** recalculée à chaque position : vitesse réelle du joueur (effets, équipements de mods), sprint, saut en sprint, glace, plafond bas, frottement, poussée des mobs collés. La vitesse suivante repart toujours de la distance **bornée** : impossible d'accumuler de l'élan en trichant un peu à chaque tick. Dépassement cumulé toléré ~0,5 bloc par seconde ; déplacement impossible en une position signalé tout de suite. |
| **Vertical** | Step, HighJump, AirJump, Spider | Même modèle vertical : monter plus haut que la trajectoire maximale (marche d'escalier ou de dalle comprise), re-sauter en plein air, escalader un mur sans échelle. Rebonds (slime, lit) avec leur vitesse exacte. |
| **NoSlow** | Courir en mangeant, buvant, se soignant, bouclier levé | Vanilla ralentit à 20 % pendant l'utilisation d'un objet. Vérifié seulement quand l'utilisation est **certaine malgré le ping** ; relâcher, changer d'objet ou le lâcher est suivi dans l'ordre exact des paquets. `noSlowIgnoredItems` pour les objets de mods qui ne ralentissent pas. |
| **NoFall** | Annuler les dégâts de chute | Signalé si le client se déclare au sol en pleine chute ; **corrigé** dans tous les cas : le serveur refuse la déclaration « au sol » s'il n'y a aucun appui réel (Mixin), les dégâts s'appliquent normalement. |
| **Velocity** | Anti-recul (AntiKB) | Le client a forcément appliqué un recul (transactions, voir plus bas) mais aucune de ses positions ne le montre alors que rien ne le bloquait. Le « jump reset » légitime est pris en compte. Signalé à partir de 4 reculs ignorés sur 8 ; pas de renvoi. |
| **Timer** | Jeu accéléré | Avec les transactions, la cadence est bornée par **l'horloge du serveur** : ×2 vu en moins d'une seconde, ×1,05 en une vingtaine de secondes, aucun crédit à accumuler en restant immobile, jamais de faux positif dû au lag (repli sur un crédit de temps réel si le client ne répond pas aux pings). |
| **BadPackets** | Paquets impossibles | Angle de vue hors limites, positions retenues alors que le client agit, téléportation jamais confirmée, inondation de paquets, pings ignorés 30 s alors que le client bouge, réponses à des pings jamais envoyés. |
| **Reach** | Frappe de trop loin | Distance œil → hitbox au moment du coup, comparée à la **portée réelle du joueur** (armes longues de mods prises en compte), avec marge selon le ping. |
| **KillAura** | Frappe hors du champ de vision | Angle entre le regard et la cible, moins la taille de la hitbox. |
| **AutoClicker** | Clics de machine | Trop de coups par seconde, ou intervalles trop réguliers (un humain varie toujours). |
| **Nuker** | Blocs cassés en masse | Plusieurs blocs **cliqués** cassés dans le même tick (un humain : 1 max). Les mods de minage en chaîne (FTB Ultimine, abattage d'arbres…) ne déclenchent pas : le joueur n'a cliqué que le premier bloc. |
| **FastBreak** | Speedmine | Temps réel de minage comparé au temps attendu pour CE joueur (outil, enchantements, Célérité, eau…), plusieurs blocs de suite. |
| **Duplication** | Objets copiés (exploit de duplication) | Chaque objet de valeur non empilable reçoit un numéro unique invisible ; un relevé périodique (inventaires, coffre de l'Ender, Curios, contenu des sacs, en une fois) repère un même numéro à deux endroits au même instant. Alerte seulement, rien n'est supprimé. |
| **IllegalEffect** | « Se donne des effets » | Effet de niveau ou de durée impossible. Un client ne peut pas se donner un vrai effet : s'il en a un, c'est une commande ou une faille de mod → à investiguer en priorité. Staff ignoré. |

**Reculs, explosions et lag : les transactions.** Le serveur envoie à chaque
joueur un ping vanilla par tick ; le client le traite dans l'ordre des paquets,
avant ses ticks. Quand il répond, il a forcément appliqué tout ce qui précédait
(recul d'un coup ou d'une balle, explosion, fin d'un effet, vol retiré…). Le
modèle de mouvement sait donc **exactement** à partir de quelle position un recul
ou un changement d'état s'applique, quel que soit le ping ou un gel du réseau,
au lieu de fenêtres de temps qu'un mauvais réseau dépasse (faux positifs) et
qu'un tricheur peut exploiter. Une position = un tick du client : un paquet
retardé par le réseau ne fait perdre aucun élan au joueur. Désactivable
(`transactions`), quelques octets par joueur et par tick.

### Côté client (best-effort : un client modifié peut mentir)

| Détection | Ce qu'elle repère |
|---|---|
| **Contrôle d'intégrité** | Voir la section 5 : JAR inconnus ou modifiés, modules et Mixins inconnus, agents Java ou natifs, programme attaché au jeu, code d'origine inconnue, DLL suspectes, logiciels de triche, anticheat modifié en mémoire. |
| **Handshake** | Le client n'a pas envoyé ses infos (mods, build, jeton) après la connexion → mod client modifié ou désactivé. |
| **Integrity** (défi tournant) | Réponse fausse ou absente au défi : le mod Spartan du joueur a été modifié, bloqué ou désactivé. Chaque défi a une nouvelle graine aléatoire et couvre **toutes** les classes du mod dans un ordre tiré au hasard : une seule classe modifiée suffit, et une ancienne réponse ne resert jamais. |
| **Launcher** | Jeton du launcher absent / invalide / pour un autre pseudo / expiré, ou mods chargés différents de ceux vérifiés par le launcher. |
| **ClientIntegrity** | Agent Java non autorisé chargé dans le jeu, ou threads anonymes (traces d'injecteurs). |
| **ModScan** | Mod interdit dans la liste des mods déclarée par le client. |
| **ResourcePack** | Pack de texture ajouté par le joueur (au-delà de ceux du serveur et du jeu). |
| **Shaders** | Shaderpack actif (Iris/Oculus/OptiFine), si non autorisé. |
| **JvmHardening** | Jeu lancé sans les protections anti-injection (si `requireHardenedJvm`). |
| **Ban matériel** | Identifiant de la carte mère / HWID d'un PC banni (voir section 3). |

### Ce qui se passe lors d'une détection
1. Le **VL** (niveau de violation) du joueur augmente pour cette détection ; il
   redescend tout seul avec le temps.
2. La **boîte noire** (10 dernières secondes de positions envoyées par le
   client : position, regard, déplacement, sol déclaré ET sol réel, vie,
   téléportations marquées) est figée dans `config/spartan/evidence/`.
3. **Alerte** : staff en jeu + `alerts.log` + Discord.
4. **Correction instantanée** : mouvement → retour ~1 s en arrière, sur une
   position au sol que le joueur a réellement occupée (donc avant la triche), ou
   à défaut dépôt au sol juste en dessous ; jamais dans un fluide, un bloc
   dangereux, une zone non chargée ou une place occupée ; effectif côté serveur
   dans le même tick. Coup annulé puis combat bloqué 3 s, bloc non cassé puis
   minage bloqué 3 s, effet retiré.
5. Si `alertOnly = false` et VL ≥ seuil : **expulsion** — uniquement pour les
   détections serveur (mouvement, combat, minage). Ce qui vient du jeu du joueur
   reste une alerte silencieuse tant que `silentClientDetections = true`.

La boîte noire ne contient **que des données de jeu** déjà connues du serveur.
La capture d'écran du joueur qui existait dans la 1.0.0 a été **supprimée**
(donnée personnelle, illégale sans consentement).

## 5. Contrôle d'intégrité : repérer les JAR de cheat et les clients injectés

À la connexion (10 à 30 s après), puis à intervalle aléatoire (3 à 8 min), le jeu
du joueur envoie en silence ce qu'il a **réellement** chargé. Le contrôle tourne
hors du thread du jeu (~0,5 s, plus ~2 s d'échantillonnage du rendu) : le joueur
ne sent rien et ne voit rien.

### Ce qui est relevé

| Indice | Gravité | Pourquoi |
|---|---|---|
| JAR inconnu dans `mods/` (comparé par empreinte : renommer ne sert à rien) | 🟠 | Mod ajouté au modpack officiel |
| JAR officiel **modifié**, fichier de Minecraft / Forge modifié | 🔴 | Triche cachée dans un mod connu |
| Config **Mixin** inconnue qui modifie le rendu, le mouvement, la souris, le réseau, TaCZ… | 🔴 | C'est ainsi que fonctionnent les mods ESP, X-ray, fly, NoSlow, aimbot. Le rapport dit quelle classe est modifiée et ce que ça permet |
| Module / composant de lancement inconnu (transformateur, coremod) | 🔴 / 🟠 | Code chargé par une autre voie que le dossier mods |
| Mod chargé hors du dossier `mods/` (options `--fml.*`) | 🔴 | Contourne la vérification du launcher |
| Agent Java au lancement, agent natif, débogueur, `-Xbootclasspath`… | 🔴 | Méthodes d'injection au démarrage (un faux « authlib-injector » est repéré par son empreinte) |
| Programme **attaché au jeu en cours de partie** | 🔴 | Signature d'un injecteur |
| **Code d'origine inconnue** en cours d'exécution (piles de threads + thread de rendu échantillonné) | 🔴 | Un client injecté exécute des classes hors de tout module chargé normalement ; un ESP tourne à chaque image |
| Anticheat modifié en mémoire (Mixin appliqué à Spartan) | 🔴 | Le cheat neutralise l'anticheat |
| **Thread démarré hors de tout module** (Windows) | 🔴 | Code natif posé en mémoire par un injecteur, même quand la DLL est cachée (« manual map ») : aucune DLL chargée normalement ne contient son adresse de départ |
| **Affichage détourné** (wglSwapBuffers / SwapBuffers) | 🔴 / 🟠 | Saut vers du code sans module (ESP / menu natif injecté) ou vers un module inconnu ; Discord, Steam, OBS, NVIDIA, RivaTuner… sont reconnus et ignorés |
| Classe d'un cheat connu (`classProbes`) | 🔴 | Signatures gardées sur le serveur, jamais dans le jar |
| DLL chargée depuis Temp / Téléchargements / Bureau, ou au nom suspect | 🟠 | Injection de DLL |
| Logiciel de triche ouvert (Cheat Engine, injecteurs…) | 🟠 | Seuls les programmes de la liste sont envoyés, jamais la liste complète |
| Thread sans nom, chargeur de classes inhabituel | 🟠 | Traces fréquentes d'injecteurs |

Tout ce qui **apparaît en cours de partie** (absent du premier contrôle) est
aggravé : c'est la signature d'une injection après le lancement.

### Mise en place : rien à faire à la main

**Modpack officiel automatique (HeraliZ 2.0)** : à chaque fabrication, `outils/construire.py` écrit
`livraison/spartan/officiel.json` à partir des jars réellement distribués aux joueurs :
- l'empreinte (sha256) de chaque jar : un jar en plus est relevé (renommer ne sert à rien), un jar officiel modifié
  est un indice fort ;
- toutes les configs Mixin que ces jars déclarent (manifeste, jars imbriqués, fichiers de config) : toute autre config
  Mixin (mod ESP / X-ray / fly, client injecté qui passe par Mixin) est inconnue, et le rapport dit ce qu'elle modifie ;
- l'empreinte du dossier mods officiel, comparée dès la connexion (mod ajouté, retiré ou modifié).

La mise en ligne la dépose dans `config/spartan/officiel.json` ; `/spartan officiel` la relit sans redémarrer.

**Client de référence (facultatif, en plus)** : `/spartan reference learn <ton pseudo>` depuis un jeu PROPRE apprend
aussi ce qui ne se voit qu'en jeu (modules et composants de lancement, chargeurs de classes, origines du code,
bibliothèques du lanceur). À refaire après une mise à jour du modpack ; `/spartan reference add <pseudo>` depuis un
2e PC propre (autre Java / Windows) pour couvrir les petites différences.

⚠️ Ne jamais apprendre la référence depuis le PC d'un joueur inconnu : sa triche deviendrait « normale ».

### Quand une alerte tombe
1. Discord : pseudo, verdict (🔴 indice fort / 🟠 suspect), liste des indices, et
   le **rapport complet en pièce jointe** (aussi dans `config/spartan/evidence/`).
2. `/spartan report <joueur>` en jeu pour revoir les indices, `/spartan scan <joueur>`
   pour un nouveau contrôle immédiat.
3. Surveiller le joueur en spectateur, puis `/sban` si c'est confirmé.

### Limites (honnêtement)
Aucun anticheat ne peut garantir 100 % : le code du cheat tourne sur le PC du
tricheur. Ce contrôle attrape les JAR de cheat, les clients injectés publics et
la grande majorité des tricheurs qui ne visent pas spécifiquement ce serveur. Un
développeur de cheat qui étudie Spartan peut falsifier les rapports : c'est pour
ça que les signatures restent sur le serveur, que les contrôles tombent au hasard,
et que les détections serveur (mouvement, combat) restent le dernier filet.

## 6. Anti-ESP / anti-wallhack : le tricheur ne reçoit plus ce qu'il ne voit pas

Un ESP, un wallhack ou un radar n'invente rien : il affiche les joueurs que le
serveur envoie au client, **murs compris**. Spartan n'envoie plus un joueur à un
autre tant qu'**aucune ligne de vue** n'existe entre eux (mur, bâtiment fermé,
porte fermée, sous-sol, colline). Le joueur caché **n'existe pas** chez le
tricheur : ni ESP, ni pseudo à travers les murs, ni radar, ni minimap ne peuvent
l'afficher, même avec un client injecté indétectable. C'est la seule parade
réelle contre l'ESP, et elle est entièrement côté serveur.

### Ce qui cache et ce qui ne cache pas
- Cachent : tous les blocs pleins et opaques (y compris ceux des mods), les blocs
  partiels opaques (dalles, escaliers…) selon leur forme, les **portes fermées**
  (`opaqueBlocks`, la porte ouverte ne cache plus).
- Ne cachent jamais : verre, vitres, feuilles, clôtures, barreaux, eau, chunks non
  chargés. Dans le doute, on montre.

### Jamais d'invisibilité injuste
- **Anticipation du ping** : un joueur est envoyé dès qu'il serait visible après un
  pas de côté (`peekBlocks`), un saut (`jumpBlocks`) ou le mouvement en cours de
  l'observateur pendant son aller-retour réseau. Il apparaît à l'angle du mur à
  temps, pas une fraction de seconde trop tard.
- Un joueur aperçu reste envoyé 1 s après avoir disparu derrière un obstacle
  (`hideDelayTicks`) : pas de clignotement le long des fenêtres.
- Toujours visibles : **coéquipiers** (équipes vanilla, équipes de match HeraliZ, mode zombies, clans), joueurs en surbrillance (effet Glowing), même véhicule, joueurs à moins de
  2,5 blocs. Les **spectateurs** et les joueurs en **créatif** voient tout le monde
  (le staff qui surveille un suspect en spectateur voit tout).
- **Bruits** : un joueur qui **tire avec TaCZ** (ou recharge, arme la culasse…) est
  envoyé aux joueurs à portée du son, à la distance exacte de TaCZ (silencieux
  compris), sinon ils n'entendraient pas son tir. Idem pour un joueur qui **parle
  avec la voix de CaraCrea** (portée normale ou chuchotement de sa config).
  Comme dans n'importe quel jeu : qui fait du bruit se fait repérer, par ceux qui
  l'entendent.
- Serveur saturé : une paire cachée qui n'a pas pu être revérifiée à temps est
  **montrée** (comportement vanilla), jamais l'inverse.

### Performances (50-70 joueurs)
Les lignes de vue sont calculées **sur d'autres cœurs du processeur**, en parallèle
du tick du serveur (`threads`, automatique par défaut) : le thread principal ne fait
que préparer la liste et appliquer les décisions (quelques dixièmes de ms). Les
joueurs immobiles ne coûtent presque rien (résultat repris tant que rien ne bouge
et qu'aucun bloc ne change entre eux). Mesuré en jeu : **70 joueurs en mouvement
serrés dans une petite ville ≈ 3,5 ms de calcul par tick**, sur un cœur séparé.

Pour mesurer sur **votre carte** avant d'avoir 70 joueurs : placez-vous au milieu
d'une zone dense (ville, base militaire) et tapez `/spartan esp bench 70 150`.
En jeu, `/spartan esp` donne le coût réel des 5 dernières secondes.

### Commandes
| Commande | Niveau op | Effet |
|---|---|---|
| `/spartan esp` | 2 | Coût et activité de l'anti-ESP (5 dernières secondes). |
| `/spartan esp <joueur>` | 2 | Joueurs cachés à ce joueur et joueurs qu'il reçoit (vérifie aussi que Minecraft applique bien la décision). |
| `/spartan esp bench [n] [rayon]` | 3 | Coût estimé sur la carte avec *n* joueurs fictifs autour de vous. |

### Mod de clan : coéquipiers toujours visibles
Les équipes vanilla (`/team`) sont reconnues d'office. Un mod de clan déclare son
propre test d'alliance au démarrage (étape `InterModEnqueueEvent`) :
```java
InterModComms.sendTo("spartan", "ally_predicate",
        () -> (BiPredicate<ServerPlayer, ServerPlayer>) (a, b) -> MesClans.memeClan(a, b));
```
Le test est appelé sur le thread serveur ; il doit être rapide (résultat gardé 2 s).

### Limites (honnêtement)
- Les **sons** restent positionnels : un tricheur peut toujours afficher d'où
  viennent les pas, les tirs et les voix qu'il entend (comme un joueur honnête
  avec un casque). Il ne voit plus personne de silencieux derrière un mur.
- Les **coffres et le butin** (X-ray de conteneurs) ne sont pas encore cachés.
- Mods client qui produisent des sons à partir des joueurs affichés (ex. Presence
  Footsteps) : un joueur caché n'en produit pas ; les pas vanilla restent audibles.
- Sur les serveurs hybrides (Mohist, Arclight…), l'anti-ESP passe par Mixin ; si un
  plugin modifie profondément le suivi des entités, vérifier avec `/spartan esp <joueur>`.

## 7. Feuille de route

- **Chest ESP** : ne pas envoyer au client les coffres et le butin qu'il ne peut
  pas voir.
- **AutoHeal / AutoTotem** et détection d'anomalies d'items (dupe).
- **Obfuscation du jar** (rendre la décompilation illisible) et nouvelles
  variantes de défi à chaque version.

## 8. Architecture du code

Sources coupées en trois (le compilateur interdit les liens interdits) :
- `src/commun` : `Spartan` (@Mod : canal réseau + chargement des parties), `pont/` (Parties, Partie, Crochet),
  `Networks/` (lecture / écriture des paquets ; leur traitement est branché par les parties), `scan/ScanReport`,
  `ScanTransfer`, `CollecteLocale`, `integrity/Fingerprint` (empreinte, forme vivante / forme du fichier), `LaunchToken` ;
- `src/client` : `Client/` (PartieClient, ClientEvents, ClientScanService…), `scan/collect/` (collecteurs, dont
  `InjectionCollector`), `integrity/ModsHasher`, `Utils/` ;
- `src/serveur` : tout le reste — `serveur/` (PartieServeur, ReceptionServeur, `compat/HeralizCompat`), détections,
  anti-ESP, bans, alertes, config, `scan/` (analyse, `OfficialProfile`, référence), Mixins.

Arborescence d'origine (avant la coupe) :

```
com.Spartan
├── Spartan.java              @Mod : config, détections, réseau, commandes
├── SpartanEvents.java        Branche les détections sur les événements serveur
├── SpartanCommand.java       /spartan checks | vl | status | setback
├── config/SpartanConfig      Config COMMON (jamais envoyée au client)
├── alert/AlertManager        Sortie unique : staff + log + Discord
├── alert/DiscordWebhook      POST asynchrone vers le webhook
├── evidence/EvidenceRecorder Boîte noire (tampon tournant + dump JSON asynchrone)
├── integrity/
│   ├── Fingerprint           Empreinte des classes du mod (identique client/serveur)
│   ├── ChallengeManager      Défi tournant : nonce, variante, classes aléatoires
│   ├── LaunchToken           Jeton du launcher + empreinte du dossier mods (Java pur, testé)
│   ├── LauncherGate          Vérification du jeton côté serveur
│   └── ModsHasher            Empreinte des mods calculée en arrière-plan (client)
├── check/
│   ├── Check, MovementCheck, CombatCheck, BlockCheck   Bases
│   ├── CheckManager          Registre, VL (avec décroissance), cooldown, grâce
│   ├── HandshakeMonitor      Vérifie que le client envoie ses infos
│   ├── Mitigation            Correction instantanée (renvoi, annulations, verrous)
│   ├── movement/
│   │   ├── PacketObserver    Observateur Netty : mouvements, confirmations, téléportations, reculs, pings
│   │   ├── MovementEngine    Rejoue les paquets à chaque tick, suit les téléportations, alimente les détections
│   │   ├── Transactions      Ping/pong par tick : ce que le client a forcément reçu (reculs, états)
│   │   ├── MotionTracker     Enveloppes de mouvement d'un joueur, une position = un tick du client
│   │   ├── MoveSample, MovementEnv   Déplacement revendiqué + environnement à cette position
│   │   ├── FlyCheck, SpeedCheck, VerticalCheck, NoSlowCheck, NoFallCheck, NoFallGuard,
│   │   │   VelocityCheck, TimerCheck, BadPacketsCheck
│   │   ├── FlightGear        Jetpacks (identifiants) / parachutes (mots-clés), slots Curios
│   │   ├── IllegalEffectCheck
│   │   └── model/            Physique vanilla (enveloppes horizontale / verticale), fenêtres de
│   │                         dépassement, horloge bornée par les pings (Java pur, testés)
│   ├── combat/               ReachCheck, KillAuraCheck, AutoClickerCheck
│   └── block/                NukerCheck, FastBreakCheck
├── scan/                     Contrôle d'intégrité du client
│   ├── ScanReport            Rapport (format borné) : fichiers, modules, Mixins, agents, threads, DLL…
│   ├── ScanTransfer          Compression + découpage (< 32 Ko par paquet), recomposition bornée
│   ├── ScanAnalyzer          Rapport → indices (Java pur, testé)
│   ├── ReferenceProfile      Client de référence (config/spartan/reference.json)
│   ├── ScanManager           Planification, réception, alertes Discord avec preuve jointe
│   └── collect/              Collecteurs : JVM, threads, Forge/Mixin, natifs, autocontrôle
├── esp/                      Anti-ESP / anti-wallhack
│   ├── Raycaster, Sight      Ligne de vue sur la grille de blocs, points anticipés (Java pur, testés)
│   ├── VisibilityEngine      Paires de joueurs, décisions, bruits, lots de calcul, statistiques
│   ├── EspWorkers            Threads de calcul en parallèle du tick
│   ├── Occluders, ChunkTable, LevelVoxels   Blocs qui cachent, lecture des chunks hors du thread serveur
│   ├── TrackerAccess         Application immédiate d'une décision au suivi des entités vanilla
│   ├── Allies, Noise         Coéquipiers (IMC + HeraliZ) ; tirs TaCZ et voix de CaraCrea
│   ├── EspBenchmark          /spartan esp bench
├── mixin/                    ServerPlayer.broadcastToPlayer, ServerLevel.sendBlockUpdated,
│                             ServerPlayer.doCheckFallDamage (NoFall), SoundManager de TaCZ (si
│                             présent) — refmap écrite à la main (noms SRG)
├── ban/
│   ├── BanManager            Bans compte (vanilla) + IP (vanilla) + PC, historique
│   ├── PlayerRegistry        IP / PC connus par joueur, comptes liés, purge RGPD
│   ├── BanCommands           /sban /sunban /sbaninfo /unbanplayer (+ AliasSanctions : /banpc /unbanpc /untempban)
│   └── Durations             30m, 12h, 7d, 2w, def
├── Client/ClientEvents       Côté joueur : handshake + intégrité (agents, threads)
├── Client/ClientScanService  Côté joueur : exécute les contrôles demandés, hors du thread du jeu
├── Networks/                 Canal "spartan:main", protocole "5"
│   ├── PacketUtil            Lecture bornée (anti-crash)
│   ├── TestPacket            Identifiant matériel
│   ├── ModsPacket            Liste des mods
│   ├── ClientReportPacket    Signalements d'intégrité
│   ├── BuildPacket, ChallengePacket, ChallengeResponsePacket   Défi tournant
│   ├── LaunchTokenPacket     Jeton du launcher + empreinte des mods
│   ├── ScanRequestPacket, ScanChunkPacket   Contrôle d'intégrité (demande / rapport en morceaux)
│   └── ResourcePackPacket, RequestModsPacket
├── util/AsyncIO              Écritures disque hors du thread du jeu
├── util/JsonFiles            Écriture atomique (jamais de fichier à moitié écrit)
├── util/PacketGuard          Anti-spam de paquets
├── util/ServerNet            Téléportation en attente côté vanilla (destination absolue)
└── Utils/                    HWID (OSHI/JNA), utilitaires
```

Tests unitaires : `./gradlew test` — lignes de vue (murs, portes, vitres, angle de
mur anticipé, pas de vision à travers un mur collé, banc d'essai d'une ville de 70
joueurs), jeton du launcher, empreinte des mods,
durées, modèles de mouvement (joueur vanilla simulé tick par tick jamais signalé :
sprint, sauts, glace, plafond bas, chutes, rebonds, reculs, explosions, paquets
retardés ou regroupés ; Speed, bunny-hop, planer, vol, saut en l'air, NoSlow
signalés), horloge par pings (client vanilla simulé image par image avec gels et
réseau instable : jamais signalé ; jeu accéléré signalé), et injection de données
aléatoires dans chaque décodeur de paquet Spartan (aucun plantage, aucun blocage).
Code launcher de référence : `docs/launcher/`.

**Ajouter une détection :** créer une classe qui étend `MovementCheck`,
`CombatCheck` ou `BlockCheck`, appeler `flag(joueur, détails)` quand c'est
suspect, et l'enregistrer dans `Spartan.java` avec `CheckManager.register(...)`.

## 9. Notes importantes

- **Changer le format d'un paquet impose de monter `PROTOCOL_VERSION`**
  (`ModMessages.java`, actuellement `"5"`), sinon client et serveur de versions
  différentes se déconnectent avec des erreurs peu claires.
- Les identifiants matériels et les IP sont des **données personnelles** (RGPD) :
  les mentionner dans le règlement du serveur et ne pas les conserver
  indéfiniment pour les joueurs non sanctionnés.
- Le contrôle d'intégrité collecte des données techniques du **processus du jeu**
  (fichiers et modules chargés, configs Mixin, noms de threads, DLL chargées dans
  le jeu avec un emplacement anonymisé) et le nom des programmes ouverts **qui
  figurent dans la liste de triche** (jamais la liste complète). Pas d'IP sur
  Discord, pas de nom d'utilisateur Windows dans les chemins. À mentionner dans le
  règlement du serveur.
- Aucun anti-cheat n'est infranchissable : le code client tourne sur une machine
  que le tricheur contrôle. Le but est d'empiler les barrières et de compter sur
  le filet serveur pour attraper ceux qui passent.

## 10. Historique

- **2.1.0 (HeraliZ)** — **Anti-duplication** (section `[antidupe]`). **Jeton du launcher HeraliZ** (6 h) et `tokenRequired` qui refuse la connexion sans jeton, même en détection silencieuse.
- **2.0.0 (HeraliZ)** — **Coupé client / serveur** : le jar des joueurs ne contient plus aucune détection serveur ;
  le jar serveur embarque une copie du jar joueur pour les défis. **Défi lié au code réellement chargé** : en plus des
  octets des classes, chaque réponse mêle la forme vivante de chaque classe (champs, méthodes, constructeurs vus par
  réflexion dans le jeu) ; le serveur la compare à celle lue dans sa copie : un Spartan neutralisé en mémoire (Mixin,
  ASM, agent) répond faux, même avec un jar intact gardé de côté. Protocole `"7"`. **Modpack officiel automatique**
  (jars, configs Mixin, empreinte du dossier) : jars et configs Mixin inconnus relevés sans référence apprise.
  **Injection native** : threads démarrés hors de tout module et crochets sur l'affichage (testé sur Windows avec une
  injection simulée : repérée ; JVM propre : aucune trace). **Salon Discord des sanctions** (pseudo, durée, raison).
  Branchements HeraliZ : voix de CaraCrea (à la place de Simple Voice Chat), alliés (équipes de match, zombies, clans).
  Forge 47.4.0 comme le modpack (`EventPriority.MONITOR`, absent de l'eventbus 6.0.5 de nos Forge, remplacé).
  Config dans le dépôt (`serveur/config/spartan-common.toml`), secrets injectés par GitHub.

- **1.8.0** — **Mouvement : physique de vanilla tick par tick.**
  Enveloppes horizontale et verticale recalculées à chaque position (Speed, Fly,
  Vertical), sans accumulation d'élan possible ; reculs et explosions **intégrés
  avec leur valeur exacte** au lieu d'exempter le joueur. **Transactions**
  (ping/pong par tick) : reculs, explosions et changements d'état suivis exactement
  malgré le lag ; Timer borné par l'horloge du serveur ; nouvelle détection
  **Velocity** (anti-recul). Nouvelles détections **NoSlow** et correction
  **NoFall** (Mixin). Validé en jeu derrière un proxy qui simule un réseau instable
  (gels de 0,2 à 0,7 s, gigue) : sprint, sauts, escaliers, dalles, chutes, reculs et
  explosions sans fausse alerte ; Speed, bunny-hop, planer, vol stationnaire, saut
  en l'air, NoSlow, NoFall, anti-recul et Timer ×2 / ×1,1 détectés.
  **Anti-ESP** (section 6). **Défi d'intégrité** : chaque défi vérifie désormais
  toutes les classes du mod (avant : 3 à 15 sur ~170), protocole `"6"`.

- **1.7.0** — **Contrôle d'intégrité silencieux** (repérer JAR de cheat et
  clients injectés avant même qu'ils servent) : à la connexion puis à intervalle
  aléatoire, le jeu envoie ce qu'il a réellement chargé ; comparaison au client de
  référence appris (`/spartan reference learn`), indices expliqués, alerte Discord
  avec le rapport complet en pièce jointe, commandes `/spartan scan | report |
  suspects | selfscan`. **Détection silencieuse** par défaut
  (`silentClientDetections`) : plus aucune expulsion pour un signal venant du jeu
  du joueur. Protocole `"5"`. Validé avec un vrai client : client propre sans
  alerte ; injection en cours de partie, agent avec code injecté, JAR inconnu et
  mod ESP à base de Mixin tous détectés, sans expulsion.
- **1.6.0** — Blocage des packs de texture (ResourcePack) et des shaders
  (Shaders, détection Iris/Oculus/OptiFine) custom ; durcissement anti-injection
  (le launcher ajoute `-XX:+DisableAttachMechanism`, le mod le vérifie et peut
  l'exiger). Correctifs de la 2e revue de sécurité : exemptions de Speed
  resserrées (recul réel via événement, mobs exclus, item de vol rangé n'exempte
  plus), HWID « unknown » sous Linux/Mac au lieu d'un hash commun, texte client
  neutralisé avant Discord / console / logs (anti-injection de markdown et
  d'ANSI), limiteur et journalisation des erreurs Discord, preuves de mouvement
  réservées aux détections serveur, écritures du registre regroupées, purge RGPD
  périodique, nettoyage des IP/identifiants hérités, bans sensibles à la casse,
  garde `hasDisconnected` sur tous les paquets.
  Correctifs de la 3e revue : Fly et Speed mesurés sur la position **acceptée du
  client** (pendant le tick, le serveur simule la physique du joueur : un joueur
  qui plane bas pouvait avoir une hitbox simulée posée au sol) ; Fly ignore les
  ticks sans paquet (lag) et détecte l'immobilité en l'air ; zone non chargée et
  changement de dimension ignorés ; Speed divisé par le temps réel entre deux
  envois. Téléportations : l'ancienne heuristique « saut de plus de 8 blocs =
  téléportation du serveur » (exploitable, vanilla accepte ~10 blocs par paquet)
  est remplacée par la téléportation **en attente de confirmation** du serveur,
  sans fenêtre de grâce (perle, chorus, waystones, corrections vanilla).
  Renvoi : effectif côté serveur dans le même tick, zone chargée et place libre
  vérifiées, dépôt au sol en secours. Défi d'intégrité : horloge monotone, délai
  compté aussi en ticks serveur (un serveur figé n'expulse personne), réponse
  juste mais tardive acceptée. Boîte noire : position du client.
  **Moteur de mouvement par paquets** (4e revue) : chaque paquet de mouvement
  est analysé tel qu'envoyé, téléportations suivies par identifiant jusqu'à leur
  confirmation (les preuves ne sont jamais effacées par une correction), fenêtres
  en temps réel (Fly, Speed), nouvelles détections Vertical, NoFall, Timer et
  BadPackets, exemptions tolérant la latence, élan physique après élytre / trident
  / recul, gravité des mods, équipements Curios, parachutes et planeurs qui ne
  font plus qu'assouplir la règle (les jetpacks se déclarent par identifiant).
  Renvoi ~1 s en arrière sur des positions réellement occupées, jamais dans la
  lave ou un fluide. Défi d'intégrité : un seul retard n'est plus signalé, build
  différent signalé au staff quand buildCheck=false. Boîte noire : trajectoire
  du client, sol déclaré et réel, téléportations marquées. Commandes
  `/spartan status` (suivi du mouvement) et `/spartan setback`. Validé sur un
  vrai client : immobile, chutes analysées de 20 et 60 blocs, explosions,
  lévitation, bateau, mort / réapparition, Nether — aucune violation.
  Détection d'injection démontrée avec un agent Java inoffensif.
- **1.5.0** — Fly compatible parachutes (descente nette sur 1 s, mots-clés
  d'items) ; **correction instantanée** (renvoi au sol, coups et casses annulés
  + verrous, effets illégaux retirés) ; **défi d'intégrité tournant** et
  vérification de build ; **jeton du launcher** + empreinte du dossier mods
  (code de référence JS/Java, vérifiés l'un contre l'autre) ; tests unitaires ;
  protocole `"4"` ; commande `/spartan status`. Testé de bout en bout avec un
  vrai client Minecraft (défis réussis, jeton valide accepté, jeton absent et
  mod ajouté après le launcher détectés).
  Correctifs de la 2e revue de sécurité :
  - **Docker / Pterodactyl** : les IP non publiques (passerelle 172.x, 10.x,
    NAT d'opérateur…) ne sont plus jamais bannies ni comparées ;
  - **cartes mères génériques** : les UUID de remplissage (03000200-0400-…)
    sont ignorés (sinon un ban toucherait des milliers de PC) ;
  - **machines de mods** (faux joueurs : déployeur Create…) ignorées ;
  - **serveur qui rame** : Speed et FastBreak mesurent en temps réel,
    AutoClicker et Nuker s'abstiennent ; KillAura attend la rotation à jour
    et une série de 3 coups ; recul et plateformes mobiles exemptés pour Speed ;
  - effets : seuls les effets bénéfiques sont examinés ;
  - bans : seule la dernière IP publique est bannie, IPv6 géré, un re-ban
    remplace l'ancien, un déban ne lève plus l'IP d'un autre banni, les bans
    de la 1.0 se lèvent par leur identifiant PC, pseudos entre guillemets,
    distinction « Steve » / « steve » (comptes différents en crack), un
    modérateur ne peut pas bannir un admin depuis le jeu, purge RGPD étendue
    aux bans terminés, fichier illisible mis de côté au lieu d'être écrasé,
    écritures vidées à l'arrêt du serveur, « 30s » refusé (ambigu).
- **1.4.1** — Compatibilité modpack : Reach utilise la portée réelle (armes
  longues de mods), Speed la vitesse réelle (équipements de mods), Nuker ignore
  le minage en chaîne (FTB Ultimine & co).
- **1.4.0** — Détections FastBreak (speedmine) et IllegalEffect (effets
  impossibles).
- **1.3.0** — Détections Speed, Reach, KillAura, AutoClicker, Nuker ; handshake
  obligatoire ; signalement d'intégrité client (agents Java, threads anonymes)
  remontés au staff au lieu de fermer le jeu en silence ; commande `/spartan`.
  **Système de ban complet** (compte + IP + PC, durée, hors ligne, historique,
  détection des comptes secondaires) ; `/unbanplayer` réparé ; migration des
  bans de la 1.0 ; purge RGPD des données.
- **1.2.0** — Durcissement après revue de sécurité : faille de crash serveur
  corrigée (paquets bornés), fuite de la config vers les clients corrigée
  (`SERVER` → `COMMON`), anti-spam de paquets, VL avec décroissance, cooldown
  d'alertes, I/O hors du thread du jeu, Fly fiabilisé, bug « unknown » corrigé.
- **1.1.0** — Capture d'écran supprimée ; moteur de détection serveur ; Fly ;
  boîte noire ; alertes staff/log/Discord ; config ; mode alerte.
- **1.0.0** — Code source reconstruit à l'identique depuis le jar d'origine.
