package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ExecutionLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
   static final ResourceLocation GASH = new ResourceLocation("reanimation", "textures/fx/gash.png");

   ExecutionLayer(RenderLayerParent<T, M> parent) {
      super(parent);
   }

   public void render(
      PoseStack ps,
      MultiBufferSource buffers,
      int light,
      T entity,
      float limbSwing,
      float limbAmount,
      float partialTick,
      float age,
      float headYaw,
      float headPitch
   ) {
      ExecutionScenes.Scene s = ExecutionScenes.ofActor(entity);
      boolean actor = s != null;
      if (s == null) {
         s = ExecutionScenes.ofVictim(entity);
      }

      if (s != null && s.state(partialTick) instanceof Script.Frame f) {
         HumanoidModel var18 = (HumanoidModel)this.getParentModel();
         if (!actor) {
            for (Script.Wound w : f.wounds) {
               wound(ps, buffers, light, var18, w);
            }
         }

         Knife k = f.knife;
         boolean mine = k.holder == Knife.Holder.HAND && actor || k.holder == Knife.Holder.VICTIM && !actor;
         if (mine) {
            ItemStack stack = Weapons.of(s);
            if (!stack.isEmpty()) {
               ps.pushPose();
               if (k.holder == Knife.Holder.HAND) {
                  var18.translateToHand(HumanoidArm.RIGHT, ps);
                  ps.mulPoseMatrix(Knife.heldMatrix(k.blade, k.spin, k.wrist, k.scale, k.shift));
               } else {
                  k.part.of(var18).translateAndRotate(ps);
                  ps.mulPoseMatrix(k.matrix);
               }

               Weapons.render(s, k, stack, entity, ps, buffers, light, s.time(partialTick));
               ps.popPose();
            }
         }
      }
   }

   private static void wound(PoseStack ps, MultiBufferSource buffers, int light, HumanoidModel<?> model, Script.Wound w) {
      if (!(w.progress() <= 0.01F)) {
         ps.pushPose();
         w.part().of(model).translateAndRotate(ps);
         Matrix4f pose = ps.last().pose();
         Matrix3f normal = ps.last().normal();
         Vector3f from = new Vector3f(w.from()).div(16.0F);
         Vector3f to = new Vector3f(from).lerp(new Vector3f(w.to()).div(16.0F), w.progress());
         Vector3f n = new Vector3f(w.normal()).normalize();
         Vector3f along = new Vector3f(to).sub(from);
         Vector3f across = new Vector3f(along).cross(n).normalize().mul(w.width() / 32.0F);
         Vector3f lift = new Vector3f(n).mul(0.06375F);
         Vector3f a = new Vector3f(from).add(lift).sub(across);
         Vector3f b = new Vector3f(from).add(lift).add(across);
         Vector3f c = new Vector3f(to).add(lift).add(across);
         Vector3f d = new Vector3f(to).add(lift).sub(across);
         float v = w.progress();
         VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GASH));
         vertex(vc, pose, normal, a, 0.0F, 0.0F, light, n);
         vertex(vc, pose, normal, b, 1.0F, 0.0F, light, n);
         vertex(vc, pose, normal, c, 1.0F, v, light, n);
         vertex(vc, pose, normal, d, 0.0F, v, light, n);
         ps.popPose();
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vector3f p, float u, float v, int light, Vector3f n) {
      vc.vertex(pose, p.x, p.y, p.z)
         .color(1.0F, 1.0F, 1.0F, 1.0F)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(normal, n.x, n.y, n.z)
         .endVertex();
   }
}
