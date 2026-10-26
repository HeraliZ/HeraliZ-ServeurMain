package fr.heraliz.casino.game.slot;

import java.util.random.RandomGenerator;

/**
 * Tirage et lecture des rouleaux d'une machine a sous (logique pure, partagee serveur / client / tests).
 * Chronologie d'un tour (ticks) : levier, rouleaux lances, arret du rouleau 1, 2 puis 3, affichage du gain.
 */
public final class SlotGame {
    private SlotGame() {
    }

    /** Duree du levier / du lancement. */
    public static final int LEVER = 8;
    /** Arret des rouleaux (ticks depuis le debut du tour). */
    public static final int[] STOP = {30, 40, 52};
    /** Fin du tour (gain verse a l'arret du dernier rouleau). */
    public static final int END = 64;
    /** Duree de la fete en cas de gain (apres le dernier arret). */
    public static final int CELEBRATE = 60;

    /** 5 lignes de la grille 3x3 : rangee (0 haut, 1 milieu, 2 bas) pour chaque rouleau. */
    public static final int[][] LINES = {{1, 1, 1}, {0, 0, 0}, {2, 2, 2}, {0, 1, 2}, {2, 1, 0}};

    /** Resultat d'un tirage. */
    public static final class Result {
        /** Arret de chaque rouleau (symbole central = bande[arret]). */
        public final int[] stops;
        /** Gain total (x la mise). */
        public double multiplier;
        /** Lignes gagnantes (masque de bits, bit i = LINES[i]). */
        public int lineMask;
        /** Jackpot (3 couronnes / 3 jokers). */
        public boolean jackpot;

        public Result(int[] stops) {
            this.stops = stops;
        }
    }

    /** Tire les 3 rouleaux selon les poids. */
    public static int[] spin(SlotRules r, RandomGenerator rng) {
        int[] stops = new int[3];
        for (int i = 0; i < 3; i++) stops[i] = pick(r.weights[i], rng);
        return stops;
    }

    private static int pick(int[] weights, RandomGenerator rng) {
        int total = 0;
        for (int w : weights) total += Math.max(0, w);
        if (total <= 0) return rng.nextInt(weights.length);
        int x = rng.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            x -= Math.max(0, weights[i]);
            if (x < 0) return i;
        }
        return weights.length - 1;
    }

    /** Symbole visible au rang "row" (0 haut, 1 centre, 2 bas) du rouleau "reel" arrete sur "stop". */
    public static int symbol(SlotRules r, int reel, int stop, int row) {
        int[] s = r.strips[reel];
        int n = s.length;
        // le haut du rouleau montre l'arret precedent (la bande defile vers le bas)
        return s[Math.floorMod(stop + row - 1, n)];
    }

    public static Result evaluate(SlotRules r, int[] stops) {
        Result res = new Result(stops);
        if (r.rows <= 1) {
            int a = symbol(r, 0, stops[0], 1), b = symbol(r, 1, stops[1], 1), c = symbol(r, 2, stops[2], 1);
            if (a == b && b == c) {
                res.multiplier = r.threeOfAKind[a];
                res.jackpot = a == 7;
            } else {
                int cherries = (a == 0 ? 1 : 0) + (b == 0 ? 1 : 0) + (c == 0 ? 1 : 0);
                if (cherries == 2) res.multiplier = r.twoCherries;
            }
            if (res.multiplier > 0) res.lineMask = 1;
            return res;
        }
        double total = 0;
        for (int l = 0; l < LINES.length; l++) {
            int a = symbol(r, 0, stops[0], LINES[l][0]);
            int b = symbol(r, 1, stops[1], LINES[l][1]);
            int c = symbol(r, 2, stops[2], LINES[l][2]);
            double pay = linePay(r, a, b, c);
            if (pay > 0) {
                total += pay / LINES.length;
                res.lineMask |= 1 << l;
                if (a == r.wild && b == r.wild && c == r.wild) res.jackpot = true;
            }
        }
        res.multiplier = total;
        return res;
    }

    /** Gain d'une ligne (x la mise d'une ligne) avec le joker. */
    public static double linePay(SlotRules r, int a, int b, int c) {
        int w = r.wild;
        if (w >= 0 && a == w && b == w && c == w) return r.threeWilds;
        int s = -1;
        for (int x : new int[]{a, b, c}) {
            if (x == w) continue;
            if (s < 0) s = x;
            else if (x != s) return 0;
        }
        return s < 0 ? 0 : r.threeOfAKind[s];
    }

    /** Redistribution theorique exacte (somme sur tous les arrets ponderes). */
    public static double[] theoreticalRtp(SlotRules r) {
        long[] tot = new long[3];
        for (int i = 0; i < 3; i++) for (int w : r.weights[i]) tot[i] += w;
        double ev = 0, hit = 0;
        for (int i = 0; i < r.strips[0].length; i++) {
            for (int j = 0; j < r.strips[1].length; j++) {
                for (int k = 0; k < r.strips[2].length; k++) {
                    double p = (double) r.weights[0][i] * r.weights[1][j] * r.weights[2][k] / ((double) tot[0] * tot[1] * tot[2]);
                    if (p == 0) continue;
                    Result res = evaluate(r, new int[]{i, j, k});
                    ev += p * res.multiplier;
                    if (res.multiplier > 0) hit += p;
                }
            }
        }
        return new double[]{ev, hit};
    }
}
