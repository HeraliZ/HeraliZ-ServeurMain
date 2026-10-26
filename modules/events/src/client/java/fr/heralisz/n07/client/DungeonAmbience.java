package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.ShrineBlockEntity;
import fr.heralisz.n07.sound.BossSounds;
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
 * Ambiance des donjons, dès qu'on y met les pieds (sous terre, près d'un sanctuaire) : un bourdon grave en boucle, et de temps
 * en temps, quelque part dans le noir, un cri lointain, une tôle qui gémit, un râle, des coups contre une porte, un chuchotement.
 * Coupée pendant le combat (la musique du boss prend le relais). Tout est côté client, rien ne passe par le réseau.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class DungeonAmbience {
   private DungeonAmbience() {}

   private static Drone drone;
   private static boolean inside;
   private static fr.heralisz.n07.dungeon.DungeonKind kind = fr.heralisz.n07.dungeon.DungeonKind.N07;
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
         ShrineBlockEntity sh = ShrineBlockEntity.nearest(mc.level, mc.player.blockPosition(), 90);
         inside = sh != null && sh.state() != ShrineBlockEntity.AWAKE && !mc.level.canSeeSky(mc.player.blockPosition().above());
         if (sh != null) kind = sh.kind;
      }
      if (inside && (drone == null || drone.isStopped())) {
         drone = new Drone(BossSounds.DG_AMB_DRONE.get());
         mc.getSoundManager().play(drone);
      }
      if (!inside) return;
      if (--nextShot > 0) return;
      RandomSource r = mc.level.random;
      nextShot = 160 + r.nextInt(300);
      SoundEvent s = pick(kind, r.nextInt(7));
      boolean near = s == BossSounds.DG_AMB_WHISPER.get();
      double ang = r.nextDouble() * Math.PI * 2, dist = near ? 2.5 : 10 + r.nextDouble() * 14;
      mc.level.playLocalSound(mc.player.getX() + Math.cos(ang) * dist, mc.player.getY() + r.nextDouble() * 3 - 1, mc.player.getZ() + Math.sin(ang) * dist,
         s, SoundSource.AMBIENT, near ? 0.6F : 1.6F, 0.85F + r.nextFloat() * 0.3F, false);
   }

   /** Les bruits du noir, propres à chaque donjon (labos : cris et tôles ; quarantaine : toux ; chapelle : cloche et chaînes ;
    *  tertre : bois qui craque ; nid : pattes au plafond). */
   private static SoundEvent pick(fr.heralisz.n07.dungeon.DungeonKind k, int i) {
      return switch (k) {
         case N07 -> switch (i) {
            case 0 -> BossSounds.DG_AMB_SCREAM.get();
            case 1, 2 -> BossSounds.DG_AMB_MOAN.get();
            case 3 -> BossSounds.DG_AMB_GROAN.get();
            case 4 -> BossSounds.DG_AMB_THUMP.get();
            case 5 -> BossSounds.DG_AMB_DRIP.get();
            default -> BossSounds.DG_AMB_WHISPER.get();
         };
      };
   }

   /** Bourdon non positionnel qui monte doucement et s'efface quand on sort du donjon (ou que le combat commence). */
   private static final class Drone extends AbstractTickableSoundInstance {
      private float fade;

      Drone(SoundEvent s) {
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
