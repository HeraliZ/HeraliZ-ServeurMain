package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
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
 * Vieille tele des annees 70 en simili bois, antennes en V (une tordue, rafistolee au scotch), boutons ronds et
 * haut-parleur. Chaines : publicite du casino (lettres qui changent de couleur), jackpot en direct, neige, mire
 * "PAS DE SIGNAL". Image qui roule et parasites de temps en temps.
 */
public final class TeleDesign extends DecorDesign {
    static final float SX0 = 3.0f, SX1 = 10.7f, SY0 = 2.9f, SY1 = 8.9f, ZF = 13.2f;
    static final float Z_BG = 13.42f, Z_TXT = 13.46f, Z_FX = 13.5f, PERIOD = 150;
    static final int[] AD_COLORS = {0xFF2A8C, 0x2AE8FF, 0xFFB02A, 0x5CFF4A, 0xFF2A2A, 0xB05AFF};

    private Mesh body = Mesh.EMPTY, bg = Mesh.EMPTY, glass = Mesh.EMPTY, mire = Mesh.EMPTY, welcome = Mesh.EMPTY, jackpot = Mesh.EMPTY,
            led = Mesh.EMPTY;
    private final Mesh[] letters = new Mesh[6];
    private final Slices slices = new Slices();
    /** Lettres de la publicite : position et taille. */
    static final float LW = 1.16f, LH = LW * LETTER_H / LETTER_W, LS = 1.12f, LY0 = 5.35f;
    static final float LX0 = (SX0 + SX1) / 2 - (5 * LS + LW) / 2;

    public TeleDesign() {
        super("tele_crt", 0x9CFF3A);
        itemScale = 1.05f;
        itemCenter = new float[]{8, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- caisson en simili bois et arriere du tube
        b.bevelBox(2.0f, 1.2f, 7.6f, 14.0f, 10.6f, ZF, 0.6f, TVWOOD);
        b.bevelBox(3.6f, 2.4f, 2.6f, 12.4f, 9.6f, 7.9f, 0.9f, CASE);
        b.bevelBox(5.2f, 3.6f, 1.6f, 10.8f, 8.4f, 2.8f, 0.6f, CASE);
        // pieds
        for (float x : new float[]{3.0f, 13.0f}) {
            for (float z : new float[]{8.5f, 12.4f}) {
                b.push().translate(x, 0, z);
                b.cylinder(0.35f, 0, 1.25f, 8, DARK, DARK, DARK);
                b.pop();
            }
        }
        // ---- facade noire
        b.bevelBox(2.5f, 1.7f, ZF - 0.05f, 13.5f, 10.1f, ZF + 0.15f, 0.15f, CASE);
        // embrasure de l'ecran (arrondie aux coins)
        float lw = 0.35f, z0 = ZF + 0.1f, z1 = ZF + 0.42f;
        b.bevelBox(SX0 - lw, SY1, z0, SX1 + lw, SY1 + lw, z1, 0.12f, DARK);
        b.bevelBox(SX0 - lw, SY0 - lw, z0, SX1 + lw, SY0, z1, 0.12f, DARK);
        b.bevelBox(SX0 - lw, SY0, z0, SX0, SY1, z1, 0.12f, DARK);
        b.bevelBox(SX1, SY0, z0, SX1 + lw, SY1, z1, 0.12f, DARK);
        // boutons, interrupteur, haut-parleur
        for (float y : new float[]{8.0f, 6.3f}) {
            b.push().translate(12.15f, y, ZF + 0.15f).rotateX(90);
            b.cylinder(0.62f, 0, 0.55f, 12, DARK, null, KNOB);
            b.pop();
        }
        b.bevelBox(11.7f, 4.4f, ZF + 0.12f, 12.6f, 4.9f, ZF + 0.4f, 0.08f, STEEL);
        b.bevelBox(11.1f, 2.0f, ZF + 0.12f, 13.2f, 3.9f, ZF + 0.2f, 0.06f, GRILLE);
        // ---- antennes en V sur leur boule : une droite, une tordue et scotchee
        b.push().translate(8, 10.6f, 9.8f);
        b.sphereSection(1.0f, 12, 4, 0, 90, CASE);
        b.pop();
        b.tube(new float[][]{p(8.2f, 11.4f, 9.8f), p(10.2f, 14.6f, 9.1f), p(12.6f, 18.2f, 8.4f)}, new float[]{0.08f, 0.07f, 0.06f}, 5, STEEL, true, true, 0, false);
        b.push().translate(12.6f, 18.2f, 8.4f);
        b.sphere(0.18f, 6, 4, STEEL);
        b.pop();
        b.tube(new float[][]{p(7.8f, 11.4f, 9.8f), p(6.4f, 14.0f, 9.2f), p(5.9f, 15.2f, 9.0f), p(4.2f, 16.1f, 8.8f), p(2.9f, 17.4f, 8.5f)},
                new float[]{0.08f, 0.07f, 0.07f, 0.06f, 0.06f}, 5, STEEL, true, true, 0, false);
        b.push().translate(2.9f, 17.4f, 8.5f);
        b.sphere(0.18f, 6, 4, STEEL);
        b.pop();
        b.push().translate(6.0f, 15.05f, 9.0f).rotateZ(-40);
        b.cylinder(0.2f, -0.45f, 0.45f, 8, TAPE, TAPE, TAPE);
        b.pop();
        // scotch sur le coin du caisson, etiquette "ne pas toucher" sur le dessus
        tape(b, TAPE, 2.9f, 9.7f, ZF + 0.2f, 3.2f, 0.9f, 45);
        b.push().translate(8.4f, 10.62f, 11.6f).rotateX(-90).rotateZ(6);
        b.decalZ(TAG, 0, 0, 0, 4.4f, 1.1f);
        b.pop();
        // ---- cable qui traine par terre
        cable(b, new float[][]{p(8, 3.0f, 1.7f), p(8.4f, 1.2f, 1.0f), p(9.2f, 0.2f, 0.3f), p(11.5f, 0.2f, -0.5f), p(13.0f, 0.2f, -1.6f)}, 0.18f, CABLE);
        Occlusion.bake(b, 0.3f, 2.2f, 0.45f, 0f);
        body = b.build();

        bg = plate(TV_BG, SX0, SY0, SX1, SY1, Z_BG);
        mire = plate(MIRE, SX0, SY0, SX1, SY1, Z_BG + 0.01f);
        welcome = plate(WELCOME, SX0 + 0.5f, 3.75f, SX1 - 0.5f, 4.6f, Z_TXT);
        jackpot = plate(JACKPOT, SX0 + 0.8f, 6.6f, SX1 - 0.8f, 8.1f, Z_TXT);
        for (int k = 0; k < 6; k++) {
            float x = LX0 + k * LS;
            letters[k] = plate(letter(k), x, LY0, x + LW, LY0 + LH, Z_TXT);
        }
        // verre bombe devant l'ecran
        MeshBuilder gb = new MeshBuilder();
        gb.surface((u, v) -> p(SX0 - 0.05f + u * (SX1 - SX0 + 0.1f), SY1 + 0.05f - v * (SY1 - SY0 + 0.1f),
                ZF + 0.36f + 0.28f * (float) (Math.sin(Math.PI * u) * Math.sin(Math.PI * v))), 8, 7, GLASS_TV, 1, 1, false, MeshBuilder.Orient.KEEP);
        glass = gb.build();
        MeshBuilder lb = new MeshBuilder();
        lb.push().translate(12.15f, 9.35f, ZF + 0.15f).rotateX(90);
        lb.cylinder(0.14f, 0, 0.1f, 8, WHITE, null, WHITE);
        lb.pop();
        led = lb.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        int ch = Math.floorMod(v.mode, DecorNames.TV.length);
        int[] cyc = new int[1];
        float g = v.on ? glitch(s, PERIOD, cyc) : 0;
        int tick = (int) s.time;
        boolean black = v.on && blackout(s, g, cyc[0]);
        float hum = 0.9f + 0.1f * (float) Math.sin(s.time * 4.3f) * (float) Math.sin(s.time * 0.37f);
        boolean lit = v.on && !black;

        out.solid(p, led, 0);
        if (v.on) out.additive(p, led, 0xFF3020, 0.8f);
        out.sheet(SCREENS);
        slices.set(SY0, SY1, g, tick, cyc[0]);
        switch (ch) {
            case 0 -> {
                // publicite : CASINO dont les lettres changent de couleur, bandeau de bienvenue
                out.solid(p, bg, lit ? 0.6f * hum : 0);
                float t = s.time / 6f;
                for (int k = 0; k < 6; k++) {
                    int col = AD_COLORS[Math.floorMod((int) t + k, AD_COLORS.length)];
                    float x = LX0 + k * LS;
                    neonQuad(p, out, letters[k], letter(k), x, LY0, x + LW, LY0 + LH, Z_TXT, slices, col, lit ? hum : 0, g);
                }
                float blink = ((int) (s.time / 10)) % 2 == 0 ? 1f : 0.55f;
                neonQuad(p, out, welcome, WELCOME, SX0 + 0.5f, 3.75f, SX1 - 0.5f, 4.6f, Z_TXT, slices, 0xFFE8B0, lit ? 0.8f * blink : 0, g);
                if (lit) snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xE0FFE0, 0.06f + 0.45f * g);
            }
            case 1 -> {
                out.solid(p, bg, lit ? 0.55f * hum : 0);
                neonQuad(p, out, jackpot, JACKPOT, SX0 + 0.8f, 6.6f, SX1 - 0.8f, 8.1f, Z_TXT, slices, 0xFFA020, lit ? hum : 0, g);
                MeshBuilder on = scratch(), off = scratch2();
                digits(on, off, LED_ON, LED_OFF, v.on ? v.jackpot : 0, 7, SX1 - 0.55f, 3.7f, 2.2f, 0.95f, 1.0f, 0.3f, Z_TXT, 0);
                out.sheet(null);
                Mesh onM = on.buildDynamic();
                out.solid(p, off.buildDynamic(), 0);
                if (lit) {
                    out.solid(p, onM, 0);
                    out.additive(p, onM, 0xFFA020, hum);
                }
                out.sheet(SCREENS);
                if (lit) snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xE0FFE0, 0.06f + 0.45f * g);
            }
            case 2 -> {
                // neige : bruit plein ecran, fantome de l'image, bande qui roule
                out.solid(p, bg, lit ? 0.3f : 0);
                if (lit) {
                    snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xFFFFFF, 0.75f);
                    p.push().translate((float) Math.sin(s.time * 0.3f) * 0.3f, 0, 0);
                    for (int k = 0; k < 6; k++) out.additive(p, letters[k], 0x9CFF3A, 0.12f);
                    p.pop();
                }
            }
            default -> {
                // mire : figee, qui scintille
                out.solid(p, mire, lit ? 0.85f * hum : 0);
                if (lit) snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xFFFFFF, 0.05f + 0.45f * g);
            }
        }
        if (lit && ch != 2 && !s.item) {
            // barre de balayage qui descend
            float roll = (s.time * 0.2f + s.seed * 30) % (SY1 - SY0 + 3);
            float y = SY1 + 1.5f - roll;
            if (y > SY0 + 0.5f && y < SY1 - 0.5f) {
                MeshBuilder b = scratch();
                b.face(SCAN, NZ, p(SX0, y - 0.5f, Z_FX + 0.01f), p(SX1, y - 0.5f, Z_FX + 0.01f), p(SX1, y + 0.5f, Z_FX + 0.01f), p(SX0, y + 0.5f, Z_FX + 0.01f));
                out.additive(p, b.buildDynamic(), 0x9CD8A0, 0.12f);
            }
        }
        out.sheet(null);
        if (lit && !s.item) {
            p.push().translate(0, 0, ZF + 0.7f);
            out.quad(p, Sprite.HALO, SX0 - 1.5f, SY0 - 1.5f, SX1 + 1.5f, SY1 + 1.5f, 0xB0D0FF, 0.12f * hum);
            p.pop();
        }
        out.translucent(p, glass, 0xFFFFFF, 0.7f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        int ch = Math.floorMod(v.mode, DecorNames.TV.length);
        int[] cyc = new int[1];
        float g = glitch(s, PERIOD, cyc);
        Scene prev = new Scene().set(s.tick - 1, 0);
        prev.seed = s.seed;
        if (Ease.hash((int) s.time * 3 + 1) > 0.998f) fx.particle(FxSink.SPARK, 8.4f, 1.0f, 1.2f, rnd(r, 0.04f), 0.05f, 0.04f, 0xFFE0A0, 0.3f, 8);
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.mode = (int) (t / 50) % DecorNames.TV.length;
        v.jackpot = 125_430;
        return v;
    }

    @Override
    public int demoLength() {
        return 200;
    }
}
