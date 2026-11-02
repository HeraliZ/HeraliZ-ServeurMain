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
 * <p>Copie toujours a jour (audit du 02/11, point 26) : la copie note l'empreinte du texte du jar d'ou elle vient
 * (« _origine ») ; quand le texte du jar change (nouvelle version d'HeraliZ Interface), la copie est remplacee, l'ancienne
 * gardee a cote (informations.json.ancien). Avant : copiee une fois puis lue pour toujours, les joueurs arrives avant le
 * 26/10 voyaient encore /duel, /zombies, /groupe... et pas /rtp, /extractions, /kit.</p>
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
            byte[] origine;
            try (InputStream in = Informations.class.getResourceAsStream(DEFAUT)) {
                origine = in != null ? in.readAllBytes() : null;
            }
            if (origine != null) aJour(f, origine);
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

    /**
     * Copie du texte du jar sur le PC, remplacee quand le texte du jar a change depuis la copie (empreinte « _origine »),
     * l'ancienne gardee en informations.json.ancien ; une copie modifiee a la main reste tant que le jar ne change pas.
     */
    private static void aJour(Path f, byte[] origine) throws java.io.IOException {
        String empreinte = empreinte(origine);
        if (Files.exists(f)) {
            String copie = null;
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("_origine") && o.get("_origine").isJsonPrimitive()) copie = o.get("_origine").getAsString();
            } catch (Exception illisible) {
                copie = null;
            }
            if (empreinte.equals(copie)) return;
            Files.copy(f, f.resolveSibling("informations.json.ancien"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            HeralizInterface.LOG.info("[interface] {} remplace par le texte de cette version (ancien garde en .ancien)", f);
        }
        Files.createDirectories(f.getParent());
        JsonObject texte = JsonParser.parseString(new String(origine, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject copie = new JsonObject();
        copie.addProperty("_origine", empreinte);
        for (java.util.Map.Entry<String, JsonElement> e : texte.entrySet()) {
            if (!"_origine".equals(e.getKey())) copie.add(e.getKey(), e.getValue());
        }
        Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(copie),
                StandardCharsets.UTF_8);
    }

    private static String empreinte(byte[] octets) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest(octets);
            StringBuilder b = new StringBuilder(40);
            for (byte x : h) b.append(String.format(java.util.Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return Integer.toHexString(java.util.Arrays.hashCode(octets)) + "-" + octets.length;
        }
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
