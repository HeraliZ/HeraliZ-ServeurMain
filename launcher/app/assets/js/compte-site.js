// Comptes HeraliZ du site (Azuriom) : connexion par l'API d'authentification d'Azuriom (/api/auth/authenticate,
// /verify, /logout ; à activer dans l'administration du site : Paramètres → Authentification). Le mot de passe ne fait
// que passer : le launcher ne garde que le jeton rendu par le site, et le site reste la seule base des comptes.
const got = require('got')
const { httpsUrl } = require('./security')
const { validateUsername } = require('./offline-profile')

const MESSAGES = {
    invalid_credentials: 'E-mail ou mot de passe incorrect.',
    invalid_2fa: 'Code de double authentification incorrect.',
    banned: 'Ce compte est banni du site HeraliZ.',
    user_banned: 'Ce compte est banni du site HeraliZ.',
    email_not_verified: 'Confirme d’abord ton adresse mail (lien reçu par mail), puis reconnecte-toi.'
}
const SITE_INJOIGNABLE = 'Le site HeraliZ ne répond pas. Vérifie ta connexion internet et réessaie.'
const API_FERMEE = 'La connexion des comptes n’est pas encore ouverte sur le site HeraliZ.'

// Requête JSON en HTTPS vers le site ; rend { status, body } sans lever d'erreur pour les réponses 4xx / 5xx.
async function requeteParDefaut(url, donnees) {
    const reponse = await got.post(url, {
        json: donnees, responseType: 'json', throwHttpErrors: false, followRedirect: false,
        timeout: { request: 15000 }, retry: { limit: 0 }
    })
    return { status: reponse.statusCode, body: reponse.body }
}

// Profil rendu par le site : seuls les champs utiles au launcher, contrôlés.
function profil(body) {
    if (!body || typeof body !== 'object' || typeof body.username !== 'string') throw new Error('Réponse du site HeraliZ illisible.')
    if (body.banned === true) throw new Error(MESSAGES.banned)
    try { validateUsername(body.username) } catch {
        throw new Error('Ton pseudo sur le site doit faire 3 à 16 caractères (lettres, chiffres ou _) pour jouer. Change-le sur heraliz.fr.')
    }
    return { id: body.id, username: body.username, jeton: typeof body.access_token === 'string' ? body.access_token : null }
}

function erreur({ status, body }) {
    if (status === 403 || status === 404) {
        if (body?.reason && MESSAGES[body.reason]) return new Error(MESSAGES[body.reason])
        return new Error(API_FERMEE)
    }
    if (body && typeof body === 'object' && body.status === 'error') {
        return new Error(MESSAGES[body.reason] || (typeof body.message === 'string' && body.message) || 'Connexion refusée par le site HeraliZ.')
    }
    if (status === 429) return new Error('Trop d’essais : attends une minute avant de réessayer.')
    return new Error('Le site HeraliZ a rencontré un problème. Réessaie dans un instant.')
}

class CompteSite {
    constructor(siteUrl, requete = requeteParDefaut) {
        this.base = httpsUrl(siteUrl).href.replace(/\/+$/, '')
        this.requete = requete
    }

    async appeler(chemin, donnees) {
        try {
            return await this.requete(`${this.base}/api/auth/${chemin}`, donnees)
        } catch {
            throw new Error(SITE_INJOIGNABLE)
        }
    }

    // { etat: 'ok', compte } ou { etat: 'a2f' } (le code de double authentification est demandé) ; erreur sinon.
    async connexion(email, motDePasse, code) {
        if (typeof email !== 'string' || !email.trim() || typeof motDePasse !== 'string' || !motDePasse) {
            throw new Error('Entre ton e-mail et ton mot de passe.')
        }
        const donnees = { email: email.trim(), password: motDePasse }
        if (code) donnees.code = String(code).replace(/\s+/g, '')
        const reponse = await this.appeler('authenticate', donnees)
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
        const reponse = await this.appeler('verify', { access_token: jeton })
        if (reponse.status >= 200 && reponse.status < 300) {
            try { return profil(reponse.body) } catch { return null }
        }
        if (reponse.status >= 500) throw new Error(SITE_INJOIGNABLE)
        return null
    }

    async deconnexion(jeton) {
        await this.appeler('logout', { access_token: jeton }).catch(() => {})
    }
}

module.exports = { CompteSite }
