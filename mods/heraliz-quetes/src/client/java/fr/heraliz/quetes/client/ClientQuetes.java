package fr.heraliz.quetes.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Langue;
import fr.heraliz.quetes.pont.Partie;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Partie client de HeraliZ Quêtes : rendu des PNJ (skin, nom, bulle), écrans (dialogue, journal /quetes, note, clavier),
 * quête suivie à droite de l'écran et notifications, lueurs des points de quête. Tout ce qui s'affiche vient du serveur.
 */
public final class ClientQuetes implements Partie {
    public static final KeyMapping JOURNAL = new KeyMapping("key.heraliz_quetes.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.heraliz_quetes");

    @Override
    public void init(IEventBus busMod) {
        Langue.brancher(() -> Minecraft.getInstance().getLanguageManager().getSelected());
        busMod.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(HeralizQuetes.PNJ.get(), PnjRendu::new));
        busMod.addListener((RegisterKeyMappingsEvent e) -> e.register(JOURNAL));
        busMod.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("quetes", HudQuetes::rendu));
        MinecraftForge.EVENT_BUS.addListener(ClientQuetes::tick);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            HudQuetes.vider();
            Bulles.vider();
            Lueurs.vider();
            PnjRendu.vider();
        });

        Reseau.DIALOGUE.brancher(EcranDialogue::recevoir);
        Reseau.JOURNAL.brancher(EcranJournal::recevoir);
        Reseau.SUIVI.brancher(HudQuetes::suivi);
        Reseau.NOTIF.brancher(HudQuetes::notif);
        Reseau.NOTE.brancher(EcranNote::ouvrir);
        Reseau.CLAVIER.brancher(EcranClavier::ouvrir);
        Reseau.BULLE.brancher(Bulles::recevoir);
        Reseau.LUEURS.brancher(Lueurs::recevoir);
        Reseau.BOUTIQUE.brancher(EcranMarchand::recevoir);
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (JOURNAL.consumeClick()) {
            if (mc.screen == null) Reseau.CANAL.sendToServer(new Reseau.DemandeJournal(true));
        }
        Lueurs.tick(mc);
    }
}
