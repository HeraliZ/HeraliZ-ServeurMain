package fr.heraliz.interfaces.mixin;

import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plus de notifications en haut à droite pour les succès (progrès) ni les recettes débloquées (23/10, demande du
 * propriétaire) : il n'y a pas de succès sur le serveur. 25/10 (audit des écrans) : plus l'avertissement « Impossible de
 * vérifier les messages » non plus : il s'affiche à chaque connexion à un serveur hors ligne (le nôtre), par-dessus le
 * créateur de personnage, et le serveur ne peut pas le retirer. Les autres notifications (système, mods) passent.
 */
@Mixin(ToastComponent.class)
public abstract class ToastsMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void heraliz$sansSucces(Toast toast, CallbackInfo ci) {
        if (toast instanceof AdvancementToast || toast instanceof RecipeToast
                || toast instanceof SystemToast s && s.getToken() == SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) {
            ci.cancel();
        }
    }
}
