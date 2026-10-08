package fr.heralisz.tags.client.gui;

import fr.heralisz.tags.HeraliszTags;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pendant les ecrans du mod, le HUD (chat, barre d'objets, vie...) et la main du joueur sont caches : seul le monde
 * assombri reste visible derriere le verre.
 */
@Mod.EventBusSubscriber(modid = HeraliszTags.MODID, value = Dist.CLIENT)
public final class GuiEvents {
    private GuiEvents() {
    }

    private static boolean ours() {
        return Minecraft.getInstance().screen instanceof EcranTags;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ours()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (ours()) event.setCanceled(true);
    }
}
