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
 *    valable 6 h, lié au pseudo du compte et à l'empreinte du dossier mods (empreinteMods), passé au jeu par
 *    -Dspartan.launchToken=… Le launcher ne contient aucune clé : il ne peut pas fabriquer de jeton lui-même.
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

const ARG_JETON = '-Dspartan.launchToken='
// jeton v1 rendu par le site : base64url(contenu) "." base64url(HMAC-SHA256), rien d'autre (il finit en option Java)
const JETON = /^[A-Za-z0-9_-]{20,2000}\.[A-Za-z0-9_-]{43}$/

/** Options Java ajoutées à chaque lancement (copie). */
function protections() {
    return [...PROTECTIONS]
}

/** Options Java du joueur sans celles qui permettent d'injecter du code (ni de fournir un faux jeton). */
function optionsSures(options) {
    if (!Array.isArray(options)) {
        return []
    }
    return options.filter(o => typeof o === 'string' && !DANGEREUSES.some(re => re.test(o.trim())))
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

/** Option Java du jeton rendu par le site pour ce lancement, ou null (pas de jeton, ou jeton mal formé). */
function argumentJeton(jeton) {
    return typeof jeton === 'string' && JETON.test(jeton) ? ARG_JETON + jeton : null
}

/** Arguments à écrire dans le journal du launcher : le jeton est masqué. */
function masquerJeton(args) {
    return args.map(a => typeof a === 'string' && a.startsWith(ARG_JETON) ? ARG_JETON + '**********' : a)
}

module.exports = { protections, optionsSures, argumentJeton, masquerJeton, empreinteMods }
