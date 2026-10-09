package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.fx.Deafen;
import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.client.render.DesignatorHold;
import fr.heraliz.airstrike.client.render.StrikeRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge bus client hooks. */
@Mod.EventBusSubscriber(modid = AirstrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientStrikes.tick();
        DesignatorClient.tick();
        CameraShake.tick();
        CloudFog.tick();
        Deafen.tick();
        fr.heraliz.airstrike.particle.SmokeBudget.endTick();
        FxQuality.endTick();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) DesignatorClient.onRenderTickStart();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        StrikeRenderer.render(event);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (DesignatorClient.isScoped()) event.setNewFovModifier(1.0f / DesignatorClient.zoom());
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CameraShake.apply(event);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        CloudFog.onRenderFog(event);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        CloudFog.onFogColor(event);
    }

    /** Low priority: a mod that takes over the hands first (revive animation...) keeps them. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        DesignatorHold.onRenderHand(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        DesignatorHold.endPlayer();
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        DesignatorHold.endPlayer();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!DesignatorClient.isScoped()) return;
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (DesignatorClient.onMouseButton(event.getButton(), event.getAction())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (DesignatorClient.onScroll(event.getScrollDelta())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStrikes.clear();
        CloudFog.reset();
        Deafen.reset();
        fr.heraliz.airstrike.entity.EffectCloudEntity.resetClientCount();
    }
}
