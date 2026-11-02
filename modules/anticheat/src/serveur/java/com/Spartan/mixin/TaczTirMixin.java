package com.Spartan.mixin;

import com.Spartan.check.combat.TirsTacz;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Résultat de chaque tir chez TaCZ, côté serveur (audit du 02/11, point 14) : TaCZ refuse lui-même un tir trop rapide
 * pour l'arme (COOL_DOWN) ou à l'horloge trafiquée (NETWORK_FAIL), sans rien dire à personne. Spartan compte ces refus
 * (TirsTacz) : un « rapid fire » se fait refuser sans cesse. Rien n'est changé au tir. Absent si TaCZ n'est pas installé
 * (@Pseudo, require = 0, ajouté par SpartanMixinPlugin seulement avec TaCZ) ; aucun champ de TaCZ cité ici (une autre
 * version de TaCZ ne peut pas empêcher le jeu de démarrer).
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczTirMixin {
    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("RETURN"), require = 0, remap = false)
    private void spartan$resultatTir(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, float charge, boolean b,
                                     CallbackInfoReturnable<Object> cir) {
        TirsTacz.resultat(this, cir.getReturnValue());
    }
}
