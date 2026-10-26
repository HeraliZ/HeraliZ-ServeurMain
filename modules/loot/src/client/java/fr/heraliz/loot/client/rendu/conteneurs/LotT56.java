package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Atlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT56Kit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T56 — T5 : forces de l'ONU et opérations lourdes (bleu ONU passé, blanc sale, acier, lettrage UN) ; T6 : haute
 * sécurité (rouge sécurité, acier noir, inox, claviers, écrans éteints, voyants faibles), laboratoires, banques,
 * transport de fonds. Échelle réelle (1 px = 6,25 cm, joueur 1,8 m), dos au mur (z = 0), objets larges vers +X.
 * Tout est abandonné depuis des mois : poussière, rayures, rouille, objets forcés, renversés, déchirés.
 */
public final class LotT56 {
    private LotT56() {
    }

    public static void enregistrer() {
        for (int[] r : LotT56Atlas.LUMINEUX) AtlasConteneurs.lumineux(AtlasConteneurs.T56, r);
        Conteneurs.ajouter("t5_aide_humanitaire", "t5", "carton", "Palette d'aide humanitaire", "Humanitarian aid pallet", LotT56::aideHumanitaire);
        Conteneurs.verre("t5_aide_humanitaire", LotT56::aideHumanitaireFilm);
        Conteneurs.ajouter("t5_caisse_medicale_onu", "t5", "metal", "Caisse médicale ONU", "UN medical crate", LotT56::caisseMedicale);
        Conteneurs.ajouter("t5_cantine_onu", "t5", "metal", "Cantine ONU", "UN footlocker", LotT56::cantineOnu);
        Conteneurs.ajouter("t5_armoire_armes", "t5", "metal", "Armoire forte à armes", "Steel weapon locker", LotT56::armoireArmes);
        LotT56b.enregistrer();
    }

    static final Mat RUBAN_BLANC = PAPIER;

    // =================================================================== T5
    static final float PX0 = 6.4f, PX1 = 25.6f, PZ0 = 0.6f, PZ1 = 13.4f;

    /** Palette Europe (120 x 80 x 14,4 cm) en bois grisé, planche avant éclatée, un dé manquant. */
    static void palette(MeshBuilder b, float x0, float x1, float z0, float z1, boolean abimee) {
        float xc = (x0 + x1) / 2, zc = (z0 + z1) / 2;
        float[][] zs = {{z0, z0 + 1.6f}, {zc - 0.8f, zc + 0.8f}, {z1 - 1.6f, z1}};
        float[][] xs = {{x0, x0 + 2.3f}, {xc - 1.15f, xc + 1.15f}, {x1 - 2.3f, x1}};
        for (float[] z : zs) b.box(x0 + 0.05f, 0, z[0] + 0.05f, x1 - 0.05f, 0.35f, z[1] - 0.05f, BOIS_GRIS);
        for (float[] x : xs) {
            for (int k = 0; k < 3; k++) {
                if (abimee && x == xs[1] && k == 2) continue;
                b.box(x[0] + 0.06f, 0.35f, zs[k][0] + 0.06f, x[1] - 0.06f, 1.6f, zs[k][1] - 0.06f, BOIS_GRIS);
            }
            b.box(x[0], 1.6f, z0 + 0.02f, x[1], 1.95f, z1 - 0.02f, BOIS_GRIS);
        }
        float g = 0.675f;
        float[] lw = {2.3f, 1.6f, 2.3f, 1.6f, 2.3f};
        float z = z0;
        for (int k = 0; k < 5; k++) {
            float xe = abimee && k == 4 ? x1 - 1.4f : x1;
            b.bevelBox(x0, 1.95f, z, xe, 2.3f, z + lw[k], 0.08f, BOIS_GRIS);
            if (abimee && k == 4) {
                // bout éclaté qui pend
                b.push().translate(xe, 2.1f, z + lw[k] / 2).rotateZ(-24);
                b.bevelBox(0.05f, -0.16f, -lw[k] / 2 + 0.15f, 1.3f, 0.16f, lw[k] / 2 - 0.35f, 0.06f, BOIS_GRIS);
                b.pop();
            }
            z += lw[k] + g;
        }
    }

    /**
     * Palette d'aide humanitaire (deux blocs) : cartons blancs marqués UN sur trois rangs, en partie déchargés ; film
     * déchiré à l'avant (passe translucide), carton du haut éventré (rations), carton affaissé, un carton tombé à droite,
     * feuilles mortes et papiers.
     */
    static void aideHumanitaire(MeshBuilder b) {
        palette(b, PX0, PX1, PZ0, PZ1, true);
        Random r = new Random(56);
        float y1 = 2.3f, h = 4.6f, w = 6.2f, d = 6.15f;
        float[] xs = {PX0 + 0.1f, PX0 + 6.5f, PX0 + 12.9f};
        float[] zs = {PZ0 + 0.15f, PZ0 + 6.45f};
        // rang 1 : complet
        for (int k = 0; k < 6; k++) {
            float x = xs[k % 3], z = zs[k / 3];
            b.push().translate(x + w / 2, 0, z + d / 2).rotateY(r.nextFloat() * 3 - 1.5f).translate(-x - w / 2, 0, -z - d / 2);
            cartonAide(b, x, y1 + 0.02f, z, w, h, d, k >= 3 ? (k == 5 ? 2 : 1) : 0, SCOTCH_BRUN);
            b.pop();
        }
        decal(b, AUREOLE, 'z', xs[1] + 4.6f, y1 + 1.6f, zs[1] + d + 0.02f, 2.6f, 2.6f, 20);
        // rang 2 : arrière complet (le droit affaissé), avant gauche seul
        float y2 = y1 + h + 0.04f;
        for (int k = 0; k < 2; k++) {
            float x = xs[k];
            b.push().translate(x + w / 2, 0, zs[0] + d / 2).rotateY(r.nextFloat() * 4 - 2).translate(-x - w / 2, 0, -zs[0] - d / 2);
            cartonAide(b, x, y2, zs[0], w, h, d, 0, SCOTCH_BRUN);
            b.pop();
        }
        float xa = xs[2];
        boiteAffaissee(b, CARTON_BLANC, xa, y2, zs[0], xa + w, y2 + h, zs[0] + d, 0.2f, 0.9f, 1.5f, 0.6f);
        b.box(xa + w / 2 - 0.5f, y2 + h - 1.6f, zs[0] + d + 0.005f, xa + w / 2 + 0.5f, y2 + h - 0.9f, zs[0] + d + 0.065f, SCOTCH_BRUN);
        Kit.pochoir(b, S_UN_NOIR, 'z', xa + w * 0.3f, y2 + h * 0.4f, zs[0] + d + 0.01f, 3.0f, 1.5f);
        decal(b, ETIQUETTE_DECHIREE, 'z', xa + w * 0.8f, y2 + 1.3f, zs[0] + d + 0.01f, 2.0f, 1.0f, -6);
        b.push().translate(xs[0] + w / 2, 0, zs[1] + d / 2).rotateY(-4).translate(-xs[0] - w / 2, 0, -zs[1] - d / 2);
        cartonAide(b, xs[0], y2, zs[1], w, h, d, 1, SCOTCH_BRUN);
        b.pop();
        // rang 3 : un carton fermé, un carton éventré (rations)
        float y3 = y2 + h + 0.04f;
        b.push().translate(xs[0] + w / 2, 0, zs[0] + d / 2).rotateY(5).translate(-xs[0] - w / 2, 0, -zs[0] - d / 2);
        cartonAide(b, xs[0] + 0.2f, y3, zs[0] + 0.2f, w - 0.6f, h - 0.4f, d - 0.6f, 1, SCOTCH_BRUN);
        b.pop();
        cartonEventre(b, xs[1] + 0.4f, y3, zs[0] + 0.25f, w - 0.8f, h - 0.6f, d - 0.7f);
        // carton tombé sur le flanc à droite, rations répandues
        b.push().translate(28.6f, 0, 9.0f).rotateY(-18);
        boiteAffaissee(b, CARTON_BLANC, -2.4f, 0, -3.0f, 2.4f, 4.4f, 3.0f, 0.1f, 0.5f, 0.9f, 0.3f);
        b.box(-0.5f, 4.38f - 0.7f, 3.0f - 0.02f + 0.06f, 0.5f, 4.38f - 0.35f, 3.06f + 0.06f, SCOTCH_BRUN);
        Kit.pochoir(b, S_AIDE, 'z', 0, 1.3f, 3.0f, 4.0f, 1.0f);
        Kit.pochoir(b, S_UN_NOIR, 'z', -0.6f, 2.7f, 3.0f, 2.4f, 1.2f);
        b.pop();
        ration(b, 10.0f, 0, 14.7f, 5, 0);
        ration(b, 14.4f, 0, 14.8f, -8, 1);
        ration(b, 11.0f, 0.36f, 14.4f, 12, 2);
        // feuilles mortes sur le rang 1 dégagé et au sol, papiers
        b.push().translate(0, y2 - 0.02f, 0);
        jonchee(b, xs[1] + 0.4f, zs[1] + 0.6f, xs[2] + w - 0.4f, zs[1] + d - 0.4f, 9, FEUILLES, 1.1f, 561);
        b.pop();
        jonchee(b, 2.0f, 13.6f, 31.0f, 15.8f, 7, FEUILLES, 1.0f, 562);
        jonchee(b, 1.5f, 12.0f, 6.0f, 15.5f, 3, PAPIER, 1.4f, 563);
    }

    /** Sachet de ration (25 x 15 cm) posé à plat. */
    static void ration(MeshBuilder b, float x, float y, float z, float ang, int v) {
        b.push().translate(x, y, z).rotateY(ang);
        if (v == 1) b.color(0.62f, 0.5f, 0.36f);
        else b.color(0.56f, 0.52f, 0.38f);
        b.bevelBox(-1.9f, 0, -1.1f, 1.9f, 0.34f, 1.1f, 0.12f, PLASTIQUE_GRAIN);
        b.color(1);
        b.box(-1.98f, 0.02f, -1.18f, -1.5f, 0.24f, 1.18f, PLASTIQUE_GRAIN);
        b.pop();
    }

    /** Carton éventré : rabats ouverts qui retombent, rations à l'intérieur, une qui dépasse. */
    static void cartonEventre(MeshBuilder b, float x0, float y0, float z0, float w, float h, float d) {
        float x1 = x0 + w, y1 = y0 + h, z1 = z0 + d, e = 0.14f;
        b.bevelBox(x0 + 0.06f, y0 + 0.04f, z0 + 0.06f, x1 - 0.06f, y0 + 0.3f, z1 - 0.06f, 0.08f, CARTON_BLANC);
        b.bevelBox(x0, y0, z0, x0 + e, y1, z1, 0.05f, CARTON_BLANC);
        b.bevelBox(x1 - e, y0, z0, x1, y1, z1, 0.05f, CARTON_BLANC);
        b.bevelBox(x0 + e, y0, z0, x1 - e, y1, z0 + e, 0.05f, CARTON_BLANC);
        b.bevelBox(x0 + e, y0, z1 - e, x1 - e, y1, z1, 0.05f, CARTON_BLANC);
        Kit.pochoir(b, S_UN_NOIR, 'z', x0 + w * 0.32f, y0 + h * 0.62f, z1, 2.6f, 1.3f);
        Kit.pochoir(b, S_AIDE, 'z', x0 + w / 2, y0 + 0.9f, z1, 4.6f, 1.15f);
        // rabats : avant et arrière (longs), côtés (courts)
        b.push().translate(0, y1, z1).rotateX(118).translate(0, -y1, -z1);
        b.box(x0 + 0.1f, y1, z1 - 0.12f, x1 - 0.1f, y1 + d * 0.48f, z1, CARTON_BLANC);
        b.pop();
        // rabat arrière arraché : moignon déchiré
        b.push().translate(0, y1, z0).rotateX(-12).translate(0, -y1, -z0);
        b.box(x0 + 0.6f, y1 - 0.05f, z0 + 0.02f, x1 - 1.8f, y1 + 0.55f, z0 + 0.14f, CARTON_BLANC);
        b.pop();
        b.push().translate(x0, y1, 0).rotateZ(125).translate(-x0, -y1, 0);
        b.box(x0, y1, z0 + 0.1f, x0 + 0.12f, y1 + w * 0.42f, z1 - 0.1f, CARTON_BLANC);
        b.pop();
        b.push().translate(x1, y1, 0).rotateZ(-150).translate(-x1, -y1, 0);
        b.box(x1 - 0.12f, y1, z0 + 0.1f, x1, y1 + w * 0.42f, z1 - 0.1f, CARTON_BLANC);
        b.pop();
        // rations empilées de chant, une penchée qui dépasse
        b.color(0.56f, 0.52f, 0.38f);
        for (int k = 0; k < 5; k++) {
            float x = x0 + 0.5f + k * (w - 1.0f) / 5;
            b.push().translate(x + 0.3f, y0 + 0.3f, (z0 + z1) / 2).rotateZ(k % 2 == 0 ? 6 : -4);
            b.bevelBox(-0.3f, 0, -1.9f, 0.3f, h - 0.9f, 1.9f, 0.1f, PLASTIQUE_GRAIN);
            b.pop();
        }
        b.color(0.62f, 0.5f, 0.36f);
        b.push().translate(x0 + w * 0.62f, y1 - 1.7f, (z0 + z1) / 2 + 1.2f).rotateX(40).rotateZ(12);
        b.bevelBox(-1.1f, 0, -0.17f, 1.1f, 3.0f, 0.17f, 0.1f, PLASTIQUE_GRAIN);
        b.pop();
        b.color(1);
    }

    /** Film étirable autour du premier rang (passe translucide) : avant déchiré, flancs, lambeau qui pend jusqu'au sol. */
    static void aideHumanitaireFilm(MeshBuilder b) {
        float y0 = 1.9f, y1 = 7.9f, zf = PZ1 + 0.14f;
        b.face(FILM_DECHIRE, MeshBuilder.v(0, 0, 1), p(PX0 - 0.12f, y0, zf), p(PX1 + 0.12f, y0, zf), p(PX1 + 0.12f, y1, zf), p(PX0 - 0.12f, y1, zf));
        b.face(FILM, MeshBuilder.v(-1, 0, 0), p(PX0 - 0.12f, y0, PZ0 + 0.1f), p(PX0 - 0.12f, y0, zf), p(PX0 - 0.12f, y1, zf), p(PX0 - 0.12f, y1, PZ0 + 0.1f));
        b.face(FILM, MeshBuilder.v(1, 0, 0), p(PX1 + 0.12f, y0, zf), p(PX1 + 0.12f, y0, PZ0 + 0.1f), p(PX1 + 0.12f, y1 - 1.2f, PZ0 + 0.1f), p(PX1 + 0.12f, y1 - 1.2f, zf));
        float[][] ch = lisse(new float[][]{p(PX1 + 0.14f, 6.6f, zf - 1.0f), p(PX1 + 0.5f, 4.2f, zf + 0.2f), p(PX1 + 1.2f, 1.2f, zf + 0.6f), p(PX1 + 2.6f, 0.06f, zf + 1.4f), p(PX1 + 4.2f, 0.05f, zf + 1.5f)}, 3);
        float[] lw = new float[ch.length];
        for (int i = 0; i < lw.length; i++) lw[i] = 2.4f - i * 0.1f;
        b.ribbon(ch, lw, new float[]{0, 0, 1}, FILM);
        b.ribbon(ch, lw, new float[]{0, 0, -1}, FILM);
    }

    /**
     * Caisse médicale ONU (70 x 45 x 40 cm) : corps blanc, couvercle bleu ouvert appuyé contre le mur, bandes et
     * médicaments dans le bac, une bande déroulée jusqu'au sol, une boîte tombée, un fermoir arraché.
     */
    static void caisseMedicale(MeshBuilder b) {
        float x0 = 2.4f, x1 = 13.6f, z0 = 2.6f, z1 = 9.8f, yb = 0.35f, yc = 5.3f, e = 0.32f;
        for (float x : new float[]{3.6f, 12.4f}) b.bevelBox(x - 0.6f, 0, z0 + 0.4f, x + 0.6f, yb + 0.03f, z1 - 0.4f, 0.1f, CAOUTCHOUC_USE);
        b.bevelBox(x0 + 0.06f, yb + 0.04f, z0 + 0.06f, x1 - 0.06f, yb + 0.6f, z1 - 0.06f, 0.2f, BLANC_ONU);
        b.bevelBox(x0, yb, z0, x0 + e, yc, z1, 0.14f, BLANC_ONU);
        b.bevelBox(x1 - e, yb, z0, x1, yc, z1, 0.14f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, yb, z0, x1 - e + 0.02f, yc, z0 + e, 0.14f, BLANC_ONU);
        b.bevelBox(x0 + e - 0.02f, yb, z1 - e, x1 - e + 0.02f, yc, z1, 0.14f, BLANC_ONU);
        // cornières bleues, bande basse
        for (float x : new float[]{x0 - 0.08f, x1 - 0.82f}) b.bevelBox(x, yb - 0.02f, z1 - 0.82f, x + 0.9f, yc + 0.06f, z1 + 0.08f, 0.18f, BLEU_ONU);
        b.bevelBox(x0 + 0.84f, 0.95f, z1 - 0.1f, x1 - 0.84f, 1.55f, z1 + 0.12f, 0.05f, BLEU_ONU);
        // fond de bac clair, cloison, contenu
        b.color(0.86f, 0.86f, 0.83f);
        b.box(x0 + e, yb + 0.6f, z0 + e, x1 - e, 2.6f, z1 - e, PLASTIQUE_USE);
        b.bevelBox(8.0f, 2.58f, z0 + e, 8.25f, 4.4f, z1 - e, 0.05f, PLASTIQUE_USE);
        b.color(1);
        b.color(0.92f, 0.9f, 0.84f);
        for (int k = 0; k < 4; k++) {
            float zz = z0 + 1.2f + k * 1.3f, xx = 3.9f + (k % 2) * 0.5f;
            b.push().translate(xx, 3.18f, zz).rotateZ(90).rotateY(k * 40);
            b.cylinder(0.58f, -0.95f, 0.95f, 10, TISSU_USE, TISSU_USE, TISSU_USE);
            b.pop();
        }
        b.color(1);
        float[][] bt = {{9.6f, 4.2f, 0, 2.2f}, {11.6f, 4.0f, 12, 1.6f}, {10.6f, 6.6f, -8, 2.2f}};
        for (float[] q : bt) {
            b.push().translate(q[0], 2.6f, q[1]).rotateY(q[2]);
            b.bevelBox(-0.9f, 0, -1.1f, 0.9f, q[3], 1.1f, 0.08f, CARTON_BLANC);
            b.push().translate(0, q[3], 0).rotateX(-90);
            b.decalZ(AtlasLoot.LABEL, 0, 0, 0.03f, 1.6f, 0.8f);
            b.pop();
            b.pop();
        }
        b.color(0.8f, 0.86f, 0.9f);
        b.push().translate(5.8f, 2.95f, 7.6f).rotateY(20).scale(1.5f, 0.32f, 1.0f);
        b.sphere(1.3f, 12, 6, PLASTIQUE_USE);
        b.pop();
        b.color(1);
        // couvercle bleu ouvert (100°) appuyé contre le mur, panneau intérieur et notice
        b.push().translate(0, yc, z0).rotateX(-100).translate(0, -yc, -z0);
        b.bevelBox(x0 - 0.06f, yc + 0.02f, z0 - 0.06f, x1 + 0.06f, yc + 1.1f, z1 + 0.06f, 0.3f, BLEU_ONU);
        b.color(0.6f, 0.64f, 0.7f);
        b.box(x0 + 0.5f, yc - 0.1f, z0 + 0.5f, x1 - 0.5f, yc + 0.04f, z1 - 0.5f, TISSU_USE);
        b.color(1);
        b.push().translate(5.6f, yc - 0.11f, 5.8f).rotateX(90).rotateZ(4);
        b.decalZ(PAPIER, 0, 0, 0.02f, 3.4f, 4.2f);
        b.pop();
        b.box(x0 + 0.5f, yc - 0.25f, 4.6f, x1 - 0.5f, yc - 0.1f, 5.2f, SCOTCH);
        b.pop();
        // fermoirs : celui de gauche pend ouvert, celui de droite arraché (deux vis)
        b.bevelBox(3.6f, yc - 1.4f, z1 - 0.02f, 5.0f, yc - 0.2f, z1 + 0.2f, 0.08f, AtlasLoot.DARK);
        b.push().translate(4.3f, yc - 1.2f, z1 + 0.22f).rotateX(-14);
        b.bevelBox(-0.5f, -2.0f, 0, 0.5f, 0.2f, 0.22f, 0.08f, METAL_NU);
        b.pop();
        boulon(b, 11.6f, yc - 0.6f, z1, 0.14f);
        boulon(b, 12.4f, yc - 0.6f, z1, 0.14f);
        Kit.poigneeU(b, x1 + 0.08f, 3.6f, (z0 + z1) / 2, 2.8f, 1, METAL_NU);
        Kit.poigneeU(b, x0 - 0.08f, 3.6f, (z0 + z1) / 2, 2.8f, -1, METAL_NU);
        // marquages et usure
        decal(b, CROIX_MED, 'z', 8.0f, 3.05f, z1, 2.0f, 2.0f);
        Kit.pochoir(b, S_UN_NOIR, 'z', 5.6f, 2.6f, z1, 2.6f, 1.3f);
        Kit.pochoir(b, S_MEDICAL, 'z', 10.8f, 2.6f, z1, 3.4f, 0.85f);
        coulure(b, 'z', 4.3f, yc - 2.2f, z1 + 0.02f, 0.9f, 1);
        coulure(b, 'z', 12.0f, yc - 0.7f, z1, 0.8f, 2);
        boue(b, 'z', 8.0f, yb, z1, 9.6f, 1.3f, 0);
        decal(b, GRIFFURES, 'X', x1, 3.0f, 5.2f, 3.0f, 1.5f, -8);
        // bande déroulée jusqu'au sol, rouleau au bout, boîte tombée, sang séché discret
        b.color(0.9f, 0.88f, 0.82f);
        bande(b, new float[][]{p(6.0f, 3.5f, 8.2f), p(6.3f, yc + 0.08f, z1 - 0.15f), p(6.4f, yc - 0.5f, z1 + 0.12f), p(6.6f, 1.6f, z1 + 0.14f),
                        p(6.9f, 0.06f, z1 + 0.9f), p(8.0f, 0.05f, 12.4f), p(9.4f, 0.05f, 13.6f)},
                new float[][]{HAUT, {0, 0.7f, 0.7f}, AVANT, AVANT, HAUT, HAUT, HAUT}, 0.9f, 0.06f, TISSU_USE);
        b.push().translate(10.0f, 0.55f, 14.0f).rotateY(-40).rotateZ(90);
        b.cylinder(0.52f, -0.9f, 0.9f, 10, TISSU_USE, TISSU_USE, TISSU_USE);
        b.pop();
        b.color(1);
        b.push().translate(12.6f, 0, 12.4f).rotateY(33);
        b.bevelBox(-0.9f, 0, -1.1f, 0.9f, 1.6f, 1.1f, 0.08f, CARTON_BLANC);
        b.pop();
        decal(b, SANG_2, 'y', 8.6f, 0, 13.6f, 1.8f, 1.8f, 30);
    }

    /**
     * Cantine ONU (80 x 40 x 35 cm) posée sur deux planches : bleu passé, lettrage UN, cornières, couvercle cabossé,
     * moraillon tordu au pied-de-biche mais cadenas tenu, un fermoir ouvert, rouille, feuilles dans le creux.
     */
    static void cantineOnu(MeshBuilder b) {
        float x0 = 1.8f, x1 = 14.2f, z0 = 0.9f, z1 = 7.1f, ys = 0.55f, yc = ys + 4.3f, h = yc + 1.3f, zc = (z0 + z1) / 2;
        for (float x : new float[]{3.4f, 12.4f}) b.bevelBox(x - 0.9f, 0, 0.5f, x + 0.9f, ys + 0.03f, 7.8f, 0.1f, BOIS_GRIS);
        b.bevelBox(x0, ys, z0, x1, yc, z1, 0.16f, BLEU_ONU);
        b.bevelBox(x0 - 0.08f, ys + 0.45f, z0 - 0.08f, x1 + 0.08f, ys + 0.95f, z1 + 0.08f, 0.04f, BLEU_ONU);
        // couvercle : bords biseautés, dessus déformé (creux)
        b.bevelBox(x0 - 0.1f, yc + 0.02f, z0 - 0.1f, x1 + 0.1f, h, z1 + 0.1f, 0.25f, BLEU_ONU, null, MeshBuilder.SKIP_PY);
        final float lx0 = x0 + 0.15f, lx1 = x1 - 0.15f, lz0 = z0 + 0.15f, lz1 = z1 - 0.15f;
        b.surface((u, v) -> {
            float x = lx0 + (lx1 - lx0) * u, z = lz0 + (lz1 - lz0) * v;
            float dx = x - 9.8f, dz = z - 4.2f, dx2 = x - 4.6f, dz2 = z - 2.9f;
            float creux = 0.95f * (float) Math.exp(-(dx * dx / 3.0f + dz * dz / 1.7f)) + 0.4f * (float) Math.exp(-(dx2 * dx2 + dz2 * dz2) / 1.0f)
                    + 0.18f * (float) Math.exp(-Math.pow((x - 0.55f * z - 9.0f) / 0.35f, 2)) * (z > 2.2f ? 1 : 0);
            float k = Math.min(1, Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v)) * 7);
            return p(x, h - creux * k, z);
        }, 18, 9, BLEU_ONU, (lx1 - lx0) / 16f, (lz1 - lz0) / 16f, false, MeshBuilder.Orient.KEEP);
        Kit.coins(b, x0 - 0.1f, ys, z0 - 0.1f, x1 + 0.1f, h, z1 + 0.1f, 0.85f, AtlasLoot.STEEL);
        // moraillon tordu, cadenas fermé
        b.push().translate(8.0f, yc + 0.8f, z1 + 0.12f).rotateX(-16);
        b.bevelBox(-0.6f, -2.0f, 0, 0.6f, 0.2f, 0.16f, 0.06f, METAL_NU);
        b.pop();
        b.bevelBox(7.75f, yc - 1.75f, z1 - 0.02f, 8.25f, yc - 0.9f, z1 + 0.5f, 0.08f, METAL_NU);
        Kit.cadenas(b, 8.0f, yc - 1.15f, z1 + 0.62f);
        // fermoirs : gauche fermé, droit ouvert qui pend
        Kit.fermoir(b, 3.8f, yc, z1 + 0.1f, AtlasLoot.STEEL);
        b.bevelBox(11.4f, yc - 0.9f, z1 - 0.02f, 13.0f, yc - 0.1f, z1 + 0.2f, 0.06f, AtlasLoot.DARK);
        b.push().translate(12.2f, yc - 0.6f, z1 + 0.2f).rotateX(-10);
        b.bevelBox(-0.55f, -1.9f, 0, 0.55f, 0, 0.26f, 0.08f, AtlasLoot.STEEL);
        b.pop();
        Kit.poigneeU(b, x1 + 0.1f, 3.4f, zc, 2.6f, 1, AtlasLoot.STEEL);
        Kit.poigneeU(b, x0 - 0.1f, 3.4f, zc, 2.6f, -1, AtlasLoot.STEEL);
        Kit.pochoir(b, S_UN_BLANC, 'z', 4.9f, ys + 2.35f, z1, 3.6f, 1.8f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', 11.3f, ys + 2.0f, z1, 2.0f, 1.0f);
        Kit.pochoir(b, S_UN_BLANC, 'X', x1, ys + 2.3f, zc, 3.0f, 1.5f);
        // usure : griffures du pied-de-biche, rouille sous les cornières, boue, ruban sur une fissure
        decal(b, GRIFFURES, 'z', 8.6f, yc + 0.55f, z1 + 0.1f, 2.6f, 0.9f, 10);
        coulure(b, 'z', x0 + 0.5f, yc - 0.1f, z1, 1.0f, 0);
        coulure(b, 'z', x1 - 0.6f, ys + 3.2f, z1, 0.9f, 1);
        coulure(b, 'X', x1, yc - 0.1f, 2.0f, 1.0f, 2);
        boue(b, 'z', 8.0f, ys, z1, 11.0f, 1.4f, 1);
        scotchZ(b, 1.9f, ys + 3.3f, 3.6f, ys + 2.6f, z1, 0.75f, SCOTCH);
        decal(b, ETIQUETTE_DECHIREE, 'x', x0, ys + 2.2f, 3.4f, 2.4f, 1.2f, 4);
        b.push().translate(0, h - 0.55f, 0);
        jonchee(b, 8.8f, 3.4f, 10.8f, 5.0f, 3, FEUILLES, 0.9f, 571);
        b.pop();
        jonchee(b, 0.5f, 7.8f, 15.0f, 11.0f, 5, FEUILLES, 1.0f, 572);
    }

    /**
     * Armoire forte à armes (60 x 45 x 180 cm), acier peint gris-bleu : porte entrouverte (serrure arrachée, pêne
     * tordu, traces de pied-de-biche), râtelier avec trois fusils et une place vide, caisse de munitions sur l'étagère,
     * barillet de serrure et un fusil abandonnés au sol.
     */
    static void armoireArmes(MeshBuilder b) {
        float x0 = 3.0f, x1 = 13.0f, z0 = 0.4f, z1 = 7.6f, y0 = 0.8f, h = 28.8f, e = 0.35f;
        b.color(0.44f, 0.5f, 0.56f);
        b.bevelBox(x0 + 0.4f, 0, z0 + 0.3f, x1 - 0.4f, y0 + 0.03f, z1 - 0.6f, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0, y0, z0, x0 + e, h, z1, 0.12f, PEINTURE_USEE);
        b.bevelBox(x1 - e, y0, z0, x1, h, z1, 0.12f, PEINTURE_USEE);
        b.bevelBox(x0 + e - 0.02f, y0, z0, x1 - e + 0.02f, y0 + e, z1, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0 + e - 0.02f, h - e, z0, x1 - e + 0.02f, h, z1, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0 + e - 0.02f, y0 + e - 0.02f, z0, x1 - e + 0.02f, h - e + 0.02f, z0 + e, 0.08f, PEINTURE_USEE);
        // râtelier : bac à crosses, peigne, étagère
        float zi = z0 + e;
        b.bevelBox(x0 + e, y0 + e - 0.02f, zi, x1 - e, y0 + e + 0.9f, zi + 3.6f, 0.1f, PEINTURE_USEE);
        b.bevelBox(x0 + e, 13.2f, zi, x1 - e, 13.8f, zi + 1.45f, 0.08f, PEINTURE_USEE);
        for (float x = x0 + e + 1.15f; x < x1 - e - 0.4f; x += 2.4f) b.bevelBox(x - 0.18f, 13.78f, zi + 0.1f, x + 0.18f, 14.6f, zi + 1.35f, 0.05f, PEINTURE_USEE);
        b.bevelBox(x0 + e, 21.0f, zi - 0.02f, x1 - e, 21.35f, z1 - 0.7f, 0.06f, PEINTURE_USEE);
        b.color(1);
        for (float xs : new float[]{4.7f, 7.1f, 9.5f}) {
            b.push().translate(xs + (xs == 9.5f ? 0.2f : 0), y0 + e + 0.95f, zi + 1.9f).rotateY(-90).rotateZ(xs == 9.5f ? 84 : 90);
            ConteneursT5T6.fusil(b, 0, 0, 0);
            b.pop();
        }
        ConteneursT4.boiteMunitions(b, 4.0f, 21.37f, zi + 0.4f, true);
        b.push().translate(10.4f, 21.37f, zi + 2.4f).rotateY(-12);
        b.bevelBox(-1.1f, 0, -1.4f, 1.1f, 1.3f, 1.4f, 0.08f, CARTON_MOUILLE);
        b.pop();
        // porte entrouverte (charnière à gauche)
        float zd = z1, ep = 0.5f;
        b.push().translate(x0, 0, zd).rotateY(-38).translate(-x0, 0, -zd);
        b.color(0.44f, 0.5f, 0.56f);
        b.bevelBox(x0 + 0.06f, y0 + 0.06f, zd, x1 - 0.06f, h - 0.06f, zd + ep, 0.14f, PEINTURE_USEE);
        b.color(1);
        float zf = zd + ep;
        for (float y0v : new float[]{3.0f, 24.6f}) {
            for (int k = 0; k < 5; k++) b.box(x0 + 2.2f, y0v + k * 0.55f, zf - 0.05f, x1 - 2.2f, y0v + k * 0.55f + 0.24f, zf + 0.06f, AtlasLoot.DARK);
        }
        // tringles de verrouillage (intérieur), pêne tordu, poignée, serrure arrachée
        for (float y : new float[]{2.0f, h - 2.0f}) b.bevelBox(x1 - 1.6f, y, zd - 0.42f, x1 - 0.9f, y + 0.5f, zd + 0.02f, 0.06f, METAL_NU);
        b.push().translate(x1 - 1.25f, 2.3f, zd - 0.2f);
        b.cylinder(0.18f, 0, h - 4.6f, 8, METAL_NU, null, null);
        b.pop();
        b.push().translate(x1 - 0.5f, 14.0f, zd + 0.2f).rotateY(35).rotateZ(-12);
        b.bevelBox(0, -0.3f, -0.15f, 1.3f, 0.3f, 0.15f, 0.06f, METAL_NU);
        b.pop();
        b.bevelBox(x1 - 2.2f, 15.2f, zf - 0.02f, x1 - 1.3f, 17.6f, zf + 0.22f, 0.08f, AtlasLoot.DARK);
        b.bevelBox(x1 - 2.0f, 16.0f, zf + 0.2f, x1 - 1.5f, 17.4f, zf + 0.6f, 0.1f, AtlasLoot.CHROME);
        b.push().translate(x1 - 1.75f, 14.2f, zf - 0.02f).rotateX(90);
        b.cylinder(0.55f, 0, 0.2f, 12, METAL_NU, null, METAL_NU);
        b.cylinder(0.3f, 0.19f, 0.26f, 10, AtlasLoot.DARK, null, AtlasLoot.DARK);
        b.pop();
        decal(b, GRIFFURES, 'z', x1 - 2.0f, 13.6f, zf, 2.4f, 1.0f, 70);
        Kit.pochoir(b, S_UN_BLANC, 'z', (x0 + x1) / 2 - 0.4f, 21.0f, zf, 4.2f, 2.1f);
        Kit.pochoir(b, S_ARMES, 'z', (x0 + x1) / 2 - 0.4f, 19.2f, zf, 3.6f, 0.9f);
        Kit.pochoir(b, AtlasLoot.LABEL, 'z', x0 + 2.4f, 7.0f, zf, 1.8f, 0.9f);
        coulure(b, 'z', x0 + 3.0f, 24.5f, zf, 1.2f, 0);
        coulure(b, 'z', x1 - 3.4f, 3.0f, zf, 0.9f, 1);
        b.pop();
        // traces sur le bâti, rouille, boue
        decal(b, GRIFFURES, 'X', x1, 14.4f, 5.6f, 2.6f, 1.2f, -76);
        coulure(b, 'X', x1, h - 0.4f, 2.6f, 1.4f, 2);
        coulure(b, 'x', x0, 18.0f, 4.0f, 1.2f, 0);
        boue(b, 'X', x1, y0, 4.0f, 6.4f, 1.6f, 0);
        decal(b, IMPACTS, 'x', x0, 9.0f, 4.4f, 3.2f, 1.6f, 0);
        // au sol : barillet arraché, douilles, fusil tombé
        b.push().translate(12.6f, 0.3f, 13.6f).rotateY(30).rotateZ(90);
        b.cylinder(0.3f, -0.45f, 0.45f, 10, AtlasLoot.BRASS, AtlasLoot.BRASS, AtlasLoot.BRASS);
        b.pop();
        for (float[] q : new float[][]{{10.4f, 14.8f, 20}, {11.2f, 15.2f, 160}, {9.6f, 12.9f, 75}}) {
            b.push().translate(q[0], 0.13f, q[1]).rotateY(q[2]);
            cartouche(b, 1.0f, 0.13f, AtlasLoot.BRASS, AtlasLoot.BRASS);
            b.pop();
        }
        b.push().translate(1.2f, 0.36f, 11.2f).rotateY(-10).rotateX(90);
        ConteneursT5T6.fusil(b, 0, 0, 0);
        b.pop();
    }
}
