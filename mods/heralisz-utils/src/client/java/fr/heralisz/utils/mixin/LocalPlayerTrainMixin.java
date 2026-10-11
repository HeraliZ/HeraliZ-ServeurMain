package fr.heralisz.utils.mixin;

import fr.heralisz.utils.train.VoyageursClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Balancement de la vue du joueur qui marche dans le train : Player.rideTick le remet à zéro à chaque tick pour tout
 * passager ; on le recalcule ensuite d'après le pas fait dans la voiture (VoyageursClient.balancement). Rien ne change
 * hors du train ni assis.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTrainMixin {
    @Inject(method = "rideTick", at = @At("TAIL"))
    private void heraliszUtils$balancementDansLeTrain(CallbackInfo ci) {
        VoyageursClient.balancement((LocalPlayer) (Object) this);
    }
}
