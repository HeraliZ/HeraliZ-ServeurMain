package fr.heralisz.n07.client;

import fr.heralisz.n07.api.BossInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cinématiques du boss (apparition, phases, mort) vues par tous les joueurs proches : plan de caméra en images clés
 * autour du boss (repère fixé à son orientation du début), titres, bandes noires. Le joueur est figé pendant ce temps.
 */
public final class Cinematic {
   public static final int SPAWN = 0, PHASE2 = 1, PHASE3 = 2, DEATH = 3;
   private static int entityId = -1, kind, duration;
   private static long start;
   private static Vec3 fwd, anchor;
   private static BossInfo boss;

   public static BossInfo boss() {
      return boss;
   }

   private Cinematic() {}

   private static boolean skippable;

   public static void start(int id, int k, int dur) {
      start(id, k, dur, false);
   }

   /** skip : l'invite « maintenir pour passer » s'affiche (combat pilote : apparition, mort). */
   public static void start(int id, int k, int dur, boolean skip) {
      skippable = skip;
      startInner(id, k, dur);
   }

   /** Arret immediat (cinematique passee). */
   public static void stop(int id) {
      if (id == entityId || id < 0) entityId = -1;
   }

   public static boolean skippable() {
      return skippable && active();
   }

   private static void startInner(int id, int k, int dur) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Entity e = mc.level.getEntity(id);
      if (e == null) return;
      entityId = id;
      kind = Mth.clamp(k, 0, 3);
      duration = Math.max(20, dur);
      start = mc.level.getGameTime();
      fwd = Vec3.directionFromRotation(0, e instanceof BossInfo bi ? bi.cinematicYaw() : e.getYRot());
      anchor = e instanceof BossInfo bi2 ? bi2.cinematicAnchor() : e.position();
      boss = e instanceof BossInfo bi ? bi : null;
   }

   public static boolean active() {
      Minecraft mc = Minecraft.getInstance();
      if (entityId < 0 || mc.level == null) return false;
      if (mc.level.getGameTime() - start > duration) {
         entityId = -1;
         return false;
      }
      return true;
   }

   public static int kind() {
      return kind;
   }

   /** Progression 0..1. */
   public static float progress(float partial) {
      Minecraft mc = Minecraft.getInstance();
      return Mth.clamp((float) ((mc.level.getGameTime() + partial - start) / duration), 0, 1);
   }

   public static float ageTicks(float partial) {
      Minecraft mc = Minecraft.getInstance();
      return (float) (mc.level.getGameTime() + partial - start);
   }

   public static int duration() {
      return duration;
   }

   /** [position caméra, point visé]. */
   public static Vec3[] camera(float partial) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level.getEntity(entityId);
      Vec3 base = e != null ? e.getPosition(partial) : anchor;
      // la mort : le corps s'enfonce, on garde l'ancre
      if (boss == null || boss.cinematicAnchored(kind)) base = anchor;
      float k = progress(partial);
      float[][] keys = boss != null ? boss.cinematicKeys(kind) : new float[][] {{0, 8, 3, 3, 2}, {1, 6, 5, 3, 2}};
      int i = 0;
      while (i < keys.length - 2 && k > keys[i + 1][0]) i++;
      float[] a = keys[i], b = keys[i + 1];
      float t = Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1);
      // mouvement continu : spline qui passe par les plans voisins (Catmull-Rom), départ et arrivée adoucis
      float[] p0 = i > 0 ? keys[i - 1] : a, p3 = i + 2 < keys.length ? keys[i + 2] : b;
      float ts = (p0 == a || p3 == b) ? t * t * (3 - 2 * t) : t;
      float f = spline(p0[1], a[1], b[1], p3[1], ts), r = spline(p0[2], a[2], b[2], p3[2], ts), h = spline(p0[3], a[3], b[3], p3[3], ts),
         look = spline(p0[4], a[4], b[4], p3[4], ts);
      Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
      Vec3 target = base.add(0, look, 0);
      Vec3 cam = base.add(fwd.scale(f)).add(right.scale(r)).add(0, h, 0);
      // caméra à l'épaule : une dérive lente, jamais parfaitement figée
      float tt = (float) (mc.level.getGameTime() + partial);
      cam = cam.add(Mth.sin(tt * 0.031F) * 0.1, Mth.sin(tt * 0.023F + 1.3F) * 0.07, Mth.cos(tt * 0.027F) * 0.1);
      // jamais dans un mur, un pilier ou un décor : on part de la cible, on s'arrête un bloc avant l'obstacle ; puis on sort de
      // tout bloc plein
      Vec3 dir = cam.subtract(target);
      double len = dir.length();
      if (len > 1e-3) {
         Vec3 from = target.add(dir.normalize().scale(Math.min(2.0, len * 0.5)));
         BlockHitResult hit = mc.level.clip(new ClipContext(from, cam, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
         if (hit.getType() != HitResult.Type.MISS) {
            double d = Math.max(1.0, hit.getLocation().distanceTo(target) - 1.0);
            if (d < len) cam = target.add(dir.normalize().scale(d));
         }
      }
      for (int step = 0; step < 12; step++) {
         net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(cam);
         var bs = mc.level.getBlockState(bp);
         if (bs.isAir() || (bs.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL && !bs.isSolidRender(mc.level, bp))) break;
         cam = cam.add(target.subtract(cam).normalize().scale(0.5));
      }
      return new Vec3[] {cam, target};
   }

   private static float spline(float v0, float v1, float v2, float v3, float t) {
      float t2 = t * t, t3 = t2 * t;
      return 0.5F * (2 * v1 + (-v0 + v2) * t + (2 * v0 - 5 * v1 + 4 * v2 - v3) * t2 + (-v0 + 3 * v1 - 3 * v2 + v3) * t3);
   }

   public static float yaw(Vec3 d) {
      return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
   }

   public static float pitch(Vec3 d) {
      return (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
   }
}
