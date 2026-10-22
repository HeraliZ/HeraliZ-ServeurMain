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
const { createOfflineProfile } = require('./offline-profile')
const { httpsUrl, validateDistribution, sansChemins } = require('./security')
const { CompteSite } = require('./compte-site')
const Spartan = require('./spartan')

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
            accordCapture: Config.getAccordCapture()
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
        const resultat = await this.compteSite.connexion(value.email, value.password, value.code)
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
        return this.compteSite.jetonJeu(this.jetonActuel(), Spartan.empreinteMods(dossierMods))
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
            try { compte = await this.compteSite.verifier(jeton) } catch { return { ...this.getState(), horsLigne: true } }
        }
        if (!compte) {
            this.oublierCompte()
            return { ...this.getState(), sessionExpiree: true }
        }
        this.jeton = jeton
        if (compte.username !== saved.displayName) this.enregistrerCompte({ ...compte, jeton }, saved.rulesAccepted === true)
        return this.getState()
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
            const raw = validateDistribution(await this.compteSite.pack(this.jetonActuel()), this.config)
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
        const raw = validateDistribution(JSON.parse(Buffer.concat(chunks).toString('utf8')), this.config)
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
            this.emit('launch', 'Démarrage de Minecraft…')
            const jetonSpartan = await this.jetonSpartan()
            const jeu = new ProcessBuilder(server, version, loader, profile, app.getVersion(), jetonSpartan).build()
            this.game = jeu
            jeu.once('spawn', () => {
                this.noterPartie(jeu.pid)
                this.emit('running', 'Minecraft est lancé. Bonne survie !', 100, 'Tu peux fermer le launcher : le jeu reste ouvert.')
            })
            jeu.once('error', error => {
                this.oublierPartie(jeu.pid)
                this.game = null
                this.busy = false
                this.emit('error', `Impossible de démarrer Minecraft : ${sansChemins(error.message)}`)
            })
            jeu.once('close', code => {
                this.oublierPartie(jeu.pid)
                this.game = null
                this.busy = false
                this.emit(code === 0 ? 'idle' : 'error', code === 0 ? 'Partie terminée. Prêt à repartir ?' : 'Minecraft s’est fermé avec une erreur. Consulte le dossier logs du jeu.')
            })
            return { started: true }
        } catch (error) {
            this.busy = false
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

    // Fermeture du launcher : le jeu, lancé détaché, n'est pas arrêté. Le launcher cesse seulement de le suivre.
    dispose() {
        this.game?.unref?.()
        this.busy = false
    }
}

module.exports = LauncherService
module.exports.cibleJeu = cibleJeu
module.exports.nomProcessus = nomProcessus
