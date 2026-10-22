package fr.heraliz.grades.serveur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
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
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Points Boutique (PB) : le solde du compte du site HeraliZ (un seul porte-monnaie pour le site, le launcher et le jeu).
 * Le serveur ne garde AUCUN solde : il demande au site (PbController de l'extension HeraliZ), par des requêtes signées
 * (HMAC-SHA256 de « horodatage \n méthode \n chemin \n corps », comme le lien Discord) avec la clé de
 * {@code config/heraliz_grades/boutique_site.json}, écrite par la mise en ligne (jamais dans le dépôt).
 * <p>
 * Chaque opération porte une référence unique ({@link #ref}) : le site ne la fait qu'une fois, même renvoyée, et
 * {@code statut} dit si elle a été faite (reprise après un plantage). Les réponses arrivent sur le fil serveur ;
 * {@code null} = site injoignable (rien n'est sûr : demander le statut avant de conclure).
 */
public final class Pb {
    private static final Logger LOG = LogUtils.getLogger();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final SecureRandom HASARD = new SecureRandom();
    private static final String CHEMIN = "/api/heraliz/pb/";

    private static String url, cle;
    private static MinecraftServer serveur;

    private Pb() {
    }

    public static void demarrer(MinecraftServer srv) {
        serveur = srv;
        url = null;
        cle = null;
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("boutique_site.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                String u = o.has("url") ? o.get("url").getAsString().trim() : "", c = o.has("cle") ? o.get("cle").getAsString().trim() : "";
                if (u.startsWith("https://") && c.length() >= 40) {
                    url = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
                    cle = c;
                }
            }
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible : Points Boutique désactivés ({})", f, ex.toString());
        }
        LOG.info("[grades] Points Boutique : {}", actif() ? "reliés au site" : "désactivés (clé du site absente : relancer la mise en ligne)");
    }

    public static void arreter() {
        serveur = null;
    }

    public static boolean actif() {
        return url != null && cle != null;
    }

    /** Référence unique d'une opération : « type-horodatage-hasard ». */
    public static String ref(String type) {
        byte[] b = new byte[9];
        HASARD.nextBytes(b);
        return type + "-" + Long.toString(System.currentTimeMillis(), 36) + "-" + HexFormat.of().formatHex(b);
    }

    /** Réponse du site : ok, raison du refus, champs. Null passé à la suite = site injoignable. */
    public record Reponse(int code, JsonObject corps) {
        public boolean ok() {
            return code == 200 && corps.has("ok") ? corps.get("ok").getAsBoolean() : code == 200;
        }

        public String raison() {
            return corps.has("reason") ? corps.get("reason").getAsString() : "";
        }

        public long nombre(String champ) {
            return corps.has(champ) && !corps.get(champ).isJsonNull() ? corps.get(champ).getAsLong() : -1;
        }
    }

    public static void solde(String joueur, Consumer<Reponse> suite) {
        JsonObject o = new JsonObject();
        o.addProperty("joueur", joueur);
        appeler("solde", o, suite);
    }

    public static void debiter(String ref, String joueur, long montant, String motif, Consumer<Reponse> suite) {
        appeler("debiter", mouvement(ref, joueur, montant, motif), suite);
    }

    public static void crediter(String ref, String joueur, long montant, String motif, Consumer<Reponse> suite) {
        appeler("crediter", mouvement(ref, joueur, montant, motif), suite);
    }

    /** Envoi de PB d'un joueur à l'autre : le site retire la taxe (10 %, brûlée) de ce que reçoit « vers ». */
    public static void transfert(String ref, String de, String vers, long montant, Consumer<Reponse> suite) {
        JsonObject o = new JsonObject();
        o.addProperty("ref", ref);
        o.addProperty("de", de);
        o.addProperty("vers", vers);
        o.addProperty("montant", montant);
        o.addProperty("motif", "trade");
        appeler("transfert", o, suite);
    }

    public static void statut(String ref, Consumer<Reponse> suite) {
        JsonObject o = new JsonObject();
        o.addProperty("ref", ref);
        appeler("statut", o, suite);
    }

    public static void stats(Consumer<Reponse> suite) {
        appeler("stats", new JsonObject(), suite);
    }

    private static JsonObject mouvement(String ref, String joueur, long montant, String motif) {
        JsonObject o = new JsonObject();
        o.addProperty("ref", ref);
        o.addProperty("joueur", joueur);
        o.addProperty("montant", montant);
        o.addProperty("motif", motif);
        return o;
    }

    private static String signer(long ts, String chemin, String corps) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((ts + "\nPOST\n" + chemin + "\n" + corps).getBytes(StandardCharsets.UTF_8)));
    }

    private static void appeler(String action, JsonObject corps, Consumer<Reponse> suite) {
        MinecraftServer srv = serveur;
        if (!actif() || srv == null) {
            suite.accept(null);
            return;
        }
        String chemin = CHEMIN + action, texte = corps.toString();
        try {
            long ts = System.currentTimeMillis();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + chemin)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("x-hz-horodatage", String.valueOf(ts)).header("x-hz-signature", signer(ts, chemin, texte))
                    .POST(HttpRequest.BodyPublishers.ofString(texte, StandardCharsets.UTF_8)).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((r, ex) -> {
                Reponse rep = null;
                if (ex != null) {
                    LOG.warn("[grades] Points Boutique : site injoignable ({}) pour {}", ex.toString(), action);
                } else {
                    try {
                        rep = new Reponse(r.statusCode(), JsonParser.parseString(r.body()).getAsJsonObject());
                    } catch (Exception e) {
                        LOG.warn("[grades] Points Boutique : réponse illisible ({}) pour {}", r.statusCode(), action);
                    }
                }
                Reponse fin = rep;
                if (srv == serveur) srv.execute(() -> suite.accept(fin));
                return null;
            });
        } catch (Exception ex) {
            LOG.warn("[grades] Points Boutique : requête impossible ({})", ex.toString());
            srv.execute(() -> suite.accept(null));
        }
    }
}
