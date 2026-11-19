# Sécurité : protections et limites

## Empêcher la récupération des mods

On ne peut pas garantir la confidentialité d’un mod exécuté sur le PC du joueur. Il faut fournir le code à sa JVM ; il peut récupérer le JAR ou le code en mémoire. Dossier caché, archive chiffrée avec clé dans le launcher ou suppression après lancement ne résolvent pas ce problème.

La protection utile consiste à séparer les composants :

- Garder côté serveur les règles de loot, l’économie, les validations de combat, données sensibles et permissions lorsque la conception le permet.
- Envoyer au client seulement ce qui est nécessaire au rendu, aux interfaces et au protocole.
- Conserver sources et modules serveur dans des dépôts privés. **Attention (vérifié le 19/11) : ce dépôt GitHub est PUBLIC**
  (`private=false`), alors qu'il contient tout Spartan (détections serveur et leurs seuils, contrôles du jeu, Gardien,
  listes de `serveur/config/spartan-common.toml`), le code du site et les réglages du serveur. N'importe qui peut le lire, et
  tout compte GitHub peut télécharger les jars fabriqués gardés dans l'onglet Actions (paquets `mods-serveur` et
  `test-mods-serveur` : le jar serveur de Spartan, toutes ses détections compilées). Les secrets sont dans GitHub et sur la
  VM du site (recherche du 19/11 des formes courantes de clés et d'adresses de webhooks dans les fichiers du dépôt : rien
  trouvé ; l'historique n'a pas été fouillé en entier), mais un tricheur peut y étudier ce que Spartan cherche. Choix à
  faire par les propriétaires : voir « Dépôt public » plus bas.
- L’obfuscation augmente le coût de lecture sans empêcher la copie ; vérifier sa compatibilité avec Forge et les mixins.
  Tant que les sources sont publiques, obfusquer le jar ne sert à rien.
- Des liens temporaires ou téléchargements autorisés limitent la distribution initiale, pas la redistribution par un joueur autorisé.

La séparation dépend de chaque mod : des blocs ou objets supplémentaires requièrent souvent une partie cliente. Référence : [client et serveur dans Forge](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/).

## Pseudos et doubles comptes

Le launcher exige un compte du site HeraliZ (connexion ou création, double authentification proposée), garde seulement le jeton du site (chiffré par le système, jamais le mot de passe), prend le pseudo du compte, calcule l’UUID hors ligne Minecraft et demande l’acceptation du règlement. Le bannissement immédiat affiché est une règle, pas une détection automatique.

Le joueur peut modifier ses fichiers ou utiliser un autre launcher. L’UUID est calculable et ne prouve pas l’identité ; la casse du pseudo le modifie. Le serveur doit réserver les identités et traiter leurs variantes de manière cohérente.

La détection et les sanctions nécessitent des contrôles serveur. Une IP ou empreinte matérielle seule peut confondre des personnes ou être contournée ; aucune collecte de ce type n’a été ajoutée. Les comptes staff nécessitent une authentification fiable.

## Protections ajoutées

- Sandbox, isolation de contexte et Node.js désactivé dans l’interface.
- Passerelle IPC limitée, vérification de la fenêtre et de sa frame principale.
- Refus de la navigation, nouvelles fenêtres, webviews et permissions web.
- Politique CSP locale stricte et aucune injection de contenu distant.
- Liens externes HTTPS choisis dans une liste limitée ; aucune commande système libre exposée.
- Validation principale des pseudos et réglages, profil unique et verrou de lancement.
- Suppression des anciens credentials Microsoft/Mojang s’ils figurent dans la configuration chargée.
- Distribution liée à HeraliZ ; refus des chemins dangereux, types inconnus, HTTP et empreintes absentes.
- Contrôle de chaque téléchargement, même de taille correcte, avant traitement. Java est vérifié avant extraction.
- Mise à jour d’Electron et des dépendances ; compilation sans publication automatique.

Référence : [recommandations de sécurité Electron](https://www.electronjs.org/docs/latest/tutorial/security).

## Anticheat Spartan

Le launcher lance le jeu sans porte d'entrée pour les injecteurs (`app/assets/js/spartan.js`, testé par
`test/spartan.test.js`) :

- `-XX:+DisableAttachMechanism` à chaque lancement, après les options du joueur : aucun programme ne peut s'attacher au
  jeu lancé (injecteurs « à chaud ») ;
- les options Java du joueur qui servent à injecter du code sont retirées : `-javaagent`, `-agentlib`, `-agentpath`,
  `-Xbootclasspath`, chargeur de classes remplacé, attache autorisée, vérification du code désactivée.

Dans le jeu, Spartan (mods/spartan du dépôt) compare chaque jar chargé au modpack officiel fabriqué avec le serveur,
y compris les jars du dossier du launcher (`common/modstore`, options `--fml.mavenRoots` / `--fml.modLists`). Le pack
du launcher doit donc contenir exactement les jars de `livraison/mods client`, dont `spartan-<version>-client.jar` de
la MÊME fabrication que le jar serveur. Quand tous les joueurs passent par le launcher, le réglage serveur
`requireHardenedJvm = true` signale ceux qui lancent le jeu sans ces protections (alerte seulement tant que
`silentClientDetections = true` ; depuis le mode strict du 27/10, un jeu sans `DisableAttachMechanism` est de toute façon
refusé par `strictOfficial`). Le jeton de lancement est signé par
le site pour le compte connecté (mode B, depuis la 1.6.0) et passé au jeu par la variable d'environnement
`SPARTAN_LAUNCH_TOKEN`, jamais sur la ligne de commande (depuis la 1.6.1) ; le serveur refuse qui n'en a pas
(`tokenRequired = true`). Le launcher retire aussi de l'environnement du jeu `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS` et
`JDK_JAVA_OPTIONS`, que Java lit au démarrage (un `-javaagent` de triche pouvait s'y glisser), et, sur Linux et Mac,
`LD_PRELOAD`, `LD_AUDIT` et `DYLD_INSERT_LIBRARIES` (bibliothèque native imposée au jeu) ; ses verrous Electron
(`electronFuses`) l'empêchent de tourner comme un simple Node.js ou de charger un autre code que son archive (sur Mac,
l'application est re-signée « ad hoc » après la pose des verrous). Le jeu est lancé détaché (il survit à la fermeture
du launcher), avec le même environnement nettoyé.

Ces protections sont les mêmes sous Windows, Mac et Linux (AppImage et .deb) : les options du jeu sont construites par
le même code (`processbuilder.js`, `_constructJVMArguments113`) quelle que soit la plateforme.

**Launcher 1.17.0 (19/11)**, avec Spartan 2.13.0 :

- options Java du joueur retirées en plus : `-XX:+ManagementServer` (contrôle à distance de Java, qui sait charger un
  agent sans passer par l'attache) et `-XX:+AllowRedefinitionToAddDeleteMethods` (ne sert qu'aux agents) ; variables
  retirées en plus : `CLASSPATH` et, sur Mac, les dossiers où chercher les bibliothèques avant ceux du système
  (`DYLD_LIBRARY_PATH`, `DYLD_FRAMEWORK_PATH`… : une fausse bibliothèque graphique y suffirait pour un ESP).
  `LD_LIBRARY_PATH` reste (Linux : souvent nécessaire au pilote graphique) ;
- le Gardien lit, **depuis l'extérieur du jeu**, les bibliothèques chargées dans le processus du jeu (Windows :
  PowerShell sur ce seul processus, `tasklist /M` en secours ; Linux : `/proc/<pid>/maps` ; Mac : pas de lecture), un
  rapport sur trois. Seules celles d'une liste fixe partent, dans la ligne signée : `instrument` (agent Java), `jdwp`,
  `dt_socket`, `dt_shmem` (débogueur), `attach`, `management_agent`, `sawindbg`, `saproc` (outils de Java). Un jeu lancé
  par le launcher ne les charge jamais (aucun agent, attache coupée) : chargées quand même, un injecteur les a mises en
  cours de partie. Un Spartan modifié ne peut pas les cacher au launcher. Jamais la liste des bibliothèques du joueur ;
- juste après le lancement, chaque jar du pack est comparé en fond à son empreinte **signée** : un jar remplacé entre le
  contrôle d'avant la partie et le lancement (puis remis en place) compte comme « jar changé ».

Côté serveur (Spartan 2.13.0) : la même clé du Gardien vue pour deux comptes, ou pour deux jeux différents (deux numéros
de processus), est refusée (« launcher imité » : le vrai launcher tire une clé neuve à chaque lancement) ; dans le jeu,
une classe du jeu ou de l'anticheat **redéfinie en mémoire** après son chargement (ce que fait un agent injecté) et les
mêmes bibliothèques d'agent ou de débogueur sont des indices forts. Ces nouvelles règles sont « en rodage » : si trois
joueurs différents les déclenchent en moins d'une heure, elles se suspendent toutes seules (alerte seulement) et le staff
est prévenu, pour qu'une erreur n'expulse jamais tout le monde (`[injection] rodage`).

## Limites restantes

**Tout ce qui tourne sur le PC du joueur peut mentir à un tricheur déterminé.** Un programme qui imite le launcher avec le
compte du joueur obtient un jeton du site et signe ses propres rapports du Gardien (avec une clé neuve à chaque fois, il
n'est pas repéré par la règle de la clé réutilisée) ; un Spartan modifié avec soin (code injecté par une DLL qui cache ses
traces) peut répondre juste au défi et envoyer des contrôles « propres ». Le launcher et Spartan arrêtent les outils tout
faits et obligent à un vrai travail ; le filet final reste les détections du serveur (mouvement, combat, tirs, anti-ESP).
Le dépôt public rend ce travail plus facile : voir ci-dessous.

### Dépôt public (constat du 19/11, décision des propriétaires)

Le dépôt GitHub est public (aucune bifurcation ni étoile au 19/11 : peu vu, mais tout ce qui y a été publié peut avoir
été copié). Options, de la plus sûre à la moins chère :

1. **Rendre le dépôt privé** (Settings → General → Danger Zone → Change visibility). Protège tout d'un coup (Spartan, site,
   réglages, jars de l'onglet Actions). Coût : les minutes d'Actions deviennent payantes au-delà du forfait. Relevé sur les
   30 jours avant le 19/11 : environ 975 exécutions et 5 000 minutes de machine (surtout « Serveur HeraliZ » ~2 500 et
   « Contrôle de branche » ~1 500 ; le launcher Mac compte 10 fois ses ~95 minutes, Windows 2 fois ses ~145), soit environ
   6 000 minutes comptées, pour 2 000 incluses (gratuit) ou 3 000 (GitHub Pro, environ 4 $ par mois) : au-delà, quelques
   millièmes de dollar la minute Linux (prix à vérifier sur la page de facturation de GitHub), soit de l'ordre de 20 à
   40 $ par mois sans rien changer. Le stockage des paquets de l'onglet Actions devient aussi limité (0,5 à 2 Go selon le
   forfait ; aujourd'hui ~8 Go de paquets gardés) : garder moins de jours ou moins de paquets. Les jetons et les secrets ne
   changent pas.
2. **Garder le dépôt public mais sortir l'anticheat** : `modules/anticheat` dans un second dépôt privé, récupéré par les
   workflows avec un jeton (secret GitHub) ; et ne plus garder le jar serveur de Spartan dans l'onglet Actions (ou le
   garder chiffré par un mot de passe secret). Une demi-journée à une journée de travail, risque de casser la fabrication
   si c'est mal branché ; les autres mods, le site et les réglages restent publics.
3. **Garder seulement les listes hors du dépôt** : les signatures cherchées (`classProbes`, `processPatterns`,
   `nativePatterns`, outils, touches) mises sur le Ptero au moment de l'envoi depuis un secret GitHub, comme les webhooks
   Discord. Une heure de travail, mais le code des détections reste lisible : protège peu.
4. Obfusquer le jar joueur : inutile tant que les sources sont publiques.

Recommandation : l'option 1 (la plus simple et la seule qui protège tout), en réduisant si besoin les fabrications Mac et
la durée de garde des paquets.

Pas de certificat de signature Windows (payant) : SmartScreen avertit au premier lancement de l'installateur. **Signature cryptographique des mises à jour du launcher et du pack de jeu : en place** (Ed25519, clé privée gardée hors de la VM dans un secret GitHub, clé publique dans le launcher qui refuse toute mise à jour ou tout pack non signé ; pack signé avec les empreintes SHA-256 de chaque fichier et la date de la signature depuis la 1.16.0 : `launcher/docs/HERALIZ-SIGNATURE.md`). HTTPS et les empreintes seuls n’empêchent pas un hébergeur compromis de publier ensemble un manifeste et des fichiers malveillants : d'où la signature.

Depuis la 1.6.0, le launcher installé se met à jour seul depuis https://heraliz.fr/telecharger/ (latest.yml : version et empreinte SHA-512 contrôlée avant installation, `mise-a-jour.js`) ; depuis la 1.8.0, l'AppImage Linux aussi (latest-linux.yml). Sur Mac (application signée « ad hoc », sans certificat Apple ni notarisation) et pour le paquet .deb, le launcher annonce seulement la nouvelle version (latest-mac.yml, latest-linux.yml) et le joueur la télécharge lui-même. Le pack est actualisé à chaque partie. Qui peut écrire sur la VM du site peut donc livrer un launcher ou un pack : protéger ses accès (secrets `SITE_*`) et les comptes GitHub qui lancent la publication. Avant diffusion, tester le serveur réel et, si possible, signer l’installeur.
