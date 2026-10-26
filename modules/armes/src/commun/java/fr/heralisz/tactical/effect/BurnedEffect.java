package fr.heralisz.tactical.effect;

import java.util.Collections;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class BurnedEffect extends MobEffect {
   public BurnedEffect(int pColor) {
      super(MobEffectCategory.HARMFUL, pColor);
   }

   public List<ItemStack> getCurativeItems() {
      return Collections.emptyList();
   }

   public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
      if (pLivingEntity.isOnFire()) {
         int r = pLivingEntity.getRemainingFireTicks();
         if (r < 40) {
            pLivingEntity.setRemainingFireTicks(r + 40);
         }
      }
   }

   public boolean isDurationEffectTick(int duration, int amplifier) {
      return duration % 20 == 0;
   }
}
