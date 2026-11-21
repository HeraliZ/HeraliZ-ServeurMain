# Validation de l'audit du 21/11 — v4 : armes, équipement, sacs

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider »), de
`modules/armes` (HeraliZ Tactique et le code repris de LR Tactical, GPL), `modules/equipement` (heraliz_armory,
heraliz_composants) et `modules/sacs` (Backpacked de MrCrayfish, LGPL). Point de départ : la branche en ligne du 21/11 à
17 h 43 (heure UTC, `6638bb00a`).

Deux travaux :
1. relire **chaque changement du jour** de la zone, sans faire confiance à la première relecture, en particulier le
   retour en arrière des skins (Armes 2.59.11) et l'étape 1 des « dessins des skins » (Armes 2.59.12) ;
2. relire **toute la zone** à nouveau : copies d'objets (sacs, décharger, objets à vie, atelier, Curios), droits, paquets
   envoyés par les joueurs, lenteurs du serveur, images par seconde chez les joueurs, mods des autres auteurs, liens par
   réflexion avec nos autres mods.

**Résultat en bref** : tous les changements du jour sont bons. Aucun bug, aucune copie d'objet, aucun plantage trouvé.
Une seule inexactitude, dans la fiche `docs/SKINS_PROTEGES.md` (les nombres que le staff doit lire dans les journaux) :
corrigée. **Aucun code changé**, donc aucune version à monter : Armes **2.59.12**, Équipement **1.4.2**, Sacs
**3.0.1-beta.4-heralisz28** restent ceux qui sont en ligne ; rien à télécharger de plus pour les joueurs.

**Suite du soir** (demande du propriétaire, section « Fin du 21/11 » en bas) : étape 2 des skins protégés et petites
corrections, **Armes 2.59.14** et **Sacs 3.0.1-beta.4-heralisz29**, contrôle de branche vert.

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, essais hors du jeu (voir « Vérifié sans problème »),
journaux du serveur en ligne lus par la tâche GitHub « Journaux du serveur », contrôles de GitHub.

Gravité : **important** (faille ou erreur qui touche les joueurs), **moyen** (abus possible, message faux), **faible**
(petit coût, bruit dans le journal ou cas très rare).

## Changements du jour validés

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `3c5a20cf9` (Armes 2.59.9) | vitres qui repoussent seulement dans un tronçon complet ; deux économies par image | **OK**. `VitresServeur.passe` (l. 406-414) demande `Troncons.charge` (fil du serveur seulement, c'est bien le cas : tick du serveur et balle reçue) ; en jeu le budget de chargements vaut 0, donc rien n'est jamais attendu ; au démarrage, budget inchangé. La vitre reposée ne prévient pas ses voisins (`DRAPEAUX` = envoi aux joueurs + forme connue, l. 82) : `setBlock` ne relit aucun bloc voisin, aucune attente cachée. Une vitre fine au bord d'un tronçon pas prêt garde son ancien raccord de ce côté (l. 460, 483, 500) : seulement visuel, rare, déjà écrit. `BagCamo.Badge` (image gardée par camo) et `ThirdPersonAnimationConfig` (champ `transient`, ignoré par Gson) : justes. |
| `e84547c77` (Sacs heralisz28) | défis de déblocage coupés quand tout est débloqué ; reliquats d'un ancien serveur retirés | **OK**. `UnlockManager.defisSuivis` (l. 54) : réglage pas encore lu = `IllegalStateException` de Framework, rattrapée, défis suivis comme avant. Le réglage en ligne est bien `unlockAllCosmetics = true` (`serveur/config/backpacked.server.toml` l. 57). Les injections qui suivent celles qu'on coupe testent déjà « rien de capturé » (`ServerPlayerGameModeMixin` l. 82 et l. 125) : sauter la capture ne casse rien. Plus aucune référence à la classe retirée ni à la clé ; `NOTICE.md` (LGPL) décrit le changement. |
| `bb4da7c10` (Armes) | objet à vie refusé aussi dans la caisse de largage posée en bloc, côté serveur | **OK**. `TEMPORAIRE` (`ObjetsAVieServeur` l. 144-152) suit les classes mères, gardé par classe (`ClassValue`) ; les deux noms existent (`fr.heraliz.airstrike.block.SupplyCrateBlockEntity` de Monde, `fr.heralisz.clans.territory.CaisseRavitaillementBlockEntity` de Clans). La caisse du largage public (une entité) refuse déjà tout dépôt. |
| `f324509e4` (partie équipement) | noyau déclaré des deux côtés pour heraliz_armory | **OK**. `modules/equipement/src/commun/resources/META-INF/mods.toml` l. 57-61 : `side="BOTH"`, `[1.0.19,)` ; Équipement 1.4.2 en ligne. |
| `dec614808` puis `9c3b34e9f` (Armes 2.59.10 puis 2.59.11) | skins liés au joueur, puis **retour comme avant** en gardant la collection écrite en fond et la cadence | **OK, retour complet et cohérent.** Comparaison de l'état d'avant `dec614808` avec `9c3b34e9f` : `CamoData`, `CamoItem`, `OngletCamo`, `RefitSkinTab` et les textes fr/en/ru sont identiques à l'octet près à ceux d'avant ; dans `CamosAVie` et `ReceptionServeur`, seule la ligne de cadence reste (l. 117 ; l. 39-40 et l. 80). 2.59.10 n'avait ajouté aucune donnée nouvelle sur les armes : les camos posés pendant l'heure où il était en ligne sont des camos à vie ordinaires (propriétaire et jeton), gardés. **Cadence** (`Cadence.java`) : appelée seulement sur le fil du serveur (les trois paquets passent par `enqueueWork`), oubliée à la déconnexion et à l'arrêt ; chaque bouton de l'atelier envoie un seul paquet (`RefitSkinTab` l. 103, 113, 122 ; `OngletCamo` l. 241, 244, 253 ; `RefitDecorTab` l. 228) : un joueur normal ne perd aucun clic. **Collection en fond** (`CollectionCamos` l. 170-217) : arbre JSON neuf relevé sur le fil du serveur, drapeau `modifie` `volatile` remis à vrai si l'écriture rate, fichier `.tmp` puis remplacement, écriture attendue au démarrage (l. 81), à l'arrêt (l. 156) et au wipe (l. 262) ; fichier illisible au démarrage : toujours jamais réécrit (fichier de secours). Méthodes du noyau utilisées (`EcrituresFond.voie(..).ecrire / attendre`) : présentes, même signature. |
| `010c4d504` et `6638bb00a` (Armes 2.59.12) | dessins des skins envoyés par le serveur (étape 1), contrôle « dessins » | **OK**, une seule inexactitude dans la fiche (nombres du journal), corrigée (voir « Corrigé »). Détail ci-dessous. |

### Les dessins des skins (étape 1) en détail

- **Le serveur** (`DessinsServeur`) lit la copie une seule fois, au démarrage (l. 58-104) : 132 ms sur le Ptero (journal du
  21/11 à 17 h 45), sur le fil du serveur mais avant l'arrivée des joueurs. Il garde 88 Ko en mémoire et envoie les mêmes
  octets à chaque connexion (l. 107-118), jamais à un faux joueur. Il ne met jamais le contenu des dessins dans le journal.
- **Taille des paquets** : un morceau fait au plus 900 000 octets (`DessinsCopie.TAILLE_PARTIE`), sous la limite d'un
  paquet vers un joueur (1 Mo) ; aujourd'hui un seul morceau de 88 Ko.
- **Contrôles chez le joueur** : lecture bornée (`DessinsPacket.decode` : empreinte de 64 lettres au plus, morceau de
  900 000 octets au plus) ; `DessinsRecus.morceau` (l. 64-101) refuse un numéro de morceau, un nombre de morceaux ou une
  taille totale hors limites, ignore un morceau en double, vérifie la taille assemblée ; à l'ouverture (seulement pour
  l'essai du staff), décompression bornée (4 096 fichiers, 32 Mo) puis empreinte SHA-256 comparée à celle annoncée.
- **Fils** : réception sur le fil du réseau, sous verrou, sans toucher au jeu ; tout le reste (`fichiers()`, les
  programmes) sur le fil du rendu (`enqueueWork`, puis `RenderSystem.isOnRenderThread`). La copie reçue remplace la
  précédente de façon sûre (`volatile`).
- **Mémoire et disque** : 88 Ko gardés pour la session (0,7 Mo de plus seulement après `/camos dessins`) ; rien n'est
  jamais écrit sur le disque, à part une ligne de journal.
- **Rien ne change pour les joueurs** : tant que `/camos dessins` n'a pas été tapé, `CamoShaders.onRegisterShaders`
  (l. 85-102) fait exactement comme avant (mêmes sources, même empreinte, mêmes programmes). Seul coût : 88 Ko reçus à la
  connexion. Le canal des camos passe en version 4 : un jeu d'avant la 2.59.12 est refusé à la connexion (normal, le
  launcher donne le bon jar).
- **`/camos dessins` ne peut pas servir à nuire** : op 2 (`CommandeCamos` l. 46), n'agit que sur le jeu de celui qui la
  tape (l. 55), ne touche ni au serveur ni aux autres joueurs ; le résultat renvoyé par le jeu n'est noté que si le
  serveur a fait la demande à ce joueur (`DessinsServeur.bilan`, nombres bornés), et la demande en attente est oubliée à
  la déconnexion et à l'arrêt. Les paquets des mods d'un joueur pas encore vérifié (jeton du launcher + `/login`) ne
  passent de toute façon pas la porte de Spartan (`PorteJoueurs`).
- **Refaire les programmes** (`CamoShaders.refaireDepuisServeur`, l. 147-176) : ferme seulement nos morceaux de programme
  en réserve chez Minecraft (noms `heralisz_camo:camo…` et `heralisz_camo:fx…`, jamais le sticker), puis les anciens
  programmes, puis refait les 79 ; tous les rendus des skins relisent le programme à chaque image (`CamoRenderType`,
  `FxRenderTypes`, `CamoFx`, icônes vidées) : aucun ne garde l'ancien. F3+T ensuite : gardés si rien n'a changé.
- **L'inexactitude** : la fiche annonçait « 161 fichiers (693 Ko) compressés en 87 Ko », le serveur en ligne écrit
  « **167** fichiers (**698** Ko) compressés en **88** Ko ». Cause trouvée et vérifiée hors du jeu : la livraison
  (`outils/construire.py` l. 157, `marquer`) pose la mention de licence `LISEZ-MOI-HeraliZ.txt` (934 octets) dans chaque
  dossier de nos jars, donc aussi dans les 6 sous-dossiers de la copie. Le jeu les ignore (un nom avec des majuscules
  n'est pas un nom de ressource valable : `DessinsRecus.fichiers`, l. 129-131). Ce n'est pas un défaut du code, mais le
  staff aurait pu croire l'essai raté : la fiche donne maintenant les bons nombres et l'explication. Le contrôle
  « dessins » de `outils/verifier.py` regarde les jars fabriqués, avant cette mention : il reste juste.

## Corrigé

- `54c25db08` — `docs/SKINS_PROTEGES.md` : nombres attendus dans les journaux pour l'essai du staff (167 fichiers,
  698 Ko, 88 Ko compressés, envoi de 88 Ko) et paragraphe « Pourquoi 167 fichiers et pas 161 » ; temps mesuré sur le
  Ptero (132 ms). Aucun code changé, aucune version à monter.

## À décider par le propriétaire

1. **`/camos ajouter <joueur> tous` à l'op 2 (moyen, rappel du point A28 de l'audit du 26/10, toujours ouvert)** :
   `CommandeCamos.java` l. 46 et l. 86-89. Un membre du staff op 2 peut se donner (ou donner à un ami) tous les camos et
   skins à vie, qui sont vendus à la boutique, et `/camos retirer` les enlever. Ailleurs, ce qui crée de la valeur
   demande l'op 4 (`/eco donner`, `/bourse donner`). Dans le dépôt, rien ne lance `/camos ajouter` (ni caisse, ni
   casino) ; si le site de la boutique la lance, il passe par la console, qui a l'op 4 : rien ne casserait. Proposition :
   `ajouter` et `retirer` à l'op 4, `voir`, `recharge` et `dessins` restent à l'op 2. À savoir avant de décider : qui est
   op 2 ou 3 dans `ops.json` du Ptero (déjà demandé par la synthèse).
2. **Deux erreurs à chaque démarrage du serveur (faible, bruit dans le journal)** : « ear_protection is not a registered
   slot type! » et « face is not a registered slot type! » (vues deux fois chacune dans chaque démarrage d'essai).
   `modules/sacs/src/serveur/resources/data/backpacked/curios/entities/ear_protection.json` et `face.json` donnent ces
   cases Curios aux joueurs, mais aucune case de ce nom n'est définie : Curios les ignore. Les capuches
   `geckolib:hood_*` (rangées « face » par `data/curios/tags/items/face.json`) ne peuvent donc pas aller dans une case
   Curios « face ». Proposition : soit retirer ces deux fichiers (aucun changement en jeu, deux erreurs de moins), soit
   créer une case « visage » pour les capuches (choix de jeu, demande un rendu). Rien fait : ce n'est pas un bug.
3. **Mêlée de LR Tactical : une ligne de journal par paquet trafiqué (faible)** : `CMeleeAttackRequest.java` l. 44-46
   écrit une ligne dans le journal du serveur à CHAQUE paquet qui vise trop de cibles. Un jeu normal ne l'envoie jamais
   (il coupe lui-même la liste) ; un tricheur connecté pourrait en envoyer des milliers par seconde et grossir le journal.
   Proposition : une ligne au plus toutes les 30 s par joueur (code GPL : modification à noter dans sa NOTICE).

Les propositions de la première relecture que le propriétaire n'a pas choisies restent telles quelles (objet à vie et
plantage n° 3, jambe cassée et téléportation n° 4, version minimale du noyau n° 5, réglage `enabledPickpocketing` n° 6,
gros chargeurs n° 7) : rien de nouveau à leur sujet.

## Vérifié sans problème

**Mods des autres auteurs**
- Les jars de compilation (`libs/`) et ceux du serveur (`serveur/mods-tiers/`) sont **les mêmes fichiers** (empreintes
  MD5 identiques) : TaCZ 1.1.8-hotfix, Curios 5.14.1, Framework 0.7.6, GeckoLib (geckolib222) 4.4.7, Player Animator
  1.0.2-rc1.
- `outils/verif_mixins.py armes equipement sacs` : 78 mixins, 174 injections, **0 erreur** (toutes les cibles de TaCZ,
  Curios, GeckoLib, Backpacked existent avec leurs descripteurs). Avertissements connus : deux `@Overwrite` de TaCZ
  (`BedrockCubeBox.compile`, `BedrockCubePerFace.compile`, aucun autre mod ne les vise) et la config serveur
  d'heraliz_armory en `defaultRequire = 0` (ses trois cibles vérifiées présentes).
- `outils/verif_reflexion.py` (tous les modules) : **0 erreur** ; 43 appels par réflexion vers cette zone vérifiés
  (`ObjetsAVie`, `ObjetsAVieServeur.oublier`, `CamosAVie.wipeReouverture`, `AllongeServeur`, `AllongeClient`,
  `Couteaux.estCouteau`, `SuitRules`, `PieceItem`, `PieceDef`, `CustomItemCoolDowns(Provider)`, `BackpackInventory`,
  `BackpackedInventoryAccess`, `Services`, `BackpackLayer`, `EcranInventaire`, `IThrowable`…). Le seul appel au nom
  calculé (ClashWar, `compat/Tacz.java` l. 218) vérifié à la main : `HeraliszTacticalAPI.getConsumableIndex`,
  `getThrowableIndex` et `getMeleeIndex(ItemStack)` existent, publiques et statiques.

**Essais hors du jeu (dossier de travail, pas dans le dépôt)**
- `DessinsCopie` compilé à part et essayé sur les 161 vrais dessins : aller-retour identique, même empreinte ; découpe
  d'un bloc de 2 Mo en 3 morceaux ; refus d'une taille négative, d'un trop grand nombre de fichiers, d'un format inconnu
  et d'octets abîmés. Avec les 6 mentions de licence : 167 fichiers, 698 Ko, 88 Ko, exactement la ligne du Ptero.
- Lecture de la copie dans un jar fait comme les nôtres, avec le vrai lecteur de jars de Mohist 1.20.1-916
  (securejarhandler « mohist-2.1.11 », comme `ModFile.findResource` de Forge 47.3.12) : 161 fichiers, 709 118 octets. Les
  6 fichiers de plus du Ptero viennent donc bien de la livraison, pas de la lecture.

**Relecture de la zone (nouvelle passe)**
- Paquets envoyés par les joueurs, relus à nouveau : camos, stickers, collection, bilan des dessins, décharger,
  armurier, couteaux, mêlée de LR, objets à vie, sacs. Chacun passe par le fil du serveur, chaque case et chaque nombre
  reçu est borné, et tout ce qui donne ou modifie un objet est revérifié par le serveur. Les lectures sans borne écrite
  restent bornées par la taille d'un paquet. Seulement survolés (déjà relus en détail par la première relecture, rien de
  changé depuis) : allonger et plonger, cible d'essai, vision de nuit, mannequin.
- Éditeur du Registre de l'armurier : op 4 revérifié à chaque paquet ; un pseudo d'op pris sans son mot de passe (serveur
  en mode cracké) n'arrive à rien : la porte de Spartan retient tous les paquets des mods tant que le jeton du launcher et
  le `/login` ne sont pas faits.
- Décharger : seulement les cases de l'inventaire du joueur (0 à 35 et 40), côté joueur la case n'est envoyée que si elle
  est à lui ; armes de match refusées ; balles jamais copiées.
- Objets à vie : clics, jet, mort, déconnexion, question restée sans réponse, boîte aux lettres (copie lue effacée
  seulement après l'enregistrement du joueur) : pas de copie ni de perte trouvée.
- Tenues complètes (kits) : la pièce remplacée est copiée puis le slot remplacé, l'objet kit consommé : rien en double.
  Maj + clic vers Curios : rangé par le serveur, déplacé, jamais copié.
- Sacs : écran valable seulement tant que la même pile est sur le dos de son porteur vivant ; Minecraft refuse tout clic
  dans un écran plus valable ; contenu écrit dans l'objet à chaque changement.
- Cartes des joueurs gardées en mémoire (couteaux, jambe cassée, labo sous-marin, cadence, dessins, objets à vie) :
  toutes vidées à la déconnexion ; compteurs des tirs refusés vidés toutes les 30 s.
- Ce qui tourne à chaque tick : soit très court (une case lue, une carte consultée), soit étalé (une fois par seconde,
  un joueur sur 20, un tour sur 10) ; grenades sans calcul de destruction de blocs ; détonateur limité au monde du
  joueur.
- Images par seconde : aucune adresse d'image refaite à chaque image dans les rendus de la zone (recherche dans tout le
  code client) ; les programmes des skins sont relus à chaque image par leur rendu, jamais gardés ailleurs.
- Journal du serveur en ligne (tâche « Journaux du serveur » du 21/11 à 17 h 46) : aucune erreur Java ; la ligne des
  dessins est là.

## À tester en jeu

- [ ] **Essai du staff `/camos dessins`** (fiche `docs/SKINS_PROTEGES.md`, partie « L'essai du staff ») : bandeau
      « 79 programmes prêts », rendu identique, F3+T sans gel. Dans les journaux, lire **167 fichiers, 698 Ko, 88 Ko**
      (pas 161 / 693 / 87 : voir plus haut, c'est normal).
- [ ] **Journal du serveur au démarrage** : `[Camos] dessins des skins : 167 fichiers (698 Ko) compressés en 88 Ko,
      1 paquet(s) par joueur` ; à chaque connexion : `… envoyés à <pseudo> : 88 Ko en 1 paquet(s)`.
- [ ] **Skins comme avant** (section 50 de la fiche des essais) : objet camo posé depuis l'atelier = définitif, l'objet
      est utilisé ; camo de la collection posé et retiré ; stickers posés, déplacés, grattés à un rythme normal : aucun
      clic perdu ; collection toujours là après un redémarrage.
- [ ] Le reste de la zone : comme la liste de la première relecture (`a4_armes_equipement_sacs.md`, « À tester en
      jeu ») : vitres, sacs, arme de mêlée en 3e personne, pastille du camo d'un sac.

Pas testé en jeu : rien de ce document (relecture, essais hors du jeu et contrôles de GitHub seulement).

## Fin du 21/11 (demande du propriétaire : « tout lançable et terminé à 100 % »)

Point de départ : en ligne, `main` = `1607d9655` (Armes 2.59.13 : `/camos ajouter` et `/camos retirer` déjà passés à
l'op 4 par le coordinateur, c'était ma proposition n° 1). Fait ensuite sur `carte-ile-reglages` : **Armes 2.59.14**,
**Sacs 3.0.1-beta.4-heralisz29**. Contrôle de branche de GitHub **vert** pour `19008fffa` (run 44950624530 : fabrication,
contrôles et images ; le nouveau contrôle « dessins » est passé sur les vrais jars).

### 1. Skins protégés, étape 2 (`cdba3098b`, Armes 2.59.14)

En simple : le jar que les joueurs téléchargent **n'a plus aucun dessin des skins** (vérifié sur le jar fabriqué par
GitHub). Le serveur en envoie une copie à chaque connexion (étape 1, en ligne depuis 17 h 43) et le jeu fabrique ses
programmes depuis elle, tout seul, juste après la connexion. Le rendu est le même qu'avant (mêmes programmes). Détail
complet, ce qu'un joueur voit et limites : `docs/SKINS_PROTEGES.md`.

- **Sources** : `modules/armes/dessins/` (161 fichiers déplacés avec Git, un seul exemplaire), hors des assets ; le
  sticker reste dans le jar des joueurs. `modules/armes/build.gradle` : la copie des jars -serveur et -complet part de ce
  dossier.
- **Fabrication chez le joueur** (`CamoShaders`) : quand la copie est complète (`DessinsRecus.morceau`), le fil du rendu
  décide (`fabriquerDepuisCopie`) ; les 79 programmes sont compilés un par un au début de chaque image (25 ms au plus par
  image, au moins un programme : l'écran de chargement reste vivant au lieu d'un gel de plusieurs secondes), rien pendant
  un écran de rechargement des ressources ; à la fin, icônes 3D vidées et une ligne au journal. Une seule fois par copie :
  une reconnexion qui apporte la même empreinte, ou un F3+T sans changement, ne refait rien (et la copie n'est pas
  redécompressée). Démarrage du jeu sans copie : aucun programme, une seule ligne au journal (avant le déplacement, il y
  aurait eu une erreur par programme absent). Rien reçu 30 s après l'arrivée sur un serveur : une ligne au journal. Copie
  abîmée ou programme qui ne se fabrique pas : skin sans effet, jamais de plantage. `/camos dessins` refait tout pour le
  staff (bandeau et résultat au serveur, comme à l'étape 1). Textes des deux bandeaux d'erreur du staff remis au vrai
  (fr, en, ru : « les skins restent sans effet », « rien n'est refait » ; plus de « dessins du jeu »).
- **Outils** : `generer_camos.py` (chemins ; relancé : seule la ligne de commentaire des 73 `camo_<n>.fsh` change,
  l'include et les effets sont identiques), `skins_couteaux.py`, `apercu_camos` (`verif_glsl.py`, `rendre.js`,
  `README.md`), `outils/video_vitrine/video.py` (et un contrôle si `rendre.js` change encore), `outils/textures_inutiles.py`
  (lit aussi `modules/<m>/dessins/`). Contrôle « dessins » de `outils/verifier.py` : copie des jars -serveur et -complet
  = le dossier des sources, et aucun `camo*`, `fx*` ni `heralisz_camo.glsl` dans les assets des jars -client et -complet
  (le sticker y reste).
- **Paquets** : rien ne change (canal des camos en version 4). Côté réseau, un jeu 2.59.12 ou 2.59.13 sur un serveur
  2.59.14 marche encore (il garde ses dessins), un jeu 2.59.14 sur un serveur 2.59.12 ou 2.59.13 aussi (ce serveur envoie
  déjà la copie) ; en pratique, Spartan n'accepte que les jars de la même fabrication que le serveur.
- **À savoir (limite, important)** : le dépôt GitHub est **public** ; les sources des dessins y restent lisibles par tout
  le monde (`modules/armes/dessins/`, et leurs modèles `modules/armes/outils/camo_gabarit.fsh` et `fx_gabarit.fsh`).
  La protection contre la copie ne vaut vraiment que si le dépôt passe en privé : **à décider par le propriétaire**.

### 2. Sacs : deux cases Curios inutiles retirées (`441d0cd86`, Sacs heralisz29)

`modules/sacs/src/serveur/resources/data/backpacked/curios/entities/ear_protection.json` et `face.json` retirés : ces
cases n'ont jamais existé (aucun mod ne les définit), Curios écrivait une erreur pour chacune à chaque démarrage. Vérifié :
rien ne s'en sert (les listes des combinaisons d'Équipement qui citent « face » et « ear_protection » ne regardent que les
cases existantes ; les listes d'objets `data/curios/tags/items/…` restent). Aucun changement en jeu. Noté dans
`modules/sacs/NOTICE.md` (LGPL).

**Résultat vérifié sur le démarrage d'essai de GitHub** (run 44950704890, `19008fffa`, serveur prêt en 76 s) : les erreurs
« face / ear_protection is not a registered slot type! » passent de **deux de chaque à une de chaque**. La dernière vient
d'un jar d'un **autre auteur**, `serveur/mods-tiers/geckolib222-forge-1.20.1-4.4.7.jar`, qui donne lui aussi ces deux
cases aux joueurs (`data/geckolib/curios/entities/face.json` et `ear_protection.json`). Pas touché (jamais de retouche
dans le jar d'un autre auteur, et hors de ma zone). **À décider** : l'alléger à la livraison comme TaCZ et les meubles
(`ALLEGES` de `outils/construire.py` : copie sans ces deux fichiers, `CONTENU-RETIRE.txt` qui dit quoi et pourquoi,
licence gardée) ferait disparaître les deux dernières erreurs, sans rien changer en jeu ; mais ce jar de 15 Mo est aussi
donné aux joueurs, qui le retéléchargeraient une fois.

### 3. Mêlée : journal limité (`cdba3098b`)

`CMeleeAttackRequest` (code de LR Tactical, GPL) : une demande qui vise trop de cibles (seul un jeu modifié en envoie)
est toujours refusée, mais n'écrit plus qu'une ligne au journal toutes les 30 s par joueur, avec le nombre de demandes
semblables ignorées depuis (avant : une ligne par demande). Carte à clés faibles : un joueur parti est oublié tout seul.
Noté dans `META-INF/heralisz_tactical-NOTICE.md`.

### 4. Version minimale du noyau (`cdba3098b`)

`modules/armes/src/commun/resources/META-INF/mods.toml` : noyau `[1.1.5,)` au lieu de `[1.0.6,)` (relevé de v10 : les
armes se servent depuis le 19/11 des tronçons complets, des écritures en fond et de l'inventaire retenu par un match,
apparus dans le noyau 1.1.5). Le noyau en ligne est en 1.1.12 : rien ne change, sauf un refus clair au lieu d'un
plantage si un jour un vieux noyau traînait.

### Essais hors du jeu

- Contrôle « dessins » essayé sur de faux jars : bons jars 0 erreur ; un dessin resté dans le jar des joueurs : 1
  erreur ; copie incomplète : 1 erreur. Puis sur les vrais jars de GitHub : 161 fichiers dans -serveur et -complet, aucun
  dessin dans les assets de -client et -complet.
- Copie de l'étape 2 recalculée : 161 dessins, 167 fichiers avec les mentions de la livraison, 698 Ko, 87 Ko compressés.
- Chemins de tous les outils vérifiés (dossiers et fichiers trouvés), syntaxe des 5 outils Python et des 4 fichiers Java
  changés analysée (`javac`) ; la fabrication complète est celle de GitHub (verte).

### À tester en jeu (avant de mettre l'étape 2 en ligne)

- [ ] **Premier lancement, puis connexion** (jeu et serveur 2.59.14) : les skins apparaissent pendant ou juste après
      l'écran de chargement, puis le rendu est identique à avant : camo animé (Dark Matter), effets 3D (Tempête, Séraphin,
      Prisme), finitions de couteaux (Doppler, Fade), sac camouflé ; en main, en 3e personne, dans l'atelier, sur les
      icônes de l'inventaire. Journal du jeu : « fabriqués depuis la copie du serveur : 79 programmes prêts, 0 en échec ».
- [ ] **Déconnexion puis reconnexion** : « même copie que celle déjà en place, rien à refaire ». **F3+T** : rien de
      refait, pas de gel.
- [ ] **`/camos dessins`** (staff) : bandeau « 79 programmes prêts… · … ms » ; même résultat dans le journal du serveur.
- [ ] **Monde solo d'essai** (jars -complet) : mêmes skins.
- [ ] **Démarrage du serveur** : « ear_protection / face is not a registered slot type! » une seule fois chacune (au lieu
      de deux, la dernière vient de geckolib222) ; cases Curios des joueurs inchangées (sac, ceinture, mains, corps,
      bracelet, collier, bague, charme).
- [ ] **Mêlée** : coups normaux comme avant (rien ne change pour un jeu normal).

Pas testé en jeu : rien de cette section (relecture, essais hors du jeu, fabrication et contrôles de GitHub seulement).
