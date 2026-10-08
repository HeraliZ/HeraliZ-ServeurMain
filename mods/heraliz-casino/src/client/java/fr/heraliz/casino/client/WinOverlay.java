package fr.heraliz.casino.client;

import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;
import java.util.Random;

/**
 * Bandeau anime affiche au joueur quand il gagne (montant qui "compte", pieces qui tombent).
 */
public final class WinOverlay implements IGuiOverlay {
    public static final WinOverlay INSTANCE = new WinOverlay();
    private static final long DURATION = 3600;

    private static long shownAt = -1;
    private static long amount;
    private static float multiplier;
    private static int machine;
    private static final float[][] COINS = new float[26][4];

    private WinOverlay() {
    }

    public static void show(long amount, float multiplier, int machine) {
        if (!ClientConfig.WIN_OVERLAY.get()) return;
        WinOverlay.amount = amount;
        WinOverlay.multiplier = multiplier;
        WinOverlay.machine = machine;
        shownAt = Util.getMillis();
        Random r = new Random();
        for (float[] c : COINS) {
            c[0] = r.nextFloat();
            c[1] = -r.nextFloat() * 0.6f;
            c[2] = 0.35f + r.nextFloat() * 0.5f;
            c[3] = r.nextFloat() * 6.28f;
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (shownAt < 0) return;
        long ms = Util.getMillis() - shownAt;
        if (ms > DURATION) {
            shownAt = -1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        float t = ms / 1000f;
        float in = Math.min(1f, t / 0.25f);
        float out = Math.min(1f, (DURATION - ms) / 400f);
        float alpha = Math.max(0.02f, in * out);
        boolean big = multiplier >= 10f;
        // pieces d'or qui tombent en tournoyant (les vraies pieces du casino ; un lingot de temps en temps pour les gros gains)
        if (ms < DURATION - 300) {
            for (int i = 0; i < COINS.length; i++) {
                float[] c = COINS[i];
                float y = (c[1] + t * c[2]) * h;
                if (y < -12 || y > h + 12) continue;
                float x = c[0] * w;
                float spin = Math.max(0.08f, (float) Math.abs(Math.cos(t * 6 + c[3])));
                boolean bar = big && i % 5 == 0;
                g.pose().pushPose();
                // derriere le bandeau (un objet est dessine 150 unites en avant)
                g.pose().translate(x, y, -160);
                g.pose().scale(0.8f * (bar ? 1 : spin), 0.8f, 1);
                g.renderItem(new net.minecraft.world.item.ItemStack(fr.heraliz.casino.registry.ModRegistry.chip(bar ? 4 : 3)), -8, -8);
                g.pose().popPose();
            }
        }
        // bandeau
        int cy = h / 4;
        int bw = Math.min(w - 20, 260), bh = 46;
        int x0 = w / 2 - bw / 2;
        int a = (int) (alpha * 200) << 24;
        g.fillGradient(x0, cy - bh / 2, x0 + bw, cy + bh / 2, a | 0x2A0A12, a | 0x12060A);
        int ga = (int) (alpha * 255) << 24;
        g.fill(x0, cy - bh / 2, x0 + bw, cy - bh / 2 + 2, ga | 0xFFD36A);
        g.fill(x0, cy + bh / 2 - 2, x0 + bw, cy + bh / 2, ga | 0xFFD36A);
        // montant qui "compte"
        float count = Math.min(1f, t / 0.9f);
        long shown = (long) (amount * (1 - Math.pow(1 - count, 3)));
        String key = multiplier >= 100 ? "overlay.heralizcasino.jackpot" : big ? "overlay.heralizcasino.big" : "overlay.heralizcasino.win";
        Component label = machine >= 0 && machine < MachineType.values().length
                ? Component.translatable(key).append(Component.literal(" • ")).append(MachineType.values()[machine].displayName())
                : Component.translatable(key);
        float pulse = 1f + 0.06f * (float) Math.sin(t * 9);
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy - 12, 0);
        g.pose().scale(pulse, pulse, 1);
        g.drawCenteredString(font, label, 0, -4, ga | (big ? 0xFFD36A : 0xFFF2C0));
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy + 4, 0);
        g.pose().scale(2f, 2f, 1);
        g.drawCenteredString(font, Component.literal("+" + Chips.format(shown)), 0, -4, ga | 0xFFE680);
        g.pose().popPose();
        String m = multiplier >= 100 ? String.format(Locale.ROOT, "x%.0f", multiplier) : fr.heraliz.casino.block.MachineBlockEntity.mult(multiplier);
        g.drawCenteredString(font, Component.literal(m), w / 2, cy + 16, ga | 0xFFB020);
    }
}
