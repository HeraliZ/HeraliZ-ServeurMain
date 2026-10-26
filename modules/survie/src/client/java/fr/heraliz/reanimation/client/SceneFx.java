package fr.heraliz.reanimation.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

class SceneFx {
   final ExecutionScenes.Scene scene;

   SceneFx(ExecutionScenes.Scene scene) {
      this.scene = scene;
   }

   Vec3 world(Vector3f local) {
      return this.scene.world(local);
   }

   Vec3 dir(Vector3f local) {
      return this.scene.world(local).subtract(this.scene.world(new Vector3f()));
   }

   Vec3 dir(float x, float y, float z) {
      return this.dir(new Vector3f(x, y, z));
   }

   void sound(Sfx sound, Vector3f at, float volume, float pitch) {
      if (this.scene.level != null) {
         Vec3 w = this.world(at);
         this.scene.level.playLocalSound(w.x, w.y, w.z, (SoundEvent)sound.get().get(), SoundSource.PLAYERS, volume, pitch, false);
      }
   }

   void sound(Sfx sound, Vector3f at) {
      this.sound(sound, at, 1.0F, 0.95F + 0.1F * Vfx.RANDOM.nextFloat());
   }

   Vfx.P p(Sprite sprite, Vector3f at) {
      return Vfx.spawn(this.world(at), sprite).floor(this.scene.origin.y + 0.03, 0.0F);
   }

   void burst(Sprite sprite, Vector3f at, int n, float speed, float size, int life, int rgb, boolean soft) {
      for (int i = 0; i < n; i++) {
         Vfx.P p = this.p(sprite, at)
            .vel(Vfx.random(speed * (0.4F + 0.6F * Vfx.RANDOM.nextFloat())))
            .size(size, size * 0.4F)
            .life((int)(life * (0.6F + 0.6F * Vfx.RANDOM.nextFloat())))
            .color(rgb)
            .drag(0.9)
            .roll(0.0F, (Vfx.RANDOM.nextFloat() - 0.5F) * 0.4F);
         if (soft) {
            p.soft();
         }
      }
   }

   void sparks(Vector3f at, int n, float speed, int rgb) {
      for (int i = 0; i < n; i++) {
         this.p(Sprite.SPARK, at)
            .vel(Vfx.random(speed * (0.5F + 0.5F * Vfx.RANDOM.nextFloat())))
            .size(0.12F, 0.04F)
            .life(8 + Vfx.RANDOM.nextInt(8))
            .color(rgb)
            .gravity(0.02)
            .drag(0.88)
            .stretch(2.5F);
      }
   }

   void ring(Sprite sprite, Vector3f at, int n, float speed, float size, int life, int rgb, boolean soft) {
      for (int i = 0; i < n; i++) {
         float a = (float) (Math.PI * 2) * i / n;
         Vfx.P p = this.p(sprite, at)
            .vel(this.dir(Mth.cos(a) * speed, 0.02F, Mth.sin(a) * speed))
            .size(size, size * 1.6F)
            .life(life)
            .color(rgb)
            .drag(0.88);
         if (soft) {
            p.soft();
         }
      }
   }

   void shake(float shake, float punch) {
      if (this.scene.level != null) {
         ExecutionFx.kick(shake, punch);
      }
   }

   void flash(Vector3f at, float strength) {
      if (this.scene.level != null) {
         ExecutionFx.flashNear(this.scene, at, strength);
      }
   }

   void darken(float amount) {
      if (this.scene.level != null) {
         ExecutionFx.darken(this.scene, amount);
      }
   }

   void heartbeat(float strength) {
      if (this.scene.level != null && ExecutionScenes.local() == this.scene) {
         ExecutionFx.heartbeat(strength);
      }
   }

   boolean watchedBy(Entity e) {
      return this.scene.involves(e);
   }
}
