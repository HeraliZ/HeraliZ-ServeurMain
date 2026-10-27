package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.util.JsonFiles;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Un fil Discord par joueur : le webhook des alertes vise un salon « Forum » ; la première alerte d'un joueur crée une
 * publication à son pseudo (thread_name), les suivantes y sont écrites (thread_id). Les numéros des fils sont gardés
 * dans config/spartan/discord_fils.json. Fil supprimé sur Discord : recréé à l'alerte suivante. Webhook d'un salon
 * normal (Discord le dit clairement, code 220003) : envois simples, et nouvel essai des fils 30 min plus tard (webhook
 * changé entre-temps). Tout autre refus à la création d'un fil ne change rien : le prochain message réessaie.
 */
final class FilsDiscord {
    /** Clé du fil des informations sans joueur (bans, débans, launcher…). */
    static final String INFOS = "_infos";
    static final String NOM_INFOS = "Informations du staff";
    /** Salon normal : délai avant de réessayer les fils. */
    private static final long REESSAI_FORUM_MS = 30L * 60_000;

    private static final Map<String, String> FILS = new HashMap<>();
    /** Dernier envoi de chaque fil : les envois d'un même joueur partent l'un après l'autre (un seul fil créé). */
    private static final Map<String, CompletableFuture<Void>> CHAINES = new HashMap<>();
    private static boolean charge;
    /** Le webhook ne vise pas un forum : envois simples jusqu'à cette heure (ms), puis nouvel essai des fils. */
    private static volatile long pasUnForumJusqua;
    /** Déjà écrit dans le journal : salon normal / refus inconnu à la création d'un fil (une fois chacun). */
    private static volatile boolean salonSignale;
    private static volatile boolean refusSignale;

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
     * créer (null : pas de création) et rend la réponse de Discord. Rend la fin de l'envoi (jamais en échec).
     */
    static CompletableFuture<Void> envoyer(String cle, String nom, Envoi envoi) {
        return envoyer(cle, nom, envoi, null);
    }

    /**
     * Pareil, et {@code rappel} reçoit une fois la réponse de Discord au message posté (envoyé avec wait=true), ou
     * null si l'envoi a échoué.
     */
    static CompletableFuture<Void> envoyer(String cle, String nom, Envoi envoi, Consumer<HttpResponse<String>> rappel) {
        Consumer<HttpResponse<String>> rappelUnique = DiscordWebhook.unique(rappel);
        synchronized (CHAINES) {
            CompletableFuture<Void> avant = CHAINES.getOrDefault(cle, CompletableFuture.completedFuture(null));
            CompletableFuture<Void> suite = avant.handle((v, e) -> null)
                    .thenCompose(v -> une(cle, nom, envoi, rappelUnique))
                    .exceptionally(ex -> {
                        Spartan.LOGGER.warn("[Spartan] Échec de l'envoi Discord : {}", ex.getMessage());
                        DiscordWebhook.appeler(rappelUnique, null);
                        return null;
                    });
            CHAINES.put(cle, suite);
            return suite;
        }
    }

    private static CompletableFuture<Void> une(String cle, String nom, Envoi envoi, Consumer<HttpResponse<String>> rappel) {
        String attendre = rappel == null ? "" : "wait=true";
        if (System.currentTimeMillis() < pasUnForumJusqua) {
            return envoi.apply(attendre.isEmpty() ? "" : "?" + attendre, null).thenAccept(r -> fin(r, rappel));
        }
        String id = fil(cle);
        if (id != null) {
            return envoi.apply("?thread_id=" + id + (attendre.isEmpty() ? "" : "&" + attendre), null).thenCompose(r -> {
                if (filDisparu(r)) {
                    garder(cle, null);
                    return creer(cle, nom, envoi, rappel);
                }
                fin(r, rappel);
                return CompletableFuture.completedFuture(null);
            });
        }
        return creer(cle, nom, envoi, rappel);
    }

    private static void fin(HttpResponse<String> r, Consumer<HttpResponse<String>> rappel) {
        refus(r);
        DiscordWebhook.appeler(rappel, r);
    }

    private static CompletableFuture<Void> creer(String cle, String nom, Envoi envoi, Consumer<HttpResponse<String>> rappel) {
        String titre = nom == null || nom.isBlank() ? cle : nom;
        if (titre.length() > 100) {
            titre = titre.substring(0, 100);
        }
        String nomDuFil = titre;
        return envoi.apply("?wait=true", nomDuFil).thenCompose(r -> {
            if (r.statusCode() < 300) {
                try {
                    garder(cle, JsonParser.parseString(r.body()).getAsJsonObject().get("channel_id").getAsString());
                } catch (Exception e) {
                    Spartan.LOGGER.warn("[Spartan] Réponse Discord sans numéro de fil : {}", e.getMessage());
                }
                DiscordWebhook.appeler(rappel, r);
                return CompletableFuture.completedFuture(null);
            }
            if (pasUnForum(r)) {
                // Salon normal : thread_name refusé. Envois simples, les fils seront réessayés plus tard.
                pasUnForumJusqua = System.currentTimeMillis() + REESSAI_FORUM_MS;
                if (!salonSignale) {
                    salonSignale = true;
                    Spartan.LOGGER.warn("[Spartan] Le webhook des alertes ne vise pas un salon Forum : pas de fil par "
                            + "joueur, nouvel essai toutes les 30 min ({}).", court(r.body()));
                }
                return envoi.apply(rappel == null ? "" : "?wait=true", null).thenAccept(x -> fin(x, rappel));
            }
            if (!refusSignale) {
                refusSignale = true;
                Spartan.LOGGER.warn("[Spartan] Discord a refusé la création du fil « {} » (HTTP {}) : {}. Le salon "
                        + "reste traité comme un Forum, nouvel essai au prochain message.", nomDuFil, r.statusCode(),
                        court(r.body()));
            }
            fin(r, rappel);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Discord dit clairement que le salon n'est pas un Forum : code 220003 (« Webhooks can only create threads in forum
     * channels »). Dans le doute (autre refus), on garde les fils.
     */
    private static boolean pasUnForum(HttpResponse<String> r) {
        if (r.statusCode() != 400) {
            return false;
        }
        String corps = r.body() == null ? "" : r.body().toLowerCase(Locale.ROOT);
        return DiscordWebhook.codeErreur(r.body()) == 220003 || corps.contains("only create threads in forum");
    }

    /** Fil supprimé ou archivé et fermé : Discord répond « Unknown Channel » (10003) ou refuse l'accès (50083). */
    private static boolean filDisparu(HttpResponse<String> r) {
        int code = r.statusCode() == 404 ? 0 : DiscordWebhook.codeErreur(r.body());
        return r.statusCode() == 404 || (r.statusCode() == 400 && code == 10003)
                || (r.statusCode() == 403 && code == 50083);
    }

    private static String court(String corps) {
        if (corps == null) {
            return "";
        }
        String s = corps.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
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
