package fr.heraliz.casino.game.slot;

/**
 * Suspense des machines a sous, comme dans les vrais casinos : quand il ne manque plus qu'un symbole pour declencher un
 * bonus, les rouleaux qui restent tournent au ralenti. Ces durees font partie de la chronologie d'une partie : elles
 * sont calculees a l'identique par le serveur (versement du gain a la fin) et par les clients (animation).
 */
public final class Suspense {
    private Suspense() {
    }

    /** Ticks de ralenti ajoutes a un rouleau en suspens (1,7 seconde : on a le temps d'y croire). */
    public static final int HOLD = 34;
    /** Ticks ajoutes a la chute d'une colonne en suspens (grilles a cascades). */
    public static final int HOLD_DROP = 26;
    /** Ralenti plus court pour un petit enjeu (un tour gratuit de plus). */
    public static final int HOLD_SHORT = 22;
    /** Vitesse d'un rouleau lance (cases par tick) : les rouleaux tournent plus longtemps, pas plus vite. */
    public static final float REEL_SPEED = 1.05f;

    /**
     * Cases a faire defiler a pleine vitesse pour un rouleau qui tourne "spin" ticks avant de s'arreter (ou de commencer
     * son ralenti) : un rouleau retarde par le suspense des precedents tourne plus longtemps, a la meme vitesse.
     */
    public static int fastCells(float spin, int rows) {
        return Math.max(4, Math.round(REEL_SPEED * Math.max(1f, spin - 1)) - rows);
    }

    /**
     * Cases parcourues apres t ticks de ralenti. Le rouleau arrive a "speed" cases par tick, freine en quelques ticks
     * puis avance au pas ; il a parcouru exactement "cells" cases au bout de "total" ticks.
     */
    public static float slow(float t, float total, float speed, float cells) {
        if (t <= 0) return 0;
        if (t >= total) return cells;
        // le freinage ne consomme pas plus de 45 % du trajet : le reste se fait au pas, bien visible
        double tau = Math.min(2.5, cells * 0.45 / Math.max(0.05, speed));
        double e = tau * (1 - Math.exp(-total / tau));
        double creep = (cells - speed * e) / (total - e);
        double p = creep * t + (speed - creep) * tau * (1 - Math.exp(-t / tau));
        return (float) Math.min(cells, p);
    }

    /** Progression (0..1) d'un rouleau lance a pleine vitesse, sans freinage : acceleration breve puis vitesse constante. */
    public static float cruise(float u) {
        final float a = 0.1f;
        u = u < 0 ? 0 : Math.min(1f, u);
        float e = u < a ? u * u / (2 * a) : a / 2 + (u - a);
        return e / (1 - a / 2);
    }

    /** Vitesse (progression par unite de u) a la fin de cruise(). */
    public static float cruiseSpeed() {
        return 1 / (1 - 0.1f / 2);
    }

    /** Intensite du suspense (0..1) a l'instant t d'un ralenti de duree total : monte vite, culmine juste avant l'arret. */
    public static float tension(float t, float total) {
        if (t < 0 || t > total) return 0;
        float u = t / total;
        return Math.min(1f, u * 4) * (0.55f + 0.45f * u);
    }

    /** Vrai aux instants d'un battement de coeur : de plus en plus rapproches (tous les 9, 7 puis 5 ticks). */
    public static boolean beat(int t, int total) {
        if (t < 0 || t >= total) return false;
        int third = total / 3;
        if (t < third) return t % 9 == 0;
        if (t < 2 * third) return (t - third) % 7 == 0;
        return (t - 2 * third) % 5 == 0;
    }
}
