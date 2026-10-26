package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import java.util.Random;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : juke-box des annees 50 recupere dans un vieux diner. Caisse de noyer arrondie en arche, facade
 * (carrousel de disques, touches de selection, grille chromee), arches de tubes lumineux dont les couleurs tournent,
 * colonnes de verre ou montent des bulles.
 */
public final class JukeboxDesign extends DecorDesign {
    static final float CX = 8, W = 14, Y0 = 1, ARCH_Y = 13, R = 7, ZC = 8.3f, DEPTH = 8, FRONT = ZC + DEPTH / 2;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY, front = Mesh.EMPTY;
    private final Mesh[] arcs = new Mesh[3];
    private Mesh pillar = Mesh.EMPTY, bubble = Mesh.EMPTY;

    public JukeboxDesign() {
        super("juke_box", 0xFF7AD0);
        itemScale = 0.62f;
        itemCenter = new float[]{8, 10, 8};
    }

    /** Contour en arche (centre en x = 0, y = 0 en bas), sens trigonometrique. */
    static float[][] arch(float w, float y0, float yArch, float r, int segs) {
        float[][] o = new float[segs + 3][];
        o[0] = new float[]{-w / 2, y0};
        o[1] = new float[]{w / 2, y0};
        for (int i = 0; i <= segs; i++) {
            double a = Math.PI * i / segs;
            o[2 + i] = new float[]{(float) Math.cos(a) * r, yArch + (float) Math.sin(a) * r};
        }
        return o;
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // socle et caisse de noyer en arche
        b.bevelBox(1.4f, 0, ZC - DEPTH / 2 + 0.3f, 14.6f, Y0, ZC + DEPTH / 2 - 0.3f, 0.2f, DARK);
        b.push().translate(CX, 0, ZC);
        b.slab(arch(W, Y0, ARCH_Y, R, 16), DEPTH, 0.6f, true, TVWOOD, TVWOOD, TVWOOD);
        b.pop();
        // jonc chrome autour de l'arche, en facade ; pieds chromes
        int n = 24;
        float[][] trim = new float[n + 3][];
        trim[0] = p(CX - W / 2 + 0.2f, Y0 + 0.2f, FRONT + 0.15f);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI - Math.PI * i / n;
            trim[1 + i] = p(CX + (float) Math.cos(a) * (R - 0.2f), ARCH_Y + (float) Math.sin(a) * (R - 0.2f), FRONT + 0.15f);
        }
        trim[n + 2] = p(CX + W / 2 - 0.2f, Y0 + 0.2f, FRONT + 0.15f);
        s.tube(trim, filled(trim.length, 0.3f), 7, CHROME, true, true, 0, false);
        for (float x : new float[]{2.4f, 13.6f}) {
            s.push().translate(x, 0, FRONT - 0.6f);
            s.cylinder(0.55f, 0, 0.6f, 10, CHROME, null, CHROME);
            s.pop();
        }
        Mesh[] m = bakePair(b, s, 0.35f, 2.4f, 0.4f, 0f);
        body = m[0];
        bodyS = m[1];
        // facade (feuille des salles) : s'allume
        MeshBuilder f = new MeshBuilder();
        float h = ARCH_Y + R - Y0, rv = R / h;
        float[][] tex = new float[2 + 17][];
        tex[0] = new float[]{0, 1};
        tex[1] = new float[]{1, 1};
        for (int i = 0; i <= 16; i++) {
            double a = Math.PI * i / 16;
            tex[2 + i] = new float[]{0.5f + 0.5f * (float) Math.cos(a), rv - rv * (float) Math.sin(a)};
        }
        f.push().translate(CX, Y0 + h / 2, FRONT + 0.02f);
        f.badge(tex, W - 1.0f, h - 0.6f, 0.05f, JUKEBOX, null);
        f.pop();
        front = f.build();
        // arches de tubes lumineux (additif, couleurs qui tournent) et colonnes de verre
        for (int k = 0; k < 3; k++) {
            float rr = R - 1.2f - k * 0.9f;
            float[][] path = new float[21][];
            for (int i = 0; i <= 20; i++) {
                double a = Math.PI * i / 20;
                path[i] = p(CX + (float) Math.cos(a) * rr, ARCH_Y + (float) Math.sin(a) * rr, FRONT + 0.25f);
            }
            MeshBuilder t = new MeshBuilder();
            t.tube(path, filled(21, 0.26f), 6, WHITE, true, true, 0, false);
            arcs[k] = t.build();
        }
        MeshBuilder pl = new MeshBuilder();
        for (float x : new float[]{1.9f, 14.1f}) {
            pl.push().translate(x, Y0 + 1.2f, FRONT - 0.6f);
            pl.cylinder(0.55f, 0, 11.0f, 10, WHITE, WHITE, WHITE);
            pl.pop();
        }
        pillar = pl.build();
        MeshBuilder bb = new MeshBuilder();
        bb.sphere(0.22f, 6, 4, WHITE);
        bubble = bb.build();
    }

    private static float[] filled(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    /** Couleur d'un tube a l'instant t (cycle rose, ambre, jaune, cyan). */
    private static int cycle(float t) {
        int[] c = {0xFF3A8A, 0xFFA02A, 0xFFE860, 0x3AE8FF};
        float u = ((t % 160) + 160) % 160 / 40f;
        int i = (int) u;
        return Ease.mix(c[i % 4], c[(i + 1) % 4], u - i);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        boolean on = v.on || s.item;
        float t = s.time + s.seed * 400;
        sheetSolid(p, out, front, on ? 0.85f : 0);
        if (!on) return;
        for (int k = 0; k < 3; k++) out.additive(p, arcs[k], cycle(t + k * 25), 0.85f);
        out.additive(p, pillar, cycle(t + 80), 0.7f);
        if (s.item) return;
        for (int k = 0; k < 8; k++) {
            float x = k % 2 == 0 ? 1.9f : 14.1f;
            float ph = ((t * 0.035f + k * 0.13f) % 1f + 1f) % 1f;
            p.push().translate(x + 0.15f * (float) Math.sin(t * 0.3f + k), Y0 + 1.4f + ph * 10.4f, FRONT - 0.6f + 0.2f * (float) Math.cos(t * 0.2f + k));
            out.additive(p, bubble, 0xFFFFFF, 0.8f * Ease.bell(ph, 0, 1));
            p.pop();
        }
        out.billboard(p, Sprite.HALO, CX, ARCH_Y + 2, FRONT + 1.5f, 9, 0, cycle(t), 0.22f);
    }

    @Override
    public void tickFx(Scene s, DecorView v, FxSink fx, Random r) {
    }
}
