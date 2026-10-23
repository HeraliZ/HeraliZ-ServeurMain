'use strict'

// Vérification de signature Ed25519 (cœur commun aux mises à jour du launcher et, plus tard, au pack de jeu).
//
// But : qu'un hôte compromis (la VM du site) ou un accès GitHub volé ne puisse PAS livrer un faux launcher ou un faux
// pack à tous les joueurs. HTTPS et l'empreinte SHA-512 ne suffisent pas : qui écrit sur la VM réécrit le fichier ET
// son empreinte ensemble. Avec une signature, il faudrait EN PLUS la clé privée, qui n'est jamais sur la VM (elle est
// un secret GitHub, fabriqué chez le propriétaire, cf. launcher/docs/HERALIZ-SIGNATURE.md).
//
// La clé publique est embarquée dans le launcher (`distributionPublicKey` de app/heraliz.json), au format base64 d'une
// clé publique Ed25519 SPKI (DER). Vide = pas de vérification (comportement d'avant : on ne bloque rien tant que le
// propriétaire n'a pas fabriqué la clé). Dès qu'une clé est réglée, tout fichier à vérifier DOIT être signé.

const crypto = require('crypto')

// Charge une clé publique Ed25519 depuis le base64 d'un SPKI DER (ce qu'écrit launcher/ci/signer.mjs --generer).
// Rend null si la clé est absente ou illisible (le launcher traite alors « pas de clé » = pas de vérification).
function chargerClePublique(base64) {
    if (typeof base64 !== 'string' || base64.trim() === '') return null
    try {
        const der = Buffer.from(base64.trim(), 'base64')
        const cle = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' })
        return cle.asymmetricKeyType === 'ed25519' ? cle : null
    } catch {
        return null
    }
}

// Vrai si `signatureBase64` est une signature Ed25519 valide de `contenu` (Buffer) par `clePublique`.
// Toute erreur (signature mal formée, clé absente…) rend faux : jamais d'exception, jamais de « valide » par défaut.
function verifier(contenu, signatureBase64, clePublique) {
    if (!clePublique || typeof signatureBase64 !== 'string' || signatureBase64.trim() === '') return false
    try {
        const signature = Buffer.from(signatureBase64.trim(), 'base64')
        if (signature.length !== 64) return false  // une signature Ed25519 fait 64 octets
        const donnees = Buffer.isBuffer(contenu) ? contenu : Buffer.from(String(contenu), 'utf8')
        return crypto.verify(null, donnees, clePublique, signature)
    } catch {
        return false
    }
}

// Texte signé pour le pack de jeu (distribution Helios). Le site réécrit les LIENS des fichiers à chaque téléchargement
// (liens signés par compte) : on signe donc ce qui décide du CONTENU installé chez le joueur, pas les liens. Pour chaque
// serveur : identifiant, adresse, version ; pour chaque module (récursivement) : type, identifiant, chemin, taille et
// empreinte MD5 (contrôlée par le launcher sur chaque fichier téléchargé). Un mod ajouté, retiré ou remplacé change
// ce texte. Lignes triées (l'ordre ne compte pas), chaque ligne en JSON (aucune ambiguïté de séparateur). La même
// fonction sert à signer (ci/signer.mjs --pack) et à vérifier (launcher-service.js) : jamais deux versions.
function textePack(raw) {
    const lignes = []
    const serveurs = Array.isArray(raw?.servers) ? raw.servers : []
    for (const s of serveurs) {
        lignes.push(JSON.stringify(['S', s?.id ?? null, s?.address ?? null, s?.minecraftVersion ?? null]))
        const parcourir = (modules, profondeur) => {
            if (!Array.isArray(modules) || profondeur > 20) return
            for (const m of modules) {
                const a = m?.artifact ?? {}
                lignes.push(JSON.stringify(['M', m?.type ?? null, m?.id ?? null, a.path ?? null, a.size ?? null,
                    typeof a.MD5 === 'string' ? a.MD5.toLowerCase() : null]))
                parcourir(m?.subModules, profondeur + 1)
            }
        }
        parcourir(s?.modules, 0)
    }
    lignes.sort()
    return 'heraliz-pack-v1\n' + lignes.join('\n') + '\n'
}

// Pack de jeu signé par HeraliZ (launcher/docs/HERALIZ-SIGNATURE.md), appelé par launcher-service.js : avec une clé
// publique réglée (distributionPublicKey de heraliz.json), un pack dont la liste des mods ou leurs empreintes ne
// correspondent pas à la signature est refusé AVANT tout téléchargement : un site piraté ne peut pas glisser un faux
// mod chez les joueurs. Sans clé réglée : comme avant. Le champ de la signature est retiré du pack avant la suite.
function verifierPack(brut, signature, config) {
    if (brut && typeof brut === 'object' && !Array.isArray(brut)) delete brut.heralizSignature
    const cle = chargerClePublique(config?.distributionPublicKey)
    if (!cle) return
    if (!verifier(Buffer.from(textePack(brut), 'utf8'), typeof signature === 'string' ? signature : '', cle)) {
        throw new Error('Le pack de jeu reçu n’est pas signé par HeraliZ : téléchargement refusé. Préviens le staff sur le Discord.')
    }
}

module.exports = { chargerClePublique, verifier, textePack, verifierPack }
