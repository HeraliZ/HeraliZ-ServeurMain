# Audit global du 21/11 — zone a3 : les bases (CRac)

Relu : `modules/bases` (mod `crac`) : pièces de construction et leurs blocs (`StructureBlock`, `StructureBlockEntity`,
formes gardées, portes et sas), armoire à outils (claim, entretien, usure, écran, déplacement au marteau), bases des clans
(`BasesClans`, miroir des clans), cœur de clan (pose, prise, déplacement), Purge et dégâts des raids (C4, charge
explosive, échelles de raid), `/home`, `/sethome`, `/spawn`, `/rtp`, tutoriel des bases (monde `crac:tuto`, parcelles,
kit, inventaire mis de côté, étapes de la cabane), zones interdites, carrières, effacements (`WipeApi`, `/base effacer`,
remise à zéro), réglages envoyés aux joueurs, règles de pose. Aussi : les appels des autres modules vers CRac par
réflexion (Quêtes, Clans, Boutique, Monde, Loot, Événements, Armes, Anticheat, Banc).

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, essai hors du jeu de la nouvelle tenue des claims
(petit programme Java qui rejoue ses règles), puis la fabrication et les contrôles sur GitHub. Version : **CRac
1.38.9-beta** (le launcher donnera le nouveau jar aux joueurs ; aucun canal réseau ne change : un jeu en 1.38.8 se
connecte toujours).

En bref : **10 points corrigés** (1 important, 4 moyens, 5 petits), 7 remarques ou propositions (rien de changé pour
elles). Pas de plantage trouvé, pas de duplication d'objets ou de matériaux, pas de paquet du joueur accepté sans contrôle.
Côté lenteurs, CRac reste petit (0,4 ms par tick au banc à 150 bots du 21/11) : aucun gros coût trouvé à chaque tick.

---

## Corrigé

Commits **a47ec976** (points 1 à 9) et **8cb1e198** (point 10), CRac 1.38.9-beta.

### 1. (Important) Tutoriel des bases fait en même temps par deux membres d'un même clan

En simple : deux joueurs du même clan qui faisaient le tutoriel en même temps (fréquent à l'ouverture : on crée le clan
entre amis, puis chacun va voir Varga) posaient chacun l'armoire de sa cabane d'essai. CRac ne gardait qu'**une armoire
par clan et par monde** : la deuxième posée « effaçait » la première de sa liste. Pour le joueur dont l'armoire avait été
posée en premier :
- sa cabane n'était plus vue comme une base : **la caisse en métal (dernière étape) était refusée** (« un objet de base ne
  se pose que dans la base de son clan ») : **tutoriel bloqué** (seule sortie : « Quitter le tuto » ou le staff) ;
- son armoire n'était plus enregistrée (perdue au redémarrage suivant) ;
- à la fin (ou en quittant), **sa cabane n'était pas effacée** : la parcelle redevenait libre avec la vieille cabane et
  son armoire dessus, et le prochain nouveau joueur arrivait sur cette parcelle encombrée.

Corrigé : les claims sont gardés **par armoire** ; un clan peut en avoir plusieurs dans le monde du tutoriel (une par
cabane d'essai), toujours une seule sur la carte (règle « une base par clan » inchangée).
- `modules/bases/src/serveur/java/fr/heraliz/crac/claim/ClaimData.java` : `all()` (l.95), l'index de `getAt` (l.145) et la
  sauvegarde (l.236) prennent toutes les armoires (`byAnchor`) ; `add` (l.152), `removeByAnchor` (l.166) et
  `removeByGroup` retirent seulement l'armoire visée, le groupe passe à une autre de ses armoires s'il en a une
  (`oublierDuGroupe`, l.194). Sur la carte (une armoire par clan), rien ne change : mêmes claims, même fichier.
- Les effacements retirent **l'armoire visée**, jamais « celle du groupe » (sinon la fin du tutoriel d'un joueur cassait
  l'armoire de la cabane de l'autre, avec son stock) : `bases/EffacerBase.java` l.146-151 (fin du tutoriel, `/wipe
  <joueur>`, `/base effacer`), `bases/WipeBases.java` l.81-84 (remise à zéro).
- Essai hors du jeu (copie des règles de `ClaimData`) : deux cabanes d'un même clan, fin de l'une puis de l'autre, retrait
  par groupe, remplacement à la même place, carte avec une seule armoire : tout passe.

### 2. (Moyen) Clan dissous pendant le tutoriel d'un de ses membres

En simple : si le clan était dissous pendant qu'un membre faisait son tutoriel, son armoire d'essai devait rester (passée
à un groupe à son nom) ; en fait elle disparaissait **toujours** (la recherche de sa cabane se faisait après avoir défait
le lien entre la cabane et l'armoire) : il restait bloqué à l'étape de l'armoire, sans armoire.
Corrigé : `heraliz/BasesClans.java` l.280 (`buildingUnder` : la cabane sous l'armoire), et toutes les armoires du clan de
chaque monde sont traitées (l.245-269 ; avant : une seule).

### 3. (Moyen) L'armoire d'essai mettait fin aux 24 h de la base raidée du clan

En simple : quand l'armoire d'une base est détruite pendant un raid, la base reste 24 h au clan. Si, pendant ces 24 h, un
membre du clan (une nouvelle recrue) posait l'armoire de sa cabane d'essai au tutoriel, la réservation de la vraie base
s'arrêtait : n'importe qui pouvait la prendre. Corrigé : `heraliz/ArmoiresPerdues.java` l.214-218 (le monde du tutoriel
ne compte pas).

### 4. (Moyen) Messages d'entretien envoyés à tout le clan pour une cabane d'essai

En simple : entre la pose de son armoire d'essai et l'étape de l'entretien, tous les membres connectés du clan recevaient
dans le chat « L'armoire de ta base n'a plus d'entretien… », puis « L'entretien de ta base est de nouveau payé » ; avec
deux cabanes d'essai (point 1), ces messages auraient alterné chaque minute. Corrigé : plus de message d'entretien pour une
cabane du tutoriel (Varga guide déjà l'entretien) : `claim/ClaimMaintenanceManager.java` l.340-345. L'entretien lui-même
se calcule comme avant.

### 5. (Moyen) Échelle de raid abîmée de loin (jeu modifié)

En simple : Forge prévient les mods d'un clic gauche sur un bloc **avant** de vérifier la distance ; un jeu modifié pouvait
donc abîmer, puis casser, une échelle de raid de n'importe où (4 coups par seconde). Corrigé (commit 8cb1e198) : le coup
ne compte qu'à portée de main (portée du joueur + 1 bloc) : `placable/EchelleServeur.java` l.108-113. Rien ne change pour
un joueur normal.

### 6. (Petit) Remise à zéro : un bloc de la carte au-dessus d'une armoire disparue

`/wipe serveur` retirait le bloc au-dessus de l'emplacement de chaque armoire, quel qu'il soit (même un bloc de la carte
si l'armoire n'était plus là). Corrigé : seulement la case du haut de l'armoire, et l'armoire seulement si c'en est une
(`bases/WipeBases.java` l.73-80).

### 7. (Petit) Tronçons « vraiment prêts » à trois endroits oubliés le 19/11

Le 19/11 (lenteurs, point C4), CRac est passé aux tronçons complets (`Troncons.charge`) pour ne jamais faire attendre le
fil du serveur sur un tronçon encore en lecture. Trois lectures de blocs restaient sur l'ancien test (`isLoaded`) :
cases touchées par une explosion (`raid/BlastDamage.java` l.104), repousse des carrières (`bases/CarrieresServeur.java`
l.143), textes « Base protégée » (`bases/Protections.java` l.355). Rare (bord de la vue), mais c'est un gel possible du
serveur : même test partout maintenant.

### 8. (Petit) Explosions des grenades : plus de lecture inutile

À chaque explosion d'une grenade de Tactique, CRac regardait si c'était un « C4 de Tactique » (liste
`c4ThrowableIds`, vide depuis le 19/11), au besoin en enregistrant toute la grenade en NBT. Liste vide : plus rien n'est
lu (`heraliz/HeralizC4.java` l.44-49).

### 9. (Petit) Dépendance au noyau déclarée aussi au serveur

`META-INF/mods.toml` déclarait le noyau « côté joueur seulement », version 1.0.19 ou plus, alors que la partie serveur de
CRac s'en sert (tronçons prêts, arrivées, écritures en fond, matchs : noyau 1.1.5 du 19/11). Maintenant : les deux côtés,
1.1.5 ou plus (le serveur a la 1.1.10). Sans noyau, Forge le dirait clairement au démarrage au lieu d'une erreur Java plus
tard. Aucun effet aujourd'hui.

### 10. Version

`modules/bases/gradle.properties` : 1.38.9-beta.

---

## À décider par le propriétaire (rien n'est changé)

1. **`/base effacer <chef>` (staff) et le tutoriel** : la commande efface la base du clan sur la carte **et** « la »
   cabane d'essai du clan au tutoriel, qui peut être celle d'un autre membre en train de faire son tutoriel
   (`bases/EffacerBase.java` l.110). Proposition : n'effacer au tutoriel que les cabanes du joueur visé. Risque faible,
   cas rare (commande du staff).
2. **Échelles de raid et murs** : depuis le point 5, la distance est vérifiée, pas la ligne de vue : un jeu modifié peut
   encore frapper, de l'intérieur de la base, l'échelle d'un pillard collée de l'autre côté du mur. Proposition : vérifier
   aussi la vue (comme pour l'ouverture des coffres). Risque : refus à tort si une échelle partage une case avec un
   demi-mur ; à essayer en jeu avant.
3. **Zombies qui attaquent les bases** (`[zombies] attackBuilds` de `crac-server.toml`, coupé aujourd'hui) : s'il est un
   jour remis, `mob/ZombieSiegeManager.java` l.90 relit toutes les créatures de la carte et toutes les pièces des bases
   deux fois par seconde : un gros coût à 150 joueurs. À ne pas rallumer sans le réécrire d'abord.
4. **`/home` et `/rtp`** ne revérifient pas le combat au départ (seul `/spawn` le fait depuis le 19/11). Au spawn il n'y a
   pas de PvP, donc presque impossible ; à ajouter seulement pour l'uniformité (`bases/Homes.java` `partir`,
   `bases/Deplacements.java` `partirZone`).
5. **Agrandir l'armoire au métal renforcé puis la déplacer** (dans les 15 min) : le métal de l'agrandissement n'est pas
   rendu (`block/ClaimBlockLogique.java` `tryUpgrade` ne note pas le palier payé). Sans effet aujourd'hui : avec la
   saison, la taille vient de l'arbre du clan et cet agrandissement est coupé (`ArbreBases.agrandissementParArbre`).
6. **Deux petites économies possibles, si un profil les montre un jour** : (a) une armoire, une caisse ou un coffre qui se
   charge n'importe où sur la carte fait refaire les formes gardées de toutes les cases de mur (`block/Adosse.java` l.101,
   une seule « version » pour tout le monde) ; une version par région garderait mieux ces formes. (b)
   `claim/ClaimManager.java` l.210 (`foreignBuildingIn`) parcourt toutes les pièces du monde ; appelé toutes les 5 s pour
   chaque clan dont l'agrandissement par l'arbre attend (bâtiment d'un autre clan dans la zone) : environ 0,2 ms chacun.
   L'index des pièces par case ferait le même travail sur quelques dizaines de pièces.
7. **Paquets sans limite de cadence** (sans danger aujourd'hui) : le choix « À la base » de l'écran de mort
   (`bases/Reapparition.java` l.52, seulement mort) et la recherche de l'onglet Accès de l'armoire
   (`bases/AccesBaseTraitement.java` l.95, déjà espacée par le jeu du joueur) n'ont pas de limite côté serveur ; chaque
   paquet coûte peu. Une limite (comme les autres paquets de CRac) se pose en quelques lignes si besoin.

---

## Vérifié sans problème

- **Paquets reçus des joueurs** (11) : chacun est repris sur le fil du serveur, refait ses contrôles et a sa limite de
  cadence : marteau, infos de pièce et pose de pièce (`build/net/PacketGuard` : joueur vivant, outil en main, distance,
  tronçon chargé, ligne de vue sur la pièce, clé de pièce bornée au décodage), ramassage (6 blocs, vue, droits du clan,
  raid, contenu vide), pose en aventure (visée refaite par le serveur), groupes et invitations (refusés pour les bases des
  clans), accès et entretien de l'armoire (6 blocs, droit « coffres »), réapparition (seulement mort ; tout revu à la
  réapparition). Tailles de listes bornées (`PacketLimits`).
- **Duplications** : stock de l'armoire (clic, Maj + clic, touches 1 à 9, glisser, entonnoirs : chaque mouvement passe par
  le compte du stock, rien ne sort par les entonnoirs), coffres par rareté et caisse en métal (deux cases, un seul
  inventaire), ramassage d'un objet posé (refusé s'il contient quelque chose), démolition (rien rendu hors du tutoriel ;
  au tutoriel, rendu marqué comme le kit), déplacement de l'armoire (stock et palier payé rendus, bloc retiré sans rien
  faire tomber), C4 raté ramassé (une fois), cœur de clan jeté (rendu, jamais en double), kit et inventaire du tutoriel
  (ordre d'écriture : dépôt sur le disque avant de vider le joueur, joueur enregistré avant d'effacer le dépôt), objets à
  vie des bases, matériel de la première base (fichier écrit avant le coffre du clan).
- **Sauvegardes** : toutes les données de CRac passent par `data/SafeSavedData` (fichier temporaire puis renommé, fichier
  illisible copié de côté et jamais écrasé, écriture en fond attendue à l'arrêt) ; inventaires du tutoriel écrits,
  synchronisés et relus avant d'être utilisés.
- **Chaque tick** : tous les systèmes sortent tout de suite quand ils n'ont rien à faire (prises du cœur, fins du
  tutoriel, départs `/home` `/spawn` `/rtp`, chantiers des parcelles, zombies coupés) ; l'entretien de chaque minute est
  étalé (2 000 unités par tick) ; l'aide au saut (`SautRebord`) ne lit rien loin des bases ; formes des cases de mur
  gardées ; aucune lecture de fichier sur le fil du serveur pendant le jeu (prix de la fiche `/base info` lus à côté).
- **Mixins** : cibles présentes dans Forge 47 / Minecraft 1.20.1 (`FireBlock.tick`, `getIgniteOdds`, `tryCatchFire` de
  Forge, `FlowingFluid.canSpreadTo`, `Player.getDimensions` / `getStandingEyeHeight`, `WorldDimensions.bake`,
  `Slot.mayPickup`) ; tous facultatifs côté serveur (`defaultRequire 0`).
- **Appels des autres modules vers CRac par réflexion** : classes et signatures présentes et inchangées —
  `api.WipeApi` (`toutEffacer`, `cabaneEssai`, `inventaireTutoriel`), `api.CoeurApi` (`clanAUnCoeur`,
  `priseSansPurgeHeures`), `api.SpawnCracApi.allerAuSpawnDefini`, `api.BaseCracApi` (`base`, `tpaRefuseeDansBase`),
  `api.BancApi` (`construire`, `effacer`, `pieces`), `api.VueApi.formeVue`, `bases.TutorielApi` (`etat`, `progression`,
  `lancer`, `etapes`, `joursProtection`, `ficheBase`, `sas`, `quitter`), `claim.ClaimManager` (`getAt`, `basePres`),
  `client.PurgeHud.hauteur`, et pour l'anti-ESP de Spartan `StructureBlockEntity.occupants` / `hasLegacyData`,
  `Occupant` (`occludes`, `kind`, `state`, `key`, `variant`), `PieceKind.hasDoor`, `Piece.STATE_SAS` / `INSERT_MASK` /
  `INSERT_SHIFT`, `PieceShapes.occlusion`. Le changement du point 1 ne touche aucune de ces signatures.
- **Commandes** : droits du staff sur chaque commande d'administration (`/base effacer` et `/cracbuild` op 4, `/tuto`,
  `/protection`, `/carriere`, `/spawn definir` op 2…) ; commandes des joueurs refusées en combat, à terre et en match.
- **Listes gardées en mémoire** : vidées au départ des joueurs ou à l'arrêt du serveur (cadences, choix de
  réapparition, avertissements, attentes du tutoriel, parcelles en chantier).
- **Affichage chez le joueur** : écran de l'armoire et points du cœur dessinés seulement de près (16 et 24 blocs), dans le
  champ de vision ; zones des armoires construites une fois et gardées dans la carte graphique ; HUD (raid, Purge,
  marteau, plan) sortis tout de suite quand il n'y a rien à montrer.

---

## À tester en jeu

Tutoriel (le point important ; deux comptes A et B **du même clan**, qui n'ont jamais fait le tutoriel, et un op) :
- [ ] A puis B lancent le tutoriel (Varga, « Faire ma base ») à quelques secondes d'écart. Chacun bâtit sa cabane et pose
      son armoire ; **A pose son armoire avant B**, puis A met les planches et **pose sa caisse en métal** : la caisse se
      pose (avant : refusée, tutoriel bloqué). A finit : sa cabane d'essai disparaît, il revient au spawn.
- [ ] B finit ensuite : sa cabane disparaît aussi ; son armoire n'avait pas bougé quand A a fini.
- [ ] Un troisième joueur C lance le tutoriel après eux : il arrive sur une parcelle vide (pas de vieille cabane).
- [ ] Pendant leurs tutoriels, les autres membres du clan connectés ne reçoivent **aucun** message « l'armoire de ta base
      n'a plus d'entretien » ni « l'entretien de ta base est de nouveau payé ».
- [ ] Redémarrage du serveur pendant que A et B sont tous deux à l'étape de l'entretien : après le retour, chacun retrouve
      sa cabane avec son armoire et peut finir.
- [ ] Variante : A et B en tutoriel, le chef du clan tape `/clan dissoudre` : chacun garde sa cabane
      et son armoire d'essai et peut finir (avant : l'armoire disparaissait).
- [ ] Un seul joueur dans le tutoriel : tout comme avant (étapes, caisse, fin, « Quitter le tuto »).

Bases sur la carte (rien ne doit changer) :
- [ ] Poser l'armoire de son clan, la déplacer au marteau dans les 15 min, la reposer : même zone, stock et métal rendus.
- [ ] Une base raidée dont l'armoire saute : 24 h réservée au clan ; une recrue qui fait le tutoriel pendant ce temps ne
      met pas fin à la réservation ; le clan repose son armoire sur la base : récupérée.
- [ ] C4 et charge explosive sur une base : dégâts aux pièces et aux objets posés comme avant ; une grenade près d'une base
      ne fait rien aux pièces (comme avant).
- [ ] Échelle de raid : à la main, elle se casse en une dizaine de coups (comme avant) ; au C4, d'un coup.
- [ ] `/wipe serveur` sur le serveur d'essai : bases, armoires et cœurs partent, la carte autour reste intacte.
- [ ] Journal du serveur au démarrage : aucune erreur de dépendance pour `crac` ; `/diag etat` : rien de nouveau.

Pas testé : tout en jeu ; seule la fabrication et les contrôles de GitHub sont faits sur ces changements.
