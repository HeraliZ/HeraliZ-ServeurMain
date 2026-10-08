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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.add;
import static fr.heraliz.crates.client.render.scene.Geo.mul;
import static fr.heraliz.crates.client.render.scene.Geo.norm;
import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Sylvorn, esprit de la foret : souche vivante aux racines tentaculaires, champignons luminescents ; sur la coupe
 * du tronc, une couronne de racines tressees enferme le coeur de la foret (graine de cristal).
 * A l'ouverture les racines s'ecartent comme une fleur, la graine s'eleve et une tornade de feuilles tourbillonne.
 */
public final class Sylvorn extends Design {
    private static final Mat FLOOR = Mat.MAIN;
    private static final Mat BARK = Mat.MAIN2;
    private static final Mat ANTLER = Mat.TRIM;
    private static final Mat WOOD = Mat.ACCENT.sub(0, 0, 0.5f, 1);
    private static final Mat BRONZE = Mat.ACCENT.sub(0.5f, 0, 1, 1);
    private static final Mat RINGS = Mat.EMBLEM;
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat LEAF = Mat.DECAL_A;
    private static final Mat CAP = Mat.DECAL_B.withWorld(8);
    private static final Mat HOLLOW = Mat.INTERIOR;
    private static final Mat SEED = Mat.GEM;
    private static final Mat VINE = Mat.DARK;
    private static final Mat BONE = Mat.SPECIAL;

    private static final float RIM_Y = 15.0f, LID_TOP = 22.6f;
    private static final float HANG_Y = 15.3f, HANG_Z = 18.3f;

    private final Parts.RockSlab floor = new Parts.RockSlab();
    private static final int ROOTS = 6;
    private Mesh base = Mesh.EMPTY, cap = Mesh.EMPTY, capsGlow = Mesh.EMPTY;
    private Mesh seed = Mesh.EMPTY, seedGlow = Mesh.EMPTY, stone = Mesh.EMPTY;
    private final Mesh[] roots = new Mesh[ROOTS];
    private final float[][] rootBase = new float[ROOTS][];
    private final float[] rootAz = new float[ROOTS];

    public Sylvorn() {
        super("sylvorn", 0x9BE35A, 0xE8FFB0);
        ringY = 40;
        ringR = 22;
        revealY = 48;
        riseFromY = 20;
        lockFloorY = 3.0f;
        floor.radius = 15.6f;
        floor.hCenter = 2.8f;
        floor.hEdge = 1.8f;
        floor.bump = 0.5f;
        floor.seed = 13;
        floor.planarTop = true;
    }

    // =================================================================== souche
    private static final float[][] STUMP = {{10.9f, 0.2f}, {10.3f, 1.4f}, {9.4f, 3.4f}, {9.0f, 6.5f}, {8.9f, 10.5f}, {9.1f, 13.8f}, {9.25f, RIM_Y}};

    private static float stumpNoise(float x, float y, float z) {
        return 1 + 0.07f * Noise.fbm(x * 0.35f, y * 0.12f, z * 0.35f, 3) + 0.04f * Noise.ridged(x * 0.9f, y * 0.25f, z * 0.9f, 2);
    }

    private void stump(MeshBuilder b, float inset, Mat m, boolean inward, float yMin) {
        int nu = seg(44);
        List<float[]> prof = new ArrayList<>();
        for (float[] q : STUMP) if (q[1] >= yMin - 0.01f) prof.add(q);
        if (yMin > 0.3f) prof.add(0, new float[]{Ease.lerp(STUMP[3][0], STUMP[4][0], 0.5f), yMin});
        int nv = prof.size() - 1;
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int j = 0; j <= nu; j++) {
            float phi = 360f * (j % nu) / nu;
            double a = Math.toRadians(phi);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int i = 0; i <= nv; i++) {
                float[] q = prof.get(i);
                float x0 = c * q[0], z0 = s * q[0];
                float k = stumpNoise(x0, q[1], z0);
                float r = q[0] * k - inset;
                P[i][j] = v(8 + c * r, q[1], 8 + s * r);
                S[i][j] = (float) j / nu * 2f;
                T[i][j] = (RIM_Y - q[1]) / 32f;
            }
        }
        b.grid(P, null, S, T, true, m, inward ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT, true);
    }


    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        floor.build(b, FLOOR, seg(40), seg(8), 4f);
        stump(b, 0, BARK, false, 0);
        // rebord superieur (epaisseur de la souche)
        int nu = seg(44);
        for (int j = 0; j < nu; j++) {
            double a0 = Math.toRadians(360.0 * j / nu), a1 = Math.toRadians(360.0 * (j + 1) / nu);
            float r = STUMP[STUMP.length - 1][0];
            float k0 = stumpNoise((float) Math.cos(a0) * r, RIM_Y, (float) Math.sin(a0) * r);
            float k1 = stumpNoise((float) Math.cos(a1) * r, RIM_Y, (float) Math.sin(a1) * r);
            float[] o0 = v(8 + (float) Math.cos(a0) * r * k0, RIM_Y, 8 + (float) Math.sin(a0) * r * k0);
            float[] o1 = v(8 + (float) Math.cos(a1) * r * k1, RIM_Y, 8 + (float) Math.sin(a1) * r * k1);
            float[] i0 = v(8 + (float) Math.cos(a0) * (r * k0 - 1.4f), RIM_Y, 8 + (float) Math.sin(a0) * (r * k0 - 1.4f));
            float[] i1 = v(8 + (float) Math.cos(a1) * (r * k1 - 1.4f), RIM_Y, 8 + (float) Math.sin(a1) * (r * k1 - 1.4f));
            b.face(WOOD, v(0, 1, 0), o0, o1, i1, i0);
        }
        // racines
        float[] rootAng = {18, 58, 128, 162, 208, 262, 318};
        Random r = new Random(7);
        for (int i = 0; i < rootAng.length; i++) {
            float a = rootAng[i];
            float drift = (r.nextFloat() - 0.5f) * 20;
            float[][] ctrl = {Geo.polar(8, 8, a, 8.2f, 5.0f), Geo.polar(8, 8, a + drift * 0.3f, 11.0f, 2.6f),
                    Geo.polar(8, 8, a + drift * 0.6f, 13.8f, 2.4f), Geo.polar(8, 8, a + drift, 16.8f, 1.0f)};
            Geo.horn(b, ctrl, 2.0f + r.nextFloat() * 0.5f, 0.35f, 1f, seg(4), seg(8), BARK);
        }
        // champignons luminescents
        MeshBuilder cg = new MeshBuilder();
        float[][] mush = {{35, 12.6f, 1.6f, 2.0f}, {45, 13.8f, 1.0f, 1.2f}, {150, 12.0f, 1.9f, 2.4f}, {140, 14.5f, 1.1f, 1.4f},
                {245, 12.8f, 1.5f, 1.8f}, {300, 13.4f, 1.2f, 1.6f}, {112, 14.8f, 0.9f, 1.0f}};
        for (float[] m : mush) {
            float[] q = Geo.polar(8, 8, m[0], m[1], 0);
            q[1] = floor.top(q[0], q[2]) - 0.2f;
            float tilt = (r.nextFloat() - 0.5f) * 20;
            b.push().translate(q[0], q[1], q[2]).rotateZ(tilt);
            b.cylinder(m[2] * 0.26f, 0, m[3], seg(7), BONE, null, null);
            b.translate(0, m[3], 0);
            b.push().scale(1, 0.65f, 1);
            b.sphereSection(m[2], seg(10), seg(4), 0, 90, CAP);
            b.pop();
            b.disc(m[2], 0, seg(10), BONE, false);
            b.pop();
            cg.push().translate(q[0], q[1], q[2]).rotateZ(tilt).translate(0, m[3], 0).scale(1.08f, 0.72f, 1.08f);
            cg.sphereSection(m[2], seg(8), seg(3), 0, 90, Mat.WHITE);
            cg.pop();
        }
        capsGlow = cg.build();
        // lianes spiralees autour de la souche
        for (int vi = 0; vi < 2; vi++) {
            int n = seg(24);
            float[][] path = new float[n][];
            for (int i = 0; i < n; i++) {
                float u = (float) i / (n - 1);
                float a = vi * 180 + 40 + u * 330;
                if (vi == 1) a = 200 - u * 300;
                float y = 1.0f + u * 13.2f;
                float rr = Ease.lerp(10.4f, 9.3f, Math.min(1, y / 6f)) + 0.35f;
                path[i] = Geo.polar(8, 8, a, rr, y);
            }
            b.tube(path, Geo.taper(n, 0.32f, 0.22f, 1), 5, VINE, true, true, 0, false);
            for (int i = 2; i < n - 1; i += 2) {
                float[] q = path[i];
                float a = (float) Math.toDegrees(Math.atan2(q[2] - 8, q[0] - 8));
                leafCard(b, q, a, 1.5f + 0.4f * (i % 3), i * 37f);
            }
        }
        Occlusion.bake(b, 0.5f, 5f, 0.55f, 0f);
        base = b.build();

        // couvercle de la souche : coupe du tronc (cernes, runes)
        MeshBuilder cp = new MeshBuilder();
        cp.push().translate(8, RIM_Y - 0.05f, 8);
        cp.disc(8.1f, 0, seg(28), RINGS, true);
        cp.pop();
        Occlusion.bake(cp, 0.5f, 3f, 0.4f, Float.NaN, base);
        cap = cp.build();

        // couronne de racines tressees qui enferme le coeur de la foret
        for (int i = 0; i < ROOTS; i++) {
            MeshBuilder rb = new MeshBuilder();
            float az = i * (360f / ROOTS) + 15;
            float[][] ctrl = {Geo.polar(8, 8, az, 8.3f, RIM_Y - 0.6f), Geo.polar(8, 8, az + 8, 8.9f, RIM_Y + 2.4f),
                    Geo.polar(8, 8, az + 22, 7.4f, RIM_Y + 5.6f), Geo.polar(8, 8, az + 40, 4.6f, RIM_Y + 8.0f),
                    Geo.polar(8, 8, az + 58, 1.6f, RIM_Y + 9.0f)};
            float[][] path = Geo.spline(ctrl, seg(4));
            rb.tube(path, Geo.taper(path.length, 1.25f, 0.35f, 1.1f), seg(8), BARK, true, true, 30, false);
            // radicelles
            for (int k = 1; k <= 2; k++) {
                float[] o = path[path.length * k / 3];
                float[][] tw = {o, add(o, v((float) Math.cos(Math.toRadians(az)) * 1.4f, 1.1f * k, (float) Math.sin(Math.toRadians(az)) * 1.4f)),
                        add(o, v((float) Math.cos(Math.toRadians(az + 30)) * 2.4f, 2.0f * k - 0.5f, (float) Math.sin(Math.toRadians(az + 30)) * 2.4f))};
                Geo.horn(rb, tw, 0.45f, 0.06f, 1.2f, seg(3), seg(5), BARK);
                leafCard(rb, tw[2], az + 20, 1.3f, k * 50f + i * 23);
            }
            Occlusion.bake(rb, 0.4f, 3f, 0.45f, Float.NaN, base);
            roots[i] = rb.build();
            rootBase[i] = Geo.polar(8, 8, az, 8.3f, RIM_Y - 0.6f);
            rootAz[i] = az;
        }

        MeshBuilder sd = new MeshBuilder();
        sd.push().scale(1, 1.5f, 1);
        sd.gem(2.0f, 1.3f, 2.3f, 8, SEED);
        sd.pop();
        for (int i = 0; i < 4; i++) {
            sd.push().rotateY(i * 90 + 20).rotateZ(38).translate(0, 1.0f, 0);
            sd.crystal(0.5f, 2.0f, 0.8f, 6, SEED);
            sd.pop();
        }
        seed = sd.build();
        MeshBuilder sg = new MeshBuilder();
        sg.push().scale(1.25f, 1.9f, 1.25f);
        sg.gem(2.0f, 1.3f, 2.3f, 8, Mat.WHITE);
        sg.pop();
        seedGlow = sg.build();

        MeshBuilder st = new MeshBuilder();
        st.rock(1.5f, 1.1f, 1.3f, 0.3f, 1.2f, 31, seg(9), BARK);
        stone = st.build();

        // cadenas en bois sculpte, feuille gravee, anse de bronze
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.ROUND_RECT;
        ps.w = 6.4f;
        ps.h = 6.6f;
        ps.d = 2.8f;
        ps.corner = 2.2f;
        ps.height = 4.2f;
        ps.halfSpan = 1.9f;
        ps.body = WOOD;
        ps.face = PLATE;
        ps.shackle = BRONZE;
        ps.trim = BRONZE;
        ps.hole = VINE;
        ps.decorate = (mb, s) -> {
            for (int side = -1; side <= 1; side += 2) {
                leafCard(mb, v(side * 2.9f, 3.0f, 0.6f), side * 70f, 1.4f, side * 30f);
            }
        };
        lock = new Padlock(ps);
        lock.build(detail);

        // cle : petits bois de cerf + graine de cristal
        Mesh[] k = KeySmith.build(WOOD, BRONZE, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 2.2f, 0);
            kb.blob(1.5f, 1.8f, 1.1f, 1f, 0.03f, 2, 2, 12, BONE);
            kb.pop();
            for (int s = -1; s <= 1; s += 2) {
                float[][] beam = {v(s * 0.9f, 3.4f, 0), v(s * 2.2f, 5.2f, 0), v(s * 3.6f, 6.4f, 0), v(s * 4.0f, 8.3f, 0)};
                Geo.horn(kb, beam, 0.45f, 0.12f, 1, 4, 6, ANTLER);
                Geo.horn(kb, new float[][]{v(s * 2.2f, 5.2f, 0), v(s * 2.0f, 6.8f, 0.1f), v(s * 2.4f, 7.9f, 0)}, 0.3f, 0.08f, 1, 3, 5, ANTLER);
                Geo.horn(kb, new float[][]{v(s * 3.6f, 6.4f, 0), v(s * 4.8f, 6.9f, 0), v(s * 5.4f, 7.8f, 0)}, 0.28f, 0.08f, 1, 3, 5, ANTLER);
                kb.push().translate(s * 0.55f, 2.6f, 0.9f);
                kb.sphere(0.22f, 6, 4, SEED);
                kb.pop();
                kg.push().translate(s * 0.55f, 2.6f, 0.9f);
                kg.sphere(0.42f, 6, 4, Mat.WHITE);
                kg.pop();
            }
            kb.push().translate(0, 4.4f, 0.2f).scale(0.45f);
            kb.gem(2.3f, 1.4f, 2.6f, 8, SEED);
            kb.pop();
            kg.push().translate(0, 4.4f, 0.2f).scale(0.6f);
            kg.gem(2.3f, 1.4f, 2.6f, 8, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    /** Carte de feuille (double face) orientee vers l'exterieur. */
    private static void leafCard(MeshBuilder b, float[] p, float outDeg, float size, float roll) {
        b.push().translate(p[0], p[1], p[2]).rotateY(90 - outDeg).rotateX(-35).rotateZ(roll % 60 - 30);
        b.translate(0, size * 0.45f, 0);
        b.decalDouble(LEAF, size * 0.62f, size);
        b.pop();
    }

    // =================================================================== animation
    /** Ouverture des racines (0..1+), en cascade. */
    private static float rootOpen(CrateState s, int i) {
        if (!s.active) return 0;
        float t = s.t;
        float o = Ease.outBack(Ease.range(t, CrateAnim.OPEN + i * 1.5f, CrateAnim.OPEN_END + i * 1.5f), 1.4f);
        float c = Ease.inCubic(Ease.range(t, CrateAnim.CLOSE + i, CrateAnim.SLAM - 4 + i));
        return o * (1 - c);
    }

    private float seedLift(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, CrateAnim.OPEN + 4, CrateAnim.OPEN_END + 6)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE - 10, CrateAnim.CLOSE + 8)));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        out.solid(p, cap, gl);
        out.additive(p, capsGlow, 0x2AD8A0, 0.14f + 0.25f * Seq.awaken(s));
        float sl = seedLift(s);
        p.push().translate(8, RIM_Y + 3.2f + sl * 10f, 8).rotY(s.time * 2.5f * sl + 20);
        out.solid(p, seed, 0.8f + 0.2f * cav);
        out.additive(p, seedGlow, glow, 0.22f + 0.35f * cav);
        p.pop();
        for (int i = 0; i < ROOTS; i++) {
            float o = rootOpen(s, i);
            p.push();
            if (o > 0.001f) {
                float[] q = rootBase[i];
                double a = Math.toRadians(rootAz[i]);
                // pivote vers l'exterieur autour de la tangente au pied de la racine
                p.translate(q[0], q[1], q[2]).rot(-105f * o, (float) -Math.sin(a), 0, (float) Math.cos(a)).translate(-q[0], -q[1], -q[2]);
            }
            out.solid(p, roots[i], gl);
            p.pop();
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, HANG_Y, HANG_Z);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        // pierres moussues et feux follets en orbite
        float orb = orbitScale(s);
        for (int i = 0; i < 5; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 72f + s.time * (0.7f + 0.1f * i) + (s.active ? s.t * s.t * 0.015f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 19f + (i % 2) * 1.5f + 4f * ex;
            float y = 10f + 3f * (float) Math.sin(s.time * 0.05f + i * 1.3f) + 9f * aw;
            p.push().translate(8, 0, 8).rotZ(8f * (float) Math.sin(i * 1.9f)).rotY(ang).translate(rad, y, 0).scale(orb);
            if (i % 2 == 0) {
                p.push().rotX(s.time * 1.2f).rotY(s.time * i);
                out.solid(p, stone, 0.6f);
                p.pop();
            }
            if (!s.item) out.billboard(p, Sprite.HALO, 0, 0, 0, i % 2 == 0 ? 3.5f : 2.6f, 0, glow, i % 2 == 0 ? 0.15f : 0.55f + 0.3f * s.pulse(0.3f + i * 0.05f));
            p.pop();
        }
        if (s.item) return;
        CommonFx.cavity(s, p, out, this, 8, RIM_Y + 1f, 8, 7f);
        out.ground(p, Sprite.HALO, 8, 0.6f, 8, 21f, 0, glow, 0.1f + 0.06f * s.pulse(0.08f) + 0.25f * aw);
        if (aw > 0.01f) {
            out.ground(p, Sprite.SWIRL, 8, 1.2f, 8, 22f, -s.time * 2f, glow, 0.35f * aw);
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, RIM_Y + 2, 8};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.18f) {
            fx.particle(FxSink.WISP, 8 + (r.nextFloat() - 0.5f) * 30, 3 + r.nextFloat() * 16, 8 + (r.nextFloat() - 0.5f) * 30,
                    (r.nextFloat() - 0.5f) * 0.1f, 0.05f, (r.nextFloat() - 0.5f) * 0.1f, r.nextBoolean() ? glow : 0xE8FF9A, 0.9f, 60);
        }
        if (r.nextFloat() < 0.04f) {
            fx.particle(FxSink.LEAF, 8 + (r.nextFloat() - 0.5f) * 20, 30, 8 + (r.nextFloat() - 0.5f) * 14, 0, -0.1f, 0,
                    r.nextBoolean() ? 0x7AB83A : 0xC8801E, 1.2f, 120);
        }
        if (r.nextFloat() < 0.1f) {
            double a = r.nextDouble() * Math.PI * 2;
            fx.particle(FxSink.DOT, 8 + (float) Math.cos(a) * 13, 3.5f, 8 + (float) Math.sin(a) * 13, 0, 0.12f, 0, 0x2AD8A0, 0.7f, 50);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 2) fx.sound(Snd.FOREST, 8, 16, 8, 0.8f, 0.9f);
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.FOREST, 8, 18, 8, 1.2f, 1.0f);
            fx.sound(Snd.WHOOSH, 8, 18, 8, 0.8f, 1.1f);
        }
        // tornade de feuilles
        if (k >= CrateAnim.OPEN && k < CrateAnim.OPEN + 40) {
            for (int arm = 0; arm < 3; arm++) {
                double a = Math.toRadians(k * 19f + arm * 120);
                float u = (k - CrateAnim.OPEN) / 40f;
                float rad = 16f - 5f * u;
                int c = r.nextInt(3) == 0 ? 0xC8801E : (r.nextBoolean() ? 0x7AB83A : 0x9BE35A);
                fx.particle(FxSink.LEAF, 8 + (float) Math.cos(a) * rad, 2 + 26 * u, 8 + (float) Math.sin(a) * rad,
                        -(float) Math.sin(a) * 0.7f, 0.5f, (float) Math.cos(a) * 0.7f, c, 1.3f, 50);
            }
        }
        if (k > CrateAnim.OPEN && k < CrateAnim.CLOSE && r.nextFloat() < 0.7f) {
            fx.particle(FxSink.WISP, 8 + (r.nextFloat() - 0.5f) * 8, 9, 8 + (r.nextFloat() - 0.5f) * 8, 0, 0.6f + r.nextFloat() * 0.4f, 0, glow, 1.0f, 40);
        }
        if (k == CrateAnim.SLAM) {
            for (int i = 0; i < 20; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.LEAF, 8 + (float) Math.cos(a) * 10, 16, 8 + (float) Math.sin(a) * 10,
                        (float) Math.cos(a) * 0.6f, 0.4f, (float) Math.sin(a) * 0.6f, r.nextBoolean() ? 0x7AB83A : 0xC8801E, 1.2f, 60);
            }
        }
    }
}
