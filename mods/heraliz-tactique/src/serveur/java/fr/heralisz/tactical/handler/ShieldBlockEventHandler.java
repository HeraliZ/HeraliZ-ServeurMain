package fr.heralisz.tactical.handler;

import fr.heralisz.tactical.item.FlashShieldItem;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SShieldShake;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.FORGE)
public class ShieldBlockEventHandler {
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onShieldBlock(ShieldBlockEvent event) {
      LivingEntity livingEntity = event.getEntity();
      ItemStack stack = livingEntity.getMainHandItem();
      if (stack.getItem() instanceof FlashShieldItem flashShieldItem) {
         int restDurability = stack.getMaxDamage() - stack.getDamageValue();
         restDurability = Math.max(0, restDurability);
         float blockedDamage = Math.min(event.getBlockedDamage(), restDurability);
         event.setBlockedDamage(blockedDamage);
         stack.hurtAndBreak((int)blockedDamage, livingEntity, e -> {});
         if (blockedDamage >= 1.0F && livingEntity instanceof ServerPlayer serverPlayer) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SShieldShake());
         }
      }
   }
}
