package fr.heralisz.clans.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Petites aides de lecture Gson tolerantes : une cle absente ou mal typee rend la valeur par defaut (jamais
 * d'exception), pour que clans.json survive aux versions successives du mod.
 */
public final class Json {
    private Json() {
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public static long lng(JsonObject o, String key, long def) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public static int integer(JsonObject o, String key, int def) {
        return (int) lng(o, key, def);
    }

    public static double dbl(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
    }

    @Nullable
    public static UUID uuid(JsonObject o, String key) {
        String s = str(o, key, null);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    public static <E extends Enum<E>> E enumOf(JsonObject o, String key, Class<E> type, E def) {
        String s = str(o, key, null);
        if (s == null) return def;
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    /** Couleur "#RRGGBB" (ecriture) — lecture tolerante : "#RRGGBB", "RRGGBB" ou entier. */
    public static String couleur(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static int couleur(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt() & 0xFFFFFF;
        String s = e.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return (int) Long.parseLong(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
