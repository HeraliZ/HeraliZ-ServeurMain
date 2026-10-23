package fr.heraliz.tactique.client.decharger;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heraliz.tactique.decharger.Decharger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Touche « Décharger l'arme » (N par défaut) : en jeu, l'arme TaCZ en main ; dans l'inventaire, un coffre ou une fouille,
 * l'arme de SON inventaire sous la souris ; dans l'Atelier d'arme, l'arme en main (aussi par son bouton « Décharger »).
 * Le serveur décide et rend les balles (DechargerServeur).
 */
public final class DechargerClient {
   public static final KeyMapping TOUCHE = new KeyMapping("key.heraliz_tactique.decharger", InputConstants.Type.KEYSYM,
      GLFW.GLFW_KEY_N, "key.categories.heraliz_tactique");

   private DechargerClient() {
   }

   public static void brancher(IEventBus busMod) {
      busMod.addListener((RegisterKeyMappingsEvent e) -> e.register(TOUCHE));
      MinecraftForge.EVENT_BUS.addListener(DechargerClient::tick);
      MinecraftForge.EVENT_BUS.addListener(DechargerClient::toucheEcran);
   }

   private static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      while (TOUCHE.consumeClick()) {
         LocalPlayer p = mc.player;
         if (p == null || p.isSpectator() || mc.screen != null) continue;
         if (IGun.getIGunOrNull(p.getMainHandItem()) != null) Decharger.demander(p.getInventory().selected);
         else if (IGun.getIGunOrNull(p.getOffhandItem()) != null) Decharger.demander(40);
      }
   }

   private static void toucheEcran(ScreenEvent.KeyPressed.Pre e) {
      if (!TOUCHE.getKey().equals(InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) return;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || p.isSpectator()) return;
      if (e.getScreen() instanceof GunRefitScreen) {
         if (IGun.getIGunOrNull(p.getMainHandItem()) != null) Decharger.demander(p.getInventory().selected);
         e.setCanceled(true);
         return;
      }
      if (e.getScreen() instanceof AbstractContainerScreen<?> ecran) {
         Slot s = ecran.getSlotUnderMouse();
         if (s == null || !(s.container instanceof Inventory inv) || inv.player != p || IGun.getIGunOrNull(s.getItem()) == null) return;
         Decharger.demander(s.getContainerSlot());
         e.setCanceled(true);
      }
   }
}
