package fr.heraliz.casino.game.duel;

/**
 * Reglages d'un jeu joueur contre joueur : les deux joueurs misent la meme somme, le gagnant remporte le pot moins
 * la commission du casino.
 */
public class DuelRules {
    public long minBet = 10;
    public long maxBet = 10000;
    /** Commission du casino sur le pot (0.05 = 5 %). */
    public double commission = 0.05;
    /** Temps d'attente d'un adversaire avant annulation (secondes). */
    public int waitSeconds = 120;
    /** Temps pour jouer un coup (secondes). */
    public int turnSeconds = 15;
    /** Pierre-feuille-ciseaux : manches a gagner pour remporter le duel. */
    public int winsNeeded = 2;

    public DuelRules sanitized() {
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        commission = Math.max(0, Math.min(0.5, commission));
        waitSeconds = Math.max(20, Math.min(600, waitSeconds));
        turnSeconds = Math.max(5, Math.min(60, turnSeconds));
        winsNeeded = Math.max(1, Math.min(5, winsNeeded));
        return this;
    }

    /** Gain du vainqueur pour une mise (pot de deux mises moins la commission). */
    public long prize(long stake) {
        long pot = stake * 2;
        return pot - (long) Math.floor(pot * commission);
    }
}
