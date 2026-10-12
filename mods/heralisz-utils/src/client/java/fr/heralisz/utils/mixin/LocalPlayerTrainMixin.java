package fr.heralisz.utils.mixin;

import fr.heralisz.utils.train.VoyageursClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fin du tick du joueur qui est dans le train (VoyageursClient.apresTick) : balancement de la vue (Player.rideTick le
 * remet à zéro à chaque tick pour tout passager ; on le recalcule d'après le pas fait dans la voiture) et « au sol » quand
 * il tient sur le plancher (l'arme de TaCZ joue alors sa marche et sa course, au lieu de rester figée comme en l'air).
 * Rien ne change hors du train.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTrainMixin {
    @Inject(method = "rideTick", at = @At("TAIL"))
    private void heraliszUtils$balancementDansLeTrain(CallbackInfo ci) {
        VoyageursClient.apresTick((LocalPlayer) (Object) this);
    }
}
