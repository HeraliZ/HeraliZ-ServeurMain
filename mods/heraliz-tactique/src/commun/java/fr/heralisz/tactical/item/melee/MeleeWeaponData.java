package fr.heralisz.tactical.item.melee;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.util.TooltipLine;
import fr.heralisz.tactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;

public class MeleeWeaponData {
   @SerializedName("draw_time")
   private int drawTime;
   @SerializedName("put_away_time")
   private int putAwayTime;
   @SerializedName("attack")
   private CombatData attackInfo = new CombatData();
   @SerializedName("attributes")
   private AttributeData attributes = new AttributeData();
   @SerializedName("max_durability")
   private int maxDurability = 0;
   @SerializedName("enchantment_value")
   private int enchantmentValue = 14;

   public int getPutAwayTime() {
      return this.putAwayTime;
   }

   public int getDrawTime() {
      return this.drawTime;
   }

   public CombatData getAttackInfo() {
      return this.attackInfo;
   }

   public AttributeData getRawAttributes() {
      return this.attributes;
   }

   public int getMaxDurability() {
      return this.maxDurability;
   }

   public int getEnchantmentValue() {
      return this.enchantmentValue;
   }

   public List<TooltipLine> getTooltipLines() {
      List<TooltipLine> lines = new ArrayList<>();

      for (MeleeAction action : MeleeAction.values()) {
         CombatData.MeleeAttackInfo attackInfo = this.attackInfoOf(action);
         if (attackInfo != null) {
            String actionKey = action == MeleeAction.LEFT ? "tooltip.heralisz_tactical.melee.attack_left" : "tooltip.heralisz_tactical.melee.attack_right";
            lines.add(
               TooltipLine.normal(
                  Component.translatable(
                     "tooltip.heralisz_tactical.melee.action_line",
                     new Object[]{
                        Component.translatable(actionKey), TooltipUtil.formatFactor(attackInfo.getFactor()), TooltipUtil.formatTicks(attackInfo.getCooldown())
                     }
                  )
               )
            );
         }
      }

      return lines;
   }

   private CombatData.MeleeAttackInfo attackInfoOf(MeleeAction action) {
      return this.attackInfo == null ? null : this.attackInfo.getAttackInfo(action);
   }
}
