package fr.heraliz.wonder.paralyzer;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import net.minecraftforge.event.TickEvent;

import java.util.Locale;

/**
 * Cout serveur du rayon du Paralyzer (nanosecondes passees dans {@link RayonServeur#impulsion}), journalise toutes les
 * 30 s avec le reglage {@code journalPerf} du mod. Aucune allocation hors journal.
 */
public final class PerfParalyseur {
    private PerfParalyseur() {
    }

    private static long ns, maxNs;
    private static int impulsions, touchees, candidates, ticks;

    static void impulsion(long t, int nt, int nc) {
        ns += t;
        impulsions++;
        touchees += nt;
        candidates += nc;
        if (t > maxNs) maxNs = t;
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks < 600) return;
        if (impulsions > 0 && WonderConfig.lire(WonderConfig.JOURNAL_PERF)) HeralizWonder.LOG.info("[wonder] perf paralyzer {}", resume());
        ns = maxNs = 0;
        impulsions = touchees = candidates = ticks = 0;
    }

    public static String resume() {
        return String.format(Locale.ROOT, "%d impulsions (%.3f ms en tout, pire %.3f ms), %d creatures touchees sur %d candidates ; %s", impulsions,
                ns / 1e6, maxNs / 1e6, touchees, candidates, RayonServeur.etat());
    }
}
