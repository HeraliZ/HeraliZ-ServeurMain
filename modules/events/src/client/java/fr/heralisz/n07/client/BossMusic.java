package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.api.BossInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Musique d'ambiance des boss : dès qu'un boss vivant (ou en train de mourir) est à moins de 72 blocs, sa piste tourne en boucle,
 * à peine audible (catégorie « Musique »), avec un fondu d'entrée de 10 s et de sortie de 5 s. La musique vanilla se tait.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class BossMusic {
   /** Volume de base (1 = plein pot) : juste une nappe sous le combat. */
   private static final float VOLUME = 0.16F;
   private static Loop playing;

   private BossMusic() {}

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (playing != null) playing.fadeOut();
         playing = null;
         return;
      }
      SoundEvent want = null;
      double best = 72 * 72;
      for (LivingEntity le : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(72), x -> x instanceof BossInfo bi && !bi.dormant())) {
         SoundEvent s = ((BossInfo) le).music();
         double dd = le.distanceToSqr(mc.player);
         if (s != null && !le.isRemoved() && dd < best) {
            best = dd;
            want = s;
         }
      }
      if (playing != null && (playing.event != want || playing.isStopped())) {
         playing.fadeOut();
         playing = null;
      }
      if (want != null && playing == null) {
         playing = new Loop(want);
         mc.getSoundManager().play(playing);
      }
      if (playing != null && mc.level.getGameTime() % 20 == 0) mc.getMusicManager().stopPlaying();
   }

   private static final class Loop extends AbstractTickableSoundInstance {
      final SoundEvent event;
      private float level;
      private boolean fading;

      Loop(SoundEvent ev) {
         super(ev, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
         this.event = ev;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
      }

      void fadeOut() {
         fading = true;
      }

      @Override
      public void tick() {
         level = fading ? Math.max(0F, level - 0.01F) : Math.min(1F, level + 0.005F);
         this.volume = Math.max(0.0001F, VOLUME * level);
         if (fading && level <= 0F) this.stop();
      }
   }
}
