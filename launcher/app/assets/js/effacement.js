// Compte banni : le launcher retire de ce PC les fichiers du jeu HeraliZ qu'il a téléchargés (mods du pack, dossier
// tacz, configs des mods, liste des mods, ancienne copie de la distribution) et ne les redonne plus
// avant un nouvel accord du site (compte-banni.json, launcher-service.js). Gardés : le launcher, sa config, ses
// journaux, et dans le dossier du jeu les réglages du joueur (options.txt), ses captures d'écran, ses journaux, ses
// rapports de plantage et les packs de ressources et de shaders qu'il a ajoutés lui-même (le pack HeraliZ n'en livre
// aucun : le pack de textures du serveur est dans un mod). Java, Minecraft et Forge (fichiers publics, communs à tous) restent aussi.
//
// Sécurité : rien n'est jamais effacé hors du dossier du launcher (racine). Chaque cible est contrôlée par son chemin
// réel (liens résolus) ; une cible qui est elle-même un lien est refusée ; à l'intérieur d'un dossier effacé, un lien
// (ou une jonction Windows) est retiré lui-même, jamais suivi.
const fs = require('node:fs')
const path = require('node:path')

// Dans le dossier du jeu (instances/<serveur>) : ce qui appartient au joueur.
const GARDER_INSTANCE = new Set(['options.txt', 'optionsof.txt', 'optionsshaders.txt', 'screenshots', 'logs', 'crash-reports',
    'resourcepacks', 'shaderpacks'])
// Anciennes copies de la distribution Helios, à la racine du launcher (distromanager.js).
const CACHES_DISTRIBUTION = ['distribution.json', 'distribution_dev.json']

const comparable = chemin => process.platform === 'win32' || process.platform === 'darwin' ? chemin.toLowerCase() : chemin

// « enfant » est-il strictement à l'intérieur de « parent » (jamais égal) ? Chemins absolus, déjà résolus.
function strictementDans(parent, enfant) {
    const relatif = path.relative(comparable(parent), comparable(enfant))
    return relatif !== '' && !relatif.startsWith('..') && !path.isAbsolute(relatif)
}

// Chemin réel de « cible » si on peut l'effacer sans risque : à l'intérieur de « racine » (pas la racine elle-même), y
// compris une fois les liens des dossiers parents résolus, et pas un lien elle-même. null sinon, ou si elle n'existe pas.
function cheminSur(racine, cible) {
    if (typeof racine !== 'string' || typeof cible !== 'string' || !racine || !cible) return null
    if (!path.isAbsolute(racine) || !path.isAbsolute(cible)) return null
    const racineLue = path.resolve(racine)
    const cibleLue = path.resolve(cible)
    if (!strictementDans(racineLue, cibleLue)) return null
    let infos
    try { infos = fs.lstatSync(cibleLue) } catch { return null }
    if (infos.isSymbolicLink()) return null
    let racineReelle
    let cibleReelle
    try {
        racineReelle = fs.realpathSync.native(racineLue)
        cibleReelle = fs.realpathSync.native(cibleLue)
    } catch { return null }
    return strictementDans(racineReelle, cibleReelle) ? cibleReelle : null
}

// Efface un fichier ou un dossier sans jamais suivre de lien ; continue malgré les erreurs (fichier verrouillé par
// Windows tant que le jeu tourne…) et les rend : [{ chemin, code }].
function effacerSansSuivre(chemin, erreurs = []) {
    let infos
    try { infos = fs.lstatSync(chemin) } catch (erreur) {
        if (erreur?.code !== 'ENOENT') erreurs.push({ chemin, code: erreur?.code || 'ERREUR' })
        return erreurs
    }
    const avant = erreurs.length
    try {
        if (infos.isSymbolicLink()) {
            // lien ou jonction : seul le lien part (une jonction Windows se retire comme un dossier vide)
            try { fs.unlinkSync(chemin) } catch { fs.rmdirSync(chemin) }
        } else if (infos.isDirectory()) {
            for (const nom of fs.readdirSync(chemin)) effacerSansSuivre(path.join(chemin, nom), erreurs)
            fs.rmdirSync(chemin)
        } else {
            fs.unlinkSync(chemin)
        }
    } catch (erreur) {
        // dossier pas vide à cause d'un fichier verrouillé : l'erreur du fichier est déjà notée
        if (!(infos.isDirectory() && erreurs.length > avant)) erreurs.push({ chemin, code: erreur?.code || 'ERREUR' })
    }
    return erreurs
}

// Cibles à effacer pour le pack HeraliZ : [chemins absolus] (non contrôlés : effacerDonneesJeu les contrôle).
function ciblesJeu({ racine, dossierCommun, dossierInstances, serveur }) {
    const cibles = [path.join(dossierCommun, 'modstore')]
    if (typeof serveur === 'string' && /^[\w.-]+$/.test(serveur)) {
        const instance = path.join(dossierInstances, serveur)
        let noms = []
        try { noms = fs.readdirSync(instance) } catch { /* pas encore de dossier du jeu */ }
        for (const nom of noms) if (!GARDER_INSTANCE.has(nom.toLowerCase())) cibles.push(path.join(instance, nom))
    }
    for (const nom of CACHES_DISTRIBUTION) cibles.push(path.join(racine, nom))
    return cibles
}

// Efface les fichiers du jeu HeraliZ de ce PC : { effaces: [chemins], refuses: [chemins], erreurs: [{ chemin, code }] }.
// Une cible hors de la racine (ou un lien) est refusée et laissée telle quelle.
function effacerDonneesJeu(options) {
    const resultat = { effaces: [], refuses: [], erreurs: [] }
    for (const cible of ciblesJeu(options)) {
        if (!fs.existsSync(cible) && !estLien(cible)) continue
        const sur = cheminSur(options.racine, cible)
        if (!sur) { resultat.refuses.push(cible); continue }
        const avant = resultat.erreurs.length
        effacerSansSuivre(sur, resultat.erreurs)
        if (resultat.erreurs.length === avant) resultat.effaces.push(sur)
    }
    return resultat
}

// Anciens dossiers de gunpacks (noms hérités d'ArkaniaZ, renommés le 26/10 ; Cislumen_Armory et heraliz_wonder retirés) : le pack ne les
// contient plus, mais le launcher ne retire jamais seul un fichier tacz qui a quitté le pack. Liste fixe, jamais un
// dossier inconnu : seul ce qui est nommé ici, dans <instance>/tacz, est effacé (même contrôle que effacerDonneesJeu).
const ANCIENS_GUNPACKS = ['arkaniaz_pack_0_converted', 'arkaniaz_pack_1_converted', 'arkaniaz_pack_2_converted',
    'arkaniaz_pack_3_converted', 'arkaniaz_pack_4_converted', 'arkaniaz_pack_5_converted', 'arkaniaz_custom_converted',
    'Cislumen_Armory', 'heraliz_wonder']

// { effaces: [chemins], erreurs: [{ chemin, code }] } ; rien hors de <dossierInstances>/<serveur>/tacz.
function retirerAnciensGunpacks({ dossierInstances, serveur }) {
    const resultat = { effaces: [], erreurs: [] }
    if (typeof serveur !== 'string' || !/^[\w.-]+$/.test(serveur) || typeof dossierInstances !== 'string') return resultat
    const tacz = path.join(dossierInstances, serveur, 'tacz')
    for (const nom of ANCIENS_GUNPACKS) {
        const sur = cheminSur(tacz, path.join(tacz, nom))
        if (!sur) continue
        const avant = resultat.erreurs.length
        effacerSansSuivre(sur, resultat.erreurs)
        if (resultat.erreurs.length === avant) resultat.effaces.push(sur)
    }
    return resultat
}

function estLien(chemin) {
    try { return fs.lstatSync(chemin).isSymbolicLink() } catch { return false }
}

module.exports = { cheminSur, effacerSansSuivre, effacerDonneesJeu, ciblesJeu, retirerAnciensGunpacks, ANCIENS_GUNPACKS, GARDER_INSTANCE }
