'use strict'

/**
 * Gardien : l'anticheat du launcher, relié à Spartan (modules/anticheat : client/Gardien.java,
 * serveur/GardienServeur.java et integrity/GardienPreuve.java).
 *
 * Tant que le jeu lancé par ce launcher est ouvert, le launcher reste en arrière-plan (fenêtre fermée ou non) et, toutes
 * les 10 à 20 s, regarde depuis l'extérieur du jeu ce qu'un jeu modifié ne pourrait pas cacher :
 *  - un programme de triche ou d'injection ouvert sur le PC (Cheat Engine, débogueurs, injecteurs, décompilateurs…) :
 *    nom de l'exécutable, et sous Windows titre de sa fenêtre, comparés à la liste OUTILS. Seuls les noms de la liste
 *    trouvés sont notés (« cheatengine »), JAMAIS la liste des programmes ni les titres des fenêtres du joueur ;
 *  - un jar du jeu changé pendant la partie (dossier mods, bibliothèques des mods du launcher) : taille et date de chaque
 *    fichier, empreinte recalculée seulement si l'une change ;
 *  - le jeu lui-même toujours là (même numéro de processus).
 * Le résultat est écrit dans <dossier du jeu>/spartan/gardien.txt (une ligne, puis sa signature) ; le jeu le transmet tel
 * quel au serveur, qui le vérifie et juge (alerte au staff). Un launcher fermé de force arrête les rapports : « Gardien
 * muet ».
 *
 * Preuve (launcher 1.16.0, audit du 02/11 ; quand le pack contient Spartan 2.10.0 ou plus récent, versionPreuve) :
 *  - à chaque lancement, une paire de clés Ed25519 ; la clé PRIVÉE reste dans la mémoire du launcher (jamais sur le
 *    disque, jamais donnée au jeu) ; la clé publique part au jeu (SPARTAN_GARDIEN_PUB) ;
 *  - la clé publique est liée au jeton de lancement : le launcher demande au site un jeton pour engagement(empreinte des
 *    mods, clé publique) au lieu de l'empreinte seule ; le serveur, qui sait vérifier le jeton, refait le calcul ;
 *  - chaque ligne porte le dernier défi tiré par le serveur pour la connexion (le jeu l'écrit dans
 *    spartan/gardien-defi.txt) : un vieux rapport rejoué n'est pas frais.
 * Un jeu modifié ne peut donc plus envoyer un rapport « propre » de son invention : il peut seulement se taire (alerte).
 * Ancien mode (pack avec un Spartan plus ancien) : clé HMAC donnée au jeu (SPARTAN_GARDIEN_CLE), lignes v1 ; le serveur ne
 * peut pas les prouver. Limite qui reste : un programme qui imite tout le launcher avec le compte du joueur peut signer ses
 * propres rapports (le site ne sait pas reconnaître le vrai launcher).
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

const FICHIER = 'gardien.txt'
// défi du serveur pour la connexion en cours, écrit par le jeu (client/Gardien.java), lu à chaque rapport
const FICHIER_DEFI = 'gardien-defi.txt'
const DEFI = /^[0-9a-f]{32}$/
// liste des outils trouvés dans une ligne : bornée (le serveur lit au plus 300 caractères)
const OUTILS_MAX = 200
// Spartan à partir duquel le serveur vérifie lui-même les rapports (lignes v2, clé liée au jeton)
const SPARTAN_PREUVE = [2, 10, 0]
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

/** Version de Spartan du pack (« fr.heraliz:spartan:2.10.0:client@jar »), ou null si le pack n'en cite aucune. */
function versionSpartan(modules, profondeur = 0) {
    if (!Array.isArray(modules) || profondeur > 20) return null
    for (const m of modules) {
        const id = String(m?.rawModule?.id ?? m?.id ?? '')
        const v = /^[^:]+:spartan:(\d+(?:\.\d+)*)(?:[:@].*)?$/i.exec(id)
        if (v) return v[1].split('.').map(Number)
        const dessous = versionSpartan(m?.subModules, profondeur + 1)
        if (dessous) return dessous
    }
    return null
}

/**
 * 2 : Spartan du pack assez récent pour vérifier lui-même les rapports (preuve) ; 1 : ancien mode. Dans le doute (pack
 * sans Spartan reconnu) : 1, qui marche avec tous les serveurs (un serveur plus ancien refuserait le jeton v2).
 */
function versionPreuve(modules) {
    const v = versionSpartan(modules)
    if (!v) return 1
    for (let i = 0; i < SPARTAN_PREUVE.length; i++) {
        const a = v[i] ?? 0
        if (a !== SPARTAN_PREUVE[i]) return a > SPARTAN_PREUVE[i] ? 2 : 1
    }
    return 2
}

class Gardien {
    /**
     * @param {object} o
     * @param {string} o.dossierJeu dossier du jeu (instance) : les fichiers vont dans <dossierJeu>/spartan/
     * @param {string[]} o.dossiersJars dossiers dont les jars ne doivent pas changer pendant la partie
     * @param {number} o.pid numéro du processus du jeu
     * @param {object} [o.cles] clés du lancement (nouvellesCles) ; sinon o.cle, clé HMAC hexadécimale (ancien mode)
     */
    constructor({ dossierJeu, dossiersJars = [], pid, cles = null, cle = null, lister = programmes, maintenant = Date.now }) {
        this.dossierJeu = dossierJeu
        this.dossiersJars = dossiersJars
        this.pid = pid
        this.cles = cles || { version: 1, cle }
        this.cle = this.cles.version === 2 ? null : Buffer.from(this.cles.cle, 'hex')
        this.lister = lister
        this.maintenant = maintenant
        this.n = 0
        this.etat = new Map() // chemin -> { taille, date, empreinte }
        this.modifies = new Set()
        this.minuteur = null
        this.arrete = false
    }

    /** Clé neuve pour un lancement en ancien mode : 32 octets en hexadécimal. */
    static nouvelleCle() {
        return crypto.randomBytes(32).toString('hex')
    }

    /**
     * Clés d'un lancement. Version 2 : paire Ed25519 ({ privee, publique }) ; la clé publique (base64url, 43 caractères)
     * part au jeu et dans l'engagement du jeton. Version 1 : clé HMAC donnée au jeu. « jeu » : ce que reçoit le jeu.
     */
    static nouvellesCles(version = 2) {
        if (version !== 2) {
            const cle = Gardien.nouvelleCle()
            return { version: 1, cle, jeu: cle }
        }
        const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519')
        const publique = publicKey.export({ format: 'der', type: 'spki' }).subarray(-32).toString('base64url')
        return { version: 2, privee: privateKey, publique, jeu: publique }
    }

    /**
     * Empreinte demandée au site (à la place de celle des mods) : lie la clé publique du Gardien au jeton de lancement.
     * Même calcul que GardienPreuve.engagement de Spartan : SHA-256 de « gardien-v2|<empreinte des mods>|<clé publique> ».
     */
    static engagement(empreinteMods, publique) {
        return crypto.createHash('sha256').update('gardien-v2|' + empreinteMods + '|' + publique, 'utf8').digest('hex')
    }

    get fichier() {
        return path.join(this.dossierJeu, 'spartan', FICHIER)
    }

    get fichierDefi() {
        return path.join(this.dossierJeu, 'spartan', FICHIER_DEFI)
    }

    /** Dernier défi du serveur écrit par le jeu, ou « - » (pas encore connecté, fichier absent ou abîmé). */
    lireDefi() {
        try {
            const d = fs.readFileSync(this.fichierDefi, 'utf8').trim()
            return DEFI.test(d) ? d : '-'
        } catch {
            return '-'
        }
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
        try { fs.rmSync(this.fichierDefi, { force: true }) } catch { /* sans importance */ }
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
        const valeurs = {
            n: ++this.n,
            t: this.maintenant(),
            pid: this.pid,
            lecture: liste ? 'ok' : 'impossible',
            outils,
            modifies: this.modifies.size
        }
        let ligne, signature
        if (this.cles.version === 2) {
            // défi lu au dernier moment (après la liste des programmes, qui peut prendre quelques secondes)
            ligne = Gardien.ligneV2({ ...valeurs, defi: this.lireDefi() })
            signature = crypto.sign(null, Buffer.from(ligne, 'utf8'), this.cles.privee).toString('base64url')
        } else {
            ligne = Gardien.ligne(valeurs)
            signature = crypto.createHmac('sha256', this.cle).update(ligne).digest('hex')
        }
        const contenu = ligne + '\n' + signature + '\n'
        fs.mkdirSync(path.dirname(this.fichier), { recursive: true })
        // écrit à côté puis renommé : le jeu ne lit jamais un fichier à moitié écrit
        const tmp = this.fichier + '.tmp'
        fs.writeFileSync(tmp, contenu)
        fs.renameSync(tmp, this.fichier)
        return ligne
    }

    /** Outils trouvés, en texte : noms sûrs seulement, liste bornée. */
    static outilsTexte(outils) {
        let o = ''
        for (const x of outils || []) {
            if (!NOM_SUR.test(x) || o.length + x.length + 1 > OUTILS_MAX) continue
            o = o ? o + ',' + x : x
        }
        return o
    }

    /**
     * Ligne v1 (ancien mode, signée HMAC avec la clé donnée au jeu) :
     * v1|n|t|pid|lecture|outil,outil|nombre de jars changés
     */
    static ligne({ n, t, pid, lecture, outils, modifies }) {
        return ['v1', n, t, pid, lecture, Gardien.outilsTexte(outils), modifies].join('|')
    }

    /**
     * Ligne v2 (signée Ed25519, vérifiée par le serveur : GardienPreuve.java, même format, à changer des deux côtés) :
     * v2|n|t|pid|défi du serveur (ou -)|lecture|outil,outil|nombre de jars changés
     */
    static ligneV2({ n, t, pid, defi, lecture, outils, modifies }) {
        return ['v2', n, t, pid, DEFI.test(String(defi)) ? defi : '-', lecture, Gardien.outilsTexte(outils), modifies].join('|')
    }
}

module.exports = { Gardien, outilsTrouves, champsCsv, versionPreuve, OUTILS, TITRES }
