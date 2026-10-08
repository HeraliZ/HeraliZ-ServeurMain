package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FirstPersonRevive {
   private static final double KNEEL = 0.3;
   private static float blend;
   private static float blendO;

   private FirstPersonRevive() {
   }

   static void tick() {
      blendO = blend;
      boolean kneeling = (ClientDowned.localFlags() & 16) != 0;
      blend = kneeling ? Math.min(1.0F, blend + 0.15F) : Math.max(0.0F, blend - 0.2F);
   }

   private static boolean active(Minecraft mc) {
      return mc.player != null && mc.options.getCameraType().isFirstPerson();
   }

   static void onCameraAngles(ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (active(mc) && CameraAccess.available()) {
         float pt = (float)event.getPartialTick();
         float k = Mth.lerp(pt, blendO, blend);
         if (!(k <= 0.0F)) {
            ClientDowned.Info me = ClientDowned.local();
            float push = 0.0F;
            if (me != null && me.has(16)) {
               float p = me.reviveProgress(pt);
               if (p > 0.1F && p < 0.86F) {
                  push = Poses.pump(me.revive + pt);
               }
            }

            float s = k * k * (3.0F - 2.0F * k);
            Vec3 look = Vec3.directionFromRotation(0.0F, event.getYaw());
            Vec3 pos = event.getCamera().getPosition().add(0.0, -0.3 * s - 0.09 * push, 0.0).add(look.scale(0.12 * push));
            CameraAccess.move(event.getCamera(), pos);
            event.setPitch(event.getPitch() + 5.0F * push);
         }
      }
   }

   static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ClientDowned.Info me = ClientDowned.local();
      if (me != null && me.has(16) && active(mc)) {
         event.setCanceled(true);
         if (event.getHand() == InteractionHand.MAIN_HAND) {
            AbstractClientPlayer player = mc.player;
            if (mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
               float var17 = event.getPartialTick();
               float p = me.reviveProgress(var17);
               float reach = smooth(p / 0.1F);
               float push = p > 0.1F && p < 0.86F ? Poses.pump(me.revive + var17) : 0.0F;
               float pull = p >= 0.86F ? smooth((p - 0.86F) / 0.13999999F) : 0.0F;
               PoseStack ps = event.getPoseStack();
               MultiBufferSource buffers = event.getMultiBufferSource();
               int light = event.getPackedLight();

               for (int side = 1; side >= -1; side -= 2) {
                  Vector3f shoulder = new Vector3f(side * 0.5F, -0.5F - 0.05F * push, -0.4F);
                  Vector3f hand = new Vector3f(side * 0.07F, Mth.lerp(reach, -0.9F, -0.2F) - 0.09F * push, -0.95F + 0.03F * push);
                  if (pull > 0.0F) {
                     if (side > 0) {
                        hand.lerp(new Vector3f(0.1F, -0.05F, -1.05F), pull);
                     } else {
                        hand.lerp(new Vector3f(-0.9F, -1.0F, -0.5F), pull);
                     }
                  }

                  Vector3f dir = new Vector3f(hand).sub(shoulder).normalize();
                  ps.pushPose();
                  ps.translate(shoulder.x, shoulder.y, shoulder.z);
                  ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z));
                  ps.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
                  ps.translate(side * 5.0F / 16.0F, -0.125F, 0.0F);
                  if (side > 0) {
                     renderer.renderRightHand(ps, buffers, light, player);
                  } else {
                     renderer.renderLeftHand(ps, buffers, light, player);
                  }

                  ps.popPose();
               }
            }
         }
      }
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }
}
