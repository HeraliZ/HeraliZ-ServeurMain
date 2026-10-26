package fr.heraliz.casino.game.cards;

/**
 * Blackjack contre la banque (sabot infini : chaque carte est tiree au hasard).
 * Regles favorables a la maison : le blackjack paie 6 contre 5, le croupier tire sur 17 souple,
 * un seul partage, pas d'assurance ni d'abandon. Avantage de la maison ~2 %.
 */
public class BlackjackRules {
    public long minBet = 5;
    public long maxBet = 5000;
    /** Gain d'un blackjack (1.2 = 6 contre 5 ; 1.5 = 3 contre 2). */
    public double blackjackPays = 1.2;
    /** Le croupier tire sur 17 souple. */
    public boolean dealerHitsSoft17 = true;
    public boolean allowDouble = true;
    public boolean allowSplit = true;
    /** Temps de reflexion avant de "rester" automatiquement (secondes). */
    public int decisionSeconds = 30;

    public BlackjackRules sanitized() {
        blackjackPays = Math.max(1.0, Math.min(2.0, blackjackPays));
        decisionSeconds = Math.max(10, Math.min(120, decisionSeconds));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }
}
