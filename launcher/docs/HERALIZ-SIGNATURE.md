# Signer les mises à jour du launcher (anti-piratage de la distribution)

## Le problème que ça règle

Le launcher installé se met à jour tout seul depuis `https://heraliz.fr/telecharger/`. Aujourd'hui, il contrôle
seulement l'empreinte (SHA-512) écrite dans `latest.yml`. Mais **qui prend la VM du site (ou un accès GitHub) peut
réécrire `latest.yml` ET l'installeur ensemble** : rien ne l'arrête, et il livrerait un faux launcher à **tous** les
joueurs d'un coup.

La **signature** règle ça : le fichier de version est signé avec une **clé privée qui n'est jamais sur la VM** (elle
reste un secret GitHub, fabriqué chez toi). Le launcher porte la **clé publique** et **refuse toute mise à jour non
signée par ta clé**. Un pirate qui a la VM n'a pas la clé : il ne peut plus rien livrer.

Comme `latest.yml` contient l'empreinte de l'installeur, signer `latest.yml` protège aussi l'installeur.

## Ce qui est déjà en place (côté code)

- le launcher vérifie la signature avant toute mise à jour (`app/assets/js/signature.js`, `mise-a-jour.js`) ;
- la publication signe les fichiers de version (`ci/signer.mjs`, branché dans `ci/publier.py` et le workflow) ;
- **tant que tu n'as pas fait les 3 étapes ci-dessous, rien ne change** : le launcher se met à jour comme avant.

## Les 3 étapes (à faire une seule fois)

### 1. Fabriquer la paire de clés (sur ton PC)

Dans le dossier `launcher/` :

```
node ci/signer.mjs --generer
```

Ça affiche deux lignes : une **CLE PUBLIQUE** et une **CLE PRIVEE**. Garde-les sous la main pour les étapes 2 et 3.
La clé privée ne doit **jamais** être mise dans le dépôt, ni sur la VM du site, ni envoyée à qui que ce soit.

### 2. Mettre la CLE PUBLIQUE dans le launcher

Dans `launcher/app/heraliz.json`, colle la clé publique dans `distributionPublicKey` :

```json
"distributionPublicKey": "COLLE_ICI_LA_CLE_PUBLIQUE"
```

Puis enregistre (commit + push). C'est public, aucun secret là-dedans.

### 3. Mettre la CLE PRIVEE dans un secret GitHub

Sur GitHub : **Settings → Secrets and variables → Actions → New repository secret**
- **Name** : `LAUNCHER_SIGN_KEY`
- **Secret** : la CLE PRIVEE de l'étape 1.

C'est tout.

## Ordre important (sinon les mises à jour se bloquent)

Fais l'**étape 3 (secret) AVANT ou EN MÊME TEMPS** que tu publies le premier launcher qui porte la clé publique
(étape 2). Sinon, un launcher avec clé publique recevrait une mise à jour **non signée** et la refuserait.

La bascule est douce : les launchers déjà installés (sans clé) installent normalement le premier launcher « à clé »
par l'ancienne voie ; à partir de là, ils exigent la signature. Le résumé de chaque publication dit en clair si la mise
à jour est **signée** (✅) ou **non signée** (⚠️).

## Vérifier que ça marche

- Publie une version (workflow « Launcher HeraliZ », case « publier ») : le résumé doit afficher « mise à jour
  **signée** ».
- Dans un navigateur, `https://heraliz.fr/telecharger/latest.yml.sig` doit exister (à côté de `latest.yml`).
- Si tu changes un octet de `latest.yml` à la main sur la VM sans re-signer, le launcher refusera la mise à jour
  (voir le journal : « signature de la mise à jour invalide »).

## Si tu perds la clé privée / veux en changer

Refais l'étape 1, remets la nouvelle clé publique (étape 2) et le nouveau secret (étape 3). Les launchers déjà
installés qui portaient l'ancienne clé passeront à la nouvelle par la même bascule douce (la mise à jour qui change la
clé publique doit être signée avec l'**ancienne** clé — donc garde l'ancien secret le temps de cette mise à jour, puis
remplace-le).

## À venir (pas encore fait)

Le **pack de jeu** (mods, gunpacks : `distribution.json`) n'est pas encore signé de cette façon, parce que le site
réécrit les liens des fichiers à chaque téléchargement (liens signés par compte). Il faudra signer les **empreintes des
fichiers** du pack (pas les liens). C'est l'étape suivante.
