package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;

/**
 * Bois mort qui pousse sur un boss pétrifié : branches noueuses au bout des os (bois, bras levés, griffes), racines qui sortent
 * du sol et enserrent les genoux. Construit en vrais volumes texturés (blocs étirés), dans l'espace du modèle : les points
 * d'attache sont des points du modèle au repos (pixels), transformés par la matrice de l'os capturée pendant le rendu.
 */
public final class Branches {
   /** Branche : part de la pointe (t) dans l'axe base (b) → pointe de l'os ; size = échelle. */
   public record Anchor(String bone, float bx, float by, float bz, float tx, float ty, float tz, float size) {}

   /** Racine : sort du sol en s (pixels, espace du modèle posé) et va s'agripper en e. */
   public record Root(float sx, float sz, float ex, float ey, float ez, float size) {}

   public static final Anchor[] SYLVORN = {
      new Anchor("antler_r", 3.4F, 86.1F, -18.5F, 25F, 115F, -18.7F, 1.0F),
      new Anchor("antler_r", 3.4F, 86.1F, -18.5F, 29.7F, 105F, -12F, 0.7F),
      new Anchor("antler_l", -3.4F, 86.1F, -18.5F, -25.5F, 114.9F, -19.1F, 1.0F),
      new Anchor("antler_l", -3.4F, 86.1F, -18.5F, -30.6F, 106.7F, -12.4F, 0.7F),
      new Anchor("bfore_r", 19F, 96F, 13F, 25.5F, 60.9F, -5.3F, 1.15F),
      new Anchor("bfore_l", -19F, 96F, 13F, -25.5F, 60.9F, -5.3F, 1.15F),
      new Anchor("f1_r", 16.5F, 27.1F, -7.6F, 5F, 9.8F, -14.2F, 0.8F),
      new Anchor("f3_r", 16.5F, 27.7F, -9.9F, 5.2F, 9.1F, -10.5F, 0.6F)
   };

   /** Pose finale de Sylvorn (à genoux, main gauche plantée) : genoux (±9,5 ; 3,7 ; -2,9), jarrets (±10 ; 3 ; 18), main (-18 ; 9 ; -21). */
   public static final Root[] SYLVORN_ROOTS = {
      new Root(24F, -9F, 9.5F, 8F, -3F, 1.1F), new Root(-24F, -9F, -9.5F, 8F, -3F, 1.1F),
      new Root(19F, 12F, 10F, 6F, 11F, 0.9F), new Root(-19F, 12F, -10F, 6F, 11F, 0.9F),
      new Root(5F, 36F, 10F, 5F, 27F, 0.8F), new Root(-5F, 36F, -10F, 5F, 27F, 0.8F),
      new Root(-30F, -18F, -18F, 10F, -20F, 0.9F), new Root(-12F, -34F, -18F, 8F, -23F, 0.8F),
      new Root(2F, -16F, 0F, 16F, 5F, 1.2F), new Root(26F, 24F, 9F, 10F, 6F, 0.8F)
   };

   /** Liane de racine enroulée autour d'un os : de a vers b (pixels du modèle au repos), rayon r, tours, ordre (0-1) de pétrification. */
   public record Vine(String bone, float ax, float ay, float az, float bx, float by, float bz, float r, float turns, float order) {}

   /** Sylvorn : les racines montent le long de ses membres à mesure que l'écorce les gagne (des jambes jusqu'au cou). */
   public static final Vine[] SYLVORN_VINES = {
      new Vine("shin_r", 9.5F, 34F, -3F, 10F, 17F, 9F, 4.2F, 1.6F, 0.02F), new Vine("shin_l", -9.5F, 34F, -3F, -10F, 17F, 9F, 4.2F, 1.6F, 0.02F),
      new Vine("hock_r", 10F, 17F, 9F, 10F, 4.5F, 4.5F, 3.2F, 1.2F, 0.0F), new Vine("hock_l", -10F, 17F, 9F, -10F, 4.5F, 4.5F, 3.2F, 1.2F, 0.0F),
      new Vine("thigh_r", 7.5F, 50F, 8F, 9.5F, 34F, -3F, 6.4F, 1.8F, 0.08F), new Vine("thigh_l", -7.5F, 50F, 8F, -9.5F, 34F, -3F, 6.4F, 1.8F, 0.06F),
      new Vine("torso", 0F, 50F, 8F, 0F, 60F, 6.8F, 9.5F, 1.4F, 0.2F), new Vine("chest", 0F, 59.4F, 6.8F, 0F, 73F, -3F, 10.5F, 2.0F, 0.4F),
      new Vine("arm_r", 12.8F, 73F, -2.5F, 17.5F, 53F, 5F, 4.4F, 1.8F, 0.45F), new Vine("arm_l", -12.8F, 73F, -2.5F, -17.5F, 53F, 5F, 4.4F, 1.8F, 0.48F),
      new Vine("forearm_l", -17.5F, 53F, 5F, -16.5F, 31.5F, -7F, 3.6F, 1.6F, 0.14F), new Vine("forearm_r", 17.5F, 53F, 5F, 16.5F, 31.5F, -7F, 3.6F, 1.6F, 0.67F),
      new Vine("neck", 0F, 74F, -4.6F, 0F, 82F, -15F, 5.2F, 1.3F, 0.55F)
   };

   public static final Set<String> SYLVORN_BONES = Set.of("root", "antler_r", "antler_l", "bfore_r", "bfore_l", "f1_r", "f3_r", "shin_r", "shin_l", "hock_r",
      "hock_l", "thigh_r", "thigh_l", "torso", "chest", "arm_r", "arm_l", "forearm_l", "forearm_r", "neck");

   private static final BlockState WOOD = Blocks.DARK_OAK_WOOD.defaultBlockState(), OLD = Blocks.SPRUCE_WOOD.defaultBlockState(),
      ROOT = Blocks.MANGROVE_ROOTS.defaultBlockState();

   private Branches() {}

   /** grow : 0 → 1 (les rameaux secondaires sortent après le tronc de la branche). */
   public static void draw(PoseStack ps, MultiBufferSource buf, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, Anchor[] anchors, float grow,
                           int light) {
      if (grow <= 0.001F) return;
      for (int i = 0; i < anchors.length; i++) {
         Anchor a = anchors[i];
         Matrix4f m = poses.get(a.bone());
         Matrix3f n = normals.get(a.bone());
         if (m == null || n == null) continue;
         ps.pushPose();
         ps.last().pose().set(m);
         ps.last().normal().set(n);
         Vector3f dir = new Vector3f(a.tx() - a.bx(), a.ty() - a.by(), a.tz() - a.bz()).normalize();
         Vector3f tip = new Vector3f(a.tx() / 16F, a.ty() / 16F, a.tz() / 16F).sub(new Vector3f(dir).mul(0.12F * a.size()));
         limb(ps, buf, 7919L * (i + 1), tip, dir, 0.62F * a.size(), 0.085F * a.size(), 0, grow, light);
         ps.popPose();
      }
   }

   /** Branche noueuse en deux tronçons coudés, puis 2-3 rameaux ; chaque niveau pousse un peu après le précédent. */
   private static void limb(PoseStack ps, MultiBufferSource buf, long seed, Vector3f from, Vector3f dir, float len, float r, int depth, float grow, int light) {
      float g = Mth.clamp((grow - depth * 0.25F) / 0.5F, 0, 1);
      if (g <= 0) return;
      RandomSource rng = RandomSource.create(seed);
      Vector3f kink = jitter(dir, rng, 0.4F);
      Vector3f mid = new Vector3f(from).add(new Vector3f(dir).mul(len * 0.5F * g));
      Vector3f end = new Vector3f(mid).add(new Vector3f(kink).mul(len * 0.5F * g));
      seg(ps, buf, from, mid, r, depth == 0 ? WOOD : OLD, light);
      seg(ps, buf, mid, end, r * 0.78F, depth == 0 ? WOOD : OLD, light);
      if (depth >= 2 || g < 1) return;
      int kids = 2 + rng.nextInt(2);
      for (int k = 0; k < kids; k++) {
         Vector3f d2 = jitter(kink, rng, 0.95F);
         limb(ps, buf, seed * 31 + k + 1, k == 0 ? mid : end, d2, len * 0.62F, r * 0.6F, depth + 1, grow, light);
      }
   }

   /** Racines qui percent le sol autour de lui et viennent s'enrouler sur les genoux et la main plantée. */
   public static void roots(PoseStack ps, MultiBufferSource buf, Matrix4f rootPose, Matrix3f rootNormal, Root[] roots, float grow, int light) {
      if (grow <= 0.001F || rootPose == null || rootNormal == null) return;
      ps.pushPose();
      ps.last().pose().set(rootPose);
      ps.last().normal().set(rootNormal);
      for (int i = 0; i < roots.length; i++) {
         Root rt = roots[i];
         float g = Mth.clamp(grow * 1.3F - i * 0.03F, 0, 1);
         if (g <= 0) continue;
         Vector3f s = new Vector3f(rt.sx() / 16F, -0.3F, rt.sz() / 16F);
         Vector3f e = new Vector3f(rt.ex() / 16F, rt.ey() / 16F, rt.ez() / 16F);
         Vector3f c = new Vector3f(s).add(e).mul(0.5F).add(0, 0.9F * rt.size(), 0);
         int n = 9;
         Vector3f prev = s;
         for (int k = 1; k <= n; k++) {
            float t = k / (float) n;
            if (t > g) break;
            float u = 1 - t;
            Vector3f q = new Vector3f(s).mul(u * u).add(new Vector3f(c).mul(2 * u * t)).add(new Vector3f(e).mul(t * t));
            seg(ps, buf, prev, q, (0.16F - 0.1F * t) * rt.size(), k < 4 ? WOOD : ROOT, light);
            prev = q;
         }
         // radicelle qui fait un tour autour du membre
         if (g >= 1) {
            RandomSource rng = RandomSource.create(104729L * (i + 1));
            Vector3f last = e;
            for (int k = 1; k <= 8; k++) {
               double a = k / 8.0 * Mth.TWO_PI * 1.2 + rng.nextDouble() * 0.3;
               Vector3f q = new Vector3f(e).add((float) Math.cos(a) * 0.32F * rt.size(), k * 0.04F, (float) Math.sin(a) * 0.32F * rt.size());
               seg(ps, buf, last, q, 0.05F * rt.size(), ROOT, light);
               last = q;
            }
         }
      }
      ps.popPose();
   }


   /** Lianes : deux racines en hélice par os ; chacune monte quand l'écorce atteint son os (p = progression de la pétrification). */
   public static void vines(PoseStack ps, MultiBufferSource buf, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, Vine[] vines, float p,
                            int light) {
      if (p <= 0.001F) return;
      for (int i = 0; i < vines.length; i++) {
         Vine v = vines[i];
         float g = Mth.clamp((p * 1.25F - v.order() + 0.12F) / 0.3F, 0, 1);
         if (g <= 0) continue;
         Matrix4f m = poses.get(v.bone());
         Matrix3f n = normals.get(v.bone());
         if (m == null || n == null) continue;
         ps.pushPose();
         ps.last().pose().set(m);
         ps.last().normal().set(n);
         Vector3f a = new Vector3f(v.ax(), v.ay(), v.az()).div(16F), b = new Vector3f(v.bx(), v.by(), v.bz()).div(16F);
         Vector3f axis = new Vector3f(b).sub(a);
         Vector3f e1 = new Vector3f(axis).cross(Math.abs(axis.y()) > 0.9F * axis.length() ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
         Vector3f e2 = new Vector3f(axis).cross(e1).normalize();
         float r = v.r() / 16F;
         for (int w = 0; w < 2; w++) {
            float phase = w * Mth.PI + i * 0.7F;
            int steps = 14;
            Vector3f prev = null;
            for (int k = 0; k <= steps; k++) {
               float u = k / (float) steps;
               if (u > g) break;
               float th = phase + u * v.turns() * Mth.TWO_PI;
               // la racine part du bas de l'os (b) et monte vers a : elle grimpe
               Vector3f q = new Vector3f(b).lerp(a, u).add(new Vector3f(e1).mul(Mth.cos(th) * r)).add(new Vector3f(e2).mul(Mth.sin(th) * r));
               if (prev != null) seg(ps, buf, prev, q, (0.075F - 0.03F * u) * (w == 0 ? 1F : 0.75F), w == 0 ? ROOT : WOOD, light);
               prev = q;
            }
         }
         ps.popPose();
      }
   }

   private static Vector3f jitter(Vector3f d, RandomSource rng, float amt) {
      return new Vector3f(d).add((rng.nextFloat() - 0.5F) * 2 * amt, (rng.nextFloat() - 0.3F) * 2 * amt, (rng.nextFloat() - 0.5F) * 2 * amt).normalize();
   }

   /** Tronçon texturé de a à b, section carrée r (bloc étiré). */
   public static void seg(PoseStack ps, MultiBufferSource buf, Vector3f a, Vector3f b, float r, BlockState st, int light) {
      Vector3f d = new Vector3f(b).sub(a);
      float len = d.length();
      if (len < 1e-4F || r < 0.004F) return;
      ps.pushPose();
      ps.translate(a.x(), a.y(), a.z());
      ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.div(len)));
      ps.scale(r, len + r * 0.5F, r);
      ps.translate(-0.5, -0.1, -0.5);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }
}
