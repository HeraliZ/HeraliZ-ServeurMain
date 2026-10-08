package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.registry.ModSounds;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

abstract class KnifeFinisher extends Script {
   private static final Map<ExecutionType, Map<Blade, KnifeFinisher>> MADE = new EnumMap<>(ExecutionType.class);
   final ExecutionType type;
   final Blade blade;

   KnifeFinisher(ExecutionType type, Blade blade) {
      this.type = type;
      this.blade = blade;
   }

   static Choreography of(ExecutionType type, @Nullable Blade blade) {
      Blade b = blade != null ? blade : Blade.KARAMBIT;
      return MADE.computeIfAbsent(type, k -> new HashMap<>()).computeIfAbsent(b, k -> {
         return switch (type) {
            case PANTIN -> new Pantin(k);
            default -> new LamesFantomes(k);
         };
      });
   }

   static Blade bladeOf(@Nullable ExecutionType knife) {
      if (knife == null) {
         return Blade.KARAMBIT;
      }

      return switch (knife) {
         case BUTTERFLY -> Blade.BUTTERFLY;
         case M9 -> Blade.M9;
         default -> Blade.KARAMBIT;
      };
   }

   @Override
   Blade blade() {
      return this.blade;
   }

   @Override
   public final void tick(ExecutionScenes.Scene scene, int before, int now) {
      for (int t = Math.max(before + 1, now - 3); t <= now; t++) {
         if (t <= 3 && scene.level != null) {
            RegistryObject<SoundEvent> track = ModSounds.music(this.type.id);
            if (track != null) {
               SceneAudio.music(scene, (SoundEvent)track.get());
            }
         }

         Script.Frame f = (Script.Frame)this.at(t, scene.side, scene.victimYaw, scene.actorStart, scene.actorYaw);
         this.events(new KnifeFinisher.Fx(scene, f), t);
      }
   }

   abstract void events(KnifeFinisher.Fx var1, int var2);

   @Override
   public final void renderExtras(PoseStack ps, MultiBufferSource buffers, CoupDeGrace.State s, float age, Choreography.Extras ex) {
      if (s instanceof Script.Frame f) {
         this.draw(new Draw(ps, buffers, ex.facing(), ex.light()), f, age, ex);
      }
   }

   void draw(Draw d, Script.Frame f, float age, Choreography.Extras ex) {
   }

   @Nullable
   Vector3f[] knifePoints(Script.Frame f) {
      Matrix4f m = this.knifeInScene(f);
      return m == null ? null : new Vector3f[]{Knife.through(m, this.blade.pivot), Knife.through(m, this.blade.tip)};
   }

   void afterimages(Draw d, Script.Frame f, Choreography.Extras ex, float age, int count, float spacing, float r, float g, float b, float alpha) {
      if (!(alpha <= 0.01F)) {
         PlayerModel<LivingEntity> m = Finisher.model(2, ex.actorSlim());

         for (int i = count; i >= 1; i--) {
            Script.Frame past = this.other(f, f.t - i * spacing);
            this.poseActor(m, past, age - i * spacing);
            float a = alpha * (1.0F - (float)i / (count + 1));
            d.model(m, FxTex.HOLOGRAM, past.actor, 1.0F, r, g, b, a, Draw.Blend.GLOW);
         }
      }
   }

   static float ease(float t, float from, float to) {
      return Finisher.ease(t, from, to);
   }

   static float pulse(float t, float length) {
      return Finisher.pulse(t, length);
   }

   static float r(int rgb) {
      return Finisher.r(rgb);
   }

   static float g(int rgb) {
      return Finisher.g(rgb);
   }

   static float b(int rgb) {
      return Finisher.b(rgb);
   }

   static final class Fx extends SceneFx {
      final Script.Frame f;

      Fx(ExecutionScenes.Scene scene, Script.Frame f) {
         super(scene);
         this.f = f;
      }
   }
}
