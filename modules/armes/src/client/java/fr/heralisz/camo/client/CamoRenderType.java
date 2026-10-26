package fr.heralisz.camo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu « entity cutout » avec le shader camo : unité 0 = texture d'origine de l'arme, unité 3 = carte de positions.
 * TaCZ termine le lot juste après chaque arme (endBatch), donc les uniformes posés ici valent pour cette arme.
 */
public final class CamoRenderType extends RenderType {
   private static final Map<Key, RenderType> CACHE = new HashMap<>();
   private static final Map<RenderType, Info> INFO = new HashMap<>();

   /** Ce qu'un rendu camo dessine (pour les effets 3D : CamoFx). */
   public record Info(int shaderId, int posMap) {}

   /**
    * uv : rectangle de la texture qui correspond à la carte de positions (u0, v0, largeur, hauteur) : tout (0, 0, 1, 1) pour
    * une arme, l'emplacement du sac dans l'atlas des blocs pour un sac. bag : ni apparition, ni chaleur, ni niveau d'arme.
    */
   private record Key(ResourceLocation texture, int posMap, int shaderId, int parts, float u0, float v0, float du, float dv, boolean bag) {}

   private CamoRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
   }

   public static RenderType get(ResourceLocation texture, int posMap, int shaderId, int parts) {
      return get(new Key(texture, posMap, shaderId, parts, 0, 0, 1, 1, false));
   }

   /** Sac (BagCamo) : texture = atlas des blocs, uv = emplacement de la texture du sac dans l'atlas. */
   public static RenderType bag(int posMap, int shaderId, int parts, float u0, float v0, float du, float dv) {
      return get(new Key(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, posMap, shaderId, parts, u0, v0, du, dv, true));
   }

   private static RenderType get(Key key) {
      return CACHE.computeIfAbsent(key, k -> register(k, create(
         "heralisz_camo_" + key.shaderId,
         DefaultVertexFormat.NEW_ENTITY,
         VertexFormat.Mode.QUADS,
         256,
         true,
         false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> CamoShaders.get(k.shaderId)))
            .setTextureState(new EmptyTextureStateShard(() -> {
               RenderSystem.setShaderTexture(0, k.texture);
               RenderSystem.setShaderTexture(3, k.posMap);
               ShaderInstance shader = CamoShaders.get(k.shaderId);
               if (shader != null) {
                  shader.safeGetUniform("CamoId").set(k.shaderId);
                  shader.safeGetUniform("CamoParts").set(k.parts);
                  shader.safeGetUniform("CamoTime").set((Util.getMillis() % 3_600_000L) / 1000.0F);
                  shader.safeGetUniform("CamoReveal").set(k.bag ? -1 : CamoFx.pendingReveal);
                  float time = (Util.getMillis() % 3_600_000L) / 1000.0F;
                  shader.safeGetUniform("CamoHeat").set(k.bag ? bagHeat(k.shaderId, time) : CamoFx.pendingHeat);
                  shader.safeGetUniform("CamoLevel").set(k.bag ? bagLevel(k.shaderId, time) : (float) CamoFx.pendingKills);
                  shader.safeGetUniform("CamoUvRect").set(k.u0, k.v0, k.du, k.dv);
               }
            }, () -> CamoFx.DEFAULT_BLEND.apply()))   // le mélange du shader camo ne doit pas rester « dernier appliqué » (voir CamoFx.emit)
            .setTransparencyState(NO_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(true))));
   }

   /** Sac Surchauffe : pas de tirs, le sac chauffe et refroidit lentement (couleurs de revenu de l'acier). */
   private static float bagHeat(int shaderId, float t) {
      return shaderId == 232 ? 0.32F + 0.12F * (float) Math.sin(t * 0.6F) : 0;
   }

   /**
    * Sac Trophée : pas de kills, il montre ses paliers l'un après l'autre (pierre, fer, or, diamant, netherite, ascendant),
    * le matériau suivant gagnant le sac derrière la ligne lumineuse, 8 s par palier.
    */
   private static float bagLevel(int shaderId, float t) {
      if (shaderId != 233) return 0;
      float[] b = {0, 25, 100, 250, 500, 1000};
      int i = (int) (t / 8) % 6;
      if (i == 5) return 1000;
      float u = (t % 8) / 8;
      float prog = Math.max(0, Math.min(1, (u - 0.15F) / 0.6F));
      prog = prog * prog * (3 - 2 * prog);
      return b[i] + (b[i + 1] - b[i]) * prog;
   }

   private static RenderType register(Key k, RenderType type) {
      INFO.put(type, new Info(k.shaderId, k.posMap));
      return type;
   }

   public static Info info(RenderType type) {
      return INFO.get(type);
   }

   public static void clear() {
      CACHE.clear();
      INFO.clear();
   }
}
