package fr.heralisz.camo.client.kit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Approches des lettres (Tactique 2.35.1) : de combien l'encre d'une lettre est en retrait de son avancée, à gauche et à
 * droite, pour centrer l'ENCRE d'un texte dans sa case (et non sa boîte d'avancée, décalée par ces retraits). Table
 * {@code assets/heralisz_camo/ui/encre.json} ÉCRITE par {@code outils/apercu_table_armurier.py --encre} depuis les TTF du
 * mod (valeurs pour une police de taille 1, multipliées par la taille de la police). Table absente : retraits nuls.
 */
public final class Encre {
    private Encre() {
    }

    private static Map<Integer, float[]> moyen, gras;

    private static void charger() {
        if (moyen != null) return;
        moyen = new HashMap<>();
        gras = new HashMap<>();
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("heralisz_camo", "ui/encre.json"));
            if (r.isEmpty()) return;
            try (Reader in = r.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                lire(o.getAsJsonObject("medium"), moyen);
                lire(o.getAsJsonObject("bold"), gras);
            }
        } catch (Exception ex) {
            moyen.clear();
            gras.clear();
        }
    }

    private static void lire(JsonObject o, Map<Integer, float[]> m) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            var a = e.getValue().getAsJsonArray();
            m.put(e.getKey().codePointAt(0), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.size() > 2 ? a.get(2).getAsFloat() : 0f});
        }
    }

    /** Taille de la police (en points d'interface) ; 0 : pas une police du kit (aucun retrait). */
    static float taille(ResourceLocation f) {
        if (!"heralisz_camo".equals(f.getNamespace())) return 0f;
        return switch (f.getPath()) {
            case "ui", "ui_bold" -> 11f;
            case "ui_small" -> 9f;
            case "ui_gros" -> 16f;
            case "ui_nom" -> 14f;
            case "ui_mini" -> 8f;
            default -> 0f;
        };
    }

    private static boolean grasse(ResourceLocation f) {
        String p = f.getPath();
        return p.equals("ui_bold") || p.equals("ui_gros") || p.equals("ui_nom");
    }

    /**
     * {retrait gauche de la 1re lettre, retrait droit de la dernière}, en points d'interface, pour un texte dessiné d'un
     * bloc : Minecraft compte deux fois l'approche gauche de chaque lettre (bearingX), la table en tient compte.
     */
    public static float[] de(String s, ResourceLocation f) {
        return retraits(s, f, false);
    }

    /** Idem pour des capitales espacées ({@link Ui#spaced}, qui retire la 2e approche lettre par lettre). */
    public static float[] deCapitales(String s, ResourceLocation f) {
        return retraits(s, f, true);
    }

    /** Approche gauche (une fois) d'une lettre, en points d'interface : Ui.spaced recule la lettre d'autant. */
    public static float approche(int cp, ResourceLocation f) {
        float t = taille(f);
        if (t == 0f) return 0f;
        charger();
        float[] a = (grasse(f) ? gras : moyen).get(cp);
        return a == null ? 0f : a[2] * t;
    }

    private static float[] retraits(String s, ResourceLocation f, boolean capitales) {
        float t = taille(f);
        String u = s.strip();
        if (t == 0f || u.isEmpty()) return new float[]{0f, 0f};
        charger();
        Map<Integer, float[]> m = grasse(f) ? gras : moyen;
        float[] a = m.get(u.codePointAt(0)), b = m.get(u.codePointBefore(u.length()));
        float g = a == null ? 0f : (capitales ? a[0] - a[2] : a[0]) * t;
        float d = b == null ? 0f : (capitales ? b[1] + b[2] : b[1]) * t;
        return new float[]{g, d};
    }
}
