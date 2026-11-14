package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.SortieSure;
import fr.heraliz.vehicules.SortieVitesse;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.network.message.ClientVehicleAction;

/**
 * Sortie d'un véhicule, sur deux méthodes de {@code AbstractVehicle} de Limitless Vehicle (remap = false : config de
 * mixins serveur sans table de correspondance ; nom SRG tel qu'il est dans le jar d'ywzj pour la méthode héritée de
 * Minecraft ; types de Minecraft dans les signatures : mêmes noms de classes en jeu). Jar d'ywzj intact ; require = 0 et
 * config non obligatoire : si ywzj change ces méthodes, rien ne plante (le comportement du moteur revient tel quel).
 * <ul>
 *   <li><b>Endroit de sortie</b> (fr.heraliz.vehicules.SortieSure, 14/11) : à la fin de
 *   {@code getDismountLocationForPassenger(LivingEntity)} ({@code m_7688_}), l'endroit choisi par le moteur est vérifié et
 *   remplacé s'il n'est pas sûr (jamais dans un bloc ni de l'autre côté d'un mur). C'est le seul moment où le serveur
 *   connaît l'endroit avant d'y poser le joueur (Minecraft le demande juste après la descente, quelle qu'en soit la
 *   cause) ; replacer le joueur après coup le ferait sauter deux fois chez lui.</li>
 *   <li><b>Sortie volontaire à grande vitesse</b> (fr.heraliz.vehicules.SortieVitesse, 14/11) : au début de
 *   {@code onClientVehicleAction(ClientVehicleAction, Player)}, seule méthode où le serveur reçoit une demande de sortie
 *   d'un joueur (touche S'accroupir ou touche du moteur), la demande est refusée si le véhicule roule trop vite. Les
 *   sorties forcées (mise à terre, mort, téléportation, véhicule détruit, déconnexion) ne passent pas par là.</li>
 * </ul>
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

    @Inject(method = "onClientVehicleAction(Lorg/ywzj/vehicle/network/message/ClientVehicleAction;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void heralizVehicules$sortieVolontaire(ClientVehicleAction message, Player joueur, CallbackInfo ci) {
        if (message.leaveVehicle && !SortieVitesse.permise(joueur)) ci.cancel();
    }
}
