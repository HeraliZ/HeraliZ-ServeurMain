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
 */
public final class Niveaux {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX = 45;

    private static long[] xp = defautXp();

    private Niveaux() {
    }

    /** XP pour passer du niveau n (1..MAX-1) au suivant ; 0 au niveau max. */
    public static long xpPour(int niveau) {
        if (niveau < 1 || niveau >= MAX) return 0;
        return xp[niveau - 1];
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
        o.addProperty("_aide", "index = niveau - 1 ; xp_suivant = XP pour passer au niveau suivant (le dernier est ignore) ;"
                + " membres et territoires : arbre d'ameliorations du clan");
        o.add("xp_suivant", ax);
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
            LOG.info("[clans] niveaux.json charge");
        } catch (IOException | RuntimeException ex) {
            LOG.error("[clans] niveaux.json illisible, valeurs par defaut gardees", ex);
            xp = defautXp();
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
