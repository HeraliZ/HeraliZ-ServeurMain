package fr.heralisz.mere.client;

import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.entity.RuinMother;
import fr.heralisz.mere.sound.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** « Le Roi Brûlé » (orgue, chœur, timbales) : dès le prélude et tant qu'Aldric combat à moins de 90 blocs, en boucle (catégorie Musique). */
@Mod.EventBusSubscriber(modid = HeraliszMere.MODID, value = Dist.CLIENT)
public final class BossMusic {
   private static final float VOLUME = 0.8F;
   private static Loop playing;

   private BossMusic() {}

   /** Le rituel a commencé (l'œuf est posé) : la musique monte déjà, derrière la toile. */
   private static boolean ritual(Minecraft mc) {
      fr.heralisz.mere.dungeon.NestHallBE h = fr.heralisz.mere.dungeon.NestHallBE.nearest(mc.level, mc.player.blockPosition(), 110);
      return h != null && (h.state() == fr.heralisz.mere.dungeon.NestHallBE.COUNTDOWN || h.state() == fr.heralisz.mere.dungeon.NestHallBE.ENTRY);
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (playing != null) playing.fadeOut();
         playing = null;
         return;
      }
      RuinMother m = Hud.findBoss(mc);
      boolean want = ritual(mc) || m != null && !m.isRemoved() && !(m.isDeadOrDying() && m.deathTime > 140) || Cinematic.active() && (Cinematic.kind() == RuinMother.SPAWN_K || Cinematic.kind() == RuinMother.ENTRY_K);
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
         super(ev, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
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
