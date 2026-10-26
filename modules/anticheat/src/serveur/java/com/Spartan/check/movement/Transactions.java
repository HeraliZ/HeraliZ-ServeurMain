package com.Spartan.check.movement;

/**
 * Points de synchronisation avec le client : un ping vanilla (ClientboundPingPacket)
 * par tick et par joueur, auquel le client répond par un pong.
 *
 * Le client traite les paquets dans l'ordre d'arrivée, au début de chaque image et
 * avant d'y jouer ses ticks. Quand le pong du ping k arrive, le client a donc appliqué
 * TOUT ce que le serveur lui avait envoyé avant ce ping (recul, explosion, effet, vol
 * autorisé…), et chacune de ses positions suivantes en tient compte. Une vitesse ou
 * un état imposé est ainsi suivi exactement, quel que soit le lag, au lieu d'une
 * fenêtre de temps qu'un réseau instable dépasse et qu'un tricheur peut exploiter.
 *
 * Identifiants : 0x535xxxxx (« SP »), numérotés ; ceux d'autres mods ou plugins sont
 * ignorés. Thread serveur (le moteur rejoue les événements PING / PONG dans l'ordre
 * exact des échanges réseau).
 */
final class Transactions {
    static final int BASE = 0x53500000;
    static final int MASK = 0x000FFFFF;
    private static final int RING = 1024;

    /** Résultat d'un pong. */
    enum Pong {
        OK,
        /** Identifiant d'un autre mod ou plugin. */
        FOREIGN,
        /** Réponse à un ping jamais envoyé (ou en double) : impossible pour un client vanilla. */
        FORGED
    }

    private long nextSeq = 1;
    private long written;
    private long ponged;
    private long pongNanos;
    private final long[] writtenNanos = new long[RING];

    static boolean ours(int id) {
        return (id & ~MASK) == BASE;
    }

    /** Identifiant du ping à envoyer maintenant. */
    int next() {
        int id = BASE | (int) (nextSeq & MASK);
        nextSeq++;
        return id;
    }

    /** Numéro du prochain ping qui sera envoyé : un état observé maintenant sera connu du client à son pong. */
    long upcoming() {
        return nextSeq;
    }

    /** Dernier ping vu partir sur le réseau. */
    long written() {
        return written;
    }

    /** Dernier ping auquel le client a répondu. */
    long ponged() {
        return ponged;
    }

    long lastPongNanos() {
        return pongNanos;
    }

    /** Le client a-t-il répondu au ping {@code seq} (et donc appliqué tout ce qui le précédait) ? */
    boolean acked(long seq) {
        return ponged >= seq;
    }

    /** Au moins une réponse reçue : les fenêtres exactes sont utilisables. */
    boolean live() {
        return ponged > 0;
    }

    /** Ping parti sur le réseau (observé par PacketObserver, dans l'ordre). */
    void onWritten(int id, long nanos) {
        long seq = written + 1 + (((id & MASK) - (written + 1)) & MASK);
        if (seq >= nextSeq) {
            return; // pas l'un des nôtres finalement
        }
        written = seq;
        writtenNanos[(int) (seq % RING)] = nanos;
    }

    /** Instant d'envoi du ping {@code seq}, ou 0 s'il est trop ancien ou inconnu. */
    long writtenNanos(long seq) {
        return seq > written || written - seq >= RING ? 0 : writtenNanos[(int) (seq % RING)];
    }

    /** Envoi du plus ancien ping sans réponse, ou 0 si tous ont une réponse. */
    long oldestUnansweredNanos() {
        return ponged >= written ? 0 : writtenNanos(ponged + 1);
    }

    Pong onPong(int id, long nanos) {
        if (!ours(id)) {
            return Pong.FOREIGN;
        }
        long seq = ponged + 1 + (((id & MASK) - (ponged + 1)) & MASK);
        if (seq >= nextSeq) {
            return Pong.FORGED;
        }
        ponged = seq;
        pongNanos = nanos;
        return Pong.OK;
    }
}
