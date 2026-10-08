package fr.heraliz.wonder;

/**
 * Calculs purs des Wonder Weapons (aucune classe de Minecraft : testables hors du jeu, voir
 * {@code tools/wonder/tests/TestsCalculs.java}) : degats de l'eclaboussure, variantes Pack-a-Punch, choix des cibles de
 * la foudre en chaine, delai des arcs, cone et souffle du Thundergun. Le serveur les appelle avec les vraies entites ({@link Monde}).
 */
public final class Calculs {
    private Calculs() {
    }

    // ------------------------------------------------------------------ Pistolet a rayons
    /** Rayon de l'eclaboussure : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +0,25 par niveau au-dessus. */
    public static double rayonEclaboussure(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 0.25 * (pap - 1);
    }

    /** Degats de l'eclaboussure a la distance {@code d} (bord de la boite de la cible) : de {@code max} au centre a {@code bord}. */
    public static float degatsEclaboussure(double d, double r, float max, float bord) {
        if (d > r || r <= 0) return 0f;
        float k = (float) Math.max(0, Math.min(1, d / r));
        return max + (bord - max) * k;
    }

    /** Degats de l'eclaboussure sur le tireur lui-meme (0 au bord, {@code max} au centre). */
    public static float autoDegats(double d, double r, double max) {
        if (d >= r || r <= 0) return 0f;
        return (float) (max * (1 - d / r));
    }

    // ------------------------------------------------------------------ Wunderwaffe
    /** Cibles au plus : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +2 par niveau au-dessus, borne {@code max}. */
    public static int ciblesFoudre(int pap, int base, int basePap, int max) {
        return Math.min(max, pap <= 0 ? base : basePap + 2 * (pap - 1));
    }

    /** Longueur d'un saut d'arc : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +0,75 par niveau au-dessus. */
    public static double sautFoudre(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 0.75 * (pap - 1);
    }

    /**
     * Degats d'un arc : hors partie de zombies, {@code hors} ; en partie, une creature d'au plus {@code seuilPvMax} PV max
     * meurt d'un coup (montant largement au-dessus de ses PV, meme apres les facteurs du mode zombies), un boss prend
     * {@code colosse}.
     */
    public static float degatsFoudre(boolean zombies, float pvMax, float pv, double seuilPvMax, float hors, float colosse) {
        if (!zombies) return hors;
        if (pvMax <= seuilPvMax) return Math.max(pvMax, pv) * 4f + 100f;
        return colosse;
    }

    /** Tick (apres le tir) ou la cible de rang {@code rang} est foudroyee : 2 cibles par tick, la premiere tout de suite. */
    public static int delaiArc(int rang) {
        return (rang + 1) / 2;
    }

    /** Instant (en ticks, fractionnaire) ou l'arc de rang {@code rang} arrive, cote client. */
    public static float arriveeArc(int rang) {
        return rang * 0.5f;
    }


    // ------------------------------------------------------------------ Thundergun
    /** Longueur du cone : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +2 par niveau au-dessus. */
    public static double porteeSouffle(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 2.0 * (pap - 1);
    }

    /** Demi-angle du cone (degres) : {@code base} sans Pack-a-Punch, {@code basePap} au niveau 1, +2,5 par niveau au-dessus. */
    public static double angleSouffle(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 2.5 * (pap - 1);
    }

    /**
     * Le point (dx, dy, dz) relatif a la bouche est-il dans le cone d'axe unitaire (ax, ay, az) ? Cone tronque : rayon
     * {@code r0} a la bouche, qui s'ouvre de {@code demiAngle} degres, long de {@code portee}. {@code marge} = demi-largeur
     * de la cible (une creature dont le bord entre dans le cone est touchee). Rend la distance le long de l'axe, -1 dehors.
     */
    public static double dansCone(double dx, double dy, double dz, double ax, double ay, double az, double portee, double demiAngle,
                                  double r0, double marge) {
        double s = dx * ax + dy * ay + dz * az;
        if (s < -marge || s > portee + marge) return -1;
        double px = dx - ax * s, py = dy - ay * s, pz = dz - az * s;
        double perp = Math.sqrt(px * px + py * py + pz * pz);
        double rayon = r0 + Math.max(0, s) * Math.tan(Math.toRadians(demiAngle));
        return perp <= rayon + marge ? Math.max(0, s) : -1;
    }

    /**
     * Vitesse donnee par le souffle (blocs par tick) a la distance {@code s} le long de l'axe : pleine force a la bouche,
     * 35 % au bout du cone ; reduite par la resistance au recul (0..1).
     */
    public static double pousseeSouffle(double s, double portee, double force, double resistanceRecul) {
        double k = 1 - 0.65 * Math.max(0, Math.min(1, s / Math.max(portee, 1e-6)));
        return force * k * (1 - Math.max(0, Math.min(1, resistanceRecul)) * 0.8);
    }

    /** Duree (ticks) du trajet du premier front d'onde sur toute la longueur du cone (le client dessine le meme). */
    public static final float DUREE_FRONT = 11f;

    /**
     * Tick (apres le tir) ou le front d'onde atteint la distance {@code s} le long de l'axe : il part tres vite et ralentit
     * (position = portee * (1 - (1 - t / DUREE_FRONT)^2,2)) ; 0 pres de la bouche, {@code DUREE_FRONT} au bout du cone.
     */
    public static int arriveeSouffle(double s, double portee) {
        double u = Math.max(0, Math.min(1, s / Math.max(portee, 1e-6)));
        return (int) Math.round(DUREE_FRONT * (1 - Math.pow(1 - u, 1 / 2.2)));
    }

    /**
     * Degats d'un tir de Thundergun : hors partie de zombies, {@code hors} (x1,5 / x2 / x2,5 au Pack-a-Punch) ; en partie,
     * une creature d'au plus {@code seuilPvMax} PV max meurt d'un coup, un boss prend {@code colosse} (memes facteurs).
     */
    public static float degatsSouffle(boolean zombies, float pvMax, float pv, double seuilPvMax, float hors, float colosse, int pap) {
        float k = 1 + 0.5f * Math.max(0, pap);
        if (!zombies) return hors * k;
        if (pvMax <= seuilPvMax) return Math.max(pvMax, pv) * 4f + 100f;
        return colosse * k;
    }

    // ------------------------------------------------------------------ choix des cibles de la chaine
    /** Ce que la chaine voit du monde. Les cibles sont designees par un numero stable pendant un tir. */
    public interface Monde {
        /**
         * Cibles valables dans la boite de demi-cote {@code r} centree en (x, y, z) : leurs numeros dans {@code out} (au
         * plus {@code out.length}), rend leur nombre. Une seule recherche bornee par appel.
         */
        int candidats(double x, double y, double z, double r, int[] out);

        double x(int c);

        double y(int c);

        double z(int c);

        /** Vue directe (pas de bloc plein) du point a la cible. */
        boolean voit(double x, double y, double z, int c);
    }

    /** Nombre maximal de candidats examines par saut (borne la recherche dans une horde tres dense). */
    public static final int CANDIDATS_MAX = 96;
    /** Lancers de rayon (vue directe) au plus par saut. */
    public static final int ESSAIS_VUE = 3;

    /**
     * Cible valable la plus proche du point, dans le rayon {@code r} (distance vraie), pas deja dans {@code deja[0..nDeja)},
     * en vue directe ; les plus proches d'abord, au plus {@link #ESSAIS_VUE} essais de vue. -1 si aucune.
     * {@code tampon} : au moins {@link #CANDIDATS_MAX} cases (reutilise, aucune allocation).
     */
    public static int plusProche(Monde m, double x, double y, double z, double r, int[] deja, int nDeja, int[] tampon) {
        int n = m.candidats(x, y, z, r, tampon);
        double r2 = r * r;
        for (int essai = 0; essai < ESSAIS_VUE; essai++) {
            int best = -1;
            double bd = r2;
            for (int i = 0; i < n; i++) {
                int c = tampon[i];
                if (c < 0 || contient(deja, nDeja, c)) continue;
                double dx = m.x(c) - x, dy = m.y(c) - y, dz = m.z(c) - z;
                double dd = dx * dx + dy * dy + dz * dz;
                if (dd <= bd) {
                    bd = dd;
                    best = i;
                }
            }
            if (best < 0) return -1;
            int c = tampon[best];
            if (m.voit(x, y, z, c)) return c;
            tampon[best] = -1;
        }
        return -1;
    }

    /**
     * Chaine complete a partir de la premiere cible {@code premiere} (deja choisie : touchee par l'eclair) : a chaque saut,
     * la cible la plus proche de la derniere, pas encore touchee, a moins de {@code saut} blocs et en vue directe ; arret a
     * {@code max} cibles ou quand plus rien n'est a portee. Rend le nombre de cibles ecrites dans {@code out} (dans l'ordre).
     * Bornes : {@code max} recherches au plus, {@link #ESSAIS_VUE} lancers de rayon au plus par saut.
     */
    public static int chaine(Monde m, int premiere, int max, double saut, int[] out, int[] tampon) {
        if (premiere < 0 || max <= 0) return 0;
        int n = 0;
        out[n++] = premiere;
        while (n < max) {
            int cur = out[n - 1];
            int s = plusProche(m, m.x(cur), m.y(cur), m.z(cur), saut, out, n, tampon);
            if (s < 0) break;
            out[n++] = s;
        }
        return n;
    }

    private static boolean contient(int[] t, int n, int v) {
        for (int i = 0; i < n; i++) if (t[i] == v) return true;
        return false;
    }
}
