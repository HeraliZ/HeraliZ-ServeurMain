package fr.heraliz.recolte.client.enmain;

import fr.heraliz.recolte.objets.OutilRecolte;

/**
 * Outils de récolte en main, chez le joueur (A_FAIRE §7) : extensions client des objets ({@link ExtensionsOutil}).
 */
public final class OutilsEnMain {
    private OutilsEnMain() {
    }

    /** Appelé par {@code PartieClient.init}, avant la construction des objets (qui demandent leurs extensions). */
    public static void brancher() {
        OutilRecolte.RENDU_CLIENT.brancher(d -> d.sortie().accept(ExtensionsOutil.INSTANCE));
    }
}
