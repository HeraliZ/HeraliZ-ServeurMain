package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.Text;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Envoi des alertes vers un salon Discord privé via webhook.
 *
 * L'envoi est 100 % asynchrone (sendAsync) : le thread principal du serveur
 * n'est jamais bloqué, même si Discord est lent ou injoignable.
 */
public class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final int COLOR_RED = 0xE74C3C;
    private static final int MAX_PER_MINUTE = 20; // le webhook Discord plafonne ~30/min
    private static final ArrayDeque<Long> SENT = new ArrayDeque<>();
    private static int dropped;

    /** Limiteur global partagé par toutes les alertes Discord. */
    private static synchronized boolean allow() {
        long now = System.currentTimeMillis();
        while (!SENT.isEmpty() && now - SENT.peekFirst() > 60_000) {
            SENT.pollFirst();
        }
        if (SENT.size() >= MAX_PER_MINUTE) {
            if (++dropped % 20 == 1) {
                Spartan.LOGGER.warn("[Spartan] Trop d'alertes Discord : {} ignorées (limite {}/min). "
                        + "Consultez alerts.log.", dropped, MAX_PER_MINUTE);
            }
            return false;
        }
        SENT.addLast(now);
        return true;
    }

    private static void post(String url, JsonObject payload) {
        post(url, payload, null, null);
    }

    /** Envoi simple (cle null) ou dans le fil Discord de {@code cle} (voir FilsDiscord). */
    private static void post(String url, JsonObject payload, String cle, String nomFil) {
        if (!allow()) {
            return;
        }
        if (cle == null) {
            requete(url, "", payload.toString(), "application/json", Duration.ofSeconds(10))
                    .thenAccept(DiscordWebhook::refus)
                    .exceptionally(DiscordWebhook::echec);
            return;
        }
        FilsDiscord.envoyer(cle, nomFil, (params, nom) -> requete(url, params, avecFil(payload, nom).toString(),
                "application/json", Duration.ofSeconds(10)));
    }

    private static JsonObject avecFil(JsonObject payload, String nomFil) {
        if (nomFil == null) {
            return payload;
        }
        JsonObject copie = payload.deepCopy();
        copie.addProperty("thread_name", nomFil);
        return copie;
    }

    private static java.util.concurrent.CompletableFuture<HttpResponse<String>> requete(String url, String params,
            String corps, String type, Duration delai) {
        return requete(url, params, HttpRequest.BodyPublishers.ofString(corps), type, delai);
    }

    private static java.util.concurrent.CompletableFuture<HttpResponse<String>> requete(String url, String params,
            HttpRequest.BodyPublisher corps, String type, Duration delai) {
        String cible = url + (params.isEmpty() ? "" : (url.contains("?") ? "&" + params.substring(1) : params));
        HttpRequest req = HttpRequest.newBuilder(URI.create(cible))
                .timeout(delai)
                .header("Content-Type", type)
                .POST(corps)
                .build();
        return CLIENT.sendAsync(req, BodyHandlers.ofString());
    }

    private static void refus(HttpResponse<String> resp) {
        if (resp.statusCode() >= 300) {
            Spartan.LOGGER.warn("[Spartan] Discord a refusé l'envoi (HTTP {}).", resp.statusCode());
        }
    }

    private static Void echec(Throwable ex) {
        Spartan.LOGGER.warn("[Spartan] Échec de l'envoi Discord : {}", ex.getMessage());
        return null;
    }

    /** Envoi avec un fichier joint (multipart/form-data, format des webhooks Discord), dans le fil de {@code cle}. */
    private static void postWithFile(String url, JsonObject payload, String fileName, byte[] file, String cle,
                                     String nomFil) {
        if (!allow()) {
            return;
        }
        FilsDiscord.envoyer(cle, nomFil, (params, nom) -> {
            String boundary = "spartan" + Long.toHexString(System.nanoTime())
                    + Long.toHexString(Double.doubleToLongBits(Math.random()));
            String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            ByteArrayOutputStream body = new ByteArrayOutputStream(file.length + 4096);
            Charset utf8 = StandardCharsets.UTF_8;
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                    + "Content-Type: application/json\r\n\r\n").getBytes(utf8));
            body.writeBytes(avecFil(payload, nom).toString().getBytes(utf8));
            body.writeBytes(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""
                    + safeName + "\"\r\nContent-Type: application/json\r\n\r\n").getBytes(utf8));
            body.writeBytes(file);
            body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(utf8));
            return requete(url, params, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()),
                    "multipart/form-data; boundary=" + boundary, Duration.ofSeconds(20));
        });
    }

    /**
     * Rapport de contrôle d'intégrité : embed + fichier JSON joint (la preuve complète).
     * @param fields paires {nom, valeur} déjà neutralisées par l'appelant si elles viennent du client
     */
    public static void sendReport(String cleJoueur, String nomJoueur, String title, int color, String description,
                                  List<String[]> fields, String fileName, byte[] file) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", Text.discord(title));
            embed.addProperty("description", description);
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonArray arr = new JsonArray();
            for (String[] f : fields) {
                arr.add(field(f[0], f[1].length() > 1024 ? f[1].substring(0, 1021) + "…" : f[1], false));
            }
            embed.add("fields", arr);
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Spartan AntiCheat — détection silencieuse");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            denyMentions(payload);
            if (file != null && fileName != null) {
                postWithFile(url, payload, fileName, file, cleJoueur, nomJoueur);
            } else {
                post(url, payload, cleJoueur, nomJoueur);
            }
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    private DiscordWebhook() {
    }

    public static void send(String cleJoueur, String playerName, String check, int vl, String details, String evidenceFile) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }

        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "⚠ Spartan — " + check);
            embed.addProperty("color", COLOR_RED);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonArray fields = new JsonArray();
            fields.add(field("Joueur", Text.discord(playerName), true));
            fields.add(field("Détection", Text.discord(check), true));
            fields.add(field("Violation Level", String.valueOf(vl), true));
            fields.add(field("Détails", details == null || details.isBlank() ? "-" : Text.discord(details), false));
            if (evidenceFile != null && !evidenceFile.isBlank()) {
                fields.add(field("Preuve (sur le serveur)", "config/spartan/evidence/" + Text.discord(evidenceFile), false));
            }
            embed.add("fields", fields);

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Spartan AntiCheat");
            embed.add("footer", footer);

            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            denyMentions(payload);
            post(url, payload, cleJoueur, playerName);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    /** Embed simple (bans, débans, infos staff). */
    public static void sendInfo(String title, String description, int color) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", Text.discord(title));
            embed.addProperty("description", Text.discord(description));
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Spartan AntiCheat");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            denyMentions(payload);
            post(url, payload, FilsDiscord.INFOS, FilsDiscord.NOM_INFOS);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    /**
     * Annonce publique d'une sanction (salon des sanctions, discordSanctionsWebhookUrl) : pseudo, durée, raison. Rien
     * qui aide un tricheur (pas de détection, pas de preuve) ni donnée personnelle (pas d'IP, pas de PC).
     */
    public static void sendSanction(String joueur, String duree, String raison) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_SANCTIONS_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Sanction");
            embed.addProperty("description", "**" + Text.discord(joueur) + "** a été banni du serveur.");
            embed.addProperty("color", AlertManager.COLOR_BAN);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonArray fields = new JsonArray();
            fields.add(field("Durée", Text.discord(duree), true));
            fields.add(field("Raison", Text.discord(raison == null || raison.isBlank() ? "non précisée" : raison), true));
            embed.add("fields", fields);
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "HeraliZ");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            denyMentions(payload);
            post(url, payload);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord des sanctions invalide : {}", e.getMessage());
        }
    }

    /** Défense en profondeur : Discord n'autorise aucune mention, même si une passait l'échappement. */
    private static void denyMentions(JsonObject payload) {
        JsonObject am = new JsonObject();
        am.add("parse", new JsonArray());
        payload.add("allowed_mentions", am);
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }
}
