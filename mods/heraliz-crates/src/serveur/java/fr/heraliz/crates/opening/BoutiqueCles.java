package fr.heraliz.crates.opening;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Caisses dont la boutique en jeu (/boutique de HeraliZ Grades) vend la clé (Caisses 2.22.0) : leur écran montre
 * « + Acheter » et « + Acheter des clés », qui ouvrent /boutique sur l'onglet Clés. Lu dans le fichier des articles de la
 * boutique (config/heraliz_grades/boutique.json : catégorie « cles », « groupe » = la caisse), relu au plus toutes les
 * 10 s s'il a changé ; sans HeraliZ Grades ou sans fichier, aucune caisse n'est vendue (pas de bouton).
 */
final class BoutiqueCles {
    private BoutiqueCles() {
    }

    private static final long RELECTURE_MS = 10_000;
    private static Set<String> vendues = Set.of();
    private static long lu = Long.MIN_VALUE / 2, date = -1;

    static synchronized boolean vendue(CrateType type) {
        long maintenant = System.currentTimeMillis();
        if (maintenant - lu >= RELECTURE_MS) {
            lu = maintenant;
            relire();
        }
        return vendues.contains(type.id);
    }

    private static void relire() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("boutique.json");
        try {
            if (ModList.get() == null || !ModList.get().isLoaded("heraliz_grades") || !Files.isRegularFile(f)) {
                vendues = Set.of();
                date = -1;
                return;
            }
            long d = Files.getLastModifiedTime(f).toMillis();
            if (d == date) return;
            Set<String> l = new HashSet<>();
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root.has("articles")) {
                    for (JsonElement e : root.getAsJsonArray("articles")) {
                        if (!e.isJsonObject()) continue;
                        JsonObject a = e.getAsJsonObject();
                        String cat = a.has("categorie") ? a.get("categorie").getAsString() : "";
                        if (!"cles".equals(cat) || !a.has("groupe")) continue;
                        CrateType t = CrateType.byId(a.get("groupe").getAsString().toLowerCase(Locale.ROOT));
                        if (t != null) l.add(t.id);
                    }
                }
            }
            vendues = Set.copyOf(l);
            date = d;
        } catch (Exception ex) {
            HeralizCrates.LOGGER.warn("[HeralizCrates] boutique.json illisible ({}) : aucun bouton « Acheter »", ex.toString());
            vendues = Set.of();
            date = -1;
        }
    }
}
