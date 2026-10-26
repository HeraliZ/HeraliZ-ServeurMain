package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.HeralizCrates;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Charte des interfaces du mod : police DM Sans (assets/heralizcrates/font, licence OFL), couleurs sobres, textes en
 * capitales espacees, filets et infobulles maison. Pas de cadres ni de boutons vanilla.
 */
public final class Ui {
    private Ui() {
    }

    /** DM Sans Medium (texte courant), Bold (titres, boutons), Medium petit corps (legendes). */
    public static final ResourceLocation FONT = HeralizCrates.id("ui"), BOLD = HeralizCrates.id("ui_bold"), SMALL = HeralizCrates.id("ui_small");

    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, DANGER = 0xFF6B6B, OK = 0x5FD08A;

    public static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    public static MutableComponent tr(ResourceLocation font, String key, Object... args) {
        return Component.translatable(key, args).withStyle(st -> st.withFont(font));
    }

    /** Le composant dans une police (ses morceaux sans police propre en heritent). */
    public static MutableComponent in(Component c, ResourceLocation font) {
        return Component.empty().withStyle(st -> st.withFont(font)).append(c);
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ capitales espacees
    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            w += font.width(lit(ch, f)) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            MutableComponent c = lit(ch, f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + spacing;
        }
    }

    /**
     * Texte en capitales espacees coupe avec « … » s'il depasse la largeur, mesure comme il est dessine (capitales,
     * espacement). Rend le texte en capitales.
     */
    public static String ellipsisSpaced(Font font, String s, ResourceLocation f, float spacing, float maxWidth) {
        String up = s.toUpperCase(Locale.ROOT);
        if (spacedWidth(font, up, f, spacing) <= maxWidth) return up;
        String dots = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            if (spacedWidth(font, sb + ch + dots, f, spacing) > maxWidth) break;
            sb.append(ch);
        }
        return sb.toString().trim() + dots;
    }

    /** Texte coupe avec « … » s'il depasse la largeur (texte simple, sans espacement). */
    public static String ellipsis(Font font, String s, ResourceLocation f, int maxWidth) {
        if (font.width(lit(s, f)) <= maxWidth) return s;
        String dots = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            if (font.width(lit(sb + ch + dots, f)) > maxWidth) break;
            sb.append(ch);
        }
        return sb.toString().trim() + dots;
    }

    public static void spacedCentered(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, cx - w / 2f, y, argb, spacing);
    }

    public static void centered(GuiGraphics g, Font font, Component c, float cx, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx - font.width(c) / 2f, y, 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ filets
    /** Filet horizontal d'un pixel qui s'efface vers les deux bouts (ou vers un seul). */
    public static void hairline(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha, boolean fadeLeft, boolean fadeRight) {
        int w = x1 - x0;
        if (w <= 0) return;
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float a = 1;
            if (fadeLeft && fadeRight) a = 1 - Math.abs(t * 2 - 1);
            else if (fadeLeft) a = t;
            else if (fadeRight) a = 1 - t;
            a = a * a * (3 - 2 * a);
            int xa = x0 + i * w / steps, xb = x0 + (i + 1) * w / steps;
            g.fill(xa, y, xb, y + 1, argb(rgb, (int) (alpha * a)));
        }
    }

    /** Bande horizontale qui s'efface vers ses deux bouts sur fade pixels. */
    public static void band(GuiGraphics g, int x0, int y0, int x1, int y1, int rgb, int alpha, int fade) {
        g.fill(x0 + fade, y0, x1 - fade, y1, argb(rgb, alpha));
        int steps = Math.max(1, fade / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            int a = (int) (alpha * t * t * (3 - 2 * t));
            int w0 = i * fade / steps, w1 = (i + 1) * fade / steps;
            g.fill(x0 + w0, y0, x0 + w1, y1, argb(rgb, a));
            g.fill(x1 - w1, y0, x1 - w0, y1, argb(rgb, a));
        }
    }

    // ------------------------------------------------------------------ infobulle
    /** Infobulle sobre : fond sombre, liseré a la couleur d'accent, premiere ligne en titre. */
    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY, int accent) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (Component l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() == 1 ? 9 : lines.size() * 10 + 1;
        int x = mouseX + 12, y = mouseY - 10;
        if (x + w + 8 > g.guiWidth()) x = mouseX - w - 14;
        y = Math.max(6, Math.min(y, g.guiHeight() - h - 6));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 6, y - 5, x + w + 6, y + h + 3, 0xF20B0C0F);
        g.fill(x - 6, y - 5, x + w + 6, y - 4, argb(accent, 200));
        g.fill(x - 6, y - 4, x - 5, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x + w + 5, y - 4, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x - 6, y + h + 2, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        int ly = y;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, ly, 0xFFFFFFFF, false);
            ly += i == 0 ? 12 : 10;
        }
        g.pose().popPose();
    }

    /** Pourcentage lisible : 50, 12,5, 3,33, 0,25, 0,005 (virgule en francais). */
    public static String pct(double v) {
        String s;
        if (v <= 0) s = "0";
        else if (v >= 10) s = String.format(Locale.ROOT, "%.1f", v);
        else if (v >= 0.01) s = String.format(Locale.ROOT, "%.2f", v);
        else s = String.format(Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        if (CardArt.french()) s = s.replace('.', ',');
        return s + " %";
    }
}
