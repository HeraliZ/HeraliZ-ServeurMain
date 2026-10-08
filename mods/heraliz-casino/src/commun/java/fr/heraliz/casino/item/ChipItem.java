package fr.heraliz.casino.item;

import fr.heraliz.casino.economy.Chips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Jeton de casino d'une valeur donnee.
 */
public class ChipItem extends Item {
    public final int value;

    public ChipItem(int value, Properties properties) {
        super(properties);
        this.value = value;
    }

    @Override
    public Component getName(ItemStack stack) {
        int k = Chips.indexOf(value);
        final int c = k >= 0 ? Chips.COLORS[k] : 0xFFFFFF;
        // nom propre a chaque piece (metal), traduit dans les fichiers de langue : jeton de fer, de cuivre...
        return Component.translatable(getDescriptionId(stack), Chips.format(value)).withStyle(s -> s.withColor(c).withBold(value >= 1000));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heralizcasino.chip.value", Chips.format(value)).withStyle(ChatFormatting.GOLD));
        if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("tooltip.heralizcasino.chip.stack", Chips.format((long) value * stack.getCount()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("tooltip.heralizcasino.chip.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
