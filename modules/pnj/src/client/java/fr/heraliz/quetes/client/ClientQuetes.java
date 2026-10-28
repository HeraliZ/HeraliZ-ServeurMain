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
 * Partie client de HeraliZ Quêtes : rendu des PNJ (skin, nom), écrans (dialogue, journal /quetes, note, clavier),
 * quête suivie à droite de l'écran et notifications, radio du Commandant Varga en bas à gauche (tutoriel des bases),
 * lueurs des points de quête, repères des PNJ au spawn (Reperes). Tout ce qui s'affiche vient du serveur.
 */
public final class ClientQuetes implements Partie {
    public static final KeyMapping JOURNAL = new KeyMapping("key.heraliz_quetes.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.heraliz_quetes");
    /** Quêtes du jour (26/10) : sans touche par défaut (le joueur la choisit ; /journalieres et le bouton du journal). */
    public static final KeyMapping JOURNALIERES = new KeyMapping("key.heraliz_quetes.journalieres", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.heraliz_quetes");

    @Override
    public void init(IEventBus busMod) {
        Langue.brancher(() -> Minecraft.getInstance().getLanguageManager().getSelected());
        busMod.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(HeralizQuetes.PNJ.get(), PnjRendu::new));
        busMod.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(JOURNAL);
            e.register(JOURNALIERES);
        });
        // écran du recycleur (25/10) : menu avec cases, barre de progression et aperçu
        busMod.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(HeralizQuetes.RECYCLEUR_MENU.get(), EcranRecycleur::new)));
        busMod.addListener((RegisterGuiOverlaysEvent e) -> {
            e.registerAboveAll("quetes", HudQuetes::rendu);
            e.registerAboveAll("radio", RadioVarga::rendu);
        });
        MinecraftForge.EVENT_BUS.addListener(ClientQuetes::tick);
        Guidage.brancher();   // 28/10 : traînée verte et surbrillance vers le Guide, puis Varga
        MinecraftForge.EVENT_BUS.addListener(Reperes::rendu);
        MinecraftForge.EVENT_BUS.addListener(RadioVarga::cacherChat);
        MinecraftForge.EVENT_BUS.addListener(Presentation::tick);
        MinecraftForge.EVENT_BUS.addListener(Presentation::regard);
        MinecraftForge.EVENT_BUS.addListener(Presentation::main);
        MinecraftForge.EVENT_BUS.addListener(Presentation::interface_);
        MinecraftForge.EVENT_BUS.addListener(Presentation::aide);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            HudQuetes.vider();
            RadioVarga.vider();
            Presentation.vider();
            Lueurs.vider();
            Guidage.vider();
            PnjRendu.vider();
        });

        Reseau.DIALOGUE.brancher(EcranDialogue::recevoir);
        Reseau.JOURNAL.brancher(EcranJournal::recevoir);
        Reseau.SUIVI.brancher(HudQuetes::suivi);
        Reseau.NOTIF.brancher(HudQuetes::notif);
        Reseau.NOTE.brancher(EcranNote::ouvrir);
        Reseau.CLAVIER.brancher(EcranClavier::ouvrir);
        Reseau.LUEURS.brancher(Lueurs::recevoir);
        Reseau.BOUTIQUE.brancher(EcranMarchand::recevoir);
        Reseau.EDITION.brancher(EcranEditionMarchand::recevoir);
        Reseau.RADIO.brancher(RadioVarga::recevoir);
        Reseau.PRESENTATION.brancher(Presentation::lancer);
        Reseau.APERCU_RECYCLAGE.brancher(EcranRecycleur::recevoir);
        Reseau.JOUR.brancher(EcranJournalieres::recevoir);
        Reseau.JOUR_TIRAGE.brancher(EcranJournalieres::recevoirTirage);
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (JOURNAL.consumeClick()) {
            if (mc.screen == null) Reseau.CANAL.sendToServer(new Reseau.DemandeJournal(true));
        }
        while (JOURNALIERES.consumeClick()) {
            if (mc.screen == null) Reseau.CANAL.sendToServer(new Reseau.JourAction(0, ""));
        }
        Lueurs.tick(mc);
        Guidage.tick(mc);
    }
}
