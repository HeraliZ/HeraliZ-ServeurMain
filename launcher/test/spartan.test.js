'use strict'

const test = require('node:test')
const assert = require('node:assert')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { protections, optionsSures, argumentJeton, masquerJeton, empreinteMods } = require('../app/assets/js/spartan')

function b64url(buf) {
    return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
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

test('jeton : celui signé par le site passe tel quel au jeu, rien d’autre', () => {
    // même forme que les jetons du site (JetonController) et de LaunchToken.java : contenu "." signature HMAC-SHA256
    const contenu = b64url(Buffer.from('v1|' + b64url('Survivant') + '|1800021600|' + 'a'.repeat(64) + '|' + 'b'.repeat(24)))
    const jeton = contenu + '.' + b64url(crypto.createHmac('sha256', 'cle').update('x').digest())
    assert.strictEqual(argumentJeton(jeton), '-Dspartan.launchToken=' + jeton)
    for (const faux of [null, '', 'abc', jeton + ' -javaagent:x.jar', jeton + '.x', '-Dautre=1.' + 'a'.repeat(43), jeton.replace('.', '')]) {
        assert.strictEqual(argumentJeton(faux), null, String(faux))
    }
})

test('empreinte des mods : même calcul que le mod', () => {
    const d = fs.mkdtempSync(path.join(os.tmpdir(), 'spartan-'))
    fs.writeFileSync(path.join(d, 'B.jar'), 'deux')
    fs.writeFileSync(path.join(d, 'a.jar'), 'un')
    fs.writeFileSync(path.join(d, 'notes.txt'), 'ignoré')
    const h = x => crypto.createHash('sha256').update(x).digest('hex')
    assert.strictEqual(empreinteMods(d), h(['a.jar:' + h('un'), 'b.jar:' + h('deux')].join(String.fromCharCode(10))))
})

test('sans jeton du site, pas d’option ; le journal ne montre jamais le jeton', () => {
    assert.strictEqual(argumentJeton(undefined), null)
    assert.deepStrictEqual(masquerJeton(['-Xmx4G', '-Dspartan.launchToken=secret.secret']), ['-Xmx4G', '-Dspartan.launchToken=**********'])
})
