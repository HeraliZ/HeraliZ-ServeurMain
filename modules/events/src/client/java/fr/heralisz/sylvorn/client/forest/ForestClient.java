package fr.heralisz.sylvorn.client.forest;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heralisz.sylvorn.particle.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Côté client de Sylvorn : quand il se fond dans la forêt pour rôder, une brume verdâtre épaisse tombe (on ne voit plus loin). */
@Mod.EventBusSubscriber(modid = HeraliszSylvorn.MODID, value = Dist.CLIENT)
public final class ForestClient {
   private static float mist;

   private ForestClient() {}

   private static boolean stalking(Minecraft mc) {
      for (Sylvorn s : mc.level.getEntitiesOfClass(Sylvorn.class, mc.player.getBoundingBox().inflate(64))) {
         Sylvorn.Action a = s.action();
         if (!s.isDeadOrDying() && (a == Sylvorn.Action.VANISH || a == Sylvorn.Action.STALK || a == Sylvorn.Action.AMBUSH)) return true;
      }
      return false;
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) return;
      mist = Mth.clamp(mist + (stalking(mc) ? 0.035F : -0.015F), 0, 1);
      if (mist <= 0.15F) return;
      int n = (int) (6 * mist);
      for (int i = 0; i < n; i++) {
         double x = mc.player.getX() + (mc.level.random.nextDouble() - 0.5) * 24, z = mc.player.getZ() + (mc.level.random.nextDouble() - 0.5) * 24;
         double y = mc.player.getY() + mc.level.random.nextDouble() * 5 - 0.5;
         mc.level.addParticle(ModParticles.SPORE.get(), x, y, z, 0.01, 0.002, 0.004);
      }
   }

   @SubscribeEvent
   public static void fog(ViewportEvent.RenderFog e) {
      if (mist <= 0) return;
      float k = mist * mist * (3 - 2 * mist);
      e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), 1.5F));
      e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), 20F));
      e.setCanceled(true);
   }

   @SubscribeEvent
   public static void fogColor(ViewportEvent.ComputeFogColor e) {
      if (mist <= 0) return;
      float k = mist * 0.85F;
      e.setRed(Mth.lerp(k, e.getRed(), 0.15F));
      e.setGreen(Mth.lerp(k, e.getGreen(), 0.19F));
      e.setBlue(Mth.lerp(k, e.getBlue(), 0.14F));
   }
}
