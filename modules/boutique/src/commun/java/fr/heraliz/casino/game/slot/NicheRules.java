package fr.heraliz.casino.game.slot;

/**
 * Reglages de "La Niche" (section "la_niche" de config/heralizcasino/casino.json), sur le modele de The Dog House
 * Multihold : 5 rouleaux x 3 rangees, 20 lignes, WILD x2 / x3 (rouleaux 2 a 4, multiplicateurs additionnes), BONUS
 * (rouleaux 1, 3 et 5 : 3 = 7 tours gratuits et 2 fois la mise), jusqu'a 4 grilles, wilds collants copies vers les
 * grilles debloquees.
 */
public class NicheRules {
    public long minBet = 5;
    public long maxBet = 2500;
    /** Poids par rouleau : 10, J, Q, K, A, os, collier, chien 1 a 4. */
    public int[][] weights = {
            {30, 30, 30, 24, 24, 16, 13, 10, 8, 6, 4},
            {30, 30, 30, 24, 24, 16, 13, 10, 8, 6, 4},
            {30, 30, 30, 24, 24, 16, 13, 10, 8, 6, 4},
            {30, 30, 30, 24, 24, 16, 13, 10, 8, 6, 4},
            {30, 30, 30, 24, 24, 16, 13, 10, 8, 6, 4},
    };
    /** Gain d'une ligne (x la mise totale) pour 3, 4 et 5 symboles : 10, J, Q, K, A, os, collier, chiens 1 a 4. */
    public double[][] pays = {
            {0.1, 0.25, 1.25},
            {0.1, 0.25, 1.25},
            {0.1, 0.25, 1.25},
            {0.25, 0.5, 2.5},
            {0.25, 0.5, 2.5},
            {0.4, 0.75, 3.75},
            {0.6, 1, 5},
            {0.75, 1.5, 7.5},
            {1, 2.5, 12.5},
            {1.5, 3.75, 17.5},
            {2, 5, 25},
    };
    /** Chance par case d'un WILD (rouleaux 2 a 4) en jeu normal, et sur la derniere grille pendant les tours gratuits. */
    public double wild = 0.075;
    public double freeWild = 0.055;
    /** Poids des multiplicateurs des wilds : x2, x3. */
    public int[] wildMults = {65, 35};
    /** Chance par rouleau (1, 3, 5) d'un BONUS en jeu normal, et sur la derniere grille pendant les tours gratuits. */
    public double bonus = 0.17;
    public double freeBonus = 0.45;
    /** Tours gratuits au depart ; poids de +1, +2, +3 tours a chaque grille debloquee. */
    public int freeSpins = 7;
    public int[] unlockSpins = {40, 35, 25};
    /** Gain immediat du declenchement (x la mise). */
    public double scatterPay = 2;
    public double maxWin = 6750;
    /** Achat des tours gratuits (x la mise) ; 0 = desactive. */
    public double buyFree = 80;
    public double payScale = 1.0;

    public NicheRules sanitized() {
        NicheRules d = new NicheRules();
        if (weights == null || weights.length != Niche.REELS) weights = d.weights;
        for (int i = 0; i < Niche.REELS; i++) if (weights[i] == null || weights[i].length != Niche.PAY_SYMBOLS || !positive(weights[i])) weights[i] = d.weights[i];
        if (pays == null || pays.length != Niche.PAY_SYMBOLS) pays = d.pays;
        for (int i = 0; i < Niche.PAY_SYMBOLS; i++) if (pays[i] == null || pays[i].length != 3) pays[i] = d.pays[i];
        if (wildMults == null || wildMults.length != 2 || !positive(wildMults)) wildMults = d.wildMults;
        if (unlockSpins == null || unlockSpins.length != 3 || !positive(unlockSpins)) unlockSpins = d.unlockSpins;
        wild = clamp(wild, 0, 0.6);
        freeWild = clamp(freeWild, 0, 0.6);
        bonus = clamp(bonus, 0, 1);
        freeBonus = clamp(freeBonus, 0, 1);
        freeSpins = Math.max(1, Math.min(30, freeSpins));
        scatterPay = clamp(scatterPay, 0, 1000);
        maxWin = Math.max(10, maxWin);
        buyFree = Math.max(0, buyFree);
        payScale = clamp(payScale, 0.1, 5);
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    /** Poids ramenes entre 0 et 1000 ; false si tous nuls. */
    private static boolean positive(int[] w) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(0, Math.min(1000, w[i]));
            sum += w[i];
        }
        return sum > 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
