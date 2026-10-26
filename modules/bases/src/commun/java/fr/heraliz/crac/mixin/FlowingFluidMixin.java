package fr.heraliz.crac.mixin;

import fr.heraliz.crac.pont.Serveur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge has no event for fluid flow: this is the only place a spread into a claim can be stopped. */
@Mixin({FlowingFluid.class})
public abstract class FlowingFluidMixin {
    @Inject(
        method = {"canSpreadTo"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void crac$stopAtClaims(
        BlockGetter level,
        BlockPos from,
        BlockState fromState,
        Direction direction,
        BlockPos to,
        BlockState toState,
        FluidState toFluid,
        Fluid fluid,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (level instanceof ServerLevel sl && Serveur.MOTEUR != null && !Serveur.MOTEUR.fluidePeutEntrer(sl, from, to)) {
            cir.setReturnValue(false);
        }
    }
}
