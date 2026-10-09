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
 * Table des niveaux : XP necessaire pour passer du niveau n au suivant, membres max et territoires max par niveau.
 * Valeurs par defaut du cahier des charges, surchargees par config/heralisz_clans/niveaux.json (ecrit s'il manque).
 * Le client recoit les limites utiles dans le snapshot : cette classe sert surtout au serveur.
 */
public final class Niveaux {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX = 20;

    private static long[] xp = defautXp();
    private static int[] membres = defautMembres();
    private static int[] territoires = defautTerritoires();

    private Niveaux() {
    }

    /** XP pour passer du niveau n (1..19) au suivant ; 0 au niveau max. */
    public static long xpPour(int niveau) {
        if (niveau < 1 || niveau >= MAX) return 0;
        return xp[niveau - 1];
    }

    public static int membresMax(int niveau) {
        return membres[borne(niveau) - 1];
    }

    public static int territoiresMax(int niveau) {
        return territoires[borne(niveau) - 1];
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

    /** arrondi50(400 * n^1.35). */
    static long[] defautXp() {
        long[] t = new long[MAX];
        for (int n = 1; n <= MAX; n++) {
            double v = 400 * Math.pow(n, 1.35);
            t[n - 1] = Math.round(v / 50.0) * 50;
        }
        return t;
    }

    /** 6 au niveau 1, +1 par niveau, plafonne a 20. */
    static int[] defautMembres() {
        int[] t = new int[MAX];
        for (int n = 1; n <= MAX; n++) t[n - 1] = Math.min(20, 5 + n);
        return t;
    }

    /** 1 (1-2), 2 (3-5), 3 (6+) : un clan ne tient jamais plus de 3 zones (choix du proprietaire). */
    static int[] defautTerritoires() {
        int[] t = new int[MAX];
        for (int n = 1; n <= MAX; n++) t[n - 1] = n <= 2 ? 1 : n <= 5 ? 2 : 3;
        return t;
    }

    /** JSON de la table courante (lisible, un tableau par propriete, index = niveau - 1). */
    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonArray ax = new JsonArray(), am = new JsonArray(), at = new JsonArray();
        for (int i = 0; i < MAX; i++) {
            ax.add(xp[i]);
            am.add(membres[i]);
            at.add(territoires[i]);
        }
        o.addProperty("_aide", "index = niveau - 1 ; xp_suivant = XP pour passer au niveau suivant (le dernier est ignore)");
        o.add("xp_suivant", ax);
        o.add("membres_max", am);
        o.add("territoires_max", at);
        return o;
    }

    /** Charge niveaux.json dans le dossier de config donne ; l'ecrit avec les valeurs par defaut s'il manque. */
    public static void charger(Path dossierConfig) {
        Path f = dossierConfig.resolve("niveaux.json");
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(dossierConfig);
                Files.writeString(f, GSON.toJson(toJson()), StandardCharsets.UTF_8);
                LOG.info("[clans] niveaux.json ecrit avec les valeurs par defaut");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            xp = lireLongs(o, "xp_suivant", defautXp());
            membres = lireInts(o, "membres_max", defautMembres());
            territoires = lireInts(o, "territoires_max", defautTerritoires());
            LOG.info("[clans] niveaux.json charge");
        } catch (IOException | RuntimeException ex) {
            LOG.error("[clans] niveaux.json illisible, valeurs par defaut gardees", ex);
            xp = defautXp();
            membres = defautMembres();
            territoires = defautTerritoires();
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

    private static int[] lireInts(JsonObject o, String cle, int[] def) {
        JsonArray a = Json.arr(o, cle);
        if (a.size() == 0) return def;
        int[] t = def.clone();
        for (int i = 0; i < Math.min(MAX, a.size()); i++) {
            JsonElement e = a.get(i);
            if (e.isJsonPrimitive()) t[i] = Math.max(1, e.getAsInt());
        }
        return t;
    }
}
