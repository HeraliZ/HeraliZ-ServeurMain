# HeraliZ v2 — architecture et plan

But : un serveur **DayZ uniquement** (survie, loot, armes, zombies, clans, bases, events), propre, rapide, et
qu'on peut mettre à jour sans tout casser. On repart d'un dossier neuf, en reprenant **seulement** ce qui sert.

---

## 1. Ce qu'on garde, ce qu'on jette

**Jeté :**
- Dans **Matchs** : le mode Zombies et The Giant (30 000 lignes), `/duel` (1v1, entraînement), la killcam, la bombe
  et l'ordinateur de recherche et destruction. **Seul ClashWar est gardé**, allégé (section 1 bis).
- **Wonder** (armes du mode Zombies) et son gunpack `heraliz_wonder`.
- **Mutants & Zombies, Zombie Awareness, CoroUtil** : remplacés par notre zombie maison (section 4).
- La dimension `heralisz_matchs:zombies`.
- Tout ce qui ne servait que l'ancien GitHub : 16 workflows, la plupart des 47 outils, le CLAUDE.md de 312 Ko.

**Gardé** (code repris, trié, nettoyé) : loot, armes (TaCZ + nos ajouts), armures, sacs, composants, clans et saison,
bases CRac, extraction, réanimation, carte du monde, train, véhicules, décors, salles à carte, frappes et largages,
boss (comme events), quêtes et PNJ, casino, caisses, emotes, grades et boutique, voix, anticheat.

**Ce qu'on récupère de Matchs avant de le supprimer** (une dizaine de mods s'en servent) : le tag de combat, le kit
d'interface, les annonces. Tout ça va dans le **noyau**.

## 1 bis. ClashWar léger

Aujourd'hui, ClashWar repose sur tout le moteur des matchs (environ 31 000 lignes avec les duels). La version légère
est un module à lui seul, sans duels ni zombies :

- **Format : élimination simple.** À chaque manche, la dernière équipe en vie gagne ; la première équipe à 3 manches
  remporte le match. Côtés échangés en cours de match. Pas de bombe, pas d'ordinateur, pas de GeckoLib.
- **Gardé :** tableau du tournoi, match pour la 3e place (joué avec la finale), podium de fin, regarder un match en
  direct, classement des clans (titres, victoires, K/D, panneau au spawn).
- **Ne coûte rien hors tournoi :** les arènes (`heralisz_matchs:arenes`) ne sont chargées que pendant un tournoi,
  puis déchargées ; aucun calcul ne tourne entre deux tournois.
- Le reste du moteur des duels (classes, kits, manches) est réécrit en petit, à l'intérieur du module, avec
  seulement ce dont ClashWar a besoin.

---

## 2. Organisation du nouveau dossier

```
HeraliZ/
  CLAUDE.md          règles courtes (une page, pas 300 Ko)
  ARCHITECTURE.md    ce fichier
  settings.gradle    UNE SEULE fabrication pour tous les modules (aujourd'hui : 25 projets séparés)
  modules/
    noyau/           socle commun : état du joueur (en combat, à terre, au spawn), tag de combat, réseau,
                     kit d'interface, données rechargeables, moniteur de perfs, journal
    survie/          réanimation, cadavres, soins, usure des armes
    armes/           TaCZ (dégâts, mêlée, couteaux, grenades, camos, atelier)
    equipement/      armures, sacs à dos, composants
    loot/            conteneurs, rotation, raretés, salles à carte, cartes d'accès
    zombies/         NOUVEAU : notre zombie optimisé (section 4)
    monde/           carte M, décors, train, véhicules, spawn, extraction, frappes et largages
    clans/           clans, territoires, saison, hérals
    bases/           CRac (construction, raids, armoire, cœur de clan)
    events/          boss et événements de la carte
    clashwar/        tournoi des clans, version légère (section 1 bis)
    pnj/             Commandant Varga, quêtes, marchands
    boutique/        grades, boutique, casino, caisses, emotes
    interface/       écran titre, menu Échap, barre DayZ, touches
    voix/            CaraCrea (créateur de personnage + voix de proximité)
    anticheat/       Spartan (toujours un jar à part)
    banc/            bots de charge et outils de test (jamais sur le serveur en jeu normal)
  serveur/           configs, plugins, mods des autres auteurs, gunpacks
  outils/            un seul point d'entrée : `python outils/hz.py construire | verifier | livrer | banc`
  launcher/  site/   repris tels quels (ils marchent)
```

On passe de **25 mods** à **19 modules** (liste réelle et contenu de chacun : `CLAUDE.md`, section « Organisation » ;
le futur module `zombies` viendra à l'étape 4). Chaque module garde le découpage `commun / client / serveur`
d'aujourd'hui : il marche bien, le code qui décide ne quitte jamais le serveur.

**Règle d'or pour la carte :** les identifiants des blocs posés sur la carte (`heraliz_decors:…`,
`heralisz_utils:…`, `heraliz_loot:…`, `crac:…`, `cfm:…`) **ne changent jamais**, même si leur code change de
module. Sinon les blocs disparaissent de la carte.

---

## 3. Mettre à jour sans tout couper

Ce qu'on peut faire, et ce qu'on ne peut pas faire :

| Changement | Comment | Joueurs déconnectés ? |
|---|---|---|
| loot, prix, raretés, zones, events, quêtes, réglages des zombies, textes | **rechargé à chaud** : `/hz recharger [module]` ou tout seul quand le fichier change | **non** |
| code Java (nouveau comportement, nouvel objet) | redémarrage obligatoire (limite de Forge) | oui, mais en douceur |

- **Données vivantes** (dans le noyau) : chaque module déclare ses fichiers de données. Le noyau les surveille,
  les relit, les **vérifie avant de les appliquer** : un fichier cassé ne casse rien, l'ancien reste et le staff
  reçoit l'erreur. Ce qui touche l'affichage est renvoyé aux joueurs connectés.
- Donc on **sort du code tout ce qui peut être une donnée** : c'est ça qui fait gagner le plus.
- **Redémarrage doux** pour le code : `/hz redemarrer 5` = compte à rebours, sauvegarde, puis le launcher
  **reconnecte tout seul** les joueurs dès que le serveur est revenu.

---

## 4. Performances

**Distance de vue :** bloquée à **8** (serveur + curseur des joueurs plafonné à 8), simulation à 6.

**Props (LOD) :**
- Aujourd'hui, les conteneurs de loot sont dessinés **à chaque image**, un par un (231 000 faces pour 246
  modèles). C'est le plus gros coût côté joueur.
- Les props **qui ne bougent pas** deviennent des modèles « cuits » dans le décor : dessinés une fois, presque
  gratuits ensuite.
- Il y a 3 niveaux de détail : le modèle complet de près, un modèle allégé à moyenne distance (fait
  automatiquement par un outil), rien au-delà.
- Les props cachés derrière un mur ne sont pas dessinés.

**Zombie maison** (remplace 3 mods) :
- **IA à niveaux :**
  - à moins de 32 blocs d'un joueur : IA complète ;
  - entre 32 et 64 blocs : il marche vers sa cible, chemin recalculé rarement ;
  - au-delà de 64 blocs : **endormi**, il ne coûte rien.
- **Pathfinding avec budget :**
  - une file d'attente commune avec un nombre maximum de calculs par tick ;
  - des chemins courts et réutilisés ;
  - un groupe de zombies qui suit la même cible partage le même chemin.
- **Plafonds :** au plus N zombies par joueur et sur toute la carte, et plus aucune apparition si le tick dépasse
  le budget.
- On garde les tenues, les attaques esquivables et le peuplement déjà faits.

**Moniteur** (noyau, en plus de spark) :
- chaque système de nos mods mesure son temps par tick : `/hz perf` montre le top, mod par mod ;
- un tick trop long déclenche **un profil spark tout seul**, avec un résumé pour le staff (Discord) ;
- les **fuites** sont surveillées : taille des listes internes, entités et blocs animés par tronçon, mémoire. Ce
  qui grossit sans jamais redescendre est signalé.

**Banc de charge :**
- **200 bots** dans le serveur (ils tirent, courent, fouillent, se soignent) pendant que le moniteur enregistre :
  rapport avec TPS, top des systèmes, mémoire ;
- plus tard, des bots **réseau** (vrais clients sans écran) pour tester aussi la bande passante.

---

## 5. Ordre des travaux

| Étape | Contenu | Résultat |
|---|---|---|
| 0 | Socle : dossier, Git, fabrication unique, outil `hz`, CLAUDE.md court | on peut fabriquer |
| 1 | Noyau, ClashWar léger sorti de Matchs, retrait du reste de Matchs et de Wonder | les mods ne dépendent plus de Matchs |
| 2 | Reprise des modules un par un, code mort retiré ; inventaire des **413 commandes** (liste à garder validée par vous) | serveur DayZ complet, propre |
| 3 | Distance 8, moniteur, données vivantes, redémarrage doux | maj sans couper, lags visibles |
| 4 | Zombie maison, retrait des 3 mods tiers | zombies sans lag |
| 5 | LOD des props | FPS des joueurs |
| 6 | Banc 200 bots | test avant chaque ouverture |
| 7 | Mise en ligne simplifiée (7 workflows au lieu de 16, 26/10 : `docs/MISE_EN_LIGNE_GITHUB.md`) | envoi au Ptero en un clic |

L'ancien dossier reste intact à côté : on y pioche, on n'y touche pas.

---

## 6. Où on en est

**Étape 0 — faite (24/10)**
- Dossier à part, sans Git ni GitHub au début (choix du propriétaire) ; mis sur GitHub le 26/10 à la place de l'ancien dépôt.
- Une seule fabrication Gradle pour tous les modules (`settings.gradle`, `gradlew` à la racine).
- Une seule copie des jars des autres auteurs dans `libs/` (avant : une copie par mod, avec des suffixes).
- Les modules dépendent les uns des autres par le code source, et non plus par de vieilles copies de jars.
- Outils sans usage retirés (14 scripts), chemins `mods/` → `modules/`.
- `CLAUDE.md` court.

Les modules gardent pour l'instant le nom et le découpage des anciens mods. Le regroupement en ~15 modules se fera à
l'étape 2.

**Étape 1 — faite (24/10)**
- **Noyau** `heraliz_noyau` créé. Il porte :
  - le tag de combat et son indicateur « EN COMBAT » ;
  - déconnexion en combat = mort ;
  - les commandes refusées en combat, à terre et sur la carte ;
  - le blocage des tirs quand on est à terre ;
  - l'état « en match » pour tout le serveur ;
  - le kit d'interface ;
  - la commande du staff `/combat`.
- 12 mods rebranchés sur le noyau. Plus aucun ne dépend de Matchs ni de Wonder.
- **ClashWar léger** `heraliz_clashwar` :
  - élimination au meilleur des 5 manches, changement de côté aux manches 3 et 5 ;
  - choix de classe à chaque manche, zone qui se resserre en mort subite ;
  - gardés : match pour la 3e place, podium, regarder un match, classement des clans ;
  - retirés : duels, zombies, bombe, killcam, `/groupe`. Ressources passées de 42 Mo à 1,3 Mo, environ 790 textes
    inutiles retirés ;
  - hors tournoi, plus aucun parcours des joueurs.
- Wonder et son gunpack retirés.
- Arènes : la dimension devient `heraliz_clashwar:arenes`. Au premier démarrage, les arènes sont reposées une fois,
  avec un budget de blocs par tick.
- Les anciennes données du dossier `heralisz_matchs/` du serveur (réglages, classement, coffres en attente) sont
  reprises toutes seules.

Rien de tout cela n'a été testé en jeu.

**Étape 2 — faite (24/10)**
- **24 mods regroupés en 19 modules**, la liste est dans `CLAUDE.md` :
  - fusionnés : `equipement` (armures + composants), `monde` (utils + décors + extraction + frappes), `boutique`
    (grades + casino + caisses), `interface` (interface + barre DayZ) ;
  - les autres modules ont été renommés par usage (`armes`, `bases`, `clans`, `events`, `pnj`, `survie`, `voix`…) ;
  - chaque mod garde son modId et sa version : rien ne change pour la carte ni pour les inventaires.
- Différence avec le plan : sacs, bunker, emotes et anticheat restent des jars à part, parce qu'ils contiennent du code
  d'autres auteurs (licences GPL / LGPL).
- Une base de fabrication commune (`gradle/module-coupe.gradle`). Les modules simples n'ont plus qu'un court fichier qui
  liste leurs dépendances, et les versions communes sont à la racine.
- Commandes : inventaire dans `docs/COMMANDES.md` (environ 90 commandes de premier niveau). Retirées, après accord :
  - `/emotetest`, `/group`, `/roll`, `/armory` ;
  - `/n07 zombies` et les réglages d'essai du boss ;
  - `/reanimation down|bot` (gardés : `execution`, utilisé par les joueurs, et `revive`).
- **Bots de charge** sortis dans le module `banc`, livré à part. La sécurité de l'étiquette des bots est passée dans le
  noyau : elle s'applique même sans le banc.
- Bancs d'essai de développement retirés des jars (18 classes : captures automatiques, auto-tests, anciens menus) et
  traces de débogage du camo.

Rien de tout cela n'a été testé en jeu.

**Boucle de jeu, quêtes, perf — lot du 24/10 (soir)**
- Petits réglages : soins plus fréquents dans le loot (T1 10 pour 100 conteneurs … T7 16), clan à 1 750 hérals, `/r`,
  F3+B seulement en créatif et spectateur, zombies deux fois moins forts et entendus à 11 blocs au plus, main nue 15 %
  plus petite, distance de vue 8 (serveur et joueurs).
- Caisses : objets disparus et objets de Minecraft retirés ; caisses de quêtes, vote, event et boss refaites
  (hérals, soins, composants, fragments, kits, capsules, clés, grade Rescapé 7 jours très rare) par `outils/caisses.py`.
- Quêtes de Varga : les 6 quêtes impossibles réparées (15 poudre, 19 plaques, 30 frappe aérienne, 33 blindage, 34 tireur
  à 40 m, 37 objet Légendaire), quête 12 allégée, grades annoncés = grades donnés ; moteur : étape refusée s'il manque
  des points de quête, exécutions toujours comptées, tous les silencieux reconnus.
- **Recycleur** (PNJ `recycleur`, `outils/recyclage.py`) et **revente élargie chez Mila** (armes, armures, sacs, grenades,
  accessoires, à petits prix ; un objet est exclu seulement s'il est identique à un article vendu).
- **IA allégée des zombies** (`IaZombies` : plus de village la nuit, seulement les joueurs comme cibles, recherches de
  chemin plus courtes, objectifs revus tous les 6 ticks) et **`/diag`** (rapport automatique à chaque blocage, profil en
  direct ; ticks en médiane, 95 % et 99 % comparés aux objectifs 30 / 40 / 50 ms).
- Bases : les brindilles ne coûtent plus d'entretien (la première base disparaissait en 30 min), 15 planches dans le
  matériel de la première base, et le Bâtisseur vend l'armoire (500, si elle est perdue), la caisse en métal (150) et le
  cœur de clan (1 000 ; avant : créatif seulement). CRac garde ses règles (une armoire par joueur, un cœur par clan).
- Fabrication : compilation des mixins toujours complète (une compilation partielle avait oublié la table de
  correspondance de `monde`).

Reste repéré (pas encore fait) : `/rtp` et `/zone base` chargent des tronçons d'un coup (pics de lag possibles), table
vanilla du largage d'airstrike (pommes d'or, diamants…), kit de clan en double après un plantage, tutoriel manqué après
un redémarrage juste après une création de clan, objets rangés dans la caisse de la cabane d'essai effacés avec elle.

Rien de tout cela n'a été testé en jeu.

**Lot du 25/10 (« fait le reste selon mes critères »)**
- **Recycleur 3D** : rendus faits (`maquettes/recycleur/`, `modules/pnj/outils/recycleur_modele.py`), en attente de
  validation. Rien n'est installé en jeu.
- **Petits correctifs** :
  - `/rtp` ne génère plus de terrain et charge au plus 6 tronçons par appel ;
  - largage aérien avec notre loot (arme Rare ou Épique, munitions, matériel militaire) ;
  - matériel de la première base enregistré tout de suite (plus de double après un plantage) ;
  - tutoriel du chef gardé s'il est déconnecté ;
  - objets de la caisse de la cabane d'essai rendus.
- **Étape 6 en partie** : `/bots scenario` (bots qui construisent aussi), rapport de charge complet.
- **Étape 3 faite** :
  - moniteur `/diag mesure`, `/diag fuites`, `/diag client` ;
  - `/recharger`, tables de loot modifiables sans redémarrer ;
  - `/redemarrage` avec reconnexion automatique des joueurs.
- **Étape 4 en partie** (zombie maison) : niveaux d'IA selon la distance, budget et partage des chemins, variétés
  (coureurs, costauds). Reste : rampant maison, après rendus.
- **Étape 5 revue** : les conteneurs T2 à T6 étaient déjà cuits dans le décor, et les T1 sont des modèles de blocs. Ajouté :
  leurs faces posées sur un bord du bloc sont cachées par Minecraft quand un bloc plein les recouvre (sans changement
  visuel). Les 3 niveaux de détail demandent des modèles allégés à valider en rendus : à décider après une mesure en jeu
  avec `/diag client`.
- **Fabrication** : mixins du serveur avec leur propre table de correspondance (`<jar>-serveur.refmap.json`).

Rien de tout cela n'a été testé en jeu.

**Liste du 25/10 (commandes, panel, chat, détails de jeu)**
- **Kits** :
  - le kit Survivant est à tous, gradés compris, avec sa propre recharge ; les kits de la boutique ont la leur ;
  - kit de départ : nourriture toujours bonne (`FoodState:0`), aliments du loot (porc en boîte, cola).
- **Sacs à dos** :
  - un sac vide entre dans la planque et le coffre VIP ;
  - un sac plein n'est jamais vendu, recyclé ni échangé : il est rendu.
- **Tirs** : les sons des gunpacks sont passés en mono chez le joueur au chargement (149 des 219 sons de tir étaient en
  stéréo, donc entendus partout sans direction). Les fichiers des auteurs ne sont pas touchés.
- **Effets** : les effets de geckolib222 sont traduits (saignement, jambe cassée, infections, radiations…).
- **Grades du staff** : Fondateur 5, Admin 4 (nouveau), Modérateur 3, Helpeur 2 (ex-Helper), Guide 1 (nouveau).
- **Coffres** (audit des duplications) :
  - planque et coffre VIP : enregistrés en même temps que le joueur à la fermeture ; refusés en combat, à terre et en
    match ; objets de match refusés ;
  - coffre du clan : écrit avec chaque sauvegarde du monde ; un fichier illisible n'est plus jamais écrasé (coffres
    fermés, copie mise de côté) ;
  - caisses : récompense retirée sur le disque avant d'être donnée ;
  - boutique : un achat livré en partie n'est plus remboursé automatiquement (le staff règle) ;
  - `/inspecter` : la vue du sac suit le vrai sac, changements toujours enregistrés, refus avec un dépôt de match.
- **Maquettes validées le 25/10, puis codées** (suite ci-dessous) : panel du staff, chat à canaux, bandage et attelle,
  recycleur.
- **Audit des commandes** : `docs/AUDIT_COMMANDES.md` (proposition à valider) et inventaire écrit par
  `outils/commandes.py`.

Rien de tout cela n'a été testé en jeu.

**Lot du 25/10 (suite : panel, chat, recycleur, soins, faim, clans, réapparition)**
- **Panel du staff** (touche P, ou `/staff panel`) : actions selon le grade du staff (Guide 1 … Fondateur 5 ; un op 4 sans
  grade compte comme Admin), réglées par le Fondateur dans l'onglet Permissions (`heraliz_grades/panel_staff.json`).
  Sanctions par LiteBans joué par la console au nom du staff (`--sender`), ban PC par Spartan, journal
  `heraliz_grades/journal_staff.log`, onglet Historique et salon du staff Discord. Donner un grade : Fondateur ET op 4,
  jamais le grade Fondateur, 10 changements en 10 minutes au plus. Un staff ne sanctionne jamais un staff de rang égal ou
  supérieur. « Voir l'inventaire » ouvre `/inspecter` en lecture seule. Ancien panneau des op (`EcranPanel`) retiré.
- **Chat** (canal réseau `heraliz_grades:chat`) : Proximité (48 blocs) / Global / Clan, Tab dans le chat ; sur Mohist, le
  message est pris à la fin de l'événement de chat de Bukkit (AuthMe, mutes LiteBans respectés), puis réparti par le
  serveur. Affichage : 4 lignes qui s'effacent en 8 s, jamais par-dessus une interface ; chat ouvert en panneau de verre
  avec onglets et messages non lus ; annonces d'extraction en notification en haut à gauche ; comptes rendus de
  commandes des autres op masqués. `/cc` retirée.
- **Recycleur** : bloc `heraliz_quetes:recycleur` (2 × 1, modèle écrit par `modules/pnj/outils/recycleur_modele.py
  --installer`), mêmes règles que le PNJ recycleur, incassable en survie.
- **Soins** : Bandage (Commun : saignement + 2 cœurs) et Attelle (Peu commun : jambe cassée), modèles en main écrits par
  `modules/armes/outils/soins_rendus.py --installer` (squelette et animations d'« Attelle et bandage ») ; « Attelle et
  bandage » passe Rare ; dans les conteneurs de soins et chez Jacqueline ; anciens pansements d'ArkaniaZ changés en Bandage.
- **Faim** : deux fois plus lente (`[survie] faim_facteur` = 0,5, `FaimMixin` de `noyau`).
- **Clans** : 4 membres au départ, 8 au plus (paliers aux niveaux 10/20/30/40, 455 000 hérals) ; aucune alliance.
- **Réapparition et /home** : écran de mort « Au camp » / « À la base » ; `/home` automatique devant l'armoire ; délai
  unique de 8 min partagé (`TeleportBase`, gardé avec le monde, jamais remis à zéro par la mort ; l'arbre du clan le
  ramène à 7, 6, 5 min) ; droit de clan « Se téléporter à la base » (`Perm.BASE_TP`, membre et plus par défaut).
  Les réductions de téléportation des grades de la boutique ne s'appliquent plus qu'à `/tpa`.

Rien de tout cela n'a été testé en jeu.

**Audit des interfaces du 25/10** (85 écrans relus, textes mesurés avec les vraies polices dans les 3 langues)
- Environ 60 défauts corrigés : textes qui sortaient de leur case (surtout en russe), chevauchements, listes sans
  défilement (journal des quêtes, kits, invitations, membres d'un groupe vocal, caisse du casino…), écrans qui pouvaient
  sortir de l'écran (dialogues), messages vides manquants, unités et textes écrits en dur, Échap qui perdait des réglages
  non enregistrés (éditeur des grades, bons, réglages de l'usure : deuxième appui demandé), roue de ping qui affichait
  « ? » en russe (lettres cyrilliques ajoutées à son atlas), boutons dont le texte n'était jamais coupé (kits
  HzButton). Infobulle du kit d'interface corrigée aussi dans le kit de référence (`noyau`), recopié par
  `outils/copier_kit_ui.py`.
- Nouveaux aperçus « rien ne déborde » : `modules/boutique/outils/apercu_staff.py` (panel du staff) et
  `modules/bases/outils/apercu_mort.py` (écran de mort), en plus de ceux de la boutique, des caisses et de la Table
  d'armurier.
- Reste (chantier à part) : beaucoup d'anciens écrans agrandissent encore leurs textes avec `pose().scale` au lieu d'une
  police à la bonne taille (règle des interfaces) : `Ui.gros` / `Ui.tile` des kits, carte du monde (`Texte`), roue des
  frappes, HUD des matchs, quêtes (dialogue, journal, marchand, radio), atelier d'arme, emotes, joueur à terre.

Rien de tout cela n'a été testé en jeu.


**Saison et boss — 25/10 (après-midi)** (failles de `docs/GAME_DESIGN_25OCT.md` §3.2)
- **Boss** : 60 points au lieu de 150, et par clan et par jour (heure de Paris) : 1er kill de ce boss 100 %, 2e 20 %,
  ensuite 0 (`boss_coefficients` de `saison.json`, compteur gardé dans `saison_etat.json`, remis à zéro à minuit).
- **Quêtes de clan « Jouer N min »** : seulement les minutes passées sur la carte, en survie, où le joueur a marché au
  moins 10 m ou agi (dégâts, cache fouillée) ; avant, tout membre connecté, même AFK au spawn, la faisait avancer.
  **« Parcourir N km »** : seulement à pied (ni véhicule, ni train, ni nage, ni vol).
- **Boss mis de côté pour l'ouverture** (choix du propriétaire : pas posés sur la carte, pas équilibrés, coûteux) : le
  module `events` est fabriqué mais livré nulle part (`MODULES_DE_COTE` de `outils/construire.py`). Aucun autre module
  n'en dépend ; aucun de leurs blocs sur la copie v3 de la carte. Sans eux, les quêtes de clan « Vaincre un boss » ne
  sont plus tirées. Restent dans le panel du staff les actions « réveiller / remettre un boss » (elles répondent
  « impossible »).

Rien de tout cela n'a été testé en jeu.

**Saison visible et Purge discrète — 25/10 (soir)** (maquettes validées : `maquettes/saison/`, `modules/clans/outils/apercu_saison.py`)
- **Onglet « Saison »** de l'écran du clan (K), à la place de « Classement » (qui classait par niveau) : classement de la
  saison (PVE / PVP / events et place dans chacune, somme des places), état du clan (place, points, cœur, saison) et
  « Gagner des points » : le barème réel envoyé par le serveur (`saison/SaisonVue`, paquet `SaisonS2C`, canal des clans
  version 4). Une nouvelle source de points ajoute sa ligne dans `SaisonVue.bareme()` (voir `docs/EVENEMENTS.md`).
  `/clan top` reste le classement par niveau, dans le chat.
- **Capture d'un drapeau = points de saison** : 3 × valeur de la ville × difficulté des vagues (PVE) ; prise à un clan qui
  la tenait depuis 60 min : × 2 en PVP ; même zone et même clan : une fois par 12 h ; mêmes deux clans sur la même zone :
  une fois par 48 h ; 40 points de capture par clan et par jour au plus (section `capture` de `saison.json`).
- **Purge** : la barre rouge de Minecraft est remplacée par une petite icône en haut à gauche (point rouge qui respire,
  « PURGE », temps restant, trait du temps écoulé ; `client/PurgeHud` de CRac, paquet `S2CPurge`, protocole 24). Le
  panneau de zone des clans et les annonces d'extraction se posent dessous (`PurgeHud.hauteur()` par réflexion). La
  Purge était déjà automatique (`purge.json` : vendredi 18-21 h, samedi et dimanche 15-22 h).
- **Études** : `docs/EVENEMENTS.md` (guide de branchement de tout événement, points obligatoires) et
  `docs/EVENEMENT_OIL_RIG.md` (événement majeur de la plateforme pétrolière, à valider avant tout code).

Rien de tout cela n'a été testé en jeu.

**Événements et saison automatique — 25/10 (soir)**
- **Saison sans commande** : la première commence toute seule (premier démarrage), sans date de fin ; `/saison reset`
  (puis `confirmer`) remet tous les clans à 0 et relance aussitôt une saison ; `/saison fin 25/12/2026 20:00` programme la
  fin (annoncée à tous ; la saison est figée et archivée à cette heure), `/saison fin annuler`. `/saison commencer` et
  `/saison terminer` n'existent plus ; `/wipe tout` relance aussi une saison.
- **Module `evenements`** (`heraliz_evenements`, 20e module) : Directeur (planning `serveur/config/heraliz_evenements/
  planning.json`, conditions : joueurs sur la carte, pas de ClashWar, un seul exemplaire ; journal et mémoire hors du monde
  dans `heraliz_evenements/` ; lieux posés en jeu, `/event point …`) et **largage public** : avion cargo et caisse des
  frappes aériennes, annonce par secteur de la carte M (notification en haut à gauche, comme les extractions), 8 points
  d'événement au clan du premier qui ouvre la caisse, 3 par clan et par jour (`SaisonApi.evenement`, barème
  `events.points` / `events.par_jour`). `/event` montre aux joueurs les prochains événements.
- **Fusée d'extraction** (Terminal d'extraction) en loot Épique : T5, T6, T7 (`EXTRAS` de `outils/loot_armes.py`).
- Boss mis de côté, études : `docs/EVENEMENTS.md` (zone chaude selon les règles du propriétaire), `docs/EVENEMENT_OIL_RIG.md`.

Rien de tout cela n'a été testé en jeu.

**Audit avant ouverture du 25/10** (détail : `docs/AUDIT_25OCT.md`, mise en ligne : `docs/DEPLOIEMENT.md`)
- Vrai serveur Forge 47.3.12 démarré sur un PC avec les jars livrés (démarre et s'arrête proprement) ; journaux de
  l'ouverture du 24/10 lus sur le Ptero (tick médian ~40 ms à 55 joueurs, gel de 6 s à une sauvegarde) ; dépendances de
  tous les jars contrôlées.
- Relecture de tous les modules (admin et joueur) : environ 40 bugs corrigés, dont les bons de caisse utilisables une
  seule fois, le menu des kits figé, `/redemarrage` qui tuait en combat, le joueur réanimé qui revenait à terre, la
  sanction d'un staff supérieur, le vanish à vie, le fichier des clans écrasé, le « fantôme » du ClashWar.
- Perf : pose des arènes sans création de tronçon sur le fil serveur (pire tick 336 → 49 ms mesuré), coffres personnels
  écrits sur un fil à part, `sync-chunk-writes=false`, monstres naturels 70, herbe sans faces invisibles (−67 %), textures
  d'armure inutiles retirées de la grande texture, mémoire par défaut du launcher 4 / 5 / 6 Go.

Rien de tout cela n'a été testé en jeu.

**Lot du 26/10 (nuit) : butin des zombies, onglet Divers, frappes aériennes, recharges de TP, quêtes du jour**
(détail et chiffres : `docs/GUIDE.md` §14 à §17 ; essais à faire : `docs/TESTS_EN_JEU_26OCT.md`)
- **Butin des zombies** (`loot` : `ButinZombies`, `EvenementsLoot` ; `outils/loot_zombies.py`) : table à part des
  conteneurs, ordinaire et militaire, environ la moitié de la rentabilité des conteneurs par heure, munitions déduites ;
  fragments de cartes d'accès (vert 0,4 %, bleu 0,16 %, rouge 0,089 % par zombie, 6 par jour au plus) ; anti-farm
  (origine du zombie, part des dégâts, kills par heure et par carré, état gardé hors du monde). Zombies remplacés par une
  apparition naturelle marqués `heraliz_naturel` (`monde`).
- **Onglet « Divers » de la Table d'armurier** (`outils/crafts.py`, `armes`) : cartes verte / bleue / rouge (4 fragments),
  jumelles de désignation, munitions de frappe légère / lourde / spéciale ; anciennes recettes d'établi des cartes
  retirées (`bunker`).
- **Frappes aériennes** (`monde`, `bases`) : jumelles Légendaires, fabriquées seulement ; munitions à 2 utilisations ;
  recharge gardée dans les données du joueur ; seulement sur la carte, hors zone VIP et hors match ; aucune casse de bloc
  ni feu ; joueurs sous un toit ou dans une base épargnés ; CRac ignore ces explosions ; jumelles suivies par
  l'anti-duplication de Spartan.
- **Recharges de TP** (`boutique`, `bases`) : `/home` et `/tpa` 8 min, −30 s / −1 min / −1 min 30 selon le grade boutique,
  sans cumul, gardées hors du monde ; définitions des grades en version 3 (migration une fois).
- **Quêtes du jour** (`pnj` : `Journalieres`, `EcranJournalieres` ; `outils/quetes_journalieres.py` ; canal des quêtes ;
  maquettes validées) : une difficulté par jour, trois propositions, une acceptée, un tirage de récompense par réussite,
  remise sans perte ni double (clés : `ClesApi.crediterUneFois`, `boutique`) ; ajoutée aux fonctions du noyau
  (`journalieres`).
- Recalage du 26/10 au matin : les conteneurs ont été enrichis la même nuit (quantités comptées pour tout objet) ; le
  simulateur de `loot_zombies.py` compte maintenant les quantités comme le serveur, et les tables des zombies ont été
  remontées (`ECHELLE`).

Rien de tout cela n'a été testé en jeu (fabriqué et vérifié : `verifier.py`).

**Liste des modifications pour la bêta (26/10)** (essais à faire : `docs/TESTS_EN_JEU_26OCT.md`, section « Bêta »)
- **Chat** : onglets Proximité / Global / Clan remontés de 4 points (`EcranChat.yOnglets`), plus sous la barre de saisie.
- **Munitions** : l'interface d'arme de TaCZ (image de l'arme, version de TaCZ dessous) est remplacée par une ligne sur le
  verre penché de l'usure, juste au-dessus d'elle (`armes`, `client/hud/HudMunitions` : chargeur, réserve comptée par
  TaCZ, mode de tir).
- **Loot** (`loot`, `outils/loot_armes.py`) : la quantité « nombre » des tables compte pour tout objet (avant : 1, sauf
  les munitions), clé « suite » (gros tas rares, `TablesCoffres`) ; quantités par sorte, rareté et palier (`QUANTITES` :
  matériaux de base, composants de fabrication, objets à revendre ; Épiques par 2 à 5 exceptionnels) ; munitions variées
  de retour dans T1 à T7 (`MUNITIONS_POUR_100`) ; plus de matériaux de base en T5 à T7 (`EN_PLUS`) ; kits de réparation
  environ 6 fois plus rares ; armes trouvées chargées à 10 % (au moins 1 balle, rien dans la chambre, `CHARGE_ARME`) et
  fiches des gunpacks lues comme TaCZ (36 armes tombaient en SEMI à 1 balle).
- **Largage** (`outils/caisses.py` `LARGAGE_*`, `monde` `SupplyCrateEntityServeur`) : arme (30 % d'en avoir deux),
  4 à 5 lots de composants I à IV, 2 à 3 lots de matériaux de base, objets à revendre, grenades, 2 à 3 munitions variées,
  soins, quantités au hasard ; chaque tas posé entier (avant : Minecraft coupait les tas en petits morceaux). Objet
  d'événement : seulement dans un largage de l'événement (`/event lancer largage`, planning) ; lancé par le staff sans
  « avec_points », objet d'essai (hérals, pas de points, `ObjetsServeur.creer`) ; bouton Largage du panel = l'événement.
- **Kits de réparation** (`monde`, `heraliz_usure`) : clic droit = atelier de réparation (`MenuReparation`,
  `EcranReparation`, aperçu « avant → après », Réparer / Annuler) ; piles de 5 ; fortune +7 % x 1, kit +18 % x 2,
  armurier +45 % x 3, jusqu'à 100 % (`Regles`, `heraliz_usure-server.toml`) ; ancien geste au curseur retiré.
- **Recyclage** (`pnj`) : chances x ((état - 15) / 85)^1,5 (neuf 1, 50 % : 0,26, 20 % : 0,01 ; `Recyclage.facteurUsure`),
  état affiché dans l'aperçu (canal des quêtes version 11) ; corde -> tissu, objets cassés -> circuit / batterie /
  ferraille / plastique, plaques -> ferraille (`outils/recyclage.py`, `COMPOSANTS_RECYCLABLES`).
- **Crafts** (`outils/crafts.py`) : C4 poudre IV, charge poudre III + 2 tissus IV, barricade en bois dans « Divers »,
  accessoires et viseurs au niveau de composant de leur rareté (comme les armes).
- **Icônes des armes** : photos 3D coupées, icônes des gunpacks ; le nom du skin reste dans l'infobulle, couleur de sa
  rareté. Depuis Tactique 2.38.0 (28/10, rendus validés `maquettes/icones_armes/`) : images `slot` des armes à feu
  rendues HORS DU JEU en 128 px depuis le vrai modèle 3D et la texture de chaque arme (de profil, canon à droite,
  penchée de 34°), **écrites** par `python outils/icones_armes.py --installer` (`--apercu DOSSIER [--armes a,b]`),
  jamais à la main ; à relancer après l'ajout d'un gunpack ou d'une arme. Couteaux non touchés (image de leur skin).
  En jeu, rien n'est photographié : `GunItemIconeMixin` dessine la même image du pack, lissée quand elle est réduite
  (`camo/client/IconesPack` : copie avec ses mipmaps). Rien testé en jeu.
- **Gunpacks** : dossiers renommés `heraliz_1_base_tacz` … `heraliz_7_vector_custom` (identifiants des objets inchangés),
  `Cislumen_Armory` (RPG-7 jamais obtenable) et `maxstuff:m82a2` (refusée par TaCZ) retirés ; anciens dossiers listés
  dans `serveur/tacz_retires.txt` (à effacer du Ptero, `docs/DEPLOIEMENT.md`) et retirés chez les joueurs par le
  launcher.
- **Anciennes versions chez les joueurs** (launcher 1.15.3, 28/10, demande du propriétaire : un joueur voyait toutes nos
  anciennes versions de Spartan, nom et jar, dans le dossier du launcher) : Helios range chaque version d'un mod dans
  son propre dossier (`common/modstore`, chemin Maven) et ne retirait jamais un fichier sorti du pack. Après chaque
  préparation réussie, tout ce qui n'est pas un fichier du pack actuel sort de `common/modstore` et de
  `instances/<serveur>/tacz` (anciens jars, anciens gunpacks, fichiers d'armes retirés, anciens dossiers `data/`)
  (`effacement.js` `retirerAnciennesVersions`, remplace `retirerAnciensGunpacks`). Rien d'autre n'est touché ; pack vide
  ou illisible : rien n'est effacé. La version en cours reste forcément sur le PC (le jeu en a besoin).
- **Soins** : animations propres du Bandage et de l'Attelle (`modules/armes/outils/animations_soins.py`).
- **Finishers** (`survie`) : seul le coup de grâce (et les exécutions des armes) est libre ; objet `reanimation:finisher`
  (`{Execution:"<id>"}`), clic droit = ajouté à la collection (menu =) ; 22 finishers en deux groupes partagés entre
  les caisses Vote et Quête.
- **Caisses de la bêta** (`outils/caisses.py` `CAISSES_BETA`) : camo (stickers et StatTrak ajoutés, mythiques Séraphin et
  Chaînes de l'Enfer seulement, 1,5 %), gemme (les 149 couteaux : 1 étoile Peu commun … 4 étoiles Mythique 0,12 %),
  event, vote (grades d'un jour, clés, cœur de clan, finishers, hérals limités), quête (grades de 7 jours très rares,
  finishers), émotes ; les autres caisses vidées (`DESACTIVEES`). Une émote déjà débloquée ne sort plus d'aucune caisse.
- **Casino** (`serveur/config/heralizcasino/casino.json`) : caisse = clés, camos, stickers, StatTrak, finishers, émote au
  hasard, grades à la journée, matériaux de base, cœur de clan ; une commande sans {amount} est jouée une fois par unité.

Rien de tout cela n'a été testé en jeu.

**Perf du 27/10** (relevé spark et `gc.log` de 8 h 43 sur le Ptero, sans joueur ; pas de fuite : ~4 Go vivants, stable) :
- Ptero : `sync-chunk-writes=false` posé à la main (il était resté à true : la mise en ligne ne touche pas
  `server.properties`). Mémoire Java réglée à 32 Go pile par l'hébergeur : Java coupe alors les « Compressed Oops »
  (pauses de 85 à 165 ms serveur vide) ; à lui demander 16 Go et les réglages Aikar (`docs/DEPLOIEMENT.md`).
- Sauvegardes hors du fil serveur : clans, saison, événements, hélicoptère, rotation du loot, butin des zombies,
  statistiques du casino, registre des joueurs de Spartan. Relevé sur le fil serveur (arbre JSON ou NBT neuf), texte,
  compression et écriture sur un fil par module (`EcrituresFond`), attendus à l'arrêt ; ce dont la suite dépend
  (opérations d'argent, points d'un événement) attend toujours que le fichier soit écrit.
- Formes gardées : portes du bunker (`rotateShape`, forme entière par sens et par état), cases de mur de CRac (forme
  avec les objets adossés gardée jusqu'au prochain changement d'un objet adossé, `Adosse.version()`), armoire et caisse
  en métal (tables par sens et par décalage) ; plus de ticker vide sur chaque bloc de porte.
- Skins et camos : la carte de positions est calculée sur un fil à part (`HeraliZ-camo-carte`) et envoyée à la carte
  graphique en demi-flottants (8 Mo au lieu de 16) ; l'arme est dessinée sans skin quelques images au lieu de figer le
  jeu. Préchauffage des armes skinnées de l'inventaire dont TaCZ a déjà chargé le modèle (`PrechauffageCamo`).
- Succès de Minecraft coupés (`SuccesMixin`, `[succes] actifs` de `heraliz_noyau-server.toml`).
- Noyau 1.0.3 (après mesure : le gel des sauvegardes restait de 110 à 230 ms serveur vide, surtout Minecraft) :
  level.dat (400 Ko avec les registres de Forge) compressé et écrit sur le fil « HeraliZ-level-dat » (`LevelDatFond`,
  `LevelDatEnFondMixin`), écrit tout de suite à l'arrêt ; zone de départ (2 209 tronçons gardés chargés par Mohist dans
  chaque monde, même vide) libérée pour le monde du tutoriel des bases et celui des arènes (`MondesSansSpawn`,
  `[perf]` de `heraliz_noyau-server.toml`). Essayé sur le serveur de test du PC (Forge, sans Mohist) : level.dat lisible
  après une sauvegarde en marche, l'arrêt et le redémarrage.

Rien de tout cela n'a été testé en jeu.

**Règles du propriétaire du 27/10** (Événements 1.1.2, Clans 3.7.2, Quêtes 1.13.1) :
- **Événements refusés** (Directeur) : un événement du planning qui ne peut pas partir (pas assez de joueurs, ClashWar,
  aucun lieu…) n'est plus réessayé 15 min plus tard : son attente repart pour un intervalle entier, comme s'il avait eu
  lieu (largage : 3 h 30 ; hélicoptère : 4 h de pause puis 2 h de hasard ; créneau à heure fixe : le suivant). Journal :
  « pas lance (…), prochain essai a HH:MM (intervalle normal) ». Clés `reessai_apres` (planning.json) et
  `reessai_minutes` (helico.json) retirées et ignorées. `/event lancer`, `/event helico lancer|prochain` inchangés.
- **Territoires tenus 2 h** : après une capture, la zone est protégée pendant toute la tenue (`tenue_min` = 120 de
  `heralisz_clans-common.toml` ; `protection_min` ne sert plus qu'avec `tenue_min = 0`, ancienne règle), puis elle
  redevient libre toute seule (`Territoires.finDeTenue` : évènement `Perdu` marqué `finTenue`, donc plus de revenu, de
  bonus de loot ni de minutes tenues ; message aux membres connectés du clan, 3 langues) et n'importe qui la recapture.
  Le HUD (« Protégée 1 h 52 »), la carte M et `/territoire info` montrent le reste. Une zone capturée avant la règle
  redevient libre au premier passage après le démarrage. On ne prend donc plus une zone à un clan : la ligne PVP
  « Prendre une zone à un clan » de l'onglet Saison n'est montrée qu'avec `tenue_min = 0`.
- **Recycleur, paquets incomplets** : une règle « par 15 » (munitions) accepte 1 à 14 objets : le tirage d'un paquet
  complet a lieu une fois sur 15 / n, sinon rien (même gain moyen par objet, plafond et hérals compris), temps x n / 15
  (0,5 s au moins) ; les roquettes (piles de 6) se recyclent case par case ; aperçu « 6 sur 15 : moins de chances » avec
  les chances réduites.

Rien de tout cela n'a été testé en jeu.


**Casino du 27/10** (branche `casino-atm-prix`, demande du propriétaire ; à valider avant `main`) :
- **Compte de capsules** (`boutique`, `economy/CompteCapsules`, API `fr.heraliz.casino.api.CapsulesApi`, lue par réflexion
  par `/wipe` : ne jamais la renommer) : un solde par joueur, hors du monde (`heralizcasino/comptes/<uuid>.json`, écrit tout
  de suite, journal `heralizcasino/comptes_journal.log` sur le fil d'écriture du casino) ; `/capsules` (solde, `deposer
  [nombre]`, `retirer <nombre>`, à 6 blocs d'une caisse du casino ; refusée sur la carte et en combat par le noyau),
  `/casino compte voir|donner|retirer` (staff 2 / 4). Retrait refusé sans la place, dépôt sans monnaie, joueur sauvegardé
  après chaque mouvement. La caisse et l'entrée du casino prennent sur le compte ce qui manque dans l'inventaire
  (`compte.payer_caisse` de `casino.json` ; indispensable au-delà de 23 040 capsules, le plus qu'un inventaire porte) ; un
  joueur qui a des capsules sur son compte n'est pas « fauché » pour les videurs. Remis à 0 par `/wipe` et `/wipe tout`.
  **Distributeur de capsules** (bloc `heralizcasino:atm`, machine `MachineType.ATM` ajoutée en dernier, 1 bloc de large
  et 2 de haut ; rendus et écran validés : `maquettes/atm_capsules/`) : clic droit = écran du compte (`client/screen/
  AtmScreen` : solde, capsules portées, 7 derniers mouvements, déposer tout / 100 / 500 / 1 000 / un nombre tapé,
  retirer 100 / 1 000 / 5 000 / un nombre tapé) ; le serveur décide de tout (`AtmBlockEntityServeur` ->
  `CompteCapsules.deposer|retirer(..., borne)`), résultat écrit dans l'écran (jamais dans le chat), une opération par
  joueur toutes les 250 ms ; l'écran 3D de la borne montre le solde du joueur qui la regarde ; capsules animées dans
  l'entonnoir ou le bac pour tous. Modèle `AtmDesign` (rendus : `outils/apercu_atm.py`), textures dans la moitié basse de
  l'atlas du guichet, **écrites** par `outils/textures_atm.py --installer`, jamais à la main. Les 30 derniers mouvements
  sont gardés dans le fichier du compte (clé `mouvements`). `/capsules` marche aussi à 6 blocs d'un distributeur. Canal
  du casino version 6 (`ComptePacket` : solde, mouvements, résultat d'une opération) : jars joueurs et serveur de la même
  fabrication.
- **Prix de la caisse** refaits (`casino.json` ; grades à la journée, confirmés par le propriétaire : Rescapé 10 000,
  Traqueur 18 000, Roi de la Zone 30 000 le jour) ; le finisher **Main du Joker** (80 000) ne sort plus d'aucune caisse (`EXCLUSIFS_CASINO` de
  `outils/caisses.py`). Une commande de la caisse qui rend 0 (émote au hasard alors qu'il les a toutes, grade déjà à vie)
  est remboursée ; `emote debloquer` rend le nombre de joueurs qui ont vraiment reçu une emote.

**Correctifs du 27/10 (soir) : bases, combat, frappe aérienne, réanimation** (essais à faire :
`docs/TESTS_EN_JEU_27OCT_CORRECTIFS.md` ; CRac 1.25.0-beta, canal 26)
- **Coups et tirs à travers portes et murs** : coup de mêlée direct sur un joueur annulé si aucun point de son corps
  n'est visible des yeux de l'attaquant (`noyau`, `CoupsAuTravers`) ; balle TaCZ sur un morceau du corps qui dépasse d'une
  porte ou d'un mur (bras tendus, allongé) refusée si un bloc solide est entre le point touché et le corps
  (`TaczHitboxCorpsMixin`). Chez le joueur, la balle s'arrête à l'écran sur le premier bloc touché (`TaczBalleArretMixin`).
- **Bases** : plus de protection donnée toute seule aux nouvelles bases (`[protection] automatique = false`, anciennes
  retirées une fois) ; dans une base, un objet qui agit sur un bloc (barricade, mannequin…) est refusé hors CRac
  (`allowedItemsInClaims`) ; rampes sans poteaux (cases réservées inchangées) ; amélioration au marteau à 4 blocs
  (`upgradeReach`) ; moitié de mur laissée à une armoire ou une caisse posée avant le mur dessinée par la case voisine,
  et reprise quand un bloc posé avant le mur part ; zones interdites en rouge plan en main (`S2CZonesInterdites`) ;
  aide au saut des montées donnée plus tôt (`SautRebord`) ; rampe triangle posée à l'inverse (28/10, choix du
  propriétaire : côté haut vers le joueur, coin bas au fond, `Sockets.volumeRot` ; CRac 1.25.1-beta).
- **Marteau** (28/10, demande du propriétaire, CRac 1.26.0-beta, canal 27) : clic droit sur un objet posé (caisse, coffre,
  four…) = le ramasser s'il est vide (même paquet et mêmes règles que accroupi + clic droit, `C2SPickup`) ; roue sur une
  fenêtre garnie : « Retirer la vitre / les barreaux », rendus au joueur (`C2SHammer.RETIRER_INSERT`,
  `StructureService.retirerInsert`) ; clic droit sur l'armoire pendant les `claimMoveMinutes` (15) minutes après sa pose :
  elle revient dans l'inventaire avec son stock d'entretien et le palier payé à la pose (`DeplacerArmoire`, heure de pose
  dans `ClaimBlockEntity`, `PoseeLe`) ; gérer la base, hors raid, hors tutoriel ; une armoire posée avant ne se déplace pas.
- **Plancher sur demi-mur** (28/10, CRac 1.26.1-beta ; le propriétaire refuse des murs plus hauts) : joueur accroupi de
  1,25 bloc (yeux 1,1) au lieu de 1,5 (`mixin/PlayerAccroupiMixin`, commun) : comme dans Rust, on saute accroupi sur ce
  plancher sous un plafond (21 px libres). Son dessus est à mi-bloc et, avec un plancher au-dessus, il n'y a aucune case
  libre entre les deux : tout objet visé dessus est refusé avec le message (`BuildEventHandler`), l'armoire aussi quand
  une pièce la porte (`claimProblem`), un objet adossé ne part plus dans une case voisine qui ne le porte pas
  (`Adosse.caseLibre`).
- **Frappe aérienne** : animation de recharge sur une horloge régulière (`HorlogeJumelles`) ; explosion sans le calcul
  des blocs (27 000 à 55 000 lectures par bombe pour rien), créatures attirées lancées 3 par tick, mesures dans le journal.
- **Réanimation** : compte à rebours visible jusqu'à la réponse du serveur, recalé tous les 5 ticks.
- **Armes du mode Zombies** : gunpack `heraliz_wonder` resté sur le Ptero, ajouté à `serveur/tacz_retires.txt`.

Rien de tout cela n'a été testé en jeu.
