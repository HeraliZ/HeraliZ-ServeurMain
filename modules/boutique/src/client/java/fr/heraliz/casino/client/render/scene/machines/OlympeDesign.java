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
import fr.heraliz.casino.game.slot.Olympe;
import fr.heraliz.casino.game.slot.OlympeRules;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.Suspense;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * "Les Portes de l'Olympe", ruines recuperees : un vieux televiseur peint en ivoire, filete d'or, pose sur un chapiteau,
 * un linteau et deux piles de tambours de colonnes (un tambour casse et un eclat de corniche par-dessus), pupitre peint
 * en or a la bombe, amphore, batterie ; le titre est affiche en haut de l'ecran, au-dessus de la grille 6 x 5 qui paie
 * partout. Les symboles tombent du ciel, les combinaisons gagnantes explosent et la grille
 * s'effondre en cascade ; Zeus, a droite de la grille, foudroie les orbes multiplicateurs.
 */
public final class OlympeDesign extends MachineDesign<OlympeView> {
    // ---- atlas : partage avec tools/textures (PaintOlympe) ; symboles en cases de 128
    public static final int[] R_ORB = {256, 128, 128, 128};
    public static final int[] R_BOLT = {768, 128, 128, 128};
    public static final int[] R_FLASH = {896, 128, 128, 128};
    public static final int[] R_ZEUS_A = {0, 256, 128, 256};
    public static final int[] R_ZEUS_B = {128, 256, 128, 256};
    public static final int[] R_SCREEN = {256, 256, 256, 256};
    public static final int[] R_MARBLE = {512, 256, 256, 256};
    public static final int[] R_FLUTED = {768, 256, 256, 256};
    public static final int[] R_TOPPER = {0, 512, 512, 256};
    public static final int[] R_PEDIMENT = {512, 512, 256, 256};
    public static final int[] R_PANEL = {768, 512, 256, 256};
    public static final int[] R_GOLD = {0, 768, 256, 128};
    public static final int[] R_GRID = {256, 768, 256, 128};
    public static final int[] R_BAR = {512, 768, 256, 128};
    public static final int[] R_BTN_RED = {768, 768, 128, 128};
    public static final int[] R_BTN_YEL = {896, 768, 128, 128};
    public static final int[] R_SEAT = {0, 896, 128, 128};
    public static final int[] R_STEEL = {128, 896, 128, 128};
    public static final int[] R_DARK = {256, 896, 128, 64};
    public static final int[] R_LED = {384, 896, 64, 64};
    public static final int[] R_BEZEL = {448, 896, 64, 64};
    public static final int[] R_GLASS = {256, 960, 256, 64};
    public static final int[] R_CLOUD = {512, 896, 256, 128};

    static final Mat MARBLE = FruitsDesign.tri(R_MARBLE, 14), FLUTED = FruitsDesign.tri(R_FLUTED, 4.3f), GOLD_M = FruitsDesign.tri(R_GOLD, 8);
    static final Mat TOPPER = FruitsDesign.fit(R_TOPPER), PEDIMENT = FruitsDesign.fit(R_PEDIMENT), PANEL = FruitsDesign.fit(R_PANEL);
    static final Mat SCREEN = FruitsDesign.fit(R_SCREEN), GRID = FruitsDesign.fit(R_GRID), BAR = FruitsDesign.fit(R_BAR);
    static final Mat BTN_RED = FruitsDesign.fit(R_BTN_RED), BTN_YEL = FruitsDesign.fit(R_BTN_YEL), SEAT = FruitsDesign.tri(R_SEAT, 6);
    static final Mat STEEL = FruitsDesign.tri(R_STEEL, 6), DARK = FruitsDesign.tri(R_DARK, 6), LED = FruitsDesign.fit(R_LED);
    static final Mat BEZEL = FruitsDesign.tri(R_BEZEL, 4), GLASS = FruitsDesign.fit(R_GLASS), CLOUD = FruitsDesign.fit(R_CLOUD);
    static final Mat BOLT = FruitsDesign.fit(R_BOLT), FLASH = FruitsDesign.fit(R_FLASH);
    static final Mat ZEUS_A = FruitsDesign.fit(R_ZEUS_A), ZEUS_B = FruitsDesign.fit(R_ZEUS_B);

    /** Symbole dans l'atlas (les orbes selon leur valeur). */
    static Mat symbol(int s) {
        if (s < 8) return Mat.fit(s * 128, 0, 128, 128);
        if (s == 8) return Mat.fit(0, 128, 128, 128);
        if (s == Olympe.ZEUS) return Mat.fit(128, 128, 128, 128);
        return orb(0);
    }

    static Mat orb(int tier) {
        return Mat.fit(R_ORB[0] + tier * 128, R_ORB[1], 128, 128);
    }

    /** Couleur d'un orbe : vert (x2-x5), bleu (x6-x10), violet (x12-x25), rouge (x50 et plus). */
    static int orbTier(int value) {
        return value <= 5 ? 0 : value <= 10 ? 1 : value <= 25 ? 2 : 3;
    }

    // ---- cotes (px)
    static final float FZ = 14.2f, SZ = 13.65f;
    static final float WX0 = 1.9f, WX1 = 14.1f, WY0 = 18.6f, WY1 = 31.4f;
    static final float RX0 = 2.2f, RX1 = 11.2f, RY0 = 20.3f, RY1 = 28.1f;
    static final float CW = (RX1 - RX0) / Olympe.REELS, CH = (RY1 - RY0) / Olympe.ROWS;
    /** Zeus a droite de la grille, et le compteur du multiplicateur sous ses pieds. */
    static final float ZX = 12.65f, ZY = 24.2f, ZH = 6.2f, MULT_Y = 20.75f;
    static final float[] HAND = {13.3f, 26.6f};
    static final float[] SPIN_BTN = {12.1f, 16.7f, 15.3f}, BONUS_BTN = {3.8f, 16.7f, 15.3f};
    static final int GOLD = 0xFFD36A, SKY = 0x6AB8FF, PURPLE = 0xC08AFF, WHITE = 0xF6F2E8, RED = 0xFF5A4A, GREEN = 0x7AFF8A;
    static final int[] ORB_COLORS = {0x6AFF7A, 0x6AB8FF, 0xC06AFF, 0xFF4A3A};
    static final int[] IDLE = {
            8, 2, 5, 0, 7, 3, 1, 4, 6, 2, 0, 9, 3, 5, 1, 7, 2, 4, 0, 8, 6, 1, 3, 2, 5, 4, 0, 7, 1, 3};

    private Mesh body = Mesh.EMPTY, kit = Mesh.EMPTY, topperGlow = Mesh.EMPTY, screenBg = Mesh.EMPTY, gridBg = Mesh.EMPTY, glass = Mesh.EMPTY;
    private Mesh bars = Mesh.EMPTY, spinBtn = Mesh.EMPTY, bonusBtn = Mesh.EMPTY, garland = Mesh.EMPTY, cloud = Mesh.EMPTY, bodyLights = Mesh.EMPTY;

    public OlympeDesign() {
        super("portes_olympe", GOLD, SKY);
        labelY = 44;
        itemScale = 0.36f;
    }

    // =================================================================== construction

    @Override
    protected void build() {
        float[] nz = v(0, 0, 1);
        MeshBuilder b = new MeshBuilder();
        MeshBuilder k = new MeshBuilder();
        // ---- ruines recuperees : dalles de marbre fendues, deux piles de tambours de colonnes, linteau, chapiteau
        b.bevelBox(-0.3f, 0, 0.8f, 8.1f, 0.9f, 16.0f, 0.12f, MARBLE);
        b.bevelBox(8.15f, 0, 0.8f, 16.3f, 0.8f, 16.0f, 0.12f, MARBLE);
        for (float[] c : new float[][]{{3.3f, 0, 4}, {12.7f, 1, -6}}) {
            float y = 0.9f;
            for (int d = 0; d < 2; d++) {
                float h = d == 0 ? 4.6f : 4.9f;
                b.push().translate(c[0] + (d == 1 ? c[2] * 0.03f : 0), y, 7.6f).rotateY(c[2] * (d + 1) * 7);
                b.cylinder(2.6f - d * 0.12f, 0, h, seg(14), FLUTED, MARBLE, MARBLE);
                b.pop();
                y += h;
            }
        }
        b.push().translate(14.6f, 0.8f, 13.6f).rotateY(30);
        b.rock(1.0f, 0.6f, 0.8f, 0.35f, 2, 5, 6, MARBLE);
        b.pop();
        b.bevelBox(-0.2f, 10.4f, 2.6f, 16.2f, 11.2f, 14.6f, 0.15f, MARBLE);
        b.bevelBox(-0.25f, 10.55f, 14.55f, 16.25f, 10.95f, 14.75f, 0.05f, GOLD_M);
        b.bevelBox(2.4f, 11.2f, 4.2f, 13.6f, SlotRig.TV_Y0, 13.8f, 0.2f, MARBLE);
        for (float x : new float[]{2.5f, 13.5f}) {
            b.push().translate(x, 15.6f, 4.4f).rotateX(90);
            b.cylinder(0.85f, 0, 9.5f, seg(10), GOLD_M, GOLD_M, GOLD_M);
            b.pop();
        }
        b.face(PANEL.sub(0.1f, 0.06f, 0.9f, 0.78f), nz, p(6.6f, 11.4f, 13.87f), p(9.4f, 11.4f, 13.87f), p(9.4f, 13.6f, 13.87f), p(6.6f, 13.6f, 13.87f));
        // ---- vieux televiseur peint en ivoire, filet d'or autour de la facade
        k.color(1f, 0.96f, 0.88f);
        SlotRig.tv(k, Recup.PLASTIC, k, false);
        k.color(1);
        SlotRig.embrasure(k, Recup.DARK, WY0, SlotRig.PY1);
        float fz = SlotRig.TV_FRONT;
        b.bevelBox(SlotRig.TV_X0 - 0.1f, SlotRig.TV_Y1 - 0.25f, fz - 0.1f, SlotRig.TV_X1 + 0.1f, SlotRig.TV_Y1 + 0.05f, fz + 0.12f, 0.04f, GOLD_M);
        b.bevelBox(SlotRig.TV_X0 - 0.1f, SlotRig.TV_Y0 - 0.05f, fz - 0.1f, SlotRig.TV_X1 + 0.1f, SlotRig.TV_Y0 + 0.25f, fz + 0.12f, 0.04f, GOLD_M);
        b.bevelBox(SlotRig.TV_X0 - 0.1f, SlotRig.TV_Y0, fz - 0.1f, SlotRig.TV_X0 + 0.2f, SlotRig.TV_Y1, fz + 0.12f, 0.04f, GOLD_M);
        b.bevelBox(SlotRig.TV_X1 - 0.2f, SlotRig.TV_Y0, fz - 0.1f, SlotRig.TV_X1 + 0.1f, SlotRig.TV_Y1, fz + 0.12f, 0.04f, GOLD_M);
        // ---- pupitre : caisse a munitions peinte en or a la bombe
        SlotRig.deck(k, Recup.GOLD, Recup.DARK);
        // ---- batterie posee sur la dalle, cables qui montent au pupitre
        SlotRig.battery(k, 3.4f, 0.9f, 12.7f, -6);
        SlotRig.cable(k, new float[][]{p(2.2f, 4.0f, 12.3f), p(1.5f, 7.5f, 15.0f), p(1.9f, 11.4f, 15.1f), p(2.8f, 13.7f, 15.3f)}, true);
        SlotRig.cable(k, new float[][]{p(4.6f, 4.0f, 12.5f), p(5.2f, 8.0f, 15.2f), p(4.6f, 11.6f, 15.0f), p(4.4f, 13.7f, 15.4f)}, false);
        // ---- sur le televiseur (le titre est affiche par l'ecran) : un tambour de colonne casse couche en travers, un
        // eclat de corniche, l'antenne
        float ty = SlotRig.TV_Y1;
        b.push().translate(5.2f, ty + 1.75f, 10.6f).rotateZ(90).rotateX(8);
        b.cylinder(1.75f, -3.2f, 2.9f, seg(12), FLUTED, MARBLE, MARBLE);
        b.pop();
        b.push().translate(12.7f, ty, 11.9f).rotateY(-14).rotateZ(4);
        b.bevelBox(-2.0f, 0, -1.2f, 2.0f, 1.1f, 1.3f, 0.12f, MARBLE);
        b.bevelBox(-2.1f, 1.1f, -1.3f, 2.1f, 1.45f, 1.45f, 0.06f, GOLD_M);
        b.pop();
        SlotRig.antenna(k, 9.6f, 10.4f);
        // ---- guirlande d'ampoules dorees ; amphore doree au pied ; tabouret : tambour de colonne, coussin de velours
        MeshBuilder bulbs = new MeshBuilder();
        b.color(0.6f, 0.5f, 0.2f);
        SlotRig.garland(k, b, bulbs, LED, p(0.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), p(15.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), 0.9f, 8);
        b.color(1);
        garland = bulbs.build();
        b.push().translate(14.4f, 0.8f, 3.4f);
        b.lathe(new float[][]{{0.05f, 0}, {0.9f, 0.2f}, {1.5f, 1.6f}, {1.3f, 3.0f}, {0.6f, 3.6f}, {0.75f, 4.3f}}, seg(10), 0, 360, GOLD_M, 1, 1, 0, 0, 0, false);
        b.pop();
        b.push().translate(8, 0, 24);
        b.cylinder(2.7f, 0, 7.4f, seg(14), FLUTED, MARBLE, null);
        b.cylinder(2.8f, 7.4f, 8.6f, seg(14), SEAT, null, SEAT);
        b.pop();
        Occlusion.bake(b, 0.35f, 3f, 0.45f, 0f);
        Occlusion.bake(k, 0.35f, 3f, 0.45f, 0f);
        body = b.build();
        kit = k.build();

        // titre "LES PORTES DE L'OLYMPE" affiche en haut de l'ecran (calque lumineux)
        MeshBuilder tg = new MeshBuilder();
        SlotRig.title(tg, TOPPER);
        topperGlow = tg.build();
        MeshBuilder lb = new MeshBuilder();
        SlotRig.led(lb, LED);
        bodyLights = lb.build();
        MeshBuilder sb = new MeshBuilder();
        sb.face(SCREEN, nz, p(WX0, WY0, SZ), p(WX1, WY0, SZ), p(WX1, SlotRig.PY1, SZ), p(WX0, SlotRig.PY1, SZ));
        screenBg = sb.build();
        MeshBuilder gb = new MeshBuilder();
        gb.face(GRID, nz, p(RX0 - 0.15f, RY0 - 0.15f, SZ + 0.01f), p(RX1 + 0.15f, RY0 - 0.15f, SZ + 0.01f), p(RX1 + 0.15f, RY1 + 0.15f, SZ + 0.01f),
                p(RX0 - 0.15f, RY1 + 0.15f, SZ + 0.01f));
        gridBg = gb.build();
        MeshBuilder br = new MeshBuilder();
        br.face(BAR, nz, p(WX0 + 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, RY0 - 0.25f, SZ + 0.02f),
                p(WX0 + 0.1f, RY0 - 0.25f, SZ + 0.02f));
        br.face(BAR, nz, p(WX0 + 0.1f, RY1 + 0.25f, SZ + 0.02f), p(WX1 - 0.1f, RY1 + 0.25f, SZ + 0.02f), p(WX1 - 0.1f, WY1 - 0.1f, SZ + 0.02f),
                p(WX0 + 0.1f, WY1 - 0.1f, SZ + 0.02f));
        bars = br.build();
        MeshBuilder cl = new MeshBuilder();
        cl.face(CLOUD, nz, p(RX1 + 0.1f, RY0 - 0.2f, SZ + 0.02f), p(WX1 - 0.05f, RY0 - 0.2f, SZ + 0.02f), p(WX1 - 0.05f, RY0 + 1.6f, SZ + 0.02f),
                p(RX1 + 0.1f, RY0 + 1.6f, SZ + 0.02f));
        cloud = cl.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(WX0, WY0, FZ - 0.05f), p(WX1, WY0, FZ - 0.05f), p(WX1, SlotRig.PY1, FZ - 0.05f), p(WX0, SlotRig.PY1, FZ - 0.05f));
        glass = gl.build();
        spinBtn = button(SPIN_BTN, 1.2f, BTN_RED);
        bonusBtn = button(BONUS_BTN, 1.0f, BTN_YEL);
    }

    private Mesh button(float[] at, float r, Mat face) {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, 15.5f, 14.8f).rotateX(10).translate(at[0] - 8, 1.4f, at[2] - 14.8f);
        b.cylinder(r + 0.25f, 0, 0.2f, seg(16), GOLD_M, null, GOLD_M);
        b.cylinder(r, 0.2f, 0.62f, seg(16), face, null, face);
        b.pop();
        return b.build();
    }

    // =================================================================== chronologie d'un tour
    static boolean orbsApply(Olympe.Spin s) {
        return s.orbSum > 0 && s.tumbleMilli() > 0;
    }

    static int orbStart(Olympe.Spin s) {
        return Olympe.settled(s) + 4;
    }

    static int winStart(Olympe.Spin s) {
        return orbStart(s) + (orbsApply(s) ? Olympe.ORB_TICKS : 0);
    }

    static int eventsStart(Olympe.Spin s) {
        return winStart(s) + Olympe.winShow(s);
    }

    /** Fin du tour hors annonce du gain maximum. */
    static int end(Olympe.Spin s) {
        return Olympe.duration(s) - ((s.events & Olympe.EV_MAXWIN) != 0 ? Rotten.BIG_WIN_TICKS : 0);
    }

    /** Ticks depuis le debut de l'annonce de Zeus (gros gain ou gain maximum), -1 sinon. */
    static float bigAt(Olympe.Spin s, float lt) {
        if (s == null) return -1;
        float t = lt - winStart(s);
        if (Rotten.bigTier(s.winMilli) > 0 && t >= 0 && t < Rotten.BIG_WIN_TICKS) return t;
        if ((s.events & Olympe.EV_MAXWIN) != 0) {
            float m = lt - end(s);
            if (m >= 0 && m < Rotten.BIG_WIN_TICKS) return m;
        }
        return -1;
    }

    // =================================================================== suspense
    /** Duree de la chute au ralenti d'une colonne en suspens. */
    static final int SLOW = Suspense.HOLD_DROP + 9;

    /** Colonne en train de tomber au ralenti et ticks ecoules depuis le debut de sa chute ; null si aucune. */
    static float[] teasing(Olympe.Spin spin, float lt) {
        if (spin == null) return null;
        for (int reel = 1; reel < Olympe.REELS; reel++) {
            if (!Olympe.tease(spin, reel)) continue;
            float land = Olympe.landTick(spin, reel), t = lt - (land - SLOW);
            if (t >= 0 && lt < land) return new float[]{reel, t};
        }
        return null;
    }

    /** Multiplicateur total affiche (tours gratuits) avant ce tour. */
    static int multBefore(OlympeView v, int i) {
        for (int k = i - 1; k >= 0; k--) {
            Olympe.Spin s = v.seq.spins.get(k);
            if (s.kind == Olympe.FREE) return s.totalMult;
        }
        return 0;
    }

    /** Orbes qui arrivent a l'etape k (grille initiale, ou cases tombees du haut apres une cascade) : masque des cases. */
    static int newOrbs(Olympe.Spin s, int k) {
        Olympe.Step st = s.steps.get(k);
        int mask = 0;
        if (k == 0) {
            for (int c = 0; c < Olympe.CELLS; c++) if (st.grid[c] == Olympe.ORB) mask |= 1 << c;
            return mask;
        }
        int prevMask = s.steps.get(k - 1).winMask;
        for (int reel = 0; reel < Olympe.REELS; reel++) {
            int fresh = 0;
            for (int row = 0; row < Olympe.ROWS; row++) if ((prevMask & (1 << Olympe.cell(reel, row))) != 0) fresh++;
            for (int row = 0; row < fresh; row++) {
                int c = Olympe.cell(reel, row);
                if (st.grid[c] == Olympe.ORB) mask |= 1 << c;
            }
        }
        return mask;
    }

    /** Instant ou Zeus foudroie l'orbe arrive a l'etape k en case c (sa valeur apparait alors). */
    static int strikeAt(Olympe.Spin s, int k, int c) {
        int idx = Integer.bitCount(newOrbs(s, k) & ((1 << c) - 1));
        return Olympe.stepStart(s, k) + 1 + idx * 3;
    }

    /** Zeus leve le bras : un orbe est en train d'etre foudroye. */
    static boolean striking(Olympe.Spin s, float lt) {
        for (int k = 0; k < s.steps.size(); k++) {
            int start = Olympe.stepStart(s, k);
            if (lt < start - 2 || lt > start + 40) continue;
            int mask = newOrbs(s, k);
            for (int c = 0; c < Olympe.CELLS; c++) {
                if ((mask & (1 << c)) == 0) continue;
                int at = strikeAt(s, k, c);
                if (lt >= at - 2 && lt < at + 8) return true;
            }
        }
        return false;
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
    protected void animate(Scene s, OlympeView v, Pose p, DrawList out) {
        float[] at = s.item ? null : v.at(v.since);
        boolean playing = at != null && v.since < v.totalTicks() + 40;
        Olympe.Spin spin = at == null ? null : v.seq.spins.get((int) at[0]);
        boolean free = spin != null && playing && spin.kind == Olympe.FREE;
        // titre a l'ecran : lueur doree (le tube gresille de temps en temps), eclairs pendant les tours gratuits
        float flick = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 991)) > 0.975f ? 0.4f : 1f;
        out.solid(p, topperGlow, (free ? 0.8f + 0.15f * s.pulse(0.2f) : 0.75f) * flick);
        out.additive(p, garland, free ? SKY : GOLD, (0.55f + 0.35f * s.pulse(0.06f)) * flick);
        if (!s.item && free && Ease.hash((int) (s.time / 4) + 17) > 0.8f) {
            out.billboard(p, Sprite.FLARE, 3.2f + 9.6f * Ease.hash((int) (s.time / 4) + 3), (SlotRig.WY1 + SlotRig.PY1) / 2, SZ + 0.3f, 2.2f, s.time * 7, SKY, 0.3f);
        }
        float press = playing && at[0] == 0 && at[1] < 6 ? Ease.bell(at[1], 0, 6) * 0.25f : 0;
        p.push().translate(0, -press, 0);
        out.solid(p, spinBtn, playing ? 0.3f : 0.55f + 0.35f * s.pulse(0.12f));
        p.pop();
        out.solid(p, bonusBtn, 0.35f + 0.25f * s.pulse(0.09f));
        out.solid(p, screenBg, 0.45f);
        // intro (le joueur vient de s'installer) : Zeus et sa foudre, puis la grille tombe colonne par colonne
        introT = Intro.time(v.intro, playing, s.item);
        if (Intro.scene(introT)) {
            intro(s, p, out, introT);
        } else {
            out.solid(p, gridBg, 0.3f);
            out.solid(p, bars, 0.45f);
            out.solid(p, cloud, 0.5f);
            screen(s, v, p, out, at);
            Intro.title(p, out, introT, I18n.get("heralizcasino.olympe.titre"), I18n.get("heralizcasino.olympe.intro"), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, GOLD, SKY);
        }
        out.translucent(p, glass, 0xFFFFFF, 0.25f);
    }

    /** Temps de l'intro pour l'image en cours (-1 : pas d'intro) ; lu par la grille (arrivee des colonnes). */
    private float introT = -1;

    /**
     * Intro : le tube s'allume sur un ciel d'orage, les nuages arrivent, Zeus monte sur le sien, leve le bras et lance la
     * foudre : eclair blanc, le titre claque ; la grille arrive ensuite (Intro.REVEAL).
     */
    private void intro(Scene s, Pose p, DrawList out, float t) {
        float cx = 8, z = SZ + 0.04f;
        float sh = Intro.shake(t, 41, 10, 0.35f);
        MeshBuilder sky = new MeshBuilder(), zeus = new MeshBuilder(), fx = new MeshBuilder(), bolts = new MeshBuilder();
        // eclairs au loin dans les nuages
        for (int k = 0; k < 3; k++) {
            float f = Ease.hash((int) (t / 3) * 7 + k * 31) > 0.86f && t > 8 && t < 40 ? 0.35f : 0;
            Intro.quad(fx, FLASH, 3 + k * 4.4f - 3, WY1 - 6, 3 + k * 4.4f + 3, WY1, WX0, WY0, WX1, WY1, z, f, false);
        }
        // nuages : ils glissent des deux cotes et se rejoignent
        for (int k = 0; k < 4; k++) {
            boolean right = k % 2 == 1;
            float in = Ease.outCubic(Ease.range(t, 6 + k * 2, 22 + k * 2));
            float x = cx + (right ? 1 : -1) * (12.5f - (7.4f - k * 0.7f) * in) + (float) Math.sin(t * 0.06f + k) * 0.25f + sh;
            float y = WY1 - 2.4f - k * 1.1f, w = 8f, h = 4f;
            Intro.quad(sky, CLOUD, x - w / 2, y - h / 2, x + w / 2, y + h / 2, WX0, WY0, WX1, WY1, z + 0.01f + k * 0.004f, 0.75f, right);
        }
        // Zeus monte sur son nuage, leve le bras, lance la foudre, puis s'eleve et disparait dans l'eclair
        float rise = Ease.outBack(Ease.range(t, 14, 28), 1.3f), leave = Ease.inCubic(Ease.range(t, 48, 60));
        float zh = 9.2f, zw = zh * 0.5f;
        float zx = cx + 1.6f + sh, zy = WY0 + 1.2f - zh / 2 + (zh * 0.95f + 2.6f) * rise + 6f * leave;
        boolean arm = t >= 30;
        Intro.quad(zeus, arm ? ZEUS_B : ZEUS_A, zx - zw / 2, zy - zh / 2, zx + zw / 2, zy + zh / 2, WX0, WY0, WX1, WY1, z + 0.05f, 1f - 0.6f * leave, false);
        float cw = 6.2f, ch = 2.6f, cyy = zy - zh / 2 + 0.6f;
        Intro.quad(zeus, CLOUD, zx - cw / 2, cyy - ch / 2, zx + cw / 2, cyy + ch / 2, WX0, WY0, WX1, WY1, z + 0.06f, 0.95f, false);
        out.solid(p, sky.buildDynamic(), 0.35f);
        out.solid(p, zeus.buildDynamic(), 0.7f + 0.3f * Ease.bell(t, 30, 46));
        // la main se charge, puis la foudre part vers la grille
        float hx = zx + 0.65f * zh / ZH, hy = zy + 2.4f * zh / ZH;
        if (t >= 28 && t < 44 && rise > 0.5f) {
            float charge = Ease.range(t, 28, 40);
            out.billboard(p, Sprite.HALO, hx, hy, z + 0.2f, 1.2f + 2.2f * charge, 0, SKY, 0.35f + 0.4f * charge);
            out.billboard(p, Sprite.FLARE, hx, hy, z + 0.22f, 1.0f + 1.4f * charge, s.time * 9, 0xE8F4FF, 0.3f + 0.6f * charge);
        }
        if (t >= 40 && t < 48) {
            float k = 1 - Ease.range(t, 44, 48);
            bolt(bolts, hx, hy, cx - 3.2f, WY0 + 1.0f, 1.4f, z + 0.12f, k);
            bolt(bolts, hx, hy, cx + 0.6f, WY0 + 2.2f, 0.8f, z + 0.12f, k * 0.6f);
            out.additive(p, bolts.buildDynamic(), 0xD8ECFF, 1f);
        }
        // eclair blanc sur tout l'ecran (Zeus reste lisible dedans)
        float white = t >= 41 ? 0.6f * (1 - Ease.range(t, 41, 52)) : 0;
        if (white > 0.01f) Intro.quad(fx, FLASH, WX0 - 6, WY0 - 6, WX1 + 6, WY1 + 6, WX0, WY0, WX1, WY1, z + 0.3f, white, false);
        if (!fx.isEmpty()) out.additive(p, fx.buildDynamic(), 0xF0F6FF, 1f);
        Intro.crtOn(p, out, DARK, FLASH, t, WX0, WY0, WX1, WY1, SZ + 0.36f, SKY);
        Intro.title(p, out, t, I18n.get("heralizcasino.olympe.titre"), I18n.get("heralizcasino.olympe.intro"), 8, (RY0 + RY1) / 2, SZ + 0.32f, 11.4f, GOLD, SKY);
    }

    /** Case a la rangee y (fractionnaire, 0 = haut) du rouleau reel, decoupee a la fenetre de la grille. */
    private static void cell(MeshBuilder b, int reel, float y, Mat m, float tint, float scale) {
        float cx = RX0 + (reel + 0.5f) * CW, cy = RY1 - (y + 0.5f) * CH;
        float hw = (CW - 0.08f) / 2 * scale, hh = (CH - 0.08f) / 2 * scale;
        BigWin.clipQuad(b, m, cx - hw, cy - hh, cx + hw, cy + hh, RY0, RY1, SZ + 0.03f, tint, false);
    }

    private static float cellX(int reel) {
        return RX0 + (reel + 0.5f) * CW;
    }

    private static float cellY(float row) {
        return RY1 - (row + 0.5f) * CH;
    }

    /** Symbole d'une case (orbes selon leur valeur). */
    private static Mat mat(OlympeRules r, int sym, int orb) {
        if (sym != Olympe.ORB) return symbol(sym);
        return orb(orbTier(orbValue(r, orb)));
    }

    static int orbValue(OlympeRules r, int orb) {
        return orb > 0 && orb <= r.orbValues.length ? r.orbValues[orb - 1] : 2;
    }

    /** Orbe et sa valeur a dessiner par-dessus. */
    private record OrbLabel(float x, float y, int value, float a) {
    }

    private void screen(Scene s, OlympeView v, Pose p, DrawList out, float[] at) {
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        Olympe.Spin spin = i >= 0 ? v.seq.spins.get(i) : null;
        boolean finished = at != null && v.since >= v.totalTicks();
        MeshBuilder mb = new MeshBuilder(), glow = new MeshBuilder(), flash = new MeshBuilder(), bolts = new MeshBuilder();
        List<OrbLabel> labels = new ArrayList<>();
        float dim = BigWin.dim(bigAt(spin, lt), Rotten.BIG_WIN_TICKS);
        float pulse = 0.55f + 0.45f * (float) Math.sin(s.time * 0.6f);
        boolean casting = false;
        // suspense : colonne en train de tomber au ralenti, Zeus deja poses qui l'attendent
        float tension = 0;
        int teaseReel = -1, teaseZeus = 0;
        if (spin == null) {
            for (int reel = 0; reel < Olympe.REELS; reel++) {
                // fin de l'intro : les colonnes tombent l'une apres l'autre
                float drop = Intro.drop(introT, reel, Olympe.ROWS), land = Intro.landed(introT, reel);
                for (int row = 0; row < Olympe.ROWS; row++) {
                    int c = Olympe.cell(reel, row);
                    int slot = (int) (s.time / 40);
                    int pick = (int) (Ease.hash(slot + (int) (s.seed * 50)) * Olympe.CELLS);
                    float k = pick == c ? 1f + 0.5f * Ease.bell(s.time % 40, 0, 20) : 1f;
                    cell(mb, reel, row - drop, symbol(IDLE[c]), k * (1 + 0.5f * land), 1);
                }
                if (land > 0) quadAt(flash, cellX(reel), cellY(Olympe.ROWS - 0.5f), CW * 1.7f, FLASH, 0.7f * land);
            }
        } else if (lt < Olympe.drop(spin)) {
            // l'ancienne grille tombe, la nouvelle arrive du ciel, colonne par colonne
            int[] oldGrid = IDLE, oldOrb = new int[Olympe.CELLS];
            if (i > 0) {
                Olympe.Step last = v.seq.spins.get(i - 1).last();
                oldGrid = last.grid;
                oldOrb = last.orb;
            }
            Olympe.Step st = spin.steps.get(0);
            // suspense : il ne manque qu'un Zeus, les colonnes restantes tombent au ralenti
            float[] tz = teasing(spin, lt);
            int hot = tz == null ? -1 : (int) tz[0];
            tension = tz == null ? 0 : Suspense.tension(tz[1], SLOW);
            for (int reel = 0; reel < Olympe.REELS; reel++) {
                float land = Olympe.landTick(spin, reel);
                boolean teased = Olympe.tease(spin, reel);
                // colonne par colonne : l'ancienne tombe hors de l'ecran, puis la nouvelle arrive du ciel ; chute normale :
                // 10 ticks, en accelerant ; en suspens : toute la duree du ralenti, a vitesse constante
                float fallFrom = teased ? land - SLOW : land - 10;
                float to = Math.max(0, lt - (fallFrom - 6));
                float off = to * to * 0.1f;
                float un = teased ? Ease.clamp01((lt - fallFrom) / SLOW) : Ease.inQuad(Ease.clamp01((lt - fallFrom) / 10f));
                for (int row = 0; row < Olympe.ROWS; row++) {
                    int c = Olympe.cell(reel, row);
                    if (off < Olympe.ROWS + 1) {
                        float y = row + off;
                        cell(mb, reel, y, mat(v.rules, oldGrid[c], oldOrb[c]), 0.9f, 1);
                        if (oldGrid[c] == Olympe.ORB) labels.add(new OrbLabel(cellX(reel), cellY(y), orbValue(v.rules, oldOrb[c]), 0.9f));
                    }
                    if (un > 0) {
                        // les orbes tombent eteints : Zeus les foudroie a l'arrivee et leur valeur apparait
                        float y = row - (Olympe.ROWS + 1) * (1 - un);
                        float tint = st.grid[c] == Olympe.ORB ? 0.45f : 1f;
                        if (hot >= 0 && reel != hot && un >= 1) tint *= st.grid[c] == Olympe.ZEUS ? 1.2f + 0.25f * pulse : Tease.dim(tension);
                        cell(mb, reel, y, mat(v.rules, st.grid[c], st.orb[c]), tint, 1);
                    }
                }
            }
            if (hot >= 0 && !s.item) {
                teaseReel = hot;
                for (int c = 0; c < hot * Olympe.ROWS; c++) {
                    if (st.grid[c] == Olympe.ZEUS) teaseZeus |= 1 << c;
                }
            }
        } else {
            int n = spin.steps.size();
            int k = 0;
            while (k < n - 1 && lt >= Olympe.stepStart(spin, k + 1)) k++;
            float local = lt - Olympe.stepStart(spin, k);
            Olympe.Step st = spin.steps.get(k);
            boolean more = k < n - 1 && st.winMask != 0;
            float show = Olympe.SHOW, pop = Olympe.SHOW + Olympe.POP;
            int os = orbStart(spin);
            boolean orbPhase = orbsApply(spin) && lt >= os && lt < os + Olympe.ORB_TICKS;
            if (!more || local < pop) {
                float u = more ? Ease.clamp01((local - show) / Olympe.POP) : 0;
                int orbIdx = 0;
                for (int reel = 0; reel < Olympe.REELS; reel++) {
                    for (int row = 0; row < Olympe.ROWS; row++) {
                        int c = Olympe.cell(reel, row);
                        boolean win = more && (st.winMask & (1 << c)) != 0;
                        int sym = st.grid[c];
                        float tint = (more ? (win ? 1.25f : 0.5f) : 1f) * dim;
                        float scale = 1;
                        if (win && local >= show) {
                            scale = 1 + 0.35f * u;
                            tint = 1.3f * (1 - u);
                            if (u < 0.8f) quadAt(flash, cellX(reel), cellY(row), CW * (0.8f + u), FLASH, 1 - u);
                        }
                        if (sym == Olympe.ZEUS && k == 0 && local < 10) tint *= 1f + 0.5f * Ease.bell(local, 0, 10);
                        // le Zeus tant attendu s'est pose sur une colonne en suspens : eclair
                        if (sym == Olympe.ZEUS && k == 0 && Olympe.tease(spin, reel)) {
                            float since = lt - Olympe.landTick(spin, reel);
                            if (since < 12) quadAt(flash, cellX(reel), cellY(row), CW * 2.6f, FLASH, 1 - Ease.clamp01(since / 12f));
                        }
                        // orbe tout juste arrive : eteint jusqu'a ce que l'eclair de Zeus le frappe
                        float strikeK = 1;
                        if (sym == Olympe.ORB && (newOrbs(spin, k) & (1 << c)) != 0) {
                            int hitAt = strikeAt(spin, k, c);
                            if (lt < hitAt) {
                                tint = 0.45f * dim;
                                strikeK = 0;
                            } else if (lt < hitAt + 6) {
                                bolt(bolts, HAND[0], HAND[1], cellX(reel), cellY(row), 1.0f, SZ + 0.14f, 1 - Ease.range(lt, hitAt + 3, hitAt + 6));
                                quadAt(flash, cellX(reel), cellY(row), CW * 1.6f, FLASH, 1 - Ease.range(lt, hitAt, hitAt + 6));
                                tint = 1.7f * dim;
                                strikeK = Ease.outBack(Ease.clamp01((lt - hitAt) / 4f), 3f);
                            }
                        }
                        // orbes : foudroyes par Zeus puis leur valeur s'envole vers le compteur
                        float orbA = 1f;
                        if (sym == Olympe.ORB && orbPhase && k == n - 1) {
                            float ot = lt - os, hit = 2 + orbIdx * 2.5f;
                            casting = true;
                            if (ot >= hit && ot < hit + 6) {
                                bolt(bolts, HAND[0], HAND[1], cellX(reel), cellY(row), 0.9f, SZ + 0.14f, 1 - Ease.range(ot, hit + 3, hit + 6));
                                tint = 1.6f;
                            }
                            if (ot >= hit + 6) orbA = 1 - 0.65f * Ease.range(ot, hit + 6, hit + 12);
                            orbIdx++;
                        }
                        // orbes deja encaisses : ils restent en veilleuse
                        boolean spent = sym == Olympe.ORB && orbsApply(spin) && k == n - 1 && lt >= os + Olympe.ORB_TICKS;
                        if (spent) orbA = 0.35f;
                        if (orbA > 0.02f) cell(mb, reel, row, mat(v.rules, sym, st.orb[c]), tint * orbA, scale * (strikeK < 1 && strikeK > 0 ? 0.8f + 0.2f * strikeK : 1));
                        if (win && local < show) quadAt(glow, cellX(reel), cellY(row), CW, symbol(sym), pulse);
                        if (sym == Olympe.ORB) {
                            float ox = cellX(reel), oy = cellY(row), a = orbA;
                            if (orbPhase && k == n - 1) {
                                float ot = lt - os, fly = Ease.inOutCubic(Ease.range(ot, 4 + orbIdx * 2.5f + 4, 4 + orbIdx * 2.5f + 12));
                                ox = Ease.lerp(ox, ZX, fly);
                                oy = Ease.lerp(oy, MULT_Y, fly);
                                a = 1 - Ease.range(fly, 0.85f, 1);
                            }
                            if (spent) a = 0.35f;
                            if (strikeK > 0) labels.add(new OrbLabel(ox, oy, orbValue(v.rules, st.orb[c]), a * Math.min(1f, strikeK)));
                        }
                    }
                }
                // gain de la cascade au centre des symboles gagnants
                if (more && st.winMilli > 0) {
                    float sx = 0, sy = 0;
                    int cnt = 0;
                    for (int c = 0; c < Olympe.CELLS; c++) {
                        if ((st.winMask & (1 << c)) == 0) continue;
                        sx += cellX(c / Olympe.ROWS);
                        sy += cellY(c % Olympe.ROWS);
                        cnt++;
                    }
                    long amount = (long) Math.floor(v.bet * st.winMilli / 1000.0);
                    String txt = fmt(amount);
                    float a = Math.min(1f, local / 3f);
                    out.text(p, txt, sx / cnt, sy / cnt + local * 0.04f, SZ + 0.22f, fit(txt, 1.1f, 5f, true), GOLD, a,
                            DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD, 0x2A1400);
                }
            } else {
                // cascade : les survivants tombent, les nouveaux symboles arrivent par le haut
                Olympe.Step next = spin.steps.get(k + 1);
                float ft = local - pop;
                for (int reel = 0; reel < Olympe.REELS; reel++) {
                    float u = Ease.inQuad(Ease.clamp01((ft - reel * 0.6f) / (Olympe.FALL - 4)));
                    int target = Olympe.ROWS - 1;
                    for (int row = Olympe.ROWS - 1; row >= 0; row--) {
                        if ((st.winMask & (1 << Olympe.cell(reel, row))) != 0) continue;
                        int c = Olympe.cell(reel, target);
                        float y = Ease.lerp(row, target, u);
                        cell(mb, reel, y, mat(v.rules, next.grid[c], next.orb[c]), 1f, 1);
                        if (next.grid[c] == Olympe.ORB) labels.add(new OrbLabel(cellX(reel), cellY(y), orbValue(v.rules, next.orb[c]), 1f));
                        target--;
                    }
                    int fresh = target + 1;
                    for (int row = 0; row < fresh; row++) {
                        // nouveaux symboles ; un orbe arrive eteint (Zeus le foudroiera)
                        int c = Olympe.cell(reel, row);
                        float y = Ease.lerp(row - fresh - 0.3f, row, u);
                        cell(mb, reel, y, mat(v.rules, next.grid[c], next.orb[c]), next.grid[c] == Olympe.ORB ? 0.45f : 1f, 1);
                    }
                }
            }
        }
        out.solid(p, mb.buildDynamic(), 0.55f);
        if (!glow.isEmpty()) out.additive(p, glow.buildDynamic(), 0xFFFFFF, 0.4f * dim);
        if (!flash.isEmpty()) out.additive(p, flash.buildDynamic(), GOLD, 1f);
        if (!bolts.isEmpty()) out.additive(p, bolts.buildDynamic(), 0xC8E8FF, 1f);
        if (teaseReel >= 0) {
            Tease.column(s, p, out, RX0 + teaseReel * CW, RY0, RX0 + (teaseReel + 1) * CW, RY1, SZ + 0.1f, SKY, tension);
            for (int c = 0; c < Olympe.CELLS; c++) {
                if ((teaseZeus & (1 << c)) != 0) Tease.ember(s, p, out, cellX(c / Olympe.ROWS), cellY(c % Olympe.ROWS), SZ + 0.25f, CW * 0.8f, GOLD, tension);
            }
        }
        for (OrbLabel l : labels) {
            if (l.y < RY0 - 0.2f || l.y > RY1 + 0.2f || l.a < 0.05f) continue;
            String txt = "x" + l.value;
            out.text(p, txt, l.x, l.y - 0.25f, SZ + 0.2f, fit(txt, 0.62f, CW * 0.95f, true), WHITE, l.a * dim, DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD,
                    0x000000);
        }
        zeus(s, v, p, out, i, spin, lt, casting || (spin != null && striking(spin, lt)), finished);
        bars(s, v, p, out, i, spin, lt, finished);
        overlays(s, v, p, out, i, spin, lt, finished);
    }

    /** Quad centre en (x, y), carre de cote size. */
    private static void quadAt(MeshBuilder b, float x, float y, float size, Mat m, float k) {
        float h = size / 2;
        BigWin.clipQuad(b, m, x - h, y - h, x + h, y + h, -1e9f, 1e9f, SZ + 0.06f, k, false);
    }

    /** Eclair (texture etiree) entre deux points de l'ecran. */
    private static void bolt(MeshBuilder b, float x0, float y0, float x1, float y1, float w, float z, float k) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-3f) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        float[][] pts = {p(x0 - nx, y0 - ny, z), p(x0 + nx, y0 + ny, z), p(x1 + nx, y1 + ny, z), p(x1 - nx, y1 - ny, z)};
        float[][] uv = {{BOLT.u(0), BOLT.v(0)}, {BOLT.u(1), BOLT.v(0)}, {BOLT.u(1), BOLT.v(1)}, {BOLT.u(0), BOLT.v(1)}};
        b.color(k);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    /** Zeus a droite de la grille, et le compteur du multiplicateur. */
    private void zeus(Scene s, OlympeView v, Pose p, DrawList out, int i, Olympe.Spin spin, float lt, boolean casting, boolean finished) {
        if (bigAt(spin, lt) >= 0) return;
        boolean cast = casting || (spin != null && ((spin.events & Olympe.EV_FREE) != 0 && lt >= eventsStart(spin) && lt < eventsStart(spin) + Olympe.FREE_T))
                || (spin != null && lt >= Olympe.drop(spin) && lt < Olympe.drop(spin) + 8 && Olympe.count(spin.steps.get(0).grid, Olympe.ZEUS) >= 3)
                || teasing(spin, lt) != null;
        if (s.item) cast = false;
        float bob = (float) Math.sin(s.time * 0.08f) * 0.12f;
        MeshBuilder b = new MeshBuilder();
        float h = ZH, w = h * 0.5f;
        BigWin.clipQuad(b, cast ? ZEUS_B : ZEUS_A, ZX - w / 2, ZY - h / 2 + bob, ZX + w / 2, ZY + h / 2 + bob, WY0, WY1, SZ + 0.04f, 1f, false);
        out.solid(p, b.buildDynamic(), cast ? 0.8f : 0.55f);
        if (cast && !s.item) out.billboard(p, Sprite.HALO, HAND[0], HAND[1], SZ + 0.3f, 1.2f + 0.3f * s.pulse(0.5f), 0, SKY, 0.5f);
        if (s.item) return;
        // multiplicateur : total des tours gratuits, ou somme des orbes du tour
        int mult = 0;
        if (spin != null && spin.kind == Olympe.FREE) {
            mult = multBefore(v, i);
            if (orbsApply(spin) && lt >= orbStart(spin) + 16) mult = spin.totalMult;
        } else if (spin != null && orbsApply(spin) && lt >= orbStart(spin) + 10 && !(finished && v.since > v.totalTicks() + 200)) {
            mult = spin.orbSum;
        }
        if (mult > 0) {
            String txt = "x" + mult;
            float bump = spin != null && orbsApply(spin) ? 1 + 0.3f * Ease.bell(lt, orbStart(spin) + 14, orbStart(spin) + 22) : 1;
            out.text(p, txt, ZX, MULT_Y, SZ + 0.2f, fit(txt, 1.0f * bump, 2.4f, true), GOLD, 1f, DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD, 0x2A1400);
        }
    }

    /** Bandeaux : titre / multiplicateur en haut, mise et gain en bas. */
    private void bars(Scene s, OlympeView v, Pose p, DrawList out, int i, Olympe.Spin spin, float lt, boolean finished) {
        if (s.item) return;
        float yb = (WY0 + RY0) / 2 - 0.1f, yt = (RY1 + WY1) / 2 - 0.05f, z = SZ + 0.08f;
        if (spin == null || (finished && v.since > v.totalTicks() + 200)) {
            boolean blink = ((int) (s.time / 20)) % 2 == 0;
            String top = I18n.get(blink ? "heralizcasino.olympe.intro" : "heralizcasino.olympe.orbes");
            out.text(p, top, 8, yt, z, fit(top, 0.7f, 11.5f, true), blink ? SKY : GOLD, 0.95f, DrawList.T_CENTER);
            String bot = I18n.get("heralizcasino.olympe.partout");
            out.text(p, bot, 8, yb, z, fit(bot, 0.5f, 11.5f, false), WHITE, 0.9f, DrawList.T_CENTER);
            return;
        }
        String top;
        int col = GOLD;
        if (teasing(spin, lt) != null) {
            // suspense : il ne manque qu'un Zeus
            String sub = spin.kind == Olympe.FREE ? I18n.get("heralizcasino.olympe.pour_n", String.valueOf(v.rules.retrigger)) : I18n.get("heralizcasino.slot.pour_tours");
            String encore = I18n.get("heralizcasino.olympe.encore_zeus");
            out.text(p, encore, 8, yt + 0.5f, z, fit(encore, 1.05f, 11.4f, true), GOLD, 0.7f + 0.3f * s.pulse(0.5f), DrawList.T_CENTER | DrawList.T_BOLD);
            out.text(p, sub, 8, yt - 0.8f, z, fit(sub, 0.5f, 11.4f, false), WHITE, 0.9f, DrawList.T_CENTER);
        } else {
            if (spin.kind == Olympe.FREE) {
                top = I18n.get("heralizcasino.olympe.tours", String.valueOf(spin.freeTotal - spin.freeLeft), String.valueOf(spin.freeTotal));
                col = SKY;
            } else {
                top = I18n.get("heralizcasino.olympe.intro");
                col = SKY;
            }
            out.text(p, top, 8, yt, z, fit(top, 0.7f, 11.5f, true), col, 1f, DrawList.T_CENTER);
        }
        // suspense : la camera remonte vers l'annonce du haut, le bandeau du bas passe sous les boutons (rien a y lire)
        if (teasing(spin, lt) != null) return;
        String mise = I18n.get("heralizcasino.slot.mise", fmt(v.bet));
        out.text(p, mise, WX0 + 0.3f, yb, z, fit(mise, 0.5f, 3.4f, false), WHITE, 0.9f, 0);
        long milli = v.winSoFar(i, false);
        if (lt >= winStart(spin)) {
            milli += spin.winMilli;
        } else {
            for (int k = 0; k < spin.steps.size(); k++) {
                Olympe.Step st = spin.steps.get(k);
                if (lt >= Olympe.stepStart(spin, k) + Olympe.SHOW) milli += st.winMilli;
            }
        }
        long won = (long) Math.floor(v.bet * (Math.min(milli, v.seq.totalMilli) / 1000.0));
        String g = I18n.get("heralizcasino.slot.gain", fmt(won));
        out.text(p, g, 9.3f, yb, z, fit(g, 0.6f, 6.4f, false), won > 0 ? GOLD : WHITE, 1f, DrawList.T_CENTER);
    }

    /** Annonces : gain du tour (ou Zeus des gros gains), tours gratuits, fin du bonus, gain maximum. */
    private void overlays(Scene s, OlympeView v, Pose p, DrawList out, int i, Olympe.Spin spin, float lt, boolean finished) {
        if (spin == null || s.item) return;
        float cx = (RX0 + RX1) / 2, cy = (RY0 + RY1) / 2, z = SZ + 0.3f;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        // multiplicateur applique
        int os = orbStart(spin);
        if (orbsApply(spin) && lt >= os + 18 && lt < os + Olympe.ORB_TICKS) {
            float a = Ease.clamp01((lt - os - 18) / 3f);
            long base = (long) Math.floor(v.bet * spin.tumbleMilli() / 1000.0);
            String txt = fmt(base) + " x" + spin.appliedMult();
            out.text(p, txt, cx, cy, z, fit(txt, 1.5f, 8.5f, true), GOLD, a, flags, 0x2A1400);
        }
        float t = lt - winStart(spin);
        int winT = Olympe.winShow(spin);
        int tier = Rotten.bigTier(spin.winMilli);
        if (spin.winMilli > 0 && t >= 0 && t < winT) {
            long amount = (long) Math.floor(v.bet * spin.winMilli / 1000.0);
            if (tier > 0) {
                zeusRise(p, out, t);
                BigWin.text(p, out, t, winT, tier, amount, 8, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, tier >= 3 ? PURPLE : tier == 2 ? SKY : GOLD, GOLD);
            } else {
                float popK = Ease.outBack(Math.min(1f, t / 5f), 2.2f);
                String txt = "+" + fmt(amount);
                out.text(p, txt, cx, cy, z, fit(txt, 1.8f * popK, 8.5f, true), GOLD, 1f, flags, 0x2A1400);
            }
        }
        float e = lt - eventsStart(spin);
        int ft0 = Olympe.FREE_T, rt = Olympe.RETRIGGER_T;
        if ((spin.events & Olympe.EV_FREE) != 0 && e >= 0 && e < ft0) {
            float a = Math.min(1f, e / 5f) * (1 - Ease.range(e, ft0 - 6, ft0));
            float popK = Ease.outBack(Math.min(1f, e / 7f), 2f);
            String portes = I18n.get("heralizcasino.olympe.portes");
            out.text(p, portes, 8, cy + 1.8f, z, fit(portes, 1.3f, 11.5f, true) * popK, SKY, a, flags, 0x000000);
            String ft = I18n.get("heralizcasino.slot.n_tours_gratuits", String.valueOf(spin.freeTotal));
            out.text(p, ft, 8, cy - 0.2f, z, fit(ft, 1.5f, 11.5f, true), GOLD, a, flags, 0x2A1400);
            String plus = I18n.get("heralizcasino.olympe.additionnent");
            out.text(p, plus, 8, cy - 2.0f, z, fit(plus, 0.6f, 11.5f, true), WHITE, a, flags, 0x000000);
            return;
        }
        if ((spin.events & Olympe.EV_FREE) != 0) e -= ft0;
        if ((spin.events & Olympe.EV_RETRIGGER) != 0 && e >= 0 && e < rt) {
            float a = Math.min(1f, e / 4f) * (1 - Ease.range(e, rt - 5, rt));
            String plus = I18n.get("heralizcasino.slot.plus_tours_gratuits", "5");
            out.text(p, plus, 8, cy, z, fit(plus, 1.4f, 11.5f, true), GOLD, a, flags, 0x2A1400);
            return;
        }
        boolean max = (spin.events & Olympe.EV_MAXWIN) != 0;
        int end = end(spin);
        if (max && lt >= end) {
            long total = (long) Math.floor(v.bet * v.seq.totalMilli / 1000.0);
            float m = lt - end;
            if (m < Rotten.BIG_WIN_TICKS) {
                zeusRise(p, out, m);
                BigWin.text(p, out, m, Rotten.BIG_WIN_TICKS, 4, total, 8, RY1 - 0.9f, RY0 + 1.2f, z + 0.1f, 11.5f, GOLD, GOLD);
            } else {
                BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * Ease.clamp01((m - Rotten.BIG_WIN_TICKS) / 5f));
                String maxTxt = I18n.get("heralizcasino.slot.gain_max");
                out.text(p, maxTxt, 8, cy + 1.2f, z, fit(maxTxt, 1.8f, 11f, true), GOLD, 1f, flags, 0x2A1400);
                out.text(p, fmt(total), 8, cy - 1.0f, z, fit(fmt(total), 2.2f, 11f, true), GOLD, 1f, flags, 0x2A1400);
            }
            return;
        }
        if ((spin.events & Olympe.EV_BONUS_END) != 0 && lt >= end - Olympe.END_T) {
            float k = lt - (end - Olympe.END_T);
            float a = Math.min(1f, k / 5f);
            long total = (long) Math.floor(v.bet * v.winSoFar(v.seq.spins.size(), false) / 1000.0);
            // le fond s'assombrit, le total ressort (les boutons du joueur reviennent, voir summary)
            BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * a);
            out.text(p, I18n.get("heralizcasino.slot.gain_total"), 8, cy + 1.3f, z, 1.2f, WHITE, a, flags, 0x000000);
            String tt = fmt(total);
            out.text(p, tt, 8, cy - 1.0f, z, fit(tt, 2.4f * Ease.outBack(Math.min(1f, k / 7f), 2f), 11f, true), GOLD, a, flags, 0x2A1400);
        }
    }

    /** Zeus des gros gains : il surgit au centre de l'ecran, la foudre a la main. */
    private void zeusRise(Pose p, DrawList out, float t) {
        BigWin.mascot(p, out, t, Rotten.BIG_WIN_TICKS, ZEUS_A, ZEUS_B, null, 8, (RY0 + RY1) / 2 - 0.4f, 9.4f, 0.5f, RY0 - 0.25f, WY1 - 0.1f, SZ + 0.12f,
                FZ, 0, null);
    }

    // =================================================================== camera
    @Override
    public void camera(OlympeView v, Scene s, int mode, Shot out) {
        // suspense : on se rapproche de la colonne qui tombe au ralenti ; l'image remonte pour garder l'annonce du haut
        // en entier (la barre du bas passe sous les boutons, l'ecran de jeu n'affiche plus sa ligne d'etat)
        float[] at = v.at(v.since);
        float[] tz = at == null || mode == CAM_MENU ? null : teasing(v.seq.spins.get((int) at[0]), at[1]);
        if (tz != null) {
            float k = Ease.inOutSine(Ease.clamp01(tz[1] / 10f));
            float x = Ease.lerp(8, cellX((int) tz[0]) * 0.5f + 4, k);
            out.set(x, 24.6f + 1.3f * k, 36.5f - 4.5f * k, x, 23.2f + 1.7f * k, SZ, 44 - 3 * k);
            return;
        }
        if (mode == CAM_MENU) out.set(8, 29, 46, 8, 26, 13, 52);
        else out.set(8, 24.6f, 36.5f, 8, 23.2f, SZ, 44);
    }

    @Override
    public boolean zoomable() {
        return true;
    }

    /** Gain total de fin de bonus (ou gain maximum) a l'ecran : les boutons du joueur reviennent. */
    @Override
    public float summary(OlympeView v, Scene s) {
        float[] at = v.at(v.since);
        if (at == null || v.since > v.totalTicks() + 200 || (int) at[0] != v.seq.spins.size() - 1) return 0;
        Olympe.Spin spin = v.seq.spins.get((int) at[0]);
        boolean max = (spin.events & Olympe.EV_MAXWIN) != 0;
        if (!max && (spin.events & Olympe.EV_BONUS_END) == 0) return 0;
        int end = end(spin);
        float k = max ? at[1] - end - Rotten.BIG_WIN_TICKS : at[1] - (end - Olympe.END_T);
        return Ease.clamp01(k / 5f);
    }

    /** Image de jeu de l'ecran en entier (bandeaux du haut et du bas compris), cadree par le zoom du joueur et pendant l'intro. */
    @Override
    public float[] screenRect(OlympeView v, Scene s) {
        return new float[]{WX0, WY0, WX1, WY1, SZ};
    }

    @Override
    public float screenFocus(OlympeView v, Scene s) {
        return Intro.focus(v.intro, v.at(v.since) != null && v.since < v.totalTicks() + 40);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, OlympeView v, FxSink fx, Random r) {
        if (r.nextInt(90) == 0) fx.particle(FxSink.WISP, 8 + rnd(r, 6), SlotRig.TV_Y1 + 1 + rnd(r, 1.5f), 14.6f, 0, 0.02f, 0.01f, GOLD, 0.3f, 40);
        float[] at = v.at(v.since);
        // intro : grondement, nuages, la main de Zeus se charge, le tonnerre, le titre
        float it = Intro.time(v.intro, at != null && v.since < v.totalTicks() + 40, s.item);
        if (it >= 0) {
            float sy = (RY0 + RY1) / 2;
            Intro.sounds(fx, it, Olympe.REELS, RX0, CW, sy, 15);
            if (at(it, 6)) fx.sound(Snd.TENSION, 8, sy, 15, 0.8f, 0.8f);
            if (at(it, 10)) fx.sound(Snd.WHOOSH, 8, sy, 15, 0.7f, 0.7f);
            if (at(it, 16)) fx.sound(Snd.THUNDER, 4, 30, 15, 0.4f, 0.7f);
            if (at(it, 30)) fx.sound(Snd.SPARKLE, 10, 28, 15, 0.8f, 1.1f);
            if (at(it, 40)) fx.sound(Snd.THUNDER, 8, sy, 15, 1f, 0.85f);
            if (at(it, 42)) fx.sound(Snd.THUNDER, 8, sy, 15, 0.7f, 1.3f);
            if (at(it, 41)) {
                for (int q = 0; q < 24; q++) fx.particle(FxSink.SPARK, 8 + rnd(r, 5), 20 + r.nextFloat() * 3, SZ + 0.6f, rnd(r, 0.15f), 0.05f + r.nextFloat() * 0.12f,
                        0.06f, r.nextBoolean() ? SKY : GOLD, 0.4f, 16 + r.nextInt(10));
            }
        }
        if (at == null || v.since > v.totalTicks() + 10) return;
        int i = (int) at[0];
        float lt = at[1];
        Olympe.Spin spin = v.seq.spins.get(i);
        float sy = (RY0 + RY1) / 2;
        if (at(lt, 0)) fx.sound(Snd.WHOOSH, 8, sy, 15, 0.6f, 1.2f);
        int drop = Olympe.drop(spin);
        if (at(lt, drop - 4)) fx.sound(Snd.REEL_STOP, 8, sy, 15, 0.5f, 0.9f);
        // suspense : le coeur bat pendant qu'une colonne tombe au ralenti ; puis la cloche (Zeus) ou la retombee
        for (int reel = 1; reel < Olympe.REELS; reel++) {
            if (!Olympe.tease(spin, reel)) continue;
            float land = Olympe.landTick(spin, reel);
            Tease.sounds(fx, lt - (land - SLOW), SLOW, cellX(reel), sy, 15);
            if (!at(lt, (int) Math.floor(land))) continue;
            boolean hit = false;
            for (int row = 0; row < Olympe.ROWS; row++) {
                if (spin.steps.get(0).grid[Olympe.cell(reel, row)] != Olympe.ZEUS) continue;
                hit = true;
                burst(fx, r, cellX(reel), cellY(row), SZ + 0.6f, GOLD, SKY, 26, 0.4f, 0.4f);
            }
            fx.sound(hit ? Snd.BELL : Snd.MISS, cellX(reel), sy, 15, hit ? 0.9f : 0.7f, hit ? 1.4f : 1f);
            fx.sound(Snd.REEL_STOP, cellX(reel), sy, 15, 0.5f, 0.9f);
        }
        if (at(lt, drop)) {
            Olympe.Step st = spin.steps.get(0);
            int zeus = Olympe.count(st.grid, Olympe.ZEUS);
            if (zeus > 0) fx.sound(Snd.SCATTER, 8, sy, 15, 0.9f, 0.7f + zeus * 0.1f);
            if (zeus >= 4) fx.sound(Snd.THUNDER, 8, 40, 15, 1f, 1f);
            if (Olympe.count(st.grid, Olympe.ORB) > 0) fx.sound(Snd.SPARKLE, 8, sy, 15, 0.8f, 1.3f);
        }
        // Zeus lance la foudre sur chaque orbe qui arrive
        for (int k = 0; k < spin.steps.size(); k++) {
            int mask = newOrbs(spin, k);
            for (int c = 0; c < Olympe.CELLS; c++) {
                if ((mask & (1 << c)) == 0 || !at(lt, strikeAt(spin, k, c))) continue;
                fx.sound(Snd.THUNDER, cellX(c / Olympe.ROWS), cellY(c % Olympe.ROWS), 15, 0.5f, 1.4f + r.nextFloat() * 0.2f);
                fx.sound(Snd.SPARKLE, cellX(c / Olympe.ROWS), cellY(c % Olympe.ROWS), 15, 0.7f, 1.2f);
                for (int q = 0; q < 5; q++) fx.particle(FxSink.SPARK, cellX(c / Olympe.ROWS), cellY(c % Olympe.ROWS), SZ + 0.6f, rnd(r, 0.1f), rnd(r, 0.1f), 0.08f, SKY, 0.35f, 10);
            }
        }
        for (int k = 0; k < spin.steps.size() - 1; k++) {
            Olympe.Step st = spin.steps.get(k);
            int start = Olympe.stepStart(spin, k);
            if (st.winMask == 0) continue;
            if (at(lt, start)) fx.sound(Snd.WIN_SMALL, 8, sy, 15, 0.6f, 1f + k * 0.08f);
            if (at(lt, start + Olympe.SHOW)) {
                fx.sound(Snd.TUMBLE, 8, sy, 15, 0.9f, 1f + k * 0.05f);
                for (int c = 0; c < Olympe.CELLS; c++) {
                    if ((st.winMask & (1 << c)) == 0) continue;
                    burst(fx, r, cellX(c / Olympe.ROWS), cellY(c % Olympe.ROWS), SZ + 0.6f, GOLD, 0xFFFFFF, 3, 0.15f, 0.25f);
                }
            }
            if (at(lt, start + Olympe.SHOW + Olympe.POP + Olympe.FALL - 3)) fx.sound(Snd.REEL_STOP, 8, sy, 15, 0.35f, 1.1f);
        }
        if (orbsApply(spin)) {
            int os = orbStart(spin), n = Olympe.count(spin.last().grid, Olympe.ORB);
            for (int k = 0; k < n; k++) {
                if (at(lt, os + 2 + (int) (k * 2.5f))) {
                    fx.sound(Snd.THUNDER, HAND[0], HAND[1], 15, 0.6f, 1.2f + k * 0.05f);
                    fx.particle(FxSink.SPARK, HAND[0], HAND[1], SZ + 0.6f, rnd(r, 0.1f), 0.05f, 0.08f, SKY, 0.4f, 10);
                }
            }
            if (at(lt, os + 18)) fx.sound(Snd.SPARKLE, 8, sy, 15, 1f, 0.9f);
        }
        int ws = winStart(spin);
        if (spin.winMilli > 0 && at(lt, ws)) {
            boolean big = Rotten.bigTier(spin.winMilli) > 0;
            fx.sound(big ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
            if (big) {
                fx.sound(Snd.THUNDER, 8, 40, 15, 1f, 0.9f);
                fx.sound(Snd.COINS, 8, 16, 16, 0.9f, 1f);
                fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, SKY, WHITE}, 30, 0.35f);
            }
        }
        int es = eventsStart(spin);
        if ((spin.events & Olympe.EV_FREE) != 0 && at(lt, es)) {
            fx.sound(Snd.BONUS, 8, sy, 15, 1f, 1.2f);
            fx.sound(Snd.THUNDER, 8, 42, 15, 1f, 0.8f);
            burst(fx, r, 8, sy, 14.5f, SKY, GOLD, 40, 0.6f, 0.6f);
        }
        int end = end(spin);
        if ((spin.events & Olympe.EV_BONUS_END) != 0 && at(lt, end - Olympe.END_T)) {
            fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
            fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, SKY, PURPLE}, 40, 0.35f);
        }
        if ((spin.events & Olympe.EV_MAXWIN) != 0 && at(lt, end)) {
            fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
            fx.sound(Snd.THUNDER, 8, 42, 15, 1f, 0.7f);
            fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, SKY, PURPLE, WHITE}, 50, 0.4f);
        }
    }

    // =================================================================== vues
    @Override
    public OlympeView newView() {
        return new OlympeView();
    }

    @Override
    public OlympeView demoView(float t) {
        OlympeView v = new OlympeView();
        // apercu hors-jeu : graine fixe (-Dolympedemo=graine), achat du bonus (-Dolympebuy=1), premier gros gain (-Dolympebig=true)
        long seed = Long.getLong("olympedemo", 7L);
        int buy = (int) (long) Long.getLong("olympebuy", 0L);
        v.seq = Olympe.play(v.rules, new java.util.SplittableRandom(seed), buy);
        if (Boolean.getBoolean("olympebig")) {
            for (long k = seed; k < seed + 200_000; k++) {
                Olympe.Sequence q = Olympe.play(v.rules, new java.util.SplittableRandom(k), buy);
                if (q.spins.size() == 1 && Rotten.bigTier(q.spins.get(0).winMilli) > 0 && orbsApply(q.spins.get(0))) {
                    v.seq = q;
                    break;
                }
            }
        }
        v.bet = 100;
        v.since = t;
        return v;
    }

    @Override
    public int demoLength() {
        return 160;
    }
}
