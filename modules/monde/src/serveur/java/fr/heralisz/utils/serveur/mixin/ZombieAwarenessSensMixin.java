package fr.heralisz.utils.serveur.mixin;

import fr.heralisz.utils.zombies.SensAwareness;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zombie Awareness (Corosus, mod d'un autre auteur, jar intact) : sa recherche des sens ({@code ZAUtil.getSenseNearEntity})
 * est faite par {@link SensAwareness} (même résultat, sans lister toutes les entités à 64 blocs à chaque appel).
 * remap = false : méthode du mod (pas de Minecraft) ; types de Minecraft dans la signature : mêmes noms de classes en jeu ;
 * config de mixins serveur non obligatoire, require = 0 : si le mod change ou manque, sa propre recherche reste.
 */
@Pseudo
@Mixin(targets = "com.corosus.zombieawareness.ZAUtil", remap = false)
public abstract class ZombieAwarenessSensMixin {
    @Inject(method = "getSenseNearEntity(Lnet/minecraft/world/entity/Entity;)Lcom/corosus/zombieawareness/EntityScent;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void heraliz$sensRapides(Entity source, CallbackInfoReturnable<Object> cir) {
        SensAwareness.chercher(source, cir);
    }
}
