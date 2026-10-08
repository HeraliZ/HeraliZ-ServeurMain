'use strict'

const test = require('node:test')
const assert = require('node:assert')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { protections, optionsSures, argumentJeton, masquerJeton, empreinteMods, DUREE_JETON_S } = require('../app/assets/js/spartan')

const CLE = 'une-cle-de-test-bien-longue-0123456789-abcdef'

function decode(b64) {
    return Buffer.from(b64.replace(/-/g, '+').replace(/_/g, '/'), 'base64')
}

test('le jeu est toujours lancé sans attache possible', () => {
    assert.deepStrictEqual(protections(), ['-XX:+DisableAttachMechanism'])
    // une copie : personne ne peut retirer la protection pour les lancements suivants
    protections().pop()
    assert.deepStrictEqual(protections(), ['-XX:+DisableAttachMechanism'])
})

test('les options d’injection du joueur sont retirées, les autres gardées', () => {
    const joueur = ['-XX:+UseG1GC', '-javaagent:C:/x/cheat.jar', '-agentlib:jdwp=transport=dt_socket', '-agentpath:x.dll',
        '-Xbootclasspath/a:x.jar', '-XX:-DisableAttachMechanism', '-Djdk.attach.allowAttachSelf=true',
        '-XX:+EnableDynamicAgentLoading', '-Xverify:none', '-noverify', '-Dfml.earlyprogresswindow=false', '-Xss2M']
    assert.deepStrictEqual(optionsSures(joueur), ['-XX:+UseG1GC', '-Dfml.earlyprogresswindow=false', '-Xss2M'])
})

test('des options absentes ou invalides ne cassent pas le lancement', () => {
    assert.deepStrictEqual(optionsSures(undefined), [])
    assert.deepStrictEqual(optionsSures(['-Xmx4G', 42, null]), ['-Xmx4G'])
})

test('un joueur ne peut pas fournir son propre jeton par ses options Java', () => {
    assert.deepStrictEqual(optionsSures(['-Dspartan.launchToken=faux', '-Xss2M']), ['-Xss2M'])
})

test('jeton : signé, lié au pseudo et aux mods, valable 6 h', () => {
    const vide = fs.mkdtempSync(path.join(os.tmpdir(), 'spartan-'))
    const maintenant = 1_800_000_000
    const arg = argumentJeton('Survivant', vide, maintenant, CLE)
    assert.ok(arg.startsWith('-Dspartan.launchToken='))
    const [payloadB64, sigB64] = arg.slice('-Dspartan.launchToken='.length).split('.')
    const payload = decode(payloadB64)
    const attendu = crypto.createHmac('sha256', Buffer.from(CLE, 'utf8')).update(payload).digest()
    assert.ok(attendu.equals(decode(sigB64)), 'signature')
    const [v, pseudo, exp, empreinte] = payload.toString('utf8').split('|')
    assert.strictEqual(v, 'v1')
    assert.strictEqual(decode(pseudo).toString('utf8'), 'Survivant')
    assert.strictEqual(Number(exp), maintenant + DUREE_JETON_S)
    assert.strictEqual(DUREE_JETON_S, 6 * 3600)
    assert.strictEqual(empreinte, crypto.createHash('sha256').update('').digest('hex'), 'dossier mods vide')
})

test('empreinte des mods : même calcul que le mod', () => {
    const d = fs.mkdtempSync(path.join(os.tmpdir(), 'spartan-'))
    fs.writeFileSync(path.join(d, 'B.jar'), 'deux')
    fs.writeFileSync(path.join(d, 'a.jar'), 'un')
    fs.writeFileSync(path.join(d, 'notes.txt'), 'ignoré')
    const h = x => crypto.createHash('sha256').update(x).digest('hex')
    assert.strictEqual(empreinteMods(d), h(['a.jar:' + h('un'), 'b.jar:' + h('deux')].join(String.fromCharCode(10))))
})

test('sans clé, pas de jeton ; le journal ne montre jamais le jeton', () => {
    assert.strictEqual(argumentJeton('Survivant', os.tmpdir(), 1_800_000_000, null), null)
    assert.deepStrictEqual(masquerJeton(['-Xmx4G', '-Dspartan.launchToken=secret.secret']), ['-Xmx4G', '-Dspartan.launchToken=**********'])
})
