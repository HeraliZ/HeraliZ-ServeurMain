# Audit game design et feuille de route — 25/10

Audit fait **sans rien développer et sans rien modifier** : lecture des documents, du code, des réglages et du contenu
réel des jars des autres auteurs (listes de fichiers, données, langues, noms de classes). Rien n'a été essayé en jeu.

Repères utilisés dans tout le document :
- **[vérifié]** : vu dans les fichiers (code, données, jar) ;
- **[à vérifier]** : probable d'après le code, mais à confirmer par un essai en jeu ;
- **[à valider]** : chiffre ou choix proposé, à décider par les propriétaires.

---

## 0. L'essentiel en une page

**Ce qui marche déjà bien** : la boucle DayZ est complète (loot T1 à T7, zombies, extraction, revente, recyclage, craft,
bases, clans, saison). Plusieurs briques techniques sont **déjà là et réutilisables** pour des événements : avions et
largages sans entité (trajectoire calculée des deux côtés), zone de feu sans vrais blocs de feu, fumée de signal visible de
loin, coffres T7 qui apparaissent et disparaissent, balles TaCZ arrêtées par un objet qui n'est pas fait de blocs (le
train), partage des points d'un boss selon les dégâts faits, vagues de zombies autour d'un point.

**Ce qui manque le plus** :
1. **Aucun « chef d'orchestre » des événements** : rien ne lance tout seul des événements sur la carte à heures choisies.
   Les boss attendent qu'on vienne les réveiller, les largages publics n'existent pas, le seul vrai « event » des points
   de saison est le ClashWar (2 soirs par semaine).
2. **Le barème des points de saison a deux failles qui écrasent tout le reste** : un boss rapporte 150 points et chaque
   boss revient toutes les **60 minutes** (5 boss) ; et les quêtes de clan « Jouer 180 minutes » avancent **même AFK au
   spawn**, pour tout le clan à la fois.
3. **Peu de raisons de se croiser** : beaucoup d'objectifs se font chacun dans son coin (salles à carte, boss en donjon,
   loot). Il manque des événements **visibles de loin** qui attirent tout le monde au même endroit.

**Les 5 meilleures idées** (détail aux sections 5 à 7) :

| # | Idée | Pourquoi |
|---|---|---|
| 1 | **Hélicoptère militaire abattu** (patrouille, combat, crash, épave en feu, caisses et pièces) | l'événement phare : spectaculaire, visible et audible de loin, donne enfin une utilité au RPG, aux lance-grenades et aux .50 |
| 2 | **Directeur d'événements** (planning, annonces par secteur, anti-chevauchement, journal) | le socle qui fait revenir les joueurs à heures fixes et qui porte tous les autres événements |
| 3 | **Rééquilibrage de la saison** (boss, AFK, plafond par clan, points de capture contestée, prime sur les meneurs) | sans lui, les gros clans accumulent sans opposition et les nouvelles sources ne pèsent rien |
| 4 | **Cargaison sensible** (objet de valeur à ramener par une extraction, porteur repérable) | le meilleur créateur de PvP naturel avec ce qui existe déjà (extractions par vagues) |
| 5 | **Zone chaude** (zone de conflit temporaire, reprise du code des drapeaux) | conflit court et lisible, ouvert aux petits clans, presque tout le code existe |

**Feuille de route en une ligne par priorité** :
- **Priorité 1** : corriger le barème et les abus de la saison, poser le Directeur d'événements, largage public disputé,
  boss du monde annoncés, zone chaude ; lancer les **rendus** de l'hélicoptère.
- **Priorité 2** : hélicoptère militaire complet, cargaison sensible, nouvelles sources de points (capture contestée,
  prime sur les meneurs), radio militaire et renseignement.
- **Priorité 3** : plateforme pétrolière avec gardes armés, train militaire, maladies façon DayZ.

---

## 1. Méthode et limites

Lu : `CLAUDE.md`, `ARCHITECTURE.md`, `docs/GUIDE.md`, `docs/COMMANDES.md`, `docs/SAISON_CLANS.md`,
`docs/QUETES_SCENARIO.md`, `docs/AUDIT_25OCT.md`, `docs/TESTS_EN_JEU_25OCT.md`, l'ancien `CLAUDE.md` (sections 3, 10, 11).
Code lu : saison (`modules/clans/.../saison/`, `ClanEvents.java`, `Quetes.java`), frappes et largages
(`modules/monde/.../airstrike/`), balles et train (`BallesTrain.java`), coffres T7 (`PointsT7.java`), boss
(`HornAltarBlockEntityServeur.java` et les salles des 4 autres). Réglages : `saison.json`, `purge.json`, `niveaux.json`,
`heralisz_clans-common.toml`, `zones.json`, `ravitaillement.json`, `clashwar.json`, `raretes.json`, `outils/marchands.py`,
`heraliz_extraction-common.toml`, `airstrike-server.toml`, configs ywzj, Zombie Awareness, TaCZ, TaCZ Addon.
Jars ouverts : ywzj_vehicle (données, langues, sons, noms de classes), geckolib222 (langues, classes d'effets), Mutants &
Zombies (langues), Zombie Awareness (config), TaCZ (classe des balles).

Limites :
- je n'ai pas lancé le jeu : tout comportement « en jeu » reste à confirmer ;
- je n'ai pas décompilé le code des autres auteurs (seulement les noms de classes et les textes qu'ils contiennent) ;
- les valeurs de dégâts des armes TaCZ **contre une entité qui n'est pas un joueur** n'ont pas été mesurées (la table
  `armes.json` ne règle que les dégâts contre les joueurs) : les points de vie de l'hélicoptère proposés sont à régler en
  essai.

---

## 2. Cartographie des mods réellement présents

Classement utilisé : **N** = disponible nativement, **C** = configurable (réglage, données), **D** = demande un
développement spécifique, **X** = trop coûteux ou risqué.

### 2.1 Mods des autres auteurs (`serveur/mods-tiers/`)

#### ywzj_vehicle 0.5.10 « fuse » (véhicules, YWZJ) — vérifié dans le jar

| Sujet | Ce qu'il y a vraiment | Classement |
|---|---|---|
| Véhicules jouables | **4 seulement** : moto (100 PV d'origine), buggy MRZR4 (300), fourgon Hiace (300), camion benne (500) — fichiers `default_vehicle/data/ywzj_vehicle/vehicles/*.json`. Notre mod divise les PV par 5 (moto 20, buggy et fourgon 60, camion 100). **Aucune arme montée** sur ces 4 véhicules. | N |
| Hélicoptères | Le **moteur** sait gérer des hélicoptères (classe `RotaryWingVehicle`, réglages « rotary wing » : régime, collectif, altitude, descente en corde `FastRopingContext`) et il reste des classes `Ah64d` (Apache), `Z10`, `Quadcopter` (drone). **Mais le pack « fuse » a retiré tous les modèles, données et sons d'hélicoptère** : la description du pack dit « Pack réduit par fuse : atv_mrzr4, motorcycle, dump_truck, hiace » ; les dossiers de sons `vehicle/ah_6/`, `vehicle/z10/`, `vehicle/quadcopter/` sont **vides**. | D (modèle à faire) / X (piloté par une IA : voir plus bas) |
| Pilote automatique | **Aucun** : aucun code d'IA, de pilote ou d'objectif dans le jar. Les véhicules sont conduits par des joueurs. | X pour un hélico ennemi ywzj |
| Armes de véhicule | Données présentes mais non utilisées : mitrailleuses 7,62 / 12,7 / 14,5 mm, canon automatique, roquettes 57 mm, missiles (Hellfire `agm_114`, Stinger `fim_92`, TOW…), bombes (`aerial_bomb`, GBU-12, bombe nucléaire), leurres thermiques, grenades fumigènes. Classes `MissileEntity`, `RocketEntity`, `AerialBombEntity`, `DecoyFlareEntity`, `BulletEntity`. Modèles 3D des munitions présents (`missile_hellfire`, `rocket_57mm`, `aerial_bomb`, `bomb_gbu_12`). | D (utilisable seulement monté sur un véhicule ywzj) |
| Sons utiles | Enregistrés et jouables par nom : `gun_7.62mm_shot`, `gun_12.7mm_shot`, `rocket_launch`, `missile_launch`, `rocket_flying`, `bomb_whistle`, `bombs_incoming`, `decoy_flare_launch`, `piston_passby` ; et dans le jar principal : `vehicle_hit_small/med/big` (impacts sur du métal), `radar_lock_warn`, `missile_launch_warn`, `radar_search_warn`. **Aucun son de rotor.** | C (on peut les jouer depuis notre code par leur nom) |
| Épaves | Un véhicule détruit garde un état « détruit », lâche des morceaux et de la fumée d'épave (`spawnDestroyedParts`, `spawnWreckageSmoke`). | N |
| Carburant, outils | Jerrican / réservoir (`fuel_tank`, recettes de remplissage), liquide « fuel », chalumeau de réparation, parachute (recettes présentes). Réglage : `fuelNameWhiteList = ["fuel", "gas", "lava"]` (la lave sert de carburant). | N / C |
| Boîte à figurine | Objet qui « range » une entité. Réglage `figureBoxOnlyCaptureVehicle = false` : elle peut ranger **n'importe quelle entité**. Pas de recette (créatif seulement) [vérifié], mais à passer à `true` par sécurité (boss, PNJ). | C |
| Radar, imagerie thermique, verrouillage | Code et textes présents (interface de visée, radar, thermique) pour les véhicules armés. | X (sans véhicule armé) |

**Conclusion pour l'hélicoptère** : impossible de faire « un hélicoptère ywzj piloté par une IA » sans réécrire une grosse
partie du comportement d'un moteur physique qui n'est pas à nous (fragile, à chaque mise à jour). En revanche, **nos propres
outils font mieux et moins cher** (section 5) ; de ywzj, on ne garde que des **sons** (tirs, impacts métal, alerte de
verrouillage) et l'idée des leurres.

Licence : le pack indique « All Rights Reserved ». Reprendre le modèle d'un hélicoptère du pack officiel complet dans notre
mod reviendrait à copier leur travail : **à ne pas faire**. Notre propre modèle (rendus validés d'abord) est la voie propre.

#### TaCZ 1.1.8 + TaCZ Addon (armes) — vérifié

| Sujet | Ce qu'il y a | Intérêt pour le game design |
|---|---|---|
| Environ 185 armes | 13 gunpacks dans `serveur/tacz/` (ArkaniaZ, Max Stuff, Cislumen, Fuse, skins…) | N |
| Armes lourdes | `tacz:rpg7`, `tacz:m320`, `arkaniaz:m79`, `maxstuff:m320t` (lance-roquettes et lance-grenades, **Légendaire**), `tacz:minigun` (Légendaire), `tacz:m249` (Épique), `.50` : `arkaniaz:aw50`, `arkaniaz:m200`, `arkaniaz:m82a3`, `maxstuff:deagle_50bmg` (Légendaire). Aujourd'hui elles n'ont **pas de cible à leur mesure** (pas de véhicule ennemi, bases protégées hors Purge). | **inexploité** : l'hélicoptère leur donne un vrai rôle |
| Balles et entités | La classe des balles gère les entités à plusieurs morceaux (`MaybeMultipartEntity`, `PartEntity` de Forge) et lance l'événement `EntityHurtByGunEvent` (avant / après). Les véhicules ywzj, qui ne sont pas des créatures, prennent déjà les balles. | N : une cible « hélicoptère » en plusieurs morceaux (rotor, queue, cabine) est possible [à vérifier en essai] |
| Son des tirs | `DefaultGunFireSoundDistance = 256` (TaCZ Addon) : un tir s'entend à 256 blocs. | N : un combat contre un hélico s'entend de loin |
| Munitions explosives | `ExplosiveAmmoDestroysBlock = false`, `ExplosiveAmmoKnockBack = true`, visibles à 192 blocs. | N |
| Table d'armurier | Recettes en données (gunpack `heraliz_crafts`), lecture des coffres proches (`containerScanRadius = 3`), fabrication en série. | C : recettes de variantes (pièces d'épave) |

#### geckolib222 (contenu ArkaniaZ caché dans GeckoLib) — vérifié

| Sujet | Ce qu'il y a | Classement |
|---|---|---|
| Nourriture et boissons | une vingtaine d'aliments (conserves, chips, jus, eau, sodas) avec état de fraîcheur (`FoodState`) | N |
| Soins | poches de sang (pleine, vide, **contaminée**), comprimés de chlore, tétracycline, antidouleurs, morphine, adrénaline, attelle, pansement, **Antiradin** | N |
| Effets | saignement, jambe cassée, **infection bactérienne, infection parasitaire, intoxication alimentaire**, **irradié**, protection contre les radiations, vision nocturne, « Bénédiction » (anti-zombies), tag de combat, pas de tir à la tête | N (déjà là) / D pour les brancher sur nos systèmes |
| Objets utilitaires | **radio, GPS**, lampe torche, allumettes, canne à pêche, bidon d'essence, jerrican, batterie de voiture, pile 9 V, whisky, vodka… | N (objets) ; **sans fonction** dans notre code [vérifié : aucune référence] |
| Tenues | des centaines : hazmat, CTRG, scout, trooper, ghillies, gilets, casques… | N |
| Armes de mêlée | machette, batte, tuyau de plomb, masse, hache d'incendie, matraque | N |
| Sons | compteur Geiger faible / moyen / fort (`rad111`, `rad222`, `rad333`) | N : parfait pour les zones de radiation |

**Inexploité** : la **radio** et le **GPS** pourraient devenir de vrais outils (renseignement sur les événements,
position sur la carte) ; les **maladies** (intoxication, infections, poche de sang contaminée) donnent une couche DayZ
gratuite à brancher.

#### Mutants & Zombies, Zombie Awareness, CoroUtil — vérifié

- Mutants & Zombies : Crawler, Blister, Brute, Spitter (le Spitter est coupé par choix du propriétaire).
- Zombie Awareness : les zombies entendent les tirs, sentent les joueurs, voient la lumière ; **hordes errantes**
  disponibles mais coupées (`wanderingHordes = false`).
- **Les trois doivent être remplacés par notre zombie maison** (ARCHITECTURE, étape 4). Conseil : **ne rien construire de
  nouveau sur eux**. L'attraction des zombies par le bruit d'un événement (crash, explosion) doit passer par notre code
  (le réglage `noiseAttractsMonsters = 48` des frappes aériennes le fait déjà).

#### Les autres

| Mod | Ce qu'il apporte | Pour le game design |
|---|---|---|
| cfm « PATCH » (meubles, avec les props ArkaniaZ) | décors, fenêtres cassables, coffres-forts utilisés comme planque et coffre VIP | N |
| Curios | emplacements d'équipement (masque, gilet, sac…) | N |
| MAtmos + TCTcore | ambiance sonore | N (à remplacer un jour) |
| Passable Foliage + Kiwi | traverser les feuillages | N |
| Framework, Player Animation Lib, Cloth Config | bibliothèques | — |
| ModernFix, FerriteCore, MemoryLeakFix, PacketFixer, EntityCulling, Embeddium | optimisation | — |

### 2.2 Nos systèmes : les briques réutilisables pour des événements

C'est la partie la plus importante : **beaucoup de choses existent déjà**.

| Brique | Où | Ce qu'elle sait faire | Réutilisation |
|---|---|---|---|
| Trajectoire d'avion calculée des deux côtés | `airstrike/strike/FlightPath.java` | l'avion n'est **pas une entité** : serveur et joueurs calculent la même trajectoire à partir de quelques nombres ; lignes droites, virages, piqué | patrouille et chute de l'hélicoptère, sans saccade et sans coût |
| Modèles d'avions maison | `airstrike/client/model/GeoLoader`, `StrikeRenderer` | avion d'attaque et avion cargo dessinés depuis nos propres modèles, ombres au sol | même chargeur pour un modèle d'hélicoptère et d'épave |
| Zone de feu sans blocs de feu | `airstrike/entity/EffectCloudEntity` (type `NAPALM`) | une seule entité invisible : particules chez les joueurs (qualité réglable), brûlure et dégâts côté serveur, **aucun bloc de feu** quand `destroyBlocks = false` (c'est le cas) | incendie de l'épave |
| Fumée de signal | même entité, type `MARKER` | colonne de fumée colorée | fumée noire de l'épave visible de loin |
| Caisse larguée en parachute | `airstrike/entity/SupplyCrateEntity`, `[supply]` | avion cargo, parachute, atterrissage, fumée de 90 s, disparition au bout de 20 min, table de butin | largage public disputé |
| Explosions sans casse | `airstrike` (`destroyBlocks = false`), assourdissement, bruit qui attire les monstres | | impact du crash |
| Coffres T7 temporaires | `loot/PointsT7.java` : `apparaitre(...)`, `declencher(...)` | 7 modèles T7 (coffre-fort, coffre d'armes, caisse de largage, mallette, reliquaire, capsule, conteneur HeraliZ), animation d'apparition, présence limitée, disparition plus tôt si vidé | caisses de l'épave, oil rig, cargaison |
| Balles TaCZ sur un objet qui n'est pas fait de blocs | `monde/.../train/BallesTrain.java` | les balles s'arrêtent sur la rame du train, avec compensation du ping | solution de secours si TaCZ touchait mal l'entité de l'hélicoptère |
| Partage des points selon les dégâts | `clans/.../ClanEvents.java` (`DEGATS_BOSS`, `onBossTouche`) | dégâts de chaque joueur notés pendant le combat, points partagés par clan à la mort ; boss invoqués par le staff marqués « sans points » | contribution des clans à l'hélicoptère |
| Vagues de zombies autour d'un point | `clans/territory/Assauts`, `assaut.json` | 3 vagues pendant une capture, retirées après | zone chaude, défense de l'épave |
| Capture de zone | territoires des clans | rayon de capture, temps, contestation, barre, HUD | zone chaude temporaire |
| Salles à carte et cartes d'accès | module `bunker` + `SallesLoot` | portes, recharge du loot, fragments de cartes dans le loot | oil rig, récompense d'événement |
| Extractions par vagues | module `monde` (`com.heraliz.extraction`) | 4 zones ouvertes 1 h, tirées à 800 blocs au moins l'une de l'autre | cargaison sensible |
| Carte du monde | `heraliz_carte` | lieux, extractions, territoires, grille de 500 m (secteurs A1, B2…) | afficher un événement par secteur |
| Saison des clans | `SaisonApi` (`pve`, `event`, `raid`) | barème en fichier, journal de chaque point | toutes les nouvelles sources |
| Train | rame calculée d'après l'horaire, sons calculés chez le joueur | | train militaire (plus tard) |
| Boss | 5 donjons avec autel, recharge `respawnMinutes = 60` | | boss du monde annoncés |

---

## 3. Diagnostic de la boucle actuelle

### 3.1 Objectifs des joueurs aujourd'hui

| Horizon | Solo | Clan |
|---|---|---|
| **Court** (une partie) | loot, extraction, survivre aux zombies, revendre | idem + territoires, ravitaillement |
| **Moyen** (une semaine) | quêtes de Varga (39 quêtes, ~43 000 hérals, 16 000 payés pour les grades), salles à carte, cartes d'accès | quêtes de clan (3 par jour, 2 par semaine), niveaux du clan, base, ClashWar (mercredi et dimanche) |
| **Long** (la saison) | **presque rien** une fois Varga fini (pas de classement solo, pas de titre durable) | arbre du clan (605 000 hérals), 8 membres (455 000 hérals), classement de saison et prix |

Constats :
- **Les solos manquent d'objectif long** après Varga. Il leur faut des activités où un solo malin gagne quelque chose
  (vendre une pièce rare, voler une cargaison) sans avoir besoin d'un clan.
- **Peu de rendez-vous** : hors Purge (week-end) et ClashWar (2 soirs), rien ne dit « ce soir à 21 h il se passe quelque
  chose ».

### 3.2 Failles du barème de saison (vérifiées dans le code et les réglages)

**Corrigé le 25/10 (après-midi), rien testé en jeu** : boss (60 points, 100 % / 20 % / 0 par clan, par boss et par
jour), quêtes de clan AFK (minutes actives sur la carte seulement) et distance (à pied seulement). Les boss sont en plus
mis de côté pour l'ouverture. Restent : plafond par clan, salles à carte au 2e passage.

| Faille | Preuve | Effet | Correction proposée |
|---|---|---|---|
| **Boss à 150 points toutes les heures** | `saison.json` : `"boss": 150` ; `respawnMinutes = 60` pour les 5 boss ; boss non plafonnés | un clan qui tient les donjons peut faire **plusieurs milliers de points par jour**, alors qu'un kill vaut 1 point et une salle rouge 40 | 1er kill de chaque boss **par clan et par jour** = plein tarif, ensuite 20 % ; et réveil des boss **annoncé** à des heures fixes (fiche 6.3) |
| **Quêtes de clan AFK** | `ClanEvents.seconde()` : `TEMPS_JEU_MIN` avance pour tout membre connecté, même immobile au spawn ; progression partagée par tout le clan | 8 membres AFK au spawn = « Jouer 180 minutes » en ~23 min ; chaque quête de clan = 5 points de saison + hérals + XP | compter seulement sur la carte, en survie, en ayant bougé ou agi dans la minute ; ou retirer ce type de quête |
| Distance parcourue | `DISTANCE_M` compte tout déplacement de moins de 25 blocs par seconde | assis dans le train ou en véhicule, la quête « Parcourir 10 km » avance seule [à vérifier : le train déplace bien le joueur] | ne pas compter en véhicule ni dans le train |
| Plafond des petites actions **par joueur** | `plafond_jour_joueur = 50` | un clan de 8 a 400 points par jour possibles, un clan de 2 en a 100 : l'écart vient du nombre, pas du talent | ajouter un **plafond par clan** (ex. 150 par jour) : un petit clan actif peut l'atteindre |
| Salles à carte | 5 / 15 / 40 points à chaque recharge (30 min / 2 h / 4 h) ; le loot revient si on se déconnecte dans la salle (audit du 25/10) | un clan qui tient un bunker « traie » la salle | 2e passage dans la même salle le même jour à 50 %, puis 0 ; corriger la règle du déconnecté |
| Catégorie « events » quasi vide | seuls le ClashWar et l'oil rig (non codé, 60 points prévus) | le classement « events » se joue 2 soirs par semaine | ajouter des événements de la carte avec un multiplicateur plus faible que le ClashWar |
| Zone « test » dans les territoires | `zones.json` : `"id": "test", "nom": "Zone de test", "rayon": 30` | si elle est sur la carte, elle peut rapporter des hérals par heure [à vérifier] | la retirer avant l'ouverture |

### 3.3 Économie (chiffres relevés)

| Source / dépense | Montant |
|---|---|
| Rachat des objets de valeur et composants (Mila) | Commun 2, Peu commun 8, Rare 20, Épique 50, Légendaire 200, Mythique 500 |
| Rachat des armes | 15 / 30 / 60 / 120 / 250 / 500 |
| Quêtes de Varga | 100 à 5 000 par quête, ~43 000 au total, 16 000 payés pour passer les grades |
| Quêtes de clan | 250 à 900 (jour), 1 500 à 3 000 (semaine) |
| Territoire tenu | 100 hérals par heure et par zone ; depuis le 27/10 (règle du propriétaire), une zone n'est tenue que 2 h après sa capture puis redevient libre : 200 hérals par capture et par zone, il faut la recapturer |
| ClashWar | 5 000 / 2 000 / 1 000 / 750 + clé d'event |
| Création de clan | 1 750 |
| Arbre du clan + membres | 605 000 + 455 000 |
| Déplacer le cœur | 50 000 |
| Bâtisseur | planche 50, métal renforcé 400, armoire 500, cœur 1 000 |

Repère utilisé pour toutes les récompenses proposées : **un clan actif doit gagner environ 13 000 hérals par jour**
(SAISON_CLANS.md §6). Un événement rare et disputé peut valoir **10 à 20 % de cette journée** pour ceux qui le gagnent,
pas plus, sinon il devient « la seule activité rentable ».

---

## 4. Objectifs de gameplay (les principes à tenir)

| Objectif | Comment le tenir avec ce qu'on a |
|---|---|
| Objectifs court / moyen / long | court : loot et extraction ; moyen : événements à heures fixes, Varga, cartes d'accès ; long : saison, arbre, titres, pièces rares à collectionner pour une fabrication unique |
| Exploration | événements par **secteur** (A1, B2… de la carte M), jamais au point près ; radio militaire pour en savoir plus |
| Conflits clans contre solos | récompenses qu'un solo peut voler (pièces d'épave, cargaison) ; points de saison réservés aux clans, mais **loot pour tous** |
| Récompenser le risque | plus c'est visible et long, plus ça rapporte (épave en feu = attente exposée ; cargaison = porteur repéré) |
| PvP valorisé sans tout-PvP | les événements sont PvE au départ (hélico, zombies) et deviennent PvP parce que tout le monde vient ; points PvP modestes et protégés contre les abus (déjà en place) |
| Valeur des ressources | pièces rares non achetables, dont la présence dépend de la rareté ; aucun objet d'événement vendu par un PNJ |
| Économie cohérente | une seule monnaie (hérals), PB seulement pour la boutique ; pas de « jetons d'événement » |
| Identité DayZ | pas de marqueur exact, on entend et on voit (fumée, rotor, tirs à 256 blocs) ; on fouille, on extrait |
| Utilité des clans | partage de contribution, défense d'un site, base, saison |
| Affrontements spontanés | colonne de fumée, message par secteur, compte à rebours public (incendie, largage) |
| Pas une seule activité rentable | rendements décroissants par clan et par jour sur chaque source ; plafond par clan |
| Anti-AFK, anti-abus | rien ne s'obtient immobile ; tout est validé par le serveur ; une récompense notée sur le disque **avant** d'être donnée |
| Performances | une entité par événement, effets chez les joueurs, rien qui tourne sans événement en cours |

---

## 5. Fiche phare : l'hélicoptère militaire abattu

### 5.0 Faisabilité réelle (ce qu'on peut et ne peut pas faire)

- **ywzj ne peut pas fournir l'hélicoptère** : pas de modèle, pas de son, pas de pilote automatique dans la version
  installée (section 2.1). Le piloter « de l'extérieur » demanderait de forcer son moteur physique : **à éviter**.
- **Notre module de frappes aériennes fait déjà 80 % du chemin** : appareils sans entité qui suivent une trajectoire
  calculée des deux côtés, modèles maison, leurres, tirs au canon, explosions sans casse, assourdissement.
- **Une vraie chute « physique » n'est pas souhaitable** (calcul lourd, résultat imprévisible : l'épave finirait dans un lac
  ou un toit). L'alternative crédible et optimisée : **une chute scénarisée** (spirale en autorotation, fumée, morceaux qui
  se détachent, perte d'altitude accélérée) calculée **une seule fois** au moment de la destruction, vers un **point de
  crash choisi d'avance et valide**, puis jouée à l'identique chez tous les joueurs. C'est fluide, déterministe et
  presque gratuit.

### Fiche (format demandé)

**1. Nom** — « Hélicoptère militaire » (événement de la carte).

**2. Objectif** — Un événement rare, spectaculaire et visible de loin qui :
- rassemble les clans et les solos au même endroit (PvP naturel) ;
- donne un vrai rôle aux armes lourdes (RPG-7, lance-grenades, .50, minigun) ;
- récompense le travail d'équipe (contribution aux dégâts) plutôt que le dernier tir ;
- crée une deuxième phase tendue (épave en feu, compte à rebours, pièces à récupérer).

**3. Expérience du joueur**
- On **entend** un rotor au loin (jusqu'à ~250 blocs), puis on le voit passer bas au-dessus des arbres.
- S'il repère un joueur à découvert, une **alarme de verrouillage** sonne chez la cible et un projecteur rouge le balaie :
  1 à 2 secondes pour se mettre à couvert avant une rafale du mitrailleur, parfois une roquette sur un groupe.
- Les joueurs se regroupent, tirent ; on entend les balles **claquer sur la carlingue** ; à 50 % il fume, à 25 % il
  prend feu et l'alarme hurle.
- Détruit : il part en **spirale**, perd des morceaux, laisse une traînée de fumée, et s'écrase plus loin dans une
  boule de feu. Une **colonne de fumée noire** monte, visible de très loin ; un message annonce « Un hélicoptère
  s'est écrasé dans le secteur D3 ».
- L'épave **brûle 2 min à 3 min 30** (durée tirée au hasard, inconnue des joueurs au départ) : compte à rebours affiché
  au-dessus de l'épave pour ceux qui sont proches. Les caisses sont visibles mais scellées. Tout le serveur arrive : il
  faut **tenir la position** pendant l'incendie.
- Le feu retombe : 2 caisses T6 + 1 coffre T7 s'ouvrent ; jusqu'à 3 pièces d'épave se démontent (maintenir la touche
  4 à 8 secondes, sans bouger, sans être touché).

**4. Déroulement étape par étape**

| Étape | Ce qui se passe | Côté serveur | Côté joueur |
|---|---|---|---|
| 0. Planification | Le Directeur d'événements (fiche 6.1) choisit un créneau : au plus 1 hélico à la fois, 2 à 4 par jour aux heures de pointe, seulement si ≥ 15 joueurs sont sur la carte, jamais pendant le ClashWar | une ligne dans le planning | rien |
| 1. Entrée | L'hélico entre par un bord de la carte et suit une **route de patrouille** calculée : points de passage vers les zones où il y a des joueurs (hors spawn, hors bases), altitude 35 à 55 blocs au-dessus du sol (relevée au calcul de la route, pas à chaque tick) | état « virtuel » : position = fonction du temps (comme les avions des frappes) | la route est envoyée une fois ; son du rotor calculé chez le joueur d'après la route (comme les sons du train), même s'il est loin |
| 2. Matérialisation | Dès qu'un joueur est à moins de ~160 blocs de sa route, une **entité** apparaît (points de vie, boîte de touche en 3 morceaux : cabine, rotor/moteur, queue) ; elle disparaît s'il n'y a plus personne | 1 entité, déplacée le long de la route | dessin de notre modèle, interpolé sur la route : aucune saccade |
| 3. Repérage | Toutes les 10 ticks au plus : joueur le plus proche à moins de 90 blocs, en survie, sur la carte, **à ciel ouvert**, pas à terre, pas au spawn ni dans une zone protégée ; un seul rayon de vue par contrôle | liste des joueurs en ligne (≤ 60), pas de recherche d'entités | — |
| 4. Attaque | L'hélico se met en cercle (rayon ~40) autour de la cible ; **avertissement** 1,5 s (son `radar_lock_warn` de ywzj, projecteur rouge), puis rafale de 8 coups (tir direct calculé par le serveur, dispersion, ~2,5 dégâts par coup, armure appliquée) toutes les 3 à 5 s ; **roquette** sur un groupe de 2+ joueurs toutes les ~25 s (explosion faible, **aucun bloc cassé**) | dégâts appliqués par le serveur ; au plus 2 roquettes en vol | traçantes et impacts en particules, sons `gun_7.62mm_shot`, `rocket_launch` |
| 5. Combat | Les joueurs tirent : dégâts notés **par joueur et par clan au moment du tir** ; points faibles : rotor ×1,5, queue ×1,25 ; balles d'armes légères ×0,6, explosifs ×2 [à valider] | comme pour les boss (`DEGATS_BOSS`), borné aux PV restants | impacts métal (`vehicle_hit_*`), fumée à 50 %, feu à 25 % |
| 6. Fuite | Pas abattu en 12 minutes (ou plus personne à portée pendant 3 min) : il quitte la carte. **Aucune récompense.** | fin d'événement, rien d'écrit | message « L'hélicoptère s'éloigne » aux joueurs proches |
| 7. Destruction | À 0 PV : invulnérable ; choix du **point de crash** (étape suivante), calcul de la chute (spirale de 6 à 10 s) ; un seul paquet envoyé | état écrit sur le disque (événement n°X, point, heure d'impact) | spirale, traînée, rotor qui ralentit, morceaux en particules |
| 8. Point de crash | Liste de **points de crash préparés** (voir « Choix du point de crash ») ; on prend le plus proche, devant l'hélico, entre 80 et 300 blocs, qui passe les contrôles du moment | contrôles légers (voir plus bas) | — |
| 9. Impact | Explosion visuelle et sonore (`explosion_far` audible de loin, onde de choc, assourdissement près), aucun bloc cassé, bruit qui attire les zombies à 48 blocs | création : 1 entité « épave » (modèle brûlé, pièces visibles), 1 zone de feu (`NAPALM`, rayon ~7), 1 fumée noire (`MARKER`), 3 caisses scellées ; **un ticket de chargement sur le seul tronçon du crash** pendant la séquence (sinon, à distance de simulation 6, le tronçon pourrait ne pas tourner) | boule de feu, fumée, message par secteur |
| 10. Incendie | Durée tirée au hasard entre 120 et 210 s, fixée à l'impact ; dans la zone : brûlure et dégâts ; caisses et pièces **refusées par le serveur** avant la fin | heure de fin en temps de jeu, gardée sur l'épave | compte à rebours au-dessus de l'épave et dans un petit bandeau à moins de 30 blocs, calé sur l'heure envoyée (pas de dérive) |
| 11. Ouverture | Le feu retombe : caisses ouvrables ; leur contenu est **tiré une seule fois**, à la première ouverture, puis gardé | tables T6 et T7 existantes | fumée grise qui s'éteint |
| 12. Pièces | 1 à 3 pièces présentes (tirées à l'impact) ; démontage long (voir « Pièces ») | état par pièce : libre / en cours (qui, depuis quand) / prise | barre de progression, pose accroupie, bruits de métal |
| 13. Points de saison | **Attribués une seule fois à la destruction** (pas à l'ouverture des caisses) | voir « Contribution » | message aux clans récompensés |
| 14. Nettoyage | Épave, caisses et fumée retirées 15 min après la fin de l'incendie, ou 2 min après que tout est vide ; ticket de chargement rendu | si le tronçon n'est pas chargé à l'heure prévue, retrait à son prochain chargement | — |

**Choix du point de crash** (pour qu'il soit valide, accessible et jamais dans l'eau) :
1. **Hors ligne, une fois** : un outil (sur le modèle de `outils/extractions_carte.py`, lecture seule d'une copie de la
   carte) relève des points candidats : sol plat de 11 × 11 blocs (±1,5 de hauteur), sol solide et non liquide, ciel
   dégagé, pas dans une zone de construction interdite pour rien, pas dans le spawn, pas dans une salle à carte, à
   distance des grandes villes (pour que l'épave ne tombe pas dans une maison). Le staff peut aussi en poser en jeu
   (`/event helico point ajouter`, comme les extractions). Objectif : 80 à 150 points répartis sur la carte.
2. **Au moment du crash** : on garde les points entre 80 et 300 blocs, dans un cône de ±70° devant l'hélico, puis on
   vérifie seulement : aucun claim de base CRac à moins de 40 blocs, pas de zone VIP, et (si le tronçon est chargé) le
   bloc du dessus n'est ni de l'eau ni un bloc de construction. Le plus proche gagne.
3. **Aucun point valide** : l'hélico explose en l'air au-dessus de sa position ; seulement 1 caisse T6 au sol, pas de
   pièces, points de saison réduits de moitié (cas rare, à surveiller dans le journal).

**Contribution et points de saison** (~10 points, à équilibrer)
- Dégâts notés **par joueur, avec le clan du joueur au moment du tir** (pas celui qu'il a à la fin) ; dégâts réels bornés
  aux PV restants (comme pour les boss) ; seulement des joueurs en survie sur la carte.
- À la destruction : part de chaque clan = ses dégâts / **tous** les dégâts des joueurs (les solos comptent dans le total :
  leurs dégâts ne vont à aucun clan).
- **Clan admis** s'il a fait au moins 15 % des dégâts [à valider]. Les 10 points sont partagés entre les clans admis selon
  leur part (recalculée entre eux). Plusieurs clans peuvent donc gagner ; le dernier tir ne compte pas plus qu'un autre.
- **Une seule attribution** : l'événement a un numéro ; la ligne « récompensé » est écrite sur le disque **avant** d'appeler
  `SaisonApi.pve(...)` ; après un redémarrage, un événement déjà récompensé ne l'est plus jamais.
- **Changement de clan pendant l'événement** : les dégâts restent au clan d'origine ; un joueur qui change de clan ne peut
  pas « emporter » ses dégâts. Clan dissous entre-temps : sa part est perdue. (Le délai de 24 h avant de rejoindre un autre
  clan, encore [à valider] dans SAISON_CLANS.md §7, rend ce cas très rare.)
- **Anti-farm** : seuls les hélicoptères lancés par le Directeur rapportent (un hélico lancé par le staff est marqué « sans
  points », comme les boss invoqués) ; au plus **2 hélicos récompensés par clan et par jour** [à valider] ; XP de clan
  100 partagée de la même façon.
- Comparaison avec le barème actuel : kill 1, coffre T7 5, salle bleue 15, salle rouge 40, boss 150 (trop haut, voir 3.2).
  10 points pour l'hélico + 5 pour le coffre T7 (règle existante, première ouverture) + 2 × 1,5 pour les T6 = **environ
  18 points** au total pour un clan qui fait tout : cohérent avec une salle bleue-rouge, bien plus disputé.

**Pièces d'épave** (jusqu'à 3)

La rareté joue sur la **chance que la pièce soit présente** sur l'épave ; tirage indépendant pour chaque pièce, on garde au
plus 3 pièces (les plus rares d'abord), au moins 1 (la plus commune si rien n'est sorti). Prix au rachat chez Mila, en
gardant l'échelle actuelle (Légendaire 200, Mythique 500 pour un objet de valeur ordinaire) mais au-dessus, car c'est un
butin d'événement disputé :

| Pièce | Rareté | Présente | Démontage | Rachat (Mila) | Variante sans nouvelle monnaie |
|---|---|---|---|---|---|
| Pale de rotor tordue | Peu commun | 70 % | 4 s | 120 | recyclage → plaques soudées et ferraille |
| Morceau de mitrailleuse (canon M134) | Rare | 45 % | 6 s | 300 | recyclage → Canon III ; ou ingrédient d'une recette spéciale |
| Module électronique de bord | Rare | 45 % | 5 s | 300 | recyclage → Circuit imprimé III ×2 |
| Boîtier de navigation militaire | Épique | 25 % | 6 s | 700 | échange chez un marchand « troc » contre 1 fragment de carte d'accès bleue |
| Turbine (pièce mécanique rare) | Épique | 20 % | 7 s | 700 | recyclage → Ressort IV + Kit de pièces IV |
| Boîte noire | Légendaire | 10 % | 8 s | 1 500 | donnée à Varga : révèle la route du prochain hélico (avantage d'information) — pas de hérals dans ce cas |

- Valeur moyenne des pièces d'un hélico : **~700 hérals** ; avec 2 caisses T6 et 1 coffre T7, un hélico vaut environ
  1 500 à 2 500 hérals de butin, partagé entre ceux qui l'emportent : 10 à 20 % d'une journée de clan, pour un événement
  à 2 à 4 par jour. **Ne devient pas la seule activité rentable.**
- Option « cours du marché » [à valider] : chaque pièce identique vendue dans les dernières 24 h baisse son prix suivant de
  10 % (plancher 50 %). Utile seulement si les pièces inondent le marché ; à ne pas faire au départ (une règle de plus à
  expliquer).
- Aucun PNJ ne **vend** ces pièces (elles ne peuvent donc pas être achetées puis revendues : règle déjà en place chez
  Mila). Pas de nouvelle monnaie : hérals, composants ou fragments existants.
- Une identité distincte de la boîte noire de la quête 29 (objet de quête, non vendable) : nouvel identifiant, autre aspect.

**Démontage d'une pièce** (interaction longue)
- Clic droit maintenu sur la pièce (visible sur le modèle de l'épave, contour au survol) ; le serveur **démarre** le
  chronomètre à la demande et le **termine lui-même** ; le jeu du joueur ne fait qu'afficher la barre.
- Annulé si le joueur bouge de plus de 0,5 bloc, s'éloigne à plus de 2,5 blocs, est touché, tombe à terre, se déconnecte,
  ou si quelqu'un d'autre a fini avant lui. Une pièce n'est démontée **qu'une fois** (état gardé sur l'épave, sur le disque).
- Retour : pose accroupie, bruits de métal (`vehicle_hit_small`) chaque seconde, barre au style du kit d'interface.

**5. Mods et fonctionnalités existants nécessaires**
- TaCZ (armes, balles qui touchent les entités à plusieurs morceaux) ;
- module `monde/airstrike` : trajectoire calculée, chargeur de modèles, explosions sans casse, zone de feu `NAPALM`,
  fumée `MARKER`, assourdissement, bruit qui attire les zombies ;
- `PointsT7.apparaitre` (coffre T7 temporaire) et les tables T6/T7 ;
- partage des dégâts des boss (`ClanEvents`) et `SaisonApi` ;
- sons de ywzj (tirs, impacts métal, alerte de verrouillage) ;
- carte M (secteurs), recycleur, marchands (Mila, troc), Varga.

**6. Développement spécifique**
- **Modèles** : hélicoptère en vol, épave brûlée avec 6 emplacements de pièces, icônes des 6 pièces, éventuellement 2
  caisses T6 « militaires » d'épave. **Rendus à valider d'abord** (règle 6).
- **Son du rotor** (boucle + passage) : aucun dans les mods ; à fabriquer par calcul comme les sons du train
  (`sons_train.py`) ou à faire fournir par le propriétaire.
- Entité hélicoptère (3 morceaux, points de vie, état synchronisé : phase, fumée), « cerveau » de patrouille et d'attaque,
  calcul de la chute, entité épave, caisses scellées avec heure d'ouverture (petit ajout à `CoffreBlockEntity` : une
  variante T6 temporaire et un verrou jusqu'à une heure), démontage des pièces, outil des points de crash, réglages
  `heraliz_events-server.toml` (PV, dégâts, durées, fréquences), textes dans les 3 langues.
- Si les balles de TaCZ touchent mal l'entité en mouvement : reprendre la méthode du train (`BallesTrain`).

**7. Récompenses et équilibrage indicatif**
- Saison : 10 points partagés (clans à 15 % des dégâts ou plus), au plus 2 hélicos par clan et par jour.
- XP de clan : 100 partagés pareil.
- Butin : 2 caisses T6, 1 coffre T7 (tables existantes), 1 à 3 pièces (tableau ci-dessus).
- Points de vie : à régler pour que **4 à 6 joueurs armés de fusils d'assaut** l'abattent en **60 à 120 s** à découvert ;
  une roquette de RPG-7 au but : ~12 % ; une grenade de lance-grenades : ~6 %.
- Danger : rafale ~20 dégâts au total si tout touche (un joueur sans armure peut tomber à terre), roquette rare ; jamais
  de tir sur un joueur à couvert (sous un toit).
- Quête de Varga facultative : « Abattre un hélicoptère » (chapitre 4 ou 5) ; quête de clan « Participer à un crash ».

**8. Interaction clans / factions / économie**
- Les clans coopèrent malgré eux pour l'abattre, puis se battent pour l'épave : conflit naturel.
- Les solos ne gagnent pas de points de saison mais peuvent **voler le butin** pendant que les clans s'entretuent.
- Les gros clans ne gagnent pas tout : partage par dégâts, plafond par jour, et l'épave tombe loin du lieu du combat (les
  premiers sur place ne sont pas forcément les tireurs).
- Économie : butin rare et non achetable, prix au niveau d'un Légendaire / Mythique ; aucun effet sur les prix du reste.

**9. Abus et protections**

| Abus possible | Parade |
|---|---|
| Faire apparaître un hélico pour farmer | seulement par le Directeur ; hélico du staff « sans points » ; 2 par clan et par jour |
| Voler les points avec le dernier tir | partage par dégâts ; seuil de 15 % |
| Deux clans complices qui « se passent » les points | les points ne sont pas transférables ; le seuil et le plafond limitent le gain |
| Changer de clan pendant le combat | dégâts liés au clan au moment du tir |
| Ouvrir les caisses pendant le feu | refus côté serveur, heure de fin connue du serveur seulement |
| Démonter deux fois la même pièce | état « prise » écrit sur l'épave ; un seul démontage en cours par pièce |
| Démonter en se cachant derrière un mur / à distance | portée 2,5 blocs vérifiée par le serveur, pas à travers un bloc |
| Se déconnecter pendant un démontage | démontage annulé, pièce libérée |
| Redémarrage pendant l'événement | hélico en vol : événement annulé, aucun point ; épave déjà au sol : reprise avec le temps restant (temps de jeu) ; points déjà donnés : jamais redonnés |
| Hélico coincé ou tronçon déchargé | état « virtuel » sur la route ; tronçon du crash tenu chargé pendant la séquence |
| Se mettre sous l'hélico au spawn ou dans sa base pour être intouchable | jamais de route au-dessus du spawn ; dans une base, il tire comme ailleurs (aucun bloc cassé) |
| Lag ou triche (« aimbot » sur l'hélico) | seuls les dégâts validés par TaCZ comptent ; Spartan reste l'arbitre |

**10. Impact performance**
- Serveur : hors événement, **rien** ne tourne. Pendant : 1 entité (déplacement par calcul simple), un contrôle de
  repérage toutes les 10 ticks sur la liste des joueurs, 1 rayon de vue, au plus 2 roquettes ; après le crash : 1 épave,
  1 zone de feu, 1 fumée, 3 blocs de caisses ; 1 tronçon tenu chargé quelques minutes. Négligeable.
- Joueurs : 1 modèle (qualité à valider : viser un modèle léger), particules réglées par la qualité d'effets existante
  (`QualiteFx`), morceaux de la chute en particules seulement (aucune entité), sons calculés chez le joueur.

**11. Complexité** — **élevée** (modèles, son, entité, chute, épave, pièces), mais beaucoup de briques existent. Ordre
conseillé : (a) rendus et son ; (b) version « simple » : patrouille, attaque, crash sur un point, épave + caisses ;
(c) pièces et démontage ; (d) réglages fins.

**12. Tests avant production**
1. Essai sur le serveur de test : balles TaCZ, RPG, grenades et .50 touchent bien les 3 morceaux de l'hélico en vol.
2. Repérage : aucun tir sur un joueur sous un toit, à terre, au spawn, en créatif.
3. 20 crashs forcés sur des points variés : jamais dans l'eau, jamais dans un claim, toujours accessible à pied.
4. Compte à rebours identique chez 3 joueurs (pings différents) ; caisses refusées 1 s avant la fin, ouvertes après.
5. Points : 3 clans à 50 / 30 / 20 % + un solo ; vérifier le journal `saison_journal.log` ; refaire après un redémarrage.
6. Changement de clan en plein combat ; dissolution d'un clan avant la fin.
7. Démontage : mouvement, coup reçu, déconnexion, deux joueurs sur la même pièce.
8. Redémarrage du serveur pendant le vol, pendant l'incendie, après l'ouverture.
9. Banc de charge (`/bots scenario`) avec un hélico actif : ticks, mémoire, nombre d'entités ; `/diag client` sur un petit PC
   pendant le crash.
10. Rendus validés, son du rotor validé.

Classement : impact **élevé**, complexité **élevée**, priorité **prochaine mise à jour** (rendus dès maintenant).

---

## 6. L'écosystème : les autres propositions

Seulement ce qui est pertinent avec le contenu réel, sans dix variantes du même événement. Chaque fiche suit le format
demandé, en plus court quand le sujet est simple.

### 6.1 Directeur d'événements (le socle)

1. **Nom** — Directeur d'événements.
2. **Objectif** — Lancer seul les événements de la carte à des heures choisies, sans chevauchement, avec des annonces
   claires ; faire revenir les joueurs à heures fixes.
3. **Expérience** — Un agenda dans `/events` (et sur le menu Échap ou la carte M) : « 19 h 30 Largage · 21 h Hélicoptère ·
   22 h Zone chaude ». Annonces à la radio : « Activité militaire signalée secteur C4 ».
4. **Déroulement** — planning en fichier (jours, heures, événement, probabilité) ; à l'heure dite, vérifie les conditions
   (joueurs sur la carte, pas de ClashWar, pas d'autre événement du même type) ; choisit un lieu (points préparés) ;
   annonce par secteur ; suit l'événement ; écrit le résultat dans un journal ; appelle `SaisonApi`.
5. **Existant** — `SaisonApi`, carte M (secteurs de 500 m), notification en haut à gauche (déjà utilisée pour les
   extractions), `/recharger` (données vivantes), moniteur `/diag`.
6. **Développement** — un petit module (ou une partie de `events`) : planning `serveur/config/heraliz_events/planning.json`,
   points de lieux par type, annonces, journal, commandes staff `/event lancer|annuler|liste|point`.
7. **Récompenses** — aucune par lui-même.
8. **Interactions** — porte toutes les fiches suivantes ; évite les heures de Purge et de ClashWar si on le souhaite.
9. **Abus** — un événement lancé par le staff est « sans points » par défaut (option pour les events officiels) ; un
   seul exemplaire actif par type.
10. **Performance** — un contrôle par minute ; rien d'autre.
11. **Complexité** — moyenne.
12. **Tests** — planning sur une journée accélérée ; redémarrage au milieu ; deux événements à la même heure.

Impact **élevé**, complexité **moyenne**, priorité **immédiate**.

### 6.2 Largage disputé (public)

1. **Nom** — Largage militaire.
2. **Objectif** — Un événement court, fréquent, peu coûteux, qui crée des escarmouches.
3. **Expérience** — Un avion cargo passe (déjà modélisé), une caisse descend en parachute avec une fumée bleue ; message
   « Largage dans le secteur B2 » ; la caisse T7 « largage » s'ouvre à l'atterrissage.
4. **Déroulement** — le Directeur choisit un point de crash/largage valide ; frappe `SUPPLY` existante sans désignateur ;
   la caisse reste 20 min puis disparaît.
5. **Existant** — `airstrike` (avion cargo, `SupplyCrateEntity`, fumée de 90 s, disparition), table du largage (refaite le
   25/10 avec notre loot), type de coffre `t7_largage`.
6. **Développement** — appel du largage par le Directeur (le système existe, il manque le déclencheur), choix du point.
7. **Récompenses** — butin du largage (arme Rare ou Épique, munitions, matériel militaire) ; saison : 5 points au clan du
   premier qui l'ouvre. [vérifié] Les coffres T7 de HeraliZ Loot donnent déjà ces 5 points (`CoffreBlockEntityServeur` →
   `SaisonLoot.conteneur(p, 7)`), **mais la caisse en parachute des frappes aériennes est une autre entité**
   (`SupplyCrateEntity`) : il faut soit qu'elle se change en coffre `t7_largage` à l'atterrissage, soit une ligne qui
   appelle `SaisonApi.pve(joueur, "conteneur_t7", 1)` à sa première ouverture.
8. **Interactions** — attire clans et solos ; les solos rapides peuvent gagner.
9. **Abus** — au plus 1 largage public à la fois ; pas au-dessus du spawn ni d'une base.
10. **Performance** — déjà mesurée avec les bots (frappes et largages dans `/bots scenario`).
11. **Complexité** — faible.
12. **Tests** — 10 largages : jamais dans l'eau, caisse toujours ouvrable, disparition propre.

Impact **moyen**, complexité **faible**, priorité **immédiate**.

### 6.3 Boss du monde annoncés (et correction du barème)

1. **Nom** — Réveil des boss.
2. **Objectif** — Garder les 5 boss comme gros moments PvE, mais **disputés** au lieu d'être farmés en silence.
3. **Expérience** — « Ymirak se réveille au cimetière des mammouths dans 15 minutes » : les clans s'y rendent, se croisent à
   l'entrée du donjon.
4. **Déroulement** — `respawnMinutes` passe de 60 à 240-360 min [à valider] ; le Directeur programme le réveil (au lieu de
   l'autel libre) ou ouvre une fenêtre d'1 h pendant laquelle l'autel marche ; annonce 15 min avant.
5. **Existant** — 5 donjons, autels, réveil par le staff (`/boss … eveil`), partage des points par dégâts.
6. **Développement** — petit : appeler le réveil depuis le Directeur ; règle « 1er kill de chaque boss par clan et par
   jour = plein tarif, ensuite 20 % » dans `ClanEvents` ; barème boss revu.
7. **Récompenses** — saison : **boss 60 points** au lieu de 150 [à valider], partagés par dégâts, plein tarif une fois par
   boss et par clan et par jour ; XP 50 (inchangé).
8. **Interactions** — combat pour l'accès au donjon ; les petits clans peuvent « finir » un boss affaibli (mais la part
   suit les dégâts).
9. **Abus** — farm horaire supprimé ; boss invoqués déjà sans points.
10. **Performance** — inchangée (moins de boss par jour = mieux).
11. **Complexité** — faible.
12. **Tests** — réveil programmé, annonce, deux clans sur le même boss, deuxième kill du jour à 20 %.

Impact **élevé**, complexité **faible**, priorité **immédiate**.

### 6.4 Zone chaude (zone de conflit temporaire)

1. **Nom** — Zone chaude.
2. **Objectif** — Un « roi de la colline » court (20 min) dans un lieu de la carte, pour le classement « events ».
3. **Expérience** — « Zone chaude ouverte : hangar de l'aérodrome, 20 minutes » ; cercle lumineux comme les drapeaux et les
   extractions ; petit HUD avec le temps tenu par clan ; vagues de zombies pendant la tenue.
4. **Déroulement** — ouverture annoncée 10 min avant ; on gagne du temps en étant seul clan (ou solo) dans le cercle ;
   contestée = personne ne gagne de temps ; fin : classement au temps tenu.
5. **Existant** — capture de zone (rayon, contestation, barre), vagues `Assauts` (difficulté 1 à 3), cercle lumineux,
   `SaisonApi.event` (grille de places, multiplicateurs par event dans `saison.json`).
6. **Développement** — une zone temporaire (pas un territoire), compteur de temps par clan, fin et classement.
7. **Récompenses** — saison catégorie events avec **multiplicateur 0,3** [à valider] : 30 / 18 / 12 / 6 + 1,5 de participation
   (le ClashWar garde ×2 : il reste le gros event) ; butin : 1 coffre T7 au centre pour le 1er, ouvert par lui seul 1 min
   puis par tous.
8. **Interactions** — petits clans et solos peuvent tenir par la ruse quelques minutes ; gros clans visibles et attaqués.
9. **Abus** — participation comptée seulement après 60 s dans le cercle et au moins un tir ou un dégât reçu ; pas plus d'une
   zone chaude par jour au départ ; 4 clans différents au moins pour donner les points de places (comme le ClashWar).
10. **Performance** — un contrôle par seconde des joueurs proches du cercle (liste, pas recherche d'entités) ; vagues déjà
    plafonnées.
11. **Complexité** — moyenne.
12. **Tests** — 3 clans et 2 solos, contestation, déconnexion dans le cercle, fin pendant une contestation.

Impact **élevé**, complexité **moyenne**, priorité **immédiate** (ou début de la prochaine mise à jour).

### 6.5 Cargaison sensible (objet à extraire)

1. **Nom** — Cargaison sensible.
2. **Objectif** — Le meilleur moteur de PvP naturel : un objet de grande valeur qu'il faut **ramener** par une extraction,
   pendant que son porteur est repérable.
3. **Expérience** — On trouve une « Mallette de renseignement » (coffre T7 `t7_mallette` d'un événement : épave, oil rig,
   zone chaude). Dès qu'on la porte, sa position approximative (cercle de 75 blocs) apparaît toutes les 60 s sur la carte
   de **tous** ; il faut tenir 90 s dans une extraction ouverte pour l'encaisser.
4. **Déroulement** — objet unique, non rangeable dans un sac, un coffre ou la planque, tombe à la mort (sur le corps) ;
   encaissé à l'extraction : hérals au porteur, points au clan ; non encaissé en 30 min : il « s'autodétruit » (rien).
5. **Existant** — extractions par vagues (4 ouvertes, à 800 blocs l'une de l'autre), carte M, cadavres, coffre T7 mallette.
6. **Développement** — objet et règles de transport, marqueur approximatif sur la carte, encaissement à l'extraction.
7. **Récompenses** — 2 000 hérals au porteur [à valider] ; saison 15 points PVE au clan du porteur à l'encaissement ; XP 80.
8. **Interactions** — un solo peut encaisser (hérals sans points) ; un clan protège son porteur ; les autres chassent :
   conflit clans contre solos très lisible.
9. **Abus** — une seule cargaison active ; pas encaissable par un joueur qui l'a reçue d'un membre de son clan **dans les
   10 dernières secondes** près de l'extraction (anti-relais) [à valider] ; objet refusé dans tous les rangements ;
   déconnexion = l'objet tombe au sol à sa position.
10. **Performance** — un marqueur par minute ; rien d'autre.
11. **Complexité** — moyenne.
12. **Tests** — mort du porteur, déconnexion, tentative de le ranger, encaissement par un solo, fin des 30 min.

Impact **élevé**, complexité **moyenne**, priorité **prochaine mise à jour**.

### 6.6 Radio militaire et renseignement (objets geckolib)

1. **Nom** — Radio militaire.
2. **Objectif** — Donner une fonction à la **radio** et au **GPS** de geckolib222 (aujourd'hui de simples objets) ;
   récompenser l'exploration et l'information plutôt que le bruit.
3. **Expérience** — Clic droit avec la radio (piles 9 V comme « charges ») : « Grésillements… hélicoptère en patrouille vers
   le secteur E2 ». Le GPS affiche ses coordonnées et son secteur sans ouvrir la carte.
4. **Déroulement** — la radio lit l'état du Directeur (prochain événement, secteur approximatif) ; une charge par écoute.
5. **Existant** — objets `geckolib:radio`, `geckolib:gps`, `geckolib:alkaline_battery_9v`, Directeur. [vérifié] Ils ont
   été **retirés des tables de loot le 22/10** comme « petits objets inutiles » (`hors_coffres` de `outils/loot_armes.py`) :
   il faudra les y remettre (rares, T3 et plus) une fois qu'ils servent à quelque chose.
6. **Développement** — faible : comportement d'objet côté serveur (sans toucher au jar geckolib, par événement Forge de
   clic droit).
7. **Récompenses** — l'information elle-même.
8. **Interactions** — avantage aux joueurs organisés et aux solos qui veulent arriver avant les clans.
9. **Abus** — information approximative et en retard (1 à 2 min) ; jamais la position d'un joueur.
10. **Performance** — nulle.
11. **Complexité** — faible.
12. **Tests** — sans pile, avec pile, sans événement en cours.

Impact **moyen**, complexité **faible**, priorité **prochaine mise à jour**.

### 6.7 Plateforme pétrolière (oil rig)

1. **Nom** — Plateforme pétrolière.
2. **Objectif** — L'événement « fin de partie » prévu par SAISON_CLANS.md : salle à carte rouge + coffre T7, très risqué.
3. **Expérience** — On y va en bateau ou à la nage ; la carte rouge ouvre la salle ; des gardes arrivent par vagues.
4. **Déroulement V1 (sans gardes armés)** : vagues de zombies costauds et coureurs (zombie maison) au lieu de soldats ;
   salle rouge existante ; coffre T7 au sommet. **V2** : gardes armés (nouveau système de PNJ tireurs).
5. **Existant** — plateforme sur la carte (zone interdite à la construction, rayon 150), salles à carte, cartes rouges et
   leurs fragments, `oil_rig: 60` déjà dans le barème, vagues `Assauts`.
6. **Développement** — V1 : moyen (déclencheur, vagues, coffre) ; V2 : **élevé** (IA de tireur, équilibrage, perf).
7. **Récompenses** — 60 points PVE déjà prévus : **trop haut à côté de l'hélico** ; proposer 30 [à valider] ; T7 et salle rouge.
8. **Interactions** — clans organisés ; un solo peut voler la sortie (cargaison sensible dans le T7).
9. **Abus** — une prise par recharge ; points au clan qui ouvre le T7, une fois.
10. **Performance** — V1 faible ; V2 à mesurer (PNJ armés = tirs, chemins).
11. **Complexité** — moyenne (V1) / élevée (V2).
12. **Tests** — accès, vagues, recharge, et en V2 banc de charge avec gardes.

Impact **moyen**, complexité **moyenne / élevée**, priorité **plus tard** (V1 possible en prochaine mise à jour).

### 6.8 Train militaire (convoi sur rails)

1. **Nom** — Train militaire.
2. **Objectif** — Utiliser le train, qui est déjà un objet unique et techniquement abouti, comme événement.
3. **Expérience** — Une fois par soirée, un wagon blindé est ajouté à la rame ; il porte un coffre T6 qui ne s'ouvre qu'en
   gare (portes ouvertes) ; on se bat sur les quais.
4. **Déroulement** — wagon marqué ; coffre ouvrable seulement à l'arrêt ; une gare par tour ; disparaît à la fin du tour.
5. **Existant** — train (horaire, gares, portes, balles sur la rame, vitres), « loot dans le train » prévu mais pas fait.
6. **Développement** — élevé : emplacements de loot dans une voiture qui bouge (le train n'est pas fait de blocs), modèle du
   wagon (rendus).
7. **Récompenses** — butin T6 ; saison : conteneur T6 normal.
8. **Interactions** — embuscades en gare, solos furtifs dans le train.
9. **Abus** — une ouverture par gare et par tour.
10. **Performance** — à mesurer (le train est déjà réglé).
11. **Complexité** — élevée.
12. **Tests** — ouverture en roulant refusée, gare, déconnexion dans le train.

Impact **moyen**, complexité **élevée**, priorité **plus tard**.

### 6.9 Maladies façon DayZ (effets geckolib déjà présents)

1. **Nom** — Maladies.
2. **Objectif** — Survie plus DayZ sans barre de soif (refusée par le propriétaire) : nourriture abîmée, eau sale, poche de
   sang contaminée, blessures sales.
3. **Expérience** — Manger une conserve abîmée peut donner une **intoxication** (vomissements, faim qui chute) ; une plaie
   non soignée peut s'**infecter** ; la tétracycline et le charbon (ou comprimés de chlore pour l'eau) soignent.
4. **Déroulement** — règles simples et rares, toujours soignables avec ce qu'on trouve dans le loot.
5. **Existant** — effets `FOOD_POISONING`, `BACTERIAL_INFECTION`, `PARASITIC_INFECTION`, `IRRADIATED`,
   `RADIATION_PROTECTION` ; objets tétracycline, chlore, poche de sang contaminée, Antiradin ; `FoodState` des aliments.
6. **Développement** — moyen : savoir **ce que font déjà** ces effets dans le jar (non décompilé) [à vérifier], puis brancher
   des déclencheurs côté serveur.
7. **Récompenses** — valeur des soins en hausse (marché de Jacqueline).
8. **Interactions** — économie des soins, zones de radiation (Antiradin).
9. **Abus** — aucune maladie mortelle sans avertissement ; pas de maladie en zone protégée.
10. **Performance** — nulle (événements de manger / boire / blesser).
11. **Complexité** — moyenne.
12. **Tests** — chaque maladie, chaque remède, mort, déconnexion.

Impact **moyen**, complexité **moyenne**, priorité **plus tard**.

### 6.10 Prime sur les meneurs (anti-domination)

1. **Nom** — Prime sur les meneurs.
2. **Objectif** — Que les gros clans ne puissent pas accumuler sans opposition.
3. **Expérience** — Les 3 premiers clans du classement général portent une « prime » (tag doré-rouge dans le tab) ; les
   tuer rapporte plus aux autres clans.
4. **Déroulement** — kill d'un membre d'un clan du top 3 par un clan hors du top 3 : PVP ×1,5 ; prise de cœur réussie
   contre un clan du top 3 : +5 % volés en plus.
5. **Existant** — classement (`Saison.classement()`), règles PVP (même IP, menace, ×1 / ×0,5 / 0), prise du cœur.
6. **Développement** — faible (coefficients dans `Saison.kill` et `Saison.raid`, réglages dans `saison.json`).
7. **Récompenses** — voir 4.
8. **Interactions** — les petits clans ont une raison de viser les gros ; les gros doivent se protéger.
9. **Abus** — mêmes protections que le PVP (même PC/IP, victime armée, même victime) ; pas de bonus entre deux clans du top 3.
10. **Performance** — nulle.
11. **Complexité** — faible.
12. **Tests** — kill top 3 par hors top 3, et l'inverse ; changement de classement en cours de journée.

Impact **moyen**, complexité **faible**, priorité **prochaine mise à jour**.

### 6.11 Points de capture contestée (territoires)

1. **Nom** — Capture contestée.
2. **Objectif** — Récompenser **le conflit** pour les territoires, pas la tenue passive.
3. **Expérience** — Prendre une ville tenue par un autre clan rapporte des points de saison ; tenir une ville rapporte
   toujours des hérals et de l'XP (inchangé), **pas** de points de saison.
4. **Déroulement** — à la capture d'une zone tenue depuis au moins 2 h par un autre clan : points = 4 × valeur de la ville
   (valeur 2 → 8 points, aérodrome valeur 5 → 20).
5. **Existant** — captures (180 s pour une zone tenue, vagues de zombies), valeurs des villes, stats des captures.
6. **Développement** — faible (appel de `SaisonApi.pve` à la capture, règles anti-abus).
7. **Récompenses** — voir 4 ; plafond 3 captures récompensées par clan et par jour.
8. **Interactions** — incite à attaquer les zones des gros clans ; les petits clans peuvent reprendre une ville quand le
   défenseur dort, mais seulement après 2 h de tenue (pas de ping-pong).
9. **Abus** — deux clans complices qui s'échangent une ville : même zone, mêmes deux clans = une fois par 48 h ; zone tenue
   moins de 2 h = rien ; même IP / même PC parmi les capteurs et les défenseurs = rien.
10. **Performance** — nulle.
11. **Complexité** — faible.
12. **Tests** — capture d'une zone libre (rien), d'une zone tenue 1 h (rien), 3 h (points), ping-pong.

Impact **moyen**, complexité **faible**, priorité **prochaine mise à jour**. Réactive aussi la quête 32 de Varga
(« Capturer un territoire »), désactivée « tant que les territoires ne sont pas faits » alors que `zones.json` est rempli.

---

## 7. Points de saison et ClashWar

### 7.1 Ce qui existe (vérifié)

- Barème `serveur/config/heralisz_clans/saison.json`, relu par `/saison recharger` ; journal de chaque point.
- **PVE** : zombies 0,01 / mutants 0,02 / Brute 0,5 ; boss 150 (partagé par dégâts) ; salles 5 / 15 / 40 ; conteneurs T1 à T6
  0,05 à 1,5 ; T7 5 ; vente 1 point pour 1 000 hérals ; craft 1 à 8 ; quête de Varga 10 ; quête de clan 5 ; oil rig 60 (non
  codé). Plafond 50 points par joueur et par jour pour les petites actions.
- **PVP** : +1 / −1, jamais sous 0 ; rien même IP, même clan, victime sans arme ni armure (Peu commun et plus, hors kit) ;
  même victime ×1, ×0,5, 0 sur 6 h ; rien pendant la protection de réapparition.
- **Events** : places 100 / 60 / 40 / 20 + 5 de participation ; ClashWar ×2, au moins 4 clans.
- **Raid** : prise du cœur pendant la Purge : −15 % au pris, +10 % à l'attaquant, une fois par Purge.
- Classement général = **somme des places** dans les 3 catégories (la plus petite gagne) : bonne idée, une catégorie
  gonflée ne compte que pour une place.
- Purge : vendredi 18-21 h, samedi et dimanche 15-22 h.
- ClashWar : mercredi et dimanche 20 h 30, formats 2v2, 3v3, 4v4, 1v1 en rotation, BO5, 5 000 / 2 000 / 1 000 / 750 hérals.

### 7.2 Ce qui ne va pas

1. **Boss** : 150 points toutes les heures par boss (3.2) : écrase toute la catégorie PVE.
2. **AFK** : quêtes de clan « Jouer N minutes » au spawn (3.2).
3. **Taille du clan** : plafond par joueur, donc un clan de 8 a 4 fois plus de petits points qu'un clan de 2.
4. **Events** : 2 soirs par semaine, rien entre les deux.
5. **Territoires** : ne donnent aucun point de saison, mais rien non plus ne récompense le fait de **prendre** une ville à
   un autre clan.
6. **Aucune opposition aux leaders** à part la Purge.

### 7.3 Sources complémentaires proposées

| Source | Objectif | Conditions | Récompense indicative | Fréquence | Risque d'abus | Anti-farm | Petit / grand clan | Perf | Interactions |
|---|---|---|---|---|---|---|---|---|---|
| **Hélicoptère abattu** (5) | gros moment PvE puis PvP | ≥ 15 % des dégâts | 10 partagés (PVE) + T7 5 + T6 1,5 ×2 | 2 à 4 / jour | moyen | Directeur seul ; 2 / clan / jour ; dégâts liés au clan du moment | petit clan : peut voler l'épave ; grand : abat plus vite mais partage | faible | armes lourdes, Mila, recycleur, Varga |
| **Largage public** (6.2) | escarmouche courte | ouvrir le T7 | 5 (règle T7 existante) | 3 à 6 / jour | faible | 1 actif à la fois | tous | nulle | airstrike |
| **Boss du monde annoncé** (6.3) | PvE disputé | dégâts sur le boss | 60 partagés, plein tarif 1× par boss / clan / jour, ensuite 20 % | 1 à 2 / jour par boss | moyen (aujourd'hui fort) | rendement décroissant ; boss invoqués sans points | petit : peut contester l'accès | inchangée | donjons |
| **Zone chaude** (6.4) | conflit court | temps tenu seul dans le cercle | events ×0,3 : 30 / 18 / 12 / 6 + 1,5 | 1 / jour | moyen | participation après 60 s et un combat ; 4 clans min. | petit : peut tenir par la ruse | faible | vagues de zombies, T7 |
| **Cargaison sensible** (6.5) | transport risqué | encaisser à une extraction | 15 PVE + 2 000 hérals au porteur | 1 à 2 / jour | moyen | anti-relais ; objet unique | solo possible (hérals) | nulle | extractions, carte |
| **Capture contestée** (6.11) | conflit territorial | zone tenue ≥ 2 h par un autre clan | 4 × valeur (8 à 20) | 3 / clan / jour max | moyen | 1× / 48 h pour la même paire de clans | petit : reprend quand le défenseur est absent | nulle | territoires, Varga q.32 |
| **Prime sur les meneurs** (6.10) | opposition aux leaders | tuer un membre du top 3 | PVP ×1,5 ; raid +5 % | continu | faible | protections PVP existantes | avantage les petits | nulle | classement, Purge |
| **Oil rig** (6.7) | fin de partie | ouvrir le T7 de la plateforme | **30** (au lieu de 60) | selon recharge | faible | 1× par recharge | grand clan avantagé, solo peut voler | à mesurer | salles, cargaison |
| Quêtes de clan (corrigées) | régularité | sans AFK, sur la carte | 5 (inchangé) | 3 / jour, 2 / semaine | aujourd'hui fort | temps compté seulement actif sur la carte | neutre | nulle | — |
| **Plafond par clan** (règle) | égalité petits / grands | toutes les petites actions | 150 / clan / jour en plus des 50 / joueur | — | — | — | aide les petits | nulle | — |

**Ce qu'il ne faut pas ajouter** : des points pour la **tenue passive** d'un territoire ou le **temps de jeu** (AFK, gros clans
qui accumulent), des points pour **défendre** (décision déjà prise : non), un « event » qui donne la grille pleine (100 points)
tous les jours (il écraserait le ClashWar).

### 7.4 ClashWar : petites améliorations

- **Finale de saison** : le dernier ClashWar de la saison (pendant les 7 jours figés) compte ×3 au lieu de ×2 [à valider] :
  un vrai rendez-vous de fin.
- **Lien avec la prime** : le clan vainqueur du dernier ClashWar porte la prime (6.10) jusqu'au suivant.
- **Format 1v1 et 2v2** déjà en rotation : les petits clans peuvent y gagner ; garder le minimum de 4 clans pour les points.
- **Agenda** : afficher le prochain ClashWar dans l'agenda du Directeur (6.1) ; pas d'autre événement de la carte pendant le
  tournoi.

---

## 8. Classement de toutes les propositions

| Proposition | Impact | Complexité | Priorité |
|---|---|---|---|
| Corrections du barème (boss, AFK, distance, plafond par clan, salles) | élevé | faible | immédiate |
| Directeur d'événements (6.1) | élevé | moyenne | immédiate |
| Largage public disputé (6.2) | moyen | faible | immédiate |
| Boss du monde annoncés (6.3) | élevé | faible | immédiate |
| Zone chaude (6.4) | élevé | moyenne | immédiate |
| Rendus et son de l'hélicoptère | — (préparation) | faible | immédiate |
| Hélicoptère militaire abattu (5) | élevé | élevée | prochaine mise à jour |
| Cargaison sensible (6.5) | élevé | moyenne | prochaine mise à jour |
| Capture contestée (6.11) | moyen | faible | prochaine mise à jour |
| Prime sur les meneurs (6.10) | moyen | faible | prochaine mise à jour |
| Radio militaire (6.6) | moyen | faible | prochaine mise à jour |
| Finale ClashWar ×3 (7.4) | moyen | faible | prochaine mise à jour |
| Oil rig V1 (6.7) | moyen | moyenne | plus tard |
| Oil rig V2 avec gardes armés | moyen | élevée | plus tard |
| Train militaire (6.8) | moyen | élevée | plus tard |
| Maladies (6.9) | moyen | moyenne | plus tard |

---

## 9. Feuille de route

### Priorité 1 — fort impact, coût raisonnable

| Chantier | Pourquoi | Prérequis |
|---|---|---|
| Corriger le barème de saison et les abus (boss 60 + rendement décroissant, quêtes de clan sans AFK, distance hors véhicule, plafond par clan, salles à 50 % au 2e passage, zone « test » retirée) | sans ça, toute nouvelle source est écrasée et les gros clans accumulent en silence | décision des chiffres par les propriétaires ; c'est surtout du réglage et quelques lignes |
| Directeur d'événements | le socle de tous les événements et de l'agenda qui fait revenir | aucun |
| Largage public | gain rapide, tout existe | Directeur ; points de largage |
| Boss du monde annoncés | rend les boss disputés au lieu de farmés | Directeur ; nouvelle recharge des boss |
| Zone chaude | conflit court pour la catégorie events entre deux ClashWar | Directeur ; reprise du code des captures |
| Rendus de l'hélicoptère, de l'épave, des 6 pièces ; son du rotor | la règle 6 impose de valider le visuel avant le code | aucun |

### Priorité 2 — structurant

| Chantier | Pourquoi | Prérequis |
|---|---|---|
| Hélicoptère militaire complet | l'événement phare, valorise les armes lourdes et les briques des frappes aériennes | rendus validés ; Directeur ; outil des points de crash ; essai des balles TaCZ sur l'entité |
| Cargaison sensible | le meilleur PvP naturel, relie événements et extractions | Directeur ; une source de mallette (épave, zone chaude) |
| Capture contestée + prime sur les meneurs | opposition aux gros clans, territoires vivants | barème corrigé |
| Radio militaire | information et exploration, objets geckolib enfin utiles | Directeur |
| Finale ClashWar ×3 | rendez-vous de fin de saison | aucun |

### Priorité 3 — ambitieux

| Chantier | Pourquoi | Prérequis |
|---|---|---|
| Oil rig (V1 zombies, puis V2 gardes armés) | fin de partie pour les clans organisés | zombie maison (étape 4) ; pour V2, un système de PNJ tireurs mesuré au banc de charge |
| Train militaire | utilise un objet unique du serveur | loot dans le train (prévu, pas fait) ; rendus du wagon |
| Maladies | identité DayZ | savoir ce que font les effets geckolib (essai) |

---

## 10. Les listes demandées

### Les 5 meilleures idées
1. **Hélicoptère militaire abattu** — spectaculaire, visible de loin, travail d'équipe récompensé, armes lourdes utiles.
2. **Directeur d'événements et agenda** — rendez-vous quotidiens, porte tout le reste.
3. **Rééquilibrage de la saison** — boss, AFK, plafond par clan, capture contestée, prime sur les meneurs.
4. **Cargaison sensible** — un objet de valeur à transporter : le porteur devient une cible.
5. **Zone chaude** — conflit court, lisible, ouvert aux petits clans, presque tout le code existe.

### Les 3 qui font le plus revenir
1. **L'agenda du Directeur** (« ce soir 21 h : hélicoptère ») — un rendez-vous chaque soir.
2. **L'hélicoptère** — rare, imprévisible dans son trajet, des pièces rares à collectionner (boîte noire 10 %).
3. **La saison rééquilibrée** avec prime et classement visible — chaque soirée peut faire bouger les places.

### Les 3 qui créent le plus de PvP naturel
1. **Crash de l'hélicoptère** — fumée noire, message par secteur, 2 à 3 min d'attente exposée autour de l'épave.
2. **Cargaison sensible** — porteur repérable toutes les minutes jusqu'à l'extraction.
3. **Zone chaude** — un cercle à tenir, contesté par tout le monde.

### Les 3 qui valorisent le mieux les mods présents
1. **Hélicoptère** — TaCZ (RPG-7, lance-grenades, .50, minigun), sons de ywzj (tirs, impacts, alerte de verrouillage),
   nos frappes aériennes (trajectoires, feu, fumée, explosions).
2. **Largage public** — l'avion cargo et la caisse en parachute de nos frappes aériennes, aujourd'hui réservés au staff et
   aux clans.
3. **Radio militaire** — la radio, le GPS et les piles de geckolib222, aujourd'hui sans aucune fonction.

### Les 3 idées à éviter
1. **Véhicules et hélicoptères armés pilotés par les joueurs** (version complète de ywzj) : licence « tous droits
   réservés », équilibrage impossible face à l'infanterie, coût serveur et joueurs, triche facile ; le moteur ywzj n'a
   aucune IA, donc aucun hélicoptère ennemi « gratuit » non plus.
2. **Points de saison pour la tenue passive ou le temps de jeu** : favorise l'AFK et les gros clans qui accumulent sans
   opposition (c'est exactement la faille actuelle des quêtes de clan).
3. **Une nouvelle monnaie d'événement** (jetons, insignes à échanger) : brouille l'économie (déjà hérals + PB + capsules
   du casino), crée un marché parallèle ; passer plutôt par les hérals, les composants et les fragments existants.

---

## 11. Ce qui reste incertain (à vérifier en essai)

| Point | Pourquoi c'est incertain | Comment vérifier |
|---|---|---|
| Les balles TaCZ touchent-elles une entité non vivante en mouvement, morceau par morceau ? | indice fort (classe `MaybeMultipartEntity`, véhicules ywzj touchés) mais pas d'essai | prototype sur le serveur de test ; sinon méthode du train |
| Dégâts des armes TaCZ contre une entité (pas un joueur) | `armes.json` ne règle que les dégâts contre les joueurs | mesurer chaque famille sur une cible de test |
| Les 5 points « conteneur_t7 » pour la caisse en parachute | vérifié : seuls les coffres T7 de HeraliZ Loot les donnent, pas la caisse des frappes aériennes | à brancher (fiche 6.2) |
| Les effets de maladie de geckolib222 font-ils déjà quelque chose ? | code de l'auteur non décompilé | essai en jeu (`/effect`) |
| La quête « distance » avance-t-elle dans le train ? | dépend de la façon dont le train déplace le joueur | essai |
| La zone « test » de `zones.json` est-elle sur la carte ? | coordonnées non comparées à la carte | `/territoire` en jeu |
| Durées et chiffres (PV de l'hélico, dégâts, prix des pièces, points) | propositions de départ | bêta, journal de la saison |

Rien de ce document n'a été développé ni testé en jeu.
