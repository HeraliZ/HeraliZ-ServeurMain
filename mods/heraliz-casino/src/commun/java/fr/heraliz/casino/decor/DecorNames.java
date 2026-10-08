package fr.heraliz.casino.decor;

/**
 * Variantes des elements de decor (Java pur : partage par le jeu, les modeles 3D et les outils hors-jeu).
 */
public final class DecorNames {
    private DecorNames() {
    }

    /** Textes de la fleche a ampoules ; variante = texte * 2 + sens (0 : vers la droite, 1 : vers la gauche). */
    public static final String[] ARROW_LABELS = {"CAISSE", "JEUX", "SORTIE", "BAR", "VIP", "ENTRÉE"};

    public static int arrowLabel(int mode) {
        return Math.floorMod(mode, ARROW_LABELS.length * 2) / 2;
    }

    public static boolean arrowLeft(int mode) {
        return (Math.floorMod(mode, ARROW_LABELS.length * 2) & 1) == 1;
    }

    /** Motifs du neon mural. */
    public static final String[] NEON = {"777", "cerises", "as", "des", "bar", "fer"};

    /** Modeles d'affiches. */
    public static final String[] POSTERS = {"casino", "jackpot", "maison", "recherche", "reglement", "roulette", "blackjack", "change"};

    /** Chaines de la tele. */
    public static final String[] TV = {"casino", "jackpot", "neige", "mire"};

    /** Couleurs de l'enseigne (neon) : rose, cyan, rouge, vert, ambre. */
    public static final int[] SIGN_COLORS = {0xFF2A8C, 0x2AE8FF, 0xFF2A2A, 0x5CFF4A, 0xFFB02A};

    /** Pancartes de carton du Trou (deux lignes au feutre). */
    public static final String[][] SIGNS = {
            {"1 JETON", "S.V.P."},
            {"J'AI TOUT PERDU", "AU BLACKJACK"},
            {"LA MAISON", "GAGNE TOUJOURS"},
            {"DIEU VOUS", "BÉNISSE"}};

    /**
     * Graffitis (CAISSE, VIP) : variante = support * 4 + fleche. Fleche : 0 vers la droite, 1 vers la gauche, 2 vers le
     * bas, 3 aucune ; support : 0 a meme le mur, 1 sur un panneau (tole rouillee / laque noire).
     */
    public static int graffitiArrow(int mode) {
        return Math.floorMod(mode, 8) % 4;
    }

    public static boolean graffitiPanel(int mode) {
        return Math.floorMod(mode, 8) >= 4;
    }
}
