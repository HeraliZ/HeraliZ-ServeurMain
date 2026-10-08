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
 * Baladeuse d'atelier pendue au plafond par son fil torsade : abat-jour emaille vert ecaille, ampoule dans une
 * cage de fil de fer. Elle se balance a peine, faiblit quand le groupe electrogene tousse et attire les papillons
 * de nuit. Un cone de lumiere descend sous l'abat-jour.
 */
public final class LampeDesign extends DecorDesign {
    static final float HX = 8, HY = 15.8f, HZ = 8, BULB_Y = 9.0f;

    private Mesh rose = Mesh.EMPTY, lamp = Mesh.EMPTY, bulbOn = Mesh.EMPTY, bulbOff = Mesh.EMPTY, cone = Mesh.EMPTY;

    public LampeDesign() {
        super("lampe_grillagee", 0xFFE3A0);
        itemScale = 1.0f;
        itemCenter = new float[]{8, 11, 8};
    }

    @Override
    protected void build() {
        MeshBuilder r = new MeshBuilder();
        r.push().translate(HX, HY, HZ);
        r.cylinder(1.0f, 0, 0.2f, 12, ENAMEL_IN, ENAMEL_IN, ENAMEL_IN);
        r.cylinder(0.35f, -0.25f, 0, 8, DARK, DARK, null);
        r.pop();
        rose = r.build();

        MeshBuilder b = new MeshBuilder();
        // ---- fil torsade (deux brins enroules)
        for (int s = 0; s < 2; s++) {
            int n = 24;
            float[][] path = new float[n + 1][];
            float[] radii = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float t = (float) i / n;
                double a = t * Math.PI * 9 + s * Math.PI;
                path[i] = p(HX + (float) Math.cos(a) * 0.07f, HY - 0.25f - t * 4.4f, HZ + (float) Math.sin(a) * 0.07f);
                radii[i] = 0.075f;
            }
            b.tube(path, radii, 5, CABLE, true, true, 0, false);
        }
        // ---- douille laiton
        b.push().translate(HX, 10.2f, HZ);
        b.cylinder(0.45f, 0, 1.2f, 10, BRASS, BRASS, BRASS);
        b.push().translate(0, 0.35f, 0);
        b.torus(0.47f, 0.06f, 10, 4, 0, 360, BRASS);
        b.pop();
        // ---- abat-jour emaille (vert dehors, blanc dedans)
        float[][] out = {{0.5f, 1.15f}, {1.25f, 0.9f}, {2.3f, 0.1f}, {3.1f, -0.9f}, {3.4f, -1.45f}, {3.45f, -1.6f}};
        float[][] in = {{0.48f, 1.05f}, {1.2f, 0.8f}, {2.24f, 0.02f}, {3.02f, -0.95f}, {3.34f, -1.5f}};
        b.lathe(out, 18, 0, 360, ENAMEL, 1, 1, 0, 0, 0, false);
        b.lathe(in, 18, 0, 360, ENAMEL_IN, 1, 1, 0, 0, 0, true);
        b.push().translate(0, -1.6f, 0);
        b.torus(3.43f, 0.07f, 18, 4, 0, 360, ENAMEL);
        b.pop();
        // ---- cage de fil de fer autour de l'ampoule
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6 + 0.3;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            b.tube(new float[][]{p(cx * 0.48f, 0.05f, cz * 0.48f), p(cx * 1.25f, -0.7f, cz * 1.25f), p(cx * 1.3f, -2.0f, cz * 1.3f), p(cx * 0.35f, -2.75f, cz * 0.35f)},
                    new float[]{0.05f, 0.05f, 0.05f, 0.05f}, 4, RUST, false, false, 0, false);
        }
        b.push().translate(0, -1.25f, 0);
        b.torus(1.3f, 0.055f, 14, 4, 0, 360, RUST);
        b.pop();
        b.push().translate(0, -2.75f, 0);
        b.sphere(0.12f, 6, 4, RUST);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.2f, 1.4f, 0.4f, Float.NaN);
        lamp = b.build();

        MeshBuilder on = new MeshBuilder(), off = new MeshBuilder();
        for (MeshBuilder m : new MeshBuilder[]{on, off}) {
            m.push().translate(HX, BULB_Y, HZ);
            m.sphere(0.9f, 12, 8, m == on ? BULB_ON : BULB_OFF);
            m.push().translate(0, 0.7f, 0);
            m.cylinder(0.45f, 0, 0.5f, 10, m == on ? BULB_ON : BULB_OFF, null, null);
            m.pop();
            m.pop();
        }
        bulbOn = on.build();
        bulbOff = off.build();

        // ---- cone de lumiere sous l'abat-jour (degrade : fort pres de l'ampoule, nul au sol)
        MeshBuilder c = new MeshBuilder();
        c.push().translate(HX, 0, HZ);
        int from = c.mark();
        c.lathe(new float[][]{{3.1f, 8.5f}, {4.6f, 5.5f}, {6.1f, 2.5f}, {7.2f, 0.2f}}, 20, 0, 360, WHITE, 1, 1, 0, 0, 0, false);
        c.shade(from, (x, y, z, nx, ny, nz, rgb) -> {
            float k = Math.max(0, Math.min(1, y / 8.5f));
            k = k * k;
            rgb[0] *= k;
            rgb[1] *= k;
            rgb[2] *= k;
        });
        c.pop();
        cone = c.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, rose, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float t = s.time + s.seed * 500;
        float swayX = s.item ? 0 : 1.3f * (float) Math.sin(t * 0.045f + s.seed * 4);
        float swayZ = s.item ? 0 : 1.0f * (float) Math.sin(t * 0.031f + s.seed * 9);
        p.push().translate(HX, HY, HZ).rotX(swayX).rotZ(swayZ).translate(-HX, -HY, -HZ);
        out.solid(p, lamp, 0);
        float lit = v.on ? level(t, s.item) : 0;
        out.solid(p, lit > 0.05f ? bulbOn : bulbOff, lit > 0.05f ? lit : 0);
        if (lit > 0.05f && !s.item) {
            out.billboard(p, Sprite.HALO, HX, BULB_Y, HZ, 3.2f * lit, 0, 0xFFD890, 0.55f * lit);
            out.additive(p, cone, 0xFFE0A0, 0.16f * lit);
        }
        p.pop();
    }

    /** Eclat de l'ampoule : baisses de tension du groupe electrogene, et une vraie coupure de temps en temps. */
    private static float level(float t, boolean item) {
        if (item) return 1f;
        int tk = (int) t;
        float c = t % 420;
        if (c > 400) return Ease.hash(tk * 3 + 5) > 0.5f ? 1f : 0.08f;
        float dip = Ease.hash((tk / 4) * 13 + 1) > 0.93f ? 0.55f : 1f;
        return dip * (0.94f + 0.06f * (float) Math.sin(t * 5.3f));
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        float t = s.time + s.seed * 500;
        // papillons de nuit attires par l'ampoule
        if (r.nextInt(90) == 0) {
            fx.particle(FxSink.FLY, HX + rnd(r, 2.5f), BULB_Y + rnd(r, 1.5f), HZ + rnd(r, 2.5f), rnd(r, 0.08f), rnd(r, 0.04f), rnd(r, 0.08f), 0xC8B8A0, 0.4f, 60);
        }
    }
}
