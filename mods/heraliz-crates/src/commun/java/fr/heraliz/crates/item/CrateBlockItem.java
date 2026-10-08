package fr.heraliz.crates.item;

import fr.heraliz.crates.pont.Crochet;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Item servant a poser un coffre (rendu 3D anime dans l'inventaire).
 */
public class CrateBlockItem extends BlockItem {
    public final CrateType type;

    public CrateBlockItem(Block block, CrateType type, Properties properties) {
        super(block, properties);
        this.type = type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(type.glow).withBold(true));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heralizcrates.crate.key", type.keyName()).withStyle(ChatFormatting.GRAY));
        int[] cells = fr.heraliz.crates.block.CrateShapes.cells(type);
        tooltip.add(Component.translatable("tooltip.heralizcrates.crate.size", cells[0], cells[2], cells[1]).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcrates.crate.preview").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcrates.tier", type.tier.displayName()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Rendu 3D dans l'inventaire : branché par la partie client (client/ClientRenderers.itemRenderer). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
