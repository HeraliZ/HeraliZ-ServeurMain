package fr.heralisz.camo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.fx.FxEffects;
import fr.heralisz.camo.knife.KnifeFinish;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
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
   /** Empreinte des sources des programmes compilés (null : rien de compilé). */
   @Nullable
   private static String empreinte;

   @Nullable
   public static ShaderInstance get(int shaderId) {
      return SHADERS.get(shaderId);
   }

   @Nullable
   public static ShaderInstance fx(int shaderId) {
      return FX.get(shaderId);
   }

   /**
    * Si un shader ne compile pas sur une machine, ce camo s'affiche juste sans effet (jamais de crash).
    * <p>
    * 19/11 (audit des images par seconde, point 11) : les ≈ 80 programmes (chacun avec l'include des camos, 261 Ko) étaient
    * recompilés à chaque rechargement des ressources (F3+T, et chaque changement des mipmaps par le profil graphique) :
    * plusieurs secondes de gel. Ils ne le sont plus que si leurs sources ont changé (empreinte des fichiers shaders/ de
    * heralisz_camo et des includes). Ils ne sont donc plus confiés à Minecraft (qui ferme à chaque rechargement tous les
    * programmes qu'on lui a donnés) : gardés ici, fermés ici quand on les refait. Les morceaux de programme partagés que
    * Minecraft libère au rechargement suivant ne gênent pas : un programme déjà lié reste valable.
    */
   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) {
      String emp = empreinte();
      if (emp != null && emp.equals(empreinte) && !SHADERS.isEmpty()) {
         LOG.info("Shaders des camos inchangés : {} programmes gardés, rien recompilé", distincts().size());
         return;
      }
      fermer();
      empreinte = emp;
      SHADERS.clear();
      // programme -> skins qu'il dessine (les skins de couteaux par paliers partagent un seul programme)
      Map<Integer, Set<Integer>> programmes = new TreeMap<>();
      for (Camo c : Camo.values()) programmes.computeIfAbsent(c.shaderId, k -> new TreeSet<>()).add(c.shaderId);
      for (KnifeFinish f : KnifeFinish.values()) programmes.computeIfAbsent(f.programme(), k -> new TreeSet<>()).add(f.shaderId);
      for (Map.Entry<Integer, Set<Integer>> e : programmes.entrySet()) {
         int id = e.getKey();
         Set<Integer> skins = e.getValue();
         try {
            ShaderInstance s = new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszCamo.MODID, "camo_" + id), DefaultVertexFormat.NEW_ENTITY);
            for (int skin : skins) SHADERS.put(skin, s);
         } catch (IOException | RuntimeException ex) {
            LOG.error("Shader camo_{} indisponible, ce skin s'affichera sans effet", id, ex);
         }
      }
      FX.clear();
      for (int id : FxEffects.ids()) {
         try {
            FX.put(id, new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HeraliszCamo.MODID, "fx_" + id), DefaultVertexFormat.NEW_ENTITY));
         } catch (IOException | RuntimeException ex) {
            LOG.error("Shader fx_{} indisponible, l'effet 3D de ce skin ne s'affichera pas", id, ex);
         }
      }
   }

   /** Programmes gardés (un même programme sert plusieurs skins de couteaux). */
   private static Set<ShaderInstance> distincts() {
      Set<ShaderInstance> s = Collections.newSetFromMap(new IdentityHashMap<>());
      s.addAll(SHADERS.values());
      s.addAll(FX.values());
      return s;
   }

   /** Ferme les programmes d'avant (refaits parce que leurs sources ont changé). */
   private static void fermer() {
      for (ShaderInstance s : distincts()) {
         try {
            s.close();
         } catch (RuntimeException ex) {
            LOG.warn("Shader {} : fermeture impossible ({})", s.getName(), ex.toString());
         }
      }
      SHADERS.clear();
      FX.clear();
   }

   /**
    * Empreinte des sources : fichiers shaders/ de heralisz_camo (programmes, vsh, fsh) et tous les includes (le nôtre et
    * ceux de Minecraft qu'il importe), tels que les packs en cours les donnent. Null si on ne peut pas la faire : on
    * recompile alors, comme avant.
    */
   @Nullable
   private static String empreinte() {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         Map<ResourceLocation, Resource> res = Minecraft.getInstance().getResourceManager().listResources("shaders",
            rl -> rl.getNamespace().equals(HeraliszCamo.MODID) || rl.getPath().startsWith("shaders/include/"));
         for (Map.Entry<ResourceLocation, Resource> en : new TreeMap<>(res).entrySet()) {
            md.update(en.getKey().toString().getBytes(StandardCharsets.UTF_8));
            try (InputStream in = en.getValue().open()) {
               md.update(in.readAllBytes());
            }
         }
         return HexFormat.of().formatHex(md.digest());
      } catch (Exception ex) {
         LOG.warn("Shaders des camos : empreinte impossible ({}), recompilés", ex.toString());
         return null;
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
