package fr.heraliz.casino.game.cards;

/**
 * Cartes : entier 0..51 ; rang = c % 13 (0 = As, 1..9 = 2..10, 10 = Valet, 11 = Dame, 12 = Roi),
 * couleur = c / 13 (0 pique, 1 coeur, 2 carreau, 3 trefle). -1 = carte cachee (face contre la table).
 */
public final class Cards {
    private Cards() {
    }

    public static final int HIDDEN = -1;
    public static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R"};
    public static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    public static int rank(int c) {
        return c % 13;
    }

    public static int suit(int c) {
        return c / 13;
    }

    public static boolean red(int c) {
        int s = suit(c);
        return s == 1 || s == 2;
    }

    public static String name(int c) {
        if (c < 0) return "?";
        return RANKS[rank(c)] + SUITS[suit(c)];
    }

    // =================================================================== blackjack
    /** Valeur d'une carte au blackjack (As = 1 ici). */
    public static int bjValue(int c) {
        int r = rank(c);
        return r >= 9 ? 10 : r + 1;
    }

    /** Total d'une main (les As comptent 11 si possible). Cartes cachees ignorees. */
    public static int total(int[] hand, int n) {
        int sum = 0;
        boolean ace = false;
        for (int i = 0; i < n; i++) {
            if (hand[i] < 0) continue;
            sum += bjValue(hand[i]);
            if (rank(hand[i]) == 0) ace = true;
        }
        return ace && sum + 10 <= 21 ? sum + 10 : sum;
    }

    /** Main "souple" (un As compte 11). */
    public static boolean soft(int[] hand, int n) {
        int sum = 0;
        boolean ace = false;
        for (int i = 0; i < n; i++) {
            if (hand[i] < 0) continue;
            sum += bjValue(hand[i]);
            if (rank(hand[i]) == 0) ace = true;
        }
        return ace && sum + 10 <= 21;
    }

    public static boolean blackjack(int[] hand, int n) {
        return n == 2 && total(hand, n) == 21;
    }
}
