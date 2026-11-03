package fr.heralisz.clans.serveur;

import fr.heralisz.clans.HeraliszClans;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Economie de la beta (03/11, demande du proprietaire : « les ameliorations pareil, pour que tout soit un peu plus
 * simple ») : facteurs du reglage [beta] de HeraliZ Loot ({@code fr.heraliz.loot.EconomieBeta.facteur}, par reflexion,
 * aucune dependance), coupes avec lui par {@code actif = false}. Ici : « ameliorations » (herals de l'arbre du clan,
 * {@link fr.heralisz.clans.model.Arbre#betaPct}) et « quetes » (herals des quetes de clan tirees ensuite). Sans HeraliZ
 * Loot : 1. Relu au plus une fois par seconde et par cle.
 */
public final class EconomieBetaClans {
    private EconomieBetaClans() {
    }

    private static Method facteur;
    private static volatile boolean absent;
    private static final Map<String, double[]> CACHE = new ConcurrentHashMap<>();

    public static double facteur(String cle) {
        long t = System.currentTimeMillis();
        double[] c = CACHE.get(cle);
        if (c != null && t >= (long) c[1] && t - (long) c[1] < 1000) return c[0];
        double v = lire(cle);
        CACHE.put(cle, new double[]{v, t});
        return v;
    }

    private static double lire(String cle) {
        if (absent) return 1.0;
        try {
            if (facteur == null) facteur = Class.forName("fr.heraliz.loot.EconomieBeta").getMethod("facteur", String.class);
            Object v = facteur.invoke(null, cle);
            return v instanceof Double d && d > 0 ? d : 1.0;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            absent = true;
            HeraliszClans.LOG.info("[clans] economie de la beta absente (HeraliZ Loot) : prix de l'arbre et quetes normaux");
            return 1.0;
        } catch (Exception e) {
            return 1.0;
        }
    }

    /** Herals d'une quete de clan avec le facteur « quetes » (arrondi). */
    public static long quete(long argent) {
        if (argent <= 0) return argent;
        double f = facteur("quetes");
        return f == 1.0 ? argent : Math.round(argent * f);
    }
}
