# Validation de l'audit du 21/11 — v1 : boutique (grades, casino, caisses)

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider »), du module
`modules/boutique` : HeraliZ Grades (`heraliz_grades`), HeraliZ Casino (`heralizcasino`), HeraliZ Caisses
(`heralizcrates`). Point de départ : la branche en ligne du 21/11 à 17 h 43 (heure UTC, `6638bb00a`).

Deux travaux :
1. relire **chaque changement du jour** de la boutique, sans faire confiance à la première relecture ;
2. relire **toute la zone** à nouveau (copies d'objets ou d'argent, droits, paquets envoyés par les joueurs, lenteurs du
   serveur, images par seconde chez les joueurs, liens par réflexion avec les autres mods).

Version : Boutique **1.7.4** (rien ne change dans les canaux réseau : un jeu de joueur d'avant se connecte toujours ; le
launcher donnera le nouveau jar, qui allège la caisse du casino chez le joueur).

**Rien n'a été testé en jeu** : relecture du code, essais hors du jeu (voir plus bas), fabrication et contrôles sur GitHub
(« Contrôle de branche »).

Gravité : **important** (faille ou réponse fausse qui touche les joueurs), **moyen** (abus possible, attente ou message
faux), **faible** (petit coût ou cas très rare).

## Changements du jour validés

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `264894b1a` (20/11 au soir, Boutique 1.7.0) | liste Tab envoyée par différences numérotées (`Synchro`) | **OK**. Les trois copies de `Synchro.java` sont identiques, l'essai hors du jeu passe (`outils/verif_synchro.py` : 16 000 ticks, avec 0, 2 et 20 % de paquets perdus, 0 problème). Le tableau envoyé est neuf à chaque envoi (`TabServeur.lignes`), un même paquet sert à tous les joueurs d'une vue, un joueur caché n'est jamais dans la vue des joueurs hors du staff. |
| `a1490b67b` (Boutique 1.7.1) | `GradesApi.voitLesCaches` pour les clans et la voix | **OK**. La méthode existe, publique, signature `(ServerPlayer) -> boolean`, lue par `PontVanish` (clans) et `HeralizVoiceCompat` (voix) avec cette signature. Coût : un appel à AuthMe par joueur toutes les 2 s (clans) ou 0,5 s (voix, seulement si quelqu'un est caché) : sans danger. |
| `81e66a6f4` (Boutique 1.7.2) | audit a1 : caisse du casino, /login, réponses visibles, objet donné à un mort, hôtel des ventes, chat | **OK**, point par point : (1) les encaissements de la caisse sont toujours ceux du serveur (`CasinoConfigServeur.java` l.100-102) et la tablette ne les réglait de toute façon pas (`AdminScreen`, page de la caisse : aucun encaissement) : rien ne change pour le Directeur ; (2) tablette et éditeur des caisses : op ET /login (`ReceptionServeur.java` l.38-40, `LootEditing.java` l.37-39) ; (3) `repondre` envoie au joueur, la console comme avant ; (5) objet donné à un mort : il attend et arrive au plus 5 s après la réapparition (`PendingRewards.tick` toutes les 100 ticks, `livrable` = vivant) ; marche aussi pour un joueur parti (sa fiche est « retirée », donc pas vivante) ; (6) hôtel des ventes : une demande au site au plus toutes les 5 s, solde relu après un achat ou un versement ; (7) pastilles du chat : le tableau `TAG` est déclaré avant `TEXTE_TAG` (pas de plantage au chargement), 5 pastilles pour les canaux 0 à 4. **Une remarque sur le point 4** (`pb retirer` « affiché comme réussi ») : le site ne répond jamais « 200 et ok faux » (il refuse par 404, 409 ou 422, voir `site/extension/heraliz/src/Controllers/PbController.php`), donc l'ancienne ligne affichait déjà le refus ; la nouvelle aussi : aucun dégât. Le vrai défaut de ce côté était ailleurs (boutique et /trade, corrigé plus bas). |
| `f324509e4` (coordination a10) | `heraliz_grades` demande `heralisz_economie` et `heralisz_clans` côté serveur | **OK**. Ces deux identifiants de mod existent dans le jar des clans ; `mandatory=true`, `side="SERVER"` : le jeu des joueurs et le solo ne sont pas touchés. |
| `3a2a69b4c` (Boutique 1.7.3) | `/kit` sans attendre le bot Discord ; pseudos de `usercache.json` gardés en mémoire | **OK**. `/kit` : le menu part tout de suite, le bot n'est réinterrogé qu'une fois par minute et par joueur, sa réponse sur le fil du serveur, joueur parti = rien gardé ; la mise à jour (`ouvrir` faux) ne rouvre jamais un menu fermé (`EcranKits.recevoir`). `/avantages nick` : le fichier est bien celui de la racine du serveur (`heraliz_grades/..` = dossier du serveur, comme avant), relu sur le fil des dates (5 s), une seule lecture à la fois, une lecture ratée (fichier en cours d'écriture par Minecraft) garde l'ancienne liste et réessaie ; les joueurs connectés et les gradés sont toujours vérifiés en direct. Même règle qu'avant (pseudos en lettres simples : comparaison en minuscules identique). |

Aucune régression trouvée dans les changements du jour.

## Corrigé

Tout dans les commits **`ea78a9699`** et **`495243dc5`** (Boutique 1.7.4).

1. **`/tpa` : deux téléportations pour une seule recharge (moyen, abus).** La recharge (8 min sans grade) n'était regardée
   qu'à l'envoi de la demande. Un joueur pouvait : demander B, B accepte, et pendant les 15 s d'attente (recharge pas
   encore posée) demander C ; arrivé chez B, C accepte et le joueur repart aussitôt. Les grosses équipes pouvaient donc
   enchaîner deux sauts. Maintenant la recharge est revue aussi quand la demande est acceptée et au moment de partir
   (mêmes messages qu'avant, déjà traduits : « Prochaine téléportation dans … » pour lui, « … ne peut pas venir pour le
   moment » pour celui qui accepte). Le staff n'a toujours pas de recharge.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/TpaServeur.java` l.186-189 (`rechargeEnCours`), l.446, l.516.
2. **Boutique en jeu et `/trade` : un refus du site des Points Boutique pris pour un site muet (moyen, joueurs).** Le site
   refuse par un code 404 (compte inconnu), 409 (solde insuffisant) ou 422 (demande invalide). L'hôtel des ventes le savait
   déjà ; la boutique et `/trade` attendaient un « 200 et ok faux » qui n'arrive jamais :
   - achat dans `/boutique` avec un solde trop bas (solde « — » ou pas à jour à l'écran) : le joueur lisait « site
     injoignable », le journal notait « ERREUR DU SITE 409 » et l'achat restait 2 min en attente pour rien ; maintenant
     « solde insuffisant » tout de suite (rien n'était débité) ;
   - `/trade` avec des PB : un solde trop bas gardait les objets des deux joueurs en dépôt au moins 2 min ; maintenant ils
     sont rendus tout de suite, avec le message du solde insuffisant.

   Un 500, un 503, un 401 ou une réponse perdue restent « on ne sait pas » (vérifiés plus tard, comme avant). Aucun risque
   pour l'argent : pour une même référence, le site ne fait l'opération qu'une fois, et ces refus ne font rien.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/Pb.java` l.90-100 (`refusClair`) ;
   `BoutiqueServeur.java` l.237 ; `EchangeServeur.java` l.492.
   Essai hors du jeu (`Pb.Reponse` avec Gson) : 404, 409, 422 = refus ; 200, 401, 500, 503 = pas un refus.
3. **Casino, chez le joueur : 18 Ko recollés à chaque image (faible, images par seconde).** Pour chaque caisse du casino à
   l'écran, la langue et tout le texte des réglages du casino (environ 18 Ko) étaient recollés puis comparés à chaque
   image, juste pour savoir si le tableau des taux avait changé. Ils sont maintenant comparés tels quels ; le tableau
   affiché ne change pas. `modules/boutique/src/client/java/fr/heraliz/casino/client/ViewBinder.java` l.269-301 (`rates`).

## À décider par le propriétaire

Rien de cela n'est grave ; chaque point est petit et sans risque pour l'argent.

1. **`/boutique` demande le solde au site à chaque ouverture (faible).** Contrairement à l'hôtel des ventes (corrigé ce
   matin : une demande toutes les 5 s), l'écran de la boutique interroge le site à chaque `/boutique`. Le jeu limite déjà
   les commandes tapées (environ une par seconde par joueur), donc c'est bien moins grave que le PNJ de l'hôtel des ventes.
   Proposition : même règle que l'hôtel des ventes (dernier solde connu pendant 5 s). Gain : moins de demandes au site,
   qui limite tout le serveur (une seule adresse). Risque : le solde affiché peut avoir 5 s de retard (il est relu après
   chaque achat). `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/BoutiqueServeur.java` l.187.
2. **Menu `/kit` : 16 kits au plus (faible, à savoir).** Le paquet du menu n'envoie que les 16 premiers kits visibles
   (`ReseauGrades.java` l.34, `MAX_KITS`), alors que l'éditeur des grades permet 8 kits par grade. Avec un kit par grade
   (cas actuel probable), rien ne se voit. Si un jour un joueur voit plus de 16 kits (Survivant + 3 grades de la boutique +
   Soutien avec plusieurs kits chacun), les derniers n'apparaîtraient pas, sans erreur. À dire seulement si vous prévoyez
   beaucoup de kits : on monterait la limite (jars joueurs et serveur ensemble).
3. **Onglet Économie du panel (Fondateur) : une erreur 500 du site affichée « refusé » (faible).** Quand le site répond par
   une erreur interne au lieu de ne pas répondre, l'onglet dit « refusé (500) » alors que le résultat n'est pas sûr (le
   panel ne redemande le statut que sans réponse). Proposition : traiter un 500 ou un 503 comme « pas de réponse » (statut
   redemandé). `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/PanelEconomie.java` l.383-416.
4. **`/heraliz loot fichier <caisse>` sans le contrôle /login (très faible).** Les autres commandes d'opérateur des caisses
   demandent d'être connecté sur AuthMe ; celle-ci (op 2) remet seulement le butin du fichier officiel. AuthMe bloque déjà
   les commandes avant le /login : aucun danger, seulement une incohérence.
   `modules/boutique/src/serveur/java/fr/heraliz/crates/command/HeralizCommand.java` l.121-132.

Les propositions de la première relecture que vous n'avez pas choisies restent telles quelles (tablette du casino à l'op 4,
liste des refus de `/trade`, messages privés vers un membre du staff caché) : elles ne sont pas reproposées ici.

## Vérifié sans problème

- **Changements du jour** : voir le tableau en haut (5 commits relus ligne à ligne).
- **Liens par réflexion vers la boutique** (signatures relues dans les sources des deux côtés) : `GradesApi` (`perm`,
  `rangBoutique`, `staff`, `cache`, `voitLesCaches`, `authentifie`, `fondateur`, `gradeAfficheTextes`), `ClesApi`
  (`crediter`, `crediterUneFois`, `wipe`, `wipeReouverture`), `CapsulesApi` (`solde`, `surLui`, `wipe`), `HdvApi`
  (`ouvrir`, `wipe`), `EchangeServeur.wipe`, `Bon.exemplaire` : toutes présentes, publiques, mêmes paramètres.
  `outils/verif_reflexion.py` : 0 erreur sur tout le dépôt.
- **Bons** : chaque appel de `Bon.exemplaire` (caisses, kits, marchands, échanges de Varga) part d'un modèle du serveur,
  jamais d'un objet du joueur ; un bon se pose seul (pile de 1) ; signature et usage unique vérifiés avant tout effet.
- **`/trade`** : cases recomparées à chaque tick, sac à dos impossible à ouvrir depuis une case de l'inventaire (contenu
  écrit dans l'objet à chaque changement), objets en dépôt avant l'appel au site, livraisons au spawn dans des cases vides.
- **Boutique en jeu** : un seul achat à la fois par joueur, référence écrite avant le débit, joueur parti pendant
  l'achat = remboursé, commandes jouées par la console avec un pseudo limité par AuthMe.
- **Hôtel des ventes** : achat en hérals débité et écrit avant la remise ; achat en PB avec référence notée avant l'appel ;
  livraisons seulement vivant, au spawn, hors match, dans des cases vides.
- **Caisses** : clé prise sur le compte et écrite avant le tirage, session numérotée, distance et monde revérifiés,
  4 ticks entre deux ouvertures, butin de l'éditeur borné, lignes existantes reprises du serveur (jamais du jeu).
- **Casino** : paquet d'action refusé hors de portée, en spectateur, ou machine pas prête ; caisse : on ne compte et on
  ne prend que des objets sans données, place vérifiée avant de payer ; distributeur : 250 ms entre deux opérations.
- **Panel du staff et commandes données par le Fondateur** : rang lu du grade et du /login, sanctions jamais sur un rang
  égal ou plus haut, raisons nettoyées des options de LiteBans, sous-commandes d'admin jamais données ; les actions du
  panel jouées avec les droits d'admin restent jugées comme des actions du serveur par le noyau (correctif du jour du
  noyau, `66233ddf8`, cohérent avec la boutique).
- **Staff caché, Tab, chat** : rien d'un caché n'est envoyé aux joueurs hors du staff ; `/tpa`, `/trade` le disent
  introuvable ; le chat n'accepte qu'un canal de 0 à 2 et retire les codes de couleur.
- **Lenteurs du serveur** : rien de lourd à chaque tick dans la zone (zones VIP tous les 4 ticks, Tab et staff caché
  toutes les 2 s, objets en attente toutes les 5 s, casino seulement dans le monde du spawn) ; appels au site et au bot
  toujours sur un autre fil, réponses traitées sur le fil du serveur.
- **Textes** : `outils/verifier_langues.py` : 0 erreur (les messages ajoutés aujourd'hui réutilisent des clés déjà
  traduites en français, anglais et russe).

## À tester en jeu

- [ ] **`/tpa` normal** : A demande B, B accepte, A arrive au bout de 15 s ; deuxième `/tpa` de A : « Prochaine
      téléportation dans … ».
- [ ] **`/tpa`, ancien abus** : A demande B, B accepte ; pendant les 15 s, A demande C ; une fois A arrivé chez B, C
      accepte : A lit « Prochaine téléportation dans … », C lit « A ne peut pas venir pour le moment », A ne bouge pas.
      Un membre du staff (op connecté) : aucune recharge, comme avant.
- [ ] **Boutique** : acheter un article plus cher que son solde de PB (solde « — » à l'ouverture, ou PB dépensés sur le
      site entre-temps) : « solde insuffisant » tout de suite, jamais « site injoignable » ; un achat normal marche comme
      avant.
- [ ] **`/trade` avec des PB** : proposer plus de PB qu'on n'en a sur le site (en les dépensant sur le site pendant
      l'échange) : l'échange se ferme, chacun récupère ses objets au spawn tout de suite, message du solde insuffisant.
- [ ] **Casino** : le tableau des taux de la caisse s'affiche comme avant ; changer la langue du jeu : il suit ; régler la
      caisse avec la tablette : le tableau suit.
- [ ] Toujours à faire (première relecture, `a1_boutique.md`) : tablette et éditeur des caisses avec /login, `/boutique
      abonnement` pour un joueur sans op, objet d'une caisse quand on meurt pendant la roue, `/kit` bot arrêté,
      `/avantages nick`.
- [ ] **Journal du serveur** : aucune erreur nouvelle de `heraliz_grades`, `heralizcasino` ou `heralizcrates`.

Pas testé : tout en jeu (aucune compilation ici : fabrication et contrôles sur GitHub seulement).

## Fin du 21/11 (choix du propriétaire : « tout terminé à 100 % »)

En ligne avant cette suite : Boutique 1.7.4 (`main` = `1607d9655`). Fait dans le commit **`500ee5957`** (Boutique
**1.7.5**). Aucun canal réseau ne change : un jeu de joueur d'avant se connecte toujours (le nouveau jar apporte seulement
les deux nouveaux textes en anglais et en russe ; un vieux jeu les montre en français).

Fait :
1. **Tablette du Directeur du casino** (proposition n° 1 de `a1_boutique.md`) : la page de la caisse (objets et prix du
   change, entrée du casino) et les réglages des machines (probabilités, gains, mises) demandent maintenant l'opérateur
   de **niveau 4** connecté sur AuthMe, comme `/casino jetons donner` ou `/eco donner`. Un opérateur de niveau 2 ou 3 qui
   enregistre ces pages lit « Caisse et réglages des machines : réservés aux opérateurs de niveau 4 connectés. » et rien
   ne change. Le reste de la tablette ne change pas : lumières des machines et statistiques au niveau 2 connecté, comme
   avant. `modules/boutique/src/serveur/java/fr/heraliz/casino/net/ReceptionServeur.java` l.67-76 ; texte
   `message.heralizcasino.admin_niveau4` (français, anglais, russe).
2. **Staff caché et messages privés** (« le cacher partout ») : `/msg`, `/tell` et `/w` (commandes de Minecraft) vers un
   membre du staff caché, tapées par un joueur qui ne voit pas le staff caché, reçoivent **la réponse de Minecraft pour un
   joueur absent** (« Aucun joueur n'a été trouvé », en rouge) et rien n'est envoyé. Le staff connecté garde l'accès.
   `/r` (noyau) passe par `/msg` : même règle, sans rien changer dans le noyau. Rien à faire pour `/teammsg` et `/tm` :
   le noyau les a retirées (`RETIREES_DEFAUT`). **Suggestions de pseudo (touche Tab)** de ces commandes : vérifié dans le
   code, elles sont faites par le jeu du joueur à partir de sa liste Tab, d'où le staff caché est déjà retiré (Bukkit
   `hidePlayer`, ou paquet « joueur retiré » sans Bukkit) : rien à changer. `/list` ne répond rien aux joueurs qui ne
   sont pas op (règle du monde `sendCommandFeedback = false`).
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/VanishServeur.java` l.79-81, l.319-362 (`messagePrive`).
3. **`/boutique`** (proposition n° 1 de cette validation) : le solde de PB est demandé au site au plus une fois toutes les
   5 s par joueur ; rouvert entre-temps, l'écran s'ouvre tout de suite avec le dernier solde connu, relu après chaque
   achat (débit, refus avec solde, remboursement). Oublié à la déconnexion.
   `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/BoutiqueServeur.java` l.65-71, l.112-127, l.210-227 ;
   `ServeurGrades.java` l.189.
4. **Onglet Économie du Fondateur** (proposition n° 3) : une erreur 500 ou 503 du site s'affiche « Erreur du site (500) :
   résultat incertain, vérifie sur le site avant de recommencer. » au lieu de « Refusé par le site : rien n'a été fait »
   (peut-être faux) ; pareil pour la simple lecture d'un solde. `PanelEconomie.java` l.264 et l.409-415 ; texte
   `heraliz_grades.staff.msg.eco_pb_erreur_site` (français, anglais, russe).
5. **`mods.toml`** : noyau **1.1.5** au moins pour le casino (relevé de la validation v10, l.92), et aussi pour les grades
   (l.31 : arrivées des téléportations du noyau, appelées directement depuis le 19/11 par `/tpa`, `/warp` et le panel) et
   les caisses (l.131 : attentes des matchs, `Matchs.inventaireRetenu`, 19/11), qui avaient le même oubli. Sans effet tant
   que tous les jars partent ensemble ; un vieux noyau laissé seul donnerait un message clair au démarrage au lieu d'un
   plantage au premier appel. `modules/boutique/src/commun/resources/META-INF/mods.toml`.

Pas fait (choix du propriétaire) : `/trade`, `/kit` à 16 kits, `/heraliz loot fichier`.

Remarque, à décider si besoin : un membre du staff caché qui écrit lui-même à un joueur par `/msg` se montre à ce joueur,
mais le joueur ne peut pas lui répondre (« Aucun joueur n'a été trouvé », comme demandé : le caché est absent pour les
joueurs). Si vous voulez qu'un joueur puisse répondre pendant quelques minutes au staff caché qui lui a écrit le premier,
c'est un petit ajout ; pour parler à un joueur sans se montrer, le bouton « Message » du panel (« [Staff] … ») reste le
moyen prévu.

Contrôles faits ici : `outils/verifier_langues.py` (0 erreur), relecture ; aucune compilation ici (fabrication et
contrôles sur GitHub).

À tester en jeu :
- [ ] **Tablette, opérateur de niveau 4 connecté** : régler une machine, puis la page de la caisse (prix d'un objet,
      entrée) : « Réglages enregistrés », comme avant ; lumières et statistiques : comme avant.
- [ ] **Tablette, opérateur de niveau 2 ou 3** (s'il y en a) : enregistrer une machine ou la caisse : message en rouge
      « réservés aux opérateurs de niveau 4 connectés », rien ne change ; les lumières s'enregistrent encore.
- [ ] **Staff caché** (S caché, A joueur hors du staff, B autre staff) : A tape `/msg S salut`, puis `/tell S salut` et
      `/w S salut` : « Aucun joueur n'a été trouvé » en rouge, exactement comme `/msg <pseudo d'un absent>` ; S ne reçoit
      rien. B tape `/msg S salut` : S le reçoit. A tape `/msg ` puis Tab : le pseudo de S n'est pas proposé. S se montre :
      `/msg S` de A marche de nouveau.
- [ ] **`/boutique`** : ouvrir, fermer, rouvrir aussitôt : le solde s'affiche tout de suite ; acheter un article : le
      solde suit ; rouvrir 10 s plus tard : solde relu.
- [ ] **Onglet Économie** (Fondateur) : donner ou retirer des PB, voir un solde : comme avant (si le site renvoie une
      erreur 500 : « résultat incertain, vérifie sur le site »).
- [ ] **Démarrage du serveur** : aucune ligne « dépendance manquante » pour `heraliz_grades`, `heralizcasino` ou
      `heralizcrates`.

Pas testé : tout en jeu.
