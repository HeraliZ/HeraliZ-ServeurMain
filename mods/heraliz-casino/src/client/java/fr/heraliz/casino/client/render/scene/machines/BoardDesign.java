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
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Tableau des gagnants : un vieux moniteur cathodique de bunker pose sur un chariot rouille, alimente par une
 * batterie de voiture. L'ecran affiche le classement des plus gros gagnants du casino et le record ; il est use
 * (lignes de balayage, rémanence) et "glitche" toutes les cinq secondes environ : lignes decalees, couleurs qui
 * se separent, neige, bande noire.
 */
public final class BoardDesign extends MachineDesign<BoardView> {
    // ---- atlas : partage avec tools/textures (PaintBoard)
    public static final int[] R_CASE = {0, 0, 256, 256};
    public static final int[] R_RUST = {256, 0, 256, 256};
    public static final int[] R_SCREEN = {512, 0, 512, 256};
    public static final int[] R_NOISE = {0, 256, 256, 256};
    public static final int[] R_BEZEL = {256, 256, 256, 128};
    public static final int[] R_TAPE = {256, 384, 256, 128};
    public static final int[] R_BATTERY = {512, 256, 256, 256};
    public static final int[] R_LED = {768, 256, 64, 64};
    public static final int[] R_CABLE = {832, 256, 128, 64};
    public static final int[] R_GLASS = {768, 320, 256, 128};
    public static final int[] R_STICKER = {768, 448, 256, 64};
    public static final int[] R_DARK = {0, 512, 128, 64};

    static final Mat CASE = FruitsDesign.tri(R_CASE, 16), RUST = FruitsDesign.tri(R_RUST, 12), SCREEN = FruitsDesign.fit(R_SCREEN);
    static final Mat NOISE = FruitsDesign.fit(R_NOISE), BEZEL = FruitsDesign.tri(R_BEZEL, 8), TAPE = FruitsDesign.fit(R_TAPE);
    static final Mat BATTERY = FruitsDesign.tri(R_BATTERY, 8), LED = FruitsDesign.fit(R_LED), CABLE = FruitsDesign.tri(R_CABLE, 6);
    static final Mat GLASS = FruitsDesign.fit(R_GLASS), STICKER = FruitsDesign.fit(R_STICKER), DARK = FruitsDesign.tri(R_DARK, 6);

    /** Ecran (px) : zone utile et profondeur. */
    static final float SX0 = -5.6f, SX1 = 21.6f, SY0 = 16.8f, SY1 = 35.2f, SZ = 12.1f, FZ = 12.6f;
    static final int GREEN = 0x9CFF3A, GOLD = 0xFFD36A, SILVER = 0xD8DCE0, BRONZE = 0xE0985A, DIM = 0x6A8A5A, RED = 0xFF4A3A;
    /** Couleurs des ecrans des perdants et des parieurs. */
    static final int LOSS = 0xFF5A4A, BET = 0xFFC040;
    /** Periode moyenne entre deux glitchs (ticks). */
    static final int PERIOD = 100;

    private Mesh body = Mesh.EMPTY, screenBg = Mesh.EMPTY, glass = Mesh.EMPTY, led = Mesh.EMPTY, band = Mesh.EMPTY;

    /** 0 gagnants, 1 perdants, 2 parieurs (pour l'apercu et l'inventaire). */
    private final int kind;

    public BoardDesign() {
        this(0);
    }

    public BoardDesign(int kind) {
        super("tableau_gagnants", kind == 1 ? LOSS : kind == 2 ? BET : GREEN, RED);
        this.kind = kind;
        labelY = 44;
        itemScale = 0.3f;
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        float[] nz = v(0, 0, 1);
        // ---- chariot : quatre montants, deux plateaux, roulettes
        for (float x : new float[]{-7.6f, 23.6f}) {
            for (float z : new float[]{5.6f, 11.8f}) {
                b.push().translate(x, 1.2f, z);
                b.cylinder(0.4f, 0, 12.4f, 8, RUST, null, RUST);
                b.pop();
                b.push().translate(x, 0.6f, z).rotateZ(90);
                b.cylinder(0.6f, -0.3f, 0.3f, 10, DARK, DARK, DARK);
                b.pop();
            }
        }
        b.bevelBox(-8.4f, 1.4f, 5, 24.4f, 2.0f, 12.4f, 0.1f, RUST);
        b.bevelBox(-8.4f, 12.8f, 5, 24.4f, 13.6f, 12.4f, 0.12f, RUST);
        // batterie de voiture et cables sur le plateau du bas
        b.bevelBox(-4.5f, 2.0f, 6.2f, 3.5f, 7.0f, 11.2f, 0.25f, BATTERY);
        for (float x : new float[]{-3.2f, 2.2f}) {
            b.push().translate(x, 7.0f, 7.4f);
            b.cylinder(0.45f, 0, 0.6f, 8, x < 0 ? RUST : DARK, null, x < 0 ? RUST : DARK);
            b.pop();
        }
        float[][] c1 = {p(-3.2f, 7.5f, 7.4f), p(-3.6f, 10.5f, 8.6f), p(-1.5f, 14.8f, 7.2f), p(0.5f, 17.5f, 6.8f)};
        float[][] c2 = {p(2.2f, 7.5f, 7.4f), p(3.6f, 9.8f, 9.8f), p(6.2f, 13.9f, 8.4f), p(8.5f, 17f, 6.8f)};
        b.tube(c1, new float[]{0.2f, 0.2f, 0.2f, 0.2f}, 6, CABLE, true, true, 0, false);
        b.tube(c2, new float[]{0.2f, 0.2f, 0.2f, 0.2f}, 6, CABLE, true, true, 0, false);
        // pinces crocodile
        b.bevelBox(-3.7f, 7.3f, 7.0f, -2.7f, 8.0f, 7.9f, 0.1f, RUST);
        b.bevelBox(1.7f, 7.3f, 7.0f, 2.7f, 8.0f, 7.9f, 0.1f, DARK);
        // ---- moniteur : caisson avant, bosse arriere du tube, facade autour de l'ecran
        b.bevelBox(-8, 13.6f, 8.6f, 24, 38.4f, FZ - 0.2f, 0.9f, CASE, null, MeshBuilder.SKIP_PZ);
        b.push().translate(8, 14.4f, 8.8f);
        b.bevelBox(-12.5f, 0, -4.8f, 12.5f, 22.4f, 0, 1.6f, CASE);
        b.bevelBox(-8f, 3, -6.6f, 8f, 19f, -4.6f, 1.2f, CASE);
        b.pop();
        float z = FZ - 0.2f;
        b.face(CASE, nz, p(-7.4f, 14.2f, z), p(23.4f, 14.2f, z), p(23.4f, SY0 - 0.8f, z), p(-7.4f, SY0 - 0.8f, z));
        b.face(CASE, nz, p(-7.4f, SY1 + 0.8f, z), p(23.4f, SY1 + 0.8f, z), p(23.4f, 37.8f, z), p(-7.4f, 37.8f, z));
        b.face(CASE, nz, p(-7.4f, SY0 - 0.8f, z), p(SX0 - 0.8f, SY0 - 0.8f, z), p(SX0 - 0.8f, SY1 + 0.8f, z), p(-7.4f, SY1 + 0.8f, z));
        b.face(CASE, nz, p(SX1 + 0.8f, SY0 - 0.8f, z), p(23.4f, SY0 - 0.8f, z), p(23.4f, SY1 + 0.8f, z), p(SX1 + 0.8f, SY1 + 0.8f, z));
        // bord interieur noir (embrasure bombee de l'ecran)
        b.bevelBox(SX0 - 0.8f, SY1, SZ - 0.1f, SX1 + 0.8f, SY1 + 0.8f, z + 0.1f, 0.2f, BEZEL);
        b.bevelBox(SX0 - 0.8f, SY0 - 0.8f, SZ - 0.1f, SX1 + 0.8f, SY0, z + 0.1f, 0.2f, BEZEL);
        b.bevelBox(SX0 - 0.8f, SY0, SZ - 0.1f, SX0, SY1, z + 0.1f, 0.2f, BEZEL);
        b.bevelBox(SX1, SY0, SZ - 0.1f, SX1 + 0.8f, SY1, z + 0.1f, 0.2f, BEZEL);
        // boutons et grille du haut-parleur sous l'ecran
        for (int k = 0; k < 4; k++) {
            b.push().translate(14.5f + k * 1.8f, 15.2f, z).rotateX(90);
            b.cylinder(0.45f, 0, 0.35f, 8, BEZEL, null, BEZEL);
            b.pop();
        }
        for (int k = 0; k < 6; k++) b.bevelBox(-6.5f, 14.6f + k * 0.3f, z - 0.02f, 2.5f, 14.72f + k * 0.3f, z + 0.06f, 0.02f, BEZEL);
        // antenne tordue et adhesif
        b.push().translate(18, 38.4f, 9.2f).rotateZ(-22);
        b.cylinder(0.12f, 0, 7, 5, RUST, null, RUST);
        b.pop();
        b.push().translate(18, 38.4f, 9.2f).rotateZ(35).rotateX(12);
        b.cylinder(0.12f, 0, 5.5f, 5, RUST, null, RUST);
        b.pop();
        b.push().translate(17.6f, 38.3f, 9.2f);
        b.sphere(0.6f, 8, 5, DARK);
        b.pop();
        b.push().translate(-4.2f, 37.4f, FZ - 0.14f).rotateZ(-28);
        b.decalZ(TAPE, 0, 0, 0, 8, 1.6f);
        b.pop();
        b.push().translate(22.2f, 17.2f, FZ - 0.14f).rotateZ(62);
        b.decalZ(TAPE, 0, 0, 0, 7, 1.5f);
        b.pop();
        b.push().translate(5f, 37.0f, FZ - 0.14f);
        b.decalZ(STICKER, 0, 0, 0, 8, 1.6f);
        b.pop();
        Occlusion.bake(b, 0.4f, 3f, 0.45f, 0f);
        body = b.build();

        MeshBuilder sb = new MeshBuilder();
        sb.face(SCREEN, nz, p(SX0, SY0, SZ), p(SX1, SY0, SZ), p(SX1, SY1, SZ), p(SX0, SY1, SZ));
        screenBg = sb.build();
        MeshBuilder gl = new MeshBuilder();
        gl.face(GLASS, nz, p(SX0, SY0, SZ + 0.35f), p(SX1, SY0, SZ + 0.35f), p(SX1, SY1, SZ + 0.35f), p(SX0, SY1, SZ + 0.35f));
        glass = gl.build();
        MeshBuilder ld = new MeshBuilder();
        ld.push().translate(11.5f, 15.2f, z + 0.05f).rotateX(90);
        ld.cylinder(0.3f, 0, 0.2f, 8, LED, null, LED);
        ld.pop();
        led = ld.build();
        MeshBuilder bd = new MeshBuilder();
        bd.face(DARK, nz, p(SX0, -0.6f, 0), p(SX1, -0.6f, 0), p(SX1, 0.6f, 0), p(SX0, 0.6f, 0));
        band = bd.build();
    }

    // =================================================================== glitch
    /** Intensite du glitch en cours (0 = ecran stable) ; cycle = numero du glitch (graine des effets). */
    static float glitch(Scene s, int[] cycle) {
        float t = s.time + s.seed * 1000;
        int c = (int) Math.floor(t / PERIOD);
        float start = c * PERIOD + Ease.hash(c * 31 + 7) * 40, len = 6 + Ease.hash(c * 17 + 3) * 8;
        if (cycle != null) cycle[0] = c;
        if (t < start || t > start + len) return 0;
        return Ease.bell(t, start - 1, start + len + 1) * (0.7f + 0.3f * Ease.hash((int) t * 13 + c));
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, BoardView v, Pose p, DrawList out) {
        int[] cyc = new int[1];
        float g = s.item ? 0 : glitch(s, cyc);
        int tick = (int) s.time;
        // voyant rouge "REC" qui clignote
        boolean rec = ((int) (s.time / 12)) % 2 == 0;
        out.solid(p, led, rec ? 1 : 0.1f);
        if (rec && !s.item) out.billboard(p, Sprite.HALO, 11.5f, 15.2f, FZ + 0.3f, 1.4f, 0, RED, 0.35f);
        // fond de l'ecran : scintille, plonge dans le noir pendant les glitchs
        float flicker = 0.9f + 0.1f * (float) Math.sin(s.time * 3.1f) * (float) Math.sin(s.time * 0.7f);
        out.solid(p, screenBg, (0.55f - 0.35f * g) * flicker);
        if (!s.item) {
            content(s, v, p, out, g, cyc[0], tick);
            // barre lumineuse qui descend lentement (balayage)
            float roll = (s.time * 0.35f + s.seed * 50) % (SY1 - SY0 + 6);
            float y = SY1 + 3 - roll;
            if (y > SY0 && y < SY1) {
                p.push().translate(0, y, SZ + 0.12f);
                out.additive(p, band, 0x3A6A2A, 0.18f);
                p.pop();
            }
            // neige et bande noire pendant le glitch
            if (g > 0.02f) {
                MeshBuilder nb = new MeshBuilder();
                float u0 = Ease.hash(tick * 7 + 1) * 0.5f, v0 = Ease.hash(tick * 11 + 2) * 0.5f;
                Mat m = NOISE.sub(u0, v0, u0 + 0.5f, v0 + 0.5f);
                nb.face(m, v(0, 0, 1), p(SX0, SY0, SZ + 0.2f), p(SX1, SY0, SZ + 0.2f), p(SX1, SY1, SZ + 0.2f), p(SX0, SY1, SZ + 0.2f));
                out.additive(p, nb.buildDynamic(), 0xC8FFC8, 0.55f * g);
                float by = SY0 + Ease.hash(tick * 3 + cyc[0]) * (SY1 - SY0);
                float bh = 0.8f + Ease.hash(tick * 5 + 9) * 2.5f;
                p.push().translate(0, by, SZ + 0.25f).scale(1, bh, 1);
                out.solid(p, band, 0);
                p.pop();
            } else {
                // un peu de neige en permanence (vieux tube)
                MeshBuilder nb = new MeshBuilder();
                float u0 = Ease.hash(tick * 7 + 1) * 0.5f, v0 = Ease.hash(tick * 11 + 2) * 0.5f;
                nb.face(NOISE.sub(u0, v0, u0 + 0.5f, v0 + 0.5f), v(0, 0, 1), p(SX0, SY0, SZ + 0.2f), p(SX1, SY0, SZ + 0.2f), p(SX1, SY1, SZ + 0.2f),
                        p(SX0, SY1, SZ + 0.2f));
                out.additive(p, nb.buildDynamic(), 0x80C880, 0.07f);
            }
        }
        out.translucent(p, glass, 0xFFFFFF, 0.5f);
    }

    /** Texte du classement, avec decalages et aberration chromatique pendant le glitch. */
    private void content(Scene s, BoardView v, Pose p, DrawList out, float g, int cycle, int tick) {
        float z = SZ + 0.15f;
        float shiftY = g > 0 ? (Ease.hash(tick * 19 + cycle) - 0.5f) * 1.6f * g : 0;
        String[] titles = {"TOP GAGNANTS", "TOP PERDANTS", "TOP PARIEURS"};
        String[] subs = {"PLUS GROS GAINS EN UNE PARTIE", "BILAN : GAINS MOINS MISES", "LES PLUS GROSSES MISES CUMULEES"};
        String[] empty = {"AUCUN GAGNANT... POUR L'INSTANT", "PERSONNE N'A ENCORE PERDU", "AUCUNE MISE POUR L'INSTANT"};
        int k = Math.max(0, Math.min(2, v.kind));
        int theme = k == 1 ? LOSS : k == 2 ? BET : GREEN;
        row(p, out, titles[k], 8, SY1 - 1.9f + shiftY, z, 1.6f, theme, DrawList.T_CENTER | DrawList.T_BOLD, g, 0, tick, cycle);
        row(p, out, subs[k], 8, SY1 - 3.5f + shiftY, z, 0.6f, DIM, DrawList.T_CENTER, g, 1, tick, cycle);
        int n = Math.min(v.names.length, Math.min(v.values.length, 8));
        if (n == 0) {
            row(p, out, empty[k], 8, (SY0 + SY1) / 2 + shiftY, z, 0.95f, theme, DrawList.T_CENTER, g, 2, tick, cycle);
        }
        for (int i = 0; i < n; i++) {
            float y = SY1 - 5.4f - i * 1.45f + shiftY;
            int col = i == 0 ? GOLD : i == 1 ? SILVER : i == 2 ? BRONZE : theme;
            row(p, out, (i + 1) + ".", SX0 + 1.8f, y, z, 0.95f, col, DrawList.T_RIGHT, g, 3 + i, tick, cycle);
            String name = v.names[i];
            row(p, out, name, SX0 + 2.3f, y, z, fit(name, 0.95f, k == 0 ? 10f : 13f, false), col, 0, g, 3 + i, tick, cycle);
            String amount = (k == 0 ? "+" : k == 1 ? "-" : "") + fmt(v.values[i]);
            row(p, out, amount, SX1 - 0.8f, y, z, 0.95f, k == 1 ? LOSS : col, DrawList.T_RIGHT, g, 3 + i, tick, cycle);
            // machine du plus gros gain, en petit
            if (k == 0 && i < v.machines.length && !v.machines[i].isEmpty()) {
                String m = v.machines[i];
                row(p, out, m, SX1 - 6.8f, y - 0.1f, z, fit(m, 0.55f, 5.4f, false), DIM, DrawList.T_RIGHT, g, 3 + i, tick, cycle);
            }
        }
        if (v.recordAmount > 0) {
            String rec = "RECORD : " + v.recordName + " — " + fmt(v.recordAmount) + (v.recordMachine.isEmpty() ? "" : " (" + v.recordMachine + ")");
            row(p, out, rec, 8, SY0 + 0.9f + shiftY, z, fit(rec, 0.7f, SX1 - SX0 - 1.2f, false), GOLD, DrawList.T_CENTER, g, 12, tick, cycle);
        }
    }

    /** Une ligne de texte ; pendant le glitch, decalage horizontal et doubles rouge / cyan. */
    private static void row(Pose p, DrawList out, String txt, float x, float y, float z, float size, int col, int flags, float g, int line, int tick, int cycle) {
        if (g <= 0.02f) {
            out.text(p, txt, x, y, z, size, col, 0.95f, flags);
            return;
        }
        float h = Ease.hash(line * 97 + tick * 13 + cycle * 7);
        if (h > 0.85f) return;
        float dx = (Ease.hash(line * 53 + tick * 29) - 0.5f) * 4f * g;
        out.text(p, txt, x + dx - 0.3f * g, y, z - 0.01f, size, 0xFF2A2A, 0.6f * g, flags);
        out.text(p, txt, x + dx + 0.3f * g, y, z - 0.01f, size, 0x2AE8FF, 0.6f * g, flags);
        out.text(p, txt, x + dx, y, z, size, col, 0.9f, flags);
    }

    // =================================================================== particules
    /** Etincelles au debut de chaque glitch (sans bruit : le glitch reste purement visuel). */
    @Override
    public void tickFx(Scene s, BoardView v, FxSink fx, Random r) {
        int[] cyc = new int[1];
        float g = glitch(s, cyc);
        Scene prev = new Scene().set(s.tick - 1, 0);
        prev.seed = s.seed;
        if (g > 0 && glitch(prev, null) <= 0) {
            for (int i = 0; i < 4; i++) fx.particle(FxSink.SPARK, SX1 + 0.5f, 15 + rnd(r, 1), FZ + 0.3f, rnd(r, 0.05f), 0.08f, 0.05f, 0xFFE0A0, 0.4f, 10);
        }
        if (r.nextInt(160) == 0) fx.particle(FxSink.SPARK, -3.2f, 7.8f, 7.6f, rnd(r, 0.04f), 0.06f, rnd(r, 0.04f), 0xFFE0A0, 0.4f, 8);
    }

    // =================================================================== camera
    @Override
    public void camera(BoardView v, Scene s, int mode, Shot out) {
        out.set(8, 27, 42, 8, 26, 10, 50);
    }

    // =================================================================== vues
    @Override
    public BoardView newView() {
        BoardView v = new BoardView();
        v.kind = kind;
        return v;
    }

    @Override
    public BoardView demoView(float t) {
        BoardView v = newView();
        v.names = new String[]{"Logan", "xX_Rat_Xx", "Survivant42", "Dr_Zomb", "Mlle_Ciseaux", "Bunker_Boss", "Pioche", "Grizzly"};
        v.values = new long[]{1_254_300, 820_500, 412_000, 205_800, 98_450, 51_200, 12_300, 4_050};
        v.machines = new String[]{"Rotten", "Olympe", "Loup", "Roulette", "Crash", "Plinko", "Mines", "Course"};
        v.recordName = "Logan";
        v.recordAmount = 500_000;
        v.recordMachine = "Rotten";
        return v;
    }

    @Override
    public int demoLength() {
        return PERIOD * 2;
    }
}
