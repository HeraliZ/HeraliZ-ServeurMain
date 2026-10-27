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
 * (rendement decroissant), points des petites actions du jour par joueur (plafond), boss tues du jour par clan. Tout est garde pour qu'un
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
    /** Jour (AAAA-MM-JJ, heure de Paris) des plafonds et des boss ci-dessous. */
    String jour = "";
    final Map<UUID, Double> plafonds = new HashMap<>();
    /** « clan|type du boss » -> kills de ce boss par ce clan dans la journee (rendement decroissant). */
    final Map<String, Integer> bossJour = new HashMap<>();
    /** Points de capture de drapeau du jour par clan (plafond). */
    final Map<UUID, Double> capturesJour = new HashMap<>();
    /** Captures recompensees : « zone|zone|clan » et « paire|zone|clan|ancien » -> heure (ms). */
    final Map<String, Long> captures = new HashMap<>();
    /** « clan|source » -> evenements de la carte recompenses dans la journee (plafond par clan et par jour). */
    final Map<String, Integer> evenementsJour = new HashMap<>();

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
            JsonObject bj = Json.obj(o, "boss_jour");
            for (Map.Entry<String, JsonElement> e : bj.entrySet()) d.bossJour.put(e.getKey(), e.getValue().getAsInt());
            JsonObject cj = Json.obj(o, "captures_jour");
            for (Map.Entry<String, JsonElement> e : cj.entrySet()) d.capturesJour.put(UUID.fromString(e.getKey()), e.getValue().getAsDouble());
            JsonObject ca = Json.obj(o, "captures");
            for (Map.Entry<String, JsonElement> e : ca.entrySet()) d.captures.put(e.getKey(), e.getValue().getAsLong());
            JsonObject ej = Json.obj(o, "evenements_jour");
            for (Map.Entry<String, JsonElement> e : ej.entrySet()) d.evenementsJour.put(e.getKey(), e.getValue().getAsInt());
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

    /** Relevé sur le fil serveur (arbre JSON neuf), écriture sur le fil d'écriture des clans ; {@code siEchec} : voir {@link #ecrire}. */
    void sauvegarder(Path f, @javax.annotation.Nullable Runnable siEchec) {
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
        JsonObject bj = new JsonObject();
        for (Map.Entry<String, Integer> e : bossJour.entrySet()) bj.addProperty(e.getKey(), e.getValue());
        o.add("boss_jour", bj);
        JsonObject cj = new JsonObject();
        for (Map.Entry<UUID, Double> e : capturesJour.entrySet()) cj.addProperty(e.getKey().toString(), e.getValue());
        o.add("captures_jour", cj);
        JsonObject ca = new JsonObject();
        for (Map.Entry<String, Long> e : captures.entrySet()) ca.addProperty(e.getKey(), e.getValue());
        o.add("captures", ca);
        JsonObject ej = new JsonObject();
        for (Map.Entry<String, Integer> e : evenementsJour.entrySet()) ej.addProperty(e.getKey(), e.getValue());
        o.add("evenements_jour", ej);
        ecrire(f, o, siEchec);
    }

    static void ecrire(Path f, JsonObject o) {
        ecrire(f, o, null);
    }

    /**
     * Écrit {@code o} (arbre neuf, plus touché par le fil serveur) dans {@code f} sur le fil d'écriture des clans (27/10,
     * perf) ; {@code siEchec} est appelé sur ce fil si l'écriture rate (seulement poser un drapeau).
     */
    static void ecrire(Path f, JsonObject o, @javax.annotation.Nullable Runnable siEchec) {
        fr.heralisz.clans.server.EcrituresFond.ecrire(f, () -> {
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                HeraliszClans.LOG.error("[saison] ecriture de {} impossible", f, ex);
                if (siEchec != null) siEchec.run();
            }
        }, null);
    }
}
