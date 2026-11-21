# Skins protégés : les dessins des skins d'armes donnés par le serveur

Mis à jour le 21/11. **Étape 1 faite** (Armes 2.59.12, branche `carte-ile-reglages`, pas encore en ligne). **Étape 2 pas
faite** : elle attend l'essai du staff décrit plus bas.

## En bref (pour le propriétaire)

**Le problème.** Les « dessins » des skins d'armes sont de petits programmes pour la carte graphique : le motif et
l'animation des camos, les effets 3D (Camouflage Tempête, Skins X), les finitions des couteaux (Doppler, Fade…). Aujourd'hui
ils sont dans le jar que chaque joueur télécharge : quelqu'un qui récupère nos jars peut les reprendre tels quels. Les
skins de couteau, eux, sont déjà protégés d'une autre façon : leurs fiches TaCZ ne sont que sur le serveur.

**La solution, en deux étapes.**

1. **Étape 1 (faite, invisible pour les joueurs).** Le serveur a sa propre copie des dessins. À chaque connexion, il
   l'envoie au jeu du joueur : 161 fichiers, 87 Ko une fois compressés, en un seul paquet. Le jeu la garde **en mémoire
   seulement**, jamais sur le disque, et **ne s'en sert pas** : il continue de dessiner avec les dessins de son jar. Pour
   les joueurs, rien ne change (rien de compilé en plus, pas de lag).
2. **Étape 2 (à faire après l'essai).** On retire les dessins du jar des joueurs. Le jeu les fabrique depuis la copie
   reçue, une fois, pendant l'écran de connexion. Même qualité, pas de lag en jeu, une connexion de quelques secondes
   plus longue.

**Avant l'étape 2, un membre du staff fait l'essai sur son PC** avec la commande `/camos dessins` (ci-dessous) : son jeu
refait tous les dessins depuis la copie du serveur, exactement comme le fera l'étape 2. Si le rendu est le même et
qu'aucun dessin n'échoue, l'étape 2 peut être faite.

## L'essai du staff (étape 1)

Il faut que le serveur et le jeu soient en Armes 2.59.12 ou plus (la mise en ligne de l'étape 1 est au choix du
propriétaire ; elle ne change rien pour les joueurs). Un serveur d'essai sur un PC convient aussi.

1. Se connecter. Mettre dans la barre d'objets quelques armes skinnées : un camo animé (Camouflage Dark Matter), un camo
   à effet 3D (Camouflage Tempête, ou un Skin X comme Séraphin ou Prisme), un couteau avec une finition (Doppler Rubis,
   Fade…), et si possible un sac camouflé.
2. Taper **`/camos dessins`** (staff, op 2). Le chat répond « Ton jeu refait les dessins des skins depuis la copie reçue
   du serveur… ». Le jeu peut se figer un court moment (il fabrique 79 programmes), puis un bandeau apparaît à droite :
   **« Dessins des skins — 79 programmes prêts depuis la copie du serveur · … ms »**.
3. Regarder les mêmes skins : **le rendu doit être identique** (couleurs, animation, reflets, effets 3D), en main, en
   3e personne (sur soi et sur l'arme d'un autre joueur), dans l'atelier (onglet Camo), sur les icônes de l'inventaire
   (elles se refont toutes seules en quelques secondes) et sur le sac.
4. Appuyer sur **F3+T** (rechargement des ressources) : rien n'est refait (pas de gel en plus), les skins restent pareils.
5. Noter le temps affiché par le bandeau : c'est à peu près le temps que la connexion prendra en plus à l'étape 2 sur ce
   PC. Envoyer le journal du jeu (`logs/latest.log`).

Ce qu'il ne faut pas voir :
- un bandeau rouge « Rien reçu du serveur » : le serveur n'a pas envoyé la copie (regarder son journal, et la version
  d'Armes des deux côtés) ;
- un bandeau rouge « Copie du serveur illisible » : la copie est arrivée abîmée (envoyer le journal du jeu) ;
- un bandeau orange « … prêts, … en échec (voir le journal) » : un ou plusieurs dessins ne se fabriquent pas depuis la
  copie. Envoyer `logs/latest.log`. Ces skins s'affichent sans effet jusqu'à ce que le jeu soit relancé (il reprend alors
  les dessins de son jar).

Lignes attendues dans le journal du jeu (`logs/latest.log`) :
- à la connexion : `[Camos] dessins des skins reçus du serveur : 161 fichiers, 693 Ko (87 Ko compressés, 1 paquet(s)), empreinte … ; gardés en mémoire seulement` ;
- après la commande : `[Camos] dessins des skins refaits depuis la copie du serveur : 79 programmes prêts, 0 en échec, … ms` ;
- au F3+T : `Shaders des camos inchangés : 79 programmes gardés, rien recompilé`.

Lignes attendues dans le journal du serveur :
- au démarrage : `[Camos] dessins des skins : 161 fichiers (693 Ko) compressés en 87 Ko, 1 paquet(s) par joueur, empreinte …, … ms` ;
- à chaque connexion : `[Camos] dessins des skins envoyés à <pseudo> : 87 Ko en 1 paquet(s)` ;
- à la commande : `[Camos] /camos dessins : le jeu de <pseudo> refait ses programmes des skins depuis la copie du serveur` ;
- puis le résultat renvoyé par son jeu : `[Camos] /camos dessins chez <pseudo> : 79 programmes prêts, 0 en échec, … ms`
  (le propriétaire peut donc lire le résultat sans demander le journal du jeu).

Le contenu des dessins n'est jamais écrit dans les journaux : seulement des nombres, des tailles et une empreinte.

## Étape 2 : la liste exacte (pas encore faite)

1. **Retirer les dessins du jar des joueurs.** Déplacer les sources hors des assets du jeu, toujours en un seul
   exemplaire dans le dépôt (par exemple `modules/armes/dessins/<espace>/<chemin>`) :
   `assets/heralisz_camo/shaders/core/camo.vsh`, `camo_<n>.fsh` et `camo_<n>.json` (73), `fx.vsh`, `fx_<n>.fsh` et
   `fx_<n>.json` (6), et `assets/minecraft/shaders/include/heralisz_camo.glsl`. Dans `modules/armes/build.gradle`, la
   copie de `processServeurResources` part de ce dossier : le jar -client n'a plus aucun de ces fichiers ; -serveur et
   -complet gardent `heralisz_camo_dessins/`. Le sticker (`sticker.vsh`, `.fsh`, `.json`) reste dans le jar des joueurs.
2. **Fabrication automatique pendant l'écran de connexion, une fois par session.** Quand la copie est complète
   (`DessinsRecus.morceau`), demander au fil du rendu (`RenderSystem.recordRenderCall`) le travail de
   `CamoShaders.refaireDepuisServeur()`, sans bandeau. Ne rien refaire si les programmes viennent déjà d'une copie de
   même empreinte (reconnexion au même serveur) ; au F3+T, le mécanisme de l'étape 1 garde déjà les programmes si rien
   n'a changé. Après la fabrication, vider les icônes 3D (`IconesArmes.vider()`, déjà fait par l'essai) : une icône
   photographiée avant serait restée sans skin.
3. **Démarrage du jeu sans copie.** `CamoShaders.onRegisterShaders` ne cherche plus les programmes dans le jar (ils n'y
   sont plus) : sans copie, aucun programme et aucune erreur au journal (aujourd'hui, une erreur par programme absent).
4. **Skin « sans effet » si rien n'est reçu.** C'est déjà le comportement du code quand un programme manque
   (`CamoShaders.get(id) == null` : `BedrockGunModelMixin`, `BagCamo`, `MeleeCamo`, `IconesArmes`, `PrechauffageCamo`,
   `CamoFx` dessinent l'arme sans le skin). À vérifier en jeu : menu principal, monde solo (jar -complet : le serveur
   intégré envoie la copie), serveur sans la copie.
5. **Outils à mettre à jour** (chemins des sources) :
   - `modules/armes/outils/generer_camos.py` (`CORE`, `INCLUS`) et `modules/armes/outils/skins_couteaux.py` (`CORE`),
     qui écrivent les fichiers des programmes ;
   - `modules/armes/outils/apercu_camos` : `verif_glsl.py` (`CORE`, `INCLUS`), `rendre.js` (chemin de
     `heralisz_camo.glsl`), `README.md` (exemples de passes) ;
   - `outils/video_vitrine/video.py` (`SHADERS`, `INCLUS`, et le chemin remplacé dans `rendre.js`) ;
   - `outils/verifier.py` : nouveau contrôle « aucun dessin de skin dans le jar des joueurs » (le jar -client ne contient
     ni `camo_*`, ni `fx_*`, ni `heralisz_camo.glsl` ; le jar -serveur contient `heralisz_camo_dessins/` avec un
     programme par fichier `camo_<n>.json` et `fx_<n>.json` du dossier des sources) ;
   - `outils/textures_inutiles.py` lit aussi les `.fsh`, `.vsh` et `.glsl` : lui donner le nouveau dossier.
6. **Versions.** Monter la version d'Armes. Les paquets ne changent pas : la version du canal des camos (4) peut rester ;
   un jeu de l'étape 2 sur un serveur de l'étape 1 marche aussi (ce serveur envoie déjà la copie).
7. **Essai avant toute mise en ligne** : le jar -client de l'étape 2 sur un PC, avec un serveur d'essai local
   (`outils/client_essai.py`, docs/AUDIT_UI.md), puis l'essai du staff : premier lancement, connexion, déconnexion et
   reconnexion, F3+T, monde solo.

## Limites

- Cela protège contre la **copie de nos jars** : quelqu'un qui récupère le jar des joueurs (fuite, dossier partagé) n'a
  plus les dessins.
- Cela ne protège pas contre quelqu'un de **très doué** : tout joueur connecté reçoit la copie ; un jeu modifié peut
  l'enregistrer, et un outil de débogage graphique peut relire les programmes dans la carte graphique. C'est vrai de tout
  jeu en ligne : ce qui s'affiche chez le joueur passe par sa machine.
- Seuls les dessins (le code des effets) sont protégés : les images des skins (icônes, textures) restent dans le jar.
- Les finitions des couteaux utilisent les mêmes programmes (`camo_<n>`, `KnifeFinish.programme`) : elles sont
  protégées en même temps.

## Coût

- **Étape 1** : 87 Ko envoyés à chaque connexion (un paquet), environ 0,1 Mo de mémoire chez le joueur. Au démarrage du
  serveur, lecture et compression une seule fois (33 ms mesurées hors jeu). Rien d'autre.
- **Étape 2** : en plus, la fabrication des 79 programmes à chaque connexion, pendant l'écran de connexion : le temps
  affiché par le bandeau de l'essai (quelques secondes au plus selon le PC). Ensuite aucun coût en jeu : ce sont
  exactement les mêmes programmes qu'aujourd'hui. Environ 0,7 Mo de mémoire (copie ouverte).

## Pour les développeurs : l'étape 1 en détail

- **Fabrication** (`modules/armes/build.gradle`, `processServeurResources`) : les sources restent à un seul endroit,
  `modules/armes/src/client/resources/assets/` ; la fabrication en met une copie dans les jars -serveur et -complet sous
  `heralisz_camo_dessins/<espace>/<chemin>` (hors des assets : jamais lue comme une ressource du jeu). Fichiers pris :
  `heralisz_camo/shaders/core/camo.vsh`, `camo_*.fsh`, `camo_*.json`, `fx.vsh`, `fx_*.fsh`, `fx_*.json` et
  `minecraft/shaders/include/heralisz_camo.glsl` (ce qu'ils importent d'autre, `fog.glsl` et `light.glsl`, est à
  Minecraft). Pas le sticker.
- **Format** (`fr.heralisz.camo.DessinsCopie`, commun) : fichiers « espace:chemin » → octets dans l'ordre du tri,
  compressés en Deflate, découpés en morceaux de 900 000 octets au plus (limite d'un paquet vers un joueur : 1 Mo), 32
  morceaux au plus ; empreinte SHA-256 des noms, tailles et octets ; lecture bornée (4 096 fichiers, 32 Mo).
- **Serveur** (`fr.heralisz.camo.serveur.DessinsServeur`) : au premier `ServerStartingEvent`, lecture du dossier dans le
  jar (`ModList…findResource`) et compression, une seule fois ; à chaque `PlayerLoggedInEvent`, envoi des mêmes morceaux
  (rien n'est refait) ; `/camos dessins` (`CommandeCamos`, op 2) envoie la demande au jeu de celui qui la tape, puis note
  le résultat qu'il renvoie.
- **Paquets** (canal `heralisz_camo:main`, version 4) : 4 = `DessinsPacket` (un morceau : numéro, nombre, taille totale,
  nombre de fichiers, taille décompressée, empreinte, octets ; traité sur le fil du réseau sans toucher au jeu) ;
  5 = `DessinsRefairePacket` (la demande ; traitée sur le fil du jeu, qui est celui du rendu) ; 6 = `DessinsBilanPacket`
  (joueur -> serveur : le résultat, noté au journal du serveur seulement si le serveur a envoyé la demande à ce joueur).
- **Jeu** (`fr.heralisz.camo.client.DessinsRecus`) : les morceaux sont mis bout à bout, la copie compressée est gardée
  en mémoire ; rien n'est ouvert tant que le staff ne le demande pas. Ouverture (une fois par copie) : décompression et
  vérification de l'empreinte annoncée. `fournisseur()` est le `ResourceProvider` en mémoire : nos fichiers viennent
  seulement de la copie (comme à l'étape 2), le reste (includes de Minecraft) du jeu.
- **Programmes** (`CamoShaders`) : `refaireDepuisServeur()` ferme d'abord les morceaux de programme que Minecraft garde en
  réserve sous leur nom (`Program.Type…getPrograms()`, noms `heralisz_camo:camo…` et `heralisz_camo:fx…`), sinon un
  nouveau programme reprendrait ceux compilés depuis le jar ; puis il ferme les anciens programmes, fabrique les 79
  nouveaux (`construire`), vide les icônes 3D et montre le bandeau. Ensuite, `onRegisterShaders` (F3+T) refait les
  programmes depuis la copie seulement, et seulement si l'empreinte « copie + includes de Minecraft » a changé.
