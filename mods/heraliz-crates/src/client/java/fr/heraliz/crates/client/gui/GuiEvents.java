package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.HeralizCrates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements de rendu cote client : pendant les interfaces du mod, le HUD (chat, barre d'objets, vie...) et la main du
 * joueur sont caches (seul le monde assombri reste visible derriere) ; a chaque image, le budget d'envoi des caisses a
 * la carte graphique est remis a zero.
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID, value = Dist.CLIENT)
public final class GuiEvents {
    private GuiEvents() {
    }

    private static boolean ours() {
        Screen s = Minecraft.getInstance().screen;
        return s instanceof CaseOpenScreen || s instanceof LootEditorScreen;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ours()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (ours()) event.setCanceled(true);
    }

    /** Debut d'image : budget d'envoi a la carte graphique des caisses (voir GpuScene). */
    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) fr.heraliz.crates.client.render.GpuScene.newFrame();
    }
}
