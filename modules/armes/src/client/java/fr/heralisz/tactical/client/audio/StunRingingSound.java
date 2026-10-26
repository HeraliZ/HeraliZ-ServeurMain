package fr.heralisz.tactical.client.audio;

import fr.heralisz.tactical.init.ModEffects;
import fr.heralisz.tactical.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public class StunRingingSound extends AbstractTickableSoundInstance {
   public StunRingingSound() {
      super((SoundEvent)ModSounds.DEAFENED_RING.get(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
      this.looping = true;
      this.attenuation = Attenuation.NONE;
      this.tick();
   }

   public void tick() {
      Player player = Minecraft.getInstance().player;
      if (player != null && player.isAlive()) {
         MobEffectInstance effect = player.getEffect((MobEffect)ModEffects.DEAFENED.get());
         if (effect != null) {
            this.x = (float)player.getX();
            this.y = (float)player.getY();
            this.z = (float)player.getZ();
            this.volume = Math.min(effect.getDuration() / 100.0F, 1.0F);
            return;
         }
      }

      this.stop();
   }
}
