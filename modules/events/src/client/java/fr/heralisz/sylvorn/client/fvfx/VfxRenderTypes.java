package fr.heralisz.sylvorn.client.fvfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Types de rendu des effets : lueur additive (énergie) et décalque translucide posé au sol (alertes, brûlures). */
public final class VfxRenderTypes extends RenderType {
   private static final Map<ResourceLocation, RenderType> GLOW = new HashMap<>();
   private static final Map<ResourceLocation, RenderType> DECAL = new HashMap<>();

   private VfxRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable c, Runnable d) {
      super(n, f, m, s, a, b, c, d);
   }

   /** Additif, sans écriture de profondeur, double face : l'énergie « brille » par-dessus la scène. */
   public static RenderType glow(ResourceLocation tex) {
      return GLOW.computeIfAbsent(tex, t -> create("heralisz_vfx_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1024, false, true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(t, false, false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY)
            .setLightmapState(LIGHTMAP)
            .createCompositeState(false)));
   }

   private static final Map<ResourceLocation, RenderType> GLASS = new HashMap<>();

   /** Verre : translucide, double face, sans écriture de profondeur (ne masque ni le liquide ni le corps). */
   public static RenderType glass(ResourceLocation tex) {
      return GLASS.computeIfAbsent(tex, t -> create("heralisz_vfx_glass", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1024, false, true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(t, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY)
            .setLightmapState(LIGHTMAP)
            .createCompositeState(false)));
   }

   /** Translucide classique, décalé en profondeur pour ne pas scintiller contre le sol. */
   public static RenderType decal(ResourceLocation tex) {
      return DECAL.computeIfAbsent(tex, t -> create("heralisz_vfx_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1024, false, true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(t, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setLayeringState(POLYGON_OFFSET_LAYERING)
            .setOverlayState(OVERLAY)
            .setLightmapState(LIGHTMAP)
            .createCompositeState(false)));
   }
}
