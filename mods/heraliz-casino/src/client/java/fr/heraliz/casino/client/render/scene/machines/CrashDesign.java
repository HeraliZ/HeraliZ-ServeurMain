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

import java.util.Locale;
import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * La Fusee (Crash), version bunker : une dalle de beton aux bandes de danger ecaillees, une baie de controle en tole
 * rivetee dont le grand ecran cathodique vert trace en direct la courbe du multiplicateur, un gyrophare et une
 * enseigne au pochoir. Sur le pas de tir (un pneu de camion coiffe d'une plaque d'egout) attend une fusee bricolee :
 * bouteille de gaz, cone de chantier en guise de coiffe, ailerons de tole. Elle decolle dans un nuage de fumee et
 * explose au "crash".
 */
public final class CrashDesign extends MachineDesign<CrashView> {
    // ---- atlas : partage avec tools/textures (PaintCrash)
    public static final int[] R_SCREEN = {0, 0, 1024, 512};
    public static final int[] R_METAL = {0, 512, 256, 256};
    public static final int[] R_CONCRETE = {256, 512, 256, 256};
    public static final int[] R_HAZARD = {512, 512, 256, 128};
    public static final int[] R_TANK = {768, 512, 128, 128};
    public static final int[] R_CONE = {896, 512, 128, 128};
    public static final int[] R_RUST = {512, 640, 128, 128};
    public static final int[] R_DARK = {640, 640, 64, 64};
    public static final int[] R_LED = {704, 640, 64, 64};
    public static final int[] R_BULB = {640, 704, 64, 64};
    public static final int[] R_WHITE = {704, 704, 64, 64};
    public static final int[] R_FLAME = {768, 640, 128, 128};
    public static final int[] R_FIN = {896, 640, 128, 128};
    public static final int[] R_SIGN = {0, 768, 512, 128};
    public static final int[] R_PANEL = {512, 768, 512, 128};
    public static final int[] R_TIRE = {0, 896, 256, 128};
    public static final int[] R_GLASS = {256, 896, 256, 128};
    public static final int[] R_BEZEL = {512, 896, 128, 128};
    public static final int[] R_CABLE = {640, 896, 128, 64};

    private static final Mat SCREEN = FruitsDesign.fit(R_SCREEN), METAL = FruitsDesign.tri(R_METAL, 24), CONCRETE = FruitsDesign.tri(R_CONCRETE, 24);
    private static final Mat HAZARD = FruitsDesign.tri(R_HAZARD, 8), TANK = FruitsDesign.tri(R_TANK, 6), CONE = FruitsDesign.tri(R_CONE, 6);
    private static final Mat RUST = FruitsDesign.tri(R_RUST, 8), DARK = FruitsDesign.tri(R_DARK, 8), LED = FruitsDesign.fit(R_LED);
    private static final Mat BULB = FruitsDesign.fit(R_BULB), WHITE = FruitsDesign.tri(R_WHITE, 4), FLAME = FruitsDesign.fit(R_FLAME);
    private static final Mat FIN = FruitsDesign.tri(R_FIN, 5), SIGN = FruitsDesign.fit(R_SIGN), PANEL = FruitsDesign.fit(R_PANEL);
    private static final Mat TIRE = FruitsDesign.tri(R_TIRE, 10), GLASS = FruitsDesign.fit(R_GLASS), BEZEL = FruitsDesign.tri(R_BEZEL, 8);
    private static final Mat CABLE = FruitsDesign.tri(R_CABLE, 6);

    /** Ecran : rectangle (monde) et zone du graphe. */
    static final float SX0 = -13.2f, SX1 = 29.2f, SY0 = 7.2f, SY1 = 43.2f, SZ = -8.9f;
    static final float GX0 = -10.5f, GX1 = 26.5f, GY0 = 11f, GY1 = 38.5f;
    /** Pas de tir. */
    static final float PX = -5.5f, PZ = 6.5f, PAD_Y = 6.2f;
    /** Gyrophare et baladeuse, sur le dessus de la baie. */
    static final float[] BEACON = {24.5f, SY1 + 3.0f, -11.4f}, LAMP = {-8.5f, SY1 + 4.2f, -10.4f};
    static final int ORANGE = 0xFF7A2E, CYAN = 0x5FE3FF, GOLD_RGB = 0xFFD36A, RED_RGB = 0xFF3A3A, PHOSPHOR = 0x8CFF7A, AMBER = 0xFFB040, PALE = 0xC8F0C0;

    private Mesh body = Mesh.EMPTY, screenGlow = Mesh.EMPTY, signGlow = Mesh.EMPTY, rocket = Mesh.EMPTY, rocketSmall = Mesh.EMPTY;
    private Mesh flame = Mesh.EMPTY, dot = Mesh.EMPTY, glass = Mesh.EMPTY, beacon = Mesh.EMPTY, bulb = Mesh.EMPTY, panelGlow = Mesh.EMPTY;

    public CrashDesign() {
        super("crash", ORANGE, CYAN);
        labelY = 52;
        itemScale = 0.22f;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        float[] nz = v(0, 0, 1);
        // ---- dalle de beton, cornieres peintes en bandes de danger sur trois cotes
        b.bevelBox(-15f, 0, -14f, 31f, 3.4f, 15f, 0.35f, CONCRETE);
        b.bevelBox(-15.2f, 2.6f, 14.6f, 31.2f, 3.5f, 15.2f, 0.1f, HAZARD);
        b.bevelBox(-15.2f, 2.6f, -14.2f, -14.6f, 3.5f, 15.2f, 0.1f, HAZARD);
        b.bevelBox(30.6f, 2.6f, -14.2f, 31.2f, 3.5f, 15.2f, 0.1f, HAZARD);
        // ---- baie de controle : caisson de tole rivetee, montants en corniere, cadre d'ecran en plastique jauni
        b.bevelBox(SX0 - 1.4f, 3.4f, -13.8f, SX1 + 1.4f, SY1 + 1.6f, SZ - 0.1f, 0.4f, METAL, new Mat[]{null, null, null, null, null, DARK}, 0);
        for (float x : new float[]{SX0 - 1.7f, SX1 + 0.9f}) b.bevelBox(x, 3.4f, -14f, x + 0.8f, SY1 + 1.9f, SZ + 0.25f, 0.1f, RUST);
        b.bevelBox(SX0 - 1.7f, SY1 + 1.5f, -14f, SX1 + 1.7f, SY1 + 2.1f, SZ + 0.25f, 0.1f, RUST);
        float fz = SZ + 0.55f, fw = 1.0f;
        b.bevelBox(SX0 - fw, SY1, SZ - 0.05f, SX1 + fw, SY1 + fw, fz, 0.25f, BEZEL);
        b.bevelBox(SX0 - fw, SY0 - fw, SZ - 0.05f, SX1 + fw, SY0, fz, 0.25f, BEZEL);
        b.bevelBox(SX0 - fw, SY0, SZ - 0.05f, SX0, SY1, fz, 0.25f, BEZEL);
        b.bevelBox(SX1, SY0, SZ - 0.05f, SX1 + fw, SY1, fz, 0.25f, BEZEL);
        b.face(SCREEN, nz, p(SX0, SY0, SZ), p(SX1, SY0, SZ), p(SX1, SY1, SZ), p(SX0, SY1, SZ));
        // bandeau de commandes sous l'ecran (cadrans, interrupteurs), rivets
        b.push().translate(8, 4.75f, SZ - 0.06f);
        b.decalZ(PANEL, 0, 0, 0, SX1 - SX0 + 2f, 2.5f);
        b.pop();
        BunkerKit.rivetsX(b, SX0 - 0.6f, SX1 + 0.6f, SY1 + 1.25f, SZ - 0.08f, 14, 0.22f, RUST);
        // adhesif sur un coin de l'ecran, cables qui descendent au sol
        BunkerKit.cable(b, p(SX1 + 1.9f, 30f, -11f), p(SX1 + 1.5f, 3.5f, -6.5f), 1.2f, 0.22f, CABLE);
        BunkerKit.cable(b, p(SX0 - 1.9f, 22f, -11.5f), p(SX0 - 1.2f, 3.5f, -7.5f), 0.8f, 0.2f, CABLE);
        // ---- enseigne au pochoir sur une tole, gyrophare et baladeuse sur le dessus
        b.bevelBox(8 - 9, SY1 + 2.1f, -12.6f, 8 + 9, SY1 + 6.4f, -10.4f, 0.2f, RUST, new Mat[]{null, null, null, null, null, SIGN}, 0);
        b.push().translate(BEACON[0], SY1 + 2.1f, BEACON[2]);
        b.cylinder(1.25f, 0, 0.5f, seg(12), RUST, null, RUST);
        b.pop();
        b.bevelBox(LAMP[0] - 0.15f, SY1 + 2.1f, LAMP[2] - 0.15f, LAMP[0] + 0.15f, LAMP[1] + 1.2f, LAMP[2] + 0.15f, 0.04f, RUST);
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2] + 0.9f, RUST);
        b.bevelBox(LAMP[0] - 0.1f, LAMP[1] + 0.95f, LAMP[2], LAMP[0] + 0.1f, LAMP[1] + 1.1f, LAMP[2] + 0.9f, 0.03f, RUST);
        // ---- pas de tir : pneu de camion, plaque d'egout ; portique d'echafaudage rouille
        BunkerKit.tire(b, PX, 3.4f, PZ, 4.15f, 1.35f, seg(28), TIRE);
        b.push().translate(PX, 3.4f, PZ);
        b.cylinder(3.0f, 0, 2.8f, seg(24), RUST, null, RUST);
        b.cylinder(2.2f, 2.8f, 2.95f, seg(20), DARK, null, DARK);
        b.pop();
        // traces de brulure autour du pas de tir : trois cales de parpaing
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(100 + k * 120);
            float cx = PX + (float) Math.cos(a) * 6.6f, cz = PZ + (float) Math.sin(a) * 6.6f;
            BunkerKit.block(b, cx - 1.4f, 3.4f, cz - 0.8f, cx + 1.4f, 4.7f, cz + 0.8f, CONCRETE, DARK);
        }
        float tx = PX + 6.2f, tz = PZ - 3.8f;
        for (float[] q : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            b.push().translate(tx + q[0] * 1.2f, PAD_Y - 2.8f, tz + q[1] * 1.2f);
            b.cylinder(0.22f, 0, 20.8f, seg(6), RUST, null, RUST);
            b.pop();
        }
        for (int k = 0; k < 8; k++) {
            float y = PAD_Y - 1.6f + k * 2.6f;
            b.bevelBox(tx - 1.3f, y, tz - 1.3f, tx + 1.3f, y + 0.25f, tz + 1.3f, 0.05f, RUST);
            if (k < 7) b.tube(new float[][]{p(tx - 1.2f, y, tz + 1.2f), p(tx + 1.2f, y + 2.6f, tz + 1.2f)}, new float[]{0.12f, 0.12f}, 5, RUST, false, false, 0, false);
        }
        b.bevelBox(tx - 1.6f, PAD_Y + 18f, tz - 1.6f, tx + 1.6f, PAD_Y + 18.6f, tz + 1.6f, 0.1f, RUST);
        // bras de maintien vers la fusee, jerrican de carburant au pied du portique
        b.bevelBox(PX + 1.6f, PAD_Y + 7.4f, tz + 0.4f, tx, PAD_Y + 7.9f, tz + 0.9f, 0.08f, RUST);
        BunkerKit.jerrycan(b, 22.5f, 3.4f, 9.5f, 1.0f, -24, TANK, RUST);
        BunkerKit.jerrycan(b, 26.4f, 3.4f, 8.2f, 1.0f, 18, METAL, RUST);
        Occlusion.bake(b, 0.5f, 3.5f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.face(SCREEN, nz, p(SX0, SY0, SZ + 0.02f), p(SX1, SY0, SZ + 0.02f), p(SX1, SY1, SZ + 0.02f), p(SX0, SY1, SZ + 0.02f));
        screenGlow = sg.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(SX0, SY0, SZ + 0.4f), p(SX1, SY0, SZ + 0.4f), p(SX1, SY1, SZ + 0.4f), p(SX0, SY1, SZ + 0.4f));
        glass = gl.build();
        MeshBuilder sn = new MeshBuilder();
        sn.push().translate(8, SY1 + 4.25f, -10.38f);
        sn.decalZ(SIGN, 0, 0, 0, 17.6f, 3.9f);
        sn.pop();
        signGlow = sn.build();
        MeshBuilder pg = new MeshBuilder();
        pg.push().translate(8, 4.75f, SZ - 0.04f);
        pg.decalZ(PANEL, 0, 0, 0, SX1 - SX0 + 2f, 2.5f);
        pg.pop();
        panelGlow = pg.build();
        // gyrophare : dome teinte selon l'etat du vol
        MeshBuilder bc = new MeshBuilder();
        bc.push().translate(BEACON[0], SY1 + 2.6f, BEACON[2]);
        bc.cylinder(0.95f, 0, 0.9f, seg(12), LED, null, null);
        bc.push().translate(0, 0.9f, 0);
        bc.sphereSection(0.95f, seg(12), 4, 0, 90, LED);
        bc.pop();
        bc.pop();
        beacon = bc.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2] + 0.9f);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();
        rocket = rocket(1f);
        rocketSmall = rocket(0.28f);
        MeshBuilder fl = new MeshBuilder();
        fl.lathe(new float[][]{{0.05f, -5f}, {0.7f, -3.2f}, {1.1f, -1.2f}, {0.95f, 0f}, {0.05f, 0.1f}}, seg(14), 0, 360, FLAME, 1, 1, 0, 0, 0, false);
        flame = fl.build();
        MeshBuilder dm = new MeshBuilder();
        dm.face(LED, nz, p(-0.5f, -0.5f, 0), p(0.5f, -0.5f, 0), p(0.5f, 0.5f, 0), p(-0.5f, 0.5f, 0));
        dot = dm.build();
    }

    /**
     * Fusee bricolee (base en y = 0, axe Y) : bouteille de gaz a la peinture rouge ecaillee cerclee de deux colliers,
     * cone de chantier orange a bande blanche pour coiffe, trois ailerons decoupes dans une tole, boite de conserve
     * en guise de tuyere.
     */
    private Mesh rocket(float k) {
        MeshBuilder b = new MeshBuilder();
        b.push().scale(k);
        // corps : la bouteille (cul bombe en bas, epaule en haut)
        float[][] hull = {{0.05f, 0}, {1.3f, 0.15f}, {1.62f, 0.9f}, {1.66f, 2f}, {1.66f, 6.6f}, {1.5f, 7.4f}, {1.1f, 8.0f}};
        b.lathe(hull, seg(20), 0, 360, TANK, 1, 1, 0, 0, 0, false);
        for (float y : new float[]{1.7f, 6.1f}) {
            b.push().translate(0, y, 0);
            b.cylinder(1.72f, 0, 0.42f, seg(20), RUST, null, null);
            b.pop();
        }
        // bande blanche au pochoir au milieu du corps
        b.push().translate(0, 3.5f, 0);
        b.cylinder(1.685f, 0, 1.3f, seg(20), WHITE, null, null);
        b.pop();
        // coiffe : cone de chantier (socle carre, bande blanche)
        b.bevelBox(-1.25f, 7.9f, -1.25f, 1.25f, 8.25f, 1.25f, 0.08f, CONE);
        b.lathe(new float[][]{{1.05f, 8.25f}, {0.78f, 9.6f}, {0.5f, 10.9f}, {0.22f, 12.1f}, {0.05f, 12.3f}}, seg(16), 0, 360, CONE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.81f, 9.5f}, {0.6f, 10.5f}}, seg(16), 0, 360, WHITE, 1.02f, 1.02f, 0, 0, 0, false);
        // ailerons de tole
        for (int f = 0; f < 3; f++) {
            b.push().rotateY(f * 120);
            b.push().translate(0, 0, 1.3f);
            b.prism(new float[][]{{-0.02f, 0}, {2.2f, -0.6f}, {2.2f, 0.4f}, {0f, 3.4f}}, 0.16f, FIN, FIN);
            b.pop();
            b.pop();
        }
        // tuyere : boite de conserve
        b.push().translate(0, -0.75f, 0);
        b.cylinder(0.72f, 0, 0.85f, seg(12), RUST, DARK, null);
        b.pop();
        b.pop();
        return b.build();
    }

    // =================================================================== graphe
    private static float gx(float sec, float xMax) {
        return GX0 + (GX1 - GX0) * Math.min(1f, sec / xMax);
    }

    private static float gy(double m, double yMax) {
        return GY0 + (GY1 - GY0) * (float) Math.min(1.0, (m - 1.0) / (yMax - 1.0));
    }

    /** Etiquette d'une graduation : 1.5x, 24x, 1250x, 30kx. */
    static String axis(double mv) {
        if (mv < 10) return String.format(Locale.ROOT, "%.1fx", mv);
        if (mv < 10_000) return String.format(Locale.ROOT, "%.0fx", mv);
        return String.format(Locale.ROOT, "%.0fkx", mv / 1000);
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, CrashView v, Pose p, DrawList out) {
        boolean flying = v.phase == RoundView.RUNNING && !v.crashed;
        boolean boom = (v.phase == RoundView.RUNNING && v.crashed) || v.phase == RoundView.RESULT;
        // ecran cathodique : il scintille, et saute a l'explosion
        float flicker = s.item ? 1 : 0.92f + 0.08f * (float) Math.sin(s.time * 3.1f) * (float) Math.sin(s.time * 0.7f);
        if (boom && v.sinceCrash < 14) flicker *= Ease.hash((int) (s.time * 2)) > 0.5f ? 1.25f : 0.5f;
        out.solid(p, screenGlow, 0.7f * flicker);
        out.solid(p, panelGlow, 0.35f);
        float lampK = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.95f ? 0.35f : 0.92f + 0.08f * (float) Math.sin(s.time * 1.1f);
        out.solid(p, bulb, lampK);
        out.solid(p, signGlow, 0.45f * lampK);
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2] + 0.9f, 2.6f, 0, 0xFFC880, 0.3f * lampK);
        // ---- gyrophare : ambre pendant les mises, vert en vol, rouge qui clignote a l'explosion
        int beaconCol = boom ? RED_RGB : flying ? Ease.mix(PHOSPHOR, 0xFFE060, (float) Math.min(1, Math.log(v.multiplier()) / 2.3)) : v.phase == RoundView.BETTING ? AMBER : 0x6A5A2A;
        float beaconK = boom ? (((int) (s.time / 3)) % 2 == 0 ? 1f : 0.2f) : flying ? 0.9f : v.phase == RoundView.BETTING ? 0.55f + 0.35f * s.pulse(0.35f) : 0.25f;
        out.solidTinted(p, beacon, 0, 0.3f, 0.26f, 0.2f);
        out.additive(p, beacon, beaconCol, beaconK);
        if (!s.item && (flying || boom || v.phase == RoundView.BETTING)) {
            out.billboard(p, Sprite.HALO, BEACON[0], BEACON[1], BEACON[2], 3.2f, 0, beaconCol, 0.4f * beaconK);
            // faisceau tournant
            p.push().translate(BEACON[0], BEACON[1], BEACON[2]).rotY(s.time * (boom ? 22 : 12)).rotZ(90);
            out.quad(p, Sprite.BEAM, -1.2f, 0, 1.2f, 9f, beaconCol, 0.3f * beaconK);
            p.pop();
        }
        // ---- fusee sur le pas de tir / en vol
        float lift = 0;
        float shake = 0;
        float alpha = 1;
        if (!s.item && v.phase == RoundView.RUNNING && !v.crashed) {
            float sec = v.flight / 20f;
            lift = 22f * (float) Math.log(v.multiplier()) + Ease.inQuad(Math.min(1, v.flight / 20f)) * 2f;
            shake = (float) Math.sin(s.time * 2.3f) * 0.08f;
            if (sec < 0.001f) lift = 0;
        } else if (!s.item && boom) {
            alpha = 0;
        }
        if (!s.item && v.phase == RoundView.IDLE && v.sincePhase < 30 && v.crashes.length > 0) {
            // nouvelle fusee qui monte du pas de tir
            lift = -12 * (1 - Ease.outCubic(v.sincePhase / 30f));
        }
        if (alpha > 0) {
            p.push().translate(PX + shake, PAD_Y + 1.2f + Math.max(lift, -11f), PZ);
            out.solid(p, rocket, 0);
            if (!s.item && (flying || (v.phase == RoundView.BETTING && v.countdown < 1.5f))) {
                float fl = flying ? 1f + 0.15f * (float) Math.sin(s.time * 3.1f) : 0.5f;
                p.push().translate(0, -0.75f, 0).scale(fl, fl * (flying ? 1.2f : 0.6f), fl);
                out.additive(p, flame, ORANGE, 0.9f);
                p.pop();
            }
            p.pop();
        }
        if (s.item) {
            out.translucent(p, glass, 0xFFFFFF, 0.28f);
            return;
        }
        // ---- ecran : graphe, multiplicateur, messages (phosphore vert, trace ambre)
        double m = v.multiplier();
        float sec = v.flightSeconds();
        float xMax = Math.max(8f, sec * 1.15f);
        double yMax = Math.max(2.0, m * 1.2);
        // axes : graduations (etiquettes courtes, tenues dans la marge gauche de l'ecran)
        for (int k = 1; k <= 4; k++) {
            double mv = 1 + (yMax - 1) * k / 4;
            float y = gy(mv, yMax);
            p.push().translate((GX0 + GX1) / 2, y, SZ + 0.05f).scale(GX1 - GX0, 0.06f, 1);
            out.additive(p, dot, 0x2A6A34, 0.6f);
            p.pop();
            String label = axis(mv);
            out.text(p, label, GX0 - 0.6f, y, SZ + 0.06f, fit(label, 0.7f, GX0 - SX0 - 0.8f, false), 0x6AC870, 0.9f, DrawList.T_RIGHT);
        }
        if (v.phase == RoundView.RUNNING || v.phase == RoundView.RESULT) {
            int col = boom ? RED_RGB : AMBER;
            int n = 28;
            float px0 = gx(0, xMax), py0 = gy(1, yMax);
            for (int i = 1; i <= n; i++) {
                float ts = sec * i / n;
                double mm = Math.exp(v.growth * ts);
                float x1 = gx(ts, xMax), y1 = gy(mm, yMax);
                float len = (float) Math.hypot(x1 - px0, y1 - py0);
                float ang = (float) Math.toDegrees(Math.atan2(y1 - py0, x1 - px0));
                p.push().translate((px0 + x1) / 2, (py0 + y1) / 2, SZ + 0.08f).rotZ(ang).scale(len + 0.1f, 0.42f, 1);
                out.additive(p, dot, col, 1f);
                p.pop();
                px0 = x1;
                py0 = y1;
            }
            // fusee miniature a la pointe
            float tipAng = (float) Math.toDegrees(Math.atan2(gy(Math.exp(v.growth * sec), yMax) - gy(Math.exp(v.growth * sec * 0.95), yMax),
                    gx(sec, xMax) - gx(sec * 0.95f, xMax)));
            if (!boom) {
                p.push().translate(px0, py0, SZ + 0.9f).rotZ(tipAng - 90).rotY(s.time * 4);
                out.solid(p, rocketSmall, 0.3f);
                p.pop();
                out.billboard(p, Sprite.FLARE, px0, py0, SZ + 1.2f, 1.6f, s.time * 5, AMBER, 0.9f);
            } else {
                float k = Math.min(1f, v.sinceCrash / 40f);
                out.billboard(p, Sprite.SHOCK, px0, py0, SZ + 1.2f, 3 + 10 * k, 0, RED_RGB, 1 - k);
                out.billboard(p, Sprite.FLARE, px0, py0, SZ + 1.3f, 5 * (1 - k), s.time * 3, 0xFFC040, 1 - k);
            }
        }
        // multiplicateur geant / messages
        float cx = (GX0 + GX1) / 2, cy = (GY0 + GY1) / 2 + 3;
        if (v.phase == RoundView.BETTING) {
            out.text(p, "MISE A FEU DANS", cx, cy + 2.6f, SZ + 0.1f, 1.6f, PALE, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            String c = String.format(Locale.ROOT, "%.1f s", Math.max(0, v.countdown));
            out.text(p, c, cx, cy - 1.8f, SZ + 0.1f, 4.2f, v.countdown < 3 ? RED_RGB : AMBER, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        } else if (v.phase == RoundView.IDLE) {
            out.text(p, "LA FUSEE", cx, cy + 1.5f, SZ + 0.1f, 3.4f, AMBER, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            out.text(p, "Misez pour lancer le compte a rebours", cx, cy - 2.5f, SZ + 0.1f, 1.1f, PALE, 0.9f, DrawList.T_CENTER);
        } else {
            String mt = String.format(Locale.ROOT, "%.2fx", m);
            int col = boom ? RED_RGB : Ease.mix(PALE, PHOSPHOR, (float) Math.min(1, Math.log(m) / 2));
            float pulse = flying ? 1 + 0.03f * (float) Math.sin(s.time * 0.9f) : 1;
            out.text(p, mt, cx, cy, SZ + 0.12f, fit(mt, 5.2f * pulse, 26f, true), col, 1f, DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x030A04);
            if (boom) out.text(p, "CRASH !", cx, cy + 5f, SZ + 0.12f, 2.2f, RED_RGB, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            else if (v.inPlay > 0) out.text(p, v.inPlay + " en vol - " + fmt(v.totalInPlay) + " jetons", cx, cy - 4.4f, SZ + 0.1f, 1f, PALE, 0.9f, DrawList.T_CENTER);
            if (v.myCash > 0) {
                String me = String.format(Locale.ROOT, "ENCAISSE A %.2fx", v.myCash / 100.0);
                out.text(p, me, cx, cy - 6.2f, SZ + 0.1f, 1.2f, GOLD_RGB, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
            }
        }
        // historique
        for (int i = 0; i < Math.min(10, v.crashes.length); i++) {
            double c = v.crashes[i] / 100.0;
            int col = c < 1.5 ? RED_RGB : c < 2 ? AMBER : c < 10 ? PHOSPHOR : GOLD_RGB;
            out.text(p, String.format(Locale.ROOT, "%.2fx", c), GX0 + 1.6f + i * 3.7f, SY0 + 1.2f, SZ + 0.1f, 0.8f, col, i == 0 ? 1f : 0.75f, DrawList.T_CENTER | DrawList.T_BOLD);
        }
        out.translucent(p, glass, 0xFFFFFF, 0.28f);
    }

    @Override
    public boolean zoomable() {
        return true;
    }

    /** Zoom du joueur : l'ecran du graphe en entier. */
    @Override
    public float[] screenRect(CrashView v, Scene s) {
        return new float[]{SX0, SY0, SX1, SY1, SZ};
    }

    // =================================================================== camera
    @Override
    public void camera(CrashView v, Scene s, int mode, Shot out) {
        if (mode == CAM_MENU) {
            out.set(8, 26, 54, 8, 24, -6, 56);
        } else {
            // pendant le vol, la camera s'approche doucement de l'ecran
            float k = Ease.inOutSine(Math.min(1f, Math.max(0, v.flight) / 160f));
            out.set(8, 26 - k, 54 - 10 * k, 8, 24.5f, -6, 56 - 4 * k);
        }
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, CrashView v, FxSink fx, Random r) {
        if (r.nextInt(240) == 0) fx.particle(FxSink.SPARK, SX1 + 1.2f, 12 + r.nextFloat() * 20, -9f, 0.05f, 0.04f, 0.08f, 0xFFE0A0, 0.45f, 9);
        if (v.phase == RoundView.BETTING && v.countdown <= 5 && v.countdown > 0 && Math.abs(v.countdown - Math.round(v.countdown)) < 0.026f) {
            fx.sound(Snd.COUNTDOWN, PX, 20, 0, 0.7f, v.countdown <= 1.05f ? 1.6f : 1.2f);
        }
        if (v.phase == RoundView.RUNNING && !v.crashed) {
            float lift = 22f * (float) Math.log(v.multiplier());
            float y = PAD_Y + 1.2f + lift;
            if (v.flight < 1) {
                fx.sound(Snd.ROCKET_LAUNCH, PX, PAD_Y + 2, PZ, 1f, 1f);
                for (int i = 0; i < 40; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    fx.particle(FxSink.SMOKE, PX + (float) Math.cos(a) * 2, PAD_Y + 0.5f, PZ + (float) Math.sin(a) * 2,
                            (float) Math.cos(a) * 0.6f, 0.05f + r.nextFloat() * 0.1f, (float) Math.sin(a) * 0.6f, 0xB8B0A4, 4f + r.nextFloat() * 3, 50 + r.nextInt(30));
                }
            }
            if (((int) v.flight) % 12 == 0) fx.sound(Snd.ROCKET_ENGINE, PX, Math.min(y, 40), PZ, 0.5f, 0.9f + r.nextFloat() * 0.2f);
            if (y < 70) {
                fx.particle(FxSink.FLAME, PX + rnd(r, 0.4f), y - 1f, PZ + rnd(r, 0.4f), rnd(r, 0.05f), -0.6f, rnd(r, 0.05f), ORANGE, 1.6f, 10);
                fx.particle(FxSink.SMOKE, PX + rnd(r, 0.6f), y - 2.5f, PZ + rnd(r, 0.6f), rnd(r, 0.1f), -0.2f, rnd(r, 0.1f), 0x8A847A, 2.6f, 40);
            }
            if (((int) v.flight) % 20 == 0 && v.multiplier() >= 2) fx.sound(Snd.SPARKLE, PX, 30, SZ + 2, 0.4f, 1f + (float) Math.min(1, Math.log(v.multiplier()) / 3));
        }
        if (v.phase == RoundView.RUNNING && v.crashed && v.sinceCrash < 1) {
            fx.sound(Snd.EXPLOSION, PX, 30, 0, 1f, 1f);
            burst(fx, r, PX, 25, SZ + 2, RED_RGB, ORANGE, 60, 2.2f, 2f);
            for (int i = 0; i < 20; i++) {
                fx.particle(FxSink.SMOKE, PX + rnd(r, 6), 20 + rnd(r, 8), SZ + 3, rnd(r, 0.3f), 0.1f, 0.2f, 0x4A4A4A, 5f, 60);
            }
            // debris de tole qui retombent sur la dalle
            for (int i = 0; i < 10; i++) {
                fx.particle(FxSink.SHARD, PX + rnd(r, 4), 24 + rnd(r, 6), SZ + 4, rnd(r, 0.5f), 0.2f + r.nextFloat() * 0.3f, 0.2f + r.nextFloat() * 0.3f, 0x8A4A2A, 0.8f, 46);
            }
        }
    }

    // =================================================================== vues
    @Override
    public CrashView newView() {
        CrashView v = new CrashView();
        v.crashes = new long[]{112, 345, 101, 1890, 223, 150, 740, 120, 260, 105};
        return v;
    }

    @Override
    public CrashView demoView(float t) {
        CrashView v = newView();
        v.inPlay = 4;
        v.totalInPlay = 3250;
        double crash = 3.47;
        long crashTick = (long) Math.ceil(Math.log(crash) / v.growth * 20);
        if (t < 40) {
            v.phase = RoundView.BETTING;
            v.countdown = (40 - t) / 20f;
        } else if (t < 40 + crashTick) {
            v.phase = RoundView.RUNNING;
            v.flight = t - 40;
            v.sincePhase = t - 40;
            if (t - 40 > 150) v.myCash = 215;
        } else {
            v.phase = RoundView.RUNNING;
            v.crashed = true;
            v.crashShown = 347;
            v.flight = crashTick;
            v.sinceCrash = t - 40 - crashTick;
            v.myCash = 215;
        }
        return v;
    }

    @Override
    public int demoLength() {
        return 400;
    }
}
