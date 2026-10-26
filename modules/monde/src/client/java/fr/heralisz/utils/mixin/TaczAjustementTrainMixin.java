package fr.heralisz.utils.mixin;

import fr.heralisz.utils.train.VoyageursClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Haut du corps d'un passager du train qui tient une arme de TaCZ, vu à la troisième personne : TaCZ tourne le buste
 * vers le regard (AdjustmentYRotModifier) sauf pour un joueur qui a un véhicule ; un passager debout tenait donc son arme
 * dans l'axe de ses jambes, pas de son regard. Pour un passager debout ou allongé, aucun véhicule n'est vu ici (comme
 * TaczAnimationTrainMixin). Cible : AdjustmentYRotModifier.apply(String) de TaCZ 1.1.8 (remap = false), appel
 * Entity.getVehicle sous son nom de jeu (m_20202_) sur Player. require = 0 : si TaCZ change, rien ne plante.
 */
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.AdjustmentYRotModifier", remap = false)
public abstract class TaczAjustementTrainMixin {
    @Redirect(method = "apply(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_20202_()Lnet/minecraft/world/entity/Entity;"),
            require = 0)
    private Entity heraliszUtils$vehiculeHorsTrain(Player joueur) {
        return VoyageursClient.vehiculePourAnimation(joueur);
    }
}
