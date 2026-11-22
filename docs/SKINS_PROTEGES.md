# Skins protégés : les dessins des skins d'armes donnés par le serveur

Mis à jour le 21/11 au soir. **Étapes 1 et 2 faites.** Étape 1 : Armes 2.59.12 (en ligne depuis le 21/11 à 17 h 43).
Étape 2 : **Armes 2.59.14** (branche `carte-ile-reglages`, pas encore en ligne ; à mettre en ligne quand le propriétaire
le décide, serveur et jeux des joueurs de la même fabrication, comme toujours).

## En bref (pour le propriétaire)

**Le problème.** Les « dessins » des skins d'armes sont de petits programmes pour la carte graphique : le motif et
l'animation des camos, les effets 3D (Camouflage Tempête, Skins X), les finitions des couteaux (Doppler, Fade…). Avant,
ils étaient dans le jar que chaque joueur télécharge : quelqu'un qui récupérait nos jars pouvait les reprendre tels quels.
Les skins de couteau, eux, étaient déjà protégés d'une autre façon : leurs fiches TaCZ ne sont que sur le serveur.

**Ce qui est fait.**

1. **Étape 1** : le serveur a sa propre copie des dessins. À chaque connexion, il l'envoie au jeu du joueur : 161 dessins
   (167 fichiers avec les mentions de licence que la livraison ajoute dans chaque dossier de nos jars, ignorées par le
   jeu), 87 Ko une fois compressés, en un seul paquet. Le jeu la garde **en mémoire seulement**, jamais sur le disque.
2. **Étape 2** : **le jar des joueurs n'a plus aucun dessin** (seulement le sticker, qui n'est pas un skin). Le jeu
   fabrique ses programmes depuis la copie reçue, tout seul, juste après la connexion, sur le fil de l'image, par petites
   tranches (25 ms au plus par image) : l'écran de chargement reste vivant, jamais un long gel d'un coup.

**Ce qu'un joueur voit.**
- À la première connexion d'une session : rien de spécial. Les skins apparaissent quand leurs programmes sont prêts,
  en général pendant l'écran « Chargement du terrain » (quelques secondes au plus selon le PC). Si le joueur est déjà
  dans le monde avant la fin, il voit quelques skins « sans effet » une ou deux secondes, puis tout est comme avant.
- Ensuite : **exactement le même rendu qu'avant** (mêmes programmes, mêmes réglages), en main, en 3e personne, dans
  l'atelier, sur les icônes de l'inventaire (refaites toutes seules à la fin) et sur les sacs.
- Reconnexion au même serveur : rien n'est refait (même copie). F3+T : rien n'est refait si rien n'a changé.
- Menu principal, avant toute connexion : les skins n'ont pas d'effet (on n'y voit pas d'armes skinnées de toute façon).
- Monde solo d'essai (jars -complet de `livraison/mods solo/`, jamais donnés aux joueurs) : le serveur intégré envoie
  lui aussi la copie, les skins marchent comme sur le serveur. Avec les jars des joueurs (-client), un monde solo n'a de
  toute façon pas la partie serveur de nos mods : skins sans effet.
- Si rien n'arrive ou si la copie est abîmée : le skin s'affiche **sans effet** (l'arme est dessinée sans son skin),
  jamais de plantage, une ligne dans le journal du jeu (voir plus bas).

**Limites.**
- Cela protège contre la **copie de nos jars** : quelqu'un qui récupère le jar des joueurs (fuite, dossier partagé) n'a
  plus les dessins.
- **Le dépôt GitHub est public** : les sources des dessins (`modules/armes/dessins/`, et leurs modèles
  `modules/armes/outils/camo_gabarit.fsh` et `fx_gabarit.fsh`) y sont lisibles par tout le monde, comme le reste du code.
  La protection ne vaut vraiment que si le dépôt passe en privé (réglage du dépôt sur GitHub, au choix du propriétaire).
- Cela ne protège pas contre quelqu'un de **très doué** : tout joueur connecté reçoit la copie ; un jeu modifié peut
  l'enregistrer, et un outil de débogage graphique peut relire les programmes dans la carte graphique. C'est vrai de tout
  jeu en ligne : ce qui s'affiche chez le joueur passe par sa machine.
- Seuls les dessins (le code des effets) sont protégés : les images des skins (icônes, textures) restent dans le jar.
- Les finitions des couteaux utilisent les mêmes programmes (`camo_<n>`, `KnifeFinish.programme`) : elles sont
  protégées en même temps.

## Essai à faire avant la mise en ligne de l'étape 2

Un membre du staff, sur son PC, avec le serveur d'essai ou le serveur de la même fabrication (Armes 2.59.14 des deux
côtés). Mettre dans la barre d'objets quelques armes skinnées : un camo animé (Camouflage Dark Matter), un camo à effet 3D
(Camouflage Tempête, ou un Skin X comme Séraphin ou Prisme), un couteau avec une finition (Doppler Rubis, Fade…), et si
possible un sac camouflé.

1. **Premier lancement, connexion** : les skins apparaissent pendant ou juste après l'écran de chargement, puis le rendu
   est **identique** à avant (couleurs, animation, reflets, effets 3D), en main, en 3e personne (sur soi et sur l'arme d'un
   autre joueur), dans l'atelier (onglet Camo), sur les icônes de l'inventaire et sur le sac.
2. **Déconnexion puis reconnexion** : rien n'est refait (journal : « même copie que celle déjà en place »).
3. **F3+T** : rien n'est refait, pas de gel en plus, les skins restent pareils.
4. **`/camos dessins`** (staff, op 2) : le jeu refait tous les programmes depuis la copie, comme à la connexion ; un
   bandeau à droite donne « Dessins des skins — 92 programmes prêts depuis la copie du serveur · … ms » (le temps de
   compilation sur ce PC). Le même résultat arrive dans le journal du serveur.
5. **Monde solo d'essai** (jars -complet) : mêmes skins qu'en ligne.
6. Envoyer le journal du jeu (`logs/latest.log`) si quelque chose cloche.

Ce qu'il ne faut pas voir :
- des skins qui restent sans effet plus de quelques secondes après l'arrivée : regarder le journal du jeu (lignes
  ci-dessous) et celui du serveur (copie absente ou illisible dans son jar ?) ;
- un bandeau rouge « Rien reçu du serveur » ou « Copie du serveur illisible » après `/camos dessins` ;
- un bandeau orange « … prêts, … en échec (voir le journal) » : un ou plusieurs programmes ne se fabriquent pas sur ce
  PC (pilote de carte graphique ?) ; ces skins restent sans effet. Envoyer `logs/latest.log`.

Lignes attendues dans le journal du jeu (`logs/latest.log`) :
- au démarrage du jeu : `[Camos] dessins des skins pas encore reçus : ils arrivent du serveur à la connexion (skins sans effet d'ici là)` ;
- à la connexion : `[Camos] dessins des skins reçus du serveur : 193 fichiers, 800 Ko (103 Ko compressés, 1 paquet(s)), empreinte … ; gardés en mémoire seulement` ;
- quelques instants après : `[Camos] dessins des skins fabriqués depuis la copie du serveur : 92 programmes prêts, 0 en échec, … ms de compilation (… ms en tout)` ;
- à une reconnexion au même serveur : `[Camos] dessins des skins : même copie que celle déjà en place, rien à refaire` ;
- au F3+T : `Shaders des camos inchangés : 92 programmes gardés, rien recompilé` ;
- si le serveur n'envoie rien : `[Camos] aucun dessin des skins reçu de ce serveur 30 s après l'arrivée : …` ;
- si la copie est abîmée : `[Camos] copie des dessins des skins illisible (…) : rien n'est refait (…)`.

Lignes attendues dans le journal du serveur :
- au démarrage : `[Camos] dessins des skins : 193 fichiers (800 Ko) compressés en 103 Ko, 1 paquet(s) par joueur, empreinte …, … ms` ;
- à chaque connexion : `[Camos] dessins des skins envoyés à <pseudo> : 103 Ko en 1 paquet(s)` ;
- à `/camos dessins` : `[Camos] /camos dessins : le jeu de <pseudo> refait ses programmes des skins depuis la copie du serveur`,
  puis le résultat renvoyé par son jeu : `[Camos] /camos dessins chez <pseudo> : 92 programmes prêts, 0 en échec, … ms`.

Le contenu des dessins n'est jamais écrit dans les journaux : seulement des nombres, des tailles et une empreinte.

Chiffres depuis la collection de Noël du 22/11 (13 programmes de plus : 8 camos, 5 couteaux ; avant : 79 programmes,
167 fichiers, 698 Ko, 87 Ko compressés), relus dans le journal du Ptero après la mise en ligne du 22/11 (démarrage de
15 h 33, heure UTC : 193 fichiers, 800 Ko, 103 Ko compressés, 57 ms).

Pourquoi 193 fichiers et pas 187 (167 et 161 avant le 22/11 ; validation du 21/11, journal du Ptero de 17 h 45) : la livraison
(`outils/construire.py`, `marquer`) pose la mention de licence `LISEZ-MOI-HeraliZ.txt` dans chaque dossier de nos jars,
donc aussi dans les 6 sous-dossiers de la copie. Le serveur les envoie avec les dessins (5 Ko, 1 Ko compressé) ; le jeu
les ignore (leur nom, avec des majuscules, n'est pas un nom de ressource valable). Rien d'anormal : 187 dessins + 6
mentions. Le contrôle « dessins » de `outils/verifier.py` regarde les jars fabriqués, avant cette mention.

## Coût

- 103 Ko envoyés à chaque connexion (un paquet ; 87 Ko avant la collection de Noël), environ 0,9 Mo de mémoire chez le joueur (copie et fichiers ouverts). Au
  démarrage du serveur, lecture et compression une seule fois (132 ms sur le Ptero le 21/11).
- La fabrication des 92 programmes une fois par session, juste après la connexion, étalée sur les images (25 ms de
  compilation au plus par image). C'est le travail que le jeu faisait avant au lancement, pendant le chargement des
  ressources : le lancement du jeu est donc un peu plus court, la connexion un peu plus longue. Ensuite aucun coût en
  jeu : ce sont exactement les mêmes programmes qu'avant.

## Pour les développeurs

- **Sources** : un seul exemplaire dans le dépôt, `modules/armes/dessins/<espace>/<chemin>`, hors des assets du jeu :
  `heralisz_camo/shaders/core/camo.vsh`, `camo_<n>.fsh` et `camo_<n>.json` (73), `fx.vsh`, `fx_<n>.fsh` et `fx_<n>.json`
  (6), et `minecraft/shaders/include/heralisz_camo.glsl` (ce qu'ils importent d'autre, `fog.glsl` et `light.glsl`, est à
  Minecraft). Écrites par `modules/armes/outils/generer_camos.py` (depuis `camo_gabarit.fsh` et `fx_gabarit.fsh`) et
  `modules/armes/outils/skins_couteaux.py`, jamais à la main. Le sticker (`sticker.vsh`, `.fsh`, `.json`) reste dans
  `src/client/resources/assets/heralisz_camo/shaders/core/` (jar des joueurs).
- **Fabrication** (`modules/armes/build.gradle`, `processServeurResources`) : copie de `dessins/` dans les jars -serveur
  et -complet sous `heralisz_camo_dessins/` (hors des assets : jamais lue comme une ressource du jeu) ; le jar -client
  n'a aucun dessin. Contrôle « dessins » de `outils/verifier.py` : copie des jars -serveur et -complet = le dossier des
  sources, et aucun `camo*`, `fx*` ni `heralisz_camo.glsl` dans les assets des jars -client et -complet (le sticker y
  reste).
- **Outils qui lisent les sources** (chemins mis à jour à l'étape 2) : `generer_camos.py`, `skins_couteaux.py`,
  `apercu_camos` (`verif_glsl.py`, `rendre.js`, `README.md`), `outils/video_vitrine/video.py`,
  `outils/textures_inutiles.py` (lit aussi `modules/<m>/dessins/`).
- **Format** (`fr.heralisz.camo.DessinsCopie`, commun) : fichiers « espace:chemin » → octets dans l'ordre du tri,
  compressés en Deflate, découpés en morceaux de 900 000 octets au plus (limite d'un paquet vers un joueur : 1 Mo), 32
  morceaux au plus ; empreinte SHA-256 des noms, tailles et octets ; lecture bornée (4 096 fichiers, 32 Mo).
- **Serveur** (`fr.heralisz.camo.serveur.DessinsServeur`) : au premier `ServerStartingEvent`, lecture du dossier dans le
  jar (`ModList…findResource`) et compression, une seule fois ; à chaque `PlayerLoggedInEvent`, envoi des mêmes morceaux
  (rien n'est refait) ; `/camos dessins` (`CommandeCamos`, op 2) envoie la demande au jeu de celui qui la tape, puis note
  le résultat qu'il renvoie.
- **Paquets** (canal `heralisz_camo:main`, version 4, inchangée à l'étape 2) : 4 = `DessinsPacket` (un morceau : numéro,
  nombre, taille totale, nombre de fichiers, taille décompressée, empreinte, octets ; traité sur le fil du réseau sans
  toucher au jeu) ; 5 = `DessinsRefairePacket` (la demande du staff ; fil du jeu, qui est celui du rendu) ;
  6 = `DessinsBilanPacket` (joueur -> serveur : le résultat, noté au journal du serveur seulement si le serveur a envoyé
  la demande à ce joueur).
- **Jeu** (`fr.heralisz.camo.client.DessinsRecus`) : les morceaux sont mis bout à bout ; la copie complète est gardée en
  mémoire pour la session et `CamoShaders.copieRecue()` est appelée (fil du réseau → `RenderSystem.recordRenderCall`).
  Ouverture (décompression, vérification de l'empreinte annoncée) une fois par copie, pas de nouveau à une reconnexion qui
  apporte la même empreinte. `fournisseur()` est le `ResourceProvider` en mémoire : nos fichiers viennent seulement de la
  copie, le reste (includes de Minecraft) du jeu. Rien reçu 30 s après l'arrivée sur un serveur
  (`ClientPlayerNetworkEvent.LoggingIn`) : une ligne au journal.
- **Programmes** (`CamoShaders`) : `fabriquerDepuisCopie()` (fil du rendu) ne fait rien si les programmes en place ou en
  cours viennent de la même copie (empreinte annoncée) ou de sources identiques ; sinon `demarrer()` ferme les morceaux
  de programme que Minecraft garde en réserve sous leur nom (`Program.Type…getPrograms()`, noms `heralisz_camo:camo…` et
  `heralisz_camo:fx…`) et les anciens programmes, puis la `Fabrique` compile les 92 programmes un par un au début de
  chaque image (`TickEvent.RenderTickEvent`, branché par `PartieClient`), 25 ms au plus par image, rien pendant un écran
  de chargement des ressources ; à la fin, les icônes 3D sont vidées (`IconesArmes.vider()`) et une ligne va au journal.
  `onRegisterShaders` (démarrage, F3+T) : sans copie, rien (une ligne) ; avec la copie, rien si l'empreinte « copie +
  includes de Minecraft » n'a pas changé, sinon tout est refait d'un coup pendant le rechargement. `get()` / `fx()` nuls :
  chaque rendu dessine l'arme sans skin (`BedrockGunModelMixin`, `BagCamo`, `MeleeCamo`, `IconesArmes`,
  `PrechauffageCamo`, `CamoFx`).
- **Compatibilité** (côté réseau) : un jeu de l'étape 2 sur un serveur de l'étape 1 marche (ce serveur envoie déjà la
  copie) ; un jeu de l'étape 1 sur un serveur de l'étape 2 aussi (il garde ses dessins du jar). En pratique, Spartan
  n'accepte que les jars de la même fabrication que le serveur.
