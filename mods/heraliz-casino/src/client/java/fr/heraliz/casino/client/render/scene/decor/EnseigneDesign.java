package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.client.render.mesh.Mat;
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
 * Enseigne de devanture : une dalle d'ecran recuperee, mince, aux bords arrondis, vissee de travers sur deux
 * equerres rouillees. "CASINO" en lettres de neon qui gresillent : une lettre a un faux contact, une autre est
 * fatiguee ; toutes les six ou sept secondes l'image se dechire (tranches decalees, couleurs qui se separent, neige,
 * coupure). Scotch sur un coin fele, autocollant, cable qui pend jusqu'a une boite de derivation.
 * Variantes : couleur du neon.
 */
public final class EnseigneDesign extends DecorDesign {
    /** Ecran (px) et profondeurs des couches. */
    static final float SX0 = 2.6f, SX1 = 29.4f, SY0 = 3.9f, SY1 = 12.1f;
    static final float ZF = 2.3f, Z_BG = 2.36f, Z_TXT = 2.41f, Z_FX = 2.45f, Z_GLASS = 2.52f;
    static final float CX = 16, CY = 8, TILT = -1.3f;
    /** Lettres : hauteur, largeur de case, pas. */
    static final float LH = 6.3f, LW = LH * LETTER_W / LETTER_H, STRIDE = 4.0f, LY0 = 4.85f;
    static final float LX0 = CX - (5 * STRIDE + LW) / 2;
    static final float PERIOD = 140;

    private Mesh body = Mesh.EMPTY, bg = Mesh.EMPTY, glass = Mesh.EMPTY, scan = Mesh.EMPTY;
    private final Mesh[] letters = new Mesh[6];
    private final Slices slices = new Slices();

    public EnseigneDesign() {
        super("enseigne_casino", 0xFF3A8A);
        itemScale = 0.62f;
        itemCenter = new float[]{16, 8, 1.6f};
    }

    // =================================================================== construction
    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // ---- equerres rouillees : platine au mur + entretoise jusqu'au dos du boitier
        for (float x : new float[]{6.5f, 25.5f}) {
            b.bevelBox(x - 1.3f, 5.2f, 0, x + 1.3f, 10.8f, 0.2f, 0.05f, RUST);
            b.bevelBox(x - 0.7f, 6.4f, 0.2f, x + 0.7f, 9.6f, 0.75f, 0.08f, RUST);
            screw(b, x, 5.75f, 0.2f, 0.22f, STEEL);
            screw(b, x, 10.25f, 0.2f, 0.22f, STEEL);
        }
        // ---- boitier mince aux bords arrondis
        b.push().translate(CX, CY, 1.5f);
        b.roundedSlab(29.6f, 10.4f, 1.6f, 1.25f, 0.32f, 4, CASE, CASE);
        b.pop();
        // levre sombre autour de l'ecran
        float lw = 0.32f, z0 = ZF - 0.03f, z1 = ZF + 0.16f;
        b.bevelBox(SX0 - lw, SY1, z0, SX1 + lw, SY1 + lw, z1, 0.06f, DARK);
        b.bevelBox(SX0 - lw, SY0 - lw, z0, SX1 + lw, SY0, z1, 0.06f, DARK);
        b.bevelBox(SX0 - lw, SY0, z0, SX0, SY1, z1, 0.06f, DARK);
        b.bevelBox(SX1, SY0, z0, SX1 + lw, SY1, z1, 0.06f, DARK);
        // vis des coins (une manque : il ne reste que le trou)
        screw(b, 1.95f, 3.35f, ZF, 0.26f, STEEL);
        screw(b, 30.05f, 3.35f, ZF, 0.26f, STEEL);
        screw(b, 1.95f, 12.65f, ZF, 0.26f, STEEL);
        b.push().translate(30.05f, 12.65f, ZF + 0.01f).rotateX(90);
        b.cylinder(0.2f, 0, 0.01f, 8, DARK, null, DARK);
        b.pop();
        // autocollant sur le bandeau du bas
        b.push().translate(8.2f, 3.36f, ZF + 0.02f).rotateZ(1.5f);
        b.decalZ(STICKER, 0, 0, 0, 7.2f, 0.9f);
        b.pop();
        // coin fele tenu par deux bandes de scotch croisees (devant la vitre)
        tape(b, TAPE, 3.9f, 11.0f, Z_GLASS + 0.06f, 4.6f, 1.05f, 38);
        tape(b, TAPE, 4.1f, 11.2f, Z_GLASS + 0.09f, 3.8f, 0.95f, -44);
        // ---- cable qui sort par le dessous et file dans une boite de derivation
        cable(b, new float[][]{p(27.6f, 2.95f, 1.35f), p(27.9f, 1.8f, 1.6f), p(28.7f, 0.55f, 1.5f), p(29.7f, 0.1f, 1.05f),
                p(30.5f, 0.35f, 0.75f)}, 0.21f, CABLE);
        // manchon de chatterton sur une epissure
        b.push().translate(28.25f, 1.1f, 1.58f).rotateZ(-38).rotateX(8);
        b.cylinder(0.3f, -0.45f, 0.45f, 8, TAPE, TAPE, TAPE);
        b.pop();
        b.bevelBox(30.2f, -0.9f, 0, 32.4f, 1.4f, 0.75f, 0.14f, STEEL);
        screw(b, 31.3f, 0.25f, 0.75f, 0.2f, RUST);
        Occlusion.bake(b, 0.3f, 2.2f, 0.45f, Float.NaN);
        body = b.build();

        bg = plate(SIGN_BG, SX0, SY0, SX1, SY1, Z_BG);
        glass = plate(GLASS, SX0 - 0.05f, SY0 - 0.05f, SX1 + 0.05f, SY1 + 0.05f, Z_GLASS);
        scan = plate(SCAN, SX0, -0.9f, SX1, 0.9f, 0);
        for (int k = 0; k < 6; k++) {
            float x = LX0 + k * STRIDE;
            letters[k] = plate(letter(k), x, LY0, x + LW, LY0 + LH, Z_TXT);
        }
    }

    // =================================================================== rendu
    @Override
    protected void body(Pose p, DrawList out) {
        p.push().translate(CX, CY, 0).rotZ(TILT).translate(-CX, -CY, 0);
        out.solid(p, body, 0);
        p.pop();
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        p.push().translate(CX, CY, 0).rotZ(TILT).translate(-CX, -CY, 0);
        int color = DecorNames.SIGN_COLORS[Math.floorMod(v.mode, DecorNames.SIGN_COLORS.length)];
        int[] cyc = new int[1];
        float g = v.on ? glitch(s, PERIOD, cyc) : 0;
        int tick = (int) s.time;
        boolean black = v.on && blackout(s, g, cyc[0]);
        float hum = 0.93f + 0.07f * (float) Math.sin(s.time * 2.3f) * (float) Math.sin(s.time * 0.61f + s.seed * 9);

        out.sheet(SCREENS);
        out.solid(p, bg, !v.on || black ? 0 : (0.55f - 0.3f * g) * hum);
        if (v.on && !black) {
            // la lumiere des tubes teinte le fond de l'ecran
            p.push().translate(0, 0, Z_FX);
            out.quad(p, Sprite.HALO, SX0 + 1, SY0 - 1.5f, SX1 - 1, SY1 + 1.5f, color, 0.16f * hum * (1 - 0.5f * g));
            p.pop();
        }
        // ---- lettres : le tube (verre) et sa lumiere
        slices.set(SY0, SY1, g, tick, cyc[0]);
        boolean skull = v.on && g > 0.45f && Ease.hash(cyc[0] * 5 + 1) > 0.72f && Ease.hash(tick * 3 + 2) > 0.5f;
        for (int k = 0; k < 6; k++) {
            float x = LX0 + k * STRIDE;
            float lit = !v.on || black || skull ? 0 : letterLevel(s, k) * hum;
            neonQuad(p, out, letters[k], letter(k), x, LY0, x + LW, LY0 + LH, Z_TXT, slices, color, lit, g);
        }
        // ---- pendant un gros glitch : tete de mort rouge a la place du texte, un instant
        if (skull) {
            MeshBuilder b = scratch();
            b.face(SKULL, NZ, p(CX - 3.4f, SY0 + 0.6f, Z_TXT), p(CX + 3.4f, SY0 + 0.6f, Z_TXT), p(CX + 3.4f, SY1 - 0.6f, Z_TXT),
                    p(CX - 3.4f, SY1 - 0.6f, Z_TXT));
            out.additive(p, b.buildDynamic(), 0xFF2A1A, 0.9f);
        }
        if (v.on) {
            // neige : un voile permanent (vieux tube), forte pendant le glitch ; bande noire qui traverse
            snow(p, out, NOISE, SX0, SY0, SX1, SY1, Z_FX, tick, 0xD8D0FF, black ? 0 : 0.05f + 0.5f * g);
            if (g > 0.1f) {
                float by = SY0 + Ease.hash(tick * 3 + cyc[0]) * (SY1 - SY0 - 1);
                float bh = 0.5f + Ease.hash(tick * 5 + 9) * 1.6f;
                MeshBuilder b = scratch();
                b.face(BLACK, NZ, p(SX0, by, Z_FX + 0.01f), p(SX1, by, Z_FX + 0.01f), p(SX1, Math.min(SY1, by + bh), Z_FX + 0.01f),
                        p(SX0, Math.min(SY1, by + bh), Z_FX + 0.01f));
                out.solid(p, b.buildDynamic(), 0);
            }
            // barre de balayage qui descend lentement
            float roll = (s.time * 0.28f + s.seed * 40) % (SY1 - SY0 + 4);
            float y = SY1 + 2 - roll;
            if (y > SY0 + 0.9f && y < SY1 - 0.9f && !black) {
                p.push().translate(0, y, Z_FX + 0.02f);
                out.additive(p, scan, color, 0.12f);
                p.pop();
            }
        }
        out.sheet(null);
        out.translucent(p, glass, 0xFFFFFF, 0.62f);
        p.pop();
    }

    /**
     * Eclat d'une lettre (0..1) : une lettre a un faux contact (coupures breves, crise de clignotement toutes les
     * neuf secondes), une autre est fatiguee (plus terne, respire). Lesquelles : propre a chaque enseigne.
     */
    private static float letterLevel(Scene s, int k) {
        int dying = (int) (s.seed * 6) % 6;
        int tired = (dying + 3) % 6;
        float t = s.time + s.seed * 500;
        int tk = (int) t;
        if (k == dying) {
            float c = t % 180;
            if (c > 152) return Ease.hash(tk * 5 + k) > 0.42f ? 1f : 0.04f;
            return Ease.hash((tk / 3) * 7 + k * 13) > 0.94f ? 0.06f : 1f;
        }
        if (k == tired) return 0.68f + 0.08f * (float) Math.sin(t * 0.9f);
        return 1f;
    }

    // =================================================================== sons et particules
    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        int[] cyc = new int[1];
        float g = glitch(s, PERIOD, cyc);
        Scene prev = new Scene().set(s.tick - 1, 0);
        prev.seed = s.seed;
        if (g > 0.3f && glitch(prev, PERIOD, null) <= 0.3f) {
            for (int i = 0; i < 3; i++) {
                fx.particle(FxSink.SPARK, 30.4f, 1.4f, 0.9f, rnd(r, 0.05f), 0.06f, 0.05f, 0xFFE0A0, 0.35f, 9);
            }
        }
        // le faux contact claque de temps en temps
        if (r.nextInt(420) == 0) {
            fx.particle(FxSink.SPARK, 28.3f, 1.1f, 1.7f, rnd(r, 0.04f), 0.05f, 0.04f, 0xFFE0A0, 0.3f, 8);
        }
    }

    // =================================================================== apercu
    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        v.mode = (int) (t / 60) % DecorNames.SIGN_COLORS.length;
        return v;
    }

    @Override
    public int demoLength() {
        return 300;
    }
}
