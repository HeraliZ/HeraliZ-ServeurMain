package fr.heralisz.n07.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Irradiation : dégâts réguliers qui ignorent l'armure, plus rapides à chaque niveau. */
public class IrradiatedEffect extends MobEffect {
   public IrradiatedEffect() {
      super(MobEffectCategory.HARMFUL, 0x7CFF3A);
   }

   /** Dégâts : dans IrradiatedEffectServeur (usine HeraliszN07.IRRADIATED_USINE) ; neutre ici (côté client, rien n'était infligé). */
   @Override
   public void applyEffectTick(LivingEntity entity, int amplifier) {
   }

   @Override
   public boolean isDurationEffectTick(int duration, int amplifier) {
      int every = Math.max(10, 40 >> amplifier);
      return duration % every == 0;
   }
}
