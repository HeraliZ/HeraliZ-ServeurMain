package fr.heralisz.clans.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Table des niveaux : XP necessaire pour passer du niveau n au suivant. Depuis la saison des clans (22/10), le niveau
 * maximum est celui qui permet de tout prendre dans l'arbre ({@link Arbre#pointsTotaux()} = 45) et chaque niveau est
 * plus dur que le precedent. Membres et territoires ne viennent pas du niveau mais de l'arbre ({@link Arbre}). Valeurs
 * surchargees par config/heralisz_clans/niveaux.json (ecrit s'il manque, relu par /clan admin recharger).
 * <p>
 * 31/10 (demande du proprietaire : « beaucoup trop facile des le debut », et pas 250 XP pour le niveau 2, moins qu'une
 * seule quete du jour) : nouvelle courbe {@code arrondi100(3 600 x (1 + 0,065 (n - 1)))} : 3 600 XP pour le niveau 2
 * (2 a 3 jours d'un petit clan normal, environ 1 500 XP par jour avant les captures), puis chaque niveau un peu plus cher
 * jusqu'a 13 700 XP pour le dernier (~380 000 XP en tout). Ancienne courbe : 20 x n^1,5 x 3 (60 XP pour le niveau 2). Les
 * clans d'avant gardent leur XP totale (recalcul une fois au demarrage : {@code MigrationNiveaux} du serveur). Les
 * plafonds par source et par jour et le bareme des dons sont dans le meme fichier, lus par le serveur seul.
 * <p>
 * Deux facteurs restent possibles dans niveaux.json, appliques par-dessus la table : {@code facteur_xp} (XP demandee par
 * niveau, 1 depuis le 31/10 : la table est deja la bonne) et {@code facteur_prix_arbre} (herals des paliers de l'arbre, 2
 * par defaut, envoye aux joueurs avec l'etat du clan).
 * <p>
 * Section {@code membres} (31/10, decision du proprietaire) : membres au depart (4) et, pour chaque palier de la branche
 * Membres de l'arbre, le niveau du clan demande (2, 5, 10, 15 : 5 membres au niveau 2, 6 au niveau 5, 7 au niveau 10, 8
 * au niveau 15) et ses herals avant le facteur de prix ({@link Arbre#reglerMembres}). Ajoutee au fichier si elle manque.
 */
public final class Niveaux {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX = 45;
    /** Facteurs par defaut : XP x1 (31/10, la table est deja la bonne ; 28/10 : x3), prix de l'arbre x2 (28/10). */
    public static final double FACTEUR_XP_DEFAUT = 1.0, FACTEUR_PRIX_ARBRE_DEFAUT = 2.0;
    /** Courbe par defaut (31/10) : XP du premier niveau, hausse de chaque niveau suivant (part du premier). */
    public static final long XP_NIVEAU_2 = 3_600L;
    public static final double HAUSSE_PAR_NIVEAU = 0.065;

    private static long[] xp = defautXp();
    private static double facteurXp = FACTEUR_XP_DEFAUT;

    private Niveaux() {
    }

    /** XP pour passer du niveau n (1..MAX-1) au suivant ({@code xp_suivant} x {@code facteur_xp}) ; 0 au niveau max. */
    public static long xpPour(int niveau) {
        if (niveau < 1 || niveau >= MAX) return 0;
        return Math.max(1, Math.round(xp[niveau - 1] * facteurXp));
    }

    /** Facteur applique a l'XP de chaque niveau (niveaux.json, {@code facteur_xp}). */
    public static double facteurXp() {
        return facteurXp;
    }

    /** XP totale cumulee pour atteindre le niveau donne (somme des paliers precedents). */
    public static long xpCumulee(int niveau) {
        long total = 0;
        for (int n = 1; n < borne(niveau); n++) total += xpPour(n);
        return total;
    }

    private static int borne(int niveau) {
        return Math.max(1, Math.min(MAX, niveau));
    }

    /** arrondi100(3 600 x (1 + 0,065 (n - 1))) : 3 600, 3 800, 4 100 ... 13 700 (31/10). */
    public static long[] defautXp() {
        long[] t = new long[MAX];
        for (int n = 1; n <= MAX; n++) {
            double v = XP_NIVEAU_2 * (1 + HAUSSE_PAR_NIVEAU * (n - 1));
            t[n - 1] = Math.round(v / 100.0) * 100;
        }
        return t;
    }

    /** JSON de la table courante (lisible, un tableau par propriete, index = niveau - 1). */
    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonArray ax = new JsonArray();
        for (int i = 0; i < MAX; i++) ax.add(xp[i]);
        o.addProperty("_aide", "index = niveau - 1 ; xp_suivant = XP de base pour passer au niveau suivant (le dernier est ignore),"
                + " multipliee par facteur_xp ; facteur_prix_arbre multiplie les herals des paliers de l'arbre du clan ;"
                + " membres et territoires : arbre d'ameliorations du clan (membres : section membres) ; plafonds_jour et"
                + " dons : lus par le serveur (XpClan), relus par /clan admin recharger");
        o.addProperty("facteur_xp", facteurXp);
        o.addProperty("facteur_prix_arbre", Arbre.facteurPrixPct() / 100.0);
        o.add("xp_suivant", ax);
        o.add("membres", membresJson());
        return o;
    }

    /** Section « membres » : reglages en cours de la branche Membres de l'arbre. */
    public static JsonObject membresJson() {
        JsonObject m = new JsonObject();
        m.addProperty("_aide", "Membres au plus d'un clan : depart, puis +1 par palier de l'amelioration Membres (arbre, onglet"
                + " Niveau), achete par le chef avec des points et des herals ; niveaux_paliers = niveau du clan demande par"
                + " palier (31/10 : 5 membres au niveau 2, 6 au 5, 7 au 10, 8 au 15) ; herals_paliers = prix avant"
                + " facteur_prix_arbre. 4 paliers, 8 membres au plus avec le depart a 4. Un clan deja au-dessus de son"
                + " maximum garde ses membres mais n'invite plus personne.");
        m.addProperty("depart", Arbre.membresDepart());
        JsonArray nv = new JsonArray();
        for (int v : Arbre.niveauxMembres()) nv.add(v);
        m.add("niveaux_paliers", nv);
        JsonArray hm = new JsonArray();
        for (long v : Arbre.heralsMembres()) hm.add(v);
        m.add("herals_paliers", hm);
        return m;
    }

    /** Lit la section « membres » (absente ou illisible : valeurs par defaut) ; vrai si elle etait absente. */
    private static boolean lireMembres(JsonObject o) {
        if (!o.has("membres") || !o.get("membres").isJsonObject()) {
            Arbre.reglerMembres(Arbre.MEMBRES_DEPART, Arbre.NIVEAUX_MEMBRES, Arbre.HERALS_MEMBRES);
            return true;
        }
        try {
            JsonObject m = o.getAsJsonObject("membres");
            int depart = Json.integer(m, "depart", Arbre.MEMBRES_DEPART);
            JsonArray a = Json.arr(m, "niveaux_paliers");
            int[] nv = new int[a.size()];
            for (int i = 0; i < nv.length; i++) nv[i] = a.get(i).getAsInt();
            JsonArray h = Json.arr(m, "herals_paliers");
            long[] hm = new long[h.size()];
            for (int i = 0; i < hm.length; i++) hm[i] = h.get(i).getAsLong();
            Arbre.reglerMembres(depart, nv.length == 0 ? null : nv, hm.length == 0 ? null : hm);
        } catch (RuntimeException ex) {
            LOG.error("[clans] niveaux.json : section « membres » illisible, paliers par defaut", ex);
            Arbre.reglerMembres(Arbre.MEMBRES_DEPART, Arbre.NIVEAUX_MEMBRES, Arbre.HERALS_MEMBRES);
        }
        return false;
    }

    /** Facteur lu dans le fichier, borne (0,1 a 100) ; absent ou illisible : valeur par defaut. */
    private static double facteur(JsonObject o, String cle, double def) {
        try {
            if (o.has(cle) && o.get(cle).isJsonPrimitive()) {
                double v = o.get(cle).getAsDouble();
                if (Double.isFinite(v)) return Math.max(0.1, Math.min(100, v));
            }
        } catch (RuntimeException ignore) {
            // valeur par defaut
        }
        return def;
    }

    /** Charge niveaux.json dans le dossier de config donne ; l'ecrit avec les valeurs par defaut s'il manque. */
    public static void charger(Path dossierConfig) {
        Path f = dossierConfig.resolve("niveaux.json");
        try {
            if (!Files.exists(f)) {
                xp = defautXp();
                facteurXp = FACTEUR_XP_DEFAUT;
                Arbre.facteurPrixPct((int) Math.round(FACTEUR_PRIX_ARBRE_DEFAUT * 100));
                Arbre.reglerMembres(Arbre.MEMBRES_DEPART, Arbre.NIVEAUX_MEMBRES, Arbre.HERALS_MEMBRES);
                Files.createDirectories(dossierConfig);
                Files.writeString(f, GSON.toJson(toJson()), StandardCharsets.UTF_8);
                LOG.info("[clans] niveaux.json ecrit avec les valeurs par defaut");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            xp = lireLongs(o, "xp_suivant", defautXp());
            facteurXp = facteur(o, "facteur_xp", FACTEUR_XP_DEFAUT);
            Arbre.facteurPrixPct((int) Math.round(facteur(o, "facteur_prix_arbre", FACTEUR_PRIX_ARBRE_DEFAUT) * 100));
            if (lireMembres(o)) {
                // fichier d'avant le 31/10 : la section est ajoutee avec les valeurs par defaut, le reste n'est pas touche
                o.add("membres", membresJson());
                try {
                    Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
                    LOG.info("[clans] niveaux.json : section « membres » ajoutee (valeurs par defaut)");
                } catch (IOException ex) {
                    LOG.warn("[clans] niveaux.json : ajout de la section « membres » impossible ({})", ex.toString());
                }
            }
            LOG.info("[clans] niveaux.json charge (XP x{}, prix de l'arbre x{}, membres {} puis paliers aux niveaux {})", facteurXp,
                    Arbre.facteurPrixPct() / 100.0, Arbre.membresDepart(), java.util.Arrays.toString(Arbre.niveauxMembres()));
        } catch (IOException | RuntimeException ex) {
            LOG.error("[clans] niveaux.json illisible, valeurs par defaut gardees", ex);
            xp = defautXp();
            facteurXp = FACTEUR_XP_DEFAUT;
            Arbre.facteurPrixPct((int) Math.round(FACTEUR_PRIX_ARBRE_DEFAUT * 100));
            Arbre.reglerMembres(Arbre.MEMBRES_DEPART, Arbre.NIVEAUX_MEMBRES, Arbre.HERALS_MEMBRES);
        }
    }

    private static long[] lireLongs(JsonObject o, String cle, long[] def) {
        JsonArray a = Json.arr(o, cle);
        if (a.size() == 0) return def;
        long[] t = def.clone();
        for (int i = 0; i < Math.min(MAX, a.size()); i++) {
            JsonElement e = a.get(i);
            if (e.isJsonPrimitive()) t[i] = Math.max(0, e.getAsLong());
        }
        return t;
    }

}
