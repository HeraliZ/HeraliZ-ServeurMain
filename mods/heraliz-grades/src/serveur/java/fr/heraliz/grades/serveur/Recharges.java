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
 * Clé = UUID du joueur pour le kit de son grade (une seule recharge quel que soit ce grade : changer de grade ne permet pas
 * d'en prendre deux), UUID + « :soutien » pour le kit Soutien (recharge à part). Même règle que
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

    /** Clé du fichier vérifiée (UUID, éventuellement suivi de « :soutien ») ; exception sinon. */
    private static String cleValide(String cle) {
        String base = cle.endsWith(":soutien") ? cle.substring(0, cle.length() - 8) : cle;
        UUID.fromString(base);
        return cle;
    }

    static String cle(UUID uuid, boolean soutien) {
        return soutien ? uuid + ":soutien" : uuid.toString();
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

    /** Remet les recharges du joueur (grade et Soutien) à zéro ; faux s'il n'en avait pas ou si l'écriture échoue. */
    boolean effacer(UUID uuid) {
        String a = cle(uuid, false), b = cle(uuid, true);
        if (illisible || !(derniers.containsKey(a) || derniers.containsKey(b))) return false;
        Long avantA = derniers.remove(a), avantB = derniers.remove(b);
        try {
            sauver();
            return true;
        } catch (IOException ex) {
            if (avantA != null) derniers.put(a, avantA);
            if (avantB != null) derniers.put(b, avantB);
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
