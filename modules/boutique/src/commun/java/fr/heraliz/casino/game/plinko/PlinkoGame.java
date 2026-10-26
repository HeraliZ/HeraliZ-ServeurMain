package fr.heraliz.casino.game.plinko;

/**
 * Trajectoire d'une bille de Plinko (partagee serveur / client) : le chemin est tire au lancement
 * (bit i = 1 : la bille part a droite a la rangee i) ; le panier = nombre de rebonds a droite.
 * Positions en "pas de clou" : x = decalage horizontal (0 = centre), y = rangee (0 = premier clou, vers le bas).
 */
public final class PlinkoGame {
    private PlinkoGame() {
    }

    public static final int ROWS = PlinkoRules.ROWS;
    /** Ticks avant le premier clou (chute depuis l'entonnoir), par rangee, et apres le dernier clou (entree au panier). */
    public static final int DROP = 8, PER_ROW = 5, LAND = 8;
    /** Duree totale d'une bille. */
    public static final int DURATION = DROP + ROWS * PER_ROW + LAND;

    public static int bucket(int path) {
        return Integer.bitCount(path & ((1 << ROWS) - 1));
    }

    /**
     * Position de la bille a l'instant t (ticks depuis le lancement).
     * out[0] = x (pas de clou, 0 = centre), out[1] = y (rangees, 0 = niveau du premier clou), out[2] = rotation.
     * @return false quand la bille est posee dans son panier (fin)
     */
    public static boolean position(int path, float t, float[] out) {
        if (t < DROP) {
            // chute depuis l'entonnoir jusqu'au sommet du premier clou
            float u = t / DROP;
            out[0] = 0;
            out[1] = -1.6f + 1.25f * u * u;
            out[2] = 0;
            return true;
        }
        float tt = t - DROP;
        int row = (int) Math.floor(tt / PER_ROW);
        float x = 0;
        for (int i = 0; i < Math.min(row, ROWS); i++) x += ((path >> i) & 1) != 0 ? 0.5f : -0.5f;
        if (row < ROWS) {
            // rebond sur le clou de la rangee "row" : petit saut parabolique vers le cote tire
            float u = (tt - row * PER_ROW) / PER_ROW;
            float dir = ((path >> row) & 1) != 0 ? 0.5f : -0.5f;
            out[0] = x + dir * smooth(u);
            float hop = 0.28f * (float) Math.sin(Math.PI * Math.min(1f, u * 1.25f));
            out[1] = row - 0.35f + u - hop;
            out[2] = (x + dir * u) * 180f;
            return true;
        }
        // entree dans le panier
        float u = Math.min(1f, (tt - ROWS * PER_ROW) / LAND);
        out[0] = x;
        out[1] = ROWS - 0.35f + 1.3f * u - 0.15f * (float) Math.sin(Math.PI * u);
        out[2] = x * 180f;
        return t < DURATION;
    }

    private static float smooth(float u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }
}
