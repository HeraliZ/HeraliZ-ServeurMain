# Audit global du 21/11 — zone a4 : armes, équipement, sacs

Modules relus : `modules/armes` (heraliz_tactique, et le code repris de LR Tactical sous GPL), `modules/equipement`
(heraliz_armory, heraliz_composants) et `modules/sacs` (Backpacked de MrCrayfish, LGPL). Relecture du code côté serveur
en priorité (paquets reçus des joueurs, duplications, ce qui tourne à chaque tick ou à chaque balle), puis les rendus
chez les joueurs (images par seconde), et les liens avec les autres modules et les mods des autres auteurs (TaCZ 1.1.8,
Curios 5.14.1, Framework 0.7.6), vérifiés dans leurs jars avec `javap`.

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, contrôle de syntaxe des fichiers changés, puis la
fabrication et les contrôles sur GitHub. Versions : **Armes 2.59.9**, **Sacs 3.0.1-beta.4-heralisz28** (le launcher les
donne aux joueurs ; aucun canal réseau ne change, jeu et serveur restent compatibles).

En bref : 5 points corrigés (1 moyen, 2 petits, 2 tout petits), 7 propositions à décider, aucun bug grave trouvé (pas de
duplication, pas de plantage, pas de paquet accepté sans contrôle qui donne un avantage).

---

## Corrigé

### 1. Vitres cassées : la repousse pouvait faire attendre tout le serveur (moyen) — commit `3c5a20cf`

En simple : chaque seconde, le serveur regarde les vitres cassées par les balles pour les reposer. Il demandait à
Minecraft « ce morceau de carte est-il chargé ? ». Minecraft répond « oui » même pendant que le morceau est encore en
train d'être lu sur le disque (un joueur qui arrive dans le coin). La lecture de la vitre qui suivait faisait alors
attendre le serveur entier jusqu'à la fin de cette lecture : un petit gel possible pour tout le monde. C'est exactement
le défaut corrigé ailleurs le 19/11 (outil `Troncons` du noyau) ; ce morceau-là avait été oublié.

Maintenant : seulement un morceau de carte complet ; sinon la vitre attend la passe suivante (une seconde plus tard).
Les vitres voisines (raccords des vitres fines) d'un morceau pas prêt ne sont plus lues ni changées non plus, ni à la
repousse, ni quand une balle casse une vitre. Au démarrage du serveur, rien ne change (les morceaux sont chargés exprès,
comme avant).

Pour les développeurs : `modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/VitresServeur.java`
- `passe()` l. 405-414 : `level.isLoaded(pos)` → `Troncons.charge(level, pos)` (le budget de chargements du démarrage
  est inchangé) ;
- `detacher()` l. 459-462 et `reposer(..., peutCharger)` l. 478-503 : voisins lus seulement si leur tronçon est complet
  (en jeu) ; `peutCharger` vrai au démarrage (comportement d'avant).

### 2. Sacs : les défis de déblocage tournaient encore alors que tout est débloqué (petit, perf) — commit `e84547c7`

En simple : depuis le 19/11, tous les sacs sont débloqués (réglage `unlockAllCosmetics = true`, votre choix : « aucun
défi suivi »). Le code du mod des sacs faisait pourtant avancer ses 118 défis : à chaque pas de chaque joueur, à chaque
clic sur un bloc (il copiait même l'objet tenu, une arme avec toutes ses données, et testait 73 défis), à chaque bloc
cassé, et à chaque tick il relisait les 118 défis de chaque joueur qui avait bougé. Tout cela pour rien. Estimation :
0,1 à 0,3 ms par tick à 150 joueurs, plus des milliers de petits objets créés par seconde.

Maintenant : tant que tout est débloqué, rien de tout cela ne tourne. Si un jour vous remettez
`unlockAllCosmetics = false`, tout revient comme avant (les défis reprennent là où ils étaient).

Pour les développeurs (`modules/sacs/src/`) :
- `commun/.../data/unlock/UnlockManager.java` l. 38-58 : `defisSuivis()` (réglage pas encore lu : suivis, comme avant)
  et `getTrackers()` vide quand rien n'est à débloquer (coupe d'un coup tous les écouteurs de défis) ;
- `commun/.../mixin/common/PlayerMixin.java` l. 141 : plus d'événement de déplacement ;
- `commun/.../mixin/common/ServerPlayerGameModeMixin.java` l. 59 et l. 101 : plus de copie de l'objet tenu ni de
  relevé des blocs ;
- `serveur/.../data/unlock/UnlockManagerServeur.java` l. 52 : la file des tests est vidée sans relire les défis.
- `modules/sacs/NOTICE.md` : modification notée (licence LGPL : chaque modification du code d'origine est décrite).

### 3. Sacs : clé et adresse d'un ancien serveur, et un « interrupteur d'arrêt » jamais branché (petit, règle n° 3) — commit `e84547c7`

En simple : le code des sacs gardait deux restes de l'époque ArkaniaZ : une clé secrète et l'adresse d'un ancien site
(jamais lues), et une petite classe qui, si quelqu'un l'avait branchée, aurait **arrêté le serveur au démarrage** si son
adresse IP n'était pas une adresse précise. Elle n'était branchée nulle part, mais elle était livrée dans le jar du
serveur. Tout est retiré (le dépôt est public : la clé reste dans l'historique Git ; elle ne sert plus à rien, l'ancien
serveur n'existe plus).

Pour les développeurs : `modules/sacs/src/commun/java/com/mrcrayfish/backpacked/Backpacked.java` (constantes
`EXPECTED_SERVER_KEY`, `KEY_URL` retirées) ; fichier supprimé
`modules/sacs/src/serveur/java/com/mrcrayfish/backpacked/ServerStartingEventHandler.java` (aucune référence, vérifié dans
tout le dépôt).

### 4. Animations des autres joueurs avec une arme de mêlée (tout petit, images par seconde) — commit `3c5a20cf`

Chez chaque joueur, à chaque image, l'identifiant de l'animation était refait (texte relu et vérifié) trois fois pour
chaque joueur visible qui tient une arme de mêlée. Il est maintenant lu une fois et gardé.
`modules/armes/src/client/java/fr/heralisz/tactical/compat/player_animator/ThirdPersonAnimationConfig.java` l. 18-36
(champ `transient`, jamais lu ni écrit par Gson).

### 5. Pastille du camo sur l'icône d'un sac (tout petit, images par seconde) — commit `3c5a20cf`

L'adresse de l'image de la pastille était refaite à chaque image pour chaque sac affiché avec un camo (inventaire,
coffres). Elle est faite une fois par camo. `modules/armes/src/client/java/fr/heralisz/camo/client/BagCamo.java` l. 199-213.

### 6. Versions

`modules/armes/gradle.properties` 2.59.8 → **2.59.9** ; `modules/sacs/gradle.properties` heralisz27 → **heralisz28**.

---

## À décider par le propriétaire

1. **Collection des camos à vie enregistrée sur le fil du serveur** (`modules/armes/src/serveur/java/fr/heralisz/camo/
   serveur/CollectionCamos.java` l. 158 `sauvegarder()`). Une seconde après chaque changement (camo ajouté, posé, retiré,
   et **chaque mort avec une arme qui porte un camo à vie**), tout le fichier `heralisz_camo/collections.json` (tous les
   joueurs) est mis en texte et écrit pendant le tick. Avec peu de joueurs c'est invisible ; quand le fichier grossira
   (centaines de joueurs), ce sera quelques millisecondes d'un coup, au plus une fois par seconde. Proposition : passer
   cette écriture sur l'écrivain en fond du noyau (`EcrituresFond`, comme les clans et le loot). Risque faible, gain
   seulement le jour où le fichier est gros. Avant de décider : regarder la taille de ce fichier sur le Ptero.
2. **Aucune limite de cadence sur les paquets des camos et des stickers** (`ReceptionServeur.decor` l. 76,
   `ReceptionServeur.appliquerCamo` l. 36, `CamosAVie.recevoir` l. 115). Tout est revérifié par le serveur (aucun gain
   possible), mais un jeu modifié peut les envoyer en rafale : chaque paquet réécrit l'arme et joue un son aux joueurs
   proches (bruit en boucle). Proposition : une action au plus toutes les 0,15 s par joueur (invisible pour un joueur
   normal, qui clique). Risque très faible.
3. **Objet à vie rangé dans un coffre, puis plantage du serveur** (`ObjetsAVieServeur.ranger` l. 361 et `reponse`
   l. 379). Pendant la question « Ranger cet objet ici ? », l'objet est gardé dans les données du joueur. Si le serveur
   plante juste après le « Oui », au mauvais moment (le coffre déjà enregistré, le joueur pas encore), l'objet pourrait
   revenir en double à la reconnexion. Il faut un plantage dans une fenêtre de quelques minutes au plus, au moment
   exact ; même famille que les cas notés le 21/11 (section 42 de la fiche). Proposition : laisser tel quel (le corriger
   demanderait d'enregistrer le joueur à chaque « Oui », plus coûteux que le risque).
4. **Jambe cassée : téléporté pendant un saut** (`JambeCasseeServeur` l. 91 et l. 110). Un joueur à la jambe cassée qui
   saute, puis est téléporté à moins de 16 blocs de là pendant le saut (`/tpa` sur un joueur tout proche), est ramené au
   point de départ du saut en retombant. Très rare, sans perte. Proposition : oublier le saut quand le joueur change de
   monde ou est téléporté.
5. **Version minimale du noyau dans `modules/armes/src/commun/resources/META-INF/mods.toml` l. 34** : `[1.0.6,)`, alors
   que les armes se servent d'outils du noyau ajoutés depuis (1.1.x : tronçons complets, inventaire retenu par le
   ClashWar…). Sans effet aujourd'hui (tous les jars partent ensemble) ; avec un vieux noyau, le jeu planterait sans
   message clair. Proposition : monter le minimum à la version actuelle du noyau. Risque : un joueur avec un noyau pas à
   jour serait refusé avec un message clair au lieu d'un plantage.
6. **Ouverture du sac d'un autre joueur** (`enabledPickpocketing` de `serveur/config/backpacked.server.toml`, aujourd'hui
   `false`, à garder ainsi). Si ce réglage revenait un jour à sa valeur par défaut (`true`, fichier perdu ou remis à zéro),
   n'importe quel joueur pourrait ouvrir le sac d'un autre de dos, et l'ouverture d'un sac de randonnée, de sport ou
   « montagne » d'un autre joueur ferait une erreur (`ServerPlayHandler.java` l. 77 → `BackpackItem.java` l. 51, ces sacs
   ne sont pas des `BackpackItem`). Proposition : rien à faire tant que le réglage reste à `false` ; ou forcer « jamais »
   dans le code (une ligne).
7. **Décharger une arme à très gros chargeur** : TaCZ (code d'un autre auteur, intact) perd 1 ou 2 balles pour certains
   nombres précis au-delà de deux piles de munitions (ex. 121 ou 182 balles en piles de 60) ; la balle de la chambre est
   déjà rendue à part depuis le 15/11. Presque jamais rencontré ; le refaire nous-mêmes changerait aussi le retrait du
   chargeur de TaCZ. Proposition : laisser.

---

## Vérifié sans problème

**Armes (heraliz_tactique, LR Tactical)**
- Hitbox des joueurs « en morceaux » (`TaczHitboxCorpsMixin`, `HitboxCorps`) : cibles de TaCZ 1.1.8 présentes
  (`EntityUtil.getHitResult`, `findEntityOnPath`, `findEntitiesOnPath`), tri rapide par boîte avant le calcul précis,
  un seul rayon contre les murs seulement quand un morceau est touché, tireur absent sans plantage ; même méthode pour la
  cible d'essai (`TaczHitboxCibleMixin`, Équipement).
- Toutes les autres cibles de mixins dans TaCZ 1.1.8 (sons des silencieux, plombs, horloges des tirs, tirs refusés,
  traçantes, s'allonger, trajet des balles dans le verre) : vérifiées avec `javap`.
- Mêlée de LR (`CMeleeAttackRequest`, `CPrepareMeleeAttack`, `CombatProperties`, `MeleeItemServeur`) : nombre de cibles
  borné, chaque cible revérifiée (même monde, devant, vue dégagée, portée plafonnée + marge), une seule fois chacune ;
  coup demandé trop tôt retenu jusqu'à la fin de l'élan ; portée de chaque coup reverifiée (`PorteeMeleeServeur`).
- Couteaux (`CouteauxServeur`) : tout décidé par le serveur (cadence en temps réel, temps de sortie, à terre, même
  couteau dans la même case au moment du coup) ; état oublié à la déconnexion.
- Plongeon et allongé (`PlongeonServeur`, `AllongeServeur`) : demandes limitées (0,15 s), toutes les conditions revues
  par le serveur, tir refusé pendant qu'on se couche.
- Décharger (`DechargerServeur`) : case bornée, arme de match refusée, rechargement en cours refusé, munitions jamais
  copiées (balle de la chambre rendue à part, remise si rien n'est rendu).
- Objets à vie (`ObjetsAVieServeur`, 844 lignes) : jet, clics dans tous les écrans, mort, déconnexion, curseur et
  grilles d'artisanat, présentoirs, boîte aux lettres hors du monde (écriture atomique, copie lue effacée seulement après
  l'enregistrement du joueur, qui est fait sur le fil du serveur) : pas de copie trouvée (hors plantage, point 3 ci-dessus).
- Camos à vie (`CamosAVie`, `CollectionCamos`) : jeton par arme, vérification étalée sur 20 ticks, fichier illisible
  jamais réécrit, à terre et en match refusés ; camo posé depuis un objet consommé une seule fois ; stickers et StatTrack
  bornés (5 au plus, valeurs finies et bornées, os de 64 lettres au plus).
- Registre de l'armurier (`ArmurierServeur`) : op 4 revérifié à chaque paquet, lecture des paquets bornée, recette
  validée par TaCZ avant d'être gardée, fichier écrit puis renommé.
- Vitres (`VitresServeur`) : zones protégées (spawn, arènes, labo, claims de CRac), eau voisine, rien ne tombe autour ;
  balles figées (`BallesEnVol`) ; dégâts contre les joueurs et les zombies (`DegatsArmes`, `ZombiesArmes`) ; brûlures du
  molotov et des balles incendiaires (`Brulures`) ; son lointain des silencieux (seuls les joueurs qui voient le tronçon).
- Paquets du serveur vers les joueurs : chaque canal impose son sens (Forge refuse un paquet dans le mauvais sens avant
  de le lire).
- Ce qui tourne à chaque tick ou chaque image : soit très court (une case lue), soit étalé (une fois par seconde, un joueur
  sur 20), soit déjà gardé d'une image à l'autre (HUD des munitions, superposition des tenues, masquage du chapeau,
  animations de Player Animator).

**Équipement (heraliz_armory)**
- Maj + clic vers les cases Curios : l'objet est déplacé, jamais copié. Mannequin : écran fermé dès qu'il est cassé ou
  trop loin, contenu rendu à la casse, rien rendu deux fois (`kill()` sans chute de Curios). Tenues complètes (kits),
  cible d'essai (choix limité, ramassage réservé au staff), lunettes de vision nocturne (bascule limitée).
- Contenu des sacs portés envoyé seulement au porteur (`CuriosContenuPorteurMixin`, `ContenuReserve`, 20/11) : cibles
  `syncCurios` et `lambda$playerStartTracking$16` de Curios 5.14.1 vérifiées, paquet mis en forme à chaque envoi (le
  porteur reçoit bien tout), relevés de Curios copiés (rien n'est retiré du vrai sac).
- Protections : un seul calcul, plafond commun ; rien de lourd pour les zombies (pas de Curios sur eux).

**Sacs (Backpacked)**
- Écran d'un sac : valable seulement tant que ce même objet est sur le dos de son porteur vivant ; clics refusés sinon ;
  contenu écrit dans l'objet à chaque changement ; mémoire oubliée dès que le sac quitte le dos ou est changé ailleurs.
- Pas de sac dans un sac (objets bannis, `canFitInsideContainerItems` faux), étagère des sacs jamais enregistrée,
  personnalisation refusée, sac d'un autre joueur et sacs des marchands ambulants coupés par les réglages.

**Liens avec les autres modules** : toutes les cibles lues par réflexion depuis Loot, Bases, Monde, Boutique, Quêtes,
Survie, ClashWar, Clans, Interface et Noyau existent avec la même signature (`ObjetsAVie.estAVie`, `contientAVie`,
`retirerContenuAVie`, `neJamaisDetruire`, `ObjetsAVieServeur.oublier`, `CamosAVie.wipeReouverture`,
`AllongeServeur.restantTrain` / `releveTrain`, `AllongeClient.allongeTrain` / `releveTrain`, `Couteaux.estCouteau`,
`SuitRules.mayEquipCurio`, `PieceItem.def`, `PieceDef.piece`, `CustomItemCoolDowns(Provider)`, `HeraliszTacticalAPI`,
`BackpackedInventoryAccess`, `BackpackInventory.saveItemsToStack`, `Services.BACKPACK`, `Network.getPlay`,
`MessageOpenBackpack`, `Keys.KEY_BACKPACK`…). Tous les `@Mod.EventBusSubscriber` ont leur `modid`.

---

## À tester en jeu

- [ ] **Vitres** : casser des vitres (verre plein, vitre fine, fenêtre de meubles `cfm:vb_4`) : elles cassent comme avant
      (pas dans les zones protégées). Attendre le délai (5 min) en restant à côté : elles repoussent, raccordées aux
      vitres fines voisines. Revenir en courant (ou par `/tp`) dans un coin plein de vitres cassées : aucune alerte
      « [Diag] Blocage » pendant l'arrivée.
- [ ] **Sacs** : ouvrir son sac, ranger, sortir, Maj + clic, mettre le sac dans un coffre et le reprendre : comme avant.
      Marcher, courir, nager, cliquer et casser des blocs : rien ne change ; journal du serveur sans erreur « backpacked ».
      `/diag profil` un soir chargé : « sacs · backpacked » plus bas dans le tick des joueurs qu'avant.
- [ ] **Démarrage du serveur** : il démarre comme avant (rien ne doit plus parler d'adresse IP ni de clé).
- [ ] **Arme de mêlée** : un autre joueur avec une arme de mêlée en main, vu en 3e personne : il marche, court et attend
      avec les mêmes gestes qu'avant.
- [ ] **Sac avec un camo** dans l'inventaire : la petite pastille du camo en bas à droite de l'icône, comme avant.

Pas testé en jeu : rien de ce document.
