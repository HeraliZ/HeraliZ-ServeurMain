'use strict'

const test = require('node:test')
const assert = require('node:assert')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { Gardien, outilsTrouves, champsCsv } = require('../app/assets/js/gardien')
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
