package fr.heraliz.crac.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Raid block timers sent by the server: the zone the player stands in (drawn on the HUD) and the player's own base
 * (used to grey out building, upgrading and repairing; a neighbour's raid does not block them).
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

    @SubscribeEvent
    public static void onRenderGui(Post event) {
        int remainingSeconds = secondsLeft(expireAtMillis);
        if (remainingSeconds > 0) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && !minecraft.options.hideGui) {
                net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.translatable("crac.raid.hud", String.format(Locale.ROOT, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
                int x = minecraft.getWindow().getGuiScaledWidth() / 2;
                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(0.0F, 0.0F, 500.0F);
                event.getGuiGraphics().drawCenteredString(minecraft.font, text, x, 10, -57312);
                event.getGuiGraphics().pose().popPose();
            }
        }
    }

    private static int secondsLeft(long expireAt) {
        if (expireAt <= 0L) {
            return 0;
        }
        long millisLeft = expireAt - System.currentTimeMillis();
        return millisLeft <= 0L ? 0 : (int) ((millisLeft + 999L) / 1000L);
    }
}
