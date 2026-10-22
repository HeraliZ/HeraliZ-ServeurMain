const test = require('node:test')
const assert = require('node:assert/strict')
const Module = require('node:module')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { createHash } = require('node:crypto')
const { EventEmitter } = require('node:events')
const { createOfflineProfile } = require('../app/assets/js/offline-profile')

const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-test-'))
const originalLoad = Module._load
const dl = require('helios-core/dl')
const fakeApp = { getPath: () => directory, getVersion: () => '1.3.0' }
Module._load = function(name, parent, isMain) {
    if (name === 'electron') return { app: fakeApp, shell: { openPath: async () => '' } }
    return originalLoad.call(this, name, parent, isMain)
}
const Config = require('../app/assets/js/configmanager')
const ProcessBuilder = require('../app/assets/js/processbuilder')
const LauncherService = require('../app/assets/js/launcher-service')
Module._load = originalLoad
test.after(() => fs.rmSync(directory, { recursive: true, force: true }))
const config = { serverId: 'heraliz-1.20.1', serverAddress: null, distributionUrl: null }

test('the game address follows the Minecraft SRV record, only for the default port', async () => {
    const dnsPromises = require('node:dns').promises
    const original = dnsPromises.resolveSrv
    const asked = []
    dnsPromises.resolveSrv = async name => { asked.push(name); return [{ name: 'jeu.example.net', port: 25570, priority: 0, weight: 5 }] }
    try {
        assert.deepEqual(await LauncherService.cibleJeu('play.heraliz.fr', 25565), { host: 'jeu.example.net', port: 25570 })
        assert.deepEqual(asked, ['_minecraft._tcp.play.heraliz.fr'])
        assert.deepEqual(await LauncherService.cibleJeu('play.heraliz.fr', 25570), { host: 'play.heraliz.fr', port: 25570 })
        assert.equal(asked.length, 1)
        dnsPromises.resolveSrv = async () => { throw Object.assign(new Error('no SRV record'), { code: 'ENODATA' }) }
        assert.deepEqual(await LauncherService.cibleJeu('play.heraliz.fr', 25565), { host: 'play.heraliz.fr', port: 25565 })
    } finally { dnsPromises.resolveSrv = original }
})

test('saved profile replaces Microsoft credentials and exposes no tokens', () => {
    Config.load()
    Config.addMicrosoftAuthAccount('previous', 'mc-secret', 'OldUser', 0, 'ms-secret', 'refresh-secret', 0)
    Config.save()
    const service = new LauncherService(config, () => {})
    assert.equal(service.getState().profile, null)
    assert.deepEqual(Config.getAuthAccounts(), {})
    assert.throws(() => service.saveProfile({ username: 'Survivant', acceptedRules: false }))
    service.saveProfile({ username: 'Survivant', acceptedRules: true })
    service.saveProfile({ username: 'Another', acceptedRules: true })
    assert.equal(Object.keys(Config.getAuthAccounts()).length, 1)
    const restarted = new LauncherService(config, () => {})
    assert.equal(restarted.getState().profile.username, 'Another')
    assert.equal(JSON.stringify(restarted.getState()).includes('accessToken'), false)
    assert.equal(fs.readFileSync(path.join(directory, 'config.json'), 'utf8').includes('refresh-secret'), false)
})

test('logout forgets the saved profile, also after a restart', () => {
    const service = new LauncherService(config, () => {})
    service.saveProfile({ username: 'Survivant', acceptedRules: true })
    assert.equal(service.logout().profile, null)
    assert.deepEqual(Config.getAuthAccounts(), {})
    assert.equal(new LauncherService(config, () => {}).getState().profile, null)
})

test('the launcher keeps no password and erases the old plain-text file', () => {
    Config.load()
    const legacy = path.join(Config.getInstanceDirectory(), config.serverId, '.sl_password')
    fs.mkdirSync(path.dirname(legacy), { recursive: true })
    fs.writeFileSync(legacy, 'Zone-Rouge 42')
    const service = new LauncherService(config, () => {})
    assert.equal(fs.existsSync(legacy), false)
    const state = service.saveProfile({ username: 'Survivant', acceptedRules: true, password: 'Zone-Rouge 42' })
    assert.equal(fs.existsSync(legacy), false)
    assert.deepEqual(state.profile, { username: 'Survivant', rulesAccepted: true, compte: false })
    assert.equal(JSON.stringify(state).includes('Zone-Rouge'), false)
    assert.equal(fs.readFileSync(path.join(directory, 'config.json'), 'utf8').includes('Zone-Rouge'), false)
})

test('the launcher offers no way to open the game folder', () => {
    const service = new LauncherService(config, () => {})
    assert.equal(service.openGameFolder, undefined)
    const preload = fs.readFileSync(path.join(__dirname, '..', 'app', 'preload.js'), 'utf8')
    const main = fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8')
    assert.equal(/folder|openPath|showItemInFolder/i.test(preload + main), false)
})

test('accord des captures redemandé à chaque ouverture du launcher', () => {
    Config.setAccordCapture(true)
    const service = new LauncherService(config, () => {})
    assert.equal(service.getState().accordCapture, false)
})

test('sans accord des captures d’écran, aucune partie ; l’accord est gardé', async () => {
    const service = new LauncherService(config, () => {})
    Config.setAccordCapture(false)
    assert.equal(service.getState().accordCapture, false)
    await assert.rejects(service.launch(), /captures d’écran/)
    assert.equal(service.busy, false)
    assert.equal(service.accordCapture(false).accordCapture, false)
    assert.equal(service.accordCapture(true).accordCapture, true)
})

test('unconfigured launch performs no downloads and releases the busy lock', async () => {
    const service = new LauncherService(config, () => {})
    service.accordCapture(true) // accord redemandé à chaque ouverture
    service.saveProfile({ username: 'Survivant', acceptedRules: true })
    await assert.rejects(service.launch(), /préparation/)
    assert.equal(service.busy, false)
    assert.equal(service.progress.phase, 'error')
    assert.equal((await service.getServerStatus()).state, 'unconfigured')
})

test('concurrent launches and settings edits are rejected during preparation', async () => {
    const service = new LauncherService(config, () => {})
    service.accordCapture(true)
    let unblock
    service.loadDistribution = () => new Promise((_resolve, reject) => { unblock = reject })
    const launch = service.launch()
    await assert.rejects(service.launch(), /déjà en cours/)
    assert.throws(() => service.saveSettings({ ramGB: 4, fullscreen: false, autoConnect: true }), /verrouillés/)
    assert.throws(() => service.saveProfile({ username: 'Other', acceptedRules: true }), /Attends/)
    assert.throws(() => service.logout(), /Attends/)
    while (!unblock) await new Promise(resolve => setImmediate(resolve))
    unblock(new Error('network failure'))
    await assert.rejects(launch, /network failure/)
    assert.equal(service.busy, false)
})

test('RAM and settings values are validated in the main process', () => {
    const service = new LauncherService(config, () => {})
    for (const value of [null, { ramGB: -1 }, { ramGB: 999 }, { ramGB: '4' }, { ramGB: 4.5 }, { ramGB: 4, fullscreen: 'false' }]) assert.throws(() => service.saveSettings(value))
    service.saveSettings({ ramGB: 2, fullscreen: false, autoConnect: true })
    assert.equal(service.getState().settings.ramGB, 2)
})

test('Minecraft 1.20.1 receives offline identity, quick play and no unresolved tokens', () => {
    Config.setFullscreen(false)
    Config.setAutoConnect(true)
    const server = { rawServer: { id: config.serverId, minecraftVersion: '1.20.1', autoconnect: true }, hostname: 'play.example.com', port: 25565 }
    const vanilla = { assets: '5', type: 'release', arguments: { jvm: ['-cp', '${classpath}'], game: ['--username', '${auth_player_name}', '--uuid', '${auth_uuid}', '--accessToken', '${auth_access_token}', '--clientId', '${clientid}', '--xuid', '${auth_xuid}', '--userType', '${user_type}', { rules: [{ action: 'allow', features: { has_custom_resolution: true } }], value: ['--width', '${resolution_width}'] }] } }
    const loader = { mainClass: 'cpw.mods.bootstraplauncher.BootstrapLauncher', arguments: { jvm: [], game: ['--launchTarget', 'forgeclient'] } }
    const before = JSON.stringify(vanilla)
    const profile = createOfflineProfile('Survivant')
    const jeton = 'A'.repeat(40) + '.' + 'B'.repeat(43)
    const builder = new ProcessBuilder(server, vanilla, loader, profile, '1.3.0', jeton)
    builder.classpathArg = () => ['classpath']
    const args = builder.constructJVMArguments([], directory)
    // jeton signé par le site : dans l'environnement du jeu, jamais sur la ligne de commande
    assert.equal(args.some(a => String(a).includes(jeton) || String(a).startsWith('-Dspartan.')), false)
    const env = builder.environnement({ Path: 'x', JAVA_TOOL_OPTIONS: '-javaagent:triche.jar' })
    assert.equal(env.SPARTAN_LAUNCH_TOKEN, jeton)
    assert.equal(env.JAVA_TOOL_OPTIONS, undefined)
    assert.equal(env.Path, 'x')
    const sansJeton = new ProcessBuilder(server, vanilla, loader, profile, '1.3.0')
    sansJeton.classpathArg = () => ['classpath']
    assert.equal(sansJeton.constructJVMArguments([], directory).some(a => String(a).startsWith('-Dspartan.')), false)
    assert.equal(sansJeton.environnement({}).SPARTAN_LAUNCH_TOKEN, undefined)
    assert.equal(args[args.indexOf('--username') + 1], 'Survivant')
    assert.equal(args[args.indexOf('--uuid') + 1], profile.uuid)
    assert.equal(args[args.indexOf('--accessToken') + 1], '0')
    // pas de connexion directe : l'écran titre HeraliZ a son bouton « Jouer »
    assert.equal(args.includes('--quickPlayMultiplayer') || args.includes('--server'), false)
    assert.equal(args.some(arg => String(arg).includes('${')), false)
    assert.ok(args.includes('-XX:+DisableAttachMechanism'), 'protection anti-injection de Spartan')
    assert.equal(JSON.stringify(vanilla), before)
    assert.deepEqual(builder.constructJVMArguments([], directory), args)
})

test('same-size corrupt download is rejected before any post-download processing', async () => {
    const assetPath = path.join(directory, 'bad.jar')
    const asset = { id: 'bad', path: assetPath, size: 4, url: 'https://example.com/bad.jar', algo: 'md5', hash: createHash('md5').update('good').digest('hex') }
    let processed = false
    const processor = class {
        async init() {}
        async validate() { return { files: [asset] } }
        async postDownload() { processed = true }
    }
    Module._load = function(name, parent, isMain) {
        if (name === 'electron') return { app: fakeApp }
        if (name === 'helios-core/dl') return { ...dl, MojangIndexProcessor: processor, DistributionIndexProcessor: processor, downloadQueue: async () => fs.writeFileSync(assetPath, 'evil') }
        return originalLoad.call(this, name, parent, isMain)
    }
    delete require.cache[require.resolve('../app/assets/js/launcher-service')]
    const TestService = require('../app/assets/js/launcher-service')
    Module._load = originalLoad
    const service = new TestService(config, () => {})
    await assert.rejects(service.repair({}, { rawServer: { id: config.serverId } }), /corrompu/)
    assert.equal(processed, false)
    assert.equal(fs.existsSync(assetPath), false)
})

test('launch succeeds through the preparation pipeline and unlocks on game exit', async () => {
    const child = Object.assign(new EventEmitter(), { pid: 4242 })
    const build = ProcessBuilder.prototype.build
    ProcessBuilder.prototype.build = () => child
    const service = new LauncherService(config, () => {})
    service.accordCapture(true)
    service.loadDistribution = async () => ({ getServerById: () => ({ rawServer: { id: config.serverId } }) })
    service.ensureJava = async () => {}
    service.repair = async () => ({ version: {}, loader: {} })
    const partie = path.join(directory, 'partie-en-cours.json')
    try {
        assert.deepEqual(await service.launch(), { started: true })
        child.emit('spawn')
        assert.equal(service.progress.phase, 'running')
        assert.match(service.progress.detail, /fermer le launcher/)
        assert.equal(service.busy, true)
        // partie notée : un launcher rouvert pendant le jeu ne relance pas un second Minecraft
        assert.equal(JSON.parse(fs.readFileSync(partie, 'utf8')).pid, 4242)
        child.emit('close', 0)
        assert.equal(service.busy, false)
        assert.equal(service.progress.phase, 'idle')
        assert.equal(fs.existsSync(partie), false)
    } finally { ProcessBuilder.prototype.build = build }
})

test('launcher rouvert pendant une partie : pas de second Minecraft ; dans le doute, le joueur n’est pas bloqué', async () => {
    const partie = path.join(directory, 'partie-en-cours.json')
    const noter = (pid, depuis = Date.now()) => fs.writeFileSync(partie, JSON.stringify({ pid, depuis }))
    const service = nom => { const s = new LauncherService(config, () => {}, { nomProcessus: async () => nom }); s.accordCapture(true); return s }
    assert.equal(await service('javaw.exe').partieDejaOuverte(), false, 'rien de noté')
    noter(4242)
    const ouvert = service('javaw.exe')
    ouvert.saveProfile({ username: 'Survivant', acceptedRules: true })
    await assert.rejects(ouvert.launch(), /déjà ouvert/)
    assert.equal(ouvert.busy, false)
    assert.equal(await service('java').partieDejaOuverte(), true)
    // numéro repris par un autre programme, processus disparu, partie trop ancienne : la note est oubliée
    assert.equal(await service('chrome').partieDejaOuverte(), false)
    assert.equal(fs.existsSync(partie), false)
    noter(4242)
    assert.equal(await service(null).partieDejaOuverte(), false)
    noter(4242, Date.now() - 3 * 24 * 3600 * 1000)
    assert.equal(await service('java').partieDejaOuverte(), false)
    fs.writeFileSync(partie, 'illisible')
    assert.equal(await service('java').partieDejaOuverte(), false)
})

test('nom du processus : celui d’un programme en cours, rien pour un processus terminé', async () => {
    const nom = await LauncherService.nomProcessus(process.pid)
    assert.equal(typeof nom, 'string')
    assert.ok(nom.length > 0 && !/^javaw?(\.exe)?$/i.test(nom), nom)
    const fini = require('node:child_process').spawn(process.execPath, ['-e', ''], { stdio: 'ignore' })
    await new Promise(resolve => fini.once('exit', resolve))
    assert.equal(await LauncherService.nomProcessus(fini.pid), null)
})

test('le jeu est lancé détaché, sa console va dans logs/console-jeu.log, sans variable d’injection', async () => {
    new LauncherService(config, () => {})
    Config.setJavaExecutable(config.serverId, process.execPath)
    Config.setModConfiguration(config.serverId, { id: config.serverId, mods: {} })
    const server = { rawServer: { id: config.serverId, minecraftVersion: '1.20.1' }, modules: [] }
    const jeton = 'A'.repeat(40) + '.' + 'B'.repeat(43)
    const builder = new ProcessBuilder(server, {}, {}, createOfflineProfile('Survivant'), '1.3.0', jeton)
    const script = 'console.log(JSON.stringify({ outil: process.env.JAVA_TOOL_OPTIONS || null, jeton: process.env.SPARTAN_LAUNCH_TOKEN || null })); console.error("erreur de démarrage")'
    builder.constructJVMArguments = () => ['-e', script]
    builder.constructModList = () => []
    const spawn = require('node:child_process').spawn
    const avant = { outil: process.env.JAVA_TOOL_OPTIONS, deprecation: process.throwDeprecation }
    let options = null
    require('node:child_process').spawn = (commande, args, opts) => { options = opts; return spawn(commande, args, opts) }
    process.env.JAVA_TOOL_OPTIONS = '-javaagent:triche.jar'
    // le jeu ne retient pas le launcher (unref) : le test, lui, attend sa fin
    const garde = setInterval(() => {}, 1000)
    try {
        const jeu = builder.build()
        const code = await new Promise((resolve, reject) => { jeu.once('close', resolve); jeu.once('error', reject) })
        assert.equal(code, 0)
        assert.equal(options.detached, true)
        assert.equal(options.stdio[0], 'ignore')
        assert.equal(typeof options.stdio[1], 'number')
        const journal = fs.readFileSync(path.join(Config.getInstanceDirectory(), config.serverId, 'logs', 'console-jeu.log'), 'utf8')
        assert.match(journal, /erreur de démarrage/)
        assert.deepEqual(JSON.parse(journal.split('\n')[0]), { outil: null, jeton })
    } finally {
        clearInterval(garde)
        require('node:child_process').spawn = spawn
        if (avant.outil === undefined) delete process.env.JAVA_TOOL_OPTIONS
        else process.env.JAVA_TOOL_OPTIONS = avant.outil
        process.throwDeprecation = avant.deprecation
    }
})

test('AppImage (Linux) : le jeu ne garde rien du dossier monté de l’image, qui disparaît avec le launcher', () => {
    const base = {
        HOME: '/home/x', APPIMAGE: '/home/x/HeraliZ-Launcher.AppImage', APPDIR: '/tmp/.mount_HeraliAbC', ARGV0: 'x', OWD: '/home/x',
        PATH: '/tmp/.mount_HeraliAbC:/tmp/.mount_HeraliAbC/usr/sbin:/usr/local/bin:/usr/bin',
        LD_LIBRARY_PATH: '/tmp/.mount_HeraliAbC/usr/lib', XDG_DATA_DIRS: '/tmp/.mount_HeraliAbC/usr/share/:/usr/share/gnome:/usr/share/',
        GSETTINGS_SCHEMA_DIR: '/tmp/.mount_HeraliAbC/usr/share/glib-2.0/schemas', LANG: 'fr_FR.UTF-8'
    }
    const copie = { ...base }
    assert.deepEqual(ProcessBuilder.sansAppImage(base), {
        HOME: '/home/x', PATH: '/usr/local/bin:/usr/bin', XDG_DATA_DIRS: '/usr/share/gnome:/usr/share/', LANG: 'fr_FR.UTF-8'
    })
    assert.deepEqual(base, copie)
    // un dossier voisin au nom proche n'est pas celui de l'image ; hors AppImage, rien ne change
    assert.equal(ProcessBuilder.sansAppImage({ ...base, PATH: '/tmp/.mount_HeraliAbCD/bin' }).PATH, '/tmp/.mount_HeraliAbCD/bin')
    const normal = { PATH: '/usr/bin', LD_LIBRARY_PATH: '/opt/lib' }
    assert.equal(ProcessBuilder.sansAppImage(normal), normal)
    // avec le reste des protections : variables d'injection retirées aussi
    const builder = new ProcessBuilder({ rawServer: { id: config.serverId } }, {}, {}, createOfflineProfile('Survivant'), '1.3.0')
    const env = builder.environnement({ ...base, LD_PRELOAD: '/tmp/x.so', JAVA_TOOL_OPTIONS: '-javaagent:x.jar' })
    assert.equal(env.LD_PRELOAD, undefined)
    assert.equal(env.JAVA_TOOL_OPTIONS, undefined)
    assert.equal(env.APPDIR, undefined)
})

test('les bibliothèques natives laissées par une partie (launcher fermé) sont effacées après 3 jours', () => {
    const parent = fs.mkdtempSync(path.join(directory, 'natives-'))
    const vieux = path.join(parent, 'a'.repeat(32))
    const recent = path.join(parent, 'b'.repeat(32))
    const autre = path.join(parent, 'autre')
    for (const d of [vieux, recent, autre]) fs.mkdirSync(d)
    fs.writeFileSync(path.join(vieux, 'lwjgl.so'), 'x')
    const ancien = new Date(Date.now() - 4 * 24 * 3600 * 1000)
    fs.utimesSync(vieux, ancien, ancien)
    fs.utimesSync(autre, ancien, ancien)
    ProcessBuilder.nettoyerNatives(parent)
    assert.equal(fs.existsSync(vieux), false)
    assert.equal(fs.existsSync(recent), true)
    assert.equal(fs.existsSync(autre), true, 'seuls les dossiers du launcher sont touchés')
    ProcessBuilder.nettoyerNatives(path.join(parent, 'absent'))
})
