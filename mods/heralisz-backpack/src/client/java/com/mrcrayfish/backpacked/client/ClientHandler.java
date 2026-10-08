package com.mrcrayfish.backpacked.client;

import com.mrcrayfish.backpacked.Backpacked;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen;
import com.mrcrayfish.backpacked.client.renderer.entity.layers.BackpackLayer;
import com.mrcrayfish.backpacked.client.renderer.entity.layers.VillagerBackpackLayer;
import com.mrcrayfish.backpacked.common.backpack.loader.ModelMetaLoader;
import com.mrcrayfish.backpacked.core.ModContainers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;

public class ClientHandler {
   public static void init() {
      MenuScreens.register((MenuType)ModContainers.BACKPACK.get(), BackpackScreen::new);
      if (!FMLLoader.isProduction()) {
         MinecraftForge.EVENT_BUS.register(new ForgeDebugClientEvents());
      }
   }

   public static void onRegisterClientLoaders(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(new ModelMetaLoader());
   }

   public static void onRegisterRenderers(RegisterRenderers event) {
   }

   public static void onAddLayers(AddLayers event) {
      addBackpackLayer(event.getSkin("default"), event.getContext().getItemRenderer());
      addBackpackLayer(event.getSkin("slim"), event.getContext().getItemRenderer());
      addBackpackLayer(event.getSkin("slim"), event.getContext().getItemRenderer());
      EntityRenderer<?> renderer = event.getRenderer(EntityType.WANDERING_TRADER);
      if (renderer instanceof WanderingTraderRenderer traderRenderer) {
         traderRenderer.addLayer(new VillagerBackpackLayer(traderRenderer, event.getContext().getItemRenderer()));
      }
   }

   private static void addBackpackLayer(EntityRenderer<?> renderer, ItemRenderer itemRenderer) {
      if (renderer instanceof PlayerRenderer playerRenderer) {
         playerRenderer.addLayer(new BackpackLayer(playerRenderer, itemRenderer));
      }
   }
}
