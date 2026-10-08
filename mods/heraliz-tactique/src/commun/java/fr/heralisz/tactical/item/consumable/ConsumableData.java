package fr.heralisz.tactical.item.consumable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import fr.heralisz.tactical.util.PotionTooltipUtil;
import fr.heralisz.tactical.util.TooltipLine;
import fr.heralisz.tactical.util.TooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;

public class ConsumableData {
   @SerializedName("use_duration")
   private int useDuration = 32;
   @SerializedName("cooldown")
   private int cooldown = 0;
   @SerializedName("cooldown_category")
   private ResourceLocation cooldownCategory = null;
   @SerializedName("stack_size")
   private int stackSize = 1;
   @SerializedName("max_durability")
   private int maxDurability = 0;
   @SerializedName("durability_damage")
   private int durabilityDamage = 1;
   @SerializedName("draw_time")
   private int drawTime = 0;
   @SerializedName("put_away_time")
   private int putAwayTime = 0;
   @SerializedName("heal")
   private float heal = 0.0F;
   @SerializedName("food")
   private int food = 0;
   @SerializedName("saturation")
   private float saturation = 0.0F;
   @SerializedName("effects")
   private List<ConsumableData.EffectData> effects = Collections.emptyList();
   @SerializedName("remove_effects")
   private List<ConsumableData.RemoveEffectSelector> removeEffects = Collections.emptyList();
   @SerializedName("use_mode")
   private ConsumableData.UseMode useMode = ConsumableData.UseMode.HOLD;

   public int getUseDuration() {
      return this.useDuration;
   }

   public ConsumableData.UseMode getUseMode() {
      return this.useMode;
   }

   public boolean isToggleUse() {
      return this.useMode == ConsumableData.UseMode.TOGGLE;
   }

   public int getCooldown() {
      return this.cooldown;
   }

   public ResourceLocation getCooldownCategory() {
      return this.cooldownCategory;
   }

   public int getStackSize() {
      return this.stackSize;
   }

   public int getMaxDurability() {
      return this.maxDurability;
   }

   public int getDurabilityDamage() {
      return this.durabilityDamage;
   }

   public boolean hasDurability() {
      return this.maxDurability > 0;
   }

   public int getDrawTime() {
      return this.drawTime;
   }

   public int getPutAwayTime() {
      return this.putAwayTime;
   }

   public float getHeal() {
      return this.heal;
   }

   public int getFood() {
      return this.food;
   }

   public float getSaturation() {
      return this.saturation;
   }

   public List<ConsumableData.EffectData> getEffects() {
      return this.effects;
   }

   public List<ConsumableData.RemoveEffectSelector> getRemoveEffects() {
      return this.removeEffects;
   }

   public List<TooltipLine> getTooltipLines() {
      List<TooltipLine> lines = new ArrayList<>();
      if (this.getHeal() > 0.0F) {
         lines.add(TooltipLine.normal(Component.translatable("tooltip.heralisz_tactical.consumable.heal", new Object[]{TooltipUtil.format(this.getHeal())})));
      }

      if (this.getFood() > 0 || this.getSaturation() > 0.0F) {
         lines.add(
            TooltipLine.normal(
               Component.translatable("tooltip.heralisz_tactical.consumable.food", new Object[]{this.getFood(), TooltipUtil.format(this.getSaturation())})
            )
         );
      }

      List<PotionTooltipUtil.EffectWithChance> effects = new ArrayList<>();

      for (ConsumableData.EffectData effectData : this.getEffects()) {
         MobEffectInstance effect = effectData.createInstance();
         if (effect != null) {
            effects.add(new PotionTooltipUtil.EffectWithChance(effect, effectData.getChance()));
         }
      }

      List<Component> effectLines = new ArrayList<>();
      PotionTooltipUtil.addPotionTooltip(effects, effectLines, 1.0F);

      for (Component line : effectLines) {
         lines.add(TooltipLine.collapsible(line));
      }

      for (ConsumableData.RemoveEffectSelector selector : this.getRemoveEffects()) {
         if (selector.isCategory()) {
            String categoryKey = switch (selector.getCategory()) {
               case BENEFICIAL -> "tooltip.heralisz_tactical.consumable.effect_category.beneficial";
               case HARMFUL -> "tooltip.heralisz_tactical.consumable.effect_category.harmful";
               case NEUTRAL -> "tooltip.heralisz_tactical.consumable.effect_category.neutral";
               default -> throw new IncompatibleClassChangeError();
            };
            lines.add(
               TooltipLine.collapsible(
                  Component.translatable("tooltip.heralisz_tactical.consumable.remove_effects_by_category", new Object[]{Component.translatable(categoryKey)})
                     .withStyle(ChatFormatting.GRAY)
               )
            );
         } else {
            MobEffect effect = (MobEffect)ForgeRegistries.MOB_EFFECTS.getValue(selector.getEffect());
            if (effect != null) {
               lines.add(
                  TooltipLine.collapsible(
                     Component.translatable("tooltip.heralisz_tactical.consumable.remove_effect", new Object[]{effect.getDisplayName()})
                        .withStyle(ChatFormatting.GRAY)
                  )
               );
            }
         }
      }

      if (this.getMaxDurability() > 1 && this.getDurabilityDamage() > 0) {
         lines.add(
            TooltipLine.normal(Component.translatable("tooltip.heralisz_tactical.consumable.uses", new Object[]{this.getMaxDurability() / this.getDurabilityDamage()}))
         );
      }

      if (this.getUseDuration() > 0) {
         lines.add(
            TooltipLine.normal(
               Component.translatable("tooltip.heralisz_tactical.consumable.use_duration", new Object[]{TooltipUtil.formatTicks(this.getUseDuration())})
            )
         );
      }

      if (this.getCooldown() > 0) {
         lines.add(
            TooltipLine.normal(Component.translatable("tooltip.heralisz_tactical.consumable.cooldown", new Object[]{TooltipUtil.formatTicks(this.getCooldown())}))
         );
      }

      return lines;
   }

   public static class EffectData {
      @SerializedName("id")
      private ResourceLocation id;
      @SerializedName("duration")
      private int duration = 0;
      @SerializedName("amplifier")
      private int amplifier = 0;
      @SerializedName("chance")
      private float chance = 1.0F;
      @SerializedName("ambient")
      private boolean ambient = false;
      @SerializedName("visible")
      private boolean visible = true;
      @SerializedName("show_icon")
      private boolean showIcon = true;

      public ResourceLocation getId() {
         return this.id;
      }

      public float getChance() {
         return this.chance;
      }

      public MobEffectInstance createInstance() {
         MobEffect effect = (MobEffect)ForgeRegistries.MOB_EFFECTS.getValue(this.id);
         return effect == null ? null : new MobEffectInstance(effect, this.duration, this.amplifier, this.ambient, this.visible, this.showIcon);
      }
   }

   public static class RemoveEffectSelector {
      private final ResourceLocation effect;
      private final MobEffectCategory category;

      private RemoveEffectSelector(ResourceLocation effect, MobEffectCategory category) {
         this.effect = effect;
         this.category = category;
      }

      public static ConsumableData.RemoveEffectSelector effect(ResourceLocation effect) {
         return new ConsumableData.RemoveEffectSelector(effect, null);
      }

      public static ConsumableData.RemoveEffectSelector category(MobEffectCategory category) {
         return new ConsumableData.RemoveEffectSelector(null, category);
      }

      public boolean isCategory() {
         return this.category != null;
      }

      public ResourceLocation getEffect() {
         return this.effect;
      }

      public MobEffectCategory getCategory() {
         return this.category;
      }

      public enum CategoryAlias {
         BENEFICIAL("@beneficial", MobEffectCategory.BENEFICIAL),
         HARMFUL("@harmful", MobEffectCategory.HARMFUL),
         NEUTRAL("@neutral", MobEffectCategory.NEUTRAL);

         private static final Map<String, ConsumableData.RemoveEffectSelector.CategoryAlias> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(ConsumableData.RemoveEffectSelector.CategoryAlias::getId, Function.identity()));
         private final String id;
         private final MobEffectCategory category;

         CategoryAlias(String id, MobEffectCategory category) {
            this.id = id;
            this.category = category;
         }

         public String getId() {
            return this.id;
         }

         public MobEffectCategory getCategory() {
            return this.category;
         }

         public static ConsumableData.RemoveEffectSelector.CategoryAlias byId(String id) {
            return BY_ID.get(id);
         }
      }

      public static class Deserializer implements JsonDeserializer<ConsumableData.RemoveEffectSelector> {
         public ConsumableData.RemoveEffectSelector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
               String value = json.getAsString();
               ConsumableData.RemoveEffectSelector.CategoryAlias alias = ConsumableData.RemoveEffectSelector.CategoryAlias.byId(value);
               if (alias != null) {
                  return ConsumableData.RemoveEffectSelector.category(alias.getCategory());
               } else {
                  ResourceLocation effectId = ResourceLocation.tryParse(value);
                  if (effectId == null) {
                     throw new JsonParseException("Invalid effect id or category selector \"" + value + "\"");
                  } else {
                     return ConsumableData.RemoveEffectSelector.effect(effectId);
                  }
               }
            } else {
               throw new JsonParseException("Expected remove effect selector to be a string");
            }
         }
      }
   }

   public enum UseMode {
      @SerializedName("hold")
      HOLD,
      @SerializedName("toggle")
      TOGGLE;
   }
}
