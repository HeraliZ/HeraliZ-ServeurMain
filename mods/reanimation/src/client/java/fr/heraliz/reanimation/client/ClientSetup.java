package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.ReanimationMod;
import fr.heraliz.reanimation.core.Downed;
import fr.heraliz.reanimation.registry.ModEntities;
import fr.heraliz.reanimation.registry.ModMenus;
import java.lang.reflect.Field;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "reanimation", bus = Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   // Touches par defaut (1.8.1) choisies libres parmi toutes les touches du profil RLZ Test (mods + options.txt) :
   // avant, K (abandonner) = menu des clans, V (executer) = zoom TaCZ + chat vocal, J (menu) = roue des tags HeraliZ.
   // Les touches se changent toujours dans Options > Commandes > Reanimation.
   public static final KeyMapping GIVE_UP = new KeyMapping("key.reanimation.give_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.reanimation");
   public static final KeyMapping EXECUTE = new KeyMapping("key.reanimation.execute", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.reanimation");
   public static final KeyMapping MENU = new KeyMapping("key.reanimation.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "key.categories.reanimation");

   private ClientSetup() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      Downed.clientFlags = ClientDowned::flags;
      event.enqueueWork(() -> MenuScreens.register((MenuType)ModMenus.SEARCH.get(), SearchScreen::new));
   }

   @SubscribeEvent
   public static void onKeys(RegisterKeyMappingsEvent event) {
      event.register(GIVE_UP);
      event.register(EXECUTE);
      event.register(MENU);
   }

   @SubscribeEvent
   public static void onOverlays(RegisterGuiOverlaysEvent event) {
      event.registerAboveAll("downed", DownedHud::render);
   }

   @SubscribeEvent
   public static void onRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ModEntities.BOT.get(), BotRenderer::new);
   }

   @SubscribeEvent
   public static void onAddLayers(AddLayers event) {
      for (String skin : event.getSkins()) {
         if (event.getSkin(skin) instanceof PlayerRenderer player) {
            swapModel(player, event.getEntityModels(), "slim".equals(skin));
            player.addLayer(new ExecutionLayer(player));
         }
      }
   }

   private static void swapModel(PlayerRenderer renderer, EntityModelSet models, boolean slim) {
      try {
         for (Field field : LivingEntityRenderer.class.getDeclaredFields()) {
            if (EntityModel.class.isAssignableFrom(field.getType())) {
               field.setAccessible(true);
               field.set(renderer, new AnimatedPlayerModel(models.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim));
               return;
            }
         }

         ReanimationMod.LOGGER.warn("Player model field not found, no search/revive animations");
      } catch (RuntimeException | ReflectiveOperationException var7) {
         ReanimationMod.LOGGER.warn("Could not install the animated player model", var7);
      }
   }
}
