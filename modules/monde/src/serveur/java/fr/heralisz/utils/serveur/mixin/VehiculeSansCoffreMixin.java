package fr.heralisz.utils.serveur.mixin;

import fr.heraliz.vehicules.CoffreRetire;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Plus de coffre dans les véhicules (fr.heraliz.vehicules.CoffreRetire) : le serveur n'ouvre plus le coffre de 54 cases
 * que Limitless Vehicle donne à chaque véhicule, ni à bord (touche « Coffre du véhicule » : paquet « ouvrir l'inventaire »
 * de Minecraft), ni d'un clic droit sur une épave.
 * <p>
 * Cible : {@code org.ywzj.vehicle.entity.ContainerCraft} (classe mère de tous les véhicules du moteur), ses deux méthodes
 * héritées de Minecraft, écrites avec leur nom SRG tel qu'il est dans le jar d'ywzj (remap = false : config de mixins
 * serveur sans table de correspondance ; types de Minecraft dans la signature : mêmes noms de classes en jeu) :
 * <ul>
 *   <li>{@code openCustomInventoryScreen(Player)} ({@code m_213583_}) : ne fait plus rien (pas d'écran, pas d'événement
 *   « coffre ouvert ») ;</li>
 *   <li>{@code createMenu(int, Inventory, Player)} ({@code m_7208_}) : rend null (aucun écran), au cas où un autre chemin
 *   demanderait l'écran du coffre.</li>
 * </ul>
 * require = 0 et config non obligatoire : si ywzj change ces méthodes ou manque, rien ne plante (le coffre reviendrait).
 * Le contenu déjà rangé est posé au sol par la partie serveur (SuiviVehicules).
 */
@Mixin(targets = "org.ywzj.vehicle.entity.ContainerCraft", remap = false)
public abstract class VehiculeSansCoffreMixin {
    @Inject(method = "m_213583_(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void heralizVehicules$coffreFerme(Player joueur, CallbackInfo ci) {
        if ((Object) this instanceof AbstractVehicle && CoffreRetire.actif()) ci.cancel();
    }

    @Inject(method = "m_7208_(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void heralizVehicules$sansEcran(int id, Inventory inventaire, Player joueur, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if ((Object) this instanceof AbstractVehicle && CoffreRetire.actif()) cir.setReturnValue(null);
    }
}
