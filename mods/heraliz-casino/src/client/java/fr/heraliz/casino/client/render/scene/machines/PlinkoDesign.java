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
import fr.heraliz.casino.game.plinko.PlinkoGame;

import java.util.Locale;
import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Plinko, version bunker : une grande planche a clous bricolee. Panneau de contreplaque peint et marque au pochoir,
 * pris entre deux tubes d'echafaudage rouilles, 12 rangees de boulons en guise de clous, entonnoir de tole, 13 boites
 * de conserve peintes selon leur multiplicateur, plexiglas sale et fendu. Le tout repose sur deux futs et une palette,
 * sous une enseigne de tole et deux tubes fluorescents fatigues. Les billes d'acier rebondissent de boulon en boulon
 * (les boulons touches s'allument) et tombent dans les boites, qui sautillent a l'arrivee.
 */
public final class PlinkoDesign extends MachineDesign<PlinkoView> {
    // ---- atlas : partage avec tools/textures (PaintPlinko)
    public static final int[] R_PLY = {0, 0, 512, 512};
    public static final int[] R_BOARD = {512, 0, 512, 512};
    public static final int[] R_RUST = {0, 512, 256, 256};
    public static final int[] R_NEON = {256, 512, 256, 128};
    public static final int[] R_BALL = {256, 640, 128, 128};
    public static final int[] R_CAN = {384, 640, 128, 128};
    public static final int[] R_SIGN = {512, 512, 512, 256};
    public static final int[] R_PANEL = {512, 768, 512, 256};
    public static final int[] R_GLASS = {0, 768, 128, 64};
    public static final int[] R_DARK = {128, 768, 128, 64};
    public static final int[] R_PEG = {256, 768, 128, 128};
    public static final int[] R_WOOD = {384, 768, 128, 128};
    public static final int[] R_DRUM = {0, 832, 256, 192};
    public static final int[] R_BULB = {256, 896, 64, 64};
    public static final int[] R_CABLE = {320, 896, 64, 64};
    public static final int[] R_TAPE = {384, 896, 128, 64};

    private static final Mat PLY = FruitsDesign.tri(R_PLY, 24), BOARD = FruitsDesign.fit(R_BOARD), RUST = FruitsDesign.tri(R_RUST, 12);
    private static final Mat NEON = FruitsDesign.fit(R_NEON), BALL = FruitsDesign.tri(R_BALL, 4), CAN = FruitsDesign.tri(R_CAN, 5);
    private static final Mat SIGN = FruitsDesign.fit(R_SIGN), PANEL = FruitsDesign.fit(R_PANEL), GLASS = FruitsDesign.fit(R_GLASS);
    private static final Mat DARK = FruitsDesign.tri(R_DARK, 8), PEG = FruitsDesign.tri(R_PEG, 4), WOOD = FruitsDesign.tri(R_WOOD, 10);
    /** Futs du support : rayon, et hauteur couverte par leur tuile (le marquage tombe entre les cerclages). */
    public static final float DRUM_R = 4.5f, DRUM_WORLD = 12.1f;
    private static final Mat DRUM = FruitsDesign.tri(R_DRUM, DRUM_WORLD), BULB = FruitsDesign.fit(R_BULB), CABLE = FruitsDesign.tri(R_CABLE, 6);
    private static final Mat TAPE = FruitsDesign.fit(R_TAPE);

    static final int ROWS = PlinkoGame.ROWS;
    static final float CX = 8f, DX = 2.8f, DY = 2.7f, ROW0_Y = 51.6f;
    static final float BACK_Z = 6.2f, BALL_Z = 7.9f, GLASS_Z = 10.4f, BALL_R = 0.78f, PEG_R = 0.36f;
    static final float BX0 = -12.6f, BX1 = 28.6f, BY0 = 13.6f, BY1 = 57.6f;
    static final float BUCKET_Y = ROW0_Y - ROWS * DY - 1.0f;
    /** Baladeuse de l'enseigne. */
    static final float[] LAMP = {8, BY1 + 10.2f, 9.4f};
    static final int PINK = 0xFF4FD8, CYAN = 0x5FE3FF, GOLD_RGB = 0xFFD36A, AMBER = 0xFFB040, TUBE = 0xD8FFD0, STEEL = 0xB8D0E8;

    private Mesh body = Mesh.EMPTY, signGlow = Mesh.EMPTY, tubeL = Mesh.EMPTY, tubeR = Mesh.EMPTY, glass = Mesh.EMPTY, ball = Mesh.EMPTY, ballGlow = Mesh.EMPTY;
    private Mesh bucket = Mesh.EMPTY, bucketGlow = Mesh.EMPTY, pegGlow = Mesh.EMPTY, bulb = Mesh.EMPTY;

    public PlinkoDesign() {
        super("plinko", PINK, CYAN);
        labelY = 68;
        itemScale = 0.22f;
    }

    static float pegX(int row, int k) {
        return CX + (k - (row + 2) / 2f) * DX;
    }

    static float rowY(float row) {
        return ROW0_Y - row * DY;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- support : palette au sol, deux futs, madrier, panneau de contreplaque au pochoir devant
        BunkerKit.pallet(b, -14.6f, 0, 3.6f, 30.6f, 1.5f, 13.6f, 10, WOOD);
        for (float x : new float[]{-7.6f, 23.6f}) BunkerKit.drum(b, x, 1.5f, 8.2f, DRUM_R, 11.4f, seg(22), DRUM, RUST);
        b.bevelBox(-14.2f, 12.9f, 3.8f, 30.2f, BY0, 12.6f, 0.15f, WOOD);
        b.bevelBox(-11.4f, 2.3f, 12.7f, 27.4f, 12.5f, 13.4f, 0.12f, PLY);
        b.push().translate(CX, 7.4f, 13.42f);
        b.decalZ(PANEL, 0, 0, 0, 38f, 9.4f);
        b.pop();
        // ---- panneau du jeu : fond de contreplaque peint, deux tubes d'echafaudage, traverse haute
        b.bevelBox(BX0 - 1.2f, BY0, 3.8f, BX1 + 1.2f, BY1 + 1.2f, BACK_Z, 0.3f, PLY, new Mat[]{null, null, null, null, null, BOARD}, 0);
        for (float x : new float[]{BX0 - 0.8f, BX1 + 0.8f}) {
            b.push().translate(x, BY0, (3.6f + GLASS_Z + 0.8f) / 2);
            b.cylinder(0.85f, 0, BY1 + 3.4f - BY0, seg(10), RUST, null, RUST);
            // colliers d'echafaudage
            for (float y : new float[]{3f, (BY1 - BY0) / 2, BY1 - BY0 - 1f}) {
                b.push().translate(0, y, 0);
                b.cylinder(1.05f, 0, 0.9f, seg(10), RUST, RUST, RUST);
                b.pop();
            }
            b.pop();
            // flancs de contreplaque entre le fond et la vitre
            float x0 = x < CX ? BX0 - 0.35f : BX1, x1 = x0 + 0.35f;
            b.bevelBox(x0, BY0, BACK_Z, x1, BY1 + 1.2f, GLASS_Z, 0.04f, PLY);
        }
        b.bevelBox(BX0 - 1.9f, BY1 + 1.2f, 3.6f, BX1 + 1.9f, BY1 + 2.7f, GLASS_Z + 0.8f, 0.2f, WOOD);
        // pattes qui tiennent la vitre, adhesif sur la fente
        for (float[] c : new float[][]{{BX0 + 0.5f, BY0 + 1.2f}, {BX1 - 0.5f, BY0 + 1.2f}, {BX0 + 0.5f, BY1}, {BX1 - 0.5f, BY1}}) {
            b.bevelBox(c[0] - 0.7f, c[1] - 0.5f, GLASS_Z - 0.05f, c[0] + 0.7f, c[1] + 0.5f, GLASS_Z + 0.3f, 0.06f, RUST);
        }
        b.push().translate(BX1 - 9.5f, BY1 - 9.5f, GLASS_Z + 0.06f).rotateZ(-38);
        b.decalZ(TAPE, 0, 0, 0, 11f, 1.7f);
        b.pop();
        // ---- clous : boulons rouilles a tete hexagonale
        for (int r = 0; r < ROWS; r++) {
            for (int k = 0; k < r + 3; k++) {
                b.push().translate(pegX(r, k), rowY(r), BACK_Z).rotateX(90);
                b.cylinder(PEG_R, 0, GLASS_Z - BACK_Z - 0.35f, seg(8), PEG, null, null);
                b.cylinder(PEG_R * 1.45f, GLASS_Z - BACK_Z - 0.35f, GLASS_Z - BACK_Z - 0.1f, 6, PEG, PEG, PEG);
                b.pop();
            }
        }
        // ---- entonnoir (deux toles inclinees) au-dessus du premier clou
        for (int sd = -1; sd <= 1; sd += 2) {
            float x0 = CX + sd * 1.5f, x1 = CX + sd * 7f;
            float ya = ROW0_Y + 2.4f, yb = ROW0_Y + 5.4f;
            float len = (float) Math.hypot(x1 - x0, yb - ya);
            float ang = (float) Math.toDegrees(Math.atan2(yb - ya, x1 - x0));
            b.push().translate((x0 + x1) / 2, (ya + yb) / 2, (BACK_Z + GLASS_Z) / 2).rotateZ(ang);
            b.bevelBox(-len / 2, -0.2f, -(GLASS_Z - BACK_Z) / 2, len / 2, 0.2f, (GLASS_Z - BACK_Z) / 2, 0.05f, RUST);
            b.pop();
        }
        // ---- separations des boites
        for (int i = 0; i <= ROWS + 1; i++) {
            float x = CX + (i - (ROWS + 1) / 2f) * DX;
            b.bevelBox(x - 0.12f, BUCKET_Y - 2.4f, BACK_Z, x + 0.12f, BUCKET_Y + 1.3f, GLASS_Z, 0.03f, RUST);
        }
        b.bevelBox(CX - (ROWS + 1) / 2f * DX - 0.2f, BUCKET_Y - 2.9f, BACK_Z, CX + (ROWS + 1) / 2f * DX + 0.2f, BUCKET_Y - 2.4f, GLASS_Z + 0.2f, 0.1f, WOOD);
        // ---- enseigne de tole au pochoir, baladeuse, boitiers des tubes fluorescents
        b.bevelBox(BX0 + 4, BY1 + 2.7f, 5.4f, BX1 - 4, BY1 + 8.6f, 7.4f, 0.2f, RUST, new Mat[]{null, null, null, null, null, SIGN}, 0);
        for (float x : new float[]{BX0 + 6.5f, BX1 - 6.5f}) b.bevelBox(x - 0.4f, BY1 + 2.7f, 4.4f, x + 0.4f, BY1 + 8.4f, 5.4f, 0.06f, WOOD);
        b.bevelBox(LAMP[0] - 0.12f, LAMP[1] + 0.9f, 6.2f, LAMP[0] + 0.12f, LAMP[1] + 1.05f, LAMP[2], 0.03f, RUST);
        b.bevelBox(LAMP[0] - 0.15f, BY1 + 8.4f, 6.0f, LAMP[0] + 0.15f, LAMP[1] + 1.05f, 6.3f, 0.04f, RUST);
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2], RUST);
        for (float x : new float[]{BX0 - 0.8f, BX1 + 0.8f}) {
            float z = GLASS_Z + 1.35f;
            b.bevelBox(x - 0.75f, BY0 + 5.2f, z - 0.45f, x + 0.75f, BY0 + 6.6f, z + 0.5f, 0.1f, RUST);
            b.bevelBox(x - 0.75f, BY1 - 5.6f, z - 0.45f, x + 0.75f, BY1 - 4.2f, z + 0.5f, 0.1f, RUST);
            b.bevelBox(x - 0.55f, BY0 + 6.6f, z - 0.45f, x + 0.55f, BY1 - 5.6f, z - 0.2f, 0.05f, RUST);
        }
        // cable d'alimentation qui descend derriere un fut
        BunkerKit.cable(b, p(BX1 + 1.2f, BY0 + 5.4f, GLASS_Z + 1.2f), p(BX1 + 1.6f, 1.6f, 12.6f), 0.6f, 0.16f, CABLE);
        Occlusion.bake(b, 0.45f, 3.5f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(CX, BY1 + 5.65f, 7.42f);
        sg.decalZ(SIGN, 0, 0, 0, BX1 - BX0 - 8.4f, 5.5f);
        sg.pop();
        signGlow = sg.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();

        // tubes fluorescents verticaux, de part et d'autre du panneau
        tubeL = tube(BX0 - 0.8f);
        tubeR = tube(BX1 + 0.8f);

        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, v(0, 0, 1), p(BX0, BY0, GLASS_Z), p(BX1, BY0, GLASS_Z), p(BX1, BY1 + 1.2f, GLASS_Z), p(BX0, BY1 + 1.2f, GLASS_Z));
        glass = gl.build();

        MeshBuilder bl = new MeshBuilder();
        bl.sphere(BALL_R, seg(14), seg(9), BALL);
        ball = bl.build();
        MeshBuilder bg = new MeshBuilder();
        bg.sphere(BALL_R * 1.5f, seg(12), seg(7), NEON);
        ballGlow = bg.build();

        // boite de conserve (origine : centre du bord superieur, face +Z) : corps teinte, bord roule
        MeshBuilder bk = new MeshBuilder();
        bk.bevelBox(-DX / 2 + 0.2f, -2.3f, BACK_Z + 0.05f, DX / 2 - 0.2f, 0f, GLASS_Z - 0.1f, 0.3f, CAN);
        bk.bevelBox(-DX / 2 + 0.14f, -0.28f, BACK_Z + 0.02f, DX / 2 - 0.14f, 0.02f, GLASS_Z - 0.06f, 0.1f, CAN);
        bucket = bk.build();
        MeshBuilder bkg = new MeshBuilder();
        bkg.face(NEON, v(0, 0, 1), p(-DX / 2 + 0.2f, -2.3f, GLASS_Z - 0.08f), p(DX / 2 - 0.2f, -2.3f, GLASS_Z - 0.08f),
                p(DX / 2 - 0.2f, 0f, GLASS_Z - 0.08f), p(-DX / 2 + 0.2f, 0f, GLASS_Z - 0.08f));
        bucketGlow = bkg.build();

        MeshBuilder pgl = new MeshBuilder();
        pgl.push().rotateX(90);
        pgl.cylinder(PEG_R * 2.2f, 0.2f, GLASS_Z - BACK_Z, seg(10), NEON, null, NEON);
        pgl.pop();
        pegGlow = pgl.build();
    }

    private Mesh tube(float x) {
        MeshBuilder n = new MeshBuilder();
        float z = GLASS_Z + 1.4f;
        n.tube(new float[][]{p(x, BY0 + 6.6f, z), p(x, BY1 - 5.6f, z)}, new float[]{0.2f, 0.2f}, seg(8), NEON, true, true, 0, true);
        return n.build();
    }

    /** Couleur d'une boite selon son multiplicateur (peinture passee). */
    static int bucketColor(double m) {
        if (m >= 10) return 0xE23A34;
        if (m >= 3) return 0xE8772E;
        if (m >= 1.5) return 0xE0A42E;
        if (m >= 1) return 0xD8C24A;
        if (m >= 0.5) return 0x6E9CC0;
        return 0x56628A;
    }

    /** Etiquette compacte d'un multiplicateur : 80, 16, 1.5, 0.7... */
    static String label(double m) {
        if (m >= 10) return String.valueOf(Math.round(m));
        String s = String.format(Locale.ROOT, "%.1f", m);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Eclat d'un tube fluorescent fatigue : il tient, puis papillote de temps en temps (le gauche surtout). */
    private static float tubeLight(Scene s, int which) {
        float t = s.time + which * 37 + s.seed * 900;
        int c = (int) Math.floor(t / (which == 0 ? 70 : 190));
        float start = c * (which == 0 ? 70 : 190) + Ease.hash(c * 13 + which) * 40, len = which == 0 ? 16 : 7;
        if (t >= start && t < start + len) return Ease.hash((int) (t * 1.7f) + c) > 0.45f ? 0.95f : 0.12f;
        return 0.86f + 0.06f * (float) Math.sin(s.time * 2.3f + which);
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, PlinkoView v, Pose p, DrawList out) {
        float lampK = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.95f ? 0.35f : 0.92f + 0.08f * (float) Math.sin(s.time * 1.1f);
        out.solid(p, bulb, lampK);
        out.solid(p, signGlow, 0.5f * lampK);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2], 2.8f, 0, 0xFFC880, 0.3f * lampK);
        // ---- tubes fluorescents : blanc verdatre, le gauche papillote
        float kl = s.item ? 0.9f : tubeLight(s, 0), kr = s.item ? 0.9f : tubeLight(s, 1);
        out.solidTinted(p, tubeL, 0, 0.5f, 0.56f, 0.5f);
        out.additive(p, tubeL, TUBE, 0.55f * kl);
        out.solidTinted(p, tubeR, 0, 0.5f, 0.56f, 0.5f);
        out.additive(p, tubeR, TUBE, 0.55f * kr);
        if (!s.item) {
            for (int k = 0; k < 3; k++) {
                float y = BY0 + 10 + k * 13;
                out.billboard(p, Sprite.HALO, BX0 - 0.8f, y, GLASS_Z + 1.7f, 3.4f, 0, TUBE, 0.13f * kl);
                out.billboard(p, Sprite.HALO, BX1 + 0.8f, y, GLASS_Z + 1.7f, 3.4f, 0, TUBE, 0.13f * kr);
            }
        }
        // ---- boites : couleur selon le multiplicateur, rebond a l'arrivee d'une bille
        float[] bump = new float[ROWS + 1];
        float[] flash = new float[ROWS + 1];
        for (int i = 0; i < v.count; i++) {
            float land = v.t[i] - (PlinkoGame.DURATION - PlinkoGame.LAND / 2f);
            if (land >= 0 && land < 20) {
                int bk = PlinkoGame.bucket(v.path[i]);
                bump[bk] = Math.max(bump[bk], Ease.spring(land, 0.9f, 0.25f));
                flash[bk] = Math.max(flash[bk], 1 - land / 20f);
            }
        }
        for (int i = 0; i <= ROWS; i++) {
            double m = v.multipliers[i];
            int c = bucketColor(m);
            float x = CX + (i - ROWS / 2f) * DX;
            p.push().translate(x, BUCKET_Y - bump[i] * 0.8f, 0);
            out.solidTinted(p, bucket, 0, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            out.additive(p, bucketGlow, c, 0.06f + 0.85f * flash[i]);
            if (!s.item) {
                String lab = label(m);
                out.text(p, lab, 0, -1.15f, GLASS_Z + 0.02f, fit(lab, 1.05f, DX - 0.55f, true), 0xF4EEDC, 1f, DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x14100A);
            }
            p.pop();
        }
        if (s.item) {
            out.translucent(p, glass, 0xFFFFFF, 0.32f);
            return;
        }
        // ---- billes d'acier
        float[] pos = new float[3];
        for (int i = 0; i < v.count; i++) {
            float t = v.t[i];
            if (t < 0 || t > PlinkoGame.DURATION + 10) continue;
            PlinkoGame.position(v.path[i], Math.min(t, PlinkoGame.DURATION), pos);
            float x = CX + pos[0] * DX, y = rowY(pos[1]) + BALL_R + PEG_R;
            int col = v.mine[i] ? GOLD_RGB : STEEL;
            float fade = t > PlinkoGame.DURATION ? 1 - (t - PlinkoGame.DURATION) / 10f : 1;
            p.push().translate(x, y, BALL_Z).rotZ(-pos[2]);
            out.solidTinted(p, ball, 0.35f, 1f, v.mine[i] ? 0.86f : 1f, v.mine[i] ? 0.5f : 1f);
            out.additive(p, ballGlow, col, (v.mine[i] ? 0.4f : 0.22f) * fade);
            p.pop();
            // boulon touche : il s'allume un court instant
            float tt = t - PlinkoGame.DROP;
            if (tt >= 0 && tt < ROWS * PlinkoGame.PER_ROW + 4) {
                int row = (int) (tt / PlinkoGame.PER_ROW);
                float since = tt - row * PlinkoGame.PER_ROW;
                if (row < ROWS && since < 4) {
                    float px = 0;
                    for (int r = 0; r < row; r++) px += ((v.path[i] >> r) & 1) != 0 ? 0.5f : -0.5f;
                    p.push().translate(CX + px * DX, rowY(row), BACK_Z);
                    out.additive(p, pegGlow, v.mine[i] ? GOLD_RGB : AMBER, 0.8f * (1 - since / 4f));
                    p.pop();
                }
            }
        }
        out.translucent(p, glass, 0xFFFFFF, 0.32f);
    }

    // =================================================================== camera
    @Override
    public void camera(PlinkoView v, Scene s, int mode, Shot out) {
        out.set(CX, 33, 64, CX, 29, 8, 58);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, PlinkoView v, FxSink fx, Random r) {
        // le tube de gauche gresille quand il papillote
        if (tubeLight(s, 0) < 0.3f && r.nextInt(3) == 0) {
            fx.particle(FxSink.SPARK, BX0 - 0.8f, BY1 - 6f, GLASS_Z + 1.6f, rnd(r, 0.05f), -0.04f, 0.05f, 0xE8FFE0, 0.4f, 8);
        }
        for (int i = 0; i < v.count; i++) {
            float t = v.t[i];
            if (at(t, 0)) fx.sound(Snd.WHOOSH, CX, ROW0_Y + 5, 10, 0.25f, 1.8f);
            float tt = t - PlinkoGame.DROP;
            if (tt >= 0 && tt < ROWS * PlinkoGame.PER_ROW && Math.abs(tt % PlinkoGame.PER_ROW) < 1f) {
                int row = (int) (tt / PlinkoGame.PER_ROW);
                float px = 0;
                for (int k = 0; k < row; k++) px += ((v.path[i] >> k) & 1) != 0 ? 0.5f : -0.5f;
                fx.sound(Snd.PEG, CX + px * DX, rowY(row), 10, 0.35f, 1.0f + row * 0.04f + r.nextFloat() * 0.1f);
            }
            float land = t - (PlinkoGame.DURATION - PlinkoGame.LAND / 2f);
            if (land >= 0 && land < 1) {
                int bk = PlinkoGame.bucket(v.path[i]);
                double m = v.multipliers[bk];
                float x = CX + (bk - ROWS / 2f) * DX;
                fx.sound(Snd.BUCKET, x, BUCKET_Y, 10, 0.7f, m >= 1 ? 1.3f : 0.7f);
                if (m >= 1) {
                    fx.sound(m >= 10 ? Snd.WIN_BIG : Snd.COINS, x, BUCKET_Y, 10, m >= 10 ? 1f : 0.5f, 1.1f);
                    burst(fx, r, x, BUCKET_Y + 0.5f, GLASS_Z + 0.5f, bucketColor(m), 0xFFFFFF, m >= 10 ? 30 : 8, 0.9f, 0.9f);
                }
            }
        }
    }

    // =================================================================== vues
    @Override
    public PlinkoView newView() {
        return new PlinkoView();
    }

    @Override
    public PlinkoView demoView(float t) {
        PlinkoView v = newView();
        Random r = new Random(9);
        for (int i = 0; i < 12; i++) {
            v.path[i] = i == 5 ? 0 : r.nextInt(1 << ROWS);
            v.t[i] = t - i * 7;
            v.mine[i] = i % 3 == 0;
        }
        v.count = 12;
        return v;
    }

    @Override
    public int demoLength() {
        return 160;
    }
}
