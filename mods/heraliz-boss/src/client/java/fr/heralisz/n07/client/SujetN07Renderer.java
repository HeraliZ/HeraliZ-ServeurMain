package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.entity.SujetN07;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rendu du Sujet N-07. Phase 1 : respirateur, cuve pleine (verre et liquide qui ballotte dessinés en direct par TankLayer).
 * Phase 2 : la cuve a éclaté, le liquide coule sur lui, les os jaillissent des épaules, les tumeurs gonflent. Phase 3 : le
 * respirateur arraché laisse voir la gorge qui luit, le tuyau planté dans le cœur, toutes les veines allumées.
 * Mort : la fusion le vitrifie ; le verre vert (trinitite) le gagne depuis le cœur, os par os.
 */
public class SujetN07Renderer extends GeoEntityRenderer<SujetN07> {
   private static final ResourceLocation TEX = new ResourceLocation(HeraliszN07.MODID, "textures/entity/sujet_n07.png");
   private static final ResourceLocation GLOW1 = new ResourceLocation(HeraliszN07.MODID, "textures/entity/sujet_n07_glow1.png");
   private static final ResourceLocation GLOW3 = new ResourceLocation(HeraliszN07.MODID, "textures/entity/sujet_n07_glow3.png");
   static final ResourceLocation GLASS = new ResourceLocation(HeraliszN07.MODID, "textures/entity/sujet_n07_petrified.png");
   static final ResourceLocation GLASS_GLOW = new ResourceLocation(HeraliszN07.MODID, "textures/entity/sujet_n07_petrified_glow.png");
   private static final String[] SPILL = {"spill", "spill_hips", "spill_arm_r", "spill_arm_l", "spill_fore_r", "spill_fore_l"};
   /** Ordre dans lequel le verre gagne les os (0 = le cœur, d'où part la fusion). */
   private static final Map<String, Float> GLASS_ORDER = new HashMap<>();

   static {
      Object[] o = {"core", 0F, "hose_chest", 0.02F, "chest", 0.05F, "spill", 0.08F, "spikes", 0.1F, "neck", 0.15F, "tank", 0.18F, "valve", 0.2F,
         "hose_neck", 0.2F, "spine", 0.2F, "head", 0.25F, "maw", 0.28F, "mask", 0.28F, "arm_r", 0.3F, "arm_l", 0.3F, "spill_arm_r", 0.32F,
         "spill_arm_l", 0.32F, "hips", 0.35F, "spill_hips", 0.37F, "coat_tail", 0.45F, "coat_l", 0.45F, "coat_r", 0.45F, "rag_front", 0.45F,
         "forearm_r", 0.45F, "forearm_l", 0.45F, "growth_r", 0.48F, "growth_l", 0.48F, "spill_fore_r", 0.48F, "spill_fore_l", 0.48F,
         "thigh_r", 0.55F, "thigh_l", 0.55F, "hand_r", 0.62F, "hand_l", 0.62F, "shin_r", 0.7F, "shin_l", 0.7F, "foot_r", 0.85F, "foot_l", 0.85F};
      for (int i = 0; i < o.length; i += 2) GLASS_ORDER.put((String) o[i], (Float) o[i + 1]);
   }

   static float glassAlpha(String bone, float p) {
      float h = GLASS_ORDER.getOrDefault(bone, bone.startsWith("f") || bone.startsWith("thumb") ? 0.75F : 0.5F);
      return Mth.clamp((p * 1.35F - h) / 0.25F, 0, 1);
   }

   /** Pousse avec dépassement (les os « jaillissent »). */
   private static float grow(float t, float start, float len) {
      float k = Mth.clamp((t - start) / len, 0, 1);
      return k >= 1 ? 1 : 1 + 2.7F * (float) Math.pow(k - 1, 3) + 1.7F * (float) Math.pow(k - 1, 2);
   }

   public static class Model extends GeoModel<SujetN07> {
      private static final ResourceLocation GEO = new ResourceLocation(HeraliszN07.MODID, "geo/entity/sujet_n07.geo.json");
      private static final ResourceLocation ANIM = new ResourceLocation(HeraliszN07.MODID, "animations/entity/sujet_n07.animation.json");

      private static final class Dyn {
         float sway, swayV, roll, rollV, lastYaw, lastSpeed;
         long lastNanos;
      }

      private final Map<SujetN07, Dyn> dyn = new WeakHashMap<>();

      @Override
      public ResourceLocation getModelResource(SujetN07 e) {
         return GEO;
      }

      @Override
      public ResourceLocation getTextureResource(SujetN07 e) {
         return TEX;
      }

      @Override
      public ResourceLocation getAnimationResource(SujetN07 e) {
         return ANIM;
      }

      @Override
      public void setCustomAnimations(SujetN07 e, long id, AnimationState<SujetN07> state) {
         super.setCustomAnimations(e, id, state);
         getBone("hand_l").ifPresent(b -> b.setTrackingMatrices(true));
         float partial = state.getPartialTick();
         float t = e.actionTime() + partial;
         SujetN07.Action a = e.action();
         int ph = e.phase();
         // --- mutations : os, tumeurs, liquide répandu (phase 2), respirateur arraché, tuyau planté dans le cœur (phase 3)
         float bones = ph >= 2 ? 1 : 0;
         if (a == SujetN07.Action.PHASE2) bones = grow(t, SujetN07.P2_BONES, 10);
         for (String b : new String[] {"spikes", "growth_r", "growth_l"}) {
            hide(b, bones <= 0.001F);
            scale(b, Math.max(0.001F, bones));
         }
         boolean spilled = ph >= 2 || (a == SujetN07.Action.PHASE2 && t >= SujetN07.P2_BURST);
         float flow = a == SujetN07.Action.PHASE2 ? Mth.clamp((t - SujetN07.P2_BURST) / 12F, 0, 1) : 1;
         for (String b : SPILL) {
            hide(b, !spilled);
            getBone(b).ifPresent(bone -> bone.setScaleY(bone.getScaleY() * Math.max(0.001F, flow)));
         }
         boolean masked = ph < 3 && !(a == SujetN07.Action.PHASE3 && t >= SujetN07.P3_MASK);
         boolean stabbed = ph == 3 || (a == SujetN07.Action.PHASE3 && t >= SujetN07.P3_STAB);
         if (e.isDeadOrDying()) {
            masked = ph < 3;
            stabbed = ph == 3;
         }
         hide("mask", !masked);
         hide("maw", masked);
         hide("core", !stabbed);
         hide("hose_chest", !stabbed);
         hide("hose_neck", stabbed);

         // --- mouvements secondaires : blouse, haillons, cuve (ressorts amortis pilotés par l'accélération et la rotation du corps)
         if (e.isDeadOrDying() && e.vitrify(partial) > 0) return;
         Dyn d = dyn.computeIfAbsent(e, k -> new Dyn());
         long now = System.nanoTime();
         float dt = d.lastNanos == 0 ? 0.016F : Mth.clamp((now - d.lastNanos) / 1e9F, 0.001F, 0.05F);
         d.lastNanos = now;
         float speed = (float) e.getDeltaMovement().horizontalDistance() * 20F;
         float accel = (speed - d.lastSpeed) / dt;
         float yawRate = Mth.wrapDegrees(e.yBodyRot - d.lastYaw) / dt;
         d.lastSpeed = speed;
         d.lastYaw = e.yBodyRot;
         float swayTarget = Mth.clamp(-accel * 0.02F - speed * 0.04F, -0.5F, 0.5F);
         float rollTarget = Mth.clamp(yawRate * 0.004F, -0.4F, 0.4F);
         d.swayV += ((swayTarget - d.sway) * 60F - d.swayV * 6F) * dt;
         d.sway += d.swayV * dt;
         d.rollV += ((rollTarget - d.roll) * 55F - d.rollV * 5.5F) * dt;
         d.roll += d.rollV * dt;
         add("coat_tail", -d.sway * 1.4F, 0, d.roll * 0.8F);
         add("coat_l", -d.sway * 0.8F, 0, d.roll * 0.5F);
         add("coat_r", -d.sway * 0.8F, 0, d.roll * 0.5F);
         add("rag_front", d.sway * 0.9F, 0, d.roll * 0.4F);
         add("tank", d.sway * 0.2F, 0, d.roll * 0.3F);
         add("hose_neck", d.sway * 0.3F, 0, d.roll * 0.3F);
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

      private void add(String bone, float x, float y, float z) {
         getBone(bone).ifPresent(b -> {
            b.setRotX(b.getRotX() + x);
            b.setRotY(b.getRotY() + y);
            b.setRotZ(b.getRotZ() + z);
         });
      }
   }

   /** Pose exacte (espace caméra) de l'os « tank » telle que GeckoLib la dessine : le liquide s'y cale au pixel près. */
   static Matrix4f tankPose;
   private boolean glassPass;
   private float glassP;

   public SujetN07Renderer(EntityRendererProvider.Context ctx) {
      super(ctx, new Model());
      this.withScale(SujetN07.RS);
      this.shadowRadius = 2.4F;
      this.addRenderLayer(new TankLayer(this));
   }

   @Override
   public RenderType getRenderType(SujetN07 e, ResourceLocation tex, MultiBufferSource buf, float partial) {
      return RenderType.entityCutoutNoCull(tex);
   }

   @Override
   public int getPackedOverlay(SujetN07 e, float u, float partial) {
      return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime > 0 && !e.isDeadOrDying()));
   }

   @Override
   public int getPackedOverlay(SujetN07 e, float u) {
      return getPackedOverlay(e, u, 0);
   }

   @Override
   protected float getDeathMaxRotation(SujetN07 e) {
      return 0F;
   }

   @Override
   public boolean shouldRender(SujetN07 e, Frustum f, double x, double y, double z) {
      // 1.1.0 : plus de rendu hors champ (le modele fait ~17 000 faces) ; le rayon traverse l'ecran meme boss hors champ
      if (e.distanceToSqr(x, y, z) >= 160 * 160) return false;
      return e.beamFiring() || f.isVisible(e.getBoundingBoxForCulling().inflate(4.5, 3, 4.5));
   }

   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      if (glassPass) {
         float al = glassAlpha(bone.getName(), glassP);
         if (al > 0.01F) super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, al);
         return;
      }
      if (bone.getName().equals("tank")) tankPose = new Matrix4f(ps.last().pose());
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   /** Intensité des lueurs (verres du respirateur, veines, incision, cœur) : elles pulsent, s'emballent pendant les charges. */
   private static float glowStrength(SujetN07 e, float partial) {
      float time = e.tickCount + partial;
      float t = e.actionTime() + partial;
      SujetN07.Action a = e.action();
      float k = e.phase() == 3 ? 0.85F + 0.15F * Mth.sin(time * 0.3F) : 0.62F + 0.12F * Mth.sin(time * 0.08F);
      if (a == SujetN07.Action.SPAWN) {
         // endormi, éteint ; les verres du masque s'allument en grésillant
         if (t < SujetN07.SPAWN_EYES) return 0.05F;
         if (t < SujetN07.SPAWN_EYES + 10) return (Mth.sin(t * 2.7F) > 0.1F ? 1F : 0.15F) * k;
      }
      if (a == SujetN07.Action.PULSE && t < 26) k = Math.min(1.8F, k + t / 20F);
      if (a == SujetN07.Action.BEAM && t < SujetN07.BEAM_FIRE) k = Math.min(1.8F, k + t / 18F);
      if (a == SujetN07.Action.MELTDOWN && t < SujetN07.MELT_BOOM) k = Math.min(2F, k + t / 40F * (1 + 0.5F * Mth.sin(t * (0.3F + t / 60F))));
      if (a == SujetN07.Action.PHASE3 && t >= SujetN07.P3_STAB && t < SujetN07.P3_STAB + 20) k = 1.8F;
      if (e.isDeadOrDying()) {
         float dt = e.deathTime + partial;
         if (dt < SujetN07.VITRIFY) k = Math.min(2F, k + Math.max(0, dt - 38) / 30F);
         else k *= 1 - e.vitrify(partial);
      }
      return k;
   }

   @Override
   public void render(SujetN07 e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      TankLayer.entityPose = new Matrix4f(ps.last().pose());
      // jamais une silhouette noire : lumière minimale 9 (bloc) pour lire le modèle dans la pénombre du labo
      int bl = Math.max(LightTexture.block(light), 9), sl = LightTexture.sky(light);
      super.render(e, yaw, partial, ps, buf, LightTexture.pack(bl, sl));
      if (e.action() == SujetN07.Action.BEAM && !e.isDeadOrDying()) renderBeam(e, partial, ps, buf);
      // (la chaîne du poignet a disparu : dans son donjon il flotte dans la cuve de confinement)
   }

   // ------------------------------------------------------------------ chaîne rivée au poignet
   private static final ResourceLocation CHAIN = new ResourceLocation("minecraft", "textures/block/chain.png");
   /** Tick de l'apparition où il arrache sa chaîne (SujetN07.spawnSeq). */
   private static final int CHAIN_BREAK = 80;

   /**
    * Dans son donjon il dort enchaîné : une lourde chaîne part d'un anneau au sol, à sa gauche, jusqu'à son poignet gauche.
    * Il l'arrache pendant son réveil ; ensuite un bout de chaîne pend à son poignet (et, le temps du réveil, un bout reste à
    * l'anneau).
    */
   private void renderChain(SujetN07 e, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      GeoBone hand = getGeoModel().getBone("hand_l").orElse(null);
      if (hand == null || !hand.isTrackingMatrices()) return;
      org.joml.Vector3d wp = hand.getWorldPosition();
      Vec3 wrist = new Vec3(wp.x, wp.y, wp.z).subtract(e.position());
      boolean spawning = e.action() == SujetN07.Action.SPAWN && !e.isDeadOrDying();
      float t = e.actionTime() + partial;
      boolean chained = e.dormant() || spawning && t < CHAIN_BREAK;
      Vec3 f = Vec3.directionFromRotation(0, e.yBodyRot);
      Vec3 left = new Vec3(f.z, 0, -f.x);
      Vec3 anchor = f.scale(0.9).add(left.scale(1.9)).add(0, 0.06, 0);
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
      Matrix4f m = ps.last().pose();
      float time = e.tickCount + partial;
      if (chained) {
         // tendue quand il se redresse et tire dessus, lâche quand il dort
         float pull = e.dormant() ? 0 : Mth.clamp((t - 50) / 25F, 0, 1);
         chainLine(vc, m, anchor, wrist, 0.45F * (1 - pull), 18, light);
         ring(vc, m, anchor, light);
         return;
      }
      // bout arraché qui pend au poignet (il se balance)
      Vec3 sway = new Vec3(Mth.sin(time * 0.15F) * 0.25, 0, Mth.cos(time * 0.11F) * 0.25);
      chainLine(vc, m, wrist, wrist.add(0, -1.3, 0).add(sway), 0.05F, 6, light);
      if (spawning && t < CHAIN_BREAK + 120) {
         chainLine(vc, m, anchor, anchor.add(f.scale(-0.6)).add(left.scale(0.5)).add(0, 0.02, 0), 0.0F, 4, light);
         ring(vc, m, anchor, light);
      }
   }

   private static void chainLine(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float sag, int seg, int light) {
      Vec3 prev = a;
      float v = 0;
      for (int i = 1; i <= seg; i++) {
         float u = (float) i / seg;
         Vec3 p = a.add(b.subtract(a).scale(u)).add(0, -sag * 4 * u * (1 - u), 0);
         if (p.y < a.y && p.y < b.y) p = new Vec3(p.x, Math.min(a.y, b.y), p.z);
         float len = (float) p.distanceTo(prev);
         link(vc, m, prev, p, v, v + len, light);
         v += len;
         prev = p;
      }
   }

   /** Un tronçon de chaîne : deux plans croisés comme le bloc de chaîne vanilla (maillons de 3 pixels). */
   private static void link(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float v0, float v1, int light) {
      Vec3 d = b.subtract(a);
      if (d.lengthSqr() < 1e-6) return;
      Vec3 n = d.normalize();
      Vec3 up = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 s1 = n.cross(up).normalize().scale(1.5 / 16 * 1.6);
      Vec3 s2 = n.cross(s1).normalize().scale(1.5 / 16 * 1.6);
      quad(vc, m, a.subtract(s1), a.add(s1), b.add(s1), b.subtract(s1), 0, 3 / 16F, v0, v1, light);
      quad(vc, m, a.subtract(s2), a.add(s2), b.add(s2), b.subtract(s2), 3 / 16F, 6 / 16F, v0, v1, light);
   }

   private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float u1, float v0, float v1, int light) {
      vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(0, 1, 0).endVertex();
      vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(0, 1, 0).endVertex();
      vc.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(0, 1, 0).endVertex();
      vc.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(0, 1, 0).endVertex();
   }

   /** Anneau d'amarrage scellé au sol. */
   private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, int light) {
      int seg = 10;
      double r = 0.28;
      for (int i = 0; i < seg; i++) {
         double a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
         Vec3 p = c.add(Math.cos(a0) * r, 0.02, Math.sin(a0) * r), q = c.add(Math.cos(a1) * r, 0.02, Math.sin(a1) * r);
         link(vc, m, p, q, i * 0.2F, i * 0.2F + 0.2F, light);
      }
   }

   @Override
   public void actuallyRender(PoseStack ps, SujetN07 e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, boolean reRender,
                              float partial, int light, int overlay, float r, float g, float b, float a) {
      float vit = e.vitrify(partial);
      if (reRender) {
         super.actuallyRender(ps, e, model, rt, buf, vc, true, partial, light, overlay, r, g, b, a);
         return;
      }
      if (vit < 1F) super.actuallyRender(ps, e, model, rt, buf, vc, false, partial, light, overlay, r, g, b, a);
      else {
         // entièrement vitrifié : le corps d'origine n'est plus dessiné, seulement le verre
         glassPass = true;
         glassP = 1;
         RenderType gl = RenderType.entityCutoutNoCull(GLASS);
         super.actuallyRender(ps, e, model, gl, buf, buf.getBuffer(gl), false, partial, light, overlay, 1, 1, 1, 1);
         glassPass = false;
      }
      float k = glowStrength(e, partial);
      if (k > 0.01F) {
         RenderType glow = RenderType.eyes(e.phase() == 3 ? GLOW3 : GLOW1);
         super.actuallyRender(ps, e, model, glow, buf, buf.getBuffer(glow), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k, k, k, 1);
      }
      if (vit > 0.001F && vit < 1F) {
         glassPass = true;
         glassP = vit;
         RenderType gl = RenderType.entityTranslucent(GLASS);
         super.actuallyRender(ps, e, model, gl, buf, buf.getBuffer(gl), true, partial, light, overlay, 1, 1, 1, 1);
         glassPass = false;
      }
      if (vit > 0.001F) {
         // le verre encore brûlant luit, les fissures crépitent
         float em = vit * (0.55F + 0.2F * Mth.sin((e.tickCount + partial) * 0.2F)) + (1 - vit) * 0.8F;
         glassPass = true;
         glassP = vit;
         RenderType gg = RenderType.eyes(GLASS_GLOW);
         super.actuallyRender(ps, e, model, gg, buf, buf.getBuffer(gg), true, partial, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, em, em, em, 1);
         glassPass = false;
      }
   }

   // ------------------------------------------------------------------ rayon
   private static final ResourceLocation B_CORE = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/beam_core.png");
   private static final ResourceLocation B_NOISE = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/beam_noise.png");
   private static final ResourceLocation B_RING = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/beam_ring.png");
   private static final ResourceLocation B_FLASH = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/flash.png");

   /**
    * Pendant la charge : fin laser de visée rouge vers la cible + énergie qui grossit dans la gorge ; au tir : faisceau en
    * couches (halo bruité, cœur blanc-vert), anneaux qui filent le long du rayon, éclat à l'impact.
    */
   private void renderBeam(SujetN07 e, float partial, PoseStack ps, MultiBufferSource buf) {
      Vec3 base = e.getPosition(partial);
      Vec3 from = e.mouth().subtract(e.position());
      Vec3 to = e.beamEnd().subtract(base);
      if (to.lengthSqr() < 1e-3) return;
      float t = e.actionTime() + partial;
      Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
      if (t < SujetN07.BEAM_FIRE) {
         float k = t / SujetN07.BEAM_FIRE;
         strip(ps, buf.getBuffer(VfxRenderTypes.glow(B_CORE)), from, to, cam, 0.09F, 0, 0.9F, 0xFF3A2A);
         billboard(ps, buf.getBuffer(VfxRenderTypes.glow(B_FLASH)), from, cam, 0.4F + k * 1.8F, t * 0.05F, 0xB8FF7A, 0.4F + 0.6F * k);
         billboard(ps, buf.getBuffer(VfxRenderTypes.glow(B_FLASH)), to, cam, 0.5F, 0, 0xFF3A2A, 0.8F);
         return;
      }
      if (!e.beamFiring()) return;
      float flick = 1F + 0.1F * Mth.sin(t * 2.7F);
      VertexConsumer glow = buf.getBuffer(VfxRenderTypes.glow(B_NOISE));
      strip(ps, glow, from, to, cam, 2.2F * flick, -t * 0.22F, 0.55F, 0x7CFF3A);
      strip(ps, glow, from, to, cam, 1.3F * flick, -t * 0.35F, 0.7F, 0xB8FF7A);
      strip(ps, buf.getBuffer(VfxRenderTypes.glow(B_CORE)), from, to, cam, 0.7F * flick, 0, 1F, 0xF0FFE0);
      Vec3 dir = to.subtract(from);
      double len = dir.length();
      Vec3 n = dir.normalize();
      Vec3 up = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 u = n.cross(up).normalize(), w = n.cross(u).normalize();
      VertexConsumer rv = buf.getBuffer(VfxRenderTypes.glow(B_RING));
      Matrix4f m = ps.last().pose();
      for (double d = (t * 0.8) % 3.0; d < len; d += 3.0) {
         Vec3 c = from.add(n.scale(d));
         float s = 0.9F + 0.3F * (float) Math.sin(d + t);
         vtxC(rv, m, c.subtract(u.scale(s)).subtract(w.scale(s)), 0, 1, 0xB8FF7A, 0.7F);
         vtxC(rv, m, c.add(u.scale(s)).subtract(w.scale(s)), 1, 1, 0xB8FF7A, 0.7F);
         vtxC(rv, m, c.add(u.scale(s)).add(w.scale(s)), 1, 0, 0xB8FF7A, 0.7F);
         vtxC(rv, m, c.subtract(u.scale(s)).add(w.scale(s)), 0, 0, 0xB8FF7A, 0.7F);
      }
      VertexConsumer fl = buf.getBuffer(VfxRenderTypes.glow(B_FLASH));
      billboard(ps, fl, from, cam, 1.6F * flick, t * 0.1F, 0xD8FF9A, 1F);
      billboard(ps, fl, to, cam, 2.4F * flick, -t * 0.1F, 0xD8FF9A, 1F);
   }

   private static void vtxC(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float v, int rgb, float a) {
      vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Mth.clamp(a, 0, 1) * 255)).uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
   }

   private static void strip(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float w, float scroll, float alpha, int rgb) {
      Vec3 dir = b.subtract(a);
      double len = dir.length();
      Vec3 mid = a.add(b).scale(0.5);
      Vec3 side = dir.cross(cam.subtract(mid)).normalize().scale(w / 2);
      Matrix4f m = ps.last().pose();
      float v0 = scroll, v1 = scroll + (float) len * 0.2F;
      vtxC(vc, m, a.add(side), 0, v0, rgb, alpha);
      vtxC(vc, m, a.subtract(side), 1, v0, rgb, alpha);
      vtxC(vc, m, b.subtract(side), 1, v1, rgb, alpha);
      vtxC(vc, m, b.add(side), 0, v1, rgb, alpha);
   }

   private static void billboard(PoseStack ps, VertexConsumer vc, Vec3 c, Vec3 cam, float s, float ang, int rgb, float a) {
      Vec3 f = cam.subtract(c).normalize();
      Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 r = up.cross(f).normalize();
      Vec3 u = f.cross(r).normalize();
      Vec3 r2 = r.scale(Math.cos(ang)).add(u.scale(Math.sin(ang))).scale(s);
      Vec3 u2 = u.scale(Math.cos(ang)).subtract(r.scale(Math.sin(ang))).scale(s);
      Matrix4f m = ps.last().pose();
      vtxC(vc, m, c.subtract(r2).subtract(u2), 0, 1, rgb, a);
      vtxC(vc, m, c.add(r2).subtract(u2), 1, 1, rgb, a);
      vtxC(vc, m, c.add(r2).add(u2), 1, 0, rgb, a);
      vtxC(vc, m, c.subtract(r2).add(u2), 0, 0, rgb, a);
   }
}
