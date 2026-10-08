package fr.heralisz.bunker.keycard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import fr.heralisz.bunker.HeraliszBunker;
import fr.heralisz.bunker.keycard.KeycardItem;
import fr.heralisz.bunker.keycard.ReaderGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Passages de carte en cours (joueur local et autres joueurs). La carte n'est plus « tenue » : elle est dessinée dans le
 * monde et suit un trajet lié au lecteur — poche, présentation en haut de la rainure, insertion, glissement jusqu'en bas
 * le long de la rainure (puce côté tête de lecture), retrait, retour à la poche. Le bras droit et la tête du joueur
 * visent la carte à chaque instant (Player Animator), la carte tenue en main est masquée pendant ce temps.
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, value = Dist.CLIENT)
public final class KeycardSwipes {
   // chronologie (ticks) — la lecture serveur (CardReaderBlock.READ_TICKS) tombe à la fin du glissement
   static final float T_OUT = 6, T_PRESENT = 16, T_INSERT = 20, T_SLIDE_END = 36, T_PULL = 40, T_BACK = 50;
   public static final int LENGTH = 52;

   record Swipe(BlockPos reader, Direction facing, long start, ItemStack card) {}

   private static final Map<Integer, Swipe> ACTIVE = new HashMap<>();
   private static final Map<AbstractClientPlayer, AimLayer> LAYERS = new WeakHashMap<>();

   public static void begin(AbstractClientPlayer player, BlockPos reader, Direction facing) {
      ItemStack card = player.getMainHandItem().getItem() instanceof KeycardItem ? player.getMainHandItem().copy() : player.getOffhandItem().copy();
      ACTIVE.put(player.getId(), new Swipe(reader, facing, player.level().getGameTime(), card));
      LAYERS.computeIfAbsent(player, p -> {
         AimLayer layer = new AimLayer(p);
         PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(1500, layer);
         return layer;
      });
   }

   @Nullable
   static Swipe of(Player player) {
      Swipe s = ACTIVE.get(player.getId());
      if (s != null && player.level().getGameTime() - s.start > LENGTH) {
         ACTIVE.remove(player.getId());
         return null;
      }
      return s;
   }

   static float time(Player player, Swipe s, float partial) {
      return player.level().getGameTime() - s.start + partial;
   }

   private static float ease(float a, float b, float t) {
      float x = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   static Vec3 bodyForward(Player p) {
      float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
   }

   static Vec3 bodyRight(Player p) {
      float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
   }

   /** Centre de la carte à l'instant t. */
   static Vec3 cardCenter(Player p, Swipe s, float t) {
      Vec3 f = ReaderGeometry.front(s.facing);
      double outset = ReaderGeometry.CARD_SHORT / 2 - 0.02;               // bord intérieur au fond de la rainure
      double along = ease(T_INSERT, T_SLIDE_END, t) * (1.0 - ReaderGeometry.CARD_LONG / (ReaderGeometry.SLOT_TOP - ReaderGeometry.SLOT_BOTTOM));
      Vec3 inSlot = ReaderGeometry.slot(s.reader, s.facing, 0).add(0, -ReaderGeometry.CARD_LONG / 2 + 0.04, 0)
         .add(0, -(ReaderGeometry.SLOT_TOP - ReaderGeometry.SLOT_BOTTOM) * along, 0).add(f.scale(outset));
      Vec3 outside = inSlot.add(f.scale(0.22));
      Vec3 pocket = p.getPosition(Minecraft.getInstance().getFrameTime()).add(0, 0.82, 0).add(bodyRight(p).scale(0.3)).add(bodyForward(p).scale(0.08));
      if (t < T_PRESENT) return pocket.lerp(outside, ease(T_OUT, T_PRESENT, t));
      if (t < T_INSERT) return outside.lerp(inSlot, ease(T_PRESENT, T_INSERT, t));
      if (t < T_PULL) return inSlot.lerp(outside, ease(T_SLIDE_END, T_PULL, t));
      return outside.lerp(pocket, ease(T_PULL, T_BACK, t));
   }

   /** Point saisi par la main : bord extérieur de la carte. */
   static Vec3 grip(Player p, Swipe s, float t) {
      return cardCenter(p, s, t).add(ReaderGeometry.front(s.facing).scale(ReaderGeometry.CARD_SHORT / 2 - 0.02));
   }

   // ------------------------------------------------------------------ rendu de la carte dans le monde
   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Vec3 cam = event.getCamera().getPosition();
      PoseStack ps = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      for (Map.Entry<Integer, Swipe> e : ACTIVE.entrySet()) {
         if (!(mc.level.getEntity(e.getKey()) instanceof Player p)) continue;
         Swipe s = e.getValue();
         float t = time(p, s, event.getPartialTick());
         if (t < 0 || t > T_BACK) continue;
         Vec3 c = cardCenter(p, s, t);
         Vec3 up = new Vec3(0, 1, 0);
         Vec3 f = ReaderGeometry.front(s.facing);
         Vec3 side = up.cross(f);                                           // épaisseur de la carte
         ps.pushPose();
         ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
         // modèle : X = longueur -> vertical, Y = largeur -> sort de la rainure, Z = épaisseur -> vers la tête de lecture
         Matrix4f m = new Matrix4f(
            (float) up.x, (float) up.y, (float) up.z, 0,
            (float) f.x, (float) f.y, (float) f.z, 0,
            (float) side.x, (float) side.y, (float) side.z, 0,
            0, 0, 0, 1);
         ps.mulPoseMatrix(m);
         ps.scale((float) ReaderGeometry.CARD_SCALE, (float) ReaderGeometry.CARD_SCALE, (float) ReaderGeometry.CARD_SCALE);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(c));
         mc.getItemRenderer().renderStatic(s.card, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, 0);
         ps.popPose();
      }
      buffers.endBatch();
   }

   // ------------------------------------------------------------------ la carte tenue en main est masquée pendant le trajet
   private static ItemStack hiddenMain = ItemStack.EMPTY, hiddenOff = ItemStack.EMPTY;

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      Player p = event.getEntity();
      Swipe s = of(p);
      if (s != null && time(p, s, event.getPartialTick()) <= T_BACK) {
         hiddenMain = p.getItemInHand(InteractionHand.MAIN_HAND);
         hiddenOff = p.getItemInHand(InteractionHand.OFF_HAND);
         if (hiddenMain.getItem() instanceof KeycardItem) p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); else hiddenMain = ItemStack.EMPTY;
         if (hiddenOff.getItem() instanceof KeycardItem) p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); else hiddenOff = ItemStack.EMPTY;
      }
   }

   @SubscribeEvent
   public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
      Player p = event.getEntity();
      if (!hiddenMain.isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, hiddenMain);
      if (!hiddenOff.isEmpty()) p.setItemInHand(InteractionHand.OFF_HAND, hiddenOff);
      hiddenMain = ItemStack.EMPTY;
      hiddenOff = ItemStack.EMPTY;
   }

   // ------------------------------------------------------------------ bras droit et tête qui visent la carte
   static final class AimLayer implements IAnimation {
      private final AbstractClientPlayer player;
      private float partial;

      AimLayer(AbstractClientPlayer player) {
         this.player = player;
      }

      @Override
      public boolean isActive() {
         return of(this.player) != null;
      }

      @Override
      public void setupAnim(float tickDelta) {
         this.partial = tickDelta;
      }

      @Override
      public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value0) {
         Swipe s = of(this.player);
         if (s == null || type != TransformType.ROTATION || !(part.equals("rightArm") || part.equals("head"))) {
            return value0;
         }
         float t = time(this.player, s, this.partial);
         float w = ease(0, 5, t) * (1.0F - ease(T_BACK - 4, T_BACK + 2, t));
         Vec3 fw = bodyForward(this.player), right = bodyRight(this.player);
         Vec3 base = this.player.getPosition(this.partial);
         Vec3 target;
         Vec3 from;
         if (part.equals("rightArm")) {
            from = base.add(0, 1.375 * 0.9375, 0).add(right.scale(0.31));      // épaule droite
            target = grip(this.player, s, t);
         } else {
            from = base.add(0, this.player.getEyeHeight(), 0);
            target = cardCenter(this.player, s, t);
         }
         Vec3 d = target.subtract(from);
         double f = d.dot(fw), u = d.y, l = d.dot(right);
         float pitch, yaw;
         if (part.equals("rightArm")) {
            pitch = (float) -Math.atan2(Math.sqrt(f * f + l * l), -u);        // bras pendant = 0, devant = -90°
            yaw = (float) Math.atan2(l, Math.max(f, 0.05));
         } else {
            pitch = (float) -Math.atan2(u, Math.sqrt(f * f + l * l));         // regard vers le bas = positif
            yaw = Mth.clamp((float) Math.atan2(l, Math.max(f, 0.05)), -0.8F, 0.8F);
         }
         return new Vec3f(Mth.lerp(w, value0.getX(), pitch), Mth.lerp(w, value0.getY(), yaw), Mth.lerp(w, value0.getZ(), 0.0F));
      }
   }

   private KeycardSwipes() {}
}
