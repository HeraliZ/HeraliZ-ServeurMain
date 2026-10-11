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

import java.util.Locale;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Le Demineur : un etabli de deminage bricole dans le bunker. Grosse caisse de transport kaki ("DANGER MINES" au
 * pochoir) posee sur deux chevrons, dessus en tole larmee rouillee, bac a sable cerne de piquets et de barbeles ou
 * reposent 5 x 5 plaques d'acier. Deux vieux pupitres a ecran vert (mines, multiplicateur, gain), un panneau emaille
 * "LE DEMINEUR" sous sa baladeuse. Chaque plaque choisie se retourne sur une emeraude ou sur une mine (explosion) ;
 * a la fin, toutes les mines sont devoilees.
 */
public final class MinesDesign extends MachineDesign<MinesView> {
    // ---- atlas : partage avec tools/textures (PaintMines)
    public static final int[] R_CRATE = {0, 0, 512, 512};
    public static final int[] R_PLATE = {512, 0, 256, 256};
    public static final int[] R_TILE = {768, 0, 256, 256};
    public static final int[] R_OPEN = {512, 256, 256, 256};
    public static final int[] R_RUST = {768, 256, 128, 128};
    public static final int[] R_SAND = {896, 256, 128, 128};
    public static final int[] R_GEM = {768, 384, 128, 128};
    public static final int[] R_MINE = {896, 384, 128, 128};
    public static final int[] R_SIGN = {0, 512, 512, 256};
    public static final int[] R_HAZARD = {512, 512, 256, 128};
    public static final int[] R_CASE = {768, 512, 128, 128};
    public static final int[] R_SCREEN = {896, 512, 128, 128};
    public static final int[] R_LED = {512, 640, 64, 64};
    public static final int[] R_DARK = {576, 640, 64, 64};
    public static final int[] R_BULB = {640, 640, 64, 64};
    public static final int[] R_FLAG = {704, 640, 64, 64};
    public static final int[] R_CONCRETE = {768, 640, 128, 128};
    public static final int[] R_AMMO = {896, 640, 128, 128};
    public static final int[] R_STENCIL = {0, 768, 512, 128};
    public static final int[] R_GLASS = {512, 768, 128, 64};
    public static final int[] R_CABLE = {640, 768, 128, 64};
    public static final int[] R_WOOD = {0, 896, 256, 128};

    private static final Mat CRATE = FruitsDesign.tri(R_CRATE, 24), PLATE = FruitsDesign.tri(R_PLATE, 12), TILE = FruitsDesign.fit(R_TILE);
    private static final Mat OPEN = FruitsDesign.fit(R_OPEN), RUST = FruitsDesign.tri(R_RUST, 8), SAND = FruitsDesign.tri(R_SAND, 8);
    private static final Mat GEM = FruitsDesign.tri(R_GEM, 4), MINE = FruitsDesign.tri(R_MINE, 4), SIGN = FruitsDesign.fit(R_SIGN);
    private static final Mat HAZARD = FruitsDesign.tri(R_HAZARD, 6), CASE = FruitsDesign.tri(R_CASE, 6), SCREEN = FruitsDesign.fit(R_SCREEN);
    private static final Mat LED = FruitsDesign.fit(R_LED), DARK = FruitsDesign.tri(R_DARK, 8), BULB = FruitsDesign.fit(R_BULB);
    private static final Mat FLAG = FruitsDesign.tri(R_FLAG, 4), CONCRETE = FruitsDesign.tri(R_CONCRETE, 8), AMMO = FruitsDesign.tri(R_AMMO, 6);
    private static final Mat STENCIL = FruitsDesign.fit(R_STENCIL), GLASS = FruitsDesign.fit(R_GLASS), CABLE = FruitsDesign.tri(R_CABLE, 6);
    private static final Mat WOOD = FruitsDesign.tri(R_WOOD, 12);

    static final float TOP = 12.6f, GCX = 8f, GCZ = 3.6f, STEP = 4.25f, TILE_S = 3.8f;
    /** Demi-cote du bac a sable. */
    static final float BAC = 2.5f * STEP + 0.6f;
    static final int GREEN = 0x3DF5A0, RED_RGB = 0xFF4D5E, GOLD_RGB = 0xFFD36A, PHOSPHOR = 0x8CFF7A, AMBER = 0xFFB040;
    /** Duree du retournement d'une plaque. */
    static final int FLIP = 8;
    /** Baladeuse au-dessus du panneau. */
    static final float[] LAMP = {8, TOP + 6.35f, -8.2f};

    private Mesh body = Mesh.EMPTY, signGlow = Mesh.EMPTY, tile = Mesh.EMPTY, gem = Mesh.EMPTY, bomb = Mesh.EMPTY, beacons = Mesh.EMPTY;
    private Mesh screens = Mesh.EMPTY, glass = Mesh.EMPTY, bulb = Mesh.EMPTY, mineLed = Mesh.EMPTY;

    public MinesDesign() {
        super("mines", GREEN, RED_RGB);
        labelY = 30;
        itemScale = 0.3f;
    }

    static float tileX(int t) {
        return GCX + (t % 5 - 2) * STEP;
    }

    static float tileZ(int t) {
        return GCZ + (t / 5 - 2) * STEP;
    }

    /** Pupitres a ecran, de part et d'autre du bac. */
    private static float deskX(int side) {
        return side < 0 ? -8.6f : 24.6f;
    }

    /** Les quatre piquets du bac (x, z) : deux au fond, deux sur le rebord avant de la table. */
    private static float[][] posts() {
        float q = BAC + 0.35f;
        return new float[][]{{GCX - q, GCZ - q}, {GCX + q, GCZ - q}, {GCX + q, 14.45f}, {GCX - q, 14.45f}};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        float[] up = v(0, 1, 0), nz = v(0, 0, 1);
        // ---- deux chevrons au sol, grosse caisse de transport kaki, cornieres et ferrures rouillees
        for (float z : new float[]{-5.6f, 9.4f}) b.bevelBox(-13.4f, 0, z, 29.4f, 1.1f, z + 2.2f, 0.15f, WOOD);
        b.bevelBox(-12.6f, 1.1f, -7.8f, 28.6f, TOP - 1.0f, 13.2f, 0.25f, CRATE);
        for (float y : new float[]{1.7f, TOP - 3.2f}) b.bevelBox(-12.8f, y, -8f, 28.8f, y + 1.3f, 13.4f, 0.1f, CRATE);
        BunkerKit.corners(b, -12.6f, 1.1f, -7.8f, 28.6f, TOP - 1.0f, 13.2f, 1.1f, RUST);
        b.push().translate(8, 6.35f, 13.42f);
        b.decalZ(STENCIL, 0, 0, 0, 26f, 6.5f);
        b.pop();
        // poignees de corde sur les flancs (barres rouillees)
        for (float x : new float[]{-12.9f, 28.9f}) b.bevelBox(x - 0.25f, 6.2f, 0.4f, x + 0.25f, 6.8f, 5.0f, 0.1f, RUST);
        // ---- dessus : tole larmee, chant rouille, bande de danger ecaillee devant
        b.bevelBox(-14.6f, TOP - 1.0f, -10.2f, 30.6f, TOP, 14.8f, 0.2f, RUST, new Mat[]{null, null, null, PLATE, null, null}, 0);
        b.bevelBox(-14.8f, TOP - 1.05f, 14.4f, 30.8f, TOP + 0.12f, 15f, 0.12f, HAZARD);
        BunkerKit.rivetsX(b, -13.6f, 29.6f, TOP - 0.5f, 14.82f, 12, 0.2f, RUST);
        // ---- bac a sable : cadre de planches grises, sable, quatre piquets et deux rangs de fil barbele
        float g = BAC;
        b.face(SAND, up, p(GCX - g, TOP + 0.05f, GCZ + g), p(GCX + g, TOP + 0.05f, GCZ + g), p(GCX + g, TOP + 0.05f, GCZ - g), p(GCX - g, TOP + 0.05f, GCZ - g));
        // (devant, c'est le rebord de la table et sa bande de danger qui ferment le bac)
        b.bevelBox(GCX - g - 0.7f, TOP, GCZ - g - 0.7f, GCX + g + 0.7f, TOP + 0.62f, GCZ - g, 0.08f, WOOD);
        b.bevelBox(GCX - g - 0.7f, TOP, GCZ - g, GCX - g, TOP + 0.62f, GCZ + g - 0.05f, 0.08f, WOOD);
        b.bevelBox(GCX + g, TOP, GCZ - g, GCX + g + 0.7f, TOP + 0.62f, GCZ + g - 0.05f, 0.08f, WOOD);
        float[][] posts = posts();
        for (float[] c : posts) {
            b.push().translate(c[0], TOP + 0.6f, c[1]);
            b.cylinder(0.22f, 0, 1.75f, 6, RUST, null, RUST);
            b.pop();
        }
        for (int k = 0; k < 4; k++) {
            float[] a = posts[k], c = posts[(k + 1) % 4];
            // le cote du joueur reste degage : pas de barbele devant
            if (k == 2) continue;
            for (float y : new float[]{TOP + 1.35f, TOP + 2.0f}) {
                BunkerKit.cable(b, p(a[0], y, a[1]), p(c[0], y, c[1]), 0.12f, 0.05f, RUST);
            }
            // barbes
            for (int j = 1; j < 7; j++) {
                float t = j / 7f, x = a[0] + (c[0] - a[0]) * t, z = a[1] + (c[1] - a[1]) * t;
                b.bevelBox(x - 0.16f, TOP + 1.22f, z - 0.03f, x + 0.16f, TOP + 1.28f, z + 0.03f, 0, RUST);
                b.bevelBox(x - 0.03f, TOP + 1.87f, z - 0.16f, x + 0.03f, TOP + 1.93f, z + 0.16f, 0, RUST);
            }
        }
        // ---- pupitres lateraux : boitiers militaires inclines, cadre d'ecran, interrupteurs
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(deskX(sd), TOP, 4f).rotateX(28);
            b.bevelBox(-4.6f, 0, -4.4f, 4.6f, 1.2f, 4.4f, 0.3f, CASE);
            b.bevelBox(-4.3f, 1.2f, -4.1f, 4.3f, 1.42f, -3.5f, 0.06f, RUST);
            b.bevelBox(-4.3f, 1.2f, 3.5f, 4.3f, 1.42f, 4.1f, 0.06f, RUST);
            b.bevelBox(-4.3f, 1.2f, -3.5f, -3.7f, 1.42f, 3.5f, 0.06f, RUST);
            b.bevelBox(3.7f, 1.2f, -3.5f, 4.3f, 1.42f, 3.5f, 0.06f, RUST);
            for (int k = 0; k < 3; k++) {
                b.push().translate(-2.4f + k * 2.4f, 0.55f, 4.42f).rotateX(90);
                b.cylinder(0.3f, 0, 0.32f, 6, RUST, null, RUST);
                b.pop();
            }
            b.pop();
            // cale de bois sous le pupitre, cable vers l'arriere de la caisse
            b.bevelBox(deskX(sd) - 3.6f, TOP, 0.2f, deskX(sd) + 3.6f, TOP + 1.9f, 1.6f, 0.1f, WOOD);
            BunkerKit.cable(b, p(deskX(sd) - sd * 1.5f, TOP + 1.6f, 0.4f), p(deskX(sd) - sd * 4.5f, TOP + 0.2f, -8.6f), 0.5f, 0.13f, CABLE);
        }
        // ---- panneau emaille "LE DEMINEUR" sur deux poteaux de bois, baladeuse au-dessus
        for (float x : new float[]{0.7f, 15.3f}) b.bevelBox(x - 0.4f, TOP, -9.75f, x + 0.4f, TOP + 5.9f, -8.95f, 0.06f, WOOD);
        b.bevelBox(0.2f, TOP + 0.7f, -9.05f, 15.8f, TOP + 4.5f, -8.75f, 0.06f, RUST, new Mat[]{null, null, null, null, null, SIGN}, 0);
        b.bevelBox(0.3f, TOP + 5.5f, -9.6f, 15.7f, TOP + 5.95f, -9.1f, 0.06f, WOOD);
        b.bevelBox(LAMP[0] - 0.12f, LAMP[1] + 0.9f, -9.4f, LAMP[0] + 0.12f, LAMP[1] + 1.05f, LAMP[2], 0.03f, RUST);
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2], RUST);
        // ---- ce qui traine sur l'etabli : caisse de munitions, fanions de balisage, sonde de deminage
        b.bevelBox(-13.6f, TOP, -9.4f, -8.4f, TOP + 2.6f, -6.6f, 0.15f, AMMO);
        b.bevelBox(-13.75f, TOP + 2.35f, -9.55f, -8.25f, TOP + 2.85f, -6.45f, 0.1f, AMMO);
        b.bevelBox(-11.6f, TOP + 2.85f, -8.3f, -10.4f, TOP + 3.1f, -7.7f, 0.05f, RUST);
        for (int k = 0; k < 3; k++) {
            float x = 25.2f + k * 1.5f, z = -7.6f - (k % 2) * 1.1f, h = 3.4f - k * 0.5f;
            b.push().translate(x, TOP, z).rotateZ(-6 + k * 5);
            b.cylinder(0.08f, 0, h, 4, RUST, null, null);
            b.push().translate(0, h - 0.55f, 0);
            b.prism(new float[][]{{0.05f, -0.5f}, {1.35f, 0}, {0.05f, 0.5f}}, 0.04f, FLAG, FLAG);
            b.pop();
            b.pop();
        }
        b.push().translate(20.5f, TOP + 0.14f, 13.2f).rotateZ(90).rotateX(8);
        b.cylinder(0.1f, -4.2f, 4.2f, 5, RUST, RUST, RUST);
        b.pop();
        b.bevelBox(24.4f, TOP, 12.75f, 26.2f, TOP + 0.3f, 13.65f, 0.08f, WOOD);
        Occlusion.bake(b, 0.5f, 3f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(8, TOP + 2.6f, -8.73f);
        sg.decalZ(SIGN, 0, 0, 0, 15.2f, 3.8f);
        sg.pop();
        signGlow = sg.build();

        MeshBuilder sc = new MeshBuilder(), gl = new MeshBuilder();
        for (int sd = -1; sd <= 1; sd += 2) {
            sc.push().translate(deskX(sd), TOP, 4f).rotateX(28);
            sc.face(SCREEN, up, p(-3.7f, 1.22f, 3.5f), p(3.7f, 1.22f, 3.5f), p(3.7f, 1.22f, -3.5f), p(-3.7f, 1.22f, -3.5f));
            sc.pop();
            gl.push().translate(deskX(sd), TOP, 4f).rotateX(28);
            gl.face(GLASS, up, p(-3.7f, 1.4f, 3.5f), p(3.7f, 1.4f, 3.5f), p(3.7f, 1.4f, -3.5f), p(-3.7f, 1.4f, -3.5f));
            gl.pop();
        }
        screens = sc.build();
        glass = gl.build();

        // voyants en haut des quatre piquets : ils prennent la couleur de la partie
        MeshBuilder bd = new MeshBuilder();
        for (float[] c : posts) {
            bd.push().translate(c[0], TOP + 2.55f, c[1]);
            bd.sphere(0.34f, 8, 5, LED);
            bd.pop();
        }
        beacons = bd.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();

        // plaque d'acier (face cachee vers +Y, face ouverte vers -Y), origine au centre
        MeshBuilder tb = new MeshBuilder();
        float h = TILE_S / 2;
        tb.bevelBox(-h, -0.35f, -h, h, 0.35f, h, 0.14f, RUST, new Mat[]{null, null, OPEN, TILE, null, null}, 0);
        tile = tb.build();
        MeshBuilder gb = new MeshBuilder();
        gb.gem(1.1f, 0.6f, 1.2f, 8, GEM);
        gem = gb.build();
        // mine antipersonnel : galette kaki nervuree, plateau de pression rouille ; penchee pour qu'on la voie de face
        MeshBuilder bb = new MeshBuilder(), ml = new MeshBuilder();
        bb.push().rotateX(38);
        bb.cylinder(1.25f, -0.32f, 0.32f, seg(14), MINE, MINE, MINE);
        bb.push().translate(0, -0.32f, 0);
        bb.torus(1.25f, 0.14f, seg(14), 4, 0, 360, MINE);
        bb.pop();
        bb.push().translate(0, 0.32f, 0);
        bb.torus(1.25f, 0.14f, seg(14), 4, 0, 360, MINE);
        bb.pop();
        bb.cylinder(0.62f, 0.32f, 0.62f, seg(12), RUST, null, RUST);
        for (int k = 0; k < 6; k++) {
            bb.push().rotateY(k * 60);
            bb.bevelBox(0.7f, 0.3f, -0.08f, 1.2f, 0.42f, 0.08f, 0.02f, MINE);
            bb.pop();
        }
        bb.pop();
        bomb = bb.build();
        ml.push().rotateX(38).translate(0, 0.66f, 0);
        ml.sphereSection(0.2f, 6, 3, 0, 90, LED);
        ml.pop();
        mineLed = ml.build();
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, MinesView v, Pose p, DrawList out) {
        // baladeuse du panneau : elle vacille de temps en temps
        float flick = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.94f ? 0.3f : 0.9f + 0.1f * (float) Math.sin(s.time * 1.3f);
        out.solid(p, bulb, flick);
        out.solid(p, signGlow, 0.5f * flick);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2], 2.6f, 0, 0xFFC880, 0.3f * flick);
        out.solid(p, screens, 0.55f);
        boolean over = v.phase == MinesView.BOOM || v.phase == MinesView.CASHED;
        float sinceEnd = s.since(v.endTick);
        // voyants des piquets : verts en jeu, rouges clignotants a l'explosion, or a l'encaissement
        int stateCol = v.phase == MinesView.BOOM ? RED_RGB : v.phase == MinesView.CASHED ? GOLD_RGB : GREEN;
        float stateK = v.phase == MinesView.BOOM && sinceEnd < 40 ? (((int) (s.time / 2)) % 2 == 0 ? 1 : 0.2f) : v.phase == MinesView.IDLE ? 0.35f + 0.15f * s.pulse(0.06f)
                : 0.6f + 0.3f * s.pulse(0.1f);
        out.solidTinted(p, beacons, 0, 0.25f, 0.25f, 0.25f);
        out.additive(p, beacons, stateCol, stateK);
        if (!s.item) {
            for (float[] c : posts()) out.billboard(p, Sprite.HALO, c[0], TOP + 2.55f, c[1], 1.5f, 0, stateCol, 0.4f * stateK);
        }
        for (int t = 0; t < 25; t++) {
            boolean safeShown = (v.revealed & (1 << t)) != 0;
            boolean mineShown = over && (v.mineShown & (1 << t)) != 0;
            float flip = 0, lift = 0;
            boolean open = false;
            if (!s.item && (safeShown || mineShown)) {
                long start = safeShown || t == v.boomTile ? v.tileTicks[t] : v.endTick + 6 + (t % 7) * 2;
                float k = s.since(start);
                if (k >= 0) {
                    float u = Math.min(1f, k / FLIP);
                    flip = 180 * Ease.inOutSine(u);
                    lift = 1.6f * Ease.bell(u, 0, 1);
                    open = u > 0.5f;
                }
            }
            float x = tileX(t), z = tileZ(t);
            p.push().translate(x, TOP + 0.4f + lift, z).rotX(flip);
            boolean hover = !over && v.phase == MinesView.PLAYING && !safeShown;
            out.solid(p, tile, hover ? 0.2f + 0.16f * (float) Math.sin(s.time * 0.2f + t * 0.7f) : 0.12f);
            p.pop();
            if (open) {
                boolean isMine = mineShown && !safeShown;
                float k = s.since(safeShown || t == v.boomTile ? v.tileTicks[t] : v.endTick + 6 + (t % 7) * 2) - FLIP / 2f;
                float pop = Ease.outBack(Math.min(1f, k / 8f), 2f);
                p.push().translate(x, TOP + 0.8f + pop * 0.9f, z).rotY(s.time * 2 + t * 30).scale(pop);
                if (isMine) {
                    out.solid(p, bomb, 0);
                    // voyant rouge de la mine : il bat, tres vite sur celle qui a saute
                    boolean blink = ((int) (s.time / (t == v.boomTile ? 2 : 7))) % 2 == 0;
                    out.solidTinted(p, mineLed, 0, 0.4f, 0.05f, 0.05f);
                    if (blink) out.additive(p, mineLed, RED_RGB, 1f);
                } else {
                    out.solid(p, gem, 0.7f);
                    out.additive(p, gem, GREEN, 0.25f);
                }
                p.pop();
                if (isMine && t == v.boomTile && k < 30) {
                    out.billboard(p, Sprite.SHOCK, x, TOP + 2, z, 2 + k * 0.5f, 0, RED_RGB, 1 - k / 30f);
                    out.billboard(p, Sprite.FLARE, x, TOP + 2, z, 6 * (1 - k / 30f), s.time * 4, 0xFFB040, 1 - k / 30f);
                }
            }
        }
        if (s.item) return;
        // ---- ecrans des pupitres (phosphore vert)
        String left1 = I18n.get("heralizcasino.mines.titre"), left2 = String.valueOf(v.mines);
        String right1, right2;
        int rc = PHOSPHOR;
        if (v.phase == MinesView.PLAYING) {
            right1 = String.format(Locale.ROOT, "x%.2f", v.mult100 / 100.0);
            right2 = I18n.get("heralizcasino.mines.jetons", fmt(v.bet * v.mult100 / 100));
        } else if (v.phase == MinesView.BOOM) {
            right1 = I18n.get("heralizcasino.mines.boum");
            right2 = I18n.get("heralizcasino.mines.jetons", "-" + fmt(v.bet));
            rc = RED_RGB;
        } else if (v.phase == MinesView.CASHED) {
            right1 = String.format(Locale.ROOT, "x%.2f", v.mult100 / 100.0);
            right2 = I18n.get("heralizcasino.mines.jetons", "+" + fmt(v.win));
            rc = GOLD_RGB;
        } else {
            right1 = I18n.get("heralizcasino.mines.pret");
            right2 = I18n.get("heralizcasino.mines.choisissez");
        }
        screenText(p, out, deskX(-1), left1, left2, PHOSPHOR, AMBER);
        screenText(p, out, deskX(1), right1, right2, rc, 0xE8FFE0);
        out.translucent(p, glass, 0xFFFFFF, 0.3f);
        if (v.phase == MinesView.CASHED && sinceEnd < 60) {
            out.billboard(p, Sprite.FLARE, GCX, TOP + 3, GCZ, 9f * (1 - sinceEnd / 60f), s.time * 2, GOLD_RGB, 0.4f);
        }
    }

    /** Deux lignes de texte sur un ecran incline. */
    private static void screenText(Pose p, DrawList out, float cx, String l1, String l2, int c1, int c2) {
        p.push().translate(cx, TOP, 4f).rotX(28).translate(0, 1.26f, 0).rotX(-90);
        out.text(p, l1, 0, 1.4f, 0, fit(l1, 1.5f, 6.6f, true), c1, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        out.text(p, l2, 0, -1.3f, 0, fit(l2, 1.8f, 6.6f, true), c2, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        p.pop();
    }

    // =================================================================== camera et zones cliquables
    @Override
    public void camera(MinesView v, Scene s, int mode, Shot out) {
        out.set(GCX, TOP + 31, GCZ + 21, GCX, TOP, GCZ - 1.5f, 50);
    }

    @Override
    public void hotspots(MinesView v, Scene s, Hotspots out) {
        float h = TILE_S / 2;
        for (int t = 0; t < 25; t++) out.flat(t, tileX(t) - h, tileZ(t) - h, tileX(t) + h, tileZ(t) + h, TOP + 0.8f);
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, MinesView v, FxSink fx, Random r) {
        if (r.nextInt(220) == 0) fx.particle(FxSink.SPARK, LAMP[0], LAMP[1] - 0.4f, LAMP[2], rnd(r, 0.04f), 0.03f, 0.04f, 0xFFE0A0, 0.4f, 8);
        for (int t = 0; t < 25; t++) {
            boolean safe = (v.revealed & (1 << t)) != 0;
            if (safe && s.since(v.tileTicks[t]) >= 0 && s.since(v.tileTicks[t]) < 1) {
                fx.sound(Snd.CARD_FLIP, tileX(t), TOP + 1, tileZ(t), 0.6f, 0.8f);
                fx.sound(Snd.GEM, tileX(t), TOP + 1, tileZ(t), 0.8f, 0.9f + Integer.bitCount(v.revealed) * 0.05f);
                for (int k = 0; k < 8; k++) {
                    fx.particle(FxSink.SPARK, tileX(t) + rnd(r, 1.2f), TOP + 1.2f, tileZ(t) + rnd(r, 1.2f), rnd(r, 0.1f), 0.2f + r.nextFloat() * 0.2f, rnd(r, 0.1f),
                            GREEN, 0.9f, 16);
                }
                // un peu de sable souleve par la plaque
                for (int k = 0; k < 4; k++) {
                    fx.particle(FxSink.SMOKE, tileX(t) + rnd(r, 1.4f), TOP + 0.5f, tileZ(t) + rnd(r, 1.4f), rnd(r, 0.05f), 0.04f, rnd(r, 0.05f), 0x8A7450, 1.1f, 18);
                }
            }
        }
        if (v.phase == MinesView.BOOM && v.boomTile >= 0) {
            float k = s.since(v.tileTicks[v.boomTile]);
            if (k >= FLIP / 2f && k < FLIP / 2f + 1) {
                float x = tileX(v.boomTile), z = tileZ(v.boomTile);
                fx.sound(Snd.MINE, x, TOP + 1, z, 1f, 1f);
                burst(fx, r, x, TOP + 1.5f, z, RED_RGB, 0xFFB040, 50, 1.6f, 1.6f);
                for (int i = 0; i < 18; i++) {
                    fx.particle(FxSink.SMOKE, x + rnd(r, 1.5f), TOP + 1, z + rnd(r, 1.5f), rnd(r, 0.2f), 0.15f + r.nextFloat() * 0.2f, rnd(r, 0.2f), 0x3A3A3A, 3f, 50);
                }
                // gerbe de sable
                for (int i = 0; i < 14; i++) {
                    fx.particle(FxSink.DROP, x + rnd(r, 0.8f), TOP + 0.8f, z + rnd(r, 0.8f), rnd(r, 0.35f), 0.35f + r.nextFloat() * 0.4f, rnd(r, 0.35f), 0x9A8258, 0.5f, 26);
                }
            }
        }
        if (v.phase == MinesView.CASHED && s.since(v.endTick) >= 0 && s.since(v.endTick) < 1) {
            fx.sound(Snd.WIN_SMALL, GCX, TOP + 2, GCZ, 1f, 1f);
            fx.sound(Snd.COINS, GCX, TOP + 2, GCZ, 0.8f, 1f);
        }
    }

    // =================================================================== vues
    @Override
    public MinesView newView() {
        return new MinesView();
    }

    @Override
    public MinesView demoView(float t) {
        MinesView v = new MinesView();
        v.phase = MinesView.PLAYING;
        v.mines = 5;
        v.bet = 100;
        int[] order = {12, 6, 18, 8, 16};
        for (int i = 0; i < order.length; i++) {
            long at = DEMO_START + 10 + i * 18;
            if (DEMO_START + t >= at) {
                v.revealed |= 1 << order[i];
                v.tileTicks[order[i]] = at;
                v.mult100 = new long[]{118, 143, 175, 216, 270}[i];
            }
        }
        if (t >= 110) {
            v.phase = MinesView.BOOM;
            v.boomTile = 3;
            v.tileTicks[3] = DEMO_START + 110;
            v.endTick = DEMO_START + 110;
            v.mineShown = (1 << 3) | (1 << 0) | (1 << 21) | (1 << 14) | (1 << 24);
        }
        return v;
    }

    @Override
    public int demoLength() {
        return 170;
    }
}
