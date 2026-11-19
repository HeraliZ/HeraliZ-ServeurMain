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
    /**
     * Butins d'événement fermés jusqu'à une heure réelle (ms) : numéro porté par les conteneurs (« helico#12 ») -> fin du
     * verrou. 19/11 (audit des bugs, n° 3) : le feu de l'épave de l'hélicoptère n'était gardé qu'en mémoire ; après un
     * redémarrage pendant le feu, coffre T7 et caisses T6 s'ouvraient tout de suite (plantage) ou étaient retirés (arrêt
     * propre). Gardé ici, il est rétabli au démarrage jusqu'à cette heure.
     */
    private static final Map<String, Long> VERROUS = new LinkedHashMap<>();
    private static final int VERROUS_MAX = 16;

    static void charger(Path dossier) {
        fichier = dossier.resolve("etat.json");
        numero = 0;
        OUVERTS.clear();
        RECOMPENSES.clear();
        RECOMPENSES_SET.clear();
        PROCHAINS.clear();
        VERROUS.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            numero = o.has("numero") ? o.get("numero").getAsInt() : 0;
            if (o.has("prochains")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("prochains").entrySet()) PROCHAINS.put(e.getKey(), e.getValue().getAsLong());
            }
            if (o.has("verrous") && o.get("verrous").isJsonObject()) {
                long maintenant = System.currentTimeMillis();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("verrous").entrySet()) {
                    long fin = e.getValue().getAsLong();
                    if (fin > maintenant) VERROUS.put(e.getKey(), fin);   // déjà passés : oubliés
                }
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

    /**
     * Relevé sur le fil serveur (arbre JSON neuf), écriture sur le fil d'écriture des événements (27/10, perf : appelé à
     * chaque passage du planning). Ce qui doit être sur le disque avant de continuer passe par {@link #sauverEtAttendre}.
     */
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
        JsonObject ver = new JsonObject();
        VERROUS.forEach(ver::addProperty);
        o.add("verrous", ver);
        JsonArray r = new JsonArray();
        RECOMPENSES.forEach(r::add);
        o.add("recompenses", r);
        Path f = fichier;
        EcrituresFond.ecrire(f, () -> {
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling("etat.json.tmp");
                Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                HeralizEvenements.LOG.error("[evenements] ecriture de {} impossible", f, ex);
            }
        }, null);
    }

    /** Comme {@link #sauver}, puis attend que le fichier soit écrit (ce qui suit compte sur le disque). */
    private static void sauverEtAttendre() {
        sauver();
        EcrituresFond.attendre();
    }

    /** Numéro du prochain événement (écrit tout de suite : un numéro n'est jamais redonné après un redémarrage). */
    static int suivant() {
        numero++;
        sauverEtAttendre();
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

    /** Le butin {@code id} reste fermé jusqu'à {@code finMs} (heure réelle), même après un redémarrage ; écrit tout de suite. */
    static void verrou(String id, long finMs) {
        if (id == null || id.isEmpty()) return;
        Long avant = VERROUS.put(id, finMs);
        while (VERROUS.size() > VERROUS_MAX) VERROUS.remove(VERROUS.keySet().iterator().next());
        if (avant == null || avant != finMs) sauver();
    }

    /** Fin du verrou du butin {@code id} (heure réelle en ms), 0 : aucun. */
    static long verrou(String id) {
        Long fin = VERROUS.get(id);
        return fin == null ? 0L : fin;
    }

    /** Le butin {@code id} est ouvert (feu éteint, épave partie, annulé) : verrou oublié. */
    static void oublierVerrou(String id) {
        if (id != null && VERROUS.remove(id) != null) sauver();
    }

    /** Verrous encore à venir (numéro -> fin en ms), copie : rétablis au démarrage. */
    static Map<String, Long> verrous() {
        return new LinkedHashMap<>(VERROUS);
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
        sauverEtAttendre();
        return true;
    }

    private static void ajouterRecompense(String id) {
        if (!RECOMPENSES_SET.add(id)) return;
        RECOMPENSES.addLast(id);
        while (RECOMPENSES.size() > GARDES) RECOMPENSES_SET.remove(RECOMPENSES.removeFirst());
    }
}
