package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;
import org.joml.Vector3f;

import static fr.heraliz.loot.client.rendu.coffres.AtlasT7.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Coffre d'armes : valise rigide étanche de 1,9 m posée sur deux blocs (coque graphite aux coins arrondis, nervures,
 * joint caoutchouc, quatre grenouillères, deux poignées, valve). Ouverture : les grenouillères basculent l'une après
 * l'autre, le voyant passe au vert, le couvercle se lève sur ses vérins ; dedans, un fusil de précision dans sa mousse
 * découpée, éclairé par le bandeau du couvercle.
 */
public final class CoffreArmes extends DesignCoffre {
    static final float CX = 16, W = 30.0f, D = 9.2f, R = 1.4f;
    static final float YB = 4.6f, YL = 7.2f;
    static final float ZF = 8 + D / 2, ZB = 8 - D / 2;
    static final float X0 = CX - W / 2, X1 = CX + W / 2;
    /** Charnière (arrière). */
    static final float HY = YB, HZ = ZB - 0.15f;
    static final float[] LATCH_X = {4.0f, 11.0f, 21.0f, 28.0f};

    private Mesh base, couvercle, grenouillere, ledRouge, ledVerte, bandeau, lavisMousse, corpsVerin, tigeVerin;

    public CoffreArmes() {
        super("t7_coffre_armes", 44);
    }

    @Override
    protected void build() {
        float[][] contour = MeshBuilder.roundRect(W, D, R, 5);
        // ------------------------------------------------------------ coque basse (fixe)
        MeshBuilder b = new MeshBuilder();
        b.push().translate(CX, YB / 2, 8).rotateX(-90);
        b.slab(contour, YB, 0.45f, true, GRAPHITE, GRAPHITE, GRAPHITE);
        b.pop();
        decalHaut(b, FOAM_CUT, CX, YB + 0.01f, 8, W - 1.8f, D - 1.6f);
        // fusil calé sur la découpe : modèle dessiné pour une découpe de 13,2 px, étiré à la longueur de la caisse
        b.push().translate(X0 + 0.9f, 0, 0).scale((W - 1.8f) / 13.2f, 1, 1).translate(-1.4f, 0, 0);
        fusil(b, YB);
        b.pop();
        // joint caoutchouc : sa face haute reste sous le bord (pas de faces confondues)
        b.push().translate(CX, YB - 0.16f, 8).rotateX(-90);
        b.slab(MeshBuilder.roundRect(W + 0.2f, D + 0.2f, R + 0.1f, 5), 0.24f, 0.05f, true, RUBBER, RUBBER, RUBBER);
        b.pop();
        b.push().translate(CX, 1.6f, 8).rotateX(-90);
        b.slab(MeshBuilder.roundRect(W + 0.24f, D + 0.24f, R + 0.12f, 5), 0.5f, 0.15f, true, GRAPHITE, GRAPHITE, GRAPHITE);
        b.pop();
        for (float x : LATCH_X) {
            b.bevelBox(x - 1.0f, 2.3f, ZF - 0.1f, x + 1.0f, 3.9f, ZF + 0.35f, 0.15f, DARK);
            b.push().translate(x - 0.9f, 3.45f, ZF + 0.55f).rotateZ(-90);
            b.cylinder(0.22f, 0, 1.8f, 10, CHROME, CHROME, CHROME);
            b.pop();
        }
        for (float cx : new float[]{8.0f, 24.0f}) {
            for (float x : new float[]{cx - 1.8f, cx + 1.8f}) b.bevelBox(x - 0.55f, 2.4f, ZF - 0.1f, x + 0.55f, 3.6f, ZF + 0.6f, 0.15f, DARK);
            b.tube(new float[][]{p(cx - 1.8f, 3.0f, ZF + 0.5f), p(cx - 1.4f, 3.0f, ZF + 1.15f), p(cx + 1.4f, 3.0f, ZF + 1.15f), p(cx + 1.8f, 3.0f, ZF + 0.5f)},
                    new float[]{0.42f, 0.45f, 0.45f, 0.42f}, 12, RUBBER, false, false, 0, false);
        }
        for (float[] q : new float[][]{{X0 + 1.4f, ZB + 1.4f}, {X1 - 1.4f, ZB + 1.4f}, {X0 + 1.4f, ZF - 1.4f}, {X1 - 1.4f, ZF - 1.4f}, {CX, ZB + 1.4f}, {CX, ZF - 1.4f}}) pied(b, q[0], q[1], 0.7f, 0.3f);
        for (float x : new float[]{4.0f, 15.3f, 26.6f}) {
            b.push().translate(x, HY, HZ).rotateZ(-90);
            b.cylinder(0.42f, 0, 1.4f, 12, GUNMETAL, GUNMETAL, GUNMETAL);
            b.pop();
        }
        base = cuire(b, 0);

        // ------------------------------------------------------------ couvercle (pivote à l'arrière)
        MeshBuilder c = new MeshBuilder();
        float lh = YL - YB;
        c.push().translate(CX, YB + lh / 2, 8).rotateX(-90);
        c.slab(contour, lh, 0.5f, true, GRAPHITE, FOAM, GRAPHITE);
        c.pop();
        for (float z : new float[]{5.0f, 7.65f, 10.3f}) c.bevelBox(X0 + 2.2f, YL - 0.1f, z, X1 - 2.2f, YL + 0.3f, z + 0.7f, 0.14f, GRAPHITE);
        decalHaut(c, T7, X0 + 2.6f, YL + 0.01f, 11.8f, 1.8f, 0.9f);
        decal(c, LOGO, CX, YB + lh / 2, ZF + 0.02f, 5.6f, 1.4f);
        c.push().translate(X0 + 2.2f, YB + lh / 2, ZF - 0.15f).rotateX(90);
        c.cylinder(0.6f, 0, 0.45f, 16, GUNMETAL, null, GUNMETAL);
        c.cylinder(0.22f, 0.45f, 0.6f, 10, CHROME, null, CHROME);
        c.pop();
        for (float x : new float[]{5.4f, 16.7f}) {
            c.push().translate(x + 4.0f, HY, HZ).rotateZ(90);
            c.cylinder(0.42f, 0, 4.0f, 12, GUNMETAL, GUNMETAL, GUNMETAL);
            c.pop();
        }
        for (float x : LATCH_X) c.bevelBox(x - 0.75f, 5.1f, ZF - 0.05f, x + 0.75f, 5.7f, ZF + 0.3f, 0.1f, CHROME);
        couvercle = cuire(c, Float.NaN, base);

        MeshBuilder s = new MeshBuilder();
        s.bevelBox(19.4f, YB + 1.05f, ZF - 0.02f, 20.1f, YB + 1.45f, ZF + 0.1f, 0.05f, LED_RED);
        ledRouge = s.build();
        s = new MeshBuilder();
        s.bevelBox(19.4f, YB + 1.05f, ZF - 0.02f, 20.1f, YB + 1.45f, ZF + 0.1f, 0.05f, LED_GREEN);
        ledVerte = s.build();
        s = new MeshBuilder();
        s.bevelBox(X0 + 2.2f, YB - 0.05f, ZF - 1.5f, X1 - 2.2f, YB + 0.02f, ZF - 1.0f, 0.02f, LED_WHITE);
        bandeau = s.build();
        s = new MeshBuilder();
        lavis(s, LED_WHITE, v(0, 1, 0), new float[][]{p(X0 + 0.8f, YB + 0.03f, ZB + 0.8f), p(X1 - 0.8f, YB + 0.03f, ZB + 0.8f), p(X1 - 0.8f, YB + 0.03f, ZF - 0.8f), p(X0 + 0.8f, YB + 0.03f, ZF - 0.8f)},
                0.85f, 0.85f, 0.3f, 0.3f);
        lavisMousse = s.build();

        MeshBuilder g = new MeshBuilder();
        g.bevelBox(-0.85f, 0, -0.25f, 0.85f, 2.3f, 0.3f, 0.18f, GUNMETAL);
        g.bevelBox(-0.6f, 1.9f, -0.45f, 0.6f, 2.25f, 0.0f, 0.06f, CHROME);
        grenouillere = g.build();

        MeshBuilder vc = new MeshBuilder();
        vc.cylinder(0.32f, 0, 1, 10, GUNMETAL, GUNMETAL, GUNMETAL);
        corpsVerin = vc.build();
        vc = new MeshBuilder();
        vc.cylinder(0.16f, 0, 1, 8, CHROME, null, CHROME);
        tigeVerin = vc.build();
    }

    /**
     * Fusil couché dans sa découpe de mousse (canon vers +X), avec deux chargeurs de rechange, dessiné pour une
     * découpe de 13,2 px partant de x = 1,4 (étiré ensuite à la longueur de la caisse).
     */
    static void fusil(MeshBuilder b, float y) {
        float y0 = y - 0.15f;
        b.bevelBox(2.2f, y0, 7.45f, 4.6f, y0 + 0.6f, 8.75f, 0.2f, RUBBER);
        b.bevelBox(4.4f, y0, 7.55f, 5.0f, y0 + 0.55f, 7.95f, 0.08f, GUNMETAL);
        b.bevelBox(4.6f, y0, 6.95f, 8.5f, y0 + 0.85f, 7.85f, 0.18f, GUNMETAL);
        b.bevelBox(8.4f, y0 + 0.05f, 7.0f, 11.8f, y0 + 0.75f, 7.72f, 0.2f, DARK);
        for (int k = 0; k < 5; k++) b.bevelBox(8.8f + k * 0.6f, y0 + 0.74f, 7.15f, 9.1f + k * 0.6f, y0 + 0.8f, 7.57f, 0.02f, GUNMETAL);
        b.tube(new float[][]{p(11.8f, y0 + 0.4f, 7.36f), p(13.4f, y0 + 0.4f, 7.36f)}, new float[]{0.2f, 0.18f}, 10, GUNMETAL, false, true, 0, false);
        b.bevelBox(12.9f, y0 + 0.15f, 7.18f, 13.5f, y0 + 0.65f, 7.54f, 0.08f, DARK);
        b.bevelBox(5.1f, y0, 7.85f, 5.9f, y0 + 0.55f, 9.7f, 0.15f, DARK);
        b.bevelBox(6.5f, y0, 7.85f, 7.1f, y0 + 0.5f, 9.3f, 0.15f, RUBBER);
        b.push().translate(5.0f, y0 + 1.25f, 7.4f).rotateZ(-90);
        b.cylinder(0.3f, 0, 3.0f, 16, DARK, DARK, DARK);
        b.cylinder(0.38f, 0, 0.5f, 16, DARK, DARK, GLASS.withWorld(1));
        b.cylinder(0.36f, 2.5f, 3.0f, 16, DARK, LED_OFF, DARK);
        b.pop();
        for (float x : new float[]{5.6f, 7.4f}) b.bevelBox(x, y0 + 0.8f, 7.22f, x + 0.35f, y0 + 1.0f, 7.58f, 0.05f, GUNMETAL);
        for (float x : new float[]{10.0f, 11.4f}) b.bevelBox(x, y0, 8.55f, x + 1.1f, y0 + 0.5f, 10.25f, 0.15f, DARK);
    }

    /** Angle du couvercle (degrés, négatif = vers l'arrière). */
    private float angle(float t) {
        return -104f * Ease.outBack(seg(t, 14, 36), 1.1f);
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, base, 0);
        float a = angle(t);
        float lum = seg(t, 16, 30);
        if (lum > 0.01f) out.additive(p, lavisMousse, 0xFFF4EA, 0.3f * lum);
        p.push();
        autour(p, 0, HY, HZ, 'x', a);
        out.solid(p, couvercle, 0);
        float pulse = ferme ? 0.7f + 0.3f * (float) Math.sin(h * 0.12f) : 1f;
        out.solid(p, t >= 11 ? ledVerte : ledRouge, pulse);
        if (lum > 0.01f) out.solid(p, bandeau, lum);
        p.pop();
        for (int k = 0; k < LATCH_X.length; k++) {
            float f = Ease.outBack(seg(t, 1.5f + k * 2.2f, 6.5f + k * 2.2f), 1.4f);
            p.push().translate(LATCH_X[k], 3.45f, ZF + 0.55f).rotX(112f * f);
            out.solid(p, grenouillere, 0);
            p.pop();
        }
        if (a < -0.5f) {
            Pose q = new Pose();
            autour(q, 0, HY, HZ, 'x', a);
            for (float x : new float[]{X0 + 0.9f, X1 - 0.9f}) {
                float[] bas = {x, 2.4f, ZB + 2.2f};
                float[] haut = q.apply(x, YB + 0.6f, ZB + 3.6f);
                Vector3f d = new Vector3f(haut[0] - bas[0], haut[1] - bas[1], haut[2] - bas[2]);
                float len = d.length();
                d.div(len);
                float deg = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d.y))));
                p.push().translate(bas[0], bas[1], bas[2]);
                if (deg > 0.01f) p.rot(deg, d.z, 0, -d.x);
                p.push().scale(1, len * 0.58f, 1);
                out.solid(p, corpsVerin, 0);
                p.pop();
                p.translate(0, len * 0.5f, 0).scale(1, len * 0.5f, 1);
                out.solid(p, tigeVerin, 0);
                p.pop();
            }
        }
    }

    @Override
    public float[] bornes() {
        return new float[]{X0, 0, ZB, X1, YL + 0.4f, ZF + 1.2f};
    }
}
