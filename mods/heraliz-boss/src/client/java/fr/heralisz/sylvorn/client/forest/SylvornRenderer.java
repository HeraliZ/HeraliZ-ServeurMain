package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.entity.Sylvorn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendu de Sylvorn : le masque de crâne se fend (phase 2) puis vole en éclats (phase 3) et révèle le visage de chair aux yeux
 * multiples ; lueur verte des orbites et du cœur, veines luminescentes en phase 3 ; tronc tenu en main pendant le lancer.
 * Mort : l'écorce monte des genoux jusqu'à la main levée (os par os, dans l'ordre de hauteur de la pose finale), la lueur meurt,
 * il ne reste que des braises ; des racines l'enserrent et des branches mortes sortent de ses bois et de ses bras.
 */
public class SylvornRenderer extends GeoEntityRenderer<Sylvorn> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszSylvorn.MODID, "textures/entity/sylvorn.png");
   private static final ResourceLocation GLOW1 = new ResourceLocation(HeraliszSylvorn.MODID, "textures/entity/sylvorn_glow1.png");
   private static final ResourceLocation GLOW3 = new ResourceLocation(HeraliszSylvorn.MODID, "textures/entity/sylvorn_glow3.png");
   static final ResourceLocation WITHERED = new ResourceLocation(HeraliszSylvorn.MODID, "textures/entity/sylvorn_withered.png");

   public static class Model extends GeoModel<Sylvorn> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszSylvorn.MODID, "geo/entity/sylvorn.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszSylvorn.MODID, "animations/entity/sylvorn.animation.json");

      @Override
      public ResourceLocation getModelResource(Sylvorn e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(Sylvorn e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(Sylvorn e) {
         return ANIM;
      }

      @Override
      public void setCustomAnimations(Sylvorn e, long id, AnimationState<Sylvorn> state) {
         super.setCustomAnimations(e, id, state);
         int ph = e.phase();
         hide("mask", ph != 1);
         hide("mask_cracked", ph != 2);
         hide("mask_broken", ph != 3);
         hide("face", ph == 1);
         hide("growth", ph == 1);
         hide("eyes3", ph != 3);
         // la mort : les jambes arrachées (moignons), les lambeaux du haut des cuisses restent aux jambes
         boolean legless = e.legless(e.deathClock(state.getPartialTick()));
         hide("stump_r", !legless);
         hide("stump_l", !legless);
         hide("tear_r", true);
         hide("tear_l", true);
         hide("thigh_r", legless);
         hide("thigh_l", legless);
         // la tête suit la cible quand il n'attaque pas
         if (e.action() == Sylvorn.Action.NONE) {
            float yaw = Mth.wrapDegrees(Mth.lerp(state.getPartialTick(), e.yHeadRotO, e.yHeadRot) - Mth.lerp(state.getPartialTick(), e.yBodyRotO, e.yBodyRot));
            getBone("neck").ifPresent(b -> b.setRotY(b.getRotY() - Mth.clamp(yaw, -50, 50) * 0.4F * Mth.DEG_TO_RAD));
            getBone("head").ifPresent(b -> b.setRotY(b.getRotY() - Mth.clamp(yaw, -50, 50) * 0.6F * Mth.DEG_TO_RAD));
         }
      }

      private void hide(String bone, boolean h) {
         getBone(bone).ifPresent(b -> {
            b.setHidden(h);
            b.setChildrenHidden(h);
         });
      }
   }

   private Matrix4f handPose;
   private Matrix3f handNormal;
   private float hx, hy, hz;
   private final Map<String, Matrix4f> bonePose = new HashMap<>();
   private final Map<String, Matrix3f> boneNormal = new HashMap<>();

   public SylvornRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(Sylvorn.RS);
      this.shadowRadius = 2.2F;
   }

   @Override
   public RenderType getRenderType(Sylvorn e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   /** Pas de voile rouge pendant la mort (seulement quand il prend un coup vivant). */
   @Override
   public int getPackedOverlay(Sylvorn e, float u, float partial) {
      return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && !e.isDeadOrDying()));
   }

   @Override
   public int getPackedOverlay(Sylvorn e, float u) {
      return getPackedOverlay(e, u, 0);
   }

   @Override
   protected float getDeathMaxRotation(Sylvorn e) {
      return 0F;
   }

   @Override
   public boolean shouldRender(Sylvorn e, Frustum f, double x, double y, double z) {
      if (e.distanceToSqr(x, y, z) >= 160 * 160) return false;
      // hors champ : rien à dessiner ; boîte large (bras, tronc tenu en main) ; à la mort (branches tirées de la voûte),
      // toujours dessiné comme avant
      return e.isDeadOrDying() || f.isVisible(e.getBoundingBoxForCulling().inflate(9, 6, 9));
   }

   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      // repères des os : lus seulement par les branches de la mort ; relevés une fois par image (pas aux passes de lueur)
      if (captureOs && (Branches.SYLVORN_BONES.contains(bone.getName()) || CAPTURE.contains(bone.getName()))) {
         pivots.put(bone.getName(), new Vector3f(bone.getPivotX() / 16F, bone.getPivotY() / 16F, bone.getPivotZ() / 16F));
         bonePose.put(bone.getName(), new Matrix4f(ps.last().pose()));
         boneNormal.put(bone.getName(), new Matrix3f(ps.last().normal()));
      }
      if (!passeLueur && bone.getName().equals("hand_r")) {
         handPose = new Matrix4f(ps.last().pose());
         handNormal = new Matrix3f(ps.last().normal());
         hx = bone.getPivotX() / 16F;
         hy = bone.getPivotY() / 16F;
         hz = bone.getPivotZ() / 16F;
      }
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   private float glowStrength(Sylvorn e, float partial) {
      float t = e.tickCount + partial;
      float base = e.phase() == 3 ? 0.8F + 0.2F * Mth.sin(t * 0.25F) : 0.6F + 0.1F * Mth.sin(t * 0.08F);
      // à l'agonie la lueur vacille, puis s'éteint à mesure que l'écorce la recouvre
      if (e.isDeadOrDying()) base *= (1 - e.withered(partial)) * (0.75F + 0.25F * Mth.sin(t * 1.3F));
      return base;
   }

   private static final java.util.Set<String> CAPTURE = java.util.Set.of("foot_r", "foot_l", "chest", "hips");
   private final Map<String, Vector3f> pivots = new HashMap<>();

   /** Pendu la tête en bas, puis retombé à plat ventre : bascule autour des chevilles (puis des hanches). */
   @Override
   protected void applyRotations(Sylvorn e, PoseStack ps, float age, float yaw, float partial) {
      super.applyRotations(e, ps, age, yaw, partial);
      if (!e.isDeadOrDying()) return;
      float t = e.deathClock(partial);
      float f = e.flip(t);
      if (Math.abs(f) < 0.01F) return;
      float pv = e.flipPivot(t);
      ps.translate(0, pv, 0);
      ps.mulPose(Axis.XP.rotationDegrees(f));
      ps.translate(0, -pv, 0);
   }

   @Override
   public void render(Sylvorn e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      rootBase = new Matrix4f(ps.last().pose());
      renderMain(e, yaw, partial, ps, buf, light);
      if (e.isDeadOrDying()) deathBranches(e, partial, buf, light);
   }

   /** Repère de l'entité (translation seule, avant l'échelle du modèle), relevé au début du rendu. */
   private Matrix4f rootBase;
   private static final Matrix4f ID4 = new Matrix4f();
   private static final org.joml.Matrix3f ID3 = new org.joml.Matrix3f();

   /** Un os (son pivot) dans l'espace de la caméra, décalé de off (px du modèle). */
   private Vector3f bone(String n, float ox, float oy, float oz) {
      Matrix4f m = bonePose.get(n);
      Vector3f p = pivots.get(n);
      if (m == null || p == null) return null;
      return m.transformPosition(new Vector3f(p).add(ox / 16F, oy / 16F, oz / 16F));
   }

   /**
    * Les branches de l'Arbre : deux descendent de la voûte jusqu'à ses chevilles et s'y enroulent (elles le tiennent pendu, puis
    * emportent ses jambes) ; la troisième vient s'enrouler autour de son buste et le soulève.
    */
   private void deathBranches(Sylvorn e, float partial, MultiBufferSource buf, int light) {
      if (rootBase == null) return;
      float t = e.deathClock(partial);
      Vec3 ep = e.getPosition(partial);
      java.util.function.Function<Vec3, Vector3f> world = w -> rootBase.transformPosition(new Vector3f((float) (w.x - ep.x), (float) (w.y - ep.y), (float) (w.z - ep.z)));
      if (t >= Sylvorn.D_REACH && t < Sylvorn.D_TEAR) {
         float grow = Mth.clamp((t - Sylvorn.D_REACH) / (float) (Sylvorn.D_GRAB - Sylvorn.D_REACH), 0, 1);
         for (int i = 0; i < 2; i++) {
            String foot = i == 0 ? "foot_r" : "foot_l";
            Vector3f tip = bone(foot, 0, 2, 0);
            if (tip == null) continue;
            branch(buf, world.apply(e.branchAnchor(i)), tip, grow, bonePose.get(foot), pivots.get(foot), 3.2F, light, 7100L + i);
         }
      }
      if (t >= Sylvorn.D_REACH2 && t < Sylvorn.D_DROP + 2) {
         float grow = Mth.clamp((t - Sylvorn.D_REACH2) / (float) (Sylvorn.D_GRAB2 - Sylvorn.D_REACH2), 0, 1);
         Vector3f tip = bone("chest", 0, 4, 0);
         if (tip != null) branch(buf, world.apply(e.branchAnchor(2)), tip, grow, bonePose.get("chest"), pivots.get("chest"), 12.5F, light, 7200L);
      }
   }

   /** Une branche : de l'ancrage (voûte) en arc jusqu'à la cible, puis une spirale autour de l'os (rayon ring, en px). */
   static void branch(MultiBufferSource buf, Vector3f anchor, Vector3f tip, float grow, Matrix4f bonePose, Vector3f pivot, float ring, int light,
                      long seed) {
      Vector3f down = new Vector3f(anchor).lerp(tip, 0.35F).add(0, 3.5F, 0);
      Vector3f near = new Vector3f(tip).add(0, 2.5F, 0);
      java.util.List<Vector3f> path = new java.util.ArrayList<>(java.util.List.of(anchor, down, near, tip));
      java.util.List<Vector3f> pts = grow < 1 ? cutPath(path, grow) : path;
      if (grow >= 1 && bonePose != null && pivot != null) {
         for (int i = 1; i <= 18; i++) {
            float u = i / 18F;
            double an = u * Mth.TWO_PI * 2.2;
            pts.add(bonePose.transformPosition(new Vector3f(pivot).add((float) Math.cos(an) * ring / 16F, (4 - 8 * u) / 16F, (float) Math.sin(an) * ring / 16F)));
         }
      }
      RootTube.draw(ID4, ID3, buf, pts, 0.85F, grow >= 1 ? 0.14F : 0.3F, light, 1F, seed, true, 1F);
   }

   /** Le début d'un chemin, jusqu'à la fraction f de sa longueur. */
   static java.util.List<Vector3f> cutPath(java.util.List<Vector3f> p, float f) {
      float total = 0;
      for (int i = 1; i < p.size(); i++) total += p.get(i).distance(p.get(i - 1));
      float want = total * f, acc = 0;
      java.util.List<Vector3f> out = new java.util.ArrayList<>();
      out.add(p.get(0));
      for (int i = 1; i < p.size(); i++) {
         float l = p.get(i).distance(p.get(i - 1));
         if (acc + l >= want) {
            out.add(new Vector3f(p.get(i - 1)).lerp(p.get(i), (want - acc) / Math.max(1e-4F, l)));
            break;
         }
         out.add(p.get(i));
         acc += l;
      }
      if (out.size() < 2) out.add(new Vector3f(p.get(0)).add(0, 0.01F, 0));
      return out;
   }

   private void renderMain(Sylvorn e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      super.render(e, yaw, partial, ps, buf, BossLight.lift(light));
   }

   @Override
   public void actuallyRender(PoseStack ps, Sylvorn e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      if (reRender) {
         // redessin demandé par une couche : rien à relever (la main et les os viennent de la passe principale)
         boolean avant = passeLueur;
         passeLueur = true;
         try {
            super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
         } finally {
            passeLueur = avant;
         }
         return;
      }
      handPose = null;
      bonePose.clear();
      boneNormal.clear();
      pivots.clear();
      captureOs = e.isDeadOrDying();
      try {
         super.actuallyRender(ps, e, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
      } finally {
         captureOs = false;
      }
      passeLueur = true;
      try {
         passesLueur(ps, e, model, buf, partial, light, overlay);
      } finally {
         passeLueur = false;
      }
   }

   /** Vrai pendant la passe principale d'un boss qui meurt : on relève les repères des os pour les branches. */
   private boolean captureOs;
   /** Vrai pendant les passes de lueur, de dessèchement et les redessins : la main n'est pas relevée de nouveau. */
   private boolean passeLueur;

   private void passesLueur(PoseStack ps, Sylvorn e, BakedGeoModel model, MultiBufferSource buf, float partial, int light, int overlay) {
      Matrix4f hp = handPose;
      Matrix3f hn = handNormal;
      // modèle en cubes (remake) : la couche lumineuse repasse sur les mêmes cubes (orbites, cœur, sève, yeux en phase 3)
      RenderType glow = RenderType.eyes(e.phase() == 3 ? GLOW3 : GLOW1);
      float k = glowStrength(e, partial);
      if (k > 0.01F) super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, k, k, k, 1);
      float w = e.withered(partial);
      if (w > 0.001F) {
         // la vie aspirée : il se dessèche en bois mort gris
         RenderType dry = RenderType.entityTranslucent(WITHERED);
         super.actuallyRender(ps, e, model, dry, buf, buf.getBuffer(dry), true, partial, light, overlay, 1, 1, 1, w);
      }
      if (e.holdingLog() && hp != null) renderLog(ps, hp, hn, buf, light);
   }

   /** Tronc d'épicéa serré dans la main droite, dans l'axe des doigts. */
   private void renderLog(PoseStack ps, Matrix4f pose, Matrix3f normal, MultiBufferSource buf, int light) {
      ps.pushPose();
      ps.last().pose().set(pose);
      ps.last().normal().set(normal);
      ps.translate(hx, hy - 0.4, hz);
      ps.mulPose(Axis.XP.rotationDegrees(90));
      ps.scale(0.9F, 1F, 0.9F);
      for (int i = -2; i <= 2; i++) {
         ps.pushPose();
         ps.translate(-0.5, i - 0.5, -0.5);
         Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.SPRUCE_LOG.defaultBlockState(), ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
      ps.popPose();
   }
}
