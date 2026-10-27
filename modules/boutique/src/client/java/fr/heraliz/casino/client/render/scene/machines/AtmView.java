package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat du distributeur de capsules lu par son modele : dernier mouvement (instant, depot ou retrait, nombre de capsules),
 * et le solde du compte du joueur qui regarde (-1 : compte ferme ou inconnu ; chacun voit le sien).
 */
public final class AtmView {
    public static final int DEPOT = 0, RETRAIT = 1;

    public long lastTx = Long.MIN_VALUE / 4;
    public int lastTxType;
    public long lastTxAmount;
    public long solde = -1;
}
