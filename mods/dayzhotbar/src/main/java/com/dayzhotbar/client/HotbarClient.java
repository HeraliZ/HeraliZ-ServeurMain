package com.dayzhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class HotbarClient {
    private static final HotbarOverlay OVERLAY = new HotbarOverlay();

    private HotbarClient() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HotbarConfig.SPEC);
        modBus.addListener(HotbarClient::registerOverlays);
        // Lowest priority and not receiving cancelled events: if another mod hides the hotbar, ours stays hidden too.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HotbarClient::replaceVanillaHotbar);
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "hotbar", OVERLAY);
    }

    private static void replaceVanillaHotbar(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()) && shouldReplace()) {
            event.setCanceled(true);
            OVERLAY.requestFrame();
        }
    }

    /** The spectator hotbar (teleport menu) is left to vanilla. */
    private static boolean shouldReplace() {
        Minecraft mc = Minecraft.getInstance();
        return HotbarConfig.ENABLED.get()
                && mc.getCameraEntity() instanceof Player
                && mc.gameMode != null
                && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
    }
}
