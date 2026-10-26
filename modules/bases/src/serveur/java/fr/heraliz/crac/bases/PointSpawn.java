package fr.heraliz.crac.bases;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import fr.heraliz.crac.Crac;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Le point « spawn » de {@code /spawn}, posé par le staff ({@code /spawn definir}) : monde, pieds, regard. Gardé HORS du
 * monde, à la racine du serveur ({@code crac/spawn.json}) : il survit à un changement de carte. Sans point (ou s'il est
 * effacé, ou son monde absent), {@code /spawn} mène au point de réveil de la cinématique (SpawnApi de HeraliZ Utils).
 * Le point d'arrivée de la première connexion (cinématique) ne change jamais. Fil serveur seulement.
 */
final class PointSpawn {
    record Point(ResourceLocation monde, double x, double y, double z, float lacet, float tangage) {
        ResourceKey<net.minecraft.world.level.Level> cle() {
            return ResourceKey.create(Registries.DIMENSION, this.monde);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Nullable
    private static Point point;
    @Nullable
    private static MinecraftServer charge;

    private PointSpawn() {
    }

    private static Path fichier(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("crac").resolve("spawn.json");
    }

    /** Le point défini, ou null (lu une fois par démarrage du serveur). */
    @Nullable
    static Point get(MinecraftServer server) {
        if (charge != server) {
            charge = server;
            point = lire(fichier(server));
        }
        return point;
    }

    /** Le monde du point défini, s'il existe sur ce serveur. */
    @Nullable
    static ServerLevel niveau(MinecraftServer server, Point p) {
        return server.getLevel(p.cle());
    }

    static boolean definir(MinecraftServer server, @Nullable Point p) {
        get(server);
        Path f = fichier(server);
        try {
            if (p == null) {
                Files.deleteIfExists(f);
            } else {
                Files.createDirectories(f.getParent());
                JsonObject o = new JsonObject();
                o.addProperty("monde", p.monde().toString());
                o.addProperty("x", p.x());
                o.addProperty("y", p.y());
                o.addProperty("z", p.z());
                o.addProperty("lacet", p.lacet());
                o.addProperty("tangage", p.tangage());
                Path tmp = f.resolveSibling("spawn.json.tmp");
                try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    GSON.toJson(o, w);
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Crac.LOGGER.error("[CRac] point /spawn : écriture de {} impossible", f, e);
            return false;
        }
        point = p;
        return true;
    }

    @Nullable
    private static Point lire(Path f) {
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            ResourceLocation m = ResourceLocation.tryParse(o.get("monde").getAsString());
            if (m == null) {
                throw new IllegalArgumentException("monde illisible");
            }
            return new Point(m, o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                o.has("lacet") ? o.get("lacet").getAsFloat() : 0f, o.has("tangage") ? o.get("tangage").getAsFloat() : 0f);
        } catch (IOException | RuntimeException e) {
            // fichier illisible : mis de côté (jamais écrasé), /spawn repart vers le point de réveil
            Path cote = f.resolveSibling("spawn.json.illisible-" + System.currentTimeMillis());
            try {
                Files.move(f, cote);
            } catch (IOException ex) {
                Crac.LOGGER.warn("[CRac] point /spawn : {} illisible et impossible à mettre de côté ({})", f, ex.toString());
            }
            Crac.LOGGER.error("[CRac] point /spawn : {} illisible ({}), mis de côté dans {} : /spawn mène au point de réveil", f, e.toString(), cote);
            return null;
        }
    }
}
