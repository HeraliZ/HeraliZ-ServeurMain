package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.mesh.Noise;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1aAtlas.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Pièces communes du lot T1a (rue, commerces, déchets) : formes molles posées au sol (sacs), cuves à section de
 * rectangle arrondi (bacs, bennes, seaux), étiquettes enroulées lisibles, anneaux à texture de disque (pneus),
 * parois doubles (grillages, cagettes ajourées), petits déchets (canettes, bouteilles, conserves, papiers).
 * Repère : pixels (1 px = 6,25 cm), sol en y = 0, dos au mur en z = 0, avant vers +Z.
 */
final class LotT1aKit {
    private LotT1aKit() {
    }

    // =================================================================== outils

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    static float[] normale(float[]... pts) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < pts.length; i++) {
            float[] a = pts[i], b = pts[(i + 1) % pts.length];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-8f ? new float[]{0, 1, 0} : new float[]{nx / l, ny / l, nz / l};
    }

    /** Quad visible des deux côtés (grillage, paroi ajourée, film, page). */
    static void doubleFace(MeshBuilder b, Mat m, float[]... pts) {
        float[] n = normale(pts);
        b.face(m, n, pts);
        b.face(m, new float[]{-n[0], -n[1], -n[2]}, pts);
    }

    /** Décalcomanie (comme KitUsure.decal) avec un décollement choisi : deux décors superposés ne se confondent pas. */
    static void decalO(MeshBuilder b, Mat m, char face, float cx, float cy, float cz, float w, float h, float angle, float decolle) {
        b.push().translate(cx, cy, cz);
        switch (face) {
            case 'Z' -> b.rotateY(180);
            case 'x' -> b.rotateY(-90);
            case 'X' -> b.rotateY(90);
            case 'y' -> b.rotateX(-90);
            default -> {
            }
        }
        if (angle != 0) b.rotateZ(angle);
        b.decalZ(m, 0, 0, decolle, w, h);
        b.pop();
    }

    // =================================================================== formes molles

    /**
     * Forme molle de révolution (sac, coussin, ballot) placée par une matrice monde, écrasée au sol : les points qui
     * passeraient sous y = sol sont aplatis dessus (la forme s'affaisse et pose à plat). Profil (rayon, y) du bas vers
     * le haut, axe Y local ; sx, sz : ovalisation ; amp, freq : bosses (bruit) ; affaisse : le haut retombe (0..1).
     */
    static void mou(MeshBuilder b, Matrix4f t, float[][] prof, int segs, float sx, float sz, float amp, float freq, int graine,
                    Mat m, float sol) {
        mou(b, t, prof, segs, sx, sz, amp, freq, graine, m, sol, false, 0);
    }

    /**
     * Variante : dedans = faces tournées vers l'intérieur (doublure d'un sac ouvert) ; dechire = le dernier rang du
     * profil (bord ouvert) est déchiqueté de cette amplitude (px).
     */
    static void mou(MeshBuilder b, Matrix4f t, float[][] prof, int segs, float sx, float sz, float amp, float freq, int graine,
                    Mat m, float sol, boolean dedans, float dechire) {
        int n = prof.length;
        float len = 0;
        float rMax = 0;
        for (float[] q : prof) rMax = Math.max(rMax, q[0]);
        float per = (float) (2 * Math.PI * rMax * (sx + sz) / 2);
        for (int i = 1; i < n; i++) len += (float) Math.hypot(prof[i][0] - prof[i - 1][0], prof[i][1] - prof[i - 1][1]);
        float w = m.world > 0 ? m.world : 16f;
        Vector3f v = new Vector3f();
        b.surface((u, vv) -> {
            int i = Math.min(n - 1, Math.round(vv * (n - 1)));
            float r = prof[i][0], y = prof[i][1];
            double a = u * 2 * Math.PI;
            if (i == n - 1 && dechire != 0) {
                float d = Noise.fbm((float) Math.cos(a) * 1.7f + graine, (float) Math.sin(a) * 1.7f, graine * 0.37f, 2);
                y += dechire * d * 1.6f;
                r *= 1 + 0.15f * d;
            }
            float x = (float) Math.cos(a) * r * sx, z = (float) Math.sin(a) * r * sz;
            if (amp != 0 && r > 1e-3f) {
                float k = 1 + amp * Noise.fbm(x * freq + graine * 7.13f, y * freq + graine * 3.71f, z * freq - graine * 1.3f, 3);
                x *= k;
                z *= k;
                y += amp * 0.6f * r * Noise.fbm(x * freq - graine * 2.1f, y * freq + 5.3f, z * freq + graine, 2);
            }
            t.transformPosition(x, y, z, v);
            if (v.y < sol) v.y = sol;
            return new float[]{v.x, v.y, v.z};
        }, segs, n - 1, m, Math.max(0.3f, per / w), Math.max(0.3f, len / w), true, dedans ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT);
    }

    /** Profil (rayon, y) décalé en y (centrer un sac couché sur son milieu). */
    static float[][] decale(float[][] prof, float dy) {
        float[][] o = new float[prof.length][];
        for (int i = 0; i < prof.length; i++) o[i] = new float[]{prof[i][0], prof[i][1] + dy};
        return o;
    }

    /** Point du repère local transformé (sommet d'un sac, ouverture...). */
    static Vector3f point(Matrix4f t, float x, float y, float z) {
        return t.transformPosition(x, y, z, new Vector3f());
    }

    /** Rangs ronds d'une cuve : {diamètre, y} -> {w, d, r, y}. */
    static float[][] rond(float[]... dy) {
        float[][] o = new float[dy.length][];
        for (int i = 0; i < dy.length; i++) o[i] = new float[]{dy[i][0], dy[i][0], dy[i][0] / 2, dy[i][1]};
        return o;
    }

    /** Décalcomanie orientée librement (face inclinée) : rotations Y, X, Z (degrés) puis quad face à +Z local. */
    static void decalT(MeshBuilder b, Mat m, float cx, float cy, float cz, float w, float h, float ry, float rx, float rz, float decolle) {
        b.push().translate(cx, cy, cz).rotateY(ry).rotateX(rx).rotateZ(rz);
        b.decalZ(m, 0, 0, decolle, w, h);
        b.pop();
    }

    /** Décalcomanie tangente à un cylindre vertical (centre cx, cz ; rayon R) à l'angle aDeg (90 = face +Z). */
    static void decalCyl(MeshBuilder b, Mat m, float cx, float cz, float R, float aDeg, float y, float w, float h, float rz) {
        double a = Math.toRadians(aDeg);
        b.push().translate(cx + R * (float) Math.cos(a), y, cz + R * (float) Math.sin(a)).rotateY(90 - aDeg);
        if (rz != 0) b.rotateZ(rz);
        b.decalZ(m, 0, 0, 0.05f, w, h);
        b.pop();
    }

    /** Champ de hauteur (surface d'ordures, de cendres, de terre) sur un rectangle, normales vers le haut. */
    static void champ(MeshBuilder b, float x0, float z0, float x1, float z1, float y, float amp, float freq, int nu, int nv, int graine, Mat m) {
        float w = m.world > 0 ? m.world : 16f;
        b.surface((u, v) -> {
            float x = x0 + (x1 - x0) * u, z = z0 + (z1 - z0) * v;
            float bord = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
            float k = Math.min(1f, bord * 6f);
            return new float[]{x, y + amp * k * Noise.fbm(x * freq + graine, z * freq - graine * 0.7f, graine * 0.31f, 3), z};
        }, nu, nv, m, (x1 - x0) / w, (z1 - z0) / w, false, MeshBuilder.Orient.KEEP);
    }

    /** Matrice monde : translation puis rotations (degrés, ordre Y, X, Z). */
    static Matrix4f place(float x, float y, float z, float ry, float rx, float rz) {
        return new Matrix4f().translate(x, y, z).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx)).rotateZ((float) Math.toRadians(rz));
    }

    /** Profil d'un sac poubelle 60 L debout (Ø 45, 70 cm) noué en haut ; h = hauteur totale (px). */
    static float[][] profilSac(float r, float h) {
        return new float[][]{{0.01f, 0}, {r * 0.7f, 0.05f}, {r * 0.95f, h * 0.08f}, {r * 1.02f, h * 0.25f}, {r, h * 0.45f}, {r * 0.92f, h * 0.62f},
                {r * 0.72f, h * 0.76f}, {r * 0.42f, h * 0.86f}, {r * 0.16f, h * 0.92f}, {r * 0.12f, h * 0.95f}, {r * 0.2f, h * 0.975f}, {0.01f, h}};
    }

    /** Nœud d'un sac (deux oreilles de plastique) au sommet (x, y, z). */
    static void noeud(MeshBuilder b, float x, float y, float z, float ang, Mat m) {
        b.push().translate(x, y, z).rotateY(ang);
        b.push().scale(1, 0.8f, 1);
        b.sphere(0.42f, 8, 4, m);
        b.pop();
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{p(0, 0.1f, 0), p(s * 0.7f, 0.55f, 0.15f), p(s * 1.25f, 0.45f, 0.3f), p(s * 1.5f, 0.1f, 0.2f)}, new float[]{0.3f, 0.32f, 0.22f, 0.04f}, 5, m, false, false, 0, false);
        }
        b.pop();
    }

    // =================================================================== cuves (section en rectangle arrondi)

    /** Contour d'un rectangle arrondi (centre 0), points (x, z) dans le sens trigonométrique. */
    static float[][] contour(float w, float d, float r, int cseg) {
        r = Math.max(0.02f, Math.min(r, Math.min(w, d) / 2 - 0.01f));
        float hw = w / 2, hd = d / 2;
        float[][] c = {{hw - r, hd - r, 0}, {-hw + r, hd - r, 90}, {-hw + r, -hd + r, 180}, {hw - r, -hd + r, 270}};
        float[][] out = new float[4 * (cseg + 1)][];
        int k = 0;
        for (float[] cc : c) {
            for (int i = 0; i <= cseg; i++) {
                double a = Math.toRadians(cc[2] + 90.0 * i / cseg);
                out[k++] = new float[]{cc[0] + r * (float) Math.cos(a), cc[1] + r * (float) Math.sin(a)};
            }
        }
        return out;
    }

    /**
     * Paroi d'une cuve : chaque rang du profil est un rectangle arrondi {w, d, r, y} (largeur X, profondeur Z, rayon des
     * coins, hauteur), centré en (cx, cz). dedans : faces tournées vers l'intérieur (paroi intérieure d'un bac ouvert).
     * bosse(x, y, z) (optionnel) : enfoncement vers l'intérieur (px) d'un point de la paroi (bosses, coups).
     */
    static void cuve(MeshBuilder b, float cx, float cz, float[][] prof, int cseg, Mat m, boolean dedans, Bosse bosse) {
        int ni = prof.length;
        float[][][] ct = new float[ni][][];
        for (int i = 0; i < ni; i++) ct[i] = contour(prof[i][0], prof[i][1], prof[i][2], cseg);
        int nj = ct[0].length + 1;
        float[][][] P = new float[ni][nj][];
        float[][] S = new float[ni][nj], T = new float[ni][nj];
        float w = m.world > 0 ? m.world : 16f;
        float[] lenI = new float[ni];
        for (int i = 1; i < ni; i++) lenI[i] = lenI[i - 1] + (float) Math.hypot(prof[i][3] - prof[i - 1][3], (prof[i][0] - prof[i - 1][0]) / 2);
        for (int i = 0; i < ni; i++) {
            float per = 0;
            for (int j = 0; j < nj; j++) {
                float[] q = ct[i][j % (nj - 1)];
                if (j > 0) {
                    float[] q0 = ct[i][(j - 1) % (nj - 1)];
                    per += (float) Math.hypot(q[0] - q0[0], q[1] - q0[1]);
                }
                float x = cx + q[0], y = prof[i][3], z = cz + q[1];
                if (bosse != null) {
                    float d = bosse.enfonce(x, y, z);
                    if (d != 0) {
                        float l = (float) Math.hypot(q[0], q[1]);
                        if (l > 1e-4f) {
                            x -= q[0] / l * d;
                            z -= q[1] / l * d;
                        }
                    }
                }
                P[i][j] = new float[]{x, y, z};
                S[i][j] = per / w;
                T[i][j] = lenI[i] / w;
            }
        }
        b.grid(P, null, S, T, true, m, dedans ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT, true);
    }

    @FunctionalInterface
    interface Bosse {
        float enfonce(float x, float y, float z);
    }

    /** Coup localisé (bosse ronde) centré en (x, y, z), rayon r, profondeur p. */
    static Bosse coup(float bx, float by, float bz, float r, float prof) {
        return (x, y, z) -> {
            float d = (float) Math.sqrt((x - bx) * (x - bx) + (y - by) * (y - by) + (z - bz) * (z - bz)) / r;
            return d >= 1 ? 0 : prof * (1 - d * d) * (1 - d * d);
        };
    }

    /** Fond ou couvercle plat d'une cuve (éventail de triangles), face vers le haut (up) ou vers le bas. */
    static void capot(MeshBuilder b, float cx, float y, float cz, float w, float d, float r, int cseg, Mat m, boolean up) {
        float[][] c = contour(w, d, r, cseg);
        float[] n = {0, up ? 1 : -1, 0};
        for (int i = 0; i < c.length; i++) {
            float[] a = c[i], e = c[(i + 1) % c.length];
            b.face(m, n, p(cx, y, cz), p(cx + a[0], y, cz + a[1]), p(cx + e[0], y, cz + e[1]));
        }
    }

    // =================================================================== révolution avec étiquette

    /**
     * Cylindre (axe Y local) dont la paroi porte une étiquette FIT enroulée, lisible à l'endroit ; le centre de
     * l'étiquette regarde la direction aDeg (90 = +Z). top/bas : disques (null = ouvert).
     */
    static void etiquetteCyl(MeshBuilder b, float r, float y0, float y1, int segs, Mat lab, float aDeg, Mat top, Mat bas) {
        for (int i = 0; i < segs; i++) {
            double a0 = Math.toRadians(aDeg - 180 + 360.0 * i / segs), a1 = Math.toRadians(aDeg - 180 + 360.0 * (i + 1) / segs);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float u0 = 1f - (float) i / segs, u1 = 1f - (float) (i + 1) / segs;
            float[][] pts = {p(r * c0, y0, r * s0), p(r * c1, y0, r * s1), p(r * c1, y1, r * s1), p(r * c0, y1, r * s0)};
            float[][] uv = {{lab.u(u0), lab.v(1)}, {lab.u(u1), lab.v(1)}, {lab.u(u1), lab.v(0)}, {lab.u(u0), lab.v(0)}};
            float[][] nr = {{c0, 0, s0}, {c1, 0, s1}, {c1, 0, s1}, {c0, 0, s0}};
            b.poly(pts, uv, nr, new float[]{(c0 + c1) / 2, 0, (s0 + s1) / 2});
        }
        if (top != null) b.disc(r, y1, segs, top, true);
        if (bas != null) b.disc(r, y0, segs, bas, false);
    }

    /**
     * Anneau (couronne) d'axe Y à la hauteur y, entre rIn et rOut, texture FIT du disque entier de rayon R ;
     * bombe : la couronne se soulève de bombe px au milieu (flanc de pneu). up : face vers +Y (sinon -Y).
     */
    static void anneau(MeshBuilder b, float y, float rIn, float rOut, float R, float bombe, int segs, int rangs, Mat m, boolean up) {
        float sg = up ? 1 : -1;
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            for (int k = 0; k < rangs; k++) {
                float ra = rIn + (rOut - rIn) * k / rangs, rb = rIn + (rOut - rIn) * (k + 1) / rangs;
                float ya = y + sg * bombe * (float) Math.sin(Math.PI * k / rangs), yb = y + sg * bombe * (float) Math.sin(Math.PI * (k + 1) / rangs);
                float[][] pts = {p(ra * c0, ya, ra * s0), p(rb * c0, yb, rb * s0), p(rb * c1, yb, rb * s1), p(ra * c1, ya, ra * s1)};
                float[][] uv = new float[4][];
                for (int q = 0; q < 4; q++) {
                    float uu = (pts[q][0] / R * (up ? 1 : -1) + 1) / 2, vv = (pts[q][2] / R + 1) / 2;
                    uv[q] = new float[]{m.u(uu), m.v(vv)};
                }
                float pente = (float) (sg * bombe * Math.PI / (rOut - rIn) * Math.cos(Math.PI * (k + 0.5) / rangs));
                float[] nn = norm(-pente * (c0 + c1) / 2, sg, -pente * (s0 + s1) / 2);
                b.poly(pts, uv, null, nn);
            }
        }
    }

    /** Bornes planaires (projection FIT) d'un ensemble de points pour la normale n. */
    static float[] bornes(float[] n, float[]... pts) {
        float[] r = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] q : pts) {
            float[] ab = Mat.planar(q, n);
            r[0] = Math.min(r[0], ab[0]);
            r[1] = Math.max(r[1], ab[0]);
            r[2] = Math.min(r[2], ab[1]);
            r[3] = Math.max(r[3], ab[1]);
        }
        return r;
    }

    static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return l < 1e-8f ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l};
    }

    // =================================================================== petits déchets

    /** Canette 33 cl (Ø 6,6 cm, 11,5 cm) debout, centre du fond en (x, y, z). */
    static void canette(MeshBuilder b, float x, float y, float z, Mat lab, float ang) {
        b.push().translate(x, y, z);
        etiquetteCyl(b, 0.52f, 0.12f, 1.7f, 10, lab, ang, null, null);
        b.lathe(new float[][]{{0.38f, 0}, {0.5f, 0.06f}, {0.52f, 0.12f}}, 10, 0, 360, CHROME_PIQUE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.52f, 1.7f}, {0.42f, 1.84f}, {0.42f, 1.86f}}, 10, 0, 360, CHROME_PIQUE, 1, 1, 0, 0, 0, false);
        b.disc(0.42f, 1.8f, 10, METAL_NU, true);
        b.disc(0.38f, 0.02f, 10, METAL_NU, false);
        b.pop();
    }

    /** Canette écrasée couchée au sol (aplatie, pliée), centre (x, z). */
    static void canetteEcrasee(MeshBuilder b, float x, float z, Mat lab, float ang, int graine) {
        b.push().translate(x, 0.28f, z).rotateY(ang).rotateZ(90).scale(0.5f, 0.8f, 1);
        b.lathe(new float[][]{{0.5f, -0.75f}, {0.55f, -0.3f}, {0.42f, 0.05f}, {0.56f, 0.4f}, {0.5f, 0.75f}}, 9, 0, 360, lab.withWorld(2.4f), 1, 1, 0.12f, 1.5f, graine, false);
        b.disc(0.5f, 0.75f, 9, METAL_NU, true);
        b.disc(0.5f, -0.75f, 9, METAL_NU, false);
        b.pop();
    }

    /** Bouteille de bière (33 cl, 23 cm) debout ; cassee : goulot brisé (bord dentelé). */
    static void bouteille(MeshBuilder b, float x, float y, float z, Mat verre, boolean cassee, int graine) {
        b.push().translate(x, y, z);
        if (!cassee) {
            b.lathe(new float[][]{{0.4f, 0}, {0.47f, 0.08f}, {0.48f, 2.0f}, {0.4f, 2.45f}, {0.2f, 2.95f}, {0.19f, 3.45f}, {0.23f, 3.5f}, {0.23f, 3.66f}, {0.01f, 3.68f}},
                    9, 0, 360, verre, 1, 1, 0, 0, 0, false);
        } else {
            b.lathe(new float[][]{{0.4f, 0}, {0.47f, 0.08f}, {0.48f, 1.6f}, {0.44f, 1.9f}}, 9, 0, 360, verre, 1, 1, 0.1f, 2.2f, graine, false);
            b.lathe(new float[][]{{0.44f, 1.9f}, {0.4f, 0.2f}}, 9, 0, 360, verre, 1, 1, 0.1f, 2.2f, graine, true);
        }
        b.pop();
    }

    /** Bouteille couchée au sol (axe le long de X tourné de ang). */
    static void bouteilleCouchee(MeshBuilder b, float x, float z, float ang, Mat verre) {
        b.push().translate(x, 0.48f, z).rotateY(ang).rotateZ(90).translate(0, -1.8f, 0);
        b.lathe(new float[][]{{0.01f, -0.01f}, {0.4f, 0}, {0.47f, 0.08f}, {0.48f, 2.0f}, {0.4f, 2.45f}, {0.2f, 2.95f}, {0.19f, 3.45f}, {0.23f, 3.5f}, {0.23f, 3.66f}, {0.01f, 3.68f}},
                9, 0, 360, verre, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Bouteille plastique 1,5 L (Ø 9 cm, 31 cm) couchée, étiquette FIT, bouchon. Écrasée : aplatie. */
    static void bouteillePet(MeshBuilder b, float x, float z, float ang, Mat lab, Mat bouchon, float ecrase) {
        float r = 0.72f;
        b.push().translate(x, r * ecrase, z).rotateY(ang).rotateZ(90).scale(ecrase, 1, 1).translate(0, -2.5f, 0);
        b.lathe(new float[][]{{0.01f, 0}, {0.6f, 0.02f}, {r, 0.25f}, {r, 1.3f}}, 10, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        etiquetteCyl(b, r + 0.01f, 1.3f, 2.6f, 10, lab, 90, null, null);
        b.lathe(new float[][]{{r, 2.6f}, {r, 3.3f}, {0.5f, 4.0f}, {0.24f, 4.5f}}, 10, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.cylinder(0.26f, 4.5f, 4.85f, 8, bouchon, null, bouchon);
        b.pop();
    }

    /** Boîte de conserve (Ø 7,5 cm, 11 cm) couchée ou debout. */
    static void conserve(MeshBuilder b, float x, float y, float z, Mat lab, float ang, boolean couchee) {
        b.push().translate(x, y, z);
        if (couchee) b.translate(0, 0.62f, 0).rotateY(ang).rotateZ(90).translate(0, -0.88f, 0);
        etiquetteCyl(b, 0.6f, 0.1f, 1.66f, 10, lab, couchee ? 90 : ang, null, null);
        b.cylinder(0.62f, 0, 0.12f, 10, METAL_NU, METAL_NU, null);
        b.cylinder(0.62f, 1.64f, 1.76f, 10, METAL_NU, null, METAL_NU);
        b.pop();
    }

    /** Boule de papier froissé. */
    static void papierFroisse(MeshBuilder b, float x, float y, float z, float r, Mat m, int graine) {
        mou(b, place(x, y + r * 0.62f, z, graine * 37f, 0, 0), new float[][]{{0.01f, -r * 0.8f}, {r * 0.7f, -r * 0.6f}, {r, 0}, {r * 0.75f, r * 0.55f}, {0.01f, r * 0.8f}},
                7, 1, 0.85f, 0.3f, 1.6f, graine, m, y);
    }

    /** Feuille posée à plat (légèrement gondolée, deux faces) au sol ou sur un objet, à la hauteur y. */
    static void feuille(MeshBuilder b, float x, float y, float z, float w, float h, float ang, float pli, Mat m) {
        b.push().translate(x, y, z).rotateY(ang);
        float[][] a = {p(-w / 2, 0.05f, -h / 2), p(0, 0.05f + pli, -h / 2), p(0, 0.05f + pli, h / 2), p(-w / 2, 0.05f, h / 2)};
        float[][] c = {p(0, 0.05f + pli, -h / 2), p(w / 2, 0.05f, -h / 2), p(w / 2, 0.05f, h / 2), p(0, 0.05f + pli, h / 2)};
        Mat f = m.fit ? m : m.asFit();
        float[][] coins = {p(-w / 2, 0, -h / 2), p(w / 2, 0, h / 2)};
        for (float[][] q : new float[][][]{a, c}) {
            float[] n = normale(q);
            if (n[1] < 0) n = new float[]{-n[0], -n[1], -n[2]};
            float[] ni = {-n[0], -n[1], -n[2]};
            b.faceBounded(f, n, bornes(n, coins), q);
            b.faceBounded(f, ni, bornes(ni, coins), q);
        }
        b.pop();
    }

    /** Brique de lait (1 L, 7 x 7 x 24 cm) couchée, toit en pignon. */
    static void briqueLait(MeshBuilder b, float x, float z, float ang) {
        b.push().translate(x, 0, z).rotateY(ang);
        b.bevelBox(-1.9f, 0, -0.56f, 1.5f, 1.12f, 0.56f, 0.06f, WHITE);
        b.extrudeProfileX(new float[][]{{-0.56f, 0.02f}, {0, 0.56f}, {0.56f, 0.02f}}, 1.5f, 1.9f, WHITE);
        b.push().translate(1.5f, 0.56f, 0).rotateZ(-90);
        b.bevelBox(-0.5f, 0, -0.56f, 0.5f, 0.75f, 0.56f, 0.04f, WHITE);
        b.pop();
        b.bevelBox(-1.6f, 0.2f, 0.565f, 1.2f, 0.95f, 0.6f, 0.02f, PLASTIC_BLUE);
        b.pop();
    }

    /** Éclats de verre au sol. */
    static void eclats(MeshBuilder b, float cx, float cz, float r, int n, Mat m, long graine) {
        KitUsure.debris(b, cx, cz, r, n, m, graine);
    }

    // =================================================================== composants de mobilier urbain

    /** Roue (axe X) : pneu plein, moyeu ; centre (x, y, z), rayon r, largeur e. */
    static void roue(MeshBuilder b, float x, float y, float z, float r, float e, Mat pneu, Mat moyeu) {
        b.push().translate(x, y, z).rotateZ(90);
        b.cylinder(r, -e / 2, e / 2, 12, pneu, pneu, pneu);
        b.cylinder(r * 0.5f, e / 2 + 0.04f, e / 2 + 0.16f, 10, moyeu, null, moyeu);
        b.cylinder(r * 0.5f, -e / 2 - 0.16f, -e / 2 - 0.04f, 10, moyeu, moyeu, null);
        b.pop();
    }

    /** Roulette pivotante : platine, chape, roue ; le bas de la roue touche y = 0. */
    static void roulette(MeshBuilder b, float x, float z, float r, float hChape, float ang, Mat chape, Mat pneu) {
        b.push().translate(x, 0, z).rotateY(ang);
        float yc = r;
        roue(b, 0, yc, 0, r, r * 0.55f, pneu, METAL_NU);
        for (int s = -1; s <= 1; s += 2) b.bevelBox(s * r * 0.4f - 0.08f, yc - 0.15f, -0.3f, s * r * 0.4f + 0.08f, yc + r + hChape * 0.4f, 0.3f, 0.04f, chape);
        b.bevelBox(-r * 0.48f, yc + r + hChape * 0.4f - 0.02f, -0.45f, r * 0.48f, yc + r + hChape, 0.45f, 0.06f, chape);
        b.pop();
    }

    /** Rng partagé (graines reproductibles). */
    static Random rng(long g) {
        return new Random(g);
    }
}
