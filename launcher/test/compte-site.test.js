const test = require('node:test')
const assert = require('node:assert/strict')
const Module = require('node:module')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { CompteSite } = require('../app/assets/js/compte-site')

// Faux site Azuriom : réponses de /api/auth/<chemin> selon ce que le test prépare.
function fauxSite(reponses) {
    const appels = []
    const requete = async (url, donnees) => {
        appels.push({ url, donnees })
        const chemin = url.split('/api/auth/')[1]
        const r = reponses[chemin]
        if (r instanceof Error) throw r
        return typeof r === 'function' ? r(donnees) : r
    }
    return { appels, requete }
}
const PROFIL = { id: 7, username: 'Survivant_42', uuid: 'a', access_token: 'jeton-secret', email_verified: true, banned: false, role: { id: 1, name: 'Membre' } }

test('connexion au compte du site : succès, code A2F demandé, erreurs claires', async () => {
    const site = fauxSite({
        authenticate: d => d.code === '123456' ? { status: 200, body: PROFIL }
            : d.password === 'a2f' ? { status: 422, body: { status: 'pending', reason: '2fa', message: 'Code requis' } }
                : d.password === 'bon' ? { status: 200, body: PROFIL }
                    : { status: 422, body: { status: 'error', reason: 'invalid_credentials', message: 'Invalid credentials' } }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.deepEqual(await compte.connexion(' moi@exemple.fr ', 'bon'), { etat: 'ok', compte: { id: 7, username: 'Survivant_42', jeton: 'jeton-secret' } })
    assert.equal(site.appels[0].url, 'https://heraliz.fr/api/auth/authenticate')
    assert.deepEqual(site.appels[0].donnees, { email: 'moi@exemple.fr', password: 'bon' })
    assert.deepEqual(await compte.connexion('moi@exemple.fr', 'a2f'), { etat: 'a2f' })
    assert.equal((await compte.connexion('moi@exemple.fr', 'a2f', '123 456')).etat, 'ok')
    await assert.rejects(compte.connexion('moi@exemple.fr', 'faux'), /E-mail ou mot de passe incorrect/)
    await assert.rejects(compte.connexion('', ''), /Entre ton e-mail/)
})

test('compte banni, pseudo impossible en jeu, API fermée, site injoignable', async () => {
    const cas = async (reponse, attendu) => {
        const compte = new CompteSite('https://heraliz.fr/', fauxSite({ authenticate: reponse }).requete)
        await assert.rejects(compte.connexion('a@b.fr', 'x'), attendu)
    }
    await cas({ status: 200, body: { ...PROFIL, banned: true } }, /banni/)
    await cas({ status: 200, body: { ...PROFIL, username: 'Joueur avec espaces' } }, /3 à 16 caractères/)
    await cas({ status: 403, body: { message: 'Auth API disabled' } }, /pas encore ouverte/)
    await cas(new Error('ECONNREFUSED'), /ne répond pas/)
    assert.throws(() => new CompteSite('http://heraliz.fr'), /HTTPS/)
})

test('vérification du jeton : profil, jeton refusé (null), site en panne (erreur)', async () => {
    const site = fauxSite({ verify: d => d.access_token === 'bon' ? { status: 200, body: { ...PROFIL, access_token: undefined } } : { status: 401, body: { status: 'error', reason: 'invalid_token' } } })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.equal((await compte.verifier('bon')).username, 'Survivant_42')
    assert.equal(await compte.verifier('perime'), null)
    await assert.rejects(new CompteSite('https://heraliz.fr', fauxSite({ verify: { status: 503, body: null } }).requete).verifier('bon'), /ne répond pas/)
})

// Le service du launcher avec un faux compte du site.
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-compte-'))
const originalLoad = Module._load
const fakeApp = { getPath: () => directory, getVersion: () => '1.4.0' }
Module._load = function(name, parent, isMain) {
    if (name === 'electron') return { app: fakeApp, shell: { openPath: async () => '' } }
    return originalLoad.call(this, name, parent, isMain)
}
const LauncherService = require('../app/assets/js/launcher-service')
Module._load = originalLoad
test.after(() => fs.rmSync(directory, { recursive: true, force: true }))
const config = { serverId: 'heraliz-1.20.1', serverAddress: null, distributionUrl: null, siteUrl: 'https://heraliz.fr' }

function fauxCompte() {
    const f = {
        deconnexions: [],
        verification: 'ok',
        connexion: async (email, password, code) => {
            if (password === 'a2f' && !code) return { etat: 'a2f' }
            if (password === 'faux') throw new Error('E-mail ou mot de passe incorrect.')
            return { etat: 'ok', compte: { id: 7, username: 'Survivant_42', jeton: 'jeton-secret' } }
        },
        verifier: async () => {
            if (f.verification === 'panne') throw new Error('Le site HeraliZ ne répond pas.')
            return f.verification === 'ok' ? { id: 7, username: f.pseudo || 'Survivant_42', jeton: null } : null
        },
        deconnexion: async jeton => { f.deconnexions.push(jeton) }
    }
    return f
}

test('le launcher se connecte au compte du site et ne garde jamais le mot de passe', async () => {
    const compte = fauxCompte()
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    assert.equal(service.getState().comptesSite, true)
    assert.throws(() => service.saveProfile({ username: 'Triche', acceptedRules: true }), /compte HeraliZ/)
    await assert.rejects(service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: false }), /règlement/)
    assert.equal((await service.connexion({ email: 'a@b.fr', password: 'a2f', acceptedRules: true })).a2f, true)
    assert.equal(service.getState().profile, null)
    const state = await service.connexion({ email: 'a@b.fr', password: 'MotDePasse-Secret', code: '123456', acceptedRules: true })
    assert.deepEqual(state.profile, { username: 'Survivant_42', rulesAccepted: true, compte: true })
    const fichier = fs.readFileSync(path.join(directory, 'config.json'), 'utf8')
    assert.equal(fichier.includes('MotDePasse-Secret'), false)
    assert.equal(fichier.includes('jeton-secret'), false, 'sans chiffrement du système, le jeton n’est pas écrit sur le disque')
})

test('pseudo changé sur le site, site en panne, session expirée, déconnexion', async () => {
    const compte = fauxCompte()
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    compte.pseudo = 'Nouveau_Nom'
    assert.equal((await service.verifierCompte()).profile.username, 'Nouveau_Nom')
    compte.verification = 'panne'
    const horsLigne = await service.verifierCompte()
    assert.equal(horsLigne.horsLigne, true)
    assert.equal(horsLigne.profile.username, 'Nouveau_Nom')
    compte.verification = 'refuse'
    const expiree = await service.verifierCompte()
    assert.equal(expiree.sessionExpiree, true)
    assert.equal(expiree.profile, null)
    compte.verification = 'ok'
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    assert.equal((await service.logout()).profile, null)
    assert.deepEqual(compte.deconnexions, ['jeton-secret'])
})

test('pas de partie sans compte, et une session expirée libère le launcher', async () => {
    const compte = fauxCompte()
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await assert.rejects(service.launch(), /Connecte-toi/)
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    compte.verification = 'refuse'
    await assert.rejects(service.launch(), /session a expiré/)
    assert.equal(service.busy, false)
    assert.equal(service.getState().profile, null)
})
