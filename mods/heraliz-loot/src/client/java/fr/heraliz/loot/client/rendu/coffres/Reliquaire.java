package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;

import static fr.heraliz.loot.client.rendu.coffres.AtlasT7.*;

/**
 * Reliquaire : socle hexagonal graphite cerclé de lumière, plateau gravé de pistes lumineuses, vitrine cylindrique
 * sous un chapeau tenu par trois colonnettes ; dedans, un cristal qui flotte et tourne dans un anneau. Ouverture : les
 * liseres s'intensifient, la vitrine descend dans le socle, le cristal s'élève et accélère, une colonne de lumière
 * monte.
 */
public final class Reliquaire extends DesignCoffre {
    static final float YS = 3.2f, YP = 3.8f, YG1 = 12.6f, RG = 3.9f;
    static final float GEM_Y = 7.6f;

    private Mesh socle, liseres, plateau, vitrine, cristal, anneau, colonne;

    public Reliquaire() {
        super("t7_reliquaire", 60);
    }

    @Override
    protected void build() {
        MeshBuilder s = new MeshBuilder();
        s.push().translate(8, 0, 8);
        s.cylinder(7.2f, 0, YS, 6, GRAPHITE, GRAPHITE, HEX);
        s.cylinder(6.2f, YS, YP, 6, GUNMETAL, null, null);
        // chapeau, colonnettes
        s.cylinder(4.7f, YG1, YG1 + 1.3f, 48, GRAPHITE, GRAPHITE, GRAPHITE);
        s.cylinder(3.9f, YG1 + 1.3f, YG1 + 1.6f, 48, GUNMETAL, null, GUNMETAL);
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(270 + k * 120);
            s.push().translate((float) Math.cos(a) * 5.2f, YP, (float) Math.sin(a) * 5.2f);
            s.cylinder(0.32f, 0, YG1 - YP, 12, ALU, null, null);
            s.cylinder(0.55f, 0, 0.35f, 12, GUNMETAL, null, GUNMETAL);
            s.pop();
        }
        s.pop();
        decal(s, LOGO, 8, 1.6f, 8 + 7.2f * 0.866f + 0.02f, 4.4f, 1.1f);
        socle = cuire(s, 0);

        MeshBuilder pl = new MeshBuilder();
        pl.push().translate(8, YP, 8);
        pl.disc(6.15f, 0.01f, 6, CIRCUIT, true);
        pl.pop();
        plateau = pl.build();

        MeshBuilder l = new MeshBuilder();
        l.push().translate(8, 0, 8);
        l.cylinder(7.26f, 0.5f, 0.78f, 6, LED_RED, null, null);
        l.cylinder(7.26f, YS - 0.3f, YS - 0.1f, 6, LED_RED, null, null);
        l.cylinder(4.74f, YG1 + 0.05f, YG1 + 0.3f, 48, LED_RED, LED_RED, null);
        l.pop();
        liseres = l.build();

        MeshBuilder v = new MeshBuilder();
        v.push().translate(8, 0, 8);
        v.cylinder(RG, YP, YG1, 48, GLASS, null, null);
        v.pop();
        vitrine = v.build();

        MeshBuilder c = new MeshBuilder();
        c.gem(1.5f, 0.9f, 2.3f, 8, CORE);
        cristal = c.build();
        MeshBuilder a = new MeshBuilder();
        a.torus(2.6f, 0.12f, 48, 6, 0, 360, RING_LIT);
        anneau = a.build();

        // colonne de lumière : quadrilatères colorés (vif en bas, nul en haut)
        MeshBuilder col = new MeshBuilder();
        int n = 24;
        float r = 3.4f;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float[][] pts = {MeshBuilder.p(8 + (float) Math.cos(a0) * r, YP, 8 + (float) Math.sin(a0) * r),
                    MeshBuilder.p(8 + (float) Math.cos(a1) * r, YP, 8 + (float) Math.sin(a1) * r),
                    MeshBuilder.p(8 + (float) Math.cos(a1) * r * 0.7f, 22, 8 + (float) Math.sin(a1) * r * 0.7f),
                    MeshBuilder.p(8 + (float) Math.cos(a0) * r * 0.7f, 22, 8 + (float) Math.sin(a0) * r * 0.7f)};
            lavis(col, LED_RED, MeshBuilder.v((float) Math.cos((a0 + a1) / 2), 0, (float) Math.sin((a0 + a1) / 2)), pts, 0.9f, 0.9f, 0, 0);
        }
        colonne = col.build();
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, socle, 0);
        float eclat = ferme ? 0.55f + 0.2f * (float) Math.sin(h * 0.08f) : 0.55f + 0.45f * seg(t, 0, 16);
        out.solid(p, liseres, eclat);
        out.solid(p, plateau, eclat);
        float descend = Ease.inOutSine(seg(t, 12, 36));
        p.push().translate(0, -(YG1 - YP + 0.2f) * descend, 0);
        out.translucent(p, vitrine, 0xFFFFFF, 1f);
        p.pop();
        float monte = Ease.inOutCubic(seg(t, 20, 46));
        float spin = h * (1.5f + 4f * monte);
        float y = GEM_Y + 1.6f * monte + 0.3f * (float) Math.sin(h * 0.1f);
        p.push().translate(8, y, 8).rotY(spin % 360);
        out.solid(p, cristal, 0.85f + 0.15f * (float) Math.sin(h * 0.25f));
        p.pop();
        p.push().translate(8, y, 8).rotX(70).rotZ((-h * 3f) % 360);
        out.solid(p, anneau, 1f);
        p.pop();
        float c = seg(t, 30, 52);
        if (c > 0.01f) out.additive(p, colonne, 0xFF5A3A, 0.32f * c * (0.85f + 0.15f * (float) Math.sin(h * 0.2f)));
    }

    @Override
    public float[] centreObjet() {
        return new float[]{8, 7.5f, 8};
    }

    @Override
    public float echelleObjet() {
        return 0.55f;
    }
}
