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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.heraliz.crates.client.render.scene.Geo.v;

/**
 * Caisse Build : le Chantier. Une dalle de beton cerclee de bandes hazard, une grue a tour jaune (mat en treillis,
 * fleche, contre-fleche lestee, gyrophare) qui tient suspendu un conteneur militaire olive "HZ BUILD - KIT BUNKER" ;
 * cones de chantier, parpaings, caisse a outils. Le cadenas ferme le loquet du conteneur.
 * <p>A l'ouverture : la grue descend le conteneur (choc au sol, poussiere), les quatre parois tombent a plat, la grue
 * enleve le toit et l'emporte ; le plan s'ouvre sur la paroi avant et projette l'hologramme du bunker. Pendant que la
 * roue tourne, les parpaings s'envolent de la pile et montent les murs rangee par rangee, la grue revient poser le toit,
 * la porte blindee roule dans son cadre et s'ouvre a l'arret de la roue dans un flot de lumiere. A la fermeture tout se
 * demonte a l'envers et la grue remonte le conteneur.
 */
public final class Chantier extends Design {
    private static final Mat CONCRETE = Mat.MAIN;
    private static final Mat OLIVE = Mat.MAIN2;
    private static final Mat YELLOW = Mat.TRIM;
    private static final Mat HAZARD = Mat.ACCENT;
    private static final Mat STENCIL = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat PLAN = Mat.DECAL_A;
    private static final Mat VAULT = Mat.DECAL_B;
    private static final Mat BLOCKS = Mat.INTERIOR.withWorld(8);
    private static final Mat STEEL = Mat.GEM;
    private static final Mat BLACK = Mat.DARK;
    private static final Mat ORANGE = Mat.SPECIAL;

    private static final int HOLO = 0x6FD8FF;

    /** Dalle ; grue (pied du mat, sommet, fleche) ; conteneur (centre, demi-tailles, hauteur, epaisseur des parois). */
    private static final float PAD_T = 1f;
    private static final float MX = -2.5f, MZ = -2.5f, MW = 1.2f, MAST_TOP = 25f, JIB_L = 19.5f, CJIB_L = 6.5f;
    private static final float PHI0 = -45f, PHI_SIDE = -8f;
    private static final float RT = (float) Math.hypot(8 - MX, 8 - MZ);
    private static final float CX = 8, CZ = 8, HX = 7, HZ = 6, CH = 6.4f, PT = 0.4f, SLING = 4.6f, HANG_B = 8.5f, ROOF_HIGH = 20f;
    /** Bunker : base (plancher du conteneur), hauteur de rangee, rangees, epaisseur des murs, porte. */
    private static final float B_Y0 = PAD_T + PT, RH = 1.3f, WT = 1.4f;
    private static final int ROWS = 4;
    private static final float B_TOP = B_Y0 + ROWS * RH;
    private static final float DOOR_X0 = 6f, DOOR_X1 = 10f, DOOR_Y = B_Y0 + 1.95f, DOOR_R = 1.85f, DOOR_Z = CZ + HZ - 0.45f;
    private static final float PILE_H = 3.2f;

    private Mesh site = Mesh.EMPTY, mast = Mesh.EMPTY, slew = Mesh.EMPTY, beacon = Mesh.EMPTY, trolley = Mesh.EMPTY, hook = Mesh.EMPTY;
    private Mesh floor = Mesh.EMPTY, pFront = Mesh.EMPTY, pBack = Mesh.EMPTY, pLeft = Mesh.EMPTY, pRight = Mesh.EMPTY, roof = Mesh.EMPTY;
    private Mesh pile = Mesh.EMPTY, plates = Mesh.EMPTY, door = Mesh.EMPTY, doorFrame = Mesh.EMPTY, sheet = Mesh.EMPTY, planHolo = Mesh.EMPTY;
    private Mesh holoWire = Mesh.EMPTY, holoGrid = Mesh.EMPTY, scanPlane = Mesh.EMPTY;
    private final List<float[]> pieces = new ArrayList<>();
    private Mesh[] pieceMesh = new Mesh[0];

    public Chantier() {
        super("build", 0xFFB020, 0x6FD8FF);
        keyRgb = 0xFFC030;
        cavityRgb = 0xFFD070;
        cavityAccent = 0x6FD8FF;
        size = 1.3f;
        ringY = 47;
        ringR = 24;
        revealY = 53;
        riseFromY = 8;
        lockFloorY = PAD_T;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 4, m, true, true, 45, false);
    }

    @Override
    protected void build() {
        // ---------------------------------------------------------------- dalle, accessoires
        MeshBuilder b = new MeshBuilder();
        float p0 = -5.5f, p1 = 21.5f;
        b.box6(p0, 0, p0, p1, PAD_T, p1, HAZARD, HAZARD, null, CONCRETE, HAZARD, HAZARD);
        // semelle du mat
        b.bevelBox(MX - 2.1f, PAD_T, MZ - 2.1f, MX + 2.1f, PAD_T + 0.7f, MZ + 2.1f, 0.15f, CONCRETE);
        for (int i = 0; i < 4; i++) {
            float bx = MX + (i % 2 == 0 ? -1 : 1) * 1.6f, bz = MZ + (i / 2 == 0 ? -1 : 1) * 1.6f;
            b.push().translate(bx, PAD_T + 0.7f, bz);
            b.cylinder(0.22f, 0, 0.3f, 6, STEEL, null, STEEL);
            b.pop();
        }
        // cones de chantier
        for (float[] c : new float[][]{{19.6f, 19.6f}, {-3.6f, 19.8f}, {20.0f, 12.5f}}) {
            b.box(c[0] - 1.2f, PAD_T, c[1] - 1.2f, c[0] + 1.2f, PAD_T + 0.3f, c[1] + 1.2f, BLACK);
            b.push().translate(c[0], PAD_T + 0.3f, c[1]);
            b.lathe(new float[][]{{0.95f, 0}, {0.8f, 0.4f}, {0.28f, 2.9f}, {0.12f, 3.05f}, {0, 3.05f}}, 12, 0, 360, ORANGE, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        // pile de parpaings et caisse a outils
        b.box(16.2f, PAD_T, -4.0f, 20.2f, PAD_T + 1.3f, -2.0f, BLOCKS);
        b.box(16.2f, PAD_T, -1.9f, 20.2f, PAD_T + 1.3f, 0.1f, BLOCKS);
        b.box(17.2f, PAD_T + 1.3f, -3.0f, 21.2f, PAD_T + 2.6f, -1.0f, BLOCKS);
        b.bevelBox(-4.4f, PAD_T, 14.0f, -1.2f, PAD_T + 1.6f, 15.8f, 0.15f, ORANGE);
        b.box(-4.2f, PAD_T + 1.55f, 14.8f, -1.4f, PAD_T + 1.7f, 15.0f, BLACK);
        bar(b, v(-3.6f, PAD_T + 1.7f, 14.9f), v(-2.0f, PAD_T + 1.7f, 14.9f), 0.12f, STEEL);
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();

        // ---------------------------------------------------------------- mat en treillis
        MeshBuilder m = new MeshBuilder();
        float y0 = PAD_T + 0.7f;
        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -MW : MW, sz = i / 2 == 0 ? -MW : MW;
            bar(m, v(MX + sx, y0, MZ + sz), v(MX + sx, MAST_TOP, MZ + sz), 0.16f, YELLOW);
        }
        int sections = (int) ((MAST_TOP - y0) / (MW * 2));
        float sh = (MAST_TOP - y0) / sections;
        float[][] corners = {{-MW, -MW}, {MW, -MW}, {MW, MW}, {-MW, MW}};
        for (int k = 0; k <= sections; k++) {
            float y = y0 + k * sh;
            for (int f = 0; f < 4; f++) {
                float[] a = corners[f], c = corners[(f + 1) % 4];
                bar(m, v(MX + a[0], y, MZ + a[1]), v(MX + c[0], y, MZ + c[1]), 0.07f, YELLOW);
                if (k < sections) {
                    boolean flip = (k + f) % 2 == 0;
                    float[] lo = flip ? a : c, hi = flip ? c : a;
                    bar(m, v(MX + lo[0], y, MZ + lo[1]), v(MX + hi[0], y + sh, MZ + hi[1]), 0.06f, YELLOW);
                }
            }
        }
        Occlusion.bake(m, 0.3f, 3f, 0.4f, Float.NaN);
        mast = m.build();

        // ---------------------------------------------------------------- partie tournante (repere : axe du mat, fleche en +X)
        MeshBuilder s = new MeshBuilder();
        s.cylinder(1.5f, 0, 0.8f, 16, STEEL, null, STEEL);
        s.box(-1.9f, 0.8f, -1.3f, 2.2f, 1.05f, 1.3f, YELLOW);
        s.bevelBox(0.3f, 1.05f, 1.35f, 2.5f, 3.2f, 3.3f, 0.12f, YELLOW);
        s.face(BLACK, v(1, 0, 0), v(2.52f, 2.0f, 1.55f), v(2.52f, 2.0f, 3.1f), v(2.52f, 3.0f, 3.1f), v(2.52f, 3.0f, 1.55f));
        s.face(BLACK, v(0, 0, 1), v(0.5f, 2.0f, 3.32f), v(2.3f, 2.0f, 3.32f), v(2.3f, 3.0f, 3.32f), v(0.5f, 3.0f, 3.32f));
        // fleche : treillis triangulaire
        float jb = 1.0f, jt = 2.4f, jw = 0.75f;
        bar(s, v(0, jb, -jw), v(JIB_L, jb, -jw), 0.13f, YELLOW);
        bar(s, v(0, jb, jw), v(JIB_L, jb, jw), 0.13f, YELLOW);
        bar(s, v(0, jt, 0), v(JIB_L - 1.2f, jt, 0), 0.13f, YELLOW);
        for (float x = 0; x < JIB_L - 0.5f; x += 1.6f) {
            bar(s, v(x, jb, -jw), v(x, jb, jw), 0.06f, YELLOW);
            float xm = Math.min(x + 0.8f, JIB_L - 1.2f), xn = Math.min(x + 1.6f, JIB_L);
            for (int sd = -1; sd <= 1; sd += 2) {
                bar(s, v(x, jb, sd * jw), v(xm, jt, 0), 0.06f, YELLOW);
                bar(s, v(xm, jt, 0), v(xn, jb, sd * jw), 0.06f, YELLOW);
            }
        }
        s.box(JIB_L - 0.2f, jb - 0.2f, -0.9f, JIB_L + 0.3f, jb + 0.6f, 0.9f, YELLOW);
        // contre-fleche, passerelle, lest
        bar(s, v(0, jb, -jw), v(-CJIB_L, jb, -jw), 0.13f, YELLOW);
        bar(s, v(0, jb, jw), v(-CJIB_L, jb, jw), 0.13f, YELLOW);
        s.box(-CJIB_L, jb - 0.1f, -0.8f, -0.5f, jb, 0.8f, STEEL);
        for (int i = 0; i < 3; i++) {
            float xa = -CJIB_L + 0.1f + i * 0.85f;
            s.bevelBox(xa, jb - 1.4f, -1.0f, xa + 0.8f, jb + 1.2f, 1.0f, 0.08f, CONCRETE);
        }
        // tete (A) et haubans
        float ay = 5.4f;
        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -0.9f : 0.9f, sz = i / 2 == 0 ? -jw : jw;
            bar(s, v(sx, jb, sz), v(0, ay, 0), 0.1f, YELLOW);
        }
        bar(s, v(0, ay, 0), v(JIB_L * 0.62f, jt, 0), 0.05f, STEEL);
        bar(s, v(0, ay, 0), v(-CJIB_L, jb + 0.2f, 0), 0.05f, STEEL);
        s.push().translate(0, ay, 0);
        s.cylinder(0.32f, 0, 0.3f, 10, STEEL, null, STEEL);
        s.cylinder(0.26f, 0.3f, 0.75f, 10, ORANGE, null, ORANGE);
        s.pop();
        Occlusion.bake(s, 0.3f, 3f, 0.4f, Float.NaN);
        slew = s.build();
        MeshBuilder bc = new MeshBuilder();
        bc.push().translate(0, ay + 0.55f, 0);
        bc.sphere(0.36f, 10, 6, Mat.WHITE);
        bc.pop();
        beacon = bc.build();
        // chariot et moufle
        MeshBuilder tr = new MeshBuilder();
        tr.box(-0.7f, -0.35f, -0.95f, 0.7f, 0.25f, 0.95f, YELLOW);
        for (int i = 0; i < 4; i++) {
            tr.push().translate(i % 2 == 0 ? -0.45f : 0.45f, 0.3f, i / 2 == 0 ? -0.75f : 0.75f).rotateX(90);
            tr.cylinder(0.2f, -0.08f, 0.08f, 8, BLACK, BLACK, BLACK);
            tr.pop();
        }
        trolley = tr.build();
        MeshBuilder hk = new MeshBuilder();
        hk.bevelBox(-0.65f, -1.4f, -0.4f, 0.65f, 0, 0.4f, 0.1f, ORANGE);
        hk.face(HAZARD, v(0, 0, 1), v(-0.6f, -1.3f, 0.41f), v(0.6f, -1.3f, 0.41f), v(0.6f, -0.2f, 0.41f), v(-0.6f, -0.2f, 0.41f));
        hk.push().translate(0, -2.0f, 0).rotateY(90);
        hk.torus(0.45f, 0.14f, 12, 5, 200, 520, STEEL);
        hk.pop();
        hook = hk.build();

        // ---------------------------------------------------------------- conteneur (repere : bas au centre)
        MeshBuilder fl = new MeshBuilder();
        fl.box(-HX, 0, -HZ, HX, PT, HZ, BLACK);
        fl.box(-HX, 0.05f, -HZ - 0.05f, HX, 0.35f, -HZ + 0.3f, YELLOW);
        floor = fl.build();
        pFront = panel(true, true);
        pBack = panel(true, false);
        pLeft = panel(false, true);
        pRight = panel(false, false);
        MeshBuilder rf = new MeshBuilder();
        rf.box(-HX, 0, -HZ, HX, PT, HZ, OLIVE);
        for (int sd = -1; sd <= 1; sd += 2) {
            rf.box(-HX, PT, sd * HZ - (sd > 0 ? 0.6f : 0), HX, PT + 0.08f, sd * HZ + (sd < 0 ? 0.6f : 0), HAZARD);
            rf.box(sd * HX - (sd > 0 ? 0.6f : 0), PT, -HZ + 0.6f, sd * HX + (sd < 0 ? 0.6f : 0), PT + 0.08f, HZ - 0.6f, HAZARD);
        }
        for (float[] l : lugs()) {
            rf.push().translate(l[0], PT + 0.25f, l[2]);
            rf.torus(0.32f, 0.1f, 10, 4, 0, 360, STEEL);
            rf.pop();
        }
        roof = rf.build();
        // pile de materiaux (parpaings) et toles
        MeshBuilder pl = new MeshBuilder();
        pl.box(-4.6f, 0, -3.6f, 4.6f, PILE_H, 3.6f, BLOCKS);
        pile = pl.build();
        MeshBuilder pt = new MeshBuilder();
        for (int i = 0; i < 3; i++) {
            pt.box(-5.2f + i * 0.1f, PT + i * 0.3f, 3.9f, 1.4f - i * 0.2f, PT + i * 0.3f + 0.25f, 5.5f, STEEL);
        }
        pt.box(-6.5f, PT, -5.6f, -5.0f, PT + 2.6f, -3.6f, OLIVE);
        pt.box(-6.5f, PT + 2.6f, -5.6f, -5.0f, PT + 2.7f, -3.6f, HAZARD);
        plates = pt.build();
        // porte blindee (repere : centre, face +Z) et son cadre
        MeshBuilder dr = new MeshBuilder();
        dr.push().rotateX(90);
        dr.cylinder(DOOR_R, -0.28f, 0.28f, 28, STEEL, VAULT, VAULT);
        dr.pop();
        dr.push().translate(DOOR_R - 0.1f, 0, 0.3f).rotateX(90);
        dr.cylinder(0.22f, -0.1f, 0.5f, 8, BLACK, null, BLACK);
        dr.pop();
        door = dr.build();
        MeshBuilder df = new MeshBuilder();
        df.box(DOOR_X0 - 0.3f, B_Y0, DOOR_Z - 0.5f, DOOR_X0, B_Y0 + 3 * RH, DOOR_Z + 0.55f, HAZARD);
        df.box(DOOR_X1, B_Y0, DOOR_Z - 0.5f, DOOR_X1 + 0.3f, B_Y0 + 3 * RH, DOOR_Z + 0.55f, HAZARD);
        df.box(DOOR_X0 - 0.3f, B_Y0 + 3 * RH - 0.3f, DOOR_Z - 0.5f, DOOR_X1 + 0.3f, B_Y0 + 3 * RH, DOOR_Z + 0.55f, STEEL);
        for (int i = 0; i < 2; i++) {
            df.push().translate(DOOR_X0 + 0.05f, DOOR_Y + (i == 0 ? -1.1f : 1.1f), DOOR_Z + 0.35f);
            df.cylinder(0.2f, -0.35f, 0.35f, 8, BLACK, BLACK, BLACK);
            df.pop();
        }
        df.box(DOOR_X0, B_Y0, DOOR_Z - 0.5f, DOOR_X1, B_Y0 + 0.02f, DOOR_Z + 0.5f, BLACK);
        doorFrame = df.build();
        // plan deroule (sur la face interieure de la paroi avant) et sa projection
        MeshBuilder sp = new MeshBuilder();
        // UV explicites : une fois la paroi couchee, le haut du plan est cote charniere (lisible depuis l'avant)
        float zs = -PT - 0.03f;
        sp.poly(new float[][]{v(-4.2f, 0, zs), v(4.2f, 0, zs), v(4.2f, 5.2f, zs), v(-4.2f, 5.2f, zs)},
                new float[][]{{PLAN.u(0), PLAN.v(0)}, {PLAN.u(1), PLAN.v(0)}, {PLAN.u(1), PLAN.v(1)}, {PLAN.u(0), PLAN.v(1)}}, null, v(0, 0, -1));
        sheet = sp.build();
        MeshBuilder ph = new MeshBuilder();
        ph.face(PLAN, v(0, 0, 1), v(-4.5f, -3.4f, 0), v(4.5f, -3.4f, 0), v(4.5f, 3.4f, 0), v(-4.5f, 3.4f, 0));
        planHolo = ph.build();

        // ---------------------------------------------------------------- bunker : morceaux de murs, rangee par rangee
        pieces.clear();
        float x0 = CX - HX, x1 = CX + HX, z0 = CZ - HZ, z1 = CZ + HZ;
        for (int r = 0; r < ROWS; r++) {
            float ya = B_Y0 + r * RH, yb = ya + RH;
            pieces.add(new float[]{x0, ya, z0, CX, yb, z0 + WT, r});
            pieces.add(new float[]{CX, ya, z0, x1, yb, z0 + WT, r});
            pieces.add(new float[]{x0, ya, z0 + WT, x0 + WT, yb, z1 - WT, r});
            pieces.add(new float[]{x1 - WT, ya, z0 + WT, x1, yb, z1 - WT, r});
            if (r < 3) {
                pieces.add(new float[]{x0, ya, z1 - WT, DOOR_X0 - 0.3f, yb, z1, r});
                pieces.add(new float[]{DOOR_X1 + 0.3f, ya, z1 - WT, x1, yb, z1, r});
            } else {
                pieces.add(new float[]{x0, ya, z1 - WT, x1, yb, z1, r});
            }
        }
        pieceMesh = new Mesh[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            float[] q = pieces.get(i);
            float hx = (q[3] - q[0]) / 2, hy = (q[4] - q[1]) / 2, hz = (q[5] - q[2]) / 2;
            MeshBuilder pm = new MeshBuilder();
            pm.push().translate(-(q[0] + hx), -(q[1] + hy), -(q[2] + hz));
            pm.box(q[0], q[1], q[2], q[3], q[4], q[5], BLOCKS);
            if (q[6] == 3 && q[5] >= z1 - 0.01f) {
                pm.face(HAZARD, v(0, 0, 1), v(q[0], q[1] + 0.25f, q[5] + 0.02f), v(q[3], q[1] + 0.25f, q[5] + 0.02f), v(q[3], q[4] - 0.25f, q[5] + 0.02f), v(q[0], q[4] - 0.25f, q[5] + 0.02f));
            }
            pm.pop();
            pieceMesh[i] = pm.build();
        }

        // ---------------------------------------------------------------- hologramme : aretes du bunker, grille au sol, plan de balayage
        MeshBuilder hw = new MeshBuilder();
        float e = 0.07f;
        float[][] outer = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        float[][] inner = {{x0 + WT, z0 + WT}, {x1 - WT, z0 + WT}, {x1 - WT, z1 - WT}, {x0 + WT, z1 - WT}};
        for (int i = 0; i < 4; i++) {
            float[] a = outer[i], c = outer[(i + 1) % 4];
            for (int r = 0; r <= ROWS; r++) {
                float y = B_Y0 + r * RH;
                edge(hw, v(a[0], y, a[1]), v(c[0], y, c[1]), r == 0 || r == ROWS ? e * 1.4f : e * 0.7f);
            }
            edge(hw, v(a[0], B_Y0, a[1]), v(a[0], B_TOP, a[1]), e * 1.4f);
            float[] ia = inner[i], ic = inner[(i + 1) % 4];
            edge(hw, v(ia[0], B_TOP, ia[1]), v(ic[0], B_TOP, ic[1]), e);
            edge(hw, v(ia[0], B_TOP, ia[1]), v(ia[0], B_Y0, ia[1]), e * 0.6f);
        }
        edge(hw, v(DOOR_X0, B_Y0, z1 + 0.02f), v(DOOR_X0, B_Y0 + 3 * RH, z1 + 0.02f), e * 1.2f);
        edge(hw, v(DOOR_X1, B_Y0, z1 + 0.02f), v(DOOR_X1, B_Y0 + 3 * RH, z1 + 0.02f), e * 1.2f);
        edge(hw, v(DOOR_X0, B_Y0 + 3 * RH, z1 + 0.02f), v(DOOR_X1, B_Y0 + 3 * RH, z1 + 0.02f), e * 1.2f);
        holoWire = hw.build();
        MeshBuilder hg = new MeshBuilder();
        float gy = B_Y0 + 0.03f;
        for (float x = x0; x <= x1 + 0.01f; x += 1f) {
            hg.face(Mat.WHITE, v(0, 1, 0), v(x - 0.04f, gy, z0), v(x + 0.04f, gy, z0), v(x + 0.04f, gy, z1), v(x - 0.04f, gy, z1));
        }
        for (float z = z0; z <= z1 + 0.01f; z += 1f) {
            hg.face(Mat.WHITE, v(0, 1, 0), v(x0, gy, z - 0.04f), v(x1, gy, z - 0.04f), v(x1, gy, z + 0.04f), v(x0, gy, z + 0.04f));
        }
        holoGrid = hg.build();
        MeshBuilder sc = new MeshBuilder();
        sc.face(Mat.WHITE, v(0, 1, 0), v(x0, 0, z0), v(x1, 0, z0), v(x1, 0, z1), v(x0, 0, z1));
        scanPlane = sc.build();

        // ---------------------------------------------------------------- cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.ROUND_RECT;
        ps.w = 3.4f;
        ps.h = 3.2f;
        ps.d = 1.3f;
        ps.height = 1.6f;
        ps.halfSpan = 0.95f;
        ps.wire = 0.36f;
        ps.body = YELLOW;
        ps.face = LOCKFACE;
        ps.shackle = STEEL;
        ps.trim = BLACK;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(STEEL, YELLOW, 0.5f, (kb, kg) -> {
            // tete de cle : casque de chantier
            kb.push().translate(0, 2.8f, 0);
            kb.sphereSection(1.9f, 16, 6, 0, 90, YELLOW);
            kb.push().translate(0, -0.05f, 0);
            kb.cylinder(2.5f, -0.15f, 0.05f, 16, YELLOW, YELLOW, YELLOW);
            kb.pop();
            kb.box(-0.25f, 0, -1.9f, 0.25f, 2.0f, 1.9f, ORANGE);
            kb.push().translate(0, 0.9f, 1.75f).rotateX(90);
            kb.cylinder(0.5f, 0, 0.35f, 10, STEEL, null, Mat.WHITE);
            kb.pop();
            kb.pop();
            kg.push().translate(0, 3.7f, 1.95f);
            kg.sphere(0.55f, 8, 6, Mat.WHITE);
            kg.pop();
        });
        key = k[0];
        keyGlow = k[1];
    }

    /** Arete d'hologramme (barre fine). */
    private static void edge(MeshBuilder b, float[] a, float[] c, float r) {
        bar(b, a, c, r, Mat.WHITE);
    }

    /** Anneaux de levage du toit (repere du toit). */
    private static float[][] lugs() {
        return new float[][]{v(-HX + 0.8f, 0, -HZ + 0.8f), v(HX - 0.8f, 0, -HZ + 0.8f), v(HX - 0.8f, 0, HZ - 0.8f), v(-HX + 0.8f, 0, HZ - 0.8f)};
    }

    /** Paroi du conteneur, exprimee depuis sa charniere (arete basse exterieure). */
    private static Mesh panel(boolean longSide, boolean positive) {
        MeshBuilder b = new MeshBuilder();
        if (longSide) {
            float za = positive ? -PT : 0, zb = positive ? 0 : PT;
            b.box(-HX, 0, za, HX, CH, zb, OLIVE);
            float zf = positive ? 0.02f : -0.02f;
            float[] n = v(0, 0, positive ? 1 : -1);
            if (positive) {
                b.face(STENCIL, n, v(-HX + 0.25f, 0.25f, zf), v(HX - 0.25f, 0.25f, zf), v(HX - 0.25f, CH - 0.25f, zf), v(-HX + 0.25f, CH - 0.25f, zf));
                for (int sd = -1; sd <= 1; sd += 2) {
                    bar(b, v(sd * 1.6f, 0.4f, 0.2f), v(sd * 1.6f, CH - 0.4f, 0.2f), 0.12f, STEEL);
                    b.box(sd * 1.6f - 0.3f, CH * 0.45f - 0.35f, 0.05f, sd * 1.6f + 0.3f, CH * 0.45f + 0.35f, 0.3f, STEEL);
                }
                b.box(-1.9f, CH * 0.45f - 0.12f, 0.2f, 1.9f, CH * 0.45f + 0.12f, 0.38f, STEEL);
            } else {
                b.face(HAZARD, n, v(-HX, CH - 0.9f, zf), v(HX, CH - 0.9f, zf), v(HX, CH - 0.2f, zf), v(-HX, CH - 0.2f, zf));
            }
        } else {
            float xa = positive ? 0 : -PT, xb = positive ? PT : 0;
            b.box(xa, 0, -HZ + PT, xb, CH, HZ - PT, OLIVE);
            float xf = positive ? -0.02f : 0.02f;
            float[] n = v(positive ? -1 : 1, 0, 0);
            b.face(HAZARD, n, v(xf, CH - 0.9f, -HZ + PT), v(xf, CH - 0.9f, HZ - PT), v(xf, CH - 0.2f, HZ - PT), v(xf, CH - 0.2f, -HZ + PT));
        }
        return b.build();
    }

    // =================================================================== chronologie
    private static final float DESC0 = CrateAnim.UNLOCK + 2, LAND = CrateAnim.OPEN;
    private static final float FOLD0 = LAND + 2, FOLD_LEN = 6;
    private static final float ROOF_UP0 = LAND + 3, ROOF_UP1 = LAND + 13, SLEW0 = ROOF_UP1, SLEW1 = SLEW0 + 10;
    private static final float HOLO0 = LAND + 10;
    private static final float ROOF_BACK0 = CrateAnim.STOP - 34, ROOF_BACK1 = ROOF_BACK0 + 8, ROOF_SET = ROOF_BACK1 + 9;
    /** Les 23 morceaux de mur montent entre BUILD0 et l'arrivee du toit. */
    private static final float BUILD0 = LAND + 16, PIECE_FLY = 7, PIECE_GAP = (ROOF_BACK0 - 5 - PIECE_FLY - BUILD0) / 22f;
    private static final float DOOR0 = ROOF_SET + 1, DOOR1 = DOOR0 + 7;
    private static final float OPEN_DOOR0 = CrateAnim.STOP, OPEN_DOOR1 = CrateAnim.STOP + 8;
    private static final float C_DOOR0 = CrateAnim.CLOSE, C_DOOR1 = C_DOOR0 + 3, C_DOORBACK1 = C_DOOR1 + 5;
    private static final float C_PIECES0 = CrateAnim.CLOSE + 1, C_ROOF_UP0 = CrateAnim.CLOSE + 1, C_ROOF_UP1 = CrateAnim.CLOSE + 6;
    private static final float C_FOLD0 = CrateAnim.CLOSE + 7, C_FOLD1 = CrateAnim.CLOSE + 13, C_ROOF_DOWN0 = CrateAnim.CLOSE + 10, C_ROOF_DOWN1 = CrateAnim.SLAM;
    private static final float C_LIFT0 = CrateAnim.SLAM + 2, C_LIFT1 = CrateAnim.END - 1;

    private float launchT(int i) {
        return BUILD0 + i * PIECE_GAP;
    }

    private float landT(int i) {
        return launchT(i) + PIECE_FLY;
    }

    private float backT(int i) {
        return C_PIECES0 + (pieces.size() - 1 - i) * 0.3f;
    }

    /** Orientation de la fleche (degres autour du mat). */
    private static float phi(CrateState s) {
        float t = s.t;
        float drift = 2.5f * (float) Math.sin(s.time * 0.011f) * idleW(s);
        if (!s.active) return PHI0 + drift;
        float out = Ease.inOutCubic(Ease.range(t, SLEW0, SLEW1)) * (1 - Ease.inOutCubic(Ease.range(t, ROOF_BACK0, ROOF_BACK1)));
        return PHI0 + drift + (PHI_SIDE - PHI0) * out;
    }

    /** Point sous le chariot (x, z) pour une orientation donnee. */
    private static float[] hookXZ(float phiDeg) {
        double a = Math.toRadians(phiDeg);
        return new float[]{MX + (float) Math.cos(a) * RT, MZ - (float) Math.sin(a) * RT};
    }

    /** Bas du conteneur (y). */
    private static float cBottom(CrateState s) {
        if (!s.active) return HANG_B;
        float t = s.t;
        float d = Ease.inOutCubic(Ease.range(t, DESC0, LAND));
        float up = Ease.outBack(Ease.range(t, C_LIFT0, C_LIFT1), 1.2f);
        return HANG_B + (PAD_T - HANG_B) * d * (1 - up);
    }

    /** Bas du toit (y). */
    private static float roofY(CrateState s) {
        if (!s.active) return HANG_B + CH;
        float t = s.t;
        if (t < ROOF_UP0) return cBottom(s) + CH;
        if (t < ROOF_BACK1) return Ease.lerp(PAD_T + CH, ROOF_HIGH, Ease.inOutCubic(Ease.range(t, ROOF_UP0, ROOF_UP1)));
        if (t < CrateAnim.CLOSE) return Ease.lerp(ROOF_HIGH, B_TOP, Ease.inOutCubic(Ease.range(t, ROOF_BACK1, ROOF_SET)));
        if (t < C_ROOF_DOWN0) return Ease.lerp(B_TOP, 14f, Ease.inOutCubic(Ease.range(t, C_ROOF_UP0, C_ROOF_UP1)));
        if (t < C_LIFT0) return Ease.lerp(14f, PAD_T + CH, Ease.inOutCubic(Ease.range(t, C_ROOF_DOWN0, C_ROOF_DOWN1)));
        return cBottom(s) + CH;
    }

    /** Poids des mouvements du repos (derive de la fleche, balancement) : s'efface pendant la sequence. */
    private static float idleW(CrateState s) {
        if (!s.active) return 1;
        return (1 - Ease.smooth(Ease.range(s.t, 0, DESC0))) + Ease.smooth(Ease.range(s.t, C_LIFT0, CrateAnim.END));
    }

    /** Balancement de la charge (degres Z, X). */
    private static float[] sway(CrateState s) {
        float w = idleW(s);
        float z = 1.3f * (float) Math.sin(s.time * 0.045f) * w, x = 0.9f * (float) Math.sin(s.time * 0.033f + 1) * w;
        if (s.active) {
            float t = s.t;
            if (t > SLEW0) z += 6f * (float) Math.sin((t - SLEW0) * 0.3f) * (float) Math.exp(-(t - SLEW0) * 0.07f) * (t < ROOF_BACK0 ? 1 : 0);
            if (t > ROOF_BACK0 && t < ROOF_SET) z -= 5f * (float) Math.sin((t - ROOF_BACK0) * 0.35f) * (float) Math.exp(-(t - ROOF_BACK0) * 0.1f);
            if (t > C_LIFT1 - 4) z += 3f * (float) Math.sin((t - C_LIFT1) * 0.4f) * (float) Math.exp(-Math.max(0, t - C_LIFT1) * 0.1f);
        }
        return new float[]{z, x};
    }

    /** Pliage d'une paroi (0 debout, 1 a plat), order : 0 avant, 1 cotes, 2 arriere. */
    private static float fold(CrateState s, int order) {
        if (!s.active) return 0;
        float t = s.t;
        float down = Ease.outBounce(Ease.range(t, FOLD0 + order, FOLD0 + order + FOLD_LEN));
        float up = Ease.inOutCubic(Ease.range(t, C_FOLD0 + (2 - order) * 0.8f, C_FOLD1 + (2 - order) * 0.8f - 1.6f));
        return down * (1 - up);
    }

    /** Hologramme (0..1). */
    private static float holo(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.smooth(Ease.range(t, HOLO0, HOLO0 + 8)) * (1 - Ease.smooth(Ease.range(t, ROOF_SET, DOOR1 + 4)));
    }

    /** Nombre de morceaux encore dans la pile (fraction). */
    private float pileLeft(CrateState s) {
        if (!s.active) return 1;
        int n = pieces.size(), gone = 0;
        for (int i = 0; i < n; i++) {
            if (s.t > launchT(i) + 1 && s.t < backT(i) + 5) gone++;
        }
        return 1 - gone / (float) n;
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        float v = 0;
        if (t >= LAND) v = Math.max(v, 0.55f * (float) Math.exp(-(t - LAND) * 0.35f));
        if (t >= ROOF_SET) v = Math.max(v, 0.25f * (float) Math.exp(-(t - ROOF_SET) * 0.4f));
        if (t >= DOOR1) v = Math.max(v, 0.2f * (float) Math.exp(-(t - DOOR1) * 0.4f));
        if (t >= OPEN_DOOR0) v = Math.max(v, 0.3f * (float) Math.exp(-(t - OPEN_DOOR0) * 0.2f));
        for (int i = 0; i < pieces.size(); i++) {
            float l = t - landT(i);
            if (l >= 0 && l < 3) v = Math.max(v, 0.08f * (1 - l / 3));
        }
        if (t >= C_ROOF_DOWN1) v = Math.max(v, 0.3f * (float) Math.exp(-(t - C_ROOF_DOWN1) * 0.4f));
        return Math.max(v, 0.2f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        out.solid(p, site, gl);
        out.solid(p, mast, gl);
        // grue : partie tournante, chariot, cable, moufle
        float ph = phi(s);
        p.push().translate(MX, MAST_TOP, MZ).rotY(ph);
        out.solid(p, slew, gl);
        float blink = (float) Math.pow(Math.max(0, Math.sin(s.time * (0.12f + 0.2f * aw))), 6);
        out.additive(p, beacon, 0xFF8A1A, 0.3f + 0.7f * blink);
        p.push().translate(RT, 0.55f, 0);
        out.solid(p, trolley, gl);
        p.pop();
        p.pop();
        float[] hx = hookXZ(ph);
        float ry = roofY(s);
        float hookY = ry + PT + SLING;
        float[] sw = sway(s);
        // cables de levage (deux brins) du chariot au moufle
        MeshBuilder cb = new MeshBuilder();
        for (int sd = -1; sd <= 1; sd += 2) {
            bar(cb, v(hx[0] + sd * 0.25f, MAST_TOP + 0.2f, hx[1]), v(hx[0] + sd * 0.25f, hookY, hx[1]), 0.06f, STEEL);
        }
        out.solid(p, cb.buildDynamic(), gl);
        // charge suspendue : balancement autour du moufle
        p.push().translate(hx[0], hookY, hx[1]).rotZ(sw[0]).rotX(sw[1]);
        out.solid(p, hook, gl);
        // elingues du crochet aux anneaux du toit
        MeshBuilder sl = new MeshBuilder();
        for (float[] l : lugs()) {
            float[] top = v(0, -2.3f, 0);
            float[] at = v(l[0], -SLING + 0.3f, l[2]);
            bar(sl, top, at, 0.05f, STEEL);
        }
        out.solid(p, sl.buildDynamic(), gl);
        p.translate(0, -hookY, 0);
        // toit (toujours sous le crochet)
        p.push().translate(0, ry, 0);
        out.solid(p, roof, gl);
        p.pop();
        // conteneur : suspendu avec le toit, ou pose sur la dalle
        boolean hanging = !s.active || s.t < LAND || s.t > C_LIFT0;
        if (hanging) {
            p.push().translate(0, cBottom(s), 0);
            container(s, p, out, gl);
            p.pop();
        }
        p.pop();
        if (!hanging) {
            p.push().translate(CX, cBottom(s), CZ);
            container(s, p, out, gl);
            p.pop();
            bunker(s, p, out, gl);
        }
    }

    /** Conteneur (repere : bas au centre) : plancher, parois (pliees selon l'ouverture), pile de materiaux. */
    private void container(CrateState s, Pose p, DrawList out, float gl) {
        out.solid(p, floor, gl);
        float left = pileLeft(s);
        if (left > 0.01f) {
            p.push().translate(0, PT, 0).scale(1, Math.max(0.06f, left), 1);
            out.solid(p, pile, gl);
            p.pop();
        }
        out.solid(p, plates, gl);
        // porte blindee rangee sur la pile tant qu'elle n'est pas partie
        if (!s.active || s.t < DOOR0 || s.t > C_DOORBACK1) {
            p.push().translate(0, PT + PILE_H * Math.max(0.06f, left) + 0.3f, 0.5f).rotX(-90);
            out.solid(p, door, gl);
            p.pop();
        }
        float f0 = fold(s, 0) * 90, f1 = fold(s, 1) * 90, f2 = fold(s, 2) * 90;
        p.push().translate(0, 0, HZ).rotX(f0);
        out.solid(p, pFront, gl);
        // plan qui se deroule sur la face interieure
        float un = s.active ? Ease.smooth(Ease.range(s.t, FOLD0 + FOLD_LEN, FOLD0 + FOLD_LEN + 5)) * (1 - Ease.smooth(Ease.range(s.t, C_FOLD0 - 3, C_FOLD0))) : 0;
        if (un > 0.01f) {
            p.push().translate(0, 0.6f, 0).scale(1, un, 1);
            out.solid(p, sheet, 1.0f);
            out.additive(p, sheet, HOLO, 0.4f * holo(s));
            p.pop();
        }
        p.pop();
        p.push().translate(0, 0, -HZ).rotX(-f2);
        out.solid(p, pBack, gl);
        p.pop();
        p.push().translate(-HX, 0, 0).rotZ(f1);
        out.solid(p, pLeft, gl);
        p.pop();
        p.push().translate(HX, 0, 0).rotZ(-f1);
        out.solid(p, pRight, gl);
        p.pop();
    }

    /** Pose d'un morceau de mur (centre) ; renvoie son echelle (0 = cache). */
    private float piecePose(CrateState s, int i, Pose p) {
        float[] q = pieces.get(i);
        float[] c = v((q[0] + q[3]) / 2, (q[1] + q[4]) / 2, (q[2] + q[5]) / 2);
        float t = s.t;
        float l0 = launchT(i), l1 = landT(i), b0 = backT(i);
        if (t < l0 || t > b0 + 5) return 0;
        float left = pileLeft(s);
        float[] src = v(CX + (Ease.hash(i * 3) - 0.5f) * 5, B_Y0 + PILE_H * Math.max(0.1f, left), CZ + (Ease.hash(i * 3 + 1) - 0.5f) * 4);
        float sc;
        if (t < l1) {
            float u = Ease.range(t, l0, l1);
            float k = Ease.inOutCubic(u);
            float[] pos = Geo.lerp(src, c, k);
            pos[1] += (4.5f + q[6] * 1.2f) * (float) Math.sin(u * Math.PI);
            p.translate(pos[0], pos[1], pos[2]).rotY(200f * (1 - k) * (i % 2 == 0 ? 1 : -1)).rotX(35f * (float) Math.sin(u * Math.PI));
            sc = 0.45f + 0.55f * Ease.smooth(u);
        } else if (t < b0) {
            float land = t - l1;
            float sq = land < 5 ? 0.12f * (float) Math.exp(-land * 0.6f) * (float) Math.cos(land * 1.6f) : 0;
            p.translate(c[0], c[1] - sq * RH, c[2]).scale(1 + sq * 0.3f, 1 - sq, 1 + sq * 0.3f);
            sc = 1;
        } else {
            float u = Ease.range(t, b0, b0 + 5);
            float k = Ease.inCubic(u);
            float[] pos = Geo.lerp(c, src, k);
            pos[1] += 3f * (float) Math.sin(u * Math.PI);
            p.translate(pos[0], pos[1], pos[2]).rotY(-160f * k);
            sc = 1 - 0.6f * k;
        }
        return sc;
    }

    /** Bunker en construction : murs, porte blindee, hologramme. */
    private void bunker(CrateState s, Pose p, DrawList out, float gl) {
        for (int i = 0; i < pieces.size(); i++) {
            p.push();
            float sc = piecePose(s, i, p);
            if (sc > 0.01f) {
                p.scale(sc);
                out.solid(p, pieceMesh[i], gl);
            }
            p.pop();
        }
        float t = s.t;
        // porte : quitte la pile, roule dans son cadre, s'ouvre a l'arret de la roue
        if (t >= DOOR0 && t <= C_DOORBACK1) {
            float[] slot = v(CX, DOOR_Y, DOOR_Z);
            float[] src = v(CX, B_Y0 + PILE_H * 0.06f + 0.3f, CZ + 0.5f);
            boolean frame = t >= DOOR1 - 1 && t < C_DOOR1 + 1;
            if (frame) out.solid(p, doorFrame, gl);
            p.push();
            if (t < DOOR1) {
                float u = Ease.range(t, DOOR0, DOOR1);
                float k = Ease.inOutCubic(u);
                float[] pos = Geo.lerp(src, slot, k);
                pos[1] += 5f * (float) Math.sin(u * Math.PI);
                p.translate(pos[0], pos[1], pos[2]).rotX(-90f * (1 - k)).rotZ(-360f * k);
            } else if (t < C_DOOR1) {
                float o = Ease.outBack(Ease.range(t, OPEN_DOOR0 + 2, OPEN_DOOR1), 1.1f) * (1 - Ease.inOutCubic(Ease.range(t, C_DOOR0, C_DOOR1)));
                float spin = -180f * Ease.smooth(Ease.range(t, OPEN_DOOR0, OPEN_DOOR0 + 3));
                p.translate(CX - DOOR_R + 0.15f, DOOR_Y, DOOR_Z).rotY(-105f * o).translate(DOOR_R - 0.15f, 0, 0).rotZ(spin);
            } else {
                float u = Ease.range(t, C_DOOR1, C_DOORBACK1);
                float k = Ease.inCubic(u);
                float[] pos = Geo.lerp(slot, src, k);
                pos[1] += 3f * (float) Math.sin(u * Math.PI);
                p.translate(pos[0], pos[1], pos[2]).rotX(-90f * k).scale(1 - 0.3f * k);
            }
            out.solid(p, door, gl);
            p.pop();
        }
        // hologramme du bunker
        float h = holo(s);
        if (h > 0.01f) {
            float fl = 0.85f + 0.15f * (float) Math.sin(s.time * 1.7f) * (float) Math.sin(s.time * 0.43f);
            out.additive(p, holoWire, HOLO, 0.75f * h * fl);
            out.additive(p, holoGrid, HOLO, 0.28f * h);
            float sy = B_Y0 + (B_TOP - B_Y0 + 0.4f) * ((s.time * 0.025f) % 1f);
            p.push().translate(0, sy, 0);
            out.additive(p, scanPlane, HOLO, 0.1f * h);
            p.pop();
            // projection du plan au-dessus de la paroi avant couchee
            p.push().translate(CX, B_Y0 + 5.5f + 0.3f * (float) Math.sin(s.time * 0.05f), CZ + HZ + CH * 0.6f).rotX(-25f).scale(0.8f + 0.2f * h);
            out.additive(p, planHolo, HOLO, 0.55f * h * fl);
            p.pop();
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        // au loquet de la paroi avant du conteneur suspendu
        float[] hx = hookXZ(phi(s));
        float ry = roofY(s);
        float hookY = ry + PT + SLING;
        float[] sw = sway(s);
        p.translate(hx[0], hookY, hx[1]).rotZ(sw[0]).rotX(sw[1]).translate(0, -hookY + cBottom(s), 0);
        p.translate(0, CH * 0.45f - 0.1f, HZ + 0.42f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        // gyrophare : halo tournant
        float ph = phi(s);
        Pose q = new Pose();
        q.translate(MX, MAST_TOP, MZ).rotY(ph);
        float[] bw = q.apply(0, 5.95f, 0);
        float blink = (float) Math.pow(Math.max(0, Math.sin(s.time * (0.12f + 0.2f * aw))), 6);
        out.billboard(p, Sprite.HALO, bw[0], bw[1], bw[2], 2.5f + 3f * blink, 0, 0xFF8A1A, 0.3f + 0.6f * blink);
        out.billboard(p, Sprite.FLARE, bw[0], bw[1], bw[2], 3.5f * blink, s.time, 0xFFB050, 0.8f * blink);
        out.ground(p, Sprite.HALO, 8, PAD_T + 0.05f, 8, 16f, 0, 0xFFB020, 0.08f + 0.12f * aw);
        if (!s.active) return;
        float t = s.t;
        // choc au sol a la pose du conteneur
        if (t >= LAND && t < LAND + 14) {
            float k = (t - LAND) / 14f;
            out.ground(p, Sprite.SHOCK, CX, PAD_T + 0.1f, CZ, 8f + 16f * k, 0, 0xD8C8A0, 0.7f * (1 - k));
        }
        // chocs des morceaux de mur
        for (int i = 0; i < pieces.size(); i++) {
            float l = t - landT(i);
            if (l >= 0 && l < 6) {
                float[] c = pieces.get(i);
                out.billboard(p, Sprite.HALO, (c[0] + c[3]) / 2, (c[1] + c[4]) / 2, (c[2] + c[5]) / 2 + 0.8f, 2.5f, 0, HOLO, 0.5f * (1 - l / 6));
            }
        }
        // lumiere du bunker quand la porte s'ouvre
        float o = Ease.smooth(Ease.range(t, OPEN_DOOR0 + 1, OPEN_DOOR1)) * (1 - Ease.smooth(Ease.range(t, C_DOOR0, C_DOOR1)));
        if (o > 0.01f) {
            float e = t >= OPEN_DOOR0 ? (float) Math.exp(-(t - OPEN_DOOR0 - 3) * 0.15f) : 0;
            out.billboard(p, Sprite.FLARE, CX, DOOR_Y, DOOR_Z + 0.8f, 5f + 12f * Math.min(1, e), s.time, 0xFFE0A0, 0.7f * o);
            out.rays(p, CX, DOOR_Y, DOOR_Z + 1f, 10, 16f * o, 1.6f, s.time * 0.8f, 0xFFD070, 0.45f * o);
            out.ground(p, Sprite.HALO, CX, PAD_T + 0.08f, DOOR_Z + 5f, 7f, 0, 0xFFD070, 0.5f * o);
            out.beam(p, CX, B_TOP + 0.6f, CZ, 3f, 24f, 0xFFD070, 0.18f * o);
        }
        CommonFx.cavity(s, p, out, this, CX, B_Y0 + 1, CZ, 6f);
    }

    @Override
    public float[] cavityPos() {
        return new float[]{CX, B_Y0 + 2, CZ};
    }

    // =================================================================== particules et sons
    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        if (r.nextFloat() < 0.05f) {
            fx.particle(FxSink.SMOKE, r.nextFloat() * 24 - 4, PAD_T + 0.3f, r.nextFloat() * 24 - 4, rnd(r, 0.05f), 0.03f, rnd(r, 0.05f), 0xB8B0A0, 1.4f, 40);
        }
    }

    private static void dust(FxSink fx, Random r, float x, float y, float z, float spread, int n) {
        for (int i = 0; i < n; i++) {
            double th = r.nextDouble() * Math.PI * 2;
            float sp = 0.2f + r.nextFloat() * 0.5f;
            fx.particle(FxSink.SMOKE, x + rnd(r, spread), y, z + rnd(r, spread), (float) Math.cos(th) * sp, 0.05f + r.nextFloat() * 0.15f, (float) Math.sin(th) * sp,
                    r.nextBoolean() ? 0xC8BCA4 : 0x9C9484, 2.2f + r.nextFloat() * 1.5f, 30 + r.nextInt(20));
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        if (k == (int) DESC0) fx.sound(Snd.CRANE, CX, 20, CZ, 0.9f, 0.9f);
        if (k == (int) LAND) {
            fx.sound(Snd.CLANG, CX, PAD_T + 1, CZ, 1.2f, 0.9f);
            dust(fx, r, CX, PAD_T + 0.5f, CZ, 7f, 40);
        }
        for (int o = 0; o < 3; o++) {
            if (k == (int) (FOLD0 + o + FOLD_LEN * 0.36f)) {
                fx.sound(Snd.CLANG, CX, PAD_T + 0.5f, CZ, 0.7f, 1.25f + 0.1f * o);
                dust(fx, r, CX, PAD_T + 0.3f, CZ, 12f, 14);
            }
        }
        if (k == (int) ROOF_UP0) fx.sound(Snd.CRANE, CX, 18, CZ, 0.8f, 1.1f);
        if (k == (int) HOLO0) fx.sound(Snd.HOLOGRAM, CX, B_Y0 + 3, CZ + HZ, 1.0f, 1.0f);
        if (k == (int) SLEW0) fx.sound(Snd.CRANE, CX, 20, CZ, 0.7f, 0.8f);
        for (int i = 0; i < pieces.size(); i++) {
            float[] c = pieces.get(i);
            float x = (c[0] + c[3]) / 2, y = (c[1] + c[4]) / 2, z = (c[2] + c[5]) / 2;
            if (k == (int) launchT(i) && i % 2 == 0) fx.sound(Snd.WHOOSH, x, y + 3, z, 0.35f, 1.5f);
            if (k == (int) landT(i)) {
                fx.sound(Snd.BLOCK, x, y, z, 0.8f, 0.9f + 0.04f * (i % 5));
                dust(fx, r, x, c[1] + 0.2f, z, 1.5f, 5);
                for (int j = 0; j < 4; j++) {
                    fx.particle(FxSink.SPARK, x + rnd(r, 1.5f), c[4], z + rnd(r, 1f), 0, 0.2f, 0, HOLO, 1.1f, 10);
                }
            }
            if (k == (int) backT(i) && i % 4 == 0) fx.sound(Snd.BLOCK, x, y, z, 0.5f, 1.2f);
        }
        if (k == (int) ROOF_BACK0) fx.sound(Snd.CRANE, CX, 20, CZ, 0.7f, 0.85f);
        if (k == (int) ROOF_SET) {
            fx.sound(Snd.CLANG, CX, B_TOP, CZ, 1.0f, 1.1f);
            dust(fx, r, CX, B_TOP + 0.4f, CZ, 7f, 20);
        }
        if (k == (int) DOOR0) fx.sound(Snd.WHOOSH, CX, DOOR_Y, DOOR_Z, 0.6f, 0.9f);
        if (k == (int) DOOR1) fx.sound(Snd.CLANG, CX, DOOR_Y, DOOR_Z, 0.9f, 1.45f);
        if (k == (int) OPEN_DOOR0) {
            fx.sound(Snd.VAULT, CX, DOOR_Y, DOOR_Z, 1.1f, 1.0f);
            burst(fx, r, CX, DOOR_Y, DOOR_Z + 1, 0xFFD070, 0xFFFFFF, 30, 1.4f, 1.6f);
        }
        if (k == (int) C_DOOR0) fx.sound(Snd.VAULT, CX, DOOR_Y, DOOR_Z, 0.7f, 1.3f);
        if (k == (int) C_ROOF_UP0) fx.sound(Snd.CRANE, CX, 16, CZ, 0.7f, 1.1f);
        if (k == (int) C_FOLD1) fx.sound(Snd.CLANG, CX, PAD_T + 3, CZ, 0.7f, 1.3f);
        if (k == (int) C_ROOF_DOWN1) fx.sound(Snd.CLANG, CX, PAD_T + CH, CZ, 0.9f, 1.0f);
        if (k == (int) C_LIFT0) fx.sound(Snd.CRANE, CX, 18, CZ, 0.8f, 1.0f);
    }
}
