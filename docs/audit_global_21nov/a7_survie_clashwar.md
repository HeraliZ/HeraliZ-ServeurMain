# Audit global du 21/11 — zone a7 : réanimation et ClashWar

Relu : `modules/survie` (mod `reanimation` : joueurs à terre, saignement, fouille avec les emplacements Curios,
réanimation, exécutions et leurs scènes, étiquette « reste allongé », API `Downed` / `DownedManager`) et
`modules/clashwar` (mod `heraliz_clashwar` : inscriptions, salle et arènes, matchs, **Coffre** = inventaire mis de côté
pendant le tournoi, spectateurs et `/clashwar regarder`, récompenses, classement, kits, structures, tableau de score),
plus leur branchement au noyau (`Matchs`, tag de combat, `ATerre`, clé `heralisz:en_match`, marque
`HeralizClashWarDepot`), sans toucher au noyau.

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code ligne à ligne des chemins serveur, vérification des
cibles lues par réflexion (dans nos sources et dans les jars de TaCZ, Curios et Forge 47.3.12), puis la fabrication et
les contrôles sur GitHub. Versions : **ClashWar 1.3.7**, **Réanimation 1.16.14** (seuls les jars du serveur changent
vraiment ; aucun canal réseau ne change : le launcher donnera les nouveaux jars, une ancienne version des joueurs reste
compatible).

En bref : **10 points corrigés** (1 grave, 4 moyens, 5 petits), 6 propositions à décider. Le grave était une
duplication encore possible au départ d'un ClashWar, malgré les correctifs de ce matin (sections 42 et 44 de la fiche
d'essais) : elle est fermée.

---

## Corrigé (commit `c532ae5e`)

### 1. Coffre du ClashWar : duplication par un écran ouvert pendant l'écriture du dépôt (grave)

En simple : au départ d'un tournoi (et à `/clashwar regarder`), le serveur note l'inventaire du joueur, l'écrit sur le
disque en tâche de fond (un ou deux ticks), puis vide le joueur. Pendant ces quelques ticks, le joueur est « figé »
(clics sur les blocs et les entités, objets, coups et commandes refusés), et au moment de vider, le serveur vérifie que
chaque case contient encore le même objet, en même nombre, avec la même arme / munition / accessoire / soin. Mais il ne
regarde pas le reste des données d'un objet (accessoires montés sur une arme, skin, StatTrak, usure). Or le coffre du
clan s'ouvre aussi depuis l'écran du clan (touche K, un simple paquet), sans clic sur un bloc : un jeu modifié pouvait,
pendant ces ticks, échanger dans le coffre du clan son fusil complet (lunette, silencieux, skin) contre le même fusil nu.
La vérification passait (même objet, même nombre, même arme) : le fusil complet revenait avec le dépôt **et** restait
dans le coffre du clan. Même chose avec une pièce d'armure neuve contre une usée.

Corrigé : pendant l'écriture du dépôt, tout écran qui s'ouvre (coffre du clan, sac, Curios, tout menu ouvert par un
paquet) est refermé aussitôt ; plus aucun échange avec un autre conteneur n'est possible. Un joueur honnête qui
ouvrirait son sac exactement à ce moment le voit s'ouvrir et se refermer (une ligne dans le journal).
Développeurs : `Coffres.ecran` (`PlayerContainerEvent.Open`, LOWEST), `modules/clashwar/.../moteur/coffre/Coffres.java:435`.

### 2. Coffre : déconnexion pendant l'écriture du dépôt = objets de la souris perdus (moyen)

En simple : ce que le joueur tient à la souris et ce qui est dans sa petite grille d'artisanat (2 x 2) est mis à part
au début du dépôt. S'il se déconnectait (ou si le serveur s'arrêtait) pendant les un ou deux ticks de l'écriture, il était
enregistré sans ces objets, puis le fichier du dépôt, qui les gardait, était effacé : perdus.
Corrigé : à la déconnexion (avant que Minecraft n'enregistre le joueur), ces objets lui sont rendus dans l'inventaire,
sinon à ses pieds (comme Minecraft le fait pour la souris et la grille), et le dépôt est abandonné comme avant.
Développeurs : `Coffres.deconnexion` (`Coffres.java:622`), appelé par `MoteurLogique.deconnexion` (ligne 222).

### 3. Coffre : retour sur un joueur mort = inventaire perdu (moyen, rare)

En simple : un retour de Coffre fait sur un joueur à l'écran de mort lui remettait son inventaire… qui disparaissait à
sa réapparition (Minecraft ne garde pas l'inventaire d'un mort). Ça peut arriver après un plantage : le joueur se
reconnecte dans le monde des arènes, son Coffre est rendu dans les 3 secondes, et s'il meurt entre-temps (vide,
étouffement dans une arène reposée), tout était perdu.
Corrigé : jamais de retour sur un joueur mort ; le dépôt reste sur le disque et lui est rendu juste après sa
réapparition (ou à sa reconnexion). La garde hors match et la bordure des arènes laissent aussi un mort tranquille.
Développeurs : `Coffres.java:850`, `MoteurLogique.reapparition` (ligne 210), `GardeHorsMatch.java:37`,
`BordureArenes.java:185`.

### 4. Coffre : retour sur un joueur à terre (moyen, rare)

En simple : même cas (hors match, après un plantage), mais le joueur est à terre. Celui qui le fouillait voyait arriver
dans la fouille tout l'inventaire rendu et pouvait le prendre ; et la téléportation vers le spawn, refusée à un joueur à
terre, le laissait dans le monde des arènes où il se vidait de son sang avec tout son stuff.
Corrigé : il est d'abord relevé (fouille fermée), puis tout lui est rendu. Développeurs : `Coffres.java:889`
(`ATerre.relever`, l'API du noyau prévue pour ça).

### 5. Spectateurs d'un match : téléportation vers n'importe quel joueur du serveur (moyen, triche sans logiciel)

En simple : un joueur éliminé passe en mode spectateur de Minecraft. Ce mode a un menu (touches 1 à 9) « se téléporter
vers un joueur » qui liste tout le serveur : on arrivait une fraction de seconde sur n'importe quel joueur de la carte,
avec sa position, les joueurs autour et sa base, avant d'être ramené dans l'arène. Corrigé : un spectateur de match ne
part plus vers aucun autre monde que celui des arènes (le staff de niveau 2 n'est pas concerné).
Développeurs : `Spectateurs.voyage` (`EntityTravelToDimensionEvent`, `Spectateurs.java:203`), branché dans
`MoteurLogique.java:77`. **À vérifier en jeu** : sur Mohist, une téléportation d'un monde à l'autre pourrait ne pas
passer par cet événement de Forge (voir la proposition D).

### 6. Coffre : ce qui ne tient pas tombait dans l'arène au retour (petit)

Ce qui ne tient ni dans l'inventaire rendu, ni dans la grille 2 x 2, ni à la souris (rare : par exemple le contenu d'un
établi ouvert au départ avec un inventaire plein) tombait aux pieds du joueur **avant** son retour, donc dans l'arène,
perdu pour lui. Maintenant après la téléportation, à l'arrivée. Développeurs : `Coffres.java:925`.

### 7. `/wipe` : objets tenus à la souris au départ du tournoi oubliés (petit)

Le wipe d'un joueur dont l'inventaire est dans un Coffre reprend l'inventaire et les Curios du dépôt (et garde les objets
à vie), mais pas ce qu'il tenait à la souris ou dans sa grille au départ : un couteau à vie tenu à la souris
disparaissait. Développeurs : `WipeClashWar.java:42`.

### 8. Tournoi : « équipe retirée » annoncé à une équipe qui joue quand même (petit)

Pendant les dépôts du départ vers la salle, un joueur qui se déconnectait faisait annoncer à ses coéquipiers « votre
équipe est retirée du ClashWar »… alors qu'elle partait quand même, sans lui. Le message n'est plus envoyé que s'il dit
vrai (avant le départ). Règle à confirmer : proposition B. Développeurs : `Tournoi.java:487`.

### 9. Réanimation : demandes d'exécution en rafale (petit, anti-abus)

Le jeu n'envoie qu'une demande d'exécution par appui maintenu (une demi-seconde au moins). Un jeu modifié pouvait en
envoyer des centaines par seconde ; chacune faisait un « coup d'essai » sur la victime et, en zone protégée, une ligne de
journal (journal noyé, serveur chargé). Maintenant : une demande par joueur toutes les 400 ms au plus ; rien ne change
pour un joueur honnête. Développeurs : `ActionPacketServeur.java:28` et `:65`, oubli à la déconnexion
(`ServerEventsServeur`).

### 10. Garde hors match et bordure des arènes sur un mort (petit)

Avec le point 3 : un joueur mort dans le monde des arènes n'est plus « renvoyé au spawn » chaque seconde (message et
téléportation d'un mort) ; il est traité à sa réapparition.

---

## À décider par le propriétaire (rien n'a été changé)

**A. Vérification du dépôt plus stricte (sécurité en plus).** Depuis le point 1, on ne connaît plus de moyen d'échanger
un objet pendant l'écriture. On pourrait quand même comparer aussi toutes les données d'un objet quand ce n'est plus le
même objet dans la case (un objet seulement modifié sur place, comme une arme qui tire, reste accepté). Bénéfice :
protège contre un moyen encore inconnu. Risque : si un mod remplace un objet tout seul pendant ces 1-2 ticks, le dépôt
est refait (au plus 5 fois, puis refusé, inventaire intact). Je conseille de le faire. **Fait le 21/11 (choix du
propriétaire) : voir « Suite du 21/11 » en bas.**

**B. Déconnexion pendant les dépôts du départ.** Règle du 15/11 : « un inscrit qui se déconnecte avant l'entrée dans la
salle retire son équipe ». Pendant les quelques secondes des dépôts, l'équipe partait déjà quand même, sans lui (seul le
message disait le contraire, corrigé au point 8). Voulez-vous que l'équipe entière soit retirée aussi dans ce moment-là
(il faudrait rendre le Coffre de ses coéquipiers déjà en salle), ou qu'elle joue sans lui (comme maintenant) ?

**C. Erreur d'un autre mod pendant un dépôt.** Si un autre mod plantait pendant le dépôt (fermeture d'un écran,
sauvegarde du joueur), l'erreur arrêterait le tick du serveur (plantage). Ces actions sont celles que Minecraft fait à
chaque sauvegarde : c'est très peu probable. Proposition : attraper l'erreur, la noter et laisser le joueur intact.
**Fait le 21/11 (choix du propriétaire) : voir « Suite du 21/11 » en bas.** B reste comme aujourd'hui (l'équipe joue
sans lui).

**D. Mohist et les changements de monde.** Le point 5 (et, déjà avant, la règle « un joueur à terre ne change pas de
monde » de la réanimation) s'appuie sur l'événement de voyage de Forge. Sur Mohist, une téléportation d'un monde à
l'autre (`/tp`, menu des spectateurs) passe peut-être par Bukkit sans cet événement. À essayer en jeu (liste
ci-dessous). Si ça ne marche pas : il faudra un petit mixin sur le paquet de téléportation des spectateurs.

**E. Petits envois réseau.** À chaque fois qu'un joueur sort de la vue d'un autre, la réanimation lui envoie un petit
paquet « plus à terre », même s'il n'était pas à terre (quelques dizaines d'octets, sans effet visible) ; le paquet
« spectateur suivant » n'a pas de limite de cadence. Gain faible, à faire plus tard si on cherche de la bande passante.

**F. Tirs pendant l'écriture du dépôt.** Un joueur qui tire pendant les 1-2 ticks de l'écriture récupère ces balles
dans son chargeur au retour (le dépôt garde le chargeur d'avant). Quelques balles au plus, au spawn sans PvP : je
propose de laisser comme ça.

---

## Vérifié sans problème

**Coffre du ClashWar, étape par étape (plantage et déconnexion à chaque moment) :**
- ordre « disque avant toute modification » : relevé sur le fil serveur, copie complète pour le fil d'écriture (aucun
  objet du jeu touché par ce fil, pas de course avec l'inventaire), écriture `.tmp` + `fsync` + relecture comparée +
  déplacement atomique ; marque posée seulement au vidage, avec l'enregistrement immédiat du joueur ;
- plantage pendant l'écriture : joueur intact sans marque, fichier jamais rendu (mis en enquête) ; plantage après le
  vidage mais avant l'enregistrement du joueur : idem ; après : rendu à la connexion ; retour : marque retirée et joueur
  enregistré **avant** l'effacement du fichier (jamais rendu deux fois, jamais perdu) ;
- déconnexion en file d'attente, pendant l'écriture (point 2), en salle, en match, pendant le retour (le fichier reste,
  rendu à la reconnexion) ; reconnexion rapide avec un nouvel objet joueur (fichier mis en enquête, rien en double) ;
- souris, grille 2 x 2, objets lâchés pendant l'écriture (retenus), contenu du sac (porté ou dans l'inventaire, liste
  « Items » écrite par les Sacs à chaque changement), Curios, ramassage, coups, commandes, interactions refusés pendant
  l'écriture ; vérification refaite au plus 5 fois ;
- arrêt du serveur : écritures attendues et fichiers effacés (joueurs enregistrés avec tout) ; retours en attente
  laissés sur le disque ; fil d'écriture unique et dans l'ordre (la lecture d'avance d'un retour voit toujours le
  fichier final) ;
- objets prêtés du match : purgés au retour, à la connexion, au lâcher et au sol ; jamais de vraie mort ni de mise à
  terre en match (filet `LivingDeathEvent` HIGHEST avant la réanimation, relève toutes les 10 ticks) ;
- `/clashwar regarder` (seulement au spawn, dépôt comme un inscrit, rendu à `/clashwar quitter`, à la fin et après une
  reconnexion), récompenses (clés retirées de l'attente et écrites avant d'être données, jamais pendant un match),
  primes une par clan, classement.

**Lenteurs :** hors tournoi, rien ne tourne (Coffre, parties, poses d'arènes, choix de classe, spectateurs, bornes et
restrictions sortent tout de suite ; la garde hors match regarde seulement le monde de chaque joueur une fois par
seconde). Arènes : pose par tranches avec budget de temps et tickets rendus tronçon par tronçon ; structures lues une
fois au démarrage. Réanimation : rien à faire sans joueur à terre ; le test « à terre » appelé partout (tirs TaCZ, poses
de chaque joueur à chaque tick) coûte une recherche dans une petite table. Diffusion du tableau du tournoi : seulement
quand il change, deux fois par seconde au plus.

**Images par seconde :** HUD à terre, réanimation, tableau de score, fin de match, choix de classe : textes gardés en
cache et refaits seulement quand ils changent ; poses des joueurs : sortie immédiate pour un joueur qui n'est ni à terre
ni dans une scène.

**Paquets client → serveur revérifiés côté serveur :** inscription (taille, doublons, membres du clan, connectés, au
spawn, cadence 300 ms, listes bornées), choix de classe (session, limite par équipe, cadence 150 ms), spectateur
(seulement un spectateur), réanimation (abandon après 1 s à terre, annulation, exécution : distance, vue sans mur, coup
d'essai qui respecte le PvP et les zones protégées, choix d'une exécution possédée).

**Fouille d'un joueur à terre :** les objets sont déplacés, jamais copiés (vrai inventaire, vrais emplacements Curios) ;
objets à vie non prenables par tous les clics (prise, Maj + clic, touches 1 à 9, double-clic, jeter) ; fouille fermée à
travers un mur (revue tous les 5 ticks) ; écran fermé quand la victime est relevée, achevée, se déconnecte ou meurt ;
même nombre de cases chez le joueur et sur le serveur (33 Curios au plus).

**Dépendances :** toutes les cibles lues par réflexion existent avec la même signature : `Downed.isDowned/isActing`,
`DownedManager.get/kill/reviveNow`, `DownedState`, `ExecutionsCommun.probing`, `Executions.of`,
`Executions$Execution.type`, `ExecutionType.id`, `client.SearchScreen` (lus par le noyau, Tactique, Corps, Clans,
Quêtes, Voix, Emotes, Interface…), `Tournois.enCours/courant`, `Tournoi.actif/simulation`,
`CwConfig.get/saisonClansMin`, `CwClassement.toutEffacer`, `WipeClashWar.vider`, `Coffre.aUnDepot` ; et dans l'autre
sens `ObjetsAVie.estAVie`, `SuitRules.mayEquipCurio`, `SpawnApi.allerAuSpawn`, `SaisonApi.event`, `ClesApi.crediter`,
les temps de recharge de Tactique, et l'API de TaCZ 1.1.8 (`GunItemBuilder`, `TimelessAPI`, `CommonGunIndex`,
`AttachmentDataUtils`, vérifiée dans son jar). Canaux réseau inchangés (`reanimation:main` 4, `heraliz_clashwar:main`
3). Clés figées gardées (`heralisz:en_match`, `HeraliszMatch`, `HeralizClashWarDepot`, `heraliz_reste_allonge`).

---

## À tester en jeu (serveur d'essai, un op, 2 à 4 comptes)

- [ ] **Départ d'un ClashWar** (`/clashwar lancer`, puis `/clashwar cloturer`) avec un fusil équipé (lunette,
      silencieux, skin), un sac plein sur le dos et un objet tenu à la souris : au retour, tout est là, exactement une
      fois. Juste à la clôture, appuyer sans arrêt sur la touche du sac, ou cliquer sur « Coffre » dans l'écran du clan
      (touche K) : la fenêtre est très courte (1 à 2 ticks), difficile à viser à la main ; si l'écran s'ouvre à ce
      moment-là, il se referme tout seul (journal : « ecran ouvert pendant l'ecriture de son depot, referme »), rien ne
      double.
- [ ] Même départ en quittant le jeu juste à la clôture avec un objet à la souris : à la reconnexion, l'objet est dans
      l'inventaire (ou à ses pieds au spawn), rien en double, pas de Coffre qui traîne (`/clashwar coffre etat <joueur>`).
- [ ] **Spectateur** : se faire éliminer dans une manche, puis touches 1 à 9 (menu des spectateurs) → « se téléporter »
      vers un joueur resté sur la carte : **rien ne doit se passer**. Si la téléportation marche quand même, le dire
      (proposition D). Un op peut toujours se téléporter.
- [ ] `/clashwar regarder` depuis le spawn, puis `/clashwar quitter`, deux fois : inventaire intact.
- [ ] **Plantage simulé** (staff) : arrêter le serveur brutalement pendant un match, se reconnecter : Coffre rendu,
      rien en double. Si possible, se reconnecter et se faire tuer dans le monde des arènes dans les 3 secondes (`/kill`
      par le staff) : après la réapparition, l'inventaire revient (journal : « joueur mort (rendu a sa reapparition) »).
- [ ] **Réanimation** : exécution normale (touche maintenue sur un joueur à terre) : marche comme avant ; relâcher
      et réappuyer aussitôt : marche aussi. Fouille et réanimation : comme avant.
- [ ] `/wipe` d'un joueur en plein ClashWar qui tenait un objet à vie à la souris au départ : l'objet à vie est gardé.
- [ ] Hors ClashWar : rien ne change (coffre du clan, sac, marchands, morts et réapparitions).

---

## Suite du 21/11 (choix du propriétaire : « sécurités en plus »)

Le propriétaire a choisi les propositions A et C ; B reste comme aujourd'hui. Version : **ClashWar 1.3.8** (seul le jar
du serveur change vraiment ; aucun canal réseau ne change). **Rien n'a été testé en jeu.**

### Fait (commit `3f7f330e`)

**A. Vérification plus stricte du dépôt.** En simple : au moment où le serveur note l'inventaire d'un joueur qui part
au ClashWar, il garde maintenant aussi chaque objet lui-même et une copie de ses données (accessoires, skin, usure,
contenu du sac…). Au moment de vider le joueur, si une case ne tient plus **le même objet**, celui qui est là doit avoir
exactement les mêmes données ; sinon le dépôt est refait, comme quand l'inventaire change. Un objet seulement modifié
sur place (une arme qui tire, un sac qui réécrit son contenu) reste accepté. Avec le correctif du matin (écrans refermés
pendant l'écriture), un échange contre un objet du même type mais d'une autre valeur ne peut plus passer, même par un
moyen encore inconnu.
Développeurs : `Coffres.Releve` (champs `piles`, `copies`, `piecesCurios`, `copiesCurios`, `Coffres.java:209`),
`releve(p, true)` au relevé (ligne 235), `inchange` (ligne 293) et `meme` (ligne 329, `ItemStack.matches`).

**C. Erreur d'un autre mod pendant un dépôt.** En simple : si un autre mod plante pendant un dépôt (vérifications,
descente d'un véhicule, fermeture d'un écran, relevé, sauvegarde, vidage), l'erreur est attrapée : le joueur reste
intact (rien n'est vidé, ce qui était à la souris ou dans la grille 2 x 2 lui est rendu), aucun fichier ne lui sera
rendu en plus, une ligne au journal, et le serveur continue (avant : son tick s'arrêtait, donc plantage). Deux sécurités
de plus dans la même idée :
- la marque du dépôt est posée **juste après** le vidage, avant tout autre appel : une erreur ensuite (effets, santé,
  mode de jeu, tag de combat) ne peut plus faire perdre le dépôt déjà fait (avant, une erreur à ce moment-là laissait le
  joueur vide et sans marque : son dépôt n'aurait jamais été rendu automatiquement) ;
- si des Curios ne se vident pas (erreur de Curios), le joueur est remis exactement comme au relevé et le dépôt est
  refusé : avant, ces Curios seraient restés sur lui **et** dans le dépôt (en double au retour).
Les retours du Coffre ne sont pas concernés (une erreur au milieu d'un retour arrête toujours le serveur : c'est plus
sûr qu'un retour à moitié fait).
Développeurs : `commencer` (`Coffres.java:590`), `erreur` (705), `rendreSansRisque` (714), `erreurImprevue` (733, dernier
filet dans `Coffres.tick` : lignes 1327 et 1347), `confirmer` (775 ; marque ligne 844), `viderObjets` (933),
`remettreEnForme` et `sansErreur` (943 et 962), `remettre` (977).

### À tester en jeu (un op, 2 à 4 comptes)

- [ ] Départ d'un ClashWar et `/clashwar regarder` avec des armes équipées (lunette, silencieux, skin), un sac plein et
      des Curios (gilet, masque) : chacun entre en salle **du premier coup**. Dans le journal du serveur, aucune ligne
      « inventaire change pendant l'ecriture » ni « erreur d'un autre mod » pour un joueur qui ne touchait à rien (s'il y
      en a, le dire : un mod changerait un objet tout seul).
- [ ] Au retour : chaque objet exactement une fois, avec ses accessoires, son skin, son usure et le contenu du sac.
- [ ] Hors ClashWar : rien ne change.
