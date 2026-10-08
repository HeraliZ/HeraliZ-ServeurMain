package fr.heraliz.casino.game.crash;

import java.util.random.RandomGenerator;

/**
 * Calculs du Crash (logique pure).
 */
public final class CrashGame {
    private CrashGame() {
    }

    /** Tire le point d'explosion (arrondi au centieme inferieur, au moins 1.00). */
    public static double crashPoint(CrashRules r, RandomGenerator rng) {
        double u = 1.0 - rng.nextDouble(); // ]0, 1]
        double c = (1.0 - r.houseEdge) / u;
        c = Math.floor(c * 100.0) / 100.0;
        return Math.max(1.0, Math.min(r.maxMultiplier, c));
    }

    /** Multiplicateur apres "ticks" ticks de vol (arrondi au centieme inferieur). */
    public static double multiplier(CrashRules r, double ticks) {
        return multiplier(r.growth, ticks);
    }

    public static double multiplier(double growth, double ticks) {
        double m = Math.exp(growth * Math.max(0, ticks) / 20.0);
        return Math.floor(m * 100.0 + 1e-9) / 100.0;
    }

    /** Multiplicateur x100 (entier) apres "ticks" ticks de vol. */
    public static long multiplier100(double growth, long ticks) {
        return Math.round(multiplier(growth, ticks) * 100.0);
    }

    /** Multiplicateur non arrondi (affichage fluide). */
    public static double smooth(double growth, double ticks) {
        return Math.exp(growth * Math.max(0, ticks) / 20.0);
    }

    /** Tick de vol ou le multiplicateur atteint "m". */
    public static long ticksFor(CrashRules r, double m) {
        if (m <= 1.0) return 0;
        return (long) Math.ceil(Math.log(m) / r.growth * 20.0);
    }
}
