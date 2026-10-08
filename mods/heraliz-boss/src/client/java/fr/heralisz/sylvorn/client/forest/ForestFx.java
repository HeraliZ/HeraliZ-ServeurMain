package fr.heralisz.sylvorn.client.forest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.client.fvfx.VfxDraw;
import fr.heralisz.sylvorn.client.fvfx.VfxRenderTypes;
import fr.heralisz.sylvorn.entity.Ground;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Effets 3D de Sylvorn, construits en vrais volumes texturés (écorce, racines, aiguilles) : racines tordues qui percent le sol,
 * racine géante qui s'enroule autour d'un joueur, épicéa entier qui s'abat.
 */
public final class ForestFx {
   private static final BlockState BARK = Blocks.DARK_OAK_WOOD.defaultBlockState(), ROOT = Blocks.MANGROVE_ROOTS.defaultBlockState(),
      LOG = Blocks.SPRUCE_LOG.defaultBlockState(), NEEDLES = Blocks.SPRUCE_LEAVES.defaultBlockState(), MOSS = Blocks.ROOTED_DIRT.defaultBlockState();
   /** Dernière position connue de l'entité saisie (si elle sort de la portée de suivi, la racine reste en place). */
   private static final Map<Integer, Vec3> LAST = new HashMap<>();
   /** Dernière position de la proie marquée (la rune reste où elle l'a perdue). */
   private static final Map<Integer, Vec3> MARKED = new HashMap<>();
   private static final BlockState SOD = Blocks.COARSE_DIRT.defaultBlockState(), CLOD = Blocks.ROOTED_DIRT.defaultBlockState(),
      TURF = Blocks.MOSS_BLOCK.defaultBlockState();

   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszSylvorn.MODID, "textures/fvfx/" + n + ".png");
   }

   private static final ResourceLocation T_RUNE = tex("rune_mark"), T_HOWL = tex("howl_wave"), T_PRINT = tex("hoofprint"), T_ROT = tex("rot_stain");

   private ForestFx() {}

   public static void draw(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf) {
      switch (p.type()) {
         case ROOTS, ROOTGRAB -> { }                                    // plus de racines qui jaillissent du sol
         case TREEFALL -> tree(p, k, ps, buf);
         case MARK -> mark(p, age, k, partial, ps, buf);
         case HOWL -> howl(p, age, k, ps, buf);
         case FURROW -> furrow(p, seed, k, ps, buf);
         case CRATER -> crater(p, seed, k, ps, buf);
         case PRINT -> print(p, k, ps, buf);
         default -> {
         }
      }
   }

   private static float backOut(float x) {
      return 1 + 2.70158F * (float) Math.pow(x - 1, 3) + 1.70158F * (float) Math.pow(x - 1, 2);
   }

   /** Un tronçon texturé de a à b, section carrée r (bloc étiré le long du segment). */
   private static void seg(PoseStack ps, MultiBufferSource buf, Vec3 a, Vec3 b, float r, BlockState st) {
      Vec3 d = b.subtract(a);
      float len = (float) d.length();
      if (len < 1e-3 || r < 0.01F) return;
      Minecraft mc = Minecraft.getInstance();
      ps.pushPose();
      ps.translate(a.x, a.y, a.z);
      ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) d.x / len, (float) d.y / len, (float) d.z / len)));
      ps.scale(r, len + r * 0.5F, r);
      ps.translate(-0.5, -0.1, -0.5);
      int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(a.add(0, 0.5, 0)));
      mc.getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   /** Dessine une racine (tube d'écorce, RootTube) le long de pts, en coordonnées monde (repère : origine o). */
   private static void tube(PoseStack ps, MultiBufferSource buf, Vec3 o, List<Vec3> pts, float r0, float r1, boolean tip, long seed, float glow) {
      if (pts.size() < 2) return;
      Minecraft mc = Minecraft.getInstance();
      List<Vector3f> local = new ArrayList<>(pts.size());
      for (Vec3 q : pts) local.add(new Vector3f((float) (q.x - o.x), (float) (q.y - o.y), (float) (q.z - o.z)));
      ps.pushPose();
      ps.translate(o.x, o.y, o.z);
      int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pts.get(pts.size() / 2).add(0, 0.5, 0)));
      fr.heralisz.sylvorn.client.forest.RootTube.draw(ps.last().pose(), ps.last().normal(), buf, local, r0, r1, light, glow, seed, tip, 1F);
      ps.popPose();
   }

   /** Racine en arc : part du sol, monte en s'écartant, la pointe se recourbe. */
   private static void tendril(PoseStack ps, MultiBufferSource buf, Vec3 base, double ang, float size, float grow, float sink, float r0, RandomSource rng) {
      int n = 7;
      double lean = 0.35 + rng.nextDouble() * 0.5, curl = 0.6 + rng.nextDouble() * 0.8, twist = (rng.nextDouble() - 0.5) * 0.8;
      List<Vec3> pts = new ArrayList<>();
      for (int i = 0; i <= n; i++) {
         float t = i / (float) n * grow;
         double a2 = ang + twist * t;
         double out = size * (lean * t * 1.6 + curl * Math.max(0, t - 0.6) * 1.5);
         double h = size * 3.0 * Math.sin(t * 2.2) - sink * size * 3.5 - (i == 0 ? 0.6 : 0);
         pts.add(base.add(Math.cos(a2) * out, h, Math.sin(a2) * out));
      }
      tube(ps, buf, base, pts, r0 * size * 0.55F, r0 * size * 0.12F, true, rng.nextLong(), 0.35F);
   }

   private static void roots(Vfx.Packet p, float seed, float k, PoseStack ps, MultiBufferSource buf) {
      RandomSource rng = RandomSource.create((long) (seed * 1000) ^ (long) (p.x() * 31 + p.z() * 17));
      float grow = k < 0.12F ? Mth.clamp(backOut(k / 0.12F), 0, 1.15F) : 1F;
      float sink = Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
      Vec3 base = p.pos().add(0, -0.3, 0);
      int count = 4 + rng.nextInt(3);
      for (int i = 0; i < count; i++) {
         double ang = i * Mth.TWO_PI / count + rng.nextDouble() * 0.8;
         Vec3 b = base.add(Math.cos(ang) * 0.3 * p.a(), 0, Math.sin(ang) * 0.3 * p.a());
         tendril(ps, buf, b, ang, p.a() * (0.7F + rng.nextFloat() * 0.5F), grow, sink, 0.34F, rng);
      }
   }

   private static void grab(Vfx.Packet p, float seed, float age, float k, float partial, PoseStack ps, MultiBufferSource buf) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level.getEntity(p.extra());
      Vec3 target = e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.45, 0) : LAST.getOrDefault(p.extra(), p.pos().add(0, 1, 0));
      LAST.put(p.extra(), target);
      if (k >= 0.99F) LAST.remove(p.extra());
      float grow = Mth.clamp(age / 5F, 0, 1);
      float sink = Mth.clamp((k - 0.85F) / 0.15F, 0, 1);
      Vec3 a = p.pos().add(0, -0.6 - sink * 3, 0);
      Vec3 tip = a.add(target.subtract(a).scale(grow));
      double wob = Math.sin(age * 0.35F) * 0.4;
      Vec3 ctrl = a.add(tip.subtract(a).scale(0.5)).add(0.9 + wob, 0.6, -0.6 - wob);
      List<Vec3> main = new ArrayList<>();
      int n = 10;
      for (int i = 0; i <= n; i++) {
         double t = i / (double) n;
         main.add(a.scale((1 - t) * (1 - t)).add(ctrl.scale(2 * t * (1 - t))).add(tip.scale(t * t)));
      }
      long sd = (long) (seed * 7919);
      // spirale serrée autour de la victime : elle prolonge la racine
      if (grow >= 1 && sink < 1) {
         Vec3 c = target.add(0, -0.9, 0);
         int m = 18;
         for (int i = 1; i <= m; i++) {
            double t = i / (double) m, an = t * Mth.TWO_PI * 2.3 + age * 0.05, rr = 0.52 + 0.1 * Math.sin(t * 9);
            main.add(c.add(Math.cos(an) * rr, t * 1.9, Math.sin(an) * rr));
         }
         tube(ps, buf, a, main, 0.5F, 0.08F, true, sd, 0.5F);
      } else {
         tube(ps, buf, a, main, 0.5F, 0.2F, true, sd, 0.5F);
      }
      // radicelles secondaires autour du point d'ancrage
      RandomSource rng = RandomSource.create((long) (seed * 997));
      for (int i = 0; i < 4; i++) tendril(ps, buf, p.pos().add(0, -0.3, 0), i * 1.57 + rng.nextDouble(), 0.9F, grow, sink, 0.3F, rng);
   }

   /** Épicéa : surgit du sol (0 → 0,1), bascule en accélérant jusqu'à toucher le sol (0,53), reste couché puis s'enfonce. */
   private static void tree(Vfx.Packet p, float k, PoseStack ps, MultiBufferSource buf) {
      Minecraft mc = Minecraft.getInstance();
      float h = p.a();
      float rise = Mth.clamp(k / 0.1F, 0, 1);
      float fk = Mth.clamp((k - 0.1F) / 0.43F, 0, 1);
      float tilt = 90F * fk * fk * fk;
      float sink = Mth.clamp((k - 0.8F) / 0.2F, 0, 1);
      Vec3 d = p.dir().lengthSqr() > 1e-6 ? new Vec3(p.dx(), 0, p.dz()).normalize() : new Vec3(1, 0, 0);
      ps.pushPose();
      ps.translate(p.x(), p.y() - (1 - rise) * h - sink * 1.2, p.z());
      // axe de bascule horizontal, perpendiculaire à la direction de chute (le sommet part vers d)
      ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(tilt), (float) d.z, 0, (float) -d.x));
      int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p.pos().add(0, 1, 0)));
      int ih = (int) h;
      for (int y = 0; y < ih; y++) block(ps, buf, -0.5, y, -0.5, LOG, light);
      for (int y = (int) (ih * 0.35); y <= ih; y++) {
         float kk = (y - ih * 0.35F) / (ih * 0.65F);
         int r = Math.max(0, Math.round(2.6F * (1 - kk) + ((y & 1) == 0 ? 0.4F : -0.4F)));
         for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
               if ((dx == 0 && dz == 0 && y < ih) || Math.abs(dx) + Math.abs(dz) > r + (r > 1 ? 1 : 0)) continue;
               block(ps, buf, dx - 0.5, y, dz - 0.5, NEEDLES, light);
            }
      }
      block(ps, buf, -0.5, ih + 1, -0.5, NEEDLES, light);
      ps.popPose();
   }

   // ------------------------------------------------------------------ marque du chasseur
   /** Rune de bois pâle qui colle aux pieds de la proie et tourne ; verrouillée, elle rougit, se resserre et pulse de plus en plus vite. */
   private static void mark(Vfx.Packet p, float age, float k, float partial, PoseStack ps, MultiBufferSource buf) {
      Minecraft mc = Minecraft.getInstance();
      boolean locked = p.b() > 0.5F;
      Vec3 c = p.pos();
      if (!locked && p.extra() != 0) {
         Entity e = mc.level.getEntity(p.extra());
         if (e != null) {
            Vec3 ep = e.getPosition(partial);
            double gy = fr.heralisz.sylvorn.entity.Ground.y(mc.level, ep.x, ep.y, ep.z);
            c = new Vec3(ep.x, Math.min(ep.y, gy), ep.z);
            MARKED.put(p.extra(), c);
         } else {
            c = MARKED.getOrDefault(p.extra(), c);
         }
         if (k >= 0.99F) MARKED.remove(p.extra());
      }
      Matrix4f m = ps.last().pose();
      float in = Mth.clamp(age / 6F, 0, 1);
      float r = p.a() * (locked ? 1.0F - 0.12F * k : 0.6F + 0.4F * backOut(in));
      float pulse = locked ? 0.6F + 0.4F * Mth.sin(age * (0.6F + 2.2F * k)) : 0.75F + 0.25F * Mth.sin(age * 0.2F);
      int col = locked ? 0xFF4A1E : 0xC8F08A;
      float out = locked ? 1 : 1 - Mth.clamp((k - 0.9F) / 0.1F, 0, 1);
      double y = c.y + 0.06;
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), m, c.x, y - 0.02, c.z, r * 1.15, 0, VfxDraw.argb(0xFFFFFF, 0.55F * in * out));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RUNE)), m, c.x, y, c.z, r, age * (locked ? 0.09F : 0.035F), VfxDraw.argb(col, pulse * in * out));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RUNE)), m, c.x, y + 0.01, c.z, r * 0.55, -age * 0.06F, VfxDraw.argb(col, 0.6F * pulse * in * out));
      // colonne de lueur qui monte de la marque (on la voit de loin, derrière les arbres)
      VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_HOWL)), m, c.x, c.y, c.z, r * 0.35, locked ? 3.5 : 2.2, 16, 2, -age * 0.05F,
         VfxDraw.argb(col, 0.35F * in * out * pulse), VfxDraw.argb(col, 0));
   }

   // ------------------------------------------------------------------ hurlement
   /** Trois coquilles d'onde sonore qui s'éloignent de la gueule en s'ouvrant, laissant la poussière trembler au sol. */
   private static void howl(Vfx.Packet p, float age, float k, PoseStack ps, MultiBufferSource buf) {
      Matrix4f m = ps.last().pose();
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      if (d.lengthSqr() < 1e-6) return;
      d = d.normalize();
      double a0 = Math.atan2(d.z, d.x), half = Math.toRadians(p.b() / 2);
      float range = p.a();
      float speed = range / Math.max(6F, p.duration() * 0.55F);
      VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(T_HOWL));
      for (int i = 0; i < 4; i++) {
         float f = age * speed - i * 2.6F;
         if (f <= 0.4F || f >= range) continue;
         float al = (1 - f / range) * (1 - f / range) * (i == 0 ? 1F : 0.7F);
         double hh = 0.7 + f * 0.28, cy = p.y() - f * 0.12;
         int seg = 22;
         for (int s = 0; s < seg; s++) {
            double t0 = a0 - half + 2 * half * s / seg, t1 = a0 - half + 2 * half * (s + 1) / seg;
            float u0 = (float) s / seg, u1 = (float) (s + 1) / seg;
            double x0 = p.x() + Math.cos(t0) * f, z0 = p.z() + Math.sin(t0) * f, x1 = p.x() + Math.cos(t1) * f, z1 = p.z() + Math.sin(t1) * f;
            int c = VfxDraw.argb(0xE6F8D2, al);
            VfxDraw.v(vc, m, x0, cy - hh, z0, u0, 1, c);
            VfxDraw.v(vc, m, x1, cy - hh, z1, u1, 1, c);
            VfxDraw.v(vc, m, x1, cy + hh, z1, u1, 0, c);
            VfxDraw.v(vc, m, x0, cy + hh, z0, u0, 0, c);
         }
      }
   }

   // ------------------------------------------------------------------ sillon de charge
   /** Mottes de terre arrachées et retournées de part et d'autre du passage, qui se tassent ensuite. */
   private static void furrow(Vfx.Packet p, float seed, float k, PoseStack ps, MultiBufferSource buf) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      if (d.lengthSqr() < 1e-6) return;
      d = d.normalize();
      Vec3 side = new Vec3(-d.z, 0, d.x);
      float rise = Mth.clamp(k / 0.08F, 0, 1);
      float sink = Mth.clamp((k - 0.7F) / 0.3F, 0, 1);
      RandomSource rng = RandomSource.create((long) (seed * 7919) ^ (long) (p.x() * 13 + p.z() * 7));
      VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), ps.last().pose(), p.x(), p.y() + 0.03, p.z(), d.x, d.z, p.a(), 2.2,
         0, 1, VfxDraw.argb(0x3A2A1E, 0.8F * (1 - sink)));
      int n = Math.max(2, (int) (p.a() / 0.7F));
      for (int i = 0; i < n; i++) {
         for (int sd = -1; sd <= 1; sd += 2) {
            Vec3 at = p.pos().add(d.scale(i * p.a() / n + rng.nextDouble() * 0.3)).add(side.scale(sd * (0.9 + rng.nextDouble() * 0.5)));
            float tilt = (float) (sd * (25 + rng.nextDouble() * 30)) * backOut(rise);
            float s = 0.45F + rng.nextFloat() * 0.35F;
            ps.pushPose();
            ps.translate(at.x, at.y - 0.25 - sink * 0.6, at.z);
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(tilt), (float) d.x, 0, (float) d.z));
            ps.mulPose(new Quaternionf().rotationY((float) (rng.nextDouble() * Mth.TWO_PI)));
            ps.scale(s, s * 0.55F, s * 0.8F);
            ps.translate(-0.5, 0, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 0.5, 0)));
            mc.getBlockRenderer().renderSingleBlock((i + sd) % 3 == 0 ? CLOD : SOD, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
         }
      }
   }

   // ------------------------------------------------------------------ cratère
   /** Anneau de plaques de sol arrachées par la réception, dressées vers l'extérieur, qui retombent lentement. */
   private static void crater(Vfx.Packet p, float seed, float k, PoseStack ps, MultiBufferSource buf) {
      Minecraft mc = Minecraft.getInstance();
      float rise = k < 0.1F ? backOut(k / 0.1F) : 1F;
      float sink = Mth.clamp((k - 0.72F) / 0.28F, 0, 1);
      RandomSource rng = RandomSource.create((long) (seed * 104729));
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_ROT)), ps.last().pose(), p.x(), p.y() + 0.03, p.z(), p.a() * 0.9, seed,
         VfxDraw.argb(0xFFFFFF, 0.9F * (1 - sink)));
      int n = 12 + (int) (p.a() * 2);
      for (int i = 0; i < n; i++) {
         double ang = i * Mth.TWO_PI / n + rng.nextDouble() * 0.2;
         double r = p.a() * (0.62 + rng.nextDouble() * 0.2);
         float s = 0.7F + rng.nextFloat() * 0.5F;
         ps.pushPose();
         ps.translate(p.x() + Math.cos(ang) * r, p.y() - 0.35 - sink * 0.9, p.z() + Math.sin(ang) * r);
         ps.mulPose(new Quaternionf().rotationY((float) (-ang)));
         ps.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-(28 + rng.nextDouble() * 22) * rise)));
         ps.scale(s * 0.5F, s * 0.9F, s * 1.1F);
         ps.translate(-0.5, 0, -0.5);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p.pos().add(0, 1, 0)));
         mc.getBlockRenderer().renderSingleBlock(i % 4 == 0 ? TURF : i % 2 == 0 ? CLOD : SOD, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
   }

   // ------------------------------------------------------------------ empreinte
   private static void print(Vfx.Packet p, float k, PoseStack ps, MultiBufferSource buf) {
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      // disque texturé : le haut de la texture (pointe du sabot) suit la direction de marche
      float rot = d.lengthSqr() < 1e-6 ? 0 : (float) Math.atan2(d.x, -d.z);
      float in = Mth.clamp(k * 12, 0, 1), out = 1 - Mth.clamp((k - 0.55F) / 0.45F, 0, 1);
      VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_PRINT)), ps.last().pose(), p.x(), p.y() + 0.03, p.z(), p.a(), rot,
         VfxDraw.argb(0xFFFFFF, 0.9F * in * out));
   }

   private static void block(PoseStack ps, MultiBufferSource buf, double x, double y, double z, BlockState st, int light) {
      ps.pushPose();
      ps.translate(x, y, z);
      Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }
}
