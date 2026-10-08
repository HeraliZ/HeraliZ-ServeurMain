package fr.heraliz.wonder.blundergat;

/**
 * Calculs purs du Blundergat et de l'Acid Gat (aucune classe de Minecraft : testes hors du jeu par
 * {@code tools/wonder/tests_blundergat.py}) : directions des 3 cartouches d'un tir, pas de vol (meme physique cote serveur
 * et cote client), degats de l'explosion d'acide selon la distance, clignotement de la cartouche collee.
 */
public final class CalculsBlunder {
    private CalculsBlunder() {
    }

    /** Gravite d'une cartouche (blocs par tick par tick) et frottement de l'air (par tick). */
    public static final double GRAVITE = 0.035, FROTTEMENT = 0.99;

    /**
     * Direction (unitaire) de la cartouche {@code k} sur {@code n} : la direction du regard (unitaire) tournee autour de la
     * verticale de {@code (k - (n-1)/2) * ecart} degres (gerbe horizontale, comme le tir en eventail de l'Acid Gat).
     */
    public static void directionCartouche(double dx, double dy, double dz, int k, int n, double ecartDeg, double[] out) {
        double a = Math.toRadians((k - (n - 1) / 2.0) * ecartDeg);
        double c = Math.cos(a), s = Math.sin(a);
        double x = dx * c + dz * s, z = -dx * s + dz * c;
        double l = Math.sqrt(x * x + dy * dy + z * z);
        if (l < 1e-9) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 1;
            return;
        }
        out[0] = x / l;
        out[1] = dy / l;
        out[2] = z / l;
    }

    /** Un tick de vol : {@code p} (x, y, z) avance de {@code v}, puis la vitesse subit la gravite et le frottement. */
    public static void pas(double[] p, double[] v) {
        p[0] += v[0];
        p[1] += v[1];
        p[2] += v[2];
        v[0] *= FROTTEMENT;
        v[1] = (v[1] - GRAVITE) * FROTTEMENT;
        v[2] *= FROTTEMENT;
    }

    /**
     * Degats d'une explosion d'acide sur une creature dont la boite est a la distance {@code d} du centre : {@code centre}
     * au centre, 40 % au bord du rayon, 0 au-dela.
     */
    public static float degatsAcide(double d, double rayon, double centre) {
        if (rayon <= 0 || d > rayon) return 0f;
        double k = Math.max(0, Math.min(1, d / rayon));
        return (float) (centre * (1 - 0.6 * k));
    }

    /** Facteur des degats hors partie de zombies selon le niveau de Pack-a-Punch (x1 ; x1,5 ; x2 ; x2,5). */
    public static double facteurPapHors(int pap) {
        return 1 + 0.5 * Math.max(0, pap);
    }

    /**
     * Clignotement de la cartouche collee (0..1) a l'age {@code age} (ticks depuis qu'elle a colle) sur {@code delai} ticks :
     * la periode passe de 8 a 1,5 tick (le bip accelere avant l'explosion).
     */
    public static float clignote(float age, float delai) {
        float u = Math.max(0, Math.min(1, age / Math.max(1, delai)));
        float periode = 8f - 6.5f * u;
        float ph = (age % periode) / periode;
        return ph < 0.35f ? 1f : 0.25f;
    }
}
