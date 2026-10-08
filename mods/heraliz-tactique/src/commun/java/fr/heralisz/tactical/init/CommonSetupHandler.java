package fr.heralisz.tactical.init;

import fr.heralisz.tactical.capability.CombatProperties;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import fr.heralisz.tactical.capability.CustomItemCoolDowns;
import fr.heralisz.tactical.capability.CustomItemCoolDownsProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "heralisz_tactical")
public class CommonSetupHandler {
   @SubscribeEvent
   public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
      if (event.getObject() instanceof Player player) {
         event.addCapability(CustomItemCoolDowns.ID, new CustomItemCoolDownsProvider(player));
         event.addCapability(CombatProperties.ID, new CombatPropertiesProvider(player));
      }
   }
}
