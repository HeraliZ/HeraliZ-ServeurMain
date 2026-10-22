const { app, BrowserWindow, ipcMain, shell, dialog, clipboard, Menu } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const { pathToFileURL } = require('node:url')
const { isTrustedFrame, httpsUrl, sansChemins } = require('./app/assets/js/security')
const { MiseAJour } = require('./app/assets/js/mise-a-jour')
const config = require('./app/heraliz.json')

// Test runs never touch the player's real profile or game installation.
if (!app.isPackaged && process.env.HERALIZ_TEST_DATA) app.setPath('userData', process.env.HERALIZ_TEST_DATA)
// Same id as electron-builder's appId: Windows shows the HeraliZ icon and name in the taskbar, even in dev.
if (process.platform === 'win32') app.setAppUserModelId('fr.heraliz.launcher')
// Linux : launcher en AppImage (variable posée par l'image elle-même) ; sinon paquet .deb
const appImage = process.platform === 'linux' && Boolean(process.env.APPIMAGE)
// Nouveau launcher à télécharger à la main (Mac, .deb) : fichiers publiés par le workflow « Launcher HeraliZ »
const FICHIER_LAUNCHER = { win32: 'HeraliZ-Launcher-Setup.exe', darwin: 'HeraliZ-Launcher.dmg', linux: appImage ? 'HeraliZ-Launcher.AppImage' : 'HeraliZ-Launcher.deb' }
const pageUrl = pathToFileURL(path.join(__dirname, 'app', 'index.html')).href
let win
let service

function handle(name, action) {
    ipcMain.handle(`heraliz:${name}`, async (event, ...args) => {
        if (!win || !isTrustedFrame(event, win.webContents, pageUrl)) throw new Error('Accès refusé.')
        try {
            return { ok: true, data: await action(...args) }
        } catch (error) {
            console.error(`[HeraliZ/${name}]`, error)
            const reponse = { ok: false, error: sansChemins(error.message) || 'Une erreur est survenue. Réessaie dans un instant.' }
            // erreurs par champ d'un formulaire (création de compte) : seulement des textes
            if (error.champs && typeof error.champs === 'object') {
                reponse.champs = Object.fromEntries(Object.entries(error.champs).filter(([, v]) => typeof v === 'string').slice(0, 8))
            }
            return reponse
        }
    })
}

function createWindow() {
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
}

if (!app.requestSingleInstanceLock()) app.quit()
else {
    app.on('second-instance', () => { if (win) { win.restore(); win.focus() } })
    app.whenReady().then(() => {
        const LauncherService = require('./app/assets/js/launcher-service')
        service = new LauncherService(config, progress => {
            if (win && !win.isDestroyed()) {
                win.webContents.send('heraliz:progress', progress)
                win.setProgressBar(progress.busy && progress.phase !== 'running' ? (progress.percent == null ? 2 : progress.percent / 100) : -1)
            }
        })
        // mise à jour du launcher lui-même : seulement la version installée (pas en développement). Automatique sous
        // Windows et en AppImage ; Mac (application non signée par Apple) et .deb : le joueur est prévenu (mise-a-jour.js)
        const majActive = app.isPackaged && ['win32', 'darwin', 'linux'].includes(process.platform)
        const fichierMaj = path.join(app.getPath('userData'), 'mise-a-jour.json')
        const miseAJour = new MiseAJour({
            updater: majActive ? choisirUpdater() : null,
            actif: majActive,
            auto: process.platform === 'win32' || appImage,
            envoyer: etat => { if (win && !win.isDestroyed()) win.webContents.send('heraliz:maj', etat) },
            occupe: () => Boolean(service?.busy),
            memoire: {
                lire: () => JSON.parse(fs.readFileSync(fichierMaj, 'utf8')),
                ecrire: valeur => fs.writeFileSync(fichierMaj, JSON.stringify(valeur))
            }
        })
        handle('maj-launcher', () => miseAJour.verifier())
        handle('state', () => service.getState())
        handle('profile', value => service.saveProfile(value))
        handle('connexion', value => service.connexion(value))
        handle('compte', () => service.verifierCompte())
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
                compte: pageSite('/profile'), conditions: service.etatSite?.conditions || null,
                telecharger: FICHIER_LAUNCHER[process.platform] ? pageSite(`/telecharger/${FICHIER_LAUNCHER[process.platform]}`) : null
            }
            if (!Object.hasOwn(links, name) || !links[name]) throw new Error('Ce lien n’est pas encore configuré.')
            const url = httpsUrl(links[name])
            if (name === 'discord' && !['discord.gg', 'discord.com'].includes(url.hostname)) throw new Error('Lien Discord invalide.')
            if (name !== 'discord' && url.host !== site.host) throw new Error('Lien du site invalide.')
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
        createWindow()
    })
}

// Linux : AppImage ou paquet .deb choisi d'après la façon dont le launcher tourne (pas d'après le fichier « package-type »
// qu'electron-builder peut laisser dans les deux). Windows et Mac : celui qu'electron-updater choisit.
function choisirUpdater() {
    const majs = require('electron-updater')
    if (process.platform !== 'linux') return majs.autoUpdater
    return appImage ? new majs.AppImageUpdater() : new majs.DebUpdater()
}
app.on('window-all-closed', () => app.quit())
app.on('before-quit', () => service?.dispose())
