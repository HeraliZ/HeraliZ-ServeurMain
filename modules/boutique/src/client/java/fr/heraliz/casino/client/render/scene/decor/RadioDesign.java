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
 * Le Trou : ghetto-blaster pose sur une caisse de fruits retournee. Deux haut-parleurs, une cassette "MIXTAPE",
 * poignee et antenne chromees. Allume : l'afficheur et les vu-metres dansent, les membranes des haut-parleurs
 * battent la mesure.
 */
public final class RadioDesign extends DecorDesign {
    static final float FX0 = 2.4f, FX1 = 13.6f, FY0 = 4.85f, FY1 = 10.25f, FZ = 10.42f;

    private Mesh body = Mesh.EMPTY, bodyS = Mesh.EMPTY, front = Mesh.EMPTY, cones = Mesh.EMPTY;

    public RadioDesign() {
        super("radio_cassette", 0x40FF80);
        itemScale = 0.9f;
        itemCenter = new float[]{8, 6, 8};
    }

    @Override
    protected void build() {
        MeshBuilder b = new MeshBuilder(), s = new MeshBuilder();
        // caisse de fruits retournee : montants, lattes, fond plein sur le dessus
        for (float[] c : new float[][]{{1.6f, 3.7f}, {13.4f, 3.7f}, {1.6f, 11.3f}, {13.4f, 11.3f}}) b.bevelBox(c[0], 0, c[1], c[0] + 1.0f, 4.6f, c[1] + 1.0f, 0.1f, PALLET);
        for (float y : new float[]{0.4f, 2.6f}) {
            b.bevelBox(1.5f, y, 3.6f, 14.5f, y + 1.5f, 4.0f, 0.1f, PALLET);
            b.bevelBox(1.5f, y, 12.0f, 14.5f, y + 1.5f, 12.4f, 0.1f, PALLET);
            b.bevelBox(1.5f, y, 3.6f, 1.9f, y + 1.5f, 12.4f, 0.1f, PALLET);
            b.bevelBox(14.1f, y, 3.6f, 14.5f, y + 1.5f, 12.4f, 0.1f, PALLET);
        }
        for (int k = 0; k < 3; k++) b.bevelBox(1.5f + k * 4.35f, 4.2f, 3.6f, 1.5f + k * 4.35f + 4.2f, 4.6f, 12.4f, 0.08f, PALLET);
        // boitier du poste
        b.bevelBox(2.2f, 4.6f, 5.4f, 13.8f, 10.4f, 10.4f, 0.6f, CASE);
        // poignee chromee, antenne telescopique, boutons sur le cote
        s.tube(new float[][]{p(3.6f, 10.4f, 7.9f), p(3.9f, 11.9f, 7.9f), p(5.4f, 12.4f, 7.9f), p(10.6f, 12.4f, 7.9f), p(12.1f, 11.9f, 7.9f), p(12.4f, 10.4f, 7.9f)},
                new float[]{0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f}, 7, CHROME, true, true, 0, false);
        s.tube(new float[][]{p(12.9f, 10.3f, 6.2f), p(13.9f, 13.0f, 5.1f), p(14.7f, 15.4f, 4.2f), p(15.3f, 17.4f, 3.4f)}, new float[]{0.22f, 0.17f, 0.12f, 0.09f}, 6,
                CHROME, true, true, 0, false);
        for (float y : new float[]{6.4f, 8.6f}) {
            b.push().translate(13.8f, y, 7.9f).rotateZ(-90);
            b.cylinder(0.55f, 0, 0.6f, 10, DARK, null, KNOB);
            b.pop();
        }
        Mesh[] m = bakePair(b, s, 0.3f, 2.0f, 0.4f, 0f);
        body = m[0];
        bodyS = m[1];
        // facade (feuille des salles) : dessinee a part pour s'allumer
        MeshBuilder f = new MeshBuilder();
        f.decalZ(BOOMBOX, (FX0 + FX1) / 2, (FY0 + FY1) / 2, FZ, FX1 - FX0, FY1 - FY0);
        front = f.build();
        // membranes des haut-parleurs (avancent au rythme)
        MeshBuilder c = new MeshBuilder();
        for (float u : new float[]{0.17f, 0.83f}) {
            float cx = FX0 + (FX1 - FX0) * u, cy = FY1 - (FY1 - FY0) * 0.58f;
            c.push().translate(cx, cy, FZ).rotateX(90);
            c.cylinder(1.0f, 0, 0.2f, 14, DARK, null, DARK);
            c.pop();
        }
        cones = c.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, body, bodyS, 0);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        boolean on = v.on && !s.item;
        float t = s.time + s.seed * 400;
        // pulsation : grosse caisse tous les 6 ticks, caisse claire entre
        float beat = on ? Math.max(Ease.bell(t % 6, 0, 2.2f), 0.55f * Ease.bell((t + 3) % 6, 0, 1.6f)) : 0;
        sheetSolid(p, out, front, on ? 0.55f + 0.45f * beat : 0);
        p.push().translate(0, 0, 0.12f + 0.22f * beat);
        out.solid(p, cones, 0);
        p.pop();
        if (on) out.billboard(p, Sprite.HALO, 8, FY1 - 1.2f, FZ + 0.4f, 2.6f, 0, 0x40FF80, 0.2f + 0.2f * beat);
    }
}
