package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.client.gui.Ui;
import fr.heraliz.armory.suit.Diving;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenue de plongee, cote client (0.9.0, reprise de heraliz_plongee) : carte « Oxygène » au-dessus de la faim (sous l'eau,
 * ou tant que la bouteille n'est pas pleine) et nage automatique : en eau profonde, avancer suffit pour nager (le
 * sprint est mis tout seul).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class DivingClient {
    private DivingClient() {}

    private static int tank = -1;
    private static int max = 1;

    private static final ResourceLocation MINI = HeralizArmory.id("ui_mini"), MINI_GRAS = HeralizArmory.id("ui_mini_bold");
    private static final int BLEU = 0x2FB4D6, AMBRE = 0xE0A030, ROUGE = 0xFF4A3D;
    private static final int LARGEUR = 84, HAUTEUR = 30;

    /**
     * Carte « Oxygène » au-dessus de la faim (maquette validée) : temps qui reste dans la bouteille, barre (bleue, ambre sous
     * 25 %, rouge sous 10 %), et une ligne d'aide : combien de temps à l'air pour la remplir, « Remonte respirer ! » quand
     * elle est presque vide, ou le temps avant qu'elle soit pleine pendant qu'elle se recharge.
     */
    private static final IGuiOverlay TANK = (gui, g, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || tank < 0 || mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !Diving.wears(p)) return;
        boolean sousLEau = p.isEyeInFluid(FluidTags.WATER);
        if (!sousLEau && tank >= max) return;
        int x0 = width / 2 + 8;
        int y0 = height - gui.rightHeight + 9 - HAUTEUR;
        gui.rightHeight += HAUTEUR + 1;
        float f = Math.max(0.0F, Math.min(1.0F, (float) tank / max));
        int couleur = f > 0.25F ? BLEU : f > 0.1F ? AMBRE : ROUGE;
        Font font = gui.getFont();
        Ui.glass(g, x0, y0, LARGEUR, HAUTEUR, 180);
        // barre : piste, lueur, remplissage
        int bx = x0 + 5, bw = LARGEUR - 10, by = y0 + 14, fw = Math.round(bw * f);
        g.fill(bx, by, bx + bw, by + 2, Ui.argb(0xFFFFFF, 30));
        if (fw > 0) {
            g.fill(bx, by - 1, bx + fw, by + 3, Ui.argb(couleur, 50));
            g.fill(bx, by, bx + fw, by + 2, Ui.argb(couleur, 255));
        }
        // textes
        int secondes = (tank + 19) / 20;
        String temps = String.format(Locale.ROOT, "%d:%02d", secondes / 60, secondes % 60);
        Ui.spaced(g, font, Component.translatable("hud.heraliz_armory.oxygene").getString(), MINI_GRAS, x0 + 5, y0 + 5, Ui.argb(0xBFE6F2, 255), 1.0F);
        MutableComponent valeur = Ui.lit(temps, Ui.BOLD);
        g.pose().pushPose();
        g.pose().translate(x0 + LARGEUR - 5 - font.width(valeur) * 0.86F, y0 + 3, 0);
        g.pose().scale(0.86F, 0.86F, 1);
        g.drawString(font, valeur, 0, 0, Ui.argb(couleur == BLEU ? 0xFFFFFF : couleur, 255), false);
        g.pose().popPose();
        String aide;
        if (sousLEau && f <= 0.1F) {
            aide = Component.translatable("hud.heraliz_armory.remonte").getString();
        } else {
            // recharge : la bouteille entière en refill_seconds, au prorata de ce qui manque
            int refill = Math.max(0, ArmorConfig.REFILL_SECONDS.get());
            int restant = Math.max(1, (int) Math.ceil(refill * (1.0F - f)));
            aide = Component.translatable(sousLEau ? "hud.heraliz_armory.pleine_air" : "hud.heraliz_armory.recharge", restant).getString();
        }
        g.drawString(font, Ui.lit(aide, MINI), x0 + 5, y0 + 19, Ui.argb(sousLEau && f <= 0.1F ? 0xFFB3AC : 0xFFFFFF, sousLEau && f <= 0.1F ? 255 : 153), false);
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
