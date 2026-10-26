package fr.heralisz.tactical.item;

import fr.heraliz.tactique.pont.Crochet;
import com.tacz.guns.api.item.IAnimationItem;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.inventory.tooltip.ConsumableTooltip;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConsumableItem extends Item implements IAnimationItem, IConsumable {
   public ConsumableItem() {
      super(new Properties().stacksTo(1));
   }

   @Nullable
   public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
      return this.getConsumableIndex(stack).map(ConsumableIndex::getFoodProperties).orElse(null);
   }

   public int getMaxStackSize(ItemStack stack) {
      return this.getConsumableIndex(stack).map(ConsumableIndex::getMaxStackSize).orElse(1);
   }

   public int getMaxDamage(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().getMaxDurability()).orElse(0);
   }

   public boolean isDamageable(ItemStack stack) {
      return this.getMaxDamage(stack) > 0;
   }

   public int getUseDuration(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().isToggleUse() ? 72000 : index.getData().getUseDuration()).orElse(0);
   }

   @NotNull
   public UseAnim getUseAnimation(@NotNull ItemStack stack) {
      return UseAnim.DRINK;
   }

   @NotNull
   public SoundEvent getDrinkingSound() {
      return SoundEvents.EMPTY;
   }

   @Override
   public int getDrawTime(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().getDrawTime()).orElse(0);
   }

   @Override
   public int getPutAwayTime(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().getPutAwayTime()).orElse(0);
   }

   /** Fusion (heraliz_tactique) : rendu en main (code client d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      RENDU_CLIENT.accept(consumer);
   }

   @NotNull
   public String getDescriptionId(@NotNull ItemStack stack) {
      return this.getConsumableIndex(stack).map(ConsumableIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
   }

   @ParametersAreNonnullByDefault
   @NotNull
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
      if (usedHand == InteractionHand.OFF_HAND) {
         return InteractionResultHolder.fail(player.getItemInHand(usedHand));
      }

      ItemStack stack = player.getItemInHand(usedHand);
      boolean drawing = player.getCapability(CombatPropertiesProvider.CAPABILITY).map(cap -> cap.getCoolDownTick() > 0).orElse(false);
      if (drawing) {
         return InteractionResultHolder.fail(stack);
      }

      boolean onCooldown = this.getCoolDownId(stack)
         .map(id -> player.getCapability(CustomItemCoolDownsProvider.CAPABILITY).map(cap -> cap.isOnCooldown(id)).orElse(false))
         .orElse(false);
      if (onCooldown) {
         return InteractionResultHolder.fail(stack);
      }

      player.startUsingItem(usedHand);
      return InteractionResultHolder.consume(stack);
   }

   @ParametersAreNonnullByDefault
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      // commun (client) : l'original ne faisait rien côté client ; soins, effets et consommation : ConsumableItemServeur
      return stack;
   }

   @Override
   public boolean isSame(ItemStack stack1, ItemStack stack2) {
      return IConsumable.super.isSame(stack1, stack2);
   }

   public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
      return this.getConsumableIndex(stack).isEmpty() ? Optional.empty() : Optional.of(new ConsumableTooltip(stack));
   }
}
