package com.Spartan.scan.collect;

import com.Spartan.scan.ScanReport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Classes modifiées EN MÉMOIRE après leur chargement (19/11).
 *
 * <p>Un client de triche injecté en cours de partie (DLL qui charge un agent Java, ou qui parle directement à Java par
 * JVMTI, sans l'attache coupée par le launcher) trouve les classes du jeu déjà chargées : pour détourner le rendu, le
 * mouvement, le réseau, ou neutraliser l'anticheat sans toucher à son jar, il doit les REDÉFINIR (RedefineClasses /
 * RetransformClasses). Mixin et Forge, eux, modifient les classes AVANT leur chargement : dans un jeu lancé par le
 * launcher (aucun agent ni débogueur permis), aucune classe n'est jamais redéfinie.
 *
 * <p>Java compte lui-même chaque redéfinition dans la classe (champ caché {@code Class.classRedefinedCount}, qui lui sert
 * à vider ses caches de réflexion). On le lit sans rien modifier : une lecture d'entier par {@code sun.misc.Unsafe},
 * trouvé par réflexion (aucune dépendance à la compilation). Différent de 0 : classe modifiée en mémoire. Illisible
 * (autre Java que HotSpot) : rien n'est dit. Essayé le 19/11 sur Java 21 : 0 sans agent ; 1 et 2 après une redéfinition
 * et deux retransformations par un agent.
 */
public final class Redefinitions {
    /** Au plus autant de classes redéfinies dans un rapport (le reste ne dit rien de plus). */
    static final int MAX = 8;
    private static final Object UNSAFE;
    private static final Method GET_INT;
    private static final long DECALAGE;

    static {
        Object u = null;
        Method g = null;
        long d = -1;
        try {
            Class<?> k = Class.forName("sun.misc.Unsafe");
            Field f = k.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = f.get(null);
            Field champ = Class.class.getDeclaredField("classRedefinedCount");
            if (champ.getType() == int.class && !Modifier.isStatic(champ.getModifiers())) {
                g = k.getMethod("getInt", Object.class, long.class);
                d = (Long) k.getMethod("objectFieldOffset", Field.class).invoke(u, champ);
            }
        } catch (Throwable t) {
            g = null;
            d = -1;
        }
        UNSAFE = u;
        GET_INT = g;
        DECALAGE = d;
    }

    private Redefinitions() {
    }

    /** La lecture marche-t-elle sur ce Java ? */
    public static boolean disponible() {
        return GET_INT != null && DECALAGE >= 0;
    }

    /** Nombre de redéfinitions de la classe depuis son chargement ; -1 si illisible. */
    public static int compte(Class<?> c) {
        if (c == null || !disponible()) {
            return -1;
        }
        try {
            return (Integer) GET_INT.invoke(UNSAFE, c, DECALAGE);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Ajoute au rapport « redef|classe|fois » pour chaque classe redéfinie (au plus {@link #MAX}). Une classe introuvable
     * (propre à l'autre côté, mod absent) est ignorée.
     *
     * @return les classes trouvées (surveillées ensuite par la veille du jeu)
     */
    public static List<Class<?>> collect(ScanReport r, List<String> classes, ClassLoader loader) {
        List<Class<?>> vues = new ArrayList<>();
        int ajoutees = 0;
        for (String nom : classes) {
            Class<?> c;
            try {
                c = Class.forName(nom, false, loader);
            } catch (Throwable t) {
                continue;
            }
            vues.add(c);
            int n = compte(c);
            if (n > 0 && ajoutees < MAX && r.injection.size() < ScanReport.MAX_INJECTION) {
                r.injection.add(entree(nom, n));
                ajoutees++;
            }
        }
        return vues;
    }

    /** Entrée du rapport pour une classe redéfinie {@code fois} fois (même format que les autres traces d'injection). */
    static String entree(String classe, int fois) {
        return "redef|" + ScanReport.cut(classe, 200) + "|" + fois;
    }
}
