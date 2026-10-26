package fr.heralisz.clans.territory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Json;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lecture / ecriture de config/heralisz_clans/zones.json (tableau de {@link Zone}). Si le fichier manque, il est ecrit
 * avec les villes de la map (meme liste que tools/clans/gen_zones.py, calculee depuis work/heralisz-map/gen/design.py).
 * Ecriture atomique (fichier temporaire puis renommage).
 */
public final class Zones {
    private Zones() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    /** Villes de la map : id, nom, x, z, rayon (drapeau a poser par un op). */
    public static List<Zone> defaut() {
        List<Zone> l = new ArrayList<>();
        l.add(new Zone("tcherno", "Tcherno", 261, 1483, 400, null));
        l.add(new Zone("novodar", "Novodar", -150, -700, 250, null));
        l.add(new Zone("lesnoi", "Lesnoi", -850, 450, 140, null));
        l.add(new Zone("krestovo", "Krestovo", 850, 620, 150, null));
        l.add(new Zone("zalivka", "Zalivka", 1260, 1120, 140, null));
        l.add(new Zone("nagorny", "Nagorny", -620, -250, 130, null));
        l.add(new Zone("vostok", "Vostok", 1320, -150, 130, null));
        l.add(new Zone("pristan", "Pristan", 1850, 250, 170, null));
        l.add(new Zone("pustovo", "Pustovo", 870, -660, 140, null));
        l.add(new Zone("rudnik", "Rudnik", -1950, -180, 130, null));
        l.add(new Zone("gorny", "Gorny", -120, -1700, 100, null));
        return l;
    }

    /**
     * Charge le fichier ; l'ecrit avec les valeurs par defaut s'il manque. Renvoie null s'il est illisible (JSON casse,
     * erreur d'E/S) : l'appelant garde alors ses zones et n'ecrit surtout pas par-dessus le fichier de l'admin.
     */
    @Nullable
    public static List<Zone> charger(Path fichier) {
        List<Zone> zones = new ArrayList<>();
        try {
            if (!Files.exists(fichier)) {
                zones.addAll(defaut());
                ecrire(fichier, zones);
                HeraliszClans.LOG.info("[clans] zones.json ecrit avec les {} villes par defaut", zones.size());
                return zones;
            }
            JsonElement root = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8));
            JsonArray a = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("zones");
            if (a == null) a = new JsonArray();
            for (JsonElement e : a) {
                if (!e.isJsonObject()) continue;
                Zone z = Zone.fromJson(e.getAsJsonObject());
                if (z == null) {
                    HeraliszClans.LOG.warn("[clans] zone ignoree (id invalide) : {}", e);
                    continue;
                }
                if (zones.stream().anyMatch(x -> x.id.equals(z.id))) {
                    HeraliszClans.LOG.warn("[clans] zone {} en double dans zones.json, ignoree", z.id);
                    continue;
                }
                String nomBrut = Json.str(e.getAsJsonObject(), "nom", "");
                if (!Zone.nomValide(nomBrut)) HeraliszClans.LOG.warn("[clans] nom de la zone {} invalide ou trop long, remplace par « {} »", z.id, z.nom);
                zones.add(z);
            }
            HeraliszClans.LOG.info("[clans] {} zone(s) chargee(s) depuis {}", zones.size(), fichier);
        } catch (IOException | RuntimeException ex) {
            HeraliszClans.LOG.error("[clans] zones.json illisible ({}) : zones gardees telles quelles, fichier non reecrit", fichier, ex);
            return null;
        }
        return zones;
    }

    /** Ecrit le tableau de zones (atomique). */
    public static void ecrire(Path fichier, Collection<Zone> zones) {
        JsonArray a = new JsonArray();
        for (Zone z : zones) a.add(z.toJson());
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(a), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[clans] ecriture de zones.json impossible ({})", fichier, ex);
        }
    }
}
