const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { MiseAJour } = require('../app/assets/js/mise-a-jour')

// Faux autoUpdater d'electron-updater : ce que le test prépare (version en ligne, téléchargement réussi ou non).
function fauxUpdater({ version = null, echec = false, jamais = false } = {}) {
    const u = new EventEmitter()
    u.verifications = 0
    u.installations = []
    u.checkForUpdates = async () => {
        u.verifications++
        if (jamais) return new Promise(() => {})
        return version ? { isUpdateAvailable: true, updateInfo: { version } } : { isUpdateAvailable: false, updateInfo: { version: '1.6.0' } }
    }
    u.downloadUpdate = async () => {
        u.emit('download-progress', { percent: 10.2 })
        u.emit('download-progress', { percent: 10.9 })
        u.emit('download-progress', { percent: 57 })
        if (echec) throw new Error('réseau coupé')
        u.emit('download-progress', { percent: 100 })
    }
    u.quitAndInstall = (silencieux, relancer) => u.installations.push({ silencieux, relancer })
    return u
}
const attendre = ms => new Promise(resolve => setTimeout(resolve, ms))
function fausseMemoire() {
    const m = { valeur: null }
    m.lire = () => m.valeur
    m.ecrire = v => { m.valeur = v }
    return m
}

test('mise à jour : rien en développement, rien si le launcher est à jour ou si le site ne répond pas', async () => {
    const envois = []
    const inactive = new MiseAJour({ updater: fauxUpdater({ version: '9.0.0' }), actif: false, envoyer: e => envois.push(e), occupe: () => false })
    assert.deepEqual(await inactive.verifier(), { disponible: false })
    const aJour = fauxUpdater()
    assert.deepEqual(await new MiseAJour({ updater: aJour, actif: true, envoyer: e => envois.push(e), occupe: () => false }).verifier(), { disponible: false })
    const lent = new MiseAJour({ updater: fauxUpdater({ jamais: true }), actif: true, envoyer: e => envois.push(e), occupe: () => false, delaiMs: 20 })
    assert.deepEqual(await lent.verifier(), { disponible: false })
    assert.deepEqual(envois, [])
    // réglages de l'updater : téléchargement seulement quand on le demande, jamais de retour en arrière
    assert.equal(aJour.autoDownload, false)
    assert.equal(aJour.allowDowngrade, false)
    assert.equal(aJour.disableDifferentialDownload, true)
})

test('mise à jour disponible : téléchargée avec sa progression, puis installée sans question et relancée', async () => {
    const envois = []
    const updater = fauxUpdater({ version: '1.7.0' })
    const maj = new MiseAJour({ updater, actif: true, envoyer: e => envois.push(e), occupe: () => false, delaiInstallationMs: 0 })
    assert.deepEqual(await maj.verifier(), { disponible: true, version: '1.7.0' })
    assert.deepEqual(await maj.verifier(), { disponible: true, version: '1.7.0' })
    assert.equal(updater.verifications, 1, 'une seule vérification par démarrage')
    await attendre(10)
    assert.deepEqual(envois, [
        { etat: 'telechargement', pourcent: 10 }, { etat: 'telechargement', pourcent: 57 },
        { etat: 'telechargement', pourcent: 100 }, { etat: 'installation' }
    ])
    assert.deepEqual(updater.installations, [{ silencieux: true, relancer: true }])
})

test('téléchargement raté : le launcher continue ; partie en cours : installation à la fermeture', async () => {
    const envois = []
    const rate = fauxUpdater({ version: '1.7.0', echec: true })
    await new MiseAJour({ updater: rate, actif: true, envoyer: e => envois.push(e), occupe: () => false, delaiInstallationMs: 0 }).verifier()
    await attendre(10)
    assert.deepEqual(envois.at(-1), { etat: 'erreur' })
    assert.deepEqual(rate.installations, [])

    const enJeu = fauxUpdater({ version: '1.7.0' })
    const etats = []
    await new MiseAJour({ updater: enJeu, actif: true, envoyer: e => etats.push(e), occupe: () => true, delaiInstallationMs: 0 }).verifier()
    await attendre(10)
    assert.deepEqual(etats.at(-1), { etat: 'plus-tard' })
    assert.deepEqual(enJeu.installations, [])
    assert.equal(enJeu.autoInstallOnAppQuit, true)
})

test('installation ratée : au plus 2 essais par version, puis le joueur est prévenu (pas de redémarrage en boucle)', async () => {
    const memoire = fausseMemoire()
    const demarrer = async () => {
        const envois = []
        const updater = fauxUpdater({ version: '1.7.0' })
        const r = await new MiseAJour({ updater, actif: true, envoyer: e => envois.push(e), occupe: () => false, memoire, delaiInstallationMs: 0 }).verifier()
        await attendre(10)
        return { r, installations: updater.installations.length }
    }
    // le launcher redémarre toujours en 1.6.0 : l'installation de la 1.7.0 n'a pas pris
    assert.deepEqual(await demarrer(), { r: { disponible: true, version: '1.7.0' }, installations: 1 })
    assert.deepEqual(await demarrer(), { r: { disponible: true, version: '1.7.0' }, installations: 1 })
    assert.deepEqual(await demarrer(), { r: { disponible: false, bloquee: true, version: '1.7.0' }, installations: 0 })
    // une version plus récente repart de zéro ; une mémoire illisible aussi
    memoire.valeur = { version: '1.7.0', fois: 2, depuis: Date.now() }
    const autre = fauxUpdater({ version: '1.8.0' })
    assert.equal((await new MiseAJour({ updater: autre, actif: true, envoyer: () => {}, occupe: () => false, memoire, delaiInstallationMs: 0 }).verifier()).disponible, true)
    const casse = { lire: () => { throw new SyntaxError('JSON') }, ecrire: () => { throw new Error('disque plein') } }
    assert.equal((await new MiseAJour({ updater: fauxUpdater({ version: '1.7.0' }), actif: true, envoyer: () => {}, occupe: () => false, memoire: casse, delaiInstallationMs: 0 }).verifier()).disponible, true)
})

test('Mac et .deb : pas d’installation automatique, le joueur est prévenu de la nouvelle version', async () => {
    const envois = []
    const updater = fauxUpdater({ version: '1.8.0' })
    let telechargements = 0
    const telecharger = updater.downloadUpdate
    updater.downloadUpdate = async () => { telechargements++; return telecharger() }
    const maj = new MiseAJour({ updater, actif: true, auto: false, envoyer: e => envois.push(e), occupe: () => false, delaiInstallationMs: 0 })
    assert.deepEqual(await maj.verifier(), { disponible: false, manuelle: true, version: '1.8.0' })
    await attendre(10)
    assert.equal(telechargements, 0)
    assert.deepEqual(updater.installations, [])
    assert.deepEqual(envois, [])
    assert.equal(updater.autoInstallOnAppQuit, false, 'rien ne s’installe à la fermeture')
    // déjà à jour : rien à dire ; version mal formée annoncée par le site : ignorée
    assert.deepEqual(await new MiseAJour({ updater: fauxUpdater(), actif: true, auto: false, envoyer: () => {}, occupe: () => false }).verifier(), { disponible: false })
    assert.deepEqual(await new MiseAJour({ updater: fauxUpdater({ version: '<b>2</b>' }), actif: true, auto: false, envoyer: () => {}, occupe: () => false }).verifier(), { disponible: false })
})
