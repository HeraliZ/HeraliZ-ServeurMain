'use strict'

// Démarrage de secours du launcher (processus principal, index.js). Si le launcher installé n'arrive pas à démarrer
// (fichier manquant ou cassé : les 1.14.0 à 1.15.0 plantaient ainsi), il ne s'arrête plus tout de suite : il cherche
// d'abord une version plus récente sur heraliz.fr. Sous Windows et en AppImage, elle se télécharge et s'installe seule,
// puis le launcher redémarre réparé ; sinon le joueur est invité à le retélécharger.
// Ce fichier ne charge rien (ni Electron, ni les autres fichiers du launcher) : il doit marcher même quand le reste du
// launcher est cassé.

// plus aucune nouvelle de la mise à jour pendant ce temps (vérification ou téléchargement bloqué) : on abandonne
const SANS_NOUVELLE_MS = 2 * 60 * 1000

// Cherche une mise à jour et attend son issue.
// - verifier() : celle de MiseAJour (mise-a-jour.js) ;
// - abonner(ecouteur) : l'écouteur reçoit les états envoyés par MiseAJour ({ etat: 'telechargement', pourcent },
//   { etat: 'installation' | 'erreur' | 'plus-tard' }) ; rend de quoi se désabonner ;
// - progression(pourcent, version) : appelée pendant le téléchargement.
// Rend { resultat, version } : 'installation' (MiseAJour installe la nouvelle version et relance le launcher),
// 'manuelle' (Mac, .deb : à télécharger à la main), 'aucune', 'bloquee' (installation déjà ratée 2 fois),
// 'signature' (mise à jour refusée par sécurité), 'erreur' ou 'delai'.
function attendreReparation({ verifier, abonner, progression = () => {}, delaiMs = SANS_NOUVELLE_MS }) {
    return new Promise(resolve => {
        let fini = false
        let version = null
        let minuterie = null
        let desabonner = () => {}
        const finir = resultat => {
            if (fini) return
            fini = true
            clearTimeout(minuterie)
            try { desabonner() } catch { /* rien à faire */ }
            resolve({ resultat, version })
        }
        const relancerMinuterie = () => {
            clearTimeout(minuterie)
            minuterie = setTimeout(() => finir('delai'), delaiMs)
        }
        let recherche = null
        // fin du téléchargement : après la réponse de verifier() (numéro de la version connu)
        const finirApres = resultat => recherche.catch(() => null).then(() => finir(resultat))
        // abonné avant la demande : aucun état perdu, même si le téléchargement échoue tout de suite
        desabonner = abonner(etat => {
            if (etat?.etat === 'telechargement') {
                relancerMinuterie()
                try { progression(etat.pourcent, version) } catch { /* affichage seulement */ }
            } else if (etat?.etat === 'installation') finirApres('installation')
            else if (etat?.etat === 'erreur' || etat?.etat === 'plus-tard') finirApres('erreur')
        }) || (() => {})
        relancerMinuterie()
        recherche = Promise.resolve().then(() => verifier())
        recherche
            .then(maj => {
                if (typeof maj?.version === 'string') version = maj.version
                // téléchargement lancé : la suite arrive par abonner()
                if (maj?.disponible) return
                finir(maj?.manuelle ? 'manuelle' : maj?.bloquee ? 'bloquee' : maj?.signatureInvalide ? 'signature' : 'aucune')
            })
            .catch(() => finir('erreur'))
    })
}

// Lien direct du launcher à retélécharger (même fichier que le bouton du site), seulement en https.
function lienTelechargement(siteUrl, fichier) {
    if (typeof siteUrl !== 'string' || typeof fichier !== 'string' || !/^[\w.-]+$/.test(fichier)) return null
    try {
        const url = new URL(`/telecharger/${fichier}`, siteUrl)
        return url.protocol === 'https:' ? url.href : null
    } catch { return null }
}

// Cause d'un démarrage raté, courte et sans chemin du PC du joueur (« fichier gardien introuvable »).
function causeCourte(erreur) {
    const ligne = String(erreur?.message || erreur || '').split('\n')[0]
    const module = /Cannot find module '([^']+)'/.exec(ligne)
    if (module) return `fichier ${module[1].split(/[\\/]/).pop().replace(/\.js$/, '')} introuvable`
    // nom du compte Windows (dossier personnel) retiré d'abord, puis chaque chemin réduit à son dernier élément
    const texte = ligne.replace(/([\\/](?:Users|home)[\\/])[^\\/'"\r\n]+/gi, '$1…')
        .replace(/(?:[A-Za-z]:)?(?:[\\/][^\s'"\\/]+)+[\\/]([^\s'"\\/]+)/g, '$1').trim()
    return (texte || 'erreur inconnue').slice(0, 160)
}

// Message affiché quand le launcher ne s'est pas réparé tout seul : { message, detail }.
function messageFinal(issue, cause) {
    if (issue?.resultat === 'manuelle' && issue.version) {
        return {
            message: `Une nouvelle version du launcher (${issue.version}) corrige ce problème.`,
            detail: 'Télécharge-la sur heraliz.fr/telecharger et installe-la par-dessus : ton compte et ton jeu sont gardés.'
        }
    }
    return {
        message: `Le launcher n’a pas pu démarrer (${cause}).`,
        detail: 'Retélécharge-le sur heraliz.fr/telecharger puis réinstalle-le par-dessus : ton compte et ton jeu sont gardés.'
    }
}

const echapper = texte => String(texte ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', '\'': '&#39;' })[c])

// Petite page de la fenêtre de réparation : aucun script, aucune commande vers le launcher, rien de chargé d'ailleurs
// (polices et images du launcher comprises : elles peuvent manquer, c'est justement le cas à réparer).
function pageSecours(texte, pourcent = null) {
    const barre = Number.isFinite(pourcent)
        ? `<div class="barre"><i style="width:${Math.max(0, Math.min(100, Math.round(pourcent)))}%"></i></div>`
        : '<div class="barre attente"><i></i></div>'
    return '<!doctype html><html lang="fr"><head><meta charset="utf-8">'
        + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'">'
        + '<title>HeraliZ Launcher</title><style>'
        + 'html,body{margin:0;height:100%;background:#06070a;color:#e8e9ec;font:14px "Segoe UI",system-ui,sans-serif;}'
        + 'body{display:flex;flex-direction:column;justify-content:center;padding:0 28px;box-sizing:border-box;gap:14px;user-select:none;}'
        + 'h1{margin:0;font-size:13px;letter-spacing:.14em;color:#ff5a4e;font-weight:700;}'
        + 'p{margin:0;line-height:1.45;}'
        + '.barre{height:6px;border-radius:3px;background:rgba(255,255,255,.1);overflow:hidden;}'
        + '.barre i{display:block;height:100%;background:#ff5a4e;}'
        + '.attente i{width:30%;animation:va 1.4s ease-in-out infinite alternate;}'
        + '@keyframes va{from{margin-left:0}to{margin-left:70%}}'
        + '</style></head><body><h1>HERALIZ LAUNCHER</h1>'
        + `<p>${echapper(texte)}</p>${barre}</body></html>`
}

module.exports = { attendreReparation, lienTelechargement, causeCourte, messageFinal, pageSecours, SANS_NOUVELLE_MS }
