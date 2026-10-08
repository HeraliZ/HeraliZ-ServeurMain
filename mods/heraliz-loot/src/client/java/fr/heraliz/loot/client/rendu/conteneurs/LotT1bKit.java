package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.LotT1bAtlas.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Pièces communes du lot T1b (repère : pixels, sol en y = 0, avant vers +Z) : plaques trouées (hublots, vasques),
 * livres, flacons, canettes, chaussures, tas de linge, poignées de meubles.
 */
final class LotT1bKit {
    private LotT1bKit() {
    }

    static final Mat PLAST = AtlasUsure.PLASTIQUE_USE;
    static final Mat PEINT = AtlasUsure.PEINTURE_USEE;
    static final Mat TOILE = AtlasUsure.TISSU_USE;

    static void teinte(MeshBuilder b, int rgb) {
        b.colorRgb(rgb);
    }

    static float[] fill(int n, float v) {
        float[] o = new float[n];
        java.util.Arrays.fill(o, v);
        return o;
    }

    /**
     * Plaque rectangulaire (centrée sur le trou, demi-côtés hw, hh) percée d'un trou rond de rayon r, dans le plan z,
     * tournée vers +Z. Les coins du rectangle tombent sur les rayons à 45° (segs multiple de 8).
     */
    static void plaqueTrouee(MeshBuilder b, Mat m, float cx, float cy, float z, float hw, float hh, float r, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float[] c0 = p(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0), z);
            float[] c1 = p(cx + r * (float) Math.cos(a1), cy + r * (float) Math.sin(a1), z);
            float[] b0 = bord(cx, cy, z, hw, hh, a0), b1 = bord(cx, cy, z, hw, hh, a1);
            b.face(m, v(0, 0, 1), c0, c1, b1, b0);
        }
    }

    /** Même chose dans le plan y (dessus d'un meuble percé d'une vasque), tournée vers +Y ; ellipse rx, rz. */
    static void plaqueTroueeY(MeshBuilder b, Mat m, float cx, float y, float cz, float hw, float hd, float rx, float rz, int segs) {
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float[] c0 = p(cx + rx * (float) Math.cos(a0), y, cz + rz * (float) Math.sin(a0));
            float[] c1 = p(cx + rx * (float) Math.cos(a1), y, cz + rz * (float) Math.sin(a1));
            float[] q0 = bord(cx, cz, 0, hw, hd, a0), q1 = bord(cx, cz, 0, hw, hd, a1);
            b.face(m, v(0, 1, 0), c0, c1, p(q1[0], y, q1[1]), p(q0[0], y, q0[1]));
        }
    }

    private static float[] bord(float cx, float cy, float z, float hw, float hh, double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float k = Math.max(Math.abs(c), Math.abs(s));
        return p(cx + hw * c / k, cy + hh * s / k, z);
    }

    /** Paroi intérieure d'un trou rond (axe Z), de z0 à z1, tournée vers l'axe. */
    static void fut(MeshBuilder b, Mat m, float cx, float cy, float z0, float z1, float r, int segs) {
        b.push().translate(cx, cy, 0).rotateX(90);
        b.lathe(new float[][]{{r, z0}, {r, z1}}, segs, 0, 360, m, 1, 1, 0, 0, 0, true);
        b.pop();
    }

    /** Disque tourné vers +Z (fond de tambour, hublot, bouton). */
    static void disqueZ(MeshBuilder b, Mat m, float cx, float cy, float z, float r, int segs) {
        b.push().translate(cx, cy, z).rotateX(90);
        b.disc(r, 0, segs, m, false);
        b.pop();
    }

    /**
     * Livre debout (dos vers +Z) : couvertures teintées sur les côtés et le dos, tranche de pages en haut et à
     * l'arrière (légèrement en retrait). Coin bas-gauche-arrière (x, y, z), épaisseur e, hauteur h, profondeur d.
     */
    static void livre(MeshBuilder b, float x, float y, float z, float e, float h, float d, int rgb) {
        teinte(b, rgb);
        b.box6(x, y, z, x + e, y + h, z + d, LIVRE, LIVRE, null, null, null, LIVRE);
        b.color(1);
        b.box6(x + 0.06f, y + 0.02f, z + 0.04f, x + e - 0.06f, y + h - 0.06f, z + d - 0.08f, null, null, null, PAGES, PAGES, null);
    }

    /** Livre couché (à plat), dos vers +Z ; ouvert = false. */
    static void livreCouche(MeshBuilder b, float x, float y, float z, float l, float e, float d, int rgb) {
        teinte(b, rgb);
        b.box6(x, y, z, x + l, y + e, z + d, null, null, LIVRE, LIVRE, null, LIVRE);
        b.color(1);
        b.box6(x + 0.04f, y + 0.06f, z + 0.04f, x + l - 0.04f, y + e - 0.06f, z + d - 0.06f, PAGES, PAGES, null, null, PAGES, null);
    }

    /** Flacon (lathe) avec bouchon et étiquette enroulée ; posé en (x, y, z). */
    static void flacon(MeshBuilder b, float x, float y, float z, float r, float h, int rgb, Mat etiquette, int rgbBouchon) {
        b.push().translate(x, y, z);
        teinte(b, rgb);
        b.lathe(new float[][]{{r * 0.86f, 0}, {r, 0.25f}, {r, h * 0.72f}, {r * 0.82f, h * 0.84f}, {r * 0.42f, h * 0.9f}, {r * 0.42f, h * 0.93f}},
                12, 0, 360, PLAST, 1, 1, 0, 0, 0, false);
        b.disc(r * 0.86f, 0.001f, 12, PLAST, false);
        teinte(b, rgbBouchon);
        b.cylinder(r * 0.46f, h * 0.92f, h, 10, PLAST, null, PLAST);
        b.color(1);
        if (etiquette != null) b.cylinder(r + 0.06f, h * 0.18f, h * 0.62f, 12, etiquette, null, null);
        b.pop();
    }

    /** Canette (6,6 x 12 cm) debout. */
    static void canette(MeshBuilder b, float x, float y, float z, Mat etiquette) {
        b.push().translate(x, y, z);
        b.lathe(new float[][]{{0.4f, 0}, {0.53f, 0.12f}, {0.53f, 1.75f}, {0.42f, 1.92f}}, 12, 0, 360, AtlasUsure.METAL_NU, 1, 1, 0, 0, 0, false);
        b.cylinder(0.575f, 0.2f, 1.68f, 12, etiquette, null, null);
        b.disc(0.42f, 1.92f, 12, AtlasUsure.METAL_NU, true);
        b.disc(0.4f, 0.001f, 10, AtlasUsure.METAL_NU, false);
        b.pop();
    }

    /** Canette couchée (axe le long de X tourné de ang degrés autour de Y), écrasée ou non. */
    static void canetteCouchee(MeshBuilder b, float x, float z, float ang, Mat etiquette, boolean ecrasee) {
        b.push().translate(x, 0.58f * (ecrasee ? 0.7f : 1f), z).rotateY(ang).rotateZ(90);
        if (ecrasee) b.scale(0.7f, 1, 0.85f);
        b.translate(0, -0.96f, 0);
        b.lathe(new float[][]{{0.4f, 0}, {0.53f, 0.12f}, {0.53f, 1.75f}, {0.42f, 1.92f}}, 12, 0, 360, AtlasUsure.METAL_NU, 1, 1, 0, 0, 0, false);
        b.cylinder(0.575f, 0.2f, 1.68f, 12, etiquette, null, null);
        b.disc(0.42f, 1.92f, 12, AtlasUsure.METAL_NU, true);
        b.disc(0.4f, 0, 10, AtlasUsure.METAL_NU, false);
        b.pop();
    }

    /** Petit bouton de meuble (rond) sur une face +Z. */
    static void bouton(MeshBuilder b, float x, float y, float z, float r, Mat m) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(r * 0.5f, -0.02f, r * 0.7f, 8, m, null, null);
        b.translate(0, r * 0.75f, 0);
        b.sphereSection(r, 10, 4, 0, 100, m);
        b.pop();
    }

    /** Poignée barre (meuble de cuisine, classeur) horizontale sur une face +Z. */
    static void poigneeBarre(MeshBuilder b, float x0, float x1, float y, float z, Mat m) {
        for (float x : new float[]{x0, x1}) b.bevelBox(x - 0.18f, y - 0.18f, z - 0.02f, x + 0.18f, y + 0.18f, z + 0.42f, 0.06f, m);
        b.bevelBox(x0 - 0.25f, y - 0.2f, z + 0.38f, x1 + 0.25f, y + 0.2f, z + 0.62f, 0.08f, m);
    }

    /** Tas de vêtements (bloc mou déformé) centré en (x, y, z). */
    static void tas(MeshBuilder b, float x, float y, float z, float rx, float ry, float rz, int graine, Mat m) {
        b.push().translate(x, y, z);
        b.blob(rx, ry, rz, 0.8f, 0.22f, 1.6f, graine, 14, m);
        b.pop();
    }

    /** Manche, jambe de pantalon, serviette qui pend : tube mou le long d'un chemin. */
    static void pendant(MeshBuilder b, float[][] chemin, float r, Mat m) {
        float[] rr = new float[chemin.length];
        for (int i = 0; i < rr.length; i++) rr[i] = r * (0.9f + 0.2f * (i % 2));
        b.tube(chemin, rr, 8, m, true, true, 0, false);
    }

    /** Linge plat qui pend (ruban large, serviette, drap) le long d'un chemin ; side : direction de la largeur. */
    static void drape(MeshBuilder b, float[][] chemin, float largeur, float[] side, Mat m) {
        float[] w = fill(chemin.length, largeur);
        b.ribbon(chemin, w, side, m);
        // envers (le ruban n'a qu'une face)
        float[][] inv = new float[chemin.length][];
        for (int i = 0; i < chemin.length; i++) inv[i] = chemin[chemin.length - 1 - i];
        float[] w2 = fill(chemin.length, largeur);
        b.ribbon(inv, w2, side, m);
    }

    /**
     * Chaussure couchée ou debout : semelle caoutchouc, empeigne. (x, z) : centre ; ang : orientation (degrés, 0 =
     * pointe vers +X) ; type 0 basket, 1 chaussure de ville, 2 botte, 3 pantoufle.
     */
    static void chaussure(MeshBuilder b, float x, float y, float z, float ang, int type, int rgb) {
        float L = type == 2 ? 4.6f : 4.4f, W = 1.6f;
        b.push().translate(x, y, z).rotateY(ang);
        b.color(type == 1 ? 0.35f : 0.85f);
        b.bevelBox(-L / 2, 0, -W / 2, L / 2, 0.38f, W / 2, 0.14f, AtlasUsure.CAOUTCHOUC_USE);
        b.color(1);
        teinte(b, rgb);
        Mat m = type == 1 || type == 2 ? AtlasUsure.CUIR_USE : (type == 3 ? LotT1bAtlas.EPONGE : TOILE);
        // avant-pied (bombé) et talon
        b.push().translate(L * 0.18f, 0.36f, 0).scale(1.4f, 0.62f, 0.78f);
        b.sphereSection(1.0f, 10, 4, 0, 90, m);
        b.pop();
        float hh = type == 2 ? 5.6f : (type == 3 ? 0.55f : 1.25f);
        b.bevelBox(-L / 2 + 0.1f, 0.36f, -W / 2 + 0.06f, L * 0.05f, 0.36f + hh, W / 2 - 0.06f, type == 3 ? 0.2f : 0.4f, m);
        if (type == 0) {
            b.color(0.92f);
            b.bevelBox(-L / 2 + 0.6f, 0.4f + hh - 0.06f, -W / 2 + 0.3f, L * 0.05f - 0.25f, 0.36f + hh + 0.06f, W / 2 - 0.3f, 0.12f, AtlasUsure.INTERIEUR_SALE);
            b.color(0.92f, 0.9f, 0.86f);
            b.bevelBox(-L / 2 - 0.03f, 0.3f, -W / 2 - 0.03f, L / 2 + 0.03f, 0.62f, W / 2 + 0.03f, 0.12f, PLAST);
        }
        b.color(1);
        b.pop();
    }

    /** Papiers épars au sol (feuilles, enveloppes). */
    static void papiers(MeshBuilder b, float x0, float z0, float x1, float z1, int n, long graine) {
        Random r = new Random(graine);
        for (int k = 0; k < n; k++) {
            float x = x0 + r.nextFloat() * (x1 - x0), z = z0 + r.nextFloat() * (z1 - z0), w = 1.4f + r.nextFloat() * 1.2f, h = w * (0.7f + r.nextFloat() * 0.5f);
            float t = r.nextFloat() * 6 - 3, y = 0.04f + k * 0.012f + Math.max(w, h) / 2 * (float) Math.sin(Math.toRadians(Math.abs(t)));
            b.push().translate(x, y, z).rotateY(r.nextFloat() * 360).rotateX(-90 + t);
            b.decalDouble(AtlasUsure.PAPIER.sub(r.nextFloat() * 0.4f, r.nextFloat() * 0.4f, 0.6f + r.nextFloat() * 0.4f, 0.6f + r.nextFloat() * 0.4f), w, h);
            b.pop();
        }
    }

    /** Graine pseudo-aléatoire stable. */
    static Random rnd(long g) {
        return new Random(g);
    }

    /** Masse molle posée au sol (sac, ballot, tas) : centrée en (x, z), le bas ne passe pas sous le sol. */
    static void tasSol(MeshBuilder b, float x, float z, float rx, float ry, float rz, float bruit, int graine, Mat m) {
        b.push().translate(x, ry * (1 + bruit * 0.8f), z);
        b.blob(rx, ry, rz, 0.85f, bruit, 1.4f, graine, 14, m);
        b.pop();
    }

    /** Fourchette à plat (y = support), pointe vers +X tournée de ang degrés. */
    static void fourchette(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y, z).rotateY(ang);
        b.box(-1.6f, 0, -0.14f, 0.4f, 0.08f, 0.14f, AtlasUsure.CHROME_PIQUE);
        b.box(0.4f, 0, -0.24f, 0.9f, 0.07f, 0.24f, AtlasUsure.CHROME_PIQUE);
        for (float t : new float[]{-0.18f, 0, 0.18f}) b.box(0.9f, 0, t - 0.04f, 1.6f, 0.06f, t + 0.04f, AtlasUsure.CHROME_PIQUE);
        b.pop();
    }

    static void cuillere(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y, z).rotateY(ang);
        b.box(-1.6f, 0, -0.14f, 0.4f, 0.08f, 0.14f, AtlasUsure.CHROME_PIQUE);
        b.push().translate(0.95f, 0.24f, 0).scale(0.6f, 0.22f, 0.38f).rotateZ(180);
        b.sphereSection(1.0f, 10, 4, 0, 90, AtlasUsure.CHROME_PIQUE);
        b.pop();
        b.pop();
    }

    static void couteau(MeshBuilder b, float x, float y, float z, float ang) {
        b.push().translate(x, y, z).rotateY(ang);
        b.bevelBox(-1.8f, 0, -0.2f, -0.2f, 0.3f, 0.2f, 0.08f, AtlasCivil.DARK);
        b.box(-0.2f, 0, -0.04f, 1.8f, 0.05f, 0.3f, AtlasUsure.CHROME_PIQUE);
        b.pop();
    }
}
