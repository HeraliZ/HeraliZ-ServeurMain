package fr.heraliz.armory.cible;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Corps « en morceaux » de la cible d'essai pour les balles de TaCZ (03/11), le même que celui d'un joueur debout sans arme
 * en main (fr.heraliz.tactique.hitbox.HitboxCorps du module armes, mixin TaczHitboxCorpsMixin) : tête, torse, deux bras,
 * deux jambes, cubes et pivots du modèle du joueur, échelle 0,9375, demi-pixel autour de la tête et quart de pixel autour du
 * reste. Tir à la tête = morceau « tête », exactement comme sur un joueur (sans cela, TaCZ jugerait la tête sur la boîte
 * droite de la cible et sa hauteur des yeux, une autre zone).
 * <p>Seule différence : la cible est posée sur un socle d'un pixel (modèle du mannequin d'armurerie), tout le corps est donc
 * plus haut d'un pixel ; les morceaux suivent exactement le dessin (mêmes pivots que client.cible.CibleModel). Les matrices
 * ne sont recalculées que si la cible a bougé ou tourné (gardées par CibleEssaiServeur). Fil du serveur.</p>
 */
public final class HitboxCible {
    private HitboxCible() {}

    private static final float ECHELLE = 0.9375F;
    /** Pivots des morceaux en pixels du modèle (corps relevé d'un pixel pour le socle) : tête, torse, bras, jambes. */
    private static final float[][] PIVOTS = {{0, -1, 0}, {0, -1, 0}, {-5, 1, 0}, {5, 1, 0}, {-1.9F, 11, 0}, {1.9F, 11, 0}};
    /** Cubes en pixels (avec leur marge), comme HitboxCorps. */
    private static final float[][] CUBES = {cube(-4, -8, -4, 8, 8, 8, 0.5F), cube(-4, 0, -2, 8, 12, 4, 0.25F),
            cube(-3, -2, -2, 4, 12, 4, 0.25F), cube(-1, -2, -2, 4, 12, 4, 0.25F), cube(-2, 0, -2, 4, 12, 4, 0.25F),
            cube(-2, 0, -2, 4, 12, 4, 0.25F)};
    /** Le premier morceau est la tête. */
    static final int TETE = 0;
    /** Marge de tri rapide autour de la boîte de la cible (les bras dépassent de la boîte de 0,17 bloc). */
    private static final double MARGE = 0.5D;

    /** Un morceau touché : point du monde et tête ou non. */
    public record Touche(Vec3 point, boolean tete) {}

    /** Matrices monde → repère de chaque morceau, pour une cible posée en (x, y, z) et tournée de {@code lacet}. */
    static Matrix4f[] inverses(double x, double y, double z, float lacet) {
        Matrix4f base = new Matrix4f().translate((float) x, (float) y, (float) z).rotateY((float) Math.toRadians(180.0F - lacet))
                .scale(-1.0F, -1.0F, 1.0F).scale(ECHELLE).translate(0.0F, -1.501F, 0.0F);
        Matrix4f[] r = new Matrix4f[PIVOTS.length];
        for (int i = 0; i < PIVOTS.length; i++) {
            float[] p = PIVOTS[i];
            r[i] = new Matrix4f(base).translate(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F).scale(1.0F / 16.0F).invert();
        }
        return r;
    }

    /** Premier morceau traversé par le segment {@code de} → {@code a}, ou null (balle à côté). */
    static Touche toucher(Matrix4f[] inverses, AABB boite, Vec3 de, Vec3 a) {
        AABB large = boite.inflate(MARGE);
        if (!large.contains(de) && large.clip(de, a).isEmpty()) return null;
        Vector3f d = new Vector3f(), f = new Vector3f();
        double meilleur = -1.0D;
        int morceau = -1;
        for (int i = 0; i < inverses.length; i++) {
            inverses[i].transformPosition((float) de.x, (float) de.y, (float) de.z, d);
            inverses[i].transformPosition((float) a.x, (float) a.y, (float) a.z, f);
            double t = rayonBoite(d, f, CUBES[i]);
            if (t >= 0.0D && (morceau < 0 || t < meilleur)) {
                meilleur = t;
                morceau = i;
            }
        }
        return morceau < 0 ? null : new Touche(de.add(a.subtract(de).scale(meilleur)), morceau == TETE);
    }

    private static float[] cube(float x, float y, float z, float l, float h, float p, float marge) {
        return new float[]{x - marge, y - marge, z - marge, x + l + marge, y + h + marge, z + p + marge};
    }

    /** Méthode des dalles dans le repère du cube : fraction d'entrée, 0 si le départ est dedans, -1 si manqué (HitboxCorps). */
    private static double rayonBoite(Vector3f d, Vector3f f, float[] c) {
        double tMin = 0.0D, tMax = 1.0D;
        float[] o = {d.x, d.y, d.z};
        float[] v = {f.x - d.x, f.y - d.y, f.z - d.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < 1.0E-7F) {
                if (o[i] < c[i] || o[i] > c[i + 3]) return -1.0D;
            } else {
                double t1 = (c[i] - o[i]) / v[i], t2 = (c[i + 3] - o[i]) / v[i];
                if (t1 > t2) {
                    double s = t1;
                    t1 = t2;
                    t2 = s;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return -1.0D;
            }
        }
        return tMin;
    }
}
