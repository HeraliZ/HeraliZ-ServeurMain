package com.Spartan.check.movement.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Arrivées des tirs notées par le fil réseau et reprises par le fil du serveur (19/11), et silences du réseau.
 */
class ArriveesTirsTest {
    private static final long MS = 1_000_000L;

    @Test
    void chaqueTirRetrouveSonArriveeParSonHeure() {
        ArriveesTirs a = new ArriveesTirs();
        a.noter(1000, 10 * MS, 0);
        a.noter(1100, 110 * MS, 0);
        a.noter(1200, 210 * MS, 40 * MS);
        long[] s = new long[2];
        assertTrue(a.prendre(1100, 220 * MS, s));
        assertEquals(110 * MS, s[0]);
        // celle d'avant (tir refusé avant d'être jugé) est oubliée, celle d'après reste
        assertTrue(a.prendre(1200, 220 * MS, s));
        assertEquals(210 * MS, s[0]);
        assertEquals(40 * MS, s[1]);
        assertFalse(a.prendre(1000, 220 * MS, s));
    }

    @Test
    void heureRecompteeApresUnRecalageReprendLaPlusAncienne() {
        ArriveesTirs a = new ArriveesTirs();
        a.noter(5_000_000, 10 * MS, 0);
        a.noter(5_000_100, 110 * MS, 0);
        long[] s = new long[2];
        assertTrue(a.prendre(42, 120 * MS, s)); // heure recomptée par la nouvelle base : ordre d'arrivée
        assertEquals(10 * MS, s[0]);
        assertTrue(a.prendre(142, 120 * MS, s));
        assertEquals(110 * MS, s[0]);
    }

    @Test
    void uneArriveeDePlusDeTroisSecondesNestPlusReprise() {
        ArriveesTirs a = new ArriveesTirs();
        a.noter(1000, 0, 0);
        long[] s = new long[2];
        assertFalse(a.prendre(1000, 3_100 * MS, s));
    }

    @Test
    void trenteDeuxArriveesAuPlusRienNeDeborde() {
        ArriveesTirs a = new ArriveesTirs();
        for (int i = 0; i < 100; i++) {
            a.noter(i, i * MS, 0);
        }
        long[] s = new long[2];
        assertTrue(a.prendre(99, 100 * MS, s));
        assertEquals(99 * MS, s[0]);
        assertFalse(a.prendre(50, 100 * MS, s)); // trop ancienne : remplacée
    }

    @Test
    void silenceDuReseauMesureLExcesSurCinquanteMs() {
        Silences s = new Silences();
        long t = 0;
        for (int i = 0; i < 40; i++) {
            t += 50 * MS; // une réponse par tick
            s.paquet(t);
        }
        assertEquals(0, s.recent(t, 1500 * MS));
        t += 430 * MS; // coupure : rien pendant 430 ms
        s.paquet(t);
        assertEquals(380 * MS, s.recent(t, 1500 * MS));
        for (int i = 0; i < 40; i++) {
            t += 50 * MS;
            s.paquet(t);
        }
        assertEquals(0, s.recent(t, 1500 * MS), "silence ancien encore compté");
    }

    @Test
    void petitsACoupsNeSontPasDesSilences() {
        Silences s = new Silences();
        long t = 0;
        for (int i = 0; i < 100; i++) {
            t += (i % 2 == 0 ? 75 : 25) * MS; // réponses groupées par deux
            s.paquet(t);
        }
        assertEquals(0, s.recent(t, 1500 * MS));
    }
}
