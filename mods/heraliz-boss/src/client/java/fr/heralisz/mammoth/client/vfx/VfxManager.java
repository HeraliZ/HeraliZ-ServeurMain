package fr.heralisz.mammoth.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.client.Cinematic;
import fr.heralisz.mammoth.client.Shake;
import fr.heralisz.mammoth.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effets d'Ymirak côté client : alertes au sol (contour net + remplissage qui avance jusqu'à l'impact), ondes de givre,
 * souffle de blizzard, stalactites qui tombent, bloc de glace lancé, cage de pics, nova, tourbillon, débris 3D.
 */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID, value = Dist.CLIENT)
public final class VfxManager {
   private static final List<Fx> ACTIVE = new ArrayList<>();
   private static final List<Debris> DEBRIS = new ArrayList<>();
   private static final RandomSource RNG = RandomSource.create();

   private static ResourceLocation tex(String n) {
      return new ResourceLocation(HeraliszMammoth.MODID, "textures/vfx/" + n + ".png");
   }

   private static final ResourceLocation T_RING = tex("telegraph_ring"), T_FILL = tex("telegraph_fill"), T_BAND = tex("telegraph_band"),
      T_CHEV = tex("telegraph_chevron"), T_GROUND = tex("ring_ground"), T_WALL = tex("ring_wall"), T_FLASH = tex("flash"), T_GLOW = tex("glow"),
      T_FROST = tex("frost"), T_CRYSTAL = tex("crystal"), T_TRAIL = tex("trail"), T_CRACK = tex("crack"), T_NOVA = tex("nova"), T_VORTEX = tex("vortex");

   /** Alerte : bleu de glace vif, qui blanchit à l'impact. */
   private static final int WARN = 0x2AA8FF, WARN_HOT = 0xE8F8FF;

   private record Fx(Vfx.Packet p, long start, float seed) {}

   private static final class Debris {
      Vec3 pos, vel;
      float rx, ry, rz, vrx, vry, vrz, size;
      int life, age;
      BlockState state;
   }

   private VfxManager() {}

   public static void receive(Vfx.Packet p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;
      switch (p.type()) {
         case SHAKE -> {
            double d = mc.player.position().distanceTo(p.pos());
            if (d < p.b()) Shake.add(p.a() * (float) (1 - d / p.b()), p.duration());
         }
         case CINEMATIC -> {
            if (mc.player.position().distanceTo(p.pos()) < 100) Cinematic.start(p.extra(), (int) p.b(), p.duration());
         }
         case DEBRIS -> spawnDebris(p);
         case SCREEN -> {
            if (p.color() == 1) fr.heralisz.mammoth.client.FrostScreen.hit(p.extra() / 100F);
            else fr.heralisz.mammoth.client.FrostScreen.add(p.a(), p.b(), p.duration());
         }
         case BREATH -> breath(mc, p);
         default -> ACTIVE.add(new Fx(p, mc.level.getGameTime(), (float) Math.floorMod((long) (p.x() * 131 + p.z() * 71), 628) / 100F));
      }
   }

   /** Souffle : des volutes de neige et de givre fusent dans le cône. */
   private static void breath(Minecraft mc, Vfx.Packet p) {
      Vec3 d = p.dir().normalize();
      double half = Math.toRadians(p.b() / 2);
      for (int i = 0; i < 26; i++) {
         double a = (RNG.nextDouble() * 2 - 1) * half;
         Vec3 dd = d.yRot((float) a).add(0, (RNG.nextDouble() - 0.5) * 0.25, 0).normalize();
         double sp = 0.6 + RNG.nextDouble() * 0.9;
         Vec3 o = p.pos().add(dd.scale(RNG.nextDouble() * 1.5));
         mc.level.addParticle(i % 3 == 0 ? fr.heralisz.mammoth.fx.Particles.MIST.get() : fr.heralisz.mammoth.fx.Particles.FLAKE.get(), o.x, o.y, o.z, dd.x * sp, dd.y * sp, dd.z * sp);
      }
   }

   private static void spawnDebris(Vfx.Packet p) {
      BlockState state = Block.stateById(p.extra());
      if (state.isAir()) return;
      int n = Math.min(70, p.color());
      for (int i = 0; i < n; i++) {
         Debris d = new Debris();
         double a = RNG.nextDouble() * Mth.TWO_PI, r = Math.sqrt(RNG.nextDouble()) * p.a();
         d.pos = p.pos().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
         double out = p.b() * (0.25 + RNG.nextDouble() * 0.5);
         d.vel = new Vec3(Math.cos(a) * out, p.b() * (0.6 + RNG.nextDouble() * 0.8), Math.sin(a) * out).add(p.dir().scale(p.b()));
         d.vrx = (RNG.nextFloat() - 0.5F) * 900;
         d.vry = (RNG.nextFloat() - 0.5F) * 900;
         d.vrz = (RNG.nextFloat() - 0.5F) * 900;
         d.size = 0.2F + RNG.nextFloat() * 0.38F;
         d.life = 40 + RNG.nextInt(40);
         d.state = state;
         DEBRIS.add(d);
      }
   }

   /** Boss qui s'éveillent dans leur glace (yeux à travers la prison), relevés une fois par tick. */
   private static java.util.List<fr.heralisz.mammoth.entity.Mammoth> waking = java.util.List.of();
   private static long wakingTick = Long.MIN_VALUE;

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload e) {
      ACTIVE.clear();
      DEBRIS.clear();
      waking = java.util.List.of();
      wakingTick = Long.MIN_VALUE;
   }

   private static long lastNanos;

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      // recherche des boss qui s'éveillent : une fois par tick (avant : à chaque image, dans une boîte de 240 blocs)
      long gt = mc.level.getGameTime();
      if (gt != wakingTick) {
         wakingTick = gt;
         waking = mc.player == null ? java.util.List.of()
            : mc.level.getEntitiesOfClass(fr.heralisz.mammoth.entity.Mammoth.class, mc.player.getBoundingBox().inflate(120), fr.heralisz.mammoth.entity.Mammoth::eyesInIce);
      }
      if (ACTIVE.isEmpty() && DEBRIS.isEmpty() && waking.isEmpty()) return;
      long now = System.nanoTime();
      float dt = lastNanos == 0 ? 0.016F : Mth.clamp((now - lastNanos) / 1e9F, 0.001F, 0.05F);
      lastNanos = now;
      if (mc.isPaused()) dt = 0;
      float partial = e.getPartialTick();
      double time = mc.level.getGameTime() + partial;
      Vec3 cam = e.getCamera().getPosition();
      PoseStack ps = e.getPoseStack();
      ps.pushPose();
      ps.translate(-cam.x, -cam.y, -cam.z);
      Matrix4f m = ps.last().pose();
      MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
      Iterator<Fx> it = ACTIVE.iterator();
      while (it.hasNext()) {
         Fx fx = it.next();
         float age = (float) (time - fx.start);
         if (age > fx.p.duration()) {
            it.remove();
            continue;
         }
         float k = Mth.clamp(age / Math.max(1, fx.p.duration()), 0, 1);
         if (fx.p.type() == Vfx.Type.BOULDER) boulder(mc, fx, age, k, ps, buf);
         else draw(mc, fx, age, k, m, buf, cam);
      }
      for (fr.heralisz.mammoth.entity.Mammoth w : waking) if (!w.isRemoved()) wakingEyes(w, partial, m, buf, cam);
      buf.endBatch();
      renderDebris(mc, ps, buf, dt);
      buf.endBatch();
      ps.popPose();
   }

   private static void renderDebris(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, float dt) {
      Iterator<Debris> it = DEBRIS.iterator();
      while (it.hasNext()) {
         Debris d = it.next();
         d.age++;
         if (d.age > d.life * 3) {
            it.remove();
            continue;
         }
         d.vel = d.vel.add(0, -22 * dt, 0).scale(Math.pow(0.6, dt));
         Vec3 next = d.pos.add(d.vel.scale(dt));
         BlockPos bp = BlockPos.containing(next);
         if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) {
            if (d.vel.y < 0) {
               next = new Vec3(next.x, bp.getY() + 1.0 + d.size * 0.3, next.z);
               d.vel = new Vec3(d.vel.x * 0.45, -d.vel.y * 0.3, d.vel.z * 0.45);
               d.vrx *= 0.5F;
               d.vry *= 0.5F;
               d.vrz *= 0.5F;
            } else {
               d.vel = new Vec3(-d.vel.x * 0.3, d.vel.y, -d.vel.z * 0.3);
               next = d.pos;
            }
         }
         d.pos = next;
         d.rx += d.vrx * dt;
         d.ry += d.vry * dt;
         d.rz += d.vrz * dt;
         float k = d.age / (d.life * 3F);
         float s = d.size * (k > 0.75F ? (1 - k) / 0.25F : 1F);
         ps.pushPose();
         ps.translate(d.pos.x, d.pos.y, d.pos.z);
         ps.mulPose(Axis.XP.rotationDegrees(d.rx));
         ps.mulPose(Axis.YP.rotationDegrees(d.ry));
         ps.mulPose(Axis.ZP.rotationDegrees(d.rz));
         ps.scale(s, s, s);
         ps.translate(-0.5, -0.5, -0.5);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos));
         mc.getBlockRenderer().renderSingleBlock(d.state, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
   }

   /** Bloc de glace lancé en cloche : il tourne sur lui-même, sème de la neige. */
   private static void boulder(Minecraft mc, Fx fx, float age, float k, PoseStack ps, MultiBufferSource buf) {
      Vfx.Packet p = fx.p;
      Vec3 from = p.pos(), to = from.add(p.dir());
      double h = 6 + p.dir().horizontalDistance() * 0.25;
      Vec3 at = from.add(p.dir().scale(k)).add(0, h * 4 * k * (1 - k), 0);
      float s = p.a();
      ps.pushPose();
      ps.translate(at.x, at.y, at.z);
      ps.mulPose(Axis.XP.rotationDegrees(age * 23));
      ps.mulPose(Axis.YP.rotationDegrees(age * 17 + fx.seed * 50));
      for (int i = 0; i < 3; i++) {
         ps.pushPose();
         float o = (i - 1) * 0.35F * s;
         ps.translate(o, (i % 2) * 0.3F * s, -o * 0.5F);
         float ss = s * (i == 1 ? 1F : 0.7F);
         ps.scale(ss, ss, ss);
         ps.translate(-0.5, -0.5, -0.5);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
         mc.getBlockRenderer().renderSingleBlock(i == 1 ? fr.heralisz.mammoth.dungeon.ModBlocks.ICE_CHUNK.get().defaultBlockState()
            : fr.heralisz.mammoth.dungeon.ModBlocks.GLACIER_GLOW.get().defaultBlockState(), ps, buf, light,
            OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }
      ps.popPose();
      if (RNG.nextInt(2) == 0) mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), at.x, at.y, at.z, 0, -0.02, 0);
   }

   private static void draw(Minecraft mc, Fx fx, float age, float k, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      Vfx.Packet p = fx.p;
      double x = p.x(), y = p.y() + 0.04, z = p.z();
      int col = p.color() & 0xFFFFFF;
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      if (d.lengthSqr() > 1e-6) d = d.normalize();
      float in = Mth.clamp(age / 4F, 0, 1);
      float endFlash = Mth.clamp((k - 0.88F) / 0.12F, 0, 1);
      switch (p.type()) {
         case CIRCLE -> {
            float r = p.a();
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, r, 0, VfxDraw.argb(c, 0.18F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, r * ease(k), 0, VfxDraw.argb(c, 0.45F * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r, 0, VfxDraw.argb(c, (0.85F + 0.15F * Mth.sin(age)) * in));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.01, z, Math.max(0.05, r * ease(k)), 0, VfxDraw.argb(WARN_HOT, 0.7F * in));
         }
         case LINE -> {
            float len = p.a(), w = p.b();
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_BAND)), m, x, y, z, d.x, d.z, len, w, 0, 1, VfxDraw.argb(c, 0.45F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, d.x, d.z, len * ease(k), w * 0.96, 0.5F, 0.5F,
               VfxDraw.argb(c, 0.42F * in));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CHEV)), m, x, y + 0.008, z, d.x, d.z, len, w, -age * 0.06F, len / w - age * 0.06F,
               VfxDraw.argb(WARN_HOT, 0.55F * in));
         }
         case CONE -> {
            float r = p.a(), half = (float) Math.toRadians(p.b() / 2);
            double a = Math.atan2(d.z, d.x);
            int c = lerpColor(WARN, WARN_HOT, endFlash);
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y, z, 0.2, r, a - half, a + half, 32, VfxDraw.argb(c, 0.18F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.decal(T_FILL)), m, x, y + 0.004, z, 0.2, r * ease(k), a - half, a + half, 32, VfxDraw.argb(c, 0.42F * in));
            VfxDraw.sector(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.008, z, r * 0.9, r, a - half, a + half, 32, VfxDraw.argb(c, 0.9F * in));
         }
         case SHOCKWAVE -> {
            float e = 1 - (1 - k) * (1 - k);
            double r = 0.5 + (p.a() - 0.5) * e;
            float al = (1 - k) * (1 - k);
            VfxDraw.annulus(buf.getBuffer(VfxRenderTypes.glow(T_GROUND)), m, x, y, z, Math.max(0, r - 2.2), r, 64, 0, VfxDraw.argb(col, al));
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, y, z, r * 0.98, p.b() * (1 - k * 0.6), 64, 10, -age * 0.03F,
               VfxDraw.argb(col, al * 0.8F), VfxDraw.argb(col, 0));
            if (k < 0.5F && RNG.nextInt(2) == 0) {
               double a = RNG.nextDouble() * Mth.TWO_PI;
               mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r, Math.cos(a) * 0.2, 0.1, Math.sin(a) * 0.2);
            }
         }
         case FLASH -> {
            float fl = 1 - k;
            billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, p.pos(), cam, p.a() * (0.5F + 0.5F * k), fx.seed, VfxDraw.argb(col, fl * fl));
         }
         case FROST -> {
            float grow = ease(Mth.clamp(k * 5, 0, 1));
            float al = 1 - Mth.clamp((k - 0.7F) / 0.3F, 0, 1);
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.decal(T_FROST)), m, x, y - 0.015, z, p.a() * (0.3 + 0.7 * grow), fx.seed, VfxDraw.argb(0xFFFFFF, 0.6F * al));
         }
         case SPIKES -> spikes(fx, k, m, buf, x, y, z, p.a(), 5, 0.0);
         case CAGE -> {
            // anneau de pics autour du prisonnier : jaillit, tient, se fend et éclate
            int n = 11;
            for (int i = 0; i < n; i++) {
               double a = i * Mth.TWO_PI / n + fx.seed;
               double r = p.a();
               spikes(new Fx(p, fx.start, fx.seed + i * 1.7F), k, m, buf, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1.1F, 2, -Math.toDegrees(a));
            }
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_RING)), m, x, y + 0.01, z, p.a() + 0.2, 0, VfxDraw.argb(0x8AD8FF, 0.6F * (1 - k)));
         }
         case ICICLE -> {
            float fall = Mth.clamp(k / 0.95F, 0, 1);
            double hgt = 22 * (1 - fall * fall);
            float al = k > 0.97F ? (1 - k) / 0.03F : 1;
            PoseStackLite.push(m, x, y + hgt, z, fx.seed * 57F, 180F);
            Matrix4f mm = PoseStackLite.top();
            // stalactite qui tombe pointe en bas (le repère est retourné) : même éclat que les pics du sol
            Matrix4f mi = new Matrix4f(mm).translate(0, (float) (-4.5 * p.a()), 0);
            VfxDraw.shard(buf.getBuffer(VfxRenderTypes.glass(T_CRYSTAL)), mi, 4.5 * p.a(), 0.55 * p.a(), 6, fx.seed * 11F, VfxDraw.argb(0xE4F4FF, 0.92F * al));
            VfxDraw.shard(buf.getBuffer(VfxRenderTypes.glow(T_CRYSTAL)), mi, 4.5 * p.a(), 0.57 * p.a(), 6, fx.seed * 11F, VfxDraw.argb(0x2A5A90, 0.35F * al));
            PoseStackLite.pop();
         }
         case TRAIL -> {
            float al = 1 - k;
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_TRAIL)), m, x, y - 0.015, z, d.x, d.z, p.a(), p.b(), 0, p.a() / p.b() / 2,
               VfxDraw.argb(0xD8F0FF, al));
         }
         case CRACK -> {
            float reveal = Mth.clamp(k * 6, 0, 1);
            float al = 1 - Mth.clamp((k - 0.55F) / 0.45F, 0, 1);
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.decal(T_CRACK)), m, x, y - 0.01, z, d.x, d.z, p.a() * reveal, p.b(), 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(0x06121E, al * 0.9F));
            VfxDraw.strip(buf.getBuffer(VfxRenderTypes.glow(T_CRACK)), m, x, y, z, d.x, d.z, p.a() * reveal, p.b() * 0.7, 0, p.a() / p.b() / 3 * reveal,
               VfxDraw.argb(col, al));
         }
         case VORTEX -> {
            float al = Math.min(in, 1 - Mth.clamp((k - 0.85F) / 0.15F, 0, 1));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_VORTEX)), m, x, y + 0.02, z, p.a(), -age * 0.12F, VfxDraw.argb(0x6AC0FF, 0.55F * al));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_VORTEX)), m, x, y + 0.03, z, p.a() * 0.6, -age * 0.2F + 1, VfxDraw.argb(0xE0F6FF, 0.4F * al));
            for (int i = 0; i < 3; i++) {
               double a = RNG.nextDouble() * Mth.TWO_PI, r = p.a() * (0.4 + RNG.nextDouble() * 0.6);
               double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
               mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), px, y + 0.3 + RNG.nextDouble() * 2, pz, (x - px) * 0.06 - Math.sin(a) * 0.2, 0.02,
                  (z - pz) * 0.06 + Math.cos(a) * 0.2);
            }
         }
         case NOVA -> {
            float e = 1 - (float) Math.pow(1 - k, 3);
            double r = p.a() * e;
            float al = (1 - k) * (1 - k);
            VfxDraw.sphere(buf.getBuffer(VfxRenderTypes.glow(T_NOVA)), m, x, p.y(), z, r, 24, age * 0.02F, VfxDraw.argb(col, 0.5F * al));
            VfxDraw.sphere(buf.getBuffer(VfxRenderTypes.glass(T_GLOW)), m, x, p.y(), z, r * 0.92, 20, -age * 0.03F, VfxDraw.argb(0xDDF4FF, 0.25F * al));
            float fl = Mth.clamp(1 - age / 6F, 0, 1);
            if (fl > 0) billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, p.pos(), cam, p.a() * 0.4F, 0, VfxDraw.argb(0xF0FAFF, fl * 0.9F));
         }
         case BEAM -> {
            // colonne de lumière froide : fondu d'entrée (2 s) et de sortie (2 s), la neige tombe dedans
            float dur = p.duration();
            float al = Math.min(Mth.clamp(age / 40F, 0, 1), Mth.clamp((dur - age) / 40F, 0, 1));
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.07F);
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, p.y(), z, p.a(), p.b(), 32, 3, age * 0.004F, VfxDraw.argb(col, 0.28F * al * pulse),
               VfxDraw.argb(col, 0.05F * al));
            VfxDraw.wall(buf.getBuffer(VfxRenderTypes.glow(T_WALL)), m, x, p.y(), z, p.a() * 0.45, p.b(), 24, 2, -age * 0.006F,
               VfxDraw.argb(0xFFFFFF, 0.35F * al * pulse), VfxDraw.argb(0xFFFFFF, 0.08F * al));
            VfxDraw.disc(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), m, x, p.y() + 0.03, z, p.a() * 1.6, 0, VfxDraw.argb(col, 0.35F * al));
            if (RNG.nextInt(3) == 0) {
               double a = RNG.nextDouble() * Mth.TWO_PI, r = Math.sqrt(RNG.nextDouble()) * p.a();
               mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x + Math.cos(a) * r, p.y() + p.b() * RNG.nextDouble(), z + Math.sin(a) * r,
                  0, -0.03, 0);
            }
         }
         case EYES -> {
            // « pouf » : elle s'allume en trois ticks, sursaute, tient en palpitant, puis s'éteint
            float on = Mth.clamp(age / 3F, 0, 1), off = Mth.clamp((p.duration() - age) / 15F, 0, 1);
            float pop = 1F + 1.2F * Math.max(0, 1 - age / 6F);
            float pulse = 0.85F + 0.15F * Mth.sin(age * 0.6F);
            float al = on * off;
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p.pos(), cam, p.a() * 2.6F * pop, 0, VfxDraw.argb(col, 0.55F * al * pulse));
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_FLASH)), m, p.pos(), cam, p.a() * pop, age * 0.02F, VfxDraw.argb(0xFF6A40, 0.95F * al));
            billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p.pos(), cam, p.a() * 0.4F, 0, VfxDraw.argb(0xFFE0C0, al));
         }
         case HEART -> {
            // il monte en battant (deux coups rapprochés), puis enfle et éclate en lumière
            float rise = ease(Mth.clamp(k / 0.8F, 0, 1));
            Vec3 at = p.pos().add(p.dir().scale(rise));
            float ph = (age % 24) / 24F;
            float beat = (float) (Math.exp(-Math.pow((ph - 0.1) / 0.05, 2)) + 0.7 * Math.exp(-Math.pow((ph - 0.28) / 0.05, 2)));
            float burst = Mth.clamp((k - 0.9F) / 0.1F, 0, 1);
            float s = p.a() * (1 + 0.35F * beat + burst * 3F);
            float al = 1 - burst * burst;
            billboard(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), m, at, cam, s * 2.4F, 0, VfxDraw.argb(col, 0.55F * al));
            billboard(buf.getBuffer(VfxRenderTypes.glow(T_FLASH)), m, at, cam, s, age * 0.03F, VfxDraw.argb(0xFFFFFF, 0.9F * al));
            if (RNG.nextInt(2) == 0) mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.AURORA.get(), at.x + (RNG.nextDouble() - 0.5) * s,
               at.y + (RNG.nextDouble() - 0.5) * s, at.z + (RNG.nextDouble() - 0.5) * s, 0, 0.02, 0);
         }
         default -> {
         }
      }
   }

   /** Grappe de cristaux : jaillit (effet ressort), reste, puis se fend et disparaît. */
   private static void spikes(Fx fx, float k, Matrix4f m, MultiBufferSource buf, double x, double y, double z, float a, int n, double lean) {
      float rise = k < 0.12F ? backOut(k / 0.12F) : 1F;
      float die = Mth.clamp((k - 0.82F) / 0.18F, 0, 1);
      float sc = a * rise * (1 - die * 0.6F);
      RandomSource r = RandomSource.create((long) (fx.seed * 1000));
      VertexConsumer body = buf.getBuffer(VfxRenderTypes.glass(T_CRYSTAL));
      VertexConsumer glow = buf.getBuffer(VfxRenderTypes.glow(T_CRYSTAL));
      for (int i = 0; i < n; i++) {
         double ang = r.nextDouble() * Mth.TWO_PI, off = i == 0 ? 0 : 0.35 + r.nextDouble() * 0.5;
         double h = (i == 0 ? 2.4 : 0.9 + r.nextDouble() * 1.1) * sc, rad = (i == 0 ? 0.45 : 0.2 + r.nextDouble() * 0.15) * a;
         float lx = (float) (Math.cos(ang) * off * a), lz = (float) (Math.sin(ang) * off * a);
         float tilt = i == 0 ? (float) (lean == 0 ? 0 : 8) : (float) (18 + r.nextDouble() * 20);
         PoseStackLite.push(m, x + lx, y - 0.1, z + lz, lean != 0 && i == 0 ? (float) lean : (float) Math.toDegrees(ang), tilt);
         Matrix4f mm = PoseStackLite.top();
         float sd = fx.seed * 13F + i * 3.1F;
         VfxDraw.shard(body, mm, h * 1.3, rad, 5 + (i % 2), sd, VfxDraw.argb(0xE4F4FF, 0.9F * (1 - die)));
         VfxDraw.shard(glow, mm, h * 1.3, rad * 1.03, 5 + (i % 2), sd, VfxDraw.argb(0x2A5A90, 0.35F * (1 - die)));
         PoseStackLite.pop();
      }
   }

   private static float backOut(float x) {
      return 1 + 2.70158F * (float) Math.pow(x - 1, 3) + 1.70158F * (float) Math.pow(x - 1, 2);
   }

   /** Mini pile de matrices pour poser les cristaux (translation + rotation Y puis inclinaison X). */
   private static final class PoseStackLite {
      private static final java.util.ArrayDeque<Matrix4f> S = new java.util.ArrayDeque<>();

      static void push(Matrix4f base, double x, double y, double z, float yawDeg, float tiltDeg) {
         S.push(new Matrix4f(base).translate((float) x, (float) y, (float) z).rotateY((float) Math.toRadians(yawDeg)).rotateX((float) Math.toRadians(tiltDeg)));
      }

      static Matrix4f top() {
         return S.peek();
      }

      static void pop() {
         S.pop();
      }
   }

   private static float ease(float k) {
      return k * k * (3 - 2 * k);
   }

   /** Les yeux d'Ymirak à travers la glace de sa prison : lus sur l'état du boss (aucun paquet), dessinés par-dessus tout. */
   private static void wakingEyes(fr.heralisz.mammoth.entity.Mammoth w, float partial, Matrix4f m, MultiBufferSource buf, Vec3 cam) {
      float age = w.actionTime() - fr.heralisz.mammoth.entity.Mammoth.EYES_FROM + partial;
      float left = fr.heralisz.mammoth.entity.Mammoth.FREE_AT - (w.actionTime() + partial);
      float on = Mth.clamp(age / 3F, 0, 1), off = Mth.clamp(left / 4F, 0, 1);
      float pop = 1F + 1.4F * Math.max(0, 1 - age / 8F);
      float beat = 0.8F + 0.2F * Mth.sin(age * 0.45F);
      float al = on * off;
      Vec3 fwd = Vec3.directionFromRotation(0, w.yBodyRot);
      Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
      Vec3 head = w.getPosition(partial).add(fwd.scale(5.2)).add(0, 6.0, 0);
      billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, head, cam, 4.5F * beat, 0, VfxDraw.argb(0xFF2010, 0.35F * al));
      for (int sd = -1; sd <= 1; sd += 2) {
         Vec3 p = head.add(right.scale(0.75 * sd));
         billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p, cam, 2.2F * pop * beat, 0, VfxDraw.argb(0xFF2A18, 0.8F * al));
         billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_FLASH)), m, p, cam, 0.9F * pop, age * 0.02F, VfxDraw.argb(0xFF5030, al));
         billboard(buf.getBuffer(VfxRenderTypes.glowXray(T_GLOW)), m, p, cam, 0.45F, 0, VfxDraw.argb(0xFFE0C0, al));
      }
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

   private static int lerpColor(int a, int b, float t) {
      int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
      return (r << 16) | (g << 8) | bl;
   }
}
