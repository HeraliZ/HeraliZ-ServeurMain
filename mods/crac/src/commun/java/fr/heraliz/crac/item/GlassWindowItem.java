package fr.heraliz.crac.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

/** Window insert (glass pane or metal bars), fitted by right-clicking a window piece (see StructureService.onUse). */
public class GlassWindowItem extends Item {
    public GlassWindowItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("crac.tip.window1").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("crac.tip.window2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
