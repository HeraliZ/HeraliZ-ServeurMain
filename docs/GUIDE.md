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
| Loot des conteneurs | `loot` | tables `modules/loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json`…`t7.json` (**écrites par `outils/loot_armes.py`**) ; raretés `serveur/config/heraliz_loot/raretes.json` ; rotation `defaultconfigs/heraliz_loot-server.toml` |
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
| `serveur/config/` | COMMON / CLIENT, et fichiers JSON des mods | le dépôt **complète** le serveur ; un fichier changé en jeu à part (ex. `butin_jeu/`, `marchands_jeu.json`) n'est jamais écrasé | selon le mod (`/territoire recharger`, `/rarete recharger`…) |
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
| ajouter / changer une quête | `serveur/config/heraliz_quetes/pnj/varga.json` | `/quete recharger` ou redémarrage |
| changer le butin d'une caisse | `serveur/config/heralizcrates/<caisse>.json` | `/heraliz reload` |
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
9. **Mesurer avant et après** : spark (`/spark profiler`), puis le moniteur HeraliZ (à venir, étape 3).

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
`outils/recyclage.py`, puis `python outils/recyclage.py` (relu au démarrage et par `/quete recharger`).
Même règle que les marchands : seulement au spawn, ni en combat, ni à terre, ni en match. Joueur mort ou déconnecté
pendant que le coffre est ouvert : rien n'est recyclé, les objets tombent à ses pieds.

## 10. Diagnostic `/diag` et moniteur

Dans le noyau (`fr.heraliz.noyau.serveur.diag`), opérateurs niveau 4 et console. Tous les rapports vont dans `diag/` à la
racine du serveur (les 60 derniers sont gardés).

- **Blocages, toujours actif** : un fil surveille la durée de chaque tick. Au-delà du seuil (250 ms par défaut), il relève
  la pile du serveur toutes les 5 ms jusqu'à la fin du tick, puis écrit `blocage-<date>.txt` :
  - mod ou plugin responsable ;
  - catégorie (entités, chemins, tronçons, sauvegarde…) ;
  - lignes de code et piles ;
  - temps du ramasse-miettes, mémoire, entités les plus nombreuses.
  Les opérateurs connectés sont prévenus. Tant que tout va bien, il ne coûte rien.
- `/diag start [secondes]` : profil en direct, résumé dans le chat toutes les 10 s, `profil-<date>.txt` à la fin.
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
- `/diag auto on|off`, `/diag seuil <ms>` : réglages pour la session. Au démarrage, c'est la section `[diag]` de
  `heraliz_noyau-server.toml` qui fait foi.

spark reste installé pour aller plus loin.

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
  `/heralizloot tables`. `outils/loot_armes.py` écrit les deux copies.
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
2. Pendant ce temps, `/diag start` et le chronomètre de Forge tournent, et une ligne est relevée toutes les 5 s.
3. À la fin :
   - bots retirés et leurs bases effacées ;
   - objets au sol, projectiles et cadavres des bots retirés ;
   - rapport `diag/charge-<date>.txt` : moyenne, 95 % et 99 % des ticks comparés aux objectifs, mémoire, entités,
     actions, temps par mod, liste des blocages.

Ce sont des bots sans réseau : ils chargent le serveur, pas la connexion. `/bots scenario stop` arrête avant la fin.

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
`serveur/config/heraliz_loot/zombies.json` (relues par `/heralizloot tables`). `--simuler` donne les valeurs moyennes.

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
`serveur/config/heraliz_quetes/journalieres.json` (relu par `/quete recharger`).

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
