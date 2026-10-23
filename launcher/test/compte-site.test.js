const test = require('node:test')
const assert = require('node:assert/strict')
const Module = require('node:module')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const http = require('node:http')
const { CompteSite, verifierInscription, requeteParDefaut } = require('../app/assets/js/compte-site')

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
    await cas({ status: 429, body: { status: 'error', reason: 'too_many_attempts', message: 'Too many attempts' } }, /attends 10 minutes/)
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

test('jeton de lancement du jeu : signé par le site pour le compte connecté', async () => {
    const JETON = 'A'.repeat(40) + '.' + 'B'.repeat(43)
    const EMP = 'c'.repeat(64)
    const site = fauxSite({
        'heraliz/jeton-jeu': d => d.access_token === 'perime' ? { status: 401, body: { status: 'error', reason: 'invalid_token' } }
            : d.access_token === 'banni' ? { status: 403, body: { status: 'error', reason: 'user_banned' } }
                : d.access_token === 'sans-cle' ? { status: 503, body: { status: 'error', reason: 'jeton_indisponible' } }
                    : { status: 200, body: { jeton: JETON, expire: 1800021600 } }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.equal(await compte.jetonJeu('bon', EMP), JETON)
    assert.deepEqual(site.appels[0], { url: 'https://heraliz.fr/api/heraliz/jeton-jeu', methode: 'POST', donnees: { access_token: 'bon', empreinte: EMP } })
    assert.equal(await compte.jetonJeu('sans-cle', EMP), null)
    await assert.rejects(compte.jetonJeu('perime', EMP), /session a expiré/)
    await assert.rejects(compte.jetonJeu('banni', EMP), /banni/)
    assert.equal(await compte.jetonJeu('bon', 'pas-une-empreinte'), null)
    assert.equal(site.appels.length, 4)
    // réponse bizarre ou site en panne : pas de jeton, la partie se lance quand même (le serveur décide)
    const bizarre = fauxSite({ 'heraliz/jeton-jeu': { status: 200, body: { jeton: JETON + ' -javaagent:x.jar' } } })
    assert.equal(await new CompteSite('https://heraliz.fr', bizarre.requete).jetonJeu('bon', EMP), null)
    const panne = fauxSite({ 'heraliz/jeton-jeu': new Error('ECONNRESET') })
    assert.equal(await new CompteSite('https://heraliz.fr', panne.requete).jetonJeu('bon', EMP), null)
})

test('pack de jeu du compte connecté : rendu tel quel (liens signés par le site), erreurs claires', async () => {
    const PACK = { version: '1.0.0', servers: [{ id: 'heraliz-1.20.1', modules: [] }] }
    const site = fauxSite({
        'heraliz/pack': d => d.access_token === 'perime' ? { status: 401, body: { status: 'error', reason: 'invalid_token' } }
            : d.access_token === 'banni' ? { status: 403, body: { status: 'error', reason: 'user_banned' } }
                : d.access_token === 'sans-pack' ? { status: 503, body: { status: 'error', reason: 'pack_absent' } }
                    : d.access_token === 'bizarre' ? { status: 200, body: ['pas', 'un', 'pack'] }
                        : { status: 200, body: PACK }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.deepEqual(await compte.pack('bon'), PACK)
    assert.deepEqual(site.appels[0], { url: 'https://heraliz.fr/api/heraliz/pack', methode: 'POST', donnees: { access_token: 'bon' } })
    await assert.rejects(compte.pack('perime'), /session a expiré/)
    await assert.rejects(compte.pack('banni'), /banni/)
    await assert.rejects(compte.pack('sans-pack'), /pas encore en ligne/)
    await assert.rejects(compte.pack('bizarre'), /illisible/)
    await assert.rejects(new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/pack': { status: 404, body: null } }).requete).pack('bon'), /ne donne pas encore/)
    await assert.rejects(new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/pack': new Error('ECONNRESET') }).requete).pack('bon'), /ne répond pas/)
})

test('grosse réponse du site (pack) : lue par morceaux, refusée au-delà de la limite, erreurs du site rendues', async () => {
    const serveur = http.createServer((req, res) => {
        let corps = ''
        req.on('data', c => { corps += c })
        req.on('end', () => {
            const { access_token: jeton } = JSON.parse(corps)
            if (jeton === 'perime') { res.writeHead(401, { 'Content-Type': 'application/json' }); res.end('{"reason":"invalid_token"}'); return }
            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ servers: [], remplissage: jeton === 'gros' ? 'x'.repeat(5000) : '' }))
        })
    })
    await new Promise(resolve => serveur.listen(0, '127.0.0.1', resolve))
    try {
        const url = `http://127.0.0.1:${serveur.address().port}/api/heraliz/pack`
        assert.deepEqual(await requeteParDefaut(url, { access_token: 'bon' }, 'POST', { limite: 1000 }), { status: 200, body: { servers: [], remplissage: '' } })
        assert.deepEqual(await requeteParDefaut(url, { access_token: 'perime' }, 'POST', { limite: 1000 }), { status: 401, body: { reason: 'invalid_token' } })
        await assert.rejects(requeteParDefaut(url, { access_token: 'gros' }, 'POST', { limite: 1000 }), /volumineuse/)
    } finally {
        serveur.close()
    }
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
        a2fActiver: async (jeton, code, motDePasse) => { f.activation = { jeton, code, motDePasse }; return { codes: ['abcd-1234'] } },
        jetonJeu: async (jeton, empreinte) => { f.demandeJeton = { jeton, empreinte }; return 'A'.repeat(40) + '.' + 'B'.repeat(43) }
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
    service.accordCapture(true)
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

test('à chaque partie, le jeton de lancement est demandé au site pour le compte et le dossier mods du jeu', async () => {
    const compte = fauxCompte()
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    assert.equal(await service.jetonSpartan(), 'A'.repeat(40) + '.' + 'B'.repeat(43))
    assert.equal(compte.demandeJeton.jeton, 'jeton-secret')
    // dossier mods du jeu absent ou vide : empreinte de « rien », comme le calcule le mod
    assert.equal(compte.demandeJeton.empreinte, require('node:crypto').createHash('sha256').update('').digest('hex'))
})

test('avec les comptes du site, le pack de jeu vient du site pour le compte connecté', async () => {
    const compte = fauxCompte()
    const artifact = nom => ({ url: `https://heraliz.fr/launcher/${nom}?e=1800000000&s=${'A'.repeat(22)}`, MD5: '0'.repeat(32), size: 1 })
    compte.pack = async jeton => {
        compte.demandePack = jeton
        return { servers: [{ id: 'heraliz-1.20.1', address: 'play.heraliz.fr', minecraftVersion: '1.20.1', modules: [
            { id: 'net.minecraftforge:lowcodelanguage:1.20.1-47.4.0', type: 'ForgeHosted', artifact: artifact('forge.jar'), subModules: [
                { id: '1.20.1-47.4.0', type: 'VersionManifest', artifact: artifact('forge.json') }] }] }] }
    }
    const reglages = { ...config, serverAddress: 'play.heraliz.fr', distributionUrl: 'https://heraliz.fr/launcher/distribution.json' }
    const service = new LauncherService(reglages, () => {}, { compteSite: compte })
    await assert.rejects(service.loadDistribution(), /Connecte-toi/)
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    const distribution = await service.loadDistribution()
    assert.equal(compte.demandePack, 'jeton-secret')
    assert.equal(distribution.getServerById('heraliz-1.20.1').rawServer.javaOptions.supported, '17.x')
    // un pack qui ne serait pas celui du serveur HeraliZ est refusé, même venant du site
    compte.pack = async () => ({ servers: [{ id: 'autre', address: 'play.heraliz.fr', minecraftVersion: '1.20.1', modules: [] }] })
    await assert.rejects(service.loadDistribution(), /ne correspond pas/)
})

test('accès au jeu selon le Discord du compte : statut, liens contrôlés, ancien site, refus du pack et du jeton', async () => {
    const site = fauxSite({
        'heraliz/compte/etat': d => d.access_token === 'perime' ? { status: 401, body: { status: 'error', reason: 'invalid_token' } }
            : d.access_token === 'banni' ? { status: 403, body: { status: 'error', reason: 'user_banned' } }
                : d.access_token === 'panne' ? { status: 502, body: null }
                    : d.access_token === 'bizarre' ? { status: 200, body: { statut: 'autre' } }
                        : d.access_token === 'piege' ? { status: 200, body: { statut: 'a_lier', discord: null, lien: 'https://ailleurs.example/lier', invitation: 'https://discord.example/x' } }
                            : { status: 200, body: { statut: 'en_attente', discord: { pseudo: 'Survivant\u0007#42' }, lien: 'https://heraliz.fr/heraliz/discord/lier?jeton=abc', invitation: 'https://discord.gg/heraliz', validation_manuelle: true } }
    })
    const compte = new CompteSite('https://heraliz.fr', site.requete)
    assert.deepEqual(await compte.etatDiscord('bon'), {
        statut: 'en_attente', pseudo: 'Survivant#42', validationManuelle: true,
        lien: 'https://heraliz.fr/heraliz/discord/lier?jeton=abc', invitation: 'https://discord.gg/heraliz'
    })
    assert.deepEqual(site.appels[0], { url: 'https://heraliz.fr/api/heraliz/compte/etat', methode: 'POST', donnees: { access_token: 'bon' } })
    const piege = await compte.etatDiscord('piege')
    assert.equal(piege.lien, null)
    assert.equal(piege.invitation, null)
    assert.equal((await compte.etatDiscord('banni')).statut, 'banni')
    assert.equal(await compte.etatDiscord('bizarre'), null)
    await assert.rejects(compte.etatDiscord('perime'), e => e.sessionExpiree === true)
    await assert.rejects(compte.etatDiscord('panne'), /ne répond pas/)
    // site d'avant cette règle : rien n'est exigé
    assert.equal((await new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/compte/etat': { status: 404, body: null } }).requete).etatDiscord('bon')).statut, 'ok')
    // pack et jeton de lancement refusés à cause du Discord
    const refus = fauxSite({
        'heraliz/pack': { status: 403, body: { erreur: 'discord', statut: 'a_lier' } },
        'heraliz/jeton-jeu': { status: 403, body: { erreur: 'discord', statut: 'pas_membre' } }
    })
    const bloque = new CompteSite('https://heraliz.fr', refus.requete)
    await assert.rejects(bloque.pack('bon'), e => e.discord === 'a_lier' && /Lie ton compte Discord/.test(e.message))
    await assert.rejects(bloque.jetonJeu('bon', 'a'.repeat(64)), e => e.discord === 'pas_membre')
})

test('pas de partie tant que le Discord du compte n’est pas en règle ; la déconnexion oublie l’état', async () => {
    const compte = fauxCompte()
    let statut = 'a_lier'
    compte.etatDiscord = async () => {
        if (statut === 'perime') throw Object.assign(new Error('expirée'), { sessionExpiree: true })
        if (statut === 'panne') throw new Error('Le site HeraliZ ne répond pas.')
        return { statut, pseudo: 'Survivant#42', validationManuelle: true, lien: 'https://heraliz.fr/heraliz/discord/lier?jeton=abc', invitation: 'https://discord.gg/heraliz' }
    }
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    service.accordCapture(true)
    compte.verification = 'ok'
    const etat = await service.etatDiscord()
    assert.deepEqual(etat.discord, { statut: 'a_lier', pseudo: 'Survivant#42', validationManuelle: true })
    assert.equal(JSON.stringify(etat).includes('jeton=abc'), false, 'les liens restent dans le launcher')
    assert.equal(service.lienDiscord('lier'), 'https://heraliz.fr/heraliz/discord/lier?jeton=abc')
    assert.equal(service.lienDiscord('invitation'), 'https://discord.gg/heraliz')
    assert.equal(service.lienDiscord('autre'), null)
    await assert.rejects(service.launch(), e => e.discord === 'a_lier')
    assert.equal(service.busy, false)
    statut = 'panne'
    const horsLigne = await service.etatDiscord()
    assert.equal(horsLigne.horsLigne, true)
    assert.equal(horsLigne.discord.statut, 'a_lier')
    statut = 'perime'
    assert.equal((await service.etatDiscord()).sessionExpiree, true)
    assert.equal(service.getState().profile, null)
    assert.equal(service.getState().discord, null)
})

test('compte banni : fichiers du jeu retirés de ce PC, plus de pack avant un nouvel « ok » du site', async () => {
    const Config = require('../app/assets/js/configmanager')
    const compte = fauxCompte()
    let statut = 'banni'
    compte.etatDiscord = async () => statut === 'muet' ? null : ({ statut, pseudo: null, validationManuelle: false, lien: null, invitation: null })
    const service = new LauncherService(config, () => {}, { compteSite: compte })
    await service.connexion({ email: 'a@b.fr', password: 'bon', acceptedRules: true })
    service.accordCapture(true)
    const instance = path.join(Config.getInstanceDirectory(), config.serverId)
    const modstore = path.join(Config.getCommonDirectory(), 'modstore')
    fs.mkdirSync(path.join(instance, 'tacz'), { recursive: true })
    fs.mkdirSync(path.join(instance, 'screenshots'), { recursive: true })
    fs.mkdirSync(modstore, { recursive: true })
    fs.writeFileSync(path.join(modstore, 'mod.jar'), 'jar')
    fs.writeFileSync(path.join(instance, 'options.txt'), 'lang:fr_fr')
    const fichierBan = path.join(directory, 'compte-banni.json')

    const etat = await service.etatDiscord()
    assert.equal(etat.discord.statut, 'banni')
    assert.equal(etat.fichiersEffaces, true)
    assert.equal(fs.existsSync(modstore), false)
    assert.equal(fs.existsSync(path.join(instance, 'tacz')), false)
    assert.ok(fs.existsSync(path.join(instance, 'options.txt')))
    assert.ok(fs.existsSync(path.join(instance, 'screenshots')))
    assert.deepEqual({ ...JSON.parse(fs.readFileSync(fichierBan, 'utf8')), depuis: 0 }, { compte: 7, depuis: 0, effacement: 'fait' })
    await assert.rejects(service.launch(), e => e.discord === 'banni' && e.banni === true)
    assert.equal(service.busy, false)
    // autre compte sur ce PC, site muet : le verrou reste, pas de pack
    service.logout()
    await service.connexion({ email: 'c@d.fr', password: 'bon', acceptedRules: true })
    statut = 'muet'
    await assert.rejects(service.launch(), /confirmer l’accès/)
    assert.ok(fs.existsSync(fichierBan))
    // nouvel « ok » du site : verrou levé
    statut = 'ok'
    await service.etatDiscord()
    assert.equal(fs.existsSync(fichierBan), false)
    assert.equal(service.getState().fichiersEffaces, false)
    // ban vu à la vérification du compte (Azuriom) : même effacement, le compte reste connecté pour voir le panneau
    compte.verifier = async () => { throw Object.assign(new Error('Ce compte est banni du site HeraliZ.'), { banni: true }) }
    const verifie = await service.verifierCompte()
    assert.equal(verifie.discord.statut, 'banni')
    assert.ok(verifie.profile)
    assert.ok(fs.existsSync(fichierBan))
    fs.rmSync(fichierBan)
})

test('compte-site : un ban d’Azuriom est signalé par .banni (connexion, vérification, pack, jeton)', async () => {
    const banni = { status: 403, body: { status: 'error', reason: 'user_banned' } }
    const compte = new CompteSite('https://heraliz.fr', fauxSite({ authenticate: banni, verify: banni, 'heraliz/pack': banni, 'heraliz/jeton-jeu': banni }).requete)
    await assert.rejects(compte.connexion('a@b.fr', 'x'), e => e.banni === true)
    await assert.rejects(compte.verifier('j'), e => e.banni === true)
    await assert.rejects(compte.pack('j'), e => e.banni === true)
    await assert.rejects(compte.jetonJeu('j', 'a'.repeat(64)), e => e.banni === true)
    const profilBanni = new CompteSite('https://heraliz.fr', fauxSite({ verify: { status: 200, body: { ...PROFIL, banned: true } } }).requete)
    await assert.rejects(profilBanni.verifier('j'), e => e.banni === true)
})

test('connexion bloquée (IP) : message du site, jamais un ban ; jeton refusé pour trop de demandes', async () => {
    const refuse = { status: 403, body: { status: 'error', reason: 'refused', message: 'Impossible de continuer depuis cette connexion.\u0007' } }
    const compte = new CompteSite('https://heraliz.fr', fauxSite({ authenticate: refuse }).requete)
    const erreur = await compte.connexion('moi@exemple.fr', 'secret').catch(e => e)
    assert.match(erreur.message, /^Impossible de continuer depuis cette connexion\.$/)
    assert.equal(erreur.banni, undefined)

    const ip = { status: 403, body: { erreur: 'discord', statut: 'ip_bloquee' } }
    const parIp = await new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/pack': ip }).requete).pack('jeton').catch(e => e)
    assert.equal(parIp.discord, 'ip_bloquee')
    assert.equal(parIp.banni, undefined)

    const sature = new CompteSite('https://heraliz.fr', fauxSite({ 'heraliz/jeton-jeu': { status: 429, body: null } }).requete)
    await assert.rejects(sature.jetonJeu('jeton', 'a'.repeat(64)), /Trop de demandes/)
})
