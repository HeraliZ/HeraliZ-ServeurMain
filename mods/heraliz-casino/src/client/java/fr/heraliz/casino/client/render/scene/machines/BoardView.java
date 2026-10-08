package fr.heraliz.casino.client.render.scene.machines;

/**
 * Classement affiche par un vieil ecran : plus gros gains (0), plus grosses pertes (1) ou plus gros parieurs (2).
 */
public final class BoardView {
    public int kind;
    public String[] names = new String[0], machines = new String[0];
    public long[] values = new long[0];
    public String recordName = "", recordMachine = "";
    public long recordAmount;
}
