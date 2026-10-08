package fr.heraliz.reanimation.execution;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

final class ChuteMeteoreScript extends ExecutionScript {
   static final ChuteMeteoreScript INSTANCE = new ChuteMeteoreScript();

   private ChuteMeteoreScript() {
   }

   @Override
   public void tick(Executions.Execution x, ServerLevel level, int t) {
      Vec3 feet = x.at(0.95, 0.0, 0.05);
      if (t == 6) {
         ServerFx.play(level, feet, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.6F);
      }

      if (t == 12) {
         ServerFx.play(level, feet, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6F, 0.6F);
         ServerFx.play(level, feet, SoundEvents.ENDER_DRAGON_FLAP, 1.4F, 0.8F);
         ServerFx.play(level, feet, SoundEvents.PLAYER_BIG_FALL, 1.0F, 0.6F);
         ServerFx.particles(level, ParticleTypes.POOF, feet, 30, 0.5, 0.05, 0.12);
         ServerFx.debris(level, feet, 40, 0.4, 0.3);
      }

      if (t == 38) {
         ServerFx.play(level, x.at(0.0, 0.0, 9.0), SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
      }

      if (t == 40) {
         ServerFx.play(level, x.at(0.0, 0.0, 8.0), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6F, 0.5F);
      }

      if (t == 44) {
         ServerFx.play(level, x.at(0.0, 0.0, 4.0), SoundEvents.BLAZE_SHOOT, 1.4F, 0.6F);
      }
   }

   @Override
   public void strike(Executions.Execution x, ServerLevel level) {
      Vec3 ground = x.at(0.0, 0.0, 0.1);
      ServerFx.crater(level, ground, 3.2, 260);
      ServerFx.particles(level, ParticleTypes.FLAME, ground.add(0.0, 0.3, 0.0), 40, 0.8, 0.2, 0.2);
      ServerFx.particles(level, ParticleTypes.LAVA, ground.add(0.0, 0.2, 0.0), 25, 0.8, 0.1, 0.0);
      ServerFx.blood(level, ground.add(0.0, 0.2, 0.0), 25);
      ServerFx.play(level, ground, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
      ServerFx.play(level, ground, SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
      ServerFx.play(level, ground, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.6F);
      ServerFx.play(level, ground, SoundEvents.PLAYER_BIG_FALL, 1.5F, 0.6F);
      ServerFx.play(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 0.8F);
   }
}
