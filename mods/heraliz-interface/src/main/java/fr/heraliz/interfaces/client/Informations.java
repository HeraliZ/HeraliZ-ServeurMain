package fr.heraliz.interfaces.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.client.Minecraft;
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
 * en « fr », « en » et « ru » ; la langue du jeu choisit, l'anglais puis le francais servent de secours. Un fichier
 * cree avant le russe (aucun texte « ru ») laisse la place au texte d'origine du jar pour un joueur en russe. Un fichier
 * illisible ne bloque rien : le texte d'origine du jar est affiche et l'erreur va au journal.
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

    /** Code de langue des textes : « fr », « ru » ou « en » (toute autre langue du jeu). */
    private static String langue() {
        if (Ui.french()) return "fr";
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("ru") ? "ru" : "en";
    }

    /** Lit le contenu (fichier de config, sinon texte d'origine). */
    public static Contenu charger() {
        String langue = langue();
        Path f = fichier();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try (InputStream in = Informations.class.getResourceAsStream(DEFAUT)) {
                    if (in != null) Files.write(f, in.readAllBytes());
                }
            }
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                // fichier d'avant le russe : le texte d'origine du jar (qui l'a) pour un joueur en russe
                if (!"ru".equals(langue) || o.toString().contains("\"ru\"")) return lire(o, langue);
            }
        } catch (Exception ex) {
            HeralizInterface.LOG.warn("[interface] {} illisible, texte d'origine affiche : {}", f, ex.toString());
        }
        try (InputStream in = Informations.class.getResourceAsStream(DEFAUT)) {
            if (in != null) return lire(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(), langue);
        } catch (Exception ex) {
            HeralizInterface.LOG.warn("[interface] texte d'origine illisible", ex);
        }
        return new Contenu(List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }

    private static Contenu lire(JsonObject o, String langue) {
        // langue choisie, puis l'anglais, puis le francais
        String[] l = "fr".equals(langue) ? new String[]{"fr", "en"} : "ru".equals(langue) ? new String[]{"ru", "en", "fr"}
                : new String[]{"en", "fr"};
        List<Chiffre> chiffres = new ArrayList<>();
        for (JsonObject c : objets(o, "chiffres")) {
            String v = "fr".equals(langue) ? "" : texte(c, langue + "_valeur");
            if (v.isEmpty() && "ru".equals(langue)) v = texte(c, "en_valeur");
            if (v.isEmpty()) v = texte(c, "valeur");
            chiffres.add(new Chiffre(v, texte(c, l)));
        }
        List<Repere> reperes = new ArrayList<>();
        for (JsonObject c : objets(o, "reperes")) reperes.add(new Repere(texte(c, "annee"), texte(c, l)));
        List<Commande> commandes = new ArrayList<>();
        for (JsonObject c : objets(o, "commandes")) commandes.add(new Commande(texte(c, "commande"), texte(c, "touche"), texte(c, l)));
        return new Contenu(chiffres, reperes, lignes(o, "histoire", l), commandes, lignes(o, "regles", l),
                o.has("note_regles") && o.get("note_regles").isJsonObject() ? texte(o.getAsJsonObject("note_regles"), l) : "");
    }

    private static List<JsonObject> objets(JsonObject o, String cle) {
        List<JsonObject> l = new ArrayList<>();
        if (o.has(cle) && o.get(cle).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(cle)) if (e.isJsonObject()) l.add(e.getAsJsonObject());
        }
        return l;
    }

    /** Liste de textes par langue : {"fr": [...], "en": [...], "ru": [...]} (ou une simple liste). */
    private static List<String> lignes(JsonObject o, String cle, String[] langues) {
        List<String> r = new ArrayList<>();
        if (!o.has(cle)) return r;
        JsonElement e = o.get(cle);
        JsonArray a = null;
        if (e.isJsonArray()) a = e.getAsJsonArray();
        else if (e.isJsonObject()) {
            JsonObject p = e.getAsJsonObject();
            for (String l : langues) {
                if (p.has(l) && p.get(l).isJsonArray()) {
                    a = p.getAsJsonArray(l);
                    break;
                }
            }
        }
        if (a != null) for (JsonElement x : a) if (x.isJsonPrimitive() && !x.getAsString().isBlank()) r.add(x.getAsString().trim());
        return r;
    }

    private static String texte(JsonObject o, String cle) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsString().trim() : "";
    }

    /** Premier texte non vide dans l'ordre des langues. */
    private static String texte(JsonObject o, String[] langues) {
        for (String l : langues) {
            String s = texte(o, l);
            if (!s.isEmpty()) return s;
        }
        return "";
    }
}
