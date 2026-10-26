package fr.heralisz.bunker.keycard.client;

import fr.heralisz.bunker.HeraliszBunker;
import fr.heralisz.bunker.keycard.CardReaderBlock;
import fr.heralisz.bunker.keycard.KeycardNetwork;
import fr.heralisz.bunker.keycard.ReaderGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Cinématique du passage de carte, jouée par tous les joueurs à moins de 10 blocs du lecteur :
 * 1. la caméra quitte les yeux du joueur pour un plan de côté sur celui qui passe la carte (geste + carte dans la rainure) ;
 * 2. accès accordé et porte liée : la caméra va cadrer la porte, qui s'ouvre à son arrivée, et reste jusqu'à la fin de
 *    l'ouverture ; sinon gros plan sur l'écran du lecteur (ACCESS GRANTED / DENIED) ;
 * 3. chacun retrouve sa vue. Pendant la séquence : déplacements bloqués, interface masquée, vue 3e personne.
 * La position de caméra est appliquée par CameraMixin.
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, value = Dist.CLIENT)
public final class KeycardCinematic {
   private static final int T_TO_SIDE = 10, T_SIDE_END = CardReaderBlock.READ_TICKS, T_TO_TARGET = T_SIDE_END + CardReaderBlock.DOOR_DELAY, T_BACK = 10;
   private static long start = -1;
   private static int targetEnd, end;
   private static Vec3 eye, look, sidePos, sideTarget, targetPos, targetLook;
   private static boolean lockView;
   private static float lockYaw, lockPitch;
   @Nullable private static CameraType previousCamera;
   private static boolean previousHideGui;

   public static boolean active() {
      Minecraft mc = Minecraft.getInstance();
      return start >= 0 && mc.level != null && mc.level.getGameTime() - start <= end;
   }

   public static void begin(AbstractClientPlayer swiper, BlockPos reader, Direction facing, boolean granted, KeycardNetwork.DoorShot door) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer me = mc.player;
      if (me == null || mc.level == null) return;
      Vec3 f = ReaderGeometry.front(facing);
      Vec3 side = ReaderGeometry.slotSide(facing);
      Vec3 slot = ReaderGeometry.slot(reader, facing, 0.5);
      // celui qui passe la carte se tourne vers la fente
      if (swiper == me) {
         Vec3 toSlot = slot.subtract(me.getEyePosition());
         me.setYRot((float) (Mth.atan2(-toSlot.x, toSlot.z) * Mth.RAD_TO_DEG));
         me.setXRot((float) (-Mth.atan2(toSlot.y, toSlot.horizontalDistance()) * Mth.RAD_TO_DEG));
         me.yHeadRot = me.yBodyRot = me.yBodyRotO = me.getYRot();
      }
      lockYaw = me.getYRot();
      lockPitch = me.getXRot();
      lockView = true;
      eye = me.getEyePosition();
      look = eye.add(me.getLookAngle().scale(2.0));
      // plan de côté, à hauteur du lecteur, du côté de la rainure : on voit la carte suivre la fente
      sideTarget = slot.add(f.scale(0.35));
      sidePos = slot.add(side.scale(1.7)).add(f.scale(0.95)).add(0, 0.25, 0);
      if (granted && door.present()) {
         Vec3 min = Vec3.atLowerCornerOf(door.min()), max = Vec3.atLowerCornerOf(door.max()).add(1, 1, 1);
         Vec3 c = min.add(max).scale(0.5);
         Vec3 size = max.subtract(min);
         double span = Math.max(Math.max(size.x, size.z), size.y);
         double dist = span * 1.25 + 2.0;
         Vec3 toReader = Vec3.atCenterOf(reader).subtract(c);
         if (size.y < 2.0 && Math.max(size.x, size.z) >= 2.0) {
            // trappe au sol : vue plongeante depuis le côté du lecteur
            Vec3 h = new Vec3(toReader.x, 0, toReader.z);
            h = h.lengthSqr() < 1e-4 ? f : h.normalize();
            targetPos = c.add(0, dist * 0.8, 0).add(h.scale(dist * 0.55));
         } else {
            Vec3 n = ReaderGeometry.front(door.facing());
            double s = Math.signum(toReader.dot(n));
            targetPos = c.add(n.scale((s == 0 ? 1 : s) * dist)).add(0, 0.6, 0);
         }
         targetLook = c;
         targetEnd = T_TO_TARGET + door.openTicks() + 15;
      } else {
         targetLook = ReaderGeometry.screen(reader, facing);
         targetPos = targetLook.add(f.scale(0.7)).subtract(side.scale(0.08));
         targetEnd = T_TO_TARGET + 18;
      }
      end = targetEnd + T_BACK;
      start = mc.level.getGameTime();
      if (previousCamera == null) {
         previousCamera = mc.options.getCameraType();
         previousHideGui = mc.options.hideGui;
      }
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      mc.options.hideGui = true;
   }

   private static void finish() {
      Minecraft mc = Minecraft.getInstance();
      start = -1;
      lockView = false;
      if (previousCamera != null) {
         mc.options.setCameraType(previousCamera);
         mc.options.hideGui = previousHideGui;
         previousCamera = null;
      }
   }

   private static float ease(float a, float b, float t) {
      float x = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   /** Position et cible de la caméra à l'instant t (ticks + fraction). */
   public static Vec3[] camera(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      float t = mc.level.getGameTime() - start + partialTick;
      Vec3 pos, target;
      if (t < T_SIDE_END) {
         float k = ease(0, T_TO_SIDE, t);
         float push = ease(T_TO_SIDE, T_SIDE_END, t) * 0.12F;
         pos = eye.lerp(sidePos.lerp(sideTarget, push), k);
         target = look.lerp(sideTarget, k);
      } else if (t < targetEnd) {
         float k = ease(T_SIDE_END, T_TO_TARGET, t);
         pos = sidePos.lerp(sideTarget, 0.12).lerp(targetPos, k);
         target = sideTarget.lerp(targetLook, k);
      } else {
         float k = ease(targetEnd, end, t);
         pos = targetPos.lerp(eye, k);
         target = targetLook.lerp(look, k);
      }
      return new Vec3[]{pos, target};
   }

   public static float yaw(Vec3 dir) {
      return (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
   }

   public static float pitch(Vec3 dir) {
      return (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || start < 0) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.level.getGameTime() - start > end) {
         finish();
         return;
      }
      if (lockView) {
         mc.player.setYRot(lockYaw);
         mc.player.setXRot(lockPitch);
         mc.player.yHeadRot = mc.player.yBodyRot = lockYaw;
      }
   }

   /** Commandes de déplacement bloquées pendant la séquence. */
   @SubscribeEvent
   public static void onMovementInput(MovementInputUpdateEvent event) {
      if (active()) {
         var in = event.getInput();
         in.forwardImpulse = 0;
         in.leftImpulse = 0;
         in.up = in.down = in.left = in.right = false;
         in.jumping = false;
         in.shiftKeyDown = false;
      }
   }

   private KeycardCinematic() {}
}
