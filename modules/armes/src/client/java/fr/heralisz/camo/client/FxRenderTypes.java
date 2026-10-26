package fr.heralisz.camo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.camo.fx.FxMesh;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendus des effets 3D : un par skin (shader fx_&lt;id&gt;) et par mode — opaque (écrit la profondeur), transparent,
 * additif (lueurs, flammes, éclairs : ne cachent rien derrière eux). Pas de face cachée : les éléments fins se voient des deux côtés.
 */
public final class FxRenderTypes extends RenderType {
   private static final Map<Integer, RenderType> CACHE = new HashMap<>();

   private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
                         Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
   }

   public static RenderType get(int id, int mode) {
      return CACHE.computeIfAbsent(id * 4 + mode, k -> create(
         "heralisz_fx_" + id + "_" + mode,
         DefaultVertexFormat.NEW_ENTITY,
         VertexFormat.Mode.QUADS,
         65536,
         false,
         mode == FxMesh.ALPHA,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> CamoShaders.fx(id)))
            .setTextureState(new EmptyTextureStateShard(() -> {
               ShaderInstance s = CamoShaders.fx(id);
               if (s != null) {
                  s.safeGetUniform("FxTime").set(CamoFx.fxTime);
                  s.safeGetUniform("FxFade").set(CamoFx.fxFade);
                  s.safeGetUniform("FxGui").set(CamoFx.fxGui);
                  s.safeGetUniform("FxLight").set(CamoFx.fxLight);
                  s.safeGetUniform("FxFp").set(CamoFx.fxFp);
                  s.safeGetUniform("FxScale").set(CamoFx.fxScale);
               }
            }, () -> {}))
            .setTransparencyState(mode == FxMesh.SOLID ? NO_TRANSPARENCY : mode == FxMesh.ALPHA ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(mode == FxMesh.SOLID ? COLOR_DEPTH_WRITE : COLOR_WRITE)
            .setLightmapState(NO_LIGHTMAP)
            .setOverlayState(NO_OVERLAY)
            .createCompositeState(false)));
   }

   public static void clear() {
      CACHE.clear();
   }
}
