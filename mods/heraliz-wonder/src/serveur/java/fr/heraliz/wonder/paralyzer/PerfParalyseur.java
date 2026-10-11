package fr.heraliz.wonder.paralyzer;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.serveur.Perf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.TickEvent;

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
        if (impulsions > 0 && WonderConfig.lire(WonderConfig.JOURNAL_PERF)) HeralizWonder.LOG.info("[wonder] perf paralyzer {}", Perf.journal(resume()));
        ns = maxNs = 0;
        impulsions = touchees = candidates = ticks = 0;
    }

    /** Resume depuis le dernier releve : message traduit du staff ; {@link Perf#journal} pour le journal. */
    public static MutableComponent resume() {
        return Component.translatableWithFallback("heraliz_wonder.perf.paralyzer",
                "%s impulsions (%s ms en tout, pire %s ms), %s créatures touchées sur %s candidates ; %s", Perf.n(impulsions),
                Perf.ms(ns / 1e6), Perf.ms(maxNs / 1e6), Perf.n(touchees), Perf.n(candidates), RayonServeur.etat());
    }
}
