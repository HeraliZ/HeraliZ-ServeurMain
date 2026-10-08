package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.suit.Diving;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenue de plongee, cote client (0.9.0, reprise de heraliz_plongee) : jauge de la bouteille au-dessus de la barre d'air
 * (sous l'eau, ou tant qu'elle n'est pas pleine) et nage automatique : en eau profonde, avancer suffit pour nager (le
 * sprint est mis tout seul).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class DivingClient {
    private DivingClient() {}

    private static int tank = -1;
    private static int max = 1;

    private static final IGuiOverlay TANK = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || tank < 0 || mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !Diving.wears(p)) return;
        if (!p.isEyeInFluid(FluidTags.WATER) && tank >= max) return;
        int x0 = width / 2 + 10;
        int y0 = height - gui.rightHeight - 1;
        gui.rightHeight += 11;
        float f = Math.max(0.0F, Math.min(1.0F, (float) tank / max));
        g.fill(x0, y0, x0 + 81, y0 + 10, 0xB80C1418);
        int w = Math.round(79.0F * f);
        int col = f > 0.25F ? 0xFF2FB4D6 : f > 0.1F ? 0xFFE0A030 : 0xFFE0402E;   // bleu, ambre, rouge
        g.fill(x0 + 1, y0 + 1, x0 + 1 + w, y0 + 9, col);
        g.fill(x0 + 1, y0 + 1, x0 + 1 + w, y0 + 3, 0x50FFFFFF);
        g.drawString(gui.getFont(), Component.translatable("hud.heraliz_armory.tank", (tank + 19) / 20), x0 + 3, y0 + 1, 0xFFFFFFFF, true);
    };

    /** Reserve recue du serveur (ticks) et capacite de la bouteille. */
    public static void sync(int t, int m) {
        tank = t;
        max = Math.max(1, m);
    }

    /** En eau profonde, avancer suffit pour nager. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.input == null || !Diving.wears(p)) return;
        if (!p.isSwimming() && p.input.hasForwardImpulse() && Diving.deepWater(p) && !p.isPassenger()) {
            p.setSprinting(true);
            p.setSwimming(true);
        }
    }

    @Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(), "bouteille", TANK);
        }
    }
}
