package com.Spartan.check.movement.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Scénarios de mouvement rejoués sur les modèles purs : un client honnête (sauts,
 * chutes, parachute, lag + rafale) ne doit JAMAIS être signalé ; les schémas de
 * triche connus doivent l'être.
 */
class MovementModelTest {
    private static final double MIN_FALL = 0.4;
    private static final double GEAR_MIN_FALL = 0.05;
    private static final double G = Physics.GRAVITY;
    private static final long TICK = 50_000_000L;

    /** dy par tick d'un saut vanilla depuis le sol jusqu'au retour au sol. */
    private static List<Double> jumpArc(double v0) {
        List<Double> out = new ArrayList<>();
        double y = 0;
        double v = v0;
        while (true) {
            double ny = y + v;
            if (ny <= 0) {
                out.add(-y);
                return out;
            }
            out.add(v);
            y = ny;
            v = (v - G) * Physics.DRAG;
        }
    }

    private static int flags(FallWindow w, List<Double> dys, int ticksEach, double minFall) {
        int n = 0;
        for (double dy : dys) {
            if (w.add(dy, ticksEach, minFall, G).flag()) {
                n++;
            }
        }
        return n;
    }

    @Test
    void jumpHeightMatchesVanilla() {
        assertEquals(1.2522, Physics.jumpHeight(0.42, G), 0.001);
        assertTrue(Physics.jumpHeight(0.62, G) > 2.4, "Saut amélioré II");
    }

    @Test
    void ticksCoveredIgnoresJitterAndCapsGaps() {
        assertEquals(1, Physics.ticksCovered(0));
        assertEquals(1, Physics.ticksCovered(120_000_000L));
        assertEquals(4, Physics.ticksCovered(200_000_000L));
        assertEquals(5, Physics.ticksCovered(10_000_000_000L));
    }

    @Test
    void honestFallsAndJumpsAreNeverFlagged() {
        FallWindow w = new FallWindow();
        List<Double> fall = new ArrayList<>();
        double v = 0;
        for (int i = 0; i < 60; i++) {
            v = (v - G) * Physics.DRAG;
            fall.add(v);
        }
        assertEquals(0, flags(w, fall, 1, MIN_FALL), "chute libre");
        // Sauts et bunny-hop : chaque arc se termine au sol (fenêtre vidée par le moteur) ;
        // même sans vidage, un arc reste balistique.
        w.clear();
        assertEquals(0, flags(w, jumpArc(0.42), 1, MIN_FALL), "saut");
        w.clear();
        assertEquals(0, flags(w, jumpArc(0.72), 1, MIN_FALL), "saut amélioré III");
    }

    @Test
    void parachuteAndGliderDescentsPass() {
        FallWindow w = new FallWindow();
        assertEquals(0, flags(w, repeat(-0.12, 200), 1, MIN_FALL), "parachute 2.4 b/s");
        w.clear();
        assertEquals(0, flags(w, repeat(-0.03, 200), 1, GEAR_MIN_FALL), "planeur très lent avec équipement");
    }

    @Test
    void honestLagBurstDoesNotInflateRequiredDescent() {
        // Parachute (-0.12/tick) : client figé 2 s puis rafale de 10 positions.
        FallWindow w = new FallWindow();
        int n = flags(w, repeat(-0.12, 30), 1, MIN_FALL);
        n += w.add(-0.12, Physics.ticksCovered(2_000_000_000L), MIN_FALL, G).flag() ? 1 : 0;
        n += flags(w, repeat(-0.12, 40), 1, MIN_FALL);
        assertEquals(0, n);
    }

    @Test
    void hoverAndLevelFlightAreFlagged() {
        FallWindow w = new FallWindow();
        assertTrue(flags(w, repeat(0.0, 25), 1, MIN_FALL) > 0, "vol à altitude constante");
        w.clear();
        List<Double> jitter = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            jitter.add(i % 2 == 0 ? 0.02 : -0.02);
        }
        assertTrue(flags(w, jitter, 1, MIN_FALL) > 0, "hovering avec micro-oscillations");
        w.clear();
        assertTrue(flags(w, repeat(0.1, 25), 1, MIN_FALL) > 0, "montée continue");
    }

    @Test
    void sparsePositionsCannotStretchTheWindow() {
        // Une position toutes les ~1 s, presque à plat : chaque échantillon couvre 5 ticks.
        FallWindow w = new FallWindow();
        assertTrue(flags(w, repeat(-0.02, 10), Physics.ticksCovered(1_000_000_000L), MIN_FALL) > 0);
    }

    @Test
    void fakeBallisticArcsInMidAirAreFlagged() {
        // Petits « sauts » répétés en l'air (+0.12 → -0.12) : jamais balistique sur 1 s.
        FallWindow w = new FallWindow();
        List<Double> arcs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            arcs.add(0.12);
            arcs.add(0.04);
            arcs.add(-0.04);
            arcs.add(-0.12);
        }
        assertTrue(flags(w, arcs, 1, MIN_FALL) > 0);
    }

    @Test
    void lowGravityJumpIsBallisticNotFlight() {
        double g = 0.0133;
        FallWindow w = new FallWindow();
        int n = 0;
        double v = 0.42;
        for (int i = 0; i < 60; i++) {
            n += w.add(v, 1, MIN_FALL * g / G, g).flag() ? 1 : 0;
            v = (v - g) * Physics.DRAG;
        }
        assertEquals(0, n);
    }

    @Test
    void timerHonestClientWithLagAndJitterIsNeverFlagged() {
        TimerBalance t = new TimerBalance();
        long now = 0;
        boolean flagged = false;
        for (int i = 0; i < 2000; i++) {
            // gigue : alternance 20 ms / 80 ms (moyenne 50 ms)
            now += i % 2 == 0 ? 20_000_000L : 80_000_000L;
            flagged |= t.onPacket(now, true);
        }
        // gel de 3 s puis rafale de 10 paquets (le client vanilla rattrape 10 ticks max)
        now += 3_000_000_000L;
        for (int i = 0; i < 10; i++) {
            now += 1_000_000L;
            flagged |= t.onPacket(now, true);
        }
        for (int i = 0; i < 400; i++) {
            now += TICK;
            flagged |= t.onPacket(now, true);
        }
        assertFalse(flagged);
    }

    @Test
    void timerCatchesAcceleratedClients() {
        assertTrue(secondsToFlag(2.0) < 4.0, "Timer x2");
        assertTrue(secondsToFlag(1.2) < 20.0, "Timer x1.2");
    }

    private static double secondsToFlag(double speed) {
        TimerBalance t = new TimerBalance();
        long now = 0;
        long step = (long) (TICK / speed);
        for (int i = 1; i < 100_000; i++) {
            now += step;
            if (t.onPacket(now, true)) {
                return now / 1e9;
            }
        }
        return Double.MAX_VALUE;
    }

    @Test
    void timerDoesNotChargeTeleportReplies() {
        TimerBalance t = new TimerBalance();
        long now = 0;
        boolean flagged = false;
        for (int i = 0; i < 4000; i++) {
            now += TICK;
            flagged |= t.onPacket(now, true);
            if (i % 20 == 0) {
                flagged |= t.onPacket(now + 1, false); // réponse à une téléportation chaque seconde
            }
        }
        assertFalse(flagged);
    }

    private static List<Double> repeat(double v, int n) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(v);
        }
        return out;
    }
}
