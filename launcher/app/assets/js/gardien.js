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
 *    fichier, empreinte recalculée seulement si l'une change ; depuis le 19/11, chaque jar du pack est aussi comparé une
 *    fois à son empreinte SIGNÉE juste après le lancement (un jar remplacé entre le contrôle et le lancement compte) ;
 *  - (19/11) les bibliothèques chargées DANS le processus du jeu, lues de l'extérieur (Windows : PowerShell ou tasklist ;
 *    Linux : /proc) : seules celles d'agent Java ou de débogueur de Java (instrument, jdwp…) partent, en « dll.nom » parmi
 *    les outils ; un jeu modifié ne peut pas les cacher au launcher ;
 *  - le jeu lui-même toujours là (même numéro de processus).
 * (20/11, launcher 1.18.1) Les programmes qu'il lance pour ça (tasklist, PowerShell, ps) tournent en priorité basse, un
 * PowerShell à la fois : le jeu garde le processeur (prioriteBasse).
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
const os = require('os')
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
// liste des programmes (rapide : moins d'une seconde d'habitude) ; titres des fenêtres (PowerShell, plus lent au démarrage)
const COMMANDE_MS = 20_000
const TITRES_MS = 20_000
// injecteurs et éditeurs de mémoire : le launcher refuse de lancer le jeu tant qu'ils sont ouverts, et le serveur expulse
// un joueur chez qui le Gardien les voit (Spartan, section [injection], liste « outils »). Les autres outils de la liste
// (débogueurs, décompilateurs, gestionnaires de processus) restent des alertes : un développeur honnête peut les avoir.
const OUTILS_INTERDITS = ['cheatengine', 'cheat engine', 'extremeinjector', 'extreme injector', 'xenos', 'ghinjector',
    'gh injector', 'dllinjector', 'kdmapper', 'artmoney', 'squalr']
const NOMS_OUTILS = {
    cheatengine: 'Cheat Engine', 'cheat engine': 'Cheat Engine', extremeinjector: 'Extreme Injector',
    'extreme injector': 'Extreme Injector', xenos: 'Xenos', ghinjector: 'GH Injector', 'gh injector': 'GH Injector',
    dllinjector: 'DLL Injector', kdmapper: 'kdmapper', artmoney: 'ArtMoney', squalr: 'Squalr'
}
// nom d'outil écrit dans le fichier : lettres, chiffres, espace, point, tiret (comme la liste)
const NOM_SUR = /^[a-z0-9 ._-]{1,32}$/
// Bibliothèques de Java qu'un jeu lancé par ce launcher ne charge JAMAIS (19/11, lues par Spartan 2.13.0) : agent Java
// (instrument), débogueur (jdwp et ses liaisons), attache à un autre Java, contrôle à distance (JMX), lecture de la
// mémoire de Java. Le Gardien lit, depuis l'extérieur du jeu, les bibliothèques chargées dans SON processus : un jeu
// modifié ne peut pas les lui cacher. Seules celles-ci partent (« dll.instrument »), jamais la liste du joueur.
const BIBLIOTHEQUES_JAVA = /^(?:lib)?(instrument|jdwp|dt_socket|dt_shmem|attach|management_agent|sawindbg|saproc)\.(?:dll|so|dylib)$/i
// la liste des bibliothèques du jeu est relue un rapport sur trois (et au premier) : une bibliothèque chargée le reste
const TOURS_MODULES = 3
const MODULES_PS = '[Console]::OutputEncoding=[Text.Encoding]::UTF8; try { (Get-Process -Id {pid} -ErrorAction Stop).Modules'
    + ' | ForEach-Object { $_.ModuleName } } catch { }'
// chemins comparés comme le système de fichiers (insensible à la casse sous Windows et Mac)
const comparable = chemin => {
    const p = path.resolve(chemin)
    return process.platform === 'win32' || process.platform === 'darwin' ? p.toLowerCase() : p
}

/**
 * Programme lancé par le Gardien (tasklist, PowerShell, ps) : priorité « en dessous de la normale » (audit des images par
 * seconde du 20/11). Sous Windows, chaque rapport démarre un PowerShell (titres des fenêtres), et un sur trois un second
 * (bibliothèques du jeu) : quelques centaines de millisecondes de calcul chacun, plus d'une seconde sur un petit PC, toutes
 * les 10 à 20 s pendant la partie. Plus bas que le jeu, ils ne lui prennent plus le processeur quand il en a besoin
 * (le système leur laisse le temps libre ; jamais affamés : Windows relance un programme qui attend trop). Jamais
 * bloquant : un programme déjà fini ou un refus du système ne change rien.
 */
function prioriteBasse(pid, regler = os.setPriority) {
    if (!Number.isInteger(pid) || pid <= 0) return false
    try {
        regler(pid, os.constants.priority.PRIORITY_BELOW_NORMAL)
        return true
    } catch {
        return false
    }
}

function execFile(cmd, args, options, lancer = execFileCb, baisser = prioriteBasse) {
    return new Promise((resolve, reject) => {
        const enfant = lancer(cmd, args, { maxBuffer: 16 * 1024 * 1024, ...options }, (err, stdout) => err ? reject(err) : resolve(String(stdout)))
        baisser(enfant?.pid)
    })
}

/**
 * Morceaux de la liste trouvés dans un nom d'exécutable ou un titre (minuscules), sans doublon, triés.
 * marquerTitres (rapports du Gardien, 03/11) : un outil vu SEULEMENT dans le titre d'une fenêtre est rendu « outil.titre »
 * (un fichier « Cheat Engine.txt » ouvert dans un éditeur n'est pas Cheat Engine) : le serveur l'alerte sans expulser.
 */
function outilsTrouves(noms, titres = [], marquerTitres = false) {
    const trouves = new Set()
    for (const n of noms) {
        const nom = String(n || '').toLowerCase().replace(/\.exe$/, '')
        for (const o of OUTILS) if (nom.includes(o)) trouves.add(o)
    }
    const parNom = new Set(trouves)
    for (const t of titres) {
        const titre = String(t && typeof t === 'object' ? t.titre : t || '').toLowerCase()
        if (t && typeof t === 'object' && SANS_TITRE.test(String(t.nom || ''))) continue
        if (!titre || titre === 'n/a') continue
        TITRES.forEach((o, i) => { if (TITRES_RE[i].test(titre)) trouves.add(o) })
    }
    return [...trouves].map(o => marquerTitres && !parNom.has(o) && !parNom.has(o.replace(/ /g, '')) ? o + '.titre' : o)
        .filter(o => NOM_SUR.test(o)).sort()
}

/** Lecture d'une ligne CSV de tasklist ("a","b",...) : champs entre guillemets, "" pour un guillemet. */
function champsCsv(ligne) {
    const out = []
    const re = /"((?:[^"]|"")*)"/g
    let m
    while ((m = re.exec(ligne)) !== null) out.push(m[1].replace(/""/g, '"'))
    return out
}

// PowerShell : titres des fenêtres (nom du programme, tabulation, titre), en UTF-8. Seulement les programmes qui ont une
// fenêtre ; rien d'autre n'est lu.
const TITRES_PS = '[Console]::OutputEncoding=[Text.Encoding]::UTF8;'
    + 'Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { $_.ProcessName + [char]9 + $_.MainWindowTitle }'
const NOMS_PS = '[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-Process | ForEach-Object { $_.ProcessName + \'.exe\' }'

function powershell(commande, delai, exec) {
    return exec('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', commande], { timeout: delai, windowsHide: true })
}

/** Noms des exécutables lus dans la sortie CSV de « tasklist /FO CSV /NH » (première colonne). */
function nomsTasklist(out) {
    const noms = []
    for (const ligne of String(out).split(/\r?\n/)) {
        const c = champsCsv(ligne)
        if (c.length >= 2 && c[0]) noms.push(c[0])
    }
    return noms
}

/** Titres lus dans la sortie de TITRES_PS : [{ nom, titre }]. */
function titresPowershell(out) {
    const titres = []
    for (const ligne of String(out).split(/\r?\n/)) {
        const i = ligne.indexOf('\t')
        if (i <= 0) continue
        titres.push({ nom: ligne.slice(0, i).trim(), titre: ligne.slice(i + 1).trim() })
    }
    return titres
}

/**
 * Programmes ouverts : { noms, titres }. null si même la liste des noms est impossible (le jeu ne doit jamais en pâtir).
 *
 * 03/11 (retour du propriétaire : « le Gardien n'arrive jamais à lister les programmes ») : avant, une seule commande,
 * « tasklist /V », qui va chercher le titre de la fenêtre ET le compte Windows de CHAQUE programme. Elle est très lente
 * (souvent plus de 12 s quand le PC est chargé, comme au lancement du jeu, et parfois bloquée par un programme suspendu
 * ou une recherche de compte) : coupée au bout de 12 s, la liste entière était perdue et le rapport disait « lecture
 * impossible ». Maintenant : la liste des noms d'abord, par « tasklist » sans /V (moins d'une seconde), avec PowerShell
 * en secours ; les titres à part, par PowerShell, en même temps ; des titres manquants n'empêchent pas la lecture.
 */
async function programmes(plateforme = process.platform, exec = execFile) {
    if (plateforme !== 'win32') {
        try {
            const out = await exec('ps', ['-A', '-o', 'comm='], { timeout: COMMANDE_MS })
            return { noms: out.split(/\r?\n/).map(l => path.basename(l.trim())).filter(Boolean), titres: [] }
        } catch {
            return null
        }
    }
    const lireNoms = async () => {
        try {
            const noms = nomsTasklist(await exec('tasklist', ['/FO', 'CSV', '/NH'], { timeout: COMMANDE_MS, windowsHide: true }))
            if (noms.length) return noms
        } catch { /* secours ci-dessous */ }
        try {
            const noms = String(await powershell(NOMS_PS, COMMANDE_MS, exec)).split(/\r?\n/).map(l => l.trim()).filter(Boolean)
            return noms.length ? noms : null
        } catch {
            return null
        }
    }
    const lireTitres = async () => {
        try {
            return titresPowershell(await powershell(TITRES_PS, TITRES_MS, exec))
        } catch {
            return []
        }
    }
    const [noms, titres] = await Promise.all([lireNoms(), lireTitres()])
    return noms ? { noms, titres } : null
}

/** Bibliothèques de la liste BIBLIOTHEQUES_JAVA parmi des noms de fichiers (« instrument », « jdwp »…), sans doublon, triées. */
function bibliothequesJava(noms) {
    const out = new Set()
    for (const n of noms || []) {
        const m = BIBLIOTHEQUES_JAVA.exec(path.posix.basename(String(n ?? '').trim().replace(/\\/g, '/')))
        if (m) out.add(m[1].toLowerCase())
    }
    return [...out].sort()
}

/** Noms des bibliothèques lus dans /proc/<pid>/maps (Linux) : dernier élément de chaque chemin .so, sans doublon. */
function nomsMaps(texte) {
    const noms = new Set()
    for (const ligne of String(texte).split('\n')) {
        const i = ligne.indexOf('/')
        if (i < 0) continue
        const chemin = ligne.slice(i).replace(/ \(deleted\)$/, '').trim()
        if (/\.so(\.|$)|\.dylib$/.test(chemin)) noms.add(path.posix.basename(chemin))
    }
    return [...noms]
}

/** Noms des bibliothèques lus dans la sortie de « tasklist /M /FO CSV /NH » (troisième colonne, séparée par des virgules). */
function nomsTasklistModules(out) {
    const noms = []
    for (const ligne of String(out).split(/\r?\n/)) {
        const c = champsCsv(ligne)
        if (c.length < 3) continue
        for (const n of c[2].split(',')) {
            const nom = n.trim()
            if (nom && nom !== 'N/A') noms.push(nom)
        }
    }
    return noms
}

/**
 * Bibliothèques chargées dans le processus du jeu, lues DE L'EXTÉRIEUR du jeu (19/11) : Windows, PowerShell (ce seul
 * processus), tasklist en secours ; Linux, /proc/<pid>/maps. null si la lecture est impossible (Mac, jeu fermé, accès
 * refusé) : rien n'est dit.
 */
async function modulesJeu(pid, plateforme = process.platform, exec = execFile, lire = f => fs.readFileSync(f, 'utf8')) {
    if (!Number.isInteger(pid) || pid <= 0) return null
    if (plateforme === 'linux') {
        try { return nomsMaps(lire(`/proc/${pid}/maps`)) } catch { return null }
    }
    if (plateforme !== 'win32') return null
    try {
        const noms = String(await powershell(MODULES_PS.replace('{pid}', String(pid)), COMMANDE_MS, exec)).split(/\r?\n/)
            .map(l => l.trim()).filter(Boolean)
        if (noms.length) return noms
    } catch { /* secours ci-dessous */ }
    try {
        const noms = nomsTasklistModules(await exec('tasklist', ['/M', '/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH'],
            { timeout: COMMANDE_MS, windowsHide: true }))
        return noms.length ? noms : null
    } catch {
        return null
    }
}

/** Empreinte SHA-256 d'un fichier, lu par morceaux (jamais tout en mémoire, et sans bloquer le launcher). */
function sha256Flux(fichier) {
    return new Promise((resolve, reject) => {
        const h = crypto.createHash('sha256')
        fs.createReadStream(fichier).on('data', d => h.update(d)).on('end', () => resolve(h.digest('hex'))).on('error', reject)
    })
}

/** Injecteurs et éditeurs de mémoire ouverts (noms lisibles, sans doublon), [] si aucun ou liste impossible. */
async function outilsInterdits(lister = programmes) {
    const liste = await lister().catch(() => null)
    if (!liste) return []
    // noms d'exécutables seulement : un titre de fenêtre (fichier, page) ne bloque jamais le lancement
    const vus = outilsTrouves(liste.noms, []).filter(o => OUTILS_INTERDITS.includes(o))
    return [...new Set(vus.map(o => NOMS_OUTILS[o] || o))]
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
     * @param {function} [o.modules] (pid) -> noms des bibliothèques chargées dans le jeu (modulesJeu) ; sans : pas lues
     * @param {Map} [o.attendues] empreintes signées du pack (empreintesAttendues) : un jar surveillé qui n'y correspond pas,
     *   même avant le premier relevé, compte comme changé
     */
    constructor({ dossierJeu, dossiersJars = [], pid, cles = null, cle = null, lister = programmes, maintenant = Date.now,
        modules = null, attendues = null }) {
        this.dossierJeu = dossierJeu
        this.dossiersJars = dossiersJars
        this.pid = pid
        this.cles = cles || { version: 1, cle }
        this.cle = this.cles.version === 2 ? null : Buffer.from(this.cles.cle, 'hex')
        this.lister = lister
        this.maintenant = maintenant
        this.modules = typeof modules === 'function' ? modules : null
        this.attendues = attendues instanceof Map ? attendues : new Map()
        this.bibliotheques = new Set() // bibliothèques de Java vues dans le jeu (une fois chargée, elle reste dite)
        this.tours = 0
        this.n = 0
        this.etat = new Map() // chemin -> { taille, date, empreinte, chemin }
        this.modifies = new Set()
        this.minuteur = null
        this.arrete = false
    }

    /**
     * Empreintes signées du pack (empreintesDuPack du service : [{ chemin, sha256 }]) rangées pour le Gardien : chemin
     * comparable -> sha256 en minuscules. Les entrées mal formées sont ignorées.
     */
    static empreintesAttendues(liste) {
        const out = new Map()
        for (const e of Array.isArray(liste) ? liste : []) {
            if (e && typeof e.chemin === 'string' && /^[a-f0-9]{64}$/i.test(String(e.sha256))) {
                out.set(comparable(e.chemin), String(e.sha256).toLowerCase())
            }
        }
        return out
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

    /**
     * Relevé de départ des jars (avant le premier rapport), puis premier rapport et suivants. 19/11 : les jars du pack sont
     * aussi comparés, en fond, à leur empreinte SIGNÉE : un jar remplacé entre le contrôle d'avant la partie et le
     * lancement du jeu (puis remis en place) ne passe plus inaperçu.
     */
    demarrer() {
        this.releverJars(true)
        this.verifierPack().catch(erreur => console.error('[HeraliZ/gardien]', erreur?.code || erreur?.message))
        this.tour()
        return this
    }

    /**
     * Chaque jar surveillé qui a une empreinte signée dans le pack est relu une fois : différent = changé (compté dans le
     * prochain rapport). Égal : son empreinte devient la référence des relevés suivants. Fichier illisible : rien.
     */
    async verifierPack() {
        for (const [cle, e] of [...this.etat]) {
            if (this.arrete) return
            const attendu = e?.chemin ? this.attendues.get(comparable(e.chemin)) : null
            if (!attendu) continue
            let lu
            try { lu = await sha256Flux(e.chemin) } catch { continue }
            if (lu !== attendu) {
                this.modifies.add(path.basename(e.chemin))
            } else if (this.etat.get(cle) === e && e.empreinte == null) {
                e.empreinte = lu
            }
        }
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
                    this.etat.set(cle, { taille: st.size, date: st.mtimeMs, empreinte: null, chemin: p })
                    continue
                }
                if (avant.taille === st.size && avant.date === st.mtimeMs) continue
                let e
                try { e = sha256Fichier(p) } catch { continue }
                // pas d'empreinte de départ (relevé rapide au lancement) : taille ou date changée = changé
                if (avant.empreinte == null || avant.empreinte !== e) this.modifies.add(path.basename(p))
                this.etat.set(cle, { taille: st.size, date: st.mtimeMs, empreinte: e, chemin: p })
            }
        }
        if (!depart) {
            for (const cle of this.etat.keys()) if (!vus.has(cle)) this.modifies.add(path.basename(cle.split('|').pop()))
        }
    }

    async rapport() {
        // 19/11 : bibliothèques du processus du jeu relues un rapport sur trois (et au premier) ; une lecture impossible ne
        // dit rien. 20/11 : APRÈS la liste des programmes, plus en même temps (sous Windows, deux PowerShell à la fois
        // prenaient deux cœurs au jeu sur un petit PC ; le rapport part une seconde plus tard, rien d'autre ne change)
        const relire = this.modules !== null && this.tours++ % TOURS_MODULES === 0
        const liste = await this.lister()
        const modules = relire ? await Promise.resolve().then(() => this.modules(this.pid)).catch(() => null) : null
        for (const b of bibliothequesJava(modules)) this.bibliotheques.add(b)
        // les bibliothèques d'abord : la liste des outils est bornée (outilsTexte)
        const outils = [...this.bibliotheques].map(b => 'dll.' + b)
            .concat(liste ? outilsTrouves(liste.noms, liste.titres, true) : [])
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

module.exports = {
    Gardien, outilsTrouves, champsCsv, versionPreuve, programmes, outilsInterdits, nomsTasklist, titresPowershell,
    modulesJeu, bibliothequesJava, nomsMaps, nomsTasklistModules, execFile, prioriteBasse, OUTILS, TITRES, OUTILS_INTERDITS
}
