package fr.heraliz.casino.game.roulette;

/**
 * Disposition du tapis de roulette (unites abstraites : largeur 24.7, profondeur 21), partagee par le modele 3D
 * et l'ecran de mise. u vers la droite, v vers le joueur (v = 0 : bord cote croupier).
 * <pre>
 * [0 ]| 3  6  9 ... 36 | 2:1     (v 0..3.2)
 * [  ]| 2  5  8 ... 35 | 2:1     (v 3.2..6.4)
 * [00]| 1  4  7 ... 34 | 2:1     (v 6.4..9.6)
 *     | 1ere 12 | 2e 12 | 3e 12  (v 9.6..12.6)
 * </pre>
 */
public final class RouletteLayout {
    private RouletteLayout() {
    }

    public static final float W = 24.7f, D = 21f;
    public static final float ZERO_W = 2.4f, CELL_W = 1.6f, ROW_H = 3.2f, NUM_END = ZERO_W + 12 * CELL_W;
    public static final float DOZEN_V0 = 9.6f, DOZEN_V1 = 12.6f, EVEN_V0 = 12.6f, EVEN_V1 = 15.6f;

    /** Rectangle d'une case de mise : u0, v0, u1, v1 (null si invalide pour ce nombre de zeros). */
    public static float[] rect(int spot, int zeros) {
        if (spot >= 1 && spot <= 36) {
            int col = (spot - 1) / 3, row = 2 - (spot - 1) % 3;
            float u0 = ZERO_W + col * CELL_W, v0 = row * ROW_H;
            return new float[]{u0, v0, u0 + CELL_W, v0 + ROW_H};
        }
        if (spot == 0) return zeros == 1 ? new float[]{0, 0, ZERO_W, 3 * ROW_H} : new float[]{0, 0, ZERO_W, 1.5f * ROW_H};
        if (spot == Roulette.DOUBLE_ZERO) return zeros == 2 ? new float[]{0, 1.5f * ROW_H, ZERO_W, 3 * ROW_H} : null;
        if (spot >= Roulette.COL1 && spot <= Roulette.COL3) {
            int row = 2 - (spot - Roulette.COL1);
            return new float[]{NUM_END, row * ROW_H, W, (row + 1) * ROW_H};
        }
        if (spot >= Roulette.DOZEN1 && spot <= Roulette.DOZEN3) {
            int k = spot - Roulette.DOZEN1;
            return new float[]{ZERO_W + k * 4 * CELL_W, DOZEN_V0, ZERO_W + (k + 1) * 4 * CELL_W, DOZEN_V1};
        }
        // pas de chances simples (rouge/noir, pair/impair, manque/passe) : le casino ne propose pas de pari a une chance sur deux
        return null;
    }

    /** Case sous le point (u, v), ou -1. */
    public static int spotAt(float u, float v, int zeros) {
        for (int s = 0; s < Roulette.SPOTS; s++) {
            float[] r = rect(s, zeros);
            if (r != null && u >= r[0] && u < r[2] && v >= r[1] && v < r[3]) return s;
        }
        return -1;
    }

    /** Libelle d'une case (tapis). */
    public static String label(int spot) {
        if (spot <= Roulette.DOUBLE_ZERO) return Roulette.label(spot);
        return switch (spot) {
            case Roulette.RED_BET -> "ROUGE";
            case Roulette.BLACK_BET -> "NOIR";
            case Roulette.EVEN -> "PAIR";
            case Roulette.ODD -> "IMPAIR";
            case Roulette.LOW -> "1-18";
            case Roulette.HIGH -> "19-36";
            case Roulette.DOZEN1 -> "1re 12";
            case Roulette.DOZEN2 -> "2e 12";
            case Roulette.DOZEN3 -> "3e 12";
            default -> "2:1";
        };
    }
}
