package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.race.RaceRules;

/**
 * Etat de la course de chevaux lu par son modele 3D.
 */
public final class RaceView extends RoundView {
    public double[] odds = {2.5, 3.4, 4.2, 6.8, 12};
    public long seed = 12345;
    public int winner = -1;
    public int raceTicks = 240;

    public RaceView() {
        totals = new long[RaceRules.HORSES];
        mine = new long[RaceRules.HORSES];
    }
}
