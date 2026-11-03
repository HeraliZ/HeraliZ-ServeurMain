package com.Spartan.check.movement.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Spartan.check.combat.StatsTirs;
import org.junit.jupiter.api.Test;

/**
 * Détections serveur de l'audit du 02/11 (point 14) : vitesse soutenue, latence trafiquée, statistiques de tir. Un
 * joueur honnête (sprint parfait, lag, gel du jeu, bon tireur) n'est jamais signalé ; un tricheur réglé « juste en
 * dessous » des anciennes marges l'est.
 */
class Point14Test {
    private static final double SPRINT = 0.2806; // maximum d'un sprint au sol par tick (vanilla)

    @Test
    void sprintParfaitJamaisSignale() {
        VitesseSoutenue v = new VitesseSoutenue();
        for (int t = 0; t < 2000; t++) {
            // le joueur va exactement au maximum, avec des écarts de ±0,03 qui se compensent
            double reel = SPRINT + (t % 2 == 0 ? 0.03 : -0.03);
            assertEquals(-1, v.add(reel, SPRINT, 1, 100, 10, 0.10), 1e-9, "tick " + t);
        }
    }

    @Test
    void speedHackSousLaMargeParTickSignale() {
        // +15 % : 0,323 par tick, sous l'ancienne marge (0,2806 + 0,06 = 0,34) : jamais vu par Speed
        VitesseSoutenue v = new VitesseSoutenue();
        double vu = -1;
        int t = 0;
        while (vu < 0 && t < 200) {
            vu = v.add(SPRINT * 1.15, SPRINT, 1, 100, 10, 0.10);
            t++;
        }
        assertTrue(vu > 0.14 && vu < 0.16, "dépassement mesuré " + vu);
        assertEquals(100, t, "jugé dès que la fenêtre de 5 s est pleine");
        assertEquals(100, v.constatTicks());
        // presque immobile : jamais jugé
        VitesseSoutenue lent = new VitesseSoutenue();
        for (int k = 0; k < 300; k++) {
            assertEquals(-1, lent.add(0.05, 0.02, 1, 100, 10, 0.10), 1e-9);
        }
    }

    /** Rejoue un joueur vanilla simulé (VanillaWalker) dans l'enveloppe, comme SpeedCheck, sur la vitesse soutenue. */
    private static double pireRatio(double triche) {
        VanillaWalker w = new VanillaWalker();
        HorizontalEnvelope h = new HorizontalEnvelope();
        VitesseSoutenue v = new VitesseSoutenue();
        double pire = -1;
        for (int i = 0; i < 1200; i++) {
            w.yaw += (i % 50 == 0) ? 0.7 : 0.01; // virages
            boolean saute = i % 40 >= 20;          // sprint, puis sprint en sautant
            VanillaWalker.Step s = w.tick(1, i % 30 < 15 ? 1 : 0, true, saute, false);
            boolean jump = s.groundStart() && s.dy() > 0.15 && s.dy() < 0.47;
            double reel = s.horizontal() * triche;
            double max = h.advance(new HorizontalEnvelope.Tick(s.groundStart(), false, 0.6, 0.13, jump, false, 0, 0, 0), 1,
                    reel);
            pire = Math.max(pire, v.add(reel, max, 1, 100, 10, 0.0));
        }
        return pire;
    }

    @Test
    void joueurVanillaSimuleJamaisAuDessusDuMaximum() {
        // seuil à 0 % : un joueur vanilla (sprint, sauts, virages) ne dépasse jamais le maximum physique sur 5 s
        assertEquals(-1, pireRatio(1.0), 1e-9);
        // le même joueur 25 % trop vite (virages et pas de côté compris) dépasse le seuil par défaut (+12 %)
        assertTrue(pireRatio(1.25) > 0.12, "speed hack +25 % : " + pireRatio(1.25));
    }

    @Test
    void rafalesApresUnLagCompteesParTicks() {
        VitesseSoutenue v = new VitesseSoutenue();
        for (int k = 0; k < 100; k++) {
            // 5 ticks d'un coup (rafale réseau) : distance et maximum de 5 ticks
            assertEquals(-1, v.add(SPRINT * 5, SPRINT * 5, 5, 100, 10, 0.10), 1e-9);
        }
    }

    @Test
    void fauxLagSignaleMaisJamaisUnVraiLagNiUnGel() {
        LatenceSuivi l = new LatenceSuivi();
        int lissee = 40;
        // joueur honnête : transactions à 60 ms, keepalive à 40 ms ; puis un gel de 10 s (les deux le voient)
        for (int s = 0; s < 300; s++) {
            boolean gel = s >= 100 && s < 110;
            l.pong((gel ? 10_000 : 60) * 1_000_000L);
            if (s % 15 == 0) {
                int mesure = s == 105 ? 10_000 : 40;
                lissee = (lissee * 3 + mesure) / 4;
            }
            assertEquals(0, l.seconde(lissee, 250, 300), "seconde " + s);
        }
        // faux lag : keepalive retenu 800 ms, transactions toujours à 60 ms
        LatenceSuivi f = new LatenceSuivi();
        int lf = 40;
        int max = 0;
        for (int s = 0; s < 120; s++) {
            f.pong(60 * 1_000_000L);
            if (s % 15 == 0 && s > 0) {
                lf = (lf * 3 + 840) / 4;
            }
            max = Math.max(max, f.seconde(lf, 250, 300));
        }
        assertTrue(max >= 3, "mesures suspectes de suite : " + max);
        assertTrue(f.derniereMesure() > 700, "mesure retrouvée " + f.derniereMesure());
        // les marges de Spartan ne suivent plus la latence gonflée
        assertEquals(160, f.effective(lf));
        assertEquals(40, l.effective(40));
    }

    @Test
    void statistiquesDeTir() {
        StatsTirs s = new StatsTirs(40);
        for (int k = 0; k < 30; k++) {
            s.tir();
            s.touche(k % 3 == 0, 25, false); // bon tireur : un tir sur trois à la tête
        }
        assertEquals(30, s.touches());
        assertEquals(30, s.touchesLoin(10));
        assertEquals(10, s.tetesLoin(10));
        assertEquals(0, s.coupsSecs());
        for (int k = 0; k < 40; k++) {
            s.touche(true, 40, k % 2 == 0); // aimbot : toujours la tête, souvent après un coup sec
        }
        assertEquals(40, s.touches(), "fenêtre de 40 touches");
        assertEquals(40, s.tetesLoin(10));
        assertEquals(20, s.coupsSecs());
        s.oublierTouches();
        assertEquals(0, s.touches());
        // refus de TaCZ : comptés sur une fenêtre de temps, cadence et horloge à part (03/11)
        int n = 0;
        for (int k = 0; k < 20; k++) {
            n = s.refus(true, 1000 + k * 100L, 10_000);
        }
        assertEquals(20, n);
        assertEquals(1, s.refus(false, 3000, 10_000), "les refus d'horloge ne comptent pas dans la cadence");
        assertEquals(1, s.refus(true, 60_000, 10_000), "les anciens refus sortent de la fenêtre");
        s.oublierRefus(true);
        assertEquals(1, s.refus(true, 60_100, 10_000), "après une alerte, la cadence repart de zéro");
        assertEquals(1, s.refus(false, 60_100, 10_000), "les refus d'horloge de 3 s sont sortis de la fenêtre");
        assertTrue(s.resume().contains("70 touche(s)"), s.resume());
    }

    @Test
    void partDesTirsRefusesPourCadence() {
        // minigun d'un joueur honnête : 20 tirs acceptés par seconde pendant 10 s, quelques refus
        StatsTirs honnete = new StatsTirs(40);
        for (int k = 0; k < 200; k++) {
            honnete.tir(100_000 + k * 50L);
        }
        assertEquals(200, honnete.tirsRecents(109_999, 10_000));
        assertEquals(0, honnete.tirsRecents(200_000, 10_000), "les anciens tirs sortent de la fenêtre");
        int n = 0;
        for (int k = 0; k < 15; k++) {
            n = honnete.refus(true, 105_000 + k * 300L, 10_000);
        }
        double part = (double) n / (n + honnete.tirsRecents(109_500, 10_000));
        assertEquals(15, n);
        assertTrue(part < 0.2, "15 refus sur 215 tirs : pas d'alerte (" + part + ")");
        // « rapid fire » : deux fois la cadence d'un fusil à 600 coups/min, un tir sur deux refusé
        StatsTirs triche = new StatsTirs(40);
        n = 0;
        for (int k = 0; k < 40; k++) {
            long t = 300_000 + k * 50L;
            if (k % 2 == 0) {
                triche.tir(t);
            } else {
                n = triche.refus(true, t, 10_000);
            }
        }
        part = (double) n / (n + triche.tirsRecents(302_000, 10_000));
        assertEquals(20, n);
        assertTrue(part >= 0.2, "un tir sur deux refusé : alerte (" + part + ")");
    }
}
