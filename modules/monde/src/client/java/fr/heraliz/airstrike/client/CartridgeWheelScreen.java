package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.strike.CartridgeTier;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Wheel of the strikes, opened while the reload key is held: the mouse direction picks a strike, releasing the key (or
 * a left click) loads it, the centre (or a right click) cancels. Light on the left, Heavy at the top, Special on the
 * right; the centre shows the pointed strike, its level and its power. Style of the clan screens (glass, thin
 * outlines, spaced capitals, DM Sans), drawn on a 640 x 360 design canvas.
 */
public class CartridgeWheelScreen extends Screen {
    public static final ResourceLocation FONT = new ResourceLocation(AirstrikeMod.MODID, "ui");
    public static final ResourceLocation BOLD = new ResourceLocation(AirstrikeMod.MODID, "ui_bold");
    public static final ResourceLocation ICONS = new ResourceLocation(AirstrikeMod.MODID, "textures/gui/strike_icons.png");
    private static final StrikeType[] WHEEL = StrikeType.WHEEL;
    private static final float STEP = 360f / WHEEL.length;
    private static final float R_IN = 58f, R_OUT = 118f, GAP = 0.9f;
    private static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, OK = 0x5FD08A, DANGER = 0xFF6B6B;
    /** Damage, area, duration (0..1) shown by the bars of the centre, per strike. */
    private static final float[][] STATS = new float[StrikeType.values().length][];

    static {
        STATS[StrikeType.SMOKE.ordinal()] = new float[]{0f, 0.60f, 0.60f};
        STATS[StrikeType.GUN_RUN.ordinal()] = new float[]{0.35f, 0.45f, 0.10f};
        STATS[StrikeType.BOMBING.ordinal()] = new float[]{0.65f, 0.60f, 0.10f};
        STATS[StrikeType.CLUSTER.ordinal()] = new float[]{0.50f, 0.75f, 0.15f};
        STATS[StrikeType.GAS.ordinal()] = new float[]{0.40f, 0.60f, 0.55f};
        STATS[StrikeType.PRECISION.ordinal()] = new float[]{1.00f, 0.50f, 0.05f};
        STATS[StrikeType.NAPALM.ordinal()] = new float[]{0.80f, 0.65f, 0.45f};
        STATS[StrikeType.SUPPLY.ordinal()] = new float[]{0f, 0f, 0f};
    }

    private int hovered = -1;
    private final float[] pop = new float[WHEEL.length];
    private long lastFrame;
    private int age;
    private boolean done;

    public CartridgeWheelScreen() {
        super(Component.translatable("hud.airstrike.wheel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        // the wheel lives as long as the key is held
        if (age > 1 && !keyHeld()) confirm();
    }

    private boolean keyHeld() {
        InputConstants.Key key = DesignatorClient.CYCLE_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1 to 7: direct choice, clockwise from the bottom left
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + WHEEL.length) {
            hovered = keyCode - GLFW.GLFW_KEY_1;
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (DesignatorClient.CYCLE_KEY.matches(keyCode, scanCode)) {
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) hovered = -1;
        confirm();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        int start = hovered < 0 ? (delta < 0 ? -1 : 0) : hovered;
        hovered = Math.floorMod(start + (delta < 0 ? 1 : -1), WHEEL.length);
        // the pointer follows: the wheel stays on the scrolled strike until the mouse moves again
        scrolled = true;
        uiSound(CLIC, 0.35f);
        return true;
    }

    private boolean scrolled;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    /** Loads the pointed strike (if it can be) and closes the wheel. */
    public void confirm() {
        if (done) return;
        done = true;
        int pick = hovered;
        onClose();
        if (pick < 0) {
            uiSound(FERMER, 0.35f);
            return;
        }
        StrikeType t = WHEEL[pick];
        if (t == DesignatorClient.loaded()) return;
        if (DesignatorClient.isAvailable(t)) {
            DesignatorClient.load(t);
        } else {
            DesignatorClient.error("hud.airstrike.status.no_payload");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Drawing

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.flush();
        // design canvas 640 x 360: same size on screen whatever the GUI scale, whole pixels for the text
        double guiScale = mc.getWindow().getGuiScale();
        int real = 1;
        for (int s = 8; s >= 2; s--) {
            if (mc.getWindow().getWidth() / s >= 600 && mc.getWindow().getHeight() / s >= 340) {
                real = s;
                break;
            }
        }
        float k = (float) (real / guiScale);
        float cx = width / 2f / k, cy = height / 2f / k - 2f;
        float open = Mth.clamp((age + pt) / 3f, 0f, 1f);
        float e = 1f - (1f - open) * (1f - open) * (1f - open);

        // pointer: mouse direction from the centre (a scroll keeps its choice until the mouse moves)
        if (lastMouseX != mouseX || lastMouseY != mouseY) {
            if (!Double.isNaN(lastMouseX)) scrolled = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        if (!scrolled) {
            double dx = mouseX / k - cx, dy = mouseY / k - cy;
            int before = hovered;
            if (Math.sqrt(dx * dx + dy * dy) > (R_IN - 5) * 0.75) {
                double ang = Math.toDegrees(Math.atan2(dx, -dy));      // clockwise from the top
                hovered = Math.floorMod((int) Math.round(ang / STEP) + 3, WHEEL.length);
            } else {
                hovered = -1;
            }
            if (hovered != before && hovered >= 0 && age > 1) uiSound(CLIC, 0.25f);
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;
        for (int i = 0; i < pop.length; i++) {
            float target = i == hovered ? 1f : 0f;
            pop[i] += (target - pop[i]) * Math.min(1f, dt * 18f);
        }

        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float w = width / k, h = height / k;
        // light veil: the world stays visible
        veil(mat, cx, cy, w, h, e);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        float sc = 0.82f + 0.18f * e;
        g.pose().scale(sc, sc, 1f);
        g.pose().translate(-cx, -cy, 0f);
        mat = g.pose().last().pose();
        StrikeType loaded = DesignatorClient.loaded();
        int alpha = (int) (255 * e);

        // sectors
        for (int i = 0; i < WHEEL.length; i++) {
            StrikeType t = WHEEL[i];
            CartridgeTier tier = t.tier;
            boolean avail = DesignatorClient.isAvailable(t);
            float hot = pop[i];
            float a0 = angle(i) - STEP / 2f + GAP, a1 = angle(i) + STEP / 2f - GAP;
            float r0 = R_IN + 1.8f * hot, r1 = R_OUT + 6f * hot;
            int glass = avail ? 0xCC : 0xA8;
            ring(mat, cx, cy, r0, r1, a0, a1, argb(0x0D0F14, (int) (glass * e)), argb(0x0D0F14, (int) ((glass + 12 * hot) * e)));
            if (hot > 0.01f && avail) {
                ring(mat, cx, cy, r0, r1, a0, a1, argb(tier.color, (int) (16 * hot * e)), argb(tier.color, (int) (88 * hot * e)));
            }
            int edge = hot > 0.5f ? argb(avail ? tier.color : DANGER, (int) (230 * e)) : argb(0xFFFFFF, (int) (30 * e));
            float lw = 0.5f + 0.7f * hot;
            ring(mat, cx, cy, r1 - lw, r1, a0, a1, edge, edge);
            ring(mat, cx, cy, r0, r0 + lw, a0, a1, edge, edge);
            radial(mat, cx, cy, a0, r0, r1, lw, edge);
            radial(mat, cx, cy, a1, r0, r1, lw, edge);
            // lit outer rim (top highlight of the glass panels)
            int rim = hot > 0.5f && avail ? argb(tier.color, (int) (240 * e)) : argb(0xFFFFFF, (int) (24 * e));
            ring(mat, cx, cy, r1 - 1.6f - lw, r1 - lw, a0 + 1f, a1 - 1f, rim, rim);
            if (t == loaded) {
                // "loaded" dot at the inner edge
                float lx = cx + cos(angle(i)) * (r0 + 6f), ly = cy + sin(angle(i)) * (r0 + 6f);
                ring(mat, lx, ly, 0f, 2.2f, 0f, 360f, argb(OK, alpha), argb(OK, alpha));
                ring(mat, lx, ly, 3.3f, 3.9f, 0f, 360f, argb(OK, (int) (90 * e)), argb(OK, (int) (90 * e)));
            }
            if (hot > 0.05f && avail) {
                // soft glow behind the pointed icon
                float rm = (r0 + r1) / 2f + 3f;
                float gx = cx + cos(angle(i)) * rm, gy = cy + sin(angle(i)) * rm - 6f;
                ring(mat, gx, gy, 0f, 16f, 0f, 360f, argb(t.uiColor, (int) (70 * hot * e)), argb(t.uiColor, 0));
            }
        }
        // level arcs around the wheel, chevrons beside the level names
        for (CartridgeTier tier : CartridgeTier.values()) {
            float[] span = span(tier);
            boolean has = DesignatorClient.tierCount(tier) > 0 || creative();
            int c = argb(tier.color, (int) ((has ? 242 : 56) * e));
            ring(mat, cx, cy, R_OUT + 10f, R_OUT + 12.2f, span[0] + GAP * 2, span[1] - GAP * 2, c, c);
            float[] lab = labelPlace(tier, cx, cy);
            String name = Component.translatable(tier.translationKey()).getString().toUpperCase(Locale.ROOT);
            float wName = spacedWidth(font, name, BOLD, 0.75f, 1.4f);
            int align = (int) lab[2];
            float xl = align == 0 ? lab[0] + wName / 2f + 8f : align == 1 ? lab[0] - wName - 8f : lab[0] + wName + 8f;
            int col = has ? argb(mix(tier.color, 0xFFFFFF, 0.25f), alpha) : argb(MUTED, (int) (180 * e));
            chevrons(mat, xl, lab[1] + 1f, 6f, tier.chevrons, col);
        }
        // centre
        ring(mat, cx, cy, 0f, R_IN - 5f, 0f, 360f, argb(0x0D0F14, (int) (214 * e)), argb(0x0D0F14, (int) (214 * e)));
        ring(mat, cx, cy, R_IN - 5.6f, R_IN - 5f, 0f, 360f, argb(0xFFFFFF, (int) (26 * e)), argb(0xFFFFFF, (int) (26 * e)));
        StrikeType pointed = hovered >= 0 ? WHEEL[hovered] : null;
        boolean pointedAvail = pointed != null && DesignatorClient.isAvailable(pointed);
        if (pointed != null && pointedAvail) {
            // bars: damage, area, duration
            float[] st = STATS[pointed.ordinal()];
            for (int i = 0; i < 3; i++) {
                float y = cy - 7f + i * 9f + 2f;
                quad(mat, cx - 4f, y, cx + 34f, y + 2.4f, argb(0xFFFFFF, (int) (23 * e)));
                quad(mat, cx - 4f, y, cx - 4f + Math.max(0.6f, 38f * st[i]), y + 2.4f, argb(pointed.tier.color, (int) (235 * e)));
            }
        }
        RenderSystem.enableCull();

        // icons
        for (int i = 0; i < WHEEL.length; i++) {
            StrikeType t = WHEEL[i];
            boolean avail = DesignatorClient.isAvailable(t);
            float hot = pop[i];
            float r0 = R_IN + 1.8f * hot, r1 = R_OUT + 6f * hot;
            float rm = (r0 + r1) / 2f + 3f;
            float px = cx + cos(angle(i)) * rm, py = cy + sin(angle(i)) * rm;
            float size = 17f + 3f * hot;
            int col = !avail ? argb(MUTED, (int) (90 * e)) : hot > 0.5f ? argb(t.uiColor, alpha) : argb(TEXT, (int) (224 * e));
            icon(g, t.ordinal(), px - size / 2f, py - 6f - size / 2f, size, col);
            if (!avail) icon(g, LOCK, px + 7f, py - 17.5f, 7f, argb(MUTED, (int) (160 * e)));
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // texts of the sectors
        for (int i = 0; i < WHEEL.length; i++) {
            StrikeType t = WHEEL[i];
            boolean avail = DesignatorClient.isAvailable(t);
            float hot = pop[i];
            float r0 = R_IN + 1.8f * hot, r1 = R_OUT + 6f * hot;
            float rm = (r0 + r1) / 2f + 3f;
            float px = cx + cos(angle(i)) * rm, py = cy + sin(angle(i)) * rm;
            String name = Component.translatable(t.shortKey()).getString().toUpperCase(Locale.ROOT);
            float ts = name.length() > 12 ? 0.52f : 0.6f;
            int col = !avail ? argb(MUTED, (int) (115 * e)) : hot > 0.5f ? argb(TEXT, alpha) : argb(0xC8CCD4, (int) (218 * e));
            spaced(g, font, name, hot > 0.5f ? BOLD : FONT, px, py + 7f, ts, col, 0.5f, 0);
        }
        // level labels
        for (CartridgeTier tier : CartridgeTier.values()) {
            float[] lab = labelPlace(tier, cx, cy);
            int count = DesignatorClient.tierCount(tier);
            boolean has = count > 0 || creative();
            int align = (int) lab[2];
            float xTxt = lab[0], yTxt = lab[1];
            int col = has ? argb(mix(tier.color, 0xFFFFFF, 0.25f), alpha) : argb(MUTED, (int) (180 * e));
            String name = Component.translatable(tier.translationKey()).getString().toUpperCase(Locale.ROOT);
            spaced(g, font, name, BOLD, xTxt, yTxt, 0.75f, col, 1.4f, align);
            String qty = creative() ? "∞" : has ? "×" + count : Component.translatable("hud.airstrike.wheel.none_short").getString().toUpperCase(Locale.ROOT);
            spaced(g, font, qty, FONT, xTxt, yTxt + 11f, 0.62f, argb(MUTED, (int) ((has ? 255 : 150) * e)), 0.8f, align);
        }
        // centre texts
        if (pointed != null) {
            CartridgeTier tier = pointed.tier;
            String tierName = Component.translatable(tier.translationKey()).getString().toUpperCase(Locale.ROOT);
            chip(g, font, tierName, cx, cy - 40f, pointedAvail ? tier.color : MUTED, alpha);
            String full = Component.translatable(pointed.translationKey()).getString().toUpperCase(Locale.ROOT);
            // largeur mesuree contre la corde du disque central (marge de 6 de chaque cote) ; trop long : deux lignes
            float corde = 2f * (float) Math.sqrt((R_IN - 5f) * (R_IN - 5f) - 28f * 28f) - 12f;
            float w1 = spacedWidth(font, full, BOLD, 1f, 0.9f);
            int coupe = -1;
            for (int sp = full.indexOf(' '); sp >= 0; sp = full.indexOf(' ', sp + 1)) {
                if (coupe < 0 || Math.abs(2 * sp - full.length()) < Math.abs(2 * coupe - full.length())) coupe = sp;
            }
            if (w1 * 0.72f > corde && coupe > 0) {
                String l1 = full.substring(0, coupe), l2 = full.substring(coupe + 1);
                float s = Math.min(0.78f, corde / Math.max(spacedWidth(font, l1, BOLD, 1f, 0.9f), spacedWidth(font, l2, BOLD, 1f, 0.9f)));
                spaced(g, font, l1, BOLD, cx, cy - 28f, s, argb(TEXT, alpha), 0.9f, 0);
                spaced(g, font, l2, BOLD, cx, cy - 28f + 9.5f * s, s, argb(TEXT, alpha), 0.9f, 0);
            } else {
                spaced(g, font, full, BOLD, cx, cy - 26f, Math.min(1f, corde / w1), argb(TEXT, alpha), 0.9f, 0);
            }
            if (pointedAvail) {
                String[] labels = {"hud.airstrike.wheel.damage", "hud.airstrike.wheel.area", "hud.airstrike.wheel.duration"};
                for (int i = 0; i < 3; i++) {
                    spaced(g, font, Component.translatable(labels[i]).getString().toUpperCase(Locale.ROOT), FONT, cx - 34f,
                            cy - 7f + i * 9f, 0.55f, argb(MUTED, alpha), 0.6f, -1);
                }
                if (pointed == loaded) {
                    spaced(g, font, Component.translatable("hud.airstrike.wheel.already").getString().toUpperCase(Locale.ROOT), BOLD,
                            cx, cy + 24f, 0.6f, argb(OK, alpha), 1.2f, 0);
                } else {
                    String rel = Component.translatable("hud.airstrike.wheel.release_key",
                            DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage()).getString().toUpperCase(Locale.ROOT);
                    spaced(g, font, rel, BOLD, cx, cy + 24f, 0.6f, argb(TEXT, (int) (195 * e)), 1.2f, 0);
                }
            } else {
                spaced(g, font, Component.translatable("hud.airstrike.wheel.no_cartridge").getString().toUpperCase(Locale.ROOT), BOLD,
                        cx, cy - 4f, 0.68f, argb(DANGER, alpha), 0.8f, 0);
                spaced(g, font, tierName, BOLD, cx, cy + 6f, 0.68f, argb(DANGER, alpha), 0.8f, 0);
                spaced(g, font, Component.translatable("hud.airstrike.wheel.find_" + tier.id).getString(), FONT, cx, cy + 20f, 0.55f,
                        argb(MUTED, alpha), 0.2f, 0);
            }
        } else {
            spaced(g, font, Component.translatable("hud.airstrike.wheel.cancel").getString().toUpperCase(Locale.ROOT), BOLD,
                    cx, cy - 4f, 0.72f, argb(MUTED, alpha), 1.4f, 0);
        }
        g.pose().popPose();

        // description under the wheel, key help at the bottom
        if (pointed != null && open > 0.6f) {
            int a = (int) (255 * (open - 0.6f) / 0.4f);
            String desc = Component.translatable(pointed.descriptionKey()).getString();
            float dw = Math.min(300f, spacedWidth(font, desc, FONT, 0.64f, 0f) + 24f);
            float yd = cy + R_OUT + 22f;
            glassBox(g, cx - dw / 2f, yd, dw, 15f, a);
            spaced(g, font, desc, FONT, cx, yd + 4f, 0.64f, argb(0xECEEF2, (int) (a * 0.9f)), 0f, 0);
        }
        String help = Component.translatable("hud.airstrike.wheel.help", DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage())
                .getString().toUpperCase(Locale.ROOT);
        spaced(g, font, help, FONT, cx, h - 14f, 0.55f, argb(MUTED, (int) (215 * e)), 0.9f, 0);
        g.pose().popPose();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------------------------

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild;
    }

    /** Angle of the middle of a sector, degrees clockwise from the top. */
    private static float angle(int i) {
        return (i - 3) * STEP;
    }

    /** Where the name of a level goes: x, y (top of the text), alignment (0 centred, 1 right edge, -1 left edge). */
    private static float[] labelPlace(CartridgeTier tier, float cx, float cy) {
        float[] span = span(tier);
        float am = (span[0] + span[1]) / 2f;
        float lx = cx + cos(am) * (R_OUT + 24f), ly = cy + sin(am) * (R_OUT + 24f);
        return switch (tier) {
            case HEAVY -> new float[]{lx, ly - 10f, 0};
            case LIGHT -> new float[]{lx + 6f, ly - 8f, 1};
            case SPECIAL -> new float[]{lx - 6f, ly - 8f, -1};
        };
    }

    /** n chevrons pointing up (rank marks), left edge x, top y. */
    private static void chevrons(Matrix4f mat, float x, float y, float size, int n, int argb) {
        float t = size * 0.26f;
        for (int k = 0; k < n; k++) {
            float yk = y + k * size * 0.42f;
            line(mat, x, yk + size * 0.38f, x + size / 2f, yk, t, argb);
            line(mat, x + size / 2f, yk, x + size, yk + size * 0.38f, t, argb);
        }
    }

    private static void line(Matrix4f mat, float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w / 2f, ny = dx / l * w / 2f;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(buf, mat, x0 - nx, y0 - ny, argb);
        vertex(buf, mat, x0 + nx, y0 + ny, argb);
        vertex(buf, mat, x1 + nx, y1 + ny, argb);
        vertex(buf, mat, x1 - nx, y1 - ny, argb);
        BufferUploader.drawWithShader(buf.end());
    }

    /** First and last angle covered by the sectors of a level. */
    private static float[] span(CartridgeTier tier) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < WHEEL.length; i++) {
            if (WHEEL[i].tier != tier) continue;
            min = Math.min(min, angle(i) - STEP / 2f);
            max = Math.max(max, angle(i) + STEP / 2f);
        }
        return new float[]{min, max};
    }

    private static float cos(float deg) {
        return (float) Math.cos(Math.toRadians(deg - 90f));
    }

    private static float sin(float deg) {
        return (float) Math.sin(Math.toRadians(deg - 90f));
    }

    private static int argb(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    /**
     * Spaced capitals (clan screens) at a scale: align 0 = centred on x, 1 = right edge at x, -1 = left edge at x.
     * Returns the drawn width (design units).
     */
    static float spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float scale, int argb,
                        float spacing, int align) {
        float w = spacedWidth(font, s, f, scale, spacing);
        float x0 = align == 0 ? x - w / 2f : align == 1 ? x - w : x;
        // texte à la vraie taille d'une police du même dessin (taille x scale), jamais agrandi ou réduit par la pose ;
        // milieu des capitales à la même place, chaque lettre posée sur un point de l'écran
        ResourceLocation p = police(f, scale);
        float yp = y + scale * milieu(taille(f)) - milieu(taille(p)), e = spacing * scale;
        float cx = x0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(s.codePointAt(i))), p);
            g.pose().pushPose();
            placer(g, cx, yp);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + e;
        }
        return w;
    }

    static float spacedWidth(Font font, String s, ResourceLocation f, float scale, float spacing) {
        ResourceLocation p = police(f, scale);
        float w = 0, e = spacing * scale;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            w += font.width(lit(new String(Character.toChars(s.codePointAt(i))), p)) + e;
        }
        return Math.max(0, w - e);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Texte net (règle du propriétaire, 23/10) : police TTF du mod tracée à sa vraie taille, posée sur un point entier
    // de l'écran. Polices DM Sans de 6 à 10 points (assets/airstrike/font/ui_N.json, ui_bold_N.json), 11 : FONT, BOLD.

    private static final ResourceLocation[] POLICES = polices("ui_", FONT), POLICES_GRAS = polices("ui_bold_", BOLD);

    private static ResourceLocation[] polices(String prefixe, ResourceLocation onze) {
        ResourceLocation[] p = new ResourceLocation[12];
        for (int t = 6; t <= 10; t++) p[t] = new ResourceLocation(AirstrikeMod.MODID, prefixe + t);
        p[11] = onze;
        return p;
    }

    /** Taille (unités d'interface) d'une police du mod : ui, ui_bold 11 ; ui_small 9 ; ui_mini, ui_mini_bold 6,5 ; ui_N, ui_bold_N : N. */
    public static float taille(ResourceLocation f) {
        String p = f.getPath();
        if (p.startsWith("ui_mini")) return 6.5f;
        if (p.equals("ui_small")) return 9f;
        int n = 0, rang = 1;
        for (int i = p.length() - 1; i >= 0 && Character.isDigit(p.charAt(i)); i--, rang *= 10) n += (p.charAt(i) - '0') * rang;
        return rang > 1 ? n : 11f;
    }

    /** Police du même dessin (gras ou non) que f à la taille (taille de f) x scale, arrondie, de 6 à 11 points. */
    public static ResourceLocation police(ResourceLocation f, float scale) {
        if (scale == 1f) return f;
        int t = Mth.clamp(Math.round(taille(f) * scale), 6, 11);
        return (f.getPath().contains("bold") ? POLICES_GRAS : POLICES)[t];
    }

    /** Milieu des capitales sous le point de dessin (DM Sans, décalage 1 : taille x 0,49309 - 1). */
    public static float milieu(float taille) {
        return taille * 0.49309f - 1f;
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du journal des quêtes). */
    public static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    /** Label on a tinted background (chip of the clan screens), centred on x. */
    private static void chip(GuiGraphics g, Font font, String text, float x, float y, int rgb, int alpha) {
        // largeur du texte dans sa police à la vraie taille (FONT x 0,6), plus les marges
        float w = spacedWidth(font, text, FONT, 0.6f, 0.6f) + 10f;
        float x0 = x - w / 2f;
        g.fill((int) x0, (int) y, (int) (x0 + w), (int) y + 11, argb(rgb, alpha * 34 / 255));
        int line = argb(rgb, alpha * 90 / 255);
        g.fill((int) x0, (int) y, (int) (x0 + w), (int) y + 1, line);
        g.fill((int) x0, (int) y + 10, (int) (x0 + w), (int) y + 11, line);
        g.fill((int) x0, (int) y, (int) x0 + 1, (int) y + 11, line);
        g.fill((int) (x0 + w) - 1, (int) y, (int) (x0 + w), (int) y + 11, line);
        spaced(g, font, text, FONT, x, y + 2.2f, 0.6f, argb(mix(rgb, 0xFFFFFF, 0.3f), alpha), 0.6f, 0);
    }

    private static void glassBox(GuiGraphics g, float x, float y, float w, float h, int alpha) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        g.fill(x0 + 1, y0, x1 - 1, y1, argb(0x0D0F14, alpha * 180 / 255));
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb(0x0D0F14, alpha * 180 / 255));
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb(0x0D0F14, alpha * 180 / 255));
        g.fill(x0 + 2, y0, x1 - 2, y0 + 1, argb(0xFFFFFF, alpha * 40 / 255));
    }

    /** Cell of the lock in the icon strip (cells 0 to 7: the strikes, by ordinal). */
    static final int LOCK = 8;

    /** Icon of the strip (white on transparent, tinted). */
    public static void icon(GuiGraphics g, int cell, float x, float y, float size, int argb) {
        if (size <= 0) return;
        RenderSystem.enableBlend();
        // icons scaled down smoothly (the strip is 64 px per icon)
        Minecraft.getInstance().getTextureManager().getTexture(ICONS).setFilter(true, false);
        RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
                (argb >>> 24) / 255f);
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(size / 64f, size / 64f, 1f);
        g.blit(ICONS, 0, 0, cell * 64, 0, 64, 64, 1024, 64);
        g.pose().popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Shapes (position + colour, drawn right away)

    /** Ring sector from r0 (colour c0) to r1 (colour c1), angles in degrees clockwise from the top. */
    private static void ring(Matrix4f mat, float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1) {
        if (((c0 | c1) >>> 24) == 0) return;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(4, (int) ((a1 - a0) / 3f));
        for (int i = 0; i <= steps; i++) {
            float ang = a0 + (a1 - a0) * i / steps;
            float c = cos(ang), s = sin(ang);
            vertex(buf, mat, cx + c * r1, cy + s * r1, c1);
            vertex(buf, mat, cx + c * r0, cy + s * r0, c0);
        }
        BufferUploader.drawWithShader(buf.end());
    }

    /** Radial line at an angle, from r0 to r1. */
    private static void radial(Matrix4f mat, float cx, float cy, float ang, float r0, float r1, float w, int argb) {
        float c = cos(ang), s = sin(ang), nx = -s * w / 2f, ny = c * w / 2f;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(buf, mat, cx + c * r0 - nx, cy + s * r0 - ny, argb);
        vertex(buf, mat, cx + c * r0 + nx, cy + s * r0 + ny, argb);
        vertex(buf, mat, cx + c * r1 + nx, cy + s * r1 + ny, argb);
        vertex(buf, mat, cx + c * r1 - nx, cy + s * r1 - ny, argb);
        BufferUploader.drawWithShader(buf.end());
    }

    private static void quad(Matrix4f mat, float x0, float y0, float x1, float y1, int argb) {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(buf, mat, x0, y0, argb);
        vertex(buf, mat, x0, y1, argb);
        vertex(buf, mat, x1, y1, argb);
        vertex(buf, mat, x1, y0, argb);
        BufferUploader.drawWithShader(buf.end());
    }

    /** Dark radial veil over the world, lighter in the middle. */
    private static void veil(Matrix4f mat, float cx, float cy, float w, float h, float e) {
        float r = (float) Math.hypot(w, h);
        ring(mat, cx, cy, 0f, 40f, 0f, 360f, argb(0, (int) (77 * e)), argb(0, (int) (77 * e)));
        ring(mat, cx, cy, 40f, r, 0f, 360f, argb(0, (int) (77 * e)), argb(0, (int) (158 * e)));
    }

    private static void vertex(BufferBuilder buf, Matrix4f mat, float x, float y, int argb) {
        buf.vertex(mat, x, y, 0f).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Interface sounds shared with the clan screens (heralisz_clans), quiet vanilla click without them

    private static final ResourceLocation CLIC = new ResourceLocation("heralisz_clans", "ui_clic");
    private static final ResourceLocation FERMER = new ResourceLocation("heralisz_clans", "ui_fermer");

    static void uiSound(ResourceLocation id, float volume) {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (s == null) {
            s = net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value();
            volume *= 0.35f;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, 1f, volume));
    }
}
