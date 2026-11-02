#!/usr/bin/env node
'use strict'

// Signature Ed25519 des fichiers que le launcher vérifie (mises à jour : latest*.yml ; pack de jeu : distribution.json).
// Le cœur de vérification est launcher/app/assets/js/signature.js. Guide : launcher/docs/HERALIZ-SIGNATURE.md.
//
//   node launcher/ci/signer.mjs --generer
//       fabrique une paire de clés Ed25519 et l'affiche :
//         - CLE PUBLIQUE  -> à mettre dans app/heraliz.json ("distributionPublicKey")
//         - CLE PRIVEE    -> à mettre dans le secret GitHub LAUNCHER_SIGN_KEY (jamais dans le dépôt, jamais sur la VM)
//
//   node launcher/ci/signer.mjs --signer <fichier> [<fichier2> ...]
//       signe chaque fichier avec la clé privée lue dans la variable d'environnement LAUNCHER_SIGN_KEY et écrit
//       <fichier>.sig (base64 de la signature). Sans la clé : ne fait rien et sort en code 0 (la publication marche
//       comme avant, sans signatures, tant que le propriétaire n'a pas réglé le secret).

import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'

function generer() {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519')
    const pub = publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
    const priv = privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64')
    process.stdout.write(
        'Paire de clés Ed25519 du launcher HeraliZ.\n\n' +
        'CLE PUBLIQUE (app/heraliz.json -> "distributionPublicKey") :\n' + pub + '\n\n' +
        'CLE PRIVEE (secret GitHub LAUNCHER_SIGN_KEY, JAMAIS dans le dépôt ni sur la VM) :\n' + priv + '\n'
    )
}

function clePrivee() {
    const base64 = (process.env.LAUNCHER_SIGN_KEY || '').trim()
    if (!base64) return null
    return crypto.createPrivateKey({ key: Buffer.from(base64, 'base64'), format: 'der', type: 'pkcs8' })
}

function signer(fichiers) {
    const cle = clePrivee()
    if (!cle) {
        process.stdout.write('LAUNCHER_SIGN_KEY non réglé : aucune signature écrite (publication sans signature).\n')
        return
    }
    if (cle.asymmetricKeyType !== 'ed25519') throw new Error('LAUNCHER_SIGN_KEY n’est pas une clé Ed25519.')
    for (const fichier of fichiers) {
        const signature = crypto.sign(null, fs.readFileSync(fichier), cle).toString('base64')
        fs.writeFileSync(fichier + '.sig', signature + '\n')
        process.stdout.write('signé : ' + fichier + '.sig\n')
    }
}

// --pack <distribution.json> [<clé publique attendue>] : signe le texte du pack (textePack et textePackV2 de
// signature.js, les mêmes que le launcher recalcule) et écrit à côté distribution.sig (v1, pour les launchers 1.10 à
// 1.15 déjà installés) et distribution.sig2 (v2 : empreintes SHA-256 et date de la signature, « <date>:<signature> »,
// exigée par les launchers 1.16.0 et plus). Si une clé publique est donnée, les deux signatures sont revérifiées avec
// elle (une clé privée qui ne va pas avec la clé du launcher est refusée tout de suite, rien n'est écrit).
function signerPack(fichier, clePubliqueAttendue) {
    const cle = clePrivee()
    if (!cle) {
        process.stdout.write('LAUNCHER_SIGN_KEY non réglé : pack non signé.\n')
        return
    }
    const require = createRequire(import.meta.url)
    const { textePack, textePackV2, chargerClePublique, verifier } = require('../app/assets/js/signature.js')
    const pack = JSON.parse(fs.readFileSync(fichier, 'utf8'))
    const date = Math.floor(Date.now() / 1000)
    const texte1 = Buffer.from(textePack(pack), 'utf8')
    const texte2 = Buffer.from(textePackV2(pack, date), 'utf8')
    const signature1 = crypto.sign(null, texte1, cle).toString('base64')
    const signature2 = crypto.sign(null, texte2, cle).toString('base64')
    if (clePubliqueAttendue) {
        const publique = chargerClePublique(clePubliqueAttendue)
        if (!verifier(texte1, signature1, publique) || !verifier(texte2, signature2, publique)) {
            throw new Error('LAUNCHER_SIGN_KEY ne correspond pas à la clé publique du launcher (distributionPublicKey).')
        }
    }
    const dossier = path.dirname(fichier)
    fs.writeFileSync(path.join(dossier, 'distribution.sig'), signature1 + '\n')
    fs.writeFileSync(path.join(dossier, 'distribution.sig2'), date + ':' + signature2 + '\n')
    process.stdout.write('pack signé : ' + path.join(dossier, 'distribution.sig') + ' et distribution.sig2 (SHA-256, date ' + date + ')\n')
}

const args = process.argv.slice(2)
if (args[0] === '--generer') generer()
else if (args[0] === '--signer' && args.length >= 2) signer(args.slice(1))
else if (args[0] === '--pack' && args.length >= 2) signerPack(args[1], args[2])
else {
    process.stderr.write('usage : node launcher/ci/signer.mjs --generer | --signer <fichier> [...] | --pack <distribution.json> [<clé publique>]\n')
    process.exit(2)
}
