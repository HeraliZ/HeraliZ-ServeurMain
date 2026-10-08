package fr.heralisz.tactical.capability;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SCustomCoolDownMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public class CustomItemCoolDowns {
   public static final ResourceLocation ID = new ResourceLocation("heralisz_tactical", "custom_cooldown");
   private final Player player;
   public Map<ResourceLocation, CustomItemCoolDowns.CooldownInstance> cooldowns = Maps.newHashMap();
   private int tickCount;

   public CustomItemCoolDowns(Player player) {
      this.player = player;
   }

   public boolean isOnCooldown(ResourceLocation id) {
      return this.getCooldownPercent(id, 0.0F) > 0.0F;
   }

   public float getCooldownPercent(ResourceLocation id, float pPartialTicks) {
      CustomItemCoolDowns.CooldownInstance itemcooldowns$cooldowninstance = this.cooldowns.get(id);
      if (itemcooldowns$cooldowninstance != null) {
         float f = itemcooldowns$cooldowninstance.endTime - itemcooldowns$cooldowninstance.startTime;
         float f1 = itemcooldowns$cooldowninstance.endTime - (this.tickCount + pPartialTicks);
         return Mth.clamp(f1 / f, 0.0F, 1.0F);
      } else {
         return 0.0F;
      }
   }

   public void tick() {
      this.tickCount++;
      if (!this.cooldowns.isEmpty()) {
         Iterator<Entry<ResourceLocation, CustomItemCoolDowns.CooldownInstance>> iterator = this.cooldowns.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<ResourceLocation, CustomItemCoolDowns.CooldownInstance> entry = iterator.next();
            if (entry.getValue().endTime <= this.tickCount) {
               iterator.remove();
               this.onCooldownEnded(entry.getKey());
            }
         }
      }
   }

   public void addCooldown(ResourceLocation id, int pTicks) {
      this.cooldowns.put(id, new CustomItemCoolDowns.CooldownInstance(this.tickCount, this.tickCount + pTicks));
      this.onCooldownStarted(id, pTicks);
   }

   public void removeCooldown(ResourceLocation id) {
      this.cooldowns.remove(id);
      this.onCooldownEnded(id);
   }

   protected void onCooldownStarted(ResourceLocation id, int pTicks) {
      if (!this.player.level().isClientSide()) {
         NetworkHandler.sendToClientPlayer(new SCustomCoolDownMessage(id, pTicks), this.player);
      }
   }

   protected void onCooldownEnded(ResourceLocation id) {
      if (!this.player.level().isClientSide()) {
         NetworkHandler.sendToClientPlayer(new SCustomCoolDownMessage(id, 0), this.player);
      }
   }

   public Player getPlayer() {
      return this.player;
   }

   public static class CooldownInstance {
      final int startTime;
      final int endTime;

      CooldownInstance(int pStartTime, int pEndTime) {
         this.startTime = pStartTime;
         this.endTime = pEndTime;
      }
   }
}
