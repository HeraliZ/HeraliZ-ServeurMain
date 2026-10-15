package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.Stabilite;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;

/**
 * Véhicules à roues plus maniables (fr.heraliz.vehicules.Stabilite) : sur la conduite de Limitless Vehicle
 * ({@code WheeledVehicle.tickMove}, calculée par le serveur seulement), noms d'ywzj (remap = false) :
 * <ul>
 *   <li>début : volant jamais plus lent que le réglage {@code braquage_min} ;</li>
 *   <li>constante 0.01 (seul double 0.01 de la méthode : plancher de l'adhérence, {@code Math.max(0.01, 1 - √(vitesse /
 *   vitesse max))}) : relevée au réglage {@code adherence}.</li>
 * </ul>
 * require = 0 et config non obligatoire : si ywzj change cette méthode, rien ne plante (la conduite d'origine revient).
 */
@Mixin(value = WheeledVehicle.class, remap = false)
public abstract class VehiculeConduiteMixin {
    @Inject(method = "tickMove()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), require = 0)
    private void heralizVehicules$braquage(CallbackInfoReturnable<Vec3> cir) {
        Stabilite.braquage((WheeledVehicle) (Object) this);
    }

    @ModifyConstant(method = "tickMove()Lnet/minecraft/world/phys/Vec3;", constant = @Constant(doubleValue = 0.01), require = 0)
    private double heralizVehicules$adherence(double moteur) {
        return Stabilite.adherence(moteur);
    }
}
