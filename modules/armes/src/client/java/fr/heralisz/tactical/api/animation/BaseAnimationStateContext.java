package fr.heralisz.tactical.api.animation;

import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import java.util.Optional;
import java.util.function.Function;
import fr.heralisz.tactical.api.item.IThrowable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class BaseAnimationStateContext extends ItemAnimationStateContext {
   private ItemStack currentItem = ItemStack.EMPTY;
   private int usingTick = 0;
   private boolean using = false;
   private int prepareTime = 0;
   private float walkDistAnchor = 0.0F;

   public void setCurrentItem(ItemStack currentItem) {
      this.currentItem = currentItem;
      if (currentItem.getItem() instanceof IThrowable iThrowable) {
         this.prepareTime = iThrowable.getThrowableIndex(currentItem).map(index -> index.getData().getPrepareTime()).orElse(0);
      }
   }

   protected <T> Optional<T> processCameraEntity(Function<Entity, T> processor) {
      Entity entity = Minecraft.getInstance().cameraEntity;
      return entity != null ? Optional.ofNullable(processor.apply(entity)) : Optional.empty();
   }

   public long getCurrentTimestamp() {
      return System.currentTimeMillis();
   }

   public boolean isInputUp() {
      return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.up).orElse(false);
   }

   public boolean isInputDown() {
      return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.down).orElse(false);
   }

   public boolean isInputLeft() {
      return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.left).orElse(false);
   }

   public boolean isInputRight() {
      return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.right).orElse(false);
   }

   public boolean isInputJumping() {
      return Optional.ofNullable(Minecraft.getInstance().player).map(player -> player.input.jumping).orElse(false);
   }

   public boolean isOnGround() {
      return this.<Boolean>processCameraEntity(Entity::onGround).orElse(false);
   }

   public boolean isCrouching() {
      return this.<Boolean>processCameraEntity(Entity::isCrouching).orElse(false);
   }

   public void anchorWalkDist() {
      this.processCameraEntity(entity -> {
         this.walkDistAnchor = entity.walkDist + (entity.walkDist - entity.walkDistO) * this.partialTicks;
         return null;
      });
   }

   public float getWalkDist() {
      return this.<Float>processCameraEntity(entity -> {
         float currentWalkDist = entity.walkDist + (entity.walkDist - entity.walkDistO) * this.partialTicks;
         return currentWalkDist - this.walkDistAnchor;
      }).orElse(0.0F);
   }
}
