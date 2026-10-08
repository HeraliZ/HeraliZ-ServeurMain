package fr.heralisz.plague.client;

import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.dungeon.StationHallBE;
import fr.heralisz.plague.fx.Particles;
import fr.heralisz.plague.sound.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ambiance du Nid, dès qu'on y entre : le souffle de la grotte et les gouttes en boucle ; de temps en temps, un cri d'araignée au
 * loin, la soie qui se tend, un sac d'œufs qui palpite. Des fils de soie flottent dans l'air. Coupée pendant le combat.
 */
@Mod.EventBusSubscriber(modid = HeraliszPlague.MODID, value = Dist.CLIENT)
public final class Ambience {
   private Ambience() {}

   private static Loop loop;
   private static boolean inside;
   private static int nextShot = 200;

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         inside = false;
         return;
      }
      if (mc.level.getGameTime() % 20 == 0) {
         StationHallBE h = StationHallBE.nearest(mc.level, mc.player.blockPosition(), 90);
         inside = h != null && h.state() != StationHallBE.AWAKE && Hud.findBoss(mc) == null;
      }
      if (inside && (loop == null || loop.isStopped())) {
         loop = new Loop(Sounds.DG_AMB_DRONE.get());
         mc.getSoundManager().play(loop);
      }
      if (inside) {
         RandomSource rr = mc.level.random;
         for (int i = 0; i < 2; i++) {
            double x = mc.player.getX() + (rr.nextDouble() - 0.5) * 22, y = mc.player.getY() + rr.nextDouble() * 9 - 1,
               z = mc.player.getZ() + (rr.nextDouble() - 0.5) * 22;
            if (mc.level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z)).isAir())
               mc.level.addParticle(fr.heralisz.plague.particle.ModParticles.DUST.get(), x, y, z, (rr.nextDouble() - 0.5) * 0.008, -0.003, (rr.nextDouble() - 0.5) * 0.008);
         }
      }
      if (!inside || --nextShot > 0) return;
      RandomSource r = mc.level.random;
      nextShot = 160 + r.nextInt(300);
      int k = r.nextInt(7);
      SoundEvent s = k < 2 ? Sounds.DG_AMB_DRIP.get() : k < 4 ? Sounds.DG_AMB_COUGH.get() : k < 5 ? Sounds.DG_VENT.get() : k < 6 ? Sounds.DG_AMB_MOAN.get() : Sounds.DG_AMB_THUMP.get();
      double ang = r.nextDouble() * Math.PI * 2, dist = 8 + r.nextDouble() * 18;
      mc.level.playLocalSound(mc.player.getX() + Math.cos(ang) * dist, mc.player.getY() + r.nextDouble() * 4, mc.player.getZ() + Math.sin(ang) * dist, s,
         SoundSource.AMBIENT, k < 2 ? 0.7F : 1.1F, 0.8F + r.nextFloat() * 0.3F, false);
   }

   private static final class Loop extends AbstractTickableSoundInstance {
      private float fade;

      Loop(SoundEvent s) {
         super(s, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
      }

      @Override
      public void tick() {
         fade = inside ? Math.min(1F, fade + 0.01F) : fade - 0.02F;
         this.volume = Math.max(0.001F, 0.5F * fade);
         if (!inside && fade <= 0) this.stop();
      }
   }
}
