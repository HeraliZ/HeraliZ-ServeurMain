package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

abstract class Finisher extends Spectacle {
   static final float BEAT = 8.0F;
   static final float STRIKE = 96.0F;
   static final float END = 118.0F;
   private static final PlayerModel<?>[] MODELS = new PlayerModel[6];
   private final HumanoidModel<LivingEntity> probe = Script.probe();

   Finisher(ExecutionType type) {
      super(type);
   }

   static float beat(float n) {
      return n * 8.0F;
   }

   @Override
   final void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int t) {
      if (t <= 3 && scene.level != null) {
         RegistryObject<SoundEvent> track = ModSounds.music(this.type.id);
         if (track != null) {
            SceneAudio.music(scene, (SoundEvent)track.get());
         }
      }

      this.events(new Finisher.Fx(scene, s), t);
   }

   abstract void events(Finisher.Fx var1, int var2);

   @Override
   final void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras ex) {
      Draw d = new Draw(ps, buffers, ex.facing(), ex.light());
      float va = this.victimAlpha(s);
      if (va > 0.0F && va < 1.0F) {
         this.drawVictim(d, s, age, ex, va);
      }

      float aa = this.actorAlpha(s);
      if (aa > 0.0F && aa < 1.0F) {
         this.drawActor(d, s, age, ex, aa);
      }

      this.draw(d, s, age, ex);
   }

   void draw(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex) {
   }

   void drawVictim(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex, float alpha) {
      PlayerModel<?> m = model(1, ex.victimSlim());
      this.poseVictim(m, s, age);
      d.model(m, ex.victimSkin(), s.victim, 1.0F, 1.0F, 1.0F, 1.0F, alpha, Draw.Blend.CLEAR);
   }

   void drawActor(Draw d, Spectacle.Frame s, float age, Choreography.Extras ex, float alpha) {
      PlayerModel<?> m = model(0, ex.actorSlim());
      this.poseActor(m, s, age);
      d.model(m, ex.actorSkin(), s.actor, 1.0F, 1.0F, 1.0F, 1.0F, alpha, Draw.Blend.CLEAR);
   }

   Spectacle.Frame other(Spectacle.Frame s, float t) {
      return this.build(Math.max(0.0F, t), s.side, s.sceneVictimYaw, s.sceneActorStart, s.sceneActorYaw);
   }

   void afterimages(
      Draw d, Spectacle.Frame s, Choreography.Extras ex, float age, int count, float spacing, float r, float g, float b, float alpha, boolean hologram
   ) {
      if (!(alpha <= 0.01F)) {
         PlayerModel<?> m = model(2, ex.actorSlim());

         for (int i = count; i >= 1; i--) {
            Spectacle.Frame past = this.other(s, s.t - i * spacing);
            this.poseActor(m, past, age - i * spacing);
            float a = alpha * (1.0F - (float)i / (count + 1));
            if (hologram) {
               d.model(m, FxTex.HOLOGRAM, past.actor, 1.0F, r, g, b, a, Draw.Blend.GLOW);
            } else {
               d.model(m, ex.actorSkin(), past.actor, 1.0F, r, g, b, a * 0.6F, Draw.Blend.SOFT);
            }
         }
      }
   }

   static PlayerModel<LivingEntity> model(int slot, boolean slim) {
      int i = Mth.clamp(slot, 0, 2) * 2 + (slim ? 1 : 0);
      if (MODELS[i] == null) {
         MODELS[i] = ExecutionFx.playerModel(slim);
      }

      return (PlayerModel<LivingEntity>)MODELS[i];
   }

   static Vector3f orbit(float deg, float r, float up) {
      float a = deg * (float) (Math.PI / 180.0);
      return at(r * Mth.cos(a), r * Mth.sin(a), up);
   }

   static void rise(CoupDeGrace.Body v, float k, int side, float yaw, Vector3f mid) {
      float y = Mth.rotLerp(k, 90.0F * side, yaw);
      v.turn(y, -90.0F * (1.0F - k), 0.0F, 0.0F).anchor(1.0556251F, at(0.0F, 0.0F, 0.3F).lerp(mid, k));
   }

   static void pose(ModelPart p, float k, float x, float y, float z) {
      if (k > 0.0F) {
         p.xRot = Mth.lerp(k, p.xRot, x);
         p.yRot = Mth.lerp(k, p.yRot, y);
         p.zRot = Mth.lerp(k, p.zRot, z);
      }
   }

   static void limp(HumanoidModel<?> m, float age, float k) {
      float sway = Mth.sin(age * 0.3F);
      pose(m.rightArm, k, 0.12F + 0.08F * sway, 0.0F, 0.18F);
      pose(m.leftArm, k, 0.1F - 0.08F * sway, 0.0F, -0.18F);
      pose(m.rightLeg, k, 0.12F, 0.0F, 0.06F);
      pose(m.leftLeg, k, -0.06F, 0.0F, -0.06F);
      pose(m.head, k, 0.65F, 0.15F * sway, 0.0F);
   }

   static void convulse(HumanoidModel<?> m, float age, float k) {
      pose(m.rightArm, k, -0.35F + 0.35F * Mth.sin(age * 9.1F), 0.0F, 1.0F + 0.3F * Mth.sin(age * 7.3F));
      pose(m.leftArm, k, -0.3F + 0.35F * Mth.sin(age * 8.3F + 1.0F), 0.0F, -1.0F - 0.3F * Mth.sin(age * 6.7F));
      pose(m.rightLeg, k, 0.25F * Mth.sin(age * 7.9F), 0.0F, 0.22F);
      pose(m.leftLeg, k, -0.25F * Mth.sin(age * 8.7F), 0.0F, -0.22F);
      pose(m.head, k, -0.55F + 0.25F * Mth.sin(age * 11.0F), 0.2F * Mth.sin(age * 5.0F), 0.0F);
   }

   static void spread(HumanoidModel<?> m, float k) {
      pose(m.rightArm, k, -0.1F, 0.0F, 1.4F);
      pose(m.leftArm, k, -0.1F, 0.0F, -1.4F);
      pose(m.rightLeg, k, 0.0F, 0.0F, 0.38F);
      pose(m.leftLeg, k, 0.0F, 0.0F, -0.38F);
      pose(m.head, k, 0.25F, 0.0F, 0.0F);
   }

   static void armsUp(HumanoidModel<?> m, float k) {
      pose(m.rightArm, k, -2.95F, 0.0F, 0.3F);
      pose(m.leftArm, k, -2.95F, 0.0F, -0.3F);
   }

   static Vector3f hand(CoupDeGrace.Body body, ModelPart arm) {
      float x = arm.x < 0.0F ? -1.0F : 1.0F;
      return Knife.sceneFromModel(body).mul(Knife.partMatrix(arm)).transformPosition(new Vector3f(x / 16.0F, 0.625F, 0.0F));
   }

   Vector3f actorHand(Spectacle.Frame s, boolean right) {
      this.poseActor(this.probe, s, s.t);
      return hand(s.actor, right ? this.probe.rightArm : this.probe.leftArm);
   }

   Vector3f actorFoot(Spectacle.Frame s, boolean right) {
      this.poseActor(this.probe, s, s.t);
      return foot(s.actor, right ? this.probe.rightLeg : this.probe.leftLeg);
   }

   Vector3f victimHand(Spectacle.Frame s, boolean right) {
      this.poseVictim(this.probe, s, s.t);
      return hand(s.victim, right ? this.probe.rightArm : this.probe.leftArm);
   }

   Vector3f victimFoot(Spectacle.Frame s, boolean right) {
      this.poseVictim(this.probe, s, s.t);
      return foot(s.victim, right ? this.probe.rightLeg : this.probe.leftLeg);
   }

   static Vector3f foot(CoupDeGrace.Body body, ModelPart leg) {
      return Knife.sceneFromModel(body).mul(Knife.partMatrix(leg)).transformPosition(new Vector3f(0.0F, 0.71875F, -0.0625F));
   }

   static float ease(float t, float from, float to) {
      return smooth(phase(t, from, to));
   }

   static float pulse(float t, float length) {
      float k = t % 8.0F / length;
      return k < 1.0F ? (1.0F - k) * (1.0F - k) : 0.0F;
   }

   static float r(int rgb) {
      return (rgb >> 16 & 0xFF) / 255.0F;
   }

   static float g(int rgb) {
      return (rgb >> 8 & 0xFF) / 255.0F;
   }

   static float b(int rgb) {
      return (rgb & 0xFF) / 255.0F;
   }

   static boolean local(ExecutionScenes.Scene scene) {
      return ExecutionScenes.local() == scene && Minecraft.getInstance().player != null;
   }

   static final class Fx extends SceneFx {
      final Spectacle.Frame s;

      Fx(ExecutionScenes.Scene scene, Spectacle.Frame s) {
         super(scene);
         this.s = s;
      }
   }
}
