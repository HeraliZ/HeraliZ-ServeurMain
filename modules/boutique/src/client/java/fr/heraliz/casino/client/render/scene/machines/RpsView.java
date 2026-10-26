package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat d'une table de pierre-feuille-ciseaux lu par son modele 3D.
 */
public final class RpsView {
    /** Phase du duel (0 libre, 1 defi ouvert, 2 en jeu, 3 termine) et etape de la manche (0 choix, 1 revelation). */
    public int phase, step;
    public int round, scoreA, scoreB, winsNeeded = 3;
    public boolean chosenA, chosenB;
    /** Coups reveles (-1 = aucun). */
    public int shownA = -1, shownB = -1;
    /** Gagnant de la manche (0 egalite) et du duel (1 A, 2 B, 3 annule). */
    public int roundWinner, winner;
    public float sinceReveal = 1e6f, sincePhase = 1e6f, countdown;
    public String nameA = "", nameB = "";
    /** Cote du joueur local (0 spectateur). */
    public int mySide;
    public long stake;
    /** Cote tenu par le bot (0 aucun). */
    public int botSide;
}
