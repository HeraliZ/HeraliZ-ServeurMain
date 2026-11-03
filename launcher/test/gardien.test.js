'use strict'

const test = require('node:test')
const assert = require('node:assert')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { Gardien, outilsTrouves, champsCsv, programmes, outilsInterdits } = require('../app/assets/js/gardien')
const { environnementJeu } = require('../app/assets/js/spartan')

function dossier() {
    return fs.mkdtempSync(path.join(os.tmpdir(), 'gardien-'))
}

test('seuls les noms de la liste sont gardés, jamais les autres programmes', () => {
    const noms = ['explorer.exe', 'cheatengine-x86_64.exe', 'Discord.exe', 'javaw.exe', 'x64dbg.exe']
    const titres = ['N/A', 'Mes documents', 'Cheat Engine 7.5', 'Recaf 4.0']
    assert.deepStrictEqual(outilsTrouves(noms, titres), ['cheat engine', 'cheatengine', 'recaf', 'x64dbg'])
    assert.deepStrictEqual(outilsTrouves(['chrome.exe', 'steam.exe'], ['YouTube']), [])
    // mots entiers seulement, et jamais le titre d'un navigateur (page regardée)
    assert.deepStrictEqual(outilsTrouves(['excel.exe'], ['Reclassement.xlsx - Excel', 'Cheat Engineering 101']), [])
    assert.deepStrictEqual(outilsTrouves(['chrome.exe'], [{ nom: 'chrome.exe', titre: 'Tuto Cheat Engine - YouTube' }]), [])
    assert.deepStrictEqual(outilsTrouves(['ce.exe'], [{ nom: 'ce.exe', titre: 'Cheat Engine 7.5' }]), ['cheat engine'])
})

test('lecture des lignes CSV de tasklist', () => {
    assert.deepStrictEqual(champsCsv('"a.exe","12","Console","1","1 024 K","Running","PC\\\\x","0:00:01","Titre ""cité"""'),
        ['a.exe', '12', 'Console', '1', '1 024 K', 'Running', 'PC\\\\x', '0:00:01', 'Titre "cité"'])
})

test('rapport signé : ligne lisible par le jeu, signature HMAC avec la clé du lancement', async () => {
    const d = dossier()
    const cle = Gardien.nouvelleCle()
    const g = new Gardien({ dossierJeu: d, pid: 4242, cle, lister: async () => ({ noms: ['xenos64.exe'], titres: [] }), maintenant: () => 1000 })
    const ligne = await g.rapport()
    assert.strictEqual(ligne, 'v1|1|1000|4242|ok|xenos|0')
    const [l, sig] = fs.readFileSync(path.join(d, 'spartan', 'gardien.txt'), 'utf8').trim().split('\n')
    assert.strictEqual(l, ligne)
    assert.strictEqual(sig, crypto.createHmac('sha256', Buffer.from(cle, 'hex')).update(l).digest('hex'))
    assert.strictEqual(await g.rapport(), 'v1|2|1000|4242|ok|xenos|0')
    g.arreter()
    assert.ok(!fs.existsSync(path.join(d, 'spartan', 'gardien.txt')))
})

test('un jar changé, ajouté ou retiré pendant la partie est compté', async () => {
    const d = dossier()
    const mods = path.join(d, 'mods')
    fs.mkdirSync(mods)
    fs.writeFileSync(path.join(mods, 'a.jar'), 'A')
    fs.writeFileSync(path.join(mods, 'b.jar'), 'B')
    const g = new Gardien({ dossierJeu: d, dossiersJars: [mods], pid: 1, cle: Gardien.nouvelleCle(), lister: async () => null })
    g.releverJars(true)
    assert.match(await g.rapport(), /\|impossible\|\|0$/)
    fs.writeFileSync(path.join(mods, 'a.jar'), 'AAAA')
    fs.writeFileSync(path.join(mods, 'triche.jar'), 'X')
    fs.rmSync(path.join(mods, 'b.jar'))
    assert.match(await g.rapport(), /\|3$/)
    g.arreter()
})

test('la clé du Gardien passe au jeu, jamais une clé héritée ou mal formée', () => {
    const cle = 'ab'.repeat(32)
    assert.strictEqual(environnementJeu(null, {}, false, cle).SPARTAN_GARDIEN_CLE, cle)
    assert.strictEqual(environnementJeu(null, { SPARTAN_GARDIEN_CLE: cle }, false, null).SPARTAN_GARDIEN_CLE, undefined)
    assert.strictEqual(environnementJeu(null, {}, false, 'zz').SPARTAN_GARDIEN_CLE, undefined)
})

// ------------------------------------------------------------------ preuve (launcher 1.16.0, Spartan 2.10.0)

/** Ce que fait le serveur (GardienPreuve.java) : clé publique brute -> clé, puis vérification Ed25519. */
function verifierCommeLeServeur(publique, ligne, signature) {
    const der = Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(publique, 'base64url')])
    const cle = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' })
    return crypto.verify(null, Buffer.from(ligne, 'utf8'), cle, Buffer.from(signature, 'base64url'))
}

test('preuve : ligne v2 signée avec la clé privée du launcher, défi du serveur repris, jamais la clé donnée au jeu', async () => {
    const d = dossier()
    const cles = Gardien.nouvellesCles(2)
    assert.match(cles.publique, /^[A-Za-z0-9_-]{43}$/)
    assert.strictEqual(cles.jeu, cles.publique, 'le jeu ne reçoit que la clé publique')
    const g = new Gardien({ dossierJeu: d, pid: 4242, cles, lister: async () => ({ noms: ['xenos64.exe'], titres: [] }), maintenant: () => 1000 })
    // pas encore de défi (jeu pas connecté) : « - »
    assert.strictEqual(await g.rapport(), 'v2|1|1000|4242|-|ok|xenos|0')
    const defi = 'ab12'.repeat(8)
    fs.writeFileSync(path.join(d, 'spartan', 'gardien-defi.txt'), defi + '\n')
    const ligne = await g.rapport()
    assert.strictEqual(ligne, `v2|2|1000|4242|${defi}|ok|xenos|0`)
    const [l, sig] = fs.readFileSync(path.join(d, 'spartan', 'gardien.txt'), 'utf8').trim().split('\n')
    assert.strictEqual(l, ligne)
    assert.ok(verifierCommeLeServeur(cles.publique, l, sig), 'signature vérifiable avec la seule clé publique')
    assert.ok(!verifierCommeLeServeur(cles.publique, l.replace('|xenos|', '||'), sig), 'ligne retouchée : refusée')
    assert.ok(!verifierCommeLeServeur(Gardien.nouvellesCles(2).publique, l, sig), 'autre lancement : refusée')
    // défi abîmé : ignoré
    fs.writeFileSync(path.join(d, 'spartan', 'gardien-defi.txt'), 'pas-un-defi')
    assert.match(await g.rapport(), /^v2\|3\|1000\|4242\|-\|/)
    g.arreter()
    assert.ok(!fs.existsSync(path.join(d, 'spartan', 'gardien.txt')))
    assert.ok(!fs.existsSync(path.join(d, 'spartan', 'gardien-defi.txt')))
})

test('engagement : même calcul que GardienPreuve.engagement de Spartan', () => {
    const vide = crypto.createHash('sha256').update('').digest('hex')
    assert.strictEqual(Gardien.engagement(vide, 'A'.repeat(43)), '871a1985939220cb08bb4b70d698c48d204db28fb38d843cfd6f40c3c8f27a77')
})

test('mode preuve seulement si le pack contient Spartan 2.10.0 ou plus récent', () => {
    const { versionPreuve } = require('../app/assets/js/gardien')
    const mod = id => ({ rawModule: { id }, subModules: [] })
    assert.strictEqual(versionPreuve([mod('net.minecraftforge:forge:1.20.1-47.4.0'), mod('fr.heraliz:spartan:2.10.0:client@jar')]), 2)
    assert.strictEqual(versionPreuve([{ id: 'fr.heraliz:spartan:2.11.3@jar' }]), 2)
    assert.strictEqual(versionPreuve([{ id: 'fr.heraliz:spartan:3.0@jar' }]), 2)
    assert.strictEqual(versionPreuve([{ id: 'x:y:1', subModules: [{ id: 'fr.heraliz:spartan:2.9.6:client@jar' }] }]), 1)
    assert.strictEqual(versionPreuve([{ id: 'fr.heraliz:spartan:2.9.12@jar' }]), 1)
    assert.strictEqual(versionPreuve([{ id: 'fr.heraliz:spartanplus:9.0@jar' }]), 1)
    assert.strictEqual(versionPreuve(undefined), 1)
    assert.strictEqual(Gardien.nouvellesCles(1).version, 1)
    assert.match(Gardien.nouvellesCles(1).jeu, /^[0-9a-f]{64}$/)
})

test('liste des outils bornée dans la ligne', () => {
    const beaucoup = Array.from({ length: 60 }, (_, i) => 'outil' + i)
    const ligne = Gardien.ligneV2({ n: 1, t: 1, pid: 1, defi: '-', lecture: 'ok', outils: beaucoup, modifies: 0 })
    assert.ok(ligne.split('|')[6].length <= 200)
})

test('la clé publique du Gardien passe au jeu par sa propre variable, jamais héritée', () => {
    const pub = Gardien.nouvellesCles(2).publique
    const env = environnementJeu(null, {}, false, pub)
    assert.strictEqual(env.SPARTAN_GARDIEN_PUB, pub)
    assert.strictEqual(env.SPARTAN_GARDIEN_CLE, undefined)
    assert.strictEqual(environnementJeu(null, { SPARTAN_GARDIEN_PUB: pub }, false, null).SPARTAN_GARDIEN_PUB, undefined)
})

// 03/11 : liste des programmes rapide (tasklist sans /V), PowerShell en secours, titres à part
function exec(reponses) {
    const appels = []
    const f = async (cmd, args) => {
        appels.push([cmd, ...args].join(' '))
        const r = reponses(cmd, args)
        if (r instanceof Error) throw r
        return r
    }
    f.appels = appels
    return f
}

test('Windows : noms par tasklist sans /V, titres par PowerShell', async () => {
    const e = exec((cmd, args) => cmd === 'tasklist'
        ? '"javaw.exe","4242","Console","1","900 000 K"\r\n"Cheat Engine.exe","77","Console","1","20 000 K"\r\n'
        : args.join(' ').includes('MainWindowTitle') ? 'chrome\tCheat Engine tuto - YouTube\r\nnotepad++\tCheat Engine 7.5\r\n' : '')
    const l = await programmes('win32', e)
    assert.deepStrictEqual(l.noms, ['javaw.exe', 'Cheat Engine.exe'])
    assert.ok(!e.appels.some(a => a.includes('/V')), 'tasklist /V (lent) ne doit plus être appelé')
    assert.deepStrictEqual(outilsTrouves(l.noms, l.titres), ['cheat engine'])
})

test('Windows : tasklist en échec ou trop lent, PowerShell donne les noms ; titres impossibles : lecture quand même', async () => {
    const e = exec((cmd, args) => cmd === 'tasklist' ? new Error('ETIMEDOUT')
        : args.join(' ').includes('MainWindowTitle') ? new Error('refusé') : 'javaw.exe\r\nxenos64.exe\r\n')
    const l = await programmes('win32', e)
    assert.deepStrictEqual(l.noms, ['javaw.exe', 'xenos64.exe'])
    assert.deepStrictEqual(l.titres, [])
})

test('Windows : rien ne répond, lecture impossible (null), jamais d\'erreur', async () => {
    const l = await programmes('win32', exec(() => new Error('refusé')))
    assert.strictEqual(l, null)
})

test('lancement : injecteurs et éditeurs de mémoire seulement, noms lisibles', async () => {
    assert.deepStrictEqual(await outilsInterdits(async () => ({ noms: ['xenos64.exe', 'x64dbg.exe', 'cheatengine-x86_64.exe'], titres: [] })),
        ['Cheat Engine', 'Xenos'])
    assert.deepStrictEqual(await outilsInterdits(async () => ({ noms: ['x64dbg.exe', 'recaf.exe'], titres: [] })), [])
    assert.deepStrictEqual(await outilsInterdits(async () => null), [])
    assert.deepStrictEqual(await outilsInterdits(async () => { throw new Error('x') }), [])
})

test('rapport : un outil vu seulement dans un titre de fenêtre est marqué « .titre » (alerte, jamais expulsion)', () => {
    assert.deepStrictEqual(outilsTrouves(['notepad++.exe'], [{ nom: 'notepad++', titre: 'Cheat Engine 7.5.txt - Notepad++' }], true),
        ['cheat engine.titre'])
    assert.deepStrictEqual(outilsTrouves(['cheatengine-x86_64.exe'], [{ nom: 'cheatengine-x86_64', titre: 'Cheat Engine 7.5' }], true),
        ['cheat engine', 'cheatengine'])
})
