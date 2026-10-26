package fr.heraliz.casino.client.render.scene;

import fr.heraliz.casino.registry.Snd;
/**
 * Emission de particules et de sons (coordonnees du modele, en pixels ; vitesses en px/tick).
 */
public interface FxSink {
    int SPARK = 0;
    int DOT = 1;
    int CONFETTI = 2;
    int BUBBLE = 3;
    int EMBER = 4;
    int SMOKE = 5;
    int FLAME = 6;
    int SNOW = 7;
    int LEAF = 8;
    int DROP = 9;
    int WISP = 10;
    int PAPER = 11;
    int FLY = 12;
    int SHARD = 13;

    /** Particule du mod. size en px. */
    void particle(int kind, float x, float y, float z, float vx, float vy, float vz, int rgb, float size, int life);

    void sound(Snd sound, float x, float y, float z, float volume, float pitch);

    /** Ne fait rien (rendu objet). */
    FxSink NONE = new FxSink() {
        @Override
        public void particle(int kind, float x, float y, float z, float vx, float vy, float vz, int rgb, float size, int life) {
        }

        @Override
        public void sound(Snd sound, float x, float y, float z, float volume, float pitch) {
        }
    };
}
