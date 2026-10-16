package fr.heralisz.matchs.zombies.jeu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recettes des etablis (ZOMBIES_V2_SPEC §2), payees en materiaux de recuperation. Source :
 * {@code data/heralisz_matchs/zombies/recettes.json} ({@code {"recettes": [{id, cout, type, objet?, nombre?, max}]}}),
 * sinon les recettes par defaut du contrat (grenade 3, grenade collante 5, plaque d'armure 4, kit d'auto-reanimation 10).
 * Types : {@code grenade} (objet en case grenades), {@code tactique} (objet en case tactique), {@code plaque} (plaque
 * d'armure), {@code auto_reanimation} (kit), {@code conversion} (l'arme {@code de} tenue devient l'arme {@code objet} : niveau
 * de Pack-a-Punch, camo et accessoires gardés, ex. kit Acid Gat : Blundergat -> Acid Gat). Noms :
 * {@code zombies.jeu.recette.<id>} et {@code .desc}.
 */
final class Recettes {
    private Recettes() {
    }

    /** Une recette ({@code max} = plafond de ce que le joueur peut porter, 0 = sans plafond). */
    record Recette(String id, int cout, String type, String objet, int nombre, int max, String de) {
        Recette(String id, int cout, String type, String objet, int nombre, int max) {
            this(id, cout, type, objet, nombre, max, "");
        }
    }

    private static final List<Recette> DEFAUT = List.of(
            new Recette("grenade", 3, "grenade", "heralisz_grenade:m67", 1, 4),
            new Recette("plaque_armure", 4, "plaque", "", 1, 3),
            new Recette("auto_reanimation", 10, "auto_reanimation", "", 1, 1));

    private static List<Recette> cache;

    static synchronized void invalider() {
        cache = null;
    }

    /** Recettes en vigueur (ordre du fichier). */
    static synchronized List<Recette> toutes() {
        if (cache != null) return cache;
        List<Recette> l = new ArrayList<>();
        JsonObject cfg = ConfigZombies.recettes();
        JsonElement arr = cfg == null ? null : cfg.get("recettes");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement e : arr.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                try {
                    String id = o.get("id").getAsString();
                    String type = o.has("type") ? o.get("type").getAsString().toLowerCase(Locale.ROOT) : "grenade";
                    l.add(new Recette(id, Math.max(0, o.has("cout") ? o.get("cout").getAsInt() : 3), type,
                            o.has("objet") ? o.get("objet").getAsString() : "", o.has("nombre") ? Math.max(1, o.get("nombre").getAsInt()) : 1,
                            o.has("max") ? Math.max(0, o.get("max").getAsInt()) : 0, o.has("de") ? o.get("de").getAsString() : ""));
                } catch (RuntimeException ex) {
                    HeraliszMatchs.LOG.warn("[zombies] recette illisible ignoree : {}", o);
                }
            }
        }
        cache = l.isEmpty() ? DEFAUT : List.copyOf(l);
        return cache;
    }

    /** Recette par id, ou null. */
    static Recette par(String id) {
        for (Recette r : toutes()) if (r.id().equals(id)) return r;
        return null;
    }
}
