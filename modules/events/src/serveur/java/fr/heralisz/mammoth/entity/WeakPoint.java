package fr.heralisz.mammoth.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Point faible (tête, yeux, cœur...) pour un combat aux armes à feu : un tir dont la ligne passe dans la sphère du point faible
 * est un coup critique. La ligne de tir est celle du projectile s'il y en a un, sinon le regard du tireur (armes à visée directe).
 */
public final class WeakPoint {
   public static final float CRIT = 1.75F;

   private WeakPoint() {}

   public static boolean hit(DamageSource src, Entity boss, Vec3 point, double radius) {
      if (point == null || !(src.getEntity() instanceof LivingEntity shooter)) return false;
      Entity direct = src.getDirectEntity();
      boolean ranged = direct != null && direct != shooter || shooter.distanceTo(boss) > 6.5F;
      if (!ranged) return false;
      Vec3 o, dir;
      if (direct != null && direct != shooter && direct.getDeltaMovement().lengthSqr() > 1e-4) {
         dir = direct.getDeltaMovement().normalize();
         o = direct.position().subtract(dir.scale(4));
      } else {
         o = shooter.getEyePosition();
         dir = shooter.getLookAngle();
      }
      Vec3 w = point.subtract(o);
      double t = w.dot(dir);
      return t > 0 && w.subtract(dir.scale(t)).length() < radius;
   }

   /** Retour pour le tireur seul : son de coup critique et étincelles sur le point touché. */
   public static void feedback(Entity boss, DamageSource src, Vec3 point) {
      if (!(boss.level() instanceof ServerLevel sl)) return;
      sl.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 14, 0.3, 0.3, 0.3, 0.4);
      if (src.getEntity() instanceof ServerPlayer sp) sp.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 0.9F);
   }
}
