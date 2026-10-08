package fr.heraliz.reanimation.core;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DownedState {
   public final LivingEntity entity;
   public int bleedTicks;
   public final int bleedMax;
   @Nullable
   public UUID attacker;
   public int ticks;
   public boolean dying;
   @Nullable
   public LivingEntity searcher;
   public int searchTicks;
   @Nullable
   public LivingEntity reviver;
   @Nullable
   public LivingEntity executor;
   public int reviveTicks;
   public int reviveMax;
   public Vec3 reviverStart = Vec3.ZERO;

   public DownedState(LivingEntity entity, int bleedTicks, int bleedMax) {
      this.entity = entity;
      this.bleedTicks = bleedTicks;
      this.bleedMax = bleedMax;
   }

   @Nullable
   public LivingEntity partner() {
      return this.executor != null ? this.executor : (this.reviver != null ? this.reviver : this.searcher);
   }
}
