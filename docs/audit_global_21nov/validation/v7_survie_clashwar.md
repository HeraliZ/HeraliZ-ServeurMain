# Validation de l'audit du 21/11 — zone v7 : réanimation et ClashWar

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la zone du compte rendu `../a7_survie_clashwar.md`, sans se fier à ses « vérifié sans problème ». Relu :
`modules/survie` (mod `reanimation` : joueurs à terre, saignement, fouille avec les emplacements Curios, réanimation,
exécutions et leurs scènes, objet « finisher », commandes, paquet des joueurs ; HUD et poses des joueurs survolés) et
`modules/clashwar` (mod `heraliz_clashwar` : **Coffre** relu ligne à ligne, dépôt et retour, à chaque moment possible d'un
plantage, d'un arrêt, d'une déconnexion, d'une mort, d'un changement de monde ; tournoi, salle, matchs, spectateurs et
`/clashwar regarder`, récompenses, classement, kits, protections et bordure des arènes, paquets des joueurs ; HUD du match
survolé), plus les API du noyau qu'ils utilisent (`Matchs`, `CombatTag`, `ATerre`, clé `heralisz:en_match`, marque
`HeralizClashWarDepot`), lues sans rien y changer, et tous les appels des autres mods vers cette zone par réflexion.

**Rien n'a été testé en jeu** (pas de jeu ici). Fait sur ce PC : relecture du code ; analyse de syntaxe Java de
`Coffres.java` après correction ; contrôle des API des autres auteurs dans leurs jars (`javap`) : TaCZ 1.1.8
(`GunItemBuilder`, `TimelessAPI`, `CommonGunIndex`, `AttachmentDataUtils`, `GunData` ; et une arme de kit à réserve
virtuelle « DummyAmmo » ne crée aucune munition quand on la vide), Curios 5.14.1 (`CuriosApi`, `ICuriosItemHandler`,
`ICurioStacksHandler`, `IDynamicStackHandler`, `SlotContext`, `ISlotType`) : tout existe avec la même signature (Player
Animator n'est pas utilisé dans cette zone) ; lecture du code de Mohist 1.20.1 (modifications de `ServerPlayer`,
`PlayerList`, `ServerGamePacketListenerImpl`, et la classe `CraftPlayer`) pour la téléportation des spectateurs, la
réapparition et l'ouverture des écrans. La fabrication et les contrôles se font sur GitHub (« Contrôle de branche »).

En bref : les **7 changements du jour** de la zone (plus une fusion de branches) font ce qu'ils disent ; **aucune
duplication trouvée** ; **4 cas rares corrigés** dans le Coffre (3 pertes possibles, 1 joueur qui restait vide jusqu'à sa
reconnexion), dont 1 ouvert ce matin par le déplacement de la marque du dépôt ; **4 petites propositions** ; la
proposition D du premier audit (Mohist et le menu des spectateurs) est **vérifiée dans le code de Mohist** : la protection
marche (reste l'essai en jeu). Versions : **ClashWar 1.3.9** (seul le jar du serveur change vraiment ; aucun canal réseau
ni texte affiché ne change : un jeu en 1.3.8 se connecte toujours), **Réanimation 1.16.14** inchangée.

Le soir (choix du propriétaire) : les 4 propositions, la proposition E du premier audit et la version minimale du noyau
sont faites (**ClashWar 1.3.10**, **Réanimation 1.16.15**) : voir « Fin du 21/11 » en bas.

---

## Changements du jour validés

Aucun autre commit du jour dans ces deux modules (`git log --since="2026-11-21 00:00" -- modules/survie
modules/clashwar`), ni depuis la mise en ligne de 17 h 43.

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `0379b11fc` | ClashWar 1.3.4 : au vidage, le joueur doit tenir exactement ce qui a été relevé, sinon dépôt refait (5 fois au plus) ; marque du dépôt posée seulement au vidage | **OK**, avec un effet de bord corrigé (« Corrigé » n° 1) : la marque n'étant plus posée pendant l'écriture, la règle commune du noyau (`Matchs.inventaireRetenu`) ne fait plus attendre les livraisons des autres mods pendant ces 1-2 ticks. Une livraison qui entre dans l'inventaire est bien vue (dépôt refait) ; une livraison à un inventaire plein qui « tombe » était retenue puis perdue. |
| `3a2d86eef` | ClashWar 1.3.4 : la vérification compare aussi l'arme, la munition, l'accessoire, le soin, la ration de chaque case | **OK** (`Coffres.java` l. 219-233). |
| `cc10fec2c` | ClashWar 1.3.5 : titre de l'écran d'inscription avec son numéro d'édition | **OK** : la clé `clashwar.inscription.titre` a bien un `%s` dans les trois langues. |
| `7e18032ce` | Commentaires du Coffre seulement | **OK** (aucun code changé). |
| `fec4c5081` | ClashWar 1.3.6 : la vérification compte aussi les objets rangés dans le sac | **OK** : vérifié dans HeraliZ Sacs que le sac écrit son contenu dans l'objet à chaque changement (`BackpackInventory.setChanged` → `saveItemsToStack`, liste « Items ») : la lecture du Coffre est toujours à jour. |
| `c532ae5e6` | ClashWar 1.3.7, Réanimation 1.16.14 : les 10 points du premier audit | **OK** pour les 10 points, relus un par un (écran ouvert pendant l'écriture refermé, souris et grille rendues à la déconnexion, jamais de retour à un mort ni à un joueur à terre, spectateurs gardés dans le monde des arènes, ce qui ne tient pas tombe à l'arrivée, `/wipe` avec la souris, message « équipe retirée » seulement avant le départ, exécutions à 1 / 400 ms, garde et bordure qui laissent un mort tranquille). Un trou dans le point « jamais rendu à un mort » corrigé (« Corrigé » n° 4). Spectateurs : voir plus bas. |
| `3f7f330e2` | ClashWar 1.3.8 : vérification stricte (données entières des objets), erreurs des autres mods attrapées, marque posée juste après le vidage, Curios qui ne se vident pas = joueur remis comme au relevé | **OK**. Fenêtre « vidé sans marque » vérifiée fermée : entre le vidage et la marque, plus rien ne peut lever d'erreur (`viderObjets` l. 951 : vidage de Minecraft, puis fonctions de Curios qui attrapent toute erreur). Un cas théorique de double dans la remise en état (objet lâché pendant l'écriture rendu deux fois) fermé par le correctif n° 1. |

### Spectateurs gardés dans le monde des arènes (`Spectateurs.voyage`, `Spectateurs.java` l. 203)

- **Ça marche sur Mohist** (c'était la proposition D du premier audit) : le menu des spectateurs de Minecraft passe, sous
  Mohist, par la téléportation de Bukkit (`CraftPlayer.teleport`), qui, pour un autre monde, appelle la téléportation de
  Minecraft avec le contrôle de Forge (`ForgeHooks.onTravelToDimension`) : c'est là que `voyage` refuse. Le joueur reste
  où il est (sa caméra revient sur son coéquipier au tick suivant). Reste à le voir en jeu.
- **Personne ne peut rester coincé** : chaque sortie normale d'un spectateur passe d'abord par `Spectateurs.arreter`
  (retour du Coffre `Coffres.detacher`, nouvelle manche `MatchCw.placer`, fin de match `retourSalle`, fin du visionnage
  `arreterRegarder`, reconnexion `surReconnexion` du match et de la salle, déconnexion d'un spectateur de la salle) ;
  ensuite le blocage ne s'applique plus. Un spectateur qui s'est déconnecté et dont le match est fini n'est plus rattaché à
  ce match : rien n'est bloqué. AuthMe ne téléporte personne (`teleportUnAuthedToSpawn: false`, `noTeleport: true`). Le
  staff de niveau 2 n'est jamais bloqué ; seule limite : un op ne peut pas envoyer un spectateur (non op) dans un autre
  monde avant la fin de son match (normal).

---

## Corrigé (commit `0aad77e08`, ClashWar 1.3.9)

### 1. Objet livré pendant l'écriture du dépôt : perdu (moyen, rare)

En simple : depuis ce matin, la marque du dépôt n'est posée qu'au moment où le joueur est vidé. Pendant les 1 à 2 ticks
de l'écriture, un autre mod peut donc lui livrer un objet (objet en attente de la boutique, récompense…). Si son inventaire
est plein, Minecraft fait « tomber » l'objet par le même chemin qu'un jet ; le Coffre retient tout jet pendant l'écriture
(l'objet ne part pas au sol), mais un dépôt confirmé ne rendait jamais ces objets retenus : l'objet livré était perdu.
Maintenant, un seul objet retenu suffit pour que le dépôt soit refait, et l'objet lui est rendu (comme quand l'inventaire
change). Rien ne change pour un joueur qui ne touche à rien. Ferme aussi un cas théorique de double : quand des Curios ne
se vident pas, le joueur est remis comme au relevé, et un objet lâché pendant l'écriture lui était rendu en plus.
Développeurs : `confirmer`, `Coffres.java` l. 812 (`pareil = d.retenus.isEmpty() && inchange(...)`) ; commentaires des
objets retenus (l. 171-176) et de `lache` (l. 409-415).

### 2. Fichier du dépôt disparu pendant l'écriture : joueur vidé sans rien à récupérer (moyen, très rare)

En simple : si le fichier du dépôt est déplacé ou effacé pendant son écriture (commande de dépannage du staff
`/clashwar coffre rendre` tapée à ce moment précis : le fichier part « en enquête »), le joueur était quand même vidé, puis
son retour ne trouvait plus rien. Maintenant, juste avant de vider, le serveur vérifie que le fichier est toujours là ;
sinon le dépôt est refusé et le joueur garde tout. Développeurs : `Coffres.java` l. 841.

### 3. Disque lent : le ménage d'un dépôt abandonné pouvait effacer le dépôt suivant du même joueur (moyen, très rare)

En simple : un dépôt est abandonné quand le joueur se déconnecte pendant l'écriture, ou quand l'écriture dure plus de
30 s ; son fichier est effacé dès que l'écriture finit. Tous les dépôts d'un joueur ont le même nom de fichier. Si le
disque traînait, le joueur pouvait en recommencer un (`/clashwar regarder`) écrit juste après ; les deux écritures
finissaient ensemble, le nouveau dépôt était confirmé (joueur vidé), puis le ménage de l'ancien effaçait le fichier… du
nouveau : tout l'inventaire perdu. Maintenant, le ménage n'efface rien si un nouveau dépôt du joueur est en écriture ou si
le joueur porte une marque (le nouveau dépôt s'occupe de son fichier). Développeurs : `Coffres.java` l. 1356 et
`remplace` l. 1410.

### 4. Mort puis réapparu pendant l'attente de son retour : vide jusqu'à sa reconnexion (petit, rare)

En simple : depuis ce midi, le Coffre n'est jamais rendu à un mort ; il l'est à sa réapparition. Mais si le joueur meurt
pendant les quelques ticks où son retour attend son tour (par exemple reconnecté après un plantage dans le monde des
arènes) et réapparaît avant ce tour, sa réapparition ne redemandait rien (un retour était « déjà en cours »), et ce retour,
fait pour l'ancien joueur (Minecraft crée un nouveau joueur à chaque réapparition, Mohist aussi), était abandonné : il
restait vide jusqu'à sa prochaine connexion. Maintenant, le retour est redemandé pour lui. Rien en double : le fichier est
relu au moment du retour. Développeurs : `Coffres.java` l. 1374-1384.

---

## À décider par le propriétaire (proposé l'après-midi ; tout fait le soir : voir « Fin du 21/11 » en bas)

**P1. Retour : fermer l'écran ouvert avant de mettre de côté la souris et les objets à vie (petit).** Au retour, le
serveur met de côté ce qui est à la souris et les objets à vie, PUIS ferme l'écran ouvert (`Coffres.java` l. 1116-1117) :
un objet à vie laissé dans la grille d'un autre écran ouvert à cet instant (établi d'une arène, par exemple) revenait dans
l'inventaire juste avant qu'il soit vidé, donc perdu. Cas presque impossible (un objet à vie arrive en match seulement
après une question « Jeter cet objet ? » restée ouverte). Risque du changement : faible (inverser deux lignes et garder ce
qui tomberait). À faire seulement si vous voulez fermer ce cas.
**Fait le 21/11 : voir « Fin du 21/11 » en bas.**

**P2. Dépôt immédiat (`Coffre.deposer`, `Coffres.java` l. 563 : API gardée, appelée par aucun mod ni outil aujourd'hui).**
S'il trouve un inventaire changé, il remet le dépôt en file (l. 830) tout en répondant « pas fait » : le dépôt pourrait
alors se faire plus tard sans que l'appelant le sache. En pratique impossible (le dépôt immédiat relève et vérifie dans le
même tick). Proposition : dans ce chemin, refuser au lieu de remettre en file, si un jour il sert. Rien ne presse.
**Fait le 21/11 : voir « Fin du 21/11 » en bas.**

**P3. Instantané sans Curios (sécurité en plus, comme A et C du premier audit).** Si Curios n'arrivait pas à écrire ses
emplacements dans le dépôt alors qu'il sait les vider, ils seraient perdus (`Coffres.java` l. 923, l'erreur est avalée par
`Compat.sauverCurios`). Aujourd'hui, ce cas ne peut pas arriver seul : l'enregistrement complet du joueur, fait juste
avant, passe par les mêmes objets et échoue d'abord (dépôt refusé, joueur intact). Proposition : refuser le dépôt quand
les Curios ont des objets mais ne sont pas dans l'instantané. Gain très faible.
**Fait le 21/11 : voir « Fin du 21/11 » en bas.**

**P4. Paquet de la réanimation, choix et liste des exécutions (actions 3 et 4,
`ActionPacketServeur.java` l. 47 et 57) sans limite de cadence.** Chacun renvoie un petit paquet au joueur ; un jeu modifié
peut en envoyer beaucoup, sans effet sur les autres joueurs. Même famille que la proposition E du premier audit (restée
ouverte) : à faire avec elle si vous la choisissez.
**Fait le 21/11, avec E : voir « Fin du 21/11 » en bas.**

Pour le noyau (relu sans le changer, autre relecteur) : `Matchs.inventaireRetenu` ne voit pas un dépôt du Coffre en cours
d'écriture (1-2 ticks) depuis le déplacement de la marque ce matin. Avec le correctif n° 1, plus rien n'est perdu (le dépôt
est refait) ; un signal « dépôt en cours » dans `Matchs` ferait seulement attendre les livraisons au lieu de refaire le
dépôt. Pas nécessaire.

---

## Vérifié sans problème

**Coffre, étape par étape** (`Coffres.java`) :
- ordre « disque avant toute modification » : relevé sur le fil du serveur, copie complète pour le fil d'écriture (aucun
  objet du jeu touché par ce fil), écriture `.tmp` + `fsync` + relecture comparée + déplacement atomique ; fichier encore
  là vérifié au vidage (n° 2) ; marque juste après le vidage, joueur enregistré tout de suite (`PlayerList.save`, écriture
  immédiate : la sauvegarde étalée du noyau ne touche que la sauvegarde automatique) ;
- plantage (arrêt brutal) à chaque moment : pendant l'écriture (joueur jamais vidé, fichier jamais rendu faute de marque) ;
  entre le vidage et l'enregistrement (idem) ; après (rendu à la connexion) ; pendant un retour (marque retirée et joueur
  enregistré AVANT l'effacement du fichier : jamais rendu deux fois) ;
- arrêt normal : les joueurs sont déconnectés (souris et grille rendues, dépôts abandonnés) PUIS enregistrés, et les
  fichiers des dépôts non confirmés effacés à la fin ; retours en attente laissés sur le disque ;
- déconnexion : en file (rien n'est fait), pendant l'écriture (rendu de la souris, de la grille et des objets retenus,
  enregistré avec tout), après le vidage (rendu à la reconnexion, ou reprise en salle si le tournoi continue), pendant le
  retour ; reconnexion rapide avec un nouvel objet joueur : jamais deux retours ;
- mort : pas pendant l'écriture (le joueur figé ne prend aucun coup : tout dégât est annulé au tout début), jamais de
  retour à un mort, filet « pas de vraie mort en match ni dans la salle » (`CombatEvents.mort`, HIGHEST) ;
- changement de monde pendant l'écriture : sans effet (la position d'arrivée au retour est celle du relevé) ;
- sac (porté ou dans l'inventaire), Curios (mêmes piles vivantes, pas de copies), souris et grille 2 x 2 (mises dans le
  dépôt), objets à vie (gardés au retour, au wipe), objets prêtés (purgés au retour, à la connexion, au jet, au sol) ;
  armes de kit à réserve virtuelle : les vider ne crée aucune munition à emporter ;
- écrans ouverts pendant l'écriture refermés : Mohist envoie bien l'événement de Forge à l'ouverture d'un menu
  (`openMenu`, monture, et `NetworkHooks.openScreen` de Forge) ; coffre du clan refusé à un joueur en match ou dans le
  monde des arènes (pas d'échange pendant l'attente d'un retour) ;
- un seul fil d'écriture, dans l'ordre : une lecture d'avance d'un retour voit toujours le fichier final ; aucune
  donnée partagée entre ce fil et le fil du serveur à part le résultat attendu.

**Tournoi** : inscription (taille, doublons, membres du clan connectés, au spawn, cadence 300 ms, listes bornées),
départ (équipe dont un joueur n'est pas au spawn ou pas connecté retirée, joueur tagué en combat laissé dehors, dépôts
étalés), annulation pendant les dépôts (chaque dépôt fini après l'annulation est rendu), récompenses (clés retirées de
l'attente et écrites avant d'être données, réessayées toutes les 5 s, jamais pendant un match ni avec un Coffre), primes
une par clan, classement, `/clashwar quitter` et `regarder` (seulement au spawn, dépôt comme un inscrit).

**Lenteurs** : hors tournoi, rien ne tourne (Coffre, parties, poses d'arènes, choix de classe, spectateurs, restrictions
sortent tout de suite ; la garde hors match regarde le monde de chaque joueur une fois par seconde) ; correctifs du jour
sans coût (une lecture de fichier par dépôt, un test au ménage). Réanimation : rien sans joueur à terre ; le test « à
terre » appelé partout reste une recherche dans une petite table.

**Images par seconde** : HUD du match, fin de match, à terre, réanimation : textes refaits seulement quand ils changent ;
poses des joueurs : sortie immédiate pour qui n'est ni à terre ni dans une scène.

**Réanimation** : fouille (objets déplacés, jamais copiés ; objets à vie non prenables ; fermée à travers un mur, à la
réanimation, à la mort, à la déconnexion de l'un ou de l'autre ; fouille, réanimation et exécution exclusives entre
elles) ; exécutions (une demande / 400 ms, un vrai appui en demande au moins 0,5 s : rien ne change pour un joueur
honnête ; distance, vue sans mur, coup d'essai qui respecte PvP et zones protégées, placement jamais à travers un mur) ;
finisher (consommé une seule fois, déjà possédé = rien consommé) ; commandes `revive`, `unlock`, `lock` réservées au
niveau 2.

**Paquets des joueurs revérifiés par le serveur** : inscription, retrait, choix de classe, spectateur suivant (seulement un
spectateur), réanimation (abandon après 1 s à terre, annulation, exécution, choix d'une exécution possédée).

**Liens avec les autres mods** (cibles par réflexion présentes, même signature) : `Downed.isDowned` / `isActing`,
`DownedManager.get` / `kill` / `reviveNow`, `DownedState`, `ExecutionsCommun.probing`, `Executions.of`,
`Executions$Execution.type`, `ExecutionType.id`, `client.SearchScreen` (noyau, Tactique, camos, corps, extraction,
suicide, clans, quêtes, voix, emotes, boutique, bases, banc, interface) ; `Tournois.enCours` / `courant`, `Tournoi.actif`
et le champ `simulation` (événements), `Coffre.aUnDepot` (inspecter), `WipeClashWar.vider` (wipe des quêtes),
`CwClassement.toutEffacer`, `CwConfig.get` / `saisonClansMin` (clans) ; dans l'autre sens `SpawnApi.allerAuSpawn`,
`ObjetsAVie.estAVie`, `SuitRules.mayEquipCurio`, `SaisonApi.event`, `ClesApi.crediter`, les recharges de Tactique
(`CustomItemCoolDownsProvider.CAPABILITY`, `removeCooldown`, `cooldowns`). Clés figées gardées : `heralisz:en_match`,
`HeraliszMatch`, `HeralizClashWarDepot`, `heraliz_reste_allonge`. Canaux réseau inchangés (`reanimation:main` 4,
`heraliz_clashwar:main` 3).

---

## À tester en jeu (serveur d'essai, un op, 2 à 4 comptes)

- [ ] **Départ d'un ClashWar** avec un fusil équipé (lunette, silencieux, skin), un sac plein, des Curios et un objet tenu
      à la souris : chacun entre en salle du premier coup ; au retour, tout revient exactement une fois. Dans le journal,
      aucune ligne « inventaire change pendant l'ecriture » ni « fichier du depot absent » pour un joueur qui ne touchait à
      rien.
- [ ] **Spectateur** éliminé : touches 1 à 9, « se téléporter » vers un joueur resté sur la carte : rien ne doit se passer
      (sa caméra revient sur son coéquipier). Un op peut toujours se téléporter.
- [ ] **Fin de match, fin du tournoi, `/clashwar quitter`** pour un joueur qui regardait un match : il revient bien (aucun
      spectateur coincé dans le monde des arènes).
- [ ] **Plantage simulé** (staff) : arrêter le serveur brutalement pendant un match, se reconnecter : Coffre rendu, rien en
      double. Si possible (difficile à viser : le retour se fait en 1 à 60 ticks), se reconnecter, se faire tuer tout de
      suite (`/kill` par le staff) et réapparaître aussitôt : l'inventaire revient après la réapparition (journal : « joueur
      mort (rendu a sa reapparition) »), sans avoir à se reconnecter.
- [ ] `/clashwar regarder` depuis le spawn, puis `/clashwar quitter`, deux fois : inventaire intact.
- [ ] **Réanimation** : exécution normale (touche maintenue) ; relâcher et réappuyer aussitôt : marche aussi. Fouille et
      réanimation : comme avant.
- [ ] Hors ClashWar : rien ne change (coffre du clan, sac, marchands, morts et réapparitions).

Pas testable à la main : les cas corrigés n° 1 à 3 demandent un instant de 1 à 2 ticks ou un disque très lent ; ils sont
seulement relus dans le code.

---

## Fin du 21/11 (choix du propriétaire : « que tout soit lançable et terminé à 100 % »)

Faites, dans le commit `d0fd5a29b` : **ClashWar 1.3.10**, **Réanimation 1.16.15**. Aucun canal réseau ni texte affiché ne
change : un jeu d'avant se connecte toujours. La règle du 15/11 (un joueur déconnecté pendant les dépôts : son équipe
joue sans lui) reste comme avant. **Rien n'a été testé en jeu.**

1. **P1, retour du Coffre : l'écran ouvert est fermé avant la mise de côté.** En simple : au retour, ce qu'un écran ouvert
   tenait (la grille d'un établi d'une arène, par exemple) revient d'abord dans l'inventaire, puis les objets à vie sont
   mis de côté ; avant, l'écran était fermé après, et un objet à vie laissé dans cette grille était effacé avec
   l'inventaire du match. Ce qui ne tient pas dans l'inventaire est gardé à part, comme ce qui est à la souris, au lieu de
   tomber dans l'arène (les objets prêtés du match restent effacés). La souris est toujours prise en premier, comme avant.
   Développeurs : `sauverAvantVidage`, `Coffres.java` l. 1236 (même garde qu'au dépôt, `capture`) ; `rendre` ne ferme plus
   l'écran après (l. 1136).
2. **P3, Curios absents de l'instantané : dépôt refusé.** Si des Curios sont portés mais que leur sauvegarde dans le dépôt a
   échoué, rien n'est écrit et le joueur garde tout (journal « Curios portes mais absents de l'instantane »). Aujourd'hui
   ce cas ne peut pas arriver seul : c'est une sécurité en plus. Développeurs : `commencer`, `Coffres.java` l. 671-676.
3. **P4, réanimation : cadence du menu des exécutions.** Côté serveur, au plus un choix d'exécution toutes les 150 ms et
   une liste toutes les 250 ms (un vrai clic sur « Équiper » ou une vraie ouverture du menu ne vont jamais aussi vite) ;
   oubliées à la déconnexion et à l'arrêt. Développeurs : `ActionPacketServeur.java` l. 26-33, 65 et 79.
4. **P2, dépôt immédiat (`Coffre.deposer`) : refusé au lieu d'être remis en file.** S'il trouve un inventaire changé, il
   est refusé (joueur intact) et répond « pas fait », ce qui est alors vrai. Le tournoi garde ses 5 essais. Développeurs :
   `Demande.immediat` et `Depot.immediat` (`Coffres.java` l. 161 et 188), `confirmer` l. 841.
5. **a7 E, petits envois en trop :**
   - quand un joueur sort de la vue d'un autre, cet autre ne reçoit plus le paquet « plus à terre » si le premier n'a aucun
     état (à terre, fouille, réanimation, exécution) : il n'y avait rien à effacer, puisque chaque fin d'état est déjà
     envoyée à tous ceux qui le voient. La seule fin d'état qui ne les prévenait pas (mort refusée par un autre mod) le
     fait maintenant : ce joueur était montré allongé jusqu'au prochain envoi. Développeurs : `DownedManagerServeur.java`
     l. 953 (`onStopTracking`) et l. 393 (`kill`) ;
   - spectateurs du ClashWar : au plus un changement de cible toutes les 80 ms (`Spectateurs.changer`, l. 88-101).
6. **mods.toml de ClashWar : noyau 1.1.5 au moins** (`modules/clashwar/src/commun/resources/META-INF/mods.toml` l. 45,
   relevé de la validation v10) : ClashWar appelle des fonctions du noyau de cette version ; le serveur et les joueurs ont
   la 1.1.12.

Il ne reste plus rien d'ouvert dans cette zone, à part les essais en jeu.

### À tester en jeu (en plus de la liste plus haut)

- [ ] **Retour du Coffre** avec un écran ouvert au moment du retour (fin d'un match, `/clashwar quitter`) : rien ne
      tombe, tout revient exactement une fois ; un objet à vie laissé dans l'écran revient au joueur.
- [ ] **Menu des exécutions** (touche =) : ouvrir, choisir une exécution, « Équiper », refermer, rouvrir : tout marche comme
      avant.
- [ ] **Joueurs à terre vus de loin** : un joueur à terre qui sort de la vue puis revient est bien montré allongé ; relevé
      pendant qu'il était hors de vue, il est bien debout quand il revient.
- [ ] **Spectateur** : flèches gauche / droite, plusieurs appuis rapides : la caméra change à chaque appui.
