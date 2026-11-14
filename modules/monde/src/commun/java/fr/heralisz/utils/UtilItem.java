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

/** Plan de fabrication ou composant de craft : nom coloré selon la rareté, badge de rareté et type en infobulle. */
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

    /**
     * Badge de la rareté (14/11, retour du propriétaire : « juste une fois le petit encadré avec écrit EPIC ») : le même
     * caractère que fr.heraliz.loot.Rarete (HeraliZ Loot), une petite image de la police des rangs (« COMMON »,
     * « UNCOMMON », « RARE », « EPIC », « LEGENDARY »).
     */
    private static String badge(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "\ue804";
            case "rare" -> "\ue803";
            case "epic" -> "\ue80d";
            case "legendary" -> "\ue805";
            default -> "\ue806";
        };
    }

    /** Style du badge : blanc (l'image garde ses couleurs), police par défaut (la seule qui a ce caractère), sans gras. */
    private static final Style STYLE_BADGE = Style.EMPTY.withColor(0xFFFFFF).withFont(Style.DEFAULT_FONT).withBold(false)
            .withItalic(false).withUnderlined(false);

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(rarityStyle(rarity));
    }

    /**
     * 14/11 : la rareté est le badge, plus le mot « Épique » (cet objet n'est pas dans raretes.json : HeraliZ Loot ne pose
     * pas de badge pour lui, il n'y en a qu'un).
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(badge(rarity)).setStyle(STYLE_BADGE));
        lines.add(Component.translatable("tooltip.heralisz_utils." + kind).withStyle(ChatFormatting.GRAY));
    }
}
