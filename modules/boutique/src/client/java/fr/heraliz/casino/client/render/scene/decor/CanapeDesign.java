package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Trou : canape deux places ramasse sur un trottoir. Velours a grosses fleurs des annees 70, passe et tache ; le
 * coussin de droite eventre (la mousse et un ressort sortent), l'accoudoir gauche rafistole au chatterton, une
 * couverture jetee sur le dossier, un pied remplace par une brique.
 */
public final class CanapeDesign extends DecorDesign {
    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public CanapeDesign() {
        super("canape_defonce", 0xB0805A);
        itemScale = 0.5f;
        itemCenter = new float[]{16, 7, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // pieds de bois (le quatrieme : une brique)
        for (float[] f : new float[][]{{2.0f, 3.2f}, {30.0f, 3.2f}, {2.0f, 13.6f}}) {
            b.push().translate(f[0], 0, f[1]);
            b.lathe(new float[][]{{0.55f, 0}, {0.7f, 0.6f}, {0.62f, 1.3f}}, 8, 0, 360, TVWOOD, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        b.bevelBox(28.6f, 0, 12.2f, 31.6f, 1.3f, 14.8f, 0.15f, PAINT_RED);
        // caisse et dossier
        s.bevelBox(0.6f, 1.3f, 2.2f, 31.4f, 5.4f, 14.6f, 0.8f, FLORAL);
        s.push().translate(16, 4.6f, 3.4f).rotateX(-11);
        s.bevelBox(-15.4f, 0, -1.9f, 15.4f, 9.6f, 1.6f, 1.0f, FLORAL);
        s.pop();
        // coussins de dossier (affaisses)
        for (int k = 0; k < 2; k++) {
            s.push().translate(8.6f + k * 14.8f, 9.6f, 5.4f).rotateX(-12).rotateZ(k == 0 ? 2 : -3);
            s.blob(6.4f, 3.5f, 1.4f, 0.35f, 0.07f, 2f, 51 + k, 18, FLORAL);
            s.pop();
        }
        // coussins d'assise : celui de droite eventre
        s.push().translate(8.7f, 6.3f, 9.4f);
        s.blob(6.6f, 1.3f, 4.9f, 0.3f, 0.05f, 2f, 61, 20, FLORAL);
        s.pop();
        s.push().translate(23.3f, 6.0f, 9.6f).rotateZ(-2);
        s.blob(6.6f, 1.1f, 4.9f, 0.32f, 0.08f, 2.2f, 63, 20, FLORAL);
        s.pop();
        s.push().translate(24.6f, 7.0f, 10.4f).rotateY(20);
        s.blob(2.4f, 0.75f, 1.8f, 0.7f, 0.3f, 2.6f, 65, 12, FOAM);
        s.pop();
        // ressort qui sort du coussin eventre
        int n = 30;
        float[][] coil = new float[n + 1][];
        float[] rr = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = i * 0.9;
            coil[i] = p(25.2f + (float) Math.cos(a) * 0.9f, 6.7f + i * 0.085f, 11.2f + (float) Math.sin(a) * 0.9f);
            rr[i] = 0.08f;
        }
        b.tube(coil, rr, 4, STEEL, true, true, 0, false);
        // accoudoirs roules
        for (int sd = 0; sd < 2; sd++) {
            float x = sd == 0 ? 1.9f : 30.1f;
            s.bevelBox(x - 1.9f, 1.3f, 2.0f, x + 1.9f, 9.0f, 14.9f, 0.7f, FLORAL);
            s.push().translate(x, 9.0f, 1.9f).rotateX(90);
            s.cylinder(2.05f, 0, 13.1f, 14, FLORAL, FLORAL, FLORAL);
            s.pop();
        }
        // chatterton sur l'accoudoir gauche
        b.push().translate(1.9f, 11.08f, 9.4f).rotateX(-90);
        tape(b, TAPE, 0, 0, 0, 3.6f, 1.0f, 15);
        tape(b, TAPE, 0.4f, 1.6f, 0.01f, 3.4f, 1.0f, -20);
        b.pop();
        // couverture jetee sur le dossier
        b.push().translate(22.5f, 13.6f, 3.2f).rotateZ(-4);
        b.blob(6.8f, 1.0f, 2.6f, 0.6f, 0.25f, 2.3f, 71, 18, BLANKET);
        b.pop();
        b.push().translate(23.4f, 10.4f, 0.9f).rotateZ(5);
        b.blob(5.2f, 3.4f, 0.5f, 0.55f, 0.2f, 2.4f, 73, 14, BLANKET);
        b.pop();
        Mesh[] m = bakePair(b, s, 0.35f, 2.6f, 0.5f, 0f);
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
