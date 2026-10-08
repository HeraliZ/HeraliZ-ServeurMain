package fr.heraliz.reanimation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Puppet {
   private Puppet() {
   }

   static boolean hurts(Puppet.Move move) {
      return move == Puppet.Move.SLAP || move == Puppet.Move.PUNCH || move == Puppet.Move.SLAP_BOTH;
   }

   static void pose(HumanoidModel<?> m, Puppet.Move move, float k, float u, float age) {
      if (!(k <= 0.0F)) {
         float hit = CoupDeGrace.smooth((u - 0.35F) / 0.15F);
         float snap = Keys.jolt(u * 8.0F, 4.0F, 4.0F, 1.6F);
         switch (move) {
            case SLAP:
               Finisher.pose(m.rightArm, k * (1.0F - hit), -0.4F, 0.0F, 1.3F);
               if (hit > 0.0F) {
                  Rig.aim(m.rightArm, Rig.HAND, new Vector3f(1.5F, -4.0F, -4.8F), 0.0F, k * hit);
               }

               Finisher.pose(m.head, k * hit, 0.15F, -0.75F, 0.15F);
               m.head.yRot -= 0.25F * snap * k;
               break;
            case PUNCH:
               Finisher.pose(m.leftArm, k * (1.0F - hit), -0.9F, 0.0F, -0.7F);
               if (hit > 0.0F) {
                  Rig.aim(m.leftArm, Rig.LEFT_HAND, new Vector3f(0.0F, -4.2F, -4.6F), 0.0F, k * hit);
               }

               Finisher.pose(m.head, k * hit, -0.6F, 0.1F, 0.0F);
               m.head.xRot -= 0.2F * snap * k;
               break;
            case SLAP_BOTH:
               float claps = Mth.sin(age * 2.2F);
               Rig.aim(m.rightArm, Rig.HAND, new Vector3f(-3.6F - 1.5F * claps, -4.0F, -3.5F), 0.0F, k);
               Rig.aim(m.leftArm, Rig.LEFT_HAND, new Vector3f(3.6F + 1.5F * claps, -4.0F, -3.5F), 0.0F, k);
               Finisher.pose(m.head, k, 0.1F, 0.45F * Mth.sin(age * 2.2F + 1.0F), 0.0F);
               break;
            case BOW:
               Finisher.pose(m.rightArm, k, -0.9F, 0.0F, -0.9F);
               Finisher.pose(m.leftArm, k, -0.2F, 0.0F, -0.9F);
               Finisher.pose(m.head, k, 0.7F, 0.0F, 0.0F);
               break;
            case KICK:
               Finisher.pose(m.rightLeg, k, -1.5F, 0.0F, 0.0F);
               Finisher.pose(m.rightArm, k, -0.3F, 0.0F, 1.0F);
               Finisher.pose(m.leftArm, k, -0.3F, 0.0F, -1.0F);
               break;
            case JACK:
               float beat = Math.abs(Mth.sin((float) Math.PI * u * 2.0F));
               Finisher.pose(m.rightArm, k, -0.2F, 0.0F, 2.6F * beat);
               Finisher.pose(m.leftArm, k, -0.2F, 0.0F, -2.6F * beat);
               Finisher.pose(m.rightLeg, k, 0.0F, 0.0F, 0.4F * beat);
               Finisher.pose(m.leftLeg, k, 0.0F, 0.0F, -0.4F * beat);
               Finisher.pose(m.head, k, -0.2F, 0.0F, 0.0F);
               break;
            case TWIRL:
               Finisher.pose(m.rightArm, k, -2.9F, 0.0F, 0.2F);
               Finisher.pose(m.leftArm, k, -2.9F, 0.0F, -0.2F);
               Finisher.pose(m.head, k, -0.3F, 0.0F, 0.0F);
               break;
            case MARCH:
               float step = Mth.sin(u * (float) (Math.PI * 2));
               Finisher.pose(m.rightLeg, k, -1.0F * Math.max(0.0F, step), 0.0F, 0.0F);
               Finisher.pose(m.leftLeg, k, -1.0F * Math.max(0.0F, -step), 0.0F, 0.0F);
               Finisher.pose(m.rightArm, k, 0.6F * step, 0.0F, 0.1F);
               Finisher.pose(m.leftArm, k, -0.6F * step, 0.0F, -0.1F);
               break;
            case CROSS:
               Finisher.spread(m, k);
               Finisher.pose(m.rightLeg, k, 0.0F, 0.0F, 0.05F);
               Finisher.pose(m.leftLeg, k, 0.0F, 0.0F, -0.05F);
         }
      }
   }

   static void string(Draw d, Vector3f a, Vector3f b, float taut, float pluck, int rgb, float alpha, float age, int seed) {
      if (!(alpha <= 0.01F)) {
         List<Vector3f> pts = new ArrayList<>();
         Vector3f dir = new Vector3f(b).sub(a);
         Vector3f side = new Vector3f(dir).cross(0.0F, 1.0F, 0.0F);
         if (side.lengthSquared() < 1.0E-6F) {
            side.set(1.0F, 0.0F, 0.0F);
         }

         side.normalize();
         int n = 14;

         for (int k = 0; k <= n; k++) {
            float u = (float)k / n;
            float bell = 4.0F * u * (1.0F - u);
            Vector3f p = new Vector3f(a).lerp(b, u);
            p.y -= 0.14F * (1.0F - taut) * bell;
            p.add(new Vector3f(side).mul(0.05F * pluck * bell * Mth.sin(age * 3.0F + u * 9.0F)));
            pts.add(p);
         }

         float r = Finisher.r(rgb);
         float g = Finisher.g(rgb);
         float bl = Finisher.b(rgb);
         float lit = Math.min(1.0F, alpha * (0.75F + 0.25F * taut + 0.5F * pluck));
         d.ribbon(FxTex.THREAD, pts, 0.14F, 1.0F, 0.0F, r, g, bl, 0.35F * lit, 0.35F * lit, Draw.Blend.GLOW);
         d.ribbon(FxTex.THREAD, pts, 0.04F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, lit, lit, Draw.Blend.GLOW);
         float run = (age * 0.045F + seed * 0.37F) % 1.0F;
         Vector3f bead = pts.get(Math.min(n, (int)(run * n)));
         d.glow(bead, 0.22F, r, g, bl, 0.9F * alpha);
         d.glow(b, 0.3F, r, g, bl, alpha);
         d.sprite(Sprite.RING, b, 0.18F, age * 0.2F, 1.0F, 1.0F, 1.0F, 0.6F * alpha, Draw.Blend.GLOW);
      }
   }

   static Vector3f[] bar(Draw d, Vector3f hand, float yaw, float tiltSide, float tiltFwd, float alpha) {
      Matrix4f m = new Matrix4f()
         .translate(hand)
         .translate(0.0F, 0.06F, 0.0F)
         .rotate(new Quaternionf().rotationY((180.0F - yaw) * (float) (Math.PI / 180.0)))
         .rotateZ(tiltSide)
         .rotateX(tiltFwd);
      Vector3f[] ends = new Vector3f[]{
         m.transformPosition(new Vector3f(0.42F, 0.0F, 0.0F)),
         m.transformPosition(new Vector3f(-0.42F, 0.0F, 0.0F)),
         m.transformPosition(new Vector3f(0.0F, 0.0F, -0.3F)),
         m.transformPosition(new Vector3f(0.0F, 0.0F, 0.3F)),
         m.transformPosition(new Vector3f(0.0F, 0.0F, 0.0F))
      };
      if (d != null && alpha > 0.01F) {
         Draw.Blend blend = alpha < 1.0F ? Draw.Blend.CLEAR : Draw.Blend.SOLID;
         d.box(FxTex.BARK, new Matrix4f(m).scale(0.9F, 0.05F, 0.05F), 1.0F, 1.0F, 1.0F, alpha, blend);
         d.box(FxTex.BARK, new Matrix4f(m).scale(0.05F, 0.05F, 0.65F), 1.0F, 1.0F, 1.0F, alpha, blend);

         for (Vector3f e : ends) {
            d.glow(e, 0.12F, 1.0F, 0.9F, 0.6F, 0.8F * alpha);
         }
      }

      return ends;
   }

   enum Move {
      SLAP,
      PUNCH,
      SLAP_BOTH,
      BOW,
      KICK,
      JACK,
      TWIRL,
      MARCH,
      CROSS;
   }
}
