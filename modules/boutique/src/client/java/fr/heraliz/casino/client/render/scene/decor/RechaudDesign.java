package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : rechaud de camping sur sa cartouche de gaz bleue, une gamelle cabossee qui fume dessus, une boite de
 * haricots a la tomate ouverte et sa fourchette. Allume : flamme bleue sous la gamelle, vapeur.
 */
public final class RechaudDesign extends DecorDesign {
    static final float SX = 6.6f, SZ = 7.6f, BURNER_Y = 5.1f;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY, ring = Mesh.EMPTY;

    public RechaudDesign() {
        super("rechaud", 0x5AA0FF);
        itemScale = 1.0f;
        itemCenter = new float[]{8, 4, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // cartouche de gaz (bleue, dome), bague de laiton, robinet
        s.push().translate(SX, 0, SZ);
        s.cylinder(2.5f, 0, 3.2f, 18, GAS, GAS, null);
        s.pop();
        b.push().translate(SX, 3.2f, SZ);
        b.lathe(new float[][]{{2.5f, 0}, {2.2f, 0.5f}, {1.2f, 0.9f}, {0.7f, 1.05f}}, 18, 0, 360, STEEL, 1, 1, 0, 0, 0, false);
        b.cylinder(0.6f, 0.9f, 1.6f, 10, BRASS, null, BRASS);
        b.push().translate(0.3f, 1.3f, 0).rotateZ(-90);
        b.cylinder(0.2f, 0, 1.9f, 6, BRASS, null, BRASS);
        b.pop();
        b.push().translate(2.2f, 1.3f, 0);
        b.bevelBox(0, -0.5f, -0.3f, 1.1f, 0.5f, 0.3f, 0.1f, DARK);
        b.pop();
        // tete du bruleur et trois supports de casserole
        b.push().translate(0, 1.6f, 0);
        b.cylinder(1.1f, 0, 0.3f, 14, STEEL, STEEL, SOOT);
        b.pop();
        for (int k = 0; k < 3; k++) {
            double a = Math.PI * 2 * k / 3 + 0.4;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            b.tube(new float[][]{p(c * 1.0f, 1.7f, sn * 1.0f), p(c * 2.4f, 2.0f, sn * 2.4f), p(c * 2.5f, 2.6f, sn * 2.5f)}, new float[]{0.14f, 0.14f, 0.14f}, 4,
                    STEEL, true, true, 0, false);
        }
        b.pop();
        // gamelle cabossee (suie dessous), anse, couvercle de travers
        float gy = 5.85f;
        b.push().translate(SX, gy, SZ).rotateZ(2);
        b.lathe(new float[][]{{2.4f, 0}, {2.75f, 0.35f}, {2.85f, 2.2f}, {2.9f, 2.4f}}, 18, 0, 360, STEEL, 1, 1, 0.06f, 0.6f, 5, false);
        b.disc(2.4f, 0, 18, SOOT, false);
        b.push().translate(0, 2.2f, 0);
        b.disc(2.75f, 0, 18, SOOT, true);
        b.pop();
        b.push().translate(-0.3f, 2.55f, 0.4f).rotateX(-14).rotateZ(4);
        b.cylinder(2.95f, 0, 0.18f, 18, STEEL, STEEL, STEEL);
        b.push().translate(0, 0.18f, 0);
        b.cylinder(0.35f, 0, 0.35f, 8, DARK, null, DARK);
        b.pop();
        b.pop();
        b.push().translate(2.9f, 1.7f, 0).rotateZ(-90);
        b.cylinder(0.18f, 0, 3.2f, 6, STEEL, null, STEEL);
        b.pop();
        b.pop();
        // boite de haricots ouverte, couvercle releve, fourchette
        s.push().translate(12.3f, 0, 10.6f);
        s.cylinder(1.45f, 0.25f, 3.3f, 16, CAN, null, null);
        s.pop();
        b.push().translate(12.3f, 0, 10.6f);
        b.cylinder(1.5f, 0, 0.25f, 16, STEEL, STEEL, STEEL);
        b.push().translate(0, 3.3f, 0);
        b.cylinder(1.5f, 0, 0.2f, 16, STEEL, null, null);
        b.disc(1.3f, 0.05f, 16, PAINT_RED, true);
        b.pop();
        b.push().translate(-1.4f, 3.45f, 0).rotateZ(68);
        b.cylinder(1.45f, 0, 0.08f, 16, STEEL, STEEL, STEEL);
        b.pop();
        b.push().translate(0.4f, 3.0f, -0.3f).rotateX(-14).rotateZ(-10);
        b.bevelBox(-0.12f, 0, -0.05f, 0.12f, 4.4f, 0.05f, 0.02f, STEEL);
        b.bevelBox(-0.35f, 4.4f, -0.05f, 0.35f, 5.4f, 0.05f, 0.02f, STEEL);
        b.pop();
        b.pop();
        Mesh[] m = bakePair(b, s, 0.25f, 1.8f, 0.45f, 0f);
        body = m[0];
        bodyS = m[1];
        // couronne de petites flammes bleues (calque lumineux)
        MeshBuilder f = new MeshBuilder();
        f.push().translate(SX, BURNER_Y, SZ);
        f.torus(1.05f, 0.2f, 16, 4, 0, 360, FLAME_BLUE);
        f.pop();
        ring = f.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (!v.on || s.item) return;
        float t = s.time + s.seed * 300;
        float f = 0.85f + 0.15f * (float) Math.sin(t * 1.7f);
        sheetSolid(p, out, ring, f);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8 + t * 0.05f;
            float x = SX + (float) Math.cos(a) * 1.15f, z = SZ + (float) Math.sin(a) * 1.15f;
            float h = 0.45f + 0.15f * (float) Math.sin(t * 2.3f + k);
            out.billboard(p, Sprite.FLAME, x, BURNER_Y + 0.35f, z, h, 0, 0x4A8AFF, 0.9f);
        }
        out.billboard(p, Sprite.HALO, SX, BURNER_Y + 0.3f, SZ, 3.6f * f, 0, 0x3A6AFF, 0.45f);
        out.ground(p, Sprite.HALO, SX, 0.05f, SZ, 6.0f, 0, 0x2A4AC0, 0.25f * f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
        if (!v.on) return;
        if (r.nextInt(5) == 0) fx.particle(FxSink.SMOKE, SX + rnd(r, 1.2f), 8.6f, SZ + rnd(r, 1.2f), rnd(r, 0.01f), 0.04f, rnd(r, 0.01f), 0xD8D8D8, 0.7f, 35);
        if (r.nextInt(240) == 0) fx.sound(Snd.FIRE, SX, BURNER_Y, SZ, 0.15f, 1.6f);
    }
}
