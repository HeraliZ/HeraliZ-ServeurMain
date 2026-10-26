package fr.heraliz.emote.client.catalog;

/**
 * An emote the player can walk with (pushing a shopping cart...): Emotecraft stops
 * an emote as soon as its player moves, these keep playing while the player goes forward, slowly.
 * Filled by Gson from the catalog.
 */
public final class MoveSpec {
    /** Walking speed, as a share of the normal one. */
    public float speed = 0.35F;
    /** False: going sideways or backward stops the emote (a cart only goes forward). */
    public boolean sideways = false;
}
