package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.slot.Niche;
import fr.heraliz.casino.game.slot.NicheRules;

/**
 * Etat de La Niche lu par le modele 3D : la partie en cours (sequence complete) et le temps ecoule.
 */
public final class NicheView {
    public Niche.Sequence seq = new Niche.Sequence();
    /** Ticks depuis le lancement de la partie. */
    public float since = 1e6f;
    /** Ticks depuis l'intro du joueur local (il vient de s'installer), 1e6 sinon. */
    public float intro = 1e6f;
    public long bet;
    public NicheRules rules = new NicheRules();
    private Niche.Sequence timedFor;
    private int[] starts = new int[0];
    private int total;

    private void time() {
        if (timedFor == seq) return;
        int n = seq.spins.size();
        starts = new int[n + 1];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = acc;
            acc += Niche.duration(seq.spins.get(i));
        }
        starts[n] = acc;
        total = acc;
        timedFor = seq;
    }

    /** Position dans la partie : {indice du tour, temps local} ou null (aucune partie). */
    public float[] at(float t) {
        if (seq.spins.isEmpty() || t < 0) return null;
        time();
        if (t >= total) return new float[]{seq.spins.size() - 1, t - starts[seq.spins.size() - 1]};
        int i = 0;
        while (i < seq.spins.size() - 1 && t >= starts[i + 1]) i++;
        return new float[]{i, t - starts[i]};
    }

    public int totalTicks() {
        time();
        return total;
    }

    /** Gain cumule (millemes x mise) des tours avant i, plus le tour i s'il est termine. */
    public long winSoFar(int i, boolean includeCurrent) {
        long w = 0;
        for (int k = 0; k < i && k < seq.spins.size(); k++) w += seq.spins.get(k).winMilli;
        if (includeCurrent && i < seq.spins.size()) w += seq.spins.get(i).winMilli;
        return Math.min(w, seq.totalMilli > 0 ? seq.totalMilli : w);
    }
}
