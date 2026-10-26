package com.Spartan.check.movement.model;

import java.util.ArrayDeque;

/**
 * Horloge du client bornée par nos pings (Java pur, testé).
 *
 * Un client vanilla traite les paquets reçus au début de chaque image, AVANT d'y jouer
 * ses ticks, et envoie au plus un paquet de mouvement par tick. Une fois qu'il a répondu
 * au ping k (que le serveur a envoyé à l'instant S_k), il ne peut donc pas avoir joué plus
 * de (maintenant − S_k) / 50 ms ticks, plus les 10 ticks de rattrapage qu'il s'accorde
 * après un gel. On vérifie cette borne pour chaque ping des 30 dernières secondes.
 *
 * Contrairement à un crédit de temps (TimerBalance) :
 * - aucun crédit ne s'accumule en restant immobile : accélérer le jeu est vu en moins
 *   d'une seconde à ×2, en quelques secondes à ×1,05 ;
 * - le lag ne cause jamais de faux positif : un paquet retardé arrive avec son pong
 *   retardé (TCP garde l'ordre), la borne se décale d'autant.
 */
public final class ClockBound {
    public static final long TICK_NANOS = 50_000_000L;
    /** Rattrapage vanilla après un gel (10 ticks par image au plus) + marge. */
    public static final double SLACK_TICKS = 12;
    /** Durée de vie d'une référence : la dérive des horloges y reste négligeable. */
    public static final long WINDOW_NANOS = 30_000_000_000L;

    private record Ref(long sentNanos, double value) {
    }

    /** Références dont la valeur croît de l'avant vers l'arrière : la tête est le minimum. */
    private final ArrayDeque<Ref> refs = new ArrayDeque<>();
    private long packets;
    private long origin;
    private boolean started;

    private double ticks(long nanos) {
        if (!started) {
            origin = nanos;
            started = true;
        }
        return (nanos - origin) / (double) TICK_NANOS;
    }

    /** Le client a répondu au ping envoyé à {@code sentNanos} (après tous les paquets déjà comptés). */
    public void onPong(long sentNanos) {
        double value = packets - ticks(sentNanos);
        while (!refs.isEmpty() && refs.peekLast().value() >= value) {
            refs.removeLast(); // ne sera jamais plus contraignante que la nouvelle
        }
        refs.addLast(new Ref(sentNanos, value));
    }

    /**
     * Un paquet de mouvement (un tick du client).
     * @return l'avance du client sur l'horloge du serveur, en ticks (≤ 0 en temps normal,
     *         NEGATIVE_INFINITY sans référence)
     */
    public double onPacket(long nanos) {
        packets++;
        while (!refs.isEmpty() && nanos - refs.peekFirst().sentNanos() > WINDOW_NANOS) {
            refs.removeFirst();
        }
        if (refs.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        return packets - ticks(nanos) - refs.peekFirst().value();
    }

    /** Après un signalement : on repart des prochains pongs. */
    public void reset() {
        refs.clear();
    }
}
