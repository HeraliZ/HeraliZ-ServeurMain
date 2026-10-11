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
npm run dist:win
```

L’installeur Windows est créé dans `dist/`. Sur GitHub, le workflow « Launcher HeraliZ » (`.github/workflows/launcher.yml`, à la racine du dépôt) teste et construit l’installateur Windows à chaque changement de `launcher/` sur `main`, et le met en ligne à https://heraliz.fr/telecharger/HeraliZ-Launcher-Setup.exe sur demande (case « publier ») ; voir `CLAUDE.md`, section 8 ter. Le launcher ne se met pas à jour seul ; le pack est actualisé au lancement du jeu.

Le profil et le jeu sont enregistrés dans le dossier de données de l’application HeraliZ. L’ancien dossier Helios et `.minecraft` ne sont pas réutilisés automatiquement.

## Architecture

- `index.js` : fenêtre Electron isolée, permissions et commandes IPC limitées.
- `app/preload.js` : API étroite exposée à l’interface.
- `app/index.html`, `app/assets/css/heraliz.css`, `app/assets/js/renderer.js` : interface locale, sans contenu web distant.
- `launcher-service.js` : profil, état serveur, Java, téléchargements et cycle de vie de Minecraft.
- `processbuilder.js` : arguments Minecraft/Forge, conservés depuis Helios et adaptés aux profils locaux.
- `topo.js` : fond « carte » dessiné une fois par taille de fenêtre (rien d’animé).
- `nouveautes.js` + `app/assets/images/nouveautes/` (images 960 × 540) : les trois nouveautés de l’accueil, la plus récente en premier.

Les anciens templates EJS et scripts Helios restent comme référence dans les sources. Ils ne sont ni chargés ni inclus dans l’application construite. Le manifeste de construction énumère les fichiers actifs.

## Identité et doubles comptes

Un seul compte est mémorisé : celui du site HeraliZ (Azuriom), vérifié au démarrage et avant chaque partie (`compte-site.js`). Le pseudo en jeu est celui du compte : 3 à 16 caractères ASCII (lettres, chiffres, `_`). L’UUID correspond au calcul Minecraft hors ligne et respecte la casse.

**Le launcher ne protège que lui-même.** Un autre launcher peut encore rejoindre le serveur avec n’importe quel pseudo tant que Spartan n’exige pas le jeton du launcher (`tokenRequired`) : AuthMe reste la protection en jeu. Le message de bannissement exprime le règlement ; le launcher ne détecte pas et ne bannit pas automatiquement les doubles comptes. L’authentification, les permissions et les sanctions doivent être appliquées côté serveur. Ne jamais accorder des droits administrateur sur la seule base d’un pseudo libre.

## Crédits

Basé sur [Helios Launcher](https://github.com/dscalzi/HeliosLauncher) de Daniel Scalzi, avec `helios-core`. La licence originale est conservée dans `LICENSE.txt`. Les logos ont été fournis par le propriétaire du projet.
