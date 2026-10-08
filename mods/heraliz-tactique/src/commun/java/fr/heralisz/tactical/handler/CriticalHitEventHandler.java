package fr.heralisz.tactical.handler;

import fr.heralisz.tactical.init.ModEnchantment;
import fr.heralisz.tactical.util.VectorUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.FORGE)
public class CriticalHitEventHandler {
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onCriticalHit(CriticalHitEvent event) {
      Player player = event.getEntity();
      int level = player.getMainHandItem().getEnchantmentLevel((Enchantment)ModEnchantment.BACKSTAB.get());
      if (level > 0) {
         Entity target = event.getTarget();
         Vec3 origin = player.getEyePosition();
         Vec3 positionVector = target.position().add(0.0, target.getBbHeight() / 2.0F, 0.0).subtract(origin);
         positionVector = new Vec3(positionVector.x, 0.0, positionVector.z).normalize();
         Vec3 targetForward = target.getForward();
         double angle = VectorUtil.angleBetween(positionVector, targetForward);
         if (angle <= 45.0) {
            event.setResult(Result.ALLOW);
            event.setDamageModifier(event.getOldDamageModifier() + level * 0.25F);
         }
      }
   }
}
