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
import fr.heraliz.casino.game.duel.Rps;

import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Pierre-feuille-ciseaux dans un bunker : un vieux touret de cable sert de table, un tableau des scores a LED de
 * recuperation est visse dessus, une lampe a petrole eclaire le tout. Les deux joueurs se tiennent debout de chaque
 * cote, mains dans le dos pendant le "CHI - FOU - MI", puis sortent leur objet et le jettent sur la table :
 * la pierre ecrase les ciseaux, la feuille recouvre la pierre, les ciseaux decoupent la feuille.
 */
public final class RpsDesign extends MachineDesign<RpsView> {
    // ---- atlas (x, y, largeur, hauteur) : partage avec tools/textures (PaintRps)
    public static final int[] R_WOOD = {0, 0, 512, 512};
    public static final int[] R_RUST = {512, 0, 256, 256};
    public static final int[] R_METAL = {768, 0, 256, 256};
    public static final int[] R_TOP = {512, 256, 256, 256};
    public static final int[] R_STONE = {768, 256, 256, 256};
    public static final int[] R_MAT_A = {0, 512, 256, 256};
    public static final int[] R_MAT_B = {256, 512, 256, 256};
    public static final int[] R_PAPER = {512, 512, 256, 256};
    public static final int[] R_STEEL = {768, 512, 128, 128};
    public static final int[] R_HANDLE = {896, 512, 128, 128};
    public static final int[] R_LED = {768, 640, 128, 128};
    public static final int[] R_DARK = {896, 640, 128, 64};
    public static final int[] R_GLASS = {896, 704, 128, 64};
    public static final int[] R_BOARD = {0, 768, 512, 256};
    public static final int[] R_CABLE = {512, 768, 256, 128};
    public static final int[] R_CAN = {512, 896, 256, 128};
    public static final int[] R_FLAME = {768, 768, 128, 128};
    public static final int[] R_BRASS = {896, 768, 128, 128};

    static final Mat WOOD = FruitsDesign.tri(R_WOOD, 20), RUST = FruitsDesign.tri(R_RUST, 10), METAL = FruitsDesign.tri(R_METAL, 10);
    static final Mat TOP = FruitsDesign.fit(R_TOP), STONE = FruitsDesign.tri(R_STONE, 3), MAT_A = FruitsDesign.fit(R_MAT_A);
    static final Mat MAT_B = FruitsDesign.fit(R_MAT_B), PAPER = FruitsDesign.fit(R_PAPER), STEEL = FruitsDesign.tri(R_STEEL, 4);
    static final Mat HANDLE = FruitsDesign.tri(R_HANDLE, 4), LED = FruitsDesign.fit(R_LED), DARK = FruitsDesign.tri(R_DARK, 6);
    static final Mat GLASS = FruitsDesign.fit(R_GLASS), BOARD = FruitsDesign.fit(R_BOARD), CABLE = FruitsDesign.tri(R_CABLE, 6);
    static final Mat CAN = FruitsDesign.fit(R_CAN), FLAME = FruitsDesign.fit(R_FLAME), BRASS = FruitsDesign.tri(R_BRASS, 6);

    /** Plateau du touret, rayon et hauteur. */
    static final float CX = 8, CZ = 8, TOP_Y = 12, TOP_R = 7.2f;
    /** Places des joueurs (debout), mains approximatives au moment du lancer. */
    static final float[] HAND_A = {-2.5f, 20.5f, 9.6f}, HAND_B = {18.5f, 20.5f, 6.4f};
    /** Objets poses sur la table (A a gauche, B a droite). */
    static final float[] REST_A = {5.3f, TOP_Y, 8}, REST_B = {10.7f, TOP_Y, 8};
    /** Tableau des scores (face avant). */
    static final float BX0 = 1.6f, BX1 = 14.4f, BY0 = 26f, BY1 = 34f, BZ = 2.45f;
    static final float[] LAMP = {12.9f, TOP_Y, 3.9f};
    static final int LED_RED = 0xFF5A2E, LED_AMBER = 0xFFB040, LED_GREEN = 0x7CFF6A, GOLD = 0xFFD36A;

    private Mesh body = Mesh.EMPTY, boardFace = Mesh.EMPTY, lampGlass = Mesh.EMPTY, flame = Mesh.EMPTY, flameGlow = Mesh.EMPTY;
    private Mesh rock = Mesh.EMPTY, paperFlat = Mesh.EMPTY, paperHalfL = Mesh.EMPTY, paperHalfR = Mesh.EMPTY;
    private Mesh bladeA = Mesh.EMPTY, bladeB = Mesh.EMPTY, ringA = Mesh.EMPTY, ringB = Mesh.EMPTY, screw = Mesh.EMPTY;

    public RpsDesign() {
        super("pierre_feuille_ciseaux", 0xFFB040, 0xFF4D5E);
        labelY = 38;
        itemScale = 0.5f;
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- touret de cable : deux flasques en planches, noyau enroule de cable noir
        b.push().translate(CX, 0, CZ);
        b.cylinder(7.0f, 0, 1.3f, seg(30), WOOD, WOOD, WOOD);
        b.cylinder(3.0f, 1.3f, 10.6f, seg(18), WOOD, null, null);
        for (int k = 0; k < 11; k++) {
            b.push().translate(0, 1.85f + k * 0.8f, 0).rotateY(k * 33);
            b.torus(3.45f, 0.42f, seg(22), 6, 0, 360, CABLE);
            b.pop();
        }
        b.cylinder(TOP_R, 10.6f, TOP_Y, seg(30), WOOD, WOOD, TOP);
        // cerclage de fer et boulons
        b.hollowCylinder(TOP_R + 0.12f, TOP_R - 0.05f, 10.85f, 11.55f, seg(30), RUST);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8 + 0.2;
            b.push().translate((float) Math.cos(a) * 6.1f, TOP_Y, (float) Math.sin(a) * 6.1f);
            b.rivet(0.32f, 0.2f, RUST);
            b.pop();
        }
        // trou central du touret (tube de fer)
        b.push().translate(0, TOP_Y - 0.05f, 0);
        b.hollowCylinder(0.9f, 0.62f, 0, 0.12f, seg(14), RUST);
        b.pop();
        b.pop();
        // ---- tableau des scores : tube visse au plateau, caisson de tole
        b.push().translate(CX, TOP_Y, 1.5f);
        b.cylinder(0.45f, 0, BY0 - TOP_Y + 0.5f, seg(10), RUST, null, RUST);
        b.cylinder(0.85f, 0, 0.35f, seg(12), RUST, null, RUST);
        b.pop();
        b.bevelBox(BX0 - 0.5f, BY0 - 0.5f, 0.5f, BX1 + 0.5f, BY1 + 0.5f, BZ - 0.05f, 0.3f, METAL);
        // visserie et petit auvent
        b.bevelBox(BX0 - 0.7f, BY1 + 0.4f, 0.3f, BX1 + 0.7f, BY1 + 0.75f, BZ + 0.6f, 0.1f, RUST);
        for (float x : new float[]{BX0 - 0.1f, BX1 + 0.1f}) {
            for (float y : new float[]{BY0, BY1}) {
                b.push().translate(x, y, BZ - 0.05f).rotateX(90);
                b.rivet(0.22f, 0.12f, RUST);
                b.pop();
            }
        }
        // cable electrique qui pend du tableau
        float[][] cable = {p(BX1 - 0.8f, BY0 - 0.4f, 1.4f), p(BX1 + 0.4f, BY0 - 3f, 1.6f), p(BX1 + 0.2f, BY0 - 7f, 1.9f), p(13.4f, TOP_Y + 0.2f, 2.3f)};
        b.tube(cable, new float[]{0.16f, 0.16f, 0.16f, 0.16f}, 6, CABLE, true, true, 0, false);
        // ---- tapis caoutchouc aux places des joueurs
        b.box6(-17, 0, 3, -7, 0.18f, 13, WOOD, WOOD, null, MAT_A, WOOD, WOOD);
        b.box6(23, 0, 3, 33, 0.18f, 13, WOOD, WOOD, null, MAT_B, WOOD, WOOD);
        // ---- lampe a petrole (socle, cheminee, anse)
        b.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        b.cylinder(1.05f, 0, 0.55f, seg(14), BRASS, null, BRASS);
        b.cylinder(0.55f, 2.6f, 2.95f, seg(12), BRASS, BRASS, BRASS);
        b.push().translate(0, 3.0f, 0).rotateZ(90);
        b.torus(0.9f, 0.07f, seg(12), 4, 0, 180, BRASS);
        b.pop();
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            b.push().translate((float) Math.cos(a) * 0.82f, 0.5f, (float) Math.sin(a) * 0.82f);
            b.cylinder(0.06f, 0, 2.2f, 4, BRASS, null, null);
            b.pop();
        }
        b.pop();
        // ---- boite de conserve et douilles sur la table
        b.push().translate(3.3f, TOP_Y, 11.6f);
        b.cylinder(0.85f, 0, 1.7f, seg(14), CAN, RUST, RUST);
        b.pop();
        float[][] shells = {{10.5f, 12.4f, 30}, {11.4f, 13.1f, 95}, {4.2f, 4.4f, 160}};
        for (float[] s : shells) {
            b.push().translate(s[0], TOP_Y + 0.14f, s[1]).rotateY(s[2]).rotateZ(90);
            b.cylinder(0.14f, -0.5f, 0.5f, 6, BRASS, BRASS, BRASS);
            b.pop();
        }
        Occlusion.bake(b, 0.35f, 3f, 0.4f, 0f);
        body = b.build();

        // ecran du tableau (fond sombre imprime)
        MeshBuilder bf = new MeshBuilder();
        bf.face(BOARD, v(0, 0, 1), p(BX0, BY0, BZ), p(BX1, BY0, BZ), p(BX1, BY1, BZ), p(BX0, BY1, BZ));
        boardFace = bf.build();
        // verre de la lampe et flamme
        MeshBuilder lg = new MeshBuilder();
        lg.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        lg.lathe(new float[][]{{0.5f, 0.55f}, {0.8f, 1.1f}, {0.85f, 1.6f}, {0.62f, 2.2f}, {0.5f, 2.6f}}, seg(14), 0, 360, GLASS, 1, 1, 0, 0, 0, false);
        lg.pop();
        lampGlass = lg.build();
        MeshBuilder fl = new MeshBuilder();
        fl.blob(0.18f, 0.42f, 0.18f, 1.3f, 0, 0, 0, 8, FLAME);
        flame = fl.build();
        MeshBuilder fg = new MeshBuilder();
        fg.sphere(0.55f, 10, 6, FLAME);
        flameGlow = fg.build();

        // ---- objets du jeu (centres a l'origine, poses sur y = 0)
        MeshBuilder rb = new MeshBuilder();
        rb.push().translate(0, 0.85f, 0);
        rb.rock(1.15f, 0.85f, 1.0f, 0.28f, 1.7f, 3, seg(16), STONE);
        rb.pop();
        rock = rb.build();
        paperFlat = paperMesh(-1.7f, 1.7f, 0, 1, 0);
        paperHalfL = paperMesh(-1.7f, 0, 0, 0.5f, 0);
        paperHalfR = paperMesh(0, 1.7f, 0.5f, 1, 0);
        // ciseaux : pivot a l'origine, lames vers +X, a plat dans le plan XZ (epaisseur en Y)
        bladeA = blade(1);
        bladeB = blade(-1);
        ringA = ring(1);
        ringB = ring(-1);
        MeshBuilder sc = new MeshBuilder();
        sc.cylinder(0.14f, -0.1f, 0.26f, 8, STEEL, STEEL, STEEL);
        screw = sc.build();
    }

    /** Feuille (plan XZ, a plat) ; demi-feuilles pour la decoupe. */
    private Mesh paperMesh(float x0, float x1, float s0, float s1, float lift) {
        MeshBuilder b = new MeshBuilder();
        Mat m = PAPER.sub(s0, 0, s1, 1);
        float z0 = -1.45f, z1 = 1.45f;
        b.face(m, v(0, 1, 0), p(x0, lift + 0.02f, z1), p(x1, lift + 0.02f, z1), p(x1, lift + 0.02f, z0), p(x0, lift + 0.02f, z0));
        b.face(m, v(0, -1, 0), p(x0, lift, z0), p(x1, lift, z0), p(x1, lift, z1), p(x0, lift, z1));
        return b.build();
    }

    /** Une lame de ciseaux (cote +1 / -1 en Z) avec son bras. */
    private Mesh blade(int side) {
        MeshBuilder b = new MeshBuilder();
        b.push().rotateX(90);
        float s = side;
        float[][] outline = {{0, -0.24f * s}, {2.7f, -0.02f * s}, {2.75f, 0.03f * s}, {0.35f, 0.2f * s}, {-0.2f, 0.15f * s}};
        if (side < 0) {
            java.util.Collections.reverse(java.util.Arrays.asList(outline));
        }
        b.push().translate(0, 0, side > 0 ? 0 : -0.12f);
        b.prism(outline, 0.12f, STEEL, STEEL);
        b.pop();
        b.pop();
        return b.build();
    }

    /** Anneau de prise en plastique rouge (cote +1 / -1). */
    private Mesh ring(int side) {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(-0.9f, side > 0 ? 0.05f : -0.05f, -0.25f * side);
        b.bevelBox(-0.35f, -0.08f, -0.14f, 0.9f, 0.08f, 0.14f, 0.05f, HANDLE);
        b.pop();
        b.push().translate(-1.75f, side > 0 ? 0.05f : -0.05f, -0.55f * side);
        b.torus(0.62f, 0.17f, seg(14), 6, 0, 360, HANDLE);
        b.pop();
        return b.build();
    }

    // =================================================================== objets (aussi tenus en main par les joueurs)
    /** Dessine un objet (pierre, feuille, ciseaux) a l'origine de la pose ; open = ouverture des ciseaux (degres). */
    public void drawObject(Pose p, DrawList out, int kind, float open, float glow) {
        ensureBuilt();
        switch (kind) {
            case Rps.ROCK -> out.solid(p, rock, glow);
            case Rps.PAPER -> out.solid(p, paperFlat, glow);
            case Rps.SCISSORS -> {
                p.push().rotY(open / 2);
                out.solid(p, bladeA, glow);
                out.solid(p, ringA, glow);
                p.pop();
                p.push().rotY(-open / 2);
                out.solid(p, bladeB, glow);
                out.solid(p, ringB, glow);
                p.pop();
                out.solid(p, screw, glow);
            }
            default -> {
            }
        }
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.solid(p, boardFace, 0.35f);
    }

    @Override
    protected void animate(Scene s, RpsView v, Pose p, DrawList out) {
        // ---- lampe : flamme qui vacille
        float fl = 0.75f + 0.25f * (float) Math.sin(s.time * 0.9f + s.seed * 5) * (float) Math.sin(s.time * 0.37f);
        p.push().translate(LAMP[0], LAMP[1] + 1.25f, LAMP[2]).scale(1, 0.85f + 0.3f * fl, 1);
        out.solid(p, flame, 1f);
        out.additive(p, flameGlow, 0xFF9A3A, 0.35f + 0.25f * fl);
        p.pop();
        out.translucent(p, lampGlass, 0xFFE8C8, 0.35f);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1] + 1.4f, LAMP[2], 4.5f + fl, 0, 0xFF9A3A, 0.16f + 0.08f * fl);
        if (s.item) {
            // objet d'inventaire : les trois objets poses sur la table
            p.push().translate(4.6f, TOP_Y, 8.6f).rotY(30);
            drawObject(p, out, Rps.ROCK, 0, 0);
            p.pop();
            p.push().translate(8.6f, TOP_Y, 6.4f).rotY(-20);
            drawObject(p, out, Rps.PAPER, 0, 0);
            p.pop();
            p.push().translate(11f, TOP_Y + 0.15f, 10f).rotY(110);
            drawObject(p, out, Rps.SCISSORS, 28, 0);
            p.pop();
            return;
        }
        scoreboard(s, v, p, out);
        if (v.phase == 2 && v.step == 1 || v.phase == 3 && v.step == 1 && v.sinceReveal < Rps.REVEAL_TICKS + 60) {
            reveal(s, v, p, out);
        } else if (v.phase <= 1) {
            // table libre : les trois objets attendent au centre
            p.push().translate(5.4f, TOP_Y, 9.2f).rotY(25);
            drawObject(p, out, Rps.ROCK, 0, 0);
            p.pop();
            p.push().translate(8.9f, TOP_Y, 6.8f).rotY(-15);
            drawObject(p, out, Rps.PAPER, 0, 0);
            p.pop();
            p.push().translate(10.8f, TOP_Y + 0.15f, 10.4f).rotY(120);
            drawObject(p, out, Rps.SCISSORS, 25, 0);
            p.pop();
        }
    }

    /** Tableau des scores a LED. */
    private void scoreboard(Scene s, RpsView v, Pose p, DrawList out) {
        float z = BZ + 0.05f, cx = (BX0 + BX1) / 2;
        boolean blink = ((int) (s.time / 10)) % 2 == 0;
        switch (v.phase) {
            case 0 -> {
                out.text(p, I18n.get("heralizcasino.pfc.titre_1"), cx, 32.3f, z, 0.9f, LED_AMBER, 0.95f, DrawList.T_CENTER);
                out.text(p, I18n.get("heralizcasino.pfc.titre_2"), cx, 30.6f, z, 0.9f, LED_AMBER, 0.95f, DrawList.T_CENTER);
                if (blink) out.text(p, I18n.get("heralizcasino.pfc.defi"), cx, 27.8f, z, 1.4f, LED_GREEN, 1f, DrawList.T_CENTER);
            }
            case 1 -> {
                out.text(p, I18n.get("heralizcasino.pfc.defi_ouvert"), cx, 32.4f, z, 0.9f, blink ? LED_GREEN : LED_AMBER, 1f, DrawList.T_CENTER);
                out.text(p, v.nameA, cx, 30.4f, z, fit(v.nameA, 1.1f, 11.5f, false), LED_AMBER, 1f, DrawList.T_CENTER);
                String st = I18n.get("heralizcasino.pfc.jetons", fmt(v.stake));
                out.text(p, st, cx, 27.9f, z, fit(st, 1.3f, 11.5f, false), LED_RED, 1f, DrawList.T_CENTER);
            }
            default -> {
                int sa = v.scoreA, sb = v.scoreB;
                if (v.step == 1 && v.sinceReveal < Rps.T_RESULT) {
                    if (v.roundWinner == 1) sa = Math.max(0, sa - 1);
                    if (v.roundWinner == 2) sb = Math.max(0, sb - 1);
                }
                out.text(p, v.nameA, 2.3f, 32.6f, z, fit(v.nameA, 0.75f, 5.3f, false), LED_AMBER, 0.95f, 0);
                out.text(p, v.nameB, 13.7f, 32.6f, z, fit(v.nameB, 0.75f, 5.3f, false), LED_AMBER, 0.95f, DrawList.T_RIGHT);
                boolean flash = v.step == 1 && v.sinceReveal >= Rps.T_RESULT && v.sinceReveal < Rps.T_RESULT + 20 && blink;
                out.text(p, Integer.toString(sa), 5.2f, 29.6f, z, 2.4f, flash && v.roundWinner == 1 ? LED_GREEN : LED_RED, 1f, DrawList.T_CENTER);
                out.text(p, "-", cx, 29.6f, z, 1.6f, LED_RED, 0.8f, DrawList.T_CENTER);
                out.text(p, Integer.toString(sb), 10.8f, 29.6f, z, 2.4f, flash && v.roundWinner == 2 ? LED_GREEN : LED_RED, 1f, DrawList.T_CENTER);
                String line;
                int col = LED_AMBER;
                if (v.phase == 3) {
                    line = v.winner == 1 ? I18n.get("heralizcasino.pfc.gagne", v.nameA) : v.winner == 2 ? I18n.get("heralizcasino.pfc.gagne", v.nameB) : I18n.get("heralizcasino.pfc.annule");
                    col = blink ? LED_GREEN : LED_AMBER;
                } else if (v.step == 1) {
                    float t = v.sinceReveal;
                    line = t < Rps.T_FOU ? I18n.get("heralizcasino.pfc.chi") : t < Rps.T_MI ? I18n.get("heralizcasino.pfc.fou") : t < Rps.T_THROW ? I18n.get("heralizcasino.pfc.mi")
                            : I18n.get("heralizcasino.pfc.manche", String.valueOf(v.round));
                } else {
                    line = I18n.get("heralizcasino.pfc.manche", String.valueOf(v.round)) + "  " + (int) Math.ceil(v.countdown) + "s";
                    if (v.countdown <= 5 && blink) col = LED_RED;
                }
                out.text(p, line, cx, 27.2f, z, fit(line, 0.95f, 12f, false), col, 1f, DrawList.T_CENTER);
            }
        }
    }

    /** Revelation : CHI-FOU-MI, lancer des objets et duel sur la table. */
    private void reveal(Scene s, RpsView v, Pose p, DrawList out) {
        float t = v.sinceReveal;
        // ---- CHI / FOU / MI : grandes lettres au-dessus de la table
        String[] words = {I18n.get("heralizcasino.pfc.chi"), I18n.get("heralizcasino.pfc.fou"), I18n.get("heralizcasino.pfc.mi")};
        int[] at = {0, Rps.T_FOU, Rps.T_MI};
        for (int i = 0; i < 3; i++) {
            float k = t - at[i];
            if (k < 0 || k > 14) continue;
            float pop = Ease.outBack(Math.min(1f, k / 4f), 2.5f);
            float a = 1 - Ease.range(k, 8, 14);
            out.text(p, words[i], CX, 22.5f + k * 0.1f, CZ, fit(words[i], 3.2f, 20f, true) * pop, i == 2 ? GOLD : 0xFFFFFF, a, DrawList.T_CENTER | DrawList.T_BILLBOARD | DrawList.T_OUTLINE, 0x000000);
        }
        if (t < Rps.T_THROW) return;
        int a = v.shownA, b = v.shownB;
        int win = v.roundWinner;
        // positions de repos (avant duel) et vol depuis la main
        float fly = Ease.range(t, Rps.T_THROW, Rps.T_LAND);
        float[] pa = arc(HAND_A, REST_A, fly), pb = arc(HAND_B, REST_B, fly);
        float spinA = (1 - fly) * 540, spinB = -(1 - fly) * 540;
        float clash = t - Rps.T_CLASH;
        // ---- duel des objets
        if (a >= 0 && b >= 0 && clash >= 0) {
            if (a == b) {
                // egalite : les deux objets se cognent et reculent
                float k = Ease.bell(clash, 0, 16) * 2.2f;
                pa = new float[]{pa[0] + k, pa[1], pa[2]};
                pb = new float[]{pb[0] - k, pb[1], pb[2]};
                drawAt(p, out, a, pa, 0, 20, 0);
                drawAt(p, out, b, pb, 180, 20, 0);
                if (clash > 6 && clash < 12) out.billboard(p, Sprite.FLARE, CX, TOP_Y + 1, CZ, 3f, s.time * 4, 0xFFFFFF, 0.5f);
            } else {
                int winner = win == 1 ? a : b;
                float[] wp = win == 1 ? pa : pb, lp = win == 1 ? pb : pa;
                float wrot = win == 1 ? 0 : 180;
                duel(s, p, out, winner, wp, lp, wrot, clash, t);
            }
        } else {
            if (a >= 0) drawAt(p, out, a, pa, spinA, 22, winGlow(v, 1, t));
            if (b >= 0) drawAt(p, out, b, pb, 180 + spinB, 22, winGlow(v, 2, t));
        }
        // ---- texte du resultat
        if (t >= Rps.T_CLASH + 14) {
            float k = t - Rps.T_CLASH - 14;
            float alpha = Math.min(1f, k / 5f) * (1 - Ease.range(t, Rps.REVEAL_TICKS - 6, Rps.REVEAL_TICKS));
            String txt = resultText(a, b);
            int col = win == 0 ? 0xE8E8E8 : GOLD;
            out.text(p, txt, CX, 19.5f, CZ, fit(txt, 1.5f, 22f, true), col, alpha, DrawList.T_CENTER | DrawList.T_BILLBOARD | DrawList.T_OUTLINE | DrawList.T_BOLD, 0x000000);
        }
    }

    private static float winGlow(RpsView v, int side, float t) {
        return v.roundWinner == side && t > Rps.T_RESULT ? 0.4f + 0.3f * (float) Math.sin(t * 0.5f) : 0;
    }

    /** Texte du resultat d'une manche. */
    public static String resultText(int a, int b) {
        if (a < 0 && b < 0) return I18n.get("heralizcasino.pfc.personne");
        if (a < 0 || b < 0) return I18n.get("heralizcasino.pfc.trop_tard");
        if (a == b) return I18n.get("heralizcasino.pfc.egalite");
        int w = Rps.winner(a, b) == 1 ? a : b;
        return I18n.get(switch (w) {
            case Rps.ROCK -> "heralizcasino.pfc.pierre";
            case Rps.PAPER -> "heralizcasino.pfc.feuille";
            default -> "heralizcasino.pfc.ciseaux";
        });
    }

    /** Trajectoire en cloche de la main a la table. */
    private static float[] arc(float[] from, float[] to, float k) {
        float e = Ease.inOutSine(k);
        float x = from[0] + (to[0] - from[0]) * e, z = from[2] + (to[2] - from[2]) * e;
        float y = from[1] + (to[1] - from[1]) * k + 5f * (float) Math.sin(Math.PI * k);
        return new float[]{x, y, z};
    }

    private void drawAt(Pose p, DrawList out, int kind, float[] pos, float yaw, float open, float glow) {
        p.push().translate(pos[0], pos[1], pos[2]).rotY(yaw);
        if (kind == Rps.SCISSORS) p.translate(0, 0.15f, 0);
        drawObject(p, out, kind, open, glow);
        p.pop();
    }

    /** Animation du coup gagnant : wp / lp = positions du gagnant et du perdant. */
    private void duel(Scene s, Pose p, DrawList out, int winner, float[] wp, float[] lp, float wrot, float c, float t) {
        float glow = t > Rps.T_RESULT ? 0.35f + 0.25f * (float) Math.sin(t * 0.5f) : 0;
        switch (winner) {
            case Rps.ROCK -> {
                // la pierre saute et retombe sur les ciseaux, qui volent en morceaux
                float up = Ease.range(c, 0, 8), down = Ease.range(c, 8, 11);
                float x = wp[0] + (lp[0] - wp[0]) * Ease.inOutSine(up);
                float y = TOP_Y + 6f * (float) Math.sin(Math.PI * 0.5 * up) * (1 - down) + (c > 11 ? Ease.spring(c - 11, 1.3f, 0.35f) * 0.25f : 0);
                float squash = c > 11 && c < 16 ? 1 - 0.25f * Ease.bell(c, 11, 16) : 1;
                p.push().translate(x, y, lp[2]).rotY(wrot + up * 90).scale(1 + (1 - squash) * 0.6f, squash, 1 + (1 - squash) * 0.6f);
                out.solid(p, rock, glow);
                p.pop();
                if (c < 11) {
                    drawAt(p, out, Rps.SCISSORS, lp, wrot + 180, 20, 0);
                } else {
                    // morceaux : chaque piece part dans sa direction, rebondit sur la table et s'efface
                    float k = c - 11;
                    Mesh[] parts = {bladeA, bladeB, ringA, ringB, screw};
                    float[][] vel = {{0.45f, 0.5f, 0.25f}, {0.35f, 0.42f, -0.4f}, {-0.4f, 0.55f, 0.3f}, {-0.3f, 0.35f, -0.45f}, {0.05f, 0.7f, 0.05f}};
                    float fade = 1 - Ease.range(t, Rps.REVEAL_TICKS - 20, Rps.REVEAL_TICKS - 4);
                    if (fade <= 0) return;
                    float spread = 6f * (1 - (float) Math.exp(-k / 7f));
                    for (int i = 0; i < parts.length; i++) {
                        float[] vv = vel[i];
                        float px = lp[0] + vv[0] * spread, pz = lp[2] + vv[2] * spread;
                        float py = TOP_Y + Math.max(0, vv[1] * k * 0.9f - 0.06f * k * k) + 0.1f;
                        p.push().translate(px, py, pz).rotY(wrot + i * 70 + k * 12 * (i % 2 == 0 ? 1 : -1)).rotX(k * 20 * (1 - Ease.range(k, 0, 18))).scale(fade);
                        out.solid(p, parts[i], 0);
                        p.pop();
                    }
                }
            }
            case Rps.PAPER -> {
                // la feuille s'envole, plane au-dessus de la pierre et la recouvre
                drawAt(p, out, Rps.ROCK, lp, wrot + 180, 0, 0);
                float lift = Ease.range(c, 0, 8), glide = Ease.range(c, 6, 14), drape = Ease.range(c, 12, 26);
                float x = wp[0] + (lp[0] - wp[0]) * Ease.inOutSine(glide), z = wp[2] + (lp[2] - wp[2]) * Ease.inOutSine(glide);
                float hover = TOP_Y + 0.1f + 3.4f * (float) Math.sin(Math.PI * 0.5 * lift) * (1 - Ease.smooth(drape));
                float sway = (1 - drape) * 12 * (float) Math.sin(c * 0.5f);
                p.push().translate(x, drape > 0 ? TOP_Y : hover, z).rotY(wrot + sway);
                if (drape <= 0) {
                    p.push().rotZ(sway * 0.6f);
                    out.solid(p, paperFlat, glow);
                    p.pop();
                } else {
                    out.solid(p, drapedPaper(Ease.outCubic(drape), hover - TOP_Y), glow);
                }
                p.pop();
            }
            default -> {
                // les ciseaux s'approchent, coupent trois fois et la feuille se separe en deux
                float approach = Ease.range(c, 0, 8);
                float cross = Ease.range(c, 8, 26);
                float x = wp[0] + (lp[0] - 1.6f * Math.signum(lp[0] - wp[0]) - wp[0]) * Ease.inOutSine(approach);
                x += (lp[0] - x + 1.8f * Math.signum(lp[0] - wp[0])) * cross;
                float snip = c > 8 && c < 28 ? 35f * Math.abs((float) Math.sin((c - 8) * Math.PI / 6.5)) : 25;
                float yaw = lp[0] > wp[0] ? 0 : 180;
                p.push().translate(x, TOP_Y + 0.45f, lp[2]).rotY(yaw);
                drawObject(p, out, Rps.SCISSORS, snip, glow);
                p.pop();
                float split = Ease.range(c, 24, 40);
                float fall = Ease.range(c, 30, 48);
                float fade = 1 - Ease.range(t, Rps.REVEAL_TICKS - 18, Rps.REVEAL_TICKS - 4);
                if (fade <= 0) return;
                for (int h = -1; h <= 1; h += 2) {
                    p.push().translate(lp[0], TOP_Y + 0.05f + fall * (h > 0 ? 0.2f : 0.1f), lp[2] + h * split * 1.4f).rotY(wrot + 90).rotX(h * split * 18).scale(fade);
                    out.solid(p, h < 0 ? paperHalfL : paperHalfR, 0);
                    p.pop();
                }
            }
        }
    }

    /** Feuille qui epouse la pierre (0 = a plat en hauteur, 1 = posee sur la pierre). */
    private Mesh drapedPaper(float k, float hover) {
        MeshBuilder b = new MeshBuilder();
        final float h0 = Math.max(0, hover);
        b.surface((u, w) -> {
            float x = (u - 0.5f) * 3.4f, z = (w - 0.5f) * 2.9f;
            float r = (float) Math.sqrt(x * x + z * z);
            float bump = 2.35f * (float) Math.exp(-(r * r) / 1.9f) + 0.06f;
            float y = h0 + (bump - h0) * k;
            float pull = 1 - 0.16f * k * Ease.smooth(Math.min(1f, r / 1.7f));
            return new float[]{x * pull, y, z * pull};
        }, 10, 9, PAPER, 1, 1, false, MeshBuilder.Orient.KEEP);
        b.surface((u, w) -> {
            float x = (u - 0.5f) * 3.4f, z = (w - 0.5f) * 2.9f;
            float r = (float) Math.sqrt(x * x + z * z);
            float bump = 2.35f * (float) Math.exp(-(r * r) / 1.9f) + 0.06f;
            float y = h0 + (bump - h0) * k - 0.03f;
            float pull = 1 - 0.16f * k * Ease.smooth(Math.min(1f, r / 1.7f));
            return new float[]{x * pull, y, z * pull};
        }, 10, 9, PAPER, 1, 1, false, MeshBuilder.Orient.FLIP);
        return b.buildDynamic();
    }

    // =================================================================== camera
    @Override
    public void camera(RpsView v, Scene s, int mode, Shot out) {
        if (v.mySide != 0 && v.phase == 2) {
            if (v.step == 1 && v.sinceReveal >= Rps.T_THROW - 4) {
                out.set(CX, 26f, 29f, CX, TOP_Y + 2, CZ, 46);
            } else if (v.step == 1) {
                out.set(CX, 27, 46, CX, 18, CZ, 50);
            } else if (v.mySide == 1) {
                // par-dessus l'epaule droite, assez loin pour voir l'adversaire et la table
                out.set(-34, 37, 24, 14, 17, 8, 55);
            } else {
                // meme cote que le public (le tableau lumineux est au fond) : on voit la table et l'adversaire
                out.set(50, 37, 24, 2, 17, 8, 55);
            }
            return;
        }
        if (v.phase == 3 && v.step == 1 && v.sinceReveal < Rps.REVEAL_TICKS) {
            out.set(CX, 26f, 29f, CX, TOP_Y + 2, CZ, 46);
            return;
        }
        out.set(CX, 31, 60, CX, 18, CZ, 55);
    }

    /** Le joueur local se voit (camera derriere lui ou de cote) pendant le duel. */
    @Override
    public boolean showsLocalPlayer(RpsView v, Scene s) {
        return v.mySide != 0 && (v.phase == 2 || v.phase == 3);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, RpsView v, FxSink fx, Random r) {
        // mouches autour de la table et fumee de la lampe
        if (r.nextInt(60) == 0) fx.particle(FxSink.FLY, CX + rnd(r, 5), TOP_Y + 3 + r.nextFloat() * 4, CZ + rnd(r, 5), 0, 0, 0, 0x202020, 0.35f, 60);
        if (r.nextInt(14) == 0) fx.particle(FxSink.SMOKE, LAMP[0], LAMP[1] + 3.2f, LAMP[2], rnd(r, 0.01f), 0.04f, rnd(r, 0.01f), 0x303030, 0.8f, 40);
        if (!(v.phase >= 2 && v.step == 1)) return;
        float t = v.sinceReveal;
        if (t > Rps.REVEAL_TICKS + 10) return;
        if (at(t, 0) || at(t, Rps.T_FOU) || at(t, Rps.T_MI)) fx.sound(Snd.CHANT, CX, 24, CZ, 1f, at(t, Rps.T_MI) ? 1.2f : 0.9f);
        if (at(t, Rps.T_SHOW)) fx.sound(Snd.WHOOSH, CX, 20, CZ, 0.6f, 1.3f);
        if (at(t, Rps.T_THROW)) fx.sound(Snd.WHOOSH, CX, 18, CZ, 0.8f, 0.9f);
        if (at(t, Rps.T_LAND)) {
            fx.sound(Snd.DICE_HIT, CX, TOP_Y, CZ, 0.8f, 0.8f);
            for (int i = 0; i < 6; i++) fx.particle(FxSink.SMOKE, CX + rnd(r, 4), TOP_Y + 0.3f, CZ + rnd(r, 2), rnd(r, 0.05f), 0.02f, rnd(r, 0.05f), 0x6A625A, 0.7f, 18);
        }
        int a = v.shownA, b = v.shownB;
        if (a < 0 || b < 0) return;
        float c = t - Rps.T_CLASH;
        int winner = v.roundWinner == 1 ? a : v.roundWinner == 2 ? b : -1;
        float[] lp = v.roundWinner == 1 ? REST_B : REST_A;
        if (a == b) {
            if (at(c, 8)) {
                fx.sound(Snd.BALL_CLACK, CX, TOP_Y, CZ, 1f, 0.7f);
                burst(fx, r, CX, TOP_Y + 1, CZ, 0xFFFFFF, 0xFFD36A, 8, 0.4f, 0.5f);
            }
        } else if (winner == Rps.ROCK) {
            if (at(c, 11)) {
                fx.sound(Snd.SMASH, lp[0], TOP_Y, lp[2], 1f, 1f);
                burst(fx, r, lp[0], TOP_Y + 0.6f, lp[2], 0xD8D8E0, 0xFFD36A, 16, 0.6f, 0.45f);
                for (int i = 0; i < 10; i++) {
                    fx.particle(FxSink.SMOKE, lp[0] + rnd(r, 1.5f), TOP_Y + 0.5f, lp[2] + rnd(r, 1.5f), rnd(r, 0.08f), 0.04f, rnd(r, 0.08f), 0x8A8278, 0.9f, 22);
                    fx.particle(FxSink.SHARD, lp[0], TOP_Y + 1, lp[2], rnd(r, 0.25f), 0.25f + r.nextFloat() * 0.2f, rnd(r, 0.25f), 0xB0B4BC, 0.35f, 26);
                }
            }
        } else if (winner == Rps.PAPER) {
            if (at(c, 1) || at(c, 14)) fx.sound(Snd.RUSTLE, lp[0], TOP_Y + 2, lp[2], 1f, at(c, 1) ? 1.3f : 0.9f);
            if (at(c, 24)) fx.sound(Snd.RUSTLE, lp[0], TOP_Y + 1, lp[2], 0.8f, 0.7f);
        } else if (winner == Rps.SCISSORS) {
            for (int k = 0; k < 3; k++) {
                if (at(c, 11 + k * 6.5f)) {
                    fx.sound(Snd.SNIP, lp[0], TOP_Y + 1, lp[2], 1f, 1.1f + k * 0.1f);
                    fx.particle(FxSink.PAPER, lp[0] + rnd(r, 0.8f), TOP_Y + 0.6f, lp[2], rnd(r, 0.08f), 0.12f, rnd(r, 0.08f), 0xE8E2D0, 0.35f, 30);
                }
            }
            if (at(c, 26)) {
                fx.sound(Snd.RUSTLE, lp[0], TOP_Y + 1, lp[2], 1f, 1.2f);
                for (int i = 0; i < 6; i++) {
                    fx.particle(FxSink.PAPER, lp[0] + rnd(r, 1f), TOP_Y + 0.8f, lp[2] + rnd(r, 1f), rnd(r, 0.12f), 0.18f, rnd(r, 0.12f), 0xE8E2D0, 0.3f, 40);
                }
            }
        }
        if (at(t, Rps.T_RESULT) && v.roundWinner != 0) fx.sound(Snd.WIN_SMALL, CX, 22, CZ, 0.7f, 1.1f);
        if (v.phase == 3 && at(v.sincePhase, 2) && v.winner != 3) {
            fx.sound(Snd.WIN_BIG, CX, 24, CZ, 1f, 1f);
            fountain(fx, r, CX, 28, CZ, FxSink.CONFETTI, new int[]{0xFFD36A, 0xFF5A2E, 0x7CFF6A}, 30, 0.35f);
        }
    }

    private static boolean at(float t, float k) {
        return t >= k && t < k + 1;
    }

    // =================================================================== vues
    @Override
    public RpsView newView() {
        return new RpsView();
    }

    @Override
    public RpsView demoView(float t) {
        RpsView v = new RpsView();
        v.phase = 2;
        v.step = 1;
        v.round = 2;
        v.scoreA = 1;
        v.scoreB = 1;
        v.nameA = "Logan";
        v.nameB = "Rival";
        // apercu hors-jeu : -Drpsdemo=0..3 (pierre/ciseaux, feuille/pierre, ciseaux/feuille, egalite)
        int demo = Integer.getInteger("rpsdemo", 0);
        int[][] cases = {{Rps.ROCK, Rps.SCISSORS}, {Rps.ROCK, Rps.PAPER}, {Rps.SCISSORS, Rps.PAPER}, {Rps.PAPER, Rps.PAPER}};
        v.shownA = cases[demo % 4][0];
        v.shownB = cases[demo % 4][1];
        v.roundWinner = Rps.winner(v.shownA, v.shownB);
        v.sinceReveal = t;
        return v;
    }

    @Override
    public int demoLength() {
        return Rps.REVEAL_TICKS;
    }
}
