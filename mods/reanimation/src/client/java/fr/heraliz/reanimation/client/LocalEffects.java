package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.ClientConfig;
import fr.heraliz.reanimation.network.ActionPacket;
import fr.heraliz.reanimation.network.Network;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3f;

public final class LocalEffects {
   public static final int GIVE_UP_TICKS = 50;
   private static final ResourceLocation DESATURATE = new ResourceLocation("shaders/post/desaturate.json");
   private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.45F, 0.01F, 0.01F), 0.9F);
   public static int giveUpHold;
   private static boolean giveUpSent;
   private static boolean cancelSent;
   public static int flashTicks;
   public static int downedTicks;
   public static int ticksSinceBeat = 100;
   private static int nextBeat;
   private static PostChain ourEffect;
   private static boolean effectFailed;
   public static final int EXECUTE_TICKS = 10;
   public static int executeHold;
   private static int executeTarget = -1;
   private static boolean executeSent;

   private LocalEffects() {
   }

   static void onChange(Entity e, int oldFlags, int newFlags, int event) {
      Minecraft mc = Minecraft.getInstance();
      if (e == mc.player) {
         if (event == 1) {
            downedTicks = 0;
            giveUpHold = 0;
            giveUpSent = false;
            nextBeat = 12;
         }

         if (event == 2) {
            flashTicks = 30;
         }

         if ((newFlags & 144) == 0) {
            cancelSent = false;
         }

         if ((newFlags & 1) == 0) {
            giveUpSent = false;
         }
      }
   }

   static void requestCancel() {
      if (!cancelSent) {
         cancelSent = true;
         Network.sendToServer(new ActionPacket(1));
      }
   }

   private static void executeKey(Minecraft mc) {
      Entity target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
      ClientDowned.Info info = target == null ? null : ClientDowned.get(target.getId());
      boolean valid = info != null && info.has(1) && !info.has(4) && !info.has(2) && !info.has(256) && ClientDowned.localFlags() == 0 && mc.screen == null;
      if (valid && ClientSetup.EXECUTE.isDown()) {
         if (target.getId() != executeTarget) {
            executeTarget = target.getId();
            executeHold = 0;
            executeSent = false;
         }

         if (++executeHold >= 10 && !executeSent) {
            executeSent = true;
            Network.sendToServer(new ActionPacket(2, target.getId()));
         }
      } else {
         executeHold = 0;
         executeTarget = -1;
         executeSent = false;
      }
   }

   static void tick(Minecraft mc) {
      if (mc.player != null && mc.level != null) {
         ClientDowned.Info me = ClientDowned.local();
         int f = me == null ? 0 : me.flags;
         boolean downed = (f & 1) != 0;
         desaturate(mc, downed && (Boolean)ClientConfig.DESATURATE.get() && (f & 256) == 0 || ExecutionFx.gray);
         executeKey(mc);
         ticksSinceBeat++;
         if (downed) {
            downedTicks++;
            boolean revived = (f & 4) != 0;
            float life = me.bleedFraction();
            if (--nextBeat <= 0) {
               nextBeat = revived ? 13 : Math.round(Mth.lerp(life, 34.0F, 18.0F));
               ticksSinceBeat = 0;
               float volume = (float)((Double)ClientConfig.HEARTBEAT_VOLUME.get() * (revived ? 0.9 : 0.45 + 0.55 * life));
               if (volume > 0.01F) {
                  mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, revived ? 1.15F : 0.95F, volume));
               }
            }

            boolean holding = !revived && (f & 256) == 0 && mc.screen == null && ClientSetup.GIVE_UP.isDown();
            if (holding) {
               if (++giveUpHold >= 50 && !giveUpSent) {
                  giveUpSent = true;
                  Network.sendToServer(new ActionPacket(0));
               }
            } else {
               giveUpHold = Math.max(0, giveUpHold - 3);
            }
         } else {
            downedTicks = 0;
            giveUpHold = 0;
            nextBeat = 0;
         }

         if (flashTicks > 0) {
            flashTicks--;
         }

         worldEffects(mc, mc.level);
      } else {
         ourEffect = null;
      }
   }

   private static void desaturate(Minecraft mc, boolean on) {
      GameRenderer renderer = mc.gameRenderer;
      if (on) {
         if (renderer.currentEffect() == null && !effectFailed) {
            renderer.loadEffect(DESATURATE);
            ourEffect = renderer.currentEffect();
            if (ourEffect == null) {
               effectFailed = true;
            }
         }
      } else if (ourEffect != null) {
         if (renderer.currentEffect() == ourEffect) {
            renderer.shutdownEffect();
         }

         ourEffect = null;
      }
   }

   private static void worldEffects(Minecraft mc, ClientLevel level) {
      RandomSource random = level.getRandom();

      for (Entry<ClientDowned.Info> entry : ClientDowned.all()) {
         ClientDowned.Info info = (ClientDowned.Info)entry.getValue();
         if (info.has(1)) {
            Entity e = level.getEntity(entry.getIntKey());
            if (e != null) {
               boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
               boolean moving = e.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || e.xo != e.getX() || e.zo != e.getZ();
               if (!self && random.nextInt(moving ? 3 : 12) == 0) {
                  level.addParticle(
                     BLOOD,
                     e.getX() + (random.nextDouble() - 0.5) * 0.7,
                     e.getY() + 0.12 + random.nextDouble() * 0.2,
                     e.getZ() + (random.nextDouble() - 0.5) * 0.7,
                     0.0,
                     0.0,
                     0.0
                  );
               }

               if (info.has(4) && info.reviveMax > 0) {
                  float p = (float)info.revive / info.reviveMax;
                  if (p > 0.1F && p < 0.86F && info.revive % 12 == 0) {
                     level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.45F, 1.45F, false);
                     // plus de cœurs de Minecraft pendant la réanimation (23/10, demande du propriétaire)
                  }
               }
            }
         }
      }
   }

   public static void reset() {
      giveUpHold = 0;
      flashTicks = 0;
      downedTicks = 0;
      executeHold = 0;
      ourEffect = null;
   }
}
