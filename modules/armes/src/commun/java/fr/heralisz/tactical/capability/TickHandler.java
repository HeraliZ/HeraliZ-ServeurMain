package fr.heralisz.tactical.capability;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "heralisz_tactical")
public class TickHandler {
   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.START) {
         Player player = event.player;
         player.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(CustomItemCoolDowns::tick);
         player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(CombatProperties::tick);
      }
   }
}
