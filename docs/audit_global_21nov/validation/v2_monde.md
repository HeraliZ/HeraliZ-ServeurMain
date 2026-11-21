# Validation de l'audit du 21/11 — zone v2 : monde et zombies

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la zone du compte rendu `../a2_monde.md`, sans se fier à ses « vérifié sans problème ». Relu : `modules/monde`
côté serveur et commun (zombies : peuplement, plafond, IA, chemins, bruits, attaques ; train : voyageurs, balles, bagages,
voie ; ascenseur ; cycle jour / nuit ; monde du spawn et sa protection ; borne de départ ; cinématique d'arrivée ;
coffres personnels ; `/inspecter` ; `/suicide` ; distance de vue), les sous-mods `heraliz_carte`, `heraliz_corps`,
`heraliz_ping`, `heraliz_usure`, `heraliz_vehicules`, `heraliz_decors` (brume, zones de gaz / radiation / froid, lampes),
`heraliz_extraction`, `airstrike` (frappes, largages, hélicoptère, roquettes, épave), le `mods.toml`, les Mixins sur les
mods des autres auteurs, les appels par réflexion dans les deux sens ; côté joueur, un survol des rendus et effets qui
tournent à chaque image (brouillard, nuages, lampes rouges).

**Rien n'a été testé en jeu** (pas de jeu ici). Fait hors du jeu, sur ce PC :
- relecture ligne à ligne des 4 commits du jour qui touchent la zone ;
- `outils/verif_reflexion.py monde` : **0 erreur** (69 appels vérifiés) ; les deux noms SRG côté serveur qu'il ne pouvait
  pas confirmer (`PlayerList.m_6765_`, `ServerGamePacketListenerImpl.f_9737_`) sont confirmés ici dans la table Spigot →
  SRG du Forge 47.3.12 livré avec Mohist 916 (`mappings/spigot2srg.srg` : `PlayerList.b(EntityPlayer)` = `m_6765_`,
  l'enregistrement du joueur ; `PlayerConnection.H` = `f_9737_`, le compteur « en l'air ») ;
- cibles des Mixins relues avec `javap` dans les jars du serveur (`serveur/mods-tiers/`) : toutes présentes ;
- l'essai hors du jeu du pré-tri des balles du train refait de mon côté (javac, mêmes réseaux que le jar) : même résultat ;
- `outils/verifier.py`, partie sources : synchronisation 0 erreur, langues 0 erreur.
La fabrication et les contrôles des jars se font sur GitHub (« Contrôle de branche »).

En bref : les **4 changements du jour** font ce qu'ils disent, **sans régression trouvée** ; **1 petit défaut corrigé**
(même famille que les corrections du jour : un tronçon encore en lecture pouvait faire attendre le serveur pendant la
recherche du point de crash de l'hélicoptère), **Monde 1.3.12** ; **1 proposition** nouvelle, petite ; les propositions
de la première relecture que le propriétaire n'a pas choisies restent des propositions (n° 1, 3, 5, 7, 8 de `a2_monde.md`).
Aucune duplication d'objet, aucun trou dans les droits, aucun paquet de joueur accepté sans contrôle trouvé. Aucun canal
réseau ne change : un jeu de joueur d'avant se connecte toujours (le launcher donnera le nouveau jar comme d'habitude).

---

## Changements du jour validés

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `e902c9892` | Monde 1.3.9 : une roquette de l'hélicoptère s'arrête aussi devant un tronçon pas encore prêt | **OK** |
| `b637837a6` | Monde 1.3.10 : six endroits demandent « le tronçon est-il complet ? » au lieu de « est-il chargé ? » | **OK** |
| `f324509e4` | Liens entre les mods : `mods.toml` du monde (noyau des deux côtés pour l'extraction, `heralisz_utils` 2.26 et plus) | **OK** |
| `3f1e792c0` | Monde 1.3.11 : date du fichier de l'usure relevée à part ; pré-tri des balles loin du train | **OK** |

**`e902c9892` — roquettes** (`modules/monde/src/serveur/java/fr/heraliz/airstrike/helico/Roquettes.java` l. 135-142).
Avant le rayon de vol du tick, tous les tronçons entre le point de départ et le point d'arrivée du tick doivent être
complets (`Troncons.zone`, 1 à 4 tronçons : une roquette fait 2 blocs par tick avec le réglage par défaut) ; le
contrôle « chargé » d'avant reste (il refuse aussi une roquette sortie par le haut ou le bas du monde). Au plus 32 roquettes en vol : au plus 128 coups d'œil
par tick, sans rien charger. Rien ne change près des joueurs (leurs tronçons sont complets) ; une roquette qui manque
sa cible et file au loin disparaît au bord de la zone prête, comme avant au bord de la zone chargée. Le calcul de
l'explosion (part du corps visible) ne regarde que quelques blocs autour d'un joueur : tronçons prêts.

**`b637837a6` — les six endroits**, relus un par un, avec le comportement au démarrage et quand le tronçon n'est pas prêt :
- *Hélicoptère, relevé du sol* (`entity/HelicopterServeur.java` l. 406-421) : un point dont le tronçon n'est pas complet
  ne compte pas (comme un tronçon absent avant) ; si aucun ne l'est, il garde le sol d'avant (`solVise`), au lieu de
  descendre vers le bas du monde. Au départ, `solVise` est relevé quand la veille a vu le tronçon d'arrivée complet et
  actif (`helico/Veille.java` l. 120, puis l. 257 d'`HelicopterServeur`) : jamais zéro par erreur. Les tronçons vers
  lesquels il vole reçoivent son ticket un tronçon à l'avance (`tenirTicket`, l. 893-925) : le point « juste devant » est
  presque toujours prêt ; seul un tronçon qui vient d'être demandé est sauté pendant un relevé (il est repris au relevé
  suivant, 16 blocs plus loin). Montée à 0,25 bloc par tick au plus : même marge qu'avant pour les collines.
- *Frappes aériennes, relief sous l'avion* (`strike/StrikePlanner.java` l. 36-43) : tronçon pas prêt = hauteur de secours,
  comme un tronçon absent. Pour les jumelles, le tronçon de la cible est déjà complet (contrôle suivant) ; pour le largage
  public, la cible est un sol déjà relevé par le module des événements (sa hauteur sert de secours). Le repère de la cible
  (`StrikeManager.java` l. 94) passe par la même fonction : sans attente.
- *Ping posé depuis la carte M* (`ping/server/PingService.java` l. 217-222) : coordonnées du joueur arrondies, finies et
  dans la bordure (l. 212-216) ; hauteur du sol seulement si le tronçon est complet, sinon celle du joueur. Même cadence
  (seau à jetons) qu'avant.
- *Jumelles* (`strike/DesignatorLogic.java` l. 163-168) : coordonnées finies vérifiées avant (`serveur/PartieServeur.java`
  l. 45), portée, puis tronçon de la cible complet, sinon « hors de portée » (normal tout au bord de la vue).
- *Train, quai de la gare* (`train/VoyageursServeur.java` l. 261-263 et 275) : tronçon pas prêt = le milieu du quai, le
  point déjà prévu pour un tronçon absent (sûr par construction) ; près du joueur (descente par une porte, heurt), les
  tronçons sont complets : rien ne change.
- *Lampes rallumées* (`decors/serveur/ClignotementServeur.java` l. 66-67) : la liste est remplie pendant le chargement du
  tronçon (évènement `ChunkEvent.Load` de Forge, envoyé juste avant que le tronçon soit déclaré complet, sur le fil du
  serveur) et lue à la fin du tick : le tronçon est alors complet ; seul un tronçon déchargé entre-temps est sauté (il
  sera relu à son prochain chargement). Aucune lampe oubliée dans le cas normal.

**`f324509e4` — `mods.toml`** (`modules/monde/src/commun/resources/META-INF/mods.toml` l. 55, 93, 149, 188, 220 et
l. 290-297). `heralisz_utils` est en 2.26.1 (l. 10) : la plage « 2.26 et plus » l'accepte. Le noyau devient obligatoire
aussi au serveur pour l'extraction : il y est (Noyau 1.1.11) ; le noyau ne dépend d'aucun mod du monde : aucune boucle
d'ordre de chargement (Forge range les jars, et ce jar venait déjà après le noyau à cause de `heraliz_usure`).

**`3f1e792c0` — usure et train**.
- *Usure* (`usure/ReglesServeur.java` l. 51-95) : déroulé relu dans chaque cas. Démarrage : la date est relevée une
  première fois au démarrage, puis le fichier est lu (ou créé). Fichier complété à la lecture, ou réécrit par l'écran
  `/usure config` : la date connue est celle du disque, le fil à part se met à jour en 5 s au plus ; entre-temps les
  contrôles voient une date différente mais ne relisent jamais le fichier (la date relue au disque juste avant ne
  correspond pas). Changement fait à la main : relu après deux contrôles de suite avec la même date ET la même date au
  disque (jamais un fichier à moitié écrit), en 2 à 9 s. Fichier supprimé à la main : réglages gardés, comme avant.
  Fil à part : table partagée prévue pour ça (`ConcurrentHashMap`), fil « démon » qui ne bloque pas l'arrêt.
- *Train* (`train/BallesTrain.java` l. 66-73, 158-160, 176-177) : le milieu de chaque rame est relevé une fois par tick ;
  une balle à plus de 104,8 blocs de ce milieu est écartée. Le calcul d'origine ne retient que les balles à moins de
  88,8 blocs du milieu de la rame à l'instant testé (au plus 0,95 s plus tôt ou 0,05 s plus tard) ; essai hors du jeu
  refait de mon côté sur les trois réseaux du jar (`reseau`, `reseau_v2`, `reseau_v3`), tout l'horaire, tick par tick :
  le milieu bouge d'au plus 14,25 blocs pendant ce temps, il reste 1,75 bloc de marge. Même résultat qu'avant pour
  chaque balle. L'horaire est toujours sur la grille des ticks (le décalage de `/train` est en ticks) : l'essai couvre
  tous les cas.

---

## Corrigé

Commit **`1b7f275eb`**, **Monde 1.3.12** (seul le jar du serveur change vraiment ; aucun canal réseau ne change).

### 1. (Faible) Point de crash de l'hélicoptère : un tronçon en lecture pouvait faire attendre le serveur

En simple : quand l'hélicoptère de l'événement est abattu, le serveur cherche un sol où il peut s'écraser (jusqu'à
3 000 points essayés autour de lui), et il fait le même contrôle tous les 16 blocs de son vol (pour la boîte noire). Pour
chaque point, il regarde un carré de 25 x 25 blocs (hauteurs, blocs du sol). Il ne vérifiait que les tronçons des 4 coins
de ce carré. Mais un carré à cheval sur 3 tronçons en a un au milieu que les coins ne voient pas, et c'est justement là
que tombent les premiers relevés (le centre du carré). Si ce tronçon du milieu était encore en lecture sur le disque, le
serveur attendait la fin de sa lecture : un petit gel pour tout le monde. C'est la même famille que les six corrections du
jour (hélicoptère, frappes, ping, train, jumelles, lampes). Maintenant, tous les tronçons du carré doivent être complets ;
sinon le point est refusé (comme un point au bord de la carte chargée) et la recherche passe au suivant.

Cas rare (il faut un tronçon en lecture entouré de tronçons prêts : par exemple un joueur qui arrive par téléportation
entre deux groupes d'autres joueurs au moment où l'hélicoptère tombe). Rien ne change pour les joueurs : le point de crash
est choisi avec les mêmes règles. Coût : 4 à 9 coups d'œil au lieu de 4 par point essayé, sans rien charger.

Pour les développeurs : `modules/monde/src/serveur/java/fr/heraliz/airstrike/helico/PointCrash.java` l. 118-124 (`sol`) :
`Troncons.zone(level, x - 12, z - 12, x + 12, z + 12)` à la place des 4 `charge(...)` des coins ; tout ce que `sol` lit est
dans ce carré (centre et coins à 4 blocs, anneau à 12 blocs ; `procheDUneBase` ne lit que la liste des bases de CRac).

---

## À décider par le propriétaire

Les propositions de la première relecture que vous n'avez pas choisies (`a2_monde.md` n° 1, 3, 5, 7 et 8) restent
valables telles quelles ; la n° 2 a été faite par le coordinateur (`bb4da7c1`, vérifié : la caisse de largage posée en bloc
est reconnue aussi côté serveur, `modules/armes/.../ObjetsAVieServeur.java` l. 137-152). Une seule proposition nouvelle :

1. **Borne de départ très chargée : chargement d'un tronçon par le serveur lui-même (faible, seulement sous forte
   charge).** Quand un joueur part du spawn, la borne tire un point d'arrivée et demande son tronçon à Minecraft, qui le
   lit en tâche de fond ; si ce tronçon n'est toujours pas prêt au bout de 2 s, le point est contrôlé quand même, et le
   serveur lit alors le tronçon lui-même (de 50 à 300 ms de gel pour tout le monde). Ça n'arrive que quand le serveur est
   déjà très chargé (une lecture normale prend bien moins de 2 s), donc au pire moment, par exemple 100 joueurs qui
   partent du spawn juste après une remise à zéro. Choix fait le 01/11 (« passé ce délai, il est contrôlé comme avant »).
   Possibilité : abandonner ce point et en tirer un autre (60 essais par départ). Effet : jamais de gel de ce côté ; en
   échange, sous très forte charge, l'attente « Recherche d'un point d'arrivée sûr… » peut durer quelques secondes de
   plus. Petit changement, à essayer en jeu. Fichiers : `modules/monde/src/serveur/java/fr/heralisz/utils/spawn/
   DepartServeur.java` l. 287-310 (attente de 40 ticks, `ATTENTE_TIRAGE`), l. 549 et 575 (`carte.getChunk`). Les commandes du
   staff qui envoient sans délai (`/depart essai`, `/depart joueur`) cherchent tout d'un coup et chargent eux aussi : sans
   importance (staff, rare). **Fait le soir du 21/11**, à la demande du propriétaire : voir « Fin du 21/11 » plus bas.

Remarques sans décision (rien à faire, notées pour la suite) :
- `CoffresPersonnels.java` l. 491-493 : si l'enregistrement immédiat (joueur + coffres) à la fermeture d'une planque
  échouait, rien ne serait écrit au journal (l'enregistrement normal du monde le fait ensuite). Le nom de la méthode lue par
  réflexion est maintenant confirmé exact (voir plus haut) ; une ligne au journal aiderait seulement à voir une panne.
- `/train voie …` (staff) lit encore la voie avec « le tronçon est-il chargé ? » (`train/VoieServeur.java` l. 358 et
  406) : commande du staff, rare, près du staff : pas la peine d'y toucher.
- Le mod principal `heralisz_utils` ne déclare pas le noyau dans le `mods.toml`, alors que son code s'en sert (tronçons,
  combat, écritures en fond) : sans effet, `heraliz_usure` (même jar) le déclare obligatoire, et tout part ensemble.

---

## Vérifié sans problème

**Paquets envoyés par le jeu des joueurs** (chacun relu ; tous traités sur le fil du serveur ; sens « joueur → serveur »
déclaré pour chacun : un paquet prévu pour le joueur ne peut pas être envoyé au serveur) :
- Train : `Marche` (à bord, même voiture ou la voisine aux extrémités, pas de 1,2 bloc et réserve par tick du serveur,
  valeurs non finies refusées, saut borné à la courbe d'un vrai saut ; `train/VoyageursServeur.java` l. 59-105),
  `Bagage` (voiture et place lues côté serveur, portée, bagage actif de la période ; `train/BagagesServeur.java` l. 59-90),
  `Descente` ignoré.
- Pings : `PingC2S` (cadence, vivant et pas spectateur, entité visible et à portée, ligne de vue qui ne lit que des
  tronçons complets, type borné), `AnnulerC2S` (seulement ses propres pings), `CarteC2S` (ci-dessus) ; coordonnées
  décodées depuis un bloc et un octet : toujours finies.
- Usure : `EnregistrerRegles` (op 2 vérifié au traitement ; listes bornées à la lecture, valeurs corrigées).
- Frappes : `DesignatePacket` (coordonnées finies, jumelles en main, recharge gardée dans les données du joueur, carte
  seulement, hors zone VIP et match, portée, visée dans le sens du regard, ligne de vue, nombre de frappes) ;
  `LoadCartridgePacket` (type borné ; une cartouche prise, l'ancienne rendue avec ses frappes restantes ; jumelles par 1).
- Carte : `BaseC2S` (une réponse par seconde au plus). Cinématique : `Fin` (note seulement la fin).
- Clics : seuls les clics droits (après le contrôle de portée de Minecraft) lisent un bloc (planque, ouvrants du spawn) ;
  le seul clic gauche écouté (train) ne lit aucun bloc : le défaut des clics gauches lointains trouvé dans CRac
  (`v3_bases.md`) ne touche pas cette zone.

**Duplications et pertes d'objets** :
- Corps (`corps/mort/CaptureMort.java`, `corps/menu/CorpsMenu.java`) : les mêmes objets passent du joueur au corps (rien de
  copié), objet tenu à la souris et grille 2 x 2 compris, rendus si le corps ne peut pas être posé ; mort annulée (à terre,
  match) : pas de corps ; fouille : on ne fait que prendre (Maj + clic, double clic, touches 1 à 9, glisser), revérifiée à
  chaque clic (corps là, à portée, joueur vivant, vue toutes les 10 ticks), « Tout prendre » idem.
- Planque et coffre VIP (`utils/coffres/CoffresPersonnels.java`) : cases refusant sac plein et objet de match, même par
  Maj + clic (le complètement d'une pile existante ne peut pas contourner : une vitre verrouillée a une étiquette unique,
  un sac ne s'empile pas) ; joueur et coffres enregistrés ensemble à la fermeture ; déconnexion coffre ouvert : le joueur
  est enregistré par Minecraft, les coffres par la fermeture.
- Atelier de réparation (`usure/MenuReparationServeur.java`) : copie dans les données du joueur à chaque changement,
  rendu à la fermeture, à la déconnexion (avant l'enregistrement) et à la mort (pas à une mise à terre : l'écouteur
  ignore une mort annulée) ; case du kit bloquée ; touches 1 à 9 qui sortent le kit : l'atelier se ferme et rend l'objet.
- Caisse de largage, bagages du train, coffres des véhicules (objets déplacés au sol, jamais copiés), épave (jamais
  sauvegardée ; butin posé par le module des événements), `/inspecter` (op 4 + `/login`, lecture seule respectée).

**Lenteurs (TPS)** : relus pour le travail fait à chaque tick, les allocations et les lectures de carte : peuplement
(au plus 2 poses par tick ; le point doit être dans un tronçon où les créatures bougent, ce qui garantit des tronçons
complets sur 2 tronçons autour, donc aussi pour les collisions au bord), plafond (suivi par arrivées), IA (un quart par
passage), chemins (budget en nombre et en temps), bruits (file de 2 s), attaques, train (au plus 7 recherches d'entités
par tick quand il roule), roquettes, hélicoptère et veille, frappes, extraction (une fois par seconde), zones dangereuses
(une fois par seconde, boîtes avant le calcul), protection du spawn, cycle jour / nuit (réglages en mémoire, pas de
disque), pings, lampes. Lectures de fichiers : seulement au démarrage, sur commande ou quand la date (relevée à part)
change. Aucune boucle sur les joueurs qui les fait changer de monde pendant la boucle.

**Appels entre modules** : vers cette zone, `SpawnApi.allerAuSpawn` / `reapparitionALaBase`, `Inspecter.ouvrirPourPanel`,
`CoffresPersonnels.coffre` (+ `$Sorte`, `$Coffre`, `$MenuCoffre`), `ZonesServeur.niveauZone` / `nombreZones`,
`StrikeManager.countActive`, `RecycleursApi.signaler`, `ExtractionApi.ouvertes`, `EcranCarte`, `CorpsEcran` : présents,
mêmes signatures. Depuis cette zone : 0 erreur au contrôle (CRac, Grades, Loot, ClashWar, Réanimation, Clans, Événements,
CaraCrea, sacs, Curios, Zombie Awareness). Le module des événements appelle `SupplyCrateEntityServeur.caisseEnAttente` et
`HelicoApi` directement : vérifié par la fabrication.

**Mixins** (jars du serveur, `javap`) : TaCZ 1.1.8 (`HitboxHelper.getFixedBoundingBox`, `EntityUtil.findEntityOnPath`,
côté joueur `AdjustmentYRotModifier.apply` et `AnimationManager.playLowerAnimation` avec leur appel `m_20202_`),
Limitless Vehicle 0.5.10 (`WheeledVehicle.tickMove` et sa constante 0,01, `ClientVehicleAction.lambda$onClientMessageReceived$0`,
`ContainerCraft.m_213583_` / `m_7208_`, `PhysicsEngine.motionByImpact` / `rotAndFallByGravity`), Zombie Awareness 1.13.1
(`ZAUtil.getSenseNearEntity`) : toutes présentes ; le jar des véhicules de compilation (`libs/`) est identique à celui du
serveur. Les injections dans les mods des autres auteurs sont facultatives (`require = 0` : une autre version ne bloque
pas le démarrage) ; celles dans Minecraft lui-même (chemins des zombies, train et zombies chez le joueur) sont obligatoires.
Confirmé aussi par le démarrage d'essai « mixins stricts » lancé sur GitHub ce soir avec le jar `heraliz_monde-1.3.12`
(Mohist 916, toutes les injections qui ratent comptées comme des erreurs) : les trois configurations du serveur de cette
zone s'appliquent en entier (chemins 1 / 1, cibles 1 / 1, mods des autres auteurs 7 / 7) ; la seule injection ratée de ce
démarrage est dans le noyau (hors de cette zone). Le démarrage normal de la même version est réussi. Au démarrage, le
premier tick (154 ms) compte environ un tiers de HeraliZ Utils (lecture du réseau ferré, villes des zombies) : une seule
fois, au démarrage.

**Commandes** : toutes celles du staff demandent la permission 2 (4 pour `/inspecter`) ; celles des joueurs (`/suicide`,
`/canal`, `/extractions`, `/cinematique`) vérifient combat, à terre, spawn, délai.

---

## À tester en jeu

Rien n'a été essayé en jeu (pas de jeu ici). En plus des essais déjà listés pour les changements du jour (`a2_monde.md`,
« À tester en jeu » et « Suite du 21/11 » ; sections 49 et 50 de la fiche des essais) :

- [ ] Hélicoptère (`/event helico lancer`), l'abattre en survie avec plusieurs joueurs autour, une fois près de joueurs et
      une fois loin des autres : il s'écrase près de l'endroit où il est abattu, sur un sol valide (jamais dans l'eau, ni
      sur un toit), l'épave brûle puis le butin apparaît, comme avant. Pas d'alerte « [Diag] Blocage » au moment où il
      tombe.
- [ ] Hélicoptère qui explose en l'air faute de point valide (au-dessus de l'eau ou d'une ville dense) : la boîte noire
      tombe sur le dernier sol valide survolé, comme avant.

Pas testé : tout ce qui précède en jeu ; aucune mesure sur le serveur en ligne avec des joueurs.

---

## Fin du 21/11

Demandé le soir par le propriétaire (« fais en sorte que tout soit lançable et terminé à 100 % ») : les quatre points
restants de cette zone. Commits **`a7d82cbf7`** et **`958fe1631`**, **Monde 1.3.13**. Le jar du serveur change ; côté
joueur, seulement des textes du staff et le `mods.toml` (point 4) ; aucun canal réseau ne change. Pas fait, comme
demandé : planque et coffre VIP, jumelles, essai de la vue des zombies (à mesurer en jeu).

### 1. Borne de départ : plus de terrain lu par le serveur pour un point pas prêt

En simple : c'est la proposition n° 1 plus haut. Quand un joueur part du spawn, la borne tire un point d'arrivée et
demande son tronçon à Minecraft, qui le lit en tâche de fond. Avant, si ce tronçon n'était pas prêt au bout de 2 s, le
serveur le lisait lui-même (de 50 à 300 ms de gel pour tout le monde), et pouvait recommencer à chaque essai (60 au plus).
Maintenant, ce point est abandonné et un autre est tiré, sans rien lire. Pour que le joueur parte toujours, deux derniers
recours, contrôlés comme avant : le dernier des 60 essais, et une fois si rien n'est trouvé 15 s après la fin du compte à
rebours. Donc au plus deux tronçons lus par le serveur pour un départ (au lieu de 60), jamais plus d'un par tick pour tout
le serveur, et au plus 8 points contrôlés par tick en tout (les autres attendent le tick suivant).

Pour les joueurs : rien ne change quand le serveur va bien (une lecture prend bien moins de 2 s). Sous très forte charge
(par exemple tout le monde qui part en même temps après un redémarrage), « Recherche d'un point d'arrivée sûr… » peut
durer quelques secondes de plus, mais sans gel.

Dans le même esprit :
- réglage `delai = 0` (pas utilisé sur le serveur, qui garde 5 s) : même recherche, sans compte à rebours ; avant, les
  60 essais d'un coup ;
- `/depart joueur <cibles>` (staff) : chaque joueur visé part par la même recherche, en une ou deux secondes ; avant, tous
  les joueurs visés étaient cherchés d'un coup (des secondes de gel pour un groupe). Le staff voit « N joueur(s) en route
  vers la carte : chacun part dès que son point d'arrivée est prêt. » Ce départ imposé n'est annulé ni par un pas ni par
  une blessure (le joueur ne l'a pas demandé) ; la mort ou un changement de monde l'annulent ;
- `/depart info` donne deux compteurs depuis le démarrage : points abandonnés (terrain pas encore chargé) et points
  contrôlés en dernier recours. Normalement 0 ou presque : des nombres qui grimpent veulent dire que le serveur a du mal à
  charger le terrain ;
- seul `/depart essai` (staff, pour essayer les réglages) cherche encore tout d'un coup : commande rare, à la main.

Pour les développeurs : `modules/monde/src/serveur/java/fr/heralisz/utils/spawn/DepartServeur.java` : `ATTENTE_TIRAGE`,
`RECHERCHE_MAX`, `CONTROLES_PAR_TICK`, compteurs l. 145-164 ; `EnCours.force` et `EnCours.recours` ; `demander` l. 234
(plus d'appel à `chercher`) ; `tick` l. 324-352 ; `blesse` l. 793 ; `envoyer` l. 1021 ; `info` l. 1060 ; `chercher`
l. 468 ne sert plus qu'à `/depart essai` (l. 1002).

### 2. `/inspecter <joueur> base` : les tronçons de la base lus en tâche de fond

En simple : la vue « base » montre tous les conteneurs de la base d'un joueur. Pour une base loin de tout joueur, ses
tronçons (des dizaines pour une grande base) n'étaient pas en mémoire et le serveur les lisait tous d'un coup : jusqu'à une
seconde de gel pour tout le monde. Maintenant, Minecraft les lit en tâche de fond ; le staff voit « Base de X : N
morceau(x) de carte à charger, la vue s'ouvre dès qu'ils sont prêts (10 s au plus). » et la vue s'ouvre toute seule dès
que tout est prêt. Au bout de 10 s, elle s'ouvre avec ce qui est prêt et le staff est prévenu : « … N morceau(x) de carte
toujours pas chargé(s) : leurs conteneurs ne sont pas dans la vue. Relance-la dans quelques secondes. » Une base déjà en
mémoire (près d'un joueur) s'ouvre tout de suite, comme avant. Pour cette vue, le serveur ne lit plus jamais un tronçon
pas prêt. Si le staff ouvre une autre vue pendant l'attente, celle de la base ne vient pas par-dessus. Le joueur inspecté
est repris au moment de l'ouverture (il a pu se connecter ou partir pendant l'attente). Même chose depuis le bouton
« Contenu de la base » de la vue de l'inventaire.

Pour les développeurs : `modules/monde/src/serveur/java/fr/heralisz/utils/inspecter/Inspecter.java` : `ouvrirBase` l. 271
(tickets, puis vue tout de suite ou attente) ; `DemandeBase`, `BASES_EN_ATTENTE`, `ATTENTE_BASE` l. 310-317 ; `manquants`
l. 320 et `tick` l. 331 (toutes les 5 ticks, seulement avec une demande ; `Troncons.pret` : rien chargé ni attendu) ;
`afficherBase` l. 371 (seulement les tronçons complets) ; `ouvrir` l. 161 (une autre vue annule l'attente).

### 3. Panneau d'ascenseur sans machine : plus de recherche à chaque clic

En simple : un panneau d'appel cherche sa machine au-dessus de lui (jusqu'à 220 blocs plus haut, à 5 blocs de l'axe :
jusqu'à 26 000 cases). Un panneau mal posé, sans machine, refaisait toute cette recherche à chaque clic : un joueur qui
cliquait vite pouvait faire ramer le serveur. Maintenant, un panneau sans machine n'est recherché qu'une fois toutes les
5 s ; entre-temps, « L'ascenseur ne répond pas » s'affiche tout de suite. Une machine posée ensuite est reconnue dès son
premier relevé des paliers (au plus 5 s pour un panneau tout au bord de sa zone). Rien ne change pour un ascenseur bien
monté (sa machine est déjà connue).

Pour les développeurs : `modules/monde/src/serveur/java/fr/heralisz/utils/ascenseur/MachineBlockEntityServeur.java`
l. 150-190 (`SANS_MACHINE`, 5 s ; jamais plus de 256 panneaux gardés).

### 4. `mods.toml` : version du noyau demandée

Relevé de la validation v10 : trois sous-mods de ce jar se servent des tronçons complets du noyau (`Troncons`, noyau 1.1.5
ou plus) mais acceptaient un noyau plus ancien. Maintenant, `heraliz_usure` et `heraliz_extraction` demandent le noyau
1.1.5 ou plus ; `heraliz_ping` le demande obligatoire (avant : facultatif), 1.1.5 ou plus (il appelle `Troncons` sans
protection). Le noyau est bien plus récent (1.1.12 en ligne, 1.1.13 sur la branche) : rien ne change, sauf qu'un vieux
noyau serait refusé au démarrage avec un message clair, au lieu d'une erreur en jeu. Fichier :
`modules/monde/src/commun/resources/META-INF/mods.toml` l. 70-82 (ping), 172-177 (usure), 292-297 (extraction).

### Vérifié hors du jeu

- relu tout ce que lit le contrôle d'un point de départ : une fois le tronçon du point prêt, seulement ce tronçon (sol de
  la colonne, place du corps) ; bases de CRac et conteneurs de loot : leurs listes, pas le terrain ;
- nouveaux textes en français, anglais et russe (`assets/heralisz_utils/lang/`) ; contrôle des langues : 0 erreur ;
- fabrication et contrôles sur GitHub (« Contrôle de branche », sur `19008fffa` qui contient les deux commits) :
  **réussis** (fabrication, images, contrôles : synchronisation 0 erreur, langues 0 erreur, vérificateur 0 problème) ;
- **démarrage d'essai** lancé sur GitHub sur le même commit, avec Monde 1.3.13 refabriqué à partir de ces sources :
  **réussi**, le serveur arrive à « Done » (24 s), aucune erreur de dépendance (les nouvelles versions du noyau demandées
  sont acceptées) ; aucune des 11 lignes d'erreur du journal ne vient de cette zone (modèles des véhicules d'un autre
  auteur, deux cases Curios, réglages du monde plat de l'essai).

### À tester en jeu (Monde 1.3.13)

Rien n'a été essayé en jeu.

- [ ] Borne de départ, un joueur : compte à rebours de 5 s, départ, arrivée sur un sol valide, comme avant ; bouger ou
      être blessé pendant le compte à rebours annule, comme avant.
- [ ] Borne, beaucoup de joueurs en même temps (ou juste après un redémarrage) : tous partent, pas d'alerte « [Diag]
      Blocage » ; puis `/depart info` : les deux nouveaux compteurs (normalement 0 ou presque).
- [ ] `/depart joueur <pseudo>`, puis `/depart joueur @a` au spawn : message « en route » ; chaque joueur part en une ou
      deux secondes, même s'il marche.
- [ ] `/inspecter <joueur> base` sur une base loin de tout joueur : message « N morceau(x) de carte à charger », puis la
      vue s'ouvre seule avec tous les conteneurs ; sur une base près du staff : la vue s'ouvre tout de suite, comme avant ;
      ouvrir une autre vue (inventaire, sac) pendant l'attente : la vue de la base ne vient pas par-dessus.
- [ ] Ascenseur bien monté : comme avant. Panneau posé sans machine au-dessus : « L'ascenseur ne répond pas », pas de
      ralentissement en cliquant vite ; poser ensuite la machine : le panneau marche (au plus 5 s après).
- [ ] Démarrage du serveur et connexion d'un joueur avec Monde 1.3.13 : aucune erreur de dépendance.

Pas testé : tout ce qui précède en jeu ; aucune mesure sur le serveur en ligne.
