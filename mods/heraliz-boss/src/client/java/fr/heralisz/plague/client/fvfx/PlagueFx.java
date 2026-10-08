package fr.heralisz.plague.client.fvfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.plague.HeraliszPlague;
import fr.heralisz.plague.fvfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Effets du Porteur de Peste : nappes de gaz, jets de bile, chaîne du crochet, cosses de spores qui tombent du ciel. */
final class PlagueFx {
   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszPlague.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_PUFF = tex("gas_puff"), T_BILE = tex("bile"), T_GLOW = tex("glow"), T_SCORCH = tex("scorch"),
      T_SILK = tex("silk");

   private PlagueFx() {}

   static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Matrix4f m = ps.last().pose();
      switch (p.type()) {
         case GAS -> gas(p, seed, age, k, m, buf, cam);
         case BILE -> bile(p, seed, age, m, buf, cam);
         case CHAIN -> chain(p, age, partial, ps, buf, cam);
         case SPOREPOD -> sporePod(p, age, k, ps, buf, cam);
         default -> {
         }
      }
   }

   private static int shade(int rgb, float f) {
      int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
      return (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
   }

   /** Nappe de gaz : tache brûlée au sol et bouffées lourdes qui roulent lentement au ras du sol. */
   private static void gas(Vfx.Packet p, float seed, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      float in = Mth.clamp(age / 10F, 0, 1), out = 1 - Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
      float al = in * out;
      int col = p.color() & 0xFFFFFF;
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_SCORCH)), m, p.x(), p.y() + 0.03, p.z(), p.a() * 1.05, seed, VfxDraw.argb(0xFFFFFF, 0.55F * al));
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(T_PUFF));
      RandomSource r = RandomSource.create((long) (seed * 1000) + (long) (p.a() * 17));
      int n = (int) Mth.clamp(p.a() * 5, 6, 40);
      for (int i = 0; i < n; i++) {
         double ang = r.nextDouble() * Mth.TWO_PI + age * 0.004 * (i % 2 == 0 ? 1 : -1);
         double rad = Math.sqrt(r.nextDouble()) * p.a() * 0.85;
         double h = 0.3 + r.nextDouble() * 1.4 + Math.sin(age * 0.05 + i) * 0.15;
         Vec3 c = new Vec3(p.x() + Math.cos(ang) * rad, p.y() + h, p.z() + Math.sin(ang) * rad);
         float size = (float) (0.9 + r.nextDouble() * 1.3) * (0.7F + 0.3F * in);
         billboard(vc, m, c, cam, size, r.nextFloat() * 6F + age * 0.01F * (i % 3 - 1), VfxDraw.argb(shade(col, 0.72F + r.nextFloat() * 0.3F), 0.3F * al));
      }
   }

   private static int bileColor(float u, float a) {
      int r = (int) Mth.lerp(u, 196, 92), g = (int) Mth.lerp(u, 236, 128), b = (int) Mth.lerp(u, 70, 26);
      return VfxDraw.argb((r << 16) | (g << 8) | b, a);
   }

   /** Jet de bile : gerbe de gouttes épaisses qui s'élargit et retombe en arc. */
   private static void bile(Vfx.Packet p, float seed, float age, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Vec3 d = p.dir();
      if (d.lengthSqr() < 1e-6) return;
      d = d.normalize();
      Vec3 s1 = d.cross(new Vec3(0, 1, 0));
      if (s1.lengthSqr() < 1e-4) s1 = new Vec3(1, 0, 0);
      s1 = s1.normalize();
      Vec3 s2 = s1.cross(d).normalize();
      double spread = Math.tan(Math.toRadians(p.b() / 2));
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glass(T_BILE));
      RandomSource rng = RandomSource.create((long) (seed * 1000));
      int n = 30;
      for (int i = 0; i < n; i++) {
         float u = (i / (float) n + age / Math.max(1, p.duration())) % 1F;
         double dist = u * p.a();
         double ang = rng.nextDouble() * Mth.TWO_PI, rr = Math.sqrt(rng.nextDouble()) * spread * dist;
         Vec3 c = p.pos().add(d.scale(dist)).add(s1.scale(Math.cos(ang) * rr)).add(s2.scale(Math.sin(ang) * rr)).add(0, -u * u * 2.4, 0);
         float size = (float) (0.28 + dist * 0.09);
         float a = (1 - u * 0.6F) * Math.min(1, u * 8) * 0.9F;
         billboard(vc, m, c, cam, size, rng.nextFloat() * 6F, bileColor(u, a));
      }
   }

   /** Main gauche du Porteur (d'où part la chaîne), d'après son orientation. */
   private static Vec3 hand(Entity e, float partial) {
      float yaw = e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot();
      Vec3 f = Vec3.directionFromRotation(0, yaw);
      Vec3 r = new Vec3(-f.z, 0, f.x);
      return e.getPosition(partial).add(f.scale(1.1)).add(r.scale(-1.4)).add(0, 3.4, 0);
   }

   /** Ruban face caméra de a à b. */
   private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, double w, int argb) {
      Vec3 d = b.subtract(a);
      Vec3 side = d.cross(cam.subtract(a));
      if (side.lengthSqr() < 1e-8) return;
      side = side.normalize().scale(w / 2);
      VfxDraw.v(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, 0, 0, argb);
      VfxDraw.v(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, 1, 0, argb);
      VfxDraw.v(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, 1, 1, argb);
      VfxDraw.v(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, 0, 1, argb);
   }

   /**
    * Chaîne du crochet (vraie chaîne en blocs « chaîne » alignés), de la main gauche du Porteur jusqu'au crochet : visée (fil
    * rouge), lancer (le crochet file vers sa cible), accroché à un joueur, planté dans le sol ou un mur.
    */
   private static void chain(Vfx.Packet p, float age, float partial, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      Minecraft mc = Minecraft.getInstance();
      int mode = (int) p.b();
      Entity boss = mc.level.getEntity(p.extra());
      Vec3 from = boss != null ? hand(boss, partial) : p.pos();
      Vec3 to;
      if (mode == 0 || mode == 2) {
         Entity t = mc.level.getEntity(p.color());
         if (t == null) return;
         to = t.getPosition(partial).add(0, t.getBbHeight() * (mode == 0 ? 0.6 : 0.55), 0);
         if (mode == 0) {
            float pulse = 0.5F + 0.5F * Mth.sin(age * 1.3F);
            ribbon(buf.getBuffer(VfxRenderTypes.glow(T_SILK)), ps.last().pose(), from, to, cam, 0.09, VfxDraw.argb(0xFF3A2A, 0.45F + 0.4F * pulse));
            return;
         }
      } else if (mode == 1) {
         float u = Mth.clamp(age / Math.max(1, p.duration() - 1), 0, 1);
         Vec3 goal = p.pos().add(p.dir());
         to = from.add(goal.subtract(from).scale(u));
      } else {
         to = p.pos().add(p.dir());
      }
      Vec3 d = to.subtract(from);
      double len = d.length();
      if (len < 0.3) return;
      Vec3 dir = d.scale(1 / len);
      Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));
      BlockState link = Blocks.CHAIN.defaultBlockState();
      int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(from.add(d.scale(0.5))));
      int n = (int) Math.ceil(len / 0.9);
      double step = len / n;
      for (int i = 0; i < n; i++) {
         Vec3 c = from.add(dir.scale(step * (i + 0.5)));
         ps.pushPose();
         ps.translate(c.x, c.y, c.z);
         ps.mulPose(q);
         ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(i % 2 == 0 ? 0 : 90));
         ps.scale(1.7F, (float) step * 1.02F, 1.7F);
         ps.translate(-0.5, -0.5, -0.5);
         mc.getBlockRenderer().renderSingleBlock(link, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
      // le crochet de boucher au bout : tige, courbe, pointe
      ps.pushPose();
      ps.translate(to.x, to.y, to.z);
      ps.mulPose(q);
      BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
      hookPart(ps, buf, iron, 0, 0.35, 0, 0.12F, 0.7F, 0.12F, 0, light);
      hookPart(ps, buf, iron, 0.2, 0.72, 0, 0.4F, 0.12F, 0.12F, 0, light);
      hookPart(ps, buf, iron, 0.38, 0.55, 0, 0.12F, 0.4F, 0.12F, -20, light);
      ps.popPose();
   }

   private static void hookPart(PoseStack ps, MultiBufferSource buf, BlockState st, double x, double y, double z, float sx, float sy, float sz, float rz, int light) {
      ps.pushPose();
      ps.translate(x, y, z);
      if (rz != 0) ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rz));
      ps.scale(sx, sy, sz);
      ps.translate(-0.5, -0.5, -0.5);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   /** Cosse de spores : boule de mousse au cœur luminescent qui tombe du ciel en semant une traînée de spores. */
   private static void sporePod(Vfx.Packet p, float age, float k, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
      float e = (float) Math.pow(k, 1.5);
      Vec3 from = p.pos();
      Vec3 at = from.add(p.dir().scale(e));
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_GLOW));
      for (int j = 0; j < 12; j++) {
         float kk = (float) Math.pow(Math.max(0, k - j * 0.02F), 1.5);
         Vec3 q = from.add(p.dir().scale(kk));
         float a = (1 - j / 12F) * 0.6F;
         billboard(vc, ps.last().pose(), q, cam, p.a() * (1.1F - j * 0.06F), j * 0.8F + age * 0.2F, VfxDraw.argb(0xC8FF8A, a));
      }
      Minecraft mc = Minecraft.getInstance();
      ps.pushPose();
      ps.translate(at.x, at.y, at.z);
      ps.mulPose(new Quaternionf().rotationXYZ(age * 0.2F, age * 0.15F, age * 0.1F));
      float s = p.a() * 0.8F;
      ps.scale(s, s, s);
      ps.translate(-0.5, -0.5, -0.5);
      mc.getBlockRenderer().renderSingleBlock(Blocks.MOSS_BLOCK.defaultBlockState(), ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      ps.translate(0.2, 0.2, 0.2);
      ps.scale(0.6F, 0.6F, 0.6F);
      mc.getBlockRenderer().renderSingleBlock(Blocks.SHROOMLIGHT.defaultBlockState(), ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 cam, float s, float rot, int argb) {
      Vec3 f = cam.subtract(c).normalize();
      Vec3 up = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 r = up.cross(f).normalize();
      Vec3 u = f.cross(r).normalize();
      Vec3 r2 = r.scale(Math.cos(rot)).add(u.scale(Math.sin(rot))).scale(s);
      Vec3 u2 = u.scale(Math.cos(rot)).subtract(r.scale(Math.sin(rot))).scale(s);
      VfxDraw.v(vc, m, c.x - r2.x - u2.x, c.y - r2.y - u2.y, c.z - r2.z - u2.z, 0, 1, argb);
      VfxDraw.v(vc, m, c.x + r2.x - u2.x, c.y + r2.y - u2.y, c.z + r2.z - u2.z, 1, 1, argb);
      VfxDraw.v(vc, m, c.x + r2.x + u2.x, c.y + r2.y + u2.y, c.z + r2.z + u2.z, 1, 0, argb);
      VfxDraw.v(vc, m, c.x - r2.x + u2.x, c.y - r2.y + u2.y, c.z - r2.z + u2.z, 0, 0, argb);
   }
}
