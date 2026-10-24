package fr.heralisz.plague.client.sick;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.entity.PlagueBearer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu du Porteur de Peste. Phase 1 : masque et bonbonnes intactes. Phase 2 : bonbonnes éventrées, tumeurs, sac de peste.
 * Phase 3 : le masque arraché laisse voir le crâne nécrosé, des champignons luisent sur son dos. Les objets qu'il lance (grenade,
 * pustule) apparaissent dans sa main le temps du geste ; le crochet disparaît quand il vole au bout de sa chaîne.
 * Mort : la moisissure gagne le corps os par os depuis la poitrine, puis une colonie de champignons crève sa peau.
 */
public class PlagueBearerRenderer extends GeoEntityRenderer<PlagueBearer> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszPlague.MODID, "textures/entity/plague_bearer.png");
   private static final ResourceLocation GLOW1 = new ResourceLocation(HeraliszPlague.MODID, "textures/entity/plague_bearer_glow1.png");
   private static final ResourceLocation GLOW3 = new ResourceLocation(HeraliszPlague.MODID, "textures/entity/plague_bearer_glow3.png");
   static final ResourceLocation MOLD = new ResourceLocation(HeraliszPlague.MODID, "textures/entity/plague_bearer_petrified.png");
   static final ResourceLocation MOLD_GLOW = new ResourceLocation(HeraliszPlague.MODID, "textures/entity/plague_bearer_petrified_glow.png");
   /** Ordre dans lequel la moisissure gagne les os (0 = la poitrine, d'où elle part). */
   private static final Map<String, Float> MOLD_ORDER = new HashMap<>();
   /** Moment (0..1 de la croissance) où chaque champignon de la mort commence à pousser. */
   private static final float[] BLOOM_DELAY = {0F, 0.18F, 0.3F, 0.08F, 0.45F, 0.55F};

   static {
      Object[] o = {"torso", 0F, "growth", 0.02F, "sac", 0.05F, "tanks_burst", 0.1F, "fungus", 0.1F, "body", 0.18F, "arm_r", 0.2F, "growth_r", 0.22F,
         "fungus_r", 0.22F, "head", 0.3F, "face", 0.34F, "jaw", 0.38F, "arm_l", 0.32F, "thigh_r", 0.4F, "thigh_l", 0.4F, "forearm_r", 0.45F,
         "growth_fr", 0.48F, "forearm_l", 0.5F, "growth_l", 0.52F, "shin_r", 0.6F, "shin_l", 0.6F, "hand_r", 0.66F, "hand_l", 0.7F,
         "finger_r_1", 0.78F, "finger_r_2", 0.8F, "finger_r_3", 0.82F, "finger_r_4", 0.84F, "thumb_r", 0.8F, "hook", 0.86F, "foot_r", 0.8F,
         "foot_l", 0.8F};
      for (int i = 0; i < o.length; i += 2) MOLD_ORDER.put((String) o[i], (Float) o[i + 1]);
   }

   static float moldAlpha(String bone, float p) {
      float h = MOLD_ORDER.getOrDefault(bone, 0.5F);
      return Mth.clamp((p * 1.3F - h) / 0.3F, 0, 1);
   }

   /** Taille d'un champignon de la mort (bloom_1..6) pour une croissance g (0 → 1) : il jaillit, dépasse un peu, se pose. */
   static float bloomScale(int k, float g) {
      float u = Mth.clamp((g - BLOOM_DELAY[k]) / 0.35F, 0, 1);
      if (u <= 0) return 0;
      return u < 0.7F ? u / 0.7F * 1.12F : 1.12F - 0.12F * (u - 0.7F) / 0.3F;
   }

   public static class Model extends GeoModel<PlagueBearer> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszPlague.MODID, "geo/entity/plague_bearer.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszPlague.MODID, "animations/entity/plague_bearer.animation.json");

      @Override
      public ResourceLocation getModelResource(PlagueBearer e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(PlagueBearer e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(PlagueBearer e) {
         return ANIM;
      }

      @Override
      public void setCustomAnimations(PlagueBearer e, long id, AnimationState<PlagueBearer> state) {
         super.setCustomAnimations(e, id, state);
         int ph = e.phase();
         hide("mask", ph == 3);
         hide("face", ph != 3);
         hide("tanks", ph != 1);
         hide("tanks_burst", ph == 1);
         for (String b : new String[] {"growth", "growth_r", "growth_fr", "growth_l", "sac"}) hide(b, ph == 1);
         hide("fungus", ph != 3);
         hide("fungus_r", ph != 3);
         PlagueBearer.Action a = e.action();
         int t = e.actionTime();
         // objets tenus le temps du geste
         boolean gren = a == PlagueBearer.Action.GRENADES && ((t >= 8 && t < 16) || (t >= 18 && t < 26) || (t >= 28 && t < 36));
         hide("grenade", !gren);
         hide("pustule", !(a == PlagueBearer.Action.PUSTULES && t >= 14 && t < 26));
         // le crochet vole au bout de sa chaîne (dessinée par les effets)
         boolean hookOut = (a == PlagueBearer.Action.HOOK && t >= 18) || (a == PlagueBearer.Action.HAUL && t < 30)
            || (a == PlagueBearer.Action.GRAPPLE && t < 26);
         hide("hook", hookOut);
         // phase 3 qui commence : les champignons sortent du dos
         float fung = a == PlagueBearer.Action.PHASE3 ? Mth.clamp((t + state.getPartialTick() - 56) / 20F, 0, 1) : 1F;
         scale("fungus", fung);
         scale("fungus_r", fung);
         // mort : la colonie éclot
         float g = e.bloom(state.getPartialTick());
         for (int k = 0; k < 6; k++) {
            String b = "bloom_" + (k + 1);
            float s = bloomScale(k, g);
            hide(b, s <= 0.001F);
            scale(b, Math.max(0.001F, s));
         }
      }

      private void hide(String bone, boolean h) {
         getBone(bone).ifPresent(b -> {
            b.setHidden(h);
            b.setChildrenHidden(h);
         });
      }

      private void scale(String bone, float s) {
         getBone(bone).ifPresent(b -> {
            b.setScaleX(b.getScaleX() * s);
            b.setScaleY(b.getScaleY() * s);
            b.setScaleZ(b.getScaleZ() * s);
         });
      }
   }

   private boolean moldPass;
   private float moldP;

   public PlagueBearerRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(PlagueBearer.RS);
      this.shadowRadius = 2.2F;
   }

   @Override
   public RenderType getRenderType(PlagueBearer e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   public int getPackedOverlay(PlagueBearer e, float u, float partial) {
      return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && !e.isDeadOrDying()));
   }

   @Override
   public int getPackedOverlay(PlagueBearer e, float u) {
      return getPackedOverlay(e, u, 0);
   }

   @Override
   protected float getDeathMaxRotation(PlagueBearer e) {
      return 0F;
   }

   @Override
   public boolean shouldRender(PlagueBearer e, Frustum f, double x, double y, double z) {
      if (e.distanceToSqr(x, y, z) >= 160 * 160) return false;
      // hors champ : rien à dessiner (modèle redessiné pour les lueurs et la moisissure) ; à la mort, toujours dessiné
      return e.isDeadOrDying() || f.isVisible(e.getBoundingBoxForCulling().inflate(6, 4, 6));
   }

   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      if (moldPass) {
         float al = bone.getName().startsWith("bloom") ? 0F : moldAlpha(bone.getName(), moldP);
         if (al > 0.01F) super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, al);
         return;
      }
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   private static float glowStrength(PlagueBearer e, float partial) {
      float t = e.tickCount + partial;
      float k = e.phase() == 3 ? 0.85F + 0.15F * Mth.sin(t * 0.3F) : 0.62F + 0.12F * Mth.sin(t * 0.08F);
      if (e.action() == PlagueBearer.Action.SPAWN) {
         // agenouillé dans le gaz, éteint ; les verres du masque s'allument en grésillant
         float st = e.actionTime() + partial;
         if (st < 110) return 0;
         if (st < 122) return (Mth.sin(st * 2.7F) > 0.1F ? 1F : 0.15F) * k;
      }
      if (e.isDeadOrDying()) k *= 1 - Mth.clamp((e.deathTime + partial - 120) / 30F, 0, 1);
      return k;
   }

   @Override
   public void actuallyRender(PoseStack ps, PlagueBearer e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
      if (reRender) return;
      float k = glowStrength(e, partial);
      if (k > 0.01F) {
         RenderType glow = RenderType.eyes(e.phase() == 3 ? GLOW3 : GLOW1);
         super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k, k, k, 1);
      }
      float p = e.mold(partial);
      if (p > 0.001F) {
         // la moisissure gagne les os un à un, depuis la poitrine
         moldPass = true;
         moldP = p;
         RenderType mold = RenderType.entityTranslucent(MOLD);
         super.actuallyRender(ps, e, model, mold, buf, buf.getBuffer(mold), true, partial, light, overlay, 1, 1, 1, 1);
         moldPass = false;
      }
      float bl = e.bloom(partial);
      if (bl > 0.001F || p > 0.6F) {
         // lueur des champignons et des spores prises dans la moisissure
         float em = Math.max(bl, (p - 0.6F) / 0.4F) * (0.75F + 0.25F * Mth.sin((e.tickCount + partial) * 0.12F));
         RenderType mg = RenderType.eyes(MOLD_GLOW);
         super.actuallyRender(ps, e, model, mg, buf, buf.getBuffer(mg), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, em, em, em, 1);
      }
   }
}
