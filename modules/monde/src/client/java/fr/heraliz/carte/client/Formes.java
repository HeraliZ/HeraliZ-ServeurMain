package fr.heraliz.carte.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Formes de la carte en coordonnées d'interface flottantes (cercles des zones, anneaux, pointillés, arcs de capture,
 * hachures, cône de vue, traits fins) : tout un lot de triangles colorés dessiné en un seul appel entre {@link #debut}
 * et {@link #fin}. Les dessins du GuiGraphics en attente sont envoyés avant (ordre du peintre respecté).
 */
final class Formes {
    private Formes() {
    }

    private static BufferBuilder buf;
    private static Matrix4f m;
    private static boolean ouvert;

    static void debut(GuiGraphics g) {
        g.flush();
        m = g.pose().last().pose();
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        ouvert = true;
    }

    static void fin() {
        if (!ouvert) return;
        ouvert = false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void v(float x, float y, int argb) {
        buf.vertex(m, x, y, 0).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }

    static void tri(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        if (!ouvert || ((c0 | c1 | c2) >>> 24) == 0) return;
        v(x0, y0, c0);
        v(x1, y1, c1);
        v(x2, y2, c2);
    }

    /** Quadrilatère (a, b, c, d dans l'ordre du tour). */
    static void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        tri(ax, ay, argb, bx, by, argb, cx, cy, argb);
        tri(ax, ay, argb, cx, cy, argb, dx, dy, argb);
    }

    static void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    /** Rectangle en dégradé : couleurs des coins haut-gauche, haut-droit, bas-droit, bas-gauche. */
    static void degrade(float x0, float y0, float x1, float y1, int hg, int hd, int bd, int bg) {
        tri(x0, y0, hg, x1, y0, hd, x1, y1, bd);
        tri(x0, y0, hg, x1, y1, bd, x0, y1, bg);
    }

    /** Trait d'épaisseur e entre deux points. */
    static void trait(float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * e / 2, ny = dx / l * e / 2;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    /** Trait en tirets (longueur des tirets et des vides en unités d'interface). */
    static void traitTirets(float x0, float y0, float x1, float y1, float e, float tiret, float vide, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float ux = dx / l, uy = dy / l;
        for (float t = 0; t < l; t += tiret + vide) {
            float t1 = Math.min(l, t + tiret);
            trait(x0 + ux * t, y0 + uy * t, x0 + ux * t1, y0 + uy * t1, e, argb);
        }
    }

    static int segments(float r) {
        return Math.max(20, Math.min(720, (int) (r * 1.5f)));
    }

    static void disque(float cx, float cy, float r, int argb) {
        if (r <= 0) return;
        int n = segments(r);
        float px = cx + r, py = cy;
        for (int i = 1; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            tri(cx, cy, argb, px, py, argb, x, y, argb);
            px = x;
            py = y;
        }
    }

    /** Anneau complet de rayon moyen r et d'épaisseur e. */
    static void anneau(float cx, float cy, float r, float e, int argb) {
        arc(cx, cy, r, e, 0, (float) (Math.PI * 2), argb);
    }

    /** Morceau d'anneau de l'angle a0 à a1 (radians, 0 = à droite, sens horaire à l'écran). */
    static void arc(float cx, float cy, float r, float e, float a0, float a1, int argb) {
        if (r <= 0 || a1 <= a0) return;
        float ri = Math.max(0, r - e / 2), re = r + e / 2;
        int n = Math.max(2, (int) (segments(re) * (a1 - a0) / (Math.PI * 2)) + 1);
        float pc = (float) Math.cos(a0), ps = (float) Math.sin(a0);
        for (int i = 1; i <= n; i++) {
            float a = a0 + (a1 - a0) * i / n;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            quad(cx + pc * ri, cy + ps * ri, cx + pc * re, cy + ps * re, cx + c * re, cy + s * re, cx + c * ri, cy + s * ri, argb);
            pc = c;
            ps = s;
        }
    }

    /** Anneau en pointillés : tirets de longueur {@code tiret} séparés de {@code vide} (unités d'interface, sur le cercle). */
    static void anneauPointille(float cx, float cy, float r, float e, float tiret, float vide, int argb) {
        if (r <= 0) return;
        double tour = Math.PI * 2 * r;
        int n = Math.max(4, (int) Math.round(tour / (tiret + vide)));
        float pas = (float) (Math.PI * 2 / n), part = pas * tiret / (tiret + vide);
        for (int i = 0; i < n; i++) arc(cx, cy, r, e, i * pas, i * pas + part, argb);
    }

    /** Hachures à 45° dans le disque (bandes de largeur {@code bande}, une sur deux de chaque couleur). */
    static void hachures(float cx, float cy, float r, float bande, int c1, int c2) {
        if (r <= 0 || bande <= 0) return;
        float ux = 0.70710677f, uy = 0.70710677f, nx = -uy, ny = ux; // direction des bandes, normale
        int k = 0;
        for (float d = -r; d < r; d += bande, k++) {
            float d1 = Math.min(r, d + bande);
            float h0 = (float) Math.sqrt(Math.max(0, r * r - d * d)), h1 = (float) Math.sqrt(Math.max(0, r * r - d1 * d1));
            float x0 = cx + nx * d, y0 = cy + ny * d, x1 = cx + nx * d1, y1 = cy + ny * d1;
            quad(x0 - ux * h0, y0 - uy * h0, x0 + ux * h0, y0 + uy * h0, x1 + ux * h1, y1 + uy * h1, x1 - ux * h1, y1 - uy * h1,
                    (k & 1) == 0 ? c1 : c2);
        }
    }

    /**
     * Cône de vue : secteur d'ouverture {@code ouverture} (radians) autour de la direction {@code angle} (0 = haut, sens
     * horaire), couleur pleine au centre qui s'efface au bord.
     */
    static void cone(float cx, float cy, float r, float angle, float ouverture, int centre) {
        int bord = centre & 0xFFFFFF;
        int n = 24;
        float a0 = angle - ouverture / 2;
        float px = cx + (float) Math.sin(a0) * r, py = cy - (float) Math.cos(a0) * r;
        for (int i = 1; i <= n; i++) {
            float a = a0 + ouverture * i / n;
            float x = cx + (float) Math.sin(a) * r, y = cy - (float) Math.cos(a) * r;
            tri(cx, cy, centre, px, py, bord, x, y, bord);
            px = x;
            py = y;
        }
    }

    /** Contour d'un rectangle en tirets fins (bord du monde). */
    static void cadreTirets(float x0, float y0, float x1, float y1, float e, float tiret, float vide, int argb) {
        traitTirets(x0, y0, x1, y0, e, tiret, vide, argb);
        traitTirets(x1, y0, x1, y1, e, tiret, vide, argb);
        traitTirets(x1, y1, x0, y1, e, tiret, vide, argb);
        traitTirets(x0, y1, x0, y0, e, tiret, vide, argb);
    }

    static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f + 0.5f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
