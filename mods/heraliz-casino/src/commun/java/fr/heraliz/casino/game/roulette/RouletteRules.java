package fr.heraliz.casino.game.roulette;

/**
 * Roulette (partie commune : tout le monde mise pendant le compte a rebours, puis la bille est lancee).
 * Par defaut roulette "americaine" a deux zeros (0 et 00) : avantage de la maison 5,26 %.
 */
public class RouletteRules {
    /** Mise minimale par case. */
    public long minBet = 1;
    /** Mise maximale totale d'un joueur sur un tour. */
    public long maxBet = 10000;
    /** 1 = roulette europeenne (0), 2 = roulette americaine (0 et 00). */
    public int zeros = 2;
    public int bettingSeconds = 25;
    public int spinSeconds = 11;

    public RouletteRules sanitized() {
        zeros = zeros == 1 ? 1 : 2;
        bettingSeconds = Math.max(8, Math.min(120, bettingSeconds));
        spinSeconds = Math.max(6, Math.min(20, spinSeconds));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }
}
