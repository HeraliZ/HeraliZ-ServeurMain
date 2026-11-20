package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.perf.PermissionsMohist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mohist (serveur hybride, jar intact) : la pile d'appels relevée à chaque permission refusée d'un joueur n'est plus relevée
 * quand le journal de débogage des permissions de Mohist est coupé ({@link PermissionsMohist}). Sans Mohist (Forge seul),
 * ou avec un Mohist qui ne la relève plus : rien ne change (cible absente).
 */
@Pseudo
@Mixin(targets = "org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity", remap = false)
public abstract class PermissionsSansPileMixin {
   @Redirect(method = {"hasPermission(Ljava/lang/String;)Z", "hasPermission(Lorg/bukkit/permissions/Permission;)Z"},
         at = @At(value = "INVOKE", target = "Ljava/lang/Thread;getStackTrace()[Ljava/lang/StackTraceElement;", remap = false),
         require = 0, remap = false)
   private StackTraceElement[] heraliz$pileSiJournal(Thread fil) {
      return PermissionsMohist.pile(fil);
   }
}
