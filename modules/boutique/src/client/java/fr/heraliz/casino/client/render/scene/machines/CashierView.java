package fr.heraliz.casino.client.render.scene.machines;

import net.minecraft.client.resources.language.I18n;

/**
 * Etat de la caisse lu par son modele 3D.
 */
public final class CashierView {
    public long lastTx = Long.MIN_VALUE / 4;
    /** 0 achat de jetons, 1 vente. */
    public int lastTxType;
    public long lastTxAmount;
    /** Lignes du tableau des taux (deja mises en forme) ; par defaut (apercu), noms des objets dans la langue du joueur. */
    public String[] rates = {I18n.get("item.minecraft.emerald") + " = 10", I18n.get("item.minecraft.gold_ingot") + " = 5", I18n.get("item.minecraft.diamond") + " = 50"};
}
