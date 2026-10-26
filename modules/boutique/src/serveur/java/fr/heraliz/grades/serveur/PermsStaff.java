package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.staff.ActionStaff;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ce que chaque grade du staff peut faire dans le panel (onglet Permissions, Fondateur seulement), gardé à vie hors du
 * monde : {@code heraliz_grades/panel_staff.json} (jamais touché par la mise en ligne). Une action absente du fichier
 * garde sa valeur par défaut ({@link ActionStaff#rangDefaut}). Le Fondateur a toujours tout ; une action verrouillée
 * (donner un grade) n'est jamais réglable. Fichier illisible : valeurs par défaut, fichier laissé tel quel tant que rien
 * ne change. Fil serveur seulement.
 */
final class PermsStaff {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Grade[] PAR_RANG = {null, Grade.GUIDE, Grade.HELPER, Grade.MODERATEUR, Grade.ADMIN, Grade.FONDATEUR};
    private static final Map<ActionStaff, Integer> MASQUES = new EnumMap<>(ActionStaff.class);
    private static Path fichier;

    private PermsStaff() {
    }

    static void demarrer(Path dossier) {
        fichier = dossier.resolve("panel_staff.json");
        MASQUES.clear();
        for (ActionStaff a : ActionStaff.values()) MASQUES.put(a, a.masqueDefaut());
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("actions");
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                ActionStaff a = ActionStaff.parId(e.getKey());
                if (a == null || a.verrou) continue;
                int m = 0;
                for (JsonElement g : e.getValue().getAsJsonArray()) {
                    for (int r = 1; r < PAR_RANG.length; r++) if (PAR_RANG[r].id.equals(g.getAsString())) m |= 1 << r;
                }
                MASQUES.put(a, m | 1 << ActionStaff.RANG_MAX);
            }
            LOG.info("[grades] permissions du panel du staff lues depuis {}", fichier);
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : permissions par défaut (le fichier n'est pas touché tant qu'on ne change rien)", fichier, ex.toString());
        }
    }

    static void arreter() {
        fichier = null;
    }

    static int masque(ActionStaff a) {
        Integer m = MASQUES.get(a);
        return m != null ? m : a.masqueDefaut();
    }

    /** Ce rang (1 Guide … 5 Fondateur) a l'action. */
    static boolean permis(ActionStaff a, int rang) {
        if (rang <= 0 || rang > ActionStaff.RANG_MAX) return false;
        if (a.verrou) return rang == ActionStaff.RANG_MAX;
        return (masque(a) & 1 << rang) != 0;
    }

    /** Coche ou décoche une case (rang 1 à 4 : le Fondateur a toujours tout) ; faux si refusé ou non enregistré. */
    static boolean changer(ActionStaff a, int rang, boolean permis) {
        if (a.verrou || rang < 1 || rang >= ActionStaff.RANG_MAX) return false;
        int avant = masque(a), apres = permis ? avant | 1 << rang : avant & ~(1 << rang);
        if (apres == avant) return true;
        MASQUES.put(a, apres);
        if (sauver()) return true;
        MASQUES.put(a, avant);
        return false;
    }

    static Map<String, Integer> tous() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ActionStaff a : ActionStaff.values()) m.put(a.id, masque(a));
        return m;
    }

    static String nomRang(int rang) {
        return rang > 0 && rang < PAR_RANG.length ? PAR_RANG[rang].id : "";
    }

    private static boolean sauver() {
        if (fichier == null) return false;
        JsonObject actions = new JsonObject();
        for (ActionStaff a : ActionStaff.values()) {
            if (a.verrou) continue;
            JsonArray l = new JsonArray();
            for (int r = 1; r < PAR_RANG.length; r++) if ((masque(a) & 1 << r) != 0) l.add(PAR_RANG[r].id);
            actions.add(a.id, l);
        }
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.add("actions", actions);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("panel_staff.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomiqueImpossible) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            LOG.error("[grades] {} impossible à écrire : {}", fichier, ex.toString());
            return false;
        }
    }
}
