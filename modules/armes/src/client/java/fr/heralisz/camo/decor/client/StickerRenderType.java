package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Stickers : planche de dessins, shader « sticker » (finitions papier, holographique, métallisée, or — la finition arrive dans
 * la couleur du sommet), découpe à l'alpha, décalage de profondeur pour ne pas scintiller sur la surface.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StickerRenderType extends RenderType {
   @Nullable private static ShaderInstance shader;

   private StickerRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
   }

   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) {
      try {
         event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszCamo.MODID, "sticker"), DefaultVertexFormat.NEW_ENTITY),
            s -> shader = s);
      } catch (IOException | RuntimeException e) {
         com.mojang.logging.LogUtils.getLogger().error("Shader des stickers indisponible", e);
      }
   }

   private static final java.util.Map<ResourceLocation, RenderType> TYPES = new java.util.HashMap<>();

   /**
    * Planche des stickers (unité 0) et texture de l'arme dessous (unité 1, à la place de la texture de surbrillance, inutile
    * ici) : le shader jette les pixels du sticker là où l'arme est transparente (UV de l'arme portées par les sommets, UV1).
    */
   public static RenderType get(ResourceLocation gunTexture) {
      return TYPES.computeIfAbsent(gunTexture, tex -> create("heralisz_sticker", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> shader != null ? shader : net.minecraft.client.renderer.GameRenderer.getRendertypeEntityCutoutNoCullShader()))
            .setTextureState(MultiTextureStateShard.builder().add(DecorRender.STICKERS, false, false).add(tex, false, false).build())
            .setTransparencyState(NO_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(NO_OVERLAY)
            .setLayeringState(POLYGON_OFFSET_LAYERING)
            .createCompositeState(true)));
   }
}
