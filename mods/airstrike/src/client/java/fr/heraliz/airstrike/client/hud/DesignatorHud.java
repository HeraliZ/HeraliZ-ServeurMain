package fr.heraliz.airstrike.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.CartridgeWheelScreen;
import fr.heraliz.airstrike.client.ClientStrikes;
import fr.heraliz.airstrike.client.DesignatorClient;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.strike.CartridgeTier;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/** Binocular view: lens mask, stadia reticle, compass tape, range finder and payload readouts. */
public final class DesignatorHud {
    private static final ResourceLocation MASK = new ResourceLocation(AirstrikeMod.MODID, "textures/gui/binocular_mask.png");
    private static final int LED = 0xFFFF3A22;
    private static final int LED_DIM = 0xB0FF3A22;
    private static final int GREEN = 0xFF5CFF6A;
    private static final int AMBER = 0xFFFFB02E;
    private static final int RETICLE = 0xE0141414;

    private DesignatorHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || !DesignatorClient.isScoped()) return;
        Font font = mc.font;
        float fade = Mth.clamp((p.getTicksUsingItem() - DesignatorClient.SCOPE_DELAY + pt) / 3.0f, 0f, 1f);
        long gameTime = mc.level.getGameTime();

        // -- lens mask (2:1 texture kept at screen height) + black side bars
        RenderSystem.enableBlend();
        int mh = h, mw = h * 2, mx = (w - mw) / 2;
        g.blit(MASK, mx, 0, mw, mh, 0f, 0f, 1024, 512, 1024, 512);
        if (mx > 0) {
            g.fill(0, 0, mx, h, 0xFF000000);
            g.fill(mx + mw, 0, w, h, 0xFF000000);
        }
        if (fade < 1f) {
            int a = (int) ((1f - fade) * 255) << 24;
            g.fill(0, 0, w, h, a);
        }

        int cx = w / 2, cy = h / 2;
        drawReticle(g, cx, cy, gameTime, pt);
        drawCompass(g, font, cx, Math.max(14, h / 2 - (int) (h * 0.40f)), p.getViewYRot(pt));

        // -- left: range finder readouts
        int lx = cx - Math.min(w / 2 - 12, (int) (h * 0.62f));
        int ly = cy + (int) (h * 0.16f);
        String dist = DesignatorClient.aimDistance < 0 ? "----" : String.format(Locale.ROOT, "%4.0f m", DesignatorClient.aimDistance);
        int distColor = DesignatorClient.aimInRange ? LED : AMBER;
        panel(g, lx - 4, ly - 4, 118, 64);
        g.drawString(font, Component.translatable("hud.airstrike.range"), lx, ly, LED_DIM, false);
        g.drawString(font, dist, lx + 44, ly, distColor, false);
        if (DesignatorClient.aimPoint != null) {
            double dy = DesignatorClient.aimPoint.y - p.getEyeY();
            g.drawString(font, Component.translatable("hud.airstrike.elevation"), lx, ly + 12, LED_DIM, false);
            g.drawString(font, String.format(Locale.ROOT, "%+4.0f m", dy), lx + 44, ly + 12, LED, false);
            g.drawString(font, "GRID", lx, ly + 24, LED_DIM, false);
            g.drawString(font, String.format(Locale.ROOT, "%d %d", (int) Math.floor(DesignatorClient.aimPoint.x),
                    (int) Math.floor(DesignatorClient.aimPoint.z)), lx + 44, ly + 24, LED, false);
        }
        float heading = Mth.wrapDegrees(p.getViewYRot(pt) + 180f);
        if (heading < 0) heading += 360f;
        g.drawString(font, "AZ", lx, ly + 36, LED_DIM, false);
        g.drawString(font, String.format(Locale.ROOT, "%03.0f°", heading), lx + 44, ly + 36, LED, false);
        g.drawString(font, "ZOOM", lx, ly + 48, LED_DIM, false);
        g.drawString(font, String.format(Locale.ROOT, "x%.1f", DesignatorClient.zoom()), lx + 44, ly + 48, LED, false);

        // -- right: cartridge in the bay + spares in the inventory
        int rx = cx + Math.min(w / 2 - 130, (int) (h * 0.62f) - 118);
        StrikeType type = DesignatorClient.loaded();
        boolean creative = p.getAbilities().instabuild;
        panel(g, rx - 4, ly - 4, 126, 64);
        if (type != null) {
            int spare = DesignatorClient.payloadCount(type);
            ItemStack icon = new ItemStack(ModItems.cartridgeFor(type));
            g.pose().pushPose();
            g.pose().translate(rx, ly - 1, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            g.drawString(font, Component.translatable(type.translationKey()), rx + 30, ly, 0xFF000000 | type.uiColor, false);
            CartridgeTier tier = type.tier != null ? type.tier : CartridgeTier.HEAVY;
            Component tierName = Component.translatable(tier.translationKey());
            g.drawString(font, tierName, rx + 30, ly + 12, 0xFF000000 | tier.color, false);
            g.drawString(font, creative ? "+∞" : "+" + spare, rx + 30 + font.width(tierName) + 6, ly + 12, LED_DIM, false);
        } else {
            boolean blinkEmpty = (gameTime / 8) % 2 == 0;
            g.fill(rx, ly - 1, rx + 25, ly + 24, 0x50000000);
            g.drawCenteredString(font, "?", rx + 12, ly + 7, AMBER);
            g.drawString(font, Component.translatable("hud.airstrike.empty"), rx + 30, ly, blinkEmpty ? AMBER : 0xFFFFFFFF, false);
        }
        g.drawString(font, Component.translatable("hud.airstrike.cycle_hint", DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage()),
                rx, ly + 34, 0xA0FFFFFF, false);
        g.drawString(font, Component.translatable("hud.airstrike.zoom_hint"), rx, ly + 46, 0xA0FFFFFF, false);

        // -- bottom: status line
        drawStatus(g, font, p, cx, cy + (int) (h * 0.36f), gameTime, type != null);
        RenderSystem.disableBlend();
    }

    /** Strike just loaded: a glass label at the bottom of the screen for 2.5 s (icon, name, level of the cartridge). */
    public static void renderToast(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        StrikeType t = DesignatorClient.toastType;
        if (t == null || t.tier == null || mc.level == null || mc.options.hideGui || mc.player == null) return;
        float age = mc.level.getGameTime() - DesignatorClient.toastTime + pt;
        if (age < 0 || age > 50) return;
        float a = Math.min(1f, age / 4f) * (age > 42 ? 1f - (age - 42) / 8f : 1f);
        if (a <= 0.01f) return;
        Font font = mc.font;
        int alpha = (int) (a * 255);
        Component name = Component.literal(Component.translatable(t.translationKey()).getString().toUpperCase(Locale.ROOT))
                .withStyle(st -> st.withFont(CartridgeWheelScreen.BOLD));
        Component tier = Component.literal(Component.translatable("hud.airstrike.toast.cartridge",
                        Component.translatable(t.tier.translationKey())).getString().toUpperCase(Locale.ROOT))
                .withStyle(st -> st.withFont(CartridgeWheelScreen.FONT));
        float tierScale = 0.7f;
        int tw = font.width(name), cw = (int) (font.width(tier) * tierScale) + 10;
        int pw = 26 + tw + 8 + cw + 8;
        int x0 = w / 2 - pw / 2;
        int y0 = h - 64 + (int) ((1f - Math.min(1f, age / 4f)) * 6f);
        RenderSystem.enableBlend();
        g.fill(x0 + 1, y0, x0 + pw - 1, y0 + 22, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0, y0 + 1, x0 + 1, y0 + 21, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0 + pw - 1, y0 + 1, x0 + pw, y0 + 21, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0 + 2, y0, x0 + pw - 2, y0 + 1, alpha << 24 | t.tier.color);
        CartridgeWheelScreen.icon(g, t.ordinal(), x0 + 6, y0 + 4, 14f, alpha << 24 | t.uiColor);
        g.drawString(font, name, x0 + 26, y0 + 7, alpha << 24 | 0xECEEF2, false);
        int cx = x0 + 26 + tw + 8;
        g.fill(cx, y0 + 5, cx + cw, y0 + 17, (alpha * 40 / 255) << 24 | t.tier.color);
        g.pose().pushPose();
        g.pose().translate(cx + 5, y0 + 8, 0);
        g.pose().scale(tierScale, tierScale, 1f);
        g.drawString(font, tier, 0, 0, alpha << 24 | t.tier.color, false);
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x70000000);
        g.fill(x, y, x + 3, y + 1, LED_DIM);
        g.fill(x, y, x + 1, y + 3, LED_DIM);
        g.fill(x + w - 3, y + h - 1, x + w, y + h, LED_DIM);
        g.fill(x + w - 1, y + h - 3, x + w, y + h, LED_DIM);
    }

    private static void drawReticle(GuiGraphics g, int cx, int cy, long gameTime, float pt) {
        int len = 150, gap = 10;
        int down = Math.min(len, (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.2f));
        // main stadia lines
        g.fill(cx - len, cy, cx - gap, cy + 1, RETICLE);
        g.fill(cx + gap, cy, cx + len, cy + 1, RETICLE);
        g.fill(cx, cy + gap, cx + 1, cy + down, RETICLE);
        g.fill(cx, cy - 70, cx + 1, cy - gap, RETICLE);
        // thick outer posts
        g.fill(cx - len - 60, cy - 1, cx - len, cy + 2, RETICLE);
        g.fill(cx + len, cy - 1, cx + len + 60, cy + 2, RETICLE);
        g.fill(cx - 1, cy + down, cx + 2, cy + down + 30, RETICLE);
        // mil ticks
        for (int i = 1; i * 12 < len - gap; i++) {
            int d = gap + i * 12;
            int s = i % 2 == 0 ? 4 : 2;
            g.fill(cx - d, cy - s, cx - d + 1, cy + s + 1, RETICLE);
            g.fill(cx + d, cy - s, cx + d + 1, cy + s + 1, RETICLE);
            if (d < down) g.fill(cx - s, cy + d, cx + s + 1, cy + d + 1, RETICLE);
        }
        // range finder box, shrinking after a designation
        float lock = Mth.clamp((gameTime - DesignatorClient.lockTime + pt) / 8.0f, 0f, 1f);
        boolean locking = gameTime - DesignatorClient.lockTime < 60;
        int size = locking ? (int) Mth.lerp(lock, 40, 9) : 9;
        int col = locking ? ((gameTime / 3) % 2 == 0 ? LED : 0xFFFFFFFF) : LED;
        int c = Math.max(3, size / 3);
        corner(g, cx - size, cy - size, c, 1, 1, col);
        corner(g, cx + size, cy - size, c, -1, 1, col);
        corner(g, cx - size, cy + size, c, 1, -1, col);
        corner(g, cx + size, cy + size, c, -1, -1, col);
        g.fill(cx, cy, cx + 1, cy + 1, LED);
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy, int color) {
        int x2 = x + dx * len, y2 = y + dy * len;
        g.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, color);
        g.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, color);
    }

    private static void drawCompass(GuiGraphics g, Font font, int cx, int y, float viewYaw) {
        float heading = viewYaw + 180f;
        float pxPerDeg = 3.2f;
        int half = 150;
        g.fill(cx - half - 6, y - 3, cx + half + 6, y + 19, 0x60000000);
        for (int deg = (int) Math.floor(heading - half / pxPerDeg) - 1; deg <= heading + half / pxPerDeg + 1; deg++) {
            if (Math.floorMod(deg, 5) != 0) continue;
            int x = cx + Math.round((deg - heading) * pxPerDeg);
            if (x < cx - half || x > cx + half) continue;
            int d = Math.floorMod(deg, 360);
            boolean major = d % 15 == 0;
            g.fill(x, y, x + 1, y + (major ? 6 : 3), LED_DIM);
            if (major) {
                String label = switch (d) {
                    case 0 -> "N";
                    case 45 -> "NE";
                    case 90 -> "E";
                    case 135 -> "SE";
                    case 180 -> "S";
                    case 225 -> "SW";
                    case 270 -> "W";
                    case 315 -> "NW";
                    default -> Integer.toString(d);
                };
                g.drawCenteredString(font, label, x, y + 8, d % 45 == 0 ? LED : LED_DIM);
            }
        }
        g.fill(cx, y - 5, cx + 1, y + 8, 0xFFFFFFFF);
    }

    private static void drawStatus(GuiGraphics g, Font font, LocalPlayer p, int cx, int y, long gameTime,
                                   boolean loaded) {
        Component line;
        int color;
        float cd = p.getCooldowns().getCooldownPercent(p.getUseItem().getItem(), 0f);
        ClientStrikes.ClientStrike mine = ClientStrikes.myActive();
        boolean blink = (gameTime / 10) % 2 == 0;
        if (gameTime - DesignatorClient.errorTime < 40) {
            line = Component.translatable(DesignatorClient.errorKey);
            color = AMBER;
        } else if (mine != null) {
            double eta = ClientStrikes.myNextImpact();
            if (eta > 0) {
                line = Component.translatable("hud.airstrike.status.inbound", String.format(Locale.ROOT, "%.0f", Math.ceil(eta / 20.0)));
            } else {
                line = Component.translatable("hud.airstrike.status.impact");
            }
            color = blink ? LED : 0xFFFFFFFF;
            // laser indicator
            g.fill(cx - 22, y - 20, cx - 16, y - 14, blink ? LED : 0x40FF3A22);
            g.drawString(font, "LASER", cx - 12, y - 20, LED, false);
        } else if (cd > 0f) {
            line = Component.translatable("hud.airstrike.status.cooldown");
            color = AMBER;
            int bw = 120;
            g.fill(cx - bw / 2, y + 12, cx + bw / 2, y + 14, 0x60FFFFFF);
            g.fill(cx - bw / 2, y + 12, cx - bw / 2 + (int) (bw * (1f - cd)), y + 14, AMBER);
        } else if (!loaded) {
            line = Component.translatable("hud.airstrike.status.load_hint", DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage());
            color = AMBER;
        } else if (!DesignatorClient.aimInRange) {
            line = Component.translatable("hud.airstrike.status.out_of_range", DesignatorClient.maxRange());
            color = AMBER;
        } else {
            line = Component.translatable("hud.airstrike.status.ready");
            color = blink ? GREEN : 0xFFB8FFBE;
        }
        int tw = font.width(line);
        g.fill(cx - tw / 2 - 6, y - 3, cx + tw / 2 + 6, y + 10, 0x80000000);
        g.drawString(font, line, cx - tw / 2, y, color, false);
    }
}
