package com.Spartan.mixin;

import com.Spartan.check.combat.TirsTacz;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cadence réelle des tirs (19/11, TirsTacz.cadence) : juste avant que TaCZ juge la cadence d'un tir sur l'heure écrite par
 * le jeu du joueur, Spartan regarde l'heure d'ARRIVÉE de ce tir sur le réseau. Une rafale envoyée d'un coup (tirs datés à
 * la cadence de l'arme, partis dans le même paquet) est étalée : ses tirs partent ensuite à la cadence de l'arme (le tir
 * reçu est mis de côté, TaCZ le joue plus tard avec la même heure de départ recomptée). Les tirs normaux passent sans rien.
 * <p>
 * Cible : LivingEntityShoot.shoot(Supplier, Supplier, long, float, boolean) de TaCZ 1.1.8, au moment de l'appel à
 * getShootCoolDown(long) : après les retouches d'heure de Tactique (HorlogeTirs, en tête de méthode), avant les contrôles
 * de cadence et de réseau de TaCZ. Noms de TaCZ : remap = false ; aucun type de TaCZ cité ici (@Pseudo, ajouté par
 * SpartanMixinPlugin seulement avec TaCZ) ; require = 0 : un TaCZ changé tire comme avant, rien ne plante.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczCadenceMixin {
    @Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/entity/shooter/LivingEntityShoot;getShootCoolDown(J)J",
                    remap = false),
            cancellable = true, require = 0, remap = false)
    private void spartan$cadenceReelle(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, float charge, boolean b,
                                       CallbackInfoReturnable<Object> cir) {
        Object refus = TirsTacz.cadence(this, pitch, yaw, timestamp, charge);
        if (refus != null) {
            cir.setReturnValue(refus);
        }
    }
}
