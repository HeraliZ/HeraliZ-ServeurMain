package fr.heralisz.utils.mixin;

import fr.heralisz.utils.train.VoyageursClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Jambes d'un passager du train qui tient une arme de TaCZ, vu à la troisième personne : l'animation de TaCZ
 * (AnimationManager.playLowerAnimation, avec Player Animator) met les jambes en pose de monture (« ride_lower ») dès que
 * le joueur a un véhicule ; un passager debout du train apparaissait donc assis sur une selle, et glissait dans la
 * voiture. Pour un passager debout, aucun véhicule n'est vu ici : TaCZ joue la marche, la course, l'attente debout ou
 * accroupie comme hors du train. Assis sur une banquette : la pose assise reste.
 * <p>
 * Cible : la méthode de TaCZ 1.1.8 (non obfusquée : remap = false) ; l'appel visé est Entity.getVehicle, sous son nom
 * de jeu (m_20202_), sur AbstractClientPlayer comme dans le code de TaCZ. require = 0 : si TaCZ change, rien ne plante,
 * la pose redevient celle de TaCZ.
 */
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.AnimationManager", remap = false)
public abstract class TaczAnimationTrainMixin {
    @Redirect(method = "playLowerAnimation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;m_20202_()Lnet/minecraft/world/entity/Entity;"),
            require = 0)
    private static Entity heraliszUtils$vehiculeHorsTrain(AbstractClientPlayer joueur) {
        return VoyageursClient.vehiculePourAnimation(joueur);
    }
}
