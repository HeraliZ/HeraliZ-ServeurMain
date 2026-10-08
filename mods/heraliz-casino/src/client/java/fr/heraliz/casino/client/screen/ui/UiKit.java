package fr.heraliz.casino.client.screen.ui;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.render.HDTexture;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Dessin de l'interface du casino a partir de l'atlas HD (panneaux, pastilles, boutons, champ de saisie).
 */
public final class UiKit {
    private UiKit() {
    }

    public static final ResourceLocation TEX = HeralizCasino.id("textures/gui/casino_ui.png");
    public static final int GOLD = 0xFFD36A, CREAM = 0xF2E6CC, GREY = 0xA8A0B0, RED = 0xFF6A6A, GREEN = 0x7CFF9A, BLUE = 0x8AC4FF;

    /**
     * Style des interfaces HeraliZ (charte des clans : verre, DM Sans, sans ombre), active seulement pendant le rendu des
     * ecrans d'administration (tablette du directeur) ; les ecrans a theme du casino gardent l'atlas et la police pixel.
     */
    public static boolean clan;

    /** En style clan : le texte passe en DM Sans (gras si le composant l'etait), sinon inchange. */
    public static Component prep(Component c) {
        if (!clan || c == null) return c;
        net.minecraft.resources.ResourceLocation f = c.getStyle().getFont();
        if (f.getNamespace().equals(HeralizCasino.MODID) && f.getPath().startsWith("ui")) return c;
        boolean bold = c.getStyle().isBold();
        return c.copy().withStyle(s -> s.withBold(false).withFont(bold ? fr.heraliz.casino.client.screen.kit.Ui.BOLD : fr.heraliz.casino.client.screen.kit.Ui.FONT));
    }

    private static ResourceLocation tex() {
        return HDTexture.get(TEX);
    }

    /** Morceau de l'atlas (texels) etire sur un rectangle d'interface. */
    public static void piece(GuiGraphics g, float x, float y, float w, float h, float u, float v, float uw, float vh) {
        if (w <= 0 || h <= 0) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / 64f, h / 64f, 1);
        g.blit(tex(), 0, 0, 64, 64, u, v, (int) uw, (int) vh, UiAtlas.SIZE, UiAtlas.SIZE);
        g.pose().popPose();
    }

    /** Element 9-slice : les coins gardent leur taille (bord / 4 px d'interface), le reste s'etire. */
    public static void nine(GuiGraphics g, int[] r, float x, float y, float w, float h) {
        float b = r[4] / (float) UiAtlas.SCALE, t = r[4];
        b = Math.min(b, Math.min(w, h) / 2);
        float tb = b * UiAtlas.SCALE;
        float u0 = r[0], v0 = r[1], u1 = r[0] + r[2], v1 = r[1] + r[3];
        float cw = w - 2 * b, ch = h - 2 * b, ctw = r[2] - 2 * t, cth = r[3] - 2 * t;
        // coins
        piece(g, x, y, b, b, u0, v0, tb, tb);
        piece(g, x + w - b, y, b, b, u1 - tb, v0, tb, tb);
        piece(g, x, y + h - b, b, b, u0, v1 - tb, tb, tb);
        piece(g, x + w - b, y + h - b, b, b, u1 - tb, v1 - tb, tb, tb);
        // bords et centre
        piece(g, x + b, y, cw, b, u0 + t, v0, ctw, tb);
        piece(g, x + b, y + h - b, cw, b, u0 + t, v1 - tb, ctw, tb);
        piece(g, x, y + b, b, ch, u0, v0 + t, tb, cth);
        piece(g, x + w - b, y + b, b, ch, u1 - tb, v0 + t, tb, cth);
        piece(g, x + b, y + b, cw, ch, u0 + t, v0 + t, ctw, cth);
    }

    public static void image(GuiGraphics g, int[] r, float x, float y, float w, float h) {
        piece(g, x, y, w, h, r[0], r[1], r[2], r[3]);
    }

    /** Panneau de verre fume (le rectangle donne est la partie visible ; l'ombre deborde). */
    public static void panel(GuiGraphics g, float x, float y, float w, float h) {
        if (clan) {
            fr.heraliz.casino.client.screen.kit.Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
            return;
        }
        float s = UiAtlas.PANEL_SHADOW / (float) UiAtlas.SCALE;
        nine(g, UiAtlas.PANEL, x - s, y - s, w + 2 * s, h + 2 * s);
    }

    public static void pill(GuiGraphics g, float x, float y, float w, float h) {
        if (clan) {
            fr.heraliz.casino.client.screen.kit.Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0x14FFFFFF);
            return;
        }
        nine(g, UiAtlas.PILL, x, y, w, h);
    }

    public static void field(GuiGraphics g, float x, float y, float w, float h) {
        if (clan) {
            int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
            g.fill(ix, iy, ix + iw, iy + ih, 0x60000000);
            g.fill(ix, iy + ih - 1, ix + iw, iy + ih, 0x30FFFFFF);
            return;
        }
        nine(g, UiAtlas.FIELD, x, y, w, h);
    }

    public static void button(GuiGraphics g, int style, boolean hover, float x, float y, float w, float h) {
        nine(g, UiAtlas.button(style, hover), x, y, w, h);
    }

    public static void round(GuiGraphics g, int style, boolean hover, float x, float y, float size) {
        image(g, UiAtlas.round(style, hover), x, y, size, size);
    }

    public static void ring(GuiGraphics g, float x, float y, float size) {
        image(g, UiAtlas.RING, x, y, size, size);
    }

    public static void halo(GuiGraphics g, float cx, float cy, float radius, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
        image(g, UiAtlas.HALO, cx - radius, cy - radius, radius * 2, radius * 2);
        g.setColor(1, 1, 1, 1);
    }

    // =================================================================== texte
    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Texte a l'echelle donnee ; align : -1 gauche, 0 centre, 1 droite ; (x, y) = point d'ancrage haut. */
    public static void text(GuiGraphics g, Component c, float x, float y, int color, float scale, int align, boolean shadow) {
        c = prep(c);
        if (clan) shadow = false;
        Font f = font();
        float w = f.width(c) * scale;
        float x0 = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, c, 0, 0, 0xFF000000 | color, shadow);
        g.pose().popPose();
    }

    /** Texte detoure (lisible sur la scene 3D, sans fond) ; align : -1 gauche, 0 centre, 1 droite. */
    public static void outlined(GuiGraphics g, Component c, float x, float y, int color, float scale, int align) {
        Font f = font();
        net.minecraft.util.FormattedCharSequence seq = c.getVisualOrderText();
        float w = f.width(seq) * scale;
        float x0 = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        g.pose().pushPose();
        g.pose().translate(x0, y, 0);
        g.pose().scale(scale, scale, 1);
        f.drawInBatch8xOutline(seq, 0, 0, 0xFF000000 | color, 0xE0000000, g.pose().last().pose(), g.bufferSource(),
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        g.flush();
        g.pose().popPose();
    }

    /** Texte detoure reduit si besoin pour tenir dans maxW. */
    public static void outlinedFit(GuiGraphics g, Component c, float x, float y, float maxW, int color, float scale, int align) {
        float w = font().width(c) * scale;
        float s = w > maxW ? scale * maxW / w : scale;
        outlined(g, c, x, y + (scale - s) * 4, color, s, align);
    }

    /** Texte reduit si besoin pour tenir dans maxW. */
    public static void fitted(GuiGraphics g, Component c, float x, float y, float maxW, int color, float scale, int align, boolean shadow) {
        c = prep(c);
        float w = font().width(c) * scale;
        float s = w > maxW ? scale * maxW / w : scale;
        text(g, c, x, y + (scale - s) * 4, color, s, align, shadow);
    }

    /** Paragraphe coupe a la largeur ; renvoie la hauteur utilisee. */
    public static int wrapped(GuiGraphics g, Component c, float x, float y, int maxW, int color, float scale) {
        c = prep(c);
        List<FormattedCharSequence> lines = font().split(c, Math.max(10, (int) (maxW / scale)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) g.drawString(font(), lines.get(i), 0, i * 10, 0xFF000000 | color, false);
        g.pose().popPose();
        return Math.round(lines.size() * 10 * scale);
    }

    public static int wrappedHeight(Component c, int maxW, float scale) {
        c = prep(c);
        return Math.round(font().split(c, Math.max(10, (int) (maxW / scale))).size() * 10 * scale);
    }

    /** Montant : 12500 -> "12 500". */
    public static String fmt(long n) {
        return fr.heraliz.casino.economy.Chips.format(n);
    }

    public static String mult(double m) {
        if (Math.abs(m - Math.round(m)) < 0.005) return "x" + Math.round(m);
        return String.format(java.util.Locale.ROOT, "x%.2f", m);
    }
}
