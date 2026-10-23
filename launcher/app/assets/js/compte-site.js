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
    too_many_accounts: 'Trop de comptes créés depuis cette connexion : réessaie plus tard.',
    too_many_attempts: 'Trop d’essais ratés : attends 10 minutes avant de réessayer.'
}
const SITE_INJOIGNABLE = 'Le site HeraliZ ne répond pas. Vérifie ta connexion internet et réessaie.'
const API_FERMEE = 'La connexion des comptes n’est pas encore ouverte sur le site HeraliZ.'
const INSCRIPTION_FERMEE = 'La création de compte depuis le launcher n’est pas encore ouverte : crée ton compte sur heraliz.fr.'
const SESSION_EXPIREE = 'Ta session a expiré : reconnecte-toi avec ton compte HeraliZ.'
const JETON_TROP_DE_DEMANDES = 'Trop de demandes au site HeraliZ depuis ta connexion : attends une minute avant de relancer.'
const CODE_A2F_REFUSE = 'Code incorrect : vérifie que l’heure de ton téléphone est juste et entre le code affiché maintenant.'
// champs du site -> champs du formulaire du launcher
const CHAMPS = { name: 'pseudo', email: 'email', password: 'motDePasse', conditions: 'conditions' }
const PSEUDO = /^[A-Za-z0-9_]{3,16}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const SECRET_A2F = /^[A-Z2-7]{16,128}$/
const JETON_JEU = /^[A-Za-z0-9_-]{20,2000}\.[A-Za-z0-9_-]{43}$/
// taille maximale du pack de jeu (distribution.json), comme avant qu'il ne vienne du compte
const TAILLE_PACK = 32 * 1024 * 1024
// Accès au jeu selon le Discord du compte (/api/heraliz/compte/etat ; pack et jeton de lancement refusés en 403
// { erreur: 'discord', statut } tant que ce n'est pas « ok ») : compte Discord lié, membre du Discord HeraliZ, pas banni
// et, pendant la bêta, accepté par le staff.
const MESSAGES_DISCORD = {
    a_lier: 'Lie ton compte Discord pour jouer.',
    pas_membre: 'Rejoins le Discord HeraliZ pour jouer.',
    en_attente: 'Ton compte est en attente de validation par le staff.',
    refuse: 'Ton compte a été refusé.',
    banni: 'Ce compte est banni.',
    // connexion (adresse IP) bloquée après le ban d'un autre compte vu dessus : PAS un ban de ce compte, rien n'est
    // effacé de ce PC (seul « banni » retire les fichiers du jeu)
    ip_bloquee: 'Impossible de jouer depuis cette connexion internet. Ouvre un ticket sur le Discord HeraliZ.',
    indisponible: 'Discord ne répond pas pour le moment, réessaie dans un instant.'
}
const STATUTS_DISCORD = new Set(['ok', ...Object.keys(MESSAGES_DISCORD)])
const HOTES_DISCORD = ['discord.gg', 'discord.com']

// Erreur « accès au jeu refusé à cause du Discord du compte » : .discord = le statut, pour ouvrir le bon panneau ;
// .banni en plus pour un compte banni (le launcher retire alors les fichiers du jeu de ce PC, effacement.js).
function erreurDiscord(statut) {
    if (!Object.hasOwn(MESSAGES_DISCORD, statut)) return new Error('Ton compte n’a pas accès au jeu pour le moment.')
    return Object.assign(new Error(MESSAGES_DISCORD[statut]), { discord: statut }, statut === 'banni' ? { banni: true } : {})
}

// Compte banni du site (Azuriom : « user_banned », ou banned: true dans le profil) : .banni, comme ci-dessus.
function erreurBanni() {
    return Object.assign(new Error(BANNI), { banni: true })
}

// Réponse 403 { erreur: 'discord', statut } du pack ou du jeton de lancement.
function refusDiscord(reponse) {
    return (reponse.status === 403 || reponse.status === 503) && reponse.body?.erreur === 'discord' ? erreurDiscord(reponse.body.statut) : null
}

// Requête JSON en HTTPS vers le site ; rend { status, body } sans lever d'erreur pour les réponses 4xx / 5xx.
// « limite » (octets) : grosse réponse à un POST (le pack de jeu), lue par morceaux et refusée au-delà.
async function requeteParDefaut(url, donnees, methode = 'POST', { limite } = {}) {
    const options = {
        responseType: 'json', throwHttpErrors: false, followRedirect: false, headers: { accept: 'application/json' },
        timeout: { request: 15000 }, retry: { limit: 0 }
    }
    if (limite) {
        const flux = got.stream.post(url, { ...options, responseType: undefined, json: donnees, timeout: { request: 120000 } })
        let status = 0
        flux.once('response', reponse => { status = reponse.statusCode })
        const morceaux = []
        let taille = 0
        for await (const morceau of flux) {
            taille += morceau.length
            if (taille > limite) { flux.destroy(); throw new Error('Réponse trop volumineuse.') }
            morceaux.push(morceau)
        }
        let body = null
        try { body = JSON.parse(Buffer.concat(morceaux).toString('utf8')) } catch { /* pas du JSON : body null */ }
        return { status, body }
    }
    const reponse = methode === 'GET' ? await got.get(url, options) : await got.post(url, { ...options, json: donnees })
    return { status: reponse.statusCode, body: reponse.body }
}

// Profil rendu par le site : seuls les champs utiles au launcher, contrôlés.
function profil(body) {
    if (!body || typeof body !== 'object' || typeof body.username !== 'string') throw new Error('Réponse du site HeraliZ illisible.')
    if (body.banned === true) throw erreurBanni()
    try { validateUsername(body.username) } catch {
        throw new Error('Ton pseudo sur le site doit faire 3 à 16 caractères (lettres, chiffres ou _) pour jouer. Change-le sur heraliz.fr.')
    }
    return { id: body.id, username: body.username, jeton: typeof body.access_token === 'string' ? body.access_token : null }
}

function erreur({ status, body }) {
    const raison = body && typeof body === 'object' ? body.reason : undefined
    if (raison === 'user_banned') return erreurBanni()
    if (raison && MESSAGES[raison]) return new Error(MESSAGES[raison])
    // refus de l'extension HeraliZ (connexion bloquée...) : son message, nettoyé ; jamais un ban de ce compte
    if (raison === 'refused') return new Error(messageDuSite(body) || 'Connexion refusée par le site HeraliZ.')
    // API pas activée dans l'administration du site (400, « Auth API is not enabled ») ou absente (404)
    if (status === 400 || status === 404) return new Error(API_FERMEE)
    if (status === 401) return new Error(SESSION_EXPIREE)
    if (status === 429) return new Error('Trop d’essais : attends un peu avant de réessayer.')
    if (status >= 500) return new Error('Le site HeraliZ a rencontré un problème. Réessaie dans un instant.')
    return new Error('Connexion refusée par le site HeraliZ.')
}

// Message lisible rendu par le site (texte simple, sans caractères de contrôle, 300 caractères au plus), ou null.
function messageDuSite(body) {
    const message = typeof body?.message === 'string' ? body.message.replace(/[\x00-\x1f\x7f]/g, ' ').trim().slice(0, 300) : ''
    return message || null
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
    async appeler(chemin, donnees, methode = 'POST', options) {
        try {
            return await this.requete(`${this.base}/api/${chemin}`, donnees, methode, options)
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

    // Le compte du jeton, ou null si le site ne le reconnaît plus (déconnecté, expiré). Erreur si injoignable ; erreur
    // avec .banni si le compte est banni.
    async verifier(jeton) {
        const reponse = await this.appeler('auth/verify', { access_token: jeton })
        if (reponse.status >= 200 && reponse.status < 300) {
            try { return profil(reponse.body) } catch (error) {
                if (error?.banni) throw error
                return null
            }
        }
        if (reponse.status === 403 && reponse.body?.reason === 'user_banned') throw erreurBanni()
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

    // Jeton de lancement du jeu signé par le site pour le compte connecté (Spartan le vérifie à la connexion au serveur),
    // lié au pseudo du compte et à l'empreinte du dossier mods. null si le site n'en fait pas ; erreur si la session a
    // expiré ou si le compte est banni.
    async jetonJeu(jeton, empreinte) {
        if (!/^[0-9a-f]{64}$/.test(empreinte || '')) return null
        let reponse
        try {
            reponse = await this.appeler('heraliz/jeton-jeu', { access_token: jeton, empreinte })
        } catch {
            return null
        }
        if (reponse.status === 200 && typeof reponse.body?.jeton === 'string' && JETON_JEU.test(reponse.body.jeton)) return reponse.body.jeton
        if (reponse.status === 401) throw new Error(SESSION_EXPIREE)
        if (reponse.status === 403 && reponse.body?.reason === 'user_banned') throw erreurBanni()
        const discord = refusDiscord(reponse)
        if (discord) throw discord
        if (reponse.status === 429) throw Object.assign(new Error(JETON_TROP_DE_DEMANDES), { jetonManquant: true })
        // extension absente (404), clé pas encore créée (503), site en panne : null, le launcher décide (launcher-service)
        return null
    }

    // Pack de jeu (distribution Helios) donné par le site au compte connecté : ses liens vers les fichiers du pack sont
    // signés pour quelques heures (sans compte, le site refuse ces fichiers). Erreur claire sinon.
    async pack(jeton) {
        const reponse = await this.appeler('heraliz/pack', { access_token: jeton }, 'POST', { limite: TAILLE_PACK })
        if (reponse.status === 200 && reponse.body && typeof reponse.body === 'object' && !Array.isArray(reponse.body)) return reponse.body
        const discord = refusDiscord(reponse)
        if (discord) throw discord
        if (reponse.status === 503) throw new Error('Le pack de jeu HeraliZ n’est pas encore en ligne. Réessaie plus tard.')
        if (reponse.status === 404) throw new Error('Le site HeraliZ ne donne pas encore le pack de jeu au launcher. Réessaie plus tard.')
        if (reponse.status === 200) throw new Error('Réponse du site HeraliZ illisible.')
        throw erreur(reponse)
    }

    // Accès au jeu selon le Discord du compte : { statut, pseudo, validationManuelle, lien, invitation }. Site d'avant
    // cette règle (404) : « ok ». null si la réponse est illisible (le site décidera au pack). Erreur avec
    // .sessionExpiree si le site ne reconnaît plus le jeton ; erreur si le site ne répond pas.
    async etatDiscord(jeton) {
        const reponse = await this.appeler('heraliz/compte/etat', { access_token: jeton })
        if (reponse.status === 404) return { statut: 'ok', pseudo: null, validationManuelle: false, lien: null, invitation: null }
        if (reponse.status === 401) throw Object.assign(new Error(SESSION_EXPIREE), { sessionExpiree: true })
        if (reponse.status === 403 && reponse.body?.reason === 'user_banned') return { statut: 'banni', pseudo: null, validationManuelle: false, lien: null, invitation: null }
        if (reponse.status >= 500) throw new Error(SITE_INJOIGNABLE)
        const body = reponse.status === 200 && reponse.body && typeof reponse.body === 'object' ? reponse.body : null
        if (!body || !STATUTS_DISCORD.has(body.statut)) return null
        const pseudo = typeof body.discord?.pseudo === 'string' ? body.discord.pseudo.replace(/[\x00-\x1f]/g, '').slice(0, 64) || null : null
        // lien pour lier le Discord : seulement sur le site lui-même ; invitation : seulement sur Discord
        let lien = null
        let invitation = null
        try {
            const url = typeof body.lien === 'string' ? httpsUrl(body.lien) : null
            if (url && url.host === new URL(this.base).host) lien = url.href
        } catch { /* adresse refusée */ }
        try {
            const url = typeof body.invitation === 'string' ? httpsUrl(body.invitation) : null
            if (url && HOTES_DISCORD.includes(url.hostname)) invitation = url.href
        } catch { /* adresse refusée */ }
        return { statut: body.statut, pseudo, validationManuelle: body.validation_manuelle === true, lien, invitation }
    }
}

module.exports = { CompteSite, verifierInscription, requeteParDefaut, erreurDiscord, erreurBanni, HOTES_DISCORD }
