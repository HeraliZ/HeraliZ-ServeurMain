package fr.heraliz.casino.game.duel;

import java.util.random.RandomGenerator;

/**
 * Adversaire artificiel de la bataille navale (commande /casino bot) : chasse en damier, puis quand un navire est
 * touche, vise ses voisins (dans l'alignement des touches s'il y en a deux) jusqu'a le couler.
 */
public final class NavalBot {
    private NavalBot() {
    }

    /** Case a viser (y * SIZE + x) connaissant les tirs deja faits, les touches et les cases des navires coules. */
    public static int pick(long shots, long hits, long sunk, RandomGenerator rng) {
        int n = NavalBoard.SIZE;
        long wounded = hits & ~sunk;
        if (wounded != 0) {
            int best = -1, bestScore = -1;
            for (int c = 0; c < NavalBoard.CELLS; c++) {
                if ((wounded & (1L << c)) == 0) continue;
                int x = c % n, y = c / n;
                boolean horiz = has(wounded, x - 1, y) || has(wounded, x + 1, y);
                boolean vert = has(wounded, x, y - 1) || has(wounded, x, y + 1);
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : dirs) {
                    // on prolonge la ligne des touches : on saute les cases deja touchees
                    int tx = x + d[0], ty = y + d[1];
                    while (has(wounded, tx, ty)) {
                        tx += d[0];
                        ty += d[1];
                    }
                    if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue;
                    int t = ty * n + tx;
                    if ((shots & (1L << t)) != 0) continue;
                    int score = 1 + ((d[1] == 0 && horiz) || (d[0] == 0 && vert) ? 10 : 0) + rng.nextInt(3);
                    if (score > bestScore) {
                        bestScore = score;
                        best = t;
                    }
                }
            }
            if (best >= 0) return best;
        }
        // chasse : cases en damier d'abord, loin des epaves
        int count = 0;
        for (int pass = 0; pass < 2; pass++) {
            count = 0;
            for (int c = 0; c < NavalBoard.CELLS; c++) if (huntable(c, shots, sunk, pass == 0)) count++;
            if (count > 0) {
                int k = rng.nextInt(count);
                for (int c = 0; c < NavalBoard.CELLS; c++) {
                    if (!huntable(c, shots, sunk, pass == 0)) continue;
                    if (k-- == 0) return c;
                }
            }
        }
        for (int c = 0; c < NavalBoard.CELLS; c++) if ((shots & (1L << c)) == 0) return c;
        return -1;
    }

    private static boolean huntable(int c, long shots, long sunk, boolean strict) {
        if ((shots & (1L << c)) != 0) return false;
        if (!strict) return true;
        int n = NavalBoard.SIZE, x = c % n, y = c / n;
        if ((x + y) % 2 != 0) return false;
        // les navires ne se touchent pas : inutile de tirer contre une epave
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (has(sunk, x + dx, y + dy)) return false;
        return true;
    }

    private static boolean has(long mask, int x, int y) {
        int n = NavalBoard.SIZE;
        return x >= 0 && y >= 0 && x < n && y < n && (mask & (1L << (y * n + x))) != 0;
    }
}
