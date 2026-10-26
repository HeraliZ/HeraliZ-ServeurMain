package fr.heraliz.airstrike.item;

import fr.heraliz.airstrike.strike.CartridgeTier;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
 * loaded; the strike is chosen in the wheel. 26/10 (demande du propriétaire) : une cartouche sert {@link #UTILISATIONS}
 * fois ; une cartouche entamée (rendue par les jumelles) porte le reste dans {@link #TAG_RESTE} et ne s'empile plus avec
 * les neuves. Fabriquées seulement (Table d'armurier, onglet Divers).
 */
public class CartridgeItem extends Item {
    /** Frappes par cartouche neuve. */
    public static final int UTILISATIONS = 2;
    /** Frappes restantes d'une cartouche entamée (absent : neuve). */
    public static final String TAG_RESTE = "Reste";
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

    /** Frappes que donne une pile (chaque objet) : neuve 2, entamée 1, ancienne recharge 1. */
    public static int utilisations(ItemStack stack) {
        if (stack.getItem() instanceof PayloadItem) return 1;
        CompoundTag t = stack.getTag();
        return t != null && t.contains(TAG_RESTE) ? Math.max(1, Math.min(UTILISATIONS, t.getInt(TAG_RESTE))) : UTILISATIONS;
    }

    /** Une cartouche de ce niveau qui donne encore {@code reste} frappes (entamée si moins que neuve). */
    public static ItemStack avecReste(ItemStack stack, int reste) {
        if (reste < UTILISATIONS) stack.getOrCreateTag().putInt(TAG_RESTE, Math.max(1, reste));
        return stack;
    }

    /** Frappes de ce niveau que le joueur porte (cartouches × utilisations restantes ; roue des frappes et serveur). */
    public static int count(Inventory inv, CartridgeTier tier) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (tierOf(s) == tier) n += s.getCount() * utilisations(s);
        }
        return n;
    }

    /**
     * Prend une cartouche de ce niveau dans l'inventaire (les anciennes recharges, puis les entamées, puis les neuves) ;
     * rend le nombre de frappes qu'elle donne, 0 s'il n'y en a pas.
     */
    public static int consume(Inventory inv, CartridgeTier tier) {
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (tierOf(s) != tier) continue;
                boolean old = s.getItem() instanceof PayloadItem;
                int u = utilisations(s);
                if ((pass == 0 && old) || (pass == 1 && !old && u < UTILISATIONS) || (pass == 2 && !old && u >= UTILISATIONS)) {
                    s.shrink(1);
                    return u;
                }
            }
        }
        return 0;
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
        tooltip.add(Component.translatable("tooltip.airstrike.cartridge.uses", utilisations(stack), UTILISATIONS).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.airstrike.cartridge.strikes", strikeList(tier)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.airstrike.cartridge.usage").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
