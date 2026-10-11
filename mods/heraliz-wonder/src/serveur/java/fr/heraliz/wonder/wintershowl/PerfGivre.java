package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.serveur.Perf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * Cout serveur du Winter's Howl (nanosecondes dans notre code) : tirs, suivi des creatures (fonte, file, eclatements),
 * eclatements. Journalise toutes les 30 s si {@code journalPerf} est actif, ou a la demande ({@code /wonder givre perf}).
 * Aucune allocation hors journal.
 */
public final class PerfGivre {
    private PerfGivre() {
    }

    private static long nsTirs, nsSuivi, nsEclats, maxTir, maxTick;
    private static int tirs, touchees, geleesTir, lancers, gels, eclats, ticks, picSuivies;

    static void tir(long ns, int t, int g, int l) {
        nsTirs += ns;
        tirs++;
        touchees += t;
        geleesTir += g;
        lancers += l;
        if (ns > maxTir) maxTir = ns;
    }

    static void suivi(long ns, int suivies) {
        nsSuivi += ns;
        if (ns > maxTick) maxTick = ns;
        if (suivies > picSuivies) picSuivies = suivies;
    }

    static void gel() {
        gels++;
    }

    static void eclat(long ns) {
        eclats++;
        nsEclats += ns;
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++ticks < 600) return;
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && tirs > 0) HeralizWonder.LOG.info("[wonder] perf givre {}", Perf.journal(resume(true)));
        else remettre();
    }

    /** Resume depuis le dernier releve : message traduit du staff ; {@link Perf#journal} pour le journal. */
    public static MutableComponent resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        MutableComponent r = Component.translatableWithFallback("heraliz_wonder.perf.givre",
                "%s ticks : %s tirs de givre (%s ms en tout, %s ms par tir, pire %s ms ; %s créatures touchées dont %s gelées d'un coup ; "
                        + "%s lancers de rayon) ; suivi %s ms (pire tick %s ms, pic %s créatures suivies) ; %s gels, %s éclatements (%s ms dont "
                        + "les règles de mort) ; tick serveur moyen %s ms",
                Perf.n(ticks), Perf.n(tirs), Perf.ms(nsTirs / 1e6), Perf.ms(tirs == 0 ? 0 : nsTirs / 1e6 / tirs), Perf.ms(maxTir / 1e6), Perf.n(touchees),
                Perf.n(geleesTir), Perf.n(lancers), Perf.ms(nsSuivi / 1e6), Perf.ms(maxTick / 1e6), Perf.n(picSuivies), Perf.n(gels), Perf.n(eclats),
                Perf.ms(nsEclats / 1e6), String.format(Locale.ROOT, "%.2f", mspt));
        if (raz) remettre();
        return r;
    }

    private static void remettre() {
        nsTirs = nsSuivi = nsEclats = maxTir = maxTick = 0;
        tirs = touchees = geleesTir = lancers = gels = eclats = ticks = picSuivies = 0;
    }
}
