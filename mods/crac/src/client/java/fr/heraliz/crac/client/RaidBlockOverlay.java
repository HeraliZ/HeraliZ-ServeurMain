package fr.heraliz.crac.client;

import fr.heraliz.crac.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Raid block timers sent by the server: the zone the player stands in (a red card at the top of the screen, validated
 * mock-up) and the player's own base (used to grey out building, upgrading and repairing; a neighbour's raid does not
 * block them). The card is a HUD overlay of its own ({@code crac:raid_block}), drawn once per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class RaidBlockOverlay {
    private static long expireAtMillis = 0L;
    private static long ownExpireAtMillis = 0L;

    private RaidBlockOverlay() {
    }

    /** A raid block covers the zone the player stands in (HUD timer). */
    public static boolean isActive() {
        return secondsLeft(expireAtMillis) > 0;
    }

    /** The player's own group is raid blocked: the server refuses their building, upgrades and repairs. */
    public static boolean ownBaseBlocked() {
        return secondsLeft(ownExpireAtMillis) > 0;
    }

    public static void setRemainingSeconds(int remainingSeconds, int ownSeconds) {
        long now = System.currentTimeMillis();
        expireAtMillis = remainingSeconds > 0 ? now + remainingSeconds * 1000L : 0L;
        ownExpireAtMillis = ownSeconds > 0 ? now + ownSeconds * 1000L : 0L;
    }

    @SubscribeEvent
    public static void onClientPlayerClone(Clone event) {
        expireAtMillis = 0L;
        ownExpireAtMillis = 0L;
    }

    @SubscribeEvent
    public static void onClientLogout(LoggingOut event) {
        expireAtMillis = 0L;
        ownExpireAtMillis = 0L;
    }

    /** The card: a lock, "RAID BLOCK", what it blocks, and the time left. */
    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        int left = secondsLeft(expireAtMillis);
        Minecraft mc = Minecraft.getInstance();
        if (left <= 0 || mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        float px = 1.0F / (float) mc.getWindow().getGuiScale();
        String title = Component.translatable("crac.raid.title").getString();
        String line = Component.translatable(ownBaseBlocked() ? "crac.raid.own" : "crac.raid.zone").getString();
        String time = String.format("%d:%02d", left / 60, left % 60);
        float timeW = largeur(font, time, TITRE) * 0.94F;
        float textW = Math.max(largeurCaps(font, title, MINI_GRAS, 0.85F), largeur(font, line, MINI));
        float w = Math.max(133.33F, 6 + 10 + 4.67F + textW + 6.67F + timeW + 6);
        float h = 22.67F;
        float x = (width - w) / 2, y = 10;
        degradeV(g, x, y + h, x + w, y + h + 5, a(0, 70), a(0, 0));
        rect(g, x, y, x + w, y + h, a(0x280606, 184));
        contour(g, x, y, w, h, px, a(0xFF4A3D, 140));
        rect(g, x + px, y + px, x + w - px, y + 2 * px, a(BLANC, 26));
        cadenas(g, x + 6, y + h / 2 - 5, 10, a(ROUGE, 255));
        float tx = x + 6 + 10 + 4.67F;
        caps(g, font, title, MINI_GRAS, tx, y + 8 - 4.21F, 0.85F, a(ROSE, 255));
        texte(g, font, Ui.lit(line, MINI), tx, y + 15.67F - 4.21F, a(BLANC, 179));
        g.pose().pushPose();
        g.pose().translate(x + w - 6 - timeW, y + h / 2 - (3.91F + 3.5F) * 0.94F, 0);
        g.pose().scale(0.94F, 0.94F, 1);
        g.drawString(font, Ui.lit(time, TITRE), 0, 0, a(BLANC, 255), false);
        g.pose().popPose();
    }

    private static int secondsLeft(long expireAt) {
        if (expireAt <= 0L) {
            return 0;
        }
        long millisLeft = expireAt - System.currentTimeMillis();
        return millisLeft <= 0L ? 0 : (int) ((millisLeft + 999L) / 1000L);
    }
}
