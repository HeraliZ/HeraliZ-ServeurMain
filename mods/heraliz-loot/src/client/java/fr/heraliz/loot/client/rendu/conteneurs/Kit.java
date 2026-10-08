package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasLoot.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Pièces communes des conteneurs (repère : pixels, sol en y = 0, face avant vers +Z). */
final class Kit {
    private Kit() {
    }

    /** Décalcomanie sur une face : 'z' avant (+Z), 'Z' arrière, 'x' gauche (-X), 'X' droite (+X), 'y' dessus. */
    static void pochoir(MeshBuilder b, Mat m, char face, float cx, float cy, float cz, float w, float h) {
        b.push().translate(cx, cy, cz);
        switch (face) {
            case 'Z' -> b.rotateY(180);
            case 'x' -> b.rotateY(-90);
            case 'X' -> b.rotateY(90);
            case 'y' -> b.rotateX(-90);
            default -> {
            }
        }
        b.decalZ(m, 0, 0, 0.05f, w, h);
        b.pop();
    }

    /** Vis à tête plate tournée vers +Z. */
    static void vis(MeshBuilder b, float x, float y, float z) {
        b.push().translate(x, y, z).rotateX(90);
        b.cylinder(0.22f, 0, 0.1f, 8, CHROME, null, CHROME);
        b.pop();
    }

    /** Poignée de corde sur un flanc (cote +1 : face +X en x, -1 : face -X) : deux taquets et une boucle. */
    static void poigneeCorde(MeshBuilder b, float x, float y, float zc, float largeur, int cote) {
        float z0 = zc - largeur / 2, z1 = zc + largeur / 2, s = cote;
        for (float z : new float[]{z0, z1}) b.bevelBox(Math.min(x, x + s * 0.5f), y - 0.5f, z - 0.45f, Math.max(x, x + s * 0.5f), y + 0.5f, z + 0.45f, 0.12f, OD_WOOD);
        b.tube(new float[][]{p(x + s * 0.3f, y, z0), p(x + s * 0.9f, y - 0.9f, z0 + 0.4f), p(x + s * 1.0f, y - 1.3f, zc), p(x + s * 0.9f, y - 0.9f, z1 - 0.4f), p(x + s * 0.3f, y, z1)},
                new float[]{0.24f, 0.24f, 0.24f, 0.24f, 0.24f}, 8, ROPE, false, false, 0, false);
    }

    /** Poignée en U (tube d'acier) sur un flanc ±X, rabattue vers le bas. */
    static void poigneeU(MeshBuilder b, float x, float y, float zc, float largeur, int cote, Mat m) {
        float s = cote, z0 = zc - largeur / 2, z1 = zc + largeur / 2;
        for (float z : new float[]{z0, z1}) b.bevelBox(Math.min(x, x + s * 0.35f), y - 0.6f, z - 0.45f, Math.max(x, x + s * 0.35f), y + 0.6f, z + 0.45f, 0.1f, DARK);
        b.tube(new float[][]{p(x + s * 0.3f, y, z0), p(x + s * 0.6f, y - 1.2f, z0), p(x + s * 0.6f, y - 1.2f, z1), p(x + s * 0.3f, y, z1)},
                new float[]{0.18f, 0.18f, 0.18f, 0.18f}, 8, m, false, false, 0, false);
    }

    /** Coins d'acier : huit cornières (cubes biseautés) aux coins de la boîte. */
    static void coins(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float t, Mat m) {
        for (float x : new float[]{x0 - 0.06f, x1 - t + 0.06f}) {
            for (float y : new float[]{y0, y1 - t + 0.06f}) {
                for (float z : new float[]{z0 - 0.06f, z1 - t + 0.06f}) b.bevelBox(x, y, z, x + t, y + t, z + t, 0.18f, m);
            }
        }
    }

    /** Cadenas (corps laiton, anse d'acier) accroché en (x, y, z), face avant +Z. */
    static void cadenas(MeshBuilder b, float x, float y, float z) {
        b.bevelBox(x - 0.75f, y - 1.6f, z, x + 0.75f, y - 0.2f, z + 0.55f, 0.18f, BRASS);
        b.push().translate(x, y - 0.2f, z + 0.27f).rotateX(90);
        b.torus(0.48f, 0.12f, 14, 5, 0, 180, STEEL);
        b.pop();
    }

    /** Patin caoutchouc. */
    static void pied(MeshBuilder b, float x, float z, float r, float h) {
        b.push().translate(x, 0, z);
        b.cylinder(r, 0, h, 12, RUBBER, RUBBER, RUBBER);
        b.pop();
    }

    /** Fermoir à levier (plaque + levier) sur la face avant, en (x, y), à la profondeur z. */
    static void fermoir(MeshBuilder b, float x, float y, float z, Mat m) {
        b.bevelBox(x - 0.8f, y - 0.9f, z, x + 0.8f, y + 0.5f, z + 0.25f, 0.08f, DARK);
        b.bevelBox(x - 0.55f, y - 0.6f, z + 0.2f, x + 0.55f, y + 1.1f, z + 0.5f, 0.1f, m);
    }
}
