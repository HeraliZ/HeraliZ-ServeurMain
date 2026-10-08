package fr.heralisz.bunker.keycard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Carte d'accès. Clic droit sur un lecteur = on glisse la carte (cinématique) ; le lecteur décide. Les passages réussis
 * usent la carte (compteur NBT « Uses », limite réglable dans la config) ; elle casse au dernier passage.
 */
public class KeycardItem extends Item {
   private static final String USES = "Uses";
   public final AccessLevel level;

   public KeycardItem(AccessLevel level) {
      super(new Properties().stacksTo(1));
      this.level = level;
   }

   @Override
   public InteractionResult useOn(UseOnContext ctx) {
      Level world = ctx.getLevel();
      BlockState state = world.getBlockState(ctx.getClickedPos());
      if (!(state.getBlock() instanceof CardReaderBlock reader) || ctx.getPlayer() == null) {
         return InteractionResult.PASS;
      }
      if (state.getValue(CardReaderBlock.STATUS) != CardReaderBlock.Status.IDLE) {
         return InteractionResult.FAIL;
      }
      Player player = ctx.getPlayer();
      Direction facing = state.getValue(CardReaderBlock.FACING);
      if (!ReaderGeometry.inReach(player, ctx.getClickedPos(), facing)) {
         if (world.isClientSide) {
            player.displayClientMessage(Component.translatable("msg.heralisz_bunker.keycard.closer").withStyle(ChatFormatting.YELLOW), true);
         }
         return InteractionResult.FAIL;
      }
      player.getCooldowns().addCooldown(this, CardReaderBlock.READ_TICKS + 20);
      if (!world.isClientSide) {
         reader.swipe(world, ctx.getClickedPos(), state, player, ctx.getItemInHand(), this.level);   // cinématique envoyée par le serveur
      }
      return InteractionResult.sidedSuccess(world.isClientSide);
   }

   /** Un passage réussi : +1 usage, la carte casse à la limite (0 = illimitée). */
   public void consumeUse(ItemStack stack, Player player) {
      int max = KeycardConfig.uses(this.level);
      if (max <= 0 || player.getAbilities().instabuild) {
         return;
      }
      CompoundTag tag = stack.getOrCreateTag();
      int used = tag.getInt(USES) + 1;
      if (used >= max) {
         player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
         stack.shrink(1);
      } else {
         tag.putInt(USES, used);
      }
   }

   private int remaining(ItemStack stack) {
      int max = KeycardConfig.uses(this.level);
      return max <= 0 ? -1 : max - (stack.hasTag() ? stack.getTag().getInt(USES) : 0);
   }

   @Override
   public boolean isBarVisible(ItemStack stack) {
      return stack.hasTag() && stack.getTag().getInt(USES) > 0 && KeycardConfig.uses(this.level) > 0;
   }

   @Override
   public int getBarWidth(ItemStack stack) {
      int max = KeycardConfig.uses(this.level);
      return max <= 0 ? 13 : Math.round(13.0F * this.remaining(stack) / max);
   }

   @Override
   public int getBarColor(ItemStack stack) {
      int max = KeycardConfig.uses(this.level);
      float f = max <= 0 ? 1.0F : Math.max(0.0F, (float) this.remaining(stack) / max);
      return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
   }

   @Override
   public Component getName(ItemStack stack) {
      return super.getName(stack).copy().withStyle(this.level.color);
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.heralisz_bunker.keycard.desc", Component.translatable("keycard.heralisz_bunker." + this.level.id)
         .withStyle(this.level.color)).withStyle(ChatFormatting.GRAY));
      int left = this.remaining(stack);
      tooltip.add(left < 0 ? Component.translatable("item.heralisz_bunker.keycard.unlimited").withStyle(ChatFormatting.DARK_GRAY)
         : Component.translatable("item.heralisz_bunker.keycard.uses", left).withStyle(ChatFormatting.DARK_GRAY));
   }
}
