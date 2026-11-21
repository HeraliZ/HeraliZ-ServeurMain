# Audit global du 21/11 — zone a2 : monde et zombies

Relecture du code du module `modules/monde` : HeraliZ Utils (`heralisz_utils` : zombies de la survie, peuplement, IA et
chemins, plafond, train, ascenseur, cycle jour / nuit, monde du spawn et sa protection, borne de départ, cinématique
d'arrivée, coffres personnels, `/inspecter`, `/suicide`, distance de vue), la carte du monde (`heraliz_carte`), les corps
(`heraliz_corps`), les pings (`heraliz_ping`), l'usure des armes et les kits de réparation (`heraliz_usure`), les véhicules
(`heraliz_vehicules`, mixins sur Limitless Vehicle), les décors et les zones dangereuses (`heraliz_decors`, `/brume`),
l'extraction (`heraliz_extraction`) et les frappes aériennes, largages et l'hélicoptère (`airstrike`). Plus les appels des
autres modules vers cette zone (réflexion) et les mixins sur les mods des autres auteurs. Version : **Monde 1.3.10** (seul
le jar du serveur change vraiment ; le launcher donnera le nouveau jar aux joueurs, aucun canal réseau ne change : un jeu
pas à jour entre encore).

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, contrôle des cibles des mixins dans les vrais jars
(`javap` sur TaCZ 1.1.8, Limitless Vehicle 0.5.10 et Zombie Awareness 1.13.1 de `serveur/mods-tiers/`), contrôle de chaque
cible lue par réflexion dans les autres modules, puis la fabrication et les contrôles sur GitHub.

Gravité : **moyen** (lag possible pour tout le serveur, sans perte d'objet), **faible** (petit coût ou cas très rare).

En bref : 6 points corrigés (1 moyen, 5 faibles), tous de la même famille ; 8 propositions à décider. **Aucun bug grave
trouvé** : pas de plantage, pas de duplication d'objet, pas de trou dans les droits des commandes, et chaque paquet envoyé
par le jeu d'un joueur est revérifié par le serveur.

---

## Corrigé

Tout dans le commit **`b637837a`** (Monde 1.3.10).

**Le défaut commun, en simple.** Pour savoir si un morceau de carte (un « tronçon », 16 × 16 blocs) est là, ces endroits
demandaient au jeu « est-il chargé ? » (`isLoaded`, `hasChunk`, `hasChunkAt`). Minecraft répond « oui » dès que le tronçon
est **demandé**, même s'il est encore en train d'être lu sur le disque. La lecture d'un bloc ou de la hauteur du sol qui
suivait faisait alors **attendre tout le serveur** jusqu'à la fin de cette lecture (le fil du serveur fait pendant ce temps
le travail de chargement en file) : un petit gel pour tout le monde. C'est le même défaut que le point C4 de l'audit des
lenteurs du 19/11, corrigé alors dans le noyau et d'autres modules ; le noyau a pour ça une question sûre,
« le tronçon est-il **complet** ? » (`fr.heraliz.noyau.api.Troncons`), qui n'attend jamais. Ces six endroits la posent
maintenant, et font comme pour un tronçon absent quand la réponse est non. Près des joueurs, les tronçons sont complets :
rien ne change pour eux.

1. **Hélicoptère de l'événement : le relevé du terrain faisait attendre le disque (moyen, lag).** Tous les 16 blocs,
   l'hélicoptère relève la hauteur du sol sous lui et devant lui (0 à 64 blocs) pour choisir son altitude. Les tronçons à 2
   de lui reçoivent son ticket à chaque fois qu'il change de tronçon : ce relevé tombait souvent sur un tronçon tout juste
   demandé, donc encore en lecture, surtout loin des joueurs (entrée et sortie par le bord de la carte, patrouille à
   ±150 blocs d'un joueur). Le serveur attendait alors le disque, plusieurs fois par vol. Maintenant un tronçon pas prêt ne
   compte pas (comme un tronçon absent avant) ; si aucun point n'est lisible, il garde le sol relevé juste avant (il ne
   descend pas vers le bas du monde).
   `modules/monde/src/serveur/java/fr/heraliz/airstrike/entity/HelicopterServeur.java` l.402-421 (`releverSol`).
2. **Frappes aériennes : relevé du trajet de l'avion (faible, lag rare).** À chaque frappe, la hauteur du terrain est
   relevée sous tout le trajet de l'avion (environ 75 points sur 600 blocs, une bonne partie loin du joueur qui appelle).
   Un point dans un tronçon en lecture (bord de la vue d'un autre joueur) faisait attendre le serveur. Maintenant un tronçon
   pas prêt prend la hauteur de secours, comme un tronçon absent avant. Vaut aussi pour le largage public (même calcul).
   `modules/monde/src/serveur/java/fr/heraliz/airstrike/strike/StrikePlanner.java` l.36-43 (`groundY`).
3. **Ping posé depuis la carte M (faible, lag et petite faille).** Le joueur choisit le point lui-même, n'importe où dans la
   bordure ; le serveur lisait la hauteur du sol si le tronçon était « chargé ». Un jeu modifié pouvait viser exprès des
   tronçons en lecture (au bord de la vue des autres) pour faire attendre le serveur à chaque ping (dans la limite de la
   cadence des pings). Maintenant : hauteur du sol seulement si le tronçon est complet, sinon la hauteur du joueur, comme
   pour un tronçon absent.
   `modules/monde/src/serveur/java/fr/heraliz/ping/server/PingService.java` l.217-222 (`recevoirCarte`).
4. **Jumelles de frappe : tronçon de la cible (faible).** La cible vient du jeu du joueur ; le rayon de vue qui suit lisait
   ses blocs. Cible dans un tronçon pas complet : « hors de portée », comme une cible dans un tronçon absent.
   `modules/monde/src/serveur/java/fr/heraliz/airstrike/strike/DesignatorLogic.java` l.163-168 (`designate`).
5. **Train : quai de la gare suivante (faible, rare).** Un joueur qui se déconnecte à bord (ou qu'aucune place sûre ne
   permet de poser à côté de la voiture) est posé sur le quai de la gare où est le train, sinon de la suivante, parfois
   loin. Tronçon du quai en lecture : le serveur l'attendait. Maintenant il est posé au milieu du quai (le point prévu pour
   un tronçon pas chargé, sûr par construction).
   `modules/monde/src/serveur/java/fr/heralisz/utils/train/VoyageursServeur.java` l.261-263 (`quai`) et l.275 (`posable`).
6. **Lampes rallumées au chargement d'un tronçon (faible, très rare).** Le tick qui suit un chargement relit les lampes
   restées dans la « seconde noire » ; un tronçon déchargé puis redemandé entre-temps aurait fait attendre le serveur. Il
   est maintenant sauté (il sera relu à son prochain chargement).
   `modules/monde/src/serveur/java/fr/heraliz/decors/serveur/ClignotementServeur.java` l.66-67.

---

## À décider par le propriétaire

1. **Planque et coffre VIP déjà ouverts quand le combat commence (très faible).** Votre règle du 25/10 : pas de planque ni
   de coffre VIP en combat, à terre ni en match. Elle est vérifiée **à l'ouverture** seulement
   (`modules/monde/src/serveur/java/fr/heralisz/utils/coffres/CoffresPersonnels.java` l.121-127) : un joueur qui a ouvert
   sa planque avant d'être touché pourrait continuer d'y ranger son butin sous les balles. D'après les fiches d'essai, les
   coffres-forts sont au spawn, où l'on ne se bat pas : **rien à faire tant qu'aucun n'est posé sur la carte**. Si un jour
   vous en posez sur la carte : fermer l'écran dès que le joueur passe en combat ou à terre (contrôle déjà fait à chaque
   tick par Minecraft, `MenuCoffre.stillValid` l.461 ; coût nul ; l'objet tenu à la souris revient dans l'inventaire).
2. **Objets à vie et caisse de largage posée en bloc (faible, module armes).** La liste des « contenants qui disparaissent »
   de `modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/ObjetsAVieServeur.java` l.137-139 compare le nom exact de
   la classe : `fr.heraliz.airstrike.block.SupplyCrateBlockEntity`. Sur le serveur, cette caisse est la version serveur
   `SupplyCrateBlockEntityServeur` (méthode client / serveur) : le nom ne correspond jamais. **Sans effet aujourd'hui** : la
   caisse en bloc n'est posée que par le staff (objet du menu créatif) et ne disparaît jamais (son minuteur n'est armé par
   aucun code). Proposition (zone de l'audit des armes) : ajouter aussi le nom `…SupplyCrateBlockEntityServeur`, ou
   comparer avec la classe mère. Risque nul.
3. **`/inspecter <joueur> base` peut geler le serveur (faible, staff).** Pour lister les conteneurs d'une base, la vue lit
   tous les tronçons de la zone de l'armoire, en les chargeant d'un coup sur le fil du serveur s'ils ne le sont pas
   (`modules/monde/src/serveur/java/fr/heralisz/utils/inspecter/Inspecter.java` l.287) : une base loin de tout joueur,
   c'est des dizaines de tronçons lus en attendant, peut-être une seconde de gel pour tout le monde. Proposition : s'en
   servir près de la base (ou quand quelqu'un y est), ou plus tard une version qui charge en tâche de fond puis ouvre la
   vue. Rien à changer si vous l'utilisez rarement.
4. **Usure des armes : date du fichier de réglages relue sur le fil du serveur toutes les 2 s (faible).** Les autres fichiers
   relus quand ils changent (villes, zones, zones VIP) le sont par un fil à part depuis le 01/11 (`DatesFichiers`) ; celui de
   l'usure demande encore la date au disque lui-même
   (`modules/monde/src/serveur/java/fr/heraliz/usure/ReglesServeur.java` l.67). Un seul coup d'œil toutes les 2 s, presque
   toujours instantané ; seulement si le disque du Ptero est très occupé. Proposition : passer par le même fil (petit
   changement, à essayer avec `/usure config`).
5. **Zombies dans une ville pleine de joueurs : test « personne ne voit l'endroit » (à surveiller).** Avant de poser un
   zombie, le serveur lance un rayon depuis les yeux de chaque joueur à moins de 96 blocs qui regarde vers le point
   (`modules/monde/src/serveur/java/fr/heralisz/utils/zombies/Peuplement.java` l.326-340). Avec 30 joueurs dans la même
   ville, c'est jusqu'à 60 rayons par essai (environ 1 ms par tick au pire d'après le calcul). Rien à changer tant que
   `/diag profil` un soir chargé ne montre pas `Peuplement.vu` ; sinon : ne tester que les joueurs les plus proches.
6. **Train qui roule : un petit calcul par balle et par tick (faible).** Tant que le train est en service, chaque balle de
   TaCZ en vol sur la carte refait l'horaire des deux rames à chaque tick pour savoir si elle va toucher un wagon
   (`modules/monde/src/serveur/java/fr/heralisz/utils/train/BallesTrain.java` l.142-186), même à l'autre bout de la carte :
   quelques microsecondes par balle (environ 3 % du coût d'une balle de TaCZ elle-même). Proposition : écarter d'abord, par
   une seule distance au centre de chaque rame calculée une fois par tick, les balles à plus de 105 blocs (résultat
   identique). Gain petit.
7. **Ascenseur : panneau d'appel sans machine (très faible).** Un panneau dont la machine n'est pas trouvée relit jusqu'à
   26 000 cases au-dessus de lui à chaque clic
   (`modules/monde/src/serveur/java/fr/heralisz/utils/ascenseur/MachineBlockEntityServeur.java` l.151-169). Seulement pour
   un panneau mal posé ; proposition : retenir « pas de machine » quelques secondes.
8. **Jumelles : tronçons entre l'œil et la cible (très faible).** Le point 4 de « Corrigé » vérifie le tronçon de la cible ;
   ceux du trajet du rayon (150 blocs au plus) sont presque toujours dans la vue du joueur, donc complets. Les vérifier un
   par un est possible, mais n'apporte presque rien.

---

## Vérifié sans problème

**Paquets envoyés par le jeu des joueurs** (chacun revérifié par le serveur, sur son fil) :
- Train : `Marche` (doit être à bord, même voiture ou voisine, pas de 1,2 bloc au plus et vitesse comptée par tick du
  serveur, chiffres non finis refusés, saut borné à la courbe d'un vrai saut, descente seulement par une porte ouverte côté
  quai) ; `Bagage` (sa voiture, place existante, à portée, bagage « à fouiller » de la période) ; `Descente` ignoré.
- Cinématique : `Fin` (ne fait que noter la fin d'une cinématique déjà lancée).
- Pings : cadence (seau à jetons), joueur vivant et pas spectateur, entité visible et pas cachée, portée, ligne de vue sans
  jamais charger de tronçon (`Vue`), type borné ; ping de la carte dans la bordure et le monde du joueur.
- Usure : écran d'administration réservé aux op 2, listes et textes bornés à la lecture.
- Frappes : `DesignatePacket` (coordonnées finies, jumelles en main, recharge gardée, carte seulement, hors zone VIP et
  match, portée, visée dans la direction du regard, ligne de vue, nombre de frappes) ; `LoadCartridgePacket` (type borné,
  une cartouche prise, l'ancienne rendue avec ses frappes restantes : rien n'est copié).
- Carte : `BaseC2S` (une demande par seconde au plus, réponse de CRac).

**Duplications et pertes d'objets** :
- Corps : objets du mort déplacés (jamais copiés) vers le corps, objets tenus à la souris et grille d'artisanat compris ;
  rendus si le corps ne peut pas être posé ; fouille revérifiée à chaque clic (corps encore là, à portée, contenu le même,
  vue toutes les 10 ticks), rien ne se pose dans un corps ; corps déchargé ou retiré : écran fermé.
- Planque et coffre VIP : cases verrouillées imprenables, sac plein et objet de match refusés (y compris Maj + clic et
  touches 1 à 9) ; à la fermeture, joueur et coffres enregistrés ensemble ; fichier écrit à côté puis mis à la place.
- `/inspecter` : op 4 et `/login` d'AuthMe exigés ; lecture seule du panneau du staff respectée (glisser, Maj + clic,
  double-clic, touches) ; joueur hors ligne réécrit à chaque clic, vues fermées à sa connexion ; refusé pendant un dépôt du
  ClashWar.
- Atelier de réparation : objet posé recopié dans les données du joueur, rendu à la fermeture, à la déconnexion et à la
  mort, jamais pendant un match ; la case du kit ne se vide pas en cours de route.
- Caisse de largage : on n'y dépose rien, écran fermé quand elle disparaît ; bagages du train partagés comme un coffre ;
  coffres des véhicules vidés au sol (mêmes objets, déplacés) ; véhicule interdit rendu en objet une seule fois.

**Lenteurs (TPS)** : chaque système sans travail sort tout de suite de son tick (pings, hélicoptère, épave, roquettes,
frappes, bruits, bonus de loot, cinématique, borne de départ, recherche des recycleurs, lampes). Peuplement (joueurs
répartis sur l'intervalle, 2 poses par tick, aucun tronçon chargé), plafond des zombies (suivi par arrivées, recensement
complet une fois par minute), IA par niveaux (un quart des zombies par passage), chemins (budget en nombre et en temps,
partage, ligne droite de près), bruits (file de 2 s), zones dangereuses (boîtes avant le calcul), extraction (registre une
fois par seconde), cycle jour / nuit, protection du spawn, corps (une seconde sur vingt), train (tick du monde léger),
ascenseur : rien de lourd par tick, aucune lecture de fichier par tick (dates relevées par un fil à part), aucun
parcours de toutes les entités sauf le recensement d'une fois par minute.

**Commandes** : toutes celles du staff exigent la permission 2 (ou 4 pour `/inspecter`) ; celles des joueurs (`/suicide`,
`/cinematique` au spawn seulement, `/canal`, `/extractions`) vérifient combat, à terre, spawn, délai.

**Appels entre modules** : chaque cible lue par réflexion existe avec la même signature, dans les deux sens. Vers cette
zone : `SpawnApi.allerAuSpawn` / `reapparitionALaBase` (CRac, ClashWar, Boutique, banc), `Inspecter.ouvrirPourPanel`,
`CoffresPersonnels.coffre` et ses classes `$Coffre` / `$MenuCoffre` / `$Sorte`, `ZonesServeur.niveauZone` / `nombreZones`,
`StrikeManager.countActive` / `launch`, `StrikeType.byName`, `RecycleursApi.signaler`, `ExtractionApi.ouvertes` et sa
fiche, `CineDecorServeur.get`, `CineReseauServeur.reveilPraticable`, `DepartServeur.demander` / `carte`,
`Destination.teleport`, `PointsDepart.get` et `$Zone`, `ZombieSurvie.tenue()`, `EcranCarte`, `CorpsEcran`. Depuis cette
zone : `BaseCracApi.base`, `ClaimManager.getAt` / `basePres`, `SpawnCracApi.allerAuSpawnDefini`, `GradesApi.perm` /
`rangBoutique` / `authentifie`, `Coffre.aUnDepot` (ClashWar), `TrainLoot.tirer`, `ZonesLoot.conteneurPres`,
`RevelationCaisses.ouvrir`, `RaretesObjets.de`, `EvenementsApi.coffreOuvert` / `objetDuCoffre`, `Downed.isDowned`,
`Executions.of`, `Arrivee.personnageFait` (voix), `AllongeServeur` / `AllongeClient`, `CarteApi` des clans (tous les champs
lus par leur nom), `ClientClans.accent`, sacs (`backpacked$GetBackpackInventory`, `saveItemsToStack`), Zombie Awareness
(`EntityScent.getRange`, `f_19847_`, `findSense_PercentChance`, `maxPFRangeSense`).

**Mixins sur les mods des autres auteurs** (contrôlés dans les jars) : TaCZ (`HitboxHelper.getFixedBoundingBox`,
`EntityUtil.findEntityOnPath` et `getHitResult`), Limitless Vehicle (`WheeledVehicle.tickMove` et sa constante 0,01,
`ClientVehicleAction` et `leaveVehicle`, `ContainerCraft.m_213583_` / `m_7208_`, `PhysicsEngine.motionByImpact` /
`rotAndFallByGravity` / `velocity`), Zombie Awareness (`ZAUtil.getSenseNearEntity`) : tous présents ; le jar de
compilation des véhicules est identique à celui du serveur. Tous facultatifs (`require = 0`) : une autre version d'un de
ces mods ne bloque pas le démarrage.

**Divers** : fils à part (dates des fichiers : fil démon ; canaux des pings : écritures finies à l'arrêt) ; listes en
mémoire nettoyées à la déconnexion ou bornées ; aucun `@Mod.EventBusSubscriber` sans `modid` ; textes en trois langues
(rien de nouveau dans ce lot).

---

## À tester en jeu

Rien de cette section n'a été essayé (pas de jeu ici).

- [ ] Hélicoptère (`/event helico lancer`) : il entre par le bord de la carte, patrouille et repart comme avant, à la bonne
      hauteur au-dessus du relief (il ne frôle pas les collines, ne plonge pas) ; aucune alerte « [Diag] Blocage » pendant
      son vol, surtout loin des joueurs (le suivre de loin en spectateur).
- [ ] Frappe aérienne (jumelles, chaque type) et largage public : l'avion passe au-dessus du relief comme avant, les bombes
      tombent au sol, la caisse se pose ; pas de « [Diag] Blocage » au moment de l'appel.
- [ ] Jumelles visant une cible proche ou lointaine (jusqu'à 150 blocs) : frappe acceptée comme avant ; une cible tout au bord
      de la zone chargée peut répondre « hors de portée » (normal).
- [ ] Carte M : clic droit pour poser un ping, près et très loin : il apparaît à la hauteur du sol (ou à la hauteur du
      joueur si le coin n'est chargé par personne, comme avant) chez soi et chez son clan.
- [ ] Train : se déconnecter à bord entre deux gares, se reconnecter : réveil sur le quai de la gare (comme avant).
      Descendre par une porte ouverte en gare : comme avant.
- [ ] Lampes rouges d'un bunker, après un redémarrage : la pièce est éclairée comme avant.

Pas testé : tout ce qui précède en jeu ; aucune mesure sur le serveur en ligne avec des joueurs.
