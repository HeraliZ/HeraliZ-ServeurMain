package fr.heraliz.wonder.sliquifier.serveur;

import fr.heraliz.wonder.serveur.Perf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Compteurs du cout serveur du Sliquifier (nanosecondes passees dans notre code), lus par {@code /sliq perf}. Aucune
 * allocation hors du resume.
 */
public final class SliqPerf {
    private SliqPerf() {
    }

    private static long nsTirs, nsBoules, nsFlaques, nsGlissades, maxTick;
    private static int tirs, flaques, glissades, morts, ticks, ticksActifs, recherches, candidats, picB, picF, picG;

    static void tir(long ns) {
        nsTirs += ns;
        tirs++;
    }

    static void flaque() {
        flaques++;
    }

    static void glissade() {
        glissades++;
    }

    static void mort() {
        morts++;
    }

    static void recherche(int n) {
        recherches++;
        candidats += n;
    }

    static void tick(long boules, long fl, long gl, int nB, int nF, int nG) {
        nsBoules += boules;
        nsFlaques += fl;
        nsGlissades += gl;
        long t = boules + fl + gl;
        if (t > maxTick) maxTick = t;
        ticksActifs++;
        if (nB > picB) picB = nB;
        if (nF > picF) picF = nF;
        if (nG > picG) picG = nG;
    }

    public static void compterTick() {
        ticks++;
    }

    /** Resume depuis le dernier releve : message traduit du staff ; {@link Perf#journal} pour le journal. */
    public static MutableComponent resume(boolean raz) {
        double tot = (nsBoules + nsFlaques + nsGlissades + nsTirs) / 1e6;
        MutableComponent r = Component.translatableWithFallback("heraliz_wonder.perf.sliquifier",
                "%s tirs, %s flaques posées, %s glissades, %s morts ; %s ticks actifs : boules %s ms, flaques %s ms (%s recherches, %s candidats), "
                        + "glissades %s ms, tirs %s ms ; total %s ms, %s ms par tick actif, pire tick %s ms ; pics : %s boules, %s flaques, %s glissades",
                Perf.n(tirs), Perf.n(flaques), Perf.n(glissades), Perf.n(morts), Perf.n(ticksActifs), Perf.ms(nsBoules / 1e6), Perf.ms(nsFlaques / 1e6),
                Perf.n(recherches), Perf.n(candidats), Perf.ms(nsGlissades / 1e6), Perf.ms(nsTirs / 1e6), Perf.ms(tot),
                String.format(Locale.ROOT, "%.4f", ticksActifs == 0 ? 0 : tot / ticksActifs), Perf.ms(maxTick / 1e6), Perf.n(picB), Perf.n(picF), Perf.n(picG));
        if (raz) {
            nsTirs = nsBoules = nsFlaques = nsGlissades = maxTick = 0;
            tirs = flaques = glissades = morts = ticks = ticksActifs = recherches = candidats = picB = picF = picG = 0;
        }
        return r;
    }
}
