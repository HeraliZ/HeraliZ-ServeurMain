package fr.heraliz.noyau.client.kit;

import net.minecraft.Util;

/**
 * Animations des HUD (refonte du 25/10, docs/HUD_REFONTE.md) : courbes, lissage indépendant du nombre d'images, fondu
 * d'apparition (180 ms) et de disparition (250 ms). Tout se mesure en temps réel ({@link Util#getMillis}), jamais en
 * images : même rythme à 30 et à 240 images par seconde. Aucune allocation.
 */
public final class Anim {
    private Anim() {
    }

    /** Durées des fondus de la charte : apparition, disparition (ms). */
    public static final int ENTREE_MS = 180, SORTIE_MS = 250;

    public static float borner(float t) {
        return t < 0f ? 0f : t > 1f ? 1f : t;
    }

    /** Sortie douce (cubique) : rapide au début, se pose à la fin. */
    public static float sortie(float t) {
        float u = 1f - borner(t);
        return 1f - u * u * u;
    }

    /** Entrée-sortie douce (smoothstep). */
    public static float douce(float t) {
        float u = borner(t);
        return u * u * (3f - 2f * u);
    }

    /** Rapproche {@code actuel} de {@code cible} (lissage exponentiel, {@code vitesse} par seconde). */
    public static float lisser(float actuel, float cible, float dtSecondes, float vitesse) {
        float dt = Math.max(0f, Math.min(0.25f, dtSecondes));
        return actuel + (cible - actuel) * (1f - (float) Math.exp(-vitesse * dt));
    }

    /** Progression 0..1 d'une animation commencée à {@code depuisMs} et longue de {@code dureeMs}. */
    public static float progression(long depuisMs, int dureeMs) {
        return borner((Util.getMillis() - depuisMs) / (float) Math.max(1, dureeMs));
    }

    /** Horloge d'un HUD : secondes écoulées depuis le dernier appel (0 au premier, ou après une longue pause). */
    public static final class Horloge {
        private long dernier = -1;

        public float dt() {
            long m = Util.getMillis();
            long d = dernier < 0 ? 0 : m - dernier;
            dernier = m;
            return d > 500 ? 0f : d / 1000f;
        }
    }

    /**
     * Fondu d'un HUD : monte en {@link #ENTREE_MS}, descend en {@link #SORTIE_MS}, courbe de sortie cubique ou, pour un
     * fondu plus doux, entrée-sortie douce (aucun saut si l'on change de sens en cours de route). A appeler à chaque image
     * avec « doit être visible » ; le HUD garde ce qu'il montrait tant que {@link #visible()} est vrai (état de sortie).
     */
    public static final class Fondu {
        private final int entreeMs, sortieMs;
        private final boolean doux;
        private final Horloge horloge = new Horloge();
        private float t;

        public Fondu() {
            this(ENTREE_MS, SORTIE_MS, false);
        }

        public Fondu(int entreeMs, int sortieMs) {
            this(entreeMs, sortieMs, false);
        }

        /** {@code doux} : courbe entrée-sortie douce (smoothstep) au lieu de la sortie cubique. */
        public Fondu(int entreeMs, int sortieMs, boolean doux) {
            this.entreeMs = Math.max(1, entreeMs);
            this.sortieMs = Math.max(1, sortieMs);
            this.doux = doux;
        }

        /** Avance d'une image vers visible ou caché ; rend l'opacité (0..1). */
        public float maj(boolean visible) {
            float dt = Math.min(0.1f, horloge.dt()) * 1000f;
            t = visible ? Math.min(1f, t + dt / entreeMs) : Math.max(0f, t - dt / sortieMs);
            if (visible && t == 0f) t = 0.0001f; // visible des la premiere image (opacite quasi nulle)
            return valeur();
        }

        public float valeur() {
            return doux ? douce(t) : sortie(t);
        }

        public boolean visible() {
            return t > 0f;
        }

        /** Coupe net (changement de monde, mort). */
        public void couper() {
            t = 0f;
        }
    }
}
