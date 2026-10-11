const test = require('node:test')
const assert = require('node:assert/strict')
const { createOfflineProfile } = require('../app/assets/js/offline-profile')
const { validateDistribution, httpsUrl, safeRelativePath, isTrustedFrame } = require('../app/assets/js/security')

test('Minecraft offline UUID is stable, version 3, and case-sensitive', () => {
    const profile = createOfflineProfile('Notch')
    assert.equal(profile.uuid, 'b50ad385829d3141a2167e7d7539ba7f')
    assert.equal(profile.type, 'offline')
    assert.equal(profile.accessToken, '0')
    assert.notEqual(profile.uuid, createOfflineProfile('notch').uuid)
})

test('pseudo rejects whitespace, injection, Unicode and invalid lengths', () => {
    for (const value of ['', 'ab', 'a'.repeat(17), ' aaaa', 'a b', '../name', '<script>', 'name\n', 'ééé', null, {}, 123]) assert.throws(() => createOfflineProfile(value))
    assert.equal(createOfflineProfile('Abc_123').displayName, 'Abc_123')
    assert.equal(createOfflineProfile('a'.repeat(16)).displayName.length, 16)
})

test('only HTTPS without credentials is accepted', () => {
    assert.equal(httpsUrl('https://example.com/file.jar').protocol, 'https:')
    for (const url of ['http://example.com', 'file:///test', 'javascript:alert(1)', 'https://name:secret@example.com']) assert.throws(() => httpsUrl(url))
})

test('cross-platform path traversal is rejected', () => {
    for (const value of ['../x', 'mods/../../x', 'mods\\..\\x', 'C:\\Windows\\x', '/tmp/x', '\\\\host\\x', 'mods/x:stream', './x', 'mods//x']) assert.throws(() => safeRelativePath(value))
    safeRelativePath('mods/heraliz.jar')
})

const config = { serverId: 'heraliz-1.20.1', serverAddress: 'play.example.com' }
function distro() {
    const artifact = name => ({ url: `https://example.com/${name}`, MD5: '0'.repeat(32), size: 123 })
    // same shape as Nebula's output for Forge 1.20.1
    const forge = { id: 'net.minecraftforge:lowcodelanguage:1.20.1-47.4.0', type: 'ForgeHosted', artifact: artifact('lowcodelanguage.jar'), subModules: [
        { id: '1.20.1-47.4.0', type: 'VersionManifest', artifact: artifact('1.20.1-forge-47.4.0.json') },
        { id: 'net.minecraftforge:fmlcore:1.20.1-47.4.0', type: 'Library', classpath: true, artifact: artifact('fmlcore.jar') }
    ] }
    const tacz = { id: 'stg59.png', name: 'stg59.png', type: 'File', artifact: { ...artifact('stg59.png'), path: 'tacz/pack/assets/stg59.png' } }
    return { servers: [{ id: config.serverId, address: config.serverAddress, minecraftVersion: '1.20.1', modules: [forge, tacz] }] }
}

test('distribution is bound to HeraliZ and requires a Forge loader and hashes', () => {
    assert.equal(validateDistribution(distro(), config).servers[0].javaOptions.supported, '17.x')
    for (const mutate of [
        d => { d.servers[0].id = 'other-server' },
        d => { d.servers[0].address = 'attacker.example.com' },
        d => { d.servers[0].minecraftVersion = '1.21' },
        d => { d.servers[0].modules = [] },
        d => { d.servers[0].modules[0].artifact.MD5 = '' },
        d => { d.servers[0].modules[0].artifact.path = '../escape.jar' },
        d => { d.servers[0].modules[0].id = 'net...evil:forge:1' },
        d => { d.servers[0].modules[0].artifact.url = 'http://example.com/forge.jar' },
        d => { d.servers.push(d.servers[0]) },
        d => { d.servers[0].modules[0].id = 'net.evil:lowcodelanguage:1.20.1-47.4.0' },
        d => { d.servers[0].modules[0].id = 'net.minecraftforge:forge:1.20.4-49.0.1' },
        d => { d.servers[0].modules[0].subModules.shift() },
        d => { d.servers[0].modules[0].subModules[0].id = '1.21-51.0.0' },
        d => { d.servers[0].modules.push({ ...d.servers[0].modules[0] }) },
        d => { d.servers[0].modules[1].artifact.path = 'tacz/../../escape.png' }
    ]) { const value = distro(); mutate(value); assert.throws(() => validateDistribution(value, config)) }
    const classic = distro()
    classic.servers[0].modules[0].id = 'net.minecraftforge:forge:1.20.1-47.4.0'
    validateDistribution(classic, config)
})

test('IPC rejects foreign windows, subframes and navigated pages', () => {
    const url = 'file:///app/index.html'
    const contents = { mainFrame: { url } }
    const event = { sender: contents, senderFrame: contents.mainFrame }
    assert.equal(isTrustedFrame(event, contents, url), true)
    assert.equal(isTrustedFrame({ ...event, sender: {} }, contents, url), false)
    assert.equal(isTrustedFrame({ ...event, senderFrame: { url } }, contents, url), false)
    contents.mainFrame.url = 'https://example.com'
    assert.equal(isTrustedFrame(event, contents, url), false)
})

test('messages d’erreur montrés au joueur : jamais le nom de son compte Windows', () => {
    const { sansChemins } = require('../app/assets/js/security')
    const perso = 'C:\\Users\\Jean Dupont'
    assert.equal(sansChemins('EBUSY: open \'C:\\Users\\Jean Dupont\\AppData\\Roaming\\HeraliZ Launcher\\game\\x.jar\'', perso),
        'EBUSY: open \'~\\AppData\\Roaming\\HeraliZ Launcher\\game\\x.jar\'')
    assert.equal(sansChemins('échec : c:\\users\\jean dupont\\AppData\\y', perso), 'échec : ~\\AppData\\y')
    assert.equal(sansChemins('C:/Users/Jean Dupont/AppData/z', perso), '~/AppData/z')
    assert.equal(sansChemins('C:\\Users\\JEANDU~1\\AppData\\Local\\Temp\\a', perso), 'C:\\Users\\…\\AppData\\Local\\Temp\\a')
    assert.equal(sansChemins('/home/jean/.heraliz/b', '/home/jean'), '~/.heraliz/b')
    assert.equal(sansChemins('Mot de passe incorrect.', perso), 'Mot de passe incorrect.')
    assert.equal(sansChemins(undefined, perso), '')
})
