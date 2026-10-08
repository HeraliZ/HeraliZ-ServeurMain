package fr.heraliz.casino.client.render.scene;

/**
 * Contexte d'une image pour un modele de machine : horloge du monde, camera, mode objet.
 */
public final class Scene {
    /** Periode de l'horloge d'ambiance (ticks) : garde une bonne precision en virgule flottante. */
    public static final long CLOCK_PERIOD = 240_000L;

    /** Heure du monde (ticks) et fraction de tick. */
    public long tick;
    public float partial;
    /** Horloge d'ambiance continue (ticks). */
    public float time;
    /** Rendu en tant qu'objet (inventaire, main) : pas d'effets, pose de repos. */
    public boolean item;
    /** Valeur propre a la machine (0..1). */
    public float seed;
    /** Camera dans le repere du modele (px). */
    public float camX = 8, camY = 24, camZ = 80;
    /** Le joueur local est celui qui joue sur cette machine. */
    public boolean mine;

    public Scene set(long tick, float partial) {
        this.tick = tick;
        this.partial = partial;
        this.time = (tick % CLOCK_PERIOD) + partial;
        return this;
    }

    /** Ticks ecoules depuis l'instant t (fraction comprise). */
    public float since(long t) {
        long d = tick - t;
        if (d > 1_000_000L) return 1_000_000f;
        if (d < -1_000_000L) return -1_000_000f;
        return d + partial;
    }

    public float pulse(float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed + seed * 6.2831f);
    }

    /** Scene de repos (inventaire, apercu). */
    public static Scene rest(boolean item) {
        Scene s = new Scene().set(1000, 0);
        s.item = item;
        return s;
    }
}
