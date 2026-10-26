package fr.heralisz.utils.zombies.chemins;

import fr.heralisz.utils.zombies.CheminsZombies;
import fr.heralisz.utils.zombies.ZombieSurvie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Recherches de chemin des zombies HeraliZ : budget par tick et chemin repris d'un voisin ({@link CheminsZombies}).
 * Serveur seulement ; les autres créatures ne sont pas touchées.
 */
@Mixin(PathNavigation.class)
public abstract class CheminsZombiesMixin {
    @Shadow
    @Final
    protected Mob mob;
    @Shadow
    @Nullable
    protected Path path;
    @Shadow
    @Nullable
    private BlockPos targetPos;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void heraliz$avant(Set<BlockPos> cibles, int decalage, boolean dessus, int precision, float portee, CallbackInfoReturnable<Path> cir) {
        if (!(this.mob instanceof ZombieSurvie) || this.mob.level().isClientSide || cibles.isEmpty()) return;
        // Minecraft reprend lui-même le chemin en cours s'il vise toujours la même case : rien à compter
        if (this.path != null && !this.path.isDone() && cibles.contains(this.targetPos)) return;
        Path p = CheminsZombies.avant(this.mob, cibles);
        if (p == CheminsZombies.REFUS) cir.setReturnValue(null);
        else if (p != null) cir.setReturnValue(p);
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void heraliz$apres(Set<BlockPos> cibles, int decalage, boolean dessus, int precision, float portee, CallbackInfoReturnable<Path> cir) {
        if (this.mob instanceof ZombieSurvie && !this.mob.level().isClientSide) CheminsZombies.apres(this.mob, cibles, cir.getReturnValue());
    }
}
