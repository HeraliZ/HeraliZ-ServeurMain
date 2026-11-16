package fr.heraliz.recolte.serveur;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lieux où aucune ressource n'apparaît, lus tels quels dans les fichiers des autres mods, relus quand ils changent (date
 * relevée à part, {@link DatesFichiers}) :
 * <ul>
 *    <li>les villes : cercles des territoires des clans, {@code config/heralisz_clans/zones.json} (tableau de
 *    {@code {id, x, z, rayon...}} ; la zone d'essai « test » du staff n'est pas une ville), plus leurs abords ;</li>
 *    <li>les zones VIP de HeraliZ Grades, {@code heraliz_grades/zones_vip.json} à la racine du serveur
 *    ({@code {"zones": [{nom, monde, a: [x, y, z], b: [x, y, z]}]}}), celles du monde des ressources, plus une marge.</li>
 * </ul>
 * Un fichier illisible garde les lieux lus avant (une ligne au journal) ; absent : aucun lieu. Fil du serveur seulement.
 */
final class Zones {
    private Zones() {
    }

    @Nullable
    private static Path fichierVilles, fichierVip;
    private static long dateVilles = Long.MIN_VALUE, dateVip = Long.MIN_VALUE;
    private static String mondeVip = "";
    /** Villes : {x, z, rayon}. */
    private static double[][] villes = new double[0][];
    /** Zones VIP du monde des ressources : {minX, minZ, maxX, maxZ}, bornes max exclues. */
    private static int[][] vip = new int[0][];

    /** Relit les fichiers qui ont changé (sinon rien : deux dates comparées). */
    static void rafraichir(MinecraftServer s, Reglages r) {
        if (fichierVilles == null || fichierVip == null) {
            fichierVilles = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("zones.json");
            fichierVip = s.getServerDirectory().toPath().resolve("heraliz_grades").resolve("zones_vip.json");
        }
        long d = DatesFichiers.date(fichierVilles);
        if (d != dateVilles) {
            dateVilles = d;
            lireVilles(fichierVilles, d);
        }
        String monde = r.monde.location().toString();
        long dv = DatesFichiers.date(fichierVip);
        if (dv != dateVip || !monde.equals(mondeVip)) {
            dateVip = dv;
            mondeVip = monde;
            lireVip(fichierVip, dv, monde);
        }
    }

    /** Arrêt du serveur. */
    static void oublier() {
        fichierVilles = null;
        fichierVip = null;
        dateVilles = dateVip = Long.MIN_VALUE;
        mondeVip = "";
        villes = new double[0][];
        vip = new int[0][];
    }

    /** Dans une ville ou à moins de {@code abords} blocs de son bord. */
    static boolean dansUneVille(double x, double z, int abords) {
        for (double[] v : villes) {
            double dx = x - v[0], dz = z - v[1], loin = v[2] + abords;
            if (dx * dx + dz * dz < loin * loin) {
                return true;
            }
        }
        return false;
    }

    /** Dans une zone VIP ou à moins de {@code marge} blocs d'elle. */
    static boolean dansUneZoneVip(int x, int z, int marge) {
        for (int[] b : vip) {
            if (x >= b[0] - marge && x < b[2] + marge && z >= b[1] - marge && z < b[3] + marge) {
                return true;
            }
        }
        return false;
    }

    private static void lireVilles(Path f, long date) {
        if (date == 0L) {
            villes = new double[0][];
            return;
        }
        try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            List<double[]> l = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(in).getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject v = e.getAsJsonObject();
                boolean essai = v.has("id") && v.get("id").isJsonPrimitive() && "test".equals(v.get("id").getAsString());
                if (essai || !v.has("x") || !v.has("z")) {
                    continue;
                }
                l.add(new double[]{v.get("x").getAsDouble(), v.get("z").getAsDouble(), v.has("rayon") ? v.get("rayon").getAsDouble() : 0.0});
            }
            villes = l.toArray(new double[0][]);
            HeralizRecolte.LOG.info("[recolte] {} ville(s) evitee(s) par les ressources ({})", villes.length, f);
        } catch (IOException | RuntimeException ex) {
            HeralizRecolte.LOG.warn("[recolte] villes illisibles ({}) : villes lues avant gardees ({})", f, ex.toString());
        }
    }

    private static void lireVip(Path f, long date, String monde) {
        if (date == 0L) {
            vip = new int[0][];
            return;
        }
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            List<int[]> l = new ArrayList<>();
            JsonArray zones = racine.getAsJsonArray("zones");
            if (zones != null) {
                for (JsonElement el : zones) {
                    JsonObject o = el.getAsJsonObject();
                    if (!monde.equals(o.get("monde").getAsString())) {
                        continue;
                    }
                    JsonArray a = o.getAsJsonArray("a"), b = o.getAsJsonArray("b");
                    int ax = a.get(0).getAsInt(), az = a.get(2).getAsInt(), bx = b.get(0).getAsInt(), bz = b.get(2).getAsInt();
                    l.add(new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx) + 1, Math.max(az, bz) + 1});
                }
            }
            vip = l.toArray(new int[0][]);
            HeralizRecolte.LOG.info("[recolte] {} zone(s) VIP evitee(s) par les ressources ({})", vip.length, f);
        } catch (IOException | RuntimeException ex) {
            HeralizRecolte.LOG.warn("[recolte] zones VIP illisibles ({}) : zones lues avant gardees ({})", f, ex.toString());
        }
    }
}
