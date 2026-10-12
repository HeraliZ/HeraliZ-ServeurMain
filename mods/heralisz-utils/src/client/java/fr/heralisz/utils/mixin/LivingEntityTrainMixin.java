package fr.heralisz.utils.mixin;

import fr.heralisz.utils.train.VoyageursClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jambes et corps des passagers debout du train : le jeu anime la marche et tourne le corps d'après le
 * déplacement dans le monde depuis le tick précédent, nul pour un passager (placé par son véhicule), et le joueur
 * « flottait » dans la voiture. Pour un passager d'un voyageur du train, la vitesse vient du pas fait dans la voiture
 * (VoyageursClient.marcheAnimee : position prédite pour soi, position reçue du serveur et lissée pour les autres), et le
 * corps se tourne dans le sens de ce pas (VoyageursClient.orienterCorps, même règle que LivingEntity.tick). Même formule
 * que LivingEntity.updateWalkAnimation pour les jambes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTrainMixin {
    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void heraliszUtils$marcheDansLeTrain(boolean avecHauteur, CallbackInfo ci) {
        LivingEntity soi = (LivingEntity) (Object) this;
        float pas = VoyageursClient.marcheAnimee(soi);
        if (pas >= 0f) {
            VoyageursClient.orienterCorps(soi);
            soi.walkAnimation.update(Math.min(pas * 4.0f, 1.0f), 0.4f);
            ci.cancel();
        }
    }
}
