package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Cartes a jouer 3D : 52 faces (atlas : 13 colonnes x 4 lignes, cases de 64 x 96 px) + un dos.
 * Carte a plat dans le plan XZ (face vers +Y), centree a l'origine, le haut de la carte vers -Z.
 */
public final class CardMeshes {
    public static final int CELL_W = 64, CELL_H = 96;
    public final float w, h;
    private final Mesh[] faces = new Mesh[52];
    private final Mesh back;

    /**
     * @param sheet zone de l'atlas des 52 cartes (x, y) ; backMat : dos (FIT) ; edge : tranche.
     */
    public CardMeshes(int[] sheet, Mat backMat, Mat edge, float w, float h) {
        this.w = w;
        this.h = h;
        for (int c = 0; c < 52; c++) {
            int col = c % 13, row = c / 13;
            Mat face = Mat.fit(sheet[0] + col * CELL_W, sheet[1] + row * CELL_H, CELL_W, CELL_H);
            faces[c] = card(face, backMat, edge);
        }
        back = card(backMat, backMat, edge);
    }

    private Mesh card(Mat face, Mat backMat, Mat edge) {
        MeshBuilder b = new MeshBuilder();
        float hw = w / 2, hh = h / 2, t = 0.05f;
        float[][] pts = {p(-hw, t, hh), p(hw, t, hh), p(hw, t, -hh), p(-hw, t, -hh)};
        float[][] uv = {{face.u(0), face.v(1)}, {face.u(1), face.v(1)}, {face.u(1), face.v(0)}, {face.u(0), face.v(0)}};
        b.poly(pts, uv, null, v(0, 1, 0));
        float[][] pb = {p(-hw, -t, -hh), p(hw, -t, -hh), p(hw, -t, hh), p(-hw, -t, hh)};
        float[][] ub = {{backMat.u(1), backMat.v(0)}, {backMat.u(0), backMat.v(0)}, {backMat.u(0), backMat.v(1)}, {backMat.u(1), backMat.v(1)}};
        b.poly(pb, ub, null, v(0, -1, 0));
        b.box6(-hw + 0.08f, -t, -hh + 0.08f, hw - 0.08f, t, hh - 0.08f, edge, edge, null, null, edge, edge);
        return b.build();
    }

    /** Carte (0..51) ; negative = dos. */
    public Mesh get(int card) {
        return card < 0 || card >= 52 ? back : faces[card];
    }
}
