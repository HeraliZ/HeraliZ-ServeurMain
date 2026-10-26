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
import fr.heraliz.casino.game.race.RaceGame;
import fr.heraliz.casino.game.race.RaceRules;

import java.util.Locale;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;

/**
 * Course de chevaux, version bunker : un plateau de planches rempli de terre battue, pose sur des piles de parpaings,
 * ou l'on a deroule trois les de gazon synthetique recupere. Cinq couloirs separes de lices a la peinture blanche
 * ecaillee, stalles de depart en ferraille, damier d'arrivee delave, vieil ecran des cotes dans son caisson de tole
 * (branche sur une batterie de voiture), planche de mise au pochoir. Cinq petits chevaux de fer-blanc (jockeys aux
 * couleurs) galopent pattes animees ; le vainqueur est designe par le serveur et la course est rejouee a l'identique
 * chez tous.
 */
public final class RaceDesign extends MachineDesign<RaceView> {
    public static final int[] R_TURF = {0, 0, 512, 512};
    public static final int[] R_WOOD = {512, 0, 256, 256};
    public static final int[] R_WHITE = {768, 0, 256, 256};
    public static final int[] R_HORSE = {512, 256, 256, 256};
    public static final int[] R_MANE = {768, 256, 128, 128};
    public static final int[] R_SILK = {896, 256, 128, 128};
    public static final int[] R_CHECK = {768, 384, 128, 128};
    public static final int[] R_DARK = {896, 384, 128, 128};
    public static final int[] R_CHIP_EDGE = {0, 512, 64, 64};
    public static final int[] R_CHIP_WHITE = {64, 512, 64, 64};
    public static final int[] R_CHIP_TOP = {128, 512, 64, 64};
    public static final int[] R_BULB = {192, 512, 64, 64};
    public static final int[] R_SIGN = {256, 512, 512, 128};
    public static final int[] R_RUST = {768, 512, 256, 128};
    public static final int[] R_DIRT = {0, 640, 256, 256};
    public static final int[] R_LEDGE = {256, 640, 512, 64};
    public static final int[] R_BLOCK = {768, 640, 128, 128};
    public static final int[] R_CABLE = {896, 640, 64, 64};
    public static final int[] R_SCREEN = {256, 704, 256, 128};
    public static final int[] R_TIRE = {512, 704, 256, 128};
    public static final int[] R_BATT = {768, 768, 128, 128};
    public static final int[] R_CAN = {896, 768, 128, 128};
    public static final int[] R_CRATE = {0, 896, 256, 128};

    private static final Mat TURF = FruitsDesign.fit(R_TURF), WOOD = FruitsDesign.tri(R_WOOD, 20), WHITE = FruitsDesign.tri(R_WHITE, 8);
    private static final Mat HORSE = FruitsDesign.tri(R_HORSE, 6), MANE = FruitsDesign.tri(R_MANE, 4), SILK = FruitsDesign.tri(R_SILK, 4);
    private static final Mat CHECK = FruitsDesign.tri(R_CHECK, 2), DARK = FruitsDesign.tri(R_DARK, 8), RUST = FruitsDesign.tri(R_RUST, 12);
    private static final Mat SIGN = FruitsDesign.fit(R_SIGN), DIRT = FruitsDesign.tri(R_DIRT, 16), LEDGE = FruitsDesign.fit(R_LEDGE);
    private static final Mat BLOCK = FruitsDesign.tri(R_BLOCK, 8), CABLE = FruitsDesign.tri(R_CABLE, 6), SCREEN = FruitsDesign.fit(R_SCREEN);
    private static final Mat TIRE = FruitsDesign.tri(R_TIRE, 10), BATT = FruitsDesign.fit(R_BATT), CAN = FruitsDesign.tri(R_CAN, 8);
    private static final Mat CRATE = FruitsDesign.tri(R_CRATE, 12), BULB = FruitsDesign.fit(R_BULB);

    public static final float TOP = 12f, START_X = -9.5f, FINISH_X = 26f, LANE0_Z = -6.2f, LANE = 3.6f;
    /** Piste de gazon (px) et planche de mise : reperes partages avec le peintre de textures. */
    public static final float TURF_X0 = -12.8f, TURF_X1 = 28.8f, LEDGE_X0 = -13f, LEDGE_X1 = 29f, LEDGE_Z0 = 11.2f, LEDGE_Z1 = 14.4f;
    static final int N = RaceRules.HORSES, GOLD_RGB = 0xFFD36A, GREEN = 0x7CFF6A, AMBER = 0xFFB040, PHOSPHOR = 0xC8F0B8;
    /** Robes des chevaux de fer-blanc (bai, noir, gris, alezan, isabelle). */
    static final int[] COATS = {0x8E5C36, 0x403832, 0xBEB8AC, 0xA05A2A, 0xCCAC6C};
    /** Baladeuse du tableau des cotes. */
    static final float[] LAMP = {-0.4f, TOP + 16.9f, -10.5f};

    private Mesh body = Mesh.EMPTY, signGlow = Mesh.EMPTY, horseBody = Mesh.EMPTY, leg = Mesh.EMPTY, jockey = Mesh.EMPTY, silks = Mesh.EMPTY, gate = Mesh.EMPTY;
    private Mesh bulb = Mesh.EMPTY, screenGlow = Mesh.EMPTY;
    private ChipStacks chips;

    /** Abscisse (px) du centre de la case de mise d'un cheval, sur la planche de devant. */
    public static float betX(int i) {
        return -9 + i * 8.2f;
    }

    public RaceDesign() {
        super("course", GREEN, GOLD_RGB);
        labelY = 42;
        itemScale = 0.25f;
    }

    public static float laneZ(int i) {
        return LANE0_Z + i * LANE;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        float zMin = laneZ(0) - LANE / 2, zMax = laneZ(N - 1) + LANE / 2;
        // ---- pietement : quatre piles de parpaings, deux madriers, et ce qui traine dessous
        for (float[] q : new float[][]{{-10.6f, -9.4f}, {26.6f, -9.4f}, {-10.6f, 11.4f}, {26.6f, 11.4f}}) {
            for (int k = 0; k < 5; k++) {
                float y = k * 2.04f, dx = (k % 2 == 0 ? 0.16f : -0.14f), dz = (k % 3 == 1 ? 0.12f : -0.06f);
                BunkerKit.block(b, q[0] - 3.1f + dx, y, q[1] - 1.6f + dz, q[0] + 3.1f + dx, y + 2f, q[1] + 1.6f + dz, BLOCK, DARK);
            }
        }
        b.bevelBox(-14.4f, 10.2f, -10.4f, 30.4f, TOP - 1f, -8.4f, 0.1f, WOOD);
        b.bevelBox(-14.4f, 10.2f, 10.4f, 30.4f, TOP - 1f, 12.4f, 0.1f, WOOD);
        for (int k = 0; k < 3; k++) BunkerKit.tire(b, -0.6f + (k == 1 ? 0.5f : 0), k * 2.42f, 1.6f - (k == 2 ? 0.4f : 0), 3.3f, 1.35f, seg(20), TIRE);
        BunkerKit.crate(b, 8f, 0, -2.5f, 17f, 5.6f, 4.1f, CRATE, WOOD);
        BunkerKit.jerrycan(b, 21.6f, 0, 4.6f, 1.05f, -24, CAN, RUST);
        // ---- plateau de planches rempli de terre battue, gazon synthetique deroule dessus
        b.bevelBox(-15f, TOP - 1f, -13f, 31f, TOP, 15f, 0.25f, WOOD, new Mat[]{null, null, null, DIRT, null, null}, 0);
        b.face(TURF, fr.heraliz.casino.client.render.mesh.MeshBuilder.v(0, 1, 0), p(TURF_X0, TOP + 0.02f, zMax), p(TURF_X1, TOP + 0.02f, zMax),
                p(TURF_X1, TOP + 0.02f, zMin), p(TURF_X0, TOP + 0.02f, zMin));
        // lices entre les couloirs : fers ronds a la peinture blanche ecaillee, sur leurs piquets
        for (int i = 0; i <= N; i++) {
            float z = laneZ(i) - LANE / 2;
            b.bevelBox(TURF_X0, TOP + 0.9f, z - 0.09f, TURF_X1, TOP + 1.1f, z + 0.09f, 0.03f, WHITE);
            for (float x = -12f; x <= 28.5f; x += 3.4f) {
                b.push().translate(x, TOP, z);
                b.cylinder(0.11f, 0, 0.92f, 5, WHITE, null, null);
                b.pop();
            }
        }
        // ligne d'arrivee : damier delave au sol, deux tubes rouilles, une barre et son fanion a damier
        b.bevelBox(FINISH_X - 0.5f, TOP + 0.02f, zMin, FINISH_X + 0.5f, TOP + 0.08f, zMax, 0.02f, CHECK);
        for (float z : new float[]{zMax + 0.6f, zMin - 0.6f}) {
            b.push().translate(FINISH_X, TOP, z);
            b.cylinder(0.24f, 0, 7.3f, seg(8), RUST, null, RUST);
            b.pop();
        }
        b.push().translate(FINISH_X, TOP + 6.9f, zMin - 1.1f).rotateX(90);
        b.cylinder(0.16f, 0, zMax - zMin + 2.2f, 6, RUST, RUST, RUST);
        b.pop();
        b.bevelBox(FINISH_X - 0.06f, TOP + 5.5f, zMin - 0.3f, FINISH_X + 0.06f, TOP + 6.8f, zMax + 0.3f, 0.02f, CHECK);
        // ---- tableau des cotes (au fond) : caisson de tole rouillee, vieil ecran, plaque au pochoir, baladeuse
        b.bevelBox(-2f, TOP, -13f, 22f, TOP + 11.8f, -11.8f, 0.2f, RUST);
        b.bevelBox(-1.4f, TOP + 1.8f, -11.9f, 21.4f, TOP + 11.4f, -11.6f, 0.15f, DARK, new Mat[]{null, null, null, null, null, SCREEN}, 0);
        b.bevelBox(-2.4f, TOP + 11.8f, -13.2f, 22.4f, TOP + 14.8f, -11.4f, 0.2f, RUST);
        BunkerKit.rivetsX(b, -1.4f, 21.4f, TOP + 0.9f, -11.8f, 12, 0.17f, RUST);
        b.bevelBox(LAMP[0] - 0.12f, TOP + 14.8f, -12.5f, LAMP[0] + 0.12f, LAMP[1] + 1.12f, -12.2f, 0.03f, RUST);
        b.bevelBox(LAMP[0] - 0.1f, LAMP[1] + 0.98f, -12.3f, LAMP[0] + 0.1f, LAMP[1] + 1.12f, LAMP[2], 0.03f, RUST);
        BunkerKit.cageLamp(b, LAMP[0], LAMP[1], LAMP[2], RUST);
        // la batterie de voiture qui alimente l'ecran, et ses deux cables
        b.bevelBox(23.6f, TOP, -12.6f, 26.8f, TOP + 2.2f, -10.7f, 0.2f, DARK, new Mat[]{null, null, null, null, null, BATT}, 0);
        for (int k = -1; k <= 1; k += 2) {
            b.push().translate(25.2f + k * 0.95f, TOP + 2.2f, -12f);
            b.cylinder(0.26f, 0, 0.42f, 8, RUST, null, RUST);
            b.pop();
        }
        BunkerKit.cable(b, p(24.25f, TOP + 2.5f, -12f), p(22.1f, TOP + 5.2f, -12.4f), 0.7f, 0.1f, CABLE);
        BunkerKit.cable(b, p(26.15f, TOP + 2.5f, -12f), p(22.1f, TOP + 7f, -12.5f), 1f, 0.1f, CABLE);
        // ---- planche de mise (devant) : une case par cheval, peinte a sa couleur
        b.bevelBox(LEDGE_X0, TOP, LEDGE_Z0, LEDGE_X1, TOP + 0.4f, LEDGE_Z1, 0.1f, WOOD, new Mat[]{null, null, null, LEDGE, null, null}, 0);
        Occlusion.bake(b, 0.45f, 3f, 0.4f, 0f);
        body = b.build();

        MeshBuilder sg = new MeshBuilder();
        sg.push().translate(10, TOP + 13.3f, -11.38f);
        sg.decalZ(SIGN, 0, 0, 0, 24f, 2.6f);
        sg.pop();
        signGlow = sg.build();
        MeshBuilder sc = new MeshBuilder();
        sc.push().translate(10, TOP + 6.6f, -11.58f);
        sc.decalZ(SCREEN, 0, 0, 0, 22.5f, 9.3f);
        sc.pop();
        screenGlow = sc.build();
        MeshBuilder bu = new MeshBuilder();
        bu.push().translate(LAMP[0], LAMP[1], LAMP[2]);
        bu.sphere(0.42f, 8, 5, BULB);
        bu.pop();
        bulb = bu.build();

        // stalles de depart : cloisons de tole blanche ecaillee sous une traverse de ferraille
        MeshBuilder g = new MeshBuilder();
        for (int i = 0; i <= N; i++) {
            float z = laneZ(i) - LANE / 2;
            g.bevelBox(START_X - 1.4f, TOP, z - 0.12f, START_X + 1.4f, TOP + 3.6f, z + 0.12f, 0.04f, WHITE);
        }
        g.bevelBox(START_X - 1.5f, TOP + 3.6f, zMin - 0.3f, START_X + 1.5f, TOP + 4.1f, zMax + 0.3f, 0.08f, RUST);
        gate = g.build();

        // ---- cheval de fer-blanc (repere : sol, face vers +X), sans les pattes ; teinte selon sa robe
        MeshBuilder h = new MeshBuilder();
        h.push().translate(0, 1.95f, 0).scale(1.45f, 0.72f, 0.55f);
        h.sphere(1f, seg(14), seg(9), HORSE);
        h.pop();
        h.tube(new float[][]{p(0.9f, 2.2f, 0), p(1.5f, 2.9f, 0), p(1.8f, 3.3f, 0)}, new float[]{0.42f, 0.34f, 0.28f}, seg(10), HORSE, false, false, 0, false);
        h.tube(new float[][]{p(1.7f, 3.35f, 0), p(2.2f, 3.1f, 0), p(2.55f, 2.8f, 0)}, new float[]{0.3f, 0.24f, 0.18f}, seg(10), HORSE, true, true, 0, false);
        h.tube(new float[][]{p(0.9f, 2.6f, 0), p(1.4f, 3.25f, 0), p(1.75f, 3.6f, 0)}, new float[]{0.14f, 0.14f, 0.1f}, 6, MANE, false, true, 0, false);
        for (int sd = -1; sd <= 1; sd += 2) {
            h.push().translate(1.75f, 3.55f, sd * 0.12f);
            h.spike(0.14f, 0.1f, 0.35f, HORSE);
            h.pop();
        }
        h.tube(new float[][]{p(-1.35f, 2.3f, 0), p(-1.8f, 2.0f, 0), p(-2.0f, 1.3f, 0)}, new float[]{0.16f, 0.13f, 0.08f}, 6, MANE, false, true, 0, false);
        horseBody = h.build();
        MeshBuilder l = new MeshBuilder();
        l.tube(new float[][]{p(0, 0, 0), p(0.05f, -0.8f, 0), p(0, -1.55f, 0)}, new float[]{0.17f, 0.12f, 0.11f}, 6, HORSE, true, false, 0, false);
        l.push().translate(0, -1.65f, 0);
        l.cylinder(0.14f, 0, 0.18f, 6, MANE, MANE, MANE);
        l.pop();
        leg = l.build();
        // jockey : casque et casaque teintes, visage
        MeshBuilder j = new MeshBuilder();
        j.push().translate(0.25f, 3.2f, 0).rotateZ(-35);
        j.cylinder(0.3f, 0, 0.9f, seg(8), SILK, SILK, SILK);
        j.pop();
        j.push().translate(0.95f, 3.9f, 0);
        j.sphere(0.32f, seg(10), 6, SILK);
        j.pop();
        silks = j.build();
        MeshBuilder jk = new MeshBuilder();
        jk.tube(new float[][]{p(0.1f, 2.7f, 0.3f), p(0.5f, 2.2f, 0.36f), p(0.4f, 1.8f, 0.4f)}, new float[]{0.11f, 0.1f, 0.09f}, 5, WHITE, false, true, 0, false);
        jk.tube(new float[][]{p(0.1f, 2.7f, -0.3f), p(0.5f, 2.2f, -0.36f), p(0.4f, 1.8f, -0.4f)}, new float[]{0.11f, 0.1f, 0.09f}, 5, WHITE, false, true, 0, false);
        jockey = jk.build();
        chips = new ChipStacks(0.7f, 0.2f, seg(12));
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, RaceView v, Pose p, DrawList out) {
        // baladeuse : elle vacille de temps en temps ; l'ecran des cotes scintille, la plaque luit faiblement
        float lampK = s.item ? 1 : Ease.hash((int) (s.time / 3) + (int) (s.seed * 100)) > 0.95f ? 0.35f : 0.92f + 0.08f * (float) Math.sin(s.time * 1.1f);
        out.solid(p, bulb, lampK);
        out.solid(p, signGlow, 0.3f * lampK);
        out.solid(p, screenGlow, s.item ? 0.24f : 0.25f + 0.04f * (float) Math.sin(s.time * 2.3f));
        if (!s.item) out.billboard(p, Sprite.HALO, LAMP[0], LAMP[1], LAMP[2], 3f, 0, 0xFFC880, 0.3f * lampK);
        boolean racing = !s.item && v.phase == RoundView.RUNNING;
        boolean result = !s.item && v.phase == RoundView.RESULT && v.winner >= 0;
        // stalles : se levent au depart
        float gateUp = racing ? Ease.outCubic(Math.min(1f, v.sincePhase / 8f)) : (result ? 1 : 0);
        if (gateUp < 1) {
            p.push().translate(0, gateUp * 5f, 0);
            out.solid(p, gate, 0);
            p.pop();
        }
        for (int i = 0; i < N; i++) {
            float prog, speed;
            if (racing || result) {
                float t = result ? v.raceTicks + 30 + v.sincePhase : v.sincePhase;
                prog = RaceGame.progress(i, v.winner, v.seed, t, v.raceTicks);
                speed = (RaceGame.progress(i, v.winner, v.seed, t + 1, v.raceTicks) - prog) * (FINISH_X - START_X);
            } else {
                prog = 0;
                speed = 0;
            }
            float x = START_X + 0.2f + Math.min(prog, 1.12f) * (FINISH_X - START_X);
            float z = laneZ(i);
            float gallop = speed > 0.02f ? 1 : 0;
            float phase = (x * 0.9f + i * 1.3f);
            float bob = gallop * 0.25f * (float) Math.abs(Math.sin(phase * 1.6f));
            float idle = gallop == 0 ? 0.08f * (float) Math.sin(s.time * 0.1f + i) : 0;
            p.push().translate(x, TOP + bob, z).rotZ(gallop * 6f * (float) Math.sin(phase * 1.6f) + idle * 20);
            int coat = COATS[i % COATS.length];
            float cr = ((coat >> 16) & 255) / 255f, cg = ((coat >> 8) & 255) / 255f, cb = (coat & 255) / 255f;
            out.solidTinted(p, horseBody, 0, cr, cg, cb);
            // pattes : galop (avant et arriere en opposition)
            float[][] legs = {{0.95f, 0.3f, 0}, {0.95f, -0.3f, 0.5f}, {-0.95f, 0.3f, 0.25f}, {-0.95f, -0.3f, 0.75f}};
            for (float[] lg : legs) {
                float a = gallop * 38f * (float) Math.sin(phase * 1.6f + lg[2] * 6.28f);
                p.push().translate(lg[0], 1.65f, lg[1]).rotZ(a);
                out.solidTinted(p, leg, 0, cr, cg, cb);
                p.pop();
            }
            int c = RaceRules.COLORS[i];
            out.solidTinted(p, silks, 0, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            out.solid(p, jockey, 0);
            p.pop();
            if (!s.item) {
                // numero du couloir sur le gazon (depart)
                p.push().translate(START_X - 2.4f, TOP + 0.05f, z).rotX(-90);
                out.text(p, String.valueOf(i + 1), 0, 0, 0, 1.2f, c, 1f, DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x14100A);
                p.pop();
                if (result && i == v.winner) {
                    out.billboard(p, Sprite.FLARE, x, TOP + 3, z, 4f + 1.5f * (float) Math.sin(s.time * 0.3f), s.time * 3, AMBER, 0.5f);
                    out.text(p, I18n.get("heralizcasino.course.premier"), x, TOP + 5.4f, z, 1.1f, AMBER, 1f, DrawList.T_CENTER | DrawList.T_BILLBOARD | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x2A1A04);
                }
            }
        }
        if (s.item) return;
        // ---- tableau des cotes : vieil ecran au phosphore
        String title;
        int tc = AMBER;
        if (v.phase == RoundView.BETTING) {
            title = I18n.get("heralizcasino.course.depart", String.valueOf((int) Math.ceil(v.countdown)));
            tc = v.countdown < 5 ? 0xFF5A4A : AMBER;
        } else if (racing) {
            title = I18n.get("heralizcasino.course.partis");
            tc = PHOSPHOR;
        } else if (result) {
            title = I18n.get("heralizcasino.course.vainqueur", String.valueOf(v.winner + 1), RaceRules.NAMES[v.winner].toUpperCase(Locale.ROOT));
        } else {
            title = I18n.get("heralizcasino.course.prochaine");
        }
        out.text(p, title, 10, TOP + 10.4f, -11.55f, fit(title, 1.2f, 21f, true), tc, 1f, DrawList.T_CENTER | DrawList.T_BOLD);
        for (int i = 0; i < N; i++) {
            float y = TOP + 8.6f - i * 1.5f;
            int c = RaceRules.COLORS[i];
            boolean win = result && i == v.winner;
            out.text(p, (i + 1) + "  " + RaceRules.NAMES[i], -0.6f, y, -11.55f, 0.95f, win ? AMBER : c, 1f, DrawList.T_BOLD);
            out.text(p, String.format(Locale.ROOT, "x%.1f", v.odds[i]), 20.6f, y, -11.55f, 0.95f, win ? AMBER : PHOSPHOR, 1f, DrawList.T_RIGHT | DrawList.T_BOLD);
        }
        // ---- mises (jetons) sur la planche, par cheval
        for (int i = 0; i < N; i++) {
            float x = betX(i);
            int c = RaceRules.COLORS[i];
            p.push().translate(x - 3.2f, TOP + 0.42f, 12.8f).rotX(-90);
            out.text(p, String.valueOf(i + 1), 0, 0, 0, 1.2f, c, 1f, DrawList.T_CENTER | DrawList.T_BOLD | DrawList.T_OUTLINE, 0x000000);
            p.pop();
            if (v.totals[i] > 0) {
                boolean lost = result && i != v.winner;
                float k = lost ? Ease.inOutSine(Ease.range(v.sincePhase, 20, 40)) : 0;
                if (k < 1) chips.amount(p, out, v.totals[i], x, TOP + 0.42f + 2 * Ease.bell(k, 0, 1), 12.8f - 10 * k, 1.5f, 3);
            }
        }
    }

    // =================================================================== camera et zones cliquables
    @Override
    public void camera(RaceView v, Scene s, int mode, Shot out) {
        if (mode == CAM_MENU || v.winner < 0) {
            out.set(8, 38, 34, 8, 15, -2, 54);
            return;
        }
        if (v.phase == RoundView.RESULT) {
            out.set(FINISH_X - 7, TOP + 13, 22, FINISH_X - 1, TOP, 0, 50);
            return;
        }
        // travelling : la camera suit le cheval de tete
        float lead = START_X;
        for (int i = 0; i < N; i++) {
            float prog = RaceGame.progress(i, v.winner, v.seed, v.sincePhase, v.raceTicks);
            lead = Math.max(lead, START_X + 0.2f + Math.min(prog, 1.12f) * (FINISH_X - START_X));
        }
        lead = Math.min(lead, FINISH_X);
        out.set(lead - 5, TOP + 14, 24, lead + 2, TOP, 0, 50);
    }

    @Override
    public void hotspots(RaceView v, Scene s, Hotspots out) {
        for (int i = 0; i < N; i++) {
            // ligne du tableau des cotes
            float y = TOP + 8.6f - i * 1.5f;
            out.add(i, -1.2f, y - 0.72f, -11.5f, 22.4f, 0, 0, 0, 1.44f, 0);
            // couloir du cheval
            out.flat(i, -12.8f, laneZ(i) - LANE / 2 + 0.15f, 28.8f, laneZ(i) + LANE / 2 - 0.15f, TOP + 0.1f);
            // case de mise sur le rebord
            float x = -9 + i * 8.2f;
            out.flat(i, x - 4, 11.4f, x + 4, 14.2f, TOP + 0.45f);
        }
    }
    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, RaceView v, FxSink fx, Random r) {
        if (v.phase == RoundView.BETTING && v.countdown <= 5 && v.countdown > 0 && Math.abs(v.countdown - Math.round(v.countdown)) < 0.026f) {
            fx.sound(Snd.COUNTDOWN, 10, TOP + 8, -10, 0.6f, 1.2f);
        }
        if (v.phase == RoundView.RUNNING) {
            if (v.sincePhase < 1) fx.sound(Snd.RACE_START, START_X, TOP + 3, 0, 1f, 1f);
            if (((int) v.sincePhase) % 6 == 0 && v.sincePhase < v.raceTicks) fx.sound(Snd.GALLOP, 10, TOP + 1, 0, 0.6f, 0.9f + r.nextFloat() * 0.2f);
            for (int i = 0; i < N; i++) {
                if (r.nextInt(3) != 0) continue;
                float prog = RaceGame.progress(i, v.winner, v.seed, v.sincePhase, v.raceTicks);
                if (prog >= 1) continue;
                float x = START_X + prog * (FINISH_X - START_X);
                fx.particle(FxSink.SMOKE, x - 1.5f, TOP + 0.3f, laneZ(i) + rnd(r, 0.6f), -0.1f, 0.05f, 0, 0x9A7A4A, 1.2f, 18);
            }
            if (Math.abs(v.sincePhase - v.raceTicks) < 0.5f) {
                fx.sound(Snd.BELL, FINISH_X, TOP + 6, 0, 1f, 1f);
                burst(fx, r, FINISH_X, TOP + 6, laneZ(v.winner), GOLD_RGB, RaceRules.COLORS[v.winner], 30, 1.2f, 1.2f);
            }
        }
        if (v.phase == RoundView.RESULT && v.sincePhase < 1) {
            boolean mine = v.winner >= 0 && v.mine[v.winner] > 0;
            boolean bet = false;
            for (long m : v.mine) if (m > 0) bet = true;
            if (bet) fx.sound(mine ? Snd.WIN_BIG : Snd.LOSE, 10, TOP + 3, 5, 1f, 1f);
        }
        if (v.phase == RoundView.RESULT && v.sincePhase < 40 && r.nextInt(2) == 0 && v.winner >= 0) {
            fx.particle(FxSink.CONFETTI, FINISH_X + rnd(r, 3), TOP + 8, laneZ(v.winner) + rnd(r, 3), rnd(r, 0.2f), 0.2f, rnd(r, 0.2f),
                    r.nextBoolean() ? RaceRules.COLORS[v.winner] : GOLD_RGB, 1f, 50);
        }
    }

    // =================================================================== vues
    @Override
    public RaceView newView() {
        return new RaceView();
    }

    @Override
    public RaceView demoView(float t) {
        RaceView v = newView();
        v.winner = 2;
        v.seed = 777;
        v.totals[0] = 300;
        v.totals[2] = 1500;
        v.mine[2] = 500;
        if (t < 30) {
            v.phase = RoundView.BETTING;
            v.countdown = (30 - t) / 20f + 3;
        } else if (t < 30 + v.raceTicks + 30) {
            v.phase = RoundView.RUNNING;
            v.sincePhase = t - 30;
        } else {
            v.phase = RoundView.RESULT;
            v.sincePhase = t - 30 - v.raceTicks - 30;
        }
        return v;
    }

    @Override
    public int demoLength() {
        return 340;
    }
}
