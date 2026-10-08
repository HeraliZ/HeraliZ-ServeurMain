package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.entity.BossRemains;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Dépouille d'un boss : même modèle que le boss, figé dans sa pose de mort, texture « morte » (Sujet N-07 : statue de
 * trinitite) ; pendant l'effritement final elle s'enfonce dans le sol.
 */
public class BossRemainsRenderer extends GeoEntityRenderer<BossRemains> {
   private static final int N = BossRemains.Kind.values().length;
   private static final ResourceLocation[] GEO = new ResourceLocation[N], ANIM = new ResourceLocation[N], TEX = new ResourceLocation[N],
      GLOW = new ResourceLocation[N];
   private static final float[] SCALE = new float[N];

   static {
      for (BossRemains.Kind k : BossRemains.Kind.values()) {
         GEO[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "geo/entity/" + k.id + ".geo.json");
         ANIM[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "animations/entity/" + k.id + ".animation.json");
         TEX[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/" + k.id + "_petrified.png");
         GLOW[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/" + k.id + "_petrified_glow.png");
         SCALE[k.ordinal()] = k == BossRemains.Kind.SUJET_N07 ? fr.heralisz.n07.entity.SujetN07.RS : 1F;
      }
   }

   public static class Model extends GeoModel<BossRemains> {
      @Override
      public ResourceLocation getModelResource(BossRemains e) {
         return GEO[e.kind().ordinal()];
      }

      @Override
      public ResourceLocation getTextureResource(BossRemains e) {
         return TEX[e.kind().ordinal()];
      }

      @Override
      public ResourceLocation getAnimationResource(BossRemains e) {
         return ANIM[e.kind().ordinal()];
      }

      @Override
      public void setCustomAnimations(BossRemains e, long id, AnimationState<BossRemains> state) {
         super.setCustomAnimations(e, id, state);
         if (e.kind() == BossRemains.Kind.SUJET_N07) {
            // la statue garde l'état de sa dernière phase (en pratique la 3 : gorge à nu, tuyau dans le cœur)
            int ph = e.variant();
            hide("mask", ph == 3);
            hide("maw", ph != 3);
            hide("core", ph != 3);
            hide("hose_chest", ph != 3);
            hide("hose_neck", ph == 3);
            for (String b : new String[] {"spikes", "growth_r", "growth_l", "spill", "spill_hips", "spill_arm_r", "spill_arm_l", "spill_fore_r", "spill_fore_l"})
               hide(b, ph == 1);
         }
      }

      private void hide(String bone, boolean h) {
         getBone(bone).ifPresent(b -> {
            b.setHidden(h);
            b.setChildrenHidden(h);
         });
      }
   }

   public BossRemainsRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.shadowRadius = 2F;
   }

   @Override
   public void render(BossRemains e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      float s = SCALE[e.kind().ordinal()];
      this.scaleWidth = s;
      this.scaleHeight = s;
      super.render(e, yaw, partial, ps, buf, light);
   }

   /** La dépouille garde l'orientation du boss (entité non vivante : GeckoLib ne lit pas de rotation du corps). */
   @Override
   protected void applyRotations(BossRemains e, PoseStack ps, float age, float yaw, float partial) {
      super.applyRotations(e, ps, age, e.getYRot(), partial);
   }

   @Override
   public RenderType getRenderType(BossRemains e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   public boolean shouldRender(BossRemains e, Frustum f, double x, double y, double z) {
      return e.distanceToSqr(x, y, z) < 160 * 160 && f.isVisible(e.getBoundingBoxForCulling());
   }

   @Override
   public void actuallyRender(PoseStack ps, BossRemains e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      if (reRender) {
         super.actuallyRender(ps, e, model, rt, buf, vc, true, partial, light, overlay, r, g, b, a);
         return;
      }
      float cr = e.crumble(partial);
      ps.pushPose();
      // effritement : il s'enfonce en tremblant
      ps.translate(cr > 0 ? Mth.sin((e.tickCount + partial) * 2.1F) * 0.03F * cr : 0, -cr * cr * 2.4F, 0);
      super.actuallyRender(ps, e, model, rt, buf, vc, false, partial, light, overlay, r, g, b, a);
      float ember = (0.38F + 0.14F * Mth.sin((e.tickCount + partial) * 0.07F)) * (1 - cr);
      if (ember > 0.01F) {
         RenderType eg = RenderType.eyes(GLOW[e.kind().ordinal()]);
         super.actuallyRender(ps, e, model, eg, buf, buf.getBuffer(eg), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            ember, ember, ember, 1);
      }
      ps.popPose();
   }
}
