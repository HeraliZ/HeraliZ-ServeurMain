package fr.heralisz.sylvorn.client;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heralisz.sylvorn.fx.Particles;
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
 * Côté client : la barre vanilla remplacée par la nôtre, le joueur figé pendant les cinématiques, et l'air du nid (phase 2 : la
 * poussière et la soie tombent de la voûte ; phase 3 : une brume grise qui ferme la vue).
 */
@Mod.EventBusSubscriber(modid = HeraliszSylvorn.MODID, value = Dist.CLIENT)
public final class ClientEvents {
   private ClientEvents() {}

   private static float heat, heatTarget;

   @SubscribeEvent
   public static void hideVanillaBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
      if (e.getBossEvent().getName().getContents() instanceof TranslatableContents tc && tc.getKey().equals("entity.heralisz_sylvorn.sylvorn")) {
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
      Sylvorn k = Hud.findBoss(mc);
      heatTarget = k == null || k.isDeadOrDying() ? 0 : k.phase() == 3 ? 1F : k.phase() == 2 ? 0.45F : 0F;
      heat += (heatTarget - heat) * 0.02F;
      if (heat < 0.02F) return;
      RandomSource r = mc.level.random;
      int n = (int) (heat * 10);
      for (int i = 0; i < n; i++) {
         double x = mc.player.getX() + (r.nextDouble() - 0.5) * 26, y = mc.player.getY() + r.nextDouble() * 10 - 1, z = mc.player.getZ() + (r.nextDouble() - 0.5) * 26;
         boolean ember = i % 3 != 0;
         mc.level.addParticle(ember ? Particles.MIST.get() : fr.heralisz.sylvorn.particle.ModParticles.LEAF_AUTUMN.get(), x, y, z, (r.nextDouble() - 0.5) * 0.02,
            -0.01, (r.nextDouble() - 0.5) * 0.02);
      }
   }

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      float k = ScreenFx.fovScale();
      if (k < 0.999F) e.setFOV(e.getFOV() * k);
   }

   @SubscribeEvent
   public static void fog(ViewportEvent.RenderFog e) {
      if (heat < 0.3F) return;
      float far = Mth.lerp((heat - 0.3F) / 0.7F, e.getFarPlaneDistance(), 34F);
      e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), far));
      e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.15F));
      e.setFogShape(FogShape.SPHERE);
      e.setCanceled(true);
   }

   @SubscribeEvent
   public static void fogColor(ViewportEvent.ComputeFogColor e) {
      if (heat < 0.02F) return;
      float k = heat * 0.7F;
      e.setRed(Mth.lerp(k, e.getRed(), 0.14F));
      e.setGreen(Mth.lerp(k, e.getGreen(), 0.12F));
      e.setBlue(Mth.lerp(k, e.getBlue(), 0.09F));
   }
}
