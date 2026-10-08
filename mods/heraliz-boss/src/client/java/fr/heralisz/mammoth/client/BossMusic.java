package fr.heralisz.mammoth.client;

import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.Mammoth;
import fr.heralisz.mammoth.sound.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Thème préhistorique du combat : tant qu'Ymirak est éveillé à moins de 80 blocs, sa piste tourne en boucle (catégorie Musique). */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID, value = Dist.CLIENT)
public final class BossMusic {
   private static final float VOLUME = 0.55F;
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
      Mammoth m = Hud.findBoss(mc);
      boolean want = m != null && !m.isRemoved();
      if (playing != null && (!want || playing.isStopped())) {
         playing.fadeOut();
         playing = null;
      }
      if (want && playing == null) {
         playing = new Loop(Sounds.MUSIC.get());
         mc.getSoundManager().play(playing);
      }
      if (playing != null && mc.level.getGameTime() % 20 == 0) mc.getMusicManager().stopPlaying();
   }

   private static final class Loop extends AbstractTickableSoundInstance {
      private float level;
      private boolean fading;

      Loop(SoundEvent ev) {
         super(ev, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
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
