package fr.heraliz.casino.game.roulette;

/**
 * Cases de mise et roue de la roulette (logique pure). Numero 37 = "00".
 */
public final class Roulette {
    private Roulette() {
    }

    public static final int DOUBLE_ZERO = 37;
    /** Ordre des cases sur la roue europeenne et americaine. */
    public static final int[] EUROPEAN = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
            20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    public static final int[] AMERICAN = {0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1, 37, 27, 10, 25, 29,
            12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2};
    private static final boolean[] RED = new boolean[38];

    static {
        for (int n : new int[]{1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}) RED[n] = true;
    }

    // ---- cases de mise
    /** 0..36 = plein, 37 = 00. */
    public static final int RED_BET = 38, BLACK_BET = 39, EVEN = 40, ODD = 41, LOW = 42, HIGH = 43,
            DOZEN1 = 44, DOZEN2 = 45, DOZEN3 = 46, COL1 = 47, COL2 = 48, COL3 = 49;
    public static final int SPOTS = 50;

    public static int[] wheel(int zeros) {
        return zeros == 1 ? EUROPEAN : AMERICAN;
    }

    public static boolean isRed(int n) {
        return n >= 1 && n <= 36 && RED[n];
    }

    public static boolean isBlack(int n) {
        return n >= 1 && n <= 36 && !RED[n];
    }

    public static String label(int n) {
        return n == DOUBLE_ZERO ? "00" : Integer.toString(n);
    }

    public static int color(int n) {
        if (n == 0 || n == DOUBLE_ZERO) return 0x1E9E4A;
        return isRed(n) ? 0xD42A2A : 0x1B1B1F;
    }

    /** La case "spot" gagne-t-elle si la bille tombe sur "n" ? */
    public static boolean wins(int spot, int n) {
        if (spot <= DOUBLE_ZERO) return spot == n;
        boolean zero = n == 0 || n == DOUBLE_ZERO;
        if (zero) return false;
        return switch (spot) {
            case RED_BET -> isRed(n);
            case BLACK_BET -> isBlack(n);
            case EVEN -> n % 2 == 0;
            case ODD -> n % 2 == 1;
            case LOW -> n <= 18;
            case HIGH -> n >= 19;
            case DOZEN1 -> n <= 12;
            case DOZEN2 -> n >= 13 && n <= 24;
            case DOZEN3 -> n >= 25;
            case COL1 -> n % 3 == 1;
            case COL2 -> n % 3 == 2;
            case COL3 -> n % 3 == 0;
            default -> false;
        };
    }

    /** Somme rendue (mise comprise) pour 1 jeton gagnant. */
    public static int returnMultiplier(int spot) {
        if (spot <= DOUBLE_ZERO) return 36;
        if (spot >= DOZEN1) return 3;
        return 2;
    }

    public static boolean valid(int spot, int zeros) {
        if (spot == DOUBLE_ZERO) return zeros == 2;
        // chances simples (rouge/noir, pair/impair, manque/passe) retirees : aucune mise a une chance sur deux
        if (spot >= RED_BET && spot <= HIGH) return false;
        return spot >= 0 && spot < SPOTS;
    }

    /** Angle (degres) de la bille et de la roue pendant le lancer ; la bille finit dans la case "pocket". */
    public static float wheelAngle(float t, int spinTicks, float startAngle) {
        // la roue tourne en continu et ralentit
        float u = Math.max(0, Math.min(1, t / (spinTicks + 40f)));
        return startAngle + 720f * (1 - (1 - u) * (1 - u)) + t * 0.6f;
    }
}
