package fr.heraliz.grades.serveur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Lien Discord ↔ Minecraft avec HeraliBot.
 * <ul>
 *    <li>{@code /lier} (joueur connecté sur AuthMe) : code de 6 caractères, valable 5 minutes, envoyé au bot ; le joueur le
 *    tape sur Discord dans le salon #lien ({@code /lier <code>}). Le serveur demande ensuite au bot si le code a servi ;</li>
 *    <li>{@code /delier} : retire le lien ;</li>
 *    <li>les changements de grade et de kits faits à la console sont annoncés au staff sur Discord.</li>
 * </ul>
 * Adresse du bot et clé dans {@code config/heraliz_grades/lien.json}, remplis par la mise en ligne depuis les secrets
 * GitHub (HERALIBOT_LIEN_URL, HERALIBOT_LIEN_CLE) : jamais dans le dépôt. Chaque requête est signée (HMAC-SHA256 sur
 * l'heure, la méthode, le chemin et le contenu) ; le bot refuse toute requête non signée, trop vieille ou rejouée.
 * Les appels réseau ne bloquent jamais le serveur (fil à part, réponses traitées sur le fil serveur).
 */
final class LienDiscord {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long VALIDITE_MS = 5 * 60 * 1000, ATTENTE_MS = 30 * 1000;
    private static final SecureRandom HASARD = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private record Attente(String code, long expire) {
    }

    private static String url, cle;
    private static MinecraftServer serveur;
    private static final Map<UUID, Attente> CODES = new HashMap<>();
    private static final Map<UUID, Long> DERNIER = new HashMap<>();
    /** Joueurs qui portent le tag HeraliZ sur Discord (rôle Soutien, compte lié), d'après HeraliBot. */
    private static final Map<UUID, Boolean> SOUTIENS = new HashMap<>();

    private LienDiscord() {
    }

    static void demarrer(MinecraftServer srv) {
        serveur = srv;
        url = null;
        cle = null;
        CODES.clear();
        DERNIER.clear();
        SOUTIENS.clear();
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("lien.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                String u = o.has("url") ? o.get("url").getAsString().trim() : "", c = o.has("cle") ? o.get("cle").getAsString().trim() : "";
                if ((u.startsWith("http://") || u.startsWith("https://")) && c.length() >= 40 && !c.startsWith("__")) {
                    url = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
                    cle = c;
                }
            }
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible : lien Discord désactivé ({})", f, ex.toString());
        }
        LOG.info("[grades] lien Discord : {}", actif() ? "actif" : "désactivé (secrets HERALIBOT_LIEN_URL / HERALIBOT_LIEN_CLE absents)");
    }

    static void arreter() {
        serveur = null;
        SOUTIENS.clear();
        CODES.clear();
        DERNIER.clear();
    }

    static void oublier(UUID uuid) {
        CODES.remove(uuid);
        SOUTIENS.remove(uuid);
    }

    /** Vrai si le joueur porte le tag HeraliZ sur Discord (dernière réponse de HeraliBot). */
    static boolean estSoutien(UUID uuid) {
        return Boolean.TRUE.equals(SOUTIENS.get(uuid));
    }

    /** Redemande à HeraliBot si le joueur porte le tag, puis lance la suite (sur le fil serveur, même si le bot ne répond pas). */
    static void verifierSoutien(UUID uuid, Runnable suite) {
        if (!actif()) {
            suite.run();
            return;
        }
        appeler("GET", "/mc/soutien/" + uuid, null, r -> {
            if (r != null && r.has("soutien")) SOUTIENS.put(uuid, r.get("soutien").getAsBoolean());
            suite.run();
        });
    }

    static boolean actif() {
        return url != null && cle != null;
    }

    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.lien." + cle, fr, args);
    }

    // ------------------------------------------------------------------ HTTP signé
    private static String signer(long ts, String methode, String chemin, String corps) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((ts + "\n" + methode + "\n" + chemin + "\n" + corps).getBytes(StandardCharsets.UTF_8)));
    }

    /** Appel au bot ; la suite (réponse JSON, ou null en cas d'échec) est exécutée sur le fil serveur. */
    private static void appeler(String methode, String chemin, JsonObject corps, Consumer<JsonObject> suite) {
        if (!actif()) {
            suite.accept(null);
            return;
        }
        MinecraftServer srv = serveur;
        String texte = corps == null ? "" : corps.toString();
        CompletableFuture<JsonObject> f;
        try {
            long ts = System.currentTimeMillis();
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + chemin)).timeout(Duration.ofSeconds(8))
                    .header("x-hz-horodatage", String.valueOf(ts)).header("x-hz-signature", signer(ts, methode, chemin, texte));
            b = methode.equals("POST") ? b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(texte, StandardCharsets.UTF_8)) : b.GET();
            f = HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
                if (r.statusCode() != 200) {
                    LOG.warn("[grades] lien Discord : {} {} -> {}", methode, chemin.replaceAll("/[A-Z0-9]{6}$", "/…"), r.statusCode());
                    return null;
                }
                return JsonParser.parseString(r.body()).getAsJsonObject();
            });
        } catch (Exception ex) {
            f = CompletableFuture.failedFuture(ex);
        }
        f.exceptionally(ex -> {
            LOG.warn("[grades] lien Discord injoignable : {}", ex.toString());
            return null;
        }).thenAccept(r -> {
            if (srv != null && srv == serveur) srv.execute(() -> suite.accept(r));
        });
    }

    /** Annonce au staff sur Discord (rien si le lien n'est pas configuré). */
    static void evenement(String texte) {
        if (!actif()) return;
        JsonObject o = new JsonObject();
        o.addProperty("texte", texte);
        appeler("POST", "/mc/evenement", o, r -> {
        });
    }

    // ------------------------------------------------------------------ commandes
    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lier").executes(c -> {
            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
            lier(p);
            return 1;
        }));
        d.register(Commands.literal("delier").executes(c -> {
            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
            delier(p);
            return 1;
        }));
    }

    private static boolean preambule(ServerPlayer p) {
        if (!PontBukkit.authentifie(p)) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login."));
            return false;
        }
        if (!actif()) {
            p.sendSystemMessage(msg("indisponible", "Le lien avec Discord n'est pas disponible pour le moment."));
            return false;
        }
        long maintenant = System.currentTimeMillis();
        Long avant = DERNIER.get(p.getUUID());
        if (avant != null && maintenant - avant < ATTENTE_MS) {
            p.sendSystemMessage(msg("attends", "Attends un peu avant de recommencer."));
            return false;
        }
        DERNIER.put(p.getUUID(), maintenant);
        return true;
    }

    private static void lier(ServerPlayer p) {
        if (!preambule(p)) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(HASARD.nextInt(ALPHABET.length())));
        String code = sb.toString();
        UUID uuid = p.getUUID();
        JsonObject o = new JsonObject();
        o.addProperty("code", code);
        o.addProperty("uuid", uuid.toString());
        o.addProperty("nom", p.getGameProfile().getName());
        appeler("POST", "/mc/code", o, r -> {
            ServerPlayer joueur = serveur == null ? null : serveur.getPlayerList().getPlayer(uuid);
            if (joueur == null) return;
            if (r == null) {
                joueur.sendSystemMessage(msg("erreur", "Impossible de joindre Discord, réessaie dans un moment."));
                return;
            }
            CODES.put(uuid, new Attente(code, System.currentTimeMillis() + VALIDITE_MS));
            MutableComponent c = Component.literal(code).withStyle(st -> st.withBold(true).withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, msg("copier", "Cliquer pour copier"))));
            joueur.sendSystemMessage(msg("code", "Ton code : %1$s. Sur Discord, dans le salon #lien, tape /lier %2$s (valable 5 minutes).", c, code));
        });
    }

    private static void delier(ServerPlayer p) {
        if (!preambule(p)) return;
        UUID uuid = p.getUUID();
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        appeler("POST", "/mc/delier", o, r -> {
            ServerPlayer joueur = serveur == null ? null : serveur.getPlayerList().getPlayer(uuid);
            if (joueur == null) return;
            if (r == null) joueur.sendSystemMessage(msg("erreur", "Impossible de joindre Discord, réessaie dans un moment."));
            else if (r.has("avait") && r.get("avait").getAsBoolean()) joueur.sendSystemMessage(msg("delie", "Ton compte n'est plus lié à Discord."));
            else joueur.sendSystemMessage(msg("pas_lie", "Ton compte n'était lié à aucun Discord."));
        });
    }

    /** Toutes les 5 s : demande au bot si les codes en attente ont servi (et oublie ceux qui ont expiré). */
    static void suivre() {
        if (CODES.isEmpty() || serveur == null) return;
        long maintenant = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Attente>> it = CODES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Attente> e = it.next();
            UUID uuid = e.getKey();
            Attente a = e.getValue();
            if (maintenant > a.expire() + 10_000) {
                it.remove();
                ServerPlayer p = serveur.getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(msg("expire", "Ton code a expiré : refais /lier pour en avoir un nouveau."));
                continue;
            }
            appeler("GET", "/mc/code/" + a.code(), null, r -> {
                if (r == null || !r.has("etat") || !a.equals(CODES.get(uuid))) return;
                String etat = r.get("etat").getAsString();
                if (etat.equals("attente")) return;
                CODES.remove(uuid);
                ServerPlayer p = serveur == null ? null : serveur.getPlayerList().getPlayer(uuid);
                if (p == null) return;
                if (etat.equals("lie")) {
                    String tag = r.has("discord") && !r.get("discord").isJsonNull() ? r.get("discord").getAsString() : "?";
                    p.sendSystemMessage(msg("lie", "Ton compte est maintenant lié au Discord %s.", tag));
                } else {
                    p.sendSystemMessage(msg("expire", "Ton code a expiré : refais /lier pour en avoir un nouveau."));
                }
            });
        }
    }
}
