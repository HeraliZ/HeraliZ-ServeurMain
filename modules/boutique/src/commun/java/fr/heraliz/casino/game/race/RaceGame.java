package fr.heraliz.casino.game.race;

import java.util.random.RandomGenerator;

/**
 * Cotes et deroulement deterministe d'une course (le serveur tire le gagnant ; le client rejoue la course
 * a partir de la graine et du gagnant : tout le monde voit la meme course).
 */
public final class RaceGame {
    private RaceGame() {
    }

    /** Chances de victoire de chaque cheval (somme 1). */
    public static double[] randomChances(RandomGenerator rng) {
        int n = RaceRules.HORSES;
        double[] s = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            s[i] = 0.35 + Math.pow(rng.nextDouble(), 1.6) * 1.9;
            sum += s[i];
        }
        for (int i = 0; i < n; i++) s[i] /= sum;
        return s;
    }

    /** Cote (somme rendue pour 1 mise) : rtp / chance, arrondie au dixieme inferieur, au moins 1.1. */
    public static double[] odds(double[] chances, double rtp) {
        double[] o = new double[chances.length];
        for (int i = 0; i < o.length; i++) o[i] = Math.max(1.1, Math.floor(rtp / chances[i] * 10.0) / 10.0);
        return o;
    }

    public static int pick(double[] chances, RandomGenerator rng) {
        double x = rng.nextDouble();
        for (int i = 0; i < chances.length; i++) {
            x -= chances[i];
            if (x < 0) return i;
        }
        return chances.length - 1;
    }

    /**
     * Progression (0 = depart, 1 = arrivee) du cheval "horse" apres t ticks d'une course de "duration" ticks.
     * Le gagnant franchit la ligne a "duration" ; les autres a peine apres, dans un ordre tire de la graine.
     */
    public static float progress(int horse, int winner, long seed, float t, int duration) {
        float u = t / duration;
        // rang d'arrivee des perdants (tire de la graine)
        int rank = 0;
        if (horse != winner) {
            rank = 1;
            for (int h = 0; h < RaceRules.HORSES; h++) {
                if (h == horse || h == winner) continue;
                if (hash(seed, h, 7) < hash(seed, horse, 7)) rank++;
            }
        }
        float finish = horse == winner ? 1f : 1f + 0.035f * rank + 0.02f * hash(seed, horse, 3);
        // allure : acceleration au depart, vagues de vitesse (retournements de situation), sprint final
        float w1 = (hash(seed, horse, 11) - 0.5f) * 0.09f, w2 = (hash(seed, horse, 13) - 0.5f) * 0.06f;
        float ph1 = hash(seed, horse, 17) * 6.28f, ph2 = hash(seed, horse, 19) * 6.28f;
        float v = u / finish;
        if (v >= 1f) return 1f + (u - finish) * 0.3f;
        float base = v < 0.08f ? v * v / 0.16f : v - 0.04f;
        base /= 0.96f;
        float wobble = (float) (w1 * Math.sin(v * Math.PI * 2 + ph1) * Math.sin(v * Math.PI)
                + w2 * Math.sin(v * Math.PI * 4 + ph2) * Math.sin(v * Math.PI));
        return Math.max(0f, Math.min(1f, base + wobble));
    }

    static float hash(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
