package fr.heraliz.reanimation.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(modid = "reanimation", bus = Bus.MOD)
public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "reanimation");
   public static final RegistryObject<Item> BOT_SPAWN_EGG = ITEMS.register(
      "bot_spawn_egg", () -> new ForgeSpawnEggItem(ModEntities.BOT, 4872762, 13152906, new Properties())
   );

   private ModItems() {
   }

   @SubscribeEvent
   public static void onTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
         event.accept(BOT_SPAWN_EGG);
      }
   }
}
