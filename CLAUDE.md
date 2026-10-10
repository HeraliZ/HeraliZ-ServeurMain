# HeraliZ — dépôt du serveur

Ce dépôt contient **tout le serveur Minecraft HeraliZ** : les sources de nos mods, les mods des autres auteurs, les
plugins, les configs, les gunpacks TaCZ et les outils. Deux personnes y travaillent, chacune avec sa session Claude.
Ce fichier est lu automatiquement par Claude au début de chaque session : il contient les règles, la méthode et tout
ce qu'il faut savoir du projet. **Le lire en entier avant toute modification.**

Les propriétaires ne sont pas développeurs : leur répondre en français simple, sans jargon, avec des questions courtes
quand un choix leur revient.

---

## 0. Règles d'or

1. **On modifie toujours les sources** (`mods/<projet>/src/...`), puis on refabrique les jars. Jamais de retouche à la
   main dans un jar, jamais un jar « bricolé » déposé sur le serveur.
2. **Avant toute demande : récupérer la dernière version** (`git pull --rebase`). **Après : fabriquer, vérifier,
   enregistrer, envoyer** (section 7). L'autre personne doit retrouver le travail à jour à sa prochaine demande.
3. **Un changement = un commit**, avec un message clair en français (« Boss : Ymirak frappe 20 % moins fort »).
   Jamais de `git push --force`, jamais de réécriture de l'historique.
4. **Aucun prénom, ancien pseudo, adresse mail, chemin de PC, nom de compte ou identifiant de session** dans le code,
   les fichiers, les commentaires ou les messages de commit. L'auteur de nos mods est toujours **HeraliZ**.
   `python outils/verifier.py` le contrôle (liste locale `outils/noms_interdits.txt`, hors Git, à demander au
   propriétaire).
5. **Jamais notre nom sur le code des autres** (liste section 3.2). On ne retire pas non plus leurs crédits ni leurs
   licences. Pour qu'un de ces mods devienne « à nous », on le refait nous-mêmes.
6. **Ne pas toucher** aux jars de `serveur/mods-tiers/` et `client/mods-tiers/` sans demande explicite.
7. **Aucun mot de passe, clé, jeton ou adresse de serveur dans le dépôt.** Les accès au Ptero sont dans les
   « Secrets » GitHub (section 8), jamais dans un fichier.
8. **Pas de test en jeu par Claude** : les propriétaires testent eux-mêmes. Chaque compte rendu dit clairement ce qui
   n'a PAS été testé en jeu.
9. **Contenu visuel nouveau** (modèle, arme, machine, interface importante) : d'abord des rendus à valider par le
   propriétaire, ensuite seulement le code et les jars.
10. **La carte (map) se fait hors de ce dépôt.** Quand le propriétaire fournit un mod, un pack de textures ou un monde
    modifié (jar + sources), on l'ajoute ici (section 9). La carte terminée est déposée telle quelle dans
    `monde/HeraliZ/` : on ne la retouche pas ici.

---

## 1. Le serveur en bref

- Minecraft **1.20.1**, Forge **47** ; sur le Ptero : **Mohist 1.20.1-916** (`mohist-1.20.1-916-server.jar`), Java 17.
- Joueurs en version crackée : `online-mode=false` + plugin **AuthMe** (comptes par mot de passe : `/register` à la
  première connexion puis `/login` ; BCRYPT, 8 caractères minimum, IP bloquée 30 min après 6 erreurs). À venir : le
  compte HeraliZ du site (Azuriom) dans le launcher, avec code par mail sur un nouveau PC ; AuthMe reste la 3e barrière.
- Plugins (triés le 08/10, seulement l'utile ; tout le reste a été retiré) : AuthMe, spark, LuckPerms (grades du staff,
  base locale), KauriVPN (anti-VPN), LiteBans (mutes, avertissements, historique ; les bans pour triche passent par
  Spartan), WorldEdit + WorldGuard + WorldGuardExtraFlags (outils du staff, protection du spawn), ConsoleSpamFix.
  Pas de plugin anti-duplication ni « ClearLag » (ils voient mal les mods / suppriment leurs entités).
- Armes : **TaCZ** (mod de base, jamais à notre nom) + nos gunpacks dans `serveur/tacz/`.
- Monnaie du serveur : les **hérals** (« 1 héral », « 1 500 hérals »). Identifiants internes inchangés
  (`heralisz_economie`, `EconomieApi`). Les objets du casino s'appellent « jetons ».
- Dimensions à part : `heralisz_matchs:arenes` (ClashWar, duels) et `heralisz_matchs:zombies`.
- Style de toutes les interfaces : celui des **écrans de clan** (panneaux de verre, police DM Sans, échelle 640×360,
  jamais de fond uni). Kit à copier : `python outils/copier_kit_ui.py mods/<projet>`. Exceptions : l'inventaire, la
  fouille du loot (coffres T1 à T7, caisses de ravitaillement, caisse de CRac) et l'inventaire des sacs à dos gardent
  leur style de base façon DayZ (choix du propriétaire : le style « clan » y fait trop moderne ; au plus une petite
  amélioration dans le même style), et les écrans à thème (machines du casino). Toutes les autres interfaces des mods
  passent au style des clans.
- Menu Échap (heraliz_interface, maquette validée) : à gauche le logo et le menu (Reprendre, Informations, Discord,
  Boutique, Options, Statistiques, Joueurs, Se déconnecter ; ni Mods ni Succès), à droite la carte du joueur (hérals,
  joueurs en ligne, ping, extractions ouvertes, zones du clan).
- Discord du serveur : https://discord.gg/heraliz (menu Échap et launcher). Noms de domaine heraliz.fr et heraliz.com :
  le site du serveur y sera mis plus tard (la boutique pourra y pointer).

---

## 2. Organisation du dépôt

```
CLAUDE.md                 ce fichier
docs/METHODE_CLIENT_SERVEUR.md   la méthode détaillée de séparation client / serveur (à lire avant de découper du code)
mods/<projet>/            les sources de NOS mods (un projet Gradle par jar)
mods/_gabarit/            modèle de projet coupé, pour créer un nouveau mod (outils/fusion_nouveau.py)
serveur/                  tout ce qui va sur le Ptero, en plus de nos jars :
  mods-tiers/             mods des autres auteurs (aussi chez les joueurs)
  plugins/                les plugins gardés (section 1) et leurs réglages, sans données ni secrets
  config/                 configs des mods
  defaultconfigs/         configs « serveur » des mods (copiées par Forge dans chaque nouveau monde)
  tacz/                   gunpacks TaCZ
  heralisz_matchs/, mohist-config/, server.properties, bukkit.yml, spigot.yml, commands.yml, eula.txt, server-icon.png
  mohist.sha256           empreinte du jar Mohist (le jar lui-même est dans la Release « base-serveur »)
client/mods-tiers/        mods des autres auteurs utiles aux joueurs seulement (EntityCulling)
launcher/                 le launcher des joueurs (Electron, Node.js) : `npm install` puis `npm start` dans ce dossier ;
                          doc dans launcher/README.md et launcher/docs/
outils/                   fabrication et contrôles (Python 3.11+)
monde/HeraliZ/            la carte de départ (monde neuf, jamais joué), voir section 9 ; jamais envoyée au Ptero
                          par la mise en ligne automatique
.github/workflows/        fabrication et mise en ligne automatiques
```

Le dépôt contient le serveur COMPLET : les 21 mods des autres auteurs + nos 22 mods (sources, fabriqués en 20 jars
`-serveur` pour le Ptero) = les 41 jars du dossier `mods/` du serveur, et les 44 jars des joueurs.

Jamais dans Git (voir `.gitignore`) :
- `build/`, `run/`, `dist/`, `livraison/` (refaits à chaque fabrication) ;
- **Mohist** (139 Mo, GitHub refuse les fichiers de plus de 100 Mo) : il est dans la **Release « base-serveur »** du
  dépôt (onglet Releases), contrôlé par `serveur/mohist.sha256`. Pour changer de version : nouvelle Release, nouvelle
  empreinte ;
- ses `libraries/` : Mohist les télécharge seul au premier démarrage ;
- le **monde en service** sur le Ptero (changé par les joueurs). Seule la carte de départ est dans le dépôt
  (`monde/HeraliZ/`, section 9) ;
- `ops.json`, `whitelist.json`, `usercache.json`, les bases de données des plugins (données des joueurs) ;
- `outils/chemins_local.json`, `outils/noms_interdits.txt` (propres à chaque PC).

---

## 3. Les mods

### 3.1 Nos mods (sources dans `mods/`)

Chaque mod « coupé » donne **3 jars** : `-serveur` (Ptero), `-client` (joueurs), `-complet` (jeu solo).

| Projet | Jar | modIds dedans | Rôle |
|---|---|---|---|
| heraliz-boss | heraliz_boss | heraliz_boss, heralisz_mammoth, heralisz_mere, heralisz_n07, heralisz_plague, heralisz_sylvorn | les 5 boss : Ymirak (mammouth), Mère des Ruines, Sujet N-07 (+ décors du Secteur 7, boss final de The Giant), Porteur de Peste, Sylvorn. Un onglet créatif avec un panneau par boss |
| heraliz-tactique | heraliz_tactique | heraliz_tactique, heralisz_tactical, heralisz_melee, heralisz_heal, heralisz_grenade, heralisz_camo | mêlée, soins, grenades, C4, camos et décorations d'armes. Moteur repris de LR Tactical (licence GPL gardée) |
| heraliz-clansetbases | heraliz_clansetbases | heraliz_clansetbases, heralisz_economie, heralisz_clans, heralisz_bases, heralisz_tags | hérals, clans, bunkers et pillage, tags |
| heralisz-utils | heralisz_utils | heralisz_utils, heraliz_corps, heraliz_ping, heraliz_usure, heraliz_vehicules | train (deux réseaux, choisi par monde : l'ancienne carte et la nouvelle, `train/reseau_v2.json`), ascenseur, carte, plans, ambiance, décors animés (moulins, éoliennes neuves ou à l'abandon, décanteurs), cadavres, roue de ping, usure des armes, véhicules |
| heralisz-matchs | heralisz_matchs | heralisz_matchs | matchs : moteur commun, ClashWar (tournoi de clans), Duels + killcam, Zombies (carte The Giant) |
| heraliz-casino | heralizcasino | heralizcasino | casino payé en hérals |
| heraliz-crates | heralizcrates | heralizcrates | caisses animées HD, clés, roue de butin, éditeur de butin |
| heraliz-loot | heraliz_loot | heraliz_loot | conteneurs de loot par tiers (T1 à T7 ; 68 modèles HD de plus depuis la 0.5.0 : 12 T3, 16 T4, 20 T5, 20 T6), rotation par zones : un conteneur posé ou remplacé à la main rejoint la zone du même palier à moins de 64 blocs, sinon crée la sienne (`/heralizloot proche`, `/heralizloot zone <id> part <pourcent>`) |
| heraliz-decors | heraliz_decors | heraliz_decors | décors de la nouvelle carte : grillage militaire qui se raccorde dans 8 directions (`grillage`, `grillage_haut`), blocs du désert et de l'arctique, taches de sang, lampes et guirlandes, grande cascade et rayons de soleil, eau animée ; brouillard dynamique (`/brume`) et zones dangereuses gaz / radiation / froid (`/zones`), lus dans le monde (`data/heraliz_brume.json`, `data/heraliz_zones.json`). Textures et modèles refaits par `python mods/heraliz-decors/outils/gen_assets.py` |
| heraliz-armory | heraliz_armory | heraliz_armory | armures, ghillies, brassards, masques à gaz, combinaisons |
| heraliz-wonder | heraliz_wonder | heraliz_wonder | Wonder Weapons façon CoD (zombies) |
| heralisz-backpack | heralisz_backpack | backpacked | sacs à dos (Backpacked de MrCrayfish, modifié, licence LGPL gardée) |
| heralisz-bunker | heralisz_bunker | heralisz_bunker | portes animées (reprises de HBM Modernized, GPL), cartes d'accès, barricades |
| reanimation | reanimation | reanimation | joueurs à terre, réanimation, fouille, exécutions |
| airstrike | airstrike | airstrike | jumelles et frappes aériennes |
| caracrea | caracrea | caracrea | créateur de personnage + chat vocal de proximité (UDP) |
| heraliz-emote | heraliz_emote | heraliz_emote | emotes (Emotecraft et Player Animator de KosmX embarqués, licences dans le jar) |
| crac | crac | crac | construction façon Rust (fondations, murs, armoire à outils, raids) |
| heraliz-interface | heraliz_interface | heraliz_interface | **joueurs seulement** : menu Échap, pack de textures du serveur imposé |
| heraliz-extraction | heraliz_extraction | heraliz_extraction | extraction : terminal (PC + fumigène, zone de 5 min, 1:30 à tenir à ciel ouvert, retour au spawn), zones fixes de la carte ouvertes à tour de rôle (3 à la fois, 30 à 60 min, 30 s à tenir ; `/extraction`, `/extractions` pour les joueurs) |
| dayzhotbar | dayzhotbar | dayzhotbar | **joueurs seulement** : barre d'objets façon DayZ |
| spartan | spartan | spartan | **anticheat** : détections serveur, anti-ESP, défis d'intégrité, contrôle du jeu des joueurs (jars, Mixins, injection), bans compte + IP + PC, alertes Discord. Voir section 6 bis et `mods/spartan/README.md` |

Les modIds ne changent JAMAIS (mondes, configs, gunpacks et autres mods en dépendent). Même règle pour les noms de
classes et les signatures des API (section 5).

### 3.2 Mods des autres auteurs (`serveur/mods-tiers/`, `client/mods-tiers/`)

Jamais notre nom dessus, jamais de retouche sans demande. Les sources ne sont pas dans le dépôt.

TaCZ (Serene Wave Studio) et TaCZ Addon (Mafuyu404) ; cfm « PATCH » (Furniture Mod de MrCrayfish, avec nos props
dedans) ; Framework (MrCrayfish) ; geckolib222 (GeckoLib de l'équipe Gecko, avec du contenu ArkaniaZ dedans : c'est
le SEUL GeckoLib du serveur) ; ywzj_vehicle « fuse » (YWZJ, moteur des véhicules) ; Simple Farming « heralisz1 »
(enemeez) ; Mutants & Zombies (Pete MC Mods) ; Zombie Awareness et CoroUtil (Corosus) ; MAtmos et TCTcore (The_TCT) ;
Passable Foliage et Kiwi (Snownee) ; Curios (TheIllusiveC4) ; Cloth Config (shedaniel) ; Player Animation Lib (KosmX) ;
ModernFix, FerriteCore, MemoryLeakFix, PacketFixer (optimisation) ; EntityCulling (joueurs seulement).

Les versions modifiées (cfm PATCH, geckolib222, ywzj fuse, simplefarming heralisz1) ont été retouchées directement
dans les jars : il n'existe pas de sources.

---

## 4. Modifier un mod : la séparation client / serveur

Nos mods sont coupés en deux pour que le code qui « décide » ne quitte jamais le serveur : les joueurs n'ont que
l'affichage. Chaque projet a **trois dossiers de sources** :

| Dossier | Ce qui y va | Jar |
|---|---|---|
| `src/commun` | enregistrements (blocs, objets, entités, sons), configs, paquets réseau, état synchronisé que lit l'affichage (`EntityDataAccessor`, état d'une entité de bloc), API lues des deux côtés | les trois |
| `src/serveur` | ce qui décide : IA, attaques, dégâts, phases, butin, règles, commandes, sauvegardes, traitement des paquets reçus des joueurs, `data/` | `-serveur`, `-complet` |
| `src/client` | l'affichage : rendus, modèles, HUD, écrans, sons d'ambiance, musiques, effets, mixins client, `assets/` | `-client`, `-complet` |

Le compilateur l'impose : `commun` ne voit ni `client` ni `serveur`, et ces deux-là ne se voient pas.

**Où toucher :**
- changer une attaque, des dégâts, du butin, une règle → `src/serveur` (seul le jar `-serveur` change ; les joueurs
  n'ont rien à retélécharger) ;
- changer un modèle, une texture, un effet, le HUD → `src/client` (nouveau jar `-client` pour les joueurs) ;
- ajouter un objet, un bloc, une entité, un paquet ou une donnée synchronisée → `src/commun` (**les deux** jars).
- les camos des armes (tactique) : les 62 shaders `camo_<id>.fsh` sont **générés** depuis
  `mods/heraliz-tactique/outils/camo_gabarit.fsh` par `python mods/heraliz-tactique/outils/generer_camos.py` ; on modifie
  le gabarit, jamais les fichiers générés.
- la portée du son des silencieux TaCZ (gunpacks de `serveur/tacz/`) : tableau `PORTEES` de `outils/silencieux.py`, puis
  `python outils/silencieux.py` (jamais à la main dans les fichiers des silencieux ; à relancer si la portée d'un tir
  normal change dans `serveur/config/tacz-common.toml`). Un tir silencieux part aussi deux fois moins fort pour les autres
  joueurs (mixin commun `TaczSonSilencieuxMixin` du mod tactique).
- les icônes d'inventaire des armes TaCZ sont des photos 3D faites chez le joueur (`camo/client/IconesArmes` du mod
  tactique : modèle + skin, sans accessoires, 64 au plus en mémoire graphique, rien sur le disque ni le serveur) ; l'image
  `slot` du gunpack ne s'affiche qu'en attendant la photo ou si elle est impossible.
- l'écran de modification des armes TaCZ est remplacé chez le joueur par l'**Atelier d'arme** (`camo/client/atelier` du
  mod tactique, sous-classe de `GunRefitScreen` pour que TaCZ le reconnaisse) : onglets Accessoires, Camo, Personnaliser.
  Poses et retraits par les paquets de TaCZ, camos et stickers par les nôtres. S'il plante, l'écran de TaCZ (avec les
  anciens onglets Skin et Personnaliser) revient jusqu'au redémarrage du jeu. Les accessoires rangés dans un coffre
  proche (fonction de TaCZ Addon) n'y sont pas proposés : seulement ceux de l'inventaire.
- la table de fabrication TaCZ est remplacée de même par la **Table d'armurier** (`camo/client/atelier/TableArmurier`) :
  elle pilote l'écran de TaCZ au lieu de le refaire (recettes classées et matériaux comptés par TaCZ, son bouton de
  fabrication caché pressé par le nôtre, accès par `mixin/GunSmithTableScreenAccessor`) : TaCZ Addon garde les matériaux
  des coffres proches et Maj + clic pour fabriquer en série.
- le camo d'une arme est l'étiquette NBT `HeraliszCamo` ; un camo à vie porte en plus `HeraliszCamoProprio` et
  `HeraliszCamoJeton` (`fr.heralisz.camo.CamoData`). Un mod qui pose lui-même un camo sur une arme qui peut porter un
  camo à vie doit retirer ces deux clés, sinon le serveur la prend pour un camo à vie périmé et lui enlève son camo
  (pas le cas aujourd'hui : le Pack-a-Punch et Wonder ne touchent qu'aux armes prêtées des matchs, où les camos à vie
  sont refusés).

**Les briques à connaître** (paquet `pont` de chaque projet, détails dans `docs/METHODE_CLIENT_SERVEUR.md`) :
- `Parties.serveur(...)` / `Parties.client(...)` à la fin du constructeur `@Mod` : chargent `serveur.PartieServeur` /
  `client.PartieClient` si elles sont dans le jar ;
- une classe qui a de la logique est coupée en `X` (commun) + `XServeur` (serveur, même paquet). On la crée par son
  type (`TYPE.get().create(level)`) ou son usine (`UsineEntite`, `UsineBlocEntite`, `Usine`), **jamais par `new X(...)`**
  (on obtiendrait la version sans logique) ;
- `Crochet` : point d'accroche que l'autre côté branche (paquets reçus, rendus d'objets, tickers client) ;
- façades communes branchées par le serveur : `pont/Services` (Matchs), `pont/Serveur` (CRac) ; sans partie serveur,
  elles rendent un résultat neutre.

**Les pièges les plus courants** (liste complète dans la doc, §9, §15, §16) :
1. `@Mod.EventBusSubscriber` sans `modid` dans un jar à plusieurs mods : écouteur appelé plusieurs fois.
2. `new X(...)` d'une classe qui a une version `XServeur`.
3. `EntityDataAccessor` déclaré ailleurs que dans la classe commune : désynchronisation.
4. `DistExecutor` ou `Minecraft.getInstance()` dans `src/commun` : interdit, passer par un `Crochet`.
5. Un fichier de `assets/` lu par le serveur (`getResourceAsStream`) : le mettre dans `src/commun/resources`.
6. Une constante d'une classe serveur lue par le client : la déclarer dans une classe commune.
7. Ordre des groupes d'un onglet créatif : les déclarer dans le mod porteur (FML construit les mods en parallèle).
8. GeckoLib : `compileOnly` contre `libs/geckolib222-...jar`, **jamais embarqué** (sinon tout le contenu ArkaniaZ
   disparaît).

---

## 5. Relier les mods entre eux

Nos mods se parlent par des API stables. **Ne jamais renommer ces classes ni changer leurs signatures** : d'autres mods
les appellent, parfois par leur nom (réflexion, `Class.forName("...")`), ce que le compilateur ne voit pas.

| API | Où (côté) | Pour quoi |
|---|---|---|
| `fr.heralisz.economie.api.EconomieApi` | clansetbases (commun) | soldes en hérals, paiements |
| `fr.heralisz.clans.api.ClansApi`, `ClanInfo`, événements `ClanDissousEvent` / `MembreQuitteEvent` | clansetbases (commun) | clan d'un joueur, membres, droits |
| `fr.heralisz.bases.api.BasesApi`, `PillageHooks` | clansetbases (serveur) | bunkers, pillage |
| `fr.heralisz.matchs.moteur.api.MatchApi` | matchs (commun) | `partieDe(ServerPlayer)`, `partie(id)`, en match ou pas |
| `fr.heralisz.matchs.moteur.api.Partie`, `Equipe`, `CombatTag` (`restantMs(UUID)`), `Coffre`, `Arenes`, `Kit` | matchs (commun) | parties, équipes, tag de combat, coffre de match, arènes |
| `fr.heralisz.matchs.zombies.jeu.api.ZombiesApi` | matchs (commun) | `TAG_PARTIE`, `partieDe(Entity)`, `planches(...)`, `interagir(...)`, Pack-a-Punch, sons |
| `fr.heralisz.matchs.zombies.jeu.api.HudZombies.actif()` | matchs (commun, lu par le client) | HUD zombies affiché |
| `fr.heralisz.matchs.moteur.client.MoteurClient.enMatch()`, `combatRestantMs()` | matchs (client) | états du joueur côté client |
| `fr.heralisz.n07.api.N07Api` | boss (serveur) | boss final de The Giant (utilisé par Matchs) |
| `fr.heraliz.reanimation.core.Downed.isDowned(Entity)`, `DownedManager` | reanimation (commun) | joueur à terre |
| `fr.heraliz.reanimation.execution.Executions` | reanimation (serveur) | exécutions |
| `com.caracrea.api.voice.CaraCreaVoice` | caracrea (serveur) | voix : couper un joueur, portée, routage |
| `fr.heralisz.tactical.api.HeraliszTacticalAPI` | tactique (commun) | index des objets tactiques |
| `com.heraliz.extraction.api.ExtractionApi` | extraction (commun, rempli chez le joueur) | extractions ouvertes (`ouvertes()`, `version()`) pour la carte du monde |
| `fr.heraliz.loot.client.RaretesClient.couleur(ItemStack)` | loot (client, lu par réflexion par dayzhotbar) | couleur de rareté d'un objet (0 : aucune) |
| données persistantes du joueur `PlayerPersisted.HeralizBonusLoot.<source>` | lues par loot (serveur), écrites par clansetbases (`clan`) et utils (`objet`) | bonus de loot : `{Pct, Fin (heure en ms)}` ou `{Pct, Restant (ticks de jeu)}`, sources additionnées, plafond 25 % (`fr.heraliz.loot.BonusLoot`) |

**Pour qu'un nouveau mod utilise un des nôtres :**
- dépendance obligatoire : la déclarer dans son `mods.toml` et compiler contre une copie du jar dans son `libs/`
  (`compileOnly fg.deobf("blank:<nom>:<version>-<suffixe du projet>")`, suffixe unique par projet) ;
- dépendance facultative : appel par réflexion, en tolérant l'absence du mod (exemples : `Busy` de CaraCrea,
  `Regles` de Wonder) ;
- appeler une API **du bon côté** : une API rangée dans `src/serveur` n'existe pas dans le jar des joueurs.
Après un changement d'API : `python outils/verif_dependants.py` (doc dans le fichier) sur les jars des mods qui
l'appellent.

---

## 6. Fabriquer, vérifier, livrer

**Une fois par PC :**
- JDK 17, Python 3.11 ou plus, Git ;
- un profil CurseForge Forge 1.20.1 lancé au moins une fois (les contrôles lisent Minecraft dans le dossier `Install`) ;
- une copie du dossier `libraries/` du serveur (par SFTP depuis le Ptero) dans un dossier local ;
- `outils/chemins_local.json` (modèle dans `outils/chemins.py`) et `outils/noms_interdits.txt` (demander la liste au
  propriétaire). Ces deux fichiers ne vont jamais dans Git.

**À chaque modification :**
```
python outils/construire.py heraliz-boss      fabrique ce mod (sans nom : tous les mods) et range les jars dans livraison/
python outils/verifier.py heraliz-boss        tous les contrôles (0 problème exigé avant d'envoyer)
```
Les contrôles :
- `verif_split` : séparation client / serveur correcte (rien de client dans `-serveur`, rien qui manque d'un côté) ;
- `check_geckolib_api` : compatible geckolib222 4.4.7, aucun GeckoLib embarqué ;
- `verif_reobf` : noms de Minecraft réobfusqués (sinon plantage au chargement) ; chaque nom est cherché dans
  Minecraft + Forge du lanceur CurseForge ;
- noms perso : aucun mot interdit dans les sources ni les jars ;
- silencieux : chaque silencieux des gunpacks a sa portée de son (`outils/silencieux.py`).

**Où va quoi** (dossier `livraison/`, recréé à chaque fois) :
- `mods serveur/` → `mods/` du Ptero ;
- `mods client/` → `mods/` des joueurs ;
- `mods solo/` → jeu solo et tests ;
- `plugins/` → `plugins/` du Ptero.
Ne jamais laisser deux versions du même mod dans un dossier `mods/`.

---

## 6 bis. L'anticheat Spartan

- **Deux jars de la MÊME fabrication** : `-serveur` sur le Ptero, `-client` chez les joueurs. Le jar serveur contient une
  copie du jar joueur (`META-INF/spartan/client.bin`) et calcule dessus les réponses attendues aux défis : un joueur
  avec un autre jar joueur est signalé. Après toute modification de Spartan, redonner le nouveau jar joueur.
- Le jar joueur ne contient AUCUNE détection serveur, aucun seuil, aucune signature : ne jamais déplacer de code de
  détection vers `src/commun` ou `src/client`.
- **Modpack officiel automatique** : `outils/construire.py` écrit `livraison/spartan/officiel.json` (empreinte de chaque
  jar des joueurs, configs Mixin connues, empreinte du dossier) ; la mise en ligne le dépose dans
  `config/spartan/officiel.json`. Tout jar ou toute config Mixin en plus chez un joueur est signalé. Rien à apprendre
  à la main ; `/spartan officiel` relit le fichier sans redémarrer.
- **Détection silencieuse** (choix du propriétaire) : ce qui vient du jeu du joueur n'expulse jamais, le staff reçoit
  l'alerte sur Discord avec le rapport et décide (`/sban`). `alertOnly = true` les premières semaines : régler les
  seuils sur les vraies alertes avant toute sanction automatique.
- Réglages : `serveur/config/spartan-common.toml` (fait foi). Webhooks et secret du launcher : JAMAIS dans le dépôt,
  marques `__SPARTAN_…__` remplacées par les secrets GitHub à l'envoi (section 8).
- Branchements HeraliZ (`mods/spartan/src/serveur/java/com/Spartan/serveur/compat/HeralizCompat.java`, par réflexion,
  sans dépendance) : alliés de l'anti-ESP = même équipe de match, même partie de zombies, sinon même clan ; voix de
  CaraCrea (un joueur caché qui parle est montré à ceux qui l'entendent). Si une de ces API change, ce fichier aussi.
- **Launcher** (`launcher/`, Helios) : il charge les mods depuis son dossier (`common/modstore`, fournisseur Forge
  « maven libs », options `--fml.mavenRoots` / `--fml.modLists`) ; Spartan hache et juge chacun de ces jars comme ceux
  du dossier mods. Le launcher ajoute `-XX:+DisableAttachMechanism` et retire les options d'injection du joueur
  (`launcher/app/assets/js/spartan.js`). Son pack doit être exactement `livraison/mods client` de la même fabrication.
- **Jeton du launcher** (on ne joue que depuis le launcher HeraliZ) : à chaque lancement, `spartan.js` fabrique un
  jeton signé, valable **6 h**, lié au pseudo et aux mods (`-Dspartan.launchToken=…`, masqué dans le journal du
  launcher). Clé : secret GitHub `SPARTAN_LAUNCHER_SECRET` (au moins 32 caractères aléatoires), injecté côté serveur
  par la mise en ligne et côté launcher par `launcher/outils/spartan-cle.js` au moment de fabriquer le launcher
  (`npm run dist:win` avec la variable d'environnement `SPARTAN_LAUNCHER_SECRET`) ; `spartan-cle.json` n'est JAMAIS
  dans Git. Réglage `tokenRequired` (`serveur/config/spartan-common.toml`) : `false` = alerte seulement ; `true` =
  connexion REFUSÉE sans jeton valide (CurseForge, autre launcher, jeton expiré), même en détection silencieuse. Ne le
  passer à `true` qu'une fois le launcher distribué à tous. Changer la clé à chaque nouvelle version du launcher.
  Vérifié : un jeton fabriqué par le launcher est accepté par `LaunchToken.verify` (Java) ; autre pseudo ou autre clé
  refusés.
- **Anti-duplication** (`check/item/AntiDupe`, section `[antidupe]`) : les objets de valeur NON empilables (`tacz:*`,
  `backpacked:*`, `heraliz_armory:*` par défaut) reçoivent un numéro unique invisible (donnée `spartan_uid`) quand ils
  sont rangés dans l'inventaire ; toutes les 20 s, un relevé en une fois (inventaires, souris, coffre de l'Ender,
  Curios, contenu des sacs) : même numéro à deux endroits = copie, alerte au staff avec preuve, rien de supprimé.
  Jamais numéroté : objet en main, porté, dans un sac ; joueurs en créatif ignorés. Un mod qui COPIE un objet suivi
  (au lieu de le déplacer) doit retirer `spartan_uid` de la copie, sinon fausse alerte.
- Un mod qui déplace un joueur doit passer par une vraie téléportation (`teleportTo`, `connection.teleport`) ou un recul
  (`setDeltaMovement` + `hurtMarked`) : Spartan les suit. Un `setPos` / `moveTo` seul sur un joueur peut être pris pour
  de la triche (cas connus : projection du Draugr, remontée de la Mère des Ruines : à surveiller dans les alertes).

---

## 7. Travailler à deux avec Git

Pour chaque demande, dans cet ordre :
1. `git pull --rebase` (récupère ce que l'autre a fait) ;
2. faire la modification dans les sources ;
3. `python outils/construire.py <projets touchés>` puis `python outils/verifier.py <projets touchés>` : 0 problème ;
4. `git add` des fichiers modifiés (jamais de `build/`, `livraison/`, fichiers locaux), `git commit -m "..."` ;
5. `git push`. S'il est refusé (l'autre a envoyé entre-temps) : `git pull --rebase`, régler les conflits en gardant
   les deux changements, refabriquer, revérifier, puis `git push`.

Règles :
- identité de chaque PC, dans ce dépôt : `git config user.name "HeraliZ"` et
  `git config user.email "heraliz@users.noreply.github.com"` ;
- petits commits ; un message par changement ; dire dans le message si les joueurs doivent mettre à jour leurs mods ;
- deux demandes en même temps, c'est possible : Git assemble les fichiers différents ; si le même endroit a été
  modifié des deux côtés, Claude assemble à la main et revérifie.

---

## 8. Mise en ligne automatique (GitHub Actions)

`.github/workflows/serveur.yml`, à chaque envoi sur la branche `main` :
1. fabrique les mods dont les sources ont changé (les autres jars sont repris du cache) et range les jars ;
2. lance les contrôles possibles sans Minecraft (GeckoLib, noms perso si le secret `NOMS_INTERDITS` existe) ; un
   échec arrête tout : rien n'est envoyé au Ptero ;
3. garde nos jars (paquets « mods-serveur » et « mods-client », 3 jours) à télécharger dans l'onglet Actions ;
4. **si les secrets du Ptero sont réglés**, il met à jour le serveur par SFTP :
   - `mods/` devient la copie exacte de `livraison/mods serveur` : les anciennes versions sont retirées ;
   - plugins : les jars du Ptero qui ne sont pas dans le dépôt sont SUPPRIMÉS un par un (leurs dossiers de données
     restent), ceux du dépôt sont envoyés avec leurs réglages (jamais de base de données) ;
   - `config/`, `tacz/`, `heralisz_matchs/` et `mohist-config/` sont complétés ou mis à jour, sans rien supprimer ; `config/spartan/officiel.json` est remplacé
     (les bans et preuves de Spartan, dans `config/spartan/`, ne sont jamais touchés) ;
   - `defaultconfigs/` est copié aussi dans le `serverconfig/` du monde : **les configs du dépôt font foi**, un réglage
     changé à la main sur le serveur est écrasé au prochain envoi ;
   - le monde, les données des joueurs, `ops.json` et `server.properties` ne sont jamais touchés (les fichiers de la
     racine du serveur se mettent à jour à la main) ;
5. **redémarrage seulement sur demande** : bouton « Run workflow » dans l'onglet Actions, case « redemarrer ».

**Installation complète** (serveur Ptero neuf ou à remettre à neuf) : « Run workflow » avec la case
« installation_complete » (et « redemarrer »). En plus de ce qui précède, il envoie Mohist (pris dans la Release
« base-serveur », empreinte contrôlée) et les fichiers de la racine : `server.properties`, `bukkit.yml`, `spigot.yml`,
`commands.yml`, `eula.txt`, `server-icon.png`. Il ne reste alors qu'à déposer le monde (dossier `HeraliZ` ; la carte de départ est dans `monde/HeraliZ/`) et à
démarrer. Attention : `server.properties` du dépôt remplace celui du serveur (port 25565 : vérifier que le panel
remet bien le port de l'allocation, sinon l'y changer).

Secrets à créer par le propriétaire (Settings → Secrets and variables → Actions) : `PTERO_SFTP_HOST`,
`PTERO_SFTP_PORT` (souvent 2022), `PTERO_SFTP_USER`, `PTERO_SFTP_PASSWORD`, et pour le redémarrage `PTERO_URL`,
`PTERO_SERVER_ID`, `PTERO_API_KEY` (clé « client » du compte Ptero), et `NOMS_INTERDITS` (le contenu de
`outils/noms_interdits.txt`, un mot par ligne). Pour Spartan : `SPARTAN_DISCORD_ALERTES` (webhook du salon PRIVÉ du
staff : alertes et rapports), `SPARTAN_DISCORD_SANCTIONS` (webhook du salon public des sanctions : pseudo, durée,
raison), `SPARTAN_LAUNCHER_SECRET` (quand le launcher signera les lancements). LiteBans : `LITEBANS_DB_USER` et
`LITEBANS_DB_PASSWORD` (base MySQL du Ptero, onglet « Databases » du panel) ; sans eux, LiteBans passe en base locale
(H2) et repart sans historique. Personne ne doit les écrire ailleurs.

Les contrôles `verif_split` et `verif_reobf` ont besoin de Minecraft en noms SRG : ils ne tournent que sur les PC
(section 6), pas sur GitHub. C'est pour ça qu'on vérifie AVANT d'envoyer.

---

## 9. Ajouter ce que le propriétaire fournit

- **Un de nos mods modifié ou nouveau (jar + sources)** :
  - s'il est déjà coupé (dossiers `src/commun`, `src/client`, `src/serveur`), le mettre dans `mods/<projet>` ;
  - sinon : `python outils/fusion_nouveau.py <dossier> <groupe> "<Nom>"` (nouveau projet depuis `mods/_gabarit`),
    `python outils/fusion_copier.py <sources reçues> mods/<dossier>`, puis la méthode (section 4 et la doc) ;
  - ne jamais mettre ses dossiers `build/`, `run/`, ni de README contenant des noms perso ;
  - mettre `LICENCE-HeraliZ.txt` (copie de `mods/_gabarit/src/commun/resources/`) dans ses ressources communes :
    `verifier.py` refuse un jar de nos mods sans cette mention de propriété (version « licence libre » pour un mod
    qui reprend du code GPL / LGPL, voir celles de CaraCrea, Bunker, Backpack, Tactique) ;
  - fabriquer, vérifier (noms perso compris), puis commit et push.
- **Un mod d'un autre auteur** : son jar dans `serveur/mods-tiers/` (ou `client/mods-tiers/` s'il ne sert qu'aux
  joueurs), en retirant l'ancienne version.
- **Un gunpack TaCZ (nouveau ou mis à jour)** : son dossier dans `serveur/tacz/`, puis `python outils/silencieux.py`
  (portée du son de ses silencieux, `verifier.py` le réclame) et `python outils/icones_sans_contour.py` (retire le
  contour noir de ses icônes d'inventaire). Ses `assets/` (modèles, sons, icônes) servent aussi aux joueurs : leur
  redonner le dossier `tacz` ; ses `data/` sont envoyées par le serveur.
- **Un pack de textures** : il est imposé par heraliz_interface
  (`mods/heraliz-interface/src/main/resources/resourcepacks/textures` et `.../herbe`) : remplacer le contenu du dossier
  par celui du nouveau pack, puis refabriquer heraliz_interface.
- **Le monde** : la **carte de départ** (monde neuf, jamais joué) est dans `monde/HeraliZ/` depuis le 11/10, sur
  décision du propriétaire. Version « finale v3 » : 64 régions, 259 Mo, apparition en -435, 66, 1118 ; son `level.dat`
  est en créatif, paisible, commandes permises (carte de construction : sur le serveur, c'est `server.properties` qui
  fixe le mode de jeu et la difficulté). Elle contient son pack de données (`datapacks/heraliz_carte` : zones de loot
  par lieu et par palier) et ses réglages (`data/heraliz_zones.json`, `data/heraliz_brume.json`,
  `data/heralisz_train.dat`).
  - Elle est fabriquée hors de ce dépôt (dossier de travail de la carte, chez le propriétaire) : on la remplace en
    entier quand il en fournit une nouvelle, on ne la modifie jamais ici. Chaque version envoyée ajoute sa taille au
    dépôt pour toujours : pas de nouvel envoi pour un petit changement.
  - Le **monde en service** sur le Ptero (changé par les joueurs) n'est jamais dans Git. La mise en ligne automatique
    ne dépose et n'écrase jamais de monde : mettre la carte sur le Ptero est une étape à part, décidée par le
    propriétaire (voir section 11, « Nouvelle carte » : trois mondes prévus, la carte n'est pas forcément le monde
    principal `HeraliZ`).
  - **La carte a besoin de trois de nos mods à ces versions au moins** (sans eux, leurs blocs disparaissent au
    chargement du monde et le train roule sur l'ancien tracé). Ils ont été apportés du dossier de travail de la carte
    le 11/10 ; les joueurs doivent avoir les jars `-client` de la même fabrication :
    - **HeraliZ Décors 1.7.0** (`mods/heraliz-decors`, nouveau) : grillage militaire, sable et blocs du désert, taches
      de sang, brouillard et zones dangereuses (gaz, radiation, froid) ;
    - **HeraliZ Utils** : apporté le 11/10 (version 2.2.0 : réseau de train de la nouvelle carte, gares droites,
      éoliennes à l'abandon `eolienne_usee` et `eolienne_moderne_usee`, ascenseur qui porte mieux les joueurs) ;
    - **HeraliZ Loot** : apporté le 11/10 (version 0.5.0 : les nouveaux conteneurs HD posés sur la carte, rotation du
      loot quand un conteneur est posé ou remplacé à la main).
    Ces trois mods continuent d'évoluer avec la carte, dans son dossier de travail : quand le propriétaire fournit
    une nouvelle carte, vérifier avec lui s'ils ont changé et les rapporter ici de la même façon (assembler avec ce
    qui a été fait ici entre-temps, fabriquer, vérifier). Rien de tout cela n'a été testé sur le serveur.

---

## 10. Décisions et préférences du propriétaire (à respecter)

**Cap général** : à terme, tous les mods du serveur seront les nôtres, avec leurs sources, reliés entre eux, au style
des écrans de clan, « full opti » (zéro lag, même sur les petits PC). On ne retire un mod tiers qu'une fois son
remplaçant prêt. Projets maison prévus : ambiance sonore (remplacera MAtmos et TCTcore), traverser les feuillages
(remplacera Passable Foliage et Kiwi), zombies de la survie (remplaceront Mutants & Zombies, Zombie Awareness,
CoroUtil), véhicules et parachute sans ywzj, carte du monde « AAA », optimisation des 5 boss,
« HeraliZ Décors » (le mod existe depuis le 11/10 avec les décors de la nouvelle carte ; il lui reste à reprendre
nos props sans le code de MrCrayfish, pour remplacer cfm).

**Fabrication** :
- GeckoLib = geckolib222 4.4.7 seul, jamais embarqué ;
- réponses des commandes en message direct au joueur ; les tests audio sont muets ;
- armes et modèles « niveau AAA » façon TaCZ : formes lisses, textures HD propres, cadrans lisibles sans scintillement,
  main placée comme TaCZ ;
- rien de nouveau dans les caisses ni en plans de craft : le loot et le craft seront refaits d'un bloc. En attente :
  les 7 armes de Fuse, les 4 véhicules, le jerrican, le parachute, les 7 sacs de Fuse.

**Boss** : gardés Ymirak, Mère des Ruines, Sujet N-07, Porteur de Peste, Sylvorn. Glacivore supprimé partout ;
Knight, Kraken et Vulkhar mis de côté. Le propriétaire trouve que les boss rament : les optimiser.

**Zombies (heralisz_matchs, `/zombies`)** :
- première carte The Giant (refonte de Der Riese, BO3), le plus fidèle possible ; ensuite Kino der Toten ;
- fin de carte : boss Sujet N-07 dans sa salle sous l'usine C (descente en monte-charge, intro passable, téléporteur
  de retour après sa mort, pas de récompense pour l'instant) ;
- HUD façon Black Ops 6 (barre de vie seulement après des dégâts, PV ×5, manche à la craie rouge, grosse annonce aux
  manches 5, 10, 15, 20 puis toutes les 10) ;
- dégâts des zombies qui montent avec les manches, plafond à la manche 20 ; zombies qui escaladent ; quête longue
  (fusibles, âmes…), objets de quête placés au hasard, guidage vers les objectifs, secrets cachés ; atouts mélangés à
  chaque partie ; camos du Pack-a-Punch réservés aux zombies ;
- sons d'origine fournis par le propriétaire : lui demander ses fichiers plutôt que d'en synthétiser.

**Autres choix** :
- CRac : aucun sac de couchage (retour à la base par la commande de téléportation, lits normaux) ; seuls les C4
  abîment les constructions ;
- casino : revente avec 5 % de commission, échanges d'objets coupés, entrée non payable avec le compte ;
- bonus de loot (HeraliZ Loot, au premier tirage d'un conteneur) : chaque objet a 10 % de chances d'être tiré une
  seconde fois (le plus rare est gardé) et les quantités montent de 10 % ; sources cumulables, plafond 25 % : membre
  dans une zone tenue par son clan, objet « Bonus de loot » de la boutique (`heralisz_utils:document_lootbonus`, 2 h de
  jeu décomptées seulement en ligne, gardé à la mort) ;
- raretés des objets (HeraliZ Loot, validées) : Commun gris, Peu commun vert, Rare bleu, Épique violet, Légendaire
  or, Unique rouge ; un léger dégradé de la couleur derrière l'objet et un trait fin en bas de la case, partout
  (inventaire, coffres, sacs, barre d'objets DayZ), infobulle « ★ Rare » (rien pour Commun). Réglage :
  `serveur/config/heraliz_loot/raretes.json` (à remplir avec la refonte du loot) ; le staff, objet en main :
  `/rarete <niveau|aucune|effacer|voir|defaut|recharger>` (choix en jeu dans `raretes_jeu.json`, jamais écrasé) ;
- extractions de la carte : 3 zones ouvertes à la fois, 30 à 60 min chacune, 30 s à tenir, retour au spawn ;
- camos à vie (pour la boutique) : clic droit sur un objet camo = il entre pour toujours dans la collection du joueur
  (objet consommé ; stockée hors du monde, `heralisz_camo/collections.json` à la racine du serveur, elle survit aux
  changements de carte). Onglet Skin : camos de l'inventaire (posés depuis l'objet : définitifs, comme avant) ou de la
  collection. Un camo de la collection ne vaut que sur une arme à la fois et seulement entre les mains de son
  propriétaire ; mort avec l'arme (pas à terre, pas en match) : il la quitte et ne se remet pas avant 15 min. Camos du
  Pack-a-Punch exclus. Staff : `/camos voir|ajouter|retirer|recharge <joueur> [camo|tous]` ;
- territoires des clans : 3 zones au plus par clan ; chaque zone tenue verse 100 hérals par heure à la banque du clan ;
  une ville vaut son niveau de loot (1 à 7, `/territoire valeur <zone> <1-7>`, clé « valeur » de zones.json) et
  rapporte au clan 10 XP par heure et par point de valeur (l'aérodrome T6 fait monter le clan plus vite dans le
  classement que Tcherno T3-T4) ; captures et temps tenu par ville sont gardés pour les statistiques du clan.
  Récompense à la capture : à décider plus tard (« pas abusé ») ;
- caisses (heraliz_crates) : Vulkhar, Chevalier sans visage, Kraken et Pendentifs supprimées (Glacivore aussi), et
  plus aucun pendentif (charm) d'arme dans Tactique ; la caisse Gemme montre une gemme qui change de couleur (plus de
  couteau dans le cristal) ; hitbox = vraie forme de chaque modèle en cubes de 2 px (`python outils/formes_caisses.py`
  après tout changement de modèle de caisse), contour noir visible seulement en créatif ; nom flottant sans fond
  (rareté entre deux traits, nom de la caisse, clé · clic droit) ;
- barricade en bois (heralisz_bunker) : modèle « mur de rondins couchés » choisi par le propriétaire (rondins entre trois
  poteaux, agrafes de fer, jambes de force), même taille, pose et collision qu'avant ; modèle et textures (4 états
  d'usure) refaits par `python outils/barricade_modele.py` (modèle : `mods/heralisz-bunker/outils/BarricadeModele.java`) ;
- Réanimation : les zombies peuvent achever un joueur à terre ; les exécutions respectent le PvP ; touches : exécuter
  I, menu =, abandon Retour arrière ;
- Emote : roue ², menu 0 ; aucune emote à terre, en combat, en duel ou en ClashWar ;
- Armurerie : les combinaisons intégrales (Hazmat, froid, plongée) se portent seules (sac à dos permis) ;
- chat vocal : celui de CaraCrea (Simple Voice Chat retiré) ;
- launcher (maquettes validées) : fond « carte » (relief sombre, quadrillage, point rouge « Zone HeraliZ »), au-dessus
  du logo « VERSION 1 · SAISON 1 », onglets Accueil / Règlement / Boutique (« Bientôt »), trois nouveautés non cliquables
  sur l'accueil (pas d'onglet Nouveautés, `launcher/app/assets/js/nouveautes.js`), Discord bien en
  vue (bouton en haut à droite + grand bouton sur l'accueil et l'écran de connexion). **Aucun accès au dossier du jeu**
  depuis le launcher et aucun nom de fichier affiché pendant le téléchargement (ne pas faciliter le vol des jars) ;
- comptes et site (choix du propriétaire) : site **Azuriom** au style HeraliZ, comptes communs au site et au launcher
  (un compte créé d'un côté marche de l'autre), boutique, crédits du site, votes, actualités, relié au serveur par
  AzLink. Inscription pseudo + mail + mot de passe, confirmée par un code reçu par mail ; **code par mail à chaque
  nouveau PC** (pas à chaque nouvelle IP) ; le serveur vérifie le ticket du launcher (mode B de Spartan, la clé ne
  quitte plus nos serveurs) ; le mot de passe du jeu AuthMe reste en plus. Mails automatiques depuis une adresse
  @heraliz.fr par un service d'envoi (Brevo). Domaines heraliz.fr et heraliz.com chez IONOS. Site hébergé à part du
  Ptero (petit serveur loué au nom du propriétaire conseillé, 10 € par mois au plus) ;
- boutique : achats en argent ou en crédits du site, livrés tout de suite si le joueur est connecté, sinon à sa
  prochaine connexion, une seule fois. Règles de Mojang : pas d'avantage de jeu payant (les crédits n'y changent rien :
  c'est ce qu'ils achètent qui compte) ; liste des articles à décider avec le propriétaire ;
- refusés par le propriétaire : ImmediatelyFast, Embeddium (aucun risque sur les interfaces) ; pas de barre de soif ;
- retirés, à ne pas réinstaller : Epic Hunt, zombiemod, Dynmap, Xaero, TaCZ NPC, SDM, FTB Library, CreativeCore,
  Necronomicon, Invocore, Resourceful Lib, SmartBrainLib, dyairdrop, CS Grenades, Kotlin for Forge, EndlessAmmo,
  Architectury, Magazine & Casing, Thirst Was Taken, PlayerRevive, heralisz_armor (remplacé par l'Armurerie),
  Heraliz Drugs, Chunky, immersivemessages.

---

## 11. Points ouverts et bugs connus

- **Loot** : 618 des 1 853 entrées des tables de HeraliZ Loot citent encore `heralisz_armor:*` (retiré) : les
  vêtements n'apparaissent plus dans le loot. À régénérer avec les objets de l'Armurerie (avec la refonte du loot).
- **Territoires des clans** (`serveur/config/heralisz_clans/zones.json`) : suivent l'ancienne carte, à refaire pour la
  nouvelle, en donnant sa valeur (niveau de loot) à chaque ville. Les statistiques par ville (captures, temps tenu)
  sont enregistrées mais pas encore affichées dans l'écran du clan (interface à valider d'abord).
- **Bugs repérés, non corrigés** (attendre l'accord du propriétaire) :
  - Sylvorn : les attributs de `SapNode` ne sont pas déclarés (risque de plantage à l'attaque CHANNEL ; correction d'une
    ligne dans `HeraliszSylvorn.attributes`) ;
  - Tactique : le C4 posé disparaît au redémarrage du serveur ; le feu au sol des molotovs dure toujours 2 s ;
    l'éclaboussure des grenades à effet est limitée à 4 blocs.
- **Wonder Weapons** : elles ont besoin de SimpleBedrockModel, fourni aujourd'hui par TaCZ et ywzj_vehicle (Jar-in-Jar).
  Si on retire ywzj, vérifier qu'il reste fourni.
- **Sécurité** : en mode cracké, un pseudo op est à qui l'enregistre en premier sur AuthMe : les admins enregistrent
  leurs comptes dès le premier démarrage. `ops.json` n'est pas dans le dépôt.
- **Monde** : `server.properties` → `level-name=HeraliZ`. Le dossier du monde doit porter ce nom. Les configs de
  `serveur/defaultconfigs/` sont copiées par Forge dans tout nouveau monde.
- **Nouvelle carte (en cours, hors dépôt)** : trois mondes séparés : le spawn, la carte (loot, villes, extractions) et
  les bases des joueurs. Au spawn, un PNJ téléportera au hasard sur la carte (à faire quand la carte sera finie). Les
  territoires des clans ne marchent aujourd'hui que dans l'overworld (`Territoires.monde()`) : à rendre réglable si la
  carte n'est pas l'overworld ; l'extraction renvoie au spawn (`Destination`, réglable).
- **Rien n'a été testé en jeu** depuis la séparation client / serveur (version 2.x des groupes, Matchs 0.3.0, pack
  imposé de heraliz_interface 1.1.0).
- **Protection des jars contre la décompilation** : pas encore faite. Ne jamais brouiller du code sous licence GPL ou
  LGPL (tactique, bunker, backpack, caracrea) ni du code d'un autre auteur.
- **Site et comptes HeraliZ (Azuriom)** : hébergement à choisir, puis maquettes du thème, extension Azuriom (inscription
  depuis le launcher, code par mail sur un nouveau PC, ticket signé pour Spartan), branchement du launcher, boutique et
  votes. D'ici là, le launcher demande seulement le pseudo et AuthMe protège le compte en jeu.
- **Spartan** (08/10) : rien testé en jeu. Launcher : protections anti-injection branchées ; jeton signé pas encore
  (il faut un serveur d'authentification, voir `mods/spartan/docs/LAUNCHER.md`). Prévu ensuite : détection de la visée assistée (aimbot) sur les tirs TaCZ (d'abord
  mesurer sans alerte), butin caché à travers les murs, protection du jar joueur contre la décompilation.

---

## 12. Le serveur Ptero

- Docker Image : `ghcr.io/pterodactyl/yolks:java_17` ; Server Jar File : `mohist-1.20.1-916-server.jar`.
- Mémoire « illimitée » dans le panel → la mémoire est fixée dans la commande de démarrage :
  ```
  java -Xms12G -Xmx12G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -Dterminal.jline=false -Dterminal.ansi=true -jar {{SERVER_JARFILE}} nogui
  ```
- Chat vocal de CaraCrea : port UDP dédié (24454 par défaut). Ajouter une allocation au serveur dans le panel et mettre
  son numéro dans `serveur/defaultconfigs/caracrea-server.toml` (`port = ...`) ainsi que dans le `serverconfig` du
  monde. Sans ça, la voix passe par la connexion du jeu et peut saccader.
- Un mod changé n'est pris en compte qu'au redémarrage du serveur.
