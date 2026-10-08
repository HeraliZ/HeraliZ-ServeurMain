package fr.heraliz.casino.game.plinko;

/**
 * Plinko : la bille rebondit sur 12 rangees de clous (gauche ou droite, une chance sur deux) et tombe dans
 * l'un des 13 paniers. Les paniers du centre (les plus probables) rendent moins que la mise :
 * on perd 85 % du temps, les bords rapportent gros mais tres rarement. Redistribution ~87,8 %.
 */
public class PlinkoRules {
    public long minBet = 1;
    public long maxBet = 1000;
    /** Multiplicateurs des 13 paniers (de gauche a droite). */
    public double[] multipliers = {80, 16, 5, 2, 0.7, 0.4, 0.2, 0.4, 0.7, 2, 5, 16, 80};
    /** Billes en vol au maximum par joueur. */
    public int maxBallsPerPlayer = 8;

    public static final int ROWS = 12;

    public PlinkoRules sanitized() {
        if (multipliers == null || multipliers.length != ROWS + 1) multipliers = new PlinkoRules().multipliers;
        for (int i = 0; i < multipliers.length; i++) multipliers[i] = Math.max(0, multipliers[i]);
        maxBallsPerPlayer = Math.max(1, Math.min(20, maxBallsPerPlayer));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    /** Redistribution theorique (loi binomiale). */
    public double rtp() {
        double ev = 0;
        for (int k = 0; k <= ROWS; k++) ev += binom(ROWS, k) / Math.pow(2, ROWS) * multipliers[k];
        return ev;
    }

    public static double binom(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }
}
