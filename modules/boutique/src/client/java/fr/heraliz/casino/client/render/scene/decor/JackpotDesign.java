package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Afficheur du jackpot : caisson d'acier rivete recupere sur un quai de gare, bande de danger, deux gyrophares
 * grillages. "JACKPOT" en neon ambre qui clignote pour attirer l'oeil, montant en direct sur des chiffres a
 * segments (deux segments morts, les unites qui tremblent), parasites de temps en temps.
 */
public final class JackpotDesign extends DecorDesign {
    static final float X0 = 1.2f, X1 = 30.8f, Y0 = 2.0f, Y1 = 14.0f, ZB = 0.3f, ZF = 3.2f;
    static final float SX0 = 2.6f, SX1 = 29.4f, SY0 = 3.3f, SY1 = 9.7f;
    static final float Z_BG = 3.26f, Z_TXT = 3.31f, Z_FX = 3.34f, Z_GLASS = 3.4f;
    static final float HX0 = 9.2f, HX1 = 22.8f, HY0 = 10.3f, HY1 = 13.7f;
    static final float PERIOD = 190;
    static final int AMBER = 0xFFA020;
    /** Chiffres : hauteur, largeur, pas, ecart entre groupes de trois. */
    static final float DH = 4.4f, DW = 2.2f, DS = 2.55f, DG = 0.7f, DY0 = 4.85f, DX1 = 28.0f;
    /** Segments morts : chiffre 4 segment e, chiffre 7 segment b. */
    static final long DEAD = (1L << (4 * 7 + 4)) | (1L << (7 * 7 + 1));

    private Mesh body = Mesh.EMPTY, bg = Mesh.EMPTY, glass = Mesh.EMPTY, header = Mesh.EMPTY, jetons = Mesh.EMPTY, lamps = Mesh.EMPTY;
    private final Slices slices = new Slices();

    public JackpotDesign() {
        super("ecran_jackpot", 0xFFB040);
        itemScale = 0.62f;
        itemCenter = new float[]{16, 8, 1.8f};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- equerres au mur
        for (float x : new float[]{5.5f, 26.5f}) {
            b.bevelBox(x - 1.2f, 3.2f, 0, x + 1.2f, 12.8f, 0.3f, 0.05f, RUST);
            screw(b, x, 3.8f, 0.3f, 0.22f, STEEL);
            screw(b, x, 12.2f, 0.3f, 0.22f, STEEL);
        }
        // ---- caisson d'acier et sa facade
        b.bevelBox(X0, Y0, ZB, X1, Y1, ZF - 0.2f, 0.4f, STEEL);
        // cadre de la facade (on voit l'ecran au travers)
        b.bevelBox(X0, Y0, ZF - 0.3f, X1, SY0, ZF, 0.12f, STEEL);
        b.bevelBox(X0, SY1, ZF - 0.3f, X1, Y1, ZF, 0.12f, STEEL);
        b.bevelBox(X0, SY0, ZF - 0.3f, SX0, SY1, ZF, 0.12f, STEEL);
        b.bevelBox(SX1, SY0, ZF - 0.3f, X1, SY1, ZF, 0.12f, STEEL);
        // fond noir derriere le mot JACKPOT
        b.bevelBox(HX0 - 0.5f, HY0 - 0.3f, ZF - 0.02f, HX1 + 0.5f, HY1 + 0.1f, ZF + 0.06f, 0.08f, DARK);
        // rivets en haut et en bas
        for (int i = 0; i < 12; i++) {
            float x = X0 + 0.9f + i * (X1 - X0 - 1.8f) / 11f;
            screw(b, x, Y1 - 0.45f, ZF, 0.16f, RUST);
            if (i % 2 == 0) screw(b, x, SY0 - 0.55f, ZF, 0.16f, RUST);
        }
        // pare-chocs a bandes de danger
        b.bevelBox(X0 - 0.2f, Y0 - 0.5f, ZB + 0.2f, X1 + 0.2f, Y0 + 0.35f, ZF + 0.15f, 0.12f, HAZARD);
        // ---- deux gyrophares grillages aux coins du haut (les ampoules sont dessinees a part)
        for (float x : new float[]{3.2f, 28.8f}) {
            b.push().translate(x, Y1, 1.8f);
            b.cylinder(0.95f, 0, 0.35f, 10, DARK, DARK, DARK);
            for (int k = 0; k < 6; k++) {
                double a = Math.PI * 2 * k / 6;
                float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
                b.tube(new float[][]{p(cx * 0.9f, 0.3f, cz * 0.9f), p(cx * 0.95f, 1.0f, cz * 0.95f), p(cx * 0.45f, 1.7f, cz * 0.45f), p(0, 1.85f, 0)},
                        new float[]{0.05f, 0.05f, 0.05f, 0.05f}, 4, RUST, false, false, 0, false);
            }
            b.pop();
        }
        // ---- gaine electrique qui descend sur le cote et entre dans le mur
        cable(b, new float[][]{p(X1 - 0.6f, Y0 + 3, ZB + 0.8f), p(X1 + 0.5f, Y0 + 2.2f, 1.2f), p(X1 + 0.9f, Y0 + 0.5f, 0.9f), p(X1 + 0.9f, Y0 - 1.2f, 0.35f)},
                0.28f, CABLE);
        b.bevelBox(X1 + 0.2f, Y0 - 2.0f, 0, X1 + 1.6f, Y0 - 0.9f, 0.5f, 0.1f, RUST);
        // autocollant "propriete du casino" sur le cote droit du cadre
        b.push().translate(25.5f, SY0 - 0.6f, ZF + 0.02f).rotateZ(-2);
        b.decalZ(STICKER, 0, 0, 0, 5.4f, 0.8f);
        b.pop();
        tape(b, TAPE, 29.0f, 9.2f, Z_GLASS + 0.05f, 3.4f, 0.8f, -55);
        Occlusion.bake(b, 0.35f, 2.4f, 0.45f, Float.NaN);
        body = b.build();

        bg = plate(LED_BG, SX0, SY0, SX1, SY1, Z_BG);
        glass = plate(GLASS, SX0 - 0.05f, SY0 - 0.05f, SX1 + 0.05f, SY1 + 0.05f, Z_GLASS);
        header = plate(JACKPOT, HX0, HY0, HX1, HY1, ZF + 0.1f);
        jetons = plate(JETONS, 13.8f, 3.55f, 18.2f, 4.65f, Z_TXT);
        MeshBuilder lb = new MeshBuilder();
        for (float x : new float[]{3.2f, 28.8f}) {
            lb.push().translate(x, Y1 + 0.35f, 1.8f);
            lb.sphereSection(0.72f, 10, 5, 0, 90, WHITE);
            lb.pop();
        }
        lamps = lb.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        int[] cyc = new int[1];
        float g = v.on ? glitch(s, PERIOD, cyc) : 0;
        int tick = (int) s.time;
        boolean black = v.on && blackout(s, g, cyc[0]);
        float t = s.time + s.seed * 400;
        // toutes les huit secondes, JACKPOT clignote trois fois et les gyrophares s'emballent
        float cycle = t % 160;
        boolean call = cycle < 24;
        boolean headOn = !call || ((int) (cycle / 4)) % 2 == 0;
        float hum = 0.92f + 0.08f * (float) Math.sin(s.time * 2.7f);

        // gyrophares : clignotent chacun leur tour
        int phase = (int) (t / (call ? 3 : 10)) % 2;
        out.solid(p, lamps, 0);
        if (v.on && !s.item) {
            for (int k = 0; k < 2; k++) {
                if (phase != k) continue;
                float x = k == 0 ? 3.2f : 28.8f;
                out.billboard(p, Sprite.HALO, x, Y1 + 0.8f, 1.8f, 2.2f, 0, 0xFF8A20, 0.55f);
            }
            out.additive(p, lamps, 0xFF7A18, 0.5f);
        }
        // mot JACKPOT
        slices.set(HY0, HY1, g, tick, cyc[0]);
        float headLit = !v.on || black || !headOn ? 0 : hum;
        out.sheet(SCREENS);
        neonQuad(p, out, header, JACKPOT, HX0, HY0, HX1, HY1, ZF + 0.1f, slices, AMBER, headLit, g);
        // fond de l'afficheur
        out.solid(p, bg, v.on && !black ? 0.5f : 0);
        // chiffres : segments allumes et eteints, les unites tremblent
        MeshBuilder on = scratch(), off = scratch2();
        digits(on, off, LED_ON, LED_OFF, v.on ? v.jackpot : 0, 9, DX1, DY0, DH, DW, DS, DG, Z_TXT, DEAD);
        Mesh onM = on.buildDynamic(), offM = off.buildDynamic();
        out.sheet(null);
        float dx = g > 0.05f ? (Ease.hash(tick * 17 + cyc[0]) - 0.5f) * 2.4f * g : 0;
        p.push().translate(dx, 0, 0);
        out.solid(p, offM, 0);
        if (v.on && !black) {
            out.solid(p, onM, 0);
            float lit = hum * (call ? 1.15f : 1f);
            out.additive(p, onM, AMBER, lit);
            if (g > 0.05f) {
                p.push().translate(-0.3f - 0.4f * g, 0, 0.02f);
                out.additive(p, onM, 0xFF2A1A, 0.5f * g);
                p.pop();
                p.push().translate(0.3f + 0.4f * g, 0, 0.03f);
                out.additive(p, onM, 0x1AE0FF, 0.5f * g);
                p.pop();
            }
            p.push().translate(0, 0, Z_FX);
            out.quad(p, Sprite.HALO, SX0 + 2, SY0 - 1, SX1 - 2, SY1 + 1, AMBER, 0.12f * hum);
            p.pop();
        }
        p.pop();
        out.sheet(SCREENS);
        slices.set(SY0, SY1, 0, tick, 0);
        neonQuad(p, out, jetons, JETONS, 13.8f, 3.55f, 18.2f, 4.65f, Z_TXT, slices, AMBER, v.on && !black ? 0.55f * hum : 0, 0);
        if (v.on) snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xFFE0C0, black ? 0 : 0.03f + 0.45f * g);
        out.sheet(null);
        out.translucent(p, glass, 0xFFFFFF, 0.5f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        int[] cyc = new int[1];
        float g = glitch(s, PERIOD, cyc);
        Scene prev = new Scene().set(s.tick - 1, 0);
        prev.seed = s.seed;
        float t = s.time + s.seed * 400;
        if (Math.floor(t) % 160 == 0) fx.sound(Snd.BELL, 16, 12, 3.5f, 0.25f, 1.6f);
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.jackpot = 1_254_300 + (long) (t * 37);
        return v;
    }

    @Override
    public DecorView newView() {
        DecorView v = super.newView();
        v.jackpot = 125_430;
        return v;
    }
}
