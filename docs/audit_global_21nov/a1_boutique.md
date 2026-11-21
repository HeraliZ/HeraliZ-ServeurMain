# Audit global du 21/11 — volet a1 : boutique (grades, casino, caisses)

Relecture du code du module `modules/boutique` (un seul jar, trois mods) :
- **HeraliZ Grades** (`heraliz_grades`) : grades et permissions, staff caché (vanish) et liste Tab par différences,
  panel du staff et son onglet Économie, commandes données par le Fondateur, kits et recharges, `/tpa`, `/warp`, zones
  VIP, surnom et chat en couleur, `/trade` (échange avec dépôt), boutique en jeu et Points Boutique (PB, appels signés au
  site), bons, `/money`, hôtel des ventes, chat à canaux, lien Discord ;
- **HeraliZ Casino** (`heralizcasino`) : machines, compte de capsules et distributeur, caisse, zone VIP et videurs, tablette
  du Directeur ;
- **HeraliZ Caisses** (`heralizcrates`) : clés sur le compte, roue d'ouverture, objets en attente, éditeur du butin.

Version : Boutique **1.7.2** (le launcher la donne ; aucun canal réseau ne change, un jeu pas à jour entre encore).

**Rien n'a été testé en jeu** : relecture du code, contrôle des cibles lues par réflexion (sources des autres modules,
jar de TaCZ avec `javap`), contrôle des textes (`outils/verifier_langues.py` : 0 erreur), puis fabrication et contrôles sur
GitHub (« Contrôle de branche »).

Gravité : **important** (faille ou réponse manquante qui touche les joueurs), **moyen** (lag ou abus possible, perte rare),
**faible** (petit coût ou cas très rare).

## Corrigé

Tout dans le commit **81e66a6f**.

1. **Caisse du casino : une commande de la console pouvait venir d'un joueur (important, sécurité).** La caisse a des
   « encaissements » : une commande jouée par la console du serveur (donner un grade à la journée, une émote…). La
   tablette du Directeur renvoie au serveur toute la page de la caisse ; le serveur reprenait ses commandes seulement
   quand le jeu renvoyait le signe « caché ». Un jeu modifié d'opérateur (niveau 2) pouvait donc y écrire n'importe quelle
   commande, que la console jouait ensuite avec tous les droits à chaque achat à la caisse. Maintenant les encaissements
   restent toujours ceux du serveur (la tablette ne les règle pas : ils se règlent dans `casino.json`), rien ne change
   pour un jeu normal.
   `modules/boutique/src/serveur/java/fr/heraliz/casino/config/CasinoConfigServeur.java` l.70 et l.92-101
   (`garderEncaissements`, à la place de `garderCommandes`).
2. **Tablette du Directeur et éditeur du butin des caisses : le /login d'AuthMe n'était pas exigé (moyen, sécurité,
   défense en double).** Le serveur est en mode cracké : n'importe qui peut arriver avec le pseudo d'un opérateur, et seul
   AuthMe (mot de passe) prouve que c'est lui. Les commandes du casino et des caisses exigent ce /login depuis le 15/11,
   mais pas les paquets de la tablette (réglages des machines et de la caisse, statistiques) ni celui de l'éditeur du
   butin. Aujourd'hui la « porte » de l'anticheat (Spartan, `PorteJoueurs`) retient déjà tous les paquets des mods avant
   le /login ; si elle manquait (jar retiré, réglage), ces paquets passaient. Maintenant le mod le vérifie lui-même :
   opérateur ET connecté sur AuthMe.
   `modules/boutique/src/serveur/java/fr/heraliz/casino/net/ReceptionServeur.java` l.32-44 et l.84 (`operateur`) ;
   `modules/boutique/src/serveur/java/fr/heraliz/crates/opening/LootEditing.java` l.32-39 (`allowed`).
3. **`/boutique abonnement`, `/avantages nick qui`, `/grade voir`, `/grade liste` ne répondaient rien (important,
   joueurs).** Le monde garde la règle de Minecraft `sendCommandFeedback = false`, qui cache aux joueurs qui ne sont pas
   opérateurs toutes les réponses « réussi » des commandes. Ces quatre commandes répondaient par ce canal : un joueur qui
   tapait `/boutique abonnement` (proposée dans l'écran Informations) ne voyait ni son grade ni son temps restant ; même
   chose pour `/avantages nick qui`, et pour `/grade voir` ou `/grade liste` tapées par un Helpeur ou un Modérateur qui
   n'est pas opérateur. Déjà vu le 02/11 (« vu en passant »), jamais corrigé. Maintenant la réponse part directement au
   joueur (même texte), la console reçoit comme avant.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/CommandesGrades.java` l.49-58 (`repondre`), l.228-244 ;
   `AbonnementServeur.java` l.111-112 ; `SurnomsServeur.java` l.355-356.
4. **Console : `pb retirer` refusé par le site affiché comme réussi (faible, console).** Un refus clair du site (solde
   insuffisant, compte inconnu) s'affichait « retirer … PB à … -> solde -1 » ; rien n'était retiré (le journal et Discord
   étaient justes). Signalé le 26/10 (A179). Maintenant : « Refusé par le site : solde_insuffisant ».
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/CommandesPb.java` l.131-137.
5. **Objet donné à un joueur mort : perdu (faible, perte rare).** Ce que le serveur donne (fin de la roue d'une caisse,
   bon rendu, objet acheté…) allait dans l'inventaire même pendant l'écran de mort : Minecraft ne garde pas cet inventaire
   à la réapparition, l'objet disparaissait. Exemple : ouvrir une caisse et mourir pendant que la roue tourne. Maintenant
   l'objet attend dans les objets en attente des caisses et arrive après la réapparition (« Objets qui t'attendaient remis
   dans ton inventaire »).
   `modules/boutique/src/serveur/java/fr/heraliz/crates/loot/PendingRewards.java` l.88-106 (`donner`).
6. **Hôtel des ventes : une demande au site à chaque ouverture (moyen, abus possible).** Chaque ouverture (`/hdv` ou le PNJ
   commissaire-priseur, qui se clique sans limite) demandait le solde de PB au site. Le site limite tout le serveur (une
   seule adresse) : quelqu'un qui cliquait le PNJ en boucle pouvait faire refuser les PB de tout le monde un moment (achats,
   échanges, hôtel des ventes en attente : rien n'est perdu, ils sont repris). Maintenant : au plus une demande toutes les
   5 s à l'ouverture, le dernier solde connu s'affiche tout de suite ; il est toujours relu après un achat ou un versement
   en PB, comme avant.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/HdvServeur.java` l.930-933.
7. **Chat fermé : petites allocations à chaque image (faible, images par seconde).** Pour chaque ligne affichée (jusqu'à
   4, pendant 8 s après chaque message), la pastille du canal (PROX, GLOBAL, CLAN…) était traduite et mise en capitales
   deux fois par image. Maintenant le texte des 5 pastilles est fait une fois, refait si la langue change.
   `modules/boutique/src/client/java/fr/heraliz/grades/client/chat/ChatHeraliz.java` l.216-232 (`texteTag`), l.265, l.288.

## À décider par le propriétaire

1. **Tablette du Directeur au niveau des commandes qui créent de l'argent ?** Aujourd'hui un opérateur de niveau 2 règle
   avec la tablette les gains des machines et les objets et prix de la caisse (donc, en pratique, crée des capsules et des
   objets), alors que `/casino jetons donner` ou `/eco donner` demandent le niveau 4 et le /login. Proposition : page de la
   caisse et réglages des gains au niveau 4 seulement. Risque : nul si seuls les Fondateurs sont opérateurs (à vérifier
   dans `ops.json` du Ptero) ; gain : aucun opérateur de niveau 2 ou 3 ne peut créer de l'argent par la tablette.
2. **`/trade` accepte des objets que l'hôtel des ventes et Mila refusent** : objets à vie (couteaux, skins à vie), objets de
   quête (`heraliz_quetes`), bourses de hérals, registres du staff. Le commentaire de `RegleHdv` dit le contraire (« mêmes
   refus que `/trade` »). Votre règle du 03/11 laisse un objet à vie changer de main (au sol, il devient à qui le ramasse) :
   l'échange est peut-être voulu. À dire : `/trade` doit-il refuser la même liste que l'hôtel des ventes (sauf les sacs
   pleins, qui gardent leur contenu dans un échange) ? `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/EchangeServeur.java`
   l.304-328 ; `modules/boutique/src/commun/java/fr/heraliz/grades/hdv/RegleHdv.java` l.15-19 et l.43-60.
3. **Staff caché et messages privés (zone du noyau, à vérifier en jeu).** `/msg`, `/tell`, `/w` sont les commandes de
   Minecraft : sur Mohist, elles trouvent un joueur caché par son pseudo (« Tu chuchotes à … ») là où `/trade`, `/tpa` et
   `/profil` répondent « joueur introuvable ». Un joueur peut donc savoir qu'un membre du staff caché est connecté. À
   tester : un joueur tape `/msg <staff caché> salut`. Si la réponse n'est pas « Aucun joueur trouvé », proposition : le
   noyau refuse ces commandes vers un caché pour qui n'est pas du staff (même message que les autres).
4. **`/kit` demande au bot Discord à chaque fois (faible).** Pour savoir si le joueur porte le tag Soutien, `/kit` appelle
   HeraliBot à chaque ouverture, sans limite ; bot arrêté : le menu s'ouvre au bout de 5 à 8 s. Proposition : reposer la
   question au plus une fois par minute et par joueur (la dernière réponse sert entre-temps).
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/KitsServeur.java` l.286-290.
5. **`/avantages nick` relit `usercache.json` à chaque essai (faible).** Pour vérifier qu'un surnom n'est pas le pseudo
   d'un autre joueur, le serveur relit et décode ce fichier (jusqu'à 1 000 pseudos) sur son fil, à chaque commande, sans
   délai entre deux essais. Petit à-coup (quelques millisecondes) ; réservé aux grades Traqueur et Roi de la Zone.
   Proposition : garder la liste en mémoire tant que le fichier ne change pas.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/SurnomsServeur.java` l.213-246.

## Vérifié sans problème

- **`/trade` (dépôt, risque de copie le plus fort)** : cases recomparées à chaque tick (case changée = l'objet sort, les
  « Prêt » sautent), compte à rebours de 3 s, exécution dans un seul tick puis sauvegarde des deux joueurs ; avec des PB,
  objets mis en dépôt AVANT l'appel au site, fichier écrit en entier ou pas du tout, livraisons seulement dans des cases
  vides et au spawn, « pas fait » du site cru seulement après 2 min, reprise après plantage, `/wipe` ; clés, objets de match
  et camos à vie refusés ; seul le joueur choisit des cases 0 à 35, montants bornés. Aucune copie ni perte trouvée.
- **Boutique en jeu et PB** : référence écrite avant le débit, remboursement si rien n'est livré, livraison partielle notée
  pour le staff, erreur 500 du site jamais prise pour un refus ; appels au site sur un autre fil (réponses sur le fil du
  serveur, délais de 5 et 10 s), signature HMAC, jamais de blocage du serveur ; refusés en combat, à terre, en match, au
  tutoriel et sur la carte ; pseudos limités par AuthMe (`[a-zA-Z0-9_]`) avant d'entrer dans une commande de la console.
- **Bons** : signature, usage unique (fichier écrit avant de donner), bons à PB mis de côté avant l'appel au site, copie
  neuve pour chaque bon remis par une caisse ou un kit, éditeur réservé aux op 4 connectés.
- **Hôtel des ventes** : journal d'écriture, marques sur le joueur, taxe prélevée avant la sortie de l'objet, achat réservé,
  paquets relus et limités (300 ms par action, 120 ms par liste), arme déchargée avant la mise en vente.
- **Casino** : compte de capsules (fichiers écrits avant de rendre vrai, joueur sauvegardé, jamais de solde négatif,
  250 ms entre deux opérations), mises et gains (manches notées hors du monde avant le versement), duels (remboursés à
  l'annulation, forfait des absents), caisse (quantités bornées à 10 000, place vérifiée avant de payer), paquets des
  machines (distance, machine prête, spectateur refusé).
- **Caisses** : clé prise sur le disque avant le tirage, récompense retirée du disque avant d'être donnée, aperçu en lecture
  seule, éditeur borné (quantités, poids, raretés).
- **Kits** : recharge écrite avant de donner, place vérifiée, numéro de l'anticheat retiré, une demande par 0,5 s.
- **Staff caché et Tab** : rien n'est envoyé aux joueurs hors du staff (liste Tab, tags de clan, voix), différences
  numérotées et contrôlées à chaque fabrication (`outils/verif_synchro.py`), ancien staff remis visible, tout oublié au
  départ des joueurs.
- **Panel du staff et onglet Économie** : rang lu du grade et du /login, jamais de sanction d'un staff de rang égal ou plus
  haut, options de LiteBans retirées des raisons, 0,7 s entre deux actions, Économie au vrai Fondateur seulement, montants
  et cibles revérifiés ; commandes données par le Fondateur : racines interdites et sous-commandes d'admin jamais données.
- **Paquets reçus des joueurs** (grades, staff, boutique, bons, hôtel des ventes, chat, caisses, casino) : tailles bornées
  à la lecture, chaque action revérifiée par le serveur.
- **Cibles lues par réflexion, depuis la boutique** : toutes existent avec la même signature (Emotes, Noyau `Rechargements`,
  Clans `ClanStore`/`ClanService`/`EconomieApi`, CRac `BaseCracApi`/`TutorielApi`/`SpawnCracApi`, Quêtes `QuetesApi`,
  Réanimation `Downed`, Tactique `ObjetsAVie`, Loot `RaretesObjets`, Utils `SpawnApi`/`Inspecter`, CaraCrea `TabIcons`,
  Backpacked, TaCZ `IGun`/`IGunOperator` vérifiés avec `javap`). **Vers la boutique** : `GradesApi` (perm, rangBoutique,
  staff, cache, voitLesCaches, authentifie, fondateur, gradeAfficheTextes, nomObjet), `ClesApi` (crediter,
  crediterUneFois, wipe, wipeReouverture), `CapsulesApi` (solde, surLui, wipe), `HdvApi` (ouvrir, wipe),
  `EchangeServeur.wipe`, `Bon.exemplaire` : tous présents, signatures inchangées.
- **Travail à chaque tick** : zones VIP (tous les 4 ticks, quelques tests de boîte par joueur), conversion des vieilles clés
  (une fois par seconde, rien de créé sans clé), staff caché (rien sans caché), Tab (rien sans changement), kit de départ,
  zone du casino (seulement les joueurs du monde du spawn). Fils d'écriture (hôtel des ventes, recharges, casino) : fils
  « démon », attendus à l'arrêt.

Pas refait (déjà proposé, en attente de votre décision) : départ du `/tpa` pendant l'attente de l'arrivée (§41),
conversion des vieilles clés de la planque (§41), objets et capsules en attente écrits plus tard (§42), couleurs des
raretés des caisses (§45).

## À tester en jeu

- [ ] Tablette du Directeur (op connecté) : régler une machine, puis la page de la caisse (prix d'un objet), les lumières,
      les statistiques : tout marche comme avant. À la caisse, acheter un encaissement (grade à la journée, émote au
      hasard) : il est bien donné (les commandes de `casino.json` sont gardées).
- [ ] Éditeur du butin d'une caisse (op connecté, outil d'édition ou `/heraliz edit`) : ouvrir, changer une chance,
      enregistrer : comme avant.
- [ ] Un joueur sans grade du staff ni op : `/boutique abonnement` affiche son grade et le temps restant (ou « Aucun
      grade de la boutique en cours ») ; `/avantages nick qui <surnom d'un joueur>` affiche le vrai pseudo.
- [ ] Un Helpeur (pas op) : `/grade voir <pseudo>` ; un Modérateur (pas op) : `/grade liste` : la réponse s'affiche.
- [ ] Console du serveur : `pb retirer <pseudo> 999999999` (plus qu'il n'a) : « Refusé par le site : … ».
- [ ] Ouvrir une caisse, et mourir pendant que la roue tourne (op : `/kill`) : après la réapparition, l'objet gagné arrive
      (« Objets qui t'attendaient remis dans ton inventaire »).
- [ ] Hôtel des ventes : ouvrir, fermer, rouvrir aussitôt : le solde de PB s'affiche ; acheter en PB : le solde suit.
- [ ] Chat : les pastilles PROX / GLOBAL / CLAN / MP / INFO s'affichent comme avant ; changer la langue du jeu (anglais,
      russe) : elles suivent.
- [ ] Journal du serveur : aucune erreur nouvelle de `heraliz_grades`, `heralizcasino` ou `heralizcrates`.

Pas testé : tout en jeu (aucune compilation ici : fabrication et contrôles sur GitHub seulement).
