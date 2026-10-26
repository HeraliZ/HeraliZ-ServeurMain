package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heralisz.sylvorn.entity.SylvornLegs;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Les jambes arrachées : le modèle de Sylvorn réduit à ses jambes, tête en bas, pendues aux deux branches par les chevilles. */
public class SylvornLegsRenderer extends GeoEntityRenderer<SylvornLegs> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszSylvorn.MODID, "textures/entity/sylvorn.png");
   private static final Set<String> KEEP = Set.of("root", "hips", "thigh_r", "thigh_l", "shin_r", "shin_l", "hock_r", "hock_l", "foot_r", "foot_l",
      "tear_r", "tear_l");

   public static class Model extends GeoModel<SylvornLegs> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszSylvorn.MODID, "geo/entity/sylvorn.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszSylvorn.MODID, "animations/entity/sylvorn.animation.json");

      @Override
      public ResourceLocation getModelResource(SylvornLegs e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(SylvornLegs e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(SylvornLegs e) {
         return ANIM;
      }

      @Override
      public void setCustomAnimations(SylvornLegs e, long id, AnimationState<SylvornLegs> state) {
         super.setCustomAnimations(e, id, state);
         for (software.bernie.geckolib.core.animatable.model.CoreGeoBone b : getAnimationProcessor().getRegisteredBones()) {
            boolean keep = KEEP.contains(b.getName());
            b.setHidden(!keep || b.getName().equals("root") || b.getName().equals("hips"));
            if (!keep) b.setChildrenHidden(true);
            else b.setChildrenHidden(false);
         }
      }
   }

   private Matrix4f rootBase;
   private final Map<String, Matrix4f> poses = new HashMap<>();
   private final Map<String, Vector3f> pivots = new HashMap<>();

   public SylvornLegsRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(Sylvorn.RS);
   }

   @Override
   public RenderType getRenderType(SylvornLegs e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   protected void applyRotations(SylvornLegs e, PoseStack ps, float age, float yaw, float partial) {
      ps.mulPose(Axis.YP.rotationDegrees(180F - e.legsYaw()));
      ps.translate(0, 0.3F, 0);
      ps.mulPose(Axis.XP.rotationDegrees(180F + e.swing(partial)));
      ps.translate(0, -0.3F, 0);
   }

   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      if (bone.getName().startsWith("foot_")) {
         poses.put(bone.getName(), new Matrix4f(ps.last().pose()));
         pivots.put(bone.getName(), new Vector3f(bone.getPivotX() / 16F, bone.getPivotY() / 16F, bone.getPivotZ() / 16F));
      }
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   @Override
   public void actuallyRender(PoseStack ps, SylvornLegs e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      if (!reRender) poses.clear();
      super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
   }

   @Override
   public void render(SylvornLegs e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      rootBase = new Matrix4f(ps.last().pose());
      super.render(e, yaw, partial, ps, buf, light);
      Sylvorn s = e.owner();
      if (s == null) return;
      Vec3 ep = e.getPosition(partial);
      for (int i = 0; i < 2; i++) {
         String n = i == 0 ? "foot_r" : "foot_l";
         Matrix4f m = poses.get(n);
         Vector3f p = pivots.get(n);
         if (m == null || p == null) continue;
         Vec3 an = s.branchAnchor(i);
         Vector3f anchor = rootBase.transformPosition(new Vector3f((float) (an.x - ep.x), (float) (an.y - ep.y), (float) (an.z - ep.z)));
         Vector3f tip = m.transformPosition(new Vector3f(p).add(0, 2 / 16F, 0));
         SylvornRenderer.branch(buf, anchor, tip, 1F, m, p, 3.2F, light, 7100L + i);
      }
   }
}
