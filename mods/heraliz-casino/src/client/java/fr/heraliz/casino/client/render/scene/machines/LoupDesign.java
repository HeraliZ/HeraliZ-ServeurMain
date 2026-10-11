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
import fr.heraliz.casino.game.slot.Loup;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.Suspense;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * "Souffle du Loup" (Huff N' Even More Puff), montage de ferme : un televiseur en planches de grange pose sur une caisse
 * de pommes, des bottes de paille et un fut d'eau, coiffe de la ROUE des bonus (lanternes, tuyau de poele qui fume) ; le
 * titre est affiche en haut de l'ecran. Ecran 5 x 3 : cartes, cochons, LOUP joker, CHAPEAUX, SCIES bleues et SCIE D'OR.
 * <p>
 * Bonus : cadres de paille, de bois puis de brique poses par les chapeaux (ou par les scies qui traversent la grille,
 * ou par les mega chapeaux), qui deviennent des maisons que le loup souffle pour reveler leurs gains. Pour les bonus
 * SUPER, le televiseur s'allonge : son haut, le titre et la roue montent, et trois rangees de plus apparaissent au-dessus
 * des rouleaux (grille 5 x 6).
 */
public final class LoupDesign extends MachineDesign<LoupView> {
    // ---- atlas : partage avec tools/textures (PaintLoup) ; symboles en cases de 128
    public static final int BLUR_Y = 256;
    public static final int[] R_WOLF = {0, 128, 128, 128};
    public static final int[] R_HAT = {128, 128, 128, 128};
    public static final int[] R_SAW = {256, 128, 128, 128};
    /** Maisons : paille, bois, brique (3 cases a partir de x = 384). */
    public static final int[] R_HOUSE = {384, 128, 128, 128};
    /** Symbole SCIE D'OR. */
    public static final int[] R_GOLD = {768, 128, 128, 128};
    /** Chapeau detoure qui tombe sur les cadres pendant les tours gratuits. */
    public static final int[] R_HAT_SPRITE = {896, 128, 128, 128};
    public static final int[] R_EMPTY = {128, 384, 128, 128};
    public static final int[] R_WIND = {256, 384, 256, 128};
    public static final int[] R_PLATE = {512, 384, 128, 128};
    public static final int[] R_FLASH = {640, 384, 128, 128};
    public static final int[] R_LED = {768, 384, 64, 64};
    public static final int[] R_DARK = {832, 384, 128, 64};
    public static final int[] R_TRIM = {960, 384, 64, 64};
    public static final int[] R_GLASS = {768, 448, 128, 64};
    public static final int[] R_STEEL = {896, 448, 128, 64};
    public static final int[] R_WOLF_A = {0, 512, 256, 256};
    public static final int[] R_WOLF_B = {256, 512, 256, 256};
    public static final int[] R_WOOD = {512, 512, 256, 256};
    public static final int[] R_STRAW = {768, 512, 256, 128};
    public static final int[] R_BRICK = {768, 640, 256, 128};
    public static final int[] R_TOPPER = {0, 768, 512, 256};
    public static final int[] R_PANEL = {512, 768, 256, 128};
    public static final int[] R_BAR = {512, 896, 256, 128};
    public static final int[] R_BTN_RED = {768, 768, 128, 128};
    public static final int[] R_BTN_YEL = {896, 768, 128, 128};
    public static final int[] R_SEAT = {768, 896, 128, 128};
    public static final int[] R_SCREEN = {896, 896, 128, 128};

    /**
     * Feuille des bonus (souffle_loup_cadres.png, 1024 x 1024, peinte par tools/textures/PaintLoupCadres) : cadres de
     * paille / bois / brique, lames de scie bleue et d'or, mega chapeau, plaque SUPER, echafaudage des rangees du haut.
     */
    public static final String SHEET = "cadres";
    public static final int[] C_FRAME = {0, 0, 256, 256};
    public static final int[] C_SAW = {768, 0, 256, 256};
    public static final int[] C_GOLD_SAW = {0, 256, 256, 256};
    public static final int[] C_MEGA = {256, 256, 512, 512};
    public static final int[] C_SUPER = {0, 768, 512, 128};
    public static final int[] C_EXT = {512, 768, 512, 256};

    static final Mat WOOD = FruitsDesign.tri(R_WOOD, 16), STRAW = FruitsDesign.tri(R_STRAW, 8), BRICK = FruitsDesign.tri(R_BRICK, 8);
    static final Mat TRIM = FruitsDesign.tri(R_TRIM, 4), STEEL = FruitsDesign.tri(R_STEEL, 6), DARK = FruitsDesign.tri(R_DARK, 6);
    static final Mat TOPPER = FruitsDesign.fit(R_TOPPER), PANEL = FruitsDesign.fit(R_PANEL), BAR = FruitsDesign.fit(R_BAR);
    static final Mat SCREEN = FruitsDesign.fit(R_SCREEN), GLASS = FruitsDesign.fit(R_GLASS), LED = FruitsDesign.fit(R_LED);
    static final Mat BTN_RED = FruitsDesign.fit(R_BTN_RED), BTN_YEL = FruitsDesign.fit(R_BTN_YEL), SEAT = FruitsDesign.fit(R_SEAT);
    static final Mat WIND = FruitsDesign.fit(R_WIND), PLATE = FruitsDesign.fit(R_PLATE), FLASH = FruitsDesign.fit(R_FLASH);
    static final Mat EMPTY_M = FruitsDesign.fit(R_EMPTY), WOLF_A = FruitsDesign.fit(R_WOLF_A), WOLF_B = FruitsDesign.fit(R_WOLF_B);
    static final Mat HAT_SPRITE = FruitsDesign.fit(R_HAT_SPRITE);
    static final Mat SAW_M = FruitsDesign.fit(C_SAW), GOLD_SAW_M = FruitsDesign.fit(C_GOLD_SAW), MEGA_M = FruitsDesign.fit(C_MEGA);
    static final Mat SUPER_M = FruitsDesign.fit(C_SUPER), EXT_M = FruitsDesign.fit(C_EXT);

    /**
     * La roue a sa propre feuille de texture (souffle_loup_roue.png, 2048 x 1024) : la roue a gauche, la super roue a
     * droite, peintes par tools/textures/PaintLoupWheel. WHEEL_FILL : part du carre occupee par le disque.
     */
    public static final String WHEEL_SHEET = "roue";
    public static final float WHEEL_FILL = 0.985f;
    static final Mat WHEEL_A = Mat.fit(0, 0, 512, 1024), WHEEL_B = Mat.fit(512, 0, 512, 1024);

    /** Symbole des rouleaux (net ou flou). */
    static Mat symbol(int s, boolean blur) {
        if (s <= Loup.WOLF && blur) return Mat.fit((s % 8) * 128, BLUR_Y + (s / 8) * 128, 128, 128);
        if (s < 8) return Mat.fit(s * 128, 0, 128, 128);
        if (s == Loup.WOLF) return FruitsDesign.fit(R_WOLF);
        if (s == Loup.HAT) return FruitsDesign.fit(R_HAT);
        if (s == Loup.SAW) return FruitsDesign.fit(R_SAW);
        if (s == Loup.GOLD) return FruitsDesign.fit(R_GOLD);
        return EMPTY_M;
    }

    /** Maison d'un etage : 1 paille, 2 bois, 3 brique. */
    static Mat house(int tier) {
        return Mat.fit(R_HOUSE[0] + (Math.max(1, Math.min(3, tier)) - 1) * 128, R_HOUSE[1], 128, 128);
    }

    /** Cadre d'un etage (feuille des bonus). */
    static Mat frame(int tier) {
        return Mat.fit(C_FRAME[0] + (Math.max(1, Math.min(3, tier)) - 1) * 256, C_FRAME[1], 256, 256);
    }

    // ---- cotes (px)
    static final float FZ = 14.2f, SZ = 13.65f;
    static final float WX0 = 1.9f, WX1 = 14.1f, WY0 = 18.6f, WY1 = 31.4f;
    static final float RX0 = 2.3f, RX1 = 13.7f, RY0 = 21.0f, RY1 = 28.2f;
    static final float CW = (RX1 - RX0) / Loup.REELS, CH = (RY1 - RY0) / Loup.ROWS;
    /** Grille agrandie : trois rangees de plus ; tout ce qui est au-dessus de SPLIT (haut de la fenetre) monte d'autant. */
    static final float EXT_H = Loup.TOP * CH, SPLIT = WY1;
    /** Agrandissement : la borne commence a monter, puis a fini de monter (ticks depuis le debut de la mise en place). */
    static final int EXP_GO = 12, EXP_UP = 62;
    static final float[] SPIN_BTN = {12.1f, 16.7f, 15.3f}, BONUS_BTN = {3.8f, 16.7f, 15.3f};
    /** Lanternes aux coins du dessus du televiseur. */
    static final float[] LAMPS = {1.6f, SlotRig.TV_Y1 + 2.4f, 14.6f, 14.4f, SlotRig.TV_Y1 + 2.4f, 14.6f};
    /** La roue au-dessus de la borne (sur le televiseur) : centre et rayon. */
    static final float WHX = 8, WHY = SlotRig.TV_Y1 + 6.1f, WHZ = 14.95f, WHR = 4.8f;
    /** Titre affiche en haut de l'ecran (il monte avec le haut de la borne) : centre ; les annonces de la roue s'y deplient. */
    static final float TITLE_Y = (WY1 + SlotRig.PY1) / 2;
    /** Chevilles de la jante (une par separation) que le cliquet du pointeur accroche : rayon ; axe du cliquet, longueur utile. */
    static final float PEG_R = WHR * 0.953f, FLAP_Y = WHR + 0.85f, FLAP_LEN = FLAP_Y - PEG_R;
    /** Le cliquet est pousse par une cheville de FLAP_IN degres avant le sommet jusqu'a FLAP_OUT degres apres, puis lache. */
    static final float FLAP_IN = -2.5f, FLAP_OUT = 4f, FLAP_HALF = 0.2f;
    static final int AMBER = 0xFFB040, RED = 0xFF4A2A, GOLD = 0xFFD36A, WHITE = 0xF6F0E4, GREEN = 0x9CFF3A, PURPLE = 0xC08AFF, BLUE = 0x6AC8FF;
    static final String[] JACKPOTS = {"MINI", "MINOR", "MAJOR", "GRAND", "SUPER"};
    static final int[] JACKPOT_COLORS = {0x6AD8FF, 0xC08AFF, 0xFF6A4A, 0xFFD36A, 0xFF8AE8};
    static final int[] TIER_COLORS = {0xF6F0E4, 0xFFE890, 0xE0B080, 0xFF9A7A};
    /** Cles des niveaux des cadres (traduits a l'affichage). */
    static final String[] TIERS = {"", "heralizcasino.loup.paille", "heralizcasino.loup.bois", "heralizcasino.loup.brique"};
    static final int[] IDLE = {0, 5, 3, 1, 9, 4, 8, 2, 6, 10, 0, 1, 3, 9, 5};

    private Mesh body = Mesh.EMPTY, kit = Mesh.EMPTY, top = Mesh.EMPTY, ext = Mesh.EMPTY, topperGlow = Mesh.EMPTY, bodyLights = Mesh.EMPTY;
    private Mesh spinBtn = Mesh.EMPTY, bonusBtn = Mesh.EMPTY, lamps = Mesh.EMPTY;
    private Mesh wheel = Mesh.EMPTY, superWheel = Mesh.EMPTY, wheelFlat = Mesh.EMPTY, pegs = Mesh.EMPTY, wedge = Mesh.EMPTY, pointer = Mesh.EMPTY, bulb = Mesh.EMPTY;
    private Mesh captionPlate = Mesh.EMPTY, captionFrame = Mesh.EMPTY, veil = Mesh.EMPTY;

    public LoupDesign() {
        super("souffle_loup", AMBER, RED);
        labelY = 54;
        itemScale = 0.33f;
    }

    // =================================================================== roue
    /** Couleur d'une case de la roue. */
    static int segColor(int code) {
        return switch (code) {
            case Loup.J_MINI -> 0x2A7AE8;
            case Loup.J_MINOR -> 0x9A3AE8;
            case Loup.J_MAJOR -> 0xE8302A;
            case Loup.J_GRAND -> 0xF0B020;
            case Loup.J_SUPER -> 0xFF4AC8;
            case Loup.W_BUZZ, Loup.S_BUZZ -> 0x2A9AE8;
            case Loup.W_MEGA, Loup.S_MEGA -> 0xF08A1A;
            case Loup.W_MANSION, Loup.S_MANSION -> 0x2AB04A;
            default -> 0xE83AA8;
        };
    }

    /** Nom d'un bonus. */
    static String featureName(int bonus) {
        return I18n.get(switch (bonus) {
            case Loup.B_BUZZ -> "heralizcasino.loup.scies";
            case Loup.B_MEGA -> "heralizcasino.loup.mega_chapeau";
            case Loup.B_MANSION -> "heralizcasino.loup.manoir";
            case Loup.B_SUPER_BUZZ -> "heralizcasino.loup.super_scies";
            case Loup.B_SUPER_MEGA -> "heralizcasino.loup.super_mega_chapeau";
            case Loup.B_SUPER_MANSION -> "heralizcasino.loup.super_manoir";
            default -> "heralizcasino.slot.tours_gratuits";
        });
    }

    /** Annonce de la case gagnante de la roue. */
    static String segTitle(int code) {
        if (Loup.isJackpot(code)) return I18n.get("heralizcasino.loup.jackpot_annonce", JACKPOTS[code]);
        if (code == Loup.W_UPGRADE) return I18n.get("heralizcasino.loup.super_roue");
        return I18n.get("heralizcasino.slot.annonce", featureName(Loup.featureOf(code)));
    }

    /** Point de la roue a l'angle "deg" (sens horaire depuis le haut), rayon r. */
    private static float[] wp(float deg, float r, float z) {
        double a = Math.toRadians(deg);
        return p((float) Math.sin(a) * r, (float) Math.cos(a) * r, z);
    }

    /** Rayons (fractions de WHR) et reliefs du disque peint : moyeu bombe, quartiers a plat, jante en leger relief. */
    private static final float[] FACE_R = {0, 0.164f, 0.2f, 0.9f, 0.925f, 1f}, FACE_Z = {0.34f, 0.34f, 0.06f, 0, 0.13f, 0.13f};

    /**
     * Disque de la roue, peint dans la feuille "roue" : les coordonnees de texture suivent la position (x, y) du point,
     * le relief (moyeu, jante) ne deforme donc pas l'image.
     */
    private static Mesh wheelFace(Mat m, int segs) {
        MeshBuilder b = new MeshBuilder();
        float k = WHEEL_FILL / 2;
        for (int ring = 0; ring + 1 < FACE_R.length; ring++) {
            float r0 = FACE_R[ring] * WHR, r1 = FACE_R[ring + 1] * WHR, z0 = FACE_Z[ring], z1 = FACE_Z[ring + 1];
            float slope = (z1 - z0) / Math.max(1e-3f, r1 - r0);
            for (int i = 0; i < segs; i++) {
                float a0 = i * 360f / segs, a1 = (i + 1) * 360f / segs;
                float[][] pts = ring == 0 ? new float[][]{p(0, 0, z0), wp(a1, r1, z1), wp(a0, r1, z1)}
                        : new float[][]{wp(a0, r0, z0), wp(a1, r0, z0), wp(a1, r1, z1), wp(a0, r1, z1)};
                float[][] uv = new float[pts.length][];
                for (int q = 0; q < pts.length; q++) uv[q] = new float[]{m.u(0.5f + pts[q][0] / WHR * k), m.v(0.5f - pts[q][1] / WHR * k)};
                double am = Math.toRadians((a0 + a1) / 2);
                float[] n = v((float) (-Math.sin(am) * slope), (float) (-Math.cos(am) * slope), 1);
                b.poly(pts, uv, null, n);
            }
        }
        return b.build();
    }

    /** Pieces en relief qui tournent avec la roue : les douze chevilles dorees de la jante. */
    private static Mesh wheelPegs() {
        MeshBuilder b = new MeshBuilder();
        b.color(1f, 0.82f, 0.34f);
        for (int i = 0; i < 12; i++) {
            float[] q = wp(i * 30 + 15, PEG_R, 0);
            b.push().translate(q[0], q[1], 0.1f).rotateX(90);
            b.cylinder(0.13f, 0, 0.42f, 8, LED, null, null);
            b.push().translate(0, 0.42f, 0);
            b.sphereSection(0.13f, 8, 3, 0, 90, LED);
            b.pop();
            b.pop();
        }
        b.color(1);
        return b.build();
    }

    /** Roue simplifiee (aplats de couleur de l'atlas) pour l'objet d'inventaire, qui n'a pas acces a la feuille de la roue. */
    private static Mesh wheelFlat(int[] codes) {
        MeshBuilder b = new MeshBuilder();
        float[] nz = v(0, 0, 1);
        for (int i = 0; i < 12; i++) {
            int col = segColor(codes[i]);
            b.color(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f);
            for (int k = 0; k < 4; k++) {
                float a0 = i * 30 - 15 + k * 7.5f, a1 = a0 + 7.5f;
                b.face(LED, nz, p(0, 0, 0), wp(a0, WHR, 0), wp(a1, WHR, 0));
            }
            b.color(0.95f, 0.78f, 0.3f);
            float a = i * 30 - 15;
            b.face(LED, nz, wp(a - 0.8f, 0.8f, 0.02f), wp(a - 0.8f, WHR, 0.02f), wp(a + 0.8f, WHR, 0.02f), wp(a + 0.8f, 0.8f, 0.02f));
        }
        b.color(0.95f, 0.78f, 0.3f);
        b.push().rotateX(90);
        b.cylinder(0.9f, -0.2f, 0.25f, 12, LED, null, LED);
        b.pop();
        b.color(1);
        return b.build();
    }

    // =================================================================== construction
    /** Tuyau de poele sur le dessus du televiseur (x, z) ; il fume (repere : le haut de la borne). */
    static final float[] PIPE = {12.9f, 10.7f};
    static final float PIPE_TOP = SlotRig.TV_Y1 + 7.0f;

    @Override
    protected void build() {
        float[] nz = v(0, 0, 1);
        float x0 = SlotRig.TV_X0, x1 = SlotRig.TV_X1, fz = SlotRig.TV_FRONT;
        // ---- bas (fixe) : palette, bottes de paille, fut d'eau, planche de grange en travers, caisse de pommes,
        // televiseur en planches jusqu'au haut de la fenetre, pupitre (caisse), batterie
        MeshBuilder b = new MeshBuilder();
        MeshBuilder k = new MeshBuilder();
        BunkerKit.pallet(k, -0.3f, 0, 0.8f, 16.3f, 1.1f, 16.0f, 7, Recup.PALLET);
        b.bevelBox(-0.3f, 1.1f, 3.0f, 6.4f, 5.7f, 13.4f, 0.45f, STRAW);
        b.bevelBox(-0.1f, 5.7f, 3.4f, 6.2f, 10.4f, 13.0f, 0.45f, STRAW);
        SlotRig.drum(k, 12.7f, 1.1f, 7.8f, Recup.DRUM_BLUE, 90, seg(14));
        b.bevelBox(-0.2f, 10.4f, 2.6f, 16.2f, 11.1f, 14.6f, 0.1f, WOOD);
        SlotRig.crate(k, 2.4f, 11.1f, 4.2f, 13.6f, SlotRig.TV_Y0, 13.8f, Recup.APPLE);
        // televiseur de bois (planches de grange), facade blanche ; le haut part avec le haut de la borne
        b.bevelBox(x0, SlotRig.TV_Y0, SlotRig.TV_BACK, x1, SPLIT, FZ, 0.1f, WOOD, null, MeshBuilder.SKIP_PZ | MeshBuilder.SKIP_PY);
        b.bevelBox(2.2f, 18.0f, 6.0f, 13.8f, SPLIT, SlotRig.TV_BACK + 0.1f, 0.1f, WOOD, null, MeshBuilder.SKIP_PY);
        b.bevelBox(4.4f, 20.2f, 3.9f, 11.6f, 29.6f, 6.1f, 0.6f, WOOD);
        b.bevelBox(x0, SlotRig.TV_Y0, FZ - 0.05f, x1, WY0, fz, 0.25f, TRIM);
        b.bevelBox(x0, WY0, FZ - 0.05f, WX0, SPLIT, fz, 0.05f, TRIM, null, MeshBuilder.SKIP_PY);
        b.bevelBox(WX1, WY0, FZ - 0.05f, x1, SPLIT, fz, 0.05f, TRIM, null, MeshBuilder.SKIP_PY);
        SlotRig.embrasure(b, DARK, WY0, SPLIT, true, false);
        SlotRig.details(k, false);
        SlotRig.deck(k, Recup.WOOD, Recup.DARK);
        // batterie entre la paille et le fut, cables qui montent au pupitre (devant la planche)
        SlotRig.battery(k, 8.5f, 1.1f, 12.8f, -10);
        SlotRig.cable(k, new float[][]{p(7.3f, 4.2f, 12.4f), p(6.9f, 7.5f, 15.0f), p(6.6f, 11.3f, 15.1f), p(6.2f, 13.7f, 15.3f)}, true);
        SlotRig.cable(k, new float[][]{p(9.7f, 4.2f, 12.2f), p(10.0f, 8.0f, 15.2f), p(9.6f, 11.5f, 15.0f), p(9.4f, 13.7f, 15.4f)}, false);
        // fourche plantee dans la paille
        b.push().translate(1.4f, 10.4f, 11.8f).rotateZ(-14).rotateX(-10);
        b.cylinder(0.14f, 0, 8.6f, 5, WOOD, null, WOOD);
        b.bevelBox(-0.75f, 8.5f, -0.08f, 0.75f, 8.75f, 0.08f, 0.03f, STEEL);
        for (int i = -1; i <= 1; i++) b.bevelBox(i * 0.65f - 0.05f, 8.7f, -0.05f, i * 0.65f + 0.05f, 10.6f, 0.05f, 0.02f, STEEL);
        b.pop();
        // ---- tabouret : souche d'arbre
        b.push().translate(8, 0, 24);
        b.color(0.6f);
        b.cylinder(3.3f, 0, 0.6f, seg(16), WOOD, null, null);
        b.color(0.75f);
        b.cylinder(3.0f, 0.6f, 8.4f, seg(16), WOOD, null, null);
        b.color(1);
        b.push().translate(0, 8.4f, 0);
        b.disc(3.0f, 0, seg(16), SEAT, true);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.35f, 3f, 0.45f, 0f);
        Occlusion.bake(k, 0.35f, 3f, 0.45f, 0f);
        body = b.build();
        kit = k.build();

        // ---- haut de la borne (monte quand la grille s'agrandit) : haut du televiseur et son titre (affiche par
        // l'ecran), roue sur ses montants, lanternes, tuyau de poele
        float ty = SlotRig.TV_Y1, py = SlotRig.PY1;
        MeshBuilder t = new MeshBuilder();
        t.bevelBox(x0, SPLIT, SlotRig.TV_BACK, x1, ty, FZ, 0.1f, WOOD, null, MeshBuilder.SKIP_PZ | MeshBuilder.SKIP_NY);
        t.bevelBox(2.2f, SPLIT, 6.0f, 13.8f, py + 0.4f, SlotRig.TV_BACK + 0.1f, 0.1f, WOOD, null, MeshBuilder.SKIP_NY);
        t.bevelBox(x0, py, FZ - 0.05f, x1, ty, fz, 0.25f, TRIM);
        t.bevelBox(x0, WY1, FZ - 0.05f, WX0, py, fz, 0.05f, TRIM, null, MeshBuilder.SKIP_NY);
        t.bevelBox(WX1, WY1, FZ - 0.05f, x1, py, fz, 0.05f, TRIM, null, MeshBuilder.SKIP_NY);
        SlotRig.embrasure(t, DARK, WY1, py, false, false);
        SlotRig.embrasure(t, DARK, WY0, py, false, true);
        // la roue sur un caisson de planches pose sur le televiseur
        t.bevelBox(WHX - 3.2f, ty - 0.1f, WHZ - 2.6f, WHX + 3.2f, ty + 0.7f, WHZ - 0.6f, 0.12f, WOOD);
        t.push().translate(WHX, WHY, WHZ - 0.55f).rotateX(90);
        t.cylinder(WHR + 0.75f, -0.35f, 0.3f, seg(28), DARK, DARK, DARK);
        t.pop();
        t.push().translate(WHX, WHY, WHZ - 0.12f).rotateX(90);
        t.torus(WHR + 0.42f, 0.3f, seg(36), 6, 0, 360, STEEL);
        t.pop();
        for (int sd = -1; sd <= 1; sd += 2) {
            t.push().translate(WHX + sd * 1.9f, ty + 0.5f, WHZ - 1.1f).rotateZ(-sd * 14);
            t.bevelBox(-0.28f, 0, -0.3f, 0.28f, WHY - ty - 0.6f, 0.3f, 0.06f, STEEL);
            t.pop();
        }
        t.bevelBox(WHX - 0.5f, ty + 0.5f, WHZ - 1.4f, WHX + 0.5f, WHY, WHZ - 0.8f, 0.1f, STEEL);
        t.bevelBox(WHX - 0.35f, WHY + WHR + 0.2f, WHZ - 0.9f, WHX + 0.35f, WHY + FLAP_Y + 0.45f, WHZ + 0.2f, 0.08f, STEEL);
        for (int i = 0; i < 2; i++) {
            float x = LAMPS[i * 3], y = LAMPS[i * 3 + 1], z = LAMPS[i * 3 + 2];
            t.bevelBox(x - 0.12f, ty - 0.2f, z - 1.05f, x + 0.12f, y + 0.8f, z - 0.8f, 0.03f, STEEL);
            t.bevelBox(x - 0.1f, y + 0.6f, z - 0.9f, x + 0.1f, y + 0.8f, z, 0.03f, STEEL);
            t.push().translate(x, y - 0.6f, z);
            t.cylinder(0.45f, 0, 0.15f, 8, STEEL, STEEL, STEEL);
            t.cylinder(0.3f, 1.0f, 1.3f, 8, STEEL, STEEL, STEEL);
            t.pop();
        }
        t.push().translate(PIPE[0], SlotRig.TV_Y1, PIPE[1]);
        t.cylinder(0.55f, 0, PIPE_TOP - SlotRig.TV_Y1 - 0.4f, 8, STEEL, null, null);
        t.cylinder(0.78f, PIPE_TOP - SlotRig.TV_Y1 - 0.45f, PIPE_TOP - SlotRig.TV_Y1, 8, STEEL, STEEL, DARK);
        t.pop();
        Occlusion.bake(t, 0.35f, 3f, 0.45f, 0f);
        top = t.build();

        // ---- rallonge (hauteur EXT_H, etiree selon l'agrandissement) : caisson et tube du televiseur, montants de la
        // facade avec bandes de chantier, embrasure
        MeshBuilder e = new MeshBuilder();
        int skipYY = MeshBuilder.SKIP_NY | MeshBuilder.SKIP_PY;
        e.bevelBox(x0, 0, SlotRig.TV_BACK, x1, EXT_H, FZ, 0.02f, WOOD, null, skipYY | MeshBuilder.SKIP_PZ);
        e.bevelBox(2.2f, 0, 6.0f, 13.8f, EXT_H, SlotRig.TV_BACK + 0.1f, 0.02f, WOOD, null, skipYY);
        e.bevelBox(x0, 0, FZ - 0.05f, WX0, EXT_H, fz, 0.02f, TRIM, null, skipYY);
        e.bevelBox(WX1, 0, FZ - 0.05f, x1, EXT_H, fz, 0.02f, TRIM, null, skipYY);
        SlotRig.embrasure(e, DARK, 0, EXT_H, false, false);
        // bandes jaunes et noires du chantier sur les montants de la facade
        for (float x : new float[]{x0, WX1}) {
            for (int i = 0; i < 4; i++) {
                e.color(i % 2 == 0 ? 1f : 0.12f, i % 2 == 0 ? 0.78f : 0.1f, i % 2 == 0 ? 0.12f : 0.08f);
                float y0 = 0.6f + i * 1.6f, w = WX0 - x0;
                e.face(LED, nz, p(x + 0.05f, y0, fz + 0.01f), p(x + w - 0.05f, y0, fz + 0.01f), p(x + w - 0.05f, y0 + 1.6f, fz + 0.01f), p(x + 0.05f, y0 + 1.6f, fz + 0.01f));
            }
            e.color(1);
        }
        ext = e.build();

        // titre "SOUFFLE DU LOUP" affiche en haut de l'ecran (il monte avec le haut de la borne)
        MeshBuilder tg = new MeshBuilder();
        SlotRig.title(tg, TOPPER);
        topperGlow = tg.build();
        MeshBuilder bl = new MeshBuilder();
        SlotRig.led(bl, LED);
        bodyLights = bl.build();
        spinBtn = button(SPIN_BTN, 1.2f, BTN_RED);
        bonusBtn = button(BONUS_BTN, 1.0f, BTN_YEL);
        MeshBuilder lb = new MeshBuilder();
        for (int i = 0; i < 2; i++) {
            lb.push().translate(LAMPS[i * 3], LAMPS[i * 3 + 1] - 0.45f, LAMPS[i * 3 + 2]);
            lb.cylinder(0.32f, 0, 0.85f, 8, LED, null, null);
            lb.pop();
        }
        lamps = lb.build();
        wheel = wheelFace(WHEEL_A, seg(60));
        superWheel = wheelFace(WHEEL_B, seg(60));
        wheelFlat = wheelFlat(Loup.WHEEL);
        pegs = wheelPegs();
        // quartier gagnant : voile lumineux entre le moyeu et la jante
        MeshBuilder wg = new MeshBuilder();
        for (int i = 0; i < 4; i++) {
            float a0 = -15 + i * 7.5f;
            wg.face(LED, nz, wp(a0, WHR * 0.2f, 0.06f), wp(a0 + 7.5f, WHR * 0.2f, 0.06f), wp(a0 + 7.5f, WHR * 0.9f, 0.06f), wp(a0, WHR * 0.9f, 0.06f));
        }
        wedge = wg.build();
        // cliquet du pointeur (repere : son axe) : lame rouge bordee d'or, qui plie au passage des chevilles
        MeshBuilder pt = new MeshBuilder();
        pt.color(1f, 0.82f, 0.34f);
        pt.push().translate(0, 0, 0.12f);
        pt.prism(new float[][]{{-0.78f, 0.42f}, {0.78f, 0.42f}, {0, -FLAP_LEN - 0.62f}}, 0.22f, LED, LED);
        pt.pop();
        pt.color(0.9f, 0.16f, 0.12f);
        pt.push().translate(0, 0, 0.34f);
        pt.prism(new float[][]{{-0.5f, 0.24f}, {0.5f, 0.24f}, {0, -FLAP_LEN - 0.3f}}, 0.14f, LED, LED);
        pt.pop();
        pt.color(1f, 0.82f, 0.34f);
        pt.push().translate(0, 0, 0.1f).rotateX(90);
        pt.cylinder(0.3f, 0, 0.5f, 10, LED, null, LED);
        pt.pop();
        pt.color(1);
        pointer = pt.build();
        MeshBuilder bu = new MeshBuilder();
        bu.sphere(0.24f, 8, 5, LED);
        bulb = bu.build();
        MeshBuilder cp = new MeshBuilder();
        cp.bevelBox(-7.05f, -1.4f, -0.12f, 7.05f, 1.4f, 0.1f, 0.08f, DARK);
        captionPlate = cp.build();
        MeshBuilder cf = new MeshBuilder();
        float fw = 6.9f, fh = 1.25f, ft = 0.09f;
        for (float[] q : new float[][]{{-fw, -fh, fw, -fh + ft}, {-fw, fh - ft, fw, fh}, {-fw, -fh, -fw + ft, fh}, {fw - ft, -fh, fw, fh}}) {
            cf.face(LED, nz, p(q[0], q[1], 0.12f), p(q[2], q[1], 0.12f), p(q[2], q[3], 0.12f), p(q[0], q[3], 0.12f));
        }
        captionFrame = cf.build();
        MeshBuilder vl = new MeshBuilder();
        vl.face(DARK, nz, p(-0.5f, -0.5f, 0), p(0.5f, -0.5f, 0), p(0.5f, 0.5f, 0), p(-0.5f, 0.5f, 0));
        veil = vl.build();
    }

    private Mesh button(float[] at, float r, Mat face) {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, 15.5f, 14.8f).rotateX(10).translate(at[0] - 8, 1.4f, at[2] - 14.8f);
        b.cylinder(r + 0.25f, 0, 0.2f, seg(16), STEEL, null, STEEL);
        b.cylinder(r, 0.2f, 0.62f, seg(16), face, null, face);
        b.pop();
        return b.build();
    }

    // =================================================================== chronologie
    static int end(Loup.Spin s) {
        return Loup.duration(s) - ((s.events & Loup.EV_MAXWIN) != 0 ? Rotten.BIG_WIN_TICKS : 0);
    }

    /** Agrandissement de la grille (0 = 5 x 3, 1 = 5 x 6) a l'instant lt de l'etape i. */
    static float expansion(LoupView v, int i, float lt) {
        if (i < 0) return 0;
        Loup.Spin s = v.seq.spins.get(i);
        if (!Loup.isSuper(s.bonus) || (s.kind != Loup.SETUP && s.kind != Loup.FREE)) return 0;
        if (s.kind == Loup.SETUP && (s.events & Loup.EV_EXPAND) != 0) return Ease.inOutCubic(Ease.range(lt, EXP_GO, EXP_UP));
        if (s.kind == Loup.FREE && (s.events & Loup.EV_COLLAPSE) != 0) {
            int c = Loup.collapseStart(s);
            return 1 - Ease.inOutCubic(Ease.range(lt, c + 4, c + Loup.COLLAPSE_T - 6));
        }
        return 1;
    }

    /** Ticks depuis le debut d'une annonce avec la mascotte (gros gain des facons, jackpot, gain du bonus), -1 sinon. */
    static float bigAt(Loup.Spin s, float lt) {
        if (s == null) return -1;
        if (s.kind == Loup.BASE && Rotten.bigTier(s.lineMilli) > 0) {
            float t = lt - Loup.spinEnd(s);
            if (t >= 0 && t < Rotten.BIG_WIN_TICKS) return t;
        }
        if ((s.kind == Loup.BASE || s.kind == Loup.SPIN_WHEEL) && (s.events & Loup.EV_JACKPOT) != 0) {
            float t = lt - Loup.jackpotStart(s);
            if (t >= 0 && t < Loup.JACKPOT_T) return t * Rotten.BIG_WIN_TICKS / Loup.JACKPOT_T;
        }
        if (s.kind == Loup.FREE && (s.events & Loup.EV_BONUS_END) != 0 && Rotten.bigTier(s.winMilli) > 0) {
            float t = lt - Loup.endStart(s);
            if (t >= 0 && t < Rotten.BIG_WIN_TICKS) return t;
        }
        if ((s.events & Loup.EV_MAXWIN) != 0) {
            float m = lt - end(s);
            if (m >= 0 && m < Rotten.BIG_WIN_TICKS) return m;
        }
        return -1;
    }

    // =================================================================== la roue
    /**
     * Etat de la roue a un instant.
     * @param angle    rotation (degres, sens horaire)
     * @param sup      la super roue est affichee
     * @param spinning la roue tourne
     * @param speed    vitesse (degres par tick)
     * @param turn     lancer en cours : 0 la roue, 1 la super roue, -1 aucun
     * @param w        ticks depuis le debut du lancer en cours (negatif pendant la presentation de la super roue)
     * @param flip     retournement de la roue (0 = de face, 1 = de face apres un demi-tour)
     * @param present  presentation de la super roue : elle s'avance et grandit (0..1)
     * @param spin     etape dont c'est la roue (null au repos)
     */
    record Wheel(float angle, boolean sup, boolean spinning, float speed, int turn, float w, float flip, float present, Loup.Spin spin) {
        static final Wheel REST = new Wheel(0, false, false, 0, -1, 0, 0, 0, null);
    }

    static boolean hasWheel(Loup.Spin s) {
        return (s.kind == Loup.BASE || s.kind == Loup.SPIN_WHEEL) && (s.wheelSeg >= 0 || s.superSeg >= 0);
    }

    /**
     * Decalage d'arret de la roue dans le quartier gagnant (degres) : elle ne s'arrete jamais pile au milieu, et assez
     * souvent a un cheveu de la cheville suivante (le cliquet plie deja) ou juste apres celle qui vient de passer.
     */
    static float restOffset(Loup.Spin s, int turn) {
        int h = s.wheelSeg * 31 + s.superSeg * 17 + turn * 101 + 7 + s.kind * 13;
        for (int c = 0; c < Loup.CELLS; c++) h = h * 31 + s.grid[c];
        float a = Ease.hash(h), b = Ease.hash(h * 7 + 3);
        if (a < 0.3f) return 11f + 3.2f * b;
        if (a < 0.5f) return -9.5f + 2.5f * b;
        return -6f + 14f * b;
    }

    /** Rotation finale d'un lancer parti de "from" : quelques tours puis la case "seg". */
    static float endAngle(float from, int seg, float offset, int turns) {
        float target = -seg * 30 + offset;
        return from + 360 * turns + (((target - from) % 360) + 360) % 360;
    }

    /** Rotation d'un lancer au tick w : petit recul d'elan, grande rotation, long freinage qui finit au pas. */
    private static float spinAngle(float w, float from, float to) {
        float back = 14f;
        if (w < Loup.WHEEL_GO) return from - back * Ease.inOutSine(Ease.range(w, Loup.WHEEL_GO - 8, Loup.WHEEL_GO));
        float u = Ease.clamp01((w - Loup.WHEEL_GO) / (Loup.WHEEL_STOP - Loup.WHEEL_GO));
        return from - back + (to - from + back) * (1 - (float) Math.pow(1 - u, 3.2));
    }

    static Wheel wheelAt(LoupView v, int i, float lt) {
        if (v.seq.spins.isEmpty() || i < 0) return Wheel.REST;
        float angle = 0;
        boolean supFace = false;
        Loup.Spin last = null;
        for (int k = 0; k <= i && k < v.seq.spins.size(); k++) {
            Loup.Spin s = v.seq.spins.get(k);
            if (!hasWheel(s)) continue;
            float from = angle;
            boolean fromSup = supFace;
            float e1 = s.wheelSeg >= 0 ? endAngle(from, s.wheelSeg, restOffset(s, 0), 6) : from;
            float e2 = s.superSeg >= 0 ? endAngle(e1, s.superSeg, restOffset(s, 1), 5) : e1;
            if (k < i) {
                angle = e2;
                supFace = s.superSeg >= 0;
                last = s;
                continue;
            }
            float w = lt - Loup.wheelStart(s);
            if (w < 0) {
                // la roue d'une maison : la super roue d'avant se retourne en roue normale
                float flip = fromSup ? 1 - Ease.inOutCubic(Ease.range(lt, 4, 14)) : 0;
                return new Wheel(from, flip >= 0.5f, false, 0, -1, 0, flip, 0, last);
            }
            if (s.wheelSeg >= 0 && w < Loup.WHEEL_T) {
                float a = spinAngle(w, from, e1);
                float speed = spinAngle(w + 0.5f, from, e1) - spinAngle(w - 0.5f, from, e1);
                return new Wheel(a, false, w >= Loup.WHEEL_GO && w < Loup.WHEEL_STOP, speed, 0, w, 0, 0, s);
            }
            float sw = lt - Loup.superStart(s);
            if (s.superSeg >= 0 && sw >= 0 && sw < Loup.SUPER_INTRO + Loup.WHEEL_T) {
                // presentation : la roue se retourne, s'avance et grandit ; puis elle tourne
                float flip = Ease.inOutCubic(Ease.range(sw, 6, 18));
                float lw = sw - Loup.SUPER_INTRO;
                float present = Ease.outBack(Ease.range(sw, 4, 26), 1.3f) * (1 - Ease.inOutSine(Ease.range(lw, Loup.WHEEL_STOP + 18, Loup.WHEEL_T - 2)));
                float a = lw < 0 ? e1 : spinAngle(lw, e1, e2);
                float speed = lw < 0 ? 0 : spinAngle(lw + 0.5f, e1, e2) - spinAngle(lw - 0.5f, e1, e2);
                return new Wheel(a, flip >= 0.5f, lw >= Loup.WHEEL_GO && lw < Loup.WHEEL_STOP, speed, 1, lw, flip, present, s);
            }
            angle = e2;
            supFace = s.superSeg >= 0;
            last = s;
        }
        return new Wheel(angle, supFace, false, 0, -1, 0, supFace ? 1 : 0, 0, last);
    }

    /**
     * Inclinaison du cliquet (degres) pour une rotation de la roue : une cheville qui arrive au sommet le pousse, il
     * plie, puis elle passe et il revient d'un coup.
     */
    static float flap(float angle) {
        float x = ((angle + 15) % 30 + 30) % 30;
        float th = x < 15 ? x : x - 30;
        if (th < FLAP_IN || th > FLAP_OUT + 1) return 0;
        float reach = Ease.clamp((PEG_R * (float) Math.sin(Math.toRadians(Math.min(th, FLAP_OUT))) + FLAP_HALF) / FLAP_LEN, 0, 0.8f);
        float d = (float) Math.toDegrees(Math.asin(reach));
        if (th > FLAP_OUT) d *= (1 - (th - FLAP_OUT)) * (1 - (th - FLAP_OUT));
        return d;
    }

    /** Nombre de chevilles qui ont passe le cliquet entre deux rotations (cliquetis). */
    static int pegsPassed(float a0, float a1) {
        return (int) (Math.floor((a1 + 15 - FLAP_OUT) / 30) - Math.floor((a0 + 15 - FLAP_OUT) / 30));
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.sheet(Recup.SHEET);
        out.solid(p, kit, 0);
        out.sheet(null);
        out.solid(p, bodyLights, 0.9f);
    }

    @Override
    protected void animate(Scene s, LoupView v, Pose p, DrawList out) {
        float[] at = s.item ? null : v.at(v.since);
        boolean playing = at != null && v.since < v.totalTicks() + 40;
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        float grow = s.item ? 0 : expansion(v, i, lt);
        // partie finie en plein bonus SUPER (gain maximum) : la borne reprend sa taille
        if (at != null && v.since > v.totalTicks()) grow *= 1 - Ease.inOutCubic(Ease.range(v.since - v.totalTicks(), 4, 34));
        float lift = EXT_H * grow;
        introT = Intro.time(v.intro, playing, s.item);
        float flick = s.item ? 1 : 0.8f + 0.2f * (float) Math.sin(s.time * 0.9f) * (float) Math.sin(s.time * 0.37f + 1);
        // ---- le haut de la borne monte avec la grille
        p.push().translate(0, lift, 0);
        out.solid(p, top, 0);
        out.solid(p, topperGlow, 0.75f * flick);
        out.solidTinted(p, lamps, flick, 1f, 0.8f, 0.5f);
        if (!s.item) {
            for (int k = 0; k < 2; k++) out.billboard(p, Sprite.HALO, LAMPS[k * 3], LAMPS[k * 3 + 1] - 0.1f, LAMPS[k * 3 + 2], 2.2f, 0, 0xFFB050, 0.3f * flick);
        }
        wheel(s, v, p, out, i, lt);
        p.pop();
        if (lift > 0.01f) {
            p.push().translate(0, SPLIT, 0).scale(1, lift / EXT_H, 1);
            out.solid(p, ext, 0);
            p.pop();
        }
        float press = playing && at[0] == 0 && at[1] < 6 ? Ease.bell(at[1], 0, 6) * 0.25f : 0;
        p.push().translate(0, -press, 0);
        out.solid(p, spinBtn, playing ? 0.3f : 0.55f + 0.35f * s.pulse(0.12f));
        p.pop();
        out.solid(p, bonusBtn, 0.35f + 0.25f * s.pulse(0.09f));
        // ---- fond de l'ecran (jeu et titre) et bandeaux, a la taille de la fenetre
        float[] nz = v(0, 0, 1);
        float wy1 = WY1 + lift, ry1 = RY1 + lift, py1 = SlotRig.PY1 + lift;
        MeshBuilder sb = new MeshBuilder();
        sb.face(SCREEN, nz, p(WX0, WY0, SZ), p(WX1, WY0, SZ), p(WX1, py1, SZ), p(WX0, py1, SZ));
        out.solid(p, sb.buildDynamic(), 0.45f);
        // intro (le joueur vient de s'installer) : le loup souffle la maison de paille, puis la grille tombe
        if (Intro.scene(introT)) {
            intro(s, p, out, introT);
        } else {
            MeshBuilder br = new MeshBuilder();
            br.face(BAR, nz, p(WX0 + 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, RY0 - 0.15f, SZ + 0.02f),
                    p(WX0 + 0.1f, RY0 - 0.15f, SZ + 0.02f));
            br.face(BAR, nz, p(WX0 + 0.1f, ry1 + 0.15f, SZ + 0.02f), p(WX1 - 0.1f, ry1 + 0.15f, SZ + 0.02f), p(WX1 - 0.1f, wy1 - 0.1f, SZ + 0.02f),
                    p(WX0 + 0.1f, wy1 - 0.1f, SZ + 0.02f));
            out.solid(p, br.buildDynamic(), 0.45f);
            if (lift > 0.01f) {
                // rangees du haut : echafaudage du chantier derriere les cases
                MeshBuilder eb = new MeshBuilder();
                BigWin.clipQuad(eb, EXT_M, RX0, RY1, RX1, RY1 + EXT_H, RY1, ry1, SZ + 0.01f, 1f, false);
                out.sheet(SHEET);
                out.solid(p, eb.buildDynamic(), 0.35f);
                out.sheet(null);
            }
            screen(s, v, p, out, at, lift);
            Intro.title(p, out, introT, I18n.get("heralizcasino.loup.titre"), I18n.get("heralizcasino.loup.intro"), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, AMBER, GOLD);
        }
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(WX0, WY0, FZ - 0.05f), p(WX1, WY0, FZ - 0.05f), p(WX1, py1, FZ - 0.05f), p(WX0, py1, FZ - 0.05f));
        out.translucent(p, gl.buildDynamic(), 0xFFFFFF, 0.25f);
    }

    /** Temps de l'intro pour l'image en cours (-1 : pas d'intro) ; lu par la grille et la roue. */
    private float introT = -1;

    /**
     * Intro : nuit sur la ferme (lune, etoiles), les trois maisons des cochons sortent de terre, le loup arrive par la
     * gauche, prend son souffle et souffle : la maison de paille s'envole, celle de bois tremble, la brique tient bon ;
     * le titre arrive, puis la grille (Intro.REVEAL).
     */
    private void intro(Scene s, Pose p, DrawList out, float t) {
        float z = SZ + 0.04f, ground = WY0 + 1.7f;
        MeshBuilder mb = new MeshBuilder(), fx = new MeshBuilder(), stars = new MeshBuilder();
        Intro.quad(mb, DARK, WX0, WY0, WX1, ground + 0.15f, WX0, WY0, WX1, WY1, z + 0.02f, 1f, false);
        // lune et etoiles
        float night = Ease.range(t, 6, 14);
        out.billboard(p, Sprite.HALO, WX1 - 2.6f, WY1 - 2.5f, z + 0.01f, 4.6f, 0, 0xFFF0B0, 0.45f * night);
        Intro.quad(fx, FLASH, WX1 - 4.2f, WY1 - 4.1f, WX1 - 1.0f, WY1 - 0.9f, WX0, WY0, WX1, WY1, z + 0.01f, 0.95f * night, false);
        for (int k = 0; k < 16; k++) {
            float x = WX0 + 0.5f + Ease.hash(k * 37 + 3) * (WX1 - WX0 - 5), y = WY1 - 0.6f - Ease.hash(k * 53 + 9) * 4.6f;
            float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * 0.25f + k * 1.7f));
            Intro.quad(stars, LED, x - 0.07f, y - 0.07f, x + 0.07f, y + 0.07f, WX0, WY0, WX1, WY1, z, tw * night, false);
        }
        // les trois maisons : paille, bois, brique
        for (int tier = 1; tier <= 3; tier++) {
            float pop = Ease.outBack(Ease.range(t, 8 + tier * 2, 14 + tier * 2), 2f);
            if (pop <= 0.01f) continue;
            float hx = 5.45f + tier * 2.45f, hs = 2.5f * pop;
            if (tier == 1 && t >= 38) {
                // la paille s'envole en tournoyant et rapetisse au loin (maillage opaque : pas de fondu)
                float u = (t - 38) / 14f, k = 1 - Ease.range(t, 40, 50);
                Intro.spin(mb, house(tier), hx + u * u * 8, ground + hs / 2 + u * 3.2f, hs * k, hs * k, -u * 300, z + 0.03f, 1f);
                continue;
            }
            float dx = t >= 35 && t < 52 ? (Ease.hash((int) (t * 3) + tier * 11) - 0.5f) * (tier == 2 ? 0.34f : 0.08f) : 0;
            Intro.quad(mb, house(tier), hx - hs / 2 + dx, ground, hx + hs / 2 + dx, ground + hs, WX0, WY0, WX1, WY1, z + 0.03f, 1f, false);
        }
        // le loup : il arrive, gonfle ses joues, souffle, puis repart
        float in = Ease.outCubic(Ease.range(t, 14, 26)), back = Ease.inCubic(Ease.range(t, 52, 60));
        float inhale = t < 34 ? Ease.range(t, 24, 34) : 0;
        boolean blow = t >= 34 && t < 52;
        float ws = 6.0f * (1 + 0.16f * inhale), wx = WX0 - 3.2f + 5.2f * in - 6 * back + (blow ? 0.06f * (float) Math.sin(t * 1.3f) : 0), wy = ground + 2.6f;
        Intro.quad(mb, blow ? WOLF_B : WOLF_A, wx - ws / 2, wy - ws / 2, wx + ws / 2, wy + ws / 2, WX0, WY0, WX1, WY1, z + 0.06f, 1f, false);
        out.solid(p, mb.buildDynamic(), 0.6f);
        out.additive(p, stars.buildDynamic(), 0xFFF6D8, 1f);
        // rafales de vent
        if (blow) {
            float a = Ease.clamp01((t - 34) / 3f) * (1 - Ease.range(t, 48, 52));
            for (int k = 0; k < 6; k++) {
                float x = WX0 + 1 + ((t - 34) * 0.9f + k * 2.6f) % 13f, y = WY0 + 2.6f + (k % 3) * 2.3f + (float) Math.sin(t * 0.4f + k) * 0.3f;
                Intro.quad(fx, WIND, x - 3, y - 0.7f, x + 3, y + 0.7f, WX0, WY0, WX1, WY1, z + 0.08f, a * (0.6f + 0.4f * Ease.hash(k * 7)), false);
            }
        }
        out.additive(p, fx.buildDynamic(), 0xF0F4FF, 0.9f);
        Intro.crtOn(p, out, DARK, FLASH, t, WX0, WY0, WX1, WY1, SZ + 0.36f, AMBER);
        Intro.title(p, out, t, I18n.get("heralizcasino.loup.titre"), I18n.get("heralizcasino.loup.intro"), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, AMBER, GOLD);
    }

    /** La roue des bonus au sommet de la borne, son pointeur et sa couronne d'ampoules (repere : la borne relevee). */
    private void wheel(Scene s, LoupView v, Pose p, DrawList out, int i, float lt) {
        if (s.item) {
            // objet d'inventaire : roue simplifiee (la feuille de texture de la roue n'y est pas disponible)
            p.push().translate(WHX, WHY, WHZ);
            out.solid(p, wheelFlat, 0);
            out.solid(p, pegs, 0);
            p.pop();
            p.push().translate(WHX, WHY + FLAP_Y, WHZ);
            out.solid(p, pointer, 0);
            p.pop();
            return;
        }
        Wheel w = wheelAt(v, i, lt);
        Loup.Spin ws = w.spin;
        // la case gagnante est connue une fois la roue arretee (apres un court silence)
        boolean result = w.turn >= 0 && w.w >= Loup.WHEEL_STOP + 6;
        boolean settled = w.turn < 0 && ws != null && i >= 0 && v.seq.spins.get(i) == ws && lt >= Loup.wheelStart(ws) && lt < Loup.introStart(ws) + Loup.BONUS_INTRO;
        float pr = w.present;
        float lit = w.spinning ? 0.75f : result || settled ? 0.85f : 0.5f + 0.08f * s.pulse(0.05f);
        // ---- presentation de la super roue : rayons d'or derriere, la roue s'avance et grandit
        if (pr > 0.01f) {
            out.rays(p, WHX, WHY, WHZ - 0.3f, 16, 9f * pr, 0.9f, s.time * 1.2f, GOLD, 0.28f * pr);
            out.billboard(p, Sprite.HALO, WHX, WHY, WHZ - 0.2f, 9f * pr, 0, 0xFFC040, 0.35f * pr);
        }
        p.push().translate(WHX, WHY, WHZ + 2.6f * pr).scale(1 + 0.24f * pr);
        // ---- le disque peint et ses chevilles ; il se retourne pour devenir la super roue
        p.push().rotY((w.flip < 0.5f ? w.flip : w.flip - 1) * 180).rotZ(-w.angle);
        out.sheet(WHEEL_SHEET);
        out.solid(p, w.sup ? superWheel : wheel, lit + 0.15f * pr);
        out.sheet(null);
        out.solid(p, pegs, 0);
        if ((result || settled) && ws != null) {
            int seg = w.sup ? ws.superSeg : ws.wheelSeg;
            if (seg >= 0) {
                float since = result ? w.w - Loup.WHEEL_STOP - 6 : 40;
                float pop = Ease.clamp01(since / 4f);
                p.push().rotZ(-seg * 30);
                out.additive(p, wedge, 0xFFF0C8, (0.1f + 0.2f * (float) Math.abs(Math.sin(s.time * 0.45f))) * pop);
                p.pop();
            }
        }
        p.pop();
        // ---- cliquet : il plie sous les chevilles ; a pleine vitesse il vibre
        float tilt = flap(w.angle);
        if (w.speed > 12) tilt = 17 + 7 * (float) Math.sin(s.time * 4.7f);
        p.push().translate(0, FLAP_Y, 0.05f).rotZ(tilt);
        out.solid(p, pointer, 0);
        p.pop();
        // ---- ampoules : chenillard au repos, affole pendant la rotation, toutes ensemble au resultat ; dorees en SUPER
        int step = (int) (s.time / (w.spinning ? Math.max(1.2f, 7 - w.speed * 0.4f) : pr > 0.1f ? 2.5f : 6f));
        boolean blink = ((int) (s.time / 3)) % 2 == 0;
        int n = 20;
        for (int k = 0; k < n; k++) {
            float[] q = wp(k * 360f / n + 9, WHR + 0.42f, 0);
            boolean on = result ? blink : w.spinning ? (k + step) % 3 == 0 : (k + step) % 4 < 2;
            p.push().translate(q[0], q[1], 0.12f);
            out.solidTinted(p, bulb, on ? 1f : 0.15f, 1f, on ? (pr > 0.1f ? 0.9f : 0.85f) : 0.5f, on ? (pr > 0.1f ? 0.25f : 0.45f) : 0.3f);
            p.pop();
            if (on) out.billboard(p, Sprite.HALO, q[0], q[1], 0.4f, 0.7f, 0, pr > 0.1f ? 0xFFE060 : 0xFFC860, 0.35f);
        }
        p.pop();
        if (w.turn < 0 || ws == null) return;
        // ---- mise en scene du lancer : eclat du retournement, annonce sous la roue
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        float ty = TITLE_Y, tz = FZ + 1.02f + 1.6f * pr;
        if (w.turn == 1 && w.w < -Loup.SUPER_INTRO + 22) {
            float u = w.w + Loup.SUPER_INTRO;
            out.billboard(p, Sprite.FLARE, WHX, WHY, WHZ + 2.6f, 12f * Ease.bell(u, 2, 22), s.time * 6, GOLD, 0.9f * Ease.bell(u, 2, 22));
        }
        if (result) {
            float since = w.w - Loup.WHEEL_STOP - 6;
            int code = w.turn == 1 ? Loup.SUPER_WHEEL[ws.superSeg] : Loup.WHEEL[ws.wheelSeg];
            int col = segColor(code);
            float burst = 1 - Ease.clamp01(since / 10f);
            float k = 1 + 0.24f * pr;
            out.billboard(p, Sprite.FLARE, WHX, WHY + WHR * 0.62f * k, WHZ + 0.7f + 2.6f * pr, 2.4f + 2.2f * burst, s.time * 3, Ease.mix(col, 0xFFFFFF, 0.5f), 0.14f + 0.5f * burst);
            out.rays(p, WHX, WHY + WHR * 0.62f * k, WHZ + 0.6f + 2.6f * pr, 10, 4.2f, 0.5f, s.time * 1.5f, Ease.mix(col, 0xFFFFFF, 0.4f), 0.16f * Ease.clamp01(since / 6f));
            String title = segTitle(code);
            caption(s, p, out, Ease.clamp01(since / 3f), Ease.mix(col, 0xFFFFFF, 0.25f), pr);
            out.text(p, title, WHX, ty, tz, fit(title, 1.45f, 12.6f, true) * Ease.outBack(Ease.clamp01(since / 6f), 2.4f), GOLD, 1f, flags, 0x2A1400);
        } else if (w.w < Loup.WHEEL_GO + 10) {
            boolean sup = w.turn == 1;
            String title = I18n.get(sup ? (ws.events & Loup.EV_DIRECT) != 0 ? "heralizcasino.loup.scie_or_super" : "heralizcasino.loup.super_roue" : "heralizcasino.loup.roue_bonus");
            float from = sup ? -Loup.SUPER_INTRO : 0;
            float a = Ease.clamp01((w.w - from) / 4f) * (1 - Ease.range(w.w, Loup.WHEEL_GO + 4, Loup.WHEEL_GO + 10));
            caption(s, p, out, a, sup ? GOLD : AMBER, pr);
            float pulse = sup ? 1 + 0.08f * (float) Math.sin(s.time * 0.7f) : 1;
            out.text(p, title, WHX, ty, tz, fit(title, 1.45f, 12.6f, true) * Ease.outBack(Ease.clamp01((w.w - from) / 6f), 2.2f) * pulse, sup ? GOLD : AMBER, a, flags,
                    sup ? 0x3A1400 : 0x000000);
        }
    }

    /** Panneau d'annonce qui se deplie devant le titre de l'ecran, sous la roue (k : ouverture 0..1), borde d'un filet lumineux. */
    private void caption(Scene s, Pose p, DrawList out, float k, int rgb, float pr) {
        if (k <= 0.02f) return;
        p.push().translate(8, TITLE_Y, FZ + 0.82f + 1.6f * pr).scale(1, k, 1);
        out.solid(p, captionPlate, 0);
        out.additive(p, captionFrame, rgb, 0.7f + 0.3f * s.pulse(0.4f));
        p.pop();
    }

    static float cellX(int reel) {
        return RX0 + (reel + 0.5f) * CW;
    }

    /** Centre d'une rangee du jeu normal (0 en haut). */
    static float cellY(float row) {
        return RY1 - (row + 0.5f) * CH;
    }

    /** Centre d'une rangee de la grille des bonus (0 a 5, les trois du haut au-dessus des rouleaux). */
    static float bY(float row) {
        return RY1 + EXT_H - (row + 0.5f) * CH;
    }

    private static void cell(MeshBuilder b, int reel, float y, Mat m, float tint, float dx, float scale) {
        float cx = cellX(reel) + dx, cy = cellY(y);
        float hw = (CW - 0.1f) / 2 * scale, hh = (CH - 0.1f) / 2 * scale;
        BigWin.clipQuad(b, m, cx - hw, cy - hh, cx + hw, cy + hh, RY0, RY1, SZ + 0.03f, tint, false);
    }

    /** Quad centre en (x, y), decoupe a la partie visible des rouleaux [RY0, top]. */
    private static void clip(MeshBuilder b, Mat m, float x, float y, float w, float h, float top, float z, float tint) {
        BigWin.clipQuad(b, m, x - w / 2, y - h / 2, x + w / 2, y + h / 2, RY0, top, z, tint, false);
    }

    private static void quadAt(MeshBuilder b, float x, float y, float w, float h, Mat m, float k, float z) {
        BigWin.clipQuad(b, m, x - w / 2, y - h / 2, x + w / 2, y + h / 2, -1e9f, 1e9f, z, k, false);
    }

    /** Quad tourne (lame de scie qui tourne sur elle-meme). */
    private static void spinQuad(MeshBuilder b, Mat m, float cx, float cy, float half, float deg, float z, float tint) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a) * half, sn = (float) Math.sin(a) * half;
        float[][] pts = {p(cx - c + sn, cy - sn - c, z), p(cx + c + sn, cy + sn - c, z), p(cx + c - sn, cy + sn + c, z), p(cx - c - sn, cy - sn + c, z)};
        float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
        b.color(tint);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    private static void windQuad(MeshBuilder b, float x, float y, float w, float h, float k) {
        float x0 = x - w / 2, x1 = x + w / 2;
        float c0 = Math.max(x0, WX0 + 0.1f), c1 = Math.min(x1, WX1 - 0.1f);
        if (c1 <= c0) return;
        float u0 = (c0 - x0) / w, u1 = (c1 - x0) / w, z = SZ + 0.12f;
        float[][] pts = {p(c0, y - h / 2, z), p(c1, y - h / 2, z), p(c1, y + h / 2, z), p(c0, y + h / 2, z)};
        float[][] uv = {{WIND.u(u0), WIND.v(1)}, {WIND.u(u1), WIND.v(1)}, {WIND.u(u1), WIND.v(0)}, {WIND.u(u0), WIND.v(0)}};
        b.color(k);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    private static int filler(int spin, int reel, int k) {
        float h = Ease.hash(spin * 7919 + reel * 131 + k * 17);
        return h < 0.6f ? (int) (h / 0.6f * 5) : h < 0.95f ? 5 + (int) ((h - 0.6f) / 0.35f * 3) : Loup.WOLF;
    }

    /** Montant en jetons (ou "xN" sans mise). */
    static String amountText(LoupView v, long milli) {
        if (v.bet <= 0) {
            double x = milli / 1000.0;
            return "x" + (x == Math.floor(x) ? String.valueOf((long) x) : String.format(java.util.Locale.ROOT, "%.1f", x));
        }
        return fmt((long) Math.floor(v.bet * milli / 1000.0));
    }

    /** Ce que cache une maison : jetons, jackpot ou la roue. */
    static String rewardText(LoupView v, int tier, int reward) {
        return switch (reward) {
            case Loup.R_MINI -> "MINI";
            case Loup.R_MINI2 -> "2× MINI";
            case Loup.R_MINOR -> "MINOR";
            case Loup.R_MAJOR -> "MAJOR";
            case Loup.R_GRAND -> "GRAND";
            case Loup.R_WHEEL -> I18n.get("heralizcasino.loup.roue");
            default -> amountText(v, Loup.rewardMilli(v.rules, tier, reward));
        };
    }

    static int rewardColor(int tier, int reward) {
        return switch (reward) {
            case Loup.R_MINI, Loup.R_MINI2 -> JACKPOT_COLORS[0];
            case Loup.R_MINOR -> JACKPOT_COLORS[1];
            case Loup.R_MAJOR -> JACKPOT_COLORS[2];
            case Loup.R_GRAND -> JACKPOT_COLORS[3];
            case Loup.R_WHEEL -> 0xFF8AE8;
            default -> TIER_COLORS[Math.min(3, tier)];
        };
    }

    private record Label(float x, float y, String text, int color, float size, float a) {
    }

    /** Calques de l'ecran, tous construits dans la meme image. */
    private static final class Layers {
        final MeshBuilder mb = new MeshBuilder(), glow = new MeshBuilder(), flash = new MeshBuilder(), wind = new MeshBuilder(), hats = new MeshBuilder();
        /** Feuille des bonus : cadres, lames de scie, mega chapeaux. */
        final MeshBuilder cad = new MeshBuilder(), cadGlow = new MeshBuilder();
        final List<Label> labels = new ArrayList<>();
    }

    private void screen(Scene s, LoupView v, Pose p, DrawList out, float[] at, float lift) {
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        Loup.Spin spin = i >= 0 ? v.seq.spins.get(i) : null;
        boolean finished = at != null && v.since >= v.totalTicks();
        float top = RY1 + lift;
        Layers L = new Layers();
        float dim = BigWin.dim(bigAt(spin, lt), Rotten.BIG_WIN_TICKS);
        float pulse = 0.55f + 0.45f * (float) Math.sin(s.time * 0.6f);
        if (spin == null || (finished && v.since > v.totalTicks() + 200)) {
            for (int reel = 0; reel < Loup.REELS; reel++) {
                // fin de l'intro : les colonnes tombent l'une apres l'autre
                float drop = Intro.drop(introT, reel, Loup.ROWS), land = Intro.landed(introT, reel);
                for (int row = 0; row < Loup.ROWS; row++) {
                    int c = Loup.cell(reel, row);
                    int slot = (int) (s.time / 40), pick = (int) (Ease.hash(slot + (int) (s.seed * 50)) * Loup.CELLS);
                    float k = pick == c ? 1f + 0.5f * Ease.bell(s.time % 40, 0, 20) : 1f;
                    cell(L.mb, reel, row - drop, symbol(IDLE[c], false), k * (1 + 0.5f * land), 0, 1);
                }
                if (land > 0) quadAt(L.flash, cellX(reel), cellY(Loup.ROWS - 0.5f), CW * 1.6f, CH * 1.6f, FLASH, 0.7f * land, SZ + 0.08f);
            }
            spin = null;
        } else if (spin.kind == Loup.BASE) {
            baseGrid(s, v, spin, i, lt, L, dim, pulse);
        } else if (spin.kind == Loup.SPIN_WHEEL) {
            for (int reel = 0; reel < Loup.REELS; reel++) {
                for (int row = 0; row < Loup.ROWS; row++) cell(L.mb, reel, row, EMPTY_M, dim, 0, 1);
            }
        } else {
            bonusGrid(s, v, spin, i, lt, top, L, dim, pulse);
        }
        out.solid(p, L.mb.buildDynamic(), 0.55f);
        if (!L.cad.isEmpty() || !L.cadGlow.isEmpty()) {
            out.sheet(SHEET);
            if (!L.cad.isEmpty()) out.solid(p, L.cad.buildDynamic(), 0.5f);
            if (!L.cadGlow.isEmpty()) out.additive(p, L.cadGlow.buildDynamic(), 0xFFFFFF, 0.5f);
            out.sheet(null);
        }
        if (!L.glow.isEmpty()) out.additive(p, L.glow.buildDynamic(), 0xFFFFFF, 0.4f);
        if (!L.hats.isEmpty()) out.solid(p, L.hats.buildDynamic(), 0.45f);
        if (!L.flash.isEmpty()) out.additive(p, L.flash.buildDynamic(), GOLD, 1f);
        if (!L.wind.isEmpty()) out.additive(p, L.wind.buildDynamic(), 0xE8F4FF, 0.9f);
        if (spin != null && (spin.kind == Loup.BASE || spin.kind == Loup.FREE) && !s.item) suspense(s, spin, lt, top, p, out);
        for (Label l : L.labels) {
            if (l.a < 0.05f || l.y < RY0 || l.y > top) continue;
            // marge entre deux cases voisines : "100" et "200" cote a cote ne doivent pas se lire "100200"
            out.text(p, l.text, l.x, l.y, SZ + 0.2f, fit(l.text, l.size, CW * 0.7f, true), l.color, l.a, DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD, 0x000000);
        }
        if (spin != null && spin.kind == Loup.FREE && (spin.events & Loup.EV_BONUS_END) != 0) wolf(p, out, lt - Loup.blowStart(spin), Loup.blow(spin), lift);
        if (spin != null && spin.kind == Loup.SETUP && (spin.events & Loup.EV_EXPAND) != 0) expandFx(s, p, out, lt, lift);
        bars(s, v, p, out, i, spin, lt, finished, lift);
        overlays(s, v, p, out, i, spin, lt, lift);
    }

    // =================================================================== suspense (tour normal)
    /** Rouleau en train de tourner au ralenti (-1 aucun) et ticks ecoules depuis le debut de son ralenti. */
    static float[] teasing(Loup.Spin spin, float lt) {
        if (spin == null || spin.kind != Loup.BASE) return null;
        for (int reel = 1; reel < Loup.REELS; reel++) {
            if (!Loup.tease(spin, reel)) continue;
            int stop = Loup.stopTick(spin, reel);
            float t = lt - (stop - SLOW);
            if (t >= 0 && lt < stop) return new float[]{reel, t};
        }
        return null;
    }

    /** Duree du ralenti d'un rouleau en suspens, et cases qu'il parcourt pendant ce temps. */
    static final int SLOW = Suspense.HOLD + 2;
    static final float SLOW_CELLS = Loup.ROWS + 2;
    /** Ralenti d'une colonne de chapeaux en suspens (tour gratuit), plus court. */
    static final int FREE_SLOW = Suspense.HOLD_SHORT + 2;

    static int teaseColor(int symbol) {
        return symbol == Loup.SAW ? BLUE : GOLD;
    }

    static boolean isSaw(int sym) {
        return sym == Loup.SAW || sym == Loup.GOLD;
    }

    /** Colonne de lumiere sur le rouleau en suspens, braises sur les symboles deja tombes qui attendent le dernier. */
    private void suspense(Scene s, Loup.Spin spin, float lt, float top, Pose p, DrawList out) {
        float[] ft = freeTeasing(spin, lt);
        if (ft != null) {
            // tour gratuit : il ne manque qu'un chapeau pour un tour de plus
            int reel = (int) ft[0], lo = Loup.firstRow(spin.bonus);
            float tension = Suspense.tension(ft[1], FREE_SLOW);
            Tease.column(s, p, out, RX0 + reel * CW, RY0, RX0 + (reel + 1) * CW, top, SZ + 0.1f, GOLD, tension);
            for (int r = 0; r < reel; r++) {
                for (int row = lo; row < Loup.BROWS; row++) {
                    if (spin.hat(Loup.bcell(r, row))) Tease.ember(s, p, out, cellX(r), bY(row), SZ + 0.25f, CW * 0.75f, GOLD, tension);
                }
            }
            return;
        }
        float[] tz = teasing(spin, lt);
        if (tz == null) return;
        int reel = (int) tz[0];
        float tension = Suspense.tension(tz[1], SLOW);
        int want = Loup.teaseSymbol(spin, reel), col = teaseColor(want);
        Tease.column(s, p, out, RX0 + reel * CW, RY0, RX0 + (reel + 1) * CW, RY1, SZ + 0.1f, col, tension);
        for (int c = 0; c < reel * Loup.ROWS; c++) {
            int g = spin.grid[c];
            if (want == Loup.SAW ? !isSaw(g) : g != want) continue;
            Tease.ember(s, p, out, cellX(c / Loup.ROWS), cellY(c % Loup.ROWS), SZ + 0.25f, CW * 0.75f, col, tension);
        }
    }

    /** Tour normal : rouleaux qui tournent, facons gagnantes, chapeaux et scies qui brillent. */
    private void baseGrid(Scene s, LoupView v, Loup.Spin spin, int i, float lt, Layers L, float dim, float pulse) {
        int winMask = 0;
        if (spin.lineMilli > 0) {
            int[] m = new int[1];
            Loup.ways(v.rules, spin.grid, m);
            winMask = m[0];
        }
        int end = Loup.spinEnd(spin);
        boolean showLines = winMask != 0 && lt >= end && lt < end + Loup.lineShow(spin);
        boolean hatTrig = (spin.events & Loup.EV_HATS) != 0, sawTrig = (spin.events & (Loup.EV_WHEEL | Loup.EV_SUPER)) != 0;
        float[] tz = teasing(spin, lt);
        int hot = tz == null ? -1 : (int) tz[0];
        float tension = tz == null ? 0 : Suspense.tension(tz[1], SLOW);
        int want = hot < 0 ? -1 : Loup.teaseSymbol(spin, hot);
        // annonce du bonus : les symboles s'effacent derriere le titre, sauf ceux qui l'ont declenche
        float fade = (spin.events & Loup.EV_BONUS) != 0 ? Ease.range(lt, Loup.introStart(spin), Loup.introStart(spin) + 12) : 0;
        for (int reel = 0; reel < Loup.REELS; reel++) {
            int stop = Loup.stopTick(spin, reel);
            if (lt < stop) {
                spinning(spin, i, reel, lt, stop, L.mb);
                continue;
            }
            boolean teased = Loup.tease(spin, reel);
            int hoped = teased ? Loup.teaseSymbol(spin, reel) : -1;
            for (int row = 0; row < Loup.ROWS; row++) {
                int c = Loup.cell(reel, row);
                int sym = spin.grid[c];
                float bounce = Ease.spring(lt - stop, 1.1f, 0.45f) * 0.1f;
                float tint = dim;
                if (showLines) tint *= (winMask & (1 << c)) != 0 ? 1.2f : 0.45f;
                boolean trig = (sym == Loup.HAT && hatTrig) || (isSaw(sym) && sawTrig);
                if (trig && lt >= end) tint *= 1.1f + 0.3f * pulse;
                if (!trig) tint *= 1 - 0.75f * fade;
                if ((isSaw(sym) || sym == Loup.HAT) && lt < stop + 10) tint *= 1f + 0.4f * Ease.bell(lt, stop, stop + 10);
                if (hot >= 0) tint *= (want == Loup.SAW ? isSaw(sym) : sym == want) ? 1.15f + 0.25f * pulse : Tease.dim(tension);
                cell(L.mb, reel, row - bounce, symbol(sym, false), tint, 0, 1);
                if ((showLines && (winMask & (1 << c)) != 0) || (trig && lt >= end)) quadAt(L.glow, cellX(reel), cellY(row), CW, CH, symbol(sym, false), pulse * dim, SZ + 0.06f);
                if (sym == Loup.GOLD && lt >= stop) quadAt(L.flash, cellX(reel), cellY(row), CW * 1.5f, CH * 1.5f, FLASH, 0.25f + 0.2f * pulse, SZ + 0.07f);
                if ((sym == hoped || (hoped == Loup.SAW && isSaw(sym))) && lt < stop + 12) {
                    quadAt(L.flash, cellX(reel), cellY(row), CW * 2.2f, CH * 2.2f, FLASH, 1 - Ease.range(lt, stop, stop + 12), SZ + 0.09f);
                }
            }
        }
    }

    /**
     * Rouleau qui tourne : une bande virtuelle defile vers le bas, le resultat en haut (cases 0 a ROWS - 1), puis le
     * remplissage, puis l'ancien affichage. Un rouleau en suspens finit au ralenti ; s'il ne porte pas le symbole
     * attendu, celui-ci passe parfois sous les yeux du joueur... et sort par le bas.
     */
    private void spinning(Loup.Spin spin, int i, int reel, float lt, int stop, MeshBuilder mb) {
        boolean teased = Loup.tease(spin, reel);
        int fast = Suspense.fastCells(teased ? stop - SLOW : stop, Loup.ROWS);
        int d = fast + Loup.ROWS + (teased ? (int) SLOW_CELLS : 0);
        float off, speed;
        if (teased) {
            float slowAt = stop - SLOW;
            off = Tease.travel(lt, slowAt, stop, d - (int) SLOW_CELLS, SLOW_CELLS);
            speed = Tease.travel(lt + 0.5f, slowAt, stop, d - (int) SLOW_CELLS, SLOW_CELLS) - Tease.travel(lt - 0.5f, slowAt, stop, d - (int) SLOW_CELLS, SLOW_CELLS);
        } else {
            float span = Math.max(1f, stop - 1);
            off = d * SlotView.travel(Math.max(0, lt - 1) / span);
            speed = d * (SlotView.travel(Math.max(0, lt - 0.5f) / span) - SlotView.travel(Math.max(0, lt - 1.5f) / span));
        }
        boolean blur = speed > 0.3f;
        int bait = -1;
        if (teased) {
            int hoped = Loup.teaseSymbol(spin, reel);
            boolean has = false;
            for (int row = 0; row < Loup.ROWS; row++) {
                int g = spin.grid[Loup.cell(reel, row)];
                has |= hoped == Loup.SAW ? isSaw(g) : g == hoped;
            }
            if (!has && Ease.hash(spin.grid[Loup.cell(reel, 0)] * 131 + spin.grid[Loup.cell(reel, 1)] * 17 + spin.grid[Loup.cell(reel, 2)] + reel * 7) < 0.55f) bait = hoped;
        }
        float base = d - off;
        int first = (int) Math.floor(base);
        for (int k = first; k <= first + Loup.ROWS; k++) {
            int sym;
            if (k < 0) continue;
            if (k < Loup.ROWS) sym = spin.grid[Loup.cell(reel, k)];
            else if (k >= d) sym = IDLE[Loup.cell(reel, Math.min(Loup.ROWS - 1, k - d))];
            else if (k == Loup.ROWS && bait >= 0) sym = bait;
            else sym = filler(i, reel, k);
            cell(mb, reel, k - base, symbol(sym, blur), 1f, 0, 1);
        }
    }

    // =================================================================== bonus
    /**
     * Mise en place et tours gratuits : cadres (paille, bois, brique) poses ou ameliores par les chapeaux, les scies ou
     * les mega chapeaux ; a la fin, les cadres deviennent des maisons que le loup souffle.
     */
    private void bonusGrid(Scene s, LoupView v, Loup.Spin spin, int i, float lt, float top, Layers L, float dim, float pulse) {
        boolean setup = spin.kind == Loup.SETUP;
        boolean sup = Loup.isSuper(spin.bonus);
        int lo = sup ? 0 : Loup.TOP;
        // ---- cadres a l'instant lt : ceux d'avant, plus les coups deja portes
        int[] tier = new int[Loup.BCELLS];
        List<int[]> hits;
        float ht;
        Loup.Plan plan = setup ? v.plan(i) : null;
        if (setup) {
            hits = plan.hits;
            ht = lt - Loup.actionStart(spin);
        } else {
            System.arraycopy(v.seq.spins.get(i - 1).tier, 0, tier, 0, Loup.BCELLS);
            hits = v.freeHits(i);
            ht = lt;
        }
        Loup.apply(hits, tier, ht);
        float[] lastHit = new float[Loup.BCELLS];
        java.util.Arrays.fill(lastHit, -100);
        for (int[] h : hits) if (h[0] <= ht) lastHit[h[1]] = Math.max(lastHit[h[1]], h[0]);
        boolean end = !setup && (spin.events & Loup.EV_BONUS_END) != 0;
        float bt0 = end ? lt - Loup.buildStart(spin) : -1;
        int[] built = end ? Loup.buildTimes(spin) : null;
        float bt = end ? lt - Loup.blowStart(spin) : -1;
        int[] reveal = end ? Loup.revealTimes(spin) : null;
        int blowLen = end ? Loup.blow(spin) : 0;
        Loup.Spin prev = i > 0 ? v.seq.spins.get(i - 1) : null;
        for (int reel = 0; reel < Loup.REELS; reel++) {
            for (int row = lo; row < Loup.BROWS; row++) {
                int c = Loup.bcell(reel, row);
                float x = cellX(reel), y = bY(row);
                if (y - CH / 2 >= top) continue;
                boolean upper = row < Loup.TOP;
                // fond de case (les rangees du haut, plus etroites, laissent voir l'echafaudage)
                clip(L.mb, EMPTY_M, x, y, CW - (upper ? 0.35f : 0.1f), CH - (upper ? 0.35f : 0.1f), top, SZ + 0.015f, dim * (upper ? 1.15f : 1f));
                // la rangee du haut se devoile : eclair dore
                if (upper) {
                    float seen = top - (y + CH / 2);
                    if (seen > -CH && seen < 1.6f) clip(L.flash, FLASH, x, y, CW * 1.3f, CH * 1.3f, top, SZ + 0.08f, 0.5f * Ease.bell(seen, -CH * 0.4f, 1.6f));
                }
                int t = tier[c];
                float since = ht - lastHit[c];
                float rt = end && reveal[c] >= 0 && bt >= reveal[c] ? bt - reveal[c] : -1;
                boolean isHouse = end && built[c] >= 0 && bt0 >= built[c];
                if (t > 0 && rt < 12) {
                    float scale = 1, dx = 0, k = dim;
                    if (since >= 0 && since < 8) scale = 1 + 0.25f * Ease.bell(since, 0, 8);
                    if (isHouse) {
                        float bs = bt0 - built[c];
                        if (bs < 7) scale = 1 + 0.3f * Ease.bell(bs, 0, 7);
                        if (bt >= 0 && rt < 0) {
                            // elles tremblent de plus en plus pendant que le loup inspire, puis chacune juste avant de partir
                            float shake = 0.03f + 0.11f * Ease.clamp01(bt / Loup.BLOW_WIND);
                            if (bt > reveal[c] - 5) shake = 0.34f;
                            dx = (Ease.hash((int) (lt * 3) + c * 7) - 0.5f) * shake;
                        }
                        float fly = rt >= 0 ? Ease.clamp01(rt / 12f) : 0;
                        if (fly > 0) {
                            dx = fly * fly * 9f;
                            scale = 1 - 0.5f * fly;
                            k *= 1 - fly;
                        }
                        clip(L.mb, house(t), x + dx, y - fly * 1.2f, (CW - 0.1f) * scale, (CH - 0.1f) * scale, top, SZ + 0.03f, k);
                        if (t == 3 && fly == 0) clip(L.glow, house(3), x, y, CW, CH, top, SZ + 0.06f, 0.3f * pulse);
                        if (bs >= 0 && bs < 8) clip(L.flash, FLASH, x, y, CW * 1.5f, CH * 1.5f, top, SZ + 0.08f, 1 - bs / 8f);
                    } else {
                        clip(L.cad, frame(t), x, y, CW * 1.04f * scale, CH * 1.04f * scale, top, SZ + 0.04f, k);
                        if (t == 3) clip(L.cadGlow, frame(3), x, y, CW * 1.04f, CH * 1.04f, top, SZ + 0.05f, 0.2f + 0.15f * pulse);
                    }
                }
                if (rt >= 0 && t > 0) {
                    // la plaque de gain apparait sous la maison emportee
                    float pop = Ease.outBack(Ease.clamp01(rt / 5f), 2.4f);
                    int rw = spin.reward[c];
                    boolean special = rw >= Loup.R_MINI;
                    clip(L.mb, PLATE, x, y, CW * 0.82f * pop, CH * 0.82f * pop, top, SZ + 0.035f, dim);
                    L.labels.add(new Label(x, y - 0.1f, rewardText(v, t, rw), rewardColor(t, rw), (special ? 0.72f : 0.62f) * Math.min(1.2f, pop), Ease.clamp01(rt / 3f) * dim));
                    if (rt < 9) clip(L.flash, FLASH, x, y, CW * (special ? 2.4f : 1.6f), CH * (special ? 2.4f : 1.6f), top, SZ + 0.08f, 1 - rt / 9f);
                    if (special) clip(L.glow, PLATE, x, y, CW, CH, top, SZ + 0.06f, 0.6f * pulse);
                    else if (bt >= blowLen - Loup.BLOW_TAIL + 10) clip(L.glow, PLATE, x, y, CW, CH, top, SZ + 0.06f, pulse);
                }
                if (since >= 0 && since < 8) clip(L.flash, FLASH, x, y, CW * 1.4f, CH * 1.4f, top, SZ + 0.08f, 1 - since / 8f);
            }
        }
        if (setup) setupFx(s, v, spin, prev, plan, lt, top, L, dim, pulse);
        else freeHats(s, v, spin, i, lt, top, L);
        // rafales de vent : des que le loup souffle, jusqu'a la derniere maison
        float windEnd = blowLen - Loup.BLOW_TAIL + 8;
        if (end && bt >= Loup.BLOW_WIND - 4 && bt < windEnd) {
            float a = Ease.clamp01((bt - Loup.BLOW_WIND + 4) / 5f) * Ease.clamp01((windEnd - bt) / 6f);
            int rows = sup ? Loup.BROWS : Loup.ROWS;
            for (int k = 0; k < 6 + (sup ? 4 : 0); k++) {
                float x = RX0 - 3 + (bt * 0.9f + k * 2.7f) % 17f;
                float y = RY0 + 0.9f + (k % rows) * (CH * 1.0f) + (float) Math.sin(bt * 0.4f + k) * 0.3f;
                if (y > top - 0.5f) continue;
                windQuad(L.wind, x, y, 6f, 1.4f, a * (0.6f + 0.4f * Ease.hash(k * 7)));
            }
        }
    }

    /** Part des places de la bande d'une colonne qui portent un chapeau pendant la rotation (pluie de chapeaux). */
    static final float HAT_RAIN = 0.32f;

    /**
     * Tour gratuit : chaque colonne tourne comme un rouleau de chapeaux. Ils defilent du haut vers le bas, meme quand
     * aucun ne se pose ; les colonnes s'arretent de gauche a droite (au ralenti quand il ne manque qu'un chapeau pour un
     * tour de plus) et ceux qui tombent sur une case y restent un instant avant de construire leur cadre, de rebondir
     * vers une autre case (sur une brique) ou de se changer en prime (tout est deja en brique).
     */
    private void freeHats(Scene s, LoupView v, Loup.Spin spin, int i, float lt, float top, Layers L) {
        int lo = Loup.firstRow(spin.bonus), rows = Loup.BROWS - lo;
        int go = Loup.hatStart(spin) + 2;
        float hw = CW * 0.42f, hh = CH * 0.42f;
        for (int reel = 0; reel < Loup.REELS; reel++) {
            int stop = Loup.freeStop(spin, reel);
            float x = cellX(reel);
            if (lt >= go && lt < stop) {
                // bande virtuelle : le resultat en bas de la bande (places 0 .. rows-1), puis la pluie de chapeaux ; au
                // depart la fenetre est vide (places >= d)
                boolean teased = Loup.freeTease(spin, reel);
                float t = lt - go, span = stop - go;
                int fast = Suspense.fastCells(teased ? span - FREE_SLOW : span, rows);
                int d = fast + rows + (teased ? (int) SLOW_CELLS : 0);
                float off, speed;
                if (teased) {
                    float slowAt = span - FREE_SLOW;
                    off = Tease.travel(t, slowAt, span, d - (int) SLOW_CELLS, SLOW_CELLS);
                    speed = Tease.travel(t + 0.5f, slowAt, span, d - (int) SLOW_CELLS, SLOW_CELLS) - Tease.travel(t - 0.5f, slowAt, span, d - (int) SLOW_CELLS, SLOW_CELLS);
                } else {
                    float sp = Math.max(1f, span - 1);
                    off = d * SlotView.travel(Math.max(0, t - 1) / sp);
                    speed = d * (SlotView.travel(Math.max(0, t - 0.5f) / sp) - SlotView.travel(Math.max(0, t - 1.5f) / sp));
                }
                // rate de peu : sur une colonne sans chapeau, il en passe souvent un juste avant l'arret
                boolean any = false;
                for (int row = lo; row < Loup.BROWS; row++) any |= spin.hat(Loup.bcell(reel, row));
                boolean nearMiss = !any && Ease.hash(i * 977 + reel * 37 + 11) < (teased ? 0.8f : 0.45f);
                float base = d - off;
                int first = (int) Math.floor(base);
                for (int k = Math.max(0, first - 1); k <= first + rows; k++) {
                    if (k >= d) break;
                    boolean hat;
                    if (k < rows) hat = spin.hat(Loup.bcell(reel, lo + k));
                    else if (k == rows) hat = nearMiss || Ease.hash(i * 7919 + reel * 131 + k * 17 + 5) < HAT_RAIN;
                    else hat = Ease.hash(i * 7919 + reel * 131 + k * 17 + 5) < HAT_RAIN;
                    if (!hat) continue;
                    float y = bY(lo + k - base);
                    if (speed > 0.45f) {
                        // file a toute vitesse : etire, avec une trainee
                        BigWin.clipQuad(L.hats, HAT_SPRITE, x - hw, y - hh * 1.2f, x + hw, y + hh * 1.2f, RY0, top + 0.1f, SZ + 0.15f, 0.85f, false);
                        BigWin.clipQuad(L.hats, HAT_SPRITE, x - hw * 0.9f, y + CH * 0.3f - hh, x + hw * 0.9f, y + CH * 0.3f + hh, RY0, top + 0.1f, SZ + 0.14f, 0.3f, false);
                    } else {
                        BigWin.clipQuad(L.hats, HAT_SPRITE, x - hw, y - hh, x + hw, y + hh, RY0, top + 0.1f, SZ + 0.15f, 1f, false);
                    }
                }
                continue;
            }
            if (lt < stop) continue;
            // colonne arretee : ses chapeaux restent poses un instant (petit rebond), puis construisent
            float since = lt - stop;
            for (int row = lo; row < Loup.BROWS; row++) {
                int c = Loup.bcell(reel, row);
                if (!spin.hat(c)) continue;
                float y = bY(row);
                int to = spin.hatTo[c];
                if (since < Loup.LAND_HIT) {
                    float bounce = Ease.spring(since, 1.1f, 0.45f) * 0.12f * CH;
                    BigWin.clipQuad(L.hats, HAT_SPRITE, x - hw, y - hh + bounce, x + hw, y + hh + bounce, RY0, top + 0.1f, SZ + 0.15f, 1.15f, false);
                    clip(L.flash, FLASH, x, y, CW * 1.3f, CH * 1.3f, top, SZ + 0.08f, 0.35f * (1 - since / Loup.LAND_HIT));
                    continue;
                }
                float t = since - Loup.LAND_HIT;
                if (to == Loup.PRIZE) {
                    // tout est deja en brique : le chapeau se change en prime
                    if (t < 18) {
                        L.labels.add(new Label(x, y + t * 0.06f, "+" + amountText(v, Loup.prizeMilli(v.rules)), GOLD, 0.85f, 1 - Ease.range(t, 12, 18)));
                        if (t < 8) clip(L.flash, FLASH, x, y, CW * 1.8f, CH * 1.8f, top, SZ + 0.08f, 1 - t / 8f);
                    }
                    continue;
                }
                // sur une brique : il rebondit en cloche jusqu'a la case qu'il va construire
                if (to != c && t < Loup.HOP) {
                    float u = t / Loup.HOP;
                    float tx = cellX(to / Loup.BROWS), ty = bY(to % Loup.BROWS);
                    float hx = Ease.lerp(x, tx, u), hy = Ease.lerp(y, ty, u) + (float) Math.sin(Math.PI * u) * CH * 1.3f;
                    BigWin.clipQuad(L.hats, HAT_SPRITE, hx - hw * 0.86f, hy - hh * 0.86f, hx + hw * 0.86f, hy + hh * 0.86f, RY0, top + 0.1f, SZ + 0.16f, 1f, false);
                }
            }
        }
    }

    /** Tour gratuit : colonne qui tourne au ralenti (-1 aucune) et ticks ecoules depuis le debut de son ralenti. */
    static float[] freeTeasing(Loup.Spin spin, float lt) {
        if (spin == null || spin.kind != Loup.FREE) return null;
        for (int reel = 1; reel < Loup.REELS; reel++) {
            if (!Loup.freeTease(spin, reel)) continue;
            int stop = Loup.freeStop(spin, reel);
            float t = lt - (stop - FREE_SLOW);
            if (t >= 0 && lt < stop) return new float[]{reel, t};
        }
        return null;
    }

    /**
     * Mise en place : les symboles du tour qui a declenche s'effacent (sauf chapeaux et scies), les chapeaux deviennent
     * des cadres de paille ; les scies filent vers la droite (copiees dans les rangees du haut en SUPER), ou deviennent des
     * briques (manoir) ; les mega chapeaux tombent du haut de la grille.
     */
    private void setupFx(Scene s, LoupView v, Loup.Spin spin, Loup.Spin prev, Loup.Plan plan, float lt, float top, Layers L, float dim, float pulse) {
        float at = lt - Loup.actionStart(spin);
        // ---- symboles du tour normal qui a declenche
        if (prev != null && prev.kind == Loup.BASE) {
            int k = 0;
            for (int c = 0; c < Loup.CELLS; c++) {
                int sym = prev.grid[c], bc = Loup.fromBase(c);
                float x = cellX(c / Loup.ROWS), y = cellY(c % Loup.ROWS);
                if (sym == Loup.HAT && (spin.hats & (1 << bc)) != 0) {
                    // chaque chapeau declencheur se pose et devient un cadre de paille
                    int formAt = 4 + k++ * Loup.FORM_STEP;
                    if (at < formAt) clip(L.mb, symbol(Loup.HAT, false), x, y, CW - 0.1f, CH - 0.1f, top, SZ + 0.045f, dim * (1.1f + 0.2f * pulse));
                    else if (at < formAt + 6) clip(L.hats, HAT_SPRITE, x, y - (at - formAt) * 0.05f, CW * (0.9f - (at - formAt) * 0.1f), CH * (0.9f - (at - formAt) * 0.1f), top, SZ + 0.15f, 1f);
                } else if (isSaw(sym) && plan.sawsShown >= 0) {
                    if (at < plan.sawsShown + 4) clip(L.mb, symbol(sym, false), x, y, CW - 0.1f, CH - 0.1f, top, SZ + 0.045f, dim * (1.1f + 0.2f * pulse));
                } else if (lt < 10 && sym != Loup.EMPTY) {
                    clip(L.mb, symbol(sym, false), x, y, CW - 0.1f, CH - 0.1f, top, SZ + 0.045f, dim * 0.4f * (1 - lt / 10f));
                }
            }
        }
        // ---- scies
        if (plan.sawsShown >= 0 && at >= plan.sawsShown) {
            boolean still = spin.bonus == Loup.B_MANSION;
            for (int[] sw : plan.saws) {
                int c0 = sw[0], start = sw[1], reel0 = c0 / Loup.BROWS, row = c0 % Loup.BROWS;
                boolean gold = sw[2] == 1, copy = sw[3] == 1;
                float y = bY(row), x = cellX(reel0);
                float appear = Ease.outBack(Ease.clamp01((at - plan.sawsShown) / 6f), 2f);
                if (copy) {
                    // la copie s'envole de la scie d'origine vers la rangee du haut
                    float from = plan.sawsShown + Loup.SAW_INTRO;
                    if (at < from) continue;
                    float u = Ease.clamp01((at - from) / (Loup.SAW_COPY - 4f));
                    y = Ease.lerp(bY(row + Loup.TOP), y, Ease.outBack(u, 1.6f));
                    appear = Ease.clamp01((at - from) / 4f);
                    if (u < 1) clip(L.cadGlow, GOLD_SAW_M, x, y, CW * 1.4f, CH * 1.4f, top, SZ + 0.13f, 0.4f * (1 - u));
                }
                if (still) {
                    // manoir : la scie tourne sur place puis devient une brique
                    if (at >= start) continue;
                } else if (at >= start) {
                    x = cellX(reel0) + (at - start) / Loup.SAW_STEP * CW;
                }
                float fadeOut = Ease.clamp01((RX1 + CW * 0.55f - x) / (CW * 0.8f));
                if (fadeOut <= 0) continue;
                float half = Math.min(CW, CH) * 0.55f * appear;
                float spinDeg = -(s.time * (at >= start && !still ? 38 : 14)) - c0 * 20;
                MeshBuilder target = L.cad;
                if (y + half > RY0 && y - half < top) spinQuad(target, gold ? GOLD_SAW_M : SAW_M, x, y, half, spinDeg, SZ + 0.14f, fadeOut);
                if (at >= start && !still && fadeOut > 0.2f) clip(L.flash, FLASH, x - CW * 0.4f, y, CW * 1.2f, CH * 0.9f, top, SZ + 0.09f, 0.35f * fadeOut);
            }
        }
        // ---- mega chapeaux
        for (int k = 0; k < spin.megas.length && k < plan.megaAt.length; k++) {
            int m = spin.megas[k];
            int drop = plan.megaAt[k], land = drop + Loup.MEGA_FALL;
            if (at < drop) continue;
            float x0 = RX0 + Loup.megaX(m) * CW, x1 = x0 + Loup.megaW(m) * CW;
            float yTop = bY(Loup.megaY(m)) + CH / 2, yBot = bY(Loup.megaY(m) + Loup.megaH(m) - 1) - CH / 2;
            float u = Ease.clamp01((at - drop) / Loup.MEGA_FALL);
            float off = (1 - Ease.inQuad(u)) * (top - yBot + 1f);
            float squash = at >= land ? 1 - 0.12f * Ease.bell(at, land, land + 6) : 1;
            float fade = 1 - Ease.range(at, land + 12, land + 18);
            if (fade <= 0) continue;
            float cx = (x0 + x1) / 2, cyr = (yTop + yBot) / 2;
            // la zone couverte s'illumine ; le chapeau garde ses proportions (carre, au centre de la zone)
            if (at >= land) clip(L.flash, FLASH, cx, cyr, (x1 - x0) * 1.15f, (yTop - yBot) * 1.15f, top, SZ + 0.05f, 0.35f * fade);
            float side = Math.min(x1 - x0, yTop - yBot) * 1.08f, h = side * squash;
            float by = cyr - side / 2 + off;
            BigWin.clipQuad(L.cad, MEGA_M, cx - side / 2, by, cx + side / 2, by + h, RY0, top, SZ + 0.16f, fade * dim, false);
            if (at >= land && at < land + 8) {
                clip(L.flash, FLASH, cx, (yTop + yBot) / 2, (x1 - x0) * 1.3f, (yTop - yBot) * 1.3f, top, SZ + 0.09f, 1 - (at - land) / 8f);
            }
        }
    }

    /** Agrandissement de la grille : eclairs le long de la jointure qui monte, puis balayage dore sur les nouvelles rangees. */
    private void expandFx(Scene s, Pose p, DrawList out, float lt, float lift) {
        if (lt > Loup.EXPAND_T + 4) return;
        float seam = SPLIT + lift;
        float rising = Ease.bell(lt, EXP_GO - 2, EXP_UP + 4);
        if (rising > 0.02f) {
            for (int k = 0; k < 5; k++) {
                float x = WX0 + 0.6f + k * 2.75f + (float) Math.sin(s.time * 0.8f + k) * 0.3f;
                out.billboard(p, Sprite.FLARE, x, seam, FZ + 0.5f, 1.6f + 0.6f * Ease.hash(k + (int) s.time), s.time * 9 + k * 40, GOLD, 0.55f * rising);
            }
            out.beam(p, 8, RY1, SZ + 0.3f, 6.5f, lift, GOLD, 0.12f * rising);
        }
        // balayage dore sur les nouvelles rangees, une fois la borne montee
        float sweep = Ease.range(lt, EXP_UP, Loup.EXPAND_T);
        if (sweep > 0 && sweep < 1) {
            float x = RX0 - 2 + (RX1 - RX0 + 4) * sweep;
            MeshBuilder b = new MeshBuilder();
            BigWin.clipQuad(b, FLASH, x - 1.6f, RY1, x + 1.6f, RY1 + EXT_H, RY1, RY1 + lift, SZ + 0.1f, 0.8f * Ease.bell(sweep, 0, 1), false);
            out.additive(p, b.buildDynamic(), GOLD, 1f);
        }
    }

    /**
     * Le grand loup surgit en bas a gauche, gonfle ses joues, puis souffle les maisons une a une.
     * @param bt ticks depuis le debut du souffle ; len : duree du souffle
     */
    private void wolf(Pose p, DrawList out, float bt, int len, float lift) {
        if (bt < 0 || bt > len) return;
        float leave = len - Loup.BLOW_TAIL + 10;
        float rise = Ease.outBack(Ease.clamp01(bt / 6f), 1.5f), sink = Ease.inCubic(Ease.range(bt, leave, leave + 9));
        boolean blowing = bt >= Loup.BLOW_WIND - 4 && bt < leave;
        float inhale = blowing ? 0 : Ease.clamp01((bt - 6) / (Loup.BLOW_WIND - 10f));
        float size = (6.2f + lift * 0.12f) * (1 + 0.16f * inhale), x = WX0 + 2.6f - 0.35f * inhale, y = RY0 + 2.2f - size * (1 - rise) - size * sink;
        float puff = blowing ? 1 + 0.05f * (float) Math.sin(bt * 1.3f) : 1;
        MeshBuilder b = new MeshBuilder();
        BigWin.clipQuad(b, blowing ? WOLF_B : WOLF_A, x - size / 2 * puff, y - size / 2, x + size / 2 * puff, y + size / 2, RY0 - 0.15f, WY1 + lift, SZ + 0.1f, 1f, false);
        out.solid(p, b.buildDynamic(), 0.6f);
    }

    /** Bandeaux : jackpots (ou le bonus en cours) en haut ; mise, gain, tours en bas. */
    private void bars(Scene s, LoupView v, Pose p, DrawList out, int i, Loup.Spin spin, float lt, boolean finished, float lift) {
        if (s.item) return;
        float z = SZ + 0.08f, yt = (RY1 + WY1) / 2 + lift, yb = (WY0 + RY0) / 2 - 0.05f;
        boolean idle = spin == null || (finished && v.since > v.totalTicks() + 200);
        float[] tz = idle ? null : teasing(spin, lt);
        float[] ftz = idle ? null : freeTeasing(spin, lt);
        boolean inBonus = !idle && (spin.kind == Loup.SETUP || spin.kind == Loup.FREE);
        if (ftz != null) {
            // tour gratuit : il ne manque qu'un chapeau pour un tour de plus
            int n = v.rules.retriggerSpins;
            String sub = n > 1 ? I18n.get("heralizcasino.loup.pour_n_tours", String.valueOf(n)) : I18n.get("heralizcasino.loup.pour_un_tour");
            float a = Ease.clamp01(ftz[1] / 4f);
            String encore = I18n.get("heralizcasino.loup.encore_chapeau");
            out.text(p, encore, 8, yt + 0.5f, z, fit(encore, 1.05f, 11.4f, true), GOLD, a * (0.7f + 0.3f * s.pulse(0.5f)), DrawList.T_CENTER | DrawList.T_BOLD);
            out.text(p, sub, 8, yt - 0.85f, z, fit(sub, 0.5f, 11.4f, false), WHITE, a * 0.9f, DrawList.T_CENTER);
        } else if (tz != null) {
            // suspense : il ne manque plus qu'un symbole pour un bonus
            int hoped = Loup.teaseSymbol(spin, (int) tz[0]);
            int missing = 6;
            for (int c = 0; c < (int) tz[0] * Loup.ROWS; c++) if (spin.grid[c] == Loup.HAT) missing--;
            String txt = hoped == Loup.SAW ? I18n.get("heralizcasino.loup.encore_scie") : missing == 1 ? I18n.get("heralizcasino.loup.encore_chapeau")
                    : I18n.get("heralizcasino.loup.encore_chapeaux", String.valueOf(missing));
            String sub = I18n.get(hoped == Loup.SAW ? "heralizcasino.loup.pour_roue" : "heralizcasino.slot.pour_tours");
            float a = Ease.clamp01(tz[1] / 4f);
            out.text(p, txt, 8, yt + 0.5f, z, fit(txt, 1.05f, 11.4f, true), teaseColor(hoped), a * (0.7f + 0.3f * s.pulse(0.5f)), DrawList.T_CENTER | DrawList.T_BOLD);
            out.text(p, sub, 8, yt - 0.85f, z, fit(sub, 0.5f, 11.4f, false), WHITE, a * 0.9f, DrawList.T_CENTER);
        } else if (blowing(spin, lt)) {
            boolean inhale = lt - Loup.blowStart(spin) < Loup.BLOW_WIND - 4;
            String txt = I18n.get(inhale ? "heralizcasino.loup.inspire" : "heralizcasino.loup.souffle");
            out.text(p, txt, 8, yt + 0.5f, z, fit(txt, 1.0f, 11.4f, true), inhale ? WHITE : AMBER, 0.75f + 0.25f * s.pulse(0.5f), DrawList.T_CENTER | DrawList.T_BOLD);
            String sub = I18n.get("heralizcasino.loup.maison_gain");
            out.text(p, sub, 8, yt - 0.85f, z, fit(sub, 0.5f, 11.4f, false), GOLD, 0.95f, DrawList.T_CENTER);
        } else if (inBonus) {
            // le bonus en cours a gauche ; a droite, paille > bois > brique
            boolean sup = Loup.isSuper(spin.bonus);
            String name = featureName(spin.bonus);
            if (sup) {
                MeshBuilder sb = new MeshBuilder();
                quadAt(sb, WX0 + 3.2f, yt + 0.1f, 5.6f, 1.4f, SUPER_M, 1f, z + 0.01f);
                out.sheet(SHEET);
                out.solid(p, sb.buildDynamic(), 0.4f + 0.3f * s.pulse(0.3f));
                out.sheet(null);
            }
            out.text(p, name, WX0 + 3.2f, sup ? yt - 1.05f : yt - 0.25f, z + 0.02f, fit(name, sup ? 0.5f : 0.75f, 5.8f, true), sup ? GOLD : AMBER, 1f,
                    DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x000000);
            MeshBuilder b = new MeshBuilder();
            float x0 = WX1 - 6.2f, icon = 1.3f;
            for (int t = 1; t <= 3; t++) {
                float x = x0 + (t - 1) * 2.15f;
                quadAt(b, x + 0.6f, yt + 0.35f, icon, icon, frame(t), 1f, z + 0.02f);
                String tier = I18n.get(TIERS[t]);
                out.text(p, tier, x + 0.6f, yt - 1.05f, z, fit(tier, 0.4f, 2f, true), TIER_COLORS[t], 1f, DrawList.T_CENTER);
                if (t < 3) out.text(p, "›", x + 1.65f, yt + 0.05f, z, 0.8f, GOLD, 1f, DrawList.T_CENTER);
            }
            out.sheet(SHEET);
            out.solid(p, b.buildDynamic(), 0.5f);
            out.sheet(null);
        } else {
            double grand = v.rules.jackpots[3] * v.rules.payScale;
            String g = "GRAND " + (v.bet > 0 ? fmt(Math.round(v.bet * grand)) : "x" + (long) grand);
            out.text(p, g, 8, yt + 0.55f, z, fit(g, 0.8f, 11f, true), GOLD, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            for (int k = 0; k < 3; k++) {
                double val = v.rules.jackpots[k] * v.rules.payScale;
                String t = JACKPOTS[k] + " " + (v.bet > 0 ? fmt(Math.round(v.bet * val)) : "x" + (long) val);
                out.text(p, t, WX0 + 2.1f + k * 4.0f, yt - 0.75f, z, fit(t, 0.5f, 3.3f, false), JACKPOT_COLORS[k], 0.95f, DrawList.T_CENTER);
            }
        }
        if (idle) {
            int phase = ((int) (s.time / 20)) % 3;
            String txt = I18n.get(phase == 0 ? "heralizcasino.loup.intro" : phase == 1 ? "heralizcasino.loup.idle_scies" : "heralizcasino.loup.idle_scie_or");
            out.text(p, txt, 8, yb, z, fit(txt, 0.6f, 11.5f, false), phase == 0 ? AMBER : phase == 1 ? BLUE : GOLD, 0.95f, DrawList.T_CENTER);
            return;
        }
        if (tz != null || ftz != null) return;
        String mise = I18n.get("heralizcasino.slot.mise", fmt(v.bet));
        out.text(p, mise, WX0 + 0.3f, yb, z, fit(mise, 0.5f, 3.2f, false), WHITE, 0.9f, 0);
        long milli = v.winSoFar(i, false);
        float bumpAt = -100;
        if (spin.kind == Loup.BASE || spin.kind == Loup.SPIN_WHEEL) {
            if (spin.kind == Loup.BASE && lt >= Loup.spinEnd(spin)) milli += spin.lineMilli;
            if (lt >= Loup.jackpotStart(spin) + 10) milli += spin.jackpotMilli;
        } else if (spin.kind == Loup.FREE) {
            if (spin.prizes > 0 && lt >= Loup.prizeAt(spin) + Loup.LAND_HIT) milli += spin.prizes * Loup.prizeMilli(v.rules);
            if ((spin.events & Loup.EV_BONUS_END) != 0) {
                if (lt >= Loup.endStart(spin)) {
                    milli = v.winSoFar(i, true);
                } else {
                    // le compteur grimpe a chaque maison que le loup emporte
                    float bt = lt - Loup.blowStart(spin);
                    int[] reveal = Loup.revealTimes(spin);
                    for (int c = 0; c < Loup.BCELLS; c++) {
                        if (reveal[c] < 0 || bt < reveal[c]) continue;
                        milli += Loup.rewardMilli(v.rules, spin.tier[c], spin.reward[c]);
                        bumpAt = Math.max(bumpAt, reveal[c]);
                    }
                    bumpAt = bt - bumpAt;
                }
            }
        }
        long won = (long) Math.floor(v.bet * (Math.min(milli, v.seq.totalMilli) / 1000.0));
        String gt = I18n.get("heralizcasino.slot.gain", fmt(won));
        float bump = bumpAt >= 0 && bumpAt < 5 ? 1 + 0.25f * Ease.bell(bumpAt, 0, 5) : 1;
        out.text(p, gt, 8.4f, yb, z, fit(gt, 0.62f * bump, 4.4f, false), won > 0 ? GOLD : WHITE, 1f, DrawList.T_CENTER);
        if (inBonus) {
            int left = spin.spinsLeft;
            if (spin.kind == Loup.FREE && (spin.events & Loup.EV_RETRIGGER) != 0 && lt < Loup.bonusSpin(spin)) left = Math.max(0, left - v.rules.retriggerSpins);
            boolean last = left == 0;
            String right = last ? I18n.get("heralizcasino.slot.dernier_tour") : I18n.get("heralizcasino.slot.tours_n", String.valueOf(left));
            out.text(p, right, WX1 - 0.3f, yb, z, fit(right, 0.5f, 3f, false), last ? RED : GREEN, 1f, DrawList.T_RIGHT);
        }
    }

    /** Le loup est en train d'inspirer ou de souffler les maisons (avant l'annonce du total). */
    static boolean blowing(Loup.Spin spin, float lt) {
        if (spin == null || spin.kind != Loup.FREE || (spin.events & Loup.EV_BONUS_END) == 0) return false;
        float bt = lt - Loup.blowStart(spin);
        return bt >= 2 && bt < Loup.blow(spin) - Loup.BLOW_TAIL + 10;
    }

    /** Voile sombre sur la grille (derriere une annonce). */
    private void veil(Pose p, DrawList out, float a, float lift) {
        if (a <= 0.02f) return;
        p.push().translate((RX0 + RX1) / 2, (RY0 + RY1 + lift) / 2, SZ + 0.2f).scale(RX1 - RX0 + 0.3f, RY1 - RY0 + lift + 0.1f, 1);
        out.translucent(p, veil, 0x000000, a);
        p.pop();
    }

    /** Annonces : gains, la roue, jackpots, bonus, mise en place, tours gratuits, souffle final, gain maximum. */
    private void overlays(Scene s, LoupView v, Pose p, DrawList out, int i, Loup.Spin spin, float lt, float lift) {
        if (spin == null || s.item) return;
        float cx = 8, cy = (RY0 + RY1 + lift) / 2, z = SZ + 0.3f;
        float mascotTop = WY1 + lift - 0.1f;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        if (spin.kind == Loup.BASE || spin.kind == Loup.SPIN_WHEEL) {
            if (spin.kind == Loup.BASE) {
                // gain des facons
                float t = lt - Loup.spinEnd(spin);
                int show = Loup.lineShow(spin);
                if (spin.lineMilli > 0 && t >= 0 && t < show) {
                    long amount = (long) Math.floor(v.bet * spin.lineMilli / 1000.0);
                    int tier = Rotten.bigTier(spin.lineMilli);
                    if (tier > 0) {
                        BigWin.mascot(p, out, t, show, WOLF_A, WOLF_B, null, 8, cy - 0.3f, 7.2f, RY0 - 0.15f, mascotTop, SZ + 0.14f, FZ, 0, null);
                        BigWin.text(p, out, t, show, tier, amount, 8, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, tier >= 3 ? PURPLE : tier == 2 ? RED : AMBER, GOLD);
                    } else {
                        String txt = "+" + fmt(amount);
                        out.text(p, txt, cx, cy, z, fit(txt, 1.8f * Ease.outBack(Math.min(1f, t / 5f), 2.2f), 10f, true), GOLD, 1f, flags, 0x2A1400);
                    }
                }
            } else if (lt < Loup.HOUSE_INTRO) {
                // une maison de brique cachait un tour de roue
                float a = Ease.clamp01(lt / 4f) * (1 - Ease.range(lt, Loup.HOUSE_INTRO - 5, Loup.HOUSE_INTRO));
                veil(p, out, 0.6f * a, lift);
                String cachait = I18n.get("heralizcasino.loup.maison_cachait"), laRoue = I18n.get("heralizcasino.loup.la_roue");
                out.text(p, cachait, cx, cy + 1.1f, z, fit(cachait, 1.1f, 11f, true), WHITE, a, flags, 0x000000);
                out.text(p, laRoue, cx, cy - 1.0f, z, fit(laRoue, 2f, 11f, true) * Ease.outBack(Ease.clamp01(lt / 6f), 2.4f), 0xFF8AE8, a, flags, 0x000000);
            }
            // la roue
            if (spin.wheelSeg >= 0 || spin.superSeg >= 0) {
                for (int k = 0; k < 2; k++) {
                    if (k == 0 && spin.wheelSeg < 0) continue;
                    if (k == 1 && spin.superSeg < 0) continue;
                    float w = k == 0 ? lt - Loup.wheelStart(spin) : lt - Loup.superStart(spin) - Loup.SUPER_INTRO;
                    float from = k == 1 ? -Loup.SUPER_INTRO : 0;
                    if (w < from || w >= Loup.WHEEL_T) continue;
                    boolean sup = k == 1;
                    if (sup) veil(p, out, 0.7f * Ease.clamp01((w - from) / 6f) * (1 - Ease.range(w, Loup.WHEEL_T - 10, Loup.WHEEL_T)), lift);
                    if (w < Loup.WHEEL_STOP + 6) {
                        String title = I18n.get(sup ? "heralizcasino.loup.super_roue" : "heralizcasino.loup.la_roue");
                        float a = Math.min(1f, (w - from) / 4f);
                        out.text(p, title, cx, cy + 1.2f, z, fit(title, 2f, 11f, true) * Ease.outBack(Ease.clamp01((w - from) / 6f), 2.2f), sup ? GOLD : AMBER, a, flags, 0x000000);
                        String sub = I18n.get(sup && w < 0 ? (spin.events & Loup.EV_DIRECT) != 0 ? "heralizcasino.loup.or_ouvre" : "heralizcasino.loup.niveau_sup"
                                : "heralizcasino.loup.regarde_roue");
                        out.text(p, sub, cx, cy - 1.2f, z, fit(sub, 0.7f, 11f, true), WHITE, a * (0.6f + 0.4f * s.pulse(0.3f)), flags, 0x000000);
                    } else {
                        String res = segTitle(sup ? Loup.SUPER_WHEEL[spin.superSeg] : Loup.WHEEL[spin.wheelSeg]);
                        out.text(p, res, cx, cy, z, fit(res, 1.7f, 11f, true) * Ease.outBack(Ease.clamp01((w - Loup.WHEEL_STOP - 6) / 6f), 2.5f), GOLD, 1f, flags, 0x2A1400);
                    }
                }
            }
            // jackpot de la roue
            if ((spin.events & Loup.EV_JACKPOT) != 0) {
                float j = lt - Loup.jackpotStart(spin);
                if (j >= 0 && j < Loup.JACKPOT_T) {
                    float m = j * Rotten.BIG_WIN_TICKS / Loup.JACKPOT_T;
                    BigWin.mascot(p, out, m, Rotten.BIG_WIN_TICKS, WOLF_A, WOLF_B, null, 8, cy - 0.3f, 7.2f, RY0 - 0.15f, mascotTop, SZ + 0.14f, FZ, 0, null);
                    int jk = Math.max(0, spin.jackpot);
                    String title = I18n.get("heralizcasino.slot.jackpot", JACKPOTS[jk]);
                    float a = Ease.clamp01(j / 4f) * (1 - Ease.range(j, Loup.JACKPOT_T - 6, Loup.JACKPOT_T));
                    out.text(p, title, cx, RY1 - 0.9f, z + 0.1f, fit(title, 1.8f, 11.5f, true) * Ease.outBack(Ease.clamp01(j / 6f), 2.4f), JACKPOT_COLORS[jk], a, flags, 0x000000);
                    long amount = (long) Math.floor(v.bet * spin.jackpotMilli / 1000.0);
                    String txt = fmt((long) Math.floor(amount * Ease.outCubic(Ease.clamp01((j - 4) / 40f))));
                    out.text(p, txt, cx, RY0 + 1.2f, z + 0.1f, fit(txt, 2.3f, 7.5f, true), GOLD, a, flags, 0x2A1400);
                }
            }
            // annonce du bonus
            if ((spin.events & Loup.EV_BONUS) != 0) {
                float e = lt - Loup.introStart(spin);
                if (e >= 0 && e < Loup.BONUS_INTRO) {
                    float a = Math.min(1f, e / 4f) * (1 - Ease.range(e, Loup.BONUS_INTRO - 6, Loup.BONUS_INTRO));
                    boolean sup = Loup.isSuper(spin.bonus);
                    String title = I18n.get("heralizcasino.slot.annonce", featureName(spin.bonus));
                    veil(p, out, 0.72f * a, lift);
                    out.text(p, title, cx, cy + 2.0f, z, fit(title, 1.5f, 11.2f, true) * Ease.outBack(Ease.clamp01(e / 6f), 2f), sup ? GOLD : AMBER, a, flags, 0x2A1400);
                    String[] sub = intro(spin.bonus);
                    out.text(p, sub[0], cx, cy + 0.3f, z, fit(sub[0], 0.66f, 11.2f, true), WHITE, a, flags, 0x000000);
                    out.text(p, sub[1], cx, cy - 0.8f, z, fit(sub[1], 0.66f, 11.2f, true), WHITE, a, flags, 0x000000);
                    out.text(p, sub[2], cx, cy - 2.0f, z, fit(sub[2], 0.62f, 11.2f, true), AMBER, a, flags, 0x000000);
                }
            }
        } else if (spin.kind == Loup.SETUP) {
            setupOverlay(s, v, p, out, spin, lt, lift, cx, cy, z, flags);
        } else {
            freeOverlay(s, v, p, out, spin, lt, lift, cx, cy, z, flags, mascotTop);
        }
        if ((spin.events & Loup.EV_MAXWIN) != 0 && lt >= end(spin)) {
            long total = (long) Math.floor(v.bet * v.seq.totalMilli / 1000.0);
            float m = lt - end(spin);
            if (m < Rotten.BIG_WIN_TICKS) {
                BigWin.mascot(p, out, m, Rotten.BIG_WIN_TICKS, WOLF_A, WOLF_B, null, 8, cy - 0.3f, 7.2f, RY0 - 0.15f, mascotTop, SZ + 0.14f, FZ, 0, null);
                BigWin.text(p, out, m, Rotten.BIG_WIN_TICKS, 4, total, 8, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, GOLD, GOLD);
            } else {
                BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1 + lift, SZ + 0.15f, BigWin.SHADE * Ease.clamp01((m - Rotten.BIG_WIN_TICKS) / 5f));
                String maxTxt = I18n.get("heralizcasino.slot.gain_max");
                out.text(p, maxTxt, cx, cy + 1.2f, z, fit(maxTxt, 1.8f, 11f, true), GOLD, 1f, flags, 0x2A1400);
                out.text(p, fmt(total), cx, cy - 1.0f, z, fit(fmt(total), 2.2f, 11f, true), GOLD, 1f, flags, 0x2A1400);
            }
        }
    }

    /** Les trois lignes d'explication d'un bonus. */
    static String[] intro(int bonus) {
        String[] keys = switch (bonus) {
            case Loup.B_BUZZ -> new String[]{"buzz_1", "buzz_2", "puis_6"};
            case Loup.B_MEGA -> new String[]{"mega_1", "mega_2", "puis_6"};
            case Loup.B_MANSION -> new String[]{"manoir_1", "manoir_2", "six_tours"};
            case Loup.B_SUPER_BUZZ -> new String[]{"grille_5x6", "super_buzz_2", "puis_6"};
            case Loup.B_SUPER_MEGA -> new String[]{"grille_5x6", "super_mega_2", "puis_6"};
            case Loup.B_SUPER_MANSION -> new String[]{"grille_5x6", "super_manoir_2", "six_tours_brique"};
            default -> new String[]{"cadre_1", "cadre_2", "cadre_3"};
        };
        String[] lines = new String[keys.length];
        for (int k = 0; k < keys.length; k++) lines[k] = I18n.get("heralizcasino.loup." + keys[k]);
        return lines;
    }

    /** Mise en place : la grille s'agrandit, l'action du bonus, puis l'annonce des tours gratuits. */
    private void setupOverlay(Scene s, LoupView v, Pose p, DrawList out, Loup.Spin spin, float lt, float lift, float cx, float cy, float z, int flags) {
        if ((spin.events & Loup.EV_EXPAND) != 0 && lt < Loup.EXPAND_T) {
            float a = Ease.clamp01(lt / 4f) * (1 - Ease.range(lt, Loup.EXPAND_T - 8, Loup.EXPAND_T));
            boolean up = lt >= EXP_UP;
            String t1 = I18n.get(up ? "heralizcasino.loup.grille_5x6_annonce" : "heralizcasino.loup.super"),
                    t2 = I18n.get(up ? "heralizcasino.loup.trente_cases" : "heralizcasino.loup.borne");
            float pop = up ? Ease.outBack(Ease.clamp01((lt - EXP_UP) / 6f), 2.4f) : Ease.outBack(Ease.clamp01(lt / 6f), 2.2f);
            out.text(p, t1, cx, cy + 1.3f, z, fit(t1, 2.1f, 11f, true) * pop * (1 + 0.05f * (float) Math.sin(s.time * 0.8f)), GOLD, a, flags, 0x3A1400);
            out.text(p, t2, cx, cy - 1.0f, z, fit(t2, 0.8f, 11f, true), WHITE, a * (0.7f + 0.3f * s.pulse(0.4f)), flags, 0x000000);
            return;
        }
        Loup.Plan plan = v.plan(v.seq.spins.indexOf(spin));
        float at = lt - Loup.actionStart(spin);
        // annonce de l'action, le temps qu'elle demarre
        String what = switch (spin.bonus) {
            case Loup.B_BUZZ, Loup.B_SUPER_BUZZ -> I18n.get("heralizcasino.loup.scies_decoupent");
            case Loup.B_SUPER_MANSION -> I18n.get("heralizcasino.loup.scies_briques");
            case Loup.B_MANSION -> I18n.get("heralizcasino.loup.tout_brique");
            case Loup.B_MEGA, Loup.B_SUPER_MEGA -> I18n.get("heralizcasino.loup.mega_chapeaux");
            default -> "";
        };
        float from = plan.sawsShown >= 0 ? plan.sawsShown : plan.formEnd;
        if (!what.isEmpty() && at >= from && at < from + 18) {
            float a = Ease.clamp01((at - from) / 3f) * (1 - Ease.range(at, from + 12, from + 18));
            out.text(p, what, cx, RY1 + lift - 1.0f, z, fit(what, 1.1f, 11.4f, true) * Ease.outBack(Ease.clamp01((at - from) / 5f), 2.2f), GOLD, a, flags, 0x2A1400);
        }
        // annonce des tours gratuits
        float f = at - plan.end;
        if (f >= 0 && f < Loup.FS_INTRO) {
            float a = Ease.clamp01(f / 4f) * (1 - Ease.range(f, Loup.FS_INTRO - 6, Loup.FS_INTRO));
            veil(p, out, 0.55f * a, lift);
            String t1 = I18n.get("heralizcasino.slot.n_tours_gratuits", String.valueOf(spin.spinsLeft));
            out.text(p, t1, cx, cy + 0.8f, z, fit(t1, 1.7f, 11f, true) * Ease.outBack(Ease.clamp01(f / 6f), 2.4f), GREEN, a, flags, 0x000000);
            String t2 = I18n.get(Loup.isMansion(spin.bonus) ? "heralizcasino.loup.chapeau_brique" : "heralizcasino.loup.trois_chapeaux");
            out.text(p, t2, cx, cy - 1.0f, z, fit(t2, 0.7f, 11f, true), WHITE, a, flags, 0x000000);
        }
    }

    /** Tours gratuits : dernier tour, relance, maisons, souffle, total, jackpots des maisons. */
    private void freeOverlay(Scene s, LoupView v, Pose p, DrawList out, Loup.Spin spin, float lt, float lift, float cx, float cy, float z, int flags, float mascotTop) {
        if ((spin.events & Loup.EV_LAST) != 0 && lt < Loup.LAST_T) {
            float a = Math.min(1f, lt / 3f) * (1 - Ease.range(lt, Loup.LAST_T - 5, Loup.LAST_T));
            veil(p, out, 0.6f * a, lift);
            String last = I18n.get("heralizcasino.slot.dernier_tour_annonce");
            out.text(p, last, cx, cy, z, fit(last, 1.9f, 11f, true) * Ease.outBack(Ease.clamp01(lt / 6f), 2.4f), RED, a, flags, 0x000000);
        }
        if ((spin.events & Loup.EV_RETRIGGER) != 0) {
            float r = lt - Loup.bonusSpin(spin);
            if (r >= 0 && r < Loup.RETRIG) {
                float a = Math.min(1f, r / 3f) * (1 - Ease.range(r, Loup.RETRIG - 5, Loup.RETRIG));
                String txt = I18n.get(v.rules.retriggerSpins > 1 ? "heralizcasino.slot.plus_tours_annonce" : "heralizcasino.slot.plus_tour_annonce", String.valueOf(v.rules.retriggerSpins));
                veil(p, out, 0.55f * a, lift);
                out.text(p, txt, cx, cy, z, fit(txt, 1.8f, 11f, true) * Ease.outBack(Ease.clamp01(r / 5f), 2.4f), GREEN, a, flags, 0x000000);
            }
        }
        if ((spin.events & Loup.EV_BONUS_END) == 0) return;
        float b0 = lt - Loup.buildStart(spin);
        if (b0 >= 0 && b0 < Loup.build(spin)) {
            float a = Ease.clamp01(b0 / 3f) * (1 - Ease.range(b0, Loup.build(spin) - 5, Loup.build(spin)));
            String t1 = I18n.get("heralizcasino.loup.cadres_maisons");
            out.text(p, t1, cx, RY1 + lift - 0.9f, z, fit(t1, 0.9f, 11.4f, true), GOLD, a, flags, 0x2A1400);
        }
        float bt = lt - Loup.blowStart(spin);
        int blowLen = Loup.blow(spin);
        // un jackpot ou la roue sort d'une maison : annonce
        int[] reveal = Loup.revealTimes(spin);
        for (int c = 0; c < Loup.BCELLS; c++) {
            int rw = spin.reward[c];
            if (reveal[c] < 0 || rw < Loup.R_MINI) continue;
            float j = bt - reveal[c];
            if (j < 2 || j >= Loup.REVEAL_JACK + 4) continue;
            float a = Ease.clamp01((j - 2) / 3f) * (1 - Ease.range(j, Loup.REVEAL_JACK, Loup.REVEAL_JACK + 4));
            String txt = rw == Loup.R_WHEEL ? I18n.get("heralizcasino.loup.la_roue") : I18n.get("heralizcasino.loup.jackpot_annonce", rewardText(v, spin.tier[c], rw));
            out.text(p, txt, cx, cy + 0.2f, z + 0.05f, fit(txt, 1.7f, 11f, true) * Ease.outBack(Ease.clamp01((j - 2) / 5f), 2.4f), rewardColor(spin.tier[c], rw), a, flags, 0x000000);
        }
        // toutes les maisons sont parties : le total
        float tt = bt - (blowLen - Loup.BLOW_TAIL + 12);
        if (tt >= 0 && bt < blowLen) {
            long houses = 0;
            for (int c = 0; c < Loup.BCELLS; c++) houses += Loup.rewardMilli(v.rules, spin.tier[c], spin.reward[c]);
            String txt = "+" + fmt((long) Math.floor(v.bet * houses / 1000.0));
            out.text(p, txt, cx, cy, z, fit(txt, 1.8f * Ease.outBack(Ease.clamp01(tt / 5f), 2f), 10f, true), GOLD, 1f, flags, 0x2A1400);
        }
        float t = lt - Loup.endStart(spin);
        int show = Loup.endShow(spin);
        // gain maximum : son annonce prend le relais
        if (t < 0 || ((spin.events & Loup.EV_MAXWIN) != 0 && lt >= end(spin))) return;
        long amount = (long) Math.floor(v.bet * spin.winMilli / 1000.0);
        int tier = Rotten.bigTier(spin.winMilli);
        if (tier > 0 && t < show) {
            BigWin.mascot(p, out, t, show, WOLF_A, WOLF_B, null, 8, cy - 0.3f, 7.2f, RY0 - 0.15f, mascotTop, SZ + 0.14f, FZ, 0, null);
            BigWin.text(p, out, t, show, tier, amount, 8, RY1 + lift - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, tier >= 3 ? PURPLE : tier == 2 ? RED : AMBER, GOLD);
            return;
        }
        // le total reste affiche jusqu'a la partie suivante, sur un fond assombri (les boutons du joueur reviennent)
        float k = tier > 0 ? t - show : t;
        BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1 + lift, SZ + 0.15f, BigWin.SHADE * Ease.clamp01(k / 5f));
        out.text(p, I18n.get("heralizcasino.loup.gain_bonus"), cx, cy + 1.2f, z, 1f, WHITE, Ease.clamp01(k / 3f), flags, 0x000000);
        String txt = fmt(amount);
        out.text(p, txt, cx, cy - 1.0f, z, fit(txt, 2.2f * Ease.outBack(Math.min(1f, k / 6f), 2f), 10f, true), GOLD, Ease.clamp01(k / 3f), flags, 0x2A1400);
    }

    // =================================================================== camera
    @Override
    public void camera(LoupView v, Scene s, int mode, Shot out) {
        float[] at = v.at(v.since);
        if (at != null && v.since < v.totalTicks() + 40) {
            int i = (int) at[0];
            Loup.Spin f = v.seq.spins.get(i);
            float lt = at[1];
            float lift = EXT_H * expansion(v, i, lt);
            if (hasWheel(f) && lt >= Loup.wheelStart(f) - 4 && lt < Loup.jackpotStart(f) + 8) {
                // la roue tourne : on leve les yeux, en gros plan ; la camera se rapproche quand elle va s'arreter ; la super
                // roue s'avance au centre de l'image
                Wheel w = wheelAt(v, i, lt);
                float push = w.turn < 0 || w.w < 0 ? 0 : Ease.inOutSine(Ease.range(w.w, Loup.WHEEL_STOP - 50, Loup.WHEEL_STOP - 8))
                        * (1 - Ease.inOutSine(Ease.range(w.w, Loup.WHEEL_STOP + 4, Loup.WHEEL_STOP + 12)));
                float pr = w.present;
                out.set(8, WHY - 2.9f + 1.3f * push + 0.6f * pr, 36.4f - 1.2f * push + 2.5f * pr, 8, WHY - 2.6f + 1.35f * push + 1.2f * pr, 15 + 2.6f * pr,
                        46 - 4 * push + 2 * pr);
                return;
            }
            if (f.kind == Loup.BASE || (f.kind == Loup.FREE && lift < 0.01f)) {
                // suspense : on se rapproche du rouleau (ou de la colonne de chapeaux) qui tourne au ralenti
                float[] tz = f.kind == Loup.BASE ? teasing(f, lt) : freeTeasing(f, lt);
                if (tz != null && mode != CAM_MENU) {
                    float k = Ease.inOutSine(Ease.clamp01(tz[1] / 10f));
                    float x = Ease.lerp(8, cellX((int) tz[0]) * 0.55f + 8 * 0.45f, k);
                    out.set(x, 24.6f + 1.7f * k, 36.5f - 5.5f * k, x, 23.2f + 2.1f * k, SZ, 44 - 4 * k);
                    return;
                }
            }
            if (lift > 0.01f || (f.kind == Loup.SETUP && (f.events & Loup.EV_EXPAND) != 0)) {
                // grille agrandie : la camera recule et monte avec l'ecran ; pendant l'agrandissement, petit recul de plus
                float back = f.kind == Loup.SETUP && (f.events & Loup.EV_EXPAND) != 0 ? 4f * Ease.bell(lt, 0, Loup.EXPAND_T + 10) : 0;
                out.set(8, 24.6f + 0.6f * lift + 0.5f * back, 36.5f + 0.9f * lift + back, 8, 23.4f + 0.6f * lift, SZ, 44 + 0.6f * lift + back);
                return;
            }
        }
        if (mode == CAM_MENU) out.set(8, 32, 49, 8, 29.5f, 13, 54);
        else out.set(8, 24.6f, 36.5f, 8, 23.2f, SZ, 44);
    }

    @Override
    public boolean zoomable() {
        return true;
    }

    /** Gain du bonus (apres l'annonce de gros gain) ou gain maximum a l'ecran : les boutons du joueur reviennent. */
    @Override
    public float summary(LoupView v, Scene s) {
        float[] at = v.at(v.since);
        if (at == null || v.since > v.totalTicks() + 200 || (int) at[0] != v.seq.spins.size() - 1) return 0;
        Loup.Spin spin = v.seq.spins.get((int) at[0]);
        float lt = at[1];
        if ((spin.events & Loup.EV_MAXWIN) != 0) return Ease.clamp01((lt - end(spin) - Rotten.BIG_WIN_TICKS) / 5f);
        if (spin.kind != Loup.FREE || (spin.events & Loup.EV_BONUS_END) == 0) return 0;
        float t = lt - Loup.endStart(spin);
        if (Rotten.bigTier(spin.winMilli) > 0) t -= Loup.endShow(spin);
        return Ease.clamp01(t / 5f);
    }

    /**
     * Image de jeu de l'ecran en entier (jackpots du haut et bandeau du bas compris, grille agrandie en SUPER), cadree
     * par le zoom du joueur et pendant l'intro ; pas pendant la roue ni pendant que la borne s'allonge (la camera suit
     * alors la mise en scene).
     */
    @Override
    public float[] screenRect(LoupView v, Scene s) {
        float[] at = v.at(v.since);
        float lift = 0;
        if (at != null && v.since < v.totalTicks() + 40) {
            int i = (int) at[0];
            Loup.Spin f = v.seq.spins.get(i);
            float lt = at[1];
            if (hasWheel(f) && lt >= Loup.wheelStart(f) - 4 && lt < Loup.jackpotStart(f) + 8) return null;
            if (f.kind == Loup.SETUP && (f.events & Loup.EV_EXPAND) != 0 && lt < Loup.EXPAND_T + 6) return null;
            lift = EXT_H * expansion(v, i, lt);
        }
        return new float[]{WX0, WY0, WX1, WY1 + lift, SZ};
    }

    @Override
    public float screenFocus(LoupView v, Scene s) {
        return Intro.focus(v.intro, v.at(v.since) != null && v.since < v.totalTicks() + 40);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, LoupView v, FxSink fx, Random r) {
        float[] at = v.at(v.since);
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        float lift = at == null || v.since > v.totalTicks() + 40 ? 0 : EXT_H * expansion(v, i, lt);
        if (r.nextInt(8) == 0) fx.particle(FxSink.SMOKE, PIPE[0] + rnd(r, 0.3f), PIPE_TOP + 0.2f + lift, PIPE[1] + rnd(r, 0.3f), rnd(r, 0.01f), 0.04f, rnd(r, 0.01f), 0x9A948A, 0.9f, 60);
        if (r.nextInt(300) == 0) fx.sound(Snd.OINK, 8, 10, 15, 0.5f, 0.9f + r.nextFloat() * 0.3f);
        // intro : les cochons, le hurlement, le souffle, la maison de paille qui s'envole
        float it = Intro.time(v.intro, at != null && v.since < v.totalTicks() + 40, s.item);
        if (it >= 0) {
            float iy = (RY0 + RY1) / 2;
            Intro.sounds(fx, it, Loup.REELS, RX0, CW, iy, 15);
            if (at(it, 10)) fx.sound(Snd.OINK, 10, iy, 15, 0.7f, 1.2f);
            if (at(it, 16)) fx.sound(Snd.HOWL, 4, iy, 15, 0.9f, 1.05f);
            if (at(it, 26)) fx.sound(Snd.TENSION, 8, iy, 15, 0.6f, 1f);
            if (at(it, 34)) fx.sound(Snd.WIND, 6, iy, 15, 1f, 0.9f);
            if (at(it, 40)) {
                fx.sound(Snd.CRASH, 7, iy - 2, 15, 0.8f, 1.2f);
                for (int q = 0; q < 10; q++) fx.particle(FxSink.LEAF, 7 + rnd(r, 0.8f), iy - 2.5f + rnd(r, 0.6f), FZ + 0.3f, 0.06f + r.nextFloat() * 0.08f, 0.05f + r.nextFloat() * 0.05f, rnd(r, 0.02f), 0xE0C060, 0.3f, 30);
            }
            if (at(it, 46)) fx.sound(Snd.OINK, 13.5f, iy, 15, 0.8f, 1.5f);
        }
        if (at == null || v.since > v.totalTicks() + 10) return;
        Loup.Spin spin = v.seq.spins.get(i);
        float sy = (RY0 + RY1 + lift) / 2;
        switch (spin.kind) {
            case Loup.BASE -> baseFx(v, spin, lt, fx, r, sy);
            case Loup.SPIN_WHEEL -> {
                if (at(lt, 0)) {
                    fx.sound(Snd.HOWL, 8, sy, 15, 0.8f, 1.1f);
                    fx.sound(Snd.BELL, 8, sy, 15, 0.9f, 1.3f);
                }
            }
            case Loup.SETUP -> setupFx(v, i, spin, lt, fx, r, sy, lift);
            default -> freeFx(v, i, spin, lt, fx, r, sy, lift);
        }
        if (hasWheel(spin)) wheelFx(v, i, spin, lt, fx, r, sy);
        if ((spin.events & Loup.EV_JACKPOT) != 0 && at(lt, Loup.jackpotStart(spin))) {
            fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
            fx.sound(Snd.HOWL, 8, sy, 15, 1f, 0.8f);
            fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, RED}, 40, 0.4f);
        }
        if ((spin.events & Loup.EV_BONUS) != 0 && at(lt, Loup.introStart(spin))) {
            fx.sound(Snd.BONUS, 8, sy, 15, 1f, Loup.isSuper(spin.bonus) ? 0.9f : 1.2f);
            fx.sound(Snd.OINK, 8, sy, 15, 1f, 1f);
            burst(fx, r, 8, sy, 14.5f, AMBER, GOLD, 30, 0.5f, 0.5f);
        }
        if ((spin.events & Loup.EV_MAXWIN) != 0 && at(lt, end(spin))) {
            fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
            fx.sound(Snd.HOWL, 8, sy, 15, 1f, 0.7f);
            fountain(fx, r, 8, 34 + lift, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, RED, PURPLE}, 50, 0.4f);
        }
    }

    private void baseFx(LoupView v, Loup.Spin spin, float lt, FxSink fx, Random r, float sy) {
        if (at(lt, 0)) fx.sound(Snd.REEL_START, 8, sy, 15, 0.7f, 1.2f);
        for (int reel = 0; reel < Loup.REELS; reel++) {
            int stop = Loup.stopTick(spin, reel);
            boolean teased = Loup.tease(spin, reel);
            if (teased) {
                float t = lt - (stop - SLOW);
                Tease.sounds(fx, t, SLOW, cellX(reel), sy, 15);
                if (t >= 0 && lt < stop && ((int) t) % 4 == 0) fx.sound(Snd.REEL_TICK, cellX(reel), sy, 15, 0.3f, 1.1f);
            }
            if (!at(lt, stop)) continue;
            fx.sound(Snd.REEL_STOP, cellX(reel), sy, 15, 0.45f, 1.2f + reel * 0.05f);
            int hoped = teased ? Loup.teaseSymbol(spin, reel) : -1;
            boolean hit = false;
            for (int row = 0; row < Loup.ROWS; row++) {
                int sym = spin.grid[Loup.cell(reel, row)];
                if (sym == Loup.SAW) fx.sound(Snd.SCATTER, cellX(reel), cellY(row), 15, 1f, 0.8f + reel * 0.1f);
                if (sym == Loup.GOLD) {
                    fx.sound(Snd.SPARKLE, cellX(reel), cellY(row), 15, 1f, 1.2f);
                    fx.sound(Snd.BELL, cellX(reel), cellY(row), 15, 0.8f, 1.6f);
                    burst(fx, r, cellX(reel), cellY(row), SZ + 0.6f, GOLD, 0xFFFFFF, 16, 0.3f, 0.35f);
                }
                if (sym == Loup.HAT) fx.sound(Snd.CHIP, cellX(reel), cellY(row), 15, 0.7f, 1.3f);
                if (sym == Loup.WOLF) fx.sound(Snd.HOWL, cellX(reel), cellY(row), 15, 0.35f, 1.2f);
                if (sym == hoped || (hoped == Loup.SAW && isSaw(sym))) {
                    hit = true;
                    burst(fx, r, cellX(reel), cellY(row), SZ + 0.6f, teaseColor(hoped), 0xFFFFFF, 22, 0.35f, 0.4f);
                }
            }
            if (teased) fx.sound(hit ? Snd.BELL : Snd.MISS, cellX(reel), sy, 15, hit ? 0.9f : 0.7f, hit ? 1.4f : 1f);
        }
        if (spin.lineMilli > 0 && at(lt, Loup.spinEnd(spin))) {
            boolean big = Rotten.bigTier(spin.lineMilli) > 0;
            fx.sound(big ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
            if (big) fx.sound(Snd.HOWL, 8, sy, 15, 1f, 0.9f);
        }
    }

    /** La roue et la super roue : cliquetis des chevilles, coeur qui bat, resultat ; presentation de la super roue. */
    private void wheelFx(LoupView v, int i, Loup.Spin spin, float lt, FxSink fx, Random r, float sy) {
        Wheel now = wheelAt(v, i, lt);
        if (now.turn < 0) return;
        float w = now.w;
        float ox = WHX, oy = WHY, oz = WHZ;
        if (now.turn == 1 && w < 0) {
            // presentation de la super roue
            float u = w + Loup.SUPER_INTRO;
            if (at(u, 0)) {
                fx.sound(Snd.TENSION, 8, 40, 15, 1f, 0.8f);
                fx.sound(Snd.THUNDER, 8, 40, 15, 0.8f, 1.1f);
            }
            if (at(u, 6)) fx.sound(Snd.WHOOSH, ox, oy, oz, 1f, 0.7f);
            if (at(u, 16)) {
                fx.sound(Snd.WIN_BIG, ox, oy, oz, 1f, 0.8f);
                fx.sound(Snd.BELL, ox, oy, oz, 1f, 0.9f);
                int[] cols = {GOLD, AMBER, 0xFFFFFF};
                for (int q = 0; q < 40; q++) fx.particle(FxSink.CONFETTI, ox + rnd(r, 6f), oy + WHR + 2, oz + 2.5f + rnd(r, 0.6f), rnd(r, 0.15f),
                        0.06f + r.nextFloat() * 0.14f, rnd(r, 0.05f), cols[r.nextInt(3)], 0.3f + r.nextFloat() * 0.2f, 40 + r.nextInt(20));
            }
            if (u > 20 && ((int) u) % 6 == 0 && at(u, (int) u)) fx.sound(Snd.DRUM, 8, 40, 15, 0.5f, 1f + u * 0.01f);
            return;
        }
        if (at(w, 0)) fx.sound(Snd.BONUS, 8, 40, 15, 1f, now.turn == 0 ? 1.1f : 1.4f);
        if (at(w, Loup.WHEEL_GO)) fx.sound(Snd.WHEEL_START, ox, oy, oz, 1f, 1f);
        if (w >= Loup.WHEEL_GO && w < Loup.WHEEL_STOP + 1) {
            Wheel before = wheelAt(v, i, lt - 1);
            if (pegsPassed(before.angle, now.angle) > 0) {
                float u = (w - Loup.WHEEL_GO) / (Loup.WHEEL_STOP - Loup.WHEEL_GO);
                fx.sound(Snd.WHEEL_TICK, ox, oy + WHR, oz, 0.45f + 0.4f * u, 1.45f - 0.6f * u);
            }
            float left = Loup.WHEEL_STOP - w;
            if (left > 0 && left < 44 && Suspense.beat((int) (44 - left), 44)) fx.sound(Snd.HEARTBEAT, ox, oy, oz, 1f, 0.9f + 0.5f * (1 - left / 44f));
        }
        if (at(w, Loup.WHEEL_STOP)) fx.sound(Snd.WHEEL_STOP, ox, oy, oz, 1f, 1f);
        if (at(w, Loup.WHEEL_STOP + 6)) {
            int code = now.turn == 1 ? Loup.SUPER_WHEEL[spin.superSeg] : Loup.WHEEL[spin.wheelSeg];
            fx.sound(Snd.WIN_BIG, 8, 40, 15, 0.8f, 1.1f);
            fx.sound(Snd.BELL, ox, oy, oz, 0.9f, 1.2f);
            if (code == Loup.W_UPGRADE) fx.sound(Snd.TENSION, ox, oy, oz, 1f, 1.2f);
            burst(fx, r, ox, oy + WHR * 0.6f, oz + 0.8f, segColor(code), GOLD, 36, 0.5f, 0.45f);
            int[] cols = {GOLD, AMBER, segColor(code)};
            for (int q = 0; q < 26; q++) fx.particle(FxSink.CONFETTI, ox + rnd(r, 5.5f), oy + WHR + 1.5f, oz + 0.6f + rnd(r, 0.5f), rnd(r, 0.12f),
                    0.05f + r.nextFloat() * 0.12f, rnd(r, 0.05f), cols[r.nextInt(3)], 0.3f + r.nextFloat() * 0.2f, 36 + r.nextInt(20));
        }
    }

    /** Mise en place : la borne s'agrandit (grincements, poussiere), cadres poses, scies, mega chapeaux, briques. */
    private void setupFx(LoupView v, int i, Loup.Spin spin, float lt, FxSink fx, Random r, float sy, float lift) {
        if ((spin.events & Loup.EV_EXPAND) != 0) {
            if (at(lt, 0)) {
                fx.sound(Snd.SWITCH, 8, 32, 15, 1f, 0.7f);
                fx.sound(Snd.TENSION, 8, 32, 15, 0.9f, 0.9f);
            }
            if (lt >= EXP_GO && lt < EXP_UP) {
                if (((int) lt) % 3 == 0 && at(lt, (int) lt)) fx.sound(Snd.REEL_TICK, 8, SPLIT + lift, 15, 0.5f, 0.6f + 0.6f * (lt - EXP_GO) / (EXP_UP - EXP_GO));
                if (r.nextInt(2) == 0) {
                    float x = 1 + r.nextFloat() * 14;
                    fx.particle(FxSink.SMOKE, x, SPLIT + lift, FZ + 0.3f, rnd(r, 0.02f), 0.02f, 0.03f, 0x8A7A5A, 0.8f, 30);
                    fx.particle(FxSink.SPARK, x, SPLIT + lift, FZ + 0.4f, rnd(r, 0.1f), 0.08f + r.nextFloat() * 0.08f, 0.05f, GOLD, 0.25f, 18);
                }
            }
            if (at(lt, EXP_UP)) {
                fx.sound(Snd.WIN_BIG, 8, 34, 15, 1f, 0.9f);
                fx.sound(Snd.SMASH, 8, SPLIT + lift, 15, 0.7f, 0.8f);
                fountain(fx, r, 8, RY1 + lift, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, 0xFFFFFF}, 36, 0.35f);
            }
        }
        Loup.Plan plan = v.plan(i);
        float at = lt - Loup.actionStart(spin);
        boolean mansion = Loup.isMansion(spin.bonus);
        for (int[] h : plan.hits) {
            if (!at(at, h[0])) continue;
            float x = cellX(h[1] / Loup.BROWS), y = bY(h[1] % Loup.BROWS);
            if (h[2] == 1 || mansion) {
                fx.sound(Snd.SMASH, x, y, 15, 0.35f, 1.4f);
                for (int k = 0; k < 4; k++) fx.particle(FxSink.SHARD, x, y, SZ + 0.6f, rnd(r, 0.08f), 0.06f, 0.06f, 0xC0402A, 0.25f, 18);
            } else {
                fx.sound(Snd.CHIP, x, y, 15, 0.6f, 1.2f);
                for (int k = 0; k < 3; k++) fx.particle(FxSink.LEAF, x, y, SZ + 0.6f, rnd(r, 0.08f), 0.05f, 0.05f, 0xD8B860, 0.3f, 18);
            }
        }
        if (plan.sawsShown >= 0 && at(at, plan.sawsShown)) fx.sound(Snd.SCATTER, 8, sy, 15, 1f, 0.7f);
        if (spin.copies != 0 && at(at, plan.sawsShown + Loup.SAW_INTRO)) fx.sound(Snd.WHOOSH, 8, sy, 15, 1f, 1.2f);
        if (!mansion || spin.bonus == Loup.B_SUPER_MANSION) {
            for (int[] sw : plan.saws) {
                int start = sw[1];
                float x = cellX(sw[0] / Loup.BROWS) + Math.max(0, at - start) / Loup.SAW_STEP * CW, y = bY(sw[0] % Loup.BROWS);
                if (at(at, start)) fx.sound(Snd.BALL_ROLL, x, y, 15, 1f, 0.9f + 0.2f * r.nextFloat());
                if (at >= start && x < RX1 + CW * 0.5f) {
                    for (int k = 0; k < 2; k++) fx.particle(FxSink.SPARK, x - CW * 0.3f, y - CH * 0.3f, SZ + 0.6f, -0.1f - r.nextFloat() * 0.1f, 0.05f + r.nextFloat() * 0.08f,
                            0.04f, sw[2] == 1 ? GOLD : 0xFFD080, 0.2f, 12);
                }
            }
        }
        for (int k = 0; k < spin.megas.length && k < plan.megaAt.length; k++) {
            int land = plan.megaAt[k] + Loup.MEGA_FALL;
            if (at(at, plan.megaAt[k])) fx.sound(Snd.WHOOSH, 8, sy, 15, 0.8f, 0.6f);
            if (at(at, land)) {
                fx.sound(Snd.CRASH, 8, sy, 15, 0.8f, 0.7f);
                fx.sound(Snd.CHIP, 8, sy, 15, 1f, 0.8f);
                int m = spin.megas[k];
                float cx = RX0 + (Loup.megaX(m) + Loup.megaW(m) / 2f) * CW, cy = bY(Loup.megaY(m) + (Loup.megaH(m) - 1) / 2f);
                for (int q = 0; q < 14; q++) fx.particle(FxSink.SMOKE, cx + rnd(r, Loup.megaW(m) * CW / 2), cy - Loup.megaH(m) * CH / 2, SZ + 0.6f, rnd(r, 0.05f), 0.02f,
                        0.04f, 0xB8A070, 0.6f, 24);
            }
        }
        float f = at - plan.end;
        if (at(f, 0)) {
            fx.sound(Snd.BONUS, 8, sy, 15, 0.9f, 1.3f);
            fx.sound(Snd.BELL, 8, sy, 15, 0.8f, 1.1f);
        }
    }

    /** Tours gratuits : chapeaux, rebonds, primes, relance ; maisons construites, souffle du loup, total ; retrecissement. */
    private void freeFx(LoupView v, int i, Loup.Spin spin, float lt, FxSink fx, Random r, float sy, float lift) {
        if ((spin.events & Loup.EV_LAST) != 0) {
            if (at(lt, 0)) fx.sound(Snd.BELL, 8, sy, 15, 0.8f, 0.7f);
            if (lt < Loup.LAST_T && ((int) lt) % 2 == 0 && at(lt, (int) lt)) fx.sound(Snd.DRUM, 8, sy, 15, 0.35f + 0.4f * lt / Loup.LAST_T, 1f + 0.4f * lt / Loup.LAST_T);
        }
        boolean mansion = Loup.isMansion(spin.bonus);
        // les colonnes de chapeaux tournent puis s'arretent une a une (au ralenti si un chapeau de plus relance)
        int go = Loup.hatStart(spin) + 2, lastStop = Loup.freeStop(spin, Loup.REELS - 1);
        if (at(lt, go)) fx.sound(Snd.REEL_START, 8, sy, 15, 0.6f, 1.35f);
        if (lt > go + 1 && lt < lastStop && ((int) lt) % 3 == 0 && at(lt, (int) lt)) fx.sound(Snd.REEL_TICK, 8, sy, 15, 0.14f, 1.7f + r.nextFloat() * 0.2f);
        int lo = Loup.firstRow(spin.bonus);
        for (int reel = 0; reel < Loup.REELS; reel++) {
            int stop = Loup.freeStop(spin, reel);
            boolean teased = Loup.freeTease(spin, reel);
            if (teased) Tease.sounds(fx, lt - (stop - FREE_SLOW), FREE_SLOW, cellX(reel), sy, 15);
            if (!at(lt, stop)) continue;
            fx.sound(Snd.REEL_STOP, cellX(reel), sy, 15, 0.4f, 1.25f + reel * 0.05f);
            boolean hit = false;
            for (int row = lo; row < Loup.BROWS; row++) {
                if (!spin.hat(Loup.bcell(reel, row))) continue;
                hit = true;
                fx.sound(Snd.CHIP, cellX(reel), bY(row), 15, 0.8f, 0.9f);
            }
            if (teased) fx.sound(hit ? Snd.BELL : Snd.MISS, cellX(reel), sy, 15, hit ? 0.9f : 0.7f, hit ? 1.4f : 1f);
        }
        for (int c = 0; c < Loup.BCELLS; c++) {
            if (!spin.hat(c)) continue;
            float x = cellX(c / Loup.BROWS), y = bY(c % Loup.BROWS);
            int land = Loup.landAt(spin, c) + Loup.LAND_HIT;
            if (at(lt, land)) {
                if (spin.hatTo[c] == c) fx.sound(Snd.SPARKLE, x, y, 15, 0.5f, 0.9f + spin.tier[c] * 0.15f);
                for (int k = 0; k < 4; k++) fx.particle(FxSink.LEAF, x, y, SZ + 0.6f, rnd(r, 0.08f), 0.05f, 0.05f, mansion ? 0xC0402A : 0xD8B860, 0.3f, 20);
                if (spin.hatTo[c] == Loup.PRIZE) {
                    fx.sound(Snd.COINS, x, y, 15, 0.9f, 1.3f);
                    burst(fx, r, x, y, SZ + 0.6f, GOLD, 0xFFFFFF, 12, 0.3f, 0.3f);
                } else if (spin.hatTo[c] != c) {
                    fx.sound(Snd.WHOOSH, x, y, 15, 0.5f, 1.6f);
                } else {
                    fx.sound(mansion ? Snd.SMASH : Snd.CHIP, x, y, 15, mansion ? 0.35f : 0.8f, 1.15f);
                }
            }
            if (spin.hatTo[c] != c && spin.hatTo[c] != Loup.PRIZE && at(lt, land + Loup.HOP)) {
                int to = spin.hatTo[c];
                fx.sound(mansion ? Snd.SMASH : Snd.CHIP, cellX(to / Loup.BROWS), bY(to % Loup.BROWS), 15, mansion ? 0.35f : 0.8f, 1.2f);
            }
        }
        if ((spin.events & Loup.EV_RETRIGGER) != 0 && at(lt, Loup.bonusSpin(spin))) fx.sound(Snd.BONUS, 8, sy, 15, 0.8f, 1.5f);
        if ((spin.events & Loup.EV_BONUS_END) == 0) return;
        // les cadres deviennent des maisons
        int bs = Loup.buildStart(spin);
        int[] built = Loup.buildTimes(spin);
        for (int c = 0; c < Loup.BCELLS; c++) {
            if (built[c] < 0 || !at(lt - bs, built[c])) continue;
            float x = cellX(c / Loup.BROWS), y = bY(c % Loup.BROWS);
            int tier = spin.tier[c];
            fx.sound(tier == 3 ? Snd.SMASH : tier == 2 ? Snd.DICE_HIT : Snd.RUSTLE, x, y, 15, tier == 3 ? 0.3f : 0.6f, 1.1f + tier * 0.1f);
            for (int k = 0; k < 4; k++) fx.particle(FxSink.SMOKE, x + rnd(r, CW * 0.4f), y - CH * 0.4f, SZ + 0.6f, rnd(r, 0.03f), 0.02f, 0.03f, 0xA89878, 0.4f, 16);
        }
        int bsBlow = Loup.blowStart(spin), len = Loup.blow(spin);
        float bt = lt - bsBlow;
        if (at(bt, 0)) fx.sound(Snd.HOWL, 4, 22, 15, 0.8f, 1f);
        if (bt >= 4 && bt < Loup.BLOW_WIND - 2 && ((int) bt) % 6 == 4 && at(bt, (int) bt)) fx.sound(Snd.HEARTBEAT, 8, sy, 15, 1f, 0.9f + 0.4f * bt / Loup.BLOW_WIND);
        if (at(bt, Loup.BLOW_WIND - 4)) fx.sound(Snd.WIND, 8, sy, 15, 1f, 0.8f);
        if (bt > Loup.BLOW_WIND && bt < len - Loup.BLOW_TAIL && ((int) bt) % 24 == 0 && at(bt, (int) bt)) fx.sound(Snd.WIND, 8, sy, 15, 0.6f, 0.9f);
        int[] reveal = Loup.revealTimes(spin);
        int order = 0, houses = spin.houses();
        for (int c = 0; c < Loup.BCELLS; c++) {
            if (reveal[c] < 0) continue;
            if (reveal[c] < bt - 0.5f) order++;
            if (!at(bt, reveal[c])) continue;
            float cxp = cellX(c / Loup.BROWS), cyp = bY(c % Loup.BROWS);
            int tier = spin.tier[c], rw = spin.reward[c];
            fx.sound(Snd.CRASH, cxp, cyp, 15, 0.5f + 0.12f * tier, 1.5f - 0.15f * tier);
            fx.sound(tier >= 3 ? Snd.COINS : Snd.CHIP, cxp, cyp, 15, 0.8f, 0.9f + 0.6f * order / Math.max(1, houses));
            for (int k = 0; k < 3 + tier; k++) fx.particle(FxSink.LEAF, cxp, cyp, SZ + 0.6f, 0.1f + r.nextFloat() * 0.1f, 0.03f, 0.05f,
                    tier == 3 ? 0xC8603A : tier == 2 ? 0x9A6A3A : 0xD8B860, 0.35f, 30);
            if (rw >= Loup.R_MINI) {
                fx.sound(rw == Loup.R_WHEEL ? Snd.BONUS : Snd.WIN_JACKPOT, cxp, cyp, 15, 1f, rw == Loup.R_WHEEL ? 1.4f : 1f);
                fx.sound(Snd.SPARKLE, cxp, cyp, 15, 1f, 1.2f);
                burst(fx, r, cxp, cyp, SZ + 0.6f, rewardColor(tier, rw), 0xFFFFFF, 30, 0.45f, 0.4f);
            } else if (tier == 3) {
                burst(fx, r, cxp, cyp, SZ + 0.6f, TIER_COLORS[3], 0xFFFFFF, 12, 0.25f, 0.3f);
            }
        }
        if (at(bt, len - Loup.BLOW_TAIL + 12)) {
            fx.sound(Snd.COINS, 8, 16, 16, 0.9f, 1f);
            fx.sound(Snd.OINK, 8, sy, 15, 0.8f, 1.3f);
        }
        if (at(lt, Loup.endStart(spin)) && spin.winMilli > 0) {
            boolean big = Rotten.bigTier(spin.winMilli) > 0;
            fx.sound(big ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
            if (big) fountain(fx, r, 8, 34 + lift, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, RED}, 30, 0.35f);
        }
        if ((spin.events & Loup.EV_COLLAPSE) != 0) {
            int cs = Loup.collapseStart(spin);
            if (at(lt, cs + 4)) {
                fx.sound(Snd.WHOOSH, 8, SPLIT + lift, 15, 1f, 0.6f);
                fx.sound(Snd.SWITCH, 8, SPLIT + lift, 15, 0.7f, 1.3f);
            }
            if (at(lt, cs + Loup.COLLAPSE_T - 6)) fx.sound(Snd.REEL_STOP, 8, SPLIT, 15, 1f, 0.7f);
        }
    }

    // =================================================================== vues
    @Override
    public LoupView newView() {
        return new LoupView();
    }

    @Override
    public LoupView demoView(float t) {
        LoupView v = new LoupView();
        // apercu hors-jeu : graine fixe (-Dloupdemo=graine), achat de la roue (-Dloupbuy=1) ;
        // -Dloupbonus=1..7 : premiere partie qui lance ce bonus ; -Dloupjackpot=true ; -Dlouptease=scie|chapeau ;
        // -Dloupskip=ticks : avance dans la partie
        long seed = Long.getLong("loupdemo", 5L);
        int buy = (int) (long) Long.getLong("loupbuy", 0L);
        v.seq = Loup.play(v.rules, new java.util.SplittableRandom(seed), buy);
        int wantBonus = Integer.getInteger("loupbonus", 0);
        boolean wantJack = Boolean.getBoolean("loupjackpot"), wantHouse = Boolean.getBoolean("loupmaison");
        String wantTease = System.getProperty("louptease");
        if (wantHouse) {
            // une maison de brique cache un tour de roue
            for (long k = seed; k < seed + 4_000_000; k++) {
                Loup.Sequence q = Loup.play(v.rules, new java.util.SplittableRandom(k), Loup.BUY_WHEEL);
                if (q.spins.stream().anyMatch(sp -> sp.kind == Loup.SPIN_WHEEL)) {
                    v.seq = q;
                    break;
                }
            }
        }
        if (wantBonus > 0 || wantJack || wantTease != null) {
            for (long k = seed; k < seed + 4_000_000; k++) {
                Loup.Sequence q = Loup.play(v.rules, new java.util.SplittableRandom(k), wantBonus > 1 || wantJack ? Loup.BUY_WHEEL : 0);
                Loup.Spin f = q.spins.get(0);
                if (wantTease != null) {
                    boolean found = false;
                    for (int reel = 1; reel < Loup.REELS; reel++) {
                        if (Loup.tease(f, reel) && (Loup.teaseSymbol(f, reel) == Loup.SAW) == wantTease.startsWith("s")) found = true;
                    }
                    if (found) {
                        v.seq = q;
                        break;
                    }
                    continue;
                }
                if (wantJack && (f.events & Loup.EV_JACKPOT) != 0) {
                    v.seq = q;
                    break;
                }
                if (wantBonus > 0 && f.bonus == wantBonus && (f.events & Loup.EV_BONUS) != 0) {
                    v.seq = q;
                    break;
                }
            }
        }
        v.bet = 100;
        v.since = t + Float.parseFloat(System.getProperty("loupskip", "0"));
        return v;
    }

    @Override
    public int demoLength() {
        return 200;
    }
}
