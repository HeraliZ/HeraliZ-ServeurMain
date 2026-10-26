const { app, safeStorage } = require('electron')
const fs = require('fs-extra')
const path = require('node:path')
const os = require('node:os')
const net = require('node:net')
const dns = require('node:dns').promises
const execFile = require('node:util').promisify(require('node:child_process').execFile)
const got = require('got')
const { HeliosDistribution, validateLocalFile } = require('helios-core/common')
const { DistributionIndexProcessor, MojangIndexProcessor, downloadQueue, downloadFile, getExpectedDownloadSize } = require('helios-core/dl')
const { validateSelectedJvm, ensureJavaDirIsRoot, javaExecFromRoot, discoverBestJvmInstallation, latestOpenJDK, extractJdk } = require('helios-core/java')
const { getServerStatus } = require('helios-core/mojang')
const Config = require('./configmanager')
const ProcessBuilder = require('./processbuilder')
const { Gardien } = require('./gardien')
const { createOfflineProfile } = require('./offline-profile')
const { httpsUrl, validateDistribution, sansChemins } = require('./security')
const { chargerClePublique, verifierPack } = require('./signature')
const { CompteSite, erreurDiscord } = require('./compte-site')
const Spartan = require('./spartan')
const { effacerDonneesJeu, retirerAnciensGunpacks } = require('./effacement')

// Jeton du compte du site, chiffré par le système (Windows : lié à la session de l'utilisateur ; Mac : trousseau ;
// Linux : GNOME Keyring ou KWallet). Sans vrai chiffrement disponible, il n'est pas gardé : il faudra se reconnecter
// au prochain lancement.
function chiffrementDisponible() {
    if (!safeStorage?.isEncryptionAvailable?.()) return false
    // Linux sans trousseau : Electron « chiffre » avec une clé écrite dans son propre code, autant dire en clair
    if (process.platform === 'linux' && ['basic_text', 'unknown'].includes(safeStorage.getSelectedStorageBackend?.())) return false
    return true
}
function chiffrer(jeton) {
    if (!jeton || !chiffrementDisponible()) return null
    return safeStorage.encryptString(jeton).toString('base64')
}
function dechiffrer(valeur) {
    if (!valeur || !chiffrementDisponible()) return null
    try { return safeStorage.decryptString(Buffer.from(valeur, 'base64')) } catch { return null }
}

// Le jeu est lancé détaché : il reste ouvert si le launcher est fermé. Son numéro de processus est noté
// (partie-en-cours.json) pour qu'un launcher rouvert pendant la partie ne lance pas un second Minecraft.
const PARTIE_MAX_MS = 2 * 24 * 3600 * 1000
function fichierPartie() {
    return path.join(app.getPath('userData'), 'partie-en-cours.json')
}

// Nom du programme qui porte ce numéro de processus (« javaw.exe », « java »…), ou null s'il n'existe plus.
async function nomProcessus(pid) {
    try { process.kill(pid, 0) } catch { return null }
    try {
        if (process.platform === 'linux') return (await fs.readFile(`/proc/${pid}/comm`, 'utf8')).trim() || null
        if (process.platform === 'win32') {
            const { stdout } = await execFile('tasklist', ['/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH'], { timeout: 8000, windowsHide: true })
            const premier = /^"([^"]+)","(\d+)"/m.exec(stdout)
            return premier && Number(premier[2]) === pid ? premier[1] : null
        }
        const { stdout } = await execFile('ps', ['-p', String(pid), '-o', 'comm='], { timeout: 8000 })
        return path.basename(stdout.trim()) || null
    } catch { return null }
}
const estJava = nom => /^javaw?(\.exe)?$/i.test(nom || '')

// Compte banni vu sur ce PC (compte-banni.json) : { compte, depuis, effacement: 'a_faire' | 'fait' }. Tant qu'il est là,
// le pack n'est plus téléchargé, pour aucun compte, avant un nouvel « ok » du site (compte/etat). « a_faire » : fichiers
// du jeu encore à effacer (jeu ouvert, fichier verrouillé par Windows) : nouvel essai à la vérification suivante, à la
// fermeture du jeu et au prochain démarrage du launcher.
function fichierBan() {
    return path.join(app.getPath('userData'), 'compte-banni.json')
}
function lireBan() {
    try {
        const lu = JSON.parse(fs.readFileSync(fichierBan(), 'utf8'))
        return lu && typeof lu === 'object' ? lu : { effacement: 'a_faire' }
    } catch (erreur) {
        // fichier abîmé : on le croit (mieux vaut un pack refusé qu'un pack redonné)
        return erreur?.code === 'ENOENT' ? null : { effacement: 'a_faire' }
    }
}
function ecrireBan(valeur) {
    try { fs.writeFileSync(fichierBan(), JSON.stringify(valeur)) } catch (erreur) { console.error('[HeraliZ/ban] Mémoire du ban impossible :', erreur?.code) }
}
const ATTENTE_JEU_MS = 60 * 1000

// Machine et port réels du jeu : comme Minecraft, un enregistrement DNS « _minecraft._tcp.<domaine> » (SRV) peut
// renvoyer le domaine vers un autre hôte ou un autre port. Minecraft ne le cherche que pour le port 25565.
async function cibleJeu(host, port) {
    if (port === 25565) {
        try {
            const [srv] = await dns.resolveSrv(`_minecraft._tcp.${host}`)
            if (srv?.name && srv.port) return { host: srv.name, port: srv.port }
        } catch { /* pas de renvoi : le domaine lui-même */ }
    }
    return { host, port }
}

// Temps d'ouverture d'une connexion au serveur, en millisecondes (null si injoignable) : le « ping » de l'accueil.
async function mesurerPing(host, port) {
    try {
        const cible = await cibleJeu(host, port)
        const { address } = await dns.lookup(cible.host)
        return await new Promise(resolve => {
            const debut = process.hrtime.bigint()
            const socket = net.connect({ host: address, port: cible.port, timeout: 3000 })
            const fin = value => { socket.destroy(); resolve(value) }
            socket.once('connect', () => fin(Math.max(1, Math.round(Number(process.hrtime.bigint() - debut) / 1e6))))
            socket.once('timeout', () => fin(null))
            socket.once('error', () => fin(null))
        })
    } catch { return null }
}

// « 6,4 Mo/s · ≈ 40 s » d'après la vitesse moyenne depuis le début du téléchargement.
function detailTelechargement(octets, total, debut) {
    const secondes = (Date.now() - debut) / 1000
    if (secondes < 1 || octets <= 0) return null
    const vitesse = octets / secondes
    const reste = total > octets ? Math.ceil((total - octets) / vitesse) : 0
    return `${(vitesse / 1048576).toFixed(1).replace('.', ',')} Mo/s · ≈ ${reste >= 90 ? `${Math.ceil(reste / 60)} min` : `${reste} s`}`
}

class LauncherService {
    constructor(config, notify, { compteSite, nomProcessus: nomDuProcessus = nomProcessus } = {}) {
        this.config = config
        this.notify = notify
        this.busy = false
        this.game = null
        this.nomProcessus = nomDuProcessus
        // Comptes HeraliZ du site : obligatoires dès que l'adresse du site est réglée (sinon, pseudo seul, pour les essais).
        this.compteSite = compteSite || (config.siteUrl ? new CompteSite(config.siteUrl) : null)
        this.jeton = null
        // fichiers du jeu retirés de ce PC après un ban (texte du panneau « Ce compte est banni »)
        this.fichiersEffaces = lireBan()?.effacement === 'fait'
        this.progress = { busy: false, phase: 'idle', message: 'Prêt pour ta prochaine expédition.', percent: null, detail: null }
        Config.load()
        // Accord des captures d'écran redemandé à chaque ouverture du launcher (demande du propriétaire) : celui de la
        // dernière fois ne compte plus.
        if (Config.getAccordCapture()) {
            Config.setAccordCapture(false)
            Config.save()
        }
        // Les anciennes versions écrivaient le mot de passe en clair dans le dossier du jeu, pour un module jamais
        // installé sur le serveur : on l'efface. Le mot de passe se choisit en jeu (/register, puis /login).
        fs.removeSync(path.join(Config.getInstanceDirectory(), config.serverId, '.sl_password'))
        // Never preserve or expose legacy Microsoft/Mojang credentials.
        const previous = Config.getSelectedAccount()
        // Avec les comptes du site, un ancien profil « pseudo seul » n'est pas repris : connexion au compte demandée.
        const garde = previous?.type === 'offline' && (this.compteSite ? Boolean(previous.compte?.jeton) : !previous.compte) ? previous : null
        for (const key of Object.keys(Config.getAuthAccounts())) Config.removeAuthAccount(key)
        if (garde) {
            try {
                const profile = createOfflineProfile(garde.displayName)
                Config.setOfflineProfile(garde.compte ? { ...profile, compte: { id: garde.compte.id, jeton: garde.compte.jeton } } : profile, garde.rulesAccepted === true)
            } catch { /* invalid local profile: ask again */ }
        }
        Config.setClientToken(null)
        Config.setSelectedServer(config.serverId)
        Config.ensureJavaConfig(config.serverId, { suggestedMajor: 17 })
        // le jeu est toujours lancé détaché (processbuilder.js) : il survit à la fermeture du launcher
        Config.setLaunchDetached(true)
        Config.save()
    }

    emit(phase, message, percent = null, detail = null) {
        this.progress = { busy: this.busy, phase, message, percent: percent == null ? null : Math.max(0, Math.min(100, Math.floor(percent))), detail }
        this.notify(this.progress)
    }

    getState() {
        const profile = Config.getSelectedAccount()
        const totalRAM = Math.round(os.totalmem() / 1073741824)
        const maxRAM = Math.max(2, Math.min(32, Math.floor(os.totalmem() / 1073741824) - 2))
        const memory = Config.getMaxRAM(this.config.serverId)
        const ramGB = Math.floor(Number.parseInt(memory) / (memory.endsWith('M') ? 1024 : 1)) || 2
        return {
            version: app.getVersion(),
            // win32, darwin ou linux : la consigne de mise à jour à la main du launcher en dépend (renderer.js)
            plateforme: process.platform,
            profile: profile ? { username: profile.displayName, rulesAccepted: profile.rulesAccepted === true, compte: Boolean(profile.compte) } : null,
            comptesSite: Boolean(this.compteSite),
            settings: { ramGB: Math.min(maxRAM, Math.max(2, ramGB)), maxRAM, totalRAM, fullscreen: Config.getFullscreen() },
            configured: Boolean(this.config.serverAddress && this.config.distributionUrl),
            discordConfigured: Boolean(this.config.discordUrl), progress: this.progress,
            // accord pour les captures d'écran de l'anticheat (Spartan) : sans lui, pas de partie
            accordCapture: Config.getAccordCapture(),
            // compte banni : fichiers du jeu retirés de ce PC (true), ou à retirer dès que le jeu sera fermé (false)
            fichiersEffaces: this.fichiersEffaces === true,
            // accès au jeu selon le Discord du compte (null : pas encore demandé, ou site muet) ; les liens restent ici
            discord: this.discord ? { statut: this.discord.statut, pseudo: this.discord.pseudo || null, validationManuelle: this.discord.validationManuelle === true } : null
        }
    }

    saveProfile(value) {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour changer de profil.')
        if (this.compteSite) throw new Error('Connecte-toi avec ton compte HeraliZ.')
        if (!value || value.acceptedRules !== true) throw new Error('Tu dois accepter le règlement pour continuer.')
        const profile = createOfflineProfile(value.username)
        Config.setOfflineProfile(profile, true)
        Config.save()
        return this.getState()
    }

    // Connexion au compte HeraliZ du site : { ...état, a2f: true } si le site demande le code de double authentification.
    async connexion(value) {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour changer de compte.')
        if (!this.compteSite) throw new Error('Les comptes HeraliZ ne sont pas encore ouverts.')
        if (!value || value.acceptedRules !== true) throw new Error('Tu dois accepter le règlement pour continuer.')
        let resultat
        try {
            resultat = await this.compteSite.connexion(value.email, value.password, value.code)
        } catch (error) {
            if (error?.banni) {
                await this.signalerBan(null)
                error.message = `${error.message} ${this.fichiersEffaces ? 'Les fichiers du jeu HeraliZ ont été retirés de ce PC.' : 'Les fichiers du jeu HeraliZ seront retirés de ce PC.'}`
            }
            throw error
        }
        if (resultat.etat === 'a2f') return { ...this.getState(), a2f: true }
        this.enregistrerCompte(resultat.compte, true)
        return this.getState()
    }

    // Ce que permet le site (création de compte depuis le launcher, longueur du mot de passe, conditions du site).
    async etatComptes() {
        if (!this.compteSite) return { inscription: false, motDePasseMin: 12, conditions: null }
        try {
            this.etatSite = await this.compteSite.etat()
        } catch {
            this.etatSite = null
            return { inscription: false, motDePasseMin: 12, conditions: null, injoignable: true }
        }
        return this.etatSite
    }

    // Création d'un compte du site depuis le launcher : le compte est aussitôt connecté sur ce PC.
    async inscription(value) {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour changer de compte.')
        if (!this.compteSite) throw new Error('Les comptes HeraliZ ne sont pas encore ouverts.')
        if (!value || value.acceptedRules !== true) throw new Error('Tu dois accepter le règlement pour continuer.')
        const resultat = await this.compteSite.inscription({
            pseudo: value.pseudo, email: value.email, motDePasse: value.password, confirmation: value.confirmation, conditions: true
        }, this.etatSite?.motDePasseMin ?? 12)
        this.enregistrerCompte(resultat.compte, true)
        return { ...this.getState(), nouveauCompte: true }
    }

    // Double authentification du compte connecté : { active: true } ou { active: false, secret, qr }.
    async a2fPreparer() {
        if (!this.compteSite) throw new Error('Les comptes HeraliZ ne sont pas encore ouverts.')
        return this.compteSite.a2fPreparer(this.jetonActuel())
    }

    async a2fActiver(value) {
        if (!this.compteSite) throw new Error('Les comptes HeraliZ ne sont pas encore ouverts.')
        return this.compteSite.a2fActiver(this.jetonActuel(), value?.code, value?.password)
    }

    // Jeton de lancement signé par le site pour ce compte (Spartan, mode B), lié au dossier mods du jeu ; null sans site.
    async jetonSpartan() {
        if (!this.compteSite) return null
        const dossierMods = path.join(Config.getInstanceDirectory(), this.config.serverId, 'mods')
        const jeton = await this.compteSite.jetonJeu(this.jetonActuel(), Spartan.empreinteMods(dossierMods))
        // launcher de production (clé de signature réglée) : le serveur refuse toute connexion sans jeton, inutile de
        // lancer Minecraft pour rien (site saturé, clé pas encore créée, site en panne) ; le joueur réessaie
        if (!jeton && chargerClePublique(this.config.distributionPublicKey)) {
            throw new Error('Le site HeraliZ n’a pas pu donner l’autorisation de jouer pour le moment : réessaie dans une minute.')
        }
        return jeton
    }

    jetonActuel() {
        const jeton = this.jeton || dechiffrer(Config.getSelectedAccount()?.compte?.jeton)
        if (!jeton) throw new Error('Connecte-toi avec ton compte HeraliZ.')
        return jeton
    }

    enregistrerCompte(compte, rulesAccepted) {
        const profile = createOfflineProfile(compte.username)
        this.jeton = compte.jeton || this.jeton
        Config.setOfflineProfile({ ...profile, compte: { id: compte.id, jeton: chiffrer(this.jeton) } }, rulesAccepted)
        Config.save()
    }

    oublierCompte() {
        for (const key of Object.keys(Config.getAuthAccounts())) Config.removeAuthAccount(key)
        this.jeton = null
        this.discord = null
        Config.save()
    }

    // Le site reconnaît-il toujours le compte (au démarrage et avant chaque partie) ? Pseudo mis à jour s'il a changé
    // sur le site ; site injoignable : on garde le compte (horsLigne) ; jeton refusé : déconnexion (sessionExpiree).
    async verifierCompte() {
        const saved = Config.getSelectedAccount()
        if (!this.compteSite || !saved?.compte) return this.getState()
        const jeton = this.jeton || dechiffrer(saved.compte.jeton)
        let compte = null
        if (jeton) {
            try { compte = await this.compteSite.verifier(jeton) } catch (error) {
                if (!error?.banni) return { ...this.getState(), horsLigne: true }
                // compte banni : il reste connecté pour voir le panneau « Ce compte est banni » (se déconnecter reste possible)
                this.jeton = jeton
                this.discord = { ...(this.discord || {}), statut: 'banni' }
                await this.signalerBan(saved.compte.id)
                return this.getState()
            }
        }
        if (!compte) {
            this.oublierCompte()
            return { ...this.getState(), sessionExpiree: true }
        }
        this.jeton = jeton
        if (compte.username !== saved.displayName) this.enregistrerCompte({ ...compte, jeton }, saved.rulesAccepted === true)
        return this.getState()
    }

    // Accès au jeu selon le Discord du compte (après la connexion, au démarrage, avant chaque partie et tant que le
    // panneau Discord est ouvert). Site injoignable : on garde ce qu'on savait (horsLigne) ; jeton refusé : déconnexion.
    async etatDiscord() {
        const saved = Config.getSelectedAccount()
        if (!this.compteSite || !saved?.compte || typeof this.compteSite.etatDiscord !== 'function') return this.getState()
        const jeton = this.jeton || dechiffrer(saved.compte.jeton)
        if (!jeton) {
            this.oublierCompte()
            return { ...this.getState(), sessionExpiree: true }
        }
        try {
            const etat = await this.compteSite.etatDiscord(jeton)
            if (etat) this.discord = etat
            if (etat?.statut === 'banni') await this.signalerBan(saved.compte.id)
            else if (etat?.statut === 'ok') await this.leverBan(saved.compte.id)
        } catch (error) {
            if (error?.sessionExpiree) {
                this.oublierCompte()
                return { ...this.getState(), sessionExpiree: true }
            }
            return { ...this.getState(), horsLigne: true }
        }
        return this.getState()
    }

    // Le site dit que ce compte est banni : on s'en souvient sur ce PC (plus de pack pour personne avant un nouvel « ok »
    // du site) et on retire les fichiers du jeu, maintenant ou dès que le jeu est fermé.
    async signalerBan(compteId) {
        const avant = lireBan()
        if (avant?.effacement !== 'a_faire') {
            console.warn('[HeraliZ/ban] Compte banni : les fichiers du jeu HeraliZ vont être retirés de ce PC.')
        }
        ecrireBan({ compte: compteId ?? avant?.compte ?? null, depuis: avant?.depuis || Date.now(), effacement: 'a_faire' })
        this.fichiersEffaces = false
        await this.effacerJeu()
    }

    // Nouvel « ok » du site pour un compte : le pack peut de nouveau être téléchargé. Effacement encore en attente
    // (fichier verrouillé) : on le termine d'abord, ce compte retéléchargera un jeu propre.
    async leverBan(compteId) {
        const ban = lireBan()
        if (!ban) return
        if (ban.effacement === 'a_faire') await this.effacerJeu()
        try { fs.removeSync(fichierBan()) } catch { /* nouvel essai au prochain « ok » */ }
        this.fichiersEffaces = false
        console.info(`[HeraliZ/ban] Accès au jeu confirmé par le site (compte ${compteId ?? '?'}) : téléchargement du pack de nouveau permis.`)
    }

    // Retire de ce PC les fichiers du jeu HeraliZ si un ban l'a demandé. Jamais pendant une partie (fichiers ouverts par
    // le jeu, le serveur refuse déjà la connexion) : à la fermeture du jeu, sinon toutes les minutes tant qu'un jeu lancé
    // avant ce launcher tourne, et au prochain démarrage.
    async effacerJeu() {
        const ban = lireBan()
        if (ban?.effacement !== 'a_faire' || this.effacementEnCours) return
        if (this.game) {
            if (!this.attenteFinJeu) {
                this.attenteFinJeu = true
                this.game.once('close', () => { this.attenteFinJeu = false; this.effacerJeu().catch(() => {}) })
            }
            return
        }
        // préparation en cours (téléchargement) : elle échouera au site ; nouvel essai à la vérification suivante
        if (this.busy) return
        this.effacementEnCours = true
        try {
            if (await this.partieDejaOuverte()) {
                clearTimeout(this.attenteJeuExterne)
                this.attenteJeuExterne = setTimeout(() => { this.effacerJeu().catch(() => {}) }, ATTENTE_JEU_MS)
                this.attenteJeuExterne.unref?.()
                return
            }
            const resultat = effacerDonneesJeu({
                racine: app.getPath('userData'), dossierCommun: Config.getCommonDirectory(),
                dossierInstances: Config.getInstanceDirectory(), serveur: this.config.serverId
            })
            for (const refuse of resultat.refuses) console.warn('[HeraliZ/ban] Hors du dossier du launcher (ou lien), laissé tel quel :', refuse)
            // une cible refusée (dossier du jeu déplacé hors du launcher, lien) n'est pas effacée : jamais « fait »,
            // le pack reste bloqué sur ce PC (compte-banni.json) et l'effacement sera retenté
            if (resultat.refuses.length) {
                console.warn(`[HeraliZ/ban] ${resultat.refuses.length} dossier(s) du jeu hors du dossier du launcher : fichiers pas retirés.`)
                return
            }
            if (resultat.erreurs.length) {
                console.warn(`[HeraliZ/ban] ${resultat.erreurs.length} fichier(s) du jeu pas encore effacé(s) (verrouillés ?) : nouvel essai plus tard.`, resultat.erreurs.slice(0, 5).map(e => e.code).join(', '))
                return
            }
            ecrireBan({ ...lireBan(), effacement: 'fait' })
            this.fichiersEffaces = true
            console.warn(`[HeraliZ/ban] Fichiers du jeu HeraliZ retirés de ce PC (${resultat.effaces.length} élément(s)).`)
        } finally {
            this.effacementEnCours = false
        }
    }

    // Au démarrage du launcher : effacement laissé en attente la dernière fois (jeu encore ouvert, fichier verrouillé).
    reprendreEffacement() {
        if (lireBan()?.effacement === 'a_faire') this.effacerJeu().catch(error => console.error('[HeraliZ/ban]', error?.code || error?.message))
    }

    // Lien ouvert dans le navigateur depuis le panneau Discord : « lier » (page du site) ou « invitation » (Discord).
    lienDiscord(nom) {
        return nom === 'lier' ? this.discord?.lien || null : nom === 'invitation' ? this.discord?.invitation || null : null
    }

    logout() {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour te déconnecter.')
        const saved = Config.getSelectedAccount()
        const jeton = this.jeton || dechiffrer(saved?.compte?.jeton)
        // Le site oublie aussi le jeton ; sans attendre sa réponse (le launcher, lui, l'oublie tout de suite).
        if (jeton && this.compteSite) this.compteSite.deconnexion(jeton).catch(() => {})
        this.oublierCompte()
        return this.getState()
    }

    saveSettings(value) {
        if (this.busy) throw new Error('Les réglages sont verrouillés pendant la préparation et la partie.')
        if (!value || !Number.isInteger(value.ramGB) || value.ramGB < 2 || value.ramGB > this.getState().settings.maxRAM || typeof value.fullscreen !== 'boolean') throw new Error('Réglages invalides.')
        Config.setMinRAM(this.config.serverId, '2G')
        Config.setMaxRAM(this.config.serverId, `${value.ramGB}G`)
        Config.setFullscreen(value.fullscreen)
        Config.save()
        return this.getState()
    }

    async getServerStatus() {
        if (!this.config.serverAddress) return { state: 'unconfigured' }
        if (this.statusPending) return this.statusPending
        if (this.statusAt && Date.now() - this.statusAt < 15000) return this.status
        this.statusPending = (async () => {
            try {
                const [host, port = '25565'] = this.config.serverAddress.split(':')
                const [result, ping] = await Promise.all([getServerStatus(763, host, Number(port)), mesurerPing(host, Number(port))])
                this.status = { state: 'online', online: result.players.online, max: result.players.max, ping }
            } catch { this.status = { state: 'unreachable' } }
            this.statusAt = Date.now()
            this.statusPending = null
            return this.status
        })()
        return this.statusPending
    }

    async loadDistribution() {
        if (!this.config.serverAddress || !this.config.distributionUrl) throw new Error('Le serveur est en préparation. Son adresse et son pack de jeu doivent encore être configurés par l’équipe HeraliZ.')
        // Comptes du site : le pack vient du site, pour le compte connecté, avec des liens signés pour quelques heures
        // (sans compte, le site refuse les fichiers du pack).
        if (this.compteSite) {
            const brut = await this.compteSite.pack(this.jetonActuel())
            verifierPack(brut, brut?.heralizSignature, this.config)
            const raw = validateDistribution(brut, this.config)
            return new HeliosDistribution(raw, Config.getCommonDirectory(), Config.getInstanceDirectory())
        }
        const url = httpsUrl(this.config.distributionUrl)
        // No demo-server fallback and no unvalidated remote content in the UI.
        const response = got.stream(url, {
            timeout: { request: 15000 }, retry: { limit: 1 },
            hooks: { beforeRedirect: [options => { httpsUrl(options.url.href) }] }
        })
        const chunks = []
        let size = 0
        for await (const chunk of response) {
            size += chunk.length
            if (size > 32 * 1024 * 1024) { response.destroy(); throw new Error('La distribution est trop volumineuse.') }
            chunks.push(chunk)
        }
        const brut = JSON.parse(Buffer.concat(chunks).toString('utf8'))
        // sans compte du site (essais) : la signature est le fichier distribution.sig à côté de la distribution
        let signature = null
        if (chargerClePublique(this.config.distributionPublicKey)) {
            try {
                signature = (await got(url.href.replace(/distribution\.json$/, 'distribution.sig'), { timeout: { request: 15000 }, retry: { limit: 1 } }).text()).trim()
            } catch { /* absente : refusée ci-dessous */ }
        }
        verifierPack(brut, signature, this.config)
        const raw = validateDistribution(brut, this.config)
        return new HeliosDistribution(raw, Config.getCommonDirectory(), Config.getInstanceDirectory())
    }

    async ensureJava(server) {
        this.emit('java', 'Recherche de Java 17…')
        const options = server.effectiveJavaOptions
        let executable = Config.getJavaExecutable(this.config.serverId)
        const valid = executable && await validateSelectedJvm(ensureJavaDirIsRoot(executable), '17.x').catch(() => null)
        if (!valid) {
            const found = await discoverBestJvmInstallation(Config.getDataDirectory(), '17.x')
            if (found) executable = javaExecFromRoot(found.path)
            else {
                this.emit('java', 'Téléchargement de Java 17…', 0)
                const asset = await latestOpenJDK(17, Config.getDataDirectory(), options.distribution)
                if (!asset) throw new Error('Java 17 est indisponible. Réessaie plus tard.')
                httpsUrl(asset.url)
                await downloadFile(asset.url, asset.path, ({ transferred }) => this.emit('java', 'Téléchargement de Java 17…', transferred / asset.size * 100))
                if (!await validateLocalFile(asset.path, asset.algo, asset.hash)) {
                    await fs.remove(asset.path)
                    throw new Error('La vérification de Java a échoué. Le fichier a été supprimé.')
                }
                this.emit('java', 'Installation de Java 17…')
                executable = await extractJdk(asset.path)
            }
        }
        if (!await validateSelectedJvm(ensureJavaDirIsRoot(executable), '17.x')) throw new Error('Java 17 n’a pas pu être validé.')
        Config.setJavaExecutable(this.config.serverId, executable)
        Config.save()
    }

    async repair(distro, server) {
        const processors = [
            new MojangIndexProcessor(Config.getCommonDirectory(), '1.20.1'),
            new DistributionIndexProcessor(Config.getCommonDirectory(), distro, server.rawServer.id)
        ]
        const assets = []
        this.emit('verify', 'Vérification des fichiers du jeu…')
        for (const processor of processors) {
            await processor.init()
            const invalid = await processor.validate(async () => {})
            assets.push(...Object.values(invalid).flat())
        }
        if (assets.length) {
            assets.forEach(asset => httpsUrl(asset.url))
            const total = getExpectedDownloadSize(assets)
            const debut = Date.now()
            await downloadQueue(assets, bytes => this.emit('download', 'Téléchargement du jeu et des mods…', total ? bytes / total * 100 : 0, detailTelechargement(bytes, total, debut)))
            this.emit('verify', 'Contrôle des fichiers téléchargés…')
            // Check BEFORE postDownload, which may execute a Forge installer.
            for (const asset of assets) {
                if (!await validateLocalFile(asset.path, asset.algo, asset.hash)) {
                    await fs.remove(asset.path)
                    throw new Error('Un téléchargement est corrompu. Relance la préparation pour le réparer.')
                }
            }
        }
        this.emit('install', 'Préparation de Forge 1.20.1…')
        for (const processor of processors) await processor.postDownload()
        return { version: await processors[0].getVersionJson(), loader: await processors[1].loadModLoaderVersionJson(server) }
    }

    // Accord pour les captures d'écran de l'anticheat, demandé à chaque ouverture du launcher (renderer.js), avec une
    // seconde confirmation. Refus : rien n'est gardé, le launcher se ferme.
    accordCapture(oui) {
        if (oui !== true) return this.getState()
        Config.setAccordCapture(true)
        Config.save()
        return this.getState()
    }

    async launch() {
        if (this.busy) throw new Error('Une préparation ou une partie est déjà en cours.')
        const saved = Config.getSelectedAccount()
        if (this.compteSite && !saved?.compte) throw new Error('Connecte-toi avec ton compte HeraliZ.')
        if (!saved || saved.rulesAccepted !== true) throw new Error('Choisis ton pseudo et accepte le règlement.')
        if (!Config.getAccordCapture()) throw new Error('Accepte les captures d’écran de l’anticheat pour jouer (redémarre le launcher).')
        this.busy = true
        try {
            if (await this.partieDejaOuverte()) throw new Error('Minecraft est déjà ouvert. Ferme le jeu avant de relancer une partie.')
            if (this.compteSite) {
                this.emit('prepare', 'Vérification de ton compte HeraliZ…')
                const etat = await this.verifierCompte()
                if (etat.sessionExpiree) throw new Error('Ta session a expiré : reconnecte-toi avec ton compte HeraliZ.')
                // compte Discord lié, membre du Discord, pas banni, accepté par le staff : sinon, pas de partie
                const discord = await this.etatDiscord()
                if (discord.sessionExpiree) throw new Error('Ta session a expiré : reconnecte-toi avec ton compte HeraliZ.')
                if (discord.discord && discord.discord.statut !== 'ok') throw erreurDiscord(discord.discord.statut)
                // compte banni vu sur ce PC : pas de pack sans un « ok » tout frais du site (il lève ce verrou)
                if (lireBan()) throw new Error('Le site HeraliZ doit d’abord confirmer l’accès de ton compte au jeu. Réessaie dans un instant.')
            }
            const profile = createOfflineProfile(Config.getSelectedAccount().displayName)
            this.emit('prepare', 'Chargement du pack HeraliZ…')
            const distro = await this.loadDistribution()
            const server = distro.getServerById(this.config.serverId)
            await this.ensureJava(server)
            Config.setModConfiguration(this.config.serverId, { id: this.config.serverId, mods: {} })
            const settings = this.getState().settings
            Config.setMinRAM(this.config.serverId, '2G')
            Config.setMaxRAM(this.config.serverId, `${settings.ramGB}G`)
            Config.save()
            const { version, loader } = await this.repair(distro, server)
            // anciens dossiers de gunpacks (noms d'ArkaniaZ, 26/10) : retirés du dossier du jeu, jamais bloquant
            try {
                const r = retirerAnciensGunpacks({ dossierInstances: Config.getInstanceDirectory(), serveur: this.config.serverId })
                if (r.erreurs.length) console.warn('[HeraliZ/gunpacks] anciens dossiers pas tous retirés', r.erreurs.map(e => e.code))
            } catch (erreur) {
                console.warn('[HeraliZ/gunpacks]', erreur?.message)
            }
            this.emit('launch', 'Démarrage de Minecraft…')
            const jetonSpartan = await this.jetonSpartan()
            const pb = new ProcessBuilder(server, version, loader, profile, app.getVersion(), jetonSpartan)
            // Gardien (gardien.js) : clé de ce lancement, donnée au jeu par son environnement
            const cleGardien = Gardien.nouvelleCle()
            pb.cleGardien = cleGardien
            const jeu = pb.build()
            this.game = jeu
            jeu.once('spawn', () => {
                this.noterPartie(jeu.pid)
                this.arreterGardien()
                try {
                    this.gardien = new Gardien({
                        dossierJeu: pb.gameDir, pid: jeu.pid, cle: cleGardien,
                        dossiersJars: [path.join(pb.gameDir, 'mods'), path.join(Config.getCommonDirectory(), 'modstore')]
                    }).demarrer()
                } catch (erreur) {
                    console.error('[HeraliZ/gardien]', erreur?.code || erreur?.message)
                }
                this.emit('running', 'Minecraft est lancé. Bonne survie !', 100, 'Tu peux fermer la fenêtre : l’anticheat HeraliZ reste actif en arrière-plan tant que le jeu est ouvert.')
            })
            jeu.once('error', error => {
                this.arreterGardien()
                this.oublierPartie(jeu.pid)
                this.game = null
                this.busy = false
                this.emit('error', `Impossible de démarrer Minecraft : ${sansChemins(error.message)}`)
            })
            jeu.once('close', code => {
                this.arreterGardien()
                this.oublierPartie(jeu.pid)
                this.game = null
                this.busy = false
                this.emit(code === 0 ? 'idle' : 'error', code === 0 ? 'Partie terminée. Prêt à repartir ?' : 'Minecraft s’est fermé avec une erreur. Consulte le dossier logs du jeu.')
            })
            return { started: true }
        } catch (error) {
            this.busy = false
            // pack ou jeton refusé par le site à cause du Discord : l'état suit, le panneau s'ouvre chez le joueur
            if (typeof error?.discord === 'string') this.discord = { ...(this.discord || {}), statut: error.discord }
            // compte banni (pack, jeton de lancement ou compte refusés par le site) : fichiers du jeu retirés de ce PC
            if (error?.banni) {
                if (this.discord?.statut !== 'banni') this.discord = { ...(this.discord || {}), statut: 'banni' }
                await this.signalerBan(saved?.compte?.id ?? null).catch(erreur => console.error('[HeraliZ/ban]', erreur?.code || erreur?.message))
                if (!error.discord) error.discord = 'banni'
            }
            this.emit('error', sansChemins(error.message) || 'La préparation a échoué. Réessaie.')
            throw error
        }
    }

    noterPartie(pid) {
        if (!Number.isInteger(pid) || pid <= 0) return
        try { fs.writeFileSync(fichierPartie(), JSON.stringify({ pid, depuis: Date.now() })) } catch { /* sans mémoire */ }
    }

    oublierPartie(pid) {
        try {
            if (JSON.parse(fs.readFileSync(fichierPartie(), 'utf8'))?.pid === pid) fs.removeSync(fichierPartie())
        } catch { /* rien de noté */ }
    }

    // Un Minecraft lancé par ce launcher (puis laissé ouvert, launcher fermé) tourne-t-il encore ? Dans le doute (numéro
    // repris par un autre programme, partie trop ancienne, vérification impossible) : non, le joueur n'est jamais bloqué.
    async partieDejaOuverte() {
        let lu
        try { lu = JSON.parse(fs.readFileSync(fichierPartie(), 'utf8')) } catch (erreur) {
            if (erreur?.code !== 'ENOENT') fs.remove(fichierPartie()).catch(() => {})
            return false
        }
        const recente = Number.isInteger(lu?.pid) && lu.pid > 0 && Number.isFinite(lu.depuis) && Date.now() - lu.depuis < PARTIE_MAX_MS
        const ouverte = recente && estJava(await this.nomProcessus(lu.pid).catch(() => null))
        if (!ouverte) await fs.remove(fichierPartie()).catch(() => {})
        return ouverte
    }

    /** Le Gardien suit-il une partie ? Tant que oui, le launcher reste ouvert en arrière-plan (index.js). */
    gardienActif() {
        return Boolean(this.gardien && !this.gardien.arrete)
    }

    arreterGardien() {
        this.gardien?.arreter()
        this.gardien = null
    }

    // Fermeture du launcher : le jeu, lancé détaché, n'est pas arrêté. Le launcher cesse seulement de le suivre (le
    // Gardien aussi : le serveur le verra « muet »).
    dispose() {
        this.arreterGardien()
        this.game?.unref?.()
        this.busy = false
    }
}

module.exports = LauncherService
module.exports.cibleJeu = cibleJeu
module.exports.nomProcessus = nomProcessus
