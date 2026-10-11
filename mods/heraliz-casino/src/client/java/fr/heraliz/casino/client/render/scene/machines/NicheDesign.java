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
import fr.heraliz.casino.game.slot.Niche;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.Suspense;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * "La Niche" (The Dog House Multihold), montage de recuperation : un vieux televiseur bleu passe pose sur une palette et
 * deux piles de parpaings (entre elles, la porte de la niche ou loge le chien), coiffe d'un toit de niche en tole
 * ondulee (pignon de planches, os, baladeuse) ; le titre "LA NICHE" est affiche en haut de l'ecran ; caisse de
 * croquettes, batterie et cables, corde et collier, gamelle, panneau "CHIEN MECHANT", tabouret en pneus empiles.
 * <p>
 * Ecran 5 x 3 (20 lignes) en jeu normal. Au declenchement, la grille se range dans le coin haut gauche et trois
 * emplacements verrouilles apparaissent (grillage, cadenas) ; pendant les tours gratuits, 3 pattes sur la grille allumee
 * font sauter le cadenas de la suivante, qui grandit, recoit par un trait d'energie une copie de chaque wild collant
 * (memes cases, memes multiplicateurs), puis tourne. Chaque grille affiche son propre gain.
 */
public final class NicheDesign extends MachineDesign<NicheView> {
    // ---- atlas : partage avec tools/textures (PaintNiche) ; symboles en cases de 128 (0-7 en haut, 8-10 dessous)
    public static final int BLUR_Y = 256;
    public static final int[] R_WILD2 = {384, 128, 128, 128};
    public static final int[] R_WILD3 = {512, 128, 128, 128};
    public static final int[] R_BONUS = {640, 128, 128, 128};
    /** Cadenas (image detouree : l'anse dans le haut, le corps dessous, separes a LOCK_SPLIT). */
    public static final int[] R_LOCK = {768, 128, 128, 128};
    /** Patte boueuse plaquee sur la vitre (gros gains). */
    public static final int[] R_PAW = {896, 128, 128, 128};
    public static final int[] R_FLASH = {384, 384, 128, 128};
    /** Cadre dore rivete d'un wild collant (image detouree). */
    public static final int[] R_STICKY = {512, 384, 128, 128};
    public static final int[] R_EMPTY = {640, 384, 128, 128};
    public static final int[] R_LED = {768, 384, 64, 64};
    public static final int[] R_DARK = {832, 384, 64, 64};
    public static final int[] R_TRIM = {896, 384, 64, 64};
    public static final int[] R_STEEL = {960, 384, 64, 64};
    public static final int[] R_GLASS = {768, 448, 128, 64};
    public static final int[] R_RUBBER = {896, 448, 128, 64};
    /** Chien de garde des gros gains : assis, puis qui aboie (images detourees). */
    public static final int[] R_DOG_A = {0, 512, 256, 256};
    public static final int[] R_DOG_B = {256, 512, 256, 256};
    public static final int[] R_PLANKS = {512, 512, 256, 256};
    public static final int[] R_SHINGLES = {768, 512, 256, 128};
    public static final int[] R_BLOCK = {768, 640, 256, 128};
    public static final int[] R_TOPPER = {0, 768, 512, 128};
    public static final int[] R_FENCE = {0, 896, 256, 128};
    public static final int[] R_SIGN = {256, 896, 256, 128};
    public static final int[] R_PANEL = {512, 768, 256, 128};
    public static final int[] R_BAR = {512, 896, 256, 128};
    public static final int[] R_BTN_RED = {768, 768, 128, 128};
    public static final int[] R_BTN_YEL = {896, 768, 128, 128};
    public static final int[] R_PLAID = {768, 896, 128, 128};
    public static final int[] R_SCREEN = {896, 896, 128, 128};
    public static final float LOCK_SPLIT = 0.42f;

    static final Mat PLANKS = FruitsDesign.tri(R_PLANKS, 16), SHINGLES = FruitsDesign.tri(R_SHINGLES, 8), BLOCK = FruitsDesign.tri(R_BLOCK, 8);
    static final Mat TRIM = FruitsDesign.tri(R_TRIM, 4), STEEL = FruitsDesign.tri(R_STEEL, 6), DARK = FruitsDesign.tri(R_DARK, 6);
    static final Mat RUBBER = FruitsDesign.tri(R_RUBBER, 6), PLAID = FruitsDesign.tri(R_PLAID, 6);
    static final Mat TOPPER = FruitsDesign.fit(R_TOPPER), PANEL = FruitsDesign.fit(R_PANEL), BAR = FruitsDesign.fit(R_BAR), SIGN = FruitsDesign.fit(R_SIGN);
    static final Mat SCREEN = FruitsDesign.fit(R_SCREEN), GLASS = FruitsDesign.fit(R_GLASS), LED = FruitsDesign.fit(R_LED);
    static final Mat BTN_RED = FruitsDesign.fit(R_BTN_RED), BTN_YEL = FruitsDesign.fit(R_BTN_YEL);
    static final Mat FLASH = FruitsDesign.fit(R_FLASH), STICKY = FruitsDesign.fit(R_STICKY), EMPTY_M = FruitsDesign.fit(R_EMPTY), FENCE = FruitsDesign.fit(R_FENCE);
    static final Mat LOCK = FruitsDesign.fit(R_LOCK), PAW = FruitsDesign.fit(R_PAW), DOG_A = FruitsDesign.fit(R_DOG_A), DOG_B = FruitsDesign.fit(R_DOG_B);
    static final Mat WILD2 = FruitsDesign.fit(R_WILD2), WILD3 = FruitsDesign.fit(R_WILD3), BONUS_M = FruitsDesign.fit(R_BONUS);
    static final Mat LOCK_TOP = LOCK.sub(0, 0, 1, LOCK_SPLIT), LOCK_BODY = LOCK.sub(0, LOCK_SPLIT, 1, 1);
    private static final Mat[] SYM = new Mat[Niche.PAY_SYMBOLS], BLUR = new Mat[Niche.PAY_SYMBOLS];

    static {
        for (int s = 0; s < Niche.PAY_SYMBOLS; s++) {
            SYM[s] = Mat.fit((s % 8) * 128, (s / 8) * 128, 128, 128);
            BLUR[s] = Mat.fit((s % 8) * 128, BLUR_Y + (s / 8) * 128, 128, 128);
        }
    }

    /** Symbole d'une case : carte, os, collier, chien, WILD x2 / x3 (multiplicateur peint dessus), BONUS ; -1 = case vide. */
    static Mat symbol(int s, int mult, boolean blur) {
        if (s == Niche.WILD) return mult >= 3 ? WILD3 : WILD2;
        if (s == Niche.BONUS) return BONUS_M;
        if (s < 0 || s >= Niche.PAY_SYMBOLS) return EMPTY_M;
        return blur ? BLUR[s] : SYM[s];
    }

    // ---- cotes (px) : fenetre de la borne commune
    static final float FZ = SlotRig.FZ, SZ = SlotRig.SZ;
    static final float WX0 = SlotRig.WX0, WX1 = SlotRig.WX1, WY0 = SlotRig.WY0, WY1 = SlotRig.WY1;
    /** Grille du jeu normal (5 x 3). */
    static final float RX0 = 2.3f, RX1 = 13.7f, RY0 = 21.0f, RY1 = 28.2f;
    static final float CW = (RX1 - RX0) / Niche.REELS, CH = (RY1 - RY0) / Niche.ROWS;
    /** Multihold : 2 x 2 grilles de cases carrees, un bandeau de titre au-dessus de chacune. */
    static final float MX0 = WX0 + 0.12f, GAPX = 0.4f, GW = (WX1 - WX0 - 0.24f - GAPX) / 2, MC = GW / Niche.REELS, GH = MC * Niche.ROWS, HEAD = 0.62f;
    /** Bas des cases des grilles du haut (0, 1) et du bas (2, 3). */
    static final float[] GY = {24.44f, 20.05f};
    /** Bandeaux de l'ecran en Multihold : haut (tours restants) a partir de TOP_BAR, bas (mise, gain) jusqu'a LOW_BAR. */
    static final float TOP_BAR = 28.62f, LOW_BAR = 19.98f;
    static final float[] SPIN_BTN = {12.1f, 16.7f, 15.3f}, BONUS_BTN = {3.8f, 16.7f, 15.3f};
    /** Pointe du pignon ; baladeuse de chantier sous l'avancee du toit. */
    static final float GABLE_TOP = SlotRig.TV_Y1 + 6.4f;
    static final float[] LAMP = {8f, SlotRig.TV_Y1 + 3.6f, 15.35f};
    static final int BLUE = 0x6AC8FF, AMBER = 0xFFB040, GOLD = 0xFFD36A, WHITE = 0xF6F0E4, RED = 0xFF4A2A, GREEN = 0x9CFF3A, GREY = 0x9A968E, PURPLE = 0xC08AFF;
    static final int[] LINE_COLORS = {0xFFD36A, 0x6AC8FF, 0xFF7A5A, 0x9CFF3A, 0xFF8AE8, 0xC08AFF, 0xFFB040, 0x6AFFD8};
    /** Grille affichee au repos (cases : rouleau x 3 + rangee). */
    static final int[] IDLE = {4, 7, 1, 6, Niche.WILD, 2, 10, 0, Niche.BONUS, 3, Niche.WILD, 8, 5, 9, 1};
    static final int[] IDLE_MULT = {0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0};
    private static final int[] NONE = new int[Niche.CELLS], BLANK = new int[Niche.CELLS];

    static {
        java.util.Arrays.fill(BLANK, -1);
    }

    /** Etat d'une grille du Multihold : derniere ouverte (la seule ou tombent pattes et wilds), ouverte, verrouillee. */
    static final int LATEST = 0, OPEN = 1, LOCKED = 2;
    /** Vol d'un wild recopie (ticks) d'une grille a la suivante. */
    static final int COPY_FLY = 6;
    /** Duree du ralenti d'un rouleau en suspens, et cases qu'il parcourt pendant ce temps. */
    static final int SLOW = Suspense.HOLD + 2;
    static final float SLOW_CELLS = Niche.ROWS + 2;

    private Mesh body = Mesh.EMPTY, kit = Mesh.EMPTY, bodyLights = Mesh.EMPTY, topperGlow = Mesh.EMPTY, stripGlow = Mesh.EMPTY, bulb = Mesh.EMPTY, bulbGlow = Mesh.EMPTY;
    private Mesh spinBtn = Mesh.EMPTY, bonusBtn = Mesh.EMPTY, screenBg = Mesh.EMPTY, barsBase = Mesh.EMPTY, barsMulti = Mesh.EMPTY, glass = Mesh.EMPTY, veil = Mesh.EMPTY;

    public NicheDesign() {
        super("la_niche", BLUE, AMBER);
        labelY = 48;
        itemScale = 0.34f;
    }

    // =================================================================== disposition des grilles
    /** Emplacement d'une grille : coin bas gauche des cases, largeur et hauteur d'une case. */
    record Place(float x0, float y0, float cw, float ch) {
        float cx(float reel) {
            return x0 + (reel + 0.5f) * cw;
        }

        float cy(float row) {
            return y0 + Niche.ROWS * ch - (row + 0.5f) * ch;
        }

        float x1() {
            return x0 + Niche.REELS * cw;
        }

        float y1() {
            return y0 + Niche.ROWS * ch;
        }

        float midX() {
            return x0 + Niche.REELS * cw / 2;
        }

        float midY() {
            return y0 + Niche.ROWS * ch / 2;
        }

        /** Echelle par rapport a une grille du Multihold. */
        float scale() {
            return cw / MC;
        }

        Place lerp(Place o, float t) {
            if (t <= 0) return this;
            if (t >= 1) return o;
            return new Place(Ease.lerp(x0, o.x0, t), Ease.lerp(y0, o.y0, t), Ease.lerp(cw, o.cw, t), Ease.lerp(ch, o.ch, t));
        }
    }

    static final Place BASE = new Place(RX0, RY0, CW, CH);
    private static final Place[] SLOTS = new Place[Niche.GRIDS];

    static {
        for (int k = 0; k < Niche.GRIDS; k++) SLOTS[k] = new Place(MX0 + (k % 2) * (GW + GAPX), GY[k / 2], MC, MC);
    }

    /** Emplacement de la grille k (0 haut gauche, 1 haut droite, 2 bas gauche, 3 bas droite), a l'echelle donnee. */
    static Place slot(int k, float scale) {
        Place s = SLOTS[k];
        if (scale == 1) return s;
        float c = MC * scale;
        return new Place(s.midX() - c * Niche.REELS / 2, s.midY() - c * Niche.ROWS / 2, c, c);
    }

    // =================================================================== construction
    /** Face avant du pignon (planches) : l'os y est cloue, la baladeuse pend devant. */
    static final float GABLE_Z = 15.0f;

    @Override
    protected void build() {
        float[] nz = v(0, 0, 1);
        MeshBuilder b = new MeshBuilder();
        MeshBuilder k = new MeshBuilder();
        // ---- deux piles de parpaings, palette en travers, caisse de croquettes ; entre les piles, la planche a la porte
        // de niche (le chien loge sous l'etagere)
        for (float x : new float[]{0.0f, 11.6f}) {
            for (int i = 0; i < 3; i++) BunkerKit.block(b, x, i * 3.0f, 4.0f, x + 4.4f, i * 3.0f + 3.0f, 11.0f, BLOCK, DARK);
        }
        BunkerKit.pallet(k, -0.3f, 9.0f, 2.6f, 16.3f, 10.4f, 14.8f, 7, Recup.PALLET);
        SlotRig.crate(k, 2.4f, 10.4f, 4.2f, 13.6f, SlotRig.TV_Y0, 13.8f, Recup.CROQ);
        b.bevelBox(4.5f, 0, 10.4f, 11.5f, 7.2f, 10.9f, 0.1f, PLANKS, new Mat[]{null, null, null, null, null, PANEL}, 0);
        // ---- vieux televiseur bleu passe (adhesif), antenne ; pupitre : caisse a outils galvanisee
        k.color(0.62f, 0.74f, 0.86f);
        SlotRig.tv(k, Recup.PLASTIC, k, true);
        k.color(1);
        SlotRig.embrasure(k, Recup.DARK, WY0, SlotRig.PY1);
        SlotRig.deck(k, Recup.STEEL, Recup.DARK);
        // panneau "ATTENTION CHIEN MECHANT" visse sur le flanc gauche du televiseur
        b.bevelBox(SlotRig.TV_X0 - 0.18f, 23.4f, 10.0f, SlotRig.TV_X0, 25.5f, 14.0f, 0.03f, STEEL, new Mat[]{SIGN, null, null, null, null, null}, 0);
        // ---- toit de niche : tole ondulee pose sur le televiseur, pignon de planches bleues, os, baladeuse sous
        // l'avancee (le titre LA NICHE est affiche par l'ecran)
        b.push().translate(0, 0, 5.4f);
        b.prism(new float[][]{{0.4f, SlotRig.TV_Y1}, {15.6f, SlotRig.TV_Y1}, {8f, GABLE_TOP}}, SlotRig.TV_FRONT - 5.4f, PLANKS, PLANKS);
        b.pop();
        float slope = (float) Math.toDegrees(Math.atan2(GABLE_TOP - SlotRig.TV_Y1, 7.6f));
        float len = (float) Math.hypot(7.6f, GABLE_TOP - SlotRig.TV_Y1) + 1.0f;
        for (int sd = -1; sd <= 1; sd += 2) {
            k.push().translate(8, GABLE_TOP, 0).rotateZ(-sd * slope);
            float xa = sd < 0 ? -len : -0.3f, xb = sd < 0 ? 0.3f : len;
            k.bevelBox(xa, 0, 4.8f, xb, 0.3f, 16.3f, 0.04f, Recup.CORRUGATED);
            k.pop();
        }
        k.push().translate(8, GABLE_TOP + 0.38f, 4.7f).rotateX(90);
        k.cylinder(0.32f, 0, 11.7f, 6, Recup.RUST, Recup.RUST, Recup.RUST);
        k.pop();
        bone(b, 8, SlotRig.TV_Y1 + 1.5f, GABLE_Z - 0.2f, 1.2f, 0.26f, 0.34f);
        b.bevelBox(7.85f, LAMP[1] + 0.5f, GABLE_Z - 0.6f, 8.15f, LAMP[1] + 0.75f, LAMP[2], 0.04f, STEEL);
        b.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        b.cylinder(0.4f, 0.22f, 0.4f, 8, STEEL, STEEL, STEEL);
        for (int i = 0; i < 4; i++) {
            b.push().rotateY(45 + i * 90).translate(0.38f, 0, 0);
            b.box6(-0.04f, -0.4f, -0.04f, 0.04f, 0.24f, 0.04f, STEEL, STEEL, null, null, STEEL, STEEL);
            b.pop();
        }
        b.pop();
        // ---- batterie par terre devant la pile de droite, cables jusqu'au pupitre
        SlotRig.battery(k, 13.8f, 0, 13.6f, 8);
        SlotRig.cable(k, new float[][]{p(12.6f, 3.2f, 13.3f), p(13.0f, 7.0f, 15.2f), p(12.6f, 10.6f, 15.2f), p(11.6f, 13.7f, 15.3f)}, true);
        SlotRig.cable(k, new float[][]{p(15.0f, 3.2f, 13.5f), p(15.2f, 7.4f, 15.3f), p(14.4f, 10.8f, 15.2f), p(12.8f, 13.8f, 15.4f)}, false);
        // ---- gamelle cabossee et son os, collier au bout d'une corde attachee aux parpaings
        b.push().translate(2.3f, 0, 19.4f);
        b.hollowCylinder(1.05f, 0.86f, 0, 0.5f, seg(14), STEEL);
        b.disc(0.88f, 0.12f, seg(14), DARK, true);
        b.pop();
        bone(b, 3.1f, 0.62f, 19.1f, 0.42f, 0.09f, 0.13f);
        k.tube(new float[][]{p(2.2f, 4.6f, 11.1f), p(1.6f, 1.2f, 13.0f), p(2.6f, 0.3f, 15.4f), p(4.4f, 0.25f, 16.4f)}, new float[]{0.12f, 0.12f, 0.12f, 0.12f}, 5,
                Recup.BURLAP, true, true, 0, false);
        b.push().translate(5.2f, 0.22f, 16.6f);
        b.color(0.66f, 0.16f, 0.1f);
        b.torus(0.8f, 0.2f, seg(12), 5, 0, 360, LED);
        b.color(1);
        b.pop();
        // ---- guirlande d'ampoules bleues sous l'avancee du toit
        MeshBuilder bulbs = new MeshBuilder();
        b.color(0.3f, 0.5f, 0.7f);
        SlotRig.garland(k, b, bulbs, LED, p(0.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), p(15.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), 0.9f, 8);
        b.color(1);
        stripGlow = bulbs.build();
        // ---- tabouret : pneus empiles, coussin en plaid
        b.push().translate(8, 0, 24);
        for (int i = 0; i < 4; i++) {
            b.push().translate(0, 0.95f + i * 1.9f, 0).rotateY(i * 37);
            b.torus(2.35f, 0.95f, seg(16), 6, 0, 360, RUBBER);
            b.pop();
        }
        b.cylinder(1.5f, 0.2f, 7.5f, seg(8), DARK, null, null);
        b.push().translate(0, 7.45f, 0);
        b.cylinder(2.9f, 0, 1.15f, seg(16), PLAID, null, PLAID);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.35f, 3f, 0.45f, 0f);
        Occlusion.bake(k, 0.35f, 3f, 0.45f, 0f);
        body = b.build();
        kit = k.build();

        MeshBuilder lb = new MeshBuilder();
        SlotRig.led(lb, LED);
        bodyLights = lb.build();
        // titre "LA NICHE" affiche en haut de l'ecran (calque lumineux)
        MeshBuilder tg = new MeshBuilder();
        SlotRig.title(tg, TOPPER);
        topperGlow = tg.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.3f, 8, 6, LED);
        bu.pop();
        bulb = bu.build();
        MeshBuilder bg = new MeshBuilder();
        bg.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bg.sphere(0.55f, 8, 6, LED);
        bg.pop();
        bulbGlow = bg.build();
        spinBtn = button(SPIN_BTN, 1.2f, BTN_RED);
        bonusBtn = button(BONUS_BTN, 1.0f, BTN_YEL);
        MeshBuilder sb = new MeshBuilder();
        sb.face(SCREEN, nz, p(WX0, WY0, SZ), p(WX1, WY0, SZ), p(WX1, SlotRig.PY1, SZ), p(WX0, SlotRig.PY1, SZ));
        screenBg = sb.build();
        MeshBuilder bb = new MeshBuilder();
        bar(bb, WY0 + 0.1f, RY0 - 0.15f);
        bar(bb, RY1 + 0.15f, WY1 - 0.1f);
        barsBase = bb.build();
        MeshBuilder bm = new MeshBuilder();
        bar(bm, WY0 + 0.1f, LOW_BAR - 0.06f);
        bar(bm, TOP_BAR, WY1 - 0.1f);
        barsMulti = bm.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(WX0, WY0, FZ - 0.05f), p(WX1, WY0, FZ - 0.05f), p(WX1, SlotRig.PY1, FZ - 0.05f), p(WX0, SlotRig.PY1, FZ - 0.05f));
        glass = gl.build();
        MeshBuilder vl = new MeshBuilder();
        vl.face(DARK, nz, p(-0.5f, -0.5f, 0), p(0.5f, -0.5f, 0), p(0.5f, 0.5f, 0), p(-0.5f, 0.5f, 0));
        veil = vl.build();
    }

    /** Os blanc couche (axe X) centre en (x, y, z) : demi-longueur, rayon de la tige, rayon des noeuds. */
    private static void bone(MeshBuilder b, float x, float y, float z, float half, float rod, float knob) {
        b.push().translate(x, y, z).rotateZ(90);
        b.cylinder(rod, -half, half, 8, TRIM, null, null);
        b.pop();
        for (float dx : new float[]{-half, half}) {
            for (float dy : new float[]{-knob * 0.75f, knob * 0.75f}) {
                b.push().translate(x + dx, y + dy, z);
                b.sphere(knob, 8, 5, TRIM);
                b.pop();
            }
        }
    }

    private static void bar(MeshBuilder b, float y0, float y1) {
        b.face(BAR, v(0, 0, 1), p(WX0 + 0.1f, y0, SZ + 0.02f), p(WX1 - 0.1f, y0, SZ + 0.02f), p(WX1 - 0.1f, y1, SZ + 0.02f), p(WX0 + 0.1f, y1, SZ + 0.02f));
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
    /** Debut de l'annonce du bonus (tour normal qui declenche), puis de la mise en place des quatre grilles. */
    static int trigStart(Niche.Spin s) {
        return Niche.spinEnd(s) + Niche.winShow(s);
    }

    static int transStart(Niche.Spin s) {
        return trigStart(s) + Niche.TRIGGER_T;
    }

    /** Fin du tour (avant l'annonce du gain maximum). */
    static int end(Niche.Spin s) {
        return Niche.duration(s) - ((s.events & Niche.EV_MAXWIN) != 0 ? Rotten.BIG_WIN_TICKS : 0);
    }

    /** Disposition (0 = grille du jeu normal, 1 = quatre grilles) au temps lt du tour i. */
    static float multi(NicheView v, int i, float lt) {
        if (i < 0) return 0;
        Niche.Spin s = v.seq.spins.get(i);
        if (s.kind == Niche.BASE) {
            if ((s.events & Niche.EV_TRIGGER) == 0) return 0;
            return Ease.inOutCubic(Ease.range(lt - transStart(s), 6, 30));
        }
        if ((s.events & Niche.EV_END) != 0) return 1 - Ease.inOutCubic(Ease.range(lt - Niche.fadeStart(s), 14, Niche.FADE_T));
        return 1;
    }

    /** Retrait de la grille k a la fin du bonus (0 = la, 1 = partie) : la derniere ouverte s'en va la premiere. */
    static float gone(Niche.Spin s, int k, float lt) {
        if (s.kind != Niche.FREE || (s.events & Niche.EV_END) == 0 || k == 0) return 0;
        float f = lt - Niche.fadeStart(s);
        return Ease.range(f, (3 - k) * 4, (3 - k) * 4 + 10);
    }

    /** Ticks depuis le debut du deblocage u du tour (negatif avant). */
    static float unlockT(Niche.Spin s, int u, float lt) {
        return lt - Niche.unlockStart(s, u);
    }

    /** Ecart entre les departs de deux wilds recopies lors du deblocage u. */
    static float copyStep(Niche.Spin s, int u) {
        int n = Integer.bitCount(Niche.copiedMask(s, u));
        return n == 0 ? 0 : Math.min(Niche.COPY_STEP, (Niche.copyTime(s, u) - COPY_FLY) / (float) n);
    }

    /** Debut de l'annonce +N tours, puis de la rotation de la grille qui vient de s'ouvrir (ticks depuis le deblocage). */
    static int addStart(Niche.Spin s, int u) {
        return Niche.UNLOCK_GROW + Niche.copyTime(s, u);
    }

    static int newSpinStart(Niche.Spin s, int u) {
        return addStart(s, u) + Niche.ADD_T;
    }

    /** Arret d'un rouleau de la grille qui vient de s'ouvrir (ticks depuis newSpinStart). */
    static int newStop(int reel) {
        return Niche.newStop(reel);
    }

    /** Cases parcourues a pleine vitesse par un rouleau qui s'arrete a "stop" (ralenti compris s'il est en suspens). */
    static int fast(int stop, boolean teased) {
        return Suspense.fastCells(teased ? stop - SLOW : stop, Niche.ROWS);
    }

    /** Derniere grille ouverte (celle ou tombent les pattes) au temps lt. */
    static int latest(Niche.Spin s, float lt) {
        if (s.kind != Niche.FREE) return 0;
        int g = s.startGrids - 1;
        for (int u = 0; u < s.unlocked; u++) if (unlockT(s, u, lt) >= Niche.UNLOCK_BREAK) g++;
        return g;
    }

    /** Tours restants affiches : les tours gagnes s'ajoutent quand leur annonce apparait (meme compte que la ligne d'etat). */
    static int spinsShown(Niche.Spin s, float lt) {
        return Niche.spinsShown(s, lt);
    }

    /** Grille en suspens (le 5e rouleau attend la 3e patte) et ticks depuis le debut du ralenti ; null sinon. */
    static float[] teasing(Niche.Spin spin, float lt) {
        if (spin == null) return null;
        int g = spin.kind == Niche.BASE ? 0 : spin.startGrids - 1;
        if (!Niche.tease(spin, g, Niche.REELS - 1)) return null;
        float t = lt - Niche.spinStart(spin);
        int stop = Niche.stopTick(spin, Niche.REELS - 1);
        float tt = t - (stop - SLOW);
        return tt >= 0 && t < stop ? new float[]{g, tt} : null;
    }

    /** Ticks depuis le debut d'une annonce avec le chien (gros gain, gain du bonus, gain maximum), -1 sinon. */
    static float bigAt(Niche.Spin s, float lt) {
        if (s == null) return -1;
        if (s.kind == Niche.BASE) {
            float t = lt - Niche.spinEnd(s);
            if (Rotten.bigTier(s.gridMilli[0]) > 0 && t >= 0 && t < Rotten.BIG_WIN_TICKS) return t;
        } else if ((s.events & Niche.EV_END) != 0 && endTier(s) > 0) {
            float t = lt - Niche.endStart(s);
            if (t >= 0 && t < Niche.endShow(s)) return t;
        }
        if (s.kind == Niche.BASE && (s.events & Niche.EV_MAXWIN) != 0) {
            float m = lt - end(s);
            if (m >= 0 && m < Rotten.BIG_WIN_TICKS) return m;
        }
        return -1;
    }

    /** Palier de l'annonce de fin du bonus (4 : gain maximum). */
    static int endTier(Niche.Spin s) {
        return (s.events & Niche.EV_MAXWIN) != 0 ? 4 : Rotten.bigTier(s.bonusMilli);
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
    protected void animate(Scene s, NicheView v, Pose p, DrawList out) {
        float[] at = s.item ? null : v.at(v.since);
        boolean playing = at != null && v.since < v.totalTicks() + 40;
        // la baladeuse gresille de temps en temps
        float flick = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 991)) > 0.97f ? 0.3f : 0.85f + 0.15f * (float) Math.sin(s.time * 0.4f);
        out.solid(p, topperGlow, 0.8f);
        out.additive(p, stripGlow, BLUE, 0.55f + 0.35f * s.pulse(0.08f));
        out.solid(p, bulb, flick);
        out.additive(p, bulbGlow, 0xFFD890, 0.35f * flick);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1] - 0.2f, LAMP[2] + 0.3f, 3.2f, 0, 0xFFC870, 0.2f * flick);
        float press = playing && at[0] == 0 && at[1] < 6 ? Ease.bell(at[1], 0, 6) * 0.25f : 0;
        p.push().translate(0, -press, 0);
        out.solid(p, spinBtn, playing ? 0.3f : 0.55f + 0.35f * s.pulse(0.12f));
        p.pop();
        out.solid(p, bonusBtn, 0.35f + 0.25f * s.pulse(0.09f));
        out.solid(p, screenBg, 0.4f);
        // intro (le joueur vient de s'installer) : le chien sort de sa niche et aboie, puis la grille tombe
        introT = Intro.time(v.intro, playing, s.item);
        if (Intro.scene(introT)) {
            intro(s, p, out, introT);
        } else {
            screen(s, v, p, out, at);
            Intro.title(p, out, introT, I18n.get("heralizcasino.niche.titre"), I18n.get(INTRO_SUB), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, BLUE, GOLD);
        }
        out.translucent(p, glass, 0xFFFFFF, 0.25f);
    }

    /** Temps de l'intro pour l'image en cours (-1 : pas d'intro) ; lu par la grille au repos. */
    private float introT = -1;

    /** Cle du sous-titre de l'intro (traduit a l'affichage). */
    static final String INTRO_SUB = "heralizcasino.niche.intro";
    /** Pattes de l'intro : nombre, premiere (tick), intervalle. */
    static final int PAWS = 9;
    static final float PAW_T = 36, PAW_DT = 2.2f;

    static float pawX(int k) {
        return WX0 + 1.0f + k * (WX1 - WX0 - 2.0f) / (PAWS - 1);
    }

    static float pawY(int k) {
        return WY1 - 3.0f + (k % 2 == 0 ? -0.35f : 0.35f) + 0.3f * (float) Math.sin(k * 0.8f);
    }

    /**
     * Intro : la niche au fond de la cour, la baladeuse s'allume, deux yeux brillent dans l'entree, le chien sort et
     * aboie ("WOUF !"), puis ses pattes traversent l'ecran ; le titre arrive, puis la grille tombe (Intro.REVEAL).
     */
    private void intro(Scene s, Pose p, DrawList out, float t) {
        float z = SZ + 0.04f, ground = WY0 + 1.5f, nx = 8;
        float[] nz = v(0, 0, 1);
        MeshBuilder mb = new MeshBuilder(), fx = new MeshBuilder(), eyes = new MeshBuilder(), paws = new MeshBuilder();
        // sol, palissade, lumiere de la baladeuse (qui hesite avant de prendre)
        Intro.quad(mb, DARK, WX0, WY0, WX1, ground, WX0, WY0, WX1, WY1, z + 0.01f, 1f, false);
        float fence = Ease.outCubic(Ease.range(t, 4, 12));
        for (int k = 0; k < 3; k++) {
            float x0 = WX0 + k * (WX1 - WX0) / 3;
            Intro.quad(mb, FENCE, x0, ground - 0.1f, x0 + (WX1 - WX0) / 3, ground - 0.1f + 2.2f * fence, WX0, WY0, WX1, WY1, z + 0.012f, 0.55f, false);
        }
        float light = Ease.range(t, 5, 9) * (Intro.at(t, 7) ? 0.3f : 1f);
        Intro.quad(fx, FLASH, nx - 6.5f, ground - 2, nx + 6.5f, ground + 9.5f, WX0, WY0, WX1, WY1, z + 0.005f, 0.3f * light, false);
        // la niche : planches, toit de bardeaux, entree sombre en arche
        float pop = Ease.outBack(Ease.range(t, 6, 14), 1.6f);
        if (pop > 0.01f) {
            float hw = 3.1f * pop, hh = 4.2f * pop, rh = 2.5f * pop, dw = 1.25f * pop, dh = 1.9f * pop, ry = ground + hh;
            Intro.quad(mb, PLANKS, nx - hw, ground, nx + hw, ry, WX0, WY0, WX1, WY1, z + 0.02f, 0.75f, false);
            mb.color(0.8f);
            mb.poly(new float[][]{p(nx - hw - 0.5f * pop, ry - 0.2f, z + 0.022f), p(nx + hw + 0.5f * pop, ry - 0.2f, z + 0.022f), p(nx, ry + rh, z + 0.022f)},
                    new float[][]{{SHINGLES.u(0), SHINGLES.v(1)}, {SHINGLES.u(1), SHINGLES.v(1)}, {SHINGLES.u(0.5f), SHINGLES.v(0)}}, null, nz);
            float[] uv = {DARK.u(0.5f), DARK.v(0.5f)};
            mb.color(0.1f);
            mb.poly(new float[][]{p(nx - dw, ground, z + 0.025f), p(nx + dw, ground, z + 0.025f), p(nx + dw, ground + dh, z + 0.025f), p(nx - dw, ground + dh, z + 0.025f)},
                    new float[][]{uv, uv, uv, uv}, null, nz);
            for (int k = 0; k < 8; k++) {
                double a0 = Math.PI * k / 8, a1 = Math.PI * (k + 1) / 8;
                mb.poly(new float[][]{p(nx, ground + dh, z + 0.025f), p(nx + dw * (float) Math.cos(a0), ground + dh + dw * (float) Math.sin(a0), z + 0.025f),
                        p(nx + dw * (float) Math.cos(a1), ground + dh + dw * (float) Math.sin(a1), z + 0.025f)}, new float[][]{uv, uv, uv}, null, nz);
            }
            mb.color(1);
        }
        // deux yeux dans le noir, qui clignent
        if (t >= 10 && t < 24) {
            float a = Ease.range(t, 10, 12) * (1 - Ease.range(t, 21, 24)), blink = Intro.at(t, 15) || Intro.at(t, 19) ? 0.15f : 1f;
            for (int e = -1; e <= 1; e += 2) {
                float ex = nx + e * 0.45f, ey = ground + 1.9f;
                Intro.quad(eyes, LED, ex - 0.2f, ey - 0.13f * blink, ex + 0.2f, ey + 0.13f * blink, WX0, WY0, WX1, WY1, z + 0.03f, a, false);
            }
        }
        // le chien sort de la niche, aboie deux fois, puis rentre
        float e = Ease.outBack(Ease.range(t, 20, 28), 1.8f) * (1 - Ease.inCubic(Ease.range(t, 50, 58)));
        if (e > 0.03f) {
            boolean bark = (t >= 30 && t < 33) || (t >= 34 && t < 37);
            float ds = Ease.lerp(1.4f, 5.0f, e) * (bark ? 1.06f : 1), dy = Ease.lerp(ground + 1.2f, ground + 2.3f, e);
            Intro.quad(mb, bark ? DOG_B : DOG_A, nx - ds / 2, dy - ds / 2, nx + ds / 2, dy + ds / 2, WX0, WY0, WX1, WY1, z + 0.06f, Ease.lerp(0.25f, 1, Math.min(1, e)), false);
        }
        // ses pattes boueuses traversent l'ecran
        for (int k = 0; k < PAWS; k++) {
            float pt = t - (PAW_T + k * PAW_DT);
            if (pt < 0) break;
            // (maillage opaque : la patte retrecit au lieu de s'effacer)
            float a = Ease.clamp01(pt / 1.5f) * (1 - Ease.range(pt, 5, 9)), sc = 1.5f * Ease.outBack(Ease.clamp01(pt / 3f), 2.2f) * (1 - Ease.range(pt, 5, 9));
            if (a <= 0.01f) continue;
            Intro.spin(paws, PAW, pawX(k), pawY(k), sc, sc, -90 + (k % 2 == 0 ? 8 : -8), z + 0.05f, 1f);
        }
        out.solid(p, mb.buildDynamic(), 0.6f);
        out.solid(p, paws.buildDynamic(), 0.85f);
        out.additive(p, fx.buildDynamic(), 0xFFE0A0, 1f);
        out.additive(p, eyes.buildDynamic(), 0xFFE070, 1f);
        int flags = DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE;
        for (int k = 0; k < 2; k++) {
            float bt = t - (30 + k * 4);
            if (bt < 0 || bt >= 8) continue;
            float sc = Ease.outBack(Ease.clamp01(bt / 3f), 2.6f);
            // un aboiement de chaque cote de la niche
            float x = k == 0 ? nx + 3.3f : nx - 3.3f, y = ground + (k == 0 ? 5.4f : 6.2f) + bt * 0.06f;
            out.text(p, I18n.get("heralizcasino.niche.wouf"), x, y, z + 0.3f, 1.2f * sc, k == 0 ? AMBER : GOLD, 1 - Ease.range(bt, 5, 8), flags, 0x000000);
        }
        Intro.crtOn(p, out, DARK, FLASH, t, WX0, WY0, WX1, WY1, SZ + 0.36f, BLUE);
        Intro.title(p, out, t, I18n.get("heralizcasino.niche.titre"), I18n.get(INTRO_SUB), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, BLUE, GOLD);
    }

    /** Bandeau de titre d'une grille du Multihold (texte dessine apres les maillages). */
    private record Head(int k, Place pl, float a, int state, long winMilli) {
    }

    /** Trait d'energie d'un wild recopie : depart, arrivee, ticks depuis le depart. */
    private record Beam(float x0, float y0, float x1, float y1, float t) {
    }

    /** Calques de l'ecran, tous construits dans la meme image. */
    private static final class Layers {
        /** Fonds et bandeaux des grilles. */
        final MeshBuilder plates = new MeshBuilder();
        /** Symboles. */
        final MeshBuilder mb = new MeshBuilder();
        /** Par-dessus : wilds tenus, cadres collants, grillage, cadenas. */
        final MeshBuilder over = new MeshBuilder();
        /** Symboles gagnants (calque lumineux). */
        final MeshBuilder glow = new MeshBuilder();
        /** Bordures des grilles et lignes gagnantes (couleur des sommets). */
        final MeshBuilder frames = new MeshBuilder();
        /** Eclairs dores. */
        final MeshBuilder flash = new MeshBuilder();
        final List<Head> heads = new ArrayList<>(4);
        final List<Beam> beams = new ArrayList<>();
    }

    private void screen(Scene s, NicheView v, Pose p, DrawList out, float[] at) {
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        Niche.Spin spin = i >= 0 ? v.seq.spins.get(i) : null;
        boolean finished = at != null && v.since >= v.totalTicks();
        boolean idle = spin == null || (finished && v.since > v.totalTicks() + 200);
        Layers L = new Layers();
        float pulse = 0.55f + 0.45f * (float) Math.sin(s.time * 0.6f);
        float dim = idle ? 1 : BigWin.dim(bigAt(spin, lt), Rotten.BIG_WIN_TICKS);
        float m = idle ? 0 : multi(v, i, lt);
        if (idle) {
            idleGrid(s, L, introT);
        } else if (spin.kind == Niche.BASE) {
            baseGrid(v, spin, lt, L, BASE.lerp(slot(0, 1), m), dim, pulse, m);
            if ((spin.events & Niche.EV_TRIGGER) != 0 && lt >= transStart(spin)) transition(s, spin, lt, L, pulse);
        } else {
            multihold(s, v, i, spin, lt, L, dim, pulse, m);
        }
        out.solid(p, m > 0.5f ? barsMulti : barsBase, 0.45f);
        if (!L.plates.isEmpty()) out.solid(p, L.plates.buildDynamic(), 0.3f);
        out.solid(p, L.mb.buildDynamic(), 0.55f);
        if (!L.over.isEmpty()) out.solid(p, L.over.buildDynamic(), 0.6f);
        if (!L.glow.isEmpty()) out.additive(p, L.glow.buildDynamic(), 0xFFFFFF, 0.4f);
        if (!L.frames.isEmpty()) out.additive(p, L.frames.buildDynamic(), 0xFFFFFF, 1f);
        if (!L.flash.isEmpty()) out.additive(p, L.flash.buildDynamic(), GOLD, 1f);
        if (s.item) return;
        for (Beam b : L.beams) beam(s, p, out, b);
        if (!idle) suspense(s, spin, lt, p, out);
        heads(p, out, v, L);
        bars(s, v, p, out, i, spin, lt, idle, m);
        if (!idle) overlays(s, v, p, out, spin, lt);
    }

    // =================================================================== cases
    /** Case (reel, row) d'une grille ; row fractionnaire (rouleau qui defile), decoupee a la hauteur de la grille. */
    private static void cellQuad(MeshBuilder b, Place pl, float reel, float row, Mat m, float tint, float scale, float z) {
        float gap = pl.cw * 0.045f;
        float hw = (pl.cw - gap) / 2 * scale, hh = (pl.ch - gap) / 2 * scale;
        float cx = pl.cx(reel), cy = pl.cy(row);
        BigWin.clipQuad(b, m, cx - hw, cy - hh, cx + hw, cy + hh, pl.y0, pl.y1(), z, tint, false);
    }

    private static void rect(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float z, float tint) {
        BigWin.clipQuad(b, m, x0, y0, x1, y1, -1e9f, 1e9f, z, tint, false);
    }

    /** Quad sans decoupe, couleur des sommets inchangee (bordures et lignes colorees). */
    private static void quadRaw(MeshBuilder b, float x0, float y0, float x1, float y1, float z) {
        float[][] pts = {p(x0, y0, z), p(x1, y0, z), p(x1, y1, z), p(x0, y1, z)};
        float[][] uv = {{LED.u(0), LED.v(1)}, {LED.u(1), LED.v(1)}, {LED.u(1), LED.v(0)}, {LED.u(0), LED.v(0)}};
        b.poly(pts, uv, null, v(0, 0, 1));
    }

    /** Quad tourne de deg degres autour de son centre. */
    private static void rotQuad(MeshBuilder b, Mat m, float cx, float cy, float w, float h, float deg, float z, float tint) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), sn = (float) Math.sin(a), hw = w / 2, hh = h / 2;
        float[][] pts = {p(cx - hw * c + hh * sn, cy - hw * sn - hh * c, z), p(cx + hw * c + hh * sn, cy + hw * sn - hh * c, z),
                p(cx + hw * c - hh * sn, cy + hw * sn + hh * c, z), p(cx - hw * c - hh * sn, cy - hw * sn + hh * c, z)};
        float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
        b.color(tint);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    private static void tint(MeshBuilder b, int rgb, float k) {
        b.color(((rgb >> 16) & 255) / 255f * k, ((rgb >> 8) & 255) / 255f * k, (rgb & 255) / 255f * k);
    }

    /** Segment epais (x0, y0) -> (x1, y1) dans le plan de l'ecran. */
    private static void segment(MeshBuilder b, float x0, float y0, float x1, float y1, float w, float z) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        float[][] pts = {p(x0 - nx, y0 - ny, z), p(x1 - nx, y1 - ny, z), p(x1 + nx, y1 + ny, z), p(x0 + nx, y0 + ny, z)};
        float[][] uv = {{LED.u(0), LED.v(1)}, {LED.u(1), LED.v(1)}, {LED.u(1), LED.v(0)}, {LED.u(0), LED.v(0)}};
        b.poly(pts, uv, null, v(0, 0, 1));
    }

    private static void flashAt(MeshBuilder b, float x, float y, float size, float k) {
        if (k <= 0.01f) return;
        rect(b, FLASH, x - size / 2, y - size / 2, x + size / 2, y + size / 2, SZ + 0.09f, k);
    }

    /** Wild tenu (collant) : le symbole et son cadre dore riveté par-dessus le rouleau qui tourne derriere. */
    private static void heldWild(Layers L, Place pl, int reel, float row, int mult, float tint, float pop, float frameK) {
        cellQuad(L.over, pl, reel, row, symbol(Niche.WILD, mult, false), tint, pop, SZ + 0.05f);
        if (frameK > 0.02f) cellQuad(L.over, pl, reel, row, STICKY, tint * frameK, pop * 1.04f, SZ + 0.07f);
    }

    /** Cases des lignes gagnantes (masque de cases). */
    static int lineCells(int[] sym, int lines) {
        int m = 0;
        for (int l = 0; l < Niche.LINES.length; l++) {
            if ((lines & (1 << l)) == 0) continue;
            int len = Niche.lineLength(sym, Niche.LINES[l]);
            for (int r = 0; r < len; r++) m |= 1 << Niche.cell(r, Niche.LINES[l][r]);
        }
        return m;
    }

    /** Lignes gagnantes d'une grille : toutes en filigrane, celle du moment (10 ticks chacune) bien visible. */
    private static void linePaths(Layers L, Place pl, int[] sym, int lines, float t, float k) {
        int n = Integer.bitCount(lines);
        if (n == 0 || t < 0) return;
        int cur = (int) (t / 10) % n, idx = 0;
        float w = pl.cw * 0.07f;
        for (int l = 0; l < Niche.LINES.length; l++) {
            if ((lines & (1 << l)) == 0) continue;
            boolean on = idx++ == cur;
            int[] line = Niche.LINES[l];
            int len = Niche.lineLength(sym, line);
            int col = LINE_COLORS[l % LINE_COLORS.length];
            for (int r = 0; r + 1 < Niche.REELS; r++) {
                boolean win = r + 1 < len;
                if (!on && !win) break;
                tint(L.frames, col, k * (on ? (win ? 1f : 0.35f) : 0.28f));
                segment(L.frames, pl.cx(r), pl.cy(line[r]), pl.cx(r + 1), pl.cy(line[r + 1]), on ? w * 1.5f : w, SZ + 0.1f);
            }
            if (on) {
                tint(L.frames, col, k);
                for (int r = 0; r < len; r++) {
                    float x = pl.cx(r), y = pl.cy(line[r]), d = pl.cw * 0.09f;
                    quadRaw(L.frames, x - d, y - d, x + d, y + d, SZ + 0.11f);
                }
            }
        }
        L.frames.color(1);
    }

    /** Symbole de remplissage pendant la rotation (deterministe). */
    private static int filler(int key, int reel, int k) {
        float h = Ease.hash(key * 7919 + reel * 131 + k * 17);
        return h < 0.55f ? (int) (h / 0.55f * 5) : Math.min(10, 5 + (int) ((h - 0.55f) / 0.45f * 6));
    }

    /**
     * Rouleau qui tourne : une bande virtuelle defile vers le bas, le resultat en haut (cases 0 a 2), puis le
     * remplissage, puis l'ancien affichage. Un rouleau en suspens finit au ralenti ; s'il ne porte pas de patte, il en
     * fait parfois passer une sous les yeux du joueur... qui sort par le bas.
     * @param t    ticks depuis le depart du rouleau
     * @param fast cases parcourues a pleine vitesse
     */
    private static void spinReel(MeshBuilder mb, Place pl, int reel, int[] sym, int[] mult, int[] prev, int[] prevMult, float t, int stop, int fast,
                                 boolean teased, int key, float tint) {
        int d = fast + Niche.ROWS + (teased ? (int) SLOW_CELLS : 0);
        float off, speed;
        if (teased) {
            float slowAt = stop - SLOW;
            int fc = d - (int) SLOW_CELLS;
            off = Tease.travel(t, slowAt, stop, fc, SLOW_CELLS);
            speed = Tease.travel(t + 0.5f, slowAt, stop, fc, SLOW_CELLS) - Tease.travel(t - 0.5f, slowAt, stop, fc, SLOW_CELLS);
        } else {
            float span = Math.max(1f, stop - 1);
            off = d * SlotView.travel(Math.max(0, t - 1) / span);
            speed = d * (SlotView.travel(Math.max(0, t - 0.5f) / span) - SlotView.travel(Math.max(0, t - 1.5f) / span));
        }
        boolean blur = speed > 0.3f;
        int bait = -1;
        if (teased) {
            boolean has = false;
            for (int row = 0; row < Niche.ROWS; row++) has |= sym[Niche.cell(reel, row)] == Niche.BONUS;
            if (!has && Ease.hash(key * 31 + sym[Niche.cell(reel, 0)] * 131 + sym[Niche.cell(reel, 1)] * 17 + sym[Niche.cell(reel, 2)]) < 0.55f) bait = Niche.BONUS;
        }
        float base = d - off;
        int first = (int) Math.floor(base);
        for (int k = Math.max(0, first); k <= first + Niche.ROWS; k++) {
            int s, m = 0;
            if (k < Niche.ROWS) {
                int c = Niche.cell(reel, k);
                s = sym[c];
                m = mult[c];
            } else if (k >= d) {
                int c = Niche.cell(reel, Math.min(Niche.ROWS - 1, k - d));
                s = prev[c];
                m = prevMult[c];
            } else if (k == Niche.ROWS && bait >= 0) {
                s = bait;
            } else {
                s = filler(key, reel, k);
            }
            cellQuad(mb, pl, reel, k - base, symbol(s, m, blur), tint, 1, SZ + 0.03f);
        }
    }

    /** Grille au repos ; a la fin de l'intro (introT), les colonnes tombent l'une apres l'autre. */
    private static void idleGrid(Scene s, Layers L, float introT) {
        int slot = (int) (s.time / 40), pick = (int) (Ease.hash(slot + (int) (s.seed * 50)) * Niche.CELLS);
        for (int c = 0; c < Niche.CELLS; c++) {
            int reel = c / Niche.ROWS;
            float k = !s.item && pick == c ? 1f + 0.5f * Ease.bell(s.time % 40, 0, 20) : 1f;
            k *= 1 + 0.5f * Intro.landed(introT, reel);
            cellQuad(L.mb, BASE, reel, c % Niche.ROWS - Intro.drop(introT, reel, Niche.ROWS), symbol(IDLE[c], IDLE_MULT[c], false), k, 1, SZ + 0.03f);
        }
        for (int reel = 0; reel < Niche.REELS; reel++) {
            float land = Intro.landed(introT, reel), x = BASE.cx(reel);
            if (land > 0) rect(L.flash, FLASH, x - CW * 0.8f, RY0 - CH * 0.8f, x + CW * 0.8f, RY0 + CH * 0.8f, SZ + 0.08f, 0.7f * land);
        }
    }

    // =================================================================== jeu normal
    /**
     * Tour normal : rouleaux, lignes gagnantes, pattes du declenchement ; pendant la mise en place du bonus, la grille
     * glisse et retrecit jusqu'a l'emplacement de la grille 1 (morph de 0 a 1).
     */
    private void baseGrid(NicheView v, Niche.Spin spin, float lt, Layers L, Place pl, float dim, float pulse, float morph) {
        int[] sym = spin.sym[0], mult = spin.mult[0];
        int end = Niche.spinEnd(spin), show = Niche.winShow(spin);
        int lines = spin.lines[0];
        boolean showLines = lines != 0 && lt >= end && lt < end + show;
        int winCells = showLines ? lineCells(sym, lines) : 0;
        boolean trig = (spin.events & Niche.EV_TRIGGER) != 0;
        float trigK = trig ? Ease.range(lt, trigStart(spin), trigStart(spin) + 8) * (1 - morph) : 0;
        float[] tz = teasing(spin, lt);
        float tension = tz == null ? 0 : Suspense.tension(tz[1], SLOW);
        for (int reel = 0; reel < Niche.REELS; reel++) {
            int stop = Niche.stopTick(spin, reel);
            boolean teased = Niche.tease(spin, 0, reel);
            if (lt < stop) {
                spinReel(L.mb, pl, reel, sym, mult, IDLE, IDLE_MULT, lt, stop, fast(stop, teased), teased, 1, 1f);
                continue;
            }
            float bounce = Ease.spring(lt - stop, 1.1f, 0.45f) * 0.1f;
            for (int row = 0; row < Niche.ROWS; row++) {
                int c = Niche.cell(reel, row), sy = sym[c];
                boolean bonus = sy == Niche.BONUS;
                float tint = dim;
                if (showLines) tint *= (winCells & (1 << c)) != 0 ? 1.15f : 0.45f;
                if (trig && bonus && lt >= end) tint *= 1.1f + 0.3f * pulse * (1 - morph);
                if (!bonus) tint *= 1 - 0.7f * trigK;
                if ((bonus || sy == Niche.WILD) && lt < stop + 10) tint *= 1 + 0.4f * Ease.bell(lt, stop, stop + 10);
                if (tz != null) tint *= bonus ? 1.15f + 0.25f * pulse : Tease.dim(tension);
                cellQuad(L.mb, pl, reel, row - bounce, symbol(sy, mult[c], false), tint, 1, SZ + 0.03f);
                if (showLines && (winCells & (1 << c)) != 0) cellQuad(L.glow, pl, reel, row, symbol(sy, mult[c], false), pulse * dim, 1, SZ + 0.06f);
                if (trig && bonus && lt >= trigStart(spin) && morph < 0.6f) {
                    cellQuad(L.glow, pl, reel, row, BONUS_M, pulse * (1 - morph), 1, SZ + 0.06f);
                    flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw * 1.7f, (0.25f + 0.2f * pulse) * (1 - morph));
                }
                if (teased && bonus && lt < stop + 12) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw * 2.2f, 1 - Ease.range(lt, stop, stop + 12));
            }
        }
        if (showLines) linePaths(L, pl, sym, lines, lt - end, dim);
    }

    /** Mise en place des quatre grilles : la grille 1 s'allume, les trois autres apparaissent verrouillees. */
    private void transition(Scene s, Niche.Spin spin, float lt, Layers L, float pulse) {
        float tr = lt - transStart(spin);
        float a0 = Ease.range(tr, 8, 24);
        if (a0 > 0) {
            Place pl = slot(0, 1);
            chrome(L, pl, a0, LATEST, pulse);
            L.heads.add(new Head(0, pl, a0, LATEST, 0));
        }
        for (int k = 1; k < Niche.GRIDS; k++) {
            float ap = Ease.range(tr, 10 + 5 * k, 20 + 5 * k);
            if (ap <= 0) continue;
            Place pl = slot(k, 0.85f + 0.15f * Ease.outBack(ap, 1.6f));
            chrome(L, pl, ap, LOCKED, pulse);
            lockedGrid(s, L, k, pl, ap, -1);
            L.heads.add(new Head(k, pl, ap, LOCKED, 0));
        }
    }

    // =================================================================== Multihold
    /** Fond, bandeau de titre et bordure lumineuse d'une grille (allumee en ambre : c'est la que tombent les pattes). */
    private static void chrome(Layers L, Place pl, float a, int state, float pulse) {
        float hs = pl.scale(), m = 0.1f * hs;
        float hy0 = pl.y1() + 0.04f * hs, hy1 = pl.y1() + (0.04f + HEAD) * hs;
        rect(L.plates, DARK, pl.x0() - m, pl.y0() - m, pl.x1() + m, hy1 + m * 0.5f, SZ + 0.01f, 0.9f * a);
        rect(L.plates, BAR, pl.x0(), hy0, pl.x1(), hy1, SZ + 0.02f, a);
        int col = state == LATEST ? AMBER : state == OPEN ? BLUE : GREY;
        float k = (state == LATEST ? 0.75f + 0.25f * pulse : state == OPEN ? 0.45f : 0.25f) * a;
        float x0 = pl.x0() - m, y0 = pl.y0() - m, x1 = pl.x1() + m, y1 = hy1 + m * 0.5f, w = 0.07f * hs, z = SZ + 0.025f;
        tint(L.frames, col, k);
        quadRaw(L.frames, x0, y0, x1, y0 + w, z);
        quadRaw(L.frames, x0, y1 - w, x1, y1, z);
        quadRaw(L.frames, x0, y0 + w, x0 + w, y1 - w, z);
        quadRaw(L.frames, x1 - w, y0 + w, x1, y1 - w, z);
        L.frames.color(1);
    }

    /**
     * Grille verrouillee : cases vides sous un grillage, cadenas qui se balance. Pendant le deblocage (lockT : ticks
     * depuis son debut), le cadenas tremble, saute (l'anse s'envole, le corps tombe) et le grillage s'efface.
     */
    private static void lockedGrid(Scene s, Layers L, int k, Place pl, float a, float lockT) {
        float open = lockT < 0 ? 0 : Ease.range(lockT, Niche.UNLOCK_BREAK - 4, Niche.UNLOCK_BREAK + 6);
        if (lockT < Niche.UNLOCK_BREAK) {
            for (int c = 0; c < Niche.CELLS; c++) cellQuad(L.mb, pl, c / Niche.ROWS, c % Niche.ROWS, EMPTY_M, 0.6f * a, 1, SZ + 0.03f);
        }
        if (open < 1) rect(L.over, FENCE, pl.x0(), pl.y0(), pl.x1(), pl.y1(), SZ + 0.05f, (1 - open) * 0.85f * a);
        float cx = pl.midX(), cy = pl.midY(), size = pl.ch() * 1.75f;
        float swing = (float) Math.sin(s.time * 0.07f + k * 1.3f) * 5;
        float brk = Niche.UNLOCK_BREAK - 4;
        if (lockT < brk) {
            float shake = lockT < Niche.UNLOCK_LOCK ? 0 : (float) Math.sin(lockT * 2.6f) * (4 + 1.6f * Math.min(10, lockT - Niche.UNLOCK_LOCK));
            rotQuad(L.over, LOCK, cx, cy, size, size, swing + shake, SZ + 0.08f, a);
            if (lockT >= 0) flashAt(L.flash, cx, cy, size * 1.6f, 0.5f * Ease.range(lockT, 0, brk));
            return;
        }
        float bt = lockT - brk, fade = 1 - Ease.range(bt, 6, 14);
        if (fade > 0) {
            float up = 1.4f * Ease.outCubic(Ease.clamp01(bt / 8f)) * pl.ch(), down = 2.4f * Ease.inQuad(Ease.clamp01(bt / 12f)) * pl.ch();
            rotQuad(L.over, LOCK_TOP, cx + bt * 0.06f, cy + size * (0.5f - LOCK_SPLIT / 2) + up, size, size * LOCK_SPLIT, 40 * Ease.clamp01(bt / 10f), SZ + 0.09f, fade * a);
            rotQuad(L.over, LOCK_BODY, cx - bt * 0.03f, cy - size * LOCK_SPLIT / 2 - down, size, size * (1 - LOCK_SPLIT), -25 * Ease.clamp01(bt / 12f), SZ + 0.09f, fade * a);
        }
        flashAt(L.flash, cx, cy, size * 2.4f, Ease.bell(bt, -1, 8));
    }

    /** Grille affichee juste avant le tour i (fin du tour precedent). */
    private static int[] prevSym(NicheView v, int i, int k) {
        if (i <= 0) return IDLE;
        Niche.Spin p = v.seq.spins.get(i - 1);
        return k < p.grids ? p.sym[k] : BLANK;
    }

    private static int[] prevMult(NicheView v, int i, int k) {
        if (i <= 0) return IDLE_MULT;
        Niche.Spin p = v.seq.spins.get(i - 1);
        return k < p.grids ? p.mult[k] : NONE;
    }

    /** Tours gratuits : les quatre emplacements, leurs rouleaux, verrous, deblocages, gains ; m < 1 : fin du bonus. */
    private void multihold(Scene s, NicheView v, int i, Niche.Spin spin, float lt, Layers L, float dim, float pulse, float m) {
        int lg = latest(spin, lt);
        for (int k = 0; k < Niche.GRIDS; k++) {
            float gone = gone(spin, k, lt);
            if (gone >= 1) continue;
            float scale = 1 - 0.35f * Ease.inCubic(gone), fadeK = 1 - gone;
            if (k >= spin.grids) {
                Place pl = slot(k, scale);
                chrome(L, pl, fadeK, LOCKED, pulse);
                lockedGrid(s, L, k, pl, fadeK, -1);
                L.heads.add(new Head(k, pl, fadeK, LOCKED, 0));
            } else if (k >= spin.startGrids) {
                unlocking(s, v, i, spin, k, lt, L, dim * fadeK, pulse, scale, lg);
            } else {
                // fin du bonus : la grille 1 reprend la place du jeu normal
                Place pl = k == 0 ? slot(0, 1).lerp(BASE, 1 - m) : slot(k, scale);
                float a = k == 0 ? m : fadeK;
                if (a > 0.01f) {
                    chrome(L, pl, a, k == lg ? LATEST : OPEN, pulse);
                    L.heads.add(new Head(k, pl, a, k == lg ? LATEST : OPEN, winShown(spin, k, lt)));
                }
                openGrid(v, i, spin, k, lt, L, pl, dim * fadeK, pulse, k == 0 ? m : 1);
            }
        }
    }

    /** Gain de la grille k affiche dans son bandeau (de l'annonce des gains a la fin du tour), 0 sinon. */
    private static long winShown(Niche.Spin spin, int k, float lt) {
        return lt >= Niche.winStart(spin) ? spin.gridMilli[k] : 0;
    }

    /** Grille ouverte des le debut du tour : ses rouleaux tournent (les wilds collants restent), puis ses gains. */
    private void openGrid(NicheView v, int i, Niche.Spin spin, int k, float lt, Layers L, Place pl, float dim, float pulse, float frameK) {
        int[] sym = spin.sym[k], mult = spin.mult[k];
        float t = lt - Niche.spinStart(spin);
        int held = spin.sticky[k] & ~spin.fresh[k];
        int[] prev = prevSym(v, i, k), prevM = prevMult(v, i, k);
        int lines = spin.lines[k], winAt = Niche.winStart(spin);
        boolean showWin = lines != 0 && lt >= winAt && lt < winAt + Niche.winShow(spin);
        int winCells = showWin ? lineCells(sym, lines) : 0;
        boolean latestAtStart = k == spin.startGrids - 1 && spin.startGrids < Niche.GRIDS;
        // grille source d'un deblocage : ses pattes brillent, puis ses wilds envoient leur copie
        int src = k - (spin.startGrids - 1);
        float ut = src >= 0 && src < spin.unlocked ? unlockT(spin, src, lt) : -1e6f;
        boolean paws = ut >= -2 && ut < Niche.UNLOCK_BREAK + 6;
        boolean sending = src >= 0 && src < spin.unlocked && ut >= Niche.UNLOCK_GROW - 4 && ut < addStart(spin, src) + 2;
        float[] tz = latestAtStart ? teasing(spin, lt) : null;
        float tension = tz == null ? 0 : Suspense.tension(tz[1], SLOW);
        for (int reel = 0; reel < Niche.REELS; reel++) {
            int stop = Niche.stopTick(spin, reel);
            boolean teased = latestAtStart && Niche.tease(spin, k, reel);
            if (t < stop) {
                spinReel(L.mb, pl, reel, sym, mult, prev, prevM, t, stop, fast(stop, teased), teased, i * 7 + k, dim);
                for (int row = 0; row < Niche.ROWS; row++) {
                    int c = Niche.cell(reel, row);
                    if ((held & (1 << c)) != 0) heldWild(L, pl, reel, row, mult[c], dim, 1, frameK);
                }
                continue;
            }
            float bounce = Ease.spring(t - stop, 1.1f, 0.45f) * 0.08f;
            for (int row = 0; row < Niche.ROWS; row++) {
                int c = Niche.cell(reel, row), sy = sym[c];
                boolean isHeld = (held & (1 << c)) != 0, fresh = (spin.fresh[k] & (1 << c)) != 0, bonus = sy == Niche.BONUS;
                float tint = dim;
                if (showWin) tint *= (winCells & (1 << c)) != 0 ? 1.15f : 0.45f;
                if (paws && bonus) tint *= 1.15f + 0.3f * pulse;
                if (sending && sy == Niche.WILD) tint *= 1.1f + 0.3f * pulse;
                if (tz != null) tint *= bonus ? 1.15f + 0.25f * pulse : Tease.dim(tension);
                if (isHeld) {
                    heldWild(L, pl, reel, row, mult[c], tint, 1, frameK);
                } else if (fresh) {
                    // le wild tombe et se fige : son cadre dore se referme dessus
                    float snap = Ease.outBack(Ease.range(t, stop, stop + 6), 2f);
                    cellQuad(L.mb, pl, reel, row - bounce, symbol(sy, mult[c], false), tint, 1, SZ + 0.03f);
                    if (frameK > 0.02f) cellQuad(L.over, pl, reel, row - bounce, STICKY, tint * frameK, 1.4f - 0.36f * snap, SZ + 0.07f);
                    if (t < stop + 10) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.8f, 1 - Ease.range(t, stop, stop + 10));
                } else {
                    if (bonus && t < stop + 10) tint *= 1 + 0.4f * Ease.bell(t, stop, stop + 10);
                    cellQuad(L.mb, pl, reel, row - bounce, symbol(sy, mult[c], false), tint, 1, SZ + 0.03f);
                }
                if (showWin && (winCells & (1 << c)) != 0) cellQuad(L.glow, pl, reel, row, symbol(sy, mult[c], false), pulse * dim, 1, SZ + 0.06f);
                if (paws && bonus && ut >= 0) {
                    cellQuad(L.glow, pl, reel, row, BONUS_M, pulse, 1, SZ + 0.06f);
                    flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.9f, 0.6f * Ease.bell(ut, 0, Niche.UNLOCK_BREAK + 6));
                }
                if (sending && sy == Niche.WILD) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.5f, 0.3f + 0.2f * pulse);
                if (teased && bonus && t < stop + 12) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 2.2f, 1 - Ease.range(t, stop, stop + 12));
            }
        }
        if (showWin) linePaths(L, pl, sym, lines, lt - winAt, dim);
    }

    /**
     * Grille qui s'ouvre pendant ce tour (deblocage u) : verrou qui tremble puis saute, grille qui grandit, copie des
     * wilds collants de la grille precedente (un trait d'energie par wild), +N tours, puis ses rouleaux tournent.
     */
    private void unlocking(Scene s, NicheView v, int i, Niche.Spin spin, int k, float lt, Layers L, float dim, float pulse, float scale, int lg) {
        int u = k - spin.startGrids;
        float ut = unlockT(spin, u, lt);
        if (ut < Niche.UNLOCK_BREAK) {
            Place pl = slot(k, scale);
            chrome(L, pl, 1, LOCKED, pulse);
            lockedGrid(s, L, k, pl, 1, ut < 0 ? -1 : ut);
            L.heads.add(new Head(k, pl, 1, LOCKED, 0));
            return;
        }
        float grow = Ease.outBack(Ease.range(ut, Niche.UNLOCK_BREAK, Niche.UNLOCK_BREAK + 10), 1.7f);
        Place pl = slot(k, (0.84f + 0.16f * grow) * scale);
        int state = lg == k ? LATEST : OPEN;
        chrome(L, pl, 1, state, pulse);
        L.heads.add(new Head(k, pl, 1, state, winShown(spin, k, lt)));
        if (ut < Niche.UNLOCK_BREAK + 14) lockedGrid(s, L, k, pl, 1, ut);
        int copied = Niche.copiedMask(spin, u);
        float step = copyStep(spin, u);
        Place src = slot(k - 1, 1);
        float[] arrive = new float[Niche.CELLS];
        int j = 0;
        for (int c = 0; c < Niche.CELLS; c++) {
            if ((copied & (1 << c)) == 0) continue;
            float a0 = Niche.UNLOCK_GROW + j++ * step;
            arrive[c] = a0 + COPY_FLY;
            float bt = ut - a0;
            if (bt >= 0 && bt < COPY_FLY + 5) {
                L.beams.add(new Beam(src.cx(c / Niche.ROWS), src.cy(c % Niche.ROWS), pl.cx(c / Niche.ROWS), pl.cy(c % Niche.ROWS), bt));
            }
        }
        int[] sym = spin.sym[k], mult = spin.mult[k];
        float ns = ut - newSpinStart(spin, u);
        int lines = spin.lines[k], winAt = Niche.winStart(spin);
        boolean showWin = lines != 0 && lt >= winAt && lt < winAt + Niche.winShow(spin);
        int winCells = showWin ? lineCells(sym, lines) : 0;
        // la grille ouverte sert a son tour de source si elle debloque la suivante dans le meme tour
        float next = u + 1 < spin.unlocked ? unlockT(spin, u + 1, lt) : -1e6f;
        boolean paws = next >= -2 && next < Niche.UNLOCK_BREAK + 6;
        boolean sending = u + 1 < spin.unlocked && next >= Niche.UNLOCK_GROW - 4 && next < addStart(spin, u + 1) + 2;
        float light = 0.45f + 0.55f * Ease.clamp01(grow);
        for (int reel = 0; reel < Niche.REELS; reel++) {
            int stop = newStop(reel);
            boolean spinning = ns >= 0 && ns < stop;
            if (spinning) spinReel(L.mb, pl, reel, sym, mult, BLANK, NONE, ns, stop, fast(stop, false), false, i * 7 + k + 3, dim);
            float bounce = ns >= stop ? Ease.spring(ns - stop, 1.1f, 0.45f) * 0.08f : 0;
            for (int row = 0; row < Niche.ROWS; row++) {
                int c = Niche.cell(reel, row), sy = sym[c];
                if ((copied & (1 << c)) != 0) {
                    if (ut < arrive[c]) {
                        if (!spinning) cellQuad(L.mb, pl, reel, row, EMPTY_M, 0.75f * dim * light, 1, SZ + 0.03f);
                        continue;
                    }
                    float pop = 1 + 0.35f * Ease.bell(ut, arrive[c], arrive[c] + 6);
                    float tint = dim * (sending ? 1.1f + 0.3f * pulse : 1);
                    if (showWin) tint *= (winCells & (1 << c)) != 0 ? 1.15f : 0.45f;
                    heldWild(L, pl, reel, row, mult[c], tint, pop, 1);
                    if (ut < arrive[c] + 8) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.9f, 1 - Ease.range(ut, arrive[c], arrive[c] + 8));
                    if (showWin && (winCells & (1 << c)) != 0) cellQuad(L.glow, pl, reel, row, symbol(sy, mult[c], false), pulse * dim, 1, SZ + 0.08f);
                    continue;
                }
                if (spinning) continue;
                if (ns < 0) {
                    cellQuad(L.mb, pl, reel, row, EMPTY_M, 0.75f * dim * light, 1, SZ + 0.03f);
                    continue;
                }
                boolean fresh = (spin.fresh[k] & (1 << c)) != 0, bonus = sy == Niche.BONUS;
                float tint = dim;
                if (showWin) tint *= (winCells & (1 << c)) != 0 ? 1.15f : 0.45f;
                if (paws && bonus) tint *= 1.15f + 0.3f * pulse;
                if (sending && sy == Niche.WILD) tint *= 1.1f + 0.3f * pulse;
                if (bonus && ns < stop + 10) tint *= 1 + 0.4f * Ease.bell(ns, stop, stop + 10);
                cellQuad(L.mb, pl, reel, row - bounce, symbol(sy, mult[c], false), tint, 1, SZ + 0.03f);
                if (fresh) {
                    float snap = Ease.outBack(Ease.range(ns, stop, stop + 6), 2f);
                    cellQuad(L.over, pl, reel, row - bounce, STICKY, tint, 1.4f - 0.36f * snap, SZ + 0.07f);
                    if (ns < stop + 10) flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.8f, 1 - Ease.range(ns, stop, stop + 10));
                }
                if (showWin && (winCells & (1 << c)) != 0) cellQuad(L.glow, pl, reel, row, symbol(sy, mult[c], false), pulse * dim, 1, SZ + 0.06f);
                if (paws && bonus && next >= 0) {
                    cellQuad(L.glow, pl, reel, row, BONUS_M, pulse, 1, SZ + 0.06f);
                    flashAt(L.flash, pl.cx(reel), pl.cy(row), pl.cw() * 1.9f, 0.6f * Ease.bell(next, 0, Niche.UNLOCK_BREAK + 6));
                }
            }
        }
        if (showWin) linePaths(L, pl, sym, lines, lt - winAt, dim);
    }

    /** Trait d'energie : du wild de la grille precedente vers la meme case de la nouvelle grille. */
    private static void beam(Scene s, Pose p, DrawList out, Beam b) {
        float k = Ease.inOutSine(Ease.clamp01(b.t() / COPY_FLY));
        float hx = Ease.lerp(b.x0(), b.x1(), k), hy = Ease.lerp(b.y0(), b.y1(), k);
        float fade = 1 - Ease.range(b.t(), COPY_FLY, COPY_FLY + 5);
        float dx = hx - b.x0(), dy = hy - b.y0(), len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0.05f) {
            float ang = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90;
            p.push().translate(b.x0(), b.y0(), SZ + 0.3f).rotZ(ang);
            out.quad(p, Sprite.BEAM, -0.32f, 0, 0.32f, len, GOLD, 0.85f * fade);
            p.pop();
        }
        out.billboard(p, Sprite.HALO, hx, hy, SZ + 0.32f, 1.3f, 0, GOLD, 0.6f * fade);
        out.billboard(p, Sprite.FLARE, hx, hy, SZ + 0.36f, 0.9f, s.time * 12, 0xFFF0B0, fade);
    }

    /** Suspense : colonne de lumiere sur le 5e rouleau, braises sur les deux pattes deja tombees. */
    private void suspense(Scene s, Niche.Spin spin, float lt, Pose p, DrawList out) {
        float[] tz = teasing(spin, lt);
        if (tz == null) return;
        int g = (int) tz[0], reel = Niche.REELS - 1;
        Place pl = spin.kind == Niche.BASE ? BASE : slot(g, 1);
        float tension = Suspense.tension(tz[1], SLOW);
        Tease.column(s, p, out, pl.x0() + reel * pl.cw(), pl.y0(), pl.x0() + (reel + 1) * pl.cw(), pl.y1(), SZ + 0.1f, AMBER, tension);
        int[] sym = spin.sym[g];
        for (int c = 0; c < reel * Niche.ROWS; c++) {
            if (sym[c] == Niche.BONUS) Tease.ember(s, p, out, pl.cx(c / Niche.ROWS), pl.cy(c % Niche.ROWS), SZ + 0.25f, pl.cw() * 0.75f, AMBER, tension);
        }
    }

    // =================================================================== textes
    static String amountText(NicheView v, long milli) {
        if (v.bet <= 0) return "x" + mult(milli / 1000.0);
        return fmt((long) Math.floor(v.bet * milli / 1000.0));
    }

    static String mult(double x) {
        return x == Math.floor(x) ? String.valueOf((long) x) : String.format(Locale.ROOT, "%.1f", x);
    }

    /** Bandeaux des grilles : numero a gauche ; a droite, le gain de la grille, "PATTES ICI" ou "VERROUILLEE". */
    private void heads(Pose p, DrawList out, NicheView v, Layers L) {
        for (Head h : L.heads) {
            if (h.a() < 0.05f) continue;
            Place pl = h.pl();
            float hs = pl.scale(), y = pl.y1() + (0.04f + HEAD / 2) * hs, z = SZ + 0.09f;
            int col = h.state() == LATEST ? AMBER : h.state() == OPEN ? WHITE : GREY;
            out.text(p, I18n.get("heralizcasino.niche.grille", String.valueOf(h.k() + 1)), pl.x0() + 0.15f * hs, y, z, 0.34f * hs, col, h.a(), DrawList.T_BOLD);
            if (h.winMilli() > 0) {
                String w = "+" + amountText(v, h.winMilli());
                out.text(p, w, pl.x1() - 0.15f * hs, y, z, fit(w, 0.42f, GW * 0.5f, true) * hs, GOLD, h.a(), DrawList.T_RIGHT | DrawList.T_BOLD);
            } else if (h.state() == LOCKED) {
                out.text(p, I18n.get("heralizcasino.niche.verrouillee"), pl.x1() - 0.15f * hs, y, z, 0.28f * hs, GREY, h.a(), DrawList.T_RIGHT);
            } else if (h.state() == LATEST && h.k() < Niche.GRIDS - 1) {
                out.text(p, I18n.get("heralizcasino.niche.pattes_ici"), pl.x1() - 0.15f * hs, y, z, 0.28f * hs, AMBER, h.a(), DrawList.T_RIGHT | DrawList.T_BOLD);
            }
        }
    }

    /** Gain cumule (millemes) affiche au temps lt du tour i. */
    static long gainSoFar(NicheView v, int i, Niche.Spin spin, float lt) {
        long milli = v.winSoFar(i, false);
        if (spin.kind == Niche.BASE) {
            if (lt >= Niche.spinEnd(spin)) milli += spin.gridMilli[0];
            if ((spin.events & Niche.EV_TRIGGER) != 0 && lt >= trigStart(spin) + 12) milli += spin.bonusMilli;
        } else if (lt >= Niche.winStart(spin)) {
            milli += spin.winMilli;
        }
        return Math.min(milli, v.seq.totalMilli);
    }

    /** Bandeaux de l'ecran : en haut le titre (ou les tours restants), en bas mise, gain et lignes (ou grilles). */
    private void bars(Scene s, NicheView v, Pose p, DrawList out, int i, Niche.Spin spin, float lt, boolean idle, float m) {
        float z = SZ + 0.08f;
        int bold = DrawList.T_CENTER | DrawList.T_BOLD;
        if (m <= 0.5f) {
            float yt = (RY1 + WY1) / 2, yb = (WY0 + RY0) / 2 - 0.05f;
            float[] tz = idle ? null : teasing(spin, lt);
            if (tz != null) {
                float a = Ease.clamp01(tz[1] / 4f);
                String encore = I18n.get("heralizcasino.niche.encore_patte"), pour = I18n.get("heralizcasino.slot.pour_tours");
                out.text(p, encore, 8, yt + 0.5f, z, fit(encore, 1.05f, 11.4f, true), AMBER, a * (0.7f + 0.3f * s.pulse(0.5f)), bold);
                out.text(p, pour, 8, yt - 0.85f, z, fit(pour, 0.5f, 11.4f, false), WHITE, a * 0.9f, DrawList.T_CENTER);
            } else {
                out.text(p, "MULTIHOLD", 8, yt + 0.45f, z, fit("MULTIHOLD", 1.0f, 11f, true), BLUE, 1f, bold);
                String sub = I18n.get("heralizcasino.niche.collants");
                out.text(p, sub, 8, yt - 0.8f, z, fit(sub, 0.5f, 11.4f, false), GOLD, 0.95f, DrawList.T_CENTER);
            }
            if (idle) {
                int phase = ((int) (s.time / 20)) % 3;
                String txt = phase == 0 ? I18n.get("heralizcasino.niche.idle_pattes", String.valueOf(v.rules.freeSpins), mult(v.rules.scatterPay))
                        : I18n.get(phase == 1 ? "heralizcasino.niche.idle_wild" : "heralizcasino.niche.idle_lignes");
                out.text(p, txt, 8, yb, z, fit(txt, 0.58f, 11.4f, false), phase == 0 ? AMBER : phase == 1 ? GOLD : BLUE, 0.95f, DrawList.T_CENTER);
                return;
            }
            if (tz != null) return;
            bottomLine(v, p, out, i, spin, lt, yb, z, I18n.get("heralizcasino.niche.lignes"), WHITE);
            return;
        }
        float yt = (TOP_BAR + WY1) / 2, yb = (WY0 + LOW_BAR) / 2;
        boolean free = spin.kind == Niche.FREE;
        int left = free ? spinsShown(spin, lt) : v.rules.freeSpins;
        boolean last = free && left == 0;
        String big = last ? I18n.get("heralizcasino.slot.dernier_tour")
                : I18n.get(free ? "heralizcasino.niche.tours_restants" : "heralizcasino.niche.tours_gratuits", String.valueOf(left));
        out.text(p, big, 8, yt + 0.45f, z, fit(big, 0.95f, 11.2f, true), last ? RED : GREEN, 1f, bold);
        int open = latest(spin, lt) + 1;
        String sub;
        int col;
        if (free && spin.winMilli > 0 && lt >= Niche.winStart(spin)) {
            sub = I18n.get("heralizcasino.niche.ce_tour", amountText(v, spin.winMilli));
            col = GOLD;
        } else if (open < Niche.GRIDS) {
            sub = I18n.get("heralizcasino.niche.grille_suivante");
            col = AMBER;
        } else {
            sub = I18n.get("heralizcasino.niche.quatre_grilles");
            col = GOLD;
        }
        out.text(p, sub, 8, yt - 0.75f, z, fit(sub, 0.48f, 11.4f, false), col, 0.95f, DrawList.T_CENTER);
        bottomLine(v, p, out, i, spin, lt, yb, z, I18n.get("heralizcasino.niche.grilles", String.valueOf(open), String.valueOf(Niche.GRIDS)), open == Niche.GRIDS ? GOLD : BLUE);
    }

    private void bottomLine(NicheView v, Pose p, DrawList out, int i, Niche.Spin spin, float lt, float y, float z, String right, int col) {
        // trois zones qui ne se touchent jamais : mise a gauche, gain au centre, lignes / grilles a droite
        String mise = I18n.get("heralizcasino.slot.mise", fmt(v.bet));
        out.text(p, mise, WX0 + 0.3f, y, z, fit(mise, 0.5f, 2.9f, false), WHITE, 0.9f, 0);
        long won = (long) Math.floor(v.bet * (gainSoFar(v, i, spin, lt) / 1000.0));
        String g = I18n.get("heralizcasino.slot.gain", fmt(won));
        out.text(p, g, 7.9f, y, z, fit(g, 0.62f, 3.9f, false), won > 0 ? GOLD : WHITE, 1f, DrawList.T_CENTER);
        out.text(p, right, WX1 - 0.3f, y, z, fit(right, 0.5f, 2.7f, false), col, 1f, DrawList.T_RIGHT);
    }

    /** Voile sombre derriere une annonce. */
    private void veil(Pose p, DrawList out, float a, float x0, float y0, float x1, float y1) {
        if (a <= 0.02f) return;
        p.push().translate((x0 + x1) / 2, (y0 + y1) / 2, SZ + 0.2f).scale(x1 - x0, y1 - y0, 1);
        out.translucent(p, veil, 0x000000, a);
        p.pop();
    }

    /** Le chien de garde des gros gains : il surgit du bas de l'ecran, aboie, et plaque ses pattes sur la vitre. */
    private void dog(Pose p, DrawList out, float t, float len) {
        BigWin.mascot(p, out, t, len, DOG_A, DOG_B, PAW, 8, 23.9f, 7.6f, WY0 + 0.1f, WY1 - 0.1f, SZ + 0.14f, FZ + 0.02f, 2.4f,
                new float[]{WX0 + 1.9f, WY0 + 3.0f, WX1 - 1.9f, WY0 + 3.3f});
    }

    /** Annonces : gains, declenchement (+2 fois la mise), dernier tour, deblocages, fin du bonus, gain maximum. */
    private void overlays(Scene s, NicheView v, Pose p, DrawList out, Niche.Spin spin, float lt) {
        float z = SZ + 0.3f;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        if (spin.kind == Niche.BASE) {
            float cx = 8, cy = (RY0 + RY1) / 2;
            int end = Niche.spinEnd(spin), show = Niche.winShow(spin);
            long lines = spin.gridMilli[0];
            float t = lt - end;
            if (lines > 0 && t >= 0 && t < show) {
                int tier = Rotten.bigTier(lines);
                if (tier > 0) {
                    dog(p, out, t, show);
                    BigWin.text(p, out, t, show, tier, (long) Math.floor(v.bet * lines / 1000.0), cx, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f,
                            tier >= 3 ? PURPLE : tier == 2 ? RED : AMBER, GOLD);
                } else {
                    String txt = "+" + amountText(v, lines);
                    out.text(p, txt, cx, cy, z, fit(txt, 1.8f * Ease.outBack(Math.min(1f, t / 5f), 2.2f), 10f, true), GOLD, 1f, flags, 0x2A1400);
                }
            }
            if ((spin.events & Niche.EV_TRIGGER) != 0) {
                float e = lt - trigStart(spin);
                if (e >= 0 && e < Niche.TRIGGER_T + 4) {
                    float a = Math.min(1f, e / 4f) * (1 - Ease.range(e, Niche.TRIGGER_T - 4, Niche.TRIGGER_T + 4));
                    veil(p, out, 0.62f * a, RX0 - 0.15f, RY0 - 0.1f, RX1 + 0.15f, RY1 + 0.1f);
                    String bonus = I18n.get("heralizcasino.niche.bonus");
                    out.text(p, bonus, cx, cy + 1.9f, z, fit(bonus, 2.2f, 11f, true) * Ease.outBack(Ease.clamp01(e / 6f), 2.2f), AMBER, a, flags, 0x2A1400);
                    String ft = I18n.get("heralizcasino.slot.n_tours_gratuits", String.valueOf(v.rules.freeSpins));
                    out.text(p, ft, cx, cy + 0.15f, z, fit(ft, 1.05f, 11f, true), GREEN, a, flags, 0x000000);
                    float pay = Ease.clamp01((e - 10) / 6f);
                    String pt = I18n.get("heralizcasino.niche.fois_mise", mult(v.rules.scatterPay), amountText(v, spin.bonusMilli));
                    out.text(p, pt, cx, cy - 1.45f, z, fit(pt, 0.85f, 11f, true) * Math.max(0.05f, Ease.outBack(pay, 2.4f)), GOLD, a * pay, flags, 0x2A1400);
                }
            }
        } else {
            freeOverlay(v, p, out, spin, lt, z, flags);
        }
        if ((spin.events & Niche.EV_MAXWIN) != 0 && lt >= end(spin)) {
            long total = (long) Math.floor(v.bet * v.seq.totalMilli / 1000.0);
            float mt = lt - end(spin), cy = (RY0 + RY1) / 2;
            if (spin.kind == Niche.BASE && mt < Rotten.BIG_WIN_TICKS) {
                dog(p, out, mt, Rotten.BIG_WIN_TICKS);
                BigWin.text(p, out, mt, Rotten.BIG_WIN_TICKS, 4, total, 8, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, GOLD, GOLD);
            } else {
                BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * Ease.clamp01((spin.kind == Niche.BASE ? mt - Rotten.BIG_WIN_TICKS : mt) / 5f));
                String maxTxt = I18n.get("heralizcasino.slot.gain_max");
                out.text(p, maxTxt, 8, cy + 1.2f, z, fit(maxTxt, 1.8f, 11f, true), GOLD, 1f, flags, 0x2A1400);
                out.text(p, fmt(total), 8, cy - 1.0f, z, fit(fmt(total), 2.2f, 11f, true), GOLD, 1f, flags, 0x2A1400);
            }
        }
    }

    private void freeOverlay(NicheView v, Pose p, DrawList out, Niche.Spin spin, float lt, float z, int flags) {
        float cx = 8, cy = (GY[1] + GY[0] + GH + HEAD) / 2;
        float gx0 = MX0 - 0.1f, gx1 = WX1 - 0.02f, gy0 = GY[1] - 0.1f, gy1 = GY[0] + GH + HEAD + 0.1f;
        // annonce du dernier tour
        if ((spin.events & Niche.EV_LAST) != 0 && lt < Niche.LAST_T + 2) {
            float a = Ease.clamp01(lt / 3f) * (1 - Ease.range(lt, Niche.LAST_T - 3, Niche.LAST_T + 2));
            veil(p, out, 0.5f * a, gx0, gy0, gx1, gy1);
            String last = I18n.get("heralizcasino.slot.dernier_tour_annonce");
            out.text(p, last, cx, cy, z, fit(last, 2f, 11f, true) * Ease.outBack(Ease.clamp01(lt / 5f), 2.2f), RED, a, flags, 0x000000);
        }
        // deblocages : "3 PATTES !" sur la grille source, "GRILLE n !" puis "+N TOURS" sur la nouvelle
        for (int u = 0; u < spin.unlocked; u++) {
            float ut = unlockT(spin, u, lt);
            int to = spin.startGrids + u;
            Place pt = slot(to, 1), pf = slot(to - 1, 1);
            if (ut >= 0 && ut < Niche.UNLOCK_BREAK) {
                float a = Ease.clamp01(ut / 3f) * (1 - Ease.range(ut, Niche.UNLOCK_BREAK - 4, Niche.UNLOCK_BREAK));
                String pattes = I18n.get("heralizcasino.niche.trois_pattes");
                out.text(p, pattes, pf.midX(), pf.midY(), z, fit(pattes, 1.15f, GW * 0.9f, true) * Ease.outBack(Ease.clamp01(ut / 5f), 2.4f), AMBER, a, flags, 0x000000);
            }
            if (ut >= Niche.UNLOCK_BREAK && ut < Niche.UNLOCK_GROW + 6) {
                float g = ut - Niche.UNLOCK_BREAK, a = Ease.clamp01(g / 3f) * (1 - Ease.range(ut, Niche.UNLOCK_GROW, Niche.UNLOCK_GROW + 6));
                String txt = I18n.get("heralizcasino.niche.grille_annonce", String.valueOf(to + 1));
                out.text(p, txt, pt.midX(), pt.midY(), z, fit(txt, 1.15f, GW * 0.9f, true) * Ease.outBack(Ease.clamp01(g / 5f), 2.4f), GOLD, a, flags, 0x2A1400);
            }
            float ad = ut - addStart(spin, u);
            if (ad >= 0 && ad < Niche.ADD_T + 4) {
                int n = spin.unlockAdd[u];
                String txt = I18n.get(n > 1 ? "heralizcasino.niche.plus_tours" : "heralizcasino.niche.plus_tour", String.valueOf(n));
                float a = Ease.clamp01(ad / 3f) * (1 - Ease.range(ad, Niche.ADD_T, Niche.ADD_T + 4));
                out.text(p, txt, pt.midX(), pt.midY(), z, fit(txt, 1.25f, GW * 0.92f, true) * Ease.outBack(Ease.clamp01(ad / 5f), 2.4f), GREEN, a, flags, 0x000000);
            }
        }
        // fin du bonus : total (declenchement compris), le chien pour les gros gains ; le total reste affiche jusqu'a
        // la partie suivante, sur un fond assombri (les boutons du joueur reviennent, voir summary)
        if ((spin.events & Niche.EV_END) != 0 && !((spin.events & Niche.EV_MAXWIN) != 0 && lt >= end(spin))) {
            float e = lt - Niche.endStart(spin);
            int show = Niche.endShow(spin);
            if (e >= 0) {
                int tier = endTier(spin);
                long amount = (long) Math.floor(v.bet * spin.bonusMilli / 1000.0);
                if (tier > 0 && e < show) {
                    dog(p, out, e, show);
                    BigWin.text(p, out, e, show, tier, amount, cx, 27.0f, 21.6f, z + 0.1f, 11.5f, tier >= 3 ? PURPLE : tier == 2 ? RED : AMBER, GOLD);
                } else {
                    float k = tier > 0 ? e - show : e, a = Ease.clamp01(k / 4f);
                    BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * a);
                    String fin = I18n.get("heralizcasino.niche.bonus_termine");
                    out.text(p, fin, cx, cy + 1.7f, z, fit(fin, 1.4f, 11f, true) * Ease.outBack(Ease.clamp01(k / 6f), 2f), AMBER, a, flags, 0x000000);
                    out.text(p, I18n.get("heralizcasino.slot.gain_total"), cx, cy + 0.35f, z, 0.75f, WHITE, a, flags, 0x000000);
                    String tt = fmt((long) Math.floor(amount * (tier > 0 ? 1 : Ease.outCubic(Ease.clamp01((k - 4) / 24f)))));
                    out.text(p, tt, cx, cy - 1.3f, z, fit(tt, 2.3f, 10f, true) * Ease.outBack(Ease.clamp01((k - 2) / 7f), 2f), GOLD, a, flags, 0x2A1400);
                }
            }
        }
    }

    // =================================================================== camera
    @Override
    public void camera(NicheView v, Scene s, int mode, Shot out) {
        float[] at = v.at(v.since);
        if (at != null && v.since < v.totalTicks() + 40 && mode != CAM_MENU) {
            int i = (int) at[0];
            Niche.Spin f = v.seq.spins.get(i);
            float lt = at[1];
            float[] tz = f.kind == Niche.BASE ? teasing(f, lt) : null;
            if (tz != null) {
                // suspense : on se rapproche du 5e rouleau
                float k = Ease.inOutSine(Ease.clamp01(tz[1] / 10f));
                float x = Ease.lerp(8, BASE.cx(Niche.REELS - 1) * 0.55f + 8 * 0.45f, k);
                out.set(x, 24.6f + 1.7f * k, 36.5f - 5.5f * k, x, 23.2f + 2.1f * k, SZ, 44 - 4 * k);
                return;
            }
            // quatre grilles : meme cadrage (toute la fenetre tient deja dans l'image, au-dessus de la ligne d'etat)
            out.set(8, 24.6f, 36.5f, 8, 23.2f, SZ, 44);
            return;
        }
        if (mode == CAM_MENU) out.set(8, 31.5f, 48, 8, 28.5f, 13, 54);
        else out.set(8, 24.6f, 36.5f, 8, 23.2f, SZ, 44);
    }

    @Override
    public boolean zoomable() {
        return true;
    }

    /** Gain total du bonus (apres l'annonce de gros gain) ou gain maximum a l'ecran : les boutons du joueur reviennent. */
    @Override
    public float summary(NicheView v, Scene s) {
        float[] at = v.at(v.since);
        if (at == null || v.since > v.totalTicks() + 200 || (int) at[0] != v.seq.spins.size() - 1) return 0;
        Niche.Spin spin = v.seq.spins.get((int) at[0]);
        float lt = at[1];
        if ((spin.events & Niche.EV_MAXWIN) != 0 && lt >= end(spin)) {
            return Ease.clamp01((spin.kind == Niche.BASE ? lt - end(spin) - Rotten.BIG_WIN_TICKS : lt - end(spin)) / 5f);
        }
        if ((spin.events & Niche.EV_END) == 0) return 0;
        float e = lt - Niche.endStart(spin);
        if (endTier(spin) > 0) e -= Niche.endShow(spin);
        return Ease.clamp01(e / 4f);
    }

    /** Image de jeu de l'ecran en entier (bandeaux du haut et du bas compris), cadree par le zoom du joueur et pendant l'intro. */
    @Override
    public float[] screenRect(NicheView v, Scene s) {
        return new float[]{WX0, WY0, WX1, WY1, SZ};
    }

    @Override
    public float screenFocus(NicheView v, Scene s) {
        return Intro.focus(v.intro, v.at(v.since) != null && v.since < v.totalTicks() + 40);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, NicheView v, FxSink fx, Random r) {
        if (r.nextInt(120) == 0) fx.particle(FxSink.FLY, 8 + rnd(r, 7), 22 + rnd(r, 10), 16 + rnd(r, 2), 0, 0, 0, 0x202020, 0.3f, 60);
        float[] at = v.at(v.since);
        // intro : le chien gemit, grogne, aboie deux fois, puis trottine sur l'ecran
        float it = Intro.time(v.intro, at != null && v.since < v.totalTicks() + 40, s.item);
        if (it >= 0) {
            float iy = (RY0 + RY1) / 2;
            Intro.sounds(fx, it, Niche.REELS, RX0, CW, iy, 15);
            if (at(it, 12)) fx.sound(Snd.WHINE, 8, iy, 15, 0.6f, 1.1f);
            if (at(it, 22)) fx.sound(Snd.GROWL, 8, iy, 15, 0.7f, 1f);
            if (at(it, 30)) fx.sound(Snd.BARK, 8, iy, 15, 1f, 1f);
            if (at(it, 34)) fx.sound(Snd.BARK, 8, iy, 15, 1f, 1.15f);
            for (int k = 0; k < PAWS; k++) {
                if (at(it, (int) (PAW_T + k * PAW_DT))) fx.sound(Snd.CHIP, pawX(k), pawY(k), 15, 0.25f, 1.6f + 0.1f * (k % 2));
            }
        }
        if (at == null || v.since > v.totalTicks() + 10) {
            // le chien s'impatiente dans sa niche
            if (r.nextInt(900) == 0) fx.sound(r.nextBoolean() ? Snd.WHINE : Snd.BARK, 8, 8, 14, 0.3f, 0.9f + r.nextFloat() * 0.3f);
            return;
        }
        int i = (int) at[0];
        float lt = at[1];
        Niche.Spin spin = v.seq.spins.get(i);
        if (spin.kind == Niche.BASE) baseFx(v, spin, lt, fx, r);
        else freeFx(spin, lt, fx, r);
        if ((spin.events & Niche.EV_MAXWIN) != 0 && at(lt, end(spin))) {
            fx.sound(Snd.WIN_JACKPOT, 8, 25, 15, 1f, 1f);
            fx.sound(Snd.BARK, 8, 25, 15, 1f, 0.8f);
            fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, BLUE, RED}, 50, 0.4f);
        }
    }

    private void baseFx(NicheView v, Niche.Spin spin, float lt, FxSink fx, Random r) {
        float sy = (RY0 + RY1) / 2;
        if (at(lt, 0)) fx.sound(Snd.REEL_START, 8, sy, 15, 0.7f, 1.2f);
        int last = Niche.stopTick(spin, Niche.REELS - 1);
        if (lt > 1 && lt < last && ((int) lt) % 2 == 0) fx.sound(Snd.REEL_TICK, 8, sy, 15, 0.16f, 1.8f + r.nextFloat() * 0.2f);
        for (int reel = 0; reel < Niche.REELS; reel++) {
            int stop = Niche.stopTick(spin, reel);
            boolean teased = Niche.tease(spin, 0, reel);
            float x = BASE.cx(reel);
            if (teased) Tease.sounds(fx, lt - (stop - SLOW), SLOW, x, sy, 15);
            if (!at(lt, stop)) continue;
            fx.sound(Snd.REEL_STOP, x, sy, 15, 0.45f, 1.2f + reel * 0.05f);
            boolean hit = false;
            for (int row = 0; row < Niche.ROWS; row++) {
                int c = Niche.cell(reel, row), sym = spin.sym[0][c];
                float y = BASE.cy(row);
                if (sym == Niche.BONUS) {
                    hit = true;
                    fx.sound(Snd.SCATTER, x, y, 15, 1f, 0.8f + reel * 0.1f);
                    burst(fx, r, x, y, SZ + 0.6f, AMBER, 0xFFFFFF, 10, 0.25f, 0.35f);
                }
                if (sym == Niche.WILD) fx.sound(Snd.BARK, x, y, 15, 0.35f, 1.1f + 0.1f * spin.mult[0][c]);
            }
            if (teased) fx.sound(hit ? Snd.BELL : Snd.MISS, x, sy, 15, hit ? 0.9f : 0.7f, hit ? 1.4f : 1f);
        }
        int end = Niche.spinEnd(spin);
        long lines = spin.gridMilli[0];
        if (lines > 0 && at(lt, end)) {
            boolean big = Rotten.bigTier(lines) > 0;
            fx.sound(big ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
            if (big) {
                fx.sound(Snd.BARK, 8, sy, 15, 1f, 0.8f);
                fx.sound(Snd.COINS, 8, 16, 16, 0.9f, 1f);
                fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, BLUE}, 24, 0.3f);
            }
        }
        if (Rotten.bigTier(lines) > 0) dogFx(fx, r, lt - end, Rotten.BIG_WIN_TICKS);
        if ((spin.events & Niche.EV_TRIGGER) != 0) {
            int ts = trigStart(spin), tr = transStart(spin);
            if (at(lt, ts)) {
                fx.sound(Snd.BONUS, 8, sy, 15, 1f, 1.1f);
                fx.sound(Snd.BARK, 8, sy, 15, 1f, 1f);
                burst(fx, r, 8, sy, 14.5f, AMBER, GOLD, 30, 0.5f, 0.5f);
            }
            if (at(lt, ts + 12)) fx.sound(Snd.COINS, 8, 16, 16, 0.9f, 1.1f);
            if (at(lt, tr + 6)) fx.sound(Snd.WHOOSH, 8, sy, 15, 0.8f, 0.9f);
            for (int k = 1; k < Niche.GRIDS; k++) {
                if (at(lt, tr + 10 + 5 * k)) fx.sound(Snd.CHIP, slot(k, 1).midX(), slot(k, 1).midY(), 15, 0.7f, 0.7f + 0.1f * k);
            }
        }
    }

    private void freeFx(Niche.Spin spin, float lt, FxSink fx, Random r) {
        float t = lt - Niche.spinStart(spin), sy = 24.5f;
        if ((spin.events & Niche.EV_LAST) != 0 && at(lt, 0)) {
            fx.sound(Snd.BELL, 8, sy, 15, 1f, 0.7f);
            fx.sound(Snd.WHINE, 8, sy, 15, 0.6f, 1f);
        }
        if (at(t, 0)) fx.sound(Snd.REEL_START, 8, sy, 15, 0.6f, 1.4f);
        int last = Niche.stopTick(spin, Niche.REELS - 1);
        if (t > 1 && t < last && ((int) t) % 2 == 0) fx.sound(Snd.REEL_TICK, 8, sy, 15, 0.14f, 1.9f + r.nextFloat() * 0.2f);
        int lg = spin.startGrids - 1;
        for (int reel = 0; reel < Niche.REELS; reel++) {
            int stop = Niche.stopTick(spin, reel);
            if (spin.startGrids < Niche.GRIDS && Niche.tease(spin, lg, reel)) Tease.sounds(fx, t - (stop - SLOW), SLOW, slot(lg, 1).cx(reel), slot(lg, 1).midY(), 15);
            if (!at(t, stop)) continue;
            fx.sound(Snd.REEL_STOP, 8, sy, 15, 0.4f, 1.3f + reel * 0.05f);
            for (int k = 0; k < spin.startGrids; k++) landFx(spin, k, reel, slot(k, 1), k == lg, fx, r);
        }
        for (int u = 0; u < spin.unlocked; u++) {
            float ut = unlockT(spin, u, lt);
            int to = spin.startGrids + u;
            Place pt = slot(to, 1), pf = slot(to - 1, 1);
            float cx = pt.midX(), cy = pt.midY();
            if (at(ut, 0)) {
                fx.sound(Snd.BONUS, pf.midX(), pf.midY(), 15, 1f, 1.2f);
                fx.sound(Snd.BARK, pf.midX(), pf.midY(), 15, 0.9f, 1.1f);
            }
            if (at(ut, Niche.UNLOCK_LOCK)) fx.sound(Snd.TENSION, cx, cy, 15, 0.7f, 1.2f);
            if (ut >= Niche.UNLOCK_LOCK && ut < Niche.UNLOCK_BREAK - 4 && ((int) ut) % 3 == 0 && at(ut, (int) ut)) fx.sound(Snd.CHIP, cx, cy, 15, 0.45f, 0.6f);
            if (at(ut, Niche.UNLOCK_BREAK - 4)) {
                fx.sound(Snd.LOCK_BREAK, cx, cy, 15, 1f, 0.9f);
                for (int q = 0; q < 8; q++) fx.particle(FxSink.SHARD, cx, cy, SZ + 0.5f, rnd(r, 0.12f), 0.04f + r.nextFloat() * 0.1f, 0.05f, 0xB8B0A0, 0.22f, 16);
            }
            if (at(ut, Niche.UNLOCK_BREAK)) fx.sound(Snd.UNLOCK, cx, cy, 15, 0.8f, 1.1f);
            int copied = Niche.copiedMask(spin, u), j = 0;
            float step = copyStep(spin, u);
            for (int c = 0; c < Niche.CELLS; c++) {
                if ((copied & (1 << c)) == 0) continue;
                float a0 = Niche.UNLOCK_GROW + j * step;
                if (at(ut, (int) Math.floor(a0))) fx.sound(Snd.WHOOSH, pf.cx(c / Niche.ROWS), pf.cy(c % Niche.ROWS), 15, 0.35f, 1.6f + 0.05f * j);
                if (at(ut, (int) Math.floor(a0 + COPY_FLY))) {
                    float x = pt.cx(c / Niche.ROWS), y = pt.cy(c % Niche.ROWS);
                    fx.sound(Snd.STICK, x, y, 15, 0.7f, 1.2f + 0.05f * j);
                    burst(fx, r, x, y, SZ + 0.5f, GOLD, 0xFFFFFF, 8, 0.2f, 0.3f);
                }
                j++;
            }
            int add = addStart(spin, u), ns = newSpinStart(spin, u);
            if (at(ut, add)) fx.sound(Snd.BELL, cx, cy, 15, 1f, 1.2f);
            if (at(ut, ns)) fx.sound(Snd.REEL_START, cx, cy, 15, 0.6f, 1.5f);
            for (int reel = 0; reel < Niche.REELS; reel++) {
                if (!at(ut, ns + newStop(reel))) continue;
                fx.sound(Snd.REEL_STOP, pt.cx(reel), cy, 15, 0.4f, 1.3f + reel * 0.05f);
                landFx(spin, to, reel, pt, to < Niche.GRIDS - 1, fx, r);
            }
        }
        int ws = Niche.winStart(spin);
        if (spin.winMilli > 0 && at(lt, ws)) fx.sound(Rotten.bigTier(spin.winMilli) > 0 ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
        if ((spin.events & Niche.EV_END) != 0) {
            int es = Niche.endStart(spin), show = Niche.endShow(spin);
            boolean big = endTier(spin) > 0;
            if (at(lt, es)) {
                fx.sound(big ? Snd.WIN_JACKPOT : Snd.WIN_BIG, 8, sy, 15, 1f, 1f);
                fx.sound(Snd.BARK, 8, sy, 15, 1f, 0.9f);
                fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, AMBER, BLUE, RED}, big ? 44 : 26, 0.35f);
            }
            if (big) dogFx(fx, r, lt - es, show);
            if (at(lt, Niche.fadeStart(spin))) fx.sound(Snd.WHOOSH, 8, sy, 15, 0.8f, 0.8f);
        }
    }

    /** Arret d'un rouleau d'une grille : wild qui se fige (clac metallique), patte qui tombe sur la grille allumee. */
    private void landFx(Niche.Spin spin, int k, int reel, Place pl, boolean paws, FxSink fx, Random r) {
        for (int row = 0; row < Niche.ROWS; row++) {
            int c = Niche.cell(reel, row);
            float x = pl.cx(reel), y = pl.cy(row);
            if ((spin.fresh[k] & (1 << c)) != 0) {
                fx.sound(Snd.STICK, x, y, 15, 0.8f, 1.1f);
                burst(fx, r, x, y, SZ + 0.5f, GOLD, 0xFFFFFF, 10, 0.25f, 0.3f);
            }
            if (paws && spin.sym[k][c] == Niche.BONUS) fx.sound(Snd.SCATTER, x, y, 15, 1f, 0.8f + reel * 0.1f);
        }
    }

    /** Le chien : pattes qui frappent la vitre, aboiements, jetons qui pleuvent. */
    private void dogFx(FxSink fx, Random r, float t, float len) {
        if (t < 0 || t > len) return;
        for (int k = 0; k < 2; k++) {
            if (!at(t, 8 + k * 5)) continue;
            float x = k == 0 ? WX0 + 1.9f : WX1 - 1.9f;
            fx.sound(Snd.SMASH, x, WY0 + 3.2f, FZ, 0.6f, 0.9f + k * 0.1f);
        }
        if (at(t, 20) || at(t, 40)) fx.sound(Snd.BARK, 8, 24, 15, 0.9f, 0.85f + r.nextFloat() * 0.2f);
        if (at(t, 60)) fx.sound(Snd.GROWL, 8, 24, 15, 0.7f, 1f);
        if (t > 10 && t < len - 20 && ((int) t) % 6 == 0) fx.sound(Snd.COINS, 8, 16, 16, 0.4f, 1.2f);
    }

    // =================================================================== vues
    @Override
    public NicheView newView() {
        return new NicheView();
    }

    @Override
    public NicheView demoView(float t) {
        NicheView v = new NicheView();
        // apercu hors-jeu : graine (-Dnichedemo), achat (-Dnichebuy=1) ; -Dnichegrids=N : premiere partie qui ouvre N
        // grilles ; -Dnichebig=true : gros gain en jeu normal ; -Dnichetease=true : suspense ;
        // -Dnicheat=trigger|unlock|end : se place au debut de ce moment ; -Dnicheskip=ticks : avance encore
        long seed = Long.getLong("nichedemo", 3L);
        int buy = (int) (long) Long.getLong("nichebuy", 0L), wantGrids = Integer.getInteger("nichegrids", 0);
        boolean wantBig = Boolean.getBoolean("nichebig"), wantTease = Boolean.getBoolean("nichetease");
        v.seq = Niche.play(v.rules, new java.util.SplittableRandom(seed), buy);
        if (wantGrids > 0 || wantBig || wantTease) {
            for (long k = seed; k < seed + 3_000_000; k++) {
                Niche.Sequence q = Niche.play(v.rules, new java.util.SplittableRandom(k), wantGrids > 0 ? Niche.BUY_FREE : 0);
                Niche.Spin f = q.spins.get(0), l = q.spins.get(q.spins.size() - 1);
                if (wantTease ? Niche.tease(f, 0, Niche.REELS - 1)
                        : wantBig ? q.spins.size() == 1 && Rotten.bigTier(f.gridMilli[0]) > 0 : l.grids >= wantGrids) {
                    v.seq = q;
                    break;
                }
            }
        }
        float anchor = 0;
        String where = System.getProperty("nicheat", "");
        int acc = 0;
        for (Niche.Spin sp : v.seq.spins) {
            if (where.equals("trigger") && sp.kind == Niche.BASE && (sp.events & Niche.EV_TRIGGER) != 0) {
                anchor = acc + trigStart(sp);
                break;
            }
            if (where.equals("unlock") && (sp.events & Niche.EV_UNLOCK) != 0) {
                anchor = acc + Niche.unlockStart(sp, 0);
                break;
            }
            if (where.equals("end") && (sp.events & Niche.EV_END) != 0) {
                anchor = acc + Niche.endStart(sp);
                break;
            }
            acc += Niche.duration(sp);
        }
        v.bet = 100;
        v.since = anchor + t + Float.parseFloat(System.getProperty("nicheskip", "0"));
        return v;
    }

    @Override
    public int demoLength() {
        return 200;
    }
}
