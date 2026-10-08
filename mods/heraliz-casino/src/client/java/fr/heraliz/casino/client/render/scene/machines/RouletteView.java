package fr.heraliz.casino.client.render.scene.machines;

/**
 * Etat de la roulette lu par son modele 3D.
 */
public final class RouletteView extends RoundView {
    public int zeros = 2;
    public int number = -1;
    public int spinTicks = 220;
    /** Angle du cylindre au lancement (base de la rotation du tour). */
    public float wheelStart;
    public float ballStart;
    /** Au repos : angle du cylindre et ticks depuis la fin du dernier tour. */
    public float restAngle;
    public float sinceRest;
    /** Ticks depuis le debut du lancer (RUNNING) ou depuis le lancer precedent (RESULT). */
    public float spinTime;
}
