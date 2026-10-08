package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/** Pièces communes des conteneurs T2-T3 (atlas civil). Repère : pixels, sol en y = 0, avant vers +Z. */
final class KitCivil {
    private KitCivil() {
    }

    static void pied(MeshBuilder b, float x, float z, float r, float h, Mat m) {
        b.push().translate(x, 0, z);
        b.cylinder(r, 0, h, 12, m, m, m);
        b.pop();
    }

    /** Poignée en U (tube) sur un flanc ±X, rabattue vers le bas. */
    static void poigneeU(MeshBuilder b, float x, float y, float zc, float largeur, int cote, Mat m) {
        float s = cote, z0 = zc - largeur / 2, z1 = zc + largeur / 2;
        for (float z : new float[]{z0, z1}) b.bevelBox(Math.min(x, x + s * 0.35f), y - 0.6f, z - 0.45f, Math.max(x, x + s * 0.35f), y + 0.6f, z + 0.45f, 0.1f, DARK);
        b.tube(new float[][]{p(x + s * 0.3f, y, z0), p(x + s * 0.6f, y - 1.2f, z0), p(x + s * 0.6f, y - 1.2f, z1), p(x + s * 0.3f, y, z1)},
                new float[]{0.18f, 0.18f, 0.18f, 0.18f}, 8, m, false, false, 0, false);
    }

    /** Anse de sac (sangle arquée) sur le dessus, entre x0 et x1. */
    static void anse(MeshBuilder b, float x0, float x1, float y, float z, float h, Mat m) {
        b.tube(new float[][]{p(x0, y, z), p(x0 + 0.4f, y + h, z), p(x1 - 0.4f, y + h, z), p(x1, y, z)},
                new float[]{0.22f, 0.24f, 0.24f, 0.22f}, 8, m, false, false, 0, false);
    }

    /** Fermoir (plaque + levier) sur la face avant. */
    static void fermoir(MeshBuilder b, float x, float y, float z, Mat m) {
        b.bevelBox(x - 0.8f, y - 0.9f, z, x + 0.8f, y + 0.5f, z + 0.25f, 0.08f, DARK);
        b.bevelBox(x - 0.55f, y - 0.6f, z + 0.2f, x + 0.55f, y + 1.1f, z + 0.5f, 0.1f, m);
    }

    /** Fermeture à glissière (ligne sombre et curseur) le long de X sur une face +Z. */
    static void glissiere(MeshBuilder b, float x0, float x1, float y, float z) {
        b.bevelBox(x0, y - 0.12f, z - 0.05f, x1, y + 0.12f, z + 0.06f, 0.03f, DARK);
        b.bevelBox(x1 - 0.9f, y - 0.35f, z, x1 - 0.3f, y + 0.25f, z + 0.25f, 0.08f, CHROME);
    }

    /** Coins de laiton. */
    static void coins(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float t, Mat m) {
        for (float x : new float[]{x0 - 0.06f, x1 - t + 0.06f}) {
            for (float y : new float[]{y0, y1 - t + 0.06f}) {
                for (float z : new float[]{z0 - 0.06f, z1 - t + 0.06f}) b.bevelBox(x, y, z, x + t, y + t, z + t, 0.16f, m);
            }
        }
    }
}
