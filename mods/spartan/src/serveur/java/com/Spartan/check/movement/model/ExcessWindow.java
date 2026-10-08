package com.Spartan.check.movement.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dépassements d'une enveloppe sur ~1 s de temps réel (Java pur, testé).
 *
 * Seuls les dépassements comptent : aller lentement un moment n'« économise » rien
 * pour tricher ensuite (l'enveloppe elle-même retombe quand on ralentit). Une
 * petite tolérance absorbe ce que le modèle ne voit pas (bloc fantôme corrigé avec
 * le ping, poussée imprévue…) ; au-delà, c'est signalé et la fenêtre repart de zéro.
 */
public final class ExcessWindow {
    public static final int SPAN_TICKS = 20;

    private final Deque<double[]> entries = new ArrayDeque<>(); // {excès, ticks}
    private int ticks;
    private double sum;

    public void clear() {
        entries.clear();
        ticks = 0;
        sum = 0;
    }

    /**
     * @param excess    dépassement de l'échantillon (≥ 0)
     * @param t         ticks couverts (≥ 1)
     * @param tolerance dépassement cumulé toléré sur la fenêtre
     * @return le dépassement cumulé s'il franchit la tolérance (la fenêtre est alors vidée), sinon 0
     */
    public double add(double excess, int t, double tolerance) {
        int span = Math.max(1, t);
        double e = Math.max(0, excess);
        entries.addLast(new double[] {e, span});
        ticks += span;
        sum += e;
        while (entries.size() > 1 && ticks - (int) entries.peekFirst()[1] >= SPAN_TICKS) {
            double[] old = entries.removeFirst();
            ticks -= (int) old[1];
            sum -= old[0];
        }
        if (sum > tolerance) {
            double total = sum;
            clear();
            return total;
        }
        return 0;
    }

    public double sum() {
        return sum;
    }
}
