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
 * Sujet N-07, monstre de dechets radioactifs : unite de confinement industrielle. Plateforme grillagee,
 * cuve de verre remplie de liquide radioactif ou flotte la creature, jauges qui se remplissent et se vident,
 * manometres, gyrophare d'alarme, couvercle souleve par des verins hydrauliques dans un jet de vapeur.
 */
public final class SujetN07 extends Design {
    private static final Mat GRATE = Mat.MAIN;
    private static final Mat PAINT = Mat.MAIN2;
    private static final Mat STEEL = Mat.TRIM;
    private static final Mat HAZARD = Mat.ACCENT;
    private static final Mat RADIO = Mat.EMBLEM;
    private static final Mat PLATE = Mat.LOCK;
    private static final Mat GLASS = Mat.DECAL_A;
    private static final Mat DIAL = Mat.DECAL_B;
    private static final Mat LIQUID = Mat.INTERIOR;
    private static final Mat EYE = Mat.GEM;
    private static final Mat RUBBER = Mat.DARK;
    private static final Mat SLIME = Mat.SPECIAL;

    private static final float TANK_R = 7.4f, TANK_Y0 = 4.8f, TANK_Y1 = 19.4f, LIQ_R = 7.05f;
    private static final float LID_Y = 20.2f;
    private static final float HANG_Y = 19.3f, HANG_Z = 8 + 8.4f + 1.3f;
    /** Jauges laterales : x, z. */
    private static final float[][] GAUGES = {{-4.5f, 17.5f}, {20.5f, 17.5f}};
    private static final float G_Y0 = 3.2f, G_Y1 = 14.5f;

    private Mesh base = Mesh.EMPTY, glass = Mesh.EMPTY, liquid = Mesh.EMPTY, liquidTop = Mesh.EMPTY, gaugeGlass = Mesh.EMPTY;
    private Mesh gaugeLiquid = Mesh.EMPTY, needle = Mesh.EMPTY, lid = Mesh.EMPTY, rod = Mesh.EMPTY, beacon = Mesh.EMPTY;
    private Mesh blob = Mesh.EMPTY, eye = Mesh.EMPTY, pupil = Mesh.EMPTY, barrel = Mesh.EMPTY, beaconGlow = Mesh.EMPTY;

    public SujetN07() {
        super("sujet_n07", 0x7CFF2A, 0xE8FF6A);
        ringY = 40;
        ringR = 22;
        revealY = 48;
        riseFromY = 16;
        lockFloorY = 2.6f;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        int n = seg(36);
        // plateforme grillagee
        b.push().translate(8, 0, 8);
        b.cylinder(15.2f, 0.2f, 2.4f, n, HAZARD, null, GRATE);
        b.push().translate(0, 2.4f, 0);
        b.torus(15.2f, 0.45f, n, 5, 0, 360, STEEL);
        b.pop();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            b.push().translate((float) Math.cos(a) * 13.6f, 2.4f, (float) Math.sin(a) * 13.6f);
            b.rivet(0.6f, 0.4f, STEEL);
            b.pop();
        }
        // socle de la cuve, montants
        b.cylinder(8.8f, 2.4f, TANK_Y0, n, PAINT, null, STEEL);
        b.push().translate(0, TANK_Y0, 0);
        b.torus(8.6f, 0.4f, n, 5, 0, 360, STEEL);
        b.pop();
        b.cylinder(8.5f, TANK_Y1, LID_Y, n, STEEL, STEEL, STEEL);
        for (int i = 0; i < 4; i++) {
            float a = i * 90 + 45;
            b.push().rotateY(-a).translate(8.25f, 0, 0);
            b.bevelBox(-0.6f, TANK_Y0, -0.8f, 0.6f, TANK_Y1, 0.8f, 0.25f, STEEL);
            for (float y = TANK_Y0 + 2; y < TANK_Y1; y += 4) {
                b.push().translate(0.6f, y, 0).rotateZ(-90);
                b.rivet(0.3f, 0.2f, STEEL);
                b.pop();
            }
            b.pop();
        }
        // agrafe du cadenas
        b.push().translate(0, HANG_Y - 1.0f, 8.4f).rotateX(90);
        b.cylinder(0.45f, 0, 1.4f, seg(8), STEEL, null, null);
        b.pop();
        b.push().translate(0, HANG_Y - 0.7f, HANG_Z - 8).rotateZ(90);
        b.torus(1.05f, 0.33f, seg(14), seg(6), 0, 360, STEEL);
        b.pop();
        b.pop();
        // jauges : socles, capuchons, graduations, tuyaux vers la cuve
        for (float[] g : GAUGES) {
            b.push().translate(g[0], 0, g[1]);
            b.cylinder(1.9f, 2.4f, G_Y0, seg(12), STEEL, null, STEEL);
            b.cylinder(1.8f, G_Y1, G_Y1 + 1.0f, seg(12), STEEL, null, STEEL);
            for (float y = G_Y0 + 1; y < G_Y1; y += 1.4f) {
                b.push().rotateY(g[0] < 8 ? 40 : -40).translate(0, y, 1.45f);
                b.box(-0.35f, -0.08f, -0.05f, 0.35f, 0.08f, 0.1f, STEEL);
                b.pop();
            }
            b.pop();
            float side = g[0] < 8 ? -1 : 1;
            float[][] pipe = {v(g[0], G_Y1 + 0.5f, g[1]), v(g[0], G_Y1 + 2.5f, g[1] - 1), v(8 + side * 7f, 17.5f, 11.5f), v(8 + side * 6.4f, 16.5f, 10.5f)};
            b.tube(Geo.spline(pipe, seg(4)), Geo.taper(3 * seg(4) + 1, 0.55f, 0.55f, 1), seg(7), STEEL, true, true, 0, false);
            // manometre sur le tuyau
            float[] dp = Geo.lerp(pipe[1], pipe[2], 0.35f);
            b.push().translate(dp[0], dp[1] + 1.4f, dp[2]).rotateY(side * -35);
            b.cylinder(1.7f, -0.35f, 0.35f, seg(14), STEEL, STEEL, STEEL);
            b.pop();
        }
        // tuyaux et cables vers la plateforme
        float[][] feed = {v(8, 3.5f, 15.9f), v(8, 2.9f, 18.5f), v(12, 2.8f, 21.5f), v(17, 2.8f, 21)};
        b.tube(Geo.spline(feed, seg(3)), Geo.taper(3 * seg(3) + 1, 0.8f, 0.8f, 1), seg(8), STEEL, true, true, 0, false);
        Random r = new Random(3);
        for (int i = 0; i < 4; i++) {
            float a = 150 + i * 40 + r.nextFloat() * 10;
            float[] p0 = Geo.polar(8, 8, a, 8.4f, TANK_Y1 - 0.4f);
            float[] p2 = Geo.polar(8, 8, a + 8, 13.5f, 2.6f);
            float[] p1 = Geo.lerp(p0, p2, 0.5f);
            p1[1] -= 3.5f;
            p1 = add(p1, mul(norm(v(p1[0] - 8, 0, p1[2] - 8)), 2.5f));
            b.tube(Geo.spline(new float[][]{p0, p1, p2}, seg(5)), Geo.taper(2 * seg(5) + 1, 0.35f, 0.35f, 1), 5, RUBBER, false, false, 0, false);
        }
        // pupitre de controle a l'arriere
        b.push().translate(8, 2.4f, -5.8f).rotateY(180);
        b.bevelBox(-4, 0, -1.6f, 4, 5.5f, 1.6f, 0.4f, PAINT);
        b.push().translate(0, 5.5f, 0).rotateX(-25);
        b.bevelBox(-4, 0, -1.6f, 4, 0.8f, 1.8f, 0.2f, STEEL);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.5f, 5f, 0.55f, 0f);
        base = b.build();

        MeshBuilder gl = new MeshBuilder();
        gl.push().translate(8, 0, 8);
        gl.cylinder(TANK_R, TANK_Y0, TANK_Y1, n, GLASS.asFit(), null, null);
        gl.pop();
        glass = gl.build();

        MeshBuilder lq = new MeshBuilder();
        lq.push().translate(8, 0, 8);
        lq.cylinder(LIQ_R, 0, 1, n, LIQUID, LIQUID, null);
        lq.pop();
        liquid = lq.build();
        MeshBuilder lt = new MeshBuilder();
        lt.surface((u, vv) -> {
            double a = u * Math.PI * 2;
            float rr = LIQ_R * vv;
            return v(8 + (float) Math.cos(a) * rr, 0.25f * (float) Math.sin(rr * 1.4f) * (1 - vv * 0.3f), 8 + (float) Math.sin(a) * rr);
        }, n, seg(5), LIQUID, 1, 0.5f, true, MeshBuilder.Orient.OUT);
        liquidTop = lt.build();

        MeshBuilder gg = new MeshBuilder();
        gg.cylinder(1.35f, G_Y0, G_Y1, seg(14), GLASS.asFit(), null, null);
        gaugeGlass = gg.build();
        MeshBuilder gq = new MeshBuilder();
        gq.cylinder(1.15f, 0, 1, seg(12), LIQUID, null, LIQUID);
        gaugeLiquid = gq.build();

        MeshBuilder nd = new MeshBuilder();
        nd.push().translate(0, 0, 0.42f);
        nd.disc(1.45f, 0, seg(16), DIAL, true);
        nd.pop();
        needle = nd.build();

        // couvercle : chapeau d'acier, events, bande de danger, embleme, gyrophare
        MeshBuilder l = new MeshBuilder();
        l.push().translate(8, 0, 8);
        l.cylinder(8.7f, LID_Y, LID_Y + 1.6f, n, HAZARD, STEEL, null);
        l.cylinder(8.3f, LID_Y + 1.6f, LID_Y + 3.0f, n, PAINT, null, null);
        l.push().translate(0, LID_Y + 3.0f, 0).scale(1, 0.32f, 1);
        l.sphereSection(8.3f, n, seg(6), 0, 90, PAINT);
        l.pop();
        for (int i = 0; i < 6; i++) {
            float a = i * 60 + 30;
            l.push().rotateY(-a).translate(6.2f, LID_Y + 3.6f, 0).rotateZ(12);
            l.bevelBox(-1.2f, 0, -1.6f, 1.2f, 1.1f, 1.6f, 0.25f, STEEL);
            for (int k = 0; k < 3; k++) {
                l.box(-0.9f + k * 0.7f, 1.1f, -1.3f, -0.6f + k * 0.7f, 1.3f, 1.3f, RUBBER);
            }
            l.pop();
        }
        // poignees
        for (int sd = -1; sd <= 1; sd += 2) {
            l.push().translate(sd * 8.9f, LID_Y + 2.2f, 0).rotateX(90);
            l.torus(1.2f, 0.25f, seg(10), 5, 90, 270, STEEL);
            l.pop();
        }
        // socle du gyrophare
        l.cylinder(1.7f, LID_Y + 5.3f, LID_Y + 6.3f, seg(14), STEEL, null, STEEL);
        l.pop();
        // embleme radioactif a l'avant du chapeau
        l.push().translate(8, LID_Y + 2.3f, 8 + 8.32f);
        l.decalZ(RADIO, 0, 0, 0, 2.6f, 2.6f);
        l.pop();
        Occlusion.bake(l, 0.45f, 4f, 0.5f, Float.NaN);
        lid = l.build();
        MeshBuilder bc = new MeshBuilder();
        bc.push().translate(8, LID_Y + 6.3f, 8);
        bc.cylinder(1.35f, 0, 1.6f, seg(12), GLASS.asFit(), null, null);
        bc.push().translate(0, 1.6f, 0);
        bc.sphereSection(1.35f, seg(12), 4, 0, 90, GLASS.asFit());
        bc.pop();
        bc.pop();
        beacon = bc.build();
        MeshBuilder bg = new MeshBuilder();
        bg.push().translate(8, LID_Y + 7.3f, 8);
        bg.sphere(1.1f, 10, 6, Mat.WHITE);
        bg.pop();
        beaconGlow = bg.build();

        MeshBuilder rd = new MeshBuilder();
        rd.cylinder(0.45f, 0, 1, seg(8), STEEL, null, STEEL);
        rod = rd.build();

        // creature : masse gelatineuse
        MeshBuilder bl = new MeshBuilder();
        bl.blob(3.6f, 3.2f, 3.4f, 1f, 0.22f, 1.1f, 7, seg(16), SLIME);
        blob = bl.build();
        MeshBuilder ey = new MeshBuilder();
        ey.sphere(0.75f, seg(10), seg(6), EYE);
        eye = ey.build();
        MeshBuilder pu = new MeshBuilder();
        pu.color(0.05f);
        pu.push().translate(0, 0, 0.55f).scale(0.35f, 0.5f, 0.3f);
        pu.sphere(1, 8, 5, RUBBER);
        pu.pop();
        pupil = pu.build();

        MeshBuilder br = new MeshBuilder();
        br.cylinder(1.3f, -1.5f, 1.5f, seg(12), HAZARD, STEEL, STEEL);
        for (float y : new float[]{-1.0f, 1.0f}) {
            br.push().translate(0, y, 0);
            br.torus(1.32f, 0.12f, seg(12), 4, 0, 360, STEEL);
            br.pop();
        }
        barrel = br.build();

        // cadenas industriel jaune, embleme radioactif
        Padlock.Style st = new Padlock.Style();
        st.shape = Padlock.Shape.ROUND_RECT;
        st.w = 6.6f;
        st.h = 6.4f;
        st.d = 3.0f;
        st.corner = 1.0f;
        st.height = 4.2f;
        st.halfSpan = 2.0f;
        st.body = HAZARD;
        st.face = PLATE;
        st.shackle = STEEL;
        st.trim = STEEL;
        st.hole = RUBBER;
        st.decorate = (mb, s) -> {
            for (int sd = -1; sd <= 1; sd += 2) {
                mb.push().translate(sd * 2.7f, 2.6f, s.d / 2 - 0.05f).rotateX(90);
                mb.rivet(0.35f, 0.25f, STEEL);
                mb.pop();
                mb.push().translate(sd * 2.7f, -2.6f, s.d / 2 - 0.05f).rotateX(90);
                mb.rivet(0.35f, 0.25f, STEEL);
                mb.pop();
            }
        };
        lock = new Padlock(st);
        lock.build(detail);

        // cle : fiole de liquide radioactif dans un anneau d'acier
        Mesh[] k = KeySmith.build(STEEL, HAZARD, 0.55f, (kb, kg) -> {
            kb.push().translate(0, 3.8f, 0).rotateX(90);
            kb.torus(3.0f, 0.55f, 20, 8, 0, 360, HAZARD);
            kb.pop();
            kb.push().translate(0, 1.6f, 0);
            kb.cylinder(1.0f, 0, 4.2f, 12, GLASS.asFit(), STEEL, STEEL);
            kb.pop();
            kb.push().translate(0, 1.8f, 0);
            kb.cylinder(0.85f, 0, 3.4f, 12, LIQUID, LIQUID, LIQUID);
            kb.pop();
            kg.push().translate(0, 1.8f, 0);
            kg.cylinder(1.05f, 0, 3.4f, 10, Mat.WHITE, Mat.WHITE, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== animation
    /** Niveau du liquide de la cuve (y). */
    private float level(CrateState s) {
        float idle = 0.72f + 0.1f * (float) Math.sin(s.time * 0.03f + s.seed * 4) + 0.02f * (float) Math.sin(s.time * 0.21f);
        if (!s.active) return TANK_Y0 + (TANK_Y1 - 0.8f - TANK_Y0) * idle;
        float surge = Ease.smooth(Ease.range(s.t, CrateAnim.UNLOCK, CrateAnim.OPEN)) * (1 - Ease.smooth(Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)));
        float lv = Ease.lerp(idle, 0.97f, surge) + 0.02f * (float) Math.sin(s.t * 0.6f) * surge;
        return TANK_Y0 + (TANK_Y1 - 0.8f - TANK_Y0) * Math.min(1f, lv);
    }

    /** Niveau d'une jauge (0..1) : se remplit et se vide en alternance. */
    private float gaugeLevel(CrateState s, int i) {
        float ph = i * 2.4f + s.seed * 3;
        float base = 0.5f + 0.42f * (float) Math.sin(s.time * 0.045f + ph);
        base = Ease.smooth(base);
        if (!s.active) return base;
        float t = s.t;
        float alarm = Ease.range(t, CrateAnim.UNLOCK - 4, CrateAnim.UNLOCK + 6) * (1 - Ease.range(t, CrateAnim.CLOSE, CrateAnim.END));
        float fast = 0.5f + 0.48f * (float) Math.sin(t * 0.35f + ph);
        return Ease.lerp(base, t < CrateAnim.OPEN + 10 ? 1f : fast, alarm);
    }

    private float lidLift(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        if (t < CrateAnim.OPEN) return 0.03f * Ease.bell(t, CrateAnim.UNLOCK + 2, CrateAnim.OPEN);
        if (t < CrateAnim.CLOSE) return Ease.outBack(Ease.range(t, CrateAnim.OPEN, CrateAnim.OPEN + 14), 1.3f);
        return 1 - Ease.inCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.SLAM));
    }

    private float creatureRise(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.outBack(Ease.range(t, CrateAnim.OPEN + 6, CrateAnim.OPEN_END + 4), 1.2f) * (1 - Ease.inCubic(Ease.range(t, CrateAnim.CLOSE - 10, CrateAnim.CLOSE + 8)));
    }

    private float alarm(CrateState s) {
        if (!s.active) return 0;
        return Ease.range(s.t, CrateAnim.UNLOCK, CrateAnim.UNLOCK + 3) * (1 - Ease.range(s.t, CrateAnim.SLAM, CrateAnim.END));
    }

    private Mesh tendrils(CrateState s, float[] c) {
        MeshBuilder b = new MeshBuilder();
        float rise = creatureRise(s);
        int n = seg(10);
        for (int i = 0; i < 5; i++) {
            float a0 = i * 72 + s.seed * 50;
            float[][] path = new float[n][];
            float[] p = Geo.polar(c[0], c[2], a0, 2.6f, c[1] - 1.5f);
            for (int k = 0; k < n; k++) {
                path[k] = p.clone();
                float x = (float) k / (n - 1);
                float wav = (float) Math.sin(s.time * (0.12f + 0.2f * rise) + i * 1.7f - x * 4) * (0.6f + 1.2f * rise);
                double a = Math.toRadians(a0 + wav * 25);
                float[] dir = norm(v((float) Math.cos(a) * 0.7f, -0.6f + 0.9f * rise + wav * 0.2f, (float) Math.sin(a) * 0.7f));
                p = add(p, mul(dir, 0.75f));
            }
            b.tube(path, Geo.taper(n, 0.55f, 0.08f, 1), seg(6), SLIME, true, true, 0, false);
        }
        return b.buildDynamic();
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        out.solid(p, base, gl);
        // creature (opaque, a l'interieur de la cuve)
        float lv = level(s);
        float rise = creatureRise(s);
        float cy = Ease.lerp(Math.min(lv - 3.8f, 11.5f), lv + 4.5f, rise) + (float) Math.sin(s.time * 0.06f) * 0.6f;
        float[] c = v(8 + (float) Math.sin(s.time * 0.023f) * 1.2f * (1 - rise), cy, 8 + (float) Math.cos(s.time * 0.031f) * 1.0f * (1 - rise));
        float wob = (float) Math.sin(s.time * 0.17f);
        p.push().translate(c[0], c[1], c[2]).rotY(s.time * 0.8f).scale(1 + 0.06f * wob, 1 - 0.06f * wob, 1 + 0.04f * wob);
        out.solid(p, blob, 0.8f + 0.2f * aw);
        p.pop();
        float look = (float) Math.sin(s.time * 0.05f) * 40;
        float blink = ((s.time + s.seed * 70) % 70) < 3 ? 0.15f : 1f;
        float[][] eyes = {{0, 1.4f, 2.9f, 1.0f}, {-1.7f, 0.6f, 2.5f, 0.7f}, {1.6f, 0.2f, 2.6f, 0.8f}};
        for (float[] e : eyes) {
            p.push().translate(c[0], c[1], c[2]).rotY(s.time * 0.8f).translate(e[0], e[1], e[2]).rotY(look).scale(e[3], e[3] * blink, e[3]);
            out.solid(p, eye, 1f);
            out.solid(p, pupil, 0f);
            out.additive(p, eye, 0xD8FF4A, 0.6f);
            p.pop();
        }
        out.solid(p, tendrils(s, c), 0.8f);
        // jauges : le liquide monte et descend
        for (int i = 0; i < GAUGES.length; i++) {
            float[] g = GAUGES[i];
            float h = (G_Y1 - G_Y0 - 0.4f) * Math.max(0.03f, gaugeLevel(s, i));
            p.push().translate(g[0], G_Y0 + 0.1f, g[1]).scale(1, h, 1);
            out.solid(p, gaugeLiquid, 1f);
            p.pop();
            // manometre : aiguille
            float side = g[0] < 8 ? -1 : 1;
            float[][] pipe = {v(g[0], G_Y1 + 0.5f, g[1]), v(g[0], G_Y1 + 2.5f, g[1] - 1), v(8 + side * 7f, 17.5f, 11.5f)};
            float[] dp = Geo.lerp(pipe[1], pipe[2], 0.35f);
            float needleAng = -120 + 240 * gaugeLevel(s, i) + (float) Math.sin(s.time * 1.3f + i) * 3;
            p.push().translate(dp[0], dp[1] + 1.4f, dp[2]).rotY(side * -35).rotX(90);
            out.solid(p, needle, 0.4f);
            p.pop();
            p.push().translate(dp[0], dp[1] + 1.4f, dp[2]).rotY(side * -35).translate(0, 0, 0.5f).rotZ(-needleAng);
            out.quad(p, Sprite.RAY, -0.12f, 0, 0.12f, 1.25f, 0xFF3020, 0.9f);
            p.pop();
        }
        // verins et couvercle
        float lift = lidLift(s) * 13f;
        for (int sd = -1; sd <= 1; sd += 2) {
            float px = 8 + sd * 9.4f, pz = 4.5f;
            p.push().translate(px, TANK_Y0, pz).scale(1.3f, 9.5f, 1.3f);
            out.solid(p, rod, gl);
            p.pop();
            p.push().translate(px, TANK_Y0 + 9.5f, pz).scale(0.8f, LID_Y + 1.5f - TANK_Y0 - 9.5f + lift, 0.8f);
            out.solid(p, rod, gl);
            p.pop();
        }
        p.push().translate(0, lift, 0);
        if (s.active) p.translate(8, 0, 8).rotY(Ease.range(s.t, CrateAnim.OPEN, CrateAnim.OPEN + 16) * 25f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.SLAM))).translate(-8, 0, -8);
        out.solid(p, lid, gl);
        float al = alarm(s);
        float blinkLamp = al > 0 ? 1 : (((s.time + s.seed * 60) % 60) < 6 ? 1f : 0.25f);
        out.translucent(p, beacon, 0xFF8A20, 0.85f);
        out.additive(p, beaconGlow, 0xFF7A10, 0.25f + 0.6f * blinkLamp);
        p.pop();
        // translucides : liquide et verre
        p.push().translate(0, TANK_Y0 + 0.05f, 0).scale(1, lv - TANK_Y0, 1);
        out.translucent(p, liquid, 0xFFFFFF, 0.5f);
        p.pop();
        p.push().translate(0, lv, 0).translate(8, 0, 8).rotY(s.time * 0.5f).translate(-8, 0, -8);
        out.translucent(p, liquidTop, 0xFFFFFF, 0.78f);
        out.additive(p, liquidTop, glow, 0.35f + 0.3f * aw);
        p.pop();
        out.translucent(p, glass, 0xDFFFE8, 0.9f);
        for (float[] g : GAUGES) {
            p.push().translate(g[0], 0, g[1]);
            out.translucent(p, gaugeGlass, 0xDFFFE8, 0.9f);
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
        // barils radioactifs en orbite
        float orb = orbitScale(s);
        for (int i = 0; i < 4; i++) {
            if (orb <= 0.001f) break;
            float ang = i * 90f + s.time * (0.8f + 0.15f * i) + (s.active ? s.t * s.t * 0.016f * (1 - Ease.range(s.t, CrateAnim.CLOSE, CrateAnim.END)) : 0);
            float rad = 20f + (i % 2) * 1.5f + 4f * ex;
            float y = 10f + 3f * (float) Math.sin(s.time * 0.05f + i * 1.6f) + 9f * aw;
            p.push().translate(8, 0, 8).rotZ(9f * (float) Math.sin(i * 2.1f)).rotY(ang).translate(rad, y, 0).rotX(s.time * 2f + i * 50).rotZ(s.time * 1.2f).scale(orb);
            out.solid(p, barrel, 0.7f);
            if (!s.item) out.billboard(p, Sprite.HALO, 0, 0, 0, 3.8f, 0, glow, 0.15f + 0.2f * ex);
            p.pop();
        }
        if (s.item) return;
        // lueur de la cuve
        float lv = level(s);
        out.billboard(p, Sprite.HALO, 8, (TANK_Y0 + lv) / 2, 8, 16f, 0, glow, 0.14f + 0.06f * s.pulse(0.1f) + 0.25f * aw);
        out.ground(p, Sprite.HALO, 8, 2.6f, 8, 19f, 0, glow, 0.12f + 0.2f * aw);
        CommonFx.cavity(s, p, out, this, 8, lv, 8, 6.5f);
        // gyrophare : faisceaux tournants
        float al = alarm(s);
        if (al > 0.01f) {
            float lift = lidLift(s) * 13f;
            float by = LID_Y + 7.3f + lift;
            for (int k = 0; k < 2; k++) {
                float a = s.time * 12f + k * 180;
                p.push().translate(8, by, 8).rotY(a).rotZ(-90);
                out.quad(p, Sprite.RAY, -1.2f, 0, 1.2f, 40f, 0xFF8A20, 0.45f * al);
                p.pop();
            }
            out.billboard(p, Sprite.FLARE, 8, by, 8, 6f, s.time * 4, 0xFF9A30, 0.6f * al);
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{8, 16, 8};
    }

    // =================================================================== particules
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        float lv = level(s);
        if (r.nextFloat() < 0.5f) {
            double a = r.nextDouble() * Math.PI * 2;
            float rr = r.nextFloat() * 6f;
            fx.particle(FxSink.BUBBLE, 8 + (float) Math.cos(a) * rr, TANK_Y0 + 0.5f + r.nextFloat() * 2, 8 + (float) Math.sin(a) * rr,
                    0, 0.25f + r.nextFloat() * 0.2f, 0, 0xC8FF7A, 0.4f + r.nextFloat() * 0.4f, (int) ((lv - TANK_Y0) / 0.45f));
        }
        for (float[] g : GAUGES) {
            if (r.nextFloat() < 0.15f) {
                fx.particle(FxSink.BUBBLE, g[0] + (r.nextFloat() - 0.5f), G_Y0 + 0.5f, g[1] + (r.nextFloat() - 0.5f), 0, 0.2f, 0, 0xC8FF7A, 0.3f, 20);
            }
        }
        if (r.nextFloat() < 0.05f) {
            fx.particle(FxSink.SMOKE, 8 + (r.nextFloat() - 0.5f) * 10, LID_Y + 4, 8 + (r.nextFloat() - 0.5f) * 10, 0, 0.15f, 0, 0xD8E8D0, 2.2f, 40);
        }
        if (r.nextFloat() < 0.1f) {
            fx.particle(FxSink.WISP, 8 + (r.nextFloat() - 0.5f) * 28, 3 + r.nextFloat() * 4, 8 + (r.nextFloat() - 0.5f) * 28, 0, 0.05f, 0, glow, 1.2f, 60);
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == CrateAnim.UNLOCK) fx.sound(Snd.TOXIC, 8, 20, 8, 1.0f, 1.0f);
        if (k == CrateAnim.OPEN) {
            fx.sound(Snd.WHOOSH, 8, 22, 8, 1.0f, 0.6f);
            fx.sound(Snd.TOXIC, 8, 18, 8, 1.2f, 0.8f);
            // jets de vapeur des events
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60 + 30);
                for (int q = 0; q < 6; q++) {
                    fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 6.5f, LID_Y + 3, 8 + (float) Math.sin(a) * 6.5f,
                            (float) Math.cos(a) * (0.4f + r.nextFloat() * 0.5f), 0.6f + r.nextFloat() * 0.6f, (float) Math.sin(a) * (0.4f + r.nextFloat() * 0.5f), 0xE8F0E0, 2.5f + r.nextFloat() * 1.5f, 30);
                }
            }
        }
        if (k > CrateAnim.OPEN && k < CrateAnim.CLOSE) {
            float lv = level(s);
            if (r.nextFloat() < 0.8f) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.BUBBLE, 8 + (float) Math.cos(a) * 5, lv, 8 + (float) Math.sin(a) * 5, (float) Math.cos(a) * 0.2f, 0.5f, (float) Math.sin(a) * 0.2f, 0xC8FF7A, 0.7f, 30);
            }
            if (r.nextFloat() < 0.35f) {
                fx.particle(FxSink.DROP, 8 + (r.nextFloat() - 0.5f) * 8, lv + 6, 8 + (r.nextFloat() - 0.5f) * 8, (r.nextFloat() - 0.5f) * 0.6f, 0.8f, (r.nextFloat() - 0.5f) * 0.6f, 0x7CFF2A, 0.8f, 30);
            }
        }
        if (k == CrateAnim.SLAM) {
            fx.sound(Snd.WHOOSH, 8, 22, 8, 0.6f, 1.4f);
            for (int i = 0; i < 20; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                fx.particle(FxSink.SMOKE, 8 + (float) Math.cos(a) * 9, LID_Y + 1, 8 + (float) Math.sin(a) * 9, (float) Math.cos(a) * 0.6f, 0.2f, (float) Math.sin(a) * 0.6f, 0xE8F0E0, 2.8f, 35);
            }
        }
    }
}
