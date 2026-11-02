/**
 * ClashWar : tournoi a elimination directe entre clans (MATCHS_SPEC §2). Perimetre de l'agent clashwar.
 * <p>Classe d'entree attendue : {@code fr.heraliz.clashwar.tournoi.ClashWar} (methodes publiques statiques facultatives
 * {@code construire(IEventBus)}, {@code paquets(MatchsNet.Registre)}, {@code commandes(CommandDispatcher, CommandBuildContext)}),
 * client : {@code fr.heraliz.clashwar.tournoi.client.ClashWarClient} ({@code etapesBanc(MatchsHarness.Registre)}).
 * Contrat du moteur : {@code fr.heraliz.clashwar.moteur.api}. 02/11 (audit) : reglages, etat, formats et tableau du
 * tournoi ({@code CwConfig}, {@code CwEtat}, {@code Format}, {@code Tableau}) sont dans src/serveur ; restent partages
 * les paquets ({@code net}), le mur du tableau ({@code bloc}) et les registres.
 */
package fr.heraliz.clashwar.tournoi;
