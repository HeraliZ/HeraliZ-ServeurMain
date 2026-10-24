package fr.heralisz.utils.diag;

import com.sun.management.GarbageCollectionNotificationInfo;
import fr.heralisz.utils.HeraliszUtils;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pauses du ramasse-miettes (garbage collector) : chaque collecte annoncée par la JVM (avis JMX, sur un fil de la JVM,
 * jamais celui du serveur) est notée avec son heure de fin, sa durée, son nom et sa cause, dans un anneau des 1 024
 * dernières. Une pause « arrête le monde » : pendant une longue pause, tous les fils sont figés, y compris le chien de
 * garde (elle se voit comme un trou dans les échantillons).
 */
final class PausesGc {
    private PausesGc() {
    }

    record Pause(long finMs, long dureeMs, String nom, String cause) {
    }

    private static final int N = 1024;
    private static final Pause[] ANNEAU = new Pause[N];
    private static long total;
    private static boolean branche;

    static synchronized void brancher() {
        if (branche) return;
        branche = true;
        try {
            long debutJvm = ManagementFactory.getRuntimeMXBean().getStartTime();
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (!(gc instanceof NotificationEmitter emetteur)) continue;
                emetteur.addNotificationListener((avis, donnee) -> {
                    try {
                        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(avis.getType())) return;
                        GarbageCollectionNotificationInfo i = GarbageCollectionNotificationInfo.from((CompositeData) avis.getUserData());
                        noter(new Pause(debutJvm + i.getGcInfo().getEndTime(), i.getGcInfo().getDuration(), i.getGcName(), i.getGcCause()));
                    } catch (Throwable ignored) {
                        // jamais d'erreur dans un fil de la JVM
                    }
                }, null, null);
            }
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[diag] pauses du ramasse-miettes non suivies : {}", t.toString());
        }
    }

    private static synchronized void noter(Pause p) {
        ANNEAU[(int) (total % N)] = p;
        total++;
    }

    /** Pauses finies entre {@code debutMs} et {@code finMs} (horloge murale). */
    static synchronized List<Pause> entre(long debutMs, long finMs) {
        List<Pause> l = new ArrayList<>();
        int n = (int) Math.min(total, N);
        for (int j = n - 1; j >= 0; j--) {
            Pause p = ANNEAU[(int) ((total - 1 - j) % N)];
            if (p != null && p.finMs >= debutMs && p.finMs - p.dureeMs <= finMs) l.add(p);
        }
        return l;
    }

    /** Temps total de collecte depuis le démarrage (ms), toutes collectes. */
    static long tempsTotalMs() {
        long t = 0;
        try {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) t += Math.max(0, gc.getCollectionTime());
        } catch (Throwable ignored) {
        }
        return t;
    }

    /** « 12 pauses, 340 ms en tout, pire 120 ms (G1 Young Generation, G1 Evacuation Pause) ». */
    static String resume(List<Pause> l) {
        if (l.isEmpty()) return "aucune pause";
        long somme = 0;
        Pause pire = l.get(0);
        for (Pause p : l) {
            somme += p.dureeMs;
            if (p.dureeMs > pire.dureeMs) pire = p;
        }
        return String.format(Locale.ROOT, "%d pause(s), %d ms en tout, pire %d ms (%s, %s)", l.size(), somme, pire.dureeMs, pire.nom, pire.cause);
    }

    static long pire(List<Pause> l) {
        long m = 0;
        for (Pause p : l) m = Math.max(m, p.dureeMs);
        return m;
    }
}
