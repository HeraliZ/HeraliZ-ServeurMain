package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Guichet de change du bunker : comptoir de palettes bardé de tôle ondulée kaki (pochoir "GUICHET"), plateau de
 * contreplaqué, sacs de sable aux deux bouts. Au-dessus, une cloison de tubes d'échafaudage et de grillage, coiffée de
 * barbelés ; au milieu, une vitre de plexiglas rayée à hygiaphone et, dessous, le passe-monnaie : un bac de tôle qui
 * coulisse vers le joueur à chaque échange. Derrière : coffre-caisse vert, balance pour peser le loot, caisses de
 * munitions, baladeuse. Enseigne "CHANGE" peinte à la bombe, éclairée par deux ampoules grillagées.
 */
public final class CashierDesign extends MachineDesign<CashierView> {
    public static final int[] R_TOLE = {0, 0, 256, 256};
    public static final int[] R_PLY = {256, 0, 256, 256};
    public static final int[] R_SIGN = {512, 0, 512, 128};
    public static final int[] R_STENCIL = {512, 128, 512, 128};
    public static final int[] R_PLEXI = {0, 256, 256, 256};
    public static final int[] R_MESH = {256, 256, 128, 128};
    public static final int[] R_RUST = {384, 256, 128, 128};
    public static final int[] R_STEEL = {512, 256, 128, 128};
    public static final int[] R_SAND = {640, 256, 128, 128};
    public static final int[] R_HAZARD = {768, 256, 128, 128};
    public static final int[] R_CASHBOX = {896, 256, 128, 128};
    public static final int[] R_CRATE = {256, 384, 128, 128};
    public static final int[] R_SLATE = {384, 384, 256, 128};
    public static final int[] R_TAPE = {640, 384, 256, 64};
    public static final int[] R_DARK = {896, 384, 64, 64};
    public static final int[] R_BULB = {960, 384, 64, 64};
    public static final int[] R_CABLE = {896, 448, 64, 64};
    public static final int[] R_BRASS = {960, 448, 64, 64};
    public static final int[] R_CHIP_EDGE = {640, 448, 64, 64};
    public static final int[] R_CHIP_WHITE = {704, 448, 64, 64};
    public static final int[] R_CHIP_TOP = {768, 448, 64, 64};

    private static final Mat TOLE = FruitsDesign.tri(R_TOLE, 14), PLY = FruitsDesign.tri(R_PLY, 16), SIGN = FruitsDesign.fit(R_SIGN);
    private static final Mat STENCIL = FruitsDesign.fit(R_STENCIL), PLEXI = FruitsDesign.fit(R_PLEXI), MESH = FruitsDesign.tri(R_MESH, 5);
    private static final Mat RUST = FruitsDesign.tri(R_RUST, 10), STEEL = FruitsDesign.tri(R_STEEL, 10), SAND = FruitsDesign.tri(R_SAND, 8);
    private static final Mat HAZARD = FruitsDesign.tri(R_HAZARD, 6), CASHBOX = FruitsDesign.tri(R_CASHBOX, 8), CRATE = FruitsDesign.tri(R_CRATE, 12);
    private static final Mat SLATE = FruitsDesign.fit(R_SLATE), TAPE = FruitsDesign.fit(R_TAPE), DARK = FruitsDesign.tri(R_DARK, 6);
    private static final Mat BULB = FruitsDesign.fit(R_BULB), CABLE = FruitsDesign.tri(R_CABLE, 6), BRASS = FruitsDesign.tri(R_BRASS, 6);

    /** Plateau du comptoir, facade, plan de la cloison. */
    static final float TOP = 14f, FRONT = 14.85f, PZ = 9.6f;
    /** Vitre (entre deux tubes) et passe-monnaie dessous. */
    static final float WX0 = -1.5f, WX1 = 17.5f, WY0 = 17.8f, WY1 = 29.4f, SLOT0 = 4.2f, SLOT1 = 11.8f, SLOT_Y = 16.8f;
    /** Coffre-caisse (charniere a l'arriere) et lampes. */
    static final float[] BOX = {11.6f, TOP, 4.4f};
    static final float[][] SIGN_LAMPS = {{1.8f, 37.4f, PZ + 1.9f}, {14.2f, 37.4f, PZ + 1.9f}};
    static final float[] ROOM_LAMP = {6.0f, 26.6f, 4.6f};
    static final int GOLD_RGB = 0xFFD36A, GREEN = 0x3DF5A0, CHALK = 0xE8E4D8;

    private Mesh body = Mesh.EMPTY, signFace = Mesh.EMPTY, glass = Mesh.EMPTY, tray = Mesh.EMPTY, lid = Mesh.EMPTY, bulbs = Mesh.EMPTY, roomBulb = Mesh.EMPTY;
    private ChipStacks chips;

    public CashierDesign() {
        super("caisse", GOLD_RGB, GREEN);
        labelY = 44;
        itemScale = 0.25f;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        float x0 = -14.8f, x1 = 30.8f;
        // ---- carcasse : poteaux de palette, dos et flancs en contreplaque
        for (float x : new float[]{x0, x1 - 1.4f}) {
            for (float z : new float[]{1.2f, FRONT - 1.7f}) b.bevelBox(x, 0, z, x + 1.4f, TOP - 1.0f, z + 1.4f, 0.12f, PLY);
        }
        b.bevelBox(x0 + 0.3f, 0.4f, 1.3f, x1 - 0.3f, TOP - 1.0f, 1.8f, 0.08f, PLY);
        b.bevelBox(x0 - 0.1f, 0.4f, 1.6f, x0 + 0.4f, TOP - 1.0f, FRONT - 0.4f, 0.08f, PLY);
        b.bevelBox(x1 - 0.4f, 0.4f, 1.6f, x1 + 0.1f, TOP - 1.0f, FRONT - 0.4f, 0.08f, PLY);
        // ---- facade : trois toles ondulees mal alignees, vissees
        float[][] sheets = {{x0 - 0.2f, -0.5f, 0.8f}, {0.6f, 0.25f, -0.6f}, {16.0f, -0.2f, 1.1f}};
        for (float[] sh : sheets) {
            float sx0 = sh[0], sx1 = sh[0] + 15.6f, yo = sh[1];
            b.push().translate((sx0 + sx1) / 2, 6.8f, 0).rotateZ(sh[2]).translate(-(sx0 + sx1) / 2, -6.8f, 0);
            b.bevelBox(sx0, 0.5f + yo, FRONT - 0.32f, sx1, TOP - 0.9f + yo * 0.4f, FRONT, 0.05f, TOLE);
            for (float sxx : new float[]{sx0 + 0.9f, sx1 - 0.9f}) {
                for (float syy : new float[]{1.5f + yo, TOP - 2.0f + yo * 0.4f}) {
                    b.push().translate(sxx, syy, FRONT).rotateX(90);
                    b.rivet(0.24f, 0.14f, RUST);
                    b.pop();
                }
            }
            b.pop();
        }
        // pochoir "GUICHET" peint a la bombe par-dessus les toles, ruban de danger en travers d'un coin
        // (la fleche du pochoir, a 66 % de sa largeur, tombe sous le passe-monnaie)
        b.push().translate(2.6f, 7.2f, FRONT + 0.06f);
        b.decalZ(STENCIL, 0, 0, 0, 34f, 8.5f);
        b.pop();
        b.push().translate(26.6f, 3.6f, FRONT + 0.08f).rotateZ(38);
        b.decalZ(HAZARD.asFit(), 0, 0, 0, 9f, 1.4f);
        b.pop();
        // ---- plateau de contreplaque, nez d'acier, sacs de sable aux deux bouts
        b.bevelBox(-15.4f, TOP - 1.0f, 0.8f, 31.4f, TOP, 15.4f, 0.14f, PLY);
        b.bevelBox(-15.5f, TOP - 1.15f, 15.2f, 31.5f, TOP + 0.1f, 15.6f, 0.05f, RUST);
        BunkerKit.sandbag(b, -12.0f, TOP, 12.0f, 5.4f, 2.1f, 3.1f, 6, 1, SAND);
        BunkerKit.sandbag(b, -12.4f, TOP, 4.4f, 5.2f, 2.0f, 3.0f, -4, 2, SAND);
        BunkerKit.sandbag(b, -12.1f, TOP + 1.85f, 8.2f, 5.0f, 1.9f, 3.0f, 85, 3, SAND);
        BunkerKit.sandbag(b, 28.2f, TOP, 12.2f, 5.2f, 2.0f, 3.0f, -8, 4, SAND);
        BunkerKit.sandbag(b, 28.0f, TOP, 4.6f, 5.4f, 2.1f, 3.1f, 3, 5, SAND);
        // ---- cloison : tubes d'echafaudage, colliers, grillage de part et d'autre de la vitre
        float[] posts = {-14.4f, WX0, WX1, 30.4f};
        for (float x : posts) {
            b.push().translate(x, TOP, PZ);
            b.cylinder(0.42f, 0, 30.6f - TOP, 8, STEEL, null, STEEL);
            b.pop();
        }
        b.push().translate(-14.9f, 30.2f, PZ).rotateZ(-90);
        b.cylinder(0.42f, 0, 45.8f, 8, STEEL, STEEL, STEEL);
        b.pop();
        for (float[] seg : new float[][]{{-14.4f, WX0}, {WX1, 30.4f}}) {
            b.push().translate(seg[0], 22.6f, PZ).rotateZ(-90);
            b.cylinder(0.36f, 0, seg[1] - seg[0], 8, STEEL, null, null);
            b.pop();
            mesh(b, seg[0] + 0.3f, seg[1] - 0.3f, TOP + 0.1f, 29.9f, PZ - 0.05f);
        }
        for (float x : posts) {
            for (float y : new float[]{22.6f, 30.2f}) b.bevelBox(x - 0.62f, y - 0.62f, PZ - 0.62f, x + 0.62f, y + 0.62f, PZ + 0.62f, 0.12f, RUST);
        }
        // ---- encadrement de la vitre (corniere) et plaques d'acier autour du passe-monnaie
        b.bevelBox(WX0 + 0.3f, WY0 - 0.5f, PZ - 0.35f, WX1 - 0.3f, WY0, PZ + 0.35f, 0.06f, RUST);
        b.bevelBox(WX0 + 0.3f, WY1, PZ - 0.35f, WX1 - 0.3f, WY1 + 0.5f, PZ + 0.35f, 0.06f, RUST);
        b.bevelBox(WX0 + 0.3f, TOP, PZ - 0.25f, SLOT0, WY0 - 0.5f, PZ + 0.25f, 0.06f, STEEL);
        b.bevelBox(SLOT1, TOP, PZ - 0.25f, WX1 - 0.3f, WY0 - 0.5f, PZ + 0.25f, 0.06f, STEEL);
        b.bevelBox(SLOT0, SLOT_Y, PZ - 0.25f, SLOT1, WY0 - 0.5f, PZ + 0.25f, 0.06f, STEEL);
        for (float x : new float[]{WX0 + 1.2f, WX1 - 1.2f}) {
            for (float y : new float[]{TOP + 1.0f, WY0 - 1.4f}) {
                b.push().translate(x, y, PZ + 0.25f).rotateX(90);
                b.rivet(0.2f, 0.12f, RUST);
                b.pop();
            }
        }
        // glissiere du passe-monnaie dans le plateau
        b.bevelBox(SLOT0 - 0.4f, TOP, PZ - 3.4f, SLOT0, TOP + 0.35f, PZ + 3.6f, 0.05f, RUST);
        b.bevelBox(SLOT1, TOP, PZ - 3.4f, SLOT1 + 0.4f, TOP + 0.35f, PZ + 3.6f, 0.05f, RUST);
        // ---- barbeles enroules au-dessus du grillage
        for (float[] seg : new float[][]{{-14.0f, -2.0f}, {18.0f, 30.0f}}) barbed(b, seg[0], seg[1], 31.4f, PZ);
        // ---- enseigne en contreplaque posee sur le tube du haut, ligaturee au fil de fer ; deux lampes au-dessus
        b.bevelBox(-0.6f, 30.7f, PZ - 0.3f, 16.6f, 35.9f, PZ + 0.3f, 0.12f, PLY);
        for (float x : new float[]{1.2f, 14.8f}) {
            b.push().translate(x, 30.6f, PZ);
            b.torus(0.55f, 0.06f, 8, 4, 0, 360, RUST);
            b.pop();
        }
        for (float[] l : SIGN_LAMPS) {
            b.tube(new float[][]{p(l[0], 35.9f, PZ - 0.1f), p(l[0], 37.9f, PZ + 0.2f), p(l[0], 38.6f, l[2] - 0.6f), p(l[0], l[1] + 1.0f, l[2])},
                    new float[]{0.14f, 0.14f, 0.14f, 0.14f}, 5, RUST, true, true, 0, false);
            BunkerKit.cageLamp(b, l[0], l[1], l[2], RUST);
        }
        BunkerKit.cable(b, p(SIGN_LAMPS[0][0], 38.0f, PZ + 0.3f), p(SIGN_LAMPS[1][0], 38.0f, PZ + 0.3f), 1.2f, 0.1f, CABLE);
        // ---- cote du changeur : coffre-caisse, balance, caisses de munitions, baladeuse
        b.push().translate(BOX[0], BOX[1], BOX[2]);
        b.bevelBox(-3.0f, 0, -1.9f, 3.0f, 2.0f, 1.9f, 0.18f, CASHBOX);
        b.bevelBox(-2.7f, 1.9f, -1.6f, 2.7f, 2.02f, 1.6f, 0.02f, DARK);
        b.bevelBox(-0.5f, 1.1f, 1.88f, 0.5f, 1.6f, 2.05f, 0.04f, BRASS);
        b.pop();
        scale(b, -6.4f, TOP, 4.6f);
        BunkerKit.crate(b, 20.6f, TOP, 2.2f, 29.4f, TOP + 4.4f, 7.8f, CRATE, PLY);
        BunkerKit.crate(b, 22.0f, TOP + 4.4f, 2.8f, 28.4f, TOP + 7.8f, 7.2f, CRATE, PLY);
        BunkerKit.jerrycan(b, 18.4f, TOP, 4.2f, 0.62f, -20, HAZARD, RUST);
        BunkerKit.cable(b, p(ROOM_LAMP[0], 30.2f, PZ - 0.4f), p(ROOM_LAMP[0], ROOM_LAMP[1] + 1.0f, ROOM_LAMP[2]), 0.3f, 0.09f, CABLE);
        BunkerKit.cageLamp(b, ROOM_LAMP[0], ROOM_LAMP[1], ROOM_LAMP[2], RUST);
        // ---- ardoise des taux accrochee au grillage, cote client ; sonnette de comptoir
        b.bevelBox(-13.4f, 19.4f, PZ + 0.35f, -3.0f, 28.4f, PZ + 0.75f, 0.1f, PLY, new Mat[]{null, null, null, null, null, SLATE}, 0);
        for (float x : new float[]{-12.0f, -4.4f}) {
            b.tube(new float[][]{p(x, 28.4f, PZ + 0.55f), p(x + 0.4f, 29.4f, PZ + 0.2f), p(x + 0.6f, 29.9f, PZ)}, new float[]{0.05f, 0.05f, 0.05f}, 4, RUST, false, false, 0,
                    false);
        }
        b.push().translate(21.5f, TOP, 13.0f);
        b.cylinder(0.85f, 0, 0.3f, 12, DARK, null, DARK);
        b.push().translate(0, 0.3f, 0);
        b.sphereSection(0.68f, 12, 4, 0, 90, BRASS);
        b.pop();
        b.push().translate(0, 0.98f, 0);
        b.cylinder(0.1f, 0, 0.3f, 6, BRASS, null, BRASS);
        b.pop();
        b.pop();
        // scotch sur une fissure de la vitre (devant)
        b.push().translate(14.6f, 27.0f, PZ + 0.3f).rotateZ(-35);
        b.decalZ(TAPE, 0, 0, 0, 4.2f, 0.9f);
        b.pop();
        Occlusion.bake(b, 0.5f, 3.5f, 0.45f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(8, 33.3f, PZ + 0.31f);
        sg.decalZ(SIGN, 0, 0, 0, 16.8f, 5.0f);
        sg.pop();
        signFace = sg.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(PLEXI, v(0, 0, 1), p(WX0 + 0.3f, WY0, PZ), p(WX1 - 0.3f, WY0, PZ), p(WX1 - 0.3f, WY1, PZ), p(WX0 + 0.3f, WY1, PZ));
        glass = gl.build();
        // bac du passe-monnaie (repere : centre du bac, fond en y = 0)
        MeshBuilder t = new MeshBuilder();
        float tw = (SLOT1 - SLOT0 - 0.3f) / 2, td = 2.2f;
        t.bevelBox(-tw, 0, -td, tw, 0.12f, td, 0.03f, STEEL);
        t.bevelBox(-tw, 0.12f, -td, -tw + 0.12f, 0.75f, td, 0.03f, STEEL);
        t.bevelBox(tw - 0.12f, 0.12f, -td, tw, 0.75f, td, 0.03f, STEEL);
        t.bevelBox(-tw, 0.12f, -td, tw, 0.75f, -td + 0.12f, 0.03f, STEEL);
        t.bevelBox(-tw, 0.12f, td - 0.12f, tw, 0.4f, td, 0.03f, STEEL);
        tray = t.build();
        MeshBuilder ld = new MeshBuilder();
        ld.bevelBox(-3.05f, 0, 0, 3.05f, 0.35f, 3.9f, 0.1f, CASHBOX);
        ld.bevelBox(-0.6f, -0.15f, 3.75f, 0.6f, 0.25f, 4.05f, 0.04f, BRASS);
        lid = ld.build();
        MeshBuilder bl = new MeshBuilder();
        for (float[] l : SIGN_LAMPS) {
            bl.push().translate(l[0], l[1], l[2]);
            bl.sphere(0.42f, 8, 5, BULB);
            bl.pop();
        }
        bulbs = bl.build();
        MeshBuilder rb = new MeshBuilder();
        rb.push().translate(ROOM_LAMP[0], ROOM_LAMP[1], ROOM_LAMP[2]);
        rb.sphere(0.42f, 8, 5, BULB);
        rb.pop();
        roomBulb = rb.build();
        chips = new ChipStacks(0.75f, 0.22f, seg(12));
    }

    /** Panneau de grillage decoupe en carreaux (la maille garde sa taille). */
    private static void mesh(MeshBuilder b, float xa, float xb, float ya, float yb, float z) {
        int nx = Math.max(1, (int) Math.ceil((xb - xa) / 4.8f)), ny = Math.max(1, (int) Math.ceil((yb - ya) / 4.8f));
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float a0 = xa + (xb - xa) * i / nx, a1 = xa + (xb - xa) * (i + 1) / nx, c0 = ya + (yb - ya) * j / ny, c1 = ya + (yb - ya) * (j + 1) / ny;
                b.face(MESH, v(0, 0, 1), p(a0, c0, z), p(a1, c0, z), p(a1, c1, z), p(a0, c1, z));
            }
        }
    }

    /** Rouleau de barbele : spirale de fil rouille et ses pointes. */
    private static void barbed(MeshBuilder b, float xa, float xb, float y, float z) {
        int n = (int) ((xb - xa) * 6);
        float[][] path = new float[n + 1][];
        float[] radii = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            double a = t * (xb - xa) / 1.6 * Math.PI * 2;
            path[i] = p(xa + (xb - xa) * t, y + (float) Math.cos(a) * 1.05f, z + (float) Math.sin(a) * 1.05f);
            radii[i] = 0.06f;
            if (i % 9 == 4) {
                float[] q = path[i];
                b.bevelBox(q[0] - 0.22f, q[1] - 0.03f, q[2] - 0.03f, q[0] + 0.22f, q[1] + 0.03f, q[2] + 0.03f, 0.01f, RUST);
                b.bevelBox(q[0] - 0.03f, q[1] - 0.22f, q[2] - 0.03f, q[0] + 0.03f, q[1] + 0.22f, q[2] + 0.03f, 0.01f, RUST);
            }
        }
        b.tube(path, radii, 4, RUST, true, true, 0, false);
    }

    /** Balance a plateaux (pour peser le loot) posee sur le comptoir. */
    private static void scale(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z);
        b.bevelBox(-2.4f, 0, -0.9f, 2.4f, 0.6f, 0.9f, 0.12f, BRASS);
        b.cylinder(0.18f, 0.6f, 3.6f, 6, BRASS, null, BRASS);
        b.push().translate(0, 3.6f, 0).rotateZ(4);
        b.bevelBox(-2.8f, -0.1f, -0.1f, 2.8f, 0.1f, 0.1f, 0.03f, BRASS);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 2.6f, 0, 0);
            b.tube(new float[][]{p(0, 0, 0), p(0, -1.6f, 0)}, new float[]{0.04f, 0.04f}, 4, RUST, false, false, 0, false);
            b.push().translate(0, -1.7f, 0);
            b.lathe(new float[][]{{0.05f, 0}, {1.1f, 0.05f}, {1.25f, 0.3f}}, 12, 0, 360, BRASS, 1, 1, 0, 0, 0, false);
            b.pop();
            b.pop();
        }
        b.pop();
        b.pop();
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, CashierView v, Pose p, DrawList out) {
        float t = s.item ? -1 : s.since(v.lastTx);
        boolean tx = t >= 0 && t < 70;
        // ampoules de l'enseigne : elles gresillent, s'allument franchement a chaque echange
        float k = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.94f ? 0.3f : 0.9f + 0.1f * (float) Math.sin(s.time * 1.3f);
        if (tx && t < 20) k = ((int) (t / 3)) % 2 == 0 ? 1.2f : 0.5f;
        out.solid(p, bulbs, Math.min(1f, k));
        out.solid(p, signFace, 0.55f * k);
        out.solid(p, roomBulb, 0.85f);
        if (!s.item) {
            for (float[] l : SIGN_LAMPS) out.billboard(p, Sprite.HALO, l[0], l[1], l[2], 2.6f, 0, 0xFFC880, 0.35f * k);
            out.billboard(p, Sprite.HALO, ROOM_LAMP[0], ROOM_LAMP[1], ROOM_LAMP[2], 3.0f, 0, 0xFFC880, 0.3f);
        }
        // piles de pieces derriere la vitre (la reserve du changeur)
        for (int i = 0; i < 5; i++) {
            chips.stackOf(p, out, 3 + (i * 3) % 7, i, 2.0f + i * 1.45f, TOP, 3.3f + (i % 2) * 1.6f);
        }
        // coffre-caisse : le couvercle se souleve pendant l'echange, une pile de pieces dedans
        float open = tx ? Ease.outBack(Math.min(1f, t / 7f), 1.4f) * (1 - Ease.inCubic(Ease.range(t, 48, 62))) : 0;
        p.push().translate(BOX[0], BOX[1] + 2.0f, BOX[2] - 1.95f).rotX(-72 * open);
        out.solid(p, lid, 0);
        p.pop();
        if (open > 0.05f) {
            for (int i = 0; i < 4; i++) chips.stackOf(p, out, 3 + i, (int) ((v.lastTxAmount + i) % 5), BOX[0] - 2.0f + i * 1.35f, BOX[1] + 0.3f, BOX[2]);
        }
        // passe-monnaie : le bac sort vers le joueur avec la monnaie, puis rentre
        float slide = tx ? Ease.inOutSine(Ease.range(t, 6, 18)) * (1 - Ease.inOutSine(Ease.range(t, 50, 64))) : 0;
        float tz = PZ + 0.2f + slide * 3.4f;
        p.push().translate((SLOT0 + SLOT1) / 2, TOP + 0.36f, tz);
        out.solid(p, tray, 0);
        if (tx && t > 4 && t < 60) chips.amount(p, out, v.lastTxAmount, 0, 0.12f, 0, 1.6f, 3);
        p.pop();
        if (s.item) return;
        if (tx && t > 10 && t < 58) {
            String amt = (v.lastTxType == 0 ? "+" : "-") + fmt(v.lastTxAmount);
            float a = Ease.range(t, 10, 16) * (1 - Ease.range(t, 50, 58));
            out.text(p, amt, (SLOT0 + SLOT1) / 2, TOP + 2.6f, tz, fit(amt, 1.0f, 8f, true), v.lastTxType == 0 ? GOLD_RGB : 0xFF8A6A, a,
                    DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_BILLBOARD | DrawList.T_OUTLINE, 0x140C04);
        }
        out.translucent(p, glass, 0xFFFFFF, 0.75f);
        // ardoise des taux, ecrite a la craie
        float bx = -8.2f, bz = PZ + 0.78f;
        out.text(p, "TAUX DU JOUR", bx, 27.3f, bz, 0.66f, CHALK, 0.95f, DrawList.T_CENTER | DrawList.T_BOLD);
        for (int i = 0; i < Math.min(7, v.rates.length); i++) {
            String line = v.rates[i];
            out.text(p, line, bx, 26.0f - i * 0.95f, bz, fit(line, 0.52f, 9.2f, false), CHALK, 0.9f, DrawList.T_CENTER);
        }
        if (tx && t < 30) out.billboard(p, Sprite.GLINT, (SLOT0 + SLOT1) / 2, TOP + 1.2f, tz, 2.2f * (1 - t / 30f), s.time * 4, GOLD_RGB, 0.6f);
    }

    // =================================================================== camera
    @Override
    public void camera(CashierView v, Scene s, int mode, Shot out) {
        out.set(8, 24, 42, 6, 21, 8, 54);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, CashierView v, FxSink fx, Random r) {
        float t = s.since(v.lastTx);
        if (t >= 0 && t <= 70) {
            if (at(t, 0)) {
                fx.sound(Snd.BELL, 21.5f, TOP + 1, 13, 0.6f, 1.6f);
                fx.sound(Snd.SAFE, BOX[0], BOX[1] + 2, BOX[2], 0.45f, 1.4f);
            }
            if (at(t, 7)) fx.sound(Snd.LEVER, 8, TOP + 0.5f, PZ, 0.35f, 1.6f);
            if (at(t, 16)) fx.sound(Snd.COINS, 8, TOP + 1, PZ + 3.4f, 1f, 1f);
            if (at(t, 60)) fx.sound(Snd.LEVER, 8, TOP + 0.5f, PZ, 0.3f, 1.3f);
            if (t > 14 && t < 26 && ((int) t) % 3 == 0) {
                fx.particle(FxSink.SPARK, 8 + rnd(r, 2.5f), TOP + 0.8f, PZ + 3.4f + rnd(r, 1.2f), rnd(r, 0.05f), 0.15f, rnd(r, 0.05f), GOLD_RGB, 0.7f, 14);
            }
        }
        // l'ampoule qui gresille
        if (r.nextInt(500) == 0) fx.particle(FxSink.SPARK, SIGN_LAMPS[1][0], SIGN_LAMPS[1][1], SIGN_LAMPS[1][2], rnd(r, 0.04f), 0.04f, rnd(r, 0.04f), 0xFFE0A0, 0.3f, 8);
    }

    // =================================================================== vues
    @Override
    public CashierView newView() {
        return new CashierView();
    }

    @Override
    public CashierView demoView(float t) {
        CashierView v = newView();
        v.lastTx = DEMO_START + 10;
        v.lastTxAmount = 250;
        return v;
    }

    @Override
    public int demoLength() {
        return 80;
    }
}
