package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.Texte;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Marchands créés EN JEU par un op (Quêtes 1.11.0, {@code /marchand nouveau <id> <rôle> <nom>}) : un PNJ sans fichier
 * {@code pnj/<id>.json}. Son identité (nom affiché, rôle) est gardée à la racine du serveur
 * ({@code heraliz_quetes/pnj_jeu.json}, hors de {@code config/} : jamais écrasée par la mise en ligne), ce qu'il vend,
 * rachète ou échange dans {@code marchands_jeu.json} comme les autres marchands modifiés en jeu ({@link EditionMarchands}).
 * Un marchand supprimé est noté ({@code supprimes}) : ses PNJ encore posés dans un coin non chargé du monde disparaissent
 * quand ce coin se charge ({@link PnjServeur}).
 */
final class MarchandsCrees {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Rôles possibles (mots des commandes) : il vend, il rachète, il échange. */
    static final List<String> ROLES = List.of("vendeur", "racheteur", "troc");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{2,32}");
    static final int NOM_MAX = 32;

    record Cree(String nom, String role) {
    }

    private static Path fichier;
    private static final Map<String, Cree> CREES = new LinkedHashMap<>();
    private static final Set<String> SUPPRIMES = new LinkedHashSet<>();

    private MarchandsCrees() {
    }

    static void demarrer(Path racineServeur) {
        fichier = racineServeur.resolve("heraliz_quetes").resolve("pnj_jeu.json");
        CREES.clear();
        SUPPRIMES.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("pnj") && o.get("pnj").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("pnj").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject c = e.getValue().getAsJsonObject();
                    String role = Defs.chaine(c, "role", "vendeur");
                    CREES.put(e.getKey(), new Cree(Defs.chaine(c, "nom", e.getKey()), ROLES.contains(role) ? role : "vendeur"));
                }
            }
            if (o.has("supprimes") && o.get("supprimes").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("supprimes")) if (e.isJsonPrimitive()) SUPPRIMES.add(e.getAsString());
            }
        } catch (Exception ex) {
            // illisible : jamais écrasé, mis de côté (comme marchands_jeu.json)
            LOG.error("[heraliz_quetes] {} illisible, mis de côté : {}", fichier, ex.getMessage());
            try {
                Files.move(fichier, fichier.resolveSibling("pnj_jeu.json.illisible"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
            }
        }
    }

    private static boolean sauver() {
        if (fichier == null) return false;
        JsonObject o = new JsonObject();
        JsonObject pnj = new JsonObject();
        CREES.forEach((id, c) -> {
            JsonObject j = new JsonObject();
            j.addProperty("nom", c.nom());
            j.addProperty("role", c.role());
            pnj.add(id, j);
        });
        o.add("pnj", pnj);
        JsonArray sup = new JsonArray();
        SUPPRIMES.forEach(sup::add);
        o.add("supprimes", sup);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("pnj_jeu.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ex) {
            LOG.error("[heraliz_quetes] écriture de {} impossible : {}", fichier, ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ lecture
    static boolean cree(String id) {
        return CREES.containsKey(id);
    }

    static boolean supprime(String id) {
        return SUPPRIMES.contains(id);
    }

    static Set<String> ids() {
        return CREES.keySet();
    }

    static String role(String id) {
        Cree c = CREES.get(id);
        return c == null ? null : c.role();
    }

    /** Identifiant permis : 2 à 32 caractères, minuscules, chiffres ou « _ ». */
    static boolean idValide(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** Nom affiché nettoyé : sans codes « § » ni caractères de contrôle, 32 caractères au plus ; vide si rien ne reste. */
    static String nettoyerNom(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§') {
                i++;
                continue;
            }
            if (Character.isISOControl(ch)) continue;
            b.append(ch);
        }
        String r = b.toString().trim();
        return r.length() > NOM_MAX ? r.substring(0, NOM_MAX).trim() : r;
    }

    /** Ajoute la fiche de chaque marchand créé en jeu aux PNJ lus dans les fichiers (appelé à chaque lecture). */
    static void ajouter(Map<String, Defs.Pnj> pnj, List<String> erreurs) {
        for (Map.Entry<String, Cree> e : CREES.entrySet()) {
            if (pnj.containsKey(e.getKey())) {
                erreurs.add("marchand créé en jeu « " + e.getKey() + " » : un fichier pnj/" + e.getKey() + ".json existe aussi, le fichier passe devant");
                continue;
            }
            pnj.put(e.getKey(), fiche(e.getKey(), e.getValue()));
        }
    }

    private static Defs.Pnj fiche(String id, Cree c) {
        Defs.Pnj p = new Defs.Pnj();
        p.id = id;
        p.nom = Texte.fr(c.nom());
        p.role = Texte.cle("heraliz_quetes.marchand.role." + c.role());
        Texte accueil = Texte.cle("heraliz_quetes.marchand.accueil." + c.role());
        p.presentation = accueil;
        p.inconnu = accueil;
        p.connu = accueil;
        // marchand vide du bon rôle : remplacé par celui de marchands_jeu.json (EditionMarchands.appliquer)
        p.marchand = new Defs.Marchand();
        appliquerRole(p.marchand, c.role());
        return p;
    }

    static void appliquerRole(Defs.Marchand m, String role) {
        m.rachat = role.equals("racheteur");
        m.troc = role.equals("troc");
    }

    // ------------------------------------------------------------------ changements (op, déjà vérifié par l'appelant)
    /** Nouveau marchand ; faux si l'écriture du fichier a échoué (rien n'est alors gardé). */
    static boolean creer(String id, String nom, String role) {
        id = id.toLowerCase(Locale.ROOT);
        CREES.put(id, new Cree(nom, role));
        boolean etaitSupprime = SUPPRIMES.remove(id);
        if (sauver()) return true;
        CREES.remove(id);
        if (etaitSupprime) SUPPRIMES.add(id);
        return false;
    }

    static boolean supprimer(String id) {
        Cree avant = CREES.remove(id);
        if (avant == null) return false;
        SUPPRIMES.add(id);
        if (sauver()) return true;
        CREES.put(id, avant);
        SUPPRIMES.remove(id);
        return false;
    }

    static boolean renommer(String id, String nom) {
        Cree avant = CREES.get(id);
        if (avant == null) return false;
        CREES.put(id, new Cree(nom, avant.role()));
        if (sauver()) return true;
        CREES.put(id, avant);
        return false;
    }

    static boolean changerRole(String id, String role) {
        Cree avant = CREES.get(id);
        if (avant == null) return false;
        CREES.put(id, new Cree(avant.nom(), role));
        if (sauver()) return true;
        CREES.put(id, avant);
        return false;
    }
}
