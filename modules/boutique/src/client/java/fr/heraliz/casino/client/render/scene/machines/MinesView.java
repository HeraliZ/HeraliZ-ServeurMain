package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat du Demineur lu par son modele 3D.
 */
public final class MinesView {
    public static final int IDLE = 0, PLAYING = 1, BOOM = 2, CASHED = 3;
    public int phase = IDLE;
    public int mines = 3;
    public int revealed;
    public final long[] tileTicks = new long[25];
    public int mineShown;
    public int boomTile = -1;
    public long endTick = Long.MIN_VALUE / 4;
    public long mult100 = 100;
    public long bet, win;
    /** Multiplicateur de la prochaine case sure (x100), pour l'affichage. */
    public long next100 = 100;
}
