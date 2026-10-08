package fr.heraliz.interfaces.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Contenu de l'ecran Informations : config/heraliz_interface/informations.json (cree au premier besoin a partir de
 * assets/heraliz_interface/informations_defaut.json du jar), relu a chaque ouverture de l'ecran. Chaque texte existe
 * en « fr » et « en » ; la langue du jeu choisit, l'autre sert de secours. Un fichier illisible ne bloque rien : le
 * texte d'origine du jar est affiche et l'erreur va au journal.
 */
public final class Informations {
    private Informations() {
    }

    public record Chiffre(String valeur, String legende) {
    }

    public record Repere(String annee, String titre) {
    }

    public record Commande(String commande, String touche, String description) {
    }

    public record Contenu(List<Chiffre> chiffres, List<Repere> reperes, List<String> histoire, List<Commande> commandes,
                          List<String> regles, String noteRegles) {
    }

    private static final String DEFAUT = "/assets/" + HeralizInterface.MODID + "/informations_defaut.json";

    public static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve(HeralizInterface.MODID).resolve("informations.json");
    }

    /** Lit le contenu (fichier de config, sinon texte d'origine). */
    public static Contenu charger() {
        boolean fr = Ui.french();
        Path f = fichier();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try (InputStream in = Informations.class.getResourceAsStream(DEFAUT)) {
                    if (in != null) Files.write(f, in.readAllBytes());
                }
            }
            if (Files.exists(f)) return lire(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject(), fr);
        } catch (Exception ex) {
            HeralizInterface.LOG.warn("[interface] {} illisible, texte d'origine affiche : {}", f, ex.toString());
        }
        try (InputStream in = Informations.class.getResourceAsStream(DEFAUT)) {
            if (in != null) return lire(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(), fr);
        } catch (Exception ex) {
            HeralizInterface.LOG.warn("[interface] texte d'origine illisible", ex);
        }
        return new Contenu(List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }

    private static Contenu lire(JsonObject o, boolean fr) {
        String l = fr ? "fr" : "en", autre = fr ? "en" : "fr";
        List<Chiffre> chiffres = new ArrayList<>();
        for (JsonObject c : objets(o, "chiffres")) {
            String v = texte(c, fr ? "valeur" : "en_valeur");
            if (v.isEmpty()) v = texte(c, "valeur");
            chiffres.add(new Chiffre(v, texte(c, l, autre)));
        }
        List<Repere> reperes = new ArrayList<>();
        for (JsonObject c : objets(o, "reperes")) reperes.add(new Repere(texte(c, "annee"), texte(c, l, autre)));
        List<Commande> commandes = new ArrayList<>();
        for (JsonObject c : objets(o, "commandes")) commandes.add(new Commande(texte(c, "commande"), texte(c, "touche"), texte(c, l, autre)));
        return new Contenu(chiffres, reperes, lignes(o, "histoire", l, autre), commandes, lignes(o, "regles", l, autre),
                o.has("note_regles") && o.get("note_regles").isJsonObject() ? texte(o.getAsJsonObject("note_regles"), l, autre) : "");
    }

    private static List<JsonObject> objets(JsonObject o, String cle) {
        List<JsonObject> l = new ArrayList<>();
        if (o.has(cle) && o.get(cle).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(cle)) if (e.isJsonObject()) l.add(e.getAsJsonObject());
        }
        return l;
    }

    /** Liste de textes par langue : {"fr": [...], "en": [...]} (ou une simple liste). */
    private static List<String> lignes(JsonObject o, String cle, String l, String autre) {
        List<String> r = new ArrayList<>();
        if (!o.has(cle)) return r;
        JsonElement e = o.get(cle);
        JsonArray a = null;
        if (e.isJsonArray()) a = e.getAsJsonArray();
        else if (e.isJsonObject()) {
            JsonObject p = e.getAsJsonObject();
            if (p.has(l) && p.get(l).isJsonArray()) a = p.getAsJsonArray(l);
            else if (p.has(autre) && p.get(autre).isJsonArray()) a = p.getAsJsonArray(autre);
        }
        if (a != null) for (JsonElement x : a) if (x.isJsonPrimitive() && !x.getAsString().isBlank()) r.add(x.getAsString().trim());
        return r;
    }

    private static String texte(JsonObject o, String cle) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsString().trim() : "";
    }

    private static String texte(JsonObject o, String l, String autre) {
        String s = texte(o, l);
        return s.isEmpty() ? texte(o, autre) : s;
    }
}
