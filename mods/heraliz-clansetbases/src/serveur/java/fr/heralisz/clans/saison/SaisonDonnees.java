package fr.heralisz.clans.saison;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Etat de la saison, hors du monde (survit a une nouvelle carte) : <serveur>/heralisz_clans/saison_etat.json. Scores
 * par clan (PVE, PVP, events), prises de coeur deja faites (une par clan pris et par Purge), kills recents par couple
 * (rendement decroissant), points des petites actions du jour par joueur (plafond). Tout est garde pour qu'un
 * redemarrage ne remette aucun compteur a zero.
 */
final class SaisonDonnees {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final int PVE = 0, PVP = 1, EVENTS = 2;

    int numero;
    long debut, fin;
    boolean active;
    final Map<UUID, double[]> scores = new LinkedHashMap<>();
    /** « purge|clan pris ». */
    final Set<String> raids = new HashSet<>();
    /** « tueur|victime » -> heures (ms) des kills dans la fenetre. */
    final Map<String, List<Long>> paires = new HashMap<>();
    /** Jour (AAAA-MM-JJ, heure de Paris) des plafonds ci-dessous. */
    String jour = "";
    final Map<UUID, Double> plafonds = new HashMap<>();

    double[] scores(UUID clan) {
        return scores.computeIfAbsent(clan, k -> new double[3]);
    }

    // ------------------------------------------------------------------ fichier
    static SaisonDonnees charger(Path f) {
        SaisonDonnees d = new SaisonDonnees();
        if (!Files.exists(f)) return d;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            d.numero = Json.integer(o, "numero", 0);
            d.debut = Json.lng(o, "debut", 0);
            d.fin = Json.lng(o, "fin", 0);
            d.active = Json.bool(o, "active", false);
            JsonObject sc = Json.obj(o, "scores");
            for (Map.Entry<String, JsonElement> e : sc.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                JsonArray a = e.getValue().getAsJsonArray();
                double[] v = new double[3];
                for (int i = 0; i < 3 && i < a.size(); i++) v[i] = Math.max(0, a.get(i).getAsDouble());
                d.scores.put(UUID.fromString(e.getKey()), v);
            }
            for (JsonElement e : Json.arr(o, "raids")) if (e.isJsonPrimitive()) d.raids.add(e.getAsString());
            JsonObject pa = Json.obj(o, "paires");
            for (Map.Entry<String, JsonElement> e : pa.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Long> l = new ArrayList<>();
                for (JsonElement t : e.getValue().getAsJsonArray()) l.add(t.getAsLong());
                d.paires.put(e.getKey(), l);
            }
            d.jour = Json.str(o, "jour", "");
            JsonObject pl = Json.obj(o, "plafonds");
            for (Map.Entry<String, JsonElement> e : pl.entrySet()) d.plafonds.put(UUID.fromString(e.getKey()), e.getValue().getAsDouble());
        } catch (IOException | RuntimeException ex) {
            // un fichier illisible n'est jamais ecrase : copie a cote, et la saison repart vide (journal du serveur)
            Path copie = f.resolveSibling(f.getFileName() + ".illisible-" + System.currentTimeMillis());
            try {
                Files.copy(f, copie, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // rien de plus a faire
            }
            HeraliszClans.LOG.error("[saison] {} illisible (copie : {}), saison vide", f, copie, ex);
            return new SaisonDonnees();
        }
        return d;
    }

    void sauvegarder(Path f) {
        JsonObject o = new JsonObject();
        o.addProperty("numero", numero);
        o.addProperty("debut", debut);
        o.addProperty("fin", fin);
        o.addProperty("active", active);
        JsonObject sc = new JsonObject();
        for (Map.Entry<UUID, double[]> e : scores.entrySet()) {
            JsonArray a = new JsonArray();
            for (double v : e.getValue()) a.add(v);
            sc.add(e.getKey().toString(), a);
        }
        o.add("scores", sc);
        JsonArray r = new JsonArray();
        raids.stream().sorted().forEach(r::add);
        o.add("raids", r);
        JsonObject pa = new JsonObject();
        for (Map.Entry<String, List<Long>> e : paires.entrySet()) {
            JsonArray a = new JsonArray();
            e.getValue().forEach(a::add);
            pa.add(e.getKey(), a);
        }
        o.add("paires", pa);
        o.addProperty("jour", jour);
        JsonObject pl = new JsonObject();
        for (Map.Entry<UUID, Double> e : plafonds.entrySet()) pl.addProperty(e.getKey().toString(), e.getValue());
        o.add("plafonds", pl);
        ecrire(f, o);
    }

    static void ecrire(Path f, JsonObject o) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[saison] ecriture de {} impossible", f, ex);
        }
    }
}
