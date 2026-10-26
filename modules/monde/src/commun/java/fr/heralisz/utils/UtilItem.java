package fr.heralisz.utils;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Plan de fabrication ou composant de craft : nom coloré selon la rareté, type et rareté en infobulle. */
public class UtilItem extends Item {
    protected final String kind, rarity;

    public UtilItem(String kind, String rarity) {
        super(new Properties().stacksTo("plan".equals(kind) ? 16 : 64));
        this.kind = kind;
        this.rarity = rarity;
    }

    static Style rarityStyle(String rarity) {
        int rgb = switch (rarity) {
            case "uncommon" -> 0x55FF55;
            case "rare" -> 0x5599FF;
            case "epic" -> 0xC060FF;
            case "legendary" -> 0xFFAA00;
            default -> 0xFFFFFF;
        };
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(rarityStyle(rarity));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("rarity.heralisz_utils." + rarity).withStyle(rarityStyle(rarity)));
        lines.add(Component.translatable("tooltip.heralisz_utils." + kind).withStyle(ChatFormatting.GRAY));
    }
}
