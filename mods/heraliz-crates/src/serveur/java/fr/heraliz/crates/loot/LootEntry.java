package fr.heraliz.crates.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.crates.HeralizCrates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Une recompense possible d'un coffre.
 *
 * Exemple JSON :
 * { "item": "minecraft:diamond", "count": 3, "weight": 10, "rarity": "rare",
 *   "nbt": "{Enchantments:[...]}", "name": "&bDiamants", "give_item": true, "commands": ["say {player}"] }
 */
public final class LootEntry {
    public String item = "minecraft:stone";
    public int count = 1;
    @Nullable
    public String nbt;
    public int weight = 10;
    public LootRarity rarity = LootRarity.COMMON;
    @Nullable
    public String name;
    public boolean giveItem = true;
    public List<String> commands = new ArrayList<>();

    public LootEntry() {
    }

    public LootEntry(String item, int count, int weight, LootRarity rarity) {
        this.item = item;
        this.count = count;
        this.weight = weight;
        this.rarity = rarity;
    }

    public LootEntry nbt(String nbt) {
        this.nbt = nbt;
        return this;
    }

    public LootEntry name(String name) {
        this.name = name;
        return this;
    }

    public LootEntry command(String cmd) {
        this.commands.add(cmd);
        return this;
    }

    public LootEntry noItem() {
        this.giveItem = false;
        return this;
    }

    public boolean isValid() {
        Item it = resolveItem();
        return it != null && it != Items.AIR && weight > 0 && count > 0;
    }

    @Nullable
    private Item resolveItem() {
        ResourceLocation rl = ResourceLocation.tryParse(item);
        if (rl == null) return null;
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        // icone de la caisse des emotes absente (HeraliZ Emote plus ancien ou absent) : etiquette a la place, la
        // recompense (une commande) reste valable au lieu de vider toute la caisse
        if ((it == null || it == Items.AIR) && "heraliz_emote".equals(rl.getNamespace())) return Items.NAME_TAG;
        return it;
    }

    /** Cree la pile d'objets reelle (count peut depasser la taille max, elle sera decoupee au don). */
    public ItemStack createStack(int amount) {
        Item it = resolveItem();
        if (it == null || it == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(it, Math.max(1, amount));
        if (nbt != null && !nbt.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbt);
                stack.setTag(tag);
            } catch (CommandSyntaxException e) {
                HeralizCrates.LOGGER.warn("[HeralizCrates] NBT invalide pour {} : {}", item, e.getMessage());
            }
        }
        if (name != null && !name.isBlank()) {
            stack.setHoverName(Component.literal(colorize(name)).withStyle(s -> s.withItalic(false)));
        }
        return stack;
    }

    /** Pile affichee dans la roue / l'apercu (quantite limitee a 64 pour l'affichage). */
    public ItemStack displayStack() {
        return createStack(Math.min(count, 64));
    }

    public static String colorize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                b.append('§');
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ NBT (sauvegarde de la recompense en cours)
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("item", item);
        t.putInt("count", count);
        if (nbt != null) t.putString("nbt", nbt);
        t.putInt("weight", weight);
        t.putString("rarity", rarity.id);
        if (name != null) t.putString("name", name);
        t.putBoolean("give", giveItem);
        ListTag cmds = new ListTag();
        for (String c : commands) cmds.add(StringTag.valueOf(c));
        t.put("commands", cmds);
        return t;
    }

    public static LootEntry fromTag(CompoundTag t) {
        LootEntry e = new LootEntry();
        e.item = t.getString("item");
        e.count = Math.max(1, t.getInt("count"));
        e.nbt = t.contains("nbt") ? t.getString("nbt") : null;
        e.weight = t.getInt("weight");
        e.rarity = LootRarity.byId(t.getString("rarity"));
        e.name = t.contains("name") ? t.getString("name") : null;
        e.giveItem = !t.contains("give") || t.getBoolean("give");
        ListTag cmds = t.getList("commands", Tag.TAG_STRING);
        for (int i = 0; i < cmds.size(); i++) e.commands.add(cmds.getString(i));
        return e;
    }

    // ------------------------------------------------------------------ JSON (fichiers de config)
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("item", item);
        o.addProperty("count", count);
        o.addProperty("weight", weight);
        o.addProperty("rarity", rarity.id);
        if (nbt != null && !nbt.isBlank()) o.addProperty("nbt", nbt);
        if (name != null && !name.isBlank()) o.addProperty("name", name);
        if (!giveItem) o.addProperty("give_item", false);
        if (!commands.isEmpty()) {
            JsonArray a = new JsonArray();
            for (String c : commands) a.add(c);
            o.add("commands", a);
        }
        return o;
    }

    public static LootEntry fromJson(JsonObject o) {
        LootEntry e = new LootEntry();
        e.item = o.has("item") ? o.get("item").getAsString() : "minecraft:air";
        e.count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
        e.weight = o.has("weight") ? Math.max(0, o.get("weight").getAsInt()) : 10;
        e.rarity = LootRarity.byId(o.has("rarity") ? o.get("rarity").getAsString() : "common");
        e.nbt = o.has("nbt") ? o.get("nbt").getAsString() : null;
        e.name = o.has("name") ? o.get("name").getAsString() : null;
        e.giveItem = !o.has("give_item") || o.get("give_item").getAsBoolean();
        if (o.has("commands") && o.get("commands").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("commands")) e.commands.add(el.getAsString());
        }
        return e;
    }
}
