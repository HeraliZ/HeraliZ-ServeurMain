package fr.heralisz.tactical.api.animation;

import fr.heralisz.tactical.item.FlashShieldItem;
import net.minecraft.world.item.ItemStack;

public class FlashShieldAnimationStateContext extends BaseAnimationStateContext {
   private ItemStack currentItem = ItemStack.EMPTY;
   private int usingTick = 0;
   private boolean using = false;
   private int prepareTime = 0;
   private float walkDistAnchor = 0.0F;

   @Override
   public void setCurrentItem(ItemStack currentItem) {
      this.currentItem = currentItem;
      if (currentItem.getItem() instanceof FlashShieldItem flashShieldItem) {
         this.prepareTime = flashShieldItem.getMaxUsingTick(currentItem);
      }
   }

   public int getUsingTick() {
      return this.usingTick;
   }

   public void setUsingTick(int throwTime) {
      this.usingTick = throwTime;
   }

   public boolean isUsing() {
      return this.using;
   }

   public void setUsing(boolean using) {
      this.using = using;
   }

   public int getPrepareTime() {
      return this.prepareTime;
   }
}
