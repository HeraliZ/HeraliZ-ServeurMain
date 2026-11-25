package fr.heralisz.utils.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Touche F4 sans effet (25/11, demande du propriétaire : « quand on appuie sur F4, c'est soit clair, soit plus foncé :
 * qu'on ne puisse pas changer, bloqué sur le rendu clair »). F4 allume et coupe l'effet de post-traitement en cours :
 * le filtre d'image de HeraliZ (coupé pour tout le monde, voir FiltreImage), mais aussi l'écran gris d'un joueur à terre,
 * le saignement ou l'irradiation : un joueur ne peut plus les retirer.
 */
@Mixin(GameRenderer.class)
public abstract class F4BloqueMixin {
    @Inject(method = "togglePostEffect", at = @At("HEAD"), cancellable = true)
    private void heraliszUtils$f4SansEffet(CallbackInfo ci) {
        ci.cancel();
    }
}
