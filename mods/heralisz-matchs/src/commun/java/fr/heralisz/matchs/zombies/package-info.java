/**
 * Zombies : mode facon Call of Duty Zombies en groupe (MATCHS_SPEC §4). Perimetres : {@code zombies/jeu} (regles,
 * manches, points, achats), {@code zombies/contenu} (entites, blocs-entites, objets, sons), {@code zombies/client}
 * (HUD, rendus, ecrans).
 * <p>Classes d'entree attendues : {@code fr.heralisz.matchs.zombies.jeu.ZombiesJeu} et
 * {@code fr.heralisz.matchs.zombies.contenu.ZombiesContenu} (methodes publiques statiques facultatives
 * {@code construire(IEventBus)}, {@code paquets(MatchsNet.Registre)}, {@code commandes(CommandDispatcher, CommandBuildContext)}) ;
 * client : {@code fr.heralisz.matchs.zombies.client.ZombiesClient} ({@code etapesBanc(MatchsHarness.Registre)}).
 * Contrat du moteur : {@code fr.heralisz.matchs.moteur.api}.
 */
package fr.heralisz.matchs.zombies;
