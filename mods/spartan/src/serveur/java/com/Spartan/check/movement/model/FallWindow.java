package com.Spartan.check.movement.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fenêtre de descente de la détection Fly, en TEMPS (ticks couverts) et non en
 * nombre de paquets : un client qui n'envoie sa position que rarement ne peut
 * pas étirer la fenêtre pour planer.
 *
 * En l'air et sans cause légitime, un joueur descend. Sur ~1 s :
 * - trajectoire balistique (la vitesse verticale décroît sous l'effet de la
 *   gravité : saut, chute, rampe de lancement) → jamais signalée ;
 * - sinon la descente nette doit atteindre minFall × durée.
 */
public final class FallWindow {
    public static final int SPAN_TICKS = 20;

    /** Résultat d'un ajout : flag=true si la fenêtre montre un vol / hovering. */
    public record Verdict(boolean flag, double net, double required, int ticks) {
        static final Verdict NONE = new Verdict(false, 0, 0, 0);
    }

    private final Deque<double[]> entries = new ArrayDeque<>(); // {dy, ticks}
    private int ticks;

    public void clear() {
        entries.clear();
        ticks = 0;
    }

    /**
     * @param dy               déplacement vertical de l'échantillon
     * @param t                ticks couverts par l'échantillon (≥ 1)
     * @param minFallPerSecond descente minimale attendue (blocs/s)
     * @param gravity          gravité du joueur (blocs/tick²)
     */
    public Verdict add(double dy, int t, double minFallPerSecond, double gravity) {
        int span = Math.max(1, t);
        entries.addLast(new double[] {dy, span});
        ticks += span;
        while (entries.size() > 1 && ticks - (int) entries.peekFirst()[1] >= SPAN_TICKS) {
            ticks -= (int) entries.removeFirst()[1];
        }
        if (ticks < SPAN_TICKS) {
            return Verdict.NONE;
        }
        double net = 0;
        for (double[] e : entries) {
            net += e[0];
        }
        // Balistique : vitesse par tick du premier et du dernier échantillon.
        double[] first = entries.peekFirst();
        double[] last = entries.peekLast();
        double vFirst = first[0] / first[1];
        double vLast = last[0] / last[1];
        double elapsed = Math.max(1, ticks - first[1]);
        double acceleration = (vLast - vFirst) / elapsed;
        if (acceleration < -0.375 * gravity) {
            return Verdict.NONE;
        }
        double required = minFallPerSecond * ticks / 20.0;
        if (net > -required) {
            Verdict v = new Verdict(true, net, required, ticks);
            // Ré-alerte au bout d'une demi-fenêtre si le vol continue.
            while (!entries.isEmpty() && ticks > SPAN_TICKS / 2) {
                ticks -= (int) entries.removeFirst()[1];
            }
            return v;
        }
        return Verdict.NONE;
    }
}
