package fr.heralisz.tags.catalogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.tags.HeraliszTags;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogue des tags v2 (TAGS_V2_SPEC \u00a72) : lu une fois au demarrage dans le jar (assets/heralisz_tags/tags.json,
 * produit par tools/tags/tag_art.py), cote commun (client et serveur). Ordre stable = ordre du fichier.
 * <ul>
 *   <li>Les 49 ids de reference ({@link #REFERENCE}, ordre du \u00a72 = case de l'atlas) existent toujours : un id absent
 *       du fichier est ajoute en fin de liste avec sa case, sa rarete et sa taille de reference.</li>
 *   <li>Les ids retires de la v1 ({@link #IDS_RETIRES}) sont ignores meme s'ils figurent encore dans un ancien
 *       tags.json : aucun objet n'est enregistre pour eux, le serveur les oublie au chargement (TagStore).</li>
 *   <li>Sans fichier : liste de reference seule (mod fonctionnel, seuls les visuels manquent).</li>
 * </ul>
 */
public final class TagCatalog {
    private TagCatalog() {
    }

    /** Atlas 8x8 cases de 256 px (2048x2048). */
    public static final int ATLAS_COLONNES = 8, ATLAS_CASES = 64, CASE_PX = 256;
    public static final String CHEMIN = "/assets/heralisz_tags/tags.json";

    /** Entree de la liste de reference (secours si tags.json manque ou est incomplet). */
    public record Reference(String id, Rarete rarete, String nomFr, String nomEn) {
    }

    /** Taille de pose (cote, en blocs) par rarete (\u00a72) : 0,9 / 1,0 / 1,1 / 1,25. */
    public static float tailleDe(Rarete r) {
        return switch (r) {
            case COMMUN -> 0.9f;
            case RARE -> 1.0f;
            case EPIQUE -> 1.1f;
            case LEGENDAIRE -> 1.25f;
        };
    }

    /** Les 49 tags v2 dans l'ordre du \u00a72 (index = case de l'atlas, memes ids que gen_item_models.py). */
    public static final List<Reference> REFERENCE = List.of(
            // COMMUN : pochoirs de survivants (cases 0..15)
            ref("rodeur", Rarete.COMMUN, "R\u00f4deur", "Walker"),
            ref("botte", Rarete.COMMUN, "Empreinte de rangers", "Boot print"),
            ref("balle", Rarete.COMMUN, "Cartouche 7.62", "7.62 round"),
            ref("viseur", Rarete.COMMUN, "R\u00e9ticule", "Reticle"),
            ref("danger", Rarete.COMMUN, "Danger", "Danger"),
            ref("oeil", Rarete.COMMUN, "L'\u0152il", "The Eye"),
            ref("safe", Rarete.COMMUN, "Zone s\u00fbre", "Safe zone"),
            ref("eau", Rarete.COMMUN, "Point d'eau", "Water point"),
            ref("no_go", Rarete.COMMUN, "No go", "No go"),
            ref("compte_jours", Rarete.COMMUN, "Compte des jours", "Day tally"),
            ref("maison_pillee", Rarete.COMMUN, "Maison pill\u00e9e", "Looted house"),
            ref("radio", Rarete.COMMUN, "Fr\u00e9quence radio", "Radio frequency"),
            ref("cible_tete", Rarete.COMMUN, "Cible de tir", "Range target"),
            ref("quarantaine", Rarete.COMMUN, "Quarantaine", "Quarantine"),
            ref("biohazard", Rarete.COMMUN, "Risque biologique", "Biohazard"),
            ref("croix_medicale", Rarete.COMMUN, "Croix m\u00e9dicale", "Medical cross"),
            // RARE : lettrages graff (cases 16..27)
            ref("gg", Rarete.RARE, "GG", "GG"),
            ref("ez", Rarete.RARE, "EZ", "EZ"),
            ref("ggwp", Rarete.RARE, "GGWP", "GGWP"),
            ref("wp", Rarete.RARE, "WP", "WP"),
            ref("nt", Rarete.RARE, "NT", "NT"),
            ref("ace", Rarete.RARE, "ACE", "ACE"),
            ref("clutch", Rarete.RARE, "CLUTCH", "CLUTCH"),
            ref("rip", Rarete.RARE, "RIP", "RIP"),
            ref("boom", Rarete.RARE, "BOOM", "BOOM"),
            ref("glhf", Rarete.RARE, "GLHF", "GLHF"),
            ref("hz", Rarete.RARE, "HZ \u00b7 signature HeraliZ", "HZ \u00b7 HeraliZ throw-up"),
            ref("one_tap", Rarete.RARE, "1 TAP", "1 TAP"),
            // EPIQUE : street art (cases 28..40)
            ref("masque_gaz", Rarete.EPIQUE, "Masque \u00e0 gaz", "Gas mask"),
            ref("crane_casque", Rarete.EPIQUE, "Cr\u00e2ne casqu\u00e9", "Helmeted skull"),
            ref("corbeau", Rarete.EPIQUE, "Corbeau", "Raven"),
            ref("loup", Rarete.EPIQUE, "Loup", "Wolf"),
            ref("grenade", Rarete.EPIQUE, "Grenade", "Grenade"),
            ref("molotov", Rarete.EPIQUE, "Cocktail Molotov", "Molotov cocktail"),
            ref("papillon", Rarete.EPIQUE, "Couteau papillon", "Butterfly knife"),
            ref("awp", Rarete.EPIQUE, "Fusil de pr\u00e9cision", "Sniper rifle"),
            ref("faucheuse", Rarete.EPIQUE, "Faucheuse", "Reaper"),
            ref("coeur_barbele", Rarete.EPIQUE, "C\u0153ur barbel\u00e9", "Barbed heart"),
            ref("silencieux", Rarete.EPIQUE, "Silencieux", "Suppressor"),
            ref("tete_mort_soviet", Rarete.EPIQUE, "T\u00eate de mort sovi\u00e9tique", "Soviet skull"),
            ref("main_ombre", Rarete.EPIQUE, "Main d'ombre", "Shadow hand"),
            // LEGENDAIRE : les 7 boss (cases 41, 43..48 ; la 42 etait le Glacivore, retire)
            ref("n07", Rarete.LEGENDAIRE, "Sujet N-07", "Subject N-07"),
            ref("sylvorn", Rarete.LEGENDAIRE, "Sylvorn", "Sylvorn"),
            ref("mere", Rarete.LEGENDAIRE, "M\u00e8re des Ruines", "Mother of Ruins"),
            ref("vulkhar", Rarete.LEGENDAIRE, "Vulkhar", "Vulkhar"),
            ref("peste", Rarete.LEGENDAIRE, "Porteur de Peste", "Plague Bearer"),
            ref("chevalier", Rarete.LEGENDAIRE, "Chevalier Sans Visage", "Faceless Knight"),
            ref("kraken", Rarete.LEGENDAIRE, "Kraken", "Kraken"));

    /** Ids de reference seuls, dans l'ordre (objets toujours enregistres pour eux). */
    public static final List<String> IDS_REFERENCE = REFERENCE.stream().map(Reference::id).toList();

    /** Ids de la v1 retires en v2 : ignores partout (catalogue, stockage, roue). */
    public static final Set<String> IDS_RETIRES = Set.of(
            "rat", "cafard", "chaine_brisee", "sans_signal", "evacuation", "zombie_barre", "eclair", "etoile",
            "main_sang", "ombre", "radioactif",
            // boss Glacivore retire du serveur (2026-10-01)
            "glacivore");

    private static Reference ref(String id, Rarete r, String fr, String en) {
        return new Reference(id, r, fr, en);
    }

    private static final Map<String, TagDef> PAR_ID = new LinkedHashMap<>();
    private static List<TagDef> LISTE = List.of();
    private static boolean depuisFichier;

    /** Charge le catalogue (idempotent). */
    public static synchronized void charger() {
        if (!PAR_ID.isEmpty()) return;
        Map<String, Reference> refs = new LinkedHashMap<>();
        for (Reference r : REFERENCE) refs.put(r.id(), r);
        List<TagDef> lus = new ArrayList<>();
        List<String> retiresVus = new ArrayList<>();
        try (InputStream in = TagCatalog.class.getResourceAsStream(CHEMIN)) {
            if (in != null) {
                JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                Set<String> vus = new HashSet<>();
                int ordre = 0;
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    if (!id.matches("[a-z0-9_]{1,32}")) {
                        HeraliszTags.LOGGER.warn("[HeraliZ Tags] id de tag invalide ignore : {}", id);
                        continue;
                    }
                    if (IDS_RETIRES.contains(id)) {
                        retiresVus.add(id);
                        continue;
                    }
                    if (!vus.add(id)) {
                        HeraliszTags.LOGGER.warn("[HeraliZ Tags] id de tag en double ignore : {}", id);
                        continue;
                    }
                    Reference ref = refs.get(id);
                    int casDefaut = ref != null ? IDS_REFERENCE.indexOf(id) : ordre;
                    int cas = Math.max(0, Math.min(ATLAS_CASES - 1, o.has("case") ? o.get("case").getAsInt() : casDefaut));
                    Rarete r = o.has("rarete") ? Rarete.depuis(o.get("rarete").getAsString()) : (ref != null ? ref.rarete() : Rarete.COMMUN);
                    float taille = o.has("taille") ? o.get("taille").getAsFloat() : tailleDe(r);
                    taille = Math.max(0.5f, Math.min(1.5f, taille));
                    String fr = o.has("nom_fr") ? o.get("nom_fr").getAsString() : (ref != null ? ref.nomFr() : id);
                    String en = o.has("nom_en") ? o.get("nom_en").getAsString() : (ref != null ? ref.nomEn() : fr);
                    lus.add(new TagDef(id, ordre++, cas, taille, r, fr, en));
                }
                depuisFichier = true;
            }
        } catch (Exception e) {
            HeraliszTags.LOGGER.error("[HeraliZ Tags] tags.json illisible, liste de reference utilisee", e);
            lus.clear();
            depuisFichier = false;
        }
        if (!retiresVus.isEmpty()) {
            HeraliszTags.LOGGER.warn("[HeraliZ Tags] tags.json contient {} id(s) retire(s) en v2, ignore(s) : {}", retiresVus.size(), retiresVus);
        }
        // les ids de reference absents du fichier (ou tous, sans fichier) sont ajoutes en fin de liste, avec leur case
        Set<String> presents = new HashSet<>();
        for (TagDef d : lus) presents.add(d.id());
        List<String> manquants = new ArrayList<>();
        int ordre = lus.size();
        for (int i = 0; i < REFERENCE.size(); i++) {
            Reference ref = REFERENCE.get(i);
            if (presents.contains(ref.id())) continue;
            if (depuisFichier) manquants.add(ref.id());
            lus.add(new TagDef(ref.id(), ordre++, i, tailleDe(ref.rarete()), ref.rarete(), ref.nomFr(), ref.nomEn()));
        }
        if (!manquants.isEmpty()) {
            HeraliszTags.LOGGER.warn("[HeraliZ Tags] {} tag(s) de reference absent(s) de tags.json, ajoute(s) : {}", manquants.size(), manquants);
        }
        for (TagDef d : lus) PAR_ID.put(d.id(), d);
        LISTE = Collections.unmodifiableList(new ArrayList<>(PAR_ID.values()));
        HeraliszTags.LOGGER.info("[HeraliZ Tags] catalogue : {} tags ({})", LISTE.size(), depuisFichier ? "tags.json" : "liste de reference");
    }

    /** Tous les tags, dans l'ordre du catalogue. */
    public static List<TagDef> tous() {
        charger();
        return LISTE;
    }

    /** Le tag ou null. */
    public static TagDef get(String id) {
        charger();
        return id == null ? null : PAR_ID.get(id);
    }

    public static boolean existe(String id) {
        return get(id) != null;
    }

    public static int taille() {
        return tous().size();
    }

    /** Vrai si le catalogue vient de tags.json (et non de la liste de secours). */
    public static boolean depuisFichier() {
        charger();
        return depuisFichier;
    }
}
