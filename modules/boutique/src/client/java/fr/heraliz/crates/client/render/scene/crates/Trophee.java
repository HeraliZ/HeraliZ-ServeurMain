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
import fr.heraliz.crates.client.render.scene.Trail;

import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse Evenement : le Trophee. Une scene ronde laquee de noir cerclee de LED d'or, un tapis rouge, un podium de marbre
 * blanc (2 - 1 - 3) : la grande coupe d'or du champion trone sur la premiere marche sous une vitrine de verre (le
 * cadenas la ferme), la coupe d'argent et la coupe de bronze a ses cotes. Deux tours d'eclairage de stade encadrent la
 * scene, reliees par une poutre ou pend la banderole EVENT et une guirlande de fanions ; deux canons a confettis devant.
 * <p>A l'ouverture : roulement de tambour, les projecteurs balaient la scene puis se braquent tous sur la coupe ; coup de
 * cymbale : la vitrine s'envole, les canons crachent confettis et serpentins, la foule acclame, fanfare. La coupe s'eleve
 * en tournant, son couvercle s'ouvre et la lumiere d'or en jaillit (la roue sort de la coupe). Feux d'artifice pendant
 * la roue, seconde salve a l'arret.
 */
public final class Trophee extends Design {
    private static final Mat MARBLE = Mat.MAIN;
    private static final Mat STAGE = Mat.MAIN2;
    private static final Mat GOLD = Mat.TRIM;
    private static final Mat VELVET = Mat.ACCENT;
    private static final Mat NUMBERS = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat PLATE = Mat.DECAL_A;
    private static final Mat BANNER = Mat.DECAL_B;
    private static final Mat TRUSS = Mat.INTERIOR;
    private static final Mat SILVER = Mat.GEM;
    private static final Mat BLACK = Mat.DARK;
    private static final Mat BRONZE = Mat.SPECIAL;

    private static final int GOLD_RGB = 0xFFD86A;
    private static final int[] CONFETTI = {0xFFD86A, 0xFF3A4A, 0xFFFFFF, 0x3AA8FF, 0xFF7ADC, 0x6AFF8A};

    /** Scene ; podium (marche 1 au centre) ; trophee (bas du socle, bord de la coupe) ; vitrine ; tours. */
    private static final float STAGE_R = 12.5f, STAGE_H = 1.0f;
    private static final float P1_TOP = 7.0f, P2_TOP = 5.0f, P3_TOP = 4.0f;
    private static final float TX = 8, TZ = 6.5f, PLINTH_H = 2.4f, RIM_Y = PLINTH_H + 7.7f, RIM_R = 2.95f;
    private static final float CASE_X0 = 4.8f, CASE_X1 = 11.2f, CASE_Z0 = 3.3f, CASE_Z1 = 9.7f, CASE_H = 14.5f;
    private static final float[][] TOWER = {{-4f, -3f}, {20f, -3f}};
    private static final float TOWER_H = 26f;
    private static final float[][] CANNON = {{-2.5f, 16f}, {18.5f, 16f}};

    private Mesh site = Mesh.EMPTY, cup = Mesh.EMPTY, lid = Mesh.EMPTY, cupGlow = Mesh.EMPTY, silverCup = Mesh.EMPTY, bronzeCup = Mesh.EMPTY;
    private Mesh caseGlass = Mesh.EMPTY, caseFrame = Mesh.EMPTY, lamp = Mesh.EMPTY, beamCone = Mesh.EMPTY, leds = Mesh.EMPTY, pennants = Mesh.EMPTY;
    private final float[][] ledAt = new float[28][];

    public Trophee() {
        super("event", 0xFFD86A, 0xFF3A4A);
        keyRgb = 0xFFD86A;
        cavityRgb = 0xFFE9A0;
        cavityAccent = 0xFF3A4A;
        size = 1.4f;
        ringY = 45;
        ringR = 24;
        revealY = 51;
        riseFromY = P1_TOP + RIM_Y;
        lockFloorY = P1_TOP;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    /** Coupe a deux anses (repere : bas du socle au centre) ; socle noir a plaque. */
    private static void trophy(MeshBuilder b, Mat metal, boolean plate) {
        b.bevelBox(-1.7f, 0, -1.7f, 1.7f, PLINTH_H, 1.7f, 0.2f, BLACK);
        if (plate) b.face(PLATE, v(0, 0, 1), v(-1.5f, 0.6f, 1.8f), v(1.5f, 0.6f, 1.8f), v(1.5f, 1.8f, 1.8f), v(-1.5f, 1.8f, 1.8f));
        b.box(-1.8f, PLINTH_H - 0.2f, -1.8f, 1.8f, PLINTH_H, 1.8f, metal);
        b.push().translate(0, PLINTH_H, 0);
        b.lathe(new float[][]{{1.5f, 0}, {1.55f, 0.35f}, {0.55f, 0.7f}, {0.42f, 1.9f}, {0.85f, 2.3f}, {0.45f, 2.7f}, {0.7f, 3.0f},
                {2.1f, 4.1f}, {2.75f, 5.9f}, {2.9f, 7.3f}, {3.15f, 7.55f}, {RIM_R, 7.7f}}, 24, 0, 360, metal, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.01f, 3.3f}, {2.0f, 4.3f}, {2.6f, 5.9f}, {2.75f, 7.6f}}, 24, 0, 360, metal, 1, 1, 0, 0, 0, true);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(sd * 2.8f, 5.6f, 0).rotateZ(sd * -90f);
            b.torusXY(1.35f, 0.26f, 14, 5, metal);
            b.pop();
        }
        b.pop();
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // scene, liseres, tapis rouge
        b.push().translate(8, 0, 8);
        b.cylinder(STAGE_R, 0, STAGE_H, 44, BLACK, null, STAGE);
        b.push().translate(0, STAGE_H, 0);
        b.torus(STAGE_R, 0.28f, 44, 5, 0, 360, GOLD);
        b.pop();
        b.pop();
        b.box(5.8f, STAGE_H - 0.1f, 9.8f, 10.2f, STAGE_H + 0.22f, 20.3f, VELVET);
        b.box(5.6f, 0, 20.3f, 10.4f, 0.12f, 22.5f, VELVET);
        // podium : trois blocs de marbre, liseres d'or, plaques numerotees (2, 1, 3)
        float[][] blocks = {{-2.5f, 4.5f, 4f, 10f, P2_TOP}, {4.5f, 11.5f, 3f, 10f, P1_TOP}, {11.5f, 18.5f, 4f, 10f, P3_TOP}};
        for (int i = 0; i < 3; i++) {
            float[] q = blocks[i];
            b.bevelBox(q[0], STAGE_H, q[2], q[1], q[4], q[3], 0.15f, MARBLE);
            // liseré d'or en bandeau, juste sous le dessus (pas de face confondue avec le marbre)
            b.box(q[0] - 0.12f, q[4] - 0.42f, q[2] - 0.12f, q[1] + 0.12f, q[4] - 0.12f, q[3] + 0.12f, GOLD);
            Mat n = NUMBERS.sub((i % 2) / 2f, (i / 2) / 2f, (i % 2 + 1) / 2f, (i / 2 + 1) / 2f);
            float cx = (q[0] + q[1]) / 2, ph = q[4] - STAGE_H, hs = Math.min(1.35f, ph * 0.36f), cy = STAGE_H + (ph - 0.3f) / 2;
            b.face(n, v(0, 0, 1), v(cx - hs, cy - hs, q[3] + 0.16f), v(cx + hs, cy - hs, q[3] + 0.16f), v(cx + hs, cy + hs, q[3] + 0.16f), v(cx - hs, cy + hs, q[3] + 0.16f));
        }
        // tours d'eclairage (treillis), poutre, banderole
        for (float[] t : TOWER) {
            for (int i = 0; i < 4; i++) {
                float sx = i % 2 == 0 ? -0.6f : 0.6f, sz = i / 2 == 0 ? -0.6f : 0.6f;
                bar(b, v(t[0] + sx, 0, t[1] + sz), v(t[0] + sx, TOWER_H, t[1] + sz), 0.13f, TRUSS);
            }
            for (float y = 1.2f; y < TOWER_H; y += 2.4f) {
                bar(b, v(t[0] - 0.6f, y, t[1] + 0.6f), v(t[0] + 0.6f, y + 1.2f, t[1] + 0.6f), 0.06f, TRUSS);
                bar(b, v(t[0] + 0.6f, y + 1.2f, t[1] + 0.6f), v(t[0] - 0.6f, y + 2.4f, t[1] + 0.6f), 0.06f, TRUSS);
            }
            b.push().translate(t[0], 0, t[1]);
            b.bevelBox(-1.4f, 0, -1.4f, 1.4f, 0.8f, 1.4f, 0.15f, BLACK);
            b.pop();
        }
        bar(b, v(TOWER[0][0], TOWER_H - 1, TOWER[0][1]), v(TOWER[1][0], TOWER_H - 1, TOWER[1][1]), 0.25f, TRUSS);
        bar(b, v(TOWER[0][0], TOWER_H - 2.6f, TOWER[0][1]), v(TOWER[1][0], TOWER_H - 2.6f, TOWER[1][1]), 0.2f, TRUSS);
        float bz = TOWER[0][1] + 0.3f;
        b.face(BANNER, v(0, 0, 1), v(0, 18.2f, bz), v(16, 18.2f, bz), v(16, 21.6f, bz), v(0, 21.6f, bz));
        bar(b, v(0.5f, 21.6f, bz), v(0.5f, TOWER_H - 2.6f, bz), 0.05f, BLACK);
        bar(b, v(15.5f, 21.6f, bz), v(15.5f, TOWER_H - 2.6f, bz), 0.05f, BLACK);
        // canons a confettis
        for (float[] c : CANNON) {
            float yaw = (float) Math.toDegrees(Math.atan2(TX - c[0], TZ - c[1]));
            b.push().translate(c[0], STAGE_H * 0, c[1]);
            b.bevelBox(-1.2f, 0, -1.2f, 1.2f, 1.0f, 1.2f, 0.15f, BLACK);
            b.push().translate(0, 1.0f, 0).rotateY(yaw).rotateX(40);
            b.cylinder(0.7f, 0, 4.2f, 12, BLACK, BLACK, BLACK);
            b.push().translate(0, 3.6f, 0);
            b.torus(0.72f, 0.12f, 12, 4, 0, 360, GOLD);
            b.pop();
            b.pop();
            b.pop();
        }
        // coupes d'argent (marche 2) et de bronze (marche 3)
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();
        MeshBuilder sc = new MeshBuilder();
        trophy(sc, SILVER, false);
        silverCup = sc.build();
        MeshBuilder bc = new MeshBuilder();
        trophy(bc, BRONZE, false);
        bronzeCup = bc.build();
        MeshBuilder tc = new MeshBuilder();
        trophy(tc, GOLD, true);
        cup = tc.build();
        // couvercle (repere : charniere arriere au bord de la coupe) avec etoile
        MeshBuilder ld = new MeshBuilder();
        ld.push().translate(0, 0, RIM_R);
        ld.lathe(new float[][]{{RIM_R + 0.05f, 0}, {2.7f, 0.5f}, {1.7f, 1.2f}, {0.45f, 1.55f}, {0.01f, 1.6f}}, 24, 0, 360, GOLD, 1, 1, 0, 0, 0, false);
        ld.push().translate(0, 1.6f, 0);
        ld.cylinder(0.25f, 0, 0.8f, 8, GOLD, null, GOLD);
        ld.push().translate(0, 1.9f, -0.3f);
        float[][] st = new float[10][];
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(90 + i * 36);
            float r = i % 2 == 0 ? 1.3f : 0.55f;
            st[i] = new float[]{(float) Math.cos(a) * r, (float) Math.sin(a) * r};
        }
        ld.prism(st, 0.6f, GOLD, GOLD);
        ld.pop();
        ld.pop();
        ld.pop();
        lid = ld.build();
        MeshBuilder cg = new MeshBuilder();
        cg.push().translate(0, PLINTH_H + 5.0f, 0).scale(1f, 1.35f, 1f);
        cg.sphere(3.3f, 16, 10, Mat.WHITE);
        cg.pop();
        cupGlow = cg.build();
        // vitrine : verre et cadre d'or (repere : bas au niveau de la marche 1)
        MeshBuilder gl = new MeshBuilder();
        gl.box6(CASE_X0, 0.25f, CASE_Z0, CASE_X1, CASE_H, CASE_Z1, Mat.WHITE, Mat.WHITE, null, Mat.WHITE, Mat.WHITE, Mat.WHITE);
        caseGlass = gl.build();
        MeshBuilder cf = new MeshBuilder();
        float[][] cc = {{CASE_X0, CASE_Z0}, {CASE_X1, CASE_Z0}, {CASE_X1, CASE_Z1}, {CASE_X0, CASE_Z1}};
        for (int i = 0; i < 4; i++) {
            bar(cf, v(cc[i][0], 0, cc[i][1]), v(cc[i][0], CASE_H, cc[i][1]), 0.14f, GOLD);
            float[] a = cc[i], c = cc[(i + 1) % 4];
            bar(cf, v(a[0], CASE_H, a[1]), v(c[0], CASE_H, c[1]), 0.14f, GOLD);
            bar(cf, v(a[0], 0.15f, a[1]), v(c[0], 0.15f, c[1]), 0.16f, GOLD);
        }
        cf.bevelBox(CASE_X0 - 0.2f, CASE_H, CASE_Z0 - 0.2f, CASE_X1 + 0.2f, CASE_H + 0.5f, CASE_Z1 + 0.2f, 0.12f, BLACK);
        cf.push().translate((CASE_X0 + CASE_X1) / 2, CASE_H + 0.5f, (CASE_Z0 + CASE_Z1) / 2);
        cf.sphere(0.6f, 10, 6, GOLD);
        cf.pop();
        cf.box(TX - 0.6f, 0.4f, CASE_Z1 - 0.05f, TX + 0.6f, 1.4f, CASE_Z1 + 0.25f, GOLD);
        caseFrame = cf.build();
        // projecteur (repere : centre, faisceau vers +Z)
        MeshBuilder lp = new MeshBuilder();
        lp.push().rotateX(90);
        lp.cylinder(0.9f, -0.9f, 0.6f, 12, BLACK, BLACK, Mat.WHITE);
        lp.pop();
        lamp = lp.build();
        MeshBuilder bcn = new MeshBuilder();
        Geo.cone(bcn, v(0, 0, 0), v(0, 0, -1), 1f, 1f, 16, Mat.WHITE);
        beamCone = bcn.build();
        // LED du pourtour
        MeshBuilder le = new MeshBuilder();
        for (int i = 0; i < ledAt.length; i++) {
            ledAt[i] = Geo.polar(8, 8, i * 360f / ledAt.length, STAGE_R - 0.5f, STAGE_H + 0.05f);
            le.push().translate(ledAt[i][0], ledAt[i][1], ledAt[i][2]);
            le.sphere(0.25f, 6, 4, Mat.WHITE);
            le.pop();
        }
        leds = le.build();
        // guirlande de fanions entre les tours
        MeshBuilder pn = new MeshBuilder();
        Mat[] pm = {GOLD, VELVET, SILVER, BRONZE};
        int np = 14;
        for (int i = 0; i < np; i++) {
            float u0 = i / (float) np, u1 = (i + 0.8f) / np;
            float x0 = Ease.lerp(TOWER[0][0], TOWER[1][0], u0), x1 = Ease.lerp(TOWER[0][0], TOWER[1][0], u1);
            float y0 = TOWER_H - 1.2f - 3f * (float) Math.sin(Math.PI * u0), y1 = TOWER_H - 1.2f - 3f * (float) Math.sin(Math.PI * u1);
            float xm = (x0 + x1) / 2, ym = (y0 + y1) / 2 - 1.8f;
            for (int f = -1; f <= 1; f += 2) pn.face(pm[i % 4], v(0, 0, f), v(x0, y0, TOWER[0][1] + 0.5f), v(x1, y1, TOWER[0][1] + 0.5f), v(xm, ym, TOWER[0][1] + 0.5f));
        }
        pennants = pn.build();

        // cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.DISC;
        ps.w = 3.2f;
        ps.h = 3.2f;
        ps.d = 1.3f;
        ps.height = 1.4f;
        ps.halfSpan = 0.85f;
        ps.wire = 0.32f;
        ps.body = VELVET;
        ps.face = LOCKFACE;
        ps.shackle = GOLD;
        ps.trim = GOLD;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(GOLD, GOLD, 0.5f, (kb, kg) -> {
            // tete de cle : petite coupe d'or
            kb.push().translate(0, 1.2f, 0).scale(0.55f);
            trophy(kb, GOLD, false);
            kb.pop();
            kg.push().translate(0, 1.2f + 0.55f * (PLINTH_H + 7.7f), 0);
            kg.torus(0.55f * RIM_R, 0.2f, 16, 4, 0, 360, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    // =================================================================== chronologie
    private static final float DRUM = CrateAnim.UNLOCK + 1, CYMBAL = DRUM + 36;
    private static final float LOCKON = CYMBAL - 4;
    private static final float CASE_UP0 = CYMBAL - 2, CASE_UP1 = CYMBAL + 14;
    private static final float RISE0 = CYMBAL + 2, RISE1 = CYMBAL + 18, LID0 = RISE1 - 3, LID1 = RISE1 + 7;
    private static final float C_LID0 = CrateAnim.CLOSE, C_LID1 = CrateAnim.CLOSE + 6, C_DOWN0 = CrateAnim.CLOSE + 4, C_DOWN1 = CrateAnim.CLOSE + 13;
    private static final float C_CASE0 = CrateAnim.CLOSE + 10, C_CASE1 = CrateAnim.SLAM;
    /** Hauteur dont la coupe s'eleve, hauteur dont le couvercle se souleve (px). */
    private static final float CUP_LIFT = 7f, LID_LIFT = 3.6f;

    /** Hauteur de la vitrine au-dessus de sa pose (et opacite). */
    private static float caseLift(CrateState s) {
        if (!s.active) return 0;
        float up = Ease.inOutCubic(Ease.range(s.t, CASE_UP0, CASE_UP1));
        float down = Ease.inOutCubic(Ease.range(s.t, C_CASE0, C_CASE1));
        return 22f * up * (1 - down);
    }

    private static float trophyLift(CrateState s) {
        if (!s.active) return 0;
        return Ease.inOutCubic(Ease.range(s.t, RISE0, RISE1)) * (1 - Ease.inOutCubic(Ease.range(s.t, C_DOWN0, C_DOWN1)));
    }

    private static float lidOpen(CrateState s) {
        if (!s.active) return 0;
        return Ease.outBack(Ease.range(s.t, LID0, LID1), 1.3f) * (1 - Ease.inOutCubic(Ease.range(s.t, C_LID0, C_LID1)));
    }

    /** Rotation de la coupe (degres) : un tour lent en s'elevant, puis une rotation de presentation ; revient a 0. */
    private static float cupSpin(CrateState s) {
        if (!s.active || s.t < RISE0) return 0;
        float t = s.t;
        float a = 360f * Ease.inOutCubic(Ease.range(t, RISE0, RISE1 + 6)) + 0.9f * Math.max(0, Math.min(t, C_DOWN0) - (RISE1 + 6));
        if (t > C_DOWN0) {
            float target = (float) Math.ceil(a / 360f) * 360f;
            a = Ease.lerp(a, target, Ease.inOutCubic(Ease.range(t, C_DOWN0, C_DOWN1)));
        }
        return a;
    }

    /** Frisson de la coupe pendant le roulement de tambour (px). */
    private static float cupShiver(CrateState s) {
        if (!s.active) return 0;
        return 0.12f * Ease.range(s.t, DRUM, CYMBAL) * (s.t < CYMBAL ? 1 : 0) * (float) Math.sin(s.t * 4.1f);
    }

    /** Cible d'un projecteur (tour a, lampe i) : balayage pendant le roulement, braque sur la coupe ensuite. */
    private static float[] beamTarget(CrateState s, int a, int i) {
        float[] cupAt = v(TX, P1_TOP + PLINTH_H + 4 + CUP_LIFT * trophyLift(s), TZ);
        if (!s.active) {
            float ph = s.time * 0.01f + a * 1.7f + i * 0.9f;
            return v(8 + 9f * (float) Math.sin(ph), STAGE_H, 8 + 7f * (float) Math.cos(ph * 1.3f));
        }
        float t = s.t;
        float ph = t * (0.08f + 0.03f * i) + a * 2.1f + i * 1.3f;
        float[] sweep = v(8 + 11f * (float) Math.sin(ph), STAGE_H, 8 + 9f * (float) Math.cos(ph * 1.2f));
        float lockOn = Ease.inOutCubic(Ease.range(t, LOCKON - 5, LOCKON));
        float[] tgt = Geo.lerp(sweep, cupAt, lockOn);
        if (t > CrateAnim.SPIN && t < CrateAnim.CLOSE) {
            float w = Ease.smooth(Ease.range(t, CrateAnim.SPIN, CrateAnim.SPIN + 10)) * (1 - Ease.smooth(Ease.range(t, CrateAnim.STOP - 10, CrateAnim.STOP)));
            float[] circle = Geo.polar(8, 8, t * 3f + a * 180 + i * 90, 9f, STAGE_H);
            tgt = Geo.lerp(tgt, circle, w);
        }
        return tgt;
    }

    private static float[] lampPos(int a, int i) {
        return v(TOWER[a][0] + (a == 0 ? 1.2f : -1.2f), TOWER_H - 3.6f - i * 2.0f, TOWER[a][1] + 1.0f);
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float c = s.t >= CYMBAL ? 0.45f * (float) Math.exp(-(s.t - CYMBAL) * 0.3f) : 0;
        return Math.max(c, 0.3f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        out.solid(p, site, gl);
        out.solid(p, pennants, gl);
        // LED : chenille doree, s'emballe a l'ouverture
        for (int i = 0; i < ledAt.length; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(s.time * (0.06f + 0.25f * aw) - i * 0.45f);
            float[] q = ledAt[i];
            out.billboard(p, Sprite.HALO, q[0], q[1] + 0.3f, q[2], 0.9f, 0, i % 2 == 0 ? GOLD_RGB : 0xFFFFFF, 0.2f + 0.35f * wave * wave);
        }
        out.additive(p, leds, GOLD_RGB, 0.5f + 0.4f * aw);
        // coupes d'argent et de bronze
        p.push().translate(1f, P2_TOP, 7f).rotY(12f).scale(0.6f);
        out.solid(p, silverCup, gl);
        p.pop();
        p.push().translate(15f, P3_TOP, 7f).rotY(-12f).scale(0.55f);
        out.solid(p, bronzeCup, gl);
        p.pop();
        // la coupe d'or : s'eleve en tournant, couvercle qui s'ouvre
        float lift = trophyLift(s);
        float bob = 0.25f * (float) Math.sin(s.time * 0.08f) * lift;
        float sh = cupShiver(s);
        p.push().translate(TX + sh, P1_TOP + CUP_LIFT * lift + bob, TZ).rotY(cupSpin(s)).rotZ(2.5f * sh);
        out.solid(p, cup, gl);
        // couvercle : se souleve tout droit au-dessus de la coupe, penche un peu et flotte
        float lo = lidOpen(s);
        float lidY = RIM_Y + LID_LIFT * lo + 0.25f * (float) Math.sin(s.time * 0.11f) * Math.min(1, lo);
        p.push().translate(0, lidY, 0).rotY(25f * lo).rotX(-9f * Math.min(1, lo)).translate(0, 0, -RIM_R);
        out.solid(p, lid, gl);
        p.pop();
        out.additive(p, cupGlow, GOLD_RGB, 0.06f + 0.16f * lo + 0.25f * Seq.stopFlash(s));
        p.pop();
        // vitrine
        float cl = caseLift(s);
        float fade = 1 - Ease.smooth(Ease.range(cl, 5f, 15f));
        if (fade > 0.01f) {
            p.push().translate(0, P1_TOP + cl, 0).rotY(0);
            out.solid(p, caseFrame, gl);
            out.translucent(p, caseGlass, 0xD8F4FF, 0.14f * fade);
            p.pop();
        }
        // projecteurs orientes vers leur cible
        for (int a = 0; a < 2; a++) {
            for (int i = 0; i < 3; i++) {
                float[] l = lampPos(a, i), t = beamTarget(s, a, i);
                float[] d = Geo.sub(t, l);
                float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
                float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
                p.push().translate(l[0], l[1], l[2]).rotY(yaw).rotX(-pitch);
                out.solid(p, lamp, 1.2f);
                p.pop();
            }
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(TX, P1_TOP + 0.7f + caseLift(s), CASE_Z1 + 0.4f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        // faisceaux de projecteurs (cones) et taches au sol
        for (int a = 0; a < 2; a++) {
            for (int i = 0; i < 3; i++) {
                float[] l = lampPos(a, i), t = beamTarget(s, a, i);
                float[] d = Geo.sub(l, t);
                float len = Geo.len(d);
                float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
                float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
                int col = i == 1 ? 0xFFE9B0 : 0xFFFFFF;
                p.push().translate(t[0], t[1], t[2]).rotY(yaw).rotX(-pitch).scale(2.4f, 2.4f, len);
                out.additive(p, beamCone, col, 0.04f + 0.05f * aw);
                p.pop();
                out.ground(p, Sprite.HALO, t[0], STAGE_H + 0.45f, t[2], 3.2f, 0, col, 0.2f + 0.15f * aw);
                out.billboard(p, Sprite.FLARE, l[0], l[1], l[2] + 0.6f, 1.6f, s.time, col, 0.6f + 0.3f * aw);
            }
        }
        out.ground(p, Sprite.HALO, 8, STAGE_H + 0.4f, 8, 17f, 0, GOLD_RGB, 0.08f + 0.15f * aw);
        // reflets sur la vitrine et la coupe
        float ph = (s.time * 0.02f) % 1f;
        if (ph < 0.25f) {
            float u = ph / 0.25f;
            out.billboard(p, Sprite.GLINT, CASE_X0 + (CASE_X1 - CASE_X0) * u, P1_TOP + 12 - 8 * u, CASE_Z1 + 0.2f, 2.2f, s.time * 3, 0xFFFFFF, 0.8f * Ease.bell(u, 0, 1));
        }
        out.billboard(p, Sprite.GLINT, TX + 1.8f, P1_TOP + PLINTH_H + 6f + CUP_LIFT * trophyLift(s), TZ + 2.5f, 2f, s.time * 2, 0xFFFFFF, 0.5f + 0.4f * (float) Math.sin(s.time * 0.13f));
        if (!s.active) return;
        float t = s.t;
        // cymbale : eclair d'or, onde, colonne de lumiere sortant de la coupe
        if (t >= CYMBAL) {
            float e = (float) Math.exp(-(t - CYMBAL) * 0.25f);
            float cy = P1_TOP + PLINTH_H + 6 + CUP_LIFT * trophyLift(s);
            out.billboard(p, Sprite.FLARE, TX, cy, TZ, 6f + 20f * e, s.time, 0xFFFFFF, e);
            out.rays(p, TX, cy, TZ, 16, 30f * (0.4f + 0.6f * e), 2.6f, s.time * 1.3f, GOLD_RGB, 0.55f * e);
            out.ground(p, Sprite.SHOCK, 8, STAGE_H + 0.5f, 8, 4f + 30f * (1 - e), 0, GOLD_RGB, 0.8f * e);
            float lo = lidOpen(s);
            out.beam(p, TX, P1_TOP + RIM_Y + CUP_LIFT * trophyLift(s), TZ, 2.4f, 26f, GOLD_RGB, 0.35f * lo);
        }
        // serpentins des canons
        for (int c = 0; c < 2; c++) {
            for (float shot : new float[]{CYMBAL, CrateAnim.STOP}) {
                float age = t - shot;
                if (age < 0 || age > 22) continue;
                for (int k = 0; k < 4; k++) {
                    float[][] pts = new float[7][];
                    for (int i = 0; i < pts.length; i++) pts[i] = streamer(c, k, Math.max(0, age - i * 0.8f));
                    out.additive(p, Trail.ribbon(pts, 0.6f, 0.2f), CONFETTI[(k + c * 2) % CONFETTI.length], 0.8f * (1 - age / 22));
                }
            }
        }
        if (lidOpen(s) > 0.01f) CommonFx.cavity(s, p, out, this, TX, P1_TOP + RIM_Y + CUP_LIFT * trophyLift(s), TZ, 4f, GOLD_RGB);
    }

    /** Position d'un serpentin (canon c, brin k) a l'age a (ticks). */
    private static float[] streamer(int c, int k, float a) {
        float[] o = v(CANNON[c][0], 4.2f, CANNON[c][1]);
        float dx = (TX - o[0]) * 0.05f + (k - 1.5f) * 0.12f, dz = (TZ - o[2]) * 0.05f + (k % 2 == 0 ? 0.1f : -0.1f);
        float vy = 1.5f + 0.15f * k;
        return v(o[0] + dx * a * 1.2f, o[1] + vy * a - 0.06f * a * a, o[2] + dz * a * 1.2f + 0.3f * (float) Math.sin(a * 0.6f + k));
    }

    @Override
    public float[] cavityPos() {
        return new float[]{TX, P1_TOP + RIM_Y + 5, TZ};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.08f) {
            fx.particle(FxSink.SPARK, TX + rnd(r, 3f), P1_TOP + 4 + r.nextFloat() * 8, TZ + rnd(r, 3f), 0, 0.03f, 0, GOLD_RGB, 1.0f, 26);
        }
    }

    private static void cannonShot(FxSink fx, Random r, int c) {
        float[] o = v(CANNON[c][0], 4.2f, CANNON[c][1]);
        fx.sound(Snd.CANNON, o[0], o[1], o[2], 1.1f, 0.95f + 0.1f * c);
        for (int i = 0; i < 60; i++) {
            float dx = (TX - o[0]) * 0.05f + rnd(r, 0.25f), dz = (TZ - o[2]) * 0.05f + rnd(r, 0.25f);
            fx.particle(FxSink.CONFETTI, o[0], o[1], o[2], dx * (1 + r.nextFloat()), 1.4f + r.nextFloat() * 1.4f, dz * (1 + r.nextFloat()),
                    CONFETTI[r.nextInt(CONFETTI.length)], 1.3f + r.nextFloat() * 0.8f, 50 + r.nextInt(30));
        }
        burst(fx, r, o[0], o[1] + 0.5f, o[2], 0xFFFFFF, GOLD_RGB, 14, 0.8f, 1.2f);
    }

    private static void firework(FxSink fx, Random r, float x, float y, float z, int rgb) {
        fx.sound(Snd.FIREWORK, x, y, z, 0.9f, 0.9f + r.nextFloat() * 0.3f);
        for (int i = 0; i < 36; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(2 * r.nextDouble() - 1);
            float sp = 1.0f + r.nextFloat() * 0.4f;
            fx.particle(FxSink.SPARK, x, y, z, (float) (Math.sin(ph) * Math.cos(th)) * sp, (float) Math.cos(ph) * sp, (float) (Math.sin(ph) * Math.sin(th)) * sp,
                    i % 4 == 0 ? 0xFFFFFF : rgb, 1.5f, 22 + r.nextInt(12));
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) DRUM) fx.sound(Snd.DRUMROLL, 8, 12, 8, 1.2f, 1.0f);
        if (k == (int) LOCKON) fx.sound(Snd.CLANG, 8, 20, 0, 0.5f, 1.8f);
        if (k == (int) CYMBAL) {
            cannonShot(fx, r, 0);
            cannonShot(fx, r, 1);
            fx.sound(Snd.CHEER, 8, 10, 12, 1.1f, 1.0f);
            burst(fx, r, TX, P1_TOP + 9, TZ, GOLD_RGB, 0xFFFFFF, 40, 1.8f, 1.6f);
        }
        if (k == (int) CYMBAL + 3) fx.sound(Snd.FANFARE, 8, 14, 8, 1.1f, 1.0f);
        if (k == (int) LID0 + 2) fx.sound(Snd.GEM_CHIME, TX, P1_TOP + RIM_Y + 4, TZ, 0.8f, 1.1f);
        // feux d'artifice pendant la roue
        if (k > CrateAnim.SPIN && k < CrateAnim.STOP - 10 && (k - CrateAnim.SPIN) % 22 == 0) {
            float[] q = Geo.polar(8, 8, r.nextFloat() * 360, 10 + r.nextFloat() * 6, 30 + r.nextFloat() * 8);
            fx.sound(Snd.FIREWORK_LAUNCH, q[0], 5, q[2], 0.6f, 1.0f);
            firework(fx, r, q[0], q[1], q[2], CONFETTI[r.nextInt(CONFETTI.length)]);
        }
        if (k == CrateAnim.STOP) {
            cannonShot(fx, r, 0);
            cannonShot(fx, r, 1);
            fx.sound(Snd.CHEER, 8, 10, 12, 0.9f, 1.05f);
            for (int i = 0; i < 3; i++) {
                float[] q = Geo.polar(8, 8, i * 120 + 30, 12, 32 + i * 3);
                firework(fx, r, q[0], q[1], q[2], CONFETTI[i]);
            }
        }
        if (k == (int) C_CASE1) fx.sound(Snd.CLANG, TX, P1_TOP + 1, TZ, 0.7f, 1.4f);
    }
}
