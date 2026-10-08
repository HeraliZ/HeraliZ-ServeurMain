'use strict'

/**
 * Spartan (anticheat HeraliZ, mods/spartan du dépôt) côté launcher.
 *
 * 1. Le jeu est lancé SANS porte d'entrée pour les injecteurs :
 *    - protections() : options Java ajoutées à chaque lancement. -XX:+DisableAttachMechanism empêche tout programme
 *      de s'attacher au jeu déjà lancé. Placée APRÈS les options du joueur : la dernière l'emporte. Java 17 : surtout
 *      pas -XX:-EnableDynamicAgentLoading (Java 21+, le jeu refuserait de démarrer).
 *    - optionsSures(options) : retire des options Java du joueur tout ce qui sert à injecter du code au lancement.
 * 2. Jeton de lancement (le serveur peut refuser ceux qui ne passent pas par ce launcher) :
 *    - jetonLancement(pseudo, dossierMods) : jeton signé, valable DUREE_JETON_S, lié au pseudo et à l'empreinte du
 *      dossier mods, passé au jeu par -Dspartan.launchToken=… Format identique à
 *      mods/spartan/src/commun/java/com/Spartan/integrity/LaunchToken.java (docs : mods/spartan/docs/LAUNCHER.md).
 *    - La clé n'est JAMAIS dans le dépôt : outils/spartan-cle.js l'écrit dans spartan-cle.json au moment de fabriquer
 *      le launcher, depuis la variable SPARTAN_LAUNCHER_SECRET (même valeur que le secret GitHub du serveur). Sans
 *      clé : pas de jeton, le serveur ne fait que signaler.
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

/** Durée de validité d'un jeton : une soirée de jeu (le serveur ne le vérifie qu'à la connexion). */
const DUREE_JETON_S = 6 * 3600
const ARG_JETON = '-Dspartan.launchToken='

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

function b64url(buf) {
    return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Jeton v1 : base64url(payload) "." base64url(HMAC-SHA256(clé, payload)). */
function creerJeton(cle, pseudo, expiration, empreinte) {
    const alea = crypto.randomBytes(12).toString('hex')
    const payload = Buffer.from(['v1', b64url(Buffer.from(pseudo, 'utf8')), String(expiration), empreinte, alea].join('|'), 'utf8')
    const signature = crypto.createHmac('sha256', Buffer.from(cle, 'utf8')).update(payload).digest()
    return b64url(payload) + '.' + b64url(signature)
}

/** Clé fabriquée avec le launcher (spartan-cle.json), ou null. */
function cle() {
    try {
        const c = JSON.parse(fs.readFileSync(path.join(__dirname, 'spartan-cle.json'), 'utf8')).cle
        return typeof c === 'string' && c.length >= 32 ? c : null
    } catch (e) {
        return null
    }
}

/** Option Java du jeton pour ce lancement, ou null si le launcher n'a pas de clé. */
function argumentJeton(pseudo, dossierMods, maintenant = Math.floor(Date.now() / 1000), cleImposee = undefined) {
    const k = cleImposee === undefined ? cle() : cleImposee
    if (!k || typeof pseudo !== 'string' || !pseudo.trim()) {
        return null
    }
    return ARG_JETON + creerJeton(k, pseudo.trim(), maintenant + DUREE_JETON_S, empreinteMods(dossierMods))
}

/** Arguments à écrire dans le journal du launcher : le jeton est masqué. */
function masquerJeton(args) {
    return args.map(a => typeof a === 'string' && a.startsWith(ARG_JETON) ? ARG_JETON + '**********' : a)
}

module.exports = { protections, optionsSures, argumentJeton, masquerJeton, empreinteMods, DUREE_JETON_S }
