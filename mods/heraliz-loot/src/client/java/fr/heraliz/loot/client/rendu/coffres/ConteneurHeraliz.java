package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;

import static fr.heraliz.loot.client.rendu.coffres.AtlasT7.*;

/**
 * Conteneur HeraliZ : monolithe noir sur socle, quatre panneaux blindés en moulinet (motif hexagonal, écusson, joint
 * lumineux rouge sur l'arête), chapeau frappé de l'écusson. Ouverture : les joints s'embrasent, le chapeau s'élève en
 * tournant, les panneaux s'écartent puis s'enfoncent dans le socle ; au centre, la colonne quadrillée de lumière et le
 * noyau qui monte, entouré de deux anneaux.
 */
public final class ConteneurHeraliz extends DesignCoffre {
    static final float YS = 1.6f, YC = 12.6f, YT = 15.6f;
    static final float CORE_Y = 14.4f;

    private Mesh socle, panneau, joint, chapeau, colonne, noyau, anneau1, anneau2;

    public ConteneurHeraliz() {
        super("t7_conteneur_heraliz", 54);
    }

    @Override
    protected void build() {
        MeshBuilder s = new MeshBuilder();
        s.bevelBox(0.4f, 0, 0.4f, 15.6f, YS, 15.6f, 0.4f, GRAPHITE);
        s.bevelBox(0.34f, 0.85f, 0.34f, 15.66f, 1.1f, 15.66f, 0.04f, DARK);
        socle = cuire(s, 0);

        // panneau avant (x 2.4..15.2, z 13.6..15.2) ; les trois autres en tournant autour du centre
        MeshBuilder p = new MeshBuilder();
        p.bevelBox(2.4f, YS, 13.6f, 15.2f, YC, 15.2f, 0.3f, HEX);
        p.bevelBox(2.6f, YS + 0.25f, 15.2f, 15.0f, YS + 0.6f, 15.32f, 0.05f, ALU);
        p.bevelBox(2.6f, YC - 0.6f, 15.2f, 15.0f, YC - 0.25f, 15.32f, 0.05f, ALU);
        p.push().translate(8.8f, 7.1f, 15.21f);
        p.decalZ(BADGE, 0, 0, 0, 4.6f, 4.6f);
        p.pop();
        panneau = cuire(p, Float.NaN, socle);
        MeshBuilder j = new MeshBuilder();
        j.bevelBox(2.4f, YS + 0.4f, 15.2f, 2.85f, YC - 0.4f, 15.32f, 0.05f, LED_RED);
        joint = j.build();

        MeshBuilder c = new MeshBuilder();
        c.bevelBox(0.4f, YC, 0.4f, 15.6f, YT, 15.6f, 0.6f, GRAPHITE);
        c.bevelBox(0.34f, YC + 0.3f, 0.34f, 15.66f, YC + 0.55f, 15.66f, 0.04f, LED_RED);
        decalHaut(c, BADGE, 8, YT + 0.01f, 8, 7.0f, 7.0f);
        decal(c, LOGO, 8, YC + 1.9f, 15.62f, 5.2f, 1.3f);
        chapeau = cuire(c, Float.NaN);

        MeshBuilder col = new MeshBuilder();
        col.bevelBox(4.3f, YS, 4.3f, 11.7f, YC - 0.2f, 11.7f, 0.2f, GRID_LIT);
        col.push().translate(8, YC - 0.2f, 8);
        col.cylinder(2.4f, 0, 0.4f, 32, GUNMETAL, null, GUNMETAL);
        col.pop();
        colonne = cuire(col, Float.NaN);

        MeshBuilder n = new MeshBuilder();
        n.gem(2.0f, 1.2f, 2.8f, 10, CORE);
        noyau = n.build();
        MeshBuilder a = new MeshBuilder();
        a.torus(3.3f, 0.14f, 56, 6, 0, 360, RING_LIT);
        anneau1 = a.build();
        a = new MeshBuilder();
        a.torus(4.0f, 0.1f, 56, 6, 0, 360, RING_LIT);
        anneau2 = a.build();
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, socle, 0);
        float joints = ferme ? 0.5f + 0.2f * (float) Math.sin(h * 0.1f) : 0.5f + 0.5f * seg(t, 0, 10);
        float ecarte = 1.0f * Ease.outCubic(seg(t, 16, 24)) * (1 - Ease.inOutSine(seg(t, 24, 30)));
        float enfonce = (YC - YS + 0.2f) * Ease.inOutSine(seg(t, 24, 44));
        float grille = seg(t, 20, 34);
        if (enfonce > 0.05f || grille > 0) out.solid(p, colonne, 0.3f + 0.7f * grille);
        for (int k = 0; k < 4; k++) {
            p.push();
            autour(p, 8, 0, 8, 'y', k * 90f);
            p.translate(0, -enfonce, ecarte);
            out.solid(p, panneau, 0);
            out.solid(p, joint, joints);
            p.pop();
        }
        float leve = 4.2f * Ease.outCubic(seg(t, 8, 24));
        float tour = 90f * Ease.inOutSine(seg(t, 12, 30)) + (t >= ouverture ? (h * 0.4f) % 360 : 0);
        p.push().translate(0, leve, 0);
        autour(p, 8, 0, 8, 'y', tour);
        out.solid(p, chapeau, joints);
        p.pop();
        float c = Ease.outCubic(seg(t, 22, 42));
        if (c > 0.01f) {
            float y = (YC - 3) + (CORE_Y - YC + 3) * c + 0.3f * (float) Math.sin(h * 0.09f);
            float sc = 0.4f + 0.6f * c;
            p.push().translate(8, y, 8).scale(sc).rotY((h * 3f) % 360);
            out.solid(p, noyau, 0.85f + 0.15f * (float) Math.sin(h * 0.3f));
            p.pop();
            p.push().translate(8, y, 8).scale(sc).rotX(18).rotY((-h * 4f) % 360);
            out.solid(p, anneau1, 1f);
            p.pop();
            p.push().translate(8, y, 8).scale(sc).rotZ(64).rotY((h * 2.5f) % 360);
            out.solid(p, anneau2, 1f);
            p.pop();
        }
    }

    @Override
    public float[] centreObjet() {
        return new float[]{8, 8, 8};
    }

    @Override
    public float echelleObjet() {
        return 0.6f;
    }
}
