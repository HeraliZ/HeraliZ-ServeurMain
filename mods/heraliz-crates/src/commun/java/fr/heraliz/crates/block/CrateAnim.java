package fr.heraliz.crates.block;

/**
 * Chronologie (en ticks, 20 ticks = 1 seconde) de la sequence d'ouverture.
 * Partagee par le serveur (remise de la recompense) et le client (animations, sons).
 */
public final class CrateAnim {
    /** Durée du défilement de la roue de l'interface d'ouverture (ticks) ; reprise par opening/CaseOpening (serveur). */
    public static final int REEL_TICKS = 130;
    /** Quantité maximale d'une ligne de l'éditeur de butin ; reprise par opening/LootEditing (serveur). */
    public static final int EDITEUR_MAX_COUNT = 6400;

    private CrateAnim() {
    }

    /** La cle apparait et vole vers le cadenas. */
    public static final int KEY_APPEAR = 0;
    /** La cle s'enfonce dans le cadenas. */
    public static final int KEY_IN = 12;
    /** La cle tourne. */
    public static final int KEY_TURN = 17;
    /** Declic : l'anse du cadenas saute, le cadenas tombe ; la creature/le coffre s'eveille. */
    public static final int UNLOCK = 24;
    /** Le couvercle s'ouvre (choregraphie propre a chaque caisse). */
    public static final int OPEN = 34;
    public static final int OPEN_END = 56;
    /** Les objets sortent de la caisse et forment la roue (l'ouverture de chaque caisse a le temps de se jouer). */
    public static final int RISE = 64;
    /** La roue tourne... */
    public static final int SPIN = 78;
    /** ... ralentit longuement et se pose sur l'objet gagnant (6 s de rotation). */
    public static final int STOP = 196;
    /** L'objet gagne se place au centre. */
    public static final int REVEAL = 200;
    /** L'objet vole vers le joueur. */
    public static final int FLY = 238;
    /** Le serveur donne la recompense. */
    public static final int REWARD = 248;
    /** Le couvercle se referme. */
    public static final int CLOSE = 252;
    public static final int SLAM = 268;
    /** Fin de la sequence. */
    public static final int END = 280;

    /** Nombre de tours complets de la roue. */
    public static final int SPIN_TURNS = 5;

    /**
     * Angle (degres) de la roue a l'instant t (ticks depuis le debut de la sequence).
     * Facon ouverture de caisse CS:GO : la roue s'elance en quelques ticks, puis ralentit longuement (vitesse en
     * (1 - x)^3) ; sur la fin elle n'avance presque plus mais continue de glisser jusqu'a se poser sur l'objet gagnant.
     */
    public static float ringAngle(float t, int ringSize, int winIndex) {
        float slot = 360f / ringSize;
        float target = SPIN_TURNS * 360f + mod(-winIndex * slot, 360f);
        if (t <= SPIN) return 0f;
        if (t >= STOP) return target;
        float s = (t - SPIN) / (float) (STOP - SPIN);
        return (float) (target * spinProfile(s) / spinProfile(1));
    }

    /** Vitesse relative de la roue (0..1, 1 = vitesse maximale) a l'avancement s (0..1) de la rotation. */
    public static float spinVelocity(float s) {
        if (s <= 0 || s >= 1) return 0;
        double v = (1 - Math.exp(-s / RAMP)) * Math.pow(1 - s, 3);
        return (float) (v / VMAX);
    }

    /** Montee en vitesse (fraction de la rotation). */
    private static final double RAMP = 0.03;
    /** Maximum de (1 - e^(-s/RAMP)) (1 - s)^3 (normalisation de spinVelocity). */
    private static final double VMAX;

    static {
        double m = 0;
        for (int i = 1; i < 2000; i++) {
            double x = i / 2000.0;
            m = Math.max(m, (1 - Math.exp(-x / RAMP)) * Math.pow(1 - x, 3));
        }
        VMAX = m;
    }

    /** Integrale exacte de v(x) = (1 - e^(-x/c)) (1 - x)^3 entre 0 et s. */
    private static double spinProfile(double s) {
        double c = RAMP;
        double poly = (1 - Math.pow(1 - s, 4)) / 4;
        // (1 - x)^3 = 1 - 3x + 3x^2 - x^3 ; integrale de x^n e^(-x/c) = n! c^(n+1) (1 - e^(-s/c) somme_k<=n (s/c)^k / k!)
        double[] coef = {1, -3, 3, -1};
        double u = s / c, e = Math.exp(-u), exp = 0, fact = 1;
        for (int n = 0; n <= 3; n++) {
            if (n > 0) fact *= n;
            double sum = 0, term = 1;
            for (int k = 0; k <= n; k++) {
                if (k > 0) term *= u / k;
                sum += term;
            }
            exp += coef[n] * fact * Math.pow(c, n + 1) * (1 - e * sum);
        }
        return poly - exp;
    }

    public static float mod(float a, float m) {
        float r = a % m;
        return r < 0 ? r + m : r;
    }
}
