package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import fr.heraliz.tactique.decharger.Decharger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Décharger une arme TaCZ, côté serveur (Decharger) : l'arme de la case demandée rend les balles de son chargeur et celle
 * de sa chambre à l'inventaire (posées au sol si l'inventaire est plein), par {@code dropAllAmmo} de TaCZ, celui qui sert
 * quand on retire un chargeur dans l'écran de TaCZ (munitions de l'arme, jamais copiées). Le chargeur reste sur l'arme,
 * vide. Refusé : arme d'un match (prêtée), arme en cours de rechargement, arme sans munitions à rendre (énergie,
 * carburant, munitions de l'inventaire, réserve de TaCZ).
 */
final class DechargerServeur {
   /** Main gauche dans {@link Inventory#getItem(int)}. */
   private static final int MAIN_GAUCHE = 40;

   private DechargerServeur() {
   }

   static void brancher() {
      Decharger.DEMANDE_SERVEUR.brancher(DechargerServeur::recevoir);
   }

   private static void recevoir(Decharger.Demande d) {
      ServerPlayer sp = d.joueur();
      int c = d.caseInventaire();
      if (sp.isSpectator() || !sp.isAlive() || !((c >= 0 && c < 36) || c == MAIN_GAUCHE)) return;
      Inventory inv = sp.getInventory();
      ItemStack st = inv.getItem(c);
      if (st.isEmpty() || !(st.getItem() instanceof IGun gun)) return;
      if (st.hasTag() && st.getTag().contains("HeraliszMatch")) {
         message(sp, "heraliz_tactique.decharger.match", "Impossible de décharger une arme de match.");
         return;
      }
      boolean enMain = c == inv.selected || c == MAIN_GAUCHE;
      if (enMain && IGunOperator.fromLivingEntity(sp).getSynReloadState().getStateType().isReloading()) {
         message(sp, "heraliz_tactique.decharger.recharge", "Attends la fin du rechargement.");
         return;
      }
      boolean carburant = TimelessAPI.getCommonGunIndex(gun.getGunId(st))
         .map(i -> FeedType.FUEL.equals(i.getGunData().getReloadData().getType())).orElse(true);
      if (carburant || sp.isCreative() || gun.useInventoryAmmo(st) || gun.useDummyAmmo(st)) {
         message(sp, "heraliz_tactique.decharger.impossible", "Cette arme ne se décharge pas.");
         return;
      }
      int balles = gun.getCurrentAmmoCount(st) + (gun.hasBulletInBarrel(st) ? 1 : 0);
      if (balles <= 0) {
         message(sp, "heraliz_tactique.decharger.vide", "L'arme est déjà vide.");
         return;
      }
      // la balle de la chambre rejoint le chargeur, puis TaCZ rend tout le chargeur
      if (gun.hasBulletInBarrel(st)) {
         gun.setBulletInBarrel(st, false);
         gun.setCurrentAmmoCount(st, balles);
      }
      gun.dropAllAmmo(sp, st);
      int restant = gun.getCurrentAmmoCount(st);
      if (restant > 0) { // munitions inconnues de TaCZ : rien rendu, l'arme garde ses balles
         return;
      }
      inv.setChanged();
      sp.inventoryMenu.broadcastChanges();
      sp.containerMenu.broadcastChanges();
      message(sp, "heraliz_tactique.decharger.ok", "Arme déchargée : %s balles récupérées.", String.valueOf(balles));
   }

   private static void message(ServerPlayer sp, String cle, String francais, Object... args) {
      sp.displayClientMessage(Component.translatableWithFallback(cle, francais, args), true);
   }
}
