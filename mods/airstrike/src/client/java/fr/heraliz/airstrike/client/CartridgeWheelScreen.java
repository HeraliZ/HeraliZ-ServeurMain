package fr.heraliz.airstrike.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Cartridge wheel: opens while the swap key is held, the mouse direction picks a cartridge, releasing the key (or
 * clicking) loads it. The centre cancels.
 */
public class CartridgeWheelScreen extends Screen {
    private static final StrikeType[] TYPES = StrikeType.values();
    private static final float R_IN = 46f, R_OUT = 122f;
    private static final float GAP = 0.035f;

    private int hovered = -1;
    private int age;
    private boolean done;
    /** Development: forces the pointer direction (degrees clockwise from the top). */
    private Float forcedAngle;

    public CartridgeWheelScreen() {
        super(Component.translatable("hud.airstrike.wheel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void pointAt(float degrees) {
        forcedAngle = degrees;
    }

    @Override
    public void tick() {
        age++;
        // the wheel lives as long as the key is held
        if (age > 1 && forcedAngle == null && !keyHeld()) confirm();
    }

    private boolean keyHeld() {
        InputConstants.Key key = DesignatorClient.CYCLE_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
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
        confirm();
        return true;
    }

    /** Loads the pointed cartridge (if any) and closes the wheel. */
    public void confirm() {
        if (done) return;
        done = true;
        int pick = hovered;
        onClose();
        if (pick >= 0 && isAvailable(TYPES[pick]) && TYPES[pick] != DesignatorClient.loaded()) {
            DesignatorClient.load(TYPES[pick]);
        }
    }

    private static boolean isAvailable(StrikeType t) {
        return DesignatorClient.availableTypes().contains(t);
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Font font = Minecraft.getInstance().font;
        float cx = width / 2f, cy = height / 2f;
        float open = Mth.clamp((age + pt) / 4f, 0f, 1f);
        float grow = 1f - (1f - open) * (1f - open);
        // fits between the title and the hotbar at any GUI scale
        float fit = Math.min(1f, (height / 2f - 32f) / R_OUT);
        float rIn = R_IN * fit, rOut = R_OUT * fit;
        // pointer: mouse direction from the centre
        double dx = mouseX - cx, dy = mouseY - cy;
        if (forcedAngle != null) {
            dx = Math.sin(Math.toRadians(forcedAngle)) * 80;
            dy = -Math.cos(Math.toRadians(forcedAngle)) * 80;
        }
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > rIn * 0.55f) {
            double ang = Math.atan2(dx, -dy);                      // clockwise from the top
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.floor((ang + Math.PI / TYPES.length) / (Math.PI * 2 / TYPES.length)) % TYPES.length;
        } else {
            hovered = -1;
        }

        g.fill(0, 0, width, height, (int) (0x70 * open) << 24);
        g.flush();
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        StrikeType loaded = DesignatorClient.loaded();
        float step = Mth.TWO_PI / TYPES.length;
        for (int i = 0; i < TYPES.length; i++) {
            StrikeType t = TYPES[i];
            boolean avail = isAvailable(t);
            boolean hot = i == hovered;
            float a0 = -Mth.HALF_PI + (i - 0.5f) * step + GAP, a1 = a0 + step - 2 * GAP;
            float r1 = (rOut + (hot ? 10f * fit : 0f)) * grow, r0 = rIn * grow;
            int base = avail ? (hot ? 0xE0000000 | (t.uiColor & 0xFFFFFF) : 0xC0181C14) : 0x90101010;
            if (hot && avail) {
                sector(mat, cx, cy, r0, r1, a0, a1, darken(t.uiColor, 0.55f, 0xE0));
                sector(mat, cx, cy, r1 - 3f, r1, a0, a1, 0xFF000000 | t.uiColor);
            } else {
                sector(mat, cx, cy, r0, r1, a0, a1, base);
            }
            if (t == loaded) sector(mat, cx, cy, r0, r0 + 3f, a0, a1, 0xFF5CFF6A);
        }
        sector(mat, cx, cy, 0f, rIn * grow - 4f, 0f, Mth.TWO_PI, 0xD0101410);
        RenderSystem.disableBlend();

        // icons, names and stock
        for (int i = 0; i < TYPES.length; i++) {
            StrikeType t = TYPES[i];
            boolean avail = isAvailable(t);
            boolean hot = i == hovered;
            float mid = -Mth.HALF_PI + i * step;
            float rr = (rIn + rOut) / 2f * grow + (hot ? 5f * fit : 0f);
            float ix = cx + Mth.cos(mid) * rr, iy = cy + Mth.sin(mid) * rr;
            float s = (hot ? 1.9f : 1.5f) * Math.max(0.8f, fit);
            g.pose().pushPose();
            g.pose().translate(ix - 8 * s, iy - 8 * s - 5, 0);
            g.pose().scale(s, s, 1f);
            g.renderItem(new ItemStack(ModItems.payload(t)), 0, 0);
            g.pose().popPose();
            if (!avail) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill((int) (ix - 8 * s), (int) (iy - 8 * s - 5), (int) (ix + 8 * s), (int) (iy + 8 * s - 5), 0xA0101010);
                g.pose().popPose();
            }
            String qty = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild ? "∞"
                    : "x" + DesignatorClient.payloadCount(t);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawCenteredString(font, qty, (int) ix, (int) (iy + 8 * s - 2), avail ? 0xFFFFFFFF : 0xFF777777);
            g.pose().popPose();
        }

        // centre: what is pointed at
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (hovered >= 0) {
            StrikeType t = TYPES[hovered];
            boolean avail = isAvailable(t);
            g.drawCenteredString(font, Component.translatable(t.translationKey()), (int) cx, (int) cy - 14, 0xFF000000 | t.uiColor);
            Component state = t == loaded ? Component.translatable("hud.airstrike.loaded")
                    : avail ? Component.translatable("hud.airstrike.wheel.release") : Component.translatable("hud.airstrike.wheel.none");
            g.drawCenteredString(font, state, (int) cx, (int) cy - 2, avail ? 0xFF5CFF6A : 0xFFFF6A4A);
            // description beside the wheel when there is room, under it otherwise
            int side = (int) (cx + rOut + 18);
            int room = width - side - 8;
            if (room >= 90) {
                List<FormattedCharSequence> desc = font.split(Component.translatable(t.descriptionKey()), Math.min(150, room));
                int y = (int) cy - desc.size() * 5;
                g.fill(side - 6, y - 6, side + Math.min(150, room) + 6, y + desc.size() * 10 + 4, 0x90101410);
                for (FormattedCharSequence line : desc) {
                    g.drawString(font, line, side, y, 0xFFE0E0E0);
                    y += 10;
                }
            } else {
                List<FormattedCharSequence> desc = font.split(Component.translatable(t.descriptionKey()), 150);
                int y = (int) (cy + rOut * grow + 18);
                for (FormattedCharSequence line : desc) {
                    g.drawCenteredString(font, line, (int) cx, y, 0xFFE0E0E0);
                    y += 10;
                }
            }
        } else {
            g.drawCenteredString(font, Component.translatable("hud.airstrike.wheel.cancel"), (int) cx, (int) cy - 4, 0xFFB0B0B0);
        }
        g.drawCenteredString(font, getTitle(), (int) cx, (int) (cy - rOut * grow - 18), 0xFFFFFFFF);
        g.pose().popPose();
    }

    private static int darken(int rgb, float k, int alpha) {
        int r = (int) (((rgb >> 16) & 255) * k), gg = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (alpha << 24) | (r << 16) | (gg << 8) | b;
    }

    /** Ring sector (angles in radians, clockwise on screen). */
    private static void sector(Matrix4f mat, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f, gr = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(4, (int) ((a1 - a0) / 0.06f));
        for (int i = 0; i <= steps; i++) {
            float ang = a0 + (a1 - a0) * i / steps;
            float c = Mth.cos(ang), s = Mth.sin(ang);
            buf.vertex(mat, cx + c * r1, cy + s * r1, 0).color(r, gr, b, a).endVertex();
            buf.vertex(mat, cx + c * r0, cy + s * r0, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }
}
