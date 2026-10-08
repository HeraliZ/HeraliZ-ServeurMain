package fr.heraliz.wonder.wintershowl;

/**
 * Calculs purs du Winter's Howl (aucune classe de Minecraft : testables hors du jeu, {@code tools/wonder/tests_wintershowl.py}
 * les execute dans une JVM) : cone et ses variantes Pack-a-Punch, gel apporte selon la distance, ralentissement, fonte,
 * arrivee du front de givre, delai et degats de l'eclatement. Le test de cone lui-meme est celui du Thundergun
 * ({@link fr.heraliz.wonder.Calculs#dansCone}).
 */
public final class CalculsGivre {
    private CalculsGivre() {
    }

    /** Longueur du cone : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +1,5 par niveau au-dessus. */
    public static double portee(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 1.5 * (pap - 1);
    }

    /** Demi-angle du cone (degres) : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +2 par niveau au-dessus. */
    public static double angle(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 2.0 * (pap - 1);
    }

    /**
     * Gel apporte par un tir a la distance {@code s} le long de l'axe : 1 (gel immediat) jusqu'a la fraction {@code zone} de
     * la portee, puis decroit lineairement jusqu'a {@code gainBout} au bout du cone.
     */
    public static float gain(double s, double portee, double zone, double gainBout) {
        double u = Math.max(0, Math.min(1, s / Math.max(portee, 1e-6)));
        if (u <= zone) return 1f;
        double k = (u - zone) / Math.max(1 - zone, 1e-6);
        return (float) (1 + (gainBout - 1) * k);
    }

    /** Niveau de ralentissement 0..9 (dixiemes de vitesse en moins) pour un gel 0..1 et un ralentissement maximum. */
    public static int niveauRalenti(float gel, double ralentiMax) {
        double r = Math.max(0, Math.min(1, gel)) * Math.max(0, Math.min(0.95, ralentiMax));
        return (int) Math.max(0, Math.min(9, Math.round(r * 10)));
    }

    /**
     * Front de givre : les bouffees de tete du jet de brume partent a {@link #TETE_V} x la portee par tick et perdent
     * {@code 1 - FROTTEMENT} de leur vitesse a chaque tick (le client les dessine ainsi) ; elles atteignent le bout du cone
     * en ~6 ticks. Le gel est applique quand elles arrivent.
     */
    public static final double TETE_V = 0.25, FROTTEMENT = 0.82;
    /** Duree (ticks) au plus du trajet du front jusqu'au bout du cone. */
    public static final float DUREE_FRONT = 7f;

    /** Tick (apres le tir) ou le front de givre atteint la distance {@code s} : 0 a la bouche, ~6 au bout du cone. */
    public static int arrivee(double s, double portee) {
        double u = Math.max(0, Math.min(1, s / Math.max(portee, 1e-6)));
        double r = 1 - u / (TETE_V / (1 - FROTTEMENT));
        return (int) Math.max(0, Math.min(DUREE_FRONT, Math.round(Math.log(Math.max(r, 1e-6)) / Math.log(FROTTEMENT))));
    }

    /** Delai (ticks) avant l'eclatement spontane d'une creature gelee. */
    public static int delaiEclat(int pap, double baseS, double papS) {
        return (int) Math.max(4, Math.round((pap <= 0 ? baseS : papS) * 20));
    }

    /** Une creature de {@code pvMax} PV max peut-elle geler ? (les boss ne gelent jamais) */
    public static boolean peutGeler(float pvMax, double seuilPvMax) {
        return pvMax <= seuilPvMax;
    }

    /**
     * Degats de l'eclatement : en partie de zombies, la creature meurt (montant largement au-dessus de ses PV, meme apres les
     * facteurs du mode zombies) ; hors partie, {@code hors} (x1,5 / x2 / x2,5 au Pack-a-Punch).
     */
    public static float degatsEclat(boolean zombies, float pvMax, float pv, float hors, int pap) {
        if (zombies) return Math.max(pvMax, pv) * 4f + 100f;
        return hors * (1 + 0.5f * Math.max(0, pap));
    }

    /** Degats d'un tir sur un boss (qui ne gele pas). */
    public static float degatsColosse(float colosse, int pap) {
        return colosse * (1 + 0.5f * Math.max(0, pap));
    }

    /**
     * Gel apres un tick sans nouveau tir : intact pendant {@code dureeFroid} ticks apres le dernier tir, puis fond de
     * {@code fonteParTick} par tick (jamais sous 0).
     */
    public static float fondre(float gel, long ticksDepuisTir, double dureeFroidTicks, double fonteParTick) {
        if (ticksDepuisTir <= dureeFroidTicks) return gel;
        return (float) Math.max(0, gel - fonteParTick);
    }

    /** Gel plafonne : un boss reste sous le gel complet. */
    public static float cumuler(float gel, float gain, boolean peutGeler) {
        float g = gel + Math.max(0, gain);
        return peutGeler ? Math.min(1f, g) : Math.min(0.95f, g);
    }
}
