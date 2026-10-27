const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { ecarterJarsEnTrop, controlerJava, oublierJava } = require('../app/assets/js/integrite')

function dossierEssai() {
    return fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-integrite-'))
}

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
