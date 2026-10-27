// Démarrage de secours (secours.js) : un launcher qui ne démarre pas cherche d'abord une version réparée.
const test = require('node:test')
const assert = require('node:assert/strict')
const { attendreReparation, lienTelechargement, causeCourte, messageFinal, pageSecours } = require('../app/assets/js/secours')

// Faux MiseAJour : résultat de verifier() et états envoyés ensuite (comme mise-a-jour.js)
function fausseMaj(resultat, etats = []) {
    const ecouteurs = new Set()
    return {
        demandes: 0,
        ecouteurs,
        abonner(ecouter) { ecouteurs.add(ecouter); return () => ecouteurs.delete(ecouter) },
        async verifier() {
            this.demandes++
            setTimeout(() => { for (const etat of etats) for (const e of ecouteurs) e(etat) }, 1)
            return resultat
        }
    }
}

test('réparation : mise à jour trouvée, téléchargée puis installée', async () => {
    const maj = fausseMaj({ disponible: true, version: '1.15.2' }, [
        { etat: 'telechargement', pourcent: 12 }, { etat: 'telechargement', pourcent: 80 }, { etat: 'installation' }
    ])
    const progres = []
    const issue = await attendreReparation({ verifier: () => maj.verifier(), abonner: e => maj.abonner(e), progression: (p, v) => progres.push([p, v]) })
    assert.deepEqual(issue, { resultat: 'installation', version: '1.15.2' })
    assert.deepEqual(progres, [[12, '1.15.2'], [80, '1.15.2']])
    assert.equal(maj.demandes, 1)
    assert.equal(maj.ecouteurs.size, 0, 'désabonné à la fin')
})

test('réparation impossible : pas de mise à jour, à la main (Mac, .deb), bloquée, refusée, ratée, sans nouvelles', async () => {
    const cas = [
        [{ disponible: false }, [], 'aucune'],
        [{ disponible: false, manuelle: true, version: '1.15.2' }, [], 'manuelle'],
        [{ disponible: false, bloquee: true, version: '1.15.2' }, [], 'bloquee'],
        [{ disponible: false, signatureInvalide: true }, [], 'signature'],
        [{ disponible: true, version: '1.15.2' }, [{ etat: 'erreur' }], 'erreur'],
        [{ disponible: true, version: '1.15.2' }, [], 'delai']
    ]
    for (const [resultat, etats, attendu] of cas) {
        const maj = fausseMaj(resultat, etats)
        const issue = await attendreReparation({ verifier: () => maj.verifier(), abonner: e => maj.abonner(e), delaiMs: 30 })
        assert.equal(issue.resultat, attendu)
        assert.equal(maj.ecouteurs.size, 0)
    }
    // verifier() qui plante : erreur, jamais d'attente sans fin
    const issue = await attendreReparation({ verifier: () => { throw new Error('cassé') }, abonner: () => () => {}, delaiMs: 1000 })
    assert.equal(issue.resultat, 'erreur')
})

test('réparation : un téléchargement qui avance n’est pas abandonné', async () => {
    const ecouteurs = new Set()
    const verifier = async () => {
        let n = 0
        const pas = setInterval(() => {
            n += 10
            for (const e of ecouteurs) e(n < 100 ? { etat: 'telechargement', pourcent: n } : { etat: 'installation' })
            if (n >= 100) clearInterval(pas)
        }, 15)
        return { disponible: true, version: '1.15.2' }
    }
    // 10 pas de 15 ms : bien plus long que le délai sans nouvelles (40 ms), relancé à chaque pas
    const issue = await attendreReparation({ verifier, abonner: e => { ecouteurs.add(e); return () => ecouteurs.delete(e) }, delaiMs: 40 })
    assert.equal(issue.resultat, 'installation')
})

test('messages : cause courte sans chemin du PC, lien de téléchargement en https', () => {
    const erreur = new Error('Cannot find module \'C:\\Users\\Compte Joueur\\AppData\\Local\\Programs\\heraliz-launcher\\resources\\app.asar\\app\\assets\\js\\gardien\'\nRequire stack:\n- C:\\Users\\Compte Joueur\\x.js')
    assert.equal(causeCourte(erreur), 'fichier gardien introuvable')
    const autre = causeCourte(new Error('ENOENT: no such file, open \'C:\\Users\\Compte Joueur\\AppData\\Roaming\\HeraliZ Launcher\\config.json\''))
    assert.equal(/Compte|Joueur/.test(autre), false, autre)
    assert.equal(causeCourte(null), 'erreur inconnue')

    assert.equal(lienTelechargement('https://heraliz.fr', 'HeraliZ-Launcher-Setup.exe'), 'https://heraliz.fr/telecharger/HeraliZ-Launcher-Setup.exe')
    assert.equal(lienTelechargement('http://heraliz.fr', 'HeraliZ-Launcher-Setup.exe'), null)
    assert.equal(lienTelechargement('https://heraliz.fr', '../x'), null)
    assert.equal(lienTelechargement(undefined, 'HeraliZ-Launcher.dmg'), null)

    assert.match(messageFinal({ resultat: 'aucune' }, 'fichier gardien introuvable').message, /fichier gardien introuvable/)
    assert.match(messageFinal({ resultat: 'aucune' }, 'x').detail, /heraliz\.fr\/telecharger/)
    assert.match(messageFinal({ resultat: 'manuelle', version: '1.15.2' }, 'x').message, /1\.15\.2/)
})

test('page de réparation : texte échappé, aucun script, rien de chargé d’ailleurs', () => {
    const page = pageSecours('<b>Réparation</b> & "test"', 42)
    assert.equal(page.includes('<b>Réparation</b>'), false)
    assert.match(page, /&lt;b&gt;Réparation&lt;\/b&gt; &amp; &quot;test&quot;/)
    assert.match(page, /width:42%/)
    assert.equal(/<script|src=|href=|url\(/i.test(page), false)
    assert.match(page, /default-src 'none'/)
    assert.match(pageSecours('Recherche'), /attente/)
})
