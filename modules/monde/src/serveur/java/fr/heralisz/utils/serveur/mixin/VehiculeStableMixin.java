package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.Stabilite;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.vehicle.PhysicsEngine;
import org.ywzj.vehicle.vehicle.structure.VehicleCubeOBB;

import java.util.List;

/**
 * Véhicules à roues stables (fr.heraliz.vehicules.Stabilite) : trois points d'accroche sur le moteur physique de
 * Limitless Vehicle ({@code org.ywzj.vehicle.vehicle.PhysicsEngine}, appelé par le serveur seulement), noms d'ywzj
 * (remap = false) :
 * <ul>
 *   <li>{@code motionByImpact} (chocs) : reçoit les points de contact sans les obstacles bas, et sa vitesse vers le haut
 *   est plafonnée à la fin ;</li>
 *   <li>{@code rotAndFallByGravity} (bascule et chute) : accélération du tick adoucie et vitesse de bascule bornée avant
 *   le calcul.</li>
 * </ul>
 * require = 0 et config non obligatoire : si ywzj change ces méthodes, rien ne plante (la physique d'origine revient).
 */
@Mixin(value = PhysicsEngine.class, remap = false)
public abstract class VehiculeStableMixin {
    @Unique
    private float heralizVehicules$vitesseYAvant;

    @ModifyVariable(method = "motionByImpact(Ljava/util/List;[Lorg/joml/Vector3f;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private List<VehicleCubeOBB.CubePoint> heralizVehicules$petitesMarches(List<VehicleCubeOBB.CubePoint> points) {
        return Stabilite.sansPetitesMarches((PhysicsEngine) (Object) this, points);
    }

    @Inject(method = "motionByImpact(Ljava/util/List;[Lorg/joml/Vector3f;)V", at = @At("HEAD"), require = 0)
    private void heralizVehicules$avantChocs(List<VehicleCubeOBB.CubePoint> points, Vector3f[] axes, CallbackInfo ci) {
        heralizVehicules$vitesseYAvant = ((PhysicsEngine) (Object) this).velocity.y;
    }

    @Inject(method = "motionByImpact(Ljava/util/List;[Lorg/joml/Vector3f;)V", at = @At("RETURN"), require = 0)
    private void heralizVehicules$apresChocs(List<VehicleCubeOBB.CubePoint> points, Vector3f[] axes, CallbackInfo ci) {
        Stabilite.plafonnerMontee((PhysicsEngine) (Object) this, heralizVehicules$vitesseYAvant);
    }

    @Inject(method = "rotAndFallByGravity(Ljava/util/List;[Lorg/joml/Vector3f;Lorg/joml/Vector3f;)V", at = @At("HEAD"), require = 0)
    private void heralizVehicules$avantBascule(List<VehicleCubeOBB.CubePoint> points, Vector3f[] axes, Vector3f force, CallbackInfo ci) {
        Stabilite.adoucirBascule((PhysicsEngine) (Object) this);
    }
}
