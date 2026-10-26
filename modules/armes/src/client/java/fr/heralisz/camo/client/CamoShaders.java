package fr.heralisz.camo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.fx.FxEffects;
import fr.heralisz.camo.knife.KnifeFinish;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamoShaders {
   private static final Logger LOG = LogUtils.getLogger();
   /** Un petit programme par camo (généré par tools/gen_camo_shaders.py), indexé par shaderId. */
   private static final Map<Integer, ShaderInstance> SHADERS = new HashMap<>();
   /** Shaders des effets 3D (fx_<id>), même numéro que le camo. */
   private static final Map<Integer, ShaderInstance> FX = new HashMap<>();

   @Nullable
   public static ShaderInstance get(int shaderId) {
      return SHADERS.get(shaderId);
   }

   @Nullable
   public static ShaderInstance fx(int shaderId) {
      return FX.get(shaderId);
   }

   /** Si un shader ne compile pas sur une machine, ce camo s'affiche juste sans effet (jamais de crash). */
   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) {
      SHADERS.clear();
      // programme -> skins qu'il dessine (les skins de couteaux par paliers partagent un seul programme)
      Map<Integer, Set<Integer>> programmes = new TreeMap<>();
      for (Camo c : Camo.values()) programmes.computeIfAbsent(c.shaderId, k -> new TreeSet<>()).add(c.shaderId);
      for (KnifeFinish f : KnifeFinish.values()) programmes.computeIfAbsent(f.programme(), k -> new TreeSet<>()).add(f.shaderId);
      for (Map.Entry<Integer, Set<Integer>> e : programmes.entrySet()) {
         int id = e.getKey();
         Set<Integer> skins = e.getValue();
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszCamo.MODID, "camo_" + id), DefaultVertexFormat.NEW_ENTITY),
               s -> skins.forEach(skin -> SHADERS.put(skin, s)));
         } catch (IOException | RuntimeException ex) {
            LOG.error("Shader camo_{} indisponible, ce skin s'affichera sans effet", id, ex);
         }
      }
      FX.clear();
      for (int id : FxEffects.ids()) {
         try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszCamo.MODID, "fx_" + id), DefaultVertexFormat.NEW_ENTITY),
               s -> FX.put(id, s));
         } catch (IOException | RuntimeException e) {
            LOG.error("Shader fx_{} indisponible, l'effet 3D de ce skin ne s'affichera pas", id, e);
         }
      }
   }

   @SubscribeEvent
   public static void onRegisterDecorations(net.minecraftforge.client.event.RegisterItemDecorationsEvent event) {
      BagCamo.registerBadges(event);
   }

   @SubscribeEvent
   public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener((ResourceManagerReloadListener) rm -> {
         PosMapCache.clear();
         FxRenderTypes.clear();
      });
   }

   private CamoShaders() {}
}
