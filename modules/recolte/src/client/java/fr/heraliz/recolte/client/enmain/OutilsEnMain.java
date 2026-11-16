package fr.heraliz.recolte.client.enmain;

import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Outils de récolte en main, chez le joueur (A_FAIRE §7) : extensions client des objets ({@link ExtensionsOutil}),
 * première personne ({@link PremierePersonne}), coups ({@link CoupClient}), vue des autres ({@link VueDesAutres}).
 */
public final class OutilsEnMain {
    private OutilsEnMain() {
    }

    /** Appelé par {@code PartieClient.init}, avant la construction des objets (qui demandent leurs extensions). */
    public static void brancher() {
        OutilRecolte.RENDU_CLIENT.brancher(d -> d.sortie().accept(ExtensionsOutil.INSTANCE));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderHandEvent.class, PremierePersonne::mains);
        CoupClient.brancher();
    }
}
