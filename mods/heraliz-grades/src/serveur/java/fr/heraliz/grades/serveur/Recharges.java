package fr.heraliz.grades.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dernier kit pris par chaque joueur (heure en ms) : {@code heraliz_grades/recharges.json} à la racine du serveur.
 * Clé : voir {@link #cle} (une seule recharge par kit de Survivant ou de la boutique quel que soit le grade : changer de
 * grade ne permet pas d'en prendre deux). Même règle que
 * le registre des grades : un fichier illisible n'est jamais écrasé et plus aucun kit ne se prend tant qu'il l'est.
 */
final class Recharges {
    private static final Logger LOG = LogUtils.getLogger();

    private final Path fichier;
    private final Map<String, Long> derniers = new HashMap<>();
    private boolean illisible;

    Recharges(Path dossier) {
        this.fichier = dossier.resolve("recharges.json");
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) derniers.put(cleValide(e.getKey()), e.getValue().getAsLong());
        } catch (Exception ex) {
            derniers.clear();
            illisible = true;
            LOG.error("[grades] {} ILLISIBLE ({}) : aucun kit ne peut être pris tant qu'il n'est pas réparé. Le fichier n'est pas touché.", fichier, ex.toString());
        }
    }

    /** Clé du fichier vérifiée (UUID, éventuellement suivi de « :… ») ; exception sinon. */
    private static String cleValide(String cle) {
        int i = cle.indexOf(':');
        UUID.fromString(i < 0 ? cle : cle.substring(0, i));
        return cle;
    }

    /**
     * Clé d'un kit (2.0.0) : groupe « base » (kits de Survivant et de la boutique, une seule recharge quel que soit le
     * grade), « soutien », ou l'identifiant d'un grade du staff ou spécial. Le kit « kit » des groupes base et soutien garde
     * la clé d'avant la 2.0.0 (UUID, UUID:soutien) : les recharges en cours sont gardées.
     */
    static String cle(UUID uuid, String groupe, String kit) {
        if ("kit".equals(kit) && "base".equals(groupe)) return uuid.toString();
        if ("kit".equals(kit) && "soutien".equals(groupe)) return uuid + ":soutien";
        return uuid + ":" + groupe + ":" + kit;
    }

    boolean illisible() {
        return illisible;
    }

    /** Heure du dernier kit pris (0 = jamais). */
    long dernier(String cle) {
        return derniers.getOrDefault(cle, 0L);
    }

    /** Note un kit pris et l'écrit tout de suite ; faux (et rien de changé) si l'écriture échoue. */
    boolean noter(String cle, long quand) {
        if (illisible) return false;
        Long avant = derniers.put(cle, quand);
        try {
            sauver();
            return true;
        } catch (IOException ex) {
            if (avant == null) derniers.remove(cle);
            else derniers.put(cle, avant);
            LOG.error("[grades] écriture de {} impossible : {}", fichier, ex.toString());
            return false;
        }
    }

    /** Remet toutes les recharges de kits du joueur à zéro ; faux s'il n'en avait pas ou si l'écriture échoue. */
    boolean effacer(UUID uuid) {
        String u = uuid.toString();
        Map<String, Long> avant = new HashMap<>();
        derniers.forEach((c, t) -> {
            if (c.equals(u) || c.startsWith(u + ":")) avant.put(c, t);
        });
        if (illisible || avant.isEmpty()) return false;
        avant.keySet().forEach(derniers::remove);
        try {
            sauver();
            return true;
        } catch (IOException ex) {
            derniers.putAll(avant);
            LOG.error("[grades] écriture de {} impossible : {}", fichier, ex.toString());
            return false;
        }
    }

    private void sauver() throws IOException {
        JsonObject o = new JsonObject();
        derniers.forEach((c, t) -> o.addProperty(c, t));
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling("recharges.json.tmp");
        Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomiqueImpossible) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
