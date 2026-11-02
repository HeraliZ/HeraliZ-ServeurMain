# Audit du 02/11 — volet « mods-1 »

Modules relus : `noyau`, `armes`, `equipement`, `sacs`, `survie`, `voix`, `emotes`, `interface` (code de `main`, commit
65e64fad). Audit en lecture seule : rien n'a été changé, rien fabriqué, **rien testé en jeu**. Les jars des autres
auteurs ont seulement été lus (geckolib222, avec `javap`).

Déjà lus pour ne pas redire : `docs/AUDIT_COMPLET_26OCT.md`, `docs/AUDIT_25OCT.md`, les fiches `docs/TESTS_EN_JEU_*`
du 25/10 au 01/11, et le `git log` depuis le 25/10. Quand un point reprend un ancien numéro (A30, A240…), c'est qu'il
est **toujours là dans le code d'aujourd'hui** (revérifié ligne par ligne) ; les autres points sont nouveaux.
Corrigés depuis le 26/10 et donc pas repris : A42 (sacs), A50 (touche ² de la roue des emotes, passée sur Y).

Le gros chantier « Minecraft allégé » du noyau (24 mixins de `allege/`, `perf/`) a été relu sur les points sensibles
(paquets de tronçon partagés, comparaisons des menus, balles figées) : rien de faux trouvé ; chaque morceau a ses
garde-fous (contrôles, réglage pour le couper). Il reste à l'essayer en jeu (fiche du 01/11).

---

## Bloquants

### 1. Les armes de mêlée « à vie » s'usent et finissent par disparaître (A30, toujours là)
- **Gravité : bloquant** (perte d'objets payés).
- Où : `modules/armes/src/serveur/java/fr/heralisz/tactical/item/MeleeItemServeur.java:66-69` ; réglage
  `serveur/config/heralisz_tactical-common.toml:8` (`meleeItemConsumeDurability = true`) ; toutes les armes de mêlée
  sont « à vie » (`modules/armes/src/commun/java/fr/heraliz/tactique/avie/ObjetsAVie.java:47-49`).
- Ce qui se passe : chaque coup qui touche retire de l'usure. Une batte (caisse Gemme) a 240 points, une Miséricorde
  1 400 (`data/heralisz_melee/index/melee/*.json`, `max_durability`). Arrivée à zéro, l'arme casse et disparaît, sans
  réparation possible. Le joueur perd un objet acheté ou gagné « à vie ».
- Proposition : pas d'usure pour un objet à vie (test `ObjetsAVie.estAVie` avant `hurtAndBreak`), ou le réglage à
  `false`. Effort : **petit**.

### 2. Objets à vie perdus quand on quitte avec un écran ouvert (A240, toujours là, plus un cas nouveau)
- **Gravité : bloquant** (perte d'objets payés ; cas rare : Alt+F4, plantage, coupure).
- Où : `modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/ObjetsAVieServeur.java:85-104` (jet), `:186-221`
  (remise), `:269-283` (déconnexion).
- Ce qui se passe : à la déconnexion, Minecraft enregistre le joueur PUIS vide sa grille d'artisanat 2×2 et l'objet tenu
  à la souris (il les « jette »). Notre règle des objets à vie refuse ce jet et remet l'objet dans l'inventaire… d'un
  joueur déjà enregistré : au retour, l'objet n'est plus là. Nouveau cas trouvé : l'inventaire du joueur est maintenant
  l'écran de Curios. S'il est ouvert, l'objet part dans la « question Jeter ? » (`EN_ATTENTE`) APRÈS notre écouteur de
  déconnexion : il n'est écrit nulle part, reste en mémoire, peut ressortir plus tard chez le joueur au hasard d'un autre
  jet, ou disparaît au prochain redémarrage.
- Proposition : dans `deconnexion()`, ramener d'abord le curseur et les deux grilles (base et Curios) dans l'inventaire
  ou dans les données gardées (`HeralizObjetsAVie`), et vider `EN_ATTENTE` du joueur après coup. Effort : **petit à
  moyen**.

### 3. Tirs à la tête et coups d'arme blanche refaits par geckolib222 (T-conf-1, toujours là, chiffres nouveaux)
- **Gravité : bloquant** (le combat PvP ne suit pas l'équilibrage).
- Où : jar d'un autre auteur `serveur/mods-tiers/geckolib222-forge-1.20.1-4.4.7.jar`, classe
  `software.bernie.example.client.handler.CuriosEventHandler` (lue avec `javap`) ; notre réglage dans
  `modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/DegatsArmes.java:131` et
  `serveur/config/heraliz_tactique/armes.json`.
- Ce qui se passe : cet écouteur (actif sur le serveur) garde dans une variable commune à tout le serveur « le dernier
  tir était-il à la tête, et avec quel multiplicateur ». Ensuite, sur chaque balle ET chaque coup d'épée (batte,
  machette… de geckolib222 sont des épées) reçu par un joueur, il remultiplie les dégâts par « multiplicateur ÷ 2 ».
  Nos multiplicateurs de tête vont de 1,25 à 2 : un tir à la tête fait donc **moins** que prévu. Exemple concret : les
  11 fusils à pompe (AA-12, M870, Saiga 12, double canon…, tête 1,25) : un plomb à la tête fait 0,78 fois un plomb au
  corps. Un coup de batte qui suit, n'importe où sur le serveur, un tir à la tête reçu par un autre joueur, est lui
  aussi réduit (×0,62 à ×1). Il écrit aussi deux nombres dans la console à chaque coup reçu par un joueur.
- Proposition : un mixin serveur qui empêche seulement ses `setAmount` et ses écritures console (garder sa chance de
  saignement), jar intact ; puis un tir d'essai tête / corps. Effort : **petit**.

---

## Importants

### 4. L'écran « Informations » des anciens joueurs montre des commandes retirées (A2, toujours là, preuve nouvelle)
- **Gravité : important** (visible par tous les joueurs arrivés avant le 26/10).
- Où : `modules/interface/src/main/java/fr/heraliz/interfaces/client/Informations.java:59-68`.
- Ce qui se passe : à la première ouverture, le texte du jar est copié sur le PC du joueur
  (`config/heraliz_interface/informations.json`) et c'est cette copie qui est lue ensuite, pour toujours. Les versions
  d'avant le 26/10 (historique Git) citent `/duel`, `/zombies`, `/groupe`, `/cc`, `/solde`, `/payer`… : ces joueurs les
  voient encore, et ne voient pas `/rtp`, `/extractions`, `/kit`, `/discord lier`.
- Proposition : un numéro de version dans le fichier ; si celui du jar est plus récent, la copie est remplacée (ou
  plus de copie du tout : lire le texte du jar). Effort : **petit**.

### 5. Armure : un plomb dans la tête rend tous les coups du même tick « à la tête » (A43, toujours là)
- **Gravité : important**.
- Où : `modules/equipement/src/serveur/java/fr/heraliz/armory/compat/TaczCompat.java:27-30` et `:37-40`,
  `modules/equipement/src/serveur/java/fr/heraliz/armory/armor/ProtectionEvents.java:63`.
- Ce qui se passe : la note « touché à la tête » vaut pour toute la victime pendant tout le tick. Un seul plomb à la
  tête d'une décharge de fusil à pompe, et tous les autres plombs (et les balles des autres tireurs ce tick-là) ignorent
  le gilet : seules les pièces de tête comptent.
- Proposition : chaque balle qui ne touche pas la tête efface la note, et la note est consommée au premier coup qui la
  lit. Effort : **petit**.

### 6. Reconnexion automatique : elle cache toute raison de refus pendant 10 minutes (nouveau)
- **Gravité : important**.
- Où : `modules/interface/src/main/java/fr/heraliz/interfaces/client/Reconnexion.java:61-75` ; message de
  `/redemarrage` : `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/maintenance/Maintenance.java:143`.
- Ce qui se passe : après un `/redemarrage`, pendant 10 minutes, **toute** déconnexion avant l'entrée en jeu est
  remplacée par « Reconnexion automatique dans … s » : serveur plein, banni, VPN refusé, jeu pas à jour (version d'un
  canal changée)… Le joueur ne voit jamais pourquoi, puis lit « Le serveur ne répond toujours pas ». Or `/redemarrage`
  sert justement aux mises à jour du code : si un canal a changé, le jeu du joueur, resté avec les anciens mods,
  réessaie 30 fois pour rien alors qu'il faudrait relancer le launcher. L'annonce dit pourtant « Ton jeu se reconnectera
  tout seul ».
- Proposition : ne réessayer que si la raison est « serveur injoignable » ; sinon montrer la vraie raison. Annonce :
  « si une mise à jour est sortie, relance le launcher ». Effort : **petit**.

### 7. Voix : un jeu modifié peut faire renvoyer la liste vocale à tout le monde à chaque tick (A210, toujours là)
- **Gravité : important** (abus qui peut saturer la connexion de tous).
- Où : `modules/voix/src/serveur/java/com/caracrea/server/VoiceServer.java:548-554` (`clientState`), `:572-633`
  (`groupAction`), `:411` et `:530-543` (envoi de la liste à tous).
- Ce qui se passe : chaque paquet « état de la voix » qui change, ou chaque « créer / quitter un groupe », marque la
  liste comme changée ; elle part alors à tous les joueurs au tick suivant (tous les joueurs et tous les groupes dans
  chaque envoi). Aucune limite par joueur : 20 envois par seconde à 150 joueurs, ~150 lignes chacun, plusieurs Mo par
  seconde sortants. Seuls les mots de passe de groupe sont limités.
- Proposition : une cadence par joueur (ex. 4 actions par seconde) et une liste envoyée au plus 2 fois par seconde.
  Effort : **petit**.

### 8. Trois fichiers de joueurs repartent de zéro s'ils sont illisibles, puis sont écrasés (A13, A44, A209, toujours là)
- **Gravité : important** (rare, mais perte d'achats).
- Où :
  - camos à vie : `modules/armes/src/serveur/java/fr/heralisz/camo/serveur/CollectionCamos.java:87-97` (lecture) et
    `:144` (`sauvegarder` ne regarde pas `fiable`) ; conséquence dans `CamosAVie.java:284-292` ;
  - emotes : `modules/emotes/src/serveur/java/fr/heraliz/emote/serveur/Deblocages.java:122-155` (le commentaire de la
    classe dit « jamais écrasé », c'est faux) ;
  - sourdines vocales : `modules/voix/src/serveur/java/com/caracrea/server/VoiceMutes.java:61-83` et `:94-121` (pas
    même de copie de secours).
- Ce qui se passe : un fichier illisible au démarrage donne une liste vide ; au premier changement (un camo ajouté,
  une emote débloquée, une sourdine posée), la liste vide est écrite à sa place. Pour les camos, c'est pire au
  redémarrage suivant : le fichier est redevenu « lisible », et le serveur retire alors leurs camos à vie des armes de
  tous les joueurs. Une copie `.bad` / `.illisible` existe pour les deux premiers, mais personne n'est prévenu.
- Proposition : tant que la lecture a échoué, ne rien écrire et prévenir le staff ; copie de secours datée. Effort :
  **petit**.

### 9. Soins : changer d'objet pendant le soin garde le temps du premier (A31, toujours là)
- **Gravité : important** (triche, jeu modifié).
- Où : `modules/armes/src/commun/java/fr/heralisz/tactical/item/ConsumableItem.java:30` (et `ThrowableItem.java:25`) :
  pas de `canContinueUsing`.
- Ce qui se passe : tous les soins sont le même objet de Minecraft ; pendant l'utilisation, Forge ne compare que
  l'objet. Un jeu modifié commence un soin léger (1,5 s) et le remplace dans la même case par un soin lourd : soin lourd
  en 1,5 s, et sa recharge n'est pas vérifiée.
- Proposition : `canContinueUsing` qui compare aussi la fiche du soin (et de la grenade). Effort : **petit**.

### 10. Commandes des plugins hors du filtre « combat / à terre / carte » (A279, toujours là)
- **Gravité : important** (à vérifier en jeu, 5 min).
- Où : `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/combat/CombatService.java:94` et `:274-281` : seul
  l'événement de commande de Forge est écouté ; aucun module n'écoute l'événement de commande de Bukkit.
- Ce qui se passe : sous Mohist, une commande de plugin (AuthMe `/logout`, LiteBans, WorldGuard…) ne passe pas par ce
  filtre. `/logout` en combat ou à terre : AuthMe fige et protège le joueur, son tag expire. Les droits de LuckPerms ne
  sont pas dans le dépôt : impossible de voir ici si `authme.player.logout` a été retiré au groupe `default`.
- Proposition : d'abord retirer ce droit (LuckPerms), puis un écouteur Bukkit par réflexion (comme `ClicObjetBukkit`)
  avec les mêmes refus. Effort : **petit** puis **moyen**.
- Question : le droit `/logout` a-t-il été retiré aux joueurs dans LuckPerms ?

### 11. Armes skinnées : décalques et skins coûteux pour les joueurs (A15 et T-perf-8, toujours là)
- **Gravité : important** (images par seconde des joueurs, poids du téléchargement).
- Où : `modules/armes/src/client/java/fr/heralisz/camo/decor/client/DecorRender.java:121-133`, `:196-214`, `:262-293` ;
  `modules/armes/src/commun/java/fr/heralisz/camo/decor/DecorData.java:91-100` ; dossier
  `modules/armes/src/client/resources/assets/heralisz_camo/shaders/core/`.
- Ce qui se passe : à chaque image et pour chaque arme visible qui porte des stickers ou un StatTrak, les données sont
  relues, chaque pièce du modèle recopiée et toute la géométrie du décalque recalculée (pièces × faces × cubes).
  Les skins : 67 programmes de dessin de 191 Ko chacun (13 Mo), identiques à une ligne près (`#define CAMO_ID`), donc
  compilés 67 fois.
- Proposition : garder la géométrie des stickers par arme tant que l'arme et ses stickers ne changent pas ; un seul
  programme de skin avec le numéro passé en paramètre. Rien de visuel ne change. Effort : **moyen**.

---

## Mineurs

### 12. Les dégâts des armes ne se rechargent pas sans redémarrer (A29, toujours là)
- Où : `modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/DegatsArmes.java:74-76` (lu au démarrage seulement) ;
  absent de `[recharger] commandes` de `serveur/defaultconfigs/heraliz_noyau-server.toml`.
- Changer `armes.json` oblige à redémarrer, contre la règle « données rechargeables ».
- Proposition : une commande de rechargement (et renvoi aux joueurs connectés), ajoutée à la liste de `/recharger`.
  Effort : **petit**.

### 13. Réanimation : restes du bot d'essai et de commandes retirées (nouveau)
- Où : `modules/survie/src/commun/java/fr/heraliz/reanimation/registry/ModEntities.java:19-31` (entité
  `reanimation:bot`), `ModItems.java:19` (œuf `bot_spawn_egg`), classes `entity/BotEntity`, `BotEntityServeur`,
  `BotGoals`, `BotInventory`, `client/BotRenderer` ; `execution/ExecutionUnlocks.java:108-119` (`knifeExecution` ne fait
  rien, `switchKnife` jamais appelé) ; textes de `/reanimation down`, `/reanimation bot` et `execution switch` dans
  `assets/reanimation/lang/*.json` (l. 68-71, 152-153 du français).
- Les commandes du bot ont été retirées le 24/10, mais l'entité, son œuf (onglet créatif), son IA et son rendu sont
  toujours dans les jars, joueurs compris.
- Proposition : tout retirer. Effort : **petit**.
- Question : un membre du staff garde-t-il cet œuf dans un inventaire ? (règle des identifiants figés : il
  disparaîtrait)

### 14. Camos « Pack-a-Punch » du mode Zombies retiré (nouveau)
- Où : `modules/armes/src/commun/java/fr/heralisz/camo/Camo.java:60-64` et `:106-109`,
  `modules/armes/src/serveur/java/fr/heralisz/camo/serveur/CamosAVie.java:43` et `:68-71` ; objets
  `camo_pap_1..3` et programmes `shaders/core/camo_73..75` (575 Ko) dans le jar joueur.
- Ces trois camos n'existaient que pour le mode Zombies, retiré : plus rien ne les donne, mais ils sont téléchargés par
  tous et ont leur message « réservé au mode zombies ».
- Proposition : les retirer. Effort : **petit**.
- Question : quelqu'un a-t-il encore une arme avec un camo Pack-a-Punch, ou l'objet camo, à garder ?

### 15. Outils de développement livrés dans les jars des joueurs (nouveau)
- Où : `modules/equipement/src/client/java/fr/heraliz/armory/client/devtools/CinemaClient.java` (226 lignes, tournage
  vidéo image par image), branché dans `MannequinModel.java:45,61`, `MannequinRenderer.java:37-74`,
  `SetArmorLayer.java:101-110` ; trace du banc d'essai des skins `modules/armes/src/client/java/fr/heralisz/camo/client/SkinInspect.java:53`.
- Inactifs sans option de lancement, mais livrés (le 24/10, 18 classes de ce genre avaient été retirées).
- Proposition : les retirer des jars. Effort : **petit**.

### 16. Noyau : restes de PlayerRevive (mod retiré le 02/10) et de spark (retiré le 01/11) (nouveau)
- Où : `modules/noyau/src/commun/java/fr/heraliz/noyau/api/ATerre.java:30-31`, `:102-119`, `:147-163` ;
  `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/combat/CombatEvents.java:47` et `:84-89` (écouteur de tous
  les dégâts annulés, pour PlayerRevive) ; `serveur/interactions/ClicObjetBukkit.java:45` (« spark » dans la liste des
  plugins porteurs).
- PlayerRevive n'est plus installé (ni dans `serveur/mods-tiers`, ni dans `libs`). Sans lui, `ATerre.aTerre` lit encore
  l'ancienne marque `playerrevive:bleeding` des données du joueur : un joueur qui l'aurait gardée de l'époque serait vu
  « à terre » pour toujours par la planque et le coffre VIP (`monde/.../coffres/CoffresPersonnels.java:124`),
  l'hélicoptère et le ClashWar. Pas vérifiable sans les données des joueurs.
- Proposition : retirer la partie PlayerRevive et « spark ». Effort : **petit**.

### 17. Kit d'interface recopié 21 fois, toutes les copies différentes (A302, toujours là, chiffres)
- Où : `Ui.java` existe en 21 exemplaires tous différents (~10 400 lignes), plus 15 `HzButton`, 15 `HzScroll`,
  14 `HzField`, 15 `Echelle`, 18 `SonsUi`. Dans ce volet : référence `modules/noyau/src/client/java/fr/heraliz/noyau/client/kit/`,
  copies dans `armes` (deux dans le même jar : `heralisz/camo/client/kit` et `heraliz/tactique/client/kit`), `emotes`,
  `equipement` (`armory/client/gui/Ui.java`), `interface`, `survie`, `voix`.
- Une correction faite dans l'une n'arrive pas dans les autres (`outils/copier_kit_ui.py` écrase sans fusionner).
- Proposition : les modules qui dépendent déjà du noyau utilisent directement son kit ; une seule copie par jar sinon.
  Effort : **moyen à gros** (par étapes).

### 18. Petits doublons de code entre modules (nouveau)
- « Objet à vie ? » relu par réflexion à 5 endroits : `boutique/.../grades/serveur/EchangeServeur.java:597` (recherche
  la méthode à CHAQUE appel), `boutique/.../hdv/RegleHdv.java:67`, `pnj/.../quetes/serveur/Commandes.java:485-492`,
  `survie/.../menu/SearchMenu.java:37-45`, `clans/.../server/EtatJoueur.java:25`.
- « En match ? » lu sur la marque brute des données du joueur au lieu de `fr.heraliz.noyau.api.Matchs` :
  `armes` (`ObjetsAVieServeur.java:232`, `CamosAVie.java:171,200`, `KillCounter.java:29`), `voix` (`Busy.java:32`,
  `HeralizVoiceCompat.java:94`), `emotes` (`EmoteRules.java:130`). Deux vérités possibles si la marque reste après un
  plantage.
- Proposition : une seule fonction dans le noyau, appelée partout. Effort : **petit**.

### 19. Plusieurs sacs pleins portés dans l'inventaire (A45, toujours là)
- Où : `serveur/config/backpacked.server.toml` (`lockIntoSlot = false`) ; aucune règle dans `modules/sacs`.
- Un joueur peut porter plusieurs sacs pleins (jusqu'à 54 cases chacun) dans son inventaire : la place est multipliée.
- Proposition selon le choix : refuser un sac non vide hors du dos, ou le laisser. Effort : **petit**.
- Question : un sac plein doit-il pouvoir être porté ailleurs que sur le dos ?

### 20. Réglages d'équilibrage écrits dans le code (nouveau)
- Plafond des armures 60 % (`modules/equipement/src/commun/java/fr/heraliz/armory/armor/Protection.java:22`), attente
  de 15 min des camos à vie après la mort (`armes/.../camo/serveur/CamosAVie.java:54`), 10 s sans arme après une emote
  (`emotes/src/commun/java/fr/heraliz/emote/EmoteRules.java:46`), angle de 100° accepté pour la mêlée
  (`armes/.../tactical/item/MeleeItemServeur.java:90`, A32).
- Proposition : les passer dans les fichiers de réglages. Effort : **petit**.

### 21. Textes et documents qui ne collent pas (nouveau)
- `/redemarrage` écrit « min » et « s » en français pour tous (`noyau/.../maintenance/Maintenance.java:141`) : les
  joueurs anglais et russes lisent les unités françaises.
- `ARCHITECTURE.md` dit faim « 0,5 » ; le réglage et le code disent 0,35 (`NoyauConfig.java:131-133`).
- `interface/.../client/Touches.java:45` dit encore « emotes (0, ²) » alors que ² est interdite.
- Proposition : unités par clé de texte ; mettre les documents à jour. Effort : **petit**.

### 22. Petites dépenses à chaque tick (nouveau)
- `armes/.../tactique/serveur/ObjetsAVieServeur.java:107-114` : chaque tick, les 41 cases de chaque joueur sont relues
  pour retirer une marque de sac (~6 000 lectures par tick à 150 joueurs) ; une fois par seconde suffirait.
- `armes/.../camo/serveur/CamosAVie.java:259-262` : toutes les armes de tous les joueurs relues dans le même tick, une
  fois par seconde ; à étaler sur 20 ticks.
- Effort : **petit**.

---

Vu en passant, hors de ce volet : `serveur/defaultconfigs/` garde les réglages des boss mis de côté
(`heralisz_mammoth`, `heralisz_mere`, `heralisz_n07`, `heralisz_plague`, `heralisz_sylvorn`) et de Mutants & Zombies
(`mutantszombies-server.toml`), dont le jar est toujours dans `serveur/mods-tiers/` alors que le plan dit « remplacé ».

**Rien de ce rapport n'a été testé en jeu ni fabriqué** : tout vient de la lecture du code et des jars.

## Résumé en 5 lignes
1. Trois bloquants : armes de mêlée à vie qui s'usent et cassent (1), objets à vie perdus en quittant avec un écran ouvert (2), tirs à la tête remultipliés par geckolib222 (au fusil à pompe, la tête fait moins que le corps) (3).
2. Importants : écran Informations figé chez les anciens joueurs (/duel, /zombies), armure « tête » sur tout un tir, reconnexion automatique qui cache les vraies raisons de refus, liste vocale qu'un tricheur peut faire renvoyer sans fin.
3. Encore ouverts depuis le 26/10 : fichiers des camos, emotes et sourdines écrasés après une lecture ratée, échange de soin en cours d'utilisation, `/logout` hors du filtre de combat, décalques et 67 programmes de skin trop coûteux.
4. Restes à retirer : bot d'essai de Réanimation, camos Pack-a-Punch du mode Zombies, outils de tournage dans les jars joueurs, code PlayerRevive et spark du noyau.
5. Propreté : kit d'interface en 21 copies toutes différentes, réflexions recopiées, réglages d'équilibrage dans le code, dégâts des armes pas rechargeables à chaud.
