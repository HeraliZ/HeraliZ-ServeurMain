# Spartan et le launcher HeraliZ

Le launcher HeraliZ (`launcher/` du dépôt) est **la première muraille** : il prépare un jeu propre, demande au site
l'autorisation de jouer, puis surveille le PC pendant la partie. Spartan vérifie ensuite, sur le serveur, que le joueur
est bien passé par lui. Il n'existe plus qu'une seule façon de faire (l'ancien « mode A », où le launcher signait lui-même
avec un secret embarqué, et le jeton passé en option Java `-Dspartan.launchToken` ont disparu).

Code réel, à lire avant de changer quoi que ce soit (les deux côtés doivent rester d'accord) :

| Côté | Fichier | Rôle |
|---|---|---|
| launcher | `launcher/app/assets/js/spartan.js` | options Java permises, protections, empreinte du dossier mods, environnement du jeu |
| launcher | `launcher/app/assets/js/gardien.js` | Gardien : surveillance du PC pendant la partie, rapports signés |
| launcher | `launcher/app/assets/js/launcher-service.js` | ordre des étapes au clic sur « Jouer » |
| launcher | `launcher/app/assets/js/integrite.js` | jars en trop du dossier mods mis de côté |
| site | `site/extension/heraliz/src/Controllers/JetonController.php` | jeton de lancement signé pour le compte connecté |
| jeu | `src/commun/java/com/Spartan/integrity/LaunchToken.java` | format du jeton, empreinte du dossier mods |
| jeu | `src/client/java/com/Spartan/Client/Gardien.java` | transmet les rapports du Gardien tels quels, recopie le défi |
| serveur | `src/serveur/java/com/Spartan/integrity/LauncherGate.java` | vérifie le jeton (et la clé du Gardien qu'il engage) |
| serveur | `src/serveur/java/com/Spartan/integrity/GardienPreuve.java`, `GardienJuge.java`, `serveur/GardienServeur.java` | vérifie et juge les rapports du Gardien |

## Au clic sur « Jouer »

1. **Pack signé** : le launcher télécharge le pack (mods, gunpacks) depuis le site et refuse un pack qui n'est pas signé
   par HeraliZ (`signature.js`). Les anciennes versions sont effacées, les jars en trop du dossier `mods` mis de côté.
2. **Java et options** : Java contrôlé ; les options Java du joueur passent par une liste blanche (mémoire, ramasse-miettes,
   réglages de Forge) ; `-XX:+DisableAttachMechanism` est toujours ajouté (aucun programme ne peut s'attacher au jeu) ;
   les variables d'environnement qui injectent du code (`JAVA_TOOL_OPTIONS`, `LD_PRELOAD`…) sont retirées.
3. **Clés du Gardien** (launcher 1.16.0, quand le pack contient Spartan 2.10.0 ou plus récent) : une paire de clés Ed25519
   tirée pour ce lancement. La clé privée reste dans la mémoire du launcher ; la clé publique part au jeu.
4. **Jeton de lancement** : le launcher envoie au site (compte connecté) `SHA-256("gardien-v2|" + empreinte du dossier
   mods + "|" + clé publique)` ; le site signe ce texte avec la clé des jetons (jamais dans le launcher ni dans le dépôt),
   pour le pseudo de SON compte, valable 6 h. Ancien mode (pack plus ancien) : l'empreinte des mods seule.
5. **Lancement** : le jeton passe par la variable `SPARTAN_LAUNCH_TOKEN`, la clé publique par `SPARTAN_GARDIEN_PUB`
   (ancien mode : clé HMAC `SPARTAN_GARDIEN_CLE`), l'accord des captures par `SPARTAN_CAPTURE_CONSENT`. Jamais sur la
   ligne de commande. Le jeu les efface de son environnement dès le démarrage.
6. **Pendant la partie** : le launcher reste en arrière-plan (Gardien, ci-dessous) jusqu'à la fermeture du jeu.

## Ce que vérifie le serveur à la connexion

- **Jeton** (`[launcher]`, `tokenRequired = true` sur HeraliZ) : signature du site, pseudo exact (sensible à la casse),
  expiration (6 h, jamais plus de 30 jours) ; l'empreinte signée doit être celle des mods réellement chargés (calculée
  par le jeu) — seule, ou avec la clé du Gardien annoncée par le jeu (la clé est alors **prouvée**). Sinon : connexion
  refusée (« lancez le jeu depuis le launcher HeraliZ »).
- **Jeu identique au jeu officiel** (`integrity.strictOfficial`) : voir le README (§ Jeu identique), `officiel.json` étant
  fabriqué avec le serveur par `outils/construire.py` (rien à faire à la main).
- **Protections** : jeu sans `DisableAttachMechanism`, agent Java (`allowedJavaAgents` vide : aucun n'est permis) : écart.
- **Launcher à jour et qui veille** (03/11, `[gardien]`) : jeu lancé sans Gardien (`required = true`) ou launcher 1.15 et
  plus ancien (`ancienLauncher = "refuser"`) : refus « ton launcher n'est pas à jour, ferme-le et rouvre-le » ; launcher
  1.16 et plus récent sans première preuve du Gardien dans les `preuveSecondes` (120 s) après le /login, ou avec un
  rapport signé d'une autre clé avant toute preuve (`preuveExigee = "refuser"`) : refus « le launcher ne répond pas,
  relance depuis le launcher et laisse-le ouvert ». Toujours un refus de connexion propre, jamais un plantage du jeu
  (fragile, contournable, et un joueur honnête en pâtirait).
- **Injection** (03/11, `[injection]`) : traces certaines d'un code étranger dans le jeu (agent, débogueur, anticheat
  modifié en mémoire, client de triche connu, défi faux, code natif caché revu deux fois) ou injecteur / éditeur de
  mémoire vu par le Gardien : alerte + capture + expulsion. Voir le README et la section du fichier de config.

## Le Gardien (section `[gardien]`)

Toutes les 10 à 20 s, le launcher regarde depuis l'extérieur du jeu les programmes de triche connus (Cheat Engine,
débogueurs, injecteurs, décompilateurs : seuls les noms de sa liste trouvés partent, jamais la liste des programmes du
joueur) et les jars du jeu (changés pendant la partie). Il écrit une ligne dans `spartan/gardien.txt` du dossier du jeu :

```
v2|n|t|pid|défi|lecture|outil,outil|jars changés
<signature Ed25519 de la ligne, base64url>
```

Le jeu la transmet **telle quelle** au serveur toutes les 15 s ; il ne vérifie rien et n'a aucune clé secrète. Le serveur
vérifie : la signature avec la clé prouvée par le jeton, le numéro (jamais en arrière), le numéro de processus du jeu, et
le **défi** : 16 octets tirés par le serveur pour cette connexion et renouvelés chaque minute (`defiSeconds`), envoyés au
jeu qui les écrit dans `spartan/gardien-defi.txt`, que le launcher recopie dans sa ligne. Un rapport sans le défi récent
n'est pas « frais ».

Alertes (jamais d'expulsion en pleine partie, sauf les programmes de `[injection] outils` ; à la connexion : `required`,
`ancienLauncher = "refuser"`, `preuveExigee = "refuser"`) :
- « Gardien » : programme de triche ouvert, jar changé pendant la partie, rapport refusé (inventé, retouché, rejoué,
  d'un autre jeu), jeu lancé sans Gardien, ancien launcher (selon `ancienLauncher`), joueur revenu avec un ancien
  launcher après avoir eu le nouveau ;
- « Gardien muet » : plus aucun rapport prouvé et frais depuis `silenceSeconds` (launcher fermé de force, jeu modifié qui
  ne transmet plus ou renvoie de vieux rapports).

Liste des programmes (launcher 1.16.2) : noms par `tasklist /FO CSV /NH` (rapide ; PowerShell `Get-Process` en
secours), titres des fenêtres à part par PowerShell. Avant, `tasklist /V` (titres et comptes de tous les programmes)
dépassait souvent 12 s sur un PC chargé : « lecture impossible ». Un outil vu seulement dans un titre de fenêtre part
marqué `outil.titre` : alerte, jamais expulsion.

## Lancement bloqué par le launcher (launcher 1.16.2, site 1.4.1)

Le launcher refuse de lancer le jeu (ou remet des fichiers en état) quand : un injecteur ou éditeur de mémoire est
ouvert (nom de l'exécutable), un fichier du jeu diffère du pack signé, le Java du jeu a été modifié (remplacé ; refusé
s'il l'est de nouveau aussitôt), des jars traînent dans le dossier mods (mis de côté), le pack est refusé (signature).
Chaque cas est signalé au site (`POST /api/heraliz/launcher/refus`, compte connecté, raison d'une liste fermée, détail
court sans chemin ni donnée personnelle), qui le poste dans le salon privé du staff (une alerte par compte et par raison
toutes les 10 min ; synthèse au-delà de 5 comptes en 10 min).

## Mise en ligne d'un changement

1. Changement du format (jeton, ligne du Gardien, empreinte) : changer **les deux côtés** et leurs tests
   (`launcher/test/gardien.test.js`, `GardienPreuveTest`, `GardienJugeTest`, `LaunchTokenTest`), monter la version du
   launcher et de Spartan.
2. Envoyer sur `main` (serveur et pack), puis publier le launcher (workflow « Launcher HeraliZ », case « publier ») : les
   launchers Windows et AppImage se mettent à jour seuls au démarrage, Mac et `.deb` préviennent le joueur.
3. Le launcher ne passe à un nouveau format que si le pack contient la version de Spartan qui le comprend : il peut être
   publié avant ou après le serveur sans casser les connexions.

## Limites (à connaître)

Tout ce qui tourne sur le PC du joueur peut mentir à un tricheur déterminé : un programme qui imite le launcher avec le
compte du joueur obtient un jeton du site et peut signer ses propres rapports du Gardien ; un Spartan modifié avec soin
peut répondre juste au défi d'intégrité ; le jeu calcule lui-même l'empreinte de ses mods. Ces murailles arrêtent les
outils tout faits et obligent à un vrai travail ; le filet final reste les **détections côté serveur** (mouvement,
combat, tirs TaCZ, latence, anti-ESP), que le joueur ne peut pas falsifier.
