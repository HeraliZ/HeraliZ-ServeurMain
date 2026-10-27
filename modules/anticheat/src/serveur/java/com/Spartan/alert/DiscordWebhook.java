package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.Text;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Envoi des alertes vers un salon Discord privé via webhook.
 *
 * L'envoi est 100 % asynchrone : chaque message entre dans une file bornée ({@value #FILE_MAX} au plus), vidée par un
 * seul thread qui espace les envois d'environ 1 s par webhook et respecte les limites de Discord (HTTP 429 : attente
 * retry_after, puis {@value #ESSAIS_429} nouveaux essais). Les sanctions et les bans / débans passent devant les autres
 * messages ; file pleine : les plus anciens messages ordinaires sont abandonnés d'abord. Le thread principal du serveur
 * n'est jamais bloqué, même si Discord est lent ou injoignable.
 */
public class DiscordWebhook {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final int COLOR_RED = 0xE74C3C;
    /** Messages en attente au plus (toutes priorités). */
    private static final int FILE_MAX = 300;
    /** Écart entre deux envois sur un même webhook. */
    private static final long ECART_MS = 1000;
    /** Nouveaux essais après un refus HTTP 429 (limite de Discord). */
    private static final int ESSAIS_429 = 2;
    /** Envoi sans fin depuis plus longtemps : son webhook est libéré (filet de sécurité). */
    private static final long EN_COURS_MAX_MS = 360_000;
    /** Pièce jointe plus grosse : compressée (gzip), sinon coupée (Discord refuse plus de 10 Mio). */
    private static final int JOINT_MAX = 8 * 1024 * 1024;

    /** Résultats de {@link #tester} quand rien n'a été posté (sinon : le code HTTP de Discord). */
    public static final int NON_CONFIGURE = 0;
    public static final int DESACTIVE = -1;
    public static final int ECHEC = -2;

    private static final ArrayDeque<Message> PRIORITAIRES = new ArrayDeque<>();
    private static final ArrayDeque<Message> ORDINAIRES = new ArrayDeque<>();
    /** Webhook → heure du prochain envoi permis. */
    private static final Map<String, Long> PROCHAIN = new ConcurrentHashMap<>();
    /** Webhook → début de l'envoi en cours (un seul à la fois par webhook). */
    private static final Map<String, Long> EN_COURS = new ConcurrentHashMap<>();
    private static ScheduledExecutorService envoyeur;
    private static int perdus;
    private static boolean annonce;

    /** Message en attente : {@code envoi} le poste et rend sa fin ; {@code rappel} reçoit null s'il est abandonné. */
    private record Message(String webhook, Supplier<CompletableFuture<?>> envoi,
                           Consumer<HttpResponse<String>> rappel) {
    }

    /** Clé du webhook pour l'espacement des envois : son adresse sans paramètres. */
    private static String webhook(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    /** Met un message dans la file, sans attendre : le thread d'envoi le poste dès que son webhook est libre. */
    private static void mettreEnFile(String url, boolean prioritaire, Supplier<CompletableFuture<?>> envoi,
                                     Consumer<HttpResponse<String>> rappel) {
        Message m = new Message(webhook(url), envoi, rappel);
        Message abandon = null;
        synchronized (DiscordWebhook.class) {
            if (PRIORITAIRES.size() + ORDINAIRES.size() >= FILE_MAX) {
                if (!ORDINAIRES.isEmpty()) {
                    abandon = ORDINAIRES.pollFirst();
                } else if (prioritaire) {
                    abandon = PRIORITAIRES.pollFirst();
                } else {
                    abandon = m;
                }
                if (++perdus % 20 == 1) {
                    Spartan.LOGGER.warn("[Spartan] File d'envoi Discord pleine : {} message(s) abandonné(s) en tout "
                            + "(les plus anciens d'abord). Consultez alerts.log.", perdus);
                }
            }
            if (abandon != m) {
                (prioritaire ? PRIORITAIRES : ORDINAIRES).addLast(m);
            }
            if (envoyeur == null) {
                envoyeur = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "Spartan-discord");
                    t.setDaemon(true);
                    return t;
                });
                envoyeur.scheduleWithFixedDelay(DiscordWebhook::vider, 200, 200, TimeUnit.MILLISECONDS);
            }
        }
        if (abandon != null) {
            appeler(abandon.rappel(), null);
        }
    }

    /** Thread d'envoi, toutes les 200 ms : lance le plus ancien message de chaque webhook libre, prioritaires d'abord. */
    private static void vider() {
        try {
            long now = System.currentTimeMillis();
            EN_COURS.entrySet().removeIf(e -> {
                if (now - e.getValue() <= EN_COURS_MAX_MS) {
                    return false;
                }
                Spartan.LOGGER.warn("[Spartan] Envoi Discord sans réponse depuis {} s : webhook libéré.",
                        EN_COURS_MAX_MS / 1000);
                return true;
            });
            List<Message> lances = new ArrayList<>();
            synchronized (DiscordWebhook.class) {
                if (PRIORITAIRES.isEmpty() && ORDINAIRES.isEmpty()) {
                    return;
                }
                choisir(PRIORITAIRES, now, lances);
                choisir(ORDINAIRES, now, lances);
            }
            for (Message m : lances) {
                lancer(m, now);
            }
        } catch (Throwable t) {
            Spartan.LOGGER.warn("[Spartan] Envoi Discord : {}", t.toString());
        }
    }

    private static void choisir(ArrayDeque<Message> file, long now, List<Message> lances) {
        Iterator<Message> it = file.iterator();
        while (it.hasNext()) {
            Message m = it.next();
            if (EN_COURS.containsKey(m.webhook()) || PROCHAIN.getOrDefault(m.webhook(), 0L) > now) {
                continue;
            }
            it.remove();
            EN_COURS.put(m.webhook(), now);
            PROCHAIN.merge(m.webhook(), now + ECART_MS, Math::max);
            lances.add(m);
        }
    }

    private static void lancer(Message m, long debut) {
        CompletableFuture<?> f;
        try {
            f = m.envoi().get();
        } catch (Exception e) {
            echec(e);
            appeler(m.rappel(), null);
            f = CompletableFuture.completedFuture(null);
        }
        f.whenComplete((v, ex) -> {
            EN_COURS.remove(m.webhook(), debut);
            if (ex != null) {
                echec(ex);
            }
        });
    }

    /** Envoi simple (cle null) ou dans le fil Discord de {@code cle} (voir FilsDiscord), par la file d'envoi. */
    private static void post(String url, JsonObject payload, String cle, String nomFil, boolean prioritaire,
                             Consumer<HttpResponse<String>> rappel) {
        Consumer<HttpResponse<String>> une = unique(rappel);
        if (cle == null) {
            mettreEnFile(url, prioritaire, () -> requete(url, "", payload.toString(), "application/json",
                    Duration.ofSeconds(10)).handle((r, ex) -> {
                        if (ex != null) {
                            echec(ex);
                        } else {
                            refus(r);
                        }
                        appeler(une, ex == null ? r : null);
                        return null;
                    }), une);
            return;
        }
        mettreEnFile(url, prioritaire, () -> FilsDiscord.envoyer(cle, nomFil, (params, nom) -> requete(url, params,
                avecFil(payload, nom).toString(), "application/json", Duration.ofSeconds(10)), une), une);
    }

    private static JsonObject avecFil(JsonObject payload, String nomFil) {
        if (nomFil == null) {
            return payload;
        }
        JsonObject copie = payload.deepCopy();
        copie.addProperty("thread_name", nomFil);
        return copie;
    }

    private static CompletableFuture<HttpResponse<String>> requete(String url, String params,
            String corps, String type, Duration delai) {
        return requete(url, params, HttpRequest.BodyPublishers.ofString(corps), type, delai);
    }

    private static CompletableFuture<HttpResponse<String>> requete(String url, String params,
            HttpRequest.BodyPublisher corps, String type, Duration delai) {
        String cible = url + (params.isEmpty() ? "" : (url.contains("?") ? "&" + params.substring(1) : params));
        HttpRequest req = HttpRequest.newBuilder(URI.create(cible))
                .timeout(delai)
                .header("Content-Type", type)
                .POST(corps)
                .build();
        return essayer(req, webhook(url), ESSAIS_429);
    }

    /** Envoi ; refus HTTP 429 (limite de Discord) : attente demandée, puis jusqu'à {@code restants} nouveaux essais. */
    private static CompletableFuture<HttpResponse<String>> essayer(HttpRequest req, String webhook, int restants) {
        return CLIENT.sendAsync(req, BodyHandlers.ofString()).thenCompose(r -> {
            if (r.statusCode() != 429 || restants <= 0) {
                return CompletableFuture.completedFuture(r);
            }
            long ms = attente429(r);
            PROCHAIN.merge(webhook, System.currentTimeMillis() + ms + ECART_MS, Math::max);
            Spartan.LOGGER.warn("[Spartan] Discord limite les envois (HTTP 429) : nouvel essai dans {} ms.", ms);
            return CompletableFuture.supplyAsync(() -> req, CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS))
                    .thenCompose(x -> essayer(x, webhook, restants - 1));
        });
    }

    /**
     * Attente demandée par Discord après un 429 : la plus longue de l'en-tête Retry-After et du retry_after du JSON (en
     * secondes ; une valeur énorme est lue en millisecondes, anciennes versions de l'API), entre 0,25 et 60 s.
     */
    private static long attente429(HttpResponse<String> r) {
        double s = -1;
        try {
            s = Double.parseDouble(r.headers().firstValue("Retry-After").orElse("").trim());
        } catch (Exception ignore) {
            // pas d'en-tête lisible
        }
        try {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("retry_after")) {
                double v = o.get("retry_after").getAsDouble();
                s = Math.max(s, v > 600 ? v / 1000 : v);
            }
        } catch (Exception ignore) {
            // corps sans JSON
        }
        if (Double.isNaN(s) || s < 0) {
            s = 2;
        }
        return Math.max(250L, Math.min(60_000L, (long) Math.ceil(s * 1000) + 100));
    }

    /** Code d'erreur de Discord dans le corps d'une réponse ({"code": 10003, …}) ; 0 s'il n'y en a pas. */
    static int codeErreur(String corps) {
        try {
            JsonObject o = JsonParser.parseString(corps).getAsJsonObject();
            return o.has("code") ? o.get("code").getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Le rappel n'est appelé qu'une fois, même si plusieurs chemins (réponse, échec, abandon) y mènent. */
    static Consumer<HttpResponse<String>> unique(Consumer<HttpResponse<String>> rappel) {
        if (rappel == null) {
            return null;
        }
        AtomicBoolean fait = new AtomicBoolean();
        return r -> {
            if (fait.compareAndSet(false, true)) {
                rappel.accept(r);
            }
        };
    }

    static void appeler(Consumer<HttpResponse<String>> rappel, HttpResponse<String> r) {
        if (rappel == null) {
            return;
        }
        try {
            rappel.accept(r);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Réponse Discord mal traitée : {}", e.toString());
        }
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

    /** Pièce jointe prête à poster (nom, contenu, type). */
    private record Joint(String nom, byte[] octets, String type) {
    }

    /**
     * Pièce jointe de plus de 8 Mio : compressée en .gz, ou coupée avec une note si elle reste trop grosse (Discord
     * refuse plus de 10 Mio). Appelé sur le thread d'envoi, jamais sur celui du serveur.
     */
    private static Joint joint(String fileName, byte[] file) {
        String nom = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (file.length <= JOINT_MAX) {
            return new Joint(nom, file, nom.endsWith(".jpg") ? "image/jpeg" : "application/json");
        }
        try {
            ByteArrayOutputStream gz = new ByteArrayOutputStream(file.length / 4);
            try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
                z.write(file);
            }
            if (gz.size() <= JOINT_MAX) {
                Spartan.LOGGER.info("[Spartan] Pièce jointe Discord {} compressée : {} -> {} octets.", nom, file.length,
                        gz.size());
                return new Joint(nom + ".gz", gz.toByteArray(), "application/gzip");
            }
        } catch (java.io.IOException e) {
            // coupée ci-dessous
        }
        byte[] note = ("\n\n[Spartan] Fichier coupé à 8 Mio pour Discord (" + file.length + " octets en tout) : "
                + "rapport complet sur le serveur, dans config/spartan/evidence/.\n").getBytes(StandardCharsets.UTF_8);
        byte[] coupe = new byte[JOINT_MAX];
        System.arraycopy(file, 0, coupe, 0, JOINT_MAX - note.length);
        System.arraycopy(note, 0, coupe, JOINT_MAX - note.length, note.length);
        Spartan.LOGGER.warn("[Spartan] Pièce jointe Discord {} trop grosse ({} octets) : coupée à 8 Mio.", nom,
                file.length);
        return new Joint(nom + ".coupe.txt", coupe, "text/plain; charset=utf-8");
    }

    /** Envoi avec un fichier joint (multipart/form-data, format des webhooks Discord), dans le fil de {@code cle}. */
    private static void postWithFile(String url, JsonObject payload, String fileName, byte[] file, String cle,
                                     String nomFil, Consumer<HttpResponse<String>> rappel) {
        Consumer<HttpResponse<String>> une = unique(rappel);
        mettreEnFile(url, false, () -> {
            Joint j = joint(fileName, file);
            return FilsDiscord.envoyer(cle, nomFil, (params, nom) -> {
                String boundary = "spartan" + Long.toHexString(System.nanoTime())
                        + Long.toHexString(Double.doubleToLongBits(Math.random()));
                ByteArrayOutputStream body = new ByteArrayOutputStream(j.octets().length + 4096);
                Charset utf8 = StandardCharsets.UTF_8;
                body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                        + "Content-Type: application/json\r\n\r\n").getBytes(utf8));
                body.writeBytes(avecFil(payload, nom).toString().getBytes(utf8));
                body.writeBytes(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""
                        + j.nom() + "\"\r\nContent-Type: " + j.type() + "\r\n\r\n").getBytes(utf8));
                body.writeBytes(j.octets());
                body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(utf8));
                return requete(url, params, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()),
                        "multipart/form-data; boundary=" + boundary, Duration.ofSeconds(20));
            }, une);
        }, une);
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
            JsonObject payload = embedChamps(title, color, description, fields, "Spartan AntiCheat — détection silencieuse");
            if (file != null && fileName != null) {
                postWithFile(url, payload, fileName, file, cleJoueur, nomJoueur, null);
            } else {
                post(url, payload, cleJoueur, nomJoueur, false, null);
            }
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    /**
     * Message court dans le fil Discord du joueur, sans pièce jointe (validation du jeu à la connexion…), par la file
     * d'envoi ordinaire.
     * @param fields paires {nom, valeur} déjà neutralisées par l'appelant si elles viennent du client
     */
    public static void sendJoueur(String cleJoueur, String nomJoueur, String title, int color, String description,
                                  List<String[]> fields) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            post(url, embedChamps(title, color, description, fields, "Spartan AntiCheat — validation à la connexion"),
                    cleJoueur, nomJoueur, false, null);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    /** Embed à champs (titre neutralisé, description et champs déjà prêts) ; un champ vide devient « - » (refusé sinon). */
    private static JsonObject embedChamps(String title, int color, String description, List<String[]> fields,
                                          String pied) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", Text.discord(title));
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        JsonArray arr = new JsonArray();
        for (String[] f : fields) {
            String v = f[1] == null || f[1].isBlank() ? "-" : f[1];
            arr.add(field(f[0], v.length() > 1024 ? v.substring(0, 1021) + "…" : v, false));
        }
        embed.add("fields", arr);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", pied);
        embed.add("footer", footer);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        denyMentions(payload);
        return payload;
    }

    /** Capture d'écran d'un joueur : embed avec l'image jointe, dans le fil du joueur (salon privé du staff). */
    public static void sendCapture(String cleJoueur, String nomJoueur, String raison, String fileName, byte[] jpg) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            JsonObject embed = new JsonObject();
            embed.addProperty("title", Text.discord("Capture d'écran — " + nomJoueur));
            embed.addProperty("description", Text.discord(raison));
            embed.addProperty("color", 0x3498DB);
            embed.addProperty("timestamp", Instant.now().toString());
            JsonObject image = new JsonObject();
            image.addProperty("url", "attachment://" + safeName);
            embed.add("image", image);
            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Spartan AntiCheat — capture autorisée par le joueur dans le launcher · effacée dans 3 jours");
            embed.add("footer", footer);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            denyMentions(payload);
            // effacée du salon au bout de 3 jours (CapturesDiscord)
            postWithFile(url, payload, safeName, jpg, cleJoueur, nomJoueur, CapturesDiscord::noter);
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
            post(url, payload, cleJoueur, playerName, false, null);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    /** Embed simple (bans, débans, infos staff) ; bans et débans passent devant les alertes ordinaires. */
    public static void sendInfo(String title, String description, int color) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            return;
        }
        String url = SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK);
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            boolean prioritaire = color == AlertManager.COLOR_BAN || color == AlertManager.COLOR_UNBAN;
            post(url, embedSimple(title, description, color), FilsDiscord.INFOS, FilsDiscord.NOM_INFOS, prioritaire,
                    null);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
        }
    }

    private static JsonObject embedSimple(String title, String description, int color) {
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
        return payload;
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
            post(url, payload, null, null, true, null);
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord des sanctions invalide : {}", e.getMessage());
        }
    }

    /**
     * /spartan discordtest : un message d'essai par le webhook des alertes (fil « Informations du staff ») et un par
     * celui des sanctions. Chaque résultat arrive plus tard, hors du thread du serveur : code HTTP de Discord, ou
     * {@link #NON_CONFIGURE}, {@link #DESACTIVE}, {@link #ECHEC}.
     */
    public static void tester(IntConsumer alertes, IntConsumer sanctions) {
        essai(SpartanConfig.DISCORD_WEBHOOK, "Essai de l'anticheat",
                "Message d'essai (/spartan discordtest) : les alertes du staff arrivent bien ici.",
                FilsDiscord.INFOS, FilsDiscord.NOM_INFOS, alertes);
        essai(SpartanConfig.DISCORD_SANCTIONS_WEBHOOK, "Essai",
                "Message d'essai de l'anticheat : les sanctions seront annoncées dans ce salon.", null, null, sanctions);
    }

    private static void essai(ForgeConfigSpec.ConfigValue<String> reglage, String titre, String texte, String cle,
                              String nomFil, IntConsumer resultat) {
        if (!SpartanConfig.DISCORD_ENABLED.get()) {
            resultat.accept(DESACTIVE);
            return;
        }
        String url = SpartanConfig.secret(reglage);
        if (url == null || url.isBlank()) {
            resultat.accept(NON_CONFIGURE);
            return;
        }
        try {
            post(url, embedSimple(titre, texte, AlertManager.COLOR_INFO), cle, nomFil, true,
                    r -> resultat.accept(r == null ? ECHEC : r.statusCode()));
        } catch (Exception e) {
            Spartan.LOGGER.warn("[Spartan] Webhook Discord invalide : {}", e.getMessage());
            resultat.accept(ECHEC);
        }
    }

    /** « Alertes Discord : actif ; sanctions publiques : absent ». */
    static String etat() {
        boolean on = SpartanConfig.DISCORD_ENABLED.get();
        boolean alertes = on && !SpartanConfig.secret(SpartanConfig.DISCORD_WEBHOOK).isBlank();
        boolean sanctions = on && !SpartanConfig.secret(SpartanConfig.DISCORD_SANCTIONS_WEBHOOK).isBlank();
        return "Alertes Discord : " + (alertes ? "actif" : "absent") + " ; sanctions publiques : "
                + (sanctions ? "actif" : "absent");
    }

    /**
     * Au démarrage (appelé par CapturesDiscord.demarrer) : une fois le serveur prêt, l'état des webhooks est écrit dans
     * le journal, alerts.log et le fil « Informations du staff ».
     */
    static synchronized void demarrer() {
        if (annonce) {
            return;
        }
        annonce = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartedEvent.class,
                e -> AlertManager.info(e.getServer(), "Discord", etat(), AlertManager.COLOR_INFO));
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
