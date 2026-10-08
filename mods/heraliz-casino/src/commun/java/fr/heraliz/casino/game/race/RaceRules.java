package fr.heraliz.casino.game.race;

/**
 * Course de chevaux : 5 chevaux, une "forme" tiree a chaque course et des cotes affichees.
 * Les cotes contiennent la marge du bookmaker : chaque pari rend "rtp" en moyenne (85 % par defaut).
 */
public class RaceRules {
    public long minBet = 1;
    public long maxBet = 5000;
    public double rtp = 0.85;
    public int bettingSeconds = 25;
    public int raceSeconds = 12;

    public static final int HORSES = 5;
    public static final String[] NAMES = {"Eclair", "Tonnerre", "Comete", "Bourrasque", "Mirage"};
    public static final int[] COLORS = {0xE8323C, 0x3F8CE8, 0xF2B233, 0x4FC36A, 0xB45BE8};

    public RaceRules sanitized() {
        rtp = Math.max(0.5, Math.min(0.98, rtp));
        bettingSeconds = Math.max(8, Math.min(120, bettingSeconds));
        raceSeconds = Math.max(6, Math.min(30, raceSeconds));
        if (minBet < 1) minBet = 1;
        if (maxBet < minBet) maxBet = minBet;
        return this;
    }
}
