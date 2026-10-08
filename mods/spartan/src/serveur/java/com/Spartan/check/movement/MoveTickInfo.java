package com.Spartan.check.movement;

/**
 * Constats du moteur de mouvement en fin de tick.
 *
 * @param withheldSeconds durée sans position alors que le client agissait (0 = rien)
 * @param unackedSeconds  durée d'une téléportation jamais confirmée malgré l'activité du client (0 = rien)
 * @param flooded         le client a envoyé trop de paquets pour être analysés
 * @param ignoredPingsSeconds le client bouge mais ne répond plus à nos pings depuis autant (0 = rien)
 * @param forgedPongs     réponses à des pings jamais envoyés (ou en double, ou dans le désordre)
 */
public record MoveTickInfo(double withheldSeconds, double unackedSeconds, boolean flooded,
                           double ignoredPingsSeconds, int forgedPongs) {
}
