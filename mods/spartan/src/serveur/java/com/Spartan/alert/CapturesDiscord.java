package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.JsonFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Captures d'écran envoyées sur Discord : supprimées du salon au bout de 3 jours (demande du propriétaire, RGPD).
 * Chaque message est noté (numéro, fil, heure) dans config/spartan/captures_discord.json, puis effacé par le webhook
 * qui l'a posté (DELETE …/messages/{id}?thread_id={fil}). Vérifié au démarrage puis toutes les heures, hors du thread du
 * serveur. Message déjà effacé à la main : retiré de la liste.
 */
public final class CapturesDiscord {
    static final long DUREE_MS = 3L * 24 * 3600 * 1000;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final List<JsonObject> LISTE = new ArrayList<>();
    private static boolean charge;
    private static ScheduledExecutorService minuteur;

    private CapturesDiscord() {
    }

    private static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("captures_discord.json");
    }

    private static synchronized void charger() {
        if (charge) {
            return;
        }
        charge = true;
        try {
            String txt = JsonFiles.readOrNull(fichier());
            if (txt != null) {
                for (JsonElement e : JsonParser.parseString(txt).getAsJsonArray()) {
                    LISTE.add(e.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] captures_discord.json illisible : {}", e.getMessage());
        }
    }

    private static synchronized void sauver() {
        JsonArray a = new JsonArray();
        LISTE.forEach(a::add);
        JsonFiles.writeAsync(fichier(), a.toString());
    }

    /** Réponse de Discord à l'envoi d'une capture (envoyée avec wait=true). */
    static void noter(HttpResponse<String> r) {
        if (r == null || r.statusCode() >= 300) {
            return;
        }
        try {
            JsonObject m = JsonParser.parseString(r.body()).getAsJsonObject();
            JsonObject e = new JsonObject();
            e.addProperty("id", m.get("id").getAsString());
            e.addProperty("fil", m.has("channel_id") ? m.get("channel_id").getAsString() : "");
            e.addProperty("t", System.currentTimeMillis());
            synchronized (CapturesDiscord.class) {
                charger();
                LISTE.add(e);
                sauver();
            }
        } catch (Exception ex) {
            Spartan.LOGGER.warn("[Spartan] Capture Discord non notée (pas effacée dans 3 jours) : {}", ex.getMessage());
        }
    }

    /** Au démarrage du serveur : un passage tout de suite, puis toutes les heures. */
    public static synchronized void demarrer() {
        if (minuteur != null) {
            return;
        }
        minuteur = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Spartan-captures-discord");
            t.setDaemon(true);
            return t;
        });
        minuteur.scheduleWithFixedDelay(CapturesDiscord::nettoyer, 30, 3600, TimeUnit.SECONDS);
    }

    private static void nettoyer() {
        try {
            String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
            if (url == null || url.isBlank()) {
                return;
            }
            List<JsonObject> vieux = new ArrayList<>();
            synchronized (CapturesDiscord.class) {
                charger();
                long limite = System.currentTimeMillis() - DUREE_MS;
                for (JsonObject e : LISTE) {
                    if (e.get("t").getAsLong() < limite) {
                        vieux.add(e);
                    }
                }
            }
            String base = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
            for (JsonObject e : vieux) {
                if (effacer(base, e.get("id").getAsString(), e.get("fil").getAsString())) {
                    synchronized (CapturesDiscord.class) {
                        LISTE.remove(e);
                        sauver();
                    }
                }
                Thread.sleep(1500); // limite de Discord
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            Spartan.LOGGER.warn("[Spartan] Nettoyage des captures Discord : {}", ex.getMessage());
        }
    }

    /** true : effacé ou déjà absent. */
    private static boolean effacer(String base, String id, String fil) throws Exception {
        int code = supprimer(base + "/messages/" + id + (fil.isEmpty() ? "" : "?thread_id=" + fil));
        if (code == 400 && !fil.isEmpty()) {
            code = supprimer(base + "/messages/" + id); // salon normal : pas de fil
        }
        if (code < 300 || code == 404) {
            return true;
        }
        Spartan.LOGGER.warn("[Spartan] Capture Discord {} pas effacée (HTTP {}), nouvel essai dans 1 h.", id, code);
        return false;
    }

    private static int supprimer(String cible) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cible)).timeout(Duration.ofSeconds(15)).DELETE().build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
