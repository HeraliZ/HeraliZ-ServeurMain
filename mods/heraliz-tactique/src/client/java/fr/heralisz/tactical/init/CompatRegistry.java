package fr.heralisz.tactical.init;

import fr.heralisz.tactical.client.gui.ClothConfigScreen;
import fr.heralisz.tactical.compat.cloth.MenuIntegration;
import fr.heralisz.tactical.compat.player_animator.PlayerAnimatorHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Fusion (heraliz_tactique) : partie client (cloth_config, playeranimator : affichage seulement). */
@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.MOD, value = Dist.CLIENT)
public class CompatRegistry {
   public static final String CLOTH_CONFIG = "cloth_config";
   public static final String PLAYER_ANIMATOR = "playeranimator";

   @SubscribeEvent
   public static void onEnqueue(InterModEnqueueEvent event) {
      event.enqueueWork(() -> checkModLoad("cloth_config", () -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> MenuIntegration::registerModsPage)));
      event.enqueueWork(() -> {
         if (FMLEnvironment.dist == Dist.CLIENT) {
            ClothConfigScreen.registerNoClothConfigPage();
         }
      });
   }

   @SubscribeEvent
   public static void onCommonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> checkModLoad("playeranimator", () -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> PlayerAnimatorHelper::initIntegration)));
   }

   public static void checkModLoad(String modId, Runnable runnable) {
      if (ModList.get().isLoaded(modId)) {
         runnable.run();
      }
   }
}
