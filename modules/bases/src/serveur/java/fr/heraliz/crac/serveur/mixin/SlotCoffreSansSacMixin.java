package fr.heraliz.crac.serveur.mixin;

import fr.heraliz.crac.heraliz.SansSac;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Coffres des bases sans sac à dos (HeraliZ, 11/11, choix du propriétaire) : voir {@link SansSac}. Minecraft demande
 * {@code Slot.mayPlace} avant de poser un objet dans une case, par tous les chemins de l'écran : clic, Maj + clic (depuis
 * l'inventaire), glisser sur plusieurs cases, touches 1 à 9, touche de la main gauche. Pour une case d'une caisse en
 * métal ou d'un coffre par rareté, un sac à dos (vide ou plein) est refusé : il reste au curseur ou dans l'inventaire (le
 * serveur remet l'écran du joueur à jour). Le double clic ne fait que ramasser vers le curseur. Serveur seulement, au
 * clic seulement (aucun coût par tick) ; reprendre un objet d'un coffre ne change pas.
 */
@Mixin(Slot.class)
public abstract class SlotCoffreSansSacMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void crac$coffreSansSac(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (SansSac.coffreDeBase(slot.container) && !SansSac.accepte(stack)) {
            cir.setReturnValue(false);
        }
    }
}
