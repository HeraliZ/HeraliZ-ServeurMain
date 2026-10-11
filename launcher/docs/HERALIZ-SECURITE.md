# Sécurité : protections et limites

## Empêcher la récupération des mods

On ne peut pas garantir la confidentialité d’un mod exécuté sur le PC du joueur. Il faut fournir le code à sa JVM ; il peut récupérer le JAR ou le code en mémoire. Dossier caché, archive chiffrée avec clé dans le launcher ou suppression après lancement ne résolvent pas ce problème.

La protection utile consiste à séparer les composants :

- Garder côté serveur les règles de loot, l’économie, les validations de combat, données sensibles et permissions lorsque la conception le permet.
- Envoyer au client seulement ce qui est nécessaire au rendu, aux interfaces et au protocole.
- Conserver sources et modules serveur dans des dépôts privés. Ce dépôt est privé (il contient les détections de Spartan) : le garder privé.
- L’obfuscation augmente le coût de lecture sans empêcher la copie ; vérifier sa compatibilité avec Forge et les mixins.
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
`requireHardenedJvm = true` signale ceux qui lancent le jeu sans ces protections. Le jeton de lancement est signé par
le site pour le compte connecté (mode B, depuis la 1.6.0) et passé au jeu par la variable d'environnement
`SPARTAN_LAUNCH_TOKEN`, jamais sur la ligne de commande (depuis la 1.6.1) ; le serveur refuse qui n'en a pas
(`tokenRequired = true`). Le launcher retire aussi de l'environnement du jeu `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS` et
`JDK_JAVA_OPTIONS`, que Java lit au démarrage (un `-javaagent` de triche pouvait s'y glisser), et ses verrous Electron
(`electronFuses`) l'empêchent de tourner comme un simple Node.js ou de charger un autre code que son archive.

## Limites restantes

Pas encore de certificat Windows ni de signature cryptographique des mises à jour et du manifeste (prévu : clé gardée hors de la VM du site). HTTPS et les empreintes n’empêchent pas un hébergeur compromis de publier ensemble un manifeste et des fichiers malveillants.

Depuis la 1.6.0, le launcher installé se met à jour seul depuis https://heraliz.fr/telecharger/ (latest.yml : version et empreinte SHA-512 contrôlée avant installation, `mise-a-jour.js`) ; le pack est actualisé à chaque partie. Qui peut écrire sur la VM du site peut donc livrer un launcher ou un pack : protéger ses accès (secrets `SITE_*`) et les comptes GitHub qui lancent la publication. Avant diffusion, tester le serveur réel et, si possible, signer l’installeur.
