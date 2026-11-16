package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plafond par joueur et par jour des objets reçus par des échanges (16/11, HeraliZ Récolte, choix du propriétaire :
 * {@code maquettes/recolte/PROPOSITION.md} §2). Le Contremaître change le minerai récolté en métal pour les bases ; pour
 * ne pas avoir des bases tout en métal en une semaine, un joueur ne reçoit pas plus de 12 fragments de métal et 3 métal
 * renforcé par jour (heure de Paris) par des échanges, tous marchands confondus (fichiers et marchands créés en jeu).
 * <ul>
 * <li>Réglages : {@code config/heraliz_quetes/plafonds_echanges.json} (objet reçu -> nombre par jour ; une clé qui
 * commence par « _ » est une note), relu avec les PNJ (démarrage, {@code /recharger quetes}). Absent : aucun plafond ;
 * illisible : les plafonds d'avant restent et l'erreur est dite au staff.</li>
 * <li>Compteurs hors du monde : {@code heraliz_quetes/echanges_du_jour.json} à la racine du serveur (le jour, puis joueur
 * -> objet -> nombre reçu), remis à zéro à minuit, heure de Paris (une horloge reculée ne rouvre pas une journée finie).
 * À chaque échange compté, le texte est fait sur le fil du serveur et écrit sur le fil des quêtes ({@link EcrituresFond} :
 * seule la dernière version en attente est écrite, et l'arrêt du serveur l'attend), avant la sauvegarde du joueur : rien
 * ne bloque le fil du serveur, rien n'est perdu.</li>
 * </ul>
 * Fil serveur seulement.
 */
final class PlafondsEchanges {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** Objet reçu -> nombre par jour au plus (vide : aucun plafond). */
    private static Map<String, Integer> plafonds = Map.of();
    /** {@code heraliz_quetes/echanges_du_jour.json} (null : serveur arrêté, rien n'est écrit). */
    private static Path fichier;
    /** Jour des compteurs (AAAA-MM-JJ, heure de Paris). */
    private static String jour = "";
    /** Joueur -> objet reçu -> nombre reçu par des échanges aujourd'hui (objets plafonnés seulement). */
    private static final Map<UUID, Map<String, Integer>> RECUS = new HashMap<>();

    private PlafondsEchanges() {
    }

    static Path reglages() {
        return FMLPaths.CONFIGDIR.get().resolve("heraliz_quetes").resolve("plafonds_echanges.json");
    }

    // ------------------------------------------------------------------ réglages
    /** Relit les plafonds (démarrage, /recharger quetes : avec les PNJ) ; chaque problème va dans {@code erreurs}. */
    static void charger(List<String> erreurs) {
        Path f = reglages();
        if (!Files.exists(f)) {
            plafonds = Map.of();
            LOG.warn("[heraliz_quetes] {} absent : aucun plafond sur les échanges", f);
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Integer> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
                if (rl == null || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
                    erreurs.add("plafonds_echanges.json : « " + e.getKey() + " » ignoré (il faut un identifiant d'objet et un nombre par jour)");
                    continue;
                }
                if (!ForgeRegistries.ITEMS.containsKey(rl)) erreurs.add("plafonds_echanges.json : objet inconnu « " + rl + " »");
                m.put(rl.toString(), Math.max(0, e.getValue().getAsInt()));
            }
            plafonds = Collections.unmodifiableMap(m);
            LOG.info("[heraliz_quetes] plafonds des échanges par joueur et par jour : {}", plafonds);
        } catch (Exception ex) {
            erreurs.add("plafonds_echanges.json illisible (" + ex.getMessage() + ") : plafonds des échanges inchangés");
        }
    }

    // ------------------------------------------------------------------ compteurs du jour
    /** Démarrage du serveur : relit les compteurs du jour ; un fichier illisible est mis de côté (jamais écrasé). */
    static void demarrer(Path racineServeur) {
        fichier = racineServeur.resolve("heraliz_quetes").resolve("echanges_du_jour.json");
        RECUS.clear();
        jour = "";
        if (EcrituresFond.occupe(fichier)) EcrituresFond.attendre();
        if (Files.exists(fichier)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                String lu = o.has("jour") && o.get("jour").isJsonPrimitive() ? o.get("jour").getAsString() : "";
                JsonObject joueurs = o.has("joueurs") && o.get("joueurs").isJsonObject() ? o.getAsJsonObject("joueurs") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    Map<String, Integer> recus = new HashMap<>();
                    for (Map.Entry<String, JsonElement> x : e.getValue().getAsJsonObject().entrySet()) {
                        if (x.getValue().isJsonPrimitive()) recus.put(x.getKey(), Math.max(0, x.getValue().getAsInt()));
                    }
                    RECUS.put(UUID.fromString(e.getKey()), recus);
                }
                jour = lu;
            } catch (Exception ex) {
                RECUS.clear();
                LOG.error("[heraliz_quetes] {} illisible ({}) : mis de côté, compteurs du jour repartis de zéro", fichier, ex.toString());
                try {
                    Files.move(fichier, fichier.resolveSibling("echanges_du_jour.json.illisible"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignore) {
                    // gardé tel quel
                }
            }
        }
        jourCourant();
    }

    static void arreter() {
        RECUS.clear();
        jour = "";
        fichier = null;
    }

    /** Minuit passé (heure de Paris) : compteurs remis à zéro ; une horloge reculée ne rouvre pas une journée finie. */
    private static void jourCourant() {
        String aujourdhui = LocalDate.now(PARIS).toString();
        if (aujourdhui.compareTo(jour) <= 0) return;
        jour = aujourdhui;
        RECUS.clear();
    }

    /** Nombre au plus de cet objet reçu par des échanges chaque jour, ou -1 s'il n'est pas plafonné. */
    static int plafond(String objet) {
        Integer max = plafonds.get(objet);
        return max == null ? -1 : max;
    }

    /** Combien ce joueur peut encore recevoir de cet objet par des échanges aujourd'hui ({@link Integer#MAX_VALUE} : sans plafond). */
    static int reste(UUID joueur, String objet) {
        Integer max = plafonds.get(objet);
        if (max == null) return Integer.MAX_VALUE;
        jourCourant();
        Map<String, Integer> recus = RECUS.get(joueur);
        return Math.max(0, max - (recus == null ? 0 : recus.getOrDefault(objet, 0)));
    }

    /** Le joueur vient de recevoir {@code n} objets par des échanges : comptés et écrits (objets plafonnés seulement). */
    static void compter(UUID joueur, String objet, int n) {
        if (n <= 0 || !plafonds.containsKey(objet)) return;
        jourCourant();
        RECUS.computeIfAbsent(joueur, k -> new HashMap<>()).merge(objet, n, Integer::sum);
        ecrire();
    }

    /** Texte fait ici (fil du serveur), écrit sur le fil des quêtes : un fichier temporaire, puis renommé. */
    private static void ecrire() {
        Path f = fichier;
        if (f == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("_note", "Objets reçus aujourd'hui par des échanges, par joueur (HeraliZ Quêtes ; plafonds : "
                + "config/heraliz_quetes/plafonds_echanges.json). Remis à zéro à minuit, heure de Paris.");
        o.addProperty("jour", jour);
        JsonObject joueurs = new JsonObject();
        for (Map.Entry<UUID, Map<String, Integer>> e : RECUS.entrySet()) {
            JsonObject recus = new JsonObject();
            for (Map.Entry<String, Integer> x : e.getValue().entrySet()) recus.addProperty(x.getKey(), x.getValue());
            joueurs.add(e.getKey().toString(), recus);
        }
        o.add("joueurs", joueurs);
        String texte = GSON.toJson(o);
        Path tmp = f.resolveSibling("echanges_du_jour.json.tmp");
        EcrituresFond.ecrire(f, () -> {
            Files.createDirectories(f.getParent());
            Files.writeString(tmp, texte, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }
}
