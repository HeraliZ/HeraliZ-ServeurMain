package fr.heraliz.wonder.jetgun.client;

import net.minecraft.client.Camera;
import org.joml.Vector3f;

/**
 * Géométrie de la vue à la première personne du Jet Gun : TaCZ ne donne que la position de la bouche (os muzzle_flash) ;
 * les autres points (section chaude, soupapes, tuyère) en sont déduits avec la vue de la hanche du modèle
 * ({@code tools/wonder/jg_modele_hd.py} : VUE_OEIL, VUE_ROT et les points ci-dessous, à garder en phase). Espace caméra de
 * Minecraft = repère V du modèle (x à droite, y en haut, z vers l'arrière) tourné par l'inverse de idle_view, en blocs
 * (1 pixel de modèle = 1/16 de bloc, multiplié par l'échelle mesurée sur la bouche).
 */
final class JetVue {
    private JetVue() {
    }

    // vue de la hanche du modèle HD (tools/wonder/jg_modele_hd.py : VUE_OEIL, VUE_ROT = aucune rotation, convention de TaCZ)
    private static final double OX = -12.0, OY = 20.0, OZ = 7.5, LACET = 0, TANGAGE = 0, ROULIS = 0;
    private static final double[] BOUCHE = {0, 12, -28.2};
    private static final double[][] RV = rv();
    /** Axe de l'arme (vers l'avant) en espace caméra. */
    static final float[] AVANT = vecteur(0, 0, -1);
    /** Décalages bouche -> point chaud (dessus de la section chaude), soupapes, sortie de tuyère (blocs, échelle 1). */
    static final float[] H = delta(-2.15, 17.9, -1.6), S = delta(3.4, 18.6, -2.2), X = delta(0, 12, 8.2);
    /** Distance œil -> bouche attendue (blocs, échelle 1) : sert à mesurer l'échelle réelle de la vue. */
    static final float LONG_BOUCHE;

    static {
        float[] b = camera(BOUCHE[0], BOUCHE[1], BOUCHE[2]);
        LONG_BOUCHE = (float) Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
    }

    private static double[][] rv() {
        double[][] y = rot(1, LACET), x = rot(0, TANGAGE), z = rot(2, ROULIS);
        return mul(y, mul(x, z));
    }

    private static double[][] rot(int axe, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return switch (axe) {
            case 0 -> new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
            case 1 -> new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
            default -> new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
        };
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    /** RV^T · v (vecteur du repère V -> espace caméra). */
    private static float[] vecteur(double x, double y, double z) {
        return new float[]{(float) (RV[0][0] * x + RV[1][0] * y + RV[2][0] * z), (float) (RV[0][1] * x + RV[1][1] * y + RV[2][1] * z),
                (float) (RV[0][2] * x + RV[1][2] * y + RV[2][2] * z)};
    }

    /** Point du modèle (pixels, repère V) -> espace caméra (blocs, échelle 1). */
    private static float[] camera(double x, double y, double z) {
        float[] v = vecteur(x - OX, y - OY, z - OZ);
        v[0] /= 16f;
        v[1] /= 16f;
        v[2] /= 16f;
        return v;
    }

    private static float[] delta(double x, double y, double z) {
        float[] v = vecteur(x - BOUCHE[0], y - BOUCHE[1], z - BOUCHE[2]);
        v[0] /= 16f;
        v[1] /= 16f;
        v[2] /= 16f;
        return v;
    }

    private static final Vector3f T = new Vector3f();
    private static final float RAD = (float) (Math.PI / 180.0);

    /** Vecteur de l'espace caméra -> monde (même rotation que le décalage du canon de TaCZ). */
    static void versMonde(Camera cam, float[] v, Vector3f out) {
        out.set(v[0], v[1], v[2]).rotateX(-cam.getXRot() * RAD).rotateY(-(cam.getYRot() + 180f) * RAD);
    }

    /** Décalage (espace caméra, échelle 1) multiplié par l'échelle s, en monde (vecteur temporaire partagé). */
    static Vector3f decalageMonde(Camera cam, float[] d, float s) {
        T.set(d[0] * s, d[1] * s, d[2] * s).rotateX(-cam.getXRot() * RAD).rotateY(-(cam.getYRot() + 180f) * RAD);
        return T;
    }
}
