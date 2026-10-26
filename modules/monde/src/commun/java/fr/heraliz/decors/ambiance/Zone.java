package fr.heraliz.decors.ambiance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Zone dangereuse : un polygone (x, z), une plage de hauteurs, un genre (gaz, radiation, froid), une intensité et une
 * bordure où l'effet monte progressivement (on sent qu'on y entre). Écrites par la map dans le monde
 * (data/heraliz_zones.json) : aucune zone sur un monde qui n'en a pas.
 */
public record Zone(String nom, String genre, float[] xs, float[] zs, float ymin, float ymax, float intensite, float bord) {

    /** 1 au cœur, 0 dehors, entre les deux dans la bordure (distance au bord / largeur). */
    public float niveau(double x, double y, double z) {
        if (y < ymin - 4 || y > ymax + 4) return 0f;
        boolean dedans = contient(x, z);
        double d = distanceBord(x, z);
        if (dedans) return (float) Math.min(1.0, 0.5 + d / Math.max(1, bord) * 0.5) * intensite;
        return d < bord ? (float) ((1 - d / bord) * 0.5) * intensite : 0f;
    }

    public boolean contient(double x, double z) {
        boolean c = false;
        int n = xs.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((zs[i] > z) != (zs[j] > z) && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) c = !c;
        }
        return c;
    }

    public double distanceBord(double x, double z) {
        double best = Double.MAX_VALUE;
        int n = xs.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
            double dx = bx - ax, dz = bz - az;
            double l2 = dx * dx + dz * dz;
            double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2)) : 0;
            double px = ax + t * dx - x, pz = az + t * dz - z;
            best = Math.min(best, px * px + pz * pz);
        }
        return Math.sqrt(best);
    }

    void ecrire(FriendlyByteBuf b) {
        b.writeUtf(nom);
        b.writeUtf(genre);
        b.writeVarInt(xs.length);
        for (int i = 0; i < xs.length; i++) {
            b.writeFloat(xs[i]);
            b.writeFloat(zs[i]);
        }
        b.writeFloat(ymin);
        b.writeFloat(ymax);
        b.writeFloat(intensite);
        b.writeFloat(bord);
    }

    static Zone lire(FriendlyByteBuf b) {
        String nom = b.readUtf(), genre = b.readUtf();
        int n = b.readVarInt();
        float[] xs = new float[n], zs = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = b.readFloat();
            zs[i] = b.readFloat();
        }
        return new Zone(nom, genre, xs, zs, b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    /** {"zones": [{"nom", "genre", "points": [[x, z], ...], "ymin", "ymax", "intensite", "bord"}]} */
    public static List<Zone> lireJson(JsonObject o) {
        List<Zone> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("zones")) {
            JsonObject z = e.getAsJsonObject();
            JsonArray pts = z.getAsJsonArray("points");
            float[] xs = new float[pts.size()], zs = new float[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                xs[i] = pts.get(i).getAsJsonArray().get(0).getAsFloat();
                zs[i] = pts.get(i).getAsJsonArray().get(1).getAsFloat();
            }
            out.add(new Zone(z.get("nom").getAsString(), z.get("genre").getAsString(), xs, zs,
                    z.has("ymin") ? z.get("ymin").getAsFloat() : -64, z.has("ymax") ? z.get("ymax").getAsFloat() : 320,
                    z.has("intensite") ? z.get("intensite").getAsFloat() : 1f, z.has("bord") ? z.get("bord").getAsFloat() : 24f));
        }
        return out;
    }
}
