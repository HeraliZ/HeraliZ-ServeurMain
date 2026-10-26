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
 * Le Coffre : pile de lingots d'or estampilles HERALIZ 999,9 (trois rangs croises), deux lingots poses a cote. Des
 * eclats de lumiere glissent sur l'or.
 */
public final class LingotsDesign extends DecorDesign {
    /** Lingot : longueur, largeur et hauteur a la base, retrait du dessus (tronc de pyramide). */
    static final float L = 5.4f, W = 2.5f, H = 1.35f, IN = 0.38f;

    private Mesh bodyS = Mesh.EMPTY;
    private final float[][] glints = {{6.2f, 4.2f, 7.4f}, {9.8f, 2.9f, 9.6f}, {4.6f, 1.6f, 11.2f}, {12.0f, 1.5f, 4.4f}};

    public LingotsDesign() {
        super("lingots", 0xFFD040);
        itemScale = 1.0f;
        itemCenter = new float[]{8, 3, 8};
    }

    @Override
    protected void build() {
        MeshBuilder s = new MeshBuilder();
        // rang du bas : 2 x 4 lingots dans la longueur
        for (int i = 0; i < 4; i++) for (int j = 0; j < 2; j++) bar(s, 4.6f + j * (L + 0.3f) + 0.0f, 0, 4.4f + i * (W + 0.25f), 0);
        // rang du milieu : 3 lingots en travers
        for (int i = 0; i < 3; i++) bar(s, 5.0f + i * (W + 0.3f), H, 8.1f, 90);
        // rang du haut : 2 lingots
        for (int i = 0; i < 2; i++) bar(s, 6.9f + i * (L + 0.2f) * 0.5f, H * 2, 6.8f + i * (W + 0.25f), 0);
        // deux lingots poses a cote, de biais
        bar(s, 12.6f, 0, 12.4f, 28);
        bar(s, 3.0f, 0, 12.8f, -50);
        fr.heraliz.casino.client.render.mesh.Occlusion.bake(s, 0.25f, 1.8f, 0.45f, 0f);
        bodyS = s.build();
    }

    /** Lingot (tronc de pyramide), centre en (x, z), base a la hauteur y, tourne de "rot" degres. */
    static void bar(MeshBuilder s, float x, float y, float z, float rot) {
        s.push().translate(x, y, z).rotateY(rot);
        float l = L / 2, w = W / 2, li = l - IN, wi = w - IN;
        float[] b0 = p(-l, 0, -w), b1 = p(l, 0, -w), b2 = p(l, 0, w), b3 = p(-l, 0, w);
        float[] t0 = p(-li, H, -wi), t1 = p(li, H, -wi), t2 = p(li, H, wi), t3 = p(-li, H, wi);
        s.face(GOLD_TOP, v(0, 1, 0), t3, t2, t1, t0);
        float[] c = p(0, H / 2, 0);
        s.flatOut(GOLD, c, b0, b1, t1, t0);
        s.flatOut(GOLD, c, b1, b2, t2, t1);
        s.flatOut(GOLD, c, b2, b3, t3, t2);
        s.flatOut(GOLD, c, b3, b0, t0, t3);
        s.pop();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        sheetSolid(p, out, bodyS, 0.15f);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        if (s.item) return;
        float t = s.time + s.seed * 500;
        for (int k = 0; k < glints.length; k++) {
            float ph = (t * 0.04f + k * 0.37f) % 1f;
            float a = Ease.bell(ph, 0.0f, 0.18f);
            if (a <= 0.01f) continue;
            float[] g = glints[k];
            out.billboard(p, Sprite.GLINT, g[0], g[1] + 0.2f, g[2], 1.4f * a, t * 3 + k * 40, 0xFFF4C0, 0.9f * a);
        }
        out.ground(p, Sprite.HALO, 8, 0.05f, 8, 8.0f, 0, 0xFFC040, 0.12f);
    }
}
