package fr.heraliz.tactique.serveur;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;

/**
 * Plus de deuxième main (demande du propriétaire, 24/10) : en survie et en aventure, la touche d'échange des mains (F)
 * ne fait rien et tout objet posé dans la main gauche (case du bouclier de l'inventaire) revient dans l'inventaire au
 * tick suivant, déplacé, jamais copié. Inventaire plein : l'objet reste en main gauche jusqu'à ce qu'une case se libère
 * (jamais jeté, ni perdu : objets à vie compris). Créatif et spectateur : rien ne change.
 */
public final class SansMainGauche {
   private SansMainGauche() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(SansMainGauche::echange);
      MinecraftForge.EVENT_BUS.addListener(SansMainGauche::tick);
   }

   private static void echange(LivingSwapItemsEvent.Hands e) {
      if (e.getEntity() instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) e.setCanceled(true);
   }

   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()
            || p.tickCount % 5 != 0) {
         return;
      }
      ItemStack g = p.getOffhandItem();
      if (g.isEmpty() || p.isUsingItem()) return;
      ItemStack copie = g.copy();
      if (p.getInventory().add(copie) && copie.isEmpty()) {
         p.getInventory().offhand.set(0, ItemStack.EMPTY);
      } else if (copie.getCount() < g.getCount()) {
         // une partie seulement est rangée : le reste reste en main gauche
         p.getInventory().offhand.set(0, copie);
      }
      p.inventoryMenu.broadcastChanges();
   }
}
