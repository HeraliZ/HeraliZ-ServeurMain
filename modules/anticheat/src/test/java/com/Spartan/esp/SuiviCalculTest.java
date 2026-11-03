package com.Spartan.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Suivi à travers les murs (03/11) : un tricheur qui suit les zigzags d'une cible cachée est vu ; un joueur honnête qui
 * tient un angle, prolonge le mouvement d'une cible, tourne autour d'un point ou regarde ailleurs ne l'est jamais.
 * Observateur en (0, 1.62, 0), cible à ~20 blocs au sud (z), 10 échantillons par seconde.
 */
class SuiviCalculTest {
    private static final SuiviCalcul.Reglages R = new SuiviCalcul.Reglages(3.0, 0.9, 30, 0.85, 20.0, 0.85, 1, 10, 0.4);
    private static final double OEIL = 1.62;

    /** Cible qui zigzague latéralement (x) à 20 blocs : aller-retour de 8 blocs toutes les 4 s, avec arrêts. */
    private static double zigzag(int i) {
        double t = i / 10.0;
        double phase = t % 4.0;
        return phase < 2.0 ? -4 + 4 * phase : 4 - 4 * (phase - 2.0);
    }

    private static double lacet(double ex, double ez, double tx, double tz) {
        return SuiviCalcul.lacetVers(ex, ez, tx, tz);
    }

    private static double tangage(double ey, double ty, double dist) {
        return -Math.toDegrees(Math.atan2(ty - ey, dist));
    }

    @Test
    void tricheurQuiSuitLesZigzagsEstVu() {
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        boolean vu = false;
        for (int i = 0; i < 80; i++) {
            double tx = zigzag(i);
            double tz = 20;
            double bruit = Math.sin(i * 1.7) * 0.8; // petite imprécision de la main
            double y = lacet(0, 0, tx, tz) + bruit;
            assertTrue(SuiviCalcul.ajouter(e, R, 0, OEIL, 0, y, tangage(OEIL, 0.9, 20), tx, 0.9, tz));
            vu |= SuiviCalcul.suspect(e, R);
        }
        assertTrue(vu, () -> "bilan : " + SuiviCalcul.bilan(e));
    }

    @Test
    void tenirUnAngleFixeNestPasSuivre() {
        // la visée reste sur la porte (x = 0) pendant que la cible va et vient derrière le mur
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        for (int i = 0; i < 80; i++) {
            double tx = zigzag(i) * 0.3; // petits mouvements autour de la porte : souvent « sur la cible »
            if (!SuiviCalcul.ajouter(e, R, 0, OEIL, 0, 0, tangage(OEIL, 0.9, 20), tx, 0.9, 20)) {
                break;
            }
            assertFalse(SuiviCalcul.suspect(e, R), () -> "bilan : " + SuiviCalcul.bilan(e));
        }
    }

    @Test
    void prolongerUneCourseToutDroitNestPasSuspect() {
        // cible qui court tout droit derrière un long mur ; l'observateur prolonge son mouvement (aucun virage)
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        for (int i = 0; i < 60; i++) {
            double tx = -15 + i * 0.5;
            double y = lacet(0, 0, tx, 20);
            if (!SuiviCalcul.ajouter(e, R, 0, OEIL, 0, y, tangage(OEIL, 0.9, 20), tx, 0.9, 20)) {
                break;
            }
            assertFalse(SuiviCalcul.suspect(e, R), () -> "bilan : " + SuiviCalcul.bilan(e));
        }
    }

    @Test
    void tournerAutourDUneCibleImmobileNestPasSuspect() {
        // l'observateur se déplace en gardant la visée sur un ennemi qui ne bouge pas (il sait où il campe)
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        for (int i = 0; i < 80; i++) {
            double ex = zigzag(i);
            double y = lacet(ex, 0, 0, 20);
            if (!SuiviCalcul.ajouter(e, R, ex, OEIL, 0, y, tangage(OEIL, 0.9, 20), 0, 0.9, 20)) {
                break;
            }
            assertFalse(SuiviCalcul.suspect(e, R), () -> "bilan : " + SuiviCalcul.bilan(e));
        }
    }

    @Test
    void regarderAilleursFinitLEpisode() {
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        boolean encore = true;
        for (int i = 0; i < 40 && encore; i++) {
            encore = SuiviCalcul.ajouter(e, R, 0, OEIL, 0, 90, 0, zigzag(i), 0.9, 20);
        }
        assertFalse(encore);
        assertFalse(SuiviCalcul.suspect(e, R));
    }

    @Test
    void reactionEnRetardAuxVirages() {
        // joueur honnête qui suit au son, avec 0,5 s de retard : il sort de la cible à chaque virage
        SuiviCalcul.Episode e = new SuiviCalcul.Episode();
        boolean vu = false;
        for (int i = 5; i < 85; i++) {
            double tx = zigzag(i);
            double y = lacet(0, 0, zigzag(i - 5), 20);
            if (!SuiviCalcul.ajouter(e, R, 0, OEIL, 0, y, tangage(OEIL, 0.9, 20), tx, 0.9, 20)) {
                break;
            }
            vu |= SuiviCalcul.suspect(e, R);
        }
        assertFalse(vu, () -> "bilan : " + SuiviCalcul.bilan(e));
    }

    @Test
    void ecartEtLacetSuiventLaConventionDeMinecraft() {
        // lacet 0 = sud (+z), 90 = ouest (-x), tangage positif = vers le bas
        assertTrue(SuiviCalcul.ecart(0, 0, 0, 0, 0, 0, 0, 10) < 1e-6);
        assertTrue(SuiviCalcul.ecart(0, 0, 0, 90, 0, -10, 0, 0) < 1e-6);
        assertTrue(Math.abs(SuiviCalcul.ecart(0, 0, 0, 0, 0, 10, 0, 0) - 90) < 1e-6);
        assertTrue(SuiviCalcul.ecart(0, 0, 0, 0, 45, 0, -10, 10) < 1e-6);
    }
}
