'use strict'

// Intégrité du jeu avant chaque partie (27/10, demande du propriétaire : « le launcher vérifie aussi que le jeu n'est pas
// modifié »). En plus du contrôle des fichiers du pack (empreinte de chaque fichier, refait à chaque partie par repair) :
//  - dossier mods du jeu : le pack HeraliZ n'y met rien (ses mods sont dans le dossier du launcher) ; tout jar posé là
//    serait chargé par Forge : il est mis de côté (déplacé dans <jeu>/spartan/ecartes, jamais effacé : c'est un fichier
//    du joueur) ;
//  - Java du jeu : empreinte de ses programmes, bibliothèques et classes, relevée quand il est installé ; un fichier
//    changé ou ajouté (une bibliothèque glissée dans son dossier serait chargée par le jeu) : ce Java n'est plus utilisé
//    et le launcher en retélécharge un propre.
// Jamais rien hors des dossiers du launcher ; les liens ne sont jamais suivis (comme effacement.js).

const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const comparable = chemin => process.platform === 'win32' || process.platform === 'darwin' ? chemin.toLowerCase() : chemin

function dans(parent, enfant) {
    const relatif = path.relative(comparable(path.resolve(parent)), comparable(path.resolve(enfant)))
    return relatif !== '' && !relatif.startsWith('..') && !path.isAbsolute(relatif)
}

/**
 * Met de côté les jars du dossier mods du jeu (<dossierInstances>/<serveur>/mods). Rend { ecartes: [noms], erreurs: [codes] }.
 * Le dossier mods lui-même ou un lien à sa place : rien n'est touché.
 */
function ecarterJarsEnTrop({ dossierInstances, serveur, maintenant = Date.now }) {
    const resultat = { ecartes: [], erreurs: [] }
    if (typeof serveur !== 'string' || !/^[\w.-]+$/.test(serveur) || typeof dossierInstances !== 'string') return resultat
    const instance = path.join(dossierInstances, serveur)
    const mods = path.join(instance, 'mods')
    let infos
    try { infos = fs.lstatSync(mods) } catch { return resultat }
    if (!infos.isDirectory() || infos.isSymbolicLink()) return resultat
    let noms
    try { noms = fs.readdirSync(mods) } catch (erreur) { resultat.erreurs.push(erreur?.code || 'ERREUR'); return resultat }
    const jars = noms.filter(n => /\.(jar|zip)$/i.test(n))
    if (!jars.length) return resultat
    const cote = path.join(instance, 'spartan', 'ecartes', String(maintenant()))
    for (const nom of jars) {
        const source = path.join(mods, nom)
        try {
            const st = fs.lstatSync(source)
            if (st.isSymbolicLink()) { fs.unlinkSync(source); resultat.ecartes.push(nom); continue }   // un lien : seul le lien part
            if (!st.isFile() || !dans(instance, source)) continue
            fs.mkdirSync(cote, { recursive: true })
            fs.renameSync(source, path.join(cote, nom))
            resultat.ecartes.push(nom)
        } catch (erreur) {
            resultat.erreurs.push(erreur?.code || 'ERREUR')
        }
    }
    return resultat
}

// Fichiers du Java comparés : programmes, bibliothèques natives, jars, archive des classes et sa copie partagée.
const SURVEILLES = /\.(dll|exe|so|dylib|jar|jsa)$/i
const NOMS_SURVEILLES = new Set(['modules', 'release'])

function surveille(relatif) {
    return SURVEILLES.test(relatif) || NOMS_SURVEILLES.has(path.basename(relatif).toLowerCase())
}

async function sha256(fichier) {
    const h = crypto.createHash('sha256')
    await new Promise((resolve, reject) => {
        fs.createReadStream(fichier).on('data', d => h.update(d)).on('end', resolve).on('error', reject)
    })
    return h.digest('hex')
}

/** Empreintes des fichiers surveillés d'un dossier Java : { chemin relatif (/) -> sha256 }. Liens jamais suivis. */
async function empreintesJava(racine, max = 20_000) {
    const fichiers = []
    ;(function parcourir(d, profondeur) {
        if (profondeur > 16 || fichiers.length >= max) return
        let entrees
        try { entrees = fs.readdirSync(d, { withFileTypes: true }) } catch { return }
        for (const e of entrees) {
            const p = path.join(d, e.name)
            if (e.isSymbolicLink()) continue
            if (e.isDirectory()) parcourir(p, profondeur + 1)
            else if (e.isFile() && surveille(path.relative(racine, p))) fichiers.push(p)
        }
    })(racine, 0)
    const out = {}
    for (const f of fichiers) out[path.relative(racine, f).split(path.sep).join('/')] = await sha256(f)
    return out
}

function lireJson(fichier) {
    try { return JSON.parse(fs.readFileSync(fichier, 'utf8')) } catch { return null }
}

/**
 * Contrôle d'un dossier Java contre son relevé (fichier JSON du launcher, une entrée par dossier). Premier contrôle :
 * relevé pris (Java installé ou validé par le launcher à ce moment-là). Rend { etat: 'nouveau'|'ok'|'change', ecarts: [] }
 * (ecarts : chemins relatifs changés, ajoutés ou retirés, au plus 10).
 */
async function controlerJava(racine, fichierReleve, { reprendre = false } = {}) {
    const actuel = await empreintesJava(racine)
    const tous = lireJson(fichierReleve) || {}
    const cle = comparable(path.resolve(racine))
    const avant = reprendre ? null : tous[cle]
    if (!avant || typeof avant !== 'object') {
        tous[cle] = actuel
        fs.mkdirSync(path.dirname(fichierReleve), { recursive: true })
        fs.writeFileSync(fichierReleve + '.tmp', JSON.stringify(tous))
        fs.renameSync(fichierReleve + '.tmp', fichierReleve)
        return { etat: 'nouveau', ecarts: [] }
    }
    const ecarts = []
    for (const [rel, e] of Object.entries(actuel)) if (avant[rel] !== e) ecarts.push(rel)
    for (const rel of Object.keys(avant)) if (!(rel in actuel)) ecarts.push(rel)
    return { etat: ecarts.length ? 'change' : 'ok', ecarts: ecarts.slice(0, 10) }
}

/** Oublie le relevé d'un dossier Java (avant de le remplacer). */
function oublierJava(racine, fichierReleve) {
    const tous = lireJson(fichierReleve)
    if (!tous) return
    delete tous[comparable(path.resolve(racine))]
    try {
        fs.writeFileSync(fichierReleve + '.tmp', JSON.stringify(tous))
        fs.renameSync(fichierReleve + '.tmp', fichierReleve)
    } catch { /* relevé laissé : sera repris au prochain contrôle */ }
}

module.exports = { ecarterJarsEnTrop, controlerJava, oublierJava, empreintesJava, dans }
