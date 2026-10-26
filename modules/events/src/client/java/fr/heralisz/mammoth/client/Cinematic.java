package fr.heralisz.mammoth.client;

import fr.heralisz.mammoth.entity.Mammoth;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cinématiques d'Ymirak (apparition, phases, mort) vues par tous les joueurs proches : plans de caméra en images clés autour
 * de lui (repère fixé à son orientation du début), titres, bandes noires. Le joueur est figé pendant ce temps.
 */
public final class Cinematic {
   private static int entityId = -1, kind, duration;
   private static long start;
   private static Vec3 fwd, anchor;
   private static float[][] keys;
   private static String[] titles;
   private static float titleAt;
   private static boolean follows;

   private Cinematic() {}

   public static void start(int id, int k, int dur) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(id) instanceof fr.heralisz.mammoth.entity.CineSource m)) return;
      net.minecraft.world.entity.Entity ent = mc.level.getEntity(id);
      entityId = id;
      kind = k;
      follows = m.cinematicFollows(k);
      duration = Math.max(20, dur);
      start = mc.level.getGameTime();
      fwd = Vec3.directionFromRotation(0, ent instanceof fr.heralisz.mammoth.entity.Draugr ? ent.getYRot()
         : ent instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : ent.getYRot());
      anchor = ent.position();
      net.minecraft.world.phys.Vec3 face = m.cinematicFacing(k);
      if (face != null && face.lengthSqr() > 1e-4) fwd = new Vec3(face.x, 0, face.z).normalize();
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
      return mc.level != null && mc.level.getEntity(entityId) instanceof fr.heralisz.mammoth.entity.CineSource m ? m.accentColor() : 0x7AC8FF;
   }

   /** [position caméra, point visé]. */
   public static Vec3[] camera(float partial) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level.getEntity(entityId);
      Vec3 base = follows ? (e != null ? e.getPosition(partial) : anchor) : anchor;
      float k = progress(partial);
      int i = 0;
      while (i < keys.length - 2 && k > keys[i + 1][0]) i++;
      float[] a = keys[i], b = keys[i + 1];
      float t = Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1);
      boolean cutNext = b.length > 5 && b[5] > 0;
      // avant une coupe franche, on tient le plan (léger travelling avant) ; sinon glissement doux
      t = cutNext ? 0 : t * t * (3 - 2 * t);
      float push = cutNext ? 1 - 0.06F * Mth.clamp((k - a[0]) / Math.max(1e-4F, b[0] - a[0]), 0, 1) : 1;
      float f = Mth.lerp(t, a[1], b[1]), r = Mth.lerp(t, a[2], b[2]), h = Mth.lerp(t, a[3], b[3]), look = Mth.lerp(t, a[4], b[4]);
      Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
      Vec3 target = base.add(0, look, 0);
      if (a.length > 7 && b.length > 7) {                              // visée décalée (plan sur un autre point de la salle)
         float tf = Mth.lerp(t, a[6], b[6]), tr = Mth.lerp(t, a[7], b[7]);
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

   public static float yaw(Vec3 d) {
      return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
   }

   public static float pitch(Vec3 d) {
      return (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
   }
}
