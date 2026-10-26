package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.particle.ModParticles;
import fr.heralisz.n07.sound.BossSounds;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Sons et effets côté serveur du rituel de chaque sanctuaire (s = ticks depuis l'arrivée du joueur). */
final class RitualFx {
   private RitualFx() {}

   private static void play(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
      l.playSound(null, p.x, p.y, p.z, s, SoundSource.BLOCKS, vol, pitch);
   }

   private static Vec3 bossFeet(ShrineBlockEntity be) {
      return Vec3.atBottomCenterOf(be.bossPos());
   }

   /** Le sanctuaire au repos : un peu de vie autour. */
   static void idle(ShrineBlockEntity be, ServerLevel l, long now) {
      Vec3 c = be.center();
      switch (be.kind) {
         case N07 -> {
            Vec3 b = bossFeet(be);
            if (now % 40 == 0) l.sendParticles(ModParticles.TOXIC_SMOKE.get(), b.x, b.y + 0.2, b.z, 2, 0.6, 0.1, 0.6, 0.004);
         }
      }
   }

   /** Mauvais moment (boss éveillé, délai) : le sanctuaire refuse. */
   static void deny(ShrineBlockEntity be, ServerLevel l) {
      Vec3 c = be.center().add(0, 1, 0);
      if (be.kind == DungeonKind.N07) play(l, c, BossSounds.DG_KEY_DENIED.get(), 1.2F, 1F);
      else {
         play(l, c, BossSounds.DG_DENY.get(), 1.2F, 1F);
         l.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.25, 0.2, 0.25, 0.01);
      }
   }

   /** Un bloc rituel s'allume (une fois sur quelques-uns : un son, pour ne pas saturer). */
   static void lampLit(ShrineBlockEntity be, ServerLevel l, BlockPos p, int index) {
      Vec3 c = Vec3.atCenterOf(p);
      switch (be.kind) {
         case N07 -> {
            if (index % 3 == 0) play(l, c, BossSounds.DG_KEY_BEEP.get(), 0.5F, 0.6F);
         }
      }
   }

   /** L'alerte se déclenche (le boss se réveille) : les lumières sautent, la sirène hurle. */
   static void alert(ShrineBlockEntity be, ServerLevel l) {
      Vec3 c = be.center();
      play(l, c.add(0, 3, 0), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 3F, 0.5F);
      switch (be.kind) {
         case N07 -> play(l, c.add(0, 3, 0), BossSounds.DG_ALARM.get(), 4F, 0.95F);
         default -> {
         }
      }
   }

   /** Pendant le combat : la sirène reprend régulièrement (labos) ; ailleurs un grondement sourd. */
   static void alarm(ShrineBlockEntity be, ServerLevel l) {
      if (be.kind == DungeonKind.N07) play(l, be.center().add(0, 4, 0), BossSounds.DG_ALARM.get(), 2.5F, 0.95F);
   }

   static void ritual(ShrineBlockEntity be, ServerLevel l, int s) {
      Vec3 c = be.center();
      Vec3 b = bossFeet(be);
      switch (be.kind) {
         case N07 -> {
            if (s == 4) play(l, c.add(0, 1.3, 0), BossSounds.DG_KEY_BEEP.get(), 0.9F, 1.0F);
            if (s == be.kind.insert) play(l, c.add(0, 1.3, 0), BossSounds.DG_KEY_SWIPE.get(), 1F, 1F);
            if (s == 20) play(l, c.add(0, 1.3, 0), BossSounds.DG_KEY_BEEP.get(), 0.9F, 1.4F);
            if (s == be.kind.grant) play(l, c.add(0, 1.3, 0), BossSounds.DG_KEY_GRANTED.get(), 1.2F, 1F);
            if (s == 30) play(l, b.add(0, 3, 0), BossSounds.DG_ALARM.get(), 3F, 1F);
            if (s >= 30 && s % 4 == 0) l.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.2, b.z, 3, 1.2, 0.05, 1.2, 0.02);
         }
      }
   }
}
