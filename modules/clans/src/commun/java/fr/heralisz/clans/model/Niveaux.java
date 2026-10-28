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
 * plus dur que le precedent : 20 x n^1,5 (arrondi a 10 : 20 XP pour le 1er, ~5 800 pour le dernier, ~106 000 en tout,
 * soit 2 a 3 semaines pour un clan tres actif d'apres les gains actuels ; a regler pendant la beta). Membres et
 * territoires ne viennent plus du niveau mais de l'arbre ({@link Arbre}). Valeurs surchargees par
 * config/heralisz_clans/niveaux.json (ecrit s'il manque).
 * <p>
 * 28/10 (demande du proprietaire : « beaucoup trop facile ») : deux facteurs dans niveaux.json, appliques par-dessus la
 * table : {@code facteur_xp} (XP demandee par niveau, 3 par defaut) et {@code facteur_prix_arbre} (herals des paliers de
 * l'arbre, 2 par defaut, envoye aux joueurs avec l'etat du clan). Le serveur seul les lit dans le fichier.
 */
public final class Niveaux {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX = 45;
    /** Facteurs par defaut (28/10) : XP x3, prix de l'arbre x2. */
    public static final double FACTEUR_XP_DEFAUT = 3.0, FACTEUR_PRIX_ARBRE_DEFAUT = 2.0;

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

    /** arrondi10(20 * n^1.5). */
    static long[] defautXp() {
        long[] t = new long[MAX];
        for (int n = 1; n <= MAX; n++) {
            double v = 20 * Math.pow(n, 1.5);
            t[n - 1] = Math.round(v / 10.0) * 10;
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
                + " membres et territoires : arbre d'ameliorations du clan");
        o.addProperty("facteur_xp", facteurXp);
        o.addProperty("facteur_prix_arbre", Arbre.facteurPrixPct() / 100.0);
        o.add("xp_suivant", ax);
        return o;
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
                Files.createDirectories(dossierConfig);
                Files.writeString(f, GSON.toJson(toJson()), StandardCharsets.UTF_8);
                LOG.info("[clans] niveaux.json ecrit avec les valeurs par defaut");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            xp = lireLongs(o, "xp_suivant", defautXp());
            facteurXp = facteur(o, "facteur_xp", FACTEUR_XP_DEFAUT);
            Arbre.facteurPrixPct((int) Math.round(facteur(o, "facteur_prix_arbre", FACTEUR_PRIX_ARBRE_DEFAUT) * 100));
            LOG.info("[clans] niveaux.json charge (XP x{}, prix de l'arbre x{})", facteurXp, Arbre.facteurPrixPct() / 100.0);
        } catch (IOException | RuntimeException ex) {
            LOG.error("[clans] niveaux.json illisible, valeurs par defaut gardees", ex);
            xp = defautXp();
            facteurXp = FACTEUR_XP_DEFAUT;
            Arbre.facteurPrixPct((int) Math.round(FACTEUR_PRIX_ARBRE_DEFAUT * 100));
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
