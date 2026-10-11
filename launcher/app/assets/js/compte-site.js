// Comptes HeraliZ du site (Azuriom) : connexion par l'API d'authentification d'Azuriom (/api/auth/authenticate,
// /verify, /logout ; à activer dans l'administration du site : Paramètres → Authentification), création de compte et
// double authentification par l'extension HeraliZ du site (/api/heraliz/..., site/extension/heraliz du dépôt). Le mot
// de passe ne fait que passer : le launcher ne garde que le jeton rendu par le site, et le site reste la seule base
// des comptes.
const got = require('got')
const { httpsUrl } = require('./security')
const { validateUsername } = require('./offline-profile')

// raisons rendues par Azuriom (app/Http/Controllers/Api/AuthController.php) et par l'extension HeraliZ
const BANNI = 'Ce compte est banni du site HeraliZ.'
const MESSAGES = {
    invalid_credentials: 'E-mail, pseudo ou mot de passe incorrect.',
    invalid_2fa: 'Code de double authentification incorrect.',
    invalid_password: 'Mot de passe incorrect.',
    user_banned: BANNI,
    register_disabled: 'La création de compte est fermée pour le moment.',
    too_many_accounts: 'Trop de comptes créés depuis cette connexion : réessaie plus tard.'
}
const SITE_INJOIGNABLE = 'Le site HeraliZ ne répond pas. Vérifie ta connexion internet et réessaie.'
const API_FERMEE = 'La connexion des comptes n’est pas encore ouverte sur le site HeraliZ.'
const INSCRIPTION_FERMEE = 'La création de compte depuis le launcher n’est pas encore ouverte : crée ton compte sur heraliz.fr.'
const SESSION_EXPIREE = 'Ta session a expiré : reconnecte-toi avec ton compte HeraliZ.'
const CODE_A2F_REFUSE = 'Code incorrect : vérifie que l’heure de ton téléphone est juste et entre le code affiché maintenant.'
// champs du site -> champs du formulaire du launcher
const CHAMPS = { name: 'pseudo', email: 'email', password: 'motDePasse', conditions: 'conditions' }
const PSEUDO = /^[A-Za-z0-9_]{3,16}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const SECRET_A2F = /^[A-Z2-7]{16,128}$/

// Requête JSON en HTTPS vers le site ; rend { status, body } sans lever d'erreur pour les réponses 4xx / 5xx.
async function requeteParDefaut(url, donnees, methode = 'POST') {
    const options = {
        responseType: 'json', throwHttpErrors: false, followRedirect: false, headers: { accept: 'application/json' },
        timeout: { request: 15000 }, retry: { limit: 0 }
    }
    const reponse = methode === 'GET' ? await got.get(url, options) : await got.post(url, { ...options, json: donnees })
    return { status: reponse.statusCode, body: reponse.body }
}

// Profil rendu par le site : seuls les champs utiles au launcher, contrôlés.
function profil(body) {
    if (!body || typeof body !== 'object' || typeof body.username !== 'string') throw new Error('Réponse du site HeraliZ illisible.')
    if (body.banned === true) throw new Error(BANNI)
    try { validateUsername(body.username) } catch {
        throw new Error('Ton pseudo sur le site doit faire 3 à 16 caractères (lettres, chiffres ou _) pour jouer. Change-le sur heraliz.fr.')
    }
    return { id: body.id, username: body.username, jeton: typeof body.access_token === 'string' ? body.access_token : null }
}

function erreur({ status, body }) {
    const raison = body && typeof body === 'object' ? body.reason : undefined
    if (raison && MESSAGES[raison]) return new Error(MESSAGES[raison])
    // API pas activée dans l'administration du site (400, « Auth API is not enabled ») ou absente (404)
    if (status === 400 || status === 404) return new Error(API_FERMEE)
    if (status === 401) return new Error(SESSION_EXPIREE)
    if (status === 429) return new Error('Trop d’essais : attends un peu avant de réessayer.')
    if (status >= 500) return new Error('Le site HeraliZ a rencontré un problème. Réessaie dans un instant.')
    return new Error('Connexion refusée par le site HeraliZ.')
}

// Erreurs de formulaire rendues par le site (422 : { errors: { champ: [message] } }) : la première par champ.
function erreursChamps(body) {
    const champs = {}
    if (body && typeof body.errors === 'object') {
        for (const [nom, messages] of Object.entries(body.errors)) {
            const message = Array.isArray(messages) ? messages[0] : messages
            if (typeof message === 'string' && message) champs[CHAMPS[nom] || nom] = message
        }
    }
    return champs
}

// Contrôle avant envoi, mêmes règles que le site : { champ: message } (vide si tout va bien).
function verifierInscription(valeur, motDePasseMin = 12) {
    const champs = {}
    if (!PSEUDO.test(valeur?.pseudo || '')) champs.pseudo = 'Le pseudo doit faire 3 à 16 caractères : lettres, chiffres ou _.'
    if (!EMAIL.test((valeur?.email || '').trim()) || (valeur.email || '').trim().length > 50) champs.email = 'Entre une adresse e-mail valide.'
    if (typeof valeur?.motDePasse !== 'string' || valeur.motDePasse.length < motDePasseMin) {
        champs.motDePasse = `Le mot de passe doit faire au moins ${motDePasseMin} caractères.`
    } else if (valeur.motDePasse !== valeur.confirmation) {
        champs.confirmation = 'Les deux mots de passe ne sont pas identiques.'
    }
    return champs
}

class CompteSite {
    constructor(siteUrl, requete = requeteParDefaut) {
        this.base = httpsUrl(siteUrl).href.replace(/\/+$/, '')
        this.requete = requete
    }

    // chemin sous /api/ du site : auth/authenticate, heraliz/inscription...
    async appeler(chemin, donnees, methode = 'POST') {
        try {
            return await this.requete(`${this.base}/api/${chemin}`, donnees, methode)
        } catch {
            throw new Error(SITE_INJOIGNABLE)
        }
    }

    // { etat: 'ok', compte } ou { etat: 'a2f' } (le code de double authentification est demandé) ; erreur sinon.
    // Identifiant : l'e-mail ou le pseudo du compte (Azuriom accepte les deux).
    async connexion(identifiant, motDePasse, code) {
        if (typeof identifiant !== 'string' || !identifiant.trim() || typeof motDePasse !== 'string' || !motDePasse) {
            throw new Error('Entre ton e-mail (ou ton pseudo) et ton mot de passe.')
        }
        const donnees = { email: identifiant.trim(), password: motDePasse }
        if (code) donnees.code = String(code).replace(/\s+/g, '')
        const reponse = await this.appeler('auth/authenticate', donnees)
        if (reponse.status >= 200 && reponse.status < 300) {
            const compte = profil(reponse.body)
            if (!compte.jeton) throw new Error('Réponse du site HeraliZ illisible.')
            return { etat: 'ok', compte }
        }
        if (reponse.body?.status === 'pending' && reponse.body?.reason === '2fa') return { etat: 'a2f' }
        throw erreur(reponse)
    }

    // Le compte du jeton, ou null si le site ne le reconnaît plus (déconnecté, expiré, banni). Erreur si injoignable.
    async verifier(jeton) {
        const reponse = await this.appeler('auth/verify', { access_token: jeton })
        if (reponse.status >= 200 && reponse.status < 300) {
            try { return profil(reponse.body) } catch { return null }
        }
        if (reponse.status >= 500) throw new Error(SITE_INJOIGNABLE)
        return null
    }

    async deconnexion(jeton) {
        await this.appeler('auth/logout', { access_token: jeton }).catch(() => {})
    }

    // Ce que permet le site : { inscription, motDePasseMin, conditions } ; inscription fermée si l'extension manque.
    async etat() {
        const reponse = await this.appeler('heraliz/etat', null, 'GET')
        const body = reponse.status === 200 && reponse.body && typeof reponse.body === 'object' ? reponse.body : null
        if (!body) return { inscription: false, motDePasseMin: 12, conditions: null }
        let conditions = null
        try { conditions = typeof body.conditions === 'string' ? httpsUrl(body.conditions).href : null } catch { /* adresse refusée */ }
        return {
            inscription: body.inscription === true,
            motDePasseMin: Number.isInteger(body.mot_de_passe_min) && body.mot_de_passe_min >= 8 && body.mot_de_passe_min <= 64 ? body.mot_de_passe_min : 12,
            conditions
        }
    }

    // Création d'un compte du site : { etat: 'ok', compte } (déjà connecté) ; erreur avec .champs pour le formulaire.
    async inscription(valeur, motDePasseMin = 12) {
        const champs = verifierInscription(valeur, motDePasseMin)
        if (Object.keys(champs).length > 0) throw Object.assign(new Error(Object.values(champs)[0]), { champs })
        const reponse = await this.appeler('heraliz/inscription', {
            name: valeur.pseudo, email: valeur.email.trim(), password: valeur.motDePasse,
            password_confirmation: valeur.confirmation, conditions: valeur.conditions === true
        })
        if (reponse.status >= 200 && reponse.status < 300) {
            const compte = profil(reponse.body)
            if (!compte.jeton) throw new Error('Réponse du site HeraliZ illisible.')
            return { etat: 'ok', compte }
        }
        if (reponse.status === 422) {
            const erreurs = erreursChamps(reponse.body)
            const premier = Object.values(erreurs)[0]
            throw Object.assign(new Error(premier || 'Vérifie les informations du formulaire.'), { champs: erreurs })
        }
        if (reponse.status === 404) throw new Error(INSCRIPTION_FERMEE)
        throw erreur(reponse)
    }

    // Double authentification : { active: true } ou { active: false, secret, qr } (QR code en SVG, à scanner).
    async a2fPreparer(jeton) {
        const reponse = await this.appeler('heraliz/a2f/preparer', { access_token: jeton })
        if (reponse.status !== 200) throw reponse.status === 404 ? new Error('La double authentification depuis le launcher n’est pas encore ouverte.') : erreur(reponse)
        const body = reponse.body
        if (body?.active === true) return { active: true }
        if (body?.active !== false || typeof body.secret !== 'string' || !SECRET_A2F.test(body.secret)
            || typeof body.qr !== 'string' || !body.qr.trimStart().startsWith('<svg') || body.qr.length > 200000) {
            throw new Error('Réponse du site HeraliZ illisible.')
        }
        return { active: false, secret: body.secret, qr: body.qr.trim() }
    }

    // Active la double authentification avec le code de l'application : { codes } (codes de secours, à noter).
    async a2fActiver(jeton, code, motDePasse) {
        const chiffres = String(code || '').replace(/\s+/g, '')
        if (!/^\d{6}$/.test(chiffres)) throw new Error('Entre le code à 6 chiffres de ton application.')
        if (typeof motDePasse !== 'string' || !motDePasse) throw new Error('Entre ton mot de passe.')
        const reponse = await this.appeler('heraliz/a2f/activer', { access_token: jeton, code: chiffres, password: motDePasse })
        if (reponse.status === 422 && reponse.body?.reason === 'invalid_2fa') throw new Error(CODE_A2F_REFUSE)
        if (reponse.status !== 200) throw erreur(reponse)
        const codes = Array.isArray(reponse.body?.codes) ? reponse.body.codes.filter(c => typeof c === 'string' && /^[\w-]{4,64}$/.test(c)) : []
        return { codes }
    }
}

module.exports = { CompteSite, verifierInscription }
