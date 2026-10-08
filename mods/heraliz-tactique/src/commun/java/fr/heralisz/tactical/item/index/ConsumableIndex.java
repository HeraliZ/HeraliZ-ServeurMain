package fr.heralisz.tactical.item.index;

import com.google.gson.JsonElement;
import fr.heralisz.tactical.api.index.ICustomItemIndex;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.item.consumable.ConsumableData;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ConsumableIndex implements ICustomItemIndex {
   private final Item baseItem;
   private final ConsumableData data;
   private final ResourceLocation id;
   private final String name;
   private final String tooltip;
   private final FoodProperties foodProperties;

   private ConsumableIndex(ConsumableData data, String name, String tooltip, ResourceLocation id, Item baseItem) {
      this.baseItem = baseItem;
      this.data = data;
      this.id = id;
      this.name = name;
      this.tooltip = tooltip;
      this.foodProperties = buildFoodProperties(data);
   }

   private static FoodProperties buildFoodProperties(ConsumableData data) {
      if (data.getFood() <= 0 && data.getSaturation() <= 0.0F && data.getEffects().isEmpty()) {
         return null;
      }

      Builder builder = new Builder().nutrition(data.getFood()).saturationMod(data.getSaturation());

      for (ConsumableData.EffectData effectData : data.getEffects()) {
         builder.effect(effectData::createInstance, effectData.getChance());
      }

      return builder.build();
   }

   @Nullable
   public static ConsumableIndex deserialize(JsonElement data, String name, String tooltip, ResourceLocation id, Item baseItem) {
      ConsumableData consumableData = (ConsumableData)CommonAssetsManager.GSON.fromJson(data, ConsumableData.class);
      return consumableData == null ? null : new ConsumableIndex(consumableData, name, tooltip, id, baseItem);
   }

   public ConsumableData getData() {
      return this.data;
   }

   @Nullable
   public String getTooltip() {
      return this.tooltip;
   }

   @Override
   public ItemStack createItemStack() {
      ItemStack stack = new ItemStack(this.baseItem);
      if (stack.getItem() instanceof IConsumable consumable) {
         consumable.setId(stack, this.getId());
      }

      return stack;
   }

   @Override
   public int getMaxStackSize() {
      return this.data.hasDurability() ? 1 : this.data.getStackSize();
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
   public String getDescriptionId() {
      return this.name;
   }

   public FoodProperties getFoodProperties() {
      return this.foodProperties;
   }
}
