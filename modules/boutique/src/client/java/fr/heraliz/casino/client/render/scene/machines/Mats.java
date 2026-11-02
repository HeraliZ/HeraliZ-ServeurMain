package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;

/**
 * Matières d'un atlas de machine décrites par leur rectangle (x, y, largeur, hauteur). Outils communs aux rendus des
 * machines (02/11 : sortis du rendu de l'ancienne machine « Fruits d'Or », retirée du jeu, qui les portait).
 */
final class Mats {
    private Mats() {
    }

    /** Rectangle de l'atlas répété tous les {@code world} blocs (projection triplanaire). */
    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    /** Rectangle de l'atlas étiré sur la face entière. */
    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }
}
