package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : palmier en pot de laiton cabosse (deux blocs de haut), un peu poussiereux, les pointes des palmes
 * sechees. Seul le pot a une collision ; le haut est reserve.
 */
public final class PalmierDesign extends DecorDesign {
    static final float SOIL = 7.8f;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public PalmierDesign() {
        super("palmier_pot", 0x6AB048);
        itemScale = 0.5f;
        itemCenter = new float[]{8, 14, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // pot de laiton martele, rebord, terreau
        b.push().translate(8, 0, 8);
        b.lathe(new float[][]{{2.7f, 0}, {3.1f, 0.4f}, {3.6f, 3.0f}, {4.25f, 7.0f}, {4.55f, 8.1f}, {4.35f, 8.5f}}, 24, 0, 360, POLISHED, 1, 1, 0.025f, 1.4f, 7, false);
        b.lathe(new float[][]{{4.15f, 8.4f}, {3.9f, SOIL}}, 24, 0, 360, POLISHED, 1, 1, 0, 0, 0, true);
        b.disc(2.7f, 0, 24, DARK, false);
        b.disc(3.95f, SOIL, 24, SOOT, true);
        b.pop();
        // stipes fibreux
        Random r = new Random(191);
        float[][] tops = {{8.0f, 13.6f, 8.0f}, {7.0f, 11.6f, 8.8f}, {9.0f, 12.2f, 7.4f}};
        for (float[] t : tops) {
            s.tube(new float[][]{p(8 + (t[0] - 8) * 0.3f, SOIL, 8 + (t[2] - 8) * 0.3f), p((8 + t[0]) / 2, (SOIL + t[1]) / 2, (8 + t[2]) / 2), p(t[0], t[1], t[2])},
                    new float[]{0.75f, 0.6f, 0.5f}, 8, CIGAR, false, true, 0, false);
        }
        // palmes : rubans arques, visibles des deux cotes
        int fronds = 9;
        for (int k = 0; k < fronds; k++) {
            float[] base = tops[k % 3];
            double phi = Math.PI * 2 * k / fronds + r.nextFloat() * 0.35f;
            float len = 10.5f + r.nextFloat() * 3.5f, rise = 6.5f + r.nextFloat() * 5.0f;
            float cx = (float) Math.cos(phi), cz = (float) Math.sin(phi);
            int n = 11;
            float[][] path = new float[n][];
            float[] widths = new float[n];
            for (int i = 0; i < n; i++) {
                float t = (float) i / (n - 1);
                float rad = len * t;
                float y = base[1] + rise * (float) Math.sin(Math.PI * t * 0.7f) - 7.5f * t * t * t;
                path[i] = p(base[0] + cx * rad, y, base[2] + cz * rad);
                widths[i] = 0.6f + 6.6f * (float) Math.sin(Math.PI * Math.min(1, t * 1.05f));
            }
            float[] side = {-cz, 0.25f, cx};
            s.ribbon(path, widths, side, PALM);
            s.ribbon(path, widths, new float[]{cz, -0.25f, -cx}, PALM);
        }
        Mesh[] m = bakePair(b, s, 0.35f, 2.4f, 0.35f, 0f);
        body = m[0];
        bodyS = m[1];
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
    }
}
