package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.game.plinko.PlinkoRules;

/**
 * Etat du Plinko lu par son modele 3D : billes en vol et multiplicateurs des paniers.
 */
public final class PlinkoView {
    public static final int MAX = 48;
    public int count;
    public final int[] path = new int[MAX];
    /** Ticks depuis le lancement de chaque bille (fraction comprise). */
    public final float[] t = new float[MAX];
    public final boolean[] mine = new boolean[MAX];
    public double[] multipliers = new PlinkoRules().multipliers.clone();
}
