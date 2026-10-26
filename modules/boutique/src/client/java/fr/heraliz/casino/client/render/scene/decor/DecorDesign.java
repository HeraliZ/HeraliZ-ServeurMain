package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Modele 3D d'un element de decor : textures dans l'atlas partage "decor" (ou celui des affiches), vue = DecorView.
 * Outils communs aux ecrans recuperes : parasites ("glitch"), tranches decalees, aberration chromatique, neige.
 */
public abstract class DecorDesign extends MachineDesign<DecorView> {
    protected static final float[] NZ = {0, 0, 1};

    /** Constructeur de maillages reutilise pour les parties recalculees a chaque image (pas d'allocation lourde). */
    private final MeshBuilder scratch = new MeshBuilder();
    /** Centre du modele (px) : objet centre dans la case de l'inventaire. */
    public float[] itemCenter = {8, 8, 8};

    protected DecorDesign(String id, int glow) {
        this(id, DecorAtlas.ATLAS, glow);
    }

    protected DecorDesign(String id, String atlas, int glow) {
        super(id, atlas, glow, glow);
        labelY = 16;
        itemScale = 0.8f;
    }

    @Override
    public DecorView newView() {
        return new DecorView();
    }

    @Override
    public int demoLength() {
        return 200;
    }

    private final MeshBuilder scratch2 = new MeshBuilder();

    /** Constructeur vide pour une partie dynamique (a transformer avec buildDynamic()). */
    protected MeshBuilder scratch() {
        return scratch.reset();
    }

    /** Second constructeur vide (deux maillages dynamiques a la fois : segments allumes / eteints). */
    protected MeshBuilder scratch2() {
        return scratch2.reset();
    }

    // =================================================================== parasites
    /**
     * Intensite du grand glitch en cours (0 = image stable). Un glitch tous les "period" ticks environ, de 4 a 14 ticks,
     * a un instant propre a chaque element (seed). cycle[0] recoit le numero du glitch (graine des effets).
     */
    protected static float glitch(Scene s, float period, int[] cycle) {
        if (s.item) return 0;
        float t = s.time + s.seed * 7919f;
        int c = (int) Math.floor(t / period);
        float start = c * period + Ease.hash(c * 31 + 7) * period * 0.55f, len = 4 + Ease.hash(c * 17 + 3) * 10;
        if (cycle != null) cycle[0] = c;
        if (t < start || t > start + len) {
            // micro-coupure d'un tick de temps en temps
            int k = (int) t;
            return Ease.hash(k * 13 + 5 + (int) (s.seed * 997)) > 0.985f ? 0.35f : 0;
        }
        return Ease.bell(t, start - 1, start + len + 1) * (0.65f + 0.35f * Ease.hash((int) t * 13 + c));
    }

    /** Coupure franche au debut du glitch (ecran noir un instant). */
    protected static boolean blackout(Scene s, float g, int cycle) {
        return g > 0.55f && Ease.hash((int) s.time * 7 + cycle * 3) > 0.8f;
    }

    /**
     * Tranches horizontales decalees pendant un glitch : bornes (y) et decalage (x) de chaque tranche, communes a tous
     * les elements d'un meme ecran pour que l'image se dechire d'un seul tenant.
     */
    protected static final class Slices {
        final float[] cuts = new float[10];
        final float[] shift = new float[9];
        int n;

        public Slices set(float y0, float y1, float g, int tick, int cycle) {
            n = g <= 0.02f ? 1 : 3 + (int) (Ease.hash(tick * 7 + cycle * 3) * 5);
            cuts[0] = y0;
            cuts[n] = y1;
            for (int i = 1; i < n; i++) {
                float f = (i + (Ease.hash(tick * 11 + i * 5 + cycle) - 0.5f) * 0.9f) / n;
                cuts[i] = y0 + (y1 - y0) * Math.max(0.02f, Math.min(0.98f, f));
            }
            java.util.Arrays.sort(cuts, 1, n);
            for (int i = 0; i < n; i++) {
                float h = Ease.hash(i * 31 + tick * 13 + cycle * 7);
                shift[i] = g <= 0.02f || h < 0.4f ? 0 : (Ease.hash(i * 17 + tick * 29 + cycle) - 0.5f) * 3.4f * g;
            }
            return this;
        }

        public boolean flat() {
            for (int i = 0; i < n; i++) if (shift[i] != 0) return false;
            return true;
        }

        /** Quad de texture "m" (FIT) de (x0,y0) a (x1,y1), decoupe selon les tranches. */
        public void quad(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float z) {
            for (int i = 0; i < n; i++) {
                float ya = Math.max(y0, cuts[i]), yb = Math.min(y1, cuts[i + 1]);
                if (yb <= ya + 1e-4f) continue;
                float t0 = (y1 - yb) / (y1 - y0), t1 = (y1 - ya) / (y1 - y0);
                float dx = shift[i];
                b.face(m.sub(0, t0, 1, t1), NZ, p(x0 + dx, ya, z), p(x1 + dx, ya, z), p(x1 + dx, yb, z), p(x0 + dx, yb, z));
            }
        }
    }

    /**
     * Lettre ou mot au neon sur un ecran : le tube (verre, decoupe) puis sa lumiere teintee ; pendant un glitch, l'image
     * est decoupee en tranches decalees et doublee de rouge et de cyan. rest = quad fige de la case au repos.
     */
    protected void neonQuad(Pose p, DrawList out, Mesh rest, Mat cell, float x0, float y0, float x1, float y1, float z, Slices sl,
                            int color, float lit, float g) {
        Mesh m = rest;
        if (!sl.flat()) {
            MeshBuilder b = scratch();
            sl.quad(b, cell, x0, y0, x1, y1, z);
            m = b.buildDynamic();
        }
        out.solid(p, m, 0);
        if (lit <= 0.02f) return;
        out.additive(p, m, color, lit);
        if (g > 0.05f) {
            float d = 0.12f + 0.4f * g;
            p.push().translate(-d, 0, 0.015f);
            out.additive(p, m, 0xFF2A1A, 0.6f * g * lit);
            p.pop();
            p.push().translate(d, 0, 0.02f);
            out.additive(p, m, 0x1AE0FF, 0.6f * g * lit);
            p.pop();
        }
    }

    /** Segments allumes de chaque chiffre (bit 0 = a, en haut ; puis b, c, d, e, f dans le sens horaire ; bit 6 = g, au milieu). */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    /**
     * Afficheur a 7 segments (penches) : "count" chiffres alignes a droite sur xRight, groupes de trois separes de "gap".
     * Les segments allumes vont dans "on", les eteints (visibles, sombres) dans "off". dead : segments morts (bit
     * chiffre*7 + segment), jamais allumes.
     */
    protected static void digits(MeshBuilder on, MeshBuilder off, Mat ledOn, Mat ledOff, long value, int count, float xRight, float y0, float h,
                                 float w, float stride, float gap, float z, long dead) {
        String s = Long.toString(Math.max(0, value));
        float t = w * 0.24f, slant = 0.14f;
        for (int k = 0; k < count; k++) {
            // k = 0 : chiffre des unites
            int groupGaps = k / 3;
            float x = xRight - w - k * stride - groupGaps * gap;
            int idx = s.length() - 1 - k;
            int mask = idx >= 0 ? SEGMENTS[s.charAt(idx) - '0'] : 0;
            for (int seg = 0; seg < 7; seg++) {
                boolean lit = (mask & (1 << seg)) != 0 && (dead & (1L << (k * 7 + seg))) == 0;
                float[] q = switch (seg) {
                    case 0 -> new float[]{x + t * 0.6f, y0 + h - t, x + w - t * 0.6f, y0 + h};
                    case 1 -> new float[]{x + w - t, y0 + h / 2 + t * 0.3f, x + w, y0 + h - t * 0.6f};
                    case 2 -> new float[]{x + w - t, y0 + t * 0.6f, x + w, y0 + h / 2 - t * 0.3f};
                    case 3 -> new float[]{x + t * 0.6f, y0, x + w - t * 0.6f, y0 + t};
                    case 4 -> new float[]{x, y0 + t * 0.6f, x + t, y0 + h / 2 - t * 0.3f};
                    case 5 -> new float[]{x, y0 + h / 2 + t * 0.3f, x + t, y0 + h - t * 0.6f};
                    default -> new float[]{x + t * 0.6f, y0 + h / 2 - t / 2, x + w - t * 0.6f, y0 + h / 2 + t / 2};
                };
                float sa = (q[1] - y0) * slant, sb = (q[3] - y0) * slant;
                (lit ? on : off).face(lit ? ledOn : ledOff, NZ, p(q[0] + sa, q[1], z), p(q[2] + sa, q[1], z), p(q[2] + sb, q[3], z), p(q[0] + sb, q[3], z));
            }
        }
    }

    /** Neige (bruit) sur un ecran : zone de la tuile tiree au hasard a chaque tick. */
    protected void snow(Pose p, DrawList out, Mat noise, float x0, float y0, float x1, float y1, float z, int tick, int rgb, float a) {
        if (a <= 0.003f) return;
        MeshBuilder b = scratch();
        float u0 = Ease.hash(tick * 7 + 1) * 0.5f, v0 = Ease.hash(tick * 11 + 2) * 0.5f;
        b.face(noise.sub(u0, v0, u0 + 0.5f, v0 + 0.5f), NZ, p(x0, y0, z), p(x1, y0, z), p(x1, y1, z), p(x0, y1, z));
        out.additive(p, b.buildDynamic(), rgb, a);
    }

    /** Quad plat face a +Z (maillage fige). */
    protected static Mesh plate(Mat m, float x0, float y0, float x1, float y1, float z) {
        MeshBuilder b = new MeshBuilder();
        b.face(m, NZ, p(x0, y0, z), p(x1, y0, z), p(x1, y1, z), p(x0, y1, z));
        return b.build();
    }

    /** Vis cruciforme / tete de boulon posee sur une face tournee vers +Z. */
    protected static void screw(MeshBuilder b, float x, float y, float z, float r, Mat m) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(r, -0.05f, 0.08f, 8, m, null, m);
        b.rivet(r * 0.9f, r * 0.45f, m);
        b.pop();
    }

    /** Cable souple qui pend par des points de passage (lisse par une courbe de Catmull-Rom). */
    protected static void cable(MeshBuilder b, float[][] pts, float r, Mat m) {
        int per = 4;
        int n = (pts.length - 1) * per + 1;
        float[][] path = new float[n][];
        float[] radii = new float[n];
        for (int i = 0; i < pts.length - 1; i++) {
            float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
            for (int k = 0; k < per; k++) {
                float t = (float) k / per;
                path[i * per + k] = catmull(p0, p1, p2, p3, t);
            }
        }
        path[n - 1] = pts[pts.length - 1].clone();
        java.util.Arrays.fill(radii, r);
        b.tube(path, radii, 6, m, true, true, 0, false);
    }

    static float[] catmull(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        float[] o = new float[3];
        for (int k = 0; k < 3; k++) {
            o[k] = 0.5f * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
        }
        return o;
    }

    /** Scotch (bande grise) pose de travers sur une face tournee vers +Z. */
    protected static void tape(MeshBuilder b, Mat tape, float x, float y, float z, float len, float w, float angle) {
        b.push().translate(x, y, z).rotateZ(angle);
        b.decalZ(tape, 0, 0, 0, len, w);
        b.pop();
    }

    protected static float[] nz() {
        return v(0, 0, 1);
    }

    // =================================================================== 2.0.0 : deux textures par modele
    /**
     * Ombre ambiante croisee de deux maillages : l'un dans l'atlas du decor, l'autre dans la feuille des salles
     * (chacun ombre l'autre), puis construction des deux.
     */
    protected static Mesh[] bakePair(MeshBuilder atlas, MeshBuilder sheet, float voxel, float dist, float strength, float groundY) {
        Mesh ra = atlas.build(), rs = sheet.build();
        fr.heraliz.casino.client.render.mesh.Occlusion.bake(atlas, voxel, dist, strength, groundY, rs);
        fr.heraliz.casino.client.render.mesh.Occlusion.bake(sheet, voxel, dist, strength, groundY, ra);
        return new Mesh[]{atlas.build(), sheet.build()};
    }

    /** Dessine un maillage de l'atlas du decor puis un maillage de la feuille des salles. */
    protected static void solid2(Pose p, DrawList out, Mesh atlas, Mesh sheet, float glow) {
        out.solid(p, atlas, glow);
        out.sheet(DecorAtlas.SALLES);
        out.solid(p, sheet, glow);
        out.sheet(null);
    }

    /**
     * Grille de petits quads blancs dans le plan z (face +Z), pour une lumiere qui lave un mur : la couleur des
     * sommets (shade) dessine la tache de lumiere, la grille lui donne assez de sommets pour etre douce.
     */
    protected static void washGrid(MeshBuilder w, float x0, float y0, float x1, float y1, float z, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float xa = x0 + (x1 - x0) * i / nx, xb = x0 + (x1 - x0) * (i + 1) / nx;
                float ya = y0 + (y1 - y0) * j / ny, yb = y0 + (y1 - y0) * (j + 1) / ny;
                w.face(DecorAtlas.WHITE, NZ, p(xa, ya, z), p(xb, ya, z), p(xb, yb, z), p(xa, yb, z));
            }
        }
    }

    /** Maillage de la feuille des salles (calque lumineux "glow"). */
    protected static void sheetSolid(Pose p, DrawList out, Mesh sheet, float glow) {
        out.sheet(DecorAtlas.SALLES);
        out.solid(p, sheet, glow);
        out.sheet(null);
    }

    /** Maillage additif de la feuille des salles. */
    protected static void sheetAdditive(Pose p, DrawList out, Mesh sheet, int rgb, float a) {
        out.sheet(DecorAtlas.SALLES);
        out.additive(p, sheet, rgb, a);
        out.sheet(null);
    }
}
