package fr.heraliz.casino.game.mines;

/**
 * Le Demineur : 25 cases, le joueur choisit le nombre de mines ; chaque case sure augmente le gain,
 * on peut encaisser a tout moment. Gain = redistribution x produit des chances (quelle que soit la strategie,
 * le joueur recupere en moyenne "rtp" de sa mise).
 */
public class MinesRules {
    public long minBet = 1;
    public long maxBet = 2000;
    /** Redistribution (0.95 = la maison garde 5 %). */
    public double rtp = 0.95;
    /** Encaissement automatique apres cette inactivite (secondes). */
    public int idleSeconds = 60;

    public static final int TILES = 25;

    public MinesRules sanitized() {
        rtp = Math.max(0.5, Math.min(0.99, rtp));
        idleSeconds = Math.max(15, Math.min(600, idleSeconds));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    /** Multiplicateur apres "revealed" cases sures avec "mines" mines. */
    public double multiplier(int mines, int revealed) {
        if (revealed <= 0) return 1.0;
        double m = 1.0;
        for (int i = 0; i < revealed; i++) {
            int left = TILES - i, safe = TILES - mines - i;
            if (safe <= 0) return 0;
            m *= (double) left / safe;
        }
        return Math.floor(m * rtp * 100.0) / 100.0;
    }
}
