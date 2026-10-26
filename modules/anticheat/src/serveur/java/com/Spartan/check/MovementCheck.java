package com.Spartan.check;

import com.Spartan.check.movement.MovePacket;
import com.Spartan.check.movement.MoveSample;
import com.Spartan.check.movement.MoveTickInfo;
import com.Spartan.check.movement.VelocityOutcome;
import net.minecraft.server.level.ServerPlayer;

/**
 * Détection basée sur le mouvement, alimentée par le moteur de mouvement
 * (MovementEngine) avec ce que le client envoie réellement, paquet par paquet.
 * C'est la famille de détections infalsifiables par le client.
 */
public abstract class MovementCheck extends Check {
    protected MovementCheck(String name) {
        super(name, "movement");
    }

    /** Pour les détections évaluées à chaque tick mais qui ne sont pas du mouvement. */
    protected MovementCheck(String name, String category) {
        super(name, category);
    }

    /** Un déplacement revendiqué par le client (paquet avec position). */
    public void onSample(ServerPlayer player, MoveSample sample) {
    }

    /** Chaque paquet de mouvement (position, rotation ou sol seul). */
    public void onMovePacket(ServerPlayer player, MovePacket packet) {
    }

    /** Constats du moteur en fin de tick (positions retenues, téléportation non confirmée…). */
    public void onTickEnd(ServerPlayer player, MoveTickInfo info) {
    }

    /**
     * Le client a répondu à notre ping envoyé à {@code sentNanos} (System.nanoTime), dans
     * l'ordre exact du flux : les paquets de mouvement déjà vus le précèdent.
     */
    public void onPong(ServerPlayer player, long sentNanos, long nanos) {
    }

    /** Ce que le client a fait d'un recul qu'il a forcément appliqué (voir VelocityOutcome). */
    public void onVelocity(ServerPlayer player, VelocityOutcome outcome) {
    }

    /** Contrôle par tick indépendant des paquets (effets…). */
    public void check(ServerPlayer player) {
    }
}
