// Anti-robot de la création de compte (robot.js) : fenêtre isolée sur la page du site, réponse prise dans l'adresse de
// fin, rien d'autre. Testé avec une fausse fenêtre (sans Electron).
const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { verifierHumain, reponseDeFin } = require('../app/assets/js/robot')

const SITE = 'https://heraliz.fr'
const REPONSE = '0.AbCdEf-_ghIJ.klmn0123456789'

function fausseFenetre() {
    const fenetres = []
    class Fenetre extends EventEmitter {
        constructor(options) {
            super()
            this.options = options
            this.detruite = false
            this.permissions = null
            const pages = new EventEmitter()
            pages.setWindowOpenHandler = h => { pages.ouverture = h }
            pages.getUserAgent = () => 'Mozilla/5.0 Chrome/140.0 Electron/44.10.1 heraliz-launcher/1.16.0 Safari/537.36'
            pages.setUserAgent = ua => { pages.ua = ua }
            pages.session = {
                setPermissionRequestHandler: h => { this.permissions = h },
                setPermissionCheckHandler: h => { pages.verifPermission = h }
            }
            this.webContents = pages
            fenetres.push(this)
        }
        isDestroyed() { return this.detruite }
        destroy() { this.detruite = true; this.emit('closed') }
        removeMenu() {}
        loadURL(url) { this.adresse = url; return Promise.resolve() }
    }
    return { Fenetre, fenetres }
}

function naviguer(fenetre, adresse, evenement = 'will-navigate', principal = true) {
    const event = { url: adresse, isMainFrame: principal, empeche: false, preventDefault() { this.empeche = true } }
    fenetre.webContents.emit(evenement, event, adresse)
    return event.empeche
}

test('réponse de Turnstile prise dans l’adresse de fin du site seulement', () => {
    const site = new URL(SITE)
    assert.equal(reponseDeFin(`${SITE}/heraliz/robot/fait#${REPONSE}`, site), REPONSE)
    assert.equal(reponseDeFin(`https://pirate.example/heraliz/robot/fait#${REPONSE}`, site), null)
    assert.equal(reponseDeFin(`${SITE}/heraliz/robot/autre#${REPONSE}`, site), null)
    assert.equal(reponseDeFin(`${SITE}/heraliz/robot/fait#<script>`, site), null)
    assert.equal(reponseDeFin('pas une adresse', site), null)
})

test('fenêtre isolée sur la page du site ; réponse rendue et fenêtre fermée à la fin', async () => {
    const { Fenetre, fenetres } = fausseFenetre()
    const attente = verifierHumain({ BrowserWindow: Fenetre, siteUrl: SITE })
    const f = fenetres[0]
    assert.equal(f.adresse, `${SITE}/heraliz/robot`)
    const prefs = f.options.webPreferences
    assert.equal(prefs.sandbox, true)
    assert.equal(prefs.contextIsolation, true)
    assert.equal(prefs.nodeIntegration, false)
    assert.equal(prefs.preload, undefined, 'aucune passerelle vers le launcher')
    assert.ok(!String(prefs.partition).startsWith('persist:'), 'session en mémoire seulement')
    assert.deepEqual(f.webContents.ouverture(), { action: 'deny' })
    assert.equal(f.webContents.ua.includes('Electron'), false)
    let accord = null
    f.permissions(null, 'media', ok => { accord = ok })
    assert.equal(accord, false)
    assert.equal(naviguer(f, 'https://pirate.example/'), true, 'autre site : refusé')
    assert.equal(naviguer(f, 'https://challenges.cloudflare.com/x', 'will-redirect', false), false, 'cadre de Cloudflare : libre')
    assert.equal(naviguer(f, `${SITE}/heraliz/robot?r=1`), false, 'page du site : permise')
    assert.equal(naviguer(f, `${SITE}/heraliz/robot/fait#${REPONSE}`), true, 'fin : pas chargée')
    assert.equal(await attente, REPONSE)
    assert.equal(f.detruite, true)
})

test('fenêtre fermée par le joueur, site muet ou délai dépassé : erreur claire', async () => {
    const { Fenetre, fenetres } = fausseFenetre()
    const fermee = verifierHumain({ BrowserWindow: Fenetre, siteUrl: SITE })
    fenetres[0].destroy()
    await assert.rejects(fermee, /annulée/)
    const muet = verifierHumain({ BrowserWindow: Fenetre, siteUrl: SITE })
    fenetres[1].webContents.emit('did-fail-load', {}, -105, 'NAME_NOT_RESOLVED', SITE, true)
    await assert.rejects(muet, /ne répond pas/)
    const lent = verifierHumain({ BrowserWindow: Fenetre, siteUrl: SITE, delaiMs: 5 })
    await assert.rejects(lent, /trop longue/)
    assert.equal(fenetres[2].detruite, true)
    await assert.rejects(verifierHumain({ BrowserWindow: Fenetre, siteUrl: 'http://heraliz.fr' }), /invalide/)
})
