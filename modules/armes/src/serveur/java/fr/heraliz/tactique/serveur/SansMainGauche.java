package fr.heraliz.tactique.serveur;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;

/**
 * Plus de deuxième main (demande du propriétaire, 24/10) : en survie et en aventure, la touche d'échange des mains (F)
 * ne fait rien et tout objet posé dans la main gauche (case du bouclier de l'inventaire) revient dans l'inventaire au
 * tick suivant, déplacé, jamais copié. Inventaire plein : l'objet reste en main gauche jusqu'à ce qu'une case se libère
 * (jamais jeté, ni perdu : objets à vie compris). Créatif et spectateur : rien ne change.
 * <p>31/10 (demande du propriétaire : la main gauche ne doit servir d'aucune façon) : rangement à chaque tick au lieu
 * d'un tick sur cinq (Maj + clic d'un bouclier, touche F dans l'inventaire d'un jeu modifié, {@code /item} : l'objet
 * repart dans le même tick, avant que le joueur puisse s'en servir) ; un objet de la main gauche en cours d'utilisation
 * est arrêté puis rangé (avant : on attendait la fin de l'utilisation). Une utilisation de la main droite est toujours
 * attendue. Côté joueur, la case est retirée de l'inventaire (Équipement, {@code SansMainGaucheClient}). Coût : une
 * lecture de case par joueur et par tick.</p>
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
      if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) {
         return;
      }
      ItemStack g = p.getOffhandItem();
      if (g.isEmpty()) return;
      if (p.isUsingItem()) {
         // main droite : on attend la fin (le rangement pourrait fusionner avec l'objet utilisé) ; main gauche : arrêtée
         if (p.getUsedItemHand() != InteractionHand.OFF_HAND) return;
         p.stopUsingItem();
      }
      // la case est vidée AVANT de ranger : Inventory.add chercherait sinon d'abord de la place dans la main gauche
      // elle-même (objets empilables fusionnés puis effacés) ; ce qui ne rentre pas y revient
      int avant = g.getCount();
      p.getInventory().offhand.set(0, ItemStack.EMPTY);
      p.getInventory().add(g);
      if (!g.isEmpty()) p.getInventory().offhand.set(0, g);
      // inventaire plein : rien n'a bougé, rien à envoyer (essai refait au tick suivant)
      if (g.getCount() != avant) p.inventoryMenu.broadcastChanges();
   }
}
