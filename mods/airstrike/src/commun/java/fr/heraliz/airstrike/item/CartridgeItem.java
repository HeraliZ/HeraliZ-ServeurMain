package fr.heraliz.airstrike.item;

import fr.heraliz.airstrike.strike.CartridgeTier;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Strike cartridge of one level (Light, Heavy, Special). One goes into the binoculars when a strike of its level is
 * loaded and is used up by the call; the strike is chosen in the wheel.
 */
public class CartridgeItem extends Item {
    private final CartridgeTier tier;

    public CartridgeItem(CartridgeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CartridgeTier getTier() {
        return tier;
    }

    /** Level of a cartridge stack (the old single strike payloads count for the level of their strike), or null. */
    @Nullable
    public static CartridgeTier tierOf(ItemStack stack) {
        if (stack.getItem() instanceof CartridgeItem c) return c.tier;
        if (stack.getItem() instanceof PayloadItem p) return p.tier();
        return null;
    }

    /** Cartridges of this level carried by the player (read by the client wheel and by the server). */
    public static int count(Inventory inv, CartridgeTier tier) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (tierOf(s) == tier) n += s.getCount();
        }
        return n;
    }

    /** Takes one cartridge of this level from the inventory (the old payloads first). */
    public static boolean consume(Inventory inv, CartridgeTier tier) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                boolean old = s.getItem() instanceof PayloadItem;
                if ((pass == 0) == old && tierOf(s) == tier) {
                    s.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }

    /** « Mitraillage, Écran de fumée » : the strikes of a level. */
    public static MutableComponent strikeList(CartridgeTier tier) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (StrikeType t : tier.strikes()) {
            if (!first) out.append(Component.literal(", "));
            out.append(Component.translatable(t.translationKey()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(t.uiColor))));
            first = false;
        }
        return out;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tier.color)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.airstrike.cartridge.strikes", strikeList(tier)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.airstrike.cartridge.usage").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
