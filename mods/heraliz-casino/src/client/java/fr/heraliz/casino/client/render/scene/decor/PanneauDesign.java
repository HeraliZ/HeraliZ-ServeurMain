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
 * Petit ecran OUVERT / FERME pendu de travers a un clou par deux bouts de chaine, branche sur une vieille prise
 * murale. Le mot est en neon (vert : ouvert, rouge : ferme) et gresille comme le reste du bunker.
 */
public final class PanneauDesign extends DecorDesign {
    static final float CX = 8, CY = 7, W = 11, H = 6, ZB = 0.35f, ZF = 1.55f;
    static final float SX0 = 3.4f, SX1 = 12.6f, SY0 = 4.9f, SY1 = 9.1f;
    static final float Z_BG = 1.61f, Z_TXT = 1.66f, Z_FX = 1.69f, Z_GLASS = 1.74f;
    static final float WX0 = 3.7f, WX1 = 12.3f, WY0 = 5.9f, WY1 = 8.05f;
    static final float NAIL_X = 8, NAIL_Y = 14.2f, TILT = 3.5f, PERIOD = 170;
    static final int GREEN = 0x3CFF4A, RED = 0xFF2A22;

    private Mesh body = Mesh.EMPTY, bg = Mesh.EMPTY, glass = Mesh.EMPTY, ouvert = Mesh.EMPTY, ferme = Mesh.EMPTY, led = Mesh.EMPTY;
    private final Slices slices = new Slices();

    public PanneauDesign() {
        super("panneau_ouvert", 0x5CFF6A);
        itemScale = 1.15f;
        itemCenter = new float[]{8, 8.5f, 1};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- clou plante dans le mur
        b.push().translate(NAIL_X, NAIL_Y, 0).rotateX(90);
        b.cylinder(0.13f, 0, 1.05f, 6, STEEL, null, null);
        b.cylinder(0.32f, 1.05f, 1.15f, 8, RUST, RUST, RUST);
        b.pop();
        // ---- boitier (penche : il pend au clou)
        b.push().translate(NAIL_X, NAIL_Y, 0).rotateZ(TILT).translate(-NAIL_X, -NAIL_Y, 0);
        b.push().translate(CX, CY, (ZB + ZF) / 2);
        b.roundedSlab(W, H, ZF - ZB, 0.8f, 0.24f, 3, CASE, CASE);
        b.pop();
        float lw = 0.26f, z0 = ZF - 0.03f, z1 = ZF + 0.12f;
        b.bevelBox(SX0 - lw, SY1, z0, SX1 + lw, SY1 + lw, z1, 0.05f, DARK);
        b.bevelBox(SX0 - lw, SY0 - lw, z0, SX1 + lw, SY0, z1, 0.05f, DARK);
        b.bevelBox(SX0 - lw, SY0, z0, SX0, SY1, z1, 0.05f, DARK);
        b.bevelBox(SX1, SY0, z0, SX1 + lw, SY1, z1, 0.05f, DARK);
        // pitons sur le dessus et chainettes jusqu'au clou (tirees par le poids)
        float[][] eyes = {{4.2f, CY + H / 2 + 0.28f}, {11.8f, CY + H / 2 + 0.28f}};
        for (float[] e : eyes) {
            b.push().translate(e[0], e[1], (ZB + ZF) / 2);
            b.torusXY(0.28f, 0.07f, 8, 4, STEEL);
            b.pop();
        }
        b.pop();
        // les chaines partent des pitons (reperes penches) et montent au clou (repere du mur)
        for (float[] e : eyes) {
            float[] w = rotAround(e[0], e[1] + 0.25f, NAIL_X, NAIL_Y, TILT);
            b.chain(new float[][]{p(w[0], w[1], (ZB + ZF) / 2), p(NAIL_X + (e[0] < CX ? -0.15f : 0.15f), NAIL_Y - 0.15f, 0.85f)}, 0.62f, 0.36f, 0.08f, RUST, 8, 4);
        }
        b.push().translate(NAIL_X, NAIL_Y, 0).rotateZ(TILT).translate(-NAIL_X, -NAIL_Y, 0);
        // vis et autocollant "danger" sur le bandeau
        screw(b, 3.0f, 4.45f, ZF, 0.2f, RUST);
        screw(b, 13.0f, 9.6f, ZF, 0.2f, STEEL);
        b.push().translate(6.6f, 4.47f, ZF + 0.02f);
        b.decalZ(STICKER2, 0, 0, 0, 3.2f, 0.72f);
        b.pop();
        // scotch sur le coin superieur gauche
        tape(b, TAPE, 3.3f, 9.4f, Z_GLASS + 0.05f, 2.6f, 0.7f, 50);
        b.pop();
        // ---- cable jusqu'a une vieille prise murale
        float[] out0 = rotAround(3.9f, CY - H / 2 + 0.1f, NAIL_X, NAIL_Y, TILT);
        cable(b, new float[][]{p(out0[0], out0[1], 0.95f), p(out0[0] - 0.2f, out0[1] - 0.9f, 1.05f), p(3.3f, 2.3f, 0.95f), p(3.0f, 1.45f, 0.7f)}, 0.15f, CABLE);
        b.bevelBox(1.8f, 0.2f, 0, 4.2f, 2.6f, 0.32f, 0.12f, ENAMEL_IN);
        b.push().translate(3.0f, 1.4f, 0.32f).rotateX(90);
        b.cylinder(0.55f, 0, 0.45f, 10, DARK, null, DARK);
        b.pop();
        screw(b, 3.0f, 2.3f, 0.32f, 0.12f, STEEL);
        Occlusion.bake(b, 0.25f, 1.6f, 0.4f, Float.NaN);
        body = b.build();

        bg = plate(SMALL_BG, SX0, SY0, SX1, SY1, Z_BG);
        glass = plate(GLASS_S, SX0 - 0.04f, SY0 - 0.04f, SX1 + 0.04f, SY1 + 0.04f, Z_GLASS);
        ouvert = plate(OUVERT, WX0, WY0, WX1, WY1, Z_TXT);
        ferme = plate(FERME, WX0, WY0, WX1, WY1, Z_TXT);
        MeshBuilder lb = new MeshBuilder();
        lb.push().translate(12.2f, 4.45f, ZF).rotateX(90);
        lb.cylinder(0.16f, 0, 0.12f, 8, WHITE, null, WHITE);
        lb.pop();
        led = lb.build();
    }

    /** Point (x, y) tourne de "deg" autour de (cx, cy). */
    static float[] rotAround(float x, float y, float cx, float cy, float deg) {
        double a = Math.toRadians(deg);
        float dx = x - cx, dy = y - cy;
        return new float[]{cx + (float) (dx * Math.cos(a) - dy * Math.sin(a)), cy + (float) (dx * Math.sin(a) + dy * Math.cos(a))};
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        p.push().translate(NAIL_X, NAIL_Y, 0).rotZ(TILT).translate(-NAIL_X, -NAIL_Y, 0);
        boolean open = Math.floorMod(v.mode, 2) == 0;
        int color = open ? GREEN : RED;
        int[] cyc = new int[1];
        float g = v.on ? glitch(s, PERIOD, cyc) : 0;
        int tick = (int) s.time;
        boolean black = v.on && blackout(s, g, cyc[0]);
        float t = s.time + s.seed * 300;
        // coupures de courant breves (vieille prise)
        boolean cut = v.on && !s.item && Ease.hash((int) (t / 2) * 11 + 3) > 0.985f;
        float hum = 0.9f + 0.1f * (float) Math.sin(s.time * 3.1f);
        float lit = !v.on || black || cut ? 0 : hum;

        // voyant de marche
        out.solid(p, led, 0);
        if (v.on) {
            out.additive(p, led, open ? 0x40FF40 : 0xFF3020, 0.9f);
            if (!s.item) out.billboard(p, Sprite.HALO, 12.2f, 4.45f, ZF + 0.3f, 0.7f, 0, open ? 0x40FF40 : 0xFF3020, 0.3f);
        }
        out.sheet(SCREENS);
        out.solid(p, bg, lit > 0 ? (0.45f - 0.25f * g) * hum : 0);
        if (lit > 0) {
            p.push().translate(0, 0, Z_FX);
            out.quad(p, Sprite.HALO, SX0, SY0 - 1, SX1, SY1 + 1, color, 0.14f * hum);
            p.pop();
        }
        slices.set(SY0, SY1, g, tick, cyc[0]);
        neonQuad(p, out, open ? ouvert : ferme, open ? OUVERT : FERME, WX0, WY0, WX1, WY1, Z_TXT, slices, color, lit, g);
        if (v.on) snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xD0FFD0, black ? 0 : 0.04f + 0.5f * g);
        out.sheet(null);
        out.translucent(p, glass, 0xFFFFFF, 0.6f);
        p.pop();
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        int[] cyc = new int[1];
        float g = glitch(s, PERIOD, cyc);
        Scene prev = new Scene().set(s.tick - 1, 0);
        prev.seed = s.seed;
        if (r.nextInt(600) == 0) fx.particle(FxSink.SPARK, 3.0f, 1.9f, 0.6f, rnd(r, 0.04f), 0.05f, 0.04f, 0xFFE0A0, 0.3f, 8);
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.mode = (int) (t / 100) % 2;
        return v;
    }
}
