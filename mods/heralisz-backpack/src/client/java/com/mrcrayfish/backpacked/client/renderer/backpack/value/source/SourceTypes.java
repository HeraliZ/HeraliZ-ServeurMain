package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Collections;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class SourceTypes {
   private static final BiMap<ResourceLocation, BaseSource.Type> SOURCES = HashBiMap.create();

   public static void register(BaseSource.Type type) {
      BaseSource.Type existing = (BaseSource.Type)SOURCES.putIfAbsent(type.id(), type);
      if (existing != null) {
         throw new IllegalStateException("Source already registered: " + type.id());
      }
   }

   public static Map<ResourceLocation, BaseSource.Type> getAll() {
      return Collections.unmodifiableMap(SOURCES);
   }
}
