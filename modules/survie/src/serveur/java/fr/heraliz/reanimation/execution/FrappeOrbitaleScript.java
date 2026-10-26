package fr.heraliz.reanimation.execution;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

final class FrappeOrbitaleScript extends ExecutionScript {
   static final FrappeOrbitaleScript INSTANCE = new FrappeOrbitaleScript();
   private static final int STRIKE = 62;

   private FrappeOrbitaleScript() {
   }

   @Override
   public void tick(Executions.Execution x, ServerLevel level, int t) {
      Vec3 body = x.at(0.0, 0.0, 0.3);
      if (t == 13) {
         ServerFx.play(level, x.at(2.4, 0.0, 2.2), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
         ServerFx.play(level, x.at(2.4, 0.0, 2.2), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8F, 1.4F);
      }

      if (t == 24) {
         ServerFx.play(level, body, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.5F);
      }

      boolean beep = t >= 24 && t < 60 && (t < 44 ? (t - 24) % 5 == 0 : t >= 54 || t % 3 == 0);
      if (beep) {
         ServerFx.play(level, body, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F + (t - 24) / 36.0F * 0.8F);
      }

      if (t == 48) {
         ServerFx.play(level, body, SoundEvents.WARDEN_SONIC_CHARGE, 1.6F, 1.0F);
      }

      if (t == 59) {
         ServerFx.play(level, body, SoundEvents.TRIDENT_THUNDER, 1.6F, 0.8F);
      }

      if (t > 62 && t < 100 && t % 4 == 0) {
         ServerFx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x.at(0.0, 0.0, 0.2), 1, 0.3, 0.05, 0.01);
         if (t % 8 == 0) {
            ServerFx.particles(level, ParticleTypes.LAVA, x.at(0.0, 0.0, 0.2), 1, 0.3, 0.05, 0.0);
         }
      }

      if (t == 80) {
         ServerFx.play(level, body, SoundEvents.PLAYER_BIG_FALL, 1.2F, 0.7F);
         ServerFx.debris(level, x.at(0.0, 0.0, 0.1), 40, 0.5, 0.25);
      }
   }

   @Override
   public void strike(Executions.Execution x, ServerLevel level) {
      Vec3 ground = x.at(0.0, 0.0, 0.1);
      ServerFx.lightning(level, ground);
      ServerFx.crater(level, ground, 2.6, 220);
      ServerFx.particles(level, ParticleTypes.FLAME, ground.add(0.0, 0.4, 0.0), 50, 0.6, 0.4, 0.25);
      ServerFx.particles(level, ParticleTypes.LAVA, ground.add(0.0, 0.2, 0.0), 30, 0.5, 0.2, 0.0);
      ServerFx.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ground.add(0.0, 0.2, 0.0), 8, 0.4, 0.05, 0.02);
      ServerFx.particles(level, ParticleTypes.END_ROD, ground.add(0.0, 1.0, 0.0), 60, 0.5, 1.5, 0.3);
      ServerFx.play(level, ground, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.7F);
      ServerFx.play(level, ground, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.6F);
      ServerFx.play(level, ground, SoundEvents.DRAGON_FIREBALL_EXPLODE, 2.0F, 0.8F);
   }
}
