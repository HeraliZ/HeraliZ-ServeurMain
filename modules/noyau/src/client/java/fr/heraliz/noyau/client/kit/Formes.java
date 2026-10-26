package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Formes des HUD en coordonnées d'interface flottantes (refonte du 25/10, docs/HUD_REFONTE.md) : rectangles, dégradés,
 * filets, verre, arcs et disques aux bords adoucis d'un pixel d'écran, segments, coche. Tout est ENVOYÉ au tampon
 * ({@link #tampon}) sans le vider : un HUD envoie toutes ses formes puis vide une seule fois ({@code g.flush()}), avant
 * ses textes. Rien n'est alloué.
 * <p>Les quadrilatères sont remis dans le sens que le jeu affiche (celui de {@code GuiGraphics.fill} : haut-gauche,
 * bas-gauche, bas-droit, haut-droit), quel que soit l'ordre donné.
 */
public final class Formes {
    private Formes() {
    }

    /** Segments d'un tour complet pour un rayon de 12 (plus pour un grand rayon). */
    private static final int SEGMENTS_TOUR = 72;

    /** Tampon des formes (rendu « gui » du jeu, quadrilatères colorés). */
    public static VertexConsumer tampon(GuiGraphics g) {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    /** Un pixel d'écran en unités d'interface (1 / échelle de l'interface). */
    public static float pixel() {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (1.0 / Math.max(1.0, s));
    }

    /** Valeur posée sur un pixel entier de l'écran (HUD : pas de translation en x / y). */
    public static float caler(float v) {
        double s = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        return (float) (Math.round(v * s) / s);
    }

    // ------------------------------------------------------------------ quadrilatères
    /** Quadrilatère, une couleur ARGB par coin, envoyé dans le sens que le jeu affiche. */
    public static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1,
                            float x2, float y2, int c2, float x3, float y3, int c3) {
        float aire = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (aire <= 0f) {
            sommet(vc, m, x0, y0, c0);
            sommet(vc, m, x1, y1, c1);
            sommet(vc, m, x2, y2, c2);
            sommet(vc, m, x3, y3, c3);
        } else {
            sommet(vc, m, x0, y0, c0);
            sommet(vc, m, x3, y3, c3);
            sommet(vc, m, x2, y2, c2);
            sommet(vc, m, x1, y1, c1);
        }
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, float x, float y, int c) {
        vc.vertex(m, x, y, 0f).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    /** Rectangle plein. */
    public static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) return;
        quad(vc, m, x0, y0, argb, x0, y1, argb, x1, y1, argb, x1, y0, argb);
    }

    /** Rectangle en dégradé horizontal (gauche vers droite). */
    public static void degradeH(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int gauche, int droite) {
        if (x1 <= x0 || y1 <= y0) return;
        quad(vc, m, x0, y0, gauche, x0, y1, gauche, x1, y1, droite, x1, y0, droite);
    }

    /** Rectangle en dégradé vertical (haut vers bas). */
    public static void degradeV(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int haut, int bas) {
        if (x1 <= x0 || y1 <= y0) return;
        quad(vc, m, x0, y0, haut, x0, y1, bas, x1, y1, bas, x1, y0, haut);
    }

    /**
     * Voile aux bords fondus (fond doux derrière un texte posé sur le monde, sans cadre) : couleur {@code rgb} à
     * {@code alpha} (0..255) au milieu, transparent sur les bords, fondu sur {@code fx} unités à gauche et à droite et
     * {@code fy} en haut et en bas.
     */
    public static void voile(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float fx, float fy, int rgb, int alpha) {
        if (x1 <= x0 || y1 <= y0 || alpha <= 0) return;
        float xa = x0 + Math.min(fx, (x1 - x0) / 2f), xb = x1 - Math.min(fx, (x1 - x0) / 2f);
        float ya = y0 + Math.min(fy, (y1 - y0) / 2f), yb = y1 - Math.min(fy, (y1 - y0) / 2f);
        int p = Palette.argb(rgb, alpha), v = rgb & 0xFFFFFF;
        // 3 x 3 cases : opaque au milieu, transparent sur le pourtour
        quad(vc, m, x0, y0, v, x0, ya, v, xa, ya, p, xa, y0, v);
        quad(vc, m, xa, y0, v, xa, ya, p, xb, ya, p, xb, y0, v);
        quad(vc, m, xb, y0, v, xb, ya, p, x1, ya, v, x1, y0, v);
        quad(vc, m, x0, ya, v, x0, yb, v, xa, yb, p, xa, ya, p);
        quad(vc, m, xa, ya, p, xa, yb, p, xb, yb, p, xb, ya, p);
        quad(vc, m, xb, ya, p, xb, yb, p, x1, yb, v, x1, ya, v);
        quad(vc, m, x0, yb, v, x0, y1, v, xa, y1, v, xa, yb, p);
        quad(vc, m, xa, yb, p, xa, y1, v, xb, y1, v, xb, yb, p);
        quad(vc, m, xb, yb, p, xb, y1, v, x1, y1, v, x1, yb, v);
    }

    /** Filet horizontal de la couleur {@code rgb}, plein au milieu, qui s'efface vers les deux bouts. */
    public static void filet(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int rgb, int alpha) {
        float mx = x + w / 2f;
        int plein = Palette.argb(rgb, alpha), vide = rgb & 0xFFFFFF;
        degradeH(vc, m, x, y, mx, y + h, vide, plein);
        degradeH(vc, m, mx, y, x + w, y + h, plein, vide);
    }

    /** Contour d'épaisseur {@code e}, à l'intérieur du rectangle (x, y, w, h). */
    public static void bord(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, float e, int argb) {
        rect(vc, m, x, y, x + w, y + e, argb);
        rect(vc, m, x, y + h - e, x + w, y + h, argb);
        rect(vc, m, x, y + e, x + e, y + h - e, argb);
        rect(vc, m, x + w - e, y + e, x + w, y + h - e, argb);
    }

    /**
     * Panneau « verre » des écrans de clan, en version HUD : fond {@link Palette#VERRE} à {@code alpha} (0..255), bord
     * d'un pixel d'écran blanc à 10 %, liseré clair d'un pixel en haut ; {@code opacite} (0..1) : fondu du HUD.
     */
    public static void verre(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int alpha, float opacite) {
        float px = pixel();
        rect(vc, m, x, y, x + w, y + h, Palette.argb(Palette.VERRE, Math.round(alpha * opacite)));
        bord(vc, m, x, y, w, h, px, Palette.argb(0xFFFFFF, Math.round(Palette.BORD_ALPHA * opacite)));
        rect(vc, m, x + px, y + px, x + w - px, y + 2 * px, Palette.argb(0xFFFFFF, Math.round(Palette.LISERE_ALPHA * opacite)));
    }

    // ------------------------------------------------------------------ formes penchées (style barre DayZ)
    /** Pente d'un angle en degrés (tan) : 12° pour les cases de la barre d'objets DayZ (HotbarOverlay). */
    public static float pente(float degres) {
        return (float) Math.tan(Math.toRadians(degres));
    }

    /**
     * Rectangle (x0..x1, y0..y1) penché comme de l'italique, cisaillé autour de la ligne {@code yRef}
     * (x + k (yRef - y), comme HotbarOverlay) ; côtés penchés adoucis sur {@code flou} unités. Plusieurs formes qui
     * partagent {@code yRef} restent alignées (contour, segments).
     */
    public static void parallelogramme(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float k, float yRef,
                                       int argb, float flou) {
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) return;
        float dh = k * (yRef - y0), db = k * (yRef - y1);
        float h = Math.min(flou / 2f, (x1 - x0) / 2f);
        int vide = argb & 0xFFFFFF;
        quad(vc, m, x0 + h + dh, y0, argb, x0 + h + db, y1, argb, x1 - h + db, y1, argb, x1 - h + dh, y0, argb);
        if (h > 0f) {
            quad(vc, m, x0 - h + dh, y0, vide, x0 - h + db, y1, vide, x0 + h + db, y1, argb, x0 + h + dh, y0, argb);
            quad(vc, m, x1 - h + dh, y0, argb, x1 - h + db, y1, argb, x1 + h + db, y1, vide, x1 + h + dh, y0, vide);
        }
    }

    /** Parallélogramme cisaillé autour de sa propre ligne du milieu. */
    public static void parallelogramme(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float k, int argb, float flou) {
        parallelogramme(vc, m, x0, y0, x1, y1, k, (y0 + y1) / 2f, argb, flou);
    }

    /** Contour penché d'épaisseur {@code e}, à l'extérieur du parallélogramme (x0..x1, y0..y1) de même pente. */
    public static void bordPenche(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float k, float e, int argb) {
        float r = (y0 + y1) / 2f;
        parallelogramme(vc, m, x0 - e, y0 - e, x1 + e, y0, k, r, argb, 0f);
        parallelogramme(vc, m, x0 - e, y1, x1 + e, y1 + e, k, r, argb, 0f);
        parallelogramme(vc, m, x0 - e, y0, x0, y1, k, r, argb, 0f);
        parallelogramme(vc, m, x1, y0, x1 + e, y1, k, r, argb, 0f);
    }

    /**
     * Verre penché (comme les cases de la barre DayZ) : fond {@link Palette#VERRE} à {@code alpha}, bord d'un pixel d'écran
     * blanc à 10 %, liseré clair en haut ; {@code opacite} : fondu du HUD.
     */
    public static void verrePenche(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float k, int alpha, float opacite) {
        float px = pixel(), r = (y0 + y1) / 2f;
        parallelogramme(vc, m, x0, y0, x1, y1, k, r, Palette.argb(Palette.VERRE, Math.round(alpha * opacite)), px);
        bordPenche(vc, m, x0, y0, x1, y1, k, px, Palette.argb(0xFFFFFF, Math.round(Palette.BORD_ALPHA * opacite)));
        parallelogramme(vc, m, x0 + px, y0, x1 - px, y0 + px, k, r, Palette.argb(0xFFFFFF, Math.round(Palette.LISERE_ALPHA * opacite)), 0f);
    }

    /**
     * Barre segmentée penchée (style barre DayZ) : {@code n} segments sur la largeur {@code w}, séparés de {@code ecart} ;
     * remplie de la couleur {@code plein} jusqu'à {@code part} (0..1, le segment en cours rempli en partie), piste
     * {@code piste} ensuite ; les segments au-delà de {@code partMax} (part perdue aux réparations) prennent {@code perdu}.
     */
    public static void segments(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, float k, int n, float ecart,
                                float part, int plein, int piste, float partMax, int perdu, float flou) {
        if (n <= 0) return;
        float sw = (w - (n - 1) * ecart) / n, r = y + h / 2f;
        int premierPerdu = (int) Math.ceil(partMax * n - 1e-4f);
        for (int i = 0; i < n; i++) {
            float sx = x + i * (sw + ecart);
            float f = Math.max(0f, Math.min(1f, part * n - i));
            if (f > 0f) parallelogramme(vc, m, sx, y, sx + sw * f, y + h, k, r, plein, flou);
            if (f < 1f) parallelogramme(vc, m, sx + sw * f, y, sx + sw, y + h, k, r, i >= premierPerdu ? perdu : piste, flou);
        }
    }

    // ------------------------------------------------------------------ arcs, disques, halos
    /**
     * Anneau centré en (cx, cy), du rayon {@code rInt} au rayon {@code rExt}, de la fraction {@code de} à la fraction
     * {@code a} d'un tour (0 = midi, sens des aiguilles d'une montre). Bords intérieur et extérieur adoucis sur
     * {@code flou} unités (un pixel d'écran : {@link #pixel}) ; le bord adouci est centré sur le rayon demandé.
     */
    public static void arc(VertexConsumer vc, Matrix4f m, float cx, float cy, float rInt, float rExt, float de, float a,
                           int argb, float flou) {
        float d = Math.max(0f, Math.min(1f, de)), f = Math.max(d, Math.min(1f, a));
        if (f - d <= 0.0005f || (argb >>> 24) == 0 || rExt <= rInt) return;
        int n = Math.max(2, (int) Math.ceil(SEGMENTS_TOUR * (f - d) * Math.max(1f, rExt / 12f)));
        float h = Math.min(flou / 2f, (rExt - rInt) / 2f);
        float r0 = rInt > 0f ? rInt + h : 0f, r1 = rExt - h;
        int vide = argb & 0xFFFFFF;
        double t0 = d * 2.0 * Math.PI;
        float s0 = (float) Math.sin(t0), c0 = (float) Math.cos(t0);
        for (int i = 1; i <= n; i++) {
            double t1 = (d + (f - d) * i / (double) n) * 2.0 * Math.PI;
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            tranche(vc, m, cx, cy, s0, c0, s1, c1, r0, r1, argb, argb);
            if (flou > 0f) {
                tranche(vc, m, cx, cy, s0, c0, s1, c1, r1, r1 + 2f * h + 0.0001f, argb, vide);
                if (rInt > 0f) tranche(vc, m, cx, cy, s0, c0, s1, c1, Math.max(0f, r0 - 2f * h - 0.0001f), r0, vide, argb);
            }
            s0 = s1;
            c0 = c1;
        }
    }

    private static void tranche(VertexConsumer vc, Matrix4f m, float cx, float cy, float s0, float c0, float s1, float c1,
                                float rIn, float rOut, int cIn, int cOut) {
        if (rOut <= rIn) return;
        quad(vc, m, cx + rOut * s0, cy - rOut * c0, cOut, cx + rIn * s0, cy - rIn * c0, cIn,
                cx + rIn * s1, cy - rIn * c1, cIn, cx + rOut * s1, cy - rOut * c1, cOut);
    }

    /** Disque plein au bord adouci. */
    public static void disque(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int argb, float flou) {
        arc(vc, m, cx, cy, 0f, r, 0f, 1f, argb, flou);
    }

    /** Halo : disque de la couleur au centre, qui s'efface jusqu'au rayon {@code r}. */
    public static void halo(VertexConsumer vc, Matrix4f m, float cx, float cy, float r, int argb) {
        if (r <= 0f || (argb >>> 24) == 0) return;
        int vide = argb & 0xFFFFFF;
        int n = 32;
        float s0 = 0f, c0 = 1f;
        for (int i = 1; i <= n; i++) {
            double t1 = i * 2.0 * Math.PI / n;
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            quad(vc, m, cx + r * s0, cy - r * c0, vide, cx, cy, argb, cx, cy, argb, cx + r * s1, cy - r * c1, vide);
            s0 = s1;
            c0 = c1;
        }
    }

    // ------------------------------------------------------------------ traits
    /** Segment épais de (x0, y0) à (x1, y1), prolongé de {@code e / 2} à chaque bout (jonctions sans trou). */
    public static void segment(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f || (argb >>> 24) == 0) return;
        float ux = dx / l * e / 2f, uy = dy / l * e / 2f;
        float ax = x0 - ux, ay = y0 - uy, bx = x1 + ux, by = y1 + uy;
        quad(vc, m, ax - uy, ay + ux, argb, ax + uy, ay - ux, argb, bx + uy, by - ux, argb, bx - uy, by + ux, argb);
    }

    /**
     * Coche de réussite centrée en (cx, cy), de taille {@code t} et d'épaisseur {@code e}, tracée jusqu'à la fraction
     * {@code p} (0..1) de sa longueur (apparition animée). Mêmes points que l'aperçu (apercu_hud.py, coche).
     */
    public static void coche(VertexConsumer vc, Matrix4f m, float cx, float cy, float t, float e, float p, int argb) {
        float ax = cx - t * 0.5f, ay = cy + t * 0.02f;
        float bx = cx - t * 0.14f, by = cy + t * 0.36f;
        float qx = cx + t * 0.52f, qy = cy - t * 0.34f;
        float l1 = (float) Math.hypot(bx - ax, by - ay), l2 = (float) Math.hypot(qx - bx, qy - by);
        float l = Math.max(0f, Math.min(1f, p)) * (l1 + l2);
        if (l <= 0f) return;
        if (l <= l1) {
            segment(vc, m, ax, ay, ax + (bx - ax) * l / l1, ay + (by - ay) * l / l1, e, argb);
            return;
        }
        segment(vc, m, ax, ay, bx, by, e, argb);
        float k = (l - l1) / l2;
        segment(vc, m, bx, by, bx + (qx - bx) * k, by + (qy - by) * k, e, argb);
    }
}
