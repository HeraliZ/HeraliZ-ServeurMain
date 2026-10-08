package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.client.RadialStyle;
import fr.heraliz.crac.client.ScaledScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Rust-like radial menu: open while a key or mouse button is held, point at a segment, release to pick it (a left
 * click picks too, Escape cancels).
 */
@OnlyIn(Dist.CLIENT)
public abstract class RadialMenuScreen extends ScaledScreen {
    protected static final double R_IN = 50.0;
    protected static final double R_OUT = 116.0;
    protected static final double R_HOVER = 122.0;
    protected static final double R_ICON = 83.0;
    protected static final int WHITE = 0xFFFFFFFF;
    protected static final int GREY = 0xFF8A8A8A;
    protected static final int DARK_GREY = 0xFF5A5A5A;
    protected static final int RED = 0xFFFF6060;
    protected static final int GREEN = 0xFF7CFC7C;
    protected static final int GOLD = 0xFFFFD27A;

    /** The key that opened the last wheel: no new wheel opens until it is released. */
    private static InputConstants.Key latch;

    private final InputConstants.Key trigger;
    protected int hovered = -1;
    private boolean done;

    protected RadialMenuScreen(Component title, InputConstants.Key trigger) {
        super(title);
        this.trigger = trigger;
    }

    /** False until the key or button that opened the previous wheel has been released. */
    public static boolean mayOpen() {
        return latch == null;
    }

    /** Called every client tick: forgets the previous wheel's key once it is up. */
    public static void tickLatch() {
        if (latch != null && !BuildKeys.isHeld(latch)) {
            latch = null;
        }
    }

    protected abstract int count();

    protected abstract boolean enabled(int i);

    /** The entry currently in use (highlighted). */
    protected boolean active(int i) {
        return false;
    }

    /** Draws entry {@code i} centred on (x, y). */
    protected abstract void drawEntry(GuiGraphics g, int i, int x, int y, boolean hover);

    protected abstract void drawHub(GuiGraphics g, int cx, int cy);

    protected abstract void pick(int i);

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        RadialStyle.dim(g, this.guiW(), this.guiH());
    }

    @Override
    public void onClose() {
        latch = this.trigger;
        super.onClose();
    }

    /** Test bench only (dev/CracHarness): keeps the wheel open as if its key were held, for screenshots. */
    public static boolean benchHold;

    @Override
    public void tick() {
        if (!benchHold && !BuildKeys.isHeld(this.trigger)) {
            this.commit();
        }
    }

    private double base() {
        return Math.toRadians(-90.0) - Math.PI / this.count();
    }

    private int pickIndex(double dx, double dy) {
        if (Math.sqrt(dx * dx + dy * dy) < R_IN - 8) {
            return -1;
        }
        double twoPi = Math.PI * 2;
        double rel = (Math.atan2(dy, dx) - this.base()) % twoPi;
        if (rel < 0) {
            rel += twoPi;
        }
        return Math.min(this.count() - 1, (int) (rel / (twoPi / this.count())));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        int cx = this.guiW() / 2;
        int cy = this.guiH() / 2;
        int n = this.count();
        this.hovered = this.pickIndex(mouseX - cx, mouseY - cy);
        double arc = Math.PI * 2 / n;
        double base = this.base();
        Matrix4f mat = g.pose().last().pose();
        RadialStyle.begin();
        for (int i = 0; i < n; i++) {
            boolean on = this.enabled(i);
            boolean hov = i == this.hovered;
            int col = hov ? (on ? RadialStyle.SEG_HOVER : RadialStyle.SEG_OFF) : this.active(i) ? RadialStyle.SEG_ACTIVE : on ? RadialStyle.SEG : RadialStyle.SEG_OFF;
            RadialStyle.segment(mat, cx, cy, R_IN, hov && on ? R_HOVER : R_OUT, base + i * arc, base + (i + 1) * arc, col);
        }
        RadialStyle.disc(mat, cx, cy, R_IN - 4, RadialStyle.HUB);
        RadialStyle.ring(mat, cx, cy, R_IN - 4, 1.0, RadialStyle.RING);
        RadialStyle.end();
        for (int i = 0; i < n; i++) {
            double a = base + (i + 0.5) * arc;
            int x = cx + (int) Math.round(Math.cos(a) * R_ICON);
            int y = cy + (int) Math.round(Math.sin(a) * R_ICON);
            this.drawEntry(g, i, x, y, i == this.hovered);
        }
        this.drawHub(g, cx, cy);
    }

    private void commit() {
        if (this.done) {
            return;
        }
        this.done = true;
        int i = this.hovered;
        this.onClose();
        if (i >= 0 && i < this.count() && this.enabled(i)) {
            this.pick(i);
        }
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        if (button == 0) {
            this.commit();
        }
        return true;
    }

    @Override
    protected boolean releaseContent(double mx, double my, int button) {
        if (this.trigger.getType() == InputConstants.Type.MOUSE && this.trigger.getValue() == button) {
            this.commit();
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.trigger.getType() != InputConstants.Type.MOUSE && this.trigger.getValue() == keyCode) {
            this.commit();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ text helpers

    /** Centred text scaled by {@code scale}, top at y. */
    protected void text(GuiGraphics g, String s, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawCenteredString(this.font, s, 0, 0, color);
        g.pose().popPose();
    }

    /** Splits a label on spaces into lines no wider than {@code width} at the given scale. */
    protected List<String> wrap(String s, int width, float scale) {
        Font f = this.font;
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && f.width(next) * scale > width) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Wrapped lines centred around y. */
    protected void lines(GuiGraphics g, String s, int x, int y, int width, int color, float scale) {
        List<String> ls = this.wrap(s, width, scale);
        int lh = Math.round(10 * scale);
        int top = y - ls.size() * lh / 2;
        for (int i = 0; i < ls.size(); i++) {
            this.text(g, ls.get(i), x, top + i * lh, color, scale);
        }
    }
}
