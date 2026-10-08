package fr.heraliz.casino.game.crash;

/**
 * Crash ("La Fusee") : le multiplicateur grimpe jusqu'a l'explosion ; il faut encaisser avant.
 * Le point d'explosion suit P(explosion >= x) = (1 - avantage) / x : quelle que soit la strategie,
 * la redistribution vaut 1 - avantage (et la fusee explose immediatement dans "avantage" % des tours).
 */
public class CrashRules {
    public long minBet = 1;
    public long maxBet = 5000;
    /** Avantage de la maison (0.07 = 7 %). */
    public double houseEdge = 0.07;
    public double maxMultiplier = 1000;
    /** Croissance du multiplicateur : x = e^(croissance * secondes). */
    public double growth = 0.075;
    public int bettingSeconds = 12;

    public CrashRules sanitized() {
        houseEdge = Math.max(0.01, Math.min(0.5, houseEdge));
        maxMultiplier = Math.max(2, Math.min(100000, maxMultiplier));
        growth = Math.max(0.02, Math.min(0.5, growth));
        bettingSeconds = Math.max(5, Math.min(60, bettingSeconds));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }
}
