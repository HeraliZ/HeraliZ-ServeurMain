package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.block.entity.CoffreBlockEntity;
import fr.heraliz.crac.block.entity.MetalCrateBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Coffres des bases sans sac à dos (HeraliZ, 11/11, retour du propriétaire : « bien vérifier qu'il est impossible, dans
 * les vaults, de mettre un sac à dos vide ou plein ») : la caisse en métal et les coffres par rareté (malle de chantier,
 * casier métallique, caisse d'armes, coffre-fort) refusent tout sac à dos, VIDE OU PLEIN. Règle de Minecraft
 * {@code Item.canFitInsideContainerItems} : faux pour les sacs de HeraliZ Backpack (comme pour les boîtes de Shulker),
 * c'est déjà elle qui empêche un sac d'entrer dans un sac.
 * <ul>
 *   <li>écran du coffre (clic, Maj + clic, glisser, touches 1 à 9, touche de la main gauche) : Minecraft demande
 *   {@code Slot.mayPlace} avant de poser quoi que ce soit dans une case ({@code serveur/mixin/SlotCoffreSansSacMixin}) ;</li>
 *   <li>entonnoir, dropper, tuyaux des autres mods : {@code canPlaceItem} des deux entités de bloc.</li>
 * </ul>
 * Avant le 11/11, rien ne l'empêchait. Un sac déjà rangé dans un coffre y reste et se reprend comme avant.
 */
public final class SansSac {
    private SansSac() {
    }

    /** L'objet peut entrer dans un coffre de base : tout, sauf un sac à dos (ou un autre objet contenant). */
    public static boolean accepte(ItemStack stack) {
        return stack.isEmpty() || stack.getItem().canFitInsideContainerItems();
    }

    /** Inventaire d'un coffre de base (caisse en métal, coffres par rareté). */
    public static boolean coffreDeBase(Container conteneur) {
        return conteneur instanceof CoffreBlockEntity || conteneur instanceof MetalCrateBlockEntity;
    }
}
