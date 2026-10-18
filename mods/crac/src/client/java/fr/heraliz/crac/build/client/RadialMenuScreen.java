package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.client.kit.EcranCanevas;
import fr.heraliz.crac.client.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Rust-like radial menu (validated mock-up): open while a key or mouse button is held, point at a segment, release to
 * pick it (a left click picks too, Escape cancels). Glass segments around a dark hub that describes the entry under the
 * mouse; the hovered segment turns red and moves out a little, the entry in use has a white edge. Drawn on the 640 x 360
 * canvas of our screens.
 */
@OnlyIn(Dist.CLIENT)
public abstract class RadialMenuScreen extends EcranCanevas {
    /** Inner and outer radius of the segments, how far the hovered one moves out, hub radius, radius of the entries. */
    protected static final float R_IN = 50.0F;
    protected static final float R_OUT = 113.33F;
    protected static final float POP = 3.33F;
    protected static final float R_HUB = 46.0F;
    protected static final float R_ENTRY = 81.67F;
    /** Gap on each side of a segment, in radians. */
    private static final double GAP = 0.018;

    /** How a segment looks besides hover. */
    protected enum Look { NORMAL, CURRENT, DIMMED, DANGER }

    /** The key that opened the last wheel: no new wheel opens until it is released. */
    private static InputConstants.Key latch;

    private final InputConstants.Key trigger;
    protected int hovered = -1;
    private boolean done;
    private float[] pops = new float[0];
    private long lastFrame;

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

    /** The entry currently in use (white edge). */
    protected boolean active(int i) {
        return false;
    }

    protected Look look(int i) {
        return this.active(i) ? Look.CURRENT : this.enabled(i) ? Look.NORMAL : Look.DIMMED;
    }

    /** Draws entry {@code i}: icon box top at y - 17.33, centred on x; {@code alpha} 0..1. */
    protected abstract void drawEntry(GuiGraphics g, int i, float x, float y, boolean hover, float alpha);

    /** Le choix que le tutoriel des bases demande (HeraliZ) : il clignote en vert. */
    protected boolean conseille(int i) {
        return false;
    }

    protected abstract void drawHub(GuiGraphics g, float cx, float cy);

    /** The line above the wheel. */
    protected abstract String heading();

    protected abstract void pick(int i);

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

    /** Angle of the middle of segment i, in radians from noon, clockwise (the first segment points up). */
    private double angle(int i) {
        return i * 2 * Math.PI / this.count();
    }

    private int pickIndex(double dx, double dy) {
        if (Math.sqrt(dx * dx + dy * dy) < R_IN - 8) {
            return -1;
        }
        double a = Math.atan2(dx, -dy) + Math.PI / this.count();
        double twoPi = Math.PI * 2;
        a = (a % twoPi + twoPi) % twoPi;
        return Math.min(this.count() - 1, (int) (a / (twoPi / this.count())));
    }

    @Override
    protected void rendu(GuiGraphics g, float mouseX, float mouseY, float pt) {
        long now = System.nanoTime();
        float dt = this.lastFrame == 0 ? 0 : Math.min(0.1F, (now - this.lastFrame) / 1e9F);
        this.lastFrame = now;
        int n = this.count();
        if (this.pops.length != n) {
            this.pops = new float[n];
        }
        // the world stays visible, darker
        rect(g, 0, 0, this.cw, this.ch, a(0, 97));
        float cx = this.cw / 2F;
        float cy = this.ch / 2F;
        this.hovered = this.pickIndex(mouseX - cx, mouseY - cy);
        double half = Math.PI / n;
        for (int i = 0; i < n; i++) {
            boolean hov = i == this.hovered;
            this.pops[i] += ((hov && this.enabled(i) ? 1 : 0) - this.pops[i]) * (1 - (float) Math.pow(0.0001, dt));
            float pop = POP * this.pops[i];
            double a0 = this.angle(i) - half + GAP, a1 = this.angle(i) + half - GAP;
            Look look = this.look(i);
            int fill;
            int edge;
            float t = this.px;
            if (hov && this.enabled(i)) {
                fill = a(0x961814, 158);
                edge = a(0xFF5A4E, 255);
                t = 2 * this.px;
            } else {
                fill = switch (look) {
                    case CURRENT -> a(0x0C0E12, 209);
                    case DIMMED -> a(0x0C0E12, 140);
                    case DANGER -> a(0x160A0A, 199);
                    default -> a(0x0C0E12, 189);
                };
                edge = switch (look) {
                    case CURRENT -> a(BLANC, 102);
                    case DIMMED -> a(BLANC, hov ? 40 : 15);
                    case DANGER -> a(ROUGE, 115);
                    default -> a(BLANC, 31);
                };
            }
            if (!hov && this.conseille(i)) {
                // HeraliZ : choix demandé par le tutoriel, vert qui pulse
                float k = 0.5F + 0.5F * (float) Math.sin(now / 1.6e8);
                fill = a(0x1E5A32, 150 + 60 * k);
                edge = a(0x55E07A, 150 + 105 * k);
                t = 2 * this.px;
            }
            secteur(g, cx, cy, R_IN + pop, R_OUT + pop, a0, a1, fill);
            contourSecteur(g, cx, cy, R_IN + pop, R_OUT + pop, a0, a1, t, edge);
            if (look == Look.CURRENT) {
                secteur(g, cx, cy, R_OUT + pop - 1.33F, R_OUT + pop, a0, a1, a(BLANC, 191));
            }
        }
        disque(g, cx, cy, R_HUB, a(0x0A0C10, 219));
        anneau(g, cx, cy, R_HUB - this.px, R_HUB, a(BLANC, 36));
        for (int i = 0; i < n; i++) {
            float r = R_ENTRY + POP * this.pops[i];
            double a = this.angle(i);
            float x = cx + r * (float) Math.sin(a);
            float y = cy - r * (float) Math.cos(a);
            this.drawEntry(g, i, x, y, i == this.hovered && this.enabled(i), this.look(i) == Look.DIMMED && i != this.hovered ? 0.45F : 1);
        }
        this.drawHub(g, cx, cy);
        capsCentre(g, this.font, this.heading(), MOYEN_GRAS, cx, cy - 134.33F - 2.68F, 1.44F, a(TEXTE, 217));
        this.drawHelp(g, cx, cy + 141.33F);
    }

    /** "Release [key] or click: choose    [Esc] cancel". */
    private void drawHelp(GuiGraphics g, float cx, float cy) {
        String release = Component.translatable("crac.wheel.release").getString();
        String key = this.trigger.getDisplayName().getString();
        String choose = Component.translatable("crac.wheel.choose").getString();
        String esc = Component.translatable("crac.wheel.escape").getString();
        String cancel = Component.translatable("crac.wheel.cancel").getString();
        float total = largeur(this.font, release, MINI) + 3.33F + largeurTouche(this.font, key, 0.4F, 7.33F) + 3.33F
            + largeur(this.font, choose, MINI) + 6.67F + 3.33F + largeurTouche(this.font, esc, 0.4F, 7.33F) + 3.33F + largeur(this.font, cancel, MINI);
        float x = cx - total / 2;
        int color = a(TEXTE, 191);
        texte(g, this.font, lit(release, MINI), x, cy - 4.21F, color);
        x += largeur(this.font, release, MINI) + 3.33F;
        x += touche(g, this.font, key, x, cy, 6.67F, 0.4F, 7.33F, this.px) + 3.33F;
        texte(g, this.font, lit(choose, MINI), x, cy - 4.21F, color);
        x += largeur(this.font, choose, MINI) + 6.67F + 3.33F;
        x += touche(g, this.font, esc, x, cy, 6.67F, 0.4F, 7.33F, this.px) + 3.33F;
        texte(g, this.font, lit(cancel, MINI), x, cy - 4.21F, color);
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
    protected boolean clic(double mx, double my, int button) {
        if (button == 0) {
            this.commit();
        }
        return true;
    }

    @Override
    protected boolean relache(double mx, double my, int button) {
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

    // ------------------------------------------------------------------ hub helpers

    /** One line of the hub: text, font, colour, and the space it takes. */
    protected record HubLine(Component text, int argb, float height, boolean title) {
    }

    /** Title of the hub in spaced capitals (shrunk to fit). */
    protected static HubLine title(String s) {
        return new HubLine(Component.literal(s), a(BLANC, 255), 12, true);
    }

    protected static HubLine line(Component c, int argb) {
        return new HubLine(c, argb, 8.33F, false);
    }

    /** Draws the lines centred on the hub; long lines wrap. */
    protected void hub(GuiGraphics g, float cx, float cy, List<HubLine> lines, float titleScale) {
        List<Object[]> rows = new ArrayList<>();
        float total = 0;
        for (HubLine l : lines) {
            if (l.title()) {
                rows.add(new Object[]{l, null});
                total += l.height();
            } else {
                for (FormattedCharSequence part : this.font.split(lit(l.text().getString(), MINI), 82)) {
                    rows.add(new Object[]{l, part});
                    total += l.height();
                }
            }
        }
        float y = cy - total / 2 - 1;
        for (Object[] row : rows) {
            HubLine l = (HubLine) row[0];
            float mid = y + l.height() / 2;
            if (l.title()) {
                float spacing = 1.12F;
                float w = largeurCaps(this.font, l.text().getString(), TITRE, spacing / titleScale) * titleScale;
                float scale = w > 84 ? titleScale * 84 / w : titleScale;
                g.pose().pushPose();
                g.pose().translate(cx - Math.min(w, 84) / 2, mid - (3.91F + 3.5F) * scale, 0);
                g.pose().scale(scale, scale, 1);
                caps(g, this.font, l.text().getString(), TITRE, 0, 0, spacing / scale, l.argb());
                g.pose().popPose();
            } else {
                FormattedCharSequence part = (FormattedCharSequence) row[1];
                float w = this.font.width(part);
                if (l.argb() >>> 24 >= 4) {
                    g.pose().pushPose();
                    g.pose().translate(cx - w / 2, mid - 4.21F, 0);
                    g.drawString(this.font, part, 0, 0, l.argb(), false);
                    g.pose().popPose();
                }
            }
            y += l.height();
        }
    }

    /** Label under an entry's icon (bold small text), centred. */
    protected void label(GuiGraphics g, String s, float x, float y, int argb) {
        String shown = Ui.ellipsis(this.font, s, MINI_GRAS, 46);
        centre(g, this.font, lit(shown, MINI_GRAS), x, y - 4.21F, argb);
    }
}
