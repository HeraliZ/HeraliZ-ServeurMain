package fr.heraliz.wonder.blundergat.serveur;

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
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && (tirs + impacts) > 0) HeralizWonder.LOG.info("[wonder] perf blundergat {}", Perf.journal(resume(true)));
        else remettre();
    }

    /** Resume depuis le dernier releve : message traduit du staff ; {@link Perf#journal} pour le journal. */
    public static MutableComponent resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        double total = (nsTir + nsCart + nsExpl + nsEclats) / 1e6;
        MutableComponent r = Component.translatableWithFallback("heraliz_wonder.perf.blundergat",
                "%s ticks : %s tirs d'acide (%s cartouches, pic %s en même temps), vol+suivi %s ms, %s explosions (%s ms, %s créatures, "
                        + "%s lancers de rayon) ; %s impacts d'obus en %s paquets (%s ms) ; total %s ms (%s ms/tick) ; pire %s ms ; "
                        + "tick serveur moyen %s ms",
                Perf.n(ticks), Perf.n(tirs), Perf.n(cartouches), Perf.n(picCartouches), Perf.ms(nsCart / 1e6), Perf.n(explosions), Perf.ms(nsExpl / 1e6),
                Perf.n(touchees), Perf.n(lancers), Perf.n(impacts), Perf.n(paquets), Perf.ms(nsEclats / 1e6), Perf.ms(total),
                String.format(Locale.ROOT, "%.4f", ticks == 0 ? 0 : total / ticks), Perf.ms(maxNs / 1e6), String.format(Locale.ROOT, "%.2f", mspt));
        if (raz) remettre();
        return r;
    }

    private static void remettre() {
        nsTir = nsCart = nsExpl = nsEclats = maxNs = 0;
        tirs = cartouches = explosions = touchees = lancers = impacts = paquets = picCartouches = ticks = 0;
    }
}
