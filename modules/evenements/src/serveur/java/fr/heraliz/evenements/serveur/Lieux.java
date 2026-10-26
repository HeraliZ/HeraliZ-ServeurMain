package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lieux des événements, posés en jeu par le staff ({@code /event point ajouter <type> <nom>}, à l'endroit où il se
 * tient) : {@code <serveur>/heraliz_evenements/lieux.json}, hors du monde et jamais écrasé par la mise en ligne. Un lieu
 * = un nom et un point de la carte (overworld). Le staff choisit des lieux valides : sol dégagé, pas dans l'eau,
 * accessible à pied, loin des bases (docs/EVENEMENTS.md, « Lieux »).
 */
final class Lieux {
    private Lieux() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    record Lieu(String nom, int x, int y, int z) {
        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    @Nullable
    private static Path fichier;
    private static final Map<String, List<Lieu>> PAR_TYPE = new LinkedHashMap<>();

    static void charger(Path dossier) {
        fichier = dossier.resolve("lieux.json");
        PAR_TYPE.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Lieu> l = new ArrayList<>();
                for (JsonElement x : e.getValue().getAsJsonArray()) {
                    JsonObject p = x.getAsJsonObject();
                    l.add(new Lieu(p.get("nom").getAsString(), p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt()));
                }
                PAR_TYPE.put(e.getKey(), l);
            }
        } catch (IOException | RuntimeException ex) {
            Path copie = fichier.resolveSibling("lieux.json.illisible-" + System.currentTimeMillis());
            try {
                Files.copy(fichier, copie, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // rien de plus à faire
            }
            PAR_TYPE.clear();
            HeralizEvenements.LOG.error("[evenements] {} illisible (copie : {}) : aucun lieu", fichier, copie, ex);
        }
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        for (Map.Entry<String, List<Lieu>> e : PAR_TYPE.entrySet()) {
            JsonArray a = new JsonArray();
            for (Lieu l : e.getValue()) {
                JsonObject p = new JsonObject();
                p.addProperty("nom", l.nom());
                p.addProperty("x", l.x());
                p.addProperty("y", l.y());
                p.addProperty("z", l.z());
                a.add(p);
            }
            o.add(e.getKey(), a);
        }
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("lieux.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            HeralizEvenements.LOG.error("[evenements] ecriture de {} impossible", fichier, ex);
        }
    }

    static List<Lieu> liste(String type) {
        return PAR_TYPE.getOrDefault(type, List.of());
    }

    /** Ajoute (ou remplace, même nom) un lieu ; rend faux si le nom est invalide. */
    static boolean ajouter(String type, String nom, BlockPos pos) {
        String n = nom.toLowerCase(Locale.ROOT);
        if (!n.matches("[a-z0-9_-]{1,32}")) return false;
        List<Lieu> l = new ArrayList<>(liste(type));
        l.removeIf(x -> x.nom().equals(n));
        l.add(new Lieu(n, pos.getX(), pos.getY(), pos.getZ()));
        PAR_TYPE.put(type, l);
        sauver();
        return true;
    }

    static boolean retirer(String type, String nom) {
        List<Lieu> l = new ArrayList<>(liste(type));
        boolean fait = l.removeIf(x -> x.nom().equalsIgnoreCase(nom));
        if (fait) {
            PAR_TYPE.put(type, l);
            sauver();
        }
        return fait;
    }
}
