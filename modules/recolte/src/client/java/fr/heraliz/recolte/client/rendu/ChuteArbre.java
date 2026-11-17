package fr.heraliz.recolte.client.rendu;

/**
 * La chute d'un arbre abattu, en fonction du temps écoulé depuis {@code Arbre.CHUTE} (ticks) :
 * <ul>
 *    <li>angle du tronc avec la verticale : chute d'une tige qui pivote sur sa base, θ'' = k · sin θ (A_FAIRE §7 :
 *    1,5 g / L), partie de 2°, lente au début puis de plus en plus vite. k est choisi pour que le tronc touche le sol
 *    (84°) à {@link #IMPACT} = 2,4 s, l'heure où le serveur fait la poussière et le bruit de la chute (pour un vrai
 *    tronc de 8 à 11 m, ce serait 2,9 à 3,4 s) ; un petit rebond de 3° (puis un second, plus petit) ;</li>
 *    <li>ensuite, le tronc s'enfonce dans le sol et s'efface jusqu'à {@link #FIN} (2 s plus tard : le serveur donne
 *    les dernières bûches et ne laisse que la souche).</li>
 * </ul>
 * La courbe est calculée une fois (table), aucune allocation à l'image.
 */
public final class ChuteArbre {
    /** Impact au sol et fin de l'effacement (ticks depuis le début de la chute). */
    public static final float IMPACT = 48.0F, FIN = IMPACT + 40.0F;

    private static final float DEPART = (float) Math.toRadians(2.0), SOL = (float) Math.toRadians(84.0);
    private static final float REBOND = (float) Math.toRadians(3.0), REBOND_2 = (float) Math.toRadians(0.8);
    /** Durée des deux rebonds et de la mise en mouvement (ticks). */
    private static final float DUREE_REBOND = 6.0F, DUREE_REBOND_2 = 3.5F, ELAN = 6.0F;
    /** Profondeur atteinte à la fin de l'effacement (m). */
    private static final float PROFONDEUR = 0.9F;
    private static final int POINTS = 64;
    /** θ(u), u = t / IMPACT de 0 à 1 (radians). */
    private static final float[] ANGLES = new float[POINTS + 1];

    static {
        // θ'' = sin θ (k = 1, temps s), de θ = 2° (au repos) jusqu'à 84° : Runge-Kutta, puis la courbe ramenée à u
        double pas = 5.0E-4;
        int max = 40_000;
        double[] th = new double[max];
        double t = DEPART, w = 0.0;
        int n = 0;
        th[n++] = t;
        while (t < SOL && n < max) {
            double k1t = w, k1w = Math.sin(t);
            double k2t = w + pas / 2 * k1w, k2w = Math.sin(t + pas / 2 * k1t);
            double k3t = w + pas / 2 * k2w, k3w = Math.sin(t + pas / 2 * k2t);
            double k4t = w + pas * k3w, k4w = Math.sin(t + pas * k3t);
            t += pas / 6 * (k1t + 2 * k2t + 2 * k3t + k4t);
            w += pas / 6 * (k1w + 2 * k2w + 2 * k3w + k4w);
            th[n++] = t;
        }
        int dernier = n - 1;
        for (int i = 0; i <= POINTS; i++) {
            double x = (double) dernier * i / POINTS;
            int a = Math.min(dernier, (int) x);
            int b = Math.min(dernier, a + 1);
            double f = x - a;
            ANGLES[i] = (float) Math.min(SOL, th[a] + (th[b] - th[a]) * f);
        }
        ANGLES[POINTS] = SOL;
    }

    private ChuteArbre() {
    }

    /** Angle du tronc avec la verticale (radians) ; t en ticks depuis le début de la chute. */
    public static float angle(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t < IMPACT) {
            float u = t / IMPACT * POINTS;
            int i = Math.min(POINTS - 1, (int) u);
            float th = ANGLES[i] + (ANGLES[i + 1] - ANGLES[i]) * (u - i);
            if (t < ELAN) {
                // le tronc quitte la verticale en douceur (la loi part de 2°)
                float r = t / ELAN;
                th -= DEPART * (1.0F - r * r * (3.0F - 2.0F * r));
            }
            return Math.max(0.0F, th);
        }
        float tau = t - IMPACT;
        if (tau < DUREE_REBOND) {
            float r = tau / DUREE_REBOND;
            return SOL - REBOND * 4.0F * r * (1.0F - r);
        }
        tau -= DUREE_REBOND;
        if (tau < DUREE_REBOND_2) {
            float r = tau / DUREE_REBOND_2;
            return SOL - REBOND_2 * 4.0F * r * (1.0F - r);
        }
        return SOL;
    }

    /** Avancée de l'effacement, 0 à l'impact, 1 à la fin. */
    private static float effacement(float t) {
        return t <= IMPACT ? 0.0F : Math.min(1.0F, (t - IMPACT) / (FIN - IMPACT));
    }

    /** Profondeur dans le sol (m) : le tronc tombé s'enfonce, lentement d'abord. */
    public static float enfoncement(float t) {
        float u = effacement(t);
        return PROFONDEUR * u * u;
    }

    /** Opacité du tronc tombé : 1 jusqu'à l'impact, puis il s'efface. */
    public static float opacite(float t) {
        float u = effacement(t);
        return 1.0F - u * u;
    }
}
