/**
 * Duels : defis 1v1 / 2v2 sur Nuketown, recherche et destruction, match a mort, classes personnalisees (MATCHS_SPEC §3).
 * Perimetre des agents duels ; la killcam est dans {@code duels/killcam} (agent killcam).
 * <p>Classes d'entree attendues : {@code fr.heralisz.matchs.duels.Duels} et {@code fr.heralisz.matchs.duels.killcam.Killcam}
 * (methodes publiques statiques facultatives {@code construire(IEventBus)}, {@code paquets(MatchsNet.Registre)},
 * {@code commandes(CommandDispatcher, CommandBuildContext)}) ; client : {@code duels.client.DuelsClient} et
 * {@code duels.killcam.client.KillcamClient} ({@code etapesBanc(MatchsHarness.Registre)}).
 * Contrat du moteur : {@code fr.heralisz.matchs.moteur.api}.
 */
package fr.heralisz.matchs.duels;
