package fr.heralisz.bases.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.UUID;

/** Lectures JSON tolerantes (champ absent ou invalide = valeur par defaut, jamais d'exception). */
public final class Json {
    private Json() {
    }

    public static String str(JsonObject o, String k, String def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsString();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static long lng(JsonObject o, String k, long def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsLong();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static int i(JsonObject o, String k, int def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsInt();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static double d(JsonObject o, String k, double def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsDouble();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    public static boolean b(JsonObject o, String k, boolean def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsBoolean();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    @Nullable
    public static UUID uuid(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? null : UUID.fromString(e.getAsString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    @Nullable
    public static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
