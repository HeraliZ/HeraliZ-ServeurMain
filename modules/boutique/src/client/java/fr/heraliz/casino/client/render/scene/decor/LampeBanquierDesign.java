package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : lampe de banquier. Abat-jour de verre vert en demi-cylindre, pied de laiton ovale, tirette a perle.
 * A poser sur un comptoir ou une table ; allumee, le verre s'illumine et une flaque de lumiere chaude tombe dessous.
 */
public final class LampeBanquierDesign extends DecorDesign {
    static final float SY = 6.7f, SZ = 8.4f, SR = 2.3f, SX0 = 4.0f, SX1 = 12.0f;

    private Mesh body = Mesh.EMPTY, glass = Mesh.EMPTY, bulb = Mesh.EMPTY, pool = Mesh.EMPTY;

    public LampeBanquierDesign() {
        super("lampe_banquier", 0x3AD870);
        itemScale = 1.1f;
        itemCenter = new float[]{8, 4.5f, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // pied ovale, colonne, bras en lyre qui tient l'abat-jour
        b.push().translate(8, 0, 8).scale(1.45f, 1, 1);
        b.lathe(new float[][]{{2.6f, 0}, {2.6f, 0.25f}, {2.2f, 0.6f}, {1.1f, 0.8f}}, 22, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
        b.disc(2.6f, 0, 22, DARK, false);
        b.pop();
        b.push().translate(8, 0.8f, 8);
        b.cylinder(0.3f, 0, 4.2f, 10, POLISHED, null, null);
        b.push().translate(0, 1.0f, 0);
        b.torus(0.36f, 0.1f, 10, 4, 0, 360, POLISHED);
        b.pop();
        b.pop();
        b.tube(new float[][]{p(8, 5.0f, 8), p(8, 5.6f, 8.2f), p(8, SY - SR * 0.2f, SZ - 0.4f)}, new float[]{0.28f, 0.24f, 0.22f}, 8, POLISHED, false, true, 0, false);
        for (float x : new float[]{SX0 + 0.25f, SX1 - 0.25f}) {
            b.tube(new float[][]{p(8, 5.2f, 8.1f), p((8 + x) / 2, 5.6f, 8.2f), p(x, SY, SZ)}, new float[]{0.14f, 0.14f, 0.14f}, 6, POLISHED, false, false, 0, false);
        }
        // embouts de laiton de l'abat-jour, tirette a perle
        for (float x : new float[]{SX0, SX1 - 0.3f}) {
            b.push().translate(x, SY, SZ).rotateZ(-90);
            b.lathe(new float[][]{{SR + 0.12f, 0}, {SR + 0.12f, 0.3f}}, 14, 80, 280, POLISHED, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        b.tube(new float[][]{p(10.4f, SY - 0.6f, SZ + 0.8f), p(10.4f, SY - 2.4f, SZ + 0.9f), p(10.4f, SY - 3.4f, SZ + 0.9f)}, new float[]{0.05f, 0.05f, 0.05f}, 4, POLISHED,
                false, false, 0, false);
        b.push().translate(10.4f, SY - 3.6f, SZ + 0.9f);
        b.sphere(0.24f, 8, 6, POLISHED);
        b.pop();
        // interieur blanc de l'abat-jour
        b.push().translate(SX0, SY, SZ).rotateZ(-90);
        b.lathe(new float[][]{{SR - 0.1f, 0.1f}, {SR - 0.1f, SX1 - SX0 - 0.1f}}, 14, 80, 280, ENAMEL_IN, 1, 1, 0, 0, 0, true);
        b.pop();
        fr.heraliz.casino.client.render.mesh.Occlusion.bake(b, 0.2f, 1.4f, 0.4f, 0f);
        body = b.build();
        // verre vert (feuille des salles : s'illumine)
        MeshBuilder g = new MeshBuilder();
        g.push().translate(SX0, SY, SZ).rotateZ(-90);
        g.lathe(new float[][]{{SR, 0}, {SR, SX1 - SX0}}, 14, 80, 280, BANKER, 1, 1, 0, 0, 0, false);
        g.pop();
        glass = g.build();
        MeshBuilder u = new MeshBuilder();
        u.push().translate(SX0 + 0.8f, SY - 0.4f, SZ).rotateZ(-90);
        u.cylinder(0.5f, 0, SX1 - SX0 - 1.6f, 10, BULB_ON, BULB_ON, BULB_ON);
        u.pop();
        bulb = u.build();
        // flaque de lumiere sous l'abat-jour (additif)
        MeshBuilder c = new MeshBuilder();
        int from = c.mark();
        c.lathe(new float[][]{{3.0f, SY - 0.6f}, {5.0f, 2.6f}, {6.6f, 0.15f}}, 22, 0, 360, WHITE, 1.25f, 0.8f, 0, 0, 0, false);
        c.shade(from, (x, y, z, nx, ny, nz, rgb) -> {
            float k = Ease.clamp01(y / (SY - 0.6f));
            rgb[0] *= k * k;
            rgb[1] *= k * k;
            rgb[2] *= k * k;
        });
        pool = c.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        boolean on = v.on || s.item;
        sheetSolid(p, out, glass, on ? 0.8f : 0);
        out.solid(p, bulb, on ? 1 : 0);
        if (!on || s.item) return;
        p.push().translate(8, 0, SZ - 0.2f);
        out.additive(p, pool, 0xFFE0A0, 0.14f);
        p.pop();
        out.ground(p, Sprite.HALO, 8, 0.05f, SZ, 6.0f, 0, 0xFFD890, 0.35f);
        out.billboard(p, Sprite.HALO, 8, SY, SZ, 3.6f, 0, 0x3AE070, 0.25f);
    }
}
