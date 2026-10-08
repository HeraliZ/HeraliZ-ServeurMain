package fr.heralisz.matchs.duels.client;

/**
 * Marque des ecrans des duels : le HUD est masque derriere eux ({@link DuelsClient}), et le banc d'essai peut journaliser
 * leurs rectangles ({@code "duelLog"}).
 */
interface EcranDuels {
    /** Rectangles et etat de l'ecran, en une ligne (journal du banc). */
    String journal();
}
