package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.SortieVitesse;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.network.message.ClientVehicleAction;

/**
 * Pas de sortie volontaire d'un véhicule lancé (fr.heraliz.vehicules.SortieVitesse, 14/11 : au-dessus de 15 km/h). Une
 * demande de sortie d'un joueur (touche S'accroupir ou touche du moteur) arrive au serveur par le message
 * {@code ClientVehicleAction} de Limitless Vehicle, traité sur le fil du serveur dans
 * {@code lambda$onClientMessageReceived$0} : au début de ce traitement, une demande trop rapide perd seulement sa part
 * « sortir » ({@code leaveVehicle}), le reste du message suit son cours. Les sorties forcées (mise à terre, mort,
 * téléportation, véhicule détruit, déconnexion) ne passent pas par ce message.
 * <p>Sur le message et non sur {@code AbstractVehicle} : un mixin sur cette classe empêchait le serveur de démarrer (banc du
 * 14/11 : Limitless Vehicle n'arrivait plus à charger ses véhicules). remap = false : classe d'ywzj, nom de la lambda tel
 * qu'il est dans son jar ; require = 0 et config non obligatoire : s'il change, rien ne plante (plus de limite de vitesse).
 */
@Mixin(value = ClientVehicleAction.class, remap = false)
public abstract class VehiculeMessageMixin {
    @Inject(method = "lambda$onClientMessageReceived$0", at = @At("HEAD"), require = 0)
    private static void heralizVehicules$sortieVolontaire(NetworkEvent.Context contexte, ClientVehicleAction message,
            CallbackInfo ci) {
        if (message.leaveVehicle && !SortieVitesse.permise(contexte.getSender())) message.leaveVehicle = false;
    }
}
