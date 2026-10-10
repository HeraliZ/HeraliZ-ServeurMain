package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Les voyageurs : marche dans les voitures (collisions avec les murs, les banquettes, les tablettes, les cloisons),
 * portes d'intercirculation et de cloison qui s'ouvrent quand quelqu'un s'en approche, passage d'une voiture à
 * l'autre par le soufflet, montée par une porte ouverte en gare (on entre en marchant), descente de même, et les
 * joueurs restés sur la voie : poussés hors du gabarit à l'arrêt, percutés en marche. Coordonnées des voitures :
 * celles de tools/gen_train.py (lx le long de la voiture, +lx vers l'avant, lz en travers, +lz à droite).
 */
public final class Voyageurs {
    // Jar fusionné : ici la géométrie des voitures, les collisions et les portes (lus des deux côtés) ; la marche côté
    // client est dans VoyageursClient (src/client), la logique serveur (montée, descentes, heurts) dans VoyageursServeur.
    static final float VL = 11.1f, VW = 2.15f, VWI = 2.07f, VEST = 7.3f, PX0 = 8.7f, PX1 = 10.5f, BOUT = 0.75f;
    static final float SIEGE_Z = 1.17f, TABLE_Z = VWI - 0.48f, ASSIS_Z = SIEGE_Z + 0.45f, DEBOUT_Z = SIEGE_Z - 0.29f;
    static final float PASSERELLE = 0.7f;           // soufflet de chaque voiture (le suivant commence au milieu)
    static final float R = 0.24f;                  // demi-largeur d'un voyageur
    static final float[] FEN_X = {-6, -4, -2, 0, 2, 4, 6};
    static final double MARCHE = 0.2158, COURSE = 0.28;
    /** Objets des vestibules (x0, z0, x1, z1 : samovar, poubelle, extincteur), lus dans train/voitures.json. */
    static float[][] OBJETS = new float[0][];
    /** Par modèle de voiture (train/voitures.json, écrit par tools/gen_train.py) : banquettes ou non, obstacles en plus
     *  (caisses du fourgon). */
    private static final java.util.Map<String, float[][]> OBSTACLES = new java.util.HashMap<>();
    private static final java.util.Set<String> SANS_SIEGES = new java.util.HashSet<>();

    static {
        try (var in = Voyageurs.class.getResourceAsStream("/assets/heralisz_utils/train/voitures.json")) {
            if (in != null) {
                var o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("voitures");
                for (var en : o.entrySet()) {
                    var v = en.getValue().getAsJsonObject();
                    if (!"voyageurs".equals(v.get("genre").getAsString())) SANS_SIEGES.add(en.getKey());
                    var a = v.getAsJsonArray("obstacles");
                    float[][] t = new float[a.size()][4];
                    for (int i = 0; i < a.size(); i++)
                        for (int j = 0; j < 4; j++) t[i][j] = a.get(i).getAsJsonArray().get(j).getAsFloat();
                    OBSTACLES.put(en.getKey(), t);
                }
                var racine = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                        Voyageurs.class.getResourceAsStream("/assets/heralisz_utils/train/voitures.json"), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (racine.has("objets")) {
                    var a = racine.getAsJsonArray("objets");
                    float[][] t = new float[a.size()][4];
                    for (int i = 0; i < a.size(); i++)
                        for (int j = 0; j < 4; j++) t[i][j] = a.get(i).getAsJsonArray().get(j).getAsFloat();
                    OBJETS = t;
                }
                // cotes du modèle (tools/gen_train.py) et du mod : les mêmes, sinon on marche dans les murs
                if (racine.has("vest") && (Math.abs(racine.get("vest").getAsFloat() - VEST) > 1e-3
                        || Math.abs(racine.get("bout_l").getAsFloat() - BOUT) > 1e-3
                        || Math.abs(racine.getAsJsonArray("porte_x").get(0).getAsFloat() - PX0) > 1e-3
                        || racine.getAsJsonArray("baies").size() != FEN_X.length)) {
                    HeraliszUtils.LOG.error("Train : cotes du modèle (voitures.json) différentes de celles du mod (Voyageurs)");
                }
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.error("Voitures du train : obstacles illisibles", ex);
        }
    }

    static boolean sieges(int veh) {
        return veh >= 0 && veh < Horaire.N * Horaire.RAMES && !SANS_SIEGES.contains(Horaire.MODELES[Horaire.place(veh)]);
    }

    /** Voyageurs vus de chaque côté (le client en a besoin pour ouvrir les portes de tout le monde). */
    static final List<Voyageur> CLIENT = new ArrayList<>(), SERVEUR = new ArrayList<>();

    private Voyageurs() {}

    // ------------------------------------------------------------------------------------------------ portes

    private static float ouverture(List<Voyageur> vs, int veh, float dx) {
        float best = 99f;
        for (Voyageur v : vs) {
            if (v.isRemoved() || v.vehicule() != veh) continue;
            best = Math.min(best, (float) Math.hypot(v.lx() - dx, v.lz()));
        }
        return Math.max(0f, Math.min(1f, (2.1f - best) / 0.7f));
    }

    static boolean voiture(int veh) {
        return veh >= 1 && veh < Horaire.N * Horaire.RAMES && Horaire.RAME[Horaire.place(veh)] == Horaire.Vehicule.VOITURE;
    }

    // ------------------------------------------------------------------------------------------------ portes animées (client)
    private static final float[][] ANIM = new float[Horaire.N * Horaire.RAMES][6];
    /** Secondes avant de refermer (on ne claque pas la porte au nez de celui qui vient de passer). */
    private static final float[][] GARDE = new float[Horaire.N * Horaire.RAMES][6];
    private static long animDernier;

    private static float distance(List<Voyageur> vs, int veh, float dx) {
        float best = 99f;
        for (Voyageur v : vs) {
            if (v.isRemoved() || v.vehicule() != veh) continue;
            best = Math.min(best, (float) Math.hypot(v.lx() - dx, v.lz()));
        }
        return best;
    }

    /** Une porte intérieure doit-elle être ouverte (quelqu'un à moins de 4 blocs) ? Bien plus large que le seuil de
     *  passage (ouvertureDe) : la porte a fini de s'ouvrir quand on arrive dessus, même en courant. */
    private static boolean appel(int veh, int n) {
        return switch (n) {
            case 0 -> voiture(veh - 1) && Math.min(distance(CLIENT, veh, VL), distance(CLIENT, veh - 1, -VL)) < 4.0f;
            case 1 -> voiture(veh + 1) && Math.min(distance(CLIENT, veh, -VL), distance(CLIENT, veh + 1, VL)) < 4.0f;
            case 4 -> distance(CLIENT, veh, VEST) < 4.0f;
            case 5 -> distance(CLIENT, veh, -VEST) < 4.0f;
            default -> false;
        };
    }

    static final float DUREE_PORTE = 0.55f;

    /** Avance l'animation des portes intérieures (une fois par image) : 0,55 s pour s'ouvrir ou se fermer, refermée
     *  une demi-seconde après le départ du dernier voyageur. */
    public static void animer() {
        long t = System.nanoTime();
        float dt = animDernier == 0 ? 0f : Math.min(0.1f, (t - animDernier) / 1e9f);
        animDernier = t;
        for (int veh = 1; veh < Horaire.N * Horaire.RAMES; veh++) {
            for (int n : new int[]{0, 1, 4, 5}) {
                GARDE[veh][n] = appel(veh, n) ? 0.5f : Math.max(0f, GARDE[veh][n] - dt);
                float cible = GARDE[veh][n] > 0 ? 1f : 0f;
                ANIM[veh][n] += Math.max(-dt / DUREE_PORTE, Math.min(dt / DUREE_PORTE, cible - ANIM[veh][n]));
            }
        }
    }

    /** Courbe d'ouverture : départ et arrivée tout en douceur (dérivées première et seconde nulles aux bouts). */
    static float lisse(float o) {
        o = Math.max(0f, Math.min(1f, o));
        return o * o * o * (o * (o * 6f - 15f) + 10f);
    }

    /** Ouverture affichée (0..1, courbe adoucie) d'une porte intérieure. */
    public static float animPorte(int veh, int n) {
        if (veh < 0 || veh >= ANIM.length || n < 0 || n >= 6) return 0f;
        return lisse(ANIM[veh][n]);
    }

    /** Ouverture (0..1) d'une porte : 0 intercirculation avant, 1 arrière, 4 cloison avant, 5 cloison arrière. */
    public static float intercirculation(int veh, int n) {
        return ouvertureDe(CLIENT, veh, n);
    }

    static float ouvertureDe(List<Voyageur> vs, int veh, int n) {
        return switch (n) {
            case 0 -> voiture(veh - 1) ? Math.max(ouverture(vs, veh, VL), ouverture(vs, veh - 1, -VL)) : 0f;
            case 1 -> voiture(veh + 1) ? Math.max(ouverture(vs, veh, -VL), ouverture(vs, veh + 1, VL)) : 0f;
            case 4 -> ouverture(vs, veh, VEST);
            case 5 -> ouverture(vs, veh, -VEST);
            default -> 0f;
        };
    }

    // ------------------------------------------------------------------------------------------------ collisions

    /** Le voyageur (carré de demi-côté R) tient-il en (x, z) dans la voiture ? portesQuai : côté des portes latérales
     *  ouvertes (+1, -1, 0 aucune) ; souples : portes intérieures toujours franchissables (contrôle du serveur). */
    static boolean libre(List<Voyageur> vs, int veh, float x, float z, int portesQuai, boolean souples) {
        float az = Math.abs(z), ax = Math.abs(x);
        boolean seuil = ax >= PX0 + R - 0.02f && ax <= PX1 - R + 0.02f;
        if (az > VWI - R) {                                               // contre un flanc : seulement dans une porte ouverte
            if (!(seuil && portesQuai != 0 && Math.signum(z) == portesQuai && az < VW + 1.2f)) return false;
        }
        if (ax > VL - R) {                                                // bout de la voiture : par la porte d'intercirculation
            boolean avant = x > 0;
            int n = avant ? 0 : 1;
            boolean ouverte = souples ? voiture(avant ? veh - 1 : veh + 1) : ouvertureDe(vs, veh, n) > 0.7f;
            if (!(ouverte && az < BOUT - R && ax < VL + PASSERELLE + 0.6f)) return false;
        }
        if (Math.abs(ax - VEST) < R + 0.04f) {                            // cloisons des vestibules
            int n = x > 0 ? 4 : 5;
            boolean ouverte = souples || ouvertureDe(vs, veh, n) > 0.7f;
            if (!(ouverte && az < BOUT - R)) return false;
        }
        for (float[] o : OBJETS) {                                         // samovar, poubelle, extincteur des vestibules
            if (x > o[0] - R && x < o[2] + R && z > o[1] - R && z < o[3] + R) return false;
        }
        float[][] obs = veh >= 0 && veh < Horaire.N * Horaire.RAMES ? OBSTACLES.get(Horaire.MODELES[Horaire.place(veh)]) : null;
        if (obs != null) {
            for (float[] o : obs) {                                        // caisses du fourgon (de quoi se couvrir)
                if (x > o[0] - R && x < o[2] + R && z > o[1] - R && z < o[3] + R) return false;
            }
        }
        if (sieges(veh) && ax < VEST && az > SIEGE_Z - R) {               // banquettes et tablettes
            for (float f : FEN_X) {
                float d = Math.abs(x - f);
                if (d > 1.06f + R) continue;
                if (d > 0.52f - R) return false;                          // banquettes (dos à dos entre deux baies)
                if (az > TABLE_Z - R && d < 0.32f + R) return false;      // tablette sous la fenêtre
            }
        }
        return true;
    }

    /** Déplace (veh, x, z) de (dx, dz) en coordonnées de voiture, axe par axe ; passe d'une voiture à l'autre au
     *  milieu du soufflet. Renvoie {veh, x, z}. */
    static float[] deplacer(List<Voyageur> vs, int veh, float x, float z, float dx, float dz, int portesQuai, boolean souples) {
        float nx = x + dx;
        if (libre(vs, veh, nx, z, portesQuai, souples)) x = nx;
        float nz = z + dz;
        if (libre(vs, veh, x, nz, portesQuai, souples)) z = nz;
        float lim = VL + PASSERELLE;
        if (x > lim && voiture(veh - 1)) {
            veh -= 1;
            x -= 2 * lim;
        } else if (x < -lim && voiture(veh + 1)) {
            veh += 1;
            x += 2 * lim;
        }
        return new float[]{veh, x, z};
    }

    /** Côté des portes latérales ouvertes pour cet état (+1 droite, -1 gauche, 0 fermées). */
    static int portesQuai(Horaire h, Horaire.Etat e) {
        return e.aQuai() >= 0 && Horaire.portes(e) > 0.8 ? h.reseau.gares[e.aQuai()].cote() : 0;
    }

    // ------------------------------------------------------------------------------------------------ repères

    /** Point local (lx, ly, lz) d'un véhicule placé -> monde. */
    static Vec3 versMonde(Horaire.Placement pl, double lx, double ly, double lz) {
        double cp = Math.cos(pl.pente), sp = Math.sin(pl.pente), cc = Math.cos(pl.cap), sc = Math.sin(pl.cap);
        double a = lx * cp - ly * sp, b = lx * sp + ly * cp;
        return new Vec3(pl.x + a * cc - lz * sc, pl.y + b, pl.z + a * sc + lz * cc);
    }

    /** Point du monde -> repère local du véhicule {lx, ly, lz}. */
    static double[] versLocal(Horaire.Placement pl, Vec3 p) {
        double dx = p.x - pl.x, dy = p.y - pl.y, dz = p.z - pl.z;
        double cc = Math.cos(pl.cap), sc = Math.sin(pl.cap), cp = Math.cos(pl.pente), sp = Math.sin(pl.pente);
        double a = dx * cc + dz * sc, c = -dx * sc + dz * cc;
        return new double[]{a * cp + dy * sp, -a * sp + dy * cp, c};
    }

    /** Milieu de la baie (fenêtre) la plus proche si on y est (|lx - f| < 0,5), sinon NaN. */
    static float baie(float x) {
        for (float f : FEN_X) if (Math.abs(x - f) < 0.95f) return f;
        return Float.NaN;
    }
}
