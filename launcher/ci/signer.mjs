#!/usr/bin/env node
'use strict'

// Signature Ed25519 des fichiers que le launcher vérifie (mises à jour : latest*.yml ; plus tard : pack de jeu).
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

const args = process.argv.slice(2)
if (args[0] === '--generer') generer()
else if (args[0] === '--signer' && args.length >= 2) signer(args.slice(1))
else {
    process.stderr.write('usage : node launcher/ci/signer.mjs --generer | --signer <fichier> [...]\n')
    process.exit(2)
}
