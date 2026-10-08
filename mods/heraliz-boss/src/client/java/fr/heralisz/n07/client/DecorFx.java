package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.decor.DecorBlockEntity;
import fr.heralisz.n07.decor.DecorSpec;
import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Vie des décors côté client : feu des barils (flammes, braises, fumée, crépitements), fumée de cheminée et jets de vapeur de la
 * locomotive, échappement du groupe électrogène, étincelles des néons fatigués, poussière dans les faisceaux, gouttes ; et les
 * sons d'ambiance en boucle (ronron du groupe, bourdonnement du néon, grésillement de la radio).
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class DecorFx {
   private DecorFx() {}

   private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();

   public static void tick(DecorBlockEntity be) {
      Minecraft mc = Minecraft.getInstance();
      Level l = be.getLevel();
      if (mc.player == null || l == null) return;
      DecorSpec s = be.kind.spec;
      Vec3 c = be.toWorld(0, 8, 0);
      double d2 = mc.player.distanceToSqr(c);
      if (d2 > 64 * 64) return;
      RandomSource r = l.random;
      int drop = s.drop ? be.drop() : 0;
      for (int i = 0; i < s.fxType.length; i++) {
         double x = s.fx[i * 4], y = s.fx[i * 4 + 1] - (s.drop && s.fx[i * 4 + 1] < 0 ? drop * 16 : 0), z = s.fx[i * 4 + 2], sp = s.fx[i * 4 + 3];
         Vec3 p = be.toWorld(x + (r.nextDouble() - 0.5) * sp, y, z + (r.nextDouble() - 0.5) * sp);
         switch (s.fxType[i]) {
            case FIRE -> {
               if (r.nextInt(2) == 0) l.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.02 + r.nextDouble() * 0.02, 0);
               if (r.nextInt(6) == 0) l.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.4, p.z, 0, 0.03, 0);
               if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.3, p.z, 0, 0.05, 0);
               if (r.nextInt(10) == 0) l.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
               if (r.nextInt(40) == 0 && d2 < 20 * 20) {
                  l.playLocalSound(p.x, p.y, p.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.9F, 0.8F + r.nextFloat() * 0.4F, false);
               }
            }
            case EMBER -> {
               if (r.nextInt(4) == 0) l.addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, 0.004, 0);
               if (r.nextInt(30) == 0) l.addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 0, 0.01, 0);
            }
            case SMOKE -> {
               if (r.nextInt(3) == 0) l.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.x, p.y, p.z, 0, 0.06, 0);
            }
            case STEAM -> {
               // jets de vapeur par bouffées, parfois un long soupir
               int cyc = (int) ((l.getGameTime() + (long) (be.phase * 400)) % 160);
               if (cyc < 18 && r.nextInt(2) == 0) {
                  Vec3 side = be.toWorld(x + Math.signum(x) * 16, y, z).subtract(be.toWorld(x, y, z)).scale(0.08);
                  l.addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, side.x + (r.nextDouble() - 0.5) * 0.02, 0.01, side.z + (r.nextDouble() - 0.5) * 0.02);
                  if (cyc == 0 && d2 < 24 * 24) {
                     l.playLocalSound(p.x, p.y, p.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 0.5F + r.nextFloat() * 0.2F, false);
                  }
               }
            }
            case EXHAUST -> {
               if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.04, 0);
               if (r.nextInt(25) == 0) l.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, 0.03, 0);
            }
            case SPARK -> {
               if (r.nextInt(70) == 0) {
                  for (int k = 0; k < 4; k++) {
                     l.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.3);
                  }
               }
            }
            case DUST -> {
               if (r.nextInt(3) == 0) l.addParticle(ModParticles.DUST.get(), p.x, p.y + (r.nextDouble() - 0.5) * sp / 16, p.z, 0, 0.002, 0);
            }
            case DRIP -> {
               if (r.nextInt(40) == 0) l.addParticle(ParticleTypes.DRIPPING_WATER, p.x, p.y, p.z, 0, 0, 0);
            }
         }
      }
      if (s.loop != null && d2 < 28 * 28) {
         SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(HeraliszN07.MODID, s.loop));
         if (ev != null) loop(be, ev, s.loopVolume, c);
      }
   }

   private static void loop(DecorBlockEntity be, SoundEvent s, float vol, Vec3 at) {
      Loop lp = LOOPS.get(be.getBlockPos());
      if (lp != null && !lp.isStopped()) return;
      lp = new Loop(be, s, vol, at);
      LOOPS.put(be.getBlockPos(), lp);
      Minecraft.getInstance().getSoundManager().play(lp);
   }

   @SubscribeEvent
   public static void clientTick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      if (Minecraft.getInstance().level == null && !LOOPS.isEmpty()) LOOPS.clear();
      LOOPS.values().removeIf(AbstractTickableSoundInstance::isStopped);
   }

   /** Son en boucle attaché à un décor : s'arrête s'il disparaît ou si l'on s'éloigne. */
   private static final class Loop extends AbstractTickableSoundInstance {
      private final DecorBlockEntity be;
      private final float target;
      private float fade;

      Loop(DecorBlockEntity be, SoundEvent s, float vol, Vec3 at) {
         super(s, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
         this.be = be;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = at.x;
         this.y = at.y;
         this.z = at.z;
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         this.pitch = 0.95F + be.phase * 0.1F;
         this.target = vol;
      }

      @Override
      public void tick() {
         Minecraft mc = Minecraft.getInstance();
         boolean gone = be.isRemoved() || mc.player == null || mc.player.distanceToSqr(this.x, this.y, this.z) > 32 * 32;
         fade = gone ? fade - 0.05F : Math.min(1F, fade + 0.05F);
         this.volume = Math.max(0.001F, target * fade);
         if (gone && fade <= 0) this.stop();
      }
   }
}
