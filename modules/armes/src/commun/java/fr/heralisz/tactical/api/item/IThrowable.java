package fr.heralisz.tactical.api.item;

import java.util.Objects;
import java.util.Optional;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface IThrowable extends ICustomItem {
   String ID_TAG = "ThrowableId";
   String OVERRIDE_DISPLAY_ID = "DisplayId";
   ResourceLocation EMPTY = new ResourceLocation("heralisz_tactical", "empty");

   static IThrowable of(ItemStack stack) {
      return stack.getItem() instanceof IThrowable item ? item : null;
   }

   @Override
   default ResourceLocation getId(ItemStack stack) {
      CompoundTag nbt = stack.getOrCreateTag();
      if (nbt.contains("ThrowableId", 8)) {
         ResourceLocation gunId = ResourceLocation.tryParse(nbt.getString("ThrowableId"));
         return Objects.requireNonNullElse(gunId, EMPTY);
      } else {
         return EMPTY;
      }
   }

   @Override
   default ResourceLocation getDisplayId(ItemStack stack) {
      CompoundTag nbt = stack.getOrCreateTag();
      if (nbt.contains("DisplayId", 8)) {
         ResourceLocation rl = ResourceLocation.tryParse(nbt.getString("DisplayId"));
         return Objects.requireNonNullElse(rl, EMPTY);
      } else {
         return this.getId(stack);
      }
   }

   @Override
   default void setId(ItemStack stack, ResourceLocation id) {
      stack.getOrCreateTag().putString("ThrowableId", id.toString());
   }

   @Override
   default boolean isSame(ItemStack i, ItemStack j) {
      IThrowable iThrowable1 = of(i);
      IThrowable iThrowable2 = of(j);
      if (iThrowable1 != null && iThrowable2 != null) {
         return iThrowable1.getId(i).equals(iThrowable2.getId(j));
      } else {
         return !i.isEmpty() && !j.isEmpty() ? false : i.isEmpty() && j.isEmpty();
      }
   }

   @Override
   default Optional<ResourceLocation> getCoolDownId(ItemStack stack) {
      return this.getThrowableIndex(stack).map(index -> index.getData().getCooldownCategory());
   }

   @Override
   default int getMaxUsingTick(ItemStack stack) {
      return this.getThrowableIndex(stack).map(index -> index.getData().getPrepareTime()).orElse(0);
   }

   default Optional<ThrowableIndex<?, ?>> getThrowableIndex(ItemStack stack) {
      return HeraliszTacticalAPI.getThrowableIndex(stack);
   }
}
