package fr.heralisz.tactical.item.throwable;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.jetbrains.annotations.Nullable;

public class EntityData {
   @SerializedName("life_time")
   private int lifeTime = 100;
   @SerializedName("gravity")
   private float gravity = 0.07F;
   @SerializedName("should_bounce")
   private boolean shouldBounce = true;
   @SerializedName("broke_on_ground")
   private boolean brokeOnGround = false;
   @SerializedName("bounce_factor")
   private double bounceFactor = 0.75;
   @SerializedName("hit_damage")
   private float hitDamage = 1.0F;
   @SerializedName("tail_particles")
   private ParticleOptions tailParticles = ParticleTypes.SMOKE;

   public int getLifeTime() {
      return this.lifeTime;
   }

   public float getGravity() {
      return this.gravity;
   }

   public boolean isShouldBounce() {
      return this.shouldBounce;
   }

   public boolean isBrokeOnGround() {
      return this.brokeOnGround;
   }

   public double getBounceFactor() {
      return this.bounceFactor;
   }

   public float getHitDamage() {
      return this.hitDamage;
   }

   @Nullable
   public ParticleOptions getTailParticles() {
      return this.tailParticles;
   }
}
