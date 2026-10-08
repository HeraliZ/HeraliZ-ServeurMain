package fr.heralisz.tactical.resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.network.DataType;
import fr.heralisz.tactical.resource.manager.ConsumableIndexManager;
import fr.heralisz.tactical.resource.manager.MeleeIndexManager;
import fr.heralisz.tactical.resource.manager.ThrowableIndexManager;
import net.minecraft.resources.ResourceLocation;

public enum CommonNetworkCache implements ICommonResourceProvider {
   INSTANCE;

   public Map<ResourceLocation, ConsumableIndex> consumableIndex = Maps.newHashMap();
   public Map<ResourceLocation, ThrowableIndex<?, ?>> throwableIndex = Maps.newHashMap();
   public Map<ResourceLocation, MeleeWeaponIndex<?>> meleeWeaponIndex = Maps.newHashMap();

   @Override
   public ConsumableIndex getConsumableIndex(ResourceLocation id) {
      return this.consumableIndex.get(id);
   }

   @Override
   public Collection<ConsumableIndex> getConsumableIndexes() {
      return this.consumableIndex.values();
   }

   @Override
   public ThrowableIndex<?, ?> getThrowableIndex(ResourceLocation id) {
      return this.throwableIndex.get(id);
   }

   @Override
   public Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
      return this.throwableIndex.values();
   }

   @Override
   public MeleeWeaponIndex<?> getMeleeIndex(ResourceLocation id) {
      return this.meleeWeaponIndex.get(id);
   }

   @Override
   public Collection<MeleeWeaponIndex<?>> getMeleeIndexes() {
      return this.meleeWeaponIndex.values();
   }

   public void parseConsumableIndex(Map<ResourceLocation, String> cache) {
      Builder<ResourceLocation, ConsumableIndex> builder = ImmutableMap.builder();

      for (Entry<ResourceLocation, String> consumableEntry : cache.entrySet()) {
         JsonObject jsonObject = (JsonObject)CommonAssetsManager.GSON.fromJson(consumableEntry.getValue(), JsonObject.class);
         ConsumableIndex index = ConsumableIndexManager.parse(jsonObject, consumableEntry.getKey());
         if (index != null) {
            builder.put(consumableEntry.getKey(), index);
         }
      }

      this.consumableIndex = builder.build();
   }

   public void parseThrowableIndex(Map<ResourceLocation, String> cache) {
      Builder<ResourceLocation, ThrowableIndex<?, ?>> builder = ImmutableMap.builder();

      for (Entry<ResourceLocation, String> throwableEntry : cache.entrySet()) {
         JsonObject jsonObject = (JsonObject)CommonAssetsManager.GSON.fromJson(throwableEntry.getValue(), JsonObject.class);
         ThrowableIndex<?, ?> index = ThrowableIndexManager.parse(jsonObject, throwableEntry.getKey());
         if (index != null) {
            builder.put(throwableEntry.getKey(), index);
         }
      }

      this.throwableIndex = builder.build();
   }

   public void parseMeleeIndex(Map<ResourceLocation, String> cache) {
      Builder<ResourceLocation, MeleeWeaponIndex<?>> builder = ImmutableMap.builder();

      for (Entry<ResourceLocation, String> meleeEntry : cache.entrySet()) {
         JsonObject jsonObject = (JsonObject)CommonAssetsManager.GSON.fromJson(meleeEntry.getValue(), JsonObject.class);
         MeleeWeaponIndex<?> index = MeleeIndexManager.parse(jsonObject, meleeEntry.getKey());
         if (index != null) {
            builder.put(meleeEntry.getKey(), index);
         }
      }

      this.meleeWeaponIndex = builder.build();
   }

   public void fromNetwork(Map<DataType, Map<ResourceLocation, String>> cache) {
      for (Entry<DataType, Map<ResourceLocation, String>> entry : cache.entrySet()) {
         switch ((DataType)entry.getKey()) {
            case CONSUMABLE_INDEX:
               this.parseConsumableIndex(entry.getValue());
               break;
            case THROWABLE_INDEX:
               this.parseThrowableIndex(entry.getValue());
               break;
            case MELEE_INDEX:
               this.parseMeleeIndex(entry.getValue());
         }
      }
   }
}
