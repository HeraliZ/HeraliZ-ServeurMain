# Guide du serveur HeraliZ — comment tout fonctionne, où tout se trouve

Ce guide sert à **placer chaque modification exactement au bon endroit**. Il complète `CLAUDE.md` (les règles) et
`ARCHITECTURE.md` (le plan). À lire avant toute demande.

---

## 1. La boucle de jeu (ce que le serveur doit faire parfaitement)

```
spawn ──borne de départ──► carte DayZ ──loot──► sac / inventaire
   ▲  (mort : réapparition au camp, ou à la base du clan toutes les 8 min, délai partagé avec /home)
   ▲                          │  zombies, PvP, events, salles à carte
   │                          ▼
   └──── extraction ◄──── objets trouvés
                               │
            ┌──────────────────┼───────────────────────┐
            ▼                  ▼                       ▼
   revente aux marchands   recyclage en composants   craft (Table d'armurier)
   (hérals)                (20 % par composant)      (composants → armes, munitions)
            │                  │                       │
            └────────► hérals ─┴──► clan (1 750) ──► base du clan (CRac) ──► saison, territoires, ClashWar
```

| Étape | Module | Où c'est défini |
|---|---|---|
| Spawn, borne de départ, cinématique | `monde` | `fr.heralisz.utils.spawn`, `fr.heralisz.utils.cine` ; réglages `defaultconfigs/heraliz_spawn-server.toml` |
| Loot des conteneurs | `loot` | tables `modules/loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json`…`t7.json` (**écrites par `outils/loot_armes.py`**) ; raretés `serveur/config/heraliz_loot/raretes.json` ; rotation `defaultconfigs/heraliz_loot-server.toml` ; loot de la bêta (plus d'objets, raretés hautes plus fréquentes, `actif = false` pour l'arrêter) : section `[beta]` du même fichier, chiffres par `outils/loot_beta.py` et `/heralizloot beta` |
| Zombies | `monde` (bientôt `zombies`) | `fr.heralisz.utils.zombies` ; réglages `heraliz_zombies-server.toml` (serverconfig du monde) |
| Combat, mort, à terre | `noyau`, `survie`, `armes` | tag de combat `fr.heraliz.noyau` ; à terre `fr.heraliz.reanimation` ; dégâts des armes `serveur/config/heraliz_tactique/armes.json` (**écrit par `outils/equilibrage_armes.py`**) |
| Cadavres | `monde` | `fr.heraliz.corps` |
| Extraction | `monde` | `com.heraliz.extraction` ; réglages `serveur/config/heraliz_extraction-common.toml` |
| Revente (marchands) | `pnj` | `fr.heraliz.quetes.serveur.Marchands` ; prix `serveur/config/heraliz_quetes/pnj/*.json`, clé `marchand` (**écrite par `outils/marchands.py`**) ; changements en jeu dans `heraliz_quetes/marchands_jeu.json` (racine du serveur) |
| Recyclage | `pnj` | PNJ « recycleur » (`fr.heraliz.quetes.serveur.Recyclage`) ; règles `serveur/config/heraliz_quetes/recyclage.json` (**écrit par `outils/recyclage.py`**), voir section 9 |
| Craft | `armes` + gunpack | recettes de la Table d'armurier : gunpack `serveur/tacz/heraliz_crafts` (**écrit par `outils/crafts.py`**) ; changements en jeu dans `heraliz_tactique/recettes_jeu.json` |
| Hérals | `clans` | `fr.heralisz.economie` (`EconomieApi`) ; réglages `serveur/config/heralisz_economie-common.toml` |
| Clan | `clans` | `fr.heralisz.clans` ; prix de création `serveur/config/heralisz_clans-common.toml` (`cout_creation`) ; niveaux `niveaux.json`, quêtes de clan `quetes.json`, territoires `zones.json`, saison `saison.json` (`serveur/config/heralisz_clans/`) |
| Base | `bases` | `fr.heraliz.crac` ; coûts `defaultconfigs/crac-building.toml`, règles `crac-server.toml`, /home, délai partagé et tutoriel `crac-bases.toml` |
| Mort et réapparition | `bases` (choix), `monde` (camp) | écran de mort `crac/client/ui/EcranMort` ; choix revérifié par `crac/bases/Reapparition` ; délai partagé avec /home : `crac/bases/TeleportBase` (`[home] delai_s`, 480 s) |
| Faim | `noyau` | vitesse : `[survie] faim_facteur` de `defaultconfigs/heraliz_noyau-server.toml` (0,35 = environ trois fois plus lente) |
| Chat | `boutique` (Grades) | canaux Proximité (48 blocs) / Global / Clan : `fr.heraliz.grades.serveur.ChatServeur` ; affichage `fr.heraliz.grades.client.chat` |
| Staff | `boutique` (Grades) | panel (touche P) : `PanelStaff`, droits par grade `heraliz_grades/panel_staff.json`, journal `heraliz_grades/journal_staff.log` (racine du serveur) |
| Quêtes | `pnj` | `serveur/config/heraliz_quetes/pnj/varga.json` (guide : `serveur/config/heraliz_quetes/LISEZMOI.md`) |
| Caisses | `boutique` | butin `serveur/config/heralizcrates/<caisse>.json` ; changements en jeu dans `heraliz_crates/butin_jeu/` |

---

## 2. Où sont les commandes

Chaque commande est déclarée dans le module de son système. Liste et usage : `docs/COMMANDES.md`. Inventaire complet lu
dans les sources (permissions, sous-commandes) : `python outils/commandes.py` écrit `docs/COMMANDES_INVENTAIRE.md`. Tri
proposé (à valider) : `docs/AUDIT_COMMANDES.md`.
Pour trouver le fichier d'une commande `/xxx` :
```
grep -rn 'literal("xxx")' modules/*/src/serveur
```
Règles :
- une commande vit dans **`src/serveur`** (jamais dans `commun` ni `client`) ;
- elle s'inscrit sur `RegisterCommandsEvent` dans la partie serveur du module (`PartieServeur`, `NoyauServeur`…) ;
- droits : `requires(s -> s.hasPermission(2))` pour le staff, `4` pour les op ; les outils du staff qui touchent aux
  joueurs vérifient aussi le `/login` AuthMe ;
- réponses : `Component.translatableWithFallback(clé, texte français, ...)`, avec la clé dans les 3 langues ;
- en combat, à terre ou sur la carte, seules les commandes des listes de `defaultconfigs/heraliz_noyau-server.toml`
  passent. Une nouvelle commande de joueur utilisable sur la carte doit y être ajoutée.

---

## 3. Les deux sortes de réglages

| Dossier | Type Forge | Qui fait foi | Recharge |
|---|---|---|---|
| `serveur/config/` | COMMON / CLIENT, et fichiers JSON des mods | le dépôt **complète** le serveur ; un fichier changé en jeu à part (ex. `butin_jeu/`, `marchands_jeu.json`) n'est jamais écrasé | selon le mod (`/recharger territoires`, `/recharger raretes`…) |
| `serveur/defaultconfigs/` | SERVER (copiés dans `<monde>/serverconfig/`) | **le dépôt fait foi** : réécrit à chaque mise en ligne | redémarrage, ou relu par Forge à chaud pour certains |

Données des joueurs **hors du monde** (survivent à un changement de carte), à la racine du serveur :
`heraliz_grades/` (grades, kits, bons, warps, zones VIP), `heraliz_quetes/` (progression, marchands en jeu, wipe),
`heraliz_clashwar/` (réglages, classement, coffres de match), `heraliz_crates/butin_jeu/`, `heraliz_tactique/recettes_jeu.json`,
`heralisz_camo/collections.json`, `heraliz_emote/debloquees.json`, `crac/spawn.json`, `screenshots/` (Spartan).
Données **dans le monde** (repartent avec une nouvelle carte) : `data/*.dat` (rotation du loot, salles à carte, train,
bases CRac, lieux de la carte, extractions…).

---

## 4. Où toucher pour…

| Je veux… | Je touche… | Puis |
|---|---|---|
| changer une quantité de loot, une rareté | les tableaux en tête de `outils/loot_armes.py` (ou `raretes.json`) | `python outils/loot_armes.py`, fabriquer `loot` |
| changer un prix de marchand | `outils/marchands.py` (tableaux) | relancer l'outil ; rien à fabriquer (fichiers de `serveur/config`) |
| changer une recette de craft | tableaux de `outils/crafts.py` | relancer l'outil (gunpack `heraliz_crafts`) |
| changer les dégâts d'une arme | tableaux de `outils/equilibrage_armes.py` | relancer l'outil |
| changer une protection d'armure | `modules/equipement/outils/equilibrage_armures.py` | relancer, fabriquer `equipement` |
| ajouter / changer une quête | `serveur/config/heraliz_quetes/pnj/varga.json` | `/recharger quetes` ou redémarrage |
| changer le butin d'une caisse | `serveur/config/heralizcrates/<caisse>.json` | `/recharger caisses` |
| changer un coût de base | `serveur/defaultconfigs/crac-building.toml` | mise en ligne + redémarrage |
| changer la taille ou le prix des améliorations de clan | `modules/clans/src/commun/java/fr/heralisz/clans/model/Arbre.java` (`HERALS_MEMBRES`, niveaux requis) | fabriquer `clans` (les deux jars : chiffres affichés chez les joueurs) |
| changer ce qu'un grade du staff peut faire | en jeu : panel du staff, onglet Permissions (Fondateur, op 4) | rien à fabriquer (gardé dans `heraliz_grades/panel_staff.json`) |
| changer une règle (dégâts, IA, butin, commande) | `modules/<module>/src/serveur` | fabriquer le module ; **seul le jar serveur change** |
| changer un affichage (HUD, écran, modèle, son) | `modules/<module>/src/client` | fabriquer ; nouveau jar joueur |
| ajouter un objet, un bloc, une entité, un paquet | `modules/<module>/src/commun` | les deux jars changent ; joueurs et serveur à la même version |
| ajouter un texte | `assets/<modid>/lang/fr_fr.json`, `en_us.json`, `ru_ru.json` | `python outils/verifier_langues.py` |
| faire parler deux modules | API du module appelé (liste dans `CLAUDE.md`), par dépendance `devComplet` ou par réflexion | ne jamais renommer une API |
| savoir si un joueur est en combat / à terre / en match | `fr.heraliz.noyau.api.CombatTag`, `ATerre`, `Matchs` (ou les clés `heralisz:combat_jusqua`, `heralisz:en_match`) | — |
| créer un nouveau module | copier `modules/_gabarit`, l'ajouter à `settings.gradle` | `python outils/construire.py <module>` |

**Fichiers écrits par un outil : jamais à la main** (sinon l'outil efface le changement à la prochaine fois). Les
principaux : tables de loot, `raretes.json` (sections armes / accessoires / munitions / soins), `armes.json`, gunpacks
`heraliz_crafts` et `heraliz_accessoires`, prix des marchands, langues des quêtes (`modules/pnj/outils/langues.py`),
modèles générés (`modules/*/outils/*.py`, voir l'en-tête de chaque script).

---

## 5. Le chemin d'une demande

1. Lire `CLAUDE.md`, ce guide, puis le code de la zone concernée.
2. Placer le changement selon la section 4 : règle → `src/serveur`, affichage → `src/client`, nouveau contenu ou paquet
   → `src/commun`, chiffre → fichier de réglage ou tableau de l'outil.
3. `python outils/construire.py <modules touchés>` puis `python outils/verifier.py <modules touchés>` : 0 problème.
4. Noter dans `ARCHITECTURE.md` (section « Où on en est ») ce qui a changé et ce qui n'a pas été testé en jeu.

---

## 6. Règles de performance (les erreurs de l'ancien serveur à ne plus refaire)

L'ancien serveur ramait parce que des systèmes tournaient pour rien, à chaque tick, pour tous les joueurs. Avant
d'écrire du code qui tourne en boucle, vérifier :

1. **Rien sans travail** : un système sans rien à faire sort tout de suite de son tick (ex. ClashWar hors tournoi).
2. **Pas à chaque tick si ça peut attendre** : `tickCount % 20 == 0` pour une vérification par seconde, `% 200` pour
   un ménage.
3. **Pas de parcours de toutes les entités ni de tous les joueurs à chaque tick.** Garder une liste à jour (ajout et
   retrait sur les événements) plutôt que tout rechercher. Jamais de `level.getEntities(...)` sur une grande zone dans
   un tick.
4. **Aucune allocation dans une boucle chaude** : tampons réutilisés (voir `MatchCw.tampon`), pas de
   `new ArrayList` ni de flux (`stream()`) à chaque tick ou à chaque image.
5. **Pas de réflexion dans une boucle** : chercher la `Method` une fois, la garder.
6. **Le joueur ne dessine que ce qu'il voit** : rien hors du champ de vision ou au-delà de la distance utile ; textes et
   formes préparés quand la valeur change, pas à chaque image.
7. **Plafonds partout** : nombre de zombies, d'objets, de calculs de chemin par tick ; budget en millisecondes pour les
   travaux longs (pose d'arène, calcul de chemins).
8. **Rien qui touche le disque dans un tick** : sauvegarde différée ou groupée (une écriture par seconde au plus).
9. **Mesurer avant et après** : `/diag profil` (section 10), et le banc de charge pour 150 joueurs (`docs/TESTS_EN_JEU_01NOV_banc.md`).

---

## 7. Fabriquer, vérifier, livrer

```
python outils/construire.py               tous les modules (sans nom) ou ceux donnés
python outils/verifier.py                 tous les contrôles, 0 problème exigé
```
Résultat dans `livraison/` : `mods serveur/` (Ptero), `mods client/` (joueurs), `mods solo/`, `plugins/`,
`banc/` (bots de charge, **seulement le temps d'un test**), `spartan/officiel.json`.

---

## 8. Modules et paquets Java

| Module | Paquets principaux |
|---|---|
| noyau | `fr.heraliz.noyau` (`api`, `serveur.combat`, `serveur.chat`, `client.kit`) |
| armes | `fr.heralisz.tactical` (LR Tactical, GPL), `fr.heralisz.camo`, `fr.heraliz.tactique` |
| equipement | `fr.heraliz.armory`, `fr.heraliz.composants` |
| sacs | `com.mrcrayfish.backpacked` (LGPL) |
| loot | `fr.heraliz.loot` |
| bunker | `fr.heralisz.bunker`, `com.hbm_m` (GPL) |
| monde | `fr.heralisz.utils` (train, spawn, zombies, décors), `fr.heraliz.corps`, `fr.heraliz.ping`, `fr.heraliz.usure`, `fr.heraliz.vehicules`, `fr.heraliz.carte`, `fr.heraliz.decors`, `com.heraliz.extraction`, `fr.heraliz.airstrike` |
| survie | `fr.heraliz.reanimation` |
| clans | `fr.heralisz.clans`, `fr.heralisz.economie`, `fr.heraliz.clansetbases` |
| bases | `fr.heraliz.crac` |
| events | `fr.heralisz.mammoth`, `.mere`, `.n07`, `.plague`, `.sylvorn` |
| clashwar | `fr.heraliz.clashwar` (`moteur`, `tournoi`) |
| pnj | `fr.heraliz.quetes` |
| boutique | `fr.heraliz.grades`, `fr.heraliz.casino`, `fr.heraliz.crates` |
| emotes | `fr.heraliz.emote` |
| voix | `com.caracrea` |
| interface | `fr.heraliz.interfaces`, `com.dayzhotbar` |
| anticheat | `com.Spartan` |
| banc | `fr.heraliz.banc` |

---

## 9. Recyclage

Le PNJ « recycleur » du spawn (fichier `serveur/config/heraliz_quetes/pnj/recycleur.json`, marchand de type
`"recyclage"`, à poser une fois en jeu : `/pnj creer recycleur`). « Recycler des objets » ouvre un coffre de 27 cases ; à
la fermeture, le serveur recycle tout ce qui a été déposé :

- chaque composant de la règle de l'objet sort avec **10 % de chances** (une chance par unité de la recette, 2 pièces au
  plus), multipliées par l'état de l'objet (26/10 : neuf 1, 50 % : 0,26, 20 % : presque rien ; `Recyclage.facteurUsure`) ;
- règle de l'objet : sa **recette à la Table d'armurier** s'il en a une (armes, accessoires, munitions par paquet de 15,
  soins, explosifs) ; sinon une **règle de famille** (armures et sacs : tissu, scotch, caoutchouc ; soins : tissu et
  alcool ; nourriture : plastique ou ferraille ; mêlée, kits, outils de véhicule…), au niveau de la rareté de l'objet
  (Commun / Peu commun = I, Rare = II, Épique = III, Légendaire et Unique = IV) ;
- **paquet incomplet** (27/10, règle du propriétaire) : une règle « par 15 » (munitions) accepte aussi 1 à 14 objets ;
  pour n objets, le tirage d'un paquet complet a lieu une fois sur 15 / n, sinon rien (1 balle : 15 fois moins de
  chances d'avoir une pièce) ; temps x n / 15 (0,5 s au moins) ; même gain moyen par balle qu'un paquet complet, plafond
  de 2 pièces et hérals compris ; une munition empilée par moins de 15 (roquettes par 6) se recycle donc case par case
  (l'aperçu dit « 6 sur 15 : moins de chances » et montre les chances réduites) ;
- un objet qui ne rend rien donne quelques hérals (1 à 8 selon sa rareté) : vider son sac au lieu de jeter ;
- jamais recyclés, rendus tels quels : composants (sauf la corde, les objets cassés et les plaques, 26/10), capsules,
  fragments de cartes, clés, bons, objets à vie, prêtés par un match ou de quête.

C'est un tri pour qui est plein, pas une source de richesse. Changer les chances ou les familles : tableaux en tête de
`outils/recyclage.py`, puis `python outils/recyclage.py` (relu au démarrage et par `/recharger quetes`).
Même règle que les marchands : seulement au spawn, ni en combat, ni à terre, ni en match. Joueur mort ou déconnecté
pendant que le coffre est ouvert : rien n'est recyclé, les objets tombent à ses pieds.

## 10. Diagnostic `/diag` : profileur et moniteur

Dans le noyau (`fr.heraliz.noyau.serveur.diag`), opérateurs niveau 4 et console. Tous les rapports vont dans `diag/` à la
racine du serveur (les 60 derniers sont gardés).

### Profil du serveur (notre « spark », 01/11)

Il dit quel mod, quel fichier et quelle ligne font lagger, pendant les ticks ET entre les ticks.

- **Lancer** : `/diag profil [secondes] [intervalle]`
  - 60 s et 5 ms par défaut ; de 10 à 1800 s ; un relevé toutes les 4 à 10 ms ;
  - `/diag stop` arrête avant la fin et écrit le rapport ;
  - `/diag etat` dit si un profil tourne.
- **Depuis GitHub** : Actions → « Lenteurs du serveur » → Run workflow → cocher « profil » (durée, intervalle).
  - La commande part par la console du panel, avec les secrets déjà utilisés pour redémarrer.
  - La tâche attend le rapport, le rapatrie, rend les noms lisibles et le met dans son journal.
  - Le début du rapport (résumé, mods, points chauds) est aussi en tête de la page de la tâche.
  - Case jamais cochée par défaut : sans elle, la tâche ne fait que lire.
- **Pendant le profil** : toutes les 10 s, celui qui l'a lancé (ou la console) reçoit les ms par tick, le TPS et les 3
  mods les plus lourds ; à la fin, le résumé et le nom du rapport `diag/profil-<date>.txt`.

**Le rapport**, section par section :

| Section | Ce qu'on y lit |
|---|---|
| Résumé | ms par tick (moyenne, médiane, 95 %, 99 %, pire), TPS, ticks de plus de 50 ms ; part du fil serveur dans les ticks, entre les ticks, à attendre ; ramasse-miettes ; les mods les plus lourds ; le point le plus chaud |
| 1 et 1 bis | temps par mod ou plugin (ms par tick et %), pendant puis entre les ticks |
| 2, 2 bis, 2 ter | **points chauds** : la ligne de code responsable (`fichier:ligne`), puis les 10 premiers de chaque mod |
| 3 | méthodes les plus chères en temps propre (là où le serveur était vraiment), avec qui les appelait |
| 4 | écouteurs d'événements de Forge : temps compris, événement, qui l'a lancé |
| 5 | catégories : chemins des créatures, IA, tronçons, entités de bloc, paquets, commandes… |
| 6 | **temps par système, mesuré exactement** : par monde ; entités et entités de bloc par type (combien par tick, µs chacune) et par mod ; écouteurs de tous les mods par événement ; tronçons chargés, lus et écrits sur le disque ; entités arrivées par type ; paquets reçus et envoyés par type (nombre, Ko/s) ; commandes jouées (durée) |
| 7 | pires ticks (plus de 50 ms) et pires moments entre deux ticks, avec ce qui tournait |
| 8 et 9 | arbres des appels, comme spark (% et ms par tick) |

**À qui va le temps** : chaque relevé compte pour le code du mod ou du plugin le plus haut dans la pile, à la ligne de son
appel, ce qu'il appelle dans Minecraft compris. Sans mod ni plugin : Mohist ou Forge, sinon Minecraft. Les méthodes
ajoutées par un mixin sont rendues au mod du mixin. Nos fichiers sont donnés par leur chemin (`fr/…/Fichier.java:212`).

**Exemple** (essai sur de fausses piles) :

```
Ticks : 1200 ; 22.6 ms en moyenne, médiane 22.1, 95 % 31.8, 99 % 36.1, pire 202 ms ; 19.8 TPS
Fil serveur : 44.7 % dans les ticks, 3.7 % entre les ticks (commandes, paquets des joueurs, tâches), 51.5 % à attendre
Point le plus chaud : IaZombies.cibler (fr/heralisz/utils/zombies/IaZombies.java:212)  [monde · heralisz_utils] — 5.63 ms par tick
== 2. Points chauds pendant les ticks ==
     5.63   24.9 %  IaZombies.cibler (fr/heralisz/utils/zombies/IaZombies.java:212)  [monde · heralisz_utils]
     3.40   15.0 %  EntityKineticBullet.tick (com/tacz/guns/entity/EntityKineticBullet.java:300)  [TaCZ]
     3.19   14.1 %  PathNavigation.handler$zza000$heraliz$limiter (mixin …CheminsZombiesMixin, ligne 45)  [monde · heralisz_utils]
== 7. Pires ticks ==
-- tick 151 à 07:33:27.500 : 201 ms (40 relevés) --
   80.0 %  bases · crac — PointSpawn.garder (fr/heraliz/crac/spawn/PointSpawn.java:50)
```

**Noms de Minecraft** : dans le serveur, ses méthodes portent un nom SRG (`m_5705_`).
- Le serveur les traduit lui-même s'il trouve les correspondances de Mojang et de MCPConfig dans son `libraries/` (laissées
  par l'installation de Forge ou de Mohist), ou dans `diag/noms/`. L'en-tête du rapport dit ce qu'il en est.
- Sinon : `python outils/diag_noms.py diag/profil-<date>.txt` (télécharge une fois les correspondances, ou lit le
  `libraries/` du serveur local). Il donne aussi le vrai chemin de nos fichiers (`modules/<module>/src/…`). La tâche
  « Lenteurs du serveur » le fait seule.

**Coût** :
- hors profil : rien (une lecture de deux variables par tick, deux lectures d'horloge par commande) ;
- pendant un profil : le serveur est arrêté le temps de lire sa pile, environ 60 µs par relevé (mesuré à chaque profil et
  écrit dans l'en-tête), soit environ 1,2 % à 5 ms, 1,5 % à 4 ms et 0,6 % à 10 ms ;
- chronomètre des entités : de l'ordre de 0,1 µs par entité et par tick ; paquets comptés sur les fils réseau, pas sur
  le fil serveur ;
- le rapport est fait sur un fil à part (1 à 2 s), jamais sur le fil serveur.

**Ce qu'il ne mesure pas exactement** : le temps de chaque écouteur d'événement. Le bus d'événements de Forge est chargé
avant les mods, hors de portée des mixins, et chronométrer chacun de nos écouteurs toucherait tous les modules. Le rapport
donne donc :
- le temps de chaque écouteur d'après les relevés (précision écrite dans la section 4) ;
- le total exact, du premier au dernier écouteur, des événements chauds (section 6).

### Blocages et autres outils

- **Blocages, toujours actif** : un fil surveille la durée de chaque tick. Au-delà du seuil (250 ms par défaut), il relève
  la pile du serveur toutes les 5 ms jusqu'à la fin du tick, puis écrit `blocage-<date>.txt` :
  - mod ou plugin responsable ;
  - catégorie (entités, chemins, tronçons, sauvegarde…) ;
  - lignes de code et piles ;
  - temps du ramasse-miettes, mémoire, entités les plus nombreuses ;
  - dernières commandes jouées (01/11 : seulement leurs mots, ni pseudo, ni arguments), aussi dans le journal.
  Les opérateurs connectés sont prévenus. Tant que tout va bien, il ne coûte rien.
- **Blocages entre deux ticks** (01/11) : les commandes et les paquets des joueurs passent juste après le tick. Quand ce
  temps dépasse 50 ms + le seuil, même relevé, même alerte (« entre deux ticks ») et même dossier.
- `/diag commandes` : les commandes les plus lentes depuis le démarrage (mots de la commande seulement, nombre, moyenne,
  pire). Chaque rapport de blocage donne aussi la durée des dernières commandes.
- `/diag mesure [secondes]` : allume le chronomètre de Forge (`TimeTracker`), puis `mesure-<date>.txt` donne le temps de
  chaque tick par type d'entité et d'entité de bloc, et par mod.
- `/diag fuites` : listes surveillées une fois par minute :
  - entités, objets au sol, monstres et tronçons par monde ;
  - mémoire gardée après le ramasse-miettes ;
  - listes déclarées par les mods avec `fr.heraliz.noyau.api.Fuites.surveiller(nom, taille)`.
  Une liste qui grossit 20 minutes sans redescendre donne `fuite-<date>.txt` et une alerte.
- `/diag client <joueur> [secondes]` : le jeu du joueur enregistre ses images, puis son rapport arrive dans
  `client-<pseudo>-<date>.txt`. Il contient :
  - FPS et 1 % le plus lent ;
  - durée des images, images lentes et mod responsable ;
  - temps de dessin par type d'entité et d'entité de bloc ;
  - carte graphique, processeur, réglages.
  Le joueur est prévenu. Il peut aussi lancer lui-même `/diagclient [secondes]` (rapport dans `diag/` de son jeu).
- `/diag` ou `/diag etat` : TPS, ms par tick (médiane, 95 %, 99 %, comparés aux objectifs 30 / 40 / 50 ms), mémoire,
  entités et tronçons par monde.
- Rapports automatiques et seuil : section `[diag]` de `heraliz_noyau-server.toml` (`auto`, `seuil_ms`, `annoncer`),
  relue seule quand le fichier change (`/diag auto` et `/diag seuil` retirés le 02/11).

**spark retiré** (01/11, choix du propriétaire) : `/diag profil` le remplace. Son jar n'est plus dans `serveur/plugins/` :
la mise en ligne l'efface du Ptero (son dossier de données y reste, sans effet).

## 11. Mettre à jour sans couper

- **Données** : `/recharger` relit en une fois les données des modules, sans redémarrer ni déconnecter personne :
  - quêtes, marchands et recycleur ;
  - caisses et casino ;
  - raretés et tables de loot ;
  - saison, territoires et Purge ;
  - zones dangereuses et ClashWar ;
  - modpack officiel de l'anticheat.
  La liste des commandes jouées est la section `[recharger]` de `heraliz_noyau-server.toml`. Les réglages `.toml` se
  relisent seuls quand leur fichier change.
- **Tables de loot** : celles de `serveur/config/heraliz_loot/coffres/` passent devant celles du jar et se relisent par
  `/recharger loot`. `outils/loot_armes.py` écrit les deux copies.
- **Code Java** : Forge oblige à redémarrer. `/redemarrage <minutes> [raison]` (ou `annuler`) :
  1. compte à rebours annoncé à tous ;
  2. sauvegarde complète ;
  3. joueurs déconnectés avec un message spécial : leur jeu affiche « Reconnexion automatique dans … » et revient tout
     seul quand le serveur répond (HeraliZ Interface, `client/Reconnexion`) ;
  4. arrêt du serveur.
  Pour qu'il redémarre seul, le plus simple est une tâche « restart » du panel. Autre possibilité : `code_sortie` autre
  que 0 (section `[redemarrage]`), avec le redémarrage après plantage activé.

## 12. Test de charge

`/bots scenario <nombre> [minutes] [rayon]` (module `banc`, livré à part, op 4). À lancer dans le monde du tutoriel
`crac:bases`, jamais sur la carte.

1. X bots arrivent d'un coup et jouent :
   - tirs TaCZ, grenades, flashs, fumigènes et soins ;
   - frappes aériennes et largages ;
   - fouille des conteneurs ;
   - petites bases de CRac, via `fr.heraliz.crac.api.BancApi` (vraies pièces, mêmes règles de pose).
2. Pendant ce temps, `/diag profil` et le chronomètre de Forge tournent, et une ligne est relevée toutes les 5 s.
3. À la fin :
   - bots retirés et leurs bases effacées ;
   - objets au sol, projectiles et cadavres des bots retirés ;
   - rapport `diag/charge-<date>.txt` : moyenne, 95 % et 99 % des ticks comparés aux objectifs, mémoire, entités,
     actions, temps par mod, liste des blocages.

Ce sont des bots sans réseau : ils chargent le serveur, pas la connexion. `/bots scenario stop` arrête avant la fin.

Autres commandes du banc (01/11) : `/bots mesure <secondes>` (ou `stop`) mesure sans rien lancer : chaque tick (moyenne,
médiane, 95 %, 99 %, pire, vrai TPS), mémoire, processeur, entités, zombies, tronçons par monde, actions et commandes des
bots, rapport `diag/banc-<date>.txt` ; `/bots commandes <secondes>` (0 = arrêt) : chaque bot tape une commande de joueur
de temps en temps (avec ses droits de joueur, entre deux ticks), prise dans `config/heraliz_banc/commandes.txt`. Un bot
envoyé dans un autre monde (`/tuto lancer <bot>`, extraction) y vit autour de son point d'arrivée.

Banc réaliste (01/11, `banc` 1.2.0) : un bot mort reste sur l'écran de mort 4 à 12 s, réapparaît par le même clic qu'un
vrai joueur, passe au camp puis reprend la vraie borne de départ ; ses départs attendent que leur tronçon soit chargé (tâche
de fond). Autres commandes : `/bots camp` (dalle sous le point de réveil du spawn), `/bots zone <rayon> <arrivée>` (zone de
groupe : lieu de vie, arrivée de la borne), `/bots pregenerer <rayon>|stop` (terrain créé d'avance), `/bots rythmes <morts>
<connexions> <voyages> <combat %>|off` (par minute pour 150 joueurs), `/bots gamerule <règle> <valeur>` (tous les mondes),
`/bots pic <extraction|spawn|borne|connexion> <nombre>`, `/bots troncons` (tickets par sorte), `/bots jvm`.

### Banc de charge sur une machine de GitHub (01/11)

Workflow « Banc de charge » (`.github/workflows/banc.yml`), **sans le Ptero ni aucun secret** : un serveur Mohist complet
(nos mods de la branche, plugins, réglages du dépôt) est monté sur la machine de GitHub (4 cœurs, 16 Go), avec un monde
neuf (la vraie carte n'est pas dans le dépôt), puis jeté.

- **Lancer** : onglet Actions → « Banc de charge » → « Run workflow » (choisir la branche ; le bouton n'apparaît qu'une
  fois le fichier sur `main`). Avant cela, ou sans formulaire : envoyer sur une branche de travail un changement de
  `banc.yml` (réglages par défaut). Jamais lancé par un envoi sur `main`.
- **Réglages** : bots (100, jusqu'à 150), minutes de mesure (5), minutes de chauffe (4), commandes du diagnostic
  (`diag profil {s}; diag mesure {s300}`, à changer ici si `/diag` change), monde normal ou plat, scénario (`normal`,
  `connexions` : coût d'une connexion ; `teleportations` : pics d'extraction de groupe, de 10 `/spawn` et de départ de
  la borne), règles de jeu pour un **essai A/B** (`randomTickSpeed 0`, plusieurs séparées par `;`, case « ab » : une
  mesure sans puis une avec), distances vue/simulation (`8/4`, comme le Ptero le 01/11 : vue 8 imposée par
  `heraliz_distances-server.toml`), rythmes pour 150 joueurs
  (`8/0.85/4/25` : morts, connexions, voyages par minute, % du temps en combat ; `off` : comme avant ; morts et
  connexions d'après le propriétaire : 250 à 500 morts et 35 à 50 connexions par heure, serveur plein).
- **Déroulé** (`outils/banc_charge.py`, utilisable aussi sur un PC : `preparer <dossier>` puis `lancer <dossier>`) :
  0. machine Java : Mohist installe Forge à son premier démarrage puis se relance dans une autre machine Java sans
     aucun réglage (premier banc : 3 996 Mo au lieu de 11 Go) ; l'installation est donc faite à part, puis la mesure tourne
     dans une seule machine Java, réglages passés aussi par `JDK_JAVA_OPTIONS` ; le rapport écrit ses vrais arguments ;
  1. fabrication des mods changés (caches de la mise en ligne), serveur d'essai par `outils/serveur_local.py --banc`,
     Mohist de la Release « base-serveur » (empreinte contrôlée), réglages d'essai dans ce dossier seulement
     (`server-ip=127.0.0.1`, AuthMe en mode essai, Spartan sans jeton du launcher, LiteBans en base locale, « Can't keep
     up » affichés, zombies du peuplement aussi autour des bots) ;
  2. spawn du banc et zones de groupe, terrain des zones créé d'avance (avant les bots) ; 10 % des bots dans le tutoriel
     des bases (`/tuto lancer`), les autres par groupes de 5 sur une grille de la carte (320 blocs entre deux groupes),
     rythmes de 150 joueurs ; zones d'extraction ouvertes près d'un groupe sur quatre ; `doMobSpawning false`
     comme sur le Ptero (zombies du peuplement seulement) ; commandes de joueur tapées par les bots
     (`outils/banc/commandes.txt`) ;
  3. chauffe (au moins 4 min, plus tant que du terrain se crée ou que les arènes du ClashWar se posent), puis
     `/bots mesure` et le diagnostic pendant la mesure ;
  4. arrêt propre, puis **résumé de la tâche** : TPS et ms par tick (moyenne, 95 %, pire), **ms par tick hors banc**
     (coût du banc lui-même mis à part), machine Java réelle, réglages comparés au Ptero, mémoire et ramasse-miettes,
     temps par mod du profil, rythmes (par minute et pour 150 joueurs), entités, zombies, tronçons par monde et ce qui
     les tient chargés, pics, essai A/B, commandes les plus lentes, « Can't keep up », erreurs Java, rapports du
     diagnostic. Détail des essais : `docs/TESTS_EN_JEU_01NOV_banc.md`.
     Artefact `banc-rapport` (7 jours) : journal complet, `diag/`, `gc.log`, `erreurs.txt`.
- **Lire le résultat** : comparer des bancs entre eux (même nombre de bots, même durée), pas avec le Ptero (autre machine,
  pas la vraie carte, pas de vrais joueurs : voir « Ce que ce banc ne mesure pas » en bas du résumé).

## 13. Zombies HeraliZ

Code dans `fr.heralisz.utils.zombies` (module `monde`), réglages dans `heraliz_zombies-server.toml` (serverconfig du
monde).

- **IA allégée** (`IaZombies`) :
  - plus de recherche de village la nuit ni d'oeufs de tortue ;
  - seulement les joueurs (et ce qui les frappe) comme cibles ;
  - niveaux d'IA selon le joueur le plus proche : proche = IA complète ; moyen = objectifs tous les 6 ticks et chemins
    plus courts ; loin = objectifs tous les 20 ticks et chemins 4 fois plus courts.
- **Chemins** (`CheminsZombies`, mixin serveur) : 40 recherches par tick au plus sur tout le serveur. Un zombie qui vise
  la même case qu'un voisin (à 4 blocs, dans la seconde) reprend son chemin au lieu d'en calculer un.
- **Variétés** (`[varietes]`), tirées une fois et gardées avec le zombie :

  | Variété | Part | Vie | Vitesse | En plus |
  |---|---|---|---|---|
  | Marcheur | le reste | 30 | 0,23 | |
  | Coureur | 22 % | 24 | 0,33 | |
  | Costaud | 8 % | 60 | 0,20 | +1,5 dégâts, peu de recul |

  Les rampants restent ceux de Mutants & Zombies : un rampant maison demande d'abord des rendus.
- **Nombre** : peuplement autour des joueurs (`[peuplement]`), plafond de 1 500 monstres sur la carte.

## 14. Butin des zombies (26/10)

Code : `fr.heraliz.loot.ButinZombies` (module `loot`, serveur seulement). Tables et règles **écrites** par
`python outils/loot_zombies.py` (jamais à la main) : `coffres/zombies.json` et `coffres/zombies_militaire.json` (même
moteur que les conteneurs, un tirage par zombie, « rien » = `minecraft:air`), règles dans
`serveur/config/heraliz_loot/zombies.json` (relues par `/recharger loot`). `--simuler` donne les valeurs moyennes.

- **Ce qui sort** : objets de récupération à revendre, composants de fabrication I à III (jamais IV), petits matériaux
  (clous, corde, ciment), munitions, bandages, nourriture, plaques d'identité, rarement une capsule ou un kit de
  réparation de fortune. Zombies en tenue militaire (aérodrome, base Sentinelle…) : un peu mieux (composants II et III,
  munitions de fusil, attelles, grenades fumigènes et flash, kit standard, et très rarement une arme Commun ou Peu commun
  au chargeur vide).
- **Rentabilité** (règle du propriétaire : environ la moitié des conteneurs par heure, munitions déduites) : hypothèses
  en tête du script (60 conteneurs remplis et 90 zombies par heure, moitié des zombies à l'arme à feu, 6 balles chacun) ;
  zombie ordinaire ≈ 22,5 hérals, militaire ≈ 33 ; 15 % des zombies ordinaires ne donnent rien. À recaler avec les vrais
  chiffres de la bêta (`/heralizloot zombies stats`). Si les tables des conteneurs changent, relancer `--simuler` : le
  rapport bouge avec elles (`ECHELLE` en tête du script).
- **Fragments des cartes d'accès**, tirés à part de la table : vert 0,4 %, bleu 0,16 %, rouge 0,089 % par zombie compté
  (4 fragments, donc une carte, pour 1 000 / 2 500 / 4 500 zombies sur tout le serveur ; un joueur seul qui n'échange pas
  ses doubles en trouve en moyenne 8,3 avant d'avoir les 4 numéros). Au plus 6 fragments par joueur et par jour.
- **Ce qui ne compte pas** (anti-farm) : un zombie qui n'est pas né du peuplement ou d'une apparition naturelle (pas de
  `/summon`, ni vagues des drapeaux, ni banc d'essai, ni bots) ; tué surtout par autre chose qu'un joueur (chute, feu,
  lave…) ; tueur pas en survie ou à plus de 96 blocs ; hors de la carte ; un zombie ne donne qu'une fois. Puis, par joueur :
  au-delà de 100 zombies dans l'heure, les chances baissent jusqu'à 0 à 200 ; même chose par carré de 32 blocs (25 puis
  60 zombies en 20 minutes). Mémoire gardée dans `heraliz_loot/zombies_etat.json` (hors du monde) : une déconnexion ou un
  redémarrage ne remet rien à zéro.

## 15. Quêtes du jour (26/10)

Code : `fr.heraliz.quetes.serveur.Journalieres` (module `pnj`), écran `client/EcranJournalieres` (maquettes validées :
`maquettes/journalieres/`). Réglages **écrits** par `python outils/quetes_journalieres.py` dans
`serveur/config/heraliz_quetes/journalieres.json` (relu par `/recharger quetes`).

- Ouverture : `/journalieres`, bouton « Quêtes du jour » du journal (touche J), ou une touche libre à choisir dans les
  options. Une journée = de minuit à minuit, heure de Paris.
- Le joueur choisit **une difficulté par jour** (Facile, Moyenne, Difficile) ; le serveur tire **3 quêtes** au hasard dans
  la liste de cette difficulté ; il en accepte une, les deux autres disparaissent ; pas de nouveau tirage dans la journée.
  Abandonner = journée finie. Quêtes : zombies (ordinaires, militaires, au corps à corps), conteneurs (tous, ou d'un
  palier au moins), salle à carte verte ou bleue, extractions, distance parcourue, vente aux marchands, recyclage,
  objets à livrer, joueurs tués (Difficile). Tout est compté par le serveur (mêmes règles que le butin des zombies :
  un `/summon` ne compte pas ; un conteneur compte une fois).
- Réussie : **un tirage** dans la table de la difficulté (bande qui défile, ESPACE pour passer). La récompense est notée
  avant d'être donnée et le joueur sauvegardé tout de suite : ni perte ni double, même avec une déconnexion ou un
  redémarrage au mauvais moment (une remise restée en attente est refaite à la connexion, ou par
  `/journalieres remettre <joueur>`). Inventaire plein : le reste tombe au sol à ses pieds. Les clés de caisse vont sur
  son compte de clés.
- Tables (chances en %) : voir `RECOMPENSES` en tête du script. Difficile : AK-47 20, composants III 14, métal renforcé
  10, munitions 7,62 9, arme Épique 7, carte bleue 6, kit d'armurier 6, carte verte 6, récupération Légendaire 5, clé
  Camo 5, composant IV 4,5, fragment rouge 4, clé Emotes 2, clé Gemme 1, carte rouge 0,5.
- État par joueur dans ses données persistantes (`heraliz_jour`), journal du serveur `heraliz_quetes/journalieres.log`.

## 16. Frappes aériennes et onglet « Divers » (26/10)

- **Jumelles de désignation** (`airstrike:designator`, Légendaire) : seulement fabriquées à la Table d'armurier, onglet
  « Divers » ; jamais dans le loot ni les caisses. Seules, elles ne font rien : il faut une **munition de frappe**.
- **Munitions** (Divers) : légère (Rare : rafale de canon, fumigène), lourde (Épique : bombardement, bombe à
  fragmentation, gaz), spéciale (Légendaire : bombe guidée, napalm). **Chaque munition sert 2 fois** (reste affiché dans
  l'infobulle) ; une frappe annulée rend la munition avec son reste.
- Limites : une frappe en cours par joueur, 6 sur le serveur, 150 blocs de portée, 12 s entre la désignation et le
  passage de l'avion (fumigène coloré sur la cible : on a le temps de fuir), recharge de 30 s entre deux frappes d'un
  joueur gardée dans ses données (déconnexion et redémarrage ne l'effacent pas). Seulement sur la carte, jamais dans une
  zone VIP ni en match. Réglages : `serveur/defaultconfigs/airstrike-server.toml`.
- **Les bases ne prennent rien** : aucune frappe ne casse de bloc ni ne met le feu (explosions sans casse, imposé par le
  code) ; CRac ignore les explosions de frappe ; un joueur sous un toit (bloc plein jusqu'à 24 blocs au-dessus de ses
  yeux) ou dans une base n'est touché ni par l'explosion, ni par le canon, ni par le gaz. Dehors, à découvert : touché.
- **Recettes « Divers »** (gunpack `heraliz_crafts`, écrites par `outils/crafts.py`, tableau `DIVERS`) : carte verte,
  bleue, rouge (les 4 fragments numérotés ; le clic droit avec les 4 fragments en main marche toujours), jumelles,
  munitions légère, lourde, spéciale. Les anciennes recettes d'établi des cartes (grille 2 × 2) sont retirées : tout passe
  par la Table d'armurier.

## 17. Recharges de téléportation par grade (26/10)

- Retour à la base (`/home`) et `/tpa` : **8 min** chacun, indépendants. Rescapé 7 min 30, Traqueur 7 min, Roi de la Zone
  6 min 30 (Streamer : `/tpa` 7 min 45). Réglé dans l'éditeur des grades (`home_recharge`, `tpa_recharge`) ; les
  grades déjà créés en jeu ont été passés une fois à ces valeurs (définitions version 3).
- Pas de cumul : le meilleur grade du joueur l'emporte ; l'arbre du clan ne s'ajoute pas au grade (le plus court des deux
  compte). Grade changé pendant une recharge : la recharge en cours garde sa durée, la suivante prend la nouvelle.
- Recharge gardée hors du monde (`heraliz_grades/recharges_tp.json` pour `/tpa`, données de la base pour `/home`) :
  ni une déconnexion ni un redémarrage ne la remettent à zéro. Aucune autre commande n'est touchée.

## 18. Bases : coffres dans la rangée des murs (31/10)

- Les murs de CRac prennent la rangée de cases du bord d'une fondation (4 px centrés sur le bord). La caisse en métal et
  les coffres par rareté se posent dans la case d'un mur droit (`Adosse.dansMur`) : le mur leur laisse sa moitié
  intérieure et la reprend quand ils partent. Dans un angle, l'objet est poussé contre un mur et entre de 2 px au plus
  dans l'autre (jamais jusqu'à sa face extérieure). Places des caisses : fondation fermée 9, trois murs 9, deux
  fondations en une pièce 18, en deux pièces 15, étage fermé 9 (la limite de caisses par base compte toujours).
- Coffres par rareté : 13,5 px de profondeur au plus pour tenir dans la rangée (`modules/bases/outils/coffres_modeles.py`,
  `amincir`).
- **Fondations triangles** (03/11, demande du propriétaire, Bases 1.33.0 ; remplace la règle du 31/10 « carré toujours
  droit », retirée) : comme dans Rust, un carré (fondation, plancher, toit, escalier, rampe) se pose contre chacun des 3
  côtés d'une fondation triangle, tourné comme ce côté (30° ou 60°). Sa collision est un vrai carré tourné, tramée au
  pixel (`ShapeRaster`, formes gardées par orientation dans `PieceShapes`) : avec F3+B, de petites « marches » d'un pixel
  le long des côtés en biais (la collision de Minecraft n'est faite que de boîtes droites, normal). Murs, fenêtres, portes,
  planchers et toits posés dessus suivent son orientation (même grille à 30° que les triangles). Caisses, coffres et objets
  posés, blocs de Minecraft, restent droits sur la grille ; Bases 1.33.1 (demande du propriétaire : rien ne dépasse) : un
  objet ne se pose que si sa vraie forme (sa boîte, à sa taille) est entièrement au-dessus des fondations et planchers à
  ras de son dessous (carrés droits, tournés et triangles réunis : `StructureBlock.solSousForme`) et ne touche aucune pièce
  d'un bloc voisin (`PlacableSupport.touchePiece`) ; un bloc touché par un mur en biais est un bloc de construction, rien
  ne s'y pose. Caisses en métal posables : carré tourné 2 à 5 sans murs, 1 à 4 avec ses murs (le plus souvent 3 et 2) ;
  triangle 0 à 2 sans murs, 0 ou 1 avec murs ; carré droit fermé : 9 (caisses « dans le mur », inchangé). L'armoire
  demande toujours une case entièrement couverte. Essais : `docs/TESTS_EN_JEU_03NOV_bases-regles.md`.
