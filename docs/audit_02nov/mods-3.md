# Audit du 02/11 — volet « mods-3 » : clans, bases (CRac), ClashWar, PNJ (quêtes, marchands, recycleurs), boutique

Audit en **lecture seule** sur `main` (65e64fad) : rien n'a été modifié, fabriqué ni testé en jeu. Seul outil lancé :
`python outils/marchands.py --controle` (lecture seule) : **aucune revente ni recyclage trop rentable** (158 articles,
les deux recycleurs sous la borne des 50 %). Tout le reste vient de la lecture du code, point par point (fichier:ligne).

Lu avant : `CLAUDE.md`, `ARCHITECTURE.md`, `docs/METHODE_CLIENT_SERVEUR.md`, `docs/AUDIT_25OCT.md`,
`docs/AUDIT_COMPLET_26OCT.md`, les fiches `TESTS_EN_JEU_*` du 28/10 au 01/11 et le `git log` depuis le 25/10. Quand un
point avait déjà été signalé le 26/10 et n'est **toujours pas corrigé**, son ancien numéro est rappelé (ex. « A116 »).

Pas regardé ici (autres volets) : découpage client / serveur, commandes, site, launcher, anticheat. Pas relu en
détail : les règles de construction de CRac (géométrie, stabilité, raids), l'équité des jeux du casino.

---

## Bloquant

### 1. Quêtes de clan : 3 quêtes impossibles, 1 quête gratuite, 1 quête qu'on fait avec des comptes secondaires
- **Gravité** : bloquant (bug visible chaque jour + argent créé à partir de rien). Déjà signalé le 26/10 (A116, A117,
  A123), pas corrigé.
- **Où** :
  - « Fouiller 60 / 120 / 600 caches » (`serveur/config/heralisz_clans/quetes.json` l.53, 63, 173) : le code compte un
    clic droit sur un bloc `cfm:loot_*` (`modules/clans/src/serveur/java/fr/heralisz/clans/server/ClanEvents.java` l.71,
    436-458). Ces blocs n'existent plus (aucun dans le jar cfm ; les conteneurs sont `heraliz_loot:*`).
  - « Déposer 1 500 / 15 000 hérals » (`quetes.json` l.123, 233) : `Quetes.mouvementBanque`
    (`.../clans/server/Quetes.java` l.380-397) suit le « net déposé », mais le net ne descend jamais sous 0
    (`Math.max(0, …)` l.382) et une quête finie n'est jamais reprise.
  - « Tuer 5 / 10 / 25 joueurs » : `killJoueurValide` (`ClanEvents.java` l.253-263) ne regarde ni l'adresse IP, ni si la
    victime est armée, ni « une fois par victime et par jour » (la quête du jour de Varga, elle, le fait :
    `Journalieres.kill`).
- **Ce qui se passe en jeu** :
  - un clan sans zone (moins du niveau 5, donc presque tous au début) tire 3 quêtes du jour parmi 10 : **une fois sur deux**
    il reçoit une quête « Fouiller » qui ne peut jamais avancer ; 3 fois sur 10 la quête de la semaine aussi ;
  - « Déposer » : retirer 15 000 hérals de la banque puis les redéposer suffit (ou déposer puis retirer) : **+2 000
    hérals, +1 500 XP et +5 points de saison par semaine**, +300 hérals et +200 XP par jour, sans rien dépenser ;
  - « Tuer » : deux comptes secondaires sans clan, tués à tour de rôle (10 min entre deux kills du même couple, 60 s après
    la réapparition) : 900 hérals par jour et 2 500 par semaine à la banque.
- **Proposition** : « Fouiller » écoute la vraie fouille d'un conteneur (`EvenementsLoot.ecouterFouille` de `loot`, déjà
  utilisée par les quêtes du jour) et le code `cfm:loot_` part ; « Déposer » remplacée par une quête qui dépense
  (améliorer l'arbre, payer l'entretien…) ou retirée du catalogue ; « Tuer » avec les mêmes règles que la quête du jour
  de Varga (IP, victime armée, une fois par victime et par jour). **Effort** : petit (fichier `quetes.json` relu) à moyen.
- **Question** : on retire les quêtes « Déposer N hérals », ou on les remplace par « Dépenser N hérals pour le clan » ?

### 2. Casino : un gros gain est jeté par terre (lag et capsules ramassées par d'autres)
- **Gravité** : bloquant. Déjà signalé le 26/10 (A187), pas corrigé, alors que le compte de capsules existe maintenant.
- **Où** : `modules/boutique/src/serveur/java/fr/heraliz/casino/block/MachinesServeur.java` l.112 (remboursement) et
  l.130 (gain) appellent `ChipsServeur.give` (`.../casino/economy/ChipsServeur.java` l.106) qui jette au sol ce qui ne
  tient pas ; même chose pour les gains en attente remis à la connexion (`.../casino/event/CommonEventsServeur.java` l.53).
  Mises et plafonds de `serveur/config/heralizcasino/casino.json` : crash jusqu'à 5 000 × 1 000, machines à sous
  2 500 × 5 000, roulette 10 000 × 36.
- **Ce qui se passe** : un inventaire vide porte au plus 23 040 capsules. Un crash gagné à × 10 avec 5 000 capsules
  (50 000) jette déjà ~40 piles au sol ; un gros gain en jette des centaines ou des milliers d'un coup (gel du serveur),
  au milieu du casino où n'importe qui les ramasse.
- **Proposition** : ce qui ne tient pas dans l'inventaire va sur le compte de capsules (`CompteCapsules.crediter`,
  l.280), avec un message ; pareil pour les remboursements et les gains en attente. **Effort** : petit.

### 3. Recycleur du spawn : se déconnecter, planter ou subir un `/redemarrage` avec des objets dedans = objets par terre
- **Gravité** : bloquant (perte d'objets à chaque redémarrage pour qui s'en sert). Le 26/10 (A240) ne parlait que des
  objets à vie ; c'est en fait tout le contenu.
- **Où** : `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/MenuRecycleurServeur.java` l.198-210 : à la
  fermeture, un joueur « déconnecté » voit ses 12 cases jetées au sol (`clearContainer`). Or Minecraft (et Mohist)
  marquent le joueur déconnecté avant de fermer son écran et l'enregistrent sans ces objets. `/redemarrage` déconnecte
  tout le monde (`modules/noyau/.../maintenance/Maintenance.java` l.163). De plus, la case « à recycler » du spawn
  accepte n'importe quel objet, objets à vie compris (`.../quetes/bloc/MenuRecycleur.java` l.93 : `accepte` rend
  toujours vrai) : un couteau à vie posé là est alors gardé en mémoire seulement par Tactique, puis perdu au redémarrage.
- **En jeu** : on pose 6 armes à recycler, le jeu plante (ou le serveur redémarre) : à la reconnexion elles ne sont plus
  dans l'inventaire ; elles traînent au sol au spawn (5 min, n'importe qui les prend).
- **Proposition** : à la déconnexion (évènement de Forge, avant l'enregistrement du joueur), remettre dans l'inventaire
  ce que contient le recycleur ouvert ; refuser les objets à vie et de quête dans la case « à recycler » du spawn (comme
  celui de la carte). **Effort** : petit.

---

## Important

### 4. Recycleur de la carte : chaque clic de dépôt enregistre le joueur et la machine, sans limite de vitesse
- **Gravité** : important (lag qu'un joueur peut provoquer exprès). Nouveau (31/10).
- **Où** : `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/RecycleurCarteEntiteServeur.java` l.246-266
  (`apresClic` : `Ponts.sauverJoueur` puis `ecrire()`, fichier compressé, sur le fil du serveur) appelé à chaque clic
  par `MenuRecycleurCarteServeur.clicked` (l.120-133), sans aucune cadence.
- **En jeu** : Maj + clic en boucle (ou un clic automatique) entre l'inventaire et la machine = 20 enregistrements du
  joueur et 20 à 40 écritures de fichier par seconde. Le fichier d'un joueur avec un sac plein pèse lourd : quelques
  joueurs suffisent à faire monter le tick.
- **Proposition** : une limite (par exemple 4 clics qui changent la machine par seconde et par joueur), ou regrouper les
  écritures en fin de tick dans le même ordre sûr. **Effort** : petit.

### 5. Hérals : chaque opération d'argent « importante » fait attendre le serveur pendant la réécriture de tous les comptes
- **Gravité** : important (lag qui grossit avec le nombre de joueurs venus une fois). En partie signalé le 26/10 (A120).
- **Où** :
  - `modules/clans/src/serveur/java/fr/heralisz/economie/server/ComptesStore.java` : un seul fichier `comptes.json` pour
    tous les joueurs jamais venus (compte créé à la connexion, jamais effacé, 50 lignes de journal chacun) ; chaque
    écriture refait tout le fichier **et en recopie d'abord l'ancien** (copies `.1` à `.5`, `tourner` l.518) ;
    `sauvegarderMaintenant` (l.436, attente l.482) bloque le fil du serveur jusqu'à la fin.
  - L'attendent : banque du clan (`.../clans/server/ClanService.java` l.839-897 : `comptes.json`, puis `clans.json` et
    `joueurs.json` entiers, `ClanStore.java` l.420-424), achat, mise en vente et récupération à l'hôtel des ventes
    (`.../grades/serveur/HdvServeur.java` l.849, 984, 1235), création et dissolution de clan.
  - La banque accepte une action toutes les 200 ms par joueur (`ServerHandlers.java` l.27) : un membre qui alterne
    « déposer 1 / retirer 1 » fait 5 réécritures complètes de 3 fichiers par seconde.
- **Estimation** : environ 5 Ko par compte ; 2 000 joueurs venus = ~10 Mo écrits (et 10 Mo recopiés) à chaque opération.
- **Proposition** : petit : banque à une action par seconde ; copie de secours au plus une fois par heure au lieu de
  chaque écriture ; journal ramené à 20 lignes, comptes vides et anciens sans journal. Moyen : un fichier par compte
  (comme les capsules) ou un journal d'opérations en ajout, pour que l'attente ne porte que sur quelques octets.
  **Effort** : petit à moyen.

### 6. Boutique (PB) : une erreur 500 du site efface l'achat, sans remboursement ni trace
- **Gravité** : important (argent réel), mais seulement si le site plante après avoir débité. Signalé le 26/10 (A173).
- **Où** : `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/BoutiqueServeur.java` l.235-247 : toute réponse
  lisible qui n'est pas « ok » est prise pour un refus, l'achat en cours est oublié, rien n'est écrit au journal. Or le
  serveur demande du JSON : une erreur interne du site revient en JSON avec le code 500. L'échange `/trade`
  (`EchangeServeur.java` l.489-497) et l'hôtel des ventes (`HdvServeur.java` l.1023) traitent déjà ce cas comme « on ne
  sait pas » et redemandent le statut.
- **Proposition** : même règle que `/trade` : seul un refus clair (code 200 et ok faux, ou 404 / 409 / 422) annule ;
  le reste attend `statut` (déjà fait par `reprendre`). **Effort** : petit.

### 7. `/clan don` marche sur la carte : la valeur d'un objet Légendaire est mise à l'abri sans extraction
- **Gravité** : important (contourne le risque de l'extraction). Signalé le 26/10 (A287).
- **Où** : `modules/clans/src/serveur/java/fr/heralisz/clans/saison/SaisonCommandes.java` l.165-212 (aucun contrôle du
  lieu) ; `clan` est dans `commandes_permises` de la carte (`serveur/defaultconfigs/heraliz_noyau-server.toml` l.19).
- **En jeu** : un joueur trouve une couronne dans un T5, tape `/clan don` sur place : +1 000 XP pour son clan, plus rien
  à perdre s'il se fait tuer en rentrant. (En combat et à terre, la commande est bien refusée.)
- **Proposition** : don seulement au spawn (comme le coffre du clan). **Effort** : petit.
- **Question** : le don au clan doit-il se faire seulement au spawn, après extraction ?

### 8. Récompenses de caisses et achats jetés par terre quand l'inventaire est plein
- **Gravité** : important (un couteau Gemme payé en PB peut être ramassé par un autre). Signalé le 26/10 (A198).
- **Où** : caisses : `modules/boutique/src/serveur/java/fr/heraliz/crates/loot/RewardHelper.java` l.64
  (`giveItemToPlayer` = au sol) ; caisse du casino (objets vendus) : `.../casino/block/entity/CashierBlockEntityServeur.java` l.110 ; marchands, achat par 5 : `.../quetes/serveur/Marchands.java` l.156 (`p.drop`).
- **Proposition** : caisses : ce qui ne tient pas reste dans `PendingRewards` avec un message (remis quand il y a de la
  place) ; marchands et caisse du casino : refuser l'achat sans la place (comme les kits et le retrait de capsules).
  **Effort** : petit.

---

## Mineur

### 9. Caisses ouvertes gratuitement en créatif, avec de vraies récompenses
- **Où** : `serveur/config/heralizcrates/general.json` (`consume_key_in_creative` : false) ;
  `.../crates/opening/CaseOpening.java` l.113 et 157. Signalé le 26/10 (A200).
- **Ce qu'un staff obtient** : en créatif, des ouvertures de caisse Gemme sans clé (couteaux à vie, grades…).
- **Proposition** : mettre `true`. **Effort** : petit (réglage).

### 10. Code et contenu morts encore livrés
| | Où | Ce que c'est | Effort |
|---|---|---|---|
| a | `modules/bases/src/serveur/java/fr/heraliz/crac/build/test/` (11 fichiers, 4 739 lignes) + `data/crac/structures/*.nbt` | Tests automatiques de CRac (GameTests) dans le jar du serveur (signalé le 26/10, T-arch-11) | petit : les sortir dans un ensemble de sources jamais livré |
| b | `modules/bases/src/serveur/resources/data/crac/dimension/bases.json`, `dimension_type/bases.json`, `worldgen/` (7 biomes, bruits, relief) ; `MondeBasesServeur`, `NatureBases`, `BasesReglages` [bases], reprises dans `Tutoriel.java` l.185-190 et 455-466 | L'ancien monde des bases `crac:bases` (abandonné le 21/10, tutoriel déplacé dans `crac:tuto` le 28/10) est encore créé, tenu et enregistré à chaque démarrage | moyen (après avoir vérifié que plus personne n'y est) |
| c | `modules/bases/src/serveur/resources/data/crac/recipes/` (11) + `advancements/` | Anciennes recettes de Minecraft de CRac (C4 au netherite et à la TNT, armoire aux blocs de fer, clous faisables dans la grille 2 × 2…) ; signalé le 26/10 (A142) | petit |
| d | `modules/boutique/src/serveur/java/fr/heraliz/casino/data/Jackpot.java` (`contribute` et `win` jamais appelés), `JackpotPacket` envoyé à chaque connexion, `/casino jackpot`, rendu de la machine « Fruits d'Or » disparue (`client/.../machines/FruitsDesign.java`, 473 lignes, dont seuls deux petits outils servent encore) | Jackpot affiché nulle part ailleurs, jamais alimenté ni gagnable (A193) | petit |
| e | `modules/boutique/src/client/java/fr/heraliz/crates/client/render/scene/crates/` (MereDesRuines, PorteurDePeste, SujetN07, Sylvorn, Trauma, Chantier, Largage, Booster : ~5 000 lignes) + ~8 Mo de textures `textures/crate/` | 8 caisses sans aucune récompense (`build`, `largage`, `mere_des_ruines`, `porteur_de_peste`, `sticker`, `sujet_n07`, `sylvorn`, `trauma` : listes vides) téléchargées par tous les joueurs | décision (voir question) |
| f | `modules/boutique/src/commun/java/fr/heraliz/grades/staff/ActionStaff.java` l.48-49 (« réveiller / remettre un boss ») ; `saison.json` (`boss`, liste des 5 boss, `oil_rig`) ; `Bareme.java` l.128 ; quêtes `KILL_BOSS` | Restes des boss mis de côté et de l'Oil Rig (étude seulement) | petit |
| g | `modules/clashwar/src/serveur/java/fr/heraliz/clashwar/moteur/arenes/Protections.java` l.61-69 | Commandes permises en match : `zombies`, `duel`, `groupe` (retirées) ; la liste `COMMANDES_BLOQUEES` ne sert à rien (la liste blanche suffit) ; constantes `TYPE_ZOMBIES`, `CLASSE_ZOMBIES` | petit |
| h | `modules/clans/src/serveur/java/fr/heralisz/economie/vault/VaultPont.java` (360 lignes), `pont_vault = true` | Pont pour le plugin Vault, qui n'est pas dans `serveur/plugins/` : il cherche Vault 12 fois au démarrage pour rien | petit |
| i | `modules/bases/src/serveur/java/fr/heraliz/crac/bases/Tutoriel.java` l.155 (`SAS`), `TutorielApi` l.55 | Ensemble jamais rempli depuis le retrait du sas (1.16.0) | petit |
| j | `ClanEvents.onRightClick` (l.436) | Tourne à chaque clic droit de chaque joueur pour chercher des blocs qui n'existent plus (point 1) | avec le point 1 |
- **Question (e)** : ces 8 caisses vides sont-elles posées au spawn ? Sinon, on les retire du jeu des joueurs.
- **Question (h)** : un plugin Bukkit qui a besoin de Vault (boutique, votes…) est-il prévu ? Sinon, on coupe le pont.

### 11. Incohérences de prix et de règles
- **a. Mila rachète une arme ruinée au prix d'une neuve** : `Marchands.prixRachat` (`.../quetes/serveur/Marchands.java`
  l.230-270) ne regarde pas l'usure, alors que le recycleur la compte (`Recyclage.facteurUsure`). Une arme Légendaire à
  0 % se vend 250 hérals comme une neuve (A100). **Question** : on baisse le rachat selon l'état de l'arme ? Effort petit.
- **b. Cœur de clan** : 1 000 hérals chez le Bâtisseur (`serveur/config/heraliz_quetes/pnj/batisseur.json`) contre
  20 000 capsules à la caisse du casino ; brindilles 2 hérals contre 30 capsules, planches 50 contre 60. **Question** : le
  cœur doit-il rester à 1 000 hérals (le 26/10, A322 le jugeait trop bon marché) ?
- **c. Seuil « 4 clans » du ClashWar** écrit en dur deux fois : `modules/clashwar/.../tournoi/Recompenses.java` l.72 et
  `modules/clans/.../saison/SaisonVue.java` l.26 : changer l'un sans l'autre ferait mentir l'onglet Saison. À mettre dans
  `clashwar.json`. Effort petit.
- **d. `ARCHITECTURE.md`** dit armoire 500 et caisse en métal 150 au Bâtisseur ; le jeu les vend 250 et 300. Doc à
  corriger. Effort petit.

### 12. Ordres d'écriture qui ne suivent pas la règle « un plantage perd, il ne copie jamais »
- **a. Dépôt de capsules** (`.../casino/economy/CompteCapsules.java` l.444 puis l.460) : le compte est écrit avant le
  joueur ; un arrêt brutal entre les deux garde les capsules dans l'inventaire ET sur le compte. Le recycleur de la carte
  fait l'inverse (joueur d'abord). Fenêtre très courte. **Proposition** : retirer les capsules et enregistrer le joueur,
  puis créditer. Effort petit.
- **b. Vente à Mila, quête de Varga** (`Marchands.java` l.204-211, `Moteur.java` l.210-255) : le joueur (sans les
  objets) et la quête sont écrits tout de suite, mais les hérals (moins de 1 000) seulement à la sauvegarde suivante,
  jusqu'à 60 s plus tard. Un plantage dans ce délai : objets partis, hérals perdus (le commentaire du code dit le
  contraire). **Proposition** : demander une sauvegarde groupée (`EconomieApi.sauvegarderBientot`) après chaque vente et
  chaque quête payée. Effort petit.

### 13. Petites fuites de mémoire et code peu optimisé
- Comptes de capsules et comptes de clés gardés en mémoire pour tout joueur vu depuis le démarrage, jamais libérés
  (`CompteCapsules.java` l.106, `crates/cles/ComptesCles.java` l.79) ; `HdvServeur.VENTES_RECENTES` et
  `CommandesPb.DERNIER_PB` jamais vidés à la déconnexion. Petit à chaque redémarrage, mais sans fin entre deux.
- Hôtel des ventes : tout `hdv.json` réécrit sur le fil du serveur à chaque action (`HdvServeur.sauver`, synchrone) ;
  journal ajouté sur le fil du serveur. À passer sur un fil d'écriture comme les clans.
- `pnj` appelle l'économie par réflexion en recherchant la méthode à chaque fois (`.../quetes/serveur/Ponts.java`
  l.110-150) et refait la table des prix de tous les marchands à chaque appel (`SaisonQuetes.ventes`, l.59-68, appelé à
  chaque tick par écran du recycleur de la carte). Une dépendance directe au module `clans` (comme `bases`) et une table
  gardée jusqu'au prochain changement suffiraient.
- **Effort** : petit pour chacun.

### 14. Quêtes du jour : livrer et tirer la récompense sur la carte, en combat et à terre
- **Où** : `Journalieres.action` (`.../quetes/serveur/Journalieres.java` l.248-264) ne contrôle rien ; `/journalieres`
  est permise sur la carte et en combat (`heraliz_noyau-server.toml` l.12 et 19). Signalé le 26/10 (A280).
- **En jeu** : un joueur sur le point d'être tué livre ses composants à Varga par l'écran : son tueur ne les récupère pas.
- **Question** : livraison et tirage seulement au spawn ? Effort petit.

---

## Toujours ouverts depuis le 26/10 (vérifiés dans le code, décisions jamais prises)
- **Points PvP de saison sans plafond par jour** (A314) : `modules/clans/.../saison/Saison.java` l.312-347 : seul le
  couple tueur / victime est limité (6 h) ; une victime sans clan ne perd rien.
- **Quêtes de Varga : 10 points de saison chacune, sans plafond** (A316) : `.../quetes/serveur/Moteur.java` l.237 ;
  39 quêtes par joueur, soit ~390 points par nouveau membre (alors qu'une capture est plafonnée à 40 par jour).

## Vu en passant (pour le volet « commandes »)
- `/boutique abonnement` et `/nick qui` répondent par `sendSuccess`, que la règle `sendCommandFeedback = false` du
  serveur cache aux joueurs : le joueur ne voit rien (`.../grades/serveur/AbonnementServeur.java` l.111, `SurnomsServeur.java` l.355). Même chose pour `/pb stats`, `/warp`, `/zonevip`, `/grade` tapés en jeu par le
  staff. Les clans et le casino passent déjà par un `Reponses` qui contourne la règle.

## Ce qui a été vérifié et va bien
Hôtel des ventes (réservation, taxe prélevée avant la sortie de l'objet, reprise après plantage), `/trade`, comptes de
clés et ouverture des caisses (clé prise sur le disque avant le tirage), coffre du clan (joueurs puis coffre),
dissolution (banque partagée sans perte), arbre du clan (double clic), table de ravitaillement (anciens identifiants
convertis : A247 corrigé), drapeaux des territoires gardés par la mise en ligne (A313 corrigé), remboursement unique de
l'amélioration « Membres », déplacement de l'armoire (rend seulement ce qui a été payé), kit du tutoriel (aucun autre
écran ne s'ouvre pendant le tutoriel), étalement de l'entretien des bases du 01/11.

Rien de ce rapport n'a été testé en jeu.

---

## Résumé
1. Trois défauts des quêtes de clan coûtent cher : « Fouiller N caches » ne peut jamais avancer (une fois sur deux par
   jour), « Déposer N hérals » rapporte jusqu'à 2 000 hérals par semaine sans rien dépenser, « Tuer N joueurs » se fait
   avec des comptes secondaires.
2. Le casino jette au sol tout gain au-delà de ~23 000 capsules (lag, vol), et le recycleur du spawn jette au sol son
   contenu à chaque déconnexion ou `/redemarrage` : deux corrections courtes.
3. Le recycleur de la carte et la banque du clan peuvent faire laguer le serveur exprès ; `comptes.json` grossit sans fin
   et chaque opération d'argent importante attend sa réécriture complète.
4. Une erreur 500 du site efface un achat en PB, `/clan don` marche sur la carte, les récompenses de caisses tombent au
   sol inventaire plein, les caisses sont gratuites en créatif.
5. Beaucoup de restes à retirer : tests de CRac dans le jar serveur, ancien monde `crac:bases`, vieilles recettes, jackpot
   et machine disparue du casino, 8 caisses vides (~8 Mo chez les joueurs), pont Vault, restes des boss et des matchs.
