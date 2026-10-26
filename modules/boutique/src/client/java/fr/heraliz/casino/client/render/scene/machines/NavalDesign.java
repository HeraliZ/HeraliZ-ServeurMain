package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Hotspots;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;
import fr.heraliz.casino.game.duel.NavalBoard;

import java.util.Random;
import net.minecraft.client.resources.language.I18n;
import java.util.Locale;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Bataille navale de bunker : une longue table de planches posee sur deux futs rouilles, un paravent de contreplaque
 * au milieu (grille de tir sur chaque face), un plateau par joueur avec sa flotte en 3D, des fiches blanches (a
 * l'eau) et rouges (touche). Chaque joueur est assis sur une caisse a un bout. Les navires intacts ne sont dessines
 * que pour leur proprietaire : les spectateurs ne voient que les tirs et les epaves.
 */
public final class NavalDesign extends MachineDesign<NavalView> {
    // ---- atlas : partage avec tools/textures (PaintNaval)
    public static final int[] R_WOOD = {0, 0, 512, 512};
    public static final int[] R_RUST = {512, 0, 256, 256};
    public static final int[] R_DRUM = {768, 0, 256, 256};
    public static final int[] R_PLY = {512, 256, 256, 256};
    public static final int[] R_GRID = {768, 256, 256, 256};
    public static final int[] R_BOARD = {0, 512, 256, 256};
    public static final int[] R_HULL = {256, 512, 128, 128};
    public static final int[] R_DECK = {384, 512, 128, 128};
    public static final int[] R_PEG_W = {512, 512, 64, 64};
    public static final int[] R_PEG_R = {576, 512, 64, 64};
    public static final int[] R_LAMP = {640, 512, 128, 128};
    public static final int[] R_CRATE = {768, 512, 256, 256};
    public static final int[] R_METAL = {256, 640, 256, 128};
    public static final int[] R_DARK = {512, 576, 128, 64};
    public static final int[] R_TRAY = {0, 768, 256, 128};
    public static final int[] R_CABLE = {256, 768, 256, 128};

    static final Mat WOOD = FruitsDesign.tri(R_WOOD, 24), RUST = FruitsDesign.tri(R_RUST, 12), DRUM = FruitsDesign.tri(R_DRUM, 22);
    static final Mat PLY = FruitsDesign.tri(R_PLY, 20), GRID = FruitsDesign.fit(R_GRID), BOARD = FruitsDesign.fit(R_BOARD);
    static final Mat HULL = FruitsDesign.tri(R_HULL, 4), DECK = FruitsDesign.tri(R_DECK, 4), PEG_W = FruitsDesign.tri(R_PEG_W, 2);
    static final Mat PEG_R = FruitsDesign.tri(R_PEG_R, 2), LAMP = FruitsDesign.fit(R_LAMP), CRATE = FruitsDesign.tri(R_CRATE, 16);
    static final Mat METAL = FruitsDesign.tri(R_METAL, 10), DARK = FruitsDesign.tri(R_DARK, 6), TRAY = FruitsDesign.tri(R_TRAY, 8);
    static final Mat CABLE = FruitsDesign.tri(R_CABLE, 6);

    static final int N = NavalBoard.SIZE;
    /** Table, paravent, plateaux (px). */
    static final float TOP_Y = 12, WALL_X0 = 7.4f, WALL_X1 = 8.6f, WALL_TOP = 31f;
    static final float CELL = 1.55f, G_Z0 = 1.8f, G_Y1 = 29.0f, G_Y0 = G_Y1 - N * CELL;
    static final float BOARD_Y = 12.5f, A_X1 = 5.8f, B_X0 = 10.2f;
    static final int RED = 0xFF4A3A, WHITE = 0xF0F0F0, GOLD = 0xFFD36A, WATER = 0x5FB8FF, OK = 0x7CFF6A;

    private Mesh body = Mesh.EMPTY, gridA = Mesh.EMPTY, gridB = Mesh.EMPTY, boardA = Mesh.EMPTY, boardB = Mesh.EMPTY;
    private Mesh pegW = Mesh.EMPTY, pegR = Mesh.EMPTY, bulb = Mesh.EMPTY, bulbGlow = Mesh.EMPTY, lampA = Mesh.EMPTY, shell = Mesh.EMPTY;
    private final Mesh[] ships = new Mesh[6];

    public NavalDesign() {
        super("bataille_navale", 0x5FB8FF, 0xFF4D5E);
        labelY = 38;
        itemScale = 0.34f;
    }

    // =================================================================== reperes des grilles
    /** Centre d'une case de la grille verticale (face du joueur "side") : {x, y, z}. cx, cy peuvent etre fractionnaires. */
    static float[] wallCell(int side, float cx, float cy) {
        float y = G_Y1 - (cy + 0.5f) * CELL;
        return side == 1 ? new float[]{WALL_X0 - 0.02f, y, G_Z0 + (cx + 0.5f) * CELL} : new float[]{WALL_X1 + 0.02f, y, G_Z0 + N * CELL - (cx + 0.5f) * CELL};
    }

    /** Centre d'une case du plateau du joueur "side" : {x, z}. */
    public static float[] boardCell(int side, float cx, float cy) {
        return side == 1 ? new float[]{A_X1 - (cy + 0.5f) * CELL, G_Z0 + (cx + 0.5f) * CELL}
                : new float[]{B_X0 + (cy + 0.5f) * CELL, G_Z0 + N * CELL - (cx + 0.5f) * CELL};
    }

    /** Case (fractionnaire) du plateau du joueur "side" sous le point (x, z) de la table : {colonne, rangee}. */
    public static float[] boardCellAt(int side, float x, float z) {
        if (side == 1) return new float[]{(z - G_Z0) / CELL - 0.5f, (A_X1 - x) / CELL - 0.5f};
        return new float[]{(G_Z0 + N * CELL - z) / CELL - 0.5f, (x - B_X0) / CELL - 0.5f};
    }

    /** Hauteur du plateau (plan sur lequel on vise avec la souris pendant le placement). */
    public static final float BOARD_PLANE = 12.5f;

    /**
     * Proue proposee pour un navire tenu dont le milieu est sous la souris en (cx, cy) (cases fractionnaires) :
     * le navire reste dans la grille ; -1 si la souris est loin du plateau.
     */
    public static int snapBow(float cx, float cy, int len, boolean horizontal) {
        if (cx < -1.5f || cy < -1.5f || cx > N + 0.5f || cy > N + 0.5f) return -1;
        float bx = horizontal ? cx - (len - 1) / 2f : cx, by = horizontal ? cy : cy - (len - 1) / 2f;
        int x = Math.max(0, Math.min(horizontal ? N - len : N - 1, Math.round(bx)));
        int y = Math.max(0, Math.min(horizontal ? N - 1 : N - len, Math.round(by)));
        return y * N + x;
    }

    // ---- bac des navires a placer, sur le cote de la table (joueur A ; B en symetrie autour du centre)
    static final float DOCK_X0 = -7.4f, DOCK_X1 = 4.6f, DOCK_Z0 = 15.1f, DOCK_Z1 = 20.5f, DOCK_Y = TOP_Y + 0.4f;
    /** Place de chaque navire dans le bac (joueur A) : {x, z} du milieu, navire couche le long de X. */
    static final float[][] DOCK_A = {{-3.2f, 16.1f}, {-3.9f, 17.8f}, {1.9f, 17.8f}, {2.6f, 16.1f}, {-5.4f, 19.5f}};

    /** Point du bac du joueur A transpose du cote du joueur "side". */
    static float[] dockPt(int side, float x, float z) {
        return side == 1 ? new float[]{x, z} : new float[]{16 - x, 16 - z};
    }

    public static float[] dockSlot(int side, int k) {
        float[] s = DOCK_A[k - 1];
        return side == 1 ? s : new float[]{16 - s[0], 16 - s[1]};
    }

    /** Le point (x, z) de la table est-il au-dessus du bac des navires du joueur ? */
    public static boolean overDock(int side, float x, float z) {
        if (side == 2) {
            x = 16 - x;
            z = 16 - z;
        }
        return x >= DOCK_X0 - 0.5f && x <= DOCK_X1 + 0.5f && z >= DOCK_Z0 - 0.5f && z <= DOCK_Z1 + 0.8f;
    }

    /** Navire du bac sous le point (x, z) (0 aucun). */
    public static int dockShipAt(int side, float x, float z, int[] board, int held) {
        for (int k = 1; k <= NavalBoard.SHIPS.length; k++) {
            if (k == held || NavalBoard.ship(board, k) != null) continue;
            float[] s = dockSlot(side, k);
            float hl = NavalBoard.SHIPS[k - 1] * CELL / 2;
            if (Math.abs(x - s[0]) <= hl && Math.abs(z - s[1]) <= 0.8f) return k;
        }
        return 0;
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- deux futs rouilles sous la table
        for (float x : new float[]{1.2f, 14.8f}) {
            b.push().translate(x, 0, 8);
            b.cylinder(3.6f, 0, 11f, seg(22), DRUM, RUST, RUST);
            for (float y : new float[]{0.4f, 3.8f, 7.2f, 10.6f}) {
                b.push().translate(0, y, 0);
                b.torus(3.62f, 0.16f, seg(22), 4, 0, 360, RUST);
                b.pop();
            }
            b.pop();
        }
        // ---- plateau : planches de palette, traverses
        for (int k = 0; k < 5; k++) {
            float z0 = 2f + k * 2.4f;
            b.bevelBox(-8f - (k % 2) * 0.3f, 11f, z0 + 0.06f, 24f + (k % 3) * 0.2f, TOP_Y, z0 + 2.34f, 0.12f, WOOD);
        }
        b.bevelBox(-6.5f, 10.2f, 1.6f, -5f, 11f, 14.4f, 0.1f, WOOD);
        b.bevelBox(21f, 10.2f, 1.6f, 22.5f, 11f, 14.4f, 0.1f, WOOD);
        // ---- paravent de contreplaque, pieds et renfort metallique
        b.bevelBox(WALL_X0, TOP_Y, 0.5f, WALL_X1, WALL_TOP, 15.5f, 0.15f, PLY, new Mat[]{DARK, DARK, null, null, null, null}, MeshBuilder.SKIP_NX | MeshBuilder.SKIP_PX);
        float[] nx = v(-1, 0, 0), px = v(1, 0, 0);
        // faces du paravent autour de la grille (la grille est un decal a part)
        wallFace(b, WALL_X0, nx);
        wallFace(b, WALL_X1, px);
        b.bevelBox(WALL_X0 - 0.6f, TOP_Y, 0.2f, WALL_X1 + 0.6f, TOP_Y + 1.2f, 2.2f, 0.12f, METAL);
        b.bevelBox(WALL_X0 - 0.6f, TOP_Y, 13.8f, WALL_X1 + 0.6f, TOP_Y + 1.2f, 15.8f, 0.12f, METAL);
        b.bevelBox(WALL_X0 - 0.12f, WALL_TOP - 0.4f, 0.4f, WALL_X1 + 0.12f, WALL_TOP + 0.2f, 15.6f, 0.08f, RUST);
        // ---- plateaux (bacs de plastique) de chaque joueur
        for (int side = 1; side <= 2; side++) {
            float x0 = side == 1 ? A_X1 - N * CELL - 0.5f : B_X0 - 0.5f, x1 = side == 1 ? A_X1 + 0.5f : B_X0 + N * CELL + 0.5f;
            b.bevelBox(x0, TOP_Y, G_Z0 - 0.5f, x1, BOARD_Y - 0.02f, G_Z0 + N * CELL + 0.5f, 0.15f, TRAY);
        }
        // ---- bac des navires a placer, fixe sur le cote de la table (a droite de chaque joueur)
        for (int side = 1; side <= 2; side++) {
            float[] a = dockPt(side, DOCK_X0 - 0.3f, DOCK_Z0 - 0.5f), c = dockPt(side, DOCK_X1 + 0.3f, DOCK_Z1 + 0.3f);
            b.bevelBox(Math.min(a[0], c[0]), 11f, Math.min(a[1], c[1]), Math.max(a[0], c[0]), TOP_Y, Math.max(a[1], c[1]), 0.12f, WOOD);
            float[] i0 = dockPt(side, DOCK_X0, DOCK_Z0 - 0.1f), i1 = dockPt(side, DOCK_X1, DOCK_Z1);
            b.bevelBox(Math.min(i0[0], i1[0]), TOP_Y, Math.min(i0[1], i1[1]), Math.max(i0[0], i1[0]), DOCK_Y - 0.02f, Math.max(i0[1], i1[1]), 0.12f, TRAY);
            for (float bx : new float[]{DOCK_X0 + 1.2f, DOCK_X1 - 1.2f}) {
                float[] s0 = dockPt(side, bx - 0.25f, DOCK_Z0 - 1.2f), s1 = dockPt(side, bx + 0.25f, DOCK_Z1 - 1f);
                b.bevelBox(Math.min(s0[0], s1[0]), 9.6f, Math.min(s0[1], s1[1]), Math.max(s0[0], s1[0]), 11f, Math.max(s0[1], s1[1]), 0.06f, RUST);
            }
        }
        // ---- caisses servant de tabourets
        crate(b, -15.5f, -9.5f);
        crate(b, 25.5f, 31.5f);
        // ---- lampe de chantier accrochee au paravent, cable
        b.push().translate(8, WALL_TOP + 0.2f, 8);
        b.bevelBox(-0.5f, 0, -0.4f, 0.5f, 0.9f, 0.4f, 0.1f, METAL);
        b.cylinder(0.12f, 0.9f, 2.6f, 6, RUST, null, null);
        b.pop();
        b.push().translate(8, WALL_TOP + 2.6f, 8);
        b.lathe(new float[][]{{0.3f, 0.4f}, {0.8f, 0.2f}, {1.8f, -1.1f}, {1.95f, -1.3f}}, seg(16), 0, 360, METAL, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.9f, -1.3f}, {1.75f, -1.1f}, {0.75f, 0.1f}, {0.25f, 0.3f}}, seg(16), 0, 360, DARK, 1, 1, 0, 0, 0, false);
        b.pop();
        float[][] cable = {p(8.2f, WALL_TOP + 0.5f, 8.4f), p(8.8f, WALL_TOP - 3, 14.8f), p(9.1f, TOP_Y + 3, 15.6f), p(9.4f, 0.3f, 16.2f)};
        b.tube(cable, new float[]{0.14f, 0.14f, 0.14f, 0.14f}, 6, CABLE, true, true, 0, false);
        // ---- caisse de munitions au pied de la table
        b.bevelBox(4.5f, 0, 12.2f, 11.5f, 3.2f, 15.2f, 0.2f, METAL);
        b.bevelBox(4.3f, 3.0f, 12f, 11.7f, 3.6f, 15.4f, 0.15f, METAL);
        b.bevelBox(7.2f, 3.6f, 13.3f, 8.8f, 3.9f, 14.3f, 0.08f, RUST);
        Occlusion.bake(b, 0.35f, 3f, 0.4f, 0f);
        body = b.build();

        // grilles verticales et plateaux (decals, legerement lumineux pour la lisibilite)
        gridA = wallGrid(WALL_X0 - 0.01f, nx);
        gridB = wallGrid(WALL_X1 + 0.01f, px);
        boardA = boardGrid(1);
        boardB = boardGrid(2);
        MeshBuilder pw = new MeshBuilder();
        pw.cylinder(0.27f, 0, 0.55f, 8, PEG_W, null, PEG_W);
        pw.push().translate(0, 0.55f, 0);
        pw.sphereSection(0.27f, 8, 3, 0, 90, PEG_W);
        pw.pop();
        pegW = pw.build();
        MeshBuilder pr = new MeshBuilder();
        pr.cylinder(0.3f, 0, 0.6f, 8, PEG_R, null, PEG_R);
        pr.push().translate(0, 0.6f, 0);
        pr.sphereSection(0.3f, 8, 3, 0, 90, PEG_R);
        pr.pop();
        pegR = pr.build();
        MeshBuilder bl = new MeshBuilder();
        bl.push().translate(8, WALL_TOP + 1.3f, 8);
        bl.sphere(0.55f, 10, 6, LAMP);
        bl.pop();
        bulb = bl.build();
        MeshBuilder bg = new MeshBuilder();
        bg.push().translate(8, WALL_TOP + 1.3f, 8);
        bg.sphere(0.8f, 10, 6, LAMP);
        bg.pop();
        bulbGlow = bg.build();
        MeshBuilder la = new MeshBuilder();
        la.sphere(0.35f, 8, 5, LAMP);
        lampA = la.build();
        MeshBuilder sh = new MeshBuilder();
        sh.push().rotateX(180);
        sh.cylinder(0.22f, -0.6f, 0.3f, 8, DARK, DARK, null);
        sh.push().translate(0, 0.3f, 0);
        sh.sphereSection(0.22f, 8, 3, 0, 90, DARK);
        sh.pop();
        sh.pop();
        shell = sh.build();
        for (int len = 2; len <= 5; len++) ships[len] = ship(len);
    }

    /** Face du paravent autour de la zone de la grille. */
    private static void wallFace(MeshBuilder b, float x, float[] n) {
        float z0 = 0.5f, z1 = 15.5f, gz0 = G_Z0 - 0.4f, gz1 = G_Z0 + N * CELL + 0.4f, gy0 = G_Y0 - 0.4f, gy1 = G_Y1 + 1.4f;
        b.face(PLY, n, p(x, TOP_Y, z0), p(x, TOP_Y, z1), p(x, gy0, z1), p(x, gy0, z0));
        b.face(PLY, n, p(x, gy1, z0), p(x, gy1, z1), p(x, WALL_TOP, z1), p(x, WALL_TOP, z0));
        b.face(PLY, n, p(x, gy0, z0), p(x, gy0, gz0), p(x, gy1, gz0), p(x, gy1, z0));
        b.face(PLY, n, p(x, gy0, gz1), p(x, gy0, z1), p(x, gy1, z1), p(x, gy1, gz1));
        b.face(DARK, n, p(x, gy0, gz0), p(x, gy0, gz1), p(x, gy1, gz1), p(x, gy1, gz0));
    }

    private Mesh wallGrid(float x, float[] n) {
        MeshBuilder b = new MeshBuilder();
        float z0 = G_Z0, z1 = G_Z0 + N * CELL;
        b.face(GRID, n, p(x, G_Y0, z0), p(x, G_Y0, z1), p(x, G_Y1, z1), p(x, G_Y1, z0));
        return b.build();
    }

    private Mesh boardGrid(int side) {
        MeshBuilder b = new MeshBuilder();
        float x0 = side == 1 ? A_X1 - N * CELL : B_X0, x1 = x0 + N * CELL;
        b.face(BOARD, v(0, 1, 0), p(x0, BOARD_Y, G_Z0 + N * CELL), p(x1, BOARD_Y, G_Z0 + N * CELL), p(x1, BOARD_Y, G_Z0), p(x0, BOARD_Y, G_Z0));
        return b.build();
    }

    private void crate(MeshBuilder b, float x0, float x1) {
        b.bevelBox(x0, 0, 5f, x1, 7.3f, 11f, 0.2f, CRATE);
        for (float y : new float[]{0.4f, 6.4f}) {
            b.bevelBox(x0 - 0.12f, y, 4.9f, x1 + 0.12f, y + 0.6f, 11.1f, 0.08f, WOOD);
        }
        b.bevelBox(x0 + 0.3f, 7.3f, 5.3f, x1 - 0.3f, 7.5f, 10.7f, 0.05f, WOOD);
    }

    /** Navire de guerre gris a l'echelle d'une grille (proue vers +X, centre a l'origine, pose sur y = 0). */
    private Mesh ship(int len) {
        MeshBuilder b = new MeshBuilder();
        float hl = (len * CELL - 0.35f) / 2;
        float w = len >= 4 ? 0.55f : 0.45f;
        float[][] hull = {{-hl, -w + 0.05f}, {hl - 0.95f, -w}, {hl, 0}, {hl - 0.95f, w}, {-hl, w - 0.05f}};
        b.push().rotateX(-90);
        b.prism(hull, 0.5f, DECK, HULL);
        b.pop();
        if (len == 5) {
            // porte-avions : pont plat, ilot sur le cote
            b.bevelBox(-hl + 0.2f, 0.5f, -w + 0.1f, hl - 0.6f, 0.62f, w - 0.1f, 0.03f, DECK);
            b.bevelBox(-0.4f, 0.62f, -w + 0.12f, 0.5f, 1.25f, -w + 0.45f, 0.05f, HULL);
            b.push().translate(0, 1.25f, -w + 0.28f);
            b.cylinder(0.04f, 0, 0.55f, 4, HULL, null, null);
            b.pop();
        } else {
            b.bevelBox(-0.45f, 0.5f, -0.28f, 0.35f, 1.0f, 0.28f, 0.06f, HULL);
            b.bevelBox(-0.3f, 1.0f, -0.18f, 0.2f, 1.25f, 0.18f, 0.04f, DECK);
            b.push().translate(-0.05f, 1.25f, 0);
            b.cylinder(0.04f, 0, 0.5f, 4, HULL, null, null);
            b.pop();
            if (len >= 3) {
                for (float tx : new float[]{hl * 0.58f, -hl * 0.62f}) {
                    b.push().translate(tx, 0.5f, 0);
                    b.cylinder(0.24f, 0, 0.22f, 8, HULL, null, DECK);
                    b.pop();
                    float dir = tx > 0 ? 1 : -1;
                    b.bevelBox(Math.min(tx, tx + dir * 0.7f), 0.6f, -0.05f, Math.max(tx, tx + dir * 0.7f), 0.68f, 0.05f, 0.01f, DARK);
                }
            }
        }
        return b.build();
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, NavalView v, Pose p, DrawList out) {
        out.solid(p, gridA, 0.25f);
        out.solid(p, gridB, 0.25f);
        out.solid(p, boardA, 0.2f);
        out.solid(p, boardB, 0.2f);
        // ---- lampe du paravent (vacille de temps en temps)
        float flick = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.94f ? 0.3f : 1f;
        out.solid(p, bulb, flick);
        out.additive(p, bulbGlow, 0xFFE0A0, 0.35f * flick);
        if (!s.item) out.billboard(p, Sprite.HALO, 8, WALL_TOP + 1.2f, 8, 7f, 0, 0xFFD8A0, 0.12f * flick);
        if (s.item) {
            drawShip(p, out, 1, 1, 2, true, 3, 0, 0);
            drawShip(p, out, 2, 3, 1, false, 4, 0, 0);
            return;
        }
        labels(p, out);
        boolean playing = v.phase == 2 || v.phase == 3;
        // ---- voyants de chaque joueur (vert = pret / a son tour)
        for (int side = 1; side <= 2; side++) {
            boolean on;
            if (!playing) on = false;
            else if (v.step == 0) on = side == 1 ? v.readyA : v.readyB;
            else on = v.turn == side && ((int) (s.time / 6)) % 2 == 0 && v.phase == 2;
            float x = side == 1 ? -7.2f : 23.2f, z = side == 1 ? 15f : 1f;
            p.push().translate(x, TOP_Y + 0.35f, z);
            out.solidTinted(p, lampA, on ? 1f : 0.2f, on ? 0.5f : 0.35f, on ? 1f : 0.2f, on ? 0.5f : 0.2f);
            if (on) out.additive(p, lampA, OK, 0.8f);
            p.pop();
        }
        if (!playing) {
            // table au repos : deux navires poses sur chaque plateau
            drawShip(p, out, 1, 2, 3, true, 4, 0, 0);
            drawShip(p, out, 2, 5, 1, false, 3, 0, 0);
            return;
        }
        // ---- ma flotte (moi seul la vois)
        if (v.mySide != 0 && v.myBoard.length == NavalBoard.CELLS) {
            boolean setup = v.phase == 2 && v.step == 0;
            boolean hoverFree = setup && v.held == 0 && !Float.isNaN(v.holdX);
            int hoverShip = 0;
            if (hoverFree) {
                float[] cc = boardCellAt(v.mySide, v.holdX, v.holdZ);
                int gx = Math.round(cc[0]), gy = Math.round(cc[1]);
                if (gx >= 0 && gy >= 0 && gx < N && gy < N) hoverShip = v.myBoard[gy * N + gx];
                if (hoverShip == 0) hoverShip = dockShipAt(v.mySide, v.holdX, v.holdZ, v.myBoard, 0);
            }
            for (int k = 1; k <= NavalBoard.SHIPS.length; k++) {
                if (k == v.held && setup) continue;
                int[] sh = NavalBoard.ship(v.myBoard, k);
                float glow = k == hoverShip ? 0.45f + 0.25f * s.pulse(0.4f) : 0;
                if (sh == null) {
                    // pas encore place : il attend dans le bac, sur le cote de la table
                    if (!setup) continue;
                    float[] d = dockSlot(v.mySide, k);
                    p.push().translate(d[0], DOCK_Y, d[1]).rotY(v.mySide == 1 ? 0 : 180);
                    out.solid(p, ships[NavalBoard.SHIPS[k - 1]], glow);
                    p.pop();
                    continue;
                }
                if (isWreck(v.mySide == 1 ? v.wrecksA : v.wrecksB, k)) continue;
                drawShip(p, out, v.mySide, sh[0], sh[1], sh[2] == 1, sh[3], glow, 0);
            }
            // placement : le navire tenu suit la souris, souleve au-dessus de la table ; son emplacement s'affiche dessous
            if (setup && v.held > 0 && !Float.isNaN(v.holdX)) {
                int len = NavalBoard.SHIPS[v.held - 1];
                float bob = 0.15f * (float) Math.sin(s.time * 0.25f);
                float yaw = v.heldH ? (v.mySide == 1 ? -90 : 90) : (v.mySide == 1 ? 180 : 0);
                p.push().translate(v.holdX, BOARD_Y + 2.2f + bob, v.holdZ).rotY(yaw).rotX(4 * (float) Math.sin(s.time * 0.18f));
                out.solid(p, ships[len], 0.25f);
                p.pop();
                if (v.snapCell >= 0) ghost(p, out, v.mySide, v.snapCell % N, v.snapCell / N, v.heldH, len, v.snapOk);
            }
        }
        // ---- epaves (publiques) : sur le plateau du proprietaire, et en relief sur la grille de tir de l'adversaire
        for (int side = 1; side <= 2; side++) {
            int[] w = side == 1 ? v.wrecksA : v.wrecksB;
            for (int k = 0; k < w.length; k++) {
                if (w[k] < 0) continue;
                boolean fresh = v.lastShotSide == 3 - side && v.sinceShot < SHOT;
                if (fresh && isLastShotShip(w[k], v.lastShot)) continue;
                int x = w[k] & 15, y = (w[k] >> 4) & 15, h = (w[k] >> 8) & 1, len = (w[k] >> 9) & 15;
                drawShip(p, out, side, x, y, h == 1, len, 0, 1);
                // coule a l'instant : il clignote un moment sur la grille du tireur
                boolean justSunk = v.lastShotSide == 3 - side && isLastShotShip(w[k], v.lastShot) && v.sinceShot < SHOT + 40;
                float flash = justSunk ? 0.5f + 0.5f * (float) Math.sin((v.sinceShot - SHOT) * 0.8f) : 0;
                wallWreck(p, out, 3 - side, x, y, h == 1, len, flash);
            }
        }
        // ---- fiches : tirs recus sur chaque plateau, et reportes sur la grille verticale du tireur
        for (int side = 1; side <= 2; side++) {
            long shots = side == 1 ? v.shotsOnA : v.shotsOnB, hits = side == 1 ? v.hitsOnA : v.hitsOnB;
            int[] mine = v.mySide == side ? v.myBoard : null;
            for (int c = 0; c < NavalBoard.CELLS; c++) {
                if ((shots & (1L << c)) == 0) continue;
                boolean pending = c == v.lastShot && v.lastShotSide == 3 - side && v.sinceShot < SHOT;
                if (pending) continue;
                boolean hit = (hits & (1L << c)) != 0;
                float[] bc = boardCell(side, c % N, c / N);
                float onShip = hit && (mine != null && mine.length == NavalBoard.CELLS && mine[c] != 0 || isWreckCell(side == 1 ? v.wrecksA : v.wrecksB, c)) ? 0.55f : 0;
                p.push().translate(bc[0], BOARD_Y + onShip, bc[1]);
                out.solid(p, hit ? pegR : pegW, hit ? 0.35f : 0.1f);
                p.pop();
                // grille verticale du tireur (l'autre joueur)
                int shooter = 3 - side;
                float[] wc = wallCell(shooter, c % N, c / N);
                p.push().translate(wc[0], wc[1], wc[2]).rotZ(shooter == 1 ? 90 : -90);
                out.solid(p, hit ? pegR : pegW, hit ? 0.35f : 0.1f);
                p.pop();
            }
        }
        // ---- obus en vol puis impact
        if (v.lastShot >= 0 && v.lastShotSide != 0 && v.sinceShot < SHOT + 30) {
            int target = 3 - v.lastShotSide;
            float[] bc = boardCell(target, v.lastShot % N, v.lastShot / N);
            long hits = target == 1 ? v.hitsOnA : v.hitsOnB;
            boolean hit = (hits & (1L << v.lastShot)) != 0;
            if (v.sinceShot < SHOT) {
                float k = v.sinceShot / SHOT;
                float y = BOARD_Y + 18 * (1 - k * k);
                p.push().translate(bc[0], y, bc[1]);
                out.solid(p, shell, 0);
                p.pop();
                out.billboard(p, Sprite.HALO, bc[0], y + 1.2f, bc[1], 0.9f, 0, 0xFFB060, 0.5f);
            } else {
                float k = v.sinceShot - SHOT;
                boolean sunk = hit && isWreckCell(target == 1 ? v.wrecksA : v.wrecksB, v.lastShot);
                String txt = sunk ? I18n.get("heralizcasino.naval.coule") : hit ? I18n.get("screen.heralizcasino.naval.hit")
                        : I18n.get("screen.heralizcasino.naval.miss").toUpperCase(Locale.ROOT);
                float a = Math.min(1f, k / 3f) * (1 - Ease.range(k, 22, 30));
                float pop = Ease.outBack(Math.min(1f, k / 5f), 2.5f);
                out.text(p, txt, bc[0], BOARD_Y + 5 + k * 0.08f, bc[1], 1.7f * pop, sunk ? GOLD : hit ? RED : WATER, a,
                        DrawList.T_CENTER | DrawList.T_BILLBOARD | DrawList.T_OUTLINE | DrawList.T_BOLD, 0x000000);
                if (hit && k < 8) out.billboard(p, Sprite.FLARE, bc[0], BOARD_Y + 1, bc[1], 3.5f * (1 - k / 8f), s.time * 5, 0xFF7A3A, 0.8f);
            }
        }

    }

    static final float SHOT = 12;

    private static boolean isWreck(int[] wrecks, int k) {
        return k - 1 < wrecks.length && wrecks[k - 1] >= 0;
    }

    private static boolean isWreckCell(int[] wrecks, int cell) {
        for (int w : wrecks) {
            if (w >= 0 && inWreck(w, cell)) return true;
        }
        return false;
    }

    private static boolean inWreck(int w, int cell) {
        int x = w & 15, y = (w >> 4) & 15, h = (w >> 8) & 1, len = (w >> 9) & 15;
        int cx = cell % N, cy = cell / N;
        for (int i = 0; i < len; i++) {
            if (x + (h == 1 ? i : 0) == cx && y + (h == 1 ? 0 : i) == cy) return true;
        }
        return false;
    }

    private static boolean isLastShotShip(int w, int lastShot) {
        return lastShot >= 0 && inWreck(w, lastShot);
    }

    private static boolean cellOfShip(int[] board, int k, int cell) {
        return cell >= 0 && cell < board.length && board[cell] == k;
    }

    /** Navire sur le plateau d'un joueur : proue en (x, y), horizontal (le long des colonnes) ou vertical. */
    private void drawShip(Pose p, DrawList out, int side, int x, int y, boolean horizontal, int len, float glow, float wreck) {
        if (len < 2 || len > 5) return;
        float mx = x + (horizontal ? (len - 1) / 2f : 0), my = y + (horizontal ? 0 : (len - 1) / 2f);
        float[] c = boardCell(side, mx, my);
        // direction de la proue : colonnes = +Z pour A, -Z pour B ; rangees = -X pour A, +X pour B
        float yaw = horizontal ? (side == 1 ? -90 : 90) : (side == 1 ? 180 : 0);
        p.push().translate(c[0], BOARD_Y - wreck * 0.25f, c[1]).rotY(yaw);
        if (wreck > 0) {
            p.rotX(7).rotZ(-4);
            out.solidTinted(p, ships[len], 0, 0.28f, 0.25f, 0.22f);
        } else {
            out.solid(p, ships[len], glow);
        }
        p.pop();
    }

    /** Navire coule reporte sur la grille verticale du tireur : maquette calcinee plaquee contre le paravent. */
    private void wallWreck(Pose p, DrawList out, int shooter, int x, int y, boolean horizontal, int len, float flash) {
        if (len < 2 || len > 5) return;
        float mx = x + (horizontal ? (len - 1) / 2f : 0), my = y + (horizontal ? 0 : (len - 1) / 2f);
        float[] c = wallCell(shooter, mx, my);
        p.push().translate(c[0], c[1], c[2]).rotZ(shooter == 1 ? 90 : -90);
        if (horizontal) p.rotY(90);
        p.scale(1, 0.4f, 1);
        out.solidTinted(p, ships[len], 0.15f + 0.5f * flash, 0.55f + 0.45f * flash, 0.18f + 0.2f * flash, 0.12f);
        if (flash > 0) out.additive(p, ships[len], RED, 0.6f * flash);
        p.pop();
    }

    /** Apercu translucide d'un deplacement pendant le placement. */
    private void ghost(Pose p, DrawList out, int side, int x, int y, boolean horizontal, int len, boolean fits) {
        float mx = x + (horizontal ? (len - 1) / 2f : 0), my = y + (horizontal ? 0 : (len - 1) / 2f);
        float[] c = boardCell(side, mx, my);
        float yaw = horizontal ? (side == 1 ? -90 : 90) : (side == 1 ? 180 : 0);
        p.push().translate(c[0], BOARD_Y + 0.05f, c[1]).rotY(yaw);
        out.additive(p, ships[len], fits ? OK : RED, 0.55f);
        p.pop();
    }

    /** Lettres et chiffres des grilles (paravent et plateaux). */
    private void labels(Pose p, DrawList out) {
        for (int side = 1; side <= 2; side++) {
            // paravent
            p.push().translate(side == 1 ? WALL_X0 - 0.03f : WALL_X1 + 0.03f, 0, 0).rotY(side == 1 ? -90 : 90);
            for (int i = 0; i < N; i++) {
                float[] w = wallCell(side, i, 0);
                float lx = side == 1 ? w[2] : -w[2];
                out.text(p, String.valueOf((char) ('A' + i)), lx, G_Y1 + 0.75f, 0, 0.75f, 0xE8E0C8, 0.9f, DrawList.T_CENTER);
                float[] r = wallCell(side, 0, i);
                float lz = side == 1 ? G_Z0 - 0.75f : -(G_Z0 + N * CELL + 0.75f);
                out.text(p, Integer.toString(i + 1), lz, r[1], 0, 0.75f, 0xE8E0C8, 0.9f, DrawList.T_CENTER);
            }
            p.pop();
        }
    }

    // =================================================================== zones cliquables
    /** Identifiants : 100 + case = tir sur la grille verticale ; 200 + case = case de mon plateau (placement). */
    public static final int SHOOT = 100, PLACE = 200;

    @Override
    public void hotspots(NavalView v, Scene s, Hotspots out) {
        if (v.mySide == 0 || v.phase != 2) return;
        int side = v.mySide;
        // placement : gere directement par l'ecran (le navire tenu suit la souris sur le plan de la table)
        if (v.step != 0) {
            float h = CELL / 2;
            for (int c = 0; c < NavalBoard.CELLS; c++) {
                float[] w = wallCell(side, c % N, c / N);
                float xo = side == 1 ? w[0] - 0.02f : w[0] + 0.02f;
                out.add(SHOOT + c, xo, w[1] - h, w[2] - h, 0, 0, CELL, 0, CELL, 0);
            }
        }
    }

    // =================================================================== camera
    @Override
    public void camera(NavalView v, Scene s, int mode, Shot out) {
        if (v.mySide != 0 && v.phase == 2) {
            boolean a = v.mySide == 1;
            float ex = a ? -11.2f : 27.2f;
            // apres mon tir, on reste face a la grille le temps de voir le resultat (meme a l'eau)
            boolean myShot = v.lastShotSide == v.mySide && v.sinceShot < SHOT + 34;
            if (v.step == 0) {
                // placement : mon plateau et le bac des navires sur le cote
                out.set(a ? -13.5f : 29.5f, 25, a ? 11 : 5, a ? -0.5f : 16.5f, BOARD_Y, a ? 11 : 5, 60);
            } else if (v.turn == v.mySide || myShot) {
                out.set(ex, 21.8f, 8, a ? WALL_X0 : WALL_X1, 22.4f, 8, 56);
            } else if (v.lastShotSide != 0 && v.lastShotSide != v.mySide && v.sinceShot <= SHOT + 20) {
                // l'obus de l'adversaire tombe sur ma flotte
                out.set(ex, 22, 8, a ? -0.4f : 16.4f, BOARD_Y + 1, 8, 58);
            } else {
                // mon plateau : l'adversaire vise
                out.set(ex, 21.8f, 8, a ? -0.8f : 16.8f, BOARD_Y, 8, 58);
            }
            return;
        }
        out.set(8, 36, 62, 8, 14, 8, 50);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, NavalView v, FxSink fx, Random r) {
        if (r.nextInt(80) == 0) fx.particle(FxSink.FLY, 8 + rnd(r, 8), TOP_Y + 4 + r.nextFloat() * 6, 8 + rnd(r, 6), 0, 0, 0, 0x202020, 0.35f, 60);
        // epaves qui fument
        for (int side = 1; side <= 2; side++) {
            int[] w = side == 1 ? v.wrecksA : v.wrecksB;
            for (int k : w) {
                if (k < 0 || r.nextInt(8) != 0) continue;
                int x = k & 15, y = (k >> 4) & 15, h = (k >> 8) & 1, len = (k >> 9) & 15;
                float f = r.nextFloat() * (len - 1);
                float[] c = boardCell(side, x + (h == 1 ? f : 0), y + (h == 1 ? 0 : f));
                fx.particle(FxSink.SMOKE, c[0] + rnd(r, 0.3f), BOARD_Y + 0.8f, c[1] + rnd(r, 0.3f), rnd(r, 0.01f), 0.05f, rnd(r, 0.01f), 0x2A2826, 0.8f, 40);
            }
        }
        if (v.lastShot < 0 || v.lastShotSide == 0) return;
        int target = 3 - v.lastShotSide;
        float[] bc = boardCell(target, v.lastShot % N, v.lastShot / N);
        long hits = target == 1 ? v.hitsOnA : v.hitsOnB;
        boolean hit = (hits & (1L << v.lastShot)) != 0;
        float t = v.sinceShot;
        if (at(t, 0)) fx.sound(Snd.CANNON, 8, WALL_TOP, 8, 0.9f, 0.9f + r.nextFloat() * 0.2f);
        if (at(t, (int) SHOT)) {
            if (hit) {
                boolean sunk = isWreckCell(target == 1 ? v.wrecksA : v.wrecksB, v.lastShot);
                fx.sound(Snd.NAVAL_HIT, bc[0], BOARD_Y, bc[1], 1f, 1f);
                if (sunk) {
                    fx.sound(Snd.SINK, bc[0], BOARD_Y, bc[1], 1f, 0.8f);
                    fx.sound(Snd.HORN, 8, WALL_TOP, 8, 1f, 0.9f);
                }
                burst(fx, r, bc[0], BOARD_Y + 0.6f, bc[1], 0xFF6A2A, 0xFFD36A, sunk ? 30 : 14, 0.5f, 0.5f);
                for (int i = 0; i < 6; i++) {
                    fx.particle(FxSink.FLAME, bc[0] + rnd(r, 0.4f), BOARD_Y + 0.5f, bc[1] + rnd(r, 0.4f), rnd(r, 0.03f), 0.08f, rnd(r, 0.03f), 0xFF8A3A, 0.6f, 14);
                    fx.particle(FxSink.SMOKE, bc[0] + rnd(r, 0.5f), BOARD_Y + 0.8f, bc[1] + rnd(r, 0.5f), rnd(r, 0.03f), 0.07f, rnd(r, 0.03f), 0x2A2826, 1f, 30);
                }
            } else {
                fx.sound(Snd.SPLASH, bc[0], BOARD_Y, bc[1], 1f, 1f);
                for (int i = 0; i < 10; i++) {
                    fx.particle(FxSink.DROP, bc[0], BOARD_Y + 0.4f, bc[1], rnd(r, 0.12f), 0.25f + r.nextFloat() * 0.2f, rnd(r, 0.12f), 0x7AC8FF, 0.35f, 20);
                }
            }
        }
        // le navire coule sombre en bouillonnant
        if (hit && at(t, (int) SHOT + 8) && isWreckCell(target == 1 ? v.wrecksA : v.wrecksB, v.lastShot)) {
            fx.sound(Snd.SUNK, bc[0], BOARD_Y, bc[1], 1f, 0.9f);
            for (int i = 0; i < 12; i++) fx.particle(FxSink.BUBBLE, bc[0] + rnd(r, 1.2f), BOARD_Y + 0.3f, bc[1] + rnd(r, 1.2f), 0, 0.05f, 0, 0xB8E0FF, 0.3f, 25);
        }
        if (v.phase == 3 && at(v.sincePhase, 2) && v.winner != 3) {
            fx.sound(Snd.WIN_BIG, 8, 24, 8, 1f, 1f);
            fountain(fx, r, 8, WALL_TOP + 2, 8, FxSink.CONFETTI, new int[]{0xFFD36A, 0x5FB8FF, 0xFF4D5E}, 36, 0.35f);
        }
    }

    // =================================================================== vues
    @Override
    public NavalView newView() {
        return new NavalView();
    }

    @Override
    public NavalView demoView(float t) {
        NavalView v = new NavalView();
        v.phase = 2;
        v.step = 1;
        v.mySide = 1;
        v.nameA = "Logan";
        v.nameB = "Rival";
        v.myBoard = new int[NavalBoard.CELLS];
        NavalBoard.move(v.myBoard, 1, 1, 1, true);
        NavalBoard.move(v.myBoard, 2, 6, 2, false);
        NavalBoard.move(v.myBoard, 3, 0, 4, false);
        NavalBoard.move(v.myBoard, 4, 3, 6, true);
        NavalBoard.move(v.myBoard, 5, 2, 3, true);
        v.shotsOnA = (1L << 9) | (1L << 20) | (1L << 44) | (1L << 63) | (1L << 30);
        v.hitsOnA = (1L << 9) | (1L << 30);
        v.shotsOnB = (1L << 5) | (1L << 13) | (1L << 21) | (1L << 40) | (1L << 41) | (1L << 58);
        v.hitsOnB = (1L << 5) | (1L << 13) | (1L << 21) | (1L << 41);
        v.wrecksA = new int[]{-1, -1, -1, -1, -1};
        v.wrecksB = new int[]{-1, -1, 5 | 0 << 4 | 0 << 8 | 3 << 9, -1, -1};
        v.turn = 1;
        v.lastShot = 41;
        v.lastShotSide = 1;
        v.sinceShot = t % 50;
        return v;
    }

    @Override
    public int demoLength() {
        return 50;
    }
}
