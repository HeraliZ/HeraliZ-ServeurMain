package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.ClientConfig;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class SceneAudio {
   private static final Map<ExecutionScenes.Scene, SoundInstance> MUSIC = new IdentityHashMap<>();

   private SceneAudio() {
   }

   static void music(ExecutionScenes.Scene s, SoundEvent track) {
      if (!MUSIC.containsKey(s) && s.ticks <= 3 && (Boolean)ClientConfig.EXECUTION_MUSIC.get()) {
         Vec3 at = s.origin.add(0.0, 1.0, 0.0);
         SoundInstance sound = new SimpleSoundInstance(track, SoundSource.RECORDS, 1.0F, 1.0F, Vfx.RANDOM, at.x, at.y, at.z);
         Minecraft.getInstance().getSoundManager().play(sound);
         MUSIC.put(s, sound);
      }
   }

   static void dropped(ExecutionScenes.Scene s) {
      SoundInstance sound = MUSIC.remove(s);
      if (sound != null && !s.struck) {
         Minecraft.getInstance().getSoundManager().stop(sound);
      }
   }

   static void clear() {
      for (SoundInstance sound : MUSIC.values()) {
         Minecraft.getInstance().getSoundManager().stop(sound);
      }

      MUSIC.clear();
   }
}
