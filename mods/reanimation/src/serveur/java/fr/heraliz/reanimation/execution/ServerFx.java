package fr.heraliz.reanimation.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ServerFx {
   private ServerFx() {
   }

   static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
      level.playSound((Player)null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }

   static void particles(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double up, double speed) {
      level.sendParticles(particle, at.x, at.y, at.z, count, spread, up, spread, speed);
   }

   static BlockState ground(ServerLevel level, Vec3 at) {
      BlockState state = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
      return state.isAir() ? Blocks.DIRT.defaultBlockState() : state;
   }

   static void debris(ServerLevel level, Vec3 at, int count, double spread, double speed) {
      level.sendParticles(
         new BlockParticleOption(ParticleTypes.BLOCK, ground(level, at)), at.x, at.y + 0.1, at.z, count, spread, 0.15, spread, speed
      );
   }

   static void blood(ServerLevel level, Vec3 at, int count) {
      level.sendParticles(
         new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, count, 0.2, 0.15, 0.2, 0.3
      );
      level.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.4F), at.x, at.y + 0.1, at.z, count, 0.3, 0.2, 0.3, 0.0);
   }

   static void crater(ServerLevel level, Vec3 at, double radius, int debris) {
      level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
      level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 6, radius * 0.3, 0.15, radius * 0.3, 0.0);
      debris(level, at, debris, radius * 0.45, 0.45);
      level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.1, at.z, 10, radius * 0.4, 0.05, radius * 0.4, 0.01);
      level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 40, radius * 0.4, 0.1, radius * 0.4, 0.1);
      Executions.cracks(level, at, radius);
   }

   static void lightning(ServerLevel level, Vec3 at) {
      LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(level);
      if (bolt != null) {
         bolt.moveTo(at.x, at.y, at.z);
         bolt.setVisualOnly(true);
         level.addFreshEntity(bolt);
      }
   }
}
