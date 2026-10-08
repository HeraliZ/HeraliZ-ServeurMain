package fr.heraliz.casino.game.slot;

/**
 * Reglages de "Souffle du Loup" (section "souffle_loup" de config/heralizcasino/casino.json), sur le modele de
 * Huff N' Even More Puff : 5 rouleaux x 3 rangees, 243 facons de gagner, LOUP joker, CHAPEAUX (6 ou plus = tours
 * gratuits), SCIES bleues et SCIE D'OR (3 scies = la roue ; 2 bleues + 1 d'or = la super roue), cadres paille / bois /
 * brique qui deviennent des maisons, jackpots MINI / MINOR / MAJOR / GRAND / SUPER.
 */
public class LoupRules {
    public long minBet = 5;
    public long maxBet = 2500;
    /**
     * Poids par rouleau : 10, J, Q, K, A, cochon de paille, cochon bucheron, cochon macon, LOUP (joker, rouleaux 2 a 4).
     */
    public int[][] weights = {
            {26, 26, 24, 22, 20, 10, 8, 6, 0},
            {26, 26, 24, 22, 20, 10, 8, 6, 3},
            {26, 26, 24, 22, 20, 10, 8, 6, 3},
            {26, 26, 24, 22, 20, 10, 8, 6, 3},
            {26, 26, 24, 22, 20, 10, 8, 6, 0},
    };
    /**
     * Gain d'une facon de gagner (x la mise) pour 3, 4 et 5 rouleaux de suite depuis la gauche : 10, J, Q, K, A, cochons
     * paille / bucheron / macon. Deux symboles sur un rouleau = deux fois plus de facons.
     */
    public double[][] pays = {
            {0.27, 0.65, 1.6},
            {0.27, 0.65, 1.6},
            {0.32, 0.8, 2},
            {0.38, 1, 2.6},
            {0.5, 1.3, 3.2},
            {0.8, 2.6, 6.5},
            {1, 3.2, 10},
            {1.6, 5, 16},
    };
    /** Chance par case d'un CHAPEAU en jeu normal (6 ou plus : tours gratuits). */
    public double hat = 0.125;
    /** Chance par rouleau d'une SCIE en jeu normal (3 ou plus : la roue). */
    public double saw = 0.065;
    /** Part des scies qui sont en or (une au plus par tour ; 2 bleues + 1 d'or : la super roue). */
    public double gold = 0.022;
    /** Chance par case d'un chapeau pendant les tours gratuits (et pendant ceux du manoir, qui posent des briques). */
    public double bonusHat = 0.075;
    public double mansionHat = 0.02;
    /** Tours gratuits ; chapeaux qui en rajoutent (3 ou plus d'un coup) et combien. */
    public int bonusSpins = 6;
    public int retriggerHats = 3;
    public int retriggerSpins = 1;
    /** Valeur des maisons (x la mise) de paille, de bois et de brique, et poids de chaque valeur. */
    public double[] strawValues = {0.5, 0.8, 1, 1.5, 2};
    public int[] strawWeights = {30, 26, 22, 14, 8};
    public double[] stickValues = {2, 3, 4, 5, 8};
    public int[] stickWeights = {30, 26, 20, 15, 9};
    public double[] brickValues = {10, 12, 15, 20, 30, 50, 100, 250};
    public int[] brickWeights = {32, 24, 18, 12, 7, 4, 2, 1};
    /** Maison de bois : chance d'un jackpot, et poids de MINI, MINI x2, MINOR. */
    public double stickJackpot = 0.03;
    public int[] stickJackpots = {70, 20, 10};
    /** Maison de brique : chance d'un lot special, et poids de MINI, MINI x2, MINOR, MAJOR, GRAND, un tour de roue. */
    public double brickSpecial = 0.08;
    public int[] brickSpecials = {40, 20, 15, 2, 1, 22};
    /** Jackpots (x la mise) : MINI, MINOR, MAJOR, GRAND, SUPER (super roue). */
    public double[] jackpots = {20, 100, 1000, 5000, 7500};
    /** Roue : poids de MINI, MINOR, MAJOR, GRAND, SCIES, MEGA CHAPEAU, MANOIR, SUPER ROUE. */
    public int[] wheel = {24, 8, 1, 0, 30, 30, 10, 6};
    /** Super roue : poids de MAJOR, GRAND, SUPER, SUPER SCIES, SUPER MEGA CHAPEAU, SUPER MANOIR. */
    public int[] superWheel = {30, 4, 2, 400, 400, 120};
    /** Chapeau tombe sur une brique alors que tout est deja en brique : prime (x la mise). */
    public double prize = 5;
    public double maxWin = 10000;
    /** Achat d'un tour de roue (x la mise) ; 0 = desactive. */
    public double buyWheel = 105;
    public double payScale = 1.0;

    public LoupRules sanitized() {
        LoupRules d = new LoupRules();
        if (weights == null || weights.length != Loup.REELS) weights = d.weights;
        for (int i = 0; i < Loup.REELS; i++) if (weights[i] == null || weights[i].length != 9) weights[i] = d.weights[i];
        if (pays == null || pays.length != 8) pays = d.pays;
        for (int i = 0; i < 8; i++) if (pays[i] == null || pays[i].length != 3) pays[i] = d.pays[i];
        if (!values(strawValues, strawWeights)) {
            strawValues = d.strawValues;
            strawWeights = d.strawWeights;
        }
        if (!values(stickValues, stickWeights)) {
            stickValues = d.stickValues;
            stickWeights = d.stickWeights;
        }
        if (!values(brickValues, brickWeights)) {
            brickValues = d.brickValues;
            brickWeights = d.brickWeights;
        }
        if (stickJackpots == null || stickJackpots.length != 3 || !weights(stickJackpots)) stickJackpots = d.stickJackpots;
        if (brickSpecials == null || brickSpecials.length != 6 || !weights(brickSpecials)) brickSpecials = d.brickSpecials;
        if (jackpots == null || jackpots.length != 5) jackpots = d.jackpots;
        if (wheel == null || wheel.length != Loup.WHEEL_OUT.length || !weights(wheel)) wheel = d.wheel;
        if (superWheel == null || superWheel.length != Loup.SUPER_OUT.length || !weights(superWheel)) superWheel = d.superWheel;
        hat = clamp(hat, 0, 0.5);
        saw = clamp(saw, 0, 1);
        gold = clamp(gold, 0, 1);
        bonusHat = clamp(bonusHat, 0, 0.9);
        mansionHat = clamp(mansionHat, 0, 0.9);
        stickJackpot = clamp(stickJackpot, 0, 1);
        brickSpecial = clamp(brickSpecial, 0, 1);
        prize = clamp(prize, 0, 1000);
        bonusSpins = Math.max(1, Math.min(20, bonusSpins));
        retriggerHats = Math.max(1, Math.min(30, retriggerHats));
        retriggerSpins = Math.max(0, Math.min(10, retriggerSpins));
        maxWin = Math.max(10, maxWin);
        buyWheel = Math.max(0, buyWheel);
        payScale = clamp(payScale, 0.1, 5);
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    /** Valeurs d'un etage de maison : de 1 a 8 valeurs positives, autant de poids, pas tous nuls. */
    private static boolean values(double[] v, int[] w) {
        if (v == null || w == null || v.length < 1 || v.length > 8 || w.length != v.length) return false;
        for (double x : v) if (!(x >= 0) || x > 100_000) return false;
        return weights(w);
    }

    /** Poids d'une roue : ramenes entre 0 et 1000 ; false si tous nuls (la roue ne pourrait tomber sur rien). */
    private static boolean weights(int[] w) {
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
