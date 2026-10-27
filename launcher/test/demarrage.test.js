// Démarrage du launcher (index.js), avec un faux Electron : jamais de fenêtre du launcher sans ses commandes, et un
// launcher qui ne démarre pas (fichier manquant, comme les 1.14.0 à 1.15.0) cherche d'abord une version réparée.
const test = require('node:test')
const assert = require('node:assert/strict')
const Module = require('node:module')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { EventEmitter } = require('node:events')

const INDEX = require.resolve('../index.js')
const dossier = fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-demarrage-'))
test.after(() => fs.rmSync(dossier, { recursive: true, force: true }))
const attendre = ms => new Promise(resolve => setTimeout(resolve, ms))

// Faux updater d'electron-updater : une version plus récente en ligne, téléchargée sans erreur
function fauxUpdater() {
    const u = new EventEmitter()
    u.installations = []
    u.checkForUpdates = async () => ({ isUpdateAvailable: true, updateInfo: { version: '9.9.9' } })
    u.downloadUpdate = async () => { u.emit('download-progress', { percent: 50 }); u.emit('download-progress', { percent: 100 }) }
    u.quitAndInstall = (silencieux, relancer) => u.installations.push({ silencieux, relancer })
    return u
}

// Charge index.js avec un faux Electron. service : 'ok' (faux service) ou 'casse' (module manquant).
async function demarrer({ service, packaged = false }) {
    const journal = { fenetres: [], commandes: [], commandesALaFenetre: null, dialogues: [], sortie: null, quitter: 0, ecouteurs: {} }
    const updater = fauxUpdater()
    class BrowserWindow {
        constructor(options) {
            this.options = options
            this.urls = []
            this.ecouteurs = {}
            this.devant = 0
            this.webContents = { setWindowOpenHandler() {}, on() {}, send() {}, session: { setPermissionRequestHandler() {}, setPermissionCheckHandler() {} } }
            if (options.webPreferences?.preload) journal.commandesALaFenetre = [...journal.commandes]
            journal.fenetres.push(this)
        }
        removeMenu() {}
        once(nom, f) { this.ecouteurs[nom] = f }
        on(nom, f) { this.ecouteurs[nom] = f }
        loadURL(url) { this.urls.push(url); return Promise.resolve() }
        isDestroyed() { return false }
        isMinimized() { return false }
        setProgressBar() {}
        restore() {}
        show() {}
        focus() { this.devant++ }
    }
    const electron = {
        app: {
            isPackaged: packaged, setPath() {}, setAppUserModelId() {}, requestSingleInstanceLock: () => true,
            on: (nom, f) => { journal.ecouteurs[nom] = f }, whenReady: () => Promise.resolve(), isReady: () => true,
            getPath: () => dossier, getVersion: () => '1.15.2', quit: () => { journal.quitter++ }, exit: code => { journal.sortie = code }
        },
        BrowserWindow,
        ipcMain: { handle: nom => { assert.equal(journal.commandes.includes(nom), false, `${nom} branché deux fois`); journal.commandes.push(nom) } },
        dialog: {
            showMessageBox: async (...args) => { journal.dialogues.push(args.at(-1)); return { response: 1 } },
            showErrorBox: (titre, texte) => journal.dialogues.push({ message: texte })
        },
        shell: { openExternal: async () => {} }, clipboard: { writeText() {} },
        Menu: { setApplicationMenu() {}, buildFromTemplate: () => ({}) }
    }
    class FauxService {
        constructor() { this.busy = false }
        reprendreEffacement() {}
        gardienActif() { return false }
        dispose() {}
    }
    const chargerOrigine = Module._load
    Module._load = function (nom, parent, isMain) {
        if (nom === 'electron') return electron
        if (nom === 'electron-updater') return { autoUpdater: updater, AppImageUpdater: function () { return updater }, DebUpdater: function () { return updater } }
        // jamais de vraie requête vers heraliz.fr pendant les tests : config sans clé de signature
        if (nom === './app/heraliz.json') return { serverId: 'heraliz-1.20.1', siteUrl: 'https://heraliz.fr', distributionPublicKey: null }
        if (nom.endsWith('/launcher-service')) {
            if (service === 'casse') throw new Error('Cannot find module \'C:\\Users\\Joueur\\app.asar\\app\\assets\\js\\gardien\'')
            return FauxService
        }
        return chargerOrigine.call(this, nom, parent, isMain)
    }
    const appImage = process.env.APPIMAGE
    if (process.platform === 'linux') process.env.APPIMAGE = '/tmp/HeraliZ.AppImage' // installation automatique, comme Windows
    // faux Electron gardé jusqu'à la fin du test : index.js charge la suite du launcher après whenReady()
    const remettre = () => {
        Module._load = chargerOrigine
        if (appImage === undefined) delete process.env.APPIMAGE
        else process.env.APPIMAGE = appImage
    }
    try {
        delete require.cache[INDEX]
        require(INDEX)
    } catch (erreur) { remettre(); throw erreur }
    await attendre(20)
    return { journal, updater, remettre, fenetresLauncher: () => journal.fenetres.filter(f => f.options.webPreferences?.preload) }
}

test('démarrage normal : la fenêtre ne s’ouvre qu’avec toutes ses commandes branchées', async t => {
    const { journal, fenetresLauncher, remettre } = await demarrer({ service: 'ok' })
    t.after(remettre)
    assert.equal(fenetresLauncher().length, 1)
    for (const nom of ['heraliz:state', 'heraliz:maj-launcher', 'heraliz:accord-capture', 'heraliz:window', 'heraliz:launch']) {
        assert.ok(journal.commandesALaFenetre.includes(nom), `${nom} pas encore branché à l’ouverture de la fenêtre`)
    }
    assert.deepEqual(journal.commandesALaFenetre, journal.commandes)
    // second lancement : la même fenêtre revient devant, aucune nouvelle
    journal.ecouteurs['second-instance']()
    assert.equal(fenetresLauncher().length, 1)
    assert.equal(fenetresLauncher()[0].devant, 1)
    assert.equal(journal.sortie, null)
})

test('démarrage raté sans mise à jour : aucune fenêtre du launcher, message clair, puis fermeture', async t => {
    const { journal, fenetresLauncher, remettre } = await demarrer({ service: 'casse' })
    t.after(remettre)
    assert.equal(fenetresLauncher().length, 0)
    // fenêtre de réparation : sans commandes ni script
    const reparation = journal.fenetres.find(f => !f.options.webPreferences?.preload)
    assert.ok(reparation)
    assert.equal(reparation.options.webPreferences.javascript, false)
    assert.match(decodeURIComponent(reparation.urls[0]), /il se répare/)
    // un second lancement ne crée jamais de fenêtre sans commandes
    journal.ecouteurs['second-instance']()
    assert.equal(fenetresLauncher().length, 0)
    assert.equal(journal.dialogues.length, 1)
    assert.match(journal.dialogues[0].message, /fichier gardien introuvable/)
    assert.equal(/Joueur/.test(journal.dialogues[0].message), false)
    assert.match(journal.dialogues[0].detail, /heraliz\.fr\/telecharger/)
    assert.equal(journal.sortie, 1)
})

test('démarrage raté avec une version réparée en ligne : elle se télécharge et s’installe seule', { skip: process.platform === 'darwin' && 'Mac : mise à jour seulement annoncée' }, async t => {
    const { journal, updater, fenetresLauncher, remettre } = await demarrer({ service: 'casse', packaged: true })
    t.after(remettre)
    // MiseAJour installe 1,2 s après la fin du téléchargement
    for (let i = 0; i < 40 && updater.installations.length === 0; i++) await attendre(100)
    assert.deepEqual(updater.installations, [{ silencieux: true, relancer: true }])
    assert.equal(fenetresLauncher().length, 0)
    const reparation = journal.fenetres.find(f => !f.options.webPreferences?.preload)
    assert.match(decodeURIComponent(reparation.urls.at(-1)), /Installation de la version 9\.9\.9/)
    // le launcher attend que l'installeur le ferme : ni message d'échec ni fermeture de son côté
    assert.equal(journal.dialogues.length, 0)
    assert.equal(journal.sortie, null)
})
