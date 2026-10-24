const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { MiseAJour } = require('../app/assets/js/mise-a-jour')

// Faux autoUpdater d'electron-updater : ce que le test prépare (version en ligne, téléchargement réussi ou non).
function fauxUpdater({ version = null, echec = false, jamais = false, info = null } = {}) {
    const u = new EventEmitter()
    u.verifications = 0
    u.installations = []
    u.checkForUpdates = async () => {
        u.verifications++
        if (jamais) return new Promise(() => {})
        return version ? { isUpdateAvailable: true, updateInfo: info || { version } } : { isUpdateAvailable: false, updateInfo: { version: '1.6.0' } }
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

// --- Signature des mises à jour (Ed25519) : une clé publique réglée => le fichier de version doit être signé. ---
const crypto = require('node:crypto')

function clesEtSignature(contenu) {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519')
    const pub = publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
    const sig = crypto.sign(null, Buffer.from(contenu), privateKey).toString('base64')
    return { pub, sig }
}

test('signature : sans clé réglée, rien ne change (comportement d’avant)', async () => {
    const u = fauxUpdater({ version: '1.7.0' })
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false, delaiInstallationMs: 0,
        recuperer: async () => { throw new Error('ne doit pas être appelé') } })
    assert.deepEqual(await maj.verifier(), { disponible: true, version: '1.7.0' })
})

test('signature : fichier de version bien signé => mise à jour acceptée', async () => {
    const yml = 'version: 1.7.0\nsha512: ABC\n'
    const { pub, sig } = clesEtSignature(yml)
    const u = fauxUpdater({ version: '1.7.0', info: { version: '1.7.0', sha512: 'ABC' } })
    const recuperer = async url => Buffer.from(url.endsWith('.sig') ? sig : yml)
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false, delaiInstallationMs: 0,
        clePublique: pub, majBaseUrl: 'https://heraliz.fr/telecharger/', plateforme: 'win32', recuperer })
    assert.deepEqual(await maj.verifier(), { disponible: true, version: '1.7.0' })
    assert.equal(u.verifications, 1)
})

test('signature : fichier changé entre la vérification et la lecture d’electron-updater => REFUSÉE', async () => {
    const yml = 'version: 1.7.0\nfiles:\n  - url: HeraliZ-Launcher-Setup.exe\n    sha512: ABC\n    size: 10\n'
    const { pub, sig } = clesEtSignature(yml)
    const u = fauxUpdater({ version: '1.7.0', info: { version: '1.7.0', files: [{ url: 'pirate.exe', sha512: 'XYZ', size: 10 }] } })
    const recuperer = async url => Buffer.from(url.endsWith('.sig') ? sig : yml)
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false, delaiInstallationMs: 0,
        clePublique: pub, majBaseUrl: 'https://heraliz.fr/telecharger/', plateforme: 'win32', recuperer })
    assert.deepEqual(await maj.verifier(), { disponible: false, signatureInvalide: true })
    assert.equal(u.installations.length, 0)
})

test('signature : fichier falsifié => mise à jour REFUSÉE, electron-updater jamais appelé', async () => {
    const { pub, sig } = clesEtSignature('version: 1.7.0\nsha512: ABC\n')
    const u = fauxUpdater({ version: '9.9.9' })
    // le pirate sert un autre latest.yml (nouvelle version) avec l'ancienne signature
    const recuperer = async url => Buffer.from(url.endsWith('.sig') ? sig : 'version: 9.9.9\nsha512: PIRATE\n')
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false,
        clePublique: pub, majBaseUrl: 'https://heraliz.fr/telecharger/', plateforme: 'win32', recuperer })
    assert.deepEqual(await maj.verifier(), { disponible: false, signatureInvalide: true })
    assert.equal(u.verifications, 0, 'electron-updater ne doit pas être sollicité')
})

test('signature : pas de réseau => pas de mise à jour, sans message de sécurité', async () => {
    const { pub } = clesEtSignature('x')
    const u = fauxUpdater({ version: '1.7.0' })
    const recuperer = async () => { throw new Error('délai dépassé') }
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false,
        clePublique: pub, majBaseUrl: 'https://heraliz.fr/telecharger/', plateforme: 'win32', recuperer })
    assert.deepEqual(await maj.verifier(), { disponible: false })
    assert.equal(u.verifications, 0)
})

test('signature : signature introuvable sur le site (404) => mise à jour refusée', async () => {
    const { pub } = clesEtSignature('x')
    const u = fauxUpdater({ version: '1.7.0' })
    const recuperer = async () => { throw new Error('http 404') }
    const maj = new MiseAJour({ updater: u, actif: true, envoyer: () => {}, occupe: () => false,
        clePublique: pub, majBaseUrl: 'https://heraliz.fr/telecharger/', plateforme: 'win32', recuperer })
    assert.deepEqual(await maj.verifier(), { disponible: false, signatureInvalide: true })
    assert.equal(u.verifications, 0)
})
