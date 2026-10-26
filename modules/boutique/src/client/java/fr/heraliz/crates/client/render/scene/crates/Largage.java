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
 * Caisse Largage : la zone de largage. Une aire de terre battue marquee d'un grand X de panneaux orange fluo, un muret
 * de sacs de sable, un fumigene rouge qui fume en continu, des batons lumineux aux coins et une balise radio durcie
 * (antenne repliee, voyant qui clignote) : le cadenas ferme la balise.
 * <p>A l'ouverture : la balise s'ouvre, l'antenne se deploie et appelle le largage ; un avion cargo passe haut dans le
 * ciel et largue une caisse ; chute libre, le parachute claque, la caisse descend en se balancant et touche le sol dans
 * un nuage de poussiere pendant que la voile s'affaisse derriere. Les boulons explosifs du couvercle sautent un par un,
 * le couvercle est souffle, tournoie et retombe appuye contre le flanc ; une lumiere verte et de la fumee sortent du
 * colis et la roue en sort (on ne voit jamais son contenu). A la fermeture, le couvercle revient se poser et un ballon
 * Fulton arrache le colis vers le ciel.
 */
public final class Largage extends Design {
    private static final Mat DIRT = Mat.MAIN;
    private static final Mat BURLAP = Mat.MAIN2;
    private static final Mat PLY = Mat.TRIM;
    private static final Mat NYLON = Mat.ACCENT;
    private static final Mat STENCIL = Mat.EMBLEM;
    private static final Mat LOCKFACE = Mat.LOCK;
    private static final Mat SCREEN = Mat.DECAL_A;
    private static final Mat SIGNAL = Mat.DECAL_B;
    private static final Mat HONEY = Mat.INTERIOR;
    private static final Mat STEEL = Mat.GEM;
    private static final Mat BLACK = Mat.DARK;
    private static final Mat ORANGE = Mat.SPECIAL;

    private static final int SMOKE_RED = 0xE83A2A, CHEM = 0x7AFF5A;

    /** Aire ; caisse (centre, demi-tailles, hauteur, palette, epaisseur) ; balise ; fumigene. */
    private static final float PAD_T = 0.6f;
    private static final float CX = 8, CZ = 7.5f, HX = 5.5f, HZ = 4.5f, CH = 8.5f, PH = 1.2f, PT = 0.45f;
    private static final float BEACON_X0 = 5.6f, BEACON_X1 = 10.4f, BEACON_Z0 = 14.8f, BEACON_Z1 = 18.0f, BEACON_H = 2.6f;
    private static final float[] FLARE = {-2.6f, PAD_T, 14.0f};
    /** Manche a air (pied du mat : x, sommet, z) et projecteur (tete). */
    private static final float[] WIND = {19.5f, 19f, -2.0f}, LAMP = {-3.2f, PAD_T + 5.2f, 0.5f};
    private static final float[] PLANE_PATH = {-90f, 100f};
    private static final float PLANE_Y = 64f, PLANE_Z = -6f;

    private Mesh site = Mesh.EMPTY, beaconBody = Mesh.EMPTY, beaconLid = Mesh.EMPTY, antenna = Mesh.EMPTY, beaconLed = Mesh.EMPTY, chem = Mesh.EMPTY;
    private Mesh pallet = Mesh.EMPTY, pFront = Mesh.EMPTY, pBack = Mesh.EMPTY, pLeft = Mesh.EMPTY, pRight = Mesh.EMPTY, pTop = Mesh.EMPTY;
    private Mesh canopy = Mesh.EMPTY, pack = Mesh.EMPTY, plane = Mesh.EMPTY, props = Mesh.EMPTY;
    private Mesh balloon = Mesh.EMPTY, flareTip = Mesh.EMPTY, sock = Mesh.EMPTY, lampCone = Mesh.EMPTY;
    private final float[][] chemAt = new float[4][];

    public Largage() {
        super("largage", 0xFF5A1A, 0x9FD85A);
        keyRgb = 0xFF7A2A;
        cavityRgb = 0xA8FF7A;
        cavityAccent = 0xFFB050;
        size = 1.45f;
        ringY = 42;
        ringR = 24;
        revealY = 48;
        riseFromY = PAD_T + PH + 4;
        lockFloorY = PAD_T;
    }

    // =================================================================== construction
    private static void bar(MeshBuilder b, float[] a, float[] c, float r, Mat m) {
        b.tube(new float[][]{a, c}, new float[]{r, r}, 6, m, true, true, 0, false);
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // aire de terre battue
        b.bevelBox(-5.5f, 0, -5.5f, 21.5f, PAD_T, 21.5f, 0.2f, DIRT);
        // grand X de panneaux de signalisation
        for (int i = 0; i < 2; i++) {
            b.push().translate(CX, PAD_T + 0.03f, CZ).rotateY(45 + i * 90);
            b.face(SIGNAL, v(0, 1, 0), v(-8.5f, 0.01f * i, -1.6f), v(8.5f, 0.01f * i, -1.6f), v(8.5f, 0.01f * i, 1.6f), v(-8.5f, 0.01f * i, 1.6f));
            b.pop();
        }
        // muret de sacs de sable (arc arriere, deux rangees)
        Random r = new Random(5);
        for (int row = 0; row < 2; row++) {
            int n = row == 0 ? 10 : 9;
            for (int i = 0; i < n; i++) {
                float a = 200 + (140f / (n - 1)) * i + (row == 1 ? 140f / (n - 1) * 0.5f : 0);
                if (row == 1 && i == n - 1) continue;
                float[] q = Geo.polar(CX, CZ, a, 12.2f, PAD_T + 0.55f + row * 1.0f);
                b.push().translate(q[0], q[1], q[2]).rotateY(-a + 90 + (r.nextFloat() - 0.5f) * 10).rotateZ((r.nextFloat() - 0.5f) * 5);
                b.bevelBox(-1.45f, -0.5f, -0.8f, 1.45f, 0.5f, 0.8f, 0.42f, BURLAP);
                b.pop();
            }
        }
        // caisses de munitions
        for (int i = 0; i < 2; i++) {
            float y = PAD_T + i * 1.6f;
            b.push().translate(18.2f + i * 0.4f, 0, 15.5f - i * 0.3f).rotateY(12 - i * 20);
            b.bevelBox(-2.0f, y, -1.0f, 2.0f, y + 1.6f, 1.0f, 0.12f, BLACK);
            b.box(-2.05f, y + 1.1f, -1.05f, 2.05f, y + 1.3f, 1.05f, ORANGE);
            bar(b, v(-0.8f, y + 1.7f, 0), v(0.8f, y + 1.7f, 0), 0.12f, STEEL);
            b.pop();
        }
        // fumigene plante dans le sol
        b.push().translate(FLARE[0], FLARE[1], FLARE[2]).rotateZ(-18).rotateX(10);
        b.cylinder(0.32f, -0.3f, 1.7f, 10, ORANGE, null, BLACK);
        b.pop();
        // balise : corps durci, poignees, loquets, clavier
        b.bevelBox(BEACON_X0, PAD_T, BEACON_Z0, BEACON_X1, PAD_T + BEACON_H, BEACON_Z1, 0.3f, BLACK);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.box(8 + sd * 1.6f - 0.35f, PAD_T + BEACON_H - 0.9f, BEACON_Z1, 8 + sd * 1.6f + 0.35f, PAD_T + BEACON_H + 0.3f, BEACON_Z1 + 0.3f, STEEL);
        }
        b.box(8 - 0.5f, PAD_T + BEACON_H - 0.75f, BEACON_Z1, 8 + 0.5f, PAD_T + BEACON_H - 0.1f, BEACON_Z1 + 0.35f, STEEL);
        b.push().translate(8, PAD_T + BEACON_H - 0.45f, BEACON_Z1 + 0.4f).rotateX(90);
        b.torus(0.3f, 0.09f, 10, 4, 0, 360, STEEL);
        b.pop();
        b.box(BEACON_X0 + 0.5f, PAD_T + BEACON_H - 0.02f, BEACON_Z0 + 0.6f, BEACON_X1 - 0.5f, PAD_T + BEACON_H + 0.05f, BEACON_Z1 - 0.6f, STEEL);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                float kx = BEACON_X0 + 1.1f + j * 0.85f, kz = BEACON_Z0 + 1.0f + i * 0.7f;
                b.box(kx - 0.25f, PAD_T + BEACON_H + 0.05f, kz - 0.22f, kx + 0.25f, PAD_T + BEACON_H + 0.22f, kz + 0.22f, j == 3 && i == 2 ? ORANGE : BLACK);
            }
        }
        b.push().translate(BEACON_X1 - 0.8f, PAD_T + BEACON_H, BEACON_Z0 + 0.7f);
        b.cylinder(0.35f, 0, 0.6f, 8, STEEL, null, STEEL);
        b.pop();
        // mat de la manche a air
        bar(b, v(WIND[0], PAD_T, WIND[2]), v(WIND[0], WIND[1], WIND[2]), 0.2f, STEEL);
        b.push().translate(WIND[0], PAD_T, WIND[2]);
        b.cylinder(0.7f, 0, 0.4f, 8, BLACK, null, BLACK);
        b.pop();
        b.push().translate(WIND[0], WIND[1] + 0.2f, WIND[2]);
        b.sphere(0.3f, 8, 6, ORANGE);
        b.pop();
        // projecteur sur trepied, braque sur le X
        for (int i = 0; i < 3; i++) {
            float[] q = Geo.polar(LAMP[0], LAMP[2], i * 120 + 30, 1.6f, PAD_T);
            bar(b, q, v(LAMP[0], LAMP[1] - 0.4f, LAMP[2]), 0.1f, BLACK);
        }
        {
            float[] d = Geo.sub(v(CX, PAD_T, CZ), LAMP);
            float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
            float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
            b.push().translate(LAMP[0], LAMP[1], LAMP[2]).rotateY(yaw).rotateX(-pitch);
            b.bevelBox(-1.0f, -0.8f, -0.9f, 1.0f, 0.8f, 0.6f, 0.15f, BLACK);
            b.face(Mat.WHITE, v(0, 0, 1), v(-0.8f, -0.6f, 0.62f), v(0.8f, -0.6f, 0.62f), v(0.8f, 0.6f, 0.62f), v(-0.8f, 0.6f, 0.62f));
            b.pop();
        }
        // batons lumineux aux quatre coins du X
        for (int i = 0; i < 4; i++) {
            float[] q = Geo.polar(CX, CZ, 45 + i * 90, 11f, PAD_T + 0.12f);
            chemAt[i] = q;
        }
        Occlusion.bake(b, 0.5f, 5f, 0.5f, 0f);
        site = b.build();
        MeshBuilder cm = new MeshBuilder();
        for (float[] q : chemAt) {
            cm.push().translate(q[0], q[1], q[2]).rotateY(q[0] * 20).rotateZ(90);
            cm.cylinder(0.14f, -0.7f, 0.7f, 6, Mat.WHITE, Mat.WHITE, Mat.WHITE);
            cm.pop();
        }
        chem = cm.build();
        MeshBuilder ft = new MeshBuilder();
        ft.push().translate(FLARE[0], FLARE[1], FLARE[2]).rotateZ(-18).rotateX(10);
        ft.push().translate(0, 1.75f, 0);
        ft.sphere(0.3f, 8, 6, Mat.WHITE);
        ft.pop();
        ft.pop();
        flareTip = ft.build();

        // couvercle de la balise (repere : charniere arriere en haut), ecran sur la face interieure
        MeshBuilder bl = new MeshBuilder();
        float bw = (BEACON_X1 - BEACON_X0) / 2, bd = BEACON_Z1 - BEACON_Z0;
        bl.bevelBox(-bw, 0, 0, bw, 0.8f, bd, 0.25f, BLACK);
        bl.face(SCREEN, v(0, -1, 0), v(-bw + 0.5f, -0.02f, 0.4f), v(bw - 0.5f, -0.02f, 0.4f), v(bw - 0.5f, -0.02f, bd - 0.4f), v(-bw + 0.5f, -0.02f, bd - 0.4f));
        bl.box(-bw + 0.6f, 0.8f, 0.8f, bw - 0.6f, 0.95f, bd - 0.8f, BLACK);
        beaconLid = bl.build();
        MeshBuilder an = new MeshBuilder();
        an.cylinder(0.16f, 0, 1f, 6, STEEL, null, STEEL);
        antenna = an.build();
        MeshBuilder ld = new MeshBuilder();
        ld.sphere(0.28f, 8, 6, Mat.WHITE);
        beaconLed = ld.build();

        // caisse de largage (repere : bas de la palette, au centre)
        MeshBuilder pl = new MeshBuilder();
        pl.box(-HX - 0.3f, 0, -HZ - 0.3f, HX + 0.3f, PH * 0.6f, HZ + 0.3f, HONEY);
        pl.box(-HX - 0.1f, PH * 0.6f, -HZ - 0.1f, HX + 0.1f, PH, HZ + 0.1f, PLY);
        for (int sd = -1; sd <= 1; sd += 2) pl.box(-HX - 0.35f, 0.1f, sd * HZ - 0.4f, HX + 0.35f, 0.6f, sd * HZ + 0.4f, STEEL);
        pl.box(-HX + PT, PH, -HZ + PT, HX - PT, PH + 2.2f, HZ - PT, HONEY);
        // ratelier interieur (repere de la caisse)
        pallet = pl.build();
        pFront = panel(0);
        pBack = panel(1);
        pLeft = panel(2);
        pRight = panel(3);
        MeshBuilder tp = new MeshBuilder();
        tp.box(-HX, 0, -HZ, HX, PT, HZ, PLY);
        for (int sd = -1; sd <= 1; sd += 2) tp.box(sd * 2.2f - 0.5f, PT, -HZ - 0.05f, sd * 2.2f + 0.5f, PT + 0.06f, HZ + 0.05f, ORANGE);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -HX + 0.5f : HX - 0.5f, z = i / 2 == 0 ? -HZ + 0.5f : HZ - 0.5f;
            tp.push().translate(x, PT, z);
            tp.cylinder(0.35f, 0, 0.3f, 8, STEEL, null, ORANGE);
            tp.pop();
        }
        tp.push().translate(0, PT, 0);
        tp.cylinder(0.5f, 0, 0.6f, 10, STEEL, null, STEEL);
        tp.torus(0.5f, 0.12f, 10, 4, 0, 360, STEEL);
        tp.pop();
        pTop = tp.build();
        // parachute : voile en fuseaux (repere : sommet de la voile au-dessus du point d'attache)
        MeshBuilder cp = new MeshBuilder();
        cp.lathe(new float[][]{{10.4f, 0}, {10.0f, 0.8f}, {8.6f, 2.4f}, {6.0f, 3.7f}, {3.0f, 4.4f}, {0.01f, 4.6f}}, seg(16), 0, 360, NYLON, 1, 1, 0.12f, 3f, 7, false);
        cp.lathe(new float[][]{{0.01f, 4.5f}, {3.0f, 4.3f}, {6.0f, 3.6f}, {8.6f, 2.3f}, {10.0f, 0.7f}, {10.3f, 0}}, seg(16), 0, 360, NYLON, 1, 1, 0.12f, 3f, 7, true);
        canopy = cp.build();
        MeshBuilder pk = new MeshBuilder();
        pk.bevelBox(-2.2f, 0, -1.6f, 2.2f, 1.4f, 1.6f, 0.4f, NYLON);
        pack = pk.build();

        // avion cargo (repere : centre, nez vers +X)
        MeshBuilder ap = new MeshBuilder();
        ap.push().rotateZ(-90);
        ap.lathe(new float[][]{{0.01f, -16}, {1.4f, -14.5f}, {2.4f, -10}, {2.7f, -4}, {2.7f, 8}, {2.2f, 12}, {1.3f, 14.6f}, {0.01f, 15.6f}}, 12, 0, 360, BLACK, 1, 1, 0, 0, 0, false);
        ap.pop();
        ap.box(-2.5f, 1.9f, -17.5f, 3.2f, 2.4f, 17.5f, BLACK);
        for (float z : new float[]{-11f, -5.8f, 5.8f, 11f}) {
            ap.push().translate(0.5f, 1.5f, z).rotateZ(-90);
            ap.cylinder(0.8f, -2.5f, 3.6f, 10, BLACK, BLACK, STEEL);
            ap.pop();
        }
        ap.box(-16f, 2.0f, -0.25f, -11.5f, 9.0f, 0.25f, BLACK);
        ap.box(-16.5f, 2.6f, -6.5f, -12.5f, 2.95f, 6.5f, BLACK);
        ap.box(-14.8f, -2.9f, -1.8f, -10.5f, -2.5f, 1.8f, BLACK);
        plane = ap.build();
        MeshBuilder pr = new MeshBuilder();
        for (float z : new float[]{-11f, -5.8f, 5.8f, 11f}) {
            pr.push().translate(4.2f, 1.5f, z).rotateZ(-90);
            pr.cylinder(2.4f, -0.02f, 0.02f, 16, Mat.WHITE, Mat.WHITE, Mat.WHITE);
            pr.pop();
        }
        props = pr.build();
        // manche a air : cone tronque en cinq bandes orange / blanc, pointant vers +X
        MeshBuilder sk = new MeshBuilder();
        sk.push().rotateZ(-90);
        for (int i = 0; i < 5; i++) {
            float y0 = i * 1.3f, y1 = y0 + 1.3f, r0 = 1.05f - i * 0.12f, r1 = r0 - 0.12f;
            sk.lathe(new float[][]{{r0, y0}, {r1, y1}}, 12, 0, 360, i % 2 == 0 ? ORANGE : STEEL, 1, 1, 0, 0, 0, false);
            sk.lathe(new float[][]{{r0 - 0.02f, y0}, {r1 - 0.02f, y1}}, 12, 0, 360, i % 2 == 0 ? ORANGE : STEEL, 1, 1, 0, 0, 0, true);
        }
        sk.torus(1.05f, 0.08f, 12, 4, 0, 360, STEEL);
        sk.pop();
        sock = sk.build();
        MeshBuilder lc = new MeshBuilder();
        Geo.cone(lc, v(0, 0, 0), v(0, 0, -1), 1f, 1f, 16, Mat.WHITE);
        lampCone = lc.build();
        MeshBuilder ba = new MeshBuilder();
        ba.sphere(2.6f, 16, 10, STEEL);
        balloon = ba.build();

        // cadenas et cle
        Padlock.Style ps = new Padlock.Style();
        ps.shape = Padlock.Shape.ROUND_RECT;
        ps.w = 2.7f;
        ps.h = 2.9f;
        ps.d = 1.1f;
        ps.height = 1.3f;
        ps.halfSpan = 0.75f;
        ps.wire = 0.3f;
        ps.body = BLACK;
        ps.face = LOCKFACE;
        ps.shackle = STEEL;
        ps.trim = ORANGE;
        ps.hole = BLACK;
        lock = new Padlock(ps);
        lock.build(detail);
        Mesh[] k = KeySmith.build(STEEL, ORANGE, 0.5f, (kb, kg) -> {
            // tete de cle : mini parachute
            kb.push().translate(0, 3.2f, 0);
            kb.sphereSection(2.2f, 14, 6, 0, 80, NYLON);
            kb.pop();
            for (int i = 0; i < 4; i++) {
                float a = i * 90 + 45;
                float[] q = Geo.polar(0, 0, a, 2.1f, 3.6f);
                bar(kb, q, v(0, 1.8f, 0), 0.06f, STEEL);
            }
            kb.box(-0.6f, 1.2f, -0.6f, 0.6f, 2.2f, 0.6f, ORANGE);
            kg.box(-0.7f, 1.1f, -0.7f, 0.7f, 2.3f, 0.7f, Mat.WHITE);
        });
        key = k[0];
        keyGlow = k[1];
    }

    /** Paroi de la caisse (0 avant, 1 arriere, 2 gauche, 3 droite), exprimee depuis sa charniere (arete basse exterieure). */
    private static Mesh panel(int side) {
        MeshBuilder b = new MeshBuilder();
        float h = CH;
        if (side <= 1) {
            boolean front = side == 0;
            float za = front ? -PT : 0, zb = front ? 0 : PT;
            b.box(-HX, 0, za, HX, h, zb, PLY);
            float zf = front ? 0.02f : -0.02f;
            float[] n = v(0, 0, front ? 1 : -1);
            b.face(STENCIL, n, v(-HX + 0.3f, 0.3f, zf), v(HX - 0.3f, 0.3f, zf), v(HX - 0.3f, h - 0.3f, zf), v(-HX + 0.3f, h - 0.3f, zf));
            // cornieres d'acier
            for (int sd = -1; sd <= 1; sd += 2) b.box(sd * HX - (sd > 0 ? 0.5f : 0), 0, za - 0.08f, sd * HX + (sd < 0 ? 0.5f : 0), h, zb + 0.08f, STEEL);
            b.box(-HX, h - 0.5f, za - 0.08f, HX, h, zb + 0.08f, STEEL);
            b.box(-HX, 0, za - 0.08f, HX, 0.5f, zb + 0.08f, STEEL);
            // sangle orange
            for (int sd = -1; sd <= 1; sd += 2) b.box(sd * 2.2f - 0.5f, 0.5f, za - 0.12f, sd * 2.2f + 0.5f, h - 0.5f, zb + 0.12f, ORANGE);
        } else {
            boolean left = side == 2;
            float xa = left ? 0 : -PT, xb = left ? PT : 0;
            b.box(xa, 0, -HZ + PT, xb, h, HZ - PT, PLY);
            for (int sd = -1; sd <= 1; sd += 2) b.box(xa - 0.08f, 0, sd * (HZ - PT) - (sd > 0 ? 0.5f : 0), xb + 0.08f, h, sd * (HZ - PT) + (sd < 0 ? 0.5f : 0), STEEL);
            b.box(xa - 0.08f, h - 0.5f, -HZ + PT, xb + 0.08f, h, HZ - PT, STEEL);
            b.box(xa - 0.1f, 0.5f, -1.2f, xb + 0.1f, h - 0.5f, -0.4f, BLACK);
            b.box(xa - 0.1f, 0.5f, 0.4f, xb + 0.1f, h - 0.5f, 1.2f, BLACK);
        }
        return b.build();
    }

    // =================================================================== chronologie
    private static final float LID0 = CrateAnim.UNLOCK + 1, LID1 = LID0 + 6, ANT0 = LID0 + 2, ANT1 = ANT0 + 6;
    private static final float PLANE0 = CrateAnim.UNLOCK + 2, PLANE1 = PLANE0 + 22;
    private static final float DROP = PLANE0 + 9, CHUTE = DROP + 3, LAND = CrateAnim.RISE + 2;
    private static final float BOLT0 = LAND + 3, BLOW = LAND + 7, LID_LAND = BLOW + 13;
    private static final float C_PANELS0 = CrateAnim.CLOSE, C_PANELS1 = CrateAnim.CLOSE + 9;
    private static final float BALLOON0 = CrateAnim.CLOSE + 6, BALLOON1 = BALLOON0 + 5, YANK0 = BALLOON1 + 1, YANK1 = CrateAnim.END - 2;
    private static final float DROP_Y = 60f;

    /** Position du bas de la caisse pendant la chute (monde) et balancement (degres Z, X). */
    private static float[] cratePos(CrateState s) {
        float t = s.t;
        if (t < LAND) {
            float tau = Math.max(0, t - DROP);
            float x = CX - 21f * (float) Math.exp(-tau * 0.35f), z = CZ + (PLANE_Z - CZ) * (float) Math.exp(-tau * 0.35f);
            float y;
            if (t < CHUTE) {
                y = DROP_Y - 1.2f * tau * tau;
            } else {
                float y0 = DROP_Y - 1.2f * (CHUTE - DROP) * (CHUTE - DROP);
                float u = Ease.range(t, CHUTE, LAND);
                y = Ease.lerp(y0, PAD_T, u * (1.15f - 0.15f * u));
            }
            float sw = t > CHUTE ? (float) Math.exp(-(t - CHUTE) * 0.1f) * (1 - Ease.smooth(Ease.range(t, LAND - 4, LAND))) : 0;
            return new float[]{x, y, z, 9f * (float) Math.sin((t - CHUTE) * 0.45f) * sw, 5f * (float) Math.sin((t - CHUTE) * 0.33f + 1) * sw};
        }
        float up = Ease.inCubic(Ease.range(t, YANK0, YANK1));
        return new float[]{CX, PAD_T + 70f * up, CZ, 0, 0};
    }

    private static boolean crateVisible(CrateState s) {
        return s.active && s.t >= DROP && s.t < YANK1;
    }

    /** Ecrasement a l'atterrissage (0..1). */
    private static float squash(CrateState s) {
        float d = s.t - LAND;
        return d >= 0 && d < 8 ? (float) Math.exp(-d * 0.6f) * (float) Math.cos(d * 1.4f) : 0;
    }

    /** Couvercle : 0 en place, 1 appuye contre le flanc droit. */
    private static float lidOff(CrateState s) {
        if (!s.active || s.t < BLOW) return 0;
        if (s.t < C_PANELS0) return Ease.range(s.t, BLOW, LID_LAND);
        return 1 - Ease.inOutCubic(Ease.range(s.t, C_PANELS0, C_PANELS1));
    }

    /** Lumiere qui sort du colis ouvert (0..1). */
    private static float cargoGlow(CrateState s) {
        if (!s.active) return 0;
        return Ease.smooth(Ease.range(s.t, BLOW, BLOW + 6)) * (1 - Ease.smooth(Ease.range(s.t, C_PANELS0, C_PANELS1)));
    }

    // =================================================================== image
    @Override
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float t = s.t, v = 0;
        if (t >= LAND) v = Math.max(v, 0.55f * (float) Math.exp(-(t - LAND) * 0.35f));
        if (t >= BLOW) v = Math.max(v, 0.6f * (float) Math.exp(-(t - BLOW) * 0.3f));
        if (t >= CHUTE) v = Math.max(v, 0.12f * (float) Math.exp(-(t - CHUTE) * 0.5f));
        if (t > PLANE0 && t < PLANE1) v = Math.max(v, 0.08f * Ease.bell(t, PLANE0, PLANE1));
        return Math.max(v, 0.25f * Seq.stopFlash(s));
    }

    @Override
    protected void model(CrateState s, Pose p, DrawList out) {
        float gl = glowLevel(s);
        float aw = Seq.awaken(s);
        out.solid(p, site, gl);
        out.additive(p, chem, CHEM, 0.9f);
        float fl = 0.7f + 0.3f * (float) Math.sin(s.time * 1.9f) * (float) Math.sin(s.time * 0.7f);
        out.additive(p, flareTip, 0xFF4A2A, fl);
        // manche a air qui flotte (plus agitee quand l'avion passe)
        float gust = s.active ? Ease.bell(s.t, PLANE0 + 4, PLANE1 + 6) : 0;
        p.push().translate(WIND[0], WIND[1] - 0.3f, WIND[2]).rotY(200f + 18f * (float) Math.sin(s.time * 0.05f) + 30f * gust * (float) Math.sin(s.t * 0.8f))
                .rotZ(-14f + 6f * (float) Math.sin(s.time * 0.13f) + 10f * gust);
        out.solid(p, sock, gl);
        p.pop();
        // balise : couvercle, antenne, voyant
        float lid = s.active ? Ease.outBack(Ease.range(s.t, LID0, LID1), 1.4f) * (1 - Ease.inOutCubic(Ease.range(s.t, YANK1 - 6, YANK1))) : 0;
        p.push().translate(8, PAD_T + BEACON_H, BEACON_Z0).rotX(-108f * lid);
        out.solid(p, beaconLid, 0.4f + 0.8f * lid);
        p.pop();
        float ant = s.active ? Ease.outBack(Ease.range(s.t, ANT0, ANT1), 1.3f) * (1 - Ease.inOutCubic(Ease.range(s.t, YANK1 - 10, YANK1 - 3))) : 0;
        p.push().translate(BEACON_X1 - 0.8f, PAD_T + BEACON_H + 0.5f, BEACON_Z0 + 0.7f).scale(1, 2.5f + 11f * ant, 1);
        out.solid(p, antenna, gl);
        p.pop();
        float blink = (s.time * (s.active ? 0.5f : 0.08f)) % 1f < 0.3f ? 1 : 0.15f;
        p.push().translate(BEACON_X1 - 0.8f, PAD_T + BEACON_H + 0.9f + (2.5f + 11f * ant), BEACON_Z0 + 0.7f);
        out.additive(p, beaconLed, 0xFF2A1A, blink);
        p.pop();
        // avion cargo
        if (s.active && s.t > PLANE0 && s.t < PLANE1) {
            float u = Ease.range(s.t, PLANE0, PLANE1);
            float x = Ease.lerp(PLANE_PATH[0], PLANE_PATH[1], u);
            p.push().translate(x, PLANE_Y + 2f * (float) Math.sin(u * Math.PI), PLANE_Z).rotX(3f * (float) Math.sin(u * 5));
            out.solid(p, plane, 0.6f);
            out.additive(p, props, 0xB8B8B8, 0.12f);
            p.pop();
        }
        // caisse
        if (crateVisible(s)) {
            float[] c = cratePos(s);
            float sq = squash(s);
            float top = PH + CH;
            p.push().translate(c[0], c[1], c[2]);
            // balancement sous la voile autour du point d'attache (3 px au-dessus du toit)
            p.translate(0, top + 3, 0).rotZ(c[3]).rotX(c[4]).translate(0, -top - 3, 0);
            p.scale(1 + 0.08f * sq, 1 - 0.12f * sq, 1 + 0.08f * sq);
            out.solid(p, pallet, gl);
            // parois (fixes) et couvercle : souffle, un tour en l'air, retombe appuye contre le flanc droit
            p.push().translate(0, PH, HZ);
            out.solid(p, pFront, gl);
            p.pop();
            p.push().translate(0, PH, -HZ);
            out.solid(p, pBack, gl);
            p.pop();
            p.push().translate(-HX, PH, 0);
            out.solid(p, pLeft, gl);
            p.pop();
            p.push().translate(HX, PH, 0);
            out.solid(p, pRight, gl);
            p.pop();
            float lo = lidOff(s);
            p.push();
            if (lo > 0.001f) {
                boolean closing = s.t >= C_PANELS0;
                float e = closing ? lo : Ease.inQuad(lo);
                float[] from = v(0, top - PT, 0), to = v(HX + 1.0f, 5.6f, 0);
                float[] q = Geo.lerp(from, to, e);
                q[1] += 11f * (float) Math.sin(Math.PI * Math.min(1, lo)) * (closing ? 0.6f : 1f);
                float rz = -80f * e - (closing ? 0 : 360f * Ease.outCubic(lo));
                float land = closing ? 0 : s.t - LID_LAND;
                float wob = land > 0 ? 6f * (float) Math.sin(land * 1.3f) * (float) Math.exp(-land * 0.35f) : 0;
                p.translate(q[0], q[1], q[2]).rotZ(rz + wob).rotX(18f * (float) Math.sin(Math.PI * Math.min(1, lo)));
            } else {
                p.translate(0, top - PT, 0);
            }
            out.solid(p, pTop, gl);
            p.pop();
            // voile : pliee en chute libre, ouverte sous la voile, affaissee apres l'atterrissage
            drawCanopy(s, p, out, gl, top);
            // ballon Fulton
            float bal = Ease.outBack(Ease.range(s.t, BALLOON0, BALLOON1), 1.4f);
            if (bal > 0.01f) {
                float by = top + 10f * bal;
                p.push().translate(0, by, 0).scale(bal, bal * 1.15f, bal);
                out.solid(p, balloon, gl);
                p.pop();
                MeshBuilder ln = new MeshBuilder();
                bar(ln, v(0, top + 0.6f, 0), v(0, by - 2.6f * bal, 0), 0.06f, BLACK);
                out.solid(p, ln.buildDynamic(), gl);
            }
            p.pop();
        }
    }

    /** Voile du parachute (repere de la caisse). */
    private void drawCanopy(CrateState s, Pose p, DrawList out, float gl, float top) {
        float t = s.t;
        float fade = 1 - Ease.smooth(Ease.range(t, C_PANELS0, C_PANELS0 + 8));
        if (fade <= 0.01f) return;
        if (t < CHUTE) {
            p.push().translate(0, top, 0);
            out.solid(p, pack, gl);
            p.pop();
            return;
        }
        float open = Ease.outBack(Ease.range(t, CHUTE, CHUTE + 4), 1.6f);
        float col = Ease.inOutCubic(Ease.range(t, LAND, LAND + 12));
        float h = Ease.lerp(top + 15f, 0.6f, col);
        float z = -10f * col;
        float breathe = 1 + 0.04f * (float) Math.sin(t * 0.9f) * (1 - col);
        p.push().translate(0, h, z).rotX(-80f * col).scale(open * breathe * fade, Math.max(0.15f, open * (1 - 0.8f * col)) * fade, open * breathe * fade);
        out.solid(p, canopy, gl);
        p.pop();
        // suspentes (tant que la voile est tendue)
        if (col < 0.6f && open > 0.05f) {
            MeshBuilder ln = new MeshBuilder();
            float[] riser = v(0, top + 3, 0);
            for (int i = 0; i < 8; i++) {
                float[] q = Geo.polar(0, 0, i * 45 + 22.5f, 10.2f * open, h);
                q[2] += z;
                bar(ln, riser, q, 0.05f, BLACK);
                bar(ln, riser, v(i % 2 == 0 ? -HX + 0.5f : HX - 0.5f, top + 0.4f, i / 4 == 0 ? -HZ + 0.5f : HZ - 0.5f), 0.05f, BLACK);
            }
            out.solid(p, ln.buildDynamic(), gl);
        }
    }

    @Override
    protected void lockPose(CrateState s, Pose p) {
        p.translate(8, PAD_T + BEACON_H - 0.45f, BEACON_Z1 + 0.42f);
    }

    @Override
    protected void effects(CrateState s, Pose p, DrawList out) {
        if (s.item) return;
        float aw = Seq.awaken(s);
        // lueurs : fumigene, batons lumineux, voyant de la balise
        float fl = 0.7f + 0.3f * (float) Math.sin(s.time * 1.9f);
        out.billboard(p, Sprite.FLARE, FLARE[0] + 0.55f, FLARE[1] + 1.7f, FLARE[2] + 0.3f, 2.2f * fl, s.time * 3, 0xFF5A2A, 0.9f);
        out.ground(p, Sprite.HALO, FLARE[0] + 0.5f, PAD_T + 0.05f, FLARE[2], 6f, 0, SMOKE_RED, 0.3f * fl);
        for (float[] q : chemAt) out.ground(p, Sprite.HALO, q[0], PAD_T + 0.05f, q[2], 3.2f, 0, CHEM, 0.35f);
        out.ground(p, Sprite.HALO, 8, PAD_T + 0.05f, 8, 17f, 0, 0xFF7A2A, 0.06f + 0.12f * aw);
        {
            float[] d = Geo.sub(v(CX, PAD_T, CZ), LAMP);
            float len = Geo.len(d);
            float yaw = (float) Math.toDegrees(Math.atan2(d[0], d[2]));
            float pitch = (float) Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
            float fk = 0.85f + 0.15f * (float) Math.sin(s.time * 3.1f) * (float) Math.sin(s.time * 0.9f);
            p.push().translate(CX, PAD_T, CZ).rotY(yaw).rotX(-pitch).scale(4.5f, 4.5f, len);
            out.additive(p, lampCone, 0xFFF2D0, (0.05f + 0.04f * aw) * fk);
            p.pop();
            out.ground(p, Sprite.HALO, CX, PAD_T + 0.06f, CZ, 7f, 0, 0xFFF2D0, 0.2f * fk);
            float[] head = v(LAMP[0] + d[0] / len * 0.8f, LAMP[1] + d[1] / len * 0.8f, LAMP[2] + d[2] / len * 0.8f);
            out.billboard(p, Sprite.FLARE, head[0], head[1], head[2], 2.4f, s.time, 0xFFF2D0, 0.8f * fk);
        }
        if (!s.active) return;
        float t = s.t;
        // avion : feux de navigation
        if (t > PLANE0 && t < PLANE1) {
            float u = Ease.range(t, PLANE0, PLANE1);
            float x = Ease.lerp(PLANE_PATH[0], PLANE_PATH[1], u), y = PLANE_Y + 2f * (float) Math.sin(u * Math.PI);
            out.billboard(p, Sprite.FLARE, x + 0.5f, y + 2.2f, PLANE_Z - 17.5f, 1.6f, 0, 0xFF2A2A, 1f);
            out.billboard(p, Sprite.FLARE, x + 0.5f, y + 2.2f, PLANE_Z + 17.5f, 1.6f, 0, 0x2AFF4A, 1f);
            float strobe = (t * 0.7f) % 1f < 0.15f ? 1 : 0;
            out.billboard(p, Sprite.FLARE, x - 16f, y + 9.2f, PLANE_Z, 2.6f * strobe, 0, 0xFFFFFF, strobe);
            out.billboard(p, Sprite.HALO, x - 12.5f, y - 2.2f, PLANE_Z, 3f, 0, 0xFF3A2A, 0.6f);
        }
        if (crateVisible(s)) {
            float[] c = cratePos(s);
            float top = PH + CH;
            // flash du boitier de largage sur le toit pendant la descente
            if (t < LAND) {
                float st = (t * 0.6f) % 1f < 0.2f ? 1 : 0.2f;
                out.billboard(p, Sprite.FLARE, c[0], c[1] + top + 0.8f, c[2], 1.6f * st, 0, 0xFF6A2A, st);
                // trainee d'air
                float[][] pts = new float[6][];
                for (int i = 0; i < pts.length; i++) {
                    CrateState q = s.copy();
                    q.t = Math.max(DROP, t - i * 0.8f);
                    float[] cc = cratePos(q);
                    pts[i] = v(cc[0], cc[1] + top / 2, cc[2]);
                }
                if (t < CHUTE + 3) out.additive(p, Trail.ribbon(pts, 2.4f, 0.2f), 0xFFFFFF, 0.25f);
            }
            // atterrissage : onde de choc et poussiere
            if (t >= LAND && t < LAND + 16) {
                float k = (t - LAND) / 16f;
                out.ground(p, Sprite.SHOCK, CX, PAD_T + 0.1f, CZ, 7f + 18f * k, 0, 0xD8C8A0, 0.7f * (1 - k));
            }
            // boulons explosifs : eclairs aux coins, un par un
            for (int i = 0; i < 4; i++) {
                float bt = BOLT0 + i * 1.0f;
                float d = t - bt;
                if (d >= 0 && d < 3) {
                    float bx = CX + (i % 2 == 0 ? -HX : HX), bz = CZ + ((i / 2) % 2 == 0 ? -HZ : HZ), by = c[1] + PH + CH - 0.4f;
                    out.billboard(p, Sprite.FLARE, bx, by, bz, 2.2f * (1 - d / 3), s.time, 0xFFB050, 1 - d / 3);
                }
            }
            if (t >= BLOW && t < BLOW + 10) {
                float e = (float) Math.exp(-(t - BLOW) * 0.4f);
                out.billboard(p, Sprite.FLARE, CX, c[1] + PH + CH, CZ, 8f + 12f * e, s.time, 0xFFD8A0, e);
                out.ground(p, Sprite.SHOCK, CX, PAD_T + 0.12f, CZ, 4f + 26f * (1 - e), 0, 0xFFB050, 0.8f * e);
            }
            // lumiere de la cargaison (le contenu reste cache)
            float cg = cargoGlow(s);
            if (cg > 0.01f) {
                float mouth = c[1] + PH + CH;
                out.billboard(p, Sprite.HALO, CX, mouth + 1.5f, CZ, 10f, 0, CHEM, 0.35f * cg);
                out.ground(p, Sprite.HALO, CX, mouth + 0.3f, CZ, 6f, 0, CHEM, 0.6f * cg);
                out.beam(p, CX, mouth, CZ, 3.5f, 20f, CHEM, 0.2f * cg);
                CommonFx.cavity(s, p, out, this, CX, mouth - 1, CZ, 6f, CHEM);
            }
            // arrachement Fulton : trainee
            if (t > YANK0) {
                float k = Ease.range(t, YANK0, YANK1);
                out.additive(p, Trail.ribbon(new float[][]{v(c[0], c[1] + top + 10, c[2]), v(c[0], Math.max(PAD_T, c[1] - 12 * k), c[2])}, 1.4f, 0.2f), 0xFFFFFF, 0.35f * (1 - k));
            }
        }
    }

    @Override
    public float[] cavityPos() {
        return new float[]{CX, PAD_T + PH + 3, CZ};
    }

    // =================================================================== particules et sons
    private static void smoke(CrateState s, FxSink fx, Random r, float amount) {
        int n = (int) amount + (r.nextFloat() < amount - (int) amount ? 1 : 0);
        for (int i = 0; i < n; i++) {
            fx.particle(FxSink.SMOKE, FLARE[0] + 0.55f + rnd(r, 0.3f), FLARE[1] + 1.8f, FLARE[2] + 0.3f + rnd(r, 0.3f), 0.05f + rnd(r, 0.08f),
                    0.12f + r.nextFloat() * 0.1f, -0.04f + rnd(r, 0.08f), r.nextInt(3) == 0 ? 0xC82A1A : SMOKE_RED, 2.4f + r.nextFloat() * 2.2f, 50 + r.nextInt(30));
        }
    }

    @Override
    public void idleFx(CrateState s, FxSink fx, Random r) {
        smoke(s, fx, r, 0.6f);
        if (r.nextFloat() < 0.05f) {
            fx.particle(FxSink.SMOKE, r.nextFloat() * 24 - 4, PAD_T + 0.3f, r.nextFloat() * 24 - 4, rnd(r, 0.05f), 0.03f, rnd(r, 0.05f), 0xB8A888, 1.4f, 40);
        }
    }

    private static void dust(FxSink fx, Random r, float x, float y, float z, float spread, int n) {
        for (int i = 0; i < n; i++) {
            double th = r.nextDouble() * Math.PI * 2;
            float sp = 0.3f + r.nextFloat() * 0.6f;
            fx.particle(FxSink.SMOKE, x + rnd(r, spread), y, z + rnd(r, spread), (float) Math.cos(th) * sp, 0.05f + r.nextFloat() * 0.2f, (float) Math.sin(th) * sp,
                    r.nextBoolean() ? 0xC8B490 : 0x9C8C6C, 2.4f + r.nextFloat() * 1.8f, 30 + r.nextInt(25));
        }
    }

    @Override
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
        smoke(s, fx, r, k > LID0 && k < CrateAnim.CLOSE ? 1.6f : 0.6f);
        if (k == (int) LID0) fx.sound(Snd.RADIO, 8, PAD_T + BEACON_H, BEACON_Z0 + 1, 1.0f, 1.0f);
        if (k == (int) PLANE0) fx.sound(Snd.PLANE, 8, PLANE_Y - 20, PLANE_Z, 1.6f, 1.0f);
        if (k == (int) DROP) fx.sound(Snd.WHOOSH, CX, DROP_Y - 20, CZ, 0.8f, 0.7f);
        if (k == (int) CHUTE) fx.sound(Snd.PARACHUTE, CX, 40, CZ, 1.2f, 1.0f);
        if (k == (int) LAND) {
            fx.sound(Snd.CLANG, CX, PAD_T + 2, CZ, 1.2f, 0.8f);
            dust(fx, r, CX, PAD_T + 0.5f, CZ, 7f, 45);
        }
        if (k == (int) BOLT0) fx.sound(Snd.BOLTS, CX, PAD_T + 5, CZ, 1.2f, 1.0f);
        if (k == (int) BLOW) {
            float top = PAD_T + PH + CH;
            for (int i = 0; i < 40; i++) {
                double th = r.nextDouble() * Math.PI * 2;
                float sp = 0.3f + r.nextFloat() * 0.7f;
                fx.particle(i % 3 == 0 ? FxSink.FLAME : FxSink.SPARK, CX + rnd(r, HX), top, CZ + rnd(r, HZ), (float) Math.cos(th) * sp,
                        0.6f + r.nextFloat() * 1.0f, (float) Math.sin(th) * sp, i % 2 == 0 ? 0xFFB050 : 0xFFE0A0, 1.4f, 14 + r.nextInt(10));
            }
            for (int i = 0; i < 16; i++) {
                fx.particle(FxSink.SMOKE, CX + rnd(r, HX * 0.8f), top, CZ + rnd(r, HZ * 0.8f), rnd(r, 0.15f), 0.25f + r.nextFloat() * 0.2f, rnd(r, 0.15f),
                        0x9CB88C, 2.6f + r.nextFloat() * 1.8f, 40 + r.nextInt(20));
            }
        }
        if (k == (int) LID_LAND) {
            fx.sound(Snd.CLANG, CX + HX + 2, PAD_T + 2, CZ, 0.8f, 1.2f);
            dust(fx, r, CX + HX + 2, PAD_T + 0.4f, CZ, 3f, 12);
        }
        if (k > BLOW + 2 && k < CrateAnim.CLOSE && k % 3 == 0) {
            fx.particle(FxSink.SMOKE, CX + rnd(r, HX * 0.7f), PAD_T + PH + CH, CZ + rnd(r, HZ * 0.7f), rnd(r, 0.05f), 0.12f, rnd(r, 0.05f),
                    0x9CB88C, 1.8f + r.nextFloat(), 40);
        }
        if (k == (int) C_PANELS0) fx.sound(Snd.CLANG, CX, PAD_T + 4, CZ, 0.7f, 1.3f);
        if (k == (int) BALLOON0) fx.sound(Snd.FULTON, CX, PAD_T + 14, CZ, 1.0f, 1.0f);
        if (k == (int) YANK0) dust(fx, r, CX, PAD_T + 0.5f, CZ, 5f, 20);
    }
}
