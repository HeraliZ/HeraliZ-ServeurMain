const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { cheminSur, effacerDonneesJeu, retirerAnciennesVersions } = require('../app/assets/js/effacement')

function dossierEssai() {
    const base = fs.mkdtempSync(path.join(os.tmpdir(), 'heraliz-effacement-'))
    const racine = path.join(base, 'launcher')
    const dehors = path.join(base, 'dehors')
    fs.mkdirSync(racine)
    fs.mkdirSync(dehors)
    fs.writeFileSync(path.join(dehors, 'perso.txt'), 'à ne jamais toucher')
    return { base, racine, dehors }
}

// liens : sous Windows, une jonction (pas besoin des droits d'administrateur)
function lien(cible, chemin) {
    fs.symlinkSync(cible, chemin, process.platform === 'win32' ? 'junction' : 'dir')
}

test('cheminSur refuse tout chemin hors du dossier du launcher, la racine elle-même et les chemins relatifs', () => {
    const { base, racine, dehors } = dossierEssai()
    try {
        fs.mkdirSync(path.join(racine, 'game', 'common', 'modstore'), { recursive: true })
        assert.ok(cheminSur(racine, path.join(racine, 'game', 'common', 'modstore')))
        assert.equal(cheminSur(racine, racine), null)
        assert.equal(cheminSur(racine, path.join(racine, '..')), null)
        assert.equal(cheminSur(racine, path.join(racine, 'game', '..', '..', 'dehors')), null)
        assert.equal(cheminSur(racine, dehors), null)
        assert.equal(cheminSur(racine, path.join(dehors, 'perso.txt')), null)
        // dossier voisin dont le nom commence pareil : « launcher-bis » n'est pas dans « launcher »
        fs.mkdirSync(`${racine}-bis`)
        assert.equal(cheminSur(racine, `${racine}-bis`), null)
        assert.equal(cheminSur(racine, 'game/common'), null)
        assert.equal(cheminSur('relatif', path.join(racine, 'game')), null)
        assert.equal(cheminSur(racine, path.join(racine, 'absent')), null)
        assert.equal(cheminSur(racine, null), null)
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})

test('cheminSur refuse un lien et un dossier parent qui est un lien vers l’extérieur', () => {
    const { base, racine, dehors } = dossierEssai()
    try {
        lien(dehors, path.join(racine, 'mods'))
        assert.equal(cheminSur(racine, path.join(racine, 'mods')), null)
        // le lien est un dossier parent : le chemin réel sort de la racine
        assert.equal(cheminSur(racine, path.join(racine, 'mods', 'perso.txt')), null)
        // lien qui reste dans la racine : la cible elle-même n'est jamais un lien, mais à travers lui c'est permis
        fs.mkdirSync(path.join(racine, 'vrai'))
        fs.writeFileSync(path.join(racine, 'vrai', 'a.jar'), 'x')
        lien(path.join(racine, 'vrai'), path.join(racine, 'raccourci'))
        assert.equal(cheminSur(racine, path.join(racine, 'raccourci')), null)
        assert.ok(cheminSur(racine, path.join(racine, 'raccourci', 'a.jar')))
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})

test('effacerDonneesJeu retire le pack HeraliZ, garde les fichiers du joueur et ne suit jamais un lien', () => {
    const { base, racine, dehors } = dossierEssai()
    try {
        const jeu = path.join(racine, 'game')
        const commun = path.join(jeu, 'common')
        const instances = path.join(jeu, 'instances')
        const instance = path.join(instances, 'heraliz-1.20.1')
        for (const d of [path.join(commun, 'modstore', 'fr', 'heraliz'), path.join(commun, 'libraries'), path.join(instance, 'tacz', 'pack'),
            path.join(instance, 'mods'), path.join(instance, 'config'), path.join(instance, 'resourcepacks'), path.join(instance, 'screenshots'),
            path.join(instance, 'logs')]) fs.mkdirSync(d, { recursive: true })
        fs.writeFileSync(path.join(commun, 'modstore', 'fr', 'heraliz', 'mod.jar'), 'jar')
        fs.writeFileSync(path.join(commun, 'libraries', 'forge.jar'), 'forge')
        fs.writeFileSync(path.join(instance, 'tacz', 'pack', 'arme.json'), '{}')
        fs.writeFileSync(path.join(instance, 'forgeMods.list'), 'x')
        fs.writeFileSync(path.join(instance, 'options.txt'), 'lang:fr_fr')
        fs.writeFileSync(path.join(instance, 'screenshots', 'photo.png'), 'png')
        fs.writeFileSync(path.join(racine, 'distribution.json'), '{}')
        fs.writeFileSync(path.join(racine, 'config.json'), '{}')
        // un lien caché dans le pack vers un dossier personnel : retiré, sans rien effacer dehors
        lien(dehors, path.join(instance, 'tacz', 'piege'))
        // un dossier du jeu qui est lui-même un lien vers l'extérieur : refusé, laissé tel quel
        lien(dehors, path.join(instance, 'resourcepacks-lien'))

        const resultat = effacerDonneesJeu({ racine, dossierCommun: commun, dossierInstances: instances, serveur: 'heraliz-1.20.1' })
        assert.deepEqual(resultat.erreurs, [])
        assert.equal(fs.existsSync(path.join(commun, 'modstore')), false)
        for (const nom of ['tacz', 'mods', 'config', 'forgeMods.list']) assert.equal(fs.existsSync(path.join(instance, nom)), false, nom)
        assert.equal(fs.existsSync(path.join(racine, 'distribution.json')), false)
        // gardés
        assert.equal(fs.readFileSync(path.join(instance, 'options.txt'), 'utf8'), 'lang:fr_fr')
        assert.ok(fs.existsSync(path.join(instance, 'screenshots', 'photo.png')))
        assert.ok(fs.existsSync(path.join(instance, 'logs')))
        // packs ajoutés par le joueur (le pack HeraliZ n'en livre aucun)
        assert.ok(fs.existsSync(path.join(instance, 'resourcepacks')))
        assert.ok(fs.existsSync(path.join(commun, 'libraries', 'forge.jar')))
        assert.ok(fs.existsSync(path.join(racine, 'config.json')))
        assert.equal(fs.readFileSync(path.join(dehors, 'perso.txt'), 'utf8'), 'à ne jamais toucher')
        assert.deepEqual(resultat.refuses, [path.join(instance, 'resourcepacks-lien')])
        assert.ok(fs.lstatSync(path.join(instance, 'resourcepacks-lien')).isSymbolicLink())
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})

test('effacerDonneesJeu ne touche à rien si le dossier du jeu est hors du dossier du launcher', () => {
    const { base, racine, dehors } = dossierEssai()
    try {
        const commun = path.join(dehors, 'common')
        fs.mkdirSync(path.join(commun, 'modstore'), { recursive: true })
        fs.mkdirSync(path.join(dehors, 'instances', 'heraliz-1.20.1', 'mods'), { recursive: true })
        const resultat = effacerDonneesJeu({ racine, dossierCommun: commun, dossierInstances: path.join(dehors, 'instances'), serveur: 'heraliz-1.20.1' })
        assert.equal(resultat.effaces.length, 0)
        assert.equal(resultat.refuses.length, 2)
        assert.ok(fs.existsSync(path.join(commun, 'modstore')))
        assert.ok(fs.existsSync(path.join(dehors, 'instances', 'heraliz-1.20.1', 'mods')))
        // nom de serveur qui sortirait du dossier des instances : ignoré
        assert.deepEqual(effacerDonneesJeu({ racine, dossierCommun: path.join(racine, 'c'), dossierInstances: path.join(racine, 'i'), serveur: '../..' }).effaces, [])
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})

function ecrire(chemin, texte = 'x') {
    fs.mkdirSync(path.dirname(chemin), { recursive: true })
    fs.writeFileSync(chemin, texte)
}

test('retirerAnciennesVersions ne garde dans modstore et tacz que les fichiers du pack actuel', () => {
    const { base, racine, dehors } = dossierEssai()
    try {
        const commun = path.join(racine, 'game', 'common')
        const instances = path.join(racine, 'game', 'instances')
        const instance = path.join(instances, 'heraliz-1.20.1')
        const store = path.join(commun, 'modstore', 'fr', 'heraliz', 'spartan')
        const tacz = path.join(instance, 'tacz')
        const actuels = [
            path.join(store, '2.9.3', 'spartan-2.9.3-client.jar'),
            path.join(tacz, 'heraliz_1_base_tacz', 'assets', 'tacz', 'textures', 'gun', 'slot', 'ak47.png'),
            path.join(tacz, 'tacz-pre.toml')
        ]
        actuels.forEach(f => ecrire(f))
        ecrire(path.join(store, '2.9.2', 'spartan-2.9.2-client.jar'))
        ecrire(path.join(store, '2.9.1', 'spartan-2.9.1-client.jar'))
        ecrire(path.join(commun, 'modstore', 'fr', 'heraliz', 'ancien_mod', '1.0.0', 'ancien_mod-1.0.0.jar'))
        ecrire(path.join(tacz, 'arkaniaz_pack_0_converted', 'assets', 'a.json'), '{}')
        ecrire(path.join(tacz, 'heraliz_1_base_tacz', 'data', 'tacz', 'index', 'guns', 'ak47.json'), '{}')
        ecrire(path.join(tacz, 'heraliz_1_base_tacz', 'assets', 'tacz', 'textures', 'gun', 'slot', 'arme_retiree.png'))
        lien(dehors, path.join(tacz, 'Cislumen_Armory'))
        // hors des deux dossiers du pack : jamais touchés
        ecrire(path.join(instance, 'options.txt'))
        ecrire(path.join(commun, 'libraries', 'net', 'minecraftforge', 'forge.jar'))
        const r = retirerAnciennesVersions({ dossierCommun: commun, dossierInstances: instances, serveur: 'heraliz-1.20.1', fichiers: actuels })
        actuels.forEach(f => assert.ok(fs.existsSync(f), f))
        assert.equal(fs.existsSync(path.join(store, '2.9.2')), false)
        assert.equal(fs.existsSync(path.join(store, '2.9.1')), false)
        assert.equal(fs.existsSync(path.join(commun, 'modstore', 'fr', 'heraliz', 'ancien_mod')), false)
        assert.equal(fs.existsSync(path.join(tacz, 'arkaniaz_pack_0_converted')), false)
        assert.equal(fs.existsSync(path.join(tacz, 'heraliz_1_base_tacz', 'data')), false)
        assert.equal(fs.existsSync(path.join(tacz, 'heraliz_1_base_tacz', 'assets', 'tacz', 'textures', 'gun', 'slot', 'arme_retiree.png')), false)
        // un lien est retiré lui-même, jamais suivi : ce qu'il vise reste intact
        assert.equal(fs.existsSync(path.join(tacz, 'Cislumen_Armory')), false)
        assert.ok(fs.existsSync(path.join(dehors, 'perso.txt')))
        assert.ok(fs.existsSync(path.join(instance, 'options.txt')))
        assert.ok(fs.existsSync(path.join(commun, 'libraries', 'net', 'minecraftforge', 'forge.jar')))
        assert.deepEqual(r.erreurs, [])
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})

test('retirerAnciennesVersions ne touche à rien sans fichier du pack dans le dossier', () => {
    const { base, racine } = dossierEssai()
    try {
        const commun = path.join(racine, 'common')
        const instances = path.join(racine, 'instances')
        const jar = path.join(commun, 'modstore', 'fr', 'heraliz', 'spartan', '2.9.3', 'spartan-2.9.3-client.jar')
        const gunpack = path.join(instances, 'heraliz-1.20.1', 'tacz', 'heraliz_1_base_tacz', 'pack.json')
        ecrire(jar)
        ecrire(gunpack)
        // distribution vide ou illisible : rien n'est effacé
        for (const fichiers of [[], undefined, ['relatif/a.jar']]) {
            assert.deepEqual(retirerAnciennesVersions({ dossierCommun: commun, dossierInstances: instances, serveur: 'heraliz-1.20.1', fichiers }).effaces, [])
        }
        // pack sans fichier dans tacz : tacz laissé tel quel ; nom de serveur qui sortirait des instances : ignoré
        const r = retirerAnciennesVersions({ dossierCommun: commun, dossierInstances: instances, serveur: '../x', fichiers: [jar, gunpack] })
        assert.deepEqual(r.effaces, [])
        retirerAnciennesVersions({ dossierCommun: commun, dossierInstances: instances, serveur: 'heraliz-1.20.1', fichiers: [jar] })
        assert.ok(fs.existsSync(jar))
        assert.ok(fs.existsSync(gunpack))
    } finally { fs.rmSync(base, { recursive: true, force: true }) }
})
