package com.Spartan.check.movement;

/**
 * Ce qu'un client a fait d'un recul imposé par le serveur, une fois qu'il l'a forcément
 * appliqué (il a répondu au ping qui le suivait) et que rien ne le bloquait.
 *
 * @param expectedH vitesse horizontale imposée (0 = non jugée)
 * @param along     plus grand déplacement dans le sens du recul pendant la fenêtre
 * @param expectedUp vitesse verticale imposée (0 = non jugée)
 * @param rise      plus grande montée pendant la fenêtre
 * @param ignored   le recul n'apparaît pas dans ses positions
 */
public record VelocityOutcome(double expectedH, double along, double expectedUp, double rise, boolean ignored) {
}
