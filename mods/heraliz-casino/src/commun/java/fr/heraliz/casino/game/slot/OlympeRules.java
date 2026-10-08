package fr.heraliz.casino.game.slot;

/**
 * Reglages des "Portes de l'Olympe" (section "portes_olympe" de config/heralizcasino/casino.json).
 * Grille 6 x 5 qui paie partout (8 symboles identiques ou plus), cascades, orbes multiplicateurs, tours gratuits.
 */
public class OlympeRules {
    public long minBet = 5;
    public long maxBet = 2500;
    /** Poids des symboles : gemmes bleue, verte, jaune, violette, rouge, calice, anneau, sablier, couronne. */
    public int[] weights = {29, 26, 23, 19, 17, 13, 11, 9, 7};
    /** Gains (x la mise) pour 8-9, 10-11 et 12+ symboles identiques. */
    public double[][] pays = {
            {0.25, 0.75, 2},
            {0.4, 0.9, 4},
            {0.5, 1, 5},
            {0.8, 1.2, 8},
            {1, 1.5, 10},
            {1.5, 2, 12},
            {2, 5, 15},
            {2.5, 10, 25},
            {10, 25, 50},
    };
    /** Chance par case d'un Zeus (symbole de bonus), jeu normal / tours gratuits (tours gratuits 1 partie sur 430 environ). */
    public double scatter = 0.016;
    public double scatterFree = 0.016;
    /**
     * Chance par case d'un orbe multiplicateur, jeu normal / tours gratuits : comme dans le vrai jeu, un orbe dans un
     * tour sur 7 environ en jeu normal, dans un tour gratuit sur 1,7.
     */
    public double orb = 0.0045;
    public double orbFree = 0.025;
    /** Valeurs des orbes et leurs poids. */
    public int[] orbValues = {2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 50, 100, 250, 500};
    public int[] orbWeights = {300, 250, 200, 150, 120, 90, 70, 50, 35, 25, 15, 8, 3, 1, 1};
    /** Gains des Zeus (x la mise) pour 4, 5 et 6 ou plus. */
    public double[] scatterPays = {3, 5, 100};
    /** Tours gratuits, relance (3 Zeus pendant le bonus). */
    public int freeSpins = 15;
    public int retrigger = 5;
    public double maxWin = 5000;
    /** Achat des tours gratuits (x la mise) ; 0 = desactive. */
    public double buyFree = 100;
    /** Multiplicateur global des gains (1 = table des gains du vrai jeu ; reglage fin de la redistribution). */
    public double payScale = 1.0;

    public OlympeRules sanitized() {
        OlympeRules d = new OlympeRules();
        if (weights == null || weights.length != 9) weights = d.weights;
        if (pays == null || pays.length != 9) pays = d.pays;
        for (int i = 0; i < 9; i++) if (pays[i] == null || pays[i].length != 3) pays[i] = d.pays[i];
        if (orbValues == null || orbWeights == null || orbValues.length == 0 || orbValues.length != orbWeights.length || orbValues.length > 15) {
            orbValues = d.orbValues;
            orbWeights = d.orbWeights;
        }
        if (scatterPays == null || scatterPays.length != 3) scatterPays = d.scatterPays;
        scatter = clamp(scatter, 0, 0.3);
        scatterFree = clamp(scatterFree, 0, 0.3);
        orb = clamp(orb, 0, 0.3);
        orbFree = clamp(orbFree, 0, 0.3);
        freeSpins = Math.max(1, Math.min(50, freeSpins));
        retrigger = Math.max(0, Math.min(50, retrigger));
        maxWin = Math.max(10, maxWin);
        buyFree = Math.max(0, buyFree);
        payScale = clamp(payScale, 0.1, 5);
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
