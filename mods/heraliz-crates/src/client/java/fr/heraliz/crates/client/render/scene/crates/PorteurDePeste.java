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

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Porteur de Peste : une cuve d'egout en fer riveté et rongé par la rouille, posee sur une grille au-dessus des
 * egouts. Par ses hublots on voit bouillonner la boue pestilentielle ; des tuyaux plongent dans la grille et la
 * boue suinte des joints. A l'ouverture le volant de la vanne tourne a toute vitesse, l'ecoutille saute dans un
 * nuage de gaz et la boue deborde, entouree d'une nuee de mouches.
 */
public final class PorteurDePeste extends Design {
    private static final Mat BRICK = Mat.MAIN;
    private static final Mat RUST = Mat.MAIN2;
    private static final Mat IRON = Mat.TRIM;
    private static final Mat BRASS = Mat.ACCENT.sub(0, 0, 0.5f, 1);
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat PUS = Mat.DECAL_A.withWorld(8);
    private static final Mat SLUDGE = Mat.INTERIOR;
    private static final Mat TOXIC = Mat.GEM;
    private static final Mat DARKI = Mat.DARK;
    private static final Mat BONE = Mat.SPECIAL;

    /** Cuve (profil rayon/hauteur, centre 8,8). */
    private static final float[][] TANK = {{0.05f, 2.4f}, {5.2f, 2.5f}, {8.0f, 4.2f}, {9.4f, 7.2f}, {9.7f, 10.2f}, {9.1f, 13.2f}, {7.6f, 15.4f},
            {6.4f, 16.2f}};
    private static final float RIM_Y = 16.2f, HATCH_R = 6.6f;
    private static final float HANG_Y = 13.2f, HANG_Z = 11.6f;
    /** Hublots : azimut (degres), hauteur. */
    private static final float[][] PORTS = {{40, 9.4f}, {140, 9.4f}, {270, 9.8f}};

    private final Parts.RockSlab floor = new Parts.RockSlab();
    private Mesh base = Mesh.EMPTY, portGlass = Mesh.EMPTY, portGlow = Mesh.EMPTY, hatch = Mesh.EMPTY, wheel = Mesh.EMPTY;
    private Mesh surface = Mesh.EMPTY, dripsGlow = Mesh.EMPTY, skull = Mesh.EMPTY, bone = Mesh.EMPTY;

    public PorteurDePeste() {
        super("porteur_de_peste", 0xC6E23A, 0xF0FF9A);
        ringY = 40;
        ringR = 22;
        revealY = 48;
        riseFromY = 16;
        lockFloorY = 2.3f;
        floor.radius = 15.6f;
        floor.hCenter = 2.3f;
        floor.hEdge = 1.8f;
        floor.bump = 0.15f;
        floor.edgeNoise = 0.8f;
        floor.seed = 21;
        floor.planarTop = true;
    }

    private static float tankR(float y) {
        for (int i = 0; i < TANK.length - 1; i++) {
            if (y <= TANK[i + 1][1]) {
                float k = (y - TANK[i][1]) / Math.max(1e-3f, TANK[i + 1][1] - TANK[i][1]);
                return Ease.lerp(TANK[i][0], TANK[i + 1][0], Ease.clamp01(k));
            }
        }
        return TANK[TANK.length - 1][0];
    }

    /** Point de la paroi (azimut en degres, hauteur, decalage vers l'exterieur). */
    private static float[] onTank(float az, float y, float out) {
        return Geo.polar(8, 8, az, tankR(y) + out, y);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        floor.build(b, BRICK, seg(40), seg(8), 4f);
        float fy = floor.top(8, 8);
        // grille d'egout circulaire sous la cuve, flaques de boue
        b.push().translate(8, fy + 0.05f, 8);
        b.torus(12.2f, 0.45f, seg(36), 4, 0, 360, IRON);
        for (int k = 0; k < 12; k++) {
            b.push().rotateY(k * 15f);
            b.box(-12f, 0, -0.25f, 12f, 0.35f, 0.25f, IRON);
            b.pop();
        }
        b.pop();
        float[][] puddles = {{200, 12.8f, 3.2f}, {330, 13.4f, 2.4f}, {110, 13.0f, 2.0f}};
        MeshBuilder dg = new MeshBuilder();
        for (float[] pd : puddles) {
            float[] q = Geo.polar(8, 8, pd[0], pd[1], fy + 0.12f);
            b.push().translate(q[0], q[1], q[2]).scale(1, 1, 0.7f);
            b.disc(pd[2], 0, seg(14), SLUDGE, true);
            b.pop();
            dg.push().translate(q[0], q[1] + 0.02f, q[2]).scale(1, 1, 0.7f);
            dg.disc(pd[2] * 0.8f, 0, seg(12), SLUDGE, true);
            dg.pop();
        }
        // pieds de la cuve
        for (int k = 0; k < 4; k++) {
            float az = k * 90 + 45;
            float[] top = onTank(az, 4.4f, -0.4f), bot = Geo.polar(8, 8, az, 9.8f, fy);
            b.tube(new float[][]{top, v((top[0] + bot[0]) / 2, 3.0f, (top[2] + bot[2]) / 2), bot}, new float[]{0.9f, 0.8f, 1.0f}, seg(7), IRON, false, true, 0, false);
            b.push().translate(bot[0], fy, bot[2]);
            b.cylinder(1.5f, 0, 0.4f, seg(10), IRON, null, IRON);
            b.pop();
        }
        // cuve rivetee
        b.push().translate(8, 0, 8);
        b.lathe(TANK, seg(36), 0, 360, RUST, 1, 1, 0.02f, 0.4f, 5, false);
        for (float yb : new float[]{6.2f, 11.6f}) {
            b.push().translate(0, yb, 0);
            b.torus(tankR(yb) + 0.08f, 0.42f, seg(36), 4, 0, 360, IRON);
            b.pop();
            for (int k = 0; k < 20; k++) {
                b.push().rotateY(k * 18f + 9).translate(tankR(yb) + 0.35f, yb + 0.75f, 0).rotateZ(-90);
                b.rivet(0.24f, 0.2f, DARKI);
                b.pop();
            }
        }
        b.push().translate(0, RIM_Y, 0);
        b.torus(HATCH_R + 0.2f, 0.55f, seg(32), 5, 0, 360, IRON);
        b.pop();
        b.pop();
        // coutures verticales rivetees
        for (int k = 0; k < 6; k++) {
            float az = k * 60 + 30;
            float[][] path = new float[6][];
            for (int i = 0; i < 6; i++) {
                float y = 3.2f + i * 2.4f;
                path[i] = onTank(az, y, 0.05f);
            }
            b.tube(path, Geo.taper(6, 0.32f, 0.3f, 1), 5, IRON, false, false, 0, false);
        }
        // hublots : cerclage de laiton, boue lumineuse derriere
        MeshBuilder gl = new MeshBuilder();
        MeshBuilder pg = new MeshBuilder();
        for (float[] pt : PORTS) {
            float[] q = onTank(pt[0], pt[1], 0.1f);
            b.push().translate(q[0], q[1], q[2]).rotateY(90 - pt[0]).rotateX(90);
            b.torus(2.1f, 0.42f, seg(18), 5, 0, 360, BRASS);
            for (int r = 0; r < 8; r++) {
                b.push().rotateY(r * 45f).translate(2.1f, 0.35f, 0);
                b.rivet(0.2f, 0.16f, BRASS);
                b.pop();
            }
            b.push().translate(0, -0.35f, 0);
            b.disc(1.8f, 0, seg(16), SLUDGE, true);
            b.pop();
            b.pop();
            gl.push().translate(q[0], q[1], q[2]).rotateY(90 - pt[0]).rotateX(90);
            gl.disc(1.85f, 0.05f, seg(16), Mat.DECAL_A, true);
            gl.pop();
            pg.push().translate(q[0], q[1], q[2]).rotateY(90 - pt[0]).rotateX(90);
            pg.disc(1.7f, -0.2f, seg(16), SLUDGE, true);
            pg.pop();
        }
        // gros tuyaux qui plongent dans la grille
        for (int sd = -1; sd <= 1; sd += 2) {
            float az = 90 + sd * 95;
            float[] a = onTank(az, 12.4f, 0);
            double ar = Math.toRadians(az);
            float ox = (float) Math.cos(ar), oz = (float) Math.sin(ar);
            float[][] ctrl = {a, v(a[0] + ox * 2.6f, 12.6f, a[2] + oz * 2.6f), v(a[0] + ox * 4.6f, 10.0f, a[2] + oz * 4.6f),
                    v(a[0] + ox * 5.2f, 5.0f, a[2] + oz * 5.2f), v(a[0] + ox * 5.3f, fy - 0.5f, a[2] + oz * 5.3f)};
            float[][] path = Geo.spline(ctrl, seg(4));
            b.tube(path, Geo.taper(path.length, 1.25f, 1.25f, 1), seg(10), RUST, false, false, 0, false);
            for (int f : new int[]{1, path.length / 2, path.length - 3}) {
                float[] c = path[f], nx = path[Math.min(path.length - 1, f + 1)];
                float[][] ring = {v(c[0] - (nx[0] - c[0]) * 0.1f, c[1] - (nx[1] - c[1]) * 0.1f, c[2] - (nx[2] - c[2]) * 0.1f),
                        v(c[0] + (nx[0] - c[0]) * 0.1f, c[1] + (nx[1] - c[1]) * 0.1f, c[2] + (nx[2] - c[2]) * 0.1f)};
                b.tube(ring, new float[]{1.6f, 1.6f}, seg(10), IRON, true, true, 0, false);
            }
        }
        // la boue suinte des joints
        for (int k = 0; k < 9; k++) {
            float az = k * 40 + 17;
            if (Math.abs(((az - 90 + 540) % 360) - 180) < 16) continue;
            float y0 = k % 2 == 0 ? 11.6f : 6.2f;
            float len = 2 + (k % 3) * 1.5f;
            float[][] path = new float[4][];
            for (int i = 0; i < 4; i++) path[i] = onTank(az + i * 0.6f, y0 - len * i / 3f, 0.25f);
            b.tube(path, new float[]{0.28f, 0.25f, 0.3f, 0.42f}, 5, SLUDGE, false, true, 0, false);
            dg.tube(path, new float[]{0.3f, 0.28f, 0.32f, 0.46f}, 5, SLUDGE, false, true, 0, false);
        }
        // support du cadenas
        b.push().translate(8, HANG_Y - 0.4f, 8 + tankR(HANG_Y) + 0.5f);
        b.box(-1.3f, -0.9f, -0.6f, 1.3f, 0.9f, 1.4f, IRON);
        b.pop();
        b.push().translate(8, HANG_Y - 0.67f, 8 + HANG_Z).rotateZ(90);
        b.torus(0.95f, 0.28f, seg(12), 5, 0, 360, IRON);
        b.pop();
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        base = b.build();
        portGlass = gl.build();
        dripsGlow = dg.build();
        portGlow = pg.build();

        // ecoutille : dome rivete, event, volant de vanne
        MeshBuilder h = new MeshBuilder();
        float[][] dome = {{HATCH_R + 0.3f, 0}, {HATCH_R, 0.6f}, {5.4f, 1.6f}, {3.6f, 2.3f}, {1.4f, 2.6f}, {0.05f, 2.65f}};
        h.lathe(dome, seg(28), 0, 360, RUST, 1, 1, 0.02f, 0.5f, 9, false);
        for (int k = 0; k < 16; k++) {
            h.push().rotateY(k * 22.5f).translate(HATCH_R - 0.3f, 0.75f, 0).rotateZ(-50);
            h.rivet(0.26f, 0.22f, DARKI);
            h.pop();
        }
        h.cylinder(0.45f, 2.5f, 4.1f, seg(8), IRON, null, IRON);
        // event coude
        float[][] vent = {v(3.8f, 1.9f, -2.0f), v(4.0f, 4.0f, -2.2f), v(4.6f, 5.2f, -2.6f), v(5.8f, 5.4f, -3.2f)};
        h.tube(Geo.spline(vent, seg(3)), Geo.taper(3 * seg(3) + 1, 0.75f, 0.75f, 1), seg(8), IRON, false, true, 0, false);
        Occlusion.bake(h, 0.4f, 3f, 0.45f, Float.NaN);
        hatch = h.build();
        MeshBuilder w = new MeshBuilder();
        w.torus(2.6f, 0.3f, seg(20), 5, 0, 360, BRASS);
        for (int k = 0; k < 4; k++) {
            w.push().rotateY(k * 45f);
            w.box(-2.5f, -0.15f, -0.18f, 2.5f, 0.15f, 0.18f, BRASS);
            w.pop();
        }
        w.cylinder(0.6f, -0.3f, 0.35f, seg(8), BRASS, BRASS, BRASS);
        wheel = w.build();

        // surface de la boue (visible ouverte), ossements qui flottent
        MeshBuilder sf = new MeshBuilder();
        sf.push().translate(8, 0, 8);
        sf.disc(6.2f, 0, seg(24), SLUDGE, true);
        sf.pop();
        Random rb = new Random(8);
        for (int k = 0; k < 7; k++) {
            float[] q = Geo.polar(8, 8, rb.nextFloat() * 360, 1 + rb.nextFloat() * 4, 0.1f);
            sf.push().translate(q[0], q[1], q[2]).scale(1, 0.5f, 1);
            sf.sphereSection(0.5f + rb.nextFloat() * 0.5f, 8, 3, 0, 90, PUS);
            sf.pop();
        }
        surface = sf.build();
        MeshBuilder sk = new MeshBuilder();
        sk.blob(1.2f, 1.0f, 1.3f, 0.95f, 0.03f, 2, 3, 10, BONE);
        sk.color(0.12f);
        for (int sd = -1; sd <= 1; sd += 2) {
            sk.push().translate(sd * 0.45f, 0.1f, 1.05f);
            sk.sphere(0.32f, 6, 4, BONE);
            sk.pop();
        }
        sk.color(1f);
        skull = sk.build();
        MeshBuilder bn = new MeshBuilder();
        bn.cylinder(0.22f, -1.4f, 1.4f, 6, BONE, null, null);
        for (int sd = -1; sd <= 1; sd += 2) {
            bn.push().translate(0, sd * 1.4f, 0);
            bn.sphere(0.4f, 6, 4, BONE);
            bn.pop();
        }
        bone = bn.build();

        // cadenas de fer rouille, masque de peste grave
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.ROUND_RECT;
        st.w = 6.2f;
        st.h = 6.4f;
        st.d = 2.8f;
        st.corner = 1.4f;
        st.height = 3.2f;
        st.halfSpan = 1.9f;
        st.body = RUST;
        st.face = PLATE;
        st.shackle = IRON;
        st.trim = BRASS;
        st.hole = DARKI;
        lock = new Padlock(st);
        lock.build(detail);

        // cle : mini volant de vanne + fiole toxique
        Mesh[] k = KeySmith.build(IRON, BRASS, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 3.2f, 0).rotateX(90);
            kb.torus(2.2f, 0.3f, 16, 5, 0, 360, BRASS);
            for (int i = 0; i < 3; i++) {
                kb.push().rotateY(i * 60f);
                kb.box(-2.1f, -0.13f, -0.15f, 2.1f, 0.13f, 0.15f, BRASS);
                kb.pop();
            }
            kb.pop();
            kb.push().translate(0, 3.2f, 0);
            kb.sphere(0.9f, 10, 6, TOXIC);
            kb.pop();
            kg.push().translate(0, 3.2f, 0);
            kg.sphere(1.2f, 10, 6, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== animation
    private static float wheelAngle(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        // tourne pendant le deverrouillage, s'emballe a l'ouverture, se referme a la fin
        float a = 0;
        a += 900f * Ease.inOutCubic(Ease.range(t, CrateAnim.UNLOCK - 2, CrateAnim.OPEN + 6));
        a -= 720f * Ease.inOutCubic(Ease.range(t, CrateAnim.SLAM - 2, CrateAnim.END));
        return a;
    }

    private static float hatchOpen(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        float o = Ease.outBack(Ease.range(t, CrateAnim.OPEN + 4, CrateAnim.OPEN_END), 1.6f);
        return o * (1 - Ease.inCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM)));
    }

    private static float sludgeLevel(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, CrateAnim.OPEN + 4, CrateAnim.OPEN_END)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM)));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        float cav = Seq.cavity(s);
        out.solid(p, base, gl);
        out.additive(p, portGlow, glow, 0.35f + 0.5f * aw);
        out.additive(p, dripsGlow, glow, 0.12f + 0.2f * aw);
        out.translucent(p, portGlass, 0xB8D890, 0.55f);
        float ho = hatchOpen(s);
        float lvl = sludgeLevel(s);
        if (ho > 0.01f) {
            float sy = RIM_Y - 1.4f + lvl * 1.0f + (float) Math.sin(s.time * 0.3f) * 0.15f;
            p.push().translate(0, sy, 0);
            out.solid(p, surface, 0.6f + 0.4f * cav);
            p.pop();
            for (int i = 0; i < 3; i++) {
                float a = s.time * (1.2f + i * 0.4f) + i * 120;
                p.push().translate(8, sy + 0.35f + (float) Math.sin(s.time * 0.2f + i) * 0.3f, 8).rotY(a).translate(3.2f, 0, 0).rotZ(15 + i * 30f);
                out.solid(p, i == 0 ? skull : bone, 0.4f);
                p.pop();
            }
        }
        // ecoutille : saute, bascule et tournoie
        float wa = wheelAngle(s);
        p.push().translate(8, RIM_Y + ho * 9f, 8).rotY(ho * 130f).rot(ho * 35f, 1, 0, 0.4f);
        out.solid(p, hatch, gl);
        p.push().translate(0, 4.2f, 0).rotY(wa);
        out.solid(p, wheel, gl);
        p.pop();
        p.pop();
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, HANG_Y, 8 + HANG_Z);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        float aw = Seq.awaken(s);
        float ex = Seq.excite(s);
        float orb = orbitScale(s);
        for (int i = 0; i < 5; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 72f + s.time * (0.7f + 0.1f * i) + (s.active ? s.t * s.t * 0.015f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 19f + (i % 2) * 1.5f + 4f * ex;
            float y = 10f + 3f * (float) Math.sin(s.time * 0.05f + i * 1.3f) + 8f * aw;
            p.push().translate(8, 0, 8).rotY(ang).translate(rad, y, 0).rotX(i * 50f).rotZ(i * 30f).scale(orb);
            out.solid(p, i % 2 == 0 ? skull : bone, 0.4f);
            p.pop();
        }
        if (s.item) return;
        CommonFx.cavity(s, p, out, this, 8, RIM_Y - 1, 8, 6f);
        out.ground(p, Sprite.HALO, 8, 2.5f, 8, 17f, 0, glow, 0.1f + 0.25f * aw);
        if (aw > 0.01f) {
            out.billboard(p, Sprite.SMOKE, 8, RIM_Y + 8, 8, 14f * aw, s.time * 0.5f, 0x8AA030, 0.3f * aw);
            out.ground(p, Sprite.SWIRL, 8, 2.7f, 8, 18f, s.time * 2f, glow, 0.3f * aw);
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, RIM_Y - 1, 8};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.1f) {
            float[] q = onTank(r.nextFloat() * 360, 11.6f, 0.3f);
            fx.particle(FxSink.DROP, q[0], q[1], q[2], 0, -0.05f, 0, 0xC6E23A, 0.5f, 30);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK + 2) fx.sound(Snd.SEWER, 8, 12, 8, 0.9f, 1.0f);
        if (k == CrateAnim.OPEN + 4) {
            fx.sound(Snd.SEWER, 8, RIM_Y, 8, 1.3f, 0.8f);
            fx.sound(Snd.WHOOSH, 8, RIM_Y, 8, 0.9f, 0.6f);
            for (int i = 0; i < 34; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 4, RIM_Y + 1, 8 + (float) Math.sin(a) * 4,
                        (float) Math.cos(a) * 0.7f, 0.6f + r.nextFloat() * 0.5f, (float) Math.sin(a) * 0.7f, 0x7a8a2a, 4f, 50);
                fx.particle(FxSink.DROP, 8 + (float) Math.cos(a) * 3, RIM_Y, 8 + (float) Math.sin(a) * 3,
                        (float) Math.cos(a) * 0.5f, 1.6f + r.nextFloat() * 1.4f, (float) Math.sin(a) * 0.5f, 0xD8F040, 0.9f, 50);
            }
        }
        // jets de vapeur des events pendant que le volant tourne
        if (k >= CrateAnim.UNLOCK && k < CrateAnim.OPEN + 4 && k % 2 == 0) {
            fx.particle(FxSink.SMOKE, 8 + 5.8f, RIM_Y + 5.4f, 8 - 3.2f, 0.8f, 0.3f, -0.4f, 0xC8D0B0, 2f, 25);
        }
        if (k >= CrateAnim.OPEN && k < CrateAnim.CLOSE && r.nextFloat() < 0.7f) {
            double a = Math.toRadians(k * 29f);
            fx.particle(FxSink.FLY, 8 + (float) Math.cos(a) * 14, 4 + (k - CrateAnim.OPEN) % 30, 8 + (float) Math.sin(a) * 14,
                    -(float) Math.sin(a) * 0.6f, 0.3f, (float) Math.cos(a) * 0.6f, 0x202018, 1.0f, 50);
            fx.particle(FxSink.BUBBLE, 8 + (r.nextFloat() - 0.5f) * 8, RIM_Y, 8 + (r.nextFloat() - 0.5f) * 8, 0, 0.2f, 0, 0xE0FF80, 0.6f, 20);
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.SEWER, 8, RIM_Y, 8, 0.7f, 1.3f);
            for (int i = 0; i < 16; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 7, RIM_Y, 8 + (float) Math.sin(a) * 7, (float) Math.cos(a) * 0.5f, 0.15f,
                        (float) Math.sin(a) * 0.5f, 0x5a6a2a, 3.2f, 40);
            }
        }
    }
}
