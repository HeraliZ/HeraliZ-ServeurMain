package com.Spartan.check.movement.model;

/**
 * Détection « Timer » (jeu accéléré) : un client vanilla envoie au plus un
 * paquet de mouvement par tick, soit un toutes les 50 ms en moyenne. Chaque
 * paquet coûte 50 ms ; le temps réel écoulé crédite. Un client qui rame puis
 * rattrape (rafale) dépense son crédit accumulé ; un client accéléré finit à
 * découvert.
 *
 * Le crédit est plafonné : on ne peut pas « économiser » du temps en restant
 * immobile pour accélérer longtemps ensuite.
 */
public final class TimerBalance {
    public static final long TICK_NANOS = 50_000_000L;
    public static final long MAX_CREDIT_NANOS = 3_000_000_000L;
    public static final long FLAG_DEBT_NANOS = -500_000_000L;

    private long balance = MAX_CREDIT_NANOS;
    private long lastNanos;
    private boolean started;

    /**
     * @param nanos  instant d'arrivée du paquet
     * @param charge false pour un paquet hors cadence légitime (réponse à une téléportation)
     * @return true si le client est en avance de plus de 500 ms sur le temps réel
     */
    public boolean onPacket(long nanos, boolean charge) {
        if (started) {
            balance = Math.min(MAX_CREDIT_NANOS, balance + Math.max(0, nanos - lastNanos));
        }
        started = true;
        lastNanos = nanos;
        if (!charge) {
            return false;
        }
        balance -= TICK_NANOS;
        if (balance < FLAG_DEBT_NANOS) {
            balance = 0;
            return true;
        }
        return false;
    }

    public long balanceNanos() {
        return balance;
    }
}
