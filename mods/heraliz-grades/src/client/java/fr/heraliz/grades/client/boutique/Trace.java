package fr.heraliz.grades.client.boutique;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.grades.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dessin de la boutique (maquette validée le 23/10) : formes à coordonnées libres comme le créateur de personnage
 * (CreatorUi de CaraCrea : rectangles, dégradés, disques, traits, lueurs), images en filtrage lissé (jamais pixelisées) et
 * textes DM Sans tracés à leur vraie taille (une police par taille, jamais agrandie), posés sur un point entier de l'écran.
 */
public final class Trace {
    private Trace() {
    }

    /**
     * Une police de la boutique : sa ressource, la taille de son em en unités d'interface (celle de la maquette ; le
     * fichier .json porte em × 1,302, car Minecraft compte la hauteur ascendante + descendante de DM Sans) et
     * l'espacement des lettres (capitales espacées si > 0).
     */
    public record P(ResourceLocation police, float em, float esp) {
        /** Hauteur des capitales de DM Sans (700/1000 de l'em). */
        public float cap() {
            return em * 0.7F;
        }

        /** Du point de dessin au haut des capitales (ligne de base à 0,992 em, capitales à 0,7 em). */
        public float hautCap() {
            return em * 0.292F;
        }
    }

    public static P p(String police, float em, float esp) {
        return new P(new ResourceLocation("heraliz_grades", police), em, esp);
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
        if (argb >>> 24 == 0) return;
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

    /** Coche dans un carré de côté s (coin haut gauche x, y). */
    public static void coche(GuiGraphics g, float x, float y, float s, float e, int argb) {
        float ax = x, ay = y + s * 0.55F, bx = x + s * 0.38F, by = y + s * 0.9F, cx = x + s, cy = y + s * 0.12F;
        trait(g, ax, ay, bx, by, e, argb);
        trait(g, bx, by, cx, cy, e, argb);
        disque(g, bx, by, e / 2, argb);
    }

    /** Losange (rang des grades). */
    public static void losange(GuiGraphics g, float cx, float cy, float r, int argb) {
        VertexConsumer vc = buf(g);
        Matrix4f m = g.pose().last().pose();
        v(vc, m, cx, cy - r, argb);
        v(vc, m, cx - r, cy, argb);
        v(vc, m, cx, cy + r, argb);
        v(vc, m, cx + r, cy, argb);
        g.flush();
    }

    // ------------------------------------------------------------------ images
    private static final Map<ResourceLocation, int[]> TAILLES = new HashMap<>();

    /** Largeur et hauteur de l'image (lues une fois dans le fichier ; 1 x 1 si illisible). */
    public static int[] taille(ResourceLocation rl) {
        return TAILLES.computeIfAbsent(rl, r -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(r); NativeImage im = NativeImage.read(in)) {
                // filtrage lissé : l'image est réduite à l'écran, jamais pixelisée
                Minecraft.getInstance().getTextureManager().getTexture(r).setFilter(true, false);
                return new int[]{im.getWidth(), im.getHeight()};
            } catch (Exception ex) {
                return new int[]{1, 1};
            }
        });
    }

    /** Image entière dans le rectangle (x, y, w, h), opacité alpha (0 à 1). */
    public static void image(GuiGraphics g, ResourceLocation rl, float x, float y, float w, float h, float alpha) {
        taille(rl);
        g.flush();
        RenderSystem.setShaderTexture(0, rl);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        bb.vertex(m, x, y, 0).uv(0, 0).color(255, 255, 255, al).endVertex();
        bb.vertex(m, x, y + h, 0).uv(0, 1).color(255, 255, 255, al).endVertex();
        bb.vertex(m, x + w, y + h, 0).uv(1, 1).color(255, 255, 255, al).endVertex();
        bb.vertex(m, x + w, y, 0).uv(1, 0).color(255, 255, 255, al).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();
    }

    /** Image ajustée dans la boîte (proportions gardées), posée en bas (bas) ou au milieu. */
    public static void ajuster(GuiGraphics g, ResourceLocation rl, float x, float y, float w, float h, boolean bas, float alpha) {
        int[] t = taille(rl);
        float s = Math.min(w / t[0], h / t[1]);
        float iw = t[0] * s, ih = t[1] * s;
        image(g, rl, x + (w - iw) / 2, bas ? y + h - ih : y + (h - ih) / 2, iw, ih, alpha);
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

    /** Texte coupé avec « … » s'il dépasse maxw (secours : l'aperçu vérifie que ça n'arrive pas). */
    public static String tenir(Font f, String s, P p, float maxw) {
        if (maxw <= 0 || largeur(f, s, p) <= maxw + 0.05F) return s;
        String r = s;
        while (r.length() > 1 && largeur(f, r + "…", p) > maxw) r = r.substring(0, r.length() - 1);
        return r.trim() + "…";
    }

    /** Texte dont le haut des capitales est en « haut » ; rend sa largeur. */
    public static float texte(GuiGraphics g, Font f, String s, float x, float haut, P p, int argb) {
        s = forme(s, p);
        float y = haut - p.hautCap();
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

    /** Découpe en lignes de largeur maxw au plus (par mots). */
    public static List<String> lignes(Font f, String s, P p, float maxw) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String mot : s.split(" ")) {
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
