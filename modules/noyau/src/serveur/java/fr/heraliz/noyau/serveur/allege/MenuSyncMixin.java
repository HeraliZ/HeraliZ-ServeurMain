package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Point 4 de l'étape 2 du chantier « Minecraft allégé » (01/11) : {@code AbstractContainerMenu.broadcastChanges}, à chaque tick
 * pour le menu ouvert de chaque joueur (son inventaire le plus souvent), compare chaque case DEUX fois à sa copie : à la
 * dernière copie notée ({@code triggerSlotListeners}) puis à la copie envoyée au joueur ({@code synchronizeSlotToRemote}).
 * Chaque comparaison est complète ({@code ItemStack.matches} : objet, nombre, tout le NBT, capacités de Forge) : pour une
 * arme de TaCZ, un sac, une armure, des dizaines de valeurs ({@code CompoundTag.equals} au profil du banc).
 * <p>Quand Minecraft a noté les deux copies en même temps (le même objet, le cas de toute case qui n'a pas changé), la
 * 2e comparaison pose exactement la même question que la 1re (mêmes deux objets, rien entre les deux) : si la 1re a dit
 * « pareil », la 2e est sautée (réponse « pareil » : Minecraft n'aurait rien fait). Dans tous les autres cas (copies
 * différentes, case changée, appel hors de {@code broadcastChanges}) : la vraie comparaison. Réglage :
 * {@code [perf] menus_une_comparaison}.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuSyncMixin {
   /** La copie notée et la case comparées « pareil » juste avant, pour la même case (sinon null). */
   @Unique
   private ItemStack heraliz$copieNotee;
   @Unique
   private ItemStack heraliz$caseVue;

   @Redirect(method = "triggerSlotListeners", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
         ordinal = 0))
   private boolean heraliz$pareilNotee(ItemStack copie, ItemStack actuelle) {
      boolean pareil = ItemStack.matches(copie, actuelle);
      if (pareil && NoyauConfig.menusUneComparaison) {
         heraliz$copieNotee = copie;
         heraliz$caseVue = actuelle;
      } else {
         heraliz$copieNotee = null;
         heraliz$caseVue = null;
      }
      return pareil;
   }

   @Redirect(method = "synchronizeSlotToRemote", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
         ordinal = 0))
   private boolean heraliz$pareilEnvoyee(ItemStack envoyee, ItemStack actuelle) {
      ItemStack notee = heraliz$copieNotee;
      ItemStack vue = heraliz$caseVue;
      heraliz$copieNotee = null;
      heraliz$caseVue = null;
      if (notee != null && envoyee == notee && actuelle == vue) {
         Allege.menusComparaisonsEvitees++;
         return true;
      }
      return ItemStack.matches(envoyee, actuelle);
   }
}
