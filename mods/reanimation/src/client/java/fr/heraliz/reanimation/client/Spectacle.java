package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.Timeline;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

abstract class Spectacle implements Choreography {
   static final float PX = 0.05859375F;
   static final float NECK = 1.4071876F;
   static final float HIPS = 0.7040626F;
   static final float MID = 1.0556251F;
   static final float WAIST = 0.9F;
   static final float LYING = 0.15F;
   static final float PRONE = 0.3F;
   static final float KNEEL_DROP = 0.32F;
   final ExecutionType type;

   Spectacle(ExecutionType type) {
      this.type = type;
   }

   protected abstract void frames(Spectacle.Frame var1, float var2, float var3);

   abstract void poseActor(HumanoidModel<?> var1, Spectacle.Frame var2, float var3);

   abstract void poseVictim(HumanoidModel<?> var1, Spectacle.Frame var2, float var3);

   void poseSoul(HumanoidModel<?> m, Spectacle.Frame s, float age) {
   }

   abstract CoupDeGrace.Shot shot(Spectacle.Frame var1);

   abstract float endFwd();

   abstract float endSide();

   void tick(ExecutionScenes.Scene scene, Spectacle.Frame s, int tick) {
   }

   void renderExtras(PoseStack ps, MultiBufferSource buffers, Spectacle.Frame s, float age, Choreography.Extras extras) {
   }

   final Spectacle.Frame build(float t, int side, float victimYaw, Vector3f actorStart, float actorYaw) {
      Spectacle.Frame s = new Spectacle.Frame(t, side);
      s.sceneVictimYaw = victimYaw;
      s.sceneActorStart.set(actorStart);
      s.sceneActorYaw = actorYaw;
      this.frames(s, t, victimYaw);
      float in = smooth(t / 5.0F);
      if (in < 1.0F) {
         s.actorPoint.x = Mth.lerp(in, actorStart.x, s.actorPoint.x);
         s.actorPoint.z = Mth.lerp(in, actorStart.z, s.actorPoint.z);
         s.actorYaw = Mth.rotLerp(in, actorYaw, s.actorYaw);
      }

      s.actor.turn(s.actorYaw, s.actorPitch, s.actorRoll, 0.0F).anchor(s.actorAnchor, s.actorPoint);
      s.actorFwd = s.actor.pos.z;
      s.actorSide = -s.actor.pos.x;
      Vector3f along = s.victim.rot.transform(new Vector3f(0.0F, 1.0F, 0.0F));
      if (Math.abs(along.y) < 0.6F) {
         float sideways = Math.abs(s.victim.rot.transform(new Vector3f(1.0F, 0.0F, 0.0F)).y);
         float center = s.victim.axis(1.0556251F).y;
         s.victim.pos.y = s.victim.pos.y + Math.max(0.0F, 0.47F * sideways + 0.117F * (1.0F - sideways) - center);
      }

      float sinkFrom = Timeline.old(this.type, this.type.sceneEndStruck()) - 25.0F;
      if (t > sinkFrom) {
         s.victim.pos.y = s.victim.pos.y - 0.5F * smooth(phase(t, sinkFrom, sinkFrom + 25.0F));
      }

      return s;
   }

   @Override
   public final CoupDeGrace.State at(float t, int side, float victimYaw, Vector3f actorStart, float actorYaw) {
      return this.build(Timeline.old(this.type, t), side, victimYaw, actorStart, actorYaw);
   }

   @Override
   public final void poseActor(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
      this.poseActor(m, (Spectacle.Frame)s, age);
   }

   @Override
   public final void poseVictim(HumanoidModel<?> m, CoupDeGrace.State s, float age) {
      this.poseVictim(m, (Spectacle.Frame)s, age);
      Vector3f back = s.victim.rot.transform(new Vector3f(0.0F, 0.0F, 1.0F));
      if (back.y < -0.6F) {
         Rig.keepHeadUp(m.head, s.victim.scene(0.0F, 0.0F, 0.0F).y / 0.05859375F - 0.3F);
      }

      Vector3f up = s.victim.rot.transform(new Vector3f(0.0F, 1.0F, 0.0F));
      if (Math.abs(up.y) < 0.6F) {
         float onSide = 1.0F - smooth(Math.abs(back.y) / 0.75F);
         m.rightArm.zRot *= 1.0F - onSide;
         m.leftArm.zRot *= 1.0F - onSide;
      }
   }

   @Override
   public final CoupDeGrace.Shot shot(CoupDeGrace.State s) {
      return this.shot((Spectacle.Frame)s);
   }

   @Override
   public final void renderExtras(PoseStack ps, MultiBufferSource buffers, CoupDeGrace.State s, float age, Choreography.Extras extras) {
      if (s instanceof Spectacle.Frame f) {
         this.renderExtras(ps, buffers, f, age, extras);
      }
   }

   @Override
   public final void tick(ExecutionScenes.Scene s, int before, int now) {
      int from = (int)Math.floor(Timeline.old(this.type, before));
      int to = (int)Math.floor(Timeline.old(this.type, now));

      for (int t = Math.max(from + 1, to - 3); t <= to; t++) {
         this.tick(s, this.build(t, s.side, s.victimYaw, s.actorStart, s.actorYaw), t);
      }
   }

   @Override
   public Vector3f end(int side) {
      return at(this.endFwd(), this.endSide() * side, 0.0F);
   }

   @Override
   public Vector3f corpse(int side) {
      Spectacle.Frame last = this.build(Timeline.old(this.type, 118 + 10), side, 90.0F * side, new Vector3f(0.0F, 0.0F, this.reach()), 180.0F);
      return last.victim.axis(1.0556251F).setComponent(1, 0.15F);
   }

   static Quaternionf rotation(float yaw, float pitch, float roll, float twist) {
      return new Quaternionf()
         .rotationY((180.0F - yaw) * (float) (Math.PI / 180.0))
         .rotateX(pitch * (float) (Math.PI / 180.0))
         .rotateZ(roll * (float) (Math.PI / 180.0))
         .rotateY(twist * (float) (Math.PI / 180.0));
   }

   static void prone(CoupDeGrace.Body v, float t, float startYaw, int side) {
      float yaw = Mth.rotLerp(smooth(t / 8.0F), startYaw, 90.0F * side);
      float r = yaw * (float) (Math.PI / 180.0);
      v.turn(yaw, -90.0F, 0.0F, 0.0F).anchor(1.0556251F, new Vector3f(-Mth.sin(r) * 0.057F, 0.3F, Mth.cos(r) * 0.057F));
   }

   static void rollOver(CoupDeGrace.Body v, float k, int side, float fwd, float up) {
      v.turn(90.0F * side, -90.0F, 0.0F, -180.0F * side * k).anchor(1.0556251F, at(fwd, 0.057F * side * (1.0F - k), Mth.lerp(k, 0.3F, 0.15F) + up));
   }

   static void stand(HumanoidModel<?> m) {
      CoupDeGrace.stand(m);
   }

   static void bend(HumanoidModel<?> m, float lean) {
      CoupDeGrace.bend(m, lean);
   }

   static void kneel(HumanoidModel<?> m, float lean, float amount) {
      bend(m, lean);
      m.rightLeg.xRot = Mth.lerp(amount, m.rightLeg.xRot, 1.2F);
      m.leftLeg.xRot = Mth.lerp(amount, m.leftLeg.xRot, 1.2F);
      m.rightLeg.yRot = 0.08F * amount;
      m.leftLeg.yRot = -0.08F * amount;
      m.rightLeg.zRot = Mth.lerp(amount, m.rightLeg.zRot, 0.1F);
      m.leftLeg.zRot = Mth.lerp(amount, m.leftLeg.zRot, -0.1F);
   }

   static void lyingPose(HumanoidModel<?> m) {
      m.rightArm.xRot = -3.05F;
      m.leftArm.xRot = -3.05F;
      m.rightArm.zRot = -0.35F;
      m.leftArm.zRot = 0.35F;
      m.rightLeg.xRot = 0.04F;
      m.leftLeg.xRot = 0.04F;
      m.rightLeg.zRot = 0.07F;
      m.leftLeg.zRot = -0.07F;
      m.head.xRot = -0.3F;
   }

   static void deadPose(HumanoidModel<?> m, float headTurn) {
      m.rightArm.xRot = -0.1F;
      m.leftArm.xRot = -0.1F;
      m.rightArm.zRot = 1.2F;
      m.leftArm.zRot = -1.25F;
      m.rightLeg.xRot = 0.0F;
      m.leftLeg.xRot = 0.0F;
      m.rightLeg.zRot = 0.16F;
      m.leftLeg.zRot = -0.16F;
      m.head.xRot = 0.12F;
      m.head.yRot = headTurn;
   }

   static void copyLayers(PlayerModel<?> m) {
      m.hat.copyFrom(m.head);
      m.jacket.copyFrom(m.body);
      m.rightSleeve.copyFrom(m.rightArm);
      m.leftSleeve.copyFrom(m.leftArm);
      m.rightPants.copyFrom(m.rightLeg);
      m.leftPants.copyFrom(m.leftLeg);
   }

   static void flail(HumanoidModel<?> m, float age, float amount) {
      float w = age * 0.9F;
      m.rightArm.xRot = Mth.lerp(amount, m.rightArm.xRot, -1.6F + 0.9F * Mth.sin(w));
      m.leftArm.xRot = Mth.lerp(amount, m.leftArm.xRot, -1.6F + 0.9F * Mth.sin(w + 2.1F));
      m.rightArm.zRot = Mth.lerp(amount, m.rightArm.zRot, 1.0F + 0.35F * Mth.sin(w * 1.3F));
      m.leftArm.zRot = Mth.lerp(amount, m.leftArm.zRot, -1.0F - 0.35F * Mth.sin(w * 1.3F + 1.0F));
      m.rightLeg.xRot = Mth.lerp(amount, m.rightLeg.xRot, 0.5F * Mth.sin(w * 1.1F));
      m.leftLeg.xRot = Mth.lerp(amount, m.leftLeg.xRot, 0.5F * Mth.sin(w * 1.1F + 2.6F));
      m.rightLeg.zRot = Mth.lerp(amount, m.rightLeg.zRot, 0.2F);
      m.leftLeg.zRot = Mth.lerp(amount, m.leftLeg.zRot, -0.2F);
   }

   static void walk(HumanoidModel<?> m, float phase, float amount, float dir) {
      if (!(amount <= 0.0F)) {
         float s = Mth.sin(phase) * dir;
         m.rightLeg.xRot = Mth.lerp(amount, m.rightLeg.xRot, 0.6F * s);
         m.leftLeg.xRot = Mth.lerp(amount, m.leftLeg.xRot, -0.6F * s);
         m.rightArm.xRot = Mth.lerp(amount, m.rightArm.xRot, -0.4F * s);
         m.leftArm.xRot = Mth.lerp(amount, m.leftArm.xRot, 0.4F * s);
         m.rightArm.zRot = Mth.lerp(amount, m.rightArm.zRot, 0.06F);
         m.leftArm.zRot = Mth.lerp(amount, m.leftArm.zRot, -0.06F);
      }
   }

   static void hold(ModelPart arm, Vector3f target, float amount) {
      if (amount > 0.0F) {
         CoupDeGrace.hold(arm, target, amount);
      }
   }

   static void look(ModelPart head, Vector3f target, float maxYaw, float maxPitch) {
      CoupDeGrace.look(head, target, maxYaw, maxPitch);
   }

   static Vector3f at(float fwd, float side, float up) {
      return CoupDeGrace.at(fwd, side, up);
   }

   static float phase(float t, float from, float to) {
      return CoupDeGrace.phase(t, from, to);
   }

   static float smooth(float x) {
      return CoupDeGrace.smooth(x);
   }

   static float out(float x, float power) {
      return 1.0F - (float)Math.pow(1.0F - Mth.clamp(x, 0.0F, 1.0F), power);
   }

   static float in(float x, float power) {
      return (float)Math.pow(Mth.clamp(x, 0.0F, 1.0F), power);
   }

   static float window(float t, float from, float to, float edge) {
      return smooth(phase(t, from, from + edge)) * (1.0F - smooth(phase(t, to - edge, to)));
   }

   static final class Frame extends CoupDeGrace.State {
      @Nullable
      CoupDeGrace.Body soul;
      float soulAlpha;
      float soulScale = 1.0F;
      final Vector3f actorPoint = new Vector3f();
      float actorAnchor;
      float actorYaw = 180.0F;
      float actorPitch;
      float actorRoll;
      float sceneVictimYaw;
      final Vector3f sceneActorStart = new Vector3f();
      float sceneActorYaw;

      Frame(float t, int side) {
         super(t, side);
      }

      Vector3f actorChest() {
         return this.actor.axis(1.1F);
      }

      Vector3f victimChest() {
         return this.victim.axis(1.1728126F);
      }
   }
}
