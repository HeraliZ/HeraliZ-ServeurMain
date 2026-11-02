# Audit du 02/11 — volet « mods-2 » : loot, bunker, monde, événements

Audit en **lecture seule** sur `main` (commit 65e64fad) : rien n'a été changé, compilé, fabriqué ni relancé (les outils
`outils/*.py` ont seulement été lus). **Rien n'a été testé en jeu.** Lus avant : `CLAUDE.md`, `ARCHITECTURE.md`,
`docs/METHODE_CLIENT_SERVEUR.md`, `docs/AUDIT_COMPLET_26OCT.md`, les `docs/TESTS_EN_JEU_*` du 31/10 et du 01/11 et le
journal Git depuis le 25/10. Quand un point avait déjà été signalé le 26/10 et n'est **toujours pas corrigé**, son
ancien numéro est rappelé (« A143… ») ; chaque point a été revérifié dans le code actuel.

Hors de ce volet (traités ailleurs) : découpage client / serveur, commandes, site, launcher, anticheat.

Gravité : **bloquant** (bug visible, faille, perte), **important**, **mineur**. Effort : petit / moyen / gros.

---

## Bloquant

### 1. Après le premier ClashWar, plus aucun largage ni hélicoptère jusqu'au redémarrage (A143, toujours là)
- **Où** : `modules/evenements/src/serveur/java/fr/heraliz/evenements/serveur/Outils.java` l.172-186
  (`clashwarEnCours` lit `Tournois.courant() != null`) ; `modules/clashwar/src/serveur/java/fr/heraliz/clashwar/tournoi/Tournois.java`
  l.49-51 : `courant` n'est remis à `null` qu'au démarrage et à l'arrêt du serveur (l.56 et 68), jamais à la fin d'un tournoi.
- **Ce qui se passe** : dès l'ouverture des inscriptions du ClashWar (mercredi et dimanche 20:30), le Directeur croit qu'un
  tournoi est en cours **jusqu'au prochain redémarrage**. Chaque largage (toutes les 3 h 30) et chaque hélicoptère est
  refusé, et comme un refus repousse l'essai d'un intervalle entier (règle du 27/10), ils sont perdus. Le journal
  `heraliz_evenements/journal.log` le montre : « pas lance (ClashWar en cours) ». Une simulation du banc bloque pareil.
- **En jeu** : deux soirs par semaine, plus aucun événement de la carte (ni points de saison des largages) toute la nuit.
- **Proposition** : lire « tournoi actif et pas une simulation » (`courant() != null && courant().actif() && !simulation`,
  ou une petite méthode `Tournois.enCours()`). **Petit.**

---

## Important

### 2. Bunker : fuite de mémoire chez les joueurs (portes HBM), jamais vidée
- **Où** : `modules/bunker/src/client/java/com/hbm_m/client/overlay/DoorAnimationDelayHelper.java` l.14-15 et 19-24 ;
  appelé par `modules/bunker/src/client/java/fr/heralisz/bunker/client/PartieClient.java` l.33-36 depuis
  `DoorBlockEntity` l.601-603 (fin de chaque ouverture ou fermeture d'une porte vue par le joueur). Les méthodes qui
  vident les files, `DoorAnimationDelayHelper.processQueue()` (l.26) et
  `com/hbm_m/client/render/DoorChunkInvalidationHelper.processPendingInvalidations()` (l.28), **ne sont appelées nulle
  part** (vérifié dans tout le dépôt et dans l'historique : déjà le cas avant le 26/10).
- **Ce qui se passe** : chaque porte HBM qui finit de bouger est ajoutée à une table gardée à vie, qui retient l'objet de
  la porte… et, à travers lui, **tout l'ancien monde chargé** chez le joueur (une porte garde son monde). Après chaque
  passage carte ↔ spawn (mort, extraction, départ), un monde entier peut rester en mémoire. Les rafraîchissements de
  modèles de portes demandés (choix de skin) ne sont jamais faits non plus.
- **En jeu** : sur une longue session avec des salles à carte, la mémoire du jeu monte, saccades de nettoyage, voire
  plantage « mémoire insuffisante » sur les PC à 4 Go.
- **Proposition** : appeler les deux méthodes à chaque tick du jeu côté joueur (comme le faisait HBM), et vider la table
  au changement de monde. Retouche minime du code HBM (licence GPL : on garde ses crédits). **Petit.**

### 3. Largage : tirage du point qui charge (ou crée) du terrain sur le fil du serveur (A149, toujours là)
- **Où** : `modules/evenements/src/serveur/java/fr/heraliz/evenements/serveur/Largage.java` l.219-255
  (`pointAuHasard`, `plat` : `getHeightmapPos` / `getHeight` sur des tronçons pas chargés).
- **Ce qui se passe** : jusqu'à 40 essais, chacun lit 1 à 5 tronçons sur le disque **en bloquant le serveur** (le 01/11,
  le même défaut a été mesuré à 10-50 ms par tronçon sur la borne de départ et corrigé là, pas ici). Pire : hors de la
  partie construite de la carte (pas de fichier de région), Minecraft **génère du terrain neuf** dans `dayz_map`. La
  borne vérifie ça (`DepartServeur.regionPresente` l.175-183) et l'hélicoptère ne lit que des tronçons déjà chargés
  (`PointCrash.charge` l.168-170) ; le largage, non.
- **Aussi** : le point n'évite pas les **zones VIP** (frappes et hélicoptère les évitent : `PointCrash.dansZoneVip`) : un
  largage public peut tomber dans une zone où seuls les grades boutique entrent.
- **En jeu** : un gel du serveur à chaque largage (toutes les 3 h 30, jusqu'à 1-2 s au pire), et du terrain généré
  autour de la carte.
- **Proposition** : même méthode que la borne (fichier de région présent, tronçon demandé en tâche de fond, contrôle au
  tick suivant), et refuser les zones VIP. **Moyen.**
- **Question** : y a-t-il des zones VIP sur la carte elle-même ?

### 4. Conteneurs de loot : un objet déposé est détruit 5 minutes plus tard (A52, toujours là)
- **Où** : `modules/loot/src/serveur/java/fr/heraliz/loot/RotationLoot.java` l.1041 (coffre de Minecraft ordinaire :
  dépôts permis, Maj + clic compris) et l.843-867 (règle des restes : vidé après `restes_secondes` = 300).
- **Ce qui se passe** : un joueur qui range ses affaires dans un conteneur T1-T6 (cache improvisée) les perd 5 min après
  son départ, objets à vie compris ; n'importe qui peut aussi les prendre avant.
- **Proposition** : conteneurs « prise seulement » (cases qui refusent tout dépôt, comme les corps et les coffres T7 le
  font déjà : `CorpsMenu.CaseCorps`, `CoffreMenu.Case`). **Petit.**

### 5. Nom de zone d'extraction de plus de 64 caractères : le serveur plante (A93, toujours là)
- **Où** : `modules/monde/src/serveur/java/com/heraliz/extraction/serveur/ExtractionCommand.java` l.76 et 128-140 (nom
  sans longueur maximale) ; `modules/monde/src/commun/java/com/heraliz/extraction/reseau/Reseau.java` l.60
  (`writeUtf(nom, 64)`).
- **Ce qui se passe** : le paquet est fabriqué tout de suite, dans le tick du serveur ; un nom trop long lève une erreur
  à chaque ouverture de la zone par la rotation (et à chaque connexion) : plantage du serveur ou joueurs expulsés.
- **En jeu** : seulement après une erreur de frappe du staff, mais l'effet est grave et dure tant que la zone existe.
- **Proposition** : refuser un nom de plus de 32 caractères à la création. **Petit.**

### 6. Coffres personnels : chaque fermeture d'une planque relit les coffres de TOUS les joueurs (A84, en partie corrigé)
- **Où** : `modules/monde/src/serveur/java/fr/heralisz/utils/coffres/CoffresPersonnels.java` l.461-481 (`removed`) →
  `Donnees.save(File)` l.356-370, qui fait `this.save(new CompoundTag())` (l.359) sur le fil du serveur.
- **Ce qui se passe** : depuis le 25/10, la compression et l'écriture partent sur un fil à part, mais la **copie** de
  tous les objets de tous les coffres (planques et coffres VIP de chaque joueur inscrit, avec copie de leur NBT) se fait
  encore dans le tick, à chaque fermeture. Le coût grandit avec la saison (chaque nouveau joueur qui range quelque chose).
- **En jeu** : petits à-coups au spawn quand beaucoup de joueurs ouvrent leur planque, de plus en plus marqués.
- **Proposition** : ne recopier que le coffre du joueur qui ferme (un fichier par joueur, ou copie gardée par joueur et
  marquée « à écrire »). **Moyen.**

### 7. Loot sans fichier de zones : un conteneur isolé est toujours plein (à vérifier sur la carte en construction)
- **Où** : `modules/loot/src/serveur/java/fr/heraliz/loot/ZonesLoot.java` l.170-176 (`cible` : au moins 1 conteneur
  actif par zone) ; `RotationLoot.java` l.611-625 (`creerZone` : un conteneur posé à plus de 64 blocs de tout conteneur
  du même palier ouvre sa propre zone) et l.372-394 (`activerUn` : seul conteneur de sa zone → c'est lui qui se remplit).
- **Ce qui se passe** : le dépôt n'a aucun fichier de zones (`data/heraliz_loot/zones/` est vide, les zones viennent du
  pack de données du monde ou sont créées en jeu). Pour tout conteneur posé loin de ses pareils (un T5 seul dans un
  poste de police, un T6 isolé…), la règle « au moins 1 » le rend plein en permanence : vidé, il se remplit de nouveau
  4 à 6 min plus tard. Pour 3 conteneurs T6 regroupés : 1 sur 3 plein (33 %) au lieu des ~11 % visés.
- **En jeu** : un joueur qui campe près d'un T5/T6 isolé le refouille toutes les 5 min (loot et points de saison :
  0,8 / 1,5 par ouverture).
- **Proposition** : vérifier avec `/heralizloot zones` la taille des zones « ajouts/… » ; puis, pour les petites zones,
  arrondir au hasard (0,3 → plein 3 fois sur 10) au lieu de « au moins 1 », ou rattacher les isolés à la zone de la
  ville la plus proche, tous paliers confondus. **Petit à moyen.**
- **Question** : un conteneur haut de gamme isolé doit-il être plein à chaque passage ?

### 8. Sacs à dos trouvés bien meilleurs que prévu (A258, toujours là, vérifié sur les tables)
- **Où** : `outils/loot_armes.py` l.72-76 (`SACS_HORS_LOOT` retire le seul sac Commun, `backpack_school` ;
  `PARTS_SACS` = 62 % Commun) et l.675-683 (les parts sont ramenées aux raretés présentes).
- **Mesuré dans `serveur/config/heraliz_loot/coffres/t1.json`… `t7.json`** : Peu commun 63 %, Rare 26 %, Épique 9,2 %,
  Légendaire 1,3 % — au lieu de 24 / 10 / 3,5 / 0,5 % voulus (la part « Commun » de 62 % est répartie sur les autres).
- **En jeu** : un sac Épique ou Légendaire sort presque 3 fois plus souvent que décidé le 22-23/10.
- **Proposition** : remettre le sac d'école dans le loot, ou refaire `PARTS_SACS` sans Commun, puis relancer la chaîne des
  outils. **Petit.**
- **Question** : quelles parts voulez-vous pour les sacs (avec ou sans sac d'école) ?

### 9. Zombies : composants Épiques (niveau III) par 1 à 7 (A255, toujours là)
- **Où** : `outils/loot_zombies.py` l.79-81 (`fab` : [1, 1]) puis l.150-167 (`ECHELLE` × 3,8 : [1, 7], tiré
  uniformément) ; `serveur/config/heraliz_loot/coffres/zombies.json` (`canon_iii`… `[1, 7]`, sans « suite »).
- **Ce qui se passe** : dans les conteneurs, un composant III sort presque toujours par 1 (clé « suite ») ; sur un zombie,
  il sort par 4 en moyenne. Même chose pour les composants II.
- **Proposition** : quantité « suite » (comme les conteneurs) pour les niveaux II et III, ou [1, 1] pour le III. **Petit.**

### 10. Largage jamais visité : la caisse fume et reste pour toujours, et ses points se perdent (A148 + nouveau constat)
- **Où** : `modules/monde/src/serveur/java/fr/heraliz/airstrike/entity/SupplyCrateEntityServeur.java` l.56-69
  (disparition comptée en ticks de l'entité) ; `serveur/spigot.yml` l.90-94 (`entity-activation-range.misc: 16`) ;
  `Largage.java` l.138-143 et 159 (suivi arrêté en heure réelle : disparition + 3 min après le départ de l'avion).
- **Ce qui se passe** : sous Mohist, une entité « divers » posée au sol sans joueur à 16 blocs n'est plus mise à jour (le
  code des corps le constate déjà : `CorpsEntityServeur` l.139-145). La fumée et le compte à rebours de la caisse sont
  donc figés tant que personne ne vient : elle fume et reste indéfiniment. Mais le Directeur, lui, ferme l'événement en
  heure réelle : une caisse ouverte après ce délai ne donne plus de points, et son objet d'événement n'est plus marqué
  « essai » pour un largage du staff.
- **À vérifier en jeu** : un largage où personne ne va, puis s'en approcher 30 min plus tard.
- **Proposition** : faire disparaître la caisse en heure réelle (date de fin gardée dans la caisse, contrôlée par le
  serveur), et garder l'événement ouvert tant que la caisse existe. **Petit à moyen.**

---

## Mineur

### 11. Zombies du peuplement gardés sur le disque quand leur tronçon se décharge (A80, toujours là)
- **Où** : `modules/monde/src/serveur/java/fr/heralisz/utils/zombies/Peuplement.java` l.220-238 (un zombie déchargé est
  seulement oublié, sans `discard` ni retour de sa place) et l.291 (`setPersistenceRequired`) ;
  `ReservesZombies.java` l.67-86 (la place revient toute seule au bout de 5 à 8 min).
- **Ce qui se passe** : quand un joueur se téléporte (mort, extraction, déconnexion), les tronçons autour de lui se
  déchargent avant le passage de retrait (toutes les 3 s) : ses zombies sont écrits dans le monde. Leur place revient
  quand même ; au retour d'un joueur, les anciens reviennent **et** de nouveaux apparaissent.
- **Proposition** : ne pas marquer ces zombies « persistants » (Minecraft les jette alors au déchargement) ou les
  retirer à l'événement de déchargement. **Petit.**

### 12. Véhicules posables au spawn et dans le tutoriel ; liste des mondes interdits périmée (A92, toujours là)
- **Où** : `modules/monde/src/commun/java/fr/heraliz/vehicules/VehiculesConfig.java` l.31-34 et
  `serveur/defaultconfigs/heraliz_vehicules-server.toml` : `["heraliz_clashwar:arenes", "heralisz_bases:bunkers"]`.
  `heralisz_bases:bunkers` n'existe plus ; `heralisz:spawn`, `crac:tuto` et `crac:bases` n'y sont pas.
- **Proposition** : ajouter `heralisz:spawn` et `crac:tuto` (et `crac:bases` si ce monde sert encore), retirer l'ancien
  monde. **Petit.**

### 13. Atelier de réparation : l'arme posée tombe au sol à la déconnexion ou au redémarrage (A99, toujours là)
- **Où** : `modules/monde/src/serveur/java/fr/heraliz/usure/MenuReparationServeur.java` l.83-90.
- **Ce qui se passe** : Minecraft enregistre le joueur **avant** de fermer son écran ; l'arme posée dans l'atelier est
  alors jetée au sol (ramassable par tous, disparaît en 5 min). Même défaut qu'A240 (objets à vie, autres volets).
- **Proposition** : à la déconnexion, rendre l'objet dans l'inventaire avant l'enregistrement (écouteur de déconnexion en
  priorité haute). **Petit.**

### 14. Un membre du staff en créatif qui ouvre un largage public prend ses points (A321, toujours là)
- **Où** : `SupplyCrateEntityServeur.java` l.200-216 (première ouverture, sans contrôle du mode de jeu) ;
  `Largage.java` l.173-192 ; `modules/clans/.../saison/Saison.java` l.232-236 (ne refuse pas le créatif).
- **Ce qui se passe** : les points vont à son clan et le largage est noté « récompensé » : les joueurs n'en ont plus.
- **Proposition** : en créatif ou spectateur, ouvrir sans prévenir l'événement (comme `SaisonLoot.pve`). **Petit.**

### 15. Départ de la borne : 3 secondes où le joueur peut bouger avant d'être envoyé
- **Où** : `modules/monde/src/serveur/java/fr/heralisz/utils/spawn/DepartServeur.java` l.306-312 (le départ sort de la
  liste surveillée) ; `Prechargement.java` l.96-104 et 117-143 (action jusqu'à 3 s plus tard, seul contrôle : joueur
  vivant et connecté).
- **Ce qui se passe** : si les tronçons d'arrivée ne sont pas prêts à la fin du compte à rebours, le joueur peut marcher,
  ouvrir un écran ou prendre un autre téléporteur pendant 3 s ; il est quand même envoyé sur la carte.
- **Proposition** : au moment de partir, revérifier qu'il est toujours au spawn, près de la borne, sans écran ouvert.
  **Petit.**

### 16. Fusée d'extraction utilisable hors de la carte
- **Où** : `modules/monde/src/commun/java/com/heraliz/extraction/item/ExtractionTerminalItem.java` l.63-71 (seuls
  contrôles : ciel ouvert, au sol). Les jumelles de frappe, elles, se limitent à la carte (`DesignatorLogic` l.62-69).
- **En jeu** : au spawn ou dans le monde du tutoriel, la fusée se consomme pour rien (ou fait sortir du tutoriel).
- **Proposition** : refuser hors de la carte, comme les frappes. **Petit.**

### 17. Incohérences de valeurs
- **Prix de secours de la boîte noire** : 900 hérals dans le code (`ObjetsServeur.java` l.125), 400 dans
  `serveur/config/heraliz_evenements/objets.json` et `docs/EVENEMENTS.md` l.133 (choix du propriétaire). Ne sert que si
  le fichier manque, mais à aligner. **Petit.**
- **Nourriture** : celle des zombies est toujours bonne (`{FoodState:0}` écrit par `outils/loot_zombies.py`), celle des
  conteneurs est bonne une fois sur trois (`EtatNourriture.java`, tables sans état). **Question** : voulu ?
- **Bonus de loot de la boutique** : son temps s'use dès que le joueur est connecté, même au spawn ou AFK
  (`modules/monde/src/serveur/java/fr/heralisz/utils/BonusLootTemps.java` l.53-66). **Question** : ne le décompter que
  sur la carte ?

### 18. Couleur des salles à carte devinée dans un texte français
- **Où** : `modules/bunker/src/serveur/java/fr/heralisz/bunker/serveur/salle/LootSalles.java` l.228-235 (libellé
  « Salle à carte « x » (rouge) ») ; `modules/loot/src/serveur/java/fr/heraliz/loot/SaisonLoot.java` (`salle()`, lit la fin
  « (rouge) ») ; `RotationLoot.java` l.1018-1036.
- **Risque** : changer ce libellé (traduction, correction) coupe en silence les points de saison des salles et les quêtes
  du jour « salle ». **Proposition** : passer la couleur à part dans la déclaration de la salle. **Petit.**

### 19. Doublons de code (à regrouper dans le noyau quand on y touche)
- **Écritures en tâche de fond** recopiées 5 fois : `loot/.../EcrituresFond.java`, `evenements/.../EcrituresFond.java`
  (quasi identiques, vérifié), `boutique/.../casino/data/EcrituresFond.java`, `pnj/.../EcrituresFond.java`,
  `clans/.../EcrituresFond.java` ; plus un fil à part dans `CoffresPersonnels` (l.80-84) et deux relevés de dates de
  fichiers (`monde/.../serveur/DatesFichiers.java`, « même méthode que la boutique »).
- **Lecture des bases de CRac par réflexion** recopiée 4 fois, avec 4 réactions différentes en cas d'erreur :
  `Largage.java` l.316-336 (point accepté), `PointCrash` (point refusé), `ConteneursVanilla.java` l.159-179 et
  `Peuplement.java` (coupé jusqu'au redémarrage, A59).
- **Zones VIP** lues par deux copies du même code : `Peuplement.lireVip` (l.379-400) et `PointCrash.lireVip` (l.206-236).
- **Règles du noyau relues à la main** alors que `monde` dépend du noyau : clé `heralisz:combat_jusqua` lue en dur
  (`Suicide.java` l.79, `vehicules/Regles.java` l.51-72), `heralisz:en_match` (`DesignatorLogic.java` l.64,
  `ButinZombies.java` l.276), « à terre » par réflexion vers Réanimation (`Suicide.java` l.110-122 (`aTerre`),
  `extraction/serveur/Compat.java`) au lieu de `CombatTag`, `Matchs`, `ATerre` du noyau (déjà utilisés par
  `CoffresPersonnels`).
- **Trois copies du « pont »** dans le même jar `monde` (`fr/heralisz/utils/pont`, `fr/heraliz/airstrike/pont`,
  `com/heraliz/extraction/pont`).
- **Effort** : moyen (à faire au fil des chantiers, sans urgence).

### 20. Code mort et restes d'anciens systèmes (téléchargés par les joueurs pour certains)
- **Convertisseur des corps de l'ancien mod « corpse »** (A105) : `monde/src/commun/java/fr/heraliz/corps/entite/AncienCorps.java`,
  `AncienCorpsServeur.java`, `corps/registre/Registres.java` l.50-68 : enregistre encore une entité `corpse:corpse` chez
  tous. La carte `dayz_map` est neuve : plus aucun ancien corps à convertir. **Question** : on le retire ?
- **Restes des matchs retirés** dans les textes et réglages : `VehiculesConfig.java` l.31 et 35 et le `.toml` (« matchs
  ClashWar/Duels, mode Zombies », « clé … de HeraliZ Matchs »), javadoc de `vehicules/Regles.java` l.35-41 (« Coffre des
  matchs »), `usure/UsureConfig.java` l.133 (« duels, ClashWar, zombies ») ; monde `heralisz_bases:bunkers` (supprimé)
  encore listé dans `VehiculesConfig.java` l.34, `modules/bases/.../crac/Config.java` l.358 et
  `modules/armes/.../vitres/Vitres.java` l.53.
- **Réglage sans effet** : `ia_rythme` (`ZombiesConfig.java` l.84-87), sans effet sous Minecraft 1.20.1 (noté le 01/11,
  gardé) : à retirer du fichier pour ne pas tromper le staff.
- **Petits restes** : liste `perdus` jamais remplie (`RotationLoot.java` l.682 et 695) ; `DoorDecl.getLockedMessage`
  (l.1411-1413, clé `door.locked` absente des langues, jamais appelée) ; `CardReaderBlock` l.168 (`toLowerCase()` sans
  `Locale.ROOT` sur un état de bloc).
- **Notes des tables T1-T6** (`serveur/config/heraliz_loot/coffres/t1.json` … `t6.json`, clé `_note`) : citent un outil
  disparu (`tools/gen_tables_rotation.py`) et un ancien profil de test, alors que c'est `outils/loot_armes.py` qui les
  écrit (T-arch-4). **Petit.**
- **Effort** : petit.

### 21. Valeurs en dur qui seraient mieux dans un fichier
- Objets qui protègent du gaz, de la radiation et du froid : `decors/serveur/ZonesServeur.java` (listes `P_GAZ`,
  `P_RAD`, `P_FROID`) : une nouvelle tenue de l'Armurerie demande un nouveau jar.
- Délai de `/suicide` (5 min, `Suicide.java` l.21), distance de départ de l'avion du largage (400 blocs,
  `Largage.java` l.150), balles au plus par case (40, `TablesCoffres.java` l.249).
- Messages du staff de `/event helico …` écrits en français dans le code (`Helico.java` l.534-588 ; détail laissé au
  volet des commandes). **Effort** : petit.

---

## Tables générées et outils (lu, rien relancé)

- **Copies identiques** : les 18 tables du jar (`modules/loot/src/serveur/resources/data/heraliz_loot/coffres/`) et de
  `serveur/config/heraliz_loot/coffres/` sont identiques octet pour octet (T-arch-7 réglé).
- **Références valides** : toutes les armes (`GunId`), accessoires, munitions (`AmmoId`) et composants cités par les
  tables existent dans les gunpacks de `serveur/tacz/` et dans `ComponentDefs.java` ; les 183 armes ont leur rareté dans
  `raretes.json` ; la table du largage `modules/monde/src/serveur/resources/data/airstrike/loot_tables/chests/supply_drop.json`
  est valide. Munitions .50 BMG, 40 mm et roquettes RPG présentes (A257 réglé).
- **Accord outil / tables** : `facteurs_actifs` identiques dans `ConfigLoot.java` et `heraliz_loot-server.toml` ; tables
  des zombies conformes à `outils/loot_zombies.py` (`ECHELLE`) ; crâne, sceptre et calice en T7 seulement.
- **Écarts voulus par l'outil mais contraires aux décisions** : points 8 (sacs) et 9 (composants des zombies).

## Déjà corrigé depuis l'audit du 26/10 (vérifié, pas redit)
Salle à carte ouverte sans carte (A68), porte à carte restée ouverte (A69), copie de `/inspecter` (A78), munitions des
armes Légendaires (A257), fuite des passages de carte (A71, table devenue « faible »), budget en temps des chemins des
zombies (A264), borne de départ (A82, A83), exposition remise à zéro à la mort (A98, en partie), rotation du loot
compressée en tâche de fond (A269, en partie).

---

## Résumé (5 lignes)
1. Bloquant : depuis le premier ClashWar d'une session, plus aucun largage ni hélicoptère jusqu'au redémarrage (une ligne à changer).
2. Importants : fuite de mémoire chez les joueurs par les portes du bunker ; gel (et terrain généré) au tirage de chaque largage ; objets déposés dans le loot détruits ; nom d'extraction trop long qui fait planter.
3. Économie : conteneurs isolés toujours pleins sans fichier de zones, sacs trouvés ~3 fois trop bons, composants Épiques des zombies par 4 ; planques qui relisent les coffres de tout le monde à chaque fermeture.
4. Mineurs : caisses de largage figées loin des joueurs, zombies gardés sur le disque, véhicules au spawn, atelier qui jette l'arme, staff qui prend les points d'un largage, petits trous de contrôle (borne, fusée).
5. Propreté : tables cohérentes avec leurs outils ; doublons (écritures de fond ×5, lecture des bases ×4, règles du noyau relues à la main) et restes (ancien mod de corps, textes des matchs) à nettoyer sans urgence. Rien n'a été testé en jeu.
