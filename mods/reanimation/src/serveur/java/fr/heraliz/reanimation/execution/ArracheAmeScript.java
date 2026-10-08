package fr.heraliz.reanimation.execution;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

final class ArracheAmeScript extends ExecutionScript {
   static final ArracheAmeScript INSTANCE = new ArracheAmeScript();

   private ArracheAmeScript() {
   }

   @Override
   public void tick(Executions.Execution x, ServerLevel level, int t) {
      Vec3 chest = x.at(0.0, 0.0, 0.3);
      if (t == 6) {
         ServerFx.play(level, chest, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.7F);
      }

      if (t == 13) {
         ServerFx.play(level, chest, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9F, 0.6F);
      }

      if (t == 14) {
         ServerFx.play(level, x.at(0.5, 0.0, 1.6), SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
         ServerFx.play(level, x.at(0.5, 0.0, 1.6), SoundEvents.SOUL_ESCAPE, 1.2F, 0.6F);
      }

      if (t == 24) {
         ServerFx.play(level, chest, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 1.3F);
         ServerFx.play(level, chest, SoundEvents.PLAYER_HURT, 1.0F, 0.6F);
         ServerFx.play(level, chest, SoundEvents.SCULK_BLOCK_SPREAD, 1.2F, 0.6F);
         ServerFx.particles(level, ParticleTypes.SCULK_SOUL, chest, 10, 0.2, 0.1, 0.05);
         ServerFx.blood(level, chest, 8);
      }

      if (t == 28) {
         ServerFx.play(level, chest, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.3F, 0.7F);
      }

      if (t == 30 || t == 38 || t == 46) {
         ServerFx.play(level, x.at(0.1, 0.0, 1.0), SoundEvents.SOUL_ESCAPE, 1.3F, t == 30 ? 1.0F : (t == 38 ? 0.8F : 0.6F));
      }

      if (t == 32 || t == 44 || t == 56 || t == 66) {
         float k = (t - 32) / 34.0F;
         ServerFx.play(level, chest, SoundEvents.WARDEN_HEARTBEAT, 1.4F - 0.6F * k, 1.0F - 0.35F * k);
      }

      if (t == 60) {
         ServerFx.play(level, x.at(0.1, 0.0, 1.6), SoundEvents.ELDER_GUARDIAN_CURSE, 0.7F, 1.2F);
      }
   }

   @Override
   public void strike(Executions.Execution x, ServerLevel level) {
      Vec3 soul = x.at(0.1, 0.0, 1.4);
      ServerFx.particles(level, ParticleTypes.SCULK_SOUL, soul, 60, 0.6, 0.6, 0.12);
      ServerFx.particles(level, ParticleTypes.SOUL, soul, 60, 0.7, 0.7, 0.1);
      ServerFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, x.at(0.0, 0.0, 0.1), 60, 1.2, 0.05, 0.08);
      ServerFx.play(level, soul, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.3F);
      ServerFx.play(level, soul, SoundEvents.GLASS_BREAK, 1.5F, 0.6F);
      ServerFx.play(level, soul, SoundEvents.SOUL_ESCAPE, 2.0F, 0.5F);
      ServerFx.play(level, soul, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 1.4F);
   }
}
