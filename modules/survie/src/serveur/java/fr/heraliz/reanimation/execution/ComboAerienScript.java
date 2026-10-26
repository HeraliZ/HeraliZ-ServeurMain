package fr.heraliz.reanimation.execution;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

final class ComboAerienScript extends ExecutionScript {
   static final ComboAerienScript INSTANCE = new ComboAerienScript();

   private ComboAerienScript() {
   }

   @Override
   public void tick(Executions.Execution x, ServerLevel level, int t) {
      if (t == 8) {
         ServerFx.play(level, x.at(0.3, 0.0, 0.6), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.7F);
         ServerFx.play(level, x.at(0.3, 0.0, 0.6), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.7F);
      }

      if (t == 18) {
         ServerFx.play(level, x.at(0.9, 0.0, 1.0), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
      }

      if (t == 28) {
         Vec3 chin = x.at(0.2, 0.0, 1.3);
         ServerFx.play(level, chin, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F);
         ServerFx.play(level, chin, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
         ServerFx.play(level, chin, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2F, 0.6F);
         ServerFx.particles(level, ParticleTypes.CRIT, chin, 25, 0.3, 0.3, 0.6);
         ServerFx.particles(level, ParticleTypes.EXPLOSION, chin, 1, 0.0, 0.0, 0.0);
         ServerFx.particles(level, ParticleTypes.SWEEP_ATTACK, chin, 1, 0.0, 0.0, 0.0);
         ServerFx.blood(level, chin, 10);
      }

      if (t == 34) {
         Vec3 feet = x.at(0.9, 0.0, 0.05);
         ServerFx.play(level, feet, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4F, 0.7F);
         ServerFx.particles(level, ParticleTypes.POOF, feet, 20, 0.4, 0.05, 0.1);
      }

      if (t == 48) {
         Vec3 hit = x.at(-0.1, 0.0, 8.3);
         ServerFx.play(level, hit, SoundEvents.PLAYER_ATTACK_CRIT, 1.6F, 0.6F);
         ServerFx.play(level, hit, SoundEvents.ANVIL_LAND, 1.0F, 0.8F);
         ServerFx.play(level, hit, SoundEvents.IRON_GOLEM_ATTACK, 1.2F, 0.5F);
         ServerFx.particles(level, ParticleTypes.EXPLOSION, hit, 2, 0.3, 0.3, 0.0);
         ServerFx.particles(level, ParticleTypes.CRIT, hit, 30, 0.4, 0.4, 0.5);
      }

      if (t == 64) {
         Vec3 feet = x.at(0.9, 0.0, 0.05);
         ServerFx.play(level, feet, SoundEvents.PLAYER_BIG_FALL, 1.2F, 0.9F);
         ServerFx.particles(level, ParticleTypes.POOF, feet, 15, 0.4, 0.05, 0.08);
      }
   }

   @Override
   public void strike(Executions.Execution x, ServerLevel level) {
      Vec3 ground = x.at(-0.3, 0.0, 0.1);
      ServerFx.crater(level, ground, 2.6, 220);
      ServerFx.particles(level, ParticleTypes.LAVA, ground.add(0.0, 0.2, 0.0), 12, 0.6, 0.1, 0.0);
      ServerFx.blood(level, ground.add(0.0, 0.2, 0.0), 25);
      ServerFx.play(level, ground, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.7F);
      ServerFx.play(level, ground, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.7F);
      ServerFx.play(level, ground, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
      ServerFx.play(level, ground, SoundEvents.PLAYER_BIG_FALL, 1.5F, 0.7F);
   }
}
