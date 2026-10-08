package fr.heralisz.mere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mere.HeraliszMere;
import fr.heralisz.mere.entity.RuinMother;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Rendu de la Mère des Ruines : yeux pâles lumineux, sac crevé en phase 2 (os « sac_torn »), yeux rouges et épines d'os en phase 3
 * (os « rage_p3 », lueur _glow3) ; invisible tant qu'elle dort dans son cocon ; à sa mort, le cocon tissé par la couvée, qui
 * tourne lentement au bout de sa soie pendant qu'on la hisse.
 */
public class RuinMotherRenderer extends GeoEntityRenderer<RuinMother> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszMere.MODID, "textures/entity/ruin_mother.png");
   private static final ResourceLocation GLOW = new ResourceLocation(HeraliszMere.MODID, "textures/entity/ruin_mother_glow.png");
   private static final ResourceLocation GLOW3 = new ResourceLocation(HeraliszMere.MODID, "textures/entity/ruin_mother_glow3.png");
   /** Cocon de la mort (centre et rayons en px, repère de l'os « body ») : il enferme tout le corps, pattes repliées comprises. */
   public static final float[] COCOON = {0, 26, 26, 40, 45, 46};
   // (le fuseau recule : la tête de la pendue et ses mains dépassent de la soie, à l'avant)
   /** Os qui luisent : le reste n'est pas redessiné par la passe de lueur. */
   private static final java.util.Set<String> GLOW_BONES = java.util.Set.of("cephalothorax", "abdomen", "hanging_head", "hanging_torso", "sac_torn",
      "rage_p3");

   public static class Model extends GeoModel<RuinMother> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszMere.MODID, "geo/entity/ruin_mother.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszMere.MODID, "animations/entity/ruin_mother.animation.json");

      @Override
      public ResourceLocation getModelResource(RuinMother e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(RuinMother e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(RuinMother e) {
         return ANIM;
      }
   }

   private Matrix4f bodyPose;
   private Matrix3f bodyNormal;
   private boolean glowPass;

   public RuinMotherRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(RuinMother.RS);
      this.shadowRadius = 2.6F;
   }

   @Override
   public RenderType getRenderType(RuinMother e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   public int getPackedOverlay(RuinMother e, float u, float partial) {
      return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && !e.isDeadOrDying()));
   }

   @Override
   public int getPackedOverlay(RuinMother e, float u) {
      return getPackedOverlay(e, u, 0);
   }

   @Override
   protected float getDeathMaxRotation(RuinMother e) {
      return 0F;
   }

   @Override
   public boolean shouldRender(RuinMother e, Frustum f, double x, double y, double z) {
      if (e.dormant()) return false;                                             // elle dort dans le cocon
      return e.distanceToSqr(x, y, z) < 160 * 160;
   }

   @Override
   public void renderRecursively(PoseStack ps, RuinMother e, GeoBone bone, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                                 float partial, int light, int overlay, float r, float g, float b, float a) {
      super.renderRecursively(ps, e, bone, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
   }

   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      if (glowPass && !GLOW_BONES.contains(bone.getName())) return;
      if (!glowPass && bone.getName().equals("body")) {
         bodyPose = new Matrix4f(ps.last().pose());
         bodyNormal = new Matrix3f(ps.last().normal());
      }
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   @Override
   protected void applyRotations(RuinMother e, PoseStack ps, float age, float yaw, float partial) {
      // portée sur le dos par la couvée, puis dressée contre le pilier : orientation libre
      org.joml.Quaternionf q = e.orient(partial);                               // aussi : accrochée à un fût (attaque des piliers)
      if (q != null) {
         ps.mulPose(q);
         return;
      }
      float c = Mth.lerp(partial, e.tiltO, e.tilt);
      if (Math.abs(c) < 0.01F || e.isDeadOrDying()) {
         super.applyRotations(e, ps, age, yaw, partial);
         return;
      }
      net.minecraft.world.phys.Vec3 f = net.minecraft.world.phys.Vec3.directionFromRotation(0, Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot));
      net.minecraft.world.phys.Vec3 up = new net.minecraft.world.phys.Vec3(0, Math.cos(c), 0).subtract(f.scale(Math.sin(c)));
      net.minecraft.world.phys.Vec3 fw = f.scale(Math.cos(c)).add(0, Math.sin(c), 0);
      ps.mulPose(fr.heralisz.mere.entity.Spiderling.basis(up, fw));
   }

   @Override
   public void render(RuinMother e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      super.render(e, yaw, partial, ps, buf, light);
      net.minecraft.world.phys.Vec3 axis = e.isDeadOrDying() ? e.pillarAxis() : null;
      if (axis != null) {
         float w = Mth.clamp((e.deathTime + partial - RuinMother.WRAP0) / (float) (RuinMother.WRAP1 - RuinMother.WRAP0), 0, 1);
         PillarWrap.draw(ps, buf, e.getPosition(partial), axis, e.pillarDir(), w, e.tickCount + partial, light);
      }
   }

   @Override
   public void preRender(PoseStack ps, RuinMother e, BakedGeoModel model, MultiBufferSource buf, VertexConsumer vc, boolean reRender, float partial,
                         int light, int overlay, float r, float g, float b, float a) {
      super.preRender(ps, e, model, buf, vc, reRender, partial, light, overlay, r, g, b, a);
      model.getBone("sac_torn").ifPresent(bn -> bn.setHidden(e.phase() < 2));
      model.getBone("rage_p3").ifPresent(bn -> bn.setHidden(e.phase() < 3));
   }

   @Override
   public void actuallyRender(PoseStack ps, RuinMother e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      if (!reRender) bodyPose = null;
      super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
      if (reRender) return;
      float t = e.tickCount + partial;
      float k = e.phase() == 3 ? 0.85F + 0.15F * Mth.sin(t * 0.3F) : 0.6F + 0.1F * Mth.sin(t * 0.07F);
      if (e.isDeadOrDying()) k *= 1 - Mth.clamp((e.deathTime + partial - 20) / 40F, 0, 1);
      if (k > 0.01F) {
         RenderType glow = RenderType.eyes(e.phase() == 3 ? GLOW3 : GLOW);
         glowPass = true;
         try {
            super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k, k, k, 1);
         } finally {
            glowPass = false;
         }
      }
      float c = e.cocoon(partial);
      if (c > 0) Cocoon.draw(ps, buf, bodyPose, bodyNormal, COCOON[0], COCOON[1], COCOON[2], COCOON[3], COCOON[4], COCOON[5], c, light);
   }
}
