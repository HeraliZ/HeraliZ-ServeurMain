package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : vieille reglette a deux tubes fluorescents pendue au plafond par des chainettes (deux blocs de long). Le
 * diffuseur a moitie arrache pend d'un cote, des papillons de nuit tournent autour. Elle gresille pour de bon : le
 * serveur fait sauter sa lumiere (pleine, faible, coupee) et toute la salle clignote ; le modele y ajoute les
 * claquements du starter, les cathodes qui rougeoient quand le tube ne s'allume pas, un tube plus fatigue que l'autre.
 */
public final class NeonPlafondDesign extends DecorDesign {
    static final float TY = 11.3f, TZA = 6.9f, TZB = 9.1f, TX0 = 2.6f, TX1 = 29.4f, TR = 0.72f;

    private Mesh fixture = Mesh.EMPTY, diffuser = Mesh.EMPTY, tubeA = Mesh.EMPTY, tubeB = Mesh.EMPTY, ends = Mesh.EMPTY, cone = Mesh.EMPTY;

    public NeonPlafondDesign() {
        super("neon_plafond", 0xD8F4FF);
        itemScale = 0.5f;
        itemCenter = new float[]{16, 12, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // boitier en tole : dessus acier, dessous reflecteur emaille (sale), bosse du ballast
        b.box6(1, 12.3f, 5.2f, 31, 13.3f, 10.8f, STEEL, STEEL, ENAMEL_IN, STEEL, STEEL, STEEL);
        b.bevelBox(10, 13.3f, 6.5f, 22, 14.2f, 9.5f, 0.2f, STEEL);
        // douilles des tubes
        for (float x : new float[]{1.2f, 29.6f}) b.bevelBox(x, 10.2f, 5.7f, x + 1.2f, 12.3f, 10.3f, 0.15f, DARK);
        // chainettes jusqu'au plafond, crochets
        for (float x : new float[]{6, 26}) {
            b.chain(new float[][]{p(x, 14.0f, 8), p(x, 16.1f, 8)}, 0.7f, 0.42f, 0.11f, STEEL, 8, 4);
            b.push().translate(x, 15.85f, 8);
            b.cylinder(0.45f, 0, 0.2f, 8, STEEL, STEEL, STEEL);
            b.pop();
        }
        // cable d'alimentation scotche
        b.tube(new float[][]{p(21.5f, 14.0f, 8), p(22.4f, 14.9f, 8.3f), p(23.6f, 15.9f, 8.6f)}, new float[]{0.14f, 0.14f, 0.14f}, 5, CABLE, true, true, 0, false);
        b.push().translate(22.4f, 14.9f, 8.3f);
        b.cylinder(0.24f, -0.4f, 0.4f, 6, RUBBER, RUBBER, RUBBER);
        b.pop();
        Occlusion.bake(b, 0.3f, 2.0f, 0.45f, Float.NaN);
        fixture = b.build();
        // diffuseur arrache : pend d'un cote, en biais (translucide)
        MeshBuilder d = new MeshBuilder();
        d.push().translate(2.5f, 12.2f, 5.0f).rotateX(62).rotateZ(-8);
        d.face(GLASS_S, v(0, -1, 0), p(0, 0, 0), p(12.5f, 0, 0), p(12.5f, 0, 5.6f), p(0, 0, 5.6f));
        d.face(GLASS_S, v(0, 1, 0), p(0, 0.02f, 0), p(0, 0.02f, 5.6f), p(12.5f, 0.02f, 5.6f), p(12.5f, 0.02f, 0));
        d.pop();
        diffuser = d.build();
        // les deux tubes (feuille des salles : verre laiteux + calque lumineux)
        tubeA = tube(TZA);
        tubeB = tube(TZB);
        MeshBuilder e = new MeshBuilder();
        for (float z : new float[]{TZA, TZB}) {
            for (float x : new float[]{TX0, TX1 - 1.1f}) {
                e.push().translate(x, TY, z).rotateZ(-90);
                e.cylinder(TR + 0.04f, 0, 1.1f, 10, CATHODE, null, null);
                e.pop();
            }
        }
        ends = e.build();
        // volume de lumiere sous la reglette (additif, s'efface vers le sol)
        MeshBuilder c = new MeshBuilder();
        int from = c.mark();
        float yt = TY - 0.6f, yb = -14f;
        float[][] top = {p(2, yt, 5.6f), p(30, yt, 5.6f), p(30, yt, 10.4f), p(2, yt, 10.4f)};
        float[][] bot = {p(-5, yb, -2), p(37, yb, -2), p(37, yb, 18), p(-5, yb, 18)};
        for (int k = 0; k < 4; k++) {
            int q = (k + 1) % 4;
            c.flatOut(WHITE, p(16, (yt + yb) / 2, 8), top[k], top[q], bot[q], bot[k]);
        }
        c.shade(from, (x, y, z, nx, ny, nz, rgb) -> {
            float k = Ease.clamp01((y - yb) / (yt - yb));
            k = k * k;
            rgb[0] *= k;
            rgb[1] *= k;
            rgb[2] *= k;
        });
        cone = c.build();
    }

    private static Mesh tube(float z) {
        MeshBuilder t = new MeshBuilder();
        t.push().translate(TX0, TY, z).rotateZ(-90);
        t.cylinder(TR, 0, TX1 - TX0, 12, FLUO_TUBE, null, null);
        t.pop();
        return t.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, fixture, 0);
        out.translucent(p, diffuser, 0xFFFFFF, 0.55f);
    }

    /** Eclat de chaque tube (0..1) : etat du bloc (pleine, faible, coupee), claquements apres chaque saute, tube B fatigue. */
    static float[] levels(Scene s, DecorView v) {
        if (s.item) return new float[]{1, 1};
        if (!v.on) return new float[]{0, 0};
        float t = s.time + s.seed * 997;
        int seed = (int) (s.seed * 9973);
        float base = v.flicker == 0 ? 1f : v.flicker == 1 ? 0.38f : 0f;
        // juste apres une saute : le starter claque, le tube hesite
        if (v.flickerSince < 6) base *= Ease.hash((int) (t * 2.7f) + seed) > 0.45f ? 1f : 0.12f;
        // lumiere faible : stroboscope irregulier
        if (v.flicker == 1) base *= Ease.hash((int) (t * 1.9f) + seed * 3) > 0.35f ? 1.4f : 0.2f;
        base *= 0.93f + 0.07f * (float) Math.sin(t * 7.3f);
        float a = Math.min(1f, base);
        // tube B : plus use, il decroche tout seul de temps en temps
        float drop = Ease.hash((int) (t * 0.6f) + seed * 7 + 77) > 0.9f ? (Ease.hash((int) (t * 3.1f) + seed) > 0.5f ? 0.08f : 0.6f) : 1f;
        return new float[]{a, Math.min(1f, base * drop)};
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float[] l = levels(s, v);
        sheetSolid(p, out, tubeA, l[0] > 0.04f ? l[0] : 0);
        sheetSolid(p, out, tubeB, l[1] > 0.04f ? l[1] : 0);
        // cathodes : rougeoient quand le tube peine a s'allumer
        float hot = v.on && !s.item ? (v.flicker == 0 ? 0.25f : 0.9f) : 0;
        sheetSolid(p, out, ends, hot);
        if (s.item) return;
        float avg = (l[0] + l[1]) / 2;
        if (avg > 0.03f) {
            for (int k = 0; k < 4; k++) {
                float x = TX0 + 3 + k * (TX1 - TX0 - 6) / 3f;
                out.billboard(p, Sprite.HALO, x, TY, TZA, 3.4f * l[0], 0, 0xCFEFFF, 0.32f * l[0]);
                out.billboard(p, Sprite.HALO, x, TY, TZB, 3.4f * l[1], 0, 0xCFEFFF, 0.32f * l[1]);
            }
            out.additive(p, cone, 0xC8E8FF, 0.09f * avg);
        }
        if (hot > 0.5f) {
            for (float z : new float[]{TZA, TZB}) {
                out.billboard(p, Sprite.HALO, TX0 + 0.5f, TY, z, 1.2f, 0, 0xFF7A2A, 0.5f);
                out.billboard(p, Sprite.HALO, TX1 - 0.5f, TY, z, 1.2f, 0, 0xFF7A2A, 0.5f);
            }
        }
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        if (v.flickerSince < 1) {
            // claquement du starter a chaque saute ; une gerbe d'etincelles quand le tube lache
            if (v.flicker == 2 && r.nextInt(3) == 0) {
                float x = r.nextBoolean() ? TX0 + 0.5f : TX1 - 0.5f;
                for (int k = 0; k < 4; k++) {
                    fx.particle(FxSink.SPARK, x, TY - 0.6f, r.nextBoolean() ? TZA : TZB, rnd(r, 0.08f), -0.05f - r.nextFloat() * 0.08f, rnd(r, 0.08f),
                            0xFFE0A0, 0.22f, 12 + r.nextInt(10));
                }
            }
        }
        if (v.flicker != 2 && r.nextInt(70) == 0) {
            fx.particle(FxSink.FLY, 6 + r.nextFloat() * 20, TY - 1.5f + rnd(r, 1.5f), 8 + rnd(r, 3), rnd(r, 0.1f), rnd(r, 0.04f), rnd(r, 0.1f), 0xC8B8A0, 0.35f, 60);
        }
    }

    @Override
    public DecorView demoView(float t) {
        DecorView v = newView();
        int phase = (int) (t / 25) % 4;
        v.flicker = phase == 1 ? 1 : phase == 3 ? 2 : 0;
        v.flickerSince = t % 25;
        return v;
    }
}
