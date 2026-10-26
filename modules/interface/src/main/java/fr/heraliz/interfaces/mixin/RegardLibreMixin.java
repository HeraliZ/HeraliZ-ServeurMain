package fr.heraliz.interfaces.mixin;

import fr.heraliz.interfaces.client.RegardLibre;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Regard libre ({@link RegardLibre}) : pendant que la touche est tenue, le mouvement de la souris tourne la caméra et
 * non le joueur (ni corps, ni marche, ni visée). {@code Entity.turn} n'est appelé que par la souris, pour le joueur local.
 */
@Mixin(Entity.class)
public abstract class RegardLibreMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void heraliz$regardLibre(double dx, double dy, CallbackInfo ci) {
        if (RegardLibre.actif() && (Object) this == Minecraft.getInstance().player) {
            RegardLibre.tourner(dx, dy);
            ci.cancel();
        }
    }
}
