package fr.heraliz.interfaces.kit;

import net.minecraft.Util;

/** Petites aides d'animation des interfaces (courbes d'acceleration, lissage independant du nombre d'images). */
public final class Anim {
    private Anim() {
    }

    public static float borner(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    /** Sortie douce (cubique) : rapide au debut, se pose a la fin. */
    public static float sortie(float t) {
        float u = 1 - borner(t);
        return 1 - u * u * u;
    }

    /** Progression 0..1 d'une animation commencee a {@code depuisMs} (Util.getMillis) avec un retard et une duree. */
    public static float progression(long depuisMs, long retardMs, long dureeMs) {
        return borner((Util.getMillis() - depuisMs - retardMs) / (float) Math.max(1, dureeMs));
    }

    /** Rapproche {@code actuel} de {@code cible} (lissage exponentiel, {@code vitesse} par seconde). */
    public static float vers(float actuel, float cible, float dtSecondes, float vitesse) {
        return actuel + (cible - actuel) * (1 - (float) Math.exp(-vitesse * Math.max(0, Math.min(0.25f, dtSecondes))));
    }

    /** Horloge d'un widget : temps ecoule (s) depuis le dernier appel. */
    public static final class Horloge {
        private long dernier = -1;

        public float dt() {
            long m = Util.getMillis();
            float d = dernier < 0 ? 0 : (m - dernier) / 1000f;
            dernier = m;
            return d;
        }
    }
}
