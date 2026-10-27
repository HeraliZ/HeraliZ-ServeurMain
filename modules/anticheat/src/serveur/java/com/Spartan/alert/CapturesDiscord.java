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
 * serveur. Message (ou fil) déjà effacé à la main : retiré de la liste. Tout autre refus (400, 403, webhook changé…) :
 * gardé et réessayé toutes les heures, abandonné 7 jours après la date prévue (ligne dans le journal).
 */
public final class CapturesDiscord {
    static final long DUREE_MS = 3L * 24 * 3600 * 1000;
    /** Effacement toujours refusé si longtemps après la date prévue : abandonné. */
    private static final long ABANDON_MS = 7L * 24 * 3600 * 1000;
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

    /** Copies du serveur (screenshots/ à la racine) de plus de 3 jours : supprimées, dossiers vides compris. */
    private static void effacerFichiers() {
        java.nio.file.Path racine = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("screenshots");
        if (!java.nio.file.Files.isDirectory(racine)) {
            return;
        }
        long limite = System.currentTimeMillis() - DUREE_MS;
        try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(racine)) {
            for (java.nio.file.Path f : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    if (f.equals(racine)) {
                        continue;
                    }
                    if (java.nio.file.Files.isDirectory(f)) {
                        try (java.util.stream.Stream<java.nio.file.Path> d = java.nio.file.Files.list(f)) {
                            if (d.findAny().isEmpty()) {
                                java.nio.file.Files.delete(f);
                            }
                        }
                    } else if (java.nio.file.Files.getLastModifiedTime(f).toMillis() < limite) {
                        java.nio.file.Files.delete(f);
                    }
                } catch (Exception ignore) {
                    // réessayé dans une heure
                }
            }
        } catch (Exception ignore) {
            // réessayé dans une heure
        }
    }

    /** Au démarrage du serveur : un passage tout de suite, puis toutes les heures. */
    public static synchronized void demarrer() {
        if (minuteur != null) {
            return;
        }
        DiscordWebhook.demarrer(); // état des webhooks annoncé une fois le serveur prêt
        minuteur = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Spartan-captures-discord");
            t.setDaemon(true);
            return t;
        });
        minuteur.scheduleWithFixedDelay(CapturesDiscord::nettoyer, 30, 3600, TimeUnit.SECONDS);
    }

    private static void nettoyer() {
        effacerFichiers();
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
            if (vieux.isEmpty()) {
                return;
            }
            String base = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
            String salon = salonDuWebhook(base);
            for (JsonObject e : vieux) {
                String id = e.get("id").getAsString();
                int code = effacer(base, id, e.get("fil").getAsString(), salon);
                boolean fini = code == EFFACE;
                if (!fini && System.currentTimeMillis() - e.get("t").getAsLong() > DUREE_MS + ABANDON_MS) {
                    Spartan.LOGGER.warn("[Spartan] Capture Discord {} abandonnée : toujours pas effacée 7 jours après la "
                            + "date prévue (HTTP {}). À supprimer à la main dans le salon.", id, code);
                    fini = true;
                }
                if (fini) {
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

    /** Résultat de {@link #effacer} : effacé ou déjà absent (sinon : le code HTTP du refus). */
    private static final int EFFACE = 0;

    /**
     * Efface un message. {@code salon} : salon du webhook (null si inconnu) ; un message noté dans ce salon n'est pas
     * dans un fil. Seuls un effacement réussi et un « message / fil inconnu » de Discord comptent comme faits.
     */
    private static int effacer(String base, String id, String fil, String salon) throws Exception {
        boolean dansFil = !fil.isEmpty() && !fil.equals(salon);
        HttpResponse<String> r = supprimer(base + "/messages/" + id + (dansFil ? "?thread_id=" + fil : ""));
        if (r.statusCode() == 400 && dansFil && salon == null) {
            // Salon du webhook inconnu : peut-être un salon normal (pas de fil). Ici, seul un vrai effacement compte :
            // un 404 sans fil ne prouve rien (le message est peut-être dans un fil).
            HttpResponse<String> simple = supprimer(base + "/messages/" + id);
            if (simple.statusCode() < 300) {
                return EFFACE;
            }
        }
        if (r.statusCode() < 300 || dejaAbsent(r, salon != null)) {
            return EFFACE;
        }
        Spartan.LOGGER.warn("[Spartan] Capture Discord {} pas effacée (HTTP {}), nouvel essai dans 1 h.", id,
                r.statusCode());
        return r.statusCode();
    }

    /**
     * Déjà supprimé : 404 « Unknown Message » (10008) ou, si le salon du webhook est connu, « Unknown Channel » (10003 :
     * fil supprimé avec le message ; sans le salon, ce pourrait être un faux fil).
     */
    private static boolean dejaAbsent(HttpResponse<String> r, boolean salonConnu) {
        if (r.statusCode() != 404) {
            return false;
        }
        int code = DiscordWebhook.codeErreur(r.body());
        return code == 10008 || (salonConnu && code == 10003);
    }

    /** Salon où poste le webhook (lecture du webhook) : pour savoir si un message noté est dans un fil ; null si inconnu. */
    private static String salonDuWebhook(String base) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base)).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> r = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 300) {
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                return o.has("channel_id") ? o.get("channel_id").getAsString() : null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ignore) {
            // inconnu : effacement prudent (voir effacer)
        }
        return null;
    }

    private static HttpResponse<String> supprimer(String cible) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cible)).timeout(Duration.ofSeconds(15)).DELETE().build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
