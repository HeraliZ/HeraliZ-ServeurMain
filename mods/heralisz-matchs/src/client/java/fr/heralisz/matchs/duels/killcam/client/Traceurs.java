package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Traceurs, eclairs de bouche et etincelles d'impact de la killcam (rendu maison, aucune particule vanilla). Reserve
 * fixe de {@link #MAX} traceurs, aucune allocation par image. Le temps est celui de l'horloge du {@link Lecteur}
 * (en images, ralenti compris) : une balle « vole » a {@link #VITESSE} blocs par image.
 */
final class Traceurs {
    static final int MAX = 32;
    /** Blocs parcourus par image de jeu (≈ 260 blocs/s). */
    private static final double VITESSE = 13;
    /** Longueur de la trainee. */
    private static final double LONGUEUR = 4.5;
    private static final double LARGEUR = 0.035;
    /** Duree de l'eclair de bouche et de l'etincelle d'impact, en images. */
    private static final double FLASH = 1.3, IMPACT = 3;

    private final double[] sx = new double[MAX], sy = new double[MAX], sz = new double[MAX];
    private final double[] ex = new double[MAX], ey = new double[MAX], ez = new double[MAX];
    private final double[] longueur = new double[MAX], ne = new double[MAX], flash = new double[MAX];
    private final boolean[] touche = new boolean[MAX], actif = new boolean[MAX];
    private int prochain;

    void vider() {
        java.util.Arrays.fill(actif, false);
    }

    /**
     * Nouveau traceur de (x0..) a (x1..), ne a l'instant {@code horloge} ; touche = impact sur un joueur ;
     * {@code echelleFlash} = taille de l'eclair de bouche (1 normal, plus petit vu par les yeux du tireur).
     */
    void ajouter(double x0, double y0, double z0, double x1, double y1, double z1, boolean surJoueur, double horloge, double echelleFlash) {
        int i = prochain;
        prochain = (prochain + 1) % MAX;
        sx[i] = x0;
        sy[i] = y0;
        sz[i] = z0;
        ex[i] = x1;
        ey[i] = y1;
        ez[i] = z1;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        longueur[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        ne[i] = horloge;
        flash[i] = echelleFlash;
        touche[i] = surJoueur;
        actif[i] = true;
    }

    int actifs() {
        int n = 0;
        for (boolean a : actif) if (a) n++;
        return n;
    }

    /** Dessine tout (appele dans RenderLevelStageEvent, apres les particules). */
    void dessiner(PoseStack pose, Camera camera, MultiBufferSource.BufferSource tampons, double horloge) {
        Vec3 c = camera.getPosition();
        double cx = c.x, cy = c.y, cz = c.z;
        Vector3f gauche = camera.getLeftVector(), haut = camera.getUpVector();
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = tampons.getBuffer(RenderType.lightning());
        boolean rien = true;
        for (int i = 0; i < MAX; i++) {
            if (!actif[i]) continue;
            double age = horloge - ne[i];
            double l = longueur[i];
            double fin = l / VITESSE + IMPACT;
            if (age < 0 || age > fin + 0.5) {
                if (age > fin + 0.5) actif[i] = false;
                continue;
            }
            rien = false;
            double ux = (ex[i] - sx[i]) / Math.max(l, 1e-3), uy = (ey[i] - sy[i]) / Math.max(l, 1e-3), uz = (ez[i] - sz[i]) / Math.max(l, 1e-3);
            // trainee : de (tete - LONGUEUR) a tete, bornee au trajet
            double tete = Math.min(l, age * VITESSE), queue = Math.max(0, tete - LONGUEUR);
            if (tete > queue + 0.01 && age * VITESSE < l + LONGUEUR) {
                double ax = sx[i] + ux * queue - cx, ay = sy[i] + uy * queue - cy, az = sz[i] + uz * queue - cz;
                double bx = sx[i] + ux * tete - cx, by = sy[i] + uy * tete - cy, bz = sz[i] + uz * tete - cz;
                // cote = direction x (camera -> milieu), normalise
                double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
                double px = uy * mz - uz * my, py = uz * mx - ux * mz, pz = ux * my - uy * mx;
                double pl = Math.sqrt(px * px + py * py + pz * pz);
                if (pl > 1e-6) {
                    px = px / pl * LARGEUR;
                    py = py / pl * LARGEUR;
                    pz = pz / pl * LARGEUR;
                    int aT = 235, aQ = 0;
                    quad(vc, m, ax + px, ay + py, az + pz, ax - px, ay - py, az - pz, bx - px, by - py, bz - pz, bx + px, by + py, bz + pz,
                            255, 214, 150, aQ, aT);
                    // coeur plus fin et plus clair
                    quad(vc, m, ax + px * 0.4, ay + py * 0.4, az + pz * 0.4, ax - px * 0.4, ay - py * 0.4, az - pz * 0.4,
                            bx - px * 0.4, by - py * 0.4, bz - pz * 0.4, bx + px * 0.4, by + py * 0.4, bz + pz * 0.4, 255, 250, 235, aQ, 255);
                }
            }
            // eclair de bouche
            if (age < FLASH) {
                float k = (float) (1 - age / FLASH);
                double t = (0.12 + 0.16 * k) * flash[i];
                etoile(vc, m, sx[i] - cx, sy[i] - cy, sz[i] - cz, gauche, haut, t, 255, 196, 110, (int) (230 * k));
            }
            // etincelle d'impact
            double arrivee = l / VITESSE;
            if (age >= arrivee && age < arrivee + IMPACT) {
                float k = (float) (1 - (age - arrivee) / IMPACT);
                double t = 0.10 + 0.12 * k;
                if (touche[i]) etoile(vc, m, ex[i] - cx, ey[i] - cy, ez[i] - cz, gauche, haut, t * 1.4, 255, 70, 60, (int) (220 * k));
                else etoile(vc, m, ex[i] - cx, ey[i] - cy, ez[i] - cz, gauche, haut, t, 255, 236, 200, (int) (200 * k));
            }
        }
        if (!rien) tampons.endBatch(RenderType.lightning());
    }

    /** Quadrilatere a deux faces ; (1, 2) = bout de queue (alpha aQ), (3, 4) = tete (alpha aT). */
    private static void quad(VertexConsumer vc, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int aQ, int aT) {
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, aQ).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, aQ).endVertex();
        vc.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, b, aT).endVertex();
        vc.vertex(m, (float) x4, (float) y4, (float) z4).color(r, g, b, aT).endVertex();
        vc.vertex(m, (float) x4, (float) y4, (float) z4).color(r, g, b, aT).endVertex();
        vc.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, b, aT).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, aQ).endVertex();
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, aQ).endVertex();
    }

    /** Etoile a 4 branches face a la camera (deux losanges croises), centre (x, y, z) relatif a la camera. */
    private static void etoile(VertexConsumer vc, Matrix4f m, double x, double y, double z, Vector3f g, Vector3f h, double t,
                               int r, int v, int b, int a) {
        if (a <= 2) return;
        double gx = g.x() * t, gy = g.y() * t, gz = g.z() * t, hx = h.x() * t, hy = h.y() * t, hz = h.z() * t;
        double f = 0.22;
        // branche horizontale
        quadPlein(vc, m, x + gx, y + gy, z + gz, x + hx * f, y + hy * f, z + hz * f, x - gx, y - gy, z - gz, x - hx * f, y - hy * f, z - hz * f,
                r, v, b, a);
        // branche verticale
        quadPlein(vc, m, x + hx, y + hy, z + hz, x - gx * f, y - gy * f, z - gz * f, x - hx, y - hy, z - hz, x + gx * f, y + gy * f, z + gz * f,
                r, v, b, a);
    }

    private static void quadPlein(VertexConsumer vc, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                                  double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        quad(vc, m, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, r, g, b, a, a);
    }
}
