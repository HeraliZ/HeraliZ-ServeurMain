package fr.heraliz.casino.game.duel;

/**
 * Pierre-feuille-ciseaux (logique pure).
 */
public final class Rps {
    private Rps() {
    }

    public static final int NONE = -1, ROCK = 0, PAPER = 1, SCISSORS = 2;
    public static final String[] KEYS = {"rock", "paper", "scissors"};

    /**
     * Chronologie de la revelation (ticks) : "CHI", "FOU", "MI !" (mains dans le dos), les mains sortent avec l'objet,
     * les objets sont lances au centre de la table, l'un ecrase / couvre / coupe l'autre, puis le score change.
     */
    public static final int T_FOU = 12, T_MI = 24, T_SHOW = 30, T_THROW = 46, T_LAND = 58, T_CLASH = 62, T_RESULT = 92;
    public static final int REVEAL_TICKS = 112;

    /** Resultat d'une manche : 0 egalite, 1 le premier gagne, 2 le second gagne (un coup absent perd). */
    public static int winner(int a, int b) {
        boolean va = a >= ROCK && a <= SCISSORS, vb = b >= ROCK && b <= SCISSORS;
        if (!va && !vb) return 0;
        if (!va) return 2;
        if (!vb) return 1;
        if (a == b) return 0;
        return (a - b + 3) % 3 == 1 ? 1 : 2;
    }
}
