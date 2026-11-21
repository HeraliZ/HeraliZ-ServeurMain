# Audit global du 21/11 — zone a5 : clans, PNJ, événements, récolte

Modules relus : `modules/clans` (heraliz_clansetbases : hérals et comptes, clans, banque, coffre du clan, arbre et
niveaux, territoires et captures, vagues d'assaut, saison et points, profils `/profil` `/money`, classements, envoi des
tags de clan par différences et le staff caché du 21/11, carte), `modules/pnj` (heraliz_quetes : Varga et ses quêtes,
quêtes du jour, marchands et achat avec la quantité, Passeur, rachat de Mila, échanges, recycleurs du spawn et de la
carte, bourses de hérals et leur registre, éditeur des marchands, `/wipe`), `modules/evenements` (Directeur, largage
public, hélicoptère, objets d'événement signés) et `modules/recolte` (arbres et gisements). Relecture du code côté serveur
d'abord (paquets reçus des joueurs, argent et objets, sauvegardes, plantages, ce qui tourne à chaque tick), puis les
liens avec les autres modules (noms de classes et de méthodes lus par réflexion, vérifiés un par un) et les affichages
chez les joueurs.

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, contrôles des textes (`outils/verifier_langues.py`,
0 erreur) et des listes envoyées par différences (`outils/verif_synchro.py`, 0 erreur) sur ce PC, puis la fabrication et
les contrôles sur GitHub. Versions : **Clans 3.19.2**, **Quêtes 1.24.4**, **Événements 1.3.3** (le launcher les donne aux
joueurs ; aucun canal réseau ne change, jeu et serveur restent compatibles). Récolte : rien à changer.

En bref : **12 points trouvés**, aucun grave (aucune duplication d'objets ni d'argent trouvée, aucun plantage courant) :
6 corrigés (4 moyens, 2 petits), 6 à décider (1 moyen, 5 petits).

---

## Corrigé

### 1. `/bourse donner` créait de l'argent avec un droit trop bas (moyen, sécurité) — commit `1a03ce3b`

En simple : la commande du staff `/bourse donner <joueur> <hérals>` fabrique une bourse de hérals (jusqu'à 1 000 000
à chaque fois), que Mila rachète comme de l'argent. Elle demandait seulement le niveau « op 2 ». Or votre règle du 28/10
pour créer de l'argent (`/eco donner`) est : op 4, connecté sur AuthMe, ou la console. `/bourse donner` contournait
cette règle. Maintenant elle suit la même règle (refus clair : « Connecte-toi d'abord avec /login. »). `/bourse info`
(lire le registre) reste op 2. Rien ne change pour les joueurs.

Pour les développeurs : `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/Commandes.java` l. 63-93 (`donner` :
`requires(hasPermission(4))` + `Ponts.authentifie` pour un joueur) ; texte `heraliz_quetes.bourse.cmd.login` ajouté par
`modules/pnj/outils/langues.py` (3 langues) ; `docs/COMMANDES.md` et `docs/COMMANDES_INVENTAIRE.md` (refait par
`outils/commandes.py`).

### 2. Fichier des comptes illisible : l'argent pouvait disparaître pour de bon (moyen, perte) — commit `1a03ce3b`

En simple : si `heralisz_economie/comptes.json` ne se lit pas au démarrage (disque, fichier abîmé), le serveur part avec
des comptes vides et **n'écrit plus rien** pour protéger les vrais soldes, jusqu'à la réparation (le staff est prévenu).
Mais pendant ce temps, le jeu continuait à « payer » en mémoire : une vente à Mila prenait les objets, un retrait de la
banque du clan vidait la banque (elle, bien enregistrée), une quête payée était finie ; puis la relecture du fichier
réparé (`/recharger herals`) effaçait ces hérals jamais écrits. Objets ou banque perdus pour rien. La description du code
disait pourtant « transactions bloquées ». Maintenant, dans ce cas seulement, l'économie se dit « indisponible » :
Mila, la banque du clan, la création de clan, l'hôtel des ventes refusent avec leur message habituel, une quête payée
attend (« récompense en attente »), jusqu'à la relecture réussie ou `/eco sauvegarder --forcer`. Quand le fichier se lit
normalement, rien ne change.

Pour les développeurs : `modules/clans/src/serveur/java/fr/heralisz/economie/api/EconomieApiServeur.java` l. 31-44
(`disponible()` faux si `ComptesStore.chargementEchoue()`), doc de `EconomieApi.disponible()` (commun). Les appels qui
ne demandent pas `disponible()` avant de payer restent comme avant (ils écrivaient déjà dans le vide).

### 3. Staff caché : son clan le voyait « hors ligne… vu à l'instant » (moyen, suite du 21/11) — commit `1a03ce3b`

En simple : depuis ce matin, un membre du staff caché est montré « hors ligne » à son clan. Mais l'écran du clan affiche
aussi « vu il y a … » et le temps de jeu de chaque membre : son « vu le » était remis à l'heure à sa connexion puis chaque
minute, et son temps de jeu montait chaque seconde. Son clan lisait donc « hors ligne · vu il y a moins d'une minute »,
ce qui le trahissait. Maintenant son clan voit son « vu le » et son temps de jeu d'avant sa partie en cours, comme pour un
joueur vraiment hors ligne (même règle que son profil). Le staff le voit toujours en ligne. Les données du clan ne
changent pas (seul l'affichage pour les autres membres).

Pour les développeurs : `modules/clans/src/serveur/java/fr/heralisz/clans/server/Sync.java` l. 85-103 (`AVANT_SESSION`,
`debutSession`, `finSession`, vidé à l'arrêt) et l. 460-478 (`pour()` : membre caché vu par un joueur hors du staff) ;
`ClanEvents.java` l. 149 (connexion, avant `m.vuLe = now`) et l. 200 (déconnexion).

### 4. Chez les marchands, plus de 4 achats, ventes ou échanges par seconde (moyen, lenteurs) — commit `f112b1d9`

En simple : chaque achat, vente ou échange chez un marchand enregistre tout de suite le joueur (et souvent les comptes)
sur le disque, pendant le tick du serveur. Rien n'en limitait le nombre : un jeu modifié qui en envoyait des dizaines par
seconde (une brindille à 2 hérals à la fois, revendue aussitôt) pouvait faire ramer tout le serveur. Maintenant, 4 par
seconde et par joueur au plus, comme le recycleur de la carte et la banque du clan ; une demande en trop est ignorée
(rien n'est pris ni donné). Le panneau d'achat n'en envoie jamais plus de deux par seconde : rien ne change pour un
joueur normal.

Pour les développeurs : `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/Marchands.java` l. 53-73 (`cadence`,
oubliée à la déconnexion et à l'arrêt), appelée au début de `achat` (l. 186), `vente` (l. 251) et `Echanges.troc` (l. 179).

### 5. Événements : une erreur rare ne peut plus arrêter tout le serveur (petit, plantage) — commit `1a03ce3b`

En simple : si un événement en cours tombe en erreur, le Directeur l'annule. Mais si l'annulation elle-même tombait en
erreur (même état cassé), ou si un départ lancé par le planning tombait en erreur, l'erreur sortait du tick et Minecraft
arrête alors le serveur entier. Maintenant elle est notée dans le journal du serveur ; un départ en erreur compte comme
un refus (l'attente repart normalement, comme la règle du 27/10).

Pour les développeurs : `modules/evenements/src/serveur/java/fr/heraliz/evenements/serveur/Directeur.java` l. 114-124
(`annuler` protégé) et l. 268, 284, 292-305 (`lancerDuPlanning`).

### 6. Panneau de zone : plus de textes refaits à chaque image (petit, images par seconde) — commit `5ce63148`

En simple : tant qu'un joueur est dans une zone de territoire, le petit panneau en haut à gauche traduisait et mettait en
forme trois ou quatre textes à chaque image (à qui est la zone, le drapeau, l'état de la capture), puis les comparait à
ceux déjà affichés. Ils ne sont maintenant refaits qu'à l'arrivée d'un nouvel état de la zone (le serveur ne l'envoie que
s'il change), quand on change de clan ou de langue. Rien ne change à l'écran.

Pour les développeurs : `modules/clans/src/client/java/fr/heralisz/clans/client/gui/hud/ClanHud.java` l. 112-140
(`barreZone` : cache par paquet `TerritoireHudS2C`, clan et langue ; `preparer` pour les textes et la barre).

---

## À décider par le propriétaire

### P1. `/territoire capturer` (op 2) donne des points de saison et de l'XP (moyen)

Une capture forcée par le staff (`/territoire capturer <zone> <tag>`) passe par la même fonction qu'une vraie capture :
elle donne au clan les points de saison d'une capture (jusqu'à 40 par jour et par clan), l'XP de capture (150, jusqu'à 450
par jour) et fait avancer ses quêtes de clan. Or vos règles disent qu'un geste du staff ne rapporte pas de points (largage
du staff « sans points », staff en créatif sans points, « le staff qui joue avec ses droits » dans les abus), et la saison a
des prix réels.
- Proposition : capture du staff sans points ni XP (comme un largage du staff) ; pour dédommager un clan, `/saison points`
  existe déjà (op 4, noté au journal).
- Risque : aucun pour les joueurs. Bénéfice : plus aucun point de saison donné par une commande d'op 2.
- Code : `Territoires.capturerAdmin` (l. 916) appelle `capturer(...)` qui appelle `Saison.capture` (l. 624).

### P2. Recycleur de la carte : un objet en double si le disque est plein au mauvais moment (petit)

Chaque changement de la machine est écrit tout de suite dans son fichier. Si cette écriture rate (disque plein) juste
après qu'un joueur a repris un objet, le fichier garde encore l'objet ; la machine se ferme (règle prévue) mais, au
redémarrage (ou au rechargement de son coin de carte), elle relit ce fichier : l'objet est là ET chez le joueur. Il faut
un disque plein sur le Ptero, et à ce moment-là bien d'autres sauvegardes de Minecraft ratent aussi.
- Proposition : réessayer l'écriture plus tard (et à l'arrêt) au lieu de garder la machine fermée jusqu'au redémarrage ;
  sinon, effacer le fichier (perte plutôt que copie).
- Code : `RecycleurCarteEntiteServeur.ecrire()` (l. 233 : `bloquee = true`).

### P3. `/profil` : tout le fichier des profils est refait chaque minute sur le fil du serveur (petit, perf)

`heralisz_clans/profils.json` garde une fiche pour chaque joueur connu (tous les comptes de hérals compris) ; il est remis
en JSON en entier sur le fil du serveur à chaque minute où un chiffre a changé (en jeu : toujours). Avec quelques milliers
de joueurs connus : quelques millisecondes par minute. Comme pour les clans le 19/11 : ne refaire que les fiches changées.
Code : `Profils.sauvegarder()` (l. 226).

### P4. Demandes d'écran sans cadence côté serveur (petit)

Le journal des quêtes (touche J), les actions du journal et l'écran de la saison des clans se recalculent à chaque demande
du jeu ; un jeu modifié peut les demander en boucle (calcul et envoi, sans disque). Une petite cadence (quelques par
seconde) les rendrait sans effet. Code : `ServeurQuetes.init` (`DEMANDE_JOURNAL`, `ACTION`), `ServerHandlers.simple`
(`CLASSEMENT`, 5 par seconde aujourd'hui).

### P5. Staff caché : restes très faibles (très petit)

Le total « temps de jeu » du clan (`/clan info`) continue de monter pendant qu'un membre du staff est caché (total de tous
les membres, difficile à lire) ; un staff caché qui rejoint un clan pendant sa partie montre son arrivée dans l'écran du
clan. Rien à faire sauf si vous voulez l'invisibilité totale jusque-là.

### P6. Dépôt au-delà du plafond des comptes (très petit)

Un compte ne dépasse pas 10 000 000 hérals : un dépôt au-delà est coupé sans que l'appelant le sache. Une vente à Mila
d'un joueur déjà au plafond prend donc les objets sans tout payer. Il faudrait 10 millions de hérals. Code :
`EconomieApiServeur.deposer` (l. 88-98).

Déjà notés par d'autres audits, pas reproposés : coffre du clan et planque après un plantage (section 24 et 42 de la fiche
d'essais), panneau d'achat quand le staff retire un article pendant l'achat (section 41).

---

## Vérifié sans problème

- **Hérals** (`ComptesStore`, `EconomieApiServeur`) : transferts tout ou rien, journal d'écriture rejoué au démarrage,
  fichier complet écrit en fond puis morceaux du journal effacés, fichier illisible jamais écrasé ; `/herals payer`
  (cadence, plafond), `/eco` (op 4 + AuthMe, bornes, journal).
- **Banque du clan** : une opération par seconde, droits, plafonds, côté débité écrit en premier (journal du clan puis
  comptes, ou l'inverse) ; dissolution (refus si la banque ne tient pas sur les comptes, coffre rendu au chef) ; `/clan
  don` (joueur écrit sans l'objet avant l'XP, numéro anti-copie de Spartan) ; arbre et quêtes de clan (banque et état dans
  la même ligne du journal du clan).
- **Coffre du clan** : objets interdits, droits déposer / prendre (Maj + clic compris), fermé en combat, à terre, en match
  et sur la carte, menu fermé si le joueur quitte le clan ; fichier illisible jamais écrasé.
- **Bourses** : registre écrit avant que la bourse existe ou change, copie payée une seule fois (même numéro), montant
  gonflé jamais payé plus que le plafond, deux bourses de même numéro jamais fusionnées, refusées par le recycleur,
  l'hôtel des ventes et les échanges, `/wipe tout`.
- **Marchands et Mila** : prix confirmé revérifié, place vérifiée avant de payer, ventes dédoublonnées, objets d'événement
  notés vendus avant le paiement, refus en match / combat / à terre / sur la carte / inventaire mis de côté par le
  ClashWar, écritures dans l'ordre (joueur puis comptes, ou l'inverse selon le sens) ; échanges calculés sur une copie ;
  éditeur des marchands (op 4, indices bornés).
- **Recycleurs** : spawn (contenu gardé avec le joueur, rendu à la fermeture, à la déconnexion, après la mort ou un
  plantage, jamais pendant un ClashWar), carte (contenu hors du monde écrit à chaque clic dans l'ordre sûr, échanges avec
  la barre refusés, cases d'un autre joueur refusées, 4 clics par seconde, aucun accès par entonnoir).
- **Quêtes** : récompenses (progression et joueur écrits avant les hérals ; fichier jamais remplacé par un fichier à
  moitié écrit), étapes « donner » et « payer », dialogues (le jeu ne choisit qu'un numéro d'option, tout est revérifié),
  quêtes du jour (tirage noté avant la remise, clés par `ClesApi.crediterUneFois`), objets à rendre.
- **Saison** : créatif / spectateur / bots refusés partout, plafonds du jour, kills (même IP, victime armée, même victime
  ×1 / ×0,5 / 0, jamais sous 0), captures (12 h, 48 h, 40 par jour), événements (récompense notée sur le disque avant les
  points, plafond par clan), fichier illisible = saison en pause.
- **Événements** : largage (point tiré en tâche de fond, rien lu sur le disque par le fil du serveur, points une seule
  fois), hélicoptère (butin gardé au redémarrage pendant le feu, boîte noire « essai »), objets signés (clé hors du dépôt,
  numéro vendu noté avant le paiement), état illisible (numéros neufs, jamais deux fois les mêmes points).
- **Paquets reçus des joueurs** : tous traités sur le fil du serveur, tailles bornées au décodage, droits et distance
  revérifiés (clans : une action toutes les 200 ms ; quêtes : session de dialogue, marchand ouvert à 8 blocs).
- **Lenteurs** : territoires (un passage par seconde sur les joueurs, rien à vide), assauts et ravitaillement (rien à
  vide), Sync (tags relus toutes les 2 s, rien ne part si rien ne change), hologrammes, récolte (une place contrôlée par
  tick au plus), Directeur (une comparaison par seconde hors événement).
- **Liens avec les autres modules** (noms lus par réflexion, tous présents avec les mêmes paramètres) : `SaisonApi`
  (`pve`, `evenement`, `pointsEvenement`, `active`, `clanDe`, `aUnCoeur`, `raid`, `perdreTout`, `points`, `rangs`,
  `palier`), `EvenementsApi` (`coffreOuvert`, `objetDuCoffre`, `refusOuverture`, `prixObjet`, `pointsObjet`,
  `raisonSansPoints`, `vendreObjet`, `objetEssai`), `QuetesApi.bourses`, `ClanStore.get/clanDe/lectureIncomplete`,
  `ClanService.prefixe/chat`, `Sync.ecranOuvert/envoyerEtat`, `ClansNet.toPlayer`, `Packets$OuvrirS2C(String)`,
  `ClansApi.clanDe` (anticheat), `TerritoireEvents$Capture.capteurs`, `GradesApi.cache/voitLesCaches/authentifie`,
  `Tournois.enCours/courant`, `Tournoi.actif/simulation`, `ClaimManager.getAt`, `CoeurApi.clanAUnCoeur`,
  `DepartServeur.regionPresente`, `SupplyCrateEntityServeur.caisseEnAttente`, `Services.BACKPACK.getBackpackStack`.
- **Textes** : les trois langues ont les mêmes clés (`outils/verifier_langues.py` : 0 erreur, 8 avertissements connus :
  noms propres).

---

## À tester en jeu

- [ ] `/bourse donner <joueur> 100` : par un op 4 connecté, la bourse arrive ; par un op 2 ou 3, la commande n'est pas
      proposée ; à la console, elle marche. `/bourse info` : op 2 comme avant.
- [ ] Rien ne change pour Mila, la banque du clan, les quêtes et l'hôtel des ventes quand le serveur démarre normalement
      (le cas « fichier des comptes illisible » ne se provoque que sur le serveur d'essai : abîmer une copie de
      `comptes.json`, démarrer, vérifier que Mila et la banque refusent « économie indisponible », puis `/recharger herals`
      après réparation : tout remarche).
- [ ] Staff caché (S dans un clan, C membre du même clan hors du staff) : S se connecte caché ; chez C, l'écran du clan
      montre S « hors ligne » avec son ancien « vu il y a … » et son ancien temps de jeu, qui ne bougent pas pendant 5 min.
      Un autre staff voit S en ligne. S se montre puis se déconnecte : chez C, « vu il y a … » repart de la déconnexion.
- [ ] Marchands : acheter, vendre et échanger normalement, aussi vite que possible à la souris : tout passe. Rien
      n'est perdu ni donné en double.
- [ ] Événements : largage du planning et `/event lancer largage` comme avant ; journal du serveur sans erreur.
- [ ] Territoires : entrer dans une zone, capturer, contester, être protégé : le panneau en haut à gauche suit comme avant
      (pourcentage, « Protégée 1 h 52 », « drapeau à 45 m ») ; changer la langue du jeu dans une zone : le panneau suit.

Pas testé : tout en jeu (aucune compilation ici : contrôle de la branche sur GitHub).

---

## Suite du 21/11 (choix du propriétaire)

Vos choix : « Sécurités en plus » et « Allègements du serveur ». **P1 refusée** : les captures du staff
(`/territoire capturer`) gardent leurs points de saison et leur XP, rien n'est changé. P5 et P6 ne sont pas faites (pas
demandées). Versions : **Quêtes 1.24.5**, **Clans 3.19.3** (le launcher les donne ; aucun canal réseau ne change).
**Rien n'a été testé en jeu** : la fabrication et les contrôles sur GitHub seulement.

### Fait

**P2. Recycleur de la carte : jamais d'objet en double après une écriture ratée** — commits `24397a2d3` et `247ca990e`

En simple : chaque changement de la machine est écrit tout de suite dans son fichier. Si cette écriture ratait (disque
plein) juste après qu'un joueur avait repris un objet, l'ancien fichier gardait encore l'objet et la machine restait
fermée jusqu'au redémarrage, qui relisait cet ancien fichier : l'objet était là ET chez le joueur. Maintenant, mieux vaut
perdre que copier : dès qu'une écriture rate, l'ancien fichier est effacé (un effacement passe même disque plein), la
machine se ferme et son écriture est réessayée toutes les 5 s, quand son coin de carte se décharge et à l'arrêt du
serveur ; dès qu'elle passe, la machine rouvre telle quelle (rien de perdu). Un plantage avant fait perdre ce qu'elle
contenait, jamais un objet en double. Un fichier illisible au chargement, lui, n'est toujours jamais écrasé.

Pour les développeurs : `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/RecycleurCarteEntiteServeur.java` :
`ecrire()` (l. 230, ancien fichier effacé, `ecritureRatee` distinct de `bloquee` = fichier illisible), `ecritureReussie()`
(l. 280), `effacer()` (l. 287, le journal ne parle que de l'ancien fichier, une fois), `reessayerEcritures()` (l. 307, rien
tant que la liste est vide), `arret()` (l. 319), `onChunkUnloaded()` (l. 150, aucun bloc changé pendant le déchargement),
`ferme()` (l. 348) ; branchés dans `ServeurQuetes` (l. 147 et 274).

**P3. Profils : plus tout le fichier refait sur le fil du serveur chaque minute** — commit `b93e323b6`

En simple : `heralisz_clans/profils.json` garde une fiche par joueur connu ; chaque minute où un chiffre avait changé
(toujours, en jeu), tout le fichier était remis en forme pendant le tick du serveur. Maintenant le serveur ne fait qu'une
copie des chiffres, très rapide ; la mise en forme et l'écriture se font à côté, sur le fil d'écriture des clans. Même
fichier qu'avant ; à l'arrêt, le serveur attend la fin de l'écriture : rien n'est perdu au redémarrage ni à l'arrêt.

Pour les développeurs : `modules/clans/src/serveur/java/fr/heralisz/clans/profil/Profils.java` `sauvegarder()` (l. 225-280 :
tableaux `ids` / `noms` / `chiffres` relevés sur le fil serveur, JSON fait dans l'écriture en fond ; mêmes clés).

**P4. Petites cadences sur les écrans qui se recalculent à chaque demande** — commits `24397a2d3` et `b93e323b6`

En simple : un jeu modifié pouvait redemander en boucle le journal des quêtes, ses actions ou l'écran de la saison des
clans (chaque demande refait la liste et la renvoie). Maintenant : journal des quêtes et ses actions (suivre, ne plus
suivre, abandonner, visite du Guide, quitter le tutoriel), 4 demandes par seconde et par joueur au plus ; écran de la
saison, une fois par seconde (l'onglet Saison ne le redemande que toutes les 2 s). Une demande en trop est ignorée sans
message. Un joueur normal n'en envoie jamais autant.

Pour les développeurs : `ServeurQuetes.java` l. 56-62 (`DEMANDE_JOURNAL`, `ACTION`) et l. 125-143 (`demandeOk`, oublié à la
déconnexion et à l'arrêt) ; `modules/clans/src/serveur/java/fr/heralisz/clans/server/ServerHandlers.java` l. 41-61
(`classementOk`, oublié à la déconnexion) et l. 197-199 (`CLASSEMENT`).

### À tester en jeu

- [ ] Recycleur de la carte : déposer, recycler, reprendre, redémarrer : tout comme avant, rien en double ni en moins.
- [ ] (Facultatif, pour un développeur, serveur d'essai seulement : faire rater l'écriture.) Mettre deux objets dans la
      machine ; dans `heraliz_quetes/recycleurs/<monde>/`, à côté du fichier `<x>_<y>_<z>.dat` de la machine, créer un
      dossier `<x>_<y>_<z>.dat.tmp` avec un fichier dedans ; reprendre UN objet : la machine se ferme, le journal du serveur
      dit « machine fermée, ancien fichier effacé ». Supprimer ce dossier : sous 5 s, « machine rouverte », elle contient
      l'autre objet. Refaire (deux objets, en reprendre un), puis arrêter le serveur sans supprimer le dossier : au retour, la
      machine est vide et l'objet repris n'est que chez le joueur (rien en double) ; supprimer ensuite le dossier.
- [ ] `/profil` : temps de jeu, morts, zombies tués, extractions, conteneurs fouillés, séries et plus longue vie restent
      justes après un redémarrage et après un arrêt propre ; aucune ligne « écriture de … profils.json impossible » dans le
      journal du serveur.
- [ ] Journal des quêtes (J), suivre ou abandonner une quête, visite du Guide avec Espace : tout répond comme avant, même en
      cliquant vite.
- [ ] Écran du clan, onglet Saison : le classement et le barème s'affichent et se mettent à jour comme avant.
