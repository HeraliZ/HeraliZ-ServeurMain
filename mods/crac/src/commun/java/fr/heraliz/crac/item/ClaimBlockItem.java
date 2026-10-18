package fr.heraliz.crac.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import fr.heraliz.crac.block.ObjetAdossable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** L'armoire à outils tenue : 2 blocs de haut, elle se colle aux murs ({@link ObjetAdossable}). */
public class ClaimBlockItem extends ObjetAdossable {
    public ClaimBlockItem(Block block, Properties props) {
        super(block, props, 2);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("crac.tip.claim1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crac.tip.claim2").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("crac.tip.claim3").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
