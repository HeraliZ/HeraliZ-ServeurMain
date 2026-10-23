const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const { verifierPack, textePack } = require('../app/assets/js/signature')

const SIGNER = path.join(__dirname, '..', 'ci', 'signer.mjs')

function cles() {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519')
    return {
        pub: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
        priv: privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64')
    }
}

// Distribution comme Nebula la fabrique (forme réduite), liens vers le dossier du pack sur le site.
function distribution() {
    const art = (nom, md5, size) => ({ size, MD5: md5, url: `https://heraliz.fr/launcher/${nom}` })
    return {
        version: '1.0.0',
        servers: [{
            id: 'heraliz-1.20.1', minecraftVersion: '1.20.1', address: 'play.heraliz.fr',
            modules: [
                { id: 'net.minecraftforge:forge:1.20.1-47.4.0', type: 'ForgeHosted', artifact: art('forge.jar', 'a'.repeat(32), 10),
                    subModules: [{ id: '1.20.1-47.4.0', type: 'VersionManifest', artifact: art('forge.json', 'b'.repeat(32), 2) }] },
                { id: 'fr.heraliz:spartan:2.7.6@jar', type: 'ForgeMod', artifact: art('mods/spartan.jar', 'C'.repeat(32), 300) },
                { id: 'fr.heraliz:loot:0.9.11@jar', type: 'ForgeMod', artifact: art('mods/loot.jar', 'd'.repeat(32), 400) }
            ]
        }]
    }
}

// Signe comme la mise en ligne (ci/signer.mjs --pack), puis sert comme le site (PackController) : liens réécrits
// (?e=…&s=…), signature jointe, aller-retour JSON.
function signerEtServir(distro, priv, pub) {
    const dossier = fs.mkdtempSync(path.join(os.tmpdir(), 'pack-'))
    const fichier = path.join(dossier, 'distribution.json')
    fs.writeFileSync(fichier, JSON.stringify(distro, null, 2))
    execFileSync('node', [SIGNER, '--pack', fichier, pub], { env: { ...process.env, LAUNCHER_SIGN_KEY: priv } })
    const signature = fs.readFileSync(path.join(dossier, 'distribution.sig'), 'utf8').trim()
    const servi = JSON.parse(fs.readFileSync(fichier, 'utf8'))
    const reecrire = mods => mods.forEach(m => {
        m.artifact.url += '?e=1700000000&s=AbC-_x'
        if (m.subModules) reecrire(m.subModules)
    })
    reecrire(servi.servers[0].modules)
    servi.heralizSignature = signature
    return JSON.parse(JSON.stringify(servi))
}

test('pack signé, liens réécrits par le site : accepté, et le champ de la signature est retiré', () => {
    const { pub, priv } = cles()
    const servi = signerEtServir(distribution(), priv, pub)
    verifierPack(servi, servi.heralizSignature, { distributionPublicKey: pub })
    assert.equal(servi.heralizSignature, undefined)
})

test('pack falsifié : mod remplacé, ajouté ou retiré => refusé', () => {
    const { pub, priv } = cles()
    const config = { distributionPublicKey: pub }

    const remplace = signerEtServir(distribution(), priv, pub)
    remplace.servers[0].modules[2].artifact.MD5 = 'e'.repeat(32)
    assert.throws(() => verifierPack(remplace, remplace.heralizSignature, config), /pas signé par HeraliZ/)

    const ajoute = signerEtServir(distribution(), priv, pub)
    ajoute.servers[0].modules.push({ id: 'pirate:cheat:1@jar', type: 'ForgeMod', artifact: { size: 5, MD5: 'f'.repeat(32), url: 'https://heraliz.fr/launcher/x.jar' } })
    assert.throws(() => verifierPack(ajoute, ajoute.heralizSignature, config), /pas signé/)

    const retire = signerEtServir(distribution(), priv, pub)
    retire.servers[0].modules.pop()
    assert.throws(() => verifierPack(retire, retire.heralizSignature, config), /pas signé/)

    const adresse = signerEtServir(distribution(), priv, pub)
    adresse.servers[0].address = 'pirate.example'
    assert.throws(() => verifierPack(adresse, adresse.heralizSignature, config), /pas signé/)
})

test('pack sans signature, ou signé par une autre clé => refusé', () => {
    const { pub, priv } = cles()
    const autre = cles()
    const sans = JSON.parse(JSON.stringify(distribution()))
    assert.throws(() => verifierPack(sans, undefined, { distributionPublicKey: pub }), /pas signé/)
    const servi = signerEtServir(distribution(), autre.priv, autre.pub)
    assert.throws(() => verifierPack(servi, servi.heralizSignature, { distributionPublicKey: pub }), /pas signé/)
    void priv
})

test('sans clé publique réglée : pack accepté comme avant', () => {
    const brut = distribution()
    verifierPack(brut, undefined, { distributionPublicKey: '' })
    verifierPack(brut, undefined, {})
})

test('texte du pack : l’ordre des modules et la casse du MD5 ne comptent pas, les liens non plus', () => {
    const a = distribution()
    const b = distribution()
    b.servers[0].modules.reverse()
    b.servers[0].modules.forEach(m => { m.artifact.MD5 = m.artifact.MD5.toUpperCase(); m.artifact.url = 'https://ailleurs.example/' + m.id })
    assert.equal(textePack(a), textePack(b))
})

test('signer.mjs --pack refuse une clé privée qui ne va pas avec la clé publique du launcher', () => {
    const { priv } = cles()
    const autre = cles()
    const dossier = fs.mkdtempSync(path.join(os.tmpdir(), 'pack-'))
    const fichier = path.join(dossier, 'distribution.json')
    fs.writeFileSync(fichier, JSON.stringify(distribution()))
    assert.throws(() => execFileSync('node', [SIGNER, '--pack', fichier, autre.pub], { env: { ...process.env, LAUNCHER_SIGN_KEY: priv }, stdio: 'pipe' }))
    assert.equal(fs.existsSync(path.join(dossier, 'distribution.sig')), false)
})
