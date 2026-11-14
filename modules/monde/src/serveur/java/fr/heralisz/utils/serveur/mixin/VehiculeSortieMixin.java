package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.SortieSure;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Sortie d'un véhicule jamais dans un bloc ni de l'autre côté d'un mur (fr.heraliz.vehicules.SortieSure, 14/11) : à la
 * fin de {@code AbstractVehicle.getDismountLocationForPassenger(LivingEntity)} ({@code m_7688_}, nom SRG tel qu'il est
 * dans le jar d'ywzj : remap = false, config de mixins serveur sans table de correspondance ; types de Minecraft dans la
 * signature : mêmes noms de classes en jeu), l'endroit choisi par Limitless Vehicle est vérifié et remplacé s'il n'est pas
 * sûr.
 * <p>Pourquoi un mixin : c'est le seul moment où le serveur connaît l'endroit avant d'y poser le joueur (Minecraft le
 * demande juste après la descente, quelle qu'en soit la cause : S'accroupir, touche du moteur, téléportation, mise à
 * terre, mort) ; replacer le joueur après coup le ferait sauter deux fois chez lui. Jar d'ywzj intact.
 * <p>require = 0 et config non obligatoire : si ywzj change cette méthode, rien ne plante (l'endroit du moteur revient
 * tel quel).
 */
@Mixin(value = AbstractVehicle.class, remap = false)
public abstract class VehiculeSortieMixin {
    @Inject(method = "m_7688_(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void heralizVehicules$sortieSure(LivingEntity passager, CallbackInfoReturnable<Vec3> cir) {
        Vec3 sortie = cir.getReturnValue();
        Vec3 sure = SortieSure.verifier((AbstractVehicle) (Object) this, passager, sortie);
        if (sure != sortie) cir.setReturnValue(sure);
    }
}
