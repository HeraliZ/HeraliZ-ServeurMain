package fr.heraliz.quetes.client;

import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Chat caché pendant la visite du Guide et le tutoriel des bases avec le Commandant Varga (03/11, retour du propriétaire :
 * « dans le tuto de Varga, désactiver le chat, il ne faut plus le voir »). Le serveur décide quand ({@link Reseau.ChatCache},
 * {@code Accueil.chat}) ; ici, tant qu'il est caché :
 * <ul>
 *     <li>rien n'est dessiné à la place du chat (lignes, notifications d'extraction), ni le chat de Minecraft ni celui
 *     de HeraliZ Grades : la place est prise avant eux (priorité la plus haute) ;</li>
 *     <li>le chat ne s'ouvre pas (T, /, ni aucun écran de chat) : à sa place, « Appeler le staff »
 *     ({@link EcranAideStaff}, seulement {@code /aide}) ; s'il était ouvert à ce moment, il se ferme.</li>
 * </ul>
 * Les messages reçus pendant ce temps ne sont pas perdus : le chat les garde et les montre dès qu'on le rouvre. Avant
 * (radio de Varga, 18/10), seul le chat de Minecraft était caché pendant une consigne : le chat de HeraliZ Grades (25/10),
 * dessiné avant, restait visible et s'ouvrait.
 */
final class ChatTutoriel {
    private static boolean cache;

    private ChatTutoriel() {
    }

    static void recevoir(Reseau.ChatCache m) {
        cache = m.cache();
        Minecraft mc = Minecraft.getInstance();
        if (cache && mc.screen instanceof ChatScreen) mc.setScreen(null);
    }

    /** Déconnexion : le chat revient (un autre serveur, le menu…). */
    static void vider() {
        cache = false;
    }

    /** Place du chat : rien n'est dessiné tant qu'il est caché (avant le chat de HeraliZ Grades, priorité HAUTE). */
    static void overlay(RenderGuiOverlayEvent.Pre e) {
        if (cache && e.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) e.setCanceled(true);
    }

    /**
     * Le chat ne s'ouvre pas tant qu'il est caché (avant que HeraliZ Grades ne le remplace par le sien) : à sa place, la
     * petite fenêtre « Appeler le staff » (03/11, réponse du propriétaire : pas de commande pendant le tutoriel, sauf
     * appeler le staff), qui ne peut envoyer que {@code /aide <message>}.
     */
    static void ouverture(ScreenEvent.Opening e) {
        if (cache && e.getNewScreen() instanceof ChatScreen) e.setNewScreen(EcranAideStaff.nouveau());
    }
}
