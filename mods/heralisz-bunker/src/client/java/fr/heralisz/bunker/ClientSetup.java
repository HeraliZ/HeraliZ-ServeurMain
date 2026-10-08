package fr.heralisz.bunker;

import com.google.common.collect.ImmutableMap;
import com.hbm_m.block.ModBlocks;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.client.loader.DaeModelLoader;
import com.hbm_m.client.loader.DoorModelLoader;
import com.hbm_m.client.loader.dae.DaeModelReloader;
import com.hbm_m.client.model.variant.DoorModelRegistry;
import com.hbm_m.client.render.ModShaders;
import com.hbm_m.client.render.implementations.DoorRenderer;
import com.hbm_m.client.render.implementations.TransitionSealRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         ItemBlockRenderTypes.setRenderLayer(ModBlocks.TRANSITION_SEAL.get(), RenderType.cutout());
         BlockEntityRenderers.register(ModBlockEntities.DOOR_ENTITY.get(), DoorRenderer::new);
         BlockEntityRenderers.register(ModBlockEntities.TRANSITION_SEAL_BE.get(), TransitionSealRenderer::new);
      });
   }

   @SubscribeEvent
   public static void onGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
      event.register("door", new DoorModelLoader());
      event.register("dae", new DaeModelLoader());
   }

   @SubscribeEvent
   public static void onAdditionalModels(ModelEvent.RegisterAdditional event) {
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/round_airlock_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/round_airlock_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/round_airlock_door_modern_clean"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/round_airlock_door_modern_green"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/large_vehicle_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/large_vehicle_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/large_vehicle_door_modern_rad"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/large_vehicle_door_modern_clean"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_modern_black"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_modern_orange"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_modern_trefoil"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/fire_door_modern_yellow"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/secure_access_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/secure_access_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/secure_access_door_modern_gray"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/secure_access_door_modern_yellow"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/secure_access_door_modern_black"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/water_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/water_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/water_door_clean"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_containment_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_containment_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_containment_door_modern_trefoil"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_containment_door_modern_trefoil_yellow"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_sliding_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/qe_sliding_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_blast_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_blast_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_blast_door_modern_variant1"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_blast_door_modern_variant2"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_seal_door_legacy"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/sliding_seal_door_modern"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_2"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_81"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_87"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_99"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_101"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_106"));
      event.register(new ResourceLocation("heralisz_bunker", "block/doors/vault_door_skin_111"));
   }

   @SubscribeEvent
   public static void onReload(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(DoorModelRegistry.getInstance());
      event.registerReloadListener(new DaeModelReloader());
      event.registerReloadListener(fr.heralisz.bunker.barricade.BarricadeMesh.RELOAD);
   }

   @SubscribeEvent
   public static void onShaders(RegisterShadersEvent event) throws IOException {
      VertexFormat blockLitSimpleFormat = new VertexFormat(ImmutableMap.<String, com.mojang.blaze3d.vertex.VertexFormatElement>builder()
         .put("Position", DefaultVertexFormat.ELEMENT_POSITION)
         .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL)
         .put("UV0", DefaultVertexFormat.ELEMENT_UV0)
         .build());
      event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszBunker.MODID, "block_lit_simple"), blockLitSimpleFormat),
         ModShaders::setBlockLitSimpleShader);
   }

   private ClientSetup() {}
}
