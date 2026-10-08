package fr.heraliz.casino.game.slot;

import java.util.random.RandomGenerator;

/**
 * Machine a sous "a sequence" (Rotten, Portes de l'Olympe, Souffle du Loup) : toute la partie (tour, cascades,
 * relances, tours gratuits) est tiree d'un coup au lancement ; les clients la rejouent a l'identique et le gain est
 * verse a la fin de l'animation.
 */
public interface SlotEngine {
    /** Partie tiree : donnees rejouees par les clients, gain total (millemes de la mise), duree de l'animation (ticks). */
    record Play(int[] data, long totalMilli, int duration) {
    }

    /** Etat affiche a un instant de la partie (ligne d'etat de l'ecran). */
    record Status(int mode, int kind, int freeDone, int freeTotal, int respins) {
        /** mode : 0 jeu normal, 1 et 2 = les deux bonus de la machine ; kind : 0 tour, 1 relance / cascade, 2 tour gratuit. */
        public static final Status NONE = new Status(0, 0, 0, 0, 0);
    }

    Play play(RandomGenerator rng, int buy);

    /** Prix de l'achat du bonus 1 ou 2 (x la mise), 0 = indisponible. */
    double buyPrice(int buy);

    long minBet();

    long maxBet();
}
