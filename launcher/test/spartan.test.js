'use strict'

const test = require('node:test')
const assert = require('node:assert')
const { protections, optionsSures } = require('../app/assets/js/spartan')

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
