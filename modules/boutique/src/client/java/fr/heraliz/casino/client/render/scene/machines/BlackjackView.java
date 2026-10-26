package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.cards.BlackjackTable;

/**
 * Etat de la table de blackjack lu par son modele 3D (la main en cours, synchronisee par le serveur).
 */
public final class BlackjackView {
    public BlackjackTable table = new BlackjackTable(new BlackjackRules());
    public String player = "";
    public long doneTick;
}
