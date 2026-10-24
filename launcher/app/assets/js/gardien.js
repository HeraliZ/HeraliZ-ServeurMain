'use strict'

/**
 * Gardien : l'anticheat du launcher, relié à Spartan (mods/spartan, client/Gardien.java et serveur/GardienServeur.java).
 *
 * Tant que le jeu lancé par ce launcher est ouvert, le launcher reste en arrière-plan (fenêtre fermée ou non) et, toutes
 * les 10 à 20 s, regarde depuis l'extérieur du jeu ce qu'un jeu modifié ne pourrait pas cacher :
 *  - un programme de triche ou d'injection ouvert sur le PC (Cheat Engine, débogueurs, injecteurs, décompilateurs…) :
 *    nom de l'exécutable, et sous Windows titre de sa fenêtre, comparés à la liste OUTILS. Seuls les noms de la liste
 *    trouvés sont notés (« cheatengine »), JAMAIS la liste des programmes ni les titres des fenêtres du joueur ;
 *  - un jar du jeu changé pendant la partie (dossier mods, bibliothèques des mods du launcher) : taille et date de chaque
 *    fichier, empreinte recalculée seulement si l'une change ;
 *  - le jeu lui-même toujours là (même numéro de processus).
 * Le résultat est écrit dans <dossier du jeu>/spartan/gardien.txt, signé (HMAC-SHA256) avec une clé tirée au hasard à
 * chaque lancement et donnée au seul jeu (variable SPARTAN_GARDIEN_CLE, jamais sur le disque ni sur la ligne de
 * commande). Le jeu relit le fichier, vérifie la signature, le numéro de processus et l'heure, et l'envoie au serveur :
 * c'est le serveur qui juge (alerte au staff). Un launcher fermé de force arrête les rapports : « Gardien muet ».
 * Ce n'est pas un coffre : un tricheur décidé peut lire la clé dans la mémoire du jeu. Le but : ce que l'on cache au
 * jeu, le launcher le voit, et l'inverse.
 */

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const { execFile: execFileCb } = require('child_process')

// morceaux de noms d'exécutables (minuscules, sans .exe) : programmes de triche, d'injection, de débogage et de
// décompilation (vol des jars). Un morceau seulement : « cheatengine-x86_64 » passe par « cheatengine ».
const OUTILS = [
    'cheatengine', 'cheat engine', 'processhacker', 'systeminformer', 'x64dbg', 'x32dbg', 'ollydbg', 'ida64',
    'reclass', 'extremeinjector', 'extreme injector', 'xenos', 'ghinjector', 'gh injector', 'dllinjector',
    'recaf', 'jd-gui', 'bytecode-viewer', 'bytecodeviewer', 'jadx', 'luyten', 'httpdebugger', 'scylla', 'dnspy',
    'kdmapper', 'artmoney', 'squalr'
]
// titres de fenêtre (Windows) : un programme renommé garde souvent son titre
// (mots entiers seulement : « Reclassement.xlsx » ou un onglet « tuto Cheat Engine » d'un navigateur ne comptent pas, voir
// outilsTrouves ; « reclass » et « xenos », trop courants dans un titre, ne sont cherchés que dans le nom de l'exécutable)
const TITRES = ['cheat engine', 'process hacker', 'system informer', 'x64dbg', 'x32dbg', 'ollydbg',
    'extreme injector', 'gh injector', 'recaf', 'bytecode viewer', 'java decompiler', 'jadx', 'luyten',
    'squalr', 'artmoney']
// navigateurs et lecteurs : leur titre est celui de la page ou de la vidéo regardée, jamais un outil ouvert
const SANS_TITRE = /^(chrome|msedge|firefox|opera|brave|vivaldi|discord|explorer|vlc|spotify|notepad|winword|excel)(\.exe)?$/i
const TITRES_RE = TITRES.map(t => new RegExp('(^|[^a-z0-9])' + t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '($|[^a-z0-9])'))

const VERSION = 1
const FICHIER = 'gardien.txt'
const INTERVALLE_MIN_MS = 10_000
const INTERVALLE_MAX_MS = 20_000
const COMMANDE_MS = 12_000
// nom d'outil écrit dans le fichier : lettres, chiffres, espace, point, tiret (comme la liste)
const NOM_SUR = /^[a-z0-9 ._-]{1,32}$/

function execFile(cmd, args, options) {
    return new Promise((resolve, reject) => {
        execFileCb(cmd, args, { maxBuffer: 16 * 1024 * 1024, ...options }, (err, stdout) => err ? reject(err) : resolve(String(stdout)))
    })
}

/** Morceaux de la liste trouvés dans un nom d'exécutable ou un titre (minuscules), sans doublon, triés. */
function outilsTrouves(noms, titres = []) {
    const trouves = new Set()
    for (const n of noms) {
        const nom = String(n || '').toLowerCase().replace(/\.exe$/, '')
        for (const o of OUTILS) if (nom.includes(o)) trouves.add(o)
    }
    for (const t of titres) {
        const titre = String(t && typeof t === 'object' ? t.titre : t || '').toLowerCase()
        if (t && typeof t === 'object' && SANS_TITRE.test(String(t.nom || ''))) continue
        if (!titre || titre === 'n/a') continue
        TITRES.forEach((o, i) => { if (TITRES_RE[i].test(titre)) trouves.add(o) })
    }
    return [...trouves].filter(o => NOM_SUR.test(o)).sort()
}

/** Lecture d'une ligne CSV de tasklist ("a","b",...) : champs entre guillemets, "" pour un guillemet. */
function champsCsv(ligne) {
    const out = []
    const re = /"((?:[^"]|"")*)"/g
    let m
    while ((m = re.exec(ligne)) !== null) out.push(m[1].replace(/""/g, '"'))
    return out
}

/** Programmes ouverts : { noms, titres }. Rien en cas d'erreur (le jeu ne doit jamais en pâtir). */
async function programmes(plateforme = process.platform) {
    try {
        if (plateforme === 'win32') {
            // /V : titre de la fenêtre principale (dernière colonne)
            const out = await execFile('tasklist', ['/V', '/FO', 'CSV', '/NH'], { timeout: COMMANDE_MS, windowsHide: true })
            const noms = [], titres = []
            for (const ligne of out.split(/\r?\n/)) {
                const c = champsCsv(ligne)
                if (c.length < 2) continue
                noms.push(c[0])
                if (c.length >= 9) titres.push({ nom: c[0], titre: c[c.length - 1] })
            }
            return { noms, titres }
        }
        const out = await execFile('ps', ['-A', '-o', 'comm='], { timeout: COMMANDE_MS })
        return { noms: out.split(/\r?\n/).map(l => path.basename(l.trim())).filter(Boolean), titres: [] }
    } catch {
        return null
    }
}

function sha256Fichier(p) {
    return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex')
}

/** Jars sous un dossier (liens jamais suivis), chemin relatif -> fichier. */
function jars(dossier, max = 5000) {
    const out = new Map()
    if (!dossier || !fs.existsSync(dossier)) return out
    ;(function parcourir(d, profondeur) {
        if (profondeur > 12 || out.size >= max) return
        let entrees
        try { entrees = fs.readdirSync(d, { withFileTypes: true }) } catch { return }
        for (const e of entrees) {
            const p = path.join(d, e.name)
            if (e.isSymbolicLink()) continue
            if (e.isDirectory()) parcourir(p, profondeur + 1)
            else if (e.isFile() && e.name.toLowerCase().endsWith('.jar')) out.set(path.relative(dossier, p), p)
        }
    })(dossier, 0)
    return out
}

class Gardien {
    /**
     * @param {object} o
     * @param {string} o.dossierJeu dossier du jeu (instance) : le fichier va dans <dossierJeu>/spartan/
     * @param {string[]} o.dossiersJars dossiers dont les jars ne doivent pas changer pendant la partie
     * @param {number} o.pid numéro du processus du jeu
     * @param {string} o.cle clé de signature (hexadécimal), la même que celle donnée au jeu
     */
    constructor({ dossierJeu, dossiersJars = [], pid, cle, lister = programmes, maintenant = Date.now }) {
        this.dossierJeu = dossierJeu
        this.dossiersJars = dossiersJars
        this.pid = pid
        this.cle = Buffer.from(cle, 'hex')
        this.lister = lister
        this.maintenant = maintenant
        this.n = 0
        this.etat = new Map() // chemin -> { taille, date, empreinte }
        this.modifies = new Set()
        this.minuteur = null
        this.arrete = false
    }

    /** Clé neuve pour un lancement : 32 octets en hexadécimal. */
    static nouvelleCle() {
        return crypto.randomBytes(32).toString('hex')
    }

    get fichier() {
        return path.join(this.dossierJeu, 'spartan', FICHIER)
    }

    /** Relevé de départ des jars (avant le premier rapport), puis premier rapport et suivants. */
    demarrer() {
        this.releverJars(true)
        this.tour()
        return this
    }

    arreter() {
        this.arrete = true
        if (this.minuteur) clearTimeout(this.minuteur)
        this.minuteur = null
        try { fs.rmSync(this.fichier, { force: true }) } catch { /* jeu encore en train de le lire : sans importance */ }
    }

    async tour() {
        if (this.arrete) return
        try {
            await this.rapport()
        } catch (erreur) {
            console.error('[HeraliZ/gardien]', erreur?.code || erreur?.message)
        }
        if (this.arrete) return
        const attente = INTERVALLE_MIN_MS + Math.floor(Math.random() * (INTERVALLE_MAX_MS - INTERVALLE_MIN_MS))
        this.minuteur = setTimeout(() => this.tour(), attente)
        this.minuteur.unref?.()
    }

    /** Jars changés depuis le départ (un jar retiré ou ajouté compte aussi). */
    releverJars(depart = false) {
        const vus = new Set()
        for (const dossier of this.dossiersJars) {
            for (const [rel, p] of jars(dossier)) {
                const cle = dossier + '|' + rel
                vus.add(cle)
                let st
                try { st = fs.statSync(p) } catch { continue }
                const avant = this.etat.get(cle)
                if (!avant) {
                    if (!depart) this.modifies.add(path.basename(p))
                    this.etat.set(cle, { taille: st.size, date: st.mtimeMs, empreinte: null })
                    continue
                }
                if (avant.taille === st.size && avant.date === st.mtimeMs) continue
                let e
                try { e = sha256Fichier(p) } catch { continue }
                // pas d'empreinte de départ (relevé rapide au lancement) : taille ou date changée = changé
                if (avant.empreinte == null || avant.empreinte !== e) this.modifies.add(path.basename(p))
                this.etat.set(cle, { taille: st.size, date: st.mtimeMs, empreinte: e })
            }
        }
        if (!depart) {
            for (const cle of this.etat.keys()) if (!vus.has(cle)) this.modifies.add(path.basename(cle.split('|').pop()))
        }
    }

    async rapport() {
        const liste = await this.lister()
        const outils = liste ? outilsTrouves(liste.noms, liste.titres) : []
        this.releverJars(false)
        const ligne = Gardien.ligne({
            n: ++this.n,
            t: this.maintenant(),
            pid: this.pid,
            lecture: liste ? 'ok' : 'impossible',
            outils,
            modifies: this.modifies.size
        })
        const contenu = ligne + '\n' + crypto.createHmac('sha256', this.cle).update(ligne).digest('hex') + '\n'
        fs.mkdirSync(path.dirname(this.fichier), { recursive: true })
        // écrit à côté puis renommé : le jeu ne lit jamais un fichier à moitié écrit
        const tmp = this.fichier + '.tmp'
        fs.writeFileSync(tmp, contenu)
        fs.renameSync(tmp, this.fichier)
        return ligne
    }

    /**
     * Ligne signée, lue par client/Gardien.java (même format, à changer des deux côtés) :
     * v1|n|t|pid|lecture|outil,outil|nombre de jars changés
     */
    static ligne({ n, t, pid, lecture, outils, modifies }) {
        const o = (outils || []).filter(x => NOM_SUR.test(x)).join(',')
        return ['v' + VERSION, n, t, pid, lecture, o, modifies].join('|')
    }
}

module.exports = { Gardien, outilsTrouves, champsCsv, OUTILS, TITRES }
