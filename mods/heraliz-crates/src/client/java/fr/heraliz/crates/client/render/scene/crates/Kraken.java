package fr.heraliz.crates.client.render.scene.crates;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.mesh.MeshBuilder;
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
 * Kraken : coquille Saint-Jacques geante posee sur un rocher des abysses, enlacee par les tentacules du kraken ;
 * elle s'ouvre sur un ecrin de nacre et une perle geante qui s'eleve, dans un tourbillon d'eau.
 */
public final class Kraken extends Design {
    private static final Mat ROCK = Mat.MAIN;
    private static final Mat SHELL = Mat.MAIN2;
    private static final Mat SKIN = Mat.TRIM;
    private static final Mat BRONZE_OLD = Mat.ACCENT.sub(0, 0, 0.5f, 1);
    private static final Mat BRONZE = Mat.ACCENT.sub(0.5f, 0, 1, 1);
    private static final Mat NACRE = Mat.EMBLEM.withWorld(16);
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat CORAL = Mat.DECAL_A.withWorld(16);
    private static final Mat KELP = Mat.DECAL_B.withWorld(16);
    private static final Mat SAND = Mat.INTERIOR;
    private static final Mat PEARL = Mat.GEM;
    private static final Mat IRON = Mat.DARK;
    private static final Mat WATER = Mat.SPECIAL;

    /** Charniere de la coquille et forme de l'eventail. */
    private static final float HX = 8, HY = 12.0f, HZ = -4.5f, RMAX = 21.5f, THMAX = 56f;
    private static final float HANG_Y = 14.0f, HANG_Z = 18.4f;
    private static final float PEARL_R = 3.6f;

    private final Parts.RockSlab rock = new Parts.RockSlab();
    private Mesh base = Mesh.EMPTY, bottomIn = Mesh.EMPTY, lid = Mesh.EMPTY, lidIn = Mesh.EMPTY, pearl = Mesh.EMPTY, pearlGlow = Mesh.EMPTY;
    private Mesh treasure = Mesh.EMPTY, waterRing = Mesh.EMPTY, miniShell = Mesh.EMPTY, smallPearl = Mesh.EMPTY;
    private final Mesh[] kelp = new Mesh[3];
    private static final float[][] KELP_POS = {{-5.5f, 12f, 0}, {-6.5f, 5f, 1}, {21.5f, 15f, 2}, {23f, 7f, 0}, {19f, -3.5f, 1}, {-3f, -3f, 2}, {1f, 20.5f, 1}};
    /** Tentacules : base (x, z), longueur, rayon, phase, enroulement de la pointe. */
    private static final float[][] TENT = {
            {-6.0f, -6.5f, 36, 2.7f, 0, 1.0f}, {22.0f, -6.5f, 36, 2.7f, 1.7f, 1.0f}, {-9.0f, 6f, 28, 2.2f, 3.1f, 1.2f},
            {25.0f, 6f, 28, 2.2f, 4.4f, 1.2f}, {-6.5f, 19.5f, 19, 1.6f, 5.3f, 1.4f}, {22.5f, 19.5f, 19, 1.6f, 2.4f, 1.4f}};

    public Kraken() {
        super("kraken", 0x2AD8FF, 0xB8F4FF);
        ringY = 38;
        ringR = 22;
        revealY = 46;
        riseFromY = 12;
        lockFloorY = 2.8f;
        rock.radius = 15.4f;
        rock.hCenter = 6.2f;
        rock.hEdge = 2.6f;
        rock.bump = 0.9f;
        rock.seed = 5;
    }

    // =================================================================== coquille
    private static float rib(float th, float rho, float amp) {
        float c = 0.5f + 0.5f * (float) Math.cos(2 * Math.PI * th / 11.2f);
        return amp * (float) Math.pow(c, 0.75) * (float) Math.pow(rho, 0.7);
    }

    /** Point de la coquille (repere du modele). top : valve superieure ; inset : epaisseur vers l'interieur. */
    static float[] shellPt(float th, float rho, boolean top, float inset) {
        float tn = th / THMAX;
        float R = RMAX * (1 - 0.13f * tn * tn) - inset * 0.8f;
        float bulge = (top ? 6.6f : 5.4f) * (float) Math.pow(Math.sin(Math.PI * Math.pow(Math.max(rho, 0), 0.8)), 0.75) * (1 - 0.3f * tn * tn);
        bulge = Math.max(0, bulge - inset);
        float y = HY + (top ? bulge : -bulge) + rib(th, rho, inset > 0 ? 0.45f : 0.9f);
        double a = Math.toRadians(th);
        return v(HX + (float) Math.sin(a) * R * rho, y, HZ + (float) Math.cos(a) * R * rho);
    }

    private void valve(MeshBuilder b, boolean top, float inset, Mat m, boolean inward) {
        valve(b, top, inset, m, inward, inset > 0 ? seg(30) : seg(60), seg(12));
    }

    private void valve(MeshBuilder b, boolean top, float inset, Mat m, boolean inward, int nu, int nv) {
        float[][][] P = new float[nv + 1][nu + 1][];
        float[][] S = new float[nv + 1][nu + 1];
        float[][] T = new float[nv + 1][nu + 1];
        for (int i = 0; i <= nv; i++) {
            float rho = 0.02f + 0.98f * (float) i / nv;
            for (int j = 0; j <= nu; j++) {
                float th = -THMAX + 2 * THMAX * j / nu;
                P[i][j] = shellPt(th, rho, top, inset);
                S[i][j] = (float) j / nu * 2f;
                T[i][j] = rho * 0.95f;
            }
        }
        MeshBuilder.Orient o = inward ? MeshBuilder.Orient.IN : MeshBuilder.Orient.OUT;
        b.grid(P, null, S, T, false, m, o, true);
    }

    /** Tranche de la valve (bord festonne) entre la peau exterieure et la nacre. */
    private void valveRim(MeshBuilder b, boolean top) {
        int nu = seg(60);
        for (int j = 0; j < nu; j++) {
            float t0 = -THMAX + 2 * THMAX * j / nu, t1 = -THMAX + 2 * THMAX * (j + 1) / nu;
            float[] o0 = shellPt(t0, 1, top, 0), o1 = shellPt(t1, 1, top, 0);
            float[] i0 = shellPt(t0, 1, top, 0.55f), i1 = shellPt(t1, 1, top, 0.55f);
            float[] n = norm(Geo.sub(o0, v(HX, o0[1], HZ)));
            b.face(SHELL, n, o0, o1, i1, i0);
        }
        // cotes droits (bords de l'eventail) : relient les deux peaux
        for (float th : new float[]{-THMAX, THMAX}) {
            int nv = seg(12);
            for (int i = 0; i < nv; i++) {
                float r0 = 0.02f + 0.98f * (float) i / nv, r1 = 0.02f + 0.98f * (float) (i + 1) / nv;
                float[] o0 = shellPt(th, r0, top, 0), o1 = shellPt(th, r1, top, 0);
                float[] i0 = shellPt(th, r0, top, 0.55f), i1 = shellPt(th, r1, top, 0.55f);
                float[] n = v(Math.signum(th), 0, 0);
                b.face(SHELL, n, o0, o1, i1, i0);
            }
        }
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        rock.build(b, ROCK, seg(40), seg(9), 3f);
        valve(b, false, 0, SHELL, false);
        valveRim(b, false);
        // coraux
        coral(b, -3.5f, 16.5f, 20, 1.0f);
        coral(b, 21.5f, 11f, -30, 0.85f);
        coral(b, 17.5f, -6.5f, 160, 0.9f);
        // etoile de mer
        float[] sp = {20.5f, 0, 19.5f};
        sp[1] = rock.top(sp[0], sp[2]) + 0.25f;
        b.push().translate(sp[0], sp[1], sp[2]).rotateX(-90).rotateZ(20);
        float[][] star = new float[10][];
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(90 + i * 36);
            float r = i % 2 == 0 ? 3.1f : 1.2f;
            star[i] = new float[]{(float) Math.cos(a) * r, (float) Math.sin(a) * r};
        }
        b.color(1f, 0.75f, 0.55f);
        b.slab(star, 0.7f, 0.25f, true, CORAL, CORAL, CORAL);
        b.color(1f);
        b.pop();
        // agrafe de bronze sur la valve inferieure (le cadenas est accroche a la valve superieure)
        Occlusion.bake(b, 0.5f, 6f, 0.6f, 0f);
        base = b.build();

        MeshBuilder bi = new MeshBuilder();
        valve(bi, false, 0.55f, NACRE, true);
        // lit de sable et tresors dans la valve inferieure
        Random r = new Random(9);
        for (int i = 0; i < 14; i++) {
            float th = (r.nextFloat() - 0.5f) * 80, rho = 0.25f + r.nextFloat() * 0.55f;
            float[] q = shellPt(th, rho, false, 0.55f);
            bi.push().translate(q[0], q[1] + 0.55f, q[2]);
            if (i % 3 == 0) {
                bi.rotateY(r.nextFloat() * 360).rotateX(r.nextFloat() * 30 - 15);
                bi.cylinder(0.9f, 0, 0.25f, seg(9), BRONZE, BRONZE, BRONZE);
            } else {
                bi.sphere(0.55f + r.nextFloat() * 0.45f, seg(8), seg(5), PEARL);
            }
            bi.pop();
        }
        Occlusion.bake(bi, 0.4f, 4f, 0.45f, Float.NaN, base);
        bottomIn = bi.build();

        MeshBuilder l = new MeshBuilder();
        valve(l, true, 0, SHELL, false);
        valveRim(l, true);
        // anneau de bronze sur le bord avant (le cadenas y est accroche)
        l.push().translate(8, HANG_Y - 0.7f, HANG_Z).rotateZ(90);
        l.torus(1.05f, 0.32f, seg(16), seg(7), 0, 360, BRONZE);
        l.pop();
        l.push().translate(8, HANG_Y - 0.9f, 17.2f).rotateX(90);
        l.cylinder(0.5f, 0, 1.3f, seg(10), BRONZE, null, null);
        l.pop();
        Occlusion.bake(l, 0.4f, 4f, 0.45f, Float.NaN);
        lid = l.build();
        MeshBuilder li = new MeshBuilder();
        valve(li, true, 0.55f, NACRE, true);
        lidIn = li.build();

        MeshBuilder pb = new MeshBuilder();
        pb.sphere(PEARL_R, seg(20), seg(12), PEARL);
        pearl = pb.build();
        MeshBuilder pg = new MeshBuilder();
        pg.sphere(PEARL_R * 1.12f, seg(16), seg(10), Mat.WHITE);
        pearlGlow = pg.build();
        MeshBuilder sm = new MeshBuilder();
        sm.sphere(0.9f, seg(10), seg(6), PEARL);
        smallPearl = sm.build();

        // anneau d'eau (additif, texture d'eau lumineuse)
        MeshBuilder w = new MeshBuilder();
        int n = seg(48);
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float[][] pts = new float[4][];
            float ri = 15.5f, ro = 19.5f;
            float w0 = (float) Math.sin(a0 * 3) * 0.8f, w1 = (float) Math.sin(a1 * 3) * 0.8f;
            pts[0] = v((float) Math.cos(a0) * ri, w0, (float) Math.sin(a0) * ri);
            pts[1] = v((float) Math.cos(a1) * ri, w1, (float) Math.sin(a1) * ri);
            pts[2] = v((float) Math.cos(a1) * ro, w1 + 1.2f, (float) Math.sin(a1) * ro);
            pts[3] = v((float) Math.cos(a0) * ro, w0 + 1.2f, (float) Math.sin(a0) * ro);
            float s0 = (float) i / n * 6, s1 = (float) (i + 1) / n * 6;
            float[][] uv = {{WATER.u(MeshBuilder.mirror(s0)), WATER.v(0)}, {WATER.u(MeshBuilder.mirror(s1)), WATER.v(0)},
                    {WATER.u(MeshBuilder.mirror(s1)), WATER.v(1)}, {WATER.u(MeshBuilder.mirror(s0)), WATER.v(1)}};
            float[][] col = {v(0.2f, 0.2f, 0.2f), v(0.2f, 0.2f, 0.2f), v(1, 1, 1), v(1, 1, 1)};
            w.polyColored(pts, uv, null, v(0, 1, 0), col);
        }
        waterRing = w.build();

        // mini coquille (en orbite)
        MeshBuilder ms = new MeshBuilder();
        ms.scale(0.16f);
        ms.translate(-HX, -HY, -HZ - 8);
        valve(ms, true, 0, SHELL, false, 20, 4);
        valve(ms, false, 0, SHELL, false, 20, 4);
        miniShell = ms.build();

        for (int i = 0; i < kelp.length; i++) {
            MeshBuilder kb = new MeshBuilder();
            float h = 9f + i * 2.2f;
            int m = seg(9);
            float[][] path = new float[m][];
            float[] widths = new float[m];
            for (int k = 0; k < m; k++) {
                float u = (float) k / (m - 1);
                path[k] = v((float) Math.sin(u * 5 + i) * 0.9f * u, u * h, (float) Math.cos(u * 4 + i) * 0.5f * u);
                widths[k] = 2.0f * (1 - u * 0.7f) * (0.7f + 0.3f * (float) Math.sin(u * 9));
            }
            kb.ribbon(path, widths, v(1, 0, 0.3f), KELP);
            kb.ribbon(path, widths, v(-0.3f, 0, 1), KELP);
            kelp[i] = kb.build();
        }

        // cadenas : disque de bronze (hublot), bernaches
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.DISC;
        st.w = 6.4f;
        st.h = 6.4f;
        st.d = 2.8f;
        st.bevel = 0.55f;
        st.height = 3.8f;
        st.halfSpan = 1.9f;
        st.wire = 0.65f;
        st.body = BRONZE_OLD;
        st.face = PLATE;
        st.shackle = BRONZE;
        st.trim = BRONZE;
        st.hole = IRON;
        st.decorate = (mb, s) -> {
            // rivets en couronne et bernaches
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 + 45);
                mb.push().translate((float) Math.cos(a) * 2.75f, (float) Math.sin(a) * 2.75f, s.d / 2 - 0.1f).rotateX(90);
                mb.rivet(0.3f, 0.2f, BRONZE);
                mb.pop();
            }
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(205 + i * 40);
                float[] q = v((float) Math.cos(a) * 3.1f, (float) Math.sin(a) * 3.1f, 0.3f - i * 0.2f);
                Geo.cone(mb, q, v((float) Math.cos(a), (float) Math.sin(a), 0.3f), 0.8f, 0.55f, 6, IRON);
            }
        };
        lock = new Padlock(st);
        lock.build(detail);

        // cle : tentacule enroule en spirale + perle
        Mesh[] k = KeySmith.build(BRONZE, BRONZE_OLD, 0.55f, (kb, kg) -> {
            int m = 34;
            float[][] path = new float[m][];
            float[] radii = new float[m];
            for (int i = 0; i < m; i++) {
                float u = (float) i / (m - 1);
                double a = Math.toRadians(-90 + u * 560);
                float rr = 3.3f * (1 - u * 0.62f);
                path[i] = v((float) Math.cos(a) * rr, 3.6f + (float) Math.sin(a) * rr, 0);
                radii[i] = 0.12f + 0.66f * (float) Math.pow(1 - u, 0.9);
            }
            kb.tube(path, radii, 10, SKIN, true, true, 0, true, v(0, 1, 0));
            kb.push().translate(0, 3.6f, 0.2f);
            kb.sphere(1.05f, 12, 8, PEARL);
            kb.pop();
            kg.push().translate(0, 3.6f, 0.2f);
            kg.sphere(1.35f, 10, 6, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    private void coral(MeshBuilder b, float x, float z, float yaw, float sc) {
        float y = rock.top(x, z) - 0.4f;
        b.push().translate(x, y, z).rotateY(yaw).scale(sc);
        Random r = new Random((long) (x * 31 + z * 7));
        float[][] trunk = {v(0, 0, 0), v(0.3f, 2.2f, 0), v(-0.2f, 4.2f, 0.3f)};
        Geo.horn(b, trunk, 1.1f, 0.75f, 1f, seg(2), seg(6), CORAL);
        for (int i = 0; i < 3; i++) {
            float a = i * 120 + r.nextFloat() * 40;
            double ar = Math.toRadians(a);
            float[] s0 = v(0, 2.0f + i * 0.8f, 0);
            float[][] br = {s0, add(s0, v((float) Math.cos(ar) * 1.6f, 1.6f, (float) Math.sin(ar) * 1.6f)),
                    add(s0, v((float) Math.cos(ar) * 2.2f, 3.8f + r.nextFloat() * 1.5f, (float) Math.sin(ar) * 2.2f))};
            Geo.horn(b, br, 0.6f, 0.38f, 1f, seg(2), seg(5), CORAL);
            b.push().translate(br[2][0], br[2][1], br[2][2]);
            b.sphere(0.45f, seg(5), seg(3), CORAL);
            b.pop();
        }
        b.pop();
    }

    // =================================================================== tentacules (dynamiques)
    private Mesh tentacles(CrateState s) {
        MeshBuilder b = new MeshBuilder();
        float aw = Seq.awaken(s);
        float spin = Seq.spin(s);
        int n = seg(21);
        for (float[] tc : TENT) {
            float bx = tc[0], bz = tc[1], L = tc[2], r0 = tc[3], ph = tc[4], curl = tc[5];
            float by = rock.top(Math.max(-6, Math.min(22, bx)), Math.max(-6, Math.min(22, bz))) - 3.0f;
            float[] base = v(bx, Math.min(by, 0.2f), bz);
            float[] toC = norm(v(8 - bx, 0, 6 - bz));
            float[] up = v(0, 1, 0);
            float[] side = norm(Geo.cross(toC, up));
            // au repos : s'eleve, s'arque au-dessus de la coquille et s'enroule ; a l'ouverture : se deploie vers l'exterieur
            float a0 = Ease.lerp(0.05f, -0.45f, aw), a1 = Ease.lerp(0.55f, -0.1f, aw), a2 = Ease.lerp(1.25f, 0.9f, aw);
            float amp = Ease.lerp(0.18f, 0.5f, Math.max(aw, spin));
            float om = Ease.lerp(0.055f, 0.15f, aw);
            float[][] path = new float[n][];
            float[] p = base.clone();
            float ds = L / (n - 1);
            float[] inner0 = null;
            for (int i = 0; i < n; i++) {
                path[i] = p.clone();
                float x = (float) i / (n - 1);
                float tip = Ease.smooth((x - 0.55f) / 0.45f);
                float al = a0 + a1 * x + a2 * x * x + curl * 3.6f * tip * tip
                        + amp * (float) Math.sin(s.time * om - 4f * x + ph) * x;
                float be = 0.3f * (float) Math.sin(s.time * om * 0.7f + x * 2.5f + ph * 1.3f) * x;
                float[] dir = add(add(mul(up, (float) Math.cos(al)), mul(toC, (float) Math.sin(al))), mul(side, (float) Math.sin(be)));
                dir = norm(dir);
                if (i == 0) inner0 = add(mul(up, -(float) Math.sin(al)), mul(toC, (float) Math.cos(al)));
                p = add(p, mul(dir, ds));
            }
            float[] radii = new float[n];
            for (int i = 0; i < n; i++) {
                float x = (float) i / (n - 1);
                radii[i] = 0.12f + (r0 - 0.12f) * (float) Math.pow(1 - x, 1.1f);
            }
            b.tube(path, radii, seg(8), SKIN, true, true, 0, true, mul(inner0, -1));
        }
        return b.buildDynamic();
    }

    // =================================================================== animation
    private float lidAngle(CrateState s) {
        float idle = 1.6f * Ease.bell((s.time + s.seed * 150) % 150, 0, 24);
        if (!s.active) return idle;
        return Math.max(idle, 72f * Seq.open(s));
    }

    private float pearlLift(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return 4.5f * Ease.smooth(Ease.range(t, CrateAnim.OPEN + 6, CrateAnim.OPEN_END + 4)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE - 8, CrateAnim.CLOSE + 6)));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        // algues qui ondulent
        for (int i = 0; i < KELP_POS.length; i++) {
            float[] kp = KELP_POS[i];
            float y = rock.top(kp[0], kp[1]) - 0.5f;
            float sway = (float) Math.sin(s.time * 0.05f + i * 1.3f) * 7f + (float) Math.sin(s.time * 0.13f + i) * 3f;
            p.push().translate(kp[0], y, kp[1]).rotY(i * 47f).rotZ(sway).rotX(sway * 0.5f);
            out.solid(p, kelp[(int) kp[2]], gl);
            p.pop();
        }
        float ang = lidAngle(s);
        boolean inside = ang > 0.5f;
        if (inside) {
            out.solid(p, bottomIn, 0.12f + 0.22f * cav);
            float lift = pearlLift(s);
            p.push().translate(8, HY - 5.4f + 0.6f + PEARL_R * 0.8f + lift, 6.0f).rotY(s.time * 2f);
            out.solid(p, pearl, 0.35f + 0.3f * cav);
            out.additive(p, pearlGlow, glow, 0.08f + 0.22f * cav + 0.06f * s.pulse(0.1f));
            p.pop();
        }
        p.push().translate(HX, HY, HZ).rotX(-ang).translate(-HX, -HY, -HZ);
        out.solid(p, lid, gl);
        if (inside) out.solid(p, lidIn, 0.12f + 0.22f * cav);
        p.pop();
        out.solid(p, tentacles(s), gl);
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        float ang = lidAngle(s);
        p.translate(HX, HY, HZ).rotX(-ang).translate(-HX, -HY, -HZ);
        p.translate(8, HANG_Y, HANG_Z);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        // perles et petites coquilles en orbite
        float orb = orbitScale(s);
        for (int i = 0; i < 5; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 72f + s.time * (0.9f + 0.15f * i) + (s.active ? s.t * s.t * 0.015f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 19.5f + 4f * ex;
            float y = 11f + 2.5f * (float) Math.sin(s.time * 0.05f + i * 1.9f) + 10f * aw;
            p.push().translate(8, 0, 8).rotZ(8f * (float) Math.sin(i * 1.3f)).rotY(ang).translate(rad, y, 0).rotY(s.time * 3f).rotX(20).scale(orb);
            if (i % 2 == 0) {
                out.solid(p, smallPearl, 0.8f);
            } else {
                out.solid(p, miniShell, 0.6f);
            }
            if (!s.item) out.billboard(p, Sprite.HALO, 0, 0, 0, 3.6f, 0, glow, 0.2f + 0.2f * ex);
            p.pop();
        }
        if (s.item) return;
        // tourbillon d'eau : deux anneaux qui s'elevent et tournent a l'ouverture
        for (int i = 0; i < 2; i++) {
            float h = 1.2f + i * 1.5f + aw * (6f + i * 12f);
            float sc = 1f - 0.12f * i * aw + 0.1f * aw;
            p.push().translate(8, h, 8).rotY(s.time * (i == 0 ? 2.2f : -3.1f)).scale(sc, 1, sc);
            out.additive(p, waterRing, glow, 0.16f + 0.1f * s.pulse(0.08f) + 0.45f * aw);
            p.pop();
        }
        out.ground(p, Sprite.SWIRL, 8, 0.5f, 8, 24f, s.time * 1.6f, glow, 0.16f + 0.35f * aw);
        CommonFx.cavity(s, p, out, this, 8, 9f, 6, 7f);
        float cav = Seq.cavity(s);
        if (cav > 0.01f) {
            out.billboard(p, Sprite.HALO, 8, HY - 1f + pearlLift(s), 6, 13f, 0, 0xCFF8FF, 0.2f * cav);
            out.billboard(p, Sprite.GLINT, 8, HY - 1f + pearlLift(s) + 2f, 6.5f, 6f, s.time * 0.8f, 0xFFFFFF, 0.3f * cav * (0.6f + 0.4f * s.pulse(0.3f)));
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, 8, 6};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.25f) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = 4 + r.nextFloat() * 12;
            float x = 8 + (float) Math.cos(a) * rr, z = 8 + (float) Math.sin(a) * rr;
            fx.particle(FxSink.BUBBLE, x, rock.top(x, z) + 0.5f, z, 0, 0.25f + r.nextFloat() * 0.2f, 0, 0xCFF6FF, 0.6f + r.nextFloat() * 0.6f, 30 + r.nextInt(20));
        }
        if (r.nextFloat() < 0.12f) {
            float th = (r.nextFloat() - 0.5f) * 100;
            float[] q = shellPt(th, 1, true, 0);
            fx.particle(FxSink.BUBBLE, q[0], q[1] + 0.3f, q[2], 0, 0.3f, 0, 0xCFF6FF, 0.5f, 25);
        }
        if (r.nextFloat() < 0.08f) {
            fx.particle(FxSink.WISP, 8 + (r.nextFloat() - 0.5f) * 28, 1 + r.nextFloat() * 10, 8 + (r.nextFloat() - 0.5f) * 28,
                    0, 0.05f, 0, glow, 1.2f, 50);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 2) {
            for (int i = 0; i < 30; i++) {
                float th = (r.nextFloat() - 0.5f) * 110;
                float[] q = shellPt(th, 1, true, 0);
                fx.particle(FxSink.BUBBLE, q[0], q[1], q[2], (r.nextFloat() - 0.5f) * 0.3f, 0.4f + r.nextFloat() * 0.5f, (r.nextFloat() - 0.5f) * 0.3f, 0xCFF6FF, 0.7f + r.nextFloat() * 0.6f, 30);
            }
        }
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.WATER, 8, 12, 8, 1.2f, 1.0f);
            fx.sound(Snd.WHOOSH, 8, 12, 8, 0.8f, 0.8f);
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                float sp = 0.6f + r.nextFloat() * 1.2f;
                fx.particle(FxSink.DROP, 8 + (float) Math.cos(a) * 6, 13, 8 + (float) Math.sin(a) * 6,
                        (float) Math.cos(a) * sp, 1.6f + r.nextFloat() * 1.8f, (float) Math.sin(a) * sp, 0x8FE8FF, 0.9f + r.nextFloat() * 0.6f, 40);
            }
        }
        // colonne de bulles en spirale
        if (k >= CrateAnim.OPEN && k < CrateAnim.CLOSE && r.nextFloat() < 0.9f) {
            double a = Math.toRadians(k * 17f);
            for (int arm = 0; arm < 2; arm++) {
                double aa = a + arm * Math.PI;
                fx.particle(FxSink.BUBBLE, 8 + (float) Math.cos(aa) * 14, 3, 8 + (float) Math.sin(aa) * 14,
                        -(float) Math.sin(aa) * 0.4f, 0.8f + r.nextFloat() * 0.4f, (float) Math.cos(aa) * 0.4f, 0xCFF6FF, 0.8f + r.nextFloat() * 0.5f, 40);
            }
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.WATER, 8, 10, 8, 0.7f, 1.3f);
            for (int i = 0; i < 26; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.DROP, 8 + (float) Math.cos(a) * 12, 10, 8 + (float) Math.sin(a) * 10,
                        (float) Math.cos(a) * 0.8f, 1.2f + r.nextFloat(), (float) Math.sin(a) * 0.8f, 0x8FE8FF, 0.8f, 30);
            }
        }
    }
}
