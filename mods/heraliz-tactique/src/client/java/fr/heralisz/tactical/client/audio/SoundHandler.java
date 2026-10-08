package fr.heralisz.tactical.client.audio;

import com.tacz.guns.client.sound.GunSoundInstance;
import java.lang.reflect.Field;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import fr.heralisz.tactical.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

public class SoundHandler {
   private static SoundHandler instance;
   private final Map<SoundInstance, Float> soundVolumes = new ConcurrentHashMap<>();
   private boolean isDeafened;
   private Field playingSounds;
   private SoundEngine soundEngine;
   private StunRingingSound ringing;

   public static SoundHandler get() {
      if (instance == null) {
         instance = new SoundHandler();
      }

      return instance;
   }

   private SoundHandler() {
      this.initReflection();
   }

   private void initReflection() {
      this.playingSounds = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
   }

   @SubscribeEvent
   public void deafenPlayer(ClientTickEvent event) {
      if (event.phase != Phase.START && Minecraft.getInstance().player != null && this.soundEngine != null) {
         MobEffectInstance effect = Minecraft.getInstance().player.getEffect((MobEffect)ModEffects.DEAFENED.get());
         if (effect != null || this.isDeafened) {
            if (this.ringing != null && Minecraft.getInstance().getSoundManager().isActive(this.ringing)) {
               Map<SoundInstance, ChannelHandle> playingSounds;
               try {
                  playingSounds = (Map<SoundInstance, ChannelHandle>)this.playingSounds.get(this.soundEngine);
               } catch (IllegalArgumentException | IllegalAccessException e) {
                  return;
               }

               if (effect != null) {
                  try {
                     playingSounds.forEach(
                        (sound, entryx) -> {
                           if (sound != null && !(sound instanceof TickableSoundInstance)) {
                              ResourceLocation loc = sound.getLocation();
                              if (!loc.equals(new ResourceLocation("minecraft", "entity.generic.explode"))) {
                                 if (!loc.equals(new ResourceLocation("heralisz_tactical", "entity.grenade.flash.explode"))) {
                                    float volume = sound instanceof SoundHandler.SoundMuted
                                       ? ((SoundHandler.SoundMuted)sound).getVolumeInitial()
                                       : sound.getVolume();
                                    this.soundVolumes.put(sound, volume);
                                    entryx.execute(soundSource -> soundSource.setVolume(this.getMutedVolume(effect.getDuration(), volume)));
                                 }
                              }
                           }
                        }
                     );
                  } catch (ConcurrentModificationException var7) {
                  }

                  this.isDeafened = true;
               } else if (this.isDeafened) {
                  this.isDeafened = false;

                  for (Entry<SoundInstance, Float> entry : this.soundVolumes.entrySet()) {
                     ChannelHandle entry1 = playingSounds.get(entry.getKey());
                     if (entry1 != null) {
                        entry1.execute(soundSource -> soundSource.setVolume(entry.getValue()));
                     }
                  }

                  this.soundVolumes.clear();
               }
            } else {
               this.ringing = new StunRingingSound();
               Minecraft.getInstance().getSoundManager().play(this.ringing);
            }
         }
      }
   }

   @SubscribeEvent
   public void lowerInitialVolume(PlaySoundEvent event) {
      if (this.soundEngine == null) {
         this.soundEngine = event.getEngine();
      }

      if (this.isDeafened
         && Minecraft.getInstance().player != null
         && (!(event.getSound() instanceof TickableSoundInstance) || event.getSound() instanceof GunSoundInstance)) {
         ResourceLocation loc = event.getSound().getLocation();
         if (!loc.equals(new ResourceLocation("minecraft", "entity.generic.explode"))) {
            MobEffectInstance effect = Minecraft.getInstance().player.getEffect((MobEffect)ModEffects.DEAFENED.get());
            int duration = effect != null ? effect.getDuration() : 0;
            if (duration != 0) {
               event.getSound().resolve(Minecraft.getInstance().getSoundManager());
               event.setSound(new SoundHandler.SoundMuted(event.getSound(), duration, false));
            }
         }
      }
   }

   private float getMutedVolume(float duration, float volumeBase) {
      float volumeMin = 0.01F * volumeBase;
      float percent = Math.min(duration / 100.0F, 1.0F);
      return volumeMin + (1.0F - percent) * (volumeBase - volumeMin);
   }

   public static class SoundMuted implements SoundInstance {
      private SoundInstance parent;
      private float volume;
      private float volumeInitial;

      public SoundMuted(SoundInstance parent, int duration, boolean isStunGrenade) {
         this.parent = parent;
         this.volumeInitial = Mth.clamp(parent.getVolume(), 0.0F, 1.0F);
         this.volume = SoundHandler.get().getMutedVolume(duration, this.volumeInitial);
         if (isStunGrenade) {
            this.volumeInitial = this.volume;
         }
      }

      public float getVolume() {
         return this.volume;
      }

      public float getVolumeInitial() {
         return this.volumeInitial;
      }

      public ResourceLocation getLocation() {
         return this.parent.getLocation();
      }

      @Nullable
      public WeighedSoundEvents resolve(SoundManager handler) {
         return this.parent.resolve(handler);
      }

      public Sound getSound() {
         return this.parent.getSound();
      }

      public SoundSource getSource() {
         return this.parent.getSource();
      }

      public boolean isLooping() {
         return this.parent.isLooping();
      }

      public boolean isRelative() {
         return false;
      }

      public int getDelay() {
         return this.parent.getDelay();
      }

      public float getPitch() {
         return this.parent.getPitch();
      }

      public double getX() {
         return this.parent.getX();
      }

      public double getY() {
         return this.parent.getY();
      }

      public double getZ() {
         return this.parent.getZ();
      }

      public Attenuation getAttenuation() {
         return this.parent.getAttenuation();
      }
   }
}
