package fr.heralisz.utils.diag;

import net.minecraft.world.entity.Entity;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Lecture de la pile d'un fil depuis un AUTRE fil (chien de garde, profil). Attention : lire la pile d'un fil arrête la
 * JVM un court instant (point de sûreté, quelques dizaines de microsecondes) : seulement pendant un tick déjà long ou
 * pendant un profil demandé, jamais quand tout va bien.
 */
final class Echantillon {
    private Echantillon() {
    }

    static final int PROFONDEUR_MAX = 512;
    private static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();

    /** Pile, état et verrou du fil, ou null (fil mort, erreur). */
    @SuppressWarnings("deprecation")
    static ThreadInfo lire(Thread fil) {
        if (fil == null) return null;
        try {
            return MX.getThreadInfo(fil.getId(), PROFONDEUR_MAX);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Entité dont le tick est en cours : « type #id à x y z (monde) » ou, si {@code typeSeul}, son type ; null si aucune. */
    static String entite(boolean typeSeul) {
        Entity e = SuiviEntites.courante();
        if (e == null) return null;
        try {
            String type = Instantane.nom(e.getType());
            if (typeSeul) return type;
            return String.format(Locale.ROOT, "%s #%d à %d %d %d (%s)", type, e.getId(), (int) Math.floor(e.getX()),
                    (int) Math.floor(e.getY()), (int) Math.floor(e.getZ()), e.level().dimension().location());
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Piles de tous les fils (une fois, pendant un blocage) : nom, état, verrou, 40 méthodes au plus par fil. */
    static String tousLesFils() {
        StringBuilder b = new StringBuilder();
        try {
            ThreadInfo[] tous = MX.dumpAllThreads(false, false);
            Arrays.sort(tous, Comparator.comparing(t -> t == null ? "" : t.getThreadName()));
            for (ThreadInfo t : tous) {
                if (t == null) continue;
                StackTraceElement[] st = t.getStackTrace();
                b.append("\"").append(t.getThreadName()).append("\" ").append(t.getThreadState());
                if (t.getLockName() != null) b.append(" sur ").append(t.getLockName());
                if (t.getLockOwnerName() != null) b.append(" tenu par \"").append(t.getLockOwnerName()).append('"');
                b.append('\n');
                int max = Math.min(st.length, 40);
                for (int i = 0; i < max; i++) b.append("    ").append(Agregat.texte(st[i])).append('\n');
                if (st.length > max) b.append("    … (").append(st.length - max).append(" de plus)\n");
            }
        } catch (Throwable t) {
            b.append("(piles des fils illisibles : ").append(t).append(")\n");
        }
        return b.toString();
    }
}
