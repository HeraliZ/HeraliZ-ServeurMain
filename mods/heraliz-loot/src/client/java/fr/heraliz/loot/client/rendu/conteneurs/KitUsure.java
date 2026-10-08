package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import java.util.Random;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;

/**
 * Dégâts et usure posés sur les modèles (monde post-apocalyptique) : décalcomanies d'usure (coulures, boue, sang,
 * croix de fouille...), scotch, débris, papiers et feuilles au sol. Les décalcomanies sont décollées de 0,05 à 0,14 px
 * de la face selon leur couche ({@link #niveau}) : aucun scintillement, même quand elles se chevauchent. Elles doivent
 * rester sur la face (rien qui dépasse dans le vide) et ne pas recouvrir un pochoir (Kit.pochoir est à 0,05).
 * Faces : 'z' avant (+Z), 'Z' arrière, 'x' gauche (-X), 'X' droite (+X), 'y' dessus.
 */
final class KitUsure {
    private KitUsure() {
    }

    static final float DECOLLE = 0.05f;

    /**
     * Recale ce qui a été construit depuis {@code depuis} (b.mark()) : posé au sol (y min = 0) et appuyé au mur
     * (z min = mur, 0,05 px conseillé). À appeler après la rotation d'un objet penché ou couché.
     */
    static void caler(MeshBuilder b, int depuis, float mur) {
        float[] bo = b.bounds(depuis);
        b.offset(depuis, 0, -bo[1], mur - bo[2]);
    }

    /** Comme caler, et ramène aussi x min à gauche (x min = gauche) : objets couchés en travers. */
    static void caler(MeshBuilder b, int depuis, float gauche, float mur) {
        float[] bo = b.bounds(depuis);
        b.offset(depuis, gauche - bo[0], -bo[1], mur - bo[2]);
    }

    /**
     * Hauteur de la décalcomanie au-dessus de la face, par couches : boue et mousse au plus près, puis auréoles et
     * brûlures, puis coulures, puis le reste (étiquettes, affiches, sang, croix...) : deux décalcomanies qui se
     * chevauchent ne sont jamais dans le même plan (écart de 0,03 px).
     */
    static float niveau(Mat m) {
        if (m == BOUE_1 || m == BOUE_2 || m == MOUSSE_VERTE) return DECOLLE;
        if (m == AUREOLE || m == BRULURE) return DECOLLE + 0.03f;
        if (m == COULURE_1 || m == COULURE_2 || m == COULURE_3) return DECOLLE + 0.06f;
        return DECOLLE + 0.09f;
    }

    /** Décalcomanie (centre cx, cy, cz sur la face), tournée de angle degrés dans le plan de la face. */
    static void decal(MeshBuilder b, Mat m, char face, float cx, float cy, float cz, float w, float h, float angle) {
        b.push().translate(cx, cy, cz);
        switch (face) {
            case 'Z' -> b.rotateY(180);
            case 'x' -> b.rotateY(-90);
            case 'X' -> b.rotateY(90);
            case 'y' -> b.rotateX(-90);
            default -> {
            }
        }
        if (angle != 0) b.rotateZ(angle);
        b.decalZ(m, 0, 0, niveau(m), w, h);
        b.pop();
    }

    static void decal(MeshBuilder b, Mat m, char face, float cx, float cy, float cz, float w, float h) {
        decal(b, m, face, cx, cy, cz, w, h, 0);
    }

    /**
     * Coulure de rouille qui part d'un point (vis, gond, coin, soudure) et descend sur la face.
     * (x, yHaut, z) : point de départ sur la face ; largeur : px (la coulure fait deux fois plus haut).
     */
    static void coulure(MeshBuilder b, char face, float x, float yHaut, float z, float largeur, int variante) {
        Mat m = switch (Math.floorMod(variante, 3)) {
            case 0 -> COULURE_1;
            case 1 -> COULURE_2;
            default -> COULURE_3;
        };
        float h = largeur * 2;
        decal(b, m, face, x, yHaut - h / 2 + largeur * 0.24f, z, largeur, h);
    }

    /** Boue au pied d'une face : bande d'éclaboussures posée sur le sol (y = bas). */
    static void boue(MeshBuilder b, char face, float cx, float bas, float z, float largeur, float hauteur, int variante) {
        decal(b, Math.floorMod(variante, 2) == 0 ? BOUE_1 : BOUE_2, face, cx, bas + hauteur / 2 + 0.02f, z, largeur, hauteur);
    }

    /** Bande de ruban adhésif à plat sur une face +Z (z = plan de la face), de (x0, y0) à (x1, y1). */
    static void scotchZ(MeshBuilder b, float x0, float y0, float x1, float y1, float z, float largeur, Mat m) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.hypot(dx, dy);
        b.push().translate((x0 + x1) / 2, (y0 + y1) / 2, z).rotateZ((float) Math.toDegrees(Math.atan2(dy, dx)));
        b.box(-l / 2, -largeur / 2, DECOLLE * 0.4f, l / 2, largeur / 2, DECOLLE * 0.4f + 0.06f, m);
        b.pop();
    }

    /** Bande de ruban adhésif à plat sur le dessus (y = plan du dessus), de (x0, z0) à (x1, z1). */
    static void scotchY(MeshBuilder b, float x0, float z0, float x1, float z1, float y, float largeur, Mat m) {
        float dx = x1 - x0, dz = z1 - z0, l = (float) Math.hypot(dx, dz);
        b.push().translate((x0 + x1) / 2, y, (z0 + z1) / 2).rotateY((float) -Math.toDegrees(Math.atan2(dz, dx)));
        b.box(-l / 2, DECOLLE * 0.4f, -largeur / 2, l / 2, DECOLLE * 0.4f + 0.06f, largeur / 2, m);
        b.pop();
    }

    /**
     * Ruban qui ferme un couvercle ou une porte : il passe sur l'arête avant du dessus (y = haut, z = avant) et
     * descend de « bas » sur la face avant, à l'abscisse x.
     */
    static void scotchArete(MeshBuilder b, float x, float haut, float avant, float surDessus, float bas, float largeur, Mat m) {
        float e = DECOLLE * 0.4f;
        b.box(x - largeur / 2, haut + e, avant - surDessus, x + largeur / 2, haut + e + 0.06f, avant + e + 0.06f, m);
        b.box(x - largeur / 2, haut - bas, avant + e, x + largeur / 2, haut + e, avant + e + 0.06f, m);
    }

    /** Tête de boulon rouillée sur une face +Z (z = plan de la face). */
    static void boulon(MeshBuilder b, float x, float y, float z, float r) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(r, 0, r * 0.55f, 6, ROUILLE, null, ROUILLE);
        b.pop();
    }

    /** Débris à plat au sol (éclats de verre, gravats, morceaux de plastique) dans un disque. */
    static void debris(MeshBuilder b, float cx, float cz, float rayon, int n, Mat m, long graine) {
        Random r = new Random(graine);
        for (int k = 0; k < n; k++) {
            double a = r.nextFloat() * Math.PI * 2;
            float d = rayon * (float) Math.sqrt(r.nextFloat());
            float x = cx + (float) Math.cos(a) * d, z = cz + (float) Math.sin(a) * d;
            float s = 0.25f + r.nextFloat() * 0.45f;
            b.push().translate(x, 0.08f, z).rotateY(r.nextFloat() * 360).rotateX(-90 + r.nextFloat() * 10 - 5);
            b.prism(new float[][]{{-s, 0}, {s * 0.8f, -s * 0.4f}, {s * 0.3f, s * 0.9f}}, 0.06f + r.nextFloat() * 0.1f, m, m);
            b.pop();
        }
    }

    /** Feuilles mortes ou papiers épars au sol (cartes fines inclinées) dans un rectangle. */
    static void jonchee(MeshBuilder b, float x0, float z0, float x1, float z1, int n, Mat m, float taille, long graine) {
        Random r = new Random(graine);
        for (int k = 0; k < n; k++) {
            float x = x0 + r.nextFloat() * (x1 - x0), z = z0 + r.nextFloat() * (z1 - z0), s = taille * (0.6f + r.nextFloat() * 0.6f);
            b.push().translate(x, 0.04f + r.nextFloat() * 0.08f, z).rotateY(r.nextFloat() * 360).rotateX(-90 + r.nextFloat() * 14 - 7);
            b.decalDouble(m.sub(r.nextFloat() * 0.5f, r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.5f), s, s * (0.6f + r.nextFloat() * 0.5f));
            b.pop();
        }
    }
}
