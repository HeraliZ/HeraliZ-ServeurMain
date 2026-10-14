package fr.heralisz.matchs.duels.client.bombe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Câble noir entre la pointe de l'ordinateur et la prise « DATA » de la bombe : courbe lisse (Catmull-Rom) passant par
 * des points de contrôle (sortie de la pointe, détour derrière l'ordinateur, pied de la bombe), jamais sous le sol,
 * dessinée en deux rubans croisés (lisible sous tous les angles), éclairée par le monde. Coordonnées relatives à
 * l'origine de la pile de poses (entité dessinée).
 */
final class Cable {
    private Cable() {
    }

    /** Subdivisions entre deux points de contrôle. */
    private static final int PAS = 8;
    private static final float RAYON = 0.018f;
    /** Gaine (deux teintes en alternance, comme une laisse) et manchon orange contre la pointe de l'ordinateur. */
    private static final int GAINE_A = 0x1B1C1F, GAINE_B = 0x26282C, MANCHON = 0xFF7A1A;

    /**
     * @param p    points de contrôle x, y, z à la suite (au moins 2) : du bout de l'ordinateur à la prise de la bombe
     * @param sol  hauteur du sol (le câble reste au-dessus)
     */
    static void dessiner(PoseStack ps, MultiBufferSource tampons, double[] p, double sol, int lumiere) {
        int n = p.length / 3;
        if (n < 2) return;
        VertexConsumer v = tampons.getBuffer(TypesRendu.COULEUR);
        Matrix4f m = ps.last().pose();
        double plancher = sol + RAYON + 0.004;
        double px = p[0], py = p[1], pz = p[2];
        int k = 0;
        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(0, i - 1), i2 = i + 1, i3 = Math.min(n - 1, i + 2);
            for (int s = 1; s <= PAS; s++) {
                double t = s / (double) PAS;
                double qx = catmull(p[i0 * 3], p[i * 3], p[i2 * 3], p[i3 * 3], t);
                double qy = Math.max(plancher, catmull(p[i0 * 3 + 1], p[i * 3 + 1], p[i2 * 3 + 1], p[i3 * 3 + 1], t));
                double qz = catmull(p[i0 * 3 + 2], p[i * 3 + 2], p[i2 * 3 + 2], p[i3 * 3 + 2], t);
                k++;
                int c = k <= 2 ? MANCHON : (k % 2 == 0 ? GAINE_A : GAINE_B);
                segment(v, m, px, py, pz, qx, qy, qz, c, lumiere);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static void segment(VertexConsumer v, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int rgb, int lumiere) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return;
        dx /= l;
        dy /= l;
        dz /= l;
        // largeur horizontale (perpendiculaire au câble et à la verticale), puis largeur « verticale » (perpendiculaire aux deux)
        double hx = -dz, hz = dx, hl = Math.sqrt(hx * hx + hz * hz);
        if (hl < 1e-4) {
            hx = 1;
            hz = 0;
            hl = 1;
        }
        hx = hx / hl * RAYON;
        hz = hz / hl * RAYON;
        double vx = dy * hz, vy = dz * hx - dx * hz, vz = -dy * hx;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl > 1e-6) {
            vx = vx / vl * RAYON;
            vy = vy / vl * RAYON;
            vz = vz / vl * RAYON;
        }
        quad(v, m, x0 + hx, y0, z0 + hz, x0 - hx, y0, z0 - hz, x1 - hx, y1, z1 - hz, x1 + hx, y1, z1 + hz, rgb, lumiere);
        quad(v, m, x0 + vx, y0 + vy, z0 + vz, x0 - vx, y0 - vy, z0 - vz, x1 - vx, y1 - vy, z1 - vz, x1 + vx, y1 + vy, z1 + vz, rgb, lumiere);
    }

    private static void quad(VertexConsumer v, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2,
                             double z2, double x3, double y3, double z3, int rgb, int lumiere) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        v.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, b, 255).uv2(lumiere).endVertex();
        v.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, 255).uv2(lumiere).endVertex();
        v.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, 255).uv2(lumiere).endVertex();
        v.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, b, 255).uv2(lumiere).endVertex();
    }
}
