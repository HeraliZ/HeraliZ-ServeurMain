package fr.heraliz.ping.client.hud;

import java.lang.management.ManagementFactory;

/**
 * Mesure du cout du HUD (banc d'essai seulement : coupee en jeu) : temps passe et octets alloues par le fil de rendu
 * dans nos dessins, image par image.
 */
public final class Mesure {
    private Mesure() {
    }

    public static boolean active;
    public static long images, nanos, octets, quads;
    private static com.sun.management.ThreadMXBean mx;
    private static long t0, a0;

    public static void debut() {
        if (mx == null) mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        a0 = mx.getCurrentThreadAllocatedBytes();
        t0 = System.nanoTime();
    }

    public static void fin() {
        long t = System.nanoTime() - t0;
        long a = mx.getCurrentThreadAllocatedBytes() - a0;
        nanos += t;
        octets += a;
        quads += Lot.quads;
        images++;
    }

    public static void zero() {
        images = nanos = octets = quads = 0;
    }

    /** Cout de la mesure elle-meme (octets alloues par une paire debut/fin vide), a retrancher. */
    public static long octetsAVide() {
        if (mx == null) mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long a = mx.getCurrentThreadAllocatedBytes();
        long b = mx.getCurrentThreadAllocatedBytes();
        return b - a;
    }
}
