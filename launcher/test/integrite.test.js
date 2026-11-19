const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const crypto = require('node:crypto')
const { ecarterJarsEnTrop, ecarterPacksAjoutes, controlerJava, oublierJava, controlerEmpreintes } = require('../app/assets/js/integrite')

function dossierEssai() {
    return fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-integrite-'))
}

test('empreintes SHA-256 du pack : fichier identique accepté, changé ou absent signalé', async () => {
    const dossier = dossierEssai()
    const bon = path.join(dossier, 'bon.jar')
    const change = path.join(dossier, 'change.jar')
    const absent = path.join(dossier, 'absent.jar')
    fs.writeFileSync(bon, 'contenu officiel')
    fs.writeFileSync(change, 'contenu modifié')
    const sha = texte => crypto.createHash('sha256').update(texte).digest('hex')
    const ecarts = await controlerEmpreintes([
        { chemin: bon, sha256: sha('contenu officiel').toUpperCase() },
        { chemin: change, sha256: sha('contenu officiel') },
        { chemin: absent, sha256: sha('x') }
    ])
    assert.deepEqual(ecarts, [change, absent])
})

function lien(cible, chemin) {
    fs.symlinkSync(cible, chemin, process.platform === 'win32' ? 'junction' : 'dir')
}

test('les jars du dossier mods du jeu sont mis de côté, le reste du dossier ne bouge pas', () => {
    const base = dossierEssai()
    try {
        const mods = path.join(base, 'instances', 'heraliz', 'mods')
        fs.mkdirSync(mods, { recursive: true })
        fs.writeFileSync(path.join(mods, 'triche.jar'), 'x')
        fs.writeFileSync(path.join(mods, 'Autre.ZIP'), 'y')
        fs.writeFileSync(path.join(mods, 'lisez-moi.txt'), 'z')
        const r = ecarterJarsEnTrop({ dossierInstances: path.join(base, 'instances'), serveur: 'heraliz', maintenant: () => 42 })
        assert.deepEqual(r.ecartes.sort(), ['Autre.ZIP', 'triche.jar'])
        assert.deepEqual(fs.readdirSync(mods), ['lisez-moi.txt'])
        const cote = path.join(base, 'instances', 'heraliz', 'spartan', 'ecartes', '42')
        assert.deepEqual(fs.readdirSync(cote).sort(), ['Autre.ZIP', 'triche.jar'])
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})

test('pas de dossier mods, nom de serveur douteux ou dossier mods remplacé par un lien : rien n’est touché', () => {
    const base = dossierEssai()
    try {
        const instances = path.join(base, 'instances')
        fs.mkdirSync(path.join(instances, 'heraliz'), { recursive: true })
        assert.deepEqual(ecarterJarsEnTrop({ dossierInstances: instances, serveur: 'heraliz' }).ecartes, [])
        assert.deepEqual(ecarterJarsEnTrop({ dossierInstances: instances, serveur: '../dehors' }).ecartes, [])
        const dehors = path.join(base, 'dehors')
        fs.mkdirSync(dehors)
        fs.writeFileSync(path.join(dehors, 'perso.jar'), 'à ne jamais toucher')
        lien(dehors, path.join(instances, 'heraliz', 'mods'))
        assert.deepEqual(ecarterJarsEnTrop({ dossierInstances: instances, serveur: 'heraliz' }).ecartes, [])
        assert.ok(fs.existsSync(path.join(dehors, 'perso.jar')))
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})

test('19/11 : packs de textures ajoutés par le joueur mis de côté, jamais ceux du pack signé ni les autres fichiers', () => {
    const base = dossierEssai()
    try {
        const instance = path.join(base, 'instances', 'heraliz')
        const packs = path.join(instance, 'resourcepacks')
        const kiwi = path.join(instance, 'kiwipacks')
        fs.mkdirSync(path.join(packs, 'Xray Eclate'), { recursive: true })
        fs.writeFileSync(path.join(packs, 'Xray Eclate', 'pack.mcmeta'), '{}')
        fs.writeFileSync(path.join(packs, 'Xray Ultimate.ZIP'), 'x')
        fs.writeFileSync(path.join(packs, 'officiel.zip'), 'livré par le pack')
        fs.writeFileSync(path.join(packs, 'lisez-moi.txt'), 'ignoré par le jeu')
        fs.mkdirSync(kiwi)
        fs.writeFileSync(path.join(kiwi, 'impose.zip'), 'y')
        fs.mkdirSync(path.join(instance, 'mods'))
        fs.writeFileSync(path.join(instance, 'options.txt'), 'resourcePacks:["vanilla","file/Xray Ultimate.ZIP"]')
        const r = ecarterPacksAjoutes({ dossierInstances: path.join(base, 'instances'), serveur: 'heraliz',
            fichiersPack: [path.join(packs, 'officiel.zip'), 42], maintenant: () => 7 })
        assert.deepEqual(r.ecartes.sort(), ['kiwipacks/impose.zip', 'resourcepacks/Xray Eclate', 'resourcepacks/Xray Ultimate.ZIP'])
        assert.deepEqual(r.erreurs, [])
        assert.deepEqual(fs.readdirSync(packs).sort(), ['lisez-moi.txt', 'officiel.zip'])
        assert.deepEqual(fs.readdirSync(kiwi), [])
        const cote = path.join(instance, 'spartan', 'ecartes', '7')
        assert.deepEqual(fs.readdirSync(path.join(cote, 'resourcepacks')).sort(), ['Xray Eclate', 'Xray Ultimate.ZIP'])
        assert.ok(fs.existsSync(path.join(cote, 'resourcepacks', 'Xray Eclate', 'pack.mcmeta')), 'jamais effacé')
        assert.deepEqual(fs.readdirSync(path.join(cote, 'kiwipacks')), ['impose.zip'])
        // options.txt du joueur jamais réécrit (le jeu oublie seul un pack absent)
        assert.equal(fs.readFileSync(path.join(instance, 'options.txt'), 'utf8'), 'resourcePacks:["vanilla","file/Xray Ultimate.ZIP"]')
        // plus rien à mettre de côté au lancement suivant
        assert.deepEqual(ecarterPacksAjoutes({ dossierInstances: path.join(base, 'instances'), serveur: 'heraliz',
            fichiersPack: [path.join(packs, 'officiel.zip')] }).ecartes, [])
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})

test('19/11 : packs : sans dossier, nom de serveur douteux ou dossier remplacé par un lien, rien n’est touché', () => {
    const base = dossierEssai()
    try {
        const instances = path.join(base, 'instances')
        fs.mkdirSync(path.join(instances, 'heraliz'), { recursive: true })
        assert.deepEqual(ecarterPacksAjoutes({ dossierInstances: instances, serveur: 'heraliz' }).ecartes, [])
        assert.deepEqual(ecarterPacksAjoutes({ dossierInstances: instances, serveur: '../dehors' }).ecartes, [])
        assert.deepEqual(ecarterPacksAjoutes({ dossierInstances: 42, serveur: 'heraliz' }).ecartes, [])
        const dehors = path.join(base, 'dehors')
        fs.mkdirSync(dehors)
        fs.writeFileSync(path.join(dehors, 'perso.zip'), 'à ne jamais toucher')
        lien(dehors, path.join(instances, 'heraliz', 'resourcepacks'))
        assert.deepEqual(ecarterPacksAjoutes({ dossierInstances: instances, serveur: 'heraliz' }).ecartes, [])
        assert.ok(fs.existsSync(path.join(dehors, 'perso.zip')))
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})

test('Java du jeu : relevé au premier contrôle, puis tout fichier surveillé changé ou ajouté est vu', async () => {
    const base = dossierEssai()
    try {
        const jdk = path.join(base, 'runtime', 'jdk-17')
        fs.mkdirSync(path.join(jdk, 'bin'), { recursive: true })
        fs.mkdirSync(path.join(jdk, 'lib'), { recursive: true })
        fs.writeFileSync(path.join(jdk, 'bin', 'java.exe'), 'programme')
        fs.writeFileSync(path.join(jdk, 'bin', 'jli.dll'), 'bibliotheque')
        fs.writeFileSync(path.join(jdk, 'lib', 'modules'), 'classes')
        fs.writeFileSync(path.join(jdk, 'release'), 'JAVA_VERSION="17"')
        const releve = path.join(base, 'heraliz-java.json')
        assert.equal((await controlerJava(jdk, releve)).etat, 'nouveau')
        assert.equal((await controlerJava(jdk, releve)).etat, 'ok')
        // un fichier non surveillé (texte, journal) ne compte pas
        fs.writeFileSync(path.join(jdk, 'lisez-moi.txt'), 'texte')
        assert.equal((await controlerJava(jdk, releve)).etat, 'ok')
        // une bibliothèque ajoutée à côté de java.exe
        fs.writeFileSync(path.join(jdk, 'bin', 'version.dll'), 'ajoutee')
        let c = await controlerJava(jdk, releve)
        assert.equal(c.etat, 'change')
        assert.deepEqual(c.ecarts, ['bin/version.dll'])
        fs.rmSync(path.join(jdk, 'bin', 'version.dll'))
        // l'archive des classes modifiée
        fs.writeFileSync(path.join(jdk, 'lib', 'modules'), 'classes modifiees')
        c = await controlerJava(jdk, releve)
        assert.equal(c.etat, 'change')
        assert.deepEqual(c.ecarts, ['lib/modules'])
        // relevé repris (Java réinstallé) puis oublié
        assert.equal((await controlerJava(jdk, releve, { reprendre: true })).etat, 'nouveau')
        assert.equal((await controlerJava(jdk, releve)).etat, 'ok')
        oublierJava(jdk, releve)
        assert.equal((await controlerJava(jdk, releve)).etat, 'nouveau')
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})
