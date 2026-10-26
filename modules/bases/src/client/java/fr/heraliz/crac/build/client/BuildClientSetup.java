package fr.heraliz.crac.build.client;

import fr.heraliz.crac.build.world.ClientMirror;
import fr.heraliz.crac.client.RaidBlockOverlay;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registrations (mod bus) and session cleanup (Forge bus) of the building UI. */
@OnlyIn(Dist.CLIENT)
public final class BuildClientSetup {
    private BuildClientSetup() {
    }

    @Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(BuildKeys.ROTATE);
            event.register(BuildKeys.MENU);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "building", BuildHud::render);
            // once per frame, above everything (it used to be drawn after every overlay of the HUD)
            event.registerAboveAll("raid_block", RaidBlockOverlay::render);
            // 25/10 : icône de la Purge en haut à gauche (à la place de la barre rouge de Minecraft)
            event.registerAboveAll("purge", fr.heraliz.crac.client.PurgeHud::render);
        }

        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> BuildIcons.reset());
        }
    }

    @Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                RadialMenuScreen.tickLatch();
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            fr.heraliz.crac.client.PurgeHud.vider();
            ClientMirror.clear();
            PieceInfoCache.clear();
            HammerController.reset();
        }

        /** Dimension change or leaving the world: the old level's pieces must not linger in the mirror. */
        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                ClientMirror.clear();
                PieceInfoCache.clear();
                HammerController.reset();
            }
        }
    }
}
