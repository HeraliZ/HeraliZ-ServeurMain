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
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.Suspense;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Machine a sous "Rotten", montage de recuperation : un vieux televiseur kaki scotche pose sur une caisse de munitions,
 * une porte de tole et deux futs (toxique, carburant) sur une palette, batterie et cables ; le titre "ROTTEN" est
 * affiche en haut de l'ecran, barbeles, antenne tordue et baladeuse qui gresille sur le televiseur, guirlande verte,
 * seau rouille en guise de tabouret.
 * L'ecran (6 x 5 symboles) rejoue la partie tiree par le serveur : rouleaux qui defilent,
 * MUTATIONS (les symboles choisis se transforment en clowns ou en WILD), tours gratuits Savant Fou et Invasion Totale.
 */
public final class RottenDesign extends MachineDesign<RottenView> {
    // ---- atlas : partage avec tools/textures (PaintRotten) ; symboles en cases de 128 (8 par rangee)
    public static final int SYM_Y = 0, BLUR_Y = 256;
    public static final int[] R_WIRE = {768, 128, 128, 128};
    public static final int[] R_FLASH = {896, 128, 128, 128};
    public static final int[] R_BAR = {384, 384, 256, 128};
    public static final int[] R_CAB = {0, 512, 256, 256};
    public static final int[] R_RUST = {256, 512, 256, 256};
    public static final int[] R_TOPPER = {512, 512, 512, 256};
    public static final int[] R_SCREEN = {0, 768, 256, 256};
    public static final int[] R_PANEL = {256, 768, 256, 256};
    public static final int[] R_STEEL = {512, 768, 128, 128};
    public static final int[] R_BTN_RED = {640, 768, 128, 128};
    public static final int[] R_BTN_YEL = {768, 768, 128, 128};
    public static final int[] R_SEAT = {896, 768, 128, 128};
    public static final int[] R_DARK = {512, 896, 128, 64};
    public static final int[] R_LED = {640, 896, 128, 64};
    public static final int[] R_GLASS = {768, 896, 128, 64};
    public static final int[] R_BEZEL = {896, 896, 128, 128};
    /** Zombie des gros gains (buste calme / qui rugit) et main plaquee sur la vitre (images detourees). */
    public static final int[] R_ZOMBIE_A = {640, 384, 128, 128};
    public static final int[] R_ZOMBIE_B = {768, 384, 128, 128};
    public static final int[] R_HAND = {896, 384, 128, 128};

    static final Mat CAB = FruitsDesign.tri(R_CAB, 16), RUST = FruitsDesign.tri(R_RUST, 12), TOPPER = FruitsDesign.fit(R_TOPPER);
    static final Mat SCREEN = FruitsDesign.fit(R_SCREEN), PANEL = FruitsDesign.fit(R_PANEL), STEEL = FruitsDesign.tri(R_STEEL, 6);
    static final Mat BTN_RED = FruitsDesign.fit(R_BTN_RED), BTN_YEL = FruitsDesign.fit(R_BTN_YEL), SEAT = FruitsDesign.tri(R_SEAT, 6);
    static final Mat DARK = FruitsDesign.tri(R_DARK, 6), LED = FruitsDesign.fit(R_LED), GLASS = FruitsDesign.fit(R_GLASS);
    static final Mat BEZEL = FruitsDesign.tri(R_BEZEL, 6), WIRE = FruitsDesign.tri(R_WIRE, 4), FLASH = FruitsDesign.fit(R_FLASH);
    static final Mat BAR = FruitsDesign.fit(R_BAR);
    static final Mat ZOMBIE_A = FruitsDesign.fit(R_ZOMBIE_A), ZOMBIE_B = FruitsDesign.fit(R_ZOMBIE_B), HAND = FruitsDesign.fit(R_HAND);

    /** Symbole (net ou flou) dans l'atlas. */
    static Mat symbol(int s, boolean blur) {
        if (blur && s <= Rotten.WILD) return Mat.fit((s % 8) * 128, BLUR_Y + (s / 8) * 128, 128, 128);
        return Mat.fit((s % 8) * 128, SYM_Y + (s / 8) * 128, 128, 128);
    }

    // ---- cotes (px)
    static final float FZ = 14.2f, SZ = 13.65f;
    static final float WX0 = 1.9f, WX1 = 14.1f, WY0 = 18.6f, WY1 = 31.4f;
    static final float RX0 = 2.3f, RX1 = 13.7f, RY0 = 20.2f, RY1 = 29.4f;
    static final float CW = (RX1 - RX0) / Rotten.REELS, CH = (RY1 - RY0) / Rotten.ROWS;
    static final float[] SPIN_BTN = {12.1f, 16.7f, 15.3f}, BONUS_BTN = {3.8f, 16.7f, 15.3f};
    /** Ampoule de la baladeuse pendue a la potence rouillee vissee sur le televiseur (a droite). */
    static final float[] BULB = {14.4f, SlotRig.TV_Y1 + 2.1f, 12.9f};
    static final int TOXIC = 0x9CFF3A, BLOOD = 0xFF3A2A, GOLD = 0xFFD36A, PURPLE = 0xC06AFF, WHITE = 0xF4F0E8;
    /** Grille affichee au repos. */
    static final int[] IDLE = {
            9, 4, 7, 1, 3, 0, 6, 2, 9, 4, 5, 8, 1, 2, 0, 3, 7, 4, 6, 1, 2, 9, 5, 3, 0, 1, 8, 4, 7, 2};

    private Mesh body = Mesh.EMPTY, kit = Mesh.EMPTY, topperGlow = Mesh.EMPTY, screenBg = Mesh.EMPTY, glass = Mesh.EMPTY, bars = Mesh.EMPTY;
    private Mesh spinBtn = Mesh.EMPTY, bonusBtn = Mesh.EMPTY, bulb = Mesh.EMPTY, bulbGlow = Mesh.EMPTY, garland = Mesh.EMPTY, bodyLights = Mesh.EMPTY;

    public RottenDesign() {
        super("rotten", TOXIC, BLOOD);
        labelY = 45;
        itemScale = 0.36f;
    }

    // =================================================================== construction

    @Override
    protected void build() {
        float[] nz = v(0, 0, 1);
        MeshBuilder b = new MeshBuilder();
        MeshBuilder k = new MeshBuilder();
        // ---- montage de recuperation : palette, deux futs (toxique, carburant), vieille porte de tole en travers,
        // caisse de munitions sous le televiseur
        BunkerKit.pallet(k, -0.3f, 0, 0.8f, 16.3f, 1.1f, 16.0f, 7, Recup.PALLET);
        SlotRig.drum(k, 3.3f, 1.1f, 7.6f, Recup.DRUM_TOXIC, 90, seg(14));
        SlotRig.drum(k, 12.7f, 1.1f, 7.6f, Recup.DRUM_OLIVE, 75, seg(14));
        b.bevelBox(-0.2f, 10.4f, 2.6f, 16.2f, 11.0f, 14.6f, 0.08f, RUST);
        SlotRig.crate(k, 2.4f, 11.0f, 4.2f, 13.6f, SlotRig.TV_Y0, 13.8f, Recup.AMMO);
        // affiche "biohazard" scotchee sur la caisse, sous le pupitre
        b.face(PANEL.sub(0.12f, 0.02f, 0.88f, 0.7f), nz, p(6.8f, 11.2f, 13.87f), p(9.2f, 11.2f, 13.87f), p(9.2f, 13.4f, 13.87f), p(6.8f, 13.4f, 13.87f));
        // ---- vieux televiseur (plastique kaki crasseux, adhesif en croix), pupitre : caisse a munitions kaki
        k.color(0.7f, 0.74f, 0.6f);
        SlotRig.tv(k, Recup.PLASTIC, k, true);
        k.color(1);
        SlotRig.embrasure(k, Recup.DARK, WY0, SlotRig.PY1);
        SlotRig.deck(k, Recup.OLIVE, Recup.DARK);
        // ---- batterie devant le fut de gauche, cables jusqu'au pupitre (devant la tole)
        SlotRig.battery(k, 3.4f, 1.1f, 12.7f, 8);
        SlotRig.cable(k, new float[][]{p(2.2f, 4.2f, 12.2f), p(1.5f, 7.5f, 15.0f), p(1.9f, 11.2f, 15.1f), p(2.8f, 13.7f, 15.3f)}, true);
        SlotRig.cable(k, new float[][]{p(4.6f, 4.2f, 12.4f), p(5.2f, 8.0f, 15.2f), p(4.6f, 11.4f, 15.0f), p(4.4f, 13.7f, 15.4f)}, false);
        // ---- sur le televiseur : rouleau de barbeles, antenne tordue, potence rouillee et sa baladeuse (le titre
        // ROTTEN est affiche par l'ecran lui-meme)
        float ty = SlotRig.TV_Y1;
        float[][] wire = new float[30][];
        for (int i = 0; i < 30; i++) {
            double a = i * 1.9;
            wire[i] = p(0.8f + 14.4f * i / 29f, ty + 0.55f + 0.42f * (float) Math.sin(a), 12.5f + 0.6f * (float) Math.cos(a));
        }
        b.tube(wire, fill(30, 0.07f), 4, WIRE, false, false, 0, false);
        for (int i = 0; i < 7; i++) {
            b.push().translate(1.3f + i * 2.25f, ty + 0.55f, 12.5f).rotateY(i * 50).rotateZ(35);
            b.box6(-0.5f, -0.04f, -0.04f, 0.5f, 0.04f, 0.04f, WIRE, WIRE, WIRE, WIRE, WIRE, WIRE);
            b.pop();
        }
        SlotRig.antenna(k, 4.6f, 10.5f);
        float armY = BULB[1] + 1.9f;
        b.bevelBox(BULB[0] - 0.16f, ty - 0.2f, 9.9f, BULB[0] + 0.16f, armY + 0.16f, 10.3f, 0.04f, RUST);
        b.bevelBox(BULB[0] - 0.14f, armY - 0.14f, 9.9f, BULB[0] + 0.14f, armY + 0.14f, BULB[2] + 0.12f, 0.04f, RUST);
        b.bevelBox(BULB[0] - 0.035f, BULB[1] + 0.55f, BULB[2] - 0.035f, BULB[0] + 0.035f, armY - 0.1f, BULB[2] + 0.035f, 0.01f, WIRE);
        BunkerKit.cageLamp(b, BULB[0], BULB[1], BULB[2], RUST);
        // ---- guirlande d'ampoules vertes sur le haut du televiseur
        MeshBuilder bulbs = new MeshBuilder();
        b.color(0.35f, 0.6f, 0.2f);
        SlotRig.garland(k, b, bulbs, LED, p(0.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), p(15.5f, SlotRig.TV_Y1 + 0.4f, SlotRig.TV_FRONT + 0.3f), 0.9f, 8);
        b.color(1);
        garland = bulbs.build();
        // ---- sac de sable au pied, tabouret : seau rouille retourne, coussin de siege de voiture
        BunkerKit.sandbag(k, 14.4f, 1.1f, 14.0f, 3.4f, 1.5f, 2.0f, 20, 3, Recup.BURLAP);
        k.push().translate(8, 0, 24);
        k.lathe(new float[][]{{2.65f, 0}, {2.35f, 7.3f}, {0.05f, 7.35f}}, seg(12), 0, 360, Recup.RUST, 1, 1, 0, 0, 0, false);
        k.torus(2.62f, 0.12f, seg(12), 3, 0, 360, Recup.RUST);
        k.cylinder(2.5f, 7.3f, 8.6f, seg(12), Recup.VINYL, null, Recup.VINYL);
        k.pop();
        Occlusion.bake(b, 0.35f, 3f, 0.45f, 0f);
        Occlusion.bake(k, 0.35f, 3f, 0.45f, 0f);
        body = b.build();
        kit = k.build();

        // voyant de marche du televiseur (calque lumineux de la carrosserie figee)
        MeshBuilder lb = new MeshBuilder();
        SlotRig.led(lb, LED);
        bodyLights = lb.build();
        // titre ROTTEN affiche en haut de l'ecran (calque lumineux)
        MeshBuilder tg = new MeshBuilder();
        SlotRig.title(tg, TOPPER);
        topperGlow = tg.build();
        // fond de l'ecran (jeu et titre), bandeaux haut / bas
        MeshBuilder sb = new MeshBuilder();
        sb.face(SCREEN, nz, p(WX0, WY0, SZ), p(WX1, WY0, SZ), p(WX1, SlotRig.PY1, SZ), p(WX0, SlotRig.PY1, SZ));
        screenBg = sb.build();
        MeshBuilder br = new MeshBuilder();
        br.face(BAR, nz, p(WX0 + 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, WY0 + 0.1f, SZ + 0.02f), p(WX1 - 0.1f, RY0 - 0.12f, SZ + 0.02f),
                p(WX0 + 0.1f, RY0 - 0.12f, SZ + 0.02f));
        br.face(BAR, nz, p(WX0 + 0.1f, RY1 + 0.12f, SZ + 0.02f), p(WX1 - 0.1f, RY1 + 0.12f, SZ + 0.02f), p(WX1 - 0.1f, WY1 - 0.1f, SZ + 0.02f),
                p(WX0 + 0.1f, WY1 - 0.1f, SZ + 0.02f));
        bars = br.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(WX0, WY0, FZ - 0.05f), p(WX1, WY0, FZ - 0.05f), p(WX1, SlotRig.PY1, FZ - 0.05f), p(WX0, SlotRig.PY1, FZ - 0.05f));
        glass = gl.build();
        // boutons du pupitre
        spinBtn = button(SPIN_BTN, 1.2f, BTN_RED);
        bonusBtn = button(BONUS_BTN, 1.0f, BTN_YEL);
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(BULB[0], BULB[1], BULB[2]);
        bu.sphere(0.42f, 8, 6, LED);
        bu.pop();
        bulb = bu.build();
        MeshBuilder bg = new MeshBuilder();
        bg.push().translate(BULB[0], BULB[1], BULB[2]);
        bg.sphere(0.7f, 8, 6, LED);
        bg.pop();
        bulbGlow = bg.build();
    }

    private static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    /** Gros bouton rond sur le pupitre incline. */
    private Mesh button(float[] at, float r, Mat face) {
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, 15.5f, 14.8f).rotateX(10).translate(at[0] - 8, 1.4f, at[2] - 14.8f);
        b.cylinder(r + 0.25f, 0, 0.2f, seg(16), STEEL, null, STEEL);
        b.cylinder(r, 0.2f, 0.62f, seg(16), face, null, face);
        b.pop();
        return b.build();
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
    protected void animate(Scene s, RottenView v, Pose p, DrawList out) {
        float[] at = s.item ? null : v.at(v.since);
        boolean playing = at != null && v.since < v.totalTicks() + 40;
        // ---- enseigne : gresille de temps en temps (ambiance), clignote pendant les bonus
        float flick = s.item ? 1 : Ease.hash((int) (s.time / 2) + (int) (s.seed * 997)) > 0.965f ? 0.25f : 1f;
        out.solid(p, topperGlow, 0.8f * flick);
        out.additive(p, garland, TOXIC, (0.55f + 0.35f * s.pulse(0.07f)) * flick);
        float bulbK = s.item ? 1 : Ease.hash((int) (s.time / 3) + 7) > 0.9f ? 0.1f : 0.85f + 0.15f * (float) Math.sin(s.time * 1.7f);
        out.solid(p, bulb, bulbK);
        out.additive(p, bulbGlow, 0xFFD89A, 0.4f * bulbK);
        if (!s.item) out.billboard(p, Sprite.HALO, BULB[0], BULB[1], BULB[2], 3.5f, 0, 0xFFC880, 0.18f * bulbK);
        // ---- boutons : LANCER respire, s'enfonce au lancement
        float press = playing && at[0] == 0 && at[1] < 6 ? Ease.bell(at[1], 0, 6) * 0.25f : 0;
        p.push().translate(0, -press, 0);
        out.solid(p, spinBtn, playing ? 0.3f : 0.55f + 0.35f * s.pulse(0.12f));
        p.pop();
        out.solid(p, bonusBtn, 0.35f + 0.25f * s.pulse(0.09f));
        // ---- ecran ; intro (le joueur vient de s'installer) : le zombie surgit, puis la grille tombe colonne par colonne
        out.solid(p, screenBg, 0.35f);
        introT = Intro.time(v.intro, playing, s.item);
        if (Intro.scene(introT)) {
            intro(s, p, out, introT);
        } else {
            out.solid(p, bars, 0.4f);
            screen(s, v, p, out, at, playing);
            Intro.title(p, out, introT, "ROTTEN", INTRO_SUB, 8, (RY0 + RY1) / 2, SZ + 0.32f, 11f, TOXIC, 0xFFA040);
        }
        out.translucent(p, glass, 0xFFFFFF, 0.3f);
    }

    static final String INTRO_SUB = "3 SAVANT FOU = TOURS GRATUITS";

    /** Temps de l'intro pour l'image en cours (-1 : pas d'intro) ; lu par la grille (arrivee des colonnes). */
    private float introT = -1;

    /**
     * Intro : le tube s'allume en gresillant (neige, barre verte qui descend), un zombie surgit du bas de l'ecran, rugit
     * et plaque ses mains sur la vitre ; le titre arrive, puis la grille (Intro.REVEAL).
     */
    private void intro(Scene s, Pose p, DrawList out, float t) {
        float z = SZ + 0.04f, cy = (WY0 + WY1) / 2;
        MeshBuilder snow = new MeshBuilder(), fx = new MeshBuilder();
        // neige du tube qui s'eteint peu a peu
        float noise = 1 - Ease.range(t, 12, 26);
        if (noise > 0.01f) {
            int seed = (int) (t * 2);
            for (int k = 0; k < 46; k++) {
                float x = WX0 + Ease.hash(seed * 131 + k * 7) * (WX1 - WX0), y = WY0 + Ease.hash(seed * 71 + k * 13) * (WY1 - WY0);
                float w = 0.3f + Ease.hash(seed + k * 3) * 1.6f, h = 0.12f + Ease.hash(seed * 5 + k) * 0.18f;
                Intro.quad(snow, LED, x, y, x + w, y + h, WX0, WY0, WX1, WY1, z, (0.15f + 0.35f * Ease.hash(seed * 17 + k)) * noise, false);
            }
            out.additive(p, snow.buildDynamic(), 0xC8D8C8, 1f);
        }
        // barre de balayage verte
        float bar = WY1 - ((t * 0.55f) % 1f) * (WY1 - WY0) * 1.2f;
        Intro.quad(fx, FLASH, WX0 - 4, bar - 0.5f, WX1 + 4, bar + 0.5f, WX0, WY0, WX1, WY1, z + 0.01f, 0.25f * Ease.range(t, 6, 10), false);
        // le zombie surgit, rugit, ses mains claquent sur la vitre, puis il redescend
        float zt = t - 14;
        if (zt >= 0) {
            boolean roar = t >= 30 && t < 52 && ((int) (t / 5)) % 4 != 3;
            float rise = Ease.outBack(Ease.clamp01(zt / 14f), 1.2f), sink = Ease.inCubic(Ease.range(t, 50, 60));
            float sz = 8.4f * (1 + (roar ? 0.04f : 0) + 0.02f * (float) Math.sin(t * 0.5f));
            float zx = 8 + (roar ? (Ease.hash((int) (t * 2) + 5) - 0.5f) * 0.3f : 0), zy = WY0 - sz / 2 + (sz * 0.95f + 1.2f) * rise - sz * sink;
            MeshBuilder zb = new MeshBuilder();
            Intro.quad(zb, roar ? ZOMBIE_B : ZOMBIE_A, zx - sz / 2, zy - sz / 2, zx + sz / 2, zy + sz / 2, WX0, WY0, WX1, WY1, z + 0.05f, 1f, false);
            out.solid(p, zb.buildDynamic(), roar ? 0.8f : 0.6f);
            if (roar) out.billboard(p, Sprite.HALO, zx, zy + sz * 0.12f, z + 0.1f, sz * 0.9f, 0, TOXIC, 0.18f);
        }
        // mains plaquees sur la vitre (devant tout), qui glissent hors de l'ecran quand le titre arrive
        MeshBuilder hands = new MeshBuilder();
        for (int k = 0; k < 2; k++) {
            float hitAt = 32 + k * 3;
            if (t < hitAt || t >= Intro.TITLE + 10) continue;
            float slam = Ease.clamp01((t - hitAt) / 2.5f), hs = 3.2f * (1.6f - 0.6f * Ease.outCubic(slam));
            float slide = Ease.inQuad(Ease.range(t, Intro.TITLE - 2, Intro.TITLE + 10)) * 10;
            float hx = k == 0 ? WX0 + 2.6f : WX1 - 2.6f, hy = cy + 1.2f - k * 0.8f - slide;
            Intro.quad(hands, HAND, hx - hs / 2, hy - hs / 2, hx + hs / 2, hy + hs / 2, WX0, WY0, WX1, WY1, FZ + 0.02f, 0.4f + 0.6f * slam, k == 1);
            if (slam < 1) Intro.quad(fx, FLASH, hx - 2.5f, hy - 2.5f, hx + 2.5f, hy + 2.5f, WX0, WY0, WX1, WY1, z + 0.2f, 1 - slam, false);
        }
        if (!hands.isEmpty()) out.solid(p, hands.buildDynamic(), 0.3f);
        if (!fx.isEmpty()) out.additive(p, fx.buildDynamic(), 0xB8FF8A, 0.9f);
        Intro.crtOn(p, out, DARK, FLASH, t, WX0, WY0, WX1, WY1, SZ + 0.36f, TOXIC);
        Intro.title(p, out, t, "ROTTEN", INTRO_SUB, 8, (RY0 + RY1) / 2, SZ + 0.32f, 11f, TOXIC, 0xFFA040);
    }

    /** Symbole affiche a une case avant le tour i (grille finale du tour precedent). */
    private static int prevSymbol(RottenView v, int i, int c) {
        if (i <= 0 || i > v.seq.spins.size()) return IDLE[c];
        return v.seq.spins.get(i - 1).finalSymbol(c);
    }

    /** Symbole de remplissage pendant la rotation (deterministe). */
    private static int filler(int spin, int reel, int k) {
        float h = Ease.hash(spin * 7919 + reel * 131 + k * 17);
        return h < 0.55f ? (int) (h / 0.55f * 5) : h < 0.93f ? 5 + (int) ((h - 0.55f) / 0.38f * 5) : Rotten.WILD;
    }

    private void screen(Scene s, RottenView v, Pose p, DrawList out, float[] at, boolean playing) {
        MeshBuilder mb = new MeshBuilder();
        MeshBuilder flash = new MeshBuilder();
        MeshBuilder winGlow = new MeshBuilder();
        int i = at == null ? -1 : (int) at[0];
        float lt = at == null ? 0 : at[1];
        Rotten.Spin spin = i >= 0 ? v.seq.spins.get(i) : null;
        boolean finished = at != null && v.since >= v.totalTicks();
        int morphAt = spin != null ? Rotten.morphAt(spin) : 0;
        int landed = spin != null ? Rotten.landed(spin) : 0;
        long winMask = 0;
        if (spin != null && spin.winMilli > 0) {
            long[] m = new long[1];
            Rotten.evaluate(v.rules, spin.finalGrid(), m);
            winMask = m[0];
        }
        boolean showWin = spin != null && lt >= landed && spin.winMilli > 0 && (!finished || v.since < v.totalTicks() + 60);
        float pulse = 0.55f + 0.45f * (float) Math.sin(s.time * 0.6f);
        float dim = BigWin.dim(zombieAt(spin, lt, landed), Rotten.BIG_WIN_TICKS);
        // suspense : rouleau qui tourne au ralenti parce qu'il ne manque qu'un symbole de bonus
        float[] tz = teasing(spin, lt);
        int hot = tz == null ? -1 : (int) tz[0];
        float tension = tz == null ? 0 : Suspense.tension(tz[1], SLOW);
        int want = hot < 0 ? -1 : Rotten.teaseSymbol(spin, hot);
        MeshBuilder hit = new MeshBuilder();
        int hitColor = TOXIC;
        for (int reel = 0; reel < Rotten.REELS; reel++) {
            float x0 = RX0 + reel * CW + 0.06f, x1 = x0 + CW - 0.12f;
            if (spin == null) {
                // fin de l'intro : les colonnes tombent l'une apres l'autre
                float drop = Intro.drop(introT, reel, Rotten.ROWS), land = Intro.landed(introT, reel);
                for (int row = 0; row < Rotten.ROWS; row++) {
                    int sym = IDLE[Rotten.cell(reel, row)];
                    float k = idleShimmer(s, reel, row) * (1 + 0.5f * land);
                    cell(mb, x0, x1, row - drop, 0, 1, symbol(sym, false), k);
                }
                if (land > 0) quad(flash, x0 - 0.3f, x1 + 0.3f, Rotten.ROWS - 1, FLASH, 0.7f * land);
                continue;
            }
            int stop = Rotten.stopTick(spin, reel);
            int hoped = Rotten.teaseSymbol(spin, reel);
            if (lt < stop) {
                // rotation : bande virtuelle (precedent en bas, remplissage, resultat en haut) qui defile vers le bas ;
                // un rouleau retarde par le suspense des precedents continue a filer a pleine vitesse
                float slowAt = hoped >= 0 ? stop - SLOW : stop;
                int fast = Rotten.ROWS + Suspense.fastCells(slowAt, Rotten.ROWS);
                int d = fast + (hoped >= 0 ? (int) SLOW_CELLS : 0);
                float off, speed;
                if (hoped >= 0) {
                    off = Tease.travel(lt, slowAt, stop, fast, SLOW_CELLS);
                    speed = Tease.travel(lt + 0.5f, slowAt, stop, fast, SLOW_CELLS) - Tease.travel(lt - 0.5f, slowAt, stop, fast, SLOW_CELLS);
                } else {
                    float span = Math.max(1f, stop - 1);
                    off = d * SlotView.travel(Math.max(0, lt - 1) / span);
                    speed = d * (SlotView.travel(Math.max(0, lt - 0.5f) / span) - SlotView.travel(Math.max(0, lt - 1.5f) / span));
                }
                boolean blur = speed > 0.35f;
                // appat : le symbole de bonus attendu glisse dans la fenetre pendant le ralenti... et sort par le bas
                int bait = -1;
                if (hoped >= 0) {
                    boolean has = false;
                    int h = reel * 7 + i * 13;
                    for (int row = 0; row < Rotten.ROWS; row++) {
                        has |= spin.grid[Rotten.cell(reel, row)] == hoped;
                        h = h * 31 + spin.grid[Rotten.cell(reel, row)];
                    }
                    if (!has && Ease.hash(h) < 0.55f) bait = hoped;
                }
                float base = d - off;
                int first = (int) Math.floor(base);
                for (int k = Math.max(0, first); k <= first + Rotten.ROWS; k++) {
                    float y = k - base;
                    int sym;
                    if (k < Rotten.ROWS) sym = spin.grid[Rotten.cell(reel, k)];
                    else if (k >= d) sym = prevSymbol(v, i, Rotten.cell(reel, Math.min(Rotten.ROWS - 1, k - d)));
                    else if (k == Rotten.ROWS && bait >= 0) sym = bait;
                    else sym = filler(i, reel, k);
                    cellClip(mb, x0, x1, y, symbol(sym, blur && sym <= Rotten.WILD), 1f);
                }
            } else {
                // arret avec petit rebond, puis mutation et gains
                float bounce = Ease.spring(lt - stop, 1.1f, 0.45f) * 0.12f;
                for (int row = 0; row < Rotten.ROWS; row++) {
                    int c = Rotten.cell(reel, row);
                    boolean morphed = spin.mutates(c) && lt >= morphAt + 4;
                    int sym = morphed ? spin.finalSymbol(c) : spin.grid[c];
                    float k = 1f;
                    if (lt >= landed && winMask != 0) k = (winMask & (1L << c)) != 0 ? 1f : 0.35f;
                    if (sym >= Rotten.SAVANT && lt < stop + 12) k = 1f + 0.4f * Ease.bell(lt, stop, stop + 12);
                    // suspense : les autres rouleaux s'assombrissent, les symboles attendus restent vifs
                    if (hot >= 0) k *= sym == want ? 1.15f + 0.25f * pulse : Tease.dim(tension);
                    cellClip(mb, x0, x1, row - bounce, symbol(sym, false), k * dim);
                    if (spin.mutates(c) && lt >= morphAt && lt < morphAt + 10) {
                        float fk = Ease.bell(lt, morphAt, morphAt + 10);
                        quad(flash, x0 - 0.2f, x1 + 0.2f, row, FLASH, fk);
                    }
                    if (showWin && (winMask & (1L << c)) != 0) quad(winGlow, x0, x1, row, symbol(sym, false), pulse * dim);
                    // le symbole attendu est tombe sur le rouleau en suspens : eclair
                    if (hoped >= 0 && sym == hoped && lt < stop + 12) {
                        float cx = (x0 + x1) / 2, cy = RY1 - (row + 0.5f) * CH, h = CW * 1.3f;
                        BigWin.clipQuad(hit, FLASH, cx - h, cy - h, cx + h, cy + h, -1e9f, 1e9f, SZ + 0.09f, 1 - Ease.range(lt, stop, stop + 12), false);
                        hitColor = teaseColor(hoped);
                    }
                }
            }
        }
        out.solid(p, mb.buildDynamic(), 0.55f);
        if (!flash.isEmpty()) out.additive(p, flash.buildDynamic(), PURPLE, 1f);
        if (!hit.isEmpty()) out.additive(p, hit.buildDynamic(), hitColor, 1f);
        if (!winGlow.isEmpty()) out.additive(p, winGlow.buildDynamic(), 0xFFFFFF, 0.45f);
        if (hot >= 0 && !s.item) {
            int col = teaseColor(want);
            Tease.column(s, p, out, RX0 + hot * CW, RY0, RX0 + (hot + 1) * CW, RY1, SZ + 0.1f, col, tension);
            for (int c = 0; c < hot * Rotten.ROWS; c++) {
                if (spin.grid[c] != want) continue;
                Tease.ember(s, p, out, RX0 + (c / Rotten.ROWS + 0.5f) * CW, RY1 - (c % Rotten.ROWS + 0.5f) * CH, SZ + 0.25f, CW * 0.75f, col, tension);
            }
        }
        switchBar(s, v, p, out, spin, lt, morphAt);
        infoBar(s, v, p, out, i, spin, lt, landed, finished);
        overlays(s, v, p, out, i, spin, lt, landed, finished);
    }

    // =================================================================== suspense
    /** Duree du ralenti d'un rouleau en suspens, et cases qu'il parcourt pendant ce temps. */
    static final int SLOW = Suspense.HOLD + 2;
    static final float SLOW_CELLS = Rotten.ROWS + 2;

    /** Rouleau en train de tourner au ralenti et ticks ecoules depuis le debut de son ralenti ; null si aucun. */
    static float[] teasing(Rotten.Spin spin, float lt) {
        if (spin == null) return null;
        for (int reel = 1; reel < Rotten.REELS; reel++) {
            if (!Rotten.tease(spin, reel)) continue;
            int stop = Rotten.stopTick(spin, reel);
            float t = lt - (stop - SLOW);
            if (t >= 0 && lt < stop) return new float[]{reel, t};
        }
        return null;
    }

    static int teaseColor(int symbol) {
        return symbol == Rotten.INVASION ? BLOOD : TOXIC;
    }

    /** Ticks depuis l'arrivee du zombie (gros gain du tour, ou gain maximum en fin de partie), -1 sinon. */
    static float zombieAt(Rotten.Spin spin, float lt, int landed) {
        if (spin == null) return -1;
        float t = lt - landed;
        if (Rotten.bigTier(spin.winMilli) > 0 && t >= 0 && t < Rotten.BIG_WIN_TICKS) return t;
        if ((spin.events & Rotten.EV_MAXWIN) != 0) {
            float m = lt - (Rotten.duration(spin) - Rotten.BIG_WIN_TICKS);
            if (m >= 0 && m < Rotten.BIG_WIN_TICKS) return m;
        }
        return -1;
    }

    /** Au repos, un symbole scintille de temps en temps. */
    private static float idleShimmer(Scene s, int reel, int row) {
        int slot = (int) (s.time / 40);
        int pick = (int) (Ease.hash(slot + (int) (s.seed * 50)) * Rotten.CELLS);
        if (pick != Rotten.cell(reel, row)) return 1f;
        return 1f + 0.5f * Ease.bell(s.time % 40, 0, 20);
    }

    /** Case entiere (rangee "row", decalage vertical en rangees). */
    private static void cell(MeshBuilder b, float x0, float x1, float row, float t0, float t1, Mat m, float tint) {
        cellClip(b, x0, x1, row, m, tint);
    }

    /** Case a la rangee y (fractionnaire, 0 = haut), decoupee a la fenetre des rouleaux. */
    private static void cellClip(MeshBuilder b, float x0, float x1, float y, Mat m, float tint) {
        float top = RY1 - y * CH - 0.05f, bottom = top - CH + 0.1f;
        float yt = Math.min(top, RY1), yb = Math.max(bottom, RY0);
        if (yt <= yb) return;
        float h = top - bottom;
        float vt = (top - yt) / h, vb = (top - yb) / h;
        float[][] pts = {p(x0, yb, SZ + 0.03f), p(x1, yb, SZ + 0.03f), p(x1, yt, SZ + 0.03f), p(x0, yt, SZ + 0.03f)};
        float[][] uv = {{m.u(0), m.v(vb)}, {m.u(1), m.v(vb)}, {m.u(1), m.v(vt)}, {m.u(0), m.v(vt)}};
        b.color(Math.min(1.4f, tint));
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    private static void quad(MeshBuilder b, float x0, float x1, float row, Mat m, float k) {
        float top = RY1 - row * CH, bottom = top - CH;
        float[][] pts = {p(x0, bottom, SZ + 0.06f), p(x1, bottom, SZ + 0.06f), p(x1, top, SZ + 0.06f), p(x0, top, SZ + 0.06f)};
        float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
        b.color(k);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }

    /** Bandeau du haut : les 9 symboles qui peuvent muter ; ceux qui ont mute sont allumes (et leur cible). */
    private void switchBar(Scene s, RottenView v, Pose p, DrawList out, Rotten.Spin spin, float lt, int morphAt) {
        int switched = 0, wilds = 0;
        if (spin != null) {
            switched = spin.switched;
            wilds = spin.wilds;
            // les nouvelles mutations s'allument apres l'annonce
            if ((spin.events & Rotten.EV_MUTATION) != 0 && lt >= Rotten.landed(spin) + Math.min(Rotten.winShow(spin), 34)) {
                switched |= spin.added;
            }
        }
        // suspense : l'annonce remplace les symboles mutants le temps du ralenti
        float[] tz = s.item ? null : teasing(spin, lt);
        if (tz != null) {
            int hoped = Rotten.teaseSymbol(spin, (int) tz[0]);
            boolean inv = hoped == Rotten.INVASION;
            String txt = inv ? "ENCORE UNE INVASION..." : "ENCORE UN SAVANT FOU...";
            String sub = spin.kind == Rotten.FREE ? "POUR DES TOURS GRATUITS EN PLUS" : inv ? "POUR L'INVASION TOTALE" : "POUR LES TOURS GRATUITS";
            float a = Ease.clamp01(tz[1] / 4f), ym = (RY1 + WY1) / 2;
            out.text(p, txt, 8, ym + 0.32f, SZ + 0.08f, fit(txt, 0.82f, 11f, true), teaseColor(hoped), a * (0.7f + 0.3f * s.pulse(0.5f)), DrawList.T_CENTER | DrawList.T_BOLD);
            out.text(p, sub, 8, ym - 0.58f, SZ + 0.08f, fit(sub, 0.42f, 11f, false), WHITE, a * 0.9f, DrawList.T_CENTER);
            return;
        }
        MeshBuilder b = new MeshBuilder();
        float size = 1.12f, gap = (RX1 - RX0 - 9 * size) / 8f, y0 = RY1 + 0.35f;
        for (int k = 0; k < Rotten.SWITCHABLE; k++) {
            boolean on = (switched & (1 << k)) != 0;
            float x = RX0 + k * (size + gap);
            b.color(on ? 1.25f : 0.3f);
            b.face(symbol(k, false), v(0, 0, 1), p(x, y0, SZ + 0.04f), p(x + size, y0, SZ + 0.04f), p(x + size, y0 + size, SZ + 0.04f), p(x, y0 + size, SZ + 0.04f));
            if (on) {
                int target = (wilds & (1 << k)) != 0 ? Rotten.WILD : Rotten.CLOWN;
                float tx = x + size * 0.55f, ts = size * 0.55f;
                b.color(1.2f);
                b.face(symbol(target, false), v(0, 0, 1), p(tx, y0 - 0.12f, SZ + 0.06f), p(tx + ts, y0 - 0.12f, SZ + 0.06f), p(tx + ts, y0 - 0.12f + ts, SZ + 0.06f),
                        p(tx, y0 - 0.12f + ts, SZ + 0.06f));
            }
        }
        b.color(1);
        out.solid(p, b.buildDynamic(), 0.6f);
    }

    /** Bandeau du bas : mise, gain cumule, tours gratuits / relances. */
    private void infoBar(Scene s, RottenView v, Pose p, DrawList out, int i, Rotten.Spin spin, float lt, int landed, boolean finished) {
        if (s.item) return;
        float y = (WY0 + RY0) / 2 - 0.05f, z = SZ + 0.08f;
        if (spin == null || (finished && v.since > v.totalTicks() + 200)) {
            boolean blink = ((int) (s.time / 20)) % 2 == 0;
            String idle = blink ? "INSÉREZ VOS JETONS" : "3 SAVANT FOU = TOURS GRATUITS";
            out.text(p, idle, 8, y, z, fit(idle, 0.6f, 11f, false), blink ? TOXIC : GOLD, 0.95f, DrawList.T_CENTER);
            return;
        }
        // suspense : la camera remonte vers l'annonce du haut, le bandeau du bas passe sous les boutons (rien a y lire)
        if (teasing(spin, lt) != null) return;
        String mise = "MISE " + fmt(v.bet);
        out.text(p, mise, RX0 + 0.1f, y, z, fit(mise, 0.5f, 3.2f, false), WHITE, 0.9f, 0);
        long milli = v.winSoFar(i, lt >= landed);
        long won = (long) Math.floor(v.bet * (milli / 1000.0));
        String g = "GAIN " + fmt(won);
        out.text(p, g, 8.6f, y, z, fit(g, 0.62f, 4.4f, false), won > 0 ? GOLD : WHITE, 1f, DrawList.T_CENTER);
        String right = null;
        int col = WHITE;
        if (spin.kind == Rotten.RESPIN) {
            right = "RELANCES " + spin.respinsLeft;
            col = PURPLE;
        } else if (spin.mode != Rotten.MODE_BASE || spin.kind == Rotten.FREE) {
            right = (spin.mode == Rotten.MODE_INVASION ? "INVASION " : "SAVANT ") + (spin.freeTotal - spin.freeLeft) + "/" + spin.freeTotal;
            col = spin.mode == Rotten.MODE_INVASION ? BLOOD : TOXIC;
        }
        if (right != null) out.text(p, right, RX1 - 0.1f, y, z, fit(right, 0.5f, 3.2f, false), col, 1f, DrawList.T_RIGHT);
    }

    /** Annonces plein ecran : gain, MUTATION, bonus, fin du bonus. */
    private void overlays(Scene s, RottenView v, Pose p, DrawList out, int i, Rotten.Spin spin, float lt, int landed, boolean finished) {
        if (spin == null || s.item) return;
        float cx = 8, cy = (RY0 + RY1) / 2, z = SZ + 0.2f;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        float t = lt - landed;
        int winT = Rotten.winShow(spin);
        int tier = Rotten.bigTier(spin.winMilli);
        if (spin.winMilli > 0 && t >= 0 && t < winT) {
            long amount = (long) Math.floor(v.bet * spin.winMilli / 1000.0);
            if (tier > 0) {
                zombie(p, out, t);
                BigWin.text(p, out, t, winT, tier, amount, cx, RY1 - 1.1f, RY0 + 1.3f, z + 0.1f, 11f, tier >= 3 ? PURPLE : tier == 2 ? BLOOD : TOXIC, GOLD);
            } else {
                float pop = Ease.outBack(Math.min(1f, t / 5f), 2.2f);
                String txt = "+" + fmt(amount);
                out.text(p, txt, cx, cy, z, fit(txt, 1.8f * pop, 10.5f, true), GOLD, 1f, flags, 0x2A0A00);
            }
        }
        float e = t - winT;
        int mt = Rotten.MUTATION_T, bt = Rotten.BONUS_T, rt = Rotten.RETRIGGER_T;
        if ((spin.events & Rotten.EV_MUTATION) != 0 && e >= 0 && e < mt) {
            float a = Math.min(1f, e / 4f) * (1 - Ease.range(e, mt - 5, mt));
            out.text(p, "MUTATION !", cx, cy + 1.3f, z, fit("MUTATION !", 2f, 10.5f, true) * Ease.outBack(Math.min(1f, e / 6f), 2.5f), PURPLE, a, flags, 0x10001A);
            int n = Integer.bitCount(spin.added);
            String sub = spin.kind == Rotten.RESPIN ? "+ RELANCES" : spin.respinsLeft + (spin.respinsLeft > 1 ? " RELANCES" : " RELANCE");
            String line = sub + (n > 0 ? "  ·  " + n + " SYMBOLE" + (n > 1 ? "S" : "") : "");
            out.text(p, line, cx, cy - 1.3f, z, fit(line, 0.85f, 10.5f, true), WHITE, a, flags, 0x000000);
            return;
        }
        if ((spin.events & (Rotten.EV_SAVANT | Rotten.EV_INVASION)) != 0) {
            float b = e - ((spin.events & Rotten.EV_MUTATION) != 0 ? mt : 0);
            if (b >= 0 && b < bt) {
                boolean inv = (spin.events & Rotten.EV_INVASION) != 0;
                float a = Math.min(1f, b / 5f) * (1 - Ease.range(b, bt - 6, bt));
                out.text(p, inv ? "INVASION TOTALE" : "SAVANT FOU", cx, cy + 1.6f, z, fit(inv ? "INVASION TOTALE" : "SAVANT FOU", 2.1f, 11f, true)
                        * Ease.outBack(Math.min(1f, b / 7f), 2f), inv ? BLOOD : TOXIC, a, flags, 0x000000);
                int free = spin.kind == Rotten.BASE ? spin.freeTotal : spin.freeLeft;
                String ft = free + " TOURS GRATUITS";
                out.text(p, ft, cx, cy - 0.9f, z, fit(ft, 1.1f, 10.5f, true), GOLD, a, flags, 0x000000);
                if (inv) out.text(p, "LES MUTATIONS RESTENT !", cx, cy - 2.5f, z, fit("LES MUTATIONS RESTENT !", 0.7f, 10.5f, true), WHITE, a, flags, 0x000000);
                return;
            }
        }
        if ((spin.events & Rotten.EV_RETRIGGER) != 0 && e >= 0 && e < rt) {
            float a = Math.min(1f, e / 4f) * (1 - Ease.range(e, rt - 5, rt));
            out.text(p, "TOURS GRATUITS EN PLUS !", cx, cy, z, fit("TOURS GRATUITS EN PLUS !", 1.2f, 10.5f, true), GOLD, a, flags, 0x000000);
            return;
        }
        boolean max = (spin.events & Rotten.EV_MAXWIN) != 0;
        int end = Rotten.duration(spin) - (max ? Rotten.BIG_WIN_TICKS : 0);
        if (max && lt >= end) {
            long total = (long) Math.floor(v.bet * v.seq.totalMilli / 1000.0);
            float m = lt - end;
            if (m < Rotten.BIG_WIN_TICKS) {
                zombie(p, out, m);
                BigWin.text(p, out, m, Rotten.BIG_WIN_TICKS, 4, total, cx, RY1 - 1.1f, RY0 + 1.3f, z + 0.1f, 11f, GOLD, GOLD);
            } else {
                BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * Ease.clamp01((m - Rotten.BIG_WIN_TICKS) / 5f));
                out.text(p, "GAIN MAXIMUM !", cx, cy + 1.2f, z, fit("GAIN MAXIMUM !", 1.8f, 10.5f, true), GOLD, 1f, flags, 0x2A0A00);
                out.text(p, fmt(total), cx, cy - 1.0f, z, fit(fmt(total), 2.2f, 10.5f, true), GOLD, 1f, flags, 0x2A0A00);
            }
            return;
        }
        if ((spin.events & Rotten.EV_BONUS_END) != 0 && lt >= end - Rotten.END_T) {
            float k = lt - (end - Rotten.END_T);
            float a = Math.min(1f, k / 5f);
            long total = (long) Math.floor(v.bet * v.winSoFar(v.seq.spins.size(), false) / 1000.0);
            // le fond s'assombrit, le total ressort (les boutons du joueur reviennent, voir summary)
            BigWin.shade(p, out, DARK, WX0, WY0, WX1, WY1, SZ + 0.15f, BigWin.SHADE * a);
            out.text(p, "GAIN TOTAL", cx, cy + 1.3f, z, 1.2f, WHITE, a, flags, 0x000000);
            String tt = fmt(total);
            out.text(p, tt, cx, cy - 1.0f, z, fit(tt, 2.4f * Ease.outBack(Math.min(1f, k / 7f), 2f), 10.5f, true), GOLD, a, flags, 0x2A0A00);
        }
    }

    /** Le zombie des gros gains : il surgit de sous les rouleaux, rugit, et plaque ses mains sur la vitre. */
    private void zombie(Pose p, DrawList out, float t) {
        float cy = (RY0 + RY1) / 2 - 0.2f;
        BigWin.mascot(p, out, t, Rotten.BIG_WIN_TICKS, ZOMBIE_A, ZOMBIE_B, HAND, 8, cy, 7.6f, RY0 - 0.1f, WY1 - 0.1f, SZ + 0.12f, FZ + 0.02f, 2.7f,
                new float[]{WX0 + 1.2f, WY0 + 2.8f, WX1 - 1.2f, WY0 + 3.1f});
    }

    // =================================================================== camera
    @Override
    public void camera(RottenView v, Scene s, int mode, Shot out) {
        // suspense : on se rapproche du rouleau qui tourne au ralenti ; l'image remonte pour garder l'annonce du haut en
        // entier (la barre du bas passe sous les boutons, l'ecran de jeu n'affiche plus sa ligne d'etat)
        float[] at = v.at(v.since);
        float[] tz = at == null || mode == CAM_MENU ? null : teasing(v.seq.spins.get((int) at[0]), at[1]);
        if (tz != null) {
            float k = Ease.inOutSine(Ease.clamp01(tz[1] / 10f));
            float x = Ease.lerp(8, (RX0 + ((int) tz[0] + 0.5f) * CW) * 0.5f + 4, k);
            out.set(x, 24.6f + 1.2f * k, 36.5f - 4.6f * k, x, 23.2f + 1.6f * k, SZ, 44 - 3 * k);
            return;
        }
        // l'ecran occupe le haut de l'image : le bas reste libre pour les boutons
        if (mode == CAM_MENU) out.set(8, 29, 46, 8, 26, 13, 52);
        else out.set(8, 24.6f, 36.5f, 8, 23.2f, SZ, 44);
    }

    @Override
    public boolean zoomable() {
        return true;
    }

    /** Gain total de fin de bonus (ou gain maximum) a l'ecran : les boutons du joueur reviennent. */
    @Override
    public float summary(RottenView v, Scene s) {
        float[] at = v.at(v.since);
        if (at == null || v.since > v.totalTicks() + 200 || (int) at[0] != v.seq.spins.size() - 1) return 0;
        Rotten.Spin spin = v.seq.spins.get((int) at[0]);
        boolean max = (spin.events & Rotten.EV_MAXWIN) != 0;
        if (!max && (spin.events & Rotten.EV_BONUS_END) == 0) return 0;
        int end = Rotten.duration(spin) - (max ? Rotten.BIG_WIN_TICKS : 0);
        float k = max ? at[1] - end - Rotten.BIG_WIN_TICKS : at[1] - (end - Rotten.END_T);
        return Ease.clamp01(k / 5f);
    }

    /** Image de jeu de l'ecran en entier (bandeaux du haut et du bas compris), cadree par le zoom du joueur et pendant l'intro. */
    @Override
    public float[] screenRect(RottenView v, Scene s) {
        return new float[]{WX0, WY0, WX1, WY1, SZ};
    }

    @Override
    public float screenFocus(RottenView v, Scene s) {
        return Intro.focus(v.intro, v.at(v.since) != null && v.since < v.totalTicks() + 40);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, RottenView v, FxSink fx, Random r) {
        if (r.nextInt(70) == 0) fx.particle(FxSink.FLY, 8 + rnd(r, 6), 28 + rnd(r, 10), 16 + rnd(r, 3), 0, 0, 0, 0x202020, 0.35f, 60);
        if (r.nextInt(200) == 0) fx.particle(FxSink.SPARK, BULB[0], BULB[1], BULB[2] + 0.4f, rnd(r, 0.05f), 0.05f, 0.05f, 0xFFD89A, 0.5f, 10);
        float[] at = v.at(v.since);
        // intro : neige du tube, le zombie grogne, rugit et frappe la vitre
        float it = Intro.time(v.intro, at != null && v.since < v.totalTicks() + 40, s.item);
        if (it >= 0) {
            float sy = (RY0 + RY1) / 2;
            Intro.sounds(fx, it, Rotten.REELS, RX0, CW, sy, 15);
            if (at(it, 16)) fx.sound(Snd.ZOMBIE, 8, 20, 15, 0.6f, 0.65f);
            if (at(it, 30)) fx.sound(Snd.ZOMBIE, 8, sy, 15, 1f, 0.8f);
            for (int k = 0; k < 2; k++) {
                if (!at(it, 32 + k * 3)) continue;
                float hx = k == 0 ? WX0 + 2.6f : WX1 - 2.6f;
                fx.sound(Snd.SMASH, hx, sy, FZ, 0.8f, 0.65f + k * 0.1f);
                for (int q = 0; q < 6; q++) fx.particle(FxSink.SHARD, hx, sy + 1, FZ + 0.3f, rnd(r, 0.08f), 0.05f + r.nextFloat() * 0.05f, 0.06f, 0xC8D8C8, 0.25f, 16);
            }
        }
        if (at == null || v.since > v.totalTicks() + 10) return;
        int i = (int) at[0];
        float lt = at[1];
        Rotten.Spin spin = v.seq.spins.get(i);
        float sy = (RY0 + RY1) / 2;
        if (at(lt, 0)) fx.sound(Snd.REEL_START, 8, sy, 15, 0.7f, spin.kind == Rotten.RESPIN ? 1.5f : 1.2f);
        int last = Rotten.stopTick(spin, Rotten.REELS - 1);
        if (lt > 1 && lt < last && ((int) lt) % 2 == 0) fx.sound(Snd.REEL_TICK, 8, sy, 15, 0.18f, 1.8f + r.nextFloat() * 0.2f);
        for (int reel = 0; reel < Rotten.REELS; reel++) {
            int stop = Rotten.stopTick(spin, reel);
            int hoped = Rotten.teaseSymbol(spin, reel);
            // suspense : battements de coeur pendant le ralenti
            if (hoped >= 0) Tease.sounds(fx, lt - (stop - SLOW), SLOW, RX0 + CW * (reel + 0.5f), sy, 15);
            if (!at(lt, stop)) continue;
            fx.sound(Snd.REEL_STOP, RX0 + CW * (reel + 0.5f), sy, 15, 0.5f, 1.3f + reel * 0.05f);
            boolean hit = false;
            for (int row = 0; row < Rotten.ROWS; row++) {
                int sym = spin.grid[Rotten.cell(reel, row)];
                float x = RX0 + CW * (reel + 0.5f), y = RY1 - CH * (row + 0.5f);
                if (sym == hoped) {
                    hit = true;
                    burst(fx, r, x, y, 14.2f, teaseColor(hoped), 0xFFFFFF, 26, 0.4f, 0.4f);
                }
                if (sym == Rotten.SAVANT || sym == Rotten.INVASION) {
                    fx.sound(Snd.SCATTER, x, y, 15, 1f, 0.8f + reel * 0.08f);
                    burst(fx, r, x, y, 14.2f, sym == Rotten.SAVANT ? TOXIC : BLOOD, 0xFFFFFF, 8, 0.25f, 0.35f);
                }
                if (sym == Rotten.MUTATION) {
                    fx.sound(Snd.SWITCH, x, y, 15, 1f, 1.1f);
                    burst(fx, r, x, y, 14.2f, PURPLE, 0xFFFFFF, 10, 0.3f, 0.35f);
                }
            }
            // fin du suspense : le symbole est tombe (cloche) ou pas (retombee)
            if (hoped >= 0) fx.sound(hit ? Snd.BELL : Snd.MISS, RX0 + CW * (reel + 0.5f), sy, 15, hit ? 0.9f : 0.7f, hit ? 1.4f : 1f);
        }
        int morphAt = Rotten.morphAt(spin);
        boolean morph = false;
        for (int c = 0; c < Rotten.CELLS && !morph; c++) morph = spin.mutates(c);
        if (morph && at(lt, morphAt)) fx.sound(Snd.SPARKLE, 8, sy, 15, 0.8f, 1.2f);
        int landed = Rotten.landed(spin);
        if (spin.winMilli > 0 && at(lt, landed)) {
            boolean big = Rotten.bigTier(spin.winMilli) > 0;
            fx.sound(big ? Snd.WIN_BIG : Snd.WIN_SMALL, 8, sy, 15, 0.9f, 1f);
            if (big) {
                fx.sound(Snd.ZOMBIE, 8, sy, 15, 1f, 0.75f);
                fx.sound(Snd.COINS, 8, 16, 16, 0.9f, 1f);
                fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, TOXIC, BLOOD}, 24, 0.3f);
            }
        }
        if (Rotten.bigTier(spin.winMilli) > 0) zombieFx(fx, r, lt - landed);
        boolean max = (spin.events & Rotten.EV_MAXWIN) != 0;
        int end = Rotten.duration(spin) - (max ? Rotten.BIG_WIN_TICKS : 0);
        if (max) {
            if (at(lt, end)) {
                fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
                fx.sound(Snd.ZOMBIE, 8, sy, 15, 1f, 0.7f);
                fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, TOXIC, BLOOD, PURPLE}, 50, 0.4f);
            }
            zombieFx(fx, r, lt - end);
        }
        int winT = Rotten.winShow(spin);
        if ((spin.events & Rotten.EV_MUTATION) != 0 && at(lt, landed + winT)) fx.sound(Snd.SWITCH, 8, sy, 15, 1f, 0.8f);
        if ((spin.events & (Rotten.EV_SAVANT | Rotten.EV_INVASION)) != 0) {
            int b = landed + winT + ((spin.events & Rotten.EV_MUTATION) != 0 ? Rotten.MUTATION_T : 0);
            if (at(lt, b)) {
                fx.sound(Snd.BONUS, 8, sy, 15, 1f, 1f);
                fx.sound(Snd.ZOMBIE, 8, 30, 15, 1f, 0.8f);
                burst(fx, r, 8, sy, 14.5f, (spin.events & Rotten.EV_INVASION) != 0 ? BLOOD : TOXIC, GOLD, 40, 0.6f, 0.6f);
            }
        }
        if ((spin.events & Rotten.EV_BONUS_END) != 0 && at(lt, end - Rotten.END_T)) {
            fx.sound(Snd.WIN_JACKPOT, 8, sy, 15, 1f, 1f);
            fountain(fx, r, 8, 34, 15, FxSink.CONFETTI, new int[]{GOLD, TOXIC, BLOOD, PURPLE}, 40, 0.35f);
        }
    }

    /** Mains du zombie qui frappent la vitre, grognements, jetons qui pleuvent. */
    private void zombieFx(FxSink fx, Random r, float t) {
        if (t < 0 || t > Rotten.BIG_WIN_TICKS) return;
        for (int k = 0; k < 2; k++) {
            if (!at(t, 8 + k * 5)) continue;
            float x = k == 0 ? WX0 + 1.9f : WX1 - 1.9f;
            fx.sound(Snd.SMASH, x, WY0 + 3.4f, FZ, 0.8f, 0.6f + k * 0.1f);
            for (int i = 0; i < 5; i++) fx.particle(FxSink.SHARD, x, WY0 + 3.4f, FZ + 0.3f, rnd(r, 0.08f), 0.05f + r.nextFloat() * 0.05f, 0.06f, 0xC8D8C8, 0.25f, 16);
        }
        if (at(t, 30) || at(t, 54)) fx.sound(Snd.ZOMBIE, 8, 25, 15, 0.8f, 0.7f + r.nextFloat() * 0.2f);
        if (t > 10 && t < 60 && ((int) t) % 6 == 0) fx.sound(Snd.COINS, 8, 16, 16, 0.4f, 1.2f);
    }

    // =================================================================== vues
    @Override
    public RottenView newView() {
        return new RottenView();
    }

    @Override
    public RottenView demoView(float t) {
        RottenView v = new RottenView();
        // apercu hors-jeu : une partie tiree avec une graine fixe (-Drottendemo=graine) ou achat du Savant Fou
        long seed = Long.getLong("rottendemo", 11L);
        v.seq = Rotten.play(v.rules, new java.util.SplittableRandom(seed), (int) (long) Long.getLong("rottenbuy", 0L));
        if (Boolean.getBoolean("rottenbig")) {
            // premier tour simple avec un gros gain (zombie)
            for (long k = seed; k < seed + 500_000; k++) {
                Rotten.Sequence q = Rotten.play(v.rules, new java.util.SplittableRandom(k), 0);
                if (q.spins.size() == 1 && Rotten.bigTier(q.spins.get(0).winMilli) > 0) {
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
        return 140;
    }
}
