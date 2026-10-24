package fr.heraliz.crates.client.gui.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dessin des écrans des caisses au niveau de la boutique premium (Caisses 2.22.0, maquette « variante A Vitrine »
 * validée le 24/10, outils/apercu_caisses_premium.py) : formes à coordonnées libres (rectangles, dégradés, bords, disques,
 * lueurs, traits, triangles), comme client/boutique/Trace de HeraliZ Grades, et textes DM Sans tracés à leur vraie taille
 * (une police par taille, polices cp_*, jamais agrandies), posés sur un point entier de l'écran ({@link Ui#placer}).
 */
public final class Trace {
    private Trace() {
    }

    /**
     * Placement vertical des lettres TTF de Minecraft 1.20.1 (voir CaseOpenScreen avant la 2.22.0 et le kit de Tactique) :
     * ligne de base = point de dessin + ascendante + 2 x décalage du .json - 3. Nos polices cp_* n'ont pas de décalage
     * (shift 0) et portent la taille em x 1,302 (ascendante + descendante de DM Sans) : ligne de base à 0,992 em - 3 sous
     * le point de dessin, haut des capitales (700/1000 de l'em) à 0,292 em - 3. Police de secours cyrillique (Onest :
     * ascendante 970, descendante -305, capitales 707) : haut des capitales à 0,2685 em - 3.
     */
    private static final float BASE = 3f;

    /**
     * Une police des caisses : sa ressource, la taille de son em en unités d'interface (celle de la maquette ; le fichier
     * .json porte em x 1,302) et l'espacement des lettres (capitales espacées si > 0).
     */
    public record P(ResourceLocation police, float em, float esp) {
        /** Hauteur des capitales de DM Sans (700/1000 de l'em). */
        public float cap() {
            return em * 0.7F;
        }

        /** Du point de dessin au haut des capitales du texte (celles de sa première lettre : DM Sans ou Onest). */
        public float hautCap(String texte) {
            return (cyrillique(texte) ? em * 0.2685F : em * 0.292F) - BASE;
        }
    }

    public static P p(String police, float em, float esp) {
        return new P(new ResourceLocation("heralizcrates", police), em, esp);
    }

    private static boolean cyrillique(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC;
        }
        return false;
    }

    public static int a(int rgb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        return Ui.mix(a, b, Math.max(0, Math.min(1, t)));
    }

    // ------------------------------------------------------------------ formes
    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, int argb) {
        vc.vertex(m, x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int tl, int bl, int br, int tr) {
        v(vc, m, x0, y0, tl);
        v(vc, m, x0, y1, bl);
        v(vc, m, x1, y1, br);
        v(vc, m, x1, y0, tr);
    }

    private static VertexConsumer buf(GuiGraphics g) {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || argb >>> 24 == 0) return;
        quad(buf(g), g.pose().last().pose(), x0, y0, x1, y1, argb, argb, argb, argb);
        g.flush();
    }

    /** Dégradé de gauche à droite. */
    public static void gradH(GuiGraphics g, float x0, float y0, float x1, float y1, int gauche, int droite) {
        if (x1 <= x0 || y1 <= y0 || (gauche >>> 24 == 0 && droite >>> 24 == 0)) return;
        quad(buf(g), g.pose().last().pose(), x0, y0, x1, y1, gauche, gauche, droite, droite);
        g.flush();
    }

    /** Dégradé de haut en bas. */
    public static void gradV(GuiGraphics g, float x0, float y0, float x1, float y1, int haut, int bas) {
        if (x1 <= x0 || y1 <= y0 || (haut >>> 24 == 0 && bas >>> 24 == 0)) return;
        quad(buf(g), g.pose().last().pose(), x0, y0, x1, y1, haut, bas, bas, haut);
        g.flush();
    }

    /** Bord intérieur d'épaisseur e. */
    public static void bord(GuiGraphics g, float x, float y, float w, float h, float e, int argb) {
        if (argb >>> 24 == 0 || w <= 0 || h <= 0) return;
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        quad(vc, m, x, y, x + w, y + e, argb, argb, argb, argb);
        quad(vc, m, x, y + h - e, x + w, y + h, argb, argb, argb, argb);
        quad(vc, m, x, y + e, x + e, y + h - e, argb, argb, argb, argb);
        quad(vc, m, x + w - e, y + e, x + w, y + h - e, argb, argb, argb, argb);
        g.flush();
    }

    /** Anneau (rIn = 0 : disque). */
    public static void anneau(GuiGraphics g, float cx, float cy, float rIn, float rOut, int argb) {
        if (argb >>> 24 == 0 || rOut <= 0) return;
        int n = Math.max(24, (int) Math.ceil(64 * Math.max(1, rOut / 8)));
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        float s0 = 0, c0 = 1;
        for (int i = 1; i <= n; i++) {
            double t1 = i * 2 * Math.PI / n;
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            v(vc, m, cx + rOut * s0, cy - rOut * c0, argb);
            v(vc, m, cx + rIn * s0, cy - rIn * c0, argb);
            v(vc, m, cx + rIn * s1, cy - rIn * c1, argb);
            v(vc, m, cx + rOut * s1, cy - rOut * c1, argb);
            s0 = s1;
            c0 = c1;
        }
        g.flush();
    }

    public static void disque(GuiGraphics g, float cx, float cy, float r, int argb) {
        anneau(g, cx, cy, 0, r, argb);
    }

    /** Lueur elliptique : la couleur au centre, rien au bord. */
    public static void lueur(GuiGraphics g, float cx, float cy, float rx, float ry, int argb) {
        if (argb >>> 24 == 0 || rx <= 0 || ry <= 0) return;
        int bord = argb & 0xFFFFFF;
        int n = 48;
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < n; i++) {
            double t0 = i * 2 * Math.PI / n, t1 = (i + 1) * 2 * Math.PI / n;
            v(vc, m, cx + rx * (float) Math.sin(t0), cy - ry * (float) Math.cos(t0), bord);
            v(vc, m, cx, cy, argb);
            v(vc, m, cx, cy, argb);
            v(vc, m, cx + rx * (float) Math.sin(t1), cy - ry * (float) Math.cos(t1), bord);
        }
        g.flush();
    }

    /** Trait droit d'épaisseur e. */
    public static void trait(GuiGraphics g, float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4F || argb >>> 24 == 0) return;
        float nx = -dy / l * e / 2, ny = dx / l * e / 2;
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        v(vc, m, x0 - nx, y0 - ny, argb);
        v(vc, m, x0 + nx, y0 + ny, argb);
        v(vc, m, x1 + nx, y1 + ny, argb);
        v(vc, m, x1 - nx, y1 - ny, argb);
        g.flush();
    }

    /** Repère de la roue : triangle de base (cx - t, y) - (cx + t, y), pointe en bas (bas) ou en haut, de hauteur 1,1 t. */
    public static void repere(GuiGraphics g, float cx, float y, float t, boolean bas, int argb) {
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        // même sens de rotation que les quads de fill (gauche, pointe, droite vers le bas ; droite, pointe, gauche vers le haut)
        if (bas) {
            v(vc, m, cx - t, y, argb);
            v(vc, m, cx, y + t * 1.1F, argb);
            v(vc, m, cx + t, y, argb);
            v(vc, m, cx + t, y, argb);
        } else {
            v(vc, m, cx + t, y, argb);
            v(vc, m, cx, y - t * 1.1F, argb);
            v(vc, m, cx - t, y, argb);
            v(vc, m, cx - t, y, argb);
        }
        g.flush();
    }

    // ------------------------------------------------------------------ textes
    private static String forme(String s, P p) {
        return p.esp() > 0 ? s.toUpperCase(Locale.ROOT) : s;
    }

    private static Component lit(String s, P p) {
        return Ui.lit(s, p.police());
    }

    /** Largeur exacte (en flottant) du texte dans la police. */
    public static float largeur(Font f, String s, P p) {
        s = forme(s, p);
        if (p.esp() <= 0) return f.getSplitter().stringWidth(lit(s, p));
        float w = 0;
        int n = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            w += f.getSplitter().stringWidth(lit(new String(Character.toChars(s.codePointAt(i))), p));
            n++;
        }
        return w + p.esp() * Math.max(0, n - 1);
    }

    /** Texte coupé avec « … » s'il dépasse maxw (secours : l'aperçu vérifie que ça n'arrive pas ; maxw <= 0 : jamais). */
    public static String tenir(Font f, String s, P p, float maxw) {
        if (maxw <= 0 || largeur(f, s, p) <= maxw + 0.05F) return s;
        String r = s;
        while (r.length() > 1 && largeur(f, r + "…", p) > maxw) r = r.substring(0, r.length() - 1);
        return r.trim() + "…";
    }

    /** Texte dont le haut des capitales est en « haut » ; rend sa largeur. */
    public static float texte(GuiGraphics g, Font f, String s, float x, float haut, P p, int argb) {
        s = forme(s, p);
        // Minecraft dessine opaque un texte d'opacité presque nulle : rien à dessiner
        if ((argb & 0xFC000000) == 0) return largeur(f, s, p);
        float y = haut - p.hautCap(s);
        if (p.esp() <= 0) {
            g.pose().pushPose();
            Ui.placer(g, x, y);
            g.drawString(f, lit(s, p), 0, 0, argb, false);
            g.pose().popPose();
            return largeur(f, s, p);
        }
        float cx = x;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            Component c = lit(new String(Character.toChars(s.codePointAt(i))), p);
            g.pose().pushPose();
            Ui.placer(g, cx, y);
            g.drawString(f, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += f.getSplitter().stringWidth(c) + p.esp();
        }
        return cx - p.esp() - x;
    }

    /** Aligné à gauche, haut des capitales en « haut », coupé à maxw. */
    public static float t(GuiGraphics g, Font f, String s, float x, float haut, P p, int argb, float maxw) {
        return texte(g, f, tenir(f, s, p, maxw), x, haut, p, argb);
    }

    /** Aligné à gauche, milieu des capitales sur cy. */
    public static float m(GuiGraphics g, Font f, String s, float x, float cy, P p, int argb, float maxw) {
        return texte(g, f, tenir(f, s, p, maxw), x, cy - p.cap() / 2, p, argb);
    }

    /** Centré sur (cx, cy). */
    public static float c(GuiGraphics g, Font f, String s, float cx, float cy, P p, int argb, float maxw) {
        s = tenir(f, s, p, maxw);
        float w = largeur(f, s, p);
        return texte(g, f, s, cx - w / 2, cy - p.cap() / 2, p, argb);
    }

    /** Aligné à droite sur xd. */
    public static float d(GuiGraphics g, Font f, String s, float xd, float cy, P p, int argb, float maxw) {
        s = tenir(f, s, p, maxw);
        float w = largeur(f, s, p);
        return texte(g, f, s, xd - w, cy - p.cap() / 2, p, argb);
    }

    /** Découpe en lignes de largeur maxw au plus (par mots ; un mot trop long seul est coupé, comme dans l'aperçu). */
    public static List<String> lignes(Font f, String s, P p, float maxw) {
        List<String> mots = new ArrayList<>();
        for (String mot : s.split(" ")) {
            while (mot.length() > 1 && largeur(f, mot, p) > maxw) {
                int k = mot.length() - 1;
                while (k > 1 && largeur(f, mot.substring(0, k), p) > maxw) k--;
                mots.add(mot.substring(0, k));
                mot = mot.substring(k);
            }
            mots.add(mot);
        }
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String mot : mots) {
            String essai = cur.isEmpty() ? mot : cur + " " + mot;
            if (!cur.isEmpty() && largeur(f, essai, p) > maxw) {
                out.add(cur);
                cur = mot;
            } else {
                cur = essai;
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
