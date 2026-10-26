package fr.heraliz.casino.client.render;

/**
 * Mesure du cout des machines cote client (tests de performance) : temps passe a dessiner les machines et les decors,
 * et a calculer leurs effets (particules, sons). Inactif par defaut : un simple test de booleen par appel.
 */
public final class RenderProfile {
    private RenderProfile() {
    }

    /** Active la mesure (scenario de test "perf", ou -Dheralizcasino.profile=true). */
    public static boolean on = Boolean.getBoolean("heralizcasino.profile");
    public static long renderNanos, renders, animated, fxNanos, fxCalls;
    /** Detail par etape : carrosserie, animation, opaque, translucide, lumineux, sprites, textes, nom flottant. */
    public static final String[] SECTIONS = {"carrosserie", "animation", "opaque", "translucide", "lumineux", "sprites", "textes", "nom", "envoi groupe"};
    public static final long[] section = new long[SECTIONS.length];
    private static long mark;

    public static void reset() {
        renderNanos = renders = animated = fxNanos = fxCalls = 0;
        java.util.Arrays.fill(section, 0);
    }

    /** Debut d'une etape mesuree. */
    public static void start() {
        if (on) mark = System.nanoTime();
    }

    /** Fin de l'etape k (et debut de la suivante). */
    public static void lap(int k) {
        if (!on) return;
        long now = System.nanoTime();
        section[k] += now - mark;
        mark = now;
    }

    public static String detail(int frames) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < SECTIONS.length; k++) {
            b.append(k == 0 ? "" : ", ").append(SECTIONS[k]).append(' ').append(String.format(java.util.Locale.ROOT, "%.2f", section[k] / 1e6 / Math.max(1, frames)));
        }
        return b.toString();
    }
}
