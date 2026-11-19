package com.Spartan.check.interaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Points visés pour la vue d'un objet (19/11) : le point visé par le jeu du joueur d'abord, tel quel (ramené au bord de la
 * boîte s'il en sort) ; les autres points rentrés dans la boîte ; rayon arrêté juste avant son point ; distance des yeux à
 * la boîte.
 */
class PointsDeVueTest {
    @Test
    void lePointViseVientDAbordRameneAuBordDeLaBoite() {
        double[][] p = PointsDeVue.points(10, 64, 10, 11, 65, 11, 10.5, 65.0, 9.0);
        assertEquals(16, p.length);
        // visé sur la face du dessus, un peu hors de la boîte en z : ramené au bord, sans être poussé dedans
        assertArrayEquals(new double[] {10.5, 65.0, 10.0}, p[0], 1e-9);
        assertArrayEquals(new double[] {10.5, 64.5, 10.5}, p[1], 1e-9);
    }

    @Test
    void sansPointViseLeMilieuDAbord() {
        double[][] p = PointsDeVue.points(0, 0, 0, 2, 1, 1, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(15, p.length);
        assertArrayEquals(new double[] {1, 0.5, 0.5}, p[0], 1e-9);
    }

    @Test
    void tousLesPointsSontDansLaBoite() {
        double[][] p = PointsDeVue.points(-3.2, 70, 5, -2.1, 71.8, 5.4, -10, 90, 0);
        for (int i = 0; i < p.length; i++) {
            double[] q = p[i];
            boolean bord = i == 0; // le point visé peut être sur le bord ; les autres sont rentrés
            assertTrue(bord ? q[0] >= -3.2 && q[0] <= -2.1 : q[0] > -3.2 && q[0] < -2.1, () -> "x hors de la boîte : " + q[0]);
            assertTrue(bord ? q[1] >= 70 && q[1] <= 71.8 : q[1] > 70 && q[1] < 71.8, () -> "y hors de la boîte : " + q[1]);
            assertTrue(bord ? q[2] >= 5 && q[2] <= 5.4 : q[2] > 5 && q[2] < 5.4, () -> "z hors de la boîte : " + q[2]);
        }
    }

    @Test
    void leRayonSArreteJusteAvantSonPoint() {
        double[] f = PointsDeVue.finDuRayon(0, 0, 0, 3, 0, 4); // 5 blocs
        assertArrayEquals(new double[] {3 * 4.95 / 5, 0, 4 * 4.95 / 5}, f, 1e-9);
        // très près : le point lui-même
        assertArrayEquals(new double[] {0.05, 0, 0}, PointsDeVue.finDuRayon(0, 0, 0, 0.05, 0, 0), 1e-9);
    }

    @Test
    void distanceDesYeuxALaBoite() {
        assertEquals(0.0, PointsDeVue.distance(0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
        assertEquals(3.0, PointsDeVue.distance(4, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
        assertEquals(5.0, PointsDeVue.distance(4, 5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
    }
}
