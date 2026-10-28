package fr.heraliz.crac.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HeraliZ (28/10, demande du propriétaire : « sur Rust on saute accroupi sur le sol posé sur un demi-mur même avec un
 * sol au-dessus de la tête ») : un joueur accroupi fait 1,25 bloc de haut au lieu de 1,5. Un plancher posé sur des
 * demi-murs laisse 21 px (1,31 bloc) jusqu'au plancher du dessus : accroupi, on y passe et on y saute (aide au saut de
 * {@code SautRebord}) au lieu d'y rester coincé. Les yeux descendent avec (1,1 au lieu de 1,27) pour rester sous le
 * plafond. Mêmes nombres chez le joueur et au serveur (code commun) ; les balles visent toujours le corps dessiné
 * ({@code HitboxCorps} de Tactique), pas cette boîte.
 */
@Mixin({Player.class})
public abstract class PlayerAccroupiMixin {
    @Unique
    private static final EntityDimensions CRAC$ACCROUPI = EntityDimensions.scalable(0.6F, 1.25F);
    @Unique
    private static final float CRAC$YEUX_ACCROUPI = 1.1F;

    @Inject(
        method = {"getDimensions"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void crac$accroupi(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.CROUCHING) {
            cir.setReturnValue(CRAC$ACCROUPI);
        }
    }

    @Inject(
        method = {"getStandingEyeHeight"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void crac$yeuxAccroupi(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        if (pose == Pose.CROUCHING) {
            cir.setReturnValue(CRAC$YEUX_ACCROUPI);
        }
    }
}
