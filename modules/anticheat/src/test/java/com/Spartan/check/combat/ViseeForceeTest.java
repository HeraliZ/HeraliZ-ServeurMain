package com.Spartan.check.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Visée forcée (silent aim, 19/11) : l'aller-retour d'un aimbot discret est vu ; le coup de souris d'un humain, le recul
 * de l'arme, le suivi d'une cible qui court et une petite correction ne le sont jamais.
 */
class ViseeForceeTest {
    private static final double COUP = 15;
    private static final double RETOUR = 4;

    @Test
    void ecartDesVisees() {
        assertEquals(0, ViseeForcee.ecart(10, 5, 10, 5), 1e-6);
        assertEquals(90, ViseeForcee.ecart(0, 0, 90, 0), 1e-6);
        assertEquals(20, ViseeForcee.ecart(170, 0, -170, 0), 1e-6); // le lacet passe par 180
        assertEquals(30, ViseeForcee.ecart(0, -10, 0, 20), 1e-6);
    }

    @Test
    void allerRetourDUnAimbotDiscretEstVu() {
        // l'écran regarde à 0° ; le paquet du tir vise la cible à 40° ; le paquet suivant revient à 0,5°
        assertTrue(ViseeForcee.allerRetour(0, 2, 40, -3, 0.5f, 2.2f, COUP, RETOUR));
    }

    @Test
    void coupDeSourisVersLaCibleSansRetourNEstPasVu() {
        // un humain saute sur la cible, tire et reste sur elle (ou continue un peu)
        assertFalse(ViseeForcee.allerRetour(0, 2, 40, -3, 41, -3.5f, COUP, RETOUR));
        assertFalse(ViseeForcee.allerRetour(0, 2, 40, -3, 30, -2, COUP, RETOUR));
    }

    @Test
    void reculDeLArmeNEstPasUnAllerRetour() {
        // tir : le recul relève la visée de 3 à 6 degrés, puis elle redescend
        assertFalse(ViseeForcee.allerRetour(5, 0, 5, 0, 5, -6, COUP, RETOUR));
        assertFalse(ViseeForcee.allerRetour(5, -6, 5, 0, 5, -5, COUP, RETOUR));
    }

    @Test
    void suiviDUneCibleQuiCourtNestPasVu() {
        // visée qui suit une cible : 10 degrés par tick dans le même sens
        assertFalse(ViseeForcee.allerRetour(0, 0, 10, 0, 20, 0, COUP, RETOUR));
    }

    @Test
    void petiteCorrectionNestPasVue() {
        // petit aller-retour de 6 degrés : en dessous du coup minimal
        assertFalse(ViseeForcee.allerRetour(0, 0, 6, 0, 0.2f, 0, COUP, RETOUR));
    }

    @Test
    void ecartAUneBoiteSelonSaTailleApparente() {
        // joueur à 20 blocs plein sud (z+), œil à sa hauteur : visée droite dessus = 0
        assertEquals(0, ViseeForcee.ecartBoite(0, 1.6, 0, 0, 0, -0.3, 0.7, 19.7, 0.3, 2.5, 20.3), 1e-6);
        // 30 degrés à côté : un peu moins de 30 (sa taille apparente compte)
        double e = ViseeForcee.ecartBoite(0, 1.6, 0, 30, 0, -0.3, 0.7, 19.7, 0.3, 2.5, 20.3);
        assertTrue(e > 25 && e < 30, () -> "écart " + e);
    }
}
