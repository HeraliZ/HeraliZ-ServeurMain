package fr.heraliz.casino.game.slot;

/**
 * Regles d'une machine a sous (section de config/heralizcasino/casino.json).
 * Chaque rouleau est une bande de symboles (ordre visible sur le modele 3D) ; chaque arret de la bande a un poids
 * (rouleau "virtuel" : les symboles rares peuvent etre visibles sans sortir souvent, surtout sur le dernier rouleau).
 */
public class SlotRules {
    public long minBet = 1;
    public long maxBet = 1000;
    /** Bandes des rouleaux : indices de symboles (voir SlotSymbols). */
    public int[][] strips;
    /** Poids de chaque arret (meme taille que les bandes). */
    public int[][] weights;
    /** Gain (x la mise) pour 3 symboles identiques, par symbole. */
    public double[] threeOfAKind;
    /** Gain pour exactement 2 cerises (classique) : x la mise. */
    public double twoCherries = 2;
    /** Joker (symbole qui remplace les autres sur les lignes), -1 = aucun (machine Diamant). */
    public int wild = -1;
    /** Gain pour 3 jokers (Diamant). */
    public double threeWilds = 0;
    /** Nombre de rangees visibles (1 = une ligne, 3 = grille 3x3 et 5 lignes). */
    public int rows = 1;
    /** Part de chaque mise versee au jackpot progressif commun des machines a sous. */
    public double jackpotContribution = 0.01;

    public static SlotRules classic() {
        SlotRules r = new SlotRules();
        r.minBet = 1;
        r.maxBet = 1000;
        r.rows = 1;
        // 0 cerise 1 citron 2 orange 3 raisin 4 cloche 5 BAR 6 sept 7 couronne
        r.strips = new int[][]{
                {6, 0, 1, 5, 2, 0, 3, 4, 1, 7, 2, 0},
                {7, 0, 2, 1, 5, 0, 4, 3, 1, 6, 2, 0},
                {0, 4, 1, 7, 2, 0, 5, 3, 1, 6, 2, 0},
        };
        // poids par arret, calcules pour : cerise 18, citron 12, orange 10, raisin 8, cloche 6, BAR 5, sept 3, couronne 2
        // (rouleau 3 : citron 13, orange 11, sept 2, couronne 1) -> redistribution 87,2 %, un tirage gagnant sur 4,8
        r.weights = new int[][]{
                weightsFor(r.strips[0], new int[]{18, 12, 10, 8, 6, 5, 3, 2}),
                weightsFor(r.strips[1], new int[]{18, 12, 10, 8, 6, 5, 3, 2}),
                weightsFor(r.strips[2], new int[]{18, 13, 11, 8, 6, 5, 2, 1}),
        };
        r.threeOfAKind = new double[]{10, 14, 18, 24, 32, 64, 200, 1000};
        r.twoCherries = 2;
        return r;
    }

    public static SlotRules diamond() {
        SlotRules r = new SlotRules();
        r.minBet = 5;
        r.maxBet = 2500;
        r.rows = 3;
        // 0 emeraude 1 saphir 2 rubis 3 amethyste 4 lingot 5 couronne 6 diamant 7 etoile (joker)
        r.strips = new int[][]{
                {0, 1, 2, 0, 3, 4, 1, 0, 5, 2, 1, 0, 3, 6, 1, 2, 0, 7, 3, 1},
                {1, 0, 3, 2, 0, 5, 1, 4, 0, 2, 3, 1, 6, 0, 2, 1, 7, 0, 3, 2},
                {2, 1, 0, 4, 3, 1, 0, 2, 5, 1, 0, 3, 2, 0, 6, 1, 3, 0, 2, 7},
        };
        r.weights = new int[][]{ones(20), ones(20), ones(20)};
        // gains par ligne gagnante (la mise est repartie sur les 5 lignes : x la mise / 5)
        // -> redistribution 87,2 %, un tirage gagnant sur 3,4
        r.threeOfAKind = new double[]{5, 6, 9, 13, 26, 52, 250, 0};
        r.wild = 7;
        r.threeWilds = 1000;
        r.twoCherries = 0;
        return r;
    }

    /** Repartit des poids par symbole sur les arrets d'une bande (poids entier, reste sur les premiers arrets). */
    public static int[] weightsFor(int[] strip, int[] perSymbol) {
        int[] out = new int[strip.length];
        int[] count = new int[perSymbol.length];
        for (int s : strip) count[s]++;
        int[] seen = new int[perSymbol.length];
        for (int i = 0; i < strip.length; i++) {
            int s = strip[i];
            int base = perSymbol[s] / Math.max(1, count[s]);
            int extra = seen[s] < perSymbol[s] % Math.max(1, count[s]) ? 1 : 0;
            out[i] = base + extra;
            seen[s]++;
        }
        return out;
    }

    private static int[] ones(int n) {
        int[] o = new int[n];
        java.util.Arrays.fill(o, 1);
        return o;
    }

    /** Verifie/complete les regles lues dans la config (tailles coherentes). */
    public SlotRules sanitized(SlotRules def) {
        if (strips == null || strips.length != 3) strips = def.strips;
        if (weights == null || weights.length != 3) weights = def.weights;
        for (int i = 0; i < 3; i++) {
            if (strips[i] == null || strips[i].length < 3) strips[i] = def.strips[i];
            if (weights[i] == null || weights[i].length != strips[i].length) weights[i] = weightsFor(strips[i], uniform(8));
        }
        if (threeOfAKind == null || threeOfAKind.length != 8) threeOfAKind = def.threeOfAKind;
        if (rows != 1 && rows != 3) rows = def.rows;
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }

    private static int[] uniform(int n) {
        int[] o = new int[n];
        java.util.Arrays.fill(o, 4);
        return o;
    }
}
