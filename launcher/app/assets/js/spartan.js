'use strict'

/**
 * Spartan (anticheat HeraliZ, mods/spartan du dépôt) côté launcher.
 *
 * 1. Le jeu est lancé SANS porte d'entrée pour les injecteurs :
 *    - protections() : options Java ajoutées à chaque lancement. -XX:+DisableAttachMechanism empêche tout programme
 *      de s'attacher au jeu déjà lancé. Placée APRÈS les options du joueur : la dernière l'emporte. Java 17 : surtout
 *      pas -XX:-EnableDynamicAgentLoading (Java 21+, le jeu refuserait de démarrer).
 *    - optionsSures(options) : retire des options Java du joueur tout ce qui sert à injecter du code au lancement.
 * 2. Jeton de lancement (le serveur peut refuser ceux qui ne passent pas par ce launcher) : signé par le SITE pour le
 *    compte connecté (mode B de mods/spartan/docs/LAUNCHER.md ; /api/heraliz/jeton-jeu, site/extension/heraliz),
 *    valable 6 h, lié au pseudo du compte et à l'empreinte du dossier mods (empreinteMods), passé au jeu par la variable
 *    d'environnement SPARTAN_LAUNCH_TOKEN (environnementJeu), jamais sur la ligne de commande, que d'autres programmes
 *    et les antivirus lisent. Le launcher ne contient aucune clé : il ne peut pas fabriquer de jeton lui-même.
 * 3. Gardien (gardien.js) : clé de signature de ses rapports, donnée au seul jeu par SPARTAN_GARDIEN_CLE.
 * 4. environnementJeu() retire aussi les variables que Java lit au démarrage (JAVA_TOOL_OPTIONS…) : un programme de
 *    triche pourrait y glisser un -javaagent sans toucher aux options du launcher. Même chose sur Linux et Mac pour les
 *    variables qui font charger une bibliothèque native dans tout programme lancé (LD_PRELOAD, LD_AUDIT,
 *    DYLD_INSERT_LIBRARIES) : rien n'est injecté dans le jeu par ce biais (une surcouche comme MangoHud non plus).
 */

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

const PROTECTIONS = ['-XX:+DisableAttachMechanism']

const DANGEREUSES = [
    /^-javaagent:/i,
    /^-agentlib:/i,
    /^-agentpath:/i,
    /^-Xbootclasspath/i,
    /^-Djava\.system\.class\.loader=/i,
    /^-Djdk\.attach\.allowAttachSelf/i,
    /^-XX:\+EnableDynamicAgentLoading/i,
    /^-XX:\+StartAttachListener/i,
    /^-XX:-DisableAttachMechanism/i,
    /^-Xverify:none/i,
    /^-noverify$/i,
    /^-Dspartan\./i
]

// options Java permises au joueur (liste blanche, 24/10) : mémoire, ramasse-miettes, réglages de Forge. Tout le reste est
// retiré : une liste noire laissait passer « @fichier » (Java y lit d'autres options, -javaagent compris), --patch-module,
// -XX:VMOptionsFile=, -Djava.library.path=...
const PERMISES = [
    /^-Xm[snx]\d+[kKmMgG]?$/,
    /^-Xss\d+[kKmMgG]?$/,
    /^-XX:[+-][A-Za-z0-9]+$/,
    /^-XX:(G1[A-Za-z0-9]*|MaxGCPauseMillis|ParallelGCThreads|ConcGCThreads|SurvivorRatio|MaxTenuringThreshold|InitiatingHeapOccupancyPercent|NewRatio|NewSize|MaxNewSize|MetaspaceSize|MaxMetaspaceSize|ReservedCodeCacheSize|SoftRefLRUPolicyMSPerMB|CICompilerCount|TieredStopAtLevel|ShenandoahGCHeuristics|ZCollectionInterval|ZUncommitDelay)=[A-Za-z0-9.%_-]+$/,
    /^-D(fml|forge|sun\.java2d)\.[A-Za-z0-9._-]+=[A-Za-z0-9._-]*$/,
    /^-Dfile\.encoding=[A-Za-z0-9_-]+$/,
    /^-Djava\.net\.preferIPv4Stack=(true|false)$/
]

const ARG_JETON = '-Dspartan.launchToken='
const VAR_JETON = 'SPARTAN_LAUNCH_TOKEN'
// accord du joueur pour les captures d'écran (fenêtre avant de jouer, accordCapture.js) : posé seulement par le launcher
const VAR_CAPTURE = 'SPARTAN_CAPTURE_CONSENT'
// clé du Gardien (gardien.js) pour ce lancement : 32 octets en hexadécimal, posée seulement par le launcher
const VAR_GARDIEN = 'SPARTAN_GARDIEN_CLE'
const CLE_GARDIEN = /^[0-9a-f]{64}$/
// jeton v1 rendu par le site : base64url(contenu) "." base64url(HMAC-SHA256), rien d'autre
const JETON = /^[A-Za-z0-9_-]{20,2000}\.[A-Za-z0-9_-]{43}$/
// variables d'environnement lues par Java au démarrage (une option -javaagent y suffit), bibliothèques natives imposées
// à tout programme lancé (Linux, Mac), et tout jeton hérité
const VARIABLES_DANGEREUSES = ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'LD_PRELOAD', 'LD_AUDIT',
    'DYLD_INSERT_LIBRARIES', VAR_JETON, VAR_CAPTURE, VAR_GARDIEN]

/** Options Java ajoutées à chaque lancement (copie). */
function protections() {
    return [...PROTECTIONS]
}

/** Options Java du joueur sans celles qui permettent d'injecter du code (ni de fournir un faux jeton). */
function optionsSures(options) {
    if (!Array.isArray(options)) {
        return []
    }
    return options.filter(o => typeof o === 'string' && PERMISES.some(re => re.test(o.trim()))
        && !DANGEREUSES.some(re => re.test(o.trim())) && !/BytecodeVerification/i.test(o))
}

// ------------------------------------------------------------------ jeton de lancement
function sha256Hex(data) {
    return crypto.createHash('sha256').update(data).digest('hex')
}

function byCodeUnit(a, b) {
    return a < b ? -1 : a > b ? 1 : 0
}

function sha256Dir(dir) {
    const lines = []
    ;(function walk(current) {
        for (const n of fs.readdirSync(current).sort(byCodeUnit)) {
            const p = path.join(current, n)
            const st = fs.statSync(p)
            if (st.isDirectory()) {
                walk(p)
            } else if (st.isFile()) {
                lines.push(path.relative(dir, p).split(path.sep).join('/') + ':' + sha256Hex(fs.readFileSync(p)))
            }
        }
    })(dir)
    lines.sort(byCodeUnit)
    return sha256Hex(lines.join('\n'))
}

/** Empreinte du dossier mods, même calcul que le mod (LaunchToken.modsHash) : dossier absent ou vide compris. */
function empreinteMods(dossierMods) {
    const lines = []
    if (fs.existsSync(dossierMods)) {
        for (const n of fs.readdirSync(dossierMods)) {
            if (!n.toLowerCase().endsWith('.jar')) {
                continue
            }
            const p = path.join(dossierMods, n)
            const st = fs.statSync(p)
            if (st.isFile()) {
                lines.push(n.toLowerCase() + ':' + sha256Hex(fs.readFileSync(p)))
            } else if (st.isDirectory()) {
                lines.push(n.toLowerCase() + ':dir:' + sha256Dir(p))
            }
        }
    }
    lines.sort(byCodeUnit)
    return sha256Hex(lines.join('\n'))
}

/**
 * Environnement du jeu : celui du launcher, sans les variables qui permettent d'injecter du code dans Java (ni un jeton
 * venu d'ailleurs), plus le jeton rendu par le site pour ce lancement s'il est bien formé. Les noms sont comparés sans
 * tenir compte des majuscules (Windows). base n'est jamais modifié.
 */
function environnementJeu(jeton, base = process.env, accordCapture = false, cleGardien = null) {
    const env = {}
    for (const [nom, valeur] of Object.entries(base || {})) {
        if (!VARIABLES_DANGEREUSES.includes(nom.toUpperCase())) env[nom] = valeur
    }
    if (typeof jeton === 'string' && JETON.test(jeton)) env[VAR_JETON] = jeton
    if (accordCapture === true) env[VAR_CAPTURE] = '1'
    if (typeof cleGardien === 'string' && CLE_GARDIEN.test(cleGardien)) env[VAR_GARDIEN] = cleGardien
    return env
}

/** Arguments à écrire dans le journal du launcher : un jeton qui y serait quand même est masqué. */
function masquerJeton(args) {
    return args.map(a => typeof a === 'string' && a.startsWith(ARG_JETON) ? ARG_JETON + '**********' : a)
}

module.exports = { protections, optionsSures, environnementJeu, masquerJeton, empreinteMods }
