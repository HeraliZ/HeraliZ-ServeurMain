'use strict'

// Mise à jour automatique du launcher lui-même (version installée sur Windows). Au démarrage, le launcher lit
// https://heraliz.fr/telecharger/latest.yml (adresse : « publish » de electron-builder.yml, écrite dans
// app-update.yml à la fabrication). Si une version plus récente existe, il la télécharge (empreinte SHA-512
// contrôlée), l'installe sans rien demander puis redémarre. Le site injoignable ou lent ne bloque jamais le démarrage.
// Le pack de jeu (mods, gunpacks) a sa propre mise à jour, à chaque partie (launcher-service.js).

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

class MiseAJour {
    // updater : l'autoUpdater d'electron-updater ; actif : version installée sur Windows ; envoyer : état vers
    // l'interface ; occupe : une préparation ou une partie est en cours (jamais de redémarrage dans ce cas) ;
    // memoire : { lire(), ecrire(valeur) }, les essais d'installation gardés d'un démarrage à l'autre.
    constructor({ updater, actif, envoyer, occupe, memoire, delaiMs = DELAI_VERIFICATION_MS, delaiInstallationMs = 1200 }) {
        this.updater = updater
        this.memoire = memoire
        this.actif = Boolean(actif && updater)
        this.envoyer = envoyer
        this.occupe = occupe
        this.delaiMs = delaiMs
        this.delaiInstallationMs = delaiInstallationMs
        this.verification = null
        this.dernierPourcent = -1
        if (!this.actif) return
        this.updater.autoDownload = false
        this.updater.autoInstallOnAppQuit = true
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
    // { disponible: false, bloquee: true } : cette version n'a pas réussi à s'installer, à retélécharger à la main.
    verifier() {
        if (!this.verification) this.verification = this.chercher()
        return this.verification
    }

    async chercher() {
        if (!this.actif) return { disponible: false }
        let resultat = null
        try {
            resultat = await avecDelai(this.updater.checkForUpdates(), this.delaiMs)
        } catch {
            return { disponible: false }
        }
        const version = resultat?.updateInfo?.version
        if (!resultat?.isUpdateAvailable || typeof version !== 'string') return { disponible: false }
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

module.exports = { MiseAJour }
