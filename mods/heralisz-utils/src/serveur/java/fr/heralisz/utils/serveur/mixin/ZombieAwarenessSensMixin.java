package fr.heralisz.utils.serveur.mixin;

import fr.heralisz.utils.zombies.SensAwareness;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zombie Awareness (Corosus, jar intact) : sa recherche des sens ({@code ZAUtil.getSenseNearEntity}) est faite par
 * {@link SensAwareness} (même résultat, sans lister toutes les entités à 64 blocs à chaque appel). remap = false : méthode
 * du mod (pas de Minecraft), config de mixins serveur sans table de correspondance ; require = 0 et config non
 * obligatoire : si le mod change ou manque, sa propre recherche reste.
 */
@Mixin(targets = "com.corosus.zombieawareness.ZAUtil", remap = false)
public abstract class ZombieAwarenessSensMixin {
    @Inject(method = "getSenseNearEntity(Lnet/minecraft/world/entity/Entity;)Lcom/corosus/zombieawareness/EntityScent;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void heralizZombies$sensRapides(Entity source, CallbackInfoReturnable<Object> cir) {
        SensAwareness.chercher(source, cir);
    }
}
