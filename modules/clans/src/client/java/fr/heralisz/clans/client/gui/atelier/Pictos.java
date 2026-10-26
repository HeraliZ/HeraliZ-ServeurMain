package fr.heralisz.clans.client.gui.atelier;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Pictogrammes de la rangee d'outils de l'atelier (11 x 11 px d'interface, traits d'un pixel) : grille, annuler,
 * retablir, miroir horizontal, miroir vertical, dupliquer, supprimer. Dessines en aplats (segments horizontaux
 * precalcules au chargement de la classe), teintes a la demande : nets a toutes les echelles, rien a charger.
 */
public final class Pictos {
    private Pictos() {
    }

    public static final int GRILLE = 0, ANNULER = 1, RETABLIR = 2, MIROIR_H = 3, MIROIR_V = 4, DUPLIQUER = 5, SUPPRIMER = 6;
    public static final int NOMBRE = 7, TAILLE = 11;

    private static final String[] DESSIN_GRILLE = {
            "###########",
            "#....#....#",
            "#....#....#",
            "#....#....#",
            "#....#....#",
            "###########",
            "#....#....#",
            "#....#....#",
            "#....#....#",
            "#....#....#",
            "###########"};
    private static final String[] DESSIN_ANNULER = {
            "...#.......",
            "..##.......",
            ".###.......",
            "########...",
            ".###....#..",
            "..##.....#.",
            "...#.....#.",
            ".........#.",
            "........#..",
            "...#####...",
            "..........."};
    private static final String[] DESSIN_SUPPRIMER = {
            "....###....",
            "###########",
            "...........",
            ".#########.",
            ".#..#.#..#.",
            ".#..#.#..#.",
            ".#..#.#..#.",
            ".#..#.#..#.",
            ".#..#.#..#.",
            ".#########.",
            "..........."};

    /** Par pictogramme : triplets (x, y, longueur) des segments horizontaux. */
    private static final int[][] SEGMENTS = new int[NOMBRE][];

    static {
        boolean[][][] p = new boolean[NOMBRE][TAILLE][TAILLE];
        lire(DESSIN_GRILLE, p[GRILLE], false);
        lire(DESSIN_ANNULER, p[ANNULER], false);
        lire(DESSIN_ANNULER, p[RETABLIR], true);
        lire(DESSIN_SUPPRIMER, p[SUPPRIMER], false);
        // miroir horizontal : triangle plein a gauche, triangle creux a droite, axe en pointilles
        boolean[][] mh = p[MIROIR_H];
        for (int r = 0; r < TAILLE; r += 2) mh[r][5] = true;
        for (int r = 1; r <= 9; r++) {
            int d = Math.abs(r - 5);
            for (int x = d; x <= 4; x++) mh[r][x] = true;
            mh[r][6] = true;
            mh[r][10 - d] = true;
        }
        // miroir vertical : le meme, transpose
        for (int y = 0; y < TAILLE; y++) for (int x = 0; x < TAILLE; x++) p[MIROIR_V][y][x] = mh[x][y];
        // dupliquer : deux carres de 8 decales, celui de derriere masque par celui de devant
        boolean[][] du = p[DUPLIQUER];
        for (int i = 0; i < 8; i++) {
            pose(du, i, 0, true);
            pose(du, i, 7, true);
            pose(du, 0, i, true);
            pose(du, 7, i, true);
        }
        for (int y = 3; y <= 10; y++) for (int x = 3; x <= 10; x++) du[y][x] = false;
        for (int i = 3; i <= 10; i++) {
            du[3][i] = true;
            du[10][i] = true;
            du[i][3] = true;
            du[i][10] = true;
        }
        for (int k = 0; k < NOMBRE; k++) SEGMENTS[k] = segments(p[k]);
    }

    private static void pose(boolean[][] m, int x, int y, boolean v) {
        m[y][x] = v;
    }

    private static void lire(String[] lignes, boolean[][] m, boolean miroir) {
        for (int y = 0; y < Math.min(TAILLE, lignes.length); y++) {
            String l = lignes[y];
            for (int x = 0; x < Math.min(TAILLE, l.length()); x++) {
                if (l.charAt(x) == '#') m[y][miroir ? TAILLE - 1 - x : x] = true;
            }
        }
    }

    private static int[] segments(boolean[][] m) {
        int n = 0;
        int[] tmp = new int[TAILLE * TAILLE * 3];
        for (int y = 0; y < TAILLE; y++) {
            int x = 0;
            while (x < TAILLE) {
                if (!m[y][x]) {
                    x++;
                    continue;
                }
                int x0 = x;
                while (x < TAILLE && m[y][x]) x++;
                tmp[n++] = x0;
                tmp[n++] = y;
                tmp[n++] = x - x0;
            }
        }
        int[] r = new int[n];
        System.arraycopy(tmp, 0, r, 0, n);
        return r;
    }

    /** Dessine le pictogramme en (x, y) (coin haut gauche, 11 x 11), couleur ARGB. */
    public static void dessiner(GuiGraphics g, int picto, int x, int y, int argb) {
        if (picto < 0 || picto >= NOMBRE) return;
        int[] s = SEGMENTS[picto];
        for (int i = 0; i < s.length; i += 3) g.fill(x + s[i], y + s[i + 1], x + s[i] + s[i + 2], y + s[i + 1] + 1, argb);
    }
}
