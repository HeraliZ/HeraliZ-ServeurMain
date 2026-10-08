package fr.heralisz.tactical.util;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class DefaultAttrUUIDUtil {
   private static final Map<ResourceLocation, UUID> cache = Maps.newHashMap();

   public static UUID getUUID(ResourceLocation id) {
      return cache.computeIfAbsent(id, k -> UUID.randomUUID());
   }

   static {
      cache.put(new ResourceLocation("minecraft:generic.attack_damage"), Item.BASE_ATTACK_DAMAGE_UUID);
      cache.put(new ResourceLocation("minecraft:generic.attack_speed"), Item.BASE_ATTACK_SPEED_UUID);
   }
}
