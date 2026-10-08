'use strict'

// Lancé automatiquement avant la fabrication du launcher (npm run dist / dist:win).
// Écrit la clé des jetons Spartan dans app/assets/js/spartan-cle.json depuis la variable d'environnement
// SPARTAN_LAUNCHER_SECRET (même valeur que le secret GitHub du serveur). Le fichier n'est JAMAIS dans Git.
// Sans variable : le fichier est retiré, le launcher ne fabrique pas de jeton (le serveur ne fait que signaler).

const fs = require('fs')
const path = require('path')

const fichier = path.join(__dirname, '..', 'app', 'assets', 'js', 'spartan-cle.json')
const cle = (process.env.SPARTAN_LAUNCHER_SECRET || '').trim()

if (cle.length >= 32) {
    fs.writeFileSync(fichier, JSON.stringify({ cle }), 'utf8')
    console.log('[spartan] clé des jetons ajoutée au launcher.')
} else {
    fs.rmSync(fichier, { force: true })
    console.log('[spartan] SPARTAN_LAUNCHER_SECRET absent (ou moins de 32 caractères) : launcher sans jeton.')
}
