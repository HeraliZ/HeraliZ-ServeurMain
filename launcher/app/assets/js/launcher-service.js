const { app } = require('electron')
const fs = require('fs-extra')
const path = require('node:path')
const os = require('node:os')
const net = require('node:net')
const dns = require('node:dns').promises
const got = require('got')
const { HeliosDistribution, validateLocalFile } = require('helios-core/common')
const { DistributionIndexProcessor, MojangIndexProcessor, downloadQueue, downloadFile, getExpectedDownloadSize } = require('helios-core/dl')
const { validateSelectedJvm, ensureJavaDirIsRoot, javaExecFromRoot, discoverBestJvmInstallation, latestOpenJDK, extractJdk } = require('helios-core/java')
const { getServerStatus } = require('helios-core/mojang')
const Config = require('./configmanager')
const ProcessBuilder = require('./processbuilder')
const { createOfflineProfile } = require('./offline-profile')
const { httpsUrl, validateDistribution } = require('./security')

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
    constructor(config, notify) {
        this.config = config
        this.notify = notify
        this.busy = false
        this.game = null
        this.progress = { busy: false, phase: 'idle', message: 'Prêt pour ta prochaine expédition.', percent: null, detail: null }
        Config.load()
        // Les anciennes versions écrivaient le mot de passe en clair dans le dossier du jeu, pour un module jamais
        // installé sur le serveur : on l'efface. Le mot de passe se choisit en jeu (/register, puis /login).
        fs.removeSync(path.join(Config.getInstanceDirectory(), config.serverId, '.sl_password'))
        // Never preserve or expose legacy Microsoft/Mojang credentials.
        const previous = Config.getSelectedAccount()
        const profile = previous?.type === 'offline' ? previous : null
        for (const key of Object.keys(Config.getAuthAccounts())) Config.removeAuthAccount(key)
        if (profile) {
            try { Config.setOfflineProfile(createOfflineProfile(profile.displayName), profile.rulesAccepted === true) } catch { /* invalid local profile: ask again */ }
        }
        Config.setClientToken(null)
        Config.setSelectedServer(config.serverId)
        Config.ensureJavaConfig(config.serverId, { suggestedMajor: 17 })
        Config.setLaunchDetached(false)
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
            profile: profile ? { username: profile.displayName, rulesAccepted: profile.rulesAccepted === true } : null,
            settings: { ramGB: Math.min(maxRAM, Math.max(2, ramGB)), maxRAM, totalRAM, fullscreen: Config.getFullscreen(), autoConnect: Config.getAutoConnect() },
            configured: Boolean(this.config.serverAddress && this.config.distributionUrl),
            discordConfigured: Boolean(this.config.discordUrl), progress: this.progress
        }
    }

    saveProfile(value) {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour changer de profil.')
        if (!value || value.acceptedRules !== true) throw new Error('Tu dois accepter le règlement pour continuer.')
        const profile = createOfflineProfile(value.username)
        Config.setOfflineProfile(profile, true)
        Config.save()
        return this.getState()
    }

    logout() {
        if (this.busy) throw new Error('Attends la fin de la préparation ou de la partie pour te déconnecter.')
        for (const key of Object.keys(Config.getAuthAccounts())) Config.removeAuthAccount(key)
        Config.save()
        return this.getState()
    }

    saveSettings(value) {
        if (this.busy) throw new Error('Les réglages sont verrouillés pendant la préparation et la partie.')
        if (!value || !Number.isInteger(value.ramGB) || value.ramGB < 2 || value.ramGB > this.getState().settings.maxRAM || typeof value.fullscreen !== 'boolean' || typeof value.autoConnect !== 'boolean') throw new Error('Réglages invalides.')
        Config.setMinRAM(this.config.serverId, '2G')
        Config.setMaxRAM(this.config.serverId, `${value.ramGB}G`)
        Config.setFullscreen(value.fullscreen)
        Config.setAutoConnect(value.autoConnect)
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

    async launch() {
        if (this.busy) throw new Error('Une préparation ou une partie est déjà en cours.')
        const saved = Config.getSelectedAccount()
        if (!saved || saved.rulesAccepted !== true) throw new Error('Choisis ton pseudo et accepte le règlement.')
        const profile = createOfflineProfile(saved.displayName)
        this.busy = true
        try {
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
            this.game = new ProcessBuilder(server, version, loader, profile, app.getVersion()).build()
            this.game.once('spawn', () => this.emit('running', 'Minecraft est lancé. Bonne survie !', 100))
            this.game.once('error', error => {
                this.game = null
                this.busy = false
                this.emit('error', `Impossible de démarrer Minecraft : ${error.message}`)
            })
            this.game.once('close', code => {
                this.game = null
                this.busy = false
                this.emit(code === 0 ? 'idle' : 'error', code === 0 ? 'Partie terminée. Prêt à repartir ?' : 'Minecraft s’est fermé avec une erreur. Consulte le dossier logs du jeu.')
            })
            return { started: true }
        } catch (error) {
            this.busy = false
            this.emit('error', error.message || 'La préparation a échoué. Réessaie.')
            throw error
        }
    }

    dispose() {
        if (this.game) {
            this.game.stdout?.destroy()
            this.game.stderr?.destroy()
            this.game.unref()
        }
        this.busy = false
    }
}

module.exports = LauncherService
module.exports.cibleJeu = cibleJeu
