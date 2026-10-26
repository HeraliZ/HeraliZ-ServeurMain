package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.evenements.HeralizEvenements;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mémoire des événements, hors du monde : {@code <serveur>/heraliz_evenements/etat.json}. Numéro du dernier événement,
 * événements ouverts (coffre pas encore ouvert : rapporte-t-il des points, dans quel secteur — gardé pour qu'une caisse
 * ouverte après un redémarrage donne encore ses points), et événements déjà récompensés : la ligne « récompensé » est
 * écrite sur le disque AVANT les points, et un événement récompensé ne l'est plus jamais (docs/EVENEMENTS.md).
 */
final class Etat {
    private Etat() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int GARDES = 500;

    /** Un événement lancé dont le coffre n'est pas encore ouvert. */
    record Ouvert(boolean points, String secteur) {
    }

    @Nullable
    private static Path fichier;
    private static int numero;
    private static final Map<String, Ouvert> OUVERTS = new LinkedHashMap<>();
    private static final Deque<String> RECOMPENSES = new ArrayDeque<>();
    private static final Set<String> RECOMPENSES_SET = new HashSet<>();
    /** Prochain départ des événements « toutes les … » du planning (heure en ms) : un redémarrage ne remet pas l'attente à zéro. */
    private static final Map<String, Long> PROCHAINS = new LinkedHashMap<>();

    static void charger(Path dossier) {
        fichier = dossier.resolve("etat.json");
        numero = 0;
        OUVERTS.clear();
        RECOMPENSES.clear();
        RECOMPENSES_SET.clear();
        PROCHAINS.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            numero = o.has("numero") ? o.get("numero").getAsInt() : 0;
            if (o.has("prochains")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("prochains").entrySet()) PROCHAINS.put(e.getKey(), e.getValue().getAsLong());
            }
            if (o.has("ouverts")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ouverts").entrySet()) {
                    JsonObject x = e.getValue().getAsJsonObject();
                    OUVERTS.put(e.getKey(), new Ouvert(x.get("points").getAsBoolean(), x.get("secteur").getAsString()));
                }
            }
            if (o.has("recompenses")) for (JsonElement e : o.getAsJsonArray("recompenses")) ajouterRecompense(e.getAsString());
        } catch (IOException | RuntimeException ex) {
            // jamais écrasé : copie à côté (sinon un événement déjà récompensé pourrait l'être deux fois)
            Path copie = fichier.resolveSibling("etat.json.illisible-" + System.currentTimeMillis());
            try {
                Files.copy(fichier, copie, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // rien de plus à faire
            }
            numero = (int) (System.currentTimeMillis() / 60_000L % 1_000_000L);   // numéros neufs : jamais ceux d'avant
            HeralizEvenements.LOG.error("[evenements] {} illisible (copie : {})", fichier, copie, ex);
        }
    }

    static void sauver() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("numero", numero);
        JsonObject ouv = new JsonObject();
        for (Map.Entry<String, Ouvert> e : OUVERTS.entrySet()) {
            JsonObject x = new JsonObject();
            x.addProperty("points", e.getValue().points());
            x.addProperty("secteur", e.getValue().secteur());
            ouv.add(e.getKey(), x);
        }
        o.add("ouverts", ouv);
        JsonObject pro = new JsonObject();
        PROCHAINS.forEach(pro::addProperty);
        o.add("prochains", pro);
        JsonArray r = new JsonArray();
        RECOMPENSES.forEach(r::add);
        o.add("recompenses", r);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("etat.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            HeralizEvenements.LOG.error("[evenements] ecriture de {} impossible", fichier, ex);
        }
    }

    /** Numéro du prochain événement (écrit tout de suite). */
    static int suivant() {
        numero++;
        sauver();
        return numero;
    }

    /** Prochain départ prévu d'un événement « toutes les … » (0 : aucun). */
    static long prochain(String evenement) {
        return PROCHAINS.getOrDefault(evenement, 0L);
    }

    static void prochain(String evenement, long ms) {
        PROCHAINS.put(evenement, ms);
        sauver();
    }

    static void ouvrir(String id, boolean points, String secteur) {
        OUVERTS.put(id, new Ouvert(points, secteur));
        while (OUVERTS.size() > 64) OUVERTS.remove(OUVERTS.keySet().iterator().next());
        sauver();
    }

    @Nullable
    static Ouvert ouvert(String id) {
        return OUVERTS.get(id);
    }

    static void fermer(String id) {
        if (OUVERTS.remove(id) != null) sauver();
    }

    /**
     * Marque l'événement comme récompensé, sur le disque, AVANT de donner quoi que ce soit. Faux s'il l'était déjà : rien
     * ne doit être donné.
     */
    static boolean recompenser(String id) {
        if (RECOMPENSES_SET.contains(id)) return false;
        ajouterRecompense(id);
        OUVERTS.remove(id);
        sauver();
        return true;
    }

    private static void ajouterRecompense(String id) {
        if (!RECOMPENSES_SET.add(id)) return;
        RECOMPENSES.addLast(id);
        while (RECOMPENSES.size() > GARDES) RECOMPENSES_SET.remove(RECOMPENSES.removeFirst());
    }
}
