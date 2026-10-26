package fr.heraliz.quetes.serveur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Les PNJ et leurs quêtes, lus dans {@code config/heraliz_quetes/pnj/*.json} au démarrage et à /quete recharger. Un
 * fichier illisible est sauté (avec la raison dans le journal et dans la réponse de la commande) : les autres restent.
 */
final class Catalogue {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile Map<String, Defs.Pnj> pnj = Map.of();

    private Catalogue() {
    }

    static Path dossier() {
        return FMLPaths.CONFIGDIR.get().resolve("heraliz_quetes").resolve("pnj");
    }

    /** Relit tous les fichiers ; rend la liste des problèmes trouvés (vide = tout va bien). */
    static List<String> charger() {
        List<String> erreurs = new ArrayList<>();
        Map<String, Defs.Pnj> m = new LinkedHashMap<>();
        Path d = dossier();
        try {
            Files.createDirectories(d);
        } catch (IOException ex) {
            erreurs.add("dossier " + d + " : " + ex.getMessage());
        }
        List<Path> fichiers = new ArrayList<>();
        try (Stream<Path> s = Files.list(d)) {
            s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().forEach(fichiers::add);
        } catch (IOException ex) {
            erreurs.add("lecture de " + d + " : " + ex.getMessage());
        }
        for (Path f : fichiers) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                Defs.Pnj p = Defs.pnj(o, erreurs);
                if (p.id.isEmpty()) {
                    erreurs.add(f.getFileName() + " : pas d'« id »");
                    continue;
                }
                if (m.containsKey(p.id)) erreurs.add(f.getFileName() + " : le PNJ « " + p.id + " » existe déjà");
                m.put(p.id, p);
            } catch (Exception ex) {
                erreurs.add(f.getFileName() + " : " + ex.getMessage());
            }
        }
        EditionMarchands.appliquer(m, erreurs);
        Recyclage.charger(erreurs);
        pnj = Map.copyOf(m);
        Marchands.oublierCache();
        LOG.info("[heraliz_quetes] {} PNJ chargés ({} quêtes de suite, {} modèles de contrats){}", m.size(),
                m.values().stream().mapToInt(p -> p.suite.size()).sum(), m.values().stream().mapToInt(p -> p.contrats.size()).sum(),
                erreurs.isEmpty() ? "" : " ; " + erreurs.size() + " problème(s)");
        for (String e : erreurs) LOG.warn("[heraliz_quetes] {}", e);
        return erreurs;
    }

    static Defs.Pnj pnj(String id) {
        return id == null ? null : pnj.get(id);
    }

    static Collection<Defs.Pnj> tous() {
        return pnj.values();
    }
}
