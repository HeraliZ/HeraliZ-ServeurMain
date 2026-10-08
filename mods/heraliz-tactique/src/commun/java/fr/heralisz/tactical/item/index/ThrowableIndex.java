package fr.heralisz.tactical.item.index;

import com.google.gson.JsonElement;
import fr.heralisz.tactical.api.index.ICustomItemIndex;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.entity.ThrowableItemEntity;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.item.throwable.ThrowableType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThrowableIndex<T extends ThrowableData, E extends ThrowableItemEntity> implements ICustomItemIndex {
   private final ThrowableType<T, E> type;
   private final Item baseItem;
   private final T data;
   private final ResourceLocation id;
   private final String name;
   private final String tooltip;

   private ThrowableIndex(@NotNull ThrowableType<T, E> type, T data, String name, String tooltip, ResourceLocation id, Item baseItem) {
      this.type = type;
      this.data = data;
      this.id = id;
      this.baseItem = baseItem;
      this.name = name;
      this.tooltip = tooltip;
   }

   @Nullable
   public static <T extends ThrowableData, E extends ThrowableItemEntity> ThrowableIndex<T, E> deserialize(
      @NotNull ThrowableType<T, E> type, JsonElement data, String name, String tooltip, ResourceLocation id, Item baseItem
   ) {
      T throwableData = type.serializer().parse(data);
      return throwableData == null ? null : new ThrowableIndex<>(type, throwableData, name, tooltip, id, baseItem);
   }

   public T getData() {
      return this.data;
   }

   @Nullable
   public String getTooltip() {
      return this.tooltip;
   }

   @Override
   public int getMaxStackSize() {
      return this.data.getStackSize();
   }

   public ThrowableType<T, E> getType() {
      return this.type;
   }

   @Override
   public String getDescriptionId() {
      return this.name;
   }

   public E createEntity(ItemStack stack, LivingEntity thrower) {
      return this.type.factory().create(stack, thrower, this.data);
   }

   @Override
   public ResourceLocation getId() {
      return this.id;
   }

   @Override
   public Item getBaseItem() {
      return this.baseItem;
   }

   @Override
   public ItemStack createItemStack() {
      ItemStack stack = new ItemStack(this.baseItem);
      if (stack.getItem() instanceof IThrowable iThrowable) {
         iThrowable.setId(stack, this.getId());
      }

      return stack;
   }
}
