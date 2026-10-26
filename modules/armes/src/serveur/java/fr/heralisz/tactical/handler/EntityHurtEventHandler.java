package fr.heralisz.tactical.handler;

import fr.heralisz.tactical.init.ModEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.FORGE)
public class EntityHurtEventHandler {
   @SubscribeEvent
   public static void onEntityHurt(LivingHurtEvent event) {
      if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
         MobEffectInstance effect = event.getEntity().getEffect((MobEffect)ModEffects.FLAMMABLE.get());
         if (effect != null) {
            float additionalDamage = 2.0F + effect.getAmplifier() * 0.5F;
            event.setAmount(event.getAmount() * additionalDamage);
         }
      }
   }
}
