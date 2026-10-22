package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.PermsGrades;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définitions des grades (2.0.0) : apparence, permissions et kits de chaque grade, dans
 * {@code heraliz_grades/definitions.json} à la racine du serveur. Ce fichier est écrit par l'éditeur en jeu (« Registre
 * des grades ») et n'est jamais touché par la mise en ligne : un réglage fait en jeu reste, redémarrage, mise à jour des
 * mods et changement de carte compris. Il n'est pas dans le dépôt Git.
 * <p>
 * Premier démarrage (fichier absent) : grades d'origine et leurs réglages d'avant la 2.0.0, kits repris de
 * {@code config/heraliz_grades/kits.json} (ensuite, ce fichier n'est plus lu). Comme le registre des joueurs : un fichier
 * illisible n'est jamais écrasé ; tant qu'il l'est, les grades d'origine servent et l'éditeur refuse d'enregistrer.
 */
final class DefinitionsGrades {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final int MAX_KITS_PAR_GRADE = 8, MAX_OBJETS = 27, RECHARGE_MAX_H = 24 * 30;

    /** Un kit : grade, identifiant (unique dans le grade), nom affiché, recharge en heures, contenu (27 piles au plus). */
    record KitDef(String grade, String id, String nom, int rechargeH, List<ItemStack> objets) {
    }

    private static Path fichier;
    private static boolean illisible;
    private static final List<KitDef> KITS = new ArrayList<>();

    private DefinitionsGrades() {
    }

    static boolean illisible() {
        return illisible;
    }

    static List<KitDef> kits() {
        return List.copyOf(KITS);
    }

    /** Kits d'un grade, dans l'ordre de l'éditeur. */
    static List<KitDef> kitsDe(Grade g) {
        List<KitDef> l = new ArrayList<>();
        for (KitDef k : KITS) if (k.grade().equals(g.id)) l.add(k);
        return l;
    }

    static KitDef kit(String grade, String id) {
        for (KitDef k : KITS) if (k.grade().equals(grade) && k.id().equals(id)) return k;
        return null;
    }

    // ------------------------------------------------------------------ lecture
    static void charger(Path dossier) {
        fichier = dossier.resolve("definitions.json");
        illisible = false;
        KITS.clear();
        if (!Files.exists(fichier)) {
            LOG.info("[grades] {} absent : grades d'origine, kits repris de config/heraliz_grades/kits.json", fichier);
            reprendreAnciensKits();
            try {
                sauver();
            } catch (IOException ex) {
                LOG.error("[grades] {} impossible à écrire : {}", fichier, ex.toString());
            }
            return;
        }
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Grade.Def> defs = new ArrayList<>();
            List<KitDef> kits = new ArrayList<>();
            for (JsonElement e : racine.getAsJsonArray("grades")) {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                Grade existant = Grade.parId(id);
                Map<String, Integer> perms = new LinkedHashMap<>();
                if (o.has("perms")) for (Map.Entry<String, JsonElement> p : o.getAsJsonObject("perms").entrySet()) perms.put(p.getKey(), p.getValue().getAsInt());
                Grade.Type type = Grade.Type.valueOf(o.get("famille").getAsString().toUpperCase(Locale.ROOT));
                defs.add(new Grade.Def(id, existant != null && existant.interne, texte(o, "fr", id), texte(o, "en", id), texte(o, "ru", id),
                        Integer.parseInt(o.get("couleur").getAsString().replace("#", ""), 16), type,
                        o.has("rang") ? o.get("rang").getAsInt() : 1, o.has("ordre") ? o.get("ordre").getAsInt() : 0,
                        !o.has("gras") || o.get("gras").getAsBoolean(), !o.has("prefixe") || o.get("prefixe").getAsBoolean(),
                        o.has("colonne_staff") && o.get("colonne_staff").getAsBoolean(), perms));
                if (o.has("kits")) {
                    for (JsonElement k : o.getAsJsonArray("kits")) kits.add(lireKit(id, k.getAsJsonObject()));
                }
            }
            Grade.appliquer(defs, true);
            KITS.addAll(kits);
            LOG.info("[grades] {} grade(s) et {} kit(s) chargés depuis {}", defs.size(), kits.size(), fichier);
        } catch (Exception ex) {
            illisible = true;
            LOG.error("[grades] {} ILLISIBLE ({}) : grades d'origine en service, l'éditeur n'enregistre rien tant qu'il n'est pas réparé. Le fichier n'est pas touché.",
                    fichier, ex.toString());
        }
    }

    private static String texte(JsonObject o, String cle, String secours) {
        return o.has(cle) ? o.get(cle).getAsString() : secours;
    }

    private static KitDef lireKit(String grade, JsonObject o) throws Exception {
        List<ItemStack> objets = new ArrayList<>();
        for (JsonElement x : o.getAsJsonArray("objets")) {
            ItemStack s = ItemStack.of(TagParser.parseTag(x.getAsString()));
            if (s.isEmpty()) {
                LOG.warn("[grades] kit {}/{} : objet inconnu ignoré ({})", grade, o.get("id").getAsString(), x.getAsString());
                continue;
            }
            objets.add(s);
        }
        return new KitDef(grade, o.get("id").getAsString(), o.get("nom").getAsString(),
                Math.max(1, Math.min(RECHARGE_MAX_H, o.get("recharge_heures").getAsInt())), List.copyOf(objets));
    }

    /** Premier démarrage : kits de config/heraliz_grades/kits.json (format d'avant la 2.0.0), un « kit » par grade. */
    private static void reprendreAnciensKits() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("kits.json");
        if (!Files.exists(f)) return;
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            List<ItemStack> rescape = null;
            for (JsonElement e : racine.getAsJsonArray("kits")) {
                JsonObject o = e.getAsJsonObject();
                Grade g = Grade.parId(o.get("grade").getAsString());
                if (g == null) continue;
                List<ItemStack> objets = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray("objets")) {
                    JsonObject ob = x.getAsJsonObject();
                    ResourceLocation id = new ResourceLocation(ob.get("item").getAsString());
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item == null || !ForgeRegistries.ITEMS.containsKey(id)) continue;
                    ItemStack s = new ItemStack(item, Math.max(1, Math.min(64, ob.has("nombre") ? ob.get("nombre").getAsInt() : 1)));
                    if (ob.has("nbt")) s.setTag(TagParser.parseTag(ob.get("nbt").getAsString()));
                    objets.add(s);
                }
                int h = (int) Math.max(1, Math.min(RECHARGE_MAX_H, Math.round(o.get("recharge_heures").getAsDouble())));
                KITS.add(new KitDef(g.id, "kit", "Kit " + g.nomFr, h, List.copyOf(objets)));
                if (g == Grade.RESCAPE) rescape = objets;
            }
            // le kit Streamer part de celui de Rescapé : à régler dans l'éditeur
            if (rescape != null) KITS.add(new KitDef(Grade.STREAMER.id, "kit", "Kit Streamer", 24, List.copyOf(rescape)));
        } catch (Exception ex) {
            LOG.error("[grades] {} faux ({}) : aucun kit repris", f, ex.toString());
        }
    }

    // ------------------------------------------------------------------ écriture
    static void sauver() throws IOException {
        if (illisible || fichier == null) throw new IOException("fichier des grades illisible");
        JsonArray grades = new JsonArray();
        for (Grade g : Grade.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", g.id);
            o.addProperty("fr", g.nomFr);
            o.addProperty("en", g.nomEn);
            o.addProperty("ru", g.nomRu);
            o.addProperty("couleur", String.format(Locale.ROOT, "#%06X", g.couleur & 0xFFFFFF));
            o.addProperty("famille", g.type.name().toLowerCase(Locale.ROOT));
            o.addProperty("rang", g.rang);
            o.addProperty("ordre", g.ordre);
            o.addProperty("gras", g.gras);
            o.addProperty("prefixe", g.prefixe);
            o.addProperty("colonne_staff", g.colonneStaff);
            JsonObject perms = new JsonObject();
            g.perms.forEach(perms::addProperty);
            o.add("perms", perms);
            JsonArray kits = new JsonArray();
            for (KitDef k : kitsDe(g)) {
                JsonObject ko = new JsonObject();
                ko.addProperty("id", k.id());
                ko.addProperty("nom", k.nom());
                ko.addProperty("recharge_heures", k.rechargeH());
                JsonArray objets = new JsonArray();
                for (ItemStack s : k.objets()) objets.add(s.save(new CompoundTag()).toString());
                ko.add("objets", objets);
                kits.add(ko);
            }
            o.add("kits", kits);
            grades.add(o);
        }
        JsonObject racine = new JsonObject();
        racine.addProperty("_note", "Grades HeraliZ : écrit par l'éditeur en jeu (Registre des grades, /grades editeur). Familles : staff, boutique, special, base, soutien. "
                + "Permissions : " + String.join(", ", PermsGrades.TOUTES.stream().map(PermsGrades.Perm::cle).toList()) + ".");
        racine.addProperty("version", 1);
        racine.add("grades", grades);
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling("definitions.json.tmp");
        Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomiqueImpossible) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Remplace (ou ajoute) un kit ; null pour objets = le retirer. */
    static void poserKit(String grade, String id, KitDef kit) {
        for (int i = 0; i < KITS.size(); i++) {
            KitDef k = KITS.get(i);
            if (k.grade().equals(grade) && k.id().equals(id)) {
                if (kit == null) KITS.remove(i);
                else KITS.set(i, kit);
                return;
            }
        }
        if (kit != null) KITS.add(kit);
    }

    /** Remet une liste de kits (écriture ratée : retour à l'état d'avant). */
    static void remplacerKits(List<KitDef> kits) {
        KITS.clear();
        KITS.addAll(kits);
    }

    /** Retire les kits d'un grade supprimé. */
    static void retirerKits(String grade) {
        KITS.removeIf(k -> k.grade().equals(grade));
    }
}
