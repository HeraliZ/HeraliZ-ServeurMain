// Génère le manifeste des mods officiels à partir du dossier mods de référence.
// Usage : node make-manifest.js /chemin/vers/mods > manifest.json
// Le launcher télécharge ce manifeste et refuse de lancer si le dossier mods du
// joueur ne correspond pas exactement (voir verifyModsFolder).
//
// La dernière ligne affichée sur la sortie d'erreur est l'empreinte du dossier :
// à mettre dans allowedModsHashes (config/spartan-common.toml du serveur).

'use strict';

const { modsHash, manifestOf } = require('./spartan-launcher');

const dir = process.argv[2];
if (!dir) {
  console.error('Usage : node make-manifest.js <dossier mods>');
  process.exit(1);
}

const manifest = manifestOf(dir);
process.stdout.write(JSON.stringify(manifest, null, 2) + '\n');
console.error(Object.keys(manifest).length + ' mods. Empreinte du dossier (allowedModsHashes) : ' + modsHash(dir));
