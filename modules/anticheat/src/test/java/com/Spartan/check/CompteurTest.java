package com.Spartan.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Compte sur une fenêtre glissante (19/11) : seuls les événements récents comptent, vider repart de zéro. */
class CompteurTest {
    @Test
    void seulsLesEvenementsDeLaFenetreComptent() {
        Compteur c = new Compteur(8);
        assertEquals(1, c.ajouter(0, 10_000));
        assertEquals(2, c.ajouter(4_000, 10_000));
        assertEquals(3, c.ajouter(9_000, 10_000));
        assertEquals(2, c.ajouter(14_500, 10_000)); // celui de 0 et celui de 4 000 sont sortis
        assertEquals(1, c.compter(24_000, 10_000));
    }

    @Test
    void viderRepartDeZero() {
        Compteur c = new Compteur(4);
        c.ajouter(0, 1000);
        c.ajouter(1, 1000);
        c.vider();
        assertEquals(1, c.ajouter(2, 1000));
    }

    @Test
    void auPlusLaTailleDuTableau() {
        Compteur c = new Compteur(4);
        int n = 0;
        for (int i = 0; i < 10; i++) {
            n = c.ajouter(i, 1000);
        }
        assertEquals(4, n);
    }
}
