package fr.heralisz.utils.mixin;

import fr.heralisz.utils.cine.Cinematique;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pendant la cinématique d'arrivée, le mur rayé de la bordure du monde n'est jamais dessiné : la scène du train
 * (au-dessus du point de réveil) dépasse la bordure du monde du spawn, et on voyait la caméra la traverser dans la
 * cabine. La bordure elle-même ne change pas (serveur), seul son dessin est sauté chez ce joueur.
 */
@Mixin(LevelRenderer.class)
public abstract class BordureCinematiqueMixin {
    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true)
    private void heraliszUtils$pasDeBordureEnCinematique(Camera camera, CallbackInfo ci) {
        if (Cinematique.actif()) {
            ci.cancel();
        }
    }
}
