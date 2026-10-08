package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Poses {
   public static final float REACH = 0.1F;
   public static final float PULL = 0.86F;
   public static final int BEAT = 12;
   private static final float KNEEL_LEGS = 1.2F;
   private static final double KNEEL_DROP = 0.32;
   private static final ArrayDeque<Boolean> RENDERING = new ArrayDeque<>();

   private Poses() {
   }

   public static float pump(float reviveTicks) {
      float c = reviveTicks % 12.0F / 12.0F;
      return c < 0.4F ? Mth.sin(c / 0.4F * (float) Math.PI) : 0.0F;
   }

   static boolean apply(HumanoidModel<?> m, LivingEntity e, float limbSwing, float limbAmount, float age) {
      m.head.x = 0.0F;
      m.head.z = 0.0F;
      m.head.zRot = 0.0F;
      m.body.x = 0.0F;
      m.body.z = 0.0F;
      m.body.zRot = 0.0F;
      m.rightLeg.x = -1.9F;
      m.leftLeg.x = 1.9F;
      if (e instanceof Player && RENDERING.isEmpty()) {
         return false;
      }

      if (ExecutionScenes.pose(m, e, age)) {
         return true;
      }

      ClientDowned.Info info = ClientDowned.get(e.getId());
      if (info == null) {
         return false;
      }

      float partial = age - e.tickCount;
      if (info.has(8)) {
         search(m, age);
         return true;
      }

      if (info.has(16)) {
         revive(m, info.revive + partial, info.reviveProgress(partial));
         return true;
      }

      if (info.has(1)) {
         lying(m, limbSwing, limbAmount);
         if (info.has(4)) {
            revived(m, info.reviveProgress(partial));
         }

         return true;
      } else {
         return false;
      }
   }

   private static void lying(HumanoidModel<?> m, float limbSwing, float limbAmount) {
      float moving = Mth.clamp(limbAmount * 2.5F, 0.0F, 1.0F);
      float stroke = Mth.sin(limbSwing * 0.35F);
      float kick = moving * stroke * 0.28F;
      m.rightLeg.xRot = 0.04F;
      m.leftLeg.xRot = 0.04F;
      m.rightLeg.yRot = 0.0F;
      m.leftLeg.yRot = 0.0F;
      m.rightLeg.zRot = 0.07F + Math.max(0.0F, kick);
      m.leftLeg.zRot = -0.07F - Math.max(0.0F, -kick);
      m.rightArm.xRot = -3.05F;
      m.leftArm.xRot = -3.05F;
      m.rightArm.yRot = 0.0F;
      m.leftArm.yRot = 0.0F;
      m.rightArm.zRot = -0.35F + 0.35F * moving * stroke;
      m.leftArm.zRot = 0.35F + 0.35F * moving * stroke;
      m.head.xRot = Math.min(m.head.xRot, -0.3F);
   }

   private static void revived(HumanoidModel<?> m, float p) {
      if (p > 0.86F) {
         float k = smooth((p - 0.86F) / 0.13999999F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.6F);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.2F);
      }
   }

   private static void search(HumanoidModel<?> m, float age) {
      float t = age * 0.42F;
      kneel(m, 0.4F + 0.05F * Mth.sin(age * 0.13F));
      m.rightArm.xRot = -0.75F + 0.3F * Mth.sin(t);
      m.leftArm.xRot = -0.75F + 0.3F * Mth.sin(t + 2.3F);
      m.rightArm.yRot = -0.2F + 0.15F * Mth.sin(t * 0.5F);
      m.leftArm.yRot = 0.2F + 0.15F * Mth.sin(t * 0.5F + 1.1F);
      m.rightArm.zRot = -0.12F + 0.1F * Mth.cos(t);
      m.leftArm.zRot = 0.12F - 0.1F * Mth.cos(t + 2.3F);
      m.head.xRot = 0.55F + 0.06F * Mth.sin(age * 0.19F);
      m.head.yRot = 0.3F * Mth.sin(age * 0.06F);
      m.head.zRot = 0.0F;
   }

   private static void revive(HumanoidModel<?> m, float ticks, float p) {
      m.head.yRot = 0.0F;
      m.head.zRot = 0.0F;
      m.rightArm.yRot = 0.0F;
      m.leftArm.yRot = 0.0F;
      if (p < 0.1F) {
         float k = smooth(p / 0.1F);
         kneel(m, 0.1F + 0.35F * k);
         m.rightArm.xRot = Mth.lerp(k, -0.2F, -0.62F);
         m.leftArm.xRot = Mth.lerp(k, -0.2F, -0.62F);
         m.rightArm.zRot = -0.28F * k;
         m.leftArm.zRot = 0.28F * k;
         m.head.xRot = 0.45F + 0.2F * k;
      } else if (p < 0.86F) {
         float push = pump(ticks);
         kneel(m, 0.45F + 0.2F * push);
         m.rightArm.xRot = -0.62F + 0.22F * push;
         m.leftArm.xRot = -0.62F + 0.22F * push;
         m.rightArm.zRot = -0.28F;
         m.leftArm.zRot = 0.28F;
         m.head.xRot = 0.65F + 0.1F * push;
      } else {
         float k = smooth((p - 0.86F) / 0.13999999F);
         kneel(m, Mth.lerp(k, 0.45F, 0.05F));
         m.rightArm.xRot = Mth.lerp(k, -0.62F, -1.25F);
         m.rightArm.zRot = Mth.lerp(k, -0.28F, 0.0F);
         m.leftArm.xRot = Mth.lerp(k, -0.62F, -0.3F);
         m.leftArm.zRot = Mth.lerp(k, 0.28F, 0.15F);
         m.head.xRot = Mth.lerp(k, 0.65F, 0.3F);
      }
   }

   private static void kneel(HumanoidModel<?> m, float lean) {
      float c = Mth.cos(lean);
      float s = Mth.sin(lean);
      m.body.xRot = lean;
      m.body.y = 12.0F - 12.0F * c;
      m.body.z = -12.0F * s;
      m.head.y = m.body.y;
      m.head.z = m.body.z;
      m.rightArm.y = m.body.y + 2.0F * c;
      m.rightArm.z = m.body.z + 2.0F * s;
      m.leftArm.y = m.rightArm.y;
      m.leftArm.z = m.rightArm.z;
      m.rightLeg.y = 12.0F;
      m.rightLeg.z = 0.0F;
      m.leftLeg.y = 12.0F;
      m.leftLeg.z = 0.0F;
      m.rightLeg.xRot = 1.2F;
      m.leftLeg.xRot = 1.2F;
      m.rightLeg.yRot = 0.08F;
      m.leftLeg.yRot = -0.08F;
      m.rightLeg.zRot = 0.1F;
      m.leftLeg.zRot = -0.1F;
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   static boolean pushTransform(LivingEntity e, PoseStack poseStack, float partialTick) {
      if (ExecutionScenes.push(e, poseStack, partialTick)) {
         return true;
      } else {
         ClientDowned.Info info = ClientDowned.get(e.getId());
         if (info == null) {
            return false;
         } else if (info.has(8) || info.has(16)) {
            poseStack.pushPose();
            poseStack.translate(0.0, -0.32, 0.0);
            return true;
         } else if (!info.has(4)) {
            return false;
         } else {
            float p = info.reviveProgress(partialTick);
            if (!(p <= 0.1F) && !(p >= 0.86F)) {
               float push = pump(info.revive + partialTick);
               poseStack.pushPose();
               poseStack.translate(0.0, -0.035 * push, 0.0);
               poseStack.scale(1.0F + 0.03F * push, 1.0F - 0.05F * push, 1.0F + 0.03F * push);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   static void beginPlayer(LivingEntity e, PoseStack poseStack, float partialTick) {
      RENDERING.push(pushTransform(e, poseStack, partialTick));
   }

   static void endPlayer(PoseStack poseStack) {
      Boolean pushed = RENDERING.poll();
      if (pushed != null && pushed) {
         poseStack.popPose();
      }
   }
}
