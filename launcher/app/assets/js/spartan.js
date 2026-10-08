'use strict'

/**
 * Spartan (anticheat HeraliZ, mods/spartan du dépôt) côté launcher : le jeu est lancé SANS porte d'entrée pour les
 * injecteurs.
 *
 * - protections() : options Java ajoutées à chaque lancement. -XX:+DisableAttachMechanism empêche tout programme de
 *   s'attacher au jeu déjà lancé (c'est ainsi que passent la plupart des injecteurs « à chaud »). Placée APRÈS les
 *   options du joueur : la dernière l'emporte. Java 17 : surtout pas -XX:-EnableDynamicAgentLoading (Java 21+, le jeu
 *   refuserait de démarrer).
 * - optionsSures(options) : retire des options Java réglées par le joueur tout ce qui sert à injecter du code au
 *   lancement (agent Java ou natif, chargeur de démarrage, vérification du code désactivée, attache autorisée).
 *
 * Spartan vérifie de son côté, dans le jeu, que ces protections sont bien là (réglage requireHardenedJvm du serveur).
 */

const PROTECTIONS = ['-XX:+DisableAttachMechanism']

const DANGEREUSES = [
    /^-javaagent:/i,
    /^-agentlib:/i,
    /^-agentpath:/i,
    /^-Xbootclasspath/i,
    /^-Djava\.system\.class\.loader=/i,
    /^-Djdk\.attach\.allowAttachSelf/i,
    /^-XX:\+EnableDynamicAgentLoading/i,
    /^-XX:\+StartAttachListener/i,
    /^-XX:-DisableAttachMechanism/i,
    /^-Xverify:none/i,
    /^-noverify$/i
]

/** Options Java ajoutées à chaque lancement (copie). */
function protections() {
    return [...PROTECTIONS]
}

/** Options Java du joueur sans celles qui permettent d'injecter du code. */
function optionsSures(options) {
    if (!Array.isArray(options)) {
        return []
    }
    return options.filter(o => typeof o === 'string' && !DANGEREUSES.some(re => re.test(o.trim())))
}

module.exports = { protections, optionsSures }
