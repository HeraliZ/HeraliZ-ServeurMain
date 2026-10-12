package fr.heralisz.utils.train;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Ce qui arrête les balles dans le train (le train n'est pas fait de blocs : sans cela, les balles le traversent) : la
 * caisse de chaque véhicule (plancher, toit, flancs, bouts), les cloisons des vestibules, les banquettes et les
 * tablettes, les objets des vestibules, les caisses du fourgon et le soufflet entre deux voitures, en boîtes dans le
 * repère du véhicule (lx le long, ly au-dessus du rail, lz en travers : celui de Voyageurs.versLocal). Cotes relevées sur
 * les modèles (assets/heralisz_utils/train/*.bin, tools/gen_train.py) et sur Voyageurs.
 * <p>
 * Ouvertures : une balle passe par une vitre cassée (sinon elle la casse et s'arrête), par les portes latérales ouvertes
 * côté quai, par les portes d'intercirculation et de cloison ouvertes. La locomotive est pleine.
 * <p>
 * Commun : géométrie seule ; le serveur l'utilise pour les balles de TaCZ (BallesTrain).
 */
public final class Blindage {
    private Blindage() {}

    /** Nature de ce qui est touché (pour l'impact : étincelles, bourre, éclats de verre). */
    public static final int TOLE = 0, VERRE = 1, SIEGE = 2, BOIS = 3;

    /** Premier obstacle touché par un trajet : fraction t du trajet (0..1), nature, point local, et pour une vitre son
     *  côté (+1 droite, -1 gauche) et sa baie (0..6, Voyageurs.FEN_X). */
    public record Impact(double t, int nature, double lx, double ly, double lz, int cote, int baie) {}

    // cotes de la caisse des voitures (voiture.bin : caisse, verre, intérieur)
    static final float L = 11.5f, W = 2.31f, Y_SOUS = 0.6f, Y_PLAFOND = 4.95f, Y_TOIT = 5.2f;
    /** Baies : vitres de 1,4 bloc de large, de 2,07 à 3,37 au-dessus du rail. */
    static final float BAIE_DEMI = 0.7f, BAIE_Y0 = 2.07f, BAIE_Y1 = 3.37f;
    /** Portes latérales (Voyageurs.PX0..PX1), hautes de 2,75 ; portes intérieures : |lz| < BOUT, jusqu'à 3,72. */
    static final float PORTE_Y1 = 3.87f, PASSAGE_Y1 = 3.72f;
    /** Soufflet : bout du cadre de la caisse jusqu'au milieu de l'attelage (Horaire : 23 + 0,6 entre deux centres). */
    static final float SOUF_X1 = 11.8f, SOUF_W = 1.15f, SOUF_Y0 = 0.96f, SOUF_Y1 = 4.06f;
    /** Locomotive (loco.bin) : pleine. */
    static final float LOCO_L = 10.1f, LOCO_W = 2.28f, LOCO_Y0 = 0.5f, LOCO_Y1 = 4.75f;
    /** Hauteur des objets des vestibules (samovar, poubelle, extincteur), dans l'ordre de voitures.json. */
    private static final float[] H_OBJETS = {2.58f, 1.67f, 1.77f};

    /** Une boîte du repère du véhicule et ce qu'elle est (nature). */
    private record Boite(float x0, float y0, float z0, float x1, float y1, float z1, int nature) {}

    /** Boîtes fixes des voitures à banquettes et du fourgon (calculées une fois). */
    private static List<Boite> fixes(boolean sieges, float[][] obstacles) {
        List<Boite> b = new ArrayList<>();
        b.add(new Boite(-L, Y_SOUS, -W, L, Reseau.PLANCHER, W, TOLE));                       // plancher et châssis
        b.add(new Boite(-L, Y_PLAFOND, -W, L, Y_TOIT, W, TOLE));                             // toit
        for (float[] o : Voyageurs.OBJETS) {
            float h = Voyageurs.OBJETS.length == H_OBJETS.length ? H_OBJETS[indexObjet(o)] : 2.0f;
            b.add(new Boite(o[0], Reseau.PLANCHER, o[1], o[2], h, o[3], TOLE));
        }
        if (sieges) {
            float zs = Voyageurs.SIEGE_Z, zi = Voyageurs.VWI;
            for (float f : Voyageurs.FEN_X) {
                for (int s = -1; s <= 1; s += 2) {
                    for (int c = -1; c <= 1; c += 2) {
                        float za = c > 0 ? zs : -zi, zb = c > 0 ? zi : -zs;
                        b.add(boite(f + s * 0.52f, f + s * 0.89f, Reseau.PLANCHER, 1.55f, za, zb, SIEGE));    // assise
                        b.add(boite(f + s * 0.89f, f + s * 1.11f, Reseau.PLANCHER, 2.42f, za, zb, SIEGE));    // dossier
                    }
                }
                for (int c = -1; c <= 1; c += 2) {
                    float za = c > 0 ? Voyageurs.TABLE_Z : -zi, zb = c > 0 ? zi : -Voyageurs.TABLE_Z;
                    b.add(boite(f - 0.32f, f + 0.32f, 1.85f, 2.12f, za, zb, BOIS));                     // tablette
                }
            }
        }
        if (obstacles != null) {
            for (float[] o : obstacles) {                                                    // caisses du fourgon
                boolean poteau = Math.abs((o[1] + o[3]) / 2) < 0.6f;                         // au milieu : du sol au plafond
                b.add(new Boite(o[0], Reseau.PLANCHER, o[1], o[2], poteau ? 4.83f : 2.6f, o[3], BOIS));
            }
        }
        return b;
    }

    private static int indexObjet(float[] o) {
        for (int i = 0; i < Voyageurs.OBJETS.length; i++) if (Voyageurs.OBJETS[i] == o) return i;
        return 0;
    }

    private static Boite boite(float xa, float xb, float y0, float y1, float z0, float z1, int nature) {
        return new Boite(Math.min(xa, xb), y0, z0, Math.max(xa, xb), y1, z1, nature);
    }

    private static final java.util.Map<String, List<Boite>> PAR_MODELE = new java.util.HashMap<>();

    private static synchronized List<Boite> boites(int veh) {
        String m = Horaire.MODELES[Horaire.place(veh)];
        return PAR_MODELE.computeIfAbsent(m, k -> fixes(Voyageurs.sieges(veh), Voyageurs.obstacles(veh)));
    }

    /** Clé d'une vitre : voiture (numéro global), côté (+1 droite, -1 gauche), baie (0..6). */
    public static int cle(int veh, int cote, int baie) {
        return veh * 16 + (cote > 0 ? 8 : 0) + baie;
    }

    public static int vehiculeDe(int cle) {
        return cle >> 4;
    }

    /** Index de la vitre dans sa voiture (0..13 : 7 baies à gauche, puis 7 à droite), celui de ModeleTrain. */
    public static int fenetreDe(int cle) {
        return ((cle & 8) != 0 ? 7 : 0) + (cle & 7);
    }

    /**
     * Premier obstacle du véhicule veh sur le trajet local a -> b (repère du véhicule), ou null. Un trajet qui commence
     * dans une boîte (tireur collé à un siège) ne la touche pas. portesQuai : côté des portes latérales ouvertes (+1, -1,
     * 0 fermées) ; porteOuverte : portes intérieures ouvertes (0 intercirculation avant, 1 arrière, 4 cloison avant,
     * 5 cloison arrière) ; vitreCassee : clé (cle) d'une vitre déjà cassée.
     */
    public static Impact traverser(int veh, double[] a, double[] b, int portesQuai, IntPredicate porteOuverte, IntPredicate vitreCassee) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        boolean loco = !Voyageurs.voiture(veh);
        // tri rapide : le trajet passe-t-il près de la caisse ?
        float hx = loco ? LOCO_L : SOUF_X1, hz = loco ? LOCO_W : W;
        if (Double.isNaN(entree(a, dx, dy, dz, -hx - 0.1, 0, -hz - 0.1, hx + 0.1, Y_TOIT + 0.3, hz + 0.1, true))) return null;
        if (loco) {
            double t = entree(a, dx, dy, dz, -LOCO_L, LOCO_Y0, -LOCO_W, LOCO_L, LOCO_Y1, LOCO_W, false);
            return Double.isNaN(t) ? null : impact(t, TOLE, a, dx, dy, dz, 0, -1);
        }
        double best = 2;
        Impact res = null;
        for (Boite o : boites(veh)) {
            double t = entree(a, dx, dy, dz, o.x0, o.y0, o.z0, o.x1, o.y1, o.z1, false);
            if (!Double.isNaN(t) && t < best) {
                best = t;
                res = impact(t, o.nature, a, dx, dy, dz, 0, -1);
            }
        }
        // flancs : vitres (cassées : ouvertes) et portes latérales (ouvertes côté quai à l'arrêt)
        for (int c = -1; c <= 1; c += 2) {
            double t = entree(a, dx, dy, dz, -L, Reseau.PLANCHER, c > 0 ? Voyageurs.VWI : -W, L, Y_PLAFOND, c > 0 ? W : -Voyageurs.VWI, false);
            if (Double.isNaN(t) || t >= best) continue;
            double ex = a[0] + dx * t, ey = a[1] + dy * t;
            double ax = Math.abs(ex);
            if (ax > Voyageurs.PX0 - 0.02 && ax < Voyageurs.PX1 + 0.02 && ey < PORTE_Y1 && c == portesQuai) continue;
            int baie = -1;
            if (ey > BAIE_Y0 && ey < BAIE_Y1) {
                for (int k = 0; k < Voyageurs.FEN_X.length; k++) if (Math.abs(ex - Voyageurs.FEN_X[k]) < BAIE_DEMI) baie = k;
            }
            if (baie >= 0 && vitreCassee.test(cle(veh, c, baie))) continue;
            best = t;
            res = impact(t, baie >= 0 ? VERRE : TOLE, a, dx, dy, dz, c, baie);
        }
        // bouts de la caisse (porte d'intercirculation) et cloisons des vestibules (porte de cloison)
        for (int s = -1; s <= 1; s += 2) {
            boolean voisine = Voyageurs.voiture(s > 0 ? veh - 1 : veh + 1);
            int nBout = s > 0 ? 0 : 1, nCloison = s > 0 ? 4 : 5;
            double t = entree(a, dx, dy, dz, s > 0 ? Voyageurs.VL - 0.06 : -L, Reseau.PLANCHER, -W, s > 0 ? L : -Voyageurs.VL + 0.06, Y_PLAFOND, W, false);
            if (!Double.isNaN(t) && t < best && !(voisine && porteOuverte.test(nBout) && passage(a, dy, dz, t))) {
                best = t;
                res = impact(t, TOLE, a, dx, dy, dz, 0, -1);
            }
            float xc = s * Voyageurs.VEST;
            t = entree(a, dx, dy, dz, xc - 0.05, Reseau.PLANCHER, -Voyageurs.VWI, xc + 0.05, Y_PLAFOND, Voyageurs.VWI, false);
            if (!Double.isNaN(t) && t < best && !(porteOuverte.test(nCloison) && passage(a, dy, dz, t))) {
                best = t;
                res = impact(t, TOLE, a, dx, dy, dz, 0, -1);
            }
            if (voisine) {                                                                    // soufflet (toile, passerelle)
                float x0 = s > 0 ? L : -SOUF_X1, x1 = s > 0 ? SOUF_X1 : -L;
                for (int c = -1; c <= 1; c += 2) {
                    t = entree(a, dx, dy, dz, x0, SOUF_Y0, c > 0 ? SOUF_W - 0.12 : -SOUF_W, x1, SOUF_Y1, c > 0 ? SOUF_W : -SOUF_W + 0.12, false);
                    if (!Double.isNaN(t) && t < best) {
                        best = t;
                        res = impact(t, TOLE, a, dx, dy, dz, 0, -1);
                    }
                }
                t = entree(a, dx, dy, dz, x0, SOUF_Y1 - 0.12, -SOUF_W, x1, SOUF_Y1, SOUF_W, false);
                if (!Double.isNaN(t) && t < best) {
                    best = t;
                    res = impact(t, TOLE, a, dx, dy, dz, 0, -1);
                }
                t = entree(a, dx, dy, dz, x0, SOUF_Y0, -SOUF_W, x1, Reseau.PLANCHER, SOUF_W, false);
                if (!Double.isNaN(t) && t < best) {
                    best = t;
                    res = impact(t, TOLE, a, dx, dy, dz, 0, -1);
                }
            }
        }
        return res;
    }

    /** Le point d'entrée (fraction t) est-il dans le passage d'une porte intérieure (|lz| < BOUT, sous le linteau) ? */
    private static boolean passage(double[] a, double dy, double dz, double t) {
        double ey = a[1] + dy * t, ez = a[2] + dz * t;
        return Math.abs(ez) < Voyageurs.BOUT && ey < PASSAGE_Y1;
    }

    private static Impact impact(double t, int nature, double[] a, double dx, double dy, double dz, int cote, int baie) {
        return new Impact(t, nature, a[0] + dx * t, a[1] + dy * t, a[2] + dz * t, cote, baie);
    }

    /**
     * Entrée du trajet a + t·d (t de 0 à 1) dans la boîte : t, ou NaN si le trajet ne la touche pas ou s'il part de
     * dedans (sauf avecDedans : alors 0, pour le tri rapide).
     */
    private static double entree(double[] a, double dx, double dy, double dz, double x0, double y0, double z0, double x1, double y1,
                                 double z1, boolean avecDedans) {
        double tmin = 0, tmax = 1;
        double[] d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        boolean dedans = true;
        for (int i = 0; i < 3; i++) {
            if (a[i] < lo[i] || a[i] > hi[i]) dedans = false;
            if (Math.abs(d[i]) < 1e-12) {
                if (a[i] < lo[i] || a[i] > hi[i]) return Double.NaN;
                continue;
            }
            double t1 = (lo[i] - a[i]) / d[i], t2 = (hi[i] - a[i]) / d[i];
            if (t1 > t2) {
                double x = t1;
                t1 = t2;
                t2 = x;
            }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return Double.NaN;
        }
        if (dedans) return avecDedans ? 0 : Double.NaN;
        return tmin;
    }
}
