package fr.heralisz.utils.train;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Le réseau ferré de HeraliZ : une boucle échantillonnée au bloc (x, y = dessus du ballast, z), la nature de chaque
 * point (plat, remblai, tranchée, pont, tunnel, passage à niveau) et les 10 gares. Écrit par la map
 * (heralisz-map/gen/sites/rail_export.py) dans le jar : serveur et clients lisent exactement le même tracé. Toutes les
 * positions le long de la voie sont des abscisses curvilignes s (blocs), qui bouclent sur la longueur du réseau.
 */
public final class Reseau {
    /** Dessus du rail au-dessus du ballast. */
    public static final float RAIL = 0.32f;
    /** Plancher des voitures au-dessus du dessus du rail (= niveau du quai). */
    public static final float PLANCHER = 1.12f;
    /** Écartement : axe de chaque file de rail à cette distance de l'axe de la voie. */
    public static final float FILE = 1.0f;

    public static final byte PLAT = 0, REMBLAI = 1, TRANCHEE = 2, PONT = 3, TUNNEL = 4, PASSAGE = 5;

    /** Une gare : nom, nom cyrillique, abscisse du centre du quai, cap du quai (degrés, 0 = est, 90 = sud), côté du
     *  quai (+1 à droite dans le sens de la marche, -1 à gauche), hauteur du dessus du quai (= plancher du train). */
    public record Gare(String nom, String cyr, int indice, double s, float cap, int cote, int yQuai) {}

    private static Reseau instance;
    private static boolean essaye;
    /** Fichier du réseau dans le jar (assets/heralisz_utils/train/<nom>.json) : « reseau » (ancienne map) ou « reseau_v2 »
     *  (nouvelle map, 09/10). Chaque monde dit le sien (données « heralisz_train »), le serveur l'annonce aux joueurs. */
    private static String nom = "reseau";

    /** Choisit le réseau ; s'il change, il sera relu et l'horaire recalculé. */
    public static void choisir(String n) {
        if (n == null || n.isEmpty()) n = "reseau";
        synchronized (Reseau.class) {
            if (n.equals(nom)) return;
            nom = n;
            instance = null;
            essaye = false;
        }
        Horaire.oublier();
        HeraliszUtils.LOG.info("Réseau ferré choisi : {}", n);
    }

    public static synchronized String nom() {
        return nom;
    }

    public final int n;
    public final float[] x, y, z;
    public final double[] s;
    public final double longueur;
    public final byte[] genre;
    /** Rayon de courbure (blocs) à chaque point, 1e4 en ligne droite. */
    public final float[] rayon;
    public final Gare[] gares;

    private Reseau(float[] x, float[] y, float[] z, byte[] genre, Gare[] gares) {
        this.n = x.length;
        this.x = x;
        this.y = y;
        this.z = z;
        this.genre = genre;
        this.s = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            int j = i % n;
            s[i] = s[i - 1] + Math.sqrt(sq(x[j] - x[i - 1]) + sq(y[j] - y[i - 1]) + sq(z[j] - z[i - 1]));
        }
        this.longueur = s[n];
        this.rayon = new float[n];
        int k = 10;
        for (int i = 0; i < n; i++) {
            int a = (i - k + n) % n, b = (i + k) % n;
            double ax = x[a], az = z[a], bx = x[i], bz = z[i], cx = x[b], cz = z[b];
            double ab = Math.hypot(bx - ax, bz - az), bc = Math.hypot(cx - bx, cz - bz), ca = Math.hypot(ax - cx, az - cz);
            double aire = Math.abs((bx - ax) * (cz - az) - (bz - az) * (cx - ax)) / 2;
            rayon[i] = (float) Math.min(1e4, ab * bc * ca / Math.max(4 * aire, 1e-9));
        }
        Gare[] g = new Gare[gares.length];
        for (int i = 0; i < g.length; i++) {
            Gare o = gares[i];
            g[i] = new Gare(o.nom, o.cyr, o.indice, s[o.indice], o.cap, o.cote, o.yQuai);
        }
        this.gares = g;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Le réseau du jar, ou null s'il est absent ou illisible (pas de train). */
    public static synchronized Reseau get() {
        if (!essaye) {
            essaye = true;
            try (InputStream in = Reseau.class.getResourceAsStream("/assets/heralisz_utils/train/" + nom + ".json")) {
                if (in == null) {
                    HeraliszUtils.LOG.warn("Réseau ferré absent du jar : pas de train");
                } else {
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        instance = lire(JsonParser.parseReader(r).getAsJsonObject());
                        HeraliszUtils.LOG.info("Réseau ferré : {} blocs, {} gares", Math.round(instance.longueur), instance.gares.length);
                    }
                }
            } catch (Exception e) {
                HeraliszUtils.LOG.error("Réseau ferré illisible : pas de train", e);
            }
        }
        return instance;
    }

    private static Reseau lire(JsonObject o) {
        JsonArray p = o.getAsJsonArray("points");
        int n = p.size() / 3;
        float[] x = new float[n], y = new float[n], z = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = p.get(3 * i).getAsFloat();
            y[i] = p.get(3 * i + 1).getAsFloat();
            z[i] = p.get(3 * i + 2).getAsFloat();
        }
        String gs = o.get("genre").getAsString();
        byte[] genre = new byte[n];
        for (int i = 0; i < n && i < gs.length(); i++) {
            genre[i] = (byte) "prtTPx".indexOf(gs.charAt(i));
            if (genre[i] < 0) genre[i] = PLAT;
        }
        JsonArray ga = o.getAsJsonArray("gares");
        Gare[] gares = new Gare[ga.size()];
        for (int i = 0; i < gares.length; i++) {
            JsonObject g = ga.get(i).getAsJsonObject();
            gares[i] = new Gare(g.get("nom").getAsString(), g.get("cyr").getAsString(), g.get("i").getAsInt(), 0,
                    g.get("cap").getAsFloat(), g.get("cote").getAsInt(), g.get("yquai").getAsInt());
        }
        return new Reseau(x, y, z, genre, gares);
    }

    /** Ramène s dans [0, longueur). */
    public double mod(double s0) {
        double r = s0 % longueur;
        return r < 0 ? r + longueur : r;
    }

    /** Indice du segment [i, i+1] qui contient l'abscisse s (déjà ramenée). */
    public int segment(double sv) {
        int i = (int) Math.min(n - 1, Math.max(0, sv));          // échantillonné au bloc : s[i] ≈ i
        while (i > 0 && s[i] > sv) i--;
        while (i < n - 1 && s[i + 1] <= sv) i++;
        return i;
    }

    /** Point de l'axe de la voie (dessus du ballast) à l'abscisse s : out = {x, y, z}. */
    public void point(double s0, double[] out) {
        double sv = mod(s0);
        int i = segment(sv), j = (i + 1) % n;
        double f = (sv - s[i]) / Math.max(1e-9, s[i + 1] - s[i]);
        out[0] = x[i] + (x[j] - x[i]) * f;
        out[1] = y[i] + (y[j] - y[i]) * f;
        out[2] = z[i] + (z[j] - z[i]) * f;
    }

    /** Rayon de courbure à l'abscisse s. */
    public float rayon(double s0) {
        return rayon[segment(mod(s0))];
    }

    public byte genre(double s0) {
        return genre[segment(mod(s0))];
    }

    /** Écart signé b - a le long de la boucle, dans (-L/2, L/2]. */
    public double ecart(double a, double b) {
        double d = mod(b - a);
        return d > longueur / 2 ? d - longueur : d;
    }

}
