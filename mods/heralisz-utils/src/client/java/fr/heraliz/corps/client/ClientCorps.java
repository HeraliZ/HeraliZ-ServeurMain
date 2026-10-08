package fr.heraliz.corps.client;

import fr.heraliz.corps.CorpsConfig;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.registre.Registres;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.UUID;

/**
 * Cote client : rendu des corps, ecran de fouille, petit panneau quand on regarde un corps.
 * <p>Toutes les 10 ticks (jamais pendant le rendu) : quels corps dessiner (les plus proches, au plus N au total et M par
 * chunk, dans la distance de rendu) et lesquels sont du clan du joueur. Tableaux reutilises : aucune allocation.
 */
public final class ClientCorps {
    private ClientCorps() {
    }

    private static int horloge;
    private static CorpsEntity[] tri = new CorpsEntity[16];
    private static double[] distances = new double[16];
    private static final Long2IntOpenHashMap PAR_CHUNK = new Long2IntOpenHashMap();

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(Registres.CORPS.get(), CorpsRenderer::new);
            // anciens corps : jamais envoyes aux clients, rendu vide par securite
            if (Registres.convertisseur) e.registerEntityRenderer(Registres.ANCIEN.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        });
        bus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> MenuScreens.register(Registres.MENU.get(), CorpsEcran::new)));
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "corps", HudCorps::rendre));
        MinecraftForge.EVENT_BUS.addListener(Marqueurs::dessiner);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) tick();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            CorpsEntity.CLIENT.clear();
            Peaux.vider();
        });
    }

    private static void tick() {
        if (++horloge < 10) return;
        horloge = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CorpsEntity.CLIENT.isEmpty()) return;
        majVisibles(mc);
    }

    /** Plafonds de rendu : les plus proches d'abord (tri par insertion sur des tableaux reutilises). */
    static void majVisibles(Minecraft mc) {
        int n = CorpsEntity.CLIENT.size();
        if (tri.length < n) {
            tri = new CorpsEntity[n * 2];
            distances = new double[n * 2];
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < n; i++) {
            CorpsEntity c = CorpsEntity.CLIENT.get(i);
            double d = c.distanceToSqr(cam);
            int j = i - 1;
            while (j >= 0 && distances[j] > d) {
                distances[j + 1] = distances[j];
                tri[j + 1] = tri[j];
                j--;
            }
            distances[j + 1] = d;
            tri[j + 1] = c;
        }
        PAR_CHUNK.clear();
        int rendus = 0;
        int max = CorpsConfig.maxRendus, maxChunk = CorpsConfig.maxRendusParChunk;
        double loin = CorpsConfig.distanceRenduCarre;
        UUID monClan = ClansClient.monClan();
        for (int i = 0; i < n; i++) {
            CorpsEntity c = tri[i];
            tri[i] = null;
            boolean ok = distances[i] <= loin && rendus < max;
            if (ok) {
                long cle = (((long) (c.getBlockX() >> 4)) << 32) ^ ((c.getBlockZ() >> 4) & 0xFFFFFFFFL);
                int deja = PAR_CHUNK.get(cle);
                if (deja >= maxChunk) ok = false;
                else PAR_CHUNK.put(cle, deja + 1);
            }
            c.rendu = ok;
            if (ok) rendus++;
            c.clanAmi = monClan != null && monClan.equals(c.clan());
        }
        dernierRendus = rendus;
    }

    /** Corps dessines au dernier calcul (banc d'essai). */
    public static int dernierRendus;
}
