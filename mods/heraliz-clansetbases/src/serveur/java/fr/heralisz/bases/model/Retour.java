package fr.heralisz.bases.model;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

/**
 * Position d'ou un joueur est entre dans une base (retour a la sortie, BASES_SPEC §3). {@code dim} = id de la
 * dimension (« minecraft:overworld »).
 */
public record Retour(String dim, double x, double y, double z, float yaw, float pitch) {
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("dim", dim);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("yaw", yaw);
        o.addProperty("pitch", pitch);
        return o;
    }

    @Nullable
    public static Retour fromJson(JsonObject o) {
        String dim = Json.str(o, "dim", null);
        if (dim == null) return null;
        return new Retour(dim, Json.d(o, "x", 0), Json.d(o, "y", 64), Json.d(o, "z", 0), (float) Json.d(o, "yaw", 0),
                (float) Json.d(o, "pitch", 0));
    }
}
