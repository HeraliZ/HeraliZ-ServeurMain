package com.Spartan.check.movement.model;

/**
 * Physique verticale du joueur vanilla 1.20.1, sans dépendance Minecraft
 * (testable unitairement).
 */
public final class Physics {
    /** Gravité vanilla (blocs/tick²) ; un mod peut la changer via l'attribut forge:entity_gravity. */
    public static final double GRAVITY = 0.08;
    /** Frottement de l'air sur la vitesse verticale, appliqué à chaque tick. */
    public static final double DRAG = 0.98;
    /** Vitesse verticale d'un saut sans effet Saut amélioré. */
    public static final double BASE_JUMP = 0.42;

    /** Écart d'arrivée en dessous duquel deux positions sont considérées consécutives (gigue réseau). */
    public static final long JITTER_NANOS = 150_000_000L;
    /** Durée maximale attribuée à un seul échantillon (au-delà : client figé, pas de mouvement). */
    public static final int MAX_SAMPLE_TICKS = 5;

    private Physics() {
    }

    /**
     * Nombre de ticks couverts par une position, d'après l'écart d'arrivée avec la
     * précédente. Un client vanilla envoie sa position à chaque tick où il bouge :
     * des positions rapprochées (même en rafale après un lag) valent 1 tick chacune.
     * Un client qui espace volontairement ses positions se voit compter le temps
     * réel écoulé (plafonné), pour qu'il ne puisse pas « étirer » les fenêtres.
     */
    public static int ticksCovered(long gapNanos) {
        if (gapNanos <= JITTER_NANOS) {
            return 1;
        }
        return (int) Math.max(1, Math.min(MAX_SAMPLE_TICKS, Math.round(gapNanos / 50_000_000.0)));
    }

    /** Hauteur maximale atteinte avec une vitesse verticale initiale v0 (gravité g). */
    public static double jumpHeight(double v0, double gravity) {
        double y = 0;
        double v = v0;
        for (int i = 0; i < 1000 && v > 0; i++) {
            y += v;
            v = (v - gravity) * DRAG;
        }
        return y;
    }
}
