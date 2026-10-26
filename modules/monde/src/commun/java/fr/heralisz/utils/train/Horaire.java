package fr.heralisz.utils.train;

/**
 * Horaire du train, entièrement déterminé par le temps du monde : le serveur et chaque client calculent la même
 * position au même instant, sans rien s'envoyer. Le train fait le tour de la boucle sans fin et s'arrête 1,5 minute à
 * chaque gare (son milieu au milieu du quai). Entre deux gares : accélération douce, vitesse de croisière, ralentie
 * dans les courbes tant qu'une partie du train y est, freinage jusqu'à l'arrêt exact.
 */
public final class Horaire {
    public static final double VMAX = 15.0;          // blocs/s (54 km/h)
    public static final double ACC = 0.45, DEC = 0.55;
    public static final double ALAT = 1.6;            // accélération latérale admise dans les courbes (blocs/s²)
    public static final double ARRET = 90.0;          // secondes à quai
    public static final double OUVERTURE = 3.0;       // portes ouvertes 3 s après l'arrêt...
    public static final double FERMETURE = 8.0;       // ...et fermées 8 s avant le départ
    public static final double MOUVEMENT_PORTE = 1.7; // durée d'ouverture / de fermeture (sortie puis glissement)

    /** Véhicules de la rame, de l'avant à l'arrière. */
    public enum Vehicule {
        LOCO(20.0, 6.4, "loco"), VOITURE(23.0, 8.0, "voiture");

        public final double longueur, bogie;
        public final String modele;

        Vehicule(double longueur, double bogie, String modele) {
            this.longueur = longueur;
            this.bogie = bogie;
            this.modele = modele;
        }
    }

    public static final Vehicule[] RAME = {Vehicule.LOCO, Vehicule.VOITURE, Vehicule.VOITURE, Vehicule.VOITURE,
            Vehicule.VOITURE, Vehicule.VOITURE, Vehicule.VOITURE};
    /** Modèle de chaque véhicule (tools/gen_train.py, VARIANTES) : la loco puis des voitures toutes différentes. */
    public static final String[] MODELES = {"loco", "voiture_a", "voiture_c", "fourgon", "voiture_b", "voiture_a", "voiture_c"};
    /** Rames sur la boucle (v2, 09/10) : la seconde suit exactement le même horaire avec un demi-tour d'avance (elles
     *  sont toujours à l'opposé l'une de l'autre et ne se croisent jamais sur la voie unique). Une voiture est désignée
     *  par un numéro global : rame * N + place dans la rame. */
    public static final int RAMES = 2;
    public static final int N = RAME.length;

    public static int rame(int veh) {
        return Math.max(0, veh) / N;
    }

    public static int place(int veh) {
        return Math.floorMod(veh, N);
    }

    public static final double ATTELAGE = 0.6;
    /** Longueur de la rame et décalage du centre de chaque véhicule par rapport au milieu de la rame. */
    public static final double LONGUEUR;
    public static final double[] DECALAGE = new double[RAME.length];

    static {
        double l = 0;
        for (Vehicule v : RAME) l += v.longueur;
        l += ATTELAGE * (RAME.length - 1);
        LONGUEUR = l;
        double avant = l / 2;
        for (int i = 0; i < RAME.length; i++) {
            DECALAGE[i] = avant - RAME[i].longueur / 2;
            avant -= RAME[i].longueur + ATTELAGE;
        }
    }

    private static volatile Horaire instance;

    /** Le réseau a changé (Reseau.choisir) : l'horaire sera recalculé. */
    static void oublier() {
        instance = null;
    }

    public final Reseau reseau;
    public final double[] sGare;            // abscisse d'arrêt (milieu du quai) de chaque gare
    public final double[] longueurTrajet;   // de la gare k à la gare k+1
    public final float[][] temps;            // temps (s) depuis le départ, à chaque pas du trajet k
    public final double[] pas;
    public final double[] debutArret;       // instant (dans le cycle) de l'arrivée à la gare k
    public final double periode;

    private Horaire(Reseau r) {
        this.reseau = r;
        int ng = r.gares.length;
        sGare = new double[ng];
        for (int k = 0; k < ng; k++) sGare[k] = r.gares[k].s();
        // vitesse limite en chaque point, puis sur toute la longueur du train (fenêtre glissante)
        float[] vp = new float[r.n];
        for (int i = 0; i < r.n; i++) vp[i] = (float) Math.min(VMAX, Math.sqrt(ALAT * r.rayon[i]));
        int demi = (int) Math.ceil(LONGUEUR / 2) + 4;
        float[] vt = new float[r.n];
        for (int i = 0; i < r.n; i++) {
            float m = (float) VMAX;
            for (int d = -demi; d <= demi; d++) m = Math.min(m, vp[(i + d + r.n) % r.n]);
            vt[i] = m;
        }
        longueurTrajet = new double[ng];
        temps = new float[ng][];
        pas = new double[ng];
        debutArret = new double[ng];
        double t = 0;
        for (int k = 0; k < ng; k++) {
            debutArret[k] = t;
            t += ARRET;
            double D = r.mod(sGare[(k + 1) % ng] - sGare[k]);
            int m = Math.max(2, (int) Math.ceil(D));
            double ds = D / m;
            longueurTrajet[k] = D;
            pas[k] = ds;
            double[] v = new double[m + 1];
            for (int i = 0; i <= m; i++) v[i] = vt[r.segment(r.mod(sGare[k] + i * ds))];
            v[0] = 0;
            v[m] = 0;
            for (int i = 1; i <= m; i++) v[i] = Math.min(v[i], Math.sqrt(v[i - 1] * v[i - 1] + 2 * ACC * ds));
            for (int i = m - 1; i >= 0; i--) v[i] = Math.min(v[i], Math.sqrt(v[i + 1] * v[i + 1] + 2 * DEC * ds));
            float[] tk = new float[m + 1];
            for (int i = 1; i <= m; i++) {
                double vm = Math.max(0.15, (v[i - 1] + v[i]) / 2);
                tk[i] = (float) (tk[i - 1] + ds / vm);
            }
            temps[k] = tk;
            t += tk[m];
        }
        periode = t;
    }

    /** L'horaire du réseau du jar, ou null s'il n'y a pas de réseau. */
    public static synchronized Horaire get() {
        if (instance == null) {
            Reseau r = Reseau.get();
            if (r != null && r.gares.length >= 2) instance = new Horaire(r);
        }
        return instance;
    }

    /** Position de la rame à un instant : abscisse du milieu, vitesse, gare à quai (-1 en route), gare suivante, temps
     *  restant à quai (en route : temps depuis le départ). */
    public record Etat(double s, double v, int aQuai, int suivante, double reste, double tCycle) {}

    /** Temps de l'horaire de la rame r (la rame 0 est celle d'origine). */
    public double tRame(double t, int r) {
        return t + r * periode / RAMES;
    }

    /** État de la rame r à l'instant t. */
    public Etat etatRame(double t, int r) {
        return etat(tRame(t, r));
    }

    /** t : secondes de jeu (temps du monde / 20, décalage compris). */
    public Etat etat(double t) {
        double tc = t % periode;
        if (tc < 0) tc += periode;
        int ng = sGare.length;
        for (int k = 0; k < ng; k++) {
            double a = debutArret[k];
            double fin = a + ARRET;
            if (tc < fin) {
                return new Etat(sGare[k], 0, k, (k + 1) % ng, fin - tc, tc);
            }
            float[] tk = temps[k];
            double tr = tc - fin;
            if (tr < tk[tk.length - 1] || k == ng - 1) {
                int lo = 0, hi = tk.length - 1;
                while (hi - lo > 1) {
                    int mi = (lo + hi) >>> 1;
                    if (tk[mi] <= tr) lo = mi;
                    else hi = mi;
                }
                double f = (tr - tk[lo]) / Math.max(1e-6, tk[hi] - tk[lo]);
                f = Math.max(0, Math.min(1, f));
                double sv = sGare[k] + pas[k] * (lo + f);
                double v = pas[k] / Math.max(1e-6, tk[hi] - tk[lo]);
                return new Etat(reseau.mod(sv), v, -1, (k + 1) % ng, tr, tc);
            }
        }
        return new Etat(sGare[0], 0, 0, 1 % ng, ARRET, tc);
    }

    /** Secondes avant l'arrivée du train à la gare k (0 s'il est à quai). */
    public double attente(int k, Etat e) {
        if (e.aQuai() == k) return 0;
        double d = (debutArret[k] - e.tCycle()) % periode;
        return d < 0 ? d + periode : d;
    }

    /** Ouverture des portes côté quai (0 fermées, 1 ouvertes) pour un état à quai. */
    public static double portes(Etat e) {
        if (e.aQuai() < 0) return 0;
        double depuis = ARRET - e.reste();
        double o = Math.min((depuis - OUVERTURE) / MOUVEMENT_PORTE, (e.reste() - FERMETURE) / MOUVEMENT_PORTE);
        return Math.max(0, Math.min(1, o));
    }

    /** Placement d'un véhicule : centre de la caisse au niveau du dessus du rail, cap (radians, sens de la marche),
     *  pente (radians), et pour chaque bogie son point et son cap. */
    public static final class Placement {
        public double x, y, z, cap, pente;
        public final double[] bogieAvant = new double[4], bogieArriere = new double[4];   // x, y, z, cap
        public double s;
    }

    private final double[] pa = new double[3], pb = new double[3], pt = new double[3];

    /** Place le véhicule i (numéro global ou place dans la rame) de la rame dont le milieu est à l'abscisse sRame. */
    public synchronized void placer(int i, double sRame, Placement out) {
        Vehicule v = RAME[place(i)];
        double sc = sRame + DECALAGE[place(i)];
        out.s = sc;
        bogie(sc + v.bogie, out.bogieAvant);
        bogie(sc - v.bogie, out.bogieArriere);
        double[] a = out.bogieAvant, b = out.bogieArriere;
        out.x = (a[0] + b[0]) / 2;
        out.y = (a[1] + b[1]) / 2;
        out.z = (a[2] + b[2]) / 2;
        double dx = a[0] - b[0], dz = a[2] - b[2];
        out.cap = Math.atan2(dz, dx);
        out.pente = Math.atan2(a[1] - b[1], Math.sqrt(dx * dx + dz * dz));
    }

    private void bogie(double sv, double[] out) {
        reseau.point(sv, pt);
        reseau.point(sv + 1.5, pa);
        reseau.point(sv - 1.5, pb);
        out[0] = pt[0];
        out[1] = pt[1] + Reseau.RAIL;
        out[2] = pt[2];
        out[3] = Math.atan2(pa[2] - pb[2], pa[0] - pb[0]);
    }
}
