package com.Spartan.check.movement;

/**
 * Un paquet de mouvement tel qu'envoyé par le client (position et/ou rotation
 * et/ou sol). charged=false pour la réponse à une téléportation, envoyée hors
 * de la cadence normale d'un paquet par tick.
 */
public record MovePacket(long nanos, boolean hasPos, boolean hasRot, float yRot, float xRot,
                         boolean onGround, boolean charged) {
}
