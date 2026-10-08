package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Racine vivante de Sylvorn : un vrai tube organique (section ronde, bosselée, qui s'affine jusqu'à une pointe), habillé
 * d'une écorce faite main (plaques, fissures, mousse) qui file dans la longueur, avec des veines de sève qui luisent.
 * Le chemin est lissé (Catmull-Rom) et suivi par des repères transportés (pas de torsion), la texture se répète le long de
 * la racine à densité constante. Les points sont donnés dans le repère de {@code pose}.
 */
public final class RootTube {
   public static final ResourceLocation BARK = new ResourceLocation(HeraliszSylvorn.MODID, "textures/fvfx/root_bark.png");
   public static final ResourceLocation GLOW = new ResourceLocation(HeraliszSylvorn.MODID, "textures/fvfx/root_bark_glow.png");
   /** Texture : 64 texels pour un tour, 256 pour une longueur de V_BLOCKS blocs. */
   private static final float V_BLOCKS = 9F;

   private RootTube() {}

   /**
    * @param pts   chemin (au moins 2 points), dans le repère de pose
    * @param r0    rayon au départ, r1 à l'arrivée (la toute fin se referme en pointe si tip)
    * @param glow  intensité des veines (0 : aucune)
    * @param seed  bosses et nœuds propres à cette racine
    * @param scale 1 bloc = scale unités du repère (1 en monde ; 1/échelle du modèle dans un repère de modèle)
    */
   public static void draw(Matrix4f pose, Matrix3f normal, MultiBufferSource buf, List<Vector3f> pts, float r0, float r1, int light, float glow,
                           long seed, boolean tip, float scale) {
      if (pts.size() < 2) return;
      List<Vector3f> path = smooth(pts, 0.3F * scale);
      int n = path.size();
      if (n < 2) return;
      float[] len = new float[n];
      for (int i = 1; i < n; i++) len[i] = len[i - 1] + path.get(i).distance(path.get(i - 1));
      float total = len[n - 1];
      if (total < 1e-4F) return;
      int sides = Math.max(6, Math.min(12, Math.round(7 + 4 * Math.max(r0, r1) / scale)));
      // tour de texture : un tour complet de 64 texels couvre ~3 blocs de circonférence (densité à peu près constante)
      float uRep = Math.max(1, Math.round(2 * Mth.PI * Math.max(r0, r1) / scale / 3F));
      Vector3f[] rings = new Vector3f[n * (sides + 1)];
      Vector3f[] norms = new Vector3f[n * (sides + 1)];
      Vector3f t0 = new Vector3f(path.get(1)).sub(path.get(0)).normalize();
      Vector3f nx = Math.abs(t0.y) < 0.9F ? new Vector3f(0, 1, 0).cross(t0).normalize() : new Vector3f(1, 0, 0).cross(t0).normalize();
      Vector3f tPrev = new Vector3f(t0);
      for (int i = 0; i < n; i++) {
         Vector3f t = i < n - 1 ? new Vector3f(path.get(i + 1)).sub(path.get(i)) : new Vector3f(path.get(i)).sub(path.get(i - 1));
         if (t.lengthSquared() < 1e-12F) t.set(tPrev);
         t.normalize();
         // transport parallèle du repère (la racine ne se vrille pas)
         Vector3f axis = new Vector3f(tPrev).cross(t);
         float s = axis.length();
         if (s > 1e-5F) {
            float ang = (float) Math.atan2(s, tPrev.dot(t));
            nx.rotateAxis(ang, axis.x / s, axis.y / s, axis.z / s);
         }
         nx.sub(new Vector3f(t).mul(nx.dot(t))).normalize();
         Vector3f bx = new Vector3f(t).cross(nx).normalize();
         tPrev.set(t);
         float u = len[i] / total;
         float r = Mth.lerp(u, r0, r1);
         if (tip) r *= Math.min(1F, (1F - u) * 7F + 0.02F);
         // bosses : rayon qui ondule lentement, et quelques nœuds francs
         float lb = len[i] / scale;
         r *= 1F + 0.1F * noise(lb * 0.9F, seed) + 0.16F * Math.max(0, noise(lb * 0.35F, seed + 7) - 0.55F) * 2.2F;
         Vector3f c = path.get(i);
         for (int j = 0; j <= sides; j++) {
            double a = j * Mth.TWO_PI / sides;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            // section un peu aplatie et irrégulière (écorce noueuse)
            float rr = r * (1F + 0.07F * noise(lb * 1.7F + j * 1.3F, seed + 3));
            Vector3f d = new Vector3f(nx).mul(cs).add(new Vector3f(bx).mul(sn));
            rings[i * (sides + 1) + j] = new Vector3f(c).add(new Vector3f(d).mul(rr));
            norms[i * (sides + 1) + j] = d;
         }
      }
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BARK));
      emit(vc, pose, normal, rings, norms, len, n, sides, uRep, scale, light, 1F);
      if (glow > 0.01F) {
         VertexConsumer g = buf.getBuffer(RenderType.eyes(GLOW));
         // (un soupçon plus gros, pour ne pas se battre avec l'écorce)
         Vector3f[] out = new Vector3f[rings.length];
         for (int k = 0; k < rings.length; k++) out[k] = new Vector3f(rings[k]).add(new Vector3f(norms[k]).mul(0.012F * scale));
         emit(g, pose, normal, out, norms, len, n, sides, uRep, scale, LightTexture.FULL_BRIGHT, Mth.clamp(glow, 0, 1));
      }
   }

   private static void emit(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vector3f[] rings, Vector3f[] norms, float[] len, int n, int sides, float uRep,
                            float scale, int light, float k) {
      for (int i = 0; i < n - 1; i++) {
         float v0 = len[i] / scale / V_BLOCKS, v1 = len[i + 1] / scale / V_BLOCKS;
         for (int j = 0; j < sides; j++) {
            int a = i * (sides + 1) + j, b = a + 1, c = a + sides + 1, d = c + 1;
            float u0 = uRep * j / sides, u1 = uRep * (j + 1) / sides;
            vert(vc, pose, normal, rings[a], norms[a], u0, v0, light, k);
            vert(vc, pose, normal, rings[c], norms[c], u0, v1, light, k);
            vert(vc, pose, normal, rings[d], norms[d], u1, v1, light, k);
            vert(vc, pose, normal, rings[b], norms[b], u1, v0, light, k);
         }
      }
   }

   private static void vert(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vector3f p, Vector3f nrm, float u, float v, int light, float k) {
      vc.vertex(pose, p.x, p.y, p.z).color(k, k, k, 1F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
         .normal(normal, nrm.x, nrm.y, nrm.z).endVertex();
   }

   /** Catmull-Rom : un point tous les ~step le long du chemin. */
   private static List<Vector3f> smooth(List<Vector3f> p, float step) {
      List<Vector3f> out = new ArrayList<>();
      int m = p.size();
      for (int i = 0; i < m - 1; i++) {
         Vector3f p0 = p.get(Math.max(0, i - 1)), p1 = p.get(i), p2 = p.get(i + 1), p3 = p.get(Math.min(m - 1, i + 2));
         int k = Math.max(1, (int) Math.ceil(p1.distance(p2) / step));
         for (int s = 0; s < k; s++) {
            float t = s / (float) k, t2 = t * t, t3 = t2 * t;
            Vector3f q = new Vector3f(p1).mul(2)
               .add(new Vector3f(p2).sub(p0).mul(t))
               .add(new Vector3f(p0).mul(2).sub(new Vector3f(p1).mul(5)).add(new Vector3f(p2).mul(4)).sub(p3).mul(t2))
               .add(new Vector3f(p1).mul(3).sub(p0).sub(new Vector3f(p2).mul(3)).add(p3).mul(t3))
               .mul(0.5F);
            out.add(q);
         }
      }
      out.add(new Vector3f(p.get(m - 1)));
      return out;
   }

   /** Bruit 1D lissé dans [-1, 1]. */
   static float noise(float x, long seed) {
      int i = Mth.floor(x);
      float f = x - i;
      float a = hash(i, seed), b = hash(i + 1, seed);
      float w = f * f * (3 - 2 * f);
      return Mth.lerp(w, a, b);
   }

   private static float hash(int i, long seed) {
      long h = i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
      h ^= h >>> 29;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 32;
      return ((h & 0xFFFF) / 32767.5F) - 1F;
   }
}
