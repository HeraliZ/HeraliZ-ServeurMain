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
 * Coffre-fort : caisson graphite mat aux arêtes adoucies, porte épaisse encastrée sur gonds à droite, volant chromé à
 * trois branches, pavé numérique et écran, bandeau lumineux de statut. Ouverture : l'écran passe de LOCKED à OPEN, le
 * voyant vire au vert, le volant tourne, les pênes rentrent, la porte pivote lourdement ; l'intérieur s'éclaire.
 */
public final class CoffreFort extends DesignCoffre {
    // caisson
    static final float X0 = 1, X1 = 15, Y0 = 0.8f, Y1 = 15.6f, Z0 = 1.5f, Z1 = 14.4f;
    // ouverture (cavité) et porte
    static final float OX0 = 2.7f, OX1 = 13.3f, OY0 = 2.5f, OY1 = 13.9f, CZ = 3.0f;
    static final float DZ0 = 12.4f, DZ1 = 14.9f;
    /** Axe des gonds (côté droit). */
    static final float HX = 13.55f, HZ = 14.75f;
    /** Centre du volant. */
    static final float WX = 7.2f, WY = 8.2f;

    private Mesh caisson, porte, volant, pene, ledRouge, ledVerte, ecranLock, ecranOpen, lumiere, lavisInterieur;

    public CoffreFort() {
        super("t7_coffre_fort", 46);
    }

    @Override
    protected void build() {
        // ------------------------------------------------------------ caisson (fixe)
        MeshBuilder c = new MeshBuilder();
        c.bevelBox(X0, Y0, Z0, X1, Y1, Z1, 0.7f, GRAPHITE, null, MeshBuilder.SKIP_PZ);
        // façade : cadre autour de l'ouverture (4 quadrilatères), puis la cavité (parois vers l'intérieur)
        float fz = Z1;
        float bx0 = X0 + 0.7f, bx1 = X1 - 0.7f, by0 = Y0 + 0.7f, by1 = Y1 - 0.7f;
        c.face(GRAPHITE, v(0, 0, 1), p(bx0, by0, fz), p(bx1, by0, fz), p(OX1, OY0, fz), p(OX0, OY0, fz));
        c.face(GRAPHITE, v(0, 0, 1), p(OX0, OY1, fz), p(OX1, OY1, fz), p(bx1, by1, fz), p(bx0, by1, fz));
        c.face(GRAPHITE, v(0, 0, 1), p(bx0, by0, fz), p(OX0, OY0, fz), p(OX0, OY1, fz), p(bx0, by1, fz));
        c.face(GRAPHITE, v(0, 0, 1), p(OX1, OY0, fz), p(bx1, by0, fz), p(bx1, by1, fz), p(OX1, OY1, fz));
        c.face(INNER, v(1, 0, 0), p(OX0, OY0, CZ), p(OX0, OY0, fz), p(OX0, OY1, fz), p(OX0, OY1, CZ));
        c.face(INNER, v(-1, 0, 0), p(OX1, OY0, CZ), p(OX1, OY1, CZ), p(OX1, OY1, fz), p(OX1, OY0, fz));
        c.face(INNER, v(0, 1, 0), p(OX0, OY0, CZ), p(OX1, OY0, CZ), p(OX1, OY0, fz), p(OX0, OY0, fz));
        c.face(INNER, v(0, -1, 0), p(OX0, OY1, CZ), p(OX0, OY1, fz), p(OX1, OY1, fz), p(OX1, OY1, CZ));
        c.face(INNER, v(0, 0, 1), p(OX0, OY0, CZ), p(OX1, OY0, CZ), p(OX1, OY1, CZ), p(OX0, OY1, CZ));
        // liseré d'alu brossé autour de l'ouverture
        float e = 0.32f, tz = fz + 0.06f;
        float j = 0.015f;
        c.bevelBox(OX0 - e, OY0 - e, fz - 0.2f, OX1 + e, OY0 - j, tz, 0.05f, ALU);
        c.bevelBox(OX0 - e, OY1 + j, fz - 0.2f, OX1 + e, OY1 + e, tz, 0.05f, ALU);
        c.bevelBox(OX0 - e, OY0 - j, fz - 0.2f, OX0 - j, OY1 + j, tz, 0.05f, ALU);
        c.bevelBox(OX1 + j, OY0 - j, fz - 0.2f, OX1 + e, OY1 + j, tz, 0.05f, ALU);
        // étagère, gâches des pênes, logo au fond
        c.bevelBox(OX0, 7.6f, CZ, OX1, 8.0f, 11.6f, 0.12f, GUNMETAL);
        for (float y : new float[]{4.4f, 8.2f, 12.0f}) {
            c.bevelBox(OX0 - 0.05f, y - 0.75f, 12.8f, OX0 + 0.25f, y + 0.75f, 14.0f, 0.08f, DARK);
        }
        decal(c, LOGO, 8, 10.9f, CZ + 0.02f, 5.2f, 1.3f);
        // gonds : demi-charnières fixes
        for (float y : new float[]{3.0f, 11.6f}) {
            c.push().translate(HX, y, HZ);
            c.cylinder(0.45f, 0, 1.4f, 14, GUNMETAL, GUNMETAL, GUNMETAL);
            c.pop();
        }
        // pieds, plaque de marquage sur le flanc, vis
        for (float[] q : new float[][]{{2.6f, 3.0f}, {13.4f, 3.0f}, {2.6f, 13.0f}, {13.4f, 13.0f}}) pied(c, q[0], q[1], 0.9f, 0.85f);
        c.push().translate(X1, 13.2f, 8).rotateY(90);
        c.decalZ(T7, 0, 0, 0.02f, 2.6f, 1.3f);
        c.pop();
        caisson = cuire(c, 0);

        // ------------------------------------------------------------ porte (pivote sur les gonds)
        MeshBuilder d = new MeshBuilder();
        d.bevelBox(OX0 + 0.12f, OY0 + 0.12f, DZ0, OX1 - 0.12f, OY1 - 0.12f, DZ1, 0.45f, GRAPHITE);
        // panneau intérieur (dos de la porte) en acier, tringlerie
        d.bevelBox(OX0 + 1.0f, OY0 + 1.0f, DZ0 - 0.4f, OX1 - 1.0f, OY1 - 1.0f, DZ0, 0.15f, GUNMETAL);
        for (float y : new float[]{4.4f, 8.2f, 12.0f}) d.bevelBox(OX0 + 0.8f, y - 0.2f, DZ0 - 0.7f, WX, y + 0.2f, DZ0 - 0.4f, 0.06f, CHROME);
        // face avant : léger cadre en creux (filet sombre), pavé, écran, logo
        float fz2 = DZ1;
        d.bevelBox(OX0 + 0.9f, OY0 + 0.9f, fz2, OX1 - 0.9f, OY0 + 1.05f, fz2 + 0.06f, 0.02f, DARK);
        d.bevelBox(OX0 + 0.9f, OY1 - 1.05f, fz2, OX1 - 0.9f, OY1 - 0.9f, fz2 + 0.06f, 0.02f, DARK);
        d.bevelBox(9.9f, 8.2f, fz2, 12.8f, 13.1f, fz2 + 0.18f, 0.08f, ALU);
        d.bevelBox(10.1f, 8.4f, fz2, 12.6f, 12.9f, fz2 + 0.3f, 0.14f, DARK);
        decal(d, KEYPAD, 11.35f, 9.9f, fz2 + 0.31f, 2.0f, 2.3f);
        decal(d, LOGO, WX, 3.9f, fz2 + 0.01f, 4.6f, 1.15f);
        // moyeu du volant (fixe sur la porte)
        d.push().translate(WX, WY, fz2).rotateX(90);
        d.cylinder(1.55f, 0, 0.35f, 28, GUNMETAL, null, GUNMETAL);
        d.pop();
        for (float y : new float[]{6.9f, 10.0f}) {
            d.push().translate(HX, y, HZ);
            d.cylinder(0.45f, -1.2f, 1.2f, 14, GUNMETAL, GUNMETAL, GUNMETAL);
            d.pop();
            d.bevelBox(OX1 - 1.2f, y - 1.0f, DZ1 - 0.6f, HX, y + 1.0f, DZ1 - 0.1f, 0.1f, GUNMETAL);
        }
        porte = cuire(d, Float.NaN, caisson);

        MeshBuilder s = new MeshBuilder();
        s.bevelBox(10.4f, 11.7f, fz2 + 0.31f, 12.3f, 12.6f, fz2 + 0.33f, 0.01f, SCREEN_LOCK.asFit());
        ecranLock = s.build();
        s = new MeshBuilder();
        s.bevelBox(10.4f, 11.7f, fz2 + 0.31f, 12.3f, 12.6f, fz2 + 0.33f, 0.01f, SCREEN_OPEN.asFit());
        ecranOpen = s.build();
        s = new MeshBuilder();
        s.bevelBox(3.45f, 4.6f, fz2, 3.8f, 12.2f, fz2 + 0.12f, 0.04f, LED_RED);
        ledRouge = s.build();
        s = new MeshBuilder();
        s.bevelBox(3.45f, 4.6f, fz2, 3.8f, 12.2f, fz2 + 0.12f, 0.04f, LED_GREEN);
        ledVerte = s.build();

        // ------------------------------------------------------------ volant à trois branches
        MeshBuilder w = new MeshBuilder();
        w.push().translate(WX, WY, fz2 + 0.35f).rotateX(90);
        w.cylinder(0.85f, 0, 0.75f, 24, CHROME, null, GUNMETAL);
        w.pop();
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(90 + k * 120);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            float z = fz2 + 0.75f;
            w.tube(new float[][]{p(WX + cs * 0.6f, WY + sn * 0.6f, z), p(WX + cs * 2.6f, WY + sn * 2.6f, z + 0.2f)},
                    new float[]{0.26f, 0.22f}, 10, CHROME, false, false, 0, false);
            // poignée caoutchouc au bout de chaque branche, embout chromé
            w.push().translate(WX + cs * 2.6f, WY + sn * 2.6f, z + 0.2f).rotateX(90);
            w.cylinder(0.34f, 0, 1.1f, 12, RUBBER, null, null);
            w.translate(0, 1.1f, 0);
            w.cylinder(0.36f, 0, 0.18f, 12, CHROME, null, CHROME);
            w.pop();
        }
        volant = w.build();

        // ------------------------------------------------------------ un pêne (sorti), le long de -X
        MeshBuilder pb = new MeshBuilder();
        pb.push().translate(OX0 + 0.4f, 0, 13.4f).rotateZ(90);
        pb.cylinder(0.42f, 0, 1.2f, 12, CHROME, CHROME, CHROME);
        pb.pop();
        pene = pb.build();

        // ------------------------------------------------------------ bandeau lumineux intérieur
        MeshBuilder l = new MeshBuilder();
        l.bevelBox(OX0 + 0.4f, OY1 - 0.35f, CZ + 1, OX1 - 0.4f, OY1 - 0.05f, 12.2f, 0.05f, LED_WHITE);
        l.bevelBox(OX0 + 0.4f, 7.25f, 10.9f, OX1 - 0.4f, 7.6f, 11.3f, 0.05f, LED_WHITE);
        lumiere = l.build();
        // lavis de la lampe sur les parois (plus clair en haut et au fond, plus sombre en bas et devant)
        MeshBuilder lv = new MeshBuilder();
        float zf = 13.6f;
        lavis(lv, LED_WHITE, v(0, 0, 1), new float[][]{p(OX0, OY0, CZ + 0.03f), p(OX1, OY0, CZ + 0.03f), p(OX1, OY1, CZ + 0.03f), p(OX0, OY1, CZ + 0.03f)}, 0.35f, 0.35f, 0.9f, 0.9f);
        lavis(lv, LED_WHITE, v(1, 0, 0), new float[][]{p(OX0 + 0.03f, OY0, CZ), p(OX0 + 0.03f, OY0, zf), p(OX0 + 0.03f, OY1, zf), p(OX0 + 0.03f, OY1, CZ)}, 0.35f, 0.15f, 0.55f, 0.9f);
        lavis(lv, LED_WHITE, v(-1, 0, 0), new float[][]{p(OX1 - 0.03f, OY0, CZ), p(OX1 - 0.03f, OY1, CZ), p(OX1 - 0.03f, OY1, zf), p(OX1 - 0.03f, OY0, zf)}, 0.35f, 0.9f, 0.55f, 0.15f);
        lavis(lv, LED_WHITE, v(0, 1, 0), new float[][]{p(OX0, OY0 + 0.03f, CZ), p(OX1, OY0 + 0.03f, CZ), p(OX1, OY0 + 0.03f, zf), p(OX0, OY0 + 0.03f, zf)}, 0.45f, 0.45f, 0.2f, 0.2f);
        lavis(lv, LED_WHITE, v(0, 1, 0), new float[][]{p(OX0, 8.03f, CZ), p(OX1, 8.03f, CZ), p(OX1, 8.03f, 11.6f), p(OX0, 8.03f, 11.6f)}, 0.8f, 0.8f, 0.5f, 0.5f);
        lavisInterieur = lv.build();
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        boolean deverrouille = t >= 5;
        out.solid(p, caisson, 0);
        float lum = seg(t, 18, 32);
        if (lum > 0.01f) {
            out.solid(p, lumiere, lum);
            out.additive(p, lavisInterieur, 0xFFF2E6, 0.32f * lum);
        }
        float angle = 104f * Ease.outCubic(seg(t, 20, 44));
        p.push();
        autour(p, HX, 0, HZ, 'y', angle);
        out.solid(p, porte, 0);
        float pulse = ferme ? 0.75f + 0.25f * (float) Math.sin(h * 0.12f) : 1f;
        out.solid(p, deverrouille ? ledVerte : ledRouge, pulse);
        out.solid(p, deverrouille ? ecranOpen : ecranLock, ferme ? pulse : 1f);
        p.push();
        autour(p, WX, WY, 0, 'z', -540f * Ease.inOutSine(seg(t, 4, 18)));
        out.solid(p, volant, 0);
        p.pop();
        float rentre = 1.3f * Ease.inOutSine(seg(t, 14, 20));
        for (float y : new float[]{4.4f, 8.2f, 12.0f}) {
            p.push().translate(rentre, y, 0);
            out.solid(p, pene, 0);
            p.pop();
        }
        p.pop();
    }
}
