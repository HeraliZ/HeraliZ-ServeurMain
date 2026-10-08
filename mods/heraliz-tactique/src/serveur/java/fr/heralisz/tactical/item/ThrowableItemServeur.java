package fr.heralisz.tactical.item;

import javax.annotation.ParametersAreNonnullByDefault;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import fr.heralisz.tactical.entity.ThrowableItemEntity;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.item.throwable.explode.ExplodeThrowableData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

/**
 * Partie SERVEUR de {@link ThrowableItem} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun (ModItems) dès que la partie serveur est dans le jar (objet unique, partagé par les
 * deux côtés avec -complet : le code d'origine garde ses tests isClientSide). Ne jamais faire « new ThrowableItem(...) ».
 */
public class ThrowableItemServeur extends ThrowableItem {
   public ThrowableItemServeur() {
      super();
   }

   public void onThrow(Level world, LivingEntity entity, ItemStack stack, ThrowableIndex<?, ?> index) {
      ThrowableItemEntity throwable = index.createEntity(stack, entity);
      if (index.getData().isCookable()) {
         int newLife = throwable.getLife() - (entity.getTicksUsingItem() - index.getData().getPrepareTime());
         newLife = Math.max(newLife, 0);
         throwable.setLife(newLife);
      }

      world.addFreshEntity(throwable);
      ResourceLocation id = index.getData().getCooldownCategory();
      if (id != null) {
         entity.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(cap -> cap.addCooldown(id, index.getData().getCooldown()));
      }

      stack.shrink(1);
      if (index.getData() instanceof ExplodeThrowableData explode && explode.getExplode().isRemoteDetonation()) {
         ItemStack detonatorStack = new ItemStack((ItemLike)ModItems.DETONATOR.get());
         if (detonatorStack.getItem() instanceof DetonatorItem detonatorItem) {
            detonatorItem.recordEntity(throwable, detonatorStack);
         }

         entity.setItemInHand(InteractionHand.MAIN_HAND, detonatorStack);
      }
   }

   @ParametersAreNonnullByDefault
   public void onUseTick(Level world, LivingEntity entity, ItemStack stack, int pRemainingUseDuration) {
      this.getThrowableIndex(stack).ifPresent(index -> {
         ThrowableData data = index.getData();
         if (data.isCookable() && entity.getTicksUsingItem() >= data.getPrepareTime() + data.getEntityData().getLifeTime() && !world.isClientSide()) {
            this.onThrow(world, entity, stack, (ThrowableIndex<?, ?>)index);
            entity.stopUsingItem();
         }
      });
   }

   @ParametersAreNonnullByDefault
   public void releaseUsing(ItemStack stack, Level world, LivingEntity entity, int timeLeft) {
      this.getThrowableIndex(stack).ifPresent(index -> {
         if (entity.getTicksUsingItem() >= index.getData().getPrepareTime() && !world.isClientSide()) {
            this.onThrow(world, entity, stack, (ThrowableIndex<?, ?>)index);
         }
      });
   }
}
