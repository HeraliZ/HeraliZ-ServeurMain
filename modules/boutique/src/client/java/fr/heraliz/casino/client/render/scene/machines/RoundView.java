package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat commun des jeux en tours (Grande Roue, Roulette, Course, Crash) lu par les modeles 3D.
 */
public class RoundView {
    public static final int IDLE = 0, BETTING = 1, RUNNING = 2, RESULT = 3;

    public int phase = IDLE;
    /** Ticks depuis le debut de la phase (fraction comprise). */
    public float sincePhase;
    /** Secondes restantes pour miser. */
    public float countdown;
    /** Total mise par case. */
    public long[] totals = new long[64];
    /** Mise du joueur local par case. */
    public long[] mine = new long[64];
    public int history[] = new int[0];

    public void clearTotals() {
        java.util.Arrays.fill(totals, 0);
        java.util.Arrays.fill(mine, 0);
    }
}
