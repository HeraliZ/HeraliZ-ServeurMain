package fr.heralisz.tactical.item;

import fr.heraliz.tactique.pont.Crochet;
import com.tacz.guns.api.item.IAnimationItem;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.inventory.tooltip.ThrowableTooltip;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

public class ThrowableItem extends Item implements IAnimationItem, IThrowable {
   public ThrowableItem() {
      super(new Properties().stacksTo(1));
   }

   public int getMaxStackSize(ItemStack stack) {
      return this.getThrowableIndex(stack).map(ThrowableIndex::getMaxStackSize).orElse(1);
   }

   @ParametersAreNonnullByDefault
   public int getUseDuration(ItemStack pStack) {
      return 72000;
   }

   public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
      return true;
   }

   @NotNull
   public UseAnim getUseAnimation(@NotNull ItemStack pStack) {
      return UseAnim.BOW;
   }

   /** Fusion (heraliz_tactique) : rendu en main (code client d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      RENDU_CLIENT.accept(consumer);
   }

   @ParametersAreNonnullByDefault
   @NotNull
   public InteractionResultHolder<ItemStack> use(Level pLevel, Player player, InteractionHand pUsedHand) {
      if (pUsedHand == InteractionHand.OFF_HAND) {
         return InteractionResultHolder.fail(player.getItemInHand(pUsedHand));
      }

      ItemStack stack = player.getItemInHand(pUsedHand);
      boolean flag = this.getThrowableIndex(stack)
         .map(index -> index.getData().getCooldownCategory())
         .map(id -> player.getCapability(CustomItemCoolDownsProvider.CAPABILITY).map(cap -> cap.isOnCooldown(id)).orElse(false))
         .orElse(false);
      if (!flag) {
         player.startUsingItem(pUsedHand);
      }

      return InteractionResultHolder.consume(stack);
   }

   public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
      super.onStopUsing(stack, entity, count);
   }

   public boolean useOnRelease(@NotNull ItemStack pStack) {
      return true;
   }

   @NotNull
   public String getDescriptionId(@NotNull ItemStack stack) {
      return this.getThrowableIndex(stack).map(ThrowableIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
   }

   @Override
   public boolean isSame(ItemStack stack1, ItemStack stack2) {
      return IThrowable.super.isSame(stack1, stack2);
   }

   public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
      return this.getThrowableIndex(stack).isEmpty() ? Optional.empty() : Optional.of(new ThrowableTooltip(stack));
   }
}
