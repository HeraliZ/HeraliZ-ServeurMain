package fr.heraliz.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Tables de loot (pack de données : {@code data/<ns>/coffres/<nom>.json}).
 * <ul><li>Coffre T7 : la table portant son nom (ex. t7_coffre_fort), sinon « t7 ».</li>
 * <li>Conteneur T1 à T6 et familles (rotation, {@link RotationLoot}) : la table portant son nom exact, sinon celle de
 * son préfixe (t2, nourriture3...), sinon celle de sa famille (nourriture, soins, munitions). Le chiffre du préfixe est
 * le niveau du conteneur : « niveaux » peut donner un nombre de cases par niveau.</li></ul>
 * Format :
 * <pre>{ "emplacements": [6, 10],
 *   "niveaux": { "1": [1, 2], "2": [2, 3] },                       (facultatif)
 *   "objets": [ { "item": "minecraft:diamond", "nombre": [1, 3], "poids": 20, "rarete": "rare",
 *                 "nom": "facultatif", "nbt": "{facultatif}" } ] }</pre>
 * « rarete » est facultatif : sans elle, l'objet reste ordinaire (pas de marque, il s'empile avec les autres). Un objet
 * d'un mod absent est ignoré (journal en DEBUG) ; un objet inconnu d'un mod présent est signalé (WARN).
 */
public final class TablesCoffres extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    static final Map<String, Table> TABLES = new HashMap<>();

    record Entree(Item item, int min, int max, int poids, @Nullable Rarete rarete, String nom, String nbt) {}

    record Table(int min, int max, Map<Integer, int[]> niveaux, List<Entree> objets) {}

    /** Table choisie pour un conteneur (id de la table), avec le nombre de cases à remplir. */
    record Choix(String id, Table table, int min, int max) {}

    public TablesCoffres() {
        super(GSON, "coffres");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> fichiers, ResourceManager rm, ProfilerFiller pf) {
        TABLES.clear();
        int[] absents = {0};
        fichiers.forEach((id, json) -> {
            try {
                JsonObject o = json.getAsJsonObject();
                JsonArray e = o.getAsJsonArray("emplacements");
                Map<Integer, int[]> niveaux = new HashMap<>();
                if (o.has("niveaux")) {
                    for (Map.Entry<String, JsonElement> n : o.getAsJsonObject("niveaux").entrySet()) {
                        JsonArray a = n.getValue().getAsJsonArray();
                        niveaux.put(Integer.parseInt(n.getKey().trim()), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
                    }
                }
                List<Entree> l = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray("objets")) {
                    JsonObject j = x.getAsJsonObject();
                    String cle = j.get("item").getAsString();
                    ResourceLocation rl = ResourceLocation.tryParse(cle);
                    // le registre des objets a une valeur par défaut (air) : un id inconnu se teste avec containsKey
                    Item it = rl == null || !ForgeRegistries.ITEMS.containsKey(rl) ? null : ForgeRegistries.ITEMS.getValue(rl);
                    if (it == null) {
                        if (rl != null && !ModList.get().isLoaded(rl.getNamespace())) {
                            absents[0]++;
                            HeralizLoot.LOG.debug("Table {} : {} ignoré (mod {} absent)", id, cle, rl.getNamespace());
                        } else {
                            HeralizLoot.LOG.warn("Table {} : objet inconnu {}", id, cle);
                        }
                        continue;
                    }
                    JsonArray n = j.has("nombre") ? j.getAsJsonArray("nombre") : null;
                    int min = Math.max(1, n == null ? 1 : n.get(0).getAsInt());
                    int max = Math.max(min, n == null ? 1 : n.get(1).getAsInt());
                    l.add(new Entree(it, min, max, j.has("poids") ? Math.max(0, j.get("poids").getAsInt()) : 10,
                            j.has("rarete") ? Rarete.de(j.get("rarete").getAsString()) : null,
                            j.has("nom") ? j.get("nom").getAsString() : null, j.has("nbt") ? j.get("nbt").getAsString() : null));
                }
                TABLES.put(id.getPath(), new Table(e.get(0).getAsInt(), e.get(1).getAsInt(), niveaux, l));
            } catch (Exception ex) {
                HeralizLoot.LOG.error("Table de coffre {} illisible", id, ex);
            }
        });
        HeralizLoot.LOG.info("{} tables de loot{}", TABLES.size(), absents[0] > 0 ? " (" + absents[0] + " objets de mods absents ignorés)" : "");
    }

    static Table table(String nom) {
        Table t = TABLES.get(nom);
        if (t == null && nom.startsWith("t7")) {
            t = TABLES.get("t7");
        }
        return t;
    }

    /**
     * Table d'un conteneur de la rotation (t2_frigo, nourriture3_loot_nourriture_1...) : nom exact, puis préfixe (t2,
     * nourriture3), puis famille (nourriture). Null si aucune table ne convient.
     */
    @Nullable
    static Choix pourConteneur(String nom) {
        int i = nom.indexOf('_');
        String prefixe = i > 0 ? nom.substring(0, i) : nom;
        int j = prefixe.length();
        while (j > 0 && Character.isDigit(prefixe.charAt(j - 1))) j--;
        String famille = prefixe.substring(0, j);
        int niveau = 1;
        try {
            if (j < prefixe.length()) niveau = Integer.parseInt(prefixe.substring(j));
        } catch (NumberFormatException ignore) {
            // chiffre trop long : niveau 1
        }
        String id = nom;
        Table t = TABLES.get(id);
        if (t == null) t = TABLES.get(id = prefixe);
        if (t == null && famille.length() > 1) t = TABLES.get(id = famille);
        if (t == null) return null;
        int[] n = t.niveaux().get(niveau);
        return n != null ? new Choix(id, t, n[0], n[1]) : new Choix(id, t, t.min(), t.max());
    }

    /** Tire un objet de la table (poids) ; si l'entrée a une rareté, elle est inscrite dessus. */
    static ItemStack tirer(Table t, RandomSource r) {
        int total = t.objets().stream().mapToInt(Entree::poids).sum();
        if (total <= 0) return ItemStack.EMPTY;
        int k = r.nextInt(total);
        for (Entree e : t.objets()) {
            k -= e.poids();
            if (k < 0) {
                int n = e.min() + r.nextInt(e.max() - e.min() + 1);
                ItemStack st = new ItemStack(e.item(), n);
                if (e.nbt() != null) {
                    try {
                        st.setTag(TagParser.parseTag(e.nbt()));
                    } catch (Exception ex) {
                        HeralizLoot.LOG.warn("NBT illisible pour {} : {}", e.item(), e.nbt());
                    }
                }
                // pile maximale selon la pile (NBT compris) : munitions TaCZ, soins et grenades data-driven
                st.setCount(Math.min(n, Math.max(1, st.getMaxStackSize())));
                Rarete ra = e.rarete();
                if (e.nom() != null) {
                    Style s = Style.EMPTY.withItalic(false);
                    st.setHoverName(Component.literal(e.nom()).setStyle(ra != null ? s.withColor(TextColor.fromRgb(ra.couleur)) : s));
                }
                if (ra != null) st.getOrCreateTag().putString(Rarete.CLE, ra.id);
                return st;
            }
        }
        return ItemStack.EMPTY;
    }
}
