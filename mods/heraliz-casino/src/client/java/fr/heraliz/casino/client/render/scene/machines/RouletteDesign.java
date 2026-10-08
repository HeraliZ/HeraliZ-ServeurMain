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
import fr.heraliz.casino.game.roulette.Roulette;
import fr.heraliz.casino.game.roulette.RouletteLayout;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Table de roulette, version bunker : de vieilles planches posees sur deux futs et un treteau de caisses, un tapis de
 * mise repeint au pochoir sur une couverture verte, un tuyau de caoutchouc en guise de bourrelet. La roue est bricolee
 * dans un pneu : cuvette de tole, 37 ou 38 cases peintes a la main, volant de vanne rouge en guise de croisillon. Une
 * bille d'acier tourne en sens inverse, ralentit, tombe et rebondit dans sa case ; les piles de jetons de tous les
 * joueurs couvrent le tapis, une cartouche de fusil marque le numero gagnant, et un vieux boitier a ecran, sur son
 * poteau, affiche les derniers numeros.
 */
public final class RouletteDesign extends MachineDesign<RouletteView> {
    public static final int[] R_LAYOUT = {0, 0, 1024, 512};
    public static final int[] R_PLANK = {0, 512, 256, 256};
    public static final int[] R_WOOD = {256, 512, 256, 256};
    public static final int[] R_RUBBER = {512, 512, 256, 128};
    public static final int[] R_RUST = {768, 512, 256, 128};
    public static final int[] R_POCKET = {512, 640, 128, 128};
    public static final int[] R_CHIP_EDGE = {640, 640, 64, 64};
    public static final int[] R_CHIP_WHITE = {704, 640, 64, 64};
    public static final int[] R_CHIP_TOP = {768, 640, 64, 64};
    public static final int[] R_DARK = {832, 640, 64, 64};
    public static final int[] R_BALL = {896, 640, 128, 128};
    public static final int[] R_STEEL = {0, 768, 256, 256};
    public static final int[] R_SIGN = {256, 768, 512, 128};
    public static final int[] R_SHELL = {768, 768, 128, 128};
    public static final int[] R_BRASS = {896, 768, 128, 128};
    public static final int[] R_DRUM = {256, 896, 256, 128};
    public static final int[] R_TIRE = {512, 896, 256, 128};
    public static final int[] R_VALVE = {768, 896, 128, 128};
    public static final int[] R_BULB = {896, 896, 64, 64};
    public static final int[] R_CABLE = {960, 896, 64, 64};
    public static final int[] R_CRATE = {896, 960, 128, 64};

    private static final Mat LAYOUT = FruitsDesign.fit(R_LAYOUT), PLANK = FruitsDesign.tri(R_PLANK, 30), WOOD = FruitsDesign.tri(R_WOOD, 20);
    private static final Mat RUBBER = FruitsDesign.tri(R_RUBBER, 16), RUST = FruitsDesign.tri(R_RUST, 10), POCKET = FruitsDesign.tri(R_POCKET, 8);
    private static final Mat DARK = FruitsDesign.tri(R_DARK, 4), BALL = FruitsDesign.tri(R_BALL, 2), STEEL = FruitsDesign.tri(R_STEEL, 10);
    private static final Mat SIGN = FruitsDesign.fit(R_SIGN), SHELL = FruitsDesign.tri(R_SHELL, 4), WHITE = FruitsDesign.fit(R_CHIP_WHITE);
    /** Hauteur de fut couverte par sa tuile (le marquage au pochoir tombe a mi-hauteur). */
    public static final float DRUM_WORLD = 10.4f, DRUM_R = 5.1f;
    private static final Mat BRASS = FruitsDesign.tri(R_BRASS, 4), DRUM = FruitsDesign.tri(R_DRUM, DRUM_WORLD), TIRE = FruitsDesign.tri(R_TIRE, 10);
    private static final Mat VALVE = FruitsDesign.tri(R_VALVE, 6), BULB = FruitsDesign.fit(R_BULB), CABLE = FruitsDesign.tri(R_CABLE, 6);
    private static final Mat CRATE = FruitsDesign.tri(R_CRATE, 12);

    /** Plateau : assez large pour que le bourrelet fasse tout le tour sans toucher le pneu de la roue. */
    static final float TOP = 12f, X0 = -30.2f, X1 = 30.2f, Z0 = -13.3f, Z1 = 13.9f;
    static final float WX = -14.2f, WZ = 0.2f;
    static final float R_BOWL = 11.4f, R_TRACK = 10f, R_RING_OUT = 8.6f, R_RING_IN = 7.2f, R_POCK = 6.6f, R_CONE = 6f;
    static final float Y_TRACK = 2.6f, Y_RING = 1.5f, Y_POCK = 1.2f;
    /** Tapis : origine (coin cote croupier, a gauche) et echelle (unites de RouletteLayout -> px). */
    static final float LX = 1.5f, LZ = -11f, MAT_S = 1.1f;
    static final int GOLD_RGB = 0xFFD36A, PHOSPHOR = 0x8CFF7A, AMBER = 0xFFB040;
    /** Boitier des derniers numeros (poteau au coin gauche) et sa baladeuse. */
    static final float BOX_X = -28.8f, BOX_Z = -10.6f;
    static final float[] LAMP = {BOX_X, TOP + 23.9f, BOX_Z + 1.5f};
    /** Plaque au pochoir vissee sur le boitier (px). */
    public static final float SIGN_W = 3.6f, SIGN_H = 1.6f;

    private Mesh body = Mesh.EMPTY, turret = Mesh.EMPTY, ball = Mesh.EMPTY, dolly = Mesh.EMPTY, signGlow = Mesh.EMPTY, bulb = Mesh.EMPTY;
    private Mesh[] headColors37, headColors38;
    private Mesh highlight = Mesh.EMPTY;
    private ChipStacks chips;

    public RouletteDesign() {
        super("roulette", 0xFF4D5E, GOLD_RGB);
        labelY = 32;
        itemScale = 0.2f;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- plateau : larges planches grises, couverture verte agrafee (le tapis de mise), tuyau de caoutchouc
        b.bevelBox(X0, TOP - 1.4f, Z0, X1, TOP, Z1, 0.2f, WOOD, new Mat[]{null, null, null, PLANK, null, null}, 0);
        float mw = RouletteLayout.W * MAT_S, md = RouletteLayout.D * MAT_S;
        b.face(LAYOUT, v(0, 1, 0), p(LX, TOP + 0.02f, LZ + md), p(LX + mw, TOP + 0.02f, LZ + md), p(LX + mw, TOP + 0.02f, LZ), p(LX, TOP + 0.02f, LZ));
        // ---- bourrelet : un seul tuyau de caoutchouc qui fait tout le tour du plateau (coins arrondis), pose a cheval
        // sur le chant ; feuillard d'acier cloue le long du chant, colliers de fer tous les huit pixels
        float[][] loop = railLoop(X0 + 0.1f, Z0 + 0.1f, X1 - 0.1f, Z1 - 0.1f, 2.4f);
        float[] radii = new float[loop.length];
        java.util.Arrays.fill(radii, 0.88f);
        float[][] rail = new float[loop.length][];
        for (int i = 0; i < loop.length; i++) rail[i] = p(loop[i][0], TOP + 0.42f, loop[i][1]);
        b.tube(rail, radii, seg(12), RUBBER, false, false, 0, false);
        float band = 0.12f;
        b.bevelBox(X0 - band, TOP - 1.25f, Z0 + 1.5f, X0, TOP - 0.45f, Z1 - 1.5f, 0.03f, RUST);
        b.bevelBox(X1, TOP - 1.25f, Z0 + 1.5f, X1 + band, TOP - 0.45f, Z1 - 1.5f, 0.03f, RUST);
        b.bevelBox(X0 + 1.5f, TOP - 1.25f, Z0 - band, X1 - 1.5f, TOP - 0.45f, Z0, 0.03f, RUST);
        b.bevelBox(X0 + 1.5f, TOP - 1.25f, Z1, X1 - 1.5f, TOP - 0.45f, Z1 + band, 0.03f, RUST);
        float acc = 3.5f;
        for (int i = 1; i < rail.length; i++) {
            float dx = rail[i][0] - rail[i - 1][0], dz = rail[i][2] - rail[i - 1][2];
            acc += (float) Math.sqrt(dx * dx + dz * dz);
            if (acc < 8f) continue;
            acc = 0;
            float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            b.push().translate(rail[i][0], rail[i][1], rail[i][2]).rotateY(yaw).rotateX(90);
            b.cylinder(0.95f, -0.2f, 0.2f, seg(12), RUST, null, null);
            b.pop();
        }
        // equerres de tole aux quatre coins, sous le tuyau
        for (float[] c : new float[][]{{X0, Z0, 1, 1}, {X1, Z0, -1, 1}, {X0, Z1, 1, -1}, {X1, Z1, -1, -1}}) {
            float x0 = c[0], z0 = c[1], sx = c[2], sz = c[3];
            // plaque sur le chant d'en face (le long de x) et sur le chant de cote (le long de z)
            b.bevelBox(Math.min(x0, x0 + sx * 2.6f), TOP - 1.3f, Math.min(z0, z0 - sz * 0.16f), Math.max(x0, x0 + sx * 2.6f), TOP - 0.35f,
                    Math.max(z0, z0 - sz * 0.16f), 0.03f, RUST);
            b.bevelBox(Math.min(x0, x0 - sx * 0.16f), TOP - 1.3f, Math.min(z0, z0 + sz * 2.6f), Math.max(x0, x0 - sx * 0.16f), TOP - 0.35f,
                    Math.max(z0, z0 + sz * 2.6f), 0.03f, RUST);
        }
        // traverses clouees sous les planches
        for (float x : new float[]{X0 + 6, -2, X1 - 6}) b.bevelBox(x - 1, TOP - 2.2f, Z0 + 0.6f, x + 1, TOP - 1.4f, Z1 - 0.6f, 0.08f, WOOD);
        // ---- pietement : un fut a chaque bout, un treteau de caisses au milieu, tube d'echafaudage en entretoise
        BunkerKit.drum(b, X0 + 7.5f, 0, 0.2f, DRUM_R, TOP - 2.2f, seg(24), DRUM, RUST);
        BunkerKit.drum(b, X1 - 7.5f, 0, 0.2f, DRUM_R, TOP - 2.2f, seg(24), DRUM, RUST);
        BunkerKit.crate(b, -4.5f, 0, -3.6f, 4.5f, 5.6f, 4.0f, CRATE, WOOD);
        BunkerKit.crate(b, -3.6f, 5.6f, -2.8f, 3.6f, TOP - 2.2f, 3.2f, CRATE, WOOD);
        b.push().translate(X0 + 12.4f, 4.2f, -5.2f).rotateZ(-90);
        b.cylinder(0.4f, 0, 35.2f, seg(8), RUST, RUST, RUST);
        b.pop();
        // ---- la roue : un pneu couche sert de cuvette, garni d'une tole ou roule la bille
        b.push().translate(WX, TOP, WZ);
        float[][] tire = {{R_BOWL - 0.2f, 0}, {R_BOWL + 0.25f, 0.6f}, {R_BOWL + 0.3f, 2f}, {R_BOWL + 0.1f, Y_TRACK + 0.6f}, {R_BOWL - 0.45f, Y_TRACK + 1.15f},
                {R_BOWL - 0.85f, Y_TRACK + 0.95f}};
        float[][] track = {{R_BOWL - 0.85f, Y_TRACK + 0.95f}, {R_TRACK + 0.45f, Y_TRACK + 0.35f}, {R_TRACK, Y_TRACK}, {R_RING_OUT + 0.45f, Y_RING + 0.25f},
                {R_RING_OUT + 0.1f, Y_RING}};
        b.lathe(tire, seg(72), 0, 360, BunkerKit.seamless(TIRE, tire), 1, 1, 0, 0, 0, false);
        b.lathe(track, seg(72), 0, 360, BunkerKit.seamless(STEEL, track), 1, 1, 0, 0, 0, false);
        b.push().translate(0, Y_TRACK + 0.95f, 0);
        b.torus(R_BOWL - 0.85f, 0.1f, seg(72), 4, 0, 360, RUST);
        b.pop();
        b.pop();
        // ---- boitier des derniers numeros (poteau au coin gauche) : tole rouillee, plaque au pochoir, baladeuse
        b.push().translate(BOX_X, TOP, BOX_Z);
        b.cylinder(0.35f, 0, 9.5f, seg(8), RUST, null, null);
        b.bevelBox(-1.8f, 9.2f, -0.6f, 1.8f, 20.5f, 0.6f, 0.15f, RUST, new Mat[]{null, null, null, null, null, DARK}, 0);
        b.bevelBox(-1.95f, 20.3f, -0.75f, 1.95f, 22.2f, 0.75f, 0.12f, RUST);
        for (float y : new float[]{9.35f, 20.05f}) b.bevelBox(-1.9f, y, 0.55f, 1.9f, y + 0.3f, 0.75f, 0.04f, RUST);
        b.bevelBox(-0.1f, 22.2f, -0.1f, 0.1f, 25.1f, 0.1f, 0.03f, RUST);
        b.bevelBox(-0.08f, 24.95f, 0, 0.08f, 25.1f, 1.5f, 0.02f, RUST);
        b.pop();
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2], RUST);
        BunkerKit.cable(b, p(BOX_X + 0.5f, TOP + 9.4f, BOX_Z - 0.5f), p(BOX_X + 1.6f, TOP + 0.1f, BOX_Z - 1.0f), 0.4f, 0.12f, CABLE);
        Occlusion.bake(b, 0.45f, 3f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(BOX_X, TOP + 21.25f, BOX_Z + 0.77f);
        sg.decalZ(SIGN, 0, 0, 0, SIGN_W, SIGN_H);
        sg.pop();
        signGlow = sg.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();

        headColors37 = head(37);
        headColors38 = head(38);
        // moyeu : cone de tole (enjoliveur) et volant de vanne rouge en guise de croisillon (tournent avec le cylindre)
        MeshBuilder t = new MeshBuilder();
        float[][] cone = {{R_CONE, Y_POCK}, {R_CONE - 0.4f, Y_POCK + 0.4f}, {3.5f, 2.3f}, {1.75f, 2.8f}, {0.05f, 2.9f}};
        t.lathe(cone, seg(48), 0, 360, BunkerKit.seamless(STEEL, cone), 1, 1, 0, 0, 0, false);
        t.push().translate(0, 2.8f, 0);
        t.cylinder(0.5f, 0, 1.4f, seg(10), RUST, null, null);
        t.cylinder(0.6f, 1.8f, 2.15f, 6, RUST, null, RUST);
        t.cylinder(0.26f, 2.15f, 2.45f, seg(8), RUST, null, RUST);
        t.pop();
        t.push().translate(0, 4.35f, 0);
        t.torus(2.7f, 0.3f, seg(28), 6, 0, 360, VALVE);
        t.cylinder(0.75f, -0.25f, 0.25f, seg(10), VALVE, VALVE, VALVE);
        t.pop();
        for (int k = 0; k < 4; k++) {
            t.push().translate(0, 4.35f, 0).rotateY(k * 90);
            t.tube(new float[][]{p(0, 0, 0.6f), p(0, 0.12f, 1.6f), p(0, 0, 2.6f)}, new float[]{0.26f, 0.22f, 0.24f}, seg(6), VALVE, false, false, 0, false);
            t.pop();
        }
        turret = t.build();

        MeshBuilder bl = new MeshBuilder();
        bl.sphere(0.42f, seg(12), seg(8), BALL);
        ball = bl.build();
        // marqueur du numero gagnant : une cartouche de fusil posee debout (culot de laiton, tube rouge)
        MeshBuilder dl = new MeshBuilder();
        dl.cylinder(0.52f, 0, 0.16f, seg(12), BRASS, BRASS, BRASS);
        dl.cylinder(0.46f, 0.16f, 0.62f, seg(12), BRASS, null, null);
        dl.cylinder(0.43f, 0.62f, 1.85f, seg(12), SHELL, null, SHELL);
        dolly = dl.build();
        MeshBuilder hl = new MeshBuilder();
        hl.face(WHITE, v(0, 1, 0), p(-0.5f, 0, 0.5f), p(0.5f, 0, 0.5f), p(0.5f, 0, -0.5f), p(-0.5f, 0, -0.5f));
        highlight = hl.build();
        chips = new ChipStacks(0.62f, 0.2f, seg(12));
    }

    /** Contour ferme (x, z) d'un rectangle aux coins arrondis, parcouru une fois (le dernier point rejoint le premier). */
    static float[][] railLoop(float x0, float z0, float x1, float z1, float r) {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        float[][] corners = {{x1 - r, z1 - r, 0}, {x0 + r, z1 - r, 90}, {x0 + r, z0 + r, 180}, {x1 - r, z0 + r, 270}};
        for (float[] c : corners) {
            for (int i = 0; i <= 6; i++) {
                double a = Math.toRadians(c[2] + 90.0 * i / 6);
                pts.add(new float[]{c[0] + r * (float) Math.cos(a), c[1] + r * (float) Math.sin(a)});
            }
        }
        // cotes droits decoupes en troncons (le tuyau reste bien rond sur toute la longueur)
        java.util.List<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            float[] a = pts.get(i), bb = pts.get((i + 1) % pts.size());
            float d = (float) Math.hypot(bb[0] - a[0], bb[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(d / 3f));
            for (int k = 0; k < n; k++) out.add(new float[]{a[0] + (bb[0] - a[0]) * k / n, a[1] + (bb[1] - a[1]) * k / n});
        }
        out.add(out.get(0).clone());
        return out.toArray(new float[0][]);
    }

    /**
     * Cylindre de n cases : [0] rouge, [1] noir, [2] vert (couronne des numeros + fonds de case), [3] frettes et anneaux.
     * Case i centree a l'angle i * 360 / n (convention : x = r sin a, z = r cos a).
     */
    private Mesh[] head(int n) {
        MeshBuilder[] bs = {new MeshBuilder(), new MeshBuilder(), new MeshBuilder(), new MeshBuilder()};
        int[] wheel = Roulette.wheel(n == 37 ? 1 : 2);
        float step = 360f / n;
        for (int i = 0; i < n; i++) {
            int num = wheel[i];
            int c = Roulette.isRed(num) ? 0 : Roulette.isBlack(num) ? 1 : 2;
            double a0 = Math.toRadians(i * step - step / 2), a1 = Math.toRadians(i * step + step / 2);
            // couronne des numeros (legerement inclinee)
            bs[c].face(POCKET, v(0, 1, 0), pc(R_RING_IN, a1, Y_RING + 0.08f), pc(R_RING_OUT, a1, Y_RING), pc(R_RING_OUT, a0, Y_RING), pc(R_RING_IN, a0, Y_RING + 0.08f));
            // fond de case
            bs[c].face(POCKET, v(0, 1, 0), pc(R_CONE, a1, Y_POCK), pc(R_RING_IN, a1, Y_POCK), pc(R_RING_IN, a0, Y_POCK), pc(R_CONE, a0, Y_POCK));
            // frette entre deux cases
            bs[3].push().rotateY((float) Math.toDegrees(a0));
            bs[3].bevelBox(-0.07f, Y_POCK, R_CONE, 0.07f, Y_POCK + 0.55f, R_RING_IN, 0.02f, STEEL);
            bs[3].pop();
        }
        bs[3].torus(R_RING_IN, 0.12f, seg(n), 4, 0, 360, STEEL);
        bs[3].push().translate(0, Y_RING - 0.05f, 0);
        bs[3].torus(R_RING_OUT, 0.1f, seg(n), 4, 0, 360, RUST);
        bs[3].pop();
        // paroi exterieure des cases
        bs[3].push().translate(0, Y_POCK, 0);
        bs[3].cylinder(R_RING_IN + 0.02f, 0, Y_RING - Y_POCK + 0.08f, seg(n), RUST, null, null);
        bs[3].pop();
        Mesh[] out = new Mesh[4];
        for (int k = 0; k < 4; k++) out[k] = bs[k].build();
        return out;
    }

    private static float[] pc(float r, double a, float y) {
        return p(r * (float) Math.sin(a), y, r * (float) Math.cos(a));
    }

    // =================================================================== cinematique
    /** Angle du cylindre (degres, rotation autour de Y). */
    static float wheelAngle(RouletteView v) {
        if (v.phase == RoundView.RUNNING || v.phase == RoundView.RESULT) {
            return Roulette.wheelAngle(v.spinTime, v.spinTicks, v.wheelStart);
        }
        return v.restAngle + v.sinceRest * 0.6f;
    }

    /** Bille : angle (repere de la table), rayon, hauteur ; false si pas de bille visible. */
    static boolean ballPos(RouletteView v, float[] out) {
        int n = v.zeros == 1 ? 37 : 38;
        float step = 360f / n;
        float w = wheelAngle(v);
        int number = v.phase == RoundView.RUNNING || v.phase == RoundView.RESULT ? v.number : (v.history.length > 0 ? v.history[0] : -1);
        if (number < 0) return false;
        int idx = indexOf(Roulette.wheel(v.zeros), number);
        if (idx < 0) return false;
        float pocket = idx * step;
        if (v.phase != RoundView.RUNNING) {
            out[0] = w + pocket;
            out[1] = (R_POCK + R_RING_IN) / 2 - 0.1f;
            out[2] = Y_POCK + 0.34f;
            return true;
        }
        float T = v.spinTicks;
        float u = Math.min(1f, v.spinTime / T);
        float rel0 = v.ballStart - v.wheelStart;
        float d = (((rel0 - pocket) % 360f) + 360f) % 360f;
        float relStart = pocket + 4 * 360f + d;
        float e = 1 - (float) Math.pow(1 - u, 2.4);
        float rel = relStart + (pocket - relStart) * e;
        out[0] = w + rel;
        // chute depuis la piste vers la case, avec deux rebonds
        float drop = Ease.range(u, 0.6f, 0.8f);
        float r = Ease.lerp(R_TRACK - 0.1f, (R_POCK + R_RING_IN) / 2 - 0.1f, Ease.inQuad(drop));
        float hop = 0.9f * Ease.bell(u, 0.8f, 0.86f) + 0.4f * Ease.bell(u, 0.86f, 0.9f);
        out[1] = r + (u > 0.8f ? 0.5f * Ease.bell(u, 0.8f, 0.9f) : 0);
        out[2] = Ease.lerp(Y_TRACK + 0.34f, Y_POCK + 0.34f, Ease.inQuad(drop)) + hop;
        return true;
    }

    static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, RouletteView v, Pose p, DrawList out) {
        // baladeuse : elle vacille de temps en temps ; la plaque au pochoir luit faiblement dessous
        float lampK = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.95f ? 0.35f : 0.92f + 0.08f * (float) Math.sin(s.time * 1.1f);
        out.solid(p, bulb, lampK);
        out.solid(p, signGlow, 0.3f * lampK);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2], 3f, 0, 0xFFC880, 0.3f * lampK);
        int n = v.zeros == 1 ? 37 : 38;
        Mesh[] head = n == 37 ? headColors37 : headColors38;
        float w = wheelAngle(v);
        float step = 360f / n;
        // ---- cylindre : cases repeintes a la main (rouge sang, noir, vert passe), la peinture s'ecaille
        p.push().translate(WX, TOP, WZ).rotY(w);
        out.solidTinted(p, head[0], 0, 0.66f, 0.13f, 0.11f);
        out.solidTinted(p, head[1], 0, 0.12f, 0.12f, 0.12f);
        out.solidTinted(p, head[2], 0, 0.2f, 0.47f, 0.24f);
        out.solid(p, head[3], 0);
        out.solid(p, turret, 0);
        if (!s.item) {
            int[] wheel = Roulette.wheel(v.zeros);
            float rMid = (R_RING_IN + R_RING_OUT) / 2;
            // largeur disponible par case (arc) : les numeros ne doivent pas se toucher
            float arc = (float) (2 * Math.PI * rMid / n) * 0.8f;
            for (int i = 0; i < n; i++) {
                String lab = Roulette.label(wheel[i]);
                p.push().rotY(i * step).translate(0, Y_RING + 0.12f, rMid).rotX(-82);
                out.text(p, lab, 0, 0, 0, fit(lab, 0.72f, arc, false), 0xEDE4CC, 1f, DrawList.T_CENTER);
                p.pop();
            }
        }
        p.pop();
        // ---- bille
        float[] bp = new float[3];
        if (!s.item && ballPos(v, bp)) {
            double a = Math.toRadians(bp[0]);
            p.push().translate(WX + bp[1] * (float) Math.sin(a), TOP + bp[2], WZ + bp[1] * (float) Math.cos(a));
            out.solid(p, ball, 0.12f);
            p.pop();
        }
        if (s.item) return;
        // ---- libelles des zeros (0, ou 0 et 00 selon la config)
        for (int z : v.zeros == 1 ? new int[]{0} : new int[]{0, Roulette.DOUBLE_ZERO}) {
            float[] r = RouletteLayout.rect(z, v.zeros);
            if (r == null) continue;
            p.push().translate(LX + (r[0] + r[2]) / 2 * MAT_S, TOP + 0.03f, LZ + (r[1] + r[3]) / 2 * MAT_S).rotX(-90);
            out.text(p, Roulette.label(z), 0, 0, 0, 1.2f * MAT_S, 0xE4DCC0, 1f, DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x14301C);
            p.pop();
        }
        // ---- jetons sur le tapis
        boolean result = v.phase == RoundView.RESULT && v.number >= 0;
        for (int spot = 0; spot < Roulette.SPOTS; spot++) {
            long total = v.totals[spot];
            if (total <= 0) continue;
            float[] r = RouletteLayout.rect(spot, v.zeros);
            if (r == null) continue;
            float cx = LX + (r[0] + r[2]) / 2 * MAT_S, cz = LZ + (r[1] + r[3]) / 2 * MAT_S;
            boolean win = result && Roulette.wins(spot, v.number);
            if (result && !win) {
                // mises perdantes ramassees vers la roue
                float k = Ease.inOutSine(Ease.range(v.sincePhase, 20, 44));
                if (k >= 1) continue;
                cx = Ease.lerp(cx, WX + 3, k);
                cz = Ease.lerp(cz, WZ - 6, k);
            }
            chips.amount(p, out, total, cx, TOP + 0.04f, cz, 1.3f, 2);
            if (v.mine[spot] > 0) {
                p.push().translate(cx, TOP + 0.05f, cz).scale(1.5f, 1, 1.5f);
                out.additive(p, highlight, GOLD_RGB, 0.35f + 0.2f * s.pulse(0.2f));
                p.pop();
            }
            if (win) {
                long gain = total * (Roulette.returnMultiplier(spot) - 1);
                float k = Ease.outCubic(Ease.range(v.sincePhase, 30, 50));
                if (k > 0) chips.amount(p, out, gain, Ease.lerp(WX + 3, cx + 1.2f, k), TOP + 0.04f + 2 * Ease.bell(k, 0, 1), Ease.lerp(WZ - 6, cz, k), 1.3f, 2);
            }
        }
        // ---- numero gagnant : une cartouche posee sur la case, qui s'eclaire
        if (result) {
            float[] r = RouletteLayout.rect(v.number, v.zeros);
            if (r != null) {
                float cx = LX + (r[0] + r[2]) / 2 * MAT_S, cz = LZ + (r[1] + r[3]) / 2 * MAT_S;
                float drop = Ease.outBounce(Ease.range(v.sincePhase, 6, 22));
                p.push().translate(cx, TOP + 0.05f + 4 * (1 - drop), cz);
                out.solid(p, dolly, 0.1f);
                p.pop();
                p.push().translate(cx, TOP + 0.04f, cz).scale((r[2] - r[0]) * MAT_S, 1, (r[3] - r[1]) * MAT_S);
                out.additive(p, highlight, 0xFFE8B0, 0.4f + 0.22f * (float) Math.sin(s.time * 0.5f));
                p.pop();
            }
            out.billboard(p, Sprite.FLARE, WX, TOP + 3, WZ, 7f * (1 - Ease.range(v.sincePhase, 60, 110)), s.time * 2, Roulette.color(v.number) == 0x1B1B1F ? 0xFFF0D0 : Roulette.color(v.number), 0.3f);
        }
        // ---- boitier : vieil ecran au phosphore (compte a rebours, annonces, derniers numeros)
        float bx = BOX_X, bz = BOX_Z + 0.65f;
        String head1;
        int c1 = AMBER;
        if (v.phase == RoundView.BETTING) {
            head1 = (int) Math.ceil(v.countdown) + " s";
            c1 = v.countdown < 5 ? 0xFF5A4A : AMBER;
        } else if (v.phase == RoundView.RUNNING) {
            head1 = v.spinTime > v.spinTicks * 0.55f ? "RIEN NE" : "FAITES";
        } else if (result) {
            head1 = Roulette.label(v.number);
            c1 = v.number == 0 || v.number == Roulette.DOUBLE_ZERO ? PHOSPHOR : Roulette.isRed(v.number) ? 0xFF5A4A : 0xE8E0CC;
        } else {
            head1 = "MISEZ";
        }
        String head2 = v.phase == RoundView.RUNNING ? (v.spinTime > v.spinTicks * 0.55f ? "VA PLUS" : "VOS JEUX") : null;
        out.text(p, head1, bx, TOP + 19.1f, bz, fit(head1, result ? 1.6f : 0.9f, 3.2f, true), c1, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        if (head2 != null) out.text(p, head2, bx, TOP + 18f, bz, fit(head2, 0.9f, 3.2f, true), c1, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        for (int i = 0; i < Math.min(8, v.history.length); i++) {
            int num = v.history[i];
            int col = num == 0 || num == Roulette.DOUBLE_ZERO ? PHOSPHOR : Roulette.isRed(num) ? 0xFF5A4A : 0xD8D0BC;
            float x = Roulette.isRed(num) ? bx - 0.7f : (num == 0 || num == Roulette.DOUBLE_ZERO ? bx : bx + 0.7f);
            out.text(p, Roulette.label(num), x, TOP + 16.4f - i * 0.95f, bz, i == 0 ? 0.8f : 0.62f, col, i == 0 ? 1f : 0.8f, DrawList.T_CENTER | (i == 0 ? DrawList.T_BOLD : 0));
        }
    }

    // =================================================================== camera et zones cliquables
    @Override
    public void camera(RouletteView v, Scene s, int mode, Shot out) {
        if (mode == CAM_MENU) {
            // vue plongeante sur le tapis de mise (la roue reste visible a gauche)
            float cx = LX + RouletteLayout.W * MAT_S / 2 - 2, cz = LZ + RouletteLayout.EVEN_V1 * MAT_S / 2;
            out.set(cx, TOP + 36, cz + 27, cx, TOP, cz + 1.5f, 46);
        } else {
            // gros plan sur le cylindre pendant le lancer
            out.set(WX, TOP + 26, WZ + 19, WX, TOP + 0.5f, WZ, 44);
        }
    }

    @Override
    public void hotspots(RouletteView v, Scene s, Hotspots out) {
        for (int spot = 0; spot < Roulette.SPOTS; spot++) {
            float[] r = RouletteLayout.rect(spot, v.zeros);
            if (r == null) continue;
            out.flat(spot, LX + r[0] * MAT_S, LZ + r[1] * MAT_S, LX + r[2] * MAT_S, LZ + r[3] * MAT_S, TOP + 0.06f);
        }
    }
    // =================================================================== sons et particules
    private float lastRel = Float.NaN;

    @Override
    public void tickFx(Scene s, RouletteView v, FxSink fx, Random r) {
        if (v.phase == RoundView.BETTING && v.countdown <= 5 && v.countdown > 0 && Math.abs(v.countdown - Math.round(v.countdown)) < 0.026f) {
            fx.sound(Snd.COUNTDOWN, X0 + 1.2f, TOP + 19, Z0 + 2, 0.5f, 1.2f);
        }
        if (v.phase == RoundView.RUNNING) {
            float u = v.spinTime / v.spinTicks;
            if (v.spinTime < 1) fx.sound(Snd.WHOOSH, WX, TOP + 2, WZ, 0.6f, 1.4f);
            if (u < 0.6f && ((int) v.spinTime) % 3 == 0) fx.sound(Snd.BALL_ROLL, WX, TOP + 2, WZ, 0.35f * (1 - u), 1.6f - u);
            if (u >= 0.6f && u < 0.95f && r.nextInt(3) == 0) fx.sound(Snd.BALL_CLACK, WX, TOP + 1, WZ, 0.5f, 1.3f + r.nextFloat() * 0.4f);
            if (Math.abs(v.spinTime - v.spinTicks * 0.3f) < 0.5f) fx.sound(Snd.BELL, X0 + 1, TOP + 18, Z0 + 2, 0.4f, 1.5f);
        }
        if (v.phase == RoundView.RESULT && v.sincePhase < 1 && v.number >= 0) {
            fx.sound(Snd.BALL_CLACK, WX, TOP + 1, WZ, 1f, 0.8f);
            boolean anyWin = false;
            for (int spot = 0; spot < Roulette.SPOTS; spot++) if (v.mine[spot] > 0 && Roulette.wins(spot, v.number)) anyWin = true;
            boolean anyBet = false;
            for (int spot = 0; spot < Roulette.SPOTS; spot++) if (v.mine[spot] > 0) anyBet = true;
            if (anyBet) fx.sound(anyWin ? Snd.WIN_SMALL : Snd.LOSE, WX, TOP + 2, WZ, 1f, 1f);
        }
        if (v.phase == RoundView.RESULT && Math.abs(v.sincePhase - 22) < 0.5f) fx.sound(Snd.CHIP, LX + 12, TOP, LZ + 8, 0.8f, 1f);
        if (v.phase == RoundView.RESULT && v.sincePhase > 30 && v.sincePhase < 31) fx.sound(Snd.COINS, LX + 12, TOP, LZ + 8, 0.6f, 1.1f);
    }

    // =================================================================== vues
    @Override
    public RouletteView newView() {
        RouletteView v = new RouletteView();
        v.history = new int[]{17, 0, 32, 5, 26, 11, 37, 20};
        return v;
    }

    @Override
    public RouletteView demoView(float t) {
        RouletteView v = newView();
        v.totals[17] = 50;
        v.totals[Roulette.RED_BET] = 1200;
        v.totals[Roulette.DOZEN2] = 300;
        v.totals[Roulette.COL1] = 25;
        v.totals[0] = 10;
        v.totals[Roulette.ODD] = 5000;
        v.mine[17] = 50;
        v.mine[Roulette.RED_BET] = 200;
        v.spinTicks = 220;
        v.wheelStart = 40;
        v.ballStart = 200;
        v.number = 5;
        if (t < 30) {
            v.phase = RoundView.BETTING;
            v.countdown = (30 - t) / 20f + 2;
            v.restAngle = 40;
            v.sinceRest = t;
        } else if (t < 30 + 230) {
            v.phase = RoundView.RUNNING;
            v.spinTime = t - 30;
            v.sincePhase = t - 30;
        } else {
            v.phase = RoundView.RESULT;
            v.spinTime = t - 30;
            v.sincePhase = t - 260;
            v.history = new int[]{5, 17, 0, 32, 5, 26, 11, 37};
        }
        return v;
    }

    @Override
    public int demoLength() {
        return 330;
    }
}
