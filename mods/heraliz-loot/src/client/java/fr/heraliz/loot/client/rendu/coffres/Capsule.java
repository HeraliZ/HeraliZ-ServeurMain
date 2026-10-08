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
 * Capsule cryogénique : caisson cylindrique couché sur deux berceaux, coque graphite, embouts d'alu brossé, verrière
 * de verre givré ; dedans, un plateau bordé de lumière glacée. Fermée, la lueur bleutée palpite derrière le givre.
 * Ouverture : dépressurisation, la verrière se soulève puis bascule vers l'arrière.
 */
public final class Capsule extends DesignCoffre {
    static final float AY = 6.4f, AZ = 8, R = 4.6f, XA = 1.6f, XB = 14.4f;
    /** Charnière de la verrière (arrière). */
    static final float HY = AY, HZ = AZ - R - 0.15f;

    private Mesh corps, verriere, cadre, lumieres, lavisInterieur;

    public Capsule() {
        super("t7_capsule", 45);
    }

    @Override
    protected void build() {
        float half = (XB - XA) / 2, cx = (XA + XB) / 2;
        // ------------------------------------------------------------ socle, berceaux, coque basse, embouts
        MeshBuilder c = new MeshBuilder();
        c.bevelBox(1.4f, 0, 4.0f, 14.6f, 0.6f, 12.0f, 0.2f, DARK);
        for (float x : new float[]{3.0f, 11.0f}) {
            c.bevelBox(x, 0.6f, 4.3f, x + 2.0f, AY - 2.2f, 11.7f, 0.3f, GRAPHITE);
        }
        // coque basse : demi-cylindre (de l'avant à l'arrière en passant par le bas)
        c.push().translate(cx, AY, AZ).rotateZ(90);
        c.lathe(new float[][]{{R, -half}, {R, half}}, 32, 90, 270, GRAPHITE, 1, 1, 0, 0, 0, false);
        c.lathe(new float[][]{{R - 0.4f, -half}, {R - 0.4f, half}}, 32, 90, 270, INNER, 1, 1, 0, 0, 0, true);
        c.pop();
        // rebords de la coque (plats, à l'avant et à l'arrière)
        c.bevelBox(XA, AY - 0.25f, AZ + R - 0.45f, XB, AY + 0.1f, AZ + R + 0.05f, 0.08f, GUNMETAL);
        c.bevelBox(XA, AY - 0.25f, AZ - R - 0.05f, XB, AY + 0.1f, AZ - R + 0.45f, 0.08f, GUNMETAL);
        // plateau intérieur
        c.bevelBox(XA + 0.8f, AY - 1.6f, AZ - 3.0f, XB - 0.8f, AY - 1.1f, AZ + 3.0f, 0.15f, DARK);
        // embouts
        for (int s = -1; s <= 1; s += 2) {
            float x = s < 0 ? XA : XB;
            c.push().translate(x, AY, AZ).rotateZ(s < 0 ? 90 : -90);
            c.cylinder(R + 0.4f, 0, 1.3f, 40, ALU, ALU, ALU);
            c.translate(0, 1.3f, 0);
            c.torus(R + 0.1f, 0.25f, 40, 6, 0, 360, GUNMETAL);
            c.pop();
        }
        // panneau de commande sur l'embout droit
        c.push().translate(XB + 1.32f, AY, AZ).rotateY(90);
        c.bevelBox(-2.0f, -1.2f, 0, 2.0f, 1.2f, 0.15f, 0.08f, DARK);
        c.decalZ(CRYO, 0, 0.4f, 0.16f, 3.6f, 0.9f);
        c.pop();
        c.push().translate(XA - 1.32f, AY, AZ).rotateY(-90);
        c.decalZ(LOGO, 0, 0, 0, 4.4f, 1.1f);
        c.pop();
        corps = cuire(c, 0);

        // ------------------------------------------------------------ verrière givrée + cadre (pivote à l'arrière)
        MeshBuilder g = new MeshBuilder();
        g.push().translate(cx, AY, AZ).rotateZ(90);
        g.lathe(new float[][]{{R + 0.05f, -half}, {R + 0.05f, half}}, 32, -90, 90, FROST, 1, 1, 0, 0, 0, false);
        g.pop();
        verriere = g.build();
        MeshBuilder f = new MeshBuilder();
        f.bevelBox(XA, AY + 0.1f, AZ + R - 0.3f, XB, AY + 0.55f, AZ + R + 0.2f, 0.12f, ALU);
        f.bevelBox(XA, AY + 0.1f, AZ - R - 0.2f, XB, AY + 0.55f, AZ - R + 0.3f, 0.12f, ALU);
        for (float x : new float[]{XA + 0.1f, cx - 0.2f, XB - 0.5f}) {
            f.push().translate(x, AY, AZ).rotateZ(90);
            f.torus(R + 0.12f, 0.2f, 32, 5, -90, 90, ALU);
            f.pop();
        }
        // poignée d'ouverture sur le cadre avant
        f.tube(new float[][]{p(6.4f, AY + 0.3f, AZ + R + 0.2f), p(6.8f, AY + 0.3f, AZ + R + 0.9f), p(9.2f, AY + 0.3f, AZ + R + 0.9f), p(9.6f, AY + 0.3f, AZ + R + 0.2f)},
                new float[]{0.22f, 0.25f, 0.25f, 0.22f}, 10, CHROME, false, false, 0, false);
        cadre = cuire(f, Float.NaN, corps);

        // ------------------------------------------------------------ lumière glacée
        MeshBuilder l = new MeshBuilder();
        l.bevelBox(XA + 0.8f, AY - 1.1f, AZ + 2.7f, XB - 0.8f, AY - 0.95f, AZ + 3.0f, 0.03f, LED_ICE);
        l.bevelBox(XA + 0.8f, AY - 1.1f, AZ - 3.0f, XB - 0.8f, AY - 0.95f, AZ - 2.7f, 0.03f, LED_ICE);
        l.push().translate(XB + 1.32f, AY, AZ).rotateY(90);
        l.bevelBox(-1.6f, -0.75f, 0.15f, 1.6f, -0.55f, 0.2f, 0.02f, LED_ICE);
        l.pop();
        lumieres = l.build();
        MeshBuilder lv = new MeshBuilder();
        lavis(lv, LED_ICE, v(0, 1, 0), new float[][]{p(XA + 0.8f, AY - 1.07f, AZ - 3.0f), p(XB - 0.8f, AY - 1.07f, AZ - 3.0f), p(XB - 0.8f, AY - 1.07f, AZ + 3.0f), p(XA + 0.8f, AY - 1.07f, AZ + 3.0f)},
                0.7f, 0.7f, 0.7f, 0.7f);
        lavisInterieur = lv.build();
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, corps, 0);
        float lueur = ferme ? 0.55f + 0.25f * (float) Math.sin(h * 0.09f) : 0.6f + 0.4f * seg(t, 0, 12);
        out.solid(p, lumieres, lueur);
        out.additive(p, lavisInterieur, 0xBFEFFF, 0.35f * lueur);
        float leve = 0.6f * Ease.outQuad(seg(t, 6, 10)) * (1 - seg(t, 10, 18));
        float a = -108f * Ease.inOutCubic(seg(t, 10, 40));
        p.push().translate(0, leve, 0);
        autour(p, 0, HY, HZ, 'x', a);
        out.solid(p, cadre, 0);
        out.translucent(p, verriere, 0xFFFFFF, 1f);
        p.pop();
    }

    @Override
    public float[] centreObjet() {
        return new float[]{8, 6, 8};
    }

    @Override
    public float echelleObjet() {
        return 0.62f;
    }
}
