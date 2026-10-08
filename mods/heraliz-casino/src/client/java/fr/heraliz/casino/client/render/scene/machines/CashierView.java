package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat de la caisse lu par son modele 3D.
 */
public final class CashierView {
    public long lastTx = Long.MIN_VALUE / 4;
    /** 0 achat de jetons, 1 vente. */
    public int lastTxType;
    public long lastTxAmount;
    /** Lignes du tableau des taux (deja mises en forme). */
    public String[] rates = {"Emeraude = 10", "Lingot d'or = 5", "Diamant = 50"};
}
