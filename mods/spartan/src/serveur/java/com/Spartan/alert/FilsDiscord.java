package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.util.JsonFiles;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Un fil Discord par joueur : le webhook des alertes vise un salon « Forum » ; la première alerte d'un joueur crée une
 * publication à son pseudo (thread_name), les suivantes y sont écrites (thread_id). Les numéros des fils sont gardés
 * dans config/spartan/discord_fils.json. Fil supprimé sur Discord : recréé à l'alerte suivante. Webhook d'un salon
 * normal (pas un forum) : envoi simple, comme avant.
 */
final class FilsDiscord {
    /** Clé du fil des informations sans joueur (bans, débans, launcher…). */
    static final String INFOS = "_infos";
    static final String NOM_INFOS = "Informations du staff";

    private static final Map<String, String> FILS = new HashMap<>();
    /** Dernier envoi de chaque fil : les envois d'un même joueur partent l'un après l'autre (un seul fil créé). */
    private static final Map<String, CompletableFuture<Void>> CHAINES = new HashMap<>();
    private static boolean charge;
    /** Le webhook ne vise pas un forum : plus de fils jusqu'au redémarrage. */
    private static volatile boolean pasUnForum;

    private FilsDiscord() {
    }

    private static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("discord_fils.json");
    }

    private static synchronized void charger() {
        if (charge) {
            return;
        }
        charge = true;
        try {
            String txt = JsonFiles.readOrNull(fichier());
            if (txt != null) {
                JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
                for (String k : o.keySet()) {
                    FILS.put(k, o.get(k).getAsString());
                }
            }
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] discord_fils.json illisible, fils recréés : {}", e.getMessage());
        }
    }

    private static synchronized String fil(String cle) {
        charger();
        return FILS.get(cle);
    }

    private static synchronized void garder(String cle, String id) {
        if (id == null) {
            FILS.remove(cle);
        } else {
            FILS.put(cle, id);
        }
        JsonObject o = new JsonObject();
        FILS.forEach(o::addProperty);
        JsonFiles.writeAsync(fichier(), o.toString());
    }

    /**
     * Envoie dans le fil de {@code cle}. {@code envoi} reçoit les paramètres à ajouter à l'URL et le nom du fil à
     * créer (null : pas de création) et rend la réponse de Discord.
     */
    static void envoyer(String cle, String nom, Envoi envoi) {
        synchronized (CHAINES) {
            CompletableFuture<Void> avant = CHAINES.getOrDefault(cle, CompletableFuture.completedFuture(null));
            CompletableFuture<Void> suite = avant.handle((v, e) -> null)
                    .thenCompose(v -> une(cle, nom, envoi))
                    .exceptionally(ex -> {
                        Spartan.LOGGER.warn("[Spartan] Échec de l'envoi Discord : {}", ex.getMessage());
                        return null;
                    });
            CHAINES.put(cle, suite);
        }
    }

    private static CompletableFuture<Void> une(String cle, String nom, Envoi envoi) {
        if (pasUnForum) {
            return envoi.apply("", null).thenAccept(r -> refus(r));
        }
        String id = fil(cle);
        if (id != null) {
            return envoi.apply("?thread_id=" + id, null).thenCompose(r -> {
                if (filDisparu(r)) {
                    garder(cle, null);
                    return creer(cle, nom, envoi);
                }
                refus(r);
                return CompletableFuture.completedFuture(null);
            });
        }
        return creer(cle, nom, envoi);
    }

    private static CompletableFuture<Void> creer(String cle, String nom, Envoi envoi) {
        String titre = nom == null || nom.isBlank() ? cle : nom;
        if (titre.length() > 100) {
            titre = titre.substring(0, 100);
        }
        return envoi.apply("?wait=true", titre).thenCompose(r -> {
            if (r.statusCode() < 300) {
                try {
                    garder(cle, JsonParser.parseString(r.body()).getAsJsonObject().get("channel_id").getAsString());
                } catch (Exception e) {
                    Spartan.LOGGER.warn("[Spartan] Réponse Discord sans numéro de fil : {}", e.getMessage());
                }
                return CompletableFuture.completedFuture(null);
            }
            if (r.statusCode() == 400 && !r.body().contains("220001")) {
                // Salon normal : thread_name refusé. On repasse aux envois simples.
                pasUnForum = true;
                Spartan.LOGGER.warn("[Spartan] Le webhook des alertes ne vise pas un salon Forum : pas de fil par "
                        + "joueur ({}).", r.body());
                return envoi.apply("", null).thenAccept(FilsDiscord::refus);
            }
            refus(r);
            return CompletableFuture.completedFuture(null);
        });
    }

    /** Fil supprimé ou archivé et fermé : Discord répond « Unknown Channel » (10003) ou refuse l'accès. */
    private static boolean filDisparu(HttpResponse<String> r) {
        return r.statusCode() == 404 || (r.statusCode() == 400 && r.body().contains("10003"))
                || (r.statusCode() == 403 && r.body().contains("50083"));
    }

    private static void refus(HttpResponse<String> r) {
        if (r.statusCode() >= 300) {
            Spartan.LOGGER.warn("[Spartan] Discord a refusé l'envoi (HTTP {}).", r.statusCode());
        }
    }

    @FunctionalInterface
    interface Envoi {
        CompletableFuture<HttpResponse<String>> apply(String parametres, String nomDuFil);
    }

}
