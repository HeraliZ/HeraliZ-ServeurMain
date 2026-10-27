// Fichiers emballés dans le launcher installé (liste `files` de electron-builder.yml) : tout ce que le launcher charge
// doit y être. Le 27/10, gardien.js (1.14.0) et integrite.js (1.15.0) manquaient : le launcher installé plantait au
// démarrage (fenêtre sans rien derrière, accord de l'anticheat impossible), sans que les tests le voient.
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { builtinModules } = require('node:module')
const yaml = require('js-yaml')
const { minimatch } = require('minimatch')

const RACINE = path.join(__dirname, '..')
const IGNORES = new Set(['node_modules', 'dist', 'test', 'ci', 'docs', 'build', 'libraries', '.git'])

function tousLesFichiers(dossier = RACINE, prefixe = '') {
    const liste = []
    for (const e of fs.readdirSync(dossier, { withFileTypes: true })) {
        if (!prefixe && IGNORES.has(e.name)) continue
        const rel = prefixe ? `${prefixe}/${e.name}` : e.name
        if (e.isDirectory()) liste.push(...tousLesFichiers(path.join(dossier, e.name), rel))
        else liste.push(rel)
    }
    return liste
}

const config = yaml.load(fs.readFileSync(path.join(RACINE, 'electron-builder.yml'), 'utf8'))
const emballes = new Set(tousLesFichiers().filter(f => config.files.some(motif => minimatch(f, motif))))
const paquet = JSON.parse(fs.readFileSync(path.join(RACINE, 'package.json'), 'utf8'))
const lire = rel => fs.readFileSync(path.join(RACINE, rel), 'utf8')
const vers = (depuis, cible) => path.posix.normalize(path.posix.join(path.posix.dirname(depuis), cible))

test('les fichiers d’entrée du launcher sont emballés', () => {
    for (const f of ['index.js', 'package.json', 'app/preload.js', 'app/index.html', 'app/heraliz.json']) {
        assert.ok(emballes.has(f), `${f} manque dans files de electron-builder.yml`)
    }
})

test('chaque module chargé par le launcher est emballé, ou est une dépendance du paquet', () => {
    const dependances = new Set(Object.keys(paquet.dependencies || {}))
    for (const fichier of [...emballes].filter(f => f.endsWith('.js'))) {
        for (const [, cible] of lire(fichier).matchAll(/require\(\s*['"]([^'"]+)['"]\s*\)/g)) {
            if (cible.startsWith('.')) {
                const base = vers(fichier, cible)
                const trouve = [base, `${base}.js`, `${base}.json`, `${base}/index.js`].some(c => emballes.has(c))
                assert.ok(trouve, `${fichier} charge ${cible}, absent de files dans electron-builder.yml`)
            } else {
                const nom = cible.startsWith('@') ? cible.split('/').slice(0, 2).join('/') : cible.split('/')[0]
                const interne = cible.startsWith('node:') || builtinModules.includes(nom) || nom === 'electron'
                assert.ok(interne || dependances.has(nom), `${fichier} charge ${cible}, qui n’est pas dans dependencies`)
            }
        }
    }
})

test('chaque image, police, feuille de style et script de la page est emballé', () => {
    const page = 'app/index.html'
    for (const [, cible] of lire(page).matchAll(/\b(?:src|href)="([^"#:]+)"/g)) {
        assert.ok(emballes.has(vers(page, cible)), `${page} affiche ${cible}, absent de files dans electron-builder.yml`)
    }
    for (const css of [...emballes].filter(f => f.endsWith('.css'))) {
        for (const [, cible] of lire(css).matchAll(/url\(\s*['"]?([^'")#:]+)['"]?\s*\)/g)) {
            assert.ok(emballes.has(vers(css, cible)), `${css} utilise ${cible}, absent de files dans electron-builder.yml`)
        }
    }
    for (const [, image] of lire('app/assets/js/nouveautes.js').matchAll(/image:\s*'([^']+)'/g)) {
        const f = `app/assets/images/nouveautes/${image}`
        assert.ok(emballes.has(f), `nouveautes.js affiche ${f}, absent de files dans electron-builder.yml`)
    }
})
