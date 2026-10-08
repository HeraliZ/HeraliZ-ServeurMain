package fr.heraliz.wonder.paralyzer;

/**
 * Calculs purs du Paralyzer (sans Minecraft : testes hors jeu par {@code tools/wonder/pz_tests.py}). Aucune allocation.
 */
public final class CalculsParalyseur {
    private CalculsParalyseur() {
    }

    /** Couleurs : Paralyzer jaune pale / blanc, Petrifier (Pack-a-Punch) turquoise. */
    public static final int LUEUR = 0xFFE07A, COEUR = 0xFFFBEA, LUEUR_PAP = 0x2FF0DA, COEUR_PAP = 0xE4FFFB;

    public static int lueur(int pap) {
        return pap > 0 ? LUEUR_PAP : LUEUR;
    }

    public static int coeur(int pap) {
        return pap > 0 ? COEUR_PAP : COEUR;
    }

    /** Longueur du rayon : base, ou porteePap + 2 par niveau au-dessus du premier. */
    public static double portee(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 2.0 * (Math.min(pap, 3) - 1);
    }

    /** Rayon du faisceau : base, ou rayonPap + 0,1 par niveau au-dessus du premier. */
    public static double rayon(int pap, double base, double basePap) {
        return pap <= 0 ? base : basePap + 0.1 * (Math.min(pap, 3) - 1);
    }

    /** Impulsions avant le gel. */
    public static int gel(int pap, int base, int basePap) {
        return Math.max(1, pap <= 0 ? base : basePap);
    }

    /** Impulsions avant la poussiere (toujours au moins une de plus que le gel). */
    public static int poussiere(int pap, int base, int basePap, int gelPap) {
        int n = pap <= 0 ? base : basePap - (Math.min(pap, 3) - 1);
        return Math.max(n, gel(pap, gelPap, gelPap) + 1);
    }

    /** Multiplicateur des degats sur un boss : 1 ; 1,5 / 2 / 2,5 au Pack-a-Punch. */
    public static float multiplicateurColosse(int pap) {
        return pap <= 0 ? 1f : 1f + 0.5f * Math.min(pap, 3);
    }

    /**
     * Position le long du rayon (0..longueur) du point le plus proche de P, si P (centre du corps) est a moins de
     * {@code marge} de l'axe ; -1 sinon. Le rayon part de O dans la direction unitaire D.
     */
    public static double dansRayon(double px, double py, double pz, double ox, double oy, double oz, double dx, double dy, double dz, double longueur,
                                   double marge) {
        double vx = px - ox, vy = py - oy, vz = pz - oz;
        double t = vx * dx + vy * dy + vz * dz;
        if (t < -marge || t > longueur + marge) return -1;
        double tc = t < 0 ? 0 : (t > longueur ? longueur : t);
        double qx = vx - dx * tc, qy = vy - dy * tc, qz = vz - dz * tc;
        return qx * qx + qy * qy + qz * qz <= marge * marge ? tc : -1;
    }

    /** Le regard vise-t-il assez bas pour flotter ? (dy : composante verticale du regard, unitaire) */
    public static boolean viseLeSol(double dy, double angleMinDeg) {
        return dy <= -Math.sin(Math.toRadians(angleMinDeg));
    }

    /**
     * Vitesse verticale pour flotter a {@code cible} blocs du sol quand on est a {@code hauteur} : monte doucement si trop
     * bas (au plus 0,16 bloc/tick), descend en douceur si trop haut (au plus 0,06 bloc/tick), jamais plus vite que la
     * vitesse actuelle vers le bas n'est freinee : chute ralentie, pas de vol libre (on ne depasse jamais la cible).
     */
    public static double vitesseFlotte(double hauteur, double cible, double vy) {
        double ecart = cible - hauteur;
        if (ecart > 0) return Math.min(0.16, Math.max(vy, ecart * 0.3));
        return Math.max(vy, Math.max(-0.06, ecart * 0.2));
    }

    /** Degats d'une impulsion qui reduit en poussiere : de quoi tuer surement. */
    public static float degatsPoussiere(float pv) {
        return pv + 1000f;
    }
}
