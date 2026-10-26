package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxDraw;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.decor.DecorBlockEntity;
import fr.heralisz.n07.decor.DecorKind;
import fr.heralisz.n07.decor.DecorSpec;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Rendu des décors du spawn. Tout le mouvement est procédural, lu dans le nom des os du modèle :
 * <ul>
 * <li>swing… : pendule (lanternes, panneaux sur chaînes) — amplitude et période de la fiche, rafales lentes, chaque bloc déphasé ;</li>
 * <li>flame… : flamme qui vacille (échelle) ; neon… : tube néon qui grésille (…bad : le tube fatigué qui clignote souvent) ;</li>
 * <li>blink… : voyant qui clignote ; spin_x/y/z… : rotation continue (ventilateur, bobine) ; wave_N : ondulation d'un tissu ;</li>
 * <li>flap… : feuille qui bat au courant d'air ; vib… : vibration de moteur ; needle… : aiguille de cadran ;</li>
 * <li>drop_N / hang : maillons ajoutés et lanterne descendue quand on rallonge la chaîne.</li>
 * </ul>
 * Plus les faisceaux de lumière (projecteur, phare de la locomotive) : cônes additifs qui s'estompent au bout.
 */
public class DecorRenderer extends GeoBlockRenderer<DecorBlockEntity> {
   private static final int N = DecorKind.values().length;
   private static final ResourceLocation[] GEO = new ResourceLocation[N], TEX = new ResourceLocation[N], ANIM = new ResourceLocation[N];
   private static final ResourceLocation T_BEAM = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/decor_beam.png");

   static {
      for (DecorKind k : DecorKind.values()) {
         String m = "decor_" + k.spec.id;
         GEO[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "geo/block/" + m + ".geo.json");
         TEX[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "textures/entity/prop/" + m + ".png");
         ANIM[k.ordinal()] = new ResourceLocation(HeraliszN07.MODID, "animations/block/" + m + ".animation.json");
      }
   }

   public DecorRenderer() {
      super(new Model());
      addRenderLayer(new AutoGlowingGeoLayer<>(this) {
         @Override
         public void render(PoseStack ps, DecorBlockEntity be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial,
                            int light, int overlay) {
            if (be.kind.glow) super.render(ps, be, model, rt, buf, vc, partial, light, overlay);
         }
      });
   }

   @Override
   protected void rotateBlock(Direction facing, PoseStack ps) {
      ps.mulPose(Axis.YP.rotationDegrees(yaw(facing)));
   }

   private static float yaw(Direction facing) {
      return switch (facing) {
         case SOUTH -> 180F;
         case WEST -> 90F;
         case EAST -> 270F;
         default -> 0F;
      };
   }

   @Override
   public boolean shouldRenderOffScreen(DecorBlockEntity be) {
      return true;
   }

   @Override
   public int getViewDistance() {
      return 128;
   }

   // ------------------------------------------------------------------ verre : seconde passe translucide
   private enum Pass { ALL, SOLID, GLASS }

   private Pass pass = Pass.ALL;

   @Override
   public void actuallyRender(PoseStack ps, DecorBlockEntity be, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                              boolean reRender, float partial, int light, int overlay, float r, float g, float b, float a) {
      if (!be.kind.glass || reRender) {
         super.actuallyRender(ps, be, model, rt, buf, vc, reRender, partial, light, overlay, r, g, b, a);
         return;
      }
      pass = Pass.SOLID;
      super.actuallyRender(ps, be, model, rt, buf, vc, false, partial, light, overlay, r, g, b, a);
      pass = Pass.GLASS;
      RenderType gl = VfxRenderTypes.glass(TEX[be.kind.ordinal()]);
      super.actuallyRender(ps, be, model, gl, buf, buf.getBuffer(gl), true, partial, light, overlay, 1, 1, 1, 1);
      pass = Pass.ALL;
   }

   /** Os « glass… » : verre translucide, éclairé de l'intérieur (lanternes, ampoules, phares). */
   @Override
   public void renderCubesOfBone(PoseStack ps, GeoBone bone, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
      boolean glass = bone.getName().startsWith("glass");
      if (pass == Pass.SOLID && glass || pass == Pass.GLASS && !glass) return;
      if (pass == Pass.GLASS) {
         super.renderCubesOfBone(ps, bone, buf, LightTexture.FULL_BRIGHT, overlay, r, g, b, 1F);
         return;
      }
      super.renderCubesOfBone(ps, bone, buf, light, overlay, r, g, b, a);
   }

   // (render(...) ne peut pas être redéfini ici : conflit d'effacement avec GeckoLib ; on passe par defaultRender)
   @Override
   public void defaultRender(PoseStack ps, DecorBlockEntity be, MultiBufferSource buf, RenderType rt, VertexConsumer vc, float yaw, float partial,
                             int light) {
      ps.pushPose();
      if (be.onRail()) ps.translate(0, -1, 0);
      super.defaultRender(ps, be, buf, rt, vc, yaw, partial, light);
      DecorSpec s = be.kind.spec;
      if (s.beams.length > 0) {
         ps.pushPose();
         ps.translate(0.5, 0, 0.5);
         ps.mulPose(Axis.YP.rotationDegrees(yaw(be.facing())));
         beams(be, s, partial, ps, buf);
         ps.popPose();
      }
      ps.popPose();
   }

   // ------------------------------------------------------------------ faisceaux
   private static void beams(DecorBlockEntity be, DecorSpec s, float partial, PoseStack ps, MultiBufferSource buf) {
      Level l = be.getLevel();
      float t = l == null ? 0 : (l.getGameTime() % 24000L) + partial;
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_BEAM));
      Matrix4f m = ps.last().pose();
      for (int i = 0; i + 7 < s.beams.length; i += 8) {
         Vector3f o = new Vector3f((float) s.beams[i], (float) s.beams[i + 1], (float) s.beams[i + 2]).div(16F);
         Vector3f d = new Vector3f((float) s.beams[i + 3], (float) s.beams[i + 4], (float) s.beams[i + 5]).normalize();
         float len = (float) s.beams[i + 6] / 16F, r1 = (float) s.beams[i + 7] / 16F, r0 = r1 * 0.12F;
         float hum = 0.9F + 0.1F * Mth.sin(t * 0.13F + be.phase * 9F);
         cone(vc, m, o, d, len, r0, r1, 18, VfxDraw.argb(s.beamColor, 0.16F * hum), VfxDraw.argb(s.beamColor, 0));
         cone(vc, m, o, d, len * 0.7F, r0 * 0.7F, r1 * 0.45F, 14, VfxDraw.argb(s.beamColor, 0.12F * hum), VfxDraw.argb(s.beamColor, 0));
      }
   }

   private static void cone(VertexConsumer vc, Matrix4f m, Vector3f o, Vector3f d, float len, float r0, float r1, int seg, int c0, int c1) {
      Vector3f a = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
      Vector3f u = new Vector3f(d).cross(a).normalize(), w = new Vector3f(d).cross(u).normalize();
      Vector3f e = new Vector3f(d).mul(len).add(o);
      for (int j = 0; j < seg; j++) {
         float a0 = Mth.TWO_PI * j / seg, a1 = Mth.TWO_PI * (j + 1) / seg;
         float s0 = (float) j / seg, s1 = (float) (j + 1) / seg;
         Vector3f p00 = ring(o, u, w, r0, a0), p01 = ring(o, u, w, r0, a1), p11 = ring(e, u, w, r1, a1), p10 = ring(e, u, w, r1, a0);
         VfxDraw.v(vc, m, p00.x, p00.y, p00.z, s0, 0, c0);
         VfxDraw.v(vc, m, p01.x, p01.y, p01.z, s1, 0, c0);
         VfxDraw.v(vc, m, p11.x, p11.y, p11.z, s1, 1, c1);
         VfxDraw.v(vc, m, p10.x, p10.y, p10.z, s0, 1, c1);
      }
   }

   private static Vector3f ring(Vector3f c, Vector3f u, Vector3f w, float r, float a) {
      return new Vector3f(u).mul(Mth.cos(a) * r).add(new Vector3f(w).mul(Mth.sin(a) * r)).add(c);
   }

   // ------------------------------------------------------------------ bruit
   static float hash(long n) {
      n = (n << 13) ^ n;
      n = n * (n * n * 15731L + 789221L) + 1376312589L;
      return (float) ((n & 0x7fffffffL) / (double) 0x7fffffffL);
   }

   /** Bruit lisse 1D dans [0, 1]. */
   static float noise(float x, int seed) {
      int i = Mth.floor(x);
      float f = x - i;
      f = f * f * (3 - 2 * f);
      return Mth.lerp(f, hash(i * 7919L + seed), hash((i + 1) * 7919L + seed));
   }

   private static int suffix(String n) {
      int k = n.lastIndexOf('_');
      if (k < 0) return 0;
      try {
         return Integer.parseInt(n.substring(k + 1));
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   static final class Model extends GeoModel<DecorBlockEntity> {
      @Override
      public ResourceLocation getModelResource(DecorBlockEntity be) {
         return GEO[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getTextureResource(DecorBlockEntity be) {
         return TEX[be.kind.ordinal()];
      }

      @Override
      public ResourceLocation getAnimationResource(DecorBlockEntity be) {
         return ANIM[be.kind.ordinal()];
      }

      @Override
      public void setCustomAnimations(DecorBlockEntity be, long instanceId, AnimationState<DecorBlockEntity> state) {
         super.setCustomAnimations(be, instanceId, state);
         Level l = be.getLevel();
         float t = (l == null ? 0 : (l.getGameTime() % 240000L)) + state.getPartialTick();
         DecorSpec s = be.kind.spec;
         float ph = be.phase;
         int drop = s.drop ? be.drop() : 0;
         for (CoreGeoBone b : getAnimationProcessor().getRegisteredBones()) {
            String n = b.getName();
            int id = n.hashCode();
            if (n.startsWith("swing")) {
               // pendule : ellipse lente, amplitude modulée par des rafales ; une chaîne plus longue balance plus lentement
               float amp = s.swingAmp / (1 + 0.3F * drop) * (n.startsWith("swing2") ? 0.55F : 1F);
               float per = s.swingPeriod * Mth.sqrt(1 + drop) * (0.9F + 0.2F * hash(id + 3));
               float gust = 0.45F + 0.55F * noise(t / 160F + ph * 17F, id);
               float lag = n.startsWith("swing2") ? 1.1F : 0F;
               float w = Mth.TWO_PI * t / per + ph * 6.28F + hash(id) * 6.28F - lag;
               b.setRotX(Mth.DEG_TO_RAD * amp * gust * Mth.sin(w));
               b.setRotZ(Mth.DEG_TO_RAD * amp * 0.5F * gust * Mth.sin(w * 0.87F + 1.3F));
            } else if (n.startsWith("flame")) {
               float a = noise(t * 0.45F + ph * 31F, id), c = noise(t * 0.8F + ph * 13F, id + 7);
               b.setScaleY(0.78F + 0.45F * a);
               b.setScaleX(0.88F + 0.24F * c);
               b.setScaleZ(0.88F + 0.24F * (1 - c));
               b.setRotZ(Mth.DEG_TO_RAD * 8F * (noise(t * 0.3F, id + 11) - 0.5F));
            } else if (n.startsWith("neon")) {
               long step = (long) (t / 2) + (long) (ph * 1000);
               boolean off;
               if (n.contains("bad")) {
                  // tube fatigué : de longues rafales de grésillement puis quelques secondes de calme
                  boolean burst = noise(t / 60F + ph * 7F, id) > 0.55F;
                  off = burst ? hash(step * 31L + id) < 0.45F : hash(step * 17L + id) < 0.03F;
               } else {
                  off = noise(t / 400F + ph * 5F, id) > 0.93F && hash(step * 13L + id) < 0.5F;
               }
               b.setHidden(off);
            } else if (n.startsWith("blink")) {
               int period = 14 + Math.floorMod(id, 5) * 7;
               b.setHidden(((int) ((t + ph * 97F + Math.floorMod(id, 37)) / period)) % 2 == 0);
            } else if (n.startsWith("spin_")) {
               float a = Mth.DEG_TO_RAD * (t * s.spin * (n.contains("slow") ? 0.25F : 1F) + ph * 360F);
               switch (n.charAt(5)) {
                  case 'x' -> b.setRotX(a);
                  case 'y' -> b.setRotY(a);
                  default -> b.setRotZ(a);
               }
            } else if (n.startsWith("wave_")) {
               int i = suffix(n);
               float k = 0.35F + 0.25F * i;
               float gust = 0.5F + 0.5F * noise(t / 120F + ph * 9F, 5);
               b.setRotX(Mth.DEG_TO_RAD * s.wave * gust * k * Mth.sin(t * 0.11F - i * 0.8F + ph * 6.28F));
               b.setRotZ(Mth.DEG_TO_RAD * s.wave * 0.25F * gust * Mth.sin(t * 0.07F - i * 0.5F + ph * 3F));
            } else if (n.startsWith("flap")) {
               b.setRotX(Mth.DEG_TO_RAD * 14F * (noise(t * 0.09F + ph * 19F, id) - 0.5F) * 2F);
            } else if (n.startsWith("vib")) {
               b.setPosX(0.12F * Mth.sin(t * 2.9F + id));
               b.setPosY(0.1F * Mth.sin(t * 4.1F + 1F));
               b.setPosZ(0.08F * Mth.sin(t * 3.3F + 2F));
            } else if (n.startsWith("needle")) {
               b.setRotZ(Mth.DEG_TO_RAD * (40F * (noise(t * 0.05F + ph * 4F, id) - 0.5F) * 2F + 3F * Mth.sin(t * 1.7F)));
            } else if (n.startsWith("drop_")) {
               b.setHidden(suffix(n) > drop);
            } else if (n.equals("hang")) {
               b.setPosY(-16F * drop);
            }
         }
      }
   }
}
