'use strict'

// Mise à jour du launcher lui-même (version installée). Au démarrage, le launcher lit sur https://heraliz.fr/telecharger/
// (adresse : « publish » de electron-builder.yml, écrite dans app-update.yml à la fabrication) latest.yml (Windows),
// latest-mac.yml ou latest-linux.yml. Si une version plus récente existe :
// - Windows et Linux en AppImage (auto) : il la télécharge (empreinte SHA-512 contrôlée), l'installe sans rien
//   demander puis redémarre ;
// - Mac (application non signée par Apple : macOS refuse qu'elle se remplace seule) et Linux en paquet .deb (il
//   faudrait le mot de passe administrateur) : le joueur est seulement prévenu, avec un lien vers le nouveau fichier.
// Le site injoignable ou lent ne bloque jamais le démarrage. Le pack de jeu (mods, gunpacks) a sa propre mise à jour,
// à chaque partie (launcher-service.js).

const https = require('https')
const yaml = require('js-yaml')
const { chargerClePublique, verifier } = require('./signature')

// Fichier de version lu par electron-updater selon le système (servi sur https://heraliz.fr/telecharger/).
const CHANNEL = { win32: 'latest.yml', darwin: 'latest-mac.yml', linux: 'latest-linux.yml' }

// Télécharge un petit fichier en HTTPS (le fichier de version ou sa signature). HTTPS seul, 10 s, 5 Mo au plus.
function recupererHttps(url) {
    return new Promise((resolve, reject) => {
        let u
        try { u = new URL(url) } catch { return reject(new Error('url invalide')) }
        if (u.protocol !== 'https:') return reject(new Error('https requis'))
        const req = https.get(url, { timeout: 10000 }, reponse => {
            if (reponse.statusCode !== 200) { reponse.resume(); return reject(new Error('http ' + reponse.statusCode)) }
            const morceaux = []
            let taille = 0
            reponse.on('data', c => {
                taille += c.length
                if (taille > 5 * 1024 * 1024) { reponse.destroy(); reject(new Error('trop volumineux')) }
                else morceaux.push(c)
            })
            reponse.on('end', () => resolve(Buffer.concat(morceaux)))
        })
        req.on('timeout', () => req.destroy(new Error('délai dépassé')))
        req.on('error', reject)
    })
}

const DELAI_VERIFICATION_MS = 10000
// une même version n'est installée automatiquement que 2 fois par jour : si l'installation échoue (fichier bloqué,
// antivirus), le launcher ne redémarre pas en boucle et demande de le retélécharger
const ESSAIS_MAX = 2
const PERIODE_ESSAIS_MS = 24 * 3600 * 1000

function avecDelai(promesse, ms) {
    let minuterie
    const delai = new Promise(resolve => { minuterie = setTimeout(() => resolve(null), ms) })
    return Promise.race([promesse, delai]).finally(() => clearTimeout(minuterie))
}

// Ce qu'electron-updater utilise pour télécharger et contrôler l'installeur : version, fichiers (lien, SHA-512,
// taille), ancien champ path / sha512. Tout doit être identique au fichier de version signé.
function resumeVersion(info) {
    if (!info || typeof info !== 'object') return null
    const fichiers = Array.isArray(info.files) ? info.files.map(f => [String(f?.url), String(f?.sha512), Number(f?.size)]) : []
    return JSON.stringify([String(info.version), String(info.path ?? ''), String(info.sha512 ?? ''), fichiers])
}

function memeVersion(signe, lu) {
    const a = resumeVersion(signe)
    return a !== null && a === resumeVersion(lu)
}

class MiseAJour {
    // updater : celui d'electron-updater ; actif : version installée (pas en développement) ; auto : installation
    // automatique (sinon le joueur est seulement prévenu) ; envoyer : état vers l'interface ; occupe : une préparation
    // ou une partie est en cours (jamais de redémarrage dans ce cas) ; memoire : { lire(), ecrire(valeur) }, les essais
    // d'installation gardés d'un démarrage à l'autre.
    constructor({ updater, actif, auto = true, envoyer, occupe, memoire, delaiMs = DELAI_VERIFICATION_MS, delaiInstallationMs = 1200,
        clePublique = null, majBaseUrl = null, plateforme = process.platform, recuperer = recupererHttps }) {
        this.updater = updater
        this.memoire = memoire
        this.actif = Boolean(actif && updater)
        this.auto = auto !== false
        this.envoyer = envoyer
        this.occupe = occupe
        this.delaiMs = delaiMs
        this.delaiInstallationMs = delaiInstallationMs
        this.verification = null
        this.dernierPourcent = -1
        // signature des mises à jour : clé publique embarquée (app/heraliz.json). Vide = pas de vérification
        // (comportement d'avant). Dès qu'une clé est réglée, le fichier de version DOIT être signé (signature.js).
        this.clePublique = chargerClePublique(clePublique)
        this.majBaseUrl = typeof majBaseUrl === 'string' && majBaseUrl ? majBaseUrl.replace(/\/+$/, '') + '/' : null
        this.channel = CHANNEL[plateforme] || null
        this.recuperer = recuperer
        if (!this.actif) return
        this.updater.autoDownload = false
        this.updater.autoInstallOnAppQuit = this.auto
        this.updater.disableDifferentialDownload = true
        this.updater.allowDowngrade = false
        this.updater.allowPrerelease = false
        this.updater.on('download-progress', progres => {
            const pourcent = Math.max(0, Math.min(100, Math.floor(Number(progres?.percent) || 0)))
            if (pourcent === this.dernierPourcent) return
            this.dernierPourcent = pourcent
            this.envoyer({ etat: 'telechargement', pourcent })
        })
        this.updater.on('error', erreur => console.error('[HeraliZ/mise-a-jour]', erreur?.message || erreur))
    }

    // { disponible: false } ou { disponible: true, version } : le téléchargement est alors lancé, suivi par envoyer() ;
    // { disponible: false, bloquee: true } : cette version n'a pas réussi à s'installer, à retélécharger à la main ;
    // { disponible: false, manuelle: true, version } : sans installation automatique (Mac, .deb), à télécharger à la main.
    verifier() {
        if (!this.verification) this.verification = this.chercher()
        return this.verification
    }

    // Vérifie la signature Ed25519 du fichier de version AVANT qu'electron-updater ne télécharge ou n'installe.
    // Rend { verifie: false } si aucune clé n'est réglée (on n'empêche rien, comme avant) ; sinon { verifie: true,
    // ok } : ok seulement si le fichier de version a bien été signé par la clé du propriétaire. Le fichier de version
    // contient l'empreinte SHA-512 de l'installeur (contrôlée par electron-updater) : signer ce fichier protège donc
    // aussi l'installeur. Toute erreur (réseau, signature absente) => ok:false : on ne met pas à jour ce coup-ci ;
    // reseau:true quand le site n'a pas répondu du tout (le joueur n'a alors pas de message de sécurité).
    async verifierSignature() {
        if (!this.clePublique) return { verifie: false }
        if (!this.majBaseUrl || !this.channel) return { verifie: true, ok: false }
        try {
            const [contenu, signature] = await Promise.all([
                this.recuperer(this.majBaseUrl + this.channel),
                this.recuperer(this.majBaseUrl + this.channel + '.sig')
            ])
            if (!verifier(contenu, signature.toString('utf8'), this.clePublique)) return { verifie: true, ok: false }
            return { verifie: true, ok: true, info: yaml.load(contenu.toString('utf8'), { schema: yaml.JSON_SCHEMA }) }
        } catch (erreur) {
            // le site a répondu autre chose que le fichier (signature absente : 404…) = refus par sécurité ; pas de
            // réseau (hors ligne, délai dépassé) = simplement pas de mise à jour ce coup-ci, sans message de sécurité
            const reponduParLeSite = /^http \d+$/.test(String(erreur?.message || ''))
            return { verifie: true, ok: false, reseau: !reponduParLeSite }
        }
    }

    async chercher() {
        if (!this.actif) return { disponible: false }
        const sig = await this.verifierSignature()
        if (sig.verifie && !sig.ok && sig.reseau) return { disponible: false }
        if (sig.verifie && !sig.ok) {
            console.error('[HeraliZ/mise-a-jour] signature de la mise à jour invalide ou absente : mise à jour refusée')
            return { disponible: false, signatureInvalide: true }
        }
        let resultat = null
        try {
            resultat = await avecDelai(this.updater.checkForUpdates(), this.delaiMs)
        } catch {
            return { disponible: false }
        }
        // electron-updater relit lui-même le fichier de version : ce qu'il va télécharger doit être EXACTEMENT ce qui a
        // été signé (sinon un fichier changé entre les deux lectures passerait sans signature).
        if (sig.verifie && !memeVersion(sig.info, resultat?.updateInfo)) {
            console.error('[HeraliZ/mise-a-jour] le fichier de version a changé depuis la vérification : mise à jour refusée')
            return { disponible: false, signatureInvalide: true }
        }
        const version = resultat?.updateInfo?.version
        if (!resultat?.isUpdateAvailable || typeof version !== 'string' || !/^\d{1,4}\.\d{1,4}\.\d{1,6}$/.test(version)) return { disponible: false }
        if (!this.auto) return { disponible: false, manuelle: true, version }
        const essai = this.essai(version)
        if (essai.fois >= ESSAIS_MAX) return { disponible: false, bloquee: true, version }
        this.telecharger(version, essai)
        return { disponible: true, version }
    }

    // essais récents d'installation de cette version : { fois, depuis }
    essai(version) {
        let lu = null
        try { lu = this.memoire?.lire() } catch { /* mémoire illisible : comme vide */ }
        const recent = lu && lu.version === version && Number.isFinite(lu.depuis) && Date.now() - lu.depuis < PERIODE_ESSAIS_MS
        return recent && Number.isInteger(lu.fois) ? { fois: lu.fois, depuis: lu.depuis } : { fois: 0, depuis: Date.now() }
    }

    async telecharger(version, essai) {
        try {
            await this.updater.downloadUpdate()
        } catch {
            this.envoyer({ etat: 'erreur' })
            return
        }
        // une partie lancée entre-temps : la mise à jour s'installera à la fermeture du launcher
        if (this.occupe()) {
            this.envoyer({ etat: 'plus-tard' })
            return
        }
        try { this.memoire?.ecrire({ version, fois: essai.fois + 1, depuis: essai.depuis }) } catch { /* sans mémoire */ }
        this.envoyer({ etat: 'installation' })
        // installation silencieuse, puis le launcher se relance de lui-même
        setTimeout(() => this.updater.quitAndInstall(true, true), this.delaiInstallationMs)
    }
}

module.exports = { MiseAJour, memeVersion }
