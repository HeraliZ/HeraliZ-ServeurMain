package fr.heraliz.crac.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class ClaimBlockItem extends BlockItem {
    public ClaimBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("crac.tip.claim1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crac.tip.claim2").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("crac.tip.claim3").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
