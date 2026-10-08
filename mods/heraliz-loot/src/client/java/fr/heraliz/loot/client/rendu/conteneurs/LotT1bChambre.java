package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** T1b — chambre : table de chevet, matelas, panier à linge, coffre à jouets, cabas, chaussures. */
final class LotT1bChambre {
    private LotT1bChambre() {
    }

    // =================================================================== table de chevet
    /**
     * Table de chevet en placage chêne (50 x 40 x 55 cm) : tiroir ouvert (médicaments, papiers), un bouton arraché,
     * niche à revues ; lampe de chevet renversée sur le dessus, abat-jour tombé au sol, fil qui pend ; roman et verre.
     */
    static void tableChevet(MeshBuilder b) {
        float x0 = 3.0f, x1 = 11.0f, z0 = 0.4f, z1 = 6.8f, h = 8.8f, ep = 0.35f;
        // pieds tournés
        for (float[] q : new float[][]{{3.6f, 1.0f}, {10.4f, 1.0f}, {3.6f, 6.2f}, {10.4f, 6.2f}}) {
            b.push().translate(q[0], 0, q[1]);
            b.lathe(new float[][]{{0.2f, 0}, {0.28f, 0.45f}, {0.34f, 0.92f}}, 8, 0, 360, BOIS_VERNI, 1, 1, 0, 0, 0, false);
            b.pop();
        }
        // caisson : fond, côtés, dos en aggloméré, dessus débordant
        b.bevelBox(x0 + ep, 0.9f, z0, x1 - ep, 1.28f, z1, 0.08f, BOIS_VERNI);
        b.bevelBox(x0, 0.9f, z0, x0 + ep, h - 0.4f, z1, 0.08f, BOIS_VERNI);
        b.bevelBox(x1 - ep, 0.9f, z0, x1, h - 0.4f, z1, 0.08f, BOIS_VERNI);
        b.box(x0 + ep, 1.28f, z0, x1 - ep, h - 0.4f, z0 + 0.2f, AGGLO);
        b.bevelBox(x0 - 0.15f, h - 0.42f, z0, x1 + 0.15f, h, z1 + 0.2f, 0.12f, BOIS_VERNI);
        // traverse sous le tiroir, niche (revue jaunie, boîte)
        float yt = 5.9f;
        b.bevelBox(x0 + ep, yt, z0 + 0.2f, x1 - ep, yt + 0.25f, z1, 0.06f, BOIS_VERNI);
        b.push().translate(5.8f, 1.3f, 3.4f).rotateY(8);
        b.box(-2.0f, 0, -1.5f, 2.0f, 0.16f, 1.5f, JOURNAL);
        b.box(-1.9f, 0.16f, -1.4f, 1.9f, 0.3f, 1.45f, JOURNAL);
        b.pop();
        b.color(0.82f, 0.36f, 0.3f);
        b.bevelBox(8.4f, 1.3f, 1.2f, 10.5f, 2.4f, 3.0f, 0.08f, PLAST);
        b.color(1);
        // tiroir sorti de 2,6 px : façade, caisson, fond ; contenu
        float out = 2.6f, fz = z1 + out, yb = 6.2f, yh = 8.3f;
        b.bevelBox(x0 + 0.4f, yb, fz - 0.35f, x1 - 0.4f, yh, fz, 0.1f, BOIS_VERNI);
        b.color(0.9f, 0.86f, 0.78f);
        b.box(x0 + 0.55f, yb + 0.05f, fz - 5.8f, x0 + 0.75f, yh - 0.6f, fz - 0.35f, MELAMINE);
        b.box(x1 - 0.75f, yb + 0.05f, fz - 5.8f, x1 - 0.55f, yh - 0.6f, fz - 0.35f, MELAMINE);
        b.box(x0 + 0.75f, yb + 0.05f, fz - 5.8f, x1 - 0.75f, yh - 0.6f, fz - 5.6f, MELAMINE);
        b.color(1);
        b.box(x0 + 0.75f, yb + 0.05f, fz - 5.6f, x1 - 0.75f, yb + 0.18f, fz - 0.35f, AGGLO);
        b.push().translate(5.0f, yb + 0.18f, fz - 1.6f).rotateY(-12);
        b.bevelBox(-0.8f, 0, -0.45f, 0.8f, 0.55f, 0.45f, 0.05f, AtlasCivil.WHITE, new fr.heraliz.loot.client.rendu.mesh.Mat[]{null, null, null, MEDOC, null, null}, 0);
        b.pop();
        b.push().translate(8.4f, yb + 0.2f, fz - 2.4f).rotateY(25);
        b.box(-1.3f, 0, -0.9f, 1.3f, 0.05f, 0.9f, PAPIER);
        b.pop();
        b.push().translate(7.4f, yb + 0.38f, fz - 1.2f).rotateY(70).rotateZ(90);
        b.cylinder(0.12f, -1.0f, 1.0f, 6, AtlasCivil.PLASTIC_BLUE, AtlasCivil.DARK, AtlasCivil.WHITE);
        b.pop();
        // bouton laiton à gauche, celui de droite arraché (deux trous de vis)
        bouton(b, 5.1f, 7.25f, fz, 0.32f, AtlasCivil.KNOB);
        b.color(0.25f);
        b.box(8.82f, 7.12f, fz - 0.02f, 8.98f, 7.38f, fz + 0.03f, INTERIEUR_SALE);
        b.color(1);
        decal(b, GRIFFURES, 'z', 7.6f, 7.0f, fz, 2.6f, 1.0f, -8);
        // dessus : lampe de chevet couchée (pied en céramique), roman, verre (passe translucide)
        // lampe : pied rond en laiton, colonne, douille, ampoule ; couchée sur le pied et l'ampoule
        b.push().translate(5.2f, h + 1.3f, 2.4f).rotateY(-22).rotateZ(-99.7f);
        b.lathe(new float[][]{{0.01f, 0}, {1.25f, 0}, {1.3f, 0.15f}, {1.15f, 0.45f}, {0.55f, 0.62f}, {0.3f, 0.8f}}, 16, 0, 360, AtlasCivil.BRASS, 1, 1, 0, 0, 0, false);
        b.cylinder(0.2f, 0.75f, 3.6f, 8, AtlasCivil.BRASS, null, null);
        b.cylinder(0.34f, 3.55f, 4.25f, 10, AtlasCivil.DARK, AtlasCivil.DARK, null);
        b.push().translate(0, 4.7f, 0);
        b.sphere(0.5f, 10, 6, AtlasCivil.MILK);
        b.pop();
        b.pop();
        b.tube(new float[][]{p(5.0f, h + 0.25f, 2.1f), p(7.4f, h + 0.12f, 1.3f), p(10.9f, h + 0.08f, 1.2f), p(11.4f, h - 1.5f, 1.3f), p(11.5f, 3.0f, 1.6f),
                p(11.8f, 0.12f, 2.6f), p(13.0f, 0.12f, 3.2f), p(13.4f, 0.12f, 2.0f)}, fill(8, 0.09f), 5, AtlasCivil.WHITE, false, true, 0, false);
        b.push().translate(4.6f, h, 4.9f).rotateY(18);
        livreCouche(b, -1.6f, 0.02f, -1.1f, 3.2f, 0.5f, 2.1f, 0x7A3A30);
        b.pop();
        decal(b, AUREOLE, 'y', 9.0f, h, 5.6f, 1.8f, 1.8f);
        // abat-jour tombé au sol (tronc de cône couché, intérieur visible)
        b.push().translate(13.2f, 1.81f, 6.0f).rotateY(-35).rotateZ(-108.4f);
        b.color(0.86f, 0.8f, 0.66f);
        b.lathe(new float[][]{{1.9f, 0}, {1.1f, 2.4f}}, 16, 0, 360, TISSU_USE, 1, 1, 0, 0, 0, false);
        b.color(0.6f, 0.56f, 0.46f);
        b.lathe(new float[][]{{1.86f, 0.02f}, {1.07f, 2.38f}}, 16, 0, 360, TISSU_USE, 1, 1, 0, 0, 0, true);
        b.color(1);
        b.pop();
        // pantoufle tombée au pied, boue
        chaussure(b, 4.2f, 0, 9.0f, 150, 3, 0x6A7A9A);
        boue(b, 'X', x1, 0.9f, 3.6f, 5.6f, 1.2f, 0);
        boue(b, 'z', 7.0f, 0.9f, z1, 7.6f, 0.36f, 1);
        decal(b, MOUSSE_VERTE, 'x', x0, 2.2f, 4.0f, 4.2f, 2.0f);
    }

    static void tableChevetVerre(MeshBuilder b) {
        b.push().translate(4.4f, 8.82f, 2.0f);
        b.lathe(new float[][]{{0.4f, 0}, {0.48f, 1.5f}}, 12, 0, 360, VERRE_SALE, 1, 1, 0, 0, 0, false);
        b.disc(0.4f, 0.02f, 12, VERRE_SALE, true);
        b.pop();
    }
}
