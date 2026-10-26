package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;

import static fr.heraliz.loot.client.rendu.coffres.AtlasT7.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Mallette scellée : attaché-case en aluminium brossé posé à plat (coins arrondis, nervures, jonc caoutchouc), deux
 * serrures à molettes, poignée gainée, lecteur biométrique sur le couvercle. Ouverture : un trait laser balaie le
 * lecteur, le voyant passe au vert, les molettes tournent, les fermoirs sautent, le couvercle s'ouvre ; dedans, mousse
 * découpée et bandeau lumineux.
 */
public final class Mallette extends DesignCoffre {
    static final float W = 13.6f, D = 8.8f, R = 1.0f;
    static final float Y0 = 0.35f, YB = 2.55f, YL = 4.3f;
    static final float ZF = 8 + D / 2, ZB = 8 - D / 2;
    static final float HY = YB, HZ = ZB - 0.1f;
    static final float[] SERRURE_X = {3.4f, 12.6f};
    /** Lecteur biométrique (sur le couvercle). */
    static final float SX0 = 10.0f, SX1 = 13.2f, SZ0 = 9.4f, SZ1 = 11.6f;

    private Mesh base, couvercle, fermoir, molette, ledRouge, ledVerte, laser, bandeau, lavisMousse;

    public Mallette() {
        super("t7_mallette", 46);
    }

    @Override
    protected void build() {
        float[][] contour = MeshBuilder.roundRect(W, D, R, 5);
        // ------------------------------------------------------------ coque basse
        MeshBuilder b = new MeshBuilder();
        float hb = YB - Y0;
        b.push().translate(8, Y0 + hb / 2, 8).rotateX(-90);
        b.slab(contour, hb, 0.3f, true, ALU, ALU, ALU);
        b.pop();
        decalHaut(b, FOAM.asFit(), 8, YB + 0.01f, 8, W - 1.4f, D - 1.2f);
        // liasses de billets (2 rangées de 3), bague de papier
        for (int r = 0; r < 2; r++) {
            for (int k = 0; k < 3; k++) {
                float x0 = 2.4f + k * 3.85f, z0 = 4.6f + r * 3.5f, x1 = x0 + 3.5f, z1 = z0 + 3.1f, y1 = YB + 0.45f;
                b.bevelBox(x0, YB - 0.6f, z0, x1, y1, z1, 0.06f, CASH_SIDE, new fr.heraliz.loot.client.rendu.mesh.Mat[]{null, null, null, CASH, null, null}, 0);
                b.bevelBox(x0 + 1.45f, YB - 0.62f, z0 - 0.04f, x0 + 2.05f, y1 + 0.03f, z1 + 0.04f, 0.02f, BAND);
            }
        }
        b.push().translate(8, 1.1f, 8).rotateX(-90);
        b.slab(MeshBuilder.roundRect(W + 0.16f, D + 0.16f, R + 0.08f, 5), 0.3f, 0.1f, true, ALU, ALU, ALU);
        b.pop();
        b.push().translate(8, YB - 0.1f, 8).rotateX(-90);
        b.slab(MeshBuilder.roundRect(W + 0.18f, D + 0.18f, R + 0.09f, 5), 0.2f, 0.04f, true, RUBBER, RUBBER, RUBBER);
        b.pop();
        // serrures (boîtiers et molettes fixes), poignée, patins
        for (float x : SERRURE_X) {
            b.bevelBox(x - 1.1f, 0.9f, ZF - 0.1f, x + 1.1f, 2.35f, ZF + 0.35f, 0.12f, GUNMETAL);
        }
        for (float x : new float[]{6.3f, 9.7f}) b.bevelBox(x - 0.4f, 1.3f, ZF - 0.1f, x + 0.4f, 2.1f, ZF + 0.55f, 0.12f, CHROME);
        b.tube(new float[][]{p(6.3f, 1.7f, ZF + 0.45f), p(6.8f, 1.7f, ZF + 1.3f), p(9.2f, 1.7f, ZF + 1.3f), p(9.7f, 1.7f, ZF + 0.45f)},
                new float[]{0.4f, 0.46f, 0.46f, 0.4f}, 12, RUBBER, false, false, 0, false);
        for (float[] q : new float[][]{{2.2f, ZB + 1.2f}, {13.8f, ZB + 1.2f}, {2.2f, ZF - 1.2f}, {13.8f, ZF - 1.2f}}) pied(b, q[0], q[1], 0.55f, Y0);
        base = cuire(b, 0);

        // ------------------------------------------------------------ couvercle
        MeshBuilder c = new MeshBuilder();
        float hl = YL - YB;
        c.push().translate(8, YB + hl / 2, 8).rotateX(-90);
        c.slab(contour, hl, 0.35f, true, ALU, FOAM, ALU);
        c.pop();
        c.push().translate(8, YB + 0.8f, 8).rotateX(-90);
        c.slab(MeshBuilder.roundRect(W + 0.16f, D + 0.16f, R + 0.08f, 5), 0.3f, 0.1f, true, ALU, ALU, ALU);
        c.pop();
        // lecteur biométrique : cadre noir, vitre sombre
        c.bevelBox(SX0 - 0.3f, YL - 0.05f, SZ0 - 0.3f, SX1 + 0.3f, YL + 0.12f, SZ1 + 0.3f, 0.08f, DARK);
        c.bevelBox(SX0, YL + 0.12f, SZ0, SX1, YL + 0.15f, SZ1, 0.01f, LED_OFF);
        decalHaut(c, LOGO, 5.4f, YL + 0.01f, 10.5f, 4.4f, 1.1f);
        decalHaut(c, T7, 2.8f, YL + 0.01f, 5.2f, 1.6f, 0.8f);
        for (float x : SERRURE_X) c.bevelBox(x - 0.8f, YB + 0.25f, ZF - 0.05f, x + 0.8f, YB + 0.95f, ZF + 0.2f, 0.08f, CHROME);
        couvercle = cuire(c, Float.NaN, base);

        MeshBuilder s = new MeshBuilder();
        s.bevelBox(SX1 - 0.6f, YL + 0.12f, SZ0 - 0.75f, SX1 - 0.1f, YL + 0.2f, SZ0 - 0.45f, 0.03f, LED_RED);
        ledRouge = s.build();
        s = new MeshBuilder();
        s.bevelBox(SX1 - 0.6f, YL + 0.12f, SZ0 - 0.75f, SX1 - 0.1f, YL + 0.2f, SZ0 - 0.45f, 0.03f, LED_GREEN);
        ledVerte = s.build();
        s = new MeshBuilder();
        s.push().translate((SX0 + SX1) / 2, YL + 0.17f, 0).rotateX(-90);
        s.decalZ(SCAN, 0, 0, 0, SX1 - SX0, 0.5f);
        s.pop();
        laser = s.build();
        s = new MeshBuilder();
        s.bevelBox(2.2f, YB - 0.05f, ZF - 1.3f, 13.8f, YB + 0.02f, ZF - 0.9f, 0.02f, LED_WHITE);
        bandeau = s.build();
        s = new MeshBuilder();
        lavis(s, LED_WHITE, v(0, 1, 0), new float[][]{p(1.8f, YB + 0.03f, ZB + 0.7f), p(14.2f, YB + 0.03f, ZB + 0.7f), p(14.2f, YB + 0.03f, ZF - 0.7f), p(1.8f, YB + 0.03f, ZF - 0.7f)},
                0.85f, 0.85f, 0.3f, 0.3f);
        lavisMousse = s.build();

        // fermoir (sur la coque basse, bascule vers le haut et l'avant) et molettes
        MeshBuilder f = new MeshBuilder();
        f.bevelBox(-0.7f, 0, 0, 0.7f, 1.1f, 0.3f, 0.1f, CHROME);
        fermoir = f.build();
        MeshBuilder m = new MeshBuilder();
        m.push().rotateZ(-90);
        m.cylinder(0.32f, -0.22f, 0.22f, 12, DIAL.withWorld(2), GUNMETAL, GUNMETAL);
        m.pop();
        molette = m.build();
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, base, 0);
        float lum = seg(t, 26, 40);
        if (lum > 0.01f) out.additive(p, lavisMousse, 0xFFF4EA, 0.3f * lum);
        // molettes des serrures
        for (float x : SERRURE_X) {
            for (int k = 0; k < 3; k++) {
                p.push().translate(x - 0.55f + k * 0.55f, 1.55f, ZF + 0.38f).rotX(-360f * Ease.inOutSine(seg(t, 10 + k * 1.5f, 18 + k * 1.5f)) - k * 40);
                out.solid(p, molette, 0);
                p.pop();
            }
            float f = Ease.outBack(seg(t, 18, 23), 1.6f);
            p.push().translate(x, 2.2f, ZF + 0.36f).rotX(75f * f);
            out.solid(p, fermoir, 0);
            p.pop();
        }
        float a = -100f * Ease.outCubic(seg(t, 24, 44));
        p.push();
        autour(p, 0, HY, HZ, 'x', a);
        out.solid(p, couvercle, 0);
        float pulse = ferme ? 0.65f + 0.35f * (float) Math.sin(h * 0.12f) : 1f;
        out.solid(p, t >= 18 ? ledVerte : ledRouge, pulse);
        if (t > 0 && t < 18) {
            // trait laser : deux allers-retours sur le lecteur
            float u = 0.5f - 0.5f * (float) Math.cos(seg(t, 0, 18) * Math.PI * 4);
            p.push().translate(0, 0, SZ0 + 0.3f + u * (SZ1 - SZ0 - 0.6f));
            out.solid(p, laser, 1f);
            p.pop();
        }
        if (lum > 0.01f) out.solid(p, bandeau, lum);
        p.pop();
    }

    @Override
    public float[] centreObjet() {
        return new float[]{8, 3, 8};
    }

    @Override
    public float echelleObjet() {
        return 0.75f;
    }
}
