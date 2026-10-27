# HeraliZ Launcher

Launcher de survie Minecraft DayZ pour **Minecraft 1.20.1 / Forge / Java 17**.

Interface française aux couleurs HeraliZ (maquettes validées) : fond « carte », accueil « VERSION 1 · SAISON 1 », onglets Accueil / Règlement / Boutique (bientôt), trois nouveautés non cliquables sur l’accueil, Discord bien en vue, préparation du jeu avec ses étapes, réglages de mémoire, vérification des fichiers. Compte HeraliZ obligatoire, le même que sur heraliz.fr : connexion par e-mail ou pseudo et mot de passe, ou création du compte dans le launcher, puis proposition de la double authentification (QR code). Le launcher ne garde que le jeton du site, jamais le mot de passe ; en jeu, AuthMe demande en plus son propre mot de passe (`/register` la première fois, puis `/login`). Aucune connexion Microsoft dans l’application.

**Aucun accès au dossier du jeu** depuis le launcher (ni bouton, ni lien, ni nom de fichier affiché pendant le téléchargement) : choix du propriétaire, pour ne pas faciliter le vol des jars.

## Démarrer

Avec Node.js 24 et npm :

```sh
npm ci
npm start
```

Le launcher s’ouvre même sans serveur configuré. Il affiche alors « Serveur en préparation » et refuse le lancement, sans télécharger la distribution de démonstration de Helios.

## Configurer le serveur avant diffusion

Renseigner `app/heraliz.json` :

- `serverAddress` : `play.heraliz.fr`, l’adresse publique du serveur de jeu (domaine ou IPv4, avec le port si nécessaire).
- `distributionUrl` : URL HTTPS du manifeste Helios/Nebula complet : `https://heraliz.fr/launcher/distribution.json`, refait à chaque mise en ligne du serveur.
- `discordUrl` : invitation HTTPS Discord (`https://discord.gg/heraliz`), ou `null`.
- `serverId` : `heraliz-1.20.1`, identique à celui du manifeste.

L’adresse est `play.heraliz.fr` ; le pack (Forge 1.20.1-47.4.0, mods joueurs, gunpacks) est servi par le site. Ne pas diffuser cette version comme un accès opérationnel avant un test de la connexion réelle.

Voir [la configuration du pack](docs/HERALIZ-DEPLOIEMENT.md) et [les protections et limites de sécurité](docs/HERALIZ-SECURITE.md).

## Vérifier et construire

```sh
npm test
npm run lint
npm run dist:win     # sur Windows : dist/HeraliZ-Launcher-Setup.exe
npm run dist:mac     # sur un Mac : dist/HeraliZ-Launcher.dmg et HeraliZ-Launcher-mac.zip (application universelle)
npm run dist:linux   # sur Linux : dist/HeraliZ-Launcher.AppImage et HeraliZ-Launcher.deb
```

Sur GitHub, le workflow « Launcher HeraliZ » (`.github/workflows/launcher.yml`, à la racine du dépôt) teste et fabrique le launcher Windows et Linux à chaque changement de `launcher/` sur `main`, et aussi le launcher Mac sur « Run workflow ». Avec la case « publier », chaque fichier est mis en ligne à un lien fixe, avec son fichier de version (`ci/publier.py`) ; voir `CLAUDE.md`, section 8 ter :

| Système | Lien | Mise à jour du launcher |
|---|---|---|
| Windows | https://heraliz.fr/telecharger/HeraliZ-Launcher-Setup.exe | automatique (`latest.yml`) |
| Mac (Intel et Apple Silicon) | https://heraliz.fr/telecharger/HeraliZ-Launcher.dmg | le launcher prévient et propose le nouveau fichier (`latest-mac.yml`) |
| Linux, toutes distributions | https://heraliz.fr/telecharger/HeraliZ-Launcher.AppImage | automatique (`latest-linux.yml`) |
| Linux, Ubuntu / Debian / Mint | https://heraliz.fr/telecharger/HeraliZ-Launcher.deb | le launcher prévient et propose le nouveau fichier |

Le pack de jeu est actualisé à chaque lancement, sur tous les systèmes. Java 17 est téléchargé pour le bon système (Apple Silicon compris, sans Rosetta).

Le jeu est lancé à part du launcher (« détaché ») : le joueur peut fermer le launcher pendant la partie, Minecraft reste ouvert. Un launcher rouvert pendant la partie refuse d'en lancer une seconde. La console du jeu (dont les erreurs de Java au démarrage) est écrite dans `logs/console-jeu.log` du dossier du jeu.

## Installer sur Mac ou Linux (à donner aux joueurs)

**Mac** : télécharger `HeraliZ-Launcher.dmg`, l'ouvrir et glisser HeraliZ Launcher dans Applications. L'application n'est pas signée par Apple (certificat payant) : au premier lancement, macOS la bloque une fois.

- macOS 15 (Sequoia) et plus récent : ouvrir le launcher, fermer le message avec « Terminé », puis Réglages Système → Confidentialité et sécurité → en bas, « Ouvrir quand même » à côté de HeraliZ Launcher, et confirmer (mot de passe ou Touch ID).
- macOS 14 et plus ancien : dans Applications, clic droit (ou Ctrl + clic) sur HeraliZ Launcher → Ouvrir → Ouvrir.
- Si macOS demande l'accès au trousseau « HeraliZ Launcher Safe Storage » (après une nouvelle version), choisir « Toujours autoriser » : c'est là qu'est gardée la connexion au compte.
- Nouvelle version : le launcher l'annonce avec un bouton « Télécharger » ; glisser la nouvelle application dans Applications pour remplacer l'ancienne (compte et jeu gardés).

**Linux** :

- Ubuntu, Debian, Mint, Pop!_OS : télécharger `HeraliZ-Launcher.deb` et l'installer (double-clic, ou `sudo apt install ./HeraliZ-Launcher.deb`). Nouvelle version : annoncée par le launcher, installer le nouveau `.deb` de la même façon.
- Autres distributions : télécharger `HeraliZ-Launcher.AppImage`, le rendre exécutable (Propriétés → Autoriser l'exécution, ou `chmod +x HeraliZ-Launcher.AppImage`) et l'ouvrir. Il se met à jour seul. S'il ne s'ouvre pas, installer FUSE 2 (paquet `libfuse2` ou `fuse2`).
- La connexion au compte est gardée dans le trousseau du bureau (GNOME Keyring, KWallet). Sans trousseau, le launcher ne la garde pas : il faut se reconnecter à chaque ouverture.

## Architecture

- `index.js` : fenêtre Electron isolée, permissions et commandes IPC limitées.
- `app/preload.js` : API étroite exposée à l’interface.
- `app/index.html`, `app/assets/css/heraliz.css`, `app/assets/js/renderer.js` : interface locale, sans contenu web distant.
- `launcher-service.js` : profil, état serveur, Java, téléchargements et cycle de vie de Minecraft.
- `processbuilder.js` : arguments Minecraft/Forge, conservés depuis Helios et adaptés aux profils locaux ; lancement détaché du jeu.
- `mise-a-jour.js` : mise à jour du launcher lui-même (automatique ou annoncée selon le système).
- `ci/publier.py` : mise en ligne sur heraliz.fr/telecharger (workflow « Launcher HeraliZ »).
- `topo.js` : fond « carte » dessiné une fois par taille de fenêtre (rien d’animé).
- `nouveautes.js` + `app/assets/images/nouveautes/` (images 960 × 540) : les trois nouveautés de l’accueil, la plus récente en premier.

Les anciens templates EJS et scripts Helios restent comme référence dans les sources. Ils ne sont ni chargés ni inclus dans l’application construite. Le manifeste de construction (`files` de `electron-builder.yml`) énumère les fichiers actifs : **un nouveau module, une image ou une police s’y ajoute**, sinon le launcher installé ne le trouve pas (1.14.0 à 1.15.0 : `gardien.js` et `integrite.js` oubliés, launcher planté au démarrage, à retélécharger une fois). `test/emballage.test.js` le vérifie à chaque `npm test`.

L’onglet « Anticheat » et le détail de la fenêtre d’accord (`#anticheat-modele` de `index.html`) reprennent la page Confidentialité du site (`site/vm/pages_legales.php`, point 3) : changer les deux ensemble.

## Identité et doubles comptes

Un seul compte est mémorisé : celui du site HeraliZ (Azuriom), vérifié au démarrage et avant chaque partie (`compte-site.js`). Le pseudo en jeu est celui du compte : 3 à 16 caractères ASCII (lettres, chiffres, `_`). L’UUID correspond au calcul Minecraft hors ligne et respecte la casse.

**Le launcher ne protège que lui-même.** Un autre launcher peut encore rejoindre le serveur avec n’importe quel pseudo tant que Spartan n’exige pas le jeton du launcher (`tokenRequired`) : AuthMe reste la protection en jeu. Le message de bannissement exprime le règlement ; le launcher ne détecte pas et ne bannit pas automatiquement les doubles comptes. L’authentification, les permissions et les sanctions doivent être appliquées côté serveur. Ne jamais accorder des droits administrateur sur la seule base d’un pseudo libre.

## Crédits

Basé sur [Helios Launcher](https://github.com/dscalzi/HeliosLauncher) de Daniel Scalzi, avec `helios-core`. La licence originale est conservée dans `LICENSE.txt`. Les logos ont été fournis par le propriétaire du projet.
