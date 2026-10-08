package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
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
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && tirs > 0) HeralizWonder.LOG.info("[wonder] perf givre {}", resume(true));
        else remettre();
    }

    public static String resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        String r = String.format(Locale.ROOT,
                "%d ticks : %d tirs de givre (%.3f ms en tout, %.3f ms par tir, pire %.3f ms ; %d creatures touchees dont %d gelees d'un coup ; "
                        + "%d lancers de rayon) ; suivi %.3f ms (pire tick %.3f ms, pic %d creatures suivies) ; %d gels, %d eclatements (%.3f ms dont "
                        + "les regles de mort) ; tick serveur moyen %.2f ms",
                ticks, tirs, nsTirs / 1e6, tirs == 0 ? 0 : nsTirs / 1e6 / tirs, maxTir / 1e6, touchees, geleesTir, lancers, nsSuivi / 1e6, maxTick / 1e6,
                picSuivies, gels, eclats, nsEclats / 1e6, mspt);
        if (raz) remettre();
        return r;
    }

    private static void remettre() {
        nsTirs = nsSuivi = nsEclats = maxTir = maxTick = 0;
        tirs = touchees = geleesTir = lancers = gels = eclats = ticks = picSuivies = 0;
    }
}
