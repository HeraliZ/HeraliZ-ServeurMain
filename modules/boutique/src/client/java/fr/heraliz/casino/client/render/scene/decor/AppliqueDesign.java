package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : applique murale de laiton a deux bras, fausses bougies et petits abat-jour de tissu plisse. Allumee :
 * les abat-jour s'illuminent, la lumiere chaude lave le mur au-dessus et au-dessous.
 */
public final class AppliqueDesign extends DecorDesign {
    static final float[][] CUPS = {{3.8f, 8.6f, 4.6f}, {12.2f, 8.6f, 4.6f}};
    static final float SHADE_Y = 10.4f, SHADE_H = 3.4f;

    private Mesh body = Mesh.EMPTY, shades = Mesh.EMPTY, bulbs = Mesh.EMPTY, wash = Mesh.EMPTY;

    public AppliqueDesign() {
        super("applique_laiton", 0xFFD890);
        itemScale = 1.0f;
        itemCenter = new float[]{8, 9, 4};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder();
        // platine ovale, rosace, bras en S
        b.push().translate(8, 8.2f, 0).rotateX(90).scale(1, 1, 1.5f);
        b.lathe(new float[][]{{1.9f, 0}, {1.9f, 0.25f}, {1.5f, 0.55f}, {0.6f, 0.7f}}, 20, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
        b.pop();
        b.push().translate(8, 8.2f, 0.7f);
        b.sphere(0.65f, 12, 8, POLISHED);
        b.pop();
        for (float[] c : CUPS) {
            float sd = Math.signum(c[0] - 8);
            b.tube(new float[][]{p(8, 8.2f, 0.9f), p(8 + sd * 1.8f, 7.0f, 2.2f), p(8 + sd * 3.6f, 6.9f, 3.6f), p(c[0], 7.6f, c[2]), p(c[0], c[1] - 0.2f, c[2])},
                    new float[]{0.28f, 0.26f, 0.24f, 0.24f, 0.24f}, 8, POLISHED, false, false, 0, false);
            // coupelle et fausse bougie
            b.push().translate(c[0], c[1] - 0.3f, c[2]);
            b.lathe(new float[][]{{0.3f, 0}, {1.1f, 0.25f}, {1.2f, 0.5f}}, 14, 0, 360, POLISHED, 1, 1, 0, 0, 0, false);
            b.cylinder(0.42f, 0.4f, 2.0f, 10, WAX, null, null);
            b.pop();
        }
        fr.heraliz.casino.client.render.mesh.Occlusion.bake(b, 0.2f, 1.4f, 0.4f, Float.NaN);
        body = b.build();
        // petits abat-jour de tissu plisse (troncs de cone)
        MeshBuilder sh = new MeshBuilder(), bl = new MeshBuilder();
        for (float[] c : CUPS) {
            sh.push().translate(c[0], SHADE_Y, c[2]);
            sh.lathe(new float[][]{{1.9f, 0}, {1.15f, SHADE_H}}, 16, 0, 360, SHADE, 1, 1, 0, 0, 0, false);
            sh.lathe(new float[][]{{1.85f, 0}, {1.1f, SHADE_H}}, 16, 0, 360, SHADE, 1, 1, 0, 0, 0, true);
            sh.pop();
            bl.push().translate(c[0], c[1] + 2.3f, c[2]).scale(1, 1.5f, 1);
            bl.sphere(0.36f, 10, 8, BULB_ON);
            bl.pop();
        }
        shades = sh.build();
        bulbs = bl.build();
        // lumiere qui lave le mur (additif) : ovales au-dessus et au-dessous de chaque abat-jour
        MeshBuilder w = new MeshBuilder();
        int from = w.mark();
        washGrid(w, -2, 0, 18, 16, 0.1f, 10, 8);
        w.shade(from, (x, y, z, nx, ny, nz, rgb) -> {
            float k = Math.max(0, 1 - Math.abs(y - 11) / 6.5f) * (1 - Math.abs(x - 8) / 11f);
            k = Ease.clamp01(k);
            rgb[0] *= k;
            rgb[1] *= k;
            rgb[2] *= k;
        });
        wash = w.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        out.solid(p, body, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        boolean on = v.on || s.item;
        sheetSolid(p, out, shades, on ? 0.75f : 0);
        out.solid(p, bulbs, on ? 1 : 0);
        if (!on || s.item) return;
        float t = s.time + s.seed * 300;
        float f = 0.95f + 0.05f * (float) Math.sin(t * 0.7f);
        out.additive(p, wash, 0xFFD8A0, 0.22f * f);
        for (float[] c : CUPS) out.billboard(p, Sprite.HALO, c[0], SHADE_Y + SHADE_H * 0.4f, c[2], 3.6f * f, 0, 0xFFC878, 0.35f);
    }
}
