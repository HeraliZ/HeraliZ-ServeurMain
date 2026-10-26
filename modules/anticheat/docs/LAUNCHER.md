# Intégrer Spartan au launcher officiel

Le launcher est **la première muraille** : il bloque la triche avant même que le
jeu démarre. Spartan vérifie ensuite côté serveur que le joueur est bien passé
par lui, et que rien n'a été ajouté entre-temps.

Code de référence prêt à copier : [`docs/launcher/spartan-launcher.js`](launcher/spartan-launcher.js)
(Node.js / Electron, aucune dépendance). Pour un launcher en Java, copier
directement `src/main/java/com/Spartan/integrity/LaunchToken.java` (Java pur).
Les deux implémentations ont été vérifiées l'une contre l'autre : mêmes
empreintes, et un jeton créé en JavaScript est accepté par le serveur.

## Ce que fait le launcher, au clic sur « Jouer »

1. **Vérifier le dossier `mods`** avec `verifyModsFolder(modsDir, manifeste)` :
   le dossier doit contenir **exactement** les mods officiels (aucun en plus,
   aucun modifié). Sinon : resynchroniser les mods, ou refuser de lancer.
   → bloque les mods de triche ajoutés à la main (Meteor, Wurst, X-ray…).
2. **Calculer l'empreinte** du dossier : `modsHash(modsDir)`.
3. **Obtenir un jeton** signé (voir les deux modes ci-dessous) contenant le
   pseudo, une expiration (ex. 12 h) et cette empreinte.
4. **Lancer le jeu** avec l'argument JVM `-Dspartan.launchToken=<jeton>`.
   Le launcher maîtrise aussi les autres arguments JVM. **Ajouter les protections
   anti-injection** (voir ci-dessous) et n'ajouter aucun `-javaagent` non prévu
   (Spartan signale au serveur tout agent non autorisé).

### Protections anti-injection (arguments JVM)
La plupart des injecteurs « à chaud » (qui s'attachent au jeu déjà lancé)
utilisent le mécanisme d'attache de la JVM. Le launcher le désactive en ajoutant
aux arguments de lancement :

```
-XX:+DisableAttachMechanism
-XX:-EnableDynamicAgentLoading   (Java 21+ ; ignoré en Java 17, sans effet négatif)
```

- `DisableAttachMechanism` empêche tout processus externe de s'attacher à la JVM
  du jeu : c'est ce qui bloque la classe entière des injecteurs par attache.
- Le mod vérifie ces protections côté client et le signale au serveur si elles
  manquent. Avec `requireHardenedJvm = true` (config serveur), un joueur dont le
  jeu n'a pas été lancé ainsi est refusé — à n'activer qu'une fois le launcher
  distribué à tous.

Ces protections ne couvrent pas tout (un injecteur plus bas niveau reste
possible), mais elles éliminent les outils grand public et, combinées au filet
serveur, rendent l'injection très coûteuse.

En jeu, Spartan recalcule l'empreinte **réelle** du dossier `mods` et l'envoie au
serveur avec le jeton. Le serveur vérifie :
- la **signature** du jeton (seul le détenteur du secret peut en fabriquer un) ;
- le **pseudo** (un jeton ne sert qu'au joueur pour qui il a été émis) ;
- l'**expiration** ;
- que les mods **réellement chargés** sont ceux que le launcher a vérifiés
  (un mod ajouté après le lancement est repéré) ;
- optionnellement, que l'empreinte fait partie des modpacks officiels
  (`allowedModsHashes`).

## Deux façons d'émettre le jeton

| | Mode A — le launcher signe | Mode B — le serveur d'authentification signe (**recommandé**) |
|---|---|---|
| Où est le secret | Dans le launcher | Uniquement sur ton serveur d'auth et sur le serveur Minecraft |
| Mise en place | Immédiate | Un appel API en plus après le login du launcher |
| Solidité | Le secret peut être extrait en décompilant le launcher : obfusquer le launcher et **changer le secret à chaque mise à jour** | Le secret ne quitte jamais tes serveurs : impossible de fabriquer un jeton |

Comme ton launcher a déjà un login, le **mode B** est le bon choix : après la
connexion réussie, le launcher envoie `modsHash` à ton API, et l'API répond avec
`createLaunchToken(SECRET, pseudo, expiration, modsHash)`.

⚠️ L'API doit signer le pseudo de **sa propre session authentifiée** (celui du
compte qui vient de se connecter), **jamais** un pseudo envoyé par le launcher :
sinon n'importe qui obtiendrait un jeton au nom d'un autre joueur.

Expiration conseillée : **24 h** (le jeton reste valable tant que le joueur ne
relance pas le jeu ; au-delà de 30 jours, le serveur le refuse). Un joueur dont
le jeton a expiré voit « relancez le jeu depuis le launcher ».

## Imposer les ressources du serveur (textures / shaders)

Pour qu'un joueur ne puisse **rien changer** à l'apparence du jeu :

1. **Pack de ressources du serveur obligatoire** — dans `server.properties` :
   ```
   resource-pack=<URL du pack>
   resource-pack-sha1=<sha1 du pack>
   require-resource-pack=true
   ```
   Un joueur qui refuse le pack est expulsé (mécanisme vanilla).
2. **Packs de texture personnalisés interdits** — `blockCustomResourcePacks = true`
   (défaut) : le mod repère et bloque tout pack ajouté par le joueur.
3. **Shaders interdits** — le plus sûr est de **ne pas inclure de mod de shaders**
   (Iris, Oculus, OptiFine) dans le modpack officiel : le launcher empêche alors
   d'en ajouter un. Si tu inclus Iris pour les performances (avec Sodium), laisse
   `blockShaders = true` : le mod détecte le shaderpack actif et le bloque
   (best-effort). Pour autoriser un shaderpack précis, ajoute son nom dans
   `allowedShaderpacks`.

## Côté serveur Minecraft (`config/spartan-common.toml`)

```toml
[launcher]
    launcherSecret = "…au moins 32 caractères aléatoires, le même que le launcher / l'API…"
    tokenRequired = false     # phase 1 : alertes seulement, le temps de distribuer le launcher
    allowedModsHashes = []    # optionnel : empreintes des modpacks officiels
```

1. Générer un secret : `openssl rand -base64 48` (ne jamais le publier ni le commiter).
2. Phase 1 : `tokenRequired = false`. Les joueurs sans jeton valide déclenchent
   une **alerte** (staff + Discord) mais peuvent jouer.
3. Quand tous les joueurs ont le nouveau launcher : `tokenRequired = true`. Un
   joueur sans jeton valide est **expulsé** avec le message « lancez le jeu
   depuis le launcher officiel ».

## Manifeste des mods officiels

```bash
node docs/launcher/make-manifest.js /chemin/vers/mods-officiels > manifest.json
```

- Publier `manifest.json` là où le launcher le télécharge (avec les mods).
- La sortie affiche aussi l'**empreinte du dossier** : la mettre dans
  `allowedModsHashes` si on veut que le serveur refuse tout autre modpack.
- À regénérer à chaque mise à jour du modpack.

## Formats exacts (pour une autre implémentation)

**Empreinte du dossier mods** : pour chaque entrée `.jar` du dossier (non
récursif, liens suivis), une ligne `nom-en-minuscules:sha256hex` pour un fichier,
ou `nom-en-minuscules:dir:sha256hex` pour un dossier nommé `.jar` (mod « éclaté » :
empreinte de ses fichiers, voir le code) ; trier les lignes (ordre des codes
UTF-16), les joindre par `\n`, puis SHA-256 hexadécimal (minuscules) en UTF-8.
Utiliser des noms de fichiers ASCII dans le modpack (les noms accentués peuvent
être lus différemment selon la langue du système).

**Jeton v1** :
```
payload = "v1|<base64url(pseudo)>|<expiration en secondes epoch>|<empreinte mods>|<aléa hex>"
jeton   = base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload))
```
base64url sans `=` de remplissage ; secret et pseudo encodés en UTF-8. Le pseudo
est **sensible à la casse** (en crack, « Steve » et « steve » sont deux comptes).

## Limites (à connaître)

- Un joueur qui extrait le secret (mode A) peut fabriquer des jetons : d'où le
  mode B, ou un secret changé à chaque version du launcher.
- Le launcher et le mod tournent sur le PC du joueur : ce sont des murailles
  qui découragent et ralentissent, pas des garanties. Le filet final reste les
  **détections côté serveur** (vol, vitesse, portée, minage…), que le joueur ne
  peut pas falsifier.
