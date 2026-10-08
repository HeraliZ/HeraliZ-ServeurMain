package fr.heralisz.plague.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cinématiques d'Aldric (apparition, phases, mort) vues par tous les joueurs proches : plans de caméra en images clés autour
 * de lui (repère fixé à son orientation du début), titres, bandes noires. Le joueur est figé pendant ce temps.
 */
public final class Cinematic {
   private static int entityId = -1, kind, duration;
   private static long start;
   private static Vec3 fwd, anchor;
   private static float[][] keys;
   private static String[] titles;
   private static float titleAt;
   private static boolean follows, followsPlayer;

   private Cinematic() {}

   public static void start(int id, int k, int dur, Vec3 facing, Vec3 at) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(id) instanceof fr.heralisz.plague.entity.CineSource m)) return;
      net.minecraft.world.entity.Entity ent = mc.level.getEntity(id);
      entityId = id;
      kind = k;
      follows = m.cinematicFollows(k);
      followsPlayer = m.cinematicFollowsPlayer(k);
      duration = Math.max(20, dur);
      start = mc.level.getGameTime();
      fwd = Vec3.directionFromRotation(0, ent instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : ent.getYRot());
      anchor = ent.position();
      net.minecraft.world.phys.Vec3 face = m.cinematicFacing(k);
      if (face != null && face.lengthSqr() > 1e-4) fwd = new Vec3(face.x, 0, face.z).normalize();
      if (facing != null && facing.horizontalDistanceSqr() > 1e-4) {        // repère envoyé par le serveur (réveil, mort)
         fwd = new Vec3(facing.x, 0, facing.z).normalize();
         anchor = at;
      }
      keys = m.cinematicKeys(kind);
      titles = m.cinematicTitles(kind);
      titleAt = m.cinematicTitleAt(kind);
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

   public static String[] titles() {
      return titles;
   }

   public static float titleAt() {
      return titleAt;
   }

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

   public static int accent() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && mc.level.getEntity(entityId) instanceof fr.heralisz.plague.entity.CineSource m ? m.accentColor() : 0xD8A850;
   }

   /** [position caméra, point visé]. */
   public static Vec3[] camera(float partial) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level.getEntity(entityId);
      Vec3 base = followsPlayer && mc.player != null ? mc.player.getPosition(partial) : follows ? (e != null ? e.getPosition(partial) : anchor) : anchor;
      float k = progress(partial);
      int i = 0;
      while (i < keys.length - 2 && k > keys[i + 1][0]) i++;
      float[] a = keys[i], b = keys[i + 1];
      float t = Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1);
      boolean cutNext = b.length > 5 && b[5] > 0;
      // avant une coupe franche, on tient le plan (léger travelling avant) ; sinon glissement doux
      // mouvement continu : spline passant par les plans voisins (tant qu'il n'y a pas de coupe), sinon on tient le plan
      float[] p0 = i > 0 && !(a.length > 5 && a[5] > 0) ? keys[i - 1] : a;
      float[] p3 = i + 2 < keys.length && !(keys[i + 2].length > 5 && keys[i + 2][5] > 0) ? keys[i + 2] : b;
      boolean ends = p0 == a || p3 == b;
      float ts = cutNext ? 0 : ends ? t * t * (3 - 2 * t) * 0.5F + t * 0.5F : t;
      float push = cutNext ? 1 - 0.06F * Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1) : 1;
      float f = spline(p0, a, b, p3, 1, ts), r = spline(p0, a, b, p3, 2, ts), h = spline(p0, a, b, p3, 3, ts), look = spline(p0, a, b, p3, 4, ts);
      Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
      Vec3 target = base.add(0, look, 0);
      if (a.length > 7 && b.length > 7) {                              // visée décalée (plan sur un autre point de la salle)
         float tf = spline(p0, a, b, p3, 6, ts), tr = spline(p0, a, b, p3, 7, ts);
         target = target.add(fwd.scale(tf)).add(new Vec3(-fwd.z, 0, fwd.x).scale(tr));
      }
      Vec3 cam = base.add(fwd.scale(f * push)).add(right.scale(r * push)).add(0, h, 0);
      // caméra à l'épaule : une dérive lente, jamais parfaitement fixe
      float tt = ageTicks(partial);
      cam = cam.add(Mth.sin(tt * 0.031F) * 0.12, Mth.sin(tt * 0.023F + 1.3F) * 0.08, Mth.cos(tt * 0.027F) * 0.12);
      float[] s = Shake.offset(partial);
      if (s != null) cam = cam.add(s[0] * 0.03, s[1] * 0.03, 0);
      // la caméra ne traverse jamais un bloc (murs, voûte, décors, crâne) : on garde le plus proche obstacle entre la cible et
      // elle (forme de collision ET forme visible), avec une marge d'un bloc, puis on la sort de tout bloc plein
      Vec3 dir = cam.subtract(target);
      double len = dir.length();
      for (ClipContext.Block mode : new ClipContext.Block[] {ClipContext.Block.VISUAL}) {
         // le rayon part à 2 blocs de la cible : un décor posé sur la cible (le crâne) n'aspire pas la caméra en lui
         Vec3 from = target.add(dir.normalize().scale(Math.min(2.0, len * 0.5)));
         BlockHitResult hit = mc.level.clip(new ClipContext(from, cam, mode, ClipContext.Fluid.NONE, mc.player));
         if (hit.getType() != HitResult.Type.MISS) {
            double d = Math.max(0.5, hit.getLocation().distanceTo(target) - 1.0);
            if (d < len) {
               len = d;
               cam = target.add(dir.normalize().scale(d));
            }
         }
      }
      for (int step = 0; step < 12; step++) {
         net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(cam);
         // seuls les blocs qu'on voit comptent (les volumes invisibles des décors et du rideau de glace sont ignorés)
         net.minecraft.world.level.block.state.BlockState bs = mc.level.getBlockState(bp);
         if (bs.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL || !bs.isSolidRender(mc.level, bp)) break;
         cam = cam.add(target.subtract(cam).normalize().scale(0.5));
      }
      return new Vec3[] {cam, target};
   }

   /** Catmull-Rom sur la colonne c (les plans voisins sans visée décalée comptent comme 0). */
   private static float spline(float[] p0, float[] p1, float[] p2, float[] p3, int c, float t) {
      float v0 = c < p0.length ? p0[c] : 0, v1 = c < p1.length ? p1[c] : 0, v2 = c < p2.length ? p2[c] : 0, v3 = c < p3.length ? p3[c] : 0;
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
