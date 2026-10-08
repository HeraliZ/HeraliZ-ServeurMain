package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.client.vfx.VfxDraw;
import fr.heralisz.mere.client.vfx.VfxRenderTypes;
import fr.heralisz.mere.entity.Spiderling;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Rejeton de la Mère des Ruines : modèle velu, yeux qui luisent ; pendu à son fil, la soie monte jusqu'à son point d'attache. */
public class SpiderlingRenderer extends GeoEntityRenderer<Spiderling> {
   private static final ResourceLocation SILK = new ResourceLocation(HeraliszMere.MODID, "textures/vfx/beam_core.png");

   public static class Model extends GeoModel<Spiderling> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszMere.MODID, "geo/entity/spiderling.geo.json");
      private static final ResourceLocation TEX = new ResourceLocation(HeraliszMere.MODID, "textures/entity/spiderling.png");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszMere.MODID, "animations/entity/spiderling.animation.json");

      @Override
      public ResourceLocation getModelResource(Spiderling e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(Spiderling e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(Spiderling e) {
         return ANIM;
      }
   }

   public SpiderlingRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      addRenderLayer(new AutoGlowingGeoLayer<>(this));
      this.shadowRadius = 0.5F;
   }

   @Override
   protected void applyRotations(Spiderling e, PoseStack ps, float age, float yaw, float partial) {
      if (e.mode() == Spiderling.CARRY) {
         ps.mulPose(e.orient(partial));
         return;
      }
      float c = net.minecraft.util.Mth.lerp(partial, e.climbO, e.climb);
      if (e.mode() != Spiderling.FREE || Math.abs(c) < 0.02F || e.isDeadOrDying()) {
         super.applyRotations(e, ps, age, yaw, partial);
         return;
      }
      // contre une paroi : le dos s'écarte du mur, la tête monte (même repère que les marionnettes de la Mère)
      Vec3 f = Vec3.directionFromRotation(0, net.minecraft.util.Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot));
      double a = c * Math.PI / 2;
      Vec3 up = new Vec3(0, Math.cos(a), 0).subtract(f.scale(Math.sin(a)));
      Vec3 fw = f.scale(Math.cos(a)).add(0, Math.sin(a), 0);
      ps.translate(0, Math.max(0, c) * 0.35, 0);
      ps.mulPose(Spiderling.basis(up, fw));
   }

   @Override
   public void render(Spiderling e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      super.render(e, yaw, partial, ps, buf, light);
      if (e.carriesEgg() && e.mode() == Spiderling.CARRY) {                      // l'Œuf de la Couvée, entre ses crochets
         ps.pushPose();
         ps.mulPose(e.orient(partial));
         ps.translate(0, 0.45, -0.75);
         ps.scale(0.9F, 0.9F, 0.9F);
         net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(HeraliszMere.BROOD_EGG.get()),
            net.minecraft.world.item.ItemDisplayContext.GROUND, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
         ps.popPose();
      }
      if (e.mode() != Spiderling.HANG) return;
      Vec3 p = e.getPosition(partial);
      double top = e.threadY() - p.y;
      if (top <= 0.8) return;
      Matrix4f m = ps.last().pose();
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(SILK));
      Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(p);
      Vec3 a = new Vec3(0, 0.7, 0), b = new Vec3(0, top, 0);
      Vec3 side = new Vec3(cam.z, 0, -cam.x);
      side = side.lengthSqr() < 1e-4 ? new Vec3(0.02, 0, 0) : side.normalize().scale(0.025);
      int col = VfxDraw.argb(0xE8E4D8, 0.8F);
      VfxDraw.v(vc, m, a.x - side.x, a.y, a.z - side.z, 0, 0, col);
      VfxDraw.v(vc, m, a.x + side.x, a.y, a.z + side.z, 1, 0, col);
      VfxDraw.v(vc, m, b.x + side.x, b.y, b.z + side.z, 1, 1, col);
      VfxDraw.v(vc, m, b.x - side.x, b.y, b.z - side.z, 0, 1, col);
   }
}
