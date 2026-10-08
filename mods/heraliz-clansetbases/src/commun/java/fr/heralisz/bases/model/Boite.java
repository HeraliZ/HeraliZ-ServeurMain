package fr.heralisz.bases.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Boite de blocs entiere, bornes INCLUSES, en coordonnees relatives au coin de la structure du bunker (sauf mention
 * contraire). Lue avec tolerance depuis bunker.json : {@code [x0,y0,z0,x1,y1,z1]}, {@code [[x0,y0,z0],[x1,y1,z1]]},
 * {@code {"de":[..],"a":[..]}}, {@code {"min":[..],"max":[..]}}, {@code {"from":[..],"to":[..]}} ou
 * {@code {"x0":..,"y0":..,"z0":..,"x1":..,"y1":..,"z1":..}} ; les bornes sont remises dans l'ordre.
 */
public record Boite(int x0, int y0, int z0, int x1, int y1, int z1) {
    public Boite {
        int a = Math.min(x0, x1), b = Math.max(x0, x1);
        x0 = a;
        x1 = b;
        a = Math.min(y0, y1);
        b = Math.max(y0, y1);
        y0 = a;
        y1 = b;
        a = Math.min(z0, z1);
        b = Math.max(z0, z1);
        z0 = a;
        z1 = b;
    }

    public boolean contient(int x, int y, int z) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
    }

    /** Nombre de blocs. */
    public long volume() {
        return (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
    }

    public int dx() {
        return x1 - x0 + 1;
    }

    public int dy() {
        return y1 - y0 + 1;
    }

    public int dz() {
        return z1 - z0 + 1;
    }

    public Boite decalee(int ox, int oy, int oz) {
        return new Boite(x0 + ox, y0 + oy, z0 + oz, x1 + ox, y1 + oy, z1 + oz);
    }

    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        a.add(x0);
        a.add(y0);
        a.add(z0);
        a.add(x1);
        a.add(y1);
        a.add(z1);
        return a;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(x0);
        b.writeVarInt(y0);
        b.writeVarInt(z0);
        b.writeVarInt(x1);
        b.writeVarInt(y1);
        b.writeVarInt(z1);
    }

    public static Boite read(FriendlyByteBuf b) {
        return new Boite(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    // ------------------------------------------------------------------ lecture tolerante
    @Nullable
    private static int[] triple(JsonElement e) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return null;
        JsonArray a = e.getAsJsonArray();
        return new int[]{(int) Math.floor(a.get(0).getAsDouble()), (int) Math.floor(a.get(1).getAsDouble()),
                (int) Math.floor(a.get(2).getAsDouble())};
    }

    /** Une boite, ou null si l'element n'en decrit pas une. */
    @Nullable
    public static Boite lire(JsonElement e) {
        try {
            if (e == null || e.isJsonNull()) return null;
            if (e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                if (a.size() == 6 && a.get(0).isJsonPrimitive()) {
                    int[] v = new int[6];
                    for (int i = 0; i < 6; i++) v[i] = (int) Math.floor(a.get(i).getAsDouble());
                    return new Boite(v[0], v[1], v[2], v[3], v[4], v[5]);
                }
                // [[x0,y0,z0],[x1,y1,z1]] : deux TRIPLETS exactement (deux boites de 6 nombres = une liste de boites)
                if (a.size() == 2 && a.get(0).isJsonArray() && a.get(0).getAsJsonArray().size() == 3
                        && a.get(1).isJsonArray() && a.get(1).getAsJsonArray().size() == 3) {
                    int[] p = triple(a.get(0)), q = triple(a.get(1));
                    return p == null || q == null ? null : new Boite(p[0], p[1], p[2], q[0], q[1], q[2]);
                }
                return null;
            }
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                String[][] paires = {{"de", "a"}, {"min", "max"}, {"from", "to"}, {"debut", "fin"}};
                for (String[] k : paires) {
                    if (o.has(k[0]) && o.has(k[1])) {
                        int[] p = triple(o.get(k[0])), q = triple(o.get(k[1]));
                        if (p != null && q != null) return new Boite(p[0], p[1], p[2], q[0], q[1], q[2]);
                    }
                }
                if (o.has("x0") && o.has("x1")) {
                    return new Boite(Json.i(o, "x0", 0), Json.i(o, "y0", 0), Json.i(o, "z0", 0), Json.i(o, "x1", 0),
                            Json.i(o, "y1", 0), Json.i(o, "z1", 0));
                }
                // {"aabb": ...} ou {"boite": ...}
                for (String k : new String[]{"aabb", "boite", "box"}) {
                    if (o.has(k)) return lire(o.get(k));
                }
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }

    /** Une liste de boites : tableau de boites, ou une boite seule. Les elements illisibles sont ignores. */
    public static List<Boite> liste(JsonElement e) {
        List<Boite> l = new ArrayList<>();
        if (e == null || e.isJsonNull()) return l;
        Boite seule = lire(e);
        if (seule != null) {
            l.add(seule);
            return l;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                Boite b = lire(x);
                if (b != null) l.add(b);
            }
        } else if (e.isJsonObject()) {
            // objet nomme {"nord": boite, "sud": boite} : toutes ses valeurs
            for (var x : e.getAsJsonObject().entrySet()) l.addAll(liste(x.getValue()));
        }
        return l;
    }

    public static boolean dans(List<Boite> boites, int x, int y, int z) {
        for (int i = 0, n = boites.size(); i < n; i++) {
            if (boites.get(i).contient(x, y, z)) return true;
        }
        return false;
    }
}
