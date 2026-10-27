package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat du distributeur de capsules lu par son modele : dernier mouvement (instant, depot ou retrait, nombre de capsules).
 * A completer avec l'etat synchronise du bloc quand le modele sera valide et branche dans le jeu.
 */
public final class AtmView {
    public static final int DEPOT = 0, RETRAIT = 1;

    public long lastTx = Long.MIN_VALUE / 4;
    public int lastTxType;
    public long lastTxAmount;
}
