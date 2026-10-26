package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.Mammoth;
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
 * Rendu d'Ymirak : les pièces de chaque phase (cristaux de la phase 2, crâne fendu et éclats de glace noire de la phase 3),
 * lueurs qui battent au rythme de son cœur (plus fortes à chaque phase), gel qui l'envahit à la mort.
 */
public class MammothRenderer extends GeoEntityRenderer<Mammoth> {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszMammoth.MODID, "textures/entity/" + n + ".png");
   }

   private static final ResourceLocation TEX = tex("mammoth"), GLOW1 = tex("mammoth_glow1"), GLOW3 = tex("mammoth_glow3"), FROZEN = tex("mammoth_frozen");

   public static class Model extends GeoModel<Mammoth> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszMammoth.MODID, "geo/entity/mammoth.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszMammoth.MODID, "animations/entity/mammoth.animation.json");

      @Override
      public ResourceLocation getModelResource(Mammoth e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(Mammoth e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(Mammoth e) {
         return ANIM;
      }

      @Override
      public void setCustomAnimations(Mammoth e, long id, AnimationState<Mammoth> state) {
         super.setCustomAnimations(e, id, state);
         int ph = e.phase();
         getBone("skull_p12").ifPresent(b -> b.setHidden(ph >= 3));
         getBone("skull_p3").ifPresent(b -> b.setHidden(ph < 3));
         getBone("growth_p23").ifPresent(b -> b.setHidden(ph < 2));
         getBone("growth_tusk_l").ifPresent(b -> b.setHidden(ph < 2));
         getBone("growth_tusk_r").ifPresent(b -> b.setHidden(ph < 2));
         getBone("spikes_p3").ifPresent(b -> b.setHidden(ph < 3));
         boolean gone = e.headGone(state.getPartialTick());                // la tête a éclaté : son crâne est resté sur les dalles
         getBone("head").ifPresent(b -> {
            b.setHidden(gone);
            if (gone) b.setChildrenHidden(true);
         });
      }
   }

   public MammothRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(Mammoth.RS);
      this.shadowRadius = 3.2F;
   }

   @Override
   public RenderType getRenderType(Mammoth e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   public int getPackedOverlay(Mammoth e, float u, float partial) {
      return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && !e.isDeadOrDying()));
   }

   @Override
   public int getPackedOverlay(Mammoth e, float u) {
      return getPackedOverlay(e, u, 0);
   }

   /** Toujours lisible : même dans la pénombre de la salle, le modèle garde un éclairage minimal (on voit ses détails). */
   @Override
   public void render(Mammoth e, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack ps, MultiBufferSource buf, int light) {
      int bl = Math.max(LightTexture.block(light), 11), sl = Math.max(LightTexture.sky(light), 11);
      // sa large boîte peut reposer sur un brasero ou un rebord : le corps, lui, se pose sur le vrai sol sous son centre
      float drop = e.isDeadOrDying() && e.deathTime > 0 ? Mammoth.sink(e.deathTime + partial) : groundGap(e, partial);   // mort : entraîné sous terre
      ps.pushPose();
      if (drop > 0) ps.translate(0, -drop, 0);
      super.render(e, yaw, partial, ps, buf, LightTexture.pack(bl, sl));
      ps.popPose();
   }

   /** Points sondés sous le corps et les pattes (créés une fois, plus à chaque image). */
   private static final double[][] SOL = {{0, 0}, {1.5, 1.5}, {-1.5, 1.5}, {1.5, -1.5}, {-1.5, -1.5}};

   private static float groundGap(Mammoth e, float partial) {
      if (!e.onGround()) return 0;
      net.minecraft.world.phys.Vec3 p = e.getPosition(partial);
      float worst = 0, n = 0, sum = 0;
      for (double[] o : SOL) {
         net.minecraft.core.BlockPos b = net.minecraft.core.BlockPos.containing(p.x + o[0], p.y + 0.4, p.z + o[1]);
         for (int i = 0; i < 4; i++) {
            net.minecraft.core.BlockPos q = b.below(i);
            net.minecraft.world.phys.shapes.VoxelShape sh = e.level().getBlockState(q).getCollisionShape(e.level(), q);
            if (!sh.isEmpty() && q.getY() + sh.max(net.minecraft.core.Direction.Axis.Y) <= p.y + 0.05) {
               float gap = (float) (p.y - (q.getY() + sh.max(net.minecraft.core.Direction.Axis.Y)));
               sum += gap;
               n++;
               break;
            }
         }
      }
      if (n == 0) return 0;
      float g = sum / n;                                             // sol moyen sous le corps et les pattes
      return g > 0.2F && g < 3F ? g : 0;
   }

   @Override
   protected float getDeathMaxRotation(Mammoth e) {
      return 0F;
   }

   @Override
   public boolean shouldRender(Mammoth e, Frustum f, double x, double y, double z) {
      if (e.shattered() || e.distanceToSqr(x, y, z) >= 180 * 180) return false;
      // hors champ : rien à dessiner (modèle GeckoLib redessiné deux à trois fois par image) ; boîte élargie pour la tête, les
      // défenses et les pièces des phases ; à la mort (il s'enfonce, son crâne reste), toujours dessiné comme avant
      return e.isDeadOrDying() || f.isVisible(e.getBoundingBoxForCulling().inflate(7, 5, 7));
   }

   /** Lueur : le cœur bat (deux coups rapprochés), plus fort à chaque phase ; presque éteinte quand il dort dans sa glace. */
   private static float glow(Mammoth e, float partial) {
      float t = (e.tickCount + partial) / 20F;
      float period = e.isDeadOrDying() ? 1.6F + e.deathTime / 90F : e.phase() == 3 ? 0.9F : e.phase() == 2 ? 1.2F : 1.6F;
      float ph = (t % period) / period;
      float beat = (float) (Math.exp(-Math.pow((ph - 0.08) / 0.05, 2)) + 0.7 * Math.exp(-Math.pow((ph - 0.24) / 0.05, 2)));
      float base = e.dormant() ? 0.18F : e.phase() == 3 ? 0.85F : e.phase() == 2 ? 0.7F : 0.55F;
      float k = base + beat * (e.dormant() ? 0.12F : 0.3F);
      if (e.isDeadOrDying()) k *= Mth.clamp(1 - (e.deathTime + partial - Mammoth.SOUL_OUT) / (float) (Mammoth.SOUL_IN - Mammoth.SOUL_OUT), 0F, 1F);
      return Math.min(1.25F, k);
   }

   @Override
   public void actuallyRender(PoseStack ps, Mammoth e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      float fz = e.frozen(partial);
      float wr = 1 - 0.3F * fz, wg = 1 - 0.12F * fz;
      super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r * wr, g * wg, b, a);
      if (reRender) return;
      RenderType glow = RenderType.eyes(e.phase() == 3 ? GLOW3 : GLOW1);
      float k = glow(e, partial);
      if (e.eyesAblaze()) {                                                // ses yeux s'allument en rouge à travers la glace
         float f = 1.3F + 0.3F * Mth.sin((e.tickCount + partial) * 0.5F);
         super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, f, f * 0.18F,
            f * 0.12F, 1);
      } else {
         super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k, k, k, 1);
      }
      if (e.eyesInIce()) eyesThroughIce(ps, e, model, buf, partial);
      if (fz > 0.01F) {
         RenderType fr = RenderType.entityTranslucent(FROZEN);
         super.actuallyRender(ps, e, model, fr, buf, buf.getBuffer(fr), true, partial, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, fz * 0.92F);
      }
   }

   /**
    * Ses yeux, sur le modèle même, s'allument en rouge à travers la glace : seule la tête est redessinée, avec sa carte de
    * lueur (les orbites), en additif et sans test de profondeur ; la glace de la prison ne peut pas les masquer.
    */
   private void eyesThroughIce(PoseStack ps, Mammoth e, BakedGeoModel model, MultiBufferSource buf, float partial) {
      java.util.List<software.bernie.geckolib.cache.object.GeoBone> all = new java.util.ArrayList<>();
      java.util.ArrayDeque<software.bernie.geckolib.cache.object.GeoBone> q = new java.util.ArrayDeque<>(model.topLevelBones());
      while (!q.isEmpty()) {
         software.bernie.geckolib.cache.object.GeoBone b = q.poll();
         all.add(b);
         q.addAll(b.getChildBones());
      }
      boolean[] hid = new boolean[all.size()], kids = new boolean[all.size()];
      for (int i = 0; i < all.size(); i++) {
         software.bernie.geckolib.cache.object.GeoBone b = all.get(i);
         hid[i] = b.isHidden();
         kids[i] = b.isHidingChildren();
         b.setHidden(!"head".equals(b.getName()));
         b.setChildrenHidden(false);
      }
      float age = e.actionTime() - Mammoth.EYES_FROM + partial;
      float on = Mth.clamp(age / 4F, 0, 1) * (0.85F + 0.15F * Mth.sin(age * 0.45F));
      RenderType xr = fr.heralisz.mammoth.client.vfx.VfxRenderTypes.glowXray(GLOW1);
      for (int pass = 0; pass < 3; pass++) {                             // trois passes : un rouge vif, saturé
         super.actuallyRender(ps, e, model, xr, buf, buf.getBuffer(xr), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, on,
            on * 0.12F, on * 0.06F, 1);
      }
      for (int i = 0; i < all.size(); i++) {
         all.get(i).setHidden(hid[i]);
         all.get(i).setChildrenHidden(kids[i]);
      }
   }
}
