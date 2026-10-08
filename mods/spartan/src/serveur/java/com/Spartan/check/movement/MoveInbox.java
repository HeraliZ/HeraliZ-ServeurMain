package com.Spartan.check.movement;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;

/**
 * File des paquets observés pour un joueur. Remplie par le thread réseau
 * (PacketObserver), vidée par le thread serveur (MovementEngine) à chaque tick.
 * Entrants et sortants d'une connexion passent par le même thread Netty : l'ordre
 * de la file est l'ordre réel des échanges.
 */
final class MoveInbox {
    /** Au-delà, le client inonde le serveur : les paquets suivants ne sont plus analysés. */
    static final int MAX_EVENTS = 4096;

    enum Kind {
        MOVE,          // ServerboundMovePlayerPacket (position et/ou rotation et/ou sol)
        ACTION,        // attaque, interaction, clic, casse, swing : preuve que le client tourne
        ACK,           // le client confirme une téléportation
        TELEPORT_SENT, // le serveur impose une position
        IMPULSE,       // le serveur impose une vitesse (recul, explosion, propulsion de mod) : x, y, z = vitesse
        PING,          // notre ping part vers le client (voir Transactions)
        PONG,          // le client répond à un ping
        USE_CHANGE     // le client relâche, change ou lâche l'objet en main : son ralentissement peut cesser
    }

    /**
     * @param additive IMPULSE : la vitesse s'ajoute à celle du joueur (explosion) au lieu de la remplacer (recul)
     */
    record Event(Kind kind, long nanos, boolean hasPos, double x, double y, double z,
                 boolean hasRot, float yRot, float xRot, boolean onGround, int id, Vec3 target, boolean additive) {
        static Event move(long nanos, boolean hasPos, double x, double y, double z,
                          boolean hasRot, float yRot, float xRot, boolean onGround) {
            return new Event(Kind.MOVE, nanos, hasPos, x, y, z, hasRot, yRot, xRot, onGround, 0, null, false);
        }

        static Event simple(Kind kind, long nanos, int id, Vec3 target) {
            return new Event(kind, nanos, false, 0, 0, 0, false, 0, 0, false, id, target, false);
        }

        static Event impulse(long nanos, double vx, double vy, double vz, boolean additive) {
            return new Event(Kind.IMPULSE, nanos, false, vx, vy, vz, false, 0, 0, false, 0, null, additive);
        }
    }

    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private volatile boolean overflowed;

    void push(Event e) {
        if (size.incrementAndGet() > MAX_EVENTS) {
            size.decrementAndGet();
            overflowed = true;
            return;
        }
        queue.add(e);
    }

    Event poll() {
        Event e = queue.poll();
        if (e != null) {
            size.decrementAndGet();
        }
        return e;
    }

    /** Vrai si des paquets ont été perdus depuis le dernier appel (inondation). */
    boolean takeOverflow() {
        boolean o = overflowed;
        overflowed = false;
        return o;
    }
}
