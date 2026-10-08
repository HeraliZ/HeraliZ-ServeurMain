package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;

import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Le Coffre : mur de coffres de depot de la banque (trois blocs de large, deux de haut). Six colonnes de petites
 * portes d'acier brosse numerotees, plaque de laiton et deux serrures chacune ; une porte grande ouverte sur un tiroir
 * tire (lingot et liasses), une cle oubliee dans une serrure. Corniche et plinthe de laiton.
 */
public final class CasiersDesign extends DecorDesign {
    static final float X0 = -15.5f, X1 = 31.5f, Y0 = 1.2f, Y1 = 30.0f, D = 6.6f;
    static final int COLS = 6, ROWS = 4;
    /** Porte ouverte : colonne, rangee. */
    static final int OC = 4, OR = 1;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY;

    public CasiersDesign() {
        super("casiers_coffres", 0xC8C8D0);
        itemScale = 0.32f;
        itemCenter = new float[]{8, 16, 4};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // caisson, plinthe et corniche de laiton
        s.bevelBox(X0, Y0, 0, X1, Y1, D, 0.3f, BRUSHED);
        b.bevelBox(X0 - 0.4f, 0, 0, X1 + 0.4f, Y0, D + 0.5f, 0.2f, POLISHED);
        b.bevelBox(X0 - 0.5f, Y1, 0, X1 + 0.5f, Y1 + 1.4f, D + 0.7f, 0.3f, POLISHED);
        float cw = (X1 - X0 - 1.0f) / COLS, ch = (Y1 - Y0 - 1.0f) / ROWS;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                float x0 = X0 + 0.5f + c * cw + 0.18f, x1 = x0 + cw - 0.36f, y0 = Y0 + 0.5f + r * ch + 0.18f, y1 = y0 + ch - 0.36f;
                int k = (c * 3 + r * 5) % 4;
                Mat door = DEPOSIT.sub((k % 2) * 0.5f, (k / 2) * 0.5f, (k % 2) * 0.5f + 0.5f, (k / 2) * 0.5f + 0.5f);
                if (c == OC && r == OR) {
                    // la case ouverte : interieur sombre, tiroir tire, porte pivotee sur sa gauche
                    b.box6(x0, y0, D - 4.2f, x1, y1, D + 0.01f, DARK, DARK, DARK, DARK, DARK, null);
                    s.bevelBox(x0 + 0.3f, y0 + 0.2f, D - 3.6f, x1 - 0.3f, y0 + 2.4f, D + 3.4f, 0.12f, BRUSHED);
                    s.bevelBox(x0 + 0.7f, y0 + 2.4f, D + 0.4f, x0 + 3.2f, y0 + 3.4f, D + 2.9f, 0.1f, GOLD);
                    s.push().translate(x1 - 2.0f, y0 + 2.4f, D + 1.4f);
                    s.bevelBox(-1.2f, 0, -0.75f, 1.2f, 1.1f, 0.75f, 0.06f, BILLS_SIDE, new Mat[]{BILLS_SIDE, BILLS_SIDE, BILLS_SIDE, BILLS, BILLS_SIDE, BILLS_SIDE}, 0);
                    s.pop();
                    s.push().translate(x0, y0, D).rotateY(-108);
                    s.bevelBox(0, 0, 0, x1 - x0, y1 - y0, 0.35f, 0.08f, BRUSHED, new Mat[]{BRUSHED, BRUSHED, BRUSHED, BRUSHED, BRUSHED, door}, 0);
                    s.pop();
                    continue;
                }
                s.bevelBox(x0, y0, D - 0.05f, x1, y1, D + 0.3f, 0.08f, BRUSHED, new Mat[]{BRUSHED, BRUSHED, BRUSHED, BRUSHED, BRUSHED, door}, 0);
                // petite poignee
                b.bevelBox((x0 + x1) / 2 - 0.9f, y0 + 0.6f, D + 0.3f, (x0 + x1) / 2 + 0.9f, y0 + 0.95f, D + 0.75f, 0.08f, POLISHED);
            }
        }
        // une cle oubliee dans une serrure
        float kx = X0 + 0.5f + 1 * cw + 0.18f + (cw - 0.36f) * 0.3f, ky = Y0 + 0.5f + 2 * ch + 0.18f + (ch - 0.36f) * 0.44f;
        b.push().translate(kx, ky, D + 0.3f);
        b.cylinder(0.1f, 0, 0.1f, 4, POLISHED, null, null);
        b.push().translate(0, 0, 0.1f).rotateX(90);
        b.cylinder(0.08f, 0, 0.6f, 6, POLISHED, null, POLISHED);
        b.pop();
        b.push().translate(0, 0, 0.9f).rotateZ(25);
        b.torusXY(0.45f, 0.12f, 10, 4, POLISHED);
        b.pop();
        b.pop();
        Mesh[] m = bakePair(b, s, 0.3f, 2.4f, 0.45f, Float.NaN);
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
