const { app, BrowserWindow, ipcMain, shell, dialog, clipboard, Menu } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const { pathToFileURL } = require('node:url')
const config = require('./app/heraliz.json')
// Ici, seulement Electron, Node et heraliz.json : le reste du launcher (security, launcher-service…) est chargé dans
// demarrerLauncher(), après la mise à jour. Un fichier manquant ou cassé n'empêche plus le launcher de se réparer
// (launcher 1.14.0 à 1.15.0 : gardien.js absent, le launcher plantait avant de pouvoir se mettre à jour).

// Test runs never touch the player's real profile or game installation.
if (!app.isPackaged && process.env.HERALIZ_TEST_DATA) app.setPath('userData', process.env.HERALIZ_TEST_DATA)
// Same id as electron-builder's appId: Windows shows the HeraliZ icon and name in the taskbar, even in dev.
if (process.platform === 'win32') app.setAppUserModelId('fr.heraliz.launcher')
// Linux : launcher en AppImage (variable posée par l'image elle-même) ; sinon paquet .deb
const appImage = process.platform === 'linux' && Boolean(process.env.APPIMAGE)
// Nouveau launcher à télécharger à la main (Mac, .deb) : fichiers publiés par le workflow « Launcher HeraliZ »
const FICHIER_LAUNCHER = { win32: 'HeraliZ-Launcher-Setup.exe', darwin: 'HeraliZ-Launcher.dmg', linux: appImage ? 'HeraliZ-Launcher.AppImage' : 'HeraliZ-Launcher.deb' }
const pageUrl = pathToFileURL(path.join(__dirname, 'app', 'index.html')).href
// la page n'a pas demandé la mise à jour du launcher ce temps après son ouverture (page cassée) : il la cherche lui-même
const GARDE_FOU_PAGE_MS = 20000
// fenêtre du launcher : jamais avant que toutes ses commandes (IPC) soient branchées (pret), sinon ses boutons ne
// répondraient pas (« No handler registered for 'heraliz:state' »)
let win
let pret = false
// petite fenêtre de réparation (démarrage raté), sans aucune commande
let fenetreSecours = null
let finSecours = false
let service
let securite
// mise à jour du launcher, créée avant tout le reste ; ses états vont à la page et à la réparation
let miseAJour = null
let majDemandee = false
let dernierEtatMaj = null
const ecouteursMaj = new Set()

function handle(name, action) {
    ipcMain.handle(`heraliz:${name}`, async (event, ...args) => {
        if (!win || !securite.isTrustedFrame(event, win.webContents, pageUrl)) throw new Error('Accès refusé.')
        try {
            return { ok: true, data: await action(...args) }
        } catch (error) {
            console.error(`[HeraliZ/${name}]`, error)
            const reponse = { ok: false, error: securite.sansChemins(error.message) || 'Une erreur est survenue. Réessaie dans un instant.' }
            // erreurs par champ d'un formulaire (création de compte) : seulement des textes
            if (error.champs && typeof error.champs === 'object') {
                reponse.champs = Object.fromEntries(Object.entries(error.champs).filter(([, v]) => typeof v === 'string').slice(0, 8))
            }
            // accès refusé à cause du Discord du compte : le statut, pour ouvrir le bon panneau
            if (typeof error.discord === 'string' && /^[a-z_]{1,20}$/.test(error.discord)) reponse.discord = error.discord
            return reponse
        }
    })
}

function createWindow() {
    // sans toutes ses commandes, jamais de fenêtre : elle n'aurait rien derrière (boutons sans effet)
    if (!pret) return
    win = new BrowserWindow({
        title: 'HeraliZ Launcher', width: 1180, height: 780,
        minWidth: 940, minHeight: 680, frame: false, show: false,
        backgroundColor: '#06070a',
        icon: path.join(__dirname, 'app', 'assets', 'images', process.platform === 'win32' ? 'heraliz-icon.ico' : 'heraliz-emblem.png'),
        webPreferences: {
            preload: path.join(__dirname, 'app', 'preload.js'),
            nodeIntegration: false, contextIsolation: true, sandbox: true,
            webSecurity: true, webviewTag: false
        }
    })
    win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
    win.webContents.on('will-navigate', event => event.preventDefault())
    win.webContents.on('will-attach-webview', event => event.preventDefault())
    win.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false))
    win.webContents.session.setPermissionCheckHandler(() => false)
    win.removeMenu()
    win.once('ready-to-show', () => win.show())
    win.on('close', async event => {
        // Partie en cours : le jeu est lancé à part (détaché), il reste ouvert quand le launcher se ferme. Rien à demander.
        if (!service?.busy || service.progress?.phase === 'running') return
        event.preventDefault()
        const { response } = await dialog.showMessageBox(win, {
            type: 'question', title: 'Préparation en cours',
            message: 'Le jeu est en cours de préparation.',
            detail: 'Fermer le launcher arrête la préparation : elle reprendra au prochain clic sur JOUER. Une fois Minecraft lancé, tu pourras fermer le launcher sans fermer le jeu.',
            buttons: ['Rester', 'Fermer'], defaultId: 0, cancelId: 0
        })
        if (response === 1) { service.dispose(); win.destroy(); app.quit() }
    })
    win.on('closed', () => { win = null })
    win.loadURL(pageUrl)
    // Garde-fou : la page demande la mise à jour du launcher dès son ouverture. Si elle ne l'a pas fait (page cassée,
    // fichier manquant), le launcher la cherche lui-même : une version réparée s'installe quand même.
    if (!majDemandee) {
        setTimeout(() => {
            if (majDemandee || !miseAJour) return
            majDemandee = true
            console.error('[HeraliZ] la page n’a pas demandé la mise à jour du launcher : recherche lancée par le launcher')
            Promise.resolve(miseAJour.verifier()).catch(() => {})
        }, GARDE_FOU_PAGE_MS).unref?.() // unref : ce délai seul ne garde pas Node en vie (tests)
    }
}

// Mise à jour du launcher lui-même : seulement la version installée (pas en développement). Automatique sous Windows et
// en AppImage ; Mac (application non signée par Apple) et .deb : le joueur est prévenu (mise-a-jour.js). Créée avant le
// reste du launcher et sans lui : elle doit marcher même quand il ne démarre pas (reparer).
function creerMiseAJour() {
    const { MiseAJour } = require('./app/assets/js/mise-a-jour')
    const majActive = app.isPackaged && ['win32', 'darwin', 'linux'].includes(process.platform)
    const fichierMaj = path.join(app.getPath('userData'), 'mise-a-jour.json')
    return new MiseAJour({
        updater: majActive ? choisirUpdater() : null,
        actif: majActive,
        auto: process.platform === 'win32' || appImage,
        envoyer: etat => {
            dernierEtatMaj = etat
            if (win && !win.isDestroyed()) win.webContents.send('heraliz:maj', etat)
            for (const ecouter of ecouteursMaj) ecouter(etat)
        },
        occupe: () => Boolean(service?.busy),
        memoire: {
            lire: () => JSON.parse(fs.readFileSync(fichierMaj, 'utf8')),
            ecrire: valeur => fs.writeFileSync(fichierMaj, JSON.stringify(valeur))
        },
        // signature de la mise à jour : clé publique embarquée + adresse des fichiers de version
        // (https://<site>/telecharger/). Sans clé réglée, aucune vérification (comportement d'avant).
        clePublique: config.distributionPublicKey || null,
        majBaseUrl: config.siteUrl ? config.siteUrl.replace(/\/+$/, '') + '/telecharger/' : null
    })
}

// Le launcher lui-même : service, commandes de la page, puis fenêtre. Une erreur ici passe par reparer().
function demarrerLauncher() {
    securite = require('./app/assets/js/security')
    const { httpsUrl } = securite
    const LauncherService = require('./app/assets/js/launcher-service')
    service = new LauncherService(config, progress => {
        // fenêtre fermée pendant la partie : le launcher restait pour le Gardien ; partie finie, il se ferme
        if (pret && !win && !service?.gardienActif() && progress.phase !== 'running') { app.quit(); return }
        if (win && !win.isDestroyed()) {
            win.webContents.send('heraliz:progress', progress)
            win.setProgressBar(progress.busy && progress.phase !== 'running' ? (progress.percent == null ? 2 : progress.percent / 100) : -1)
        }
    })
    // compte banni vu sur ce PC : fichiers du jeu encore à retirer la dernière fois (jeu ouvert, fichier verrouillé)
    service.reprendreEffacement()
    handle('maj-launcher', async () => {
        majDemandee = true
        const maj = miseAJour ? await miseAJour.verifier() : { disponible: false }
        // téléchargement commencé ou fini avant que la page n'écoute (garde-fou, fenêtre rouverte pendant une partie) :
        // son dernier état lui est renvoyé, sinon elle l'attendrait sans fin
        if (dernierEtatMaj) setImmediate(() => { if (win && !win.isDestroyed()) win.webContents.send('heraliz:maj', dernierEtatMaj) })
        return maj
    })
    handle('state', () => service.getState())
    handle('profile', value => service.saveProfile(value))
    handle('connexion', value => service.connexion(value))
    handle('compte', () => service.verifierCompte())
    handle('etat-discord', () => service.etatDiscord())
    handle('etat-comptes', () => service.etatComptes())
    handle('inscription', value => service.inscription(value))
    handle('a2f-preparer', () => service.a2fPreparer())
    handle('a2f-activer', value => service.a2fActiver(value))
    // codes de secours de la double authentification vers le presse-papiers (seulement des codes, rien d'autre)
    handle('copier-codes', codes => {
        if (typeof codes !== 'string' || codes.length > 1000 || !/^[\w-]+(\n[\w-]+)*$/.test(codes)) throw new Error('Copie impossible.')
        clipboard.writeText(codes)
    })
    handle('logout', () => service.logout())
    handle('settings', value => service.saveSettings(value))
    handle('status', () => service.getServerStatus())
    handle('accord-capture', oui => service.accordCapture(oui))
    handle('launch', () => service.launch())
    handle('link', async name => {
        // Aucun lien vers les fichiers du serveur ou du jeu (choix du propriétaire : ne pas faciliter le vol des jars).
        // Pages du site HeraliZ (Azuriom) : inscription, mot de passe oublié, compte, conditions (adresse donnée par le
        // site, gardée seulement si elle est sur le site lui-même).
        const site = config.siteUrl ? httpsUrl(config.siteUrl) : null
        const pageSite = chemin => site && new URL(chemin, site).href
        const links = {
            discord: config.discordUrl, inscription: pageSite('/user/register'), motdepasse: pageSite('/user/password/reset'),
            compte: pageSite('/profile'), boutique: pageSite('/shop'), votes: pageSite('/vote'), conditions: service.etatSite?.conditions || null,
            confidentialite: pageSite('/confidentialite'), cgu: pageSite('/cgu'),
            telecharger: FICHIER_LAUNCHER[process.platform] ? pageSite(`/telecharger/${FICHIER_LAUNCHER[process.platform]}`) : null,
            // panneau Discord : adresses données par le site pour ce compte (jamais par la page du launcher)
            'discord-lier': service.lienDiscord('lier'), 'discord-invitation': service.lienDiscord('invitation')
        }
        if (!Object.hasOwn(links, name) || !links[name]) throw new Error('Ce lien n’est pas encore configuré.')
        const url = httpsUrl(links[name])
        const versDiscord = name === 'discord' || name === 'discord-invitation'
        if (versDiscord && !['discord.gg', 'discord.com'].includes(url.hostname)) throw new Error('Lien Discord invalide.')
        if (!versDiscord && url.host !== site.host) throw new Error('Lien du site invalide.')
        await shell.openExternal(url.href)
    })
    handle('window', action => {
        if (action === 'minimize') win.minimize()
        else if (action === 'maximize') win.isMaximized() ? win.unmaximize() : win.maximize()
        else if (action === 'close') win.close()
        else throw new Error('Action inconnue.')
    })
    // Mac : menu de l'application réduit à l'essentiel (Quitter, copier-coller dans les champs, fenêtre), sans les
    // outils de développement du menu par défaut d'Electron. Windows et Linux : pas de menu (removeMenu).
    if (process.platform === 'darwin') Menu.setApplicationMenu(Menu.buildFromTemplate([{ role: 'appMenu' }, { role: 'editMenu' }, { role: 'windowMenu' }]))
    // tout est branché : la fenêtre peut s'ouvrir
    pret = true
    createWindow()
}

// Petite fenêtre de réparation : un texte et une barre, page sans script ni commande (rien n'y attend de réponse),
// dessinée par secours.js sans les polices ni les images du launcher (elles peuvent manquer).
let dernierAffichage = null
function afficherSecours(secours, texte, pourcent = null) {
    try {
        if (dernierAffichage && dernierAffichage.texte === texte && dernierAffichage.pourcent === pourcent) return
        dernierAffichage = { texte, pourcent }
        if (!fenetreSecours || fenetreSecours.isDestroyed()) {
            const icone = path.join(__dirname, 'app', 'assets', 'images', process.platform === 'win32' ? 'heraliz-icon.ico' : 'heraliz-emblem.png')
            fenetreSecours = new BrowserWindow({
                title: 'HeraliZ Launcher', width: 480, height: 200, show: false,
                resizable: false, maximizable: false, fullscreenable: false, backgroundColor: '#06070a',
                ...(fs.existsSync(icone) ? { icon: icone } : {}),
                webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true, webSecurity: true, webviewTag: false, javascript: false }
            })
            fenetreSecours.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
            fenetreSecours.webContents.on('will-navigate', event => event.preventDefault())
            fenetreSecours.removeMenu()
            fenetreSecours.once('ready-to-show', () => fenetreSecours?.show())
            // le joueur la ferme pendant la réparation : il abandonne, le launcher se ferme
            fenetreSecours.on('closed', () => { fenetreSecours = null; if (!finSecours) app.quit() })
        }
        fenetreSecours.setProgressBar(Number.isFinite(pourcent) ? pourcent / 100 : 2)
        fenetreSecours.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(secours.pageSecours(texte, pourcent))).catch(() => {})
    } catch (erreur) {
        console.error('[HeraliZ] fenêtre de réparation impossible', erreur)
    }
}

// Démarrage raté (fichier manquant ou cassé) : le launcher ne s'arrête pas tout de suite. Il cherche une version plus
// récente ; sous Windows et en AppImage, elle se télécharge et s'installe seule, puis le launcher redémarre réparé.
// Sinon (pas de mise à jour, Mac, .deb, échec), il demande au joueur de le retélécharger, puis se ferme.
async function reparer(erreur) {
    const secours = require('./app/assets/js/secours')
    const cause = secours.causeCourte(erreur)
    afficherSecours(secours, 'Le launcher n’a pas pu démarrer : il se répare. Recherche d’une mise à jour sur heraliz.fr…')
    const issue = miseAJour
        ? await secours.attendreReparation({
            verifier: () => miseAJour.verifier(),
            abonner: ecouter => { ecouteursMaj.add(ecouter); return () => ecouteursMaj.delete(ecouter) },
            progression: (pourcent, version) => {
                // une image tous les 2 % au plus (la page est redessinée à chaque fois)
                const affiche = Math.min(100, Math.floor(Number(pourcent) / 2) * 2)
                afficherSecours(secours, `Téléchargement de la version réparée${version ? ` (${version})` : ''} : ${affiche} %`, affiche)
            }
        })
        : { resultat: 'aucune', version: null }
    console.log('[HeraliZ] réparation du launcher :', issue.resultat, issue.version || '')
    if (issue.resultat === 'installation') {
        afficherSecours(secours, `Installation de la version ${issue.version || 'réparée'} : le launcher redémarre tout seul…`, 100)
        // MiseAJour lance l'installeur et ferme le launcher ; s'il ne se passe rien, on n'attend pas sans fin
        await new Promise(resolve => setTimeout(resolve, 90000).unref?.())
    }
    finSecours = true
    const { message, detail } = secours.messageFinal(issue, cause)
    const lien = secours.lienTelechargement(config.siteUrl, FICHIER_LAUNCHER[process.platform])
    const options = {
        type: 'error', title: 'HeraliZ Launcher', message, detail, noLink: true,
        buttons: lien ? ['Télécharger le launcher', 'Fermer'] : ['Fermer'], defaultId: 0, cancelId: lien ? 1 : 0
    }
    const parent = fenetreSecours && !fenetreSecours.isDestroyed() ? fenetreSecours : null
    const { response } = await (parent ? dialog.showMessageBox(parent, options) : dialog.showMessageBox(options))
    if (lien && response === 0) await shell.openExternal(lien).catch(() => {})
    app.exit(1)
}

async function demarrer() {
    // 1. la mise à jour du launcher d'abord, à part du reste : même si la suite plante, il peut encore se remplacer
    //    par une version réparée (les 1.14.0 à 1.15.0 créaient leur mise à jour après le chargement qui plantait)
    try { miseAJour = creerMiseAJour() } catch (erreur) { console.error('[HeraliZ] mise à jour du launcher indisponible', erreur) }
    // 2. le launcher lui-même
    try {
        demarrerLauncher()
    } catch (erreur) {
        console.error('[HeraliZ] démarrage impossible', erreur)
        await reparer(erreur)
    }
}

if (!app.requestSingleInstanceLock()) app.quit()
else {
    // Second lancement : la fenêtre déjà ouverte revient devant (celle de la réparation s'il y en a une). Jamais de
    // fenêtre du launcher sans ses commandes : tant que le démarrage n'est pas fini (ou s'il a raté), rien de plus.
    app.on('second-instance', () => {
        const fenetre = win && !win.isDestroyed() ? win : fenetreSecours && !fenetreSecours.isDestroyed() ? fenetreSecours : null
        if (fenetre) { if (fenetre.isMinimized()) fenetre.restore(); fenetre.show(); fenetre.focus() }
        else if (app.isReady() && pret) createWindow()
    })
    app.whenReady().then(demarrer).catch(erreur => {
        // dernier recours : même la réparation a planté. Le dire au joueur au lieu de rester ouvert sans fenêtre.
        console.error('[HeraliZ] démarrage impossible', erreur)
        dialog.showErrorBox('HeraliZ Launcher', 'Le launcher n’a pas pu démarrer.\n\nRetélécharge-le sur heraliz.fr/telecharger puis réinstalle-le par-dessus : ton compte et ton jeu sont gardés.')
        app.exit(1)
    })
}

// Linux : AppImage ou paquet .deb choisi d'après la façon dont le launcher tourne (pas d'après le fichier « package-type »
// qu'electron-builder peut laisser dans les deux). Windows et Mac : celui qu'electron-updater choisit.
function choisirUpdater() {
    const majs = require('electron-updater')
    if (process.platform !== 'linux') return majs.autoUpdater
    return appImage ? new majs.AppImageUpdater() : new majs.DebUpdater()
}
// Pendant une partie, le launcher reste ouvert sans fenêtre : le Gardien (gardien.js) surveille le jeu jusqu'à sa
// fermeture, puis le launcher se ferme seul (rappel de progression ci-dessus). Relancer le launcher rouvre la fenêtre.
// Réparation en cours (pas prêt) : c'est la fenêtre de réparation qui décide (reparer).
app.on('window-all-closed', () => { if (pret && !service?.gardienActif()) app.quit() })
app.on('before-quit', () => service?.dispose())
