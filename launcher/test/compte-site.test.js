const test = require('node:test')
const assert = require('node:assert/strict')
const Module = require('node:module')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { CompteSite, verifierInscription } = require('../app/assets/js/compte-site')

// Faux site Azuriom : réponses de /api/auth/<chemin> (authenticate, verify...) et de /api/heraliz/<chemin> (extension
// HeraliZ : heraliz/etat, heraliz/inscription...) selon ce que le test prépare.
function fauxSite(reponses) {
    const appels = []
    const requete = async (url, donnees, methode = 'POST') => {
        appels.push({ url, donnees, methode })
        const chemin = url.split('/api/')[1].replace(/^auth\//, '')
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
    await assert.rejects(compte.connexion('moi@exemple.fr', 'faux'), /E-mail, pseudo ou mot de passe incorrect/)
    // le pseudo marche aussi : il part dans le même champ, le site cherche l'un ou l'autre
    await compte.connexion('Survivant_42', 'bon')
    assert.deepEqual(site.appels.at(-1).donnees, { email: 'Survivant_42', password: 'bon' })
    await assert.rejects(compte.connexion('', ''), /Entre ton e-mail/)
})

test('compte banni, pseudo impossible en jeu, API fermée, site injoignable', async () => {
    const cas = async (reponse, attendu) => {
        const compte = new CompteSite('https://heraliz.fr/', fauxSite({ authenticate: reponse }).requete)
        await assert.rejects(compte.connexion('a@b.fr', 'x'), attendu)
    }
    await cas({ status: 200, body: { ...PROFIL, banned: true } }, /banni/)
    await cas({ status: 200, body: { ...PROFIL, username: 'Joueur avec espaces' } }, /3 à 16 caractères/)
    await cas({ status: 403, body: { status: 'error', reason: 'user_banned', message: 'User banned', ban_reason: 'x' } }, /banni/)
    await cas({ status: 400, body: { status: 'error', message: 'Auth API is not enabled' } }, /pas encore ouverte/)
    await cas({ status: 404, body: '<html>' }, /pas encore ouverte/)
    await cas({ status: 422, body: { status: 'error', reason: 'invalid_2fa', message: 'Invalid 2FA code' } }, /double authentification incorrect/)
    await cas({ status: 502, body: null }, /rencontré un problème/)
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

test('ce que permet le site : création de compte, mot de passe, conditions (adresses du site seulement)', async () => {
    const etat = async reponse => new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/etat': reponse }).requete).etat()
    const site = fauxSite({ 'heraliz/etat': { status: 200, body: { connexion: true, inscription: true, mot_de_passe_min: 12, conditions: 'https://heraliz.fr/user/register' } } })
    assert.deepEqual(await new CompteSite('https://heraliz.fr', site.requete).etat(), { inscription: true, motDePasseMin: 12, conditions: 'https://heraliz.fr/user/register' })
    assert.deepEqual(site.appels[0], { url: 'https://heraliz.fr/api/heraliz/etat', donnees: null, methode: 'GET' })
    // extension absente (404) ou réponse bizarre : pas de création de compte, réglages par défaut
    assert.deepEqual(await etat({ status: 404, body: '<html>' }), { inscription: false, motDePasseMin: 12, conditions: null })
    assert.deepEqual(await etat({ status: 200, body: { inscription: 'oui', mot_de_passe_min: 3, conditions: 'javascript:alert(1)' } }), { inscription: false, motDePasseMin: 12, conditions: null })
    assert.equal((await etat({ status: 200, body: { inscription: true, conditions: 'http://exemple.fr/cgu' } })).conditions, null)
    await assert.rejects(etat(new Error('ECONNRESET')), /ne répond pas/)
})

test('création de compte : mêmes règles que le site, erreurs sous les bons champs', async () => {
    const BON = { pseudo: 'Nouveau_42', email: ' nouveau@exemple.fr ', motDePasse: 'un-mot-de-passe', confirmation: 'un-mot-de-passe', conditions: true }
    assert.deepEqual(verifierInscription(BON), {})
    assert.deepEqual(Object.keys(verifierInscription({ ...BON, pseudo: 'a b', email: 'pas-un-mail', motDePasse: 'court', confirmation: 'court' })), ['pseudo', 'email', 'motDePasse'])
    assert.match(verifierInscription({ ...BON, confirmation: 'autre-mot-de-passe' }).confirmation, /pas identiques/)
    assert.deepEqual(verifierInscription({ ...BON, motDePasse: 'huit-car', confirmation: 'huit-car' }, 8), {})

    const site = fauxSite({
        'heraliz/inscription': d => d.name === 'Pris' ? { status: 422, body: { message: 'x', errors: { name: ['Ce pseudo est déjà pris.'], email: ['Cet e-mail a déjà un compte : connecte-toi.'] } } }
            : d.name === 'Ferme' ? { status: 403, body: { status: 'error', reason: 'register_disabled' } }
                : d.name === 'Trop' ? { status: 429, body: { status: 'error', reason: 'too_many_accounts' } }
                    : d.name === 'Absent' ? { status: 404, body: '<html>' }
                        : { status: 201, body: { ...PROFIL, username: d.name, access_token: 'jeton-neuf' } }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.deepEqual(await compte.inscription(BON), { etat: 'ok', compte: { id: 7, username: 'Nouveau_42', jeton: 'jeton-neuf' } })
    assert.deepEqual(site.appels[0], {
        url: 'https://heraliz.fr/api/heraliz/inscription', methode: 'POST',
        donnees: { name: 'Nouveau_42', email: 'nouveau@exemple.fr', password: 'un-mot-de-passe', password_confirmation: 'un-mot-de-passe', conditions: true }
    })
    const refus = await compte.inscription({ ...BON, pseudo: 'Pris' }).catch(e => e)
    assert.equal(refus.message, 'Ce pseudo est déjà pris.')
    assert.deepEqual(refus.champs, { pseudo: 'Ce pseudo est déjà pris.', email: 'Cet e-mail a déjà un compte : connecte-toi.' })
    await assert.rejects(compte.inscription({ ...BON, pseudo: 'Ferme' }), /fermée/)
    await assert.rejects(compte.inscription({ ...BON, pseudo: 'Trop' }), /Trop de comptes/)
    await assert.rejects(compte.inscription({ ...BON, pseudo: 'Absent' }), /crée ton compte sur heraliz\.fr/)
    // contrôlé avant d'envoyer : rien ne part vers le site
    const avant = site.appels.length
    const local = await compte.inscription({ ...BON, motDePasse: 'court', confirmation: 'court' }).catch(e => e)
    assert.deepEqual(Object.keys(local.champs), ['motDePasse'])
    assert.equal(site.appels.length, avant)
})

test('double authentification : QR code à scanner, activation, codes de secours', async () => {
    const SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10"/></svg>'
    const preparer = async reponse => new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/a2f/preparer': reponse }).requete).a2fPreparer('jeton')
    assert.deepEqual(await preparer({ status: 200, body: { active: true } }), { active: true })
    assert.deepEqual(await preparer({ status: 200, body: { active: false, secret: 'ABCDEFGHIJKLMNOP', qr: SVG } }), { active: false, secret: 'ABCDEFGHIJKLMNOP', qr: SVG })
    await assert.rejects(preparer({ status: 200, body: { active: false, secret: 'abc', qr: SVG } }), /illisible/)
    await assert.rejects(preparer({ status: 200, body: { active: false, secret: 'ABCDEFGHIJKLMNOP', qr: '<img src=x onerror=alert(1)>' } }), /illisible/)
    await assert.rejects(preparer({ status: 401, body: { status: 'error', reason: 'invalid_token' } }), /session a expiré/)
    await assert.rejects(preparer({ status: 404, body: '<html>' }), /pas encore ouverte/)

    const site = fauxSite({
        'heraliz/a2f/activer': d => d.password !== 'bon' ? { status: 422, body: { status: 'error', reason: 'invalid_password' } }
            : d.code !== '123456' ? { status: 422, body: { status: 'error', reason: 'invalid_2fa' } }
                : { status: 200, body: { active: true, codes: ['abcd-1234', 'efgh-5678', '<b>pas un code</b>'] } }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.deepEqual(await compte.a2fActiver('jeton', '123 456', 'bon'), { codes: ['abcd-1234', 'efgh-5678'] })
    assert.deepEqual(site.appels[0].donnees, { access_token: 'jeton', code: '123456', password: 'bon' })
    await assert.rejects(compte.a2fActiver('jeton', '654321', 'bon'), /heure de ton téléphone/)
    await assert.rejects(compte.a2fActiver('jeton', '123456', 'faux'), /Mot de passe incorrect/)
    await assert.rejects(compte.a2fActiver('jeton', '12345', 'bon'), /6 chiffres/)
    await assert.rejects(compte.a2fActiver('jeton', '123456', ''), /mot de passe/)
    assert.equal(site.appels.length, 3)
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
        deconnexion: async jeton => { f.deconnexions.push(jeton) },
        etat: async () => {
            if (f.etatPanne) throw new Error('Le site HeraliZ ne répond pas.')
            return { inscription: true, motDePasseMin: 12, conditions: null }
        },
        inscription: async (valeur, min) => {
            f.inscrit = { valeur, min }
            return { etat: 'ok', compte: { id: 8, username: valeur.pseudo, jeton: 'jeton-neuf' } }
        },
        a2fPreparer: async jeton => { f.jetonA2F = jeton; return { active: false, secret: 'ABCDEFGHIJKLMNOP', qr: '<svg/>' } },
        a2fActiver: async (jeton, code, motDePasse) => { f.activation = { jeton, code, motDePasse }; return { codes: ['abcd-1234'] } }
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

test('création de compte depuis le launcher : connecté tout de suite, puis double authentification proposée', async () => {
    const compte = fauxCompte()
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await assert.rejects(service.a2fPreparer(), /Connecte-toi/)
    compte.etatPanne = true
    assert.equal((await service.etatComptes()).injoignable, true)
    compte.etatPanne = false
    assert.deepEqual(await service.etatComptes(), { inscription: true, motDePasseMin: 12, conditions: null })
    await assert.rejects(service.inscription({ pseudo: 'Nouveau_42', email: 'n@exemple.fr', password: 'un-mot-de-passe', confirmation: 'un-mot-de-passe', acceptedRules: false }), /règlement/)
    const state = await service.inscription({ pseudo: 'Nouveau_42', email: 'n@exemple.fr', password: 'un-mot-de-passe', confirmation: 'un-mot-de-passe', acceptedRules: true })
    assert.equal(state.nouveauCompte, true)
    assert.deepEqual(state.profile, { username: 'Nouveau_42', rulesAccepted: true, compte: true })
    assert.deepEqual(compte.inscrit, { valeur: { pseudo: 'Nouveau_42', email: 'n@exemple.fr', motDePasse: 'un-mot-de-passe', confirmation: 'un-mot-de-passe', conditions: true }, min: 12 })
    assert.equal(fs.readFileSync(path.join(directory, 'config.json'), 'utf8').includes('un-mot-de-passe'), false)
    assert.equal((await service.a2fPreparer()).active, false)
    assert.equal(compte.jetonA2F, 'jeton-neuf')
    assert.deepEqual(await service.a2fActiver({ code: '123456', password: 'un-mot-de-passe' }), { codes: ['abcd-1234'] })
    assert.deepEqual(compte.activation, { jeton: 'jeton-neuf', code: '123456', motDePasse: 'un-mot-de-passe' })
})
