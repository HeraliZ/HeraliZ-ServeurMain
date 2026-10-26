package fr.heraliz.casino.game.slot;

/**
 * Reglages de la machine a sous "Rotten" (section "rotten" de config/heralizcasino/casino.json).
 * Grille de 6 rouleaux x 5 rangees, 35 lignes. Les gains sont exprimes en "x la mise totale".
 */
public class RottenRules {
    public long minBet = 5;
    public long maxBet = 2500;
    /**
     * Poids des symboles ordinaires par rouleau : 10, J, Q, K, A, zombie vert, bleu, jaune, rouge, clown, WILD.
     * (le WILD n'apparait pas sur le premier rouleau)
     */
    public int[][] weights = {
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 0},
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 3},
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 3},
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 3},
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 3},
            {26, 26, 24, 22, 20, 11, 10, 9, 8, 5, 3},
    };
    /** Gains d'une ligne (x la mise) pour 3, 4, 5 et 6 symboles, par symbole (10 ... clown). */
    public double[][] pays = {
            {0.3, 0.8, 1.6, 3},
            {0.3, 0.8, 1.6, 3},
            {0.4, 1, 2, 4},
            {0.5, 1.2, 2.5, 5},
            {0.6, 1.5, 3, 6},
            {0.8, 2, 5, 10},
            {1, 2.5, 6, 12},
            {1.2, 3, 8, 16},
            {1.5, 4, 10, 20},
            {2, 5, 12, 30},
    };
    /** Chance (par rouleau et par tour) d'un symbole Savant Fou / Invasion Totale, jeu normal. */
    public double scatterSavant = 0.055;
    public double scatterInvasion = 0.022;
    /** Idem pendant les tours gratuits (relance du bonus). */
    public double scatterSavantFree = 0.04;
    public double scatterInvasionFree = 0.018;
    /** Chance d'un symbole MUTATION par tour (jeu normal / Savant Fou / Invasion Totale / relances). */
    public double mutation = 0.03;
    public double mutationSavant = 0.5;
    public double mutationInvasion = 0.13;
    public double mutationRespin = 0.06;
    /** Nombre de relances d'une serie MUTATION : poids pour 1, 2, ... 10 relances. */
    public int[] respins = {35, 26, 15, 9, 6, 4, 2, 1, 1, 1};
    /** Nombre de symboles transformes a chaque MUTATION : poids pour 1, 2, 3 (jeu normal / tours gratuits). */
    public int[] switchCount = {85, 13, 2};
    public int[] switchCountFree = {50, 35, 15};
    /** Poids des symboles choisis pour muter (10 ... zombie rouge). */
    public int[] switchPick = {3, 3, 3, 4, 4, 6, 6, 6, 6};
    /** Part des mutations qui donnent un WILD (sinon le clown). */
    public double switchWild = 0.15;
    /** Tours gratuits : Savant Fou, Invasion Totale, relances (3 symboles pendant le bonus). */
    public int freeSavant = 10;
    public int freeInvasion = 10;
    public int retriggerSavant = 5;
    public int retriggerInvasion = 4;
    /** Gain maximal d'une partie (x la mise) : la partie s'arrete quand il est atteint. */
    public double maxWin = 5000;
    /** Achat direct des bonus (x la mise) ; 0 = desactive. */
    public double buySavant = 76;
    public double buyInvasion = 230;
    /** Multiplicateur global des gains (reglage fin de la redistribution). */
    public double payScale = 1.075;

    public RottenRules sanitized() {
        RottenRules d = new RottenRules();
        if (weights == null || weights.length != Rotten.REELS) weights = d.weights;
        for (int i = 0; i < Rotten.REELS; i++) {
            if (weights[i] == null || weights[i].length != 11) weights[i] = d.weights[i];
            int sum = 0;
            for (int w : weights[i]) sum += Math.max(0, w);
            if (sum <= 0) weights[i] = d.weights[i];
        }
        if (pays == null || pays.length != 10) pays = d.pays;
        for (int i = 0; i < 10; i++) if (pays[i] == null || pays[i].length != 4) pays[i] = d.pays[i];
        if (respins == null || respins.length != 10) respins = d.respins;
        if (switchCount == null || switchCount.length != 3) switchCount = d.switchCount;
        if (switchCountFree == null || switchCountFree.length != 3) switchCountFree = d.switchCountFree;
        if (switchPick == null || switchPick.length != 9) switchPick = d.switchPick;
        scatterSavant = clamp(scatterSavant, 0, 0.5);
        scatterInvasion = clamp(scatterInvasion, 0, 0.5);
        scatterSavantFree = clamp(scatterSavantFree, 0, 0.5);
        scatterInvasionFree = clamp(scatterInvasionFree, 0, 0.5);
        mutation = clamp(mutation, 0, 1);
        mutationSavant = clamp(mutationSavant, 0, 1);
        mutationInvasion = clamp(mutationInvasion, 0, 1);
        mutationRespin = clamp(mutationRespin, 0, 1);
        switchWild = clamp(switchWild, 0, 1);
        freeSavant = Math.max(1, Math.min(50, freeSavant));
        freeInvasion = Math.max(1, Math.min(50, freeInvasion));
        retriggerSavant = Math.max(0, Math.min(50, retriggerSavant));
        retriggerInvasion = Math.max(0, Math.min(50, retriggerInvasion));
        maxWin = Math.max(10, maxWin);
        buySavant = Math.max(0, buySavant);
        buyInvasion = Math.max(0, buyInvasion);
        payScale = clamp(payScale, 0.1, 5);
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
