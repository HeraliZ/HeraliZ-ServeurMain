package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Noise;
import fr.heraliz.crates.client.render.mesh.Occlusion;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.client.render.scene.KeySmith;
import fr.heraliz.crates.client.render.scene.Padlock;
import fr.heraliz.crates.client.render.scene.Pose;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse de Noël (22/11, demande du propriétaire : « pas de modèle animé, un sapin avec étoile, guirlande, cadeaux au sol
 * et neige ») : un sapin à cinq étages de branches poudrées de neige, une guirlande d'ampoules de couleur en festons sous
 * chaque étage, des boules au bout des branches et l'étoile d'or au sommet ; au pied, sur un tapis de neige, des paquets
 * enrubannés, le plus grand (devant) fermé par le cadenas. Modèle fixe : rien ne bouge au repos (cuit une fois,
 * StaticCrate), les ampoules et l'étoile brillent (calque additif).
 */
public final class Sapin extends Design {
    private static final Mat NEEDLES = Mat.MAIN;
    private static final Mat SNOW = Mat.MAIN2;
    private static final Mat PAPER_RED = Mat.TRIM;
    private static final Mat GOLD = Mat.ACCENT;
    private static final Mat PAPER_GREEN = Mat.EMBLEM.withWorld(12);
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat PAPER_BLUE = Mat.DECAL_A.withWorld(12);
    private static final Mat PAPER_STRIPES = Mat.DECAL_B.withWorld(12);
    private static final Mat BARK = Mat.INTERIOR;
    private static final Mat GLASS = Mat.GEM;
    private static final Mat DARK = Mat.DARK;
    private static final Mat RIBBON = Mat.SPECIAL;
    private static final Mat LIGHT = Mat.WHITE;

    private static final int RED = 0xD81E2C, GOLD_RGB = 0xF2C14E, SILVER = 0xDCE3EC, BLUE = 0x2E6FD8, GREEN = 0x2BA84A;
    /** Ampoules de la guirlande, dans l'ordre le long du fil. */
    private static final int[] BULBS = {0xFF3B3B, 0xFFC53A, 0x3BFF6A, 0x3A9BFF, 0xFF5AD2};
    /** Boules : rouge, or, argent, bleu, rouge sombre. */
    private static final int[] BAUBLES = {RED, GOLD_RGB, SILVER, BLUE, 0x9E1426};

    /** Étages de branches : bas, hauteur, rayon au bord, nombre de pointes, décalage des pointes (radians). */
    private static final float[][] TIERS = {
            {6.0f, 11.0f, 12.4f, 13, 0.0f},
            {12.0f, 10.0f, 10.2f, 12, 0.6f},
            {17.6f, 9.0f, 8.0f, 11, 1.1f},
            {22.8f, 8.0f, 5.9f, 9, 0.3f},
            {27.6f, 8.6f, 3.9f, 8, 0.9f},
    };
    /**
     * Profil d'un étage, du dessous au sommet : fraction du rayon, hauteur (lignes 0 à 2 : px au-dessus du bas de l'étage ;
     * ensuite : part de la hauteur de l'étage), affaissement des pointes (0 à 1).
     */
    private static final float[][] PROFILE = {
            {0.30f, 1.2f, 0f},
            {0.80f, 0.15f, 0.5f},
            {1.00f, -0.55f, 1f},
            {0.74f, 0.30f, 0.6f},
            {0.50f, 0.58f, 0.3f},
            {0.25f, 0.82f, 0.1f},
            {0.04f, 1.00f, 0f},
    };
    /** Affaissement des pointes (px) et part des pointes dans le rayon. */
    private static final float DROOP = 1.4f, TIP_OUT = 0.18f;
    private static final float STAR_Y = 39.4f, STAR_R = 3.4f, STAR_IN = 1.45f;

    /** Cadeaux : centre x, z, largeur, hauteur, profondeur, rotation (degrés), papier (0 rouge, 1 vert, 2 bleu, 3 rayé), ruban, neige dessus. */
    private static final float[][] GIFTS = {
            {8.0f, 19.2f, 7.4f, 5.8f, 6.0f, 0, 0, GOLD_RGB, 0},
            {-0.8f, 16.6f, 5.0f, 4.2f, 5.0f, 24, 1, RED, 1},
            {17.0f, 16.8f, 5.2f, 5.0f, 5.2f, -18, 2, SILVER, 1},
            {13.4f, 22.4f, 3.4f, 2.8f, 3.4f, 12, 3, GREEN, 0},
            {-2.6f, 3.8f, 5.0f, 4.6f, 4.6f, -10, 0, GOLD_RGB, 1},
            {18.6f, 2.6f, 4.4f, 3.6f, 4.4f, 30, 1, GOLD_RGB, 0},
    };

    private final Parts.RockSlab ground = new Parts.RockSlab();
    private Mesh scene = Mesh.EMPTY, bulbs = Mesh.EMPTY, starGlow = Mesh.EMPTY;
    /** Halo des ampoules, un maillage par couleur (la couleur est donnée au dessin : même rendu partout). */
    private final Mesh[] bulbsGlow = new Mesh[BULBS.length];
    /** Point d'accroche du cadenas (haut de la face avant du grand cadeau). */
    private float lockY, lockZ;

    public Sapin() {
        super("noel", 0xFF3B4F, 0xFFD86A);
        keyRgb = 0xFFD86A;
        cavityRgb = 0xFFE9A0;
        cavityAccent = 0xFF3B4F;
        ringY = 50;
        ringR = 24;
        revealY = 56;
        riseFromY = STAR_Y;
        ground.cx = 8;
        ground.cz = 8;
        ground.radius = 16.6f;
        ground.hCenter = 2.0f;
        ground.hEdge = 1.0f;
        ground.bump = 0.5f;
        ground.freq = 0.2f;
        ground.edgeNoise = 1.2f;
        ground.seed = 7;
        ground.planarTop = true;
    }

    private int segs(float n) {
        return Math.max(6, Math.round(n * detail));
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // tapis de neige
        ground.build(b, SNOW, segs(56), 10, 6);
        // tronc
        b.push().translate(8, 0, 8);
        b.cylinder(1.6f, 1.2f, 7.5f, segs(12), BARK, null, null);
        b.pop();
        // étages de branches, neige au bout des branches, boules
        for (int t = 0; t < TIERS.length; t++) {
            tier(b, t);
        }
        // étoile d'or facettée, doublée en croix : elle se voit de tous les côtés
        b.push().translate(8, STAR_Y, 8);
        star(b, STAR_R, STAR_IN, 1.0f, GOLD);
        b.rotateY(90);
        star(b, STAR_R * 0.92f, STAR_IN * 0.92f, 0.8f, GOLD);
        b.pop();
        // tige de l'étoile, plantée dans la cime
        b.push().translate(8, TIERS[TIERS.length - 1][0] + TIERS[TIERS.length - 1][1] - 0.8f, 8);
        b.cylinder(0.45f, 0, 2.4f, segs(8), GOLD, null, null);
        b.pop();
        // guirlande : fil (dans le modèle) ; ampoules à part (allumées)
        MeshBuilder lb = new MeshBuilder();
        MeshBuilder[] gb = new MeshBuilder[BULBS.length];
        for (int c = 0; c < gb.length; c++) gb[c] = new MeshBuilder();
        for (int t = 0; t < TIERS.length - 1; t++) {
            garland(b, lb, gb, t);
        }
        // cadeaux
        for (float[] g : GIFTS) {
            gift(b, g);
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        scene = b.build();
        bulbs = lb.build();
        for (int c = 0; c < gb.length; c++) bulbsGlow[c] = gb[c].build();
        // halo de l'étoile : étoile plate un peu plus grande, des deux côtés
        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(8, STAR_Y, 8);
        flatStar(sg, STAR_R * 1.45f, STAR_IN * 1.7f, 0.62f);
        flatStar(sg, STAR_R * 1.45f, STAR_IN * 1.7f, -0.62f);
        sg.rotateY(90);
        flatStar(sg, STAR_R * 1.3f, STAR_IN * 1.55f, 0.5f);
        flatStar(sg, STAR_R * 1.3f, STAR_IN * 1.55f, -0.5f);
        sg.pop();
        starGlow = sg.build();
        buildLock();
    }

    /** Hauteur d'un étage au rayon relatif f (sur la ligne d'une pointe si tip = 1), entre le bord et le sommet. */
    private static float tierY(float[] t, float f, float tip) {
        for (int i = 3; i < PROFILE.length; i++) {
            float[] a = PROFILE[i - 1], c = PROFILE[i];
            if (f <= a[0] && f >= c[0]) {
                float u = (a[0] - f) / (a[0] - c[0]);
                return lerp(profY(t, a, tip, i - 1), profY(t, c, tip, i), u);
            }
        }
        return t[0] + t[1];
    }

    private static float profY(float[] t, float[] q, float tip, int i) {
        float y = i >= 3 ? t[0] + q[1] * t[1] : t[0] + q[1];
        return y - DROOP * tip * q[2];
    }

    private static float lerp(float a, float b, float u) {
        return a + (b - a) * u;
    }

    /** Pointe de branche (0..1) à l'angle phi : 1 au bout d'une branche, 0 entre deux. */
    private static float tipAt(float[] t, double phi) {
        float c = (float) (0.5 + 0.5 * Math.cos(t[3] * phi + t[4]));
        return c * c * c;
    }

    private void tier(MeshBuilder b, int ti) {
        float[] t = TIERS[ti];
        float R = t[2];
        int nu = segs(t[3] * 7), ni = PROFILE.length;
        float[][][] P = new float[ni][nu + 1][];
        float[][] S = new float[ni][nu + 1], T = new float[ni][nu + 1];
        for (int j = 0; j <= nu; j++) {
            double phi = 2 * Math.PI * (j % nu) / nu;
            float tip = tipAt(t, phi);
            float c = (float) Math.cos(phi), s = (float) Math.sin(phi);
            float n = Noise.fbm(c * 1.7f + ti * 3.1f, ti * 0.7f, s * 1.7f, 3) - 0.5f;
            float len = 0;
            for (int i = 0; i < ni; i++) {
                float[] q = PROFILE[i];
                float r = R * q[0] * (1 + TIP_OUT * tip * q[2] + 0.06f * n);
                float y = profY(t, q, tip, i);
                P[i][j] = v(8 + c * r, y, 8 + s * r);
                if (i > 0) len += (float) Math.hypot(R * (PROFILE[i][0] - PROFILE[i - 1][0]), y - P[i - 1][j][1]);
                S[i][j] = (float) j / nu * (float) (2 * Math.PI * R / 32f);
                T[i][j] = len / 32f;
            }
        }
        b.grid(P, null, S, T, true, NEEDLES, MeshBuilder.Orient.OUT, true);
        int tips = (int) t[3];
        for (int k = 0; k < tips; k++) {
            double phi = (2 * Math.PI * k - t[4]) / t[3];
            float c = (float) Math.cos(phi), s = (float) Math.sin(phi);
            float deg = (float) -Math.toDegrees(phi);
            // neige posée sur la branche, le long de la pente
            float rIn = R * 0.74f, rOut = R * 0.98f * (1 + TIP_OUT * 0.8f);
            float yIn = tierY(t, 0.74f, 1), yOut = tierY(t, 0.98f, 1);
            float slope = (float) Math.toDegrees(Math.atan2(yOut - yIn, rOut - rIn));
            float rm = (rIn + rOut) / 2;
            b.push().translate(8 + c * rm, (yIn + yOut) / 2 + 0.12f, 8 + s * rm).rotateY(deg).rotateZ(slope);
            b.blob((rOut - rIn) * 0.5f, 0.3f, 0.45f + R * 0.05f, 0.8f, 0.35f, 2.2f, ti * 17 + k, segs(9), SNOW);
            b.pop();
            // boule au bout d'une branche sur deux (pas sur l'étage du haut)
            if (ti < TIERS.length - 1 && (k + ti) % 2 == 0) {
                int col = BAUBLES[(k * 3 + ti) % BAUBLES.length];
                float rb = 0.85f + 0.09f * R / 4f;
                float rt = R * (1 + TIP_OUT) * 0.97f;
                float yt = profY(t, PROFILE[2], 1, 2) - 0.55f - rb;
                b.push().translate(8 + c * rt, yt, 8 + s * rt);
                b.colorRgb(col);
                b.sphere(rb, segs(10), segs(7), GLASS);
                b.color(1);
                b.cylinder(rb * 0.32f, rb * 0.82f, rb * 1.18f, segs(6), GOLD, null, GOLD);
                b.pop();
            }
        }
    }

    /** Festons de la guirlande sous l'étage ti (d'une pointe à l'autre) et ses ampoules. */
    private void garland(MeshBuilder wire, MeshBuilder lb, MeshBuilder[] gb, int ti) {
        float[] t = TIERS[ti];
        float R = t[2];
        int tips = (int) t[3];
        float sag = 0.9f + 0.08f * R;
        float yTip = profY(t, PROFILE[2], 1, 2) + 0.35f;
        int per = 8;
        float[][] path = new float[tips * per + 1][];
        float[] radii = new float[path.length];
        int bulb = ti;
        for (int k = 0; k < tips; k++) {
            double a0 = (2 * Math.PI * k - t[4]) / t[3], a1 = (2 * Math.PI * (k + 1) - t[4]) / t[3];
            for (int i = 0; i < per; i++) {
                float u = (float) i / per;
                double phi = a0 + (a1 - a0) * u;
                float r = R * (1 + TIP_OUT * 0.85f) + 0.2f;
                float y = yTip - sag * (float) Math.sin(Math.PI * u);
                path[k * per + i] = v(8 + (float) Math.cos(phi) * r, y, 8 + (float) Math.sin(phi) * r);
                radii[k * per + i] = 0.12f;
            }
            for (int q = 1; q <= 3; q++) {
                float u = q / 4f;
                double phi = a0 + (a1 - a0) * u;
                float r = R * (1 + TIP_OUT * 0.85f) + 0.2f;
                float y = yTip - sag * (float) Math.sin(Math.PI * u) - 0.5f;
                float x = 8 + (float) Math.cos(phi) * r, z = 8 + (float) Math.sin(phi) * r;
                int ci = bulb++ % BULBS.length;
                // ampoule de verre teinté (sans calque lumineux : sa lumière est le halo de sa couleur)
                lb.push().translate(x, y, z);
                lb.colorRgb(BULBS[ci]);
                lb.blob(0.34f, 0.46f, 0.34f, 1f, 0f, 1f, 0, segs(8), GLASS);
                lb.pop();
                gb[ci].push().translate(x, y, z);
                gb[ci].blob(0.6f, 0.74f, 0.6f, 1f, 0f, 1f, 0, segs(8), LIGHT);
                gb[ci].pop();
            }
        }
        path[path.length - 1] = path[0];
        radii[radii.length - 1] = radii[0];
        wire.colorRgb(0x1E4A26);
        wire.tube(path, radii, segs(5), DARK, false, false, 0, false);
        wire.color(1);
    }

    /** Étoile à cinq branches, facettée (pointes dans le plan XY, sommets avant et arrière à ±depth), origine au centre. */
    private static void star(MeshBuilder b, float r1, float r2, float depth, Mat m) {
        float[][] o = outline(r1, r2);
        float[] front = v(0, 0, depth), back = v(0, 0, -depth);
        for (int i = 0; i < 10; i++) {
            float[] a = o[i], c = o[(i + 1) % 10];
            facet(b, m, front, a, c);
            facet(b, m, back, c, a);
        }
    }

    /** Étoile plate (calque lumineux) à la profondeur z. */
    private static void flatStar(MeshBuilder b, float r1, float r2, float z) {
        float[][] o = outline(r1, r2);
        float[] c = v(0, 0, z);
        for (int i = 0; i < 10; i++) {
            float[] a = v(o[i][0], o[i][1], z), d = v(o[(i + 1) % 10][0], o[(i + 1) % 10][1], z);
            if (z >= 0) b.face(LIGHT, v(0, 0, 1), c, a, d);
            else b.face(LIGHT, v(0, 0, -1), c, d, a);
        }
    }

    private static float[][] outline(float r1, float r2) {
        float[][] o = new float[10][];
        for (int k = 0; k < 10; k++) {
            double a = Math.PI / 2 + k * Math.PI / 5;
            float r = k % 2 == 0 ? r1 : r2;
            o[k] = v((float) Math.cos(a) * r, (float) Math.sin(a) * r, 0);
        }
        return o;
    }

    private static void facet(MeshBuilder b, Mat m, float[] apex, float[] a, float[] c) {
        float[] e1 = {a[0] - apex[0], a[1] - apex[1], a[2] - apex[2]};
        float[] e2 = {c[0] - apex[0], c[1] - apex[1], c[2] - apex[2]};
        float[] n = {e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]};
        float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (l < 1e-6f) return;
        b.face(m, v(n[0] / l, n[1] / l, n[2] / l), apex, a, c);
    }

    /** Un cadeau : boîte de papier, deux rubans croisés, nœud, parfois de la neige dessus. */
    private void gift(MeshBuilder b, float[] g) {
        float cx = g[0], cz = g[1], w = g[2], h = g[3], d = g[4];
        Mat paper = switch ((int) g[6]) {
            case 1 -> PAPER_GREEN;
            case 2 -> PAPER_BLUE;
            case 3 -> PAPER_STRIPES;
            default -> PAPER_RED;
        };
        int ribbon = (int) g[7];
        float y0 = ground.top(cx, cz) - 0.35f;
        b.push().translate(cx, y0, cz).rotateY(g[5]);
        b.bevelBox(-w / 2, 0, -d / 2, w / 2, h, d / 2, 0.18f, paper);
        float rw = Math.max(0.45f, w * 0.12f), e = 0.06f;
        b.colorRgb(ribbon);
        b.box(-rw / 2, 0, -d / 2 - e, rw / 2, h + e, d / 2 + e, RIBBON);
        b.box(-w / 2 - e, 0, -rw / 2, w / 2 + e, h + e, rw / 2, RIBBON);
        // nœud : deux boucles, un nœud central
        float k = Math.min(w, d) / 6f;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(sd * 0.95f * k, h + 0.55f * k, 0).rotateY(sd * 18f).rotateZ(sd * -28f).scale(k);
            b.torusXY(0.95f, 0.26f, segs(12), segs(5), RIBBON);
            b.pop();
        }
        b.push().translate(0, h + 0.3f * k, 0);
        b.sphere(0.42f * k, segs(8), segs(6), RIBBON);
        b.pop();
        b.color(1);
        if (g[8] > 0) {
            b.push().translate(0, h + 0.05f, 0);
            b.blob(w * 0.4f, 0.32f, d * 0.4f, 0.55f, 0.2f, 1.4f, (int) (cx * 7 + cz), segs(10), SNOW);
            b.pop();
        }
        b.pop();
        if (g[0] == GIFTS[0][0] && g[1] == GIFTS[0][1]) {
            lockY = y0 + h - 0.45f;
            lockZ = cz + d / 2 + 0.38f;
        }
    }

    private void buildLock() {
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.DISC;
        ps.w = 3.0f;
        ps.h = 3.0f;
        ps.d = 1.2f;
        ps.height = 1.3f;
        ps.halfSpan = 0.8f;
        ps.wire = 0.3f;
        ps.body = GOLD;
        ps.face = LOCKFACE;
        ps.shackle = GOLD;
        ps.trim = GOLD;
        ps.hole = DARK;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(GOLD, GOLD, 0.5f, (kb, kg) -> {
            // tête de clé : petite étoile d'or facettée, son halo
            kb.push().translate(0, 2.1f, 0);
            star(kb, 2.1f, 0.9f, 0.6f, GOLD);
            kb.pop();
            kg.push().translate(0, 2.1f, 0);
            flatStar(kg, 2.8f, 1.3f, 0.4f);
            flatStar(kg, 2.8f, 1.3f, -0.4f);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== dessin
    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        out.solid(p, scene, gl);
        out.solid(p, bulbs, gl);
        for (int c = 0; c < BULBS.length; c++) {
            out.additive(p, bulbsGlow[c], BULBS[c], 0.5f);
        }
        out.additive(p, starGlow, 0xFFD98A, 0.6f);
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, lockY, lockZ);
    }
}
