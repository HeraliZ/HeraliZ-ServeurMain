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
import fr.heraliz.casino.game.slot.SlotGame;
import fr.heraliz.casino.game.slot.SlotRules;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Machine a sous "Fruits d'Or" : bandit manchot des annees 50 en laque rouge et chrome, fronton en arche
 * aux ampoules chenillard, trois rouleaux mecaniques derriere la vitre, levier a boule rouge, bac a pieces.
 * Le levier s'abaisse, les rouleaux tournent (flou de vitesse) et s'arretent un par un avec un rebond ;
 * en cas de gain les ampoules clignotent et les pieces tombent dans le bac.
 */
public final class FruitsDesign extends MachineDesign<SlotView> {
    // ---- atlas (x, y, largeur, hauteur) : partage avec tools/textures (PaintFruits)
    public static final int[] R_RED = {0, 0, 512, 512};
    public static final int[] R_BLACK = {512, 0, 256, 256};
    public static final int[] R_CHROME = {768, 0, 256, 256};
    public static final int[] R_GOLD = {512, 256, 256, 256};
    public static final int[] R_CREAM = {768, 256, 256, 128};
    public static final int[] R_DARK = {768, 384, 128, 64};
    public static final int[] R_GLASS = {768, 448, 128, 64};
    public static final int[] R_BULB = {896, 384, 128, 128};
    public static final int[] R_MARQUEE = {0, 512, 512, 256};
    public static final int[] R_PANEL = {0, 768, 512, 256};
    public static final int[] R_SYM = {512, 512, 512, 256};
    public static final int[] R_SYMBLUR = {512, 768, 512, 256};

    private static final Mat RED = tri(R_RED, 24), BLACK = tri(R_BLACK, 16), CHROME = tri(R_CHROME, 12), GOLD = tri(R_GOLD, 12);
    private static final Mat CREAM = tri(R_CREAM, 8), DARK = tri(R_DARK, 8), GLASS = fit(R_GLASS), BULB = fit(R_BULB);
    private static final Mat MARQUEE = fit(R_MARQUEE), PANEL = fit(R_PANEL);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }

    /** Cellule d'un symbole (4 x 2 cases de 128 px). */
    static Mat symbol(int[] sheet, int s) {
        return Mat.fit(sheet[0] + (s % 4) * 128, sheet[1] + (s / 4) * 128, 128, 128);
    }

    // ---- cotes (px)
    static final float X0 = 1.5f, X1 = 14.5f, Z0 = 2f, Z1 = 14.5f;
    static final float WX0 = 3f, WX1 = 13f, WY0 = 20.4f, WY1 = 28.6f;
    static final float REEL_Y = 24.5f, REEL_Z = 8f, REEL_R = 5.1f, REEL_W = 3.0f;
    static final float[] REEL_X = {4.65f, 8f, 11.35f};
    static final float HUB_Y = 24f, HUB_Z = 8.5f, ARM = 8.2f;
    static final float ARCH_Y = 34f, ARCH_RX = 6.5f, ARCH_RY = 5.5f;
    static final int BULBS = 15;
    static final int GOLD_RGB = 0xFFD36A, RED_RGB = 0xFF4D5E, WARM = 0xFFE3A0;

    private Mesh body = Mesh.EMPTY, bodyGlow = Mesh.EMPTY, lever = Mesh.EMPTY, bulb = Mesh.EMPTY, bulbGlow = Mesh.EMPTY;
    private Mesh payline = Mesh.EMPTY, paylineGlow = Mesh.EMPTY, glass = Mesh.EMPTY, display = Mesh.EMPTY;
    private float[][] bulbPos;
    /** Rouleaux par bande (net / flou), reconstruits si la config change les bandes. */
    private final Map<String, Mesh[]> reels = new HashMap<>();

    public FruitsDesign() {
        super("machine_a_sous", RED_RGB, GOLD_RGB);
        labelY = 45;
        itemScale = 0.42f;
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- socle noir et plinthe chromee
        b.bevelBox(1.1f, 0, 1.6f, 14.9f, 2.0f, 14.9f, 0.3f, BLACK);
        b.bevelBox(1.3f, 0.4f, 14.85f, 14.7f, 1.4f, 15.05f, 0.1f, CHROME);
        // ---- caisson bas laque rouge, panneau des gains encadre d'or
        b.bevelBox(X0, 2, Z0, X1, 17, Z1, 0.5f, RED);
        b.push().translate(8, 11.2f, Z1 + 0.02f);
        b.decalZ(PANEL, 0, 0, 0, 10.6f, 7.4f);
        frame(b, 10.6f, 7.4f, 0.28f, 0.2f, GOLD);
        b.pop();
        // ---- bac a pieces chrome
        b.face(DARK, v(0, 0, 1), p(4.6f, 2.9f, Z1 + 0.03f), p(11.4f, 2.9f, Z1 + 0.03f), p(11.4f, 5.4f, Z1 + 0.03f), p(4.6f, 5.4f, Z1 + 0.03f));
        b.bevelBox(4f, 2.2f, Z1 - 0.2f, 12f, 2.7f, 17.3f, 0.12f, CHROME);
        b.bevelBox(4f, 2.7f, 16.9f, 12f, 4.4f, 17.4f, 0.15f, CHROME);
        b.bevelBox(4f, 2.7f, Z1 - 0.2f, 4.45f, 4.4f, 17.4f, 0.1f, CHROME);
        b.bevelBox(11.55f, 2.7f, Z1 - 0.2f, 12f, 4.4f, 17.4f, 0.1f, CHROME);
        // ---- rebord (pupitre) chrome, fente a pieces et boutons
        b.bevelBox(X0 - 0.25f, 17, Z0 - 0.25f, X1 + 0.25f, 18.4f, Z1 + 1.3f, 0.35f, CHROME);
        b.bevelBox(6.8f, 18.4f, 15.0f, 9.2f, 18.55f, 15.5f, 0.05f, BLACK);
        for (int k = 0; k < 2; k++) {
            b.push().translate(k == 0 ? 4.2f : 11.8f, 18.4f, 15.2f);
            b.cylinder(0.75f, 0, 0.3f, seg(14), GOLD, null, k == 0 ? GOLD : RED);
            b.pop();
        }
        // ---- caisson des rouleaux : facade percee de la fenetre
        b.bevelBox(X0, 18.4f, Z0, X1, 30, Z1, 0.5f, RED, null, MeshBuilder.SKIP_PZ);
        float fx0 = X0 + 0.5f, fx1 = X1 - 0.5f, fy0 = 18.9f, fy1 = 29.5f;
        float[] nz = v(0, 0, 1);
        b.face(RED, nz, p(fx0, fy0, Z1), p(fx1, fy0, Z1), p(fx1, WY0, Z1), p(fx0, WY0, Z1));
        b.face(RED, nz, p(fx0, WY1, Z1), p(fx1, WY1, Z1), p(fx1, fy1, Z1), p(fx0, fy1, Z1));
        b.face(RED, nz, p(fx0, WY0, Z1), p(WX0, WY0, Z1), p(WX0, WY1, Z1), p(fx0, WY1, Z1));
        b.face(RED, nz, p(WX1, WY0, Z1), p(fx1, WY0, Z1), p(fx1, WY1, Z1), p(WX1, WY1, Z1));
        // bords de la facade (biseau autour de la zone percee)
        b.bevelBox(X0, 18.4f, Z1 - 0.5f, X0 + 0.5f, 30, Z1, 0.2f, RED);
        b.bevelBox(X1 - 0.5f, 18.4f, Z1 - 0.5f, X1, 30, Z1, 0.2f, RED);
        // embrasure sombre de la fenetre et fond derriere les rouleaux
        float zb = 10.4f;
        b.face(DARK, v(0, 1, 0), p(WX0, WY0, zb), p(WX1, WY0, zb), p(WX1, WY0, Z1), p(WX0, WY0, Z1));
        b.face(DARK, v(0, -1, 0), p(WX0, WY1, zb), p(WX0, WY1, Z1), p(WX1, WY1, Z1), p(WX1, WY1, zb));
        b.face(DARK, v(1, 0, 0), p(WX0, WY0, zb), p(WX0, WY0, Z1), p(WX0, WY1, Z1), p(WX0, WY1, zb));
        b.face(DARK, v(-1, 0, 0), p(WX1, WY0, zb), p(WX1, WY1, zb), p(WX1, WY1, Z1), p(WX1, WY0, Z1));
        b.face(DARK, nz, p(WX0, WY0 - 1, 2.6f), p(WX1, WY0 - 1, 2.6f), p(WX1, WY1 + 1, 2.6f), p(WX0, WY1 + 1, 2.6f));
        // separations chromees entre les rouleaux
        for (int k = 0; k < 2; k++) {
            float x = (REEL_X[k] + REEL_X[k + 1]) / 2;
            b.bevelBox(x - 0.16f, WY0, zb, x + 0.16f, WY1, Z1 - 0.1f, 0.05f, CHROME);
        }
        // cadre chrome de la fenetre, fleches d'or de la ligne de gain
        b.bevelBox(WX0 - 0.45f, WY1, Z1 - 0.2f, WX1 + 0.45f, WY1 + 0.55f, Z1 + 0.35f, 0.15f, CHROME);
        b.bevelBox(WX0 - 0.45f, WY0 - 0.55f, Z1 - 0.2f, WX1 + 0.45f, WY0, Z1 + 0.35f, 0.15f, CHROME);
        b.bevelBox(WX0 - 0.45f, WY0, Z1 - 0.2f, WX0, WY1, Z1 + 0.35f, 0.12f, CHROME);
        b.bevelBox(WX1, WY0, Z1 - 0.2f, WX1 + 0.45f, WY1, Z1 + 0.35f, 0.12f, CHROME);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.push().translate(sd < 0 ? WX0 - 0.55f : WX1 + 0.55f, REEL_Y, Z1 + 0.36f).rotateZ(sd < 0 ? -90 : 90);
            b.prism(new float[][]{{-0.55f, 0}, {0.55f, 0}, {0, 0.75f}}, 0.2f, GOLD, GOLD);
            b.pop();
        }
        // ---- baguettes chromees verticales des aretes avant
        for (float x : new float[]{X0 + 0.05f, X1 - 0.05f}) {
            b.push().translate(x, 2.2f, Z1 - 0.05f);
            b.cylinder(0.32f, 0, 27.6f, seg(8), CHROME, CHROME, CHROME);
            b.pop();
        }
        // ---- fronton en arche : dalle a l'enseigne, jonc chrome, couronne d'or
        float[][] arch = archOutline(seg(24));
        b.push().translate(0, 0, 10.2f);
        b.slab(arch, 8.4f, 0.35f, true, MARQUEE, RED, RED);
        b.pop();
        float[][] rim = new float[arch.length + 1][];
        for (int i = 0; i <= arch.length; i++) {
            float[] q = arch[i % arch.length];
            rim[i] = new float[]{q[0], q[1], 14.45f};
        }
        b.tube(rim, fill(rim.length, 0.34f), seg(8), CHROME, false, false, 0, false);
        // afficheur du jackpot (noir, encastre dans le bas de l'enseigne)
        b.bevelBox(3.4f, 30.5f, 14.2f, 12.6f, 32.7f, 14.75f, 0.2f, CHROME);
        // couronne d'or au sommet
        b.push().translate(8, ARCH_Y + ARCH_RY - 0.2f, 10.2f);
        crown(b);
        b.pop();
        // ---- moyeu du levier (partie fixe)
        b.push().translate(X1, HUB_Y, HUB_Z).rotateZ(-90);
        b.cylinder(1.35f, 0, 0.5f, seg(18), CHROME, CHROME, CHROME);
        b.pop();
        Occlusion.bake(b, 0.35f, 3f, 0.45f, 0f);
        body = b.build();

        // enseigne lumineuse (calque de l'atlas) : face avant de l'arche
        MeshBuilder g = new MeshBuilder();
        g.push().translate(0, 0, 10.2f);
        g.slab(arch, 8.45f, 0.35f, true, MARQUEE, null, BLACK);
        g.pop();
        bodyGlow = g.build().filter((u, vv) -> MARQUEE.contains(u, vv));

        MeshBuilder d = new MeshBuilder();
        d.bevelBox(3.7f, 30.75f, 14.7f, 12.3f, 32.45f, 14.85f, 0.1f, DARK);
        display = d.build();

        // ---- levier (repere : axe du moyeu, bras vers le haut)
        MeshBuilder lb = new MeshBuilder();
        lb.push().rotateZ(-90);
        lb.cylinder(0.95f, 0, 1.2f, seg(16), CHROME, CHROME, GOLD);
        lb.pop();
        float[][] armPath = {p(0.75f, 0, 0), p(1.0f, 1.5f, 0), p(1.05f, 4.5f, -0.2f), p(1.05f, ARM, -0.4f)};
        lb.tube(armPath, new float[]{0.42f, 0.36f, 0.32f, 0.3f}, seg(10), CHROME, true, true, 0, false);
        lb.push().translate(1.05f, ARM + 0.9f, -0.4f);
        lb.sphere(1.15f, seg(16), seg(10), RED);
        lb.pop();
        lb.push().translate(1.05f, ARM - 0.1f, -0.4f);
        lb.torus(0.45f, 0.14f, seg(12), 5, 0, 360, GOLD);
        lb.pop();
        lever = lb.build();

        // ---- ampoules du fronton
        bulbPos = new float[BULBS][];
        for (int i = 0; i < BULBS; i++) {
            float a = (float) Math.toRadians(180 - 180.0 * i / (BULBS - 1));
            bulbPos[i] = new float[]{8 + (ARCH_RX - 0.95f) * (float) Math.cos(a), ARCH_Y + (ARCH_RY - 0.95f) * (float) Math.sin(a), 14.55f};
        }
        MeshBuilder bb = new MeshBuilder();
        bb.sphere(0.42f, seg(10), 6, BULB);
        bulb = bb.build();
        MeshBuilder bg = new MeshBuilder();
        bg.sphere(0.62f, seg(10), 6, BULB);
        bulbGlow = bg.build();

        // ---- ligne de gain et vitre
        MeshBuilder pl = new MeshBuilder();
        pl.bevelBox(WX0, REEL_Y - 0.05f, 13.6f, WX1, REEL_Y + 0.05f, 13.7f, 0.02f, BULB);
        payline = pl.build();
        MeshBuilder pg = new MeshBuilder();
        pg.bevelBox(WX0, REEL_Y - 0.12f, 13.55f, WX1, REEL_Y + 0.12f, 13.75f, 0.04f, BULB);
        paylineGlow = pg.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(WX0, WY0, Z1 - 0.12f), p(WX1, WY0, Z1 - 0.12f), p(WX1, WY1, Z1 - 0.12f), p(WX0, WY1, Z1 - 0.12f));
        glass = gl.build();
    }

    private static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    /** Contour de l'arche (sens trigonometrique) : bas droit, flancs, demi-ellipse. */
    static float[][] archOutline(int segs) {
        float[][] out = new float[segs + 3][];
        out[0] = new float[]{8 - ARCH_RX, 30f};
        out[1] = new float[]{8 + ARCH_RX, 30f};
        for (int i = 0; i <= segs; i++) {
            double a = Math.PI * i / segs;
            out[i + 2] = new float[]{8 + ARCH_RX * (float) Math.cos(a), ARCH_Y + ARCH_RY * (float) Math.sin(a)};
        }
        return out;
    }

    /** Cadre d'or plat autour d'un decal (repere : centre du decal, face +Z). */
    static void frame(MeshBuilder b, float w, float h, float t, float d, Mat m) {
        b.bevelBox(-w / 2 - t, h / 2, 0, w / 2 + t, h / 2 + t, d, 0.06f, m);
        b.bevelBox(-w / 2 - t, -h / 2 - t, 0, w / 2 + t, -h / 2, d, 0.06f, m);
        b.bevelBox(-w / 2 - t, -h / 2, 0, -w / 2, h / 2, d, 0.06f, m);
        b.bevelBox(w / 2, -h / 2, 0, w / 2 + t, h / 2, d, 0.06f, m);
    }

    /** Petite couronne d'or (5 pointes a perles). */
    private void crown(MeshBuilder b) {
        b.push().translate(0, 0.2f, 0);
        b.cylinder(1.9f, 0, 0.7f, seg(18), GOLD, GOLD, GOLD);
        b.pop();
        for (int k = 0; k < 5; k++) {
            float x = -1.6f + k * 0.8f;
            float hgt = k == 2 ? 2.3f : (k == 1 || k == 3 ? 1.7f : 1.3f);
            b.push().translate(x, 0.8f, 0);
            b.spike(0.7f, 0.7f, hgt, GOLD);
            b.pop();
            b.push().translate(x, 0.8f + hgt + 0.15f, 0);
            b.sphere(0.24f, seg(8), 5, GOLD);
            b.pop();
        }
        b.push().translate(0, 0.55f, 1.85f);
        b.sphere(0.32f, seg(8), 5, RED);
        b.pop();
    }

    /** Rouleaux (nets et flous) pour des bandes donnees. */
    private Mesh[] reels(SlotRules r) {
        StringBuilder key = new StringBuilder();
        for (int[] s : r.strips) key.append(java.util.Arrays.toString(s));
        return reels.computeIfAbsent(key.toString(), k -> {
            Mesh[] out = new Mesh[6];
            for (int i = 0; i < 3; i++) {
                out[i] = reel(r.strips[i], R_SYM);
                out[i + 3] = reel(r.strips[i], R_SYMBLUR);
            }
            return out;
        });
    }

    /** Tambour d'axe X centre a l'origine : une face par arret (arret i a l'angle -i * pas). */
    private Mesh reel(int[] strip, int[] sheet) {
        MeshBuilder b = new MeshBuilder();
        int n = strip.length;
        float step = 360f / n;
        float hw = REEL_W / 2;
        for (int i = 0; i < n; i++) {
            Mat m = symbol(sheet, Math.max(0, Math.min(7, strip[i])));
            double a0 = Math.toRadians(-i * step + step / 2), a1 = Math.toRadians(-i * step - step / 2);
            float y0 = REEL_R * (float) Math.sin(a0), z0 = REEL_R * (float) Math.cos(a0);
            float y1 = REEL_R * (float) Math.sin(a1), z1 = REEL_R * (float) Math.cos(a1);
            float[][] pts = {p(-hw, y1, z1), p(hw, y1, z1), p(hw, y0, z0), p(-hw, y0, z0)};
            float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
            double am = Math.toRadians(-i * step);
            float[] nrm = v(0, (float) Math.sin(am), (float) Math.cos(am));
            float[][] nn = {v(0, (float) Math.sin(a1), (float) Math.cos(a1)), v(0, (float) Math.sin(a1), (float) Math.cos(a1)),
                    v(0, (float) Math.sin(a0), (float) Math.cos(a0)), v(0, (float) Math.sin(a0), (float) Math.cos(a0))};
            b.poly(pts, uv, nn, nrm);
        }
        // flancs du tambour
        b.push().rotateZ(90);
        b.disc(REEL_R + 0.05f, hw, seg(n * 2), CREAM, true);
        b.disc(REEL_R + 0.05f, -hw, seg(n * 2), CREAM, false);
        b.pop();
        return b.build();
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
        out.solid(p, display, 0);
    }

    @Override
    protected void animate(Scene s, SlotView v, Pose p, DrawList out) {
        float t = s.since(v.spinStart);
        boolean active = !s.item && t >= 0 && t < SlotGame.END + SlotGame.CELEBRATE;
        boolean shown = t >= SlotGame.STOP[2];
        boolean won = active && shown && v.win > 0;
        float celebrate = won ? Ease.range(t, SlotGame.STOP[2], SlotGame.STOP[2] + 4) * (1 - Ease.range(t, SlotGame.END + SlotGame.CELEBRATE - 20, SlotGame.END + SlotGame.CELEBRATE)) : 0;
        // ---- enseigne lumineuse
        float marquee = 0.75f + 0.25f * s.pulse(0.09f) + celebrate * 0.6f * (0.5f + 0.5f * (float) Math.sin(s.time * 1.4f));
        out.solid(p, bodyGlow, Math.min(1f, marquee));
        // ---- rouleaux
        Mesh[] rm = reels(v.rules);
        for (int i = 0; i < 3; i++) {
            float ang = active ? v.reelAngle(i, t) : v.stops[i] * 360f / v.rules.strips[i].length;
            float speed = active ? v.reelSpeed(i, t) : 0;
            p.push().translate(REEL_X[i], REEL_Y, REEL_Z).rotX(-ang);
            out.solid(p, speed > 14f ? rm[i + 3] : rm[i], 0);
            p.pop();
        }
        // ---- ligne de gain
        float line = won ? 0.55f + 0.3f * (float) Math.sin(s.time * 1.2f) : 0.22f + 0.08f * s.pulse(0.15f);
        out.solid(p, payline, 0);
        out.additive(p, paylineGlow, won ? GOLD_RGB : RED_RGB, line);
        // ---- vitre
        out.translucent(p, glass, 0xFFFFFF, 0.55f);
        // ---- levier
        float pull = 0;
        if (active && t < 16) {
            if (t < 5) pull = Ease.inCubic(t / 5f) * 95f;
            else pull = 95f * (1 - Ease.outBack(Ease.clamp01((t - 5) / 9f), 2.2f));
        }
        p.push().translate(X1, HUB_Y, HUB_Z).rotX(-8 + pull);
        out.solid(p, lever, 0);
        p.pop();
        // ---- ampoules : chenillard au repos, clignotement general en cas de gain
        for (int i = 0; i < BULBS; i++) {
            float k;
            if (celebrate > 0) {
                k = ((int) (s.time / 3) + i) % 2 == 0 ? 1f : 0.25f;
            } else if (active && t < SlotGame.STOP[2]) {
                k = ((int) (t * 1.2f) - i) % 3 == 0 ? 1f : 0.2f;
            } else {
                float ph = (s.time * 0.25f - i * 0.9f) % BULBS;
                if (ph < 0) ph += BULBS;
                k = 0.3f + 0.7f * Math.max(0, 1 - Math.min(ph, BULBS - ph) / 2.2f);
            }
            float[] q = bulbPos[i];
            p.push().translate(q[0], q[1], q[2]);
            out.solidTinted(p, bulb, 0, 0.7f + 0.3f * k, 0.7f + 0.3f * k, 0.6f + 0.4f * k);
            out.additive(p, bulbGlow, i % 2 == 0 ? WARM : GOLD_RGB, 0.25f + 0.75f * k);
            p.pop();
        }
        // ---- afficheur : jackpot, ou gain apres l'arret des rouleaux
        if (!s.item) {
            if (won && ((int) (t / 30)) % 2 == 0) {
                String txt = (v.jackpot ? "JACKPOT " : "GAIN ") + fmt(v.win);
                out.text(p, txt, 8, 31.6f, 14.9f, fit(txt, 0.95f, 8.2f, true), GOLD_RGB, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            } else {
                String txt = fmt(v.jackpotValue);
                out.text(p, txt, 8, 31.6f, 14.9f, fit(txt, 1.0f, 8.2f, false), 0xFF6A4A, 0.95f, DrawList.T_CENTER);
            }
        }
        if (celebrate > 0) {
            out.billboard(p, Sprite.FLARE, 8, REEL_Y, 15f, 7f * celebrate, s.time * 2, GOLD_RGB, 0.35f * celebrate);
            if (v.jackpot || v.multiplier >= 20) {
                out.rays(p, 8, ARCH_Y + 1, 12f, 12, 24f, 2.2f, s.time * 1.5f, GOLD_RGB, 0.45f * celebrate);
            }
        }
    }

    // =================================================================== camera
    @Override
    public void camera(SlotView v, Scene s, int mode, Shot out) {
        if (mode == CAM_MENU) out.set(8, 29, 37, 8, 25, 9, 50);
        else out.set(8, 27.5f, 31, 8, 24.5f, 9, 48);
    }
    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, SlotView v, FxSink fx, Random r) {
        float t = s.since(v.spinStart);
        if (t < 0 || t > SlotGame.END + SlotGame.CELEBRATE) {
            if (r.nextInt(90) == 0) {
                float[] q = bulbPos[r.nextInt(BULBS)];
                fx.particle(FxSink.SPARK, q[0], q[1], q[2] + 0.4f, 0, 0.05f, 0.05f, GOLD_RGB, 0.9f, 14);
            }
            return;
        }
        if (at(t, 0)) fx.sound(Snd.LEVER, X1 + 1, HUB_Y + 6, HUB_Z, 0.9f, 1f);
        if (at(t, 3)) fx.sound(Snd.REEL_START, 8, REEL_Y, 12, 0.7f, 1.1f);
        if (t > SlotGame.LEVER && t < SlotGame.STOP[2] && ((int) t) % 3 == 0) {
            fx.sound(Snd.REEL_TICK, 8, REEL_Y, 12, 0.25f, 1.4f + r.nextFloat() * 0.2f);
        }
        for (int i = 0; i < 3; i++) {
            if (at(t, SlotGame.STOP[i])) {
                fx.sound(Snd.REEL_STOP, REEL_X[i], REEL_Y, 12, 0.8f, 0.9f + i * 0.08f);
                for (int k = 0; k < 3; k++) {
                    fx.particle(FxSink.SPARK, REEL_X[i] + rnd(r, 1.2f), WY0 - 0.4f, 15f, rnd(r, 0.04f), 0.03f, 0.05f, WARM, 0.5f, 10);
                }
            }
        }
        if (v.win > 0 && t >= SlotGame.STOP[2]) {
            float k = t - SlotGame.STOP[2];
            if (at(k, 1)) {
                Snd snd = v.jackpot ? Snd.WIN_JACKPOT : v.multiplier >= 20 ? Snd.WIN_BIG : Snd.WIN_SMALL;
                fx.sound(snd, 8, 26, 14, 1f, 1f);
                fx.sound(Snd.COINS, 8, 4, 16, 0.9f, 1f);
                burst(fx, r, 8, ARCH_Y + 1, 15.5f, GOLD_RGB, WARM, v.jackpot ? 50 : (v.multiplier >= 10 ? 20 : 8), 1.2f, 1.1f);
            }
            int coins = (int) Math.min(40, 6 + v.multiplier * 2);
            if (k < coins && ((int) k) % 2 == 0) {
                fx.particle(FxSink.CONFETTI, 8 + rnd(r, 2.5f), 5.5f, 15.2f, rnd(r, 0.08f), -0.05f, 0.03f, r.nextBoolean() ? 0xFFC83A : 0xE0A020, 0.9f, 30);
                if (((int) k) % 6 == 0) fx.sound(Snd.COINS, 8, 4, 16, 0.25f, 1.2f + r.nextFloat() * 0.3f);
            }
            if (v.jackpot && k < 60 && r.nextInt(2) == 0) {
                fx.particle(FxSink.CONFETTI, 8 + rnd(r, 7f), 40 + r.nextFloat() * 4, 12 + rnd(r, 4f), rnd(r, 0.3f), 0.3f, rnd(r, 0.3f),
                        new int[]{0xFF4D5E, 0xFFD36A, 0x5FE3FF, 0x7CFF6A}[r.nextInt(4)], 1.1f, 60);
            }
        }
    }

    // =================================================================== vues
    @Override
    public SlotView newView() {
        SlotView v = new SlotView(SlotRules.classic());
        v.stops[0] = 0;
        v.stops[1] = 9;
        v.stops[2] = 3;
        return v;
    }

    @Override
    public SlotView demoView(float t) {
        SlotView v = newView();
        v.spinStart = DEMO_START;
        System.arraycopy(new int[]{0, 9, 3}, 0, v.prev, 0, 3);
        // 3 couronnes sur la ligne : jackpot de demonstration
        v.stops[0] = 9;
        v.stops[1] = 0;
        v.stops[2] = 3;
        v.win = 125000;
        v.bet = 100;
        v.multiplier = 1250;
        v.jackpot = true;
        v.lineMask = 1;
        v.jackpotValue = 124800;
        return v;
    }

    @Override
    public int demoLength() {
        return SlotGame.END + 40;
    }
}
