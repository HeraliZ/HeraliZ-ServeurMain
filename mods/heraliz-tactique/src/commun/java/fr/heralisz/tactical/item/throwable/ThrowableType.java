package fr.heralisz.tactical.item.throwable;

import com.google.gson.JsonElement;
import fr.heralisz.tactical.entity.ThrowableItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public record ThrowableType<T extends ThrowableData, E extends ThrowableItemEntity>(
   ThrowableType.ThrowableFactory<T, E> factory, ThrowableType.ThrowableDataSerializer<T> serializer
) {
   public static class Builder<T extends ThrowableData, E extends ThrowableItemEntity> {
      private ThrowableType.ThrowableFactory<T, E> factory;
      private ThrowableType.ThrowableDataSerializer<T> serializer;

      public static <T extends ThrowableData, E extends ThrowableItemEntity> ThrowableType.Builder<T, E> of() {
         return new ThrowableType.Builder<>();
      }

      public ThrowableType.Builder<T, E> setFactory(ThrowableType.ThrowableFactory<T, E> factory) {
         this.factory = factory;
         return this;
      }

      public ThrowableType.Builder<T, E> setSerializer(ThrowableType.ThrowableDataSerializer<T> serializer) {
         this.serializer = serializer;
         return this;
      }

      public ThrowableType<T, E> build() {
         return new ThrowableType<>(this.factory, this.serializer);
      }
   }

   @FunctionalInterface
   public interface ThrowableDataSerializer<T extends ThrowableData> {
      T parse(JsonElement var1);
   }

   @FunctionalInterface
   public interface ThrowableFactory<T extends ThrowableData, E extends ThrowableItemEntity> {
      E create(ItemStack var1, LivingEntity var2, T var3);
   }
}
