package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxDraw;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.entity.SujetN07;
import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Réservoir dorsal rendu en temps réel : liquide dont la surface reste horizontale et ballotte (ressort amorti piloté par
 * l'accélération du réservoir), vagues, bulles, verre à reflets de bord, fissures, fuite, éclats.
 */
public class TankLayer extends GeoRenderLayer<SujetN07> {
   private static final ResourceLocation LIQUID = tex("liquid"), SURFACE = tex("liquid_surface"), GLASS = tex("glass"),
      GLASS_CRACKED = tex("glass_cracked"), STREAM = tex("stream"), BUBBLE = tex("bubble");
   private static final int N = 18;

   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszN07.MODID, "textures/entity/tank/" + n + ".png");
   }

   /** Pose (espace caméra) de l'origine de l'entité, capturée par le rendu juste avant GeckoLib. */
   static Matrix4f entityPose = new Matrix4f();

   private static final class Slosh {
      Vector3f n = new Vector3f(0, 1, 0), vel = new Vector3f();
      Vec3 lastPos, lastVel = Vec3.ZERO;
      long lastNanos;
      float energy;
   }

   private final Map<SujetN07, Slosh> slosh = new WeakHashMap<>();

   public TankLayer(GeoRenderer<SujetN07> renderer) {
      super(renderer);
   }

   /** Niveau de liquide (0..1) selon la phase et les transitions. */
   static float fill(SujetN07 e, float partial) {
      float t = e.actionTime() + partial;
      if (e.isDeadOrDying()) return e.phase() >= 2 || e.vitrify(partial) > 0 ? 0 : 0.9F;
      return switch (e.action()) {
         // réveil : la cuve se remplit pendant qu'il dort
         case SPAWN -> Mth.clamp(0.1F + (t - SujetN07.SPAWN_FILL0) / (SujetN07.SPAWN_FILL1 - SujetN07.SPAWN_FILL0) * 0.8F, 0.1F, 0.9F);
         // la cuve explose au début de la phase 2 : le liquide se vide d'un coup sur le corps
         case PHASE2 -> t < SujetN07.P2_BURST ? 0.9F : Mth.lerp(Mth.clamp((t - SujetN07.P2_BURST) / 4F, 0, 1), 0.9F, 0F);
         case PHASE3 -> 0;
         default -> e.phase() == 1 ? 0.9F : 0;
      };
   }

   /** 0 intact (hermétique, plein), 2 éclaté (explose à la phase 2). L'état 1 « fissuré » n'est plus utilisé. */
   static int glassState(SujetN07 e, float partial) {
      float t = e.actionTime() + partial;
      if (e.isDeadOrDying()) return e.phase() >= 2 || e.vitrify(partial) > 0 ? 2 : 0;
      if (e.action() == SujetN07.Action.PHASE2) return t < SujetN07.P2_BURST ? 0 : 2;
      if (e.action() == SujetN07.Action.PHASE3) return 2;
      return e.phase() == 1 ? 0 : 2;
   }

   @Override
   public void render(PoseStack ps, SujetN07 e, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float partial, int light, int overlay) {
      if (SujetN07Renderer.tankPose == null) return;
      // m : modèle (blocs) -> caméra, capturée pendant le dessin de l'os ; local : modèle -> repère monde centré sur l'entité
      Matrix4f m = new Matrix4f(SujetN07Renderer.tankPose);
      Matrix4f local = new Matrix4f(entityPose).invert().mul(m);
      float alphaMul = 1F;

      float cx = TankGeometry.CX / 16F, cz = TankGeometry.CZ / 16F, y0 = TankGeometry.Y0 / 16F, y1 = TankGeometry.Y1 / 16F;
      float rl = TankGeometry.R_LIQUID / 16F, rg = TankGeometry.R_GLASS / 16F;
      float time = e.tickCount + partial;

      // --- ballottement : la surface vise la « gravité apparente » (gravité - accélération du réservoir)
      Slosh s = slosh.computeIfAbsent(e, k -> new Slosh());
      Vector4f cLocal = local.transform(new Vector4f(cx, (y0 + y1) / 2, cz, 1));
      Vec3 pos = e.getPosition(partial).add(cLocal.x, cLocal.y, cLocal.z);
      long now = System.nanoTime();
      float dt = s.lastNanos == 0 ? 0.016F : Mth.clamp((now - s.lastNanos) / 1e9F, 0.001F, 0.05F);
      s.lastNanos = now;
      if (s.lastPos == null) s.lastPos = pos;
      Vec3 vel = pos.subtract(s.lastPos).scale(1 / dt);
      Vec3 acc = vel.subtract(s.lastVel).scale(1 / dt);
      s.lastPos = pos;
      s.lastVel = vel;
      double al = Math.min(acc.length(), 25);
      if (acc.lengthSqr() > 1e-6) acc = acc.normalize().scale(al);
      Vector3f target = new Vector3f((float) -acc.x * 0.35F, 9.8F, (float) -acc.z * 0.35F).normalize();
      Vector3f force = new Vector3f(target).sub(s.n).mul(70F).sub(new Vector3f(s.vel).mul(4.5F));
      s.vel.add(force.mul(dt));
      s.n.add(new Vector3f(s.vel).mul(dt)).normalize();
      if (s.n.y < 0.55F) {
         s.n.y = 0.55F;
         s.n.normalize();
      }
      s.energy = Mth.lerp(dt * 3, s.energy, Math.min(1, s.vel.length() * 0.6F));
      // normale monde -> espace du réservoir (partie rotation de la matrice locale, sans l'échelle)
      Matrix3f rot = new Matrix3f();
      local.get3x3(rot);
      float sc = new Vector3f(rot.m00, rot.m01, rot.m02).length();
      rot.scale(1F / Math.max(1e-4F, sc));
      Matrix3f inv = new Matrix3f(rot).transpose();
      Vector3f nb = inv.transform(new Vector3f(s.n));
      if (nb.y < 0.3F) {
         nb.y = 0.3F;
         nb.normalize();
      }

      float fill = fill(e, partial);
      int gstate = glassState(e, partial);
      float top = y1 - 0.4F / 16F;
      if (fill > 0.01F) drawLiquid(buf, m, cx, cz, y0, top, rl, fill, nb, time, s.energy, alphaMul);
      drawGlass(buf, m, cx, cz, y0, y1, rg, gstate, time, alphaMul);
      if (gstate == 1 && fill > 0.2F && !e.isDeadOrDying()) drawLeak(e, buf, local, cx, cz, y0, rg, time, partial, alphaMul);
   }

   private static float surfaceY(float x, float z, float cx, float cz, float hc, Vector3f n, float time, float energy, float r) {
      float h = hc - (n.x * (x - cx) + n.z * (z - cz)) / n.y;
      double ang = Math.atan2(z - cz, x - cx);
      float rr = (float) Math.hypot(x - cx, z - cz) / Math.max(1e-4F, r);
      h += (0.008F + 0.03F * energy) * rr * Mth.sin((float) (ang * 2 + time * 0.35F)) + 0.006F * Mth.sin(time * 0.6F + rr * 6);
      return h;
   }

   private void drawLiquid(MultiBufferSource buf, Matrix4f m, float cx, float cz, float y0, float top, float r, float fill, Vector3f n,
                           float time, float energy, float a) {
      float hc = y0 + fill * (top - y0);
      VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(LIQUID));
      float[] hs = new float[N + 1];
      for (int i = 0; i <= N; i++) {
         double ang = i * Mth.TWO_PI / N;
         float x = cx + (float) Math.cos(ang) * r, z = cz + (float) Math.sin(ang) * r;
         hs[i] = Mth.clamp(surfaceY(x, z, cx, cz, hc, n, time, energy, r), y0, top);
      }
      int col = VfxDraw.argb(0xFFFFFF, 0.88F * a);
      float scroll = time * 0.01F;
      for (int i = 0; i < N; i++) {
         double a0 = i * Mth.TWO_PI / N, a1 = (i + 1) * Mth.TWO_PI / N;
         float x0 = cx + (float) Math.cos(a0) * r, z0 = cz + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, z1 = cz + (float) Math.sin(a1) * r;
         float u0 = (float) i / N * 2, u1 = (float) (i + 1) / N * 2;
         VfxDraw.v(vc, m, x0, y0, z0, u0, 1 + scroll, col);
         VfxDraw.v(vc, m, x1, y0, z1, u1, 1 + scroll, col);
         VfxDraw.v(vc, m, x1, hs[i + 1], z1, u1, 1 - (hs[i + 1] - y0) * 4 + scroll, col);
         VfxDraw.v(vc, m, x0, hs[i], z0, u0, 1 - (hs[i] - y0) * 4 + scroll, col);
      }
      // surface : deux anneaux + centre, qui ondulent
      VertexConsumer sv = buf.getBuffer(RenderType.entityTranslucentEmissive(SURFACE));
      int sc = VfxDraw.argb(0xFFFFFF, 0.92F * a);
      float[] radii = {0, r * 0.5F, r};
      for (int ring = 0; ring < 2; ring++) {
         for (int i = 0; i < N; i++) {
            double a0 = i * Mth.TWO_PI / N, a1 = (i + 1) * Mth.TWO_PI / N;
            float ri = radii[ring], ro = radii[ring + 1];
            float[][] pts = {
               {cx + (float) Math.cos(a0) * ri, cz + (float) Math.sin(a0) * ri}, {cx + (float) Math.cos(a1) * ri, cz + (float) Math.sin(a1) * ri},
               {cx + (float) Math.cos(a1) * ro, cz + (float) Math.sin(a1) * ro}, {cx + (float) Math.cos(a0) * ro, cz + (float) Math.sin(a0) * ro}};
            for (float[] p : pts) {
               float y = Mth.clamp(surfaceY(p[0], p[1], cx, cz, hc, n, time, energy, r), y0, top);
               float u = 0.5F + (p[0] - cx) / (r * 2) + time * 0.004F, v = 0.5F + (p[1] - cz) / (r * 2);
               VfxDraw.v(sv, m, p[0], y, p[1], u, v, sc);
            }
         }
      }
      // bulles qui montent
      VertexConsumer bv = buf.getBuffer(VfxRenderTypes.glow(BUBBLE));
      for (int i = 0; i < 12; i++) {
         float h1 = hash(i * 3.1F), h2 = hash(i * 7.7F + 1), h3 = hash(i * 1.9F + 5);
         float ang = h1 * Mth.TWO_PI + time * 0.02F * (h2 - 0.5F);
         float rr = r * (0.2F + 0.7F * h2);
         float x = cx + Mth.cos(ang) * rr, z = cz + Mth.sin(ang) * rr;
         float surf = surfaceY(x, z, cx, cz, hc, n, time, energy, r);
         float yy = y0 + ((time * (0.004F + 0.006F * h3) + h1) % 1F) * (hc - y0 + 0.2F);
         if (yy > surf - 0.01F) continue;
         float sz = 0.012F + 0.02F * h3;
         int bc = VfxDraw.argb(0xD8FFB0, 0.8F * a);
         VfxDraw.v(bv, m, x - sz, yy - sz, z, 0, 1, bc);
         VfxDraw.v(bv, m, x + sz, yy - sz, z, 1, 1, bc);
         VfxDraw.v(bv, m, x + sz, yy + sz, z, 1, 0, bc);
         VfxDraw.v(bv, m, x - sz, yy + sz, z, 0, 0, bc);
         VfxDraw.v(bv, m, x, yy - sz, z - sz, 0, 1, bc);
         VfxDraw.v(bv, m, x, yy - sz, z + sz, 1, 1, bc);
         VfxDraw.v(bv, m, x, yy + sz, z + sz, 1, 0, bc);
         VfxDraw.v(bv, m, x, yy + sz, z - sz, 0, 0, bc);
      }
   }

   private void drawGlass(MultiBufferSource buf, Matrix4f m, float cx, float cz, float y0, float y1, float r, int state, float time, float a) {
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(state == 0 ? GLASS : GLASS_CRACKED));
      float shoulder = y1 - 2.4F / 16F;
      for (int i = 0; i < N; i++) {
         double a0 = i * Mth.TWO_PI / N, a1 = (i + 1) * Mth.TWO_PI / N;
         float u0 = (float) i / N, u1 = (float) (i + 1) / N;
         if (state < 2) {
            quadGlass(vc, m, cx, cz, a0, a1, u0, u1, y0, shoulder, r, r, a, 0);
            quadGlass(vc, m, cx, cz, a0, a1, u0, u1, shoulder, y1, r, r * 0.74F, a, 1);
         } else {
            // éclaté : dents irrégulières en bas et en haut
            float hb = y0 + (1.5F + 5.5F * hash(i * 2.3F)) / 16F;
            float ht = y1 - (0.8F + 3.2F * hash(i * 5.1F + 3)) / 16F;
            quadGlass(vc, m, cx, cz, a0, a1, u0, u1, y0, hb, r, r, a, 0);
            quadGlass(vc, m, cx, cz, a0, a1, u0, u1, ht, y1, r * (ht > shoulder ? 0.74F + 0.26F * (y1 - ht) / (y1 - shoulder) : 1F), r * 0.74F, a, 1);
         }
      }
   }

   /** Pan de verre avec alpha « de Fresnel » : transparent de face, plus opaque et brillant sur les bords. */
   private static void quadGlass(VertexConsumer vc, Matrix4f m, float cx, float cz, double a0, double a1, float u0, float u1,
                                 float yb, float yt, float rb, float rt, float a, int part) {
      float[][] p = {
         {cx + (float) Math.cos(a0) * rb, yb, cz + (float) Math.sin(a0) * rb, u0, 1}, {cx + (float) Math.cos(a1) * rb, yb, cz + (float) Math.sin(a1) * rb, u1, 1},
         {cx + (float) Math.cos(a1) * rt, yt, cz + (float) Math.sin(a1) * rt, u1, 0}, {cx + (float) Math.cos(a0) * rt, yt, cz + (float) Math.sin(a0) * rt, u0, 0}};
      double[] angs = {a0, a1, a1, a0};
      for (int k = 0; k < 4; k++) {
         Vector4f pc = m.transform(new Vector4f(p[k][0], p[k][1], p[k][2], 1));
         Vector3f nc = new Vector3f((float) Math.cos(angs[k]), part == 1 ? 0.5F : 0, (float) Math.sin(angs[k]));
         m.transformDirection(nc).normalize();
         Vector3f view = new Vector3f(-pc.x, -pc.y, -pc.z).normalize();
         float fres = 1 - Math.abs(nc.dot(view));
         float alpha = (0.1F + 0.6F * fres * fres) * a;
         vc.vertex(pc.x, pc.y, pc.z).color(1F, 1F, 1F, alpha).uv(p[k][3], p[k][4]).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .uv2(0xF000F0).normal(0, 1, 0).endVertex();
      }
   }

   /** Fuite de phase 2 : filet d'acide qui tombe verticalement jusqu'au sol (face caméra), éclaboussures en bas. */
   private void drawLeak(SujetN07 e, MultiBufferSource buf, Matrix4f local, float cx, float cz, float y0, float rg, float time, float partial, float a) {
      double ang = Math.toRadians(215);
      Vector4f p = local.transform(new Vector4f(cx + (float) Math.cos(ang) * rg * 1.02F, y0 + 3F / 16F, cz + (float) Math.sin(ang) * rg * 1.02F, 1));
      Vec3 top = new Vec3(p.x, p.y, p.z), bottom = new Vec3(p.x, 0.02, p.z);
      Minecraft mc = Minecraft.getInstance();
      Vec3 camRel = mc.gameRenderer.getMainCamera().getPosition().subtract(e.getPosition(partial));
      Vec3 mid = top.add(bottom).scale(0.5);
      Vec3 side = new Vec3(0, -1, 0).cross(camRel.subtract(mid)).normalize().scale(0.075);
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(STREAM));
      int c = VfxDraw.argb(0x9CFF5A, 0.9F * a);
      float len = (float) (top.y - bottom.y), v = -time * 0.15F;
      VfxDraw.v(vc, entityPose, top.x - side.x, top.y, top.z - side.z, 0, v, c);
      VfxDraw.v(vc, entityPose, top.x + side.x, top.y, top.z + side.z, 1, v, c);
      VfxDraw.v(vc, entityPose, bottom.x + side.x, bottom.y, bottom.z + side.z, 1, v + len * 2, c);
      VfxDraw.v(vc, entityPose, bottom.x - side.x, bottom.y, bottom.z - side.z, 0, v + len * 2, c);
      if (!mc.isPaused() && e.level().random.nextInt(3) == 0) {
         Vec3 w = e.getPosition(partial).add(bottom);
         e.level().addParticle(ModParticles.ACID_SPLASH.get(), w.x, w.y + 0.03, w.z, 0, 0, 0);
      }
   }

   private static float hash(float x) {
      float s = Mth.sin(x * 12.9898F) * 43758.547F;
      return s - (float) Math.floor(s);
   }
}
