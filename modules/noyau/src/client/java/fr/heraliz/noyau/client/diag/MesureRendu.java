package fr.heraliz.noyau.client.diag;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Temps de dessin par type d'entité et d'entité de bloc (mixins sur les deux répartiteurs de rendu de Minecraft),
 * seulement pendant un enregistrement {@link DiagClient} : sinon une seule lecture de booléen par objet dessiné. Fil de
 * rendu seulement. Le temps compté est celui où le jeu prépare les sommets de l'objet (le vrai coût de chaque conteneur
 * ou prop dessiné un par un, à chaque image).
 */
public final class MesureRendu {
    private MesureRendu() {
    }

    static volatile boolean actif;
    private static final long[] PILE = new long[32];
    private static int prof;
    /** type -> {nanos, appels} */
    static final Map<Object, long[]> ENTITES = new IdentityHashMap<>(), BLOCS = new IdentityHashMap<>();

    public static void debut() {
        if (!actif) return;
        if (prof < PILE.length) PILE[prof] = System.nanoTime();
        prof++;
    }

    public static void finEntite(Object type) {
        fin(type, ENTITES);
    }

    public static void finBloc(Object type) {
        fin(type, BLOCS);
    }

    private static void fin(Object type, Map<Object, long[]> m) {
        if (!actif || prof <= 0) return;
        prof--;
        if (prof >= PILE.length) return;
        long d = System.nanoTime() - PILE[prof];
        long[] a = m.computeIfAbsent(type, k -> new long[2]);
        a[0] += d;
        a[1]++;
    }

    /** Début d'une image : une pile mal fermée (exception pendant un rendu) ne fausse pas la suite. */
    static void image() {
        prof = 0;
    }

    static void demarrer() {
        ENTITES.clear();
        BLOCS.clear();
        prof = 0;
        actif = true;
    }

    static void arreter() {
        actif = false;
    }
}
