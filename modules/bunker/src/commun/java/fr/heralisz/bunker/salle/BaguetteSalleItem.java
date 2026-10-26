package fr.heralisz.bunker.salle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Baguette des salles à carte (staff, façon WorldEdit) : clic gauche sur un bloc = coin 1, clic droit = coin 2. Le
 * serveur garde la sélection et l'envoie au staff qui tient la baguette (contour dessiné chez lui). Elle ne casse
 * jamais de bloc. Les joueurs sans droits (op 2) ne peuvent rien en faire.
 */
public class BaguetteSalleItem extends Item {
   public BaguetteSalleItem() {
      super(new Properties().stacksTo(1));
   }

   /** Avant le clic droit du bloc lui-même : on peut viser un conteneur, une porte, un lecteur. */
   @Override
   public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
      if (ctx.getPlayer() == null) return InteractionResult.PASS;
      if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
         SallesPont.coin(sp, ctx.getClickedPos(), 2);
      }
      return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
   }

   @Override
   public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
      return false;                                                 // clic gauche = coin 1, jamais un bloc cassé
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.heralisz_bunker.baguette_salle.desc1").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.heralisz_bunker.baguette_salle.desc2").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.heralisz_bunker.baguette_salle.desc3").withStyle(ChatFormatting.DARK_GRAY));
   }
}
