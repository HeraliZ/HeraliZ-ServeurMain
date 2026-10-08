package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * Compteurs du cout serveur des tirs (nanosecondes passees dans notre code, par type), journalises toutes les 30 s si
 * {@code journalPerf} est actif, ou a la demande ({@code /wonder perf}). Aucune allocation hors journal.
 */
public final class Perf {
    private Perf() {
    }

    private static long nsEcl, nsFoudre, nsBalles, nsSouffle;
    private static int nEcl, nFoudre, nBalles, touchees, foudroyees, lancers, nSouffle, soufflees;
    private static long maxNs;
    private static int ticks;

    static void eclaboussure(long ns, int t) {
        nsEcl += ns;
        nEcl++;
        touchees += t;
        if (ns > maxNs) maxNs = ns;
    }

    static void foudre(long ns, int cibles, int l) {
        nsFoudre += ns;
        nFoudre++;
        foudroyees += cibles;
        lancers += l;
        if (ns > maxNs) maxNs = ns;
    }

    static void souffle(long ns, int cibles, int l) {
        nsSouffle += ns;
        nSouffle++;
        soufflees += cibles;
        lancers += l;
        if (ns > maxNs) maxNs = ns;
    }

    /** Coups differes du souffle (arrivee du front d'onde), comptes avec les ondes de choc. */
    static void souffleDiffere(long ns) {
        nsSouffle += ns;
        if (ns > maxNs) maxNs = ns;
    }

    static void balle(long ns) {
        nsBalles += ns;
        nBalles++;
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++ticks < 600) return;
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && (nEcl + nFoudre + nBalles + nSouffle) > 0) HeralizWonder.LOG.info("[wonder] perf {}", resume(true));
        else remettre();
    }

    /** Resume depuis le dernier releve (et remise a zero si demande). */
    public static String resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        String r = String.format(Locale.ROOT,
                "%d ticks : %d eclaboussures (%.3f ms en tout, %d creatures touchees), %d foudres (%.3f ms, %d creatures), "
                        + "%d ondes de choc (%.3f ms, %d creatures soufflees), %d lancers de rayon ; %d balles suivies (%.3f ms) ; "
                        + "pire tir %.3f ms ; tick serveur moyen %.2f ms",
                ticks, nEcl, nsEcl / 1e6, touchees, nFoudre, nsFoudre / 1e6, foudroyees, nSouffle, nsSouffle / 1e6, soufflees, lancers, nBalles,
                nsBalles / 1e6, maxNs / 1e6, mspt);
        if (raz) remettre();
        return r;
    }

    private static void remettre() {
        nsEcl = nsFoudre = nsBalles = nsSouffle = maxNs = 0;
        nEcl = nFoudre = nBalles = touchees = foudroyees = lancers = nSouffle = soufflees = 0;
        ticks = 0;
    }
}
