package fr.heralisz.n07.client;

import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.dungeon.DungeonKind;
import fr.heralisz.n07.vfx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Rituel d'invocation vu par ce client. Le joueur qui l'accomplit s'avance tout seul jusqu'au sanctuaire, puis fait le geste
 * (carte glissée dans la fente, vanne tournée, braise lâchée, graine plantée, œuf déposé) pendant que la caméra passe d'un plan
 * par-dessus l'épaule à un gros plan, puis recule pour montrer le donjon qui s'éveille. Les autres joueurs voient le geste.
 * <p>
 * Plans de caméra et poses : repère du joueur au rituel (avant = vers le sanctuaire, droite, hauteur au-dessus du sol), temps en
 * ticks depuis son arrivée.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID, value = Dist.CLIENT)
public final class Ritual {
   private Ritual() {}

   static final class Run {
      int player, kind, walk, total;
      long start;
      Vec3 stand, from, f, r;
      float yaw, fromYaw;
   }

   private static final Map<Integer, Run> RUNS = new HashMap<>();

   // ------------------------------------------------------------------ plans de caméra : {s, cam avant, cam droite, cam haut, visée avant, visée droite, visée haut}
   private static final float[][][] CAM = new float[DungeonKind.values().length][][];
   // poses : {s, bras droit x, y, z, poids droit, bras gauche x, y, z, poids gauche, tangage de la tête}
   private static final float[][][] POSE = new float[DungeonKind.values().length][][];

   static {
      CAM[DungeonKind.N07.ordinal()] = new float[][] {
         {0, -2.6F, 0.8F, 1.9F, 1.3F, 0, 1.3F},
         {9, -0.35F, 0.95F, 1.6F, 1.3F, 0.05F, 1.35F},
         {26, -0.1F, 0.72F, 1.52F, 1.3F, 0, 1.45F},
         {32, -0.1F, 0.72F, 1.52F, 1.3F, 0, 1.45F},
         // recul : la cuve, ses bobines qui s'emballent, les arcs qui la frappent
         {46, -3.0F, -3.6F, 2.6F, 7.3F, 0, 4.5F},
         {75, -4.0F, -4.6F, 3.4F, 7.3F, 0, 5.2F},
         {100, -5.2F, -5.0F, 4.4F, 7.3F, 0, 5.6F}};
      // la carte : il la sort (0), tend le bras vers le lecteur (6), l'enfonce dans la fente (10), la lâche au lecteur qui l'avale
      // (11), accompagne la glissade vers le bas (12-20), attend le voyant vert (24), retire la main (28-36) ; l'autre main se
      // pose sur le bord de la console ; la tête suit la carte puis se lève vers l'écran
      POSE[DungeonKind.N07.ordinal()] = new float[][] {
         {-3, 0, 0, 0, 0, 0, 0, 0, 0, 10},
         {0, -45, -8, 4, 0.7F, -10, 4, -4, 0.4F, 14},
         {6, -82, -16, 2, 1, -38, 18, -6, 0.8F, 20},
         {10, -92, -12, 0, 1, -46, 20, -6, 1, 26},
         {12, -90, -12, 0, 1, -46, 20, -6, 1, 30},
         {20, -70, -10, 0, 1, -46, 20, -6, 1, 30},
         {24, -71, -10, 0, 1, -44, 18, -6, 1, 12},
         {28, -64, -8, 0, 1, -40, 14, -4, 0.9F, 8},
         {36, -18, 0, 0, 0.3F, -10, 0, 0, 0.2F, 2},
         {40, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
   }

   // ------------------------------------------------------------------ réception
   public static void receive(Vfx.Packet p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      int id = p.color();
      if (p.extra() == 1) {
         RUNS.remove(id);
         return;
      }
      Run r = new Run();
      r.player = id;
      r.kind = Mth.clamp((int) p.b(), 0, DungeonKind.values().length - 1);
      r.walk = Math.max(1, (int) p.a());
      r.total = p.duration();
      r.start = mc.level.getGameTime();
      r.stand = p.pos();
      Vec3 d = new Vec3(p.dx(), 0, p.dz());
      r.f = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
      r.r = new Vec3(-r.f.z, 0, r.f.x);
      r.yaw = (float) (Mth.atan2(r.f.z, r.f.x) * Mth.RAD_TO_DEG) - 90F;
      Entity e = mc.level.getEntity(id);
      r.from = e != null ? e.position() : r.stand;
      r.fromYaw = e != null ? e.getYRot() : r.yaw;
      RUNS.put(id, r);
   }

   private static float time(Run r, float partial) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0 : mc.level.getGameTime() + partial - r.start;
   }

   private static Run local() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return null;
      Run r = RUNS.get(mc.player.getId());
      if (r != null && time(r, 0) > r.total + 12) {
         RUNS.remove(mc.player.getId());
         return null;
      }
      return r;
   }

   /** Le joueur local accomplit un rituel : entrées bloquées, caméra du rituel, interface masquée. */
   public static boolean localActive() {
      return local() != null;
   }

   private static float smooth(float u) {
      u = Mth.clamp(u, 0, 1);
      return u * u * (3 - 2 * u);
   }

   /** Échantillonne une table de clés {s, v1, v2...} (interpolation douce entre deux clés). */
   private static float[] sample(float[][] keys, float s) {
      int n = keys[0].length;
      float[] out = new float[n - 1];
      if (s <= keys[0][0]) {
         System.arraycopy(keys[0], 1, out, 0, n - 1);
         return out;
      }
      for (int i = 0; i + 1 < keys.length; i++) {
         float[] a = keys[i], b = keys[i + 1];
         if (s <= b[0]) {
            float u = smooth((s - a[0]) / Math.max(1e-3F, b[0] - a[0]));
            for (int j = 1; j < n; j++) out[j - 1] = a[j] + (b[j] - a[j]) * u;
            return out;
         }
      }
      System.arraycopy(keys[keys.length - 1], 1, out, 0, n - 1);
      return out;
   }

   private static Vec3 at(Run r, float f, float rt, float h) {
      return r.stand.add(r.f.scale(f)).add(r.r.scale(rt)).add(0, h, 0);
   }

   // ------------------------------------------------------------------ joueur local : marche, orientation
   @SubscribeEvent
   public static void playerTick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.START || !(e.player instanceof LocalPlayer pl) || !e.player.level().isClientSide) return;
      Run r = local();
      if (r == null || r.player != pl.getId()) return;
      float t = time(r, 0);
      float yaw, pitch = sample(POSE[r.kind], t - r.walk)[8];
      if (t <= r.walk) {
         float u = smooth(t / r.walk);
         Vec3 p = r.from.lerp(r.stand, u);
         pl.setPos(p.x, Math.max(p.y, Math.min(r.from.y, r.stand.y)), p.z);
         Vec3 d = r.stand.subtract(r.from);
         float moveYaw = d.horizontalDistanceSqr() > 0.09 ? (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F : r.yaw;
         // on se tourne vers la destination au départ, vers le sanctuaire à l'arrivée
         float a = Mth.clamp(t / 4F, 0, 1), b = Mth.clamp((t - r.walk + 5) / 5F, 0, 1);
         yaw = Mth.rotLerp(b, Mth.rotLerp(a, r.fromYaw, moveYaw), r.yaw);
         pitch = Mth.lerp(u, 0, pitch);
      } else {
         if (pl.position().distanceToSqr(r.stand) > 0.0025) pl.setPos(r.stand.x, pl.getY(), r.stand.z);
         yaw = r.yaw;
      }
      pl.setDeltaMovement(0, Math.min(0, pl.getDeltaMovement().y), 0);
      pl.setYRot(yaw);
      pl.setXRot(pitch);
      pl.yHeadRot = yaw;
      pl.yBodyRot = yaw;
   }

   @SubscribeEvent
   public static void clientTick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         RUNS.clear();
         return;
      }
      RUNS.values().removeIf(r -> time(r, 0) > r.total + 14);
   }

   // ------------------------------------------------------------------ caméra
   public static boolean cameraActive() {
      return localActive();
   }

   /** [position caméra, point visé]. */
   public static Vec3[] camera(float partial) {
      Run r = local();
      if (r == null) return null;
      float t = time(r, partial), s = t - r.walk;
      float[] k = sample(CAM[r.kind], s);
      Vec3 cam = at(r, k[0], k[1], k[2]), look = at(r, k[3], k[4], k[5]);
      if (s < 0) {
         // pendant la marche : par-dessus l'épaule, on suit le joueur
         Minecraft mc = Minecraft.getInstance();
         Vec3 p = mc.player.getPosition(partial);
         Vec3 follow = p.subtract(r.f.scale(2.6)).add(r.r.scale(0.8)).add(0, 1.9, 0);
         cam = follow;
      }
      // petite respiration de la caméra
      float br = Mth.sin(t * 0.07F) * 0.02F;
      return new Vec3[] {cam.add(0, br, 0), look};
   }

   // ------------------------------------------------------------------ poses (appelé par HumanoidModelMixin)
   public static void pose(LivingEntity e, HumanoidModel<?> m) {
      Run r = RUNS.get(e.getId());
      if (r == null) return;
      Minecraft mc = Minecraft.getInstance();
      float s = time(r, mc.getFrameTime()) - r.walk;
      if (s < -3 || s > r.total) return;
      float[] k = sample(POSE[r.kind], s);
      float rx = k[0], ry = k[1], rz = k[2], wr = k[3], lx = k[4], ly = k[5], lz = k[6], wl = k[7];
      float d = Mth.DEG_TO_RAD;
      m.head.xRot = Mth.lerp(Math.max(wr, wl), m.head.xRot, k[8] * d);
      if (wr > 0) {
         m.rightArm.xRot = Mth.lerp(wr, m.rightArm.xRot, rx * d);
         m.rightArm.yRot = Mth.lerp(wr, m.rightArm.yRot, ry * d);
         m.rightArm.zRot = Mth.lerp(wr, m.rightArm.zRot, rz * d);
      }
      if (wl > 0) {
         m.leftArm.xRot = Mth.lerp(wl, m.leftArm.xRot, lx * d);
         m.leftArm.yRot = Mth.lerp(wl, m.leftArm.yRot, ly * d);
         m.leftArm.zRot = Mth.lerp(wl, m.leftArm.zRot, lz * d);
      }
   }

   // ------------------------------------------------------------------ bandes noires
   public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Run r = local();
      if (r == null) return;
      float t = time(r, partial);
      float in = Mth.clamp(t / 10F, 0, 1), out = Mth.clamp((r.total - t) / 6F, 0, 1);
      int bar = (int) (h * 0.1F * smooth(Math.min(in, out)));
      if (bar <= 0) return;
      g.fill(0, 0, w, bar, 0xFF000000);
      g.fill(0, h - bar, w, h, 0xFF000000);
   }

   // ------------------------------------------------------------------ la carte dans la main (N-07)
   private static net.minecraft.world.item.ItemStack card;

   /** Pendant le geste, la carte d'accès est dans sa main droite (jusqu'à ce que le lecteur l'avale, s = 11) : vue par tous. */
   @SubscribeEvent
   public static void renderCard(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {
      Run r = RUNS.get(e.getEntity().getId());
      if (r == null || r.kind != DungeonKind.N07.ordinal()) return;
      float s = time(r, e.getPartialTick()) - r.walk;
      if (s < -3 || s >= 11) return;
      net.minecraft.world.entity.player.Player pl = e.getEntity();
      com.mojang.blaze3d.vertex.PoseStack ps = e.getPoseStack();
      net.minecraft.client.model.PlayerModel<?> model = e.getRenderer().getModel();
      ps.pushPose();
      float body = Mth.rotLerp(e.getPartialTick(), pl.yBodyRotO, pl.yBodyRot);
      ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - body));
      ps.scale(-1, -1, 1);
      ps.scale(0.9375F, 0.9375F, 0.9375F);
      ps.translate(0, -1.501F, 0);
      model.rightArm.translateAndRotate(ps);
      ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
      ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
      ps.translate(-1 / 16F, 0.125F, -0.625F);
      if (card == null) card = new net.minecraft.world.item.ItemStack(fr.heralisz.n07.dungeon.DungeonRegistry.N07_KEYCARD.get());
      Minecraft.getInstance().getItemRenderer().renderStatic(pl, card, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps,
         e.getMultiBufferSource(), pl.level(), e.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pl.getId());
      ps.popPose();
   }
}
