package fr.heralisz.camo.client;

import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import fr.heralisz.camo.knife.KnifeType;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Une carte de positions par modèle d'arme, calculée au premier affichage avec camo, vidée au rechargement des ressources. */
public final class PosMapCache {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Map<BedrockModel, Map<String, PosMap>> CACHE = new WeakHashMap<>();
   private static final Map<BedrockModel, Boolean> FAILED = new WeakHashMap<>();

   /** @param knife type de couteau (rôles lame / manche / métal), ou null pour une arme à feu */
   @Nullable
   public static PosMap get(BedrockModel model, @Nullable KnifeType knife) {
      return get(model, knife, null);
   }

   /**
    * @param texture texture de l'arme (lue une fois au calcul de la carte : faces lumineuses vives gardées, faces ternes des
    *                pièces lumineuses camouflées), ou null
    */
   @Nullable
   public static PosMap get(BedrockModel model, @Nullable KnifeType knife, @Nullable net.minecraft.resources.ResourceLocation texture) {
      Map<String, PosMap> perKind = CACHE.computeIfAbsent(model, m -> new HashMap<>());
      String kind = knife == null ? "gun" : knife.id;
      PosMap map = perKind.get(kind);
      if (map == null && !FAILED.containsKey(model)) {
         try {
            long t = System.nanoTime();
            map = PosMap.bake(model, knife, knife == null && texture != null ? TexturePixels.lire(texture) : null);
            perKind.put(kind, map);
            LOG.debug("Carte de positions camo {}² calculée en {} ms", map.size, (System.nanoTime() - t) / 1_000_000);
         } catch (RuntimeException e) {
            FAILED.put(model, Boolean.TRUE);
            LOG.warn("Camo : impossible de préparer ce modèle d'arme", e);
         }
      }
      return map;
   }

   public static void clear() {
      CACHE.values().forEach(m -> m.values().forEach(PosMap::release));
      CACHE.clear();
      FAILED.clear();
      CamoRenderType.clear();
      CamoFx.clearCache();
      BagCamo.clear();
      IconesArmes.vider();
   }

   private PosMapCache() {}
}
