package fr.heraliz.crac.mixin;

import fr.heraliz.crac.pont.Serveur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge has no event for fire spreading: fire outside a claim neither jumps into it nor burns the blocks at its edge. */
@Mixin({FireBlock.class})
public abstract class FireBlockMixin {
    /** The fire block being ticked (server thread), where any spread of that tick comes from. */
    @Unique
    private static BlockPos crac$spreadingFrom;

    @Inject(
        method = {"tick"},
        at = {@At("HEAD")}
    )
    private void crac$startSpread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        crac$spreadingFrom = pos;
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void crac$endSpread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        crac$spreadingFrom = null;
    }

    /** Fire jumping onto an air block next to something flammable (only called from tick). */
    @Inject(
        method = {"getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void crac$noIgnitionIntoClaims(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        BlockPos from = crac$spreadingFrom;
        if (from != null && level instanceof ServerLevel sl && Serveur.MOTEUR != null && !Serveur.MOTEUR.feuPeutPasser(sl, from, pos)) {
            cir.setReturnValue(0);
        }
    }

    /** Fire burning the flammable block on its {@code face} side; Forge's own method, so it keeps its name at runtime. */
    @Inject(
        method = {"tryCatchFire"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private void crac$noBurningIntoClaims(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && Serveur.MOTEUR != null && !Serveur.MOTEUR.feuPeutPasser(sl, pos.relative(face), pos)) {
            ci.cancel();
        }
    }
}
