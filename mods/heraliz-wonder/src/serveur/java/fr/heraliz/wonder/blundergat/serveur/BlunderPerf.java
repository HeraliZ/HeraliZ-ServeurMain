package fr.heraliz.wonder.blundergat.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Locale;

/**
 * Coût serveur du Blundergat et de l'Acid Gat (nanosecondes passées dans notre code) : tirs d'acide, vol et suivi des
 * cartouches, explosions, regroupement des impacts d'obus. Journal toutes les 30 s si {@code general.journalPerf}, ou
 * {@code /blundergat perf}. Aucune allocation hors journal.
 */
public final class BlunderPerf {
    private BlunderPerf() {
    }

    private static long nsTir, nsCart, nsExpl, nsEclats, maxNs;
    private static int tirs, cartouches, explosions, touchees, lancers, impacts, paquets, picCartouches, ticks;

    static void tir(long ns, int nb) {
        nsTir += ns;
        tirs++;
        cartouches += nb;
        if (ns > maxNs) maxNs = ns;
    }

    static void cartouches(long ns, int enCours) {
        nsCart += ns;
        if (enCours > picCartouches) picCartouches = enCours;
        if (ns > maxNs) maxNs = ns;
    }

    static void explosion(long ns, int t, int l) {
        nsExpl += ns;
        explosions++;
        touchees += t;
        lancers += l;
        if (ns > maxNs) maxNs = ns;
    }

    static void eclats(long ns, int i, int p) {
        nsEclats += ns;
        impacts += i;
        paquets += p;
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++ticks < 600) return;
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && (tirs + impacts) > 0) HeralizWonder.LOG.info("[wonder] perf blundergat {}", resume(true));
        else remettre();
    }

    public static String resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        double total = (nsTir + nsCart + nsExpl + nsEclats) / 1e6;
        String r = String.format(Locale.ROOT,
                "%d ticks : %d tirs d'acide (%d cartouches, pic %d en meme temps), vol+suivi %.3f ms, %d explosions (%.3f ms, %d creatures, "
                        + "%d lancers de rayon) ; %d impacts d'obus en %d paquets (%.3f ms) ; total %.3f ms (%.4f ms/tick) ; pire %.3f ms ; "
                        + "tick serveur moyen %.2f ms",
                ticks, tirs, cartouches, picCartouches, nsCart / 1e6, explosions, nsExpl / 1e6, touchees, lancers, impacts, paquets, nsEclats / 1e6,
                total, ticks == 0 ? 0 : total / ticks, maxNs / 1e6, mspt);
        if (raz) remettre();
        return r;
    }

    private static void remettre() {
        nsTir = nsCart = nsExpl = nsEclats = maxNs = 0;
        tirs = cartouches = explosions = touchees = lancers = impacts = paquets = picCartouches = ticks = 0;
    }
}
