'use strict'

// Anti-robot de la création de compte depuis le launcher (audit du 02/11, point 41). Quand le site a son anti-robot
// Turnstile réglé (« robot » de /api/heraliz/etat), le launcher ouvre la page /heraliz/robot du site dans une petite
// fenêtre à part : sans accès au launcher (ni Node, ni passerelle), session en mémoire seulement, aucune autorisation,
// aucune autre fenêtre, et seulement des pages du site lui-même (le cadre de Cloudflare est dans la page). Turnstile
// passé, la page part vers /heraliz/robot/fait#<réponse> : le launcher prend la réponse dans l'adresse, sans charger la
// page, ferme la fenêtre et la joint à la création du compte (le site la vérifie auprès de Cloudflare). La page du
// launcher, elle, ne charge toujours aucun contenu distant.

const PAGE = '/heraliz/robot'
const FIN = '/heraliz/robot/fait'
const DELAI_MS = 5 * 60 * 1000
const REPONSE = /^[A-Za-z0-9._-]{20,4096}$/

// Réponse de Turnstile si « adresse » est la page de fin du site avec la réponse après le #, sinon null.
function reponseDeFin(adresse, site) {
    let url
    try { url = new URL(adresse) } catch { return null }
    if (url.origin !== site.origin || url.pathname !== FIN) return null
    let reponse = ''
    try { reponse = decodeURIComponent(url.hash.slice(1)) } catch { return null }
    return REPONSE.test(reponse) ? reponse : null
}

/**
 * Ouvre la vérification et rend la réponse de Turnstile ; erreur si la fenêtre est fermée, si le site ne répond pas ou
 * après 5 minutes. BrowserWindow est celui d'Electron (passé par index.js : ce fichier se teste sans Electron).
 */
function verifierHumain({ BrowserWindow, parent = null, siteUrl, delaiMs = DELAI_MS }) {
    const site = new URL(siteUrl)
    if (site.protocol !== 'https:') return Promise.reject(new Error('Adresse du site invalide.'))
    return new Promise((resolve, reject) => {
        let fini = false
        const fenetre = new BrowserWindow({
            parent: parent || undefined, modal: Boolean(parent), width: 440, height: 560, resizable: false,
            minimizable: false, maximizable: false, fullscreenable: false, show: false, autoHideMenuBar: true,
            title: 'Vérification anti-robot', backgroundColor: '#06070a',
            webPreferences: {
                sandbox: true, contextIsolation: true, nodeIntegration: false, webSecurity: true, webviewTag: false,
                spellcheck: false, partition: 'heraliz-robot'
            }
        })
        const finir = (erreur, reponse) => {
            if (fini) return
            fini = true
            clearTimeout(minuteur)
            if (!fenetre.isDestroyed()) fenetre.destroy()
            if (erreur) reject(erreur)
            else resolve(reponse)
        }
        const minuteur = setTimeout(() => finir(new Error('Vérification anti-robot trop longue : clique de nouveau sur « Créer mon compte ».')), delaiMs)
        const pages = fenetre.webContents
        // Electron s'annonce dans le nom du navigateur : retiré, Turnstile voit un Chrome ordinaire
        if (typeof pages.getUserAgent === 'function') pages.setUserAgent(pages.getUserAgent().replace(/ (Electron|heraliz-launcher)\/\S+/g, ''))
        pages.setWindowOpenHandler(() => ({ action: 'deny' }))
        pages.on('will-attach-webview', event => event.preventDefault())
        pages.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false))
        pages.session.setPermissionCheckHandler(() => false)
        const navigation = (event, ancienneAdresse) => {
            // cadres de la page (celui de Cloudflare) : libres ; seule la page elle-même est tenue au site
            if (event?.isMainFrame === false) return
            const adresse = typeof event?.url === 'string' ? event.url : ancienneAdresse
            const reponse = reponseDeFin(adresse, site)
            if (reponse) {
                event.preventDefault()
                finir(null, reponse)
                return
            }
            let url = null
            try { url = new URL(adresse) } catch { /* adresse illisible : refusée */ }
            if (!url || url.origin !== site.origin) event.preventDefault()
        }
        pages.on('will-navigate', navigation)
        pages.on('will-redirect', navigation)
        pages.on('did-fail-load', (_event, code, _description, _adresse, cadrePrincipal) => {
            // -3 : chargement arrêté (navigation refusée ci-dessus) ; seul le cadre principal compte
            if (cadrePrincipal !== false && code !== -3) finir(new Error('Le site HeraliZ ne répond pas. Vérifie ta connexion internet et réessaie.'))
        })
        fenetre.removeMenu?.()
        fenetre.once('ready-to-show', () => { if (!fenetre.isDestroyed()) fenetre.show() })
        fenetre.on('closed', () => finir(new Error('Vérification anti-robot annulée : elle est nécessaire pour créer ton compte depuis le launcher.')))
        Promise.resolve(fenetre.loadURL(new URL(PAGE, site).href)).catch(() => { /* erreur rendue par did-fail-load */ })
    })
}

module.exports = { verifierHumain, reponseDeFin, PAGE, FIN }
