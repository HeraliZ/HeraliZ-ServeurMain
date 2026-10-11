package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.WonderConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Optional;

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
        if (WonderConfig.lire(WonderConfig.JOURNAL_PERF) && (nEcl + nFoudre + nBalles + nSouffle) > 0) HeralizWonder.LOG.info("[wonder] perf {}", journal(resume(true)));
        else remettre();
    }

    /** Resume depuis le dernier releve (et remise a zero si demande) : message traduit du staff ; {@link #journal} pour le journal. */
    public static MutableComponent resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        MutableComponent r = Component.translatableWithFallback("heraliz_wonder.perf.armes",
                "%s ticks : %s éclaboussures (%s ms en tout, %s créatures touchées), %s foudres (%s ms, %s créatures), "
                        + "%s ondes de choc (%s ms, %s créatures soufflées), %s lancers de rayon ; %s balles suivies (%s ms) ; "
                        + "pire tir %s ms ; tick serveur moyen %s ms",
                n(ticks), n(nEcl), ms(nsEcl / 1e6), n(touchees), n(nFoudre), ms(nsFoudre / 1e6), n(foudroyees), n(nSouffle), ms(nsSouffle / 1e6),
                n(soufflees), n(lancers), n(nBalles), ms(nsBalles / 1e6), ms(maxNs / 1e6), String.format(Locale.ROOT, "%.2f", mspt));
        if (raz) remettre();
        return r;
    }

    /** Nombre entier en texte (argument d'un message traduit : seuls les %s y sont permis). */
    public static String n(long v) {
        return Long.toString(v);
    }

    /** Duree en millisecondes, 3 decimales (argument d'un message traduit). */
    public static String ms(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    /**
     * Texte francais d'un message traduisible, pour le journal du serveur (le meme quelle que soit la langue) : texte de
     * secours de la cle, arguments compris (eux aussi en francais s'ils sont traduisibles).
     */
    public static String journal(Component c) {
        StringBuilder b = new StringBuilder();
        if (c.getContents() instanceof TranslatableContents t && t.getFallback() != null) {
            Object[] a = t.getArgs().clone();
            for (int i = 0; i < a.length; i++) if (a[i] instanceof Component k) a[i] = journal(k);
            try {
                b.append(String.format(Locale.ROOT, t.getFallback(), a));
            } catch (IllegalFormatException ex) {
                b.append(t.getFallback());
            }
        } else {
            c.getContents().visit(morceau -> {
                b.append(morceau);
                return Optional.empty();
            });
        }
        for (Component suite : c.getSiblings()) b.append(journal(suite));
        return b.toString();
    }

    private static void remettre() {
        nsEcl = nsFoudre = nsBalles = nsSouffle = maxNs = 0;
        nEcl = nFoudre = nBalles = touchees = foudroyees = lancers = nSouffle = soufflees = 0;
        ticks = 0;
    }
}
