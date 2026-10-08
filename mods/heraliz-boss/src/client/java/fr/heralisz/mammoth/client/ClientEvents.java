package fr.heralisz.mammoth.client;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heralisz.mammoth.HeraliszMammoth;
import fr.heralisz.mammoth.entity.Mammoth;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Côté client : la barre vanilla remplacée par la nôtre, le joueur figé pendant les cinématiques, et le blizzard dans l'arène
 * (phase 2 : brouillard de neige qui ferme la vue, flocons poussés par le vent ; phase 3 : encore plus dense, plus blanc).
 */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID, value = Dist.CLIENT)
public final class ClientEvents {
   private ClientEvents() {}

   /** Intensité du blizzard (0..1), lissée. */
   private static float storm, stormTarget;

   @SubscribeEvent
   public static void hideVanillaBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
      if (e.getBossEvent().getName().getContents() instanceof TranslatableContents tc && tc.getKey().equals("entity.heralisz_mammoth.mammoth")) {
         e.setIncrement(0);
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void hideHud(RenderGuiOverlayEvent.Pre e) {
      if (Cinematic.active() && e.getOverlay().id().getNamespace().equals("minecraft")) e.setCanceled(true);
   }

   @SubscribeEvent
   public static void noHand(RenderHandEvent e) {
      if (Cinematic.active()) e.setCanceled(true);
   }

   @SubscribeEvent
   public static void freeze(MovementInputUpdateEvent e) {
      if (!Cinematic.active()) return;
      e.getInput().forwardImpulse = 0;
      e.getInput().leftImpulse = 0;
      e.getInput().up = e.getInput().down = e.getInput().left = e.getInput().right = false;
      e.getInput().jumping = false;
      e.getInput().shiftKeyDown = false;
   }

   @SubscribeEvent
   public static void noInteract(InputEvent.InteractionKeyMappingTriggered e) {
      if (Cinematic.active()) {
         e.setSwingHand(false);
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.isPaused()) return;
      Mammoth m = Hud.findBoss(mc);
      stormTarget = m == null || m.isDeadOrDying() ? 0 : m.phase() == 3 ? 1F : m.phase() == 2 ? 0.6F : 0F;
      storm += (stormTarget - storm) * 0.02F;
      if (storm < 0.02F) return;
      RandomSource r = mc.level.random;
      int n = (int) (storm * 14);
      double wind = mc.level.getGameTime() * 0.01;
      double wx = Math.cos(wind) * 0.35, wz = Math.sin(wind) * 0.35;
      for (int i = 0; i < n; i++) {
         double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24, y = mc.player.getY() + r.nextDouble() * 10 - 1, z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
         mc.level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x, y, z, wx + (r.nextDouble() - 0.5) * 0.1, -0.05 - r.nextDouble() * 0.05, wz + (r.nextDouble() - 0.5) * 0.1);
      }
   }

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      float k = FrostScreen.fovScale();
      if (k < 0.999F) e.setFOV(e.getFOV() * k);
   }

   @SubscribeEvent
   public static void fog(ViewportEvent.RenderFog e) {
      if (storm < 0.02F) return;
      float far = Mth.lerp(storm, e.getFarPlaneDistance(), 26F);
      e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), far));
      e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.1F));
      e.setFogShape(FogShape.SPHERE);
      e.setCanceled(true);
   }

   @SubscribeEvent
   public static void fogColor(ViewportEvent.ComputeFogColor e) {
      if (storm < 0.02F) return;
      float k = storm * 0.75F;
      e.setRed(Mth.lerp(k, e.getRed(), 0.72F));
      e.setGreen(Mth.lerp(k, e.getGreen(), 0.8F));
      e.setBlue(Mth.lerp(k, e.getBlue(), 0.9F));
   }
}
