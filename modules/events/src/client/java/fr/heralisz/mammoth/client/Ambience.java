package fr.heralisz.mammoth.client;

import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.dungeon.HornAltarBlockEntity;
import fr.heralisz.mammoth.sound.Sounds;
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
 * Ambiance du Cimetière des Mammouths, dès qu'on s'approche de l'autel : le vent polaire en boucle, et de temps en temps,
 * quelque part dans la glace, un grondement, un craquement, le cœur d'Ymirak qui bat sous le glacier. Coupée en combat.
 */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID, value = Dist.CLIENT)
public final class Ambience {
   private Ambience() {}

   private static Wind wind;
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
         HornAltarBlockEntity a = HornAltarBlockEntity.nearest(mc.level, mc.player.blockPosition(), 90);
         inside = a != null && a.state() != HornAltarBlockEntity.AWAKE && Hud.findBoss(mc) == null;
      }
      if (inside && (wind == null || wind.isStopped())) {
         wind = new Wind(Sounds.AMB_WIND.get());
         mc.getSoundManager().play(wind);
      }
      if (inside) {
         // l'air immobile de la salle : des poussières de givre en suspension, qui accrochent la lumière
         RandomSource rr = mc.level.random;
         for (int i = 0; i < 3; i++) {
            double x = mc.player.getX() + (rr.nextDouble() - 0.5) * 20, y = mc.player.getY() + rr.nextDouble() * 8 - 1,
               z = mc.player.getZ() + (rr.nextDouble() - 0.5) * 20;
            if (mc.level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z)).isAir())
               mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x, y, z, (rr.nextDouble() - 0.5) * 0.01, -0.004, (rr.nextDouble() - 0.5) * 0.01);
         }
      }
      if (!inside || --nextShot > 0) return;
      RandomSource r = mc.level.random;
      nextShot = 180 + r.nextInt(320);
      int k = r.nextInt(6);
      SoundEvent s = k < 2 ? Sounds.AMB_MOAN.get() : k < 4 ? Sounds.ICE_CRACK.get() : k == 4 ? Sounds.HEARTBEAT.get() : Sounds.ICICLE_HIT.get();
      double ang = r.nextDouble() * Math.PI * 2, dist = 10 + r.nextDouble() * 16;
      mc.level.playLocalSound(mc.player.getX() + Math.cos(ang) * dist, mc.player.getY() + r.nextDouble() * 4 - 1, mc.player.getZ() + Math.sin(ang) * dist, s,
         SoundSource.AMBIENT, 1.4F, 0.8F + r.nextFloat() * 0.3F, false);
   }

   private static final class Wind extends AbstractTickableSoundInstance {
      private float fade;

      Wind(SoundEvent s) {
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
         this.volume = Math.max(0.001F, 0.45F * fade);
         if (!inside && fade <= 0) this.stop();
      }
   }
}
