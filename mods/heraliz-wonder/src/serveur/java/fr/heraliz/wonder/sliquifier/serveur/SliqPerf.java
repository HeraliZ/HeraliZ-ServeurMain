package fr.heraliz.wonder.sliquifier.serveur;

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

    public static String resume(boolean raz) {
        double tot = (nsBoules + nsFlaques + nsGlissades + nsTirs) / 1e6;
        String r = String.format(Locale.ROOT,
                "%d tirs, %d flaques posees, %d glissades, %d morts ; %d ticks actifs : boules %.3f ms, flaques %.3f ms (%d recherches, %d candidats), "
                        + "glissades %.3f ms, tirs %.3f ms ; total %.3f ms, %.4f ms par tick actif, pire tick %.3f ms ; pics : %d boules, %d flaques, %d glissades",
                tirs, flaques, glissades, morts, ticksActifs, nsBoules / 1e6, nsFlaques / 1e6, recherches, candidats, nsGlissades / 1e6, nsTirs / 1e6, tot,
                ticksActifs == 0 ? 0 : tot / ticksActifs, maxTick / 1e6, picB, picF, picG);
        if (raz) {
            nsTirs = nsBoules = nsFlaques = nsGlissades = maxTick = 0;
            tirs = flaques = glissades = morts = ticks = ticksActifs = recherches = candidats = picB = picF = picG = 0;
        }
        return r;
    }
}
