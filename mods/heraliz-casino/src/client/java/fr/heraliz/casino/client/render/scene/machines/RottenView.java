package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.RottenRules;

/**
 * Etat de la machine Rotten lu par son modele 3D : la partie en cours (sequence complete) et le temps ecoule.
 */
public final class RottenView {
    public Rotten.Sequence seq = new Rotten.Sequence();
    /** Ticks depuis le lancement de la partie. */
    public float since = 1e6f;
    /** Ticks depuis l'intro du joueur local (il vient de s'installer), 1e6 sinon. */
    public float intro = 1e6f;
    public long bet, win, cost;
    public String player = "";
    public RottenRules rules = new RottenRules();
    /** Debut de chaque tour (ticks), calcule une fois par sequence. */
    private Rotten.Sequence timedFor;
    private int[] starts = new int[0];
    private int total;

    /** Position dans la partie : {indice du tour, temps local} ou null (aucune partie / terminee depuis longtemps). */
    public float[] at(float t) {
        if (seq.spins.isEmpty() || t < 0) return null;
        if (timedFor != seq) {
            starts = new int[seq.spins.size() + 1];
            int acc = 0;
            for (int i = 0; i < seq.spins.size(); i++) {
                starts[i] = acc;
                acc += Rotten.duration(seq.spins.get(i));
            }
            starts[seq.spins.size()] = acc;
            total = acc;
            timedFor = seq;
        }
        if (t >= total) return new float[]{seq.spins.size() - 1, t - starts[seq.spins.size() - 1]};
        int i = 0;
        while (i < seq.spins.size() - 1 && t >= starts[i + 1]) i++;
        return new float[]{i, t - starts[i]};
    }

    public int totalTicks() {
        at(0);
        return total;
    }

    /** Gain cumule (millemes x mise) des tours termines avant le tour i, plus le tour i s'il est pose. */
    public long winSoFar(int i, boolean includeCurrent) {
        long w = 0;
        for (int k = 0; k < i && k < seq.spins.size(); k++) w += seq.spins.get(k).winMilli;
        if (includeCurrent && i < seq.spins.size()) w += seq.spins.get(i).winMilli;
        return w;
    }
}
