package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat d'une table de bataille navale lu par son modele 3D.
 */
public final class NavalView {
    /** Phase du duel (0 libre, 1 defi, 2 en jeu, 3 termine), etape (0 placement, 1 bataille). */
    public int phase, step, turn = 1, winner;
    public boolean readyA, readyB;
    /** Tirs recus par A / B et touches (bit i = case i). */
    public long shotsOnA, hitsOnA, shotsOnB, hitsOnB;
    /** Epaves : -1 ou x | y << 4 | horizontal << 8 | longueur << 9. */
    public int[] wrecksA = new int[0], wrecksB = new int[0];
    public int lastShot = -1, lastShotSide;
    public float sinceShot = 1e6f, sincePhase = 1e6f, countdown;
    /** Ma flotte (vide pour un spectateur) et mon cote (0 spectateur). */
    public int[] myBoard = new int[0];
    public int mySide;
    /** Placement : navire tenu (0 aucun) et son sens, point de la table sous la souris, case de proue proposee et validite. */
    public int held;
    public boolean heldH = true, snapOk;
    public float holdX = Float.NaN, holdZ = Float.NaN;
    public int snapCell = -1;
    public String nameA = "", nameB = "";
    public long stake;
    /** Cote tenu par le bot (0 aucun). */
    public int botSide;
}
