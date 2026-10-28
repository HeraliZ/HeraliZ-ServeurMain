package fr.heraliz.crac.client;

import fr.heraliz.crac.block.ObjetAdossable;
import fr.heraliz.crac.block.OrientationObjet;
import fr.heraliz.crac.build.client.BuildClient;
import fr.heraliz.crac.build.client.BuildKeys;
import fr.heraliz.crac.network.C2SRotationObjet;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Touche « tourner » (R, celle du plan) avec un objet collé aux murs en main (coffres, caisse en métal, armoire) : un quart
 * de tour de plus (Maj + R : de moins), envoyé au serveur ; le fantôme de pose montre le sens (HeraliZ, 28/10).
 */
@EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class RotationObjetClient {
    private RotationObjetClient() {
    }

    /** L'objet collé aux murs tenu en main, sinon null (jamais avec le plan en main : R tourne alors la pièce). */
    static ObjetAdossable tenu(Player p) {
        if (p == null || BuildClient.holdingPlan(p)) {
            return null;
        }
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h).getItem() instanceof ObjetAdossable o) {
                return o;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || BuildClient.holdingPlan(mc.player)) {
            return;   // avec le plan en main, R tourne la pièce (PlanController)
        }
        if (tenu(mc.player) == null) {
            while (BuildKeys.ROTATE.consumeClick()) {
                // appuis sans objet en main : oubliés, pour ne pas tourner le prochain objet pris en main
            }
            return;
        }
        boolean change = false;
        while (BuildKeys.ROTATE.consumeClick()) {
            OrientationObjet.client(OrientationObjet.client() + (Screen.hasShiftDown() ? 3 : 1));
            change = true;
        }
        if (change) {
            NetworkHandler.CHANNEL.sendToServer(new C2SRotationObjet(OrientationObjet.client()));
        }
    }

    /** Nouvelle connexion : le serveur repart de 0, le joueur aussi. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        OrientationObjet.client(0);
    }

    /** Sous le viseur : la touche pour tourner l'objet tenu. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) || mc.options.hideGui || mc.screen != null
            || tenu(mc.player) == null || PlacableGhostRenderer.raison() != null) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        Component t = Component.translatable("crac.objet.tourner", BuildKeys.ROTATE.getTranslatedKeyMessage());
        g.drawCenteredString(mc.font, t, e.getWindow().getGuiScaledWidth() / 2, e.getWindow().getGuiScaledHeight() / 2 + 12, 0xB0B0B0);
    }
}
