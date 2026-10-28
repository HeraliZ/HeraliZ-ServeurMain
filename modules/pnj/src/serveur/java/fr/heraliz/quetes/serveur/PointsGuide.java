package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Points d'intérêt de la visite du Guide (Quêtes 1.14.0, demande du propriétaire du 28/10) : casino, Table d'armurier,
 * coffre « Safe Planque 1 », caisses, borne de départ, recycleur. Leur place n'est pas connue du code : le staff les pose
 * EN JEU avec {@code /guide point <id>}, debout là où la caméra doit se tenir, en regardant la chose à montrer (le bloc
 * visé, jusqu'à 24 blocs ; sinon 6 blocs devant lui). La caméra de la visite tourne alors un peu autour de ce point, du
 * côté du staff, à la même distance (2,5 à 10 blocs).
 * <p>
 * Gardés à la racine du serveur ({@code heraliz_quetes/guide_points.json}, comme {@code marchands_jeu.json}) : jamais
 * écrasés par la mise en ligne, ils survivent aux redémarrages. Un point non posé : le Guide dit sa phrase sans caméra
 * ({@link Accueil}).
 */
final class PointsGuide {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Les points de la visite, dans l'ordre où le Guide les présente. */
    static final List<String> IDS = List.of("casino", "armurier", "coffre", "caisses", "borne", "recycleur");
    private static final double PORTEE = 24, DEVANT = 6, RAYON_MIN = 2.5, RAYON_MAX = 10;

    /** Un point : monde, centre regardé par la caméra, angle (radians, atan2(dz, dx) du centre vers la caméra), rayon. */
    record Point(String monde, double x, double y, double z, float angle, float rayon) {
    }

    private static Path fichier;
    private static final Map<String, Point> POINTS = new LinkedHashMap<>();

    private PointsGuide() {
    }

    static void demarrer(Path racineServeur) {
        fichier = racineServeur.resolve("heraliz_quetes").resolve("guide_points.json");
        POINTS.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (!IDS.contains(e.getKey()) || !e.getValue().isJsonObject()) continue;
                JsonObject j = e.getValue().getAsJsonObject();
                POINTS.put(e.getKey(), new Point(Defs.chaine(j, "monde", "heralisz:spawn"), j.get("x").getAsDouble(),
                        j.get("y").getAsDouble(), j.get("z").getAsDouble(), j.get("angle").getAsFloat(),
                        (float) Math.max(RAYON_MIN, Math.min(RAYON_MAX, j.get("rayon").getAsDouble()))));
            }
        } catch (Exception ex) {
            // illisible : jamais écrasé, mis de côté (comme pnj_jeu.json)
            LOG.error("[heraliz_quetes] {} illisible, mis de côté : {}", fichier, ex.getMessage());
            POINTS.clear();
            try {
                Files.move(fichier, fichier.resolveSibling("guide_points.json.illisible"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
            }
        }
    }

    private static boolean sauver() {
        if (fichier == null) return false;
        JsonObject o = new JsonObject();
        POINTS.forEach((id, p) -> {
            JsonObject j = new JsonObject();
            j.addProperty("monde", p.monde());
            j.addProperty("x", Math.round(p.x() * 100) / 100.0);
            j.addProperty("y", Math.round(p.y() * 100) / 100.0);
            j.addProperty("z", Math.round(p.z() * 100) / 100.0);
            j.addProperty("angle", Math.round(p.angle() * 1000) / 1000.0);
            j.addProperty("rayon", Math.round(p.rayon() * 100) / 100.0);
            o.add(id, j);
        });
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("guide_points.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ex) {
            LOG.error("[heraliz_quetes] écriture de {} impossible : {}", fichier, ex.getMessage());
            return false;
        }
    }

    static Point point(String id) {
        return POINTS.get(id);
    }

    /** Pose (ou repose) le point {@code id} d'après la place et le regard du staff ; null si l'enregistrement a échoué. */
    static Point poser(ServerPlayer p, String id) {
        Vec3 oeil = p.getEyePosition();
        Vec3 regard = p.getLookAngle();
        BlockHitResult hit = p.level().clip(new ClipContext(oeil, oeil.add(regard.scale(PORTEE)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, p));
        Vec3 c;
        if (hit.getType() == HitResult.Type.BLOCK) {
            // un peu vers le staff : la caméra ne part jamais de l'intérieur du bloc visé
            c = hit.getLocation().add(oeil.subtract(hit.getLocation()).normalize().scale(0.15));
        } else {
            c = oeil.add(regard.scale(DEVANT));
        }
        double dx = oeil.x - c.x, dz = oeil.z - c.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float angle = h > 0.3 ? (float) Math.atan2(dz, dx) : (float) Math.atan2(-regard.z, -regard.x);
        float rayon = (float) Math.max(RAYON_MIN, Math.min(RAYON_MAX, h));
        Point pt = new Point(p.level().dimension().location().toString(), c.x, c.y, c.z, angle, rayon);
        Point avant = POINTS.put(id, pt);
        if (!sauver()) {
            if (avant == null) POINTS.remove(id);
            else POINTS.put(id, avant);
            return null;
        }
        return pt;
    }

    static boolean retirer(String id) {
        Point avant = POINTS.remove(id);
        if (avant == null) return false;
        if (!sauver()) {
            POINTS.put(id, avant);
            return false;
        }
        return true;
    }
}
