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
 * Le Trou : ampoule nue pendue a son fil torsade, dans une douille de bakelite rafistolee au chatterton. Elle se
 * balance dans les courants d'air, gresille, baisse quand le groupe electrogene tousse.
 */
public final class AmpouleDesign extends DecorDesign {
    static final float HX = 8, HY = 15.8f, HZ = 8, BULB_Y = 5.4f;

    private Mesh rose = Mesh.EMPTY, wire = Mesh.EMPTY, bulbOn = Mesh.EMPTY, bulbOff = Mesh.EMPTY;

    public AmpouleDesign() {
        super("ampoule_fil", 0xFFD890);
        itemScale = 1.0f;
        itemCenter = new float[]{8, 10, 8};
    }

    @Override
    protected void build() {
        MeshBuilder r = new MeshBuilder();
        r.push().translate(HX, HY, HZ);
        r.cylinder(0.9f, 0, 0.2f, 10, DARK, DARK, DARK);
        r.cylinder(0.3f, -0.3f, 0, 6, DARK, DARK, null);
        r.pop();
        rose = r.build();

        MeshBuilder b = new MeshBuilder();
        // fil torsade (deux brins), un noeud de chatterton a mi-hauteur
        float top = HY - 0.3f, bottom = BULB_Y + 2.5f;
        for (int s = 0; s < 2; s++) {
            int n = 28;
            float[][] path = new float[n + 1][];
            float[] radii = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float t = (float) i / n;
                double a = t * Math.PI * 11 + s * Math.PI;
                path[i] = p(HX + (float) Math.cos(a) * 0.07f, top - t * (top - bottom), HZ + (float) Math.sin(a) * 0.07f);
                radii[i] = 0.07f;
            }
            b.tube(path, radii, 5, CABLE, true, true, 0, false);
        }
        b.push().translate(HX, (top + bottom) / 2, HZ);
        b.cylinder(0.2f, -0.45f, 0.45f, 8, RUBBER, RUBBER, RUBBER);
        b.pop();
        // douille de bakelite felee, bague de laiton
        b.push().translate(HX, BULB_Y + 1.15f, HZ);
        b.cylinder(0.52f, 0, 1.35f, 12, DARK, DARK, DARK);
        b.push().translate(0, 1.35f, 0);
        b.sphereSection(0.52f, 12, 3, 0, 90, DARK);
        b.pop();
        b.push().translate(0, 0.1f, 0);
        b.torus(0.53f, 0.06f, 12, 4, 0, 360, BRASS);
        b.pop();
        b.pop();
        Occlusion.bake(b, 0.2f, 1.4f, 0.4f, Float.NaN);
        wire = b.build();

        MeshBuilder on = new MeshBuilder(), off = new MeshBuilder();
        for (MeshBuilder m : new MeshBuilder[]{on, off}) {
            m.push().translate(HX, BULB_Y, HZ);
            m.sphere(1.05f, 14, 10, m == on ? BULB_ON : BULB_OFF);
            m.push().translate(0, 0.6f, 0);
            m.cylinder(0.5f, 0, 0.6f, 10, m == on ? BULB_ON : BULB_OFF, null, null);
            m.pop();
            m.pop();
        }
        bulbOn = on.build();
        bulbOff = off.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, rose, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float t = s.time + s.seed * 700;
        // un fil plus long que la baladeuse : il se balance davantage
        float swayX = s.item ? 0 : 2.4f * (float) Math.sin(t * 0.05f + s.seed * 5);
        float swayZ = s.item ? 0 : 1.8f * (float) Math.sin(t * 0.037f + s.seed * 11);
        p.push().translate(HX, HY, HZ).rotX(swayX).rotZ(swayZ).translate(-HX, -HY, -HZ);
        out.solid(p, wire, 0);
        float lit = v.on ? level(t, s.item) : 0;
        out.solid(p, lit > 0.05f ? bulbOn : bulbOff, lit > 0.05f ? lit : 0);
        if (lit > 0.05f && !s.item) {
            out.billboard(p, Sprite.HALO, HX, BULB_Y, HZ, 4.6f * lit, 0, 0xFFC878, 0.6f * lit);
            out.billboard(p, Sprite.HALO, HX, BULB_Y, HZ, 1.8f * lit, 0, 0xFFF4D8, 0.8f * lit);
        }
        p.pop();
    }

    /** Eclat : gresillement leger, baisses de tension, et un faux contact de temps en temps (propre a chaque ampoule). */
    private static float level(float t, boolean item) {
        if (item) return 1f;
        int tk = (int) t;
        float c = t % 530;
        if (c > 512) return Ease.hash(tk * 5 + 3) > 0.45f ? 1f : 0.05f;
        float dip = Ease.hash((tk / 3) * 17 + 2) > 0.95f ? 0.5f : 1f;
        return dip * (0.9f + 0.1f * (float) Math.sin(t * 6.1f));
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        float t = s.time + s.seed * 700;
        if (Math.floor(t % 530) == 513) {
            fx.particle(FxSink.SPARK, HX, BULB_Y + 1.6f, HZ, rnd(r, 0.04f), -0.02f, rnd(r, 0.04f), 0xFFE0A0, 0.25f, 8);
        }
        if (r.nextInt(120) == 0) {
            fx.particle(FxSink.FLY, HX + rnd(r, 2), BULB_Y + rnd(r, 1.2f), HZ + rnd(r, 2), rnd(r, 0.08f), rnd(r, 0.04f), rnd(r, 0.08f), 0xC8B8A0, 0.35f, 60);
        }
    }
}
