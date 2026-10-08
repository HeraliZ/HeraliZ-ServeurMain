package fr.heralisz.tactical.api.item;

import java.util.Objects;
import java.util.Optional;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface IConsumable extends ICustomItem {
   String ID_TAG = "ConsumableId";
   String OVERRIDE_DISPLAY_ID = "DisplayId";
   ResourceLocation EMPTY = new ResourceLocation("heralisz_tactical", "empty");

   static IConsumable of(ItemStack stack) {
      return stack.getItem() instanceof IConsumable item ? item : null;
   }

   @Override
   default ResourceLocation getId(ItemStack stack) {
      CompoundTag nbt = stack.getOrCreateTag();
      if (nbt.contains("ConsumableId", 8)) {
         ResourceLocation rl = ResourceLocation.tryParse(nbt.getString("ConsumableId"));
         return Objects.requireNonNullElse(rl, EMPTY);
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
      stack.getOrCreateTag().putString("ConsumableId", id.toString());
   }

   @Override
   default boolean isSame(ItemStack i, ItemStack j) {
      IConsumable c1 = of(i);
      IConsumable c2 = of(j);
      if (c1 != null && c2 != null) {
         return c1.getId(i).equals(c2.getId(j));
      } else {
         return !i.isEmpty() && !j.isEmpty() ? false : i.isEmpty() && j.isEmpty();
      }
   }

   @Override
   default Optional<ResourceLocation> getCoolDownId(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().getCooldownCategory());
   }

   @Override
   default int getMaxUsingTick(ItemStack stack) {
      return this.getConsumableIndex(stack).map(index -> index.getData().getUseDuration()).orElse(0);
   }

   default Optional<ConsumableIndex> getConsumableIndex(ItemStack stack) {
      return HeraliszTacticalAPI.getConsumableIndex(stack);
   }

   default boolean spawnParticleOnBreak(ItemStack stack) {
      return false;
   }
}
