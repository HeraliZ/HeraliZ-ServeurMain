package fr.heraliz.carte.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.carte.CarteConfig;
import fr.heraliz.carte.CarteNet;
import fr.heralisz.utils.pont.Partie;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Partie client de heraliz_carte : la touche de la carte (M par défaut ; heraliz_interface la pose sur la touche marquée
 * « M » du clavier du joueur, sans doublon), l'ouverture de l'écran, les lieux reçus du serveur, le préchargement du fond
 * à la connexion et sa libération à la déconnexion.
 */
public final class PartieClient implements Partie {
    public static final String CATEGORIE = "key.categories.heraliz_carte";
    public static final KeyMapping TOUCHE = new KeyMapping("key.heraliz_carte.ouvrir", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORIE);

    private static boolean precharge;

    @Override
    public void init(IEventBus busMod) {
        busMod.addListener((RegisterKeyMappingsEvent e) -> e.register(TOUCHE));
        busMod.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) rm -> {
                    Fond.oublier();
                    precharge = false;
                }));
        CarteNet.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof CarteNet.LieuxS2C l) LieuxClient.recevoir(l);
        });
        MinecraftForge.EVENT_BUS.addListener(PartieClient::tick);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            LieuxClient.vider();
            Sources.vider();
            Fond.oublier();
            precharge = false;
        });
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!precharge) {
            precharge = true;
            Fond.precharger(CarteConfig.RELIEF.get() ? EcranCarte.RELIEF : EcranCarte.SATELLITE);
        }
        while (TOUCHE.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new EcranCarte());
        }
    }
}
