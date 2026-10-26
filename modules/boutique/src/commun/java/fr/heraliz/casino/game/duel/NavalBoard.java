package fr.heraliz.casino.game.duel;

import java.util.random.RandomGenerator;

/**
 * Bataille navale (logique pure) : grille de 8 x 8, la flotte du vrai jeu (porte-avions 5, croiseur 4,
 * contre-torpilleur 3, deux torpilleurs 2). Une flotte = tableau de 64 cases (0 = eau, k = navire numero k).
 * Les tirs sont des masques de bits (case i = bit i, i = y * 8 + x).
 */
public final class NavalBoard {
    private NavalBoard() {
    }

    public static final int SIZE = 8, CELLS = SIZE * SIZE;
    public static final int[] SHIPS = {5, 4, 3, 2, 2};

    /** Flotte placee au hasard (navires sans contact si possible, meme en diagonale). */
    public static int[] random(RandomGenerator rng) {
        for (int attempt = 0; attempt < 400; attempt++) {
            int[] b = new int[CELLS];
            if (placeAll(b, rng, true)) return b;
        }
        for (int attempt = 0; attempt < 400; attempt++) {
            int[] b = new int[CELLS];
            if (placeAll(b, rng, false)) return b;
        }
        // ne peut pas arriver sur 8 x 8 : flotte en lignes
        int[] b = new int[CELLS];
        for (int k = 0; k < SHIPS.length; k++) for (int i = 0; i < SHIPS[k]; i++) b[(k + k) * SIZE + i] = k + 1;
        return b;
    }

    private static boolean placeAll(int[] b, RandomGenerator rng, boolean noTouch) {
        for (int k = 0; k < SHIPS.length; k++) {
            boolean ok = false;
            for (int tries = 0; tries < 120 && !ok; tries++) {
                boolean horizontal = rng.nextBoolean();
                int len = SHIPS[k];
                int x = rng.nextInt(horizontal ? SIZE - len + 1 : SIZE), y = rng.nextInt(horizontal ? SIZE : SIZE - len + 1);
                if (fits(b, k + 1, x, y, horizontal, noTouch)) {
                    put(b, k + 1, x, y, horizontal);
                    ok = true;
                }
            }
            if (!ok) return false;
        }
        return true;
    }

    /** Le navire k (1..5) tient-il en (x, y) (proue), horizontal ou vertical, sans chevaucher les autres ? */
    public static boolean fits(int[] b, int k, int x, int y, boolean horizontal, boolean noTouch) {
        int len = SHIPS[k - 1];
        if (x < 0 || y < 0 || (horizontal ? x + len > SIZE : x >= SIZE) || (horizontal ? y >= SIZE : y + len > SIZE)) return false;
        for (int i = 0; i < len; i++) {
            int cx = x + (horizontal ? i : 0), cy = y + (horizontal ? 0 : i);
            int v = b[cy * SIZE + cx];
            if (v != 0 && v != k) return false;
            if (noTouch) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx, ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE) continue;
                        int n = b[ny * SIZE + nx];
                        if (n != 0 && n != k) return false;
                    }
                }
            }
        }
        return true;
    }

    private static void put(int[] b, int k, int x, int y, boolean horizontal) {
        for (int i = 0; i < b.length; i++) if (b[i] == k) b[i] = 0;
        for (int i = 0; i < SHIPS[k - 1]; i++) b[(y + (horizontal ? 0 : i)) * SIZE + x + (horizontal ? i : 0)] = k;
    }

    /**
     * Deplace le navire k (placement manuel) : proue en (x, y). Les navires peuvent se toucher mais pas se chevaucher.
     * @return true si le deplacement est valide (la flotte est modifiee)
     */
    public static boolean move(int[] b, int k, int x, int y, boolean horizontal) {
        if (k < 1 || k > SHIPS.length || !fits(b, k, x, y, horizontal, false)) return false;
        put(b, k, x, y, horizontal);
        return true;
    }

    /** Retire le navire k de la grille (il retourne sur le cote de la table). */
    public static void remove(int[] b, int k) {
        for (int i = 0; i < b.length; i++) if (b[i] == k) b[i] = 0;
    }

    /** Tous les navires sont sur la grille. */
    public static boolean complete(int[] b) {
        for (int k = 1; k <= SHIPS.length; k++) if (ship(b, k) == null) return false;
        return true;
    }

    /** Place au hasard les navires qui ne sont pas encore sur la grille (temps ecoule). */
    public static void fillMissing(int[] b, RandomGenerator rng) {
        for (int k = 1; k <= SHIPS.length; k++) {
            if (ship(b, k) != null) continue;
            boolean ok = false;
            for (int tries = 0; tries < 400 && !ok; tries++) {
                boolean horizontal = rng.nextBoolean();
                int len = SHIPS[k - 1];
                int x = rng.nextInt(horizontal ? SIZE - len + 1 : SIZE), y = rng.nextInt(horizontal ? SIZE : SIZE - len + 1);
                if (fits(b, k, x, y, horizontal, tries < 300)) {
                    put(b, k, x, y, horizontal);
                    ok = true;
                }
            }
            if (!ok) {
                // grille trop encombree : on repart d'une flotte au hasard
                int[] r = random(rng);
                System.arraycopy(r, 0, b, 0, CELLS);
                return;
            }
        }
    }

    /** Position du navire k : {x, y, horizontal (1/0), longueur}, ou null s'il n'est pas sur la grille. */
    public static int[] ship(int[] b, int k) {
        int first = -1, count = 0, last = -1;
        for (int i = 0; i < b.length; i++) {
            if (b[i] != k) continue;
            if (first < 0) first = i;
            last = i;
            count++;
        }
        if (first < 0) return null;
        boolean horizontal = count == 1 || last / SIZE == first / SIZE;
        return new int[]{first % SIZE, first / SIZE, horizontal ? 1 : 0, count};
    }

    /** Flotte complete et valide (sert a verifier une flotte recue). */
    public static boolean valid(int[] b) {
        if (b == null || b.length != CELLS) return false;
        for (int k = 1; k <= SHIPS.length; k++) {
            int[] s = ship(b, k);
            if (s == null || s[3] != SHIPS[k - 1]) return false;
        }
        return true;
    }

    /** Masque des cases de navires. */
    public static long shipMask(int[] b) {
        long m = 0;
        for (int i = 0; i < CELLS; i++) if (b[i] != 0) m |= 1L << i;
        return m;
    }

    /** Masque des cases du navire k. */
    public static long shipCells(int[] b, int k) {
        long m = 0;
        for (int i = 0; i < CELLS; i++) if (b[i] == k) m |= 1L << i;
        return m;
    }

    /** Masque des cases des navires entierement touches. */
    public static long sunk(int[] b, long hits) {
        long m = 0;
        for (int k = 1; k <= SHIPS.length; k++) {
            long cells = shipCells(b, k);
            if (cells != 0 && (hits & cells) == cells) m |= cells;
        }
        return m;
    }

    /** Nombre de navires coules. */
    public static int sunkCount(int[] b, long hits) {
        int n = 0;
        for (int k = 1; k <= SHIPS.length; k++) {
            long cells = shipCells(b, k);
            if (cells != 0 && (hits & cells) == cells) n++;
        }
        return n;
    }

    public static boolean allSunk(int[] b, long hits) {
        long ships = shipMask(b);
        return (hits & ships) == ships;
    }

    public static int totalShipCells() {
        int n = 0;
        for (int s : SHIPS) n += s;
        return n;
    }

    /** Nom de case : colonne A..H, rangee 1..8. */
    public static String cellName(int c) {
        return (char) ('A' + c % SIZE) + Integer.toString(c / SIZE + 1);
    }
}
