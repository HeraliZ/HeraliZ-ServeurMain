package fr.heraliz.crac.build.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * One material requirement, parsed from a config entry {@code "modid:item=count"} or {@code "#modid:tag=count"}.
 * An entry naming an unknown item or an empty tag is kept as an unpayable cost ({@code problem} set): a typo in the
 * config blocks the action with a clear message instead of silently making it cheaper or free.
 */
public record BuildCost(Ingredient ingredient, int count, @Nullable String label, @Nullable String problem) {
    public BuildCost(Ingredient ingredient, int count, @Nullable String label) {
        this(ingredient, count, label, null);
    }

    /** Never null: a malformed entry, an unknown item or an empty tag gives an unpayable cost. */
    public static BuildCost parse(String entry) {
        if (entry == null) {
            return invalid("?", 1, "malformed cost entry");
        }
        int sep = entry.indexOf('=');
        String key = (sep < 0 ? entry : entry.substring(0, sep)).trim();
        int count;
        try {
            count = sep < 0 ? 0 : Integer.parseInt(entry.substring(sep + 1).trim());
        } catch (NumberFormatException e) {
            count = 0;
        }
        if (sep <= 0 || count <= 0) {
            return invalid(entry, 1, "malformed cost entry");
        }
        boolean isTag = key.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? key.substring(1) : key);
        if (id == null) {
            return invalid(key, count, "malformed id");
        }
        if (isTag) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            boolean known = BuiltInRegistries.ITEM.getTag(tag).map(set -> set.size() > 0).orElse(false);
            return known ? new BuildCost(Ingredient.of(tag), count, pretty(id.getPath())) : invalid(key, count, "unknown or empty tag");
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null && item != Items.AIR ? new BuildCost(Ingredient.of(item), count, null) : invalid(key, count, "unknown item");
    }

    private static BuildCost invalid(String label, int count, String problem) {
        return new BuildCost(Ingredient.EMPTY, count, label, problem);
    }

    public static List<BuildCost> parseAll(List<? extends String> entries) {
        if (entries == null) {
            return List.of();
        }
        List<BuildCost> out = new ArrayList<>();
        for (String e : entries) {
            out.add(parse(e));
        }
        return out;
    }

    public boolean isValid() {
        return this.problem == null;
    }

    public boolean matches(ItemStack stack) {
        return this.problem == null && !stack.isEmpty() && this.ingredient.test(stack);
    }

    /** How many distinct items this cost accepts: 1 for an item, the tag size for a tag. */
    public int variety() {
        return this.ingredient.getItems().length;
    }

    public ItemStack icon() {
        ItemStack[] items = this.ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY : new ItemStack(items[0].getItem(), this.count);
    }

    public Component displayName() {
        if (this.problem != null) {
            // the English problem stays in the record (server log, config check); the player reads it in their language
            return Component.translatableWithFallback("crac.cost.invalid", "%s (%s : préviens un admin)", this.label, problemText(this.problem));
        }
        if (this.label != null) {
            return Component.literal(this.label);
        }
        ItemStack[] items = this.ingredient.getItems();
        return items.length == 0 ? Component.literal("?") : items[0].getHoverName();
    }

    /** The problem of an unpayable cost (see {@link #parse}) in the player's language. */
    private static Component problemText(String problem) {
        return switch (problem) {
            case "malformed cost entry" -> Component.translatableWithFallback("crac.cost.malformed_entry", "entrée de coût mal écrite");
            case "malformed id" -> Component.translatableWithFallback("crac.cost.malformed_id", "identifiant mal écrit");
            case "unknown or empty tag" -> Component.translatableWithFallback("crac.cost.unknown_tag", "tag inconnu ou vide");
            case "unknown item" -> Component.translatableWithFallback("crac.cost.unknown_item", "objet inconnu");
            default -> Component.literal(problem);
        };
    }

    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String p : path.split("[_/]")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
