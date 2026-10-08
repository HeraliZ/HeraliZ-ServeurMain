package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Petite geometrie des rendus du decor : paves textures et quads, dessines directement dans un {@link VertexConsumer}
 * de type entite (position, couleur, uv, overlay, lumiere, normale). Coordonnees en blocs dans le repere courant de la
 * {@link PoseStack}. Aucune allocation : les UV sont des tableaux constants (fractions de la texture) prepares par
 * {@link #uv}.
 * <p>Ordre des faces pour un tableau d'UV de 24 valeurs : bas, haut, nord (-z), sud (+z), ouest (-x), est (+x) ;
 * un tableau de 4 valeurs sert pour toutes les faces.
 */
public final class Geo {
    private Geo() {
    }

    /** Rectangle de pixels (x, y, w, h) d'une texture (tw x th) en fractions {u0, v0, u1, v1}. */
    public static float[] uv(int x, int y, int w, int h, int tw, int th) {
        return new float[]{x / (float) tw, y / (float) th, (x + w) / (float) tw, (y + h) / (float) th};
    }

    /** Six rectangles (bas, haut, nord, sud, ouest, est) concatenes. */
    public static float[] faces(float[] bas, float[] haut, float[] nord, float[] sud, float[] ouest, float[] est) {
        float[] r = new float[24];
        float[][] t = {bas, haut, nord, sud, ouest, est};
        for (int i = 0; i < 6; i++) System.arraycopy(t[i], 0, r, i * 4, 4);
        return r;
    }

    /** Pave de (x0, y0, z0) a (x1, y1, z1). {@code argb} module la texture. */
    public static void pave(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float[] uv, int light, int overlay, int argb) {
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        boolean six = uv.length >= 24;
        // bas (y0)
        quad(m, n, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, uv, six ? 0 : 0, light, overlay, r, g, b, a);
        // haut (y1)
        quad(m, n, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, uv, six ? 4 : 0, light, overlay, r, g, b, a);
        // nord (z0)
        quad(m, n, vc, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, uv, six ? 8 : 0, light, overlay, r, g, b, a);
        // sud (z1)
        quad(m, n, vc, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, uv, six ? 12 : 0, light, overlay, r, g, b, a);
        // ouest (x0)
        quad(m, n, vc, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, uv, six ? 16 : 0, light, overlay, r, g, b, a);
        // est (x1)
        quad(m, n, vc, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, uv, six ? 20 : 0, light, overlay, r, g, b, a);
    }

    /** Une seule face d'un pave : 0 bas, 1 haut, 2 nord, 3 sud, 4 ouest, 5 est. */
    public static void face(PoseStack ps, VertexConsumer vc, int face, float x0, float y0, float z0, float x1, float y1, float z1,
                            float[] uv, int light, int overlay, int argb) {
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        switch (face) {
            case 0 -> quad(m, n, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, uv, 0, light, overlay, r, g, b, a);
            case 1 -> quad(m, n, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, uv, 0, light, overlay, r, g, b, a);
            case 2 -> quad(m, n, vc, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, uv, 0, light, overlay, r, g, b, a);
            case 3 -> quad(m, n, vc, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, uv, 0, light, overlay, r, g, b, a);
            case 4 -> quad(m, n, vc, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, uv, 0, light, overlay, r, g, b, a);
            default -> quad(m, n, vc, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, uv, 0, light, overlay, r, g, b, a);
        }
    }

    /**
     * Quad dans le plan XY (z = 0), de (x0, y0) a (x1, y1), face vers +z ; visible des deux cotes si le type de rendu ne
     * retire pas les faces arriere.
     */
    public static void plan(PoseStack ps, VertexConsumer vc, float x0, float y0, float x1, float y1, float[] uv, int light, int overlay, int argb) {
        PoseStack.Pose p = ps.last();
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        quad(p.pose(), p.normal(), vc, x0, y1, 0, x0, y0, 0, x1, y0, 0, x1, y1, 0, 0, 0, 1, uv, 0, light, overlay, r, g, b, a);
    }

    /**
     * Couleur pour un rendu additif ({@link ZbTypesRendu#lueur}) : seul le RGB compte, donc l'intensite {@code k}
     * (0..1) multiplie la couleur.
     */
    public static int additif(float k, int rgb) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void quad(Matrix4f m, Matrix3f n, VertexConsumer vc,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float[] uv, int o, int light, int overlay, int r, int g, int b, int a) {
        float u0 = uv[o], v0 = uv[o + 1], u1 = uv[o + 2], v1 = uv[o + 3];
        vc.vertex(m, ax, ay, az).color(r, g, b, a).uv(u0, v0).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).uv(u0, v1).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).uv(u1, v1).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).uv(u1, v0).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
