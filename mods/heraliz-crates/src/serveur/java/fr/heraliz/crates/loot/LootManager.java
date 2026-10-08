package fr.heraliz.crates.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Set;
import java.util.Map;

/**
 * Chargement / sauvegarde des tables de butin : config/heralizcrates/<coffre>.json
 */
public final class LootManager {
    private LootManager() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<CrateType, CrateLoot> TABLES = new EnumMap<>(CrateType.class);
    private static boolean loaded;

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("heralizcrates");
    }

    public static synchronized CrateLoot get(CrateType type) {
        if (!loaded) reload();
        CrateLoot l = TABLES.get(type);
        if (l == null) {
            l = new CrateLoot(type);
            TABLES.put(type, l);
        }
        return l;
    }

    /** Recharge tous les fichiers. Retourne le nombre d'erreurs. */
    public static synchronized int reload() {
        loaded = true;
        int errors = 0;
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            HeralizCrates.LOGGER.error("[HeralizCrates] Impossible de creer {}", dir(), e);
        }
        errors += loadGeneral();
        TABLES.clear();
        for (CrateType type : CrateType.values()) {
            Path file = dir().resolve(type.id + ".json");
            CrateLoot loot = new CrateLoot(type);
            if (!Files.exists(file)) {
                loot.entries.addAll(DefaultLoot.of(type));
                TABLES.put(type, loot);
                save(type);
                continue;
            }
            int fileErrors = 0;
            int converted = 0;
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonArray arr = root.has("rewards") ? root.getAsJsonArray("rewards") : new JsonArray();
                for (JsonElement el : arr) {
                    try {
                        LootEntry e = LootEntry.fromJson(el.getAsJsonObject());
                        if (RETIRED.contains(e.item)) {
                            converted++;
                            continue;
                        }
                        if (convert(e)) converted++;
                        if (!e.isValid()) {
                            HeralizCrates.LOGGER.warn("[HeralizCrates] {} : objet inconnu ou poids nul -> {}", file.getFileName(), e.item);
                            fileErrors++;
                        }
                        loot.entries.add(e);
                    } catch (Exception ex) {
                        HeralizCrates.LOGGER.warn("[HeralizCrates] {} : entree invalide {}", file.getFileName(), el);
                        fileErrors++;
                    }
                }
            } catch (Exception ex) {
                HeralizCrates.LOGGER.error("[HeralizCrates] Erreur de lecture de {}", file, ex);
                fileErrors++;
            }
            if (obsolete(loot)) {
                // fichier d'une ancienne version (couteaux de la 2.3.0, retires) : sauvegarde puis butin par defaut
                Path backup = dir().resolve(type.id + ".json.bak");
                try {
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                    HeralizCrates.LOGGER.info("[HeralizCrates] {} cite des objets retires du mod : sauvegarde dans {}, butin par defaut regenere",
                            file.getFileName(), backup.getFileName());
                } catch (IOException e) {
                    HeralizCrates.LOGGER.error("[HeralizCrates] Impossible de sauvegarder {}", file, e);
                }
                loot.entries.clear();
                loot.entries.addAll(DefaultLoot.of(type));
                TABLES.put(type, loot);
                save(type);
                continue;
            }
            errors += fileErrors;
            TABLES.put(type, loot);
            if (converted > 0) {
                HeralizCrates.LOGGER.info("[HeralizCrates] {} : {} entree(s) d'une ancienne version convertie(s) ou retiree(s)", file.getFileName(), converted);
                save(type);
            }
        }
        return errors;
    }

    /** Packs de contenu heralisz_tactical : namespace du pack -> objet porteur et cle NBT de l'id. */
    private static final String[][] TACTICAL_PACKS = {
            {"heralisz_grenade", "heralisz_tactical:throwable", "ThrowableId"},
            {"heralisz_heal", "heralisz_tactical:consumable", "ConsumableId"},
    };

    /** Objets cfm des anciennes caisses Chantier absents du cfm installe -> remplacant. */
    private static final Map<String, String> RENAMED = Map.of(
            "cfm:container_military_green", "cfm:container_green",
            "cfm:container_military_gunrack", "cfm:container_camo2",
            "cfm:military_tent", "cfm:military_tent_1",
            "cfm:metal_fence", "cfm:zombie_chainlinkfence",
            "cfm:metal_renforced", "cfm:dark_metal_2",
            "cfm:concrete_military", "cfm:green_beton");

    /** Objets abandonnes : retires des anciennes configs. */
    private static final Set<String> RETIRED = Set.of("cfm:jerry_can");

    /**
     * Met a jour une entree d'une ancienne config. Les grenades et soins etaient cites comme objets
     * ("heralisz_grenade:m67", "heralisz_heal:cms") alors que ce sont des ids de packs heralisz_tactical :
     * on passe a heralisz_tactical:throwable / consumable + NBT de l'id (le NBT existant est conserve).
     * Retourne true si l'entree a change.
     */
    private static boolean convert(LootEntry e) {
        if (e.item == null) return false;
        String renamed = RENAMED.get(e.item);
        if (renamed != null) {
            e.item = renamed;
            return true;
        }
        ResourceLocation rl = ResourceLocation.tryParse(e.item);
        if (rl == null || ForgeRegistries.ITEMS.containsKey(rl)) return false;
        for (String[] pack : TACTICAL_PACKS) {
            if (!rl.getNamespace().equals(pack[0])) continue;
            CompoundTag tag = new CompoundTag();
            if (e.nbt != null && !e.nbt.isBlank()) {
                try {
                    tag = TagParser.parseTag(e.nbt);
                } catch (CommandSyntaxException ex) {
                    HeralizCrates.LOGGER.warn("[HeralizCrates] NBT invalide ignore pour {} : {}", e.item, ex.getMessage());
                }
            }
            tag.putString(pack[2], rl.toString());
            e.item = pack[1];
            e.nbt = tag.toString();
            return true;
        }
        return false;
    }

    /** Le fichier cite-t-il des objets de ce mod qui n'existent plus (versions precedentes) ? */
    private static boolean obsolete(CrateLoot loot) {
        for (LootEntry e : loot.entries) {
            if (e.item == null || !e.item.startsWith(HeralizCrates.MODID + ":")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.item);
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return true;
        }
        return false;
    }

    private static int loadGeneral() {
        Path file = dir().resolve("general.json");
        if (!Files.exists(file)) {
            saveGeneral();
            return 0;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("consume_key_in_creative")) GeneralConfig.consumeKeyInCreative = o.get("consume_key_in_creative").getAsBoolean();
            if (o.has("broadcast_min_rarity")) GeneralConfig.broadcastMinRarity = LootRarity.byId(o.get("broadcast_min_rarity").getAsString());
            if (o.has("title_min_rarity")) GeneralConfig.titleMinRarity = LootRarity.byId(o.get("title_min_rarity").getAsString());
            if (o.has("ring_size")) GeneralConfig.ringSize = Math.max(4, Math.min(12, o.get("ring_size").getAsInt()));
            return 0;
        } catch (Exception ex) {
            HeralizCrates.LOGGER.error("[HeralizCrates] Erreur de lecture de {}", file, ex);
            return 1;
        }
    }

    private static void saveGeneral() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "consume_key_in_creative: consommer la cle en creatif | broadcast_min_rarity: rarete annoncee a tout le serveur | title_min_rarity: rarete affichee en grand titre | ring_size: nombre d'objets dans la roue (4-12). Raretes: common, uncommon, rare, epic, legendary, mythic");
        o.addProperty("consume_key_in_creative", GeneralConfig.consumeKeyInCreative);
        o.addProperty("broadcast_min_rarity", GeneralConfig.broadcastMinRarity.id);
        o.addProperty("title_min_rarity", GeneralConfig.titleMinRarity.id);
        o.addProperty("ring_size", GeneralConfig.ringSize);
        write(dir().resolve("general.json"), o);
    }

    public static synchronized void save(CrateType type) {
        CrateLoot loot = TABLES.get(type);
        if (loot == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("_aide", "Chaque recompense : item (id), count (quantite), weight (poids du tirage), rarity (common/uncommon/rare/epic/legendary/mythic), "
                + "nbt (optionnel, ex: {Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}), name (optionnel, codes couleur &), "
                + "commands (optionnel, {player} = pseudo), give_item (false = ne donne que les commandes). "
                + "Astuce : tiens un objet en main et fais /heraliz loot add " + type.id + " <poids> <rarete>. Puis /heraliz reload.");
        JsonArray arr = new JsonArray();
        for (LootEntry e : loot.entries) arr.add(e.toJson());
        root.add("rewards", arr);
        write(dir().resolve(type.id + ".json"), root);
    }

    private static void write(Path file, JsonObject o) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (IOException e) {
            HeralizCrates.LOGGER.error("[HeralizCrates] Impossible d'ecrire {}", file, e);
        }
    }
}
