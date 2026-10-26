package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.slot.Loup;
import fr.heraliz.casino.game.slot.LoupRules;

import java.util.List;

/**
 * Etat du Souffle du Loup lu par le modele 3D : la partie en cours (sequence complete) et le temps ecoule.
 */
public final class LoupView {
    public Loup.Sequence seq = new Loup.Sequence();
    /** Ticks depuis le lancement de la partie. */
    public float since = 1e6f;
    /** Ticks depuis l'intro du joueur local (il vient de s'installer), 1e6 sinon. */
    public float intro = 1e6f;
    public long bet;
    public LoupRules rules = new LoupRules();
    private Loup.Sequence timedFor;
    private int[] starts = new int[0];
    private int total;
    private Loup.Plan[] plans = new Loup.Plan[0];
    private List<?>[] hits = new List<?>[0];

    private void time() {
        if (timedFor == seq) return;
        int n = seq.spins.size();
        starts = new int[n + 1];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = acc;
            acc += Loup.duration(seq.spins.get(i));
        }
        starts[n] = acc;
        total = acc;
        plans = new Loup.Plan[n];
        hits = new List<?>[n];
        timedFor = seq;
    }

    /** Position dans la partie : {indice de l'etape, temps local} ou null (aucune partie). */
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

    /** Mise en place de l'etape i (calculee une fois). */
    public Loup.Plan plan(int i) {
        time();
        if (plans[i] == null) plans[i] = Loup.plan(seq.spins.get(i));
        return plans[i];
    }

    /** Coups des chapeaux du tour gratuit i (calcules une fois). */
    @SuppressWarnings("unchecked")
    public List<int[]> freeHits(int i) {
        time();
        if (hits[i] == null) hits[i] = Loup.freeHits(seq.spins.get(i));
        return (List<int[]>) hits[i];
    }

    /** Gain cumule (millemes x mise) des etapes avant i, plus l'etape i si elle est terminee. */
    public long winSoFar(int i, boolean includeCurrent) {
        long w = 0;
        for (int k = 0; k < i && k < seq.spins.size(); k++) w += seq.spins.get(k).winMilli;
        if (includeCurrent && i < seq.spins.size()) w += seq.spins.get(i).winMilli;
        return Math.min(w, seq.totalMilli > 0 ? seq.totalMilli : w);
    }
}
