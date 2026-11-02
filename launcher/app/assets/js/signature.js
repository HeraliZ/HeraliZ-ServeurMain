'use strict'

// Vérification de signature Ed25519 (cœur commun aux mises à jour du launcher et au pack de jeu).
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

// Texte signé pour le pack de jeu, première forme (v1, launchers 1.10 à 1.15). Le site réécrit les LIENS des fichiers à
// chaque téléchargement (liens signés par compte) : on signe donc ce qui décide du CONTENU installé chez le joueur, pas
// les liens. Pour chaque serveur : identifiant, adresse, version ; pour chaque module (récursivement) : type,
// identifiant, chemin, taille et empreinte MD5. Lignes triées (l'ordre ne compte pas), chaque ligne en JSON (aucune
// ambiguïté de séparateur). Toujours produite par la mise en ligne pour les launchers déjà installés (distribution.sig).
function textePack(raw) {
    return 'heraliz-pack-v1\n' + lignesPack(raw, false).join('\n') + '\n'
}

// Texte signé du pack, forme v2 (launcher 1.16.0, audit du 02/11) : en plus de la v1, l'empreinte SHA-256 de chaque
// fichier du pack (champ SHA256 de l'artefact, contrôlé par le launcher sur chaque fichier avant de lancer le jeu ; null
// pour les fichiers pris chez Mojang ou Forge, contrôlés par leur MD5) et la date de la signature (secondes) : un pack
// plus ancien que le dernier déjà accepté par ce launcher est refusé (un site piraté ne peut pas resservir un vieux pack
// signé). La même fonction sert à signer (ci/signer.mjs --pack) et à vérifier : jamais deux versions.
function textePackV2(raw, date) {
    return 'heraliz-pack-v2\n' + JSON.stringify(['D', date]) + '\n' + lignesPack(raw, true).join('\n') + '\n'
}

function lignesPack(raw, avecSha256) {
    const lignes = []
    const serveurs = Array.isArray(raw?.servers) ? raw.servers : []
    for (const s of serveurs) {
        lignes.push(JSON.stringify(['S', s?.id ?? null, s?.address ?? null, s?.minecraftVersion ?? null]))
        const parcourir = (modules, profondeur) => {
            if (!Array.isArray(modules) || profondeur > 20) return
            for (const m of modules) {
                const a = m?.artifact ?? {}
                const ligne = ['M', m?.type ?? null, m?.id ?? null, a.path ?? null, a.size ?? null,
                    typeof a.MD5 === 'string' ? a.MD5.toLowerCase() : null]
                if (avecSha256) ligne.push(typeof a.SHA256 === 'string' ? a.SHA256.toLowerCase() : null)
                lignes.push(JSON.stringify(ligne))
                parcourir(m?.subModules, profondeur + 1)
            }
        }
        parcourir(s?.modules, 0)
    }
    return lignes.sort()
}

// Signature v2 telle que le site la joint au pack (heralizSignature2) ou que la mise en ligne la dépose
// (distribution.sig2) : « <date en secondes>:<signature base64> ». { date, signature } ou null si la forme est fausse.
function lireSignatureV2(texte) {
    const m = /^(\d{9,11}):([A-Za-z0-9+/]{86}==)$/.exec(typeof texte === 'string' ? texte.trim() : '')
    return m ? { date: Number(m[1]), signature: m[2] } : null
}

const PACK_NON_SIGNE = 'Le pack de jeu reçu n’est pas signé par HeraliZ : téléchargement refusé. Préviens le staff sur le Discord.'

// Pack de jeu signé par HeraliZ (launcher/docs/HERALIZ-SIGNATURE.md), appelé par launcher-service.js : avec une clé
// publique réglée (distributionPublicKey de heraliz.json), un pack dont la liste des mods, leurs empreintes (MD5 et
// SHA-256) ou la date ne correspondent pas à la signature v2 est refusé AVANT tout téléchargement : un site piraté ne
// peut pas glisser un faux mod chez les joueurs. Un pack signé avant « dateMin » (date du dernier pack accepté sur ce
// PC) est refusé aussi. Les champs des signatures sont retirés du pack avant la suite. Rend la date de la signature
// (à retenir pour la prochaine fois), ou null sans clé réglée (comportement d'avant : pas de vérification).
function verifierPack(brut, signatureV2, config, { dateMin = 0 } = {}) {
    if (brut && typeof brut === 'object' && !Array.isArray(brut)) {
        delete brut.heralizSignature
        delete brut.heralizSignature2
    }
    const cle = chargerClePublique(config?.distributionPublicKey)
    if (!cle) return null
    const lue = lireSignatureV2(signatureV2)
    if (!lue || !verifier(Buffer.from(textePackV2(brut, lue.date), 'utf8'), lue.signature, cle)) throw new Error(PACK_NON_SIGNE)
    if (Number.isSafeInteger(dateMin) && lue.date < dateMin) {
        throw new Error('Le pack de jeu reçu est plus ancien que celui déjà installé sur ce PC : réessaie dans quelques minutes, sinon préviens le staff sur le Discord.')
    }
    return lue.date
}

module.exports = { chargerClePublique, verifier, textePack, textePackV2, lireSignatureV2, verifierPack }
