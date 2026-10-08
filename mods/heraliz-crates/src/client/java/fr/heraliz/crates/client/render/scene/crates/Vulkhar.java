package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
import fr.heraliz.crates.client.render.mesh.Noise;
import fr.heraliz.crates.client.render.mesh.Occlusion;
import fr.heraliz.crates.client.render.scene.CommonFx;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.client.render.scene.Ease;
import fr.heraliz.crates.client.render.scene.FxSink;
import fr.heraliz.crates.client.render.scene.Geo;
import fr.heraliz.crates.client.render.scene.KeySmith;
import fr.heraliz.crates.client.render.scene.Padlock;
import fr.heraliz.crates.client.render.scene.Pose;
import fr.heraliz.crates.client.render.scene.Seq;
import fr.heraliz.crates.registry.Snd;
import fr.heraliz.crates.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.add;
import static fr.heraliz.crates.client.render.scene.Geo.mul;
import static fr.heraliz.crates.client.render.scene.Geo.norm;
import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Vulkhar, dragon de feu : oeuf de dragon ecaille pose sur un rocher volcanique, agrippe par quatre griffes,
 * ailes repliees, cornes, coquille fendue dont le dessus s'envole a l'ouverture (ailes deployees,
 * tornade de feu, rochers de lave en orbite).
 */
public final class Vulkhar extends Design {
    private static final Mat ROCK = Mat.MAIN;
    private static final Mat SCALES = Mat.MAIN2;
    private static final Mat HORN = Mat.TRIM.sub(0, 0, 0.5f, 1);
    private static final Mat CLAW = Mat.TRIM.sub(0.5f, 0, 1, 1);
    private static final Mat GOLD = Mat.ACCENT.sub(0, 0, 0.5f, 1);
    private static final Mat GOLD_PLAIN = Mat.ACCENT.sub(0.5f, 0, 1, 1);
    private static final Mat WING = Mat.EMBLEM.withWorld(16);
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat IRON = Mat.DECAL_A.withWorld(16);
    private static final Mat EMBERS = Mat.DECAL_B.withWorld(16);
    private static final Mat MAGMA = Mat.INTERIOR;
    private static final Mat CRYSTAL = Mat.GEM;
    private static final Mat STEEL = Mat.DARK;
    private static final Mat LAVA = Mat.SPECIAL;

    private static final float EGG_R = 10.6f, EGG_H = 24f, EGG_Y0 = 1.8f, SHELL = 0.75f;
    /** Pivot du dessus de la coquille. */
    private static final float CAP_Y = 21.5f;
    private static final float HANG_Y = 17.0f, HANG_Z = 20.0f;

    private Mesh base = Mesh.EMPTY, shellIn = Mesh.EMPTY, nest = Mesh.EMPTY, cap = Mesh.EMPTY, capIn = Mesh.EMPTY;
    private Mesh crackLow = Mesh.EMPTY, crackHigh = Mesh.EMPTY, crystalsGlow = Mesh.EMPTY;
    private final Mesh[] rocks = new Mesh[6];

    public Vulkhar() {
        super("vulkhar", 0xFF6A1A, 0xFFC04A);
        ringY = 40;
        ringR = 22;
        revealY = 47;
        riseFromY = 16;
        lockFloorY = 3.6f;
    }

    // =================================================================== forme de l'oeuf
    static float eggR(float t) {
        if (t <= 0 || t >= 1) return 0;
        return EGG_R * (float) Math.pow(Math.sin(Math.PI * t), 0.8) * (1 + 0.14f * (0.42f - t));
    }

    static float crackT(float phi) {
        float u = phi / 360f * 9f;
        float tri = Math.abs((u - (float) Math.floor(u)) - 0.5f) * 2f;
        float n = Noise.noise(phi * 0.05f, 3.1f, 0.7f);
        return 0.665f + 0.034f * (tri - 0.5f) * 2f + 0.012f * n;
    }

    static float[] egg(float phi, float t, float inset) {
        double a = Math.toRadians(phi);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float bump = 1 + 0.022f * Noise.fbm(c * 2.2f + 5, t * 4f, s * 2.2f, 3);
        float r = Math.max(0.01f, eggR(t) * bump - inset);
        return new float[]{8 + c * r, EGG_Y0 + EGG_H * t, 8 + s * r};
    }

    /** Surface de la coquille : partie basse (bas -> fissure) ou chapeau (fissure -> sommet). */
    private static void shell(MeshBuilder b, boolean capPart, float inset, Mat m, boolean inward, int nu, int nv) {
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int j = 0; j <= nu; j++) {
            float phi = 360f * (j % nu) / nu;
            float tc = crackT(phi);
            for (int i = 0; i <= nv; i++) {
                float vv = (float) i / nv;
                float t = capPart ? tc + (0.9995f - tc) * (float) Math.sin(vv * Math.PI / 2) : 0.03f + (tc - 0.03f) * vv;
                P[i][j] = egg(phi, t, inset);
                S[i][j] = (float) j / nu * 2f;
                T[i][j] = (EGG_Y0 + EGG_H - P[i][j][1]) / 32f;
            }
        }
        b.grid(P, null, S, T, true, m, inward ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT, true);
    }

    /** Tranche de la coquille le long de la fissure. */
    private static void rim(MeshBuilder b, boolean up, int nu) {
        for (int j = 0; j < nu; j++) {
            float p0 = 360f * j / nu, p1 = 360f * (j + 1) / nu;
            float t0 = crackT(p0), t1 = crackT(p1);
            float[] o0 = egg(p0, t0, 0), o1 = egg(p1, t1, 0), i0 = egg(p0, t0, SHELL), i1 = egg(p1, t1, SHELL);
            float[] n = norm(Geo.cross(Geo.sub(o1, o0), Geo.sub(i0, o0)));
            if ((n[1] < 0) == up) n = mul(n, -1);
            b.face(HORN, n, o0, o1, i1, i0);
        }
    }

    /** Liseré lumineux de la fissure (degrade par couleur de sommet : intense sur la fissure, nul au bord). */
    private static Mesh crackBand(boolean capPart, int nu) {
        MeshBuilder b = new MeshBuilder();
        float w = 0.045f;
        float[] dt = capPart ? new float[]{0, w} : new float[]{-w, 0};
        float[] k = capPart ? new float[]{1, 0} : new float[]{0, 1};
        for (int j = 0; j < nu; j++) {
            float p0 = 360f * j / nu, p1 = 360f * (j + 1) / nu;
            float[] a0 = egg(p0, crackT(p0) + dt[0], -0.09f), a1 = egg(p1, crackT(p1) + dt[0], -0.09f);
            float[] b0 = egg(p0, crackT(p0) + dt[1], -0.09f), b1 = egg(p1, crackT(p1) + dt[1], -0.09f);
            float[] n = norm(Geo.sub(a0, v(8, a0[1], 8)));
            float[][] pts = {a0, a1, b1, b0};
            float[][] uv = Mat.WHITE.project(pts, n, null);
            float[][] nr = {n, n, n, n};
            float[][] col = {v(k[0], k[0], k[0]), v(k[0], k[0], k[0]), v(k[1], k[1], k[1]), v(k[1], k[1], k[1])};
            b.polyColored(pts, uv, nr, n, col);
        }
        return b.build();
    }

    // =================================================================== rocher
    private static float rockEdge(float phi) {
        double a = Math.toRadians(phi);
        return 15.0f + 1.0f * Noise.fbm((float) Math.cos(a) * 1.3f + 11, 0.3f, (float) Math.sin(a) * 1.3f, 3);
    }

    static float rockTop(float x, float z) {
        float dx = x - 8, dz = z - 8;
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        float k = Math.min(1f, r / 15.5f);
        return 4.2f - 1.4f * k * k + 0.75f * Noise.fbm(x * 0.16f + 3, 0.5f, z * 0.16f - 2, 4);
    }

    private void rockBase(MeshBuilder b) {
        int nu = seg(48), nv = seg(10);
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int j = 0; j <= nu; j++) {
            float phi = 360f * (j % nu) / nu;
            float edge = rockEdge(phi);
            double a = Math.toRadians(phi);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int i = 0; i <= nv; i++) {
                float vv = (float) i / nv;
                float r, y;
                if (vv <= 0.72f) {
                    r = edge * (vv / 0.72f);
                    float x = 8 + c * r, z = 8 + s * r;
                    y = rockTop(x, z);
                } else {
                    float w = (vv - 0.72f) / 0.28f;
                    float x = 8 + c * edge, z = 8 + s * edge;
                    float top = rockTop(x, z);
                    r = edge * (1 + 0.05f * (float) Math.sin(w * Math.PI)) + 0.6f * w;
                    y = top * (float) Math.pow(1 - w, 0.8);
                }
                P[i][j] = v(8 + c * r, y, 8 + s * r);
                S[i][j] = (float) j / nu * 3f;
                T[i][j] = vv * 0.62f;
            }
        }
        b.grid(P, null, S, T, true, ROCK, MeshBuilder.Orient.OUT, true);
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        rockBase(b);
        // la grille du rocher est orientee vers le haut : on verifie via une face de reference
        shell(b, false, 0, SCALES, false, seg(44), seg(16));
        rim(b, true, seg(44));
        // griffes : doigt ecaille qui sort du rocher + talon noir recourbe qui agrippe l'oeuf
        for (int k = 0; k < 4; k++) {
            float phi = 45 + 90 * k;
            float[] bp = Geo.polar(8, 8, phi, 14.4f, 0);
            float yb = rockTop(bp[0], bp[2]);
            float[][] finger = {
                    Geo.polar(8, 8, phi, 15.2f, yb - 1.2f),
                    Geo.polar(8, 8, phi + 1, 14.6f, yb + 1.8f),
                    Geo.polar(8, 8, phi + 2, 13.4f, 7.4f),
                    Geo.polar(8, 8, phi + 3, 12.3f, 9.4f)};
            Geo.horn(b, finger, 2.5f, 1.55f, 1f, seg(3), seg(7), SCALES);
            b.push().translate(finger[3][0], finger[3][1], finger[3][2]);
            b.sphere(1.6f, seg(7), seg(4), SCALES);
            b.pop();
            float[][] talon = {
                    Geo.polar(8, 8, phi + 3, 12.3f, 9.4f),
                    Geo.polar(8, 8, phi + 4, 11.5f, 11.8f),
                    Geo.polar(8, 8, phi + 5, 10.9f, 14.0f),
                    Geo.polar(8, 8, phi + 6, 10.3f, 15.6f),
                    Geo.polar(8, 8, phi + 6, 9.3f, 16.4f)};
            Geo.horn(b, talon, 1.5f, 0.06f, 1.2f, seg(3), seg(7), CLAW);
            // ecailles de l'articulation
            for (int q = 0; q < 2; q++) {
                float[] kp = Geo.polar(8, 8, phi + (q - 0.5f) * 12, 15.6f, yb + 0.2f);
                b.push().translate(kp[0], kp[1], kp[2]).rotateY(-phi).rotateZ(20);
                b.blob(1.5f, 1.0f, 1.3f, 0.9f, 0.15f, 1.3f, k * 3 + q, seg(7), SCALES);
                b.pop();
                float[] tp = Geo.polar(8, 8, phi + (q - 0.5f) * 20, 16.4f, yb - 0.2f);
                Geo.horn(b, new float[][]{tp, Geo.polar(8, 8, phi + (q - 0.5f) * 24, 17.8f, yb + 0.4f), Geo.polar(8, 8, phi + (q - 0.5f) * 26, 18.6f, yb - 0.6f)},
                        0.7f, 0.05f, 1.2f, seg(2), seg(5), CLAW);
            }
        }
        // epines dorsales (partie basse)
        for (int k = 0; k < 4; k++) {
            float t = 0.2f + k * 0.11f;
            spine(b, 270, t, 1.0f + k * 0.12f, 2.6f + k * 0.4f);
        }
        // cristaux de feu dans le rocher
        float[][] cr = {{25, 12.8f, 1.1f, 4.2f}, {32, 13.6f, 0.8f, 3.0f}, {150, 13.2f, 1.0f, 3.8f}, {160, 12.6f, 0.7f, 2.6f},
                {215, 13.4f, 0.9f, 3.2f}, {300, 13.0f, 1.2f, 4.6f}, {110, 14.0f, 0.7f, 2.4f}};
        MeshBuilder cg = new MeshBuilder();
        for (float[] c : cr) {
            float[] p = Geo.polar(8, 8, c[0], c[1], 0);
            p[1] = rockTop(p[0], p[2]) - 0.6f;
            for (MeshBuilder mb : new MeshBuilder[]{b, cg}) {
                mb.push().translate(p[0], p[1], p[2]).rotateY(-c[0]).rotateZ(-28 - c[3] * 3);
                if (mb == b) {
                    mb.crystal(c[2], c[3], c[2] * 1.4f, 6, CRYSTAL);
                } else {
                    mb.scale(1.12f);
                    mb.crystal(c[2], c[3], c[2] * 1.4f, 6, Mat.WHITE);
                }
                mb.pop();
            }
        }
        crystalsGlow = cg.build();
        // mares de lave
        float[][] pools = {{75, 12.6f, 2.2f}, {195, 12.2f, 1.8f}, {335, 12.9f, 1.6f}, {260, 13.4f, 1.3f}};
        for (float[] pl : pools) {
            float[] p = Geo.polar(8, 8, pl[0], pl[1], 0);
            float y = rockTop(p[0], p[2]) + 0.12f;
            b.push().translate(p[0], y, p[2]).scale(1, 1, 0.75f).rotateY(pl[0]);
            b.disc(pl[2], 0, seg(12), LAVA, true);
            b.pop();
        }
        // agrafe doree et anneau du cadenas
        b.push().translate(8, 14.3f, 17.75f);
        b.slab(MeshBuilder.shield(5.4f, 6.2f, 6), 1.1f, 0.35f, true, GOLD, GOLD, GOLD);
        b.pop();
        for (float rx : new float[]{-1.7f, 1.7f}) {
            b.push().translate(8 + rx, 16.2f, 18.35f).rotateX(90);
            b.rivet(0.45f, 0.3f, GOLD);
            b.pop();
        }
        b.push().translate(8, HANG_Y - 0.7f, 18.4f).rotateX(90);
        b.cylinder(0.45f, 0, 1.2f, 10, GOLD_PLAIN, null, null);
        b.pop();
        b.push().translate(8, HANG_Y - 0.7f, HANG_Z).rotateZ(90);
        b.torus(1.05f, 0.32f, 16, 7, 0, 360, GOLD_PLAIN);
        b.pop();
        Occlusion.bake(b, 0.5f, 6f, 0.6f, 0f);
        base = b.build();

        // interieur de la coquille (lave) + nid
        MeshBuilder in = new MeshBuilder();
        shell(in, false, SHELL, MAGMA, true, seg(44), seg(12));
        Occlusion.bake(in, 0.5f, 5f, 0.45f, Float.NaN, base);
        shellIn = in.build();

        MeshBuilder n = new MeshBuilder();
        float nestY = 8.4f;
        float nestR = eggR((nestY - EGG_Y0) / EGG_H) - SHELL - 0.15f;
        n.surface((u, vv) -> {
            double a = u * Math.PI * 2;
            float r = nestR * vv;
            float x = 8 + (float) Math.cos(a) * r, z = 8 + (float) Math.sin(a) * r;
            float y = nestY + 1.2f * (1 - vv * vv) + 0.5f * Noise.fbm(x * 0.5f, 1.7f, z * 0.5f, 3);
            return v(x, y, z);
        }, seg(24), seg(6), EMBERS, 1f, 0.6f, true, MeshBuilder.Orient.OUT);
        Random r = new Random(42);
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = 2.5f + r.nextFloat() * (nestR - 3.5f);
            float x = 8 + (float) Math.cos(a) * rr, z = 8 + (float) Math.sin(a) * rr;
            float y = nestY + 1.2f * (1 - (rr / nestR) * (rr / nestR)) + 0.3f;
            n.push().translate(x, y, z).rotateY(r.nextFloat() * 360).rotateX(r.nextFloat() * 40 - 20);
            n.cylinder(0.95f, 0, 0.26f, seg(9), GOLD_PLAIN, GOLD_PLAIN, GOLD_PLAIN);
            n.pop();
        }
        // coeur de cristaux
        float[][] heart = {{0, 0, 1.5f, 6.5f, 0}, {70, 1.6f, 1.0f, 4.2f, 24}, {170, 1.5f, 0.9f, 3.6f, 28},
                {260, 1.7f, 1.1f, 4.8f, 22}, {330, 1.4f, 0.8f, 3.2f, 30}};
        for (float[] h : heart) {
            float[] p = Geo.polar(8, 8, h[0], h[1], nestY + 1.6f);
            n.push().translate(p[0], p[1], p[2]).rotateY(-h[0]).rotateZ(-h[4]);
            n.crystal(h[2], h[3], h[2] * 1.5f, 6, CRYSTAL);
            n.pop();
        }
        Occlusion.bake(n, 0.4f, 4f, 0.4f, Float.NaN, base);
        nest = n.build();

        // dessus de la coquille (chapeau) : cornes, epines
        MeshBuilder c = new MeshBuilder();
        shell(c, true, 0, SCALES, false, seg(44), seg(8));
        rim(c, false, seg(44));
        for (float phi : new float[]{222, 318}) {
            float t = 0.8f;
            float[] base0 = egg(phi, t, 0.6f);
            double a = Math.toRadians(phi);
            float[] R = v((float) Math.cos(a), 0, (float) Math.sin(a));
            float[] U = v(0, 1, 0), B = v(0, 0, -1);
            float[][] ctrl = {
                    base0,
                    add(base0, add(mul(R, 2.2f), add(mul(U, 2.4f), mul(B, 1.0f)))),
                    add(base0, add(mul(R, 4.4f), add(mul(U, 4.4f), mul(B, 4.4f)))),
                    add(base0, add(mul(R, 4.8f), add(mul(U, 3.6f), mul(B, 8.4f)))),
                    add(base0, add(mul(R, 4.0f), add(mul(U, 1.4f), mul(B, 11.2f))))};
            Geo.horn(c, ctrl, 1.75f, 0.1f, 1.3f, seg(4), seg(9), HORN);
        }
        for (int k = 0; k < 3; k++) {
            spine(c, 270, 0.74f + k * 0.075f, 1.1f - k * 0.15f, 3.4f - k * 0.5f);
        }
        spine(c, 90, 0.9f, 0.6f, 1.6f);
        Occlusion.bake(c, 0.4f, 5f, 0.5f, Float.NaN);
        cap = c.build();
        MeshBuilder ci = new MeshBuilder();
        shell(ci, true, SHELL, MAGMA, true, seg(44), seg(6));
        capIn = ci.build();
        crackLow = crackBand(false, seg(64));
        crackHigh = crackBand(true, seg(64));

        for (int i = 0; i < rocks.length; i++) {
            MeshBuilder rb = new MeshBuilder();
            float s = 1.3f + (i * 0.37f) % 1.1f;
            rb.rock(s * 1.1f, s * 0.9f, s, 0.35f, 1.4f, i + 3, seg(10), i % 2 == 0 ? ROCK : MAGMA);
            rocks[i] = rb.build();
        }

        // cadenas : ecusson en fer noirci, plaque "oeil de dragon"
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.SHIELD;
        st.w = 6.6f;
        st.h = 7.4f;
        st.d = 3.0f;
        st.bevel = 0.5f;
        st.height = 4.4f;
        st.halfSpan = 2.0f;
        st.wire = 0.7f;
        st.body = IRON;
        st.face = PLATE;
        st.shackle = STEEL;
        st.trim = GOLD_PLAIN;
        st.hole = IRON;
        st.decorate = (mb, s) -> {
            for (int side = -1; side <= 1; side += 2) {
                Geo.cone(mb, v(side * 2.6f, 3.2f, 0), v(side * 0.8f, 1, -0.2f), 2.2f, 0.6f, 8, HORN);
                mb.push().translate(side * 2.3f, -1.6f, s.d / 2 - 0.05f).rotateX(90);
                mb.rivet(0.4f, 0.28f, GOLD);
                mb.pop();
            }
        };
        lock = new Padlock(st);
        lock.build(detail);

        // cle : dragon enroule autour d'une gemme de feu (tete en haut a gauche, queue qui s'enroule)
        Mesh[] k = KeySmith.build(GOLD_PLAIN, IRON, 0.55f, (kb, kg) -> {
            int n2 = 30;
            float cy = 3.7f, R = 3.1f;
            float[][] path = new float[n2][];
            float[] radii = new float[n2];
            for (int i = 0; i < n2; i++) {
                float u = (float) i / (n2 - 1);
                double a = Math.toRadians(112 + u * 335);
                float rr = R * (1 - 0.1f * u);
                path[i] = v((float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, (float) Math.sin(u * Math.PI * 4) * 0.2f);
                radii[i] = 0.14f + 0.72f * (float) Math.pow(1 - u, 0.7);
            }
            kb.tube(path, radii, 10, GOLD, true, true, 0, false);
            // crete dorsale
            for (int i = 3; i < n2 - 6; i += 3) {
                float[] d = norm(Geo.sub(path[i], v(0, cy, 0)));
                Geo.cone(kb, path[i], d, 0.9f * radii[i] + 0.2f, 0.28f, 5, CLAW);
            }
            // tete : dirigee vers l'exterieur, en haut a gauche
            float[] h0 = path[0];
            float[] dir = norm(Geo.sub(path[0], path[1]));
            float ang = (float) Math.toDegrees(Math.atan2(dir[1], dir[0]));
            kb.push().translate(h0[0], h0[1], h0[2]).rotateZ(ang);
            kb.blob(1.25f, 0.95f, 0.9f, 1f, 0.04f, 2f, 3, 12, GOLD_PLAIN);
            kb.push().translate(1.25f, -0.2f, 0);
            kb.blob(1.05f, 0.55f, 0.62f, 1f, 0, 1, 1, 10, GOLD_PLAIN);
            kb.pop();
            for (int side = -1; side <= 1; side += 2) {
                Geo.horn(kb, new float[][]{v(-0.3f, 0.6f, side * 0.45f), v(-1.3f, 1.3f, side * 0.7f), v(-2.4f, 1.1f, side * 0.8f)},
                        0.34f, 0.04f, 1.2f, 4, 6, CLAW);
                kb.push().translate(0.55f, 0.35f, side * 0.62f);
                kb.sphere(0.24f, 6, 4, CRYSTAL);
                kb.pop();
                kg.push().translate(h0[0], h0[1], h0[2]).rotateZ(ang).translate(0.55f, 0.35f, side * 0.62f);
                kg.sphere(0.34f, 6, 4, Mat.WHITE);
                kg.pop();
            }
            kb.pop();
            kb.push().translate(0, cy, 0);
            kb.gem(1.45f, 0.7f, 1.1f, 8, CRYSTAL);
            kb.rotateX(180);
            kb.gem(1.45f, 0.7f, 1.1f, 8, CRYSTAL);
            kb.pop();
            kg.push().translate(0, cy, 0).scale(1.2f);
            kg.gem(1.45f, 0.7f, 1.1f, 8, Mat.WHITE);
            kg.rotateX(180);
            kg.gem(1.45f, 0.7f, 1.1f, 8, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    private void spine(MeshBuilder b, float phi, float t, float r, float len) {
        float[] p = egg(phi, t, 0.4f);
        double a = Math.toRadians(phi);
        float[] dir = v((float) Math.cos(a), 0.55f, (float) Math.sin(a));
        Geo.cone(b, p, dir, len, r, seg(7), HORN);
    }

    // =================================================================== ailes (dynamiques)
    private static final float[][] SPREAD = {
            {0.8f, 0.55f, -0.12f, 10f},
            {0.3f, 0.95f, 0.05f, 13.5f}, {0.78f, 0.6f, -0.08f, 17.5f}, {0.98f, 0.04f, 0.0f, 16f}, {0.75f, -0.5f, 0.12f, 11.5f}};
    private static final float[][] FOLD = {
            {0.15f, 0.25f, 0.95f, 8.5f},
            {0.3f, -0.25f, 0.9f, 10f}, {0.35f, -0.6f, 0.7f, 12.5f}, {0.35f, -0.85f, 0.4f, 11.5f}, {0.38f, -0.95f, 0.05f, 9f}};

    private Mesh wing(boolean left, float spread, float flap) {
        MeshBuilder b = new MeshBuilder();
        float phiS = left ? 198 : 342;
        float[] S = egg(phiS, 0.56f, -0.4f);
        double a = Math.toRadians(phiS);
        float[] R = v((float) Math.cos(a), 0, (float) Math.sin(a));
        float[] U = v(0, 1, 0);
        float[] mz = v(0, 0, -1);
        float[] B = norm(Geo.sub(mz, mul(R, Geo.dot(R, mz))));
        // battement : rotation autour de l'axe arriere
        float fl = (float) Math.toRadians(flap);
        float[] R2 = norm(add(mul(R, (float) Math.cos(fl)), mul(U, (float) Math.sin(fl))));
        float[] U2 = norm(add(mul(U, (float) Math.cos(fl)), mul(R, -(float) Math.sin(fl))));
        float[][] dirs = new float[5][];
        float[] lens = new float[5];
        for (int i = 0; i < 5; i++) {
            float[] sp = SPREAD[i], fo = FOLD[i];
            float[] ds = add(add(mul(R2, sp[0]), mul(U2, sp[1])), mul(B, sp[2]));
            float[] df = add(add(mul(R, fo[0]), mul(U, fo[1])), mul(B, fo[2]));
            dirs[i] = Geo.slerp(df, ds, spread);
            lens[i] = Ease.lerp(fo[3], sp[3], spread);
        }
        float[] W = add(S, mul(dirs[0], lens[0]));
        float[][] tips = new float[4][];
        for (int i = 0; i < 4; i++) tips[i] = add(W, mul(dirs[i + 1], lens[i + 1]));
        float[] hip = egg(left ? 210 : 330, 0.2f, -0.5f);
        // membranes
        for (int i = 0; i < 4; i++) {
            float[] a0 = W, a1 = tips[i];
            float[] b0 = i < 3 ? W : S, b1 = i < 3 ? tips[i + 1] : hip;
            membrane(b, a0, a1, b0, b1, i, seg(6), seg(5));
        }
        // os
        int rad = seg(7);
        bone(b, S, W, 0.85f, 0.55f, rad);
        for (int i = 0; i < 4; i++) bone(b, W, tips[i], 0.5f, 0.08f, rad);
        b.push().translate(W[0], W[1], W[2]);
        b.sphere(0.8f, seg(7), seg(4), HORN);
        b.pop();
        Geo.cone(b, W, add(mul(U2, 0.8f), mul(R2, 0.4f)), 2.2f, 0.45f, seg(6), HORN);
        return b.buildDynamic();
    }

    private static void bone(MeshBuilder b, float[] p0, float[] p1, float r0, float r1, int radial) {
        float[][] path = new float[6][];
        for (int i = 0; i < 6; i++) path[i] = Geo.lerp(p0, p1, i / 5f);
        b.tube(path, Geo.taper(6, r0, r1, 1f), radial, HORN, false, true, 0, false);
    }

    private static void membrane(MeshBuilder b, float[] a0, float[] a1, float[] b0, float[] b1, int idx, int nu, int nv) {
        float[][][] P = new float[nu + 1][nv + 1][];
        float[][] S = new float[nu + 1][nv + 1];
        float[][] T = new float[nu + 1][nv + 1];
        float[] mid = Geo.lerp(Geo.lerp(a0, b0, 0.5f), Geo.lerp(a1, b1, 0.5f), 0.5f);
        float[] edgeMid = Geo.lerp(a1, b1, 0.5f);
        float[] inward = norm(Geo.sub(Geo.lerp(a0, b0, 0.5f), edgeMid));
        float span = Geo.len(Geo.sub(a1, b1));
        float[] nrm = norm(Geo.cross(Geo.sub(a1, a0), Geo.sub(b1, b0)));
        for (int i = 0; i <= nu; i++) {
            float u = (float) i / nu;
            for (int j = 0; j <= nv; j++) {
                float w = (float) j / nv;
                float[] pa = Geo.lerp(a0, a1, u), pb = Geo.lerp(b0, b1, u);
                float[] p = Geo.lerp(pa, pb, w);
                float sag = span * 0.28f * (float) Math.sin(Math.PI * w) * u * u * u;
                p = add(p, mul(inward, sag));
                float bil = 0.35f * (float) Math.sin(Math.PI * w) * (float) Math.sin(Math.PI * u);
                p = add(p, mul(nrm, bil));
                P[i][j] = p;
                S[i][j] = w * 0.8f + idx * 0.5f;
                T[i][j] = u * 0.9f;
            }
        }
        b.grid(P, null, S, T, false, WING, MeshBuilder.Orient.KEEP, true);
    }

    // =================================================================== animation
    private float capLift(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        if (t < CrateAnim.OPEN) return 0.25f * Ease.bell(t, CrateAnim.UNLOCK + 2, CrateAnim.OPEN);
        if (t < CrateAnim.CLOSE) return Ease.outBack(Ease.range(t, CrateAnim.OPEN, CrateAnim.OPEN + 16), 1.4f);
        return 1 - Ease.inCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM));
    }

    private float wingSpread(CrateState s) {
        float idle = 0.03f + 0.025f * (float) Math.sin(s.time * 0.05f + s.seed * 5);
        if (!s.active) return idle;
        float t = s.t;
        float open = Ease.outBack(Ease.range(t, CrateAnim.OPEN - 2, CrateAnim.OPEN + 14), 1.2f);
        float close = Ease.inOutCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM + 4));
        return Math.max(idle, open * (1 - close));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        // souffle de l'oeuf : legere respiration et tremblement a l'eveil
        float quake = s.active ? (float) Math.sin(s.t * 3.1f) * 0.35f * Ease.bell(s.t, CrateAnim.UNLOCK, CrateAnim.OPEN + 4) : 0;
        p.push().translate(quake, 0, 0);
        out.solid(p, base, gl);
        out.additive(p, crystalsGlow, glow, 0.08f + 0.06f * s.pulse(0.11f) + 0.25f * aw);
        float cav = Seq.cavity(s);
        boolean inside = s.active && s.t > CrateAnim.UNLOCK;
        if (inside) {
            out.solid(p, shellIn, 0.12f + 0.3f * cav);
            out.solid(p, nest, 0.25f + 0.25f * cav);
        }
        float heart = heartbeat(s);
        out.additive(p, crackLow, glow, 0.14f + 0.1f * s.pulse(0.075f) + 0.45f * heart + 1.1f * Ease.bell(s.active ? s.t : 0, CrateAnim.UNLOCK - 2, CrateAnim.OPEN + 8));
        // chapeau
        float lift = capLift(s);
        float hover = s.active && s.t > CrateAnim.OPEN + 16 && s.t < CrateAnim.CLOSE ? (float) Math.sin(s.t * 0.12f) * 0.8f : 0;
        p.push().translate(8, CAP_Y + lift * 14f + hover, 8);
        if (s.active) {
            p.rotY(Ease.range(s.t, CrateAnim.OPEN, CrateAnim.CLOSE) * 170f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.SLAM)));
            p.rotX(-24f * Math.min(1f, lift));
        }
        p.translate(-8, -CAP_Y, -8);
        out.solid(p, cap, gl);
        if (inside) out.solid(p, capIn, 0.15f + 0.3f * cav);
        out.additive(p, crackHigh, glow, 0.14f + 0.1f * s.pulse(0.075f) + 0.45f * heart + 1.1f * aw);
        p.pop();
        // ailes
        float spread = wingSpread(s);
        float flap = s.active ? (float) Math.sin(s.t * 0.22f) * 12f * Ease.range(s.t, CrateAnim.OPEN + 10, CrateAnim.OPEN + 20)
                * (1 - Ease.range(s.t, CrateAnim.CLOSE - 6, CrateAnim.CLOSE)) : 0;
        out.solid(p, wing(true, spread, flap), gl);
        out.solid(p, wing(false, spread, flap), gl);
        p.pop();
    }

    /** Battement de coeur : toutes les ~6 s, la lueur de la fissure pulse deux fois. */
    private float heartbeat(CrateState s) {
        float c = (s.time + s.seed * 120f) % 120f;
        return Ease.bell(c, 0, 5) + 0.6f * Ease.bell(c, 7, 12);
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, HANG_Y, HANG_Z);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        float spin = Seq.spinSpeed(s);
        // rochers de lave en orbite
        float orb = orbitScale(s);
        for (int i = 0; i < rocks.length; i++) {
            if (orb <= 0.001f) break;
            float speed = 0.8f + 0.25f * (i % 3);
            float ang = i * 60f + s.time * speed + (s.active ? s.t * s.t * 0.02f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 19f + (i % 2) * 1.8f + 5f * ex;
            float y = 10f + 3.5f * (float) Math.sin(i * 1.7f + s.time * 0.03f) + 9f * aw;
            p.push().translate(8, 0, 8).rotZ(10f * (float) Math.sin(i * 2.1f)).rotY(ang).translate(rad, y, 0)
                    .rotX(s.time * (2f + i)).rotY(s.time * 1.3f * i).scale(orb);
            out.solid(p, rocks[i], 0.9f + 0.1f * s.pulse(0.2f));
            if (!s.item) out.billboard(p, Sprite.HALO, 0, 0, 0, 4.5f, 0, glow, 0.18f + 0.2f * ex);
            p.pop();
        }
        if (s.item) return;
        // anneau de feu au sol
        if (aw > 0.01f) {
            int n = 16;
            for (int i = 0; i < n; i++) {
                float a = i * 360f / n + s.time * 2.2f;
                float[] q = Geo.polar(8, 8, a, 16.5f, 0);
                float fl = 0.75f + 0.25f * (float) Math.sin(s.time * 0.9f + i * 1.9f);
                out.billboard(p, Sprite.FLAME, q[0], 4.5f + 2.5f * fl, q[2], 4.2f * fl * aw, 0, 0xFFFFFF, 0.8f * aw);
            }
        }
        // halo de chaleur au-dessus de l'oeuf ouvert
        float cav = Seq.cavity(s);
        CommonFx.cavity(s, p, out, this, 8, 9.5f, 8, 7.5f);
        if (cav > 0.01f) {
            out.billboard(p, Sprite.FLARE, 8, 13f, 8, 10f + 4f * spin, s.time * 1.5f, glow, 0.22f * cav);
        }
        // lueur du rocher
        out.ground(p, Sprite.HALO, 8, 0.6f, 8, 22f, 0, glow, 0.25f + 0.1f * s.pulse(0.1f) + 0.3f * aw);
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, 10, 8};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.35f) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = 8 + r.nextFloat() * 8;
            float x = 8 + (float) Math.cos(a) * rr, z = 8 + (float) Math.sin(a) * rr;
            fx.particle(FxSink.EMBER, x, rockTop(x, z) + 0.3f, z, (r.nextFloat() - 0.5f) * 0.1f, 0.35f + r.nextFloat() * 0.35f,
                    (r.nextFloat() - 0.5f) * 0.1f, r.nextInt(4) == 0 ? 0xFFE08A : glow, 1.0f + r.nextFloat() * 0.8f, 40 + r.nextInt(30));
        }
        if (r.nextFloat() < 0.05f) {
            double a = r.nextDouble() * Math.PI * 2;
            fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 12, 4.5f, 8 + (float) Math.sin(a) * 12, 0, 0.25f, 0, 0x2a2420, 3.5f, 60);
        }
        float c = (s.time + s.seed * 120f) % 120f;
        if (c >= 0 && c < 1) {
            for (int i = 0; i < 10; i++) {
                float phi = r.nextFloat() * 360;
                float[] q = egg(phi, crackT(phi), -0.3f);
                fx.particle(FxSink.EMBER, q[0], q[1], q[2], (q[0] - 8) * 0.03f, 0.2f + r.nextFloat() * 0.3f, (q[2] - 8) * 0.03f, glow, 1.2f, 30);
            }
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 2) fx.sound(Snd.FIRE, 8, 16, 8, 0.7f, 0.8f);
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.FIRE, 8, 20, 8, 1.2f, 1.0f);
            fx.sound(Snd.WHOOSH, 8, 20, 8, 1.0f, 0.7f);
            for (int i = 0; i < 40; i++) {
                float phi = r.nextFloat() * 360;
                float[] q = egg(phi, crackT(phi), -0.2f);
                fx.particle(FxSink.FLAME, q[0], q[1], q[2], (q[0] - 8) * 0.08f, 1.2f + r.nextFloat() * 1.5f, (q[2] - 8) * 0.08f, 0xFFFFFF, 1.8f + r.nextFloat() * 1.4f, 14 + r.nextInt(8));
            }
        }
        // tornade de feu en spirale autour de l'oeuf
        if (k >= CrateAnim.OPEN && k < CrateAnim.OPEN + 26) {
            float u = (k - CrateAnim.OPEN) / 26f;
            for (int arm = 0; arm < 3; arm++) {
                double a = Math.toRadians(k * 26f + arm * 120);
                float rad = 15f - 4f * u;
                float y = 2 + 30 * u;
                fx.particle(FxSink.FLAME, 8 + (float) Math.cos(a) * rad, y, 8 + (float) Math.sin(a) * rad,
                        -(float) Math.sin(a) * 0.6f, 0.4f, (float) Math.cos(a) * 0.6f, 0xFFFFFF, 1.9f, 12);
                fx.particle(FxSink.EMBER, 8 + (float) Math.cos(a) * rad, y, 8 + (float) Math.sin(a) * rad,
                        (r.nextFloat() - 0.5f) * 0.6f, 0.5f, (r.nextFloat() - 0.5f) * 0.6f, glow, 1.3f, 30);
            }
        }
        // braises aspirees par la roue
        if (k > CrateAnim.SPIN && k < CrateAnim.STOP && r.nextFloat() < 0.8f) {
            double a = r.nextDouble() * Math.PI * 2;
            fx.particle(FxSink.EMBER, 8 + (float) Math.cos(a) * 10, 10, 8 + (float) Math.sin(a) * 10,
                    (float) Math.cos(a) * 0.3f, 1.1f, (float) Math.sin(a) * 0.3f, r.nextBoolean() ? glow : accent, 1.3f, 30);
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.FIRE, 8, 18, 8, 0.6f, 1.4f);
            for (int i = 0; i < 24; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 11, 17, 8 + (float) Math.sin(a) * 11,
                        (float) Math.cos(a) * 0.5f, 0.2f, (float) Math.sin(a) * 0.5f, 0x3a3230, 4f, 40);
            }
        }
    }
}
