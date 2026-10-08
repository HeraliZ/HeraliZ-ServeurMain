package fr.heraliz.crates.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Outil d'edition des caisses (operateurs) : clic droit sur une caisse pour modifier son contenu et ses chances. Le clic
 * sur une caisse est intercepte dans CommonEvents ; ici, seulement l'objet lui-meme.
 */
public class CrateEditorItem extends Item {
    public CrateEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heralizcrates.editor.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heralizcrates.editor.op").withStyle(ChatFormatting.DARK_RED));
    }

    /** Pas de casse de bloc avec l'outil (une caisse visee en creatif ne doit pas disparaitre). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean op = player.hasPermissions(2);
            player.displayClientMessage(Component.translatable(op ? "message.heralizcrates.editor.air" : "message.heralizcrates.editor.denied")
                    .withStyle(op ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
