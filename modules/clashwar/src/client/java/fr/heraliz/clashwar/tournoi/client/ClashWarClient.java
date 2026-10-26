package fr.heraliz.clashwar.tournoi.client;

import fr.heralisz.clans.model.Banniere;
import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.tournoi.CwRegistres;
import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.clashwar.tournoi.net.VueTableau;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cote client du ClashWar (classe d'entree client de {@code Modules}) : rendu du mur « tableau du tournoi », texture
 * du tableau repeinte en debut d'image, HUD (toasts, zone), HUD vanilla masque derriere nos ecrans.
 */
public final class ClashWarClient {
    private ClashWarClient() {
    }

    private static String langue = "";

    /** Appele par {@code ClashWar.construire} sur le client physique. */
    public static void initMod(IEventBus modBus) {
        modBus.addListener(ClashWarClient::rendus);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::debutImage);
        MinecraftForge.EVENT_BUS.addListener(CwHud::rendreGui);
        MinecraftForge.EVENT_BUS.addListener(HudMatchCw::rendre);
        MinecraftForge.EVENT_BUS.addListener(HudPodium::rendre);
        // pendant un match du ClashWar, son propre bandeau (pastilles des manches, role) remplace celui du moteur
        fr.heraliz.clashwar.moteur.client.HudMoteur.masquerBandeau = HudMatchCw::actif;
        MinecraftForge.EVENT_BUS.addListener(CwHud::rendreMonde);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(ClashWarClient::masquerHud);
    }

    private static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(CwRegistres.TABLEAU_BE.get(), RenduMur::new);
    }

    private static void debutImage(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        String l = mc.getLanguageManager().getSelected();
        if (!l.equals(langue)) {
            langue = l;
            TextureTableau.invalider();
        }
        TextureTableau.avantImage();
    }

    private static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        CwClient.vider();
        TextureTableau.vider();
    }

    private static void masquerHud(RenderGuiOverlayEvent.Pre e) {
        var s = Minecraft.getInstance().screen;
        if (s instanceof EcranTableau || s instanceof EcranInscription) e.setCanceled(true);
    }
}
